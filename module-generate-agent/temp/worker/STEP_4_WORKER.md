# STEP 4 — WORKER ONLY: Một bài Knowledge README trên Google Sheets

> **Chỉ dành cho worker.** Prime điều phối bằng `STEP_4.md`; worker **chỉ đọc file `worker/STEP_4_WORKER.md` này** để lấy quy trình, không đọc `STEP_4.md`, STEP khác, `GENERAL_AGENT_RULES.md`, file rules, TXT, README cũ hoặc lịch sử hội thoại để làm nội dung. Prime **chỉ gửi link tab, tọa độ bốn ô nguồn, vùng đích và đường dẫn file này; không gửi thêm hướng dẫn/ghi chú**. Worker phải tự thực hiện trọn vẹn W1–W5 theo file này, không cần Prime nhắc lại quy tắc trong tin giao việc.

## W1. Phạm vi một lượt và nguồn dữ liệu

1. Một lượt giao việc = **duy nhất một Menu Cấp 2 tại `B<R>`**, một README Markdown hoàn chỉnh, một hàng câu trả lời `C<R>:N<R>`. Tự làm trọn: đọc → soạn → QA → preflight → ghi → readback → báo cáo → SLEEPING. Không có PHA A/B, PREPARED hay chờ Prime cấp ô giữa chừng.
2. Dùng đúng **link tab/gid Prime giao**; đọc trực tiếp từ Google Sheets **chỉ bốn ô nguồn**: `A1`, `B1`, `A<PARENT_ROW>`, `B<ITEM_ROW>`. Không đọc menu cấp 1/2 khác, câu trả lời hàng khác hay sheet khác. Có thể đọc **duy nhất vùng đích của mình** `C<R>:N<R>` để preflight và readback.
3. `A<PARENT_ROW>` phải xác định đúng ngữ cảnh cha của `B<R>`. `B<R>` là **đề bài trực tiếp**, không tự biến thành câu hỏi mới. Nếu tên/ô nguồn trống, sai tab/gid hoặc không khớp: `BLOCKER: INVALID SOURCE CELLS`; không tự dò hay chọn bài thay thế.
4. Chỉ có quyền thay đổi **giá trị văn bản** các ô cần dùng trong `C<R>:N<R>` trên đúng hàng; không sửa `A:B`, Roadmap, header, `J3`, Difficulty `O`, Video `X:AA`, định dạng, notes, validations, các hàng/tab/file khác. Không spawn worker, commit/push hoặc tự chạy STEP khác.

## W2. Tạo **một chuỗi** README Markdown độc lập

1. Soạn **toàn văn Markdown trong một chuỗi duy nhất trước khi chia ô**; nội dung đúng phạm vi của Menu Cấp 2 và module đã đọc. **KHÔNG GIỚI HẠN SỐ KÝ TỰ KHI TẠO NỘI DUNG**: phải trình bày **đầy đủ, trọn vẹn mọi yêu cầu và kiến thức thiết yếu của đề bài**, không tự tóm tắt, cắt ngắn, bỏ ví dụ/giải thích cần thiết để vừa ô hoặc để đáp ứng một mức độ dài tùy ý. Có tiêu đề phù hợp, khái niệm, cơ chế, cách áp dụng, lưu ý và lỗi phổ biến **khi có giá trị học tập**. Độ dài do mức độ phức tạp của đề quyết định, không kéo dài bằng nội dung thừa/lặp. Không đưa nhãn worker, tiến độ, lời nhắn Prime, TODO hay placeholder vào bài.
2. Dùng cú pháp Markdown hợp lệ; code fence có ngôn ngữ, giải thích code và output đáng tin. Với bài Java, ưu tiên ví dụ **Java 17+ biên dịch/chạy được**, ghi lệnh `javac`/`java` và stdout **chính xác** khi phù hợp; phân biệt rõ mã minh họa không độc lập với chương trình chạy đầy đủ. Bảng, Mermaid, flow, tình huống trước–sau chỉ dùng khi hữu ích; không ép mọi bài theo một khuôn.
3. Kiểm tra kiến thức, cú pháp, tính runnable, tên tệp/class, kết quả stdout, heading/code fence/bảng, dấu tiếng Việt, và giới hạn phạm vi. Không bịa hành vi Java/API hay tái sử dụng nội dung từ bài khác. Không ép độ dài tối thiểu, tránh lan man.
4. **Giữ nguyên chuỗi Markdown gốc** để đối chiếu sau khi ghi. Không sửa hoặc thêm ký tự khi chia nội dung.

## W3. Chia ô đúng dung lượng — **không chia nếu chưa cần**

