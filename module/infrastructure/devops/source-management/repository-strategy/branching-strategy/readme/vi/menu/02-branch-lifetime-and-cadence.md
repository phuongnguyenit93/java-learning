<a id="back-to-top"></a>

# Vòng đời nhánh và nhịp tích hợp

## Menu
- [Nhánh ngắn hạn so với nhánh dài hạn: mục đích và chi phí phối hợp](#short-vs-long-lived-branches)
- [Quan hệ giữa nhịp tích hợp, kích thước thay đổi và phản hồi sớm](#cadence-change-size-feedback)
- [Bằng chứng nhánh phân kỳ, xung đột và tích hợp dồn cuối kỳ](#divergence-and-conflict-evidence)
- [Chia nhỏ công việc và review mà không trì hoãn tích hợp](#review-small-increments)
- [Feature flags: kiểm soát tính năng chưa hoàn thiện và tích hợp từng phần](#incomplete-work-policy)

## <a id="short-vs-long-lived-branches">Nhánh ngắn hạn so với nhánh dài hạn: mục đích và chi phí phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

Nhánh **ngắn hạn** có mục tiêu hẹp, nhanh được tích hợp và đóng; nhánh **dài hạn** tồn tại qua nhiều thay đổi hoặc phiên bản. Hai loại không chỉ khác tên: thời gian tách khỏi main càng lâu thì đội càng tích lũy giả định riêng, lệch API, kiểm thử và review. Nhưng nhánh bảo trì `release/1.4` có thể cần tồn tại dài vì khách hàng còn dùng bản đó; không nên áp quy tắc xóa nhánh tính năng cho nó.

Tình huống: An sửa rounding một ngày trên `fix-rounding`; Bình phát triển refunds ba tuần trên `feature/refunds`. Nếu cùng một người sửa cả hai ở nhánh dài, review cuối kỳ khó kiểm soát hơn. Tách thay đổi nhỏ độc lập và dùng flags khi cần.

**Quan sát:** đo tuổi nhánh từ commit tách tới merge, số lần đồng bộ mainline và số commit chưa tích hợp. Dữ liệu này hữu ích hơn nhãn 'feature'.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="cadence-change-size-feedback">Quan hệ giữa nhịp tích hợp, kích thước thay đổi và phản hồi sớm</a>

<details>
<summary>Xem chi tiết</summary>

**Nhịp tích hợp** là khoảng thời gian giữa những lần thay đổi được đưa vào dòng chung; **batch size** là quy mô thay đổi được review/merge mỗi lần. Tích hợp thường xuyên với batch nhỏ tạo phản hồi sớm, thường giảm chi phí hợp nhất và dễ xác định commit gây lỗi. Đây là lựa chọn vận hành, không phải bảo đảm rằng mọi change nhỏ đều an toàn.

Ví dụ hai PR gồm 20 dòng, mỗi PR có test và mô tả, dễ chia trách nhiệm hơn một PR gồm 2.000 dòng cộng nhiều hành vi sau hai tuần. Tuy nhiên chia nhỏ phải giữ chức năng trung gian hợp lệ; không nên merge nửa giao dịch thanh toán khiến production hỏng.

**Đo lường:** theo dõi lead time, số ngày code chờ review, thời gian main đỏ và thay đổi mỗi PR. Nếu PR nhỏ nhưng bị queue năm ngày, team chưa thực sự tích hợp nhanh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="divergence-and-conflict-evidence">Bằng chứng nhánh phân kỳ, xung đột và tích hợp dồn cuối kỳ</a>

<details>
<summary>Xem chi tiết</summary>

Nhánh phân kỳ khi lịch sử của nhánh công việc và mainline có commit mà bên kia chưa có. Rủi ro bao gồm conflict dòng mã, test lệch và **semantic conflict** khi hai thay đổi merge sạch nhưng vi phạm giả định chung. Một biểu đồ commit phân nhánh rộng lâu ngày cho thấy thời gian tách khỏi thực tế nhóm, không tự chứng minh lỗi đã xảy ra.

Ví dụ An thay đơn vị tiền tệ còn Bình sửa rounding dựa trên đơn vị cũ: có thể Git không báo conflict, nhưng kiểm thử nghiệp vụ mới phát hiện kết quả sai. Nhóm nên cập nhật sớm và yêu cầu review bối cảnh.

**Bằng chứng:** nhìn graph nhánh, số commit phía trước/phía sau, ngày merge-base, conflict lặp lại và kết quả integration tests. Phân biệt 'diff lớn vì generated files' với thay đổi logic lớn trước khi chọn giải pháp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-small-increments">Chia nhỏ công việc và review mà không trì hoãn tích hợp</a>

<details>
<summary>Xem chi tiết</summary>

Chia một tính năng lớn thành **vertical slices** có thể review được: thay đổi cấu trúc chuẩn bị, hợp đồng interface tương thích, xử lý nghiệp vụ tối thiểu, rồi mở dần hành vi. Mỗi slice cần giữ dòng chung ổn định; review sớm giúp phát hiện giả định trước khi toàn bộ hoàn tiền được xây xong. Một user story có thể cần nhiều PR, không phải một PR khổng lồ tương ứng một ticket.

Ví dụ tính năng hoàn tiền được chia PR 1 bổ sung interface không phá khách hàng cũ, PR 2 thêm implementation sau flag mặc định off, PR 3 thêm giám sát và bật có kiểm soát. Reviewer kiểm tra từng rủi ro thay vì 60 file cùng lúc.

**Kiểm chứng:** yêu cầu mỗi PR có mục đích, thay đổi giới hạn, điều kiện chấp nhận và trạng thái có thể tích hợp. Không dùng 'chia nhỏ' để hợp thức mã chưa compilable.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="incomplete-work-policy">Feature flags: kiểm soát tính năng chưa hoàn thiện và tích hợp từng phần</a>

<details>
<summary>Xem chi tiết</summary>

**Feature flag** là điều kiện bật/tắt một hành vi lúc chạy hoặc khi triển khai, giúp mã chưa mở cho người dùng vẫn tích hợp sớm. Flag khác branch: branch giữ phiên bản nguồn tách biệt, còn flag kiểm soát đường thực thi trong cùng mã nguồn. Nó hỗ trợ Trunk-Based Development và quy trình phát hành riêng nhưng có chi phí trạng thái, kiểm thử cả on/off và dọn flag cũ.

Ví dụ refunds được merge sau flag `refunds.enabled=false`; production vẫn dùng luồng cũ, đội có thể test nội bộ rồi bật dần. Nếu flag rò rỉ dữ liệu hoặc code off vẫn gây thay đổi schema không tương thích, việc 'tắt flag' chưa đủ an toàn.

**Checklist:** xác định owner, default state, thời điểm bật, test khi tắt/bật, rollback và ngày xóa. Đừng thay thế review bằng flag hay để cờ vĩnh viễn.

### Tài liệu tham khảo
- [Trunk Based Development — Feature flags](https://trunkbaseddevelopment.com/feature-flags/)

</details>

- [Quay lại đầu trang](#back-to-top)
