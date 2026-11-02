# STEP 9 — AI biên soạn Swagger API Execution + API Cases + Parameter descriptions (VI, mỗi Worker một groupID)

## 0. Hợp đồng hiện hành

**STEP 9 biên soạn nội dung trong ba file Swagger tiếng Việt**:

- `<MODULE_ROOT>/src/main/resources/swagger/vi/api-execution.yml`: AI viết **nội dung HTML của `execution` cho TẤT CẢ HTTP method** trong nhóm.
- `<MODULE_ROOT>/src/main/resources/swagger/vi/api-cases.yml`: AI viết các kịch bản `cases[]` **CHỈ cho HTTP method có ít nhất một tham số đầu vào khai báo trong Java Controller**. Method không có tham số **không được có entry** trong `api-cases.yml`; nhưng vẫn phải có `execution`.
- `<MODULE_ROOT>/src/main/resources/swagger/vi/api-descriptions.yml`: AI điền **chỉ `params[].summary` và `params[].description`** cho tất cả method có params trong group; `params[].name` và method metadata là dữ liệu code-owned, không đổi. Không biên soạn lại `summary`/`description` cấp method.

**Chỉ `vi` (tiếng Việt), không tạo, copy, sửa, dịch hoặc QA lại nội dung EN trong STEP này.** Không sửa `controller-description.yml`, Java/Gradle/Case Study/Knowledge/Google Sheet. `summary = situation`, `description = resolve + knowledge` (HTML), `readmeRelated.anchorList` và Controller `description = groupName` thuộc task code-only trước đó, **không phải công việc AI STEP 9**. Controller **không có `readmeRelated`**.

**Một worker = đúng một `groupID` trọn vẹn**; không chia theo case/method, không giao cùng group cho hai worker đồng thời. Worker **chỉ ghi tạm** dưới:

```text
<MODULE_ROOT>/src/main/resources/swagger/temp/group<id>/
├── api-descriptions.yml  # chỉ fragment params[].summary/description của đúng group; {} nếu không có
├── api-execution.yml     # fragment của đúng Group<id>Controller
├── api-cases.yml         # fragment của đúng group; {} nếu không có method có input
└── report.json           # kiểm kê method, nguồn, kết quả, REVIEW/BLOCKER và SHA
```

Ví dụ `group1`, `group13` (không phải `g1`, `Group1` hoặc thư mục dùng chung). Temp chỉ là **staging / báo cáo trung gian**; không phải tài nguyên publish. **Prime độc quyền** QA, merge về ba YAML chính thức trong `swagger/vi/`, exact readback, **xóa toàn bộ `swagger/temp/group<id>` và `swagger/temp` khi rỗng** sau khi toàn bộ nhóm đã merge PASS. Nếu có lỗi phải giữ bằng chứng để xử lý, không xóa trước khi QA/merge thành công.

**Không thực thi worker/merge/đổi `CURRENT_STEP` chỉ vì đã viết tài liệu STEP này.** Code Explorer vẫn là completion gate của STEP 8; pilot STEP 9 cũ (Code Explorer, refactor/reverse sync) không áp dụng.

## 1. Prime — preflight và phân công

