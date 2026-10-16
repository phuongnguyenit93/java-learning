# STEP 5 — Independent Chapter Review & Refactor

## Mục tiêu

Sau STEP 4, **mỗi Menu Cấp 2 đã có một bài học README Markdown độc lập** ở các ô `C:G` cùng hàng. STEP 5 kiểm tra và chỉnh sửa **toàn bộ các bài học thuộc MỘT Menu Cấp 1 như một chương thống nhất**, thay vì review từng bài rời rạc.

**Mỗi sub-worker nhận trách nhiệm trực tiếp và duy nhất đối với một Menu Cấp 1 (một chương, bao gồm TẤT CẢ mục cấp 2 của chương đó).** Một worker có **quyền ghi độc quyền** vào các ô câu trả lời của chương được giao trong thời gian làm việc. Các chương khác nhau có thể do các worker khác nhau xử lý song song, nhưng **không chia một chương cho nhiều worker và không có hai writer trên cùng hàng**.

**Tối đa 3 sub-worker trong pool, tính cả ACTIVE và SLEEPING**. Tuân thủ `GENERAL_AGENT_RULES.md`; tái sử dụng worker SLEEPING trước, không spawn khi còn worker SLEEPING.

### Ngoại lệ có chủ đích về context cho STEP 5

**BẮT BUỘC sub-worker đọc TRỰC TIẾP TOÀN BỘ file `/java-learning/module-generate-agent/temp/STEP_5.md` trước khi review/refactor.** Đây là chỉ thị riêng do người dùng yêu cầu, thay cho cách Prime rút gọn mục tiêu refactor thành vài dòng dễ thiếu ý. Worker cũng phải đọc lại file này khi context bị rút gọn/compaction hoặc khi quên quy định. **Prime vẫn phải gửi link Google Sheets và tọa độ nguồn/đích cụ thể**; đọc file STEP 5 **không** có nghĩa worker được tự tìm tab hay tự chọn chương khác. Worker **không phải đọc STEP 1–4, GENERAL hoặc TXT** để định vị tác vụ; các nhiệm vụ đó thuộc Prime.

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
- **Giới hạn giữ nguyên STEP 4:** tối đa **30.000 ký tự/ô**, **5 ô C–G**, **150.000 ký tự/bài**, không sử dụng H trở đi hoặc hàng khác. Không ghi thêm cột/hàng, không sửa `A/B`, `Template`, Roadmap, header hay file source.

## 2. Trách nhiệm của Prime

1. Trước khi giao, **Prime tự đọc** `/java-learning/module-generate-agent/temp/GENERAL_AGENT_RULES.md` và `/java-learning/module-generate-agent/temp/module-generate-info.txt`. Lấy URL từ `Excel Page URL`, mapping `<TÊN MODULE> sheet id : <SHEET_ID>`; xây dựng URL trực tiếp `<URL_GỐC>/edit?gid=<SHEET_ID>#gid=<SHEET_ID>`. Không hardcode, không nhầm gid với spreadsheet ID. Xác minh tab, `A1`, `B1` và tên module.
2. **Prime tự đọc vùng A/B của Menu** để xác định từng chương, chính xác `PARENT_CELL` và danh sách/vùng `CHILD_CELLS` (chỉ những hàng có B là mục cấp 2). Nếu cấu trúc bị thiếu/sai, hai cột có dữ liệu cùng hàng hoặc không xác định được chương, báo `BLOCKER` thay vì đoán.
3. Trước khi giao, Prime kiểm tra các bài đã sinh trong `C:G` của chương. Nếu hàng mục cấp 2 không có bài từ STEP 4 hoặc bài nằm ngoài C:G, ghi nhận `BLOCKER: INCOMPLETE STEP 4 CHAPTER`; **không coi chương đã sẵn sàng refactor đầy đủ**. Không tự tạo bài mới thay STEP 4.
4. **Kiểm tra trạng thái pool** và chỉ điều phối trong giới hạn **3 sub-worker ACTIVE + SLEEPING**. Tái sử dụng worker SLEEPING; **không spawn khi còn worker SLEEPING**. Nếu pool hiện có bất thường nhiều hơn 3 worker revivable/active, không tạo thêm worker hoặc đánh thức để vượt giới hạn; báo/điều phối lại theo GENERAL, không tự kết thúc worker không thuộc nhiệm vụ này.
5. **Mỗi worker chỉ nhận 1 chương tại một thời điểm.** Prime quản lý bảng `PARENT_CELL → CHILD_CELLS → OWNER_WORKER → STATUS`, khóa phạm vi ghi trước khi giao. Không giao cùng chương cho nhiều worker; không sửa trực tiếp những ô một worker đang sở hữu.
6. **Prime gửi trực tiếp cho worker:** (a) **đường dẫn chính xác đến STEP_5.md và lệnh bắt buộc đọc toàn file**, (b) link tab Google Sheets đã xác minh, (c) `A1`, `B1`, (d) đúng ô Menu Cấp 1, (e) **từng hàng/range Menu Cấp 2 cần đọc** cùng vùng `C:G` tương ứng, (f) nhắc chỉ sửa chương này. **Không gửi nội dung câu trả lời, kết quả phân tích trùng lặp hoặc bản refactor gợi ý** thay worker.
7. Prime chỉ tiếp tục giao chương khác cho worker **sau khi worker đã báo cáo, xác minh và giải phóng ownership**. Không khởi động STEP tiếp theo, không commit/push hoặc sửa các file khác.

