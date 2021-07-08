#############################################
# Stage 1a: building all the necessary JARs #
#############################################

FROM maven:3.8.1-jdk-11-slim AS build-jars
COPY . /home/app
# Building the server JAR
RUN mvn --file /home/app/pom.xml install --projects server --also-make
# Building the license plugin JAR
RUN mvn --file /home/app/pom.xml install --projects analyzer,:license-feeder

#######################
# Stage 2a: packaging #
#######################

FROM maven:3.8.1-openjdk-11-slim AS package

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Copying all the needed JARs
COPY --from=build-jars /home/app/docker/server/server-* server.jar
COPY --from=build-jars /home/app/docker/plugins/license-feeder-* plugins/license-feeder.jar

ENTRYPOINT java $JVM_MEM_MAX --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@

###############################################
# Stage 2b: packaging (skips JARs generation) #
###############################################

FROM maven:3.8.1-openjdk-11-slim AS package-ci

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Copying all the needed (and already available) JARs
COPY docker/server/server-* server.jar
COPY docker/plugins/license-feeder-* plugins/license-feeder.jar

ENTRYPOINT java $JVM_MEM_MAX --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@

