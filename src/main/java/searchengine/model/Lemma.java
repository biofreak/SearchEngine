package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.SQLInsert;

import java.io.Serializable;

@Entity
@Data
@Table(name = "lemma", uniqueConstraints = {@UniqueConstraint(name="uk_site_lemma", columnNames={"site_id", "lemma"})})
@SQLInsert(sql="insert into lemma (lemma, site_id) values (?, ?) on duplicate key update frequency = frequency + 1")
public class Lemma implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id", foreignKey = @ForeignKey(name = "fk_lemma_site"), nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;
    @Column(name = "lemma", nullable = false)
    private String lemma;
    @Column(name = "frequency", nullable = false, insertable = false)
    @ColumnDefault(value = "1")
    private Integer frequency;
}
