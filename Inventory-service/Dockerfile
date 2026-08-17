# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Copy parent pom first for dependency resolution
COPY pom.xml ./pom.xml

# Copy inventory-service pom
COPY Inventory-service/pom.xml ./Inventory-service/pom.xml

# Download dependencies (cached layer)
RUN cd Inventory-service && mvn dependency:go-offline -B

# Copy source code
COPY Inventory-service/src ./Inventory-service/src

# Build the application
RUN cd Inventory-service && mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Copy jar from build stage
COPY --from=build /build/Inventory-service/target/*.jar app.jar

# Change ownership
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose port
EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

# Environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
