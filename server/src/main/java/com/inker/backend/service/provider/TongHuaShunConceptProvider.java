package com.inker.backend.service.provider;

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

import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TongHuaShunConceptProvider implements StockConceptProvider {

    private static final Logger log = LoggerFactory.getLogger(TongHuaShunConceptProvider.class);
    private static final String CONCEPT_URL_TEMPLATE = "http://basic.10jqka.com.cn/new/%s/concept.html";
    private static final Charset RESPONSE_CHARSET = Charset.forName("GBK");
    private static final Pattern CONCEPT_TAG_PATTERN_LEGACY = Pattern.compile(
            "class\\s*=\\s*[\"']gnStockList[\"'][^>]*tag\\s*=\\s*[\"'][^\"']*?-(?<name>[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONCEPT_TAG_PATTERN_J_POP_LINK = Pattern.compile(
            "<a(?=[^>]*\\bclass\\s*=\\s*[\"'][^\"']*\\bJ_popLink\\b)"
                    + "(?=[^>]*\\btopStock\\s*=\\s*[\"'][^\"']+[\"'])"
                    + "(?=[^>]*\\btag\\s*=\\s*[\"'](?<name>[^\"']+)[\"'])[^>]*>",
            Pattern.CASE_INSENSITIVE
    );

    private final RestTemplate restTemplate;

    public TongHuaShunConceptProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public List<String> fetchConcepts(String code, String market) {
        if (code == null || code.isBlank()) {
            return List.of();
        }

        String url = CONCEPT_URL_TEMPLATE.formatted(code.trim());
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                return List.of();
            }

            return extractConceptsFromHtml(new String(body, RESPONSE_CHARSET));
        } catch (RestClientException exception) {
            log.warn("Failed to fetch concepts from TongHuaShun. code={}, market={}, message={}",
                    code, market, exception.getMessage());
            return List.of();
        }
    }

    List<String> extractConceptsFromHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Set<String> concepts = new LinkedHashSet<>();
        extractConceptsWithPattern(html, CONCEPT_TAG_PATTERN_J_POP_LINK, concepts);
        extractConceptsWithPattern(html, CONCEPT_TAG_PATTERN_LEGACY, concepts);
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

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8");
        return headers;
    }
}
