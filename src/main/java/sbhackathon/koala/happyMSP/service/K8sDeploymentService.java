package sbhackathon.koala.happyMSP.service;

import sbhackathon.koala.happyMSP.dto.DeploymentRequest;

public interface K8sDeploymentService {
    /**
     * 입력받은 서비스 목록을 기반으로 Kubernetes Deployment와 Service를 생성하고 배포합니다.
     *
     * @param request 프로젝트명과 배포할 서비스 목록을 담은 요청 객체
     */
    void deploy(DeploymentRequest request);
}

