# STEP 5 — PRIME ONLY: Chapter Review, Difficulty & English Filename (gộp STEP 6 cũ)

## Mục tiêu

Sau STEP 4, **mỗi Menu Cấp 2 đã có một bài học README Markdown độc lập** ở các ô `C:N` cùng hàng. STEP 5 kiểm tra và chỉnh sửa **toàn bộ các bài học thuộc MỘT Menu Cấp 1 như một chương thống nhất**, **gán Difficulty tại O trên mỗi hàng Menu Cấp 2**, và **chính worker của chương đó đặt/ghi filename tiếng Anh `.md` vào B của hàng Menu Cấp 1**. Toàn bộ nhiệm vụ đặt filename STEP 6 cũ đã gộp vào đây; **STEP 6 hiện là Interview YAML, Video được chuyển sang STEP 8**.

**Mỗi sub-worker nhận trách nhiệm trực tiếp và duy nhất đối với một Menu Cấp 1 (một chương, bao gồm TẤT CẢ mục cấp 2 của chương đó).** Một worker có **quyền ghi độc quyền** vào các ô câu trả lời của chương được giao trong thời gian làm việc. Các chương khác nhau có thể do các worker khác nhau xử lý song song, nhưng **không chia một chương cho nhiều worker và không có hai writer trên cùng hàng**.

**Hạn mức pool theo Mode A/B/C được đính kèm trong prompt của session (mặc định A=3), tính cả ACTIVE và SLEEPING**. Tuân thủ `GENERAL_AGENT_RULES.md` và `PRIME_RULES.md §0`; tái sử dụng worker SLEEPING trước, không spawn khi còn worker SLEEPING.

### Phân tách PRIME / WORKER (giống STEP 4)

- **File `STEP_5.md` này chỉ dành cho Prime; worker không đọc.**
- **Toàn bộ hướng dẫn thực thi dành cho worker nằm trong `/java-learning/module-generate-agent/temp/worker/STEP_5_WORKER.md`.** Worker phải **tự đọc toàn văn file worker-only**, kể cả sau context compaction; Prime tuyệt đối không rút gọn quy trình review/refactor thành tin nhắn ngắn thay cho tài liệu. Worker không đọc STEP Prime, STEP khác, GENERAL, rules hoặc TXT.
- Prime phải đọc `GENERAL_AGENT_RULES.md`, **liệt kê và đọc tất cả file hiện có trong `module-generate-agent/rules/`**, `module-generate-info.txt`, toàn bộ cả `STEP_5.md` và `worker/STEP_5_WORKER.md` trước khi giao; đọc lại khi mất context.
- **Tin nhắn Prime giao worker chỉ gồm 4 dòng định vị:** link tab, tọa độ ô nguồn, vùng ghi của đúng chương, đường dẫn file worker-only. Không thêm lời dẫn, rules, giải thích, nội dung README, bản refactor mẫu hoặc tóm tắt. **Một lần giao = một chương = một lượt thực hiện trọn vẹn**.

## 1. Phạm vi và cấu trúc Google Sheets

| Thành phần | Ô/vùng | Quy định |
| --- | --- | --- |
| Nhãn và tên module | `A1`, `B1` | Ngữ cảnh, **chỉ đọc**. |
| Menu Cấp 1 của chương | `A<PARENT_ROW>` | Prime gửi **chính xác một ô cha**, chỉ đọc. |
| Filename của chương | `B<PARENT_ROW>` | **Worker cùng chương tự chọn và điền vào ô B cha nếu còn trống**; B cha có filename hợp lệ thì giữ nguyên. Prime KHÔNG viết filename. |
| Menu Cấp 2 của chương | `B<START_ROW>:B<END_ROW>` | Prime gửi **đúng khoảng hàng con**, không giao worker tự quét/tìm. |
| README của từng mục cấp 2 | `C<R>:N<R>` với mỗi `R` là hàng con được giao | Đọc, review, refactor và **chỉ ghi lại tại đúng hàng R**. |
| Difficulty của từng mục cấp 2 | `O<R>` trên hàng con R | Chọn đúng một giá trị `BASIC` / `INTERMEDIATE` / `ADVANCED`; không ghi O hàng cha. |

