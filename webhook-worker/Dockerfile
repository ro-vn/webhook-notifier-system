FROM eclipse-temurin:21-jre-alpine
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]



#FROM gradle:8.12.1-jdk21 AS build
#WORKDIR /home/gradle/project
#
#COPY --chown=gradle:gradle gradlew .
#COPY --chown=gradle:gradle gradle gradle
#COPY --chown=gradle:gradle settings.gradle* build.gradle* ./
#COPY --chown=gradle:gradle buildSrc buildSrc
#COPY --chown=gradle:gradle webhook-worker/build.gradle* app/
#
#RUN ./gradlew classes --no-daemon || return 0
#
#COPY --chown=gradle:gradle . .
#
#RUN ./gradlew :webhook-worker:build --no-daemon
#
#FROM eclipse-temurin:21-jre-alpine AS runtime
#WORKDIR /app
#COPY --from=build /home/gradle/project/webhook-worker/build/libs/*.jar app.jar
#
#EXPOSE 8080
#
#ENTRYPOINT ["java", "-jar", "/app/app.jar"]
