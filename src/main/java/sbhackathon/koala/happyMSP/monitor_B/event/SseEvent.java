package sbhackathon.koala.happyMSP.monitor_B.event;

public record SseEvent(
        String repoUrl,
        String event,
        Object data
) {}