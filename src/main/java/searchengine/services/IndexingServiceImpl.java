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

import java.io.IOException;
import java.net.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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

        if (taskPool.getRunningThreadCount() == 0) {
            siteRepository.deleteAll();

            sites.getSites().parallelStream().forEach(config -> {
                String url = config.getUrl();
                Site siteEntity = serializeSite(url, config.getName());

                taskPool.submit(() -> {
                    try {
                        SiteWalk walkTask = new SiteWalk(new URI(url),
                                getConfigAttribute("userAgent"), getConfigAttribute("referrer"));
                        TASKS.add(walkTask);
                        walkTask.invoke().forEach(this::addIndex);
                        TASKS.remove(walkTask);
                    } catch (RuntimeException | URISyntaxException e) {
                        Optional.ofNullable(e.getCause()).ifPresent(x ->
                                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.FAILED, x.getMessage()));
                        throw new RuntimeException(e);
                    }
                });
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
        } else {
            taskPool.shutdownNow();
            while(!taskPool.isTerminated()) {
                TASKS.forEach(task -> task.completeExceptionally(new CancellationException()));
            }
            TASKS.clear();
            taskPool = new ForkJoinPool();
        }

        return response;
    }

    private RecursiveAction formIndexTask(Site siteEntity, Page pageEntity, Map.Entry<String,Long> entry) {
       return  new RecursiveAction() {
            @Override
            protected void compute() {
                Lemma lemmaEntity = serializeLemma(siteEntity, entry.getKey());
                serializeIndex(pageEntity, lemmaEntity, Float.valueOf(entry.getValue()));
            }
        };
    }

    private RecursiveAction formPageTask(Site siteEntity, URI url) {
        return new RecursiveAction() {
            @Override
            protected void compute() {
                try {
                    Page pageEntity = serializePage(siteEntity, url);
                    SplitToLemmas splitter = SplitToLemmas.getInstance();
                    String text = splitter.removeHtmlTags(pageEntity.getContent());
                    splitter.splitTextToLemmas(text).forEach((lemma, amount) -> {
                        RecursiveAction indexTask = formIndexTask(siteEntity, pageEntity, Map.entry(lemma, amount));
                        try {
                            TASKS.add(indexTask);
                            indexTask.invoke();
                            TASKS.remove(indexTask);
                        } catch (CancellationException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (IOException | RuntimeException e) {
                    throw new RuntimeException(e);
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
            RecursiveAction pageTask = formPageTask(siteEntity, url);
            taskPool.submit(() -> {
                try {
                    siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXING, null);
                    TASKS.add(pageTask);
                    pageTask.invoke();
                    TASKS.remove(pageTask);
                    siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXED, null);
                } catch (RuntimeException e) {
                    if (e.getCause() instanceof CancellationException) {
                        siteRepository.updateStatus(siteEntity.getId(),
                                IndexStatus.FAILED, IndexError.INTERRUPTED.toString());
                        throw new RuntimeException(e);
                    } else {
                        siteRepository.updateStatus(siteEntity.getId(), IndexStatus.FAILED, e.getLocalizedMessage());
                        throw new RuntimeException(e);
                    }
                }
            });
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
        Page pageEntity = pageRepository.findBySiteAndPath(site, path);
        if (pageEntity != null) {
            refreshFrequenciesByPageId(pageEntity, -1);
            pageRepository.delete(pageEntity);
        }

        Connection.Response response = getURLConnection(url);
        Integer code = response.statusCode();
        String content = response.body();

        pageEntity = new Page();
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
        indexRepository.findByPage(page).forEach(index -> {
            Lemma lemmaEntity = index.getLemma();
            lemmaEntity.setFrequency(lemmaEntity.getFrequency() + number);
            lemmaRepository.save(lemmaEntity);
        });
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
