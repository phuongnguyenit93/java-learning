# STEP 6 — QUIZ — PHASE 1 (WORKER ONLY)

**Hợp đồng:** Một worker, đúng **QUIZ / Phase 1**, không thay loại công việc. Vùng đích cố định **`V25:W26`**.

## Nhiệm vụ duy nhất

Tạo **bộ câu hỏi trắc nghiệm về chủ đề `A1,B1`**. Mỗi câu có **đúng 4 đáp án A/B/C/D** và **ghi rõ một đáp án đúng**. Nội dung thô, chưa phân nhóm hay giải thích.

- **Chỉ đọc:** `A1,B1`.
- **Chỉ ghi:** `V25:W26`, văn bản thuần. Giữa hai câu có một dòng trống.
- **Ví dụ định dạng văn bản thô** (không phải YAML/JSON):

~~~text
Câu hỏi?
A. Lựa chọn A
B. Lựa chọn B
C. Lựa chọn C
D. Lựa chọn D
Đáp án đúng: B
~~~

- Đáp án đúng phải chính xác và có thể kiểm chứng; ba distractor hợp lý, không mơ hồ hoặc có nhiều hơn một đáp án hợp lệ. **Chưa giải thích bất kỳ lựa chọn nào**, không thêm metadata hoặc code đáp án. Prime QA đáp án trước Phase 2.

## Gate chất lượng bắt buộc — KHÔNG ĐỂ LỘ ĐÁP ÁN QUA HÌNH THỨC

**Cấm tạo bộ Quiz mà người học có thể đoán đáp án đúng chỉ nhờ độ dài, cách viết hoặc loại trừ ba phương án sai quá lộ liễu, dù `correctAnswerId` về kỹ thuật là chính xác.** Gate này áp dụng cho **mọi câu** ngay khi soạn Phase 1, trước khi ghi `V25:W26`; không đợi Phase 3 viết explanation.

1. **Bốn lựa chọn cùng mức độ hợp lý và cùng kiểu diễn đạt:** trả lời đúng trọng tâm câu hỏi, tương đương về cấu trúc ngữ pháp, độ cụ thể, độ sâu thuật ngữ và độ dài ở mức tự nhiên. Không để chỉ đáp án đúng là câu dài, chi tiết, có điều kiện/ngoại lệ hoặc chứa thuật ngữ chuẩn, trong khi ba distractor ngắn, sơ sài. Kiểm tra độ dài **sau khi bỏ nhãn A/B/C/D**; tỷ lệ `dài nhất/ngắn nhất > 1,5` là **cờ đỏ phải viết lại/giải trình và QA thủ công**, không phải mục tiêu nhồi chữ cho đủ độ dài.
2. **Distractor phải là hiểu nhầm có thật và có sức đánh lừa người học chưa nắm vững kiến thức:** ví dụ nhầm phạm vi áp dụng, lifecycle, điều kiện, thứ tự thực thi, tính chất gần đúng hoặc hai khái niệm liên quan. Mỗi distractor sai ở **một điểm kỹ thuật xác định, kiểm chứng được**, không sai bằng câu phi lý, trái chủ đề, vô nghĩa hay dễ loại chỉ vì dùng từ tuyệt đối như `luôn`, `không bao giờ`, `bắt buộc`, `chỉ` (trừ khi chính giới hạn đó là nội dung cần kiểm tra và cả bốn lựa chọn được thiết kế cân bằng).
3. **Không có tín hiệu tiết lộ đáp án:** không để chỉ lựa chọn đúng có lời giải thích/biện minh, nhiều mệnh đề, ví dụ hoặc điều kiện bổ sung; không dùng mẫu “ba đáp án tuyệt đối sai, một đáp án diễn đạt mềm dẻo đúng”; không lặp lại nguyên văn từ khóa của câu hỏi chỉ ở đáp án đúng; không tạo lỗi chính tả, chênh lệch phong cách hoặc dấu hiệu định dạng chỉ ở distractor.
4. **Phân bố vị trí đúng trong TOÀN BỘ Quiz:** rải `A/B/C/D` gần đều (chênh lệch số lần xuất hiện giữa các chữ cái **không quá 1** khi có ít nhất bốn câu), không có **ba câu liên tiếp** cùng một chữ cái đúng và không sắp theo chu kỳ dễ đoán. Đổi vị trí **cả nội dung lựa chọn và nhãn đúng** trước khi ghi Phase 1; đảo vị trí một mình **không** thay thế việc viết distractor chất lượng.
5. **Blind review từng câu:** che dòng `Đáp án đúng`, chỉ đọc câu hỏi + bốn lựa chọn và thử đoán bằng **hình thức**, không dùng kiến thức chuyên môn. Nếu vẫn nhận ra một lựa chọn nổi trội về độ dài/độ chính xác câu chữ hoặc loại ngay ba distractor vì quá ngớ ngẩn → **FAIL, viết lại các lựa chọn rồi kiểm tra lại tính đúng/sai và tính duy nhất**. Với bộ từ 10 câu trở lên, nếu đáp án đúng là lựa chọn dài nhất ở **trên 40% số câu**, phải rà và cân bằng lại để không lộ pattern; tỷ lệ thấp hơn **không miễn** blind review từng câu.

