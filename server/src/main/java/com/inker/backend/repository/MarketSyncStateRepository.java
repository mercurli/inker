package com.inker.backend.repository;

import com.inker.backend.entity.MarketSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketSyncStateRepository extends JpaRepository<MarketSyncState, String> {
}
