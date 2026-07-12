package com.nexustrade.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {
    List<TradeEntity> findTop50ByStatusOrderByTsDesc(String status);

    List<TradeEntity> findTop50ByOrderByTsDesc();

    @Query("SELECT t FROM TradeEntity t WHERE t.status = 'EXECUTED' ORDER BY t.ts ASC")
    List<TradeEntity> findAllExecutedOrdered();

    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.status = 'EXECUTED'")
    long countExecuted();

    @Query("SELECT COUNT(t) FROM TradeEntity t WHERE t.status LIKE 'REJECTED%'")
    long countRejected();

    @Query("SELECT COALESCE(SUM(t.netProfit), 0) FROM TradeEntity t WHERE t.status = 'EXECUTED'")
    java.math.BigDecimal sumNetProfit();
}
