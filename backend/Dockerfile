# 放在 backend/Dockerfile。Render 偵測到後會在雲端 build，你本機不需安裝 Docker。
# 多階段：第一階段用 JDK + Maven wrapper 打包，第二階段只留 JRE + jar，image 較小。

# ---- build stage ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -B clean package -DskipTests
# 測試交給 GitHub Actions CI 把關，image build 不重跑，加快部署

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/meeting-room-0.0.1-SNAPSHOT.jar app.jar
# 不寫死 port：app 讀 server.port=${PORT:8080}，Render 會用 PORT 環境變數指定埠
ENTRYPOINT ["java", "-jar", "app.jar"]
