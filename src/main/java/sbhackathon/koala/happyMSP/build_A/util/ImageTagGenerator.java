package sbhackathon.koala.happyMSP.build_A.util;

import org.springframework.stereotype.Component;

@Component
public class ImageTagGenerator {

    public String generate(String repositoryName, String serviceName, String gitSha) {
        if (repositoryName == null || serviceName == null || gitSha == null) {
            throw new IllegalArgumentException("RepositoryName, serviceName, and gitSha cannot be null");
        }
        
        String normalizedRepoName = repositoryName.toLowerCase().trim();
        String normalizedServiceName = serviceName.toLowerCase().trim();
        String shortSha = gitSha.length() > 7 ? gitSha.substring(0, 7) : gitSha;
        
        return String.format("%s-%s:%s", normalizedRepoName, normalizedServiceName, shortSha);
    }
    
    public String generateWithRegistry(String registry, String repositoryName, String serviceName, String gitSha) {
        String baseTag = generate(repositoryName, serviceName, gitSha);
        if (registry == null || registry.isEmpty()) {
            return baseTag;
        }
        
        String normalizedRegistry = registry.endsWith("/") ? registry.substring(0, registry.length() - 1) : registry;
        return String.format("%s/%s", normalizedRegistry, baseTag);
    }
    
    public String extractRepositoryNameFromUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            return "unknown-repo";
        }
        
        String url = repositoryUrl.trim();
        
        // Remove .git suffix if present
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        
        // Extract repository name from URL
        String[] parts = url.split("/");
        if (parts.length > 0) {
            String repoName = parts[parts.length - 1];
            // Sanitize for Docker tag format (only lowercase, numbers, hyphens, periods, underscores)
            return repoName.toLowerCase()
                    .replaceAll("[^a-z0-9._-]", "-")
                    .replaceAll("^[._-]+|[._-]+$", ""); // Remove leading/trailing special chars
        }
        
        return "unknown-repo";
    }
}