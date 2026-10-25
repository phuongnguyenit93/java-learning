# STEP 4A — PRIME ONLY: Định hướng học tập theo Menu Cấp 1

## 1. Mục đích và vị trí trong workflow

**Thứ tự: STEP 4 → STEP 4A → STEP 5.** STEP 4 tạo nội dung Knowledge Markdown trong `C:N` cho từng Menu Cấp 2. STEP 4A **đọc trọn các bài đã có** và tạo **một bản định hướng học tập Markdown độc lập cho từng bài**: mục đích, những câu hỏi người học phải tự trả lời, và bảng năng lực/kết quả mong đợi.

- **Một worker sở hữu đúng một Menu Cấp 1 (cả chương)**, xử lý **tất cả** Menu Cấp 2 thuộc chương đó trong một lượt giao. Không giao một worker cho một bài riêng lẻ, không chia một chương giữa nhiều worker.
- **Prime chịu trách nhiệm chuẩn bị cột mới `AC`, lập danh sách chương, giao đúng phạm vi, kiểm tra và nghiệm thu toàn tab.** Worker đọc/soạn và ghi kết quả **riêng vào `AC<R>` của từng Menu Cấp 2** được giao. **AC hàng cha được dành riêng cho STEP 5 lưu liên kết giữa các bài học**; STEP 4A không ghi vào hàng cha. Prime không tự viết nội dung học tập thay worker.
- **`C:N` chỉ được đọc.** Tuyệt đối không chèn, sửa, xóa, tái phân đoạn Knowledge hoặc dùng cột đang phục vụ STEP khác. STEP 4A không phải STEP 5 review/refactor, không tạo Quiz/Interview và không tạo khung liên kết giữa các bài.

**Giới hạn hiện tại:** Hai file STEP 4A là hợp đồng hướng dẫn, **không phải thay đổi code/importer/UI**. Bước này lưu Markdown trong `AC` của Google Sheets, **chưa tự hiển thị trên website/README**. Khi triển khai trọn workflow phải bổ sung routing vào `GENERAL_AGENT_RULES.md`, cân nhắc template STEP 1 và quy định STEP 5 sử dụng/đồng bộ `AC` sau refactor, nhưng **không tự sửa các file đó trong phạm vi tạo/sửa hai file STEP 4A**.

## 2. Nguồn, sơ đồ Sheet và thiết lập cột mới — PRIME chịu trách nhiệm

Trước khi giao worker:

