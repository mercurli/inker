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
    Map<String, Long> industryCounts;

    public static WatchlistGroupDto fromEntity(WatchlistGroup group, long stockCount) {
        return fromEntity(group, stockCount, Map.of());
    }

    public static WatchlistGroupDto fromEntity(WatchlistGroup group, long stockCount, Map<String, Long> industryCounts) {
        Map<String, Long> normalizedIndustryCounts = industryCounts == null ? Map.of() : industryCounts;

        return WatchlistGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .isDefault(group.isDefault())
                .sortOrder(group.getSortOrder())
                .stockCount(stockCount)
                .industryCounts(Collections.unmodifiableMap(new LinkedHashMap<>(normalizedIndustryCounts)))
                .build();
    }
}
