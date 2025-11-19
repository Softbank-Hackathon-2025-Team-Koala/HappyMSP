package sbhackathon.koala.happyMSP.dto;

public class ServiceDeployRequest {
    private String serviceName;
    private String imageUri;

    public ServiceDeployRequest() {
    }

    public ServiceDeployRequest(String serviceName, String imageUri) {
        this.serviceName = serviceName;
        this.imageUri = imageUri;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }
}

