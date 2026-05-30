'use client'

import { useEffect, useState, useCallback, useRef } from 'react'
import { Activity, Wifi, WifiOff, TrendingUp, TrendingDown, Zap, AlertTriangle, RefreshCw, Shield, ShieldOff, Wallet, BarChart3, Radio, History, Settings, Power } from 'lucide-react'

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

interface OrderBookData {
  exchange: string
  symbol: string
  bestBid: string | null
  bestBidVolume: string | null
  bestAsk: string | null
  bestAskVolume: string | null
  stalenessMs: number
  stale: boolean
}

interface ConnectorStatus { state: string; healthy: boolean; wsActive: boolean }
interface SystemStatus { timestamp: string; uptimeSeconds: number; liveConnectors: number; allHealthy: boolean; connectors: Record<string, ConnectorStatus> }
interface WalletData { usdt: number; btc: number }
interface EngineStats { totalEvaluations: number; totalOpportunities: number; totalExecuted: number; totalRejected: number; circuitBreakerActive: boolean; circuitBreakerPauseMs: number; wallets: Record<string, WalletData> }
interface TradeEvent { ts: number; buyExchange: string; sellExchange: string; buyPrice: number; sellPrice: number; volume: number; grossSpread: number; feesTotal: number; netProfit: number; spreadPct: number; status: string; rejectionReason: string | null; decisionLatencyMs: number }
interface TriangularEvent { ts: number; exchange: string; startUsdt: number; btcAmount: number; ethAmount: number; finalUsdt: number; feesTotal: number; netProfit: number; spreadPct: number; status: string; rejectionReason: string | null; decisionLatencyMs: number }
interface LiveConfig { minProfitUsd: number; maxVolumeBtc: number; decisionTimeoutMs: number; activeExchanges: string[] }

const EXCHANGE_COLORS: Record<string, string> = { BINANCE: '#F3BA2F', KRAKEN: '#5741D9', COINBASE: '#0052FF' }
const STATE_BADGE: Record<string, { label: string; color: string }> = {
  LIVE: { label: 'LIVE', color: 'bg-emerald-500' },
  CONNECTING: { label: 'CONNECTING', color: 'bg-yellow-500' },
  RECONNECTING: { label: 'RECONNECT', color: 'bg-orange-500' },
  FALLBACK_REST: { label: 'REST', color: 'bg-blue-500' },
  DEAD: { label: 'DEAD', color: 'bg-red-600' },
  INITIALIZING: { label: 'INIT', color: 'bg-gray-500' },
}

