import type { EngineStats } from "@/lib/nexus/types";

export function EnginePanel({ engine }: { engine: EngineStats }) {
  const paused = engine.circuitBreakerPauseMs > 0;
  const secs = Math.ceil(engine.circuitBreakerPauseMs / 1000);
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-2">
        <Stat label="Evaluations" value={engine.totalEvaluations.toLocaleString()} />
        <Stat label="Opportunities" value={engine.totalOpportunities.toLocaleString()} accent="text-warn" />
        <Stat label="Executed" value={engine.totalExecuted.toLocaleString()} accent="text-profit" />
        <Stat label="Rejected" value={engine.totalRejected.toLocaleString()} accent="text-loss" />
      </div>
      <div
        className={`flex items-center justify-between border p-3 ${
          paused ? "nx-breaker-alarm border-loss/40" : "border-profit/20 bg-profit/5"
        }`}
      >
        <div>
          <div className="text-[9px] uppercase tracking-widest text-foreground/40">Circuit Breaker</div>
          <div className={`text-xs font-bold uppercase tracking-wider ${paused ? "text-loss" : "text-profit"}`}>
            {paused ? `Tripped · Resuming in ${secs}s` : "Armed & Active"}
          </div>
        </div>
        <div className={`text-[10px] font-mono ${paused ? "text-loss" : "text-profit"}`}>
          {paused ? "◉ PAUSED" : "◉ ARMED"}
        </div>
      </div>
    </div>
  );
}

function Stat({ label, value, accent }: { label: string; value: string; accent?: string }) {
  return (
    <div className="nx-panel p-3">
      <div className="text-[9px] uppercase tracking-widest text-foreground/30">{label}</div>
      <div className={`nx-num text-lg font-medium ${accent ?? "text-foreground"}`}>{value}</div>
    </div>
  );
}