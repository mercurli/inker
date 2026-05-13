package com.inker.backend.service;

import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import com.inker.backend.service.provider.StockConceptProvider;
import com.inker.backend.service.provider.StockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import({StockImportService.class, StockImportServiceTest.Config.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class StockImportServiceTest {

    @Autowired
    private StockImportService stockImportService;

    @Autowired
    private StockRepository stockRepository;

    @Test
    void importShouldPreserveManuallyEditedConceptsAndSyncUneditedConcepts() {
        Stock manualStock = stock("600001", List.of("人工主概念", "人工次概念"), true);
        Stock syncedStock = stock("600002", List.of("旧概念"), false);
        stockRepository.saveAll(List.of(manualStock, syncedStock));

        stockImportService.importAStocksExcludeStAndBse();

        assertEquals(
                List.of("人工主概念", "人工次概念"),
                stockRepository.findByCode("600001").orElseThrow().getConcepts()
        );
        assertEquals(
                List.of("外部主概念-600002", "外部次概念"),
                stockRepository.findByCode("600002").orElseThrow().getConcepts()
        );
        assertEquals(
                List.of("外部主概念-600003", "外部次概念"),
                stockRepository.findByCode("600003").orElseThrow().getConcepts()
        );
        assertEquals(
                "/api/v1/logos/stocks/600003.png",
                stockRepository.findByCode("600003").orElseThrow().getLogo()
        );
    }

    private Stock stock(String code, List<String> concepts, boolean conceptsManuallyEdited) {
        Stock stock = new Stock();
        stock.setCode(code);
        stock.setName("股票" + code);
        stock.setExchangeCode("SSE");
        stock.setMarket("A股");
        stock.setConcepts(concepts);
        stock.setConceptsManuallyEdited(conceptsManuallyEdited);
        stock.setSt(false);
        return stock;
    }

    @TestConfiguration
    static class Config {
        @Bean
        StockLogoService stockLogoService() {
            return new StockLogoService("target/test-logos") {
                @Override
                public Optional<String> downloadLogo(String stockCode, String website) {
                    return Optional.of("/api/v1/logos/stocks/" + stockCode + ".png");
                }
            };
        }

        @Bean
        StockConceptProvider stockConceptProvider() {
            return (code, market) -> List.of("外部主概念-" + code, "外部次概念");
        }

        @Bean
        StockProvider stockProvider() {
            return new StockProvider() {
                @Override
                public List<ProviderStock> fetchAllAStockCandidates() {
                    return List.of(
                            providerStock("600001"),
                            providerStock("600002"),
                            providerStock("600003")
                    );
                }

                private ProviderStock providerStock(String code) {
                    return new ProviderStock(code, "股票" + code, "SSE", "A股", "行业", "20200101", 1D, 2D);
                }
            };
        }
    }
}