**Completion gate của worker:** rà **100%** số câu, thống kê `A/B/C/D` đúng, phát hiện câu có độ dài lệch quá mức, câu có distractor dễ loại và câu có cue hình thức; sửa toàn bộ trường hợp FAIL **trước WRITE**. Nếu không thể tạo bốn lựa chọn hợp lệ, `BLOCKER: STEP6_QUIZ_ANSWER_LEAK` thay vì cố ghi một câu chất lượng thấp. Trong báo cáo về Prime bổ sung `ANSWER_LEAK_QA=PASS/BLOCKER`, `CORRECT_DISTRIBUTION=A:n,B:n,C:n,D:n`, `FLAGGED_QUESTIONS=0/<indices>`; **không ghi báo cáo/đánh dấu QA vào các ô Sheet**.

## Phạm vi và an toàn bắt buộc

- **Chỉ thực hiện đúng loại và Phase của file này**; báo Prime rồi dừng, không tự sang Phase khác. Tin giao Prime chỉ chứa URL tab với `READ=A1,B1`, vùng `WRITE`, và đường dẫn file này; **không yêu cầu Prime thêm hướng dẫn hay cung cấp nội dung**.
- Tự đọc `A1,B1` trên tab được chỉ định để xác minh đúng chủ đề; các nguồn bổ sung (nếu có) **đã cố định ngay trong file này**. Không lấy Knowledge/Menu/Swagger/source Java/Quiz khác hay lịch sử chat làm nguồn sáng tác.
- Chỉ ghi **chính xác vùng WRITE được giao**, với giá trị thuần văn bản (không tạo công thức, đổi format/validation/notes/header). Kiểm tra trước khi viết: ô có formula, chip, dữ liệu lạ, owner khác hoặc nội dung đã thay đổi không rõ nguyên nhân → `BLOCKER`, không overwrite.
- **BẮT BUỘC CHIA Ô THAM LAM (MAX-FILL), KHÔNG CHIA ĐỀU:** Hoàn thiện toàn bộ chuỗi đầu ra trước. Điền **ô đầu tiên tối đa có thể**: lấy tiền tố dài nhất của phần còn lại sao cho **≤30.000 Unicode code points và ≤32.767 UTF-16 code units**. **Chỉ chuyển sang ô kế tiếp khi thêm ký tự Unicode tiếp theo sẽ vượt một trong hai giới hạn.** Tiếp tục theo thứ tự trái→phải, trên→dưới. Nếu cả chuỗi còn lại vừa một ô, **phải ghi trọn vào đúng một ô**, bỏ trống các ô sau. **CẤM** chia đều thành các phần, chia theo câu/group/line/YAML, chia để dễ đọc, đặt cỡ chunk tự chọn (2.000/2.895/5.000/28.000...), hoặc sang ô khác khi ô hiện tại còn chứa được ký tự tiếp theo. Trường hợp phần còn lại vượt 30.000 ký tự thường thì mọi ô không phải ô cuối **phải chứa đúng 30.000 code points**, trừ khi giới hạn UTF-16 chạm trước. Không chèn/thay/xóa ký tự phân cách hoặc cắt giữa surrogate pair. Không đủ tổng sức chứa → `BLOCKER: STEP6_CELL_CAPACITY`, không ghi bản thiếu. Sau ghi phải kiểm **max-fill cho từng ô không cuối** và exact readback của từng ô + chuỗi ghép.
- Sau khi ghi, đọc lại **từng ô đã ghi**, so sánh cả chuỗi ghép với bản đã soạn. Báo `TYPE`, `PHASE`, `WRITE_RANGE`, `ITEM_COUNT`, `READBACK=PASS/BLOCKER`; nếu BLOCKER ghi rõ lý do. Không chạy Gradle/importer, không sửa module/`CURRENT_STEP.VALUE`, không commit/push, không tự xóa/merge vùng khác.

**Ràng buộc mới:** Phase 1 **vẫn 2 hàng**, nhưng được dời xuống **25–26**. Phase 2 (20–24) đọc Phase 1 tại vùng mới này. Không đọc hoặc overwrite hàng 22–23 cũ.
