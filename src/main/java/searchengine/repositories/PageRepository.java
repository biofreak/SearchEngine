package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {
    Page findBySiteAndPath(Site site, String path);
    List<Page> findBySite(Site site);

    @Query("SELECT COUNT(*) FROM Page WHERE site = :site")
    Integer amountBySite(@Param("site") Site site);
}
