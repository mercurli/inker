package com.inker.backend.controller;

import com.inker.backend.dto.CreateTradingCalendarDayRequest;
import com.inker.backend.dto.LatestOpenTradingDateDto;
import com.inker.backend.dto.TradingCalendarDayDto;
import com.inker.backend.dto.UpdateTradingCalendarDayRequest;
import com.inker.backend.service.TradingCalendarService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trading-calendar")
public class TradingCalendarController {

    private final TradingCalendarService tradingCalendarService;

    public TradingCalendarController(TradingCalendarService tradingCalendarService) {
        this.tradingCalendarService = tradingCalendarService;
    }

    @GetMapping
    public List<TradingCalendarDayDto> getDays(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Boolean open,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return tradingCalendarService.query(startDate, endDate, open, sortDirection);
    }

    @GetMapping("/latest-open")
    public LatestOpenTradingDateDto getLatestOpenDay() {
        return LatestOpenTradingDateDto.builder()
                .tradeDate(tradingCalendarService.resolveLatestOpenDate().orElse(null))
                .build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradingCalendarDayDto createDay(@Valid @RequestBody CreateTradingCalendarDayRequest request) {
        return tradingCalendarService.create(request.getTradeDate(), request.getOpen(), request.getHoliday(), request.getRemark());
    }

    @PatchMapping("/{id}")
    public TradingCalendarDayDto updateDay(@PathVariable Long id,
                                           @Valid @RequestBody UpdateTradingCalendarDayRequest request) {
        return tradingCalendarService.update(id, request.getOpen(), request.getHoliday(), request.getRemark());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDay(@PathVariable Long id) {
        tradingCalendarService.delete(id);
    }
}
