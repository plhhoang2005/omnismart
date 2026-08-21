FROM eclipse-temurin:22-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode --no-transfer-progress dependency:go-offline

COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress package

FROM eclipse-temurin:22-jre-jammy

RUN groupadd --system omnismart && useradd --system --gid omnismart --home-dir /app omnismart
WORKDIR /app
COPY --from=build --chown=omnismart:omnismart /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar

USER omnismart
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
