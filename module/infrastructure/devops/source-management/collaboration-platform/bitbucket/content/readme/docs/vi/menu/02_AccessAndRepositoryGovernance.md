<a id="back-to-top"></a>

# Phân quyền và quản trị kho mã

## Menu
- [Quyền ở workspace, project và repository](#bitbucket-permission-scopes)
- [Vai trò của người dùng, nhóm và quản trị viên](#users-groups-and-administrators)
- [Hai môi trường quản trị workspace: Bitbucket và Atlassian Administration](#workspace-admin-environments)
- [Tác động của quyền project lên repository thành viên](#project-permission-inheritance)
- [Quyền xem, sửa, quản trị và phạm vi hiển thị](#read-write-admin-and-visibility)
- [Thiếu quyền và cấp quyền quá rộng: Dấu hiệu và tác động](#access-governance-failure-modes)

## <a id="bitbucket-permission-scopes">Quyền ở workspace, project và repository</a>

<details>
<summary>Xem chi tiết</summary>

Bitbucket Cloud có nhiều phạm vi quyền vì nhóm không muốn mỗi thay đổi nhỏ trong tổ chức đều phải cấp quyền riêng cho từng repository. **Workspace** là phạm vi tổ chức/thành viên; **project** có thể cấp quyền áp dụng cho nhiều repository; **repository** cho phép cấp quyền và quản trị nội dung của một kho cụ thể. Các phạm vi liên quan nhưng không thể xem là một nút “có quyền mọi thứ”.

Ví dụ Orchid chỉ muốn bộ phận kế toán đọc `invoice-api`, nhóm phát triển được sửa và Mai quản trị project Billing. Cần kiểm tra quyền thành viên/nhóm ở đúng phạm vi, gồm các quyền kế thừa từ project; không suy luận quyền sửa từ việc nhìn thấy tên workspace.

**Thực hành:** vẽ workspace `orchid-team` chứa project Billing với hai repository. Ghi “thành viên mới có thể đọc gì?” cạnh từng cấp và đánh dấu nơi phải kiểm tra quyền thực tế trước khi mời người ngoài.

### Tài liệu tham khảo
- [Atlassian — Configure project permissions](https://support.atlassian.com/bitbucket-cloud/docs/configure-project-permissions-for-users-and-groups/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="users-groups-and-administrators">Vai trò của người dùng, nhóm và quản trị viên</a>

<details>
<summary>Xem chi tiết</summary>

Cấp quyền theo **người dùng** phù hợp cho ngoại lệ nhỏ, còn **nhóm người dùng** giảm công bảo trì khi nhân sự vào/ra dự án. **Quản trị viên** chịu trách nhiệm cấp quyền, duy trì phạm vi và xử lý yêu cầu truy cập. Tên vai trò phụ thuộc phạm vi: người quản trị project không nhất thiết là quản trị viên tổ chức Atlassian.

Trong Orchid, nhóm `billing-developers` có thể được cấp quyền Write cho project Billing thay vì chọn từng người ở hai repository. Khi An rời nhóm, sửa thành viên nhóm giúp thu hồi quyền được cấp qua nhóm; vẫn phải kiểm tra những quyền trực tiếp khác còn tồn tại.

**Minh chứng:** nhìn vào danh sách thành viên và nguồn cấp quyền, nhóm có thể giải thích vì sao An được ghi vào kho. **Bài tập:** so sánh cấp quyền riêng cho năm người với cấp một nhóm rồi thử tình huống một người chuyển bộ phận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="workspace-admin-environments">Hai môi trường quản trị workspace: Bitbucket và Atlassian Administration</a>

<details>
<summary>Xem chi tiết</summary>

Bitbucket Cloud hiện có **hai mô hình quản trị workspace**. Các workspace cũ có thể tiếp tục được quản lý ngay trong Bitbucket; workspace mới được cấp phát qua **Atlassian Administration** và gắn với tổ chức Atlassian. Điều này không làm thay đổi mô hình workspace → project → repository, nhưng thay đổi **nơi quản lý người dùng và quyền ứng dụng**.

Theo tài liệu Atlassian hiện hành, ở workspace do Atlassian Administration quản lý, người quản trị tổ chức/quyền ứng dụng cấp quyền vào workspace; **quyền truy cập nội dung project và repository vẫn được quản lý trong Bitbucket**. Đừng làm theo hướng dẫn “Workspace settings → User groups” của môi trường cũ một cách máy móc; trước hết xác định workspace thuộc mô hình nào. Atlassian hiện chưa hỗ trợ chuyển trực tiếp workspace cũ sang mô hình mới bằng một thao tác thông thường.

**Tình huống:** Mai không thấy nút mời thành viên ở Bitbucket mới. Thay vì đoán tài khoản bị lỗi, hãy xác nhận workspace có được cấp phát qua Atlassian Administration hay không và xác định đúng người quản trị để xin quyền.

### Tài liệu tham khảo
- [Atlassian — What is a workspace?](https://support.atlassian.com/bitbucket-cloud/docs/what-is-a-workspace/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="project-permission-inheritance">Tác động của quyền project lên repository thành viên</a>

<details>
<summary>Xem chi tiết</summary>

Nếu một nhóm có quyền ở **project**, quyền này có thể áp dụng lên các repository hiện có và được tạo sau trong project đó. Atlassian mô tả bốn cấp quyền project **Read, Write, Create, Admin**, với cấp cao bao gồm các quyền thấp hơn. Đây là cách quản lý nhất quán, đồng thời tạo rủi ro cấp quyền rộng ngoài dự kiến.

Ví dụ nếu `billing-developers` có Write ở project Billing, nhóm có quyền ghi đối với cả `invoice-api` và `billing-docs`, kể cả khi chỉ cần sửa API. Quyền cấp ở repository cần được xem **cùng với quyền project kế thừa**, không giả định bỏ quyền riêng ở repository sẽ thu hồi được quyền đã cấp qua project.

**Bài tập:** Mai tạo repository mới `payroll-secrets` trong Billing. Hãy xác định những nhóm project nào đã có quyền và liệu nên đặt kho nhạy cảm vào project khác hoặc sửa chính sách. Sau khi đổi quyền, kiểm tra cả quyền dựa trên nhóm lẫn quyền cấp trực tiếp.

### Tài liệu tham khảo
- [Atlassian — Configure project permissions](https://support.atlassian.com/bitbucket-cloud/docs/configure-project-permissions-for-users-and-groups/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="read-write-admin-and-visibility">Quyền xem, sửa, quản trị và phạm vi hiển thị</a>

<details>
<summary>Xem chi tiết</summary>

**Read** thường cho phép xem và lấy mã; **Write** cho phép đóng góp bằng cách ghi thay đổi; **Admin** cho phép thay đổi cấu hình, thành viên và quyền trong phạm vi được quản trị. Ở project còn có cấp **Create** để tạo repository; không áp dụng máy móc danh sách bốn cấp này cho mọi loại quyền, vì quyền repository được trình bày theo cơ chế riêng.

**Khả năng hiển thị** cũng cần phân biệt với quyền thao tác. Repository *public* có thể cho người khác đọc mà không cho ghi; repository *private* yêu cầu quyền thích hợp. Trong Bitbucket Cloud, project công khai có thể chứa repository riêng tư, còn project riêng tư giới hạn khả năng hiển thị và không nên bị xem là một tập hợp repository mặc nhiên công khai.

**Quan sát:** Bình đọc được phần khác biệt trong PR nhưng không được thay đổi cài đặt project: đó là phân quyền có chủ đích, không phải lỗi. **Tự kiểm tra:** với một người hỗ trợ kiểm toán chỉ cần xem mã, hãy chọn mức quyền tối thiểu và giải thích vì sao không cấp Admin.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-governance-failure-modes">Thiếu quyền và cấp quyền quá rộng: Dấu hiệu và tác động</a>

<details>
<summary>Xem chi tiết</summary>

Cấp quyền sai có hai dạng. **Thiếu quyền:** An không xem được repository hoặc không thể cập nhật nhánh phù hợp. **Quyền dư thừa:** người chỉ cần đọc có thể thay đổi cấu hình hoặc ghi lên nguồn quan trọng. Việc thiếu mô tả ai chịu trách nhiệm quản trị làm nhóm xử lý chậm và dễ nới quyền quá tay.

Cách chẩn đoán an toàn là ghi lại *người nào, thao tác gì, repository/project nào, lỗi hoặc thông báo nào*, sau đó xem quyền ở workspace, project, repository và nhánh. Đừng thử “cấp Admin cho xong”: điều đó che giấu nguồn lỗi và tăng rủi ro. Với workspace quản lý qua Atlassian Administration, phần quyền ứng dụng có thể nằm khác chỗ quyền project.

**Thực hành:** An xem được `invoice-api` nhưng không tạo PR; Mai phải kiểm tra đúng yêu cầu cấp quyền và trạng thái nhánh, không kết luận rằng Read chắc chắn đủ cho mọi hành động. Ghi lại bằng chứng trước/sau khi sửa quyền.

</details>

- [Quay lại đầu trang](#back-to-top)
