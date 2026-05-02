package com.inker.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "market_sync_state")
public class MarketSyncState {

    @Id
    @Column(length = 64)
    private String stateKey;

    @Column(nullable = false, length = 64)
    private String stateValue;

    @Column(nullable = false, length = 64)
    private String source;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
