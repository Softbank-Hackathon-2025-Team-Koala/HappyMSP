package sbhackathon.koala.happyMSP.service;

import sbhackathon.koala.happyMSP.dto.DeploymentRequest;

import java.util.List;

public interface IngressService {
    /**
     * ALB Ingress를 생성하거나 업데이트합니다.
     *
     * @param projectName 프로젝트 이름
     * @param serviceNames 서비스 이름 리스트
     */
    void applyIngress(String projectName, List<String> serviceNames);

    /**
     * DeploymentRequest를 기반으로 ALB Ingress를 생성하거나 업데이트합니다.
     *
     * @param request 배포 요청 객체
     */
    void applyIngress(DeploymentRequest request);
}

