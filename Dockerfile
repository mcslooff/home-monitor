# Fase 1: Build de applicatie (Optioneel, maar aanbevolen)
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Fase 2: De daadwerkelijke runtime container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Kopieer de JAR vanuit de build-fase (of direct vanaf je schijf als je lokaal al buildt)
COPY --from=build /app/target/bme680-0.0.1-SNAPSHOT.jar app.jar

# Maak een niet-root gebruiker aan om de app veiliger te draaien
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Expose de poort waarop je applicatie draait (meestal 8080)
EXPOSE 8180 1883

# Start de applicatie met optimale JVM instellingen voor containers
ENTRYPOINT ["java", "-jar", "app.jar"]
