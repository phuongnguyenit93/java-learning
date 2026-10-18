<a id="back-to-top"></a>

# Protected branches và điều kiện cho phép merge

## Menu
- [Protected branch: phạm vi bảo vệ nhánh quan trọng](#protected-branches-purpose)
- [Quyền Allowed to merge so với Allowed to push and merge](#allowed-to-merge-vs-push)
- [Branch rules cấp project và bảo vệ group cấp cao: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)](#project-group-branch-rules)
- [Khi nhiều rules khớp nhánh: quyền rộng nhất và ngoại lệ Code Owner chặt nhất](#overlapping-rule-precedence)
- [Điều kiện bắt buộc Code Owner phê duyệt trên protected target branch](#code-owner-approval-protected-target)
- [Approval/check trạng thái có thể chặn merge; pipeline chỉ là điểm tích hợp](#merge-checks-and-pipeline-boundary)
- [Các nguyên nhân MR bị chặn: quyền push/merge, approval và checks](#blocked-merge-diagnosis)

## <a id="protected-branches-purpose">Protected branch: phạm vi bảo vệ nhánh quan trọng</a>

<details>
<summary>Xem chi tiết</summary>

**Protected branch** là nhánh GitLab áp dụng quyền cập nhật/hợp nhất chặt hơn nhánh thông thường, thường dành cho main, stable hoặc release. Mục đích là ngăn sửa trực tiếp thiếu kiểm tra và duy trì đường đưa thay đổi được nhóm giám sát. Protection không tự sửa mã sai: nó giới hạn ai có thể hành động và điều kiện nào cần đủ.

Ví dụ An là Developer nhưng không push trực tiếp main: nhóm yêu cầu MR tới nhánh này. Hãy mở Settings → Repository → Protected branches hoặc Branch rules theo phiên bản, xem pattern khớp và quyền thực tế, không diễn giải lỗi bị chặn là repository hỏng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="allowed-to-merge-vs-push">Quyền Allowed to merge so với Allowed to push and merge</a>

<details>
<summary>Xem chi tiết</summary>

**Allowed to merge** kiểm soát ai có thể đưa MR vào nhánh bảo vệ; **Allowed to push and merge** kiểm soát ai có thể push trực tiếp và có thể hợp nhất theo phạm vi quyền đó. Quyền merge qua MR không có nghĩa được sửa main trực tiếp, và quyền được push trực tiếp rộng có thể làm suy yếu yêu cầu review nếu cấu hình sơ suất.

Ví dụ main đặt Allowed to merge cho Maintainers nhưng Allowed to push and merge là No one: Maintainer có thể merge MR đáp ứng điều kiện mà không được push trực tiếp. Đối chiếu hai trường trên UI, thử dự đoán kết quả cho Developer và Maintainer rồi kiểm chứng bằng thông báo GitLab; không thay quyền chỉ để một MR đi qua.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="project-group-branch-rules">Branch rules cấp project và bảo vệ group cấp cao: GitLab.com, Self-Managed, Dedicated (Premium/Ultimate, 17.6+)</a>

<details>
<summary>Xem chi tiết</summary>

**Project branch rules** áp dụng trong một project. **Group-level protected branches** được GitLab hỗ trợ ở **Premium/Ultimate**, trên cả **GitLab.com, GitLab Self-Managed và GitLab Dedicated**; tính năng đã **Generally Available từ 17.6**. Chỉ **Owner của top-level group** có thể tạo group protected branch; **không hỗ trợ cấu hình ở subgroup**. Quy tắc group áp dụng cho các project trong group và **không thể sửa trực tiếp từ settings của project**. Vì vậy, một project Maintainer không thể chỉnh chính group rule kế thừa, dù người đó vẫn có thể cấu hình một project rule riêng cho cùng branch; nhiều rule khớp sẽ được đánh giá theo quy tắc kết hợp ở phần sau.

**Phân biệt tính năng UI và REST API:** hướng dẫn **Protected branches → In a group** xác nhận đủ ba offering ở trên. Riêng tài liệu **Group-level protected branches REST API** hiện ghi **Self-Managed only**; hạn chế của endpoint API đó **không phải** hạn chế của tính năng group protected branches qua giao diện.

Ví dụ Owner của top-level group `company` muốn bảo vệ `main` nhất quán cho project `payments/gateway` và `payments/billing`. Trước khi quay, xác nhận tier Premium/Ultimate, môi trường, quyền Owner và màn hình **Group → Settings → Repository → Protected branches**; không vào subgroup `payments` để giả lập một rule được cấu hình trực tiếp tại subgroup. Nếu Maintainer project không chỉnh được rule kế thừa, hãy truy về top-level group. Chỉ mô tả kết quả UI thật khi có quyền và môi trường hỗ trợ; nếu không hãy dùng sơ đồ minh họa có ghi rõ điều kiện.

### Tài liệu tham khảo
- [GitLab Docs — Protected branches, In a group (UI; three offerings)](https://docs.gitlab.com/user/project/repository/branches/protected/)
- [GitLab Docs — Group-level protected branches REST API (Self-Managed only)](https://docs.gitlab.com/api/group_protected_branches/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overlapping-rule-precedence">Khi nhiều rules khớp nhánh: quyền rộng nhất và ngoại lệ Code Owner chặt nhất</a>

<details>
<summary>Xem chi tiết</summary>

Khi một nhánh khớp nhiều protection rules, GitLab thường dùng thiết lập **cho phép rộng nhất** cho quyền push, merge và force push; riêng yêu cầu **Code Owner approval** lấy phía **chặt nhất**. Rule tên chính xác không mặc nhiên thắng wildcard đối với tất cả quyền. Đây là điểm khác mô hình branch protection của GitHub.

Ví dụ main khớp `main` (push No one) và `m*` (Developer được push): nhánh có thể bị mở rộng quyền theo rule thứ hai. Nếu bất kỳ rule khớp nào yêu cầu Code Owner, yêu cầu đó vẫn có thể bắt buộc. Để bảo vệ chặt, người quản trị phải kiểm tra **mọi pattern** khớp và tránh rule wildcard vô tình nới lỏng.

### Tài liệu tham khảo
- [GitLab Docs — Protection rules and permissions](https://docs.gitlab.com/user/project/repository/branches/protection_rules/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="code-owner-approval-protected-target">Điều kiện bắt buộc Code Owner phê duyệt trên protected target branch</a>

<details>
<summary>Xem chi tiết</summary>

Đối với target branch được bảo vệ và có bật yêu cầu phù hợp, **Code Owner approval** buộc người sở hữu vùng mã được thay đổi phải tham gia quyết định trước merge. Cơ chế này phụ thuộc GitLab Premium/Ultimate; chỉ tạo file CODEOWNERS không tự bảo đảm có blocking gate trong mọi gói. Người được ghi là owner còn phải đủ điều kiện role/membership để approval được tính.

Ví dụ MR !57 sửa `pricing/`; role của Bình có thể Approve thông thường nhưng chưa thỏa Code Owner requirement của nhóm payments. Kiểm tra pattern ở CODEOWNERS, nhánh đích, rule bật owner approval và widget approvals; đừng tăng quyền tác giả để lách ownership.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="merge-checks-and-pipeline-boundary">Approval/check trạng thái có thể chặn merge; pipeline chỉ là điểm tích hợp</a>

<details>
<summary>Xem chi tiết</summary>

GitLab có thể dùng approvals, unresolved discussions, checks hoặc trạng thái pipeline để quyết định MR có được merge. **Pipeline** là chuỗi công việc tự động như build/test, được tạo và chạy bởi hệ CI; ở đây chỉ cần đọc trạng thái được GitLab trình bày, không viết `.gitlab-ci.yml` hay học execution graph.

Ví dụ MR được Approve nhưng widget báo pipeline failed: hãy xác định trạng thái có phải merge gate được bật không, kết quả thuộc commit nào và ai chịu trách nhiệm xử lý. Một pipeline pass không thay thế review nghiệp vụ hay quyền merge; ngược lại thiếu pipeline trên một project không có cấu hình CI không tự là lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="blocked-merge-diagnosis">Các nguyên nhân MR bị chặn: quyền push/merge, approval và checks</a>

<details>
<summary>Xem chi tiết</summary>

MR bị chặn có thể do role, protected target branch, approval rule thiếu người hợp lệ, Request changes đang còn hiệu lực, unresolved discussion hoặc check/pipeline failed. Chẩn đoán đúng phải dựa vào **thông báo cụ thể trên MR** và rule có hiệu lực, không dựa vào việc nút Merge xám. Các giới hạn tier cũng quyết định nguyên nhân nào có thể thực sự được bật.

**Thứ tự:** xác nhận source/target và Ready state; kiểm tra actor có quyền merge; xem approvals/Code Owner; xem discussions, checks; cuối cùng xem tất cả protection rules khớp. Ghi blocker chính xác cho !57 trước khi sửa quyền. Không force push vào main hoặc tắt rule chỉ để kịp deadline.

</details>

- [Quay lại đầu trang](#back-to-top)
