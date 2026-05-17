package com.inker.backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTradingCalendarDayRequest {

    private Boolean open;

    private Boolean holiday;

    @Size(max = 128)
    private String remark;
}
