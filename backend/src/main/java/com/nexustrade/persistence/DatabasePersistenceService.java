package com.nexustrade.persistence;

import com.nexustrade.model.ArbitrageOpportunity;
import com.nexustrade.model.TriangularOpportunity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class DatabasePersistenceService {
    private static final Logger log = LoggerFactory.getLogger(DatabasePersistenceService.class);
    private final TradeRepository tradeRepo;

    public DatabasePersistenceService(TradeRepository tradeRepo) {
        this.tradeRepo = tradeRepo;
    }

    public void recordDirectTrade(ArbitrageOpportunity opp) {
        try {
            TradeEntity entity = new TradeEntity();
            entity.setTs(Instant.ofEpochMilli(opp.timestampMs()));
            entity.setType("DIRECT");
            entity.setStatus(opp.status().name());
            entity.setBuyExchange(opp.buyExchange());
            entity.setSellExchange(opp.sellExchange());
            entity.setBuyPrice(opp.buyPrice());
            entity.setSellPrice(opp.sellPrice());
            entity.setVolume(opp.volume());
            entity.setGrossSpread(opp.grossSpread());
            entity.setFeesTotal(opp.feesTotal());
            entity.setNetProfit(opp.netProfit());
            entity.setSpreadPct(opp.spreadPct());
            entity.setDecisionLatencyMs((int) opp.decisionLatencyMs());
            if (opp.rejectionReason() != null) {
                entity.setRejectionReason(opp.rejectionReason());
            }
            tradeRepo.save(entity);
        } catch (Exception e) {
            log.warn("Could not save direct trade to DB: {}", e.getMessage());
        }
    }

    public void recordTriangularTrade(TriangularOpportunity opp) {
        try {
            TradeEntity entity = new TradeEntity();
            entity.setTs(Instant.ofEpochMilli(opp.timestampMs()));
            entity.setType("TRIANGULAR");
            entity.setStatus(opp.status().name());
            entity.setExchange(opp.exchange());
            entity.setNetProfit(opp.netProfit());
            entity.setFeesTotal(opp.feesTotal());
            tradeRepo.save(entity);
        } catch (Exception e) {
            log.warn("Could not save triangular trade to DB: {}", e.getMessage());
        }
    }
    /**
     * Update the AI confidence score of the most recently saved trade for this opportunity.
     * Called right after recordDirectTrade to attach the score.
     */
    public void setAiScore(ArbitrageOpportunity opp, int score) {
        try {
            // Find the trade we just saved — use ts + buyExchange + sellExchange as key
            java.time.Instant ts = java.time.Instant.ofEpochMilli(opp.timestampMs());
            tradeRepo.findTop50ByStatusOrderByTsDesc("EXECUTED").stream()
                    .filter(t -> opp.buyExchange().equals(t.getBuyExchange())
                            && opp.sellExchange().equals(t.getSellExchange()))
                    .findFirst()
                    .ifPresent(t -> {
                        t.setAiConfidence(BigDecimal.valueOf(score));
                        tradeRepo.save(t);
                    });
        } catch (Exception e) {
            log.warn("Could not update AI score: {}", e.getMessage());
        }
    }
}
