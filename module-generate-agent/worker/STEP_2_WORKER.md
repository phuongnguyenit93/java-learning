# STEP 2 — WORKER ONLY: Roadmap Title / Purpose / Objectives

**STEP status:** This worker must never read or update progress in Google Sheets/Excel or `master.json`. Prime alone records `CURRENT_STEP.VALUE` after whole-STEP QA.


> **2026-10-25 Reference amendment:** When Prime assigns `L2/L3`, read `L2=Reference` and author vetted YAML `references: [{title,url,description?}]` into **L3 only**; preserve valid existing content, never invent URLs, never write module files. The new L3 ownership is an explicit addition to the earlier F:H/B17 boundaries. Verify `title`/`url` for every entry and keep L3 within 30,000 Unicode code points AND 32,767 UTF-16 units, without spilling into other cells. **Reference-only backfill** có handoff bốn dòng gồm link tab, các ô **chỉ đọc C2, tiêu đề và giá trị Special Note, L2, L3**, vùng **chỉ ghi L3**, đường dẫn file này: ở lượt ấy KHÔNG áp dụng gate F:H/B17 của Roadmap bên dưới. Dùng C2/Special Note làm ngữ cảnh để chọn nguồn Reference, preflight YAML, viết L3 nếu trống hoặc QA UNCHANGED nếu đã hợp lệ, exact readback và báo Prime. Tuyệt đối không đọc/ghi F:H/B17 hoặc các ô ngoài vùng handoff. File worker-only này tự chứa mọi ràng buộc; không đọc thêm STEP/rules/contract khác.

> **Chỉ dành cho worker.** Prime sử dụng `STEP_2.md` để điều phối; bạn **chỉ đọc toàn văn file `worker/STEP_2_WORKER.md` này**. Tin Prime giao gồm **bốn dòng: link tab; tọa độ `C2`, Special Note, `A17`, `B17`; vùng ghi `F2:H<END>` và `B17`; đường dẫn file này**. **Prime không gửi nội dung/tóm tắt Special Note: bạn BẮT BUỘC tự đọc nguyên văn trực tiếp trên đúng tab Google Sheets.** Tự làm đầy đủ W1–W5.

**FILL-ONLY — KHÔNG REPLACE:** Trừ khi có chỉ thị đặc biệt, rõ ràng từ người dùng cho chính phạm vi này, worker chỉ tạo mới khi **toàn vùng nội dung đích `F3:H<END>` và `B17` hoàn toàn trống**. Nếu **bất kỳ ô đích nào đã có nội dung**, báo `BLOCKER: EXISTING TARGET CONTENT`, `WRITE=NOT_RUN` và **DỪNG ngay toàn nhiệm vụ**; không lấp các ô còn trống, không QA để cho qua và không ghi một phần. `F2:H2` đúng header mẫu và `A17` là nhãn/ngữ cảnh **chỉ đọc**, không tính là nội dung đích cần sinh.

## W1. Nguồn và phạm vi một nhiệm vụ

1. **Một lượt duy nhất = một Roadmap hoàn chỉnh của một tab.** Đọc → suy luận/soạn → QA → preflight → ghi → readback → báo cáo → SLEEPING. Không tách nhiều worker, không có pha PREPARED / PHA A-B hay đợi lượt chat sau.
2. Mở **đúng link tab/gid Prime giao**, xác nhận tên tab và gid khớp. Đọc **nguyên văn câu hỏi `C2`**, **ô tiêu đề Special Note và ô giá trị Special Note tại đúng HAI tọa độ Prime gửi**, **cả `A17` (nhãn/ngữ cảnh) và `B17` (đích Introduction)**, cùng **vùng Roadmap `F2:H<END>`** để nhận diện header, nội dung cũ, preflight và readback. **Đọc toàn bộ giá trị Special Note trực tiếp, không thay bằng tóm tắt**. Ô trống vẫn phải xác minh trống. Không tự dò tab hoặc nguồn khác, không đọc `C3` hay Menu/Knowledge/Video.
3. Xác minh **ô tiêu đề chứa đúng nhãn `Special Note`** và ô giá trị nằm ngay bên dưới cùng cột. Nếu nhãn/vị trí sai, thiếu tọa độ hoặc không đọc được một trong hai ô: `BLOCKER: INVALID SPECIAL NOTE CELLS`, **không tự đoán địa chỉ**. Nếu link/gid không đúng, `C2` trống/không khả thi hoặc vùng đích không an toàn: `BLOCKER: INVALID TARGET / EMPTY C2 / INVALID OUTPUT RANGE`.
4. Chỉ có quyền tạo mới **giá trị văn bản** trong vùng `F2:H<END>` **và ô `B17` khi cả hai vùng nội dung đích còn trống**. `A17` là nguồn **chỉ đọc**, tuyệt đối không sửa. B17 phải được Prime xác minh không thuộc Menu/header hiện có. Không sửa `C2`, `B1`, `CURRENT_STEP.VALUE`, **hai ô Special Note (chỉ đọc)**, cột I trở đi, Menu, Knowledge, Video, header ngoài vùng, notes, validations, format, các tab/file khác. Không spawn worker, cập nhật trạng thái STEP, sửa source hay commit/push.

