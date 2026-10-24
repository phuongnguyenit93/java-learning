# PRIME_RULES.md — Quy định bắt buộc dành cho Prime

Áp dụng cho Prime khi điều phối bất kỳ STEP nào của `module-generate-agent/`. Đây là một phần của bộ quy định chung trong `module-generate-agent/rules/`, không phải file STEP. Prime phải đọc toàn bộ các file hiện có trong thư mục `rules/` cùng `module-generate-agent/GENERAL_AGENT_RULES.md` và file STEP hiện hành **trước mỗi STEP**; nếu context không còn đầy đủ hoặc không chắc chắn, phải đọc lại theo General Rules.

## 0. Worker modes — chỉ áp dụng trong session được đính kèm mode

Chỉ có **hai mode**. Prime áp dụng giới hạn tổng worker `ACTIVE` + `SLEEPING` của session:

| Mode | Giới hạn pool |
| --- | --- |
| **Mode A — Normal Mode** | **Tối đa 3 worker** |
| **Mode B — Special Mode** | **Tối đa N worker**, với `N` là số nguyên dương người dùng yêu cầu rõ ràng trong session |

- Không chỉ định mode → **mặc định Mode A = 3**. Đây là giới hạn pool tối đa; một STEP có ít nhiệm vụ hơn thì không tạo worker thừa.
- Chỉ định **Mode B** nhưng **không cung cấp N, hoặc N không phải số nguyên dương** → `BLOCKER: SPECIAL_MODE_WORKER_COUNT_REQUIRED`: **dừng điều phối/spawn và yêu cầu người dùng cung cấp số worker**. Không tự dùng 3, 6, 8 hay số mặc định khác. Mode B không có hạn mức cố định ngoài N người dùng yêu cầu và giới hạn công cụ thực tế.
- Mode chỉ hiệu lực **trong session hiện tại đã nhận prompt đó**; không ghi mode thành cấu hình mặc định, không tự mang sang session/chat/STEP độc lập khác. Nếu context bị compact, chỉ tiếp tục mode và N có bằng chứng trong chính session hiện tại.
- Nếu một prompt chứa các mode hoặc số worker mâu thuẫn, báo `BLOCKER: WORKER MODE CONFLICT`, không tự chọn. Prime theo dõi và báo `SESSION_WORKER_MODE`, `MAX_WORKERS`.
- Vẫn **ưu tiên tái sử dụng worker SLEEPING**, không spawn khi còn worker SLEEPING, không vượt quyền sở hữu vùng ghi. Worker `FAILED`/`FINISHED` không thể tái sử dụng không tính vào pool.
- Mọi giới hạn “mặc định 3” ở `GENERAL_AGENT_RULES.md` hoặc các file STEP phải được hiểu theo mode của **session hiện hành**; N của Mode B được chỉ định có ưu tiên hơn hạn mức mặc định, không được áp dụng Mode B cho session khác.

### Bắt buộc tạo worker lần lượt, từng worker một

1. Trước khi tạo worker, Prime kiểm tra trạng thái pool, số worker `ACTIVE` + `SLEEPING`, khả năng tái sử dụng, số nhiệm vụ chờ và giới hạn mode. Mode B thiếu N hợp lệ phải dừng theo blocker phía trên **trước lệnh spawn đầu tiên**.
2. Khi cần tạo mới, **mỗi lệnh spawn chỉ được yêu cầu đúng 1 worker** (`workers` chứa đúng một phần tử). **Không tạo nhiều worker trong cùng một lệnh, không gọi nhiều lệnh spawn song song**, kể cả với yêu cầu số lượng lớn.
3. **Chờ từng lệnh spawn trả kết quả**, kiểm tra worker đã khởi tạo thành công và cập nhật trạng thái pool rồi mới quyết định tạo worker tiếp theo. Nếu gặp lỗi hoặc queue đầy, xử lý/báo blocker trước khi spawn lại; không lặp lại hàng loạt lệnh thất bại.
4. Lặp lại tuần tự cho đến khi đạt số worker được yêu cầu/được phép theo mode **và còn nhiệm vụ độc lập cần giao**. Luôn ưu tiên worker `SLEEPING`; không tạo worker dư chỉ để đủ số nếu không còn việc. Giữ nguyên giới hạn ownership và giới hạn riêng của STEP.

