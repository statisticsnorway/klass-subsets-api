FROM azul/zulu-openjdk:11

# Add the service itself
COPY ./target/klass-subsets-api.jar /usr/share/klass-subsets-api/

ENTRYPOINT ["java", "-jar", "/usr/share/klass-subsets-api/klass-subsets-api.jar"]

EXPOSE 8080