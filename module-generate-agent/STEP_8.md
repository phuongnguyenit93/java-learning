# STEP 8 — Sheet Case Study → Java GET demo trong module (một lượt worker/group)

## 0. Phạm vi và luồng thực hiện

STEP 8 mới chạy sau STEP 7 mới. Chỉ có **MỘT lượt worker**: mỗi worker sở hữu **trọn một groupID** và chuyển code mọi Case Study của group đó **trực tiếp từ Google Sheet thành Java source/test final trong đúng module**. Không chia một case/worker; không có worker Phase 1/Phase 2, không staging Java rồi promote, không có lượt refactor thứ hai. Worker được phép cấu trúc lại code khi chuyển đổi để ví dụ dễ đọc và chạy được.

**Prime** chịu trách nhiệm preflight, điều phối, xác nhận output và QA **từng case + toàn module** sau khi workers hoàn tất. Nếu QA fail, trả đúng group worker sửa các Java files do group đó sở hữu, không tổ chức thêm phase. STEP 8 không viết ngược Sheet, Case Study YAML/Markdown, không chạy importer STEP 6/7. **Đồng bộ module → file/Sheet thuộc STEP 9 sau này**, chưa làm ở STEP 8. Không hồi sinh STEP 8 Practice/Interview lịch sử, MVC skeleton theo chương, .gitkeep hoặc isImportant; không tự commit/push/deploy.

Mục đích API là **bấm GET và thấy Java thực thi để học**, không phải API nghiệp vụ/CRUD bắt nhập request body hoặc tham số.

## 1. Sheet là nguồn code trực tiếp

1. Prime xác minh URL Google Sheet, gid/tab và A1,B1,I3 để ánh xạ duy nhất sang MODULE_ROOT/Gradle module thật. Sheet **chỉ đọc** trong STEP 8, không sửa ô nào.
2. **Case Study nền:** ghép chuỗi YAML trong S20:U24 (5 hàng × 3 cột), đọc ô **trái → phải, trên → dưới, không separator**, parse caseStudy[] với groupID, groupName, cases[] (situation, resolve, knowledge, readmeRelated, các metadata).
3. **Code Phase 4 cho groupID = g:** vùng **S(27+3×(g−1)):U(29+3×(g−1))** (3 hàng × 3 cột), ghép ô cùng cách. Parse groupID và codes[] chứa caseIndex (1-based) cùng code Markdown. **Ghép theo groupID và caseIndex**, không lấy caseIndex từ số hàng Sheet; không trộn nhóm. Không dùng bản YAML module thay cho Sheet.
4. Kiểm mọi group/index duy nhất, đủ số cases, không duplicate/gap/out-of-range; đọc đủ Java fences BEFORE/AFTER/TEST và giải thích code sai đúng. Với case không có runnable Java, ghi NOT_APPLICABLE có chứng cứ, không tạo endpoint giả. Nếu Sheet thiếu/thay đổi/không thể parse → BLOCKER: STEP8_SHEET_SOURCE, không tự chạy importer hoặc lấy cache thay.
5. Prime cấp SOURCE_SHA256 cho mỗi group theo công thức **SHA256(UTF8(baseYamlJoined + "\n---STEP8-GROUP-PHASE4---\n" + groupCodeYamlJoined))**, trong đó mỗi joined value lấy từ **nguyên văn các cell** đã đọc, không re-render YAML trước hashing. Worker tính lại trước ghi và đọc lại source trước bàn giao. Đồng thời lưu SHA-256 nguyên chuỗi code của mỗi case. Prime giữ ledger mọi group/case, owner theo group, source fingerprint, file/route/bean/QA.
6. MODULE_ROOT/content/casestudy/vi/casestudy.yml có thể đọc **chỉ để đối chiếu/provenance**, không phải nguồn thay thế hoặc nơi ghi trong STEP 8. Nếu Sheet không đồng nhất hoặc chưa đủ nguồn, báo blocker và chứng cứ, không đoán.

## 2. Java final bắt buộc cho mỗi case

Với groupID g và caseIndex c, worker ghi **trực tiếp vào source Java final**:

~~~text
MODULE_ROOT/src/main/java/JAVA_BASE_PACKAGE_PATH/casestudy/g<g>/c<c>/CaseController.java
MODULE_ROOT/src/main/java/JAVA_BASE_PACKAGE_PATH/casestudy/g<g>/c<c>/CaseService.java
MODULE_ROOT/src/test/java/JAVA_BASE_PACKAGE_PATH/casestudy/g<g>/c<c>/CaseServiceTest.java
MODULE_ROOT/src/test/java/JAVA_BASE_PACKAGE_PATH/casestudy/g<g>/c<c>/CaseControllerTest.java (nếu HTTP tests phù hợp)
~~~

