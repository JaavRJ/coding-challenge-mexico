import { useEffect, useRef, useState } from "react";
import type { DirectTradeEvent, TradeStatus, TriangularTradeEvent } from "@/lib/nexus/types";
import { EXCHANGE_META } from "./exchange";
import { SectionTitle } from "./OrderBooks";

function fmtTime(ts: number) {
  const d = new Date(ts);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}.${String(d.getMilliseconds()).padStart(3, "0").slice(0, 3)}`;
}

function StatusBadge({ status }: { status: TradeStatus }) {
  const map: Record<TradeStatus, { c: string; label: string }> = {
    EXECUTED: { c: "border-profit/30 bg-profit/10 text-profit", label: "EXEC" },
    REJECTED_FEES: { c: "border-warn/30 bg-warn/5 text-warn", label: "REJ·FEES" },
    REJECTED_CIRCUIT_BREAKER: { c: "border-loss/30 bg-loss/5 text-loss", label: "REJ·BRK" },
  };
  const m = map[status];
  return (
    <span className={`border px-1.5 py-0.5 font-mono text-[9px] tracking-wider ${m.c}`}>
      {m.label}
    </span>
  );
}

function useFlash<T extends { id: string; status: TradeStatus }>(items: T[]) {
  const [flashIds, setFlashIds] = useState<Set<string>>(new Set());
  const seen = useRef<Set<string>>(new Set());
  useEffect(() => {
    const newOnes = items.filter((i) => !seen.current.has(i.id) && i.status === "EXECUTED");
    if (newOnes.length === 0) return;
    newOnes.forEach((n) => seen.current.add(n.id));
    setFlashIds((prev) => {
      const next = new Set(prev);
      newOnes.forEach((n) => next.add(n.id));
      return next;
    });
    const ids = newOnes.map((n) => n.id);
    const t = setTimeout(() => {
      setFlashIds((prev) => {
        const next = new Set(prev);
        ids.forEach((id) => next.delete(id));
        return next;
      });
    }, 1600);
    return () => clearTimeout(t);
  }, [items]);
  return flashIds;
}

export function DirectTradesTable({ trades }: { trades: DirectTradeEvent[] }) {
  const flash = useFlash(trades);
  return (
    <div className="nx-panel overflow-hidden">
      <Header title="DIRECT ARBITRAGE" count={trades.length} />
      <div className="max-h-[420px] overflow-y-auto">
        <table className="w-full text-xs">
          <thead className="sticky top-0 bg-[#0f0f10] backdrop-blur">
            <tr className="text-left font-mono text-[10px] uppercase tracking-widest text-foreground/40">
              <Th>Time</Th><Th>Pair</Th><Th>Buy</Th><Th>Sell</Th>
              <Th className="text-right">Vol</Th>
              <Th className="text-right">Profit</Th>
              <Th className="text-right">Status</Th>
            </tr>
          </thead>
          <tbody>
            {trades.length === 0 && (
              <tr><td colSpan={7} className="py-10 text-center font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/30">Waiting for engine signals…</td></tr>
            )}
            {trades.map((t) => (
              <tr key={t.id} className={`border-t nx-hairline ${flash.has(t.id) ? "nx-flash" : ""}`}>
                <Td className="nx-num text-foreground/40">{fmtTime(t.timestampMs)}</Td>
                <Td className="nx-num">{t.symbol}</Td>
                <Td className={EXCHANGE_META[t.buyExchange].color + " font-bold"}>
                  {EXCHANGE_META[t.buyExchange].name}
                </Td>
                <Td className={EXCHANGE_META[t.sellExchange].color + " font-bold"}>
                  {EXCHANGE_META[t.sellExchange].name}
                </Td>
                <Td className="nx-num text-right">{t.volume}</Td>
                <Td className={`nx-num text-right font-semibold ${t.netProfit >= 0 ? "text-profit" : "text-loss"}`}>
                  {t.netProfit >= 0 ? "+" : ""}${t.netProfit.toFixed(2)}
                </Td>
                <Td className="text-right"><StatusBadge status={t.status} /></Td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function TriangularTradesTable({ trades }: { trades: TriangularTradeEvent[] }) {
  const flash = useFlash(trades);
  return (
    <div className="nx-panel overflow-hidden">
      <Header title="TRIANGULAR ARBITRAGE" count={trades.length} />
      <div className="max-h-[420px] overflow-y-auto">
        <table className="w-full text-xs">
          <thead className="sticky top-0 bg-[#0f0f10] backdrop-blur">
            <tr className="text-left font-mono text-[10px] uppercase tracking-widest text-foreground/40">
              <Th>Time</Th><Th>Exch</Th><Th>Route</Th>
              <Th className="text-right">Vol</Th>
              <Th className="text-right">Profit</Th>
              <Th className="text-right">Status</Th>
            </tr>
          </thead>
          <tbody>
            {trades.length === 0 && (
              <tr><td colSpan={6} className="py-10 text-center font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/30">Scanning liquidity cycles…</td></tr>
            )}
            {trades.map((t) => (
              <tr key={t.id} className={`border-t nx-hairline ${flash.has(t.id) ? "nx-flash" : ""}`}>
                <Td className="nx-num text-foreground/40">{fmtTime(t.timestampMs)}</Td>
                <Td className={EXCHANGE_META[t.exchange].color + " font-bold"}>
                  {EXCHANGE_META[t.exchange].name}
                </Td>
                <Td className="nx-num">{t.route}</Td>
                <Td className="nx-num text-right">${t.volume}</Td>
                <Td className={`nx-num text-right font-semibold ${t.netProfit >= 0 ? "text-profit" : "text-loss"}`}>
                  {t.netProfit >= 0 ? "+" : ""}${t.netProfit.toFixed(2)}
                </Td>
                <Td className="text-right"><StatusBadge status={t.status} /></Td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Header({ title, count }: { title: string; count: number }) {
  return (
    <div className="flex items-center justify-between border-b nx-hairline bg-surface-2 px-4 py-2.5">
      <h3 className="text-[10px] font-bold tracking-[0.2em] text-foreground/60">{title}</h3>
      <span className="font-mono text-[9px] uppercase tracking-widest text-foreground/30">{count} events</span>
    </div>
  );
}
const Th = ({ children, className = "" }: { children: React.ReactNode; className?: string }) => (
  <th className={`px-3 py-2 font-medium ${className}`}>{children}</th>
);
const Td = ({ children, className = "" }: { children: React.ReactNode; className?: string }) => (
  <td className={`px-3 py-2 ${className}`}>{children}</td>
);

export function LiveHistory({
  direct,
  triangular,
}: {
  direct: DirectTradeEvent[];
  triangular: TriangularTradeEvent[];
}) {
  return (
    <section className="space-y-3">
      <SectionTitle title="Live Execution Stream" sub="SSE · newest first" />
      <div className="grid gap-4 xl:grid-cols-2">
        <DirectTradesTable trades={direct} />
        <TriangularTradesTable trades={triangular} />
      </div>
    </section>
  );
}