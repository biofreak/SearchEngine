package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findByPage(Page page);
    List<Index> findByLemma(Lemma lemma);
    Index findByPageAndLemma(Page page, Lemma lemma);

    @Query("SELECT SUM(rank) FROM Index WHERE page = :page")
    Double rankByPage(@Param("page") Page page);
}
