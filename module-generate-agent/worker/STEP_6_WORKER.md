# STEP 6 — WORKER ONLY: Xây dựng API

**CURRENT_STEP policy (2026-10-25):** Progress is owned ONLY by `CURRENT_STEP.VALUE` (string) in the verified module `master.json`, never by any Google Sheet/Excel cell. Prime updates after full QA using `module-generate-agent/CURRENT_STEP.md`; Worker does not write it. Any old cell, label, formula, or Sheet-readback instructions about progress below are superseded. The STEP-specific completion gates still apply.


**Worker phải tự đọc TOÀN BỘ file này trước khi thực hiện nhiệm vụ**, không đọc file Prime, rules/MD/TXT ngoài nguồn được phép đọc. Tin giao việc Prime → Worker luôn gồm **đúng 4 dòng định vị**: URL Sheet/tab, các ô nguồn của **một chương**, **cả hai đường dẫn tuyệt đối tới package Java main và test mà Prime cấp quyền ghi**, và đường dẫn tuyệt đối tới file worker-only này. Worker không tự mở rộng vùng đọc/ghi hay chọn package khác.

## Chỉ HAI trách nhiệm của Worker

1. **Đọc TOÀN BỘ một Menu Cấp 1**: xác minh Sheet/tab, A1/B1 và A/B hàng cha đã giao; đọc **tất cả** Menu Cấp 2 và toàn bộ Knowledge C:N trên từng hàng, nối C→N **không chèn separator**. Không chỉ đọc đề mục hay vài bài mẫu, không dùng README source thay cho Sheet khi hợp đồng yêu cầu đọc Sheet. Chọn API/thí nghiệm có giá trị thật sau khi hiểu toàn chương; chương không cần API → báo `API_NOT_REQUIRED` với lý do rõ ràng.
2. **Tự thiết kế, hiện thực và kiểm chứng API Java có hành vi thực**: input → logic/đối tượng hợp tác → output có thể quan sát; kiểm soát input, lỗi, side effect, số thao tác, tính lặp lại; test đúng/sai/edge cases. Không tạo quota endpoint, không chỉ bàn giao design/YAML, không nhồi lý thuyết vào JSON giả kết quả chạy.

## Package skeleton do STEP_5_WORKER tạo — bắt buộc dùng

**Prime đầu STEP 5** đã chuẩn bị module `SERVLET`, bật `BUILD_SWAGGER` và sync cấu hình; **worker STEP 5** sau đó vừa review chương vừa tạo skeleton main/test **với tên package tiếng Anh theo filename Markdown B cha**, không phải tiếng Việt bỏ dấu. **Prime STEP 6** xác minh và giao đúng cặp package đã có, đã đối chiếu B cha, cho worker STEP 6; không tự chọn tên khác. Cấu trúc:

```text
<module>/src/main/java/<base-package>/<chapter-package>/
  controller/
  dto/
  service/
  model/
  exception/
  repository/
<module>/src/test/java/<base-package>/<chapter-package>/...
```

- Worker **chỉ tạo/chỉnh Java code và tests trong HAI đường dẫn package đã được Prime ghi chính xác ở dòng thứ ba**. Kiểm tra chúng đều thuộc cùng module, cùng một chương, tương ứng `src/main/java` và `src/test/java`; không ghi package cha hoặc module chung. `@RestController` ở `controller/`, DTO/request/response ở `dto/`, nghiệp vụ ở `service/`, domain ở `model/`, handler/exception ở `exception/`, persistence ở `repository/` nếu thực cần. **Không để Controller/DTO/Service/Model trực tiếp ở gốc `<chapter-package>/`**.
- Không tạo class rỗng cho các thư mục chưa dùng. Skeleton do STEP 5 worker tạo có thể mang marker rỗng `.gitkeep` để Git giữ thư mục; **không xóa marker trong package chưa có code khi vẫn đang giữ ownership**. Sau khi worker API bàn giao, Prime STEP 6 dọn marker dư trong package đã có source và xóa package chưa dùng chỉ có `.gitkeep`; worker không tự xóa skeleton/module hay code của người khác. Nếu dòng phân quyền thiếu một trong hai package, chỉ ghi root chung, package không khớp Menu Cấp 1, skeleton **do STEP 5 worker chịu trách nhiệm** còn thiếu, source tồn tại chưa có quyền chỉnh, route/class conflict hoặc ownership mơ hồ → `BLOCKER: CHAPTER PACKAGE SKELETON MISSING / INVALID OWNERSHIP`, `WRITE=NOT_RUN`; báo Prime trả về STEP 5 để xử lý, không tự chọn đường dẫn khác/tạo skeleton.
- Test đặt đúng namespace tương ứng trong `src/test/java`; không ghi shared helpers ngoài ownership. Worker có thể đọc source module khác để hiểu API khi được cho phép, nhưng không sửa.

## MVC, ExceptionHandler và Application

