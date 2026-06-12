FROM eclipse-temurin:24-jdk-alpine AS build

WORKDIR /workspace

ARG SERVICE_NAME=api-gateway

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY lawai-common/pom.xml lawai-common/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY billing-service/pom.xml billing-service/pom.xml
COPY legal-service/pom.xml legal-service/pom.xml
COPY document-service/pom.xml document-service/pom.xml
COPY research-service/pom.xml research-service/pom.xml
COPY platform-service/pom.xml platform-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml

RUN chmod +x mvnw && ./mvnw -q -pl ${SERVICE_NAME} -am dependency:go-offline

COPY lawai-common lawai-common
COPY auth-service auth-service
COPY billing-service billing-service
COPY legal-service legal-service
COPY document-service document-service
COPY research-service research-service
COPY platform-service platform-service
COPY api-gateway api-gateway

RUN ./mvnw -q -pl ${SERVICE_NAME} -am package -DskipTests

FROM eclipse-temurin:24-jre-alpine

WORKDIR /app

ARG SERVICE_NAME=api-gateway
ENV AUTH_SERVICE_URL=http://auth-service:8081
ENV BILLING_SERVICE_URL=http://billing-service:8082
ENV LEGAL_SERVICE_URL=http://legal-service:8083
ENV DOCUMENT_SERVICE_URL=http://document-service:8084
ENV RESEARCH_SERVICE_URL=http://research-service:8085
ENV PLATFORM_SERVICE_URL=http://platform-service:8086

COPY --from=build /workspace/${SERVICE_NAME}/target/${SERVICE_NAME}-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
