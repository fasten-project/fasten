FROM openjdk:11-jre-slim

WORKDIR /app
COPY ./target/*.jar ./rest-api.jar

ENTRYPOINT java $JAVA_OPTS -jar /app/rest-api.jar $0 $@

EXPOSE 8080
