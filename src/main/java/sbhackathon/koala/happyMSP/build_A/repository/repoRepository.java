package sbhackathon.koala.happyMSP.build_A.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sbhackathon.koala.happyMSP.build_A.entity.Repository;

import java.util.Optional;

public interface repoRepository extends JpaRepository<Repository, Integer> {
    Optional<Repository> findByUri(String uri);
}
