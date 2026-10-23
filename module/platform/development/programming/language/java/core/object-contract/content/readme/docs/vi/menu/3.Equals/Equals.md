# Quy tắc của equals

Sau khi phân biệt định danh đối tượng với phép bằng nhau về mặt logic, câu hỏi tiếp theo là: **nếu một lớp muốn tự định nghĩa “hai đối tượng được xem là bằng nhau”, phương thức `equals` phải tuân những quy tắc nào?**

`equals` không chỉ là một phương thức trả về `boolean`. Nó là một quy ước mà cấu trúc dữ liệu, framework và đoạn mã sử dụng phía ngoài dựa vào để suy luận về đối tượng.

## <a id="equals-contract">Quy tắc của equals</a>

Theo quy ước của `Object.equals(Object)`, phép bằng nhau phải là một **quan hệ tương đương trên các tham chiếu khác `null`**: các đối tượng được xem là bằng nhau phải có thể được coi là cùng một nhóm giá trị logic, bất kể ai thực hiện phép so sánh trước. Quy ước gồm **năm yêu cầu**:

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

khác null
x.equals(null) phải false khi x khác null
```

Năm yêu cầu này được nêu chính thức trong [Java SE 21 — `Object.equals(Object)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)). Đây là các quy tắc về **kết quả và ý nghĩa**, không phải mẫu câu lệnh mà trình biên dịch Java có thể kiểm tra đầy đủ. Các API sử dụng đối tượng phải **tin rằng lớp của bạn giữ đúng quy ước**. Chẳng hạn, nếu hai lượt kiểm tra cùng một cặp đối tượng mâu thuẫn nhau dù dữ liệu không đổi, đoạn mã kiểm tra trùng lặp không thể quyết định kết quả nào đáng tin.

### PHẢN XẠ (REFLEXIVE) — ĐỐI TƯỢNG PHẢI BẰNG CHÍNH NÓ

Với mọi `x` khác `null`, `x.equals(x)` phải là `true`. Ví dụ, một mã người dùng `U-100` phải bằng chính đối tượng mã đó, kể cả khi không có mã người dùng nào khác để so sánh. Nếu một đối tượng có thể không bằng chính nó, ý niệm “cùng một giá trị” sẽ mất điểm tựa.

Vì vậy nhánh kiểm tra nhanh này vừa đúng quy tắc vừa thường có chi phí thấp:

```java
if (this == other) {
    return true;
}
```

### ĐỐI XỨNG (SYMMETRIC) — HAI CHIỀU PHẢI CÙNG KẾT QUẢ

Với hai đối tượng `a`, `b`, kết quả ở hai chiều phải giống nhau. Điều này áp dụng **cả khi kết quả là `true` lẫn khi là `false`**. Nếu:

```text
a.equals(b) == true
```

thì phải có:

```text
b.equals(a) == true
```

Ngược lại, nếu `a.equals(b)` là `false`, `b.equals(a)` cũng phải là `false`. Tính đối xứng đặc biệt dễ bị phá khi lớp cha cho phép so sánh với lớp con, nhưng lớp con lại yêu cầu thêm dữ liệu mà lớp cha không biết; ta sẽ chạy một ví dụ cụ thể ở phần kế thừa.

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

Hãy xét **một luật sai**: coi hai số là bằng nhau khi chúng chỉ chênh nhau tối đa `1`.

```text
10 gần 11 → true  (chênh 1)
11 gần 12 → true  (chênh 1)
10 gần 12 → false (chênh 2)
```

Nếu một lớp đối tượng số tự định nghĩa `equals` theo luật ấy, hãy tạo ba đối tượng `a`, `b`, `c` lần lượt **chứa giá trị** `10`, `11`, `12` (đây là đối tượng, không phải biến `int` nguyên thủy). Khi đó `a.equals(b)` và `b.equals(c)` cùng đúng nhưng `a.equals(c)` sai. Kết quả vi phạm **bắc cầu** dù từng phép kiểm tra riêng lẻ nghe có vẻ hợp lý. Quy tắc “khoảng cách không quá 1” có thể dùng trong một phương thức tìm *giá trị gần nhau*, nhưng không phù hợp để định nghĩa `equals`.

Tính bắc cầu cho phép đoạn mã sử dụng gom các đối tượng tương đương thành một nhóm nhất quán, thay vì tạo một chuỗi so sánh mâu thuẫn.

