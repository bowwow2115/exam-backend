# syntax=docker/dockerfile:1

FROM node:22-alpine AS frontend-build
WORKDIR /workspace/src/main/resources/frontend

COPY src/main/resources/frontend/package*.json ./
RUN npm ci

COPY src/main/resources/frontend/ ./
RUN npm run build

FROM eclipse-temurin:17-jdk AS backend-build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
COPY --from=frontend-build /workspace/src/main/resources/frontend/dist ./src/main/resources/frontend/dist
RUN ./gradlew bootJar -PskipFrontendBuild --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=backend-build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 80
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
