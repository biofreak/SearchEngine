package searchengine.config;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Data
@ConfigurationProperties(prefix = "indexing-settings")
public class SiteList {
    public record SiteRecord(@Getter String url, @Getter String name) {}

    private List<SiteRecord> sites;
}
