package com.inker.backend.service.provider;

import java.util.List;

public interface StockConceptProvider {
    List<String> fetchConcepts(String code, String market);
}
