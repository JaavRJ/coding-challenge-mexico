import { useEffect, useRef, useState } from "react";
import {
  type DirectTradeEvent,
  type EngineStats,
  type OrderBookData,
  type TriangularTradeEvent,
  EXCHANGES,
  INITIAL_WALLETS,
} from "./types";

const SYMBOLS = ["BTC/USDT", "ETH/USDT", "ETH/BTC"];

function rand(min: number, max: number) {
  return Math.random() * (max - min) + min;
}

function fmt(n: number, d = 2) {
  return n.toFixed(d);
}

interface EngineState {
  startedAt: number;
  orderbooks: OrderBookData[];
  engine: EngineStats;
  directTrades: DirectTradeEvent[];
  triTrades: TriangularTradeEvent[];
  minProfitUsd: number;
  exchangesEnabled: Record<string, boolean>;
  health: "LIVE" | "DEGRADED" | "DEAD";
  pnl: number;
}

/** Deterministic seed used for SSR + first client render to avoid hydration mismatch. */
function seedOrderbooksDeterministic(): OrderBookData[] {
  const books: OrderBookData[] = [];
  const basePrices: Record<string, number> = {
    "BTC/USDT": 67000,
    "ETH/USDT": 3500,
    "ETH/BTC": 0.0522,
  };
  // Fixed offsets per exchange so SSR === first client render
  const exOffsets: Record<string, number> = { binance: 0, kraken: 0.0006, coinbase: -0.0004 };
  EXCHANGES.forEach((ex, ei) => {
    SYMBOLS.forEach((sym, si) => {
      const base = basePrices[sym] * (1 + exOffsets[ex]);
      const spread = base * 0.0004;
      const decimals = sym === "ETH/BTC" ? 5 : 2;
      books.push({
        exchange: ex,
        symbol: sym,
        bestBid: fmt(base - spread / 2, decimals),
        bestBidVolume: fmt(0.5 + ei * 0.3 + si * 0.2, 4),
        bestAsk: fmt(base + spread / 2, decimals),
        bestAskVolume: fmt(0.7 + ei * 0.25 + si * 0.15, 4),
        stalenessMs: 60 + ei * 30 + si * 10,
        stale: false,
      });
    });
  });
  return books;
}

