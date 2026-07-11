FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/travel-chart-plan-1.0.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