1. Đọc toàn văn `GENERAL_AGENT_RULES.md`, **liệt kê và đọc mọi file đang có** trong `module-generate-agent/rules/` (gồm `PRIME_RULES.md`, `HEARTBEAT.md`), `module-generate-info.txt`, `STEP_4A.md` và `worker/STEP_4A_WORKER.md`. Nếu mất context phải đọc lại. Lấy URL gốc + mapping `<MODULE> sheet id : <gid>`; xác minh đúng tab, `A1/B1`, `J2/J3`, `I3`, vùng Menu `A:B` từ hàng 20, header `C19:N19` và hai ô `Special Note` (nhãn + giá trị ngay dưới cùng cột). Không phỏng đoán gid hay địa chỉ Special Note.
2. **Chọn cột đích cố định: `AC` (cột thứ 29, ngay sau `AB`).** Template hiện hành có **28 cột `A:AB`**; các vùng `C:N` Knowledge, `O` Difficulty, `P:S` Interview, `T:W` Quiz, `X:AA` Video, `AB` isImportant đều đã có ownership. `AC` là vùng mới **chỉ dành cho STEP 4A**, không sử dụng `AD` hoặc mượn cột hiện hữu.
3. **Prime provision `AC` trên đúng tab module được giao**, không tự sửa `Template` hay các tab khác. Đọc `gridProperties.columnCount` và snapshot `AC18:AC<END>` nếu cột đã tồn tại. Nếu lưới chỉ có 28 cột, tăng **riêng `columnCount` lên tối thiểu 29** bằng Google Sheets API `updateSheetProperties` (field mask `gridProperties.columnCount`), giữ `rowCount` và toàn bộ dữ liệu/format cũ. Nếu cột `AC` đã có dữ liệu/metadata không thuộc STEP 4A hoặc không thể xác minh an toàn → `BLOCKER: AC_COLUMN_CONFLICT`, **không ghi đè/di chuyển dữ liệu**.
4. Chuẩn hóa header **trên đúng tab**: `AC18 = Learning Objectives`, `AC19 = Learning Objectives Markdown`. Nếu chưa có header, Prime chỉ ghi **giá trị văn bản** hai ô trên sau preflight entered/effective/formula/note/validation/chipRuns, giữ mọi format hiện hữu; nếu đã khớp thì giữ nguyên. Nếu có nội dung khác hoặc formula ở header → `BLOCKER: AC_HEADER_CONFLICT`. Đọc lại **`AC18/AC19` exact** sau thao tác. **Không đổi header `A:AB`**.
5. Quét **toàn bộ Menu từ hàng 20 đến hàng cuối thực tế** (theo grid/tab), lập bản đồ `A<PARENT>` là Menu Cấp 1, các hàng con liên tiếp với `B<R>` là Menu Cấp 2 cho tới trước Menu Cấp 1 kế tiếp. Snapshot các ô `AC<R>` hàng con và `AC<PARENT>`; **hàng cha AC nằm ngoài phạm vi STEP 4A (để trống nếu chưa chạy STEP 5, hoặc bảo toàn nguyên trạng nếu STEP 5 đã ghi liên kết)**. Xác định `CHAPTER → CHILD_ROWS → C:N/AC`, đếm đầy đủ bài, phân loại `PENDING` (AC trống, bài C:N đầy đủ), `EXISTING` (AC đã có đúng barem hợp lệ), `BLOCKER` (bài trống/đứt đoạn, AC sai hoặc bị người khác sở hữu). **Không bỏ qua các bài phía dưới chỉ vì hàng đầu đã xong**.
6. Không sửa `C:N` dù bài có bất kỳ fragment nào; ghép toàn bộ `C → N` không separator khi kiểm tra. Bài thiếu/mâu thuẫn làm không đủ căn cứ xây dựng định hướng → `BLOCKER: INCOMPLETE_KNOWLEDGE` hoặc `NEEDS_STEP_5_REVIEW`, không tự viết lại.

### Bảng phân quyền

| Thành phần | Prime | Worker của chương |
| --- | --- | --- |
| `A1`, `B1`, `A<PARENT>`, `B<CHILD_ROWS>`, Special Note | Chỉ đọc, định vị | Chỉ đọc theo tọa độ giao |
| `C<CHILD_ROWS>:N<CHILD_ROWS>` | Chỉ đọc/kiểm kê | **Chỉ đọc toàn văn từng bài** |
| `AC18`, `AC19`, kích thước lưới đến `AC` | **Provision/header + exact readback, riêng tab** | Chỉ đọc, kiểm tra schema |
| `AC<CHILD_ROWS>` | Snapshot, QA, exact readback | **Ghi duy nhất Markdown của từng bài thuộc chương** |
| `AC<PARENT>`, các chương khác, `A:AB`, `AD:...`, files/source/Template | Không ghi (trừ J3 Prime sau QA) | **Không ghi** |

**Lưu ý:** `AC` không nằm trong phạm vi importer README `C:N` hiện tại. STEP 4A không tự chạy task Gradle, không chỉnh schema/source hoặc thêm output README từ AC. Chưa có bằng chứng importer hiểu AC thì **không tuyên bố đã publish định hướng lên website**.

## 3. Worker pool và contract giao đúng một chương

