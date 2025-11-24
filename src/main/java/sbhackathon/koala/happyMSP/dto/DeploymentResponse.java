package sbhackathon.koala.happyMSP.dto;

import java.util.List;

public class DeploymentResponse {
    private String message;
    private String status;
    private List<String> deployedServices;

    public DeploymentResponse() {
    }

    public DeploymentResponse(String message, String status, List<String> deployedServices) {
        this.message = message;
        this.status = status;
        this.deployedServices = deployedServices;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getDeployedServices() {
        return deployedServices;
    }

    public void setDeployedServices(List<String> deployedServices) {
        this.deployedServices = deployedServices;
    }
}