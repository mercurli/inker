package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class MarketSummaryDto {
    long total;
    long rising;
    long falling;
    long flat;
    LocalDateTime lastSyncedAt;
    LeaderDto strongest;
    java.util.List<DistributionBucketDto> distribution;

    @Value
    @Builder
    public static class LeaderDto {
        Long id;
        String symbol;
        String name;
        Double changePercent;
    }

    @Value
    @Builder
    public static class DistributionBucketDto {
        String label;
        long count;
        String tone;
    }
}
