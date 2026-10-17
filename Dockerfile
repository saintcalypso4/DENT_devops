# Stage 1: сборка (компиляция) Java
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY src/ ./src/
RUN javac -d bin src/App.java

# Stage 2: runtime (запуск легкого контейнера)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/bin ./bin

EXPOSE 8080

CMD ["java", "-cp", "bin", "App"]