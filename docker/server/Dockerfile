FROM openjdk:11-jre-slim-buster

WORKDIR /

ADD server/server-*-with-dependencies.jar server-with-dependencies.jar
COPY /plugins/. /plugins

ENV JVM_MEM_MAX=-Xmx16g

ENTRYPOINT java $JVM_MEM_MAX -XX:+ExitOnOutOfMemoryError -cp server-with-dependencies.jar eu.fasten.server.FastenServer -p ./plugins $0 $@
