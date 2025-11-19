package sbhackathon.koala.happyMSP.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

@Component
public class KubectlExecutor {

    private static final Logger logger = LoggerFactory.getLogger(KubectlExecutor.class);

    /**
     * kubectl apply -f - 명령을 실행하여 YAML을 적용합니다.
     *
     * @param yaml 적용할 Kubernetes YAML 문자열
     * @throws RuntimeException kubectl 실행 실패 시
     */
    public void applyYaml(String yaml) {
        logger.info("kubectl apply 실행 시작");
        logger.debug("적용할 YAML:\n{}", yaml);

        ProcessBuilder processBuilder = new ProcessBuilder("kubectl", "apply", "-f", "-");
        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();

            // YAML을 stdin으로 전달
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write(yaml);
                writer.flush();
            }

            // 표준 출력 읽기
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    logger.info("kubectl stdout: {}", line);
                }
            }

            // 표준 에러 읽기
            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    logger.error("kubectl stderr: {}", line);
                }
            }

            // 프로세스 종료 대기
            int exitCode = process.waitFor();
            logger.info("kubectl apply 종료 코드: {}", exitCode);

            if (exitCode != 0) {
                String errorMessage = String.format(
                        "kubectl apply 실패 (exit code: %d). Error: %s",
                        exitCode,
                        errorOutput
                );
                logger.error(errorMessage);
                throw new RuntimeException(errorMessage);
            }

            logger.info("kubectl apply 성공: {}", output.toString().trim());

        } catch (IOException e) {
            String errorMessage = "kubectl 프로세스 실행 중 IO 오류 발생: " + e.getMessage();
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        } catch (InterruptedException e) {
            String errorMessage = "kubectl 프로세스 대기 중 인터럽트 발생: " + e.getMessage();
            logger.error(errorMessage, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(errorMessage, e);
        }
    }

    /**
     * kubectl 명령이 사용 가능한지 확인합니다.
     *
     * @return kubectl 명령 사용 가능 여부
     */
    public boolean isKubectlAvailable() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("kubectl", "version", "--client");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("kubectl 명령 사용 가능");
                return true;
            } else {
                logger.warn("kubectl 명령 실행 실패 (exit code: {})", exitCode);
                return false;
            }
        } catch (Exception e) {
            logger.warn("kubectl 사용 불가능: {}", e.getMessage());
            return false;
        }
    }
}

