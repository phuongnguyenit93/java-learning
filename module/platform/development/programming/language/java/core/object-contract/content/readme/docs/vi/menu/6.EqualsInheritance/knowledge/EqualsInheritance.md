<a id="back-to-top"></a>

# Equals và kế thừa

## Menu
- [Mở rộng lớp có equals() và nguy cơ mất đối xứng](#equals-inheritance-001)
- [Thêm trường giá trị ở lớp con và nguy cơ mất bắc cầu](#equals-inheritance-002)
- [Kiểm tra kiểu bằng instanceof hoặc getClass()](#equals-inheritance-003)
- [Ảnh hưởng của lựa chọn kiểm tra kiểu tới quan hệ lớp cha, lớp con](#equals-inheritance-004)
- [Gọi super.equals() và những điều việc đó không tự bảo đảm](#equals-inheritance-005)
- [Lớp final hoặc phương thức equals() final để giới hạn thay đổi hợp đồng](#equals-inheritance-006)
- [Dùng composition khi mở rộng giá trị qua kế thừa làm hợp đồng khó giữ](#equals-inheritance-007)
- [Mẫu canEqual(): mục đích, điều kiện phối hợp và giới hạn](#equals-inheritance-008)
- [Kiểm tra các cặp và bộ ba đối tượng ở nhiều mức kế thừa](#equals-inheritance-009)

## <a id="equals-inheritance-001">Mở rộng lớp có equals() và nguy cơ mất đối xứng</a>

<details>
<summary>Xem chi tiết</summary>

# Mở rộng lớp có `equals()` và nguy cơ mất đối xứng

## Mục tiêu

Bài này tập trung vào một tình huống dễ mắc lỗi khi thiết kế **quan hệ bằng nhau của đối tượng** trong Java: lớp cha đã ghi đè `equals(Object)`, còn lớp con kế thừa hoặc thay đổi định nghĩa bằng nhau, đặc biệt khi lớp con bổ sung thuộc tính cần so sánh. Bạn sẽ hiểu vì sao một cách triển khai có vẻ hợp lý lại vi phạm **tính đối xứng** (symmetry), vì sao cách vá ngây thơ có thể làm hỏng **tính bắc cầu** (transitivity), và cách lựa chọn thiết kế an toàn.

Trong ví dụ, một điểm có tọa độ `(x, y)` và một điểm có thêm màu sắc sẽ giúp nhìn thấy mâu thuẫn giữa “bằng nhau theo tọa độ” và “bằng nhau theo tọa độ lẫn màu”.

## 1. Hợp đồng của `equals(Object)`

`equals(Object)` là phương thức của `java.lang.Object`. Nếu ghi đè nó để định nghĩa **bằng nhau về giá trị/ý nghĩa**, chương trình phải duy trì những tính chất sau đối với các tham chiếu không `null`:

| Tính chất | Phát biểu | Ví dụ |
| --- | --- | --- |
| **Phản xạ** (reflexive) | `x.equals(x)` phải là `true` | Một đối tượng bằng chính nó |
| **Đối xứng** (symmetric) | `x.equals(y)` và `y.equals(x)` phải cùng kết quả | Không thể chỉ một phía công nhận “bằng nhau” |
| **Bắc cầu** (transitive) | Nếu `x.equals(y)` và `y.equals(z)` đều đúng thì `x.equals(z)` phải đúng | Quan hệ bằng nhau không tự mâu thuẫn qua đối tượng trung gian |
| **Nhất quán** (consistent) | Khi thông tin dùng để so sánh không thay đổi, gọi lại cho cùng kết quả | Không thay đổi kết quả tùy ý giữa hai lần gọi |
| **So với `null`** | `x.equals(null)` phải là `false` | Không một đối tượng nào bằng `null` |

**Ràng buộc gắn với `hashCode()`:** Nếu `a.equals(b)` trả về `true` thì `a.hashCode() == b.hashCode()` **bắt buộc** đúng. Chiều đảo lại **không bắt buộc**: hai đối tượng khác nhau vẫn có thể trùng hash. Nếu ghi đè `equals`, thường phải ghi đè `hashCode` đồng bộ; nếu không, việc tra cứu trong `HashMap`, `HashSet` có thể sai về mặt hành vi mong đợi.

Hợp đồng này áp dụng **xuyên các lớp** khi một lời gọi `equals` được cho phép so sánh đối tượng lớp cha với lớp con. Hợp đồng không biến mất chỉ vì các lớp nằm trong quan hệ kế thừa.

## 2. Gốc rễ của bài toán: lớp cha và lớp con muốn so sánh khác nhau

Giả sử có hai kiểu:

```text
Point(x, y)
   ▲
   │ extends
   │
ColoredPoint(x, y, color)
```

Với `Point`, hai điểm có cùng `x` và `y` được xem là bằng nhau. Với `ColoredPoint`, người thiết kế có thể muốn màu sắc cũng tham gia quyết định bằng nhau.

Hãy xét:

```text
p = Point(1, 2)
r = ColoredPoint(1, 2, "red")
```

- Nếu gọi `p.equals(r)`, lớp cha có thể chỉ xem `r` như một `Point`, rồi kết luận **bằng nhau** dựa trên tọa độ.
- Nếu gọi `r.equals(p)`, lớp con có thể yêu cầu đối phương **cũng phải có màu**, rồi kết luận **khác nhau**.
- Cùng một cặp đối tượng nhưng hai chiều so sánh trả kết quả khác nhau: **vi phạm đối xứng**.

Điều này bắt nguồn từ **chênh lệch thông tin**: `Point` không có thuộc tính `color` để so sánh, trong khi `ColoredPoint` lại coi `color` là một phần của danh tính theo giá trị. Việc `ColoredPoint` kế thừa từ `Point` không tự giải quyết được sự khác biệt về định nghĩa bằng nhau.

## 3. Ví dụ chạy được: vỡ đối xứng và cách vá làm vỡ bắc cầu

**Tệp `BrokenEqualsDemo.java`**, chạy bằng Java 17+:

```java
import java.util.Objects;

public class BrokenEqualsDemo {
    static class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Point other)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    // Cố tình SAI để minh họa mất đối xứng.
    static final class ColoredPoint extends Point {
        final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ColoredPoint other)) {
                return false;
            }
            return super.equals(other) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    // Cố tình SAI: cố cho lớp con bằng Point thường.
    static final class LenientColoredPoint extends Point {
        final String color;

        LenientColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof LenientColoredPoint other) {
                return super.equals(other) && color.equals(other.color);
            }
            return obj instanceof Point && super.equals(obj);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        ColoredPoint red = new ColoredPoint(1, 2, "red");

        System.out.println("plain.equals(red): " + plain.equals(red));
        System.out.println("red.equals(plain): " + red.equals(plain));

        LenientColoredPoint lenientRed =
                new LenientColoredPoint(1, 2, "red");
        LenientColoredPoint lenientBlue =
                new LenientColoredPoint(1, 2, "blue");

        System.out.println(
                "plain.equals(lenientRed): " + plain.equals(lenientRed));
        System.out.println(
                "lenientRed.equals(plain): " + lenientRed.equals(plain));
        System.out.println(
                "plain.equals(lenientBlue): " + plain.equals(lenientBlue));
        System.out.println(
                "lenientRed.equals(lenientBlue): "
                        + lenientRed.equals(lenientBlue));
    }
}
```

Biên dịch và chạy:

```bash
javac BrokenEqualsDemo.java
java BrokenEqualsDemo
```

**Đầu ra chính xác:**

```text
plain.equals(red): true
red.equals(plain): false
plain.equals(lenientRed): true
lenientRed.equals(plain): true
plain.equals(lenientBlue): true
lenientRed.equals(lenientBlue): false
```

### 3.1. Vì sao `plain.equals(red)` là `true`?

Phương thức được gọi là `Point.equals` vì biến nhận lời gọi `plain` đang trỏ tới đối tượng có lớp thực tế `Point`. Biểu thức:

```java
obj instanceof Point other
```

trả về `true` khi `obj` là `Point` **hoặc đối tượng của lớp con `Point`**. Vì `ColoredPoint` là một `Point`, phép so sánh chỉ xét `x`, `y` và trả `true`. Màu không được kiểm tra.

Khi đảo chiều `red.equals(plain)`, JVM gọi phương thức ghi đè trong `ColoredPoint`. Kiểm tra `obj instanceof ColoredPoint` trả về `false` vì `plain` là `Point` thuần. Kết quả thành `false`. Đây là vi phạm **đối xứng**, không phải hành vi Java ngẫu nhiên.

### 3.2. Vì sao “cho phép so sánh với Point thường” chưa giải quyết được?

Lớp `LenientColoredPoint` cố xử lý trường hợp so với `Point` bằng cách chỉ kiểm tra tọa độ. Nhờ đó:

```text
lenientRed.equals(plain)   = true
plain.equals(lenientRed)   = true
```

Nhưng `lenientRed` và `lenientBlue` có màu khác nhau, nên khi so hai đối tượng cùng lớp con, `equals` trả về `false`. Ta có:

```text
lenientRed  ==(equals)==  plain
plain       ==(equals)==  lenientBlue
lenientRed  !=(equals)!=  lenientBlue
```

Từ `lenientRed.equals(plain) == true` và `plain.equals(lenientBlue) == true`, tính **bắc cầu** yêu cầu `lenientRed.equals(lenientBlue) == true`. Chương trình lại trả `false`: phương án “vá đối xứng” này làm hỏng **bắc cầu**.

**Lưu ý:** Hai lớp con trong ví dụ được cố tình viết sai để chứng minh lỗi; **không sao chép hai cách triển khai đó vào mã sản phẩm**.

### 3.3. `hashCode` cũng có thể mất hợp đồng

Trong ví dụ trên, `Point` băm theo tọa độ, còn lớp con băm thêm màu. Nhưng một số chiều `equals` lại coi `Point` và lớp con là bằng nhau. Khi bằng nhau mà mã băm khác nhau, hợp đồng `equals/hashCode` bị vi phạm. Không cần so sánh hoặc đoán các con số hash cụ thể để nhận ra vấn đề: **tập trường tham gia bình đẳng và tập trường tham gia băm phải tương thích cho mọi cặp được coi là bằng nhau**.

Điều này có thể tạo lỗi khó tái hiện khi dùng làm khóa `HashMap` hoặc phần tử `HashSet`: cấu trúc băm dựa vào `hashCode` để tìm vùng ứng viên trước khi gọi `equals`. Hơn nữa, do `equals` không đối xứng, ngay cả thao tác dùng `equals` trực tiếp cũng không có nghĩa ổn định theo thứ tự đối tượng.

## 4. Vì sao chỉ đổi `instanceof` thành `getClass()` vẫn cần quyết định thiết kế?

Có hai kiểu kiểm tra lớp thường gặp:

```java
// Nhận cả đối tượng lớp con nếu nó là một Point:
obj instanceof Point

// Chỉ nhận đối tượng có đúng lớp runtime của bên nhận:
obj != null && getClass() == obj.getClass()
```

- `instanceof` cho phép **bao gồm các lớp con**. Có thể phù hợp nếu mọi lớp con phải có **cùng một định nghĩa bằng nhau** và không thêm trường có ý nghĩa trong `equals`.
- `getClass()` yêu cầu **đúng cùng lớp runtime**. Có thể giúp tách quan hệ bằng nhau giữa các kiểu, miễn là **mọi lớp liên quan** triển khai nhất quán.
- Với `getClass()`, một `Point` và một `ColoredPoint` luôn không bằng nhau, kể cả tọa độ trùng nhau. Đây là **quyết định ngữ nghĩa** của API, không chỉ là mẹo đổi cú pháp.
- Khi dùng các lớp proxy do framework sinh ra, kiểm tra chính xác lớp runtime có thể chặt hơn mong muốn. Cần xác định rõ chính sách bằng nhau theo kiểu nghiệp vụ, tránh mặc định `getClass()` là đáp án cho mọi trường hợp.

Nếu lớp con được phép thay đổi ngữ nghĩa `equals`, người thiết kế phải phối hợp **toàn bộ phân cấp**, gồm `equals` và `hashCode`. Vá duy nhất một nhánh thường không đủ.

## 5. Cách an toàn A: ưu tiên composition khi lớp con thêm trạng thái bình đẳng

Khi màu thực sự là một phần định nghĩa bằng nhau, giải pháp rõ ràng thường là **không kế thừa một lớp đã có chính sách `equals` không tương thích**. Cho kiểu điểm có tọa độ bất biến, rồi tạo kiểu điểm màu **chứa** điểm tọa độ qua composition.

**Tệp `CompositionEqualityDemo.java`:**

```java
import java.util.Objects;

public class CompositionEqualityDemo {
    static final class Position {
        private final int x;
        private final int y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Position other)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPoint {
        private final Position position;
        private final String color;

        ColoredPoint(Position position, String color) {
            this.position = Objects.requireNonNull(position);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ColoredPoint other)) {
                return false;
            }
            return position.equals(other.position)
                    && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(position, color);
        }
    }

    public static void main(String[] args) {
        Position p1 = new Position(1, 2);
        Position p2 = new Position(1, 2);
        ColoredPoint red1 = new ColoredPoint(p1, "red");
        ColoredPoint red2 = new ColoredPoint(p2, "red");
        ColoredPoint blue = new ColoredPoint(p1, "blue");

        System.out.println("p1.equals(p2): " + p1.equals(p2));
        System.out.println("red1.equals(red2): " + red1.equals(red2));
        System.out.println(
                "red1.hashCode() == red2.hashCode(): "
                        + (red1.hashCode() == red2.hashCode()));
        System.out.println("red1.equals(blue): " + red1.equals(blue));
        System.out.println("red1.equals(p1): " + red1.equals(p1));
    }
}
```

```bash
javac CompositionEqualityDemo.java
java CompositionEqualityDemo
```

**Đầu ra chính xác:**

```text
p1.equals(p2): true
red1.equals(red2): true
red1.hashCode() == red2.hashCode(): true
red1.equals(blue): false
red1.equals(p1): false
```

Đặc điểm của thiết kế:

1. `Position` và `ColoredPoint` là hai lớp `final`, không có lớp con bất ngờ kế thừa và thay đổi chính sách so sánh.
2. Hai `Position` bằng nhau khi tọa độ giống nhau, hai `ColoredPoint` bằng nhau khi **cả position và color** bằng nhau.
3. Một `ColoredPoint` không tự nhận là `Position`: không tồn tại tình huống cha đồng ý, con từ chối.
4. Cả hai kiểu bất biến trong ví dụ: các trường `final` và những thành phần được giữ (`Position`, `String`) đều bất biến; nhờ vậy giá trị băm không thay đổi sau khi đưa vào tập hợp băm.
5. `hashCode` của `ColoredPoint` dùng đúng các thành phần tham gia `equals`; hai đối tượng bằng nhau chắc chắn có cùng mã băm.

Nếu mục tiêu là các kiểu dữ liệu giá trị ngắn gọn trên Java 17+, có thể dùng `record`; các thành phần record tham gia `equals` và `hashCode` mặc định:

```java
record Position(int x, int y) {}
record ColoredPoint(Position position, String color) {}
```

Đoạn record này minh họa cách khai báo độc lập (có thể đặt ở phạm vi tệp/package phù hợp). Mỗi record mặc định là `final` nên không thể được kế thừa để lén đổi chính sách so sánh. Nếu thành phần record là đối tượng mutable thì bản thân record **không tự sao chép sâu hay làm bất biến thành phần đó**.

## 6. Cách an toàn B: giữ kế thừa nhưng chủ động cấm bằng nhau xuyên lớp

Trong những hệ thống đã có kế thừa và muốn `Point` **không bằng** bất kỳ lớp con nào, có thể áp dụng kiểm tra lớp runtime chính xác ngay ở lớp cha; lớp con thêm trường thì ghi đè `equals` và `hashCode` nhất quán.

**Tệp `StrictClassEqualityDemo.java`:**

```java
import java.util.Objects;

public class StrictClassEqualityDemo {
    static class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Point other = (Point) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), x, y);
        }
    }

    static final class ColoredPoint extends Point {
        final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            ColoredPoint other = (ColoredPoint) obj;
            return color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        Point red1 = new ColoredPoint(1, 2, "red");
        Point red2 = new ColoredPoint(1, 2, "red");
        Point blue = new ColoredPoint(1, 2, "blue");

        System.out.println("plain.equals(red1): " + plain.equals(red1));
        System.out.println("red1.equals(plain): " + red1.equals(plain));
        System.out.println("red1.equals(red2): " + red1.equals(red2));
        System.out.println("red1.equals(blue): " + red1.equals(blue));
        System.out.println(
                "equal red points have equal hashes: "
                        + (red1.hashCode() == red2.hashCode()));
    }
}
```

```bash
javac StrictClassEqualityDemo.java
java StrictClassEqualityDemo
```

**Đầu ra chính xác:**

```text
plain.equals(red1): false
red1.equals(plain): false
red1.equals(red2): true
red1.equals(blue): false
equal red points have equal hashes: true
```

Giải thích:

- Trong `Point.equals`, `getClass()` trả lớp thực tế của đối tượng đang nhận lời gọi. Một `Point` thuần và một `ColoredPoint` luôn bị coi là khác lớp, **từ cả hai chiều**.
- Trong `ColoredPoint.equals`, lời gọi `super.equals(obj)` kiểm tra cùng lớp runtime và tọa độ trước. Vì kiểm tra đó thành công chỉ khi cả hai là `ColoredPoint`, ép kiểu `(ColoredPoint) obj` là an toàn.
- Lớp con kiểm tra thêm `color` và bổ sung `color` vào `hashCode`. Các đối tượng được xem là bằng nhau theo cùng chính sách.
- `Point.hashCode` đưa cả lớp runtime vào công thức băm. Điều này phù hợp với quyết định rằng các lớp khác nhau không được coi là bằng nhau. Hai đối tượng khác lớp vẫn có thể tình cờ trùng hash; không sao.

**Giới hạn thiết kế:** Cách này chấp nhận việc kiểu cha và kiểu con **không bằng nhau về giá trị** dù cùng tọa độ. Có thể không phù hợp nếu người gọi mong quan hệ đa hình “mọi Point có tọa độ giống nhau đều bằng nhau”. Không nên chọn cách này mà chưa thống nhất ngữ nghĩa API. Ví dụ sử dụng lớp con `final` để không có lớp cháu tiếp tục bổ sung trạng thái bình đẳng.

## 7. Nếu bắt buộc phải có bằng nhau đa hình giữa cha và con thì sao?

Có những trường hợp quan hệ “là một” (`is-a`) đi cùng một định nghĩa bằng nhau có chủ ý: mọi đối tượng trong một phân cấp đều được so sánh theo **cùng tập thuộc tính** do lớp cha xác định. Khi đó lớp con **không được tự ý thêm điều kiện** làm thay đổi quy tắc cho chỉ một hướng so sánh.

Ví dụ: nếu chỉ tọa độ xác định bằng nhau cho **toàn bộ phân cấp**, màu chỉ là thuộc tính hiển thị thì có thể chấp nhận:

```text
Point(1, 2) bằng ColoredPoint(1, 2, "red")
ColoredPoint(1, 2, "red") bằng ColoredPoint(1, 2, "blue")
```

Nhưng nếu người dùng thực sự kỳ vọng hai màu khác nhau là hai giá trị khác nhau, chính sách này **không đáp ứng yêu cầu miền nghiệp vụ**; hãy thiết kế composition hoặc tách kiểu thay vì giả vờ màu không quan trọng.

Một số phân cấp phức tạp dùng mô thức `canEqual` để phối hợp sự đồng ý giữa hai phía. Mô thức đó đòi hỏi thiết kế và kiểm thử xuyên toàn bộ lớp cha/lớp con, không phải chỉ thêm một phương thức là tự động bảo đảm cả đối xứng lẫn bắc cầu. Trong bài toán điểm có thêm màu, composition hoặc chính sách tách lớp rõ ràng thường dễ kiểm chứng hơn.

## 8. Quy trình rà soát `equals` khi có kế thừa

Khi đọc hoặc viết một lớp kế thừa lớp đã ghi đè `equals`, hãy kiểm tra tuần tự:

1. **Xác định ý nghĩa của bằng nhau:** Đây là đối tượng giá trị (value object) hay đối tượng có danh tính nghiệp vụ (entity)? Thuộc tính nào thực sự tham gia so sánh?
2. **Xác định phạm vi so sánh:** Lớp cha có dùng `instanceof` cho phép con, hay dùng `getClass` yêu cầu cùng lớp thực tế?
3. **Xem mọi lớp con có trạng thái mới không:** Trạng thái ấy có thuộc định nghĩa bằng nhau hay chỉ là metadata/trình bày?
4. **Thử đủ hai chiều:** `a.equals(b)` và `b.equals(a)`, với `a` là cha, `b` là con. Không chỉ thử hai thể hiện cùng một lớp.
5. **Thử bộ ba:** Với các đối tượng ở cha/con và hai lớp con khác nhau, kiểm tra quy tắc bắc cầu; chính các bộ ba là nơi cách vá ngây thơ thường thất bại.
6. **Rà soát `hashCode` và tính bất biến:** Mọi cặp `equals == true` phải có hash bằng nhau. Tránh thay đổi trường dùng băm khi đối tượng đang làm phần tử `HashSet` hoặc khóa `HashMap`.
7. **Kiểm tra `null`, chính mình và các giá trị biên:** `a.equals(a)`, `a.equals(null)`, và trường hợp thuộc tính thành phần có thể `null`.

Một bảng kiểm thử ý nghĩa cho ví dụ gồm `Point p`, `ColoredPoint red` và `ColoredPoint blue`:

| Phép thử | Triển khai sai ở mục 3 | Chính sách tách lớp mục 6 |
| --- | --- | --- |
| `p.equals(red)` | `true` | `false` |
| `red.equals(p)` | `false` (vi phạm đối xứng) | `false` |
| `red.equals(blue)` khi màu khác | `false` | `false` |
| Hai ColoredPoint cùng tọa độ và cùng màu | `true` | `true` |
| Hai đối tượng `equals == true` có cùng `hashCode`? | Không được bảo đảm cho mọi cặp | Được bảo đảm theo triển khai đã cho |

*Bảng dùng `ColoredPoint` (bản giới hạn loại), không dùng `LenientColoredPoint`, ở mục 3.*

## 9. Những lỗi suy luận phổ biến

- **“Dùng `instanceof` là luôn tốt vì hỗ trợ đa hình.”** Không hẳn: `instanceof` trong lớp cha cho phép so với lớp con, nhưng lớp con có thể có quy tắc giá trị chặt hơn. Tính đa hình của kiểu không đồng nghĩa mọi đối tượng của các kiểu đó phải bằng nhau.
- **“Thêm `color` vào `ColoredPoint.equals` là đủ.”** Không đủ: lớp cha còn có thể trả `true` ở chiều ngược lại, phá đối xứng.
- **“Cho lớp con bằng lớp cha khi tọa độ trùng nhau thì đã sửa xong.”** Chưa chắc: hai lớp con khác màu có thể cùng bằng một đối tượng cha nhưng lại không bằng nhau, phá bắc cầu.
- **“`hashCode` độc lập với `equals`.”** Sai: các đối tượng bằng nhau phải có cùng hash. Hash khác nhau cho một cặp bằng nhau làm cấu trúc băm hoạt động không đáng tin cậy.
- **“Đổi toàn bộ sang `getClass()` luôn là chuẩn.”** Không đúng với mọi mô hình, đặc biệt khi API cần bình đẳng đa hình hoặc xử lý proxy. Đây là lựa chọn thiết kế có hậu quả cụ thể.
- **“Đặt `final` cho trường `color` là đủ.”** `final` giữ nguyên tham chiếu/trường, nhưng không khắc phục mâu thuẫn bình đẳng giữa lớp cha với lớp con.

## Tự kiểm tra

**Câu 1.** Lớp cha `Vehicle` kiểm tra `obj instanceof Vehicle` và so sánh `id`; lớp con `ElectricVehicle` đòi đối phương là `ElectricVehicle` rồi so thêm `batteryModel`. Có nguy cơ nào?  
**Trả lời:** Một `Vehicle` và một `ElectricVehicle` cùng `id` có thể được lớp cha coi là bằng nhau trong khi lớp con từ chối, làm mất đối xứng.

**Câu 2.** Nếu lớp con sửa `equals` để trả `true` khi so với lớp cha cùng `id`, và lại so thêm `batteryModel` khi gặp lớp con khác, vấn đề gì có thể xảy ra?  
**Trả lời:** Hai lớp con có `batteryModel` khác nhau có thể đều bằng một đối tượng cha nhưng không bằng nhau với nhau, vi phạm bắc cầu.

**Câu 3.** Vì sao không thể chỉ thêm `color` vào `hashCode` và bỏ qua `equals`?  
**Trả lời:** Nếu `equals` của lớp cha còn công nhận hai đối tượng bằng nhau dựa trên tọa độ, hash của lớp con có thể khác hash của lớp cha, vi phạm yêu cầu “bằng nhau thì cùng hash”.

**Câu 4.** Khi nào composition phù hợp hơn inheritance?  
**Trả lời:** Khi đối tượng mở rộng có định nghĩa bình đẳng mới gồm trạng thái bổ sung, mà định nghĩa ấy không thể đồng thời giữ tính đối xứng/bắc cầu với chính sách của lớp cha.

## Kết luận

**Hợp đồng `equals` có hiệu lực trên mọi đối tượng được so sánh, kể cả khi chúng thuộc các lớp trong cùng phân cấp kế thừa.** Lớp cha so bằng `instanceof` và lớp con thêm điều kiện bình đẳng rất dễ gây **mất đối xứng**; việc cố nới điều kiện so với lớp cha có thể chuyển thành **mất bắc cầu**. Hãy quyết định trước ngữ nghĩa bằng nhau, dùng composition và lớp `final` cho value object khi phù hợp, hoặc thống nhất chính sách cùng lớp runtime trong toàn phân cấp. Luôn kiểm tra đồng thời `equals`, `hashCode` và cả hai chiều so sánh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-002">Thêm trường giá trị ở lớp con và nguy cơ mất bắc cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm trường giá trị ở lớp con và nguy cơ mất tính bắc cầu của equals

## 1. Vấn đề: lớp con có thêm một phần của “giá trị”

Xét lớp cha **Point** lưu tọa độ x, y và định nghĩa hai điểm bằng nhau nếu chúng có cùng tọa độ. Sau đó ta kế thừa thành **ColoredPoint**, bổ sung trường **color**. Một câu hỏi thiết kế tưởng chừng đơn giản xuất hiện:

> **Point(1, 2)** có bằng **ColoredPoint(1, 2, "red")** không?

Nếu **có**, vì Point chỉ quan tâm tọa độ, thì một ColoredPoint màu đỏ và một ColoredPoint màu xanh có cùng tọa độ có thể đều bằng Point gốc. Nhưng nếu hai ColoredPoint phải xét thêm màu, chúng lại **không bằng nhau**. Khi đó quan hệ bằng nhau không còn bắc cầu.

Nếu **không**, lớp con cần đảm bảo **cả hai chiều** so sánh giữa Point và ColoredPoint đều trả về false. Nếu Point dùng **instanceof Point** trong equals mà ColoredPoint lại tự loại đối tượng Point, tính đối xứng sẽ bị phá vỡ.

Đây là xung đột giữa **kế thừa kiểu** (ColoredPoint là một Point) và **ngữ nghĩa bằng nhau theo giá trị** (ColoredPoint có thể có nhiều trường cấu thành giá trị hơn Point). Java cho phép override equals nhưng **không tự bảo đảm** hợp đồng equals khi cây kế thừa trở nên phức tạp.

### Phân biệt trường giá trị và trường chỉ phục vụ hiển thị

Không phải mọi trường mới đều cần tham gia equals.

- Nếu color chỉ là một thuộc tính trang trí, không ảnh hưởng định danh/giá trị logic, cả Point và ColoredPoint có thể tiếp tục so sánh theo x, y. Khi đó **hai ColoredPoint khác màu vẫn phải bằng nhau** theo quy tắc đã chọn.
- Nếu màu là thành phần của giá trị, thì hai ColoredPoint khác màu **phải khác nhau**. Khi đó không thể đồng thời cho mỗi ColoredPoint bằng Point cùng tọa độ và giữ một quan hệ equals hợp lệ.
- Nếu điều kiện bằng nhau đến từ khóa nghiệp vụ ổn định, chẳng hạn id, một trường mới như tên hiển thị có thể được bỏ qua có chủ đích. Quyết định này thuộc về **ngữ nghĩa miền dữ liệu**, không chỉ về cú pháp Java.

Bài này tập trung vào trường hợp **lớp con thực sự thêm một trường có ý nghĩa đối với phép so sánh giá trị**.

## 2. Hợp đồng Object.equals cần được giữ trong kế thừa

Đối với các tham chiếu khác null, equals phải thỏa năm yêu cầu chủ yếu:

| Tính chất | Phát biểu | Hệ quả khi phân nhóm/tìm kiếm |
| --- | --- | --- |
| **Phản xạ** | a.equals(a) == true | Một đối tượng luôn bằng chính nó |
| **Đối xứng** | a.equals(b) và b.equals(a) có cùng kết quả | Đổi thứ tự hai toán hạng không được đổi nghĩa |
| **Bắc cầu** | Nếu a.equals(b) và b.equals(c) đều true, thì a.equals(c) phải true | Không thể có a cùng nhóm b, b cùng nhóm c nhưng a khác nhóm c |
| **Nhất quán** | Kết quả lặp lại ổn định khi dữ liệu dùng để so sánh không đổi | Kết quả không dao động khi dùng trong cấu trúc dữ liệu |
| **So với null** | a.equals(null) == false | null không bằng đối tượng khác null |

**hashCode** có hợp đồng liên quan: **nếu a.equals(b) là true thì a.hashCode() phải bằng b.hashCode()**. Chiều ngược lại không bắt buộc đúng vì va chạm băm được phép xảy ra.

**Lưu ý:** hợp đồng **equals** mạnh hơn điều kiện “code chạy được” hoặc “các trường có vẻ được so sánh đúng”. Phương thức của từng lớp có thể nhìn hợp lý khi đọc riêng, nhưng sự kết hợp giữa lớp cha và lớp con vẫn vi phạm đối xứng hoặc bắc cầu.

### Hai lựa chọn dễ dẫn đến lỗi

Giả sử **Point.equals** so sánh x, y với mọi đối tượng **instanceof Point**.

1. **ColoredPoint chỉ nhận ColoredPoint:** Point.equals(red) trả true, nhưng red.equals(Point) trả false. Đây là lỗi **đối xứng**.
2. **ColoredPoint chấp nhận Point nhưng đòi màu khi gặp ColoredPoint:** Point.equals(red) và red.equals(Point) đều true. Đối xứng có thể được giữ, nhưng với hai màu khác nhau, **bắc cầu bị phá vỡ**.

Lỗi thứ hai khó thấy hơn vì các phép so sánh theo từng cặp Point–ColoredPoint đều có vẻ hợp lý.

## 3. Ví dụ Java 17: sửa đối xứng nhưng lại làm hỏng bắc cầu

Tạo file **BrokenEqualsDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class BrokenEqualsDemo {
    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Point other)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPoint extends Point {
        private final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color, "color");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Point)) {
                return false;
            }
            if (!(obj instanceof ColoredPoint other)) {
                // Chấp nhận Point thường để cố giữ đối xứng.
                return super.equals(obj);
            }
            // Nếu cả hai đều có màu, lại dùng màu làm giá trị.
            return super.equals(obj) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            // Phải cùng hash với Point cùng tọa độ
            // vì hai đối tượng đó được xem là equals.
            return super.hashCode();
        }
    }

    public static void main(String[] args) {
        ColoredPoint red = new ColoredPoint(1, 2, "red");
        Point plain = new Point(1, 2);
        ColoredPoint blue = new ColoredPoint(1, 2, "blue");

        System.out.println("red == plain by equals: " + red.equals(plain));
        System.out.println("plain == blue by equals: " + plain.equals(blue));
        System.out.println("red == blue by equals: " + red.equals(blue));

        System.out.println("plain == red by equals: " + plain.equals(red));
        System.out.println("blue == plain by equals: " + blue.equals(plain));
        System.out.println("same hash: " +
            (red.hashCode() == plain.hashCode()));

        Set<Point> firstOrder = new HashSet<>();
        firstOrder.add(red);
        firstOrder.add(blue);
        firstOrder.add(plain);

        Set<Point> secondOrder = new HashSet<>();
        secondOrder.add(plain);
        secondOrder.add(red);
        secondOrder.add(blue);

        System.out.println("red, blue, plain -> size " + firstOrder.size());
        System.out.println("plain, red, blue -> size " + secondOrder.size());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac BrokenEqualsDemo.java
java BrokenEqualsDemo
~~~

Kết quả:

~~~text
red == plain by equals: true
plain == blue by equals: true
red == blue by equals: false
plain == red by equals: true
blue == plain by equals: true
same hash: true
red, blue, plain -> size 2
plain, red, blue -> size 1
~~~

### Phân tích chính xác lỗi

Chọn **a = red**, **b = plain**, **c = blue**:

~~~text
a.equals(b) == true
b.equals(c) == true
a.equals(c) == false
~~~

Hợp đồng đòi **a = b** và **b = c** kéo theo **a = c**, nhưng phương thức đã vi phạm. Ta có thể diễn đạt bằng các nhóm giả định:

~~~text
red   ── equals ── plain
blue  ── equals ── plain
red   ── NOT equals ── blue
~~~

Không tồn tại cách chia ba đối tượng này thành các nhóm tương đương hợp lệ mà vẫn giữ cả ba kết luận trên.

**Vì sao HashSet cho hai kích thước khác nhau?** Trong ví dụ, các đối tượng có cùng hash. Khi red và blue đến trước, cả hai được giữ vì chúng không bằng nhau; plain đến sau được coi bằng một phần tử đã có, nên còn hai phần tử. Khi plain đến trước, red và blue lần lượt được coi bằng plain, nên chỉ còn một. Thứ tự đưa phần tử vào đã làm biến đổi số lượng “giá trị duy nhất” theo cách trái với kỳ vọng của một quan hệ bằng nhau đúng.

Việc giữ **hashCode** giống nhau ở đây là cố ý để tuân điều kiện bắt buộc khi red.equals(plain) và blue.equals(plain) trả true. **Sửa hashCode để thêm color không giải quyết lỗi bắc cầu**; còn làm phát sinh vi phạm mới vì red bằng plain mà hash có thể khác.

> **Kết luận cho cách cài đặt này:** không thể có cả ba mong muốn “Point so bằng tọa độ”, “ColoredPoint so thêm màu” và “ColoredPoint vẫn bằng Point cùng tọa độ” mà vẫn giữ tính bắc cầu của equals.

## 4. Một lỗi khác: chỉ so sánh lớp con sẽ làm mất đối xứng

Nếu ColoredPoint override equals theo kiểu sau:

~~~java
@Override
public boolean equals(Object obj) {
    if (!(obj instanceof ColoredPoint other)) {
        return false;
    }
    return super.equals(obj) && color.equals(other.color);
}
~~~

Đây chỉ là **đoạn trích minh họa** đặt trong lớp ColoredPoint của phần trước, không phải chương trình độc lập.

Với Point.equals vẫn dùng **instanceof Point**:

- **plain.equals(red)** là true vì red cũng là Point và có cùng tọa độ.
- **red.equals(plain)** là false vì plain không phải ColoredPoint.

Đổi chiều so sánh cho kết quả khác nhau. Đó là vi phạm **đối xứng**. Kiểm tra bằng HashMap, HashSet hoặc thử vài trường hợp ngẫu nhiên có thể không phát hiện ngay; phải kiểm tra **cả hai chiều**.

## 5. Cách an toàn thứ nhất: cùng kiểu runtime mới bằng nhau

Nếu lớp con có thêm trường giá trị, một chính sách rõ ràng là: **Point và ColoredPoint không bao giờ bằng nhau**, kể cả khi tọa độ giống nhau. Trong từng kiểu cụ thể, tất cả trường giá trị được so sánh.

Trong trường hợp này, dùng **getClass()** để đòi **kiểu runtime chính xác** ở lớp cha và lớp con. Tạo file **ExactClassEqualsDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ExactClassEqualsDemo {
    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Point other = (Point) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPoint extends Point {
        private final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color, "color");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            if (!super.equals(obj)) {
                return false;
            }
            ColoredPoint other = (ColoredPoint) obj;
            return color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + color.hashCode();
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        Point red = new ColoredPoint(1, 2, "red");
        Point redAgain = new ColoredPoint(1, 2, "red");
        Point blue = new ColoredPoint(1, 2, "blue");

        System.out.println("plain equals red: " + plain.equals(red));
        System.out.println("red equals plain: " + red.equals(plain));
        System.out.println("red equals redAgain: " + red.equals(redAgain));
        System.out.println("red equals blue: " + red.equals(blue));
        System.out.println("matching hash: " +
            (red.hashCode() == redAgain.hashCode()));

        Set<Point> values = new HashSet<>();
        values.add(plain);
        values.add(red);
        values.add(redAgain);
        values.add(blue);
        System.out.println("unique values: " + values.size());
    }
}
~~~

Chạy:

~~~bash
javac ExactClassEqualsDemo.java
java ExactClassEqualsDemo
~~~

Kết quả:

~~~text
plain equals red: false
red equals plain: false
red equals redAgain: true
red equals blue: false
matching hash: true
unique values: 3
~~~

**Điểm cần hiểu rõ:**

- **getClass()** khiến cả hai chiều giữa Point và ColoredPoint trả false. Vì vậy không có “đối tượng trung gian” Point cùng tọa độ làm cầu nối giữa hai ColoredPoint khác màu.
- Khi hai đối tượng đều là ColoredPoint, **super.equals(obj)** vẫn kiểm tra tọa độ: lời gọi được thực hiện trên một instance ColoredPoint, nên kiểm tra **getClass()** trong lớp cha nhận cùng kiểu runtime ColoredPoint.
- **ColoredPoint.hashCode** bổ sung color đúng với các trường so sánh của ColoredPoint; nếu hai ColoredPoint bằng nhau thì tọa độ và màu giống nhau, nên chúng bắt buộc có hash giống nhau.
- **Point.hashCode** và **ColoredPoint.hashCode** vẫn có thể tình cờ trùng nhau. Điều này hợp lệ vì **hash bằng nhau không có nghĩa equals phải true**.
- **ColoredPoint** là **final** để không còn lớp cháu mới tự ý thêm trường giá trị hoặc override equals, giúp giới hạn nguy cơ.

**Đánh đổi:** Định nghĩa bằng nhau theo kiểu runtime chính xác không phù hợp nếu miền nghiệp vụ yêu cầu **đối tượng của hai kiểu khác nhau nhưng cùng ID phải được coi là một giá trị**. Nó cũng có thể gây bất ngờ với framework sinh proxy/subclass (ví dụ một số trường hợp ORM). Khi đó cần thiết kế chính sách bằng nhau theo ID hoặc khóa nghiệp vụ riêng, phù hợp vòng đời entity và cơ chế proxy; đừng đổi máy móc giữa **instanceof** và **getClass()**.

## 6. Cách an toàn thứ hai: ưu tiên composition để biểu diễn giá trị mở rộng

Khi đối tượng màu thực sự là **một loại giá trị khác**, có thể không cần thừa kế lớp Point. Hãy đặt Point làm một thành phần của ColoredPoint (composition). Hai lớp record Java tự cung cấp equals/hashCode cho toàn bộ thành phần giá trị của mỗi record.

Ví dụ đầy đủ **CompositionEqualsDemo.java**:

~~~java
public class CompositionEqualsDemo {
    record Point(int x, int y) {}

    record ColoredPoint(Point position, String color) {}

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        ColoredPoint red = new ColoredPoint(plain, "red");
        ColoredPoint redAgain = new ColoredPoint(new Point(1, 2), "red");
        ColoredPoint blue = new ColoredPoint(new Point(1, 2), "blue");

        System.out.println("plain equals red: " + plain.equals(red));
        System.out.println("red equals redAgain: " + red.equals(redAgain));
        System.out.println("red equals blue: " + red.equals(blue));
        System.out.println("same position: " +
            red.position().equals(blue.position()));
    }
}
~~~

Chạy:

~~~bash
javac CompositionEqualsDemo.java
java CompositionEqualsDemo
~~~

Kết quả:

~~~text
plain equals red: false
red equals redAgain: true
red equals blue: false
same position: true
~~~

Ở đây **red** và **blue** cùng **position** nhưng khác **giá trị ColoredPoint**. Hai khái niệm được tách rõ, không phải cố làm cho equals của cả cây kế thừa vừa bỏ qua vừa xét màu.

Khi cần kiểm tra hai vật thể có cùng vị trí hay không, gọi **red.position().equals(blue.position())**. Khi cần xem cả vị trí lẫn màu là giá trị, gọi **red.equals(blue)**. Đây là hai phép so sánh với **hai mục đích** khác nhau.

**Record không tự làm mọi trường bên trong bất biến sâu.** Nếu component là danh sách, mảng hay object có thể thay đổi, nội dung bên trong vẫn có thể bị thay đổi hoặc có ngữ nghĩa equals không như mong đợi. Trong ví dụ, **Point** là record chứa hai int và color là String bất biến, nên khóa giá trị đơn giản và ổn định.

## 7. Một số chiến lược thiết kế khác và giới hạn của chúng

### 7.1. Khóa logic chung được coi là định danh duy nhất

Giả sử cả lớp cha và lớp con đại diện cho **cùng thực thể nghiệp vụ**, có khóa **id** không đổi, còn trường bổ sung chỉ là dữ liệu mô tả. Khi ấy có thể chọn **equals chỉ dựa vào id** cho mọi đối tượng cùng miền định danh (với xử lý kiểu phù hợp).

Tuy nhiên, lúc đó hai instance có cùng id nhưng khác color **vẫn phải bằng nhau** theo hợp đồng đã chọn. Không thể vừa tuyên bố **color là một phần của giá trị equals ở lớp con**, vừa bỏ qua nó khi so với lớp cha.

Một phương án thiết kế là thực thi equals/hashCode **final** tại lớp cha cho khóa định danh chung, hoặc dùng khóa riêng. Việc gắn **final** vào equals chỉ ngăn override; bản thân chính sách ID vẫn phải đúng với nghiệp vụ, phân biệt namespace/tenant và vòng đời ID.

### 7.2. Mẫu canEqual

Một số thiết kế lớp có chủ đích cho phép kế thừa equals dùng mẫu **canEqual**, trong đó lớp cha yêu cầu đối tượng còn lại công nhận khả năng so sánh qua một phương thức hai chiều, và lớp con từ chối so bằng lớp cha khi nó thêm trường giá trị.

Ý tưởng cơ bản là cả hai phía phải đồng ý **có cùng loại giá trị có thể so sánh**, trước khi xét từng trường. Mẫu này có thể giữ hợp đồng cho một phân cấp được kiểm soát chặt chẽ, nhưng đòi hỏi **mọi lớp con** tuân cùng quy ước và cài đặt đúng equals/hashCode. Không nên thêm canEqual như bản vá cục bộ vào một cây kế thừa vốn đã có nhiều override không nhất quán.

### 7.3. Phương thức so sánh theo mục đích khác nhau

Nếu vừa cần **cùng vị trí** vừa cần **cùng vị trí và màu**, có thể cung cấp phương thức như **samePosition(Point)** hoặc dùng comparator/key extractor riêng. Những phương thức này có thể trả về kết quả khác equals mà không gây mâu thuẫn vì **chúng không cùng tuyên bố là hợp đồng Object.equals**.

Ví dụ, hai ColoredPoint khác màu có thể trả true khi so **samePosition**, nhưng false khi so **equals**. Khi lưu vào HashSet, chỉ equals/hashCode quyết định khái niệm giá trị duy nhất của tập.

### 7.4. Tránh kế thừa giá trị khi không cần thiết

**final class** hoặc **record** giúp ngăn lớp con mở rộng trạng thái bằng nhau; composition dùng được khi cần kiểu giá trị giàu thông tin hơn. Đây thường là lựa chọn đơn giản hơn so với phải duy trì hợp đồng equals qua nhiều tầng kế thừa.

## 8. Các lỗi thường gặp khi sửa equals

**Chỉ đổi instanceof sang getClass ở lớp con.** Nếu lớp cha vẫn chấp nhận lớp con, thì vẫn có thể xuất hiện:

~~~text
parent.equals(child) == true
child.equals(parent) == false
~~~

Cần thống nhất quy tắc ở **toàn bộ các lớp liên quan**, không sửa từng bên độc lập.

**Để color tham gia hashCode trong khi vẫn chấp nhận Point thường là equals.** Khi ColoredPoint.equals(Point) là true mà hash lại tính thêm color, có thể vi phạm trực tiếp hợp đồng hashCode. Cách đúng là thay đổi **định nghĩa equals** trước rồi đồng bộ hashCode.

**Cho phép một lớp con thêm trường nhưng không cập nhật equals/hashCode.** Nếu color thật sự là giá trị mà không được so sánh, hai đối tượng khác màu vẫn bị xem là bằng nhau. Đó là lỗi ngữ nghĩa nghiệp vụ; ngay cả khi quan hệ còn phản xạ, đối xứng và bắc cầu, kết quả vẫn có thể không đúng yêu cầu.

**Dùng trường mutable để tính hashCode.** Nếu tọa độ, id hoặc color thay đổi sau khi đối tượng đã làm khóa HashMap/HashSet, phép tìm lại có thể sai. Ưu tiên thành phần khóa bất biến và không sửa trạng thái có ảnh hưởng tới equals/hashCode khi đang dùng làm khóa.

**Cho rằng cùng hash là cùng giá trị.** Hai đối tượng không bằng nhau có thể cùng hash. HashMap/HashSet dựa cả vào hashCode và equals; hash không thay thế equals.

**Bỏ qua null, proxy và việc gọi qua tham chiếu lớp cha.** Equals nhận **Object**, có thể được gọi từ nhiều nguồn khác nhau. Kiểm thử phải bao gồm các đối tượng ở các lớp khác nhau trong phân cấp, trường hợp null và mọi kiểu subclass thực sự xuất hiện trong ứng dụng.

## 9. Checklist kiểm thử hồi quy cho lớp cha và lớp con

Dựng tập mẫu gồm:

- **p1, p2:** hai đối tượng lớp cha cùng giá trị; **p3:** lớp cha khác giá trị.
- **c1, c2:** hai đối tượng lớp con có cùng toàn bộ trường giá trị.
- **c3:** lớp con chỉ khác trường mới, ví dụ color.
- **c4:** lớp con khác tọa độ/trường kế thừa.
- Mọi cặp cha–con có cùng dữ liệu lớp cha nhưng khác kiểu runtime; thêm null.

Sau đó kiểm tra:

1. **Phản xạ và null:** mọi a đều a.equals(a) == true và a.equals(null) == false.
2. **Đối xứng:** với mỗi cặp a, b, kiểm tra **a.equals(b) == b.equals(a)**. Đặc biệt đổi thứ tự cha–con.
3. **Bắc cầu:** với mọi bộ a, b, c mà **a.equals(b)** và **b.equals(c)** true, kiểm tra **a.equals(c)** true; đặc biệt thử **con A → cha → con B khác trường mới**.
4. **Hash tương thích:** hễ a.equals(b) true thì **a.hashCode() == b.hashCode()**.
5. **Tính nhất quán:** gọi lặp lại trong khi không thay đổi dữ liệu phải cùng kết quả.
6. **HashSet/HashMap:** thêm các phần tử theo nhiều thứ tự khác nhau; số giá trị logic riêng biệt và khả năng tra cứu phải phù hợp với định nghĩa equals.
7. **Subclasses khác nhau:** nếu có nhiều lớp con cùng kế thừa một lớp cha, thử cả các cặp giữa hai lớp con; chỉ thử một lớp con là chưa đủ.

Có thể triển khai các kiểm tra đối xứng/bắc cầu thành vòng lặp trên các mẫu có sẵn hoặc dùng thư viện kiểm tra equals/hashCode mà dự án đang sử dụng. Đối với test dùng Java **assert**, phải chạy JVM với cờ **-ea** thì assertion mới được thực thi; test framework thường cung cấp assertion riêng.

### Câu hỏi tự đánh giá

1. Vì sao **red.equals(plain)** và **plain.equals(blue)** đều true nhưng **red.equals(blue)** false là lỗi hợp đồng, dù mỗi phương thức nhìn riêng có vẻ hợp lý?
2. Vì sao thêm **color** vào hashCode không khắc phục tình huống trên?
3. Khi nào bạn muốn **Point** khác **ColoredPoint** ngay cả khi x, y giống nhau?
4. Nếu color chỉ là thông tin hiển thị, equals và hashCode nên xét những trường nào?
5. Nếu nghiệp vụ vừa cần “cùng vị trí” vừa cần “cùng vị trí và màu”, vì sao nên dùng **hai phép so sánh có tên rõ nghĩa** hoặc **hai kiểu giá trị**?

## 10. Hướng dẫn lựa chọn thiết kế

| Ý nghĩa nghiệp vụ | Thiết kế phù hợp | Hợp đồng cần giữ |
| --- | --- | --- |
| Lớp con thêm trường **thực sự thuộc giá trị equals** | So bằng **getClass** nhất quán qua phân cấp, hoặc dùng **composition/record** | Không cho cha và con tương đương chỉ vì phần dữ liệu chung khớp |
| Cả cha và con đại diện cùng thực thể, trường mới chỉ mô tả | Có thể dùng **định danh logic bất biến**, với equals/hashCode nhất quán | Trường mới không được lúc xét lúc bỏ trong equals |
| Cần nhiều tiêu chí so sánh cùng lúc | Viết **phương thức có tên**, **key extractor** hoặc loại giá trị riêng | Không ép các tiêu chí mâu thuẫn vào một equals |
| Cây kế thừa mở, nhiều lớp con override | Xem lại kiến trúc; cân nhắc **canEqual** hoặc bỏ kế thừa giá trị | Mọi lớp trong cây phải giữ phản xạ, đối xứng, bắc cầu và hashCode |

**Ghi nhớ cốt lõi:** Khi lớp con bổ sung **trường giá trị**, lỗi dễ thấy là **mất đối xứng** do hai lớp chấp nhận nhau khác nhau. Cố sửa bằng cách cho lớp con chấp nhận cả lớp cha có thể tạo lỗi sâu hơn là **mất bắc cầu** giữa hai lớp con khác giá trị, với lớp cha làm trung gian. Muốn equals đáng tin, hãy thiết kế **một định nghĩa bằng nhau nhất quán cho mọi kiểu liên quan**; khi trường bổ sung tạo ra một giá trị khác, **tách kiểu giá trị hoặc từ chối so bằng giữa các lớp** là cách rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-003">Kiểm tra kiểu bằng instanceof hoặc getClass()</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra kiểu bằng `instanceof` hoặc `getClass()` khi cài đặt `equals()` trong Java

## 1. Vì sao việc kiểm tra kiểu ảnh hưởng đến `equals()`?

Trong Java, `equals(Object other)` quyết định khi nào hai **đối tượng** được coi là bằng nhau theo ý nghĩa của lớp. Trước khi so sánh các trường dữ liệu, phương thức thường kiểm tra xem đối tượng bên phải có thuộc **miền đối tượng được phép so sánh bằng nhau** với đối tượng bên trái hay không.

Khi một lớp có thể được kế thừa, hai cách kiểm tra thường dẫn đến chính sách khác nhau:

- `other instanceof Point`: chấp nhận đối tượng của `Point` **và mọi lớp con** của `Point` (nếu không `null`). Đây là kiểm tra **cùng kiểu hoặc kiểu con**.
- `getClass() == other.getClass()`: chỉ chấp nhận hai đối tượng có **chính xác cùng lớp lúc chạy**. Đây là kiểm tra **lớp thực tế giống hệt nhau**.

Sự khác biệt quan trọng nhất xuất hiện khi lớp con bổ sung trường dữ liệu hoặc ghi đè `equals()`. Một lớp cha có thể xem lớp con là bằng nhau theo các trường của cha, trong khi lớp con lại cần so sánh thêm trường riêng. Nếu triển khai thiếu nhất quán, `equals()` có thể vi phạm tính **đối xứng** hoặc **bắc cầu**.

Chủ đề này nói về **chọn miền so sánh kiểu trong `equals()`**. Toán tử `==` giữa hai biến tham chiếu vẫn kiểm tra **danh tính đối tượng**, không phải tiêu chí bằng nhau do `equals()` định nghĩa.

## 2. Phân biệt chính xác `instanceof` và `getClass()`

### `instanceof`: chấp nhận quan hệ kế thừa hoặc triển khai interface

Với biểu thức `obj instanceof T`, kết quả là `true` nếu đối tượng lúc chạy là thể hiện của `T`, lớp con của `T`, hoặc lớp triển khai interface `T` (theo quan hệ kiểu phù hợp); nếu `obj == null`, kết quả luôn `false`.

Java 17 hỗ trợ **pattern matching cho `instanceof`**:

```java
if (other instanceof Point point) {
    // point đã có kiểu Point, không cần ép kiểu thủ công.
}
```

Pattern chỉ khai báo biến `point` ở nơi trình biên dịch xác định phép kiểm tra đã thành công. Ví dụ `if (!(other instanceof Point point)) return false;` cho phép dùng `point` sau câu `if` vì nhánh thất bại đã thoát.

### `getClass()`: yêu cầu lớp lúc chạy trùng khớp hoàn toàn

`Object.getClass()` trả về đối tượng `Class<?>` đại diện cho **lớp thực tế lúc chạy**, không phải kiểu khai báo của biến. So sánh hai đối tượng `Class` bằng `==` là phù hợp vì cùng lớp trong cùng ngữ cảnh nạp lớp được đại diện bởi cùng đối tượng `Class`.

```java
if (other == null || getClass() != other.getClass()) {
    return false;
}
```

Phải kiểm tra `other == null` trước khi gọi `other.getClass()`: nếu không sẽ có `NullPointerException`. Trái lại, `null instanceof Point` đơn giản trả về `false`.

### Ví dụ chạy được: kiểu khai báo không quyết định `getClass()`

Tạo tệp `TypeCheckDemo.java`:

```java
public class TypeCheckDemo {
    interface Identified {}

    static class Parent implements Identified {}

    static final class Child extends Parent {}

    public static void main(String[] args) {
        Parent polymorphic = new Child();
        Parent parent = new Parent();
        Object nothing = null;

        System.out.println("is Parent: " + (polymorphic instanceof Parent));
        System.out.println("is Child: " + (polymorphic instanceof Child));
        System.out.println("is Identified: " + (polymorphic instanceof Identified));
        System.out.println("exact Parent: " + (polymorphic.getClass() == Parent.class));
        System.out.println("exact Child: " + (polymorphic.getClass() == Child.class));
        System.out.println("parent exact Parent: " + (parent.getClass() == Parent.class));
        System.out.println("null instanceof Parent: " + (nothing instanceof Parent));
    }
}
```

Biên dịch và chạy (Java 17+):

```bash
javac -encoding UTF-8 TypeCheckDemo.java
java TypeCheckDemo
```

Stdout:

```text
is Parent: true
is Child: true
is Identified: true
exact Parent: false
exact Child: true
parent exact Parent: true
null instanceof Parent: false
```

Biến `polymorphic` khai báo là `Parent` nhưng chứa đối tượng `Child`. Vì thế `instanceof Parent` là `true`, còn `polymorphic.getClass() == Parent.class` là `false`. Trong trường hợp này, `getClass()` có kết quả `Child.class`.

## 3. Nhắc lại hợp đồng `equals()` trước khi chọn cách kiểm tra kiểu

Với các tham chiếu đối tượng không `null`, một cách triển khai `equals()` đúng phải thỏa mãn:

1. **Phản xạ (reflexive):** `x.equals(x)` luôn `true`.
2. **Đối xứng (symmetric):** `x.equals(y)` và `y.equals(x)` luôn có cùng giá trị.
3. **Bắc cầu (transitive):** nếu `x.equals(y)` và `y.equals(z)` đều `true`, thì `x.equals(z)` cũng phải `true`.
4. **Nhất quán (consistent):** nhiều lần so sánh cho cùng kết quả khi trạng thái có liên quan không thay đổi.
5. **Với `null`:** `x.equals(null)` là `false`.

Hợp đồng đi kèm của `hashCode()`: **nếu `x.equals(y)` là `true`, thì `x.hashCode() == y.hashCode()` phải đúng**. Điều ngược lại không bắt buộc: hai đối tượng khác nhau vẫn có thể trùng mã băm.

Các collection như `HashSet` và `HashMap` cần cả `equals()` và `hashCode()` vận hành nhất quán. Chọn `instanceof` hay `getClass()` chưa đủ để bảo đảm toàn bộ hợp đồng: còn phải xem trường nào tham gia so sánh, tính bất biến của dữ liệu và cách lớp con ghi đè phương thức.

## 4. Rủi ro khi lớp cha dùng `instanceof`, lớp con bổ sung tiêu chí bằng nhau

Giả sử lớp `Point` chỉ quan tâm tọa độ `x, y`. Lớp con `ColoredPoint` có thêm `color` và muốn màu sắc cũng tham gia định nghĩa bằng nhau.

Tạo tệp `BadInstanceofDemo.java`:

```java
import java.util.Objects;

public class BadInstanceofDemo {
    static class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Point other)) return false;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPoint extends Point {
        final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ColoredPoint other)) return false;
            return super.equals(other) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + color.hashCode();
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        ColoredPoint red = new ColoredPoint(1, 2, "red");

        System.out.println("plain.equals(red): " + plain.equals(red));
        System.out.println("red.equals(plain): " + red.equals(plain));
    }
}
```

```bash
javac -encoding UTF-8 BadInstanceofDemo.java
java BadInstanceofDemo
```

Stdout:

```text
plain.equals(red): true
red.equals(plain): false
```

Đây là **ví dụ CỐ Ý SAI** để nhìn thấy lỗi:

- `plain.equals(red)` kiểm tra `red instanceof Point`, điều kiện đúng, rồi chỉ so `x, y`: kết quả `true`.
- `red.equals(plain)` kiểm tra `plain instanceof ColoredPoint`, điều kiện sai: kết quả `false`.
- Hợp đồng **đối xứng** bị phá vỡ. Hai `hashCode()` còn có thể khác nhau dù phép so sánh từ phía `plain` trả `true`, làm thiết kế càng không phù hợp với collection dựa trên hash.

Không nên “vá” lỗi đơn thuần bằng cách cho lớp con bỏ qua màu khi gặp `Point`. Nếu `Point(1,2)` được coi bằng cả điểm đỏ và điểm xanh, nhưng hai điểm có màu khác nhau lại không bằng nhau, tính **bắc cầu** cũng có thể bị phá vỡ. Cần chọn một chính sách bằng nhau nhất quán cho toàn bộ phân cấp lớp.

## 5. Giải pháp dùng `getClass()`: chỉ bằng nhau khi cùng lớp thực tế

Khi `Point` và `ColoredPoint` đại diện cho **hai kiểu giá trị khác nhau**, không cho chúng bằng nhau qua ranh giới lớp là một lựa chọn rõ ràng. Mỗi lớp trước tiên so `getClass()`, rồi mới so những trường xác định giá trị của lớp đó.

Tạo tệp `ExactClassEqualityDemo.java`:

```java
import java.util.Objects;

public class ExactClassEqualityDemo {
    static class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Point other = (Point) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPoint extends Point {
        final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ColoredPoint other = (ColoredPoint) obj;
            return super.equals(other) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + color.hashCode();
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        ColoredPoint red = new ColoredPoint(1, 2, "red");
        ColoredPoint redCopy = new ColoredPoint(1, 2, "red");
        ColoredPoint blue = new ColoredPoint(1, 2, "blue");

        System.out.println("plain.equals(red): " + plain.equals(red));
        System.out.println("red.equals(plain): " + red.equals(plain));
        System.out.println("red.equals(redCopy): " + red.equals(redCopy));
        System.out.println("red.equals(blue): " + red.equals(blue));
        System.out.println("equal hash: " + (red.hashCode() == redCopy.hashCode()));
    }
}
```

```bash
javac -encoding UTF-8 ExactClassEqualityDemo.java
java ExactClassEqualityDemo
```

Stdout:

```text
plain.equals(red): false
red.equals(plain): false
red.equals(redCopy): true
red.equals(blue): false
equal hash: true
```

Ở đây:

- `Point` và `ColoredPoint` **không bao giờ bằng nhau** dù tọa độ giống nhau. Hai chiều so sánh đều `false`, nên tránh được lỗi đối xứng trong ví dụ trước.
- Hai `ColoredPoint` được so theo cả `x, y` (thông qua `super.equals(other)`) và `color`.
- `hashCode()` của `ColoredPoint` kết hợp cùng những phần dữ liệu mà `equals()` dùng. Hai điểm màu bằng nhau chắc chắn có mã băm bằng nhau.
- Việc `super.equals(other)` vẫn đúng trong ví dụ này vì cả `this` và `other` đều là `ColoredPoint` tại thời điểm gọi phương thức của `Point`; kiểm tra `getClass()` ở lớp cha vì thế thành công.

**Lưu ý về thiết kế:** `getClass()` không tự động làm `equals()` đúng. Nếu một lớp con có trạng thái ảnh hưởng đến danh tính giá trị mà lại kế thừa nguyên `equals()` của lớp cha, hai thể hiện cùng lớp con vẫn có thể bị xem là bằng nhau dù trạng thái bổ sung khác nhau. Lớp con phải định nghĩa lại chính sách so sánh và `hashCode()` một cách nhất quán, hoặc phân cấp cần được thiết kế để không cho lớp con thêm dữ liệu ảnh hưởng đến equality.

## 6. Khi nào `instanceof` lại là lựa chọn hợp lệ?

`instanceof` **không sai về bản chất**. Nó hữu ích khi chính sách nghiệp vụ muốn coi các đối tượng trong cùng một phân cấp là bằng nhau theo **một tập thuộc tính chung**, bất kể lớp con cụ thể. Nhưng không thể cho lớp con tùy ý thay đổi tiêu chí equality.

Một cách đảm bảo điều đó là để lớp cha định nghĩa `equals()` và `hashCode()` là `final`, đồng thời quy định rõ các trường bổ sung của lớp con **không góp phần vào equality**.

Tạo tệp `HierarchyEqualityDemo.java`:

```java
public class HierarchyEqualityDemo {
    static class Coordinate {
        final int x;
        final int y;

        Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public final boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Coordinate other)) return false;
            return x == other.x && y == other.y;
        }

        @Override
        public final int hashCode() {
            return 31 * x + y;
        }
    }

    static final class LabeledCoordinate extends Coordinate {
        final String label;

        LabeledCoordinate(int x, int y, String label) {
            super(x, y);
            this.label = label;
        }
    }

    public static void main(String[] args) {
        Coordinate plain = new Coordinate(1, 2);
        LabeledCoordinate a = new LabeledCoordinate(1, 2, "A");
        LabeledCoordinate b = new LabeledCoordinate(1, 2, "B");

        System.out.println("plain.equals(a): " + plain.equals(a));
        System.out.println("a.equals(plain): " + a.equals(plain));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("same hash: " + (plain.hashCode() == a.hashCode()));
    }
}
```

```bash
javac -encoding UTF-8 HierarchyEqualityDemo.java
java HierarchyEqualityDemo
```

Stdout:

```text
plain.equals(a): true
a.equals(plain): true
a.equals(b): true
same hash: true
```

Ở đây hai tọa độ bằng nhau khi cùng `x, y`, **nhãn chỉ là thông tin hiển thị**. Do equality ở lớp cha là `final`, lớp con không thể ghi đè với quy tắc riêng và phá đối xứng. Điều này chỉ phù hợp khi yêu cầu thực sự cho phép bỏ qua `label`; nếu nhãn phân biệt giá trị, cần chính sách equality khác.

Với một **lớp `final` không có lớp con**, hai cách kiểm tra kiểu thường có miền chấp nhận tương đương khi so với chính lớp ấy: `other instanceof MyFinalType` và `other != null && other.getClass() == MyFinalType.class`. Trong trường hợp này, `instanceof` thường ngắn gọn hơn; quyết định chủ yếu cần cân nhắc khi lớp có thể được kế thừa.

## 7. Những tình huống cần xét trước khi quyết định

### Kiểu runtime, proxy và framework

Một số framework có thể tạo **lớp con proxy** của entity hay domain class. `getClass()` đối chiếu lớp runtime một cách nghiêm ngặt, nên một đối tượng proxy và đối tượng thật của lớp gốc có thể bị đánh giá khác lớp dù cùng dữ liệu. Tuy nhiên, thay ngay bằng `instanceof` cũng không tự giải quyết toàn bộ bài toán equality của entity (ví dụ định danh khi chưa lưu, proxy, trạng thái có thể thay đổi). Nếu dùng framework như ORM, hãy theo chính sách equality phù hợp với cơ chế proxy/identity của framework và kiểm tra hai chiều so sánh.

### So sánh với interface hoặc nhiều nhánh kế thừa

`instanceof` có thể chấp nhận đối tượng thuộc các lớp runtime khác nhau cùng triển khai một interface. Điều này hữu ích để **kiểm tra khả năng sử dụng** của đối tượng, nhưng không mặc định có nghĩa mọi hiện thực của interface đều có cùng **ngữ nghĩa bằng nhau**. Trước khi dùng trong `equals()`, phải xác định trường/thuộc tính nào được so sánh và bảo đảm tất cả nhánh tuân cùng tiêu chí.

### Tính ổn định của dữ liệu tham gia equality

Dù chọn cách kiểm tra kiểu nào, dùng các trường **mutable** làm cơ sở cho cả `equals()` và `hashCode()` có thể gây vấn đề sau khi đưa đối tượng vào `HashSet` hoặc làm khóa `HashMap`: sửa trường có thể khiến mã băm hoặc vị trí logic thay đổi. Với value object, nên ưu tiên dữ liệu bất biến và equality nhất quán. Với entity, xác định chính sách riêng theo vòng đời định danh.

### `equals()` không thay thế `==`

`this == other` ở đầu `equals()` là tối ưu hợp lệ: cùng một đối tượng luôn bằng chính nó nếu tuân đúng hợp đồng. Nó không thay thế phần kiểm tra kiểu và so trường đối với **hai đối tượng khác nhau**. Ngược lại, `getClass() == other.getClass()` so sánh **đại diện lớp runtime**, không so xem hai đối tượng dữ liệu có cùng danh tính.

## 8. Bảng chọn nhanh `instanceof` và `getClass()`

| Câu hỏi thiết kế | Hướng lựa chọn |
| --- | --- |
| Hai lớp khác nhau trong phân cấp có được coi bằng nhau không? | Nếu **không**, kiểm tra lớp chính xác bằng `getClass()` là chính sách dễ diễn đạt. |
| Mọi lớp con chỉ là những cách trình bày của cùng một giá trị cơ sở? | Có thể dùng `instanceof` với equality chung ở lớp cha; cân nhắc `final equals()` và `final hashCode()`. |
| Lớp con cần bổ sung trường tham gia equality? | Tránh để lớp cha dùng `instanceof` chấp nhận lớp con trong khi lớp con lại thu hẹp so sánh; cần chính sách toàn phân cấp bảo toàn hợp đồng. |
| Lớp là `final`, không thể kế thừa? | `instanceof` thường đơn giản; `getClass()` cũng có thể đúng nhưng không mang thêm lợi ích rõ rệt. |
| Có proxy/runtime subclass do framework tạo? | Xác minh cách lấy lớp/định danh phù hợp framework trước khi dùng `getClass()` trực tiếp. |
| Đối tượng bên phải có thể là `null`? | `instanceof` tự trả `false`; với `getClass()` phải kiểm tra `null` trước. |

## 9. Checklist kiểm thử `equals()` trong phân cấp kế thừa

Với các lớp `Base`, `ChildA`, `ChildB`, kiểm thử ít nhất:

1. **Cùng đối tượng:** `a.equals(a)` là `true`.
2. **Null:** `a.equals(null)` là `false`.
3. **Cùng lớp:** các đối tượng khác nhau nhưng có cùng trường được coi là giá trị có kết quả equality phù hợp.
4. **Khác lớp:** so cả `base.equals(child)` **và** `child.equals(base)`; chúng phải nhất quán.
5. **Hai lớp con:** so hai thể hiện cùng lớp con và các thể hiện thuộc hai lớp con khác nhau theo chính sách đã chọn.
6. **Bắc cầu:** lập bộ ba `x, y, z` ở những kiểu runtime khác nhau; nếu hai quan hệ đầu là `true`, quan hệ thứ ba phải là `true`.
7. **Hash:** với mọi cặp `x.equals(y)` đúng, kiểm tra `x.hashCode() == y.hashCode()`; thử thêm thao tác với `HashSet`/`HashMap` khi phù hợp.
8. **Trạng thái:** kiểm tra cách đối tượng ứng xử khi dữ liệu tham gia equality có thể bị thay đổi, đặc biệt sau khi dùng làm khóa hash.
9. **Proxy (nếu có):** kiểm tra các cặp đối tượng thật/proxy theo chính sách thực tế của framework.

**Ghi nhớ:** `instanceof` trả lời “đối tượng này thuộc kiểu được yêu cầu hoặc một kiểu con/triển khai phù hợp không?”, còn `getClass()` trả lời “lớp runtime chính xác là gì?”. Khi viết `equals()`, điều quyết định là **ngữ nghĩa bằng nhau trên cả phân cấp lớp**. Phương án đúng phải giữ được đối xứng, bắc cầu và hợp đồng với `hashCode()`, kể cả khi lớp con được bổ sung sau này.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-004">Ảnh hưởng của lựa chọn kiểm tra kiểu tới quan hệ lớp cha, lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của lựa chọn kiểm tra kiểu tới quan hệ lớp cha, lớp con khi triển khai `equals()` trong Java

Khi ghi đè `equals(Object)`, kiểm tra kiểu không chỉ quyết định có ép kiểu được hay không: nó còn quyết định **những lớp nào được phép xem đối tượng của nhau là bằng nhau**. Sự khác biệt giữa `instanceof` và `getClass()` đặc biệt quan trọng trong quan hệ kế thừa, nơi lớp con có thể bổ sung trạng thái mà lớp cha không biết.

Bài học này tập trung vào ảnh hưởng của lựa chọn kiểm tra kiểu đối với **equality của lớp cha và lớp con**, các lỗi **đối xứng** và **bắc cầu**, và cách chọn thiết kế có hợp đồng `equals()`/`hashCode()` nhất quán.

## 1. Hợp đồng `equals()` cần giữ khi có kế thừa

Với các tham chiếu khác `null`, `equals()` phải đáp ứng:

1. **Phản xạ (reflexive):** `a.equals(a)` là `true`.
2. **Đối xứng (symmetric):** `a.equals(b)` và `b.equals(a)` luôn có cùng kết quả.
3. **Bắc cầu (transitive):** nếu `a.equals(b)` và `b.equals(c)` đúng, `a.equals(c)` cũng phải đúng.
4. **Nhất quán (consistent):** kết quả không đổi qua các lần gọi khi trạng thái dùng để so sánh không thay đổi.
5. **Không bằng `null`:** `a.equals(null)` luôn là `false`.

Đồng thời, **nếu `a.equals(b)` là `true`, mã băm `a.hashCode()` và `b.hashCode()` phải bằng nhau**. Ngược lại, hai mã băm bằng nhau không chứng minh hai đối tượng bằng nhau.

Trong kế thừa, khó khăn là lớp cha chỉ biết các trường của chính nó, còn lớp con có thể bổ sung trường quan trọng cho định nghĩa giá trị. Nếu lớp cha cho phép đối chiếu với lớp con nhưng lớp con áp dụng điều kiện chặt hơn, phép so sánh dễ mất tính đối xứng. Nếu cố dung hòa hai cách, phép so sánh có thể mất tính bắc cầu.

## 2. `instanceof` và `getClass()` kiểm tra hai quan hệ khác nhau

### Kiểm tra bằng `instanceof`

~~~java
if (!(other instanceof Point point)) {
    return false;
}
~~~

Đây là đoạn minh họa nằm **bên trong** phương thức `equals(Object other)`. Java 17 hỗ trợ pattern matching với `instanceof`: khi điều kiện thành công, biến `point` có kiểu `Point` để dùng ngay.

Điều kiện đúng khi object thuộc lớp `Point` **hoặc một lớp con của `Point`** (và cũng có thể áp dụng với interface được triển khai). Với `other == null`, `instanceof` trả `false`. Do đó, dùng `instanceof` trong `Point.equals` cho phép `Point` so sánh tọa độ của cả các lớp kế thừa, nếu chúng có thể ép kiểu sang `Point`.

### Kiểm tra bằng `getClass()`

~~~java
if (other == null || getClass() != other.getClass()) {
    return false;
}
~~~

Đây cũng là đoạn minh họa bên trong `equals(Object other)`. Kiểm tra `getClass()` yêu cầu **đúng cùng một lớp runtime**: một instance `Point` và một instance `ColorPoint extends Point` là hai kiểu runtime khác nhau, nên `Point.equals(colorPoint)` trả `false`, ngay cả khi tọa độ giống hệt nhau.

Việc kiểm tra `null` trước `other.getClass()` là bắt buộc để tránh `NullPointerException`. Trong phương thức instance, `getClass()` tương đương `this.getClass()`.

| Câu hỏi | `other instanceof Point` | `getClass() == other.getClass()` trong `Point.equals` |
|---|---|---|
| `other` là `Point` | `true` | `true` |
| `other` là `ColorPoint extends Point` | `true` | `false` |
| `other` không thuộc họ `Point` | `false` | `false` |
| `other == null` | `false` | Phải kiểm tra `null` trước |

**Khác biệt cốt lõi:** `instanceof` coi *khả năng thuộc một hệ kiểu* là điều kiện để bước vào so sánh; `getClass()` yêu cầu *cùng lớp runtime*. Cả hai chỉ là **bước kiểm tra kiểu**, chưa đủ để `equals()` đúng: phần so sánh trường, `hashCode()` và cách ghi đè trong lớp con vẫn phải tuân theo hợp đồng.

## 3. Lỗi đối xứng khi lớp cha dùng `instanceof` nhưng lớp con thêm trường

Giả sử `Point` có `x`, `y`; lớp con `ColorPoint` bổ sung `color`. Lớp cha muốn xem hai điểm cùng tọa độ là bằng nhau, trong khi lớp con muốn màu cũng phải khớp.

- `Point.equals` dùng `instanceof Point` nên chấp nhận object `ColorPoint` chỉ dựa trên `x`, `y`.
- `ColorPoint.equals` chỉ chấp nhận `ColorPoint` và yêu cầu màu giống nhau.
- Với `p = Point(1, 2)` và `red = ColorPoint(1, 2, "red")`, ta nhận được `p.equals(red) == true` nhưng `red.equals(p) == false`.

Đây là **vi phạm đối xứng**: cùng hai object nhưng kết quả phụ thuộc thứ tự gọi. Việc lớp con thêm trường có ý nghĩa là nguyên nhân sâu hơn; `instanceof` cho phép lớp cha so với lớp con nhưng không cho lớp cha biết điều kiện mà lớp con cần.

Lỗi này còn gây khó cho `hashCode()`. Nếu `Point.hashCode()` dùng tọa độ còn `ColorPoint.hashCode()` thêm màu, ta có thể có cặp mà `Point.equals(ColorPoint)` trả `true` nhưng hash khác nhau, vi phạm thêm hợp đồng hash. **Thêm `hashCode()` vào lớp con không sửa được lỗi `equals()` thiếu đối xứng**; hai phương thức phải được thiết kế cùng nhau.

## 4. Cố sửa đối xứng có thể phá tính bắc cầu

Một cách vá thường gặp là bảo lớp con: nếu đối tượng cần so sánh chỉ là lớp cha, hãy so tọa độ; nếu là lớp con cùng loại, hãy so cả màu.

Khi đó với cùng tọa độ:

- `red = ColorPoint(1, 2, "red")` bằng `plain = Point(1, 2)`.
- `plain = Point(1, 2)` bằng `blue = ColorPoint(1, 2, "blue")`.
- Nhưng `red` **không bằng** `blue` vì màu khác nhau.

Kết quả: `red.equals(plain)` đúng **và** `plain.equals(blue)` đúng nhưng `red.equals(blue)` sai. Phép so sánh đã **mất tính bắc cầu**. Có thể làm đối xứng và hash nhất quán ở ví dụ này, nhưng vẫn không có một quan hệ equality hợp lệ. Việc "so theo lớp cha nếu không cùng lớp con" không phải giải pháp tổng quát.

Bài toán căn bản nằm ở định nghĩa: muốn cả `Point` không màu bằng hai `ColorPoint` khác màu thì theo tính bắc cầu, hai điểm có màu kia cũng phải bằng nhau; điều đó mâu thuẫn với yêu cầu màu là một phần giá trị. Không thể giải quyết mâu thuẫn định nghĩa này bằng đổi cú pháp `instanceof`.

## 5. Ví dụ Java 17 chạy được: lỗi đối xứng, lỗi bắc cầu và cách sửa

Lưu toàn bộ chương trình sau thành file **`EqualsInheritanceDemo.java`**. Các lớp lồng nhau dùng tên khác nhau để ba tình huống không ảnh hưởng lẫn nhau.

~~~java
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class EqualsInheritanceDemo {

    // 1) Lớp cha dùng instanceof: cho phép lớp con đi qua.
    static class NaivePoint {
        final int x, y;

        NaivePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NaivePoint p)) return false;
            return x == p.x && y == p.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    // Lớp con siết chặt điều kiện: gây lỗi đối xứng.
    static class NaiveColorPoint extends NaivePoint {
        final String color;

        NaiveColorPoint(int x, int y, String color) {
            super(x, y);
            this.color = color;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof NaiveColorPoint p)) return false;
            return super.equals(other)
                    && Objects.equals(color, p.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    // 2) Phiên bản thử dung hòa lớp cha và lớp con.
    static class FlexiblePoint {
        final int x, y;

        FlexiblePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FlexiblePoint p)) return false;
            return x == p.x && y == p.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static class FlexibleColorPoint extends FlexiblePoint {
        final String color;

        FlexibleColorPoint(int x, int y, String color) {
            super(x, y);
            this.color = color;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FlexiblePoint)) return false;
            if (!(other instanceof FlexibleColorPoint p)) {
                return super.equals(other); // Chỉ so tọa độ với lớp cha.
            }
            return super.equals(other)
                    && Objects.equals(color, p.color);
        }

        // Cố ý kế thừa hashCode theo tọa độ để các cặp
        // bằng nhau giữa lớp cha/con có cùng mã băm.
        // Việc này KHÔNG thể sửa lỗi bắc cầu.
    }

    // 3) Giải pháp cùng lớp runtime: getClass().
    static class StrictPoint {
        final int x, y;

        StrictPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            StrictPoint p = (StrictPoint) other;
            return x == p.x && y == p.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static class StrictColorPoint extends StrictPoint {
        final String color;

        StrictColorPoint(int x, int y, String color) {
            super(x, y);
            this.color = color;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            StrictColorPoint p = (StrictColorPoint) other;
            return super.equals(other)
                    && Objects.equals(color, p.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    // 4) Composition/value type: không có kế thừa giá trị.
    record ColoredPoint(int x, int y, String color) {}

    public static void main(String[] args) {
        NaivePoint p = new NaivePoint(1, 2);
        NaiveColorPoint red = new NaiveColorPoint(1, 2, "red");
        System.out.println("naive parent -> child: " + p.equals(red));
        System.out.println("naive child -> parent: " + red.equals(p));

        FlexiblePoint plain = new FlexiblePoint(1, 2);
        FlexibleColorPoint flexibleRed =
                new FlexibleColorPoint(1, 2, "red");
        FlexibleColorPoint flexibleBlue =
                new FlexibleColorPoint(1, 2, "blue");
        System.out.println("flexible red -> plain: "
                + flexibleRed.equals(plain));
        System.out.println("flexible plain -> blue: "
                + plain.equals(flexibleBlue));
        System.out.println("flexible red -> blue: "
                + flexibleRed.equals(flexibleBlue));

        StrictPoint strictPlain = new StrictPoint(1, 2);
        StrictColorPoint strictRed = new StrictColorPoint(1, 2, "red");
        StrictColorPoint sameRed = new StrictColorPoint(1, 2, "red");
        StrictColorPoint strictBlue = new StrictColorPoint(1, 2, "blue");
        System.out.println("strict parent -> child: "
                + strictPlain.equals(strictRed));
        System.out.println("strict child -> parent: "
                + strictRed.equals(strictPlain));
        System.out.println("strict red -> red copy: "
                + strictRed.equals(sameRed));
        System.out.println("strict red -> blue: "
                + strictRed.equals(strictBlue));
        System.out.println("strict set size: "
                + new HashSet<>(List.of(
                        strictPlain, strictRed, sameRed, strictBlue)).size());

        ColoredPoint valueA = new ColoredPoint(1, 2, "red");
        ColoredPoint valueB = new ColoredPoint(1, 2, "red");
        System.out.println("record value equality: "
                + valueA.equals(valueB));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac EqualsInheritanceDemo.java
java EqualsInheritanceDemo
~~~

Đầu ra kỳ vọng:

~~~text
naive parent -> child: true
naive child -> parent: false
flexible red -> plain: true
flexible plain -> blue: true
flexible red -> blue: false
strict parent -> child: false
strict child -> parent: false
strict red -> red copy: true
strict red -> blue: false
strict set size: 3
record value equality: true
~~~

**Đọc kết quả theo ba lớp vấn đề:**

- **Naive:** `NaivePoint` chấp nhận `NaiveColorPoint`; chiều ngược lại bị từ chối. Một chiều `true`, một chiều `false`: **đối xứng thất bại**.
- **Flexible:** cả `red` và `blue` đều bằng `plain` nhưng không bằng nhau: **bắc cầu thất bại**. Không nên áp dụng chiến lược này.
- **Strict:** so sánh chéo kiểu trả `false` ở cả hai chiều; hai `StrictColorPoint` cùng tọa độ và màu bằng nhau. `HashSet` chứa 3 giá trị phân biệt: điểm gốc, điểm đỏ, điểm xanh.
- **Record:** `ColoredPoint` là kiểu giá trị bất biến và mặc nhiên là `final`; Java sinh `equals()`/`hashCode()` từ các component. Không tồn tại lớp con `ColoredPoint` có thể âm thầm thay đổi nghĩa của equality.

Lưu ý quan trọng trong ví dụ **Strict**: lớp con có gọi `super.equals(other)`, nhưng cả lớp cha và lớp con đều kiểm tra chính xác lớp runtime trước khi so trường. Nếu bỏ kiểm tra `getClass()` ở lớp cha, hoặc ghi đè không đồng bộ giữa các lớp, kết quả có thể khác.

## 6. Khi nào dùng `getClass()`?

Dùng `getClass()` khi **hai object chỉ được bằng nhau nếu có cùng kiểu runtime chính xác**. Đây thường là lựa chọn rõ ràng khi một lớp cơ sở **có thể được kế thừa**, và các lớp con được phép bổ sung trường có ý nghĩa đối với equality.

Mẫu cho lớp cha:

~~~java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) {
        return false;
    }
    Point p = (Point) other;
    return x == p.x && y == p.y;
}
~~~

Đây là snippet đặt trong lớp `Point` có sẵn trường `x`, `y`; không phải chương trình độc lập. Một lớp con thêm dữ liệu bình đẳng có thể kiểm tra `getClass()` lần nữa, gọi `super.equals(other)`, rồi so các trường riêng và ghi đè `hashCode()` tương ứng.

**Ưu điểm:** ranh giới equality giữa các lớp rõ ràng; lớp cha và lớp con không bị tự động xem là cùng giá trị chỉ vì chia sẻ trường.

**Đánh đổi:** hai thực thể có cùng dữ liệu nghiệp vụ nhưng có **hai kiểu runtime khác nhau** sẽ không bằng nhau. Điều này có thể gây bất ngờ khi dùng **proxy subclass** do thư viện tạo động (chẳng hạn một số cơ chế ORM). Nếu ứng dụng sử dụng proxy, cần tìm hiểu quy tắc identity/equality của framework và thiết kế riêng cho mô hình entity đó; không thể sửa an toàn chỉ bằng thay `getClass()` thành `instanceof`.

`getClass()` cũng **không tự bảo đảm** hợp đồng: vẫn phải tránh trường mutable trong hash key, viết hash tương thích và duy trì equality khi lớp con bổ sung trạng thái.

## 7. Khi nào dùng `instanceof`?

`instanceof` hợp lý khi có **một khái niệm giá trị chung trên cả hệ phân cấp**, và các lớp con không được thay đổi tiêu chí bằng nhau, hoặc khi lớp **không cho phép kế thừa**.

Ví dụ đối với lớp bất biến và `final`:

~~~java
final class Point {
    private final int x;
    private final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Point p)) return false;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y);
    }
}
~~~

Đây là một khai báo lớp hoàn chỉnh, nhưng không có `main`; có thể dùng cùng các lớp khác trong dự án. Vì `Point` là `final`, không có `ColorPoint extends Point`, nên điểm yếu về lớp con bổ sung trường không phát sinh. Kiểu `record Point(int x, int y) {}` cũng là lựa chọn gọn khi đúng với thiết kế dữ liệu.

Nếu lớp vẫn cho kế thừa và dùng `instanceof`, phải trả lời câu hỏi: **lớp con có được thêm trường ảnh hưởng equality không?** Nếu không được, cần quy ước và ràng buộc thiết kế nhất quán, ví dụ dùng các lớp con chỉ bổ sung hành vi, không thay đổi danh tính giá trị. Chỉ dựa vào lời hứa "lớp con không override equals" không bảo vệ trước những thay đổi ngoài ý muốn về sau.

## 8. Mẫu `canEqual` cho hệ phân cấp cần mở rộng

Một kỹ thuật khác là cho cả hai vế **chấp thuận khả năng so sánh**. Lớp cha hỏi object bên kia có muốn được xem là cùng miền equality hay không qua `canEqual`. Lớp con bổ sung trạng thái có thể từ chối equality với lớp cha để bảo toàn đối xứng.

Minh họa phần thiết yếu (hai lớp dùng trong hệ phân cấp, không có `main`):

~~~java
import java.util.Objects;

class CanPoint {
    private final int x, y;

    CanPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    protected boolean canEqual(Object other) {
        return other instanceof CanPoint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CanPoint p)) return false;
        return p.canEqual(this) && x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

class CanColorPoint extends CanPoint {
    private final String color;

    CanColorPoint(int x, int y, String color) {
        super(x, y);
        this.color = color;
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof CanColorPoint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CanColorPoint p)) return false;
        return p.canEqual(this)
                && super.equals(other)
                && Objects.equals(color, p.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), color);
    }
}
~~~