- Mỗi Menu Cấp 1 ở **một hàng riêng trong cột A**; ô B cùng hàng **ban đầu trống** hoặc đã có filename .md. Nhận diện hàng cha từ A có text; B của hàng cha **không phải Menu Cấp 2**. Hàng cấp 2 ở các hàng B liên tiếp bên dưới có A trống, cho tới trước Menu Cấp 1 kế tiếp.
- Ví dụ thuần tọa độ: chương tại `A13`, các mục `B14:B20` thì worker đọc `A1`, `B1`, `A13`, `B13`, `B14:B20`, `C14:N20` và `O14:O20`; được ghi **`B13` (nếu trống), `C14:N20`, `O14:O20`**. Không sửa A13, B14:B20, O13, hoặc các hàng 21 trở đi.
- Phải nối các ô câu trả lời cùng hàng **C → N, không tự thêm ký tự ngăn cách**, mới có được Markdown nguyên bản của từng bài. Không nhầm các mảnh thành bài riêng.
- **Giới hạn hiện hành của STEP 5:** tối đa **30.000 ký tự/ô Knowledge**, **12 ô C–N**, **360.000 ký tự/bài**. **O hàng con chỉ dùng cho Difficulty**; **B hàng cha là filename `.md`**; P:S dùng cho Video, ngoài phạm vi STEP 5. Không sửa A, B hàng con, O hàng cha, Template, Roadmap, header, Video hay file source.
- Filename phải là ASCII PascalCase/acronym hợp lệ theo importer: `^[A-Za-z][A-Za-z0-9]*\.md$`, đại diện đúng ngữ nghĩa cả chương; không chọn tên mơ hồ kiểu `Chapter1.md`/`Topic.md`, tên hệ thống Windows `CON`, `PRN`, `AUX`, `NUL`, `COM1–COM9`, `LPT1–LPT9`. **Trùng tên case-insensitive hoặc trùng kebab-case anchor prefix được Prime kiểm tra CUỐI CÙNG trên toàn tab sau khi mọi worker đã tự điền**; không yêu cầu Prime chọn hoặc điền tên thay worker.

## 2. Trách nhiệm của Prime

1. Trước khi giao, **Prime tự đọc** `/java-learning/module-generate-agent/temp/GENERAL_AGENT_RULES.md` và `/java-learning/module-generate-agent/temp/module-generate-info.txt`. Lấy URL từ `Excel Page URL`, mapping `<TÊN MODULE> sheet id : <SHEET_ID>`; xây dựng URL trực tiếp `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`. Không hardcode, không nhầm gid với spreadsheet ID. Xác minh tab, `A1`, `B1` và tên module.
2. **Prime tự đọc vùng A/B của Menu** để xác định từng chương, chính xác `PARENT_CELL`, `PARENT_FILENAME_CELL` và danh sách/vùng `CHILD_CELLS` (chỉ những hàng A trống và B là mục cấp 2). **B trên hàng A được phép còn trống**; nếu đã có filename sai định dạng hoặc có formula/note xung đột thì báo `BLOCKER: INVALID PARENT FILENAME` trước khi giao. Prime không đặt tên thay worker. Không xác định được chương thì blocker, không đoán.
3. Trước khi giao, Prime kiểm tra các bài đã sinh trong `C:N` của chương. Nếu hàng mục cấp 2 không có bài từ STEP 4 hoặc bài nằm ngoài C:N, ghi nhận `BLOCKER: INCOMPLETE STEP 4 CHAPTER`; **không coi chương đã sẵn sàng refactor đầy đủ**. Kiểm tra header `C19:N19 = Câu trả lời`, `O19 = Difficulty`, `P19:S19 = Video Script`. Đọc các O hàng con; nếu có giá trị ngoài ba lựa chọn, formula hoặc note xung đột thì blocker. Không tự tạo bài mới thay STEP 4.
4. **Kiểm tra trạng thái pool** và chỉ điều phối trong hạn mức **Mode A/B/C của session (3/6/8 worker ACTIVE + SLEEPING)**. Tái sử dụng worker SLEEPING; **không spawn khi còn worker SLEEPING**. Nếu pool hiện có bất thường vượt hạn mức đang áp dụng, không tạo thêm worker hoặc đánh thức vượt giới hạn; báo/điều phối lại theo GENERAL.
5. **Mỗi worker chỉ nhận 1 chương tại một thời điểm.** Prime quản lý bảng `PARENT_CELL → PARENT_FILENAME_CELL → CHILD_CELLS → OWNER_WORKER → STATUS`, khóa phạm vi ghi **B hàng cha và C:N/O của mọi hàng con** trước khi giao. Không giao cùng chương cho nhiều worker; không sửa trực tiếp những ô một worker đang sở hữu. Worker của các chương khác được phép làm song song, không cần biết tên dự kiến của nhau.
6. **Prime gửi duy nhất tọa độ và file worker-only:** link tab, `A1`, `B1`, **A/B hàng Menu Cấp 1**, dải/danh sách B Menu Cấp 2, các ô README C:N và Difficulty O của hàng con; vùng **B cha (nếu trống), C:N và O hàng con** được quyền cập nhật, và đường dẫn `worker/STEP_5_WORKER.md`. **Không gửi tên file đề xuất, nội dung README, kết quả phân tích trùng lặp hoặc bản refactor mẫu.**
7. Prime chỉ tiếp tục giao chương khác cho worker **sau khi worker đã báo cáo, xác minh và giải phóng ownership**. Không khởi động STEP tiếp theo, không commit/push hoặc sửa các file khác.

