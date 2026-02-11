FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV TZ=Asia/Seoul
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
