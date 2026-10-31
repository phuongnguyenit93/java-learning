# STEP 8 — Prime tạo Controller/Service theo group; Worker chuyển nguyên trạng Java theo case

## 0. Hợp đồng hiện hành — ưu tiên cao nhất

**BLOCKER-RESOLUTION CATALOG (ưu tiên cao nhất, ghi đè các câu cấm tuyệt đối cũ bên dưới):** Worker **luôn chuyển code bình thường trước**. Nếu phát hiện nguy cơ blocker, đối chiếu **toàn bộ danh sách quy tắc hiện hành trong `worker/STEP_8_WORKER.md §3B`**. Hiện danh sách có `INPUT_REQUIRED` (truyền input gốc qua Service/Controller đúng group) và `JVM_ISOLATION_REQUIRED` (dùng JVM con khi trạng thái JVM/ClassLoader hoặc side effect process-global không an toàn/lặp lại được); **danh sách có thể bổ sung các quy tắc khác về sau**. **Khớp quy tắc nào phải xử lý theo quy tắc đã duyệt đó trước, không đánh BLOCKER ngay.** Không có quy tắc nào phù hợp, hoặc áp dụng quy tắc phù hợp mà có bằng chứng không thể chạy tương đương/an toàn, mới là BLOCKER thực sự. Worker không tự sáng tác quy tắc ngoài danh sách, không tạo PASS giả, không sửa nghiệp vụ. Prime QA sau worker release.

**Đơn vị tổ chức HTTP = group. Đơn vị chuyển đổi code = từng case, nhưng đơn vị giao việc = nguyên một group cho đúng một worker.**

1. **Prime làm trước worker:** đọc Google Sheet để xác minh module, mọi groupID/caseIndex, tạo duy nhất **một** `casestudy/controller/Group<g>Controller.java` và **một** `casestudy/service/Group<g>Service.java` cho **mỗi group**. Controller có đúng một GET endpoint cho từng case. Service có đúng một public void `executeCase<c>()` cho mỗi case, ở trạng thái khung chờ worker điền body từ `main()`. Không tạo Controller/Service theo từng case.
2. **Prime chỉ giao groupID và vùng đọc Sheet chính xác**: handoff có `UNIT=G<groupID>`, `S20:U24` và vùng Phase 4 của group đó. **Không cần giao danh sách caseIndex hoặc caseCount.** Worker đọc nguồn để xác định **toàn bộ case thuộc group được giao**, không tự chọn group khác hoặc bỏ case; không chia group thành nhiều worker hoặc nhiều phase. Worker chuyển **nguyên thân `static void main(String[] args)`** của từng case vào đúng `Group<g>Service.executeCase<c>()`, không sáng tác chương trình mới. Mặc định worker không sửa Controller; chỉ được sửa input binding đúng group khi khớp `INPUT_REQUIRED`.
3. Worker tách các type gốc (class, static/nested class, interface, record, enum) thành **top-level file** và đặt trong `casestudy/<loại-package>/group<g>/case<c>/OriginalType.java`; static helper methods vào `casestudy/utils/group<g>/case<c>/...`. **Không để nested type hay static helper của case trong GroupService.** Package được chọn theo trách nhiệm thật, không ép đủ layer.
4. **Nội dung nguồn bất biến về hành vi:** giữ toàn bộ statement và thứ tự, giá trị, biểu thức, điều kiện, side effect, console output, assertion, exception, field, constructor, method. Được format lại whitespace/layout cho dễ đọc và dọn import/qualification theo **worker/STEP_8_WORKER.md §3A** nhưng không thay đổi semantics; thay đổi nội dung nghiệp vụ khác chỉ khi có lỗi compile/runtime được tái hiện, audit before/after và sửa tối thiểu. Không xóa unused/dead, không thêm validation, fake, logic demo, DTO, `DemoResult` hoặc return JSON tự sáng tác.
5. Phạm vi STEP 8: Google Sheet **READ-ONLY**, worker không ghi Sheet/YAML/Markdown/importer, Gradle, Spring Application hoặc Git; **hạ tầng JVM con dùng chung nếu cần do Prime chuẩn bị riêng**, không cấp quyền worker sửa build/runtime toàn module. Không tự commit/push/deploy. `CURRENT_STEP` chỉ Prime xem xét sau completion gate **toàn module**; pilot vài group không chốt tiến độ. STEP 9 pilot cũ không được dùng để refactor lại nội dung.

