package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

@Entity
@Data
@Table(name = "index", uniqueConstraints={@UniqueConstraint(name="uk_page_lemma", columnNames={"page_id", "lemma_id"})})
public class Index implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "page_id", foreignKey = @ForeignKey(name = "fk_index_page"), nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page page;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "lemma_id", foreignKey = @ForeignKey(name = "fk_index_lemma"), nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Lemma lemma;
    @Column(name = "rank", nullable = false)
    private Float rank;
}
