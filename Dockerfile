# syntax=docker/dockerfile:1

# --- Build stage ---------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Copy pom.xml first so the dependency layer is cached independently of
# source changes.
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline -DskipTests

COPY src/ src/
RUN ./mvnw -B package -DskipTests

# --- Runtime stage --------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080 8081

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
