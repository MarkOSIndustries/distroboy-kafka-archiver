# Building
FROM openjdk:15 as build_env

ARG VERSION

WORKDIR /home/distroboy-kafka-archiver

COPY ./gradle ./gradle
ADD ./gradlew ./gradlew
RUN chmod +x ./gradlew
RUN ./gradlew --version

COPY . .
RUN ./gradlew -Pversion_string=$VERSION clean installDist

# Runtime
FROM openjdk:15 as runtime
COPY --from=build_env /home/distroboy-kafka-archiver/build/install/distroboy-kafka-archiver/dependencies dependencies
COPY --from=build_env /home/distroboy-kafka-archiver/build/install/distroboy-kafka-archiver/distroboy-kafka-archiver*.jar distroboy-kafka-archiver.jar
EXPOSE 7071
CMD exec java -jar distroboy-kafka-archiver.jar
