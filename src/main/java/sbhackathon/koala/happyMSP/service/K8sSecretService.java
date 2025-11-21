package sbhackathon.koala.happyMSP.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.config.KubernetesConfig;
import sbhackathon.koala.happyMSP.infra.KubectlExecutor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class K8sSecretService {

    private static final Logger logger = LoggerFactory.getLogger(K8sSecretService.class);

    private final KubectlExecutor kubectlExecutor;
    private final EcrAuthService ecrAuthService;
    private final KubernetesConfig kubernetesConfig;
    private final ObjectMapper objectMapper;

    public K8sSecretService(KubectlExecutor kubectlExecutor,
                            EcrAuthService ecrAuthService,
                            KubernetesConfig kubernetesConfig) {
        this.kubectlExecutor = kubectlExecutor;
        this.ecrAuthService = ecrAuthService;
        this.kubernetesConfig = kubernetesConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * ECR 인증을 위한 Kubernetes Secret을 생성합니다.
     */
    public void createOrUpdateImagePullSecret() {
        if (!kubernetesConfig.getImagePullSecret().isAutoCreate()) {
            logger.info("ImagePullSecret 자동 생성이 비활성화되어 있습니다.");
            return;
        }

        try {
            logger.info("ImagePullSecret 생성 시작...");

            // ECR 인증 토큰 획득
            EcrAuthService.EcrAuthToken authToken = ecrAuthService.getAuthorizationToken();

            // Docker config.json 생성
            String dockerConfigJson = createDockerConfigJson(
                    authToken.getServer(),
                    authToken.getUsername(),
                    authToken.getPassword()
            );

            // Base64 인코딩
            String encodedDockerConfig = Base64.getEncoder()
                    .encodeToString(dockerConfigJson.getBytes(StandardCharsets.UTF_8));

            // Secret YAML 생성
            String secretYaml = generateSecretYaml(
                    kubernetesConfig.getImagePullSecret().getName(),
                    kubernetesConfig.getNamespace(),
                    encodedDockerConfig
            );

            // kubectl apply
            kubectlExecutor.applyYaml(secretYaml);

            logger.info("ImagePullSecret 생성 완료: {}", kubernetesConfig.getImagePullSecret().getName());

        } catch (Exception e) {
            String errorMessage = "ImagePullSecret 생성 실패: " + e.getMessage();
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * Docker config.json 형식의 JSON 문자열을 생성합니다.
     */
    private String createDockerConfigJson(String server, String username, String password) {
        try {
            // auth = base64(username:password)
            String auth = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

            Map<String, Object> authConfig = new HashMap<>();
            authConfig.put("username", username);
            authConfig.put("password", password);
            authConfig.put("auth", auth);

            Map<String, Object> auths = new HashMap<>();
            auths.put(server, authConfig);

            Map<String, Object> dockerConfig = new HashMap<>();
            dockerConfig.put("auths", auths);

            return objectMapper.writeValueAsString(dockerConfig);

        } catch (Exception e) {
            throw new RuntimeException("Docker config.json 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Kubernetes Secret YAML을 생성합니다.
     */
    private String generateSecretYaml(String secretName, String namespace, String encodedDockerConfig) {
        return String.format("""
                apiVersion: v1
                kind: Secret
                metadata:
                  name: %s
                  namespace: %s
                type: kubernetes.io/dockerconfigjson
                data:
                  .dockerconfigjson: %s
                """, secretName, namespace, encodedDockerConfig);
    }

    /**
     * Secret 이름을 반환합니다.
     */
    public String getImagePullSecretName() {
        return kubernetesConfig.getImagePullSecret().getName();
    }
}

