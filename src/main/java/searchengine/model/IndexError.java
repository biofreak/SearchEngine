package searchengine.model;

public enum IndexError {
    STARTED("Индексация уже запущена"),
    NOTSTARTED("Индексация не запущена"),
    INTERRUPTED("Индексация прервана пользователем"),
    TERMINATING("Завершение индексации"),
    URL_FORMAT("Значение параматра url не соответствует RFC 2396"),
    PAGE_OUT_OF_CONFIG("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");

    private final String text;

    IndexError(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
