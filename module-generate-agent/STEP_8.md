 STEP 8 — PRIME ONLY: Real-world Problems & Practical Exercises

> **STEP 8 content/import contract.** Worker tạo nội dung vào Google Sheets `AD:AG` theo hai phase; **Gradle importer chỉ dùng code**, không gọi AI. Đã có schema + code importer một-file và đường dẫn STEP 13–15. Phải QA đúng tab, module, toàn bộ chương và chạy DRY_RUN trước APPLY. Template/Portal hoàn thiện và thử nghiệm end-to-end là gate riêng; không cập nhật `CURRENT_STEP.VALUE="8"` chỉ vì code đã tồn tại.

## 0. Phạm vi và nguyên tắc

- STEP 8 tạo **vấn đề thực tế → phân tích ứng dụng kiến thức → bài tập → lời giải tham khảo**. Không sinh Summary Questions (STEP 9), Interview, Quiz, Knowledge, Swagger hay Video.
- **One Worker = One Menu Cấp 1 = One Session với hai phase liên tiếp**: Phase 1 xác định số scenario và ghi **toàn bộ skeleton** vào Sheet; sau exact readback PASS, Phase 2 bổ sung nội dung **cho chính toàn bộ skeleton ấy**, cũng trong phiên hiện tại. Không handoff sang worker khác, không đợi Prime duyệt giữa hai phase.
- **Worker tự chọn số lượng** và tính đa dạng của tình huống, không quota và không ép một scenario/Menu Cấp 2 hoặc một lĩnh vực nghiệp vụ. Phải có vấn đề cụ thể và khả năng dùng kiến thức chương để xử lý. Prime chỉ kiểm tra độ phủ, trùng lặp và chất lượng **sau khi worker hoàn thành**.
- **Prime chỉ giao tọa độ ô/vùng đọc và ghi, KHÔNG chép tên các Menu Cấp 1/Cấp 2 vào prompt.** Worker **tự đọc** `A1`, `B1`, `A19`, `B19`, **chỉ cột A** của toàn menu để biết ranh giới các Menu Cấp 1, **chỉ cột B** ở những hàng Menu Cấp 2 thuộc chương được giao, header `AD18:AG19` và `AD:AG` của hàng cha mình sở hữu. Đây là **nguồn đọc ban đầu cho Phase 1**. Phase 2 đọc lại **chính AD:AG hàng cha**, không mở rộng phạm vi. Cấm đọc vùng sinh Knowledge `C:M` (kể cả cột `N`), Difficulty `O`, STEP 5 Summary `AC`, Interview `P:S`, Quiz `T:W`, README, source/test, Swagger/API, kết quả worker khác hoặc ngữ cảnh nội dung STEP trước. Prime không gửi ví dụ nghiệp vụ/đáp án làm lệch kết quả.
- Có thể chọn `BUSINESS` hoặc `ENGINEERING`; **không bắt buộc bài tập viết code**. Git, JVM, kiến trúc và các chủ đề lý thuyết có thể dùng phân tích, thực hành CLI, thiết kế workflow, quyết định/trade-off với rubric chấm được.
- AI chỉ tạo nội dung. Parse YAML, split/join fragments, schema validation, import, snapshot/rollback, clone/translation/publish là **code thuần**; Prime kiểm tra ngữ nghĩa và đáp án.

## 1. Google Sheets — chính tab module, cột AD:AG

| Cột | Số 1-based | Vai trò |
|---|---:|---|
| AD | 30 | YAML fragment 1 |
| AE | 31 | YAML fragment 2 |
| AF | 32 | YAML fragment 3 |
| AG | 33 | YAML fragment 4 |

