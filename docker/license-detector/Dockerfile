# Scancode version and info
ARG SCANCODE_VERSION="21.3.31"
ARG SCANCODE_RELEASE_CANDIDATE=""
ARG SCANCODE_INSTALLATION_FOLDER="/home"

#############################################
# Stage 1a: building all the necessary JARs #
#############################################

FROM maven:3.8.1-jdk-11-slim AS build-jars
COPY . /home/app
RUN mvn --file /home/app/pom.xml clean install --projects server,:license-detector --also-make

################################################
# STAGE 1b: Downloading and unzipping scancode #
################################################

FROM python:3.9.5-alpine3.13 AS scancode
ENV SCANCODE_LOCAL_ARCHIVE_NAME="scancode.tar.xz"
ARG SCANCODE_VERSION
ARG SCANCODE_RELEASE_CANDIDATE
ARG SCANCODE_INSTALLATION_FOLDER
WORKDIR ${SCANCODE_INSTALLATION_FOLDER}
ADD "https://github.com/nexB/scancode-toolkit/releases/download/v${SCANCODE_VERSION}${SCANCODE_RELEASE_CANDIDATE}/scancode-toolkit-${SCANCODE_VERSION}${SCANCODE_RELEASE_CANDIDATE}_sources.tar.xz" ${SCANCODE_LOCAL_ARCHIVE_NAME}
RUN tar xJf ${SCANCODE_LOCAL_ARCHIVE_NAME} --strip=1

#######################
# Stage 2a: packaging #
#######################

FROM maven:3.8.1-openjdk-11-slim AS package

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Installing scancode dependencies
RUN apt update && \
    apt install -y libgomp1 libbz2-1.0 xz-utils zlib1g libxml2-dev libxslt1-dev python3-pip=18.1-5

# Copying the scancode source code
ARG SCANCODE_INSTALLATION_FOLDER
ENV SCANCODE_LOCAL_DIRECTORY="/usr/local/bin/scancode/"
RUN mkdir -p ${SCANCODE_LOCAL_DIRECTORY}
COPY --from=scancode --chown=root ${SCANCODE_INSTALLATION_FOLDER}/ ${SCANCODE_LOCAL_DIRECTORY}

# Installing scancode
RUN ${SCANCODE_LOCAL_DIRECTORY}/configure --dev
ENV PATH="${SCANCODE_LOCAL_DIRECTORY}:${PATH}"

# Copying all the needed JARs
COPY --from=build-jars /home/app/docker/server/server-* server.jar
COPY --from=build-jars /home/app/docker/plugins/license-detector-* plugins/license-detector.jar

ENTRYPOINT java $JVM_MEM_MAX --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@

###############################################
# Stage 2b: packaging (skips JARs generation) #
###############################################

FROM maven:3.8.1-openjdk-11-slim AS package-ci

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Installing scancode dependencies
RUN apt update && \
    apt install -y libgomp1 libbz2-1.0 xz-utils zlib1g libxml2-dev libxslt1-dev python3-pip=18.1-5

# Copying the scancode source code
ARG SCANCODE_INSTALLATION_FOLDER
ENV SCANCODE_LOCAL_DIRECTORY="/usr/local/bin/scancode/"
RUN mkdir -p ${SCANCODE_LOCAL_DIRECTORY}
COPY --from=scancode --chown=root ${SCANCODE_INSTALLATION_FOLDER}/ ${SCANCODE_LOCAL_DIRECTORY}

# Installing scancode
RUN ${SCANCODE_LOCAL_DIRECTORY}/configure --dev
ENV PATH="${SCANCODE_LOCAL_DIRECTORY}:${PATH}"

# Copying all the needed (and already available) JARs
COPY docker/server/server-* server.jar
COPY docker/plugins/license-detector-* plugins/license-detector.jar

ENTRYPOINT java $JVM_MEM_MAX --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@
