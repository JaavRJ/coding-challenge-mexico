"use client";
import { useState, useEffect } from "react";
import { Settings2, ChevronDown, ChevronUp, Save, RotateCcw, Zap } from "lucide-react";

interface EngineConfig {
  walletExposurePct: number;
  minRoiPct: number;
  decisionTimeoutMs: number;
  activeExchanges: string[];
  circuitBreakerLosses: number;
  circuitBreakerPauseSeconds: number;
  maxBalanceDrawdownPct: number;
  rebalanceThresholdPct: number;
  fees?: Record<string, { feeTaker: number; feeMaker: number; withdrawalFeeBtc: number }>;
}

interface Props {
  apiBase: string;
}

const SECTION = ({ title, children, defaultOpen = false }: { title: string; children: React.ReactNode; defaultOpen?: boolean }) => {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border border-foreground/10 rounded-sm">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-4 py-3 text-[10px] font-bold uppercase tracking-[0.15em] text-foreground/60 hover:text-foreground/90 transition"
      >
        {title}
        {open ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
      </button>
      {open && <div className="px-4 pb-4 space-y-4 border-t border-foreground/10">{children}</div>}
    </div>
  );
};

const SliderField = ({
  label, value, min, max, step, unit, onChange, description
}: {
  label: string; value: number; min: number; max: number; step: number;
  unit: string; onChange: (v: number) => void; description?: string;
}) => (
  <div className="space-y-1.5">
    <div className="flex justify-between text-[10px]">
      <span className="text-foreground/50 uppercase tracking-wider">{label}</span>
      <span className="text-profit font-mono font-bold">{value.toFixed(step < 1 ? 3 : 0)}{unit}</span>
    </div>
    <input
      type="range" min={min} max={max} step={step} value={value}
      onChange={e => onChange(Number(e.target.value))}
      className="h-1 w-full cursor-pointer appearance-none rounded-lg bg-foreground/10 accent-profit"
    />
    {description && <p className="text-[9px] text-foreground/30 leading-tight">{description}</p>}
  </div>
);

const NumericField = ({
  label, value, min, max, step, unit, onChange, description
}: {
  label: string; value: number; min: number; max: number; step: number;
  unit: string; onChange: (v: number) => void; description?: string;
}) => (
  <div className="space-y-1">
    <div className="flex items-center justify-between gap-3">
      <span className="text-[10px] text-foreground/50 uppercase tracking-wider flex-1">{label}</span>
      <div className="flex items-center gap-1">
        <input
          type="number" min={min} max={max} step={step} value={value}
          onChange={e => onChange(Number(e.target.value))}
          className="w-24 bg-surface-2 border border-foreground/15 px-2 py-1 text-[11px] font-mono text-foreground text-right rounded-sm focus:outline-none focus:border-profit/50"
        />
        <span className="text-[9px] text-foreground/40 w-10">{unit}</span>
      </div>
    </div>
    {description && <p className="text-[9px] text-foreground/30 leading-tight">{description}</p>}
  </div>
);

const EXCHANGES = ["binance", "kraken", "coinbase"];

