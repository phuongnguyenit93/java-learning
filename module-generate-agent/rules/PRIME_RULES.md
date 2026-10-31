# PRIME_RULES.md — Quy định bắt buộc dành cho Prime

Áp dụng cho Prime khi điều phối bất kỳ STEP nào của `module-generate-agent/`. Đây là một phần của bộ quy định chung trong `module-generate-agent/rules/`, không phải file STEP. Prime phải đọc toàn bộ các file hiện có trong thư mục `rules/` cùng `module-generate-agent/GENERAL_AGENT_RULES.md` và file STEP hiện hành **trước mỗi STEP**; nếu context không còn đầy đủ hoặc không chắc chắn, phải đọc lại theo General Rules.

## 0. Worker modes — chỉ áp dụng trong session được đính kèm mode

Chỉ có **hai mode**. Prime áp dụng giới hạn tổng worker `ACTIVE` + `SLEEPING` của session:

| Mode | Giới hạn pool |
| --- | --- |
| **Mode A — Normal Mode** | **Tối đa 3 worker** |
| **Mode B — Special Mode** | **Tối đa N worker**, với `N` là số nguyên dương người dùng yêu cầu rõ ràng trong session |

- Không chỉ định mode → **mặc định Mode A = 3**. Đây là giới hạn pool tối đa; một STEP có ít nhiệm vụ hơn thì không tạo worker thừa.
- Chỉ định **Mode B** nhưng **không cung cấp N, hoặc N không phải số nguyên dương** → `BLOCKER: SPECIAL_MODE_WORKER_COUNT_REQUIRED`: **dừng điều phối/spawn và yêu cầu người dùng cung cấp số worker**. Không tự dùng 3, 6, 8 hay số mặc định khác. Mode B không có hạn mức cố định ngoài N người dùng yêu cầu và giới hạn công cụ thực tế.
- Mode chỉ hiệu lực **trong session hiện tại đã nhận prompt đó**; không ghi mode thành cấu hình mặc định, không tự mang sang session/chat/STEP độc lập khác. Nếu context bị compact, chỉ tiếp tục mode và N có bằng chứng trong chính session hiện tại.
- Nếu một prompt chứa các mode hoặc số worker mâu thuẫn, báo `BLOCKER: WORKER MODE CONFLICT`, không tự chọn. Prime theo dõi và báo `SESSION_WORKER_MODE`, `MAX_WORKERS`.
- Vẫn **ưu tiên tái sử dụng worker SLEEPING**, không spawn khi còn worker SLEEPING, không vượt quyền sở hữu vùng ghi. Worker `FAILED`/`FINISHED` không thể tái sử dụng không tính vào pool.
- Mọi giới hạn “mặc định 3” ở `GENERAL_AGENT_RULES.md` hoặc các file STEP phải được hiểu theo mode của **session hiện hành**; N của Mode B được chỉ định có ưu tiên hơn hạn mức mặc định, không được áp dụng Mode B cho session khác.

### Bắt buộc tạo worker lần lượt, từng worker một

1. Trước khi tạo worker, Prime kiểm tra trạng thái pool, số worker `ACTIVE` + `SLEEPING`, khả năng tái sử dụng, số nhiệm vụ chờ và giới hạn mode. Mode B thiếu N hợp lệ phải dừng theo blocker phía trên **trước lệnh spawn đầu tiên**.
2. Khi cần tạo mới, **mỗi lệnh spawn chỉ được yêu cầu đúng 1 worker** (`workers` chứa đúng một phần tử). **Không tạo nhiều worker trong cùng một lệnh, không gọi nhiều lệnh spawn song song**, kể cả với yêu cầu số lượng lớn.
3. **Chờ từng lệnh spawn trả kết quả**, kiểm tra worker đã khởi tạo thành công và cập nhật trạng thái pool rồi mới quyết định tạo worker tiếp theo. Nếu gặp lỗi hoặc queue đầy, xử lý/báo blocker trước khi spawn lại; không lặp lại hàng loạt lệnh thất bại.
4. Lặp lại tuần tự cho đến khi đạt số worker được yêu cầu/được phép theo mode **và còn nhiệm vụ độc lập cần giao**. Luôn ưu tiên worker `SLEEPING`; không tạo worker dư chỉ để đủ số nếu không còn việc. Giữ nguyên giới hạn ownership và giới hạn riêng của STEP.

