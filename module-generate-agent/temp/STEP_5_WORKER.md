# STEP 5 — WORKER ONLY: Review & Refactor toàn bộ một chương

> Chỉ dành cho worker. Prime dùng STEP_5.md để điều phối; bạn bắt buộc tự đọc TOÀN VĂN đúng file STEP_5_WORKER.md này trước khi làm, đọc lại nếu mất context. Không đọc STEP_5.md, STEP khác, file rules/TXT hoặc dùng lịch sử hội thoại làm tiêu chuẩn. Prime chỉ gửi link tab, tọa độ ô nguồn, vùng câu trả lời C:G, và đường dẫn file này; không gửi tóm tắt tiêu chí refactor.

## W1. Một chương, một lượt, một owner

1. Mỗi lượt chỉ có MỘT Menu Cấp 1 và TOÀN BỘ Menu Cấp 2 thuộc chương đó; tự thực hiện đọc đủ bài → lập bản đồ → review/refactor → snapshot/preflight → ghi → readback → chapter QA → báo cáo → SLEEPING. Không chia một chương cho worker khác và không đòi Prime diễn giải thêm.
2. Trên đúng tab/gid Prime giao, chỉ đọc nguồn A1, B1, A<PARENT_ROW>, dải B<CHILD_ROWS>, và toàn bộ các ô C:G tương ứng các hàng con. Các hàng B được Prime chỉ định cụ thể, không tự dò chương/bài khác hoặc đọc câu trả lời ngoài phạm vi.
3. Chỉ có quyền thay thế giá trị câu trả lời C:G trên các hàng con được giao; một ô tối đa 30.000 ký tự, một hàng tối đa 150.000 ký tự. Không ghi H trở đi, A:B, header, J3, Template, các tab, module hoặc file khác. Nếu có Knowledge nằm ngoài C:G thì báo BLOCKER, không âm thầm sửa phần ngoài quyền.
4. Kiểm tra ownership của Prime; nếu chapter thiếu Knowledge, nguồn sai, không đủ quyền hoặc đang do worker khác sửa: báo BLOCKER, không sửa một phần và không nhận chương thay thế. Không spawn worker, không chạy STEP tiếp theo, không commit/push.

## W2. Đọc trọn chương, đánh giá và refactor

### W2.1. Preflight: đọc đầy đủ, không vội viết

1. **Đọc hết file STEP_5_WORKER.md**, xác nhận đúng link tab/gid và **chỉ** những ô `A1`, `B1`, `PARENT_CELL`, `CHILD_CELLS` và `C:G` mà Prime đã định vị.
2. **Đọc TOÀN BỘ bài học của TẤT CẢ mục cấp 2** trong chương được giao, không review chỉ từ tiêu đề, độ dài, vài đoạn đầu/cuối. Nếu nhiều nội dung, đọc theo các lượt/chunk liên tiếp; nối chính xác C:G của từng bài. Có thể đọc lại để giữ context khi chương dài. Không tuyên bố review đầy đủ nếu chưa đọc đủ từng bài.
3. **Lập bản đồ nội dung nội bộ trước khi sửa:** mục cấp 2 nào là nơi dạy chính của từng khái niệm, ví dụ, thao tác, giới hạn và bài tập; mục nào chỉ cần nhắc lại vắn tắt để dẫn sang kiến thức khác; đâu là nội dung được yêu cầu riêng bởi từng tiêu đề. Không đảo thứ tự Menu, không chuyển toàn bộ trách nhiệm một bài sang bài khác.
4. Nếu thiếu câu trả lời `C<R>`, có dữ liệu bị đứt đoạn/đè lên ngoài C:G, sai chương, nội dung có dấu hiệu chưa tải hết, hoặc không đọc đủ, **báo blocker** và không tự tạo bài mới để lấp chỗ trống. Không sửa chương một phần rồi báo `COMPLETED` toàn chương.

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
4. **Giữ nguyên vị trí hàng và mọi ô A/B.** Worker chỉ sửa nội dung câu trả lời của đúng chương trong `C:G` các hàng con mà Prime giao; không đụng mục/chap khác, không đụng config, không ghi file tạm trong repository, không commit/push.

## W3. Ghi lại an toàn vào C:G và đọc lại

