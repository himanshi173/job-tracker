FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

# Copy Maven wrapper and pom.xml first to download dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw
# Pre-download all Maven dependencies for caching
RUN ./mvnw dependency:go-offline -B

# Copy source code and build package
COPY src src
RUN ./mvnw clean package -DskipTests

# Run stage - use slim JRE image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /app/target/job-tracker-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

CMD ["java", "-jar", "app.jar"]