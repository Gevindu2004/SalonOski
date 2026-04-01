FROM eclipse-temurin:21-jdk-alpine AS builder
# If Java 24 is strictly required, you might need a different base image, e.g., ubuntu with openjdk-24
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew build -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
