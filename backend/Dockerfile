# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 argus
WORKDIR /app
COPY --from=build /build/target/argus-backend-*.jar app.jar
RUN mkdir -p /app/data/uploads && chown -R argus:argus /app
USER argus
EXPOSE 8080
ENV ARGUS_UPLOAD_DIR=/app/data/uploads
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
