FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -pl workspace-service -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/workspace-service/target/workspace-service-0.1.0.jar app.jar
EXPOSE 8082 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
