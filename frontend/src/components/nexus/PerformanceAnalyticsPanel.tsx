"use client";
import { useState, useEffect, useMemo } from "react";
import { ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid } from "recharts";
import { TrendingUp, ShieldAlert, Zap, AlertCircle, Clock, BarChart3, Activity, Layers } from "lucide-react";

interface TradeEvent {
  id: string;
  timestampMs?: number;
  timestamp?: number;
  symbol?: string;
  exchange?: string;
  buyExchange?: string;
  sellExchange?: string;
  netProfit?: number;
  status?: string;
  rejectionReason?: string | null;
}

interface Props {
  apiBase: string;
  direct: any[];
  triangular: any[];
}

const REJECTION_META: Record<string, { label: string; desc: string; color: string; bg: string; bar: string }> = {
  REJECTED_FEES: {
    label: "Comisiones y ROI Mínimo",
    desc: "El spread bruto fue inferior al costo combinado de comisiones (Taker/Maker) o no superó el ROI mínimo configurado en el Cockpit.",
    color: "text-amber-400", bg: "bg-amber-500/10", bar: "bg-amber-400"
  },
  REJECTED_SLIPPAGE: {
    label: "Deslizamiento (Slippage) Excesivo",
    desc: "La ejecución de la orden habría desplazado el precio del libro más allá del umbral de tolerancia de riesgo permitido.",
    color: "text-orange-400", bg: "bg-orange-500/10", bar: "bg-orange-400"
  },
  REJECTED_LIQUIDITY: {
    label: "Profundidad de Liquidez Insuficiente",
    desc: "El volumen disponible en los primeros niveles del libro de órdenes no es suficiente para llenar el tamaño mínimo de operación.",
    color: "text-blue-400", bg: "bg-blue-500/10", bar: "bg-blue-400"
  },
  REJECTED_LATENCY: {
    label: "Latencia Excedida (Timeout)",
    desc: "El tiempo transcurrido desde la captura del snapshot hasta la decisión de trading superó el timeout máximo configurado en el motor.",
    color: "text-purple-400", bg: "bg-purple-500/10", bar: "bg-purple-400"
  },
  REJECTED_CIRCUIT_BREAKER: {
    label: "Circuit Breaker Activo",
    desc: "El bot se encuentra pausado temporalmente tras alcanzar el límite de pérdidas consecutivas o el porcentaje máximo de drawdown permitido.",
    color: "text-rose-400", bg: "bg-rose-500/10", bar: "bg-rose-400"
  },
};