export function useMockEngine() {
  const [state, setState] = useState<EngineState>(() => ({
    startedAt: 0,
    orderbooks: seedOrderbooksDeterministic(),
    engine: {
      totalEvaluations: 0,
      totalOpportunities: 0,
      totalExecuted: 0,
      totalRejected: 0,
      circuitBreakerActive: true,
      circuitBreakerPauseMs: 0,
      wallets: structuredClone(INITIAL_WALLETS),
    },
    directTrades: [],
    triTrades: [],
    minProfitUsd: 5,
    exchangesEnabled: { binance: true, kraken: true, coinbase: true },
    health: "LIVE",
    pnl: 0,
  }));

  const stateRef = useRef(state);
  stateRef.current = state;

  // Initialize clock on client only (avoid SSR/CSR mismatch on Date.now())
  useEffect(() => {
    setState((s) => (s.startedAt === 0 ? { ...s, startedAt: Date.now() } : s));
  }, []);

  // Tick: update orderbooks & engine stats every 1s
  useEffect(() => {
    const id = setInterval(() => {
      setState((s) => {
        const breakerActive = s.engine.circuitBreakerPauseMs <= 0;
        const newBooks = s.orderbooks.map((b) => {
          const enabled = s.exchangesEnabled[b.exchange];
          const base = Number(b.bestBid);
          const drift = base * rand(-0.0008, 0.0008);
          const spread = base * rand(0.0001, 0.0006);
          const next = base + drift;
          const decimals = b.symbol === "ETH/BTC" ? 5 : 2;
          const staleness = enabled ? Math.floor(rand(20, 220)) : Math.floor(rand(800, 2500));
          return {
            ...b,
            bestBid: fmt(next - spread / 2, decimals),
            bestBidVolume: fmt(rand(0.05, 3), 4),
            bestAsk: fmt(next + spread / 2, decimals),
            bestAskVolume: fmt(rand(0.05, 3), 4),
            stalenessMs: staleness,
            stale: staleness > 1500,
          };
        });
        const newPause = Math.max(0, s.engine.circuitBreakerPauseMs - 1000);
        const evals = s.engine.totalEvaluations + Math.floor(rand(80, 220));
        const opps = s.engine.totalOpportunities + Math.floor(rand(0, 4));
        return {
          ...s,
          orderbooks: newBooks,
          engine: {
            ...s.engine,
            totalEvaluations: evals,
            totalOpportunities: opps,
            circuitBreakerActive: newPause <= 0 && breakerActive,
            circuitBreakerPauseMs: newPause,
          },
          health:
            Object.values(s.exchangesEnabled).filter(Boolean).length === 3
              ? newPause > 0
                ? "DEGRADED"
                : "LIVE"
              : Object.values(s.exchangesEnabled).some(Boolean)
                ? "DEGRADED"
                : "DEAD",
        };
      });
    }, 1000);
    return () => clearInterval(id);
  }, []);

  // Trade event stream (SSE-like) every ~1.4s
  useEffect(() => {
    const id = setInterval(() => {
      setState((s) => {
        if (s.engine.circuitBreakerPauseMs > 0) return s;
        const enabled = Object.entries(s.exchangesEnabled).filter(([, v]) => v).map(([k]) => k);
        if (enabled.length < 2) return s;
        const isTri = Math.random() < 0.35;
        const now = Date.now();
        if (isTri) {
          const ex = enabled[Math.floor(Math.random() * enabled.length)];
          const profit = rand(-2, 14);
          const status = profit < s.minProfitUsd
            ? Math.random() < 0.5
              ? "REJECTED_FEES"
              : "REJECTED_CIRCUIT_BREAKER"
            : "EXECUTED";
          const trade: TriangularTradeEvent = {
            id: `t-${now}-${Math.random().toString(36).slice(2, 7)}`,
            timestampMs: now,
            exchange: ex,
            route: "USDT → BTC → ETH → USDT",
            volume: Number(fmt(rand(100, 800), 2)),
            netProfit: Number(fmt(profit, 2)),
            status,
          };
          return applyTrade(s, undefined, trade);
        }
        const a = enabled[Math.floor(Math.random() * enabled.length)];
        let b = enabled[Math.floor(Math.random() * enabled.length)];
        while (b === a) b = enabled[Math.floor(Math.random() * enabled.length)];
        const profit = rand(-3, 22);
        const status = profit < s.minProfitUsd
          ? Math.random() < 0.5
            ? "REJECTED_FEES"
            : "REJECTED_CIRCUIT_BREAKER"
          : "EXECUTED";
        const trade: DirectTradeEvent = {
          id: `d-${now}-${Math.random().toString(36).slice(2, 7)}`,
          timestampMs: now,
          symbol: SYMBOLS[Math.floor(Math.random() * SYMBOLS.length)],
          buyExchange: a,
          sellExchange: b,
          volume: Number(fmt(rand(0.01, 0.5), 4)),
          grossSpread: Number(fmt(profit + rand(1, 4), 2)),
          netProfit: Number(fmt(profit, 2)),
          status,
        };
        return applyTrade(s, trade, undefined);
      });
    }, 1400);
    return () => clearInterval(id);
  }, []);

  function applyTrade(
    s: EngineState,
    d?: DirectTradeEvent,
    t?: TriangularTradeEvent,
  ): EngineState {
    const trade = d ?? t!;
    const executed = trade.status === "EXECUTED";
    const wallets = { ...s.engine.wallets };
    if (executed && d) {
      wallets[d.buyExchange] = {
        ...wallets[d.buyExchange],
        totalUsd: wallets[d.buyExchange].totalUsd + trade.netProfit / 2,
      };
      wallets[d.sellExchange] = {
        ...wallets[d.sellExchange],
        totalUsd: wallets[d.sellExchange].totalUsd + trade.netProfit / 2,
      };
    } else if (executed && t) {
      wallets[t.exchange] = {
        ...wallets[t.exchange],
        totalUsd: wallets[t.exchange].totalUsd + trade.netProfit,
      };
    }
    return {
      ...s,
      pnl: executed ? s.pnl + trade.netProfit : s.pnl,
      engine: {
        ...s.engine,
        totalOpportunities: s.engine.totalOpportunities + 1,
        totalExecuted: s.engine.totalExecuted + (executed ? 1 : 0),
        totalRejected: s.engine.totalRejected + (executed ? 0 : 1),
        wallets,
      },
      directTrades: d ? [d, ...s.directTrades].slice(0, 40) : s.directTrades,
      triTrades: t ? [t, ...s.triTrades].slice(0, 40) : s.triTrades,
    };
  }

  function setMinProfit(v: number) {
    setState((s) => ({ ...s, minProfitUsd: v }));
  }
  function toggleExchange(ex: string, value: boolean) {
    setState((s) => ({ ...s, exchangesEnabled: { ...s.exchangesEnabled, [ex]: value } }));
  }
  function triggerFlashCrash() {
    setState((s) => ({
      ...s,
      engine: { ...s.engine, circuitBreakerActive: false, circuitBreakerPauseMs: 60000 },
    }));
  }

  return { state, setMinProfit, toggleExchange, triggerFlashCrash };
}