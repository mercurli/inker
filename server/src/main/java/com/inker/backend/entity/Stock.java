package com.inker.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "stocks",
        indexes = {
                @Index(name = "idx_stocks_code", columnList = "code", unique = true),
                @Index(name = "idx_stocks_name", columnList = "name"),
                @Index(name = "idx_stocks_exchange", columnList = "exchangeCode")
        }
)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 256)
    private String logo;

    @Column(nullable = false, length = 16)
    private String exchangeCode;

    @Column(nullable = false, length = 16)
    private String market;

    @Column(length = 64)
    private String industry;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stock_concepts", joinColumns = @JoinColumn(name = "stock_id"))
    @Column(name = "concept", nullable = false, length = 128)
    @OrderColumn(name = "sort_order")
    private List<String> concepts = new ArrayList<>();

    @Column(length = 16)
    private String listDate;

    @Column
    private Double latestPrice;

    @Column
    private Double changePercent;

    @Column
    private Double fiveDayChangePercent;

    @Column
    private Double volume;

    @Column
    private Double amount;

    @Column
    private Double turnoverRate;

    @Column
    private Double totalMarketValue;

    @Column
    private Double circulatingMarketValue;

    @Column
    private Double dynamicPeRatio;

    @Column(length = 16)
    private String boardType;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean conceptsManuallyEdited;

    @Column(nullable = false)
    private boolean st;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public void setConcepts(List<String> concepts) {
        this.concepts = concepts == null ? new ArrayList<>() : new ArrayList<>(concepts);
    }
}
