"use client";
import { useState, useEffect, useRef } from "react";
import { X, Settings, Scale, SlidersHorizontal, ChevronDown, ChevronUp, Save, RotateCcw, Zap, RefreshCw, AlertTriangle, CheckCircle, ArrowRightLeft, Clock } from "lucide-react";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

interface WalletBalance { usdt: number; btc: number; usdtValue: number; pctOfTotal: number }
interface RebalancingEvent {
  timestampMs: number; trigger: "AUTO" | "MANUAL";
  exchangeFrom: string; exchangeTo: string;
  btcAmount: number; usdtEquivalent: number;
  asymmetryPctBefore: number; asymmetryPctAfter: number;
}
interface RebalancingStatus {
  balanced: boolean; maxAsymmetryPct: number; thresholdPct: number;
  walletBalances: Record<string, WalletBalance>;
  history: RebalancingEvent[]; totalRebalances: number;
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

const fmtTime = (ms: number) =>
  new Date(ms).toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
const fmtUsd = (n: number) =>
  `$${n.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

const EXCHANGE_COLORS: Record<string, { bg: string; text: string; bar: string }> = {
  BINANCE:  { bg: "bg-yellow-500/10",  text: "text-yellow-400",  bar: "bg-yellow-400" },
  KRAKEN:   { bg: "bg-purple-500/10",  text: "text-purple-400",  bar: "bg-purple-400" },
  COINBASE: { bg: "bg-blue-500/10",    text: "text-blue-400",    bar: "bg-blue-400"   },
};

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

function Accordion({ title, defaultOpen = false, children }: { title: string; defaultOpen?: boolean; children: React.ReactNode }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border border-white/8 rounded-sm overflow-hidden">
      <button onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-4 py-3 text-[10px] font-bold uppercase tracking-[0.15em] text-white/50 hover:text-white/80 transition-colors bg-white/2 hover:bg-white/4">
        {title}
        {open ? <ChevronUp size={11} /> : <ChevronDown size={11} />}
      </button>
      {open && (
        <div className="px-4 pb-4 pt-3 space-y-4 border-t border-white/8 bg-black/10">
          {children}
        </div>
      )}
    </div>
  );
}

function Slider({ label, value, min, max, step, unit, onChange, description }: {
  label: string; value: number; min: number; max: number; step: number;
  unit: string; onChange: (v: number) => void; description?: string;
}) {
  return (
    <div className="space-y-1.5">
      <div className="flex justify-between text-[10px]">
        <span className="text-white/40 uppercase tracking-wider">{label}</span>
        <span className="text-profit font-mono font-bold">{value.toFixed(step < 1 ? 3 : 0)}{unit}</span>
      </div>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={e => onChange(Number(e.target.value))}
        className="h-0.5 w-full cursor-pointer appearance-none rounded-full bg-white/10 accent-profit" />
      {description && <p className="text-[9px] text-white/25 leading-relaxed">{description}</p>}
    </div>
  );
}

function NumInput({ label, value, min, max, step, unit, onChange, description }: {
  label: string; value: number; min: number; max: number; step: number;
  unit: string; onChange: (v: number) => void; description?: string;
}) {
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between gap-3">
        <span className="text-[10px] text-white/40 uppercase tracking-wider flex-1">{label}</span>
        <div className="flex items-center gap-1.5">
          <input type="number" min={min} max={max} step={step} value={value}
            onChange={e => onChange(Number(e.target.value))}
            className="w-20 bg-white/5 border border-white/10 px-2 py-1 text-[10px] font-mono text-white text-right rounded-sm focus:outline-none focus:border-profit/50 transition-colors" />
          <span className="text-[9px] text-white/30 w-8">{unit}</span>
        </div>
      </div>
      {description && <p className="text-[9px] text-white/25 leading-relaxed">{description}</p>}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Config Panel
// ─────────────────────────────────────────────────────────────────────────────

function ConfigContent({ apiBase }: { apiBase: string }) {
  const [config, setConfig] = useState<any>(null);
  const [draft, setDraft] = useState<any>({});
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    fetch(`${apiBase}/api/config`).then(r => r.ok ? r.json() : null).then(d => {
      if (!d) return;
      setConfig(d);
      setDraft({
        walletExposurePct: d.walletExposurePct ?? 100,
        minRoiPct: (d.minRoiPct ?? 0.01) * 100,
        decisionTimeoutMs: d.decisionTimeoutMs ?? 200,
        circuitBreakerLosses: d.circuitBreakerLosses ?? d.risk?.circuitBreakerLosses ?? 3,
        circuitBreakerPauseSeconds: d.circuitBreakerPauseSeconds ?? d.risk?.circuitBreakerPauseSeconds ?? 60,
        maxBalanceDrawdownPct: d.maxBalanceDrawdownPct ?? d.risk?.maxBalanceDrawdownPct ?? 2.0,
        rebalanceThresholdPct: d.rebalanceThresholdPct ?? d.risk?.rebalanceThresholdPct ?? 40.0,
        fees: d.fees ?? {
          binance: { feeTaker: 0.001, feeMaker: 0.001, withdrawalFeeBtc: 0.0005 },
          kraken:  { feeTaker: 0.0026, feeMaker: 0.0016, withdrawalFeeBtc: 0.0002 },
          coinbase:{ feeTaker: 0.006, feeMaker: 0.004, withdrawalFeeBtc: 0.0 },
        }
      });
    }).catch(() => {});
  }, [apiBase]);

  const set = (k: string, v: any) => setDraft((p: any) => ({ ...p, [k]: v }));
  const setFee = (ex: string, k: string, v: number) => setDraft((p: any) => ({
    ...p, fees: { ...p.fees, [ex]: { ...(p.fees?.[ex] ?? {}), [k]: v } }
  }));

  const handleSave = async () => {
    setSaving(true);
    try {
      const res = await fetch(`${apiBase}/api/config`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          walletExposurePct: draft.walletExposurePct,
          minRoiPct: draft.minRoiPct / 100,
          decisionTimeoutMs: draft.decisionTimeoutMs,
          circuitBreakerLosses: draft.circuitBreakerLosses,
          circuitBreakerPauseSeconds: draft.circuitBreakerPauseSeconds,
          maxBalanceDrawdownPct: draft.maxBalanceDrawdownPct,
          rebalanceThresholdPct: draft.rebalanceThresholdPct,
          feeOverrides: draft.fees,
        }),
      });
      if (res.ok) { setConfig(await res.json()); setSaved(true); setTimeout(() => setSaved(false), 2000); }
    } catch {}
    finally { setSaving(false); }
  };

  if (!config) return (
    <div className="flex items-center justify-center h-32 text-white/30 text-[11px] font-mono animate-pulse">
      Cargando configuración…
    </div>
  );

  const EXCHANGES_FEE = ["binance", "kraken", "coinbase"];

  return (
    <div className="space-y-3">
      {/* Save bar */}
      <div className="flex items-center gap-2 pb-1">
        <button onClick={() => setDraft({
          walletExposurePct: config.walletExposurePct ?? 100,
          minRoiPct: (config.minRoiPct ?? 0.01) * 100,
          decisionTimeoutMs: config.decisionTimeoutMs ?? 200,
          circuitBreakerLosses: config.risk?.circuitBreakerLosses ?? 3,
          circuitBreakerPauseSeconds: config.risk?.circuitBreakerPauseSeconds ?? 60,
          maxBalanceDrawdownPct: config.risk?.maxBalanceDrawdownPct ?? 2.0,
          rebalanceThresholdPct: config.risk?.rebalanceThresholdPct ?? 40.0,
          fees: config.fees,
        })}
          className="flex items-center gap-1 px-2 py-1.5 text-[9px] uppercase tracking-wider border border-white/10 text-white/30 hover:text-white/60 transition rounded-sm">
          <RotateCcw size={9} /> Reset
        </button>
        <button onClick={handleSave} disabled={saving}
          className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 text-[9px] font-bold uppercase tracking-wider transition rounded-sm ${
            saved ? "bg-profit/20 text-profit border border-profit/40"
                  : "bg-blue-500/15 text-blue-300 border border-blue-500/30 hover:bg-blue-500/25"
          }`}>
          {saving ? <RefreshCw size={9} className="animate-spin" /> : <Save size={9} />}
          {saved ? "✓ Cambios Aplicados" : "Aplicar al Motor"}
        </button>
      </div>

      {/* Presets Rápidos */}
      <div className="bg-white/3 border border-white/8 p-2.5 rounded-sm space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-[9px] font-bold uppercase tracking-widest text-white/60 flex items-center gap-1">
            <Zap size={10} className="text-amber-400" /> Presets de Operación (1-Clic)
          </span>
          <span className="text-[8px] text-white/30 font-mono">Modo Institucional</span>
        </div>
        <div className="grid grid-cols-2 gap-1.5">
          {[
            { id: "cons", label: "🛡️ Conservador", desc: "ROI 0.8% | Drawdown 1%", exposure: 25, roi: 0.8, timeout: 500, losses: 2, pause: 120, dd: 1.0, reb: 20, color: "hover:border-emerald-500/40 hover:bg-emerald-500/10 text-emerald-400" },
            { id: "bal", label: "⚖️ Balanceado", desc: "ROI 0.3% | Drawdown 3%", exposure: 50, roi: 0.3, timeout: 200, losses: 3, pause: 60, dd: 3.0, reb: 35, color: "hover:border-blue-500/40 hover:bg-blue-500/10 text-blue-400" },
            { id: "agr", label: "🚀 Agresivo", desc: "ROI 0.1% | Drawdown 5%", exposure: 100, roi: 0.1, timeout: 150, losses: 5, pause: 30, dd: 5.0, reb: 50, color: "hover:border-amber-500/40 hover:bg-amber-500/10 text-amber-400" },
            { id: "hft", label: "⚡ HFT / Scalping", desc: "ROI 0.05% | Latencia 50ms", exposure: 100, roi: 0.05, timeout: 50, losses: 10, pause: 10, dd: 10.0, reb: 60, color: "hover:border-purple-500/40 hover:bg-purple-500/10 text-purple-400" },
          ].map((p) => (
            <button
              key={p.id}
              onClick={() => {
                setDraft((prev: any) => ({
                  ...prev,
                  walletExposurePct: p.exposure,
                  minRoiPct: p.roi,
                  decisionTimeoutMs: p.timeout,
                  circuitBreakerLosses: p.losses,
                  circuitBreakerPauseSeconds: p.pause,
                  maxBalanceDrawdownPct: p.dd,
                  rebalanceThresholdPct: p.reb,
                }));
              }}
              className={`p-2 rounded-sm border border-white/5 bg-white/3 text-left transition flex flex-col justify-between cursor-pointer ${p.color}`}
            >
              <div className="text-[10px] font-bold">{p.label}</div>
              <div className="text-[8px] text-white/40 font-mono mt-0.5">{p.desc}</div>
            </button>
          ))}
        </div>
      </div>

      {/* Engine */}
      <Accordion title="⚙ Motor de Evaluación" defaultOpen>
        <Slider label="Exposición de Cartera" value={draft.walletExposurePct ?? 100}
          min={5} max={100} step={5} unit="%" onChange={v => set("walletExposurePct", v)}
          description="% del balance que el motor puede usar por operación." />
        <Slider label="ROI Mínimo Requerido" value={draft.minRoiPct ?? 1}
          min={0.001} max={1.0} step={0.001} unit="%"
          onChange={v => set("minRoiPct", v)}
          description="Retorno mínimo post-fees para ejecutar una oportunidad." />
        <NumInput label="Timeout de Decisión" value={draft.decisionTimeoutMs ?? 200}
          min={50} max={2000} step={50} unit="ms" onChange={v => set("decisionTimeoutMs", v)}
          description="Oportunidades con latencia mayor son descartadas." />
      </Accordion>

      {/* Circuit Breaker */}
      <Accordion title="⚡ Circuit Breaker">
        <div className="flex items-start gap-2 p-2 rounded-sm bg-loss/5 border border-loss/15 mb-2">
          <Zap size={10} className="text-loss mt-0.5 shrink-0" />
          <p className="text-[9px] text-loss/70 leading-relaxed">
            Pausa el motor automáticamente ante pérdidas consecutivas o drawdown excesivo.
          </p>
        </div>
        <NumInput label="Pérdidas para Activar" value={draft.circuitBreakerLosses ?? 3}
          min={1} max={20} step={1} unit="trades" onChange={v => set("circuitBreakerLosses", v)} />
        <NumInput label="Duración de la Pausa" value={draft.circuitBreakerPauseSeconds ?? 60}
          min={10} max={3600} step={10} unit="seg" onChange={v => set("circuitBreakerPauseSeconds", v)} />
        <Slider label="Drawdown Máximo" value={draft.maxBalanceDrawdownPct ?? 2}
          min={0.5} max={20} step={0.5} unit="%" onChange={v => set("maxBalanceDrawdownPct", v)}
          description="Si el balance total cae este porcentaje, el motor se detiene." />
      </Accordion>

      {/* Rebalanceo */}
      <Accordion title="⚖ Rebalanceo Automático">
        <Slider label="Umbral de Asimetría" value={draft.rebalanceThresholdPct ?? 40}
          min={5} max={80} step={5} unit="%" onChange={v => set("rebalanceThresholdPct", v)}
          description="Si la distribución de BTC entre exchanges supera este %, se rebalancea." />
      </Accordion>

      {/* Fees */}
      <Accordion title="💱 Comisiones por Exchange">
        {EXCHANGES_FEE.map(ex => (
          <div key={ex} className="space-y-2">
            <div className="text-[9px] font-bold uppercase tracking-widest text-white/40 border-b border-white/8 pb-1">
              {ex.toUpperCase()}
            </div>
            <div className="grid grid-cols-3 gap-2">
              {[
                { k: "feeTaker", lbl: "Taker", step: 0.0001 },
                { k: "feeMaker", lbl: "Maker", step: 0.0001 },
                { k: "withdrawalFeeBtc", lbl: "Retiro BTC", step: 0.00001 },
              ].map(({ k, lbl, step }) => (
                <div key={k} className="space-y-1">
                  <span className="text-[8px] text-white/30 uppercase block">{lbl}</span>
                  <input type="number" min={0} max={0.1} step={step}
                    value={draft.fees?.[ex]?.[k] ?? 0}
                    onChange={e => setFee(ex, k, Number(e.target.value))}
                    className="w-full bg-white/4 border border-white/10 px-2 py-1 text-[10px] font-mono text-white rounded-sm focus:outline-none focus:border-profit/40 transition-colors" />
                </div>
              ))}
            </div>
          </div>
        ))}
      </Accordion>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Rebalancing Panel
