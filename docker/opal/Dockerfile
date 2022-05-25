FROM openjdk:11

WORKDIR /

ADD plugins/javacg-opal-* opal-plugin.jar

ENTRYPOINT ["java", "-Xmx16g", "-XX:+ExitOnOutOfMemoryError", "-cp", "opal-plugin.jar", "eu.fasten.analyzer.javacgopal.Main"]