1. **Không hạn chế độ dài khi soạn bài.** Mốc 30.000 ký tự là **giới hạn lưu trữ trên một ô**, KHÔNG phải giới hạn độ dài của README. Vùng cho phép ghi trên một hàng là 12 ô C:N, sức chứa tối đa 360.000 ký tự. Đếm cẩn thận Unicode/UTF-16 và không cắt giữa surrogate pair; nếu cách đếm khác nhau, dùng ngưỡng an toàn hơn.
2. Chỉ **SAU KHI đã viết đầy đủ toàn bộ README** mới chia bản gốc theo đúng thứ tự thành các phần: **mỗi phần trước phần cuối đúng 30.000 ký tự**, lưu lần lượt vào **C, D, E, …, N** trên **cùng một hàng**. Không phân chia theo số heading, không san đều giữa các ô, không chủ động chia thành 12 phần và không bỏ/chèn bất kỳ ký tự nào (ngoại lệ duy nhất: tránh cắt giữa một ký tự Unicode).
3. **Phần cuối nếu ít hơn 30.000 ký tự thì GIỮ NGUYÊN toàn bộ phần dư và ghi vào DUY NHẤT một ô cuối; KHÔNG chia nhỏ phần dư để phân phối sang các ô còn lại.** Nếu cả bài ≤30.000 ký tự, chỉ ghi `C<R>`; D:N vẫn trống. Ví dụ: 29.000 → C=29.000; 60.000 → C=30.000, D=30.000; 62.000 → C=30.000, D=30.000, E=2.000. Ghép các ô có nội dung từ C sang phải **không thêm separator** phải tái lập 100% README ban đầu, kể cả xuống dòng, khoảng trắng, code fence.
4. Nếu bài đầy đủ cần >12 ô, báo `BLOCKER: CONTENT EXCEEDS C:N CAPACITY` và tổng số ký tự/số ô thực sự cần; **KHÔNG rút ngắn nội dung để vừa giới hạn, không ghi một phần**, không truncate, không thêm hàng/cột hoặc lấn sang Difficulty O/Video X:AA.

## W4. Preflight, ghi một lần và readback

1. **Ngay trước WRITE**, đọc lại **bốn ô nguồn** và **cả 12 ô** `C<R>:N<R>` (kể cả các ô không định ghi). Các ô đích phải hoàn toàn trống đối với `userEnteredValue` (gồm formula), `effectiveValue`, `formattedValue`, `note`, `dataValidation`, `chipRuns`. Bốn nguồn phải **unchanged** so với lúc bắt đầu. Nếu bất cứ ô đích nào có dữ liệu/metadata hoặc nguồn thay đổi, báo `BLOCKER: TARGET CELLS NOT EMPTY` / `BLOCKER: SOURCE CHANGED` và **không ghi**.
2. Xác định số ô **thực sự cần** theo W3; ghi **một lần** bằng `updateCells` với `fields=userEnteredValue`, chỉ những ô cần ghi, bắt đầu từ C trên đúng hàng. **Không ghi các đoạn rỗng** vào ô còn lại; không cập nhật `userEnteredFormat`, `note`, `dataValidation`, `chipRuns`, hàng khác hoặc bất kỳ field nào khác.
3. Nếu công cụ báo lỗi hoặc kết quả write không rõ, **đọc lại trạng thái thực** trước; không tự retry ghi đè. Không sửa một câu trả lời đã tồn tại hoặc thuộc worker khác.
4. READBACK **từng ô trong cả 12 ô C:N**: các mảnh đã ghi phải khớp riêng từng ô; các ô còn lại phải vẫn trống. Nối theo thứ tự C→N **không separator**, so khớp **exact 100% Unicode, whitespace, newline** với chuỗi Markdown gốc. Đọc lại bốn nguồn và xác nhận unchanged.
5. QA sau readback: đủ nội dung, Markdown hợp lệ, code fence cân bằng, ví dụ và output đáng tin, không thiếu/trùng đoạn, giới hạn mỗi ô/toàn bài PASS, ngoài phạm vi được bảo toàn. **Chỉ báo COMPLETED khi cả READBACK và QA đều PASS**, không chỉ dựa vào phản hồi write thành công.

## W5. Báo cáo một lần rồi SLEEPING

Gửi Prime một báo cáo có: `STATUS=COMPLETED/BLOCKER/FAILED`, `SHEET/gid`, `SOURCE=A1,B1,A<PARENT>,B<R>`, `OUTPUT_RANGE` (phân biệt một ô C với nhiều ô), `TOTAL_CHARS`, `CHARS_PER_CELL`, `PREFLIGHT`, `READBACK=PASS/FAIL`, `CONTENT_QA=PASS/FAIL`, `PRESERVED`, `BLOCKERS`.

**Một nhiệm vụ, một lần giao, chỉ một bài.** Báo cáo xong thì giải phóng ownership và SLEEPING; không tự nhận hàng mới, tự sửa file repo, cập nhật `J3` hoặc thực hiện hành động ngoài phạm vi.
