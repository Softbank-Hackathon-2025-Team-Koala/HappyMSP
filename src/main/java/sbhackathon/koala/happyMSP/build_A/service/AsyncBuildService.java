package sbhackathon.koala.happyMSP.build_A.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sbhackathon.koala.happyMSP.build_A.dto.*;
import sbhackathon.koala.happyMSP.entity.Ecr;
import sbhackathon.koala.happyMSP.entity.Repository;
import sbhackathon.koala.happyMSP.entity.ServiceStatus;
import sbhackathon.koala.happyMSP.build_A.repository.EcrRepository;
import sbhackathon.koala.happyMSP.build_A.repository.repoRepository;
import sbhackathon.koala.happyMSP.deployment_CD.repository.ServiceRepository;
import sbhackathon.koala.happyMSP.build_A.util.ImageTagGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncBuildService {

    private final GitService gitService;
    private final DockerService dockerService;
    private final EcrService ecrService;
    private final ImageTagGenerator imageTagGenerator;
    private final repoRepository repositoryRepo;
    private final ServiceRepository serviceRepository;
    private final EcrRepository ecrRepository;

    @Value("${build.workspace.path}")
    private String workspacePath;

    @Value("${aws.ecr.registry.uri}")
    private String ecrRegistryUri;

    @Async("buildTaskExecutor")
    public CompletableFuture<Void> startAsyncDeployment(int repositoryId, String repositoryUrl, String latestCommit) {
        log.info("=== ASYNC DEPLOYMENT STARTED === Repository ID: {}", repositoryId);
        String projectId = "project-" + repositoryId;
        
        try {

            CloneResultDto cloneResult = gitService.cloneRepository(repositoryUrl, projectId);
            log.info("Git clone completed: {}", cloneResult.getGitSha());

            Repository repository = repositoryRepo.findById(repositoryId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));
                    
            List<String> deployedServices = new ArrayList<>();
            
            // Extract repository name from URL for Docker image tagging
            String repositoryName = imageTagGenerator.extractRepositoryNameFromUrl(repositoryUrl);

            // Find all services for this repository (avoid lazy loading issue)
            List<sbhackathon.koala.happyMSP.entity.Service> services = serviceRepository.findByRepository(repository);
            
            for (sbhackathon.koala.happyMSP.entity.Service service : services) {
                try {
                    log.info("Starting deployment for service: {}", service.getName());
                    
                    // Update status to BUILDING
                    updateServiceStatus(service, ServiceStatus.BUILDING);
                    log.info("Service {} status updated to BUILDING", service.getName());
                    
                    String imageTag = imageTagGenerator.generate(repositoryName, service.getName(),
                            cloneResult.getGitSha());
                    log.info("Generated image tag: {}", imageTag);

                    // Find service directory path (reconstruct from service scan)
                    String servicePath = cloneResult.getRepoPath() + "/services/" + service.getName();
                    
                    // Docker Build Phase
                    BuildResultDto buildResult = dockerService.buildImage(
                            service.getName(),
                            servicePath,
                            imageTag);

                    if (buildResult.isSuccess()) {
                        // Update status to BUILT
                        updateServiceStatus(service, ServiceStatus.BUILT);
                        log.info("Service {} built successfully, status updated to BUILT", service.getName());
                        
                        try {
                            // Update status to PUSHING
                            updateServiceStatus(service, ServiceStatus.PUSHING);
                            log.info("Service {} status updated to PUSHING", service.getName());
                            
                            // ECR Push Phase
                            String registryUri = ecrRegistryUri;
                            PushResultDto pushResult = ecrService.pushImage(
                                    service.getName(),
                                    imageTag,
                                    registryUri);

                            if (pushResult.isSuccess()) {
                                // Update status to PUSHED
                                updateServiceStatusAndAddress(service, ServiceStatus.PUSHED, pushResult.getImageUri());
                                
                                // Create and save ECR Entity
                                createEcrEntity(service, pushResult.getImageUri(), imageTag);
                                
                                deployedServices.add(service.getName());
                                log.info("Service {} pushed successfully with ECR URI: {}, port: {}", 
                                        service.getName(), pushResult.getImageUri(), service.getPortNumber());
                            } else {
                                updateServiceStatus(service, ServiceStatus.FAILED);
                                log.error("Failed to push service {} to ECR: {}", service.getName(),
                                        pushResult.getErrorMessage());
                            }
                        } catch (Exception pushException) {
                            updateServiceStatus(service, ServiceStatus.FAILED);
                            log.error("Exception during ECR push for service {}: {}", service.getName(), 
                                    pushException.getMessage(), pushException);
                        }
                    } else {
                        updateServiceStatus(service, ServiceStatus.FAILED);
                        log.error("Failed to build service {}: {}", service.getName(), buildResult.getBuildLog());
                    }
                } catch (Exception e) {
                    try {
                        updateServiceStatus(service, ServiceStatus.FAILED);
                    } catch (Exception saveException) {
                        log.error("Failed to save FAILED status for service {}: {}", service.getName(), 
                                saveException.getMessage());
                    }
                    log.error("Unexpected error deploying service {}: {}", service.getName(), e.getMessage(), e);
                }
            }

            repository.updateLatestCommit(latestCommit);
            repositoryRepo.save(repository);

            log.info("=== ASYNC DEPLOYMENT COMPLETED === Repository ID: {}. Deployed services: {}", repositoryId, deployedServices);

        } catch (Exception e) {
            log.error("=== ASYNC DEPLOYMENT FAILED === Repository ID: {}, Error: {}", repositoryId, e.getMessage(), e);

            // Handle async deployment failures gracefully without affecting API response
            try {
                // Set all services in this repository to FAILED if they're still in progress
                Repository repository = repositoryRepo.findById(repositoryId)
                        .orElseThrow(() -> new RuntimeException("Repository not found"));
                
                List<sbhackathon.koala.happyMSP.entity.Service> failedServices = serviceRepository.findByRepository(repository);
                for (sbhackathon.koala.happyMSP.entity.Service service : failedServices) {
                    if (service.getStatus() == ServiceStatus.PENDING ||
                        service.getStatus() == ServiceStatus.BUILDING ||
                        service.getStatus() == ServiceStatus.BUILT ||
                        service.getStatus() == ServiceStatus.PUSHING) {
                        updateServiceStatus(service, ServiceStatus.FAILED);
                        log.warn("Set service {} status to FAILED due to async deployment failure", service.getName());
                    }
                }
                
                repositoryRepo.save(repository);
                log.info("Completed failure cleanup for repository: {}", repositoryId);
            } catch (Exception cleanupException) {
                log.error("Failed to cleanup services after async deployment failure - Repository {}: {}", 
                        repositoryId, cleanupException.getMessage(), cleanupException);
            }
        } finally {
            // Always cleanup temp directory in async process
            try {
                cleanupTempDirectory(projectId);
            } catch (Exception cleanupException) {
                log.warn("Failed to cleanup temp directory in async process: {}", cleanupException.getMessage());
            }
        }

        log.info("=== ASYNC DEPLOYMENT FINISHED === Repository ID: {}", repositoryId);
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

    @Transactional
    private void updateServiceStatus(sbhackathon.koala.happyMSP.entity.Service service, ServiceStatus status) {
        service.updateStatus(status);
        serviceRepository.save(service);
    }

    @Transactional
    private void updateServiceStatusAndAddress(sbhackathon.koala.happyMSP.entity.Service service, ServiceStatus status, String address) {
        service.updateStatus(status);
        service.updateAddress(address);
        serviceRepository.save(service);
    }

    @Transactional
    private void createEcrEntity(sbhackathon.koala.happyMSP.entity.Service service, String imageUri, String imageTag) {
        try {
            Ecr ecr = Ecr.builder()
                    .name(service.getName())
                    .uri(imageUri)
                    .tag(imageTag)
                    .service(service)
                    .build();
            
            ecrRepository.save(ecr);
            log.info("ECR entity created for service: {} with URI: {}", service.getName(), imageUri);
        } catch (Exception e) {
            log.error("Failed to create ECR entity for service: {}, error: {}", service.getName(), e.getMessage(), e);
        }
    }
}