# STEP 8 MỚI — Chuyển nguyên trạng mã Case Study trên Sheet thành Java phân package

## 0. Mệnh lệnh ưu tiên tuyệt đối

**STEP 8 là STRUCTURAL TRANSFORMATION (tách/đặt class vào package), KHÔNG phải generate/refactor/sáng tác logic.** Nguồn chân lý duy nhất là Java code đã có trong Case Study Phase 4 trên Google Sheet. **Không thay đổi bất kỳ nội dung hoặc hành vi nào so với code gốc, NGOẠI TRỪ đoạn thật sự gây lỗi và có bằng chứng cụ thể.** Việc đổi chỗ khai báo nhằm làm Java compile được ở package mới là thay đổi cấu trúc bắt buộc, không phải quyền sửa nghiệp vụ.

- **ĐÚNG MỘT WORKER / MỘT GROUPID / MỘT LƯỢT:** Prime giao nguyên group, **bao gồm tất cả caseIndex của group đó**, cho đúng một worker sở hữu từ lúc nhận nhiệm vụ đến khi hoàn tất/readback và nhả quyền ghi. **Cấm giao riêng từng case cho các worker khác nhau, chia một group thành batch hoặc để nhiều worker cùng ghi một group.** Worker viết trực tiếp Java final dưới casestudy/gX/cY. Prime điều phối và **chỉ QA**, không tự viết/chỉnh/gom source. Không Phase 2, temp promotion hoặc worker refactor tự do.
- **Worker TUYỆT ĐỐI KHÔNG tạo, sửa, xóa hay di chuyển Controller hoặc ControllerTest.** Controller/HTTP adapter phải có owner/quy trình độc lập bên ngoài worker STEP 8; Prime chỉ xác minh contract có sẵn. Không tự giao cho Prime viết Controller. Nếu thiếu hoặc bất tương thích, ghi blocker và chờ xử lý đúng ownership, không chế endpoint/DTO.
- Worker chỉ tạo/chuyển **CaseService.execute()**, các class/interface/record/enum vốn có trong code gốc thành file top-level, các static helper sang utils, tests bảo toàn nguyên bản và manifest. **Cấm đặt bất kỳ inner/nested class, static class, record, enum, interface nào trong CaseService.**
- Giữ toàn bộ câu lệnh, phép tính, hằng số, vòng lặp, điều kiện, thứ tự side effect, thông báo, exception, assertion, tên và ý nghĩa mã nguồn; **không xóa code chỉ vì unused/dead, không đổi thuật toán, không tối ưu, không thêm validation/fake/luồng demo/DTO/response trường mới**. Không sửa code để “đẹp hơn” hay tăng coverage.
- STEP 8 chỉ READ Google Sheet và đối chiếu YAML module; không ghi Sheet, YAML/Markdown, Interview/Quiz/Knowledge, Gradle, Spring Application, master.json hay Git. Không commit/push/deploy. Không coi kết quả STEP 8/9 pilot cũ đã đạt hợp đồng mới; muốn chạy lại phải preflight ownership/baseline trước.

## 1. Nguồn Sheet và fingerprint — giữ contract hiện có

1. Prime đọc GENERAL_AGENT_RULES.md, toàn bộ rules/, STEP_8.md và worker/STEP_8_WORKER.md. Xác minh module-generate-info.txt, URL/gid/tab, A1/B1/I3 và MODULE_ROOT/master.json (MODULE_TYPE, JAVA_BASE_PACKAGE, CURRENT_STEP). Không tự suy ra tab hoặc module từ tên.
2. Ghép nguyên văn YAML Case Study nền **S20:U24** (theo hàng trên→dưới, mỗi hàng cột S→T→U, **không separator**), parse caseStudy[].groupID/cases[] và situation/resolve/knowledge/readmeRelated.
3. Với group g, ghép nguyên văn code Phase 4 **S(27+3×(g−1)):U(29+3×(g−1))**, parse groupID/codes[].caseIndex/code. Ghép **theo groupID + caseIndex** (1-based), đọc toàn bộ Markdown/Java fences và giải thích; phân biệt phiên bản sai/đúng, ví dụ compile-fail, executable code và instruction chạy ngoài JVM. Không chỉ lấy fence đầu hoặc tự trộn nhiều chương trình.
4. Tính và kiểm **SOURCE_SHA256 = SHA256(UTF8(baseYamlJoined + "\n---STEP8-GROUP-PHASE4---\n" + groupCodeYamlJoined))** trên đúng bytes từ cell, cùng **CASE_CODE_SHA256 = SHA256(UTF8(raw code Markdown))** của từng case. Worker kiểm trước ghi và đọc lại Sheet trước bàn giao. Drift/missing/case mapping sai → BLOCKER: STEP8_SHEET_SOURCE_DRIFT.
5. Case không có runnable Java entrypoint hoặc có nhiều entrypoint không xác định được chương trình canonical: báo NOT_APPLICABLE/BLOCKER kèm lý do và vị trí nguồn; **không chế main(), không hợp nhất các fence, không tạo Service trả PASS giả**. Không dùng YAML module/cache làm nguồn thay Sheet.

