FROM openjdk:11-jre-slim-buster

WORKDIR /

ADD server/server-0.0.1-SNAPSHOT-with-dependencies.jar server-0.0.1-SNAPSHOT-with-dependencies.jar
COPY /plugins/vulnerability-statements-processor-*.jar /plugins/
COPY /plugins/vulnerability-packages-listener-*.jar /plugins/

ENV JVM_MEM_MAX=-Xmx16g

ENTRYPOINT java $JVM_MEM_MAX -XX:+ExitOnOutOfMemoryError -cp server-0.0.1-SNAPSHOT-with-dependencies.jar eu.fasten.server.FastenServer -p ./plugins $0 $@
