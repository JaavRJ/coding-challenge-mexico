# 🚀 NobaTrade — Plan de Ejecución: 3 Días

## Decisiones Confirmadas
- **Nombre:** NobaTrade (unificar todo el código y UI)
- **Wallets:** Virtuales (sin ejecución real)
- **Infra:** Supabase (PostgreSQL), Vercel (frontend), Render (backend) — todo free tier
- **Sin presupuesto** para servicios de pago

## Features Aprobadas (14 de 20)

| # | Feature | Día |
|---|---------|-----|
| 1 | 🗄️ Supabase PostgreSQL (reemplazar JSONL de 1.23 GB) | 1 |
| 4 | ⚡ WebSocket bidireccional (matar polling) | 1 |
| 16 | 💾 Persistencia de wallets (sobreviven reinicios) | 1 |
| 17 | 🎨 Unificar branding → NobaTrade | 1 |
| 5 | 📊 TradingView Lightweight Charts | 2 |
| 15 | 🔗 Conectores reales Bitfinex + OKX | 2 |
| 8 | 🔔 Telegram Bot notificaciones | 2 |
| 7 | 🤖 IA/ML: Anomaly detection + confidence score | 2 |
| 6 | 🔄 Motor de backtesting | 3 |
| 2 | 🔐 JWT Authentication (Spring Security) | 3 |
| 9 | 📱 Multi-asset (ETH, SOL, XRP) | 3 |
| 10 | 🏗️ Grafana + Prometheus monitoring | 3 |
| 3 | 🧪 Testing suite (JUnit + Vitest + Playwright) | 3 |
| 20 | 🧩 Strategy API extensible | 3 |

---

## DÍA 1: Fundamentos Sólidos

### 1.1 — Supabase PostgreSQL (Reemplazar JSONL)

**Problema actual:** `events.jsonl` = 1.23 GB, se lee completo en cada request.

**Schema en Supabase:**

```sql
-- Trades ejecutados (fuente de verdad para P&L)
CREATE TABLE trades (
  id BIGSERIAL PRIMARY KEY,
  ts TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  type VARCHAR(20) NOT NULL, -- 'DIRECT' | 'TRIANGULAR'
  status VARCHAR(30) NOT NULL,
  buy_exchange VARCHAR(20),
  sell_exchange VARCHAR(20),
  exchange VARCHAR(20), -- for triangular
  buy_price DECIMAL(18,8),
  sell_price DECIMAL(18,8),
  volume DECIMAL(18,8),
  gross_spread DECIMAL(18,8),
  fees_total DECIMAL(18,8),
  net_profit DECIMAL(18,8),
  spread_pct DECIMAL(10,6),
  decision_latency_ms INT,
  rejection_reason VARCHAR(40),
  ai_confidence DECIMAL(5,2), -- future: ML score
  metadata JSONB -- flexible extra fields
);

CREATE INDEX idx_trades_ts ON trades(ts DESC);
CREATE INDEX idx_trades_status ON trades(status);
CREATE INDEX idx_trades_type ON trades(type);

-- Wallet snapshots (persistencia de estado)
CREATE TABLE wallet_snapshots (
  id BIGSERIAL PRIMARY KEY,
  ts TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  exchange VARCHAR(20) NOT NULL,
  usdt_balance DECIMAL(18,8) NOT NULL,
  btc_balance DECIMAL(18,8) NOT NULL,
  total_usd_value DECIMAL(18,8)
);

CREATE INDEX idx_wallet_ts ON wallet_snapshots(ts DESC);

-- Engine metrics (para analytics y grafana)
CREATE TABLE engine_metrics (
  id BIGSERIAL PRIMARY KEY,
  ts TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  total_evaluations BIGINT,
  total_executed BIGINT,
  total_rejected BIGINT,
  cumulative_pnl DECIMAL(18,8),
  circuit_breaker_active BOOLEAN,
  active_exchanges INT
);

CREATE INDEX idx_metrics_ts ON engine_metrics(ts DESC);
```

**Backend changes:**
- Añadir `spring-boot-starter-data-jpa` + `postgresql` driver al `pom.xml`
- Crear entities JPA: `TradeEntity`, `WalletSnapshotEntity`, `EngineMetricEntity`
- Crear repositories Spring Data
- Nuevo `DatabasePersistenceService` que reemplaza `ShadowLearningRecorder`
- Migrar `AnalyticsController` para usar queries SQL en lugar de file scan
- Connection string via env var: `DATABASE_URL` (de Supabase)

### 1.2 — WebSocket Bidireccional

**Reemplazar:** SSE (`/api/stream`) + 4 polling endpoints