## 2. Phép biến đổi duy nhất được phép

**A. main → Service.execute**

- Lấy **chính xác phần thân** của phương thức **static void main(String[] args)** trong chương trình runnable được xác định, đưa vào **CaseService.execute()** theo nguyên thứ tự; không tóm tắt, diễn giải, viết lại hay hoán đổi câu lệnh.
- Giữ nguyên khởi tạo biến, literal, cấu trúc điều khiển, lời gọi, assert/check, System.out/err, throw, kết quả và side effect. **Không tự thay println bằng Map/JSON, không sinh DemoResult**, không xóa assertion/check, không sinh sample inputs/policy/notification mô phỏng ngoài những gì main gốc đã có.
- Khác biệt kỹ thuật bắt buộc: tên/signature main → execute, static context và định danh kiểu/phương thức theo package mới. Với main gốc void, **không tự sáng tạo return object**. Kiểu trả về và kết nối HTTP là hợp đồng adapter độc lập; nếu Controller hiện có yêu cầu return type khác hoặc main dùng args nhưng GET không có input, báo **BLOCKER: STEP8_EXECUTE_CONTRACT** thay vì viết lại code.
- Không giữ bản main() trùng lặp trong production sau khi đã chuyển thân vào execute, trừ khi source có nhiều mục đích chạy riêng bắt buộc phải giữ và Prime đã xác minh; **không xóa chính các câu lệnh của main**.

**B. Class/type → top-level file trong package phù hợp**

