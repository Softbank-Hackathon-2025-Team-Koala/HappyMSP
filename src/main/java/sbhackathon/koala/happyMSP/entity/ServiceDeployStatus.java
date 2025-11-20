package sbhackathon.koala.happyMSP.entity;

public enum ServiceDeployStatus {
    PENDING("대기중"),
    BUILDING("빌드중"),
    DEPLOYED("배포완료");

    private final String description;

    ServiceDeployStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}