export function PerformanceAnalyticsPanel({ apiBase, direct = [], triangular = [] }: Props) {
  const [backendData, setBackendData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"pnl" | "rejections">("pnl");
  const [sessionExecuted, setSessionExecuted] = useState<any[]>([]);

  useEffect(() => {
    const allProps = [...direct, ...triangular];
    const newExecuted = allProps
      .filter((e) => e.status === "EXECUTED" && (e.timestampMs || e.timestamp))
      .map((e) => {
        const ts = e.timestampMs || e.timestamp || Date.now();
        const d = new Date(ts);
        const route = e.buyExchange && e.sellExchange
          ? `${e.buyExchange.toUpperCase()}→${e.sellExchange.toUpperCase()}`
          : e.exchange ? e.exchange.toUpperCase() : "BTC/USDT";
        return {
          ts,
          time: `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`,
          tradeProfit: e.netProfit || 0,
          type: e.route === "Triangular" || e.type === "TRIANGULAR" ? "TRIANGULAR" : "DIRECT",
          symbol: route,
        };
      });

    if (newExecuted.length > 0) {
      setSessionExecuted((prev) => {
        const seen = new Set(prev.map((p) => p.ts));
        const added = newExecuted.filter((item) => !seen.has(item.ts));
        if (added.length === 0) return prev;
        return [...prev, ...added].sort((a, b) => a.ts - b.ts).slice(-300);
      });
    }
  }, [direct, triangular]);

  useEffect(() => {
    const fetchAnalytics = () => {
      fetch(`${apiBase}/api/analytics/performance`)
        .then((r) => (r.ok ? r.json() : null))
        .then((d) => {
          if (d) setBackendData(d);
          setLoading(false);
        })
        .catch(() => setLoading(false));
    };
    fetchAnalytics();
    const iv = setInterval(fetchAnalytics, 6000);
    return () => clearInterval(iv);
  }, [apiBase]);

  // Combine backend history with live/demo events without resetting or dropping P&L
  const { chartData, rejectionsMap, totalExecuted, totalRejected, currentPnl } = useMemo(() => {
    const allProps = [...direct, ...triangular];
    const bHist = [...(backendData?.pnlHistory || [])].sort((a: any, b: any) => a.ts - b.ts);
    const bRej = backendData?.rejections || {};
    const seenTs = new Set(bHist.map((p: any) => p.ts));

    // Start running P&L from the exact last point of backend history
    let currentRunningPnl = bHist.length > 0 ? (bHist[bHist.length - 1].pnl || 0) : 0;

    const extraExecuted = sessionExecuted
      .filter((e) => !seenTs.has(e.ts))
      .sort((a, b) => a.ts - b.ts);

    const processedExtra = extraExecuted.map((item) => {
      currentRunningPnl += item.tradeProfit || 0;
      return {
        ...item,
        pnl: Math.round(currentRunningPnl * 100) / 100,
      };
    });

    const finalChart = [...bHist, ...processedExtra].sort((a, b) => a.ts - b.ts);

    // If still empty (new setup without any trades), provide a sleek baseline graph so it's not empty
    if (finalChart.length === 0) {
      const now = Date.now();
      for (let i = 5; i >= 0; i--) {
        const t = new Date(now - i * 10000);
        finalChart.push({
          ts: t.getTime(),
          time: `${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}:${String(t.getSeconds()).padStart(2, "0")}`,
          pnl: 0.0,
          tradeProfit: 0.0,
          type: "SYSTEM",
          symbol: "EN ESPERA",
        });
      }
    }

    // Merge rejection counts from props
    const mergedRej: Record<string, number> = {
      REJECTED_FEES: bRej.REJECTED_FEES || 0,
      REJECTED_SLIPPAGE: bRej.REJECTED_SLIPPAGE || 0,
      REJECTED_LIQUIDITY: bRej.REJECTED_LIQUIDITY || 0,
      REJECTED_LATENCY: bRej.REJECTED_LATENCY || 0,
      REJECTED_CIRCUIT_BREAKER: bRej.REJECTED_CIRCUIT_BREAKER || 0,
    };

    allProps.forEach((e) => {
      if (e.status === "REJECTED_FEES") mergedRej.REJECTED_FEES++;
      else if (e.status === "REJECTED_CIRCUIT_BREAKER") mergedRej.REJECTED_CIRCUIT_BREAKER++;
      else if (e.status === "REJECTED_SLIPPAGE") mergedRej.REJECTED_SLIPPAGE++;
      else if (e.status === "REJECTED_LIQUIDITY") mergedRej.REJECTED_LIQUIDITY++;
      else if (e.status === "REJECTED_LATENCY") mergedRej.REJECTED_LATENCY++;
      else if (e.status === "REJECTED" || e.rejectionReason) {
        const r = e.rejectionReason || "";
        if (r.includes("FEE") || r.includes("ROI")) mergedRej.REJECTED_FEES++;
        else if (r.includes("BREAKER") || r.includes("PAUSE")) mergedRej.REJECTED_CIRCUIT_BREAKER++;
        else if (r.includes("LIQUID") || r.includes("VOLUME") || r.includes("DEPTH")) mergedRej.REJECTED_LIQUIDITY++;
        else if (r.includes("SLIP")) mergedRej.REJECTED_SLIPPAGE++;
        else if (r.includes("TIMEOUT") || r.includes("LATENCY")) mergedRej.REJECTED_LATENCY++;
        else mergedRej.REJECTED_FEES++;
      }
    });

    const totExec = finalChart.filter((c) => c.type !== "SYSTEM").length;
    const totRej = Object.values(mergedRej).reduce((a, b) => a + b, 0);
    const curPnl = finalChart[finalChart.length - 1]?.pnl || 0;

    return {
      chartData: finalChart,
      rejectionsMap: mergedRej,
      totalExecuted: totExec,
      totalRejected: totRej,
      currentPnl: curPnl,
    };
  }, [backendData, direct, triangular]);

  const totalEvaluated = totalExecuted + totalRejected;
  const efficiency = totalEvaluated > 0 ? (totalExecuted / totalEvaluated) * 100 : 0;

  return (
    <section className="space-y-4">
      {/* Panel Header */}
      <div className="flex flex-wrap items-center justify-between border-b nx-hairline pb-3">
        <div className="flex items-center gap-2.5">
          <div className="flex h-6 w-6 items-center justify-center rounded-sm bg-profit/15 text-profit border border-profit/30">
            <Activity size={14} className="animate-pulse" />
          </div>
          <div>
            <h2 className="text-[11px] font-bold uppercase tracking-[0.2em] text-foreground/80">
              Rendimiento y Desglose Analítico
            </h2>
            <p className="text-[9px] font-mono uppercase tracking-widest text-foreground/35">
              Curva de Crecimiento de Capital · Diagnóstico del Motor de Riesgo
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="flex items-center gap-1.5 rounded-sm bg-surface-2 border border-foreground/10 px-2.5 py-1 text-[9px] font-mono text-foreground/60 uppercase tracking-widest">
            <span className="h-1.5 w-1.5 rounded-full bg-profit animate-ping" />
            Sincronización en Tiempo Real
          </span>
        </div>
      </div>

      {/* KPI Row */}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <div className="nx-panel p-3 space-y-1 bg-gradient-to-br from-surface to-profit/5 border-l-2 border-l-profit">
          <span className="text-[9px] font-bold uppercase tracking-wider text-foreground/40 block">
            P&L Acumulado
          </span>
          <div className={`text-lg font-mono font-bold ${currentPnl >= 0 ? "text-profit" : "text-loss"}`}>
            {currentPnl >= 0 ? "+" : ""}${currentPnl.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </div>
          <span className="text-[8px] font-mono text-foreground/30 uppercase tracking-widest block">
            Beneficio Neto Post-Comisiones
          </span>
        </div>

        <div className="nx-panel p-3 space-y-1">
          <span className="text-[9px] font-bold uppercase tracking-wider text-foreground/40 block">
            Operaciones Ejecutadas
          </span>
          <div className="text-lg font-mono font-bold text-foreground">
            {totalExecuted}
          </div>
          <span className="text-[8px] font-mono text-profit uppercase tracking-widest block">
            ✓ Capturas Exitosas
          </span>
        </div>

        <div className="nx-panel p-3 space-y-1">
          <span className="text-[9px] font-bold uppercase tracking-wider text-foreground/40 block">
            Oportunidades Rechazadas
          </span>
          <div className="text-lg font-mono font-bold text-warn">
            {totalRejected}
          </div>
          <span className="text-[8px] font-mono text-warn/70 uppercase tracking-widest block">
            ⚠ Filtradas por Riesgo/ROI
          </span>
        </div>

        <div className="nx-panel p-3 space-y-1">
          <span className="text-[9px] font-bold uppercase tracking-wider text-foreground/40 block">
            Eficiencia del Motor
          </span>
          <div className="text-lg font-mono font-bold text-blue-400">
            {efficiency.toFixed(1)}%
          </div>
          <span className="text-[8px] font-mono text-foreground/30 uppercase tracking-widest block">
            Tasa de Conversión de Señales
          </span>
        </div>
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        {/* Left Column: Cumulative P&L Timeline */}
        <div className="nx-panel p-4 lg:col-span-7 flex flex-col justify-between space-y-4">
          <div className="flex items-center justify-between border-b nx-hairline pb-2.5">
            <div className="flex items-center gap-2">
              <TrendingUp size={14} className="text-profit" />
              <span className="text-[10px] font-bold uppercase tracking-[0.15em] text-foreground/70">
                Línea de Tiempo P&L Acumulado (USD)
              </span>
            </div>
            <span className="text-[9px] font-mono text-foreground/40">
              {chartData.length} Puntos de Evaluación
            </span>
          </div>

          <div className="h-[280px] w-full pt-2">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <defs>
                  <linearGradient id="colorPnl" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="hsl(155 80% 55%)" stopOpacity={0.35} />
                    <stop offset="95%" stopColor="hsl(155 80% 55%)" stopOpacity={0.0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                <XAxis
                  dataKey="time"
                  stroke="rgba(255,255,255,0.25)"
                  tick={{ fontSize: 9, fontFamily: "JetBrains Mono" }}
                  tickLine={false}
                />
                <YAxis
                  stroke="rgba(255,255,255,0.25)"
                  tick={{ fontSize: 9, fontFamily: "JetBrains Mono" }}
                  tickLine={false}
                  tickFormatter={(val) => `$${val}`}
                />
                <Tooltip
                  content={({ active, payload }) => {
                    if (active && payload && payload.length) {
                      const data = payload[0].payload;
                      return (
                        <div className="bg-background/95 border border-foreground/15 p-2.5 rounded-sm shadow-xl space-y-1 font-mono text-[10px]">
                          <div className="text-foreground/40 text-[9px] border-b border-foreground/10 pb-1 mb-1">
                            {data.time} · {data.type}
                          </div>
                          <div className="flex justify-between gap-4">
                            <span className="text-foreground/60">Ruta:</span>
                            <span className="font-bold text-foreground">{data.symbol}</span>
                          </div>
                          <div className="flex justify-between gap-4">
                            <span className="text-foreground/60">Ganancia Trade:</span>
                            <span className={data.tradeProfit >= 0 ? "text-profit font-bold" : "text-loss font-bold"}>
                              {data.tradeProfit >= 0 ? "+" : ""}${data.tradeProfit.toFixed(2)}
                            </span>
                          </div>
                          <div className="flex justify-between gap-4 border-t border-foreground/10 pt-1 mt-1">
                            <span className="text-foreground/60">P&L Acumulado:</span>
                            <span className="text-profit font-bold text-[11px]">
                              ${data.pnl.toFixed(2)}
                            </span>
                          </div>
                        </div>
                      );
                    }
                    return null;
                  }}
                />
                <Area
                  type="monotone"
                  dataKey="pnl"
                  stroke="hsl(155 80% 55%)"
                  strokeWidth={2}
                  fillOpacity={1}
                  fill="url(#colorPnl)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>

          <div className="flex items-center justify-between text-[8px] font-mono text-foreground/30 border-t nx-hairline pt-2">
            <span>Inicio del Sistema · 0.00 USD</span>
            <span>Eje Y: Beneficio Neto en USDT</span>
            <span>Última Evaluación · ${currentPnl.toFixed(2)} USD</span>
          </div>
        </div>

        {/* Right Column: Rejection Breakdown by Reason */}
        <div className="nx-panel p-4 lg:col-span-5 flex flex-col justify-between space-y-3">
          <div className="flex items-center justify-between border-b nx-hairline pb-2.5">
            <div className="flex items-center gap-2">
              <ShieldAlert size={14} className="text-warn" />
              <span className="text-[10px] font-bold uppercase tracking-[0.15em] text-foreground/70">
                Desglose de Rechazos del Motor
              </span>
            </div>
            <span className="text-[9px] font-mono text-warn font-bold">
              {totalRejected} Disparos Protegidos
            </span>
          </div>

          <div className="space-y-3 pt-1 overflow-y-auto max-h-[290px] pr-1">
            {Object.entries(REJECTION_META).map(([key, meta]) => {
              const count = rejectionsMap[key] || 0;
              const pct = totalRejected > 0 ? (count / totalRejected) * 100 : 0;
              return (
                <div key={key} className={`${meta.bg} border border-foreground/10 p-3 rounded-sm space-y-1.5 transition-all hover:border-foreground/20`}>
                  <div className="flex items-center justify-between">
                    <span className={`text-[10px] font-bold uppercase tracking-wider ${meta.color}`}>
                      {meta.label}
                    </span>
                    <div className="flex items-center gap-2 font-mono">
                      <span className="text-[11px] font-bold text-foreground">{count}</span>
                      <span className="text-[9px] text-foreground/40">({pct.toFixed(1)}%)</span>
                    </div>
                  </div>
                  <p className="text-[9px] text-foreground/50 leading-relaxed font-sans">
                    {meta.desc}
                  </p>
                  <div className="h-1.5 w-full bg-foreground/10 rounded-full overflow-hidden mt-1">
                    <div
                      className={`h-full ${meta.bar} transition-all duration-700`}
                      style={{ width: `${Math.min(pct, 100)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>

          <div className="text-[8px] font-mono text-foreground/30 border-t nx-hairline pt-2 text-center">
            Protección Activa: Las oportunidades descartadas evitan pérdidas por slippage o fees negativos.
          </div>
        </div>
      </div>
    </section>
  );
}
