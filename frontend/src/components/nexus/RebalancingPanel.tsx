"use client";
import { useState, useEffect, useCallback } from "react";
import { Scale, RefreshCw, AlertTriangle, CheckCircle, ArrowRightLeft, Clock } from "lucide-react";

interface WalletBalance {
  usdt: number;
  btc: number;
  usdtValue: number;
  pctOfTotal: number;
}

interface RebalancingEvent {
  timestampMs: number;
  trigger: "AUTO" | "MANUAL";
  exchangeFrom: string;
  exchangeTo: string;
  btcAmount: number;
  usdtEquivalent: number;
  asymmetryPctBefore: number;
  asymmetryPctAfter: number;
}

interface RebalancingStatus {
  balanced: boolean;
  maxAsymmetryPct: number;
  thresholdPct: number;
  walletBalances: Record<string, WalletBalance>;
  history: RebalancingEvent[];
  totalRebalances: number;
}

interface Props {
  apiBase: string;
}

const fmtTime = (ms: number) => {
  const d = new Date(ms);
  return d.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
};

const fmtUsd = (n: number) => `$${n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const EXCHANGE_COLORS: Record<string, { bg: string; text: string; bar: string }> = {
  BINANCE:  { bg: "bg-yellow-500/10",  text: "text-yellow-400",  bar: "bg-yellow-400" },
  KRAKEN:   { bg: "bg-purple-500/10",  text: "text-purple-400",  bar: "bg-purple-400" },
  COINBASE: { bg: "bg-blue-500/10",    text: "text-blue-400",    bar: "bg-blue-400"   },
};

export function RebalancingPanel({ apiBase }: Props) {
  const [status, setStatus] = useState<RebalancingStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [forcing, setForcing] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [showHistory, setShowHistory] = useState(false);

  const fetchStatus = useCallback(async () => {
    try {
      const res = await fetch(`${apiBase}/api/rebalancing`);
      if (res.ok) {
        setStatus(await res.json());
        setLastUpdate(new Date());
      }
    } catch {}
    finally { setLoading(false); }
  }, [apiBase]);

  useEffect(() => {
    fetchStatus();
    const iv = setInterval(fetchStatus, 6000);
    return () => clearInterval(iv);
  }, [fetchStatus]);

  const handleForceRebalance = async () => {
    setForcing(true);
    try {
      const res = await fetch(`${apiBase}/api/rebalancing/force`, { method: "POST" });
      if (res.ok) setStatus(await res.json());
    } catch {}
    finally { setForcing(false); }
  };

  if (loading) return (
    <div className="nx-panel p-5 flex items-center gap-2 text-foreground/30 text-[11px] font-mono">
      <span className="animate-pulse">Analizando balances…</span>
    </div>
  );

  const entries = Object.entries(status?.walletBalances ?? {});
  const totalUsdtValue = entries.reduce((s, [, w]) => s + (w.usdtValue ?? 0), 0);

  return (
    <div className="nx-panel p-5 space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-foreground/10 pb-3">
        <div className="flex items-center gap-2">
          <Scale size={14} className="text-blue-400" />
          <span className="text-[11px] font-bold uppercase tracking-[0.15em] text-foreground/70">
            Gestor de Rebalanceo
          </span>
          {status && (
            <span className={`text-[8px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
              status.balanced
                ? "bg-profit/15 text-profit border border-profit/30"
                : "bg-loss/15 text-loss border border-loss/30"
            }`}>
              {status.balanced ? "✓ Balanceado" : "⚠ Desbalanceado"}
            </span>
          )}
        </div>
        {lastUpdate && (
          <div className="flex items-center gap-1 text-[8px] text-foreground/20 font-mono">
            <Clock size={8} />
            {lastUpdate.toLocaleTimeString("es-MX")}
          </div>
        )}
      </div>

      {status && (
        <>
          {/* Asimetría Global */}
          <div className="grid grid-cols-3 gap-3">
            <div className="bg-surface-2 border border-foreground/10 p-3 rounded-sm space-y-1">
              <div className="text-[8px] uppercase tracking-wider text-foreground/40">Asimetría Máx.</div>
              <div className={`text-lg font-mono font-bold ${status.maxAsymmetryPct > status.thresholdPct ? "text-loss" : "text-profit"}`}>
                {status.maxAsymmetryPct.toFixed(1)}%
              </div>
            </div>
            <div className="bg-surface-2 border border-foreground/10 p-3 rounded-sm space-y-1">
              <div className="text-[8px] uppercase tracking-wider text-foreground/40">Umbral Config.</div>
              <div className="text-lg font-mono font-bold text-foreground/60">
                {status.thresholdPct.toFixed(0)}%
              </div>
            </div>
            <div className="bg-surface-2 border border-foreground/10 p-3 rounded-sm space-y-1">
              <div className="text-[8px] uppercase tracking-wider text-foreground/40">Rebalanceos</div>
              <div className="text-lg font-mono font-bold text-blue-400">
                {status.totalRebalances}
              </div>
            </div>
          </div>

          {/* Alerta si desbalanceado */}
          {!status.balanced && (
            <div className="flex items-center gap-2 px-3 py-2 bg-loss/8 border border-loss/20 rounded-sm text-[10px] text-loss">
              <AlertTriangle size={11} />
              Asimetría del {status.maxAsymmetryPct.toFixed(1)}% detectada — supera el umbral de {status.thresholdPct}%. El rebalanceo automático se activará en el próximo ciclo.
            </div>
          )}
          {status.balanced && status.totalRebalances > 0 && (
            <div className="flex items-center gap-2 px-3 py-2 bg-profit/8 border border-profit/20 rounded-sm text-[10px] text-profit">
              <CheckCircle size={11} />
              Todos los exchanges operan dentro del rango óptimo.
            </div>
          )}

          {/* Wallet Breakdown */}
          <div className="space-y-3">
            <div className="text-[9px] uppercase tracking-[0.15em] text-foreground/30">Balance por Exchange</div>
            {entries.map(([exchange, wallet]) => {
              const colors = EXCHANGE_COLORS[exchange] ?? { bg: "bg-foreground/5", text: "text-foreground/60", bar: "bg-foreground/30" };
              const pct = totalUsdtValue > 0 ? ((wallet.usdtValue ?? 0) / totalUsdtValue * 100) : 0;
              return (
                <div key={exchange} className={`${colors.bg} border border-foreground/10 p-3 rounded-sm space-y-2`}>
                  <div className="flex items-center justify-between">
                    <span className={`text-[10px] font-bold ${colors.text}`}>{exchange}</span>
                    <span className="text-[10px] font-mono font-bold text-foreground">{fmtUsd(wallet.usdtValue ?? 0)}</span>
                  </div>
                  <div className="grid grid-cols-2 gap-4 text-[9px] font-mono text-foreground/50">
                    <span>USDT: <span className="text-foreground/80">{fmtUsd(wallet.usdt ?? 0)}</span></span>
                    <span>BTC: <span className="text-foreground/80">{(wallet.btc ?? 0).toFixed(6)}</span></span>
                  </div>
                  {/* Progress bar */}
                  <div className="h-1 w-full bg-foreground/10 rounded-full overflow-hidden">
                    <div
                      className={`h-full ${colors.bar} transition-all duration-500`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                  <div className="text-[8px] text-foreground/30 text-right">{pct.toFixed(1)}% del total</div>
                </div>
              );
            })}
          </div>

          {/* Acción Manual */}
          <button
            onClick={handleForceRebalance}
            disabled={forcing}
            className="w-full flex items-center justify-center gap-2 py-3 text-[10px] font-bold uppercase tracking-[0.15em] transition border rounded-sm border-blue-500/30 text-blue-300 bg-blue-500/10 hover:bg-blue-500/20 cursor-pointer"
          >
            {forcing ? (
              <><RefreshCw size={11} className="animate-spin" /> Rebalanceando…</>
            ) : (
              <><ArrowRightLeft size={11} /> Forzar Rebalanceo Manual</>
            )}
          </button>

          {/* Historial */}
          {status.history.length > 0 && (
            <div className="space-y-2">
              <button
                onClick={() => setShowHistory(!showHistory)}
                className="text-[9px] text-foreground/30 hover:text-foreground/60 uppercase tracking-wider transition"
              >
                {showHistory ? "▲ Ocultar" : "▼ Ver"} historial ({status.history.length} eventos)
              </button>
              {showHistory && (
                <div className="max-h-48 overflow-y-auto space-y-1">
                  {status.history.slice(0, 20).map((ev, i) => (
                    <div key={i} className="flex items-center gap-2 text-[9px] font-mono border border-foreground/8 px-3 py-2 rounded-sm">
                      <span className={`px-1.5 py-0.5 rounded text-[8px] font-bold ${ev.trigger === "AUTO" ? "bg-blue-500/15 text-blue-400" : "bg-purple-500/15 text-purple-400"}`}>
                        {ev.trigger}
                      </span>
                      <span className="text-foreground/30">{fmtTime(ev.timestampMs)}</span>
                      <ArrowRightLeft size={8} className="text-foreground/20" />
                      <span className="text-foreground/60">
                        {ev.exchangeFrom} → {ev.exchangeTo}
                      </span>
                      <span className="text-foreground/40 ml-auto">
                        {ev.btcAmount?.toFixed(6)} BTC
                      </span>
                      <span className={`${ev.asymmetryPctAfter < ev.asymmetryPctBefore ? "text-profit" : "text-loss"}`}>
                        {ev.asymmetryPctBefore?.toFixed(1)}% → {ev.asymmetryPctAfter?.toFixed(1)}%
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      )}

      {!status && !loading && (
        <div className="text-center text-foreground/30 text-[11px] font-mono py-6">
          Backend no disponible
        </div>
      )}
    </div>
  );
}
