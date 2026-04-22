package com.inker.backend.service;

import com.inker.backend.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StockBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockBootstrapService.class);

    private final StockRepository stockRepository;
    private final StockImportService stockImportService;

    public StockBootstrapService(StockRepository stockRepository, StockImportService stockImportService) {
        this.stockRepository = stockRepository;
        this.stockImportService = stockImportService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long totalCount = stockRepository.count();
        long priceCount = stockRepository.countByLatestPriceIsNotNull();
        log.info("Bootstrap check: totalStocks={}, withPrice={}", totalCount, priceCount);

        if (totalCount < 1000 || priceCount == 0) {
            log.info("Stock data incomplete (total={}, withPrice={}), performing full import...",
                    totalCount, priceCount);
            stockImportService.importAStocksExcludeStAndBse();
        } else {
            log.info("Stock data looks complete, skipping bootstrap import");
        }
    }
}