### NHẤT QUÁN (CONSISTENT) — KHÔNG TỰ THAY ĐỔI KHI TRẠNG THÁI KHÔNG ĐỔI

Nếu không thông tin nào được dùng trong phép so sánh thay đổi, cùng một cặp đối tượng phải cho cùng kết quả ở những lần gọi lặp lại. Một cách cài đặt phụ thuộc vào thời gian hiện tại, giá trị ngẫu nhiên hoặc I/O bên ngoài có thể khiến cùng hai đối tượng lúc thì bằng nhau, lúc thì không.

```java
// Phản ví dụ: đây chỉ là biểu thức trả về trong một equals viết sai,
// không phải một phương thức equals hoàn chỉnh.
return Instant.now().getEpochSecond() % 2 == 0;
```

Với cùng `a` và `b`, lần gọi ở giây chẵn có thể trả `true`, lần gọi ở giây lẻ trả `false` dù cả hai đối tượng không thay đổi. Đoạn mã trên còn có thể vi phạm phản xạ, nên không phải chỉ một quy tắc bị đe dọa. **Nhất quán không có nghĩa là đối tượng bị thay đổi trạng thái liên quan vẫn phải giữ kết quả cũ**; phần triển khai dưới đây sẽ phân biệt hai tình huống.

### KHÁC NULL — KHÔNG XEM ĐỐI TƯỢNG LÀ BẰNG NULL

Với mọi tham chiếu `x` khác `null`, `x.equals(null)` phải trả `false`. `null` biểu thị **không có đối tượng để so sánh**, nên không thể là cùng giá trị logic với một đối tượng đang tồn tại. Đây là yêu cầu thứ năm, riêng với bốn tính chất của quan hệ tương đương trên các tham chiếu khác `null`.

## <a id="equals-implementation">Cách triển khai equals</a>

Sau khi biết phép bằng nhau phải giữ những gì, ta cần chuyển quy tắc nghiệp vụ thành các nhánh xử lý cụ thể. Ví dụ `UserId("U-100")` đại diện cho **mã người dùng**, nên hai `UserId` có cùng chuỗi mã phải bằng nhau dù được tạo bằng hai lệnh `new` khác nhau.

Một cách cài đặt cho đối tượng giá trị thường đi theo luồng sau:

```text
1. this và other là cùng đối tượng? → true ngay (phản xạ)
2. other có kiểu dữ liệu phù hợp?   → không thì false (bao gồm null)
3. so sánh trạng thái xác định giá trị logic → true/false
```

Ví dụ hoàn chỉnh sau có thể lưu thành `EqualsDemo.java` và chạy với Java 21:

```java
import java.util.Objects;

public class EqualsDemo {
    static final class UserId {
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

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public static void main(String[] args) {
        UserId a = new UserId("U-100");
        UserId b = new UserId("U-100");
        UserId c = new UserId("U-200");

        System.out.println(a == b);
        System.out.println(a.equals(a));
        System.out.println(a.equals(b));
        System.out.println(b.equals(a));
        System.out.println(a.equals(c));
        System.out.println(a.equals(null));
    }
}
```

Kết quả theo đúng thứ tự sáu dòng in:

```text
false
true
true
true
false
false
```

Lần lượt, `a == b` sai vì `a`, `b` là hai đối tượng được tạo riêng. `a.equals(a)` vào ngay nhánh `this == other`, còn `a.equals(b)` và `b.equals(a)` đi tiếp qua `instanceof UserId`, sau đó so hai giá trị chuỗi cùng là `"U-100"`, nên cả hai trả `true`. `a.equals(c)` qua kiểm tra kiểu nhưng chuỗi khác nhau nên trả `false`; `a.equals(null)` dừng ở kiểm tra kiểu và trả `false`.

Trong ví dụ này, `final` ngăn tạo lớp con tự thay đổi tiêu chí bằng nhau; `private final String value` và `Objects.requireNonNull` giữ mã không bị đổi sang giá trị khác hoặc `null` sau khi tạo. `Objects.equals(value, that.value)` là phép so sánh hai chuỗi theo nội dung; trong trường hợp này cả hai đều khác `null`, nhưng cách viết đó vẫn phù hợp nếu sau này lớp có trường tùy chọn.

