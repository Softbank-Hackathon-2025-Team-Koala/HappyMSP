package sbhackathon.koala.happyMSP.deployment_CD.entity;

import jakarta.persistence.*;
import lombok.*;
import sbhackathon.koala.happyMSP.build_A.entity.Ecr;
import sbhackathon.koala.happyMSP.build_A.entity.Repository;

import java.util.ArrayList;
import java.util.List;

// 엔티티 클래스 자기 개발방식에 맞게 바꾸셔도 상관없어요! (생성자등)
@Entity
@Getter
@ToString(exclude = {"repository", "ecrs"})
@Table(name = "service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private int serviceId;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "address", nullable = false)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repository;

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    private List<Ecr> ecrs = new ArrayList<>();

    @Builder
    public Service(String serviceName, String address, Repository repository) {
        this.serviceName = serviceName;
        this.address = address;
        this.repository = repository;
    }
}
