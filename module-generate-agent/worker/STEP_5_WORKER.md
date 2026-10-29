# STEP 5 — WORKER ONLY: Chapter Review, Summary, Liên kết bài học và Package Skeleton

**STEP status:** This worker must never read or update progress in Google Sheets/Excel or `master.json`. Prime alone records `CURRENT_STEP.VALUE` after whole-STEP QA.


> **CURRENT Summary contract:** Worker fills B filename and C base ID at parent, AC Markdown at children and authored-only YAML at AC parent. Prime fills A child IDs after QA. Summary importer joins AC entries to child A/B by index, materializes full section.yml with derived id/order/title/to. AC parent never contains those technical fields.

> Chỉ dành cho worker. Prime dùng STEP_5.md để điều phối; bạn bắt buộc tự đọc TOÀN VĂN đúng file worker/STEP_5_WORKER.md này trước khi làm, đọc lại nếu mất context. Không đọc STEP_5.md, STEP khác, file rules/TXT hoặc dùng lịch sử hội thoại làm tiêu chuẩn. Prime đã **chuyển MODULE_TYPE=SERVLET, bật BUILD_SWAGGER, sync Gradle và provision AC18/19 trước khi giao**. Tin giao chỉ có bốn dòng: link tab; A/B hàng cha + C:N/O/AB/AC hàng con + **AC hàng cha + AC18/19 + Special Note**; **vùng ghi B cha, C:N/O/AB/AC của từng hàng con, AC hàng cha và HAI đường dẫn tuyệt đối package Java main/test**; đường dẫn file này. Không gửi tóm tắt tiêu chí refactor, filename gợi ý hay Java code mẫu.

> **STEP 5 ID and AC contract (current):** Worker writes the chapter base ID to C parent (derived from B parent filename: OOPBasic.md -> oop-basic), on top of all existing chapter work. Prime fills A child IDs by code after all workers and filename QA. AC parent must contain authored-only YAML: each roadmap item has only description and transition (description or final null); no id/order/title/to. The Summary importer materializes the full YAML using the A/B child rows. No AI in importers.

## W1. Một chương, một lượt, một owner

1. **Lượt STEP 5 thông thường** chỉ có MỘT Menu Cấp 1 và TOÀN BỘ Menu Cấp 2 thuộc chương đó. Trong **cùng một lượt**, tự đọc trọn chương → preflight hai package main/test được giao → **tạo skeleton package của chương** → lập bản đồ → review/refactor đầy đủ Knowledge → QA bản Knowledge cuối → phân loại Difficulty/isImportant từng bài → đặt/ghi filename tiếng Anh vào B cha → **soạn Summary Markdown 0/1/2/3 cho từng AC hàng con từ Knowledge ĐÃ REVIEW** → **soạn toàn bộ YAML `section.yml` cho AC hàng cha từ toàn chương** → snapshot/preflight → ghi đúng ownership → exact readback → chapter QA **cả Sheet và skeleton** → báo cáo → SLEEPING. Có thể xử lý các bài dài theo từng chunk/lượt đọc nhưng phải giữ bản đồ cả chương và kiểm tra 100% trước khi ghi. Không chia chương cho worker khác hoặc yêu cầu Prime tạo skeleton hộ. **Ngoại lệ duy nhất là lượt sửa trùng filename sau QA toàn tab, giới hạn B cha như W2.5.4: KHÔNG đụng source/skeleton, không chạy lại review hoặc AC.**
2. Trên đúng tab/gid Prime giao, chỉ đọc nguồn A1, B1, **A<PARENT_ROW>, B<PARENT_ROW>**, dải B<CHILD_ROWS>, **toàn bộ C:N, O, AB và AC trên chính các hàng con**, cùng **AC<PARENT_ROW>, AC18, AC19**. Các hàng B được Prime chỉ định cụ thể, không tự dò chương/bài khác hoặc đọc câu trả lời ngoài phạm vi; không bắt buộc đọc tên filename của các chương khác.
3. Worker may write B/C parent, C:N/O/AB/AC each child, and authored-only AC parent YAML within exact chapter ownership; A-child ID fill belongs ONLY to Prime. Skeleton paths remain restricted to the granted main/test directories. Preserve cell capacities, formats, other modules and CURRENT_STEP.VALUE.
4. Kiểm tra ownership **cả Sheet lẫn hai package**; chapter thiếu Knowledge, source sai, chưa được cấp hai đường dẫn main/test, package conflict, module chưa sync hoặc đang có worker khác sở hữu → báo BLOCKER, không viết nửa chừng và không nhận chương thay thế. Không spawn worker, không chạy STEP tiếp theo, không commit/push.

