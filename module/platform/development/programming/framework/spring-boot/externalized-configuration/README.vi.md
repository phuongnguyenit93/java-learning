# 📂 README MODULE STRUCTURE (VI)

* **1.ConfigurationMentalModel**
    * [ConfigurationMentalModel](readme/vi/menu/1.ConfigurationMentalModel/ConfigurationMentalModel.md)
* **2.PropertySourcesAndPrecedence**
    * [PropertySourcesAndPrecedence](readme/vi/menu/2.PropertySourcesAndPrecedence/PropertySourcesAndPrecedence.md)
* **3.ConfigDataFilesAndLocations**
    * [ConfigDataFilesAndLocations](readme/vi/menu/3.ConfigDataFilesAndLocations/ConfigDataFilesAndLocations.md)
* **4.ConfigDataImportsAndTrees**
    * [ConfigDataImportsAndTrees](readme/vi/menu/4.ConfigDataImportsAndTrees/ConfigDataImportsAndTrees.md)
* **5.ProfilesAndActivation**
    * [ProfilesAndActivation](readme/vi/menu/5.ProfilesAndActivation/ProfilesAndActivation.md)
* **6.EnvironmentValueAndPlaceholders**
    * [EnvironmentValueAndPlaceholders](readme/vi/menu/6.EnvironmentValueAndPlaceholders/EnvironmentValueAndPlaceholders.md)
* **7.ConfigurationProperties**
    * [ConfigurationProperties](readme/vi/menu/7.ConfigurationProperties/ConfigurationProperties.md)
* **8.RelaxedAndComplexBinding**
    * [RelaxedAndComplexBinding](readme/vi/menu/8.RelaxedAndComplexBinding/RelaxedAndComplexBinding.md)
* **9.ValidationAndMetadata**
    * [ValidationAndMetadata](readme/vi/menu/9.ValidationAndMetadata/ValidationAndMetadata.md)
* **10.DiagnosticsAndSynthesis**
    * [DiagnosticsAndSynthesis](readme/vi/menu/10.DiagnosticsAndSynthesis/DiagnosticsAndSynthesis.md)

# Spring Boot Externalized Configuration

Module này xây mô hình tư duy về cách Spring Boot nhận cấu hình từ nhiều nguồn, phân giải giá trị có hiệu lực, nạp Config Data, kích hoạt profile, cung cấp từng giá trị và ánh xạ cấu hình có cấu trúc vào đối tượng có kiểu.

## Bạn sẽ học gì?

Bạn sẽ học cách suy luận về thứ tự ưu tiên giữa các nguồn thuộc tính, Config Data đóng gói và bên ngoài, quy tắc vị trí/import, profile, `Environment`, placeholder, `@Value`, `@ConfigurationProperties`, relaxed binding và binding kiểu phức hợp, xác thực, metadata cấu hình và các giai đoạn lỗi giúp chẩn đoán vấn đề cấu hình.

## Kiến thức cần có

Bạn nên hiểu mô hình tư duy khởi động của Spring Boot và các khái niệm cơ bản về Spring bean/ApplicationContext. Phần triển khai nội bộ của Spring Framework `Environment`, Bean Validation tổng quát, Spring Cloud Config và hệ thống quản lý secret trong môi trường vận hành thực tế chủ ý nằm ngoài module này.

## Lộ trình học

1. Xây mô hình tư duy về externalized configuration và `Environment`.
2. Hiểu cách các nguồn thuộc tính cạnh tranh và thứ tự ưu tiên quyết định giá trị có hiệu lực.
3. Học các tệp Config Data, vị trí tìm kiếm mặc định, vị trí tùy chỉnh và các đầu vào xác định vị trí phải được cung cấp sớm.
4. Bổ sung import, tài nguyên tùy chọn, gợi ý định dạng và cây cấu hình.
5. Dùng profile và cấu hình theo profile mà không biến profile thành công tắc triển khai cho mọi vấn đề.
6. Đọc giá trị đơn lẻ qua `Environment`, placeholder và `@Value`.
7. Mô hình hóa cấu hình theo nhóm bằng `@ConfigurationProperties`.
8. Binding tên linh hoạt, đối tượng lồng nhau, collection, map và các kiểu giá trị sau chuyển đổi.
9. Xác thực cấu hình và tạo metadata phục vụ công cụ.
10. Chẩn đoán lỗi nạp, kích hoạt, binding, chuyển đổi kiểu và xác thực như các giai đoạn của một luồng phân giải từ đầu đến cuối.

## Ranh giới module

Module này sở hữu cách Spring Boot nạp cấu hình, thứ tự ưu tiên, Config Data, profile, cách tiêu thụ cấu hình ở tầng Boot, binding có kiểu, tích hợp xác thực và metadata. Phần triển khai nội bộ của Spring Framework, cơ chế ghi đè chỉ dành cho kiểm thử, Spring Cloud Config, quản lý secret tổng quát và cấu hình riêng của nền tảng triển khai vẫn thuộc các module chuyên trách.
