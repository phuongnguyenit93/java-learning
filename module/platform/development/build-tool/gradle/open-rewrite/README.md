# Open-Rewrite : Công cụ tự động nâng cấp source code

## <a id="open-rewrite-basic">Tự động hóa Refactoring với OpenRewrite</a>

**OpenRewrite** là một công cụ mã nguồn mở mạnh mẽ được thiết kế để tự động hóa việc tái cấu trúc mã nguồn (refactoring) trên quy mô lớn. Nó không chỉ đơn thuần là tìm kiếm và thay thế văn bản, mà nó thực sự "hiểu" cấu trúc của mã để thực hiện các thay đổi một cách thông minh và an toàn.

## 1. Cách thức hoạt động
OpenRewrite sử dụng một khái niệm gọi là **Lossless Semantic Tree (LST)**.



* **Phân tích:** Khi bạn chạy OpenRewrite, nó quét mã nguồn và xây dựng một cây sơ đồ chi tiết về cú pháp và ngữ nghĩa của code.
* **"Lossless" (Không mất mát):** Giữ nguyên định dạng gốc của bạn (khoảng trắng, chú thích, dấu ngoặc) để sau khi sửa đổi, mã trông vẫn tự nhiên.
* **Recipe (Công thức):** Áp dụng các quy tắc lập trình sẵn để tìm và sửa các mẫu mã cụ thể.

## 2. Các ứng dụng phổ biến
OpenRewrite đặc biệt mạnh mẽ trong hệ sinh thái Java và Spring Boot:

* **Nâng cấp Framework:** Tự động chuyển đổi từ Spring Boot 2.x lên 3.x, hoặc nâng cấp từ Java 8 lên Java 17/21.
* **Sửa lỗi bảo mật:** Tự động tìm các thư viện lỗi thời có lỗ hổng bảo mật (CVE) và cập nhật chúng.
* **Tuân thủ quy chuẩn (Linting):** Tự động sửa các lỗi phong cách code (xóa import thừa, đổi tên biến đúng chuẩn).
* **Di chuyển thư viện:** Chuyển đổi từ JUnit 4 sang JUnit 5 hoặc cập nhật Mockito.

## 3. Tại sao nó lại hữu ích?
Trong các dự án lớn (Multi-module hoặc Microservices), việc nâng cấp thủ công là cực kỳ rủi ro. OpenRewrite giúp:

* **Tiết kiệm thời gian:** Thực hiện hàng nghìn thay đổi chỉ trong vài giây.
* **Đảm bảo tính nhất quán:** Tất cả các module đều áp dụng cùng một tiêu chuẩn sửa đổi.
* **Tích hợp dễ dàng:** Chạy thông qua Gradle hoặc Maven plugin mà không cần cài đặt phức tạp.

## 4. Ví dụ lệnh chạy với Gradle
Để sử dụng OpenRewrite kiểm tra các Best practices cho dự án Java, bạn thêm plugin vào `build.gradle` và chạy lệnh sau:

	```bash
	./gradlew rewriteRun
	```

---

### 💡 Lưu ý cho dự án java-learning:
Khi bạn có kế hoạch chuyển đổi dự án từ Java 11 lên Java 21 để sử dụng **Virtual Threads**, OpenRewrite sẽ là "trợ thủ" đắc lực giúp bạn quét lại toàn bộ mã nguồn và gợi ý những thay đổi cần thiết để tương thích với phiên bản mới nhất.


## <a id="open-rewrite-setup">Hướng dẫn: Tích hợp OpenRewrite vào dự án Multi-module (Gradle)</a>

Để tích hợp OpenRewrite vào một dự án Multi-module, bạn cần thực hiện cấu hình tại dự án gốc (Root) để có thể quản lý việc tái cấu trúc (refactor) cho tất cả các module con cùng một lúc.

## 1. Cấu hình file build.gradle (Root Project)

Bạn thêm plugin và khai báo các Recipe (công thức) cần thiết trong khối `subprojects` để áp dụng cho toàn bộ hệ thống.

```gradle
	plugins {
	    id("org.openrewrite.rewrite") version "6.23.0"
	}
	
	rewrite {
	    // Chỉ định các Recipe bạn muốn áp dụng (ví dụ: Cleanup và Migrate Java 17)
	    activeRecipe("org.openrewrite.java.cleanup.Cleanup", "org.openrewrite.java.migrate.Java17")
	}
	
	subprojects {
	    apply plugin: "org.openrewrite.rewrite"
	    
	    dependencies {
	        // Khai báo các thư viện chứa Recipe (chỉ dùng trong quá trình rewrite)
	        rewrite("org.openrewrite.recipe:rewrite-migrate-java:2.20.0")
	        rewrite("org.openrewrite.recipe:rewrite-static-analysis:1.15.0")
	        rewrite("org.openrewrite.recipe:rewrite-spring:5.18.0")
	    }
	}
```

## 2. Các lệnh thực thi quan trọng

OpenRewrite cung cấp 2 tác vụ chính mà bạn sẽ thường xuyên sử dụng:

* **Kiểm tra thử (`rewriteDryRun`):** Quét mã nguồn và tạo file báo cáo (thường ở `build/reports/rewrite/rewrite.patch`). Lệnh này **không** thay đổi code, giúp bạn xem trước các sửa đổi.
```bash
  ./gradlew rewriteDryRun
 ```

* **Áp dụng thay đổi (`rewriteRun`):** Thực sự ghi đè và thay đổi file mã nguồn của bạn dựa trên các Recipe đã chọn.
```bash
  ./gradlew rewriteRun
```

## 3. Các Recipe hữu ích cho Backend Java/Spring

Bạn có thể thay đổi danh sách trong `activeRecipe` tùy theo nhu cầu hiện tại của dự án:

| Tên Recipe | Mục đích | Thư viện cần thêm |
| :--- | :--- | :--- |
| **UpgradeSpringBoot_3_2** | Nâng cấp toàn bộ dự án lên Spring Boot 3.2. | `rewrite-spring` |
| **JUnit4to5Migration** | Chuyển đổi Unit Test từ JUnit 4 sang 5. | `rewrite-testing` |
| **CommonStaticAnalysis** | Tự động sửa các lỗi code smell (SonarLint). | `rewrite-static-analysis` |

## 4. Lưu ý khi dùng cho dự án Multi-module

* **Chạy cho module cụ thể:** Nếu bạn chỉ muốn chạy cho một module (ví dụ: `api-gateway`), hãy dùng lệnh:
```bash
  ./gradlew :api-gateway:rewriteRun
```
* **Git Check:** Luôn đảm bảo bạn đã **commit** hoặc **stash** code trước khi chạy `rewriteRun`. Điều này giúp bạn dễ dàng so sánh (diff) và kiểm tra lại các thay đổi tự động của công cụ.