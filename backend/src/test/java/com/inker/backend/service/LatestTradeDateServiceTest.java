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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
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

    private LatestTradeDateService service;

    @BeforeEach
    void setUp() {
        service = new LatestTradeDateService(marketSyncStateRepository, restTemplate, new ObjectMapper());
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
    void refreshLatestAshareTradeDate_shouldUseCachedDateWhenEastMoneyFails() {
        MarketSyncState cached = new MarketSyncState();
        cached.setStateKey(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY);
        cached.setStateValue("20260429");
        cached.setSource(LatestTradeDateService.EASTMONEY_SOURCE);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network down"));
        when(marketSyncStateRepository.findById(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY))
                .thenReturn(Optional.of(cached));

        String tradeDate = service.refreshLatestAshareTradeDate();

        assertEquals("20260429", tradeDate);
    }

    @Test
    void refreshLatestAshareTradeDate_shouldThrowWhenEastMoneyFailsAndNoCacheExists() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("network down"));
        when(marketSyncStateRepository.findById(LatestTradeDateService.A_SHARE_LATEST_TRADE_DATE_KEY))
                .thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.refreshLatestAshareTradeDate());

        assertEquals("Failed to resolve latest A-share trade date from EastMoney and no cached value is available",
                exception.getMessage());
    }
}
