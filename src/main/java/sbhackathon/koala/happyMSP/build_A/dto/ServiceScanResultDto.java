package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ServiceScanResultDto {
    private final List<ServiceInfo> services;
    
    @Getter
    @Builder
    public static class ServiceInfo {
        private final String name;
        private final String path;
        private final boolean dockerfileExists;
        private final Integer portNumber;
    }
}