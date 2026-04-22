package com.inker.backend.repository;

import com.inker.backend.entity.StockDailyKLine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface StockDailyKLineRepository extends JpaRepository<StockDailyKLine, Long> {

    List<StockDailyKLine> findByStockIdOrderByTradeDateDesc(Long stockId, Pageable pageable);

    List<StockDailyKLine> findByStockIdAndTradeDateIn(Long stockId, Collection<LocalDate> tradeDates);

    long countByStockId(Long stockId);
}
