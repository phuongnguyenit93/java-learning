<a id="back-to-top"></a>

# Mô hình tập trung và phân tán

## Menu
- [Kiểm soát phiên bản tập trung: Mô hình máy chủ và máy khách](#centralized-vcs-model)
- [Kiểm soát phiên bản phân tán: Bản sao chứa lịch sử trên máy cá nhân](#distributed-vcs-model)
- [So sánh khả năng làm việc cục bộ và phối hợp](#centralized-distributed-tradeoffs)
- [Khả năng truy cập lịch sử và quản trị bản sao](#history-availability-and-governance)
- [Tình huống máy chủ gián đoạn: Khả năng sử dụng lịch sử cục bộ](#offline-history-availability-scenario)

## <a id="centralized-vcs-model">Kiểm soát phiên bản tập trung: Mô hình máy chủ và máy khách</a>

<details>
<summary>Xem chi tiết</summary>

Sau khi biết vì sao cần lịch sử, câu hỏi tiếp theo là **lịch sử được lưu ở đâu**. Trong mô hình kiểm soát phiên bản **tập trung** (CVCS), máy chủ chung giữ kho phiên bản chính; các máy khách lấy tệp và trao đổi thay đổi với máy chủ theo quy trình của công cụ. Apache Subversion là ví dụ quen thuộc.

Lợi thế là quản trị một điểm tập trung: nhóm biết lịch sử chính ở đâu và có thể tổ chức quyền truy cập tại máy chủ. Đổi lại, khi máy chủ hoặc kết nối không sẵn sàng, thao tác cần truy cập lịch sử tập trung có thể bị hạn chế. Điều này không đồng nghĩa mọi thao tác sửa tệp cục bộ đều ngừng hoạt động.

**Minh chứng:** An tải bản `Invoice.java` về, sửa khi đi tàu không có mạng. An vẫn có thể sửa tệp, nhưng một số thao tác lịch sử hoặc chia sẻ cần máy chủ trở lại. **Thử áp dụng:** vẽ một máy chủ ở giữa và ba máy khách, đánh dấu nơi giữ lịch sử theo mô hình này.

### Tài liệu tham khảo
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="distributed-vcs-model">Kiểm soát phiên bản phân tán: Bản sao chứa lịch sử trên máy cá nhân</a>

<details>
<summary>Xem chi tiết</summary>

Mô hình **phân tán (DVCS)** giải quyết một phần giới hạn trên bằng cách cho bản sao kho trên máy người làm việc chứa lịch sử phiên bản, không chỉ bản tệp mới nhất. Git là ví dụ điển hình. Trong cách làm thông thường, người dùng có thể xem lịch sử và ghi nhận thay đổi cục bộ mà không cần hỏi máy chủ mỗi lần.

“Phân tán” **không có nghĩa không cần nguồn chung**. Nhóm vẫn có thể chọn một repository từ xa làm điểm phối hợp và áp dụng quyền, review ở đó. Mỗi bản sao cục bộ cần được bảo vệ; có lịch sử trên nhiều máy giảm một số rủi ro nhưng không thay thế kế hoạch sao lưu có kiểm chứng.

**Ví dụ:** Bình tạo trạng thái mới của cách làm tròn trong kho cục bộ khi ngoại tuyến; sau đó mới chia sẻ cho nhóm. **Tự kiểm tra:** phân biệt khả năng tự ghi nhận lịch sử với quyền được đưa thay đổi vào nguồn chung.

### Tài liệu tham khảo
- [Pro Git — About Version Control](https://git-scm.com/book/en/v2/Getting-Started-About-Version-Control)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="centralized-distributed-tradeoffs">So sánh khả năng làm việc cục bộ và phối hợp</a>

<details>
<summary>Xem chi tiết</summary>

Khác biệt quan trọng nằm ở **khả năng sử dụng lịch sử cục bộ** và cách phối hợp, không phải “một hệ thống có máy chủ, hệ kia không”. Cả CVCS và DVCS đều có thể dựa vào một nơi chung để cộng tác; Git vẫn có thể làm việc theo quy trình tập trung về quyền tiếp nhận.

| Tiêu chí | Tập trung | Phân tán |
| --- | --- | --- |
| Lịch sử đầy đủ trên máy khách | Thường phụ thuộc máy chủ | Thông thường có bản sao lịch sử cục bộ |
| Ghi nhận lịch sử khi không có mạng | Tùy công cụ, thường hạn chế | Có thể thực hiện cục bộ |
| Điểm điều phối nhóm | Kho trung tâm | Có thể chọn kho từ xa chung |
| Chi phí quản trị | Quản trị máy chủ là trọng tâm | Cần quản lý bản sao, đồng bộ và quyền tiếp nhận |

**Lựa chọn không tuyệt đối:** đội có chính sách mạng chặt và quản trị tập trung có yêu cầu khác đội thường làm việc ngoại tuyến. **Thực hành:** nêu một lợi ích và một trách nhiệm mới khi chuyển từ CVCS sang DVCS.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="history-availability-and-governance">Khả năng truy cập lịch sử và quản trị bản sao</a>

<details>
<summary>Xem chi tiết</summary>

Có nhiều bản sao lịch sử không đồng nghĩa chúng đồng nhất. An có thể có một trạng thái mới chưa chia sẻ; Bình vẫn có lịch sử cũ; kho chung có một trạng thái thứ ba. Cần phân biệt **khả năng truy cập lịch sử**, **tính đầy đủ của bản sao** và **quyền công nhận trạng thái chung**.

Quản trị bản sao gồm bảo vệ máy cá nhân, kiểm soát thông tin nhạy cảm, chuẩn bị sao lưu và xác định nơi chấp nhận thay đổi. Nếu dữ liệu bí mật đã đi vào lịch sử rồi được nhân bản, chỉ xóa một dòng trong tệp hiện tại không chắc loại bỏ được dữ liệu khỏi các bản sao lịch sử.

**Tình huống:** máy An mất ổ cứng nhưng kho chung vẫn giữ những thay đổi An đã chia sẻ; công việc An mới ghi nhận nhưng chưa chia sẻ có thể không có trong kho chung. **Bài tập:** chỉ ra bản nào có thể hỗ trợ khôi phục từng phần và vì sao không được giả định mọi bản sao là bản sao lưu đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="offline-history-availability-scenario">Tình huống máy chủ gián đoạn: Khả năng sử dụng lịch sử cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử máy chủ chung ngừng truy cập trong hai giờ. Với mô hình tập trung, An vẫn có thể sửa tệp đang có nhưng có thể chưa thực hiện được các thao tác yêu cầu máy chủ. Với kho Git cục bộ có lịch sử, An có thể xem những trạng thái đã có và ghi nhận thay đổi mới trên máy. Dù vậy, cả hai vẫn chưa thể trao đổi trực tiếp qua máy chủ đang ngừng.

**Giới hạn quan trọng:** Bình vừa ghi nhận một bản vá ở kho riêng trước khi mất mạng. Bản vá này **không tự xuất hiện** trong kho An. Làm việc ngoại tuyến khác với đồng bộ và khác với phê duyệt đưa vào nguồn chung.

**Thực hành:** điền bảng *hành động | cần máy chủ? | có đảm bảo nhìn thấy thay đổi của Bình?* cho việc sửa file, xem lịch sử sẵn có, ghi nhận cục bộ và cập nhật trạng thái chung. Kết quả dùng để chuẩn bị sang chương kho cục bộ/từ xa.

</details>

- [Quay lại đầu trang](#back-to-top)
