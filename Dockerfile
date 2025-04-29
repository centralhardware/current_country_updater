FROM gradle:jdk23 as gradle

COPY ./ ./

RUN gradle installDist

FROM openjdk:23-slim

WORKDIR /app

COPY --from=gradle /home/gradle/build/install/CurrentCountryUpdater ./

RUN apt-get update && apt-get install -y curl tzdata && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && ln -fs /usr/share/zoneinfo/Asia/Kuala_Lumpur /etc/localtime \
    && echo "Asia/Kuala_Lumpur" > /etc/timezone \
    && dpkg-reconfigure -f noninteractive tzdata

ENV TZ=Asia/Kuala_Lumpur

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

CMD ["./bin/CurrentCountryUpdater"]
