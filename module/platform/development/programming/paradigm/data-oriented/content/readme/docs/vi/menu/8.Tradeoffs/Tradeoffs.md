<a id="back-to-top"></a>

# Lựa chọn thiết kế hướng dữ liệu và giới hạn

## Menu
- [Lựa chọn cách tiếp cận dữ liệu hay trách nhiệm đối tượng](#dop-choose)
- [Chi phí schema, khả năng quan sát và đánh đổi đóng gói](#dop-costs)
- [Điểm chung và khác biệt với lập trình hàm](#dop-functional-relation)
- [Mô hình lập trình và Data-Oriented Design theo bố trí bộ nhớ](#dop-versus-dod)
- [Quyết định đầu cuối và hướng học tiếp ở module chuyên sâu](#dop-handoff)

## <a id="dop-choose">Lựa chọn cách tiếp cận dữ liệu hay trách nhiệm đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

Hãy cân nhắc DOP khi một cấu trúc dữ liệu phải được **kiểm tra, quan sát, biến đổi và dùng lại** bởi nhiều thao tác độc lập: đơn hàng phục vụ tính tiền, báo cáo và kiểm tra điều kiện. Việc truyền map bất biến giúp các thao tác nhìn được cùng một hình dạng mà không phụ thuộc vào lớp mang nhiều hành vi.

Ngược lại, nếu đối tượng có bất biến nghiệp vụ phức tạp và chỉ vài nơi sử dụng, đặt trách nhiệm chung trong đối tượng có thể rõ hơn. Ta vẫn có thể dùng lớp có kiểu để giữ ranh giới miền và phép biến đổi dữ liệu đơn giản bên trong một luồng đã được kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-costs">Chi phí schema, khả năng quan sát và đánh đổi đóng gói</a>

<details>
<summary>Xem chi tiết</summary>

Map linh hoạt giúp thao tác nhanh lúc đầu nhưng **tăng phụ thuộc vào tên khóa**. Đổi `lines` thành `items` có thể làm hỏng nhiều hàm; tách hành vi khỏi đối tượng cũng làm một số điều kiện nhất quán không còn được đóng gói tự động. Schema và kiểm tra ranh giới giảm rủi ro nhưng cần bảo trì.

Ảnh chụp bất biến có thể tăng cấp phát nếu thư viện không chia sẻ cấu trúc. Đừng mặc định phơi bày trường dữ liệu là miễn phí: cần cân nhắc quyền riêng tư, số lượng bên dùng, chi phí chuyển schema và khả năng gỡ lỗi trước khi quyết định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-functional-relation">Điểm chung và khác biệt với lập trình hàm</a>

<details>
<summary>Xem chi tiết</summary>

Lập trình **hàm** ưu tiên phép biến đổi dễ dự đoán và ghép hàm; DOP theo Sharvit bổ sung lựa chọn **biểu diễn**: dữ liệu phổ dụng, bất biến, thao tác riêng và schema tách khỏi dữ liệu. Hàm giảm giá thuần `giamGia(don)` có thể xuất hiện trong cả hai, nhưng đây không phải hai tên gọi của cùng một thứ.

Hàm đọc cơ sở dữ liệu theo thời gian thực vẫn có tác động phụ dù nhận map. Ngược lại, một thiết kế lập trình hàm không bắt buộc dùng map phổ dụng. **Tính thuần của thao tác** và **hình thức biểu diễn dữ liệu** là hai quyết định liên quan nhưng khác nhau.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-versus-dod">Mô hình lập trình và Data-Oriented Design theo bố trí bộ nhớ</a>

<details>
<summary>Xem chi tiết</summary>

**Data-Oriented Programming** trong module này bàn về cách biểu diễn dữ liệu ứng dụng và đặt thao tác ở đâu. **Data-Oriented Design** theo hướng hiệu năng lại tập trung bố trí bộ nhớ, locality của cache, xử lý theo lô, SIMD và khả năng tận dụng phần cứng. Hai lĩnh vực dùng tên gần nhau nhưng giải quyết câu hỏi khác nhau.

Ví dụ danh sách các map đơn hàng bất biến không tự tối ưu truy cập cache. Việc đổi từ array-of-structures sang structure-of-arrays có thể phù hợp với dữ liệu số lớn, nhưng thuộc bài toán hiệu năng khác, không phải một nguyên tắc của Sharvit.

### Tài liệu tham khảo

- [Data-Oriented Design — Richard Fabian](https://www.dataorienteddesign.com/dodbook/): cách nhìn bố trí bộ nhớ và hiệu năng, khác phạm vi lập trình hướng dữ liệu ở đây.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dop-handoff">Quyết định đầu cuối và hướng học tiếp ở module chuyên sâu</a>

<details>
<summary>Xem chi tiết</summary>

Quay lại đơn A: nhận `id` và `lines`; xác minh từng số lượng/đơn giá, tính tổng **100**, tạo bản sau giảm 10% còn **90**, rồi dùng cơ chế phiên bản để điều phối việc lưu. Người kiểm tra có thể đối chiếu map trước–sau, xác định schema cần thiết và xem việc dùng kiểu biến thể trong Java có giúp giảm trạng thái bất hợp lệ hay không.

```text
nhận A → kiểm tra → tổng 100 → giảm 10% thành 90
       → kiểm phiên bản → lưu hoặc báo xung đột
```

Kết thúc bài này, bạn có thể giải thích vì sao chọn mô hình hướng dữ liệu và các chi phí của nó. Học **record/sealed/patterns** sâu ở Java, persistence ở module dữ liệu, bất biến miền ở thiết kế phần mềm và tối ưu cache ở bài hiệu năng chuyên trách.

</details>

- [Quay lại đầu trang](#back-to-top)
