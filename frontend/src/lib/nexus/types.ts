export interface OrderBookData {
  exchange: string;
  symbol: string;
  bestBid: string | null;
  bestBidVolume: string | null;
  bestAsk: string | null;
  bestAskVolume: string | null;
  stalenessMs: number;
  stale: boolean;
}

export interface EngineStats {
  totalEvaluations: number;
  totalOpportunities: number;
  totalExecuted: number;
  totalRejected: number;
  circuitBreakerActive: boolean;
  circuitBreakerPauseMs: number;
  wallets: Record<string, { totalUsd: number; btcBalance: number }>;
}

export type TradeStatus = "EXECUTED" | "REJECTED_FEES" | "REJECTED_CIRCUIT_BREAKER";

export interface DirectTradeEvent {
  id: string;
  timestampMs: number;
  symbol: string;
  buyExchange: string;
  sellExchange: string;
  volume: number;
  grossSpread: number;
  netProfit: number;
  status: TradeStatus;
}

export interface TriangularTradeEvent {
  id: string;
  timestampMs: number;
  exchange: string;
  route: string;
  volume: number;
  netProfit: number;
  status: TradeStatus;
}

export const EXCHANGES = ["binance", "kraken", "coinbase"] as const;
export type Exchange = (typeof EXCHANGES)[number];

export const INITIAL_WALLETS: Record<string, { totalUsd: number; btcBalance: number }> = {
  binance: { totalUsd: 10000, btcBalance: 0.15 },
  kraken: { totalUsd: 10000, btcBalance: 0.15 },
  coinbase: { totalUsd: 10000, btcBalance: 0.15 },
};