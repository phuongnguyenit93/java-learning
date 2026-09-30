# Quy tắc của equals

Sau khi phân biệt định danh đối tượng với phép bằng nhau về mặt logic, câu hỏi tiếp theo là: **nếu một lớp muốn tự định nghĩa “hai đối tượng được xem là bằng nhau”, phương thức `equals` phải tuân những quy tắc nào?**

`equals` không chỉ là một phương thức trả về `boolean`. Nó là một quy ước mà cấu trúc dữ liệu, framework và đoạn mã sử dụng phía ngoài dựa vào để suy luận về đối tượng.

## <a id="equals-contract">Quy tắc của equals</a>

Một cách triển khai `equals` đúng phải giữ các tính chất cơ bản:

```text
phản xạ (reflexive)
x.equals(x) luôn true

đối xứng (symmetric)
x.equals(y) và y.equals(x) phải cho cùng kết quả

bắc cầu (transitive)
nếu x.equals(y) và y.equals(z) đều true
thì x.equals(z) cũng phải true

nhất quán (consistent)
kết quả ổn định khi trạng thái liên quan không đổi

null
x.equals(null) phải false
```

Trình biên dịch không ép bạn giữ các quy tắc này, nhưng các cấu trúc dữ liệu và thư viện mặc định **tin rằng đối tượng của bạn tuân thủ chúng**.

### PHẢN XẠ (REFLEXIVE) — ĐỐI TƯỢNG PHẢI BẰNG CHÍNH NÓ

Nếu `x.equals(x)` có thể trả `false`, gần như mọi cấu trúc dựa trên phép bằng nhau đều mất nền tảng suy luận.

Vì vậy nhánh kiểm tra nhanh này vừa đúng quy tắc vừa thường có chi phí thấp:

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

Tính đối xứng đặc biệt dễ bị phá khi kế thừa cho phép lớp con thêm trạng thái mới có ảnh hưởng tới phép bằng nhau.

### BẮC CẦU (TRANSITIVE) — PHÉP BẰNG NHAU KHÔNG ĐƯỢC “GÃY” QUA PHẦN TỬ TRUNG GIAN

Nếu:

```text
x.equals(y) == true
y.equals(z) == true
```

thì:

```text
x.equals(z) == true
```

Tính bắc cầu cho phép đoạn mã sử dụng xem phép bằng nhau như một quan hệ ổn định thay vì một chuỗi so sánh mâu thuẫn.

### NHẤT QUÁN (CONSISTENT) — KHÔNG TỰ THAY ĐỔI KHI TRẠNG THÁI KHÔNG ĐỔI

Một cách cài đặt phụ thuộc vào thời gian hiện tại, giá trị ngẫu nhiên hoặc I/O bên ngoài có thể khiến cùng hai đối tượng lúc thì bằng nhau, lúc thì không.

```java
// Cách làm không nên dùng: phép bằng nhau phụ thuộc thời gian
return Instant.now().getEpochSecond() % 2 == 0;
```

Đây là thiết kế sai vì đoạn mã sử dụng không thể dùng đối tượng một cách ổn định.

## <a id="equals-implementation">Cách triển khai equals</a>

Một cách cài đặt cho đối tượng giá trị thường đi theo luồng sau:

```text
1. cùng định danh?                  → true ngay
2. other có kiểu dữ liệu phù hợp?   → nếu không, false
3. so sánh trạng thái liên quan     → kết quả bằng nhau về mặt logic
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

> Đoạn mã này cố ý chỉ tập trung vào `equals`. Một kiểu dữ liệu giá trị hoàn chỉnh khi ghi đè `equals` còn phải cung cấp `hashCode` sao cho mọi đối tượng bằng nhau luôn tạo cùng mã băm; hai chương tiếp theo sẽ ghép đầy đủ phần quy ước đó.

### GHI ĐÈ (OVERRIDE), ĐỪNG VÔ TÌNH NẠP CHỒNG (OVERLOAD)

Chữ ký phương thức mà `Object` quy định là:

```java
public boolean equals(Object other)
```

Nếu viết:

```java
public boolean equals(UserId other) {
    ...
}
```

thì đó là **nạp chồng (overload)**, không phải **ghi đè (override)**. Đoạn mã gọi qua `Object`, cấu trúc dữ liệu hoặc API chuẩn vẫn có thể dùng `Object.equals()` thay vì phương thức bạn tưởng đã thay thế.

Vì vậy nên luôn dùng `@Override`:

```java
@Override
public boolean equals(Object other) {
    ...
}
```

Trình biên dịch sẽ giúp bắt lỗi chữ ký phương thức ngay tại chỗ.

### TRƯỜNG DỮ LIỆU NÀO NÊN THAM GIA PHÉP BẰNG NHAU?

Không có quy tắc “mọi trường dữ liệu đều phải so sánh”. Hãy hỏi:

> Trường dữ liệu nào thực sự xác định hai đối tượng là cùng giá trị hoặc cùng thực thể theo nghiệp vụ?

Ví dụ `User` có thể có:

```text
userId       → định danh nghiệp vụ ổn định
displayName  → trạng thái hiển thị có thể thay đổi
lastLoginAt  → trạng thái vận hành
```

Nếu phép bằng nhau của `User` đại diện định danh nghiệp vụ, việc đưa `lastLoginAt` vào phép so sánh có thể làm cùng một người dùng trở thành “khác nhau” chỉ vì vừa đăng nhập.

### TRẠNG THÁI XÁC ĐỊNH BẰNG NHAU CÓ THỂ THAY ĐỔI

Quy tắc **nhất quán** của `equals` yêu cầu các lần so sánh lặp lại phải cho cùng kết quả **khi trạng thái liên quan tới phép bằng nhau không đổi**. Nó không có nghĩa là một đối tượng đã thay đổi chính trạng thái đó vẫn phải trả kết quả cũ.

Ví dụ nếu phép bằng nhau phụ thuộc vào trường `email` có thể thay đổi:

```text
trước khi thay đổi
user.email = "a@example.com"
→ user có thể bằng một đối tượng khác có cùng email

