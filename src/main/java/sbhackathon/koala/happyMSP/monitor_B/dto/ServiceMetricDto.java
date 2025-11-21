package sbhackathon.koala.happyMSP.monitor_B.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServiceMetricDto {
    private String serviceName;
    private String podName;
    private String status;
    private String cpuUsage;
    private String memoryUsage;
    private String age;
    private int restarts;
}