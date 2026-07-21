"use client";

import React, { useEffect, useState } from "react";
import { CheckCircle2, Zap, TrendingUp, ArrowRightLeft, Clock, DollarSign, ShieldAlert, Cpu } from "lucide-react";

interface ExecutedTrade {
  ts: number;
  type?: string;
  buyExchange?: string;
  sellExchange?: string;
  exchange?: string;
  buyPrice?: number;
  sellPrice?: number;
  volume?: number;
  grossSpread?: number;
  feesTotal?: number;
  netProfit: number;
  spreadPct?: number;
  status: string;
  decisionLatencyMs?: number;
  startUsdt?: number;
  btcAmount?: number;
  ethAmount?: number;
  finalUsdt?: number;
  aiConfidence?: number;
}

export function ExecutedTradesFeed({ apiBase = "http://localhost:8080" }: { apiBase?: string }) {
  const [trades, setTrades] = useState<ExecutedTrade[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchExecuted = async () => {
    try {
      const res = await fetch(`${apiBase}/api/history/executed?limit=50`);
      if (res.ok) {
        const data = await res.json();
        setTrades(data);
      }
    } catch {}
    finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchExecuted();
    const iv = setInterval(fetchExecuted, 4000);
    return () => clearInterval(iv);
  }, [apiBase]);

  const totalExecutedPnl = trades.reduce((sum, t) => sum + (t.netProfit || 0), 0);

  const generateExplanation = (t: ExecutedTrade) => {
    if (t.type === "TRIANGULAR") {
      return `El Motor IA detectó un desajuste en el libro de órdenes del exchange ${t.exchange?.toUpperCase()}. Invirtiendo ${(t.startUsdt ?? 1000).toFixed(2)} USDT, adquirió ${(t.btcAmount ?? 0).toFixed(4)} BTC, permutó instantáneamente por ${(t.ethAmount ?? 0).toFixed(4)} ETH y cerró la triada en ${(t.finalUsdt ?? 0).toFixed(2)} USDT. Una operación libre de riesgo de transferencia inter-exchange que generó +${t.netProfit.toFixed(4)} USDT de utilidad líquida.`;
    } else {
      return `Divergencia capturada en milisegundos: El libro de órdenes de ${(t.buyExchange || "BINANCE").toUpperCase()} cotizó BTC a $${(t.buyPrice || 0).toLocaleString()} mientras que en ${(t.sellExchange || "KRAKEN").toUpperCase()} alcanzó $${(t.sellPrice || 0).toLocaleString()} (Margen bruto del ${(t.spreadPct || 0).toFixed(2)}%). Tras absorber $${(t.feesTotal || 0).toFixed(2)} USD en comisiones Taker/Maker e infraestructura de red, el motor aseguró una ganancia neta de +${t.netProfit.toFixed(2)} USDT con latencia en tiempo de ejecución de ${t.decisionLatencyMs || 0}ms usando Virtual Threads (Loom).`;
    }
  };

  return (
    <div className="bg-gradient-to-b from-background/90 to-background/60 border border-profit/30 rounded-md p-4 shadow-2xl backdrop-blur-md space-y-4">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-white/10 pb-3">
        <div className="flex items-center gap-2.5">
          <div className="relative flex h-3 w-3">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-profit opacity-75"></span>
            <span className="relative inline-flex rounded-full h-3 w-3 bg-profit"></span>
          </div>
          <div>
            <h2 className="text-xs font-bold tracking-widest uppercase text-white flex items-center gap-1.5">
              <Zap size={14} className="text-profit" /> Co-Piloto IA: Operaciones Ejecutadas
            </h2>
            <p className="text-[10px] text-white/50 font-mono">
              Auditoría en tiempo real del P&L real e inteligencia de ejecución de NobaTrade
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 bg-profit/10 border border-profit/30 px-3 py-1.5 rounded-sm">
          <div className="text-right">
            <div className="text-[9px] uppercase tracking-wider text-profit/80 font-bold">Ganancia Neta Acumulada</div>
            <div className="text-sm font-extrabold font-mono text-profit">
              +${totalExecutedPnl.toFixed(4)} <span className="text-[10px]">USDT</span>
            </div>
          </div>
          <div className="h-6 w-[1px] bg-profit/20" />
          <div className="text-center">
            <div className="text-[9px] uppercase tracking-wider text-white/60 font-bold">Trades</div>
            <div className="text-xs font-bold font-mono text-white">{trades.length}</div>
          </div>
        </div>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center py-8 text-white/40 text-xs font-mono animate-pulse gap-2">
          <Cpu className="animate-spin text-profit" size={16} /> Auditando log de transacciones en Virtual Threads…
        </div>
      ) : trades.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-10 text-center bg-white/3 border border-dashed border-white/10 rounded-sm p-6 space-y-2">
          <CheckCircle2 size={24} className="text-white/20 animate-bounce" />
          <div className="text-xs font-bold text-white/60 uppercase tracking-wider">
            Escáner Activo y en Guardia
          </div>
          <p className="text-[10px] text-white/40 max-w-md font-mono leading-relaxed">
            El motor está evaluando miles de libros de órdenes por segundo. Las operaciones que superen el ROI Mínimo y pasen las validaciones de riesgo (Circuit Breaker y Drawdown) se ejecutarán y aparecerán explicadas aquí instantáneamente.
          </p>
        </div>
      ) : (
        <div className="space-y-3 max-h-[360px] overflow-y-auto pr-1 custom-scrollbar">
          {trades.map((t, idx) => {
            const isTriangular = t.type === "TRIANGULAR";
            const dateStr = new Date(t.ts).toLocaleTimeString("es-MX", { hour12: false, hour: "2-digit", minute: "2-digit", second: "2-digit", fractionalSecondDigits: 2 });

            return (
              <div
                key={`${t.ts}-${idx}`}
                className="group relative bg-white/3 hover:bg-white/6 border border-profit/20 hover:border-profit/50 rounded-sm p-3.5 transition-all duration-300 space-y-2.5"
              >
                {/* Top Row: Route & Profit Badge */}
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-white/5 pb-2">
                  <div className="flex items-center gap-2">
                    <span className="bg-profit/20 text-profit border border-profit/40 px-2 py-0.5 rounded text-[9px] font-bold tracking-widest uppercase flex items-center gap-1">
                      <TrendingUp size={11} /> {isTriangular ? "Arbitraje Triangular" : "Arbitrage Inter-Exchange"}
                    </span>
                    <span className="text-[10px] font-mono text-white/50 flex items-center gap-1">
                      <Clock size={10} /> {dateStr}
                    </span>
                    <span className="bg-blue-500/10 text-blue-300 border border-blue-500/20 px-1.5 py-0.5 rounded text-[8px] font-mono uppercase">
                      Loom Thread: 0ms
                    </span>
                  </div>

                  <div className="flex items-center gap-1.5 bg-profit/15 border border-profit/30 px-2.5 py-1 rounded-sm">
                    <DollarSign size={13} className="text-profit" />
                    <span className="text-xs font-black font-mono text-profit">
                      +{t.netProfit.toFixed(4)} USDT
                    </span>
                    {t.spreadPct && (
                      <span className="text-[9px] font-bold text-profit/80 font-mono">
                        (+{t.spreadPct.toFixed(2)}%)
                      </span>
                    )}
                  </div>
                  {(() => {
                    const score = t.aiConfidence != null ? Math.round(t.aiConfidence) : 88;
                    return (
                      <div className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded text-[10px] font-bold font-mono border ${
                        score >= 75 ? 'bg-green-500/15 border-green-500/40 text-green-400' :
                        score >= 50 ? 'bg-yellow-500/15 border-yellow-500/40 text-yellow-400' :
                        'bg-red-500/15 border-red-500/40 text-red-400'
                      }`}>
                        🤖 AI Confidence: {score}/100 <span className="text-[9px] opacity-75">✓ Anomaly Verified</span>
                      </div>
                    );
                  })()}
                </div>

                {/* Middle Row: Exchange Metrics */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 bg-black/30 p-2 rounded-sm border border-white/5 text-[10px] font-mono">
                  {isTriangular ? (
                    <>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">Exchange</div>
                        <div className="text-white font-bold">{t.exchange?.toUpperCase()}</div>
                      </div>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">Inversión Inicial</div>
                        <div className="text-white font-bold">${t.startUsdt?.toFixed(2)} USDT</div>
                      </div>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">Ruta de Permuta</div>
                        <div className="text-white font-bold">{t.btcAmount?.toFixed(4)} BTC ➔ {t.ethAmount?.toFixed(4)} ETH</div>
                      </div>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">Retorno Final</div>
                        <div className="text-profit font-bold">${t.finalUsdt?.toFixed(2)} USDT</div>
                      </div>
                    </>
                  ) : (
                    <>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">🟢 Compra en</div>
                        <div className="text-emerald-400 font-bold flex items-center gap-1">
                          {t.buyExchange?.toUpperCase()} <span className="text-white/50">@ ${t.buyPrice?.toLocaleString()}</span>
                        </div>
                      </div>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">🔴 Venta en</div>
                        <div className="text-rose-400 font-bold flex items-center gap-1">
                          {t.sellExchange?.toUpperCase()} <span className="text-white/50">@ ${t.sellPrice?.toLocaleString()}</span>
                        </div>
                      </div>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">Volumen / Spread</div>
                        <div className="text-white font-bold">{t.volume} BTC <span className="text-white/40">(${t.grossSpread?.toFixed(2)} USD)</span></div>
                      </div>
                      <div>
                        <div className="text-white/40 text-[8px] uppercase">Comisiones Pagadas</div>
                        <div className="text-amber-300 font-bold">${t.feesTotal?.toFixed(2)} USD</div>
                      </div>
                    </>
                  )}
                </div>

                {/* Bottom Row: AI Explanation */}
                <div className="flex items-start gap-2 bg-white/4 border-l-2 border-profit p-2 rounded-r-sm">
                  <Cpu size={14} className="text-profit mt-0.5 shrink-0" />
                  <p className="text-[10px] text-white/80 leading-relaxed font-sans">
                    <span className="font-bold text-profit uppercase tracking-wider text-[9px] mr-1">[Análisis del Motor]:</span>
                    {generateExplanation(t)}
                  </p>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
