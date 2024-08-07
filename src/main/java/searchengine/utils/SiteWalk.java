package searchengine.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.model.IndexError;
import searchengine.model.Page;

import java.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveTask;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SiteWalk extends RecursiveTask<Stream<String>> {
    private final List<URI> REFS;

    private final Page PAGE;

    private final String BASE_ADDRESS;

    private final String CHILD_REGEX = "(/[\\S&&[^/]]+)*(/[\\S&&[^/.]]+)(.htm(l)?)?";

    public SiteWalk(List<URI> urlList, Page pageEntity, String baseAddress) {
        REFS = urlList;
        PAGE = pageEntity;
        this.BASE_ADDRESS = baseAddress;
    }

    private String stripSlash(String link) {
        return (link.lastIndexOf("/") == link.length() - 1) ? link.substring(0, link.length() - 1) : link;
    }

    private Stream<String> getReferences(Document html, String regex) {
        try {
            return html.select("a[href]").stream()
                    .map(link -> link.attr("href").contains(BASE_ADDRESS) ? link.attr("href") :
                            (BASE_ADDRESS + link.attr("href").strip())).map(this::stripSlash)
                    .map(link -> link.contains("?") ? link.substring(0, link.lastIndexOf("?")) : link)
                    .map(link -> link.contains("#") ? link.substring(0, link.lastIndexOf("#")) : link)
                    .filter(link -> !link.isEmpty()).filter(link -> link.matches(regex))
                    .filter(link -> REFS.stream().map(URI::toString).noneMatch(Predicate.isEqual(link))).distinct();
        } catch (RuntimeException e) {
            return Stream.of();
        }
    }

    @Override
    protected Stream<String> compute() {
        try {
            String path = PAGE.getPath();
            String address = BASE_ADDRESS + (path.equals("/") ? "" : path);
            return getReferences(Jsoup.parse(PAGE.getContent()), BASE_ADDRESS + CHILD_REGEX)
                    .filter(link -> !link.equals(address));
        } catch (CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}