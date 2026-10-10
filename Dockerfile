# fdiet backend: Spring Boot 4.1 on Java 21.
# Build:  docker build -t fdiet-backend .
# Every database setting comes from the environment (MYSQL_HOST, ...), as with bootRun.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# The wrapper and the build script first, so the dependency download stays cached until they change.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/fdiet-*-SNAPSHOT.jar /src/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system fdiet && useradd --system --gid fdiet --home /app fdiet
COPY --from=build /src/app.jar app.jar
# Read at runtime by relative path: the composition snapshots (Git LFS - run `git lfs pull`
# before building), the reference CSVs and the branded catalogue.
COPY reference-data reference-data
COPY fooddata.csv fooddata.csv
USER fdiet
EXPOSE 5000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
