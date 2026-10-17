FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN --mount=type=cache,id=ticketdesk-maven,target=/root/.m2 ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system ticketdesk && useradd --system --gid ticketdesk ticketdesk
COPY --from=build /app/target/ticketdesk-api-0.1.0-SNAPSHOT.jar app.jar
USER ticketdesk
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
