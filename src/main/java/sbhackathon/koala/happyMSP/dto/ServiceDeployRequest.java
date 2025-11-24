package sbhackathon.koala.happyMSP.dto;

public class ServiceDeployRequest {
    private int serviceId;
    private String imageUri;

    public ServiceDeployRequest() {
    }

    public ServiceDeployRequest(int serviceId, String imageUri) {
        this.serviceId = serviceId;
        this.imageUri = imageUri;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }
}