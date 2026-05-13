package com.inker.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public class StockConceptRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public int deleteByStockIdIn(Collection<Long> stockIds) {
        if (stockIds == null || stockIds.isEmpty()) {
            return 0;
        }
        return entityManager.createNativeQuery("delete from stock_concepts where stock_id in (:stockIds)")
                .setParameter("stockIds", stockIds)
                .executeUpdate();
    }
}
