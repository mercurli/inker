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
    String market;
    String exchangeCode;
    String industry;
    List<String> concepts;
    String boardType;
    String listDate;
    LocalDateTime addedAt;

    public static WatchlistStockDto fromEntity(WatchlistGroupStock watchlistGroupStock) {
        Stock stock = watchlistGroupStock.getStock();

        return WatchlistStockDto.builder()
                .id(stock.getId())
                .symbol(stock.getCode())
                .name(stock.getName())
                .latestPrice(stock.getLatestPrice())
                .changePercent(stock.getChangePercent())
                .market(stock.getMarket())
                .exchangeCode(stock.getExchangeCode())
                .industry(stock.getIndustry())
                .concepts(List.copyOf(stock.getConcepts()))
                .boardType(stock.getBoardType())
                .listDate(stock.getListDate())
                .addedAt(watchlistGroupStock.getAddedAt())
                .build();
    }
}
