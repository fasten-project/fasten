FROM openjdk:11

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Copying the Fasten server JAR
COPY server/server-* server.jar

# Copying the license detector plugin JAR
COPY plugins/license-detector-* plugins/license-detector.jar

ENTRYPOINT java $JVM_MEM_MAX -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@