1. Theo mode của **session hiện hành**: **Mode A = tối đa 3 worker mặc định; Mode B = N worker khi user chỉ định N nguyên dương**; tính cả `ACTIVE + SLEEPING`. Mode B thiếu N/mâu thuẫn → blocker. Kiểm tra pool trước spawn, **ưu tiên tái sử dụng SLEEPING**, không spawn khi còn SLEEPING; spawn từng worker, chờ kết quả trước worker tiếp theo.
2. **Mỗi worker nhận một Menu Cấp 1 và trọn các hàng con của chương đó**; không có hai worker đọc/ghi cùng `AC<R>`, không tạo hai owner cho một chương. Worker phải lần lượt hoàn tất và exact readback **tất cả** hàng con của chính chương; chỉ sau khi chapter QA đạt mới release ownership/SLEEPING.
3. Prime chuẩn bị đúng **bốn dòng định vị** (không gửi nội dung, tóm tắt, lời giải, prompt bổ sung):

```text
<VERIFIED_TAB_LINK_WITH_GID>
READ: A1, B1, A<PARENT_ROW>, B<START_ROW>:B<END_ROW>, C<START_ROW>:N<END_ROW>, AC18, AC19, AC<PARENT_ROW>, AC<START_ROW>:AC<END_ROW>, <SPECIAL_NOTE_TITLE_CELL>, <SPECIAL_NOTE_VALUE_CELL>
WRITE: AC<START_ROW>:AC<END_ROW> ONLY, CHILD_ROWS=<EXACT_CHILD_ROW_LIST>
/java-learning/module-generate-agent/worker/STEP_4A_WORKER.md
```

`<START_ROW>:<END_ROW>` và `CHILD_ROWS` là danh sách **hàng con thực sự thuộc đúng Menu Cấp 1**, do Prime xác minh từ Menu; không trao vùng hàng cha/chương kế tiếp. Nếu hàng không liên tiếp hoặc có ô chen đặc biệt, Prime dùng tọa độ hàng con **tường minh** cho cả READ và WRITE (không cấp dải chứa hàng không sở hữu). Không gửi công việc qua nhiều tin nhắn hoặc thêm hướng dẫn ngoài bốn dòng. Worker chỉ đọc toàn văn file worker-only của chính STEP.

## 4. Barem nội dung Markdown — bắt buộc đối với MỖI AC hàng con

Worker phải xuất **một tài liệu Markdown hoàn chỉnh, độc lập, không HTML/JSON/YAML**. **Chỉ cố định bốn mục 0/1/2/3, tên bài lấy đúng từ Menu Cấp 2 và định dạng bảng hai cột ở mục 3.** Số đoạn văn, cách lập luận, số nhóm/câu hỏi và số năng lực **do nội dung bài quyết định**, không bị mẫu minh họa ràng buộc.

**Mẫu sau chỉ minh họa cách trình bày, KHÔNG phải danh sách nhiệm vụ hoặc số lượng bắt buộc:**

```markdown
# 0. Định hướng học tập

## 1. Mục đích của bài học

**<Tên Menu Cấp 2 thực tế>**

<Viết phần mục đích bằng số đoạn phù hợp: vì sao cần học, kiến thức giải quyết vấn đề nào, giá trị đối với OOP/module. Có thể kết hợp hoặc mở rộng các ý; không buộc đúng ba đoạn.>

## 2. Những câu hỏi người học phải tự trả lời được

### <Nhóm về bản chất hoặc vấn đề cốt lõi của bài>

1. <Câu hỏi yêu cầu giải thích một vấn đề được dạy trong bài?>
2. <Câu hỏi về nguyên nhân, cơ chế hoặc sự khác biệt quan trọng?>

### <Nhóm về vận dụng, thiết kế hoặc kiểm chứng nếu bài có nội dung tương ứng>

1. <Câu hỏi về cách áp dụng hoặc lựa chọn phương án?>
2. <Câu hỏi về lỗi, giới hạn hoặc tình huống thực tế?>

## 3. Sau khi học xong, người học phải đạt được điều gì?

| Năng lực | Kết quả mong đợi |
| --- | --- |
| <Năng lực cụ thể được bài dạy> | <Hành động có thể kiểm chứng qua ví dụ hoặc tình huống> |
| <Năng lực khác nếu có> | <Kết quả mong đợi phù hợp đúng phạm vi bài> |
```

