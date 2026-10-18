<a id="back-to-top"></a>

# Nền tảng cộng tác mã nguồn trên GitLab

## Menu
- [GitLab: nền tảng lưu trữ Git và phối hợp thay đổi của nhóm](#gitlab-hosting-purpose)
- [Rủi ro khi chia sẻ mã nguồn thiếu theo dõi, review và điều kiện hợp nhất](#unstructured-collaboration-risks)
- [Lịch sử phiên bản do Git quản lý và quá trình cộng tác trên GitLab](#git-versus-gitlab)
- [Project GitLab và Git repository: phạm vi cộng tác và lịch sử nguồn](#gitlab-project-repository)
- [Namespace cá nhân, group và subgroup: các cấp tổ chức nguồn](#gitlab-namespace-basics)
- [Trách nhiệm của thành viên, người đóng góp, assignee và reviewer](#gitlab-participants)
- [Merge request: đề xuất thay đổi để thảo luận và đánh giá trước khi hợp nhất](#gitlab-mr-introduction)
- [Quan hệ project, nhánh nguồn/đích, merge request và reviewer](#gitlab-workflow-concepts)

## <a id="gitlab-hosting-purpose">GitLab: nền tảng lưu trữ Git và phối hợp thay đổi của nhóm</a>

<details>
<summary>Xem chi tiết</summary>

GitLab là nền tảng lưu trữ Git repository trong **project**, đồng thời hỗ trợ theo dõi công việc, trao đổi thay đổi và quản lý người có quyền tiếp nhận mã. Vấn đề nhóm cần giải quyết không chỉ là đặt mã lên máy chủ mà còn là nhận biết ai đề xuất, ai xem xét và khi nào mã trở thành phiên bản dùng chung. Nếu chỉ gửi tệp ZIP qua chat, một thay đổi có thể bị ghi đè và thiếu lịch sử quyết định.

**Lộ trình học:** bắt đầu với GitLab project, repository, group/subgroup, người đóng góp, reviewer và merge request (MR), đồng thời phân biệt Git với GitLab. Tiếp theo học cách tổ chức project và cấp quyền, gửi MR từ nhánh chung hoặc fork, đánh giá/Approve, kiểm tra protected branch và các quy tắc hợp nhất; sau cùng nối Issue, MR và Release trong một tình huống từ yêu cầu đến nguồn chung được chấp nhận. Cơ chế commit Git và triển khai CI/CD có chương/module riêng, không nên hiểu là chức năng tự động của một MR.

Ví dụ nhóm thanh toán cùng sửa phép làm tròn: tác giả tạo đề xuất, đồng nghiệp xem khác biệt, người đủ quyền mới hợp nhất. Hãy mở một project mẫu và phân biệt giao diện **Code**, **Merge requests** và **Issues**. Source đã được tải lên GitLab chưa có nghĩa đề xuất đã merge hoặc bản mới đã được triển khai.

### Tài liệu tham khảo
- [GitLab Docs — Manage projects](https://docs.gitlab.com/user/project/working_with_projects/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="unstructured-collaboration-risks">Rủi ro khi chia sẻ mã nguồn thiếu theo dõi, review và điều kiện hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

Không có quy trình cộng tác, hai người có thể sửa cùng phương thức nhưng mỗi người dựa vào giả định nghiệp vụ khác nhau. Lỗi không chỉ là xung đột dòng mã; thiếu mô tả, bình luận và trách nhiệm phê duyệt khiến nhóm không thể giải thích vì sao một bản sửa được nhận. Review giảm rủi ro nhưng không thay cho kiểm chứng hành vi.

Tình huống Issue #42 nói tổng tiền âm phải bị từ chối; MR !57 đổi logic thành đưa về 0. Nếu tác giả không mô tả quy tắc, reviewer khó phát hiện sai ý định. Hãy kiểm tra MR có mục đích, tập tệp thay đổi, thảo luận, người xem xét và trạng thái merge; chỉ rõ điều gì không thể chứng minh nếu từng bằng chứng thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="git-versus-gitlab">Lịch sử phiên bản do Git quản lý và quá trình cộng tác trên GitLab</a>

<details>
<summary>Xem chi tiết</summary>

Git quản lý commit, nhánh, lịch sử và việc đồng bộ repository; các cơ chế này hoạt động ngay cả khi không dùng GitLab. GitLab tổ chức những dữ liệu Git trong project, thêm quyền thành viên, merge request, bình luận, Issues và Releases. **Merge cục bộ** chỉ là thao tác trên lịch sử; **MR được chấp thuận** là một trạng thái quy trình. Hai việc không chứng minh cho nhau.

Ví dụ lập trình viên merge vào nhánh cá nhân vẫn có thể chưa có quyền đưa thay đổi vào nhánh main được bảo vệ. Ngược lại reviewer Approve MR cũng không tự làm mã xuất hiện ở main. Ở đây chỉ giới thiệu commit/branch đủ để đọc source/target; lệnh Git chi tiết thuộc module Git, còn chính sách dài hạn của nhánh thuộc Branching Strategy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gitlab-project-repository">Project GitLab và Git repository: phạm vi cộng tác và lịch sử nguồn</a>

<details>
<summary>Xem chi tiết</summary>

Project GitLab là phạm vi quản lý gồm một Git repository và các tính năng cộng tác được bật như MR, Issues và thành viên. Git repository biểu diễn tệp theo nhánh và lịch sử commit; **project không đồng nghĩa chỉ là một thư mục chứa mã**. Một Issue của project có thể mô tả công việc trước khi commit nào tồn tại, và một MR có thể đang chờ merge dù file đã hiện ở nhánh nguồn.

Ví dụ DiscountPolicy chỉ có trên nhánh fix-rounding: mở Code trên main chưa thấy thay đổi nhưng MR lại có diff. Hãy ghi cùng bằng chứng tên project, nhánh đang xem, commit và trạng thái MR để tránh nhầm đề xuất với mã đã tiếp nhận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gitlab-namespace-basics">Namespace cá nhân, group và subgroup: các cấp tổ chức nguồn</a>

<details>
<summary>Xem chi tiết</summary>

**Namespace** là phần đường dẫn xác định chủ sở hữu project. Project trong namespace cá nhân do tài khoản sở hữu, project trong **group** thuộc phạm vi nhóm; **subgroup** tổ chức cấp con và có thể nhận chính sách/thành viên từ group cha. Ví dụ `company/payments/gateway` cho thấy gateway thuộc subgroup payments trong group company, không phải ba Git repository lồng nhau.

Các cấp này giúp phân chia trách nhiệm khi dự án lớn dần. Hãy mở trang group/subgroup và xem danh sách project, thành viên, sau đó đối chiếu với đường dẫn project. Không suy rằng mọi người có quyền ở group con sẽ quản trị được toàn bộ group cha.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gitlab-participants">Trách nhiệm của thành viên, người đóng góp, assignee và reviewer</a>

<details>
<summary>Xem chi tiết</summary>

Thành viên được cấp role tại project/group; **contributor** là người có đóng góp nhưng không nhất thiết đang có quyền push; **assignee** thường chịu trách nhiệm xử lý MR/Issue; **reviewer** tập trung đánh giá mã. Một người vừa là tác giả vừa là thành viên project không tự động có quyền phê duyệt MR của chính mình khi chính sách hạn chế self-approval.

Tình huống: An nhận Issue #42, Bình được mời reviewer MR !57, Chi là Maintainer quản lý protected branch. Hãy phân biệt họ có thể **xem**, **đề xuất**, **đánh giá** hay **merge** thay vì gọi chung là 'người có quyền'. Sang chương quyền thành viên, mỗi trách nhiệm được gắn với role cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gitlab-mr-introduction">Merge request: đề xuất thay đổi để thảo luận và đánh giá trước khi hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

**Merge request (MR)** là đề xuất tích hợp thay đổi từ nhánh/project nguồn vào nhánh/project đích. MR lưu mô tả, diff, thảo luận, reviewer, tình trạng approvals và trạng thái hợp nhất. Nó giúp review **trước** khi mã vào nhánh chung; mở MR không tự động chấp nhận thay đổi.

Ví dụ tác giả mở MR !57 từ `fix-rounding` sang `main`, đính Issue #42 và kịch bản đầu vào -1. Reviewer có thể yêu cầu thêm trường hợp biên. Đọc MR trên UI và phân biệt Draft/Open/Merged/Closed; nhất là **Approve không đồng nghĩa Merge**. Khi source và target thuộc hai project khác nhau, hãy kiểm tra cả project chứ không chỉ tên nhánh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="gitlab-workflow-concepts">Quan hệ project, nhánh nguồn/đích, merge request và reviewer</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình chung gồm: group tổ chức project và quyền; repository/nhánh nguồn chứa đề xuất; project/nhánh đích định nơi sẽ tiếp nhận; MR nối hai bên; reviewer đánh giá; protected branch và approval rules quyết định điều kiện merge. Issue có thể là lý do mở MR và Release là hồ sơ công bố sau khi hợp nhất.

Hãy tự vẽ tình huống Issue #42 → nhánh fix-rounding → MR !57 → feedback → approval → merge → Release v1.4. Mỗi mũi tên cần dấu vết: liên kết Issue, diff, review, merge status, tag/notes. Vẽ rõ điểm giao sang Git mechanics và CI/CD mà không thực hiện pipeline trong module này.

</details>

- [Quay lại đầu trang](#back-to-top)
