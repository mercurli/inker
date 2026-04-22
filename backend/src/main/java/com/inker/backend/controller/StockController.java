package com.inker.backend.controller;

import com.inker.backend.dto.ImportResultDto;
import com.inker.backend.dto.MarketSummaryDto;
import com.inker.backend.dto.QuoteSyncResultDto;
import com.inker.backend.dto.StockDailyKLineDto;
import com.inker.backend.dto.StockDto;
import com.inker.backend.service.StockImportService;
import com.inker.backend.service.StockMarketDataService;
import com.inker.backend.service.StockQuoteSyncService;
import com.inker.backend.service.StockQueryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class StockController {

    private final StockQueryService stockQueryService;
    private final StockImportService stockImportService;
    private final StockMarketDataService stockMarketDataService;
    private final StockQuoteSyncService stockQuoteSyncService;

    public StockController(StockQueryService stockQueryService,
                           StockImportService stockImportService,
                           StockMarketDataService stockMarketDataService,
                           StockQuoteSyncService stockQuoteSyncService) {
        this.stockQueryService = stockQueryService;
        this.stockImportService = stockImportService;
        this.stockMarketDataService = stockMarketDataService;
        this.stockQuoteSyncService = stockQuoteSyncService;
    }

    @GetMapping("/stocks")
    public Page<StockDto> getStocks(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String exchangeCode,
                                    @RequestParam(required = false) String boardType,
                                    @RequestParam(required = false) String industry,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "code") String sortBy,
                                    @RequestParam(defaultValue = "ASC") String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        return stockQueryService.query(keyword, exchangeCode, boardType, industry, safePage, safeSize, sortBy, sortDirection);
    }

    @GetMapping("/stocks/industries")
    public List<String> getIndustries() {
        return stockQueryService.getAllIndustries();
    }

    @GetMapping("/stocks/{id}")
    public StockDto getStock(@PathVariable Long id) {
        return stockQueryService.getById(id)
                .orElseThrow(() -> new NoSuchElementException("Stock not found, id=" + id));
    }

    @GetMapping("/stocks/{id}/daily-k-line")
    public StockDailyKLineDto getDailyKLine(@PathVariable Long id,
                                            @RequestParam(required = false) Integer limit) {
        return stockMarketDataService.getDailyKLine(id, limit);
    }

    @GetMapping("/stocks/summary")
    public MarketSummaryDto getMarketSummary() {
        return stockQueryService.getMarketSummary();
    }

    @PostMapping("/stocks/import")
    public ImportResultDto importStocks() {
        return stockImportService.importAStocksExcludeStAndBse();
    }

    @PostMapping("/stocks/quotes/sync")
    public QuoteSyncResultDto syncQuotes() {
        return stockQuoteSyncService.syncDailyQuotesFromAkshare();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        result.put("message", "Inker API is running");
        return result;
    }
}
