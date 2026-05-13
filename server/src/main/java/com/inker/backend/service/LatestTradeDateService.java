package com.inker.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.entity.MarketSyncState;
import com.inker.backend.repository.MarketSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LatestTradeDateService {

    static final String A_SHARE_LATEST_TRADE_DATE_KEY = "A_SHARE_LATEST_TRADE_DATE";
    static final String EASTMONEY_SOURCE = "eastmoney_index_kline";
    static final String TUSHARE_SOURCE = "tushare_trade_cal";

    private static final Logger log = LoggerFactory.getLogger(LatestTradeDateService.class);
    private static final String EASTMONEY_KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    private static final String USER_AGENT_VALUE = "Inker/1.0 (+https://github.com/inker)";
    private static final DateTimeFormatter EASTMONEY_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TUSHARE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MarketSyncStateRepository marketSyncStateRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tushare.api-url:http://api.tushare.pro}")
    private String tushareApiUrl;

    @Value("${tushare.token:}")
    private String tushareToken;

    @Autowired
    public LatestTradeDateService(MarketSyncStateRepository marketSyncStateRepository) {
        this(marketSyncStateRepository, createRestTemplate(), new ObjectMapper());
    }

    LatestTradeDateService(MarketSyncStateRepository marketSyncStateRepository,
                           RestTemplate restTemplate,
                           ObjectMapper objectMapper) {
        this.marketSyncStateRepository = marketSyncStateRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String refreshLatestAshareTradeDate() {
        Optional<ResolvedTradeDate> onlineTradeDate = resolveFromOnlineSources();
        if (onlineTradeDate.isPresent()) {
            ResolvedTradeDate resolved = onlineTradeDate.get();
            saveLatestTradeDate(resolved.tradeDate(), resolved.source());
            return resolved.tradeDate();
        }

        return resolveCachedTradeDate()
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to resolve latest A-share trade date from online sources and no cached value is available"));
    }

    private Optional<ResolvedTradeDate> resolveFromOnlineSources() {
        try {
            String tradeDate = fetchLatestTradeDateFromEastMoney();
            return Optional.of(new ResolvedTradeDate(tradeDate, EASTMONEY_SOURCE));
        } catch (Exception exception) {
            log.warn("Failed to fetch latest A-share trade date from EastMoney. Trying Tushare trade calendar.", exception);
        }

        try {
            String tradeDate = fetchLatestOpenTradeDateFromTushare();
            return Optional.of(new ResolvedTradeDate(tradeDate, TUSHARE_SOURCE));
        } catch (Exception exception) {
            log.warn("Failed to fetch latest A-share trade date from Tushare. Trying cached value.", exception);
            return Optional.empty();
        }
    }

    private Optional<String> resolveCachedTradeDate() {
        return marketSyncStateRepository.findById(A_SHARE_LATEST_TRADE_DATE_KEY)
                .map(MarketSyncState::getStateValue)
                .filter(this::isValidTushareDate);
    }

    String fetchLatestTradeDateFromEastMoney() {
        URI uri = UriComponentsBuilder.fromHttpUrl(EASTMONEY_KLINE_URL)
                .queryParam("secid", "1.000001")
                .queryParam("klt", "101")
                .queryParam("fqt", "1")
                .queryParam("end", "20500101")
                .queryParam("lmt", "1")
                .queryParam("fields1", "f1,f2,f3,f4,f5,f6")
                .queryParam("fields2", "f51")
                .build(true)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT_VALUE);

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("EastMoney latest trade date API returns empty body");
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode klinesNode = root.path("data").path("klines");
            if (!klinesNode.isArray() || klinesNode.isEmpty()) {
                throw new IllegalStateException("EastMoney latest trade date response missing kline data");
            }

            String kline = klinesNode.get(0).asText(null);
            if (kline == null || kline.isBlank()) {
                throw new IllegalStateException("EastMoney latest trade date response contains empty kline");
            }

            String datePart = kline.split(",", 2)[0];
            return LocalDate.parse(datePart, EASTMONEY_DATE_FORMATTER).format(TUSHARE_DATE_FORMATTER);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse EastMoney latest trade date response", exception);
        }
    }

    String fetchLatestOpenTradeDateFromTushare() {
        ensureTushareTokenConfigured();

        LocalDate today = LocalDate.now();
        String startDate = today.minusDays(14).format(TUSHARE_DATE_FORMATTER);
        String endDate = today.format(TUSHARE_DATE_FORMATTER);
        JsonNode data = callTushare(
                "trade_cal",
                Map.of("exchange", "SSE", "start_date", startDate, "end_date", endDate, "is_open", "1"),
                "cal_date"
        );
        JsonNode itemsNode = data.path("items");
        Map<String, Integer> fieldIndexMap = buildFieldIndexMap(data.path("fields"));
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            throw new IllegalStateException("Tushare trade_cal API returned empty open trade date data");
        }

        String latestTradeDate = null;
        for (JsonNode item : itemsNode) {
            String calDate = readText(item, fieldIndexMap, "cal_date");
            if (isValidTushareDate(calDate) && (latestTradeDate == null || calDate.compareTo(latestTradeDate) > 0)) {
                latestTradeDate = calDate;
            }
        }
        if (latestTradeDate == null) {
            throw new IllegalStateException("Tushare trade_cal API response contains no valid open trade date");
        }
        return latestTradeDate;
    }

    private JsonNode callTushare(String apiName, Map<String, Object> params, String fields) {
        URI uri = UriComponentsBuilder.fromHttpUrl(tushareApiUrl).build(true).toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT_VALUE);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_name", apiName);
        payload.put("token", tushareToken);
        payload.put("params", params);
        payload.put("fields", fields);

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Tushare " + apiName + " API returns empty body");
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("unknown");
                throw new IllegalStateException("Tushare " + apiName + " API returns error code=" + code + ", msg=" + msg);
            }

            JsonNode data = root.path("data");
            if (!data.path("fields").isArray() || !data.path("items").isArray()) {
                throw new IllegalStateException("Tushare " + apiName + " API response missing fields/items array");
            }
            return data;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse Tushare " + apiName + " response", exception);
        }
    }

    private Map<String, Integer> buildFieldIndexMap(JsonNode fieldsNode) {
        Map<String, Integer> fieldIndexMap = new HashMap<>();
        if (!fieldsNode.isArray()) {
            return fieldIndexMap;
        }
        for (int i = 0; i < fieldsNode.size(); i++) {
            fieldIndexMap.put(fieldsNode.get(i).asText(), i);
        }
        return fieldIndexMap;
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

    private void ensureTushareTokenConfigured() {
        if (tushareToken == null || tushareToken.isBlank()) {
            throw new IllegalStateException("Tushare token is not configured");
        }
    }

    private void saveLatestTradeDate(String tradeDate, String source) {
        MarketSyncState state = marketSyncStateRepository.findById(A_SHARE_LATEST_TRADE_DATE_KEY)
                .orElseGet(MarketSyncState::new);
        state.setStateKey(A_SHARE_LATEST_TRADE_DATE_KEY);
        state.setStateValue(tradeDate);
        state.setSource(source);
        marketSyncStateRepository.save(state);
    }

    private boolean isValidTushareDate(String value) {
        return value != null && value.matches("\\d{8}");
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    private record ResolvedTradeDate(String tradeDate, String source) {
    }
}
