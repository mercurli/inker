package com.inker.backend.service;

import com.inker.backend.dto.StockDto;
import com.inker.backend.dto.UpdateStockConceptsRequest;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(StockQueryService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class StockQueryServiceTest {

    @Autowired
    private StockQueryService stockQueryService;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        stockRepository.deleteAll();

        stockRepository.save(stock("600001", "人工智能一号", List.of("人工智能", "机器人"), 300D, 30D, 3D, 33D));
        stockRepository.save(stock("600002", "新能源一号", List.of("新能源", "光伏"), 100D, 10D, 1D, 11D));
        stockRepository.save(stock("600003", "空概念", List.of(""), 200D, 20D, 2D, 22D));
        stockRepository.save(stock("600099", "空指标", List.of(), null, null, null, null));
    }

    @Test
    void queryWithoutConceptShouldKeepExistingResults() {
        Page<StockDto> result = stockQueryService.query(null, null, null, null, null, 0, 20, "code", "ASC");

        assertEquals(4, result.getTotalElements());
    }

    @Test
    void queryShouldFilterByExactConcept() {
        Page<StockDto> result = stockQueryService.query(null, null, null, null, "人工智能", 0, 20, "code", "ASC");

        assertEquals(1, result.getTotalElements());
        assertEquals("人工智能一号", result.getContent().get(0).getName());
    }

    @Test
    void getAllConceptsShouldReturnSortedNonBlankUniqueValues() {
        stockRepository.save(stock("600004", "人工智能二号", List.of("人工智能", "  机器人  "), 400D, 40D, 4D, 44D));

        List<String> concepts = stockQueryService.getAllConcepts();

        assertEquals(List.of("人工智能", "光伏", "新能源", "机器人"), concepts);
    }

    @Test
    void queryShouldSortByTotalMarketValue() {
        Page<StockDto> descResult = stockQueryService.query(null, null, null, null, null, 0, 20, "totalMarketValue", "DESC");
        Page<StockDto> ascResult = stockQueryService.query(null, null, null, null, null, 0, 20, "totalMarketValue", "ASC");

        assertEquals(List.of("人工智能一号", "空概念", "新能源一号", "空指标"),
                descResult.getContent().stream().map(StockDto::getName).toList());
        assertEquals(List.of("新能源一号", "空概念", "人工智能一号", "空指标"),
                ascResult.getContent().stream().map(StockDto::getName).toList());
        assertEquals("空指标", descResult.getContent().get(3).getName());
    }

    @Test
    void queryShouldSortByQuoteMetrics() {
        assertNullsLastSortOrder("amount");
        assertNullsLastSortOrder("turnoverRate");
        assertNullsLastSortOrder("fiveDayChangePercent");
        assertNullsLastSortOrder("totalMarketValue");
        assertNullsLastSortOrder("dynamicPeRatio");
    }

    @Test
    void updateConceptsShouldNormalizePersistOrderAndPrimaryConcept() {
        Long stockId = stockRepository.findByCode("600001").orElseThrow().getId();

        StockDto result = stockQueryService.updateConcepts(
                stockId,
                updateConceptsRequest(List.of("  机器人  ", "", "人工智能", "机器人", " 算力 "))
        );

        assertEquals(List.of("机器人", "人工智能", "算力"), result.getConcepts());
        assertEquals("机器人", result.getPrimaryConcept());

        Stock saved = stockRepository.findById(stockId).orElseThrow();
        assertEquals(List.of("机器人", "人工智能", "算力"), saved.getConcepts());
        assertTrue(saved.isConceptsManuallyEdited());
    }

    @Test
    void updateConceptsShouldPromoteNextConceptWhenPrimaryIsRemoved() {
        Long stockId = stockRepository.findByCode("600001").orElseThrow().getId();

        StockDto result = stockQueryService.updateConcepts(
                stockId,
                updateConceptsRequest(List.of("机器人"))
        );

        assertEquals(List.of("机器人"), result.getConcepts());
        assertEquals("机器人", result.getPrimaryConcept());
    }

    private void assertNullsLastSortOrder(String sortBy) {
        Page<StockDto> descResult = stockQueryService.query(null, null, null, null, null, 0, 20, sortBy, "DESC");
        Page<StockDto> ascResult = stockQueryService.query(null, null, null, null, null, 0, 20, sortBy, "ASC");

        assertEquals(List.of("人工智能一号", "空概念", "新能源一号", "空指标"),
                descResult.getContent().stream().map(StockDto::getName).toList());
        assertEquals(List.of("新能源一号", "空概念", "人工智能一号", "空指标"),
                ascResult.getContent().stream().map(StockDto::getName).toList());
    }

    private UpdateStockConceptsRequest updateConceptsRequest(List<String> concepts) {
        UpdateStockConceptsRequest request = new UpdateStockConceptsRequest();
        request.setConcepts(concepts);
        return request;
    }

    private Stock stock(String code, String name, List<String> concepts, Double totalMarketValue,
                        Double amount, Double turnoverRate, Double dynamicPeRatio) {
        Stock stock = new Stock();
        stock.setCode(code);
        stock.setName(name);
        stock.setExchangeCode("SSE");
        stock.setMarket("A股");
        stock.setConcepts(concepts);
        stock.setTotalMarketValue(totalMarketValue);
        stock.setAmount(amount);
        stock.setTurnoverRate(turnoverRate);
        stock.setFiveDayChangePercent(dynamicPeRatio);
        stock.setDynamicPeRatio(dynamicPeRatio);
        stock.setSt(false);
        return stock;
    }
}
