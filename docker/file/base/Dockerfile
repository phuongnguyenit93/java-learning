# root/spring/integration/broker/kafka/producer/bank/Dockerfile

# --- Giai đoạn Build ---
FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /app

ARG MODULE_PATH
ARG JAR_PATH

# 1. Bắt buộc phải có các file quản lý ở root
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 2. CHỈ copy các module liên quan (ví dụ bank)
COPY ${JAR_PATH} ${JAR_PATH}

# Xử lý lỗi Exit Code 127 (Không tìm thấy lệnh hoặc sai định dạng file)
# 1. Chuyển đổi định dạng xuống dòng từ Windows (CRLF) sang Linux (LF)
# 2. Cấp quyền thực thi cho file gradlew
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Build module cụ thể
RUN ./gradlew :${MODULE_PATH}:bootJar -x test

# --- Giai đoạn Run ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG JAR_PATH

# Copy file jar từ kết quả của giai đoạn build
COPY --from=build /app/build/outputs/${JAR_PATH}/libs/*.jar app.jar

# Lưu ý: file default.properties phải nằm trong context (root) hoặc được mount qua volume
COPY default.properties /app/default.properties

ENTRYPOINT ["java", "-jar", "app.jar"]