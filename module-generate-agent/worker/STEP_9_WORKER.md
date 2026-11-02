# STEP 9 WORKER — Soạn Swagger VI, đúng một groupID, chỉ ghi staging

## 0. Ownership / nhiệm vụ

Đọc toàn bộ `../STEP_9.md` và rules nền tảng được Prime chỉ định trước khi làm. Mỗi assignment là **`UNIT=G<id>` duy nhất**, xử lý **tất cả HTTP method** thuộc `Group<id>Controller`, không tự tách theo case/method hoặc nhận thêm group. Prime **bắt buộc cung cấp `SERVICE_NAME`** (ví dụ OOP: `SERVICE_NAME=OBJECT_ORIENTED_PROGRAMMING`) trong handoff. Đây là tên/chủ đề học tập chung của module, **không phải tên Java Service**. Không coi Code Explorer (STEP 8) hay refactor pilot cũ là STEP 9.

- **READ-ONLY (đủ để Worker soạn nội dung):** `content/casestudy/vi/casestudy.yml` (chỉ group được giao), `src/main/resources/swagger/vi/{api-descriptions,controller-description,api-execution,api-cases}.yml` (chỉ thông tin group được giao), và `SERVICE_NAME` do Prime cấp. **Worker không phải đọc Controller, Service, helper hoặc từng file Java.** Prime chịu trách nhiệm xác thực source Java đã chuyển đúng method, mapping và input trước khi giao Worker.
- **WRITE ONLY:** `<MODULE_ROOT>/src/main/resources/swagger/temp/group<id>/api-descriptions.yml`, `api-execution.yml`, `api-cases.yml`, `report.json`.
- Cấm sửa Swagger chính thức, `swagger/en`, file của group khác, Java, Sheet, Grade/Gradle, tests, FE, importer, master, Git; không commit/push/deploy hoặc gọi generator. Không tự dọn `temp`, Prime sở hữu cleanup.
- Nếu temp group đã có nội dung của lượt trước hoặc của worker khác, **không ghi đè**: báo `BLOCKER: STEP9_TEMP_OWNERSHIP` cho Prime giải quyết.

## 1. Đọc nguồn và kiểm kê method

1. Xác minh `MODULE_ROOT`, `UNIT=G<id>`, `SERVICE_NAME`, `Group<id>Controller` trong Swagger và nhóm Case Study VI tương ứng. Dùng `api-descriptions.yml` để lấy danh sách **method signature**, HTTP mapping, summary, HTML description, Knowledge links; dùng `controller-description.yml` để hiểu tên/chủ đề cụ thể của group.
2. Đọc `situation`, `resolve`, `knowledge` và phần giải thích `code` trong `casestudy.yml` của group. Từ **ba nguồn nội dung chính: API description + Controller description + Case Study**, diễn giải mục đích học tập và kết luận cho từng method trong bối cảnh `SERVICE_NAME`. Không biến tài liệu thành phân tích từng dòng Java, không bắt Worker truy vết file Java. Không giả định đã thu được log/metric hay đã chạy công cụ bên ngoài.
3. Đối chiếu chữ ký method trong `api-descriptions.yml` và `api-execution.yml`; tập method đủ điều kiện `api-cases.yml`/input binding **do Prime kiểm kê trước bằng code**, cung cấp hoặc xác nhận qua metadata Swagger VI. **Kiểm kê mọi `params[]` của tất cả method trong group**: `name` là định danh do generator cung cấp; Worker chịu trách nhiệm viết `summary` và `description` của từng param khi các field này còn trống/placeholder. Không tự đoán parameter hay route từ mô tả tình huống. Nếu thiếu/mâu thuẫn metadata cần thiết, báo Prime `REVIEW: STEP9_METADATA_GAP`, không mở rộng quyền sang Java và không bịa kết quả.

## 2. File staging `api-execution.yml`

Với **từng method**, viết `execution` bằng HTML theo **4 phần, đúng thứ tự**:

1. `1. Mục đích của method này với chủ đề` — trả lời **“Sau khi xem/chạy method này, người học hiểu được điều gì về chủ đề ${SERVICE_NAME}?”** Nói thẳng kiến thức/vấn đề OOP cụ thể người học sẽ nhận ra, tại sao ví dụ có ích cho việc học; dựa vào `groupName`, `summary/description` và Case Study. **Không** viết theo kiểu liệt kê các class, mô tả luồng xử lý nội bộ hay giải thích chi tiết test assertion.
2. `2. Các chương trình ngoài luồng (nếu có)` — dùng tool ngoài nào, để làm gì, cần enable gì; **không có thì bỏ nguyên mục 2**.
3. `3. Hướng dẫn quan sát kết quả` — thực thi và quan sát ở đâu, thao tác, tín hiệu cần tìm, ràng buộc môi trường.
4. `4. Kết luận` — chốt rõ **“Như vậy, method này đã minh chứng cho vấn đề/nguyên lý ... của chủ đề ${SERVICE_NAME}: ...”**. Nêu **bài học đã được minh họa qua tình huống**, mối quan hệ giữa vấn đề và cách giải quyết, thay vì chỉ kết luận `PASS`/assertion không ném lỗi hoặc liệt kê những điều ví dụ *không* chứng minh. Chỉ thêm giới hạn nếu thật sự thiết yếu để tránh một kết luận sai; tuyệt đối không thổi phồng thành bằng chứng về toàn bộ hệ thống thực tế.

