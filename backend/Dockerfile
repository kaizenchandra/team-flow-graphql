FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml ./pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline
COPY backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests
FROM eclipse-temurin:21.0.8_9-jre-jammy
RUN groupadd --gid 10001 teamflow && useradd --uid 10001 --gid teamflow --no-create-home teamflow
WORKDIR /app
COPY --from=build /src/target/teamflow-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s CMD curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