## 1. Prime — đọc Sheet, xác minh và dựng khung trước khi giao worker

1. Đọc `GENERAL_AGENT_RULES.md`, **toàn bộ** `rules/`, `STEP_8.md` và `worker/STEP_8_WORKER.md`; đọc `module-generate-info.txt`, xác minh URL/gid/tab, A1/B1/I3, đúng `MODULE_ROOT`/Gradle module, `master.json` (MODULE_TYPE, JAVA_BASE_PACKAGE, CURRENT_STEP), JDK và servlet/reactive runtime. Không suy module từ tên.
2. Ghép **nguyên văn** Case Study YAML từ `S20:U24`: theo hàng trên→dưới, trong hàng S→T→U, **không separator**. Parse `caseStudy[].groupID`, `cases[]`; đối chiếu groupID/index liên tục, duy nhất và đủ case. Với group `g`, đọc Phase 4 `S(27+3×(g−1)):U(29+3×(g−1))`, cùng quy tắc ghép, parse `codes[].caseIndex` và toàn bộ Java Markdown, đối chiếu theo **groupID + caseIndex (1-based)**, không thay bằng YAML module.
3. Lập ledger **toàn bộ group và case** trước khi viết: groupID, caseCount, danh sách caseIndex đầy đủ theo thứ tự, source range, code fence/canonical main, GET route và tên method. **Prime chịu trách nhiệm xác minh groupID và cấp đúng vùng đọc Sheet của group trong handoff; danh sách case chỉ dùng nội bộ để dựng/QA scaffold, không phải trường giao việc.** Worker tự đọc và kiểm đủ case của group từ nguồn được cấp. Tính `SOURCE_SHA256 = SHA256(UTF8(baseYamlJoined + "\n---STEP8-GROUP-PHASE4---\n" + groupCodeYamlJoined))` từ raw cell text, `CASE_CODE_SHA256 = SHA256(UTF8(raw case code Markdown))` cho từng case; worker phải tính lại và đọc lại nguồn khi bàn giao. Source sai hoặc drift → BLOCKER: STEP8_SHEET_SOURCE.
4. **Preflight worktree/index/ownership trước khi tạo khung.** Snapshot/hashes các Java/test đã có, xác minh route/bean/class collision và file từng group. Pilot cũ (`casestudy/gX/cY`), các class/Controller cũ hoặc code dirty không được tự xóa/move/ghi đè. Đích có file chưa xác minh quyền sở hữu → BLOCKER: STEP8_UNSAFE_WORKTREE_OR_MIGRATION, phải có hướng xử lý có kiểm chứng, không âm thầm clean/restore.
5. **Prime tạo mới hoặc bảo toàn/cập nhật có kiểm soát** các file khung của từng group, trước khi bất kỳ worker nào bắt đầu ghi. Prime mặc định sở hữu `casestudy/controller/Group<g>Controller.java` và các method route, header/bean/constructor/**signature + placeholder** của `casestudy/service/Group<g>Service.java`. Worker sở hữu **body của `executeCase<c>()`** đúng group và import/qualification kỹ thuật; **riêng `INPUT_REQUIRED`** được đổi tối thiểu signature đúng case và input binding Controller cùng group; **riêng `JVM_ISOLATION_REQUIRED`** được thêm guard/dispatch vào body đúng case, dùng hạ tầng Prime-owned. Không đổi class/bean/GET path/case khác.
6. **Một group = đúng 1 Controller + 1 Service; một case = đúng 1 route + 1 method.** Không tạo `CaseController`, `CaseService`, `GroupController` dùng chung tất cả group, không tạo cặp Controller/Service trong `groupX/caseY`, không gộp các group vào cùng class. Runtime SERVLET dùng Spring MVC, REACTIVE dùng WebFlux; thiếu HTTP runtime → BLOCKER: STEP8_MODULE_RUNTIME_POLICY, không tự chỉnh Gradle để ép.

### Cấu trúc production canonical

Ví dụ G2/C2, chỉ những package/type thực sự có trong nguồn mới được worker tạo:

~~~text
src/main/java/<BASE_PACKAGE_PATH>/
└── casestudy/
    ├── controller/
    │   ├── Group1Controller.java          # Prime owner
    │   └── Group2Controller.java          # Prime owner, 1 GET mỗi case
    ├── service/
    │   ├── Group1Service.java             # 1 executeCase<c>() mỗi case G1
    │   └── Group2Service.java             # Prime khung; worker G2 điền body
    ├── application/
    │   └── group2/case2/<OriginalUseCase>.java
    ├── domain/
    │   └── group2/case2/<OriginalDomainType>.java
    ├── model/
    │   └── group2/case2/<OriginalModel>.java
    ├── port/
    │   └── group2/case2/<OriginalInterface>.java
    ├── dto/                                # chỉ khi source có DTO
    │   └── group2/case2/<OriginalDto>.java
    └── utils/
        └── group2/case2/Case2Utils.java
~~~

Package đầy đủ cho helper/type ở case `g,c`: **`<JAVA_BASE_PACKAGE>.casestudy.<packageKind>.group<g>.case<c>`** (ví dụ `com.example.learning.casestudy.model.group2.case2`); `controller` và `service` là hai package trực tiếp dưới `casestudy`, **không có group/case segment trong chính package Controller/Service**. `group` và `case` viết nguyên chữ (`group2/case2`), **không dùng `g2/c2`**. Các lựa chọn package theo trách nhiệm: `application`, `domain`, `port`, `dto`, `model`, `adapter`, `repository`, `utils`; không ép mọi package xuất hiện.

## 2. Hợp đồng Controller/Service do Prime dựng

- Bean/Java name chuẩn: `Group<g>Controller`, `Group<g>Service`; tên bean độc lập nếu khai tường minh: `caseStudyGroup<g>Controller` và `caseStudyGroup<g>Service`. GroupService đăng ký một bean `@Service`, **không đặt @Service lên type nghiệp vụ gốc**; Controller dùng constructor injection với **GroupService cùng group**. Không chia sẻ service giữa group.
- Mỗi caseIndex `c` có duy nhất endpoint **`GET /case-studies/group/<g>/case/<c>/execute`**; **mặc định không có client input**, chỉ `INPUT_REQUIRED` cho phép request parameter ánh xạ đúng input source (kể cả trường hợp thiếu/sai nếu main gốc có nhánh xử lý; xem Worker §3B). Controller method `executeCase<c>()` chỉ gọi Service cùng case, không nhúng code nghiệp vụ. Không đổi URL sang `/groups`, `/api/v1/java`, POST/PUT.
- **`GroupService.executeCase<c>()` phải là `void`** theo `static void main(...)` gốc. Controller có thể `void` để gọi method đó; **không thay `System.out.println()`/assertion bằng `Map` hay HTTP response JSON**. Như vậy GET có thể không có response body; phần log/output của main vẫn giữ trong code.
- Prime tạo placeholder **fail-closed rõ ràng** (ví dụ ném `UnsupportedOperationException("STEP8 pending G2/C2")`) trong mỗi method đã khai báo, **không được dùng body rỗng, `return null`, hardcode `PASS` hoặc trả thành công giả**. Worker thay đúng body placeholder bằng thân main nếu chuyển được; case BLOCKER giữ marker fail-closed và ghi manifest, Prime không coi endpoint đó là PASS. Prime chỉ được thay signature/wiring theo contract (khi có bằng chứng), không tự điền hoặc sửa nghiệp vụ.
- Với `main(String[] args)` thực sự **sử dụng args**, hoặc code có side-effect process-global, network/file/DB thật, exit JVM, non-repeatable behavior: **đối chiếu catalog §3B của Worker trước khi kết luận BLOCKER**. Nếu khớp `INPUT_REQUIRED` thì mapping input tối thiểu; khớp `JVM_ISOLATION_REQUIRED` thì cô lập JVM con với hạ tầng dùng chung do Prime kiểm chứng. Các vấn đề khác không tự suy thành một trong hai ngoại lệ; không tự chọn default, đổi logic, inject fake. Chỉ khi không match/không giải quyết tương đương-an toàn mới ghi `BLOCKER: STEP8_EXECUTE_CONTRACT` / `STEP8_UNSAFE_OR_UNSUPPORTED_EXECUTION` có chứng cứ và giữ fail-closed, **không ghi HTTP PASS**.
- Prime có thể tạo **Controller HTTP tests** riêng cho các case đã chuyển an toàn sau barrier; worker chỉ viết tests riêng để xác minh behavior gốc. Cấm để một worker thay đổi Controller của Prime. Không chạy full Gradle/startup trước khi mọi worker release ownership.

**Hạ tầng chung cho `JVM_ISOLATION_REQUIRED` (chỉ khi có case khớp):** Prime sở hữu và kiểm chứng launcher/bootstrap dùng chung để JVM con gọi lại **đúng `Group<g>Service.executeCase<c>()` đã chứa body gốc**, không tạo runner riêng/Java source riêng/JAR riêng cho từng case. Worker chỉ dùng API dispatch đã được Prime xác minh tại phần đầu body của đúng case. Mỗi invocation cần JVM mới, marker child chống đệ quy không do HTTP điều khiển, classpath/packaging hợp lệ khi chạy Spring Boot JAR, timeout, giới hạn đồng thời, đóng/thu stdout-stderr và phát hiện lỗi exit code; không ảnh hưởng System Property/ClassLoader của Spring Boot JVM. Nếu worker báo `PRIME_SETUP_REQUIRED`, Prime bổ sung hạ tầng đúng ownership trước khi giao lại group/case cho cùng worker; **thiếu hạ tầng chưa phải là kết luận blocker nghiệp vụ**. Không mặc định cô lập các case bình thường.

Ví dụ phần khung G2 do Prime sở hữu (chỉ minh họa hai case đầu, thực tế phải tạo **đủ mọi case** có trên Sheet):

~~~java
@RestController("caseStudyGroup2Controller")
@RequestMapping("/case-studies/group/2")
public class Group2Controller {
    private final Group2Service service;

    public Group2Controller(Group2Service service) {
        this.service = service;
    }

    @GetMapping("/case/1/execute")
    public void executeCase1() {
        service.executeCase1();
    }

    @GetMapping("/case/2/execute")
    public void executeCase2() {
        service.executeCase2();
    }
}
~~~

~~~java
@Service("caseStudyGroup2Service")
public class Group2Service {
    public void executeCase1() {
        throw new UnsupportedOperationException("STEP8 pending G2/C1");
    }

    public void executeCase2() {
        throw new UnsupportedOperationException("STEP8 pending G2/C2");
    }
}
~~~

Đây là **hai file riêng** trong hai package `controller` và `service`. Khi worker G2 chuyển được một case, worker chỉ thay **phần thân** method tương ứng trong `Group2Service`; không tạo class Service mới. Import, annotation, package và cấu hình Spring phù hợp với runtime của module phải được Prime bổ sung khi tạo khung; ví dụ rút gọn chỉ thể hiện mapping.

## 3. Worker — chuyển nguyên Java từng case vào Service group và package

Worker thuộc một group g phải **xử lý đủ mọi case c** (kể cả case BLOCKER/NOT_APPLICABLE trong manifest); một worker không nhận hai group đồng thời, không chia assignment theo case.

1. Đọc trực tiếp nguồn Sheet/Phase 4, SHA nhóm và case, đọc cấu trúc GroupController/GroupService do Prime đã tạo **để xác minh method mapping và ownership**. Nếu thiếu khung, endpoint, method signature, hoặc index khác Sheet → BLOCKER: STEP8_PRIME_SCAFFOLD; worker không tự dựng Controller/khung, **ngoại lệ chỉ sửa input binding đúng case `INPUT_REQUIRED`**.
2. Với một chương trình runnable có `public static void main(String[] args)`, di chuyển **nguyên phần thân** `main` sang **`Group<g>Service.executeCase<c>()`**, thứ tự statement giữ nguyên. Chỉ điều chỉnh package/import/qualification/static call tối thiểu và format thuần túy theo §3A của worker. `JVM_ISOLATION_REQUIRED` cho phép thêm guard/dispatch **phía trước** phần main gốc mà không thay nội dung/thứ tự khối gốc. Với `main void`, **không sinh kiểu trả về**. Không làm biến đổi output/behavior, không xóa `check()`, `System.out` hoặc code unused.
3. Tách mọi nested/static type/class/interface/record/enum nguồn thành top-level file tại `casestudy/<packageKind>/group<g>/case<c>/<OriginalType>.java`. Ghi mapping source type → file đầy đủ; giữ name/constructor/field/instance method/static semantics. Không tự thêm type mới để “đúng kiến trúc”; không tái cấu trúc thuật toán.
4. Mọi static helper method ngoài `main()` vào `casestudy/utils/group<g>/case<c>/...`; giữ nguyên method body, chỉ cập nhật call-site. Static fields/initializers không được đổi lifecycle; nếu extraction làm hỏng semantics, blocker thay vì tự sửa logic. **GroupService không chứa nested type hoặc helper static gốc**.
5. Cú pháp, lỗi compile/runtime thật sự mới là ngoại lệ cho sửa nội dung: lưu BEFORE/AFTER/error log/root cause/minimal fix/test trong manifest. Nếu không chứng minh được giữ semantics → BLOCKER: STEP8_CONTENT_CHANGE_UNPROVEN. Các đoạn BEFORE/AFTER/TEST và compile-fail trong Markdown là nguồn để hiểu bằng chứng, không được tự chạy/ghép thành business demo mới.
6. Test production behavior đúng code gốc, output/exception/assertions/repeatability khi an toàn. Gặp nguy cơ blocker phải **đối chiếu toàn bộ danh sách điều kiện catalog §3B của Worker**, match thì xử lý và test; không có mục phù hợp mới báo blocker thật có chứng cứ. Worker **không** tự sửa Controller ngoài input binding được phép, `GroupService` group khác, Spring Application, Gradle, YAML/Sheet/master, importer, Git hoặc file test do Prime sở hữu.
7. Worker ghi **một manifest per group** trong ignored `build/step8/<RUN_ID>/g<g>/step8-group-manifest.json`, gồm source/case SHA, endpoint/method mapping, type/static helper mapping, original main statements và đối chiếu, file/hash, ERROR_FIX log, blockers, test evidence, `FORMAT_IMPORT_QA` và **`BLOCKER_RESOLUTION` (normal attempt, toàn bộ quy tắc hiện hành đã xét, các mục khớp, xử lý, test, kết luận)**. Xác nhận Controller không đổi hoặc chỉ đổi input binding được audit. Bắt buộc exact readback mọi file/manifest/nguồn trước release group.

## 4. Assignment bốn dòng, ownership và barrier

Prime lập `RUN_ID`, source ledger, snapshot trước khi giao; worker pool theo `PRIME_RULES.md` (Mode A tối đa 3, Mode B theo số người dùng xác định; ưu tiên SLEEPING; spawn **tuần tự từng worker**). **Đơn vị WRITE = một group**, mỗi group chỉ có một worker, mọi case dưới group đó cùng owner. **Prime cấp `UNIT=G<groupID>` và đúng vùng Phase 4 của group**, worker tự kiểm kê toàn bộ case trong group từ Sheet. Sau khi worker release, nếu QA fail, giao lại đúng worker group ấy.

~~~text
<VERIFIED_SHEET_TAB_URL>
READ=A1,B1,I3,S20:U24,<EXACT_GROUP_PHASE4_RANGE>;UNIT=G<groupID>;RUN=<RUN_ID>;SOURCE_SHA256=<GROUP_SOURCE_SHA256>
WRITE=<EXACT_GROUP_SERVICE_FILE>,<EXACT_CASESTUDY_MAIN_ROOT>,<EXACT_CASESTUDY_TEST_ROOT>,<EXACT_IGNORED_GROUP_EVIDENCE_DIR>
/java-learning/module-generate-agent/worker/STEP_8_WORKER.md
~~~

**Ví dụ giao Group 2:** `READ=A1,B1,I3,S20:U24,S30:U32;UNIT=G2;RUN=<RUN_ID>;SOURCE_SHA256=<GROUP_SOURCE_SHA256>`. Worker dựa trên `UNIT=G2` và nguồn Sheet này để tự kiểm kê các case của G2, rồi đối chiếu 1:1 với Phase 4 và các method `executeCase<c>()` do Prime dựng. Thiếu/thừa/trùng case hoặc mismatch group/vùng đọc → `BLOCKER: STEP8_GROUP_CASE_ASSIGNMENT`; **không tự chọn lại group hoặc thay vùng đọc**.

WRITE root `casestudy` **không cấp quyền ghi toàn root**: worker mặc định sửa body `executeCase<c>()` và imports kỹ thuật trong `service/Group<g>Service.java`, tạo/điều chỉnh **`<packageKind>/group<g>/case<c>/`** cho g được giao, tests ở `src/test/java/<BASE>/casestudy/tests/group<g>/case<c>/`, manifest trong đúng `g<g>/` RUN. **Controller và signature chỉ được đổi input binding của case khớp `INPUT_REQUIRED`; các Service group khác READ-ONLY**. Hạ tầng JVM con do Prime quản lý; worker báo `PRIME_SETUP_REQUIRED` khi thiếu, không tự can thiệp cấu hình toàn module. Mọi file/handoff thiếu hoặc chồng ownership → BLOCKER: STEP8_WORKER_OWNERSHIP.

## 5. Prime QA sau khi workers bàn giao

Prime đã có quyền **chủ động tạo scaffold Controller/Service trước workers**, không còn quy định "Prime chỉ QA và tuyệt đối không tạo Controller" như pilot trước. **Sau handoff, Prime trở lại vai trò QA**, không tự sửa thân logic worker. Prime có thể kiểm tra và sửa **chính scaffold HTTP/placeholder do mình sở hữu** nếu có lỗi kỹ thuật có bằng chứng, tuyệt đối không rewrite main hoặc type gốc.

Checklist:
- Sheet source/hash/module mapping, `UNIT` và **vùng đọc mỗi task do Prime cấp** khớp groupID, worker kiểm kê toàn bộ case và đối chiếu 1:1 với Sheet + scaffold + manifest, **groupCount** Controller = Service, **mỗi group đúng 1 cặp**, route/method/count phủ đủ case, URL/bean uniqueness, **client input chỉ khi `INPUT_REQUIRED` được audit**.
- Mapping **main của Gg/Cc → Group<g>Service.executeCase<c>**, đúng thứ tự statements; static helpers vào `utils/groupg/casec`; mọi original types vào `<kind>/groupg/casec`; GroupService không có nested/static helper, không `DemoResult` hoặc invented behavior.
- Source→target fidelity, `BLOCKER_RESOLUTION` chứng minh worker **đã xét đầy đủ danh sách điều kiện hiện hành trước khi kết luận blocker thật**; các case được giải quyết bằng quy tắc có trong danh sách có code/test tương đương, không chuyển thành PASS chỉ dựa vào thiết kế. Blocked cases giữ fail-closed placeholder và lý do; Controller không chứa logic; mọi file/test/hash, audit minimal error fix, manifest readback PASS.
- **FORMAT + IMPORT CLEANUP gate:** Prime phải kiểm tra `FORMAT_IMPORT_QA` từ mỗi group worker: Service/type/utils/test được format dễ đọc, không dồn nhiều statement trên một dòng; import ngắn gọn khi không xung đột, giữ FQCN có lý do khi các case trùng simple name; không dùng wildcard import để né collision. Đối chiếu token/hành vi sau format, kiểm compile/test và xem các ngoại lệ; **không coi compile PASS là đủ nếu code vẫn khó đọc hoặc chưa QA format/import**. Quyền ghi vẫn giới hạn theo group và hai ngoại lệ catalog hiện hành.
- **Sau barrier** mới chạy module compile, scoped/full tests, Spring startup, GET smoke đúng đường dẫn, kiểm repeatability/safety chỉ những case runnable; với `JVM_ISOLATION_REQUIRED`, Prime kiểm launcher/classpath của artifact triển khai, ít nhất hai request tuần tự và failure/timeout không PASS, state JVM cha không đổi. Blocked cases không tính HTTP PASS. Tách baseline legacy compile lỗi khỏi lỗi STEP 8; đừng sửa ngoài ownership để làm xanh test.
- Cả module phải hoàn thành đủ case và QA không blocker mới xét `CURRENT_STEP.VALUE="8"` theo `rules/PRIME_RULES.md §2`. Pilot 3 group không được chốt STEP. Không tự chạy STEP9, import Sheet, commit/push/deploy.

**Prime report:** RUN/module/gid, group/case total, hashes, owner, `ASSIGNED_GROUP_AND_SHEET_RANGE`, `GROUP_CASE_INVENTORY_VS_SHEET_MATCH`, `PRIME_SCAFFOLD_CONTROLLER_SERVICE_COUNTS`, `GROUP_METHOD_ENDPOINT_COVERAGE`, `GROUP_SERVICE_BODY_FIDELITY`, `TYPE_STATIC_PACKAGE_MAP`, `CONTENT_ERROR_FIX_AUDIT`, `BLOCKER_RESOLUTION_CATALOG_QA`, `BLOCKED_FAIL_CLOSED`, `COMPILE/TEST/HTTP`, `SHEET/YAML_UNCHANGED`, `OUT_OF_SCOPE_UNCHANGED`, `CURRENT_STEP_BEFORE/AFTER`, BLOCKERS.
