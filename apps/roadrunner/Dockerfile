FROM openjdk:21
COPY ./target/*.jar roadrunner.jar
ENTRYPOINT ["java","-jar","/roadrunner.jar"]
# Web port
EXPOSE 8080
