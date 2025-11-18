package sbhackathon.koala.happyMSP.build_A.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.dto.BuildResultDto;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class DockerService {

    public BuildResultDto buildImage(String serviceName, String contextPath, String imageTag) {
        try {
            log.info("Building Docker image for service: {} with tag: {}", serviceName, imageTag);
            
            Path contextDir = Paths.get(contextPath);
            if (!Files.exists(contextDir) || !Files.isDirectory(contextDir)) {
                throw new RuntimeException("Context directory does not exist: " + contextPath);
            }

            Path dockerfile = contextDir.resolve("Dockerfile");
            if (!Files.exists(dockerfile)) {
                throw new RuntimeException("Dockerfile not found in: " + contextPath);
            }

            StringBuilder buildLog = new StringBuilder();

            ProcessBuilder processBuilder = new ProcessBuilder(
                "docker", "build", "-t", imageTag, "."
            );
            processBuilder.directory(contextDir.toFile());
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    buildLog.append(line).append("\n");
                    log.debug("Build output: {}", line);
                }
            }

            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Docker build completed successfully for service: {}", serviceName);
                return BuildResultDto.builder()
                        .serviceName(serviceName)
                        .imageId(imageTag)
                        .imageTag(imageTag)
                        .success(true)
                        .buildLog(buildLog.toString())
                        .build();
            } else {
                log.error("Docker build failed for service: {} with exit code: {}", serviceName, exitCode);
                return BuildResultDto.builder()
                        .serviceName(serviceName)
                        .imageId(null)
                        .imageTag(imageTag)
                        .success(false)
                        .buildLog("Build failed with exit code: " + exitCode + "\n" + buildLog.toString())
                        .build();
            }

        } catch (Exception e) {
            log.error("Docker build failed for service: {}", serviceName, e);
            return BuildResultDto.builder()
                    .serviceName(serviceName)
                    .imageId(null)
                    .imageTag(imageTag)
                    .success(false)
                    .buildLog("Build failed: " + e.getMessage())
                    .build();
        }
    }
}