## W1.1. BẮT BUỘC tạo package skeleton trong lượt review STEP 5 thường

1. Prime đã thực hiện **module setup + Gradle configuration sync** trước lúc giao; Worker **chỉ kiểm tra read-only** module đang là `SERVLET`, `BUILD_SWAGGER=TRUE`, đã có `*Application.java` duy nhất do generator/setup quản lý. Không tự sửa `master.json`, `properties.json`, Gradle hoặc tạo Application. Thiếu điều kiện → `BLOCKER: MODULE_SETUP_NOT_READY`, báo Prime xử lý, không tạo skeleton trên cấu hình sai.
2. Dòng thứ ba handoff gồm **hai đường dẫn tuyệt đối**: `<module>/src/main/java/<module-package-root>/<english-chapter-package>/` và `<module>/src/test/java/<module-package-root>/<english-chapter-package>/`. Kiểm tra cùng module, cùng chương, canonical path nằm dưới `src/main/java` / `src/test/java`, package là **TÊN TIẾNG ANH có nghĩa của Menu Cấp 1**, không phải phiên âm tiếng Việt. **Lấy chuẩn đặt tên từ filename Markdown tiếng Anh ở B cha:** bỏ `.md`, đổi PascalCase/acronym sang lowercase snake_case (ví dụ `GenericsOverview.md → generics_overview`, `Class.md → class_chapter` vì `class` là Java keyword); nếu B còn trống, tên English chapter được Prime xác định trước từ nghĩa chương và worker phải tự chọn filename tương ứng khi điền B cha. Kiểm tra tên hợp lệ `[a-z][a-z0-9_]*`, không trùng package/owner khác, không path traversal/symlink. **Không tự tạo tên package bằng cách bỏ dấu/phiên âm tiếng Việt** (ví dụ `dong_goi` là SAI, `encapsulation` mới đúng). **Dùng chính xác package Prime giao**, không tự tìm path khác; nếu tên filename và package không khớp/ngữ nghĩa mâu thuẫn thì báo `BLOCKER: PACKAGE_FILENAME_MISMATCH` cho Prime điều phối ownership, không tự đổi tên B đã có hoặc tạo một package khác.
3. Khi đã xác minh đầy đủ Sheet/Knowledge, config và ownership, **chính worker** tạo các thư mục skeleton dưới `<MAIN_CHAPTER_PACKAGE_PATH>`: `controller/`, `dto/`, `service/`, `model/`, `exception/`, `repository/`; đồng thời tạo **root `<TEST_CHAPTER_PACKAGE_PATH>/`** tương ứng. **Git không theo dõi thư mục rỗng**, nên worker đặt **duy nhất file marker rỗng `.gitkeep` trong từng thư mục mới tạo còn trống** (cả test root) để skeleton tồn tại qua branch/worktree. Không tạo `.java`, `package-info.java` hoặc bất kỳ class/test/source/placeholder có logic nào ở STEP 5. Marker không được chứa nội dung và không được ghi đè marker hiện hữu có thay đổi; Đừng phụ thuộc STEP 6 để dọn marker hoặc package không dùng.
4. Nếu package path đã tồn tại, **kiểm tra từng thư mục con/file hiện có**. Chỉ bổ sung những **thư mục thiếu** trong vùng được cấp; không xóa, di chuyển, overwrite hoặc refactor source đã tồn tại. Có file/owner khác, tên package conflict, path unsafe → blocker trước khi ghi. Không tạo skeleton ở chapter khác. Skeleton là output filesystem độc lập với nội dung Google Sheets và không được thay đổi quyền ghi Sheet.
5. Sau khi tạo, đọc lại/cross-check đúng **6 thư mục main và 1 root test**, marker `.gitkeep` khi thư mục trống, không tạo nguồn Java hay chỉnh file có sẵn; bảo đảm ngoài package cấp quyền không phát sinh file/thư mục nào. Ghi `SKELETON_STATUS=PASS/UNCHANGED/BLOCKER`, `MAIN_PACKAGE_PATH`, `TEST_PACKAGE_PATH`, danh sách thư mục/marker `CREATED/EXISTING` và xung đột để Prime nghiệm thu. **Chỉ khi cả chapter QA và skeleton QA đều PASS/UNCHANGED mới `COMPLETED`**.

## W2. Đọc trọn chương, đánh giá và refactor

### W2.1. Preflight: đọc đầy đủ, không vội viết

