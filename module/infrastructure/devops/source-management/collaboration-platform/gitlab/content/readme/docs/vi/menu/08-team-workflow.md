<a id="back-to-top"></a>

# Tổng hợp luồng cộng tác nhóm với GitLab

## Menu
- [Từ Issue tới đóng góp, review, phê duyệt và merge](#issue-to-mr-approval-merge)
- [Trở ngại do vai trò thành viên, quyền truy cập và reviewer](#roles-access-review-blockers)
- [Trở ngại do protected branch, approval rules và trạng thái kiểm tra](#protected-branch-approval-blockers)
- [Liên hệ thay đổi đã merge với thông tin Release](#merge-to-release-metadata)
- [Bàn giao sang Git mechanics, branching strategy, GitLab CI/CD và bảo mật chuyên sâu](#handoff-to-git-strategy-ci)

## <a id="issue-to-mr-approval-merge">Từ Issue tới đóng góp, review, phê duyệt và merge</a>

<details>
<summary>Xem chi tiết</summary>

Kết hợp toàn bộ hành trình: Issue #42 xác định lỗi giá âm; An dùng nhánh trong project hoặc fork tạo MR !57 nhắm `main`; Bình review diffs và yêu cầu trường hợp biên; An sửa; reviewer đủ điều kiện Approve; Maintainer chỉ merge khi protected branch và các điều kiện đang bật cho phép. Sau merge, nhóm xác minh trạng thái Issue rồi mới cân nhắc Release.

**Bài thực hành bằng giao diện:** ghi Issue URL, source/target project/branch, diff, người review, approval rule, merge status và liên kết Release. Nếu thiếu bất cứ bằng chứng nào, diễn đạt kết quả ở mức đã quan sát được thay vì khẳng định toàn bộ quy trình hoàn tất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="roles-access-review-blockers">Trở ngại do vai trò thành viên, quyền truy cập và reviewer</a>

<details>
<summary>Xem chi tiết</summary>

MR dừng tiến trình có thể do người dùng không thấy project, không push được branch, không được phép merge hoặc thiếu reviewer đủ điều kiện. Các tình trạng này khác nhau: visibility/membership điều khiển đọc; project/group roles điều khiển thao tác; approval eligibility quyết định phiếu có được tính hay không. Request review cho một thành viên chưa đủ quyền không tự giải quyết requirement.

**Tình huống:** contractor không thấy gateway private, trong khi Bình xem được nhưng không được tính vào approval rule. Kiểm tra từng người, nguồn membership, vai trò, target project và approval widget. Không sửa bằng cách cấp Owner cho cả hai; đưa ra thay đổi nhỏ nhất đúng phạm vi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="protected-branch-approval-blockers">Trở ngại do protected branch, approval rules và trạng thái kiểm tra</a>

<details>
<summary>Xem chi tiết</summary>

Một MR đã có review tích cực vẫn có thể blocked vì protected target branch, số approval thiếu, Code Owner chưa duyệt, Request changes còn hiệu lực hoặc pipeline/check chưa đạt. Khi nhiều branch rules khớp, nhớ cách GitLab tính **quyền rộng nhất**, riêng Code Owner là **yêu cầu chặt nhất**. Chế độ Premium/Ultimate hoặc Self-Managed có thể quyết định một blocker có thật sự tồn tại hay không.

**Thứ tự chẩn đoán:** kiểm tra target/Ready, actor merge permission, approvals, Code Owners, unresolved discussions, check status rồi mọi branch protection pattern áp dụng. Lưu lại tên rule và trạng thái commit hiện tại trước khi đề nghị admin thay đổi cấu hình; không tắt bảo vệ do deadline.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="merge-to-release-metadata">Liên hệ thay đổi đã merge với thông tin Release</a>

<details>
<summary>Xem chi tiết</summary>

MR Merged chứng minh thay đổi đã được tiếp nhận vào target branch; Issue Closed phản ánh trạng thái công việc; GitLab Release công bố thông tin của một tag. Đây là ba sự kiện riêng, không tự chứng minh deployment. Liên kết chúng tạo dấu vết truy vết từ yêu cầu ban đầu tới mã đã nhận và thông tin công bố, nhưng phải đọc trạng thái thực tế.

**Bài kiểm chứng:** tìm MR !57 Merged, mở Code của main đối chiếu tệp, kiểm tra Issue #42, sau đó xem Release v1.4.0 gắn tag/notes/asset. Nếu chỉ có merge, ghi 'đã merge, chưa xác nhận Release'; nếu đã công bố Release nhưng chưa có bằng chứng production, ghi 'Release published, deployment unverified'.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="handoff-to-git-strategy-ci">Bàn giao sang Git mechanics, branching strategy, GitLab CI/CD và bảo mật chuyên sâu</a>

<details>
<summary>Xem chi tiết</summary>

Phân chia trách nhiệm cuối: **Git** quản lý commit, nhánh và lịch sử; **GitLab collaboration** quản lý project/group, MR, role và điều kiện hợp nhất; **Branching Strategy** xác định nhịp tích hợp và mô hình nhánh; **GitLab CI/CD** thực hiện kiểm thử/triển khai thông qua pipeline; nội dung bảo mật chuyên sâu nằm ngoài bài cộng tác này. Các phần liên quan nhưng không thể thay thế nhau.

Ví dụ PR đã merge mà production chưa đổi: xác minh merge status ở GitLab rồi bàn giao việc pipeline/deployment cho người phụ trách CI/CD, không chỉnh role/rules tùy tiện. Nếu tranh luận cần release branch dài hạn, đó là quyết định chiến lược nhánh, không phải bắt buộc do GitLab. Bàn giao đúng chủ thể giúp tránh tăng quyền vô ích.

</details>

- [Quay lại đầu trang](#back-to-top)
