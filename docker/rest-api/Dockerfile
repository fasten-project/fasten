FROM openjdk:11-jre-slim

WORKDIR /app
COPY ./*.jar ./rest-api.jar

ENTRYPOINT ["java", "-jar", "/app/rest-api.jar"]

EXPOSE 8080
