<a id="back-to-top"></a>

# Mã nguồn cục bộ và nguồn dùng chung

## Menu
- [Tệp đang chỉnh sửa và lịch sử có phiên bản](#working-files-vs-versioned-history)
- [Kho cục bộ và kho từ xa: Vai trò và ranh giới](#local-and-remote-repositories)
- [Đồng bộ và lịch sử khác nhau giữa các bản sao](#synchronization-and-divergence)
- [Lịch sử nguồn chung được chấp nhận: Khái niệm và tiêu chí](#accepted-source-of-truth)

## <a id="working-files-vs-versioned-history">Tệp đang chỉnh sửa và lịch sử có phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

Trong ngày làm việc, nội dung `Invoice.java` mà An nhìn thấy là **tệp đang chỉnh sửa** (working file). An có thể lưu tệp nhiều lần mà chưa tạo một mốc lịch sử phiên bản. Trạng thái đã ghi nhận là điểm có thể tra lại, còn tệp đang mở có thể thay đổi liên tục.

Mô hình này giúp lý giải vì sao một lỗi mới trên máy An chưa chắc có trong lịch sử chung. Nếu vô tình sửa file rồi đóng trình soạn thảo, chỉ có bản đã ghi nhận trước đó có thể còn để đối chiếu; các thay đổi chưa được ghi nhận cần biện pháp bảo vệ riêng. Trong Git còn có vùng chuẩn bị ghi nhận (index), nhưng cơ chế này thuộc module Git, không phải yêu cầu của người mới tại đây.

**Tự kiểm tra:** An sửa 10 dòng nhưng chỉ lưu tệp; Bình xem kho chung vẫn thấy công thức cũ. Hãy giải thích vì sao cả hai quan sát đều đúng và cần hành động nào trước khi người khác có thể thấy phần sửa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="local-and-remote-repositories">Kho cục bộ và kho từ xa: Vai trò và ranh giới</a>

<details>
<summary>Xem chi tiết</summary>

**Kho cục bộ (local repository)** nằm trong môi trường của người làm việc và giúp lưu lịch sử riêng. **Kho từ xa (remote repository)** là kho khác được chỉ định làm nơi trao đổi các trạng thái phiên bản qua mạng; “remote” là quan hệ giữa các kho, không nhất thiết là một dịch vụ công cộng.

Khi nhóm dùng Git, An có thể ghi nhận lịch sử trên máy rồi chủ động gửi thay đổi tới kho từ xa; Bình có thể chủ động nhận lịch sử từ đó. Việc gửi/nhận và tên các lệnh Git là phần thực hành chuyên sâu của module Git. Đừng nhầm thư mục làm việc với kho cục bộ hoặc nhầm nơi lưu từ xa với quyền phê duyệt thay đổi.

**Mô hình giấy:** vẽ ba ô `An local`, `shared remote`, `Binh local`. Vẽ mũi tên hai chiều biểu diễn trao đổi có chủ đích, không vẽ mũi tên “tự động”. **Quan sát:** chỉ kho từ xa thay đổi chưa bảo đảm bản làm việc của Bình đã được cập nhật.

### Tài liệu tham khảo
- [Pro Git — Working with Remotes](https://git-scm.com/book/en/v2/Git-Basics-Working-with-Remotes)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-and-divergence">Đồng bộ và lịch sử khác nhau giữa các bản sao</a>

<details>
<summary>Xem chi tiết</summary>

**Đồng bộ** là quá trình trao đổi và điều chỉnh trạng thái giữa các bản sao theo quy tắc của công cụ và nhóm. Nó không xảy ra chỉ vì hai repository cùng có tên dự án. **Phân kỳ** xuất hiện khi An và Bình tiếp tục ghi nhận thay đổi khác nhau từ một trạng thái chung ban đầu.

Ví dụ mốc A chứa thuế 8%. An có thay đổi B (thuế 10%), Bình có thay đổi C (làm tròn). B và C đều bắt đầu từ A nên không thể giả định B đã bao gồm C. Khi chia sẻ, nhóm cần đối chiếu và kết hợp các thay đổi; nếu chúng sửa cùng vùng tệp, có thể phải xử lý xung đột. Việc nhận lịch sử mới cũng không bằng việc chấp nhận nó cho nhánh chung.

**Bài tập:** vẽ sơ đồ `A → B` và `A → C`, sau đó đánh dấu một kết quả D cần chứa cả điều chỉnh thuế và làm tròn. Mô tả điều kiện để D có thể trở thành trạng thái chung hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="accepted-source-of-truth">Lịch sử nguồn chung được chấp nhận: Khái niệm và tiêu chí</a>

<details>
<summary>Xem chi tiết</summary>

Trong mô hình phân tán, bản sao lịch sử có thể ngang nhau về khả năng kỹ thuật nhưng **không ngang nhau về quyền đại diện cho dự án**. Nhóm cần chỉ định repository và trạng thái/nhánh được dùng làm **nguồn sự thật chung** cho một mục đích nhất định, cùng người có thẩm quyền quyết định tiếp nhận.

“Được chấp nhận” không có nghĩa mọi thay đổi trong kho chung đều đúng tuyệt đối, cũng không nhất thiết là đã triển khai cho người dùng. Nó chỉ có nghĩa thay đổi đã đi qua quy tắc tiếp nhận hiện hành. Nếu dự án hỗ trợ nhiều phiên bản sản phẩm, có thể có nhiều dòng bảo trì được công nhận tùy chính sách; chương này không đặt chiến lược nhánh cụ thể.

**Bằng chứng:** nhóm xác nhận trạng thái D chứa cả sửa thuế lẫn làm tròn và đã đạt review cần thiết, trong khi B và C chỉ là đề xuất cá nhân. **Thực hành:** viết một câu chính sách xác định nơi xem nguồn được chấp nhận và cách xử lý khi hai bản sao khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)