1. **Đọc hết file worker/STEP_5_WORKER.md**, xác nhận đúng link tab/gid và **chỉ** những ô `A1`, `B1`, `A<PARENT>`, `B<PARENT>`, `CHILD_CELLS`, `C:N/O/AB/AC` hàng con, **AC hàng cha, AC18/AC19 và Special Note** mà Prime đã định vị. Kiểm tra `O19=Difficulty`, **`AC18=Summary`, `AC19=Summary YAML`** hoặc **nguyên cặp legacy `Learning Objectives` / `Learning Objectives YAML`** đã được Prime xác minh; nhãn `Summary Markdown` cũ không chứng minh AC cha là YAML, phải báo `BLOCKER: STEP_5_AC_SCHEMA_CONFLICT` cho Prime. Worker không tự provision, sửa header hoặc đọc ngoài phạm vi.
2. **Đọc TOÀN BỘ bài học của TẤT CẢ mục cấp 2** trong chương được giao, không review chỉ từ tiêu đề, độ dài, vài đoạn đầu/cuối. Nếu nhiều nội dung, đọc theo các lượt/chunk liên tiếp; nối chính xác C:N của từng bài. Có thể đọc lại để giữ context khi chương dài. Không tuyên bố review đầy đủ nếu chưa đọc đủ từng bài.
3. **Lập bản đồ nội dung nội bộ trước khi sửa:** mục cấp 2 nào là nơi dạy chính của từng khái niệm, ví dụ, thao tác, giới hạn và bài tập; mục nào chỉ cần nhắc lại vắn tắt để dẫn sang kiến thức khác; đâu là nội dung được yêu cầu riêng bởi từng tiêu đề. Không đảo thứ tự Menu, không chuyển toàn bộ trách nhiệm một bài sang bài khác.
4. Nếu thiếu câu trả lời `C<R>`, có dữ liệu bị đứt đoạn/đè lên ngoài C:N, sai chương, nội dung có dấu hiệu chưa tải hết, hoặc không đọc đủ, **báo blocker** và không tự tạo bài mới để lấp chỗ trống. Không sửa chương một phần rồi báo `COMPLETED` toàn chương.

### W2.2. Đánh giá chất lượng và trùng lặp theo nội dung

**Mục tiêu trung tâm: giảm trùng lặp không cần thiết, giữ chiều sâu và tính tự học của từng bài**. Trùng vài thuật ngữ, định nghĩa ngắn hoặc ví dụ xuyên suốt **không tự động là lỗi**. Xác định bằng chứng theo từng cặp mục, mức độ lặp, và ảnh hưởng đến người đọc:

- **Trùng lặp cần refactor:** nhiều bài đều mở đầu bằng **cùng một tình huống giải thích dài**; lặp nguyên một chuỗi khái niệm, bảng, code, sơ đồ, ví dụ thành công/thất bại, hoặc cùng một kết luận mà không có góc nhìn/năng lực mới; lặp lời giới thiệu AOP/Aspect/cross-cutting quá dài ở các bài sau; đoạn chuyển ý/boilerplate “Hãy tưởng tượng...” lặp liên tục.
- **Lặp có ích nên giữ:** định nghĩa/cảnh báo ngắn để bài đọc độc lập không khó hiểu; cùng một case study được **tiếp nối** nhưng mỗi bài khai thác câu hỏi, tình huống, góc nhìn, kết quả, phép thử khác nhau; mã nguồn nhỏ bắt buộc để ví dụ của bài chạy được và tự kiểm chứng.
- **Chất lượng bắt buộc song song:** mỗi bài trả lời đúng và đủ tiêu đề Menu Cấp 2; dễ tiếp cận từ người mới, giải thích *vì sao/khi nào*, ranh giới với khái niệm kế bên, ví dụ có diễn biến rõ từ đầu vào → xử lý → kết quả; không mâu thuẫn kỹ thuật giữa các bài, không quá nhiều thuật ngữ chưa định nghĩa; không bỏ các điều kiện, ngoại lệ, hạn chế đáng học chỉ để rút ngắn.
- **Độ chính xác:** soát đặc biệt hành vi Java/Spring/Spring AOP/AspectJ, phạm vi proxy và invocation, thứ tự advice, exception, transaction, bảo mật, output code/ví dụ. Không phát minh hành vi API, không đưa khẳng định tuyệt đối khi thực tế có điều kiện.
- **Hình thức README:** Markdown/heading/code fence cân đối, code block đúng ngôn ngữ, bảng không gãy, Mermaid hợp lệ, ví dụ Java hợp lý, kết quả khớp code; **trình bày đa dạng khi hữu ích**, không biến cả chương thành text thuần hoặc rập khuôn mọi mục đều có cùng số heading/bảng/sơ đồ.
- **Tính nhất quán cả chương:** thuật ngữ, ví dụ, ký hiệu, dữ liệu số và cách gọi tên không xung đột; câu chuyển mạch từ bài trước sang bài sau tự nhiên, tránh mỗi bài phải “dạy lại từ đầu”; phần tổng kết phải đóng góp điều mới hoặc củng cố trọng tâm riêng.

