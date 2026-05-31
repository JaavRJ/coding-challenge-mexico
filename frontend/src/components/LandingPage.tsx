'use client'

import { useEffect, useState } from "react";
import { ArrowUpRight, Activity, Zap, Shield, GitBranch, Terminal as TerminalIcon, Coins, RefreshCw } from "lucide-react";

export function LandingPage({ onLaunch }: { onLaunch: () => void }) {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 1500);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* HERO */}
      <section className="border-b nx-hairline">
        <div className="mx-auto grid max-w-[1400px] grid-cols-12 gap-6 px-6 py-20 lg:py-28">
          <div className="col-span-12 lg:col-span-8">
            <div className="flex items-center gap-2 font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">
              <span className="inline-block h-1.5 w-1.5 animate-pulse bg-profit" />
              <span>Sistema Activo · Motor v4.2.1</span>
            </div>
            <h1 className="mt-6 text-5xl font-bold leading-[0.95] tracking-tight md:text-7xl lg:text-[5.5rem]">
              Arbitraje con
              <br />
              <span className="italic font-light opacity-60">latencia institucional</span>
              <br />
              y precisión.
            </h1>
            <p className="mt-8 max-w-xl text-base leading-relaxed text-foreground/60">
              NobaTrade centraliza libros de órdenes de Binance, Kraken y Coinbase en tiempo real. 
              Evalúa cientos de oportunidades por segundo y ejecuta estrategias de arbitraje directo 
              y triangular, protegido por un cortocircuito automático ante anomalías del mercado.
            </p>
            <div className="mt-10 flex flex-wrap items-center gap-3">
              <button
                onClick={onLaunch}
                className="group flex h-12 items-center gap-3 bg-foreground px-6 text-xs font-bold uppercase tracking-widest text-background transition hover:opacity-90"
              >
                Abrir Terminal en Vivo
                <ArrowUpRight className="h-4 w-4 transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
              </button>
              <a
                href="#engine"
                className="flex h-12 items-center gap-3 border border-foreground/25 px-6 text-xs font-bold uppercase tracking-widest text-foreground transition hover:bg-surface"
              >
                Leer especificaciones
              </a>
            </div>
          </div>

          <aside className="col-span-12 lg:col-span-4">
            <div className="nx-panel h-full p-5 flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between border-b nx-hairline pb-3">
                  <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/40">Feed en Vivo</span>
                  <span className="font-mono text-[10px] text-profit">● transmitiendo</span>
                </div>
                <ul className="mt-4 space-y-3 font-mono text-[11px]">
                  {sampleTicks(tick).map((t, i) => (
                    <li key={i} className="flex items-center justify-between">
                      <span className="text-foreground/50">{t.pair}</span>
                      <span className="text-foreground/70">{t.venue}</span>
                      <span className={t.delta >= 0 ? "text-profit" : "text-loss"}>
                        {t.delta >= 0 ? "+" : ""}
                        {t.delta.toFixed(2)}%
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
              <div className="mt-5 grid grid-cols-3 gap-2 border-t nx-hairline pt-4">
                <Stat k="Lat" v="42ms" />
                <Stat k="Win" v="68%" />
                <Stat k="Vol" v="$8.4M" />
              </div>
            </div>
          </aside>
        </div>
      </section>

      {/* METRICS BAR */}
      <section id="metrics" className="border-b nx-hairline bg-surface">
        <div className="mx-auto grid max-w-[1400px] grid-cols-2 divide-x divide-y nx-hairline md:grid-cols-4 md:divide-y-0">
          {[
            { k: "Evaluaciones / seg", v: "214" },
            { k: "Latencia mediana", v: "42ms" },
            { k: "Exchanges integrados", v: "5" },
            { k: "Oportunidades (24h)", v: "1,842" },
          ].map((m) => (
            <div key={m.k} className="px-6 py-8 border-b md:border-b-0 nx-hairline">
              <div className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/40">{m.k}</div>
              <div className="mt-3 text-3xl font-bold tracking-tight">{m.v}</div>
            </div>
          ))}
        </div>
      </section>

      {/* DETAILED EXPLANATION */}
      <section id="how-it-works" className="border-b nx-hairline bg-background">
        <div className="mx-auto max-w-[1400px] px-6 py-24">
          <div className="max-w-3xl mb-16">
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">01 — Estrategias</span>
            <h2 className="mt-4 text-4xl font-bold tracking-tight">¿Cómo funciona NobaTrade?</h2>
            <p className="mt-4 text-sm leading-relaxed text-foreground/60">
              Nuestro motor está diseñado para identificar ineficiencias de mercado a gran velocidad.
              Detecta diferencias de precios (spreads) y ejecuta operaciones simuladas instantáneamente
              tomando en cuenta las comisiones de cada red (maker/taker fees).
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-12">
            <div className="nx-panel p-8 relative overflow-hidden group">
              <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-20 transition-opacity">
                <RefreshCw className="w-32 h-32" />
              </div>
              <h3 className="text-xl font-bold mb-4 flex items-center gap-2">
                <GitBranch className="h-5 w-5 text-profit" />
                Arbitraje Espacial (Cross-Exchange)
              </h3>
              <p className="text-foreground/70 text-sm leading-relaxed mb-6">
                Compara el mismo par (ej. BTC/USDT) entre múltiples exchanges. Si el precio de venta (Ask) en Binance es más bajo que el precio de compra (Bid) en Kraken por un margen que supera las comisiones combinadas, el motor identifica un spread rentable y emite las órdenes de compra y venta simultáneamente.
              </p>
              <div className="bg-surface p-4 border border-foreground/10 text-xs font-mono text-foreground/60">
                1. Buy BTC @ Binance (Low Ask)<br/>
                2. Sell BTC @ Kraken (High Bid)<br/>
                3. Net Profit = Spread - Fees
              </div>
            </div>

            <div className="nx-panel p-8 relative overflow-hidden group">
              <div className="absolute top-0 right-0 p-4 opacity-10 group-hover:opacity-20 transition-opacity">
                <Coins className="w-32 h-32" />
              </div>
              <h3 className="text-xl font-bold mb-4 flex items-center gap-2">
                <Activity className="h-5 w-5 text-profit" />
                Arbitraje Triangular
              </h3>
              <p className="text-foreground/70 text-sm leading-relaxed mb-6">
                Explota desequilibrios entre tres pares de criptomonedas dentro de un solo exchange. El motor rastrea el ciclo completo: cambiar USDT por BTC, luego BTC por ETH, y finalmente ETH de vuelta a USDT. Si la cantidad final de USDT supera la inicial descontando todas las tarifas, asegura la ganancia libre de riesgo.
              </p>
              <div className="bg-surface p-4 border border-foreground/10 text-xs font-mono text-foreground/60">
                1. USDT → BTC (Pair 1)<br/>
                2. BTC → ETH (Pair 2)<br/>
                3. ETH → USDT (Pair 3)
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ENGINE */}
      <section id="engine" className="border-b nx-hairline">
        <div className="mx-auto grid max-w-[1400px] grid-cols-12 gap-6 px-6 py-24">
          <div className="col-span-12 lg:col-span-4">
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">02 — El Motor</span>
            <h2 className="mt-4 text-4xl font-bold tracking-tight">
              Determinista por diseño.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-foreground/60">
              Cada evaluación es reproducible. Mismo libro de órdenes, misma configuración,
              misma decisión — siempre. NobaTrade elimina la incertidumbre de la ejecución.
            </p>
          </div>
          <div className="col-span-12 grid grid-cols-1 gap-px bg-border lg:col-span-8 md:grid-cols-2">
            {features.map((f) => (
              <div key={f.title} className="bg-background p-8 hover:bg-surface transition-colors cursor-default">
                <f.icon className="h-5 w-5 text-foreground/70" />
                <h3 className="mt-5 text-base font-semibold">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-foreground/55">{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* PIPELINE GRID */}
      <section className="border-b nx-hairline bg-surface">
        <div className="mx-auto max-w-[1400px] px-6 py-24">
          <div className="mb-12">
             <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">03 — Arquitectura del Pipeline</span>
             <h2 className="mt-4 max-w-2xl text-4xl font-bold tracking-tight md:text-4xl">
               De cinco libros de órdenes a una ejecución priorizada
             </h2>
             <p className="mt-4 max-w-2xl text-sm leading-relaxed text-foreground/60">
               Cada bloque tiene una responsabilidad acotada. El motor procesa los eventos en tiempo real; la interfaz recibe un estado determinista para mantenerse fluida sin perder el ritmo institucional.
             </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
             {pipelineSteps.map((step, i) => (
                <div key={step.title} className="nx-panel p-6 bg-background flex flex-col border border-foreground/5 hover:border-foreground/30 transition-colors">
                  <div className="font-mono text-[10px] text-foreground/30 mb-4">{String(i+1).padStart(2, '0')}</div>
                  <h3 className="font-bold text-sm mb-2">{step.title}</h3>
                  <p className="text-xs text-foreground/60 leading-relaxed">{step.desc}</p>
                </div>
             ))}
          </div>
        </div>
      </section>

      {/* MATH MODELS */}
      <section className="border-b nx-hairline bg-background">
        <div className="mx-auto max-w-[1400px] px-6 py-24">
          <div className="mb-12">
             <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">04 — Modelos Matemáticos</span>
             <h2 className="mt-4 max-w-2xl text-4xl font-bold tracking-tight md:text-4xl">
               Variables observables, decisiones auditables
             </h2>
             <p className="mt-4 max-w-2xl text-sm leading-relaxed text-foreground/60">
               Todas las decisiones de NobaTrade pueden ser rastreadas hasta sus componentes matemáticos fundamentales, evaluados a 200 iteraciones por segundo.
             </p>
          </div>
          
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
             {mathModels.map((m) => (
                <div key={m.title} className="nx-panel p-6 bg-surface flex flex-col justify-between border border-foreground/5 hover:border-foreground/20 transition-colors">
                  <h3 className="font-bold text-sm mb-4">{m.title}</h3>
                  <div className="bg-[#0f0f10] border nx-hairline p-4 font-mono text-[11px] text-profit overflow-x-auto whitespace-pre">
                    {m.formula}
                  </div>
                  <p className="mt-4 text-xs text-foreground/60 leading-relaxed">{m.desc}</p>
                </div>
             ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="border-b nx-hairline">
        <div className="mx-auto max-w-[1400px] px-6 py-24 text-center">
          <h2 className="mx-auto max-w-3xl text-5xl font-bold leading-tight tracking-tight md:text-6xl">
            Abre el terminal.
            <br />
            <span className="italic font-light opacity-60">Observa el motor trabajar.</span>
          </h2>
          <button
            onClick={onLaunch}
            className="mt-10 inline-flex h-14 items-center gap-3 bg-foreground px-8 text-xs font-bold uppercase tracking-widest text-background hover:opacity-90 cursor-pointer"
          >
            <TerminalIcon className="h-4 w-4" />
            Lanzar NobaTrade Terminal
            <ArrowUpRight className="h-4 w-4" />
          </button>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-2 px-6 py-6">
        <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/30">
          © {new Date().getFullYear()} NobaTrade · Datos simulados
        </span>
        <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/30">
          v4.2.1 · versión estable
        </span>
      </footer>
    </div>
  );
}

function Stat({ k, v }: { k: string; v: string }) {
  return (
    <div>
      <div className="font-mono text-[9px] uppercase tracking-widest text-foreground/40">{k}</div>
      <div className="nx-num mt-1 text-sm font-bold">{v}</div>
    </div>
  );
}

const features = [
  { icon: Zap, title: "Evaluación Sub-segundo", body: "Los libros de órdenes son normalizados y escaneados a más de 200Hz. Las oportunidades se detectan con una latencia de extremo a extremo de apenas 42ms." },
  { icon: Shield, title: "Cortocircuito Multi-etapa", body: "Anomalías de spread, retrasos de datos y degradación del exchange tienen sus propios umbrales de desconexión por seguridad (Circuit Breakers)." },
  { icon: GitBranch, title: "Rutas Directas y Triangulares", body: "Estrategias de arbitraje entre diferentes exchanges (espacial) e internamente (triangular) evaluadas simultáneamente." },
  { icon: Activity, title: "Ejecución Determinista", body: "Mismos datos, misma configuración, misma decisión. Cada trade es 100% reproducible desde el historial (Replay Engine)." },
];

const pipelineSteps = [
  { title: "Feeds públicos", desc: "Cinco conectores envían order books por WebSockets. El motor evalúa de inmediato si un feed pierde frescura o sincronía." },
  { title: "Integridad (L2)", desc: "Los libros ensamblados vigilan sequence gaps. Cada Exchange se valida estrictamente antes de admitir sus precios al escáner de oportunidades." },
  { title: "Quote normalization", desc: "Pares como BTC/USD y BTC/USDT se convierten a USD comparable usando el basis USDT/USD en tiempo real sin perder spread." },
  { title: "Detección Espacial", desc: "El motor cruza transversalmente el Ask más bajo contra el Bid más alto en toda la malla de exchanges conectados para identificar spreads brutos." },
  { title: "Economía real", desc: "Ejecución spot y rebalanceo se calculan por separado: fees maker/taker, network slippage, cuota límite, latencia y retiro amortizado." },
  { title: "Expected Value", desc: "El motor pondera la probabilidad de que ambas piernas del arbitraje logren ejecutarse, calculando el ratio de sobrevivencia de la operación." },
  { title: "Inventario (Preflight)", desc: "Antes de enviar a la queue, se verifica frescura y disponibilidad del inventario en las billeteras simuladas para cubrir ambas partes de la operación." },
  { title: "State machine", desc: "Cada señal comienza una traza: detected, validated, reserved, leg A, leg B y executed. Se actualiza en la interfaz de usuario en streaming." },
];

const mathModels = [
  { title: "Gross Spread Edge", formula: "Gross_Edge = max(Bid_B - Ask_A, 0)", desc: "Mide la diferencia cruda entre comprar barato en el Exchange A y vender caro en el Exchange B. Si es 0 o negativo, no hay oportunidad." },
  { title: "Execution Net P&L", formula: "Net_PnL = (Vol * Gross_Edge) - (Vol * Fee_A) - (Vol * Fee_B)", desc: "Separa el resultado operativo bruto del costo de red. Esta es la ganancia limpia (en USD) después de pagarle a ambos exchanges su porción maker/taker." },
  { title: "Minimum ROI Threshold", formula: "if (Net_PnL < Config.minProfitUsd)\n  return REJECTED_FEES", desc: "Ajuste dinámico controlado por el usuario desde el Cockpit. Solo ejecuta si el beneficio de la operación supera el umbral preestablecido en USD." },
  { title: "Rebalance Trigger", formula: "if (Wallet_A(BTC) < Threshold)\n  execute_rebalance()", desc: "Permite balancear el inventario de las billeteras automáticamente cuando una de ellas se drena después de ejecutar múltiples operaciones ganadoras en una sola dirección." },
  { title: "Triangular Arbitrage Cycle", formula: "Final_USDT = (Init_USDT / P1_Ask) * P2_Bid * P3_Bid", desc: "Calcula el retorno de invertir USDT en BTC, luego ETH, y de vuelta a USDT dentro del mismo exchange. Si Final_USDT > Init_USDT + Fees, ejecuta." },
  { title: "Circuit Breaker Latency", formula: "if (Current_Time - Last_Update > 500ms)\n  trigger_trip(DEGRADED)", desc: "Cortocircuito de seguridad: si un exchange no emite ticks en 500ms, se asume latencia de red y el motor interrumpe operaciones para evitar riesgo estructural." },
];

function sampleTicks(seed: number) {
  const base = [
    { pair: "BTC/USDT", venue: "binance" },
    { pair: "ETH/USDT", venue: "kraken" },
    { pair: "ETH/BTC", venue: "coinbase" },
    { pair: "BTC/USDT", venue: "kraken" },
    { pair: "ETH/USDT", venue: "binance" },
  ];
  return base.map((b, i) => ({
    ...b,
    delta: Math.sin((seed + i) * 1.7) * 0.35 + Math.cos((seed + i) * 0.9) * 0.18,
  }));
}
