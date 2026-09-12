# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Cache dependencies
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Build application
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:25-jre AS runner
WORKDIR /app

# Security: non-root user
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=builder --chown=spring:spring /app/target/TicketResale-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
