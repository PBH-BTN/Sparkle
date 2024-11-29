FROM docker.io/eclipse-temurin:23-noble
LABEL maintainer="https://github.com/PBH-BTN/Sparkle"
COPY target/sparkle-0.0.1-SNAPSHOT.jar /app/sparkle.jar
USER 0
ENV TZ=UTC
WORKDIR /app
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java","-XX:+UseG1GC","-jar","sparkle.jar"]
