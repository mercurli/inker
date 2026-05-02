package com.inker.backend.dto;

import com.inker.backend.entity.Stock;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StockDto {
    Long id;
    String symbol;
    String name;
    Double latestPrice;
    Double changePercent;
    Double totalMarketValue;
    String market;
    String exchangeCode;
    String industry;
    List<String> concepts;
    String boardType;
    String listDate;

    public static StockDto fromEntity(Stock stock) {
        return StockDto.builder()
                .id(stock.getId())
                .symbol(stock.getCode())
                .name(stock.getName())
                .latestPrice(stock.getLatestPrice())
                .changePercent(stock.getChangePercent())
                .totalMarketValue(stock.getTotalMarketValue())
                .market(stock.getMarket())
                .exchangeCode(stock.getExchangeCode())
                .industry(stock.getIndustry())
                .concepts(List.copyOf(stock.getConcepts()))
                .boardType(stock.getBoardType())
                .listDate(stock.getListDate())
                .build();
    }
}
