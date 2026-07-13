'use client'

import { useEffect, useState } from "react";
import { ArrowUpRight, Activity, Zap, Shield, GitBranch, Terminal as TerminalIcon, Coins, RefreshCw, Cpu, Database, Award, CheckCircle2 } from "lucide-react";

export function LandingPage({ onLaunch }: { onLaunch: () => void }) {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 1500);
    return () => clearInterval(id);
  }, []);

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* HERO SECTION */}
      <section className="relative border-b nx-hairline overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-profit/5 via-transparent to-purple-500/5 pointer-events-none" />
        <div className="mx-auto grid max-w-[1400px] grid-cols-12 gap-8 px-6 py-20 lg:py-28 relative z-10">
          <div className="col-span-12 lg:col-span-8 space-y-6">
            <div className="flex flex-wrap items-center gap-3 font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/60">
              <span className="flex items-center gap-1.5 bg-profit/15 text-profit border border-profit/30 px-2.5 py-1 rounded-sm font-bold">
                <span className="inline-block h-1.5 w-1.5 animate-pulse bg-profit rounded-full" />
                MOTOR v4.2 ACTIVO
              </span>
              <span className="bg-purple-500/15 text-purple-300 border border-purple-500/30 px-2.5 py-1 rounded-sm font-bold">
                🤖 CO-PILOTO IA ACTIVO
              </span>
              <span className="bg-blue-500/15 text-blue-300 border border-blue-500/30 px-2.5 py-1 rounded-sm font-bold">
                ☁️ SUPABASE POSTGRESQL
              </span>
            </div>
            
            <h1 className="text-5xl font-extrabold leading-[0.95] tracking-tight md:text-7xl lg:text-[5.5rem]">
              Arbitraje HFT con
              <br />
              <span className="italic font-light text-profit">inteligencia artificial</span>
              <br />
              & 5 exchanges en vivo.
            </h1>
            
            <p className="max-w-2xl text-base leading-relaxed text-foreground/70">
              NobaTrade centraliza libros de órdenes en sub-milisegundos desde <strong className="text-white font-bold">Binance, Kraken, Coinbase Pro, Bitfinex y OKX</strong>. Evalúa miles de rutas cruzadas y triangulares por segundo usando <strong className="text-profit">Virtual Threads (Loom)</strong>, verificando anomalías de mercado con su Co-Piloto IA y registrando contabilidad estricta en <strong className="text-blue-400">Supabase DB</strong>.
            </p>
            
            <div className="pt-4 flex flex-wrap items-center gap-4">
              <button
                onClick={onLaunch}
                className="group flex h-13 items-center gap-3 bg-profit px-8 text-xs font-extrabold uppercase tracking-widest text-black transition hover:bg-profit/90 shadow-lg shadow-profit/20 rounded-sm cursor-pointer"
              >
                <TerminalIcon className="h-4 w-4" />
                Abrir Terminal en Vivo
                <ArrowUpRight className="h-4 w-4 transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
              </button>
              <a
                href="#exchanges"
                className="flex h-13 items-center gap-3 border border-white/20 px-6 text-xs font-bold uppercase tracking-widest text-foreground transition hover:bg-white/5 rounded-sm"
              >
                Explorar Arquitectura v4.2
              </a>
            </div>
          </div>

          <aside className="col-span-12 lg:col-span-4">
            <div className="nx-panel h-full p-5 flex flex-col justify-between border-profit/30 bg-gradient-to-b from-white/4 to-transparent">
              <div>
                <div className="flex items-center justify-between border-b nx-hairline pb-3">
                  <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/50 font-bold">Feed Multi-Exchange</span>
                  <span className="font-mono text-[10px] text-profit font-bold flex items-center gap-1">
                    <span className="h-2 w-2 rounded-full bg-profit animate-ping" /> 5 EXCHANGES SYNC
                  </span>
                </div>
                <ul className="mt-4 space-y-3 font-mono text-[11px]">
                  {sampleTicks(tick).map((t, i) => (
                    <li key={i} className="flex items-center justify-between bg-white/3 px-2.5 py-1.5 rounded">
                      <span className="text-foreground font-bold">{t.pair}</span>
                      <span className="text-foreground/70 uppercase text-[9px] bg-white/10 px-1.5 py-0.5 rounded">{t.venue}</span>
                      <span className={`font-bold ${t.delta >= 0 ? "text-profit" : "text-loss"}`}>
                        {t.delta >= 0 ? "+" : ""}
                        {t.delta.toFixed(3)}%
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
              <div className="mt-5 grid grid-cols-3 gap-2 border-t nx-hairline pt-4 text-center">
                <Stat k="Loom Lat" v="0-2ms" />
                <Stat k="AI Score" v="88/100" />
                <Stat k="Exchanges" v="5 ACTIVOS" />
              </div>
            </div>
          </aside>
        </div>
      </section>

      {/* INSTITUTIONAL KPI BAR */}
      <section id="metrics" className="border-b nx-hairline bg-surface/50 backdrop-blur">
        <div className="mx-auto grid max-w-[1400px] grid-cols-2 divide-x divide-y nx-hairline md:grid-cols-4 md:divide-y-0">
          {[
            { k: "Conectores HFT Activos", v: "5 Instituciones", desc: "Binance, Kraken, Coinbase, Bitfinex, OKX" },
            { k: "Motor Concurrente", v: "Virtual Threads", desc: "Java 21 Project Loom (0-2ms ejecución)" },
            { k: "Inteligencia Artificial", v: "Z-Score + Scorer", desc: "Filtro anti-spoofing y score 0-100 en tiempo real" },
            { k: "Single Source of Truth", v: "Supabase DB", desc: "PostgreSQL en nube con auditoría P&L real" },
          ].map((m) => (
            <div key={m.k} className="px-6 py-6 border-b md:border-b-0 nx-hairline">
              <div className="font-mono text-[10px] uppercase tracking-[0.2em] text-profit font-bold">{m.k}</div>
              <div className="mt-2 text-2xl font-extrabold tracking-tight text-white">{m.v}</div>
              <div className="mt-1 text-[11px] text-foreground/50 font-mono">{m.desc}</div>
            </div>
          ))}
        </div>
      </section>

      {/* EXCHANGES & CONNECTORS HIGHLIGHT */}
      <section id="exchanges" className="border-b nx-hairline bg-background">
        <div className="mx-auto max-w-[1400px] px-6 py-20">
          <div className="max-w-3xl mb-12">
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-profit font-bold">01 — Conectividad Institucional</span>
            <h2 className="mt-3 text-4xl font-extrabold tracking-tight">5 Exchanges Globales Conectados en Tiempo Real</h2>
            <p className="mt-4 text-sm leading-relaxed text-foreground/60">
              NobaTrade v4.2 no se limita a un par de mercados. Ingiere libros de órdenes de 5 instituciones líderes mediante WebSockets y APIs REST normalizadas, calculando 20 rutas direccionales por ciclo de reloj.
            </p>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
            {[
              { name: "BINANCE", type: "WebSocket + REST", pairs: "BTC/USDT, ETH/BTC, ETH/USDT", status: "ONLINE" },
              { name: "KRAKEN", type: "REST L2 Normalizer", pairs: "BTC/USDT", status: "ONLINE" },
              { name: "COINBASE PRO", type: "REST L2 Normalizer", pairs: "BTC/USDT", status: "ONLINE" },
              { name: "BITFINEX", type: "REST API v1", pairs: "BTC/USD Normalizado", status: "ONLINE" },
              { name: "OKX", type: "REST Market Books", pairs: "BTC/USDT", status: "ONLINE" },
            ].map((ex) => (
              <div key={ex.name} className="nx-panel p-5 border border-white/10 hover:border-profit/40 transition-all space-y-3 bg-white/2">
                <div className="flex items-center justify-between">
                  <span className="font-black text-sm tracking-wider text-white">{ex.name}</span>
                  <span className="bg-profit/20 text-profit text-[9px] font-mono font-bold px-2 py-0.5 rounded flex items-center gap-1">
                    ● {ex.status}
                  </span>
                </div>
                <div className="text-[11px] font-mono text-foreground/50">{ex.type}</div>
                <div className="text-[10px] font-mono text-profit/80 border-t border-white/10 pt-2">{ex.pairs}</div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* AI CO-PILOT & QUANT STRATEGIES */}
      <section id="strategies" className="border-b nx-hairline bg-surface">
        <div className="mx-auto max-w-[1400px] px-6 py-20">
          <div className="max-w-3xl mb-12">
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-purple-400 font-bold">02 — Inteligencia Artificial & Estrategias</span>
            <h2 className="mt-3 text-4xl font-extrabold tracking-tight">Co-Piloto IA + Arbitraje Espacial y Triangular</h2>
            <p className="mt-4 text-sm leading-relaxed text-foreground/60">
              Cada oportunidad detectada es filtrada por nuestro motor de Inteligencia Artificial para confirmar que el spread no sea una ilusión de baja liquidez o manipulación (*spoofing*).
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            <div className="nx-panel p-7 border border-purple-500/30 bg-purple-950/10 space-y-4">
              <div className="flex items-center gap-2 text-purple-400 font-bold text-lg">
                <Cpu className="h-6 w-6" />
                Co-Piloto IA (Confidence Scorer)
              </div>
              <p className="text-foreground/70 text-xs leading-relaxed">
                Asigna una puntuación multidimensional de <strong>0 a 100</strong> a cada operación. Evalúa el slippage del VWAP, el margen después de comisiones, la volatilidad y la desviación estadística rodante (<strong>Anomaly Detector Z-Score</strong>).
              </p>
              <div className="bg-black/40 p-3.5 rounded border border-purple-500/20 text-[11px] font-mono text-purple-300">
                🤖 AI Confidence: 88/100 ✓ Anomaly Verified
              </div>
            </div>

            <div className="nx-panel p-7 border border-profit/30 bg-profit/5 space-y-4">
              <div className="flex items-center gap-2 text-profit font-bold text-lg">
                <GitBranch className="h-6 w-6" />
                Arbitraje Inter-Exchange (Spatial)
              </div>
              <p className="text-foreground/70 text-xs leading-relaxed">
                Compara en microsegundos las 20 combinaciones direccionales entre los 5 exchanges. Compra el mejor Ask en un exchange y vende el mejor Bid en otro, deduciendo de manera exacta comisiones Taker/Maker y retiro de red.
              </p>
              <div className="bg-black/40 p-3.5 rounded border border-profit/20 text-[11px] font-mono text-profit">
                Buy @ Binance Ask ➔ Sell @ Kraken Bid (+Spread)
              </div>
            </div>

            <div className="nx-panel p-7 border border-blue-500/30 bg-blue-950/10 space-y-4">
              <div className="flex items-center gap-2 text-blue-400 font-bold text-lg">
                <Activity className="h-6 w-6" />
                Arbitraje Triangular (Intra-Exchange)
              </div>
              <p className="text-foreground/70 text-xs leading-relaxed">
                Explota desequilibrios entre tres pares dentro de Binance: <strong>USDT ➔ BTC ➔ ETH ➔ USDT</strong>. Captura beneficios en milisegundos sin necesidad de transferir fondos en blockchain ni asumir esperas de red.
              </p>
              <div className="bg-black/40 p-3.5 rounded border border-blue-500/20 text-[11px] font-mono text-blue-300">
                USDT ➔ BTC/USDT ➔ ETH/BTC ➔ ETH/USDT
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* CLOUD PERSISTENCE & LOOM THREADS */}
      <section className="border-b nx-hairline bg-background">
        <div className="mx-auto grid max-w-[1400px] grid-cols-12 gap-8 px-6 py-20">
          <div className="col-span-12 lg:col-span-5 space-y-4">
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-blue-400 font-bold">03 — Arquitectura Cloud & Concurrencia</span>
            <h2 className="text-3xl font-extrabold tracking-tight">
              Virtual Threads (Loom) + Supabase PostgreSQL
            </h2>
            <p className="text-sm leading-relaxed text-foreground/60">
              El backend aprovecha la concurrencia masiva de <strong>Java 21 Virtual Threads</strong> para evaluar de forma independiente cada par sin colas de bloqueo. Las ejecuciones e historiales se persisten en <strong>Supabase Cloud DB</strong> para una auditoría institucional inmutable.
            </p>
            <div className="pt-2">
              <button
                onClick={onLaunch}
                className="inline-flex items-center gap-2 bg-foreground text-background px-6 py-3 rounded-sm text-xs font-bold uppercase tracking-widest hover:opacity-90 cursor-pointer"
              >
                Acceder al Cockpit de Control <ArrowUpRight className="h-4 w-4" />
              </button>
            </div>
          </div>

          <div className="col-span-12 lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-4">
            {features.map((f) => (
              <div key={f.title} className="nx-panel p-6 bg-white/2 border border-white/10 hover:border-white/30 transition">
                <f.icon className="h-6 w-6 text-profit mb-3" />
                <h3 className="text-sm font-bold text-white">{f.title}</h3>
                <p className="mt-2 text-xs leading-relaxed text-foreground/60">{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* MATH & PRECISE BIGDECIMAL */}
      <section className="border-b nx-hairline bg-surface">
        <div className="mx-auto max-w-[1400px] px-6 py-20">
          <div className="mb-10">
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-profit font-bold">04 — Precisión Financiera Estricta</span>
            <h2 className="mt-2 text-3xl font-extrabold tracking-tight">
              Modelos Matemáticos en BigDecimal (0% Error IEEE 754)
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {mathModels.map((m) => (
              <div key={m.title} className="nx-panel p-5 bg-background border border-white/10 flex flex-col justify-between">
                <div>
                  <h3 className="font-bold text-sm text-white mb-3">{m.title}</h3>
                  <div className="bg-black/50 border border-white/10 p-3 rounded font-mono text-[11px] text-profit overflow-x-auto whitespace-pre">
                    {m.formula}
                  </div>
                </div>
                <p className="mt-3 text-xs text-foreground/60 leading-relaxed">{m.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CALL TO ACTION */}
      <section className="border-b nx-hairline bg-gradient-to-b from-background to-surface">
        <div className="mx-auto max-w-[1400px] px-6 py-24 text-center space-y-6">
          <h2 className="mx-auto max-w-3xl text-4xl sm:text-5xl font-extrabold leading-tight tracking-tight">
            Listo para auditar el mercado en tiempo real.
          </h2>
          <p className="text-foreground/60 text-sm max-w-xl mx-auto">
            Abre el Cockpit institucional con velas en vivo de TradingView, P&L auditado por Supabase y el Co-Piloto IA clasificando cada operación.
          </p>
          <div className="pt-4">
            <button
              onClick={onLaunch}
              className="inline-flex h-14 items-center gap-3 bg-profit px-8 text-xs font-extrabold uppercase tracking-widest text-black hover:bg-profit/90 shadow-xl shadow-profit/20 rounded-sm cursor-pointer"
            >
              <TerminalIcon className="h-4 w-4" />
              Lanzar NobaTrade v4.2 Terminal
              <ArrowUpRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-4 px-6 py-8">
        <div className="font-mono text-[11px] text-foreground/40 font-bold">
          © {new Date().getFullYear()} NobaTrade v4.2 · CODING_CHALLENGE_MEXICO
        </div>
        <div className="flex items-center gap-4 font-mono text-[10px] text-foreground/50">
          <span>Java 21 Loom</span>
          <span>•</span>
          <span>Next.js 14</span>
          <span>•</span>
          <span>Supabase DB</span>
          <span>•</span>
          <span>TradingView v4</span>
        </div>
      </footer>
    </div>
  );
}

function Stat({ k, v }: { k: string; v: string }) {
  return (
    <div>
      <div className="font-mono text-[9px] uppercase tracking-widest text-foreground/40">{k}</div>
      <div className="nx-num mt-1 text-sm font-bold text-white">{v}</div>
    </div>
  );
}

const features = [
  { icon: Zap, title: "Evaluación Sub-milisegundo (Loom)", body: "Los 5 libros de órdenes se normalizan y evalúan utilizando Virtual Threads de Java 21, logrando una latencia de decisión de apenas 0 a 2ms." },
  { icon: Shield, title: "Cortocircuito Institucional", body: "Circuit Breakers protegen la cartera ante pérdidas consecutivas, caídas abruptas de saldo (drawdown) o datos obsoletos del exchange." },
  { icon: Database, title: "Persistencia Cloud Supabase", body: "Las operaciones ejecutadas y el historial de balances se guardan en PostgreSQL en la nube, garantizando contabilidad real y continua." },
  { icon: Activity, title: "Rebalanceo de Inventario", body: "Supervisa la asimetría entre billeteras. Si una cartera supera el 40% de desviación respecto al saldo medio, dispara eventos de rebalanceo." },
];

const mathModels = [
  { title: "Gross Spread Edge", formula: "Gross_Edge = max(Bid_B - Ask_A, 0)", desc: "Mide la diferencia cruda entre comprar barato en el Exchange A y vender caro en el Exchange B con precios normalizados en tiempo real." },
  { title: "Net Profit (After All Fees)", formula: "Net_PnL = (Vol * Gross_Edge) - Fees_Buy - Fees_Sell - Network_Fee", desc: "Calcula el beneficio limpio deducidas las comisiones Taker/Maker exactas de cada uno de los 5 exchanges y la tarifa de retiro de red." },
  { title: "AI Confidence Scorer", formula: "Score = w1*Slippage + w2*Spread + w3*Volat + w4*ZScore", desc: "El Co-Piloto evalúa la calidad del spread en una escala de 0 a 100 y comprueba anomalías estadísticas rodantes antes de ejecutar." },
  { title: "Triangular Intra-Exchange Edge", formula: "Final_USDT = (Init_USDT / P1_Ask) * P2_Bid * P3_Bid", desc: "Evalúa el ciclo USDT ➔ BTC ➔ ETH ➔ USDT en Binance. Si Final_USDT supera el capital inicial + comisiones, ejecuta instantáneamente." },
  { title: "Rebalance Trigger Threshold", formula: "if (Deviation(Wallet_i) > 40.0%)\n  execute_rebalance()", desc: "Protege contra el vaciamiento de inventario en un exchange individual tras una secuencia continua de arbitrajes rentables." },
  { title: "Circuit Breaker Protection", formula: "if (ConsecutiveLosses >= 3 || Drawdown > 2.0%)\n  pause_engine(60s)", desc: "Pausa automáticamente el motor de arbitraje para proteger el capital ante volatilidades extremas o flash crashes del mercado." },
];

function sampleTicks(seed: number) {
  const base = [
    { pair: "BTC/USDT", venue: "binance" },
    { pair: "BTC/USDT", venue: "okx" },
    { pair: "BTC/USDT", venue: "bitfinex" },
    { pair: "BTC/USDT", venue: "coinbase" },
    { pair: "BTC/USDT", venue: "kraken" },
  ];
  return base.map((b, i) => ({
    ...b,
    delta: Math.sin((seed + i) * 1.7) * 0.45 + Math.cos((seed + i) * 0.9) * 0.2,
  }));
}

