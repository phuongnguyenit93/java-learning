 STEP 8 — WORKER ONLY: Two-phase Real-world Practice

**Một worker = một Menu Cấp 1 = một phiên liên tục thực hiện Phase 1 rồi Phase 2.** Không spawn/nhờ worker khác làm Phase 2; không gọi Prime xin duyệt giữa các phase. Phải tự đọc **toàn bộ** file worker-only này trước khi làm. Prime **chỉ cấp URL/gid, tọa độ vùng đọc Phase 1/Phase 2, hàng cha và vùng ghi AD:AG**, **không chép tên module/Menu hoặc nội dung vào prompt**. Worker tự lấy tên từ Sheet. **Không đọc STEP_8.md Prime, GENERAL_AGENT_RULES, file STEP khác hoặc thư mục code.**

## 1. Nguồn đọc / ownership nghiêm ngặt

**Phase 1 — chỉ đọc chính xác các vùng sau, từ Sheet URL/gid được giao:**
- `A1`, `B1` để lấy/xác minh tên module và metadata module; `A19`, `B19` để xác nhận cấu trúc menu. Không mở rộng sang `A1:B19` hay `A1:AG`.
- `MENU_1_RANGE=A<FIRST_PARENT_ROW>:A<LAST_MENU_ROW>`: **chỉ cột A** để lấy tên các Menu Cấp 1 của toàn module, bỏ các ô trống của hàng con. Phải xác nhận `A<PARENT_ROW>` là một Menu Cấp 1 có thật.
- `MENU_2_RANGE=B<PARENT_ROW+1>:B<LAST_CHILD_ROW>`: **chỉ cột B** chứa tên Menu Cấp 2 của **đúng chapter**; không đọc B của chapter khác hoặc B của hàng cha.
- `AD18:AG19` để xác nhận header STEP 8 và `AD<PARENT_ROW>:AG<PARENT_ROW>` để preflight/trạng thái, ghi DRAFT và exact readback.
- File worker-only này và tọa độ/ownership Prime gửi trong đúng bốn dòng handoff.

**Phase 2 — chỉ đọc lại** `AD<PARENT_ROW>:AG<PARENT_ROW>` đã ghi ở Phase 1 (và metadata cell cần thiết để preflight/đọc lại); **không đọc lại/mở rộng mục lục hay bất kỳ vùng content nào**. Dùng tên module/chapter và mục lục đã thu được ở Phase 1 từ chính Sheet, không đòi Prime dán vào prompt.

Handoff mẫu **chỉ chứa location**, trong đó Prime phải điền ranh giới hàng thực:

```text
<VERIFIED_SPREADSHEET_URL_WITH_GID>
PARENT_ROW=<ROW> | MENU_1_RANGE=A<FIRST_PARENT_ROW>:A<LAST_MENU_ROW> | MENU_2_RANGE=B<PARENT_ROW+1>:B<LAST_CHILD_ROW>
READ_PHASE_1=A1,B1,A19,B19,<MENU_1_RANGE>,<MENU_2_RANGE>,AD18:AG19,AD<ROW>:AG<ROW> | READ_PHASE_2=AD<ROW>:AG<ROW> | WRITE_PHASE_1=AD<ROW>:AG<ROW> | WRITE_PHASE_2=AD<ROW>:AG<ROW> | STATE=<EMPTY|DRAFT|COMPLETE> | OWNER=<WORKER_ID>
/java-learning/module-generate-agent/worker/STEP_8_WORKER.md
```

**Cấm đọc:** vùng sinh Knowledge `C:M` **và cả N** (toàn `C:N`), Difficulty `O`, Summary STEP 5 `AC`, Interview `P:S`, Quiz `T:W`, các ô AD:AG chương khác và hàng con, **cột B của chương khác**, README, Java source/test, Swagger/API, Code Explorer, output Worker/STEP khác, project files hoặc lịch sử chat. Không gọi Google Sheets API bằng một vùng bao rộng `A:B` toàn module hay `A1:AG` vì trả về dữ liệu ngoài whitelist. Không quét repository, web để thu thập nội dung Knowledge, hay mở các tệp ngoài phạm vi. **Tự quyết định nội dung từ hiểu biết kỹ thuật và mục lục đọc tại Sheet**, không yêu cầu Prime gửi danh sách tên Menu/gợi ý domain sẵn.

**Chỉ được ghi:** `AD<PARENT_ROW>:AG<PARENT_ROW>` của một chapter, với **một YAML root `scenarios:`**. Không sửa A:AC, hàng con, hàng cha khác, headers, tab khác, module YAML, master.json, format/notes/validation hoặc các cột `AH+`. Nếu header/cột/ownership chưa hợp lệ: BLOCKER, không tự sửa hoặc tự thêm cột.

