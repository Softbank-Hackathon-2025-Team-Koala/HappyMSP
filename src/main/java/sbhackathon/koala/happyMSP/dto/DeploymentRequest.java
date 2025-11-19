package sbhackathon.koala.happyMSP.dto;

import java.util.List;

public class DeploymentRequest {
    private String projectName;
    private List<ServiceDeployRequest> services;

    public DeploymentRequest() {
    }

    public DeploymentRequest(String projectName, List<ServiceDeployRequest> services) {
        this.projectName = projectName;
        this.services = services;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<ServiceDeployRequest> getServices() {
        return services;
    }

    public void setServices(List<ServiceDeployRequest> services) {
        this.services = services;
    }
}