- Mỗi inner/nested class, **static class**, interface, record, enum, kể cả nested nhiều cấp, được tách thành **top-level type trong file Java tên tương ứng**. Giữ nguyên tên type, members, methods, field initialization, constructor, inheritance, generic, annotations và visibility/semantics tối đa. Outer class chỉ làm container của code gốc có thể biến mất như một wrapper sau khi **mọi nội dung có nghĩa đã được map**; nếu outer class còn state/behavior thì phải giữ dưới dạng type riêng, không làm rơi code.
- Package gốc của từng case: JAVA_BASE_PACKAGE.casestudy.g<g>.c<c>. Danh mục package gợi ý theo **trách nhiệm thực của code gốc**: **application** (use case/điều phối), **domain** (entity/value/invariant/policy), **port** (interface giao tiếp external), **dto** (chỉ DTO đã thật sự có trong source), **model** (data model không rõ domain), **adapter** (implementation I/O đã có), **repository** (logic lưu trữ đã có), **utils** (static methods); **service** chỉ chứa CaseService, **controller** là vùng của owner khác. Không ép đủ layer; không tự tạo package ngoài danh mục khi chưa được Prime xác minh. Không tạo class giả để “đủ bộ”.
- **Tất cả static methods phụ trợ (ngoại trừ main)** phải được nhấc ra class tương ứng trong **utils/** và cập nhật lời gọi. Giữ nguyên method name/body/parameters/return/exception và thứ tự; static field không tự biến thành biến instance/đổi vòng đời. Nếu method phụ thuộc private state, overload, method reference, initialization hoặc Java access rules làm việc chuyển không thể bảo toàn ngữ nghĩa, **BLOCKER: STEP8_STATIC_EXTRACTION_SEMANTICS**; không sửa thuật toán để lách.
- Chỉ được đổi **package/import, qualification, visibility ở mức tối thiểu để compile**, và những sửa chữa cú pháp thực sự bắt buộc vì tách type/method. Không thay string, number, operator, check, condition, sequence, validation, exception hoặc test outcome.

**C. Lỗi thật sự — ngoại lệ duy nhất cho sửa nội dung**

- Chỉ sửa khi có **lỗi compile/runtime cụ thể được tái hiện** từ code gốc hoặc từ phép di chuyển cấu trúc, không phải cảnh báo style/unused/dead code, lời khuyên design, hay giả định người đọc sẽ thích hơn.
- Trước sửa lưu **file, dòng, code trước, lỗi/log/stack trace, nguyên nhân, cách sửa nhỏ nhất, code sau và test xác nhận** vào manifest. Bất kỳ sửa nào ngoài package/import/qualification/visibility/signature bắt buộc phải đánh dấu **ERROR_FIX** riêng và đưa Prime QA; không chứng minh được → BLOCKER: STEP8_CONTENT_CHANGE_UNPROVEN.
- Không dùng “sửa lỗi” để thêm behavior mới, giả lập thành công, đổi output hoặc xóa ví dụ sai có chủ đích. Nếu logic gốc gây side effect thật/nguy hiểm khi GET gọi, **không tự thay bằng fake**; báo BLOCKER: STEP8_UNSAFE_OR_UNSUPPORTED_EXECUTION.

## 3. Cấu trúc output; Controller không thuộc worker

Với case Gg/Cc, module base package được xác minh:

~~~text
MODULE_ROOT/src/main/java/<BASE_PACKAGE_PATH>/casestudy/g<g>/c<c>/
    service/CaseService.java                # thân main -> execute
    application/<OriginalUseCase>.java      # nếu source có
    domain/<OriginalDomainType>.java        # nếu source có
    port/<OriginalPortInterface>.java       # nếu source có
    dto/<OriginalDto>.java                  # chỉ nếu source có
    model/ | adapter/ | repository/         # chỉ nếu source có và phù hợp
    utils/<OriginalPurpose>Utils.java       # static helper từ source
MODULE_ROOT/src/test/java/<BASE_PACKAGE_PATH>/casestudy/g<g>/c<c>/
    ...tests kiểm chứng phép chuyển cấu trúc và kết quả gốc...
~~~

- **CaseService.java chỉ chứa class top-level CaseService và execute (cùng boilerplate/annotation framework thật sự cần để nối bean), không lồng bất kỳ type gốc nào; không thêm record DemoResult.** Spring Service bean identity nếu adapter có hợp đồng: caseStudyG<g>C<c>Service.
- **Controller không nằm trong WRITE của worker, kể cả nếu file hiện hữu trong group root**. Prime kiểm tra route GET /case-studies/group/<g>/case/<c>/execute, bean caseStudyG<g>C<c>Controller, no-client-input và API signature do owner khác cung cấp. Missing Controller / incompatible execute response → **BLOCKER: STEP8_CONTROLLER_OWNER_OR_SIGNATURE**. Prime STEP 8 vẫn chỉ QA, không tự bổ sung Controller.
- Ví dụ mapping **G1/C1 của source Sheet** (chỉ minh họa tên/package, không phải hướng dẫn tạo class mới): Case1.main → service/CaseService.execute; Patient & AppointmentBook & Billing → domain; HospitalUseCase → application; Messenger → port; static check → utils/Case1Utils.check. Mọi biểu thức phí/lịch/thông báo/check và println phải giữ nguyên; không có DemoResult vì source không có.
- Chỉ triển khai từ mã nguồn gốc, không coi các Java STEP8/STEP9 pilot đã refactor là nguồn chuẩn. Nếu source final cũ đang nằm tại đích, **không overwrite tự động**; phải xác minh ownership và bảo toàn snapshot trước khi triển khai lại.

## 4. Prime preflight, assignment và ownership

1. Kiểm exact MODULE_ROOT, type/runtime, JDK, existing Controller adapter, class collisions, source/test roots và Git worktree/index; snapshot file/hash hiện có. Phải phân biệt lỗi source gốc với lỗi vì package. **Không restore/clean/overwrite code bị xóa hay sửa có chủ đích**, không sửa Controller cũ.
2. Mode A tối đa 3 worker, Mode B đúng N user cấp; reuse SLEEPING, spawn tuần tự từng worker. **Đơn vị phân công và ownership luôn là GROUP, không phải CASE**: Prime lập hàng đợi toàn bộ groupID, mỗi group chỉ một worker writer, mỗi worker nhận đúng một group tại một thời điểm; các worker khác chỉ được nhận **group khác**. Worker phải xử lý đủ mọi case của group, lập manifest đủ case, readback và release ownership **trước khi** được giao group kế tiếp. QA lỗi case nào thì trả lại **worker owner của group**, không tạo assignment cho một worker khác chỉ xử lý case đó.
3. Handoff **bốn dòng**, hai Java group roots và một đường manifest trong build ignored đã xác minh. Vùng build/step8 chỉ ghi evidence của RUN được giao; không chạm manifest lịch sử của RUN cũ:

~~~text
<VERIFIED_SHEET_TAB_URL>
READ=A1,B1,I3,S20:U24,<EXACT_GROUP_PHASE4_RANGE>;UNIT=G<groupID>;RUN=<RUN_ID>;SOURCE_SHA256=<GROUP_SOURCE_SHA256>
WRITE=<EXACT_MAIN_GROUP_ROOT>,<EXACT_TEST_GROUP_ROOT>,<EXACT_IGNORED_STEP8_GROUP_EVIDENCE_DIR>
/java-learning/module-generate-agent/worker/STEP_8_WORKER.md
~~~

4. Worker ghi riêng từng c<c>/ dưới **group đã nhận**, **không chỉnh Controller/ControllerTest hoặc group khác**; được xử lý tất cả case của group đó, không nhận thêm case/group ngoài assignment. Không ghi staging/temp ngoài owner. Lưu mapping từng original type/method/statement → file/package/dòng đích; các class không có trong source phải bị đánh dấu EXTRA và fail QA. Manifest mới đặt tại build/step8/<RUN_ID>/g<g>/step8-group-manifest.json (ignored), **phải bao phủ mọi caseIndex của group**; **không sửa step8-group-manifest.json lịch sử đã tồn tại trong test root**.
5. Khi nhiều worker còn ghi cùng module sourceSet, **không chạy full Gradle compile/test/startup**. Worker được rà riêng scope và báo BUILD_DEFERRED trung thực. Prime chờ writer barrier mới chạy QA chung.

## 5. Worker completion & Prime QA-only

- Worker đối chiếu **1:1 source-to-target**: toàn bộ type (bao gồm static/nested), toàn bộ static methods chuyển vào utils, từng statement main nằm trong CaseService.execute với nguyên thứ tự, các field/constructor/instance methods giữ nguyên; mọi code lỗi sửa có audit trail. Không tự thêm/bỏ bất kỳ logic nào.
- Tests trong đúng case test root chỉ so **hành vi nguyên bản** (output, exception, assertion, thứ tự, state) và verify structural package; không biến test trở thành lý do sửa code. Test HTTP/Controller đã có do owner khác quản lý, worker không sửa. Nếu JUnit cũ import source không còn, Prime phân biệt **baseline blocker** với STEP 8 compile lỗi.
- Exact readback Java/test + manifest, source hashes từ Sheet, path ownership, Controller unchanged. Manifest status per case: TRANSFORMED / NOT_APPLICABLE / BLOCKER, original list of types/static methods/main statements, destination files/hashes, preserved behavior evidence, compile status, **ERROR_FIX diff/audit** hoặc NONE.
- Prime **chỉ QA**: verify diff ngoài phép di chuyển, 100% source coverage và type/statement mapping, Service không nested types, Controller unchanged, compile/test startup/GET khi integration sẵn có và an toàn; lỗi giao đúng worker group sửa trong scope. Nếu Controller chưa có owner/contract, không báo endpoint PASS và không đóng STEP. Không gom Java một module/case hay tạo phase chuyển tiếp.
- Completion toàn STEP chỉ khi đủ mọi group/case, source fidelity và compile/tests/adapter integration được xác minh hoặc NOT_APPLICABLE hợp lệ; không còn blocker/worker pending. Pilot một group **không** cập nhật CURRENT_STEP. Chỉ Prime xét CURRENT_STEP.VALUE="8" theo rules/PRIME_RULES.md §2 sau QA toàn module; không commit/push.
- **STEP 9 về sau chỉ được kiểm chứng/đặt package đúng hợp đồng nguồn, không được dùng quyền refactor sáng tạo của các bản STEP 9 cũ để sửa nội dung STEP 8 vừa bảo toàn.** Nếu STEP 9 chưa được đồng bộ contract thì chưa được chạy lại theo logic cũ.

**Prime report bắt buộc:** RUN/URL/gid/module, SOURCE_SHA256 và CASE_CODE_SHA256, group/case count, TYPE_MAPPING, STATIC_METHOD_MAPPING, MAIN_STATEMENT_FIDELITY, PACKAGE_ASSIGNMENTS, UNAPPROVED_ADDITIONS=0, ERROR_FIX_LOG, SERVICE_NO_INNER_TYPES, CONTROLLER_UNCHANGED/ADAPTER_STATUS, TEST/BUILD/HTTP nếu có, HISTORICAL_MANIFEST_UNCHANGED, SHEET/YAML_UNCHANGED, OUT_OF_SCOPE_UNCHANGED, CURRENT_STEP_BEFORE/AFTER, BLOCKERS.
