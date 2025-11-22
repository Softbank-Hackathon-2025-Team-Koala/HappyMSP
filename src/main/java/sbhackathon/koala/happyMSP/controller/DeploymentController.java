package sbhackathon.koala.happyMSP.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sbhackathon.koala.happyMSP.dto.DeploymentRequest;
import sbhackathon.koala.happyMSP.dto.DeploymentResponse;
import sbhackathon.koala.happyMSP.dto.ServiceDeployRequest;
import sbhackathon.koala.happyMSP.service.IngressService;
import sbhackathon.koala.happyMSP.service.K8sDeploymentService;

@RestController
@RequestMapping("/api")
public class DeploymentController {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentController.class);

    private final K8sDeploymentService k8sDeploymentService;
    private final IngressService ingressService;


    public DeploymentController(K8sDeploymentService k8sDeploymentService,
                                IngressService ingressService) {
        this.k8sDeploymentService = k8sDeploymentService;
        this.ingressService = ingressService;
    }

    /**
     * Kubernetes에 서비스를 배포합니다.
     *
     * @param request 배포 요청 (프로젝트명, 서비스 목록)
     * @return 배포 결과
     */
    @PostMapping("/deploy")
    public ResponseEntity<DeploymentResponse> deploy(@RequestBody DeploymentRequest request) {
        logger.info("배포 요청 수신 - 프로젝트: {}, 서비스 개수: {}",
                request.getProjectName(),
                request.getServices() != null ? request.getServices().size() : 0);

        try {
            // 입력 검증
            validateRequest(request);

            // 1. Deployment와 Service 배포 실행
            k8sDeploymentService.deploy(request);

            // 2. Ingress 생성/업데이트
            try {
                logger.info("Ingress 생성 시작 - 프로젝트: {}", request.getProjectName());
                ingressService.applyIngress(request);
                logger.info("Ingress 생성 완료 - 프로젝트: {}", request.getProjectName());
            } catch (Exception e) {
                logger.warn("Ingress 생성 중 오류 발생 (배포는 성공): {}", e.getMessage(), e);
                // Ingress 생성 실패해도 배포는 성공으로 처리
            }

            // 성공 응답 생성
            DeploymentResponse response = new DeploymentResponse(
                    "배포가 성공적으로 완료되었습니다.",
                    "SUCCESS",
                    null
            );

            logger.info("배포 성공 - 프로젝트: {}", request.getProjectName());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("배포 요청 검증 실패: {}", e.getMessage());
            DeploymentResponse response = new DeploymentResponse(
                    "배포 요청이 올바르지 않습니다: " + e.getMessage(),
                    "VALIDATION_ERROR",
                    null
            );
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("배포 중 오류 발생", e);
            DeploymentResponse response = new DeploymentResponse(
                    "배포 중 오류가 발생했습니다: " + e.getMessage(),
                    "ERROR",
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Ingress만 생성하거나 업데이트합니다.
     *
     * @param request 배포 요청 (프로젝트명, 서비스 목록)
     * @return Ingress 생성 결과
     */
    @PostMapping("/ingress")
    public ResponseEntity<DeploymentResponse> createIngress(@RequestBody DeploymentRequest request) {
        logger.info("Ingress 생성 요청 수신 - 프로젝트: {}, 서비스 개수: {}",
                request.getProjectName(),
                request.getServices() != null ? request.getServices().size() : 0);

        try {
            // 입력 검증
            validateRequest(request);

            // Ingress 생성/업데이트
            ingressService.applyIngress(request);

            // 성공 응답 생성
            DeploymentResponse response = new DeploymentResponse(
                    "Ingress가 성공적으로 생성되었습니다.",
                    "SUCCESS",
                    null
            );

            logger.info("Ingress 생성 성공 - 프로젝트: {}", request.getProjectName());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Ingress 생성 요청 검증 실패: {}", e.getMessage());
            DeploymentResponse response = new DeploymentResponse(
                    "Ingress 생성 요청이 올바르지 않습니다: " + e.getMessage(),
                    "VALIDATION_ERROR",
                    null
            );
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            logger.error("Ingress 생성 중 오류 발생", e);
            DeploymentResponse response = new DeploymentResponse(
                    "Ingress 생성 중 오류가 발생했습니다: " + e.getMessage(),
                    "ERROR",
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 배포 요청의 유효성을 검증합니다.
     */
    private void validateRequest(DeploymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 비어있습니다.");
        }

        if (request.getProjectName() == null || request.getProjectName().trim().isEmpty()) {
            throw new IllegalArgumentException("프로젝트명(projectName)은 필수입니다.");
        }

        if (request.getServices() == null || request.getServices().isEmpty()) {
            throw new IllegalArgumentException("배포할 서비스 목록(services)이 비어있습니다.");
        }

        for (int i = 0; i < request.getServices().size(); i++) {
            var service = request.getServices().get(i);
            if (service.getServiceId() <= 0) {
                throw new IllegalArgumentException(
                        String.format("서비스[%d]의 serviceId가 올바르지 않습니다.", i)
                );
            }
            if (service.getImageUri() == null || service.getImageUri().trim().isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("서비스[%d](ID: %d)의 imageUri가 비어있습니다.",
                                i, service.getServiceId())
                );
            }
        }
    }
}

