FROM eclipse-temurin:21-jre
COPY ./target/*.jar roadrunner.jar
ENTRYPOINT ["java","-jar","/roadrunner.jar"]
# Web port
EXPOSE 8080