### Mẫu lệnh giao việc — CHỈ chứa thông tin định vị, không rút gọn tiêu chí refactor

> Nhiệm vụ STEP 5, duy nhất một Menu Cấp 1. **Bắt buộc đọc toàn bộ file `/java-learning/module-generate-agent/temp/STEP_5.md` và thực hiện ĐÚNG các quy tắc trong file**, kể cả sau compaction. Tab Google Sheets: `<LINK_TAB_CHÍNH_XÁC>`. Chỉ đọc `A1`, `B1`, Menu Cấp 1 `<PARENT_CELL>`, các Menu Cấp 2 `<CHILD_RANGE_HOẶC_DANH_SÁCH_Ô>` và nội dung Markdown `<ANSWER_RANGE_C_ĐẾN_G>`. Bạn sở hữu độc quyền quyền refactor **chỉ các ô C:G thuộc các hàng mục cấp 2 vừa được giao**. Review **TOÀN BỘ chương**, đánh giá trùng lặp, độ đầy đủ, chính xác, cách trình bày và tính liền mạch, refactor khi có căn cứ; giữ đủ nội dung riêng của từng bài. Tuân thủ giới hạn 30.000 ký tự/ô, tối đa 150.000/bài, không H trở đi, đọc lại khớp từng ô và cả Markdown ghép. Báo status, chương, danh sách hàng, nội dung đã chỉnh, điều giữ nguyên, kiểm tra readback và blockers. Không tự tìm/giao việc khác, không đọc hội thoại cũ làm tiêu chuẩn, không spawn, không sửa source, không commit.

**Khác STEP 2–4:** Với riêng STEP 5, việc worker tự đọc file STEP_5.md **là bắt buộc** và có thể đọc nhiều lần. Tuy nhiên, Prime **vẫn phải gửi chính xác link và tọa độ**, không được chỉ nhắn “đọc STEP_5.md rồi tự tìm sheet”.

## 3. Quy trình bắt buộc của worker theo từng chương

### 3.1. Preflight: đọc đầy đủ, không vội viết

1. **Đọc hết file STEP_5.md**, xác nhận đúng link tab/gid và **chỉ** những ô `A1`, `B1`, `PARENT_CELL`, `CHILD_CELLS` và `C:G` mà Prime đã định vị.
2. **Đọc TOÀN BỘ bài học của TẤT CẢ mục cấp 2** trong chương được giao, không review chỉ từ tiêu đề, độ dài, vài đoạn đầu/cuối. Nếu nhiều nội dung, đọc theo các lượt/chunk liên tiếp; nối chính xác C:G của từng bài. Có thể đọc lại để giữ context khi chương dài. Không tuyên bố review đầy đủ nếu chưa đọc đủ từng bài.
3. **Lập bản đồ nội dung nội bộ trước khi sửa:** mục cấp 2 nào là nơi dạy chính của từng khái niệm, ví dụ, thao tác, giới hạn và bài tập; mục nào chỉ cần nhắc lại vắn tắt để dẫn sang kiến thức khác; đâu là nội dung được yêu cầu riêng bởi từng tiêu đề. Không đảo thứ tự Menu, không chuyển toàn bộ trách nhiệm một bài sang bài khác.
4. Nếu thiếu câu trả lời `C<R>`, có dữ liệu bị đứt đoạn/đè lên ngoài C:G, sai chương, nội dung có dấu hiệu chưa tải hết, hoặc không đọc đủ, **báo blocker** và không tự tạo bài mới để lấp chỗ trống. Không sửa chương một phần rồi báo `COMPLETED` toàn chương.

