package sbhackathon.koala.happyMSP.build_A.service;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.dto.CloneResultDto;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class GitService {

    @Value("${build.workspace.path}")
    private String workspacePath;

    public String normalizeGitUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository URL cannot be null or empty");
        }
        
        String normalized = repoUrl.trim();
        
        // Remove protocol (http://, https://, git://)
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring(8);
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring(7);
        } else if (normalized.startsWith("git://")) {
            normalized = normalized.substring(6);
        }
        
        // Remove .git suffix if present
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        
        // Remove trailing slash if present
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        log.debug("Normalized URL from {} to {}", repoUrl, normalized);
        return normalized;
    }

    public CloneResultDto cloneRepository(String repoUrl, String projectId) {
        try {
            String repoPath = workspacePath + "/" + projectId;
            Path repoDir = Paths.get(repoPath);
            
            if (Files.exists(repoDir)) {
                deleteDirectory(repoDir.toFile());
            }
            
            Files.createDirectories(repoDir.getParent());
            
            log.info("Cloning repository {} to {}", repoUrl, repoPath);
            
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider("", "");
            
            Git git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(repoPath))
                    .setCredentialsProvider(credentialsProvider)
                    .call();
            
            Repository repository = git.getRepository();
            
            String defaultBranch = detectDefaultBranch(git);
            log.info("Detected default branch: {}", defaultBranch);
            
            if (!defaultBranch.equals("main") && !defaultBranch.equals("master")) {
                git.checkout().setName(defaultBranch).call();
            }
            
            String gitSha = repository.resolve("HEAD").getName();
            String shortSha = gitSha.substring(0, 7);
            
            validateMonorepoStructure(repoPath);
            
            git.close();
            
            log.info("Clone completed. Git SHA: {}", shortSha);
            
            return CloneResultDto.builder()
                    .projectId(projectId)
                    .repoPath(repoPath)
                    .gitSha(shortSha)
                    .build();
                    
        } catch (GitAPIException | IOException e) {
            log.error("Failed to clone repository: {}", e.getMessage());
            throw new RuntimeException("Git clone failed: " + e.getMessage(), e);
        }
    }
    
    private String detectDefaultBranch(Git git) throws GitAPIException {
        try {
            Ref head = git.getRepository().findRef("HEAD");
            if (head != null && head.isSymbolic()) {
                String target = head.getTarget().getName();
                return target.replace("refs/heads/", "");
            }
            
            if (git.getRepository().findRef("refs/heads/main") != null) {
                return "main";
            } else if (git.getRepository().findRef("refs/heads/master") != null) {
                return "master";
            }
            
            return "main";
        } catch (IOException e) {
            log.warn("Failed to detect default branch, using 'main': {}", e.getMessage());
            return "main";
        }
    }
    
    private void validateMonorepoStructure(String repoPath) {
        Path servicesPath = Paths.get(repoPath, "services");
        if (!Files.exists(servicesPath) || !Files.isDirectory(servicesPath)) {
            throw new RuntimeException("Monorepo structure validation failed: /services directory not found");
        }
        log.info("Monorepo structure validated: /services directory exists");
    }
    
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}