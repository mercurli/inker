package com.inker.backend.service;

import com.inker.backend.dto.TradingCalendarDayDto;
import com.inker.backend.entity.TradingCalendarDay;
import com.inker.backend.repository.TradingCalendarDayRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TradingCalendarService {

    private static final int OPEN_DATE_LOOKBACK_DAYS = 3_700;

    private final TradingCalendarDayRepository tradingCalendarDayRepository;

    public TradingCalendarService(TradingCalendarDayRepository tradingCalendarDayRepository) {
        this.tradingCalendarDayRepository = tradingCalendarDayRepository;
    }

    @Transactional(readOnly = true)
    public List<TradingCalendarDayDto> query(LocalDate startDate, LocalDate endDate, Boolean open, String sortDirection) {
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Specification<TradingCalendarDay> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("tradeDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("tradeDate"), endDate));
            }
            if (open != null) {
                predicates.add(criteriaBuilder.equal(root.get("open"), open));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };

        return tradingCalendarDayRepository.findAll(specification, Sort.by(direction, "tradeDate")).stream()
                .map(TradingCalendarDayDto::from)
                .toList();
    }

    @Transactional
    public TradingCalendarDayDto create(LocalDate tradeDate, Boolean open, String remark) {
        return create(tradeDate, open, false, remark);
    }

    @Transactional
    public TradingCalendarDayDto create(LocalDate tradeDate, Boolean open, Boolean holiday, String remark) {
        if (tradingCalendarDayRepository.existsByTradeDate(tradeDate)) {
            throw new IllegalArgumentException("Trading calendar day already exists, tradeDate=" + tradeDate);
        }

        TradingCalendarDay day = new TradingCalendarDay();
        day.setTradeDate(tradeDate);
        day.setOpen(Boolean.TRUE.equals(open));
        day.setHoliday(Boolean.TRUE.equals(holiday));
        day.setRemark(normalizeRemark(remark));
        return TradingCalendarDayDto.from(tradingCalendarDayRepository.save(day));
    }

    @Transactional
    public TradingCalendarDayDto update(Long id, Boolean open, String remark) {
        return update(id, open, null, remark);
    }

    @Transactional
    public TradingCalendarDayDto update(Long id, Boolean open, Boolean holiday, String remark) {
        TradingCalendarDay day = tradingCalendarDayRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Trading calendar day not found, id=" + id));

        if (open != null) {
            day.setOpen(open);
        }
        if (holiday != null) {
            day.setHoliday(holiday);
        }
        if (remark != null) {
            day.setRemark(normalizeRemark(remark));
        }
        return TradingCalendarDayDto.from(tradingCalendarDayRepository.save(day));
    }

    @Transactional
    public void delete(Long id) {
        if (!tradingCalendarDayRepository.existsById(id)) {
            throw new NoSuchElementException("Trading calendar day not found, id=" + id);
        }
        tradingCalendarDayRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> resolveLatestOpenDate() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(OPEN_DATE_LOOKBACK_DAYS);
        Map<LocalDate, TradingCalendarDay> overrideByDate = findOverridesByDate(startDate, endDate);
        for (LocalDate date = endDate; !date.isBefore(startDate); date = date.minusDays(1)) {
            if (isOpenDate(date, overrideByDate.get(date))) {
                return Optional.of(date);
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> resolveNthPreviousOpenDate(LocalDate tradeDate, int n) {
        if (tradeDate == null || n < 1) {
            return Optional.empty();
        }

        LocalDate endDate = tradeDate.minusDays(1);
        LocalDate startDate = tradeDate.minusDays(OPEN_DATE_LOOKBACK_DAYS);
        Map<LocalDate, TradingCalendarDay> overrideByDate = findOverridesByDate(startDate, endDate);
        int openDateCount = 0;
        for (LocalDate date = endDate; !date.isBefore(startDate); date = date.minusDays(1)) {
            if (isOpenDate(date, overrideByDate.get(date))) {
                openDateCount++;
                if (openDateCount == n) {
                    return Optional.of(date);
                }
            }
        }
        return Optional.empty();
    }

    private Map<LocalDate, TradingCalendarDay> findOverridesByDate(LocalDate startDate, LocalDate endDate) {
        return tradingCalendarDayRepository.findByTradeDateBetween(startDate, endDate).stream()
                .collect(Collectors.toMap(TradingCalendarDay::getTradeDate, Function.identity()));
    }

    private boolean isOpenDate(LocalDate date, TradingCalendarDay override) {
        if (override != null) {
            return override.isOpen();
        }
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
