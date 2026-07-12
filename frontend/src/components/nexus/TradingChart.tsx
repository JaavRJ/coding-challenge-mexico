'use client'
import { useEffect, useRef, useState, useCallback } from 'react'

const TIMEFRAMES = ['1m', '5m', '15m', '1h'] as const
type TF = typeof TIMEFRAMES[number]

interface Candle {
  time: number
  open: number
  high: number
  low: number
  close: number
  volume: number
}

interface TradeMarker {
  ts: number
  netProfit: number
  buyExchange: string
  sellExchange: string
}

interface Props {
  executedTrades?: TradeMarker[]
}

const TF_INTERVAL: Record<TF, string> = { '1m': '1m', '5m': '5m', '15m': '15m', '1h': '1h' }

async function fetchCandles(tf: TF): Promise<Candle[]> {
  const url = `https://api.binance.com/api/v3/klines?symbol=BTCUSDT&interval=${TF_INTERVAL[tf]}&limit=120`
  const res = await fetch(url)
  const data = await res.json()
  return data.map((k: any[]) => ({
    time: Math.floor(k[0] / 1000),
    open: parseFloat(k[1]),
    high: parseFloat(k[2]),
    low: parseFloat(k[3]),
    close: parseFloat(k[4]),
    volume: parseFloat(k[5]),
  }))
}

