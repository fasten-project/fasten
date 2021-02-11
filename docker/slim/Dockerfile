FROM openjdk:11-jre-slim-buster

WORKDIR /

ADD server/server-0.0.1-SNAPSHOT-with-dependencies.jar server-0.0.1-SNAPSHOT-with-dependencies.jar
COPY /plugins/. /plugins

ENTRYPOINT ["java", "-Xmx16g", "-XX:+ExitOnOutOfMemoryError", "-cp", "server-0.0.1-SNAPSHOT-with-dependencies.jar", "eu.fasten.server.FastenServer", "-p", "./plugins"]