- Setup/template dự kiến: `AD18 = Real-world Practice`, `AD19:AG19 = Practice YAML` (cùng header), **bắt buộc grid >= 33 cột**. Đây là schema mới, cần migration an toàn vì hiện template chuẩn chỉ `A:AC`; **worker không được tự thêm cột/đổi header**.
- **Chỉ `AD<PARENT_ROW>:AG<PARENT_ROW>`** thuộc đúng một Menu Cấp 1 được chứa **một** YAML root `scenarios:`. Không điền vào các hàng Menu Cấp 2; không tạo/dời hàng hoặc tràn sang AH. Bốn cell là các fragment **nối AD→AE→AF→AG không separator**, không phải bốn loại nội dung.
- **Mỗi ô tối đa 30.000 ký tự, không chia đều theo bốn ô.** Tạo **một chuỗi YAML hoàn chỉnh**, nếu dài ≤30.000 ký tự thì **chỉ ghi AD**, AE:AG trống. Nếu dài hơn, **lấp đầy AD tới ranh giới dòng cuối cùng trước ngưỡng 30.000** rồi tiếp tục sang AE, AF, AG. **Không cắt giữa từ/key/YAML line** khi có ranh giới dòng hợp lệ; không chủ ý chia một YAML ngắn thành 4 phần. Đồng thời giữ trần **32.767 UTF-16 code units/ô** cho Excel và không tách cặp surrogate. Sau chia phải nối lại bằng `AD+AE+AF+AG` **không separator**, kiểm tra bằng đúng YAML gốc **trước ghi và sau exact readback**. Nếu tổng YAML không fit bốn ô, `BLOCKER: STEP8_CAPACITY`; không tóm tắt/cắt mất đáp án hoặc ghi ra cột ngoài.
- Prime xác định hàng cha, con từ `A:B`, xác minh `fileUrl + gid, A1/B1, I3` khớp module thật dưới `java-learning/module/`. Prime tạo ledger **toàn bộ** các Menu Cấp 1 và cấp **chính xác hai vùng một cột**: `A<FIRST_PARENT>:A<LAST_MENU_ROW>` cho tên Menu Cấp 1 toàn module và `B<PARENT_ROW+1>:B<LAST_CHILD_ROW>` cho tên Menu Cấp 2 của đúng chapter. Không gửi `A:B` toàn module hay vùng `C:M`/ `C:N` cho worker, không suy vị trí từ tên tab.
- Trước mọi write: preflight `userEnteredValue`, formula, effective/formatted value, notes, validation/chip, owner; snapshot/fingerprint nguồn; ngăn race. Không ghi đè nội dung hợp lệ hoặc chỉnh vùng khác.

## 2. DRAFT YAML — chỉ skeleton trong Phase 1

Worker trước tiên tự quyết định **toàn bộ danh sách** tình huống; phải ghi **tất cả skeleton trong một lần phase**, không vừa viết scenario thứ nhất vừa triển khai lời giải của nó.

```yaml
scenarios:
  - id: scenario-01
    type: ENGINEERING
    title: "Tên một vấn đề thực tế cụ thể"
    sourceKind: REALISTIC_SIMULATION
    exercise:
      type: WORKFLOW_DESIGN
  - id: scenario-02
    type: BUSINESS
    title: "Một vấn đề khác, có giá trị riêng"
    sourceKind: REALISTIC_SIMULATION
    exercise:
      type: ANALYSIS
```

**Mỗi scenario chỉ có đúng năm field**: `id`, `type`, `title`, `sourceKind`, `exercise.type`. Không có `context`, `problem`, `analysis`, `requirements`, `deliverables`, `acceptanceCriteria`, `hints`, `referenceSolution` (kể cả rỗng/null/placeholder). Không thêm `status/count/version/related` hoặc metadata.
- `id` ổn định theo chapter: `scenario-01`, `scenario-02`, ...; duy nhất **trong chapter**, không cần duy nhất toàn module. `title` là **vấn đề cụ thể**, không chỉ tên domain hoặc thuật ngữ.
- `type`: `BUSINESS | ENGINEERING`; `sourceKind`: bước đầu **chỉ** `REALISTIC_SIMULATION`, không bịa công ty/sự cố lịch sử đã xác thực.
- `exercise.type` (enum dự kiến): `CODING | DEBUGGING | WORKFLOW_DESIGN | ARCHITECTURE_DESIGN | ANALYSIS | DECISION_MAKING | TROUBLESHOOTING | PRACTICAL_LAB | CONCEPTUAL_REASONING`. Không mặc định `CODING`.

## 3. COMPLETE YAML — Phase 2 thêm nội dung

