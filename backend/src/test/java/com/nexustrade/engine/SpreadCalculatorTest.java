package com.nexustrade.engine;

import com.nexustrade.model.ArbitrageOpportunity;
import com.nexustrade.model.OrderBookSnapshot;
import com.nexustrade.model.TradeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class SpreadCalculatorTest {

    private SpreadCalculator calculator;

    @Mock
    private EngineConfig engineConfig;

    @Mock
    private EngineConfig.Engine engineSettings;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(engineConfig.getEngine()).thenReturn(engineSettings);
        when(engineSettings.getMaxVolumeBtc()).thenReturn(0.1);
        when(engineSettings.getDecisionTimeoutMs()).thenReturn(1000L);
        when(engineSettings.getMinProfitUsd()).thenReturn(5.0);

        SlippageEstimator slippageEstimator = new SlippageEstimator();
        calculator = new SpreadCalculator(slippageEstimator, engineConfig);
    }

    private OrderBookSnapshot createSnapshot(String exchange, double askPrice, double askVol, double bidPrice, double bidVol) {
        NavigableMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        asks.put(BigDecimal.valueOf(askPrice), BigDecimal.valueOf(askVol));
        
        NavigableMap<BigDecimal, BigDecimal> bids = new TreeMap<>((a, b) -> b.compareTo(a)); // Descending
        bids.put(BigDecimal.valueOf(bidPrice), BigDecimal.valueOf(bidVol));
        
        return new OrderBookSnapshot(
            exchange, "BTC/USDT", 
            BigDecimal.valueOf(bidPrice), BigDecimal.valueOf(bidVol),
            BigDecimal.valueOf(askPrice), BigDecimal.valueOf(askVol),
            System.currentTimeMillis(), 
            bids, asks
        );
    }

    @Test
    void testProfitableSpread() {
        // Exchange A (Buy): Ask is 60,000
        OrderBookSnapshot buySnap = createSnapshot("EX_A", 60000.0, 1.0, 59900.0, 1.0);
        // Exchange B (Sell): Bid is 61,000
        OrderBookSnapshot sellSnap = createSnapshot("EX_B", 61100.0, 1.0, 61000.0, 1.0);

        // 0.1% fees, 0 withdrawal
        when(engineConfig.getTakerFee("EX_A")).thenReturn(new BigDecimal("0.001"));
        when(engineConfig.getTakerFee("EX_B")).thenReturn(new BigDecimal("0.001"));
        when(engineConfig.getWithdrawalFee(anyString())).thenReturn(BigDecimal.ZERO);

        Optional<ArbitrageOpportunity> oppOpt = calculator.evaluate(buySnap, sellSnap, System.nanoTime());

        assertTrue(oppOpt.isPresent(), "Opportunity should be evaluated");
        ArbitrageOpportunity opp = oppOpt.get();

        assertEquals(TradeStatus.EXECUTED, opp.status(), "Should be EXECUTED");
        
        // Volume traded is 0.1 (max allowed by config)
        assertEquals(0.1, opp.volume().doubleValue(), 0.0001);
        
        // Buy Cost = 60000 * 0.1 * 1.001 = 6006
        // Sell Revenue = 61000 * 0.1 * 0.999 = 6093.9
        // Net Profit = 6093.9 - 6006 = 87.9
        assertEquals(87.9, opp.netProfit().doubleValue(), 0.001);
    }

    @Test
    void testUnprofitableSpreadDueToFees() {
        // Exchange A (Buy): Ask is 60,000
        OrderBookSnapshot buySnap = createSnapshot("EX_A", 60000.0, 1.0, 59900.0, 1.0);
        // Exchange B (Sell): Bid is 60,050
        OrderBookSnapshot sellSnap = createSnapshot("EX_B", 60100.0, 1.0, 60050.0, 1.0);

        // High fees: 0.2% on both sides
        when(engineConfig.getTakerFee("EX_A")).thenReturn(new BigDecimal("0.002"));
        when(engineConfig.getTakerFee("EX_B")).thenReturn(new BigDecimal("0.002"));
        when(engineConfig.getWithdrawalFee(anyString())).thenReturn(BigDecimal.ZERO);

        Optional<ArbitrageOpportunity> oppOpt = calculator.evaluate(buySnap, sellSnap, System.nanoTime());

        assertTrue(oppOpt.isPresent());
        ArbitrageOpportunity opp = oppOpt.get();

        // Gross spread is 50, but fees make it negative
        // Buy Cost = 60000 * 0.1 * 1.002 = 6012
        // Sell Revenue = 60050 * 0.1 * 0.998 = 5992.99
        // Net Profit = 5992.99 - 6012 = -19.01
        assertEquals(TradeStatus.REJECTED_FEES, opp.status(), "Should be rejected due to fees");
        assertTrue(opp.netProfit().doubleValue() < 0, "Net profit should be negative");
        assertEquals(-19.01, opp.netProfit().doubleValue(), 0.01);
    }

    @Test
    void testNegativeGrossSpread() {
        // Exchange A (Buy): Ask is 61,000
        OrderBookSnapshot buySnap = createSnapshot("EX_A", 61000.0, 1.0, 60900.0, 1.0);
        // Exchange B (Sell): Bid is 60,000
        OrderBookSnapshot sellSnap = createSnapshot("EX_B", 60100.0, 1.0, 60000.0, 1.0);

        when(engineConfig.getTakerFee(anyString())).thenReturn(new BigDecimal("0.001"));
        when(engineConfig.getWithdrawalFee(anyString())).thenReturn(BigDecimal.ZERO);

        Optional<ArbitrageOpportunity> oppOpt = calculator.evaluate(buySnap, sellSnap, System.nanoTime());

        assertTrue(oppOpt.isPresent());
        ArbitrageOpportunity opp = oppOpt.get();

        assertEquals(TradeStatus.REJECTED_FEES, opp.status(), "Should be rejected");
        assertEquals(-1000.0, opp.grossSpread().doubleValue(), 0.001);
        assertTrue(opp.rejectionReason().contains("Negative gross spread"));
    }
}
