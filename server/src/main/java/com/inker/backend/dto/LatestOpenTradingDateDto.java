package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class LatestOpenTradingDateDto {
    LocalDate tradeDate;
}
