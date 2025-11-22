package sbhackathon.koala.happyMSP.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.deployment_CD.repository.ServiceRepository;
import sbhackathon.koala.happyMSP.dto.DeploymentRequest;
import sbhackathon.koala.happyMSP.dto.ServiceDeployRequest;
import sbhackathon.koala.happyMSP.infra.KubectlExecutor;

@Service
public class K8sDeploymentServiceImpl implements K8sDeploymentService {

    private static final Logger logger = LoggerFactory.getLogger(K8sDeploymentServiceImpl.class);
    private static final int DEFAULT_REPLICAS = 1;
    private static final int DEFAULT_CONTAINER_PORT = 8080;
    private static final int SERVICE_PORT = 80;

    private final KubectlExecutor kubectlExecutor;
    private final K8sSecretService k8sSecretService;
    private final ServiceRepository serviceRepository;

    public K8sDeploymentServiceImpl(KubectlExecutor kubectlExecutor,
                                     K8sSecretService k8sSecretService,
                                     ServiceRepository serviceRepository) {
        this.kubectlExecutor = kubectlExecutor;
        this.k8sSecretService = k8sSecretService;
        this.serviceRepository = serviceRepository;
    }

    @Override
    public void deploy(DeploymentRequest request) {
        logger.info("배포 시작 - 프로젝트: {}, 서비스 개수: {}",
                request.getProjectName(), request.getServices().size());

        // kubectl 사용 가능 여부 확인
        if (!kubectlExecutor.isKubectlAvailable()) {
            throw new RuntimeException("kubectl 명령을 사용할 수 없습니다. kubectl이 설치되어 있고 PATH에 등록되어 있는지 확인하세요.");
        }

        // ECR ImagePullSecret 생성 (자동 생성이 활성화된 경우)
        try {
            k8sSecretService.createOrUpdateImagePullSecret();
        } catch (Exception e) {
            logger.warn("ImagePullSecret 생성 중 오류 발생 (계속 진행): {}", e.getMessage());
        }

        // 각 서비스에 대해 Service 엔티티 조회 및 배포
        for (ServiceDeployRequest serviceRequest : request.getServices()) {
            try {
                // Service 엔티티에서 정보 조회
                sbhackathon.koala.happyMSP.entity.Service serviceEntity = serviceRepository.findById(serviceRequest.getServiceId())
                        .orElseThrow(() -> new RuntimeException("Service not found with id: " + serviceRequest.getServiceId()));

                String serviceName = serviceEntity.getName();
                Integer portNumber = serviceEntity.getPortNumber() != null ? serviceEntity.getPortNumber() : DEFAULT_CONTAINER_PORT;

                logger.info("서비스 배포 중: {} (Port: {})", serviceName, portNumber);

                deployService(request.getProjectName(), serviceRequest, serviceName, portNumber);
                logger.info("서비스 배포 완료: {}", serviceName);
            } catch (Exception e) {
                String errorMessage = String.format(
                        "서비스 ID '%d' 배포 실패: %s",
                        serviceRequest.getServiceId(),
                        e.getMessage()
                );
                logger.error(errorMessage, e);
                throw new RuntimeException(errorMessage, e);
            }
        }

        logger.info("모든 서비스 배포 완료");
    }

    /**
     * 개별 서비스에 대한 Deployment와 Service YAML을 생성하고 kubectl apply를 실행합니다.
     */
    private void deployService(String projectName, ServiceDeployRequest service, String serviceName, int portNumber) {
        String yaml = generateK8sYaml(projectName, service, serviceName, portNumber);
        kubectlExecutor.applyYaml(yaml);
    }

    /**
     * Kubernetes Deployment와 Service YAML을 생성합니다.
     */
    private String generateK8sYaml(String projectName, ServiceDeployRequest service, String serviceName, int portNumber) {
        String deploymentName = projectName + "-" + serviceName;
        String imageUri = service.getImageUri();
        String imagePullSecretName = k8sSecretService.getImagePullSecretName();

        StringBuilder yaml = new StringBuilder();

        // Deployment YAML 생성
        yaml.append("""
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                spec:
                  replicas: %d
                  selector:
                    matchLabels:
                      app: %s
                  template:
                    metadata:
                      labels:
                        app: %s
                    spec:
                      imagePullSecrets:
                        - name: %s
                      containers:
                        - name: %s
                          image: %s
                          ports:
                            - containerPort: %d
                """.formatted(
                deploymentName,
                DEFAULT_REPLICAS,
                serviceName,
                serviceName,
                imagePullSecretName,
                serviceName,
                imageUri,
                portNumber
        ));

        yaml.append("---\n");

        // Service YAML 생성
        yaml.append("""
                apiVersion: v1
                kind: Service
                metadata:
                  name: %s
                spec:
                  type: ClusterIP
                  selector:
                    app: %s
                  ports:
                    - port: %d
                      targetPort: %d
                """.formatted(
                serviceName,
                serviceName,
                SERVICE_PORT,
                portNumber
        ));

        logger.debug("생성된 YAML for {}:\n{}", serviceName, yaml);
        return yaml.toString();
    }
}

