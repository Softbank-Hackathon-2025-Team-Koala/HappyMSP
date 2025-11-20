package sbhackathon.koala.happyMSP.entity;

public enum ServiceStatus {
    PENDING("대기중"),
    BUILDING("빌드중"),
    BUILT("빌드완료"),
    PUSHING("푸시중"),
    PUSHED("푸시완료"),
    DEPLOYING("배포중"),
    DEPLOYED("배포완료"),
    FAILED("실패");

    private final String description;

    ServiceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}