package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DeploymentResponseDto {
    private final boolean success;
    private final String message;
    private final String deploymentStatus;
    private final List<String> deployedServices;
    private final String errorMessage;
}