Văn bản là HTML fragment (`<h3>`, `<p>`, `<ul>`, `<ol>`, `<code>`...), không Markdown hoặc script. **Ưu tiên giá trị kiến thức người học nhận được**, không liệt kê chi tiết tên class hay thuật lại source. Không chép nguyên `summary/description`, không cam kết JFR/Grafana/Jaeger/DB đã chạy, không phát minh số liệu. Hướng dẫn chung cho method, **không liệt kê mọi case cụ thể**.

```yaml
Group1Controller:
  executeCase1():
    execution: |
      <h3>1. Mục đích của method này với chủ đề</h3>
      <p>Trong chủ đề <strong>OBJECT_ORIENTED_PROGRAMMING</strong>, method này
      giúp người học hiểu vì sao tách trách nhiệm trong OOP cho phép thay đổi
      chính sách tính phí mà không làm ảnh hưởng việc đặt lịch và thông báo.</p>
      <h3>3. Hướng dẫn quan sát kết quả</h3>
      <p>Thực thi API và quan sát thông báo xác nhận các hành vi nghiệp vụ
      trong tình huống vẫn được bảo toàn khi thay đổi cách tính phí.</p>
      <h3>4. Kết luận</h3>
      <p>Như vậy, method này đã minh chứng vai trò của
      <strong>phân tách trách nhiệm trong OOP</strong>: thay đổi một chính sách
      nghiệp vụ có thể được cô lập mà không kéo theo thay đổi ở các trách nhiệm khác.</p>
```

Đây là ví dụ **method không cần mục 2**, phải giữ numbering 1/3/4, không đánh số lại. File staging chỉ chứa các `execution` của đúng một Controller, không include `usage` hoặc method group khác.

## 3. File staging `api-descriptions.yml` — mô tả parameter của group

Với **mọi method có `params[]` không rỗng**, Worker phải điền `summary` và `description` **tiếng Việt, cụ thể cho từng tham số trong đúng method**. `summary` là tên/ngữ nghĩa ngắn, rõ ràng; `description` giải thích vai trò, ý nghĩa các giá trị/điều kiện/giới hạn **chỉ khi tài liệu API description, Controller description, Case Study có căn cứ**. Cùng tên `mode` ở hai method có thể có nội dung khác nhau. Không chép placeholder, không viết nội dung chung chung kiểu “tham số đầu vào”, không bịa kiểu dữ liệu, giá trị hợp lệ, hành vi hoặc default.

- **Chỉ sửa nội dung hai trường `params[].summary` và `params[].description`**. Giữ nguyên **`params[].name`, số lượng và thứ tự params** do Prime/generator xác nhận. Không chỉnh `summary`/`description` cấp method, `readmeRelated`, `usage`, HTTP mapping hay metadata khác.
- Nếu field đã có nội dung thật (không phải mặc định như `Chưa có tiêu đề`, `Chưa có mô tả chi tiết`), **giữ nguyên field ấy**; chỉ bổ sung field còn placeholder. Mô tả đang có nhưng nghi sai → ghi `REVIEW`, **không tự overwrite**.
- Nếu thông tin thiếu để mô tả chính xác param → ghi `REVIEW: STEP9_PARAM_METADATA_GAP` với `Group#method#param`, không tự bịa. Worker **không cần đọc Java**; Prime xử lý nguồn kỹ thuật trước khi giao.
- Fragment chỉ có Controller của group và **những method có params**, mỗi method chỉ có key `params` với các object `{name, summary, description}`. Không có param trong group → file staging là `{}` (không bịa method/params).

```yaml
Group1Controller:
  executeCase9(String):
    params:
      - name: mode
        summary: "Chế độ tạo workload để quan sát hiệu năng"
        description: "Chọn cpu để minh họa CPU workload hoặc allocation để minh họa cấp phát đối tượng; quan sát theo hướng dẫn của Case Study."
```

Đây là **ví dụ cấu trúc**, không phải nguồn bảo đảm mọi module đều hỗ trợ các giá trị đó. Viết staging độc lập, **không sửa trực tiếp `swagger/vi/api-descriptions.yml`**. Prime chỉ merge hai field nội dung theo từng `name` đã xác minh.

## 4. File staging `api-cases.yml`

**Chỉ method có input parameter thật của Controller.** Method `executeCase1()` không có tham số → **không có entry** dù Service của nó tạo log/metric/DB side effect. Nếu group không có method đủ điều kiện, ghi file chỉ chứa `{}` (YAML mapping rỗng), report ghi `caseMethodCount=0`; vẫn phải viết `api-execution.yml` đủ method.

Với method hợp lệ, tạo `cases` theo các giá trị/nhánh input có ý nghĩa dựa trên API description, metadata input và Case Study đã được Prime xác minh; không ép số lượng tối thiểu >1. Mỗi case có:

- `id` ổn định duy nhất, `title`, `description`, `detailDescription` (HTML giải thích riêng scenario).
- `input.pathParams`, `queryParams`, `headers`, `body` đúng metadata Swagger đã được Prime đối chiếu với Controller; trường vắng là `{}` hoặc `null` thích hợp.
- `status`, `response` giữ `null` nếu không chứng minh được HTTP response mong đợi; không đồng nhất HTTP success với thành công nghiệp vụ.
- `observations`: list mỗi phần tử có **`type`**, **`source`**, **`program`**, **`expect: {keyword, results, conclusion}`**. `keyword` là string tìm dấu hiệu thực (log text, event identifier, metric name, data field...), `results` và `conclusion` là **HTML**, không Markdown. `results` chỉ mô tả bằng chứng kỳ vọng có căn cứ; `conclusion` nói rõ bằng chứng xác nhận được gì và **không** xác nhận được gì.

```yaml
Group1Controller:
  executeCase9(String):
    cases:
      - id: case_001
        title: "CPU workload"
        description: "Chạy mode=cpu."
        detailDescription: "<p>So sánh tín hiệu thực tế khi chạy nhánh CPU.</p>"
        input:
          pathParams: {}
          queryParams: { mode: cpu }
          headers: {}
          body: null
        status: null
        response: null
        observations:
          - type: LOG
            source: "Application stdout"
            program: "Terminal"
            expect:
              keyword: "workload completed"
              results: "<p>Tìm dòng xác nhận workload đã hoàn thành.</p>"
              conclusion: "<p>Hoàn thành workload không chứng minh nguyên nhân latency.</p>"
```

Mỗi case có thể có **nhiều** observations: HTTP, LOG, JFR, METRIC, TRACE, DATABASE, FILE, MESSAGE_QUEUE... Không bó hẹp vào `response`. `source` là nơi sinh/giữ tín hiệu, `program` là công cụ để xem. Nếu không thể xác minh một tín hiệu, **đừng bịa `keyword`, `program` hay kết quả**; báo `REVIEW` trong `report.json`. Không tự chạy side effect nguy hiểm để tạo “evidence”.

Staging chỉ chứa `cases` theo đúng key method; **không đưa `methodName/httpMethods/mappingPaths/usage`** vào fragment vì Prime bảo toàn metadata đó trong YAML chính.

## 5. Báo cáo `report.json` / QA Worker

Ghi `report.json` UTF-8, có ít nhất:

```json
{
  "unit": "G1",
  "groupID": 1,
  "controller": "Group1Controller",
  "language": "vi",
  "methodsFromController": ["executeCase1()", "executeCase9(String)"],
  "methodsWithInput": ["executeCase9(String)"],
  "executionMethodCount": 2,
  "paramMethodCount": 1,
  "paramCount": 1,
  "paramFieldsFilled": 2,
  "caseMethodCount": 1,
  "caseCount": 1,
  "sourceFilesSha256": {},
  "stagingFilesSha256": {},
  "review": [],
  "blockers": [],
  "status": "PASS"
}
```

Các danh sách/số trong ví dụ **chỉ minh họa format**; phải thay bằng inventory của group thật. `paramFieldsFilled` đếm `summary`/`description` thực sự bổ sung so với nguồn (tối đa `2 × paramCount`), không tính field đã authored được giữ nguyên. `sourceFilesSha256` ghi path+SHA256 **các tài liệu Worker thực sự đọc** (ít nhất Case Study VI, `api-descriptions.yml`, `controller-description.yml`, `api-execution.yml` và `api-cases.yml` VI); **không bắt buộc hash Controller/Service/Java helper**. Prime tự kiểm tra Java/source fidelity và metadata trước khi giao. `stagingFilesSha256` ghi checksum **cả ba YAML staging**. Tính SHA sau ghi, đọc lại để đối chiếu.

Trước bàn giao: parse **ba YAML staging** (không duplicate key), đếm chính xác mọi method; `api-descriptions` chỉ có các method có params, `params[].name` đủ/đúng thứ tự và `summary/description` đều hợp lệ hoặc đã được giữ nguyên nội dung authored, không còn placeholder chưa xử lý; `execution` đủ 4/3 heading theo chính sách, không placeholder; `cases` đúng tập có param, IDs không trùng, input thực, observations hợp lệ với `expect.keyword/results/conclusion` và HTML hợp lệ, không invent. Nếu `REVIEW/BLOCKER` chưa giải quyết được, ghi đúng trạng thái và nguyên nhân, **không tự đánh PASS**.

Report trả Prime: `UNIT`, đường dẫn temp, checksum nguồn+output, `METHODS_ALL`, `METHODS_WITH_INPUT`, `EXECUTIONS_DONE`, `PARAM_METHODS`, `PARAM_COUNT`, `PARAM_FIELDS_FILLED`, `CASE_METHODS_DONE`, `CASE_COUNT`, `REVIEW/BLOCKER`, `STATUS` và **RELEASE_OWNERSHIP=YES**. Không tự merge, xóa temp, cập nhật `CURRENT_STEP`, commit/push/deploy.
