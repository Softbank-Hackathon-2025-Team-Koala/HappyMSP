package sbhackathon.koala.happyMSP.build_A.entity;

import jakarta.persistence.*;
import lombok.*;
import sbhackathon.koala.happyMSP.deployment_CD.entity.Service;

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

    @OneToMany(mappedBy = "repository", fetch = FetchType.LAZY)
    private List<Service> services = new ArrayList<>();

    @Builder
    public Repository(String uri, String latestCommit) {
        this.uri = uri;
        this.latestCommit = latestCommit;
    }
}
