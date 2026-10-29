# STEP 6 — INTERVIEW — PHASE 3 (WORKER ONLY)

## Nhiệm vụ duy nhất

- **READ và WRITE chung đúng vùng `P20:R24` (5 hàng).** Đọc YAML khung do Phase 2 tạo trong vùng này và `A1,B1`. Phase 3 không có vùng riêng theo group, không dùng hàng 27+.
- Chỉ bổ sung `knowledge` cho **mọi câu hỏi của mọi group** trong YAML Phase 2. Mỗi knowledge phải nêu kiến thức trọng tâm cần vận dụng; không viết `answer`. Giữ nguyên `question`, `groupID`, `groupName` và thứ tự/số lượng.
- Ghi lại **toàn bộ YAML các group** đã được bổ sung thông tin vào **chính `P20:R24`**, không tạo file/group YAML phụ và không sửa vùng Phase 1.

Ví dụ cấu trúc sau Phase 3:

~~~yaml
interviewQuestion:
  - groupID: 1
    groupName: "Tên nhóm"
    questions:
      - question: "Câu hỏi từ Phase 2?"
        knowledge: "Kiến thức nền cần dùng để trả lời."
~~~

- Mọi `questions[].knowledge` của tất cả group phải không rỗng; không có field `answer` trong YAML nền.

## An toàn ghi và nghiệm thu

- Prime chỉ giao **BA dòng**: `<TAB_URL> | READ=A1,B1`, `WRITE=<EXACT_RANGE>`, đường dẫn file này. Phase 3 **một worker mỗi loại**, xử lý **tất cả group** trong vùng được giao; không suy groupID từ hàng và không yêu cầu Prime gửi groupID/nội dung.
- Kiểm tab/gid/`A1,B1` và đọc toàn bộ vùng Phase 2. Nội dung Phase 2 **đã có sẵn là nguồn hợp lệ để cập nhật**; chỉ được thay thế giá trị của **đúng các field Phase 3 được giao**. Nếu nguồn có công thức/chip/validation/note xung đột, người khác sửa đồng thời, thiếu field, dữ liệu không phải YAML Phase 2, hoặc sai group/index: BLOCKER, không ghi đè. Không chạm cột hoặc vùng của loại khác.
- **MAX-FILL bắt buộc:** soạn nguyên chuỗi YAML trước; điền từng ô trong WRITE từ trái→phải, trên→dưới, bằng tiền tố **dài nhất có thể** với đồng thời ≤30.000 Unicode code points và ≤32.767 UTF-16 code units. Chỉ chuyển ô khi ký tự tiếp theo không vừa; không chia đều, chia nhóm/câu/dòng, không thêm separator, không cắt surrogate pair. Các ô cuối không dùng phải **được để trống** sau khi cập nhật (kể cả khi Phase 2 cũ trải rộng hơn). Không đủ chỗ → `STEP6_CELL_CAPACITY`; chia sai → `STEP6_NON_MAX_FILL`.
- Trước ghi kiểm cấu trúc, mọi field khác không đổi khi so sánh YAML semantic. Sau ghi exact readback từng ô và chuỗi ghép, parse lại YAML và kiểm đủ count/groupID. Nếu không bảo toàn nội dung Phase 2, báo BLOCKER và không xác nhận PASS.
- Báo `TYPE`, `PHASE=3`, `WRITE_RANGE`, `GROUP_COUNT`, `ITEM_COUNT`, `READBACK=PASS/BLOCKER`. Không chạy importer/Gradle, không sửa module/`CURRENT_STEP.VALUE`, không commit/push hoặc tự chuyển Phase 4.