**Backend:**
- Añadir `spring-boot-starter-websocket` al pom.xml
- `WebSocketConfig.java` — STOMP broker con SockJS fallback
- Canales:
  - `/topic/trades` — nuevos trades en tiempo real
  - `/topic/orderbooks` — snapshots de order books
  - `/topic/engine` — stats del motor (evaluaciones, ejecutados, etc.)
  - `/topic/alerts` — circuit breaker, rebalanceo, anomalías
  - `/app/config` — usuario envía cambios de config

**Frontend:**
- `@stomp/stompjs` + `sockjs-client`
- Custom hook `useNobaTrade()` que maneja toda la conexión
- Eliminar TODOS los `setInterval` y `fetch` polling de `page.tsx`
- Reconexión automática con backoff

### 1.3 — Persistencia de Wallets

- `@Scheduled` cada 30 segundos: `WalletManager` → snapshot a Supabase
- On startup: `WalletManager` lee último snapshot de Supabase
- Si no hay snapshot → inicializar con defaults (100K USDT + 1 BTC)

### 1.4 — Unificar Branding → NobaTrade

- Renombrar en `page.tsx`, `Header.tsx`, `LandingPage.tsx`, `layout.tsx`
- Meta tags: title, description, og:title
- Logo text: "NOBATRADE" consistente
- Eliminar referencias a "NexusTrade" en logs del backend
- Actualizar `PROJECT_PLAN.md`

---

## DÍA 2: Diferenciadores Visuales

### 2.1 — TradingView Lightweight Charts

**Library:** `lightweight-charts` (MIT, creada por TradingView, 0 dependencias)

**Componente:** `<TradingViewChart />`
- Gráfica de velas BTC/USDT en tiempo real (datos de Binance WebSocket)
- Markers de arbitraje: punto verde cuando se ejecuta un trade, rojo cuando se rechaza
- Líneas de spread: overlay visual mostrando diferencia de precio entre exchanges
- Volume bars en la parte inferior
- Timeframes: 1m, 5m, 15m, 1h
- Datos históricos cargados desde Supabase

**Ubicación:** Panel principal del dashboard, arriba de los order books.

### 2.2 — Conectores Reales: Bitfinex + OKX

**Bitfinex:**
- WebSocket v2: `wss://api-pub.bitfinex.com/ws/2`
- Channel: `book` con symbol `tBTCUSD`, precision `P0`, length `25`
- Maneja snapshot (array of arrays) + updates (single array)
- Checksum validation para integridad del order book
- REST fallback: `https://api-pub.bitfinex.com/v2/book/tBTCUSD/P0`

**OKX:**
- WebSocket v5: `wss://ws.okx.com:8443/ws/v5/public`
- Channel: `books5` con instId `BTC-USDT`
- Maneja snapshot + incremental updates con checksum
- REST fallback: `https://www.okx.com/api/v5/market/books?instId=BTC-USDT`

### 2.3 — Telegram Bot Notificaciones

**Setup:**
- Crear bot via BotFather (gratis)
- Env vars: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`
- `TelegramNotificationService.java`:
  - Método `sendTradeAlert(trade)` — formato rico con emoji y detalles
  - Método `sendCircuitBreakerAlert()`
  - Método `sendDailySummary()` — `@Scheduled(cron = "0 0 0 * * *")`
- Throttle: máximo 1 mensaje cada 5 segundos (rate limit de Telegram)

**Mensaje ejemplo:**
```
🟢 TRADE EJECUTADO — NobaTrade

📈 BTC/USDT | BINANCE → KRAKEN
💰 Profit: +$12.40 USDT
📊 Spread: 0.58%
⚡ Latencia: 2ms
🤖 AI Score: 87/100

