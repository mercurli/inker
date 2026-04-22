package com.inker.backend.repository;

import com.inker.backend.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    Optional<Stock> findByCode(String code);

    long countByLatestPriceIsNotNull();

    long countByChangePercentGreaterThan(double value);

    long countByChangePercentLessThan(double value);

    Optional<Stock> findFirstByChangePercentIsNotNullOrderByChangePercentDesc();

    @Query("SELECT DISTINCT s.industry FROM Stock s WHERE s.industry IS NOT NULL ORDER BY s.industry")
    List<String> findDistinctIndustries();

    @Query("SELECT s.changePercent FROM Stock s")
    List<Double> findAllChangePercents();

    @Query("SELECT MAX(s.updatedAt) FROM Stock s")
    Optional<LocalDateTime> findLastUpdatedAt();
}
