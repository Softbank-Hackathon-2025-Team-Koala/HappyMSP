package sbhackathon.koala.happyMSP.monitor_B.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sbhackathon.koala.happyMSP.monitor_B.service.LogService;

@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @GetMapping
    public ResponseEntity<String> getLogs(@RequestParam("pod") String podName) {
        // 최근 300줄 조회
        String logs = logService.getPodLogs(podName, 300);
        return ResponseEntity.ok(logs);
    }
}