function formatPrice(val: string | null): string {
  if (!val) return '—'
  return parseFloat(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function formatVolume(val: string | null): string { return !val ? '—' : parseFloat(val).toFixed(4) }
function formatNumber(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return n.toString()
}

function ExchangeCard({ book, connector }: { book: OrderBookData; connector?: ConnectorStatus }) {
  const color = EXCHANGE_COLORS[book.exchange] || '#888'
  const badge = STATE_BADGE[connector?.state || 'INITIALIZING']
  const spread = book.bestBid && book.bestAsk ? (parseFloat(book.bestAsk) - parseFloat(book.bestBid)).toFixed(2) : null

  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 relative overflow-hidden">
      <div className="absolute top-0 left-0 right-0 h-0.5" style={{ backgroundColor: color }} />
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <div className="w-2 h-2 rounded-full" style={{ backgroundColor: color }} />
          <span className="font-mono font-bold text-white tracking-wider">{book.exchange} {book.symbol}</span>
        </div>
        <div className="flex items-center gap-2">
          {book.stale && <AlertTriangle size={14} className="text-yellow-500" />}
          <span className={`text-xs font-mono px-2 py-0.5 rounded text-white ${badge.color}`}>{badge.label}</span>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="bg-zinc-950 rounded-lg p-3">
          <div className="flex items-center gap-1 text-emerald-400 text-xs font-mono mb-1"><TrendingUp size={11} /><span>BID</span></div>
          <div className="text-white font-mono text-lg font-bold">${formatPrice(book.bestBid)}</div>
          <div className="text-zinc-500 text-xs font-mono mt-0.5">{formatVolume(book.bestBidVolume)} BTC</div>
        </div>
        <div className="bg-zinc-950 rounded-lg p-3">
          <div className="flex items-center gap-1 text-red-400 text-xs font-mono mb-1"><TrendingDown size={11} /><span>ASK</span></div>
          <div className="text-white font-mono text-lg font-bold">${formatPrice(book.bestAsk)}</div>
          <div className="text-zinc-500 text-xs font-mono mt-0.5">{formatVolume(book.bestAskVolume)} BTC</div>
        </div>
      </div>
      {spread && (
        <div className="mt-3 flex items-center justify-between text-xs font-mono">
          <span className="text-zinc-500">SPREAD</span><span className="text-zinc-300">${spread}</span>
        </div>
      )}
      <div className="mt-2 flex items-center justify-between text-xs font-mono">
        <span className="text-zinc-600">STALENESS</span>
        <span className={book.stale ? 'text-yellow-500' : 'text-zinc-500'}>{book.stalenessMs}ms</span>
      </div>
    </div>
  )
}

function ArbitrageMatrix({ books }: { books: OrderBookData[] }) {
  const validBooks = books.filter(b => b.bestBid && b.bestAsk)
  const pairs: Array<{ buy: OrderBookData; sell: OrderBookData; grossSpread: number; pct: number }> = []
  for (let i = 0; i < validBooks.length; i++) {
    for (let j = 0; j < validBooks.length; j++) {
      if (i === j) continue
      const ask = parseFloat(validBooks[i].bestAsk!), bid = parseFloat(validBooks[j].bestBid!)
      pairs.push({ buy: validBooks[i], sell: validBooks[j], grossSpread: bid - ask, pct: ((bid - ask) / ask) * 100 })
    }
  }
  pairs.sort((a, b) => b.grossSpread - a.grossSpread)

  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
      <div className="flex items-center gap-2 mb-4">
        <Zap size={16} className="text-yellow-400" />
        <span className="text-white font-mono font-bold text-sm tracking-wider">ARBITRAGE MATRIX</span>
        <span className="text-zinc-600 text-xs font-mono ml-auto">GROSS · NO FEES</span>
      </div>
      <div className="space-y-2">
        {pairs.length === 0 ? (
          <p className="text-zinc-600 text-sm font-mono text-center py-4">Waiting for data...</p>
        ) : pairs.map((pair, i) => {
          const pos = pair.grossSpread > 0
          return (
            <div key={`${pair.buy.exchange}-${pair.buy.symbol}-${pair.sell.exchange}-${pair.sell.symbol}`}
              className={`flex items-center gap-3 p-3 rounded-lg border ${pos ? 'border-emerald-900 bg-emerald-950/30' : 'border-zinc-800 bg-zinc-950'}`}>
              <span className="text-zinc-600 text-xs font-mono w-4">{i + 1}</span>
              <div className="flex items-center gap-2 flex-1">
                <div className="flex items-center gap-1">
                  <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: EXCHANGE_COLORS[pair.buy.exchange] }} />
                  <span className="text-zinc-400 text-xs font-mono">{pair.buy.exchange}</span>
                </div>
                <span className="text-zinc-600 text-xs">→</span>
                <div className="flex items-center gap-1">
                  <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: EXCHANGE_COLORS[pair.sell.exchange] }} />
                  <span className="text-zinc-400 text-xs font-mono">{pair.sell.exchange}</span>
                </div>
              </div>
              <span className={`font-mono text-sm font-bold ${pos ? 'text-emerald-400' : 'text-zinc-600'}`}>{pos ? '+' : ''}{pair.grossSpread.toFixed(2)}</span>
              <span className={`font-mono text-xs ${pos ? 'text-emerald-600' : 'text-zinc-700'}`}>{pair.pct.toFixed(4)}%</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function EnginePanel({ stats }: { stats: EngineStats | null }) {
  if (!stats) return null
  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
      <div className="flex items-center gap-2 mb-4">
        <BarChart3 size={16} className="text-purple-400" />
        <span className="text-white font-mono font-bold text-sm tracking-wider">ENGINE</span>
        {stats.circuitBreakerActive ? (
          <div className="flex items-center gap-1 ml-auto">
            <ShieldOff size={14} className="text-red-400" />
            <span className="text-red-400 text-xs font-mono animate-pulse">CIRCUIT BREAKER · {Math.ceil(stats.circuitBreakerPauseMs / 1000)}s</span>
          </div>
        ) : (
          <div className="flex items-center gap-1 ml-auto">
            <Shield size={14} className="text-emerald-500" />
            <span className="text-emerald-500 text-xs font-mono">ACTIVE</span>
          </div>
        )}
      </div>
      <div className="grid grid-cols-4 gap-3">
        {[
          { label: 'EVALUATIONS', value: formatNumber(stats.totalEvaluations), color: 'text-blue-400' },
          { label: 'OPPORTUNITIES', value: formatNumber(stats.totalOpportunities), color: 'text-yellow-400' },
          { label: 'EXECUTED', value: formatNumber(stats.totalExecuted), color: 'text-emerald-400' },
          { label: 'REJECTED', value: formatNumber(stats.totalRejected), color: 'text-red-400' },
        ].map(s => (
          <div key={s.label} className="bg-zinc-950 rounded-lg p-3">
            <div className={`text-xs font-mono mb-1 ${s.color} opacity-60`}>{s.label}</div>
            <div className={`font-mono font-bold text-lg ${s.color}`}>{s.value}</div>
          </div>
        ))}
      </div>
    </div>
  )
}

function WalletsPanel({ stats }: { stats: EngineStats | null }) {
  if (!stats?.wallets) return null
  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
      <div className="flex items-center gap-2 mb-4">
        <Wallet size={16} className="text-amber-400" />
        <span className="text-white font-mono font-bold text-sm tracking-wider">VIRTUAL WALLETS</span>
        <span className="text-zinc-600 text-xs font-mono ml-auto">SHADOW MODE</span>
      </div>
      <div className="grid grid-cols-3 gap-3">
        {Object.entries(stats.wallets).map(([exchange, wallet]) => {
          const color = EXCHANGE_COLORS[exchange] || '#888'
          return (
            <div key={exchange} className="bg-zinc-950 rounded-lg p-3 relative overflow-hidden">
              <div className="absolute top-0 left-0 right-0 h-0.5" style={{ backgroundColor: color }} />
              <div className="flex items-center gap-1.5 mb-2">
                <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: color }} />
                <span className="text-zinc-400 text-xs font-mono font-bold">{exchange}</span>
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="text-zinc-600 text-xs font-mono">USDT</span>
                  <span className="text-emerald-400 text-sm font-mono font-bold">
                    ${typeof wallet.usdt === 'number' ? wallet.usdt.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : wallet.usdt}
                  </span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-zinc-600 text-xs font-mono">BTC</span>
                  <span className="text-amber-400 text-sm font-mono font-bold">₿{typeof wallet.btc === 'number' ? wallet.btc.toFixed(6) : wallet.btc}</span>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function TradeHistoryTable({ events }: { events: TradeEvent[] }) {
  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
      <div className="flex items-center gap-2 mb-4">
        <History size={16} className="text-cyan-400" />
        <span className="text-white font-mono font-bold text-sm tracking-wider">TRADE HISTORY</span>
        <span className="text-zinc-600 text-xs font-mono ml-auto">LAST {events.length} EVENTS</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-xs font-mono">
          <thead>
            <tr className="text-zinc-500 border-b border-zinc-800">
              <th className="text-left py-2 px-2">TIME</th>
              <th className="text-left py-2 px-2">ROUTE</th>
              <th className="text-right py-2 px-2">GROSS</th>
              <th className="text-right py-2 px-2">FEES</th>
              <th className="text-right py-2 px-2">NET</th>
              <th className="text-right py-2 px-2">LAT</th>
              <th className="text-left py-2 px-2">STATUS</th>
            </tr>
          </thead>
          <tbody>
            {events.length === 0 ? (
              <tr><td colSpan={7} className="text-center py-4 text-zinc-600">No events yet...</td></tr>
            ) : events.map((e, i) => {
              const isExec = e.status === 'EXECUTED'
              const time = new Date(e.ts).toLocaleTimeString('en-US', { hour12: false })
              return (
                <tr key={i} className={`border-b border-zinc-800/50 ${isExec ? 'bg-emerald-950/20' : ''}`}>
                  <td className="py-1.5 px-2 text-zinc-500">{time}</td>
                  <td className="py-1.5 px-2">
                    <span style={{ color: EXCHANGE_COLORS[e.buyExchange] || '#888' }}>{e.buyExchange?.slice(0, 3) ?? '???'}</span>
                    <span className="text-zinc-600">→</span>
                    <span style={{ color: EXCHANGE_COLORS[e.sellExchange] || '#888' }}>{e.sellExchange?.slice(0, 3) ?? '???'}</span>
                  </td>
                  <td className={`py-1.5 px-2 text-right ${e.grossSpread > 0 ? 'text-emerald-400' : 'text-zinc-600'}`}>${e.grossSpread.toFixed(2)}</td>
                  <td className="py-1.5 px-2 text-right text-red-400">${e.feesTotal.toFixed(2)}</td>
                  <td className={`py-1.5 px-2 text-right font-bold ${e.netProfit > 0 ? 'text-emerald-400' : 'text-red-400'}`}>${e.netProfit.toFixed(2)}</td>
                  <td className="py-1.5 px-2 text-right text-zinc-500">{e.decisionLatencyMs}ms</td>
                  <td className="py-1.5 px-2">
                    <span className={`px-1.5 py-0.5 rounded text-[10px] ${isExec ? 'bg-emerald-900 text-emerald-300' : 'bg-zinc-800 text-zinc-500'}`}>
                      {e.status.replace('REJECTED_', '✗ ')}
                    </span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function TriangularHistoryTable({ events }: { events: TriangularEvent[] }) {
  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
      <div className="flex items-center gap-2 mb-4">
        <RefreshCw size={16} className="text-purple-400" />
        <span className="text-white font-mono font-bold text-sm tracking-wider">TRIANGULAR MATRIX</span>
        <span className="text-zinc-600 text-xs font-mono ml-auto">USDT → BTC → ETH → USDT</span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-xs font-mono">
          <thead>
            <tr className="text-zinc-500 border-b border-zinc-800">
              <th className="text-left py-2 px-2">TIME</th>
              <th className="text-left py-2 px-2">EXCHANGE</th>
              <th className="text-right py-2 px-2">BTC</th>
              <th className="text-right py-2 px-2">ETH</th>
              <th className="text-right py-2 px-2">FEES</th>
              <th className="text-right py-2 px-2">NET (USDT)</th>
              <th className="text-left py-2 px-2 ml-4">STATUS</th>
            </tr>
          </thead>
          <tbody>
            {events.length === 0 ? (
              <tr><td colSpan={7} className="text-center py-4 text-zinc-600">No triangular events yet...</td></tr>
            ) : events.map((e, i) => {
              const isExec = e.status === 'EXECUTED'
              const time = new Date(e.ts).toLocaleTimeString('en-US', { hour12: false })
              return (
                <tr key={i} className={`border-b border-zinc-800/50 ${isExec ? 'bg-emerald-950/20' : ''}`}>
                  <td className="py-1.5 px-2 text-zinc-500">{time}</td>
                  <td className="py-1.5 px-2" style={{ color: EXCHANGE_COLORS[e.exchange] }}>{e.exchange}</td>
                  <td className="py-1.5 px-2 text-right">₿{e.btcAmount.toFixed(4)}</td>
                  <td className="py-1.5 px-2 text-right">Ξ{e.ethAmount.toFixed(4)}</td>
                  <td className="py-1.5 px-2 text-right text-red-400">${e.feesTotal.toFixed(2)}</td>
                  <td className={`py-1.5 px-2 text-right font-bold ${e.netProfit > 0 ? 'text-emerald-400' : 'text-red-400'}`}>${e.netProfit.toFixed(2)}</td>
                  <td className="py-1.5 px-2 ml-4">
                    <span className={`px-1.5 py-0.5 rounded text-[10px] ${isExec ? 'bg-emerald-900 text-emerald-300' : 'bg-zinc-800 text-zinc-500'}`}>
                      {e.status.replace('REJECTED_', '✗ ')}
                    </span>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function CockpitControl({ config, onUpdate, onShock }: { config: LiveConfig | null; onUpdate: (minProfit: number, exchanges: string[]) => Promise<void>; onShock: () => Promise<void> }) {
  const [minProfit, setMinProfit] = useState(5.0)
  const [exchanges, setExchanges] = useState<Record<string, boolean>>({ BINANCE: true, KRAKEN: true, COINBASE: true })
  const [syncing, setSyncing] = useState(false)
  const [syncStatus, setSyncStatus] = useState<'idle' | 'ok' | 'error'>('idle')
  const [shocking, setShocking] = useState(false)
  const initialized = useRef(false)

  // Sync local state from server config on first load
  useEffect(() => {
    if (config && !initialized.current) {
      initialized.current = true
      setMinProfit(config.minProfitUsd)
      setExchanges({
        BINANCE: config.activeExchanges.includes('BINANCE'),
        KRAKEN: config.activeExchanges.includes('KRAKEN'),
        COINBASE: config.activeExchanges.includes('COINBASE'),
      })
    }
  }, [config])

  const handleSync = async () => {
    setSyncing(true)
    setSyncStatus('idle')
    try {
      const activeList = Object.entries(exchanges).filter(([, v]) => v).map(([k]) => k)
      await onUpdate(minProfit, activeList)
      setSyncStatus('ok')
      setTimeout(() => setSyncStatus('idle'), 2000)
    } catch {
      setSyncStatus('error')
      setTimeout(() => setSyncStatus('idle'), 3000)
    } finally {
      setSyncing(false)
    }
  }

  const toggleExchange = (name: string) => {
    setExchanges(prev => ({ ...prev, [name]: !prev[name] }))
  }

  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5">
      <div className="flex items-center gap-2 mb-4">
        <Settings size={16} className="text-orange-400" />
        <span className="text-white font-mono font-bold text-sm tracking-wider">COCKPIT CONTROL</span>
        <span className="text-zinc-600 text-xs font-mono ml-auto">LIVE CONFIG</span>
      </div>

      {/* Min Profit Control */}
      <div className="bg-zinc-950 rounded-lg p-4 mb-3">
        <div className="flex items-center justify-between mb-2">
          <span className="text-zinc-400 text-xs font-mono">MIN PROFIT (USD)</span>
          <span className="text-orange-400 font-mono font-bold text-lg">${minProfit.toFixed(1)}</span>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setMinProfit(p => Math.max(0.1, +(p - 1).toFixed(1)))} className="w-8 h-8 bg-zinc-800 hover:bg-zinc-700 rounded text-zinc-300 font-mono font-bold text-lg transition-colors">−</button>
          <input
            type="range" min="0.1" max="50" step="0.5" value={minProfit}
            onChange={e => setMinProfit(parseFloat(e.target.value))}
            className="flex-1 h-1.5 bg-zinc-800 rounded-full appearance-none cursor-pointer accent-orange-500"
          />
          <button onClick={() => setMinProfit(p => Math.min(50, +(p + 1).toFixed(1)))} className="w-8 h-8 bg-zinc-800 hover:bg-zinc-700 rounded text-zinc-300 font-mono font-bold text-lg transition-colors">+</button>
        </div>
      </div>

      {/* Exchange Toggles */}
      <div className="grid grid-cols-3 gap-2 mb-4">
        {(['BINANCE', 'KRAKEN', 'COINBASE'] as const).map(name => {
          const active = exchanges[name]
          const color = EXCHANGE_COLORS[name]
          return (
            <button key={name} onClick={() => toggleExchange(name)}
              className={`relative rounded-lg p-3 font-mono text-xs font-bold transition-all border ${
                active
                  ? 'border-zinc-700 bg-zinc-950'
                  : 'border-zinc-800 bg-zinc-900 opacity-40'
              }`}>
              <div className={`absolute top-0 left-0 right-0 h-0.5 rounded-t transition-opacity ${active ? 'opacity-100' : 'opacity-0'}`} style={{ backgroundColor: color }} />
              <div className="flex items-center justify-center gap-1.5">
                <Power size={12} className={active ? 'text-emerald-400' : 'text-zinc-600'} />
                <span style={{ color: active ? color : '#666' }}>{name}</span>
              </div>
            </button>
          )
        })}
      </div>

      {/* Sync Button */}
      <button onClick={handleSync} disabled={syncing}
        className={`w-full py-2.5 rounded-lg font-mono text-sm font-bold transition-all ${
          syncing ? 'bg-zinc-800 text-zinc-500 cursor-wait'
          : syncStatus === 'ok' ? 'bg-emerald-900 text-emerald-300'
          : syncStatus === 'error' ? 'bg-red-900 text-red-300'
          : 'bg-orange-600 hover:bg-orange-500 text-black'
        }`}>
        {syncing ? '⏳ SYNCING...' : syncStatus === 'ok' ? '✓ SYNCED' : syncStatus === 'error' ? '✗ FAILED' : '⚡ APPLY CHANGES'}
      </button>

      {/* Stress Test Button */}
      <button onClick={async () => { setShocking(true); try { await onShock() } finally { setShocking(false) } }} disabled={shocking}
        className={`w-full py-2.5 rounded-lg font-mono text-sm font-bold transition-all mt-2 border ${
          shocking ? 'bg-red-950 text-red-400 border-red-800 cursor-wait animate-pulse'
          : 'bg-zinc-950 text-red-500 border-red-900 hover:bg-red-950 hover:border-red-700'
        }`}>
        {shocking ? '💥 INJECTING...' : '⚠️ STRESS TEST (FLASH CRASH)'}
      </button>
    </div>
  )
}

export default function Dashboard() {
  const [orderBooks, setOrderBooks] = useState<OrderBookData[]>([])
  const [status, setStatus] = useState<SystemStatus | null>(null)
  const [engineStats, setEngineStats] = useState<EngineStats | null>(null)
  const [liveConfig, setLiveConfig] = useState<LiveConfig | null>(null)
  const [tradeEvents, setTradeEvents] = useState<TradeEvent[]>([])
  const [triangularEvents, setTriangularEvents] = useState<TriangularEvent[]>([])
  const [sseConnected, setSseConnected] = useState(false)
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tickCount, setTickCount] = useState(0)
  const sseRef = useRef<EventSource | null>(null)

  // SSE connection for real-time events
  useEffect(() => {
    const sse = new EventSource(`${API_BASE}/api/stream`)
    sseRef.current = sse
    sse.onopen = () => setSseConnected(true)
    sse.onerror = () => setSseConnected(false)
    sse.addEventListener('opportunity', (e) => {
      try {
        const event: TradeEvent = JSON.parse(e.data)
        setTradeEvents(prev => [event, ...prev].slice(0, 100)) // Keep last 100
      } catch {}
    })
    sse.addEventListener('triangular_opportunity', (e) => {
      try {
        const event: TriangularEvent = JSON.parse(e.data)
        setTriangularEvents(prev => [event, ...prev].slice(0, 100)) // Keep last 100
      } catch {}
    })
    return () => { sse.close(); sseRef.current = null }
  }, [])

  // Load initial history (events.jsonl contains both OPPORTUNITY and TRIANGULAR types)
  useEffect(() => {
    fetch(`${API_BASE}/api/history?limit=100`)
      .then(r => r.json())
      .then((data: Array<Record<string, any>>) => {
        const direct = data.filter(e => e.type !== 'TRIANGULAR') as unknown as TradeEvent[]
        const triangular = data.filter(e => e.type === 'TRIANGULAR') as unknown as TriangularEvent[]
        setTradeEvents(direct)
        setTriangularEvents(triangular)
      })
      .catch(() => {})
  }, [])

  const fetchData = useCallback(async () => {
    try {
      const [booksRes, statusRes, engineRes, configRes] = await Promise.all([
        fetch(`${API_BASE}/api/orderbooks`),
        fetch(`${API_BASE}/api/status`),
        fetch(`${API_BASE}/api/engine`),
        fetch(`${API_BASE}/api/config`),
      ])
      if (booksRes.ok) { setOrderBooks(await booksRes.json()); setTickCount(c => c + 1) }
      if (statusRes.ok) setStatus(await statusRes.json())
      if (engineRes.ok) setEngineStats(await engineRes.json())
      if (configRes.ok) setLiveConfig(await configRes.json())
      setLastUpdate(new Date())
      setError(null)
    } catch (e) { setError('Cannot reach backend. Is it running on port 8080?') }
  }, [])

  useEffect(() => { fetchData(); const iv = setInterval(fetchData, 1000); return () => clearInterval(iv) }, [fetchData])

  const liveCount = status?.liveConnectors ?? 0
  const allConnectors = status?.connectors ?? {}

  // Calculate cumulative P&L from events
  const pnl = tradeEvents.reduce((sum, e) => e.status === 'EXECUTED' ? sum + e.netProfit : sum, 0) +
              triangularEvents.reduce((sum, e) => e.status === 'EXECUTED' ? sum + e.netProfit : sum, 0)

  const updateEngineConfig = async (minProfitUsd: number, activeExchanges: string[]) => {
    const res = await fetch(`${API_BASE}/api/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ minProfitUsd, activeExchanges })
    })
    if (!res.ok) throw new Error('Config sync failed')
    setLiveConfig(await res.json())
  }

  const triggerShock = async () => {
    const res = await fetch(`${API_BASE}/api/risk/shock`, { method: 'POST' })
    if (!res.ok) throw new Error('Shock injection failed')
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-white">
      <header className="border-b border-zinc-800 px-6 py-3">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1">
              <div className="w-2 h-2 bg-yellow-400 rounded-full" />
              <div className="w-2 h-2 bg-yellow-400 rounded-full opacity-60" />
              <div className="w-2 h-2 bg-yellow-400 rounded-full opacity-30" />
            </div>
            <span className="font-mono font-bold text-lg tracking-widest text-white">NEXUSTRADE</span>
            <span className="text-zinc-600 font-mono text-xs">BTC ARBITRAGE ENGINE</span>
          </div>
          <div className="flex items-center gap-4">
            {sseConnected && (
              <div className="flex items-center gap-1 text-cyan-400 text-xs font-mono">
                <Radio size={12} className="animate-pulse" /><span>SSE</span>
              </div>
            )}
            {error ? (
              <div className="flex items-center gap-2 text-red-400 text-xs font-mono"><WifiOff size={14} /><span>OFFLINE</span></div>
            ) : (
              <div className="flex items-center gap-2 text-emerald-400 text-xs font-mono"><Activity size={14} className="animate-pulse" /><span>{liveCount}/3 LIVE</span></div>
            )}
            {lastUpdate && <span className="text-zinc-600 text-xs font-mono">{lastUpdate.toLocaleTimeString('en-US', { hour12: false })}</span>}
          </div>
        </div>
      </header>

      <main className="max-w-7xl mx-auto px-6 py-6 space-y-6">
        {error && (
          <div className="bg-red-950 border border-red-900 rounded-xl p-4 flex items-center gap-3">
            <AlertTriangle size={18} className="text-red-400 shrink-0" />
            <div>
              <p className="text-red-300 font-mono text-sm font-bold">BACKEND UNREACHABLE</p>
              <p className="text-red-500 font-mono text-xs mt-0.5">{error}</p>
            </div>
          </div>
        )}

        {/* Stats bar */}
        <div className="grid grid-cols-5 gap-3">
          {[
            { label: 'EXCHANGES', value: `${liveCount}/3`, icon: <Wifi size={14} />, color: liveCount === 3 ? 'text-emerald-400' : 'text-yellow-400' },
            { label: 'UPTIME', value: status ? `${Math.floor(status.uptimeSeconds / 60)}m` : '—', icon: <Activity size={14} />, color: 'text-blue-400' },
            { label: 'ENGINE', value: engineStats ? formatNumber(engineStats.totalEvaluations) : '—', icon: <Zap size={14} />, color: 'text-purple-400' },
            { label: 'P&L', value: `$${pnl.toFixed(2)}`, icon: <TrendingUp size={14} />, color: pnl >= 0 ? 'text-emerald-400' : 'text-red-400' },
            { label: 'HEALTH', value: status?.allHealthy ? 'OK' : 'DEGRADED', icon: <Shield size={14} />, color: status?.allHealthy ? 'text-emerald-400' : 'text-red-400' },
          ].map(stat => (
            <div key={stat.label} className="bg-zinc-900 border border-zinc-800 rounded-lg p-3">
              <div className={`flex items-center gap-1.5 ${stat.color} text-xs font-mono mb-1`}>{stat.icon}<span>{stat.label}</span></div>
              <div className={`font-mono font-bold text-lg ${stat.color}`}>{stat.value}</div>
            </div>
          ))}
        </div>

        {/* Order books */}
        <div>
          <h2 className="text-zinc-500 font-mono text-xs tracking-widest mb-3">ORDER BOOKS</h2>
          <div className="grid grid-cols-3 gap-4">
            {orderBooks.length > 0
              ? orderBooks.map(book => <ExchangeCard key={`${book.exchange}-${book.symbol}`} book={book} connector={allConnectors[book.exchange]} />)
              : ['BINANCE', 'KRAKEN', 'COINBASE'].map(name => (
                  <div key={name} className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 animate-pulse">
                    <div className="h-4 bg-zinc-800 rounded w-24 mb-4" />
                    <div className="grid grid-cols-2 gap-3"><div className="h-16 bg-zinc-800 rounded-lg" /><div className="h-16 bg-zinc-800 rounded-lg" /></div>
                  </div>
                ))}
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <EnginePanel stats={engineStats} />
          <WalletsPanel stats={engineStats} />
          <CockpitControl config={liveConfig} onUpdate={updateEngineConfig} onShock={triggerShock} />
        </div>

        <ArbitrageMatrix books={orderBooks.filter(b => b.symbol === 'BTC/USDT' || !b.symbol)} />

        <div className="grid grid-cols-2 gap-4">
          <TradeHistoryTable events={tradeEvents} />
          <TriangularHistoryTable events={triangularEvents} />
        </div>

        {/* Phase indicator */}
        <div className="border border-zinc-800 rounded-xl p-4">
          <div className="flex items-center gap-3">
            <div className="flex gap-2">
              {['FASE 1', 'FASE 2', 'FASE 3', 'FASE 4', 'FASE 5'].map((phase) => (
                <div key={phase} className="px-3 py-1 rounded text-xs font-mono font-bold bg-yellow-500 text-black">{phase}</div>
              ))}
            </div>
            <span className="text-zinc-600 text-xs font-mono ml-2">WS · Engine · Wallets · SSE · Dashboard ✅</span>
          </div>
        </div>
      </main>
    </div>
  )
}
