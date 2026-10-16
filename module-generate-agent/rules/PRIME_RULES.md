# PRIME_RULES.md — Quy định bắt buộc dành cho Prime

Áp dụng cho Prime khi điều phối bất kỳ STEP nào của `module-generate-agent/temp/`. Đây là một phần của bộ quy định chung trong `module-generate-agent/rules/`, không phải file STEP. Prime phải đọc toàn bộ các file hiện có trong thư mục `rules/` cùng `module-generate-agent/temp/GENERAL_AGENT_RULES.md` và file STEP hiện hành **trước mỗi STEP**; nếu context không còn đầy đủ hoặc không chắc chắn, phải đọc lại theo General Rules.

## 1. Prime phải điều phối đến khi hoàn thành TOÀN BỘ STEP

1. **Giao việc cho worker KHÔNG đồng nghĩa STEP đã hoàn thành.** Prime tuyệt đối không kết thúc phiên/chốt câu trả lời chỉ vì đã giao xong đợt việc đầu tiên (ví dụ giao cho 3 worker). Prime giữ trách nhiệm điều phối cho đến khi **toàn bộ phạm vi STEP được xử lý và xác minh**.
2. Trước khi giao, Prime lập danh sách đầy đủ các đơn vị công việc phải xử lý (module, chương, bài hoặc hạng mục tùy STEP), xác định đơn vị đã hoàn thành, còn thiếu, đang chạy, `BLOCKER`/`FAILED` và worker chịu trách nhiệm. Không chỉ lập kế hoạch cho số đơn vị vừa đủ lấp đầy pool ban đầu.
3. Sau mỗi đợt giao việc, Prime **tiếp tục theo dõi trạng thái và tiếp nhận báo cáo**. Khi một worker hoàn thành và đã giải phóng ownership, Prime **ưu tiên giao ngay đơn vị công việc kế tiếp cho chính worker có thể tái sử dụng**, cho tới khi hàng đợi công việc của STEP rỗng; vẫn giữ giới hạn pool và các quy tắc tái sử dụng trong General Rules.
4. Nếu mọi worker còn đang chạy, Prime duy trì vai trò điều phối, sử dụng các checkpoint/trạng thái **thụ động, không gây thay đổi** với nhịp phù hợp. **Không ping worker đang chạy chỉ để tạo heartbeat, không gửi nhiệm vụ trùng lặp, không spawn thêm để vượt giới hạn, không tự kết luận worker `SLEEPING` là còn thực thi.** Chỉ giao tiếp khi có nhiệm vụ mới, kết quả cần xử lý hoặc sửa lỗi có căn cứ.
5. Khi có `BLOCKER`/`FAILED`, Prime kiểm tra bằng chứng và trạng thái thực tế, xác định phần nào đã làm xong để **chỉ điều phối phần còn thiếu hoặc sửa lỗi**, không làm lại/ghi đè tùy tiện. Nếu thực sự không thể tiếp tục an toàn, nêu blocker cụ thể, phạm vi bị ảnh hưởng và việc chưa hoàn thành; **không tuyên bố STEP hoàn tất**.
6. **Chỉ được tuyên bố COMPLETED khi toàn bộ đơn vị công việc thuộc STEP đã đạt điều kiện hoàn thành của chính STEP đó**, bao gồm review, kiểm tra dữ liệu và readback khi bắt buộc. Prime phải tự đối chiếu kết quả thực tế; không coi việc worker báo xong hoặc danh sách phân công hết lượt là bằng chứng đầy đủ.
7. Sau khi hoàn tất và xác minh toàn STEP, Prime thực hiện việc cập nhật `Current STEP` theo mục 2 **trước khi đóng phiên/báo hoàn tất**. Không tự chạy STEP tiếp theo hoặc commit/push nếu nhiệm vụ hiện tại không cho phép.

## 2. Cập nhật trạng thái Current STEP ở mỗi tab module sau khi STEP hoàn tất

1. Trong **mỗi tab Google Sheets của module thực sự thuộc phạm vi STEP vừa hoàn thành**, vùng `Module Project` có nhãn **`J2 = Current STEP`** và ô **`J3`** lưu STEP đã hoàn thành. Khi STEP số **N** hoàn thành đầy đủ và đã được Prime xác minh cho tab đó, Prime phải ghi vào đúng **`J3`** giá trị **`STEP N`** (ví dụ hoàn thành STEP 1 → `STEP 1`; STEP 5 → `STEP 5`). Đây là **trạng thái STEP đã hoàn tất**, không phải STEP đang giao dở.
2. Prime phải lấy **URL gốc và mapping tên module → `sheet id` (gid)** từ `module-generate-info.txt` theo General Rules, xác minh đúng tab, đúng module và nhãn `J2` trước khi ghi `J3`. **Riêng STEP 1**, mapping của tab mới chỉ tồn tại sau khi clone/lưu TXT thành công: phải dùng mapping mới đã được xác minh ở cuối STEP 1, không yêu cầu mapping trước khi tab được tạo. Không nhầm `gid` với spreadsheet ID; không cập nhật `Template` hoặc tab không nằm trong phạm vi STEP.
3. **Chỉ cập nhật J3 sau khi STEP đạt toàn bộ điều kiện hoàn thành đối với tab đó**. Nếu STEP chạy nhiều tab, xác minh và cập nhật từng tab đủ điều kiện; không cập nhật đồng loạt những tab còn thiếu, có blocker hoặc chưa được kiểm tra. Nếu có bất kỳ công việc nào của tab đang chạy/đợi review thì giữ nguyên J3.
4. Trước khi ghi, đọc giá trị/công thức hiện có của `J3` và kiểm tra nhãn `J2`. `J3` là ô trạng thái được phép thay thế **giá trị STEP cũ** bằng STEP vừa hoàn tất; không xóa công thức, dữ liệu không phải trạng thái STEP, hoặc ghi lùi về STEP thấp hơn một cách âm thầm. Nếu nhãn sai, ô có công thức, mapping xung đột hoặc không thể xác minh trạng thái an toàn, báo `BLOCKER: CURRENT STEP UPDATE` và không ghi đoán.
5. **Đọc lại `J3` sau khi ghi** và xác minh nguyên văn khớp `STEP N`; ghi nhận tab/gid, STEP, readback `PASS`/`FAIL` trong báo cáo. Không báo hoàn tất toàn bộ phần bàn giao nếu cập nhật J3 bắt buộc còn lỗi mà không nêu rõ.

## 3. Checklist đóng STEP cho Prime

```text
Đã nắm đủ General + Prime + STEP hiện hành (đọc lại nếu quên)?
    ↓
Đã xử lý hết toàn bộ danh sách công việc, không chỉ đợt giao đầu tiên?
    ↓
Đã nhận báo cáo, xác minh kết quả và giải quyết mọi BLOCKER/FAILED?
    ↓
Đã xác nhận hoàn thành theo điều kiện thực tế của STEP cho từng tab?
    ↓
Đã ghi đúng J3 = "STEP N" và đọc lại PASS trên từng tab hoàn thành?
    ↓
Mới báo COMPLETED và kết thúc phiên STEP
```

**Không đánh dấu hoàn thành chỉ từ việc đã giao task cho worker. Không ghi J3 trước khi STEP thực sự hoàn tất.**
