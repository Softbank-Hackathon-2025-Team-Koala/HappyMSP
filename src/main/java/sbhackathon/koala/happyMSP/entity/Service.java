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
    @Column(name = "status")
    private ServiceStatus status = ServiceStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repo_id", nullable = false)
    private Repository repository;

    @OneToMany(mappedBy = "service", fetch = FetchType.LAZY)
    private List<Ecr> ecrs = new ArrayList<>();

    @Builder
    public Service(String name, String address, Repository repository, Integer portNumber, ServiceStatus status) {
        this.name = name;
        this.address = address;
        this.repository = repository;
        this.portNumber = portNumber;
        this.status = status != null ? status : ServiceStatus.PENDING;
    }

    public void updateStatus(ServiceStatus status) {
        this.status = status;
    }

    public void updateAddress(String address) {
        this.address = address;
    }
}