## 1. Prime phải điều phối đến khi hoàn thành TOÀN BỘ STEP

1. **Giao việc cho worker KHÔNG đồng nghĩa STEP đã hoàn thành.** Prime tuyệt đối không kết thúc phiên/chốt câu trả lời chỉ vì đã giao xong đợt việc đầu tiên (ví dụ giao cho 3 worker). Prime giữ trách nhiệm điều phối cho đến khi **toàn bộ phạm vi STEP được xử lý và xác minh**.
2. Trước khi giao, Prime lập danh sách đầy đủ các đơn vị công việc phải xử lý (module, chương, bài hoặc hạng mục tùy STEP), xác định đơn vị đã hoàn thành, còn thiếu, đang chạy, `BLOCKER`/`FAILED` và worker chịu trách nhiệm. Không chỉ lập kế hoạch cho số đơn vị vừa đủ lấp đầy pool ban đầu.
3. Sau mỗi đợt giao việc, Prime **tiếp tục theo dõi trạng thái và tiếp nhận báo cáo**. Khi một worker hoàn thành và đã giải phóng ownership, Prime **ưu tiên giao ngay đơn vị công việc kế tiếp cho chính worker có thể tái sử dụng**, cho tới khi hàng đợi công việc của STEP rỗng; vẫn giữ giới hạn pool và các quy tắc tái sử dụng trong General Rules.
4. Nếu mọi worker còn đang chạy, Prime duy trì vai trò điều phối, sử dụng các checkpoint/trạng thái **thụ động, không gây thay đổi** với nhịp phù hợp. **Không ping worker đang chạy chỉ để tạo heartbeat, không gửi nhiệm vụ trùng lặp, không spawn thêm để vượt giới hạn, không tự kết luận worker `SLEEPING` là còn thực thi.** Chỉ giao tiếp khi có nhiệm vụ mới, kết quả cần xử lý hoặc sửa lỗi có căn cứ.
5. Khi có `BLOCKER`/`FAILED`, Prime kiểm tra bằng chứng và trạng thái thực tế, xác định phần nào đã làm xong để **chỉ điều phối phần còn thiếu hoặc sửa lỗi**, không làm lại/ghi đè tùy tiện. Nếu thực sự không thể tiếp tục an toàn, nêu blocker cụ thể, phạm vi bị ảnh hưởng và việc chưa hoàn thành; **không tuyên bố STEP hoàn tất**.
6. **Chỉ được tuyên bố COMPLETED khi toàn bộ đơn vị công việc thuộc STEP đã đạt điều kiện hoàn thành của chính STEP đó**, bao gồm review, kiểm tra dữ liệu và readback khi bắt buộc. Prime phải tự đối chiếu kết quả thực tế; không coi việc worker báo xong hoặc danh sách phân công hết lượt là bằng chứng đầy đủ.
7. Sau khi hoàn tất và xác minh toàn STEP, Prime thực hiện việc cập nhật `Current STEP` theo mục 2 **trước khi đóng phiên/báo hoàn tất**. Không tự chạy STEP tiếp theo hoặc commit/push nếu nhiệm vụ hiện tại không cho phép.

## 2. Cập nhật CURRENT_STEP trong master.json khi STEP hoàn tất

**Nguồn duy nhất** của tiến độ đã hoàn thành là đúng `<MODULE_LOCATION_I3>/master.json`, khóa canonical `CURRENT_STEP`:

```json
"CURRENT_STEP": {
  "DESCRIPTION": "Last successfully completed module generation step (0, numeric STEP including 13, FINAL)",
  "TYPE": "string",
  "VALUE": "0"
}
```

