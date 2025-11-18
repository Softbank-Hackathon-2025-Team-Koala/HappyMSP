package sbhackathon.koala.happyMSP.build_A.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sbhackathon.koala.happyMSP.build_A.dto.*;
import sbhackathon.koala.happyMSP.entity.Repository;
import sbhackathon.koala.happyMSP.build_A.repository.repoRepository;
import sbhackathon.koala.happyMSP.build_A.util.ImageTagGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final GitService gitService;
    private final ServiceScanner serviceScanner;
    private final DockerService dockerService;
    private final EcrService ecrService;
    private final ImageTagGenerator imageTagGenerator;
    private final repoRepository repositoryRepo;

    @Value("${build.workspace.path}")
    private String workspacePath;

    @Value("${aws.ecr.registry.uri}")
    private String ecrRegistryUri;

    @Transactional(readOnly = true)
    public RepositoryStatusDto getRepositoryStatus(int repositoryId) {
        Optional<Repository> repository = repositoryRepo.findById(repositoryId);
        if (repository.isEmpty()) {
            throw new RuntimeException("Repository not found with id: " + repositoryId);
        }
        return RepositoryStatusDto.from(repository.get());
    }

    @Transactional
    public DeploymentResponseDto requestDeployment(DeploymentRequestDto request) {
        String tempProjectId = "temp-" + System.currentTimeMillis();
        try {
            // Normalize GitHub URL
            String normalizedUrl = gitService.normalizeGitUrl(request.getRepositoryUrl());
            
            // Clone temporarily to get latest commit
            CloneResultDto cloneResult = gitService.cloneRepository(request.getRepositoryUrl(), tempProjectId);
            String latestCommit = cloneResult.getGitSha();
            
            // Find repository by normalized URL
            Optional<Repository> repositoryOpt = repositoryRepo.findByUri(normalizedUrl);
            Repository repository;
            
            if (repositoryOpt.isPresent()) {
                repository = repositoryOpt.get();
                
                if (latestCommit.equals(repository.getLatestCommit())) {
                    return DeploymentResponseDto.builder()
                            .success(false)
                            .message("No changes detected. Deployment skipped.")
                            .deploymentStatus(repository.getDeploymentStatus().getDescription())
                            .deployedServices(List.of())
                            .build();
                }
                
                repository.updateLatestCommit(latestCommit);
                repository.updateDeploymentStatus(Repository.DeploymentStatus.DEPLOYING);
            } else {
                repository = Repository.builder()
                        .uri(normalizedUrl)
                        .latestCommit(latestCommit)
                        .build();
                repository.updateDeploymentStatus(Repository.DeploymentStatus.DEPLOYING);
                repository = repositoryRepo.save(repository);
            }
            
            repositoryRepo.save(repository);
            
            startAsyncDeployment(repository.getRepoId(), request.getRepositoryUrl(), latestCommit);
            
            return DeploymentResponseDto.builder()
                    .success(true)
                    .message("Deployment started")
                    .deploymentStatus("배포중")
                    .deployedServices(List.of())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to start deployment for repository: {}", e.getMessage());
            return DeploymentResponseDto.builder()
                    .success(false)
                    .message("Failed to start deployment")
                    .deploymentStatus("배포실패")
                    .errorMessage(e.getMessage())
                    .build();
        } finally {
            // Clean up temporary clone directory
            cleanupTempDirectory(tempProjectId);
        }
    }

    @Async("buildTaskExecutor")
    @Transactional
    public CompletableFuture<Void> startAsyncDeployment(int repositoryId, String repositoryUrl, String latestCommit) {
        try {
            log.info("Starting async deployment for repository {}", repositoryId);
            
            String projectId = "project-" + repositoryId;
            
            CloneResultDto cloneResult = gitService.cloneRepository(repositoryUrl, projectId);
            log.info("Git clone completed: {}", cloneResult.getGitSha());
            
            ServiceScanResultDto scanResult = serviceScanner.scanServices(cloneResult.getRepoPath());
            log.info("Service scan completed. Found {} services", scanResult.getServices().size());
            
            List<String> deployedServices = new ArrayList<>();
            
            for (ServiceScanResultDto.ServiceInfo serviceInfo : scanResult.getServices()) {
                try {
                    String imageTag = imageTagGenerator.generate(projectId, serviceInfo.getName(), cloneResult.getGitSha());
                    
                    BuildResultDto buildResult = dockerService.buildImage(
                            serviceInfo.getName(), 
                            serviceInfo.getPath(), 
                            imageTag
                    );
                    
                    if (buildResult.isSuccess()) {
                        String registryUri = ecrRegistryUri;
                        PushResultDto pushResult = ecrService.pushImage(
                                serviceInfo.getName(), 
                                imageTag, 
                                registryUri
                        );
                        
                        if (pushResult.isSuccess()) {
                            deployedServices.add(serviceInfo.getName());
                            log.info("Successfully deployed service: {}", serviceInfo.getName());
                        } else {
                            log.error("Failed to push service {}: {}", serviceInfo.getName(), pushResult.getErrorMessage());
                        }
                    } else {
                        log.error("Failed to build service {}: {}", serviceInfo.getName(), buildResult.getBuildLog());
                    }
                } catch (Exception e) {
                    log.error("Error deploying service {}: {}", serviceInfo.getName(), e.getMessage());
                }
            }
            
            Repository repository = repositoryRepo.findById(repositoryId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));
            
            repository.updateDeploymentStatus(Repository.DeploymentStatus.DEPLOYED);
            repository.updateLatestCommit(latestCommit);
            repositoryRepo.save(repository);
            
            log.info("Deployment completed for repository {}. Deployed services: {}", repositoryId, deployedServices);
            
        } catch (Exception e) {
            log.error("Async deployment failed for repository {}: {}", repositoryId, e.getMessage());
            
            Repository repository = repositoryRepo.findById(repositoryId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));
            repository.updateDeploymentStatus(Repository.DeploymentStatus.NOT_DEPLOYED);
            repositoryRepo.save(repository);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    private void cleanupTempDirectory(String projectId) {
        try {
            String tempPath = workspacePath + "/" + projectId;
            Path tempDir = Paths.get(tempPath);
            if (Files.exists(tempDir)) {
                deleteDirectory(tempDir);
                log.info("Cleaned up temporary directory: {}", tempPath);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup temporary directory for project {}: {}", projectId, e.getMessage());
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        log.warn("Failed to delete: {}", path);
                    }
                });
        }
    }
}