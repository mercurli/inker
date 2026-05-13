package com.inker.backend.repository;

import com.inker.backend.entity.WatchlistGroupStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WatchlistGroupStockRepository extends JpaRepository<WatchlistGroupStock, Long> {

    interface GroupIndustryCount {
        Long getGroupId();

        String getIndustry();

        Long getStockCount();
    }

    interface GroupPrimaryConceptCount {
        Long getGroupId();

        String getPrimaryConcept();

        Long getStockCount();
    }

    interface GroupAverageChangePercent {
        Long getGroupId();

        Double getAverageChangePercent();
    }

    boolean existsByGroupIdAndStockId(Long groupId, Long stockId);

    boolean existsByStockId(Long stockId);

    List<WatchlistGroupStock> findAllByGroupIdOrderBySortOrderAscAddedAtDescIdDesc(Long groupId);

    long countByGroupId(Long groupId);

    @Modifying
    void deleteByGroupIdAndStockId(Long groupId, Long stockId);

    @Modifying
    void deleteByGroupId(Long groupId);

    @Modifying
    void deleteByStockId(Long stockId);

    @Modifying
    long deleteByStockIdIn(Collection<Long> stockIds);

    @Query("select distinct gs.stock.id from WatchlistGroupStock gs")
    List<Long> findDistinctStockIds();

    @Query("select distinct gs.stock.id from WatchlistGroupStock gs where gs.group.id = :groupId")
    List<Long> findStockIdsByGroupId(@Param("groupId") Long groupId);

    @Query("select gs.group.id from WatchlistGroupStock gs where gs.stock.id = :stockId order by gs.group.sortOrder asc, gs.group.id asc")
    List<Long> findGroupIdsByStockId(@Param("stockId") Long stockId);

    @Query("""
            select gs.group.id as groupId,
                   avg(gs.stock.changePercent) as averageChangePercent
            from WatchlistGroupStock gs
            group by gs.group.id
            """)
    List<GroupAverageChangePercent> averageChangePercentByGroup();

    @Query("""
            select gs.group.id as groupId,
                   case
                       when gs.stock.industry is null or trim(gs.stock.industry) = '' then '未分类行业'
                       else gs.stock.industry
                   end as industry,
                   count(gs.id) as stockCount
            from WatchlistGroupStock gs
            group by gs.group.id,
                     case
                         when gs.stock.industry is null or trim(gs.stock.industry) = '' then '未分类行业'
                         else gs.stock.industry
                     end
            order by gs.group.id asc,
                     count(gs.id) desc,
                     case
                         when gs.stock.industry is null or trim(gs.stock.industry) = '' then '未分类行业'
                         else gs.stock.industry
                     end asc
            """)
    List<GroupIndustryCount> countIndustriesByGroup();

    @Query("""
            select gs.group.id as groupId,
                   case
                       when concept is null or trim(concept) = '' then '未分类概念'
                       else concept
                   end as primaryConcept,
                   count(gs.id) as stockCount
            from WatchlistGroupStock gs
            left join gs.stock.concepts concept on index(concept) = 0
            group by gs.group.id,
                     case
                         when concept is null or trim(concept) = '' then '未分类概念'
                         else concept
                     end
            order by gs.group.id asc,
                     count(gs.id) desc,
                     case
                         when concept is null or trim(concept) = '' then '未分类概念'
                         else concept
                     end asc
            """)
    List<GroupPrimaryConceptCount> countPrimaryConceptsByGroup();
}