export function TradingChart({ executedTrades = [] }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<any>(null)
  const candleSeriesRef = useRef<any>(null)
  const [tf, setTf] = useState<TF>('5m')
  const [price, setPrice] = useState<number | null>(null)
  const [priceChange, setPriceChange] = useState<number>(0)
  const [loading, setLoading] = useState(true)

  const initChart = useCallback(async () => {
    if (!containerRef.current) return
    // Dynamically import to avoid SSR issues
    const { createChart } = await import('lightweight-charts')

    // Clean up previous chart
    if (chartRef.current) { chartRef.current.remove(); chartRef.current = null }

    const chart = createChart(containerRef.current, {
      layout: {
        background: { color: 'transparent' },
        textColor: 'rgba(255,255,255,0.45)',
      },
      grid: {
        vertLines: { color: 'rgba(255,255,255,0.04)' },
        horzLines: { color: 'rgba(255,255,255,0.04)' },
      },
      crosshair: { mode: 1 },
      timeScale: {
        borderColor: 'rgba(255,255,255,0.08)',
        timeVisible: true,
        secondsVisible: false,
      },
      rightPriceScale: { borderColor: 'rgba(255,255,255,0.08)' },
      width: containerRef.current.clientWidth,
      height: 220,
    })
    chartRef.current = chart

    const candleSeries = chart.addCandlestickSeries({
      upColor: '#22c55e',
      downColor: '#ef4444',
      borderUpColor: '#22c55e',
      borderDownColor: '#ef4444',
      wickUpColor: '#22c55e',
      wickDownColor: '#ef4444',
    })
    candleSeriesRef.current = candleSeries

    const volSeries = chart.addHistogramSeries({
      color: 'rgba(34,197,94,0.25)',
      priceFormat: { type: 'volume' } as any,
      priceScaleId: 'vol',
    })
    chart.priceScale('vol').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } })

    setLoading(true)
    try {
      const candles = await fetchCandles(tf)
      candleSeries.setData(candles as any)
      volSeries.setData(candles.map(c => ({
        time: c.time as any,
        value: c.volume,
        color: c.close >= c.open ? 'rgba(34,197,94,0.35)' : 'rgba(239,68,68,0.35)',
      })))

      if (candles.length) {
        const last = candles[candles.length - 1]
        const first = candles[0]
        setPrice(last.close)
        setPriceChange(((last.close - first.open) / first.open) * 100)
      }
    } catch (err) {
      console.warn('Could not load Binance klines:', err)
    } finally {
      setLoading(false)
    }

    chart.timeScale().fitContent()

    const ro = new ResizeObserver(() => {
      if (containerRef.current && chartRef.current) {
        chartRef.current.applyOptions({ width: containerRef.current.clientWidth })
      }
    })
    ro.observe(containerRef.current)

    return () => { ro.disconnect() }
  }, [tf])

  useEffect(() => {
    let cleanup: (() => void) | undefined
    initChart().then(fn => { cleanup = fn })
    return () => { cleanup?.(); if (chartRef.current) { chartRef.current.remove(); chartRef.current = null } }
  }, [initChart])

  // Update markers separately without reloading/recreating chart
  useEffect(() => {
    if (!candleSeriesRef.current || !executedTrades) return
    const markers = executedTrades
      .filter(t => t.ts && t.ts > 0)
      .map(t => ({
        time: Math.floor(t.ts / 1000) as any,
        position: 'belowBar' as const,
        color: t.netProfit >= 0 ? '#22c55e' : '#ef4444',
        shape: 'arrowUp' as const,
        text: `${t.buyExchange}→${t.sellExchange} +$${t.netProfit.toFixed(1)}`,
      }))
      .sort((a, b) => (a.time as number) - (b.time as number))

    if (markers.length) candleSeriesRef.current.setMarkers(markers)
  }, [executedTrades])

  // Live price via Binance miniTicker WebSocket
  useEffect(() => {
    const ws = new WebSocket('wss://stream.binance.com:9443/ws/btcusdt@miniTicker')
    ws.onmessage = (e) => {
      try {
        const d = JSON.parse(e.data)
        const livePrice = parseFloat(d.c)
        setPrice(livePrice)
        if (candleSeriesRef.current && d.t) {
          // update last candle close
          candleSeriesRef.current.update({
            time: Math.floor(d.t / 1000),
            open: parseFloat(d.o),
            high: parseFloat(d.h),
            low: parseFloat(d.l),
            close: livePrice,
          })
        }
      } catch {}
    }
    ws.onerror = () => ws.close()
    return () => ws.close()
  }, [])

  return (
    <div className="nx-panel p-4 space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-[10px] font-mono uppercase tracking-widest text-foreground/40">BTC/USDT \u00b7 Binance Live</div>
          <div className="flex items-baseline gap-3">
            {price ? (
              <span className="text-2xl font-bold font-mono text-foreground">
                ${price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </span>
            ) : (
              <span className="text-2xl font-bold font-mono text-foreground/30">Loading...</span>
            )}
            <span className={`text-sm font-mono font-medium ${
              priceChange >= 0 ? 'text-profit' : 'text-loss'
            }`}>
              {priceChange >= 0 ? '+' : ''}{priceChange.toFixed(2)}%
            </span>
          </div>
          {executedTrades.length > 0 && (
            <div className="text-[10px] font-mono text-foreground/30 mt-1">
              {executedTrades.length} trades marcados en la gr\u00e1fica
            </div>
          )}
        </div>
        <div className="flex gap-1">
          {TIMEFRAMES.map(t => (
            <button
              key={t}
              onClick={() => setTf(t)}
              className={`px-2.5 py-1 text-[10px] font-mono uppercase tracking-widest border transition-all ${
                tf === t
                  ? 'bg-foreground text-background border-foreground'
                  : 'border-foreground/20 text-foreground/50 hover:border-foreground/40 hover:text-foreground/80'
              }`}
            >
              {t}
            </button>
          ))}
        </div>
      </div>
      <div className="relative">
        {loading && (
          <div className="absolute inset-0 flex items-center justify-center z-10">
            <div className="text-[10px] font-mono uppercase tracking-widest text-foreground/30 animate-pulse">Cargando datos...</div>
          </div>
        )}
        <div ref={containerRef} className="w-full" />
      </div>
      <div className="flex gap-4 text-[9px] font-mono uppercase tracking-widest text-foreground/30">
        <span>\ud83d\udfe2 Trade ejecutado (marcado)</span>
        <span>\ud83d\udd34 Trade rechazado</span>
      </div>
    </div>
  )
}
