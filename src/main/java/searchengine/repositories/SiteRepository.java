package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexStatus;
import searchengine.model.Site;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {

    Site findByUrl(String url);

    @Transactional
    @Modifying
    @Query("UPDATE Site SET status = :status, lastError = :error, statusTime = CURRENT_TIMESTAMP WHERE id = :id")
    void updateStatus(@Param("id") Integer id, @Param("status") IndexStatus status, @Param("error") String error);

    @Query("SELECT EXISTS(SELECT 1 FROM Site WHERE status = 'INDEXING')")
    Boolean isIndexing();
}
