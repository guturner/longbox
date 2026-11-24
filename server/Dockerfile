# Build on your PC (the DS923+ can build it, but slowly):
#   ./gradlew :server:buildFatJar :webApp:wasmJsBrowserDistribution
#   docker build -f server/Dockerfile -t longbox-server:0.1.0 .
#   docker save longbox-server:0.1.0 | gzip > longbox-server.tar.gz   # then import in Container Manager
# The DS923+ is x86_64 (AMD Ryzen), so the standard linux/amd64 image runs as-is.
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S longbox && adduser -S -G longbox -H longbox
WORKDIR /app
COPY server/build/libs/*-all.jar /app/longbox-server.jar
COPY webApp/build/dist/wasmJs/productionExecutable /app/web

ENV WEB_DIST_DIR=/app/web \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"
# Unprivileged user (CIS Docker Benchmark 4.1: create a user for the container).
USER longbox
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
ENTRYPOINT ["java", "-jar", "/app/longbox-server.jar"]
