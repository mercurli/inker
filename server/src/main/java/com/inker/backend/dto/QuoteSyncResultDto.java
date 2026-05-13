package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QuoteSyncResultDto {
    String source;
    int fetched;
    int matched;
    int updated;
    int skippedMissing;
    String fiveDayBaselineTradeDate;
    int fiveDayBaselineCount;
}
