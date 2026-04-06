FROM legaldocsgpt-base:latest AS build
WORKDIR /app

COPY shared/src           ./shared/src
COPY storage-service/src  ./storage-service/src

RUN mvn clean package -DskipTests -pl storage-service -am -B

FROM eclipse-temurin:21-jre-jammy AS prod
WORKDIR /app

RUN groupadd -r appgroup && useradd -r -g appgroup appuser
USER appuser

COPY --from=build /app/storage-service/target/*.jar app.jar

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]