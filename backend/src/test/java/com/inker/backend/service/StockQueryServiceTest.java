package com.inker.backend.service;

import com.inker.backend.dto.StockDto;
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

        stockRepository.save(stock("600001", "人工智能一号", List.of("人工智能", "机器人")));
        stockRepository.save(stock("600002", "新能源一号", List.of("新能源", "光伏")));
        stockRepository.save(stock("600003", "空概念", List.of("")));
    }

    @Test
    void queryWithoutConceptShouldKeepExistingResults() {
        Page<StockDto> result = stockQueryService.query(null, null, null, null, null, 0, 20, "code", "ASC");

        assertEquals(3, result.getTotalElements());
    }

    @Test
    void queryShouldFilterByExactConcept() {
        Page<StockDto> result = stockQueryService.query(null, null, null, null, "人工智能", 0, 20, "code", "ASC");

        assertEquals(1, result.getTotalElements());
        assertEquals("人工智能一号", result.getContent().get(0).getName());
    }

    @Test
    void getAllConceptsShouldReturnSortedNonBlankUniqueValues() {
        stockRepository.save(stock("600004", "人工智能二号", List.of("人工智能", "  机器人  ")));

        List<String> concepts = stockQueryService.getAllConcepts();

        assertEquals(List.of("人工智能", "光伏", "新能源", "机器人"), concepts);
    }

    private Stock stock(String code, String name, List<String> concepts) {
        Stock stock = new Stock();
        stock.setCode(code);
        stock.setName(name);
        stock.setExchangeCode("SSE");
        stock.setMarket("A股");
        stock.setConcepts(concepts);
        stock.setSt(false);
        return stock;
    }
}
