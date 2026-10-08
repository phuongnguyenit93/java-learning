<a id="back-to-top"></a>

# Quyền truy cập repository và thành viên tổ chức

## Menu
- [Organization, team và outside collaborator trên GitHub](#orgs-teams-and-outside-collaborators)
- [Các vai trò Read, Triage, Write, Maintain và Admin](#repo-roles)
- [Quyền đóng góp, yêu cầu review và quản trị repository](#review-and-maintenance-permissions)
- [Thiếu quyền, quyền dư thừa và trách nhiệm chưa rõ](#least-privilege-pitfalls)

## <a id="orgs-teams-and-outside-collaborators">Organization, team và outside collaborator trên GitHub</a>

<details>
<summary>Xem chi tiết</summary>

Organization tập hợp thành viên và teams để quản lý nhiều repository. Team có thể được cấp quyền với repository theo nhóm, giảm việc mời từng người; **outside collaborator** có quyền tới một số repository nhưng không mặc nhiên là thành viên organization. Quyền cấp ở nhiều cấp cần được kiểm tra theo người cụ thể, không suy từ tên team.

Ví dụ nhóm payments có quyền Write trên repository thanh toán, còn bên kiểm toán chỉ cần Read; chuyên gia bên ngoài có thể được mời vào một repository với quyền thích hợp. Khi rà soát quyền, tìm thành viên organization, thành viên team, mức truy cập repository và chính sách giới hạn từ cấp trên.

### Tài liệu tham khảo
- [GitHub Docs — About teams](https://docs.github.com/en/organizations/organizing-members-into-teams/about-teams)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repo-roles">Các vai trò Read, Triage, Write, Maintain và Admin</a>

<details>
<summary>Xem chi tiết</summary>

Các vai trò repository tổ chức tăng dần theo trách nhiệm: **Read** xem mã/thảo luận; **Triage** quản lý Issues/PR ở mức phù hợp mà không cần push; **Write** đóng góp mã; **Maintain** quản lý phần lớn hoạt động repository nhưng không có đầy đủ thao tác nhạy cảm; **Admin** quản lý quyền và các hành động nhạy cảm. Tính năng riêng và custom role có thể thay đổi theo gói, nên cần kiểm tra bảng quyền chính thức.

Ví dụ người phân loại bug cần Triage thay vì Write; người bảo trì mã cần Write nhưng không mặc nhiên cần Admin. Thử lập ma trận tác vụ: đọc nguồn, giao Issue, push nhánh, điều chỉnh quyền, xóa repository. Chọn vai trò đáp ứng tác vụ nhưng không mở rộng hơn.

### Tài liệu tham khảo
- [GitHub Docs — Repository roles](https://docs.github.com/en/organizations/managing-user-access-to-your-organizations-repositories/managing-repository-roles/repository-roles-for-an-organization)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="review-and-maintenance-permissions">Quyền đóng góp, yêu cầu review và quản trị repository</a>

<details>
<summary>Xem chi tiết</summary>

Quyền truy cập quyết định ai được xem, tạo nhánh, gửi thay đổi hoặc điều chỉnh Settings; **review request** là lời mời đánh giá một PR, còn **required approval** là điều kiện để merge. Không đồng nhất hai khái niệm này với quyền sở hữu repository. Chính sách tổ chức hoặc quyền chuyên biệt cũng có thể giới hạn thao tác dù người dùng nhìn thấy nút giao diện.

Tình huống nhân viên có quyền Write nhưng không sửa được branch rules: đây là ranh giới quản trị có chủ đích. Nếu PR còn thiếu reviewer phù hợp, yêu cầu đúng team thay vì trao Admin cho tác giả. Khi kiểm chứng, so sánh hành động mong muốn, role thực tế, thông báo lỗi và rules đang bật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="least-privilege-pitfalls">Thiếu quyền, quyền dư thừa và trách nhiệm chưa rõ</a>

<details>
<summary>Xem chi tiết</summary>

**Cấp quyền tối thiểu** là trao đủ quyền cho công việc hiện tại, không cấp quyền rộng để né chướng ngại. Thiếu quyền biểu hiện như không mở được repository, không push được hoặc không chỉnh Settings; quyền dư thừa làm tăng khả năng thay đổi nhầm, xóa repository hoặc bỏ qua quy tắc. Trách nhiệm mơ hồ khiến PR không ai nhận review dù có nhiều người được truy cập.

Ví dụ người hỗ trợ kiểm tra lỗi chỉ cần Triage, nhưng được cấp Admin để 'đỡ vướng': đây là lựa chọn khó kiểm toán. Quy trình tốt: xác định tác vụ, kiểm tra role hiện tại, chọn quyền tối thiểu, ghi nhận chủ thể/phạm vi/thời hạn và xem lại khi dự án đổi người. Nếu vấn đề do ruleset, quyền Admin cũng không phải phương án sửa lỗi mặc định.

</details>

- [Quay lại đầu trang](#back-to-top)
