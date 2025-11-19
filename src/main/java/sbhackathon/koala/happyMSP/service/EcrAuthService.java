package sbhackathon.koala.happyMSP.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.AuthorizationData;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenRequest;
import software.amazon.awssdk.services.ecr.model.GetAuthorizationTokenResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class EcrAuthService {

    private static final Logger logger = LoggerFactory.getLogger(EcrAuthService.class);

    private final EcrClient ecrClient;

    @Value("${aws.ecr.registry}")
    private String ecrRegistry;

    public EcrAuthService(EcrClient ecrClient) {
        this.ecrClient = ecrClient;
    }

    /**
     * ECR 인증 토큰을 가져옵니다.
     *
     * @return ECR 인증 토큰 (username:password 형식)
     */
    public EcrAuthToken getAuthorizationToken() {
        try {
            logger.info("ECR 인증 토큰 요청 중...");

            GetAuthorizationTokenRequest request = GetAuthorizationTokenRequest.builder().build();
            GetAuthorizationTokenResponse response = ecrClient.getAuthorizationToken(request);

            if (response.authorizationData().isEmpty()) {
                throw new RuntimeException("ECR 인증 토큰을 가져올 수 없습니다.");
            }

            AuthorizationData authData = response.authorizationData().get(0);
            String encodedToken = authData.authorizationToken();

            // Base64 디코딩하여 username:password 추출
            String decodedToken = new String(
                    Base64.getDecoder().decode(encodedToken),
                    StandardCharsets.UTF_8
            );

            String[] parts = decodedToken.split(":", 2);
            if (parts.length != 2) {
                throw new RuntimeException("ECR 인증 토큰 형식이 올바르지 않습니다.");
            }

            logger.info("ECR 인증 토큰 획득 성공");
            return new EcrAuthToken(parts[0], parts[1], authData.proxyEndpoint());

        } catch (Exception e) {
            String errorMessage = "ECR 인증 토큰 획득 실패: " + e.getMessage();
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * ECR 레지스트리 주소를 반환합니다.
     */
    public String getEcrRegistry() {
        return ecrRegistry;
    }

    /**
     * ECR 인증 정보를 담는 DTO
     */
    public static class EcrAuthToken {
        private final String username;
        private final String password;
        private final String server;

        public EcrAuthToken(String username, String password, String server) {
            this.username = username;
            this.password = password;
            this.server = server;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getServer() {
            return server;
        }
    }
}