**Không đặt mục tiêu phần trăm/tổng số ký tự bắt buộc phải giảm**. Không rút gọn hàng loạt theo số từ, không xóa bảng/code/sơ đồ chỉ vì bài dài. Đánh giá **giá trị học tập**, không chỉ tỷ lệ trùng văn bản. Ghi nhận rõ nơi lặp được giữ vì có chức năng và nơi lặp được sửa vì gây nhiễu hoặc mất nhịp học.

### W2.3. Refactor có chủ đích

1. Sau khi đã đọc **toàn bộ chương**, tự đề xuất những sửa đổi có căn cứ, giữ một **nơi giải thích chính (primary teaching home)** cho mỗi ý quan trọng nhưng các bài khác được phép nhắc lại ngắn hoặc áp dụng ở ngữ cảnh mới. Không chỉ xóa câu chữ: nếu chuyển/bỏ nội dung phải bảo toàn khái niệm và khả năng hiểu bài.
2. **Mỗi bài sau refactor phải đứng được một mình ở phạm vi hợp lý** và đồng thời đóng góp cho chương; không yêu cầu người đọc phải mò sang bài khác mới hiểu câu hỏi chính. Với phần lặp, có thể rút về 1–2 câu định vị rồi đi sâu vào nội dung riêng, dùng ví dụ mới, bảng phân biệt, thao tác, code trace hoặc tình huống kiểm tra phù hợp.
3. Tránh refactor biến mọi bài thành cùng một template WHAT/WHY/HOW; độ dài tùy trọng tâm. Giữ hoặc cải thiện ví dụ chạy được, giải thích causal flow, tính chính xác và Markdown nguyên vẹn. Nếu bài hiện tốt, **giữ nguyên**, không đổi chỉ để có thay đổi.
4. **Giữ nguyên vị trí hàng, A cha và B của tất cả hàng con.** Worker chỉ sửa câu trả lời trong `C:N`, Difficulty `O` và isImportant `AB` hàng con, **tạo Summary Markdown trong AC hàng con sau review/refactor**, **điền B hàng cha nếu chưa có filename và tạo YAML `section.yml` trong AC hàng cha**, đồng thời **chỉ tạo folder skeleton rỗng trong hai package đã giao**. Không đụng chương khác, code/config, không ghi file tạm trong repository, không commit/push.

### W2.4. Phân loại Difficulty cho TỪNG bài sau review

1. **Bắt buộc chọn đúng một trong ba chuỗi nguyên văn**: `BASIC`, `INTERMEDIATE`, `ADVANCED`. `BASIC`: kiến thức nền để bắt đầu, thuật ngữ/cơ chế cơ bản. `INTERMEDIATE`: ứng dụng, kết hợp nhiều khái niệm, so sánh và đánh đổi thực tế. `ADVANCED`: cơ chế sâu, ngoại lệ/edge cases, kiến trúc hay phân tích nâng cao. **Đánh giá toàn bộ nội dung bài và năng lực cần đạt**, không suy đoán bằng số ký tự, thứ tự chương hoặc tiêu đề đơn lẻ.
2. Mỗi hàng Menu Cấp 2 nhận một giá trị `O<R>`; **không có Difficulty ở hàng Menu Cấp 1**. Nếu giá trị O hiện tại trống thì thêm; nếu đã là một trong ba nhãn thì giữ khi đánh giá vẫn đúng hoặc cập nhật nếu có căn cứ rõ ràng. Giá trị khác danh sách hoặc formula/note lạ: `BLOCKER: INVALID DIFFICULTY CELL`, không tự chữa.
3. **Giữ nguyên Data Validation dropdown và Conditional Formatting** của Google Sheets; chỉ cập nhật `userEnteredValue`, tuyệt đối không thay màu/validation/notes bằng API. Nếu dropdown không được thiết lập trên tab cũ, vẫn chỉ dùng ba nhãn hợp lệ; nêu cảnh báo cấu hình trong báo cáo để Prime xử lý, **không tự chỉnh Template hoặc validation**.

### W2.4.1. Đánh giá và ghi isImportant tại AB của từng bài

