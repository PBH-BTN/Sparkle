FROM docker.io/eclipse-temurin:21-jre-alpine
LABEL maintainer="https://github.com/PBH-BTN/Sparkle"
COPY target/sparkle-0.0.1-SNAPSHOT.jar /app/sparkle.jar
USER 0
ENV TZ=UTC
WORKDIR /app
VOLUME /tmp
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java","-Xmx2G","-XX:+UseG1GC", "-XX:+UseStringDeduplication","-XX:+ShrinkHeapInSteps","-jar","PeerBanHelper.jar"]