Ở đây `new CanPoint(1,2).equals(new CanColorPoint(1,2,"red"))` trả `false`: đối tượng lớp con không `canEqual` với lớp cha. Chiều ngược lại cũng `false` vì kiểm tra `instanceof CanColorPoint` thất bại. Hai `CanColorPoint` cùng dữ liệu thì bằng nhau và có cùng hash.

`canEqual` hữu ích khi thực sự cần một **hệ phân cấp mở** với hợp đồng equality được kiểm soát, nhưng **không tự động giải quyết mọi nhánh kế thừa**. Các lớp con khác nếu bổ sung trạng thái có ý nghĩa vẫn phải phối hợp `canEqual`, `equals`, `hashCode` và bảo toàn quan hệ bắc cầu giữa các kiểu. Nếu không có yêu cầu cụ thể cho kế thừa, `final`, `record` hoặc **composition** thường giảm rủi ro hơn.

## 9. Thiết kế thay thế: composition và phân biệt entity với value object

Kế thừa dữ liệu không luôn là quan hệ phù hợp. Nếu `ColorPoint` thực chất là "một giá trị gồm một điểm và một màu", có thể dùng composition:

~~~java
record Point(int x, int y) {}
record ColoredPoint(Point point, String color) {}
~~~

Đây là hai kiểu record độc lập. `ColoredPoint` **chứa** `Point` thay vì **là một lớp con của** `Point`. Không có yêu cầu `Point.equals(ColoredPoint)` vì hai kiểu đại diện cho hai miền giá trị khác nhau. Equality do record sinh dựa trên các component; bất biến và không có lớp con làm hợp đồng dễ dự đoán.

