package sbhackathon.koala.happyMSP.monitor_B.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.repository.EcrRepository;
import sbhackathon.koala.happyMSP.build_A.repository.RepoRepository;
import sbhackathon.koala.happyMSP.entity.Ecr;
import sbhackathon.koala.happyMSP.entity.Repository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final RepoRepository repoRepository;
    private final EcrRepository ecrRepository;

    private final PipelineNotificationService notifier;
    private final AwsEcrCheckService awsEcrCheckService;
    private final K8sResourcePoller k8sResourcePoller;

    private final Executor monitorExecutor;

    private boolean useAwsEcrCheck = true;

    @Async("monitorExecutor")
    public void startDeploymentPipeline(String repoUrl) {

        try {
            // [1단계] 빌드 및 아티팩트 확인
            List<sbhackathon.koala.happyMSP.entity.Service> services = waitForBuildAndArtifacts(repoUrl);

            if (services == null || services.isEmpty()) {
                notifier.publish(repoUrl, "deployment-failed", "배포 중단: 빌드 정보를 찾을 수 없거나 시간이 초과되었습니다.");
                return;
            }

            // [변경] ProjectName 생성 (K8s 호환을 위해 특수문자 치환)
            String projectName = extractRepositoryName(repoUrl);
            log.info("Deploying Project: {}", projectName);

            List<Map<String, String>> servicePayloads = new ArrayList<>();

            for (sbhackathon.koala.happyMSP.entity.Service service : services) {
                List<Ecr> ecrs = ecrRepository.findByService_Id(service.getId());

                if (!ecrs.isEmpty()) {
                    Ecr ecr = ecrs.get(0);
                    String fullImageUri = ecr.getUri();

                    Map<String, String> svcMap = new HashMap<>();
                    svcMap.put("serviceName", service.getName());
                    svcMap.put("imageUri", fullImageUri);
                    servicePayloads.add(svcMap);
                }
            }

            Map<String, Object> deployRequestPayload = new HashMap<>();
            deployRequestPayload.put("projectName", projectName);
            deployRequestPayload.put("services", servicePayloads);

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("message", "🚀 " + services.size() + "개의 서비스(" + projectName + ") EKS 배포를 시작합니다.");
            eventData.put("payload", deployRequestPayload);

            notifier.publish(repoUrl, "stage-2-start", eventData);

            List<CompletableFuture<Boolean>> futures = services.stream()
                    .map(service -> CompletableFuture.supplyAsync(
                            () -> monitorSingleServicePipeline(repoUrl, projectName, service),
                            monitorExecutor
                    ))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenAccept(ignored -> {
                        boolean isAllSuccess = futures.stream()
                                .allMatch(CompletableFuture::join);

                        if (isAllSuccess) {
                            notifier.publish(repoUrl, "all-complete", "🎉 모든 서비스 배포가 완료되었습니다!");
                        } else {
                            notifier.publish(repoUrl, "deployment-failed", "❌ 일부 서비스 배포에 실패하였습니다. 로그를 확인해주세요.");
                        }
                    });

        } catch (Exception e) {
            log.error("Pipeline Error", e);
            notifier.publish(repoUrl, "deployment-failed", "서버 내부 오류 발생: " + e.getMessage());
        }
    }

    private boolean monitorSingleServicePipeline(String repoUrl, String projectName, sbhackathon.koala.happyMSP.entity.Service service) {
        String serviceName = service.getName();

        notifier.sendServiceLog(repoUrl, serviceName, "RESOURCE", "PENDING", "K8s 리소스 생성 대기 중...");

        if (!k8sResourcePoller.pollK8sResourceCreation(repoUrl, projectName, serviceName)) {
            notifier.sendServiceLog(repoUrl, serviceName, "RESOURCE", "FAILED", "리소스 생성 실패");
            return false;
        }
        notifier.sendServiceLog(repoUrl, serviceName, "RESOURCE", "SUCCESS", "K8s 리소스 생성 확인됨");

        if (!k8sResourcePoller.pollPodStartupStatus(repoUrl, projectName, serviceName)) {
            notifier.sendServiceLog(repoUrl, serviceName, "POD", "FAILED", "Pod 구동 실패 (Timeout)");
            return false;
        }

        notifier.sendServiceLog(repoUrl, serviceName, "INGRESS", "PENDING", "외부 접속 주소(ALB) 할당 대기 중...");

        if (!k8sResourcePoller.pollIngressStatus(repoUrl, projectName, serviceName)) {
            notifier.sendServiceLog(repoUrl, serviceName, "INGRESS", "FAILED", "Ingress 설정 실패");
            return false;
        }
        notifier.sendServiceLog(repoUrl, serviceName, "INGRESS", "SUCCESS", "외부 접속 준비 완료");

        return true;
    }

    private List<sbhackathon.koala.happyMSP.entity.Service> waitForBuildAndArtifacts(String repoUrl) {
        notifier.publish(repoUrl, "stage-1-start", "🏗️ 1단계: 빌드 및 이미지 생성 중...");

        String searchUrl = extractRepoUri(repoUrl);

        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < 600_000) { // 10분
            Optional<Repository> repoOpt = repoRepository.findByUri(searchUrl);

            if (repoOpt.isPresent() && !repoOpt.get().getServices().isEmpty()) {
                Repository repo = repoOpt.get();
                boolean allImagesReady = true;

                for (sbhackathon.koala.happyMSP.entity.Service service : repo.getServices()) {
                    List<Ecr> ecrs = ecrRepository.findByService_Id(service.getId());

                    if (ecrs.isEmpty()) {
                        allImagesReady = false;
                        break;
                    }

                    if (useAwsEcrCheck) {
                        Ecr ecr = ecrs.get(0);
                        if (!awsEcrCheckService.checkImageExists(ecr.getUri(), ecr.getTag())) {
                            allImagesReady = false;
                            log.info("DB 커밋은 확인되었으나 AWS ECR 미발견: {}", ecr.getName());
                            break;
                        }
                    }
                }

                if (allImagesReady) {
                    notifier.publish(repoUrl, "stage-1-success", "✅ 빌드 완료: " + repo.getServices().size() + "개 서비스 이미지 등록됨");
                    return repo.getServices();
                }
            }
            sleep(3000);
        }

        notifier.publish(repoUrl, "stage-1-failed", "❌ 빌드/배포 준비 시간 초과");
        return null;
    }

    private String extractRepoUri(String repoUrl) {
        String searchUrl = repoUrl;
        if (searchUrl.startsWith("https://")) {
            searchUrl = searchUrl.substring(8);
        } else if (searchUrl.startsWith("http://")) {
            searchUrl = searchUrl.substring(7);
        }
        if (searchUrl.endsWith(".git")) {
            searchUrl = searchUrl.substring(0, searchUrl.length() - 4);
        }
        return searchUrl;
    }

    // [수정] 리포지토리 이름 추출 및 K8s 호환성 처리
    private String extractRepositoryName(String repoUrl) {
        String uri = extractRepoUri(repoUrl);
        // github.com/user/repo -> repo
        String[] parts = uri.split("/");
        if (parts.length > 0) {
            String repoName = parts[parts.length - 1];
            // K8s 리소스 이름 규칙: 소문자, 숫자, '-', '.' 만 허용
            // 언더바(_)를 하이픈(-)으로 치환
            return repoName.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
        }
        return "unknown-repo";
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}