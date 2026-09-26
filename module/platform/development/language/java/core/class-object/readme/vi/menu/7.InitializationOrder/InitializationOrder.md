# Initialization Order

Nhiều bug construction xuất phát từ việc “đúng mã nhưng sai thời điểm”. Java có thứ tự rõ ràng cho class initialization và instance initialization.

## <a id="class-initialization-order">Static Initialization Order</a>

Khi một class được initialize, superclass được initialize trước nếu cần, sau đó static field initializer/static block của class chạy theo textual order.

```text
superclass static initialization
        ↓
subclass static fields/blocks theo source order
```

Class loading, linking và initialization sâu hơn thuộc module classloader; ở đây chỉ cần mô hình tư duy về thời điểm static trạng thái trở nên sẵn sàng.

## <a id="instance-initialization-order">Instance Initialization Order</a>

Trong một class, instance field initializer và instance initializer chạy theo textual order trước constructor body của class đó.

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

Hiểu order này giải thích vì sao gọi overridable method quá sớm trong constructor nguy hiểm: subclass method có thể chạy trước khi subclass fields được initialize như mong đợi.

chương tiếp theo nhìn toàn bộ quá trình object creation ở mức lifecycle và các rủi ro `this` escape.