## 2. Quy tắc xử lý Sheet

- Template yêu cầu grid ít nhất 33 cột; `AD18=Real-world Practice` và `AD19:AG19=Practice YAML`; kiểm tra **chỉ schema/header**, không sửa. Nếu chưa được setup hoặc wrong gid/parent row: `BLOCKER: STEP8_SHEET_SCHEMA`.
- Trước **mỗi lần ghi**, đọc bốn ô hiện có và metadata: value entered/effective/formatted, formula, notes, validation, chipRuns; kiểm tra quyền ghi, các fragment có gap không, và fingerprint tránh sửa đồng thời. Không overwrite nội dung đã có mà không chứng minh nó chính là DRAFT mình đã tạo.
- **QUY TẮC BẮT BUỘC: MỖI Ô TỐI ĐA 30.000 KÝ TỰ, KHÔNG PHẢI CHIA ĐỀU RA BỐN Ô.** Soạn **một chuỗi YAML hoàn chỉnh** trước khi chia. **Nếu toàn bộ YAML dài ≤30.000 ký tự thì ghi TOÀN BỘ vào AD, AE/AF/AG để trống.** Chỉ chuyển sang AE khi AD đã chứa phần lớn nhất có thể theo ngưỡng, rồi tương tự AF, AG. **Tuyệt đối không chia theo 1/4 độ dài, không chia một YAML ngắn thành bốn mảnh vài trăm ký tự.**
- Khi YAML vượt giới hạn một ô, chọn điểm cắt tại **ký tự xuống dòng cuối cùng trước hoặc tại ngưỡng 30.000 ký tự**, giữ nguyên ký tự xuống dòng ở cuối fragment trước; fragment tiếp theo bắt đầu nguyên văn sau nó. Không cắt giữa từ, key, giá trị YAML hoặc trong cặp Unicode khi còn ranh giới dòng hợp lệ. Nếu không có ranh giới dòng phù hợp trước giới hạn, báo BLOCKER hoặc dùng bộ chia code đã được kiểm chứng, **không cắt tùy tiện bằng AI**. Giữ thêm trần **32.767 UTF-16 code units/ô** để không hỏng Excel; không bỏ dữ liệu và không tràn sang AH.
- **Ví dụ bắt buộc:** YAML dài 1.217 ký tự → `AD=1.217`, `AE=AF=AG=trống`; YAML dài 35.000 ký tự → `AD≈30.000` (cắt ở cuối dòng gần ngưỡng), `AE≈5.000`, `AF=AG=trống`. Không thêm dòng trống/dấu cách/marker khi chia.
- Trước khi ghi, **nối lại bốn fragments ngay trong bộ nhớ** bằng `AD + AE + AF + AG` (không separator), so sánh **chính xác từng ký tự** với YAML gốc và parse thử. Sau ghi, **đọc lại cả AD:AG** rồi nối/so sánh/parse lần nữa. Không được đánh dấu PASS nếu `AD` ngắn bất thường trong khi `AE` có dữ liệu và YAML vẫn vừa trong AD. Khi phát hiện cách chia cũ sai, **chỉ worker sở hữu hàng** được repack từ YAML hiện hữu với nội dung tuyệt đối không đổi, preflight snapshot và exact readback; không tái tạo nội dung hay sửa hàng khác.
- Viết một YAML document hợp lệ (không Markdown code fence bao toàn document, không JSON, không duplicate keys, YAML aliases/custom tags). Sau ghi **đọc lại nguyên 4 ô**, nối và so sánh nguyên chuỗi với nguồn, parse YAML và kiểm tra schema. Báo cả `PHASE_1_READBACK` và `PHASE_2_READBACK`.
- Với hàng **COMPLETE hợp lệ đã tồn tại**, trả `UNCHANGED` sau QA, không làm lại nội dung; với **DRAFT hợp lệ do chính worker còn sở hữu trong phiên**, chỉ resume Phase 2; với **PARTIAL/INVALID** hoặc mất quyền ownership, báo BLOCKER. Nếu session cũ đã bị gián đoạn, không tự tạo skeleton mới đè lên DRAFT cũ: bàn giao Prime kiểm chứng phục hồi.

## 3. Phase 1 — Independent Scenario Planning (BẮT BUỘC đầu tiên)