1. Đọc `GENERAL_AGENT_RULES.md`, toàn bộ `rules/`, tài liệu STEP này và `worker/STEP_9_WORKER.md`. Xác minh module bằng Sheet/I3 + `module-generate-info.txt`, `master.json`, đúng `MODULE_ROOT`/worktree và `src/main/resources/swagger/vi/`; không tự đoán module. Đối chiếu bằng chứng STEP 8 Java + Code Explorer trước khi mở STEP 9, không tự coi `CURRENT_STEP="8"` lịch sử là đạt.
2. Snapshot/hash và kiểm tra ownership của **ba YAML chính thức** (`api-descriptions`, `api-execution`, `api-cases`) và toàn bộ `swagger/temp/` (kể cả dữ liệu dở của phiên khác). Nếu có chỉnh sửa đồng thời, temp group chưa rõ owner, YAML malformed hoặc nội dung do người viết đã chỉnh sửa mà chưa có chính sách giữ/merge → `BLOCKER: STEP9_UNSAFE_WORKTREE`; **không reset/clean**.
3. Lập ledger toàn module từ các **HTTP method thật** trong `Group<id>Controller.java`, `api-descriptions.yml` và `api-execution.yml`; kiểm đủ tất cả `groupID`, chữ ký method, `usage`, route và số lượng. **Prime xác minh Java → Swagger mapping, `params[].name`, thứ tự, số lượng, input binding** trước khi giao; Worker **chỉ đọc** `api-descriptions.yml`, `controller-description.yml`, `content/casestudy/vi/casestudy.yml` và Swagger metadata VI liên quan, **không cần đọc Java**. Xác định tập method có parameter từ **Java Controller signature** (không suy từ HTTP method hoặc body Service). API Cases phải có đúng tập method đủ điều kiện; mọi method có param đều cần mô tả param, kể cả khi không tạo nhiều cases.
4. Trước worker, Prime kiểm tra/tạo skeleton Swagger bằng task hiện có theo **quy trình an toàn và bảo toàn nội dung đã viết** nếu cần; không bắt Worker chạy generator và không để generator ghi đè nội dung AI. `enrichSwaggerFromCaseStudy` chỉ phục vụ mapping code-only; không được dùng để suy ra execution/observations.
5. Giao mỗi worker **đúng `UNIT=G<id>`**, `MODULE_ROOT`, **`SERVICE_NAME`** (ví dụ `OBJECT_ORIENTED_PROGRAMMING`), `READ=` Case Study VI + `api-descriptions.yml` + `controller-description.yml` + Swagger metadata VI liên quan của group, `WRITE=` đúng `swagger/temp/group<id>/` gồm ba YAML fragment + `report.json`, `WORKER_FILE=module-generate-agent/worker/STEP_9_WORKER.md`. Không dán nội dung mẫu, câu trả lời, suy luận hoặc toàn bộ hướng dẫn vào handoff. Worker tự kiểm kê trọn group, không tự đổi group.
6. Worker có thể song song ở **các group khác nhau** theo giới hạn pool của General Rules. Prime tiếp tục giao group chưa làm sau mỗi báo cáo, bảo đảm không còn group thiếu. Prime tuyệt đối không merge trong lúc worker cùng group còn ghi; chỉ nhận output sau khi worker release ownership.

## 2. Hợp đồng nội dung `api-execution.yml`

Mỗi HTTP method có một **HTML fragment trong field `execution`**, với **đúng thứ tự 4 phần** (mục 2 có thể bỏ hẳn nếu không dùng công cụ ngoài):

1. **Mục đích của method này với chủ đề**: trả lời người học xem/chạy method sẽ hiểu gì về chủ đề `SERVICE_NAME`, liên hệ với kiến thức cụ thể của Case Study; không kể lại từng class/khẳng định test PASS là mục đích.
2. **Các chương trình ngoài luồng (nếu có)**: phần mềm/hệ thống thực sự giúp thực thi hoặc quan sát (Terminal, JMC/JFR, Grafana, Jaeger, DB client, Kafka UI...). Chỉ nêu công cụ phù hợp và **điều kiện cần bật/cài đặt**, không khẳng định đã tích hợp hay đang chạy nếu Java/config chưa chứng minh. Không có → **bỏ phần 2**, không thêm filler.
3. **Hướng dẫn quan sát kết quả**: chỉ cách chạy, nơi theo dõi (log, stdout, HTTP, metric, tracing, DB, event, file, profiler...), thao tác để kiểm chứng, và các dấu hiệu cần đối chiếu. Phân biệt tín hiệu phát sinh trực tiếp từ code và tín hiệu chỉ có nếu cấu hình hệ thống giám sát.
4. **Kết luận**: “Như vậy, method này đã minh chứng vấn đề/nguyên lý ... trong chủ đề `SERVICE_NAME` là ...”; rút ra bài học, chỉ nêu giới hạn khi thiết yếu để tránh hiểu sai.

