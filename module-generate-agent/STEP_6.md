# STEP 6 — PRIME ONLY: Xây dựng và nghiệm thu API



## 0. Vị trí trong chuỗi STEP

- STEP 5: Review Knowledge, Difficulty, filename và đồng bộ README về module.
- **STEP 6: Xây dựng API chạy được** theo Knowledge của từng chương; sau khi toàn bộ source được nghiệm thu, **Prime chạy task `generateApiSwaggerDescription` để sinh skeleton `swagger/{vi,en}/api-cases.yml`**. **Không điền nội dung Swagger/API Docs/use cases** ở STEP này.
- STEP 8: Chưa định nghĩa, để dành cho bước mới.
- STEP 9: Interview YAML (P:S), từ STEP 8 cũ.
- STEP 10: Quiz YAML (T:W), từ STEP 9 cũ. Video Script YAML (X:AA) chỉ chạy ở STEP_OPTIONAL_VIDEO sau STEP_FINAL.

Đây là workflow **Google Sheets/Worker trong `module-generate-agent/`**. Không thay đổi bộ STEP canonical độc lập ở `module-generate-agent/STEP_*.md`. Việc đánh số không làm thay đổi nội dung, quyền sở hữu, task hay dữ liệu đã có của Interview/Quiz/Video.

## Quyết định đã chốt — phân quyền STEP 6

**Mỗi worker chỉ có HAI trách nhiệm chuyên môn**, cho đúng **một Menu Cấp 1**:

1. **Đọc TOÀN BỘ chương**: đọc tất cả bài Knowledge thuộc Menu Cấp 2 của chương, không chọn vài bài mẫu hoặc chỉ đọc tiêu đề; nắm khái niệm, ví dụ, giới hạn và chọn thí nghiệm API mang giá trị học tập thật. Nếu chương không cần API, báo `API_NOT_REQUIRED` cùng lý do dựa trên toàn chương; không tạo endpoint cho đủ số lượng.
2. **Xây dựng API thực sự**: tự thiết kế và triển khai các API/thí nghiệm phù hợp, bao gồm Controller, Service, DTO/Model, exception hoặc Repository **khi cần**, cùng test/edge cases và bằng chứng runtime. Logic nằm ở Service/domain, Controller là HTTP adapter mỏng. Worker chỉ viết code trong **các package con đã được STEP_5_WORKER tạo theo chương**, được Prime STEP 6 phân quyền cụ thể; không đặt lẫn Controller/Service/DTO/Model tại package gốc chương.

**Yêu cầu kiến trúc MVC / phân tầng cho API (bắt buộc khi phù hợp với loại module):** Worker xây dựng HTTP API theo mô hình **Model–View–Controller (MVC)** và tách rõ trách nhiệm: **Controller** nhận/validate request ở HTTP boundary, gọi Service, trả response; **Service/domain Model** xử lý nghiệp vụ/thí nghiệm; **DTO** là hợp đồng input/output; **Repository** chỉ khi thực sự truy cập dữ liệu. Với REST API, JSON/HTTP là biểu diễn trả về, không bắt tạo HTML View. Không tạo class/tầng rỗng cho đủ mẫu. Với WebFlux/Reactive, giữ nguyên stack tương ứng, không ép Servlet MVC. Prime nghiệm thu MVC; **worker tự sửa code nếu không đạt**.

### Quy ước package — worker STEP 5 tạo, Prime STEP 6 phân quyền và nghiệm thu

**Đầu STEP 5**, Prime chuyển đúng module sang **`MODULE_TYPE=SERVLET`**, bật **`BUILD_SWAGGER=TRUE`** (schema không có khóa `USE_SWAGGER`) rồi **sync cấu hình Gradle trước khi giao worker STEP 5**; generator tạo hoặc giữ nguyên `*Application.java`. **Mỗi STEP_5_WORKER** vừa review chương/đánh Difficulty/điền filename vừa **tự tạo package skeleton Java main/test cho chương**. Tên package là **tiếng Anh theo basename filename Markdown của B cha** (PascalCase → lowercase snake_case, xử lý Java keyword bằng `_chapter`), tuyệt đối không phiên âm A cha tiếng Việt. **Đến STEP 6, Prime đối chiếu B cha với skeleton STEP 5 bàn giao và GIAO CỤ THỂ package main/test cho từng worker**, không tự tạo skeleton hộ STEP 5 worker hoặc để STEP 6 worker chọn package khác.

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

