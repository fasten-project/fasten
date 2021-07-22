#################################################################################
# Stage 0: layer with analyzers' pom.xml files only. Used for caching purposes. #
#################################################################################

FROM alpine:3.14.0 AS list-analyzers-pom-files

# Copying the entirety of all analyzers
COPY analyzer /home

# Finding and removing non-pom.xml files
RUN find /home \! -name "pom.xml" -mindepth 2 -maxdepth 2 -print | xargs rm -rf

#############################################
# Stage 1a: building all the necessary JARs #
#############################################

FROM maven:3.8.1-jdk-11-slim AS build-jars

# Building parent
COPY pom.xml /home/app/
COPY core/pom.xml /home/app/core/
COPY server/pom.xml /home/app/server/
COPY --from=list-analyzers-pom-files /home/ /home/app/analyzer/
RUN mvn --file /home/app/pom.xml install --projects :parent

# Building core
# - Skips integration tests requiring Testcontainers (hence, Docker).
#   Integration tests are run by the CI anyways.
COPY core/ /home/app/core
RUN mvn --file /home/app/pom.xml install --projects core -Dtest=\!MetadataDaoTest,\!PostgresConnectorTest

# Building server
COPY server/ /home/app/server
RUN mvn --file /home/app/pom.xml install --projects :server

# Building the generic analyzer plugin JAR
RUN mvn --file /home/app/pom.xml install --projects analyzer

# Building the license plugin JAR
# - Skips integration tests requiring Testcontainers (hence, Docker)
#   Integration tests are run by the CI anyways.
# - Doesn't fail in absence of tests, since the license feeder doesn't have unit tests yet
#   (the functions used in the MetadataDao do have unit tests)
#   TODO Delete `-DfailIfNoTests=false` upon creating unit tests for the license feeder class
COPY analyzer/license-feeder/ /home/app/analyzer/license-feeder
RUN mvn --file /home/app/pom.xml install --projects :license-feeder -Dtest=\!*IntegrationTest -DfailIfNoTests=false

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