## 1. Prime phải điều phối đến khi hoàn thành TOÀN BỘ STEP

1. **Giao việc cho worker KHÔNG đồng nghĩa STEP đã hoàn thành.** Prime tuyệt đối không kết thúc phiên/chốt câu trả lời chỉ vì đã giao xong đợt việc đầu tiên (ví dụ giao cho 3 worker). Prime giữ trách nhiệm điều phối cho đến khi **toàn bộ phạm vi STEP được xử lý và xác minh**.
2. Trước khi giao, Prime lập danh sách đầy đủ các đơn vị công việc phải xử lý (module, chương, bài hoặc hạng mục tùy STEP), xác định đơn vị đã hoàn thành, còn thiếu, đang chạy, `BLOCKER`/`FAILED` và worker chịu trách nhiệm. Không chỉ lập kế hoạch cho số đơn vị vừa đủ lấp đầy pool ban đầu.
3. Sau mỗi đợt giao việc, Prime **tiếp tục theo dõi trạng thái và tiếp nhận báo cáo**. Khi một worker hoàn thành và đã giải phóng ownership, Prime **ưu tiên giao ngay đơn vị công việc kế tiếp cho chính worker có thể tái sử dụng**, cho tới khi hàng đợi công việc của STEP rỗng; vẫn giữ giới hạn pool và các quy tắc tái sử dụng trong General Rules.
4. Nếu mọi worker còn đang chạy, Prime duy trì vai trò điều phối, sử dụng các checkpoint/trạng thái **thụ động, không gây thay đổi** với nhịp phù hợp. **Không ping worker đang chạy chỉ để tạo heartbeat, không gửi nhiệm vụ trùng lặp, không spawn thêm để vượt giới hạn, không tự kết luận worker `SLEEPING` là còn thực thi.** Chỉ giao tiếp khi có nhiệm vụ mới, kết quả cần xử lý hoặc sửa lỗi có căn cứ.
5. Khi có `BLOCKER`/`FAILED`, Prime kiểm tra bằng chứng và trạng thái thực tế, xác định phần nào đã làm xong để **chỉ điều phối phần còn thiếu hoặc sửa lỗi**, không làm lại/ghi đè tùy tiện. Nếu thực sự không thể tiếp tục an toàn, nêu blocker cụ thể, phạm vi bị ảnh hưởng và việc chưa hoàn thành; **không tuyên bố STEP hoàn tất**.
6. **Chỉ được tuyên bố COMPLETED khi toàn bộ đơn vị công việc thuộc STEP đã đạt điều kiện hoàn thành của chính STEP đó**, bao gồm review, kiểm tra dữ liệu và readback khi bắt buộc. Prime phải tự đối chiếu kết quả thực tế; không coi việc worker báo xong hoặc danh sách phân công hết lượt là bằng chứng đầy đủ.
7. Sau khi hoàn tất và xác minh toàn STEP, Prime thực hiện việc cập nhật `Current STEP` theo mục 2 **trước khi đóng phiên/báo hoàn tất**. Không tự chạy STEP tiếp theo hoặc commit/push nếu nhiệm vụ hiện tại không cho phép.

## 2. Cập nhật trạng thái Current STEP ở mỗi tab module sau khi STEP hoàn tất

**Quy tắc riêng STEP 4 (NEW_FILL ONLY):** Prime quét toàn bộ Menu Cấp 2 và 12 ô C:N trên từng hàng. Hàng có **bất kỳ nội dung** được phân loại `SKIPPED_EXISTING`, không đưa vào queue, không giao Worker, không đánh giá chất lượng và không viết lại. Chỉ hàng **C:N trống hoàn toàn** được giao Worker tạo bài mới và QA. Khi mở session mới phải quét trạng thái Sheet thực tế để tìm các hàng còn trống, không review lại bài cũ. **STEP 5 mới thực hiện review/refactor Knowledge**. J3 STEP 4 chỉ được cập nhật khi không còn bài trống, các bài mới đã QA PASS và không còn blocker/worker pending; không ghi lùi J3 của các STEP sau.

