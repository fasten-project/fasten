#############################################
# Stage 1a: building all the necessary JARs #
#############################################

FROM maven:3.8.1-jdk-11-slim AS build
COPY . /home/app
RUN mvn --file /home/app/pom.xml clean install --projects server,:license-detector --also-make

############################################
# Stage 1b: the Quartermaster Maven plugin #
############################################

FROM openjdk:11-slim-buster AS qmstr-maven-plugin

# Installing Maven & git
RUN mkdir -p /usr/share/man/man1 && \
    apt update && \
    apt install -y --no-install-recommends maven git

# Cloning Quartermaster
WORKDIR /home
RUN git clone --verbose --progress --depth 1 --single-branch https://github.com/QMSTR/qmstr.git qmstr && \
    cd qmstr && \
    git reset --hard 77f29045f45c7109e34130e6f468f4b7b287f604

# Installing the Quartermaster Maven plugin
WORKDIR /home/qmstr/lib/java-qmstr
RUN ./gradlew install && \
    cd ../../modules/builders/qmstr-maven-plugin && \
    mvn install

#######################
# Stage 2a: packaging #
#######################

FROM maven:3.8.1-openjdk-11-slim AS package

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Copying the Quartermaster Maven plugin
COPY --from=qmstr-maven-plugin /home/qmstr/docker/qmstr-client/settings.xml /usr/share/maven/conf/settings.xml
COPY --from=qmstr-maven-plugin /root/.m2/repository /maven/repo

# Copying all the needed JARs
COPY --from=build /home/app/docker/server/server-* server.jar
COPY --from=build /home/app/docker/plugins/license-detector-* plugins/license-detector.jar

ENTRYPOINT java $JVM_MEM_MAX --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@

###############################################
# Stage 2b: packaging (skips JARs generation) #
###############################################

FROM openjdk:11-jre-slim AS package-ci

WORKDIR /

ENV JVM_MEM_MAX="-Xmx16g"

# Copying the Quartermaster Maven plugin
COPY --from=qmstr-maven-plugin /home/qmstr/docker/qmstr-client/settings.xml /usr/share/maven/conf/settings.xml
COPY --from=qmstr-maven-plugin /root/.m2/repository /maven/repo

# Copying all the needed (and already available) JARs
COPY docker/server/server-* server.jar
COPY docker/plugins/license-detector-* plugins/license-detector.jar

ENTRYPOINT java $JVM_MEM_MAX --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -XX:+ExitOnOutOfMemoryError -cp server.jar eu.fasten.server.FastenServer -p ./plugins $0 $@
