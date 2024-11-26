FROM docker.io/eclipse-temurin:23-jre-noble
LABEL maintainer="https://github.com/PBH-BTN/Sparkle"
COPY target/sparkle-0.0.1-SNAPSHOT.jar /app/sparkle.jar
USER 0
ENV TZ=UTC
WORKDIR /app
VOLUME /tmp
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java","-XX:+UseG1GC", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=85.0", "-XX:MaxRAMPercentage=85.0", "-XX:MinRAMPercentage=85.0","-jar","sparkle.jar"]
