'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { LandingPage } from '@/components/LandingPage'
import { NexusHeader } from "@/components/nexus/Header"
import { OrderBooksGrid } from "@/components/nexus/OrderBooks"
import { EnginePanel as NexusEnginePanel } from "@/components/nexus/EnginePanel"
import { WalletsPanel as NexusWalletsPanel } from "@/components/nexus/WalletsPanel"
import { Cockpit } from "@/components/nexus/Cockpit"
import { ArbitrageMatrix as NexusArbitrageMatrix } from "@/components/nexus/ArbitrageMatrix"
import { LiveHistory } from "@/components/nexus/TradeHistory"
import { History, BarChart3, RefreshCw, Zap } from 'lucide-react'

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

// Extracted from original page.tsx
interface OrderBookData { exchange: string; symbol: string; bestBid: string | null; bestBidVolume: string | null; bestAsk: string | null; bestAskVolume: string | null; stalenessMs: number; stale: boolean }
interface ConnectorStatus { state: string; healthy: boolean; wsActive: boolean }
interface SystemStatus { timestamp: string; uptimeSeconds: number; liveConnectors: number; allHealthy: boolean; connectors: Record<string, ConnectorStatus> }
interface WalletData { usdt: number; btc: number }
interface EngineStats { totalEvaluations: number; totalOpportunities: number; totalExecuted: number; totalRejected: number; circuitBreakerActive: boolean; circuitBreakerPauseMs: number; wallets: Record<string, WalletData> }
interface TradeEvent { timestampMs: number; buyExchange: string; sellExchange: string; buyPrice: number; sellPrice: number; volume: number; grossSpread: number; feesTotal: number; netProfit: number; spreadPct: number; status: string; rejectionReason: string | null; decisionLatencyMs: number }
interface TriangularEvent { timestampMs: number; exchange: string; startUsdt: number; btcAmount: number; ethAmount: number; finalUsdt: number; feesTotal: number; netProfit: number; spreadPct: number; status: string; rejectionReason: string | null; decisionLatencyMs: number }
interface LiveConfig { minProfitUsd: number; maxVolumeBtc: number; autoScalingEnabled: boolean; walletExposurePct: number; minRoiPct: number; decisionTimeoutMs: number; activeExchanges: string[] }
interface AnalyticsHeatmapCell { hour: number; route: string; count: number; executed: number; avgGrossSpread: number }
interface ReplayResult { totalEvaluated: number; originalExecuted: number; newExecuted: number; originalPnl: number; newPnl: number; originalRejections: Record<string, number>; newRejections: Record<string, number> }

