# Build context must contain vex-core; see README ("Docker"). Simplest: build the jar locally first.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/domain-forge-0.1.0.jar app.jar
EXPOSE 8081
ENV DOMAINFORGE_AVAILABILITY=rdap
ENTRYPOINT ["java","-jar","/app/app.jar"]