- Giá trị **JSON string**. Thứ tự STEP hiện hành: **0 → 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 15 → FINAL**. STEP 6/7 dùng importer đồng bộ canonical I/C/Q; **STEP 8 mới** Prime tạo một GroupXController/GroupXService mỗi group trước worker, một GET/executeCaseY mỗi case; worker một group/lượt điền nguyên thân main và tách types/static helpers vào `<kind>/groupX/caseY`; Prime QA sau worker, không DTO/logic mới, không Phase2 hoặc ghi Sheet/YAML. **STEP 9 cũ đồng bộ ngược module→file/Sheet đã hủy**. **STEP 9 pilot cũ** chưa hợp STEP 8 mới và phải viết lại thành kiểm fidelity/package trước khi chạy; Sheet READ-ONLY, Prime QA. Trạng thái 6/7/8/9 lịch sử phải kiểm provenance và không tự coi "8" là STEP 8 mới đã hoàn tất. "12A" chỉ đọc tương thích; không cập nhật từ Sheet-only QA.
- **Không đọc hoặc ghi trạng thái tiến độ ở Google Sheets/Excel J2/J3**, dù các tab/template cũ vẫn giữ nhãn `Current STEP`. Google Sheets `I3` vẫn được dùng để xác minh đường dẫn module; không phải nguồn trạng thái. Mọi backfill lịch sử phải có phạm vi và phép đối chiếu riêng được người dùng yêu cầu; không tự reset module về `"0"` hoặc cập nhật hàng loạt.
- **MỌI STEP bắt buộc** trong chuỗi tạo module (STEP 1–9, 10–15 và STEP_FINAL) **đều phải có checkpoint cập nhật hoặc bảo toàn `CURRENT_STEP.VALUE` trong `master.json` của đúng module ngay sau khi hoàn thành**, trước khi Prime báo `COMPLETED`; bước nào có import/sync bắt buộc phải qua QA + DRY_RUN → APPLY + exact readback trước checkpoint. Chỉ Prime cập nhật sau completion gate **của chính STEP và module**; worker không có quyền chỉnh trạng thái. Rerun STEP cũ không được ghi lùi; Video tùy chọn sau FINAL luôn giữ `"FINAL"`. Không đồng nghĩa rằng importer tự ghi trạng thái: **việc này luôn là bước Prime riêng sau nghiệm thu**, không phải side effect của task import.

1. Xác minh đúng spreadsheet/tab/gid và `I3` qua mapping `module-generate-info`, đường dẫn thực nằm trong `java-learning/module/`, đúng module/worktree và đúng duy nhất một `master.json`. STEP 1 chỉ xác nhận được mapping sau khi clone/lưu tab mới.
2. Trước khi chạy, đọc/snapshot `CURRENT_STEP.TYPE` và `VALUE` trong file. `TYPE` phải là `string`, VALUE phải hợp lệ. Nếu khóa chưa tồn tại, chạy canonical module configuration sync để thêm `"0"` và exact readback; không tự chế cấu trúc. Nếu VALUE sai kiểu/schema hoặc có công việc đồng thời, báo `BLOCKER: CURRENT_STEP_UPDATE`, không ghi đoán.
3. Chỉ khi **toàn bộ** worker/import/build/test, QA, cleanup và exact readback đạt PASS/UNCHANGED, không còn pending hoặc blocker, Prime mới được cập nhật **duy nhất `CURRENT_STEP.VALUE`** sang string của STEP mới. STEP 4 giữ NEW_FILL ONLY, STEP 5 mới review Knowledge; STEP_FINAL phải qua đầy đủ gate Git. Với nhiều module, QA và ghi từng module độc lập, không ghi theo tên tab suy đoán.
4. Áp dụng thứ tự explicit ở trên, **không so sánh string theo từ điển**. Nếu trạng thái hiện tại đi xa hơn, giữ nguyên sau khi rerun STEP cũ. Nếu phát hiện historical `"8"`/STEP khác do đổi số, kiểm chứng provenance/output trước khi quyết định tiến độ; không tự dịch sang STEP hiện tại.
5. Snapshot trước khi ghi, chống sửa đồng thời, dùng patch/atomic replace an toàn hoặc backup/rollback, **giữ nguyên toàn bộ các key và VALUE khác**. Exact readback trên `master.json`, không ghi vào ô Excel/Sheet; thất bại phải báo blocker và không tuyên bố hoàn tất toàn STEP.
6. Báo `CURRENT_STEP_MODULE_PATH`, `CURRENT_STEP_BEFORE`, `CURRENT_STEP_AFTER`, `CURRENT_STEP_READBACK=PASS/FAIL`, blocker nếu có. STEP_FINAL chỉ ghi `"FINAL"` sau khi đủ QA và đưa cập nhật này vào commit/push kết thúc. Các STEP_OPTIONAL_VIDEO/TRANSLATE không đổi `CURRENT_STEP`.

