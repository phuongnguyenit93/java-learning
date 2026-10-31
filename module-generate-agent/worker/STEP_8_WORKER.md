# STEP 8 — WORKER ONLY: chuyển Java gốc từ Sheet vào package, không đổi nội dung

**Đọc toàn bộ file này trước mỗi nhiệm vụ. Quy tắc cao nhất: giữ nguyên 100% nội dung và hành vi code gốc; chỉ sửa khi lỗi thật sự, có bằng chứng và diff tối thiểu.** Không generate ví dụ mới, không tự cải tiến/refactor, không thêm DemoResult, policy, fake, validation, DTO hay lời giải khác ngoài code Sheet. Worker **KHÔNG làm Controller**. **MỘT worker nhận MỘT groupID và phải hoàn tất TẤT CẢ case trong group đó; không có assignment từng case, không chia group giữa nhiều worker.**

## 1. Handoff 4 dòng — một group / một lượt

~~~text
<VERIFIED_SHEET_TAB_URL>
READ=A1,B1,I3,S20:U24,<EXACT_GROUP_PHASE4_RANGE>;UNIT=G<groupID>;RUN=<RUN_ID>;SOURCE_SHA256=<GROUP_SOURCE_SHA256>
WRITE=<EXACT_MAIN_GROUP_ROOT>,<EXACT_TEST_GROUP_ROOT>,<EXACT_IGNORED_STEP8_GROUP_EVIDENCE_DIR>
/java-learning/module-generate-agent/worker/STEP_8_WORKER.md
~~~

- Handoff thiếu URL/gid, group, range, hash, WRITE hoặc evidence path bị Git track → BLOCKER: STEP8_WORKER_HANDOFF. **UNIT=G<groupID> là toàn bộ group, không phải G<groupID>/C<caseIndex>.** Worker phải kiểm kê và chuyển đổi đủ tất cả caseIndex của group được giao, kể cả case NOT_APPLICABLE/BLOCKER phải ghi lý do; không tự chọn bỏ case, không nhận riêng một case hoặc group khác.
- A1/B1/I3 phải đúng module. READ Sheet **chỉ đọc**, Base S20:U24 và Phase 4 g **S(27+3×(g−1)):U(29+3×(g−1))**: nối ô theo hàng trên→dưới, mỗi hàng trái→phải, **không separator**. Parse groupID, cases[], codes[].caseIndex; đối chiếu chính xác từng code Markdown với tình huống và lời giải.
- SOURCE_SHA256 = SHA256(UTF8(baseYamlJoined + "\n---STEP8-GROUP-PHASE4---\n" + groupCodeYamlJoined)). Tính lại trên chuỗi cell gốc, lưu SHA256(raw code Markdown) của từng case. Đọc lại trước bàn giao; có drift/thiếu nguồn → BLOCKER: STEP8_SHEET_SOURCE_DRIFT. Không dùng local YAML/case Service cũ làm nguồn chính.
- Nhiều Java fences trong một case phải được kiểm kê đầy đủ (đúng/sai/test/compile-fail), không tự ghép thành chương trình mới. Không có runnable main canonical → NOT_APPLICABLE hoặc BLOCKER có chứng cứ; không tạo Service giả.

## 2. Quyền ghi và cấu trúc bắt buộc

Worker **chỉ** ghi Java source dưới MAIN_GROUP_ROOT/g<g>/c<c>/ (theo handoff MAIN_GROUP_ROOT vốn đã là g<g>/), tests trong TEST_GROUP_ROOT/c<c>/ và manifest tại ignored evidence dir được cấp. Những file khác, nhất là Controller, ControllerTest, Spring Application, Gradle, YAML/Sheet, manifest lịch sử, master.json, Git, source của case/group khác = READ-ONLY. Không overwrite code hiện hữu chưa có ownership xác minh. Một writer/group.

Mỗi case có main gốc runnable, cấu trúc output:

