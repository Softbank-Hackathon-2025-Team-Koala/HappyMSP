package sbhackathon.koala.happyMSP.build_A.util;

import org.springframework.stereotype.Component;

@Component
public class ImageTagGenerator {

    public String generate(String project, String service, String gitSha) {
        if (project == null || service == null || gitSha == null) {
            throw new IllegalArgumentException("Project, service, and gitSha cannot be null");
        }
        
        String normalizedProject = project.toLowerCase().trim();
        String normalizedService = service.toLowerCase().trim();
        String shortSha = gitSha.length() > 7 ? gitSha.substring(0, 7) : gitSha;
        
        return String.format("%s-%s:%s", normalizedProject, normalizedService, shortSha);
    }
    
    public String generateWithRegistry(String registry, String project, String service, String gitSha) {
        String baseTag = generate(project, service, gitSha);
        if (registry == null || registry.isEmpty()) {
            return baseTag;
        }
        
        String normalizedRegistry = registry.endsWith("/") ? registry.substring(0, registry.length() - 1) : registry;
        return String.format("%s/%s", normalizedRegistry, baseTag);
    }
}