package com.inker.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.entity.MarketSyncState;
import com.inker.backend.repository.MarketSyncStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LatestTradeDateServiceTest {

    @Mock
    private MarketSyncStateRepository marketSyncStateRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TradingCalendarService tradingCalendarService;

    private LatestTradeDateService service;

    @BeforeEach
    void setUp() {
        service = new LatestTradeDateService(marketSyncStateRepository, tradingCalendarService, restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "tushareApiUrl", "http://api.tushare.pro");
        ReflectionTestUtils.setField(service, "tushareToken", "test-token");
    }

    @Test
    void refreshLatestAshareTradeDate_shouldPreferManualTradingCalendarAndSaveTushareDate() {
        when(tradingCalendarService.resolveLatestOpenDate()).thenReturn(Optional.of(LocalDate.parse("2026-05-06")));
        when(marketSyncStateRepository.findById(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY))
                .thenReturn(Optional.empty());

        String tradeDate = service.refreshLatestAshareTradeDate();

        assertEquals("20260506", tradeDate);
        ArgumentCaptor<MarketSyncState> stateCaptor = ArgumentCaptor.forClass(MarketSyncState.class);
        verify(marketSyncStateRepository).save(stateCaptor.capture());
        MarketSyncState saved = stateCaptor.getValue();
        assertEquals("20260506", saved.getStateValue());
        assertEquals(LatestTradeDateService.MANUAL_TRADING_CALENDAR_SOURCE, saved.getSource());
    }

    @Test
    void refreshLatestAshareTradeDate_shouldFetchFromEastMoneyAndSaveTushareDate() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"rc":0,"data":{"klines":["2026-04-30,3180.00,3190.00"]}}
                        """));
        when(marketSyncStateRepository.findById(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY))
                .thenReturn(Optional.empty());

        String tradeDate = service.refreshLatestAshareTradeDate();

        assertEquals("20260430", tradeDate);
        ArgumentCaptor<MarketSyncState> stateCaptor = ArgumentCaptor.forClass(MarketSyncState.class);
        verify(marketSyncStateRepository).save(stateCaptor.capture());
        MarketSyncState saved = stateCaptor.getValue();
        assertEquals(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY, saved.getStateKey());
        assertEquals("20260430", saved.getStateValue());
        assertEquals(LatestTradeDateService.EASTMONEY_SOURCE, saved.getSource());
    }

    @Test
    void refreshLatestAshareTradeDate_shouldFetchFromTushareDailyWhenEastMoneyFails() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network down"));
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"code":0,"msg":null,"data":{"fields":["trade_date"],"items":[
                        ["20260428"],
                        ["20260429"],
                        ["20260430"]
                        ]}}
                        """));
        when(marketSyncStateRepository.findById(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY))
                .thenReturn(Optional.empty());

        String tradeDate = service.refreshLatestAshareTradeDate();

        assertEquals("20260430", tradeDate);
        ArgumentCaptor<MarketSyncState> stateCaptor = ArgumentCaptor.forClass(MarketSyncState.class);
        verify(marketSyncStateRepository).save(stateCaptor.capture());
        assertEquals(LatestTradeDateService.TUSHARE_SOURCE, stateCaptor.getValue().getSource());
    }

    @Test
    void refreshLatestAshareTradeDate_shouldUseCachedDateWhenOnlineSourcesFail() {
        MarketSyncState cached = new MarketSyncState();
        cached.setStateKey(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY);
        cached.setStateValue("20260429");
        cached.setSource(LatestTradeDateService.EASTMONEY_SOURCE);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network down"));
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("tushare down"));
        when(marketSyncStateRepository.findById(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY))
                .thenReturn(Optional.of(cached));

        String tradeDate = service.refreshLatestAshareTradeDate();

        assertEquals("20260429", tradeDate);
    }

    @Test
    void refreshLatestAshareTradeDate_shouldThrowWhenOnlineSourcesFailAndNoCacheExists() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network down"));
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("tushare down"));
        when(marketSyncStateRepository.findById(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.refreshLatestAshareTradeDate());

        assertEquals("Failed to resolve latest A-share trade date from online sources and no cached value is available",
                exception.getMessage());
    }
}
