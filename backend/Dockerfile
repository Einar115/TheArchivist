####### Build stage #######
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY build.gradle settings.gradle ./

RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew clean build -x test --no-daemon

###### Run stage #######
FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]