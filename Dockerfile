FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY . .
RUN ./gradlew :service-clan:bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/service-clan/build/libs/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
