package com.nexustrade.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Virtual wallet for a single exchange. Tracks USDT and BTC balances.
 * Thread-safe via synchronized methods.
 */
public class Wallet {

    private static final Logger log = LoggerFactory.getLogger(Wallet.class);

    private final String exchange;
    private BigDecimal usdt;
    private BigDecimal btc;

    public Wallet(String exchange, double initialUsdt, double initialBtc) {
        this.exchange = exchange;
        this.usdt = BigDecimal.valueOf(initialUsdt);
        this.btc = BigDecimal.valueOf(initialBtc);
    }

    /** Deduct USDT to buy BTC */
    public synchronized boolean buy(BigDecimal usdtCost, BigDecimal btcAmount) {
        if (usdtCost.compareTo(usdt) > 0) {
            log.warn("[{}] Insufficient USDT: need {} have {}", exchange, usdtCost, usdt);
            return false;
        }
        usdt = usdt.subtract(usdtCost);
        btc = btc.add(btcAmount);
        return true;
    }

    /** Sell BTC to receive USDT */
    public synchronized boolean sell(BigDecimal btcAmount, BigDecimal usdtReceived) {
        if (btcAmount.compareTo(btc) > 0) {
            log.warn("[{}] Insufficient BTC: need {} have {}", exchange, btcAmount, btc);
            return false;
        }
        btc = btc.subtract(btcAmount);
        usdt = usdt.add(usdtReceived);
        return true;
    }

    /** Transfer BTC to another wallet (virtual rebalance) */
    public synchronized boolean transferBtcOut(BigDecimal amount) {
        if (amount.compareTo(btc) > 0) return false;
        btc = btc.subtract(amount);
        return true;
    }

    public synchronized void receiveBtc(BigDecimal amount) {
        btc = btc.add(amount);
    }

    public synchronized void receiveUsdt(BigDecimal amount) {
        usdt = usdt.add(amount);
    }

    public synchronized BigDecimal getUsdt() { return usdt; }
    public synchronized BigDecimal getBtc() { return btc; }
    public String getExchange() { return exchange; }

    /** Total value in USDT at given BTC price */
    public synchronized BigDecimal totalValueUsdt(BigDecimal btcPrice) {
        return usdt.add(btc.multiply(btcPrice));
    }

    @Override
    public synchronized String toString() {
        return String.format("[%s] USDT=%.2f BTC=%.6f", exchange, usdt, btc);
    }
}
