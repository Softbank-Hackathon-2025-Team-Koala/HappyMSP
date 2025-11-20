package sbhackathon.koala.happyMSP.build_A.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import sbhackathon.koala.happyMSP.entity.Repository;

import java.util.Optional;

public interface repoRepository extends JpaRepository<Repository, Integer> {
    Optional<Repository> findByUri(String uri);
}
