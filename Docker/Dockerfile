# syntax=docker/dockerfile:1.7
# Bazy: Alpine 3.24 (zawiera poprawkę CVE-2026-123158 – OpenSSH/musl, hasła >256 B;
# obraz nie zawiera openssh). Tagi i digesty przypięte (bez "latest"), stan z 2026-11-21.

# ---- build ----
FROM eclipse-temurin:25.0.5.1_2-jdk-alpine-3.24@sha256:22edbb1021420ae6e248519d1af07577742314d67d4c60fcbb2e285b7e47fa78 AS build
WORKDIR /workspace
RUN apk upgrade --no-cache
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/*-SNAPSHOT.jar extract --layers --launcher --destination /workspace/layers

# ---- runtime ----
FROM eclipse-temurin:25.0.5.1_2-jre-alpine-3.24@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89
RUN apk upgrade --no-cache \
 && apk add --no-cache tesseract-ocr tesseract-ocr-data-pol tesseract-ocr-data-eng \
 && addgroup -g 10001 -S app \
 && adduser -u 10001 -S -G app -H -h /app -s /sbin/nologin app
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/layers/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.io.tmpdir=/tmp"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD nc -z 127.0.0.1 8080 || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