## W2. Soạn hoặc hoàn thiện Roadmap đúng đề bài

1. **`C2` là đề bài chính; giá trị `Special Note` là chỉ dẫn/giới hạn bổ sung riêng cho module**. Đọc và hiểu **trực tiếp toàn văn cả hai ô** trước khi soạn. Nếu Special Note trống, chỉ áp dụng C2; nếu có nội dung, **áp dụng các ghi chú liên quan khi tạo Roadmap**, không lược bỏ, tự diễn giải lệch ý hoặc phớt lờ. Nếu Special Note mâu thuẫn C2 hoặc yêu cầu vượt vùng ghi/quyền STEP 2, báo `BLOCKER: SPECIAL NOTE CONFLICT`, không tự chọn một phía hay mở rộng quyền. Tự giải độc lập, không chép từ bài cũ hay lịch sử của worker; không mặc định WHAT/WHY/HOW hoặc tự thêm mốc.
2. Mỗi milestone là **một bộ ba đồng bộ trên cùng hàng**:
   - **F — Title:** tên mốc ngắn, phù hợp sơ đồ Roadmap, không biến thành đoạn văn dài.
   - **G — Purpose:** thông thường 1–2 câu nêu lý do/mục đích học.
   - **H — Objectives:** thông thường 2–4 mục tiêu cụ thể, có thể kiểm chứng, mỗi mục **một dòng trong cùng ô H**; không tự tạo hàng mới cho từng objective.
   Các đặc tả này là cách lưu dữ liệu, **không được lấn át ràng buộc nội dung/độ dài của chính C2**.
3. **Chỉ khi toàn vùng Roadmap F3:H<END> và B17 chưa có nội dung:** tạo mới toàn bộ milestone theo C2, một bộ F/G/H mỗi hàng từ hàng 3; không vượt `<END>`. **Nếu bất kỳ Title F, Purpose G, Objectives H hay Introduction B17 đã có nội dung**, kể cả chỉ điền một phần, báo `BLOCKER: EXISTING TARGET CONTENT` và dừng toàn nhiệm vụ. Không fill các G/H còn trống và không viết lại Title cũ.
4. Kiểm tra `F2:H2`: nếu cả ba ô trống, được điền **`Tiêu đề` / `Mục đích` / `Mục tiêu học tập`**. Nếu header hiện có đúng thì giữ nguyên. Nếu header khác nghĩa, bị chiếm một phần bất thường hoặc xung đột, báo `BLOCKER: HEADER CONFLICT`, không tự sửa.
5. Nếu nội dung theo C2 cần nhiều hàng hơn vùng Prime giao, `BLOCKER: ROADMAP EXCEEDS ASSIGNED RANGE` với số hàng cần, không cắt nội dung, không ghi một phần và không lấn sang Menu.
6. **Sau khi tạo Roadmap, đồng thời soạn Introduction Markdown cho `B17`**, dùng làm **toàn văn `content/readme/docs/vi/BASE.md`**. **Đọc `A17` để hiểu đúng nhãn/ngữ cảnh Introduction**, chỉ đọc không sửa, kết hợp C2/Special Note. Có H1 tên module, 1–3 đoạn giới thiệu rõ mục đích, phạm vi, tư duy nền tảng, đối tượng học và lộ trình học từ các milestone; không sao chép nguyên bộ roadmap hay viết dài lan man. Nếu cần có thêm danh sách ngắn về mục tiêu. **Giới thiệu là bài tự đứng được, đúng cú pháp Markdown và đủ đầu mục cơ bản; không TODO/placeholder hoặc nội dung ngoài phạm vi.** Tối đa 30.000 Unicode code points trong **một ô B17**, không chia sang ô khác. Nếu B17 đã có nội dung thì `BLOCKER: EXISTING TARGET CONTENT`, dừng; không tự QA để công nhận UNCHANGED.

