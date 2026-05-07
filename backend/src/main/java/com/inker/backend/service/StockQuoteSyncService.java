package com.inker.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.dto.QuoteSyncProgressDto;
import com.inker.backend.dto.QuoteSyncResultDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class StockQuoteSyncService {

    private static final Logger log = LoggerFactory.getLogger(StockQuoteSyncService.class);
    private static final int BATCH_SIZE = 500;
    private static final String USER_AGENT_VALUE = "Inker/1.0 (+https://github.com/inker)";
    private static final String TONG_HUA_SHUN_REALHEAD_URL_TEMPLATE = "https://d.10jqka.com.cn/v2/realhead/%s_%s/last.js";

    private final StockRepository stockRepository;
    private final LatestTradeDateService latestTradeDateService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${tushare.api-url:http://api.tushare.pro}")
    private String tushareApiUrl;

    @Value("${tushare.token:}")
    private String tushareToken;

    @Autowired
    public StockQuoteSyncService(StockRepository stockRepository,
                                 LatestTradeDateService latestTradeDateService) {
        this(stockRepository, latestTradeDateService, createRestTemplate(), new ObjectMapper());
    }

    StockQuoteSyncService(StockRepository stockRepository,
                          LatestTradeDateService latestTradeDateService,
                          RestTemplate restTemplate,
                          ObjectMapper objectMapper) {
        this.stockRepository = stockRepository;
        this.latestTradeDateService = latestTradeDateService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuoteSyncResultDto syncDailyQuotesFromAkshare() {
        return syncDailyQuotes(ignored -> {
        });
    }

    @Transactional
    public QuoteSyncResultDto syncDailyQuotesWithProgress(Consumer<QuoteSyncProgressDto> progressConsumer) {
        return syncDailyQuotes(progressConsumer);
    }

    private QuoteSyncResultDto syncDailyQuotes(Consumer<QuoteSyncProgressDto> progressConsumer) {
        ProgressState progress = new ProgressState(progressConsumer);
        progress.emit("starting", 0, "准备同步 A 股行情");
        progress.emit("starting", 5, "初始化 Tushare 行情同步");

        progress.emit("resolving_trade_date", 10, "正在获取最近交易日");
        String tradeDate = latestTradeDateService.refreshLatestAshareTradeDate();
        progress.tradeDate = tradeDate;
        progress.emit("resolving_trade_date", 20, "已确认最近交易日 " + tradeDate);

        progress.emit("fetching_quotes", 25, "正在获取 Tushare 日行情");
        List<TushareQuote> quotes = fetchQuotesFromTushare(tradeDate);
        progress.fetched = quotes.size();
        progress.emit("fetching_quotes", 40, "已获取 " + quotes.size() + " 条行情");

        progress.emit("matching", 45, "正在加载本地股票");
        Map<String, Stock> stockByCode = new HashMap<>();
        for (Stock stock : stockRepository.findAll()) {
            stockByCode.put(stock.getCode(), stock);
        }

        progress.emit("matching", 55, "正在匹配本地股票");
        List<Stock> toSave = new ArrayList<>();

        for (TushareQuote quote : quotes) {
            if (quote.code() != null && !quote.code().isBlank()) {
                Stock stock = stockByCode.get(quote.code());
                if (stock == null) {
                    progress.skippedMissing++;
                    continue;
                }

                progress.matched++;
                boolean changed = false;
                if (!sameDouble(stock.getLatestPrice(), quote.latestPrice())) {
                    stock.setLatestPrice(quote.latestPrice());
                    changed = true;
                }
                if (!sameDouble(stock.getChangePercent(), quote.changePercent())) {
                    stock.setChangePercent(quote.changePercent());
                    changed = true;
                }
                changed = updateTongHuaShunFields(stock, quote.tongHuaShunQuote()) || changed;

                if (changed) {
                    progress.updated++;
                    toSave.add(stock);
                }
            }
        }
        progress.emit("matching", 65, "匹配完成，待更新 " + toSave.size() + " 条");

        int totalBatches = toSave.isEmpty() ? 0 : (int) Math.ceil((double) toSave.size() / BATCH_SIZE);
        for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, toSave.size());
            int batchNumber = (i / BATCH_SIZE) + 1;
            int percent = 70 + (int) Math.floor((batchNumber - 1) * 25D / totalBatches);
            progress.emit("saving", percent, "正在保存第 " + batchNumber + "/" + totalBatches + " 批");
            stockRepository.saveAll(toSave.subList(i, end));
            stockRepository.flush();
            int savedPercent = 70 + (int) Math.floor(batchNumber * 25D / totalBatches);
            progress.emit("saving", Math.min(savedPercent, 95), "已保存第 " + batchNumber + "/" + totalBatches + " 批");
        }
        if (toSave.isEmpty()) {
            progress.emit("saving", 95, "没有需要更新的行情");
        }

        log.info("Synced daily quotes from Tushare. tradeDate={}, fetched={}, matched={}, updated={}, skippedMissing={}",
                tradeDate, quotes.size(), progress.matched, progress.updated, progress.skippedMissing);

        QuoteSyncResultDto result = QuoteSyncResultDto.builder()
                .source("tushare")
                .fetched(quotes.size())
                .matched(progress.matched)
                .updated(progress.updated)
                .skippedMissing(progress.skippedMissing)
                .build();
        progress.emitCompleted(result);
        return result;
    }

    private List<TushareQuote> fetchQuotesFromTushare(String tradeDate) {
        JsonNode data = callTushare("daily", Map.of("trade_date", tradeDate), "ts_code,trade_date,close,pct_chg");
        Map<String, Integer> fieldIndexMap = buildFieldIndexMap(data.path("fields"));
        JsonNode itemsNode = data.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("Tushare daily API returned empty quote data. tradeDate={}", tradeDate);
            return List.of();
        }

        List<TushareQuote> quotes = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            if (!item.isArray()) {
                continue;
            }
            String tsCode = readText(item, fieldIndexMap, "ts_code");
            String code = normalizeTsCode(readText(item, fieldIndexMap, "ts_code"));
            if (code == null) {
                continue;
            }
            quotes.add(new TushareQuote(
                    code,
                    readDouble(item, fieldIndexMap, "close"),
                    readDouble(item, fieldIndexMap, "pct_chg"),
                    fetchTongHuaShunQuote(code, normalizeTongHuaShunMarket(tsCode))
            ));
        }
        return quotes;
    }

    private TongHuaShunQuote fetchTongHuaShunQuote(String code, String market) {
        if (code == null || code.isBlank() || market == null || market.isBlank()) {
            return TongHuaShunQuote.empty();
        }

        URI uri = URI.create(TONG_HUA_SHUN_REALHEAD_URL_TEMPLATE.formatted(market, code));
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.set(HttpHeaders.REFERER, "https://stockpage.10jqka.com.cn/" + code + "/");

        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return parseTongHuaShunQuote(response.getBody());
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to fetch TongHuaShun quote. code={}, market={}, message={}", code, market, exception.getMessage());
            return TongHuaShunQuote.empty();
        }
    }

    TongHuaShunQuote parseTongHuaShunQuote(String body) {
        if (body == null || body.isBlank()) {
            return TongHuaShunQuote.empty();
        }

        String json = extractJsonpPayload(body);
        try {
            JsonNode items = objectMapper.readTree(json).path("items");
            return new TongHuaShunQuote(
                    readDouble(items, "13"),
                    readDouble(items, "19"),
                    readDouble(items, "1968584"),
                    readDouble(items, "3475914"),
                    readDouble(items, "3541450"),
                    readDouble(items, "2942")
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse TongHuaShun quote response", exception);
        }
    }

    private String extractJsonpPayload(String body) {
        int start = body.indexOf('(');
        int end = body.lastIndexOf(')');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("TongHuaShun quote response is not JSONP");
        }
        return body.substring(start + 1, end);
    }

    private boolean updateTongHuaShunFields(Stock stock, TongHuaShunQuote quote) {
        boolean changed = false;
        changed = updateIfPresent(stock.getVolume(), quote.volume(), stock::setVolume) || changed;
        changed = updateIfPresent(stock.getAmount(), quote.amount(), stock::setAmount) || changed;
        changed = updateIfPresent(stock.getTurnoverRate(), quote.turnoverRate(), stock::setTurnoverRate) || changed;
        changed = updateIfPresent(stock.getTotalMarketValue(), quote.totalMarketValue(), stock::setTotalMarketValue) || changed;
        changed = updateIfPresent(stock.getCirculatingMarketValue(), quote.circulatingMarketValue(), stock::setCirculatingMarketValue) || changed;
        changed = updateIfPresent(stock.getDynamicPeRatio(), quote.dynamicPeRatio(), stock::setDynamicPeRatio) || changed;
        return changed;
    }

    private boolean updateIfPresent(Double currentValue, Double nextValue, Consumer<Double> setter) {
        if (nextValue == null || sameDouble(currentValue, nextValue)) {
            return false;
        }
        setter.accept(nextValue);
        return true;
    }

    private JsonNode callTushare(String apiName, Map<String, Object> params, String fields) {
        ensureTokenConfigured();

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

    private String normalizeTongHuaShunMarket(String tsCode) {
        if (tsCode == null || tsCode.isBlank()) {
            return null;
        }
        String normalized = tsCode.trim().toUpperCase();
        if (normalized.endsWith(".SH")) {
            return "sh";
        }
        if (normalized.endsWith(".SZ")) {
            return "sz";
        }
        return null;
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
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double readDouble(JsonNode objectNode, String fieldName) {
        JsonNode value = objectNode.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        if (text == null || text.isBlank() || "-".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean sameDouble(Double left, Double right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Double.compare(left, right) == 0;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    private record TushareQuote(
            String code,
            Double latestPrice,
            Double changePercent,
            TongHuaShunQuote tongHuaShunQuote
    ) {
    }

    record TongHuaShunQuote(
            Double volume,
            Double amount,
            Double turnoverRate,
            Double totalMarketValue,
            Double circulatingMarketValue,
            Double dynamicPeRatio
    ) {
        static TongHuaShunQuote empty() {
            return new TongHuaShunQuote(null, null, null, null, null, null);
        }
    }

    private static class ProgressState {
        private final Consumer<QuoteSyncProgressDto> consumer;
        private String tradeDate;
        private int fetched;
        private int matched;
        private int updated;
        private int skippedMissing;

        private ProgressState(Consumer<QuoteSyncProgressDto> consumer) {
            this.consumer = consumer == null ? ignored -> {
            } : consumer;
        }

        private void emit(String stage, int percent, String message) {
            consumer.accept(QuoteSyncProgressDto.builder()
                    .stage(stage)
                    .percent(percent)
                    .message(message)
                    .tradeDate(tradeDate)
                    .fetched(fetched)
                    .matched(matched)
                    .updated(updated)
                    .skippedMissing(skippedMissing)
                    .build());
        }

        private void emitCompleted(QuoteSyncResultDto result) {
            consumer.accept(QuoteSyncProgressDto.builder()
                    .stage("completed")
                    .percent(100)
                    .message("行情同步完成")
                    .tradeDate(tradeDate)
                    .fetched(fetched)
                    .matched(matched)
                    .updated(updated)
                    .skippedMissing(skippedMissing)
                    .result(result)
                    .build());
        }
    }
}
