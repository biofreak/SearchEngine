package searchengine.model;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.*;
import org.springframework.data.annotation.LastModifiedDate;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "site", uniqueConstraints = {@UniqueConstraint(name="uk_url", columnNames={"url"})})
@RequiredArgsConstructor
public class Site implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false)
    private Integer id;
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
    private String url;
    @Column(name = "name", nullable = false, updatable = false)
    private String name;
}
