package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class StockDailyKLineDto {
    String symbol;
    String name;
    List<CandleDto> candles;

    @Value
    @Builder
    public static class CandleDto {
        String tradeDate;
        double openPrice;
        double closePrice;
        double highPrice;
        double lowPrice;
        double volume;
        double amount;
        double changePercent;
    }
}