- Worker phải đặt **đúng loại class vào đúng package con** (`controller`, `dto`, `service`, `model`, `exception`, `repository`); không gom các loại class này ở package gốc `<chapter-package>`. Nếu một vai trò không cần dùng thì không tạo class giả.
- Sau khi **tất cả worker STEP 6 hoàn thành và giải phóng ownership**, Prime kiểm tra để **dọn package con chưa dùng và chỉ có marker rỗng `.gitkeep`**, xóa marker dư trong package có Java code; không xóa source hiện hữu, marker mang nội dung, source người khác hay file ngoài module được giao. Không dọn package đang có worker sở hữu; nếu cần di chuyển/refactor code sai package thì trả lại worker STEP 6 thực hiện, Prime không viết hộ.
- Trước khi giao STEP 6, Prime **readback** output package đã được **worker STEP 5 tạo và nghiệm thu**, đối chiếu đúng Menu Cấp 1 và xác định **hai đường dẫn tuyệt đối** dưới module: `src/main/java/.../<chapter-package>/` và `src/test/java/.../<chapter-package>/`. Mỗi worker STEP 6 nhận đúng một cặp vùng ghi riêng; không giao root cha chung hoặc trùng vùng. Nếu có source cũ, Prime xác minh ownership trước khi cho sửa.
- Khi thiếu skeleton hoặc không chứng minh được ranh giới ownership, Prime báo `BLOCKER: CHAPTER PACKAGE SKELETON MISSING / INVALID OWNERSHIP` và **trả về quy trình STEP 5 để worker sở hữu bổ sung/correct**, không cho worker STEP 6 tự chế cấu trúc thay STEP 5.

### Exception handling

- **Không đặt `@ExceptionHandler` trong từng experiment Controller.** Controller tập trung vào các endpoint. Nếu cần xử lý lỗi HTTP, worker tạo handler trong `exception/` (ví dụ `@RestControllerAdvice` giới hạn phạm vi Controller của chương) hoặc sử dụng handler dùng chung **đã có và được Prime xác minh**; tránh nhiều advice vô tình bắt lỗi chéo chương.
- Service/domain có thể phát sinh exception đúng nghĩa, nhưng không nhồi HTTP response mapping vào Service. Có thể không cần handler mới nếu framework/handler hiện có đáp ứng đúng.

### Application & runtime

- **Prime STEP 5** đã đổi loại module thành `SERVLET` (nếu chưa là SERVLET), bật `BUILD_SWAGGER`, **sync Gradle** và xác minh Application từ generator trước khi giao worker STEP 5. Prime STEP 6 chỉ **readback/preflight** sự tồn tại và tính duy nhất của `*Application.java`, loại module và dependency/runtime trước khi giao worker STEP 6, kiểm tra build/test/HTTP khi nghiệm thu. Nếu chưa setup đúng → blocker chuyển về setup STEP 5, không để worker vá bằng Application mới.
- **Không tạo thêm Application class thủ công**, không để worker tự tạo/sửa `@SpringBootApplication`, `master.json`, Gradle, cấu hình Swagger hoặc source khởi động chung. Không viết Swagger info ở STEP 6 dù `BUILD_SWAGGER` đã bật từ STEP 5.

### Prime STEP 6 — giao vùng package, nghiệm thu và điều phối, KHÔNG điền Swagger

**Prime điều phối và giao vùng package trước khi worker bắt đầu; chuyên môn Prime là nghiệm thu API.** Trình tự:

