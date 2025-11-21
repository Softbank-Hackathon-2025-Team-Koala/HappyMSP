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
            return true; // 로컬 테스트 등 ECR 클라이언트가 없으면 Pass
        }

        // [수정] URI에서 순수 리포지토리 이름만 추출 (태그 제거)
        String repositoryName = extractRepositoryName(fullUri);

        // [수정] 입력된 태그가 "repo:tag" 형식이면 뒤쪽 실제 태그만 추출
        String realTag = extractRealTag(tag);

        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .repositoryName(repositoryName)
                .imageIds(ImageIdentifier.builder().imageTag(realTag).build())
                .build();

        long start = System.currentTimeMillis();
        long timeout = 20_000; // 20초 대기

        while (System.currentTimeMillis() - start < timeout) {
            try {
                ecrClient.describeImages(request);
                return true; // 이미지 존재 확인됨
            } catch (ImageNotFoundException | RepositoryNotFoundException e) {
                // 아직 이미지가 등록되지 않았거나 리포지토리가 없는 경우 -> 재시도
            } catch (Exception e) {
                log.warn("AWS ECR API Error: {}", e.getMessage());
                // 권한 문제나 잘못된 파라미터 등 API 호출 자체 에러는 재시도해도 안 될 가능성이 높음
                return false;
            }
            sleep(2000);
        }
        return false;
    }

    private String extractRepositoryName(String fullUri) {
        String repoName = fullUri;
        // 1. 도메인 제거
        if (fullUri.contains("amazonaws.com/")) {
            repoName = fullUri.split("amazonaws.com/")[1];
        }
        // 2. 태그(:)가 붙어있다면 제거 (순수 리포지토리 이름만 남김)
        if (repoName.contains(":")) {
            repoName = repoName.split(":")[0];
        }
        return repoName;
    }

    private String extractRealTag(String tag) {
        // "repo-name:tag" 형식에서 ":" 뒤의 값만 추출
        if (tag != null && tag.contains(":")) {
            return tag.substring(tag.lastIndexOf(":") + 1);
        }
        return tag;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}