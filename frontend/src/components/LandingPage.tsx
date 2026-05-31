'use client'

import { useEffect, useState } from "react";
import { ArrowUpRight, Activity, Zap, Shield, GitBranch, Terminal as TerminalIcon } from "lucide-react";

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
              <span>System Live · Engine v4.2.1</span>
            </div>
            <h1 className="mt-6 text-5xl font-bold leading-[0.95] tracking-tight md:text-7xl lg:text-[5.5rem]">
              Arbitrage at
              <br />
              <span className="italic font-light opacity-60">institutional</span>
              <br />
              latency.
            </h1>
            <p className="mt-8 max-w-xl text-base leading-relaxed text-foreground/60">
              NexusTrade aggregates order books across Binance, Kraken and Coinbase,
              evaluates 200+ opportunities per second and executes deterministic
              cross-venue arbitrage behind a multi-stage circuit breaker.
            </p>
            <div className="mt-10 flex flex-wrap items-center gap-3">
              <button
                onClick={onLaunch}
                className="group flex h-12 items-center gap-3 bg-foreground px-6 text-xs font-bold uppercase tracking-widest text-background transition hover:opacity-90"
              >
                Open Live Terminal
                <ArrowUpRight className="h-4 w-4 transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
              </button>
              <a
                href="#engine"
                className="flex h-12 items-center gap-3 border border-foreground/25 px-6 text-xs font-bold uppercase tracking-widest text-foreground transition hover:bg-surface"
              >
                Read the spec
              </a>
            </div>
          </div>

          <aside className="col-span-12 lg:col-span-4">
            <div className="nx-panel h-full p-5">
              <div className="flex items-center justify-between border-b nx-hairline pb-3">
                <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/40">Live Feed</span>
                <span className="font-mono text-[10px] text-profit">● streaming</span>
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
            { k: "Evaluations / sec", v: "214" },
            { k: "Median latency", v: "42ms" },
            { k: "Venues integrated", v: "3" },
            { k: "24h opportunities", v: "1,842" },
          ].map((m) => (
            <div key={m.k} className="px-6 py-8 border-b md:border-b-0 nx-hairline">
              <div className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/40">{m.k}</div>
              <div className="mt-3 text-3xl font-bold tracking-tight">{m.v}</div>
            </div>
          ))}
        </div>
      </section>

      {/* ENGINE */}
      <section id="engine" className="border-b nx-hairline">
        <div className="mx-auto grid max-w-[1400px] grid-cols-12 gap-6 px-6 py-24">
          <div className="col-span-12 lg:col-span-4">
            <span className="font-mono text-[10px] uppercase tracking-[0.3em] text-foreground/40">01 — Engine</span>
            <h2 className="mt-4 text-4xl font-bold tracking-tight">
              Deterministic by design.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-foreground/60">
              Every evaluation is reproducible. Same book, same configuration,
              same decision — every time.
            </p>
          </div>
          <div className="col-span-12 grid grid-cols-1 gap-px bg-border lg:col-span-8 md:grid-cols-2">
            {features.map((f) => (
              <div key={f.title} className="bg-background p-8">
                <f.icon className="h-5 w-5 text-foreground/70" />
                <h3 className="mt-5 text-base font-semibold">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-foreground/55">{f.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="border-b nx-hairline">
        <div className="mx-auto max-w-[1400px] px-6 py-24 text-center">
          <h2 className="mx-auto max-w-3xl text-5xl font-bold leading-tight tracking-tight md:text-6xl">
            Open the terminal.
            <br />
            <span className="italic font-light opacity-60">Watch the engine work.</span>
          </h2>
          <button
            onClick={onLaunch}
            className="mt-10 inline-flex h-14 items-center gap-3 bg-foreground px-8 text-xs font-bold uppercase tracking-widest text-background hover:opacity-90 cursor-pointer"
          >
            <TerminalIcon className="h-4 w-4" />
            Launch NexusTrade Terminal
            <ArrowUpRight className="h-4 w-4" />
          </button>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="mx-auto flex max-w-[1400px] flex-wrap items-center justify-between gap-2 px-6 py-6">
        <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/30">
          © {new Date().getFullYear()} NexusTrade · Simulated data
        </span>
        <span className="font-mono text-[10px] uppercase tracking-[0.25em] text-foreground/30">
          v4.2.1 · build stable
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
  { icon: Zap, title: "Sub-second evaluation", body: "Books normalized and scanned at 200+ Hz. Opportunities surface with a 42ms median end-to-end latency." },
  { icon: Shield, title: "Multi-stage circuit breaker", body: "Spread anomalies, stale feeds and venue degradation each have their own trip thresholds and cool-downs." },
  { icon: GitBranch, title: "Triangular & direct routes", body: "Two-leg cross-venue and three-leg intra-venue strategies evaluated simultaneously." },
  { icon: Activity, title: "Deterministic execution", body: "Same book, same config, same decision. Every trade is reproducible from the journal." },
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
