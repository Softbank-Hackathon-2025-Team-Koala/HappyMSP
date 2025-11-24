package sbhackathon.koala.happyMSP.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.config.KubernetesConfig;
import sbhackathon.koala.happyMSP.deployment_CD.repository.ServiceRepository;
import sbhackathon.koala.happyMSP.dto.DeploymentRequest;
import sbhackathon.koala.happyMSP.dto.ServiceDeployRequest;
import sbhackathon.koala.happyMSP.infra.KubectlExecutor;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IngressServiceImpl implements IngressService {

    private static final Logger logger = LoggerFactory.getLogger(IngressServiceImpl.class);
    private static final int SERVICE_PORT = 80;

    private final KubectlExecutor kubectlExecutor;
    private final KubernetesConfig kubernetesConfig;
    private final ServiceRepository serviceRepository;

    public IngressServiceImpl(KubectlExecutor kubectlExecutor,
                              KubernetesConfig kubernetesConfig,
                              ServiceRepository serviceRepository) {
        this.kubectlExecutor = kubectlExecutor;
        this.kubernetesConfig = kubernetesConfig;
        this.serviceRepository = serviceRepository;
    }

    @Override
    public void applyIngress(String projectName, List<String> serviceNames) {
        logger.info("Ingress 생성 시작 - 프로젝트: {}, 서비스 개수: {}",
                projectName, serviceNames.size());

        try {
            String ingressYaml = buildIngressYaml(projectName, serviceNames);
            kubectlExecutor.applyYaml(ingressYaml);
            logger.info("Ingress 생성 완료: {}-ingress", projectName);
        } catch (Exception e) {
            String errorMessage = String.format(
                    "Ingress '%s-ingress' 생성 실패: %s",
                    projectName,
                    e.getMessage()
            );
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }

    @Override
    public void applyIngress(DeploymentRequest request) {
        // serviceId로 Service 엔티티를 조회하여 serviceName 리스트 생성
        List<String> serviceNames = request.getServices().stream()
                .map(serviceRequest -> {
                    sbhackathon.koala.happyMSP.entity.Service serviceEntity =
                            serviceRepository.findById(serviceRequest.getServiceId())
                                    .orElseThrow(() -> new RuntimeException("Service not found with id: " + serviceRequest.getServiceId()));
                    return serviceEntity.getName();
                })
                .collect(Collectors.toList());

        applyIngress(request.getProjectName(), serviceNames);
    }

    /**
     * ALB Ingress YAML을 생성합니다.
     */
    private String buildIngressYaml(String projectName, List<String> serviceNames) {
        String ingressName = projectName + "-ingress";
        String namespace = kubernetesConfig.getNamespace();

        StringBuilder yaml = new StringBuilder();

        // Ingress 헤더 및 annotations
        yaml.append(String.format("""
                apiVersion: networking.k8s.io/v1
                kind: Ingress
                metadata:
                  name: %s
                  namespace: %s
                  annotations:
                    kubernetes.io/ingress.class: alb
                    alb.ingress.kubernetes.io/scheme: internet-facing
                    alb.ingress.kubernetes.io/target-type: ip
                spec:
                  rules:
                    - http:
                        paths:
                """, ingressName, namespace));

        // 각 서비스에 대한 path 규칙 추가
        for (String serviceName : serviceNames) {
            String path = "/" + serviceName;

            yaml.append(String.format("""
                          - path: %s
                            pathType: Prefix
                            backend:
                              service:
                                name: %s
                                port:
                                  number: %d
                """, path, serviceName, SERVICE_PORT));
        }

        logger.debug("생성된 Ingress YAML:\n{}", yaml);
        return yaml.toString();
    }
}