### 3.2. Đánh giá chất lượng và trùng lặp theo nội dung

**Mục tiêu trung tâm: giảm trùng lặp không cần thiết, giữ chiều sâu và tính tự học của từng bài**. Trùng vài thuật ngữ, định nghĩa ngắn hoặc ví dụ xuyên suốt **không tự động là lỗi**. Xác định bằng chứng theo từng cặp mục, mức độ lặp, và ảnh hưởng đến người đọc:

- **Trùng lặp cần refactor:** nhiều bài đều mở đầu bằng **cùng một tình huống giải thích dài**; lặp nguyên một chuỗi khái niệm, bảng, code, sơ đồ, ví dụ thành công/thất bại, hoặc cùng một kết luận mà không có góc nhìn/năng lực mới; lặp lời giới thiệu AOP/Aspect/cross-cutting quá dài ở các bài sau; đoạn chuyển ý/boilerplate “Hãy tưởng tượng...” lặp liên tục.
- **Lặp có ích nên giữ:** định nghĩa/cảnh báo ngắn để bài đọc độc lập không khó hiểu; cùng một case study được **tiếp nối** nhưng mỗi bài khai thác câu hỏi, tình huống, góc nhìn, kết quả, phép thử khác nhau; mã nguồn nhỏ bắt buộc để ví dụ của bài chạy được và tự kiểm chứng.
- **Chất lượng bắt buộc song song:** mỗi bài trả lời đúng và đủ tiêu đề Menu Cấp 2; dễ tiếp cận từ người mới, giải thích *vì sao/khi nào*, ranh giới với khái niệm kế bên, ví dụ có diễn biến rõ từ đầu vào → xử lý → kết quả; không mâu thuẫn kỹ thuật giữa các bài, không quá nhiều thuật ngữ chưa định nghĩa; không bỏ các điều kiện, ngoại lệ, hạn chế đáng học chỉ để rút ngắn.
- **Độ chính xác:** soát đặc biệt hành vi Java/Spring/Spring AOP/AspectJ, phạm vi proxy và invocation, thứ tự advice, exception, transaction, bảo mật, output code/ví dụ. Không phát minh hành vi API, không đưa khẳng định tuyệt đối khi thực tế có điều kiện.
- **Hình thức README:** Markdown/heading/code fence cân đối, code block đúng ngôn ngữ, bảng không gãy, Mermaid hợp lệ, ví dụ Java hợp lý, kết quả khớp code; **trình bày đa dạng khi hữu ích**, không biến cả chương thành text thuần hoặc rập khuôn mọi mục đều có cùng số heading/bảng/sơ đồ.
- **Tính nhất quán cả chương:** thuật ngữ, ví dụ, ký hiệu, dữ liệu số và cách gọi tên không xung đột; câu chuyển mạch từ bài trước sang bài sau tự nhiên, tránh mỗi bài phải “dạy lại từ đầu”; phần tổng kết phải đóng góp điều mới hoặc củng cố trọng tâm riêng.

**Không đặt mục tiêu phần trăm/tổng số ký tự bắt buộc phải giảm**. Không rút gọn hàng loạt theo số từ, không xóa bảng/code/sơ đồ chỉ vì bài dài. Đánh giá **giá trị học tập**, không chỉ tỷ lệ trùng văn bản. Ghi nhận rõ nơi lặp được giữ vì có chức năng và nơi lặp được sửa vì gây nhiễu hoặc mất nhịp học.

