package searchengine.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.persistence.Index;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.validation.constraints.Pattern;
import java.io.Serializable;

@Data
@Entity
@Table(name = "page", indexes = {@Index(name = "idx_path", columnList = "path")},
        uniqueConstraints = {@UniqueConstraint(name="uk_site_path", columnNames={"site_id", "path"})})
@NoArgsConstructor(onConstructor_={@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)})
@RequiredArgsConstructor
public class Page implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false)
    private int id;
    @ManyToOne(fetch = FetchType.EAGER, cascade = {CascadeType.MERGE})
    @JoinColumn(name = "site_id", foreignKey = @ForeignKey(name = "fk_page_site"))
    @NonNull
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;
    @Column(name = "path")
    @NonNull
    @Pattern(regexp = "(/[\\S&&[^/]]+)*(/[\\S&&[^/.]]+)(.htm(l)?)?", message = "Correct page address")
    private String path;
    @Column(name = "code")
    @NonNull
    private int code;
    @Column(name = "content", columnDefinition = "mediumtext", nullable = false)
    @NonNull
    private String content;
}
