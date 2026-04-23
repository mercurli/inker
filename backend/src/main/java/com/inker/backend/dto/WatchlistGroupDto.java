package com.inker.backend.dto;

import com.inker.backend.entity.WatchlistGroup;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WatchlistGroupDto {
    Long id;
    String name;
    boolean isDefault;
    int sortOrder;
    long stockCount;

    public static WatchlistGroupDto fromEntity(WatchlistGroup group, long stockCount) {
        return WatchlistGroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .isDefault(group.isDefault())
                .sortOrder(group.getSortOrder())
                .stockCount(stockCount)
                .build();
    }
}
