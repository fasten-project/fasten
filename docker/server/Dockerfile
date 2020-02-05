FROM openjdk:11

WORKDIR /

ADD server/server-0.0.1-SNAPSHOT-with-dependencies.jar server-0.0.1-SNAPSHOT-with-dependencies.jar
COPY /plugins/. /plugins

ENTRYPOINT ["java", "-XX:+HeapDumpOnOutOfMemoryError", "-Xmx64g", "-Dcom.sun.management.jmxremote", "-Dcom.sun.management.jmxremote.port=1232", "-Dcom.sun.management.jmxremote.ssl=false", "-Dcom.sun.management.jmxremote.authenticate=false", "-cp", "server-0.0.1-SNAPSHOT-with-dependencies.jar", "eu.fasten.server.FastenServer", "-p", "./plugins"]
