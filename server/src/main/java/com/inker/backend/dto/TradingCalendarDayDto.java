package com.inker.backend.dto;

import com.inker.backend.entity.TradingCalendarDay;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class TradingCalendarDayDto {
    Long id;
    LocalDate tradeDate;
    boolean open;
    boolean holiday;
    String remark;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static TradingCalendarDayDto from(TradingCalendarDay day) {
        return TradingCalendarDayDto.builder()
                .id(day.getId())
                .tradeDate(day.getTradeDate())
                .open(day.isOpen())
                .holiday(day.isHoliday())
                .remark(day.getRemark())
                .createdAt(day.getCreatedAt())
                .updatedAt(day.getUpdatedAt())
                .build();
    }
}
