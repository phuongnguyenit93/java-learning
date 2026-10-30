# STEP 8 — WORKER: Sheet Case Study → Java GET demo trong module (WORKER ONLY)

**CHỈ MỘT LƯỢT WORKER CHO MỘT GROUP.** Đọc toàn bộ file này trước mỗi assignment. Không có worker Phase 1/Phase 2, không giao mỗi case một worker, không làm refactor ở lượt riêng. Mỗi nhiệm vụ = tất cả case của đúng một groupID; một worker sở hữu hai Java group folders duy nhất trong lượt của mình.

## 1. Handoff bốn dòng

~~~text
<VERIFIED_SHEET_TAB_URL>
READ=A1,B1,I3,S20:U24,<EXACT_GROUP_PHASE4_RANGE>;UNIT=G<groupID>;RUN=<RUN_ID>;SOURCE_SHA256=<EXACT_GROUP_SOURCE_SHA256>
WRITE=<EXACT_MAIN_GROUP_FINAL_DIR>,<EXACT_TEST_GROUP_FINAL_DIR>
/java-learning/module-generate-agent/worker/STEP_8_WORKER.md
~~~

- Prime phải cấp URL tab/gid, đúng range, groupID (UNIT=Gx), RUN_ID, SHA256 group source, hai WRITE folders module thật. Nếu thiếu/sai hoặc quyền truy cập Sheet không đủ → BLOCKER: STEP8_WORKER_HANDOFF. **Không tự chọn group hoặc thêm case ngoài group được giao.**
- A1/B1/I3 xác minh đúng tab/module. Tất cả Sheet **READ-ONLY**; worker không chỉnh ô nào. **Nguồn code chính là Sheet**, không lấy code từ casestudy.yml hoặc cache module thay thế.
- READ Case Study nền **S20:U24**: nối YAML từ các ô **trái→phải, trên→dưới, không separator**; parse caseStudy[].groupID/groupName/cases[] và situation/resolve/knowledge/readmeRelated. READ code groupID g tại **S(27+3×(g−1)):U(29+3×(g−1))**, cũng 3×3 ô nối không separator; parse groupID và codes[].caseIndex/code Markdown.
- Group SHA bắt buộc = SHA256(UTF8(baseYamlJoined + "\n---STEP8-GROUP-PHASE4---\n" + groupCodeYamlJoined)), tính từ **nguyên văn text của các ô**, không từ bản YAML đã format lại. Phải trùng SOURCE_SHA256. Lưu SHA256 chuỗi code cho **mỗi** case. Nếu source thay đổi trước/trong lúc làm, group/case/index không nhất quán hoặc thiếu Phase 4 code → BLOCKER: STEP8_SHEET_SOURCE_DRIFT, không ghi code dựa trên nguồn không xác minh.
- Một case có nhiều Java fences vẫn là **một case**. Đọc hết tình huống/giải pháp/kiến thức, code BEFORE/AFTER/TEST, ví dụ sai có chủ đích và expected outputs. Không bỏ case cuối hoặc lấy fence đầu tiên rồi bỏ lời giải.

## 2. Ownership Java final

WRITE chỉ đúng hai group folders do Prime chỉ định:

~~~text
MODULE_ROOT/src/main/java/JAVA_BASE_PACKAGE_PATH/casestudy/g<groupID>/
MODULE_ROOT/src/test/java/JAVA_BASE_PACKAGE_PATH/casestudy/g<groupID>/
~~~

Đây là **source tree Java FINAL của module**, không phải temp/staging. Mỗi caseIndex c phải nằm trong package **JAVA_BASE_PACKAGE.casestudy.g<g>.c<c>** và đường dẫn c<c>/ tương ứng. Worker được đọc master.json MODULE_TYPE/JAVA_BASE_PACKAGE, Gradle và existing source để hiểu runtime, **không sửa** các file ngoài WRITE, bao gồm caseStudy YAML, Sheet, file docs, Java group khác, build.gradle, Spring Application, master.json, Git/CI. Không đụng dirty files/legacy temp; nếu package/route đã có owner khác hoặc duplicate Bean → BLOCKER: STEP8_WORKER_OWNERSHIP, không overwrite.

Với từng case có runnable Java, tạo:

~~~text
src/main/java/.../casestudy/g<g>/c<c>/CaseController.java
src/main/java/.../casestudy/g<g>/c<c>/CaseService.java
src/test/java/.../casestudy/g<g>/c<c>/CaseServiceTest.java
src/test/java/.../casestudy/g<g>/c<c>/CaseControllerTest.java (nếu HTTP test phù hợp)
~~~

