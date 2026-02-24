# --- Giai đoạn Build ---
FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /app

# Copy Gradlew để có biết cần tải gradle nào
COPY gradlew .
COPY gradle/ gradle/

# Xử lý lỗi Exit Code 127 (Không tìm thấy lệnh hoặc sai định dạng file)
# 1. Chuyển đổi định dạng xuống dòng từ Windows (CRLF) sang Linux (LF)
# 2. Cấp quyền thực thi cho file gradlew
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Mục đích để tải gradle để lưu cache cho các lần compose sau (Nếu project có thay đổi)
RUN ./gradlew --version

# COPY PROJECT
COPY . .

# Xử lý lỗi Exit Code 127 (Không tìm thấy lệnh hoặc sai định dạng file)
# 1. Chuyển đổi định dạng xuống dòng từ Windows (CRLF) sang Linux (LF)
# 2. Cấp quyền thực thi cho file gradlew
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Build module cụ thể
# Module Path khác nhau thì sẽ ảnh hưởng cache
ARG MODULE_PATH
RUN ./gradlew :${MODULE_PATH}:bootJar -x test --no-daemon -q

# --- Giai đoạn Run ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

ARG JAR_PATH

# Copy file jar từ kết quả của giai đoạn build
COPY --from=build /app/build/outputs/${JAR_PATH}/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]