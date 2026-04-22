package com.inker.backend.service.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EastMoneyStockProvider implements StockProvider {

    private static final Logger log = LoggerFactory.getLogger(EastMoneyStockProvider.class);
    private static final String THS_CONCEPT_URL_TEMPLATE = "http://basic.10jqka.com.cn/new/%s/concept.html";
    private static final Charset THS_CHARSET = Charset.forName("GBK");
    private static final Pattern THS_CONCEPT_TAG_PATTERN_LEGACY = Pattern.compile(
            "class\\s*=\\s*[\"']gnStockList[\"'][^>]*tag\\s*=\\s*[\"'][^\"']*?-(?<name>[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern THS_CONCEPT_TAG_PATTERN_J_POP_LINK = Pattern.compile(
            "<a(?=[^>]*\\bclass\\s*=\\s*[\"'][^\"']*\\bJ_popLink\\b)"
                    + "(?=[^>]*\\btopStock\\s*=\\s*[\"'][^\"']+[\"'])"
                    + "(?=[^>]*\\btag\\s*=\\s*[\"'](?<name>[^\"']+)[\"'])[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EastMoneyStockProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<String> fetchConcepts(String code, String market) {
        if (code == null || code.isBlank()) {
            return List.of();
        }

        String url = THS_CONCEPT_URL_TEMPLATE.formatted(code.trim());
        HttpHeaders headers = buildConceptHeaders();

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                return List.of();
            }

            String html = new String(body, THS_CHARSET);
            return extractConceptsFromHtml(html);
        } catch (RestClientException e) {
            log.warn("Failed to fetch concepts for stock. code={}, market={}, message={}", code, market, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ProviderStock> fetchAllAStockCandidates() {
        List<AkshareStockCandidate> candidates = fetchCandidatesFromAkshare();
        List<ProviderStock> stocks = candidates.stream()
                .map(candidate -> new ProviderStock(
                        candidate.code(),
                        candidate.name(),
                        candidate.exchangeCode(),
                        candidate.market(),
                        candidate.industry(),
                        candidate.listDate(),
                        candidate.latestPrice(),
                        candidate.changePercent()
                ))
                .toList();

        log.info("Fetched {} stock candidates from AkShare", stocks.size());
        return stocks;
    }

    private List<AkshareStockCandidate> fetchCandidatesFromAkshare() {
        Path scriptPath = resolveAkshareScriptPath();
        if (!Files.exists(scriptPath)) {
            throw new IllegalStateException("AkShare stock import script not found: " + scriptPath);
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
                throw new IllegalStateException("AkShare stock import script failed. exitCode=" + exitCode + ", output=" + output);
            }

            return objectMapper.readValue(extractJsonArray(output), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to fetch stock candidates from AkShare", exception);
        }
    }

    private Path resolveAkshareScriptPath() {
        return Paths.get("scripts", "akshare_fetch_a_stocks.py").toAbsolutePath().normalize();
    }

    private String extractJsonArray(String output) {
        if (output == null) {
            throw new IllegalStateException("AkShare stock import script returned null output");
        }
        String trimmed = output.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start < 0 || end < start) {
            throw new IllegalStateException("AkShare stock import script did not return JSON array. output=" + trimmed);
        }
        return trimmed.substring(start, end + 1);
    }

    List<String> extractConceptsFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Set<String> concepts = new LinkedHashSet<>();
        extractConceptsWithPattern(html, THS_CONCEPT_TAG_PATTERN_J_POP_LINK, concepts);
        extractConceptsWithPattern(html, THS_CONCEPT_TAG_PATTERN_LEGACY, concepts);
        return List.copyOf(concepts);
    }

    private void extractConceptsWithPattern(String html, Pattern pattern, Set<String> concepts) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String concept = matcher.group("name");
            if (concept == null) {
                continue;
            }
            String normalized = concept.trim();
            if (!normalized.isEmpty()) {
                concepts.add(normalized);
            }
        }
    }

    private HttpHeaders buildConceptHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8");
        return headers;
    }

    private record AkshareStockCandidate(
            String code,
            String name,
            String exchangeCode,
            String market,
            String industry,
            String listDate,
            Double latestPrice,
            Double changePercent
    ) {
    }
}
