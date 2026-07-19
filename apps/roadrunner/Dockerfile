FROM eclipse-temurin:21-jre
RUN useradd -m roadrunner
COPY ./target/*.jar roadrunner.jar
USER roadrunner
ENTRYPOINT ["java","-jar","/roadrunner.jar"]
# Web port
EXPOSE 8080
