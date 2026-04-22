package com.inker.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.dto.StockDailyKLineDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.entity.StockDailyKLine;
import com.inker.backend.repository.StockDailyKLineRepository;
import com.inker.backend.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class StockMarketDataService {

    private static final Logger log = LoggerFactory.getLogger(StockMarketDataService.class);
    private static final DateTimeFormatter TUSHARE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter OUTPUT_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int DEFAULT_LIMIT = 60;
    private static final int MAX_LIMIT = 500;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long[] RETRY_BACKOFF_MS = {300L, 800L};
    private static final String USER_AGENT_VALUE = "Inker/1.0 (+https://github.com/inker)";

    private final StockRepository stockRepository;
    private final StockDailyKLineRepository stockDailyKLineRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tushare.api-url:http://api.tushare.pro}")
    private String tushareApiUrl;

    @Value("${tushare.token:}")
    private String tushareToken;

    public StockMarketDataService(StockRepository stockRepository,
                                  StockDailyKLineRepository stockDailyKLineRepository) {
        this.stockRepository = stockRepository;
        this.stockDailyKLineRepository = stockDailyKLineRepository;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public StockDailyKLineDto getDailyKLine(Long stockId, Integer limit) {
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new NoSuchElementException("Stock not found, id=" + stockId));
        int normalizedLimit = normalizeLimit(limit);

        try {
            syncDailyKLineFromTushare(stock, normalizedLimit);
        } catch (Exception exception) {
            long existingCount = stockDailyKLineRepository.countByStockId(stockId);
            if (existingCount > 0) {
                log.warn(
                        "Tushare sync failed, fallback to stored daily K line data. stockId={}, code={}, limit={}, storedCount={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                        stockId,
                        stock.getCode(),
                        normalizedLimit,
                        existingCount,
                        exception.getClass().getName(),
                        rootCauseClassName(exception),
                        rootCauseMessage(exception),
                        exception
                );
            } else {
                throw new IllegalStateException("Failed to fetch daily K line for stock id=" + stockId, exception);
            }
        }

        return buildResponseFromDatabase(stock, normalizedLimit);
    }

    private void syncDailyKLineFromTushare(Stock stock, int normalizedLimit) {
        String tsCode = buildTsCode(stock);
        String startDate = LocalDate.now().minusDays(Math.max(120L, normalizedLimit * 4L))
                .format(TUSHARE_DATE_FORMATTER);
        String endDate = LocalDate.now().format(TUSHARE_DATE_FORMATTER);

        List<TushareDailyBar> bars = fetchDailyKLineWithRetry(stock, tsCode, startDate, endDate);
        if (bars.isEmpty()) {
            log.warn("Tushare returned empty daily K line data. stockId={}, code={}, tsCode={}, startDate={}, endDate={}",
                    stock.getId(), stock.getCode(), tsCode, startDate, endDate);
            return;
        }

        List<LocalDate> tradeDates = bars.stream().map(TushareDailyBar::tradeDate).toList();
        Map<LocalDate, StockDailyKLine> existingByDate = new HashMap<>();
        for (StockDailyKLine existing : stockDailyKLineRepository.findByStockIdAndTradeDateIn(stock.getId(), tradeDates)) {
            existingByDate.put(existing.getTradeDate(), existing);
        }

        List<StockDailyKLine> toSave = new ArrayList<>();
        for (TushareDailyBar bar : bars) {
            StockDailyKLine entity = existingByDate.getOrDefault(bar.tradeDate(), new StockDailyKLine());
            entity.setStock(stock);
            entity.setTradeDate(bar.tradeDate());
            entity.setOpenPrice(bar.openPrice());
            entity.setClosePrice(bar.closePrice());
            entity.setHighPrice(bar.highPrice());
            entity.setLowPrice(bar.lowPrice());
            entity.setVolume(bar.volume());
            entity.setAmount(bar.amount());
            entity.setPreClosePrice(bar.preClosePrice());
            entity.setChangeAmount(bar.changeAmount());
            entity.setChangePercent(bar.changePercent());
            toSave.add(entity);
        }

        stockDailyKLineRepository.saveAll(toSave);
        log.info("Synced daily K line from Tushare. stockId={}, code={}, tsCode={}, fetched={}, upserted={}",
                stock.getId(), stock.getCode(), tsCode, bars.size(), toSave.size());
    }

    private List<TushareDailyBar> fetchDailyKLineWithRetry(Stock stock, String tsCode, String startDate, String endDate) {
        RestClientException lastException = null;
        RuntimeException lastBusinessException = null;

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            long startNanos = System.nanoTime();
            try {
                List<TushareDailyBar> bars = fetchDailyKLine(stock, tsCode, startDate, endDate);
                long elapsedMs = elapsedMs(startNanos);
                log.info(
                        "Tushare daily K line request succeeded. attempt={}/{}, elapsedMs={}, stockId={}, code={}, tsCode={}, startDate={}, endDate={}, count={}",
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        elapsedMs,
                        stock.getId(),
                        stock.getCode(),
                        tsCode,
                        startDate,
                        endDate,
                        bars.size()
                );
                return bars;
            } catch (RestClientException exception) {
                lastException = exception;
                long elapsedMs = elapsedMs(startNanos);
                log.warn(
                        "Tushare daily K line request failed by network. attempt={}/{}, elapsedMs={}, stockId={}, code={}, tsCode={}, startDate={}, endDate={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        elapsedMs,
                        stock.getId(),
                        stock.getCode(),
                        tsCode,
                        startDate,
                        endDate,
                        exception.getClass().getName(),
                        rootCauseClassName(exception),
                        rootCauseMessage(exception),
                        exception
                );
            } catch (RuntimeException exception) {
                lastBusinessException = exception;
                long elapsedMs = elapsedMs(startNanos);
                log.warn(
                        "Tushare daily K line request failed by business response. attempt={}/{}, elapsedMs={}, stockId={}, code={}, tsCode={}, startDate={}, endDate={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        elapsedMs,
                        stock.getId(),
                        stock.getCode(),
                        tsCode,
                        startDate,
                        endDate,
                        exception.getClass().getName(),
                        rootCauseClassName(exception),
                        rootCauseMessage(exception),
                        exception
                );
            }

            if (attempt < MAX_RETRY_ATTEMPTS) {
                sleepBeforeRetry(attempt);
            }
        }

        if (lastException != null) {
            throw new IllegalStateException("Failed to call Tushare daily API after retries", lastException);
        }
        throw new IllegalStateException("Failed to call Tushare daily API after retries", lastBusinessException);
    }

    private List<TushareDailyBar> fetchDailyKLine(Stock stock, String tsCode, String startDate, String endDate) {
        ensureTokenConfigured();

        URI uri = UriComponentsBuilder.fromHttpUrl(tushareApiUrl).build(true).toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT_VALUE);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", tsCode);
        params.put("start_date", startDate);
        params.put("end_date", endDate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_name", "daily");
        payload.put("token", tushareToken);
        payload.put("params", params);
        payload.put("fields", "ts_code,trade_date,open,high,low,close,pre_close,change,pct_chg,vol,amount");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, request, String.class);

        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Tushare daily API returns empty body. stockId=" + stock.getId());
        }

        return parseTushareDailyResponse(body, stock, tsCode);
    }

    private List<TushareDailyBar> parseTushareDailyResponse(String body, Stock stock, String expectedTsCode) {
        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("unknown");
                throw new IllegalStateException("Tushare daily API returns error code=" + code + ", msg=" + msg);
            }

            JsonNode data = root.path("data");
            JsonNode fieldsNode = data.path("fields");
            JsonNode itemsNode = data.path("items");
            if (!fieldsNode.isArray() || !itemsNode.isArray()) {
                throw new IllegalStateException("Tushare daily API response missing fields/items array");
            }

            Map<String, Integer> fieldIndexMap = new HashMap<>();
            for (int i = 0; i < fieldsNode.size(); i++) {
                fieldIndexMap.put(fieldsNode.get(i).asText(), i);
            }

            List<TushareDailyBar> bars = new ArrayList<>();
            for (JsonNode item : itemsNode) {
                if (!item.isArray()) {
                    continue;
                }

                String tsCode = readText(item, fieldIndexMap, "ts_code");
                if (tsCode == null || !expectedTsCode.equalsIgnoreCase(tsCode.trim())) {
                    continue;
                }

                LocalDate tradeDate = parseTradeDate(readText(item, fieldIndexMap, "trade_date"));
                if (tradeDate == null) {
                    continue;
                }

                bars.add(new TushareDailyBar(
                        tradeDate,
                        readDouble(item, fieldIndexMap, "open"),
                        readDouble(item, fieldIndexMap, "close"),
                        readDouble(item, fieldIndexMap, "high"),
                        readDouble(item, fieldIndexMap, "low"),
                        readDouble(item, fieldIndexMap, "vol"),
                        readDouble(item, fieldIndexMap, "amount"),
                        readDouble(item, fieldIndexMap, "pre_close"),
                        readDouble(item, fieldIndexMap, "change"),
                        readDouble(item, fieldIndexMap, "pct_chg")
                ));
            }
            return bars;
        } catch (Exception exception) {
            log.error(
                    "Failed to parse Tushare daily response. stockId={}, code={}, tsCode={}, response={}, exceptionType={}, rootCauseType={}, rootCauseMessage={}",
                    stock.getId(),
                    stock.getCode(),
                    expectedTsCode,
                    truncate(body),
                    exception.getClass().getName(),
                    rootCauseClassName(exception),
                    rootCauseMessage(exception),
                    exception
            );
            throw new IllegalStateException("Failed to parse Tushare daily response for stock id=" + stock.getId(), exception);
        }
    }

    private StockDailyKLineDto buildResponseFromDatabase(Stock stock, int normalizedLimit) {
        List<StockDailyKLine> descRecords = stockDailyKLineRepository.findByStockIdOrderByTradeDateDesc(
                stock.getId(),
                PageRequest.of(0, normalizedLimit)
        );

        List<StockDailyKLineDto.CandleDto> candles = new ArrayList<>();
        for (int i = descRecords.size() - 1; i >= 0; i--) {
            StockDailyKLine item = descRecords.get(i);
            candles.add(StockDailyKLineDto.CandleDto.builder()
                    .tradeDate(item.getTradeDate().format(OUTPUT_DATE_FORMATTER))
                    .openPrice(valueOrZero(item.getOpenPrice()))
                    .closePrice(valueOrZero(item.getClosePrice()))
                    .highPrice(valueOrZero(item.getHighPrice()))
                    .lowPrice(valueOrZero(item.getLowPrice()))
                    .volume(valueOrZero(item.getVolume()))
                    .amount(valueOrZero(item.getAmount()))
                    .changePercent(valueOrZero(item.getChangePercent()))
                    .build());
        }

        return StockDailyKLineDto.builder()
                .symbol(stock.getCode())
                .name(stock.getName())
                .candles(candles)
                .build();
    }

    private void ensureTokenConfigured() {
        if (tushareToken == null || tushareToken.isBlank()) {
            throw new IllegalStateException("Tushare token is not configured");
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(10, Math.min(limit, MAX_LIMIT));
    }

    private String buildTsCode(Stock stock) {
        if ("SSE".equalsIgnoreCase(stock.getExchangeCode())) {
            return stock.getCode() + ".SH";
        }
        if ("SZSE".equalsIgnoreCase(stock.getExchangeCode())) {
            return stock.getCode() + ".SZ";
        }
        throw new IllegalArgumentException("Unsupported exchange code: " + stock.getExchangeCode());
    }

    private LocalDate parseTradeDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, TUSHARE_DATE_FORMATTER);
        } catch (DateTimeParseException exception) {
            log.warn("Invalid trade_date in Tushare response: {}", value);
            return null;
        }
    }

    private String readText(JsonNode arrayNode, Map<String, Integer> fieldIndexMap, String fieldName) {
        Integer index = fieldIndexMap.get(fieldName);
        if (index == null || index < 0 || index >= arrayNode.size()) {
            return null;
        }
        JsonNode value = arrayNode.get(index);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private Double readDouble(JsonNode arrayNode, Map<String, Integer> fieldIndexMap, String fieldName) {
        String value = readText(arrayNode, fieldIndexMap, fieldName);
        if (value == null || value.isBlank() || "-".equals(value)) {
            return 0D;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0D;
        }
    }

    private double valueOrZero(Double value) {
        return value == null ? 0D : value;
    }

    private String truncate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= 1000 ? body : body.substring(0, 1000) + "...(truncated)";
    }

    private String rootCauseClassName(Throwable throwable) {
        Throwable root = getRootCause(throwable);
        return root == null ? null : root.getClass().getName();
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable root = getRootCause(throwable);
        return root == null ? null : root.getMessage();
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private void sleepBeforeRetry(int attempt) {
        int index = Math.min(Math.max(attempt - 1, 0), RETRY_BACKOFF_MS.length - 1);
        long sleepMs = RETRY_BACKOFF_MS[index];
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Tushare daily request", interruptedException);
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private record TushareDailyBar(
            LocalDate tradeDate,
            Double openPrice,
            Double closePrice,
            Double highPrice,
            Double lowPrice,
            Double volume,
            Double amount,
            Double preClosePrice,
            Double changeAmount,
            Double changePercent
    ) {
    }
}