**BẮT BUỘC chạy hết TOÀN BỘ tab, không dừng sau đợt giao worker đầu tiên.** Khi bắt đầu hoặc tiếp tục STEP 6 (kể cả sau phiên bị gián đoạn), Prime lập sổ theo dõi **tất cả Menu Cấp 1**: `PARENT_ROW → CHAPTER_PACKAGE → CHILD_ROWS → MAIN/TEST PATHS → STATUS → OWNER → QA`. Đối chiếu trạng thái thật trong repo cùng báo cáo worker trước khi giao: chương đã có API/test và đã được nghiệm thu thì **SKIP, không giao làm lại**; chương có source nhưng chưa đủ chứng cứ QA là `PENDING_QA`, không coi là trống và không ghi đè; chương thật sự chưa làm mới xếp `PENDING`. User có thể chỉ định bỏ qua những chương đã hoàn thành từ đợt trước (ví dụ ba chương OOP đầu); ưu tiên bảo toàn source hiện hữu.

**Bộ điều phối Mode A/B của session:**

- `Mode A` tối đa **3 worker ACTIVE + SLEEPING**; `Mode B` tối đa **N worker do user chỉ định** (thiếu N hợp lệ → `BLOCKER: SPECIAL_MODE_WORKER_COUNT_REQUIRED`). Tái sử dụng worker `SLEEPING` trước, **mỗi worker đúng một chương mỗi lượt**. Khi cần spawn, tạo **từng worker một**, kiểm tra kết quả mỗi lệnh trước khi tạo tiếp. Worker `FAILED/FINISHED` không còn dùng được được loại khỏi pool khả dụng; chỉ thay thế khi kiểm tra lại trạng thái pool và không vượt mức của mode. Không tạo worker mới chỉ vì worker khác đang bận hoặc vì cần heartbeat.
- Ngay khi worker báo hoàn thành **và ownership thực sự đã giải phóng**, Prime nhận và kiểm tra kết quả, cập nhật trạng thái sổ, rồi **giao chương tiếp theo trong hàng đợi cho worker rảnh**; không đợi tất cả worker của batch cùng xong. Nếu worker trả `API_NOT_REQUIRED`, Prime xác minh lý do từ toàn chương trước khi nghiệm thu/skip.
- Worker `ACTIVE` chỉ được theo dõi bằng trạng thái/heartbeat **thụ động**. Không nhắn `continue/status?` để đánh thức worker vẫn đang chạy, không giao việc thứ hai vào cùng ownership. Nếu bị ngắt session hoặc worker lỗi, **khôi phục từ source/test đã có và báo cáo cuối cùng**, chỉ giao lại phần còn thiếu cho một owner độc quyền; không replay mù hoặc xóa code chưa nghiệm thu.
- Với từng chương, Prime nghiệm thu **sau khi worker ngừng ghi**: toàn bộ Knowledge đã đọc, class ở đúng tầng/package, tính hữu ích của API, route không trùng, input/errors/boundaries, domain/service separation, test và bằng chứng chạy. Kiểm tra độc lập báo cáo với file thực tế; kết quả worker `COMPLETED` chưa tương đương `QA_PASS`. Nếu FAIL thì giao lại **đúng vùng chương** cho worker đang được cấp ownership sửa, không để Prime viết Java nghiệp vụ hộ.
- Khi hàng đợi rỗng và **không còn writer**, Prime chạy QA tích hợp trên đúng module (`compileJava`, `test`, kiểm tra OpenAPI/HTTP runtime khi có thể), xử lý lỗi qua ownership worker của chương liên quan, rồi dọn **chỉ** marker `.gitkeep` dư/thư mục chỉ còn marker trong chapter package đã hoàn thành. Không dọn chapter chưa xử lý, không xóa file thật hoặc marker khác lạ. Đối chiếu đủ mọi Menu Cấp 1 trong scope mới báo STEP 6 `COMPLETED`.
- Bàn giao cuối bắt buộc nêu `SESSION_WORKER_MODE`, `MAX_WORKERS`, `SKIPPED_EXISTING`, `COMPLETED_QA`, `API_NOT_REQUIRED`, `BLOCKER`, `PENDING`, kết quả build/test/runtime, trạng thái Git và danh sách chương chưa xong. **Không commit/push hoặc cập nhật CURRENT_STEP.VALUE chỉ vì worker báo code API xong**; chỉ cập nhật trạng thái sau khi toàn bộ completion gate STEP 6 đạt PASS theo mục dưới đây. STEP Swagger info/examples và STEP mapping vẫn tách riêng.

