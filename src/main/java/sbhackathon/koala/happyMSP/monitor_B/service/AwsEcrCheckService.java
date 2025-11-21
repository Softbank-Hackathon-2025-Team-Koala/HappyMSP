package sbhackathon.koala.happyMSP.monitor_B.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ecr.model.ImageIdentifier;
import software.amazon.awssdk.services.ecr.model.ImageNotFoundException;
import software.amazon.awssdk.services.ecr.model.RepositoryNotFoundException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AwsEcrCheckService {

    @Autowired(required = false)
    private EcrClient ecrClient;

    public boolean checkImageExists(String fullUri, String tag) {
        if (ecrClient == null) {
            return true;
        }

        String repositoryName = extractRepositoryName(fullUri);
        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .repositoryName(repositoryName)
                .imageIds(ImageIdentifier.builder().imageTag(tag).build())
                .build();

        long start = System.currentTimeMillis();
        long timeout = 20_000;

        while (System.currentTimeMillis() - start < timeout) {
            try {
                ecrClient.describeImages(request);
                return true;
            } catch (ImageNotFoundException | RepositoryNotFoundException e) {
            } catch (Exception e) {
                log.warn("AWS ECR API Error: {}", e.getMessage());
                return false;
            }
            sleep(2000);
        }
        return false;
    }

    private String extractRepositoryName(String fullUri) {
        if (fullUri.contains("amazonaws.com/")) {
            return fullUri.split("amazonaws.com/")[1];
        }
        return fullUri;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}