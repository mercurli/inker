package com.inker.backend.scheduler;

import com.inker.backend.service.StockImportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StockSyncScheduler {

    private final StockImportService stockImportService;

    public StockSyncScheduler(StockImportService stockImportService) {
        this.stockImportService = stockImportService;
    }

    @Scheduled(cron = "0 30 6 * * ?", zone = "Asia/Shanghai")
    public void syncEveryTradingDayMorning() {
        stockImportService.importAStocksExcludeStAndBse();
    }
}
