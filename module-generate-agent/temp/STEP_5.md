# STEP 5 — PRIME ONLY: Independent Chapter Review & Refactor

## Mục tiêu

Sau STEP 4, **mỗi Menu Cấp 2 đã có một bài học README Markdown độc lập** ở các ô `C:G` cùng hàng. STEP 5 kiểm tra và chỉnh sửa **toàn bộ các bài học thuộc MỘT Menu Cấp 1 như một chương thống nhất**, thay vì review từng bài rời rạc.

**Mỗi sub-worker nhận trách nhiệm trực tiếp và duy nhất đối với một Menu Cấp 1 (một chương, bao gồm TẤT CẢ mục cấp 2 của chương đó).** Một worker có **quyền ghi độc quyền** vào các ô câu trả lời của chương được giao trong thời gian làm việc. Các chương khác nhau có thể do các worker khác nhau xử lý song song, nhưng **không chia một chương cho nhiều worker và không có hai writer trên cùng hàng**.

**Tối đa 3 sub-worker trong pool, tính cả ACTIVE và SLEEPING**. Tuân thủ `GENERAL_AGENT_RULES.md`; tái sử dụng worker SLEEPING trước, không spawn khi còn worker SLEEPING.

### Phân tách PRIME / WORKER (giống STEP 4)

- **File `STEP_5.md` này chỉ dành cho Prime; worker không đọc.**
- **Toàn bộ hướng dẫn thực thi dành cho worker nằm trong `/java-learning/module-generate-agent/temp/STEP_5_WORKER.md`.** Worker phải **tự đọc toàn văn file worker-only**, kể cả sau context compaction; Prime tuyệt đối không rút gọn quy trình review/refactor thành tin nhắn ngắn thay cho tài liệu. Worker không đọc STEP Prime, STEP khác, GENERAL, rules hoặc TXT.
- Prime phải đọc `GENERAL_AGENT_RULES.md`, **liệt kê và đọc tất cả file hiện có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, toàn bộ cả `STEP_5.md` và `STEP_5_WORKER.md` trước khi giao; đọc lại khi mất context.
- **Tin nhắn Prime giao worker chỉ gồm 4 dòng định vị:** link tab, tọa độ ô nguồn, vùng ghi của đúng chương, đường dẫn file worker-only. Không thêm lời dẫn, rules, giải thích, nội dung README, bản refactor mẫu hoặc tóm tắt. **Một lần giao = một chương = một lượt thực hiện trọn vẹn**.

## 1. Phạm vi và cấu trúc Google Sheets

| Thành phần | Ô/vùng | Quy định |
| --- | --- | --- |
| Nhãn và tên module | `A1`, `B1` | Ngữ cảnh, **chỉ đọc**. |
| Menu Cấp 1 của chương | `A<PARENT_ROW>` | Prime gửi **chính xác một ô cha**. |
| Menu Cấp 2 của chương | `B<START_ROW>:B<END_ROW>` | Prime gửi **đúng khoảng hàng con**, không giao worker tự quét/tìm. |
| README của từng mục cấp 2 | `C<R>:G<R>` với mỗi `R` là hàng con được giao | Đọc, review, refactor và **chỉ ghi lại tại đúng hàng R**. |

- Mỗi Menu Cấp 1 ở **một hàng riêng trong cột A**, ô B cùng hàng trống; hàng cấp 2 ở các hàng B liên tiếp bên dưới cho tới trước Menu Cấp 1 kế tiếp.
- Ví dụ thuần tọa độ: chương tại `A13`, các mục `B14:B20` thì worker đọc `A1`, `B1`, `A13`, `B14:B20` và `C14:G20`; chỉ được cập nhật các ô câu trả lời `C14:G20`, **không viết vào hàng 13 hoặc hàng 21 trở đi**.
- Phải nối các ô câu trả lời cùng hàng theo thứ tự **C → D → E → F → G, không tự thêm ký tự ngăn cách**, mới có được Markdown nguyên bản của từng bài. Không nhầm từng ô C/D/E/F/G thành những bài riêng.
- **Giới hạn hiện hành của STEP 5:** tối đa **30.000 ký tự/ô**, **5 ô C–G**, **150.000 ký tự/bài**, không sử dụng H trở đi hoặc hàng khác. STEP 4 hiện cho phép C:M; nếu bất kỳ bài nào đã có nội dung ở H:M, Prime báo `BLOCKER: INCOMPLETE STEP 4 CHAPTER` (sai phạm vi STEP 5) và **không tự sửa/cắt bỏ phần đó** khi chỉ được giao tách tài liệu. Không ghi thêm cột/hàng, không sửa `A/B`, `Template`, Roadmap, header hay file source.

## 2. Trách nhiệm của Prime

