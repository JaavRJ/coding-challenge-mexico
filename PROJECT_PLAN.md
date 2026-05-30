# PROJECT_PLAN.md: NexusTrade — High-Frequency BTC Arbitrage System
> **CODING CHALLENGE MEXICO** · Versión 2.0 · Arquitectura Revisada

---

## 1. Visión Global

**NexusTrade** es un sistema de arbitraje de Bitcoin diseñado para detectar y simular oportunidades de precio entre múltiples exchanges en tiempo real. El sistema monitorea *order books* de forma concurrente, evalúa la rentabilidad neta de cada divergencia (considerando fees, slippage y latencia), ejecuta simulaciones con gestión real de inventario y expone toda la información a un dashboard reactivo de alta fidelidad.

**Filosofía de diseño:** *Primero la correctitud, luego la velocidad.* Cada decisión arquitectónica prioriza la consistencia de datos bajo concurrencia alta sobre la optimización prematura.

---

## 2. Tech Stack

| Capa | Tecnología | Justificación |
|---|---|---|
| **Backend Core** | Java 21 + Spring Boot 3.3 | Virtual Threads (Project Loom), WebFlux para SSE, concurrencia sin bloqueo |
| **Estructuras internas** | `ConcurrentSkipListMap`, `AtomicReference`, `LongAdder` | Thread-safe sin `synchronized`, latencia predecible |
| **Métricas** | Micrometer + Actuator | P95/P99 histogramas nativos, exposición via `/actuator/metrics` |
| **Serialización** | Jackson 2.17 con módulos extra | Parseo de JSON tolerante a fallos |
| **Frontend** | Next.js 14 (App Router) + TypeScript + Tailwind CSS | SSR, streaming, componentes reactivos |
| **Comunicación F/B** | Server-Sent Events (SSE) | Unidireccional, sin overhead de WS, ideal para dashboards |
| **Persistencia** | Append-only `.jsonl` local | Sin dependencias externas, reproducible, portátil |
| **Despliegue** | Docker Multi-stage (`eclipse-temurin:21-jre-alpine`) | Imagen final < 120MB |

---

## 3. Exchanges Soportados

| Exchange | Protocolo | Endpoint BTC/USDT | Fee Maker/Taker |
|---|---|---|---|
| **Binance** | WebSocket | `wss://stream.binance.com:9443/ws/btcusdt@depth5@100ms` | 0.10% / 0.10% |
| **Kraken** | WebSocket v2 | `wss://ws.kraken.com/v2` | 0.16% / 0.26% |
| **Coinbase** | WebSocket Advanced | `wss://advanced-trade-ws.coinbase.com` | 0.40% / 0.60% |

> **Nota:** Coinbase se añade como tercer exchange para triplicar las combinaciones de arbitraje posibles (Binance↔Kraken, Binance↔Coinbase, Kraken↔Coinbase) y aumentar significativamente la frecuencia de oportunidades detectadas.

---

## 4. Killer Features (Requeridas)

### 4.1 Multithreading Real con Virtual Threads (Java 21)
Un `VirtualThread` dedicado por cada conector de exchange. Spring Boot 3.3 con `spring.threads.virtual.enabled=true` convierte automáticamente el pool de Tomcat en virtual threads. El Engine de arbitraje corre en su propio hilo de plataforma de alta prioridad para garantizar latencia determinista.

### 4.2 REST Fallback con Circuit Breaker
- Ingesta primaria: WebSocket con reconexión exponential backoff (1s → 2s → 4s → 8s → 16s)
- Tras 5 fallos consecutivos: switch automático a polling REST (cada 500ms)
- Reconexión al WebSocket cada 10s con `ScheduledExecutorService`
- Estado del circuit expuesto via API: `GET /api/status`

### 4.3 Reequilibrio Virtual de Inventario
El `WalletManager` monitorea la proporción USDT/BTC. Si la asimetría supera el **40%** entre exchanges (medida como: `|wallet_A - wallet_B| / total_wallet > 0.40`), activa una rutina de rebalanceo virtual que redistribuye el inventario ficticio sin interrumpir las operaciones en curso.

### 4.4 Shadow Learning (Registro de Rechazos)
Todo spread detectado genera un `OpportunityEvent`:
- Si es **rentable**: se simula y registra como `EXECUTED`
- Si se rechaza: se clasifica y registra con razón exacta:
  - `REJECTED_FEES` — spread bruto < fees combinados
  - `REJECTED_SLIPPAGE` — impacto de precio > umbral configurado
  - `REJECTED_LATENCY` — tiempo desde detección > 200ms
  - `REJECTED_LIQUIDITY` — volumen disponible < mínimo operativo
  - `REJECTED_CIRCUIT_BREAKER` — bot en pausa por pérdidas

### 4.5 Métricas SLO P95/P99
El `LatencyTracker` usa `Timer` de Micrometer para medir:
- `nexustrade.decision.latency` — desde ingesta de JSON hasta decisión del engine
- `nexustrade.orderbook.staleness` — antigüedad del order book en milisegundos
- `nexustrade.spread.gross` — distribución de spreads brutos detectados

Objetivo SLO: P95 de decisión < **50ms**.

### 4.6 Manejo Estricto de Excepciones
- **Regla de Oro:** Ningún `NullPointerException` o JSON malformado puede propagarse más allá del conector
- Todos los parseos usan `Optional<T>` con fallback explícito
- Los métodos de colecciones devuelven `boolean` de éxito/fallo
- Logging estructurado con nivel `WARN` para datos descartados, nunca `ERROR` fatal por datos externos

