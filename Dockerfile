FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

RUN mkdir -p lib && \
    apk add --no-cache curl ca-certificates && \
    curl -fsSL -o lib/postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar && \
    curl -fsSL -o lib/json.jar https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar

COPY src/ ./src/
RUN javac -cp "lib/*" -d bin src/App.java

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

COPY --from=builder /app/bin ./bin
COPY --from=builder /app/lib ./lib

EXPOSE 8080

CMD ["java", "-cp", "bin:lib/*", "App"]