package searchengine.model;

import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.validation.constraints.Pattern;
import java.io.Serializable;

@Entity
@Data
@Table(name = "page", indexes = {@Index(name = "idx_path", columnList = "path")},
        uniqueConstraints = {@UniqueConstraint(name="uk_site_path", columnNames={"site_id", "path"})})
@RequiredArgsConstructor
public class Page implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false)
    private Integer id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinColumn(name = "site_id", foreignKey = @ForeignKey(name = "fk_page_site"), nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;
    @Column(name = "path", nullable = false)
    @Pattern(regexp = "(/[\\S&&[^/.]]+)+(.htm(l)?)?", message = "Correct page address")
    private String path;
    @Column(name = "code", nullable = false)
    private Integer code;
    @Column(name = "content", columnDefinition = "mediumtext", nullable = false)
    private String content;
}
