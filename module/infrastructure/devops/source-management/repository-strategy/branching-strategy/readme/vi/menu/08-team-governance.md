<a id="back-to-top"></a>

# Quy ước nhóm và kiểm soát tích hợp

## Menu
- [Quy ước vai trò, đặt tên và đối tượng sở hữu nhánh](#branch-purpose-naming)
- [Quy tắc thời gian sống, nhánh tồn đọng và đóng nhánh](#branch-lifetime-cleanup)
- [Mục đích review, người chịu trách nhiệm và thời điểm phản hồi](#review-expectations)
- [Chính sách kiểm tra trước merge và mức độ bắt buộc](#premerge-gates-policy)
- [Trách nhiệm duy trì nhánh chính và xử lý tích hợp thất bại](#mainline-health-ownership)
- [Ý định quản trị của nhóm so với quyền, bảo vệ nhánh và CI theo nền tảng](#vendor-enforcement-boundary)

## <a id="branch-purpose-naming">Quy ước vai trò, đặt tên và đối tượng sở hữu nhánh</a>

<details>
<summary>Xem chi tiết</summary>

Quy ước nhánh nên thể hiện **mục đích và đối tượng chịu trách nhiệm** để cả nhóm biết đây là công việc tạm, chuẩn bị phát hành hay bảo trì. Prefix như `feature/`, `fix/`, `release/` hữu ích cho tìm kiếm, nhưng không tự cấp quyền hoặc quyết định lifetime. Chiến lược tốt xác định ai có quyền mở nhánh release, ai được đổi base và ai xác nhận xong việc.

Ví dụ `fix/42-rounding` liên kết Issue #42, `release/1.4` có release owner; một nhánh `temp-final-final` không nói rõ phạm vi và dễ bị bỏ quên. Tên chỉ là một tín hiệu, PR description và metadata vẫn cần đủ.

**Bảng kiểm:** naming pattern, ticket link, branch owner, intended target, expected deletion trigger; các quy tắc quyền host thực thi ở module GitHub/GitLab.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="branch-lifetime-cleanup">Quy tắc thời gian sống, nhánh tồn đọng và đóng nhánh</a>

<details>
<summary>Xem chi tiết</summary>

Nhánh tính năng tồn đọng càng lâu càng dễ chậm feedback, tăng rủi ro xung đột và làm danh sách nhánh khó đọc. Nhóm cần đặt **kỳ vọng tuổi nhánh** phù hợp mô hình: TBD rất ngắn, GitHub Flow không quy định mặc định một con số, release branch theo vòng đời hỗ trợ. Auto-delete sau merge hữu ích nhưng không thay việc theo dõi PR bị bỏ dở.

Ví dụ một nhánh refunds không có commit mới sau 18 ngày: người phụ trách phải quyết định chia nhỏ, đóng PR, tiếp tục với lịch rõ ràng hoặc dọn khi không còn ai cần. Không xóa nhánh release 1.4 vẫn có khách hàng.

**Quan sát:** dashboard tuổi nhánh/PR, nhánh không cập nhật, số PR đóng nhưng chưa cleanup và trách nhiệm cuối; định kỳ review exceptions thay vì xóa tự động tất cả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-expectations">Mục đích review, người chịu trách nhiệm và thời điểm phản hồi</a>

<details>
<summary>Xem chi tiết</summary>

Review hiệu quả cần định nghĩa **mục tiêu** (tính đúng, tương thích, bảo trì), **người chịu trách nhiệm** và **thời gian phản hồi kỳ vọng**. Không phải PR nào cũng cần nhiều người như nhau; thay đổi nhạy cảm cần reviewer chuyên môn, còn sửa tài liệu đơn giản có thể áp mức nhẹ hơn. Review bị tồn đọng nhiều ngày làm vô hiệu mục tiêu tích hợp nhanh dù branch nhỏ.

Ví dụ PR sửa rounding liên quan nghiệp vụ tiền cần người hiểu điều kiện âm và kiểm thử; PR đổi README không nhất thiết đợi cùng nhóm chuyên gia. Reviewer được request không tự chứng minh đã approve, và policy nhóm phải tách review discussion với merge gate.

**Bằng chứng:** median/p90 time to first review, thời gian giải quyết comments, người có quyền chấp thuận và lỗi sau merge.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="premerge-gates-policy">Chính sách kiểm tra trước merge và mức độ bắt buộc</a>

<details>
<summary>Xem chi tiết</summary>

**Pre-merge gates** là điều kiện mà nhóm yêu cầu trước khi tích hợp: kiểm thử cốt lõi, review phù hợp, kiểm tra tính tương thích và có thể đánh giá bảo mật theo mức rủi ro. Chiến lược xác định **cần bằng chứng gì**; GitHub/GitLab branch rules và hệ CI thực thi một phần. Không nên viết 'mọi pipeline luôn bắt buộc' nếu thực tế chỉ check cụ thể được cấu hình.

Ví dụ một thay đổi format dữ liệu thanh toán cần contract tests và domain approval, trong khi đổi chính tả docs có thể không cần cùng mức. Gate phải giúp giảm lỗi có ý nghĩa, tránh đẩy mọi PR vào queue kéo dài.

**Chính sách mẫu:** nêu kiểm tra theo loại change, người xử lý failed/pending, tiêu chí bypass khẩn và log quyết định. Cấu hình workflow YAML thuộc module CI, không viết trong Knowledge này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mainline-health-ownership">Trách nhiệm duy trì nhánh chính và xử lý tích hợp thất bại</a>

<details>
<summary>Xem chi tiết</summary>

Một mainline đáng tin cậy cần người sở hữu phản ứng khi tích hợp làm hỏng build/test hoặc hợp đồng dữ liệu. **Trách nhiệm chung** không nên đồng nghĩa không ai chịu trách nhiệm: team có thể phân công on-call hoặc 'person who broke the build' ưu tiên khôi phục. Tạm dừng nhận thay đổi mới khi main đỏ là lựa chọn chính sách để tránh chồng thêm lỗi.

Ví dụ PR refunds gây lỗi compile ở main, nhưng An đang sửa rounding trên nhánh ngắn: người phụ trách ưu tiên sửa/revert có kiểm chứng, sau đó mới tiếp tục merge. Đừng để mọi người tự coi sự cố là lỗi của pipeline.

**Bằng chứng:** thời gian từ phát hiện tới khôi phục, tần suất main đỏ, postmortem về nguyên nhân và có hay không chủ sở hữu rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="vendor-enforcement-boundary">Ý định quản trị của nhóm so với quyền, bảo vệ nhánh và CI theo nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

Chính sách nhóm như 'main phải có review và test' không phụ thuộc một nhà cung cấp. GitHub có pull requests, branch protection/rulesets; GitLab có merge requests, protected branches và approval rules; mỗi nền tảng có **giới hạn gói, quyền và cách kết hợp rule khác nhau**. CI gửi check result, không tự quyết định chính sách nhánh nào cần tồn tại.

Ví dụ team di chuyển repository GitHub sang GitLab nhưng vẫn muốn short branches và hai reviewer: quy trình có thể giữ, song bắt buộc kiểm tra tính năng và tier trước khi ánh xạ enforcement. Không bê nguyên giả định GitHub ruleset sang protected branch GitLab.

**Bằng chứng bàn giao:** một bản policy độc lập vendor, bảng mapping gate theo nền tảng, test trường hợp được phép/bị chặn, và owner của từng cấu hình.

</details>

- [Quay lại đầu trang](#back-to-top)
