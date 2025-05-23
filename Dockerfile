# ベースイメージの指定
FROM openjdk:17-slim

# ワーキングディレクトリの設定
WORKDIR /app

# アプリケーションのJARファイルをコンテナにコピー
COPY target/*.jar app.jar


# Cloud SQL Socket Factory に必要なライブラリを含める
RUN apt-get update && apt-get install -y libnss3


# ポートの公開
EXPOSE 8080

# アプリケーションの実行コマンド
ENTRYPOINT ["java", "-jar", "app.jar"]