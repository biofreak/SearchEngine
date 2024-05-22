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
                    .filter(link -> !link.isEmpty() && !link.equals(site.toString()))
                    .filter(link -> link.matches(regex))
                    .map(string -> {
                        try {
                            System.out.println(site + " ---> " + string);
                            return new URI(string);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException | RuntimeException e) {
            return Stream.of();
        }
    }

    @Override
    protected Stream<URI> compute() {
        try {
            Thread.sleep(120);
            return SITE.stream().flatMap(url -> getReferences(url, getParent(url.toString()) + CHILD_REGEX));
        } catch (InterruptedException | CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}