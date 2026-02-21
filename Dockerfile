FROM eclipse-temurin:23-jre-alpine

LABEL maintainer="PatternForge"
LABEL description="Context-Aware RAG System for Code Patterns"

WORKDIR /app

# Copy the compiled JAR
COPY target/pattern-forge-1.0.0-SNAPSHOT.jar app.jar

# Expose application and debug ports
EXPOSE 15550 8765

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:15550/actuator/health || exit 1

# Run with optimized JVM settings for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
