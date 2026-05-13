package com.inker.backend.controller;

import com.inker.backend.dto.ImportResultDto;
import com.inker.backend.dto.MarketSummaryDto;
import com.inker.backend.dto.QuoteSyncProgressDto;
import com.inker.backend.dto.QuoteSyncResultDto;
import com.inker.backend.dto.StockCleanupResultDto;
import com.inker.backend.dto.StockDailyKLineDto;
import com.inker.backend.dto.StockDto;
import com.inker.backend.dto.StockLogoSyncResultDto;
import com.inker.backend.dto.UpdateStockConceptsRequest;
import com.inker.backend.service.StockCleanupService;
import com.inker.backend.service.StockImportService;
import com.inker.backend.service.StockMarketDataService;
import com.inker.backend.service.StockLogoSyncService;
import com.inker.backend.service.StockQuoteSyncService;
import com.inker.backend.service.StockQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1")
public class StockController {

    private final StockQueryService stockQueryService;
    private final StockImportService stockImportService;
    private final StockMarketDataService stockMarketDataService;
    private final StockQuoteSyncService stockQuoteSyncService;
    private final StockCleanupService stockCleanupService;
    private final StockLogoSyncService stockLogoSyncService;

    public StockController(StockQueryService stockQueryService,
                           StockImportService stockImportService,
                           StockMarketDataService stockMarketDataService,
                           StockQuoteSyncService stockQuoteSyncService,
                           StockCleanupService stockCleanupService,
                           StockLogoSyncService stockLogoSyncService) {
        this.stockQueryService = stockQueryService;
        this.stockImportService = stockImportService;
        this.stockMarketDataService = stockMarketDataService;
        this.stockQuoteSyncService = stockQuoteSyncService;
        this.stockCleanupService = stockCleanupService;
        this.stockLogoSyncService = stockLogoSyncService;
    }

    @GetMapping("/stocks")
    public Page<StockDto> getStocks(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String exchangeCode,
                                    @RequestParam(required = false) String boardType,
                                    @RequestParam(required = false) String industry,
                                    @RequestParam(required = false) String concept,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(defaultValue = "code") String sortBy,
                                    @RequestParam(defaultValue = "ASC") String sortDirection) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        return stockQueryService.query(keyword, exchangeCode, boardType, industry, concept, safePage, safeSize, sortBy, sortDirection);
    }

    @GetMapping("/stocks/industries")
    public List<String> getIndustries() {
        return stockQueryService.getAllIndustries();
    }

    @GetMapping("/stocks/concepts")
    public List<String> getConcepts() {
        return stockQueryService.getAllConcepts();
    }

    @GetMapping("/stocks/{id}")
    public StockDto getStock(@PathVariable Long id) {
        return stockQueryService.getById(id)
                .orElseThrow(() -> new NoSuchElementException("Stock not found, id=" + id));
    }

    @PatchMapping("/stocks/{id}/concepts")
    public StockDto updateStockConcepts(@PathVariable Long id,
                                        @Valid @RequestBody UpdateStockConceptsRequest request) {
        return stockQueryService.updateConcepts(id, request);
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

    @PostMapping("/stocks/logos/sync")
    public StockLogoSyncResultDto syncStockLogos(@RequestParam(defaultValue = "100") int limit) {
        return stockLogoSyncService.syncMissingLogos(limit);
    }

    @PostMapping("/stocks/quotes/sync")
    public QuoteSyncResultDto syncQuotes() {
        return stockQuoteSyncService.syncDailyQuotesFromTushare();
    }

    @PostMapping("/stocks/cleanup/historical")
    public StockCleanupResultDto cleanupHistoricalStocks() {
        return stockCleanupService.cleanupHistoricalStocks();
    }

    @GetMapping(value = "/stocks/quotes/sync/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamQuoteSync() {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                stockQuoteSyncService.syncDailyQuotesWithProgress(progress -> sendProgress(emitter, progress));
                emitter.complete();
            } catch (Exception exception) {
                sendProgress(emitter, QuoteSyncProgressDto.builder()
                        .stage("failed")
                        .percent(100)
                        .message(exception.getMessage() == null ? "行情同步失败" : exception.getMessage())
                        .build());
                emitter.complete();
            }
        });

        return emitter;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        result.put("message", "Inker API is running");
        return result;
    }

    private void sendProgress(SseEmitter emitter, QuoteSyncProgressDto progress) {
        try {
            SseEmitter.SseEventBuilder event = SseEmitter.event()
                    .name(progress.getStage())
                    .data(progress, MediaType.APPLICATION_JSON);
            emitter.send(event);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send quote sync progress", exception);
        }
    }
}