1. Trước mỗi chỉnh sửa, lưu bản **snapshot đầy đủ của 5 ô C:G của chính hàng đó** và bản Markdown ghép, cả giá trị rỗng. Xác nhận không có worker khác đang viết hàng này. Khi sắp ghi, kiểm tra lại dữ liệu hiện tại còn khớp snapshot; nếu có chỉnh sửa bên ngoài, **dừng hàng đó và báo `BLOCKER: SOURCE CHANGED`**, không ghi đè.
2. **STEP 5 được quyền thay thế câu trả lời STEP 4 đã tồn tại trong C:G của các hàng được giao** (khác với STEP 4 chỉ ghi vào ô trống), nhưng **không được sửa bất kỳ ô nào ngoài phạm vi**. Không xóa nội dung không liên quan, không gộp ô, không thêm hàng/cột, không chuyển câu trả lời sang row khác.
3. Soạn bản README refactor hoàn chỉnh trước khi ghi. Nếu quá **150.000 ký tự**, báo `BLOCKER: CONTENT EXCEEDS C:G CAPACITY`, không cắt bỏ nội dung. Nếu vừa giới hạn, chia tuần tự **C, D, E, F, G**, mỗi ô tối đa **30.000 ký tự**; bảo toàn chính xác mọi ký tự Unicode/newline/fence khi nối.
4. **Khi bản mới ngắn hơn bản cũ**, bắt buộc **xóa chỉ những ô đuôi cũ trong C:G của hàng đó** không còn dùng đến; nếu giữ đoạn D/E cũ thì người đọc sẽ ghép nhầm bài mới + bài cũ. Phần ô thay đổi và ô đuôi cần dọn **phải được cập nhật cùng một giao dịch/batch khi công cụ hỗ trợ**. Không ghi nguyên văn một phần khi còn lỗi dữ liệu/công cụ.
5. **Đọc lại C:G của từng hàng sau khi ghi**, nối theo thứ tự không có ký tự nối, đối chiếu **exact-match 100%** với bản Markdown refactor, từng ô `<=30.000` ký tự, tổng `<=150.000`, mọi ô đuôi rỗng. Kiểm tra `A1`, `B1`, hàng Menu A/B và ngoài phạm vi vẫn nguyên vẹn.
6. Nếu phát sinh lỗi khi ghi/read-back hoặc đã lỡ ghi một phần, **không báo PASS**. Nêu hàng bị ảnh hưởng, snapshot gốc và trạng thái cuối cùng; chỉ phục hồi từ snapshot nếu quyền sở hữu còn hợp lệ và chắc chắn không chạm thay đổi của người khác.

## W4. Kiểm tra sau refactor theo cả chương

Sau khi sửa từng bài, **đọc lại TOÀN BỘ các bài của chương theo thứ tự Menu Cấp 2** để xác minh: không mất ý gốc quan trọng, không phát sinh trùng lặp mới, không mâu thuẫn giữa bài, không lỗi Markdown/code/diagram, mọi bài trả lời đúng chủ đề và giữ phân vai kiến thức rõ ràng. Nếu còn lỗi, tự chỉnh trong phạm vi được giao và lặp lại QA.

**Không đánh dấu chương CLEAN nếu mới chỉ đọc từng bài độc lập nhưng chưa kiểm tra lại toàn chương.**

## W5. Báo cáo bắt buộc và SLEEPING

Worker trả kết quả đủ để Prime kiểm chứng, không thay thế bằng câu “đã refactor xong”:

- `STATUS`: `COMPLETED` / `BLOCKER` / `FAILED`; `SHEET`: tên tab, gid; `CHAPTER`: tọa độ Menu Cấp 1; `CHILD_ROWS`: các hàng Menu Cấp 2 thực sự review.
- `READ_COVERAGE`: tổng số bài phải đọc, số bài đã đọc **đầy đủ từ C:G**, và tổng số bài đã review lại sau refactor. Không báo 100% khi chỉ sample.
- `FINDINGS`: chỗ trùng lặp có ý nghĩa (mục/hàng nào với mục/hàng nào), điểm lặp cố ý giữ, vấn đề về độ chính xác/thiếu ý/Markdown và lý do.
- `CHANGES`: mỗi hàng sửa ghi tóm tắt **đã đổi gì, vì sao**, số ký tự trước/sau, các ô C:G bị thay đổi/xóa đuôi; danh sách hàng giữ nguyên và lý do ngắn.
- `READBACK`: PASS/FAIL **theo từng hàng đã sửa** sau khi ghép Markdown; xác nhận nội dung không vượt 30.000/ô và 150.000/hàng, cùng kiểm tra A/B và ngoài phạm vi được giữ nguyên.
- `CHAPTER_QA`: PASS/FAIL kiểm tra lại toàn chương sau refactor, số lỗi trùng lặp/độ chính xác/format còn lại và `BLOCKERS` theo tọa độ (nếu có).
**Một chương, một lần giao.** Chỉ `COMPLETED` khi đọc đủ mọi bài, readback từng hàng PASS và Chapter QA PASS. Sau báo cáo hãy giải phóng ownership và chuyển `SLEEPING`; không tự nhận thêm chương hoặc cập nhật J3.
