ARG JAVA_VERSION=21

# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.11-eclipse-temurin-${JAVA_VERSION} AS builder
WORKDIR /build

# Download dependencies first (cached layer — only re-runs when pom.xml changes)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build the application (skips tests — run them in CI before building the image)
COPY src ./src
RUN mvn clean package -DskipTests -q

# Extract layered JAR for optimal Docker layer caching
RUN java -Djarmode=layertools -jar target/subscribe-master.jar extract --destination target/extracted

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Copy layered JAR contents (most-stable layers first for cache efficiency)
COPY --from=builder /build/target/extracted/dependencies/ ./
COPY --from=builder /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/ ./

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "org.springframework.boot.loader.launch.JarLauncher"]
