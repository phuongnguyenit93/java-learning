# STEP 7 — WORKER PHASE 1 (K2Q): một chương Knowledge → mọi câu hỏi

**Đọc trọn file này trước mỗi nhiệm vụ.** Chỉ nhận đúng bốn dòng từ Prime: URL tab; `READ=PHASE1,UNIT=<chapterBaseID>,KNOWLEDGE=<EXACT_CHAPTER_A_TO_N_ROWS>,QUESTIONS=P20:W24,RUN=<token>`; `WRITE=<đúng một ô trong A14:AD15>`; đường dẫn file này. Không đọc quy tắc Prime/STEP chung thay cho hợp đồng này. Nếu thiếu/chồng lấn phạm vi, báo `BLOCKER`, không phỏng đoán.

**Mục tiêu:** đề xuất các ID bài học thực sự phù hợp để Prime quyết định điền `readmeRelated.anchorList` của Interview, Case Study, Quiz; **worker không trực tiếp điền field này**, không tạo `apiRelated`.

## 1. Ownership và nguồn

- Đọc đúng module/tab được giao: `A1,B1,I3`; **chỉ đúng vùng Knowledge một chương đã được Prime giao** (các dòng parent/child, A-child ID, B-child title, C:N-child content) và **đúng vùng `P20:W24`** chứa toàn bộ ba YAML của Interview (`P20:R24`), Case Study (`S20:U24`) và Quiz (`V20:W24`). **Ghép riêng từng loại theo thứ tự trái→phải, trên→dưới, không thêm separator**; parse group và index từ YAML thật, xét toàn bộ item I/C/Q và giải thích/resolve/knowledge. Không yêu cầu Prime copy nội dung trong handoff hoặc tự dựa vào catalog chưa kiểm chứng.
- **Chỉ đánh giá ID bài Knowledge thuộc chương được giao**. Mỗi item I/C/Q ở toàn module là ứng viên; không giả định câu hỏi phải nằm cùng chương Knowledge. Không được đọc module khác hay thay đổi bất cứ nội dung nào.
- Quyền ghi **duy nhất một ô** `WRITE` trong `A14:AD15`, do Prime cấp. Thứ tự slot là `A14, B14, ... AD14, A15, ... AD15`, pool Mode A mặc định bắt đầu `A14, B14, C14`; worker **không tự chọn ô** hoặc ghi ngoài 60 ô này. Không ghi `P20:R24`/`S20:U24`/`V20:W24`, file/module/Gradle/Git/CURRENT_STEP; không tự sang chương khác. Một ô chỉ dùng cho đúng nhiệm vụ RUN hiện tại.

## 2. Relevance: KHÔNG ép link

- Đọc trọn các bài Knowledge trong chương. Chỉ đề xuất ID nếu kiến thức **giải thích trực tiếp, tạo điều kiện cần để hiểu, hoặc giúp giải quyết thực chất** câu hỏi/case. Đối với Quiz, đánh giá cả câu hỏi, đáp án đúng/sai và explanation; Interview gồm question/knowledge; Case Study gồm situation/resolve/knowledge. Chỉ dùng đúng dữ liệu `P20:W24` và Knowledge được giao, không tự đọc vùng Phase 4 STEP 6.
- **Không vì cùng từ khóa, cùng chương, cùng lĩnh vực OOP, hoặc muốn đủ coverage mà gán liên kết.** Một chương có thể không phù hợp với **bất cứ** I/C/Q nào: trả `NONE`. Một item có thể nhận 0, 1 hoặc nhiều ID từ chương này nếu thật sự cần thiết. Không ép quota.
- Không suy ra ID từ tên/index. Mọi ID phải xuất hiện trong A-child thực; không dùng category/base ID, tên file, Knowledge module khác, API/Swagger hay `apiRelated`.
- Nếu catalog nguồn không đủ hoặc đối tượng bị mơ hồ: báo blocker/review cho Prime, không bịa proposal. Không tự gắn nhãn `null` hay kết luận Knowledge thiếu.

## 3. Ghi một ô — format đơn giản

```text
PHASE=1;UNIT=<EXACT_CHAPTER_BASE_ID>;RUN=<EXACT_TOKEN>
I|1|2|<lesson-id>|<lý do ngắn>
C|3|5|<lesson-id-1>,<lesson-id-2>|<lý do ngắn>
Q|2|4|<lesson-id>|<lý do ngắn>
END=3
```

Các dòng trên chỉ là **mẫu placeholder**. `I/C/Q` là loại câu hỏi, số thứ nhất là groupID, số thứ hai là index 1-based trong group; ID nối bằng dấu phẩy, **chỉ ID thuộc chương được giao**. Mỗi item chỉ một dòng, không duplicate item/ID; lý do ngắn, không `|` hoặc xuống dòng.

Nếu không có một quan hệ đủ căn cứ:

```text
PHASE=1;UNIT=<EXACT_CHAPTER_BASE_ID>;RUN=<EXACT_TOKEN>
NONE
END=0
```

**Không cần YAML/JSON, không chép lại câu hỏi hay bài Knowledge.** Nếu quá 30.000 code points hoặc 32.767 UTF-16 units: **không được chia nhiều ô**, báo `STEP7_STAGING_CAPACITY` để Prime chia nhỏ nhiệm vụ.

## 4. Preflight và bàn giao

1. Kiểm tra đúng URL/gid, RUN, UNIT, **Knowledge chỉ một chương, QUESTIONS đúng P20:W24**, question identity đúng; ô WRITE **đang trống** không formula/note/chip/validation hoặc writer khác. Không xóa ô đang có dữ liệu của người khác.
2. Ghi đúng một ô text; đọc lại **chính ô đó**, xác minh nguyên văn, header, mỗi dòng parse được, END=count, giới hạn chiều dài, không trùng và chỉ có chapter IDs. Nếu readback không khớp → `BLOCKER`.
3. Báo `PHASE=1`, `UNIT`, `RUN`, `WRITE`, số item/ID, `READBACK=PASS/BLOCKER`. **Không xóa ô**; Prime nhận proposal và quyết định khi nào giải phóng. **Phase 1 chưa phải bước APPLY**: Prime sẽ tổng hợp **mọi chương** và tự điền `anchorList` vào ba YAML nguồn rồi QA/readback trước khi giao Phase 2. Sau báo cáo, về SLEEPING, không tự nhận việc tiếp.
