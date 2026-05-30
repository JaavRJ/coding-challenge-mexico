# NexusTrade — BTC Arbitrage Engine

High-frequency Bitcoin arbitrage monitoring system. CODING CHALLENGE MEXICO.

## Quick Start

### Prerequisites
- Java 21+ (`java -version`)
- Maven 3.8+ or use the included `./mvnw`
- Node.js 18+
- Docker (optional)

---

### Backend (Spring Boot)

```bash
cd backend

# Run locally
./mvnw spring-boot:run

# Or build JAR
./mvnw clean package -DskipTests
java -jar target/nexustrade-backend-1.0.0.jar
```

Backend starts on **http://localhost:8080**

Endpoints:
- `GET /api/status` — connector states
- `GET /api/orderbooks` — live best bid/ask from all exchanges
- `GET /actuator/health` — health check

---

### Frontend (Next.js)

```bash
cd frontend
npm install
npm run dev
```

Frontend starts on **http://localhost:3000**

---

### Docker (Full Stack)

```bash
# From project root
docker-compose up --build
```

---

## Architecture (Phase 1)

```
Binance WS ──┐
Kraken WS  ──┤── ConnectorRegistry ──► OrderBook (ConcurrentSkipListMap)
Coinbase WS ─┘         │
                        └──► REST /api/orderbooks ──► Next.js Dashboard
```

Each connector runs in its own **Virtual Thread** (Java 21 Project Loom).
If WebSocket fails 5 times → automatic REST fallback with 10s WS reconnect attempts.

---

## Phases

| Phase | Status | Description |
|-------|--------|-------------|
| **1** | ✅ | WS connectors (Binance, Kraken, Coinbase), OrderBook, Status API, Dashboard skeleton |
| **2** | 🔜 | ArbitrageEngine, SpreadCalculator, VWAP slippage |
| **3** | 🔜 | WalletManager, Shadow Learning, SLO P95, JSONL export |
| **4** | 🔜 | SSE streaming endpoint |
| **5** | 🔜 | Full dashboard with P&L, trade history, real-time charts |
