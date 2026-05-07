package com.inker.backend.dto;

import com.inker.backend.entity.Stock;
import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashSet;
import java.util.List;

@Value
@Builder
public class StockDto {
    Long id;
    String symbol;
    String name;
    Double latestPrice;
    Double changePercent;
    Double volume;
    Double amount;
    Double turnoverRate;
    Double totalMarketValue;
    Double circulatingMarketValue;
    Double dynamicPeRatio;
    String market;
    String exchangeCode;
    String industry;
    List<String> concepts;
    String primaryConcept;
    String boardType;
    String listDate;

    public static StockDto fromEntity(Stock stock) {
        List<String> concepts = normalizeConcepts(stock.getConcepts());

        return StockDto.builder()
                .id(stock.getId())
                .symbol(stock.getCode())
                .name(stock.getName())
                .latestPrice(stock.getLatestPrice())
                .changePercent(stock.getChangePercent())
                .volume(stock.getVolume())
                .amount(stock.getAmount())
                .turnoverRate(stock.getTurnoverRate())
                .totalMarketValue(stock.getTotalMarketValue())
                .circulatingMarketValue(stock.getCirculatingMarketValue())
                .dynamicPeRatio(stock.getDynamicPeRatio())
                .market(stock.getMarket())
                .exchangeCode(stock.getExchangeCode())
                .industry(stock.getIndustry())
                .concepts(concepts)
                .primaryConcept(primaryConcept(concepts))
                .boardType(stock.getBoardType())
                .listDate(stock.getListDate())
                .build();
    }

    public static List<String> normalizeConcepts(List<String> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalizedConcepts = new LinkedHashSet<>();
        for (String concept : concepts) {
            if (concept == null) {
                continue;
            }

            String normalized = concept.trim();
            if (!normalized.isBlank()) {
                normalizedConcepts.add(normalized);
            }
        }

        return List.copyOf(normalizedConcepts);
    }

    public static String primaryConcept(List<String> concepts) {
        return concepts == null || concepts.isEmpty() ? null : concepts.get(0);
    }
}