1. Trong **cùng lượt review cả chương**, đọc đầy đủ từng bài `C:N` và quyết định isImportant TRUE/FALSE độc lập với Difficulty O. Gắn TRUE khi đó là prerequisite trọng yếu để hiểu chương, kiến thức bắt buộc để sử dụng cơ chế/API đúng, hoặc vấn đề bảo đảm an toàn/độ chính xác cần tránh. Không gắn TRUE chỉ vì ADVANCED hay dài; không ấn định tỷ lệ TRUE cố định.
2. Chỉ đọc và ghi `AB<R>` của **các hàng Menu Cấp 2 trong chương được Prime giao**, không chạm AB hàng cha hay bất kỳ chương khác. Kiểm tra `AB19=isImportant` nếu Prime đã xác minh. Ô AB có sẵn TRUE/FALSE thì giữ nếu đánh giá vẫn đúng; chỉ đổi với lý do rõ ràng. Nếu formula/notes/giá trị không phải TRUE/FALSE, báo BLOCKER, không xóa metadata tự tiện.
3. Snapshot và preflight toàn bộ AB trước khi ghi. Dùng userEnteredValue.stringValue = TRUE/FALSE tương thích dropdown ONE_OF_LIST, không đổi validation/format. Ghi AB trong cùng batch với C:N, O, B cha khi khả thi. Đọc lại từng ô AB chính xác. Với hàng hiện có TRUE, tuyệt đối không reset thành FALSE chỉ để khớp default.
4. IMPORTANCE report covers each child O/AB; filename collision correction touches only B/C parent, not AB.


### W2.5. Chính worker tự chọn và GHI filename vào B hàng cha

1. Sau khi đọc đầy đủ chương và biết trọng tâm Knowledge, tự chọn một tên `.md` tiếng Anh ngắn, **có nghĩa với toàn chương** (không chỉ dịch một bài con). Tên phải theo regex chính xác `^[A-Za-z][A-Za-z0-9]*\.md$`, ưu tiên **PascalCase** hoặc acronym chuẩn. Ví dụ `OOPBasic.md`, `Class.md`, `Encapsulation.md`. Không dùng tên chung chung như `Chapter1.md`/`Topic.md`, tên chứa dấu/space/gạch/underscore/slash/chấm thừa hoặc basename Windows dành riêng `CON/PRN/AUX/NUL/COM1–COM9/LPT1–LPT9`.
2. **B hàng cha trống → chính worker của chương phải tự ghi filename vào B này trong phiên STEP 5.** Nếu B đã chứa một filename hợp lệ, giữ nguyên; nếu B có tên sai định dạng, formula, note hoặc metadata ngoài quyền thì `BLOCKER: INVALID PARENT FILENAME`, không tự ghi đè. **Không bao giờ chạm B của Menu Cấp 2**.
3. Worker kiểm tra độ hợp lý với A cha và tất cả B con **trong chương được giao**. Không được gọi Prime đặt tên hộ hoặc gửi `SUGGESTED_FILENAME` rồi kết thúc mà **không ghi B cha**. Mỗi worker chịu trách nhiệm filename của chính chương. **Prime thực hiện kiểm tra trùng tên case-insensitive và trùng kebab-case prefix trên toàn tab SAU KHI tất cả worker hoàn thành**; vì vậy việc một worker chưa biết tên của chương khác không phải lý do trì hoãn ghi B cha.
4. Filename collision correction is a separate same-worker handoff, with read scope A/B/C parent and B children, write scope ONLY B/C parent before A child IDs exist. Update B filename and C base ID together, snapshot and exact-readback both. Do not touch Knowledge/AC/metadata/skeleton or other chapters. If A child IDs already exist, refuse automatic filename/base rename pending explicit ID migration. Report COLLISION_FIXED.

### W2.5.5. Chapter base ID in C parent

For the normal STEP 5 review, also write C<PARENT_ROW> = ReadmeSheetImportService.slugFromStem(B-parent-filename-without-md). Use lowercase kebab-case: OOPBasic.md -> oop-basic, Encapsulation.md -> encapsulation. Do not write C on children (their C:N is Knowledge) or A on children. C parent empty -> fill; exact matching value -> unchanged; mismatching value, formula, notes, chips or unclear owner -> BLOCKER. Snapshot and exact readback C parent. Collision correction of a B parent filename must also update C parent before A child IDs exist; Prime must grant BOTH B and C for correction. Once A IDs exist, refuse automatic B/C renaming to protect references.

### W2.6. BẮT BUỘC tạo Summary cho TỪNG bài trong AC hàng con (sau review/refactor)

**Chỉ thực hiện sau khi bản Knowledge cuối trong C:N của TOÀN BỘ chương đã review/refactor và qua chapter QA về độ chính xác, trùng lặp, thuật ngữ, code và Markdown.** Với từng Menu Cấp 2 tại `B<R>`, worker tạo **một tài liệu Markdown độc lập duy nhất tại `AC<R>`**; không sao chép cùng một bản cho cả chương, không chèn vào C:N hay AC cha. Phải đọc đủ body sau refactor, Special Note và bản đồ trách nhiệm các bài; không suy nội dung từ mỗi tiêu đề, không đặt mục tiêu mà Knowledge chưa dạy.

