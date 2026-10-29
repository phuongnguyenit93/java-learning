# STEP 6 — QUIZ — PHASE 3 (WORKER ONLY)

## Nhiệm vụ duy nhất

- **READ và WRITE chung đúng vùng `V20:W24` (5 hàng).** Đọc YAML khung do Phase 2 tạo trong vùng này và `A1,B1`. Phase 3 không có vùng riêng theo group, không dùng hàng 27+.
- Chỉ bổ sung `explanation` cho **cả bốn lựa chọn A/B/C/D của mọi câu hỏi trong mọi group** của YAML Phase 2. Giải thích vì sao từng lựa chọn đúng hoặc sai, tương thích tiêu chí chất lượng Quiz. Giữ nguyên nội dung `question`, `answers[].id`, `answers[].answer`, `correctAnswerId`, `groupID`, `groupName`, **`readmeRelated.anchorList`** và thứ tự. Nếu thiếu khung Related từ Phase 2, báo blocker, không tự bổ sung hoặc suy đoán liên kết.
- Ghi lại **toàn bộ YAML các group** đã được bổ sung thông tin vào **chính `V20:W24`**, không tạo file/group YAML phụ và không sửa vùng Phase 1.

Ví dụ cấu trúc sau Phase 3:

~~~yaml
quiz:
  - groupID: 1
    groupName: "Tên nhóm"
    questions:
      - question: "Câu hỏi từ Phase 2?"
        readmeRelated:
          anchorList: []
        answers:
          - { id: A, answer: "A", explanation: "Vì sao A đúng hoặc sai." }
          - { id: B, answer: "B", explanation: "Vì sao B đúng hoặc sai." }
          - { id: C, answer: "C", explanation: "Vì sao C đúng hoặc sai." }
          - { id: D, answer: "D", explanation: "Vì sao D đúng hoặc sai." }
        correctAnswerId: B
~~~

- Đủ bốn `explanation` không rỗng cho mỗi câu; `readmeRelated.anchorList` vẫn là `[]`, không có `apiRelated` hoặc `readmeRelated.anchor/file`. Không điền ID Knowledge hay `null` trong STEP 6. Nếu phát hiện `correctAnswerId` của Phase 2 sai/mơ hồ, báo BLOCKER; không âm thầm đổi đáp án đúng. Quiz dừng ở Phase 3.

## An toàn ghi và nghiệm thu

- Prime chỉ giao **BA dòng**: `<TAB_URL> | READ=A1,B1`, `WRITE=<EXACT_RANGE>`, đường dẫn file này. Phase 3 **một worker mỗi loại**, xử lý **tất cả group** trong vùng được giao; không suy groupID từ hàng và không yêu cầu Prime gửi groupID/nội dung.
- Kiểm tab/gid/`A1,B1` và đọc toàn bộ vùng Phase 2. Nội dung Phase 2 **đã có sẵn là nguồn hợp lệ để cập nhật**; chỉ được thay thế giá trị của **đúng các field Phase 3 được giao**. Nếu nguồn có công thức/chip/validation/note xung đột, người khác sửa đồng thời, thiếu field, dữ liệu không phải YAML Phase 2, hoặc sai group/index: BLOCKER, không ghi đè. Không chạm cột hoặc vùng của loại khác.
- **MAX-FILL bắt buộc:** soạn nguyên chuỗi YAML trước; điền từng ô trong WRITE từ trái→phải, trên→dưới, bằng tiền tố **dài nhất có thể** với đồng thời ≤30.000 Unicode code points và ≤32.767 UTF-16 code units. Chỉ chuyển ô khi ký tự tiếp theo không vừa; không chia đều, chia nhóm/câu/dòng, không thêm separator, không cắt surrogate pair. Các ô cuối không dùng phải **được để trống** sau khi cập nhật (kể cả khi Phase 2 cũ trải rộng hơn). Không đủ chỗ → `STEP6_CELL_CAPACITY`; chia sai → `STEP6_NON_MAX_FILL`.
- Trước ghi kiểm cấu trúc, mọi field khác không đổi khi so sánh YAML semantic. Sau ghi exact readback từng ô và chuỗi ghép, parse lại YAML và kiểm đủ count/groupID. Nếu không bảo toàn nội dung Phase 2, báo BLOCKER và không xác nhận PASS.
- Báo `TYPE`, `PHASE=3`, `WRITE_RANGE`, `GROUP_COUNT`, `ITEM_COUNT`, `READBACK=PASS/BLOCKER`. Không chạy importer/Gradle, không sửa module/`CURRENT_STEP.VALUE`, không commit/push hoặc tự chuyển Phase 4.
