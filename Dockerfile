FROM gradle:jdk22 as gradle

COPY ./ ./

RUN gradle installDist

FROM openjdk:22-slim

WORKDIR /app

COPY --from=gradle /home/gradle/build/install/CurrentCountryUpdater ./

RUN apt-get update && apt-get install -y curl && apt-get clean && rm -rf /var/lib/apt/lists/*

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

CMD ["./bin/CurrentCountryUpdater"]
