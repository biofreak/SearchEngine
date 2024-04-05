package searchengine.model;

public enum IndexStatus {
    INDEXING("INDEXING"),
    INDEXED("INDEXED"),
    FAILED("FAILED");

    private final String text;

    IndexStatus(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