1. **Tự đọc các ô `READ_PHASE_1` Prime cấp theo từng vùng đúng một cột**; đối chiếu `A1/B1`, `A19/B19`, ranh giới Menu Cấp 1 của `A<PARENT_ROW>` và danh sách Menu Cấp 2 của `MENU_2_RANGE`. Nếu ranh giới sai, B hàng con trống/bị chen chapter khác hoặc đọc API trả dữ liệu ngoài whitelist → BLOCKER, không tự mở rộng quyền đọc. Từ chính tên chapter và Menu Cấp 2 vừa đọc, **tự khám phá** các vấn đề thực tế chapter hỗ trợ xử lý. Lựa chọn `BUSINESS` hoặc `ENGINEERING` phù hợp; không ép sang banking/e-commerce/game hoặc mọi chương đều phải có code. Phân biệt các **vấn đề** chứ không chỉ các tên lĩnh vực khác nhau.
2. **Tự quyết định số lượng scenario**, không quota, không tối thiểu theo số Menu Cấp 2. Đảm bảo danh sách có giá trị học tập và không lặp lại cùng một bài. Chọn kiểu bài tập tương ứng: `CODING`, `DEBUGGING`, `WORKFLOW_DESIGN`, `ARCHITECTURE_DESIGN`, `ANALYSIS`, `DECISION_MAKING`, `TROUBLESHOOTING`, `PRACTICAL_LAB`, `CONCEPTUAL_REASONING`. Không mặc định `CODING`; với Git có thể thiết kế workflow, kiểm tra thao tác CLI hoặc giải thích quyết định kỹ thuật.
3. **Tạo toàn bộ skeleton trước, rồi mới ghi một YAML root `scenarios:` vào AD:AG hàng cha.** Mỗi item có đúng các keys `id`, `type`, `title`, `sourceKind`, `exercise.type`. Cấp ID chapter-local theo `scenario-01`, `scenario-02`... và giữ ổn định. `sourceKind` hiện chỉ `REALISTIC_SIMULATION`.
4. DRAFT mẫu **chỉ minh họa keys** (không phải đề tài phải sao chép):

```yaml
scenarios:
  - id: scenario-01
    type: ENGINEERING
    title: "Tên vấn đề cụ thể do worker tự chọn"
    sourceKind: REALISTIC_SIMULATION
    exercise:
      type: CONCEPTUAL_REASONING
```

**Tuyệt đối không thêm trong Phase 1:** `context`, `problem`, `analysis`, `requirements`, `deliverables`, `acceptanceCriteria`, `hints`, `referenceSolution` kể cả empty/null/placeholder; không thêm `status`, `count`, `related` hoặc metadata khác. Không vừa tạo skeleton-01 vừa điền body-01.

5. Ghi DRAFT theo **quy tắc lấp đầy AD trước**; exact readback **cả AD:AG**, nối/parse lại và kiểm tra từng item đúng whitelist, ID unique, thứ tự, kiểu bài và danh sách bao quát không trùng. **Chỉ khi `PHASE_1_READBACK=PASS` mới chuyển Phase 2**, trong cùng phiên, không chờ Prime.

## 4. Phase 2 — Scenario Development (CÙNG PHIÊN WORKER)

1. **Chỉ đọc `READ_PHASE_2=AD<PARENT_ROW>:AG<PARENT_ROW>`**; đọc lại **chính YAML DRAFT** vừa ghi; lưu danh sách `id/type/title/sourceKind/exercise.type` làm baseline không đổi. Không đọc lại Menu hoặc mở vùng Knowledge; không tự thêm/bớt scenario, đổi type, thứ tự, title hoặc đánh lại ID để tiện viết nội dung. Nếu phát hiện skeleton bất hợp lý gây blocker, báo Prime với bằng chứng thay vì phá baseline.
2. **Tự hoàn thiện toàn bộ scenario trong bản YAML đang soạn**, không đẩy một nửa hoàn chỉnh lên Sheet. Mỗi scenario bổ sung **chính xác** cấu trúc sau:

```yaml
scenarios:
  - id: scenario-01
    type: ENGINEERING
    title: "Tên vấn đề cụ thể do worker tự chọn"
    sourceKind: REALISTIC_SIMULATION
    context: |-
      Bối cảnh, ràng buộc và điều kiện phát sinh.
    problem: |-
      Vấn đề thật cụ thể, tác động và điều cần giải quyết.
    analysis:
      possibleCauses: |-
        Nguyên nhân hoặc cơ chế có thể xảy ra.
      application: |-
        Kiến thức chương xử lý vấn đề thế nào và giới hạn ra sao.
      tradeoffs: |-
        Đánh đổi, phương án thay thế, rủi ro và điều kiện lựa chọn.
    exercise:
      type: CONCEPTUAL_REASONING
      requirements:
        - "Yêu cầu thực tế người học cần thực hiện."
      deliverables:
        - "Sản phẩm đầu ra xác định rõ."
      acceptanceCriteria:
        - "Điều kiện đánh giá được kết quả."
      hints:
        - "Gợi ý đủ định hướng, không lộ toàn lời giải."
      referenceSolution: |-
        Lời giải tham khảo có lý do và cách tự đánh giá.
```

