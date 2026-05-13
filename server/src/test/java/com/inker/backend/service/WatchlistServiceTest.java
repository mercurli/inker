package com.inker.backend.service;

import com.inker.backend.dto.WatchlistGroupDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(WatchlistService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class WatchlistServiceTest {

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private StockRepository stockRepository;

    private Long stockId1;
    private Long stockId2;

    @BeforeEach
    void setUp() {
        stockRepository.deleteAll();

        Stock stock1 = new Stock();
        stock1.setCode("600000");
        stock1.setName("浦发银行");
        stock1.setExchangeCode("SSE");
        stock1.setMarket("A股");
        stock1.setIndustry("银行");
        stock1.setSt(false);

        Stock stock2 = new Stock();
        stock2.setCode("000001");
        stock2.setName("平安银行");
        stock2.setExchangeCode("SZSE");
        stock2.setMarket("A股");
        stock2.setIndustry(" ");
        stock2.setSt(false);

        stockId1 = stockRepository.save(stock1).getId();
        stockId2 = stockRepository.save(stock2).getId();
    }

    @Test
    void shouldCreateRenameAndDeleteGroup() {
        WatchlistGroupDto created = watchlistService.createGroup("价值");
        assertEquals("价值", created.getName());

        WatchlistGroupDto updated = watchlistService.updateGroup(created.getId(), "成长", 3);
        assertEquals("成长", updated.getName());
        assertEquals(3, updated.getSortOrder());

        watchlistService.deleteGroup(created.getId());
        List<WatchlistGroupDto> groups = watchlistService.getGroups();
        assertTrue(groups.stream().noneMatch(group -> group.getId().equals(created.getId())));
    }

    @Test
    void shouldSupportOneStockInMultipleGroupsIdempotently() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");

        watchlistService.ensureStockInGroup(groupA.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId1);

        assertEquals(1, watchlistService.getGroupStocks(groupA.getId()).size());
        assertEquals(1, watchlistService.getGroupStocks(groupB.getId()).size());
    }

    @Test
    void shouldReturnGroupIdsForStockInSortOrder() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");

        watchlistService.ensureStockInGroup(groupB.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId2);

        assertEquals(List.of(groupA.getId(), groupB.getId()), watchlistService.getStockGroupIds(stockId1));
    }

    @Test
    void ensureStockInDefaultGroupShouldBeIdempotent() {
        watchlistService.ensureStockInDefaultGroup(stockId1);
        watchlistService.ensureStockInDefaultGroup(stockId1);

        WatchlistGroupDto defaultGroup = watchlistService.getGroups().stream()
                .filter(WatchlistGroupDto::isDefault)
                .findFirst()
                .orElseThrow();

        assertEquals(1, watchlistService.getGroupStocks(defaultGroup.getId()).size());
    }

    @Test
    void shouldUnwatchStockFromAllGroups() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");

        watchlistService.ensureStockInGroup(groupA.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId2);

        watchlistService.unwatchStock(stockId1);

        assertEquals(0, watchlistService.getGroupStocks(groupA.getId()).size());
        assertEquals(1, watchlistService.getGroupStocks(groupB.getId()).size());
        assertEquals(stockId2, watchlistService.getGroupStocks(groupB.getId()).get(0).getId());
    }

    @Test
    void getGroupsShouldIncludeIndustryCountsPerGroup() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");
        Long stockId3 = stockRepository.save(createStock("688001", "华兴科技", "SSE", "半导体")).getId();
        Long stockId4 = stockRepository.save(createStock("300001", "深城银行", "SZSE", "银行")).getId();

        watchlistService.ensureStockInGroup(groupA.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId2);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId4);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId3);

        List<WatchlistGroupDto> groups = watchlistService.getGroups();
        Map<String, Long> groupAIndustries = groups.stream()
                .filter(group -> group.getId().equals(groupA.getId()))
                .findFirst()
                .orElseThrow()
                .getIndustryCounts();
        Map<String, Long> groupBIndustries = groups.stream()
                .filter(group -> group.getId().equals(groupB.getId()))
                .findFirst()
                .orElseThrow()
                .getIndustryCounts();

        assertEquals(2L, groupAIndustries.get("银行"));
        assertEquals(1L, groupAIndustries.get("未分类行业"));
        assertEquals(Map.of("半导体", 1L), groupBIndustries);
    }

    @Test
    void getGroupsShouldIncludePrimaryConceptCountsPerGroup() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");
        Long stockId3 = stockRepository.save(createStock("688001", "华兴科技", "SSE", "半导体", List.of("AI", "芯片"))).getId();
        Long stockId4 = stockRepository.save(createStock("300001", "深城银行", "SZSE", "银行", List.of("AI"))).getId();
        Long stockId5 = stockRepository.save(createStock("300002", "空白概念", "SZSE", "软件", List.of(" ", "算力"))).getId();
        Long stockId6 = stockRepository.save(createStock("600001", "无概念", "SSE", "银行", List.of())).getId();

        watchlistService.ensureStockInGroup(groupA.getId(), stockId3);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId4);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId5);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId6);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId3);

        List<WatchlistGroupDto> groups = watchlistService.getGroups();
        Map<String, Long> groupAPrimaryConcepts = groups.stream()
                .filter(group -> group.getId().equals(groupA.getId()))
                .findFirst()
                .orElseThrow()
                .getPrimaryConceptCounts();
        Map<String, Long> groupBPrimaryConcepts = groups.stream()
                .filter(group -> group.getId().equals(groupB.getId()))
                .findFirst()
                .orElseThrow()
                .getPrimaryConceptCounts();

        assertEquals(2L, groupAPrimaryConcepts.get("AI"));
        assertEquals(2L, groupAPrimaryConcepts.get("未分类概念"));
        assertEquals(Map.of("AI", 1L), groupBPrimaryConcepts);
    }

    @Test
    void getGroupsShouldIncludeAverageChangePercentPerGroup() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");
        Long stockId3 = stockRepository.save(createStock("688001", "华兴科技", "SSE", "半导体", List.of(), 3.0)).getId();
        Long stockId4 = stockRepository.save(createStock("300001", "深城银行", "SZSE", "银行", List.of(), -1.0)).getId();
        Long stockId5 = stockRepository.save(createStock("300002", "无行情", "SZSE", "软件", List.of(), null)).getId();

        watchlistService.ensureStockInGroup(groupA.getId(), stockId3);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId4);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId5);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId5);

        List<WatchlistGroupDto> groups = watchlistService.getGroups();
        WatchlistGroupDto resolvedGroupA = groups.stream()
                .filter(group -> group.getId().equals(groupA.getId()))
                .findFirst()
                .orElseThrow();
        WatchlistGroupDto resolvedGroupB = groups.stream()
                .filter(group -> group.getId().equals(groupB.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(1.0, resolvedGroupA.getAverageChangePercent());
        assertEquals(null, resolvedGroupB.getAverageChangePercent());
    }

    @Test
    void shouldReorderGroupsWhileKeepingDefaultGroupFirst() {
        WatchlistGroupDto defaultGroup = watchlistService.getGroups().stream()
                .filter(WatchlistGroupDto::isDefault)
                .findFirst()
                .orElseThrow();
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");
        WatchlistGroupDto groupC = watchlistService.createGroup("C");

        watchlistService.reorderGroups(List.of(groupC.getId(), groupA.getId(), groupB.getId()));

        List<WatchlistGroupDto> groups = watchlistService.getGroups();
        assertEquals(List.of(defaultGroup.getId(), groupC.getId(), groupA.getId(), groupB.getId()), groups.stream()
                .map(WatchlistGroupDto::getId)
                .toList());
        assertEquals(List.of(0, 1, 2, 3), groups.stream()
                .map(WatchlistGroupDto::getSortOrder)
                .toList());
    }

    @Test
    void reorderGroupsShouldRejectInvalidGroupIdsWithoutChangingOrder() {
        WatchlistGroupDto defaultGroup = watchlistService.getGroups().stream()
                .filter(WatchlistGroupDto::isDefault)
                .findFirst()
                .orElseThrow();
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");

        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroups(List.of(groupA.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroups(List.of(groupA.getId(), groupA.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroups(List.of(defaultGroup.getId(), groupA.getId(), groupB.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroups(List.of(groupA.getId(), 999999L)));

        assertEquals(List.of(defaultGroup.getId(), groupA.getId(), groupB.getId()), watchlistService.getGroups().stream()
                .map(WatchlistGroupDto::getId)
                .toList());
    }

    @Test
    void deleteGroupShouldCancelWatchForStocksInsideGroup() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");

        watchlistService.ensureStockInGroup(groupA.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId1);

        watchlistService.deleteGroup(groupA.getId());

        assertEquals(0, watchlistService.getGroupStocks(groupB.getId()).size());
        assertTrue(watchlistService.getWatchedStockIds().isEmpty());
    }

    @Test
    void shouldReorderStocksInsideGroup() {
        WatchlistGroupDto group = watchlistService.createGroup("A");
        Long stockId3 = stockRepository.save(createStock("688001", "华兴科技", "SSE", "半导体")).getId();

        watchlistService.ensureStockInGroup(group.getId(), stockId1);
        watchlistService.ensureStockInGroup(group.getId(), stockId2);
        watchlistService.ensureStockInGroup(group.getId(), stockId3);

        watchlistService.reorderGroupStocks(group.getId(), List.of(stockId1, stockId3, stockId2));

        assertEquals(List.of(stockId1, stockId3, stockId2), watchlistService.getGroupStocks(group.getId())
                .stream()
                .map(item -> item.getId())
                .toList());
    }

    @Test
    void newStockShouldBeInsertedAtTopOfGroupOrder() {
        WatchlistGroupDto group = watchlistService.createGroup("A");
        Long stockId3 = stockRepository.save(createStock("688001", "华兴科技", "SSE", "半导体")).getId();

        watchlistService.ensureStockInGroup(group.getId(), stockId1);
        watchlistService.ensureStockInGroup(group.getId(), stockId2);
        watchlistService.reorderGroupStocks(group.getId(), List.of(stockId1, stockId2));
        watchlistService.ensureStockInGroup(group.getId(), stockId3);

        assertEquals(List.of(stockId3, stockId1, stockId2), watchlistService.getGroupStocks(group.getId())
                .stream()
                .map(item -> item.getId())
                .toList());
    }

    @Test
    void reorderShouldRejectInvalidStockIdsWithoutChangingOrder() {
        WatchlistGroupDto groupA = watchlistService.createGroup("A");
        WatchlistGroupDto groupB = watchlistService.createGroup("B");
        Long stockId3 = stockRepository.save(createStock("688001", "华兴科技", "SSE", "半导体")).getId();

        watchlistService.ensureStockInGroup(groupA.getId(), stockId1);
        watchlistService.ensureStockInGroup(groupA.getId(), stockId2);
        watchlistService.ensureStockInGroup(groupB.getId(), stockId3);
        watchlistService.reorderGroupStocks(groupA.getId(), List.of(stockId1, stockId2));

        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroupStocks(groupA.getId(), List.of(stockId1)));
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroupStocks(groupA.getId(), List.of(stockId1, stockId1)));
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroupStocks(groupA.getId(), List.of(stockId1, stockId3)));
        assertThrows(IllegalArgumentException.class,
                () -> watchlistService.reorderGroupStocks(groupA.getId(), List.of(stockId1, 999999L)));

        assertEquals(List.of(stockId1, stockId2), watchlistService.getGroupStocks(groupA.getId())
                .stream()
                .map(item -> item.getId())
                .toList());
    }

    private Stock createStock(String code, String name, String exchangeCode, String industry) {
        return createStock(code, name, exchangeCode, industry, List.of());
    }

    private Stock createStock(String code, String name, String exchangeCode, String industry, List<String> concepts) {
        return createStock(code, name, exchangeCode, industry, concepts, null);
    }

    private Stock createStock(String code,
                              String name,
                              String exchangeCode,
                              String industry,
                              List<String> concepts,
                              Double changePercent) {
        Stock stock = new Stock();
        stock.setCode(code);
        stock.setName(name);
        stock.setExchangeCode(exchangeCode);
        stock.setMarket("A股");
        stock.setIndustry(industry);
        stock.setConcepts(concepts);
        stock.setChangePercent(changePercent);
        stock.setSt(false);
        return stock;
    }
}