**Riêng STEP 6/7 mới:** cuối STEP 6 Prime import đủ Interview/Case Study/Quiz bằng `generateModuleAssessmentsFromSheetByLocation`, QA và chốt `"6"` khi hợp lệ; cuối STEP 7 Prime chạy **lại đúng task đó** sau khi ba `anchorList` và cleanup Sheet PASS, rồi QA và chốt `"7"` khi hợp lệ. Task chỉ tạo ba YAML module `content/{interview,casestudy,quiz}/vi/`; Portal và learning-platform sẽ xử lý sau. Tuyệt đối không sử dụng importer legacy P:S/T:W hoặc tự cập nhật tiến độ từ một kết quả Sheet-only. Với module mang `"8"` lịch sử, bắt buộc kiểm provenance và giữ nguyên nếu không có quyết định migration riêng, không ghi `"6"`/`"7"` lùi.

**Riêng STEP 8 mới:** Prime preflight Sheet/worktree rồi **trước worker tạo chính xác một** `casestudy/controller/GroupXController.java` và một `casestudy/service/GroupXService.java` **mỗi group**. Mỗi case có một GET `/case-studies/group/x/case/y/execute` và một placeholder fail-closed `void executeCaseY()`. **Prime chỉ giao groupID và vùng đọc Sheet chính xác**: handoff `UNIT=Gx`, nguồn nền `S20:U24` và Phase 4 tương ứng. **Không giao `CASES`/`CASE_COUNT`**; worker đọc Sheet để kiểm kê tất cả case trong đúng group được giao, đối chiếu với scaffold. Prime vẫn chịu trách nhiệm lập ledger group/case và dựng scaffold đủ endpoint/method, QA lại danh sách sau worker; mismatch → `BLOCKER: STEP8_GROUP_CASE_ASSIGNMENT`. Prime sở hữu Controller/bean/header/signatures; worker chỉ viết body executeCaseY/imports tối thiểu cho group được giao và original type/static helper tại `<kind>/groupX/caseY`. Một worker sở hữu trọn một group (không `UNIT=Gx/Cy`); không chia group cho worker khác. Không CaseController/CaseService riêng case, không invented DemoResult/logic. Sau barrier Prime QA-only với logic worker, được sửa chỉ scaffold Prime-owned; lỗi worker trả đúng owner group. Source Sheet bất biến; có blocker thì không khai HTTP PASS; pilot không chốt CURRENT_STEP.

**Luồng dữ liệu Portal:** `<module>/master.json` → `ProjectStructureService` → `module-catalog.json.currentStep` → `build-module-navigation.mjs` → `module-navigation.json` → Module Picker. FE ẩn `"0"`/field chưa có, hiển thị `Step N` (bao gồm `Step 13`; `Step 12A` chỉ dành cho dữ liệu cũ) và `Finished` cho `"FINAL"`. Sau khi cập nhật trạng thái cần regenerate/publish Java data, sync vào learning-platform và deploy mới thấy trên web.

