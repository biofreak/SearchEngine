package searchengine.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

    private final int PAGES_CHUNK = 200;

    private ForkJoinPool taskPool = new ForkJoinPool();
    private final Vector<ForkJoinTask<?>> TASKS = new Vector<>();

    private final Optional<SplitToLemmas> splitterEng = Optional.ofNullable(SplitToLemmas.getInstanceEng());
    private final Optional<SplitToLemmas> splitterRus = Optional.ofNullable(SplitToLemmas.getInstanceRus());

    private final Set<Lemma> lemmaBuffer = new HashSet<>();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public IndexingResponse fullIndex() {
        IndexingResponse result = new IndexingResponse(true);
        if (!siteRepository.isIndexing()) {
            siteRepository.deleteAll();
            sites.getSites().forEach(config -> TASKS.add(taskPool.submit(() ->{
                String url = config.getUrl();
                Site siteEntity = serializeSite(url, config.getName());
                    try {
                        List<URI> urlBuffer = new ArrayList<>() {{ add(new URI(url)); }};
                        serializePages(siteEntity, urlBuffer);
                        siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXING, null);
                        walkTask(urlBuffer, urlBuffer, siteEntity).map(this::indexTask).peek(taskPool::submit)
                                .forEach(task -> {
                                    task.join();
                                    TASKS.remove(task);
                                });
                        siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXED, null);
                    } catch (RuntimeException | URISyntaxException e) {
                                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.FAILED, e.getMessage());
                    }
            })));
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

    private Stream<Page> walkTask(List<URI> urlBuffer, List<URI> walkSet, Site siteEntity) {
        try {
            List<URI> children = walkSet.stream().map(URI::getPath)
                    .map(path -> taskPool.submit(() -> {
                        Page pageEntity = pageRepository.findBySiteAndPath(siteEntity, path.isEmpty() ? "/" : path);
                        return new SiteWalk(urlBuffer, pageEntity, siteEntity.getUrl());
                    })).peek(TASKS::add).map(ForkJoinTask::join).peek(taskPool::submit).peek(TASKS::add)
                    .flatMap(task -> {
                        Stream<String> result = task.join();
                        TASKS.remove(task);
                        return result;
                    }).distinct().map(link -> {
                        try {
                            return new URI(link);
                        } catch (URISyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    }).toList();
            if (children.isEmpty()) {
                return urlBuffer.stream().map(URI::getPath)
                        .map(path -> pageRepository.findBySiteAndPath(siteEntity, path.isEmpty() ? "/" : path));
            } else {
                serializePages(siteEntity, children);
                return walkTask(Stream.concat(urlBuffer.stream(), children.stream()).toList(), children, siteEntity);
            }
        } catch(CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    public IndexingResponse addIndex(URI url) {
        IndexingResponse result = new IndexingResponse(true);
        Map<String, String> siteConfig = getConfigSite(url.getScheme() + "://" + url.getHost());
        if (!siteConfig.isEmpty()) {
            Map.Entry<String, String> configEntry = siteConfig.entrySet().iterator().next();
            Site siteEntity = serializeSite(configEntry.getKey(), configEntry.getValue());
            Page pageEntity = pageRepository.findBySiteAndPath(siteEntity, url.getPath());
            if (pageEntity != null) {
                refreshFrequencies(indexRepository.findByPage(pageEntity).stream().map(Index::getLemma).toList(),-1);
                pageRepository.delete(pageEntity);
            }
            try {
                RecursiveAction task = indexTask(pageRepository.findBySiteAndPath(siteEntity, url.getPath()));
                serializePages(siteEntity, List.of(url));
                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXING, null);
                TASKS.add(taskPool.submit(task));
                task.join();
                TASKS.remove(task);
                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXED, null);
            } catch(RuntimeException e) {
                throw new RuntimeException(e);
            }
        } else {
            result.setResult(false);
            result.setError(IndexError.PAGE_OUT_OF_CONFIG.toString());
        }
        return result;
    }

    private RecursiveAction indexTask(Page pageEntity) {
        return new RecursiveAction() {
            @Override
            protected void compute() {
                String text = pageEntity.getContent();
                Map<String, Long> lemmaMap = splitterEng.orElseThrow().splitTextToLemmas(text);
                lemmaMap.putAll(splitterRus.orElseThrow().splitTextToLemmas(text));
                synchronized (indexRepository) {
                    try {
                        indexRepository.insertAll(objectMapper.writeValueAsString(
                                serializeLemmas(pageEntity, lemmaMap.keySet()).stream()
                                        .map(lemmaEntity -> taskPool.submit(() -> new Index(pageEntity, lemmaEntity,
                                                Float.valueOf(lemmaMap.get(lemmaEntity.getLemma())))
                                        )).peek(TASKS::add).map(task -> {
                                            Index result = task.join();
                                            TASKS.remove(task);
                                            return result;
                                        }).toList()));
                    } catch (CancellationException e) {
                        throw new CancellationException(IndexError.INTERRUPTED.toString());
                    } catch (JsonProcessingException | RuntimeException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }

    private Site serializeSite(String url, String name) {
        synchronized (siteRepository) {
            Site site = siteRepository.findByUrl(url);
            return site == null ? siteRepository.save(new Site(url, name)) : site;
        }
    }

    private void serializePages(Site siteEntity, List<URI> urlList) {
        int i = urlList.size() % PAGES_CHUNK, j = (urlList.size() - i) / PAGES_CHUNK;
        for (int k = 0, start = 0; k <= j; k++, start = k * PAGES_CHUNK) {
            synchronized (pageRepository) {
                try {
                    pageRepository.insertAll(objectMapper.writeValueAsString(urlList
                            .subList(start, k < j ? (start + PAGES_CHUNK) : (start + i)).stream()
                            .map(url -> taskPool.submit(() -> {
                                String path = url.getPath().isEmpty() ? "/" : url.getPath();
                                System.out.println(url);
                                Connection.Response response = getURLConnection(url);
                                return new Page(siteEntity, path, response.statusCode(), response.body());
                            })).peek(TASKS::add).map(task -> {
                                Page result = task.join();
                                TASKS.remove(task);
                                return result;
                            }).toList()));
                } catch(CancellationException e) {
                    throw new CancellationException(IndexError.INTERRUPTED.toString());
                } catch (JsonProcessingException | RuntimeException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private List<Lemma> serializeLemmas(Page pageEntity, Set<String> lemmas) {
        Site site = pageEntity.getSite();
        synchronized (lemmaBuffer) {
            lemmaBuffer.addAll(lemmaRepository.saveAll(lemmas.stream().filter(x -> lemmaBuffer.stream()
                            .noneMatch(y -> y.getSite().getId() == site.getId() && y.getLemma().equals(x)))
                    .map(x -> new Lemma(site, x)).toList()));
        }
        List<Lemma> result = lemmaBuffer.stream()
                .filter(x -> x.getSite().getId() == site.getId())
                .filter(x -> lemmas.stream().anyMatch(y -> y.equals(x.getLemma()))).toList();
        refreshFrequencies(result, 1);

        return result;
    }

    private void refreshFrequencies(List<Lemma> lemmas, Integer delta) {
        synchronized (lemmaRepository) {
            lemmaRepository.updateFrequencies(lemmas, delta);
        }
    }

    private Connection.Response getURLConnection(URI url) throws IOException {
        return Jsoup.connect(url.toString()).ignoreHttpErrors(true)
                .userAgent(getConfigAttribute("userAgent"))
                .referrer(getConfigAttribute("referrer"))
                .execute();
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