- **Class name chính xác:** CaseController và CaseService cho **tất cả cases**. Không đổi thành Case1Controller, GroupController hoặc một cặp class dùng chung cả group. Package g/c đảm bảo class không trùng.
- **Spring bean Controller** chính xác: @RestController("caseStudyG<g>C<c>Controller"), ví dụ **caseStudyG1C1Controller**. Service bean khuyến nghị @Service("caseStudyG<g>C<c>Service").
- **Route GET chính xác**: **GET /case-studies/group/<g>/case/<c>/execute**, ví dụ GET /case-studies/group/1/case/1/execute. Không POST/PUT, không /api/v1/java, không dùng /groups.
- Controller **execute() không nhận input** từ client: không @RequestBody, @RequestParam, @PathVariable, form hay nghiệp vụ cần header tùy biến. Constructor-inject Service, Controller chỉ gọi Service.
- **Service tự khởi tạo dữ liệu demo ở mỗi lần GET**; chạy code/thuật toán nghiệp vụ thật và trả kết quả có ý nghĩa cho người học quan sát (steps, before/after, kết quả tính toán, so sánh, thông điệp rõ ràng). Không hardcode PASS/OK, không trả response có sẵn thay cho chạy code, không chỉ System.out.println.
- GET phải repeatable/safe: in-memory state mới theo mỗi request, không DB thật, network thật, email/SMS thật, shared mutable state hay vòng lặp/timeout vô hạn. Với các case muốn minh họa side effect/concurrency, dùng fake dependency/test double và thời hạn chạy hữu hạn.
- Phase chuyển đổi duy nhất: có thể cải thiện tên method, tách helper/DTO/model ngay khi dựng Java nếu thực sự giúp demo dễ hiểu nhưng **không có lượt refactor khác**. Giữ semantics của Case Study, không xóa phản ví dụ và expected behavior.
- SERVLET → Spring MVC; REACTIVE → runtime WebFlux phù hợp. LIBRARY/PLATFORM không tự thêm web runtime để ép tạo Controller: BLOCKER: STEP8_MODULE_RUNTIME_POLICY.

## 3. Tests và kiểm soát chất lượng

1. Test Service có assertions thực về output tính được, bài học chính, scenario/edge cases và repeatability. Nếu có HTTP test framework tương ứng, test GET URL đúng số group/case, **không body/query**, status 200, response meaningful, gọi nhiều lần không làm thay trạng thái. Invalid input/biến thể nằm trong Service/test, không được đẩy thành request params.
2. Không chạy full Gradle/startup khi còn worker khác đang ghi cùng module main sourceSet; nếu không có cửa sổ test độc quyền do Prime cấp thì đánh dấu **BUILD_DEFERRED**, không báo PASS giả. Không chạy Gradle clean hay sửa Gradle config. Có thể rà cú pháp/imports/đường dẫn và đọc lại files.
3. Trường hợp code Sheet chỉ là pseudocode/giải thích không thể chạy thật thì báo **NOT_APPLICABLE** cho đúng case cùng lý do; không tạo endpoint rỗng hoặc trả PASS giả. Nếu chuyển đổi cần đổi ý nghĩa tình huống/không thể thực thi an toàn, BLOCKER: STEP8_SEMANTIC_GAP.
4. **Đọc lại** toàn bộ Java/test đã ghi, đối chiếu packages, class names, Bean/GET route, source case SHA và group source hash trên Sheet. Không tuyên bố READBACK=PASS khi chưa thật readback.

## 4. Manifest duy nhất theo group, báo cáo Prime

Viết **một** step8-group-manifest.json trong TEST_GROUP_FINAL_DIR, JSON UTF-8; tối thiểu: runId, groupID, sourceSha256, caseCount, danh sách các case với caseIndex, sourceCodeSha256, status GENERATED/NOT_APPLICABLE/BLOCKER, sourceFiles/testFiles + SHA256, package, controllerClass=CaseController, serviceClass=CaseService, beanName, httpRoute, noClientInput=true, buildStatus/testEvidence, limitations. Không để thiếu cases, không dùng manifest mỗi case thay cho manifest cả group. Ghi/readback JSON và tất cả files, đúng ownership.

Báo PHASE=ONE_PASS, RUN, UNIT=Gx, GROUP_CASE_COUNT, GENERATED/NOT_APPLICABLE/BLOCKER theo từng case, JAVA_FILES/TEST_FILES, BEANS, GET_ROUTES, NO_INPUT, DEMO_RESULT_EVIDENCE, COMPILE/TEST=PASS/BUILD_DEFERRED/BLOCKER, READBACK và blocker. Release ownership về SLEEPING.

**Prime** QA mọi group/case, chạy build/test/startup/GET smoke/regression khi workers đều nghỉ. Nếu cần sửa, Prime giao lại **đúng group** để sửa Java final; không tạo Phase 2. Worker không tự cập nhật CURRENT_STEP, không commit/push, không sync ngược module→file/Sheet (STEP 9 sau này).
