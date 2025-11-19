package sbhackathon.koala.happyMSP.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sbhackathon.koala.happyMSP.config.KubernetesConfig;
import sbhackathon.koala.happyMSP.dto.DeploymentRequest;
import sbhackathon.koala.happyMSP.dto.ServiceDeployRequest;
import sbhackathon.koala.happyMSP.infra.KubectlExecutor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngressServiceImplTest {

    @Mock
    private KubectlExecutor kubectlExecutor;

    @Mock
    private KubernetesConfig kubernetesConfig;

    private IngressServiceImpl ingressService;

    @BeforeEach
    void setUp() {
        when(kubernetesConfig.getNamespace()).thenReturn("default");
        ingressService = new IngressServiceImpl(kubectlExecutor, kubernetesConfig);
    }

    @Test
    void applyIngress_서비스리스트로_Ingress_생성() {
        // given
        String projectName = "msa-demo";
        List<String> serviceNames = Arrays.asList("apigateway", "auth", "user");

        doNothing().when(kubectlExecutor).applyYaml(anyString());

        // when
        ingressService.applyIngress(projectName, serviceNames);

        // then
        ArgumentCaptor<String> yamlCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubectlExecutor, times(1)).applyYaml(yamlCaptor.capture());

        String capturedYaml = yamlCaptor.getValue();

        // Ingress 기본 구조 확인
        assertThat(capturedYaml).contains("apiVersion: networking.k8s.io/v1");
        assertThat(capturedYaml).contains("kind: Ingress");
        assertThat(capturedYaml).contains("name: msa-demo-ingress");
        assertThat(capturedYaml).contains("namespace: default");

        // Annotations 확인
        assertThat(capturedYaml).contains("kubernetes.io/ingress.class: alb");
        assertThat(capturedYaml).contains("alb.ingress.kubernetes.io/scheme: internet-facing");
        assertThat(capturedYaml).contains("alb.ingress.kubernetes.io/target-type: ip");

        // Path 규칙 확인
        assertThat(capturedYaml).contains("path: /apigateway");
        assertThat(capturedYaml).contains("path: /auth");
        assertThat(capturedYaml).contains("path: /user");

        // Service 백엔드 확인
        assertThat(capturedYaml).contains("name: apigateway");
        assertThat(capturedYaml).contains("name: auth");
        assertThat(capturedYaml).contains("name: user");
        assertThat(capturedYaml).contains("number: 80");
    }

    @Test
    void applyIngress_DeploymentRequest로_Ingress_생성() {
        // given
        DeploymentRequest request = new DeploymentRequest(
                "test-project",
                Arrays.asList(
                        new ServiceDeployRequest("service1", "image1:latest"),
                        new ServiceDeployRequest("service2", "image2:latest")
                )
        );

        doNothing().when(kubectlExecutor).applyYaml(anyString());

        // when
        ingressService.applyIngress(request);

        // then
        ArgumentCaptor<String> yamlCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubectlExecutor, times(1)).applyYaml(yamlCaptor.capture());

        String capturedYaml = yamlCaptor.getValue();

        assertThat(capturedYaml).contains("name: test-project-ingress");
        assertThat(capturedYaml).contains("path: /service1");
        assertThat(capturedYaml).contains("path: /service2");
        assertThat(capturedYaml).contains("name: service1");
        assertThat(capturedYaml).contains("name: service2");
    }

    @Test
    void applyIngress_단일_서비스() {
        // given
        String projectName = "single-app";
        List<String> serviceNames = Arrays.asList("api");

        doNothing().when(kubectlExecutor).applyYaml(anyString());

        // when
        ingressService.applyIngress(projectName, serviceNames);

        // then
        ArgumentCaptor<String> yamlCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubectlExecutor, times(1)).applyYaml(yamlCaptor.capture());

        String capturedYaml = yamlCaptor.getValue();

        assertThat(capturedYaml).contains("name: single-app-ingress");
        assertThat(capturedYaml).contains("path: /api");
        assertThat(capturedYaml).contains("name: api");
    }
}

