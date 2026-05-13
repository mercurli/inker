package com.inker.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.dto.StockCleanupResultDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockConceptRepository;
import com.inker.backend.repository.StockDailyKLineRepository;
import com.inker.backend.repository.StockRepository;
import com.inker.backend.repository.WatchlistGroupStockRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class StockCleanupService {

    private static final int SAMPLE_LIMIT = 20;
    private static final String USER_AGENT_VALUE = "Inker/1.0 (+https://github.com/inker)";

    private final StockRepository stockRepository;
    private final WatchlistGroupStockRepository watchlistGroupStockRepository;
    private final StockDailyKLineRepository stockDailyKLineRepository;
    private final StockConceptRepository stockConceptRepository;
    private final LatestTradeDateService latestTradeDateService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tushare.api-url:http://api.tushare.pro}")
    private String tushareApiUrl;

    @Value("${tushare.token:}")
    private String tushareToken;

    @Autowired
    public StockCleanupService(StockRepository stockRepository,
                               WatchlistGroupStockRepository watchlistGroupStockRepository,
                               StockDailyKLineRepository stockDailyKLineRepository,
                               StockConceptRepository stockConceptRepository,
                               LatestTradeDateService latestTradeDateService) {
        this(stockRepository, watchlistGroupStockRepository, stockDailyKLineRepository, stockConceptRepository,
                latestTradeDateService, createRestTemplate(), new ObjectMapper());
    }

    StockCleanupService(StockRepository stockRepository,
                        WatchlistGroupStockRepository watchlistGroupStockRepository,
                        StockDailyKLineRepository stockDailyKLineRepository,
                        StockConceptRepository stockConceptRepository,
                        LatestTradeDateService latestTradeDateService,
                        RestTemplate restTemplate,
                        ObjectMapper objectMapper) {
        this.stockRepository = stockRepository;
        this.watchlistGroupStockRepository = watchlistGroupStockRepository;
        this.stockDailyKLineRepository = stockDailyKLineRepository;
        this.stockConceptRepository = stockConceptRepository;
        this.latestTradeDateService = latestTradeDateService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StockCleanupResultDto cleanupHistoricalStocks() {
        String tradeDate = latestTradeDateService.refreshLatestAshareTradeDate();
        Set<String> activeQuoteCodes = fetchActiveQuoteCodes(tradeDate);
        List<Stock> allStocks = stockRepository.findAll();

        List<Stock> stocksToDelete = new ArrayList<>();
        Map<String, Integer> reasonCounts = new LinkedHashMap<>();
        List<StockCleanupResultDto.StockCleanupSampleDto> samples = new ArrayList<>();

        for (Stock stock : allStocks) {
            Optional<String> reason = resolveDeleteReason(stock, activeQuoteCodes);
            if (reason.isEmpty()) {
                continue;
            }

            stocksToDelete.add(stock);
            reasonCounts.merge(reason.get(), 1, Integer::sum);
            if (samples.size() < SAMPLE_LIMIT) {
                samples.add(StockCleanupResultDto.StockCleanupSampleDto.builder()
                        .code(stock.getCode())
                        .name(stock.getName())
                        .reason(reason.get())
                        .build());
            }
        }

        List<Long> stockIds = stocksToDelete.stream()
                .map(Stock::getId)
                .toList();
        long removedWatchlistRelations = 0;
        long removedDailyKLines = 0;
        if (!stockIds.isEmpty()) {
            removedWatchlistRelations = watchlistGroupStockRepository.deleteByStockIdIn(stockIds);
            removedDailyKLines = stockDailyKLineRepository.deleteByStockIdIn(stockIds);
            stockConceptRepository.deleteByStockIdIn(stockIds);
            stockRepository.deleteAllByIdInBatch(stockIds);
            stockRepository.flush();
        }

        return StockCleanupResultDto.builder()
                .scanned(allStocks.size())
                .deleted(stocksToDelete.size())
                .removedWatchlistRelations(removedWatchlistRelations)
                .removedDailyKLines(removedDailyKLines)
                .reasonCounts(reasonCounts)
                .samples(samples)
                .build();
    }

    Optional<String> resolveDeleteReason(Stock stock, Set<String> activeQuoteCodes) {
        String name = stock.getName() == null ? "" : stock.getName().trim();
        String upperName = name.toUpperCase();
        if (name.contains("退")) {
            return Optional.of("delisted_name");
        }
        if (upperName.startsWith("PT")) {
            return Optional.of("pt_name");
        }
        if (isHistoricalSName(name)) {
            return Optional.of("historical_s_name");
        }
        if (!activeQuoteCodes.contains(stock.getCode())
                && stock.getLatestPrice() == null
                && stock.getChangePercent() == null
                && stock.getUpdatedAt() == null) {
            return Optional.of("stale_without_quote");
        }
        return Optional.empty();
    }

    private boolean isHistoricalSName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("S*ST") || upper.startsWith("S ST") || upper.startsWith("SST")) {
            return true;
        }
        if (!upper.startsWith("S") || upper.startsWith("ST")) {
            return false;
        }
        return trimmed.length() > 1 && Character.UnicodeScript.of(trimmed.charAt(1)) == Character.UnicodeScript.HAN;
    }

    private Set<String> fetchActiveQuoteCodes(String tradeDate) {
        JsonNode data = callTushareDaily(tradeDate);
        Map<String, Integer> fieldIndexMap = buildFieldIndexMap(data.path("fields"));
        Integer codeIndex = fieldIndexMap.get("ts_code");
        if (codeIndex == null) {
            throw new IllegalStateException("Tushare daily API response missing ts_code field");
        }

        Set<String> codes = new LinkedHashSet<>();
        JsonNode itemsNode = data.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode item : itemsNode) {
                if (item.isArray() && codeIndex < item.size()) {
                    String normalized = normalizeTsCode(item.get(codeIndex).asText(null));
                    if (normalized != null) {
                        codes.add(normalized);
                    }
                }
            }
        }
        return codes;
    }

    private JsonNode callTushareDaily(String tradeDate) {
        ensureTokenConfigured();

        URI uri = UriComponentsBuilder.fromHttpUrl(tushareApiUrl).build(true).toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, USER_AGENT_VALUE);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_name", "daily");
        payload.put("token", tushareToken);
        payload.put("params", Map.of("trade_date", tradeDate));
        payload.put("fields", "ts_code,trade_date");

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Tushare daily API returns empty body");
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("msg").asText("unknown");
                throw new IllegalStateException("Tushare daily API returns error code=" + code + ", msg=" + msg);
            }
            JsonNode data = root.path("data");
            if (!data.path("fields").isArray() || !data.path("items").isArray()) {
                throw new IllegalStateException("Tushare daily API response missing fields/items array");
            }
            return data;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse Tushare daily response", exception);
        }
    }

    private void ensureTokenConfigured() {
        if (tushareToken == null || tushareToken.isBlank()) {
            throw new IllegalStateException("Tushare token is not configured");
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

    private String normalizeTsCode(String tsCode) {
        if (tsCode == null || tsCode.isBlank()) {
            return null;
        }
        String normalized = tsCode.trim();
        int dotIndex = normalized.indexOf('.');
        if (dotIndex > 0) {
            normalized = normalized.substring(0, dotIndex);
        }
        return normalized.isBlank() ? null : normalized;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}
