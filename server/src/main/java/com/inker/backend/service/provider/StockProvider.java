package com.inker.backend.service.provider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface StockProvider {
    List<ProviderStock> fetchAllAStockCandidates();

    default List<String> fetchConcepts(String code, String market) {
        return Collections.emptyList();
    }

    default Optional<String> fetchCompanyWebsite(String code, String exchangeCode) {
        return Optional.empty();
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
