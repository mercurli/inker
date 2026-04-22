package com.inker.backend.service.provider;

import java.util.Collections;
import java.util.List;

public interface StockProvider {
    List<ProviderStock> fetchAllAStockCandidates();

    default List<String> fetchConcepts(String code, String market) {
        return Collections.emptyList();
    }

    record ProviderStock(
            String code,
            String name,
            String exchangeCode,
            String market,
            String industry,
            String listDate,
            Double latestPrice,
            Double changePercent
    ) {
    }
}
