# ─────────────────────────────────────────────────────────────────────────────
# Stage 1: Build — official Maven image with Temurin JDK 21 (SSL preconfigured)
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom first (layer cache optimization for dependencies)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Runtime — ultra-minimal image
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root
RUN addgroup -S nexustrade && adduser -S nexustrade -G nexustrade

WORKDIR /app

# Data directory for JSONL persistence
RUN mkdir -p /app/data && chown nexustrade:nexustrade /app/data

COPY --from=builder /build/target/*.jar app.jar
RUN chown nexustrade:nexustrade app.jar

USER nexustrade

# JVM flags optimized for low-latency, containerized environment
ENV JAVA_OPTS="\
  -server \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:MaxGCPauseMillis=5 \
  -Xms256m -Xmx512m \
  -XX:+UseContainerSupport \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.threads.virtual.enabled=true"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Xms256m", "-Xmx450m", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "-jar", "app.jar"]