### 3.3. Refactor có chủ đích

1. Sau khi đã đọc **toàn bộ chương**, tự đề xuất những sửa đổi có căn cứ, giữ một **nơi giải thích chính (primary teaching home)** cho mỗi ý quan trọng nhưng các bài khác được phép nhắc lại ngắn hoặc áp dụng ở ngữ cảnh mới. Không chỉ xóa câu chữ: nếu chuyển/bỏ nội dung phải bảo toàn khái niệm và khả năng hiểu bài.
2. **Mỗi bài sau refactor phải đứng được một mình ở phạm vi hợp lý** và đồng thời đóng góp cho chương; không yêu cầu người đọc phải mò sang bài khác mới hiểu câu hỏi chính. Với phần lặp, có thể rút về 1–2 câu định vị rồi đi sâu vào nội dung riêng, dùng ví dụ mới, bảng phân biệt, thao tác, code trace hoặc tình huống kiểm tra phù hợp.
3. Tránh refactor biến mọi bài thành cùng một template WHAT/WHY/HOW; độ dài tùy trọng tâm. Giữ hoặc cải thiện ví dụ chạy được, giải thích causal flow, tính chính xác và Markdown nguyên vẹn. Nếu bài hiện tốt, **giữ nguyên**, không đổi chỉ để có thay đổi.
4. **Giữ nguyên vị trí hàng và mọi ô A/B.** Worker chỉ sửa nội dung câu trả lời của đúng chương trong `C:G` các hàng con mà Prime giao; không đụng mục/chap khác, không đụng config, không ghi file tạm trong repository, không commit/push.

### 3.4. Ghi lại an toàn vào C:G và đọc lại

1. Trước mỗi chỉnh sửa, lưu bản **snapshot đầy đủ của 5 ô C:G của chính hàng đó** và bản Markdown ghép, cả giá trị rỗng. Xác nhận không có worker khác đang viết hàng này. Khi sắp ghi, kiểm tra lại dữ liệu hiện tại còn khớp snapshot; nếu có chỉnh sửa bên ngoài, **dừng hàng đó và báo `BLOCKER: SOURCE CHANGED`**, không ghi đè.
2. **STEP 5 được quyền thay thế câu trả lời STEP 4 đã tồn tại trong C:G của các hàng được giao** (khác với STEP 4 chỉ ghi vào ô trống), nhưng **không được sửa bất kỳ ô nào ngoài phạm vi**. Không xóa nội dung không liên quan, không gộp ô, không thêm hàng/cột, không chuyển câu trả lời sang row khác.
3. Soạn bản README refactor hoàn chỉnh trước khi ghi. Nếu quá **150.000 ký tự**, báo `BLOCKER: CONTENT EXCEEDS C:G CAPACITY`, không cắt bỏ nội dung. Nếu vừa giới hạn, chia tuần tự **C, D, E, F, G**, mỗi ô tối đa **30.000 ký tự**; bảo toàn chính xác mọi ký tự Unicode/newline/fence khi nối.
4. **Khi bản mới ngắn hơn bản cũ**, bắt buộc **xóa chỉ những ô đuôi cũ trong C:G của hàng đó** không còn dùng đến; nếu giữ đoạn D/E cũ thì người đọc sẽ ghép nhầm bài mới + bài cũ. Phần ô thay đổi và ô đuôi cần dọn **phải được cập nhật cùng một giao dịch/batch khi công cụ hỗ trợ**. Không ghi nguyên văn một phần khi còn lỗi dữ liệu/công cụ.
5. **Đọc lại C:G của từng hàng sau khi ghi**, nối theo thứ tự không có ký tự nối, đối chiếu **exact-match 100%** với bản Markdown refactor, từng ô `<=30.000` ký tự, tổng `<=150.000`, mọi ô đuôi rỗng. Kiểm tra `A1`, `B1`, hàng Menu A/B và ngoài phạm vi vẫn nguyên vẹn.
6. Nếu phát sinh lỗi khi ghi/read-back hoặc đã lỡ ghi một phần, **không báo PASS**. Nêu hàng bị ảnh hưởng, snapshot gốc và trạng thái cuối cùng; chỉ phục hồi từ snapshot nếu quyền sở hữu còn hợp lệ và chắc chắn không chạm thay đổi của người khác.

