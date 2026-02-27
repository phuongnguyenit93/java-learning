# --- Giai đoạn Build ---
FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /app

# 1. Copy tất cả
COPY . .

# Xử lý lỗi Exit Code 127 (Không tìm thấy lệnh hoặc sai định dạng file)
# 1. Chuyển đổi định dạng xuống dòng từ Windows (CRLF) sang Linux (LF)
# 2. Cấp quyền thực thi cho file gradlew
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Build module cụ thể
# Module Path khác nhau thì sẽ ảnh hưởng cache
ARG MODULE_PATH
ARG RELATIVE_PATH

## 1. Mount cache cho thư mục .gradle để không phải tải lại thư viện mỗi lần build
## 2. Mount bind cho mã nguồn để không cần dùng lệnh COPY
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${MODULE_PATH}:bootJar -x test --build-cache --no-daemon && \
    mkdir -p /app/final && \
    cp /app/build/jar/${RELATIVE_PATH}/*.jar /app/final/app.jar


# --- Giai đoạn Run ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy file jar từ kết quả của giai đoạn build
COPY --from=build /app/final/app.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]