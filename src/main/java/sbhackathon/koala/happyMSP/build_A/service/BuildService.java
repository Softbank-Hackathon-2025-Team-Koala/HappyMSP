package sbhackathon.koala.happyMSP.build_A.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import sbhackathon.koala.happyMSP.build_A.dto.*;
import sbhackathon.koala.happyMSP.entity.Repository;
import sbhackathon.koala.happyMSP.build_A.repository.repoRepository;
import sbhackathon.koala.happyMSP.deployment_CD.repository.ServiceRepository;
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
    private final ServiceRepository serviceRepository;
    
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${build.workspace.path}")
    private String workspacePath;

    @Value("${aws.ecr.registry.uri}")
    private String ecrRegistryUri;

    @Transactional(readOnly = true)
    public GetRepositoryResponseDto getRepositoryStatus(String repoUrl) {
        try {
            // Normalize URL
            String normalizedUrl = gitService.normalizeGitUrl(repoUrl);
            
            // Find repository by normalized URL
            Optional<Repository> repositoryOpt = repositoryRepo.findByUri(normalizedUrl);
            
            if (repositoryOpt.isEmpty()) {
                return GetRepositoryResponseDto.notExist();
            }
            
            Repository repository = repositoryOpt.get();
            RepositoryDto repositoryDto = RepositoryDto.from(repository);
            
            // Determine state based on service deploy status
            RepositoryState state;
            if (repository.getServices().isEmpty()) {
                state = RepositoryState.NO_EXIST;
            } else {
                boolean allDeployed = repository.getServices().stream()
                        .allMatch(service -> service.getDeployStatus() == sbhackathon.koala.happyMSP.entity.ServiceDeployStatus.DEPLOYED);
                state = allDeployed ? RepositoryState.DEPLOYED : RepositoryState.DEPLOYING;
            }
            
            return GetRepositoryResponseDto.of(state, repositoryDto);
        } catch (Exception e) {
            log.error("Error getting repository status for URL {}: {}", repoUrl, e.getMessage());
            throw new RuntimeException("Failed to get repository status", e);
        }
    }

    @Transactional
    public PostRepositoryResponseDto requestDeployment(PostRepositoryRequestDto request) {
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
                    RepositoryDto repositoryDto = RepositoryDto.from(repository);
                    return PostRepositoryResponseDto.alreadyDeployed(repositoryDto);
                }

                repository.updateLatestCommit(latestCommit);
            } else {
                repository = Repository.builder()
                        .uri(normalizedUrl)
                        .latestCommit(latestCommit)
                        .build();
            }

            repositoryRepo.save(repository);
            
            // Scan services and create Service entities immediately
            ServiceScanResultDto scanResult = serviceScanner.scanServices(cloneResult.getRepoPath());
            log.info("Service scan completed. Found {} services", scanResult.getServices().size());
            
            // Create Service entities with PENDING status
            for (ServiceScanResultDto.ServiceInfo serviceInfo : scanResult.getServices()) {
                String expectedEcrUri = ecrRegistryUri + "/" + serviceInfo.getName() + ":latest";
                
                sbhackathon.koala.happyMSP.entity.Service serviceEntity = sbhackathon.koala.happyMSP.entity.Service.builder()
                        .name(serviceInfo.getName())
                        .address(expectedEcrUri) // Temporary ECR URI
                        .repository(repository)
                        .portNumber(serviceInfo.getPortNumber())
                        .deployStatus(sbhackathon.koala.happyMSP.entity.ServiceDeployStatus.PENDING)
                        .build();
                
                serviceRepository.save(serviceEntity);
                log.info("Created Service entity: {} with status PENDING", serviceInfo.getName());
            }
            
            // Start async deployment for Docker build and ECR push
            startAsyncDeployment(repository.getId(), request.getRepositoryUrl(), latestCommit);

            // Flush and refresh to get updated repository with services
            entityManager.flush(); // Ensure all changes are persisted
            entityManager.refresh(repository); // Refresh to get updated services collection
            
            RepositoryDto repositoryDto = RepositoryDto.from(repository);
            return PostRepositoryResponseDto.success(repositoryDto);

        } catch (Exception e) {
            log.error("Failed to start deployment for repository: {}", e.getMessage());
            throw new RuntimeException("Failed to start deployment", e);
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

            Repository repository = repositoryRepo.findById(repositoryId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));
                    
            List<String> deployedServices = new ArrayList<>();

            // Find all services for this repository
            List<sbhackathon.koala.happyMSP.entity.Service> services = repository.getServices();
            
            for (sbhackathon.koala.happyMSP.entity.Service service : services) {
                try {
                    // Update status to BUILDING
                    service.updateDeployStatus(sbhackathon.koala.happyMSP.entity.ServiceDeployStatus.BUILDING);
                    serviceRepository.save(service);
                    
                    String imageTag = imageTagGenerator.generate(projectId, service.getName(),
                            cloneResult.getGitSha());

                    // Find service directory path (reconstruct from service scan)
                    String servicePath = cloneResult.getRepoPath() + "/services/" + service.getName();
                    
                    BuildResultDto buildResult = dockerService.buildImage(
                            service.getName(),
                            servicePath,
                            imageTag);

                    if (buildResult.isSuccess()) {
                        String registryUri = ecrRegistryUri;
                        PushResultDto pushResult = ecrService.pushImage(
                                service.getName(),
                                imageTag,
                                registryUri);

                        if (pushResult.isSuccess()) {
                            // Update Service entity with actual ECR URI and DEPLOYED status
                            service.updateAddress(pushResult.getImageUri());
                            service.updateDeployStatus(sbhackathon.koala.happyMSP.entity.ServiceDeployStatus.DEPLOYED);
                            serviceRepository.save(service);

                            deployedServices.add(service.getName());
                            log.info("Successfully deployed service: {} with port: {}", service.getName(),
                                    service.getPortNumber());
                        } else {
                            log.error("Failed to push service {}: {}", service.getName(),
                                    pushResult.getErrorMessage());
                            // Keep BUILDING status or set to error state
                        }
                    } else {
                        log.error("Failed to build service {}: {}", service.getName(), buildResult.getBuildLog());
                        // Keep BUILDING status or set to error state
                    }
                } catch (Exception e) {
                    log.error("Error deploying service {}: {}", service.getName(), e.getMessage());
                }
            }

            repository.updateLatestCommit(latestCommit);
            repositoryRepo.save(repository);

            log.info("Deployment completed for repository {}. Deployed services: {}", repositoryId, deployedServices);

        } catch (Exception e) {
            log.error("Async deployment failed for repository {}: {}", repositoryId, e.getMessage());

            Repository repository = repositoryRepo.findById(repositoryId)
                    .orElseThrow(() -> new RuntimeException("Repository not found"));
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