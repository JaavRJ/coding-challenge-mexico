package com.nexustrade.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_snapshots")
public class WalletSnapshotEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts = Instant.now();

    @Column(nullable = false, length = 20)
    private String exchange;

    @Column(name = "usdt_balance", nullable = false, precision = 18, scale = 8)
    private BigDecimal usdtBalance;

    @Column(name = "btc_balance", nullable = false, precision = 18, scale = 8)
    private BigDecimal btcBalance;

    @Column(name = "total_usd_value", precision = 18, scale = 8)
    private BigDecimal totalUsdValue;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public BigDecimal getUsdtBalance() { return usdtBalance; }
    public void setUsdtBalance(BigDecimal usdtBalance) { this.usdtBalance = usdtBalance; }
    public BigDecimal getBtcBalance() { return btcBalance; }
    public void setBtcBalance(BigDecimal btcBalance) { this.btcBalance = btcBalance; }
    public BigDecimal getTotalUsdValue() { return totalUsdValue; }
    public void setTotalUsdValue(BigDecimal totalUsdValue) { this.totalUsdValue = totalUsdValue; }
}
