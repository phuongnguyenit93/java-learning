# Dùng chung tham chiếu và trạng thái có thể thay đổi

Hiện tượng nhiều tham chiếu cùng trỏ tới một đối tượng thường được gọi là **aliasing**. Với đối tượng bất biến, việc dùng chung này thường dễ kiểm soát. Với đối tượng có thể thay đổi, thay đổi qua một tham chiếu có thể xuất hiện “bất ngờ” ở nơi khác.

## <a id="aliasing-model">Nhiều tham chiếu, một đối tượng</a>

```java
BankAccount account = new BankAccount("A-01", new ArrayList<>());
BankAccount alias = account;

alias.tags().add("VIP");
System.out.println(account.tags()); // [VIP]
```

Phép gán không sao chép `BankAccount`; nó chỉ sao chép giá trị tham chiếu.

Mô hình tư duy này nối trực tiếp với quy tắc **Java luôn truyền tham trị (pass-by-value)**: phương thức nhận một bản sao của giá trị tham chiếu, vì vậy bản sao đó vẫn có thể trỏ tới và thay đổi cùng một đối tượng.

## <a id="shared-mutable-state">Trạng thái có thể thay đổi dùng chung</a>

Trạng thái có thể thay đổi và được dùng chung làm việc suy luận khó hơn vì một đối tượng có thể bị thay đổi từ nhiều nơi.

Hậu quả thường gặp:

- điều kiện hợp lệ bị phá ngoài nơi sở hữu;
- kiểm thử phụ thuộc thứ tự;
- điều kiện tranh chấp (`race condition`) trong mã đồng thời;
- dữ liệu đệm hoặc khung nhìn bị thay đổi gián tiếp;
- khó biết nơi nào chịu trách nhiệm cập nhật trạng thái.

Không phải mọi trạng thái có thể thay đổi đều xấu; vấn đề là **quan hệ sở hữu và ranh giới thay đổi trạng thái có rõ không**.

## <a id="aliasing-in-collections">Lộ tham chiếu qua tập hợp và phương thức getter</a>

Một phương thức getter trả trực tiếp tập hợp nội bộ có thể thay đổi sẽ làm lộ tham chiếu. Với ví dụ `BankAccount` xuyên suốt:

```java
List<String> tags() {
    return tags;
}
```

Bên gọi có thể thay đổi `tags` mà không đi qua quy tắc của `BankAccount`.

Tương tự, nếu hàm khởi tạo lưu thẳng tham chiếu tới tập hợp đầu vào có thể thay đổi, bên gọi vẫn có thể sửa tập hợp đó sau này.

Chương tiếp theo giải quyết vấn đề này bằng **tính bất biến và sao chép phòng vệ**.
