<a id="back-to-top"></a>

# Đề xuất thay đổi bằng merge request

## Menu
- [Đề xuất từ nhánh trong cùng project](#shared-project-contribution)
- [Fork là project riêng và cách đóng góp về upstream](#forked-project-contribution)
- [Project/nhánh nguồn và project/nhánh đích của merge request](#source-target-project-branches)
- [Mô tả thay đổi, assignee và trạng thái Draft/Ready](#mr-description-assignee-draft)
- [Mở, cập nhật, sẵn sàng đánh giá, hợp nhất hoặc đóng merge request](#mr-lifecycle-states)

## <a id="shared-project-contribution">Đề xuất từ nhánh trong cùng project</a>

<details>
<summary>Xem chi tiết</summary>

Khi người đóng góp có quyền phù hợp trong project chung, họ thường tạo **nhánh nguồn riêng** để sửa mà không đẩy thẳng lên nhánh chính. MR giúp trình bày mục đích, khác biệt tệp và yêu cầu reviewer xem trước khi hợp nhất. Khả năng tạo nhánh hoặc push phụ thuộc cả role và protection rules, không phải cứ là Developer thì luôn push được mọi nhánh.

Ví dụ An sửa lỗi #42 trên `fix-rounding`, mở MR !57 vào `main`. Hãy xem trên UI source và target có cùng project nhưng là nhánh khác nhau. Nếu push vào main bị chặn, hành vi tốt là đề xuất qua MR theo chính sách, không xin quyền Maintainer chỉ để tránh review.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="forked-project-contribution">Fork là project riêng và cách đóng góp về upstream</a>

<details>
<summary>Xem chi tiết</summary>

Fork trên GitLab là **project riêng** được tạo từ một project gốc (upstream), không chỉ là một branch. Người ngoài có thể đề xuất thay đổi từ fork mà không được cấp quyền push tới repository gốc, tùy quyền đọc và chính sách cho phép fork. Group và visibility có thể giới hạn cách tạo hoặc chia sẻ fork.

Ví dụ contractor fork project public, tạo nhánh sửa lỗi ở fork rồi mở MR về `company/payments/gateway:main`. Reviewer kiểm tra source project trong namespace contractor và target project thuộc company. Fork owner không tự có quyền merge hoặc thay đổi protected branch ở upstream.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="source-target-project-branches">Project/nhánh nguồn và project/nhánh đích của merge request</a>

<details>
<summary>Xem chi tiết</summary>

Mỗi MR so sánh **source project + source branch** với **target project + target branch**. Nhầm target có thể đưa bản sửa vào dòng release khác hoặc cho thấy diff lớn ngoài ý muốn. Tên branch giống nhau không đồng nghĩa chúng tham chiếu cùng lịch sử hay cùng project.

Khi tạo MR trên UI, chọn target project/branch trước, xác nhận source, đọc danh sách tệp thay đổi và số commit được so sánh. Nếu MR từ fork xuất hiện hàng trăm tệp không liên quan, kiểm tra lại base và lịch sử hai bên. Ghi bằng chứng cặp source/target trong mô tả review để người khác không nhầm đường đi của mã.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mr-description-assignee-draft">Mô tả thay đổi, assignee và trạng thái Draft/Ready</a>

<details>
<summary>Xem chi tiết</summary>

Mô tả MR nên trả lời vấn đề nào đang giải quyết, hành vi trước/sau, cách kiểm tra và rủi ro. **Assignee** giúp xác định người đang chịu trách nhiệm đẩy công việc; **reviewer** là người đánh giá, không mặc nhiên trùng assignee. MR **Draft** dùng để thảo luận trước khi sẵn sàng merge; việc đổi sang Ready chỉ là tín hiệu sẵn sàng review, không tự tạo approval.

Ví dụ An mở Draft MR !57 vì chưa có test đầu vào âm, gắn Issue #42. Sau khi bổ sung ví dụ, An đánh dấu Ready và yêu cầu Bình review. Hãy xem nhãn Draft, reviewer và merge widget thay vì suy rằng khi bỏ Draft thì MR đã đủ điều kiện merge.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="mr-lifecycle-states">Mở, cập nhật, sẵn sàng đánh giá, hợp nhất hoặc đóng merge request</a>

<details>
<summary>Xem chi tiết</summary>

Một MR đi qua mở đề xuất, cập nhật commit ở source, thảo luận, Ready, review và kết thúc bằng **Merged** hoặc **Closed without merge**. Khi tác giả đẩy commit mới, diff thay đổi và có thể cần reviewer kiểm tra lại; approval cũ có thể bị reset tùy setting. Trạng thái Open không chứng minh đủ điều kiện hợp nhất.

**Quy trình xem bằng chứng:** (1) đọc mô tả và source/target; (2) xem Changes và discussions; (3) kiểm tra reviewers/approvals; (4) xem merge status và checks; (5) xác minh merged commit/target source nếu Merged. Khi MR đóng mà không merge, ghi rõ lý do như thay thế bởi MR khác hoặc yêu cầu bị hủy.

</details>

- [Quay lại đầu trang](#back-to-top)
