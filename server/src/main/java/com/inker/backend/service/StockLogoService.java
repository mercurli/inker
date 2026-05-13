package com.inker.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class StockLogoService {

    private static final Logger log = LoggerFactory.getLogger(StockLogoService.class);
    private static final String LOGO_URL_PREFIX = "/api/v1/logos/stocks/";
    private static final String BAIDU_STOCK_LOGO_URL_TEMPLATE =
            "https://baidu-finance.cdn.bcebos.com/imgs/logo/stocks/stock_ab_%s.svg";

    private final RestTemplate restTemplate;
    private final Path storagePath;

    public StockLogoService(@Value("${inker.logo.storage-path:data/logos}") String storagePath) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.restTemplate = new RestTemplate(factory);
        this.storagePath = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    public Optional<String> downloadLogo(String stockCode, String website) {
        String code = normalizeCode(stockCode);
        if (code == null) {
            return Optional.empty();
        }

        return downloadBaiduSvgLogo(code);
    }

    public Optional<String> findExistingLogo(String stockCode) {
        String code = normalizeCode(stockCode);
        if (code == null) {
            return Optional.empty();
        }

        Path stockLogoDir = storagePath.resolve("stocks");
        if (!Files.isDirectory(stockLogoDir)) {
            return Optional.empty();
        }

        Path svgPath = stockLogoDir.resolve(code + ".svg");
        if (Files.isRegularFile(svgPath)) {
            return Optional.of(LOGO_URL_PREFIX + svgPath.getFileName());
        }

        return Optional.empty();
    }

    public int deleteLegacyLogos(String stockCode) {
        String code = normalizeCode(stockCode);
        if (code == null) {
            return 0;
        }

        Path stockLogoDir = storagePath.resolve("stocks");
        if (!Files.isDirectory(stockLogoDir)) {
            return 0;
        }

        int deleted = 0;
        try (Stream<Path> files = Files.list(stockLogoDir)) {
            List<Path> legacyFiles = files
                    .filter(Files::isRegularFile)
                    .filter(path -> filenameMatchesCode(path, code))
                    .filter(path -> !path.getFileName().toString().toLowerCase().endsWith(".svg"))
                    .toList();

            for (Path legacyFile : legacyFiles) {
                Files.deleteIfExists(legacyFile);
                deleted++;
            }
        } catch (IOException exception) {
            log.debug("Failed to delete legacy stock logo. code={}, message={}", stockCode, exception.getMessage());
        }

        return deleted;
    }

    private Optional<String> downloadBaiduSvgLogo(String stockCode) {
        String logoUrl = BAIDU_STOCK_LOGO_URL_TEMPLATE.formatted(stockCode);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    logoUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    byte[].class
            );
            byte[] body = response.getBody();
            if (body == null || body.length < 64) {
                return Optional.empty();
            }

            String contentType = response.getHeaders().getContentType() == null
                    ? null
                    : response.getHeaders().getContentType().toString();
            if (contentType == null || !contentType.toLowerCase().startsWith("image/svg")) {
                return Optional.empty();
            }

            Path stockLogoDir = storagePath.resolve("stocks");
            Files.createDirectories(stockLogoDir);
            Path logoPath = stockLogoDir.resolve(stockCode + ".svg");
            Files.write(logoPath, body);
            return Optional.of(LOGO_URL_PREFIX + logoPath.getFileName());
        } catch (RestClientException | IOException exception) {
            log.debug("Failed to download stock logo. url={}, message={}", logoUrl, exception.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.setAccept(List.of(MediaType.valueOf("image/svg+xml"), MediaType.ALL));
        return headers;
    }

    private String normalizeCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }
        return stockCode.trim().replaceAll("[^A-Za-z0-9_-]", "");
    }

    private boolean filenameMatchesCode(Path path, String code) {
        String filename = path.getFileName().toString();
        int dotIndex = filename.indexOf('.');
        String baseName = dotIndex < 0 ? filename : filename.substring(0, dotIndex);
        return code.equals(baseName);
    }
}