Worker giữ **nguyên từng `id/type/title/sourceKind/exercise.type`, số lượng và thứ tự** đã được readback từ Phase 1. Chỉ bổ sung đúng các field:

```yaml
scenarios:
  - id: scenario-01
    type: ENGINEERING
    title: "Tên một vấn đề thực tế cụ thể"
    sourceKind: REALISTIC_SIMULATION
    context: |-
      Bối cảnh thực tế/mô phỏng và ràng buộc.
    problem: |-
      Điều gì cần giải quyết, vì sao quan trọng.
    analysis:
      possibleCauses: |-
        Cơ chế hoặc nguyên nhân gây vấn đề.
      application: |-
        Cách kiến thức chương xử lý vấn đề và giới hạn.
      tradeoffs: |-
        Các lựa chọn, đánh đổi và rủi ro.
    exercise:
      type: WORKFLOW_DESIGN
      requirements:
        - "Đề bài/đầu vào/ràng buộc phải giải quyết."
      deliverables:
        - "Sản phẩm phải nộp (code, sơ đồ, lập luận, quy trình...)."
      acceptanceCriteria:
        - "Tiêu chí kiểm tra được."
      hints:
        - "Gợi ý không lộ toàn bộ đáp án."
      referenceSolution: |-
        Phương án tham khảo, lập luận và cách tự đánh giá.
```

Mẫu trên **chỉ diễn tả schema, không phải nội dung để worker copy**. Trường văn bản phải có nội dung thực; `requirements/deliverables/acceptanceCriteria` là mảng string không rỗng; `hints` có thể `[]` khi hợp lý; `referenceSolution` bắt buộc. Bài lý thuyết có deliverable cụ thể và acceptance criteria, **không cần repository hoặc source code**. Exercise phải khác thao tác lặp nguyên ví dụ ở `analysis` (thêm ràng buộc, thay giả định, yêu cầu lựa chọn). Không tự gán `readmeRelated/apiRelated`.

## 4. Prime: preflight và giao một worker/chapter

1. Đọc toàn văn `GENERAL_AGENT_RULES.md`, toàn bộ `rules/`, `module-generate-info.txt`, `STEP_8.md`, `worker/STEP_8_WORKER.md`. Kiểm tra STEP 7 đã QA, spreadsheet URL/gid/I3, module và trạng thái `<I3>/master.json` (không dùng J2/J3).
2. **Implementation gate**: xác minh Sheet đã có 33 cột AD:AG, header, validator và Gradle task `generateModulePracticeFromSheetByLocation`. Nếu thiếu bất kỳ phần nào, **BLOCKER: STEP8_IMPLEMENTATION_PENDING**. Không suy đoán task hoặc ghi dữ liệu khi chưa có preflight.
3. Từ `A:B` lập ledger toàn bộ chapters, kiểm tra `AD:AG` hàng con trống. Phân loại mỗi hàng cha:
   - `EMPTY`: bốn ô trống, giao worker cả hai phase.
   - `DRAFT`: toàn bộ items đúng skeleton; chỉ resume **Phase 2 của worker còn sở hữu phiên**. Nếu phiên bị ngắt, Prime phải xác minh ownership/snapshot rồi mới lập quy trình phục hồi; không tái tạo skeleton.
   - `COMPLETE`: toàn bộ items đủ schema, `UNCHANGED` và Prime QA.
   - `PARTIAL/INVALID`: trộn DRAFT/COMPLETE, gap fragments, field lạ, schema sai, conflict → BLOCKER; không rewrite đoán.
4. Mode A mặc định 3 worker ACTIVE+SLEEPING, Mode B dùng N do user đã chỉ định; reuse SLEEPING, tạo worker **tuần tự**; một owner/parent row.
5. Handoff **đúng bốn dòng**, **chỉ truyền location/A1 range**, không đưa giá trị tên module, danh sách tên Menu Cấp 1/Cấp 2, nội dung domain hay đáp án. `PARENT_ROW` là hàng Menu Cấp 1 cần làm; `FIRST_PARENT_ROW` là hàng cha đầu tiên toàn tab; `LAST_MENU_ROW` là hàng Menu cuối cùng thực tế; `LAST_CHILD_ROW` là hàng con cuối cùng của chapter (hàng ngay trước Menu Cấp 1 kế tiếp, hoặc hàng cuối module). Prime phải xác minh các ranh giới từ ledger trước khi giao:

```text
<VERIFIED_SPREADSHEET_URL_WITH_GID>
PARENT_ROW=<ACTUAL_PARENT_ROW> | MENU_1_RANGE=A<FIRST_PARENT_ROW>:A<LAST_MENU_ROW> | MENU_2_RANGE=B<PARENT_ROW+1>:B<LAST_CHILD_ROW>
READ_PHASE_1=A1,B1,A19,B19,<MENU_1_RANGE>,<MENU_2_RANGE>,AD18:AG19,AD<PARENT_ROW>:AG<PARENT_ROW> | READ_PHASE_2=AD<PARENT_ROW>:AG<PARENT_ROW> | WRITE_PHASE_1=AD<PARENT_ROW>:AG<PARENT_ROW> | WRITE_PHASE_2=AD<PARENT_ROW>:AG<PARENT_ROW> | STATE=<EMPTY|DRAFT|COMPLETE> | OWNER=<WORKER_ID>
/java-learning/module-generate-agent/worker/STEP_8_WORKER.md
```

Worker nhận tọa độ rồi **tự gọi Google Sheets read đúng từng vùng**. Không dùng một request `A1:AG`, `A:B` toàn module, hoặc `A<start>:B<end>` nếu request đó có thể mở rộng sang Menu Cấp 2 của chapter khác. Các vùng nêu trong `READ_PHASE_1` dùng để xác thực cấu trúc và tự suy ra `MODULE`/`CHAPTER`; giá trị thực không xuất hiện trong handoff.

## 5. Trong cùng phiên: Phase 1 → Phase 2 → Prime QA

1. Worker **Phase 1** trước tiên đọc các **tọa độ READ_PHASE_1 đã giao**, xác nhận `A1/B1`, `A19/B19`, tên chapter tại `A<PARENT_ROW>`, các chapter khác từ `MENU_1_RANGE` và các lesson **chỉ từ MENU_2_RANGE**. Sau đó ghi toàn bộ DRAFT vào AD:AG, readback tất cả 4 cells, nối không separator, parse/validate DRAFT, đối chiếu unique ID và các chủ đề không trùng lặp. Chỉ sau `PHASE_1_READBACK=PASS` mới sang Phase 2.
2. **Không đổi worker/session**. Worker đọc lại chính DRAFT đã ghi; tự hoàn thiện **mọi** scenario. Chỉ ghi COMPLETE **khi toàn bộ đều đã viết xong**, so sánh snapshot để giữ skeleton metadata/order/count; exact readback và validation COMPLETE. Không đọc Knowledge/Interview hay API khi viết.
3. Worker không cần Prime duyệt giữa phase. Nếu race, lỗi công cụ hoặc không đủ chỗ ghi, báo BLOCKER và giữ nguồn tốt nhất; **DRAFT chưa được import**.
4. Prime sau khi **toàn bộ worker** kết thúc mới QA kỹ thuật, tình huống có thực tế không, sai/suy đoán, chất lượng deliverables/criteria, trùng lặp giữa chapters; lúc này Prime được đối chiếu Knowledge/source. Feedback cho worker **chỉ nêu yêu cầu sửa/lỗi**, không chép Knowledge vào worker; bảo vệ nội dung sẵn có.

## 6. Import CODE ONLY — một file cho mỗi chapter

Task module-local `generateModulePracticeFromSheetByLocation`, với `-PfileUrl=<URL> -PsheetID=<GID> -PsheetMode=DRY_RUN|APPLY`. **Đây là importer Groovy thuần code; không dùng AI để tách, chữa lỗi, viết lại hoặc rút gọn nội dung.**

```powershell
.\gradlew.bat :<MODULE_GRADLE_PATH>:generateModulePracticeFromSheetByLocation -PfileUrl="<URL>" -PsheetID=<GID> -PsheetMode=DRY_RUN
.\gradlew.bat :<MODULE_GRADLE_PATH>:generateModulePracticeFromSheetByLocation -PfileUrl="<URL>" -PsheetID=<GID> -PsheetMode=APPLY
```

