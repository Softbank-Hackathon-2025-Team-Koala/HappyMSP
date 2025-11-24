package sbhackathon.koala.happyMSP.monitor_B.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogService {

    // 비동기 실행을 위해 Executor 주입 (MonitorAsyncConfig의 monitorExecutor 사용)
    private final Executor monitorExecutor;

    public String getPodLogs(String podName, int lines) {
        if (podName == null || podName.isBlank()) return "Pod name is required.";
        if (!isValidPodName(podName)) return "Invalid pod name.";
        return executeCommand("kubectl", "logs", "--tail=" + lines, podName);
    }

    /**
     * 실시간 로그 스트리밍 (SSE)
     * kubectl logs -f --tail=300 명령을 실행하여 실시간으로 전송
     */
    public void streamPodLogs(String podName, SseEmitter emitter) {
        if (podName == null || podName.isBlank() || !isValidPodName(podName)) {
            try {
                emitter.send(SseEmitter.event().data("Invalid pod name."));
                emitter.complete();
            } catch (Exception ignored) {}
            return;
        }

        monitorExecutor.execute(() -> {
            Process process = null;
            try {
                log.info("Start streaming logs for pod: {}", podName);
                // -f: follow (실시간), --tail=300: 초기 300줄 표시
                ProcessBuilder pb = new ProcessBuilder("kubectl", "logs", "-f", "--tail=300", podName);
                pb.redirectErrorStream(true); // 에러 로그도 함께 전송
                process = pb.start();

                final Process finalProcess = process;
                // 클라이언트 연결 종료 시 프로세스 kill
                emitter.onCompletion(() -> destroyProcess(finalProcess, podName));
                emitter.onTimeout(() -> destroyProcess(finalProcess, podName));
                emitter.onError((e) -> destroyProcess(finalProcess, podName));

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // 각 줄을 SSE 이벤트로 전송
                        emitter.send(SseEmitter.event().data(line));
                    }
                }

                // 프로세스가 자연 종료되면 Emitter도 종료
                emitter.complete();

            } catch (Exception e) {
                log.error("Error streaming logs for {}: {}", podName, e.getMessage());
                emitter.completeWithError(e);
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroy();
                }
            }
        });
    }

    private void destroyProcess(Process process, String podName) {
        if (process != null && process.isAlive()) {
            log.debug("Stopping log stream process for pod: {}", podName);
            process.destroy();
        }
    }

    private boolean isValidPodName(String podName) {
        return podName.matches("^[a-z0-9-]+$");
    }

    private String executeCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append("\n");
            }
            p.waitFor();
            return out.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}