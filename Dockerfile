# Giai đoạn build ứng dụng
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -DskipTests

# Giai đoạn chạy ứng dụng
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Tạo các thư mục lưu ảnh
RUN mkdir -p uploads/foods \
    uploads/avatars \
    uploads/qrcodes

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]