```yaml
Group1Controller:
  executeCase9(String):
    execution: |
      <h3>1. Mục đích của method này với chủ đề</h3>
      <p>Minh họa sự khác biệt giữa CPU workload và object allocation.</p>
      <h3>2. Các chương trình ngoài luồng (nếu có)</h3>
      <p>Terminal để đọc stdout; JMC khi đã bật và thu được JFR.</p>
      <h3>3. Hướng dẫn quan sát kết quả</h3>
      <p>Chạy mode=cpu hoặc mode=allocation; kiểm tra stdout. Nếu bật JFR,
      phân tích các event phù hợp với workload.</p>
      <h3>4. Kết luận</h3>
      <p>Chỉ có log hoàn thành không đủ để kết luận nguyên nhân latency.</p>
```

**Đây là mẫu hình thức, không phải bằng chứng đã chạy hoặc nội dung để chép hàng loạt.** Trong staging, Worker chỉ đặt `execution` dưới method đúng chữ ký (không thay `usage`/metadata); Prime chỉ merge field `execution` vào entry hiện hữu và bảo toàn các field khác.

## 3. Hợp đồng `api-descriptions.yml` — chỉ `params[].summary/description`

- Worker kiểm mọi method của group có `params[]` không rỗng. Với **từng param** mà `summary` hoặc `description` còn trống/placeholder, viết mô tả tiếng Việt dựa trên **API description + Controller description + Case Study**; `summary` ngắn gọn nêu ý nghĩa input, `description` giải thích mục đích/giá trị/điều kiện có căn cứ. Không dùng cùng một nội dung chung chỉ vì hai method cùng tên param.
- **Chỉ param content là AI-owned**: `name`, số lượng/thứ tự params, chữ ký method và mọi field method khác là code/metadata-owned. Đối với nội dung human-authored đã có, **giữ nguyên từng field**, không ghi đè; mô tả đáng nghi thì `REVIEW`.
- Worker viết fragment chỉ theo đúng `Group<id>Controller` và **method có param**, bên trong chỉ chứa `params: [{name, summary, description}]`. Group không có param → file `swagger/temp/group<id>/api-descriptions.yml` ghi `{}`. **Không tạo lại `api-params.yml` (kể cả legacy).**
- Nếu thông tin metadata không đủ, báo `REVIEW: STEP9_PARAM_METADATA_GAP`, không đoán hoặc đổi param để khớp narrative. Prime là người xác nhận technical input trước khi giao.

```yaml
Group1Controller:
  executeCase9(String):
    params:
      - name: mode
        summary: "Chế độ workload để quan sát hiệu năng"
        description: "Chọn cpu cho workload tính toán hoặc allocation cho workload cấp phát; xem kết quả theo hướng dẫn quan sát."
```

Đây chỉ là ví dụ cấu trúc; Prime merge theo **đúng group, method signature, param name**, chỉ hai field `summary`/`description`, không thay metadata.

## 4. Hợp đồng `api-cases.yml` + observations

