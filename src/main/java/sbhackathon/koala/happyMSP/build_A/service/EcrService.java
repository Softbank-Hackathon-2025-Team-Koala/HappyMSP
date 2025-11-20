package sbhackathon.koala.happyMSP.build_A.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.dto.PushResultDto;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.*;

import javax.annotation.PreDestroy;
import java.io.*;
import java.util.Base64;

@Slf4j
@Service
public class EcrService {

    private final EcrClient ecrClient;
    
    @Value("${aws.ecr.region}")
    private String region;

    public EcrService(@Value("${aws.ecr.region}") String region) {
        this.region = region;
        this.ecrClient = EcrClient.builder()
                .region(Region.of(region))
                .build();
    }

    public PushResultDto pushImage(String serviceName, String localImageTag, String registryUri) {
        try {
            String repoName = extractRepoName(serviceName);
            
            ensureRepositoryExists(repoName);
            
            String authToken = getEcrAuthToken();
            
            String fullImageUri = String.format("%s/%s", registryUri, localImageTag);
            
            tagImage(localImageTag, fullImageUri);
            
            loginToEcr(authToken, registryUri);
            
            pushToEcr(fullImageUri);
            
            log.info("Successfully pushed image to ECR: {}", fullImageUri);
            
            return PushResultDto.builder()
                    .service(serviceName)
                    .imageUri(fullImageUri)
                    .success(true)
                    .errorMessage(null)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to push image for service: {}", serviceName, e);
            return PushResultDto.builder()
                    .service(serviceName)
                    .imageUri(null)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public void ensureRepositoryExists(String repoName) {
        try {
            DescribeRepositoriesRequest request = DescribeRepositoriesRequest.builder()
                    .repositoryNames(repoName)
                    .build();
            
            ecrClient.describeRepositories(request);
            log.info("ECR repository exists: {}", repoName);
            
        } catch (RepositoryNotFoundException e) {
            log.info("Creating ECR repository: {}", repoName);
            
            CreateRepositoryRequest createRequest = CreateRepositoryRequest.builder()
                    .repositoryName(repoName)
                    .imageTagMutability(ImageTagMutability.MUTABLE)
                    .imageScanningConfiguration(ImageScanningConfiguration.builder()
                            .scanOnPush(false)
                            .build())
                    .build();
            
            ecrClient.createRepository(createRequest);
            log.info("ECR repository created: {}", repoName);
        }
    }

    private String getEcrAuthToken() {
        try {
            GetAuthorizationTokenRequest request = GetAuthorizationTokenRequest.builder().build();
            GetAuthorizationTokenResponse response = ecrClient.getAuthorizationToken(request);
            
            AuthorizationData authData = response.authorizationData().get(0);
            return authData.authorizationToken();
                    
        } catch (Exception e) {
            throw new RuntimeException("Failed to get ECR auth token: " + e.getMessage(), e);
        }
    }

    private void tagImage(String sourceTag, String targetTag) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "tag", sourceTag, targetTag);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Tagged image: {} -> {}", sourceTag, targetTag);
            } else {
                throw new RuntimeException("Failed to tag image with exit code: " + exitCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to tag image: " + e.getMessage(), e);
        }
    }

    private void loginToEcr(String authToken, String registryUri) {
        try {
            String decodedToken = new String(Base64.getDecoder().decode(authToken));
            String password = decodedToken.split(":")[1];
            
            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "login", "--username", "AWS", "--password-stdin", registryUri
            );
            Process process = processBuilder.start();
            
            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
                writer.write(password);
                writer.flush();
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Successfully logged into ECR");
            } else {
                throw new RuntimeException("Failed to login to ECR with exit code: " + exitCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to login to ECR: " + e.getMessage(), e);
        }
    }

    private void pushToEcr(String imageTag) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "push", imageTag);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("Push output: {}", line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to push image with exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to push image to ECR: " + e.getMessage(), e);
        }
    }

    private String extractRepoName(String serviceName) {
        return serviceName.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
    }

    @PreDestroy
    public void cleanup() {
        try {
            if (ecrClient != null) {
                ecrClient.close();
            }
        } catch (Exception e) {
            log.warn("Failed to close ECR client: {}", e.getMessage());
        }
    }
}