**Quy định barem:**

- **Mục 0/1/2/3** giữ tên và thứ tự để các bước sau nhận diện nhất quán. **Mục 1 chỉ bắt buộc tên bài in đậm phải khớp chính xác Menu Cấp 2**; phần mô tả mục đích được tự do tổ chức thành số đoạn phù hợp, diễn đạt trọn vẹn *vì sao học, học để giải quyết điều gì, ứng dụng vào đâu*. Những ý trong mẫu là **gợi ý**, không phải ba đoạn bắt buộc.
- **Mục 2 phải tổ chức câu hỏi thành một vài nhóm chủ đề có nghĩa**, lấy tên nhóm từ kiến thức thực tế: ví dụ bản chất/cơ chế, thiết kế/vận dụng, giới hạn/phòng lỗi **chỉ khi phù hợp**. **Không bắt buộc A/B/C, không bắt buộc đúng ba nhóm, không ấn định số câu/nhóm hay tổng chín câu.** Có thể dùng hai, ba hoặc nhiều nhóm khi bài thực sự cần; tránh chia vụn hoặc tạo nhóm rỗng để đủ mẫu. Mỗi nhóm có câu hỏi mở, tự trả lời được từ bài và thúc đẩy giải thích, suy luận, áp dụng; danh sách đánh số Markdown hợp lệ (có thể bắt đầu lại từ 1 ở mỗi nhóm), không cần đáp án.
- **Mục 3 bắt buộc là bảng Markdown hai cột `Năng lực` và `Kết quả mong đợi`**. Số hàng không cố định, phụ thuộc những năng lực có thể chứng minh từ bài. Mỗi dòng biểu đạt **năng lực cụ thể → hành động quan sát/kiểm chứng được**, không dùng riêng “hiểu”, “nắm được”, “biết” làm kết quả; không lặp cho đủ quota.
- Các placeholder `<...>` ở mẫu **không được xuất hiện trong kết quả thật**. Không tự thêm phần kết luận, Quiz, đáp án, lộ trình liên bài, code dài hoặc nội dung bài học vào AC. Barem phải dựa trên **toàn văn bài + Special Note + ngữ cảnh chương**, không phỏng đoán. Nếu bài không đủ thông tin hỗ trợ barem trung thực, báo `CONTENT_SCOPE_LIMITATION` hoặc `BLOCKER: UNSUPPORTED_LEARNING_OBJECTIVES`, không viết nội dung giả.
- Markdown hợp lệ: heading `#`/`##`/`###`, dòng trắng tách paragraph, danh sách đánh số liên tục, bảng có header + separator + đúng hai cell/row; escape `|` trong cell khi cần, không gói toàn tài liệu trong code fence, không chèn HTML, không có TODO/placeholder.

## 5. Ghi an toàn `AC`, chống làm lại và QA toàn chương

