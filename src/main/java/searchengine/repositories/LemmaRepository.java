package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Lemma findBySiteAndLemma(Site site, String lemma);
    List<Lemma> findByLemma(String lemma);

    @Query("SELECT COUNT(*) FROM Lemma WHERE site = :site")
    Integer amountBySite(@Param("site") Site site);
}
