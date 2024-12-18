FROM bellsoft/liberica-openjdk-alpine:23
LABEL maintainer="https://github.com/PBH-BTN/Sparkle"
USER 0
ENV TZ=UTC
WORKDIR /app
COPY tools/arthas-boot.jar /app/arthas-boot.jar
COPY target/sparkle-0.0.1-SNAPSHOT.jar /app/sparkle.jar

ENTRYPOINT ["java","-XX:+UseG1GC", "-XX:MaxRAMPercentage=86.0", "-XX:+UseContainerSupport", "-jar","sparkle.jar"]
