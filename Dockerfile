FROM gradle:jdk24 as gradle

COPY ./ ./

RUN gradle installDist

FROM openjdk:24-slim

WORKDIR /app

COPY --from=gradle /home/gradle/build/install/CurrentCountryUpdater ./

RUN apt-get update && apt-get install -y tzdata && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && ln -fs /usr/share/zoneinfo/Asia/Kuala_Lumpur /etc/localtime \
    && echo "Asia/Kuala_Lumpur" > /etc/timezone \
    && dpkg-reconfigure -f noninteractive tzdata

ENV TZ=Asia/Kuala_Lumpur

CMD ["./bin/CurrentCountryUpdater"]
