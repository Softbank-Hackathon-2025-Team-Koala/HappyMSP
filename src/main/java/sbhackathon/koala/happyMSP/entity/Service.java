package sbhackathon.koala.happyMSP.entity;

import jakarta.persistence.*;
import lombok.*;

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
    @Column(name = "id")
    private int id;

    @Column(name = "name")
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "port_number")
    private Integer portNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "deploy_status")
    private ServiceDeployStatus deployStatus = ServiceDeployStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repository;

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    private List<Ecr> ecrs = new ArrayList<>();

    @Builder
    public Service(String name, String address, Repository repository, Integer portNumber, ServiceDeployStatus deployStatus) {
        this.name = name;
        this.address = address;
        this.repository = repository;
        this.portNumber = portNumber;
        this.deployStatus = deployStatus != null ? deployStatus : ServiceDeployStatus.PENDING;
    }

    public void updateDeployStatus(ServiceDeployStatus deployStatus) {
        this.deployStatus = deployStatus;
    }

    public void updateAddress(String address) {
        this.address = address;
    }
}
