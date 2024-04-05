package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingResponse {
    public IndexingResponse(Boolean result) {
        this.result = result;
    }

    private Boolean result;
    private String error;
}
