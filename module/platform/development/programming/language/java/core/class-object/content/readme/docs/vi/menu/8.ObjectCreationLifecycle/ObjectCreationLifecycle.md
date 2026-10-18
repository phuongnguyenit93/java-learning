# Quá trình tạo đối tượng

`new Child()` nhìn như một biểu thức đơn giản, nhưng phía sau là nhiều bước: cấp phát bộ nhớ, gán giá trị mặc định, chuỗi hàm khởi tạo, khởi tạo trường/khối và cuối cùng mới có một đối tượng sử dụng được theo hợp đồng của lớp. Học toàn bộ chuỗi này giúp phân biệt “đã có vùng nhớ” với “đã có đối tượng hợp lệ”.

## <a id="allocation-initialization-construction">Các bước tạo đối tượng</a>

Một mô hình tư duy đủ dùng:

```text
cấp phát bộ nhớ cho đối tượng
        ↓
trường nhận giá trị mặc định 0/null/false
        ↓
khởi tạo phần lớp cha
        ↓
khởi tạo trường / khối khởi tạo đối tượng
        ↓
thân hàm khởi tạo của lớp hiện tại
        ↓
tham chiếu được trả về cho bên gọi nếu quá trình khởi tạo thành công
```

Đừng nhầm “bộ nhớ đã được cấp phát” với “đối tượng đã ở trạng thái hợp lệ”. Các điều kiện hợp lệ chỉ nên được coi là hoàn tất sau khi chuỗi hàm khởi tạo kết thúc thành công.

## <a id="constructor-dynamic-dispatch-risk">Gọi phương thức bị ghi đè trong hàm khởi tạo</a>

Lời gọi phương thức của đối tượng vẫn chọn cách triển khai theo kiểu thực tế ngay cả khi đang ở trong hàm khởi tạo.

Nếu hàm khởi tạo của lớp cha gọi một phương thức có thể bị ghi đè, cách triển khai ở lớp con có thể chạy **trước khi trạng thái của lớp con được khởi tạo đầy đủ**.

```java
class Parent {
    Parent() { print(); }
    void print() { }
}

class Child extends Parent {
    private String value = "ready";
    @Override void print() { System.out.println(value); }
}
```

`print()` có thể nhìn thấy `value == null` khi được gọi từ `Parent()`.

Nguyên tắc an toàn: tránh gọi phương thức có thể bị ghi đè từ hàm khởi tạo.

## <a id="this-escape">Chia sẻ this trước khi khởi tạo xong</a>

Rủi ro thường gọi là `this escape` xảy ra khi tham chiếu tới đối tượng đang được khởi tạo bị chia sẻ ra bên ngoài trước khi quá trình khởi tạo hoàn tất.

Ví dụ rủi ro:

```java
registry.add(this);
```

trong hàm khởi tạo, hoặc đăng ký listener/callback có thể chạy ngay.

Mã bên ngoài có thể quan sát đối tượng ở trạng thái chưa hoàn chỉnh. Trong mã chạy đồng thời, việc công bố đối tượng quá sớm còn gây vấn đề về khả năng nhìn thấy trạng thái giữa các luồng.

Chương tiếp theo chuyển từ quá trình tạo đối tượng sang cách tổ chức kiểu: **khi nào một lớp nên được đặt bên trong lớp khác và lớp nội bộ giữ ngữ cảnh gì?**
