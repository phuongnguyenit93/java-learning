<a id="back-to-top"></a>

# Tổ chức project và group trên GitLab

## Menu
- [Sở hữu project: namespace cá nhân so với group](#project-namespace-ownership)
- [Tổ chức group, subgroup và các project thành viên](#groups-and-subgroups)
- [Repository và thiết lập quản trị ở cấp project](#project-repository-settings)
- [Public, private, internal: GitLab.com không tạo internal mới nhưng dự án cũ vẫn giữ chế độ này](#project-visibility-offerings)
- [Ranh giới group/project đối với việc chia sẻ và cộng tác](#projects-group-collaboration-boundary)

## <a id="project-namespace-ownership">Sở hữu project: namespace cá nhân so với group</a>

<details>
<summary>Xem chi tiết</summary>

Project cá nhân phù hợp thử nghiệm độc lập; project thuộc group phù hợp khi mã cần tồn tại lâu hơn một nhân sự và có quyền theo nhóm. Đường dẫn `alice/demo` có owner khác `company/payments/gateway`; quyền ở project thuộc group có thể chịu chính sách group cha. Đổi vị trí project có thể ảnh hưởng URL, thành viên và tích hợp nên phải xem trước khi chuyển.

Ví dụ mã cổng thanh toán không nên phụ thuộc tài khoản cá nhân của An. Khi tạo project mới, chọn namespace tổ chức trước, xác định Maintainer và kế hoạch phân quyền. Kiểm tra breadcrumb và URL để chứng minh ai thực sự sở hữu project.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="groups-and-subgroups">Tổ chức group, subgroup và các project thành viên</a>

<details>
<summary>Xem chi tiết</summary>

Group tạo ranh giới quản trị cho nhiều project, subgroup chia nhỏ theo sản phẩm hay team. Ví dụ `company/payments` chứa gateway và billing; một số quyền ở parent có thể được kế thừa xuống child. Subgroup không phải folder source hay nhánh Git mà là đối tượng quản trị trong GitLab.

Thực hành: trên trang group, duyệt Subgroups và Projects, ghi lại thành viên chung và người quản trị. Thiết kế nhóm không nên dựa duy nhất trên cấu trúc deployment: nhiều service có thể cùng group nhưng riêng project; một project không nhất thiết tương ứng đúng một service.

### Tài liệu tham khảo
- [GitLab Docs — Groups](https://docs.gitlab.com/user/group/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="project-repository-settings">Repository và thiết lập quản trị ở cấp project</a>

<details>
<summary>Xem chi tiết</summary>

Project chứa Git repository và tùy chọn quản trị ở mức GitLab như visibility, members, default branch, merge requests và các tính năng được bật. Default branch là nhánh mặc định khi duyệt và thường là target của đề xuất mới; nó không khẳng định phiên bản hiện đang chạy production.

Tình huống đổi nhánh mặc định từ master sang main: cần xem MR mở, quyền protected branch và các hệ thống phụ thuộc trước/sau thay đổi. Người có quyền xem repository chưa chắc có quyền sửa Settings. Hãy đối chiếu trạng thái Code, Settings và vai trò người thao tác thay vì suy chỉ từ giao diện hiện nút.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="project-visibility-offerings">Public, private, internal: GitLab.com không tạo internal mới nhưng dự án cũ vẫn giữ chế độ này</a>

<details>
<summary>Xem chi tiết</summary>

**Public** cho phép người ngoài truy cập nội dung công khai; **private** cần quyền phù hợp; **internal** trên GitLab Self-Managed/Dedicated cho người dùng đã xác thực của instance (trừ external users) mức truy cập rộng hơn private. Trên **GitLab.com, không thể tạo mới project Internal**; project cũ có Internal vẫn giữ trạng thái legacy. Internal của GitLab không phải cơ chế enterprise-internal của GitHub.

Ví dụ project chứa thông tin độc quyền nên chọn private ngay từ lúc tạo trên GitLab.com. Trước khi sửa visibility, xác định người có thể clone/xem, quyền members và chính sách instance/group; mật khẩu hay token vẫn cần quản lý riêng. Đừng khuyên tìm tùy chọn Internal trên GitLab.com mà không xét giới hạn này.

### Tài liệu tham khảo
- [GitLab Docs — Project and group visibility](https://docs.gitlab.com/user/public_access/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="projects-group-collaboration-boundary">Ranh giới group/project đối với việc chia sẻ và cộng tác</a>

<details>
<summary>Xem chi tiết</summary>

Group quản lý phạm vi sở hữu/chính sách chung, project chứa một repository và MR/Issues riêng. Thành viên có quyền group cha có thể thừa hưởng quyền project; người được mời vào một project không mặc nhiên quản trị mọi project cùng group. Sự khác biệt quyết định ai được đọc mã, mời người và giải quyết MR.

Ví dụ nhà thầu chỉ cần xem gateway, không nên được mời vào toàn bộ group payments. Khi đánh giá quyền, liệt kê group hierarchy, direct members và inherited members trước khi mở rộng truy cập. Đây là lý do chương tiếp theo đi sâu vào vai trò và quyền hiệu lực.

</details>

- [Quay lại đầu trang](#back-to-top)