## 3. Checklist đóng STEP cho Prime

**STEP 8 BLOCKER-RESOLUTION CATALOG (ưu tiên hơn các câu cấm tuyệt đối cũ):** Worker **chuyển code bình thường trước**. Chỉ khi có nguy cơ BLOCKER mới đối chiếu **toàn bộ danh sách quy tắc hiện hành** trong `worker/STEP_8_WORKER.md §3B`. **Danh sách có thể bổ sung về sau, không giới hạn cố định hai mục**; hiện có (1) `INPUT_REQUIRED` — sửa tối thiểu parameter Service + input binding Controller cùng group, đúng dữ liệu `main(args)`, không default và không đổi GET route/logic; (2) `JVM_ISOLATION_REQUIRED` — giữ main body trong `executeCaseY`, chỉ thêm guard/dispatch ở đầu method để chạy method đó trong JVM con mới, dùng launcher/bootstrap dùng chung do Prime sở hữu. Worker không tự sửa Gradle, Spring Application hoặc tạo JAR/runner riêng cho case. Nếu thiếu hạ tầng cô lập, worker báo `PRIME_SETUP_REQUIRED` để Prime cung cấp rồi tiếp tục; không tự biến thiếu setup thành blocker thật. Chỉ khi **không có quy tắc hiện hành phù hợp**, hoặc phương án tương ứng không an toàn/tương đương có bằng chứng, mới kết luận BLOCKER. Worker không tự thêm quy tắc ngoài danh sách; các mục mới cần được cập nhật/phê duyệt. Manifest phải có `BLOCKER_RESOLUTION` liệt kê mọi quy tắc đã xét và test đúng loại; Prime QA sau barrier.

**STEP 8 PRIME SCAFFOLD + QA:** Prime tạo/cập nhật có kiểm soát đúng một GroupController/GroupService per group trước assignment; sau worker release Prime QA source fidelity, routing, tests/HTTP. Mặc định worker không sửa Controller/signature, chỉ điền method body và tách code gốc theo `<kind>/groupX/caseY`; ngoại lệ `INPUT_REQUIRED` cho phép sửa input đúng case/group, `JVM_ISOLATION_REQUIRED` cho phép thêm dispatch dùng hạ tầng Prime-owned. Prime chỉ sửa scaffold và launcher/bootstrap do mình sở hữu, không rewrite thân main. Blocked case giữ fail-closed sau catalog QA. Không nâng CURRENT_STEP từ pilot.

**STEP 9 — CONTRACT PILOT CŨ CHƯA HỢP STEP 8 MỚI:** Không chạy contract STEP 9 hiện có để xóa/đổi logic gốc, bởi STEP 8 mới bảo toàn tuyệt đối mọi statement/type/method, static helpers vào utils và không worker Controller. Chỉ khi STEP 9 được viết lại thành kiểm chứng fidelity/package/exception-fix thì mới điều phối. Prime QA-only và không nâng CURRENT_STEP từ pilot.

```text
Đã nắm đủ General + Prime + STEP hiện hành (đọc lại nếu quên)?
    ↓
Đã xử lý hết toàn bộ danh sách công việc, không chỉ đợt giao đầu tiên?
    ↓
Đã nhận báo cáo, xác minh kết quả và giải quyết mọi BLOCKER/FAILED?
    ↓
Đã xác nhận hoàn thành theo điều kiện thực tế của STEP cho từng tab?
    ↓
Đã ghi đúng CURRENT_STEP.VALUE trong master.json và exact readback PASS trên từng module hoàn thành?
    ↓
Mới báo COMPLETED và kết thúc phiên STEP
```

**Không đánh dấu hoàn thành chỉ từ việc đã giao task cho worker. Không cập nhật CURRENT_STEP trước khi STEP thực sự hoàn tất; không ghi tiến độ vào Excel.**
