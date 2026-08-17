FROM maven:3.9.5-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ../pom.xml
COPY payment-service/pom.xml ./pom.xml
COPY payment-service/src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8085

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

