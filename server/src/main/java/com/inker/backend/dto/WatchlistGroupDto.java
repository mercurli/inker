package com.inker.backend.dto;

import com.inker.backend.entity.WatchlistGroup;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder
public class WatchlistGroupDto {
    Long id;
    String name;
    boolean isDefault;
    int sortOrder;
    long stockCount;
    Double averageChangePercent;
    Map<String, Long> industryCounts;
    Map<String, Long> primaryConceptCounts;

    public static WatchlistGroupDto fromEntity(WatchlistGroup group, long stockCount) {
        return fromEntity(group, stockCount, null, Map.of(), Map.of());
    }

    public static WatchlistGroupDto fromEntity(WatchlistGroup group, long stockCount, Map<String, Long> industryCounts) {
        return fromEntity(group, stockCount, null, industryCounts, Map.of());
    }

    public static WatchlistGroupDto fromEntity(WatchlistGroup group,
                                               long stockCount,
                                               Map<String, Long> industryCounts,
                                               Map<String, Long> primaryConceptCounts) {
        return fromEntity(group, stockCount, null, industryCounts, primaryConceptCounts);
    }

    public static WatchlistGroupDto fromEntity(WatchlistGroup group,
                                               long stockCount,
                                               Double averageChangePercent,
                                               Map<String, Long> industryCounts,
                                               Map<String, Long> primaryConceptCounts) {
        Map<String, Long> normalizedIndustryCounts = industryCounts == null ? Map.of() : industryCounts;
        Map<String, Long> normalizedPrimaryConceptCounts = primaryConceptCounts == null ? Map.of() : primaryConceptCounts;

        return WatchlistGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .isDefault(group.isDefault())
                .sortOrder(group.getSortOrder())
                .stockCount(stockCount)
                .averageChangePercent(averageChangePercent)
                .industryCounts(Collections.unmodifiableMap(new LinkedHashMap<>(normalizedIndustryCounts)))
                .primaryConceptCounts(Collections.unmodifiableMap(new LinkedHashMap<>(normalizedPrimaryConceptCounts)))
                .build();
    }
}
