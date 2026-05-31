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
  LIVE: { color: "text-profit", bg: "bg-profit/10", border: "border-profit/20", label: "Sistema Activo" },
  DEGRADED: { color: "text-warn", bg: "bg-warn/10", border: "border-warn/20", label: "Degradado" },
  DEAD: { color: "text-loss", bg: "bg-loss/10", border: "border-loss/20", label: "Sistema Inactivo" },
} as const;

export function NexusHeader({ exchangesConnected, exchangesTotal, uptimeMs, pnl, health }: Props) {
  const h = healthMap[health];
  const { theme, toggle } = useTheme();
  return (
    <header className="flex flex-col gap-4 border-b nx-hairline bg-surface p-4 text-xs sm:flex-row sm:items-center sm:justify-between lg:px-6">
      <div className="flex flex-wrap items-center gap-6">
        <div className="flex items-center gap-3">
          <h1 className="text-sm font-bold tracking-tight text-foreground">
            NOBA<span className="font-light opacity-50">TRADE</span>
          </h1>
          <span className="hidden text-[10px] font-medium tracking-[0.25em] text-foreground/40 sm:inline-block">
            ARBITRAJE INSTITUCIONAL // V4.2
          </span>
        </div>
        <div className="flex gap-6 font-mono text-[9px] uppercase tracking-widest text-foreground/50">
          <div className="flex flex-col gap-1">
            <span>Exchanges Conectados</span>
            <span className="text-[11px] font-bold text-foreground flex items-center gap-2">
              {exchangesConnected} / {exchangesTotal}
              {exchangesConnected > 0 && <span className="h-1 w-1 bg-profit rounded-full animate-pulse" />}
            </span>
          </div>
          <div className="flex flex-col gap-1">
            <span>Uptime</span>
            <span className="text-[11px] font-bold text-foreground">
              {formatUptime(uptimeMs)}
            </span>
          </div>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-6">
        <Metric label="P&L del Motor">
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