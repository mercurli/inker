package com.inker.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.dto.StockCleanupResultDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockConceptRepository;
import com.inker.backend.repository.StockDailyKLineRepository;
import com.inker.backend.repository.StockRepository;
import com.inker.backend.repository.WatchlistGroupStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockCleanupServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private WatchlistGroupStockRepository watchlistGroupStockRepository;

    @Mock
    private StockDailyKLineRepository stockDailyKLineRepository;

    @Mock
    private StockConceptRepository stockConceptRepository;

    @Mock
    private LatestTradeDateService latestTradeDateService;

    @Mock
    private RestTemplate restTemplate;

    private StockCleanupService service;

    @BeforeEach
    void setUp() {
        service = new StockCleanupService(stockRepository, watchlistGroupStockRepository, stockDailyKLineRepository,
                stockConceptRepository, latestTradeDateService, restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "tushareApiUrl", "http://api.tushare.pro");
        ReflectionTestUtils.setField(service, "tushareToken", "test-token");
    }

    @Test
    void resolveDeleteReason_shouldRecognizeHistoricalNamePatterns() {
        Set<String> activeCodes = Set.of("000001");

        assertEquals(Optional.of("delisted_name"), service.resolveDeleteReason(stock(1L, "000018", "神城A退"), activeCodes));
        assertEquals(Optional.of("pt_name"), service.resolveDeleteReason(stock(2L, "000003", "PT金田A"), activeCodes));
        assertEquals(Optional.of("historical_s_name"), service.resolveDeleteReason(stock(3L, "000583", "S*ST托普"), activeCodes));
        assertEquals(Optional.of("historical_s_name"), service.resolveDeleteReason(stock(4L, "000549", "S湘火炬"), activeCodes));
        assertEquals(Optional.empty(), service.resolveDeleteReason(stock(5L, "000001", "平安S银行"), activeCodes));
    }

    @Test
    void resolveDeleteReason_shouldOnlyDeleteStaleCodeWhenItHasNeverUpdatedQuotes() {
        Stock stale = stock(1L, "600001", "邯郸钢铁");
        Stock quoted = stock(2L, "600187", "国中水务");
        quoted.setLatestPrice(1.84);
        quoted.setChangePercent(0.0);
        quoted.setUpdatedAt(LocalDateTime.parse("2026-05-01T23:03:39"));

        assertEquals(Optional.of("stale_without_quote"), service.resolveDeleteReason(stale, Set.of("600000")));
        assertEquals(Optional.empty(), service.resolveDeleteReason(quoted, Set.of("600000")));
        assertEquals(Optional.empty(), service.resolveDeleteReason(stale, Set.of("600001")));
    }

    @Test
    void cleanupHistoricalStocks_shouldDeleteRelationsBeforeStocksAndReturnCounts() {
        Stock delisted = stock(1L, "000018", "神城A退");
        Stock stale = stock(2L, "600001", "邯郸钢铁");
        Stock active = stock(3L, "600000", "浦发银行");
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockDailyResponse("""
                ["600000.SH","20260430"]
                """);
        when(stockRepository.findAll()).thenReturn(List.of(delisted, stale, active));
        when(watchlistGroupStockRepository.deleteByStockIdIn(List.of(1L, 2L))).thenReturn(2L);
        when(stockDailyKLineRepository.deleteByStockIdIn(List.of(1L, 2L))).thenReturn(4L);
        when(stockConceptRepository.deleteByStockIdIn(List.of(1L, 2L))).thenReturn(3);

        StockCleanupResultDto result = service.cleanupHistoricalStocks();

        assertEquals(3, result.getScanned());
        assertEquals(2, result.getDeleted());
        assertEquals(2, result.getRemovedWatchlistRelations());
        assertEquals(4, result.getRemovedDailyKLines());
        assertEquals(Map.of("delisted_name", 1, "stale_without_quote", 1), result.getReasonCounts());
        assertEquals(2, result.getSamples().size());

        InOrder inOrder = inOrder(watchlistGroupStockRepository, stockDailyKLineRepository, stockConceptRepository, stockRepository);
        inOrder.verify(watchlistGroupStockRepository).deleteByStockIdIn(List.of(1L, 2L));
        inOrder.verify(stockDailyKLineRepository).deleteByStockIdIn(List.of(1L, 2L));
        inOrder.verify(stockConceptRepository).deleteByStockIdIn(List.of(1L, 2L));
        inOrder.verify(stockRepository).deleteAllByIdInBatch(List.of(1L, 2L));
        inOrder.verify(stockRepository).flush();
    }

    @Test
    void cleanupHistoricalStocks_shouldNotDeleteWhenTushareTokenMissing() {
        ReflectionTestUtils.setField(service, "tushareToken", " ");
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.cleanupHistoricalStocks());

        assertEquals("Tushare token is not configured", exception.getMessage());
        verify(stockRepository, never()).findAll();
        verify(watchlistGroupStockRepository, never()).deleteByStockIdIn(any());
    }

    @Test
    void cleanupHistoricalStocks_shouldNotDeleteWhenTushareReturnsError() {
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"code":2002,"msg":"没有权限","data":{"fields":[],"items":[]}}
                        """));

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.cleanupHistoricalStocks());

        assertEquals("Tushare daily API returns error code=2002, msg=没有权限", exception.getMessage());
        verify(stockRepository, never()).findAll();
        verify(watchlistGroupStockRepository, never()).deleteByStockIdIn(any());
    }

    private void mockDailyResponse(String items) {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"code":0,"msg":null,"data":{"fields":["ts_code","trade_date"],"items":[
                        %s
                        ]}}
                        """.formatted(items)));
    }

    private Stock stock(Long id, String code, String name) {
        Stock stock = new Stock();
        stock.setId(id);
        stock.setCode(code);
        stock.setName(name);
        stock.setExchangeCode(code.startsWith("6") ? "SSE" : "SZSE");
        stock.setMarket(code.startsWith("6") ? "SH" : "SZ");
        stock.setSt(false);
        return stock;
    }
}
