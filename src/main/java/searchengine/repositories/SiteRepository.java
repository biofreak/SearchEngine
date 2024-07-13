package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexStatus;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    Optional<Site> findByUrl(String url);

    @Transactional
    @Modifying
    @Query("UPDATE Site SET status = :status, lastError = :error, statusTime = CURRENT_TIMESTAMP WHERE id = :id")
    void updateStatus(@Param("id") Integer id, @Param("status") IndexStatus status, @Param("error") String error);

    Boolean existsByStatusIs(IndexStatus status);
}
