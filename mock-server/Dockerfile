FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/api-simulator-mock-server-1.0.0.jar app.jar
EXPOSE 8090
EXPOSE 9999
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
