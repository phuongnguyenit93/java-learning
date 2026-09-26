# Initialization Order

Nhiều bug trong quá trình khởi tạo xuất phát từ việc “đúng mã nhưng sai thời điểm”. Java có thứ tự rõ ràng cho class initialization và instance initialization.

## <a id="class-initialization-order">Static Initialization Order</a>

Khi một class được initialize, superclass được initialize trước nếu cần, sau đó static field initializer/static block của class chạy theo thứ tự xuất hiện trong mã nguồn.

```text
superclass static initialization
        ↓
static fields/blocks của subclass theo thứ tự trong mã nguồn
```

Loading, linking và initialization ở mức sâu hơn thuộc module `classloader`; ở đây chỉ cần mô hình tư duy về thời điểm trạng thái `static` trở nên sẵn sàng.

## <a id="instance-initialization-order">Instance Initialization Order</a>

Trong một class, instance field initializer và instance initializer chạy theo thứ tự xuất hiện trong mã nguồn trước constructor body của class đó.

```text
trạng thái mặc định zero/null
→ field initializer / instance block
→ constructor body
```

Nhưng superclass portion vẫn phải được construct trước subclass portion.

## <a id="inheritance-initialization-order">Initialization qua Inheritance</a>

Với `new Child()`:

```text
class initialization nếu cần
        ↓
Parent instance initialization
        ↓
Parent constructor body
        ↓
Child instance initialization
        ↓
Child constructor body
```

Hiểu thứ tự này giải thích vì sao gọi overridable method quá sớm trong constructor nguy hiểm: method của subclass có thể chạy trước khi các field của subclass được initialize như mong đợi.

Có thể quan sát thứ tự đó bằng một đoạn trace nhỏ:

```java
class Trace {
    static int log(String step) {
        System.out.println(step);
        return 0;
    }
}

class Parent {
    int parentField = Trace.log("parent field");
    { Trace.log("parent block"); }
    Parent() { Trace.log("parent constructor"); }
}

class Child extends Parent {
    int childField = Trace.log("child field");
    { Trace.log("child block"); }
    Child() { Trace.log("child constructor"); }
}
```

Nếu class đã được khởi tạo trước đó, `new Child()` sẽ in các bước field/block/constructor của `Parent` trước rồi mới tới các bước tương ứng của `Child`. Đoạn mã này là minh chứng cho vòng đời ở trên, không thay thế cho việc hiểu quy tắc.

Chương tiếp theo nhìn toàn bộ quá trình tạo object ở mức vòng đời và rủi ro `this` escape.