Đối với **entity**, cần xem kỹ định danh nghiệp vụ có ổn định hay không. Hai lớp con đại diện cho cùng một thực thể lưu trữ có thể cần một chính sách khác so với value object, đặc biệt khi có ORM/proxy. Nếu equality theo ID, phải quyết định hành vi với ID chưa cấp, lớp runtime khác nhau và cách tính hash không đổi trong vòng đời entity. Không sử dụng `instanceof` như một cách vá chung cho mọi entity.

## 10. Ảnh hưởng trực tiếp đến collection

Một sai sót khi viết `equals()` không dừng lại ở phép `if`:

- `HashSet.add`/`contains` và `HashMap.put`/`get` cần `equals()` **và** `hashCode()` đúng hợp đồng. Nếu kết quả `a.equals(b)` phụ thuộc chiều gọi, việc nhận diện khóa có thể phụ thuộc thứ tự tra cứu và chi tiết triển khai; nếu hash cũng không nhất quán, object logic "bằng nhau" có thể bị lưu ở những bucket khác nhau.
- `List.contains` sử dụng equality để tìm phần tử; kết quả với quan hệ không đối xứng có thể không khớp trực giác khi đổi đối tượng tìm kiếm và phần tử chứa trong list.
- `TreeSet`/`TreeMap` dùng `compareTo` hoặc `Comparator` để xét khóa tương đương; `compare == 0` chưa chắc đồng nghĩa `equals == true`. Khi phối hợp equality và ordering, cần thiết kế cả hai hợp đồng, nhưng **đổi `getClass`/`instanceof` không tự sửa comparator**.

