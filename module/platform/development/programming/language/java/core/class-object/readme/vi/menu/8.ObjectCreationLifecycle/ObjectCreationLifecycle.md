# Vòng đời tạo Object

`new Child()` nhìn như một biểu thức đơn giản, nhưng phía sau là nhiều bước: cấp phát bộ nhớ (allocation), gán giá trị mặc định, chuỗi constructor, field/block initialization và cuối cùng mới có một object sử dụng được theo hợp đồng của class.

## <a id="allocation-initialization-construction">Các bước tạo Object</a>

Một mô hình tư duy đủ dùng:

```text
allocate memory cho object
        ↓
field nhận default zero/null/false
        ↓
khởi tạo phần superclass
        ↓
instance field initializer / initializer block
        ↓
constructor body của class hiện tại
        ↓
reference được trả về cho bên gọi nếu quá trình khởi tạo thành công
```

Đừng nhầm “memory đã được allocate” với “object đã ở trạng thái hợp lệ”. Invariant chỉ nên được coi là hoàn tất sau khi constructor chain kết thúc đúng.

## <a id="constructor-dynamic-dispatch-risk">Dynamic Dispatch trong Constructor</a>

Instance method call vẫn có dynamic dispatch ngay cả khi đang ở constructor.

Nếu superclass constructor gọi một overridable method, cách triển khai ở subclass có thể chạy **trước khi subclass trạng thái được initialize đầy đủ**.

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

Nguyên tắc an toàn: tránh gọi overridable method từ constructor.

## <a id="this-escape">this Escape</a>

`this` escape xảy ra khi reference tới object đang được khởi tạo bị công bố ra bên ngoài trước khi quá trình khởi tạo hoàn tất.

Ví dụ rủi ro:

```java
registry.add(this);
```

trong constructor, hoặc đăng listener/callback có thể chạy ngay.

Mã bên ngoài có thể quan sát object ở trạng thái chưa hoàn chỉnh. Trong mã chạy đồng thời, việc công bố object quá sớm còn gây rủi ro visibility nghiêm trọng hơn.

Chương tiếp theo chuyển từ vòng đời sang cách tổ chức type: **khi nào một class nên được đặt bên trong class khác và inner class giữ ngữ cảnh gì?**
