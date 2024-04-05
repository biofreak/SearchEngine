package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.searching.SearchResponse;
import searchengine.dto.searching.SearchResult;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.IndexRepository;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final IndexRepository indexRepository;

    private final double MENTION_COEFFICIENT = 0.7;

    public SearchResponse startSearch(String query, String site, Integer offset, Integer limit) {
        SearchResponse response = new SearchResponse();
        if (query.isEmpty()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
        } else {
            response.setResult(true);
            List<Site> siteList = site == null ? siteRepository.findAll() : List.of(siteRepository.findByUrl(site));
            List<String> lemmaList = getLemmasFromQuery(query, siteList);
            if(lemmaList.isEmpty()) {
                response.setCount(0);
            } else {
                List<Page> pageList;
                List<SearchResult> resultList;
                pageList = lemmaList.stream().flatMap(lemma -> siteList.stream().map(siteEntity ->
                                lemmaRepository.findBySiteAndLemma(siteEntity, lemma))
                        .flatMap(lemmaEntity -> indexRepository.findByLemma(lemmaEntity).stream()))
                        .map(Index::getPage).toList();

                for(int i = 0; i < lemmaList.size(); i++) pageList = getPagesFromLemma(lemmaList.get(i), pageList);
                resultList = getResultsFromPages(pageList, lemmaList.stream().flatMap(lemma ->
                        siteList.stream().map(siteEntity -> lemmaRepository.findBySiteAndLemma(siteEntity, lemma)))
                        .toList());
                response.setCount(resultList.size());
                response.setData(resultList);
            }
        }
        return response;
    }

    private List<Page> getPagesFromLemma(String lemma, List<Page> pageList) {
        return pageList.stream().filter(pageEntity -> {
            Lemma lemmaEntity = lemmaRepository.findBySiteAndLemma(pageEntity.getSite(), lemma);
            return Optional.ofNullable(indexRepository.findByPageAndLemma(pageEntity, lemmaEntity)).isPresent();
        }).toList();
    }

    private String getSnippetFromContent(String htmlCode, List<Lemma> lemmaList) {
        try {
            LuceneMorphology morphology = new RussianLuceneMorphology();

            return Jsoup.parse(htmlCode).getAllElements().stream().map(Element::ownText)
                    .filter(Predicate.not(String::isEmpty))
                    .flatMap(string -> Arrays.stream(string.split("(\\.(\\s+|\\z)|\\z)")))
                    .map(string -> {
                        List<String> boldList = Arrays.stream(string.replaceAll("[^а-яА-Я]", " ")
                                        .split("(\\s+|$)"))
                                .filter(Predicate.not(String::isEmpty))
                                .filter(word -> lemmaList.stream().map(Lemma::getLemma)
                                            .anyMatch(lemma -> lemma.equals(morphology
                                                    .getNormalForms(word.toLowerCase()).get(0))))
                                .toList();

                        String bolded = string;
                        for (String word : boldList) {
                            bolded = bolded.replaceAll(word, "<b>"+word+"</b>");
                        }

                        return Map.entry(boldList.size(), bolded);
                    }).filter(entry -> entry.getKey() > 0)
                    .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                    .limit(3).map(Map.Entry::getValue).collect(Collectors.joining("<br />"));
        } catch (IOException e) {
            return "";
        }
    }

    private List<String> getLemmasFromQuery(String query, List<Site> siteList) {
        try {
            SplitToLemmas splitter = SplitToLemmas.getInstance();
            return splitter.splitTextToLemmas(query).keySet().stream()
                    .map(lemma -> Map.entry(lemma, siteList.stream().mapToInt(siteEntity -> {
                           Lemma lemmaEntity = lemmaRepository.findBySiteAndLemma(siteEntity, lemma);
                           return lemmaEntity != null ? lemmaEntity.getFrequency() : 0;
                    }).sum()))
                    .filter(entry -> entry.getValue() > 0)
                    .sorted(Map.Entry.comparingByValue())
                    .filter(entry -> {
                        int pageTotal = siteList.stream().mapToInt(siteEntity ->
                                pageRepository.findBySite(siteEntity).size()).sum();
                        return pageTotal > 0; // && (double) (entry.getValue() / pageTotal) > MENTION_COEFFICIENT;
                    }).map(Map.Entry::getKey)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<SearchResult> getResultsFromPages(List<Page> pageList, List<Lemma> lemmaList) {
        Map<Page,Double> rankMap = pageList.stream().map(pageEntity -> Map.entry(pageEntity,
                                indexRepository.findByPage(pageEntity).stream().mapToDouble(Index::getRank).sum()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        double maxRank = rankMap.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getValue).orElse(0.0);

        return pageList.stream().map(pageEntity -> {
            SearchResult result = new SearchResult();
            Site siteEntity = pageEntity.getSite();
            result.setSite(siteEntity.getUrl());
            result.setSiteName(siteEntity.getName());
            result.setUri(pageEntity.getPath());
            result.setTitle(Jsoup.parse(pageEntity.getContent()).getElementsByTag("title").text());
            result.setSnippet(getSnippetFromContent(pageEntity.getContent(), lemmaList));
            result.setRelevance(maxRank > 0.0 ? rankMap.get(pageEntity) / maxRank : 0.0);
            return result;
        }).sorted(Comparator.comparingDouble(SearchResult::getRelevance).reversed()).toList();
    }
}
