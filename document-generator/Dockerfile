FROM maven:3.9.9-eclipse-temurin-21 AS base
WORKDIR /app

COPY pom.xml .
COPY shared/pom.xml ./shared/
COPY auth-service/pom.xml ./auth-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY document-generator/pom.xml ./document-generator/
COPY document-worker/pom.xml ./document-worker/
COPY storage-service/pom.xml ./storage-service/
COPY template-service/pom.xml ./template-service/

RUN mvn dependency:go-offline -B

FROM base AS dev
ARG SERVICE_PATH
ENV SERVICE_PATH_ENV=${SERVICE_PATH}

COPY shared/src ./shared/src
COPY ${SERVICE_PATH}/src ./${SERVICE_PATH}/src

WORKDIR /app/${SERVICE_PATH_ENV}

CMD ["mvn", "spring-boot:run"]

FROM base AS build
COPY . .
RUN mvn clean package -DskipTests -pl document-generator -am


FROM eclipse-temurin:21-jre-jammy AS prod
WORKDIR /app
COPY --from=build /app/document-generator/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]