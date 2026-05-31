import type { OrderBookData } from "@/lib/nexus/types";
import { EXCHANGE_META } from "./exchange";

function latencyColor(ms: number) {
  if (ms < 100) return "text-profit";
  if (ms < 1000) return "text-warn";
  return "text-loss";
}

export function SectionTitle({ title, sub }: { title: string; sub?: string }) {
  return (
    <h2 className="text-[11px] font-bold uppercase tracking-[0.15em] text-foreground/60">
      {title}
      {sub && <span className="ml-2 text-foreground/20">/ {sub}</span>}
    </h2>
  );
}

export function OrderBooksGrid({ books }: { books: OrderBookData[] }) {
  const byExchange = books.reduce<Record<string, OrderBookData[]>>((acc, b) => {
    (acc[b.exchange] ??= []).push(b);
    return acc;
  }, {});

  return (
    <section className="space-y-3">
      <SectionTitle title="Market Depth" sub="Top-of-Book" />
      <div className="grid gap-4 lg:grid-cols-3">
        {Object.entries(byExchange).map(([ex, list]) => {
          const meta = EXCHANGE_META[ex];
          const primary = list[0];
          return (
            <div key={ex} className="nx-panel p-4">
              <div className="mb-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className={`h-2 w-2 rounded-full ${meta.dot}`} />
                  <span className="text-xs font-bold uppercase tracking-wider text-foreground">
                    {meta.name}
                  </span>
                </div>
                <span className={`nx-num text-[10px] ${latencyColor(primary.stalenessMs)}`}>
                  {primary.stalenessMs}ms
                </span>
              </div>
              <div className="space-y-4 font-mono">
                {list.slice(0, 1).map((b) => (
                  <div key={b.symbol}>
                    <div className="mb-1 flex items-center justify-between">
                      <span className="text-[10px] uppercase tracking-wider text-foreground/40">
                        {b.symbol}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-2">
                      <PriceCell type="BID" price={b.bestBid} vol={b.bestBidVolume} />
                      <PriceCell type="ASK" price={b.bestAsk} vol={b.bestAskVolume} />
                    </div>
                    <div className="mt-2 flex justify-between border-t nx-hairline pt-2 text-[10px]">
                      <span className="uppercase text-foreground/30">Net Spread</span>
                      <span className="nx-num text-foreground/80">
                        ${(Number(b.bestAsk) - Number(b.bestBid)).toFixed(
                          b.symbol === "ETH/BTC" ? 5 : 2,
                        )}
                      </span>
                    </div>
                  </div>
                ))}
                <div className="space-y-1.5 border-t nx-hairline pt-3">
                  {list.slice(1).map((b) => (
                    <div key={b.symbol} className="flex items-center justify-between text-[11px]">
                      <span className="text-[10px] uppercase tracking-wider text-foreground/40">
                        {b.symbol}
                      </span>
                      <div className="flex items-center gap-3 nx-num">
                        <span className="text-bid">{b.bestBid}</span>
                        <span className="text-foreground/20">/</span>
                        <span className="text-ask">{b.bestAsk}</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function PriceCell({ type, price, vol }: { type: "BID" | "ASK"; price: string | null; vol: string | null }) {
  const isBid = type === "BID";
  return (
    <div className={`p-2 border-l ${isBid ? "bg-bid/5 border-bid" : "bg-ask/5 border-ask"}`}>
      <div className={`text-[9px] uppercase mb-1 ${isBid ? "text-bid/60" : "text-ask/60"}`}>
        {isBid ? "Bid Price" : "Ask Price"}
      </div>
      <div className={`text-lg leading-none font-bold ${isBid ? "text-bid" : "text-ask"}`}>
        {price ?? "—"}
      </div>
      <div className="mt-1 text-[9px] uppercase text-foreground/30">v: {vol ?? "—"}</div>
    </div>
  );
}