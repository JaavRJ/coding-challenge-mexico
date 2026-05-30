# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Install dependencies
# ─────────────────────────────────────────────────────────────────────────────
FROM node:18-alpine AS deps

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --prefer-offline 2>/dev/null || npm install

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Build the application
# ─────────────────────────────────────────────────────────────────────────────
FROM node:18-alpine AS builder

WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build-time env: use empty string so the client-side JS uses relative URLs.
# At runtime, the reverse proxy or browser will resolve /api/* to the backend.
ENV NEXT_PUBLIC_API_URL=""

RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 3: Runtime — minimal standalone output
# ─────────────────────────────────────────────────────────────────────────────
FROM node:18-alpine AS runtime

WORKDIR /app

# Security: run as non-root
RUN addgroup -S nexustrade && adduser -S nexustrade -G nexustrade

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

# Copy standalone output (includes server.js + required node_modules)
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
COPY --from=builder /app/public ./public

RUN chown -R nexustrade:nexustrade /app

USER nexustrade

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:3000/ || exit 1

CMD ["node", "server.js"]