1. **Preflight** trước mỗi chương và mỗi đợt ghi: đọc đầy đủ `C:N` (chỉ nguồn), tên hàng con, AC hiện tại, trạng thái entered/effective/formula/note/validation/chipRuns và ownership. Prime provision đã xác minh `AC18/AC19`; Worker phải kiểm tra lại. Không đọc nhầm hàng cha, không ghi ngoài `AC<CHILD_ROWS>`.
2. **AC trống sạch** → `NEW_FILL` với một Markdown đầy đủ theo barem; **AC đã có bản đúng barem và phản ánh Knowledge hiện tại** → `UNCHANGED`, không ghi; **AC có bất kỳ nội dung lạ, một phần, sai barem, formula, chip hoặc nội dung mâu thuẫn** → `BLOCKER: EXISTING_TARGET_CONTENT/STALE_OBJECTIVES`, không overwrite/FORMAT_FIX tùy tiện. Không làm lại các bài đã đạt chỉ vì bắt đầu session mới.
3. Với `NEW_FILL`, Worker soạn hết văn bản **trước khi ghi**, self-QA nội dung/Markdown. Một ô `AC<R>` chỉ nhận một tài liệu và **tối đa 30.000 ký tự** (ngưỡng an toàn của workflow); vượt hạn mức → `BLOCKER: AC_CELL_CAPACITY`, không tự tràn sang `AD`, không cắt hoặc rút bớt mục tiêu học tập cần thiết.
4. **Preflight lại ngay trước write**, bảo đảm ô AC vẫn trống, C:N/nhãn/Special Note vẫn như snapshot và không có writer khác. Chỉ sửa `userEnteredValue` tại chính AC của từng row; không dùng whole-row `updateCells` dễ xóa nội dung khác, không thay format, notes, validation. Khi công cụ cho phép batch từng chương, chỉ batch **những tọa độ AC đúng hàng con được sở hữu**.
5. **Exact readback TẤT CẢ `AC<R>` sau ghi**; so khớp 100% Markdown dự kiến từng hàng, kiểm tra đủ mục 0/1/2/3, **các nhóm câu hỏi thực tế**, danh sách Markdown hợp lệ, bảng 2 cột và nội dung có căn cứ; các ô `C:N`/A/B/O:AB và ngoài chương nguyên vẹn; `AC<PARENT>` không bị ghi. Nếu có lỗi một phần, không `COMPLETED` chương, báo row/giá trị cuối và snapshot; chỉ khôi phục khi quyền và trạng thái thực cho phép, không retry mù.
6. **QA cấp chương sau mọi bài:** mỗi hàng con có guide riêng, mục đích không bị sao chép y hệt xuyên bài, các nhóm câu hỏi đúng vai trò bài, bảng năng lực đúng nội dung; không trùng dữ liệu do nhầm row. **Prime tự kiểm tra** readback/quality, tổng hợp chapter và toàn tab, không tin chỉ một báo cáo `COMPLETED`. Không biến QA thành việc đánh giá lại/sửa `C:N` — đó là STEP 5.

## 6. Bàn giao STEP 5, trạng thái và báo cáo

- **STEP 5 vẫn review/refactor Knowledge `C:N` theo quyền riêng**, không được vì STEP 4A mà sửa đè `AC` trong các bước khác. Tuy nhiên sau refactor, một số mục đích/câu hỏi/năng lực có thể **không còn khớp**; cần có quy định **QA/reconcile `AC` với nội dung sau STEP 5** (qua thay đổi STEP 5 hoặc một lượt cập nhật được cấp quyền rõ ràng). **Hai file STEP 4A không tự cấp STEP 5 quyền ghi AC**, không coi guide là đã đồng bộ sau refactor nếu chưa có cơ chế đó.
- **Khung “liên kết giữa các bài” của STEP 5 là một nhiệm vụ khác**, không nằm trong `AC` của STEP 4A và không suy quan hệ prerequisite chỉ dựa trên thứ tự bài.
- **J3 chỉ do Prime** sau khi toàn tab `COMPLETED`: xác minh `J2 = Current STEP`, J3 hiện hữu; chỉ ghi `STEP 4A` khi nối tiếp an toàn sau STEP 4, không ghi lùi J3 của module đã ở STEP 5+ hoặc trạng thái legacy khó xác minh; readback exact. Không có task importer riêng cho STEP 4A, không chạy ByFiles/ByLocation hoặc nâng STEP 5.
- Báo cáo Prime: `MODULE/TAB/GID`, `AC_SCHEMA` (column count, AC18/19), `CHAPTERS_TOTAL/COMPLETED`, `LESSONS_TOTAL`, `NEW_FILL/UNCHANGED/BLOCKER`, `WORKER_OWNERSHIP`, `READBACK`, `MARKDOWN_BAREM_QA`, `PRESERVED_C:N`, `STEP5_RECONCILIATION_NEEDED`, `J3_STATUS`. Chỉ báo toàn STEP hoàn thành khi không còn chapter pending/failed/blocker.

**File worker-only bắt buộc:** `/java-learning/module-generate-agent/worker/STEP_4A_WORKER.md`. Không tự commit/push hay triển khai tiếp STEP 5 trong cùng lượt STEP 4A.
