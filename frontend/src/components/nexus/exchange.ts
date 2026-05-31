export const EXCHANGE_META: Record<
  string,
  { name: string; color: string; bg: string; border: string; dot: string }
> = {
  binance: {
    name: "Binance",
    color: "text-binance",
    bg: "bg-binance/10",
    border: "border-binance/30",
    dot: "bg-binance",
  },
  kraken: {
    name: "Kraken",
    color: "text-kraken",
    bg: "bg-kraken/10",
    border: "border-kraken/30",
    dot: "bg-kraken",
  },
  coinbase: {
    name: "Coinbase",
    color: "text-coinbase",
    bg: "bg-coinbase/10",
    border: "border-coinbase/30",
    dot: "bg-coinbase",
  },
};