Nếu các trường tham gia equality/hash có thể thay đổi sau khi đưa vào hash collection, ngay cả triển khai `equals` vốn đúng cũng có thể làm tra cứu thất bại. Trường hợp value object nên ưu tiên bất biến.

## 11. Checklist trước khi quyết định kiểm tra kiểu

| Câu hỏi thiết kế | Hướng chọn |
|---|---|
| Lớp có thể được kế thừa không? | Nếu không cần: cân nhắc `final` hoặc `record` |
| Lớp con có thêm trường quyết định equality không? | Tránh để `instanceof` ở cha chấp nhận con theo một định nghĩa rộng hơn |
| Equality giữa các lớp runtime khác nhau có phải yêu cầu nghiệp vụ không? | Chỉ cho phép khi định nghĩa tương đương xuyên lớp thống nhất và bảo toàn mọi hợp đồng |
| Cần mọi lớp con có equality riêng theo đầy đủ trạng thái? | Cân nhắc `getClass()` và override đồng bộ tại từng lớp |
| Có proxy class do framework sinh ra không? | Xác minh quy tắc định danh của framework trước khi chọn runtime-class equality |
| Có thật sự cần kế thừa dữ liệu không? | Cân nhắc composition cho value object |
| Hash code có lấy từ đúng các trường dùng để so sánh không? | Kiểm thử các object bằng nhau có cùng hash |
| Có dùng làm khóa `HashMap`/`HashSet` không? | Ưu tiên trạng thái bất biến và hợp đồng equality rõ ràng |

**Kiểm thử quan hệ**, không chỉ vài cặp riêng lẻ: kiểm tra `a.equals(a)`, `a.equals(b) == b.equals(a)`, tam giác `a → b → c`, trường hợp `null`, object khác tọa độ/khác trường con, và mã băm của cặp bằng nhau. Khi hệ phân cấp sâu hoặc có nhiều nhánh, hãy thử cả đối tượng thuộc các lớp sibling, chứ không chỉ một lớp cha và một lớp con.

## 12. Tự kiểm tra

**Câu 1.** Lớp cha `Shape` dùng `other instanceof Shape` và chỉ so `id`. Lớp con `ColoredShape` bổ sung màu vào `equals`. Rủi ro nào xuất hiện?  
**Đáp án:** Đối xứng có thể vỡ: lớp cha cho hai object bằng nhau theo ID, lớp con từ chối đối tượng cha. Nếu hash ở lớp con cũng xét màu, còn có nguy cơ vi phạm hợp đồng hash.

**Câu 2.** Đổi lớp cha sang `getClass()` có tự động làm mọi `equals()` trong hệ phân cấp đúng không?  
**Đáp án:** Không. Nó chỉ từ chối equality giữa các lớp runtime khác nhau. So sánh trường, hash, tính bất biến và các override trong lớp con vẫn phải đúng.

**Câu 3.** Vì sao không nên cho `ColorPoint(red)` bằng `Point` và `Point` bằng `ColorPoint(blue)` nhưng lại nói `red` khác `blue`?  
**Đáp án:** Vì phá tính bắc cầu của `equals()`. Nếu A bằng B, B bằng C thì A phải bằng C.

**Câu 4.** Khi lớp giá trị không cần kế thừa và mọi trường đều phải tham gia equality, lựa chọn nào đơn giản?  
**Đáp án:** Một `record` hoặc lớp `final` bất biến, với `equals`/`hashCode` dựa trên toàn bộ trạng thái có ý nghĩa.

**Kết luận:** Chọn `instanceof` nghĩa là cho phép so sánh trong **họ kiểu**; chọn `getClass()` nghĩa là yêu cầu **chính xác cùng lớp runtime**. Với kế thừa, câu hỏi quan trọng nhất là **lớp con có thêm trạng thái làm thay đổi nghĩa của "bằng nhau" không?** Nếu có, cần đặt ranh giới equality rõ ràng hoặc thay đổi mô hình dữ liệu để tránh vi phạm đối xứng, bắc cầu và hash.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-005">Gọi super.equals() và những điều việc đó không tự bảo đảm</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi `super.equals()` và những điều việc đó không tự bảo đảm

Khi một lớp Java kế thừa lớp khác, việc gọi `super.equals(other)` trong `equals()` của lớp con trông có vẻ là cách tái sử dụng phần so sánh chung. **Nó chỉ gọi triển khai `equals()` của lớp cha; nó không tự động bảo đảm hợp đồng `equals()` trên toàn bộ cây kế thừa.** Đặc biệt, lớp cha và lớp con có thể bất đồng về loại đối tượng nào được phép bằng nhau; điều này làm hỏng **tính đối xứng** hoặc **tính bắc cầu** dù từng phép kiểm tra trường riêng lẻ đều có vẻ hợp lý.