function ReplayEnginePanel() {
  const [feeMultiplier, setFeeMultiplier] = useState(0.5)
  const [minRoiPct, setMinRoiPct] = useState(0.01)
  const [result, setResult] = useState<ReplayResult | null>(null)
  const [loading, setLoading] = useState(false)

  const runReplay = useCallback(async () => {
    setLoading(true)
    try {
      const res = await fetch(`${API_BASE}/api/replay`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ feeMultiplier, minRoiPct }) })
      if (res.ok) setResult(await res.json())
    } catch {}
    setLoading(false)
  }, [feeMultiplier, minRoiPct])

  useEffect(() => { runReplay() }, [runReplay])

  return (
    <div className="nx-panel p-5 mt-6">
      <div className="flex items-center gap-2 mb-4 border-b nx-hairline pb-3">
        <History size={16} className="text-purple-400" />
        <span className="text-foreground font-mono font-bold text-sm tracking-wider">WHAT-IF REPLAY ENGINE</span>
      </div>

      <div className="grid grid-cols-2 gap-6">
        <div className="space-y-6">
          <div className="nx-panel-strong p-4 border nx-hairline">
            <div className="flex justify-between mb-2">
              <span className="text-foreground/40 text-xs font-mono">FEE MULTIPLIER</span>
              <span className="text-purple-400 font-mono font-bold">{feeMultiplier.toFixed(2)}x</span>
            </div>
            <input type="range" min="0" max="2" step="0.1" value={feeMultiplier} onChange={e => setFeeMultiplier(parseFloat(e.target.value))} className="w-full h-1.5 bg-border appearance-none accent-purple-500" />
            
            <div className="flex justify-between mb-2 mt-4">
              <span className="text-foreground/40 text-xs font-mono">MIN ROI TARGET %</span>
              <span className="text-profit font-mono font-bold">{minRoiPct.toFixed(3)}%</span>
            </div>
            <input type="range" min="0.001" max="0.1" step="0.001" value={minRoiPct} onChange={e => setMinRoiPct(parseFloat(e.target.value))} className="w-full h-1.5 bg-border appearance-none accent-emerald-500" />
          </div>
          
          <button onClick={runReplay} disabled={loading} className="w-full py-2 bg-purple-600 hover:bg-purple-500 text-white font-mono text-xs font-bold flex justify-center items-center gap-2 transition cursor-pointer">
            {loading ? <RefreshCw size={14} className="animate-spin" /> : <Zap size={14} />} RE-CALCULATE
          </button>
        </div>

        {result && (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="nx-panel-strong p-3 border nx-hairline">
                <span className="text-foreground/40 text-[10px] font-mono block">ORIGINAL P&L</span>
                <span className={`font-mono text-lg font-bold ${result.originalPnl >= 0 ? 'text-profit' : 'text-loss'}`}>${result.originalPnl.toFixed(2)}</span>
                <span className="text-foreground/60 text-[10px] font-mono block mt-1">{result.originalExecuted} TRADES</span>
              </div>
              <div className="bg-purple-900/10 p-3 border border-purple-500/30">
                <span className="text-purple-400/80 text-[10px] font-mono block">SIMULATED P&L</span>
                <span className={`font-mono text-lg font-bold ${result.newPnl >= 0 ? 'text-profit' : 'text-loss'}`}>${result.newPnl.toFixed(2)}</span>
                <span className="text-purple-400 text-[10px] font-mono block mt-1">{result.newExecuted} TRADES</span>
              </div>
            </div>
            
            <div className="nx-panel p-3 border nx-hairline flex justify-between items-center">
              <span className="text-foreground/40 text-xs font-mono">EXTRA TRADES:</span>
              <span className="text-profit font-mono font-bold text-lg">+{Math.max(0, result.newExecuted - result.originalExecuted)}</span>
            </div>
            <div className="nx-panel p-3 border nx-hairline flex justify-between items-center">
              <span className="text-foreground/40 text-xs font-mono">FEES/ROI REJECTIONS:</span>
              <div className="text-right">
                <span className="text-foreground/40 font-mono text-xs line-through mr-2">{result.originalRejections['FEES_ROI'] || 0}</span>
                <span className="text-warn font-mono font-bold text-sm">{result.newRejections['FEES_ROI'] || 0}</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function OpportunityHeatmapPanel() {
  const [data, setData] = useState<AnalyticsHeatmapCell[]>([])
  useEffect(() => { fetch(`${API_BASE}/api/analytics`).then(r => r.json()).then(setData).catch(() => {}) }, [])

  const hours = Array.from({length: 24}, (_, i) => i)
  const routes = Array.from(new Set(data.map(d => d.route))).sort()
  const maxCount = Math.max(...data.map(d => d.count), 1)

  return (
    <div className="nx-panel p-5 mt-6">
      <div className="flex items-center gap-2 mb-4 border-b nx-hairline pb-3">
        <BarChart3 size={16} className="text-blue-400" />
        <span className="text-foreground font-mono font-bold text-sm tracking-wider">OPPORTUNITY HEATMAP (BY HOUR)</span>
      </div>
      
      {routes.length === 0 ? (
        <div className="text-foreground/50 text-center py-8 font-mono text-sm">No historical data available.</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-xs font-mono border-collapse">
            <thead>
              <tr>
                <th className="p-2 text-left text-foreground/50 border-b nx-hairline">ROUTE</th>
                {hours.map(h => <th key={h} className="p-1 text-center text-foreground/50 border-b nx-hairline w-8">{h}h</th>)}
              </tr>
            </thead>
            <tbody>
              {routes.map(route => (
                <tr key={route}>
                  <td className="p-2 text-foreground/80 border-r nx-hairline whitespace-nowrap">{route}</td>
                  {hours.map(h => {
                    const cell = data.find(d => d.hour === h && d.route === route)
                    const count = cell?.count || 0
                    const opacity = Math.max(0.1, count / maxCount)
                    return (
                      <td key={h} className="p-1 text-center group relative cursor-crosshair">
                        {count > 0 ? (
                          <div className="w-full h-6 bg-profit transition-all hover:ring-1 hover:ring-white" style={{ opacity }} />
                        ) : (
                          <div className="w-full h-6 bg-surface-2" />
                        )}
                        {count > 0 && (
                          <div className="hidden group-hover:block absolute bottom-full left-1/2 -translate-x-1/2 mb-1 bg-foreground text-background p-2 text-[10px] whitespace-nowrap z-10 border nx-hairline">
                            Count: {count}<br/>
                            Avg Spread: ${cell!.avgGrossSpread.toFixed(2)}
                          </div>
                        )}
                      </td>
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

export default function Page() {
  const [view, setView] = useState<'landing' | 'terminal' | 'replay' | 'analytics'>('landing')

  const [orderBooks, setOrderBooks] = useState<OrderBookData[]>([])
  const [status, setStatus] = useState<SystemStatus | null>(null)
  const [engineStats, setEngineStats] = useState<EngineStats | null>(null)
  const [liveConfig, setLiveConfig] = useState<LiveConfig | null>(null)
  const [tradeEvents, setTradeEvents] = useState<TradeEvent[]>([])
  const [triangularEvents, setTriangularEvents] = useState<TriangularEvent[]>([])
  const [, setSseConnected] = useState(false)
  const sseRef = useRef<EventSource | null>(null)

  useEffect(() => {
    const sse = new EventSource(`${API_BASE}/api/stream`)
    sseRef.current = sse
    sse.onopen = () => setSseConnected(true)
    sse.onerror = () => setSseConnected(false)
    sse.addEventListener('opportunity', (e) => {
      try {
        const event: TradeEvent = JSON.parse(e.data)
        setTradeEvents(prev => [event, ...prev].slice(0, 100))
      } catch {}
    })
    sse.addEventListener('triangular_opportunity', (e) => {
      try {
        const event: TriangularEvent = JSON.parse(e.data)
        setTriangularEvents(prev => [event, ...prev].slice(0, 100))
      } catch {}
    })
    return () => { sse.close(); sseRef.current = null }
  }, [])

  useEffect(() => {
    fetch(`${API_BASE}/api/history?limit=100`)
      .then(r => r.json())
      .then((data: Array<Record<string, any>>) => {
        const direct = data.filter(e => e.type !== 'TRIANGULAR') as unknown as TradeEvent[]
        const triangular = data.filter(e => e.type === 'TRIANGULAR') as unknown as TriangularEvent[]
        setTradeEvents(direct)
        setTriangularEvents(triangular)
      }).catch(() => {})
  }, [])

  const fetchData = useCallback(async () => {
    try {
      const [booksRes, statusRes, engineRes, configRes] = await Promise.all([
        fetch(`${API_BASE}/api/orderbooks`),
        fetch(`${API_BASE}/api/status`),
        fetch(`${API_BASE}/api/engine`),
        fetch(`${API_BASE}/api/config`),
      ])
      if (booksRes.ok) setOrderBooks(await booksRes.json())
      if (statusRes.ok) setStatus(await statusRes.json())
      if (engineRes.ok) setEngineStats(await engineRes.json())
      if (configRes.ok) setLiveConfig(await configRes.json())
    } catch {}
  }, [])

  useEffect(() => { fetchData(); const iv = setInterval(fetchData, 1000); return () => clearInterval(iv) }, [fetchData])

  const pnl = tradeEvents.reduce((sum, e) => e.status === 'EXECUTED' ? sum + e.netProfit : sum, 0) +
              triangularEvents.reduce((sum, e) => e.status === 'EXECUTED' ? sum + e.netProfit : sum, 0)

  const updateEngineConfig = async (walletExposurePct: number, minRoiPct: number, activeExchanges: string[]) => {
    const res = await fetch(`${API_BASE}/api/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ walletExposurePct, minRoiPct, activeExchanges })
    })
    if (!res.ok) throw new Error('Config sync failed')
    setLiveConfig(await res.json())
  }

  const triggerShock = async () => {
    await fetch(`${API_BASE}/api/risk/shock`, { method: 'POST' })
  }

  let health: "LIVE" | "DEGRADED" | "DEAD" = "DEAD"
  if (status) {
    if (status.liveConnectors === 0) health = "DEAD"
    else if (status.allHealthy && !engineStats?.circuitBreakerActive) health = "LIVE"
    else health = "DEGRADED"
  }

  const exMap: Record<string, boolean> = { BINANCE: false, KRAKEN: false, COINBASE: false }
  liveConfig?.activeExchanges.forEach(e => exMap[e] = true)

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* GLOBAL NAV */}
      <header className="sticky top-0 z-30 border-b nx-hairline bg-background/85 backdrop-blur">
        <div className="mx-auto flex max-w-[1400px] items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3 cursor-pointer" onClick={() => setView('landing')}>
            <div className="grid h-7 w-7 place-items-center border border-foreground/30">
              <span className="text-[10px] font-bold tracking-tighter">NX</span>
            </div>
            <span className="text-sm font-bold tracking-tight">
              NEXUS<span className="font-light opacity-50">TRADE</span>
            </span>
          </div>
          <nav className="hidden items-center gap-8 text-[11px] font-medium uppercase tracking-widest text-foreground/60 md:flex">
            <button onClick={() => setView('landing')} className={`hover:text-foreground ${view === 'landing' ? 'text-foreground font-bold' : ''}`}>Engine</button>
            <button onClick={() => setView('replay')} className={`hover:text-foreground ${view === 'replay' ? 'text-foreground font-bold' : ''}`}>Replay</button>
            <button onClick={() => setView('analytics')} className={`hover:text-foreground ${view === 'analytics' ? 'text-foreground font-bold' : ''}`}>Analytics</button>
          </nav>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setView('terminal')}
              className="group flex h-9 items-center gap-2 bg-foreground px-4 text-[10px] font-bold uppercase tracking-widest text-background transition hover:opacity-90 cursor-pointer"
            >
              <Zap className="h-3.5 w-3.5" />
              Launch Terminal
            </button>
          </div>
        </div>
      </header>

      {view === 'landing' ? (
        <LandingPage onLaunch={() => setView('terminal')} />
      ) : (
        <div className="p-6">
          <div className="mx-auto max-w-[1400px] space-y-6">
            <NexusHeader
              exchangesConnected={status?.liveConnectors || 0}
              exchangesTotal={3}
              uptimeMs={(status?.uptimeSeconds || 0) * 1000}
              pnl={pnl}
              health={health}
            />

            {view === 'terminal' && (
              <div className="space-y-6">
                <OrderBooksGrid books={orderBooks.map(b => ({...b, exchange: b.exchange.toLowerCase()}))} />

                <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
                  <div className="lg:col-span-8">
                    <NexusArbitrageMatrix books={orderBooks.map(b => ({...b, exchange: b.exchange.toLowerCase()}))} symbol="BTC/USDT" />
                  </div>
                  <div className="space-y-6 lg:col-span-4">
                    <Cockpit
                      minProfitUsd={liveConfig?.minProfitUsd || 0}
                      exchangesEnabled={{ binance: exMap.BINANCE, kraken: exMap.KRAKEN, coinbase: exMap.COINBASE }}
                      onApply={(m, en) => {
                        const active = []
                        if (en.binance) active.push('BINANCE')
                        if (en.kraken) active.push('KRAKEN')
                        if (en.coinbase) active.push('COINBASE')
                        updateEngineConfig(liveConfig?.walletExposurePct || 0.1, liveConfig?.minRoiPct || 0.005, active)
                      }}
                      onFlashCrash={triggerShock}
                    />
                    <NexusEnginePanel engine={{
                      circuitBreakerActive: engineStats?.circuitBreakerActive || false,
                      circuitBreakerPauseMs: engineStats?.circuitBreakerPauseMs || 0,
                      totalEvaluations: engineStats?.totalEvaluations || 0,
                      totalOpportunities: engineStats?.totalOpportunities || 0,
                      totalExecuted: engineStats?.totalExecuted || 0,
                      totalRejected: engineStats?.totalRejected || 0,
                      wallets: {
                        binance: { totalUsd: engineStats?.wallets?.BINANCE?.usdt || 0, btcBalance: engineStats?.wallets?.BINANCE?.btc || 0 },
                        kraken: { totalUsd: engineStats?.wallets?.KRAKEN?.usdt || 0, btcBalance: engineStats?.wallets?.KRAKEN?.btc || 0 },
                        coinbase: { totalUsd: engineStats?.wallets?.COINBASE?.usdt || 0, btcBalance: engineStats?.wallets?.COINBASE?.btc || 0 }
                      }
                    }} />
                  </div>
                </div>

                <NexusWalletsPanel wallets={{
                  binance: { totalUsd: engineStats?.wallets?.BINANCE?.usdt || 0, btcBalance: engineStats?.wallets?.BINANCE?.btc || 0 },
                  kraken: { totalUsd: engineStats?.wallets?.KRAKEN?.usdt || 0, btcBalance: engineStats?.wallets?.KRAKEN?.btc || 0 },
                  coinbase: { totalUsd: engineStats?.wallets?.COINBASE?.usdt || 0, btcBalance: engineStats?.wallets?.COINBASE?.btc || 0 }
                }} />

                <LiveHistory 
                  direct={tradeEvents.map(e => ({
                    id: Math.random().toString(),
                    timestampMs: e.timestampMs,
                    symbol: 'BTC/USDT',
                    buyExchange: e.buyExchange.toLowerCase(),
                    sellExchange: e.sellExchange.toLowerCase(),
                    volume: e.volume || 0,
                    grossSpread: e.grossSpread,
                    netProfit: e.netProfit,
                    status: e.status === 'EXECUTED' ? 'EXECUTED' : (e.rejectionReason === 'CIRCUIT_BREAKER' ? 'REJECTED_CIRCUIT_BREAKER' : 'REJECTED_FEES')
                  }))}
                  triangular={triangularEvents.map(e => ({
                    id: Math.random().toString(),
                    timestampMs: e.timestampMs,
                    exchange: e.exchange.toLowerCase(),
                    route: 'Triangular',
                    volume: e.btcAmount || 0,
                    netProfit: e.netProfit,
                    status: e.status === 'EXECUTED' ? 'EXECUTED' : (e.rejectionReason === 'CIRCUIT_BREAKER' ? 'REJECTED_CIRCUIT_BREAKER' : 'REJECTED_FEES')
                  }))} 
                />
              </div>
            )}

            {view === 'replay' && <ReplayEnginePanel />}
            {view === 'analytics' && <OpportunityHeatmapPanel />}

            <footer className="flex flex-wrap items-center justify-between gap-2 border-t nx-hairline pt-4 mt-8">
              <span className="font-mono text-[9px] uppercase tracking-[0.2em] text-foreground/20">
                Next.js Integration
              </span>
              <div className="flex gap-4 font-mono text-[9px] uppercase tracking-widest text-foreground/20">
                <span>v4.2.1-stable</span>
                <span>Security: High</span>
                <span>Circuit: {engineStats?.circuitBreakerActive ? "Tripped" : "Armed"}</span>
              </div>
            </footer>
          </div>
        </div>
      )}
    </div>
  )
}