- Java package: JAVA_BASE_PACKAGE.casestudy.g<g>.c<c>. Mỗi case **đúng class chính tên CaseController và CaseService**, không Case1Controller/Case1Service. Khác package g/c cho phép tên class lặp lại.
- Bean Controller: @RestController("caseStudyG<g>C<c>Controller"), ví dụ **caseStudyG1C1Controller**. Bean Service riêng, khuyến nghị @Service("caseStudyG<g>C<c>Service").
- Endpoint **chính xác**: **GET /case-studies/group/<g>/case/<c>/execute**, ví dụ **GET /case-studies/group/1/case/1/execute**. Cấm POST/PUT, không thêm /api/v1/java, không đổi group thành groups. Phương thức Controller execute() **không có đối số client**, không RequestBody, RequestParam, PathVariable hoặc form/input header nghiệp vụ.
- **Service tự khai báo/tạo dữ liệu đầu vào mẫu mỗi lần GET** rồi thực thi logic Java của tình huống, trả response có kết quả **tính được thật** (steps, before/after, số lượng, so sánh kịch bản, state giả lập, explanation), để người học bấm là thấy. Không chỉ log, không trả sẵn PASS/OK hoặc response tĩnh thay cho logic.
- Nếu ví dụ gồm main()/assertion, chuyển thành method và test nhưng giữ kiến thức/behavior và phần phân biệt phản ví dụ/giải pháp. Có thể tách DTO, helper, model **trong package case** khi cần; không tạo abstraction rườm rà chỉ để tăng file.
- GET **an toàn để gọi lặp**: dữ liệu mỗi request được cô lập, không ghi DB thật, gửi mail/SMS, gọi network thật, shared mutable state hay chạy deadlock/sleep vô hạn. Với ví dụ mutation/concurrency/transaction, dùng in-memory/fake dependencies mới mỗi request và timeout hữu hạn; test đảm bảo repeatability.
- SERVLET dùng Spring MVC; REACTIVE dùng WebFlux tương ứng. LIBRARY/PLATFORM thiếu HTTP runtime → BLOCKER: STEP8_MODULE_RUNTIME_POLICY; worker không đổi module type/Gradle để ép chạy Controller. Dùng JDK tương thích Gradle/module (mặc định Java 21 khi có).

## 3. Prime preflight/ownership

