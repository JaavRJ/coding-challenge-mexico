import { useState } from "react";
import { EXCHANGE_META } from "./exchange";

interface Props {
  minProfitUsd: number;
  exchangesEnabled: Record<string, boolean>;
  onApply: (minProfit: number, enabled: Record<string, boolean>) => void;
  onFlashCrash: () => void;
}

export function Cockpit({ minProfitUsd, exchangesEnabled, onApply, onFlashCrash }: Props) {
  const [local, setLocal] = useState({ min: minProfitUsd, enabled: exchangesEnabled });
  const [dirty, setDirty] = useState(false);
  const [armed, setArmed] = useState(false);

  return (
    <div className="nx-panel space-y-4 p-4">
      <h3 className="text-[10px] font-bold uppercase tracking-[0.15em] text-foreground/40">
        Engine Cockpit
      </h3>

      <div className="space-y-1">
        <div className="flex justify-between text-[10px] text-foreground/60">
          <span className="uppercase tracking-wider">Min Profit Threshold</span>
          <span className="nx-num text-profit">${local.min.toFixed(2)} USD</span>
        </div>
        <input
          type="range"
          min={0}
          max={50}
          step={0.5}
          value={local.min}
          onChange={(e) => {
            setLocal({ ...local, min: Number(e.target.value) });
            setDirty(true);
          }}
          className="h-1 w-full cursor-pointer appearance-none rounded-lg bg-foreground/10 accent-profit"
        />
      </div>

      <div className="grid grid-cols-3 gap-2">
        {Object.entries(local.enabled).map(([ex, on]) => {
          const m = EXCHANGE_META[ex];
          return (
            <button
              key={ex}
              onClick={() => {
                setLocal({ ...local, enabled: { ...local.enabled, [ex]: !on } });
                setDirty(true);
              }}
              className={`px-2 py-2 text-[9px] font-bold uppercase tracking-wider transition ${
                on
                  ? `${m.border} ${m.bg} ${m.color} border`
                  : "border border-foreground/10 text-foreground/30 hover:bg-foreground/5"
              }`}
            >
              {m.name}
            </button>
          );
        })}
      </div>

      <button
        onClick={() => {
          onApply(local.min, local.enabled);
          setDirty(false);
        }}
        disabled={!dirty}
        className="w-full bg-foreground py-3 text-[10px] font-black uppercase tracking-[0.2em] text-background transition hover:bg-foreground/90 disabled:cursor-not-allowed disabled:opacity-30"
      >
        Apply Sync
      </button>

      {!armed ? (
        <button
          onClick={() => setArmed(true)}
          className="w-full border-2 border-dashed border-loss/30 py-2 text-[10px] font-bold uppercase tracking-wider text-loss transition hover:bg-loss/5"
        >
          Stress Test: Execute Flash-Liquidity
        </button>
      ) : (
        <div className="grid grid-cols-2 gap-2">
          <button
            onClick={() => {
              onFlashCrash();
              setArmed(false);
            }}
            className="nx-breaker-alarm border border-loss py-2 text-[10px] font-black uppercase tracking-wider text-loss"
          >
            Confirm Trip
          </button>
          <button
            onClick={() => setArmed(false)}
            className="border border-foreground/10 py-2 text-[10px] font-bold uppercase tracking-wider text-foreground/50 hover:bg-foreground/5"
          >
            Cancel
          </button>
        </div>
      )}
    </div>
  );
}