package com.inker.backend.service;

import com.inker.backend.dto.MarketSummaryDto;
import com.inker.backend.dto.StockDto;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StockQueryService {

    private final StockRepository stockRepository;

    public StockQueryService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Page<StockDto> query(String keyword,
                                String exchangeCode,
                                String boardType,
                                String industry,
                                int page,
                                int size,
                                String sortBy,
                                String sortDirection) {
        String normalizedKeyword = normalize(keyword);
        String normalizedExchangeCode = normalize(exchangeCode);
        String normalizedBoardType = normalize(boardType);
        String normalizedIndustry = normalize(industry);
        String normalizedSortBy = normalizeSortBy(sortBy);
        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, normalizedSortBy));

        Specification<Stock> specification = Specification
                .where(byKeyword(normalizedKeyword))
                .and(byExchange(normalizedExchangeCode))
                .and(byBoardType(normalizedBoardType))
                .and(byIndustry(normalizedIndustry));

        return stockRepository.findAll(specification, pageable)
                .map(StockDto::fromEntity);
    }

    public Optional<StockDto> getById(Long id) {
        return stockRepository.findById(id).map(StockDto::fromEntity);
    }

    public List<String> getAllIndustries() {
        return stockRepository.findDistinctIndustries();
    }

    public MarketSummaryDto getMarketSummary() {
        long total = stockRepository.count();
        long rising = stockRepository.countByChangePercentGreaterThan(0D);
        long falling = stockRepository.countByChangePercentLessThan(0D);
        long flat = total - rising - falling;
        var lastSyncedAt = stockRepository.findLastUpdatedAt().orElse(null);

        MarketSummaryDto.LeaderDto strongest = stockRepository.findFirstByChangePercentIsNotNullOrderByChangePercentDesc()
                .map(stock -> MarketSummaryDto.LeaderDto.builder()
                        .id(stock.getId())
                        .symbol(stock.getCode())
                        .name(stock.getName())
                        .changePercent(stock.getChangePercent())
                        .build())
                .orElse(null);

        return MarketSummaryDto.builder()
                .total(total)
                .rising(rising)
                .falling(falling)
                .flat(flat)
                .lastSyncedAt(lastSyncedAt)
                .strongest(strongest)
                .distribution(buildDistribution())
                .build();
    }

    private List<MarketSummaryDto.DistributionBucketDto> buildDistribution() {
        List<Double> changes = stockRepository.findAllChangePercents();

        List<DistributionRange> ranges = List.of(
                new DistributionRange(Double.NEGATIVE_INFINITY, -10D, "<-10%", "down"),
                new DistributionRange(-10D, -7D, "10~7", "down"),
                new DistributionRange(-7D, -5D, "7~5", "down"),
                new DistributionRange(-5D, -3D, "5~3", "down"),
                new DistributionRange(-3D, 0D, "3~0", "down"),
                new DistributionRange(0D, 0.000001D, "0", "neutral"),
                new DistributionRange(0.000001D, 3D, "0~3", "up"),
                new DistributionRange(3D, 5D, "3~5", "up"),
                new DistributionRange(5D, 7D, "5~7", "up"),
                new DistributionRange(7D, 10D, "7~10", "up"),
                new DistributionRange(10D, Double.POSITIVE_INFINITY, ">10%", "up")
        );

        return ranges.stream()
                .map(range -> MarketSummaryDto.DistributionBucketDto.builder()
                        .label(range.label())
                        .tone(range.tone())
                        .count(changes.stream()
                                .filter(change -> {
                                    double normalizedChange = change == null ? 0D : change;
                                    return normalizedChange >= range.min() && normalizedChange < range.max();
                                })
                                .count())
                        .build())
                .toList();
    }

    private record DistributionRange(double min, double max, String label, String tone) {
    }

    private Specification<Stock> byKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%")
        );
    }

    private Specification<Stock> byExchange(String exchangeCode) {
        if (exchangeCode == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("exchangeCode"), exchangeCode);
    }

    private Specification<Stock> byBoardType(String boardType) {
        if (boardType == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("boardType"), boardType);
    }

    private Specification<Stock> byIndustry(String industry) {
        if (industry == null) {
            return null;
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get("industry")), "%" + industry.toLowerCase() + "%");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "code";
        }
        return switch (sortBy) {
            case "name", "exchangeCode", "market", "industry", "listDate", "id", "latestPrice", "changePercent", "boardType" -> sortBy;
            default -> "code";
        };
    }
}
