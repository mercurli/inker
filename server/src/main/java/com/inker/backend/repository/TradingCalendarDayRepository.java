package com.inker.backend.repository;

import com.inker.backend.entity.TradingCalendarDay;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TradingCalendarDayRepository extends JpaRepository<TradingCalendarDay, Long>, JpaSpecificationExecutor<TradingCalendarDay> {

    Optional<TradingCalendarDay> findByTradeDate(LocalDate tradeDate);

    boolean existsByTradeDate(LocalDate tradeDate);

    List<TradingCalendarDay> findByTradeDateBetween(LocalDate startDate, LocalDate endDate);

    Optional<TradingCalendarDay> findFirstByOpenTrueOrderByTradeDateDesc();

    List<TradingCalendarDay> findByOpenTrueAndTradeDateLessThanOrderByTradeDateDesc(LocalDate tradeDate, Pageable pageable);
}