~~~text
casestudy/g<g>/c<c>/
  service/CaseService.java
  application/<OriginalUseCase>.java   (nếu thực sự tồn tại)
  domain/<OriginalType>.java          (nếu thực sự tồn tại)
  port/<OriginalInterface>.java       (nếu thực sự tồn tại)
  dto/<OriginalDto>.java              (chỉ nếu source có DTO)
  model/ | adapter/ | repository/     (chỉ khi source có thành phần tương ứng)
  utils/<Purpose>Utils.java           (đặt tất cả static helper gốc)
~~~

- service/CaseService.java **chỉ có class CaseService top-level và method execute() cùng boilerplate cần thiết**. **KHÔNG chứa bất kỳ inner/nested class nào, kể cả static class, record, enum, interface.** Không chuyển bất kỳ class con gốc nào vào Service dù không static. Không tạo DemoResult hoặc các type phục vụ trình diễn không có trong Sheet.
- Với **mọi inner/nested type gốc** (class, static class, interface, record, enum), tách ra **top-level file riêng**, đặt tên type như source, package theo trách nhiệm. Không tự thêm type, không xóa type source ngay cả khi chưa sử dụng; outer wrapper không có nội dung còn lại mới có thể bỏ sau mapping.
- Danh mục gợi ý: application = use case; domain = entity/value/policy/rule; port = hợp đồng external; dto = data transfer type **đã có**; model = data holder có trong source; adapter = implementation giao tiếp đã có; repository = lưu trữ đã có; utils = static methods. **Không gán package theo tên một cách tùy tiện**, đối chiếu trách nhiệm thật. Không cần đủ tất cả package; package ngoài danh mục phải hỏi Prime (BLOCKER: STEP8_PACKAGE_CLASSIFICATION).
- Với **tất cả static methods phụ trợ**, giữ nguyên body và chuyển sang class tên phù hợp trong **utils/**; sửa call-site/import/qualification ở mức tối thiểu. **Ngoại lệ static void main:** chuyển body sang CaseService.execute(), không tạo utils.main. Không chuyển static field thành instance field, không đổi vòng đời/lazy initialization; vấn đề access/private/static semantics → BLOCKER: STEP8_STATIC_EXTRACTION_SEMANTICS.
- Tách class từ nested sang top-level có thể phải đổi visibility/package/import/type qualifier/constructor access **chỉ tối thiểu để compile**. Giữ nguyên type name, field, logic, signature, generic, assignment, lambda, error messages, print, calculations, order of calls.

## 3. Thuật toán chuyển main → execute: copy logic, không rewrite

1. Định vị đúng Java entrypoint **public static void main(String[] args)** của chương trình runnable canonical trên Sheet.
2. Map **từng câu lệnh trong thân main** sang **CaseService.execute()** đúng nguyên thứ tự, không bỏ check/assertion, System.out.println/err, throw, tạo dữ liệu, vòng lặp, biến, giá trị, lời gọi hay xử lý exception.
3. Giữ nguyên hành vi của helpers; chuyển các static helpers sang utils và sửa tên gọi tại vị trí cũ. Không thay check thành boolean trả về JSON; không chuyển println thành response. **Main void thì execute không được tự sinh DTO/Map/record return**, contract HTTP là việc của owner Controller khác.
4. Không nhân bản main() thành một chương trình khác, không thêm kịch bản sai/đúng tự tưởng tượng hoặc fix validation “cho chắc”. Những ví dụ sai có chủ đích và code unused vẫn cần giữ **đúng source** để có provenance; **cấm tự xóa unused/dead code**.
5. Nếu main có args được sử dụng, gọi System.exit, truy cập file/network/DB thật, có bất đồng kiểu trả về so với Controller có sẵn hoặc không thể thực thi an toàn qua GET, **báo blocker** (STEP8_EXECUTE_CONTRACT/STEP8_UNSAFE_OR_UNSUPPORTED_EXECUTION), không đổi code cho hợp API.
6. Spring @Service("caseStudyG<g>C<c>Service") chỉ là wiring khi có hợp đồng adapter đã xác minh; không gán annotation Service lên bất kỳ type gốc nào. **Không tạo, sửa, di chuyển Controller hay ControllerTest**, kể cả trong folder WRITE. Controller cũ không hợp signature → BLOCKER: STEP8_CONTROLLER_OWNER_OR_SIGNATURE.
7. Với sửa lỗi thật: chứng minh bằng compile error/exception có thể tái hiện; chỉ patch dòng tối thiểu, giữ mọi phần khác; ghi **ERROR_FIX: BEFORE / AFTER / ROOT_CAUSE / ERROR_LOG / TEST**. Không chứng minh được → BLOCKER: STEP8_CONTENT_CHANGE_UNPROVEN. Không dùng lỗi style/unused làm lý do xóa code.
8. Test trong scope chỉ kiểm semantics gốc và mapping package/calls; không thêm logic vào production để làm test dễ. Không chạy full module Gradle khi có writer khác; PASS chỉ khi đã chạy test thực, còn lại BUILD_DEFERRED.

## 4. Ví dụ minh họa source G1/C1 (chỉ mapping, không viết lại code)

Code nguồn có Case1.main(), record Patient, static class AppointmentBook, interface Billing, interface Messenger, static class HospitalUseCase và static check(...). Mapping hợp lệ:

~~~text
Case1.main() body           -> service/CaseService.execute()
Patient                     -> domain/Patient.java
AppointmentBook             -> domain/AppointmentBook.java
Billing                     -> domain/Billing.java
Messenger                   -> port/Messenger.java
HospitalUseCase             -> application/HospitalUseCase.java
Case1.check(boolean)        -> utils/Case1Utils.check(boolean)
~~~

**Giữ nguyên fee 100_000/120_000, booking 9:00, notifications, assertion và println**. Không thêm DemoResult, không tạo fake logic khác, không tái kiến trúc lại HospitalUseCase; lambda fake messenger trong chính main gốc phải được giữ. Controller không do worker viết.

## 5. Exact readback, manifest và bàn giao

- Ghi **một manifest mới theo group** tại thư mục evidence riêng được cấp trong build/step8/<RUN_ID>/g<g>/step8-group-manifest.json; folder phải được Git ignore. **Không viết đè manifest STEP 8 lịch sử dưới src/test hoặc kết quả STEP9 pilot.**
- Per case: group/case IDs, source/code SHA, runnable canonical fence, original main statement mapping ordered, **tất cả original type và destination** (class/interface/record/enum), **tất cả static method + utils location**, mọi original type không rơi mất, created/modified files + SHA256, original behavior/test evidence, type/bean/Controller unchanged, **ERROR_FIX log hoặc NONE**, blocker/notApplicable. No invented type/class; no content-edit-unproven.
- Exact readback mọi file/manifest sau ghi, đối chiếu lại Sheet/hash, test source-to-target diff chỉ có package/import/type extraction + required wiring/signature. Nếu code vẫn chứa nested types trong Service hoặc có static helper ngoài utils → FAIL.
- **Chỉ bàn giao group sau khi đã rà/readback mọi caseIndex của group**, manifest đầy đủ và mỗi case có trạng thái rõ ràng; không bàn giao từng case để Prime giao tiếp cho worker khác. Bàn giao: RUN, UNIT=Gg, GROUP_CASE_COUNT, COUNT_TRANSFORMED/NOT_APPLICABLE/BLOCKER, từng case status, ORIGINAL_TO_PACKAGE_MAPPING, MAIN_BODY_MATCH, STATIC_UTILS, SERVICE_NO_NESTED, CONTENT_UNCHANGED/ERROR_FIX, FILE_HASHES, CONTROLLER_UNCHANGED, COMPILE/TEST=actual/DEFERRED, SOURCE_READBACK, BLOCKERS. Release **toàn group** rồi SLEEPING; chỉ sau đó mới có thể nhận group kế tiếp.
- Prime **chỉ QA, không gom source, không thêm Controller thay worker**. QA FAIL trả đúng group worker sửa trong scope; không tự tăng CURRENT_STEP; không commit/push/Sheet/YAML sync. **Các STEP9 contract cũ cho phép sửa logic và xóa helper không được dùng để override tính nguyên trạng của STEP8 mới.**
