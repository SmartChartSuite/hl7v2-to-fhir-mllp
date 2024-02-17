#Build the Maven project
#FROM maven:3.8.5-jdk-11 as builder
FROM maven:3.6.3-openjdk-17 as builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN mvn clean install

#Build the Java container
#FROM openjdk:8-alpine
FROM openjdk:17-jdk

# Copy elr_receiver jar file to webapps.
COPY --from=builder /usr/src/app/config.properties /usr/src/myapp/config.properties
COPY --from=builder /usr/src/app/target/elr_receiver-0.0.6-jar-with-dependencies.jar /usr/src/myapp/elr_receiver.jar
WORKDIR /usr/src/myapp
CMD ["java", "-jar", "elr_receiver.jar"]

EXPOSE 8888
