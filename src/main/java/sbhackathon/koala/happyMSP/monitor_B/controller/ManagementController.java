package sbhackathon.koala.happyMSP.monitor_B.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sbhackathon.koala.happyMSP.monitor_B.service.LogService;
import sbhackathon.koala.happyMSP.monitor_B.service.ManagementService;

import java.util.Map;

@RestController
@RequestMapping("/management")
@RequiredArgsConstructor
public class ManagementController {

    private final ManagementService managementService;
    private final LogService logService; // LogService 직접 주입

    // 파드 강제 재시작
    @PostMapping("/pod/restart")
    public ResponseEntity<?> restartPod(@RequestBody Map<String, String> body) {
        managementService.restartPod(body.get("podName"));
        return ResponseEntity.ok("Pod restart initiated");
    }

    // 서비스 전체 재시작
    @PostMapping("/service/restart")
    public ResponseEntity<?> restartService(@RequestBody Map<String, String> body) {
        managementService.restartService(body.get("repoUrl"), body.get("serviceName"));
        return ResponseEntity.ok("Service restart initiated");
    }

    // 스케일링
    @PostMapping("/service/scale")
    public ResponseEntity<?> scaleService(@RequestBody Map<String, Object> body) {
        managementService.scaleService(
                (String) body.get("repoUrl"),
                (String) body.get("serviceName"),
                Integer.parseInt(body.get("replicas").toString())
        );
        return ResponseEntity.ok("Scaling initiated");
    }

    // 단순 로그 조회 (필요 시 유지)
    @GetMapping("/logs")
    public ResponseEntity<String> getLogs(@RequestParam("pod") String podName) {
        return ResponseEntity.ok(managementService.getLogs(podName));
    }

    // 실시간 로그 스트리밍 (SSE)
    @GetMapping(value = "/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@RequestParam("pod") String podName) {
        // 타임아웃 30분 설정
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        logService.streamPodLogs(podName, emitter);
        return emitter;
    }
}