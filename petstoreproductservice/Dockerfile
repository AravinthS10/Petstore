FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY applicationinsights-agent-3.7.7.jar applicationinsights-agent.jar
EXPOSE 8080
ENV APPLICATIONINSIGHTS_CONNECTION_STRING=InstrumentationKey=YOUR_INSTRUMENTATION_KEY
ENTRYPOINT ["java", "-javaagent:/app/applicationinsights-agent.jar", "-jar", "app.jar"]