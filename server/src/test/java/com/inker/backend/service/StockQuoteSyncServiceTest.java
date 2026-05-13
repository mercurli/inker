package com.inker.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.dto.QuoteSyncProgressDto;
import com.inker.backend.dto.QuoteSyncResultDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
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
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockQuoteSyncServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private LatestTradeDateService latestTradeDateService;

    private StockQuoteSyncService service;

    @BeforeEach
    void setUp() {
        service = new StockQuoteSyncService(stockRepository, latestTradeDateService, restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "tushareApiUrl", "http://api.tushare.pro");
        ReflectionTestUtils.setField(service, "tushareToken", "test-token");
    }

    @Test
    void syncDailyQuotesFromTushare_shouldUpdateMatchedStocksFromTushareDaily() {
        Stock shStock = stock("600000", 8.50, 0.10);
        Stock szStock = stock("000001", 10.10, -0.20);
        when(stockRepository.findAll()).thenReturn(List.of(shStock, szStock));
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockTushareDailyResponse(dailyResponse("""
                ["600000.SH","20260430",9.12,1.23],
                ["000001.SZ","20260430",11.34,-0.56]
                """), tradeCalResponse("""
                ["20260430"],
                ["20260429"],
                ["20260428"],
                ["20260427"],
                ["20260424"],
                ["20260423"]
                """), baselineResponse("""
                ["600000.SH","20260423",8.00],
                ["000001.SZ","20260423",12.00]
                """));
        mockTongHuaShunResponses(
                tongHuaShunResponse("1000000.00", "200000000.00", "3.45", "3000000000.00", "2500000000.00", "18.90"),
                tongHuaShunResponse("2000000.00", "300000000.00", "4.56", "4000000000.00", "3500000000.00", "19.80")
        );

        QuoteSyncResultDto result = service.syncDailyQuotesFromTushare();

        assertEquals("tushare", result.getSource());
        assertEquals(2, result.getFetched());
        assertEquals(2, result.getMatched());
        assertEquals(2, result.getUpdated());
        assertEquals(0, result.getSkippedMissing());
        assertEquals(9.12, shStock.getLatestPrice());
        assertEquals(1.23, shStock.getChangePercent());
        assertEquals(14.0, shStock.getFiveDayChangePercent(), 0.000001);
        assertEquals(1000000.00, shStock.getVolume());
        assertEquals(200000000.00, shStock.getAmount());
        assertEquals(3.45, shStock.getTurnoverRate());
        assertEquals(3000000000.00, shStock.getTotalMarketValue());
        assertEquals(2500000000.00, shStock.getCirculatingMarketValue());
        assertEquals(18.90, shStock.getDynamicPeRatio());
        assertEquals(11.34, szStock.getLatestPrice());
        assertEquals(-0.56, szStock.getChangePercent());
        assertEquals(-5.5, szStock.getFiveDayChangePercent(), 0.000001);
        verify(stockRepository).saveAll(any());
        verify(stockRepository).flush();

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(3)).exchange(any(URI.class), eq(HttpMethod.POST), requestCaptor.capture(), eq(String.class));
        Map<?, ?> payload = (Map<?, ?>) requestCaptor.getAllValues().get(0).getBody();
        assertEquals("daily", payload.get("api_name"));
        assertEquals(Map.of("trade_date", "20260430"), payload.get("params"));
        Map<?, ?> tradeCalPayload = (Map<?, ?>) requestCaptor.getAllValues().get(1).getBody();
        assertEquals("trade_cal", tradeCalPayload.get("api_name"));
        Map<?, ?> baselinePayload = (Map<?, ?>) requestCaptor.getAllValues().get(2).getBody();
        assertEquals("daily", baselinePayload.get("api_name"));
        assertEquals(Map.of("trade_date", "20260423"), baselinePayload.get("params"));
        verify(restTemplate, times(2)).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void syncDailyQuotesWithProgress_shouldEmitKeyStages() {
        Stock shStock = stock("600000", 8.50, 0.10);
        when(stockRepository.findAll()).thenReturn(List.of(shStock));
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockTushareDailyResponse(dailyResponse("""
                ["600000.SH","20260430",9.12,1.23]
                """), tradeCalResponse("""
                ["20260430"],
                ["20260429"],
                ["20260428"],
                ["20260427"],
                ["20260424"],
                ["20260423"]
                """), baselineResponse("""
                ["600000.SH","20260423",8.00]
                """));
        mockTongHuaShunResponses(tongHuaShunResponse("1000000.00", "200000000.00", "3.45", "3000000000.00", "2500000000.00", "18.90"));
        List<QuoteSyncProgressDto> progressEvents = new ArrayList<>();

        QuoteSyncResultDto result = service.syncDailyQuotesWithProgress(progressEvents::add);

        assertEquals("tushare", result.getSource());
        assertEquals(List.of("starting", "starting", "resolving_trade_date", "resolving_trade_date", "fetching_quotes", "fetching_quotes",
                        "fetching_history", "fetching_history",
                        "matching", "matching", "matching", "saving", "saving", "completed"),
                progressEvents.stream().map(QuoteSyncProgressDto::getStage).toList());
        QuoteSyncProgressDto completed = progressEvents.get(progressEvents.size() - 1);
        assertEquals(100, completed.getPercent());
        assertEquals("20260430", completed.getTradeDate());
        assertEquals(1, completed.getFetched());
        assertEquals(1, completed.getMatched());
        assertEquals(1, completed.getUpdated());
        assertEquals(result, completed.getResult());
    }

    @Test
    void syncDailyQuotesFromTushare_shouldCountQuotesMissingLocalStock() {
        Stock shStock = stock("600000", null, null);
        when(stockRepository.findAll()).thenReturn(List.of(shStock));
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockTushareDailyResponse(dailyResponse("""
                ["600000.SH","20260430",9.12,1.23],
                ["000001.SZ","20260430",11.34,-0.56]
                """), tradeCalResponse("""
                ["20260430"],
                ["20260429"],
                ["20260428"],
                ["20260427"],
                ["20260424"],
                ["20260423"]
                """), baselineResponse("""
                ["600000.SH","20260423",8.00],
                ["000001.SZ","20260423",12.00]
                """));
        mockTongHuaShunResponses(
                tongHuaShunResponse("1000000.00", "200000000.00", "3.45", "3000000000.00", "2500000000.00", "18.90"),
                tongHuaShunResponse("2000000.00", "300000000.00", "4.56", "4000000000.00", "3500000000.00", "19.80")
        );

        QuoteSyncResultDto result = service.syncDailyQuotesFromTushare();

        assertEquals(2, result.getFetched());
        assertEquals(1, result.getMatched());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getSkippedMissing());
    }

    @Test
    void syncDailyQuotesFromTushare_shouldNotUpdateWhenDailyItemsAreEmpty() {
        when(stockRepository.findAll()).thenReturn(List.of(stock("600000", null, null)));
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockTushareDailyResponse("""
                {"code":0,"msg":null,"data":{"fields":["ts_code","trade_date","close","pct_chg"],"items":[]}}
                """);

        QuoteSyncResultDto result = service.syncDailyQuotesFromTushare();

        assertEquals(0, result.getFetched());
        assertEquals(0, result.getMatched());
        assertEquals(0, result.getUpdated());
        assertEquals(0, result.getSkippedMissing());
        verify(stockRepository, never()).saveAll(any());
        verify(restTemplate, never()).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void syncDailyQuotesFromTushare_shouldThrowWhenTushareReturnsBusinessError() {
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockTushareDailyResponse("""
                {"code":2002,"msg":"没有权限","data":{"fields":[],"items":[]}}
                """);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.syncDailyQuotesFromTushare());

        assertEquals("Tushare daily API returns error code=2002, msg=没有权限", exception.getMessage());
    }

    @Test
    void syncDailyQuotesFromTushare_shouldThrowWhenTushareReturnsEmptyBody() {
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockTushareDailyResponse("");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.syncDailyQuotesFromTushare());

        assertEquals("Tushare daily API returns empty body", exception.getMessage());
    }

    @Test
    void syncDailyQuotesFromTushare_shouldThrowWhenTokenMissing() {
        ReflectionTestUtils.setField(service, "tushareToken", " ");
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.syncDailyQuotesFromTushare());

        assertEquals("Tushare token is not configured", exception.getMessage());
        verify(restTemplate, never()).exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void parseTongHuaShunQuote_shouldMapRealheadFields() {
        StockQuoteSyncService.TongHuaShunQuote quote = service.parseTongHuaShunQuote(tongHuaShunResponse(
                "49997722.00",
                "1323804010.00",
                "11.341",
                "11515211000.000",
                "11515211000.000",
                "44.194"
        ));

        assertEquals(49997722.00, quote.volume());
        assertEquals(1323804010.00, quote.amount());
        assertEquals(11.341, quote.turnoverRate());
        assertEquals(11515211000.000, quote.totalMarketValue());
        assertEquals(11515211000.000, quote.circulatingMarketValue());
        assertEquals(44.194, quote.dynamicPeRatio());
    }

    @Test
    void syncDailyQuotesFromTushare_shouldKeepOldTongHuaShunFieldsWhenSingleRequestFails() {
        Stock shStock = stock("600000", 8.50, 0.10);
        shStock.setTotalMarketValue(123D);
        when(stockRepository.findAll()).thenReturn(List.of(shStock));
        when(latestTradeDateService.refreshLatestAshareTradeDate()).thenReturn("20260430");
        mockTushareDailyResponse(dailyResponse("""
                ["600000.SH","20260430",9.12,1.23]
                """), tradeCalResponse("""
                ["20260430"],
                ["20260429"],
                ["20260428"],
                ["20260427"],
                ["20260424"],
                ["20260423"]
                """), baselineResponse("""
                ["600000.SH","20260423",8.00]
                """));
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.RestClientException("boom"));

        QuoteSyncResultDto result = service.syncDailyQuotesFromTushare();

        assertEquals(1, result.getUpdated());
        assertEquals(9.12, shStock.getLatestPrice());
        assertEquals(123D, shStock.getTotalMarketValue());
    }

    private void mockTushareDailyResponse(String response, String... additionalResponses) {
        @SuppressWarnings("unchecked")
        ResponseEntity<String>[] entities = new ResponseEntity[additionalResponses.length + 1];
        entities[0] = ResponseEntity.ok(response);
        for (int i = 0; i < additionalResponses.length; i++) {
            entities[i + 1] = ResponseEntity.ok(additionalResponses[i]);
        }
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(entities[0], java.util.Arrays.copyOfRange(entities, 1, entities.length));
    }

    private void mockTongHuaShunResponses(String... responses) {
        @SuppressWarnings("unchecked")
        ResponseEntity<String>[] entities = new ResponseEntity[responses.length];
        for (int i = 0; i < responses.length; i++) {
            entities[i] = ResponseEntity.ok(responses[i]);
        }
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(entities[0], java.util.Arrays.copyOfRange(entities, 1, entities.length));
    }

    private String dailyResponse(String items) {
        return """
                {"code":0,"msg":null,"data":{"fields":["ts_code","trade_date","close","pct_chg"],"items":[
                %s
                ]}}
                """.formatted(items);
    }

    private String tradeCalResponse(String items) {
        return """
                {"code":0,"msg":null,"data":{"fields":["cal_date"],"items":[
                %s
                ]}}
                """.formatted(items);
    }

    private String baselineResponse(String items) {
        return """
                {"code":0,"msg":null,"data":{"fields":["ts_code","trade_date","close"],"items":[
                %s
                ]}}
                """.formatted(items);
    }

    private String tongHuaShunResponse(String volume,
                                       String amount,
                                       String turnoverRate,
                                       String totalMarketValue,
                                       String circulatingMarketValue,
                                       String dynamicPeRatio) {
        return """
                quotebridge_v2_realhead_sh_603985_last({"items":{
                "13":"%s",
                "19":"%s",
                "1968584":"%s",
                "3475914":"%s",
                "3541450":"%s",
                "2942":"%s"
                }})
                """.formatted(volume, amount, turnoverRate, totalMarketValue, circulatingMarketValue, dynamicPeRatio);
    }

    private Stock stock(String code, Double latestPrice, Double changePercent) {
        Stock stock = new Stock();
        stock.setCode(code);
        stock.setName(code);
        stock.setExchangeCode(code.startsWith("6") ? "SSE" : "SZSE");
        stock.setMarket(code.startsWith("6") ? "SH" : "SZ");
        stock.setLatestPrice(latestPrice);
        stock.setChangePercent(changePercent);
        stock.setSt(false);
        return stock;
    }
}