Bài học tập trung vào cách hoạt động của `super.equals()`, những lỗi đặc trưng khi thêm trạng thái vào lớp con và các phương án thiết kế an toàn.

## 1. `super.equals(other)` thực sự làm gì?

Trong một phương thức của lớp con, lệnh:

```java
@Override
public boolean equals(Object other) {
    return super.equals(other);
}
```

gọi triển khai `equals(Object)` được kế thừa từ lớp cha. Ý nghĩa của lệnh phụ thuộc **lớp cha đã cài đặt `equals()` như thế nào**:

- Nếu lớp cha không ghi đè `equals()`, cuối cùng lời gọi dùng `Object.equals()`: chỉ hai tham chiếu trỏ tới cùng đối tượng mới bằng nhau (`this == other`).
- Nếu lớp cha ghi đè `equals()` để so sánh một số trường, lời gọi kiểm tra đúng những điều kiện mà lớp cha đã viết, bao gồm chính sách kiểm tra kiểu (`instanceof` hay `getClass()`).
- Nếu lớp cha có phương thức phụ được override rồi gọi từ `equals()`, việc thực thi bên trong vẫn có thể chịu ảnh hưởng của đa hình. Gọi `super.equals()` không có nghĩa là toàn bộ quá trình chạy bị tách khỏi lớp con.

Ví dụ:

```java
class Parent {
    final int id;

    Parent(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Parent other && id == other.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}

class Child extends Parent {
    final String label;

    Child(int id, String label) {
        super(id);
        this.label = label;
    }

    // Không ghi đè equals/hashCode:
    // label không tham gia vào quan hệ bằng nhau.
}
```

`new Child(1, "A").equals(new Child(1, "B"))` trả `true` vì lớp con đang thừa hưởng quy tắc chỉ xét `id`. Đây có thể là chủ ý nếu `label` chỉ là thông tin phụ, nhưng sẽ sai nếu `label` cũng là một phần giá trị phải so sánh. **Kế thừa `equals()` không tự quyết định trường mới nào quan trọng.**

Ngược lại, việc lớp con viết `return super.equals(obj) && ...` chỉ bổ sung một điều kiện ở **hướng gọi từ lớp con**. Lớp cha vẫn dùng định nghĩa cũ khi tự so sánh với lớp con.

## 2. Hợp đồng `equals()` cần giữ trong kế thừa

Với các đối tượng không null `x`, `y`, `z`, quan hệ bằng nhau hợp lệ phải thỏa:

1. **Phản xạ:** `x.equals(x)` là `true`.
2. **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng giá trị.
3. **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)`, thì `x.equals(z)`.
4. **Nhất quán:** khi trạng thái dùng để so sánh không đổi, các lần gọi phải cho cùng kết quả.
5. **So với null:** `x.equals(null)` là `false`.

Khi ghi đè `equals()`, còn phải bảo đảm hợp đồng với `hashCode()`: nếu `x.equals(y)` là `true` thì `x.hashCode() == y.hashCode()`. Chiều ngược lại không bắt buộc. Việc lớp cha có sẵn một triển khai `hashCode()` đúng cho **quy tắc bằng nhau của riêng lớp cha** không có nghĩa quy tắc đó còn đúng nếu lớp con định nghĩa lại tính bằng nhau.

**Điểm cốt lõi:** `equals()` là quan hệ giữa **hai đối tượng**, nên phải xét cả hướng lớp cha → lớp con lẫn lớp con → lớp cha, và cả chuỗi gồm **ba đối tượng** khi đánh giá tính bắc cầu.

## 3. Lỗi đối xứng: lớp cha nhận lớp con, lớp con từ chối lớp cha

Giả sử `Point` chỉ có tọa độ `(x, y)` và dùng `instanceof Point` trong `equals()`. `ColoredPoint` bổ sung màu sắc. Lớp con chỉ cho phép so sánh với `ColoredPoint`, sau đó gọi `super.equals(other)` và so thêm màu.

- `point.equals(coloredPoint)` có thể là `true` vì một `ColoredPoint` **cũng là** `Point` và có cùng tọa độ.
- `coloredPoint.equals(point)` là `false` vì đối tượng `point` không phải `ColoredPoint`.

Cả hai phương thức đều đọc đúng các trường mà chúng quan tâm, nhưng **vi phạm tính đối xứng**. `super.equals()` trả `true` cho hai lớp con cùng tọa độ không thể làm cho lớp cha tự biết về điều kiện màu sắc mới.

## 4. Lỗi bắc cầu: cố cho lớp con so bằng lớp cha khi phù hợp

Một cách vá hấp dẫn là để lớp con so sánh như `Point` nếu đối tượng kia chỉ là `Point`, nhưng so thêm màu nếu cả hai là `ColoredPoint`. Khi đó:

- `đỏ.equals(point)` là `true`, vì cùng tọa độ.
- `point.equals(xanh)` là `true`, vì cùng tọa độ.
- `đỏ.equals(xanh)` là `false`, vì khác màu.

Tính đối xứng cho ba phép so sánh này có thể được giữ, nhưng **tính bắc cầu bị phá**: từ `đỏ == point` và `point == xanh` đáng lẽ phải suy ra `đỏ == xanh`.

### 4.1. Chạy cả hai lỗi bằng Java 17+

Lưu thành file **`EqualsInheritancePitfall.java`**:

```java
import java.util.Objects;

class Point {
    private final int x;
    private final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        return obj instanceof Point other
                && x == other.x
                && y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

// Phiên bản 1: chỉ nhận đúng nhóm lớp con.
// Sai tính đối xứng khi Point vẫn nhận mọi instanceof Point.
class StrictColorPoint extends Point {
    private final String color;

    StrictColorPoint(int x, int y, String color) {
        super(x, y);
        this.color = Objects.requireNonNull(color);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StrictColorPoint other)) {
            return false;
        }
        return super.equals(obj) && color.equals(other.color);
    }

    // Cố ý giữ hashCode theo tọa độ để cô lập lỗi đối xứng.
    // Hash trùng vẫn hợp lệ; nó không cứu được equals sai.
}

// Phiên bản 2: so như Point khi gặp Point thông thường.
// Sai tính bắc cầu khi hai lớp con khác màu.
class FlexibleColorPoint extends Point {
    private final String color;

    FlexibleColorPoint(int x, int y, String color) {
        super(x, y);
        this.color = Objects.requireNonNull(color);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Point)) {
            return false;
        }
        if (!(obj instanceof FlexibleColorPoint other)) {
            return super.equals(obj);
        }
        return super.equals(obj) && color.equals(other.color);
    }

    // Cũng dùng hashCode theo tọa độ, đủ để minh họa
    // rằng hashCode hợp lệ không kéo theo equals hợp lệ.
}

public class EqualsInheritancePitfall {
    public static void main(String[] args) {
        Point point = new Point(2, 3);
        StrictColorPoint redStrict =
                new StrictColorPoint(2, 3, "red");

        System.out.println("point -> strict: "
                + point.equals(redStrict));
        System.out.println("strict -> point: "
                + redStrict.equals(point));

        FlexibleColorPoint red =
                new FlexibleColorPoint(2, 3, "red");
        FlexibleColorPoint blue =
                new FlexibleColorPoint(2, 3, "blue");

        System.out.println("red -> point: "
                + red.equals(point));
        System.out.println("point -> blue: "
                + point.equals(blue));
        System.out.println("red -> blue: "
                + red.equals(blue));
    }
}
```

Chạy:

```bash
javac EqualsInheritancePitfall.java
java EqualsInheritancePitfall
```

Kết quả:

```text
point -> strict: true
strict -> point: false
red -> point: true
point -> blue: true
red -> blue: false
```

Hai ví dụ là **phản ví dụ có chủ ý**, không phải mẫu `equals()` nên dùng trong dự án. Trong cả hai, các lớp con kế thừa hash theo tọa độ để tránh tạo thêm sự khác biệt về hash; **ngay cả khi mã băm như nhau, các lỗi đối xứng/bắc cầu vẫn tồn tại**. Đừng cho rằng cập nhật `hashCode()` có thể sửa các lỗi logic này.

## 5. `getClass()` có giúp không?

Một phương án đơn giản là lớp cha chỉ cho phép bằng nhau khi **chính xác cùng lớp runtime**:

```java
if (obj == null || getClass() != obj.getClass()) {
    return false;
}
```

Khi cả lớp cha và lớp con đều tôn trọng chính sách đó, `Point` sẽ **không bằng** `ColoredPoint` dù trùng tọa độ. Nhờ vậy không có tình trạng lớp cha chấp nhận một lớp con mà lớp con từ chối lớp cha.

Nếu lớp cha dùng `getClass()` và lớp con ghi đè `equals()`, lớp con **vẫn có thể gọi `super.equals()`** để tái sử dụng so sánh tọa độ. Đối với hai đối tượng cùng lớp con, `getClass()` trong phương thức lớp cha vẫn là lớp runtime của hai đối tượng, nên kiểm tra cùng lớp có thể thành công.

### 5.1. Mẫu an toàn: chính sách cùng lớp, trạng thái bất biến

Tạo file **`SafeSuperEqualsDemo.java`**:

```java
import java.util.Objects;

class Coordinate {
    private final int x;
    private final int y;

    Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Coordinate other = (Coordinate) obj;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}

final class ColorCoordinate extends Coordinate {
    private final String color;

    ColorCoordinate(int x, int y, String color) {
        super(x, y);
        this.color = Objects.requireNonNull(color);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        ColorCoordinate other = (ColorCoordinate) obj;
        return color.equals(other.color);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + color.hashCode();
    }
}

public class SafeSuperEqualsDemo {
    public static void main(String[] args) {
        Coordinate point = new Coordinate(2, 3);
        ColorCoordinate red =
                new ColorCoordinate(2, 3, "red");
        ColorCoordinate redCopy =
                new ColorCoordinate(2, 3, "red");
        ColorCoordinate blue =
                new ColorCoordinate(2, 3, "blue");

        System.out.println("point -> red: "
                + point.equals(red));
        System.out.println("red -> point: "
                + red.equals(point));
        System.out.println("red -> redCopy: "
                + red.equals(redCopy));
        System.out.println("red -> blue: "
                + red.equals(blue));
        System.out.println("equal hashes: "
                + (red.hashCode() == redCopy.hashCode()));
    }
}
```

Chạy:

```bash
javac SafeSuperEqualsDemo.java
java SafeSuperEqualsDemo
```

Kết quả:

```text
point -> red: false
red -> point: false
red -> redCopy: true
red -> blue: false
equal hashes: true
```

**Vì sao ví dụ này nhất quán?**

1. `Coordinate.equals()` so sánh `getClass()` trước, nên không chấp nhận đối tượng thuộc lớp khác. Lời gọi từ hai phía đều trả `false` giữa `Coordinate` và `ColorCoordinate`.
2. Với hai `ColorCoordinate`, `super.equals()` so tọa độ (và chính sách cùng lớp); sau đó `ColorCoordinate.equals()` so tiếp `color`.
3. `ColorCoordinate.hashCode()` kết hợp mã băm phần cha và `color`, phù hợp các thuộc tính mà `equals()` của lớp con xét.
4. Trường dữ liệu đều `final`; lớp `ColorCoordinate` cũng `final`, nên không có lớp con mới tiếp tục mở rộng cách so sánh của nó.

**Giới hạn:** chính sách `getClass()` định nghĩa *khác lớp runtime thì không bằng nhau*. Điều này có thể không phù hợp nếu ứng dụng chủ ý cần bình đẳng đa hình hoặc có proxy/subclass do framework tạo. Ngoài ra, `Coordinate` ở ví dụ vẫn cho phép được kế thừa; mỗi lớp con mới có trạng thái tham gia giá trị phải tiếp tục tôn trọng chính sách `equals()`/`hashCode()`. `super.equals()` không thể ép các lớp con tương lai tự làm điều đó.

## 6. Những cách tổ chức thiết kế khác

### 6.1. Ưu tiên bất biến, lớp `final`, `record` hoặc composition

Nếu lớp con chỉ khác về dữ liệu giá trị, thường dễ bảo toàn hợp đồng hơn khi **không dùng kế thừa để mở rộng danh tính giá trị**. Dùng hai record độc lập:

```java
record PointValue(int x, int y) {}

record ColoredPointValue(int x, int y, String color) {}
```

Java tự sinh `equals()` và `hashCode()` cho mỗi record dựa trên thành phần của **chính record đó**, với quy tắc so sánh theo loại record tương ứng. Hai `ColoredPointValue` có cùng `x`, `y`, `color` sẽ bằng nhau; `PointValue` và `ColoredPointValue` là hai kiểu độc lập.

Nếu cần tái sử dụng thông tin chung mà không muốn kế thừa quan hệ bằng nhau, dùng **composition**:

```java
record ColoredPointValue(PointValue point, String color) {}
```

Cách này làm rõ rằng màu sắc và tọa độ cùng góp phần xác định giá trị. Tuy nhiên, nếu nghiệp vụ coi màu chỉ là thông tin phụ và bình đẳng chỉ dựa trên tọa độ, cần chọn một định nghĩa khác có chủ ý; không nên máy móc so tất cả trường.

### 6.2. `final equals()` ở lớp cha

Nếu mọi lớp con **thật sự có chung một định nghĩa bằng nhau** (ví dụ tất cả chỉ được nhận dạng bằng một ID bất biến), lớp cha có thể quy định `equals()`/`hashCode()` là `final`, không cho lớp con thay đổi:

```java
@Override
public final boolean equals(Object obj) {
    // So sánh đúng theo một quy tắc chung đã chọn.
}
```

Điều này chỉ phù hợp nếu không lớp con nào cần bổ sung thuộc tính vào tính bằng nhau; nếu một lớp con cần phân biệt thêm màu, làm `equals()` của lớp cha thành `final` sẽ khiến màu không tham gia quan hệ bằng nhau. `final` ngăn ghi đè, **không tự chứng minh quy tắc trong lớp cha là đúng**.

### 6.3. Mẫu `canEqual` cho hệ phân cấp đa hình

Một số thiết kế dùng phương thức như `canEqual(Object)` để hai phía thỏa thuận có được phép bằng nhau hay không trước khi kiểm tra trường:

1. Lớp cha kiểm tra đối tượng kia là kiểu phù hợp, rồi yêu cầu `other.canEqual(this)` đồng ý.
2. Lớp con có thêm trạng thái thay đổi `canEqual()` để từ chối bằng nhau với lớp cha không mang trạng thái đó.
3. Lớp con kiểm tra thêm trường mới sau khi gọi `super.equals(other)`; mọi lớp trong hệ phân cấp phải triển khai cùng quy ước.

Mẫu này giúp xây dựng một số hệ phân cấp có thể mở rộng mà vẫn giữ đối xứng, nhưng khó bảo trì hơn: một lớp con bỏ qua quy ước có thể làm hỏng hợp đồng. Không có chuyện chỉ thêm một lệnh `canEqual()` là tự động bảo đảm mọi quan hệ bắc cầu hoặc chính sách hash. Khi không có yêu cầu kế thừa đa hình rõ ràng, composition/lớp bất biến thường dễ hiểu hơn.

## 7. Những điều `super.equals()` không tự bảo đảm

| Điều dễ ngộ nhận | Sự thật |
| --- | --- |
| “Lớp cha đã so đúng nên lớp con chắc chắn đúng.” | Lớp con có thể thêm trạng thái làm thay đổi quan hệ bằng nhau, gây bất đồng với lớp cha. |
| “Gọi `super.equals()` là đủ bảo đảm đối xứng.” | Chỉ kiểm tra một chiều; lớp cha có thể nhận lớp con trong khi lớp con không nhận lớp cha. |
| “Nếu đối xứng thì bắc cầu cũng đúng.” | Phiên bản so sánh linh hoạt theo kiểu ở ví dụ 4 vẫn có thể phá bắc cầu. |
| “Đã gọi `super.equals()` thì khỏi cần `hashCode()`.” | Nếu lớp con bổ sung trường vào tính bằng nhau, phải thiết kế hash nhất quán với định nghĩa cuối cùng. |
| “Có thêm `instanceof` trong lớp con là sửa được.” | Thường chỉ làm lớp con từ chối lớp cha, chưa xử lý cách lớp cha nhìn lớp con. |
| “Dùng `getClass()` luôn tốt hơn `instanceof`.” | Phù hợp với bình đẳng chính xác theo lớp, nhưng có thể bất tiện với proxy và ý nghĩa đa hình. |
| “Lớp cha `equals()` dùng tham chiếu thì lớp con tự so được dữ liệu.” | Nếu `super.equals()` cuối cùng là `Object.equals()`, hai đối tượng riêng biệt bị từ chối ngay cả khi trường giống nhau. |
| “Hai hash giống nhau chứng minh thiết kế equals đúng.” | Hash va chạm được phép; mã băm không chứng minh đối xứng hoặc bắc cầu. |

`super.equals()` cũng không tự động giải quyết so sánh null, tránh vòng lặp giữa các tham chiếu đối tượng, tính nhất quán khi dữ liệu thay đổi, hay những vấn đề khi lớp cha/lớp con thay đổi hợp đồng qua các phiên bản. Đây đều là trách nhiệm của mô hình giá trị và triển khai cụ thể.

## 8. Checklist khi review `equals()` có kế thừa

1. **Xác định lớp cha thực sự gọi triển khai nào:** `Object.equals()`, một `equals()` dựa trên `instanceof`, hay dựa trên `getClass()`? Các lớp trung gian có ghi đè không?
2. **Định nghĩa rõ bằng nhau:** cùng ID, cùng mọi thuộc tính giá trị, hay chỉ cùng loại cụ thể? Thuộc tính mới của lớp con có tham gia không?
3. **Thử hai chiều:** `parent.equals(child)` và `child.equals(parent)` phải giống nhau. Kiểm tra cả hai trường hợp tọa độ/ID giống và khác.
4. **Thử ba đối tượng:** một lớp cha và hai lớp con giống phần chung nhưng khác trường thêm vào; kiểm tra tính bắc cầu.
5. **Soát cặp hash:** nếu `equals()` của lớp con mở rộng điều kiện, `hashCode()` của lớp con phải bảo đảm hai đối tượng bằng nhau có cùng hash; không dựa vào sự trùng hash để chữa lỗi `equals()`.
6. **Kiểm tra `null`, chính đối tượng đó, nhiều lần gọi và trạng thái thay đổi.** Dùng các trường bất biến nếu đối tượng được đưa vào `HashSet` hoặc dùng làm khóa `HashMap`.
7. **Xem xét hệ quả lên collection:** khi `equals()` không đối xứng/bắc cầu, việc `HashSet.contains()`, `HashMap.get()` hoặc khử trùng theo khóa có thể trở nên khó dự đoán. `TreeSet`/`TreeMap` còn có hợp đồng thứ tự riêng qua `compareTo()`/`Comparator`.
8. **Quyết định chính sách kế thừa:** `final`/record/composition, `getClass()`, hoặc hợp đồng hợp tác như `canEqual`; đừng trộn các quy tắc không tương thích giữa các lớp.

**Kết luận kiến thức:** `super.equals(other)` là một lời gọi tái sử dụng phép so sánh phần cha. Để thiết kế `equals()` đúng trong kế thừa, phải xác định *cả hai đối tượng có cùng đồng ý về quan hệ bằng nhau hay không*, kiểm tra đối xứng và bắc cầu trên các tổ hợp lớp, đồng thời giữ `hashCode()` nhất quán. Nếu việc thêm trạng thái ở lớp con làm quy tắc bằng nhau khó diễn đạt, cân nhắc tách các giá trị thành kiểu bất biến hoặc dùng composition.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-006">Lớp final hoặc phương thức equals() final để giới hạn thay đổi hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp `final` hoặc phương thức `equals() final` để giới hạn thay đổi hợp đồng

## 1. Vấn đề khi `equals` gặp kế thừa

`Object.equals(Object)` biểu diễn quan hệ bằng nhau logic, không chỉ so sánh địa chỉ bộ nhớ. Hợp đồng yêu cầu **phản xạ** (`a.equals(a)`), **đối xứng** (`a.equals(b) == b.equals(a)`), **bắc cầu** (nếu `a=b`, `b=c` thì `a=c`), **nhất quán** khi dữ liệu so sánh không đổi và `a.equals(null) == false`. Ngoài ra, nếu `a.equals(b)` đúng thì `a.hashCode() == b.hashCode()` bắt buộc đúng. Hai hash bằng nhau **không** bắt buộc hai đối tượng bằng nhau.

Khi lớp cha định nghĩa `equals` bằng một trường như `id` nhưng lớp con tự thêm điều kiện `department`, hai lời gọi `parent.equals(child)` và `child.equals(parent)` có thể khác nhau. Ví dụ lớp cha chấp nhận mọi đối tượng cùng `id`, còn lớp con chỉ chấp nhận chính kiểu lớp con và cùng `department`. Kết quả là **đối xứng bị phá vỡ**; nhiều đối tượng còn có thể làm hỏng bắc cầu.

`HashSet` và `HashMap` phụ thuộc hợp đồng `equals`/`hashCode` để loại trùng, tra cứu và xóa. Nếu hợp đồng bị phá, có thể không tìm thấy khóa tương đương hoặc tập hợp chứa các phần tử “trùng” một cách khó dự đoán. Hai chiến lược ngăn lớp con tùy ý thay đổi quan hệ bằng nhau là **khóa toàn bộ kiểu với `final class`** và **khóa riêng phép bằng nhau bằng `final equals(Object)`**.

## 2. `final class`: không cho kế thừa

Khi khai báo lớp là `final`, Java cấm lớp khác `extends` nó. Không có lớp con để override `equals`, thay đổi `hashCode` hay thêm phần danh tính mà lớp gốc chưa dự liệu. Đây là lựa chọn phù hợp với **value object**, ID, khóa bộ nhớ đệm và kiểu dữ liệu không cần hệ phân cấp con.

Ví dụ trong tệp `AccountId.java`:

```java
import java.util.Objects;

public final class AccountId {
    private final String value;

