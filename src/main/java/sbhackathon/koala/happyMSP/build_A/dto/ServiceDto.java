package sbhackathon.koala.happyMSP.build_A.dto;

import lombok.Builder;
import lombok.Getter;
import sbhackathon.koala.happyMSP.entity.Service;
import sbhackathon.koala.happyMSP.entity.ServiceStatus;

@Getter
@Builder
public class ServiceDto {
    private final int serviceId;
    private final String serviceName;
    private final String address;
    private final ServiceStatus status;

    public static ServiceDto from(Service service) {
        return ServiceDto.builder()
                .serviceId(service.getId())
                .serviceName(service.getName())
                .address(service.getAddress())
                .status(service.getStatus())
                .build();
    }
}