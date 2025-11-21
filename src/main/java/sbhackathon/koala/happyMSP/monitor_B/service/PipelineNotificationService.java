package sbhackathon.koala.happyMSP.monitor_B.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.monitor_B.event.SseEvent;
import sbhackathon.koala.happyMSP.monitor_B.event.SseEventStream;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PipelineNotificationService {

    private final SseEventStream eventStream;

    public void publish(String repoUrl, String event, Object data) {
        eventStream.publish(new SseEvent(repoUrl, event, data));
    }

    public void sendServiceLog(String repoUrl, String serviceName, String step, String status, String msg) {
        Map<String, String> data = Map.of(
                "serviceName", serviceName,
                "step", step,
                "status", status,
                "message", msg
        );
        publish(repoUrl, "service-update", data);
    }
}