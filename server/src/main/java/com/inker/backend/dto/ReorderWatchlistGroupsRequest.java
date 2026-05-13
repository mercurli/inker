package com.inker.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReorderWatchlistGroupsRequest {

    @NotNull
    private List<Long> groupIds;
}
