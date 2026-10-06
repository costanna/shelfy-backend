FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S shelfy && adduser -S shelfy -G shelfy
COPY --from=build /app/target/*.jar app.jar
USER shelfy

EXPOSE 8080
# JVM ajustada per a arrencades ràpides al pla free (0,5 CPU / 512 MB):
# - TieredStopAtLevel=1: només compilador C1, arrencada molt més ràpida
#   (el rendiment pic és menor, però sobra per a aquest trànsit).
# - UseSerialGC: GC d'un sol fil, menys petjada i menys CPU a l'arrencada.
# - file:/dev/./urandom: evita bloquejos d'entropia generant el JWT/SecureRandom.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
