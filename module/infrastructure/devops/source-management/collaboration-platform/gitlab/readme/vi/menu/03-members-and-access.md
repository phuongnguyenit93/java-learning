<a id="back-to-top"></a>

# Thành viên, vai trò và quyền truy cập

## Menu
- [Các vai trò Guest, Planner, Reporter, Developer, Maintainer và Owner](#access-roles-overview)
- [Quyền từ thành viên trực tiếp, kế thừa và group được chia sẻ](#direct-inherited-shared-membership)
- [Quyền hiệu lực của project và phạm vi quản trị group](#effective-project-permissions)
- [Quyền xem mã nguồn, tạo đề xuất, đánh giá và hợp nhất](#access-for-mr-contribution)
- [Thiếu quyền và quyền vượt nhu cầu khi truy cập project](#permission-troubleshooting)

## <a id="access-roles-overview">Các vai trò Guest, Planner, Reporter, Developer, Maintainer và Owner</a>

<details>
<summary>Xem chi tiết</summary>

GitLab có các vai trò **Guest, Planner, Reporter, Developer, Maintainer, Owner** với quyền tăng theo trách nhiệm nhưng không đơn giản mọi role cao hơn đều phù hợp mọi thao tác. Guest dùng cho nhu cầu theo dõi hạn chế, thường không được xem mã ở private project; Reporter có khả năng đọc repository; Developer có thể đóng góp mã theo rule; Maintainer quản lý project nhiều hơn; Owner thường gắn với quyền quản trị ở group/project theo ngữ cảnh. Planner phục vụ công việc lập kế hoạch khi được hỗ trợ.

Ví dụ người ghi nhận bug không cần Maintainer, người sửa mã có thể cần Developer, còn quản lý membership cần quyền cao hơn. Hãy tra bảng Roles and permissions theo đúng tính năng và tier/phiên bản trước khi trao quyền; không dùng vai trò như nhãn chức danh công ty.

### Tài liệu tham khảo
- [GitLab Docs — Roles and permissions](https://docs.gitlab.com/user/permissions/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="direct-inherited-shared-membership">Quyền từ thành viên trực tiếp, kế thừa và group được chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

Một người có thể được mời **trực tiếp** vào project, nhận quyền **kế thừa** từ group cha, hoặc đến từ **group được chia sẻ** với project. Ba nguồn này có vòng đời và chủ thể quản trị khác nhau. Xóa membership trực tiếp không nhất thiết làm người đó mất quyền nếu vẫn được kế thừa hoặc có shared-group access.

Tình huống Bình vẫn đọc được gateway dù bị xóa khỏi danh sách mời trực tiếp: hãy xem Members và cột nguồn quyền, sau đó kiểm tra group cha và lời mời chia sẻ. Không được tự ý thay đổi group cha nếu vấn đề chỉ thuộc một project nhỏ; chỉnh ở đúng phạm vi cấp quyền.

### Tài liệu tham khảo
- [GitLab Docs — Members of a project](https://docs.gitlab.com/user/project/members/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="effective-project-permissions">Quyền hiệu lực của project và phạm vi quản trị group</a>

<details>
<summary>Xem chi tiết</summary>

**Quyền hiệu lực** là khả năng GitLab thực sự cho phép sau khi xét role, nguồn membership, phạm vi project/group và cấu hình liên quan. Người có tên trong group chưa chắc quản trị được project nếu quyền thực tế chỉ phù hợp đọc hoặc Issue. Protected branch tiếp tục đặt giới hạn riêng cho nhánh dù role Developer thường có thể đóng góp.

Ví dụ An là Developer qua group cha nhưng không push được main được bảo vệ: đây có thể là bảo vệ nhánh chứ không phải sai membership. Khi chẩn đoán, kiểm tra người dùng, project, nguồn role và hành động cụ thể trước; sau đó mới xem branch rule. Quyền xem MR cũng khác quyền sửa settings của project.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-for-mr-contribution">Quyền xem mã nguồn, tạo đề xuất, đánh giá và hợp nhất</a>

<details>
<summary>Xem chi tiết</summary>

Đọc repository, tạo nhánh nguồn, mở MR, được chỉ định reviewer, phê duyệt và merge là những hành động với điều kiện khác nhau. Người ngoài có quyền xem project public có thể dùng fork để đề xuất mà không cần quyền push vào upstream. Ngược lại Maintainer có thể merge vào nhánh được bảo vệ nhưng vẫn phải đáp ứng approval rules.

Ví dụ bên kiểm toán cần review mà không cần quyền chỉnh mã; cần kiểm tra eligibility và cấu hình approvals theo gói. Hãy viết ma trận gồm Read source, Create MR, Approve và Merge main rồi tìm quyền tối thiểu của từng người. Đừng giải quyết MR thiếu approvals bằng cách cấp Owner cho tác giả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="permission-troubleshooting">Thiếu quyền và quyền vượt nhu cầu khi truy cập project</a>

<details>
<summary>Xem chi tiết</summary>

Thiếu quyền thường xuất hiện dưới dạng project không hiển thị, không clone được, không tạo MR hoặc không merge được; chúng không cùng một nguyên nhân. **Cấp quyền tối thiểu** chỉ cấp đủ cho công việc, tránh quyền quá rộng gây rủi ro chỉnh settings hay bypass kiểm soát. Cần phân biệt lỗi membership với target-branch protection hoặc checks chưa đạt.

Tình huống: contractor chỉ cần xử lý Issues nhưng được cấp Maintainer để 'tiện': hãy giảm theo chức năng thực tế. Quy trình đúng là ghi hành động bị từ chối, tìm role và nguồn cấp quyền, xem visibility, cuối cùng đối chiếu rule đích. Có thể dùng fork hoặc đề nghị reviewer phù hợp thay vì mở rộng quyền.

</details>

- [Quay lại đầu trang](#back-to-top)
