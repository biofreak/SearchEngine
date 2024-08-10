package searchengine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.LastModifiedDate;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "site", uniqueConstraints = {@UniqueConstraint(name="uk_url", columnNames={"url"})})
@NoArgsConstructor(onConstructor_={@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)})
@RequiredArgsConstructor
public class Site implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false)
    private int id;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, insertable = false)
    @ColumnDefault("'INDEXING'")
    @OptimisticLock(excluded = true)
    private IndexStatus status;
    @Version
    @LastModifiedDate
    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "status_time", columnDefinition = "timestamp", nullable = false, insertable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime statusTime;
    @Column(name = "last_error", columnDefinition = "text", insertable = false)
    @ColumnDefault("NULL")
    @OptimisticLock(excluded = true)
    private String lastError;
    @Column(name = "url", unique = true, nullable = false, updatable = false)
    @NonNull
    private String url;
    @Column(name = "name", nullable = false, updatable = false)
    @NonNull
    private String name;
}
