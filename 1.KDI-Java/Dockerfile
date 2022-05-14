# First stage to build the application
FROM maven:3.6.3-openjdk-11-slim AS build-env
ADD ./pom.xml pom.xml
ADD ./src src/
RUN mvn clean package

# build runtime image
FROM openjdk:11-jre-slim

EXPOSE 8080

# docker build argument
#    This can be specified during the docker build step by adding " --build-arg build_version=<value>"
#    App version can be accessed via the uri path /api/version/user-java
#    https://vsupalov.com/docker-build-pass-environment-variables/
ARG build_version="user-java default"

ENV ADLS_STORAGE_ACCOUNT_NAME="myadls" \
ADLS_STORAGE_CDC_CONTAINER_NAME="changeme" \
ADLS_CLIENT_ID="changeme" \
ADLS_CLIENT_SECRET="changeme" \
ADLS_CLIENT_TENANT="changeme" \
KAFKA_BROKER_ADDRESS="changeme:9092" \
KAFKA_TOPIC="serverchangeme.dbo.changeme" \
KAFKA_CONSUMER_NAME_SELF="whoami" \
KAFKA_BUFFER=1000 \
DELTA_WRITE_LOCAL=false

# Add the application's jar to the container
COPY --from=build-env target/kdi-java-1.0-SNAPSHOT.jar kdi-java.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/kdi-java.jar"]