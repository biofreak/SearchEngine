package searchengine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

@Entity
@Data
@Table(name = "index", uniqueConstraints={@UniqueConstraint(name="uk_page_lemma", columnNames={"page_id", "lemma_id"})})
@NoArgsConstructor(onConstructor_={@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)})
@RequiredArgsConstructor
public class Index implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "page_id", foreignKey = @ForeignKey(name = "fk_index_page"))
    @NonNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Page page;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "lemma_id", foreignKey = @ForeignKey(name = "fk_index_lemma"))
    @NonNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Lemma lemma;
    @Column(name = "rank")
    @NonNull
    private float rank;
}
