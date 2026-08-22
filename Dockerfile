ARG JAVA_VERSION=21

# Stage 1: Build
FROM maven:3.9.11-eclipse-temurin-${JAVA_VERSION} AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:${JAVA_VERSION}-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/subscribe-master.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
