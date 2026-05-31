import type { OrderBookData } from "@/lib/nexus/types";
import { EXCHANGE_META } from "./exchange";
import { SectionTitle } from "./OrderBooks";

export function ArbitrageMatrix({
  books,
  symbol = "BTC/USDT",
}: {
  books: OrderBookData[];
  symbol?: string;
}) {
  const filtered = books.filter((b) => b.symbol === symbol);

  return (
    <section className="space-y-3">
      <SectionTitle title="Arbitrage Opportunity Matrix" sub={symbol} />
      <div className="nx-panel overflow-hidden">
        <table className="w-full text-left font-mono text-[10px]">
          <thead className="bg-surface-2 uppercase tracking-wider text-foreground/40">
            <tr>
              <th className="border-b nx-hairline p-3">Source \ Dest</th>
              {filtered.map((b) => (
                <th key={b.exchange} className="border-b nx-hairline p-3">
                  {EXCHANGE_META[b.exchange].name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-[oklch(1_0_0_/_0.06)]">
            {filtered.map((buyRow) => (
              <tr key={buyRow.exchange}>
                <td className="p-3 font-bold text-foreground/40">
                  {EXCHANGE_META[buyRow.exchange].name}
                </td>
                {filtered.map((sellRow) => {
                  if (buyRow.exchange === sellRow.exchange)
                    return (
                      <td key={sellRow.exchange} className="p-3 text-foreground/10">
                        —
                      </td>
                    );
                  const buy = Number(buyRow.bestAsk);
                  const sell = Number(sellRow.bestBid);
                  const diff = sell - buy;
                  const pct = (diff / buy) * 100;
                  const pos = diff > 0;
                  return (
                    <td key={sellRow.exchange} className="p-3">
                      <div className={`font-bold ${pos ? "text-profit" : "text-loss"}`}>
                        {pos ? "+" : ""}
                        {diff.toFixed(2)}
                      </div>
                      <div className="text-[9px] uppercase opacity-40">
                        {pos ? "+" : ""}
                        {pct.toFixed(3)}%
                      </div>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}