package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CloneResultDto {
    private final String projectId;
    private final String repoPath;
    private final String gitSha;
}