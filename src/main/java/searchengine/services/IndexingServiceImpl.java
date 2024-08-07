package searchengine.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SiteList;
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
    private SiteList sites;
    private final int PAGES_CHUNK = 250;

    private ForkJoinPool taskPool = new ForkJoinPool();
    private final Vector<ForkJoinTask<?>> TASKS = new Vector<>();

    private final Optional<SplitToLemmas> splitterEng = Optional.ofNullable(SplitToLemmas.getInstanceEng());
    private final Optional<SplitToLemmas> splitterRus = Optional.ofNullable(SplitToLemmas.getInstanceRus());

    private final Set<Lemma> lemmaBuffer = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final WebClient webClient = new WebClient(BrowserVersion.FIREFOX);

    public IndexingResponse fullIndex() {
        IndexingResponse result = new IndexingResponse(true);
        if (!siteRepository.existsByStatusIs(IndexStatus.INDEXING)) {
            siteRepository.deleteAll();
            sites.getSites().forEach(config -> TASKS.add(taskPool.submit(() ->{
                String url = config.getUrl();
                Site siteEntity = serializeSite(url, config.getName());
                    try {
                        List<URI> urlBuffer = new ArrayList<>() {{ add(new URI(url)); }};
                        serializePages(siteEntity, urlBuffer);
                        siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXING, null);
                        walkTask(urlBuffer,urlBuffer,siteEntity).filter(Objects::nonNull).map(pageEntity ->
                                taskPool.submit(() -> serializeIndex(pageEntity))).peek(TASKS::add).forEach(task -> {
                                    try {
                                        task.join();
                                        TASKS.remove(task);
                                    } catch (CancellationException e) {
                                        throw new CancellationException(IndexError.INTERRUPTED.toString());
                                    }
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
            List<URI> children = walkSet.stream().map(URI::getPath).map(path -> path.isEmpty() ? "/" : path)
                    .map(path -> pageRepository.findBySiteAndPath(siteEntity, path).orElse(null))
                    .filter(Objects::nonNull)
                    .map(pageEntity -> taskPool.submit(new SiteWalk(urlBuffer, pageEntity, siteEntity.getUrl())))
                    .peek(TASKS::add).flatMap(task -> {
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
                return urlBuffer.stream().map(URI::getPath).map(path -> path.isEmpty() ? "/" : path)
                        .map(path -> pageRepository.findBySiteAndPath(siteEntity, path).orElse(null));
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
            Page pageEntity = pageRepository.findBySiteAndPath(siteEntity, url.getPath()).orElse(null);
            if (pageEntity != null) {
                decreaseFrequencies(indexRepository.findByPage(pageEntity).stream().map(Index::getLemma).toList());
                pageRepository.delete(pageEntity);
            }
            try {
                serializePages(siteEntity, List.of(url));
                siteRepository.updateStatus(siteEntity.getId(), IndexStatus.INDEXING, null);
                serializeIndex(Objects.requireNonNull(pageRepository.findBySiteAndPath(siteEntity, url.getPath())
                        .orElse(null)));
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

    private Site serializeSite(String url, String name) {
            return siteRepository.findByUrl(url).orElseGet(() -> siteRepository.saveAndFlush(new Site(url, name)));
    }

    protected void serializePages(Site siteEntity, List<URI> urlList) {
        int i = urlList.size() % PAGES_CHUNK, j = (urlList.size() - i) / PAGES_CHUNK;
        for (int k = 0, start = 0; k <= j; k++, start = k * PAGES_CHUNK) {
            try {
                pageRepository.insertAll(urlList.subList(start, k < j ? (start + PAGES_CHUNK) : (start + i)).stream()
                        .map(url -> taskPool.submit(() -> {
                            try {
                                String path = url.getPath().isEmpty() ? "/" : url.getPath();
                                WebResponse response = getURLConnection(url);
                                return new Page(siteEntity,path,response.getStatusCode(),response.getContentAsString());
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })).peek(TASKS::add).map(task -> {
                            try {
                                String result = objectMapper.writeValueAsString(task.join());
                                TASKS.remove(task);
                                return result;
                            } catch (CancellationException e) {
                                throw new CancellationException(IndexError.INTERRUPTED.toString());
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }).collect(Collectors.joining(",", "[", "]")));
            } catch (CancellationException e) {
                throw new CancellationException(IndexError.INTERRUPTED.toString());
            } catch (RuntimeException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Map<Lemma, Long> serializeLemmas(Page pageEntity) {
        Site siteEntity = pageEntity.getSite();
        String text = pageEntity.getContent();
        Map<String, Long> lemmaMap = splitterEng.orElseThrow().splitTextToLemmas(text);
        lemmaMap.putAll(splitterRus.orElseThrow().splitTextToLemmas(text));
        Set<String> lemmas = lemmaMap.keySet();
        try {
            lemmaBuffer.addAll(lemmaRepository.saveAll(lemmas.stream().filter(x -> lemmaBuffer.stream()
                            .noneMatch(y -> y.getSite().getId() == siteEntity.getId() && y.getLemma().equals(x)))
                    .map(x -> new Lemma(siteEntity, x)).toList()));
            List<Lemma> result = lemmaBuffer.stream().filter(x -> lemmas.stream()
                    .anyMatch(y -> x.getSite().getId() == siteEntity.getId() && x.getLemma().equals(y))).toList();
            increaseFrequencies(result);
            return result.stream().map(lemmaEntity -> Map.entry(lemmaEntity, lemmaMap.get(lemmaEntity.getLemma())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        }
    }

    private void serializeIndex(Page pageEntity) {
        try {
            indexRepository.insertAll(Stream.of(taskPool.submit(() -> serializeLemmas(pageEntity))).peek(TASKS::add)
                    .flatMap(task -> {
                        try {
                            Map<Lemma, Long> result = task.join();
                            TASKS.remove(task);
                            return result.entrySet().stream()
                                    .map(mapEntry -> {
                                        record index(int page_id, int lemma_id, float rank) {}
                                        return new index(pageEntity.getId(), mapEntry.getKey().getId(),
                                                Float.valueOf(mapEntry.getValue()));
                                    });
                        } catch (CancellationException e) {
                            throw new CancellationException(IndexError.INTERRUPTED.toString());
                        }
                    }).map(indexRecord -> {
                        try {
                            return objectMapper.writeValueAsString(indexRecord);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.joining(",", "[", "]")));
        } catch (CancellationException e) {
            throw new CancellationException(IndexError.INTERRUPTED.toString());
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    private void increaseFrequencies(List<Lemma> lemmas) {
            lemmaRepository.updateFrequencies(lemmas, 1);
    }

    private void decreaseFrequencies(List<Lemma> lemmas) {
        lemmaRepository.updateFrequencies(lemmas, -1);
    }

    private WebResponse getURLConnection(URI url) throws IOException {
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setJavaScriptEnabled(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setPrintContentOnFailingStatusCode(false);
        return webClient.getPage(url.toURL() + "/").getWebResponse();
    }

    private Map<String, String> getConfigSite(String site_regex) {
        return sites.getSites().stream()
                .filter(config -> config.getUrl().matches(site_regex))
                .collect(Collectors.toMap(SiteList.SiteRecord::getUrl, SiteList.SiteRecord::getName));
    }
}
