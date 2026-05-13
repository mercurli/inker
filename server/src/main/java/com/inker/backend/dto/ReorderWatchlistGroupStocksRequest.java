package com.inker.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReorderWatchlistGroupStocksRequest {

    @NotNull
    private List<Long> stockIds;
}
