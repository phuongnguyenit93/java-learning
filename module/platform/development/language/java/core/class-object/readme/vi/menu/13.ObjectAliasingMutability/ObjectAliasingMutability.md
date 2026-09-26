# Aliasing và Mutability

Aliasing xảy ra khi nhiều reference cùng trỏ tới một object. Với object bất biến (immutable), điều này thường an toàn. Với object có thể thay đổi (mutable), thay đổi qua một alias có thể xuất hiện “bất ngờ” ở nơi khác.

## <a id="aliasing-model">Nhiều Reference, Một Object</a>

```java
BankAccount account = new BankAccount("A-01", new ArrayList<>());
BankAccount alias = account;

alias.tags().add("VIP");
System.out.println(account.tags()); // [VIP]
```

Phép gán không sao chép `BankAccount`; nó chỉ sao chép reference.

Mô hình tư duy này nối trực tiếp với Java pass-by-value: method nhận một bản sao của giá trị reference, vì vậy vẫn có thể thay đổi cùng một object.

## <a id="shared-mutable-state">Trạng thái có thể thay đổi dùng chung</a>

Trạng thái có thể thay đổi và được dùng chung làm việc suy luận khó hơn vì một object có thể bị thay đổi từ nhiều nơi.

Hậu quả thường gặp:

- invariant bị phá ngoài nơi sở hữu;
- test phụ thuộc thứ tự;
- race condition trong mã đồng thời;
- cache/view bị thay đổi gián tiếp;
- khó biết nơi nào chịu trách nhiệm cập nhật trạng thái.

Không phải mọi mutability đều xấu; vấn đề là **quan hệ sở hữu và ranh giới thay đổi trạng thái có rõ không**.

## <a id="aliasing-in-collections">Aliasing qua Collection và Getter</a>

Một getter trả trực tiếp collection nội bộ có thể thay đổi sẽ làm lộ reference. Với ví dụ `BankAccount` xuyên suốt:

```java
List<String> tags() {
    return tags;
}
```

Bên gọi có thể thay đổi `tags` mà không đi qua quy tắc của `BankAccount`.

Tương tự, nếu constructor lưu thẳng reference tới collection đầu vào có thể thay đổi, bên gọi vẫn có thể sửa collection đó sau này.

Chương cuối giải quyết vấn đề này bằng **bất biến (immutability) và defensive copy**.