### Mẫu tin nhắn Prime giao một worker — CHỈ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <CHILD_B_RANGE>, <SOURCE_ANSWER_RANGE_C_TO_N>, <SOURCE_DIFFICULTY_RANGE_O>
<WRITE_PARENT_FILENAME_CELL_B_NẾU_TRỐNG>, <WRITE_RANGE_C_TO_N_CỦA_CÁC_HÀNG_CON>, <WRITE_DIFFICULTY_RANGE_O_CỦA_CÁC_HÀNG_CON>
/java-learning/module-generate-agent/temp/worker/STEP_5_WORKER.md
```

Prime thay placeholder bằng tọa độ/range thật. Nếu hàng con không liên tiếp, liệt kê đủ các ô nguồn và đích tương ứng trong hai dòng tọa độ. **Chỉ có worker đọc toàn bộ `worker/STEP_5_WORKER.md` để biết các quy tắc, Prime không gửi nội dung hoặc tóm tắt bài học.**

## 3. Kiểm tra kết quả và nghiệm thu của Prime

1. Theo dõi ownership `PARENT_CELL → PARENT_FILENAME_CELL → CHILD_ROWS → OWNER_WORKER → STATUS` và không giao tiếp khi chương còn worker sở hữu; tái sử dụng worker SLEEPING chỉ sau khi worker đã báo cáo/release.
2. Đối chiếu báo cáo với dữ liệu thật: `READ_COVERAGE` phải đủ mọi bài C:N của chương, `FINDINGS` và `CHANGES` phải có căn cứ, `READBACK` từng hàng được sửa và `CHAPTER_QA` đều PASS. Prime đọc lại từng bài C:N, nối không dấu phân cách, **xác minh từng O hàng con và B hàng cha**. Mỗi worker phải tự ghi tên file vào B cha trống trong phiên; Prime chỉ kiểm tra, tuyệt đối không điền thay.
3. Không coi phản hồi `COMPLETED` của worker là bằng chứng độc lập. Khi thiếu bài, chapter QA lỗi hoặc có blocker, ghi rõ tọa độ/trạng thái, xác minh phần đã làm trước khi giao sửa; không retry mù/ghi đè dữ liệu worker khác.
4. **Kiểm tra filename TOÀN TAB ở CUỐI STEP 5**, sau khi worker của **mọi chương** đã điền/readback B cha: đọc lại tất cả A/B cha theo thứ tự, kiểm tra không thiếu filename, regex, tên có nghĩa với A và nhóm bài, không trùng basename **case-insensitive**, không trùng **kebab-case prefix** theo quy tắc Gradle, không dùng tên dành riêng Windows. Không coi worker `COMPLETED` riêng lẻ là đủ để vượt qua kiểm tra uniqueness toàn tab. Có trùng → `BLOCKER: DUPLICATE PARENT FILENAME` nêu chính xác các ô B/tên và **KHÔNG cập nhật J3**.
5. Nếu phát hiện trùng, **chỉ giao lại chính worker sở hữu chương gây trùng** (ưu tiên worker SLEEPING đã dùng, không spawn mới khi còn worker rảnh) với đúng bốn dòng phạm vi **A1/B1, A/B hàng cha, B các hàng con; chỉ B cha được sửa** và worker-only STEP 5. Worker tự chọn filename khác, preflight B cha đang chứa tên cũ chính xác và tự update/readback chỉ B cha; **Prime không tự sửa B**. Đây là **lượt correction riêng CHỈ filename**, không đọc/refactor/ghi lại Knowledge hoặc Difficulty; kết quả worker `COLLISION_FIXED` không tự coi STEP 5 hoàn tất. Việc sửa tên đã ghi **chỉ được phép trong nhiệm vụ khắc phục trùng tên do Prime xác thực/giao lại đúng owner**, không phải quyền ghi đè B thông thường. Nếu không thể giao lại đúng owner hoặc không bảo đảm không xung đột, dừng BLOCKER thay vì ép tên hoặc ghi đè. Kiểm tra lại uniqueness toàn tab sau mọi lần sửa.

### Mẫu giao worker SỬA TRÙNG sau QA cuối — cũng chỉ bốn dòng

```text
<LINK_TAB_ĐÃ_XÁC_MINH>
A1, B1, <PARENT_CELL_A>, <PARENT_FILENAME_CELL_B>, <CHILD_B_RANGE>
<WRITE_ONLY_PARENT_FILENAME_CELL_B_ĐÃ_CÓ_TÊN_TRÙNG>
/java-learning/module-generate-agent/temp/worker/STEP_5_WORKER.md
```

Đây là phạm vi ghi B cha đã được Prime xác minh xung đột, **không phải yêu cầu worker chạy lại toàn bộ STEP 5**, và không ủy quyền đọc/ghi C:N/O của bài con.
6. Tiếp tục điều phối **tất cả các chương** trong phạm vi STEP, không dừng sau batch đầu. Khi **mọi chương đạt QA và toàn bộ filename duy nhất**, Prime **BẮT BUỘC giải quyết sync README đã hoãn ở STEP 3/4**: chạy task **đúng module** `generateModuleReadmeFromSheetByLocation` lần lượt `-PsheetMode=DRY_RUN` → `APPLY` với `-PfileUrl=<URL> -PsheetID=<GID>` đã xác minh theo `GENERAL_AGENT_RULES.md §6`. Importer đồng thời sinh/đồng bộ **`content/readme/metadata/vi/knowledge-metadata.yml`** từ các ID Knowledge (trong H2 anchor) và cột O Difficulty; cột O trống → dùng `BASIC` nếu chưa có metadata, giữ `aiGenerated=true`, `reviewed=false` mặc định khi chưa có và bảo toàn giá trị chỉnh sửa hợp lệ. Prime kiểm tra **cả Markdown trong `content/readme/docs/vi/menu` và metadata** sau APPLY (Difficulty phản ánh O theo bài, ID khớp, không trùng, không có metadata orphan). **Bất kỳ `SKIPPED_MISSING_FILENAME`, metadata validation lỗi, lỗi import/config đều là BLOCKER**; không dùng ByFiles hoặc task tổng hợp. Chỉ khi cả hai output PASS/UNCHANGED Prime mới cập nhật an toàn `J3 = STEP 5` và exact readback. STEP 6 vẫn là Interview YAML, STEP 8 là Video, không tự chạy.

## 4. Điều kiện hoàn thành STEP 5

- Mỗi Menu Cấp 1 được giao cho **đúng một worker chịu trách nhiệm trọn chương**, pool không vượt hạn mức Mode A/B/C của session.
- Worker **đã đọc trọn worker/STEP_5_WORKER.md** và **đọc đầy đủ từng bài** của chương, lập bản đồ nội dung, đánh giá/refactor có căn cứ, QA toàn chương.
- Giảm **lặp gây ảnh hưởng chất lượng** nhưng **không đánh đổi chiều sâu, tính tự học, độ chính xác hoặc ví dụ cần thiết** để giảm số chữ. Không thay đổi bài đang tốt chỉ để sửa.
- Chỉ có các ô **C:N/O thuộc hàng Menu Cấp 2 và B hàng Menu Cấp 1 được giao** mới được phép thay đổi; Markdown, Difficulty và filename đều phải exact readback; B các hàng con và toàn bộ dữ liệu ngoài phạm vi được bảo toàn.
- Mỗi hàng Menu Cấp 2 phải có **đúng một Difficulty**: `BASIC` (kiến thức nền), `INTERMEDIATE` (vận dụng/phối hợp và trade-offs), `ADVANCED` (chiều sâu, cơ chế phức tạp, giới hạn/edge cases). Đánh giá dựa trên **nội dung thực sau review**, không đoán theo độ dài bài, thứ tự hay tên file. Ô O hàng Menu Cấp 1 không được ghi.
- Chương chỉ `COMPLETED` khi read coverage đủ, filename B cha hợp lệ/được worker tự ghi, readback từng hàng sửa **PASS** và Chapter QA **PASS**; nếu thiếu bài, vượt sức chứa, lỗi công cụ hoặc xung đột dữ liệu thì phải ghi rõ `BLOCKER/FAILED`.
- STEP 5 toàn tab hoàn thành khi **mọi chương** đã được review/refactor/đánh Difficulty/điền filename, **Prime kiểm tra cuối cùng không trùng tên/prefix và đồng bộ README thật về module qua ByLocation PASS**. Chỉ **phần đặt filename của STEP 6 cũ** được gộp vào STEP 5; **STEP 6 hiện tại là Generate Interview YAML**, STEP 8 là Video. Không tự chạy STEP khác hoặc commit/push.
