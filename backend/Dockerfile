FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir /app/keystore && chown 10001:10001 /app/keystore
COPY --from=build /build/target/traceguard-backend-*.jar /app/traceguard.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/traceguard.jar"]
