FROM azul/zulu-openjdk-alpine:18-jre

# Add the service itself
COPY ./target/klass-subsets-api.jar /usr/share/klass-subsets-api/
COPY ./src/main/resources/definitions/series.json /usr/share/klass-subsets-api/
COPY ./src/main/resources/definitions/version.json /usr/share/klass-subsets-api/

ENTRYPOINT ["java", "-jar", "/usr/share/klass-subsets-api/klass-subsets-api.jar"]

EXPOSE 8080
