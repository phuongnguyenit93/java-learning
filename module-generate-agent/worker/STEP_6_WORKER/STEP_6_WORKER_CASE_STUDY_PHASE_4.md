# STEP 6 — CASE STUDY — PHASE 4 (WORKER ONLY)

## Xác định group và nhiệm vụ

- Đọc YAML **Phase 3 đã hoàn thiện** tại `S20:U24` (Phase 2 và Phase 3 dùng chung 5 hàng), thêm `A1,B1` để xác nhận chủ đề.
- WRITE chính xác **3 hàng × 3 cột `S:U`**. Group 1 = `S27:U29`; group 2 = `S30:U32`; group 3 = `S33:U35`. Group N = `S(27+3×(N−1)):U(29+3×(N−1))`.
- Suy ra **`groupID = (startRow−27)/3 + 1`**. `startRow >= 27` và `(startRow−27) % 3 = 0`; range phải rộng 3 cột, cao 3 hàng, groupID phải có trong YAML Phase 3 của chính loại này. Nếu sai: `BLOCKER: STEP6_INVALID_WRITE_RANGE`, không ghi gì.
- Chỉ đọc `situation/resolve/knowledge` của **group tương ứng** trong YAML nền. Viết Markdown và code minh chứng cho từng `situation` dựa vào `resolve`/`knowledge`. Giải thích cách kiểm chứng, thêm assertion/test nếu thích hợp; không bịa kết quả chạy code. Tạo một YAML độc lập chứa đủ phần tử của **đúng group** theo thứ tự gốc, index từ 1; không sinh lời giải cho group khác.

~~~yaml
groupID: 1
codes:
  - caseIndex: 1
    code: |-
      Markdown chứa Java code và cách kiểm chứng giải pháp.
~~~

## Quy tắc ghi và QA bắt buộc

- Prime giao đúng **3 dòng**: `<TAB_URL> | READ=A1,B1`; `WRITE=<EXACT_OWNED_RANGE>`; file worker này. Không nhận thêm câu hỏi/groupID hoặc chỉ dẫn trong handoff. GroupID suy ra **chỉ từ hàng đầu WRITE**.
- Preflight tab/gid, `A1,B1`, YAML Phase 3 trong vùng 5 hàng, target chưa có writer/dữ liệu lạ, formula/chip/note/validation. **Chỉ chặn các xung đột thực tế**; dữ liệu có sẵn ở Phase 2/3 là nguồn đọc hợp lệ, không phải blocker. Nếu block WRITE đã có kết quả hợp lệ của cùng group, không ghi đè; báo `UNCHANGED_ALREADY_COMPLETE`.
- **MAX-FILL bắt buộc:** dựng toàn bộ chuỗi YAML cho một group rồi điền ô trái nhất theo thứ tự hàng; chỉ qua ô kế khi không thể thêm Unicode code point tiếp theo do giới hạn ≤30.000 code points hoặc ≤32.767 UTF-16 code units. Cấm chia đều 9 ô, chia theo câu/line/group hoặc cỡ chunk tự chọn; không thêm separator, không cắt surrogate pair, không rút ngắn. Vừa một ô thì chỉ ghi một ô, để các ô còn lại trống. Tràn block → `STEP6_CELL_CAPACITY`; chia sai → `STEP6_NON_MAX_FILL`.
- Parse YAML, so groupID và đủ mọi index 1..N, field không rỗng, kiểm source group không thay đổi, exact readback từng ô và chuỗi ghép. Báo `TYPE`, `PHASE=4`, `GROUP_ID`, `ITEM_COUNT`, `WRITE_RANGE`, `READBACK=PASS/BLOCKER`. Không đọc/ghi block group khác, không sửa YAML nền, không nhập module/Gradle/commit/push/`CURRENT_STEP.VALUE`.
