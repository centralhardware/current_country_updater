FROM gradle:jdk22 as gradle

COPY ./ ./

RUN gradle fatJar

FROM openjdk:22-slim

WORKDIR /znatokiBot

COPY --from=gradle /home/gradle/build/libs/CurrentCountryUpdater-1.0-SNAPSHOT-standalone.jar .

RUN apt-get update
RUN apt-get update && apt-get install -y curl && apt-get clean && rm -rf /var/lib/apt/lists/*

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl --fail http://localhost:81/health || exit 1

CMD ["java", "-jar", "CurrentCountryUpdater-1.0-SNAPSHOT-standalone.jar" ]