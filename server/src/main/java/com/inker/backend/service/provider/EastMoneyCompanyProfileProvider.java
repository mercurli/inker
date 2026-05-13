package com.inker.backend.service.provider;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Optional;

@Component
public class EastMoneyCompanyProfileProvider implements CompanyProfileProvider {

    private static final Logger log = LoggerFactory.getLogger(EastMoneyCompanyProfileProvider.class);
    private static final String COMPANY_SURVEY_URL_TEMPLATE =
            "https://emweb.securities.eastmoney.com/PC_HSF10/CompanySurvey/PageAjax?code=%s";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EastMoneyCompanyProfileProvider() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<String> fetchCompanyWebsite(String code, String exchangeCode) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        String eastMoneyCode = toEastMoneyCode(code, exchangeCode);
        if (eastMoneyCode == null) {
            return Optional.empty();
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    COMPANY_SURVEY_URL_TEMPLATE.formatted(eastMoneyCode),
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    String.class
            );
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode company = root.path("jbzl").isArray() && !root.path("jbzl").isEmpty()
                    ? root.path("jbzl").get(0)
                    : null;
            String website = company == null ? null : company.path("ORG_WEB").asText(null);
            if (website == null || website.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(website.trim());
        } catch (RestClientException exception) {
            log.warn("Failed to fetch company profile from EastMoney. code={}, exchangeCode={}, message={}",
                    code, exchangeCode, exception.getMessage());
            return Optional.empty();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse EastMoney company profile response", exception);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36");
        headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9,en;q=0.8");
        return headers;
    }

    String toEastMoneyCode(String code, String exchangeCode) {
        String normalizedCode = code.trim();
        if ("SSE".equals(exchangeCode)) {
            return "SH" + normalizedCode;
        }
        if ("SZSE".equals(exchangeCode)) {
            return "SZ" + normalizedCode;
        }
        if (normalizedCode.startsWith("6")) {
            return "SH" + normalizedCode;
        }
        if (normalizedCode.startsWith("0") || normalizedCode.startsWith("3")) {
            return "SZ" + normalizedCode;
        }
        return null;
    }
}
