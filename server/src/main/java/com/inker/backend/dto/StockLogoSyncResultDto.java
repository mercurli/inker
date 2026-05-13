package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StockLogoSyncResultDto {
    int scanned;
    int downloaded;
    int reusedLocal;
    int updated;
    int skipped;
    int replacedNonSvg;
    int deletedLegacyFiles;
    long remainingMissingLogo;
}
