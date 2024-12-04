FROM docker.io/eclipse-temurin:23-noble
LABEL maintainer="https://github.com/PBH-BTN/Sparkle"
USER 0
ENV TZ=UTC
WORKDIR /app
ENV PATH="${JAVA_HOME}/bin:${PATH}"
RUN apt-get update && apt-get install curl vim -y && apt-get clean && \
    curl -o /app/arthas-boot.jar https://arthas.aliyun.com/arthas-boot.jar
COPY target/sparkle-0.0.1-SNAPSHOT.jar /app/sparkle.jar

ENTRYPOINT ["java","-XX:+UseG1GC", "-XX:MaxRAMPercentage=90.0", "-XX:+UseContainerSupport", "-jar","sparkle.jar"]
