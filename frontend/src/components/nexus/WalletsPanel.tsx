import { INITIAL_WALLETS } from "@/lib/nexus/types";
import { EXCHANGE_META } from "./exchange";
import { SectionTitle } from "./OrderBooks";

interface Props {
  wallets: Record<string, { totalUsd: number; btcBalance: number }>;
}

export function WalletsPanel({ wallets }: Props) {
  return (
    <section className="space-y-3">
      <SectionTitle title="Wallet Simulation" sub="Shadow Mode" />
      <div className="grid gap-3 sm:grid-cols-3">
        {Object.entries(wallets).map(([ex, w]) => {
          const init = INITIAL_WALLETS[ex];
          const delta = w.totalUsd - init.totalUsd;
          const meta = EXCHANGE_META[ex];
          return (
            <div key={ex} className="nx-panel p-3">
              <div className="mb-2 flex items-center gap-2">
                <span className={`h-1.5 w-1.5 rounded-full ${meta.dot}`} />
                <span className="text-[10px] font-bold uppercase tracking-wider text-foreground/60">
                  {meta.name.toUpperCase()}
                </span>
              </div>
              <div className="flex items-baseline justify-between">
                <div>
                  <div className="text-[9px] uppercase tracking-wider text-foreground/30">USDT</div>
                  <div className="nx-num text-base font-bold tracking-tight text-foreground">
                    {w.totalUsd.toFixed(2)}
                  </div>
                  <div className={`nx-num text-[10px] ${delta >= 0 ? "text-profit" : "text-loss"}`}>
                    {delta >= 0 ? "+" : ""}
                    {delta.toFixed(2)}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-[9px] uppercase tracking-wider text-foreground/30">BTC</div>
                  <div className="nx-num text-base font-bold tracking-tight text-foreground">
                    {w.btcBalance.toFixed(4)}
                  </div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}