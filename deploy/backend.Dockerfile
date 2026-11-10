# 构建镜像：在仓库根目录执行（context = 仓库根）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
# 本地密钥文件不打进镜像
RUN rm -f src/main/resources/application-local*.yml
RUN mvn -q -B -DskipTests package

# 官方 Playwright 镜像自带 Chromium，岗位爬虫（Playwright）在容器内可直接运行。
# 若服务器拉取 mcr.microsoft.com 困难，可换成本地重新打 tag 的等价镜像。
FROM mcr.microsoft.com/playwright/java:v1.49.0-noble
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /data/resumes && chown -R pwuser:pwuser /app /data/resumes
USER pwuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]