sau khi thay đổi
user.email = "b@example.com"
→ kết quả bằng nhau có thể thay đổi một cách hợp lệ
```

Vì vậy rủi ro thiết kế không phải là “có thay đổi trạng thái thì `equals` tự động sai quy tắc”. Rủi ro nằm ở việc **ý nghĩa đối tượng đang đại diện cho giá trị/thực thể logic nào có thể thay đổi theo thời gian**, khiến việc suy luận, loại bỏ phần tử trùng, lưu đệm và sử dụng đối tượng làm khóa về sau khó giữ ổn định.

Nên ưu tiên trạng thái xác định bằng nhau đủ ổn định trong suốt khoảng thời gian đoạn mã sử dụng dựa vào ý nghĩa định danh/giá trị đó. Chương `equals/hashCode` phía sau sẽ cho thấy lỗi đặc biệt dễ quan sát khi trạng thái dùng cho phép bằng nhau và mã băm bị thay đổi sau khi đối tượng đã được đưa vào cấu trúc dữ liệu dựa trên mã băm.

### `instanceof` HAY `getClass()`?

Hai lựa chọn thể hiện hai chính sách khác nhau.

```java
if (!(other instanceof UserId that)) return false;
```

cho phép kiểu con tương thích tham gia phép bằng nhau, còn:

```java
if (other == null || getClass() != other.getClass()) return false;
```

yêu cầu đúng chính xác lớp thực tế tại thời điểm chạy.

Không có một lựa chọn máy móc đúng cho mọi cây kế thừa. Điều quan trọng là chính sách phải giữ tính đối xứng, bắc cầu và phù hợp với nghiệp vụ. Với đối tượng giá trị, lớp `final` hoặc hệ phân cấp đóng thường giúp quy tắc này dễ duy trì hơn.

### `equals` VÀ NULL

Với một tham chiếu `x` khác `null`:

```java
x.equals(null) // phải false
```

Nhưng gọi phương thức trên chính tham chiếu `null` sẽ thất bại trước khi vào `equals`:

```java
UserId x = null;
x.equals(other); // NullPointerException
```

Khi cần so sánh an toàn với `null`, `Objects.equals(a, b)` là một phương thức hỗ trợ hữu ích.

## <a id="equals-inheritance-risk">Phép bằng nhau và rủi ro khi kế thừa</a>

Kế thừa làm phép bằng nhau khó thiết kế hơn khi lớp con thêm trạng thái mới có ý nghĩa đối với phép so sánh.

Giả sử lớp cha chỉ xét `x`, còn lớp con muốn xét thêm `color`:

> Ví dụ dưới đây cố ý chỉ tập trung vào lỗi đối xứng của `equals`. Trong một lớp hoàn chỉnh, mỗi lớp ghi đè `equals` vẫn phải triển khai `hashCode` nhất quán với phép bằng nhau của chính nó.

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

Tính đối xứng bị phá vỡ.

### BÀI HỌC THIẾT KẾ

Với ngữ nghĩa giá trị phức tạp, hãy cân nhắc:

- kết hợp đối tượng (composition) thay vì mở kế thừa tùy ý;
- lớp giá trị `final`;
- hệ phân cấp `sealed`/đóng với chính sách bằng nhau rõ ràng;
- phép bằng nhau dựa trên lớp nếu nghiệp vụ yêu cầu đúng chính xác lớp thực tế tại thời điểm chạy.

Mục tiêu không phải chọn một mẫu `equals` duy nhất, mà thiết kế một **quan hệ bằng nhau nhất quán**.

### CHUYỂN TIẾP

`equals` đã trả lời “hai đối tượng có cùng giá trị logic không?”. Cấu trúc dữ liệu dựa trên mã băm còn cần một tín hiệu có chi phí thấp hơn để **thu hẹp tập ứng viên trước khi gọi `equals`**. Đó là vai trò của `hashCode`.