- **Chỉ method có parameter đầu vào thực trong Controller** mới có entry. Method không có parameter/nhóm không có method đủ điều kiện: **không tạo case**; staging `api-cases.yml` phải là `{}` nếu group không có entry. Không tạo case chỉ để tăng coverage.
- Mỗi method đủ điều kiện có `cases: [1..N]`, được AI thiết kế theo những **nhánh input khác nhau thực sự có nghĩa**, không invent endpoint, tham số hoặc response. Input điền đúng `pathParams` / `queryParams` / `headers` / `body` từ Java binding và hành vi có thể chứng minh. `id` duy nhất trong cùng method, ổn định `case_001`, `case_002`, ...
- Giữ schema case hiện hành: `id`, `title`, `description`, `detailDescription` (HTML diễn giải riêng scenario), `input`, `status`, `response`; **thêm `observations` là list**. `status` và `response` **được phép null** khi không có bằng chứng về kết quả HTTP; không đoán status 200/202 hay JSON nếu Controller `void`.
- Mỗi `observations[]` có **`type`, `source`, `program`, `expect`**. `expect` là object gồm **`keyword` (chuỗi dùng nhận diện/tìm kiếm), `results` (HTML dấu hiệu/bằng chứng cần thấy), `conclusion` (HTML diễn giải và giới hạn kết luận)**. `source` là nguồn tín hiệu thực tế; `program` là công cụ người học dùng xem tín hiệu (có thể là Terminal, HTTP client, JMC, Grafana...). Dùng type mô tả đúng bản chất, ví dụ LOG, HTTP, METRIC, JFR, TRACE, DATABASE, FILE, MESSAGE_QUEUE; không ép các chương trình đã được cài.
- Nếu một trường quan trọng không thể xác minh, nêu **REVIEW/BLOCKER với nguồn thiếu** trong report; không tạo `results`, `conclusion`, keyword, chương trình hoặc số liệu giả. Phân biệt **kỳ vọng hợp lý từ source** với **đã chạy và đo được**.

```yaml
Group1Controller:
  executeCase9(String):
    cases:
      - id: case_001
        title: "Chạy workload CPU"
        description: "Gọi endpoint với mode=cpu để thực thi nhánh CPU."
        detailDescription: "<p>Chạy workload; đối chiếu tín hiệu thực từ stdout/JFR.</p>"
        input:
          pathParams: {}
          queryParams:
            mode: cpu
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
              results: "<p>Kiểm tra dòng stdout xác nhận workload hoàn tất.</p>"
              conclusion: "<p>Workload hoàn thành; chưa chứng minh bottleneck.</p>"
          - type: JFR
            source: "JFR recording (chỉ khi đã bật)"
            program: "Java Mission Control"
            expect:
              keyword: "jdk.ExecutionSample"
              results: "<p>Nếu đã ghi JFR đủ lâu, tìm sample CPU và stack trace.</p>"
              conclusion: "<p>So sánh bằng chứng profiling; không suy từ một sample.</p>"
```

**Ranh giới:** `api-execution` là hướng dẫn/mục đích/quan sát **tổng quát cho method**; `api-cases` là input và `expect` **cho từng scenario**. Không lặp nguyên văn phần giải thích từ `api-descriptions`, cũng không dồn `api-cases` vào `api-execution`.

## 5. Nguồn dữ liệu và quyền của Worker

Worker đọc **API description + Controller description + Case Study VI của đúng group**, thêm `api-execution.yml`/`api-cases.yml` hiện có để đối chiếu; **không yêu cầu đọc Controller/Service/helper Java**. Prime chịu trách nhiệm đối chiếu source Java, parameter metadata và input binding. Worker không suy đoán tool monitoring đã kết nối. Với tác động có rủi ro (network, DB writes, JVM exit, process-global state) **không tự chạy** chỉ để điền nội dung; báo `REVIEW`.

Chỉ được tạo/ghi/đọc lại **bốn file dưới đúng temp group sở hữu**. Không đụng `swagger/vi/` chính thức, `swagger/en/`, `master.json`, source, importer, build script, Google Sheet hay Git. File staging là **fragment theo đúng tên Controller/method**, không chép toàn module và không chứa group của worker khác. Hướng dẫn chi tiết trong `worker/STEP_9_WORKER.md`.

## 6. Prime — nhận báo cáo, QA, APPLY và cleanup

