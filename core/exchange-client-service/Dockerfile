FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

COPY common/build.gradle common/build.gradle
COPY user-service/build.gradle user-service/build.gradle
COPY account-service/build.gradle account-service/build.gradle
COPY transaction-service/build.gradle transaction-service/build.gradle
COPY holdings-service/build.gradle holdings-service/build.gradle
COPY trade-service/build.gradle trade-service/build.gradle
COPY exchange-client-service/build.gradle exchange-client-service/build.gradle
COPY notification-service/build.gradle notification-service/build.gradle
COPY user-gateway/build.gradle user-gateway/build.gradle
COPY bot-gateway/build.gradle bot-gateway/build.gradle

COPY common/src/ common/src/
COPY exchange-client-service/src/ exchange-client-service/src/

RUN chmod +x gradlew && ./gradlew :exchange-client-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/exchange-client-service/build/libs/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
