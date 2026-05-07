package com.inker.backend.dto;

import com.inker.backend.entity.Stock;
import com.inker.backend.entity.WatchlistGroupStock;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class WatchlistStockDto {
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
    LocalDateTime addedAt;

    public static WatchlistStockDto fromEntity(WatchlistGroupStock watchlistGroupStock) {
        Stock stock = watchlistGroupStock.getStock();
        List<String> concepts = StockDto.normalizeConcepts(stock.getConcepts());

        return WatchlistStockDto.builder()
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
                .primaryConcept(StockDto.primaryConcept(concepts))
                .boardType(stock.getBoardType())
                .listDate(stock.getListDate())
                .addedAt(watchlistGroupStock.getAddedAt())
                .build();
    }
}
