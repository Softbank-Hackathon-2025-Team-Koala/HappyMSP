package sbhackathon.koala.happyMSP.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 엔티티 클래스 자기 개발방식에 맞게 바꾸셔도 상관없어요! (생성자등)
@Entity
@Getter
@ToString(exclude = {"service"})
@Table(name = "ecr")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ecr {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int ecrId;

    @Column(name = "name")
    private String name;

    @Column(name = "uri", nullable = false)
    private String uri;

    @Column(name = "tag", nullable = false)
    private String tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;
}