📋 P&L Acumulado: +$154.18
```

### 2.4 — IA/ML: Detección de Anomalías + Score

**Implementación ligera (sin dependencias ML pesadas):**

`AnomalyDetector.java`:
- Rolling window de 100 spreads por ruta
- Z-score: si spread actual > 2σ del promedio → flag como anomalía
- Categorías: `NORMAL`, `ELEVATED`, `ANOMALOUS`, `EXTREME`

`ConfidenceScorer.java`:
- Features: spread_magnitude, volume_available, order_book_depth, latency, hora_del_dia, volatilidad_reciente
- Score 0-100 basado en weighted scoring:
  - Spread > 2x min_roi → +30 pts
  - Volume > $1000 → +20 pts
  - Order book depth > 10 levels → +15 pts
  - Latency < 100ms → +15 pts
  - Hora activa (14:00-22:00 UTC) → +10 pts
  - Volatilidad baja → +10 pts

**Frontend:** Badge visual en cada trade: `AI: 87/100` con color gradient (rojo→amarillo→verde).

---

## DÍA 3: Profesionalización

### 3.1 — Motor de Backtesting

**Endpoint:** `POST /api/backtest`
```json
{
  "startDate": "2026-07-01",
  "endDate": "2026-07-09",
  "minRoiPct": 0.003,
  "walletExposurePct": 0.15,
  "exchanges": ["BINANCE", "KRAKEN", "COINBASE"],
  "feeMultiplier": 1.0
}
```

**Lógica:**
- Lee trades históricos de Supabase (ya migrados)
- Simula el motor con los parámetros dados
- Calcula: Sharpe ratio, max drawdown, win rate, profit factor, equity curve

**Frontend:** `<BacktestPanel />`
- Form con parámetros de estrategia
- Gráfica de equity curve resultante (Recharts)
- Tabla comparativa si se corren múltiples backtests
- KPIs: Sharpe, Sortino, Max DD, Win Rate

### 3.2 — JWT Authentication

**Backend:**
- `spring-boot-starter-security` + `jjwt`
- `AuthController.java`: `POST /api/auth/login`, `POST /api/auth/register`
- Users table en Supabase: `id, email, password_hash, role, created_at`
- JWT con 24h expiry, refresh token de 7 días
- Role-based: `ADMIN` (todo), `VIEWER` (solo lectura)
- Endpoints públicos: `/api/auth/*`, `/api/status`, `/actuator/health`
- Todo lo demás requiere JWT en header `Authorization: Bearer <token>`

**Frontend:**
- Login page minimalista con branding NobaTrade
- Context provider `AuthContext` con token management
- Redirect a login si no autenticado
- Token refresh automático

### 3.3 — Multi-Asset

**Pares adicionales:**
- ETH/USDT (Binance ya tiene stream, Kraken y Coinbase soportan)
- SOL/USDT (Binance, OKX)
- XRP/USDT (Binance, Kraken)

**Cambios:**
- `ConnectorRegistry` maneja múltiples order books por exchange (Map<String, Map<String, OrderBook>>)
- `ArbitrageEngine` evalúa todos los pares configurados
- UI: selector de par activo, vista de portfolio multi-asset

### 3.4 — Grafana + Prometheus

**docker-compose additions:**
```yaml
prometheus:
  image: prom/prometheus:latest
  ports: ["9090:9090"]
  volumes: ["./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml"]

grafana:
  image: grafana/grafana:latest
  ports: ["3001:3000"]
  volumes: ["./monitoring/grafana/dashboards:/var/lib/grafana/dashboards"]
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=nobatrade
```

**Dashboards pre-configurados:**
- Trading Performance (P&L, win rate, trades/hour)
- System Health (JVM memory, GC pauses, thread count)
- Exchange Connectivity (latency per connector, reconnections)

### 3.5 — Testing Suite

**Backend (JUnit 5 + Mockito):**
- `SpreadCalculatorTest` — verify spread calculation with known values
- `CircuitBreakerTest` — verify trigger after N losses
- `WalletManagerTest` — verify buy/sell/transfer operations
- `SlippageEstimatorTest` — verify VWAP calculation

**Frontend (Vitest):**
- `ExecutedTradesFeed.test.tsx` — verify rendering with mock data
- `TradingViewChart.test.tsx` — verify chart initialization

### 3.6 — Strategy API

**Interface:**
```java
public interface TradingStrategy {
    String name();
    String description();
    List<TradeSignal> evaluate(Map<String, OrderBook> orderBooks, EngineConfig config);
}
```

**Built-in strategies:**
- `DirectArbitrageStrategy` (actual)
- `TriangularArbitrageStrategy` (actual)
- `StatisticalArbitrageStrategy` (nuevo — mean reversion)

**UI:** Dropdown para seleccionar estrategia activa + toggle para cada una.

---

## Resumen de Entregables por Día

### Día 1 → El sistema es SÓLIDO
- ✅ Base de datos real (no más JSONL de 1.23 GB)
- ✅ Comunicación instantánea (WebSocket, no polling)
- ✅ Estado persiste entre reinicios
- ✅ Marca unificada: NobaTrade

### Día 2 → El sistema es IMPRESIONANTE
- ✅ Charts profesionales estilo TradingView
- ✅ 5 exchanges reales (no mocks)
- ✅ Alertas por Telegram
- ✅ Inteligencia artificial integrada

### Día 3 → El sistema es COMPLETO
- ✅ Backtesting con datos históricos
- ✅ Login seguro con JWT
- ✅ Multi-asset (BTC, ETH, SOL, XRP)
- ✅ Monitoreo profesional (Grafana)
- ✅ Tests automatizados
- ✅ Arquitectura extensible (Strategy API)

---

> [!IMPORTANT]
> **¿Apruebas este plan para arrancar ejecución inmediata?** Al confirmar, empiezo con el Día 1 usando subagentes paralelos para maximizar velocidad.