## W3. Preflight và ghi đúng những ô được phép

1. **Preflight toàn bộ `F3:H<END>` và `B17` trước cả khi soạn**. Chỉ chuẩn bị Roadmap hoàn chỉnh và Introduction B17 nếu **không có bất kỳ nội dung cũ nào trong cả hai vùng**. Nếu có, `BLOCKER: EXISTING TARGET CONTENT`, `WRITE=NOT_RUN` rồi dừng ngay, không lấp phần thiếu, không công nhận `NO WRITE NEEDED`.
2. Ngay trước khi ghi, đọc lại **nguyên văn `C2`, cả hai ô Special Note, `A17`, toàn vùng `F2:H<END>` và `B17`**; tất cả nguồn/nhãn phải unchanged. **Toàn bộ vùng nội dung F3:H<END> và B17**, không chỉ các ô dự định ghi, không được chứa `userEnteredValue` (kể cả formula), `effectiveValue`, `formattedValue`, `note`, `dataValidation` xung đột, `chipRuns` hoặc nội dung khác. Có bất kỳ ô bị chiếm → `BLOCKER: EXISTING TARGET CONTENT`; nguồn/nhãn thay đổi → `BLOCKER: SOURCE CHANGED`. **Dừng, không ghi chồng hoặc ghi dở**.
3. Ghi duy nhất các ô trống thực sự cần dùng, với **`updateCells`, `fields=userEnteredValue`** (có thể batch nhiều ô thuộc cùng phạm vi), không gửi cập nhật lên các ô phải giữ nguyên. Không thay đổi định dạng, notes, validations hay các field khác; không insert/delete/merge hàng.
4. Nếu write lỗi hoặc kết quả không rõ: **đọc lại trạng thái thực tế trước**, không tự retry ghi mù hay overwrite.

## W4. Readback và QA

1. Đọc lại **toàn bộ `F2:H<END>`, `A17` và `B17`** sau write; so khớp **nguyên văn từng ô** đã ghi (bao gồm Unicode, xuống dòng, khoảng trắng) và xác minh không có nội dung ngoài quyền bị sửa. Kiểm tra lại **`C2`, `A17` và hai ô Special Note unchanged**.
2. Đảm bảo mỗi Title có Purpose và Objectives khớp nội dung, các ô của cùng milestone nằm **cùng một hàng**, không có mốc/ô phát sinh sai, không vượt `<END>` và không có mục tiêu bị tách thành các hàng riêng. QA cả số mục, thứ tự, độ ngắn của Title, yêu cầu C2 **và ghi chú Special Note khi có nội dung**.
3. Kiểm tra thêm **Introduction B17**: đúng module, phù hợp roadmap/Special Note, Markdown hợp lệ, không vượt một ô, không thiếu giới thiệu cơ bản. Chỉ báo `COMPLETED` khi **ROADMAP + INTRODUCTION READBACK PASS và CONTENT_QA PASS**.

## W5. Báo cáo rồi SLEEPING

Báo Prime: `STATUS=COMPLETED/BLOCKER/FAILED`, `SHEET/gid`, `SOURCE=C2`, `SPECIAL_NOTE_CELLS` (đã đọc/applied), **`INTRODUCTION_LABEL=A17` (đã đọc, unchanged)**, `OUTPUT_RANGE` + ô thực ghi, `MILESTONE_COUNT`, **`INTRODUCTION=B17`, `INTRODUCTION_QA`, `INTRODUCTION_READBACK`**, `PREFLIGHT`, `CONSISTENCY`, `READBACK=PASS/FAIL`, `CONTENT_QA=PASS/FAIL`, `PRESERVED`, `WRITE=NOT_RUN` nếu blocker và `BLOCKERS`.

**Chỉ một roadmap cho tab được giao.** Báo xong, release ownership và SLEEPING; không nhận module khác, không chạy STEP khác, không chỉnh `CURRENT_STEP.VALUE` hoặc ô Special Note.