### 3.5. Kiểm tra sau refactor theo cả chương

Sau khi sửa từng bài, **đọc lại TOÀN BỘ các bài của chương theo thứ tự Menu Cấp 2** để xác minh: không mất ý gốc quan trọng, không phát sinh trùng lặp mới, không mâu thuẫn giữa bài, không lỗi Markdown/code/diagram, mọi bài trả lời đúng chủ đề và giữ phân vai kiến thức rõ ràng. Nếu còn lỗi, tự chỉnh trong phạm vi được giao và lặp lại QA.

**Không đánh dấu chương CLEAN nếu mới chỉ đọc từng bài độc lập nhưng chưa kiểm tra lại toàn chương.**

## 4. Báo cáo bắt buộc từ worker

Worker trả kết quả đủ để Prime kiểm chứng, không thay thế bằng câu “đã refactor xong”:

- `STATUS`: `COMPLETED` / `BLOCKER` / `FAILED`; `SHEET`: tên tab, gid; `CHAPTER`: tọa độ Menu Cấp 1; `CHILD_ROWS`: các hàng Menu Cấp 2 thực sự review.
- `READ_COVERAGE`: tổng số bài phải đọc, số bài đã đọc **đầy đủ từ C:G**, và tổng số bài đã review lại sau refactor. Không báo 100% khi chỉ sample.
- `FINDINGS`: chỗ trùng lặp có ý nghĩa (mục/hàng nào với mục/hàng nào), điểm lặp cố ý giữ, vấn đề về độ chính xác/thiếu ý/Markdown và lý do.
- `CHANGES`: mỗi hàng sửa ghi tóm tắt **đã đổi gì, vì sao**, số ký tự trước/sau, các ô C:G bị thay đổi/xóa đuôi; danh sách hàng giữ nguyên và lý do ngắn.
- `READBACK`: PASS/FAIL **theo từng hàng đã sửa** sau khi ghép Markdown; xác nhận nội dung không vượt 30.000/ô và 150.000/hàng, cùng kiểm tra A/B và ngoài phạm vi được giữ nguyên.
- `CHAPTER_QA`: PASS/FAIL kiểm tra lại toàn chương sau refactor, số lỗi trùng lặp/độ chính xác/format còn lại và `BLOCKERS` theo tọa độ (nếu có).

Prime đối chiếu báo cáo và dữ liệu thực tế trước khi đánh dấu `COMPLETED` cho chương. Worker **giải phóng quyền ghi của chương** khi báo cáo xong/finish-sleep; Prime chỉ giao chương tiếp theo sau khi biết rõ trạng thái.

## 5. Điều kiện hoàn thành STEP 5

- Mỗi Menu Cấp 1 được giao cho **đúng một worker chịu trách nhiệm trọn chương**, pool không vượt 3 worker `ACTIVE` + `SLEEPING`.
- Worker **đã đọc trọn STEP_5.md** và **đọc đầy đủ từng bài** của chương, lập bản đồ nội dung, đánh giá/refactor có căn cứ, QA toàn chương.
- Giảm **lặp gây ảnh hưởng chất lượng** nhưng **không đánh đổi chiều sâu, tính tự học, độ chính xác hoặc ví dụ cần thiết** để giảm số chữ. Không thay đổi bài đang tốt chỉ để sửa.
- Chỉ có các ô **C:G thuộc hàng Menu Cấp 2 được giao** mới được phép thay đổi; Markdown sau ghi được kiểm tra exact-match và toàn bộ dữ liệu ngoài phạm vi được bảo toàn.
- Chương chỉ `COMPLETED` khi read coverage đủ, readback từng hàng sửa **PASS** và Chapter QA **PASS**; nếu thiếu bài, vượt sức chứa, lỗi công cụ hoặc xung đột dữ liệu thì phải ghi rõ `BLOCKER/FAILED`.
- STEP 5 toàn module hoàn thành khi **mọi chương** đã được review/refactor và Prime xác minh trạng thái; không tự chạy STEP tiếp theo hoặc commit/push.
