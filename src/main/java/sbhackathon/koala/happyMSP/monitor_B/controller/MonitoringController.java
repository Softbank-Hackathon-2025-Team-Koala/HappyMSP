package sbhackathon.koala.happyMSP.monitor_B.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sbhackathon.koala.happyMSP.build_A.repository.RepoRepository;
import sbhackathon.koala.happyMSP.monitor_B.event.SseEvent;
import sbhackathon.koala.happyMSP.monitor_B.event.SseEventStream;
import sbhackathon.koala.happyMSP.monitor_B.service.DashboardService; // 추가
import sbhackathon.koala.happyMSP.monitor_B.service.MonitorService;

import java.io.IOException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/metrics")
public class MonitoringController {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;
    private static final long DASHBOARD_TIMEOUT = 30 * 60 * 1000L;

    private final SseEventStream eventStream;
    private final MonitorService monitorService;
    private final DashboardService dashboardService; // 주입 추가
    private final ObjectMapper objectMapper;

    // 기존 배포 로그용
    @GetMapping(value = "/deployments", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDeploymentMetrics(@RequestParam("repo_url") String repoUrl) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        eventStream.subscribe(repoUrl, (SseEvent event) -> {
            try {
                String json = objectMapper.writeValueAsString(event.data());
                emitter.send(SseEmitter.event().name(event.event()).data(json));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        Runnable cleanup = () -> eventStream.unsubscribe(repoUrl);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());

        eventStream.publish(new SseEvent(repoUrl, "connected", "Deployment monitoring connection successful"));
        monitorService.startDeploymentPipeline(repoUrl);

        return emitter;
    }

    // 대시보드 모니터링용
    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDashboardMetrics(@RequestParam("repo_url") String repoUrl) {
        SseEmitter emitter = new SseEmitter(DASHBOARD_TIMEOUT);

        String metricKey = repoUrl + "-metric";

        eventStream.subscribe(metricKey, (SseEvent event) -> {
            try {
                String json = objectMapper.writeValueAsString(event.data());
                emitter.send(SseEmitter.event().name(event.event()).data(json));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        Runnable cleanup = () -> eventStream.unsubscribe(metricKey);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError((e) -> cleanup.run());

        dashboardService.startDashboardStreaming(repoUrl);

        return emitter;
    }


    private final RepoRepository repository;
    @GetMapping("/temp")
    public ResponseEntity<Boolean> temp(@RequestParam("repo_url") String uri) {
        if (uri.startsWith("https://")) {
            uri = uri.substring(8);
        } else if (uri.startsWith("http://")) {
            uri = uri.substring(7);
        }
        if (uri.endsWith(".git")) {
            uri = uri.substring(0, uri.length() - 4);
        }
        return ResponseEntity.ok(repository.existsByUri(uri));
    }
}