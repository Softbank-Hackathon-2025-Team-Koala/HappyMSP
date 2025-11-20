package sbhackathon.koala.happyMSP.build_A.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sbhackathon.koala.happyMSP.build_A.dto.UpdateServiceStatusRequestDto;
import sbhackathon.koala.happyMSP.build_A.dto.UpdateServiceStatusResponseDto;
import sbhackathon.koala.happyMSP.build_A.service.ServiceStatusService;

@Slf4j
@RestController
@RequestMapping("/service")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceStatusService serviceStatusService;

    @PatchMapping("/{serviceId}/status")
    public ResponseEntity<UpdateServiceStatusResponseDto> updateServiceStatus(
            @PathVariable("serviceId") Integer serviceId,
            @RequestBody UpdateServiceStatusRequestDto request) {
        try {
            log.info("Updating service status: serviceId={}, status={}", serviceId, request.getStatus());
            
            if (request.getStatus() == null) {
                return ResponseEntity.badRequest().build();
            }
            
            UpdateServiceStatusResponseDto response = serviceStatusService.updateServiceStatus(serviceId, request.getStatus());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error updating service status: serviceId={}, error={}", serviceId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}