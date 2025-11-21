package sbhackathon.koala.happyMSP.monitor_B.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.repository.RepoRepository;
import sbhackathon.koala.happyMSP.entity.Repository;
import sbhackathon.koala.happyMSP.infra.KubectlExecutor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementService {

    private final RepoRepository repoRepository;
    private final LogService logService;

    // 1. 파드 재시작 (실제로는 삭제 후 재생성)
    public void restartPod(String podName) {
        log.info("Restarting Pod: {}", podName);
        // kubectl delete pod {podName} (K8s가 알아서 새 파드를 띄움)
        executeCommand("kubectl", "delete", "pod", podName);
    }

    // 2. 서비스(Deployment) 전체 재시작 (Rollout Restart)
    public void restartService(String repoUrl, String serviceName) {
        String projectName = resolveProjectName(repoUrl);
        String deploymentName = projectName + "-" + serviceName;

        log.info("Rollout Restarting Deployment: {}", deploymentName);
        // kubectl rollout restart deployment/{name}
        executeCommand("kubectl", "rollout", "restart", "deployment/" + deploymentName);
    }

    // 3. 스케일링 (파드 개수 조절)
    public void scaleService(String repoUrl, String serviceName, int replicas) {
        String projectName = resolveProjectName(repoUrl);
        String deploymentName = projectName + "-" + serviceName;

        log.info("Scaling Deployment: {} to {}", deploymentName, replicas);
        // kubectl scale deployment {name} --replicas={n}
        executeCommand("kubectl", "scale", "deployment", deploymentName, "--replicas=" + replicas);
    }

    // 4. 로그 가져오기 (기존 로직 활용)
    public String getLogs(String podName) {
        return logService.getPodLogs(podName, 500);
    }


    private String resolveProjectName(String repoUrl) {
        String normalized = normalizeUrl(repoUrl);
        Repository repo = repoRepository.findByUri(normalized)
                .orElseThrow(() -> new RuntimeException("Repository not found"));
        return "project-" + repo.getRepoId();
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