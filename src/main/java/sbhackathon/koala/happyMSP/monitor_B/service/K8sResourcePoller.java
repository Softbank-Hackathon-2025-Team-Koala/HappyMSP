package sbhackathon.koala.happyMSP.monitor_B.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Ingress;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Config;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class K8sResourcePoller {

    private final PipelineNotificationService notifier;
    private static final String NAMESPACE = "default";

    @PostConstruct
    public void initK8sClient() {
        try {
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);
            log.info("Kubernetes Java Client가 성공적으로 설정되었습니다.");
        } catch (IOException e) {
            log.error("Kubernetes Client 초기화 실패 (Kubeconfig를 찾을 수 없거나 권한 문제)", e);
        }
    }

    // [STEP 2] K8s리소스 생성확인
    public boolean pollK8sResourceCreation(String repoUrl, String projectName, String serviceName) {
        try {
            AppsV1Api appsApi = new AppsV1Api();
            CoreV1Api coreApi = new CoreV1Api();

            // [중요] 배포규칙에 맞게 이름 생성
            String deploymentName = projectName + "-" + serviceName; // 예: project-1-was
            String k8sServiceName = serviceName;                     // 예: was (Service는 prefix 없음)

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 60_000) {
                try {
                    V1Deployment deploy = appsApi.readNamespacedDeployment(deploymentName, NAMESPACE).execute();
                    V1Service svc = coreApi.readNamespacedService(k8sServiceName, NAMESPACE).execute();

                    if (deploy != null && svc != null) {
                        return true;
                    }
                } catch (ApiException e) {
                    if (e.getCode() != 404) {
                        log.warn("Resource Check Error: {}", e.getMessage());
                    }
                }
                sleep(1000);
            }
        } catch (Exception e) {
            log.error("K8s Client Init Error", e);
        }
        return false;
    }

    // [STEP 3] Pod 구동상태 상세 모니터링
    public boolean pollPodStartupStatus(String repoUrl, String projectName, String serviceName) {
        try {
            AppsV1Api api = new AppsV1Api();
            // [중요] 배포 규칙에 맞게 이름 생성
            String deploymentName = projectName + "-" + serviceName; // 예: project-1-was

            long start = System.currentTimeMillis();
            String lastStatus = "";

            while (System.currentTimeMillis() - start < 300_000) {
                try {
                    V1Deployment deployment = api.readNamespacedDeployment(deploymentName, NAMESPACE).execute();

                    if (deployment != null && deployment.getStatus() != null) {
                        int desired = deployment.getSpec().getReplicas() != null ? deployment.getSpec().getReplicas() : 1;
                        int ready = deployment.getStatus().getReadyReplicas() != null ? deployment.getStatus().getReadyReplicas() : 0;
                        int available = deployment.getStatus().getAvailableReplicas() != null ? deployment.getStatus().getAvailableReplicas() : 0;
                        int updated = deployment.getStatus().getUpdatedReplicas() != null ? deployment.getStatus().getUpdatedReplicas() : 0;

                        if (ready >= desired && desired > 0) {
                            notifier.sendServiceLog(repoUrl, serviceName, "POD", "SUCCESS", "Pod is running normally (Ready)");
                            return true;
                        }

                        if (updated < desired) {
                            lastStatus = updateStatus(repoUrl, serviceName, "SCALING", "Requesting Pod creation...", lastStatus);
                        } else if (available < desired) {
                            lastStatus = updateStatus(repoUrl, serviceName, "PULLING", "Downloading image and starting container...", lastStatus);
                        } else {
                            lastStatus = updateStatus(repoUrl, serviceName, "RUNNING", "Initializing application...", lastStatus);
                        }
                    }
                } catch (ApiException ignored) {
                }
                sleep(2000);
            }
        } catch (Exception e) {
            log.error("Pod Monitor Error", e);
        }
        return false;
    }

    // [STEP 4] Ingress (ALB) 확인
    public boolean pollIngressStatus(String repoUrl, String projectName, String serviceName) {
        try {
            NetworkingV1Api api = new NetworkingV1Api();

            // [중요] Ingress 이름은 프로젝트 단위 (project-1-ingress)
            String targetIngressName = projectName + "-ingress";

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 180_000) {
                try {
                    V1Ingress ingress = api.readNamespacedIngress(targetIngressName, NAMESPACE).execute();
                    if (ingress != null && ingress.getStatus() != null
                            && ingress.getStatus().getLoadBalancer() != null
                            && ingress.getStatus().getLoadBalancer().getIngress() != null
                            && !ingress.getStatus().getLoadBalancer().getIngress().isEmpty()) {

                        String albUrl = ingress.getStatus().getLoadBalancer().getIngress().get(0).getHostname();
                        notifier.sendServiceLog(repoUrl, serviceName, "INGRESS", "INFO", "Access address secured: " + albUrl);
                        return true;
                    }
                } catch (ApiException e) {
                    if (e.getCode() != 404) log.warn("Ingress Check Warn: {}", e.getMessage());
                }
                sleep(5000);
            }
        } catch (Exception e) {
            log.error("Ingress Check Failed", e);
        }
        return false;
    }

    public String getIngressUrl(String projectName) {
        try {
            NetworkingV1Api api = new NetworkingV1Api();
            String targetIngressName = projectName + "-ingress"; // 생성 규칙에 맞춤

            V1Ingress ingress = api.readNamespacedIngress(targetIngressName, NAMESPACE).execute();

            if (ingress != null && ingress.getStatus() != null
                    && ingress.getStatus().getLoadBalancer() != null
                    && ingress.getStatus().getLoadBalancer().getIngress() != null
                    && !ingress.getStatus().getLoadBalancer().getIngress().isEmpty()) {

                // AWS ALB 주소 반환 (http:// 붙여서)
                return "http://" + ingress.getStatus().getLoadBalancer().getIngress().get(0).getHostname();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch Ingress URL for project: {}", projectName, e);
        }
        return null; // 주소를 찾지 못한 경우
    }

    private String updateStatus(String repoUrl, String serviceName, String status, String msg, String lastStatus) {
        if (!status.equals(lastStatus)) {
            notifier.sendServiceLog(repoUrl, serviceName, "POD", status, msg);
            return status;
        }
        return lastStatus;
    }


    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}