3. Nội dung phải **thay tất cả ví dụ/placeholder bằng nội dung kỹ thuật do chính mình tạo**, không được lặp câu mẫu ở trên. Mọi string có nghĩa, mảng `requirements/deliverables/acceptanceCriteria` không rỗng; `hints` có thể `[]`. Phải có lời giải thực sự, không dùng câu “tham khảo tài liệu” làm đáp án.
4. **Bài tập không nhất thiết viết code.** Cho phép lập luận lý thuyết, sơ đồ Git, chuỗi lệnh/plan, quyết định kiến trúc, tình huống phân tích/troubleshooting hoặc thiết kế. Mỗi bài vẫn phải có deliverable và acceptance criteria kiểm chứng được. Lời giải phải giải thích vì sao, khi nào, điều kiện áp dụng, các ngoại lệ/tradeoffs. Khi kiến thức chương không giải quyết trọn vấn đề, phân ranh rõ phần xử lý được và phần nằm ngoài.
5. Bài tập cần **buộc người học ứng dụng** (đổi giả định/ràng buộc, cân nhắc giải pháp, xử lý trường hợp khác), không chỉ nhắc lại nguyên văn bài đã giải thích trong analysis. Không bịa một sự cố lịch sử có thật; `REALISTIC_SIMULATION` luôn là tình huống mô phỏng.
6. Kiểm tra tất cả scenario COMPLETE, đối chiếu baseline skeleton bất biến và capacity AD:AG, sau đó **mới ghi một YAML đầy đủ** vào đúng hàng cha. Exact readback bốn fragments, parse/schema QA; tự rà trùng lặp và chất lượng lời giải trong chapter. Không đọc Knowledge/source/API ngay cả ở giai đoạn xác minh nội dung; Prime sẽ kiểm tra độc lập sau.

## 5. Trường hợp lỗi / dừng

- Nếu Phase 1 chưa readback PASS, **cấm** sang Phase 2; nếu Phase 2 lỗi, bảo vệ DRAFT và báo BLOCKER, không tự đánh dấu hoàn tất.
- Nếu 4 cells chứa nội dung người khác hay schema khác, hoặc giá trị thay đổi ngoài dự kiến, **không overwrite, không đẩy dữ liệu sang hàng/cột khác**. Không gọi normalize/importer Gradle và không cập nhật module/master.json.
- Không yêu cầu Prime phê duyệt từng scenario giữa hai phase. Worker chỉ được báo Prime sau COMPLETE hoặc khi gặp blocker thực sự.
- Không tự commit/push, không dịch EN, không chạy STEP 9 hoặc STEP tiếp theo; không thay đổi Current STEP.
- Nếu Prime yêu cầu sửa theo QA, chỉ sửa mục bị chỉ rõ trong phạm vi owner, giữ skeleton/id/order và các scenario khác; không dùng nội dung Knowledge/Interview được gửi kèm làm nguồn viết lại.

## 6. Báo cáo worker → Prime

Báo tối thiểu:

```text
MODULE=<...> | TAB_GID=<...> | CHAPTER=<...> | PARENT_ROW=<...>
PHASE_1_READ_SCOPE=A1,B1,A19,B19,MENU_1_RANGE,MENU_2_RANGE,AD18:AG19,AD<row>:AG<row>
PHASE_2_READ_SCOPE=AD<row>:AG<row> | WRITE_SCOPE=AD<row>:AG<row> | OWNER=<...>
MODE=NEW_FILL|UNCHANGED|RESUME_DRAFT|BLOCKER
SCENARIOS_PLANNED=<n> | SCENARIOS_COMPLETE=<n> | EXERCISE_TYPES=<...>
PHASE_1_DRAFT_SCHEMA=PASS|FAIL|N/A
PHASE_1_READBACK=PASS|FAIL|N/A
PHASE_2_COMPLETE_SCHEMA=PASS|FAIL|N/A
PHASE_2_READBACK=PASS|FAIL|N/A
SKELETON_FIELDS_AND_ORDER_PRESERVED=PASS|FAIL|N/A
FRAGMENT_CAPACITY=PASS|FAIL | CONTENT_QA=PASS|FAIL
BLOCKERS=<none or concrete error>
```

**Chỉ ghi `COMPLETED` nếu đã thực hiện đầy đủ hai phase trong cùng phiên, cả hai readback PASS, toàn bộ scenarios COMPLETE và không có blocker.** Khi hoàn tất trả quyền điều phối cho Prime; Prime mới QA toàn module và điều phối importer code thuần.
