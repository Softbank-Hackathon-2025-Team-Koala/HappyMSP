package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;
import sbhackathon.koala.happyMSP.entity.Repository;

@Getter
@Builder
public class RepositoryStatusDto {
    private final int repoId;
    private final String uri;
    private final String latestCommit;
    private final String deploymentStatus;
    private final String deploymentStatusDescription;

    public static RepositoryStatusDto from(Repository repository) {
        return RepositoryStatusDto.builder()
                .repoId(repository.getRepoId())
                .uri(repository.getUri())
                .latestCommit(repository.getLatestCommit())
                .deploymentStatus(repository.getDeploymentStatus().name())
                .deploymentStatusDescription(repository.getDeploymentStatus().getDescription())
                .build();
    }
}