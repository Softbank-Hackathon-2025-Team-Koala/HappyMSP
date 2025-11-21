package sbhackathon.koala.happyMSP.build_A.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sbhackathon.koala.happyMSP.entity.Repository;

import java.util.Optional;

public interface RepoRepository extends JpaRepository<Repository, Integer> {
    @EntityGraph(attributePaths = "services")
    Optional<Repository> findByUri(String uri);

    boolean existsByUri(String uri);
}