**Barem BẮT BUỘC: bốn mục 0 → 1 → 2 → 3, tên bài B<R> chính xác, phần 3 có bảng hai cột.** Số đoạn, số nhóm/câu hỏi, số năng lực đều theo nội dung thực tế, **không áp quota**. Ví dụ bên dưới chỉ minh họa định dạng, không được sao chép placeholder hoặc các mục minh họa làm nội dung thật:

```markdown
# 0. Định hướng học tập

## 1. Mục đích của bài học

**<Tên chính xác Menu Cấp 2 trong B<R>>**

<Lý do học, vấn đề cần giải quyết và giá trị khi vận dụng; chia thành số đoạn phù hợp bài.>

## 2. Những câu hỏi người học phải tự trả lời được

### <Nhóm kiến thức về bản chất/cơ chế, khi có trong bài>

1. <Câu hỏi mở yêu cầu giải thích bản chất được dạy?>
2. <Câu hỏi yêu cầu suy luận nguyên nhân, cơ chế hoặc hệ quả?>

### <Nhóm ứng dụng/thiết kế/giới hạn, nếu nội dung bài có>

1. <Câu hỏi tình huống áp dụng hoặc lựa chọn phương án?>
2. <Câu hỏi về lỗi, giới hạn hoặc kiểm chứng?>

## 3. Sau khi học xong, người học phải đạt được điều gì?

| Năng lực | Kết quả mong đợi |
| --- | --- |
| <Năng lực cụ thể từ nội dung bài> | <Hành động kiểm chứng được qua ví dụ/tình huống> |
```

**Tiêu chí tự QA từng AC con:**

1. Giữ **đúng bốn heading** theo thứ tự trên; Mục 0 chỉ là heading, không có prose chung chung. Mục 1 có **tên bài in đậm khớp 100% B<R>**; giải thích giá trị, vì sao cần học và vấn đề mà bài giúp giải quyết, không ép số đoạn/văn mẫu.
2. Mục 2 có một vài **nhóm câu hỏi mở có tên thực bằng heading `###`**, bên dưới dùng numbered list Markdown, câu hỏi có dấu hỏi và có thể trả lời/suy luận từ chính bài; không ghi sẵn đáp án, không ép A/B/C hay chín câu, không tạo nhóm rỗng hoặc cùng câu hỏi cho nhiều bài.
3. Mục 3 có **một bảng Markdown đúng hai cột `Năng lực` và `Kết quả mong đợi`**, mỗi hàng mô tả năng lực cụ thể → hành động quan sát/đánh giá được (phân tích, thiết kế, dự đoán, giải thích với lý do, mô phỏng, sửa lỗi...), không ghi chung chung chỉ “hiểu/biết”.
4. Nội dung phải đúng phạm vi Knowledge SAU refactor và vai trò riêng của bài so với các bài bên cạnh. Nếu Special Note đòi mục tiêu chưa được dạy hoặc không đủ căn cứ, ghi `CONTENT_SCOPE_LIMITATION` hoặc `BLOCKER: UNSUPPORTED_LEARNING_OBJECTIVES`; **không tự bịa kiến thức/đáp án, không sửa Knowledge sau khi đã soạn AC mà không rà soát AC lại**.
5. Markdown chuẩn (heading, **bold**, dòng trống, numbered list và bảng cân bằng dấu `|`; escape `|` trong cell). **Không có placeholder `<...>` trong output, HTML, JSON/YAML, fence bao trọn AC, TODO, thông báo nội bộ**, không thêm mạch liên kết nhiều bài vào AC con. Một ô AC con tối đa **30.000 ký tự Unicode**, không đẩy sang AD, không cắt bỏ mục tiêu quan trọng chỉ để vừa.

**Phân loại AC hiện có trước khi ghi:** `AC<R>` sạch/trống → `NEW_FILL`; đã đúng barem và vẫn khớp bản Knowledge cuối → `UNCHANGED/WRITE=NOT_RUN` (không rewrite cho hay hơn); có dữ liệu khác, guide thiếu mục, mâu thuẫn/stale sau refactor, công thức/chip, hoặc nghi do người sửa → `BLOCKER: EXISTING_TARGET_CONTENT/STALE_OBJECTIVES`. **Không tự xóa/ghi đè**, ngay cả khi guide trước đó được tạo bởi một workflow cũ. Muốn thay thế phải có ủy quyền riêng kèm xác minh provenance và preflight; không mặc nhiên suy quyền overwrite từ quyền refactor C:N. Trong cùng chương, xử lý các ô khác sạch/trống chỉ khi preflight cho phép và không tạo snapshot nửa chừng; chương có bất kỳ blocker không thể báo `COMPLETED`.

