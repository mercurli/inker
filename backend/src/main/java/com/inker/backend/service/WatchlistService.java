package com.inker.backend.service;

import com.inker.backend.dto.WatchlistGroupDto;
import com.inker.backend.dto.WatchlistStockDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.entity.WatchlistGroup;
import com.inker.backend.entity.WatchlistGroupStock;
import com.inker.backend.repository.StockRepository;
import com.inker.backend.repository.WatchlistGroupRepository;
import com.inker.backend.repository.WatchlistGroupStockRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class WatchlistService {

    private static final String DEFAULT_GROUP_NAME = "默认分组";

    private final WatchlistGroupRepository watchlistGroupRepository;
    private final WatchlistGroupStockRepository watchlistGroupStockRepository;
    private final StockRepository stockRepository;

    public WatchlistService(WatchlistGroupRepository watchlistGroupRepository,
                            WatchlistGroupStockRepository watchlistGroupStockRepository,
                            StockRepository stockRepository) {
        this.watchlistGroupRepository = watchlistGroupRepository;
        this.watchlistGroupStockRepository = watchlistGroupStockRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public List<WatchlistGroupDto> getGroups() {
        ensureDefaultGroup();
        List<WatchlistGroup> groups = watchlistGroupRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Map<String, Long>> industryCounts = new HashMap<>();

        groups.forEach(group -> counts.put(group.getId(), watchlistGroupStockRepository.countByGroupId(group.getId())));
        watchlistGroupStockRepository.countIndustriesByGroup()
                .forEach(item -> industryCounts
                        .computeIfAbsent(item.getGroupId(), ignored -> new LinkedHashMap<>())
                        .put(item.getIndustry(), item.getStockCount()));

        return groups.stream()
                .map(group -> WatchlistGroupDto.fromEntity(
                        group,
                        counts.getOrDefault(group.getId(), 0L),
                        industryCounts.getOrDefault(group.getId(), Map.of())
                ))
                .toList();
    }

    @Transactional
    public WatchlistGroupDto createGroup(String name) {
        WatchlistGroup group = new WatchlistGroup();
        group.setName(normalizeGroupName(name));
        group.setDefault(false);
        group.setSortOrder(watchlistGroupRepository.findMaxSortOrder() + 1);
        WatchlistGroup savedGroup = watchlistGroupRepository.save(group);

        return WatchlistGroupDto.fromEntity(savedGroup, 0);
    }

    @Transactional
    public WatchlistGroupDto updateGroup(Long groupId, String name, Integer sortOrder) {
        WatchlistGroup group = getGroupOrThrow(groupId);

        if (name != null) {
            group.setName(normalizeGroupName(name));
        }

        if (sortOrder != null) {
            group.setSortOrder(Math.max(0, sortOrder));
        }

        WatchlistGroup saved = watchlistGroupRepository.save(group);
        long count = watchlistGroupStockRepository.countByGroupId(saved.getId());

        return WatchlistGroupDto.fromEntity(saved, count);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        WatchlistGroup group = getGroupOrThrow(groupId);
        List<Long> stockIds = watchlistGroupStockRepository.findStockIdsByGroupId(groupId);

        if (!stockIds.isEmpty()) {
            watchlistGroupStockRepository.deleteByStockIdIn(stockIds);
        }

        watchlistGroupRepository.delete(group);

        if (group.isDefault()) {
            ensureDefaultGroup();
        }
    }

    @Transactional
    public List<WatchlistStockDto> getGroupStocks(Long groupId) {
        getGroupOrThrow(groupId);

        return watchlistGroupStockRepository.findAllByGroupIdOrderByAddedAtDescIdDesc(groupId)
                .stream()
                .map(WatchlistStockDto::fromEntity)
                .toList();
    }

    @Transactional
    public void ensureStockInDefaultGroup(Long stockId) {
        WatchlistGroup defaultGroup = ensureDefaultGroup();
        ensureStockInGroup(defaultGroup.getId(), stockId);
    }

    @Transactional
    public void ensureStockInGroup(Long groupId, Long stockId) {
        WatchlistGroup group = getGroupOrThrow(groupId);
        Stock stock = getStockOrThrow(stockId);

        if (watchlistGroupStockRepository.existsByGroupIdAndStockId(groupId, stockId)) {
            return;
        }

        WatchlistGroupStock relation = new WatchlistGroupStock();
        relation.setGroup(group);
        relation.setStock(stock);
        watchlistGroupStockRepository.save(relation);
    }

    @Transactional
    public void removeStockFromGroup(Long groupId, Long stockId) {
        getGroupOrThrow(groupId);
        watchlistGroupStockRepository.deleteByGroupIdAndStockId(groupId, stockId);
    }

    @Transactional
    public void unwatchStock(Long stockId) {
        watchlistGroupStockRepository.deleteByStockId(stockId);
    }

    @Transactional
    public List<Long> getWatchedStockIds() {
        return watchlistGroupStockRepository.findDistinctStockIds();
    }

    private WatchlistGroup ensureDefaultGroup() {
        return watchlistGroupRepository.findByIsDefaultTrue().orElseGet(() -> {
            WatchlistGroup defaultGroup = new WatchlistGroup();
            defaultGroup.setName(DEFAULT_GROUP_NAME);
            defaultGroup.setDefault(true);
            defaultGroup.setSortOrder(0);
            return watchlistGroupRepository.save(defaultGroup);
        });
    }

    private WatchlistGroup getGroupOrThrow(Long groupId) {
        return watchlistGroupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("Watchlist group not found, id=" + groupId));
    }

    private Stock getStockOrThrow(Long stockId) {
        return stockRepository.findById(stockId)
                .orElseThrow(() -> new NoSuchElementException("Stock not found, id=" + stockId));
    }

    private String normalizeGroupName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Group name cannot be blank");
        }

        return name.trim();
    }
}
