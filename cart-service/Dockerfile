FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml ./pom.xml
COPY cart-service/pom.xml ./cart-service/pom.xml
COPY cart-service/src ./cart-service/src

RUN cd cart-service && mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /build/cart-service/target/*.jar app.jar

EXPOSE 8086

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

