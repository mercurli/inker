package com.inker.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inker.backend.dto.QuoteSyncResultDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockQuoteSyncService {

    private static final Logger log = LoggerFactory.getLogger(StockQuoteSyncService.class);
    private static final int BATCH_SIZE = 500;

    private final StockRepository stockRepository;
    private final ObjectMapper objectMapper;

    public StockQuoteSyncService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public QuoteSyncResultDto syncDailyQuotesFromAkshare() {
        List<AkshareQuote> quotes = fetchQuotesFromAkshare();
        Map<String, AkshareQuote> quoteByCode = new HashMap<>();
        for (AkshareQuote quote : quotes) {
            if (quote.code() != null && !quote.code().isBlank()) {
                quoteByCode.put(quote.code(), quote);
            }
        }

        List<Stock> allStocks = stockRepository.findAll();
        List<Stock> toSave = new ArrayList<>();
        int matched = 0;
        int updated = 0;
        int skippedMissing = 0;

        for (Stock stock : allStocks) {
            AkshareQuote quote = quoteByCode.get(stock.getCode());
            if (quote == null) {
                skippedMissing++;
                continue;
            }

            matched++;
            boolean changed = false;
            if (!sameDouble(stock.getLatestPrice(), quote.latestPrice())) {
                stock.setLatestPrice(quote.latestPrice());
                changed = true;
            }
            if (!sameDouble(stock.getChangePercent(), quote.changePercent())) {
                stock.setChangePercent(quote.changePercent());
                changed = true;
            }

            if (changed) {
                updated++;
                toSave.add(stock);
            }
        }

        for (int i = 0; i < toSave.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, toSave.size());
            stockRepository.saveAll(toSave.subList(i, end));
            stockRepository.flush();
        }

        log.info("Synced daily quotes from AkShare. fetched={}, matched={}, updated={}, skippedMissing={}",
                quotes.size(), matched, updated, skippedMissing);

        return QuoteSyncResultDto.builder()
                .source("akshare")
                .fetched(quotes.size())
                .matched(matched)
                .updated(updated)
                .skippedMissing(skippedMissing)
                .build();
    }

    private List<AkshareQuote> fetchQuotesFromAkshare() {
        Path scriptPath = resolveAkshareScriptPath();
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("AkShare script not found: " + scriptPath);
        }

        ProcessBuilder processBuilder = new ProcessBuilder("python", scriptPath.toString());
        processBuilder.directory(scriptPath.getParent().getParent().toFile());
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                output = builder.toString();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("AkShare quote script failed. exitCode=" + exitCode + ", output=" + output);
            }

            return objectMapper.readValue(extractJsonArray(output), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch quotes from AkShare", exception);
        }
    }

    private Path resolveAkshareScriptPath() {
        return Paths.get("scripts", "akshare_sync_quotes.py").toAbsolutePath().normalize();
    }

    private String extractJsonArray(String output) {
        if (output == null) {
            throw new IllegalStateException("AkShare quote script returned null output");
        }
        String trimmed = output.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw new IllegalStateException("AkShare quote script did not return JSON array. output=" + trimmed);
        }
        return trimmed.substring(start, end + 1);
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

    private record AkshareQuote(
            String code,
            Double latestPrice,
            Double changePercent
    ) {
    }
}
