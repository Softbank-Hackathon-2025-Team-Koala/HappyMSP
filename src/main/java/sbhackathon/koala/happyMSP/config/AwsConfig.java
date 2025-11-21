package sbhackathon.koala.happyMSP.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.access.key.id}")
    private String awsAccessKeyId;

    @Value("${aws.secret.access.key}")
    private String awsSecretAccessKey;

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        // AWS Access Key가 설정되어 있으면 사용, 없으면 DefaultCredentialsProvider 사용
        // (EC2 IAM Role, ECS Task Role, 환경변수 등에서 자동으로 가져옴)
        if (awsAccessKeyId != null && !awsAccessKeyId.isEmpty()
                && awsSecretAccessKey != null && !awsSecretAccessKey.isEmpty()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
            );
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public EcrClient ecrClient(AwsCredentialsProvider credentialsProvider) {
        return EcrClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}

