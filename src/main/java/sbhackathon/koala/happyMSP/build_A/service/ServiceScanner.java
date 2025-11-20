package sbhackathon.koala.happyMSP.build_A.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.dto.ServiceScanResultDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class ServiceScanner {

    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("^[a-z0-9]{1,20}$");
    private static final Pattern EXPOSE_PATTERN = Pattern.compile("^\\s*EXPOSE\\s+(\\d+)(?:/\\w+)?.*$", Pattern.CASE_INSENSITIVE);
    
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
                                 Integer portNumber = parseDockerfileExposePorts(serviceDir);
                                 
                                 ServiceScanResultDto.ServiceInfo serviceInfo = ServiceScanResultDto.ServiceInfo.builder()
                                         .name(serviceName)
                                         .path(serviceDir.toString())
                                         .dockerfileExists(true)
                                         .portNumber(portNumber)
                                         .build();
                                 
                                 services.add(serviceInfo);
                                 log.info("Valid service found: {} at {} (port: {})", serviceName, serviceDir, portNumber);
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
    
    private Integer parseDockerfileExposePorts(Path serviceDir) {
        Path dockerfile = serviceDir.resolve("Dockerfile");
        
        try {
            List<String> lines = Files.readAllLines(dockerfile, StandardCharsets.UTF_8);
            
            for (String line : lines) {
                String trimmedLine = line.trim();
                
                // Skip comments
                if (trimmedLine.startsWith("#")) {
                    continue;
                }
                
                Matcher matcher = EXPOSE_PATTERN.matcher(trimmedLine);
                if (matcher.matches()) {
                    String portStr = matcher.group(1);
                    try {
                        int port = Integer.parseInt(portStr);
                        if (port > 0 && port <= 65535) {
                            log.debug("Found EXPOSE port {} in {}/Dockerfile", port, serviceDir.getFileName());
                            return port;
                        } else {
                            log.warn("Invalid port number {} in {}/Dockerfile (must be 1-65535)", port, serviceDir.getFileName());
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse port number '{}' in {}/Dockerfile", portStr, serviceDir.getFileName());
                    }
                }
            }
            
            log.debug("No EXPOSE directive found in {}/Dockerfile", serviceDir.getFileName());
            return null;
            
        } catch (IOException e) {
            log.warn("Failed to read Dockerfile in {}: {}", serviceDir, e.getMessage());
            return null;
        }
    }
}