- Code xác minh identity I3, header, toàn bộ `AD:AG` cha, không có orphan data con, fragment continuity, capacity, YAML parse an toàn/duplicate-key rejection, **mọi chapter COMPLETE** và scenario IDs unique chapter-local. Bất kỳ DRAFT/PARTIAL nào cũng BLOCKER; không lấy một phần tab để import.
- **Mỗi Menu Cấp 1 đúng một file** `content/readme/docs/vi/menu/<chapter>/practice/scenarios.yml` chứa **nguyên root `scenarios[]`**. Code nối AD + AE + AF + AG **không separator**, parse/validate toàn bộ tài liệu rồi ghi lại **nguyên văn chuỗi YAML nguồn**, không serialize/tách từng scenario; giữ thứ tự, Markdown scalar, newline và lời giải. **Không sinh** `scenario-01.yml`/file lẻ.
- DRY_RUN báo diff toàn bộ; APPLY stage/chống race/CLEAN–FILL **chỉ practice VI** của module đúng I3, rollback nếu lỗi, exact readback bytes và schema. Không chạm Knowledge/metadata/summary/questions/quiz/video/Swagger/EN và không âm thầm xóa từ source thiếu.
- Chỉ sau all-worker QA, importer DRY_RUN/APPLY/readback PASS, Prime mới cập nhật `CURRENT_STEP.VALUE="8"` trong đúng master.json rồi đọc lại; không ghi Sheet J2/J3, không auto-STEP 9, không tự commit/push. Giá trị `"8"` lịch sử (workflow cũ) **không phải bằng chứng** hoàn thành STEP 8 mới; không ghi lùi trạng thái cao hơn.

## 7. Phụ thuộc khi hiện thực pipeline

- **STEP 1/Template**: migrate 29 → 33 cột, AD18/AD19:AG19, giữ A:AC và formatting.
- **STEP 12**: snapshot Excel VI 33 cột, exact readback AD:AG và giới hạn UTF-16.
- **STEP 13**: clone nguyên `practice/scenarios.yml` VI → EN, validate `practiceSheet`/IDs, rollback.
- **STEP 14**: type `PRACTICE`, **một worker dịch trọn một file `scenarios.yml` của một chapter**. Chỉ dịch văn bản, giữ `id/type/sourceKind/exercise.type`, số lượng/thứ tự, code/command.
- **STEP 15**: đọc nguyên `practice/scenarios.yml` EN, xác thực VI/EN và chia greedy theo ranh giới dòng về AD:AG (30.000 ký tự/ô), không parse rồi serialize lại câu trả lời; xuất Excel EN 33 cột.
- **Portal/FE**: capability Practice riêng, index và từng scenario; giải pháp tham khảo có thể ẩn theo tương tác UI (static file không phải bảo mật).
- **GENERAL/PRIME_RULES, AGENTS, ARCHITECTURE, tooltip**: cần cập nhật khi implementation thật đã sẵn sàng; các quy tắc hiện có vẫn ghi STEP 8 reserved.

## 8. Completion gate / Report

Báo: `MODULE_I3`, `VI_GID`, `CHAPTERS_TOTAL`, `SCENARIOS_PLANNED/COMPLETE`, `EXERCISE_TYPES`, `PHASE_1_READBACK`, `PHASE_2_READBACK`, `SKELETON_PRESERVATION`, `FRAGMENTS_AD_AG`, `DRAFT/COMPLETE_SCHEMA`, `PRIME_QA`, `DUPLICATES`, `DRY_RUN/APPLY`, `ROLLBACK`, `OUTPUT_PATHS`, `CURRENT_STEP.VALUE_READBACK`, `BLOCKERS`, `GIT_STATUS`.

**COMPLETED** chỉ khi tất cả chapter COMPLETE, worker QA, Prime QA, code importer/readback PASS, không còn owner/pending/blocker, tiến độ đúng và readback PASS. Hai file contract không tự chứng minh readiness.