> Ví dụ có cả `hashCode()` vì một lớp thực tế ghi đè `equals` cần bảo đảm **đối tượng bằng nhau có cùng mã băm**. Ở đây cả hai phương thức đều dựa vào `value`; cách tra cứu theo mã băm và hệ quả vi phạm quy tắc liên kết sẽ được giải thích ở hai chương kế tiếp.

### GHI ĐÈ (OVERRIDE), ĐỪNG VÔ TÌNH NẠP CHỒNG (OVERLOAD)

Chữ ký phương thức mà `java.lang.Object` quy định là (tên tham số tùy chọn, **kiểu tham số phải là `Object`**):

```java
public boolean equals(Object other)
```

Nếu chỉ viết `equals(UserId other)` (hoặc một kiểu cụ thể khác), Java sẽ tạo **phương thức khác** theo cơ chế nạp chồng. Ví dụ sau cố ý triển khai sai ý định so giá trị:

```java
import java.util.Objects;

public class OverloadDemo {
    static final class WrongUserId {
        private final String value;

        WrongUserId(String value) {
            this.value = Objects.requireNonNull(value);
        }

        // Chỉ overload, chưa override Object.equals(Object).
        public boolean equals(WrongUserId other) {
            return other != null && value.equals(other.value);
        }
    }

    public static void main(String[] args) {
        WrongUserId a = new WrongUserId("U-100");
        WrongUserId b = new WrongUserId("U-100");
        Object bAsObject = b;

        System.out.println(a.equals(b));         // true
        System.out.println(a.equals(bAsObject)); // false
    }
}
```

Ở dòng đầu, biến `b` có kiểu khai báo `WrongUserId`, nên trình biên dịch chọn phương thức `equals(WrongUserId)`; hai chuỗi mã trùng nhau và kết quả là `true`. Ở dòng hai, `bAsObject` có kiểu khai báo `Object`, nên lời gọi chọn `equals(Object)` được kế thừa từ `Object`: hai định danh khác nhau dẫn đến `false`. Các API chuẩn cũng dựa vào chữ ký `equals(Object)`, vì vậy `equals(WrongUserId)` không thể thay thế nó.

Vì vậy nên luôn dùng `@Override` ngay trên `public boolean equals(Object other)` như trong `EqualsDemo`. Đặt `@Override` trên `equals(WrongUserId)` sẽ báo lỗi biên dịch vì **không có phương thức tương ứng trong `Object` để ghi đè**; nhờ đó ta bắt được sai lầm ngay tại nơi viết lớp.

### TRƯỜNG DỮ LIỆU NÀO NÊN THAM GIA PHÉP BẰNG NHAU?

Không có quy tắc “mọi trường dữ liệu đều phải so sánh”. Hãy hỏi:

> Trường dữ liệu nào thực sự xác định hai đối tượng là cùng giá trị hoặc cùng thực thể theo nghiệp vụ?

Ví dụ `User` có thể có:

```text
userId       → định danh nghiệp vụ ổn định
displayName  → trạng thái hiển thị có thể thay đổi
lastLoginAt  → trạng thái vận hành
```

Nếu phép bằng nhau của `User` đại diện định danh nghiệp vụ, việc đưa `lastLoginAt` vào phép so sánh có thể làm cùng một người dùng trở thành “khác nhau” chỉ vì vừa đăng nhập. Ngược lại, đối tượng giá trị như `Money(100, "USD")` thường phải so **cả số tiền lẫn đơn vị tiền**; chỉ so số `100` sẽ nhầm `100 USD` với `100 VND`. Tiêu chí nào đúng phụ thuộc giá trị/thực thể mà kiểu dữ liệu đại diện, chứ không phụ thuộc vào số lượng trường của lớp.

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

Ví dụ, ban đầu hai `User` có cùng email `"a@example.com"` nên bằng nhau. Sau khi chỉ một đối tượng đổi email thành `"b@example.com"`, kết quả `false` **không vi phạm yêu cầu nhất quán** vì thông tin được dùng để so sánh đã thay đổi. Rủi ro thiết kế nằm ở việc **ý nghĩa đối tượng đang đại diện cho giá trị/thực thể logic nào có thể thay đổi theo thời gian**, khiến việc suy luận, loại bỏ phần tử trùng, lưu đệm và sử dụng đối tượng làm khóa về sau khó giữ ổn định.

