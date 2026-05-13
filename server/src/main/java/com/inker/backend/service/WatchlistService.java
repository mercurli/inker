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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

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
        Map<Long, Double> averageChangePercents = new HashMap<>();
        Map<Long, Map<String, Long>> industryCounts = new HashMap<>();
        Map<Long, Map<String, Long>> primaryConceptCounts = new HashMap<>();

        groups.forEach(group -> counts.put(group.getId(), watchlistGroupStockRepository.countByGroupId(group.getId())));
        watchlistGroupStockRepository.averageChangePercentByGroup()
                .forEach(item -> averageChangePercents.put(item.getGroupId(), item.getAverageChangePercent()));
        watchlistGroupStockRepository.countIndustriesByGroup()
                .forEach(item -> industryCounts
                        .computeIfAbsent(item.getGroupId(), ignored -> new LinkedHashMap<>())
                        .put(item.getIndustry(), item.getStockCount()));
        watchlistGroupStockRepository.countPrimaryConceptsByGroup()
                .forEach(item -> primaryConceptCounts
                        .computeIfAbsent(item.getGroupId(), ignored -> new LinkedHashMap<>())
                        .put(item.getPrimaryConcept(), item.getStockCount()));

        return groups.stream()
                .map(group -> WatchlistGroupDto.fromEntity(
                        group,
                        counts.getOrDefault(group.getId(), 0L),
                        averageChangePercents.get(group.getId()),
                        industryCounts.getOrDefault(group.getId(), Map.of()),
                        primaryConceptCounts.getOrDefault(group.getId(), Map.of())
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

        return watchlistGroupStockRepository.findAllByGroupIdOrderBySortOrderAscAddedAtDescIdDesc(groupId)
                .stream()
                .map(WatchlistStockDto::fromEntity)
                .toList();
    }

    @Transactional
    public void reorderGroupStocks(Long groupId, List<Long> stockIds) {
        getGroupOrThrow(groupId);

        if (stockIds == null) {
            throw new IllegalArgumentException("Stock ids cannot be null");
        }

        List<WatchlistGroupStock> relations = watchlistGroupStockRepository.findAllByGroupIdOrderBySortOrderAscAddedAtDescIdDesc(groupId);
        Set<Long> currentStockIds = new HashSet<>();
        Map<Long, WatchlistGroupStock> relationByStockId = new HashMap<>();

        for (WatchlistGroupStock relation : relations) {
            Long stockId = relation.getStock().getId();
            currentStockIds.add(stockId);
            relationByStockId.put(stockId, relation);
        }

        Set<Long> requestedStockIds = new HashSet<>(stockIds);

        if (requestedStockIds.size() != stockIds.size() || !requestedStockIds.equals(currentStockIds)) {
            throw new IllegalArgumentException("Stock ids must match the current group exactly");
        }

        for (int index = 0; index < stockIds.size(); index += 1) {
            relationByStockId.get(stockIds.get(index)).setSortOrder(index);
        }

        watchlistGroupStockRepository.saveAllAndFlush(relations);
    }

    @Transactional
    public void reorderGroups(List<Long> groupIds) {
        ensureDefaultGroup();

        if (groupIds == null) {
            throw new IllegalArgumentException("Group ids cannot be null");
        }

        List<WatchlistGroup> groups = watchlistGroupRepository.findAllByOrderBySortOrderAscIdAsc();
        List<WatchlistGroup> reorderableGroups = groups.stream()
                .filter(group -> !group.isDefault())
                .toList();
        Set<Long> currentGroupIds = new HashSet<>();
        Map<Long, WatchlistGroup> groupById = new HashMap<>();

        for (WatchlistGroup group : reorderableGroups) {
            currentGroupIds.add(group.getId());
            groupById.put(group.getId(), group);
        }

        Set<Long> requestedGroupIds = new HashSet<>(groupIds);

        if (requestedGroupIds.size() != groupIds.size() || !requestedGroupIds.equals(currentGroupIds)) {
            throw new IllegalArgumentException("Group ids must match the reorderable groups exactly");
        }

        groups.stream()
                .filter(WatchlistGroup::isDefault)
                .forEach(group -> group.setSortOrder(0));

        for (int index = 0; index < groupIds.size(); index += 1) {
            groupById.get(groupIds.get(index)).setSortOrder(index + 1);
        }

        watchlistGroupRepository.saveAllAndFlush(groups);
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

        List<WatchlistGroupStock> existingRelations = watchlistGroupStockRepository.findAllByGroupIdOrderBySortOrderAscAddedAtDescIdDesc(groupId);
        existingRelations.forEach(item -> item.setSortOrder(item.getSortOrder() + 1));
        watchlistGroupStockRepository.saveAll(existingRelations);

        WatchlistGroupStock relation = new WatchlistGroupStock();
        relation.setGroup(group);
        relation.setStock(stock);
        relation.setSortOrder(0);
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

    @Transactional
    public List<Long> getStockGroupIds(Long stockId) {
        getStockOrThrow(stockId);
        return watchlistGroupStockRepository.findGroupIdsByStockId(stockId);
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
