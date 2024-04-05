package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.net.URI;
import java.net.URL;

public interface IndexingService {
    IndexingResponse fullIndex();
    IndexingResponse stopIndex();
    IndexingResponse addIndex(URI url);
}
