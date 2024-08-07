package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

import java.net.URI;

public interface IndexingService {
    IndexingResponse fullIndex();
    IndexingResponse stopIndex();
    IndexingResponse addIndex(URI url);
}
