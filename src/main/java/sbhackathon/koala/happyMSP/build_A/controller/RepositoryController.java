package sbhackathon.koala.happyMSP.build_A.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sbhackathon.koala.happyMSP.build_A.dto.GetRepositoryResponseDto;
import sbhackathon.koala.happyMSP.build_A.dto.PostRepositoryRequestDto;
import sbhackathon.koala.happyMSP.build_A.dto.PostRepositoryResponseDto;
import sbhackathon.koala.happyMSP.build_A.service.BuildService;

@Slf4j
@RestController
@RequestMapping("/repository")
@RequiredArgsConstructor
public class RepositoryController {

    private final BuildService buildService;

    @GetMapping("/{repoUrl}")
    public ResponseEntity<GetRepositoryResponseDto> getRepositoryStatus(@PathVariable("repoUrl") String repoUrl) {
        try {
            log.info("Getting repository status for URL: {}", repoUrl);
            GetRepositoryResponseDto response = buildService.getRepositoryStatus(repoUrl);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting repository status for URL {}: {}", repoUrl, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping
    public ResponseEntity<PostRepositoryResponseDto> requestDeployment(@RequestBody PostRepositoryRequestDto request) {
        try {
            log.info("Deployment request: url={}", request.getRepositoryUrl());
            
            if (request.getRepositoryUrl() == null || request.getRepositoryUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            PostRepositoryResponseDto response = buildService.requestDeployment(request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing deployment request: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}