---

## 5. Arquitectura de Módulos

```
nexustrade-backend/
├── connector/
│   ├── ExchangeConnector.java          # Interface base
│   ├── BinanceConnector.java           # WS + REST fallback
│   ├── KrakenConnector.java            # WS + REST fallback
│   ├── CoinbaseConnector.java          # WS + REST fallback
│   └── ConnectorState.java             # CONNECTING/LIVE/FALLBACK/DEAD
│
├── model/
│   ├── OrderBook.java                  # ConcurrentSkipListMap<Price, Volume>
│   ├── OrderBookSnapshot.java          # Inmutable, para el engine
│   ├── ArbitrageOpportunity.java       # Spread, profit, metadata
│   ├── TradeEvent.java                 # Executed or Rejected
│   └── Wallet.java                     # AtomicLong para thread-safety
│
├── engine/
│   ├── ArbitrageEngine.java            # Hilo maestro, evalúa cada tick
│   ├── SpreadCalculator.java           # Lógica VWAP y fee netting
│   └── SlippageEstimator.java          # Consume niveles del order book
│
├── risk/
│   ├── WalletManager.java              # Saldos virtuales + rebalanceo
│   ├── CircuitBreaker.java             # Pausa tras pérdidas consecutivas
│   └── RiskConfig.java                 # Umbrales configurables
│
├── metrics/
│   ├── LatencyTracker.java             # Micrometer Timers
│   └── ShadowLearningRecorder.java     # Append-only JSONL writer
│
└── api/
    ├── DashboardController.java         # SSE endpoint: /api/stream
    ├── StatusController.java            # REST: /api/status
    └── HistoryController.java           # REST: /api/history
```

---

## 6. Modelo de Datos — Eventos JSONL

Cada línea del archivo de log es un JSON independiente:

```json
{
  "ts": 1718000000000,
  "type": "OPPORTUNITY",
  "buyExchange": "BINANCE",
  "sellExchange": "KRAKEN",
  "buyPrice": 67420.50,
  "sellPrice": 67510.00,
  "volume": 0.05,
  "grossSpread": 89.50,
  "netProfit": 22.30,
  "feesTotal": 67.20,
  "status": "EXECUTED",
  "rejectionReason": null,
  "decisionLatencyMs": 12
}
```

---

## 7. Ecuación de Rentabilidad

Para un volumen **V** en BTC:

```
Beneficio_Neto = [P_bid × V × (1 - f_sell)] - [P_ask × V × (1 + f_buy)] - withdrawal_fee
```

Donde:
- `P_ask` = precio promedio ponderado (VWAP) de compra en exchange A
- `P_bid` = precio promedio ponderado (VWAP) de venta en exchange B
- `f_buy`, `f_sell` = fees del exchange correspondiente (decimal)
- `withdrawal_fee` = costo fijo de retiro de BTC (en USD equivalente)

**Condición de ejecución:** `Beneficio_Neto > MIN_PROFIT_USD` (configurable, default: $5.00)

---

## 8. Circuit Breaker

| Condición | Acción | Duración |
|---|---|---|
| 3 pérdidas netas consecutivas | Pausa total | 60 segundos |
| Balance global cae 2% | Pausa + alerta | 60 segundos |
| Spread detectado pero latencia > 200ms | Rechaza oportunidad | Por evento |
| Exchange sin datos > 5 segundos | Marca como `STALE` | Hasta refresh |

---

## 9. Plan de Fases

| Fase | Contenido | Criterio de Éxito |
|---|---|---|
| **FASE 1** | Proyecto Spring Boot + Docker + conexión WS a 3 exchanges | Best Bid/Ask imprimiéndose en consola de los 3 exchanges |
| **FASE 2** | Order Book en memoria + Motor de detección de spreads | Logs de oportunidades detectadas (brutas y netas) |
| **FASE 3** | WalletManager + Shadow Learning + SLO P95 + export JSONL | Trades simulados con balances actualizados y archivo de log |
| **FASE 4** | API REST + SSE para streaming de eventos al frontend | `curl /api/stream` muestra eventos en tiempo real |
| **FASE 5** | Dashboard Next.js — Cockpit de operaciones | UI con order books live, P&L acumulado, tabla de trades |

---

## 10. Configuración (`application.yml`)

```yaml
nexustrade:
  engine:
    min-profit-usd: 5.0
    max-volume-btc: 0.1
    decision-timeout-ms: 200
  risk:
    circuit-breaker-losses: 3
    circuit-breaker-pause-seconds: 60
    max-balance-drawdown-pct: 2.0
    rebalance-threshold-pct: 40.0
  wallet:
    initial-usdt: 100000.0
    initial-btc: 1.0
  exchanges:
    binance:
      ws-url: "wss://stream.binance.com:9443/ws/btcusdt@depth5@100ms"
      fee: 0.001
    kraken:
      ws-url: "wss://ws.kraken.com/v2"
      fee: 0.0026
    coinbase:
      ws-url: "wss://advanced-trade-ws.coinbase.com"
      fee: 0.006
```

---

## 11. Ejecución Rápida

```bash
# Clonar y compilar
git clone https://github.com/tu-usuario/nexustrade
cd nexustrade/backend
./mvnw clean package -DskipTests

# Docker
docker build -t nexustrade-backend .
docker run -p 8080:8080 nexustrade-backend

# Frontend
cd ../frontend
npm install && npm run dev
```

---

*Última actualización: Fase 1 implementada. Conectores Binance, Kraken y Coinbase activos.*
