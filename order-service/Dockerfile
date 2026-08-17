# Stage 1: Build the application
FROM maven:3.9.5-eclipse-temurin-17 AS build

# Set the working directory
WORKDIR /app

# Copy the parent pom.xml
COPY pom.xml ./pom.xml

# Copy the order-service project files
COPY order-service/pom.xml ./order-service/pom.xml
COPY order-service/src ./order-service/src

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests -f order-service/pom.xml

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine

# Set the working directory
WORKDIR /app

# Create a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the JAR file from the build stage
COPY --from=build /app/order-service/target/*.jar app.jar

# Expose the application port
EXPOSE 8082

# Set the active profile (can be overridden at runtime)
ENV SPRING_PROFILES_ACTIVE=docker

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]


