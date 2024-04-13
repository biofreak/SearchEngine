package searchengine.utils;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import searchengine.model.IndexError;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Stream;

public class SiteWalk extends RecursiveTask<Stream<URI>> {

    private final URI SITE;

    private final String USER_AGENT;

    private final String REFERRER;

    private final String RELATIVE_REGEX = "(/[\\S&&[^/.]]+)+(.htm(l)?)?";

    public SiteWalk(URI site, String userAgent, String referrer) {
        this.SITE = site;
        this.USER_AGENT = userAgent;
        this.REFERRER = referrer;
    }

    private Connection getURLConnection(URI url) {
        return Jsoup.connect(url.toString())
                .userAgent(USER_AGENT)
                .referrer(REFERRER);
    }

    private Stream<URI> getReferences(URI url) {
        try {
            return getURLConnection(url).get().select("a[href]").stream()
                    .map(tag -> tag.attribute("href").getValue())
                    .filter(token -> !token.isEmpty() && !token.equals("/"))
                    .map(reference -> reference.charAt(reference.length() - 1) == '/'
                            ? reference.substring(0, reference.length() - 1) : reference)
                    .map(path -> {
                        String host = SITE.getScheme() + "://" + SITE.getHost();
                        if (path.matches(host + RELATIVE_REGEX)) return path;
                        return (path.matches(RELATIVE_REGEX)) ? host + path : SITE.toString();
                    })
                    .filter(reference -> reference.matches(SITE + RELATIVE_REGEX))
                    .map(string -> {
                        try {
                            return new URI(string);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    }).distinct().sorted(Comparator.comparing(URI::toString));
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Stream<URI> compute() {
        try {
            Thread.sleep(120);
            return Stream.concat(Stream.of(SITE), getReferences(SITE).flatMap(reference ->
                    new SiteWalk(reference, USER_AGENT, REFERRER).invoke()));
        } catch (InterruptedException | CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }
}