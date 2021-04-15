############################################
# Stage 1: building all the necessary JARs #
############################################

FROM maven:3.8.1-jdk-11-slim AS build
COPY . /home/app
RUN mvn --file /home/app/pom.xml clean install --projects server,:license-detector --also-make

#######################
# Stage 2a: packaging #
#######################

FROM openjdk:11-jre-slim AS package

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Copying all the needed JARs
COPY --from=build /home/app/docker/server/server-* server.jar
COPY --from=build /home/app/docker/plugins/license-detector-* plugins/license-detector.jar

ENTRYPOINT java $JVM_MEM_MAX -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@

###############################################
# Stage 2b: packaging (skips JARs generation) #
###############################################

FROM openjdk:11-jre-slim AS package-ci

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Copying all the needed (and already available) JARs
COPY docker/server/server-* server.jar
COPY docker/plugins/license-detector-* plugins/license-detector.jar

ENTRYPOINT java $JVM_MEM_MAX -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@
