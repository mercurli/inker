package com.inker.backend.service;

import com.inker.backend.dto.ImportResultDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import com.inker.backend.service.provider.StockProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockImportService {

    private static final Logger log = LoggerFactory.getLogger(StockImportService.class);
    private static final int BATCH_SIZE = 500;

    private final StockProvider stockProvider;
    private final StockRepository stockRepository;

    public StockImportService(StockProvider stockProvider, StockRepository stockRepository) {
        this.stockProvider = stockProvider;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public ImportResultDto importAStocksExcludeStAndBse() {
        log.info("Starting A-stock import...");

        List<StockProvider.ProviderStock> candidates = stockProvider.fetchAllAStockCandidates();
        log.info("Fetched {} candidates from provider", candidates.size());

        int skippedSt = 0;
        int skippedBeijing = 0;
        int skippedOther = 0;

        Map<String, Stock> existingByCode = new HashMap<>();
        for (Stock existing : stockRepository.findAll()) {
            existingByCode.put(existing.getCode(), existing);
        }
        log.info("Found {} existing stocks in database", existingByCode.size());

        List<Stock> toSave = new ArrayList<>();
        for (StockProvider.ProviderStock candidate : candidates) {
            if (isStStock(candidate.name())) {
                skippedSt++;
                continue;
            }
            if ("BSE".equals(candidate.exchangeCode()) || "BJ".equals(candidate.market())) {
                skippedBeijing++;
                continue;
            }
            if (!"SSE".equals(candidate.exchangeCode()) && !"SZSE".equals(candidate.exchangeCode())) {
                skippedOther++;
                continue;
            }

            Stock stock = existingByCode.getOrDefault(candidate.code(), new Stock());
            stock.setCode(candidate.code());
            stock.setName(candidate.name());
            stock.setExchangeCode(candidate.exchangeCode());
            stock.setMarket(candidate.market());
            if (candidate.industry() != null) {
                stock.setIndustry(candidate.industry());
            }
            stock.setConcepts(stockProvider.fetchConcepts(candidate.code(), candidate.market()));
            if (candidate.listDate() != null) {
                stock.setListDate(candidate.listDate());
            }
            if (candidate.latestPrice() != null) {
                stock.setLatestPrice(candidate.latestPrice());
            }
            if (candidate.changePercent() != null) {
                stock.setChangePercent(candidate.changePercent());
            }
            stock.setBoardType(determineBoardType(candidate.code()));
            stock.setSt(false);
            toSave.add(stock);
        }

        log.info("Saving {} stocks (skipped: ST={}, Beijing={}, Other={})",
                toSave.size(), skippedSt, skippedBeijing, skippedOther);

        // Batch save to avoid oversized transactions
        for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, toSave.size());
            stockRepository.saveAll(toSave.subList(i, end));
            stockRepository.flush();
            log.info("Saved batch {}-{} / {}", i, end, toSave.size());
        }

        log.info("Import completed: fetched={}, imported={}", candidates.size(), toSave.size());
        return ImportResultDto.builder()
                .fetched(candidates.size())
                .imported(toSave.size())
                .skippedSt(skippedSt)
                .skippedBeijingExchange(skippedBeijing)
                .build();
    }

    private boolean isStStock(String name) {
        if (name == null) {
            return false;
        }
        String upper = name.trim().toUpperCase();
        return upper.startsWith("ST")
                || upper.startsWith("*ST")
                || upper.startsWith("S ")
                || upper.equals("S");
    }

    private String determineBoardType(String code) {
        if (code == null || code.length() < 3) {
            return "未知";
        }
        if (code.startsWith("688")) {
            return "科创板";
        }
        if (code.startsWith("30")) {
            return "创业板";
        }
        if (code.startsWith("60") || code.startsWith("00")) {
            return "主板";
        }
        return "未知";
    }
}