// ─────────────────────────────────────────────────────────────────────────────

function RebalancingContent({ apiBase }: { apiBase: string }) {
  const [status, setStatus] = useState<RebalancingStatus | null>(null);
  const [forcing, setForcing] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);

  useEffect(() => {
    const fetch_ = () => fetch(`${apiBase}/api/rebalancing`)
      .then(r => r.ok ? r.json() : null)
      .then(d => { if (d) { setStatus(d); setLastUpdate(new Date()); } })
      .catch(() => {});
    fetch_();
    const iv = setInterval(fetch_, 6000);
    return () => clearInterval(iv);
  }, [apiBase]);

  const forceRebalance = async () => {
    setForcing(true);
    try {
      const res = await fetch(`${apiBase}/api/rebalancing/force`, { method: "POST" });
      if (res.ok) setStatus(await res.json());
    } catch {}
    finally { setForcing(false); }
  };

  if (!status) return (
    <div className="flex items-center justify-center h-32 text-white/30 text-[11px] font-mono animate-pulse">
      Analizando balances…
    </div>
  );

  const entries = Object.entries(status.walletBalances ?? {});
  const totalVal = entries.reduce((s, [, w]) => s + (w.usdtValue ?? 0), 0);

  return (
    <div className="space-y-4">
      {/* Status row */}
      <div className="grid grid-cols-3 gap-2">
        {[
          { label: "Asimetría", value: `${status.maxAsymmetryPct?.toFixed(1)}%`, color: status.maxAsymmetryPct > status.thresholdPct ? "text-loss" : "text-profit" },
          { label: "Umbral", value: `${status.thresholdPct?.toFixed(0)}%`, color: "text-white/50" },
          { label: "Rebalanceos", value: String(status.totalRebalances), color: "text-blue-400" },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-white/3 border border-white/8 p-2.5 rounded-sm text-center">
            <div className="text-[8px] uppercase tracking-wider text-white/30 mb-1">{label}</div>
            <div className={`text-base font-mono font-bold ${color}`}>{value}</div>
          </div>
        ))}
      </div>

      {/* Alert */}
      {!status.balanced ? (
        <div className="flex items-start gap-2 p-2.5 bg-loss/8 border border-loss/20 rounded-sm text-[9px] text-loss">
          <AlertTriangle size={10} className="shrink-0 mt-0.5" />
          Asimetría del {status.maxAsymmetryPct?.toFixed(1)}% supera el umbral de {status.thresholdPct}%. El rebalanceo automático se activará en el próximo ciclo.
        </div>
      ) : (
        <div className="flex items-center gap-2 p-2.5 bg-profit/5 border border-profit/15 rounded-sm text-[9px] text-profit">
          <CheckCircle size={10} className="shrink-0" />
          Todos los exchanges operan dentro del rango óptimo.
        </div>
      )}

      {/* Wallet cards */}
      <div className="space-y-2">
        {entries.map(([exchange, wallet]) => {
          const c = EXCHANGE_COLORS[exchange] ?? { bg: "bg-white/3", text: "text-white/50", bar: "bg-white/30" };
          const pct = totalVal > 0 ? (wallet.usdtValue ?? 0) / totalVal * 100 : 0;
          return (
            <div key={exchange} className={`${c.bg} border border-white/8 p-3 rounded-sm`}>
              <div className="flex items-center justify-between mb-2">
                <span className={`text-[10px] font-bold tracking-wider ${c.text}`}>{exchange}</span>
                <span className="text-[11px] font-mono font-bold text-white">{fmtUsd(wallet.usdtValue ?? 0)}</span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-[9px] font-mono text-white/40 mb-2">
                <span>USDT: <span className="text-white/70">{fmtUsd(wallet.usdt ?? 0)}</span></span>
                <span>BTC: <span className="text-white/70">{(wallet.btc ?? 0).toFixed(6)}</span></span>
              </div>
              <div className="h-0.5 w-full bg-white/8 rounded-full overflow-hidden">
                <div className={`h-full ${c.bar} transition-all duration-700`} style={{ width: `${Math.min(pct, 100)}%` }} />
              </div>
              <div className="text-[8px] text-white/20 text-right mt-1">{pct.toFixed(1)}% del total</div>
            </div>
          );
        })}
      </div>

      {/* Force button */}
      <button onClick={forceRebalance} disabled={forcing}
        className="w-full flex items-center justify-center gap-2 py-2.5 text-[9px] font-bold uppercase tracking-[0.15em] transition border rounded-sm border-blue-500/30 text-blue-300 bg-blue-500/8 hover:bg-blue-500/18 cursor-pointer">
        {forcing ? <><RefreshCw size={10} className="animate-spin" /> Rebalanceando…</> : <><ArrowRightLeft size={10} /> Forzar Rebalanceo Manual</>}
      </button>

      {/* History */}
      {status.history?.length > 0 && (
        <div>
          <button onClick={() => setShowHistory(!showHistory)}
            className="text-[9px] text-white/25 hover:text-white/50 uppercase tracking-wider transition mb-2">
            {showHistory ? "▲ Ocultar" : "▼ Historial"} ({status.history.length} eventos)
          </button>
          {showHistory && (
            <div className="space-y-1 max-h-52 overflow-y-auto pr-1">
              {status.history.slice(0, 20).map((ev, i) => (
                <div key={i} className="flex items-center gap-2 text-[8px] font-mono bg-white/2 border border-white/6 px-2.5 py-1.5 rounded-sm">
                  <span className={`px-1.5 py-0.5 rounded text-[7px] font-bold shrink-0 ${ev.trigger === "AUTO" ? "bg-blue-500/15 text-blue-400" : "bg-purple-500/15 text-purple-400"}`}>
                    {ev.trigger}
                  </span>
                  <span className="text-white/25">{fmtTime(ev.timestampMs)}</span>
                  <span className="text-white/40 truncate">{ev.exchangeFrom} → {ev.exchangeTo}</span>
                  <span className={`ml-auto shrink-0 ${ev.asymmetryPctAfter < ev.asymmetryPctBefore ? "text-profit" : "text-loss"}`}>
                    {ev.asymmetryPctBefore?.toFixed(1)}%→{ev.asymmetryPctAfter?.toFixed(1)}%
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {lastUpdate && (
        <div className="flex items-center gap-1 text-[8px] text-white/15 font-mono border-t border-white/6 pt-2">
          <Clock size={8} /> Actualizado: {lastUpdate.toLocaleTimeString("es-MX")}
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Export — Sticky Side Panel
// ─────────────────────────────────────────────────────────────────────────────

type Tab = "config" | "rebalancing";

interface Props { apiBase: string }

export function StickyConfigPanel({ apiBase }: Props) {
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState<Tab>("config");
  const panelRef = useRef<HTMLDivElement>(null);

  // Close on outside click
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, [open]);

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  return (
    <>
      {/* Backdrop */}
      <div
        className={`fixed inset-0 z-40 bg-black/50 backdrop-blur-[2px] transition-opacity duration-300 ${
          open ? "opacity-100 pointer-events-auto" : "opacity-0 pointer-events-none"
        }`}
        onClick={() => setOpen(false)}
      />

      {/* Sticky trigger button */}
      <div className="fixed right-0 top-1/2 -translate-y-1/2 z-50">
        <button
          onClick={() => setOpen(!open)}
          className={`group flex flex-col items-center gap-2 px-2 py-5 transition-all duration-300 border-y border-l border-white/10 ${
            open
              ? "bg-blue-500/20 border-blue-500/30 text-blue-300"
              : "bg-black/60 backdrop-blur-sm text-white/40 hover:text-white/80 hover:bg-white/5"
          }`}
          title="Configuración Avanzada"
        >
          <SlidersHorizontal size={14} className="shrink-0" />
          <span className="text-[8px] font-bold uppercase tracking-[0.2em] [writing-mode:vertical-rl] rotate-180">
            Config
          </span>
        </button>
      </div>

      {/* Slide panel */}
      <div
        ref={panelRef}
        className={`fixed top-0 right-0 h-full z-50 w-[420px] flex flex-col transition-transform duration-300 ease-out ${
          open ? "translate-x-0" : "translate-x-full"
        }`}
        style={{ background: "hsl(260 6% 9%)", borderLeft: "1px solid rgba(255,255,255,0.08)" }}
      >
        {/* Panel header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/8 shrink-0"
          style={{ background: "hsl(260 6% 7%)" }}>
          <div className="flex items-center gap-2.5">
            <div className="w-1.5 h-4 bg-blue-500 rounded-full opacity-80" />
            <span className="text-[11px] font-bold uppercase tracking-[0.2em] text-white/70">
              Panel de Control
            </span>
          </div>
          <button onClick={() => setOpen(false)}
            className="p-1.5 text-white/30 hover:text-white/80 hover:bg-white/5 rounded-sm transition">
            <X size={14} />
          </button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-white/8 shrink-0" style={{ background: "hsl(260 6% 7%)" }}>
          {([
            { id: "config",      label: "Configuración", icon: Settings  },
            { id: "rebalancing", label: "Rebalanceo",    icon: Scale     },
          ] as { id: Tab; label: string; icon: any }[]).map(({ id, label, icon: Icon }) => (
            <button key={id} onClick={() => setTab(id)}
              className={`flex-1 flex items-center justify-center gap-2 py-3 text-[10px] font-bold uppercase tracking-wider transition-colors ${
                tab === id
                  ? "text-blue-300 border-b-2 border-blue-400"
                  : "text-white/30 hover:text-white/60 border-b-2 border-transparent"
              }`}>
              <Icon size={11} />
              {label}
            </button>
          ))}
        </div>

        {/* Content (scrollable) */}
        <div className="flex-1 overflow-y-auto p-5 space-y-1">
          {tab === "config" && <ConfigContent apiBase={apiBase} />}
          {tab === "rebalancing" && <RebalancingContent apiBase={apiBase} />}
        </div>

        {/* Footer */}
        <div className="px-5 py-3 border-t border-white/6 shrink-0 flex items-center gap-2"
          style={{ background: "hsl(260 6% 7%)" }}>
          <div className="w-1.5 h-1.5 rounded-full bg-profit animate-pulse" />
          <span className="text-[8px] font-mono text-white/20 uppercase tracking-widest">
            Motor activo · Cambios aplicados en caliente
          </span>
        </div>
      </div>
    </>
  );
}
