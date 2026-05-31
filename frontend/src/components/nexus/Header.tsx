import { Moon, Sun } from "lucide-react";
import { useTheme } from "@/hooks/use-theme";

interface Props {
  exchangesConnected: number;
  exchangesTotal: number;
  uptimeMs: number;
  pnl: number;
  health: "LIVE" | "DEGRADED" | "DEAD";
}

function formatUptime(ms: number) {
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const r = s % 60;
  if (h) return `${h}h ${m}m`;
  if (m) return `${m}m ${r}s`;
  return `${r}s`;
}

const healthMap = {
  LIVE: { color: "text-profit", bg: "bg-profit/10", border: "border-profit/20", label: "System Live" },
  DEGRADED: { color: "text-warn", bg: "bg-warn/10", border: "border-warn/20", label: "Degraded" },
  DEAD: { color: "text-loss", bg: "bg-loss/10", border: "border-loss/20", label: "System Dead" },
} as const;

export function NexusHeader({ exchangesConnected, exchangesTotal, uptimeMs, pnl, health }: Props) {
  const h = healthMap[health];
  const { theme, toggle } = useTheme();
  return (
    <header className="flex flex-wrap items-center justify-between gap-4 border-b nx-hairline pb-4">
      <div className="flex items-baseline gap-4">
        <h1 className="text-xl font-bold tracking-tighter text-foreground">
          NEXUS<span className="font-light opacity-50">TRADE</span>
        </h1>
        <div className="hidden h-4 w-px bg-foreground/20 sm:block" />
        <p className="text-[10px] font-medium uppercase tracking-[0.2em] text-foreground/40">
          Arbitrage Engine // Institutional v4.2
        </p>
      </div>
      <div className="flex flex-wrap items-center gap-6">
        <Metric label="Exchanges Connected">
          <span className="nx-num text-foreground">
            {exchangesConnected} / {exchangesTotal}
          </span>
          <span className="ml-1 text-profit">●</span>
        </Metric>
        <Metric label="Uptime">
          <span className="nx-num text-foreground">{formatUptime(uptimeMs)}</span>
        </Metric>
        <Metric label="Engine P&L">
          <span className={`nx-num font-bold ${pnl >= 0 ? "text-profit" : "text-loss"}`}>
            {pnl >= 0 ? "+" : ""}${pnl.toFixed(2)}
          </span>
        </Metric>
        <div className={`px-3 py-1 ${h.bg} border ${h.border} ${h.color} text-[10px] font-bold uppercase tracking-widest`}>
          {h.label}
        </div>
        <button
          onClick={() => window.location.reload()}
          className="flex h-9 items-center gap-2 border border-foreground/25 bg-surface px-3 text-[10px] font-bold uppercase tracking-widest text-foreground transition hover:bg-foreground hover:text-background"
        >
          Exit
        </button>
      </div>
    </header>
  );
}

function Metric({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col items-end">
      <span className="text-[10px] uppercase tracking-widest text-foreground/30">{label}</span>
      <span className="text-xs font-medium">{children}</span>
    </div>
  );
}