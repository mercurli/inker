package com.inker.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateStockConceptsRequest {
    @NotNull
    private List<String> concepts;
}
