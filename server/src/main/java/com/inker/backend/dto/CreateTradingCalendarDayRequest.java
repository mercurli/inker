package com.inker.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateTradingCalendarDayRequest {

    @NotNull
    private LocalDate tradeDate;

    @NotNull
    private Boolean open;

    private Boolean holiday;

    @Size(max = 128)
    private String remark;
}
