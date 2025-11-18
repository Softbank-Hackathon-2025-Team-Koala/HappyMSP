package sbhackathon.koala.happyMSP.build_A.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sbhackathon.koala.happyMSP.build_A.dto.DeploymentRequestDto;
import sbhackathon.koala.happyMSP.build_A.dto.DeploymentResponseDto;
import sbhackathon.koala.happyMSP.build_A.dto.RepositoryStatusDto;
import sbhackathon.koala.happyMSP.build_A.service.BuildService;

@Slf4j
@RestController
@RequestMapping("/repository")
@RequiredArgsConstructor
public class RepositoryController {

    private final BuildService buildService;

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryStatusDto> getRepositoryStatus(@PathVariable("id") int repositoryId) {
        try {
            log.info("Getting repository status for id: {}", repositoryId);
            RepositoryStatusDto status = buildService.getRepositoryStatus(repositoryId);
            return ResponseEntity.ok(status);
        } catch (RuntimeException e) {
            log.error("Repository not found with id: {}", repositoryId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting repository status for id {}: {}", repositoryId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<DeploymentResponseDto> requestDeployment(@RequestBody DeploymentRequestDto request) {
        try {
            log.info("Deployment request: url={}", request.getRepositoryUrl());
            
            if (request.getRepositoryUrl() == null || request.getRepositoryUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    DeploymentResponseDto.builder()
                        .success(false)
                        .message("Repository URL is required")
                        .errorMessage("Invalid repository URL")
                        .build()
                );
            }
            
            DeploymentResponseDto response = buildService.requestDeployment(request);
            
            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("Error processing deployment request: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                DeploymentResponseDto.builder()
                    .success(false)
                    .message("Internal server error")
                    .errorMessage(e.getMessage())
                    .build()
            );
        }
    }
}