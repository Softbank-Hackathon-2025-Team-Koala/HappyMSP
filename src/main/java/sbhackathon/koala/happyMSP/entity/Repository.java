package sbhackathon.koala.happyMSP.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

// 엔티티 클래스 자기 개발방식에 맞게 바꾸셔도 상관없어요! (생성자등)
@Entity
@Getter
@ToString(exclude = {"services"})
@Table(name = "repository")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Repository {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repo_id")
    private int repoId;

    @Column(name = "repo_uri")
    private String uri;

    @Column(name = "latest_commit")
    private String latestCommit;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_status")
    private DeploymentStatus deploymentStatus = DeploymentStatus.NOT_DEPLOYED;

    @OneToMany(mappedBy = "repository", fetch = FetchType.LAZY)
    private List<Service> services = new ArrayList<>();

    public enum DeploymentStatus {
        NOT_DEPLOYED("미등록"),
        DEPLOYING("배포중"),
        DEPLOYED("배포완료");

        private final String description;

        DeploymentStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Builder
    public Repository(String uri, String latestCommit) {
        this.uri = uri;
        this.latestCommit = latestCommit;
        this.deploymentStatus = DeploymentStatus.NOT_DEPLOYED;
    }

    public void updateLatestCommit(String latestCommit) {
        this.latestCommit = latestCommit;
    }

    public void updateDeploymentStatus(DeploymentStatus status) {
        this.deploymentStatus = status;
    }
}
