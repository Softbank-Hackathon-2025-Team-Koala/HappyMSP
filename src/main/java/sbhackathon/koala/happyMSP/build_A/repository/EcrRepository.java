package sbhackathon.koala.happyMSP.build_A.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sbhackathon.koala.happyMSP.entity.Ecr;

import java.util.List;

public interface EcrRepository extends JpaRepository<Ecr, Integer> {
    List<Ecr> findByService_ServiceId(int serviceServiceId);
}
