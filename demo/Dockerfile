# 1. Java11とMavenが入った環境を使ってプログラムを組み立てる
FROM maven:3.8.6-openjdk-11 AS build
COPY . .
RUN mvn clean package -DskipTests

# 2. 組み立てたプログラム（JARファイル）を取り出して、軽量なJava11環境で実行する
FROM eclipse-temurin:11-jre
COPY --from=build /target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "app.jar"]