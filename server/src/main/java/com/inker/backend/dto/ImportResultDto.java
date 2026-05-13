package com.inker.backend.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ImportResultDto {
    int fetched;
    int imported;
    int skippedSt;
    int skippedBeijingExchange;
}
