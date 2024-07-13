package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Optional<Lemma> findBySiteAndLemma(Site site, String lemma);

    Integer countAllBySite(Site site);

    @Transactional
    @Modifying
    @Query("UPDATE Lemma lemma SET lemma.frequency = lemma.frequency + :delta WHERE lemma IN :data")
    void updateFrequencies(List<Lemma> data, int delta);
}
