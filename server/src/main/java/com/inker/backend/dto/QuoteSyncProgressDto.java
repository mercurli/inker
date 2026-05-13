package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuoteSyncProgressDto {
    String stage;
    int percent;
    String message;
    String tradeDate;
    int fetched;
    int matched;
    int updated;
    int skippedMissing;
    String fiveDayBaselineTradeDate;
    int fiveDayBaselineCount;
    QuoteSyncResultDto result;
}
