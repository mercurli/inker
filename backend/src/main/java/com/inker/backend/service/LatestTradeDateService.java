package com.inker.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.entity.MarketSyncState;
import com.inker.backend.repository.MarketSyncStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.List;

@Service
public class LatestTradeDateService {

    static final String A_SHARE_LATEST_TRADE_DATE_KEY = "A_SHARE_LATEST_TRADE_DATE";
    static final String EASTMONEY_SOURCE = "eastmoney_index_kline";

    private static final Logger log = LoggerFactory.getLogger(LatestTradeDateService.class);
    private static final String EASTMONEY_KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";
    private static final String USER_AGENT_VALUE = "Inker/1.0 (+https://github.com/inker)";
    private static final DateTimeFormatter EASTMONEY_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TUSHARE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MarketSyncStateRepository marketSyncStateRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
        try {
            String tradeDate = fetchLatestTradeDateFromEastMoney();
            saveLatestTradeDate(tradeDate);
            return tradeDate;
        } catch (Exception exception) {
            log.warn("Failed to fetch latest A-share trade date from EastMoney. Trying cached value.", exception);
            return marketSyncStateRepository.findById(A_SHARE_LATEST_TRADE_DATE_KEY)
                    .map(MarketSyncState::getStateValue)
                    .filter(this::isValidTushareDate)
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to resolve latest A-share trade date from EastMoney and no cached value is available",
                            exception));
        }
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

    private void saveLatestTradeDate(String tradeDate) {
        MarketSyncState state = marketSyncStateRepository.findById(A_SHARE_LATEST_TRADE_DATE_KEY)
                .orElseGet(MarketSyncState::new);
        state.setStateKey(A_SHARE_LATEST_TRADE_DATE_KEY);
        state.setStateValue(tradeDate);
        state.setSource(EASTMONEY_SOURCE);
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
}
