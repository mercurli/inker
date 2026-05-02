package com.inker.backend.controller;

import com.inker.backend.dto.QuoteSyncProgressDto;
import com.inker.backend.dto.QuoteSyncResultDto;
import com.inker.backend.dto.StockCleanupResultDto;
import com.inker.backend.service.StockImportService;
import com.inker.backend.service.StockMarketDataService;
import com.inker.backend.service.StockQuoteSyncService;
import com.inker.backend.service.StockQueryService;
import com.inker.backend.service.StockCleanupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
class StockControllerSseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockQueryService stockQueryService;

    @MockBean
    private StockImportService stockImportService;

    @MockBean
    private StockMarketDataService stockMarketDataService;

    @MockBean
    private StockQuoteSyncService stockQuoteSyncService;

    @MockBean
    private StockCleanupService stockCleanupService;

    @Test
    void streamQuoteSync_shouldReturnEventStreamWithCompletedEvent() throws Exception {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<QuoteSyncProgressDto> progressConsumer = invocation.getArgument(0, Consumer.class);
            QuoteSyncResultDto result = QuoteSyncResultDto.builder()
                    .source("tushare")
                    .fetched(2)
                    .matched(2)
                    .updated(1)
                    .skippedMissing(0)
                    .build();
            progressConsumer.accept(QuoteSyncProgressDto.builder()
                    .stage("resolving_trade_date")
                    .percent(20)
                    .message("已确认最近交易日 20260430")
                    .tradeDate("20260430")
                    .build());
            progressConsumer.accept(QuoteSyncProgressDto.builder()
                    .stage("completed")
                    .percent(100)
                    .message("行情同步完成")
                    .tradeDate("20260430")
                    .fetched(2)
                    .matched(2)
                    .updated(1)
                    .result(result)
                    .build());
            return result;
        }).when(stockQuoteSyncService).syncDailyQuotesWithProgress(ArgumentMatchers.<Consumer<QuoteSyncProgressDto>>any());

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/stocks/quotes/sync/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:resolving_trade_date")))
                .andExpect(content().string(containsString("event:completed")))
                .andExpect(content().string(containsString("\"source\":\"tushare\"")));
    }

    @Test
    void streamQuoteSync_shouldReturnFailedEventWhenServiceThrows() throws Exception {
        doThrow(new IllegalStateException("Tushare token is not configured"))
                .when(stockQuoteSyncService)
                .syncDailyQuotesWithProgress(any());

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/stocks/quotes/sync/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:failed")))
                .andExpect(content().string(containsString("Tushare token is not configured")));
    }

    @Test
    void cleanupHistoricalStocks_shouldReturnCleanupResult() throws Exception {
        org.mockito.Mockito.when(stockCleanupService.cleanupHistoricalStocks())
                .thenReturn(StockCleanupResultDto.builder()
                        .scanned(3)
                        .deleted(2)
                        .removedWatchlistRelations(1)
                        .removedDailyKLines(4)
                        .reasonCounts(java.util.Map.of("delisted_name", 1, "stale_without_quote", 1))
                        .samples(java.util.List.of())
                        .build());

        mockMvc.perform(post("/api/v1/stocks/cleanup/historical")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"scanned\":3")))
                .andExpect(content().string(containsString("\"deleted\":2")))
                .andExpect(content().string(containsString("\"removedWatchlistRelations\":1")))
                .andExpect(content().string(containsString("\"removedDailyKLines\":4")));
    }
}
