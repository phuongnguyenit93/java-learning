<a id="back-to-top"></a>

# Hoàn tất PR và liên kết dấu vết công việc

## Menu
- [Điều kiện hoàn tất PR và xung đột chưa được giải quyết](#merge-readiness-and-conflicts)
- [Complete PR so với Set auto-complete](#complete-pr-versus-auto-complete)
- [Abandon, Reactivate và Complete: vòng đời của PR](#abandon-reactivate-versus-complete)
- [Tùy chọn hoàn tất PR và dấu vết hợp nhất trong Azure Repos](#pr-merge-options-and-traceability)
- [Liên kết work item với PR: mục đích và ngữ cảnh công việc](#work-item-and-pr-link)
- [Chính sách bắt buộc liên kết work item khi complete PR](#work-item-linking-policy)
- [Dấu vết PR đã hoàn tất và trạng thái work item liên quan](#completion-record-and-work-item-state)
- [Tình huống kiểm tra quyền complete PR trên nhánh đích](#target-permission-completion-scenario)
- [Chẩn đoán trở ngại do quyền, review vote, policy, check và xung đột](#diagnose-uncompleted-pr)

## <a id="merge-readiness-and-conflicts">Điều kiện hoàn tất PR và xung đột chưa được giải quyết</a>

<details>
<summary>Xem chi tiết</summary>

Một PR **sẵn sàng complete** khi source/target hợp lệ, không còn conflict không thể tự xử lý, các **blocking policies/checks** trên target đạt yêu cầu, và người hoàn tất có quyền phù hợp. Reviewer đồng ý chưa đủ nếu code không thể tích hợp do target đã thay đổi. Trạng thái “merge conflict” phải được xem là vấn đề nội dung/hợp nhất, không chỉ quyền UI.

Ví dụ feature thay đổi cùng khối tính thuế với commit mới trên `main`: Azure Repos có thể đánh dấu conflict. Người viết cần cập nhật source branch và giải quyết bằng cơ chế Git phù hợp, chạy test lại và chờ checks mới; không thử bypass để che conflict kỹ thuật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="complete-pr-versus-auto-complete">Complete PR so với Set auto-complete</a>

<details>
<summary>Xem chi tiết</summary>

**Complete** yêu cầu đưa PR vào target ngay khi đủ điều kiện; **Set auto-complete** lưu ý định tự hoàn tất khi các điều kiện/policies cần thiết đã đạt trong tương lai. Auto-complete **không làm build đang lỗi thành pass**, không tự giải quyết conflict hoặc tạo quyền bị thiếu. Tùy chọn dọn source branch, squash hay chuyển work item phải xem trước khi xác nhận.

Ví dụ PR đã đủ reviewers nhưng validation đang chạy: tác giả có thể chọn auto-complete; khi validation thành công PR mới hoàn tất nếu không có blocker khác. Ghi nhận thời điểm chọn auto-complete và thời điểm Completed riêng biệt.

### Tài liệu tham khảo

- [Microsoft Learn — complete pr versus auto complete](https://learn.microsoft.com/en-us/azure/devops/repos/git/complete-pull-requests?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="abandon-reactivate-versus-complete">Abandon, Reactivate và Complete: vòng đời của PR</a>

<details>
<summary>Xem chi tiết</summary>

PR **Active** đang được đề xuất và xem xét, **Abandoned** nghĩa đề xuất bị ngừng mà không hợp nhất, **Reactivated** là đưa PR bị bỏ lại vào quy trình đánh giá, còn **Completed** là đã thực hiện tích hợp vào nhánh đích. Abandon không đồng nghĩa xóa commits/branch và không nên viết “bug fixed” khi PR chỉ bị đóng.

Ví dụ Alice nhận ra target sai nên Abandon PR cũ và mở PR mới đúng nhánh. Nhật ký PR cũ vẫn hữu ích giải thích quyết định; nếu Reactivate thì phải đánh giá lại target, diff và policy hiện tại thay vì giả định mọi approval cũ vẫn phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pr-merge-options-and-traceability">Tùy chọn hoàn tất PR và dấu vết hợp nhất trong Azure Repos</a>

<details>
<summary>Xem chi tiết</summary>

Khi Complete, Azure Repos có thể cho lựa chọn **basic merge**, **squash**, hoặc các biến thể **rebase**, dọn source branch, thông điệp commit merge và tùy chọn liên quan work item. Kiểu merge ảnh hưởng **dấu vết commit trên target**: squash làm một commit đại diện thay đổi, basic merge giữ lịch sử/merge commit; PR record vẫn liên kết cuộc thảo luận với kết quả.

Thực hành: trước complete ghi source/target, method đã chọn; sau đó mở Completed PR, xem commits hoặc nhánh đích và liên kết PR. Không dựa vào số lượng commit để suy ra mức độ phê duyệt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="work-item-and-pr-link">Liên kết work item với PR: mục đích và ngữ cảnh công việc</a>

<details>
<summary>Xem chi tiết</summary>

**Work item** là bản ghi công việc như bug, task hoặc user story trong Azure Boards; **liên kết PR–work item** giúp người review truy lại bối cảnh tại sao cần thay đổi. Azure Repos giữ mối liên kết như metadata cộng tác; nó không tự chứng minh work item đã hoàn thành hoặc code đã chạy production.

Ví dụ work item `Bug 104` yêu cầu sửa rounding; trong PR liên kết mục này và mô tả case test. Reviewer đối chiếu thay đổi với phạm vi bug. Chi tiết lifecycle của work item, process template hoặc board thuộc Azure Boards, không dạy ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="work-item-linking-policy">Chính sách bắt buộc liên kết work item khi complete PR</a>

<details>
<summary>Xem chi tiết</summary>

Azure Repos có **Check for linked work items** branch policy áp dụng cho PR theo nhánh đích. Khi cấu hình ở mức blocking, PR thiếu liên kết work item theo yêu cầu có thể chưa complete dù build và votes đã đủ. Liên kết work item đúng bản chất giúp truy vết, không nên tạo task giả chỉ để vượt policy.

Ví dụ policy trên `main` yêu cầu một work item, nhưng PR sửa lỗi chưa liên kết `Bug 104`: trang Policies báo chưa đạt. Tác giả gắn bug phù hợp rồi kiểm tra policy được đánh giá lại; không cần thay đổi nội dung Git nếu chỉ thiếu metadata PR.

### Tài liệu tham khảo

- [Microsoft Learn — work item linking policy](https://learn.microsoft.com/en-us/azure/devops/repos/git/branch-policies?view=azure-devops)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="completion-record-and-work-item-state">Dấu vết PR đã hoàn tất và trạng thái work item liên quan</a>

<details>
<summary>Xem chi tiết</summary>

Sau Complete, trang PR lưu **trạng thái Completed**, reviewer votes, discussion, thời điểm và lựa chọn merge; nhánh đích có commit/graph phản ánh tích hợp. Work item liên quan có thể đổi trạng thái nếu người thao tác chọn **transition work items** và quy trình hỗ trợ, nhưng không nên mặc định “Completed PR ⇒ Closed bug”.

Khi audit, xem cả trạng thái PR lẫn work item: ví dụ PR đã merge nhưng bug vẫn Active vì nhóm cần xác minh triển khai. Điều này không mâu thuẫn; hoàn tất PR và hoàn tất nghiệp vụ là hai sự kiện khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="target-permission-completion-scenario">Tình huống kiểm tra quyền complete PR trên nhánh đích</a>

<details>
<summary>Xem chi tiết</summary>

Kịch bản: Alice tạo PR vào `main`, Bob Approve, build xanh nhưng nút Complete vẫn bị từ chối cho Charlie. Kiểm tra **Charlie có đủ quyền complete/contribute phù hợp tại target** không, PR có Active không, và branch policy nào còn chặn. **Contribute to pull requests** cho hoạt động PR không thay thế mọi quyền ghi/complete nhánh đích; **Bypass policies when completing pull requests** là quyền riêng chỉ dùng khi chủ ý override.

Đừng cấp Manage permissions hoặc Bypass diện rộng để xử lý nhanh. Nhờ quản trị viên xem effective permissions tại repository/branch, ghi chính xác quyền thiếu và thử lại với quyền tối thiểu cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="diagnose-uncompleted-pr">Chẩn đoán trở ngại do quyền, review vote, policy, check và xung đột</a>

<details>
<summary>Xem chi tiết</summary>

Chẩn đoán PR chưa Complete theo nhánh quyết định: (1) **Draft/Abandoned**? (2) **source/target** đúng và hợp nhất được? (3) **required reviewer votes** hay comment threads chưa đạt? (4) **build/status/work-item checks** có blocking failure? (5) người complete có **effective rights** không? (6) cần reviewer xử lý hay người quản trị sửa policy?

Ví dụ PR báo “Waiting for required reviewer” là vấn đề phiếu/policy, không phải lỗi push; “Merge conflicts” là vấn đề kết hợp mã; “not authorized” liên quan quyền. Ghi bằng chứng trạng thái và nơi cần sửa cho từng loại thay vì tắt toàn bộ policies.

</details>

- [Quay lại đầu trang](#back-to-top)
