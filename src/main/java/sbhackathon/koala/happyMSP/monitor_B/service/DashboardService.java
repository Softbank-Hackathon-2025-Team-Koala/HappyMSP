package sbhackathon.koala.happyMSP.monitor_B.service;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.Pair;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Watch;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sbhackathon.koala.happyMSP.build_A.repository.RepoRepository;
import sbhackathon.koala.happyMSP.entity.Repository;
import sbhackathon.koala.happyMSP.monitor_B.dto.ServiceMetricDto;
import sbhackathon.koala.happyMSP.monitor_B.event.SseEvent;
import sbhackathon.koala.happyMSP.monitor_B.event.SseEventStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RepoRepository repoRepository;
    private final SseEventStream eventStream;
    // [추가] Ingress URL 조회를 위해 Poller 주입
    private final K8sResourcePoller k8sResourcePoller;

    private final Map<String, ServiceMetricDto> podStateCache = new ConcurrentHashMap<>();
    private final Map<String, String[]> metricsCache = new ConcurrentHashMap<>();
    private final Map<String, Instant> podStartTimeCache = new ConcurrentHashMap<>();

    private CoreV1Api api;

    @PostConstruct
    public void init() {
        try {
            ApiClient client = Config.defaultClient();
            client.setReadTimeout(0);
            Configuration.setDefaultApiClient(client);
            api = new CoreV1Api();
        } catch (IOException e) {
            log.error("K8s Client Init Failed", e);
        }
    }

    @Async("monitorExecutor")
    public void startDashboardStreaming(String repoUrl) {
        String metricKey = repoUrl + "-metric";
        log.info("Start Hybrid Monitoring (Watch + Polling) for: {}", metricKey);

        String searchUrl = normalizeUrl(repoUrl);
        Repository repo = repoRepository.findByUri(searchUrl)
                .orElseThrow(() -> new RuntimeException("Repository not found: " + searchUrl));

        String projectName = extractRepositoryName(repoUrl);
        log.info("Monitoring Project Name (Sanitized): {}", projectName);

        // [추가] 대시보드 진입 시 Ingress URL 정보 전송
        String ingressUrl = k8sResourcePoller.getIngressUrl(projectName);
        if (ingressUrl != null) {
            log.info("Sending Ingress URL to dashboard: {}", ingressUrl);
            eventStream.publish(new SseEvent(metricKey, "ingress-info", ingressUrl));
        } else {
            log.warn("Ingress URL not found for project: {}", projectName);
        }

        podStateCache.clear();
        metricsCache.clear();
        podStartTimeCache.clear();

        ExecutorService metricExecutor = Executors.newSingleThreadExecutor();
        metricExecutor.submit(() -> runMetricPoller(projectName, metricKey));

        try {
            runPodWatcher(projectName, metricKey);
        } catch (Exception e) {
            log.error("Pod Watcher crashed", e);
        } finally {
            metricExecutor.shutdownNow();
        }
    }

    // ... (이하 기존 코드 동일: runPodWatcher, runMetricPoller, updatePodCache, broadcastToFrontend, executeCommand 등) ...

    private void runPodWatcher(String projectName, String metricKey) throws Exception {
        ApiClient client = api.getApiClient();

        while (true) {
            try {
                log.info("Starting Watch for project: {}", projectName);

                String path = "/api/v1/namespaces/default/pods";
                List<Pair> queryParams = new ArrayList<>();
                queryParams.add(new Pair("watch", "true"));

                Call call = client.buildCall(
                        client.getBasePath(),
                        path,
                        "GET",
                        queryParams,
                        new ArrayList<>(),
                        null,
                        new HashMap<>(),
                        new HashMap<>(),
                        new HashMap<>(),
                        new String[]{"BearerToken"},
                        null
                );

                Watch<V1Pod> watch = Watch.createWatch(
                        client,
                        call,
                        new TypeToken<Watch.Response<V1Pod>>(){}.getType()
                );

                for (Watch.Response<V1Pod> item : watch) {
                    V1Pod pod = item.object;
                    if (pod == null || pod.getMetadata() == null) continue;

                    String podName = pod.getMetadata().getName();

                    if (podName != null && podName.startsWith(projectName)) {
                        String eventType = item.type;

                        if ("DELETED".equals(eventType)) {
                            podStateCache.remove(podName);
                            metricsCache.remove(podName);
                            podStartTimeCache.remove(podName);
                        } else {
                            updatePodCache(projectName, pod);
                        }
                        broadcastToFrontend(metricKey);
                    }
                }
            } catch (Exception e) {
                log.warn("Watch connection lost, reconnecting in 2s...", e);
                Thread.sleep(2000);
            }
        }
    }

    private void runMetricPoller(String projectName, String metricKey) {
        while (true) {
            try {
                long startTime = System.currentTimeMillis();

                String output = executeCommand("bash", "-c", "kubectl top pods --no-headers | grep " + projectName);

                if (!output.isBlank()) {
                    for (String line : output.split("\n")) {
                        if (line.isBlank()) continue;
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length >= 3) {
                            String podName = parts[0];
                            String cpu = parts[1];
                            String memory = parts[2];
                            metricsCache.put(podName, new String[]{cpu, memory});
                        }
                    }
                }

                for (String podName : podStateCache.keySet()) {
                    ServiceMetricDto oldDto = podStateCache.get(podName);
                    if (oldDto == null) continue;

                    String[] metrics = metricsCache.getOrDefault(podName, new String[]{"0m", "0Mi"});

                    String currentAge = oldDto.getAge();
                    Instant startInstant = podStartTimeCache.get(podName);
                    if (startInstant != null) {
                        long seconds = Duration.between(startInstant, Instant.now()).getSeconds();
                        currentAge = formatDuration(seconds);
                    }

                    ServiceMetricDto newDto = ServiceMetricDto.builder()
                            .serviceName(oldDto.getServiceName())
                            .podName(oldDto.getPodName())
                            .status(oldDto.getStatus())
                            .restarts(oldDto.getRestarts())
                            .cpuUsage(metrics[0])
                            .memoryUsage(metrics[1])
                            .age(currentAge)
                            .build();

                    podStateCache.put(podName, newDto);
                }

                broadcastToFrontend(metricKey);

                long elapsed = System.currentTimeMillis() - startTime;
                long sleepTime = 2000 - elapsed;
                if (sleepTime > 0) Thread.sleep(sleepTime);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                log.error("Metric Poller Error", e);
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void updatePodCache(String projectName, V1Pod pod) {
        String podName = pod.getMetadata().getName();
        String status = pod.getStatus().getPhase();

        if (pod.getStatus().getContainerStatuses() != null) {
            for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                if (containerStatus.getState().getWaiting() != null) {
                    status = containerStatus.getState().getWaiting().getReason();
                    break;
                }
                if (pod.getMetadata().getDeletionTimestamp() != null) {
                    status = "Terminating";
                }
            }
        }

        String age = "0s";
        if (pod.getStatus().getStartTime() != null) {
            Instant startInstant = pod.getStatus().getStartTime().toInstant();
            podStartTimeCache.put(podName, startInstant);
            long seconds = Duration.between(startInstant, Instant.now()).getSeconds();
            age = formatDuration(seconds);
        }

        int restarts = 0;
        if (pod.getStatus().getContainerStatuses() != null && !pod.getStatus().getContainerStatuses().isEmpty()) {
            restarts = pod.getStatus().getContainerStatuses().get(0).getRestartCount();
        }

        String[] metrics = metricsCache.getOrDefault(podName, new String[]{"0m", "0Mi"});

        ServiceMetricDto dto = ServiceMetricDto.builder()
                .serviceName(extractServiceName(projectName, podName))
                .podName(podName)
                .status(status)
                .cpuUsage(metrics[0])
                .memoryUsage(metrics[1])
                .age(age)
                .restarts(restarts)
                .build();

        podStateCache.put(podName, dto);
    }

    private void broadcastToFrontend(String metricKey) {
        List<ServiceMetricDto> data = new ArrayList<>(podStateCache.values());
        eventStream.publish(new SseEvent(metricKey, "dashboard-update", data));
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
            return "";
        }
    }

    private String extractServiceName(String projectName, String podName) {
        try {
            String prefix = projectName + "-";
            if (podName.startsWith(prefix)) {
                String temp = podName.substring(prefix.length());
                int lastDash = temp.lastIndexOf('-');
                if (lastDash > 0) {
                    String temp2 = temp.substring(0, lastDash);
                    int secondLastDash = temp2.lastIndexOf('-');
                    if (secondLastDash > 0) return temp2.substring(0, secondLastDash);
                }
            }
        } catch (Exception ignored) {}
        return podName;
    }

    private String normalizeUrl(String repoUrl) {
        String url = repoUrl;
        if (url.startsWith("https://")) url = url.substring(8);
        else if (url.startsWith("http://")) url = url.substring(7);
        if (url.endsWith(".git")) url = url.substring(0, url.length() - 4);
        return url;
    }

    private String extractRepositoryName(String repoUrl) {
        String uri = normalizeUrl(repoUrl);
        String[] parts = uri.split("/");
        if (parts.length > 0) {
            String repoName = parts[parts.length - 1];
            return repoName.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
        }
        return "unknown-repo";
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m" + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h" + (minutes % 60) + "m";
    }
}