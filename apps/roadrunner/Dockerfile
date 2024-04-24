FROM openjdk:17
COPY ./target/*.jar roadrunner.jar
ENTRYPOINT ["java","-jar","/roadrunner.jar"]
# Web port
EXPOSE 8080
