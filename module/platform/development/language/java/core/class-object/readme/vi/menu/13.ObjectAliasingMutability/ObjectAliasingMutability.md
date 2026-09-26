# Aliasing và Mutability

Aliasing xảy ra khi nhiều reference cùng trỏ tới một object. Với immutable object, điều này thường an toàn. Với mutable object, thay đổi qua một alias có thể xuất hiện “bất ngờ” ở nơi khác.

## <a id="aliasing-model">Nhiều Reference, Một Object</a>

```java
List<String> a = new ArrayList<>();
List<String> b = a;

b.add("x");
System.out.println(a); // [x]
```

Không có copy collection ở assignment. Chỉ reference được copy.

mô hình tư duy này nối trực tiếp với Java pass-by-value: method nhận một bản copy của reference value, vì vậy vẫn có thể mutate cùng object.

## <a id="shared-mutable-state">Trạng thái Mutable dùng chung</a>

Shared mutable trạng thái làm reasoning khó hơn vì một object có thể bị thay đổi từ nhiều nơi.

Hậu quả thường gặp:

- invariant bị phá ngoài owner;
- test phụ thuộc thứ tự;
- concurrency race;
- cache/view bị thay đổi gián tiếp;
- khó biết ai chịu trách nhiệm update trạng thái.

Không phải mọi mutability đều xấu; vấn đề là **quan hệ sở hữu và mutation ranh giới có rõ không**.

## <a id="aliasing-in-collections">Aliasing qua Collection và Getter</a>

Một getter trả trực tiếp mutable collection nội bộ sẽ làm lộ reference:

```java
List<String> getRoles() {
    return roles;
}
```

bên gọi có thể mutate `roles` mà không đi qua quy tắc của owner.

Tương tự, constructor lưu thẳng mutable đầu vào collection cũng có thể bị bên gọi mutate sau đó.

chương cuối giải quyết vấn đề này bằng **immutability và defensive copy**.
