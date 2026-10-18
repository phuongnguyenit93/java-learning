<a id="back-to-top"></a>

# Góc nhìn lập trình hướng dữ liệu trong Java

## Menu
- [DOP trong Java: Hướng tiếp cận liên quan nhưng khác biệt](#amber-definition)
- [Mô hình dữ liệu minh bạch và bất biến](#amber-transparent-data)
- [Mô hình dữ liệu đầy đủ và loại trừ trạng thái bất hợp lệ](#amber-valid-variants)
- [Tách thao tác khỏi dữ liệu và xử lý các biến thể theo mẫu](#amber-separate-operations)
- [So sánh mô hình dữ liệu có kiểu Java với dữ liệu phổ dụng theo Sharvit](#amber-sharvit-comparison)

## <a id="amber-definition">DOP trong Java: Hướng tiếp cận liên quan nhưng khác biệt</a>

<details>
<summary>Xem chi tiết</summary>

Tài liệu **Inside Java/Project Amber** cũng dùng tên Data-Oriented Programming nhưng đặt trọng tâm vào **mô hình dữ liệu có kiểu chính xác**. Trong diễn đạt v1.1 năm 2024, bốn nguyên tắc là: dữ liệu **bất biến và minh bạch**; biểu diễn đúng, đủ và chỉ những dữ kiện cần thiết; **loại trừ trạng thái bất hợp lệ khỏi mô hình**; và **tách thao tác khỏi dữ liệu**.

Hướng này gặp Sharvit ở việc coi trọng giá trị và thao tác riêng, nhưng không bắt dùng map/list phổ dụng và schema rời. Java có thể dùng kiểu record, sealed hierarchy và pattern matching để diễn đạt các ràng buộc trước khi chạy; đây là **so sánh tư duy**, còn cú pháp thuộc module Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="amber-transparent-data">Mô hình dữ liệu minh bạch và bất biến</a>

<details>
<summary>Xem chi tiết</summary>

Một giá trị dữ liệu **minh bạch** cho người xử lý nhìn thấy các thành phần cần thiết để hiểu nó; **bất biến** giúp những thành phần ấy không bị thay đổi ngầm sau khi chia sẻ. Ví dụ bản tóm tắt đơn gồm mã và tổng tiền cần hiển thị hai dữ kiện ấy rõ ràng, thay vì giấu chúng trong đối tượng liên tục tự đổi hành vi.

Record Java thường thích hợp cho kiểu dữ liệu mang thành phần rõ, nhưng **bất biến của record có giới hạn nông** nếu một thành phần tham chiếu tới danh sách còn sửa được. Minh bạch cũng không có nghĩa tự do tiết lộ bí mật: quyền truy cập và dữ liệu được phép xuất ra vẫn phải được quản lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="amber-valid-variants">Mô hình dữ liệu đầy đủ và loại trừ trạng thái bất hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

Nguyên tắc **biểu diễn đúng, đủ và chỉ dữ liệu cần thiết** yêu cầu mô hình thể hiện các trường hợp có ý nghĩa, tránh cờ dư gây tổ hợp vô lý. Thay vì `{paid:true, rejected:true}` mâu thuẫn, có thể biểu diễn một trạng thái thuộc **`Pending`**, **`Paid(receipt)`** hoặc **`Rejected(reason)`**.

```text
ThanhToan = Cho | DaTra(bienNhan) | TuChoi(lyDo)
```

Một họ kiểu sealed có thể giới hạn các biến thể hợp lệ về mặt kiểu, giúp **không thể tạo đồng thời hai kết cục trái ngược** bằng cùng một giá trị. Những quy tắc không biểu diễn hết ở hệ kiểu, như số tiền dương hoặc biên nhận có thật, vẫn cần kiểm tra lúc tạo và tại ranh giới bên ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="amber-separate-operations">Tách thao tác khỏi dữ liệu và xử lý các biến thể theo mẫu</a>

<details>
<summary>Xem chi tiết</summary>

Nguyên tắc thứ tư trong **Java DOP v1.1** khuyến khích đặt các thao tác nghiệp vụ riêng khỏi kiểu chỉ mang dữ liệu. Thay vì gắn mọi quy tắc vào `Paid`, `Pending` hoặc `Rejected`, một hàm xử lý nhận dữ liệu và quyết định nhánh phù hợp theo trường hợp thực tế.

**Pattern matching** là một cách Java hỗ trợ chọn nhánh và xử lý biến thể; việc cần hiểu là chọn đúng hành vi và bao quát các trường hợp hợp lệ, không phải học thuộc cú pháp `switch`. Tách thao tác không khiến mọi hàm tự thuần: thao tác gọi dịch vụ hay ghi dữ liệu vẫn phải quản lý tác động phụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="amber-sharvit-comparison">So sánh mô hình dữ liệu có kiểu Java với dữ liệu phổ dụng theo Sharvit</a>

<details>
<summary>Xem chi tiết</summary>

Hai hướng DOP đều ưu tiên **dữ liệu có thể hiểu và thao tác tách biệt**, nhưng khác cách đạt điều đó. Sharvit thường dùng map và list phổ dụng, schema kiểm tra riêng; đổi lại có thể phải phát hiện lỗi hình dạng lúc chạy. Hướng Java/Amber tận dụng **kiểu dữ liệu và biến thể chính xác** để loại một số kết hợp sai trước khi chương trình chạy.

Trong cả hai hướng, dữ liệu từ mạng hoặc file vẫn cần được xác minh. Chọn mô hình theo độ ổn định của hợp đồng, yêu cầu linh hoạt và mức bảo đảm cần thiết; không chọn vì cho rằng một bên mới là “DOP chính hiệu”.

### Tài liệu tham khảo

- [Data-Oriented Programming in Java — Version 1.1](https://inside.java/2024/05/23/dop-v1-1-introduction/): bốn nguyên tắc DOP có kiểu theo Inside Java.
- [Principles of Data-Oriented Programming — Yehonathan Sharvit](https://blog.klipse.tech/dop/2022/06/22/principles-of-dop.html): trọng tâm dữ liệu phổ dụng và schema độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)