Nên ưu tiên trạng thái xác định bằng nhau đủ ổn định trong suốt khoảng thời gian đoạn mã sử dụng dựa vào ý nghĩa định danh/giá trị đó. Chương `equals/hashCode` phía sau sẽ cho thấy lỗi đặc biệt dễ quan sát khi trạng thái dùng cho phép bằng nhau và mã băm bị thay đổi sau khi đối tượng đã được đưa vào cấu trúc dữ liệu dựa trên mã băm.

### `instanceof` HAY `getClass()`?

Hai lựa chọn thể hiện hai chính sách **kiểm tra kiểu tại thời điểm chạy** khác nhau. `instanceof` chấp nhận đối tượng có kiểu tương thích (kể cả lớp con), còn `getClass()` yêu cầu hai đối tượng có **cùng lớp thực tế chính xác**:

```java
if (!(other instanceof UserId that)) return false;
```

Trong `UserId` đã khai báo `final`, không thể tồn tại lớp con của `UserId`, nên phép kiểm tra trên vừa ngắn vừa không mở thêm trường hợp kế thừa. Nếu dùng một lớp cha không `final`, kiểm tra `instanceof Parent` có thể nhận cả `Child` và cần xem `Child.equals` có giữ cùng chính sách bằng nhau hay không. Ngược lại:

```java
if (other == null || getClass() != other.getClass()) return false;
```

yêu cầu đúng chính xác lớp thực tế tại thời điểm chạy; sau kiểm tra thành công, mã nguồn có thể ép kiểu và so những trường liên quan. Nó sẽ xem một `Parent` và `Child` là không bằng nhau ngay cả khi những trường chung giống hệt nhau.

Không có một lựa chọn máy móc đúng cho mọi cây kế thừa. `getClass()` giúp tránh **một số** lỗi vì lớp cha không nhận lớp con, nhưng cũng thay đổi chính sách: hai kiểu có quan hệ cha–con sẽ không bao giờ bằng nhau. `instanceof` vẫn phù hợp khi mọi kiểu con được chấp nhận dùng cùng một ý nghĩa bằng nhau. Dù chọn cách nào, tính đối xứng và bắc cầu phải giữ được trong **toàn bộ** hệ phân cấp, không chỉ trong một lớp. Phần tiếp theo chỉ ra rủi ro qua code.

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

Khi cần so sánh an toàn với `null`, `Objects.equals(a, b)` là một phương thức hỗ trợ hữu ích:

```java
Objects.equals(null, null);             // true: cả hai đều không có đối tượng
Objects.equals("U-100", null);          // false
```

Lưu ý `Objects.equals(null, null)` trả `true` vì **hai tham số cùng `null`**; điều đó không mâu thuẫn với yêu cầu `x.equals(null) == false` khi `x` là đối tượng khác `null`. `Objects.equals` kiểm tra tham chiếu an toàn trước khi có thể gọi `equals` trên đối tượng.

## <a id="equals-inheritance-risk">Phép bằng nhau và rủi ro khi kế thừa</a>

Kế thừa làm phép bằng nhau khó thiết kế hơn khi lớp con thêm trạng thái mới có ý nghĩa đối với phép so sánh. Lớp cha có thể nói “chỉ cần tọa độ giống nhau”, còn lớp con nói “tọa độ **và** màu sắc đều phải giống nhau”. Cả hai quy tắc nghe riêng lẻ hợp lý, nhưng chúng sẽ xung đột nếu lớp cha vẫn chấp nhận lớp con là một đối tượng bằng nó.

Giả sử lớp cha chỉ xét `x`, còn lớp con muốn xét thêm `color`:

Đây là ví dụ đầy đủ có thể lưu thành `InheritanceDemo.java` để chạy với Java 21. Cả hai lớp cố ý trả mã băm dựa trên `x` để minh họa **lỗi nằm ở `equals`**, không phải do mã băm khác nhau; hai màu khác nhau trùng mã băm vẫn được phép.

```java
import java.util.Objects;

public class InheritanceDemo {
    static class Point {
        final int x;

        Point(int x) {
            this.x = x;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Point p && x == p.x;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(x);
        }
    }

    static final class ColoredPoint extends Point {
        final String color;

        ColoredPoint(int x, String color) {
            super(x);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ColoredPoint p
                    && x == p.x
                    && Objects.equals(color, p.color);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(x);
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1);
        ColoredPoint red = new ColoredPoint(1, "red");

        System.out.println(plain.equals(red)); // true
        System.out.println(red.equals(plain)); // false
    }
}
```

