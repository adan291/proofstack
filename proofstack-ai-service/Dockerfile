# syntax=docker/dockerfile:1
# Build the jar first:  mvn -pl proofstack-ai-service -am -DskipTests package
# Then, from proofstack-ai-service/:  docker build -t proofstack-ai-service .

# ---------- stage 1: split the Spring Boot jar into cacheable layers + compile the health probe ----------
FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build
WORKDIR /build
COPY target/proofstack-ai-service-*-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted
COPY docker/HealthProbe.java .
RUN javac --release 25 -d probe HealthProbe.java

# ---------- stage 2: minimal runtime ----------
FROM eclipse-temurin:25.0.4.1_1-jre-noble
LABEL org.opencontainers.image.title="proofstack-ai-service" \
      org.opencontainers.image.description="Ask-my-portfolio RAG service (Spring Boot + Spring AI)" \
      org.opencontainers.image.licenses="Apache-2.0"

# Unprivileged, fixed UID so volume permissions are predictable.
RUN groupadd --system --gid 10001 proofstack \
 && useradd --system --uid 10001 --gid proofstack --no-create-home --shell /usr/sbin/nologin proofstack
WORKDIR /app

# Least-changing layers first: dependencies rarely change, application code changes on every release.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
COPY --from=build /build/probe/ /opt/probe/

USER 10001:10001
ENV PROOFSTACK_PROFILE=/profile/profile.yaml \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# 8081 (actuator) is intentionally not exposed: reach it only from the internal network.

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["java", "-cp", "/opt/probe", "HealthProbe", "http://127.0.0.1:8081/actuator/health/readiness"]

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