### W2.7. Authored-only AC parent YAML, matched by child row index

Worker writes one YAML document to AC parent after full Knowledge review and AC child Summaries. Root has exactly:
version: 1, type: LEARNING_ROADMAP, title, roadmapHeading, roadmap, comparisonMarkdown.

For N lessons, roadmap contains N maps in the exact B-child row order. Each map has exactly TWO keys:
description (specific to that lesson) and transition. For items 1..N-1, transition has just
description (the reason to continue); the last item's transition must be null. Do NOT author
id, order, title or transition.to. The Summary importer produces those technical fields
from A child ID, B child title, and list index. A child IDs are filled by Prime after worker QA.

Example of AC parent (two lessons):

    version: 1
    type: LEARNING_ROADMAP
    title: Chapter learning flow
    roadmapHeading: Learning sequence
    roadmap:
      - description: Understand foundational principles.
        transition:
          description: Apply those principles to concrete objects.
      - description: Model object state and behavior.
        transition: null
    comparisonMarkdown: |-
      ## 2. Lesson comparison

      | Criteria | Lesson 1 | Lesson 2 |
      | --- | --- | --- |
      | Ten | First | Second |
      | Van de can hieu | Basics | Behavior |
      | Gia tri doi voi module | Principles | Model |
      | Tu duy hinh thanh | Abstract reasoning | Object reasoning |

comparisonMarkdown continues to be a single GFM table with N+1 columns, proper heading,
and at least four existing pedagogical comparison rows. Preserve Vietnamese labels and
substance for real chapters; the example above is illustrative only. Never insert
fake arrows, redundant links or chapter conclusions in description.

QA: exactly N roadmap items; authored-only keys; N-1 meaningful transition descriptions,
last transition null; one valid N+1-column table; semantic match to final Knowledge and
AC-child Summaries; size at most 30,000 characters and exact readback.

Existing full-schema AC parent YAML (with id/order/title/to) is NOT authored-only: stop with
BLOCKER: LEGACY_AC_SCHEMA rather than overwrite/migrate it without explicit authorization.
The module section.yml output retains full schema, generated deterministically by code.

## W3. Ghi an toàn Knowledge C:N, Difficulty O, isImportant AB, filename B cha, AC con và AC cha

**W3/W4 correction exception:** For an authorized filename collision fix, update ONLY B and C parent together (before A-child ID fill), after snapshot/preflight and exact readback. Do not repeat Knowledge review or touch child C:N/O/AB/AC or filesystem. If A-child IDs already exist, BLOCKER pending explicit migration.

1. Before writing, snapshot B AND C parent, AC parent and every child C:N/O/AB/AC value including formulas/notes/validation. Prepare all Knowledge, metadata, filename/base ID and authored-only Summary changes from final reviewed Knowledge. Preflight all original cells and ownership; do not replace human-owned/stale AC blindly.
2. In ordinary chapter review, worker may refactor existing STEP 4 Knowledge C:N children; set O/AB; fill B parent filename and C parent base ID; author AC-child Markdown and authored-only AC-parent YAML, subject to snapshot/ownership rules. Do not write A children. Do not overwrite existing AC human edits without explicit provenance/authorization.
3. Soạn bản README refactor hoàn chỉnh trước khi ghi. Nếu quá **360.000 ký tự**, báo `BLOCKER: CONTENT EXCEEDS C:N CAPACITY`, không cắt bỏ nội dung. Nếu vừa giới hạn, chia tuần tự **C, D, E, ... , N**, mỗi ô tối đa **30.000 ký tự**; bảo toàn chính xác mọi ký tự Unicode/newline/fence khi nối.
4. **Khi bản mới ngắn hơn bản cũ**, bắt buộc **xóa chỉ những ô đuôi cũ trong C:N của hàng đó** không còn dùng đến; nếu giữ đoạn D/E cũ thì người đọc sẽ ghép nhầm bài mới + bài cũ. Phần ô thay đổi và ô đuôi cần dọn **phải được cập nhật cùng một giao dịch/batch khi công cụ hỗ trợ**. Không ghi nguyên văn một phần khi còn lỗi dữ liệu/công cụ.
5. Write only owned cells: B/C parent, C:N/O/AB/AC each child and AC parent, value-only. Exact-readback all. AC parent validates authored-only roadmap: N entries with description/transition only, final null and one complete GFM comparison table. C parent must be kebab-case of B parent filename. A child IDs remain exclusively Prime-owned.
6. Nếu phát sinh lỗi khi ghi/read-back hoặc đã lỡ ghi một phần, **không báo PASS**. Nêu hàng bị ảnh hưởng, snapshot gốc và trạng thái cuối cùng; chỉ phục hồi từ snapshot nếu quyền sở hữu còn hợp lệ và chắc chắn không chạm thay đổi của người khác.