- **Controller mỏng**: nhận/validate HTTP request, gọi Service và trả response; không chứa business logic. **Service/domain** chứa logic/chứng cứ thí nghiệm; **DTO** biểu diễn input/output; **Repository** chỉ khi cần dữ liệu lưu trữ. REST JSON/HTTP là biểu diễn View, không phải tạo HTML View. Với Reactive/WebFlux giữ đúng stack và separation of concerns, không tự chuyển sang Servlet.
- **Không đặt `@ExceptionHandler` trong Controller.** Khi cần custom HTTP error mapping, đặt class xử lý tại `exception/`, dùng `@RestControllerAdvice` **scope cho đúng Controller/chương** hoặc sử dụng handler shared đã tồn tại nếu Prime xác nhận tương thích. Không viết global handler trùng và làm ảnh hưởng chương khác; logic ném lỗi hợp lệ trong Service/domain nhưng không trả HTTP từ Service.
- **Tuyệt đối không tạo/sửa `*Application.java`, `@SpringBootApplication` hoặc class khởi động.** Prime STEP 5 đã setup `SERVLET` + `BUILD_SWAGGER` và sync Gradle để generator tạo Application; worker STEP 6 chỉ xác minh code dùng được, không tạo lần hai. Nếu thiếu Application/dependency hoặc còn `LIBRARY`, báo Prime blocker để xử lý setup STEP 5; không tự sửa `master.json`, `properties.json`, `build.gradle`, task/Gradle hay config chung.

## KHÔNG thuộc STEP 6 — để STEP khác theo yêu cầu riêng của người dùng

- **Swagger info / API documentation:** worker không soạn, cập nhật hay sinh `controller-description.yml`, `api-descriptions.yml`, `api-execution.yml`, `api-params.yml`, `api-cases.yml`; không viết summary/description, execution guide, params description, request/response examples, expected results hoặc use-case mẫu. Worker **không chạy Swagger scanner/generator**. **Riêng Prime, sau khi toàn bộ worker STEP 6 xong, chạy `generateApiSwaggerDescription` để sinh skeleton `api-cases.yml` và metadata kỹ thuật; không tự điền nội dung.** Nội dung Swagger/use cases dành cho STEP mới chưa đánh số.
- **Mapping:** không tạo hoặc sửa API ↔ Knowledge/Quiz/Interview, `readmeRelated`, `apiRelated`, file/anchor/controller/methodSignature hay các liên kết chéo; STEP mapping **riêng biệt** chưa được yêu cầu thực thi.
- **Dữ liệu và dự án khác:** không ghi Google Sheets/CURRENT_STEP.VALUE, không sửa README/Quiz/Interview/Video/Template, Swagger resources, dependencies/setup/Application hoặc source thuộc worker khác; không chạy Sheet sync/ByFiles/ByLocation, không commit/push.

Việc Spring tự tạo OpenAPI **cấu trúc kỹ thuật ở runtime** khi kiểm thử không có nghĩa worker được phép viết/tạo các YAML hoặc các ví dụ Swagger.

## Trình tự và báo cáo khi hợp đồng thực thi được duyệt

1. Xác minh Sheet, đúng chương, tất cả hàng Knowledge C:N; **đối chiếu hai package main/test được Prime giao trong dòng thứ ba với skeleton thực tế và chapter**, preflight ownership, route và Application đã được setup. Nguồn thiếu/truncated, đường dẫn sai/trùng vùng ownership thì blocker; không tự quyết định lại phạm vi.
2. Phân tích toàn chương, chỉ chọn thí nghiệm cần API, triển khai Java/test theo package con và MVC; không tạo endpoint chỉ cho đủ bài.
3. Tự QA compile/test/hành vi, xử lý lỗi đúng; chỉ báo runtime/HTTP PASS nếu thực tế đã test. Nếu chưa đủ cấu hình/kiểm thử, báo rõ BLOCKER hoặc điều kiện chưa xác minh, không tự thay đổi cấu hình.
4. Báo Prime: `STATUS=COMPLETED/API_NOT_REQUIRED/BLOCKER/FAILED`, `CHAPTER`, `CHILD_ROWS`, `KNOWLEDGE_COVERAGE`, `MAIN_PACKAGE_PATH`, `TEST_PACKAGE_PATH`, `SOURCE_OWNERSHIP`, `PACKAGES_USED`, `CONTROLLERS/SERVICES/DTO/MODELS/HANDLERS`, `ROUTES`, `TESTS`, `COMPILE`, `RUNTIME`, `BLOCKERS`. Giải phóng ownership, chuyển `SLEEPING`; Prime là người nghiệm thu.

Nếu không thể xác minh phạm vi hoặc quyền sở hữu, **không ghi code, không sửa Sheet**; báo `STATUS=BLOCKER`, `WRITE=NOT_RUN`, nêu chính xác đường dẫn/vùng đang xung đột, giải phóng ownership và chuyển `SLEEPING`.
