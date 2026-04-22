# ─────────────────────────────────────────────
# 1. Build stage (Maven)
# ─────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build Spring Boot JAR (skip tests for faster build)
RUN mvn clean package -DskipTests

# ─────────────────────────────────────────────
# 2. Runtime stage (lightweight JDK)
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy only the built jar
COPY --from=build /app/target/*.jar app.jar

# Expose port (Render will override with PORT env)
EXPOSE 8080

# ─────────────────────────────────────────────
# 3. Run application
# ─────────────────────────────────────────────
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]

