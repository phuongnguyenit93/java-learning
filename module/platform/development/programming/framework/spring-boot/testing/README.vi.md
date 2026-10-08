# 📂 README MODULE STRUCTURE (VI)

* **1.Introduction**
    * [BootTesting](readme/vi/menu/1.Introduction/BootTesting.md)
* **2.SpringBootTest**
    * [SpringBootTest](readme/vi/menu/2.SpringBootTest/SpringBootTest.md)
* **3.WebEnvironment**
    * [WebEnvironment](readme/vi/menu/3.WebEnvironment/WebEnvironment.md)
* **4.TestSlices**
    * [TestSlices](readme/vi/menu/4.TestSlices/TestSlices.md)
* **5.WebLayerTesting**
    * [WebLayerTesting](readme/vi/menu/5.WebLayerTesting/WebLayerTesting.md)
* **6.DataLayerTesting**
    * [DataLayerTesting](readme/vi/menu/6.DataLayerTesting/DataLayerTesting.md)
* **7.TestAutoConfiguration**
    * [TestAutoConfiguration](readme/vi/menu/7.TestAutoConfiguration/TestAutoConfiguration.md)
* **8.PropertyOverrides**
    * [PropertyOverrides](readme/vi/menu/8.PropertyOverrides/PropertyOverrides.md)
* **9.MockReplacement**
    * [MockReplacement](readme/vi/menu/9.MockReplacement/MockReplacement.md)
* **10.Testcontainers**
    * [Testcontainers](readme/vi/menu/10.Testcontainers/Testcontainers.md)
* **11.IntegrationTestingStrategy**
    * [IntegrationTestingStrategy](readme/vi/menu/11.IntegrationTestingStrategy/IntegrationTestingStrategy.md)

# Kiểm thử ứng dụng với Spring Boot

Module này giải thích cách Spring Boot tạo test context gắn với chính ứng dụng và giúp bạn chọn mức tích hợp nhỏ nhất nhưng vẫn đủ để chứng minh hành vi cần kiểm thử. Trọng tâm là bootstrap test riêng của Boot, full context với `@SpringBootTest`, web environment, test slice tập trung, test auto-configuration, tùy biến context chỉ dành cho test và service connection với Testcontainers.

## Bạn sẽ học gì?

Bạn sẽ học cách Boot tìm cấu hình chính của ứng dụng, khi nào cần full context, `WebEnvironment` thay đổi mức độ sát server thật và ranh giới transaction như thế nào, web/data slice giới hạn context ra sao, các annotation `@AutoConfigure...` tùy biến hạ tầng test thế nào, properties, `@TestConfiguration`, `@MockBean` và `@SpyBean` ảnh hưởng context cùng chi phí tái sử dụng ra sao, và cách `@ServiceConnection` chuyển container được hỗ trợ thành `ConnectionDetails` cho integration test dùng dịch vụ thật.

## Kiến thức cần có

Bạn nên nắm kiến thức nền về Spring Boot, auto-configuration, externalized configuration, web runtime và application context của Spring. Module giả định bạn đã biết từ vựng kiểm thử cơ bản nhưng không dạy JUnit, Mockito, AssertJ, vòng đời/giao dịch/cache tổng quát của Spring TestContext hay cơ chế container của Testcontainers.

## Lộ trình học

1. Xác định Boot bổ sung gì cho kiểm thử có application context và cách Boot tìm cấu hình chính của ứng dụng.
2. Dùng `@SpringBootTest` cho full context, chọn `WebEnvironment` phù hợp và hiểu ranh giới transaction khi chạy server thật.
3. Ưu tiên Boot test slice nhỏ nhất nhưng đủ dùng, rồi học mô hình web slice và data slice tập trung.
4. Hiểu test auto-configuration phía sau các slice và tùy biến có chủ đích bằng `@AutoConfigure...`, cơ chế loại trừ và import.
5. Tùy biến test context bằng properties cục bộ, `@TestConfiguration`, mock và spy đồng thời tính đến việc làm phân mảnh context cache.
6. Kết nối Testcontainers được hỗ trợ với Boot qua `@ServiceConnection` và `ConnectionDetails`, chỉ dùng `@DynamicPropertySource` khi service connection không phù hợp.
7. Tổng hợp chiến lược kiểm thử cân bằng kích thước context, độ sát production, mức bao phủ qua server/dịch vụ thật và chi phí khởi động của bộ test.

## Ranh giới module

Module này chịu trách nhiệm cho test bootstrap của Spring Boot, `@SpringBootTest`, các Boot test slice và test auto-configuration đi kèm, tùy biến test context riêng của Boot và tích hợp service connection với Testcontainers. Vòng đời, transaction và cơ chế cache tổng quát của Spring TestContext thuộc Spring Framework testing; kiến thức nền về JUnit, Mockito, AssertJ và các thư viện test khác thuộc module chuyên trách; vòng đời container do Testcontainers/JUnit quản lý, image, network và API container tổng quát của Testcontainers nằm ngoài phạm vi Boot.