1. Đọc quy tắc chung và ngữ cảnh Sheet/module; xác minh đúng tab qua `gid`, `A1/B1`, từng Menu Cấp 1, `A/B` hàng cha và toàn bộ dải `B, C:N` của các Menu Cấp 2 thuộc chương; không đoán ranh giới chương.
2. **Readback package skeleton do STEP_5_WORKER tạo**, trạng thái Application/runtime đã được Prime STEP 5 sync, source hiện hữu và xung đột route. **Chốt `CHAPTER → WORKER → MAIN_PACKAGE_PATH → TEST_PACKAGE_PATH`** trước khi giao. Khóa ownership từng cặp package, không có hai writer cùng vùng.
3. **Giao mỗi worker đúng bốn dòng định vị**: (1) URL tab Sheet, (2) các ô nguồn chính xác của một chương, (3) **cả hai đường dẫn package main và test tuyệt đối mà worker được phép ghi**, (4) đường dẫn tuyệt đối tới `worker/STEP_6_WORKER.md`. Không đưa tên package chung chung, không yêu cầu worker tự tìm hoặc tự quyết định vùng ghi.
4. Khi worker bàn giao, Prime nghiệm thu coverage toàn chương, MVC, đúng package, tính học tập và quan sát được, validation/error handling, giới hạn input/side effects, route/class không trùng, build/tests và HTTP runtime nếu khả dụng. Nếu không đạt, trả **đúng worker của chương** sửa trong ownership độc quyền; Prime không thiết kế/viết/refactor Java nghiệp vụ thay worker.
5. Sau khi tất cả worker hoàn thành và giải phóng ownership, chỉ dọn thư mục thực sự rỗng, kiểm tra bảo toàn ngoài phạm vi, tổng hợp `COMPLETED/API_NOT_REQUIRED/BLOCKER` và các kết quả QA.
6. **Prime bắt buộc chạy đúng task module-local `:<EXACT_MODULE_GRADLE_PATH>:generateApiSwaggerDescription` sau khi API code/QA của TOÀN tab đã xong**, để quét Controller và sinh/cập nhật `src/main/resources/swagger/<language>/api-cases.yml` cho các language đã khai báo. Task hiện cũng đồng bộ **khung metadata kỹ thuật** ở bốn Swagger YAML hiện hữu; phải bảo toàn trường do người viết đã điền. Prime nghiệm thu số controller/method, các key, `usage`, skeleton `case_001` (title/description để trống, input.body/status/response null), cả `vi` và `en`, đồng thời xác minh chạy lại không làm mất case đã điền. **Không tự viết thêm use case, ví dụ request/response, expected status hay description**. Nếu task không sinh được skeleton, báo blocker, không tuyên bố STEP 6 hoàn tất.
7. **BẮT BUỘC cập nhật Current STEP sau khi STEP 6 thực sự hoàn tất cho TOÀN tab.** Completion gate: mọi Menu Cấp 1 thuộc phạm vi tab đều đã được Prime xác minh `COMPLETED_QA` hoặc `API_NOT_REQUIRED` hợp lệ; không còn `PENDING`/`PENDING_QA`/`BLOCKER` hay writer đang giữ ownership; QA tích hợp module (build/test/runtime khi khả dụng) đạt; task `generateApiSwaggerDescription` chạy thành công và Swagger skeleton cả `vi`/`en` được kiểm tra đúng, không làm mất dữ liệu đã có. Khi tất cả đều PASS, **chính Prime** lấy URL/gid từ `module-generate-info.txt`, xác minh tab và `CURRENT_STEP = Current STEP`, đọc giá trị/công thức hiện có của `CURRENT_STEP.VALUE` rồi ghi chính xác **`CURRENT_STEP.VALUE = "6"** theo `rules/PRIME_RULES.md §2`. **Đọc lại `CURRENT_STEP.VALUE` và xác nhận nguyên văn `STEP 6`** trước khi báo `COMPLETED`; ghi `TAB_GID`, `STEP_STATUS_UPDATE=PASS/FAIL`, `CURRENT_STEP.VALUE_READBACK` trong báo cáo. Nếu CURRENT_STEP.VALUE chứa công thức, nhãn/mapping sai, trạng thái lịch sử từ cách đánh số STEP cũ chưa được đối chiếu hoặc có nguy cơ ghi lùi trạng thái, báo `BLOCKER: CURRENT STEP UPDATE` và không tự ghi đoán. Nếu bất kỳ completion gate nào chưa đạt, **giữ nguyên CURRENT_STEP.VALUE** và không tuyên bố STEP 6 hoàn tất.

**Mẫu giao việc Prime → một worker — CHỈ bốn dòng:**

```text
<SHEET_URL_CUA_DUNG_TAB>
A1, B1, <PARENT_CELL_A>, <PARENT_CELL_B>, <CHILD_B_RANGE>, <KNOWLEDGE_C_TO_N_RANGE>
<ABSOLUTE_MAIN_CHAPTER_PACKAGE_PATH> ; <ABSOLUTE_TEST_CHAPTER_PACKAGE_PATH>
/java-learning/module-generate-agent/worker/STEP_6_WORKER.md
```

Prime thay placeholder bằng đường dẫn thật trong module, đã preflight và khóa ownership. Giao worker **chỉ một Menu Cấp 1**; khi hàng con không liên tiếp phải ghi đủ các vùng thực tế. Worker tự đọc toàn văn file worker-only.

**Không thực hiện trong STEP 6 (ngoại trừ Prime chạy generator chỉ để tạo khung kỹ thuật và `api-cases.yml` như bước 6):**

- Không **tự biên soạn/điền** `controller-description.yml`, `api-descriptions.yml`, `api-execution.yml`, `api-params.yml`, Swagger/OpenAPI description, summary, execution, parameter descriptions, request/response examples, use-case examples hoặc expected results. **Chỉ task `generateApiSwaggerDescription` được phép tạo/cập nhật skeleton kỹ thuật**; không dùng generator/merge/import để điền nội dung. Việc tạo **nội dung thật** cho `api-cases.yml` và Swagger descriptions thuộc STEP mới do người dùng yêu cầu sau; cả Prime lẫn worker STEP 6 đều không thực hiện.
- Không mapping API ↔ Knowledge ↔ Quiz ↔ Interview (`readmeRelated`, `apiRelated`, file/anchor/controller/methodSignature hoặc quan hệ chéo). Mapping thuộc **STEP riêng khác**, không gộp vào STEP Swagger/use case nếu người dùng chưa quyết định.
- Không viết, chỉnh sửa hoặc đồng bộ **nội dung Knowledge** trên Google Sheets trong STEP 6; nguồn Knowledge chỉ đọc, không chạy ByFiles/ByLocation import. **Ngoại lệ duy nhất: Prime ghi `CURRENT_STEP.VALUE = "6" sau khi toàn bộ completion gate ở bước 7 đã PASS và readback xác nhận**; worker không được ghi CURRENT_STEP.VALUE.

Việc Spring/OpenAPI tự suy ra **cấu trúc kỹ thuật từ annotation/DTO để kiểm thử runtime** không phải công việc AI điền Swagger. Không chủ động tạo/sửa tài liệu Swagger do đó ở STEP 6; giữ nguyên metadata đã có nếu kiểm thử.

Prime tuân thủ hạn mức worker và tái sử dụng worker đang `SLEEPING` theo rules chung. Không có concurrent writer; mọi thay đổi ngoài vùng package được giao phải báo blocker. **Không thực thi STEP Swagger examples/docs hoặc STEP mapping trước yêu cầu riêng của người dùng.**
