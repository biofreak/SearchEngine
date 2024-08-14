package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;

public interface IndexingService {
    IndexingResponse fullIndex();
    IndexingResponse stopIndex();
    IndexingResponse addIndex(String link);
}
