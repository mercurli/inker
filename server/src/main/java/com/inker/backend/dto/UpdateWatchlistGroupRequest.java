package com.inker.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateWatchlistGroupRequest {

    @Size(max = 64)
    private String name;

    @Min(0)
    @Max(9999)
    private Integer sortOrder;
}
