package searchengine.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    public record SiteRecord(@Getter String url,@Getter String name) {}

    @Getter @Setter
    private List<SiteRecord> sites;
}
