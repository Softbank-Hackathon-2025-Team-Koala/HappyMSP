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

    // [캐시] Pod 이름 -> DTO 매핑 (상태와 메트릭을 병합해서 저장)
    private final Map<String, ServiceMetricDto> podStateCache = new ConcurrentHashMap<>();

    // [캐시] Pod 이름 -> CPU/Memory 정보 (별도 스레드가 갱신)
    private final Map<String, String[]> metricsCache = new ConcurrentHashMap<>();

    // Pod 이름 -> 시작 시간 (Age 실시간 계산용)
    private final Map<String, Instant> podStartTimeCache = new ConcurrentHashMap<>();

    private CoreV1Api api;

    @PostConstruct
    public void init() {
        try {
            ApiClient client = Config.defaultClient();
            // 타임아웃 무제한 설정 (Watch 연결 유지를 위해 필수)
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

        String projectName = "project-" + repo.getRepoId();

        // 캐시 초기화
        podStateCache.clear();
        metricsCache.clear();
        podStartTimeCache.clear();

        // [스레드1] 메트릭 폴러 (CPU/Memory & Age)
        ExecutorService metricExecutor = Executors.newSingleThreadExecutor();
        metricExecutor.submit(() -> runMetricPoller(projectName, metricKey));

        // [스레드2] Pod Watcher
        try {
            runPodWatcher(projectName, metricKey);
        } catch (Exception e) {
            log.error("Pod Watcher crashed", e);
        } finally {
            metricExecutor.shutdownNow();
        }
    }

    // === 1. Pod 상태 감시 ===
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
                        // Watch이벤트 발생시 즉시 전송
                        broadcastToFrontend(metricKey);
                    }
                }
            } catch (Exception e) {
                log.warn("Watch connection lost, reconnecting in 2s...", e);
                Thread.sleep(2000);
            }
        }
    }

    // === 2. 리소스 폴러 2초 sleep===
    private void runMetricPoller(String projectName, String metricKey) {
        while (true) {
            try {
                long startTime = System.currentTimeMillis();

                // 1. 모든 파드 메트릭 가져오기
                String output = executeCommand("bash", "-c", "kubectl top pods --no-headers | grep " + projectName);

                // 2. 메트릭 캐시 업데이트
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

                // 3. 모든 Pod에 대해 DTO 갱신
                // Watcher가 감지한 모든 Pod를 대상으로 수행
                for (String podName : podStateCache.keySet()) {
                    ServiceMetricDto oldDto = podStateCache.get(podName);
                    if (oldDto == null) continue;

                    // 메트릭 가져오기 (없으면 0)
                    String[] metrics = metricsCache.getOrDefault(podName, new String[]{"0m", "0Mi"});

                    // Age 재계산
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
                            .age(currentAge) // 갱신된 Age
                            .build();

                    podStateCache.put(podName, newDto);
                }

                // 4. 변경 여부와 상관없이 무조건 전송 (실시간인걸 인지하게끔?)
                broadcastToFrontend(metricKey);

                // 5. 2초 대기
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

    // === 헬퍼 메소드 ===

    private void updatePodCache(String projectName, V1Pod pod) {
        String podName = pod.getMetadata().getName();
        String status = pod.getStatus().getPhase();

        // 상세 상태 계산
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

        // Start Time 저장 (Age 계산용)
        String age = "0s";
        if (pod.getStatus().getStartTime() != null) {
            Instant startInstant = pod.getStatus().getStartTime().toInstant();
            podStartTimeCache.put(podName, startInstant); // 캐시에 저장

            long seconds = Duration.between(startInstant, Instant.now()).getSeconds();
            age = formatDuration(seconds);
        }

        // Restart Count
        int restarts = 0;
        if (pod.getStatus().getContainerStatuses() != null && !pod.getStatus().getContainerStatuses().isEmpty()) {
            restarts = pod.getStatus().getContainerStatuses().get(0).getRestartCount();
        }

        // 메트릭 정보 조회
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
        // 데이터가 없더라도 빈 리스트 전송 (삭제 반영)
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

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m" + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h" + (minutes % 60) + "m";
    }
}