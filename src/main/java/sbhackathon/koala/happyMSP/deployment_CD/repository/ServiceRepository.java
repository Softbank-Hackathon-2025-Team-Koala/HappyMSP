package sbhackathon.koala.happyMSP.deployment_CD.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sbhackathon.koala.happyMSP.deployment_CD.entity.Service;

public interface ServiceRepository extends JpaRepository<Service, Integer> {
}
