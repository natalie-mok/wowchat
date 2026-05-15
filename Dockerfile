# WoWChat targets Java 8 bytecode; Temurin 8 matches pom.xml and avoids JDK mismatch issues on hosts.
FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -B -DskipTests

FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build /app/target/wowchat.jar /app/wowchat.jar
COPY --from=build /app/src/main/resources/wowchat.conf /app/wowchat.conf
# Secrets: DISCORD_TOKEN, WOW_ACCOUNT, WOW_PASSWORD, WOW_CHARACTER (see wowchat.conf)
CMD ["java", "-jar", "/app/wowchat.jar", "/app/wowchat.conf"]
