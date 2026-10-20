FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B clean package

FROM eclipse-temurin:8-jre-jammy
LABEL org.opencontainers.image.source="https://github.com/sjj124390/-"
WORKDIR /app
RUN groupadd --system shop && useradd --system --gid shop shop
COPY --from=build --chown=shop:shop /build/target/online-shopping-backend-1.0.0.jar /app/app.jar
USER shop
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