1. Trong **mỗi tab Google Sheets của module thực sự thuộc phạm vi STEP vừa hoàn thành**, vùng `Module Project` có nhãn **`J2 = Current STEP`** và ô **`J3`** lưu STEP đã hoàn thành. Khi STEP số **N** hoàn thành đầy đủ và đã được Prime xác minh cho tab đó, Prime phải ghi vào đúng **`J3`** giá trị **`STEP N`** (ví dụ hoàn thành STEP 1 → `STEP 1`; STEP 5 → `STEP 5`). Đây là **trạng thái STEP đã hoàn tất**, không phải STEP đang giao dở.
2. Prime phải lấy **URL gốc và mapping tên module → `sheet id` (gid)** từ `module-generate-info.txt` theo General Rules, xác minh đúng tab, đúng module và nhãn `J2` trước khi ghi `J3`. **Riêng STEP 1**, mapping của tab mới chỉ tồn tại sau khi clone/lưu TXT thành công: phải dùng mapping mới đã được xác minh ở cuối STEP 1, không yêu cầu mapping trước khi tab được tạo. Không nhầm `gid` với spreadsheet ID; không cập nhật `Template` hoặc tab không nằm trong phạm vi STEP.
3. **Chỉ cập nhật J3 sau khi STEP đạt toàn bộ điều kiện hoàn thành đối với tab đó**. Nếu STEP chạy nhiều tab, xác minh và cập nhật từng tab đủ điều kiện; không cập nhật đồng loạt những tab còn thiếu, có blocker hoặc chưa được kiểm tra. Nếu có bất kỳ công việc nào của tab đang chạy/đợi review thì giữ nguyên J3.
4. Trước khi ghi, đọc giá trị/công thức hiện có của `J3` và kiểm tra nhãn `J2`. `J3` là ô trạng thái được phép thay thế **giá trị STEP cũ** bằng STEP vừa hoàn tất; không xóa công thức, dữ liệu không phải trạng thái STEP, hoặc ghi lùi về STEP thấp hơn một cách âm thầm. Nếu nhãn sai, ô có công thức, mapping xung đột hoặc không thể xác minh trạng thái an toàn, báo `BLOCKER: CURRENT STEP UPDATE` và không ghi đoán.
5. **Đọc lại `J3` sau khi ghi** và xác minh nguyên văn khớp `STEP N`; ghi nhận tab/gid, STEP, readback `PASS`/`FAIL` trong báo cáo. Không báo hoàn tất toàn bộ phần bàn giao nếu cập nhật J3 bắt buộc còn lỗi mà không nêu rõ.
6. **Riêng STEP 6**, mặc dù không có Sheets importer và worker không được chỉnh Sheet, Prime **phải cập nhật `J3 = STEP 6`** sau khi đạt đầy đủ completion gate trong `STEP_6.md`: toàn bộ chương nghiệm thu, tích hợp build/test đạt và module-local `generateApiSwaggerDescription` sinh/kiểm tra skeleton thành công. Ghi và exact readback theo các mục 1–5; không ghi J3 chỉ vì API của một vài chương đã xong hoặc worker báo `COMPLETED`.
7. **Riêng STEP 7**, Prime chỉ cập nhật `J3 = STEP 7` sau khi tất cả chương đã QA đầy đủ, staging VI/EN đã merge an toàn vào Swagger chính, readback PASS, kiểm tra build/test và **xóa sạch `step7-temp` đúng module**; không nâng J3 chỉ vì worker hoàn thành staging hoặc mới merge một phần. Áp dụng preflight J2/J3, cảnh báo trạng thái STEP 7 legacy và exact readback theo các mục 1–5 và `STEP_7.md`.

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
