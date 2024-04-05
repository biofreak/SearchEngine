package searchengine.dto.searching;

import lombok.Data;
import searchengine.dto.searching.SearchResult;

import java.util.List;

@Data
public class SearchResponse {
    private Boolean result;
    private Integer count;
    private List<SearchResult> data;
    private String error;
}