1. Đọc GENERAL_AGENT_RULES.md, **toàn bộ rules/**, file STEP_8.md này và **một file worker-only**: worker/STEP_8_WORKER.md. Worker pool theo PRIME_RULES.md: Mode A tối đa 3, Mode B đúng N user cấp (8 worker có thể xử lý tối đa 8 group song song), reuse SLEEPING, spawn tuần tự. **Một group chỉ một writer**; worker xong mới nhận group khác.
2. Kiểm master.json (MODULE_TYPE, JAVA_BASE_PACKAGE, CURRENT_STEP), Gradle/JDK/runtime, existing Java/test, Controller/Service/Bean/routes, **Git worktree và index**. Snapshot trước khi chạy; không overwrite source nghiệp vụ có sẵn, không stage/restore/clean source khác. Staged deletions, writers đồng thời hay task Gradle làm biến mất source ngoài ownership → BLOCKER: STEP8_UNSAFE_WORKTREE; không tự phục hồi/chỉnh index của người khác. Tách lỗi baseline build với lỗi Java STEP 8.
3. Kiểm các package final đã tồn tại/bean/route collision. Nếu còn Java pilot cũ trong casestudy/temp/gX/cY hoặc API tương đương đang scan, **không tự xóa, ghi đè hoặc cho Spring scan trùng**. Cần xác minh cách xử lý theo ownership và bảo toàn source; nếu không làm được → BLOCKER: STEP8_LEGACY_TEMP_COLLISION.
4. Prime cấp RUN_ID, ledger/snapshot/QA dưới MODULE_ROOT/build/step8/RUN_ID/ (Git ignored); worker chỉ WRITE đúng hai group final root: src/main/java/.../casestudy/g<g>/ và src/test/java/.../casestudy/g<g>/. Group chia c1/, c2/, ... riêng, không tạo thư mục temp/proposal hay .gitkeep.
5. **Không chạy Gradle compile/test/startup khi bất kỳ worker còn đang ghi** vào main sourceSet. Prime xây barrier sau khi mọi group worker release. Không tự chạy Gradle clean xóa backup.

## 4. Handoff chính xác bốn dòng — một group, một lượt

~~~text
<VERIFIED_SHEET_TAB_URL>
READ=A1,B1,I3,S20:U24,<EXACT_GROUP_PHASE4_RANGE>;UNIT=G<groupID>;RUN=<RUN_ID>;SOURCE_SHA256=<EXACT_GROUP_SOURCE_SHA256>
WRITE=<EXACT_MAIN_GROUP_FINAL_DIR>,<EXACT_TEST_GROUP_FINAL_DIR>
/java-learning/module-generate-agent/worker/STEP_8_WORKER.md
~~~

Prime thay toàn bộ placeholder bằng URL, range, path và source hash **đã xác minh**; không gửi code/đáp án/yêu cầu refactor trong chat handoff. UNIT **chỉ Gx**, không Gx/Cy. Worker không ghi ra ngoài WRITE hoặc đọc/ghi Sheet ngoài scope được cấp. Khi cần sửa theo QA, giao lại **cùng group và quyền sở hữu**, không đặt tên Phase 2.

## 5. Worker một lượt Sheet → Java module

1. Đọc trực tiếp S20:U24 và **block Phase 4 của group** từ Sheet, đối chiếu nguồn/hash, đủ tình huống, giải pháp, kiến thức và code cho mọi case. Không chỉ lấy code fence đầu tiên hoặc cố tạo API giả cho case không code.
2. Tạo mỗi case runnable một **CaseController.java, CaseService.java**, tests và helper cần thiết **ngay trong package final gX/cY**, theo class/bean/GET/no-input ở §2. Data demo tự có trong Service; kết quả GET bộc lộ cách chạy. Refactor tối thiểu **trong cùng lượt chuyển đổi** nếu giúp code runnable/rõ ràng, không có worker phase thứ hai.
3. Tạo Service assertions (behavior, before/after, repeatability, edge) và Controller/HTTP GET tests (status 200 + response thực, không body/query) phù hợp với runtime. Nếu module còn worker đang ghi, defer build cho Prime; không báo compile PASS khi chưa chạy.
4. Không ghi Sheet, YAML module, Interview/Quiz/Knowledge/README, Gradle, Spring Application, master.json, Java group khác hoặc Git. Không import/export ngược file. Không tự cập nhật CURRENT_STEP.
5. Tạo **một** step8-group-manifest.json trong root test group; gồm runId, groupID, sourceSha256, caseCount, từng caseIndex/sourceCodeSha256, status GENERATED/NOT_APPLICABLE/BLOCKER, Java/test files + SHA-256, package/classes/beans/GET route, test evidence/build status, limitations. **Exact readback** toàn file và nguồn Sheet trước báo cáo. Report rõ case nào không code, case nào blocker. Worker release ownership rồi về SLEEPING.

## 6. Prime QA sau worker — không có phase khác

1. Đợi **toàn bộ worker** dừng ghi. Verify source hashes vs Sheet, manifest/file hashes, group/case coverage và class/bean/route uniqueness. Mọi case runnable có Java thực, tests và GET không input; case NOT_APPLICABLE có lý do. Không tin vào report thay test.
2. QA module: compileJava/test, Spring context/startup, HTTP smoke **GET đúng từng URL không body/query**, kiểm response có giá trị thực và gọi lặp an toàn, regression không phá Java cũ. Nếu lỗi, giao đúng **worker group** sửa **Java final**, không tạo Phase 2, không thay Sheet/YAML. Nếu build khiến tracked source/index thay đổi ngoài phạm vi thì dừng, báo blocker, không tự git restore/clean.
3. Output đã nằm **thẳng trong production package casestudy/gX/cY**; Prime **không merge/promote temp**, không tạo thêm Controller/Service chung toàn group. Giữ provenance/QA mapping cho STEP 9 nhưng **không thực hiện module→file/Sheet sync** trong STEP 8. Chỉ dọn staging/backup do chính RUN sở hữu khi QA PASS; không xóa temp pilot chưa rõ owner.
4. Completion chỉ khi **tất cả group/case của module** đã PASS/NOT_APPLICABLE có chứng cứ và full tests/HTTP/regression đạt. Pilot 1 group **không** nâng CURRENT_STEP. Chỉ Prime xét CURRENT_STEP.VALUE="8" theo rules/PRIME_RULES.md §2, không ghi lùi và không coi giá trị "8" lịch sử là bằng chứng STEP 8 mới hoàn tất. Không commit/push/deploy nếu chưa có lệnh riêng.

**Prime report:** RUN_ID, module/gid, worker N/assignments, group/case count và statuses, source range/hash, Java classes/beans/GET routes, GET_NO_INPUT/DEMO_RESULTS, COMPILE/TEST/STARTUP/HTTP_SMOKE/REGRESSION, SHEET_AND_YAML_UNCHANGED, WORKTREE_SAFETY, CURRENT_STEP_BEFORE/AFTER/READBACK, BLOCKERS. Không claim PASS nếu chưa chạy thực.
