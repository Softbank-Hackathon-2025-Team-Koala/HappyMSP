package sbhackathon.koala.happyMSP.deployment_CD.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sbhackathon.koala.happyMSP.entity.Service;
import sbhackathon.koala.happyMSP.entity.Repository;
import java.util.List;

public interface ServiceRepository extends JpaRepository<Service, Integer> {
    List<Service> findByRepository(Repository repository);
}
