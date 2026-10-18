<a id="back-to-top"></a>

# Tổ chức và chia sẻ repository trên GitHub

## Menu
- [Repository cá nhân và repository thuộc organization](#personal-vs-organization-repos)
- [Public, private và internal: repository internal của GitHub Enterprise Cloud hiển thị cho thành viên toàn enterprise](#repository-visibility-and-plans)
- [Thông tin repository, thiết lập chia sẻ và nhánh mặc định](#repository-settings-and-default-branch)
- [Phạm vi tìm thấy, xem và đóng góp theo quyền và chế độ hiển thị](#repository-access-and-discovery)

## <a id="personal-vs-organization-repos">Repository cá nhân và repository thuộc organization</a>

<details>
<summary>Xem chi tiết</summary>

Repository cá nhân hợp với bản thử nghiệm do một người kiểm soát; repository của organization phù hợp khi nhóm cần team, quyền thành viên và chính sách chung. Việc chuyển mã từ namespace cá nhân sang tổ chức ảnh hưởng người sở hữu, quyền, URL và các tích hợp liên quan; vì vậy nên xác định owner trước khi mời người khác cộng tác.

Ví dụ ứng dụng thanh toán của công ty không nên phụ thuộc tài khoản riêng của một nhân viên. Khi tạo repository, chọn đúng owner, kiểm tra mức visibility và người quản trị dự phòng. Trên giao diện Settings, phân biệt người có quyền quản lý repository với người chỉ cần đóng góp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-visibility-and-plans">Public, private và internal: repository internal của GitHub Enterprise Cloud hiển thị cho thành viên toàn enterprise</a>

<details>
<summary>Xem chi tiết</summary>

**Public** cho mọi người trên Internet xem nội dung; **private** giới hạn người đã có quyền phù hợp; **internal** trên GitHub Enterprise Cloud mở quyền đọc cho thành viên enterprise, kể cả người không thuộc organization sở hữu. Internal không đơn giản có nghĩa 'chỉ các thành viên organization'. Các lựa chọn khả dụng còn tùy mô hình tài khoản, gói và chính sách enterprise.

Ví dụ mã sản phẩm nội bộ cho nhiều phòng ban có thể hợp với internal, nhưng tài liệu bí mật chỉ dành cho một nhóm nên cân nhắc private. Trước khi đổi visibility, kiểm tra ảnh hưởng đến người đọc và forks, không chỉ nút chọn. Với thông tin nhạy cảm, visibility là một lớp kiểm soát, không thay thế quản lý bí mật.

### Tài liệu tham khảo
- [GitHub Docs — About repositories](https://docs.github.com/en/enterprise-cloud@latest/repositories/creating-and-managing-repositories/about-repositories)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-settings-and-default-branch">Thông tin repository, thiết lập chia sẻ và nhánh mặc định</a>

<details>
<summary>Xem chi tiết</summary>

Tên, mô tả, chủ sở hữu, visibility, nhánh mặc định và cấu hình cộng tác là các thiết lập ở cấp repository. **Default branch** là nhánh GitHub đề xuất làm đích cho PR mới và ngữ cảnh mặc định khi duyệt Code; nó không tự động chỉ ra nhánh đã triển khai hay nhánh duy nhất được phép đóng góp.

Tình huống nhóm đổi mặc định từ master sang main: PR tạo sau đó có thể chọn main, nhưng các PR đang mở và hệ thống liên quan cần được kiểm tra theo hành vi GitHub thực tế. Khi thao tác Settings, nên ghi lại trạng thái trước/sau và kiểm tra quyền người thực hiện; một số cài đặt bị policy cấp tổ chức/enterprise giới hạn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="repository-access-and-discovery">Phạm vi tìm thấy, xem và đóng góp theo quyền và chế độ hiển thị</a>

<details>
<summary>Xem chi tiết</summary>

Khả năng **tìm thấy repository**, **đọc nội dung** và **đề xuất thay đổi** không giống nhau. Người ngoài có thể xem public repository và tạo fork/PR nếu quy tắc cho phép, nhưng không vì thế có quyền push trực tiếp. Người có quyền đọc private repository cũng chưa chắc có quyền chỉnh sửa Settings. Internal giới hạn người xem theo enterprise chứ không theo tên một nhóm cụ thể.

Ví dụ cộng tác viên nói 'không thấy repo': kiểm tra URL và visibility trước; nếu thấy nhưng không thể chỉnh nhánh gốc, kiểm tra role và đường đóng góp. Hãy phân tích riêng lỗi 404/không có quyền đọc, không thấy nút Settings và PR bị từ chối, vì chúng thuộc các tầng khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)
