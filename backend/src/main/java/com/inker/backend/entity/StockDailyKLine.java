package com.inker.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(
        name = "stock_daily_k_lines",
        indexes = {
                @Index(name = "idx_daily_kline_stock_trade_date", columnList = "stock_id,tradeDate", unique = true),
                @Index(name = "idx_daily_kline_trade_date", columnList = "tradeDate")
        }
)
public class StockDailyKLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(nullable = false)
    private LocalDate tradeDate;

    @Column
    private Double openPrice;

    @Column
    private Double closePrice;

    @Column
    private Double highPrice;

    @Column
    private Double lowPrice;

    @Column
    private Double volume;

    @Column
    private Double amount;

    @Column
    private Double preClosePrice;

    @Column
    private Double changeAmount;

    @Column
    private Double changePercent;
}
