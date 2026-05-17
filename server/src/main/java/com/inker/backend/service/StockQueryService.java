package com.inker.backend.service;

import com.inker.backend.dto.MarketSummaryDto;
import com.inker.backend.dto.StockDto;
import com.inker.backend.dto.UpdateStockConceptsRequest;
import com.inker.backend.entity.Stock;
import com.inker.backend.repository.StockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockQueryService {

    private static final Set<String> NULLS_LAST_SORT_FIELDS = Set.of(
            "amount",
            "turnoverRate",
            "fiveDayChangePercent",
            "totalMarketValue",
            "dynamicPeRatio"
    );
    private static final double FIVE_DAY_RISING_THRESHOLD = 5D;
    private static final int TOP_FIVE_DAY_RISING_GROUP_LIMIT = 5;
    private static final String UNCLASSIFIED_INDUSTRY_LABEL = "未分类行业";
    private static final String EMPTY_CONCEPT_LABEL = "暂无概念";

    private final StockRepository stockRepository;

    public StockQueryService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Page<StockDto> query(String keyword,
                                String exchangeCode,
                                String boardType,
                                String industry,
                                String concept,
                                int page,
                                int size,
                                String sortBy,
                                String sortDirection) {
        String normalizedKeyword = normalize(keyword);
        String normalizedExchangeCode = normalize(exchangeCode);
        String normalizedBoardType = normalize(boardType);
        String normalizedIndustry = normalize(industry);
        String normalizedConcept = normalize(concept);
        String normalizedSortBy = normalizeSortBy(sortBy);
        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        boolean nullsLastSort = NULLS_LAST_SORT_FIELDS.contains(normalizedSortBy);
        Pageable pageable = PageRequest.of(page, size, nullsLastSort ? Sort.unsorted() : buildSort(normalizedSortBy, direction));

        Specification<Stock> specification = Specification
                .where(byKeyword(normalizedKeyword))
                .and(byExchange(normalizedExchangeCode))
                .and(byBoardType(normalizedBoardType))
                .and(byIndustry(normalizedIndustry))
                .and(byConcept(normalizedConcept));

        if (nullsLastSort) {
            specification = specification.and(orderByNullsLast(normalizedSortBy, direction));
        }

        return stockRepository.findAll(specification, pageable)
                .map(StockDto::fromEntity);
    }

    public Optional<StockDto> getById(Long id) {
        return stockRepository.findById(id).map(StockDto::fromEntity);
    }

    public Page<StockDto> queryStrengthMembers(String type,
                                               String label,
                                               int page,
                                               int size,
                                               String sortBy,
                                               String sortDirection) {
        String normalizedType = normalize(type);
        String normalizedLabel = normalize(label);
        String normalizedSortBy = normalizeSortBy(sortBy);
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, buildSort(normalizedSortBy, direction));

        if (normalizedType == null || normalizedLabel == null || (!"industry".equals(normalizedType) && !"concept".equals(normalizedType))) {
            return Page.empty(pageable);
        }

        List<StockDto> results = stockRepository.findAll().stream()
                .filter(stock -> stock.getFiveDayChangePercent() != null)
                .filter(stock -> stock.getFiveDayChangePercent() > FIVE_DAY_RISING_THRESHOLD)
                .filter(stock -> strengthLabel(stock, normalizedType).equals(normalizedLabel))
                .sorted(strengthMemberComparator(normalizedSortBy, direction))
                .map(StockDto::fromEntity)
                .toList();

        int fromIndex = Math.min((int) pageable.getOffset(), results.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), results.size());

        return new PageImpl<>(results.subList(fromIndex, toIndex), pageable, results.size());
    }

    @Transactional
    public StockDto updateConcepts(Long id, UpdateStockConceptsRequest request) {
        Stock stock = stockRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Stock not found, id=" + id));
        List<String> concepts = StockDto.normalizeConcepts(request.getConcepts());

        stock.setConcepts(concepts);
        stock.setConceptsManuallyEdited(true);
        return StockDto.fromEntity(stockRepository.save(stock));
    }

    public List<String> getAllIndustries() {
        return stockRepository.findDistinctIndustries();
    }

    public List<String> getAllConcepts() {
        return stockRepository.findDistinctConcepts().stream()
                .map(String::trim)
                .filter(concept -> !concept.isBlank())
                .distinct()
                .sorted(String::compareTo)
                .toList();
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
                .topFiveDayRisingIndustries(buildTopFiveDayRisingGroups(this::industryLabel))
                .topFiveDayRisingConcepts(buildTopFiveDayRisingGroups(this::primaryConceptLabel))
                .build();
    }

    private List<MarketSummaryDto.DistributionBucketDto> buildTopFiveDayRisingGroups(Function<Stock, String> labelMapper) {
        Map<String, Long> counts = stockRepository.findAll().stream()
                .filter(stock -> stock.getFiveDayChangePercent() != null)
                .filter(stock -> stock.getFiveDayChangePercent() > FIVE_DAY_RISING_THRESHOLD)
                .collect(Collectors.groupingBy(labelMapper, LinkedHashMap::new, Collectors.counting()));

        return counts.entrySet().stream()
                .sorted(
                        Comparator.<Map.Entry<String, Long>>comparingLong(entry -> entry.getValue())
                                .reversed()
                                .thenComparing(Map.Entry::getKey)
                )
                .limit(TOP_FIVE_DAY_RISING_GROUP_LIMIT)
                .map(entry -> MarketSummaryDto.DistributionBucketDto.builder()
                        .label(entry.getKey())
                        .count(entry.getValue())
                        .tone("up")
                        .build())
                .toList();
    }

    private String strengthLabel(Stock stock, String type) {
        return "concept".equals(type) ? primaryConceptLabel(stock) : industryLabel(stock);
    }

    private Comparator<Stock> strengthMemberComparator(String sortBy, Sort.Direction direction) {
        return (left, right) -> {
            int result = compareSortValue(sortValue(left, sortBy), sortValue(right, sortBy), direction);

            if (result != 0) {
                return result;
            }

            return compareSortValue(left.getCode(), right.getCode());
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareSortValue(Comparable left, Comparable right, Sort.Direction direction) {
        if (left == null && right == null) {
            return 0;
        }

        if (left == null) {
            return 1;
        }

        if (right == null) {
            return -1;
        }

        int result = left.compareTo(right);
        return direction == Sort.Direction.DESC ? -result : result;
    }

    private int compareSortValue(Comparable<?> left, Comparable<?> right) {
        return compareSortValue(left, right, Sort.Direction.ASC);
    }

    private Comparable<?> sortValue(Stock stock, String sortBy) {
        return switch (sortBy) {
            case "name" -> stock.getName();
            case "exchangeCode" -> stock.getExchangeCode();
            case "market" -> stock.getMarket();
            case "industry" -> stock.getIndustry();
            case "listDate" -> stock.getListDate();
            case "id" -> stock.getId();
            case "latestPrice" -> stock.getLatestPrice();
            case "changePercent" -> stock.getChangePercent();
            case "fiveDayChangePercent" -> stock.getFiveDayChangePercent();
            case "amount" -> stock.getAmount();
            case "turnoverRate" -> stock.getTurnoverRate();
            case "totalMarketValue" -> stock.getTotalMarketValue();
            case "dynamicPeRatio" -> stock.getDynamicPeRatio();
            case "boardType" -> stock.getBoardType();
            default -> stock.getCode();
        };
    }

    private String industryLabel(Stock stock) {
        String industry = normalize(stock.getIndustry());
        return industry == null ? UNCLASSIFIED_INDUSTRY_LABEL : industry;
    }

    private String primaryConceptLabel(Stock stock) {
        if (stock.getConcepts() == null) {
            return EMPTY_CONCEPT_LABEL;
        }

        return stock.getConcepts().stream()
                .map(this::normalize)
                .filter(concept -> concept != null)
                .findFirst()
                .orElse(EMPTY_CONCEPT_LABEL);
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

    private Specification<Stock> byConcept(String concept) {
        if (concept == null) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(cb.trim(root.join("concepts")), concept);
        };
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
            case "name", "exchangeCode", "market", "industry", "listDate", "id", "latestPrice", "changePercent",
                    "fiveDayChangePercent", "amount", "turnoverRate", "totalMarketValue", "dynamicPeRatio", "boardType" -> sortBy;
            default -> "code";
        };
    }

    private Sort buildSort(String sortBy, Sort.Direction direction) {
        return Sort.by(Sort.Order.by(sortBy).with(direction));
    }

    private Specification<Stock> orderByNullsLast(String sortBy, Sort.Direction direction) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var nullRank = cb.selectCase()
                        .when(cb.isNull(root.get(sortBy)), 1)
                        .otherwise(0);

                query.orderBy(
                        cb.asc(nullRank),
                        direction == Sort.Direction.DESC ? cb.desc(root.get(sortBy)) : cb.asc(root.get(sortBy))
                );
            }

            return null;
        };
    }
}