export function AdvancedConfigPanel({ apiBase }: Props) {
  const [config, setConfig] = useState<EngineConfig | null>(null);
  const [draft, setDraft] = useState<Partial<EngineConfig> & { feeOverrides?: Record<string, any> }>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchConfig = async () => {
    try {
      const res = await fetch(`${apiBase}/api/config`);
      if (res.ok) {
        const data = await res.json();
        setConfig(data);
        setDraft({
          walletExposurePct: data.walletExposurePct,
          minRoiPct: data.minRoiPct,
          decisionTimeoutMs: data.decisionTimeoutMs,
          circuitBreakerLosses: data.circuitBreakerLosses ?? 3,
          circuitBreakerPauseSeconds: data.circuitBreakerPauseSeconds ?? 60,
          maxBalanceDrawdownPct: data.maxBalanceDrawdownPct ?? 2.0,
          rebalanceThresholdPct: data.rebalanceThresholdPct ?? 40.0,
          feeOverrides: data.fees ?? {
            binance: { feeTaker: 0.001, feeMaker: 0.001, withdrawalFeeBtc: 0.0005 },
            kraken: { feeTaker: 0.0026, feeMaker: 0.0016, withdrawalFeeBtc: 0.0002 },
            coinbase: { feeTaker: 0.006, feeMaker: 0.004, withdrawalFeeBtc: 0.0 },
          }
        });
      }
    } catch { setError("No se pudo conectar con el backend"); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchConfig(); }, []);

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const res = await fetch(`${apiBase}/api/config`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          walletExposurePct: draft.walletExposurePct,
          minRoiPct: draft.minRoiPct,
          decisionTimeoutMs: draft.decisionTimeoutMs,
          circuitBreakerLosses: draft.circuitBreakerLosses,
          circuitBreakerPauseSeconds: draft.circuitBreakerPauseSeconds,
          maxBalanceDrawdownPct: draft.maxBalanceDrawdownPct,
          rebalanceThresholdPct: draft.rebalanceThresholdPct,
          feeOverrides: draft.feeOverrides,
        }),
      });
      if (res.ok) {
        const updated = await res.json();
        setConfig(updated);
        setSaved(true);
        setTimeout(() => setSaved(false), 2000);
      } else {
        setError("Error al aplicar configuración");
      }
    } catch { setError("Error de conexión"); }
    finally { setSaving(false); }
  };

  const handleReset = () => {
    if (config) {
      setDraft({
        walletExposurePct: config.walletExposurePct,
        minRoiPct: config.minRoiPct,
        decisionTimeoutMs: config.decisionTimeoutMs,
        circuitBreakerLosses: config.circuitBreakerLosses ?? 3,
        circuitBreakerPauseSeconds: config.circuitBreakerPauseSeconds ?? 60,
        maxBalanceDrawdownPct: config.maxBalanceDrawdownPct ?? 2.0,
        rebalanceThresholdPct: config.rebalanceThresholdPct ?? 40.0,
        feeOverrides: config.fees,
      });
    }
  };

  const set = (key: keyof typeof draft, value: any) => setDraft(prev => ({ ...prev, [key]: value }));
  const setFee = (exchange: string, key: string, value: number) => setDraft(prev => ({
    ...prev,
    feeOverrides: {
      ...prev.feeOverrides,
      [exchange]: { ...(prev.feeOverrides?.[exchange] ?? {}), [key]: value }
    }
  }));

  if (loading) return (
    <div className="nx-panel p-5 flex items-center gap-2 text-foreground/30 text-[11px] font-mono">
      <span className="animate-pulse">Cargando configuración…</span>
    </div>
  );

  return (
    <div className="nx-panel p-5 space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-foreground/10 pb-3">
        <div className="flex items-center gap-2">
          <Settings2 size={14} className="text-blue-400" />
          <span className="text-[11px] font-bold uppercase tracking-[0.15em] text-foreground/70">
            Configuración Avanzada del Motor
          </span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleReset}
            className="flex items-center gap-1 px-2 py-1 text-[9px] uppercase tracking-wider border border-foreground/15 text-foreground/40 hover:text-foreground/70 transition rounded-sm"
          >
            <RotateCcw size={9} /> Reset
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className={`flex items-center gap-1 px-3 py-1.5 text-[9px] font-bold uppercase tracking-wider transition rounded-sm ${
              saved ? "bg-profit/20 text-profit border border-profit/40" :
              "bg-blue-500/20 text-blue-300 border border-blue-500/30 hover:bg-blue-500/30"
            }`}
          >
            {saving ? <span className="animate-spin">⟳</span> : <Save size={9} />}
            {saved ? "✓ Aplicado" : "Aplicar Cambios"}
          </button>
        </div>
      </div>

      {error && (
        <div className="text-[10px] text-loss bg-loss/10 border border-loss/20 px-3 py-2 rounded-sm">
          {error}
        </div>
      )}

      {/* Motor Principal */}
      <SECTION title="⚙ Motor de Evaluación" defaultOpen>
        <div className="pt-3 space-y-5">
          <SliderField
            label="Exposición de Cartera"
            value={draft.walletExposurePct ?? 100}
            min={5} max={100} step={5} unit="%"
            onChange={v => set("walletExposurePct", v)}
            description="Porcentaje del balance disponible que el motor puede usar en cada operación."
          />
          <SliderField
            label="ROI Mínimo Requerido"
            value={(draft.minRoiPct ?? 0.01) * 100}
            min={0.001} max={1.0} step={0.001} unit="%"
            onChange={v => set("minRoiPct", v / 100)}
            description="Retorno mínimo sobre la inversión para que una oportunidad sea ejecutada."
          />
          <NumericField
            label="Timeout de Decisión"
            value={draft.decisionTimeoutMs ?? 200}
            min={50} max={2000} step={50} unit="ms"
            onChange={v => set("decisionTimeoutMs", v)}
            description="Latencia máxima permitida. Oportunidades que superen este límite son descartadas."
          />
        </div>
      </SECTION>

      {/* Circuit Breaker */}
      <SECTION title="⚡ Circuit Breaker">
        <div className="pt-3 space-y-5">
          <div className="flex items-center gap-2 mb-2 text-[9px] text-loss/70 bg-loss/5 border border-loss/15 px-3 py-2 rounded-sm">
            <Zap size={10} className="text-loss" />
            El circuit breaker detiene el motor automáticamente ante pérdidas consecutivas.
          </div>
          <NumericField
            label="Pérdidas para Activar"
            value={draft.circuitBreakerLosses ?? 3}
            min={1} max={20} step={1} unit="trades"
            onChange={v => set("circuitBreakerLosses", v)}
            description="Número de pérdidas consecutivas que activan el circuit breaker."
          />
          <NumericField
            label="Duración de la Pausa"
            value={draft.circuitBreakerPauseSeconds ?? 60}
            min={10} max={3600} step={10} unit="seg"
            onChange={v => set("circuitBreakerPauseSeconds", v)}
            description="Segundos que el motor permanece pausado después de que el breaker se activa."
          />
          <SliderField
            label="Drawdown Máximo de Cartera"
            value={draft.maxBalanceDrawdownPct ?? 2.0}
            min={0.5} max={20} step={0.5} unit="%"
            onChange={v => set("maxBalanceDrawdownPct", v)}
            description="Si el balance total cae más de este porcentaje, el motor se detiene."
          />
        </div>
      </SECTION>

      {/* Rebalanceo */}
      <SECTION title="⚖ Umbrales de Rebalanceo">
        <div className="pt-3 space-y-5">
          <SliderField
            label="Umbral de Asimetría para Rebalancear"
            value={draft.rebalanceThresholdPct ?? 40}
            min={5} max={80} step={5} unit="%"
            onChange={v => set("rebalanceThresholdPct", v)}
            description="Si la distribución de BTC entre exchanges difiere más de este porcentaje del promedio, se activa el rebalanceo automático."
          />
        </div>
      </SECTION>

      {/* Fees por Exchange */}
      <SECTION title="💱 Comisiones por Exchange">
        <div className="pt-3 space-y-6">
          <p className="text-[9px] text-foreground/30 leading-tight">
            Ajusta las comisiones reales de cada exchange. Estos valores afectan directamente el cálculo de rentabilidad neta.
          </p>
          {EXCHANGES.map(ex => (
            <div key={ex} className="space-y-3">
              <div className="text-[9px] font-bold uppercase tracking-widest text-foreground/50 border-b border-foreground/8 pb-1">
                {ex.toUpperCase()}
              </div>
              <div className="grid grid-cols-3 gap-3">
                {[
                  { key: "feeTaker", label: "Taker Fee", step: 0.0001 },
                  { key: "feeMaker", label: "Maker Fee", step: 0.0001 },
                  { key: "withdrawalFeeBtc", label: "Retiro BTC", step: 0.00001 },
                ].map(({ key, label, step }) => (
                  <div key={key} className="space-y-1">
                    <span className="text-[8px] text-foreground/40 uppercase tracking-wider block">{label}</span>
                    <input
                      type="number"
                      min={0} max={0.1} step={step}
                      value={draft.feeOverrides?.[ex]?.[key] ?? 0}
                      onChange={e => setFee(ex, key, Number(e.target.value))}
                      className="w-full bg-surface-2 border border-foreground/10 px-2 py-1 text-[10px] font-mono text-foreground rounded-sm focus:outline-none focus:border-profit/40"
                    />
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </SECTION>

      {/* Comparación Live vs Config */}
      {config && (
        <div className="text-[9px] text-foreground/25 border-t border-foreground/8 pt-3 font-mono">
          Configuración activa en backend: ROI={((config.minRoiPct ?? 0) * 100).toFixed(3)}% · Exposición={config.walletExposurePct}% · CB={config.circuitBreakerLosses ?? 3} pérdidas
        </div>
      )}
    </div>
  );
}