    public AccountId(String value) {
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof AccountId that
                    && value.equals(that.value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
```

Lớp này biên dịch được trên Java 17+ bằng `javac --release 17 AccountId.java`. Hai `AccountId` có cùng `value` sẽ bằng nhau và có cùng hash. Trường `value` là `String` bất biến và được khai báo `final`; constructor từ chối `null`. `final class` cấm `class ChildId extends AccountId` ở thời điểm biên dịch.

Vì bản thân lớp đã không thể bị kế thừa, thường không cần đánh dấu từng `equals` và `hashCode` là `final` để chống override. Nhưng **`final class` không tự làm cho logic đúng**: lập trình viên vẫn có thể viết `equals` bất đối xứng hoặc hash không tương thích, hoặc dùng một trường mutable trong phép so sánh.

Record trong Java 17 cũng là kiểu **ngầm định `final`**. Chẳng hạn `record AccountId(String value) {}` sinh `equals`/`hashCode` từ thành phần `value`. Tuy nhiên, record chứa đối tượng mutable không tự có bất biến sâu; thành phần mảng được so sánh theo tham chiếu chứ không tự so sánh từng phần tử.

## 3. `final equals(Object)`: vẫn cho subclass, giữ chung một định nghĩa

Khi cần lớp con để mở rộng hành vi nhưng mọi lớp phải dùng chung danh tính, lớp cha có thể khóa `equals(Object)`:

```java
import java.util.Objects;

public class Member {
    private final String memberId;

    protected Member(String memberId) {
        this.memberId = Objects.requireNonNull(memberId);
    }

    @Override
    public final boolean equals(Object other) {
        return this == other
                || (other instanceof Member that
                    && memberId.equals(that.memberId));
    }

    @Override
    public final int hashCode() {
        return memberId.hashCode();
    }
}
```

Đây là lớp minh họa độc lập trong `Member.java`. `Member` không `final`, nên subclass được phép thêm trường và phương thức; nhưng **không được override** `equals(Object)` hoặc `hashCode()`. Mọi `Member` có cùng `memberId` đều bằng nhau, kể cả khi chúng thuộc hai lớp con khác nhau. Quy tắc này **chỉ đúng khi miền nghiệp vụ thực sự xem cùng ID là cùng danh tính xuyên mọi subtype**.

### Vì sao cần quan tâm `hashCode()`?

Khóa `equals` nhưng cho phép subclass override `hashCode` chưa đủ an toàn. Lớp con có thể tạo hash theo `memberId` *và* `level`, trong khi `equals` vẫn chỉ nhìn `memberId`. Khi đó hai đối tượng bằng nhau có hash khác nhau. Vì vậy, nếu lớp cha sở hữu một chính sách equality thống nhất, thường nên khai báo **cả `equals(Object) final` và `hashCode() final`**, rồi dùng cùng bộ thuộc tính bất biến để triển khai.

Nếu lớp con viết:

```java
// Cố ý không biên dịch: Member.equals(Object) là final.
class BadMember extends Member {
    BadMember(String id) {
        super(id);
    }

    @Override
    public boolean equals(Object other) {
        return false;
    }
}
```

thì trình biên dịch từ chối override. Lưu ý `equals(BadMember)` chỉ là một **overload**, không phải override `equals(Object)`. Collection trong Java dựa trên chữ ký `equals(Object)`; đừng dùng overload để thử thay đổi khái niệm bằng nhau vì kết quả lời gọi có thể phụ thuộc kiểu tĩnh của tham số.

## 4. Demo Java 17+ chạy được

Tạo `FinalEqualityDemo.java`. Hai ví dụ dùng hai cách khóa khác nhau; output cho thấy chúng hoạt động với `HashSet` như thế nào.

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class FinalEqualityDemo {
    static final class AccountKey {
        private final String id;

        AccountKey(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof AccountKey that
                        && id.equals(that.id));
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    static class Member {
        private final String id;

        protected Member(String id) {
            this.id = Objects.requireNonNull(id);
        }

        @Override
        public final boolean equals(Object other) {
            return this == other
                    || (other instanceof Member that
                        && id.equals(that.id));
        }

        @Override
        public final int hashCode() {
            return id.hashCode();
        }
    }

    static final class BasicMember extends Member {
        BasicMember(String id) {
            super(id);
        }
    }

    static final class PremiumMember extends Member {
        private final int rewardPoints;

        PremiumMember(String id, int rewardPoints) {
            super(id);
            this.rewardPoints = rewardPoints;
        }
    }

    public static void main(String[] args) {
        AccountKey a = new AccountKey("A-7");
        AccountKey b = new AccountKey("A-7");
        Set<AccountKey> keys = new HashSet<>();
        keys.add(a);
        keys.add(b);
        System.out.println("key equals: " + a.equals(b));
        System.out.println("key set size: " + keys.size());

        Member basic = new BasicMember("M-9");
        Member premium = new PremiumMember("M-9", 100);
        Set<Member> members = new HashSet<>();
        members.add(basic);
        members.add(premium);
        System.out.println("member forward: " + basic.equals(premium));
        System.out.println("member reverse: " + premium.equals(basic));
        System.out.println("member hash match: "
                + (basic.hashCode() == premium.hashCode()));
        System.out.println("member set size: " + members.size());
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac --release 17 FinalEqualityDemo.java
java FinalEqualityDemo
```

Stdout:

```text
key equals: true
key set size: 1
member forward: true
member reverse: true
member hash match: true
member set size: 1
```

Hai khóa `AccountKey` bằng nhau nên `HashSet` lưu một phần tử. `BasicMember` và `PremiumMember` cũng bằng nhau hai chiều vì `Member` áp dụng một chính sách khóa chung. Thuộc tính `rewardPoints` **không tham gia danh tính**, dù là trường của `PremiumMember`; đó là một lựa chọn miền dữ liệu, không phải một thuộc tính mà `final` tự suy ra.

Nếu nghiệp vụ đòi hỏi một `PremiumMember` phải khác `BasicMember` ngay cả khi ID giống nhau, việc khóa `equals` ở cha theo mỗi `id` là **không phù hợp**. Không nên cố sửa hậu quả ở `HashSet`: phải điều chỉnh mô hình danh tính.

## 5. `instanceof` hay `getClass()` bên trong phương thức `final equals`?

Hai lựa chọn kiểm tra kiểu dẫn đến ý nghĩa khác nhau:

| Kiểm tra | Cách hiểu về equality | Lưu ý |
| --- | --- | --- |
| `other instanceof Member` | Một instance lớp cha có thể bằng instance lớp con nếu khóa chung bằng nhau; hai subtype khác nhau cũng có thể bằng nhau | Chỉ dùng nếu ID có cùng ý nghĩa xuyên các subtype |
| `other != null && getClass() == other.getClass()` | Chỉ instance cùng **lớp runtime chính xác** mới bằng nhau | Subtype khác nhau không bằng nhau; trường riêng của subtype vẫn không được thêm vào `equals` đã khóa |

Ví dụ, với `final equals` dựa vào `getClass()`, `Member` sẽ không bằng `PremiumMember`. Nhưng hai `PremiumMember` cùng ID và khác `rewardPoints` vẫn có thể bằng nhau vì phương thức ở cha chỉ thấy `id`. Lớp con không thể override để bổ sung điểm thưởng vào equality.

Một số framework tạo proxy bằng subclass làm lớp runtime khác kiểu miền gốc. Do đó, chọn `getClass()` cần cân nhắc kiến trúc proxy và quy tắc identity thực tế. Với lớp đã `final`, `instanceof` hay `getClass` thường không tạo ra khác biệt do subclass là không thể.

## 6. So sánh hai chiến lược

| Tiêu chí | `final class` | `final equals` trên lớp có thể kế thừa |
| --- | --- | --- |
| Có subclass không? | Không | Có |
| Subclass thay đổi `equals` được không? | Không có subclass | Không thể override |
| Subclass thêm dữ liệu/hành vi? | Không | Có |
| Subclass định nghĩa lại identity riêng? | Không áp dụng | Không, trừ khi thay thiết kế lớp cha |
| `hashCode` có bị override không? | Không có subclass | Cần khai báo `final hashCode()` để cấm override |
| Chặn mọi lỗi hợp đồng? | Không | Không |
| Phù hợp nhất | Value object, key, identifier độc lập | Phân cấp chia sẻ chung một định nghĩa identity |

`final class` hạn chế mọi kế thừa, mạnh hơn nhưng cũng giảm khả năng mở rộng kiểu. `final equals` hẹp hơn: tiếp tục cho phép polymorphism nhưng khóa một chính sách cụ thể. Trong trường hợp thứ hai, cần bảo đảm không có phương thức còn override được làm thay đổi dữ liệu quyết định identity theo thời gian.

## 7. Những lỗi suy luận phổ biến

**Cho rằng final đồng nghĩa bất biến.** Một lớp `final` vẫn có thể chứa `List` hoặc mảng mutable; một trường `final` chỉ cố định tham chiếu, không cố định nội dung. Nếu hash phụ thuộc nội dung có thể sửa, đối tượng làm khóa `HashMap` có thể mất khả năng được tìm lại sau khi thay đổi.

**Khóa equality quá sớm.** Nếu lớp con cần dùng thuộc tính riêng trong tiêu chí danh tính, `final equals` ở cha sẽ ngăn điều đó. Khi ấy nên định nghĩa lại khóa chung sao cho chứa đầy đủ danh tính ngay khi khởi tạo, hoặc tách thành các value type `final` riêng. Có thể dùng composition/interface để chia sẻ hành vi mà không trói equality vào cây kế thừa.

**Chỉ khóa equals, quên hashCode.** Lớp con có thể override hash dẫn đến hai đối tượng bằng nhau nhưng khác mã hash. Ngay cả không có override, mọi thay đổi trong phương thức gốc phải giữ quan hệ bắt buộc giữa hai phương thức.

**Giả định tất cả subclass phải khác nhau.** Một `PremiumMember` và một `BasicMember` có thể là cùng thành viên với một ID; loại thành viên hoặc điểm thưởng là trạng thái, không hẳn danh tính. Ngược lại, nếu hai kiểu đại diện cho hai loại thực thể khác nhau, cùng ID có thể chưa đủ. Quyết định bằng nhau phải dựa vào miền dữ liệu.

**Mọi framework đều chấp nhận final.** Một số thư viện ORM/proxy cần subclass hoặc override phương thức để cung cấp chức năng; `final class`/`final method` có thể ảnh hưởng. Không chọn công thức equality cho entity dựa trên ví dụ value object mà thiếu phân tích identity và cơ chế của framework.

**Dùng `sealed` như thay thế tự động.** `sealed class` chỉ giới hạn **những subclass nào được phép kế thừa**; chúng vẫn có thể override `equals` nếu phương thức không `final`. Sealed giúp kiểm soát phạm vi kế thừa, nhưng không tự bảo đảm tính đối xứng hay bắc cầu.

## 8. Kiểm thử cần có trước khi khóa hợp đồng

Dùng ít nhất ba đối tượng đại diện cho cùng khóa, khác khóa và các subtype; kiểm tra:

1. `a.equals(a)` đúng; `a.equals(null)` sai.
2. `a.equals(b)` và `b.equals(a)` giống nhau, đặc biệt với cặp cha–con.
3. Nếu `a.equals(b)` và `b.equals(c)` thì `a.equals(c)`.
4. Nếu `a.equals(b)` thì `a.hashCode()==b.hashCode()`. **Không** kiểm tra chiều ngược vì va chạm hash hợp lệ.
5. Khi trạng thái liên quan không đổi, kết quả `equals`/`hashCode` ổn định qua nhiều lời gọi.
6. `HashSet` loại trùng theo đúng identity; `HashMap.get` tìm lại giá trị bằng một đối tượng mới nhưng bằng nhau logic.
7. Dữ liệu identity ổn định sau khi đưa đối tượng vào collection; rà soát các trường mutable và alias tham chiếu.
8. Nếu khóa `equals` ở cha, thử tất cả cặp subtype có cùng/khác ID; xác nhận semantics thật sự mong muốn, đồng thời kiểm tra `hashCode` không còn đường override gây mâu thuẫn.

Với `TreeSet`/`TreeMap`, còn phải kiểm tra `Comparable`/`Comparator` và ý nghĩa của `compare(a,b)==0`; `final equals` không tự ràng buộc comparator.

**Kết luận:** `final class` khóa **cả đường kế thừa**, phù hợp với loại dữ liệu không cần subclass. `final equals(Object)` khóa **một định nghĩa bằng nhau dùng chung cho phân cấp**, nên đi cùng `final hashCode()`. Cả hai chỉ hữu ích khi tiêu chí identity đúng, khóa đủ ổn định và mọi đối tượng liên quan được kiểm tra theo hợp đồng Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-007">Dùng composition khi mở rộng giá trị qua kế thừa làm hợp đồng khó giữ</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng composition khi mở rộng giá trị qua kế thừa làm hợp đồng khó giữ

## 1. Vấn đề: thêm “giá trị” vào subclass có thể phá hợp đồng equals

Trong Java, kế thừa (`extends`) phù hợp khi một đối tượng con thực sự có thể thay thế đối tượng cha theo những cam kết của kiểu cha. Nhưng **kế thừa một lớp giá trị chỉ để bổ sung thành phần dùng cho so sánh bằng nhau** thường tạo ra mâu thuẫn.

Xét `Point(x, y)` định nghĩa hai điểm bằng nhau nếu tọa độ giống nhau. Sau đó ta muốn `ColorPoint(x, y, color)` bổ sung màu và định nghĩa hai điểm có màu bằng nhau khi **cả tọa độ lẫn màu** giống nhau. Nếu `ColorPoint extends Point`, cách lớp cha nhìn equality và cách lớp con nhìn equality không còn cùng tiêu chí.

Hợp đồng `Object.equals(Object)` yêu cầu, với các đối tượng khác `null`:

- **Phản xạ:** `x.equals(x)` là `true`.
- **Đối xứng:** `x.equals(y)` và `y.equals(x)` có cùng kết quả.
- **Bắc cầu:** nếu `x.equals(y)` và `y.equals(z)` đều `true`, thì `x.equals(z)` cũng phải `true`.
- **Nhất quán:** kết quả ổn định khi trạng thái tham gia so sánh không đổi.
- **So sánh với null:** `x.equals(null)` là `false`.

Ngoài ra, hai đối tượng bằng nhau phải có cùng `hashCode()`. Việc cài đặt `equals(Object)` và `hashCode()` ở hai lớp phải tương thích, không chỉ đúng riêng rẽ ở từng lớp.

Điểm khó không nằm ở cách viết `instanceof` hay cú pháp Java, mà ở **hai định nghĩa giá trị khác nhau cùng xuất hiện trong một hệ phân cấp**: `Point` bỏ qua màu, còn `ColorPoint` muốn tính cả màu.

## 2. Bẫy đối xứng khi ghi đè equals trong lớp con

Giả sử lớp cha cài:

```java
@Override
public boolean equals(Object obj) {
    return obj instanceof Point other
            && x == other.x && y == other.y;
}
```

Đây là **đoạn trích minh họa**, giả sử `x`, `y` là các trường của `Point` và có `hashCode()` phù hợp. Vì dùng `instanceof Point`, lớp cha chấp nhận cả các subclass có cùng tọa độ.

Nếu subclass chỉ chấp nhận đối tượng `ColorPoint` rồi so thêm `color`, ta nhận được:

- `point.equals(redPoint)` → `true`: lớp cha chỉ xét tọa độ.
- `redPoint.equals(point)` → `false`: lớp con đòi đối tượng còn phải có màu.

Hai chiều khác nhau, nên **phá tính đối xứng**. Đổi `instanceof Point` thành `getClass() == obj.getClass()` ở lớp cha có thể thay đổi ngữ nghĩa để `Point` và `ColorPoint` không bằng nhau, nhưng không phải lúc nào cũng sửa được API lớp cha đã phát hành, và có thể đi ngược kỳ vọng mà người dùng đang dựa vào.

## 3. Bẫy bắc cầu khi cố “vá” đối xứng

Một cách vá hấp dẫn là: nếu `ColorPoint` được so với `Point` thường, hãy để `Point` quyết định; nếu so với `ColorPoint`, hãy xét cả màu. Cách này khiến mỗi cặp `Point`–`ColorPoint` có thể đối xứng, nhưng lại làm hỏng **tính bắc cầu**.

Tạo tệp đầy đủ `InheritanceTrapDemo.java`:

```java
import java.util.Objects;

public class InheritanceTrapDemo {
    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Point other)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColorPoint extends Point {
        private final String color;

        ColorPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Point)) {
                return false;
            }
            if (!(obj instanceof ColorPoint other)) {
                // Chấp nhận Point thường theo định nghĩa của lớp cha.
                return obj.equals(this);
            }
            return super.equals(other) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            // Phải khớp cả Point thường nếu equals cho rằng chúng bằng nhau.
            return super.hashCode();
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        ColorPoint red = new ColorPoint(1, 2, "red");
        ColorPoint blue = new ColorPoint(1, 2, "blue");

        System.out.println(red.equals(plain));
        System.out.println(plain.equals(blue));
        System.out.println(red.equals(blue));
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac InheritanceTrapDemo.java
java InheritanceTrapDemo
```

Kết quả:

```text
true
true
false
```

Ta có:

```text
red == plain   (true)
plain == blue  (true)
red == blue    (false)  <-- mâu thuẫn với tính bắc cầu
```

Vì `Point` xem hai đối tượng cùng tọa độ là bằng nhau, nó trở thành cầu nối giữa hai `ColorPoint` khác màu. Hai `ColorPoint` kia lại không bằng nhau theo định nghĩa mới. Đây là mâu thuẫn logic của cách tổ chức giá trị, không đơn thuần là lỗi điều kiện `if`.

Trong ví dụ, `hashCode()` của `ColorPoint` cố tình dùng `super.hashCode()` để không tạo thêm vi phạm quy tắc “bằng nhau ⇒ cùng mã băm” cho các cặp có `equals` trả `true`. Điều đó **không cứu được tính bắc cầu**, cũng không có nghĩa đây là implementation nên dùng trong sản phẩm. Lưu ý đoạn gọi `obj.equals(this)` chỉ minh họa trường hợp `Point` đơn giản này; nếu có thêm subclass với quy tắc so sánh riêng, cách chuyển quyền qua lại còn có thể phát sinh vấn đề khác.

## 4. Giải pháp: composition thay cho extends

**Composition (kết hợp đối tượng)** nghĩa là `ColorPoint` **chứa một `Point` làm thành phần** thay vì tự nhận mình là một subtype của `Point`.

```text
Kế thừa:                 Composition:

ColorPoint               ColorPoint
    extends Point            has-a Point
    + color                   + color
```

Khi đó có hai miền giá trị rõ ràng:

- `Point` so sánh tọa độ với các `Point` khác.
- `ColorPoint` so sánh tọa độ **và màu** với các `ColorPoint` khác.
- `Point` và `ColorPoint` không bị buộc phải coi nhau là bằng nhau chỉ vì có chung tọa độ.
- Nếu một API chỉ cần tọa độ, `ColorPoint` có thể **cung cấp thành phần `Point`** qua `position()` hoặc một phương thức chuyển đổi rõ ràng.

Tạo tệp đầy đủ `CompositionDemo.java`:

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CompositionDemo {
    static final class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Point other)) {
                return false;
            }
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColorPoint {
        private final Point point;
        private final String color;

        ColorPoint(Point point, String color) {
            this.point = Objects.requireNonNull(point);
            this.color = Objects.requireNonNull(color);
        }

        Point position() {
            return point;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ColorPoint other)) {
                return false;
            }
            return point.equals(other.point)
                    && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(point, color);
        }
    }

    public static void main(String[] args) {
        Point plain = new Point(1, 2);
        ColorPoint red = new ColorPoint(new Point(1, 2), "red");
        ColorPoint redCopy = new ColorPoint(new Point(1, 2), "red");
        ColorPoint blue = new ColorPoint(new Point(1, 2), "blue");

        System.out.println(plain.equals(red));
        System.out.println(red.equals(plain));
        System.out.println(red.equals(redCopy));
        System.out.println(red.equals(blue));
        System.out.println(red.position().equals(plain));

        Set<Object> unique = new HashSet<>();
        unique.add(plain);
        unique.add(red);
        unique.add(redCopy);
        unique.add(blue);
        System.out.println(unique.size());
    }
}
```

Biên dịch và chạy:

```bash
javac CompositionDemo.java
java CompositionDemo
```

Kết quả:

```text
false
false
true
false
true
3
```

**Giải thích:**

1. `plain.equals(red)` và `red.equals(plain)` cùng `false` bởi hai kiểu giá trị khác nhau; không xảy ra bất đối xứng.
2. `red.equals(redCopy)` là `true`: hai vị trí bằng nhau và hai màu bằng nhau. `hashCode()` của cả hai được tính từ cùng bộ thành phần `point` + `color`.
3. `red.equals(blue)` là `false` vì màu khác nhau. Không có một `Point` trung gian nào đồng thời bằng cả hai `ColorPoint`; loại bỏ bẫy bắc cầu đã nêu.
4. `red.position().equals(plain)` là `true`: người gọi vẫn lấy được *giá trị tọa độ* khi chỉ cần so sánh vị trí, mà không lẫn nó với equality của toàn bộ điểm có màu.
5. `HashSet` chứa đúng ba giá trị khác nhau: `plain`, `red`, `blue`; `redCopy` không tạo mục mới do bằng `red`.

Ở đây `Point` và `ColorPoint` đều `final`; dữ liệu gồm hai số nguyên và chuỗi `String` bất biến, nên mã băm không bị thay đổi sau khi đưa đối tượng vào tập hợp. Việc trả về trực tiếp `point` là an toàn **trong ví dụ vì `Point` bất biến**. Nếu thành phần bên trong có thể thay đổi, cần cân nhắc bản sao phòng vệ hoặc một giao diện chỉ đọc để bảo vệ bất biến và tính nhất quán của equality.

## 5. Vì sao composition giúp giữ hợp đồng tốt hơn?

| Tiêu chí | Kế thừa lớp giá trị rồi thêm trường vào equality | Composition |
| --- | --- | --- |
| Quan hệ kiểu | `ColorPoint` cũng là một `Point` | `ColorPoint` có một `Point` |
| Định nghĩa bình đẳng | Lớp cha và lớp con có nguy cơ xung đột | Hai loại giá trị xác định equality độc lập |
| Đối xứng, bắc cầu | Dễ vi phạm nếu lớp cha cho phép subclass bằng nhau | Dễ chứng minh hơn với các kiểu `final` và thành phần ổn định |
| `hashCode` | Phải giải quyết các đối tượng “bằng nhau” chéo lớp | Tính theo đúng thành phần của từng kiểu |
| Thay thế ở API nhận `Point` | Có thể truyền `ColorPoint` trực tiếp | Cần lấy `position()` hoặc chuyển đổi tường minh |
| Ý nghĩa thiết kế | Cam kết “is-a” / thay thế được | Diễn đạt quan hệ “has-a” và quyền sở hữu thành phần |

Composition có đánh đổi thực tế: `ColorPoint` không còn được truyền trực tiếp vào phương thức có tham số `Point`. Đây là thay đổi API có chủ ý. Nếu một API chỉ cần vị trí, ta có thể gọi `render(colorPoint.position())`; nếu API thực sự cần cả vị trí và màu, hãy dùng abstraction hoặc tham số phù hợp, thay vì giả vờ rằng hai kiểu có cùng identity.

**Composition không tự động làm equality đúng.** Bạn vẫn phải cài `equals(Object)` và `hashCode()` cùng một định nghĩa, xử lý `null`, trạng thái có thể thay đổi và lớp con. Nó chỉ loại bỏ sự xung đột không cần thiết giữa equality của hai lớp trong quan hệ kế thừa.

## 6. Java record là một lựa chọn ngắn gọn cho giá trị bất biến

Khi mục tiêu là mô hình hóa dữ liệu bất biến theo các thành phần, Java 17 có `record`. Sau đây là **đoạn khai báo minh họa** có thể đặt trong một tệp nguồn Java cùng cấp hoặc làm các kiểu lồng nhau:

```java
record Point(int x, int y) {}

