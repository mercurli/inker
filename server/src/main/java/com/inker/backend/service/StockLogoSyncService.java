package com.inker.backend.service;

import com.inker.backend.dto.StockLogoSyncResultDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockLogoSyncService {

    private final StockRepository stockRepository;
    private final StockLogoService stockLogoService;
    private final Set<String> skippedCodesThisRun = ConcurrentHashMap.newKeySet();

    public StockLogoSyncService(StockRepository stockRepository,
                                StockLogoService stockLogoService) {
        this.stockRepository = stockRepository;
        this.stockLogoService = stockLogoService;
    }

    public StockLogoSyncResultDto syncMissingLogos(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Stock> candidates = stockRepository.findAll(Sort.by("code")).stream()
                .filter(this::needsLogoSync)
                .filter(stock -> !skippedCodesThisRun.contains(stock.getCode()))
                .limit(safeLimit)
                .toList();

        int downloaded = 0;
        int reusedLocal = 0;
        int updated = 0;
        int skipped = 0;
        int replacedNonSvg = 0;
        int deletedLegacyFiles = 0;

        for (Stock stock : candidates) {
            boolean hadNonSvgLogo = stock.getLogo() != null
                    && !stock.getLogo().isBlank()
                    && !isSvgLogo(stock.getLogo());
            Optional<String> logo = stockLogoService.findExistingLogo(stock.getCode());
            if (logo.isPresent()) {
                reusedLocal++;
            } else {
                logo = stockLogoService.downloadLogo(stock.getCode(), null);
                if (logo.isPresent()) {
                    downloaded++;
                }
            }

            if (logo.isPresent()) {
                stock.setLogo(logo.get());
                stockRepository.saveAndFlush(stock);
                updated++;
                if (hadNonSvgLogo) {
                    replacedNonSvg++;
                }
                deletedLegacyFiles += stockLogoService.deleteLegacyLogos(stock.getCode());
            } else {
                skipped++;
                skippedCodesThisRun.add(stock.getCode());
            }
        }

        return StockLogoSyncResultDto.builder()
                .scanned(candidates.size())
                .downloaded(downloaded)
                .reusedLocal(reusedLocal)
                .updated(updated)
                .skipped(skipped)
                .replacedNonSvg(replacedNonSvg)
                .deletedLegacyFiles(deletedLegacyFiles)
                .remainingMissingLogo(countPendingLogoSync())
                .build();
    }

    private long countPendingLogoSync() {
        return stockRepository.findAll().stream()
                .filter(this::needsLogoSync)
                .count();
    }

    private boolean needsLogoSync(Stock stock) {
        return stock.getLogo() == null || stock.getLogo().isBlank() || !isSvgLogo(stock.getLogo());
    }

    private boolean isSvgLogo(String logo) {
        return logo != null && logo.trim().toLowerCase().endsWith(".svg");
    }
}
