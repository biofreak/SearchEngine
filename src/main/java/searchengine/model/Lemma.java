package searchengine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;

@Entity
@Data
@Table(name = "lemma", uniqueConstraints = {@UniqueConstraint(name="uk_site_lemma", columnNames={"site_id", "lemma"})})
@NoArgsConstructor(onConstructor_={@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)})
@RequiredArgsConstructor
public class Lemma implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id", foreignKey = @ForeignKey(name = "fk_lemma_site"))
    @NonNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;
    @Column(name = "lemma")
    @NonNull
    private String lemma;
    @Column(name = "frequency", insertable = false)
    @ColumnDefault(value = "0")
    private int frequency;
}
