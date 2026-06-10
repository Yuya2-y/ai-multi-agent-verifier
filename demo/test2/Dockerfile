# 1段階目: アプリケーションのビルド
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# 2段階目: アプリケーションの実行（エラーの原因となっていた箇所を修正）
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Renderのポート10000番にEXPOSE設定
EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
