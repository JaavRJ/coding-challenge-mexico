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

## Architecture (Completed System)

```
Binance WS ──┐
Kraken WS  ──┤── ConnectorRegistry ──► OrderBook (ConcurrentSkipListMap)
Coinbase WS ─┘         │
                       ▼
               ArbitrageEngine ◄──► SpreadCalculator (VWAP & Fees)
                       │
             ┌─────────┴─────────┐
             ▼                   ▼
       WalletManager       CircuitBreaker
   (Shadow Trading)    (Risk Management)
             │
             ├───────────────────► ShadowLearningRecorder (events.jsonl)
             │
             └──► SSE (/api/stream) ──► Next.js Live Dashboard
```

Each connector runs in its own **Virtual Thread** (Java 21 Project Loom).
If WebSocket fails 5 times → automatic REST fallback with 10s WS reconnect attempts.

---

## Phases

| Phase | Status | Description |
|-------|--------|-------------|
| **1** | ✅ | WS connectors (Binance, Kraken, Coinbase), OrderBook, Status API, Dashboard skeleton |
| **2** | ✅ | ArbitrageEngine, SpreadCalculator, VWAP slippage |
| **3** | ✅ | WalletManager, Shadow Learning, SLO P95, JSONL export, Circuit Breaker |
| **4** | ✅ | REST endpoints, SSE streaming endpoint |
| **5** | ✅ | Full dashboard with live P&L, SSE trade history table, Real-time Arbitrage Matrix |

---

## 🚀 Production Deployment (Vercel + Render)

This project is optimized for cloud deployment with minimal latency.

### 1. Backend Deployment (Render)
Render is an excellent platform for deploying Docker containers directly from GitHub.

1. Create a new account in [Render.com](https://render.com/).
2. Create a new **Web Service** and connect your GitHub repository.
3. Set the **Root Directory** to `backend`.
4. Render will automatically detect the Dockerfile (Runtime: Docker).
5. Under **Environment Variables**, add:
   - `PORT=8080`
   - `SPRING_PROFILES_ACTIVE=prod`
   - `FRONTEND_URL=https://<your-vercel-domain>.vercel.app`
6. Click Deploy.

**⚠️ IMPORTANT NOTE FOR RENDER FREE TIER:** 
Render's free tier spins down your application after 15 minutes of inactivity. To keep your Arbitrage Engine running 24/7, go to [cron-job.org](https://cron-job.org/) and create a free cron job that pings your backend URL (`https://<your-render-domain>.onrender.com/api/status`) every 10 minutes.

### 2. Frontend Deployment (Vercel)
Vercel is recommended for the Next.js frontend to utilize Edge delivery.

1. Import your GitHub repository in [Vercel](https://vercel.com/).
2. Set the **Framework Preset** to Next.js.
3. Set the **Root Directory** to `frontend`.
4. Add the Environment Variable:
   - `NEXT_PUBLIC_API_URL=https://<your-render-domain>.onrender.com`
5. Deploy.

**Note on WebSockets & SSE:** The backend includes a 15-second heartbeat ping to keep Server-Sent Events (SSE) connections alive behind cloud reverse proxies. CORS is dynamically configured via the `FRONTEND_URL` variable.
