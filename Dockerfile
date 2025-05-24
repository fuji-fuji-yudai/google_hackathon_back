# === ビルドステージ ===
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# 依存関係のキャッシュを活かすため、先にpom.xmlだけコピー
COPY pom.xml .
RUN mvn dependency:go-offline

# ソースコードをコピーしてビルド
COPY src ./src
RUN mvn clean package -DskipTests

# === 実行ステージ ===
FROM openjdk:17-slim

WORKDIR /app

# Cloud SQL Socket Factory に必要なライブラリを含める
RUN apt-get update && apt-get install -y libnss3 && apt-get clean

# ビルド成果物をコピー
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
