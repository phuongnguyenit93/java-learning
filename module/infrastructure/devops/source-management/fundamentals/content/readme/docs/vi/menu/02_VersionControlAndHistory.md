<a id="back-to-top"></a>

# Kiểm soát phiên bản và lịch sử

## Menu
- [Hệ thống kiểm soát phiên bản: Khái niệm và nhiệm vụ](#version-control-purpose)
- [Lưu tệp và sao lưu thư mục so với lịch sử phiên bản có truy vết](#saving-backups-and-version-history)
- [Nội dung, người thực hiện, thời điểm và lý do thay đổi](#change-provenance)
- [So sánh, xem lại và khôi phục phiên bản](#compare-inspect-and-restore)

## <a id="version-control-purpose">Hệ thống kiểm soát phiên bản: Khái niệm và nhiệm vụ</a>

<details>
<summary>Xem chi tiết</summary>

Vấn đề của các thư mục `final-1`, `final-2` không nằm ở số lượng bản sao mà ở việc không xác định được sự tiến triển của dự án. **Hệ thống kiểm soát phiên bản (Version Control System — VCS)** ghi nhận những trạng thái của tệp theo thời gian, cho phép xem lịch sử và tìm lại phiên bản cụ thể.

Ở mức khái niệm, VCS cung cấp **mốc ghi nhận**, **thông tin thay đổi**, khả năng **so sánh** và phối hợp các bản sửa. Git là một VCS phân tán; SVN là ví dụ về mô hình tập trung. VCS không thay thế đánh giá nghiệp vụ, kiểm thử và quy tắc chọn kết quả của nhóm.

**Ví dụ tiếp nối:** An ghi nhận thay đổi thuế và Bình ghi nhận thay đổi làm tròn thành hai thay đổi có ngữ cảnh riêng; nhóm có thể xem chúng trước khi quyết định. **Thực hành:** mô tả hai lợi ích mà một lịch sử có thể truy vấn mang lại so với sao chép thư mục.

### Tài liệu tham khảo
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="saving-backups-and-version-history">Lưu tệp và sao lưu thư mục so với lịch sử phiên bản có truy vết</a>

<details>
<summary>Xem chi tiết</summary>

Nhấn **Save** chỉ ghi nội dung hiện tại của tệp. Sao lưu một thư mục cho phép lấy lại dữ liệu nếu máy hỏng hoặc xóa nhầm. **Lịch sử phiên bản** thêm mối liên hệ giữa các trạng thái: thay đổi nào tạo ra trạng thái mới và có thể so sánh, truy tìm trạng thái nào.

Ba khả năng có thể cùng tồn tại nhưng không thay thế nhau. Repository cũng có thể bị mất hoặc hỏng, nên VCS không loại bỏ nhu cầu sao lưu và kế hoạch khôi phục. Một bản sao trên máy khác chưa chắc đã chứa mọi thay đổi chưa chia sẻ; cần biết điểm đến, thời điểm và phạm vi sao lưu.

**Minh họa:** lưu `Invoice.java` lúc 10:00, sao lưu dự án lúc 11:00, ghi nhận thay đổi thuế lúc 11:30. Bản sao 11:00 giúp phục hồi dữ liệu nhưng không chứa lần ghi nhận 11:30. **Bài tập:** vẽ ba lớp *working file — backup — versioned history* và ghi mục đích mỗi lớp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="change-provenance">Nội dung, người thực hiện, thời điểm và lý do thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Một trạng thái mã nguồn sẽ dễ tin cậy hơn nếu nhóm biết **nội dung** đã đổi, **ai** thực hiện, **khi nào** và **vì sao**. Các trường này tạo thành ngữ cảnh nguồn gốc thay đổi (provenance). Trong công cụ thực tế, tên người ghi nhận không nhất thiết chứng minh danh tính pháp lý; thời điểm và mô tả cũng có thể bị cấu hình sai, nên cần thêm review khi hậu quả quan trọng.

Hãy phân biệt phần **khác biệt kỹ thuật** (ví dụ thuế 8% thành 10%) với **lý do nghiệp vụ** (“quy định thuế mới có hiệu lực”). Nếu chỉ ghi “fix” trong mô tả, người điều tra lỗi sau ba tháng vẫn phải đoán. Kết nối một quyết định với mã nguồn giúp người khác hiểu và kiểm tra giả định đó.

**Quan sát:** hai thay đổi có cùng nội dung nhưng khác mục đích — sửa lỗi thử nghiệm hay cập nhật quy định chính thức — có thể cần quyết định tiếp nhận khác nhau. **Thực hành:** viết mô tả 1–2 câu nêu thay đổi, lý do và tác động dự kiến cho `Invoice.java`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="compare-inspect-and-restore">So sánh, xem lại và khôi phục phiên bản</a>

<details>
<summary>Xem chi tiết</summary>

Lịch sử chỉ có ích khi sử dụng được. **So sánh (diff)** cho thấy sự khác biệt giữa hai trạng thái; **xem lại (inspect)** giúp đọc nội dung và ngữ cảnh tại một mốc; **khôi phục** đưa tệp hoặc dự án về nội dung của trạng thái đã ghi nhận theo cách phù hợp. Khôi phục không đồng nghĩa xóa vĩnh viễn toàn bộ lịch sử mới hơn.

**Tình huống:** sau khi thay đổi quy tắc làm tròn, tổng tiền chênh một đồng. Nhóm so sánh phiên bản trước/sau, thấy đúng dòng sửa, đọc lý do, rồi lựa chọn sửa tiếp hoặc đưa cách tính cũ trở lại. Không nên cho rằng “quay về bản cũ” tự xử lý được dữ liệu đã xuất cho khách hàng.

**Thực hành:** tưởng tượng một bảng gồm *mốc A: 100.49*, *mốc B: 100*, *mốc C: 101*; xác định hai lần khác biệt, quyết định cần xem lại và bằng chứng để chọn khôi phục. Cú pháp diff/restore của Git thuộc module Git.

### Tài liệu tham khảo
- [Pro Git — Recording Changes to the Repository](https://git-scm.com/book/en/v2/Git-Basics-Recording-Changes-to-the-Repository)

</details>

- [Quay lại đầu trang](#back-to-top)
