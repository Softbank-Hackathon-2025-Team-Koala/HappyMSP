package sbhackathon.koala.happyMSP.build_A.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import sbhackathon.koala.happyMSP.build_A.dto.*;
import sbhackathon.koala.happyMSP.entity.Repository;
import sbhackathon.koala.happyMSP.entity.ServiceStatus;
import sbhackathon.koala.happyMSP.build_A.repository.repoRepository;
import sbhackathon.koala.happyMSP.deployment_CD.repository.ServiceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildService {

    private final GitService gitService;
    private final ServiceScanner serviceScanner;
    private final AsyncBuildService asyncBuildService;
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
                        .allMatch(service -> service.getStatus() == ServiceStatus.DEPLOYED);
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
                        .status(ServiceStatus.PENDING)
                        .build();
                
                serviceRepository.save(serviceEntity);
                log.info("Created Service entity: {} with status PENDING", serviceInfo.getName());
            }
            
            // Flush and refresh to get updated repository with services
            entityManager.flush(); // Ensure all changes are persisted
            entityManager.refresh(repository); // Refresh to get updated services collection
            
            // Start async deployment for Docker build and ECR push (fire-and-forget)
            log.info("Triggering async deployment for repository: {} (background process)", repository.getId());
            asyncBuildService.startAsyncDeployment(repository.getId(), request.getRepositoryUrl(), latestCommit);
            
            // Return immediate response without waiting for async deployment
            RepositoryDto repositoryDto = RepositoryDto.from(repository);
            log.info("Returning immediate response for repository {} with {} services in PENDING status", 
                    repository.getId(), repository.getServices().size());
            return PostRepositoryResponseDto.success(repositoryDto);

        } catch (Exception e) {
            log.error("Failed to start deployment for repository: {}", e.getMessage());
            throw new RuntimeException("Failed to start deployment", e);
        } finally {
            // Clean up temporary clone directory
            cleanupTempDirectory(tempProjectId);
        }
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