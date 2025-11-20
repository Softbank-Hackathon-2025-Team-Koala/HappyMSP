package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BuildResultDto {
    private final String serviceName;
    private final String imageId;
    private final String imageTag;
    private final boolean success;
    private final String buildLog;
}