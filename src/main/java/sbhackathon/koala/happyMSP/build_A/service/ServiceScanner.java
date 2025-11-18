package sbhackathon.koala.happyMSP.build_A.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.dto.ServiceScanResultDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class ServiceScanner {

    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("^[a-z0-9]{1,20}$");
    
    public ServiceScanResultDto scanServices(String repoPath) {
        try {
            Path servicesDir = Paths.get(repoPath, "services");
            
            if (!Files.exists(servicesDir) || !Files.isDirectory(servicesDir)) {
                throw new RuntimeException("Services directory not found: " + servicesDir);
            }
            
            List<ServiceScanResultDto.ServiceInfo> services = new ArrayList<>();
            
            try (Stream<Path> paths = Files.list(servicesDir)) {
                paths.filter(Files::isDirectory)
                     .forEach(serviceDir -> {
                         String serviceName = serviceDir.getFileName().toString();
                         
                         if (isValidServiceName(serviceName)) {
                             boolean hasDockerfile = checkDockerfileExists(serviceDir);
                             
                             if (hasDockerfile) {
                                 ServiceScanResultDto.ServiceInfo serviceInfo = ServiceScanResultDto.ServiceInfo.builder()
                                         .name(serviceName)
                                         .path(serviceDir.toString())
                                         .dockerfileExists(true)
                                         .build();
                                 
                                 services.add(serviceInfo);
                                 log.info("Valid service found: {} at {}", serviceName, serviceDir);
                             } else {
                                 log.warn("Service {} skipped: Dockerfile not found", serviceName);
                             }
                         } else {
                             log.warn("Service {} skipped: Invalid name (must be lowercase alphanumeric, ≤20 chars)", serviceName);
                         }
                     });
            }
            
            log.info("Service scan completed. Found {} valid services", services.size());
            
            return ServiceScanResultDto.builder()
                    .services(services)
                    .build();
                    
        } catch (IOException e) {
            log.error("Failed to scan services directory: {}", e.getMessage());
            throw new RuntimeException("Service scan failed: " + e.getMessage(), e);
        }
    }
    
    private boolean isValidServiceName(String serviceName) {
        return serviceName != null && SERVICE_NAME_PATTERN.matcher(serviceName).matches();
    }
    
    private boolean checkDockerfileExists(Path serviceDir) {
        Path dockerfile = serviceDir.resolve("Dockerfile");
        return Files.exists(dockerfile) && Files.isRegularFile(dockerfile);
    }
}