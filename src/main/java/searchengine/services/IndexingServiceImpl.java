package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.SiteWalk;
import searchengine.utils.SplitToLemmas;

import java.io.IOException;
import java.net.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService  {
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final PageRepository pageRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final IndexRepository indexRepository;

    @Autowired
    private SitesList sites;

    private ForkJoinPool taskPool = new ForkJoinPool();
    private final Vector<ForkJoinTask<?>> TASKS = new Vector<>();

    public IndexingResponse fullIndex() {
        IndexingResponse result = new IndexingResponse(true);
        if (!siteRepository.isIndexing()) {
            siteRepository.deleteAll();

            sites.getSites().forEach(config -> {
                String url = config.getUrl();
                Site siteEntity = serializeSite(url, config.getName());

                TASKS.add(taskPool.submit(() -> {
                    try {
                        URI link = new URI(url);

                        Stream.concat(Stream.of(link), walkTask(Set.of(link)))
                                .sorted(Comparator.comparing(URI::toString)).forEach(this::addIndex);
                    } catch (RuntimeException | URISyntaxException e) {
                        Optional.ofNullable(e.getCause()).ifPresent(x ->
                                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.FAILED, x.getMessage()));
                    }
                }));
            });
        } else {
            result.setResult(false);
            result.setError(IndexError.STARTED.toString());
        }
        return result;
    }

    public  IndexingResponse stopIndex() {
        IndexingResponse response = new IndexingResponse(true);

        if (TASKS.isEmpty()) {
            response.setResult(false);
            response.setError(IndexError.NOTSTARTED.toString());
        } else if(taskPool.isTerminating()) {
            response.setResult(false);
            response.setError(IndexError.TERMINATING.toString());
        } else {
            taskPool.shutdownNow();
            while(!taskPool.isTerminated()) {
                TASKS.forEach(task -> task
                        .completeExceptionally(new CancellationException(IndexError.INTERRUPTED.toString())));
            }
            TASKS.clear();
            taskPool = new ForkJoinPool();
        }

        return response;
    }

    private Stream<URI> walkTask(Set<URI> urlSet) {
        try {
            SiteWalk task = new SiteWalk(urlSet, getConfigAttribute("userAgent"), getConfigAttribute("referrer"));

            TASKS.add(task);
            Set<URI> children = task.invoke().collect(Collectors.toSet());
            TASKS.remove(task);

            return children.isEmpty() ? Stream.of() : Stream.concat(children.stream(), walkTask(Stream
                    .concat(urlSet.stream(), children.stream()).collect(Collectors.toSet())));
        } catch (RuntimeException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private RecursiveAction indexTask(Site siteEntity, Page pageEntity, Map.Entry<String,Long> entry) {
       return  new RecursiveAction() {
            @Override
            protected void compute() {
                Lemma lemmaEntity = serializeLemma(siteEntity, entry.getKey());
                serializeIndex(pageEntity, lemmaEntity, Float.valueOf(entry.getValue()));
            }
        };
    }

    private RecursiveAction pageTask(Page pageEntity) {
        return new RecursiveAction() {
            @Override
            protected void compute() {
                String text = pageEntity.getContent();
                Site siteEntity = pageEntity.getSite();
                try {
                    Map<String, Long> lemmaMap = SplitToLemmas.getInstanceEng().splitTextToLemmas(text);
                    lemmaMap.putAll(SplitToLemmas.getInstanceRus().splitTextToLemmas(text));
                    siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXING, null);
                    lemmaMap.forEach((lemma, amount) -> {
                        RecursiveAction task = indexTask(siteEntity, pageEntity, Map.entry(lemma, amount));
                        try {
                            TASKS.add(task);
                            task.invoke();
                            TASKS.remove(task);
                        } catch (CancellationException e) {
                            throw new RuntimeException(e.getCause());
                        }
                    });
                    siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXED, null);
                } catch (IOException | RuntimeException e) {
                    siteRepository.updateStatus(siteEntity.getId(), IndexStatus.FAILED, e.getCause().getMessage());
                }
            }
        };
    }

    public IndexingResponse addIndex(URI url) {
        IndexingResponse result = new IndexingResponse(true);
        Map<String, String> siteConfig = getConfigSite(url.getScheme() + "://" + url.getHost());
        if (!siteConfig.isEmpty()) {
            Map.Entry<String, String> configEntry = siteConfig.entrySet().iterator().next();
            Site siteEntity = serializeSite(configEntry.getKey(), configEntry.getValue());
            Page pageEntity = pageRepository.findBySiteAndPath(siteEntity, url.getPath());
            if (pageEntity != null) {
                refreshFrequenciesByPageId(pageEntity, -1);
                pageRepository.delete(pageEntity);
            }
            try {
                TASKS.add(taskPool.submit(pageTask(serializePage(siteEntity, url))));
            } catch(IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            result.setResult(false);
            result.setError(IndexError.PAGE_OUT_OF_CONFIG.toString());
        }
        return result;
    }

    private Site serializeSite(String url, String name) {
        Site site = siteRepository.findByUrl(url);

        if (site == null) {
            site = new Site();
            site.setUrl(url);
            site.setName(name);
            return siteRepository.save(site);
        } else {
            return site;
        }
    }

    private Page serializePage(Site site, URI url) throws IOException {
        String path = url.getPath();
        Connection.Response response = getURLConnection(url);
        Integer code = response.statusCode();
        String content = response.body();

        Page pageEntity = new Page();
        pageEntity.setSite(site);
        pageEntity.setPath(path.isEmpty() ? "/" : path);
        pageEntity.setCode(code);
        pageEntity.setContent(content);
        return pageRepository.save(pageEntity);
    }

    private Lemma serializeLemma(Site site, String lemma) {
        Lemma lemmaEntity = new Lemma();
        lemmaEntity.setSite(site);
        lemmaEntity.setLemma(lemma);
        return lemmaRepository.save(lemmaEntity);
    }

    private Index serializeIndex(Page page, Lemma lemma, Float rank) {
        Index indexEntity = new Index();
        indexEntity.setPage(page);
        indexEntity.setLemma(lemma);
        indexEntity.setRank(rank);
        return indexRepository.save(indexEntity);
    }

    private Connection.Response getURLConnection(URI url) throws IOException {
        return Jsoup.connect(url.toString())
                .userAgent(getConfigAttribute("userAgent"))
                .referrer(getConfigAttribute("referrer"))
                .execute().bufferUp();
    }

    private void refreshFrequenciesByPageId(Page page, Integer number) {
        synchronized (indexRepository) {
            indexRepository.findByPage(page).forEach(index -> {
                Lemma lemmaEntity = index.getLemma();
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + number);
                lemmaRepository.save(lemmaEntity);
            });
        }
    }

    private String getConfigAttribute(String attribute) {
        Properties props = new Properties();
        try {
            props.load(getClass().getResourceAsStream("/application.yaml"));
            return props.getProperty(attribute);
        } catch (IOException e) {
            return "";
        }
    }

    private Map<String, String> getConfigSite(String site_regex) {
        return sites.getSites().stream()
                .filter(config -> config.getUrl().matches(site_regex))
                .collect(Collectors.toMap(SitesList.SiteRecord::getUrl, SitesList.SiteRecord::getName));
    }
}
