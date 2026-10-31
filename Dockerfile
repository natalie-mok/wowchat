FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /src/target/wowchat.jar /app/wowchat.jar
COPY --from=build /src/src/main/resources/wowchat.conf /app/wowchat.conf
COPY --from=build /src/src/main/resources/logback.xml /app/logback.xml

# Railway bills by actual RSS. Uncapped JVMs often grow to multi-GB heaps.
ENV JAVA_OPTS="-Xms32m -Xmx192m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -XX:+ExitOnOutOfMemoryError" \
    MALLOC_ARENA_MAX=2

CMD ["sh", "-c", "exec java $JAVA_TOOL_OPTIONS $JAVA_OPTS -Dfile.encoding=UTF-8 -Dio.netty.allocator.numHeapArenas=1 -Dio.netty.allocator.numDirectArenas=1 -Dlogback.configurationFile=logback.xml -jar wowchat.jar wowchat.conf"]