## W4. Kiểm tra sau refactor theo cả chương

After refactor, QA all Knowledge/Difficulty/isImportant/AC-child Markdown, parent B/C identity and AC-parent authored-only YAML, including exactly N description/transition entries, N-1 transitions, last null and one N+1-column GFM comparison table. Also QA main/test skeleton and protect human-owned content.

**Không đánh dấu chương CLEAN nếu mới chỉ đọc từng bài độc lập nhưng chưa kiểm tra lại toàn chương.**

## W5. Báo cáo bắt buộc và SLEEPING

Worker trả kết quả đủ để Prime kiểm chứng, không thay thế bằng câu “đã refactor xong”:

- `STATUS`: `COMPLETED` / `BLOCKER` / `FAILED`; `SHEET`: tên tab, gid; `CHAPTER`: tọa độ Menu Cấp 1; `CHILD_ROWS`: các hàng Menu Cấp 2 thực sự review.
- `READ_COVERAGE`: tổng số bài phải đọc, số bài đã đọc **đầy đủ từ C:N**, và tổng số bài đã review lại sau refactor. Không báo 100% khi chỉ sample.
- `FINDINGS`: chỗ trùng lặp có ý nghĩa (mục/hàng nào với mục/hàng nào), điểm lặp cố ý giữ, vấn đề về độ chính xác/thiếu ý/Markdown và lý do.
- `CHANGES`: mỗi hàng sửa ghi tóm tắt **đã đổi gì, vì sao**, số ký tự trước/sau, các ô C:N bị thay đổi/xóa đuôi; danh sách hàng giữ nguyên và lý do ngắn.
- `DIFFICULTY`: bảng từng `B<R> → O<R> → BASIC/INTERMEDIATE/ADVANCED` kèm lý do dựa trên nội dung đã đọc, các ô O đã điền/cập nhật/giữ nguyên; cảnh báo dropdown nếu có.
- `FILENAME`: `A<PARENT> → B<PARENT>` với tên `.md` cuối cùng, `NEW/UNCHANGED/COLLISION_FIX`, lý do đặt tên, regex/Windows-name PASS và xác nhận **chính worker đã ghi B cha** nếu trước đó trống.
- `LEARNING_OBJECTIVES`: **từng hàng con** `B<R> → AC<R> → NEW_FILL/UNCHANGED/BLOCKER`, số nhóm/câu hỏi mở/số năng lực thực, độ dài ≤30.000, đủ bốn mục 0/1/2/3, tên bài khớp B, câu hỏi/năng lực đúng Knowledge sau refactor, Markdown/bảng hai cột `PASS`, exact readback `PASS`; nêu blocker và owner của mọi AC cũ stale/human-edited. Lượt chỉ sửa filename báo `NOT_TOUCHED`.
- CHAPTER_LINK: AC parent authored-only YAML parse/schema PASS, exactly N description/transition entries, last null, full comparison table, CONTENT_GROUNDED_QA and exact readback. Do not require derived IDs/order/titles/to in AC.
- `SKELETON`: `MAIN_PACKAGE_PATH`, `TEST_PACKAGE_PATH`, `SKELETON_STATUS=PASS/UNCHANGED/BLOCKER`; danh sách `CREATED/EXISTING` (6 thư mục main + 1 root test và marker `.gitkeep` cần thiết), preflight ownership, xác nhận không Java/source/placeholder có logic hoặc config phát sinh và không ghi ngoài vùng. Lượt chỉ sửa filename báo `SKELETON=NOT_TOUCHED`.
- READBACK: PASS/FAIL B and C parent values, authored-only AC-parent YAML, and every C:N/O/AB/AC child; no unexpected edits to A children, parent A/B titles, headers or outside ownership. Check Knowledge fragments joined exactly and all cell capacity limits.
- `CHAPTER_QA`: PASS/FAIL kiểm tra lại toàn chương sau refactor, số lỗi trùng lặp/độ chính xác/format còn lại và `BLOCKERS` theo tọa độ (nếu có).
**STEP 5 chapter completion:** requires full Knowledge QA, B parent filename, C parent base ID, Difficulty/isImportant, AC-child Markdown, authored-only AC-parent YAML, exact readback, chapter QA and skeleton QA PASS. Prime exclusively handles global collisions and A-child ID fill after workers release ownership.
