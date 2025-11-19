package sbhackathon.koala.happyMSP.service;

import org.junit.jupiter.api.Test;
import sbhackathon.koala.happyMSP.config.KubernetesConfig;
import sbhackathon.koala.happyMSP.infra.KubectlExecutor;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IngressYamlTest {

    @Test
    void Ingress_YAML_형식_확인() {
        // given
        KubectlExecutor kubectlExecutor = mock(KubectlExecutor.class);
        KubernetesConfig kubernetesConfig = mock(KubernetesConfig.class);
        when(kubernetesConfig.getNamespace()).thenReturn("default");

        IngressServiceImpl ingressService = new IngressServiceImpl(kubectlExecutor, kubernetesConfig);

        String projectName = "msa-demo";
        List<String> serviceNames = Arrays.asList("apigateway", "auth", "user");

        doNothing().when(kubectlExecutor).applyYaml(anyString());

        // when
        ingressService.applyIngress(projectName, serviceNames);

        // then
        ArgumentCaptor<String> yamlCaptor = ArgumentCaptor.forClass(String.class);
        verify(kubectlExecutor, times(1)).applyYaml(yamlCaptor.capture());

        String capturedYaml = yamlCaptor.getValue();

        System.out.println("=== 생성된 Ingress YAML ===");
        System.out.println(capturedYaml);
        System.out.println("========================");

        // 기본 구조 확인
        assertThat(capturedYaml).contains("apiVersion: networking.k8s.io/v1");
        assertThat(capturedYaml).contains("kind: Ingress");
        assertThat(capturedYaml).contains("name: msa-demo-ingress");

        // paths 확인
        assertThat(capturedYaml).contains("paths:");
        assertThat(capturedYaml).contains("- path: /apigateway");
        assertThat(capturedYaml).contains("- path: /auth");
        assertThat(capturedYaml).contains("- path: /user");

        // YAML 파싱 가능 여부 확인 (줄바꿈이 제대로 되어 있는지)
        String[] lines = capturedYaml.split("\n");
        for (int i = 0; i < lines.length; i++) {
            System.out.println(String.format("Line %2d: [%s]", i + 1, lines[i]));
        }
    }
}

