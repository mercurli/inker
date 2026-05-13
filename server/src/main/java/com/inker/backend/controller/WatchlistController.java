package com.inker.backend.controller;

import com.inker.backend.dto.CreateWatchlistGroupRequest;
import com.inker.backend.dto.ReorderWatchlistGroupsRequest;
import com.inker.backend.dto.ReorderWatchlistGroupStocksRequest;
import com.inker.backend.dto.UpdateWatchlistGroupRequest;
import com.inker.backend.dto.WatchlistGroupDto;
import com.inker.backend.dto.WatchlistStockDto;
import com.inker.backend.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping("/groups")
    public List<WatchlistGroupDto> getGroups() {
        return watchlistService.getGroups();
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public WatchlistGroupDto createGroup(@Valid @RequestBody CreateWatchlistGroupRequest request) {
        return watchlistService.createGroup(request.getName());
    }

    @PatchMapping("/groups/{groupId}")
    public WatchlistGroupDto updateGroup(@PathVariable Long groupId,
                                         @Valid @RequestBody UpdateWatchlistGroupRequest request) {
        return watchlistService.updateGroup(groupId, request.getName(), request.getSortOrder());
    }

    @PutMapping("/groups/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderGroups(@Valid @RequestBody ReorderWatchlistGroupsRequest request) {
        watchlistService.reorderGroups(request.getGroupIds());
    }

    @DeleteMapping("/groups/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long groupId) {
        watchlistService.deleteGroup(groupId);
    }

    @GetMapping("/groups/{groupId}/stocks")
    public List<WatchlistStockDto> getGroupStocks(@PathVariable Long groupId) {
        return watchlistService.getGroupStocks(groupId);
    }

    @PutMapping("/groups/{groupId}/stocks/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderGroupStocks(@PathVariable Long groupId,
                                   @Valid @RequestBody ReorderWatchlistGroupStocksRequest request) {
        watchlistService.reorderGroupStocks(groupId, request.getStockIds());
    }

    @PostMapping("/stocks/{stockId}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ensureStockInDefaultGroup(@PathVariable Long stockId) {
        watchlistService.ensureStockInDefaultGroup(stockId);
    }

    @PostMapping("/groups/{groupId}/stocks/{stockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ensureStockInGroup(@PathVariable Long groupId,
                                   @PathVariable Long stockId) {
        watchlistService.ensureStockInGroup(groupId, stockId);
    }

    @DeleteMapping("/groups/{groupId}/stocks/{stockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStockFromGroup(@PathVariable Long groupId,
                                     @PathVariable Long stockId) {
        watchlistService.removeStockFromGroup(groupId, stockId);
    }

    @DeleteMapping("/stocks/{stockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unwatchStock(@PathVariable Long stockId) {
        watchlistService.unwatchStock(stockId);
    }

    @GetMapping("/stocks/ids")
    public List<Long> getWatchedStockIds() {
        return watchlistService.getWatchedStockIds();
    }

    @GetMapping("/stocks/{stockId}/groups/ids")
    public List<Long> getStockGroupIds(@PathVariable Long stockId) {
        return watchlistService.getStockGroupIds(stockId);
    }
}
