package com.inker.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateWatchlistGroupRequest {

    @NotBlank
    @Size(max = 64)
    private String name;
}