record ColorPoint(Point position, String color) {
    ColorPoint {
        Objects.requireNonNull(position);
        Objects.requireNonNull(color);
    }
}
```

Đoạn trích cần `import java.util.Objects;`. Java tự sinh `equals(Object)` và `hashCode()` cho record dựa trên các thành phần đã khai báo, cùng cách kiểm tra kiểu phù hợp với record. `Point(1,2)` là một giá trị vị trí, còn `ColorPoint(new Point(1,2),"red")` là giá trị vị trí có màu: chúng không bị nhập nhằng thành cùng một kiểu bởi kế thừa.

Record không phải lời giải cho mọi trường hợp. Nếu một thành phần tham chiếu tới đối tượng có thể thay đổi, record chỉ giữ **tham chiếu** đó ở trường `final`, chứ không tự sao chép hay làm bất biến sâu. Nếu equality nghiệp vụ không tương ứng với tất cả thành phần của record, cần thiết kế khác.

## 7. Khi nào nên chọn composition và khi nào cân nhắc kế thừa?

**Ưu tiên composition** khi:

- Subclass định **thêm trường tham gia vào khái niệm bằng nhau theo giá trị** so với lớp cha, như thêm `color`, `currency` hay `unit`.
- Lớp cha đã có hợp đồng `equals` nhận các subclass là bằng nhau theo dữ liệu riêng của nó.
- Bạn không kiểm soát được cách lớp cha triển khai `equals` và `hashCode` (ví dụ lớp từ thư viện).
- Bạn muốn giữ rõ ràng ranh giới giữa một giá trị nền (`Point`) và giá trị mở rộng (`ColorPoint`).

**Kế thừa vẫn có thể phù hợp** khi subtype thực sự thay thế được supertype và không làm sai cam kết giá trị. Ví dụ một hierarchy của hành vi có thể sử dụng polymorphism mà không cần equality theo dữ liệu ở mỗi subclass; hoặc toàn bộ hierarchy được thiết kế và kiểm soát từ đầu bằng quy tắc equality thống nhất. Không có quy tắc rằng mọi trường hợp `extends` đều sai.

Có thể cân nhắc `getClass()` để chỉ coi các đối tượng cùng lớp là bằng nhau, hoặc một giao thức tương tác giữa lớp cha/con được thiết kế cẩn thận. Nhưng những cách đó thay đổi ý nghĩa equality và đòi hỏi hợp đồng toàn hierarchy nhất quán; thêm một phương thức kiểm tra kiểu đơn lẻ không tự chữa mọi vấn đề. Nếu muốn nhiều kiểu hỗ trợ cùng hành vi, interface mô tả hành vi có thể phù hợp hơn so với dùng chung kế thừa từ một lớp giá trị.

## 8. Checklist để thiết kế và kiểm thử

1. **Định nghĩa “bằng nhau” trước khi viết code:** yếu tố nào quyết định hai giá trị là một? Các kiểu có khác nhau về identity không?
2. **Kiểm tra quan hệ is-a:** có thể thay `Point` bằng `ColorPoint` ở mọi nơi mà hợp đồng vẫn đúng không? Nếu không, chọn quan hệ chứa đối tượng.
3. **Dùng đúng chữ ký:** `public boolean equals(Object obj)` kèm `@Override`; viết `hashCode()` từ cùng các trường tham gia equality.
4. **Kiểm thử đối xứng theo hai chiều:** `a.equals(b)` và `b.equals(a)` với cả cùng lớp và khác lớp.
5. **Kiểm thử bắc cầu trên ba đối tượng:** đặc biệt thử `red`, `plain`, `blue` có cùng vị trí nhưng màu khác nhau.
6. **Kiểm tra tính nhất quán và trạng thái:** nếu đối tượng được dùng làm khóa `HashMap` hoặc phần tử `HashSet`, tránh thay đổi trường tham gia `equals/hashCode` trong thời gian nó nằm trong bộ sưu tập.
7. **Kiểm thử qua API chung:** so sánh dưới kiểu `Object`, qua `Objects.equals`, và đưa giá trị vào `HashSet` để phát hiện lỗi hợp đồng mà cách gọi trực tiếp có thể bỏ sót.
8. **Giữ trách nhiệm rõ ràng:** API nào chỉ cần tọa độ thì nhận `Point`; API nào cần thông tin màu thì nhận `ColorPoint` hoặc abstraction thể hiện đủ dữ liệu.

**Tự kiểm tra:** Nếu `Point(1,2)` bằng cả `ColorPoint(1,2,"red")` và `ColorPoint(1,2,"blue")`, còn hai điểm có màu này không bằng nhau, hợp đồng nào bị vi phạm? **Tính bắc cầu.** Nếu `Point` nói “bằng” và `ColorPoint` nói “không bằng” cho cùng một cặp thì bị vi phạm **tính đối xứng**. Composition giúp định nghĩa các miền giá trị riêng và cung cấp tọa độ tường minh khi cần.

**Ghi nhớ:** Kế thừa là một cam kết thay thế được, trong khi equality là hợp đồng logic áp dụng xuyên suốt các lời gọi và collection. Khi subclass thêm thành phần giá trị làm tiêu chí equality của lớp cha không còn đủ, composition thường là thiết kế rõ ràng và ổn định hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-008">Mẫu canEqual(): mục đích, điều kiện phối hợp và giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Mẫu `canEqual()` trong kế thừa: mục đích, điều kiện phối hợp và giới hạn

## 1. Vì sao cần `canEqual()` khi ghi đè `equals()`?

Trong Java, `Object.equals(Object)` phải tuân thủ các tính chất **phản xạ**, **đối xứng**, **bắc cầu**, **nhất quán**, và `x.equals(null)` luôn là `false` với `x` khác `null`. Khi hai đối tượng được xem là bằng nhau, chúng còn phải có cùng `hashCode()`.

Kế thừa làm việc xây dựng quan hệ bằng nhau khó hơn vì lớp cha và lớp con có thể **khác nhau về các trường quyết định danh tính logic**. Một lớp `Point` chỉ có tọa độ (`x`, `y`); lớp `ColoredPoint` kế thừa `Point` nhưng bổ sung `color`. Nếu lớp cha dùng `instanceof Point` để so sánh tọa độ, còn lớp con chỉ nhận `ColoredPoint` và kiểm tra thêm màu, cùng một cặp đối tượng có thể cho hai câu trả lời trái ngược:

| Phép so sánh | Kết quả theo cách triển khai sai | Nguyên nhân |
| --- | --- | --- |
| `point.equals(coloredPoint)` | `true` | Lớp cha nhìn thấy đối tượng con là một `Point` và chỉ xét tọa độ |
| `coloredPoint.equals(point)` | `false` | Lớp con từ chối đối tượng không phải `ColoredPoint` |

Đó là vi phạm **tính đối xứng**: với hai tham chiếu không null `a` và `b`, `a.equals(b)` phải bằng `b.equals(a)`. Chỉ sửa ở lớp con chưa đủ nếu phương thức `equals` của lớp cha vẫn tự ý chấp nhận đối tượng lớp con.

**`canEqual()` là một mẫu thiết kế bổ trợ**, do người lập trình tự định nghĩa, cho phép đối tượng bên phải trả lời câu hỏi: *“Tôi có chấp nhận được so sánh bằng nhau theo kiểu của đối tượng bên trái không?”* Đây **không phải** phương thức có sẵn của `java.lang.Object` và Java **không** tự sinh nó cho các lớp thường.

## 2. Cơ chế: hai đối tượng phải đồng thuận

Trong một phiên bản đơn giản của mẫu, lớp cha triển khai:

```java
protected boolean canEqual(Object other) {
    return other instanceof Point;
}

@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof Point that)) return false;
    return that.canEqual(this) && x == that.x && y == that.y;
}
```

Đây là **đoạn minh họa nằm trong lớp `Point`**, giả sử lớp đã khai báo trường `x`, `y` và phương thức `canEqual` như trên; chưa phải một tệp Java độc lập.

Điểm then chốt là lời gọi **`that.canEqual(this)`**:

1. `this` là đối tượng đang được gọi `equals` (bên trái).
2. `that` là đối tượng bên phải đã được xác nhận là một `Point` hoặc lớp con.
3. Lời gọi `that.canEqual(this)` dùng **dynamic dispatch**: JVM chọn cách triển khai `canEqual` của **kiểu runtime của `that`**, không nhất thiết là cách triển khai tại `Point`.
4. Nếu `that` là `ColoredPoint`, nó có thể từ chối `this` chỉ thuộc `Point`. Lớp cha lúc này trả về `false` trước khi so sánh tọa độ.

Mẫu này cho phép một lớp con có thêm tiêu chí bằng nhau ngăn lớp cha xem nó là cùng giá trị với lớp cha. Đổi lại, **mọi lớp tham gia phải cùng tuân thủ quy ước**. Nếu lớp con bổ sung trạng thái vào `equals` nhưng không điều chỉnh `canEqual`, lớp cha vẫn có thể chấp nhận nó theo một chiều và làm hỏng đối xứng.

## 3. Chương trình Java 17+ hoàn chỉnh

Lưu mã sau vào **`CanEqualDemo.java`**. `Point` có tọa độ; `ColoredPoint` thêm màu sắc và ghi đè đồng bộ `canEqual`, `equals`, `hashCode`. Chương trình kiểm tra các cặp theo cả hai chiều.

```java
import java.util.Objects;

