package searchengine.services;

import searchengine.dto.searching.SearchResponse;

public interface SearchService {
    SearchResponse startSearch(String query, String site, Integer offset, Integer limit);
}
