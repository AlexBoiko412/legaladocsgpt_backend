ARG BASE_IMAGE=legaldocsgpt-base:latest
FROM ${BASE_IMAGE} AS build
WORKDIR /app

COPY shared/src           ./shared/src
COPY document-worker/src  ./document-worker/src

RUN mvn clean package -DskipTests -pl document-worker -am -B

FROM eclipse-temurin:21-jre-jammy AS prod
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

COPY --from=build /app/document-worker/target/*.jar app.jar

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]