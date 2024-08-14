package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.searching.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;


    public ApiController(StatisticsService statisticsService,
                         IndexingService indexingService,
                         SearchService searchService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchService = searchService;
    }

    @GetMapping("/statistics")
    @ResponseBody
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    @ResponseBody
    public ResponseEntity<IndexingResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.fullIndex());
    }

    @GetMapping("/stopIndexing")
    @ResponseBody
    public ResponseEntity<IndexingResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndex());
    }

    @PostMapping("/indexPage")
    @ResponseBody
    public ResponseEntity<IndexingResponse> indexPage(@RequestParam(name = "url") String link) {
        return ResponseEntity.ok(indexingService.addIndex(link));
    }

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<SearchResponse> search(@RequestParam(name = "query") String query,
                                                 @RequestParam(name = "site", required = false) String site,
                                                 @RequestParam(name = "offset", required = false) Integer offset,
                                                 @RequestParam(name = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(searchService.startSearch(query, site,
                Optional.ofNullable(offset).isPresent() ? offset : 0,
                Optional.ofNullable(limit).isPresent() ? limit : 20));
    }
}
