package sbhackathon.koala.happyMSP.monitor_B.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.repository.RepoRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementService {

    private final RepoRepository repoRepository;
    private final LogService logService;

    // 1. 파드 재시작
    public void restartPod(String podName) {
        log.info("Restarting Pod: {}", podName);
        executeCommand("kubectl", "delete", "pod", podName);
    }

    // 2. 서비스(Deployment) 전체 재시작
    public void restartService(String repoUrl, String serviceName) {
        String projectName = resolveProjectName(repoUrl);
        String deploymentName = projectName + "-" + serviceName;

        log.info("Rollout Restarting Deployment: {}", deploymentName);
        executeCommand("kubectl", "rollout", "restart", "deployment/" + deploymentName);
    }

    // 3. 스케일링
    public void scaleService(String repoUrl, String serviceName, int replicas) {
        String projectName = resolveProjectName(repoUrl);
        String deploymentName = projectName + "-" + serviceName;

        log.info("Scaling Deployment: {} to {}", deploymentName, replicas);
        executeCommand("kubectl", "scale", "deployment", deploymentName, "--replicas=" + replicas);
    }

    // 4. 로그 가져오기
    public String getLogs(String podName) {
        return logService.getPodLogs(podName, 500);
    }

    // [변경] 프로젝트명 추출 방식 변경
    private String resolveProjectName(String repoUrl) {
        String normalized = normalizeUrl(repoUrl);

        repoRepository.findByUri(normalized)
                .orElseThrow(() -> new RuntimeException("Repository not found"));

        String[] parts = normalized.split("/");
        if (parts.length > 0) {
            String repoName = parts[parts.length - 1];
            // 언더바(_)를 하이픈(-)으로 치환
            return repoName.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
        }
        return "unknown-repo";
    }

    private String normalizeUrl(String repoUrl) {
        String url = repoUrl;
        if (url.startsWith("https://")) url = url.substring(8);
        else if (url.startsWith("http://")) url = url.substring(7);
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        return url;
    }

    private void executeCommand(String... command) {
        try {
            new ProcessBuilder(command).start().waitFor();
        } catch (Exception e) {
            log.error("Command failed: {}", e.getMessage());
        }
    }
}