public class CanEqualDemo {
    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        protected boolean canEqual(Object other) {
            return other instanceof Point;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Point that)) return false;
            return that.canEqual(this)
                    && x == that.x
                    && y == that.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ColoredPoint extends Point {
        private final String color;

        ColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof ColoredPoint;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ColoredPoint that)) return false;
            return that.canEqual(this)
                    && super.equals(that)
                    && color.equals(that.color);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + color.hashCode();
        }
    }

    static void check(String label, Object a, Object b) {
        boolean forward = Objects.equals(a, b);
        boolean reverse = Objects.equals(b, a);

        System.out.printf("%s: forward=%s, reverse=%s%n",
                label, forward, reverse);

        if (forward != reverse) {
            throw new AssertionError("Broken symmetry: " + label);
        }
    }

    public static void main(String[] args) {
        Point base = new Point(2, 5);
        Point baseCopy = new Point(2, 5);
        ColoredPoint red = new ColoredPoint(2, 5, "red");
        ColoredPoint redCopy = new ColoredPoint(2, 5, "red");
        ColoredPoint blue = new ColoredPoint(2, 5, "blue");

        check("Point vs ColoredPoint", base, red);
        check("ColoredPoint copies", red, redCopy);
        check("Different colors", red, blue);
        check("Point copies", base, baseCopy);
        check("Point vs null", base, null);

        System.out.println("equal red objects share hash: "
                + (red.hashCode() == redCopy.hashCode()));
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac CanEqualDemo.java
java CanEqualDemo
```

Kết quả chuẩn:

```text
Point vs ColoredPoint: forward=false, reverse=false
ColoredPoint copies: forward=true, reverse=true
Different colors: forward=false, reverse=false
Point copies: forward=true, reverse=true
Point vs null: forward=false, reverse=false
equal red objects share hash: true
```

**Vì sao các dòng đó xuất hiện?**

- **`base.equals(red)`**: `red` thỏa `instanceof Point`, nhưng `red.canEqual(base)` gọi bản ghi đè tại `ColoredPoint`. Vì `base` không phải `ColoredPoint`, lời gọi trả `false`. Lớp cha từ chối so sánh chéo kiểu dù tọa độ trùng.
- **`red.equals(base)`**: điều kiện `base instanceof ColoredPoint` là `false`, nên lớp con cũng trả `false`. Hai chiều thống nhất.
- **`red.equals(redCopy)`**: cả hai đều là `ColoredPoint`; `that.canEqual(this)` chấp nhận, `super.equals(that)` xác nhận tọa độ, và `color.equals(that.color)` xác nhận màu giống nhau. Chiều đảo cũng `true`.
- **`red.equals(blue)`**: tọa độ giống nhưng màu khác, hai chiều đều `false`.
- **`base.equals(baseCopy)`**: cả hai đều là `Point` cùng tọa độ, hai chiều `true`.
- `Objects.equals(base, null)` và `Objects.equals(null, base)` đều `false`, giúp kiểm thử hướng đảo an toàn với `null`.
- Hai `ColoredPoint` bằng nhau phải cùng `hashCode`; ví dụ kiểm chứng điều đó. Hai đối tượng **không** bằng nhau vẫn được phép trùng hash.

Trong `ColoredPoint.equals`, điều kiện `that.canEqual(this)` thể hiện rõ sự đồng thuận của đối tượng bên phải. Lời gọi `super.equals(that)` còn kiểm tra `that.canEqual(this)` tại lớp cha, sau đó kiểm tra các tọa độ; ở ví dụ một cấp này, việc hỏi lại là thừa về mặt tính toán nhưng giúp lớp cha luôn tự duy trì hợp đồng của mình.

## 4. Những điều kiện cần phối hợp giữa lớp cha và lớp con

### 4.1. Lớp cha phải hỏi ý kiến đối tượng bên phải

`Point.equals` cần kiểm tra `other.canEqual(this)` **trước khi kết luận** bằng nhau theo các trường của lớp cha. Nếu lớp cha chỉ viết `other instanceof Point` rồi so sánh tọa độ, phương thức `canEqual` ở lớp con sẽ không được dùng và mẫu không giải quyết được tình huống ban đầu.

### 4.2. Lớp con có thêm trạng thái bình đẳng phải ghi đè `canEqual`

Nếu màu sắc tham gia quyết định `equals`, `ColoredPoint.canEqual` nên chấp nhận đúng nhóm đối tượng mà nó sẵn sàng so sánh theo định nghĩa ấy. Trong ví dụ, nó chỉ chấp nhận `ColoredPoint`.

Nếu lớp con chỉ thêm dữ liệu trình bày, **không** thêm trường tham gia vào định nghĩa bằng nhau, có thể không cần mở rộng quy tắc của lớp cha; tuy nhiên quyết định này phải được ghi nhận rõ trong mô hình. Không có nguyên tắc “mọi lớp con đều phải khác lớp cha” áp dụng cho mọi bài toán.

### 4.3. Lớp con phải phối hợp `equals` với phần bằng nhau được kế thừa

Lớp con cần kết hợp các phép kiểm tra: kiểu đối tượng phù hợp, đối tượng bên phải chấp nhận, phần dữ liệu của lớp cha bằng nhau (ví dụ `super.equals(that)`), và phần dữ liệu mới bằng nhau. Nếu bỏ `super.equals`, hai điểm khác tọa độ có thể bị coi là bằng nhau chỉ vì cùng màu.

Không đủ nếu chỉ ghi đè `canEqual`: **chính `equals` vẫn phải thực hiện đầy đủ so sánh thuộc tính**. Ngược lại, chỉ ghi đè `equals` mà không ghi đè `canEqual` trong lớp con có thêm trạng thái bình đẳng dễ tái tạo lỗi đối xứng.

### 4.4. `hashCode` phải theo cùng quan hệ bằng nhau

Khi `a.equals(b)` là `true` thì bắt buộc `a.hashCode() == b.hashCode()`. Trong ví dụ, `Point` dùng tọa độ, `ColoredPoint` kết hợp hash của phần cha với màu. Cả hai đối tượng `ColoredPoint` bằng nhau có hash bằng nhau.

Không suy luận chiều ngược: hai hash bằng nhau **không** kéo theo hai đối tượng bằng nhau. Nếu cho phép các kiểu khác nhau bằng nhau, cần bảo đảm cách tính hash của **mọi kiểu tương thích** thống nhất, không chỉ tính hash hợp lệ trong từng lớp.

### 4.5. Quy ước cần áp dụng xuyên suốt hệ phân cấp

Đối với hệ phân cấp nhiều tầng (`Point → ColoredPoint → LabeledColoredPoint`), mỗi tầng bổ sung thuộc tính tham gia bình đẳng phải cân nhắc cập nhật `canEqual`, `equals` và `hashCode`. Lớp ở tầng giữa cũng phải duy trì phép kiểm tra đồng thuận; nếu một lớp bỏ qua, bất đối xứng có thể trở lại.

Trong ví dụ, `ColoredPoint` được đánh dấu `final` để không phát sinh một tầng kế thừa mới tự ý thay đổi quy tắc. Đó là biện pháp thiết kế hữu ích, không phải điều kiện bắt buộc của mọi cách dùng `canEqual`.

## 5. `canEqual()` giải quyết được gì và **không** giải quyết được gì?

| Nội dung | Vai trò thực tế của `canEqual` |
| --- | --- |
| Đối xứng khi cha/con khác quy tắc giá trị | **Có thể hỗ trợ**: lớp con từ chối quan hệ với lớp cha; lớp cha tôn trọng quyết định đó |
| Phản xạ | Không tự bảo đảm; `equals` vẫn phải trả `true` khi cùng đối tượng |
| Bắc cầu | Không tự bảo đảm; nếu `a=b` và `b=c` thì thiết kế phải làm cho `a=c` |
| Nhất quán | Không tự bảo đảm nếu các trường tham gia so sánh thay đổi mà không có quy tắc rõ |
| So sánh với `null` | Không tự bảo đảm; `equals(null)` phải là `false` |
| Hợp đồng `hashCode` | Không tự bảo đảm; phải triển khai và kiểm tra riêng |
| Tính đúng đắn của mô hình miền | Không thay thế quyết định nghiệp vụ: đối tượng nào có thể đại diện cho cùng giá trị |

**Giới hạn lớn nhất:** `canEqual` là một *quy ước hợp tác*. Nó không có quyền ép mọi lớp con thực hiện đúng, không chứng minh được quan hệ bằng nhau là bắc cầu, và không ngăn mã khác ghi đè `equals` một cách không phù hợp. Nếu bên trái kết luận bằng nhau theo bộ trường A, bên phải dựa trên bộ trường B không tương thích, chỉ có một phương thức kiểm tra ý định không thể tự hòa giải hai định nghĩa.

**Đối tượng có thể thay đổi:** Nếu `x`, `y` hoặc `color` có thể bị sửa sau khi đưa đối tượng vào `HashSet` hay dùng làm khóa `HashMap`, giá trị hash có thể thay đổi và làm việc tìm kiếm sai mong đợi. Ví dụ dùng trường `final` nhằm loại trừ rủi ro này trong chính chương trình, nhưng thiết kế bất biến cũng cần áp dụng cho các đối tượng thành phần khi liên quan.

**Các kiểu lạ hoặc proxy:** Kiểm tra bằng `instanceof`, `getClass()` và `canEqual` biểu thị các chính sách tương thích kiểu khác nhau. Nếu framework tạo lớp con ở runtime, chính sách bằng nhau cần tính đến kiểu runtime và dữ liệu đại diện; không có một lựa chọn đúng cho mọi loại proxy hoặc entity.

## 6. So sánh với các phương án thiết kế khác

| Cách tiếp cận | Khi nào phù hợp | Đánh đổi |
| --- | --- | --- |
| `getClass() == other.getClass()` | Chỉ các đối tượng **cùng lớp runtime** mới có thể bằng nhau | Dễ hiểu, nhưng luôn bác bỏ bình đẳng giữa cha/con dù cùng tiêu chí |
| `instanceof` đơn thuần | Hệ phân cấp có định nghĩa bình đẳng thống nhất, lớp con không tự thêm trạng thái quyết định bình đẳng | Dễ bị vi phạm đối xứng/bắc cầu khi lớp con bổ sung tiêu chí |
| `instanceof` kết hợp `canEqual` | Cần cho phép từng kiểu kiểm soát khả năng bình đẳng xuyên hệ phân cấp | Linh hoạt hơn, nhưng mọi lớp liên quan phải hợp tác và kiểm thử cẩn thận |
| `final class` hoặc `record` | Đối tượng giá trị có danh tính logic rõ và không cần biến thể qua kế thừa | Ít rủi ro hợp đồng hơn; `record` tự sinh `equals`/`hashCode` theo thành phần |
| Composition | Kiểu mới **chứa** giá trị cũ và bổ sung thành phần như màu | Tách được các miền bình đẳng, tránh phải điều phối quan hệ cha–con |

Ví dụ, thay vì thiết kế `ColoredPoint extends Point`, có thể tạo hai `record` riêng:

```java
record PointValue(int x, int y) {}
record ColoredPointValue(PointValue point, String color) {}
```

Đây là **đoạn khai báo kiểu độc lập**, không phải toàn bộ chương trình chạy. Kiểu có màu chứa kiểu tọa độ và Java sinh phép so sánh theo thành phần. Một `PointValue` không bằng một `ColoredPointValue` theo mặc định ở cả hai chiều; trong trường hợp không cần bình đẳng xuyên kiểu, đó thường là thiết kế đơn giản hơn.

`canEqual` hữu ích khi kế thừa thật sự là yêu cầu của mô hình và không muốn áp dụng chính sách cứng “chỉ đúng cùng lớp”. Nó không phải một nghi thức buộc mọi lớp Java đều phải dùng.

## 7. Kiểm thử đầy đủ và các lỗi thường gặp

Khi có kế thừa, đừng chỉ viết assertion cho `a.equals(b)`. Kiểm tra **đảo chiều** và so sánh tất cả nhóm lớp có khả năng liên quan:

- Cùng tham chiếu; hai đối tượng khác tham chiếu nhưng cùng giá trị; cùng kiểu khác giá trị.
- Cha–con theo cả hai hướng; hai lớp con cùng kiểu; hai lớp con khác kiểu.
- `null` và đối tượng không liên quan; thứ tự gọi đảo.
- Ba đối tượng để kiểm tra tính **bắc cầu**; lặp phép so sánh khi không đổi trạng thái để kiểm tra **nhất quán**.
- Hai đối tượng được xác định bằng nhau phải có cùng `hashCode()`; thử dùng làm khóa trong `HashMap`/`HashSet` nếu đó là trường hợp sử dụng thật.

Những sai sót đáng chú ý:

1. **Quên `other.canEqual(this)` trong lớp cha:** lớp con có ghi đè `canEqual` vẫn không thể từ chối khi lớp cha chủ động so sánh.
2. **Lớp con thêm trường vào `equals` nhưng không ghi đè `canEqual`:** có thể khôi phục tình trạng lớp cha chấp nhận lớp con theo một chiều, lớp con từ chối lớp cha theo chiều kia.
3. **Không gọi `super.equals` khi cần so sánh trường kế thừa:** hai đối tượng có phần dữ liệu cha khác nhau vẫn có thể được đánh giá bằng nhau.
4. **Không đồng bộ `hashCode`:** các đối tượng bằng nhau có thể rơi vào những bucket khác nhau trong cấu trúc hash, vi phạm hợp đồng.
5. **Tưởng rằng `canEqual` tự kiểm tra mọi tính chất của `equals`:** nó chỉ hỗ trợ kiểm soát kiểu tương thích; cần kiểm thử cả bắc cầu, phản xạ và nhất quán.
6. **Viết `equals(Point)` thay vì `equals(Object)`:** đó là overload, không ghi đè đúng hợp đồng `Object.equals`. Dùng `@Override` giúp phát hiện nhầm chữ ký khi biên dịch.

**Bài tự kiểm tra:** Giả sử `Point.equals` chỉ so tọa độ và có gọi `other.canEqual(this)`, nhưng `ColoredPoint` ghi đè `equals` để so thêm màu mà **quên ghi đè `canEqual`**. Khi `point` và `coloredPoint` có cùng tọa độ, hãy tính hai chiều: `point.equals(coloredPoint)` có thể là `true` vì `ColoredPoint` kế thừa `Point.canEqual`; `coloredPoint.equals(point)` là `false` vì đối tượng cha không phải `ColoredPoint`. Đây là lý do **lớp cha và lớp con phải cùng phối hợp** thì mẫu mới giúp bảo toàn đối xứng.

Tóm lại, `canEqual()` là cách đưa quyền chấp nhận quan hệ bằng nhau về phía đối tượng được so sánh. Hãy dùng nó khi cần quản lý bình đẳng trong hệ phân cấp mở; bảo đảm mỗi tầng kết hợp `canEqual`, `equals` và `hashCode` nhất quán, rồi kiểm thử toàn bộ hợp đồng. Nếu không thật sự cần bình đẳng xuyên kiểu, các lớp giá trị `final`, `record` và composition thường giúp đơn giản hóa thiết kế.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-inheritance-009">Kiểm tra các cặp và bộ ba đối tượng ở nhiều mức kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra các cặp và bộ ba đối tượng ở nhiều mức kế thừa khi cài đặt `equals`

## 1. Vì sao cần thử nhiều đối tượng trong một hệ phân cấp?

Khi cài đặt `equals(Object)` cho lớp có quan hệ kế thừa, một phép thử `a.equals(b)` đơn lẻ không đủ để xác nhận hợp đồng bằng nhau. Lớp cha có thể chấp nhận lớp con, trong khi lớp con lại từ chối lớp cha vì muốn so sánh thêm trường riêng. Một cách sửa vội để làm đối xứng hai chiều còn có thể khiến **tính bắc cầu** bị vi phạm khi so sánh ba đối tượng.

Ví dụ một lớp `Point` chỉ có tọa độ và lớp con `ColoredPoint` có thêm màu sắc:

- Nếu `Point` coi mọi `ColoredPoint` cùng tọa độ là bằng mình, còn `ColoredPoint` từ chối `Point` vì thiếu màu, ta có thể gặp `a.equals(b) != b.equals(a)`: **vi phạm đối xứng**.
- Nếu `ColoredPoint` chấp nhận so với `Point` bằng tọa độ, nhưng đòi hai `ColoredPoint` phải cùng màu, thì một `Point` thường có thể trở thành cầu nối giữa hai `ColoredPoint` khác màu: **vi phạm bắc cầu**.

Vì thế, với một phân cấp như `Base → Middle → Leaf`, cần kiểm tra **cả hai hướng của mọi cặp** và **mọi thứ tự liên quan của bộ ba**. Mục tiêu là phát hiện trường hợp một triển khai có vẻ đúng khi so sánh những đối tượng cùng lớp nhưng sai khi so sánh qua các cấp kế thừa.

Đề bài tập trung vào **phương pháp kiểm tra** tính đúng đắn của `equals` qua nhiều mức kế thừa: chọn mẫu, lập ma trận cặp, kiểm tra bộ ba, nhận diện phản ví dụ và chọn chính sách bình đẳng phù hợp.

## 2. Hợp đồng cần dùng làm tiêu chuẩn kiểm tra

Với các tham chiếu đối tượng không phải `null`, `equals` phải tuân thủ:

| Thuộc tính | Biểu thức cần kiểm tra | Ý nghĩa khi kiểm thử |
| --- | --- | --- |
| Phản xạ (*reflexive*) | `x.equals(x) == true` | Từng đối tượng phải bằng chính nó. |
| Đối xứng (*symmetric*) | `x.equals(y) == y.equals(x)` | Một **cặp có thứ tự** phải được kiểm tra đủ hai chiều. |
| Bắc cầu (*transitive*) | Nếu `x.equals(y)` và `y.equals(z)` đều đúng thì `x.equals(z)` cũng đúng. | Kiểm tra **bộ ba có thứ tự**, đặc biệt qua lớp cha làm cầu nối. |
| Nhất quán (*consistent*) | Lặp lại phép so sánh khi trạng thái liên quan không đổi phải cho cùng kết quả. | Thử lặp và tránh sửa trạng thái giữa các phép thử. |
| So với `null` | `x.equals(null) == false` | Mọi đối tượng thật phải từ chối `null` mà không ném lỗi. |

Ngoài ra, quan hệ với `hashCode` bắt buộc là:

```text
Nếu a.equals(b) == true thì a.hashCode() == b.hashCode().
```

Chiều ngược lại **không bắt buộc**: hai đối tượng có thể cùng hash nhưng không bằng nhau. `equals` cần trả `false` cho đối tượng không tương thích về kiểu theo chính sách lớp, chứ không ném `ClassCastException` do ép kiểu thiếu kiểm tra.

Đây là các điều kiện **đồng thời**. Một lớp vượt qua thử nghiệm phản xạ hay đối xứng vẫn có thể thất bại ở tính bắc cầu. Những phép thử trên một tập mẫu hữu hạn giúp tìm phản ví dụ; chúng không thể chứng minh mọi trường hợp có thể có của lớp đều đúng.

## 3. Chọn cặp ở cùng cấp, khác cấp và nhánh anh em

Xét phân cấp ba mức:

```text
Point
  └── NamedPoint
        └── ColoredNamedPoint
```

Giả sử lớp cha xem tọa độ là bản chất, lớp giữa xem thêm tên, lớp dưới cùng xem thêm màu. Để phát hiện sai sót, nên tạo các đối tượng có thể so sánh một cách có chủ đích:

1. **Cùng cấp, cùng dữ liệu**: hai `Point` có tọa độ bằng nhau; hai `NamedPoint` cùng tọa độ và tên; hai `ColoredNamedPoint` có mọi trường giống nhau.
2. **Cùng cấp, khác dữ liệu**: chỉ đổi một trường tham gia `equals` để chắc chắn nhận `false` nếu trường đó thực sự ảnh hưởng đến bằng nhau.
3. **Cùng tham chiếu**: `a.equals(a)` và một biến alias `b = a`; đây là kiểm tra phản xạ, không phải đối xứng đầy đủ.
4. **Khác cấp kề nhau**: `Point ↔ NamedPoint`, `NamedPoint ↔ ColoredNamedPoint`, luôn gọi cả hai chiều.
5. **Cách nhau hai mức**: `Point ↔ ColoredNamedPoint`; không được bỏ qua vì hai lớp trung gian có thể có quy tắc khác nhau.
6. **Khác nhánh cùng cha**: nếu có hai lớp con khác nhau, kiểm tra `ChildA ↔ ChildB` cũng như những cặp với cha.
7. **`null` và kiểu không liên quan**: mỗi đối tượng so với `null` và một loại ngoài phân cấp để phát hiện lỗi kiểm tra kiểu.

Một **ma trận cặp** theo danh sách mẫu `[p, n, c]` tương đương với chín phép gọi:

| Bên trái `equals` | `p` (Point) | `n` (NamedPoint) | `c` (ColoredNamedPoint) |
| --- | --- | --- | --- |
| `p` | `p.equals(p)` | `p.equals(n)` | `p.equals(c)` |
| `n` | `n.equals(p)` | `n.equals(n)` | `n.equals(c)` |
| `c` | `c.equals(p)` | `c.equals(n)` | `c.equals(c)` |

Để xác minh **đối xứng**, so sánh mỗi ô `(i, j)` với ô đối diện qua đường chéo `(j, i)`. Chẳng hạn, `p.equals(c)` phải cho kết quả giống `c.equals(p)`. Nếu chỉ gọi theo một chiều hoặc chỉ so sánh hai bản thể cùng lớp, lỗi tại ranh giới cha/con có thể bị bỏ qua.

Không nên mặc định mọi cặp cha/con **phải bằng nhau**. Tùy mô hình dữ liệu, `Point` với `ColoredNamedPoint` có thể được định nghĩa luôn khác nhau, hoặc bằng nhau theo một khóa bất biến chung. Điều bắt buộc là chính sách đó phải nhất quán ở cả hai hướng và không phá hợp đồng.

## 4. Bộ ba kiểm tra bắc cầu: phải xét thứ tự

Nếu `a.equals(b)` đúng và `b.equals(c)` đúng thì bắt buộc `a.equals(c)` đúng. Điều kiện này không chỉ áp dụng khi ba đối tượng có cùng runtime class.

Ví dụ:

```text
red   = ColoredPoint(5, "red")
plain = Point(5)
blue  = ColoredPoint(5, "blue")

red.equals(plain)  → true
plain.equals(blue) → true
red.equals(blue)   → false   // VI PHẠM BẮC CẦU
```

`plain` có cùng tọa độ với cả hai điểm màu, nhưng `red` và `blue` lại khác nhau. Không thể chấp nhận đồng thời tất cả ba quy tắc này nếu muốn `equals` là quan hệ tương đương.

Với danh sách `n` mẫu, cách kiểm tra tổng quát:

```text
for mỗi a trong mẫu:
    for mỗi b trong mẫu:
        for mỗi c trong mẫu:
            nếu a.equals(b) và b.equals(c) và !a.equals(c):
                báo VI PHẠM BẮC CẦU với bộ ba (a, b, c)
```

Ba vòng lặp duyệt **bộ ba có thứ tự**, kể cả lặp lại đối tượng. Với `n` phần tử có nhiều nhất `n³` bộ ba, rất phù hợp cho một tập kiểm thử nhỏ đã chọn kỹ. Đổi thứ tự các phần tử có thể làm điều kiện tiên đề khác đi; vì thế chỉ thử `(a, b, c)` mà không xét thứ tự còn lại là chưa đủ.

Bộ ba nên bao gồm: ba đối tượng cùng lớp; một lớp cha ở giữa hai lớp con; lớp giữa ở giữa cha và cháu; ba cấp liên tiếp; hai nhánh con với cha chung; những đối tượng khác giá trị nhưng cùng phần dữ liệu của lớp cha.

## 5. Java 17+: chương trình kiểm tra tự động cặp và bộ ba

Ví dụ sau cố ý tạo **hai kiểu triển khai sai**, sau đó so với một mô hình hợp lệ. Tất cả đều nằm trong một tệp độc lập `HierarchyEqualsAudit.java`, không cần thư viện kiểm thử bên ngoài.

```java
import java.util.List;

public class HierarchyEqualsAudit {
    record Sample(String label, Object value) { }

    // Cấp cha: coi mọi Point có x bằng nhau là bằng nhau.
    static class Point {
        final int x;

        Point(int x) {
            this.x = x;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Point other)) return false;
            return x == other.x;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(x);
        }
    }

    // Cấp giữa: đòi phía bên kia ít nhất là NamedPoint.
    static class NamedPoint extends Point {
        final String name;

        NamedPoint(int x, String name) {
            super(x);
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof NamedPoint other)) return false;
            return super.equals(other) && name.equals(other.name);
        }
    }

    // Cấp lá: đòi phía bên kia là ColoredNamedPoint.
    static final class ColoredNamedPoint extends NamedPoint {
        final String color;

        ColoredNamedPoint(int x, String name, String color) {
            super(x, name);
            this.color = color;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ColoredNamedPoint other)) return false;
            return super.equals(other) && color.equals(other.color);
        }
    }

    // Cố sửa đối xứng khi so với Point thường, nhưng phá bắc cầu.
    static final class LenientColorPoint extends Point {
        final String color;

        LenientColorPoint(int x, String color) {
            super(x);
            this.color = color;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Point other)) return false;

            if (!(obj instanceof LenientColorPoint colored)) {
                return super.equals(other);
            }
            return super.equals(colored)
                    && color.equals(colored.color);
        }
    }

    // Phương án hợp lệ: mọi cấp cùng so sánh theo ID bất biến.
    static class Entity {
        private final int id;

        Entity(int id) {
            this.id = id;
        }

        @Override
        public final boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Entity other)) return false;
            return id == other.id;
        }

        @Override
        public final int hashCode() {
            return Integer.hashCode(id);
        }
    }

    static class DetailedEntity extends Entity {
        final String description;

        DetailedEntity(int id, String description) {
            super(id);
            this.description = description;
        }
    }

    static final class ColoredEntity extends DetailedEntity {
        final String color;

        ColoredEntity(int id, String description, String color) {
            super(id, description);
            this.color = color;
        }
    }

    static void audit(String name, List<Sample> samples) {
        boolean reflexive = true;
        boolean nullRule = true;
        boolean symmetric = true;
        boolean transitive = true;
        boolean hashAgreement = true;

        for (Sample a : samples) {
            Object left = a.value();

            if (!left.equals(left)) reflexive = false;
            if (left.equals(null)) nullRule = false;

            for (Sample b : samples) {
                Object right = b.value();
                boolean ab = left.equals(right);
                boolean ba = right.equals(left);

                if (ab != ba) symmetric = false;
                if (ab && left.hashCode() != right.hashCode()) {
                    hashAgreement = false;
                }

                for (Sample c : samples) {
                    Object third = c.value();
                    if (ab && right.equals(third)
                            && !left.equals(third)) {
                        transitive = false;
                    }
                }
            }
        }

        System.out.printf(
                "%s: reflexive=%s, nullRule=%s, symmetry=%s, "
                        + "transitivity=%s, hashAgreement=%s%n",
                name, reflexive, nullRule, symmetric,
                transitive, hashAgreement);
    }

    public static void main(String[] args) {
        Point base = new Point(5);
        NamedPoint middle = new NamedPoint(5, "oak");
        ColoredNamedPoint leaf =
                new ColoredNamedPoint(5, "oak", "red");

        System.out.println("base.equals(middle): "
                + base.equals(middle));
        System.out.println("middle.equals(base): "
                + middle.equals(base));
        audit("Strict subclasses", List.of(
                new Sample("base", base),
                new Sample("middle", middle),
                new Sample("leaf", leaf)));

        Point bridge = new Point(9);
        LenientColorPoint red = new LenientColorPoint(9, "red");
        LenientColorPoint blue = new LenientColorPoint(9, "blue");

        System.out.println("red.equals(bridge): "
                + red.equals(bridge));
        System.out.println("bridge.equals(blue): "
                + bridge.equals(blue));
        System.out.println("red.equals(blue): "
                + red.equals(blue));
        audit("Lenient subclasses", List.of(
                new Sample("red", red),
                new Sample("bridge", bridge),
                new Sample("blue", blue)));

        audit("Final inherited ID", List.of(
                new Sample("root", new Entity(7)),
                new Sample("middle", new DetailedEntity(7, "A")),
                new Sample("leaf", new ColoredEntity(7, "A", "red")),
                new Sample("other", new ColoredEntity(8, "B", "blue"))));
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac HierarchyEqualsAudit.java
java HierarchyEqualsAudit
```

Stdout:

```text
base.equals(middle): true
middle.equals(base): false
Strict subclasses: reflexive=true, nullRule=true, symmetry=false, transitivity=true, hashAgreement=true
red.equals(bridge): true
bridge.equals(blue): true
red.equals(blue): false
Lenient subclasses: reflexive=true, nullRule=true, symmetry=true, transitivity=false, hashAgreement=true
Final inherited ID: reflexive=true, nullRule=true, symmetry=true, transitivity=true, hashAgreement=true
```

**Cách đọc:**

- **`Strict subclasses`:** `Point.equals` dùng `instanceof Point`, nên nhận một `NamedPoint` cùng `x`. Nhưng `NamedPoint.equals` đòi đối số là `NamedPoint`, nên loại `Point`. Ma trận cặp phát hiện **đối xứng sai**. Với bộ ba mẫu cụ thể này, bắc cầu vẫn đúng; không thể kết luận phương thức hợp lệ chỉ vì cột bắc cầu là `true`.
- **`Lenient subclasses`:** `LenientColorPoint` bỏ qua màu khi đối số là `Point` thuần và so màu khi đối số cũng là `LenientColorPoint`. Mỗi cặp trong tập mẫu đối xứng, nhưng bộ ba `(red, bridge, blue)` làm **bắc cầu sai**.
- **`Final inherited ID`:** các cấp `Entity → DetailedEntity → ColoredEntity` kế thừa cùng một `equals/hashCode` dạng `final`, so sánh duy nhất theo `id`. Với chính sách đã chọn, lớp con không thể bổ sung điều kiện làm hai chiều hoặc bộ ba mâu thuẫn. Các trường mô tả/màu **cố ý không tham gia** quan hệ bằng nhau.

Trong ví dụ, các lớp sai `NamedPoint`, `ColoredNamedPoint` và `LenientColorPoint` kế thừa `Point.hashCode` dựa trên `x`. Đây là chủ ý để minh họa **đối xứng và bắc cầu độc lập** với lỗi hash: việc hai đối tượng không bằng nhau vẫn có thể trùng hash là hợp lệ. Trong hệ thống thật, cách chọn trường hash phải phù hợp với toàn bộ chính sách `equals`.

## 6. Mở rộng kiểm thử để bắt phản ví dụ tốt hơn

### Tạo mẫu khác biệt đúng một trường

Với mỗi mức, bổ sung các biến thể đổi một trường để phát hiện việc bỏ quên trường:

```text
Point(5), Point(6)
NamedPoint(5,"oak"), NamedPoint(5,"pine")
ColoredNamedPoint(5,"oak","red"),
ColoredNamedPoint(5,"oak","blue")
```

Nếu chính sách của lớp quy định trường màu phải tham gia bằng nhau, hai đối tượng khác màu không được tự nhiên bị gộp chung. Ngược lại, nếu toàn phân cấp cùng định nghĩa bằng nhau chỉ qua `id`, thay đổi các trường mô tả **không nên** thay đổi kết quả. Kết quả kỳ vọng phải xuất phát từ hợp đồng nghiệp vụ được chọn, không từ tên trường.

### Duyệt ma trận sau mỗi cấp mới

Khi thêm một lớp cháu, chạy lại không chỉ kiểm thử của lớp cháu mà cả các cặp với **mọi lớp tổ tiên** và những lớp con khác cùng cha. Lớp cha cũng có thể bắt đầu vi phạm hợp đồng khi một kiểu con mới xuất hiện, dù mã nguồn `equals` của lớp cha chưa hề thay đổi.

Có thể bổ sung danh sách mẫu qua bảng dữ liệu hoặc tham số hóa kiểm thử JUnit 5; phần quan trọng là cùng một bộ kiểm tra được áp dụng lên **tất cả các runtime type** trong phạm vi. Ví dụ `List<Sample>` của chương trình trên lưu chung các đối tượng kiểu `Object`, nên phép gọi `left.equals(right)` sử dụng **dynamic dispatch** tới phương thức thực tế của đối tượng bên trái. Đây chính là lý do đổi chiều phép gọi có thể cho kết quả khác nhau khi override sai.

### Xác minh hash, null, nhất quán và ngoại lệ

- Với mọi cặp mà `a.equals(b)` là `true`, kiểm tra hash bằng nhau. Không yêu cầu hai mã băm khác nhau khi `equals` là `false`.
- Với mọi đối tượng mẫu, thử `a.equals(null)` và `a.equals(new Object())`; kết quả kỳ vọng thông thường là `false`.
- Chạy lại các phép kiểm tra khi không thay đổi trạng thái liên quan để xem kết quả có ổn định.
- Nếu các trường tham gia `equals/hashCode` có thể thay đổi, thiết kế thêm thử nghiệm có kiểm soát. Tránh sửa các trường đó khi đối tượng đang làm khóa của `HashMap` hoặc nằm trong `HashSet`, vì có thể khiến thao tác tra cứu bị sai.
- Khi kiểm thử những cài đặt có khả năng ném ngoại lệ, bao rõ lỗi theo cặp/bộ ba để xác định phương thức nào bị hỏng. Ví dụ chương trình trên giả định `equals` trả boolean hợp lệ, nên không bao ngoại lệ trong vòng lặp.

Một tập thử nhỏ có thể chứa nhiều đối tượng **có cùng giá trị logic** nhưng khác tham chiếu, nhiều đối tượng khác nhau ở một trường, một đối tượng cha và nhiều lớp con. Chỉ sử dụng `a.equals(a)` hay hai instance cùng lớp không thể kiểm tra được nguy cơ do kế thừa.

## 7. Chọn chính sách bình đẳng phù hợp để kiểm thử có ý nghĩa

Có ba hướng thường gặp:

| Chính sách | Cách áp dụng | Điều phải chú ý |
| --- | --- | --- |
| **Bằng nhau theo kiểu runtime chính xác** | Dùng `getClass()` để yêu cầu cùng lớp trước khi so sánh trường. | Cha và con không bằng nhau, kể cả trùng dữ liệu cha; phải áp dụng nhất quán qua các override. |
| **Bằng nhau theo khóa chung bất biến** | Lớp cha định nghĩa `final equals/hashCode` dựa trên khóa đại diện cho cùng thực thể ở mọi cấp. | Trường bổ sung ở lớp con không được âm thầm thay đổi ý nghĩa của `equals`. |
| **Loại bỏ kế thừa khỏi lớp giá trị** | Dùng lớp `final` hoặc `record` cho kiểu giá trị; so sánh các thành phần được xác định rõ. | Các kiểu giá trị mới không tham gia bình đẳng qua một phân cấp có thể mở rộng. |

Nếu chọn kiểu runtime chính xác, một lớp cha có thể dùng:

```java
@Override
public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    Point other = (Point) obj;
    return x == other.x;
}
```

Đoạn này là **minh họa bên trong lớp `Point` có trường `x`**, không phải tệp chạy riêng. Nếu có lớp con override `equals`, chính sách kiểm tra kiểu và cách gọi `super.equals` vẫn phải được thiết kế đồng bộ. Chỉ thay `instanceof` thành `getClass` ở một vị trí, rồi giữ những so sánh lỏng lẻo khác, **không tự động** chứng minh cả phân cấp đúng.

Chính sách khóa chung `final` của ví dụ chỉ phù hợp khi các thực thể có cùng `id` thực sự được xem là cùng giá trị **bất kể kiểu con**. Không nên áp dụng máy móc cho các loại khác nhau mà `id` có thể trùng nhưng ý nghĩa nghiệp vụ khác nhau. Với loại dữ liệu không cần thay đổi cấu trúc bằng kế thừa, lớp `final` hoặc `record` thường giảm đáng kể số tình huống phải kiểm thử.

Một lựa chọn mở rộng khác là mẫu `canEqual` để phối hợp bình đẳng trong phân cấp có override. Mẫu đó chỉ hữu ích khi được thiết kế và dùng nhất quán ở mọi cấp; vẫn cần ma trận cặp và bộ ba như trên để xác nhận hợp đồng thực tế.

## 8. Chiến lược kiểm thử và cách đọc kết quả thất bại

Dùng một vòng lặp chung như `audit` giúp phát hiện **tính chất của quan hệ bằng nhau** mà không phải viết riêng một câu lệnh `assert` cho từng cặp. Tuy nhiên, để một bộ kiểm thử có giá trị lâu dài:

1. **Nêu rõ quy tắc bằng nhau dự kiến** cho các cặp cùng lớp và khác lớp; các tiên đề chỉ nói quan hệ phải nhất quán, không quyết định giúp bạn cặp nào phải bằng nhau.
2. **Chọn mẫu phủ ít nhất ba mức** và các nhánh con liên quan, không chỉ những đối tượng cùng lớp. Sử dụng nhiều giá trị và ít nhất một cặp khác instance nhưng được kỳ vọng bằng nhau.
3. **Kiểm tra phản xạ/null** trước; sau đó **ma trận cặp có hướng** để bắt đối xứng.
4. **Kiểm tra mọi bộ ba có thứ tự** để bắt bắc cầu, in ra tên `(a,b,c)` gây thất bại để dễ sửa.
5. **Kiểm tra hash khi bằng nhau** và chạy lại sau khi thay đổi mẫu/thiết kế.
6. **Sửa quy tắc so sánh ở cấp phù hợp**, rồi chạy lại toàn bộ bộ kiểm thử của hệ phân cấp thay vì chỉ test trường hợp vừa hỏng.

Với `n` mẫu, cặp là `O(n²)`, bộ ba là `O(n³)`; thường nên dùng tập mẫu có chủ ý trong unit test, kết hợp kiểm thử sinh dữ liệu (*property-based testing*) khi mô hình có nhiều biến thể. Nếu kiểm tra kết quả thay đổi theo trạng thái, cần đảm bảo không có luồng khác đồng thời sửa các trường trong lúc kiểm thử; các tiên đề của `equals` giả định trạng thái dùng để so sánh vẫn ổn định.

### Câu hỏi tự kiểm tra

1. Nếu `Base.equals(Child)` là `true` nhưng `Child.equals(Base)` là `false`, hợp đồng nào bị vi phạm?
2. Vì sao chỉ thử `a.equals(b)` mà không thử `b.equals(a)` có thể bỏ sót lỗi?
3. Với `red.equals(plain) == true`, `plain.equals(blue) == true`, nhưng `red.equals(blue) == false`, lỗi gì xảy ra? Thứ tự của ba đối tượng có ý nghĩa thế nào?
4. Nếu cả hai phía đều dùng mã băm bằng nhau nhưng không bằng nhau theo `equals`, đó có phải vi phạm không?
5. Khi thêm lớp cháu, vì sao phải chạy lại các kiểm thử của **lớp cha và lớp giữa**?
6. Khi nào được chủ động bỏ qua các trường mới của lớp con trong `equals`?

**Ghi nhớ:** Với `equals` và kế thừa, cần kiểm tra **mọi cặp theo hai chiều** để xác minh đối xứng và **mọi bộ ba có thứ tự** để xác minh bắc cầu. Hãy lập mẫu ở các cấp cha, con, cháu và nhánh khác nhau, đồng thời xác định rõ chính sách bình đẳng trước khi kết luận triển khai đúng.

</details>

- [Quay lại đầu trang](#back-to-top)
