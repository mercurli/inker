package com.inker.backend.repository;

import com.inker.backend.entity.WatchlistGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WatchlistGroupRepository extends JpaRepository<WatchlistGroup, Long> {

    Optional<WatchlistGroup> findByIsDefaultTrue();

    List<WatchlistGroup> findAllByOrderBySortOrderAscIdAsc();

    @Query("select coalesce(max(g.sortOrder), -1) from WatchlistGroup g")
    int findMaxSortOrder();
}
