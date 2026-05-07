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

    boolean existsByGroupIdAndStockId(Long groupId, Long stockId);

    boolean existsByStockId(Long stockId);

    List<WatchlistGroupStock> findAllByGroupIdOrderByAddedAtDescIdDesc(Long groupId);

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
}
