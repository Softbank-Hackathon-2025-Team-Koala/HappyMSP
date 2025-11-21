package sbhackathon.koala.happyMSP.monitor_B.event;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class SseEventStream {

    private final Map<String, Consumer<SseEvent>> listeners = new ConcurrentHashMap<>();

    public void subscribe(String repoUrl, Consumer<SseEvent> listener) {
        listeners.put(repoUrl, listener);
    }

    public void unsubscribe(String repoUrl) {
        listeners.remove(repoUrl);
    }

    public void publish(SseEvent event) {
        Consumer<SseEvent> listener = listeners.get(event.repoUrl());
        if (listener != null) {
            listener.accept(event);
        }
    }
}