package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PushResultDto {
    private final String service;
    private final String imageUri;
    private final boolean success;
    private final String errorMessage;
}