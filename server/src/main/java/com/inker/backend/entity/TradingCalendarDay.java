package com.inker.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "trading_calendar_days",
        indexes = {
                @Index(name = "idx_trading_calendar_trade_date", columnList = "tradeDate", unique = true),
                @Index(name = "idx_trading_calendar_open_trade_date", columnList = "open, tradeDate")
        }
)
public class TradingCalendarDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private boolean open;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean holiday;

    @Column(length = 128)
    private String remark;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
