# ---- build stage: builds Vex from source, then the app, and bakes the embedding model in
FROM maven:3.9-eclipse-temurin-17 AS build
ARG VEX_REPO=https://github.com/AshuGuptaz/Vex.git
WORKDIR /src
RUN git clone --depth 1 "$VEX_REPO" vex && mvn -B -q -f vex/pom.xml -pl core -am install -DskipTests
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package
RUN mkdir -p /models && \
    curl -fsSL -o /models/tokenizer.json https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json && \
    curl -fsSL -o /models/model.onnx https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx

# ---- runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/domain-forge-0.1.0.jar app.jar
COPY --from=build /models /models
ENV DOMAINFORGE_MODEL_DIR=/models \
    DOMAINFORGE_AVAILABILITY=rdap \
    PORT=8081
EXPOSE 8081
ENTRYPOINT ["sh","-c","java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT} -jar /app/app.jar"]