1. Thu **đúng một report hoàn tất mỗi group** và xác nhận worker release ownership. Đối chiếu ledger với staging: `api-execution.yml` đủ **mọi method active** của group; `api-descriptions.yml` đủ **mọi method có param**, `params[].name` đủ/đúng thứ tự, `summary/description` được bổ sung hoặc giữ nguyên authored; `api-cases.yml` đúng **tập method có input**, kể cả `{}` nếu không có. Kiểm `paramMethodCount`, `paramCount`, `paramFieldsFilled` trong `report.json`. Không dùng số lượng test/case cố định để suy đủ.
2. Parse **ba YAML staging** chặt (không duplicate key), xác minh đúng Controller/method signature, `params` phải là list object `{name,summary,description}`; `name` khớp đúng code/Swagger, `summary`/`description` là chuỗi tiếng Việt có ý nghĩa, không placeholder. Xác minh `case_XXX` không trùng, query/path/header/body khớp Java; `observations` và `expect.keyword/results/conclusion` đúng kiểu, `results/conclusion` HTML hợp lệ/được escape khi hiển thị; không có script hoặc HTML nguy hiểm. QA ngữ nghĩa bằng nguồn đã xác minh, không chỉ regex/parse.
3. Xác minh 4 phần execution đúng thứ tự (mục 2 optional), không lấy kiến thức sang suy đoán output. Với case, đối chiếu `type/source/program/expect` với tác động thực tế (stdout/HTTP/JFR/DB/metric...), điều kiện cần bật, đặc biệt khi HTTP trả `void`. Mọi `REVIEW/BLOCKER` phải được Prime giải quyết với đúng Worker; **không ép điền và không coi STEP PASS** khi còn điểm chưa có bằng chứng.
4. Snapshot/hash ngay trước APPLY, chặn concurrent writes, đối chiếu snapshot trước khi merge; lập diff **chỉ** ở `params[].summary/description` của `swagger/vi/api-descriptions.yml`, `execution` của `swagger/vi/api-execution.yml`, và `cases` của `swagger/vi/api-cases.yml`. **Không ghi đè nội dung authored**: nếu field param đã có mô tả thật, giữ nguyên; nếu staging khác nội dung authored, báo conflict để review, không merge last-writer-wins. Bảo toàn `params[].name`/thứ tự, `usage`, `methodName`, mapping, method-level `summary`/`description`, `readmeRelated` và mọi file ngoài ba đích.
5. APPLY có backup/rollback **cả ba file đích**, validate và **exact readback**. Rerun an toàn/không nhân đôi case; `api-cases.yml` sau merge **không chứa bất kỳ method không có parameter**, cả khi các group không có case. Kiểm toàn module VI đủ method và group; không có rewrite EN. Nếu cần thay đổi consumer schema/projection để hiển thị observations, Prime xác minh riêng, không mặc định runtime/Portal đã hỗ trợ.
6. **Chỉ khi toàn module QA + APPLY + exact readback PASS**, Prime xóa **từng `swagger/temp/group<id>/` đúng quyền sở hữu và thuộc phiên hiện hành**, rồi kiểm temp không còn file và xóa `swagger/temp/` khi rỗng. Nếu APPLY fail, phải rollback và giữ staging/report để phục hồi. Xác nhận `TEMP_CLEANUP=PASS` và không có `swagger/temp/**` lọt vào Git/package/data publish.
7. Sau mọi gate, Prime mới xét cập nhật `CURRENT_STEP.VALUE="9"` theo `rules/PRIME_RULES.md §2` (nếu trạng thái hiện hành cao hơn thì không ghi lùi; trạng thái legacy phải kiểm provenance). **Không tự commit/push/deploy** trong STEP 9 khi chưa có yêu cầu tương ứng. Ghi báo cáo tổng: module, group/method/case, source SHA, số group PASS/REVIEW/BLOCKER, exact readback, temp cleanup, `CURRENT_STEP_BEFORE/AFTER`.

**Completion gate:** `STEP9=PASS` chỉ khi tất cả group trong module được xử lý đúng ownership, toàn bộ `execution` active có nội dung AI đã QA, mọi parameter active thuộc đúng method có `summary/description` đầy đủ hoặc authored được giữ nguyên, mọi method có parameter đủ cases và observations có cơ sở, **ba YAML VI** merge/readback PASS, zero temp, không còn conflict/blocker. **Hợp đồng nội dung này không đồng nghĩa task AI hay schema consumer đã được tự động triển khai bằng code.**
