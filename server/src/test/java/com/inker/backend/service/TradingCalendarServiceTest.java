package com.inker.backend.service;

import com.inker.backend.dto.TradingCalendarDayDto;
import com.inker.backend.repository.TradingCalendarDayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(TradingCalendarService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class TradingCalendarServiceTest {

    @Autowired
    private TradingCalendarService tradingCalendarService;

    @Autowired
    private TradingCalendarDayRepository tradingCalendarDayRepository;

    @BeforeEach
    void setUp() {
        tradingCalendarDayRepository.deleteAll();
    }

    @Test
    void shouldCreateUpdateAndDeleteTradingCalendarDay() {
        TradingCalendarDayDto created = tradingCalendarService.create(LocalDate.parse("2026-05-06"), true, " 节后开市 ");

        assertEquals(LocalDate.parse("2026-05-06"), created.getTradeDate());
        assertTrue(created.isOpen());
        assertEquals(false, created.isHoliday());
        assertEquals("节后开市", created.getRemark());

        TradingCalendarDayDto updated = tradingCalendarService.update(created.getId(), false, true, "");
        assertEquals(false, updated.isOpen());
        assertEquals(true, updated.isHoliday());
        assertEquals(null, updated.getRemark());

        tradingCalendarService.delete(created.getId());
        assertTrue(tradingCalendarDayRepository.findById(created.getId()).isEmpty());
    }

    @Test
    void shouldRejectDuplicateTradeDate() {
        tradingCalendarService.create(LocalDate.parse("2026-05-06"), true, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tradingCalendarService.create(LocalDate.parse("2026-05-06"), false, "duplicate"));

        assertEquals("Trading calendar day already exists, tradeDate=2026-05-06", exception.getMessage());
    }

    @Test
    void shouldQueryByRangeAndOpenFlag() {
        tradingCalendarService.create(LocalDate.parse("2026-05-01"), false, "劳动节");
        tradingCalendarService.create(LocalDate.parse("2026-05-04"), true, null);
        tradingCalendarService.create(LocalDate.parse("2026-05-05"), true, null);

        List<TradingCalendarDayDto> result = tradingCalendarService.query(
                LocalDate.parse("2026-05-02"),
                LocalDate.parse("2026-05-05"),
                true,
                "ASC"
        );

        assertEquals(List.of(LocalDate.parse("2026-05-04"), LocalDate.parse("2026-05-05")),
                result.stream().map(TradingCalendarDayDto::getTradeDate).toList());
    }

    @Test
    void shouldResolveLatestAndNthPreviousOpenDate() {
        LocalDate today = LocalDate.now();

        if (today.getDayOfWeek().getValue() <= 5) {
            assertEquals(Optional.of(today), tradingCalendarService.resolveLatestOpenDate());
        } else {
            assertTrue(tradingCalendarService.resolveLatestOpenDate().orElseThrow().isBefore(today));
        }
        assertEquals(Optional.of(LocalDate.parse("2026-04-23")),
                tradingCalendarService.resolveNthPreviousOpenDate(LocalDate.parse("2026-04-30"), 5));
        assertEquals(Optional.of(LocalDate.parse("2026-04-22")),
                tradingCalendarService.resolveNthPreviousOpenDate(LocalDate.parse("2026-04-30"), 6));
    }

    @Test
    void shouldUseWeekdayOpenAndWeekendClosedDefaults() {
        assertEquals(Optional.of(LocalDate.parse("2026-05-08")),
                tradingCalendarService.resolveNthPreviousOpenDate(LocalDate.parse("2026-05-11"), 1));
        assertEquals(Optional.of(LocalDate.parse("2026-05-07")),
                tradingCalendarService.resolveNthPreviousOpenDate(LocalDate.parse("2026-05-11"), 2));
    }

    @Test
    void shouldApplyManualOverridesOnTopOfDefaultWeekRules() {
        tradingCalendarService.create(LocalDate.parse("2026-05-07"), false, true, "特殊休市");
        tradingCalendarService.create(LocalDate.parse("2026-05-09"), true, "周末调休");

        assertEquals(Optional.of(LocalDate.parse("2026-05-09")),
                tradingCalendarService.resolveNthPreviousOpenDate(LocalDate.parse("2026-05-11"), 1));
        assertEquals(Optional.of(LocalDate.parse("2026-05-08")),
                tradingCalendarService.resolveNthPreviousOpenDate(LocalDate.parse("2026-05-11"), 2));
        assertEquals(Optional.of(LocalDate.parse("2026-05-06")),
                tradingCalendarService.resolveNthPreviousOpenDate(LocalDate.parse("2026-05-11"), 3));
    }

    @Test
    void shouldStoreHolidayMarkerWithoutChangingOpenDateRulesBeyondOpenFlag() {
        TradingCalendarDayDto holiday = tradingCalendarService.create(LocalDate.parse("2026-10-01"), false, true, "国庆节");

        assertEquals(false, holiday.isOpen());
        assertEquals(true, holiday.isHoliday());
        assertEquals("国庆节", holiday.getRemark());
    }
}