1. Trước khi giao, **Prime tự đọc** `/java-learning/module-generate-agent/temp/GENERAL_AGENT_RULES.md` và `/java-learning/module-generate-agent/temp/module-generate-info.txt`. Lấy URL từ `Excel Page URL`, mapping `<TÊN MODULE> sheet id : <SHEET_ID>`; xây dựng URL trực tiếp `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`. Không hardcode, không nhầm gid với spreadsheet ID. Xác minh tab, `A1`, `B1` và tên module.
2. **Prime tự đọc vùng A/B của Menu** để xác định từng chương, chính xác `PARENT_CELL` và danh sách/vùng `CHILD_CELLS` (chỉ những hàng có B là mục cấp 2). Nếu cấu trúc bị thiếu/sai, hai cột có dữ liệu cùng hàng hoặc không xác định được chương, báo `BLOCKER` thay vì đoán.
3. Trước khi giao, Prime kiểm tra các bài đã sinh trong `C:G` của chương. Nếu hàng mục cấp 2 không có bài từ STEP 4 hoặc bài nằm ngoài C:G, ghi nhận `BLOCKER: INCOMPLETE STEP 4 CHAPTER`; **không coi chương đã sẵn sàng refactor đầy đủ**. Không tự tạo bài mới thay STEP 4.
4. **Kiểm tra trạng thái pool** và chỉ điều phối trong giới hạn **3 sub-worker ACTIVE + SLEEPING**. Tái sử dụng worker SLEEPING; **không spawn khi còn worker SLEEPING**. Nếu pool hiện có bất thường nhiều hơn 3 worker revivable/active, không tạo thêm worker hoặc đánh thức để vượt giới hạn; báo/điều phối lại theo GENERAL, không tự kết thúc worker không thuộc nhiệm vụ này.
5. **Mỗi worker chỉ nhận 1 chương tại một thời điểm.** Prime quản lý bảng `PARENT_CELL → CHILD_CELLS → OWNER_WORKER → STATUS`, khóa phạm vi ghi trước khi giao. Không giao cùng chương cho nhiều worker; không sửa trực tiếp những ô một worker đang sở hữu.
6. **Prime gửi duy nhất tọa độ và file worker-only:** link tab, `A1`, `B1`, đúng ô Menu Cấp 1, dải/danh sách Menu Cấp 2, các ô README C:G của chúng, vùng C:G được quyền thay thế và đường dẫn `STEP_5_WORKER.md`. **Không gửi nội dung README, kết quả phân tích trùng lặp, chỉ thị QA hoặc bản refactor gợi ý thay worker.**
7. Prime chỉ tiếp tục giao chương khác cho worker **sau khi worker đã báo cáo, xác minh và giải phóng ownership**. Không khởi động STEP tiếp theo, không commit/push hoặc sửa các file khác.

### Mẫu tin nhắn Prime giao một worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL>, <CHILD_B_RANGE>, <SOURCE_ANSWER_RANGE_C_TO_G>
<WRITE_RANGE_C_TO_G_CỦA_CÁC_HÀNG_CON>
/java-learning/module-generate-agent/temp/STEP_5_WORKER.md
```

Prime thay placeholder bằng tọa độ/range thật. Nếu hàng con không liên tiếp, liệt kê đủ các ô nguồn và đích tương ứng trong hai dòng tọa độ. **Chỉ có worker đọc toàn bộ `STEP_5_WORKER.md` để biết các quy tắc, Prime không gửi nội dung hoặc tóm tắt bài học.**

## 3. Kiểm tra kết quả và nghiệm thu của Prime

1. Theo dõi ownership `PARENT_CELL → CHILD_ROWS → OWNER_WORKER → STATUS` và không giao tiếp khi chương còn worker sở hữu; tái sử dụng worker SLEEPING chỉ sau khi worker đã báo cáo/release.
2. Đối chiếu báo cáo với dữ liệu thật: `READ_COVERAGE` phải đủ mọi bài C:G của chương, `FINDINGS` và `CHANGES` phải có căn cứ, `READBACK` từng hàng được sửa và `CHAPTER_QA` đều PASS. Prime đọc lại từng bài C:G, nối không dấu phân cách và tự kiểm tra chất lượng, tính đủ nội dung, hạn mức ô/hàng, cũng như dữ liệu ngoài vùng giữ nguyên.
3. Không coi phản hồi `COMPLETED` của worker là bằng chứng độc lập. Khi thiếu bài, chapter QA lỗi hoặc có blocker, ghi rõ tọa độ/trạng thái, xác minh phần đã làm trước khi giao sửa; không retry mù/ghi đè dữ liệu worker khác.
4. Tiếp tục điều phối **tất cả các chương** trong phạm vi STEP, không dừng sau batch đầu. Khi mọi chương của tab đạt QA PASS, Prime mới xác minh `J2 = Current STEP`, kiểm tra trạng thái `J3`, cập nhật an toàn `J3 = STEP 5`, đọc lại nguyên văn theo `PRIME_RULES.md`; nếu chỉ thử nghiệm một số chương, không cập nhật J3.

## 4. Điều kiện hoàn thành STEP 5

- Mỗi Menu Cấp 1 được giao cho **đúng một worker chịu trách nhiệm trọn chương**, pool không vượt 3 worker `ACTIVE` + `SLEEPING`.
- Worker **đã đọc trọn STEP_5_WORKER.md** và **đọc đầy đủ từng bài** của chương, lập bản đồ nội dung, đánh giá/refactor có căn cứ, QA toàn chương.
- Giảm **lặp gây ảnh hưởng chất lượng** nhưng **không đánh đổi chiều sâu, tính tự học, độ chính xác hoặc ví dụ cần thiết** để giảm số chữ. Không thay đổi bài đang tốt chỉ để sửa.
- Chỉ có các ô **C:G thuộc hàng Menu Cấp 2 được giao** mới được phép thay đổi; Markdown sau ghi được kiểm tra exact-match và toàn bộ dữ liệu ngoài phạm vi được bảo toàn.
- Chương chỉ `COMPLETED` khi read coverage đủ, readback từng hàng sửa **PASS** và Chapter QA **PASS**; nếu thiếu bài, vượt sức chứa, lỗi công cụ hoặc xung đột dữ liệu thì phải ghi rõ `BLOCKER/FAILED`.
- STEP 5 toàn module hoàn thành khi **mọi chương** đã được review/refactor và Prime xác minh trạng thái; không tự chạy STEP tiếp theo hoặc commit/push.
