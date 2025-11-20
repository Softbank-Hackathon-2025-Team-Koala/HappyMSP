package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import sbhackathon.koala.happyMSP.entity.ServiceStatus;

@Getter
@Builder
@AllArgsConstructor
public class UpdateServiceStatusResponseDto {
    private int serviceId;
    private String serviceName;
    private ServiceStatus status;
    private String message;
    
    public static UpdateServiceStatusResponseDto success(int serviceId, String serviceName, ServiceStatus status) {
        return UpdateServiceStatusResponseDto.builder()
                .serviceId(serviceId)
                .serviceName(serviceName)
                .status(status)
                .message("Service status updated successfully")
                .build();
    }
}