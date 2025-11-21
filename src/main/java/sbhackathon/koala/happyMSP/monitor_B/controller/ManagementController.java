package sbhackathon.koala.happyMSP.monitor_B.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sbhackathon.koala.happyMSP.monitor_B.service.ManagementService;

import java.util.Map;

@RestController
@RequestMapping("/management")
@RequiredArgsConstructor
public class ManagementController {

    private final ManagementService managementService;

    // 1. 파드 강제 재시작 (삭제)
    @PostMapping("/pod/restart")
    public ResponseEntity<?> restartPod(@RequestBody Map<String, String> body) {
        String podName = body.get("podName");
        managementService.restartPod(podName);
        return ResponseEntity.ok("Pod restart initiated");
    }

    // 2. 서비스 전체 재시작
    @PostMapping("/service/restart")
    public ResponseEntity<?> restartService(@RequestBody Map<String, String> body) {
        String repoUrl = body.get("repoUrl");
        String serviceName = body.get("serviceName");
        managementService.restartService(repoUrl, serviceName);
        return ResponseEntity.ok("Service restart initiated");
    }

    // 3. 스케일링 (개수 조절)
    @PostMapping("/service/scale")
    public ResponseEntity<?> scaleService(@RequestBody Map<String, Object> body) {
        String repoUrl = (String) body.get("repoUrl");
        String serviceName = (String) body.get("serviceName");
        int replicas = Integer.parseInt(body.get("replicas").toString());

        managementService.scaleService(repoUrl, serviceName, replicas);
        return ResponseEntity.ok("Scaling initiated");
    }

    // 4. 로그 조회
    @GetMapping("/logs")
    public ResponseEntity<String> getLogs(@RequestParam("pod") String podName) {
        return ResponseEntity.ok(managementService.getLogs(podName));
    }
}