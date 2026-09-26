# equals

Sau khi phân biệt identity và logical equality, câu hỏi tiếp theo là: **nếu một class muốn tự định nghĩa “hai object được xem là bằng nhau”, method `equals` phải tuân những quy tắc nào?**

`equals` không chỉ là một boolean method. Nó là một contract mà collection, framework và code gọi phía ngoài dựa vào để suy luận về object.

## <a id="equals-contract">Hợp đồng equals</a>

Một cách triển khai `equals` đúng phải giữ các tính chất cơ bản:

```text
reflexive
x.equals(x) luôn true

symmetric
x.equals(y) và y.equals(x) phải cho cùng kết quả

transitive
nếu x.equals(y) và y.equals(z) đều true
thì x.equals(z) cũng phải true

consistent
kết quả ổn định khi trạng thái liên quan không đổi

null
x.equals(null) phải false
```

Compiler không ép bạn giữ các luật này, nhưng collection và library mặc định **tin rằng object của bạn giữ hợp đồng**.

### PHẢN XẠ (REFLEXIVE) — OBJECT PHẢI BẰNG CHÍNH NÓ

Nếu `x.equals(x)` có thể trả false, gần như mọi cấu trúc dựa trên equality đều mất nền tảng suy luận.

Vì vậy fast path này vừa đúng contract vừa thường rẻ:

```java
if (this == other) {
    return true;
}
```

### ĐỐI XỨNG (SYMMETRIC) — HAI CHIỀU PHẢI CÙNG KẾT QUẢ

Nếu:

```text
a.equals(b) == true
```

thì phải có:

```text
b.equals(a) == true
```

Symmetry đặc biệt dễ bị phá khi inheritance cho phép class con thêm equality-relevant state.

### BẮC CẦU (TRANSITIVE) — EQUALITY KHÔNG ĐƯỢC “GÃY” QUA PHẦN TỬ TRUNG GIAN

Nếu:

```text
x.equals(y) == true
y.equals(z) == true
```

thì:

```text
x.equals(z) == true
```

Transitivity cho phép caller xem equality như một quan hệ ổn định thay vì một chuỗi so sánh mâu thuẫn.

### NHẤT QUÁN (CONSISTENT) — KHÔNG TỰ THAY ĐỔI KHI TRẠNG THÁI KHÔNG ĐỔI

Một implementation phụ thuộc vào thời gian hiện tại, random value hoặc external I/O có thể khiến cùng hai object lúc thì equal, lúc thì không.

```java
// Anti-pattern: equality phụ thuộc thời gian
return Instant.now().getEpochSecond() % 2 == 0;
```

Đây là design sai vì caller không thể dùng object một cách ổn định.

## <a id="equals-implementation">Cách triển khai equals</a>

Một implementation kiểu value object thường đi theo flow:

```text
1. cùng identity?              → true ngay
2. other có type phù hợp?      → nếu không, false
3. so sánh equality state      → kết quả logical equality
```

Ví dụ:

```java
import java.util.Objects;

final class UserId {
    private final String value;

    UserId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserId that)) return false;
        return Objects.equals(value, that.value);
    }
}
```

### OVERRIDE, ĐỪNG VÔ TÌNH OVERLOAD

Signature contract của `Object` là:

```java
public boolean equals(Object other)
```

Nếu viết:

```java
public boolean equals(UserId other) {
    ...
}
```

thì đó là **overload**, không phải override. Code gọi qua `Object`, collection hoặc API chuẩn vẫn có thể dùng `Object.equals()` thay vì method bạn tưởng đã thay thế.

Vì vậy nên luôn dùng `@Override`:

```java
@Override
public boolean equals(Object other) {
    ...
}
```

Compiler sẽ giúp bắt lỗi signature ngay tại chỗ.

### FIELD NÀO NÊN THAM GIA EQUALITY?

Không có rule “mọi field đều phải so sánh”. Hãy hỏi:

> Field nào thực sự định nghĩa object này là cùng value/entity theo domain?

Ví dụ `User` có thể có:

```text
userId       → identity domain ổn định
displayName  → mutable presentation state
lastLoginAt  → operational state
```

Nếu equality của `User` đại diện identity domain, việc đưa `lastLoginAt` vào equality có thể làm cùng user trở thành “khác nhau” chỉ vì vừa đăng nhập.

### `instanceof` HAY `getClass()`?

Hai lựa chọn thể hiện hai policy khác nhau.

```java
if (!(other instanceof UserId that)) return false;
```

cho phép subtype tương thích tham gia equality, còn:

```java
if (other == null || getClass() != other.getClass()) return false;
```

yêu cầu exact runtime class.

Không có một lựa chọn máy móc đúng cho mọi hierarchy. Điều quan trọng là policy phải giữ symmetry/transitivity và phù hợp domain. Với value object, `final` class hoặc closed hierarchy thường làm contract dễ giữ hơn.

### `equals` VÀ NULL

Với một reference `x` khác null:

```java
x.equals(null) // phải false
```

Nhưng gọi method trên chính reference null sẽ fail trước khi vào `equals`:

```java
UserId x = null;
x.equals(other); // NullPointerException
```

Khi cần null-safe equality, `Objects.equals(a, b)` là helper hữu ích.

## <a id="equals-inheritance-risk">Equality và Inheritance</a>

Inheritance làm equality khó hơn khi class con thêm trạng thái mới có ý nghĩa đối với equality.

Giả sử class cha chỉ xét `x`, còn class con muốn xét thêm `color`:

> Ví dụ dưới đây cố ý chỉ tập trung vào lỗi symmetry của `equals`. Trong class hoàn chỉnh, mỗi class override `equals` vẫn phải triển khai `hashCode` nhất quán với equality state tương ứng.

```java
class Point {
    final int x;

    Point(int x) {
        this.x = x;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Point p && x == p.x;
    }
}

class ColoredPoint extends Point {
    final String color;

    ColoredPoint(int x, String color) {
        super(x);
        this.color = color;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ColoredPoint p
                && x == p.x
                && Objects.equals(color, p.color);
    }
}
```

Khi đó có thể xảy ra:

```text
new Point(1).equals(new ColoredPoint(1, "red"))
→ true

new ColoredPoint(1, "red").equals(new Point(1))
→ false
```

Symmetry bị phá.

### BÀI HỌC THIẾT KẾ

Với value semantics phức tạp, hãy cân nhắc:

- composition thay vì mở inheritance tùy ý;
- `final` value class;
- sealed/closed hierarchy với equality policy rõ ràng;
- class-based equality nếu domain yêu cầu exact type identity.

Mục tiêu không phải chọn một template `equals` duy nhất, mà thiết kế một **quan hệ equality nhất quán**.

### CHUYỂN TIẾP

`equals` đã trả lời “hai object có cùng logical value không?”. Hash-based collection còn cần một tín hiệu rẻ hơn để **thu hẹp candidate trước khi gọi equals**. Đó là vai trò của `hashCode`.
