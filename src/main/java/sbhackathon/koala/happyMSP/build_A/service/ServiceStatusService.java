package sbhackathon.koala.happyMSP.build_A.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sbhackathon.koala.happyMSP.build_A.dto.UpdateServiceStatusResponseDto;
import sbhackathon.koala.happyMSP.deployment_CD.repository.ServiceRepository;
import sbhackathon.koala.happyMSP.entity.ServiceStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceStatusService {

    private final ServiceRepository serviceRepository;

    @Transactional
    public UpdateServiceStatusResponseDto updateServiceStatus(Integer serviceId, ServiceStatus newStatus) {
        sbhackathon.koala.happyMSP.entity.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));

        ServiceStatus oldStatus = service.getStatus();
        service.updateStatus(newStatus);
        serviceRepository.save(service);

        log.info("Service status updated: serviceId={}, {} -> {}", serviceId, oldStatus, newStatus);

        return UpdateServiceStatusResponseDto.success(
                service.getId(),
                service.getName(),
                newStatus
        );
    }
}