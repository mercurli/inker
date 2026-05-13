package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class StockCleanupResultDto {
    int scanned;
    int deleted;
    long removedWatchlistRelations;
    long removedDailyKLines;
    Map<String, Integer> reasonCounts;
    List<StockCleanupSampleDto> samples;

    @Value
    @Builder
    public static class StockCleanupSampleDto {
        String code;
        String name;
        String reason;
    }
}
