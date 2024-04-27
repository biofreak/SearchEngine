package searchengine.utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import searchengine.model.IndexError;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveTask;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SiteWalk extends RecursiveTask<Stream<URI>> {

    private final Set<URI> SITE;

    private final String USER_AGENT;

    private final String REFERRER;

    private final String CHILD_REGEX = "(/[\\S&&[^/]]+)*(/[\\S&&[^/.]]+)(.htm(l)?)?";

    public SiteWalk(Set<URI> site, String userAgent, String referrer) {
        this.SITE = site;
        this.USER_AGENT = userAgent;
        this.REFERRER = referrer;
    }

    private Connection getURLConnection(URI site) {
        return Jsoup.connect(site.toString())
                .userAgent(USER_AGENT)
                .referrer(REFERRER);
    }

    private String getParent(String link) {
        return link.equals(getBaseAddress()) ? link : link.substring(0, link.lastIndexOf("/"));
    }

    private String getBaseAddress() {
        URI url = SITE.stream().findFirst().orElse(null);
        return url == null ? "" : url.getScheme() + "://" + url.getHost();
    }

    private Stream<URI> getReferences(URI site, String regex) {
        try {
            return getURLConnection(site).get().select("a[href]").stream()
                    .map(link -> link.absUrl("href"))
                    .map(link -> (link.lastIndexOf("/") == link.length()-1) ?link.substring(0,link.length()-1):link)
                    .map(link -> link.contains("?") ? link.substring(0, link.lastIndexOf("?")) : link)
                    .map(link -> link.contains("#") ? link.substring(0, link.lastIndexOf("#")) : link)
                    .filter(link -> !link.isEmpty()&&!link.equals(site.toString()))
                    .filter(link -> link.matches(regex))
                    .map(string -> {
                        try {
                            return new URI(string);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException | RuntimeException e) {
            return Stream.of();
        }
    }

    private Stream<URI> getChildren(URI site) {
        return getReferences(site, site.toString() + CHILD_REGEX);
    }

    private Set<URI> getNeighbors(Set<URI> neighbors) {
        try {
            Set<URI> result = neighbors.stream()
                    .flatMap(url -> getReferences(url, getParent(url.toString()) + CHILD_REGEX)
                            .filter(Predicate.not(neighbors::contains))).collect(Collectors.toSet());
            Set<URI> iteration = result;
            while (!iteration.isEmpty()) {
                iteration = iteration.stream()
                        .flatMap(url -> getReferences(url, getParent(url.toString()) + CHILD_REGEX)
                                .filter(Predicate.not(neighbors::contains))
                                .filter(Predicate.not(result::contains)))
                        .collect(Collectors.toSet());
                result.addAll(iteration);
            }
            return result;
        } catch (CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Stream<URI> compute() {
        try {
            Thread.sleep(120);
            Set<URI> children = SITE.stream().flatMap(this::getChildren).collect(Collectors.toSet());
            children.addAll(getNeighbors(children));
            return children.isEmpty() ? SITE.stream() : Stream.concat(SITE.stream(),
                        new SiteWalk(children, USER_AGENT, REFERRER).invoke());
        } catch (InterruptedException | CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}