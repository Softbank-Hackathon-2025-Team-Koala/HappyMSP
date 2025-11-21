package sbhackathon.koala.happyMSP.monitor_B.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Service
public class LogService {

    public String getPodLogs(String podName, int lines) {
        if (podName == null || podName.isBlank()) {
            return "Pod name is required.";
        }

        if (!podName.matches("^[a-z0-9-]+$")) {
            return "Invalid pod name.";
        }

        return executeCommand("kubectl", "logs", "--tail=" + lines, podName);
    }

    private String executeCommand(String... command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("Log fetch failed for command: {}", String.join(" ", command));
                return "Logs not available or pod is not running.\n" + output.toString();
            }

            return output.toString();

        } catch (Exception e) {
            log.error("Command execution error: {}", e.getMessage());
            return "Error fetching logs: " + e.getMessage();
        }
    }
}