Với hai đối tượng trên, luồng kiểm tra lần lượt là:

```text
plain.equals(red)
→ Point.equals gọi "other instanceof Point"
→ red là ColoredPoint, đồng thời cũng là Point: true
→ x của cả hai bằng 1: true

red.equals(plain)
→ ColoredPoint.equals gọi "other instanceof ColoredPoint"
→ plain chỉ là Point: false
```

Kết quả in ra là `true` rồi `false` đối với **cùng một cặp đối tượng**. Lỗi đối xứng xuất phát từ việc mỗi chiều dùng **một tập kiểu chấp nhận khác nhau**, chứ không xuất phát từ thứ tự gọi ngẫu nhiên hoặc từ một thư viện Java nào đó.

### VÌ SAO MỘT CÁCH "VÁ" ĐỐI XỨNG CÒN PHÁ BẮC CẦU?

Có người cố sửa `ColoredPoint.equals` bằng cách cho phép so với `Point` thông thường theo `x` mà bỏ qua `color`, đồng thời vẫn so màu khi cả hai là `ColoredPoint`. Nếu **thay riêng phương thức** `ColoredPoint.equals(Object)` của ví dụ trên thành phiên bản sau:

```java
@Override
public boolean equals(Object other) {
    if (other instanceof ColoredPoint p) {
        return x == p.x && Objects.equals(color, p.color);
    }
    return other instanceof Point p && x == p.x;
}
```

và tạo `Point plain = new Point(1)`, `ColoredPoint red = new ColoredPoint(1, "red")`, `ColoredPoint blue = new ColoredPoint(1, "blue")`, ta nhận được:

```text
red.equals(plain)  → true   (cùng x; bỏ qua color vì plain chỉ là Point)
plain.equals(blue) → true   (Point chỉ so x)
red.equals(blue)   → false  (cùng là ColoredPoint nhưng khác color)
```

Ta đã khiến so sánh với `Point` thông thường đúng ở cả hai chiều, nhưng một đối tượng trung gian `plain` nay nối hai `ColoredPoint` **không bằng nhau**. Đây là lỗi bắc cầu. Chỉ vá từng trường hợp `instanceof` mà không chọn **một chính sách bằng nhau chung** cho hệ phân cấp có thể chuyển lỗi từ đối xứng sang bắc cầu.

### BÀI HỌC THIẾT KẾ

Với ngữ nghĩa giá trị phức tạp, hãy cân nhắc:

- kết hợp đối tượng (composition) thay vì mở kế thừa tùy ý;
- lớp giá trị `final`;
- hệ phân cấp `sealed`/đóng với chính sách bằng nhau rõ ràng;
- phép bằng nhau dựa trên lớp nếu nghiệp vụ yêu cầu đúng chính xác lớp thực tế tại thời điểm chạy.

Trong trường hợp chọn so đúng lớp thực tế, cả `Point` và `ColoredPoint` phải dùng một chính sách kiểu thống nhất (`getClass()`), để `Point` và `ColoredPoint` trả `false` ở **cả hai chiều**. Nếu nghiệp vụ thật sự muốn chúng bằng nhau, lớp con không thể tùy ý thêm một điều kiện `color` khiến các cặp đối tượng trong cùng nhóm cho kết quả mâu thuẫn. Lựa chọn composition hoặc lớp `final` thường giảm đáng kể số trường hợp phải kiểm tra.

Mục tiêu không phải chọn một mẫu `equals` duy nhất, mà thiết kế một **quan hệ bằng nhau nhất quán** trong toàn bộ tập đối tượng có thể xuất hiện. Một bài tự kiểm tra hữu ích là đặt ba đối tượng gồm lớp cha, lớp con màu đỏ và lớp con màu xanh: hãy dự đoán cả sáu phép so hai chiều và xem chúng có giữ đối xứng, bắc cầu không.

### CHUYỂN TIẾP

`equals` đã trả lời “hai đối tượng có cùng giá trị logic không?”. Cấu trúc dữ liệu dựa trên mã băm còn cần một tín hiệu có chi phí thấp hơn để **thu hẹp tập ứng viên trước khi gọi `equals`**. Đó là vai trò của `hashCode`.
