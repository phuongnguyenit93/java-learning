<a id="back-to-top"></a>

# Thực hành tổng hợp

## Menu
- [Xây dựng đối tượng tọa độ bất biến với equals(), hashCode() và toString()](#practical-exercises-001)
- [Xây dựng khóa nghiệp vụ có nhiều trường](#practical-exercises-002)
- [Sửa lớp có equals(MyType) nhưng chưa ghi đè equals(Object)](#practical-exercises-003)
- [Thực nghiệm va chạm hash với các đối tượng không bằng nhau](#practical-exercises-004)
- [Tái hiện lỗi thay đổi khóa sau khi đưa vào HashMap](#practical-exercises-005)
- [So sánh HashSet và TreeSet với BigDecimal hoặc Comparator theo một khóa](#practical-exercises-006)
- [Thiết kế lớp chứa mảng và kiểm tra sao chép phòng vệ](#practical-exercises-007)
- [Rà soát một hệ lớp kế thừa làm mất tính đối xứng](#practical-exercises-008)
- [Tổng hợp danh sách câu hỏi trước khi công bố một lớp có hợp đồng giá trị](#practical-exercises-009)

## <a id="practical-exercises-001">Xây dựng đối tượng tọa độ bất biến với equals(), hashCode() và toString()</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: Xây dựng đối tượng tọa độ bất biến với equals(), hashCode() và toString()

## 1. Mục tiêu và yêu cầu

Bài thực hành xây dựng kiểu dữ liệu **tọa độ hai chiều** `Point2D` bằng Java 17+. Mỗi đối tượng biểu diễn một điểm có hai tọa độ nguyên `x` và `y`. Hai đối tượng độc lập phải được xem là **bằng nhau về giá trị** khi cả `x` lẫn `y` bằng nhau; các đối tượng này phải hoạt động đúng khi được dùng làm phần tử trong `HashSet` hoặc làm khóa trong `HashMap`.

Hoàn thành bài này, bạn cần hiểu và tự triển khai được:

1. **Tính bất biến (immutability):** sau khi được khởi tạo, trạng thái tọa độ của một `Point2D` không thể thay đổi.
2. **`equals(Object)`:** định nghĩa đúng thế nào là hai tọa độ bằng nhau; tuân thủ các tính chất phản xạ, đối xứng, bắc cầu, nhất quán và không bằng `null`.
3. **`hashCode()`:** các đối tượng bằng nhau phải có cùng mã băm; mã băm không phải định danh duy nhất.
4. **`toString()`:** xuất biểu diễn dễ đọc, ổn định và hữu ích khi ghi log/debug.
5. **Sử dụng thực tế:** tạo đối tượng bằng nhau từ các lần `new` khác nhau, đưa vào `HashSet`/`HashMap` và lý giải kết quả.

**Đặc tả:** tọa độ được biểu diễn bằng kiểu nguyên `int`; cho phép số âm, `0` và toàn bộ miền `int`. Bài tập chỉ xét **tọa độ 2D**, không thêm độ dài, vận tốc, màu sắc hay trạng thái phụ vào khái niệm bằng nhau.

## 2. Thiết kế lớp bất biến trước khi viết code

Một thiết kế tối giản nhưng đúng có các quyết định sau:

| Thành phần | Thiết kế | Vì sao |
| --- | --- | --- |
| Khai báo lớp | `public final class Point2D` | Không cho subclass thay ngữ nghĩa `equals()` hoặc mở rộng trạng thái theo cách gây bất ngờ. |
| Thuộc tính | `private final int x` và `private final int y` | Đóng gói, gán một lần; `int` là kiểu nguyên thủy nên không có trạng thái bên trong để ai khác sửa. |
| Constructor | Nhận `x` và `y`, gán vào hai trường | Thiết lập trạng thái đầy đủ từ lúc tạo. |
| API đọc | `x()` và `y()` | Chỉ trả về bản sao giá trị nguyên thủy; không có setter. |
| `equals(Object)` | So sánh cả `x` và `y`, sau khi kiểm tra kiểu | Bằng nhau theo nội dung tọa độ, không phụ thuộc cùng tham chiếu. |
| `hashCode()` | Kết hợp mã băm hai tọa độ theo phép tính cố định | Tương thích với `equals()`; kết quả không thay đổi theo thời gian. |
| `toString()` | `Point2D{x=3, y=7}` | Giúp nhận dạng trạng thái khi in/log. |

Không có `setX()`, `setY()` hay phương thức nào gán lại hai trường. Đặc tính bất biến là điều kiện then chốt giúp một `Point2D` giữ được mã băm ổn định khi nó đã được đưa vào collection dựa trên hash.

**Phân biệt:** `private` che chi tiết cài đặt; `final` trên trường ngăn gán lại trường sau khởi tạo; `final` trên lớp ngăn kế thừa. Riêng `final` không tự tạo bất biến sâu nếu trường tham chiếu tới `List`, mảng hoặc đối tượng có thể sửa. Ví dụ này dùng hai `int` nên không phát sinh bài toán sao chép phòng vệ (defensive copy).

## 3. Cài đặt đầy đủ Point2D.java

Tạo file tên chính xác là `Point2D.java`:

```java
public final class Point2D {
    private final int x;
    private final int y;

    public Point2D(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Point2D other)) {
            return false;
        }
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        return result;
    }

    @Override
    public String toString() {
        return "Point2D{x=" + x + ", y=" + y + "}";
    }
}
```

Đây là **một lớp Java độc lập biên dịch được**: file và tên lớp `public` trùng nhau. `instanceof Point2D other` là cú pháp pattern matching được hỗ trợ trong Java 17; nếu đối tượng kiểm tra là `null` hoặc không phải `Point2D`, nhánh điều kiện trả về `false` mà không ném `ClassCastException`.

### Vì sao equals(Object) không nhận Point2D làm tham số?

`Object` quy định chữ ký `public boolean equals(Object obj)`. Nếu bạn chỉ viết `public boolean equals(Point2D other)`, đó là **overload**, không override `Object.equals(Object)`. Các collection dùng `equals(Object)` sẽ tiếp tục nhận phép so sánh tham chiếu kế thừa từ `Object`, làm mất mục đích so sánh tọa độ. `@Override` giúp trình biên dịch phát hiện khi chữ ký không thật sự ghi đè phương thức của lớp cha.

### Giải thích từng nhánh equals

- `this == obj`: chính cùng đối tượng thì chắc chắn bằng nhau; đây là tối ưu an toàn, không thay thế việc so sánh giá trị giữa hai instance khác nhau.
- `!(obj instanceof Point2D other)`: trả `false` khi `obj` là `null` hoặc thuộc kiểu khác; sau điều kiện, biến `other` có kiểu `Point2D`.
- `x == other.x && y == other.y`: **cả hai** tọa độ phải giống nhau. Do `x`/`y` đều là `int`, toán tử `==` kiểm tra đúng **giá trị nguyên thủy**; nếu trường là tham chiếu tới một kiểu dữ liệu cần bằng nhau theo giá trị, phải cân nhắc `equals()` tương ứng.
- Vì lớp `Point2D` là `final`, ta không có subclass nào cần quyết định so sánh thêm hoặc bớt trạng thái; cách kiểm tra `instanceof` phù hợp với thiết kế này.

### Giải thích hashCode

Với hai tọa độ `int`, `Integer.hashCode(value)` trả về chính giá trị `int` đó. Công thức trên tương đương về kết quả với:

```java
return 31 * x + y;
```

Việc dùng hệ số `31` là một cách kết hợp trường phổ biến. Nó **không bắt buộc mã băm của mọi tọa độ phải khác nhau**. Chẳng hạn `Point2D(0, 31)` và `Point2D(1, 0)` cùng có `hashCode() == 31`, nhưng `equals() == false`.

Khi phép tính `int` vượt miền số nguyên 32 bit, Java áp dụng phép toán số nguyên với kết quả tràn theo quy tắc ngôn ngữ; hai đối tượng có cùng `x` và `y` vẫn nhận cùng một mã băm. Không cần xử lý ngoại lệ cho phép cộng hoặc nhân `int` ở đây.

Có thể sử dụng `Objects.hash(x, y)` cho tính tiện lợi, nhưng kết quả số học sẽ **khác** công thức đang dùng vì thuật toán gộp của `Objects.hash`; đồng thời lời gọi varargs có thể phát sinh chi phí boxing/array. Hãy chọn một cách nhất quán, không thay đổi mã băm trong khi phụ thuộc vào giá trị cũ được lưu/persist ở nơi khác. Với bài tập này, công thức tường minh dễ hiểu và không tạo bộ nhớ phụ.

### Giải thích toString

`toString()` trả về chuỗi nêu **đúng tên kiểu và cả hai tọa độ** theo định dạng do ta tự chọn. Mục tiêu là thông tin phục vụ người đọc và debug, **không phải** định dạng dữ liệu trao đổi được bảo đảm để parse. Không đưa địa chỉ bộ nhớ, identity hash code hay mã bí mật vào `toString()`. Trong bài này, hai tọa độ nguyên là thông tin công khai, an toàn để hiển thị.

## 4. Chương trình demo chạy được (CoordinateDemo.java)

Tạo file `CoordinateDemo.java` trong **cùng thư mục** với `Point2D.java`:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CoordinateDemo {
    public static void main(String[] args) {
        Point2D p = new Point2D(3, 7);
        Point2D sameCoordinates = new Point2D(3, 7);
        Point2D third = new Point2D(3, 7);
        Point2D otherCoordinates = new Point2D(3, 8);

        System.out.println("p = " + p);
        System.out.println("p == sameCoordinates: " + (p == sameCoordinates));
        System.out.println("p.equals(sameCoordinates): "
                + p.equals(sameCoordinates));
        System.out.println("p.equals(otherCoordinates): "
                + p.equals(otherCoordinates));
        System.out.println("p.equals(null): " + p.equals(null));
        System.out.println("p.equals(text): " + p.equals("3,7"));
        System.out.println("symmetric equality: "
                + (p.equals(sameCoordinates) == sameCoordinates.equals(p)));
        System.out.println("transitive equality: "
                + (p.equals(sameCoordinates)
                   && sameCoordinates.equals(third)
                   && p.equals(third)));
        System.out.println("hash(p): " + p.hashCode());
        System.out.println("hash(sameCoordinates): "
                + sameCoordinates.hashCode());

        Set<Point2D> set = new HashSet<>();
        System.out.println("set.add(p): " + set.add(p));
        System.out.println("set.add(sameCoordinates): "
                + set.add(sameCoordinates));
        System.out.println("set.add(otherCoordinates): "
                + set.add(otherCoordinates));
        System.out.println("set.size(): " + set.size());

        Map<Point2D, String> labels = new HashMap<>();
        labels.put(p, "checkpoint");
        System.out.println("lookup by equal key: "
                + labels.get(sameCoordinates));

        Point2D collisionA = new Point2D(0, 31);
        Point2D collisionB = new Point2D(1, 0);
        System.out.println("collision equal hashes: "
                + (collisionA.hashCode() == collisionB.hashCode()));
        System.out.println("collision equals: "
                + collisionA.equals(collisionB));

        Set<Point2D> collisionSet = new HashSet<>();
        collisionSet.add(collisionA);
        collisionSet.add(collisionB);
        System.out.println("collision set size: " + collisionSet.size());
    }
}
```

Từ thư mục chứa cả hai file, chạy bằng JDK 17 trở lên:

```bash
javac Point2D.java CoordinateDemo.java
java CoordinateDemo
```

Output dự kiến, không phụ thuộc thứ tự duyệt `HashSet`:

```text
p = Point2D{x=3, y=7}
p == sameCoordinates: false
p.equals(sameCoordinates): true
p.equals(otherCoordinates): false
p.equals(null): false
p.equals(text): false
symmetric equality: true
transitive equality: true
hash(p): 100
hash(sameCoordinates): 100
set.add(p): true
set.add(sameCoordinates): false
set.add(otherCoordinates): true
set.size(): 2
lookup by equal key: checkpoint
collision equal hashes: true
collision equals: false
collision set size: 2
```

**Đọc kết quả đúng bản chất:**

- `p == sameCoordinates` là `false` vì có hai lần `new` tạo hai instance; `equals` là `true` vì cặp giá trị (`3`, `7`) giống nhau. Đây là khác biệt giữa **danh tính** và **giá trị**.
- Cùng tọa độ nhận mã băm `31 * 3 + 7 = 100`; khác tọa độ có thể cùng mã băm nhưng vẫn **khác nhau theo `equals`**.
- `HashSet.add(sameCoordinates)` trả `false` vì đã có phần tử tương đương; `HashSet.add(otherCoordinates)` trả `true` vì đó là tọa độ khác.
- `HashMap.get(sameCoordinates)` truy xuất được giá trị đã lưu với key `p`: `HashMap` sử dụng mã băm để tìm ứng viên và `equals` để kiểm tra tính bằng nhau của key.
- Hai tọa độ cố ý trùng mã băm vẫn cùng nằm trong `HashSet` (kích thước 2). **Trùng hash không có nghĩa hai đối tượng bằng nhau.** Mã băm chỉ hỗ trợ tổ chức tra cứu.

Bảng kiểm nhanh:

| Kiểm tra | Kỳ vọng | Lý do |
| --- | --- | --- |
| `p.equals(p)` | `true` | Phản xạ. |
| `p.equals(new Point2D(3, 7))` | `true` | So sánh tọa độ. |
| `p.equals(new Point2D(3, 8))` | `false` | Khác `y`. |
| `p.equals(null)` | `false` | Không đối tượng nào bằng `null`. |
| `p.equals("3,7")` | `false` | Khác kiểu. |
| `new Point2D(-1, 0).equals(new Point2D(-1, 0))` | `true` | Số âm vẫn là giá trị hợp lệ. |
| `equals` là `true` giữa hai đối tượng | `hashCode` bằng nhau | Hợp đồng bắt buộc. |
| `hashCode` bằng nhau giữa hai đối tượng | **Chưa đủ kết luận** | Va chạm mã băm là hợp lệ. |

## 5. Kiểm tra hợp đồng Object một cách có hệ thống

### Hợp đồng equals(Object)

Các tính chất cần kiểm tra:

1. **Phản xạ (reflexive):** với tham chiếu không null `a`, `a.equals(a)` phải là `true`.
2. **Đối xứng (symmetric):** `a.equals(b)` và `b.equals(a)` luôn cho cùng kết quả.
3. **Bắc cầu (transitive):** nếu `a.equals(b)` và `b.equals(c)` đều `true` thì `a.equals(c)` cũng phải `true`.
4. **Nhất quán (consistent):** gọi nhiều lần với cùng trạng thái đầu vào phải cho cùng kết quả.
5. **Non-null:** với mọi tham chiếu `a` không null, `a.equals(null)` phải là `false`.

Cài đặt `Point2D` đáp ứng các tính chất này do quyết định equality **chỉ dựa trên hai giá trị nguyên không đổi**. Phản xạ được tối ưu bằng `this == obj`; các tính chất còn lại được bảo đảm bởi phép bằng nhau của hai cặp tọa độ nguyên.

### Hợp đồng hashCode()

1. **Nếu `a.equals(b) == true` thì `a.hashCode() == b.hashCode()` bắt buộc đúng.**
2. Nếu không có trạng thái liên quan đến `equals` thay đổi, mã băm của một đối tượng phải nhất quán giữa các lần gọi trong cùng lần thực thi.
3. **Nếu `a.equals(b) == false`, hai hash code không bắt buộc khác nhau.** Mọi thuật toán ánh xạ lượng giá trị lớn vào `int` đều có thể va chạm.

Chỉ override `equals` mà quên `hashCode` sẽ gây lỗi khó nhận ra trong `HashSet`, `HashMap` hoặc các cấu trúc dựa trên hash. Chiều ngược lại, chỉ override `hashCode` mà không override `equals` vẫn không làm hai instance khác nhau trở nên bằng nhau về giá trị.

### Hợp đồng toString()

`toString()` của `Object` không bắt buộc các tính chất tương đương nghiêm ngặt như `equals/hashCode`. Tuy nhiên, biểu diễn hữu ích nên mô tả nội dung, dễ đọc, nhất quán và tránh phụ thuộc thứ tự/tác động phụ ngoài ý muốn. Không sử dụng việc so sánh `toString()` thay cho `equals()`: hai lớp khác nhau có thể hiển thị giống nhau, và format chuỗi có thể được đổi để cải thiện log mà không đổi ý nghĩa bình đẳng.

## 6. Vì sao immutable object đặc biệt phù hợp làm key?

Giả sử lớp có setter cho `x` hoặc `y`. Sau khi đưa key vào `HashMap`, đổi tọa độ có thể khiến `hashCode()` thay đổi và `equals()` không còn tương ứng với vị trí key ban đầu trong bảng hash. Hậu quả: `map.get(key)` có thể không tìm ra mapping vừa ghi, ngay cả khi sử dụng **cùng tham chiếu key**. Đây là lỗi thiết kế, không phải lỗi của `HashMap`.

Đối tượng bất biến ngăn loại sai sót này: nội dung tham gia `equals/hashCode` được chốt ở constructor. Hãy coi quy tắc là: **mọi trường dùng để tính equality/hash của hash key phải có giá trị ổn định trong toàn bộ thời gian key tồn tại trong collection**.

Nếu cần “di chuyển” một tọa độ, tạo một instance mới:

```java
Point2D oldPoint = new Point2D(3, 7);
Point2D movedPoint = new Point2D(oldPoint.x() + 1, oldPoint.y());
// oldPoint vẫn là (3, 7); movedPoint là (4, 7).
```

Đoạn trên **minh họa API, không phải chương trình độc lập**; chạy nó trong một phương thức sau khi đã biên dịch `Point2D.java`. Tránh thực hiện thay đổi ngầm bên trong `translate` kiểu mutating; nếu bổ sung phương thức dịch chuyển, phương thức đó nên **trả về một `Point2D` mới**.

**Lưu ý về tràn số:** `oldPoint.x() + 1` trong Java dùng phép toán `int`; nếu giá trị cũ là `Integer.MAX_VALUE`, kết quả sẽ quay sang `Integer.MIN_VALUE` theo số học `int`. Nếu yêu cầu miền tọa độ cấm tràn, kiểm tra trước hoặc dùng `Math.addExact` và xử lý `ArithmeticException`. Điều này độc lập với hợp đồng `equals/hashCode`.

## 7. Những cách cài đặt sai thường gặp

### Sai 1: So sánh đối tượng bằng == thay cho equals

```java
new Point2D(3, 7) == new Point2D(3, 7) // false
```

Hai lần `new` sinh hai đối tượng khác nhau. Dùng `equals` để trả lời câu hỏi **tọa độ có trùng không**; dùng `==` khi thực sự cần kiểm tra **cùng instance trong bộ nhớ**.

### Sai 2: Chỉ so sánh một trường

```java
// Sai: bỏ qua y
return x == other.x;
```

Khi ấy (`3`, `7`) và (`3`, `8`) bị coi là một tọa độ. Đặc tả yêu cầu **cả x và y** tham gia.

### Sai 3: Chỉ override equals

```java
@Override
public boolean equals(Object obj) {
    // ... so sánh tọa độ ...
}
// Thiếu hashCode(): kế thừa identity-based hashCode từ Object.
```

Đây là **mã minh họa lỗi, không phải lớp có thể chạy độc lập**. Những đối tượng bằng nhau theo `equals` có thể bị hash khác nhau, khiến `HashMap/HashSet` không thực hiện đúng điều bạn mong đợi.

### Sai 4: Chỉ override hashCode hoặc dùng hashCode để quyết định equals

```java
// Sai: va chạm hash sẽ bị nhầm thành equality.
return this.hashCode() == other.hashCode();
```

Ví dụ `Point2D(0, 31)` và `Point2D(1, 0)` là hai tọa độ khác nhau dù chung hash 31. `equals` phải so sánh dữ liệu có ý nghĩa, không suy ngược từ hash.

### Sai 5: Dùng trường có thể sửa để tạo hash key

Ngay cả nếu trường được khai báo `private`, setter hoặc trả ra một mảng mutable có thể khiến nội dung khóa thay đổi. Với hai `int` và không setter, lớp hiện tại tránh được nguy cơ đó. Nếu sau này thêm `List`, mảng hoặc `Date`, phải thiết kế defensive copy và getter an toàn khi cần giữ bất biến sâu.

### Sai 6: Sai chữ ký hoặc quên @Override

`equals(Point2D)` **không** thay thế `equals(Object)`. Luôn khai báo phương thức đúng chữ ký và gắn `@Override`. Nếu để IDE tự tạo `equals/hashCode`, vẫn phải kiểm tra danh sách trường đã chọn tương ứng đúng yêu cầu business.

### Sai 7: Mở rộng lớp nhưng không thiết kế lại tính đối xứng

Một lớp tọa độ có thể kế thừa làm `ColoredPoint` với thuộc tính màu. Nếu lớp cha cho phép subclass bằng nhau khi cùng `x,y`, còn lớp con yêu cầu cả màu, có thể xuất hiện `base.equals(colored) == true` nhưng `colored.equals(base) == false`. Dùng `final class Point2D` ngăn tình huống kế thừa này. Nếu bài toán cần hệ phân cấp, phải thiết kế chính sách equality cho **toàn bộ hệ** một cách nhất quán; thay kiểm tra `instanceof` bằng `getClass()` cũng có đánh đổi và cần xét riêng.

## 8. So sánh với record trong Java 17+

Từ Java 16, `record` là cách khai báo ngắn gọn cho một nhóm dữ liệu bất biến theo mức độ cấu trúc. Nếu bài toán không yêu cầu tự tay thực hành ba phương thức, ta có thể viết:

```java
public record Point2DRecord(int x, int y) {}
```

Đoạn này cần đặt trong file `Point2DRecord.java`. Trình biên dịch tự tạo constructor chuẩn, accessor `x()`/`y()`, `equals`, `hashCode` và `toString` dựa trên các thành phần record. Hai bản ghi `Point2DRecord(3, 7)` bằng nhau về giá trị; chuỗi toString mặc định có dạng `Point2DRecord[x=3, y=7]`, khác định dạng thủ công của `Point2D`. Không yêu cầu hash code của record phải trùng con số `100` trong ví dụ lớp `Point2D`.

**Record không bảo đảm deep immutability:** nếu một component là mảng hoặc kiểu mutable, tham chiếu component là final nhưng nội dung bên trong vẫn có thể thay đổi. Muốn bất biến sâu, vẫn phải xem xét sao chép phòng vệ và cách xây dựng logic equality cho component đó.

Trong bài thực hành này, **tự triển khai `Point2D`** có giá trị học tập vì buộc ta hiểu và kiểm tra hợp đồng `Object`; `record` là phương án ngắn gọn để cân nhắc ở dự án thực tế khi phù hợp.

## 9. Bài tự luyện và tiêu chí hoàn thành

Để tự kiểm tra, thử các thay đổi sau trong bản sao chương trình và dự đoán trước output:

1. Tạo `new Point2D(-4, 0)` hai lần. Hai instance `==` có bằng nhau không? `equals` thì sao? Cặp hash có giống nhau không?
2. Thay `otherCoordinates` thành `new Point2D(3, 7)`. `set.add(otherCoordinates)` đổi từ `true` sang gì, và kích thước set khi ấy là bao nhiêu?
3. Thử `Point2D(0, 31)` và `Point2D(1, 0)`. Vì sao `HashSet` vẫn chứa cả hai mặc dù `hashCode()` bằng nhau?
4. Thêm phương thức `translate(int dx, int dy)` trả về điểm mới; xác minh điểm gốc và hash code của nó vẫn nguyên. Nếu muốn phát hiện tràn `int`, có thể dùng `Math.addExact`.
5. Tạm thời xóa `hashCode()` trong một bản thử nghiệm và chạy lại việc tra cứu `HashMap` bằng một key tương đương được tạo riêng. Giải thích vì sao chương trình không còn đảm bảo hoạt động đúng trong collection dựa trên hash.

**Tiêu chí đạt bài:**

- Lớp `Point2D` là `final`; `x` và `y` là `private final int`; có constructor đầy đủ, accessor, không có setter.
- `equals(Object)` kiểm tra đúng kiểu, xử lý `null`, và so sánh **cả hai** tọa độ.
- `hashCode()` sử dụng đúng những trường mà `equals()` sử dụng; ổn định sau khi khởi tạo.
- `toString()` hiển thị đúng hai tọa độ và không tác động trạng thái.
- Hai object cùng tọa độ được `HashSet` coi là một phần tử, có thể dùng thay nhau để tra cứu `HashMap`.
- Hai object có hash trùng nhưng khác tọa độ vẫn là hai phần tử khác nhau.
- Demo biên dịch với Java 17+ và tạo ra output nêu trên.

## 10. Kết luận kiến thức

Ba phương thức `equals()`, `hashCode()` và `toString()` phục vụ các mục đích khác nhau: **so sánh giá trị**, **hỗ trợ collection dựa trên hash**, và **biểu diễn dễ đọc**. Xây dựng `Point2D` bất biến khiến những quyết định về equality và hash ổn định, phù hợp để dùng như một value object. Khi mở rộng hoặc thay đổi định nghĩa tọa độ, luôn xem xét lại *cả* `equals` và `hashCode`, đồng thời kiểm chứng tác động lên `HashSet`/`HashMap`.

## Tài liệu tham khảo

- [Java SE 17 — Object.equals](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#equals(java.lang.Object)): các yêu cầu về tính tương đương.
- [Java SE 17 — Object.hashCode](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#hashCode()): hợp đồng khi dùng đối tượng trong cấu trúc hash.
- [Java SE 17 — Object.toString](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#toString()): biểu diễn chuỗi của đối tượng.
- [Java SE 17 — HashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html) và [HashSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html): hành vi của các collection dùng băm.
- [Java SE 17 — Record](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html): API nền của bản ghi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-002">Xây dựng khóa nghiệp vụ có nhiều trường</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng khóa nghiệp vụ có nhiều trường trong Java

## 1. Bài toán thực tế: một trường chưa đủ xác định đối tượng

Trong nhiều hệ thống, một mã có thể trùng giữa các khách hàng, kênh tích hợp hoặc phạm vi nghiệp vụ. Ví dụ, mã hóa đơn **INV-0042** có thể xuất hiện ở cả hai tenant; cùng một tenant còn có thể nhận mã đó từ hai hệ thống nguồn khác nhau.

Giả sử quy tắc nghiệp vụ đã được xác nhận:

> Một hóa đơn được nhận diện duy nhất bằng **(tenantId, sourceSystem, externalNumber)**. Cả ba trường đều bắt buộc; hệ thống coi các mã này không phân biệt chữ hoa/chữ thường và bỏ khoảng trắng thừa ở hai đầu.

Bộ ba đó là một **khóa nghiệp vụ ghép (composite business key)**. Nó biểu diễn *cùng một thực thể theo ý nghĩa nghiệp vụ*, kể cả khi hai đối tượng Java được tạo ở hai thời điểm khác nhau.

Ví dụ:

| tenantId | sourceSystem | externalNumber | Cùng khóa với hàng đầu? |
| --- | --- | --- | --- |
| SHOP-A | ERP | INV-0042 | Chính nó |
| shop-a | erp | inv-0042 | Có, theo quy tắc chuẩn hóa đã chọn |
| SHOP-B | ERP | INV-0042 | Không: tenant khác |
| SHOP-A | WEB | INV-0042 | Không: nguồn khác |
| SHOP-A | ERP | INV-0099 | Không: số hóa đơn khác |

Nếu chỉ dùng **externalNumber** làm khóa của **Map** hoặc làm điều kiện chống trùng, dữ liệu từ các tenant/nguồn khác nhau có thể ghi đè nhau. Nếu dùng nối chuỗi tùy tiện để tạo khóa, ta còn gặp rủi ro nhập nhằng và khó bảo trì.

Trong Java, lời giải thường là tạo **một kiểu dữ liệu dành riêng cho khóa**, định nghĩa tập trường tham gia so sánh, chuẩn hóa giá trị ở đầu vào, và bảo đảm **equals()/hashCode()** nhất quán.

## 2. Phân biệt khóa nghiệp vụ và khóa kỹ thuật

**Khóa nghiệp vụ** là tổ hợp thuộc tính có ý nghĩa với người sử dụng hoặc hệ thống tích hợp: tenant, nguồn, mã đối tác, ngày hiệu lực... Ví dụ **(tenantId, sourceSystem, externalNumber)**.

**Khóa kỹ thuật** là định danh hệ thống sinh ra, thường là **id** tăng dần hoặc **UUID**, phục vụ lưu trữ, tham chiếu nội bộ hay khóa chính của cơ sở dữ liệu.

Một hóa đơn có thể có:

- **id = 98217**: khóa kỹ thuật nội bộ.
- **(SHOP-A, ERP, INV-0042)**: khóa nghiệp vụ để tìm hóa đơn nhận từ ERP của tenant SHOP-A.

Hai loại khóa không loại trừ nhau. Một thiết kế phổ biến là dùng **id** làm khóa chính và áp dụng ràng buộc **UNIQUE** cho bộ ba nghiệp vụ. Khi số hóa đơn hoặc quy tắc nghiệp vụ có thể thay đổi, cần làm rõ khóa đó đại diện cho danh tính ổn định hay chỉ là khóa tra cứu/chống nhập trùng tại một thời điểm.

Một khóa ghép tốt phải trả lời được: **“Thay đổi đúng một trường trong bộ này có làm đối tượng trở thành thực thể khác không?”** Nếu có, trường đó thường thuộc khóa; nếu không, nhiều khả năng đó là thuộc tính thông thường.

## 3. Hợp đồng equals() và hashCode() đối với khóa ghép

**HashMap**, **HashSet** và các cấu trúc dựa trên hash sử dụng **hashCode()** để phân bố phần tử và **equals()** để xác định hai khóa có cùng danh tính hay không. Vì vậy:

1. **Phản xạ:** với khóa hợp lệ **a**, **a.equals(a)** là **true**.
2. **Đối xứng:** **a.equals(b)** và **b.equals(a)** có cùng kết quả.
3. **Bắc cầu:** nếu **a.equals(b)** và **b.equals(c)** thì **a.equals(c)**.
4. **Nhất quán:** khi những giá trị tham gia so sánh không đổi, gọi **equals()** nhiều lần phải cho cùng kết quả.
5. **So sánh với null:** **a.equals(null)** là **false**.
6. **Tương thích hash:** nếu **a.equals(b)** là **true** thì **a.hashCode() == b.hashCode()** phải đúng.

Chiều ngược lại **không bắt buộc**: hai khóa *khác nhau* có thể trùng hash vì va chạm hash là bình thường. Do đó không được xem **hashCode()** là mã định danh duy nhất và không dùng riêng giá trị hash để xác nhận trùng dữ liệu.

Khi xây khóa từ ba trường, cả ba trường phải có mặt trong phép so sánh danh tính; việc bỏ sót **sourceSystem** ở **equals()** hoặc **hashCode()** có thể tạo lỗi khó phát hiện. Đồng thời các trường tham gia hash cần **ổn định suốt thời gian khóa nằm trong Map/Set**.

## 4. Giải pháp Java 17+: dùng record bất biến

**record** phù hợp với khóa nghiệp vụ gồm những thành phần giá trị đơn giản. Java tự sinh constructor chuẩn, accessor, **equals()**, **hashCode()** và **toString()** dựa trên toàn bộ các thành phần của record. Ta có thể bổ sung compact canonical constructor để xác thực và chuẩn hóa đầu vào trước khi lưu.

Ví dụ hoàn chỉnh, lưu trong tệp **InvoiceKeyDemo.java**:

~~~java
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class InvoiceKeyDemo {
    public record InvoiceKey(
            String tenantId,
            String sourceSystem,
            String externalNumber) {

        // Compact canonical constructor:
        // chuẩn hóa trước khi Java gán vào các trường của record.
        public InvoiceKey {
            tenantId = normalize("tenantId", tenantId);
            sourceSystem = normalize("sourceSystem", sourceSystem);
            externalNumber = normalize("externalNumber", externalNumber);
        }

        private static String normalize(String field, String value) {
            Objects.requireNonNull(value, field + " must not be null");
            String result = value.strip().toUpperCase(Locale.ROOT);
            if (result.isEmpty()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return result;
        }
    }

    public static void main(String[] args) {
        InvoiceKey first = new InvoiceKey(
                "shop-a", "erp", " inv-0042 ");
        InvoiceKey same = new InvoiceKey(
                " SHOP-A ", "ERP", "INV-0042");
        InvoiceKey otherTenant = new InvoiceKey(
                "shop-b", "erp", "INV-0042");
        InvoiceKey otherSource = new InvoiceKey(
                "shop-a", "web", "INV-0042");

        Map<InvoiceKey, String> states = new HashMap<>();
        states.put(first, "NEW");
        states.put(same, "PAID");
        states.put(otherTenant, "NEW");
        states.put(otherSource, "REVIEW");

        System.out.println("same identity: " + first.equals(same));
        System.out.println("same hash: "
                + (first.hashCode() == same.hashCode()));
        System.out.println("different tenant: "
                + first.equals(otherTenant));
        System.out.println("different source: "
                + first.equals(otherSource));
        System.out.println("size: " + states.size());
        System.out.println("normalized: " + first);
        System.out.println("lookup: " + states.get(
                new InvoiceKey("SHOP-A", "ErP", "inv-0042")));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac InvoiceKeyDemo.java
java InvoiceKeyDemo
~~~

Kết quả chuẩn:

~~~text
same identity: true
same hash: true
different tenant: false
different source: false
size: 3
normalized: InvoiceKey[tenantId=SHOP-A, sourceSystem=ERP, externalNumber=INV-0042]
lookup: PAID
~~~

**Giải thích:** Hai đối tượng **first** và **same** khác tham chiếu nhưng giống nhau về danh tính nghiệp vụ sau chuẩn hóa. Lệnh **put(same, "PAID")** thay thế giá trị của khóa tương đương đã tồn tại nên **size** chỉ là **3**, không phải **4**. Hai hóa đơn khác tenant hoặc khác nguồn vẫn được lưu riêng. Lệnh tra cứu cuối cùng tạo một đối tượng khóa mới nhưng vẫn tìm đúng trạng thái **PAID**.

Mã trên dùng quy tắc **bỏ khoảng trắng hai đầu + không phân biệt hoa/thường cho cả ba trường** vì đó là giả định nghiệp vụ đã nêu. **Không áp dụng chuẩn hóa này một cách máy móc**: nếu tenantId, mã có chữ hoa/chữ thường, hoặc externalNumber thực sự phân biệt ký tự, chuyển đổi chữ hoa có thể gộp nhầm hai thực thể.

### Vì sao record hữu ích?

- **final theo bản chất:** không tạo subclass để thay đổi hành vi equality.
- **Thành phần cố định:** các field của record được gán một lần khi tạo đối tượng.
- **Ít code thủ công:** không phải viết lại equals/hashCode/toString dễ thiếu trường.
- **Rõ nghĩa:** accessor **tenantId()**, **sourceSystem()** và **externalNumber()** cho biết thành phần của khóa.
- **Dễ dùng làm key:** các thành phần đều là **String** bất biến, nên giá trị khóa không bị đổi sau khi đưa vào HashMap.

Lưu ý: record chỉ bảo đảm **bất biến nông (shallow immutability)**. Nếu thành phần là mảng, danh sách hay đối tượng mutable, nội dung bên trong chúng vẫn có thể đổi. Ví dụ **record Key(byte[] bytes)** không tự sao chép mảng và phép so sánh mảng mặc định theo tham chiếu; không nên dùng trực tiếp như khóa theo nội dung byte nếu chưa có thiết kế sao chép/phép so sánh phù hợp.

## 5. Khi cần dùng class: triển khai equals/hashCode đồng bộ

Nếu không dùng record (chẳng hạn cần một lớp có khả năng tùy biến constructor, cách trình bày API khác, hoặc đang ở codebase cũ), hãy thiết kế một **final class** với field **private final**, constructor kiểm tra đầu vào, và cả **equals()** lẫn **hashCode()** dựa trên cùng bộ trường.

Ví dụ Java 17 chạy độc lập trong **InvoiceKeyClassDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class InvoiceKeyClassDemo {
    public static final class InvoiceKey {
        private final String tenantId;
        private final String sourceSystem;
        private final String externalNumber;

        public InvoiceKey(
                String tenantId, String sourceSystem, String externalNumber) {
            this.tenantId = normalize(tenantId);
            this.sourceSystem = normalize(sourceSystem);
            this.externalNumber = normalize(externalNumber);
        }

        private static String normalize(String value) {
            Objects.requireNonNull(value, "key component");
            String result = value.strip().toUpperCase(Locale.ROOT);
            if (result.isEmpty()) {
                throw new IllegalArgumentException("Blank key component");
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof InvoiceKey other)) {
                return false;
            }
            return tenantId.equals(other.tenantId)
                    && sourceSystem.equals(other.sourceSystem)
                    && externalNumber.equals(other.externalNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, sourceSystem, externalNumber);
        }
    }

    public static void main(String[] args) {
        InvoiceKey a = new InvoiceKey("shop-a", "erp", "inv-0042");
        InvoiceKey b = new InvoiceKey("SHOP-A", "ERP", " INV-0042 ");
        InvoiceKey c = new InvoiceKey("shop-a", "web", "inv-0042");

        Set<InvoiceKey> keys = new HashSet<>();
        keys.add(a);
        keys.add(b);

        System.out.println("equal: " + a.equals(b));
        System.out.println("same hash: " + (a.hashCode() == b.hashCode()));
        System.out.println("other source equal: " + a.equals(c));
        System.out.println("set size: " + keys.size());
    }
}
~~~

~~~bash
javac InvoiceKeyClassDemo.java
java InvoiceKeyClassDemo
~~~

Kết quả chuẩn:

~~~text
equal: true
same hash: true
other source equal: false
set size: 1
~~~

**Objects.hash(...)** tính hash từ nhiều thành phần; không bắt buộc dùng thuật toán này, nhưng phải dùng **đúng các trường** xuất hiện trong **equals()**. Với khóa gồm trường bất biến, không cần tự cache hash trừ khi đã đo được nhu cầu; việc cache sai hoặc dựa trên field mutable dễ làm hỏng hợp đồng. Có thể dùng **instanceof** an toàn ở đây vì lớp khóa được khai báo **final**, nên không có subclass làm thay đổi quan hệ đối xứng.

## 6. Chọn đúng các trường tham gia khóa

Thiết kế key phải bắt đầu từ **quy tắc nhận diện**, không bắt đầu từ danh sách field đang có trong entity.

Ví dụ một bản ghi hóa đơn có thể chứa:

| Trường | Tham gia InvoiceKey? | Lý do |
| --- | --- | --- |
| tenantId | Có | Cùng số hóa đơn giữa tenant là hai hóa đơn khác nhau |
| sourceSystem | Có | ERP và WEB có namespace mã riêng |
| externalNumber | Có | Mã hóa đơn trong phạm vi tenant + nguồn |
| status | Không | Trạng thái NEW/PAID có thể thay đổi |
| totalAmount | Không | Tổng tiền có thể điều chỉnh |
| updatedAt | Không | Thời gian sửa đổi không phải danh tính |
| databaseId | Không trong business key | Định danh nội bộ, thuộc mô hình lưu trữ |
| displayName | Không | Tên hiển thị không xác định bản ghi |

Các câu hỏi cần xác minh cùng domain owner:

1. Một bộ giá trị có thể được tái sử dụng cho **hai thực thể khác nhau qua thời gian** không? Nếu có, có cần thêm loại phiên bản/ngày hiệu lực, hoặc không nên dùng nó làm danh tính bất biến?
2. Giá trị nào được xác định ngay khi tạo, giá trị nào chỉ xuất hiện sau khi quy trình hoàn tất? Khóa thiếu thành phần lúc đầu cần có chiến lược rõ ràng.
3. Tenant, region, provider, môi trường hoặc kênh nguồn có chia namespace độc lập không?
4. Giá trị có phân biệt hoa/thường, khoảng trắng, dấu tiếng Việt, Unicode hoặc số 0 ở đầu không?
5. Có trường nào thay đổi sau khi khóa đã được dùng để tra cứu, cache hoặc đồng bộ không?

Nếu một trường hợp pháp có thể đổi mà **vẫn đại diện cùng thực thể**, không nên dùng trường đó làm khóa định danh ổn định trong cache lâu dài. Có thể dùng **id** kỹ thuật cho identity và để business key đóng vai trò điều kiện lookup; xử lý việc thay đổi business key như thay đổi chỉ mục/ánh xạ chứ không tự giả định entity mới.

## 7. Chuẩn hóa và validation: định nghĩa thế nào là “bằng nhau”?

Chuẩn hóa tốt giúp loại bỏ khác biệt nhập liệu không mang ý nghĩa nghiệp vụ. Chuẩn hóa sai lại gây **mất thông tin** hoặc trộn nhầm khóa.

| Dữ liệu | Quyết định cần được chốt | Ví dụ |
| --- | --- | --- |
| Khoảng trắng đầu/cuối | Có bỏ không? | " INV-1 " → "INV-1" nếu khoảng trắng không mang nghĩa |
| Hoa/thường | Có phân biệt không? | "erp" = "ERP" nếu cùng mã nguồn |
| Unicode | Có chuẩn hóa NFC/NFKC không? | Ký tự có dấu biểu diễn theo nhiều chuỗi mã |
| Số 0 ở đầu | Có mang nghĩa không? | "0007" và "7" có thể là hai mã khác |
| Giá trị null/trống | Cấm hay có ý nghĩa? | tenantId null thường phải bị từ chối |
| Kiểu số | Số thực hay mã định danh? | "1.0" và "1.00" có thể khác khi là mã |
| Thời gian | Mốc tức thời hay ngày tại múi giờ nghiệp vụ? | LocalDate khác Instant |
| Phân tách chuỗi | Có escaping và schema không? | ("AB","C") khác ("A","BC") |

Một số nguyên tắc kỹ thuật:

- **String.strip()** (Java 11+) loại bỏ khoảng trắng Unicode theo định nghĩa của Java ở hai đầu; **String.trim()** có phạm vi xử lý ký tự khác. Chọn theo đầu vào thực tế.
- **toUpperCase(Locale.ROOT)** tránh phụ thuộc locale mặc định của máy, nhưng vẫn có thể làm hai mã khác nhau trở nên giống nhau. Quy tắc không phân biệt hoa/thường phải do nghiệp vụ quyết định và phải phù hợp với cách hệ thống khác xử lý mã.
- Với Unicode có nhiều cách biểu diễn tương đương, có thể cân nhắc **java.text.Normalizer** theo chuẩn NFC khi thật sự cần; không tự dùng NFKC nếu việc gộp ký tự tương thích làm mất phân biệt nghiệp vụ.
- Với **BigDecimal**, **new BigDecimal("1.0").equals(new BigDecimal("1.00"))** là **false** vì scale khác; **compareTo(...) == 0** lại là **true**. Nếu giá trị số là thành phần khóa, xác định rõ ý nghĩa scale trước khi chọn dạng chuẩn, tránh sửa equality rời rạc.
- Với ngày giờ, dùng kiểu bất biến thích hợp (**LocalDate**, **Instant**, ...), quy định múi giờ tại ranh giới chuyển đổi thay vì lấy múi giờ mặc định khác nhau giữa các máy.
- Với khóa nullable, quyết định null có hợp lệ hay không trước khi viết **equals()**. Nếu có trường tùy chọn, phải nêu rõ sự vắng mặt tác động thế nào đến uniqueness; nhiều cơ sở dữ liệu xử lý **NULL** trong ràng buộc UNIQUE khác với so sánh Java.

**Validation đặt ngay tại lúc tạo khóa** có lợi vì sau đó mọi đối tượng khóa hợp lệ đều tuân cùng quy tắc. Nếu app, batch import và database dùng ba cách chuẩn hóa khác nhau, việc chống trùng sẽ không nhất quán. Tốt nhất nên có một chính sách chuẩn hóa được kiểm thử, áp dụng tại boundary và phản ánh vào cách lưu/index dữ liệu.

## 8. Vì sao không dùng nối chuỗi làm khóa tùy tiện?

Đoạn mã sau dễ gây nhầm lẫn:

~~~java
// Minh họa lỗi, không phải chương trình độc lập:
String key = tenantId + sourceSystem + externalNumber;
~~~

Các bộ thành phần **("AB", "C", "D")** và **("A", "BC", "D")** đều thành **"ABCD"**. Dùng delimiter đơn giản cũng có thể đụng độ nếu một thành phần tự chứa delimiter:

~~~java
// Cũng chỉ là minh họa:
String key = tenantId + "|" + sourceSystem + "|" + externalNumber;
~~~

Nếu trường chứa ký tự **|**, không có quy tắc escaping/độ dài, chuỗi sẽ khó phân tách và có thể nhập nhằng. Dùng khóa kiểu dữ liệu như **record InvoiceKey(...)** để Java so sánh **từng thành phần**. Nếu bắt buộc phải truyền khóa qua URL, message hoặc persistence bằng chuỗi, hãy thiết kế **serialization không nhập nhằng**: độ dài từng phần, định dạng cấu trúc có schema, escaping có quy tắc và phiên bản định dạng. Đó là vấn đề mã hóa/đọc lại khóa, tách biệt với hợp đồng equality của object.

Tương tự, không dùng **Arrays.toString(...)** hoặc **Objects.hash(...)** làm “mã khóa duy nhất”: biểu diễn text không mặc nhiên là định dạng bất biến, còn hash có thể trùng giữa hai khóa khác nhau.

## 9. Lỗi nguy hiểm: thay đổi khóa sau khi đưa vào HashMap

Xét tình huống giả định với một lớp khóa có trường **externalNumber** có thể gán lại:

~~~java
// Mã minh họa hành vi không an toàn:
MutableInvoiceKey key = new MutableInvoiceKey("SHOP-A", "ERP", "INV-1");
map.put(key, invoice);
key.setExternalNumber("INV-2");
Invoice result = map.get(key); // Có thể không tìm thấy
~~~

Đây **không phải chương trình Java độc lập**: **MutableInvoiceKey**, **Invoice** và **map** chỉ là các kiểu/biến minh họa. Khi giá trị tham gia **hashCode()** đổi sau lệnh **put**, bucket cũ của HashMap không tự được xây lại; tìm kiếm có thể trả về **null** hoặc gặp trạng thái khó dự đoán. **HashSet** cũng chịu vấn đề tương tự.

Cách xử lý đúng:

- Dùng khóa bất biến như **record** hoặc **final class** với field final chứa giá trị bất biến.
- Nếu nghiệp vụ cần đổi mã, tạo **khóa mới**; gỡ ánh xạ cũ và thêm ánh xạ mới theo một thao tác có kiểm soát.
- Nếu cache lưu entity mutable, có thể giữ **business key immutable** tách khỏi entity; đừng dùng trực tiếp một entity mà equals/hashCode dựa vào field có thể đổi.
- Đối với nội dung collection hoặc array, cần defensive copy và so sánh theo ngữ nghĩa thực tế, không chỉ đặt tham chiếu vào field final.

Một **final field** trỏ đến **List** vẫn cho phép **List** bị sửa nếu không có cơ chế sao chép/bất biến tương ứng.

## 10. HashMap không thay thế ràng buộc UNIQUE của database

Ví dụ ở mục 4 chứng minh **HashMap** gộp các đối tượng khóa tương đương trong **một tiến trình**. Nó không bảo đảm không có hai request, hai JVM hoặc hai worker cùng ghi hai hóa đơn trùng business key vào cơ sở dữ liệu.

Nếu ba cột đã chuẩn hóa là khóa nghiệp vụ, một thiết kế SQL minh họa là:

~~~sql
CREATE TABLE invoices (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    external_number VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_invoice_business
        UNIQUE (tenant_id, source_system, external_number)
);
~~~

Đây là **schema SQL minh họa**, cú pháp chi tiết và cách sinh **id** tùy hệ quản trị. Ứng dụng phải lưu dạng chuẩn hóa thống nhất hoặc thiết kế chỉ mục/collation tương ứng; nếu database so sánh khác Java, hai lớp có thể cho kết quả uniqueness khác nhau.

Không thể chống duplicate đáng tin chỉ bằng:

~~~text
1. SELECT để kiểm tra chưa có
2. Nếu chưa có thì INSERT
~~~

Hai request đồng thời có thể cùng vượt qua bước kiểm tra rồi cùng INSERT. Ràng buộc UNIQUE trong database mới thực thi chống trùng giữa nhiều request đồng thời; ứng dụng cần xử lý vi phạm uniqueness/transaction và quyết định retry, trả lỗi hay idempotent response theo yêu cầu nghiệp vụ. Với luồng nhập từ bên ngoài, có thể dùng business key làm cơ sở cho cơ chế **idempotency**, nhưng vẫn phải xác định xung đột payload: trùng khóa không tự khẳng định toàn bộ nội dung request hợp lệ hoặc giống nhau.

### Trường hợp JPA/Hibernate

Đừng đồng nhất object **InvoiceKey** làm khóa tra cứu với chính sách **equals/hashCode của JPA entity**. Entity có thể có **id** kỹ thuật, proxy, trạng thái managed/detached và vòng đời khác. Nếu chọn khóa chính ghép bằng **@EmbeddedId** hoặc **@IdClass**, hãy tuân các yêu cầu riêng của JPA provider (kiểu khóa, constructor, serializable, equality, ánh xạ cột). Không tự giả định record bất kỳ dùng được trực tiếp làm JPA embeddable trên mọi phiên bản/provider. Ngay cả khi database dùng khóa chính kỹ thuật, vẫn có thể có **UNIQUE (tenant_id, source_system, external_number)** để bảo toàn quy tắc nghiệp vụ.

## 11. Dùng khóa ghép trong HashSet, cache và cấu trúc có thứ tự

Với **HashSet**, thêm một hóa đơn đã có cùng khóa sẽ không tăng số lượng phần tử. Với **Map/HashMap**, **put** khóa bằng nhau thay thế value đang gắn với khóa đó. Điều này khác với database: **put** ghi đè là hành vi bình thường của Map, còn ghi trùng UNIQUE thường là một lỗi hoặc nhánh xử lý có chủ đích. Không để việc ghi đè trong cache che khuất sự kiện nghiệp vụ cần báo lỗi.

Nếu dùng **TreeMap** hoặc **TreeSet**, ngoài equals/hashCode còn phải định nghĩa **Comparator** hoặc **Comparable**. Comparator phải xét đúng cùng các thành phần quyết định identity; nếu **compare(a,b) == 0** với hai khóa mà **!a.equals(b)**, cấu trúc có thứ tự có thể coi chúng là một key và làm mất phần tử theo cách bất ngờ.

Ví dụ **đoạn mã minh họa** dùng record đã định nghĩa ở mục 4:

~~~java
import java.util.Comparator;

Comparator<InvoiceKeyDemo.InvoiceKey> byKey =
        Comparator.comparing(InvoiceKeyDemo.InvoiceKey::tenantId)
                .thenComparing(InvoiceKeyDemo.InvoiceKey::sourceSystem)
                .thenComparing(InvoiceKeyDemo.InvoiceKey::externalNumber);
~~~

Đoạn này cần đặt trong phạm vi cho phép khai báo biến (như method) và ở cùng package/đường dẫn class thích hợp. Vì tất cả thành phần đã là **String** chuẩn hóa, so sánh theo cùng ba trường cho kết quả bằng không khi record bằng nhau. Nếu so sánh không phân biệt hoa/thường lại áp dụng trên các giá trị gốc chưa chuẩn hóa, phải kiểm tra kỹ sự tương thích với **equals()**.

Với cache nhiều tenant, **tenantId** luôn là phần của lookup khi domain cần cô lập tenant; lỗi bỏ tenant khỏi khóa cache có thể dẫn đến trả nhầm dữ liệu giữa khách hàng.

## 12. Chiến lược kiểm thử khóa ghép

Một bộ kiểm thử tối thiểu nên kiểm tra cả **phép so sánh** lẫn **hành vi trong collection**, thay vì chỉ so hash:

| Ca kiểm thử | Kết quả mong đợi |
| --- | --- |
| Hai bộ ba giống hệt | equals true, hash bằng nhau |
| Khác nhau chỉ bởi hoa/thường/space theo chính sách | equals true sau chuẩn hóa |
| Khác đúng tenantId | equals false |
| Khác đúng sourceSystem | equals false |
| Khác đúng externalNumber | equals false |
| null/trống ở trường bắt buộc | Constructor từ chối với exception rõ |
| Thêm hai khóa tương đương vào HashSet | Một phần tử |
| put hai khóa tương đương vào HashMap | Value mới thay value cũ, không tăng số key |
| Tạo lại khóa từ dữ liệu đã persisted | Tra cứu ra bản ghi tương ứng |
| So sánh với null, đối tượng kiểu khác | equals false |
| Đồng thời insert duplicate ở nhiều request | Database UNIQUE chỉ cho tồn tại một khóa nghiệp vụ |

Nên có test **phản xạ, đối xứng, bắc cầu**, tính ổn định qua nhiều lần gọi và đảm bảo nếu equals true thì hash giống nhau. Có thể bổ sung **property-based tests** khi quy tắc chuẩn hóa có nhiều biến thể Unicode, khoảng trắng, tenant và nguồn tích hợp.

Ví dụ test riêng bằng Java 17, không cần thư viện ngoài, lưu tệp **InvoiceKeyChecks.java** trong cùng thư mục với **InvoiceKeyDemo.java**:

~~~java
import java.util.HashSet;
import java.util.Set;

public class InvoiceKeyChecks {
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        var a = new InvoiceKeyDemo.InvoiceKey(
                "shop-a", "erp", "inv-0042");
        var b = new InvoiceKeyDemo.InvoiceKey(
                " SHOP-A ", "ERP", " INV-0042 ");
        var c = new InvoiceKeyDemo.InvoiceKey(
                "shop-a", "erp", "inv-0042");
        var other = new InvoiceKeyDemo.InvoiceKey(
                "shop-b", "erp", "inv-0042");

        check(a.equals(a), "reflexive");
        check(a.equals(b) && b.equals(a), "symmetric");
        check(a.equals(b) && b.equals(c) && a.equals(c),
                "transitive");
        check(a.hashCode() == b.hashCode(), "same hash");
        check(!a.equals(other), "different tenant");
        check(!a.equals(null), "not equal to null");
        check(!a.equals("not an InvoiceKey"), "different type");

        Set<InvoiceKeyDemo.InvoiceKey> set = new HashSet<>();
        set.add(a);
        set.add(b);
        set.add(other);
        check(set.size() == 2, "set identity");

        boolean rejected = false;
        try {
            new InvoiceKeyDemo.InvoiceKey("shop-a", "", "inv-1");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "reject blank component");

        System.out.println("All composite-key checks passed.");
    }
}
~~~

Chạy:

~~~bash
javac InvoiceKeyDemo.java InvoiceKeyChecks.java
java InvoiceKeyChecks
~~~

Kết quả chuẩn:

~~~text
All composite-key checks passed.
~~~

Các kiểm thử này xác nhận hành vi cơ bản của object và collection. Bảo đảm uniqueness thực tế khi ghi dữ liệu đồng thời còn cần kiểm thử tích hợp với database thật, vì test HashMap không thể phát hiện race condition giữa nhiều transaction.

## 13. Thực hành tổng hợp: thiết kế khóa cho sản phẩm tích hợp

Một sàn thương mại điện tử nhận SKU từ các đối tác. Mỗi đối tác có thể gửi SKU giống nhau; mỗi tenant có các catalog độc lập. Một sản phẩm biến thể được xác định duy nhất bởi **(tenantId, partnerId, sku, variantCode)**.

**Yêu cầu thực hành:**

1. Quyết định rõ trường nào phân biệt hoa/thường, có bỏ khoảng trắng không, và xử lý thế nào khi **variantCode** thiếu. Viết giả định trước khi code.
2. Tạo một **record ProductKey** chứa bốn trường và validation theo quyết định đó.
3. Cho hai đối tượng khóa chỉ khác định dạng đầu vào vào **HashSet**; xác nhận gộp hay không gộp đúng quy tắc.
4. Tạo hai khóa có SKU giống nhau nhưng khác tenant hoặc partner; xác nhận chúng luôn được nhận diện riêng.
5. Tạo **Map<ProductKey, ProductData>** để tra cứu và giải thích chuyện gì xảy ra khi **put** hai khóa bằng nhau.
6. Đề xuất **UNIQUE (tenant_id, partner_id, sku, variant_code)** hoặc một ràng buộc thay thế phản ánh đúng cách lưu trường tùy chọn. Nếu variantCode cho phép null, phải kiểm tra ngữ nghĩa UNIQUE/NULL của hệ quản trị cụ thể.
7. Đánh giá xem đổi mã SKU có làm đổi danh tính sản phẩm hay chỉ đổi khóa tra cứu; từ đó quyết định có cần **id** nội bộ ổn định không.

**Gợi ý lời giải:** ưu tiên các thành phần khóa đã chuẩn hóa, bất biến; sử dụng equals/hashCode do record sinh ra; không đưa giá bán, số lượng tồn hay tên hiển thị vào key nếu chúng chỉ là thuộc tính thay đổi. Bảo vệ uniqueness tại database chứ không chỉ trong JVM. Đối với trường tùy chọn, quy định rõ “không có variant” là một giá trị duy nhất, hay là trạng thái chưa xác định cần xử lý riêng.

## 14. Checklist khi review thiết kế

Trước khi đưa composite business key vào production, tự kiểm tra:

- **Domain:** tổ hợp trường có thực sự xác định đúng một thực thể trong đúng phạm vi tenant/nguồn/thời gian?
- **Tính ổn định:** những trường tham gia khóa có thể thay đổi sau khi key được dùng không?
- **Chuẩn hóa:** có chính sách rõ cho case, whitespace, Unicode, null, số và thời gian; policy có thống nhất với database không?
- **Equality:** equals so đúng từng thành phần; hashCode dùng cùng thành phần và thỏa hợp đồng?
- **Bất biến:** record/final class có chứa field mutable nào cần copy hoặc thiết kế lại không?
- **Collection:** HashMap/HashSet có thao tác ghi đè hoặc gộp khóa nào cần xử lý nghiệp vụ riêng không?
- **Đa tenant:** mọi lookup/query/cache có đầy đủ trường định phạm vi để không đọc nhầm dữ liệu?
- **Lưu trữ và đồng thời:** database có UNIQUE phù hợp, transaction có xử lý xung đột khi hai request cùng tạo không?
- **Tương thích hệ thống:** định dạng serialization và versioning có bảo toàn các thành phần, không phụ thuộc delimiter mơ hồ?
- **Kiểm thử:** có ca cho từng trường khác biệt, chuẩn hóa, null, va chạm tiềm năng và luồng ghi cạnh tranh không?

## 15. Ghi nhớ

**Khóa nghiệp vụ nhiều trường là một mô hình danh tính, không chỉ là phép nối chuỗi.** Trước tiên xác nhận các trường nào *thực sự* xác định một đối tượng trong phạm vi nghiệp vụ. Sau đó đóng gói chúng thành **record** hoặc **final class** bất biến, áp dụng validation và chuẩn hóa thống nhất, để **equals()/hashCode()** phản ánh đúng toàn bộ thành phần khóa. Sử dụng khóa này an toàn trong Map/Set, đồng thời để database thực thi ràng buộc uniqueness khi nhiều tiến trình hoặc request ghi dữ liệu đồng thời.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-003">Sửa lớp có equals(MyType) nhưng chưa ghi đè equals(Object)</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: sửa lớp có `equals(MyType)` nhưng chưa ghi đè `equals(Object)`

## 1. Bài toán cần sửa

Bạn nhận một lớp Java biểu diễn khóa định danh của một bản sách. Hai đối tượng có cùng mã sách và cùng lần hiệu đính phải được xem là bằng nhau về mặt giá trị. Lớp hiện có `equals(BookKey)` và đôi khi thử trực tiếp `a.equals(b)` cho kết quả `true`. Tuy nhiên, khi truyền các đối tượng đó cho API nhận `Object` hoặc đưa vào `List`, `Set`, phép so sánh lại thất bại.

Mục tiêu là **sửa hợp đồng bình đẳng đối tượng** để các lệnh gọi thông qua kiểu `Object` và các API collection đều nhận được phép so sánh giá trị đúng. Đây là lỗi điển hình ở ranh giới giữa *overloading* (nạp chồng) và *overriding* (ghi đè).

Sau khi thực hành, bạn sẽ có thể:
- Nhìn chữ ký phương thức để nhận ra `equals(MyType)` **không** ghi đè `Object.equals(Object)`.
- Chỉ ra tại sao phép gọi trực tiếp trông đúng nhưng `List.contains` và `HashSet.contains` có thể sai.
- Ghi đè chuẩn `public boolean equals(Object obj)` và `public int hashCode()`, dùng `@Override` để trình biên dịch kiểm tra.
- Kiểm tra các thuộc tính cơ bản của `equals` cùng khả năng dùng khóa giá trị trong hash collection.
- Nhận diện các lỗi còn lại liên quan đến `null`, tính đối xứng, phân lớp và trường có thể thay đổi.

## 2. Phân tích mã lỗi: cùng tên chưa đủ để ghi đè

Đây là lớp lỗi, xét riêng các thành phần quyết định phép so sánh:

```java
import java.util.Objects;

final class BrokenBookKey {
    private final String code;
    private final int revision;

    BrokenBookKey(String code, int revision) {
        this.code = Objects.requireNonNull(code);
        this.revision = revision;
    }

    // Nạp chồng! Tham số là BrokenBookKey, không phải Object.
    public boolean equals(BrokenBookKey other) {
        return other != null
                && revision == other.revision
                && code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, revision);
    }
}
```

Hãy đọc đúng hai chữ ký:

```java
public boolean equals(BrokenBookKey other) // Phương thức mới: overload
public boolean equals(Object obj)          // Chữ ký cần override từ Object
```

Phương thức thứ hai ở đây là **chữ ký cần có**, không phải phương thức đang tồn tại trong lớp lỗi. Vì lớp mới chỉ khai báo `equals(BrokenBookKey)`, nó vẫn kế thừa `Object.equals(Object)`, nghĩa là khi lời gọi được ràng buộc với chữ ký `equals(Object)` thì bình đẳng mặc định là **cùng đối tượng (identity)**.

Việc ghi đè `hashCode()` trong lớp lỗi cố ý làm ví dụ rõ hơn: **chỉ khắc phục hash code không thể thay thế cho việc ghi đè `equals(Object)`**. Hai đối tượng khác tham chiếu có cùng giá trị và cùng mã băm vẫn có thể bị so sánh là không bằng nhau.

### Vì sao hai lời gọi chọn phương thức khác nhau?

```java
BrokenBookKey a = new BrokenBookKey("JAVA-21", 2);
BrokenBookKey b = new BrokenBookKey("JAVA-21", 2);
Object viewedAsObject = b;

boolean direct = a.equals(b);               // Chọn equals(BrokenBookKey)
boolean viaObject = a.equals(viewedAsObject); // Chọn equals(Object)
```

- Trong `a.equals(b)`, kiểu tĩnh (*compile-time type*) của `b` là `BrokenBookKey`; trình biên dịch chọn overload nhận `BrokenBookKey` vì cụ thể hơn.
- Trong `a.equals(viewedAsObject)`, kiểu tĩnh của tham số là `Object`; trình biên dịch chọn chữ ký `equals(Object)` và lúc chạy không có override ở `BrokenBookKey`, nên thực thi `Object.equals(Object)`.
- Sự lựa chọn giữa **các overload** diễn ra trong lúc biên dịch; việc chọn phần cài đặt **override** phù hợp với đối tượng nhận diễn ra lúc chạy. Hai cơ chế không thay thế nhau.
- Phép thử chỉ dùng `a.equals(b)` với hai biến đều có kiểu `BrokenBookKey` có thể che giấu lỗi.

Một cách dò lỗi nhanh: thử đặt `@Override` trước `equals(BrokenBookKey other)`. Trình biên dịch báo lỗi vì phương thức này không override phương thức nào trong lớp cha. **Không sửa lỗi đó bằng cách xóa `@Override`; phải đổi đúng chữ ký sang `equals(Object)`.**

## 3. Cách sửa tối thiểu và chính xác

Với lớp **final** có ý nghĩa bình đẳng theo hai trường `code` và `revision`, cài đặt chuẩn như sau:

```java
import java.util.Objects;

public final class BookKey {
    private final String code;
    private final int revision;

    public BookKey(String code, int revision) {
        this.code = Objects.requireNonNull(code, "code");
        this.revision = revision;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BookKey other)) {
            return false;
        }
        return revision == other.revision
                && code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, revision);
    }
}
```

Lưu tệp trên thành `BookKey.java` và biên dịch bằng `javac BookKey.java` với JDK 17 trở lên. Đây là một lớp thư viện hoàn chỉnh, **không có `main`** nên bản thân nó không in stdout.

Ý nghĩa từng phần:

1. `@Override` xác nhận với trình biên dịch rằng chữ ký đúng là `equals(Object)`. Nếu viết sai kiểu tham số hoặc phạm vi truy cập, lỗi được phát hiện sớm.
2. `this == obj` nhận biết nhanh cùng một tham chiếu; nhánh này luôn trả về `true`, giữ tính *phản xạ*.
3. `obj instanceof BookKey other` loại `null` và đối tượng khác kiểu; `other` là biến nhận kiểu (pattern variable) của Java 16+, phù hợp Java 17.
4. Chỉ so sánh **các trường cấu thành danh tính giá trị**: cùng `revision` và cùng `code`. Vì constructor loại `null`, `code.equals(other.code)` an toàn.
5. `hashCode()` sử dụng **chính các trường được kiểm tra trong `equals`**. Nếu `a.equals(b)` là `true`, bắt buộc `a.hashCode() == b.hashCode()`.
6. Khai báo lớp `final` giúp không có subclass thêm trường hoặc thay đổi quy tắc bình đẳng, giảm nguy cơ vi phạm tính đối xứng.

Nếu chính bài tập yêu cầu giữ lại `equals(BookKey)` vì tương thích mã nguồn cũ, có thể cho overload đó **ủy quyền** về đường kiểm tra chuẩn:

```java
public boolean equals(BookKey other) {
    return equals((Object) other);
}
```

Đoạn trên là phương thức bổ sung tùy chọn **bên trong lớp đã ghi đè `equals(Object)`**. Ép `(Object)` là có chủ đích để chọn đúng overload; không bắt buộc giữ `equals(BookKey)` trong thiết kế mới và tránh lặp logic bình đẳng ở hai nơi.

## 4. Chương trình chứng minh lỗi và kết quả sau sửa

Ví dụ dưới đây là **một chương trình độc lập** với hai lớp lồng nhau: một lớp vẫn bị overload và một lớp đã sửa. Nó kiểm tra phép gọi thông qua `Object`, `List.contains`, `HashSet.contains` và mã băm.

Tạo tệp `EqualsOverloadRepairDemo.java`:

```java
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EqualsOverloadRepairDemo {
    static final class BrokenBookKey {
        private final String code;
        private final int revision;

        BrokenBookKey(String code, int revision) {
            this.code = Objects.requireNonNull(code);
            this.revision = revision;
        }

        // Sai mục tiêu: overload, không phải override Object.equals(Object).
        public boolean equals(BrokenBookKey other) {
            return other != null
                    && revision == other.revision
                    && code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, revision);
        }
    }

    static final class FixedBookKey {
        private final String code;
        private final int revision;

        FixedBookKey(String code, int revision) {
            this.code = Objects.requireNonNull(code);
            this.revision = revision;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FixedBookKey other)) return false;
            return revision == other.revision
                    && code.equals(other.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, revision);
        }
    }

    public static void main(String[] args) {
        BrokenBookKey brokenA = new BrokenBookKey("JAVA-21", 2);
        BrokenBookKey brokenB = new BrokenBookKey("JAVA-21", 2);
        Object brokenAsObject = brokenB;
        Set<BrokenBookKey> brokenSet = new HashSet<>();
        brokenSet.add(brokenA);

        System.out.println("BROKEN");
        System.out.println("typedEquals=" + brokenA.equals(brokenB));
        System.out.println("objectEquals=" + brokenA.equals(brokenAsObject));
        System.out.println("listContains=" + List.of(brokenA).contains(brokenB));
        System.out.println("setContains=" + brokenSet.contains(brokenB));
        System.out.println("sameHash=" + (brokenA.hashCode() == brokenB.hashCode()));

        FixedBookKey fixedA = new FixedBookKey("JAVA-21", 2);
        FixedBookKey fixedB = new FixedBookKey("JAVA-21", 2);
        FixedBookKey different = new FixedBookKey("JAVA-21", 3);
        Object fixedAsObject = fixedB;
        Set<FixedBookKey> fixedSet = new HashSet<>();
        fixedSet.add(fixedA);

        System.out.println("FIXED");
        System.out.println("typedEquals=" + fixedA.equals(fixedB));
        System.out.println("objectEquals=" + fixedA.equals(fixedAsObject));
        System.out.println("listContains=" + List.of(fixedA).contains(fixedB));
        System.out.println("setContains=" + fixedSet.contains(fixedB));
        System.out.println("sameHash=" + (fixedA.hashCode() == fixedB.hashCode()));
        System.out.println("differentRevision=" + fixedA.equals(different));
        System.out.println("equalsNull=" + fixedA.equals(null));
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac EqualsOverloadRepairDemo.java
java EqualsOverloadRepairDemo
```

Stdout chính xác (chương trình trên chỉ dùng các giá trị xác định):

```text
BROKEN
typedEquals=true
objectEquals=false
listContains=false
setContains=false
sameHash=true
FIXED
typedEquals=true
objectEquals=true
listContains=true
setContains=true
sameHash=true
differentRevision=false
equalsNull=false
```

**Giải thích kết quả trước sửa:** `brokenA.equals(brokenB)` dùng overload nên trả `true`. Ngược lại, `brokenA.equals(brokenAsObject)` gọi chữ ký `equals(Object)` kế thừa nên `false`. Các Collection API kiểm tra bình đẳng qua giao diện `Object.equals(Object)`, vì vậy đối tượng thứ hai dù có cùng giá trị vẫn không được tìm thấy. `sameHash=true` chỉ cho thấy hai mã băm bằng nhau, không tự khiến `equals(Object)` đúng.

**Giải thích kết quả sau sửa:** `FixedBookKey.equals(Object)` được dispatch kể cả khi đối số hoặc biến được nhìn bằng kiểu `Object`. Cả `List.contains` lẫn `HashSet.contains` có thể tìm đối tượng khác tham chiếu nhưng bằng nhau về giá trị. Mã băm thống nhất với phép so sánh; `revision` khác nhau không bằng nhau; `null` không bằng đối tượng hợp lệ.

Trong `HashSet`, mã băm trước tiên giúp xác định nơi tìm kiếm, còn `equals(Object)` xác nhận hai khóa có thực sự bằng nhau hay không. Có cùng mã băm **không đồng nghĩa** hai phần tử bằng nhau; *hash collision* hợp lệ. Có `equals` đúng mà `hashCode` sai cũng có thể gây lỗi lookup vì hai đối tượng bằng nhau bị đặt vào các bucket khác nhau.

## 5. Hợp đồng `equals(Object)` và `hashCode()` cần giữ

Với các tham chiếu khác `null`, hợp đồng chính của `equals` gồm:

| Thuộc tính | Điều cần đảm bảo | Kiểm tra gợi ý |
| --- | --- | --- |
| Phản xạ (*reflexive*) | `x.equals(x)` là `true` | So sánh một instance với chính nó |
| Đối xứng (*symmetric*) | `x.equals(y)` và `y.equals(x)` cho cùng kết quả | Kiểm tra hai chiều, cả với kiểu con nếu cho phép kế thừa |
| Bắc cầu (*transitive*) | Nếu `x=y` và `y=z` thì `x=z` về nghĩa `equals` | Tạo ba instance có cùng trường giá trị |
| Nhất quán (*consistent*) | Kết quả giữ nguyên khi trạng thái dùng cho bình đẳng không đổi | Gọi nhiều lần trước khi sửa dữ liệu |
| So với null | `x.equals(null)` là `false` | Kiểm tra cụ thể qua chữ ký `Object` |

Với `hashCode`, quy tắc bắt buộc là **hai đối tượng bằng nhau theo `equals` phải có hash code bằng nhau** trong cùng một lần quan sát trạng thái. Chiều ngược lại **không bắt buộc**. Nếu hai đối tượng không bằng nhau, mã băm của chúng vẫn có thể trùng nhau.

Trong lớp khóa giá trị, nên ưu tiên các trường `final` và kiểu giá trị bất biến. Khi dùng một đối tượng làm phần tử `HashSet` hoặc khóa `HashMap`, **không thay đổi các trường tham gia `equals/hashCode` trong khi đối tượng đang được lưu**: nếu không, hash bucket cũ và mã băm mới có thể lệch nhau, khiến tìm kiếm thất bại ngay cả với chính tham chiếu đó.

## 6. Checklist sửa lớp thật tại dự án

Áp dụng trực tiếp lên lớp hiện đang có `equals(MyType)`:

1. **Xác định ý nghĩa bình đẳng.** Đặt câu hỏi “Hai instance nào được xem là cùng giá trị?” và ghi rõ những trường tham gia. Không mặc định so sánh mọi trường nếu có trường chỉ là cache, timestamp hoặc metadata không liên quan danh tính.
2. **Thay chữ ký sai.** Viết `@Override public boolean equals(Object obj)`; bảo đảm tên `equals`, chữ `Object` của `java.lang.Object`, kiểu trả về `boolean` và phạm vi `public`.
3. **Xử lý cùng tham chiếu, null và khác kiểu.** Trả `true` nhanh cho `this == obj`, trả `false` khi không đúng kiểu giá trị.
4. **So sánh nội dung theo loại trường.** Kiểu nguyên thủy dùng toán tử `==`; đối tượng dùng `Objects.equals(a, b)` khi có thể null hoặc `a.equals(b)` khi được bảo đảm khác null; mảng cần `Arrays.equals` hoặc `Arrays.deepEquals` theo cấu trúc dữ liệu, không dùng so sánh tham chiếu nếu cần giá trị.
5. **Ghi đè `hashCode()` đồng bộ.** Cùng tập trường và cùng quy tắc so sánh; với mảng cần xét `Arrays.hashCode`/`Arrays.deepHashCode` cho tương ứng.
6. **Rà soát caller.** Tìm nơi gọi `equals(MyType)`. Khi không cần API overload, bỏ phương thức đó; nếu bắt buộc giữ để tương thích, cho ủy quyền đến `equals(Object)` và tránh cài đặt hai thuật toán khác nhau.
7. **Thử dưới kiểu `Object` và qua collection.** Kiểm tra `Object ref = b; a.equals(ref)`, `List.contains(b)`, `HashSet.contains(b)`, `HashMap.get(b)` (khi đã thêm khóa bằng giá trị). Đừng chỉ thử `a.equals(b)` với hai biến kiểu cụ thể.
8. **Kiểm tra hợp đồng hai chiều và biến thể dữ liệu.** Bao gồm chính nó, instance bằng giá trị, instance khác một trường, `null`, đối tượng khác kiểu và trường hợp được phép kế thừa.

Ví dụ test thủ công không cần thư viện ngoài, dùng cùng lớp `BookKey` ở mục 3:

```java
BookKey a = new BookKey("JAVA-21", 2);
BookKey b = new BookKey("JAVA-21", 2);
BookKey c = new BookKey("JAVA-21", 2);
BookKey other = new BookKey("JAVA-21", 3);

if (!a.equals(a)) throw new AssertionError("reflexive");
if (!a.equals(b) || !b.equals(a)) throw new AssertionError("symmetric");
if (!(a.equals(b) && b.equals(c) && a.equals(c))) {
    throw new AssertionError("transitive");
}
if (a.equals(other) || a.equals(null) || a.equals("JAVA-21")) {
    throw new AssertionError("non-equal case");
}
if (a.hashCode() != b.hashCode()) throw new AssertionError("hash consistency");
if (!java.util.Set.of(a).contains(b)) {
    throw new AssertionError("collection lookup");
}
```

Đây là **đoạn kiểm chứng cần đặt trong phương thức** (ví dụ `main` hoặc test), không phải chương trình độc lập; các kiểm tra dùng `AssertionError` trực tiếp nên chạy ngay cả khi JVM không được bật tùy chọn `-ea`.

## 7. Các lựa chọn và bẫy đáng chú ý

### `instanceof`, `getClass()` và kế thừa

Ví dụ `BookKey` là lớp `final`; kiểm tra `instanceof BookKey` tương ứng với phạm vi các instance của lớp đó. Nếu lớp **không final** và có subclass thêm trường, phép so sánh giữa lớp cha và lớp con dễ phá tính đối xứng hoặc bắc cầu. Cần chọn và kiểm thử một chính sách kế thừa rõ ràng:

- `getClass() == obj.getClass()` chỉ chấp nhận **cùng runtime class**, thường phù hợp bình đẳng có phân biệt chính xác kiểu, nhưng loại cả subclass hoặc proxy.
- `instanceof` chấp nhận kiểu con; phải bảo đảm các lớp con không thay đổi cách so sánh theo hướng làm vi phạm hợp đồng.
- Đánh dấu lớp giá trị `final` hoặc chọn một thiết kế bình đẳng nhất quán thường dễ kiểm soát hơn. Không tự thay `getClass` bằng `instanceof` trong code đang dùng polymorphism mà chưa đánh giá hệ quả.

### Sự nhầm lẫn khác với overload

- `equals(MyType)` và `equals(Object)` có thể cùng tồn tại, nhưng overload thường gây bất ngờ vì kết quả khác nhau tùy kiểu tĩnh của biến. Nếu giữ cả hai, bảo đảm chúng cùng quy tắc.
- `equals(Object)` mà quên `@Override` vẫn có thể hoạt động nếu chữ ký chính xác; `@Override` giúp chặn lỗi chữ ký và nên được dùng.
- Ghi đè `equals(Object)` nhưng quên `hashCode()` có thể khiến `HashMap`/`HashSet` hoạt động sai với đối tượng bằng giá trị.
- So sánh `String` bằng `==` kiểm tra cùng tham chiếu, không kiểm tra nội dung. Với khóa bản sách, dùng `String.equals` hoặc `Objects.equals`.
- `Objects.equals(a,b)` chỉ gọi `equals(Object)` đúng theo hợp đồng, nên không tự chữa được lớp có mỗi `equals(MyType)`.
- Không sử dụng `System.identityHashCode` để xây `hashCode` cho lớp có equality theo giá trị.

### Khi nào dùng `record`?

Nếu mục tiêu là một lớp mang dữ liệu bất biến, không có quy tắc đặc thù và các thành phần phù hợp làm danh tính, Java 17 có `record`:

```java
public record BookKeyRecord(String code, int revision) {
    public BookKeyRecord {
        java.util.Objects.requireNonNull(code, "code");
    }
}
```

Record tự sinh `equals(Object)` và `hashCode()` dựa trên thành phần đã khai báo, giúp tránh lỗi bỏ quên override. Đây là lựa chọn thiết kế mới, **không phải sửa chữa bắt buộc cho một lớp hiện hữu** (đặc biệt nếu cần kế thừa từ class khác hoặc có yêu cầu API/serialization riêng).

## 8. Câu hỏi tự kiểm tra và bài tập nhỏ

1. **Tại sao `x.equals(y)` là `true` nhưng `((Object)x).equals(y)` là `false` trước sửa?** Vì lời gọi đầu dùng overload `equals(MyType)`, còn lời gọi sau chỉ nhìn thấy `equals(Object)` và lớp chưa override nó.
2. **Chỉ đổi `equals(MyType)` thành `equals(Object)` đã đủ chưa?** Chưa; phải bảo đảm logic so sánh giá trị đúng và `hashCode()` tuân theo cùng trường, đồng thời kiểm tra các thuộc tính của hợp đồng.
3. **Hai đối tượng có hash code bằng nhau thì `equals` phải trả `true`?** Không. Trùng hash là điều được phép với các đối tượng không bằng nhau.
4. **Vì sao thêm `@Override` là một thao tác debug tốt?** Compiler xác minh phương thức thực sự ghi đè, nhờ đó phát hiện chữ ký `equals(MyType)` sai.
5. **Nếu thay `revision` từ 2 thành 3, đối tượng trong `HashSet` có thể gặp vấn đề gì?** Hash code và phép bằng nhau thay đổi khi object đã nằm trong một bucket cũ; nên bất biến các trường liên quan equality.

**Bài thực hành mở rộng:** Tự bổ sung một trường `region` vào `BookKey` và quyết định liệu khu vực có cấu thành danh tính bản sách hay không. Cập nhật `equals(Object)` và `hashCode()` cùng một lúc, viết ít nhất hai đối tượng khác `region` để chứng minh quy tắc mà bạn chọn. Kiểm tra qua biến `Object` và `HashSet.contains`, rồi thay đổi cách nhìn về `region` và quan sát sự khác biệt.

**Điểm mấu chốt:** Khi cần so sánh giá trị của đối tượng Java trong mã chung và collection, **chữ ký bắt buộc là `public boolean equals(Object)`**. `equals(MyType)` chỉ là overload; một phép gọi trực tiếp thành công chưa chứng minh hợp đồng của `Object` đã được ghi đè.

## Tài liệu tham khảo

- [Java SE 21 — `Object.equals(Object)` và `Object.hashCode()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html).
- [Java SE 21 — `Set` và các điều kiện về phần tử](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html).
- [Java SE 21 — `HashSet`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html).
- [Java Language Specification — Overriding và hiding](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4.8).
- [Java Language Specification — Method invocation: tìm và chọn phương thức](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-004">Thực nghiệm va chạm hash với các đối tượng không bằng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Thực nghiệm va chạm hash với các đối tượng không bằng nhau

**Va chạm hash (hash collision)** xảy ra khi hai đối tượng có cùng giá trị `hashCode()` nhưng **`equals()` trả về `false`**. Đây là tình huống hợp lệ theo hợp đồng của Java. Một cấu trúc như `HashMap` hoặc `HashSet` phải xử lý được va chạm để vẫn phân biệt các khóa khác nhau.

Bài thực hành này trả lời bằng mã chạy được ba câu hỏi: làm sao tạo và quan sát va chạm; vì sao hai khóa trùng hash vẫn cùng tồn tại trong collection; và đâu là ranh giới giữa tính **đúng** của `equals/hashCode` với **chất lượng phân phối** của hàm hash.

## 1. Hợp đồng cần nắm trước khi thử nghiệm

Với hai tham chiếu đối tượng không `null` là `a` và `b`:

- Nếu `a.equals(b) == true`, **bắt buộc** `a.hashCode() == b.hashCode()`.
- Nếu `a.hashCode() == b.hashCode()`, **không thể kết luận** `a.equals(b) == true`.
- Nếu `a.hashCode() != b.hashCode()` và các phương thức tuân thủ hợp đồng, có thể kết luận `a` và `b` không bằng nhau.
- Trong quá trình dữ liệu dùng để so sánh không thay đổi, `equals()` và `hashCode()` phải cho kết quả nhất quán. Tránh thay đổi trường tham gia hash khi đối tượng đang làm khóa của `HashMap` hoặc phần tử của `HashSet`.

Có thể hình dung:

```text
                a.hashCode() == b.hashCode() ?
                           |
              +------------+------------+
              |                         |
             Không                      Có
              |                         |
      Hai khóa không bằng nhau   Chưa biết bằng nhau
                                        |
                                  Kiểm tra equals()
                                    /        \
                                  true       false
                                   |           |
                              Cùng khóa    Va chạm hash
```

Mũi tên **`equals == true → hashCode bằng nhau`** là một chiều. Hàm băm biến không gian giá trị đối tượng rất lớn thành một số nguyên `int` 32 bit, vì thế nhiều giá trị khác nhau có thể chia sẻ một số hash. Thậm chí một hàm trả về cùng hằng số cho mọi đối tượng vẫn có thể *đúng hợp đồng* nhưng phân phối hash cực kém.

## 2. Va chạm tự nhiên có thể tái hiện với `String`

Trong Java, hai chuỗi khác nhau `"Aa"` và `"BB"` có hash code giống nhau:

```text
"Aa": 31 × 65 + 97 = 2112
"BB": 31 × 66 + 66 = 2112
```

Ở đây 65/97/66 là giá trị ký tự `A`, `a`, `B` trong UTF-16; công thức đơn giản cho chuỗi dài hai ký tự là `31 * c0 + c1`. Với chuỗi dài hơn, `String.hashCode()` tích lũy theo đa thức nhân 31 trong số nguyên 32 bit, cho phép tràn theo quy tắc số học `int`.

Nhưng `"Aa".equals("BB")` là `false` vì nội dung chuỗi khác nhau. Đây là **một va chạm thật của `String.hashCode()`**, không cần sửa JDK, can thiệp bộ nhớ hoặc dùng thư viện bên ngoài.

## 3. Chương trình Java 17+ có thể chạy độc lập

Chương trình chứa hai thí nghiệm:

1. **Va chạm tự nhiên:** dùng trực tiếp `String` `"Aa"` và `"BB"` làm khóa.
2. **Va chạm có chủ đích:** định nghĩa `ProductKey` có `equals()` so sánh `sku` nhưng `hashCode()` luôn trả về `7` để buộc các khóa khác nhau đụng hash.

Lưu nội dung sau thành tệp **`HashCollisionDemo.java`**:

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class HashCollisionDemo {
    public static void main(String[] args) {
        // Thí nghiệm 1: va chạm thật với String.
        String a = "Aa";
        String b = "BB";

        System.out.println("String hash: "
                + a.hashCode() + " / " + b.hashCode());
        System.out.println("String equals: " + a.equals(b));
        System.out.println("Sai neu chi so hash: "
                + (a.hashCode() == b.hashCode()));

        Set<String> stringSet = new HashSet<>();
        stringSet.add(a);
        stringSet.add(b);
        System.out.println("String set size: " + stringSet.size());

        Map<String, Integer> stringMap = new HashMap<>();
        stringMap.put(a, 10);
        stringMap.put(b, 20);
        System.out.println("String map values: "
                + stringMap.get(a) + " / " + stringMap.get(b));

        // Thí nghiệm 2: cố ý làm mọi ProductKey trùng hash.
        ProductKey first = new ProductKey("SKU-A");
        ProductKey second = new ProductKey("SKU-B");
        ProductKey sameAsFirst = new ProductKey("SKU-A");

        System.out.println("Custom hash: "
                + first.hashCode() + " / " + second.hashCode());
        System.out.println("Distinct keys equal: "
                + first.equals(second));
        System.out.println("Equivalent keys equal: "
                + first.equals(sameAsFirst));

        Set<ProductKey> productSet = new HashSet<>();
        productSet.add(first);
        productSet.add(second);
        productSet.add(sameAsFirst);
        System.out.println("Custom set size: " + productSet.size());

        Map<ProductKey, String> productMap = new HashMap<>();
        productMap.put(first, "gia tri A");
        productMap.put(second, "gia tri B");
        System.out.println("Custom map size: " + productMap.size());
        System.out.println("Lookup equivalent: "
                + productMap.get(sameAsFirst));
        System.out.println("Lookup second: "
                + productMap.get(second));
    }

    // Ví dụ cố ý có hashCode kém để quan sát va chạm.
    // Không sao chép chiến lược hash hằng số vào mã production.
    static final class ProductKey {
        private final String sku;

        ProductKey(String sku) {
            this.sku = Objects.requireNonNull(sku);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ProductKey other)) {
                return false;
            }
            return sku.equals(other.sku);
        }

        @Override
        public int hashCode() {
            return 7; // Cố ý gây va chạm để thực nghiệm.
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac HashCollisionDemo.java
java HashCollisionDemo
```

**Stdout chính xác:**

```text
String hash: 2112 / 2112
String equals: false
Sai neu chi so hash: true
String set size: 2
String map values: 10 / 20
Custom hash: 7 / 7
Distinct keys equal: false
Equivalent keys equal: true
Custom set size: 2
Custom map size: 2
Lookup equivalent: gia tri A
Lookup second: gia tri B
```

Kết quả không phụ thuộc thứ tự duyệt của `HashMap` hay `HashSet`: chương trình chỉ in các giá trị tra cứu trực tiếp và `size()`. Nếu thay bằng vòng `for` để in toàn bộ collection, **không được giả định thứ tự phần tử ổn định**.

## 4. Đọc kết quả thí nghiệm

### Thí nghiệm 1: cùng hash nhưng cả hai chuỗi vẫn tồn tại

`"Aa"` và `"BB"` có cùng hash `2112`, song `String.equals()` xác nhận chúng khác nhau. `HashSet.add(a)` và `HashSet.add(b)` đều thêm được khóa riêng, vì vậy `stringSet.size() == 2`.

Với `HashMap`, hai thao tác `put("Aa", 10)` và `put("BB", 20)` giữ **hai ánh xạ riêng**. `get("Aa")` trả về `10`; `get("BB")` trả về `20`. Va chạm **không tự động ghi đè** giá trị của khóa khác.

Ngược lại, câu lệnh sai sau coi hai chuỗi là bằng nhau chỉ vì hash trùng:

```java
boolean incorrect = "Aa".hashCode() == "BB".hashCode();
// incorrect == true, dù "Aa".equals("BB") == false.
```

**Không dùng hash code như ID duy nhất hoặc thay thế cho `equals()`.** Hash hữu ích để thu hẹp ứng viên cần so sánh, không chứng minh hai đối tượng có nội dung bằng nhau.

### Thí nghiệm 2: phân biệt “hash trùng” và “khóa tương đương”

Cả `first`, `second` và `sameAsFirst` đều trả về hash `7`. Tuy nhiên:

| Cặp đối tượng | Kết quả `equals` | Ý nghĩa |
| --- | --- | --- |
| `first` / `second` | `false` | SKU khác nhau; đây là một va chạm. |
| `first` / `sameAsFirst` | `true` | SKU giống nhau; hai thể hiện của cùng một khóa logic. |
| `second` / `sameAsFirst` | `false` | SKU khác nhau; tiếp tục là một va chạm. |

`productSet.size() == 2` vì hai giá trị SKU là khác nhau, còn đối tượng thứ ba tương đương đối tượng thứ nhất và không tạo phần tử logic mới. Tương tự, `productMap.size() == 2`; truy vấn bằng một đối tượng mới có SKU `"SKU-A"` vẫn tìm ra giá trị gắn với `first`.

Điều này chứng minh **hash bằng nhau không đủ để xem là phần tử trùng lặp**. Collection dựa trên hash còn phải xét tính tương đương theo `equals()` (hoặc quan hệ so sánh khóa tương đương theo hợp đồng tương ứng).

## 5. Collection giải quyết va chạm như thế nào?

Nhìn ở mức khái niệm, tra cứu trong `HashMap` gồm các bước:

1. Tính hash từ khóa được cung cấp; `HashMap` có thể biến đổi hash này thêm để chọn vị trí lưu trong bảng nội bộ.
2. Xác định vị trí/nhóm ứng viên từ hash.
3. Trong nhóm ứng viên, đối chiếu hash và tính bằng nhau của khóa. Khóa có cùng hash nhưng `equals() == false` vẫn là khóa khác.
4. Nếu có khóa tương đương, `get` trả về giá trị tương ứng; nếu `put` với khóa tương đương, giá trị của ánh xạ hiện hữu được thay thế. Nếu không có, `put` tạo ánh xạ mới.

`HashSet` sử dụng cấu trúc dựa trên hash nên cũng cần phân biệt các phần tử cùng hash bằng quan hệ bằng nhau.

Sơ đồ minh họa (không mô tả layout bộ nhớ chính xác của từng bản JDK):

```text
hashCode("Aa") = 2112 ─┐
                       ├── Nhóm ứng viên có hash trùng
hashCode("BB") = 2112 ─┘          |
                                  +-- equals("Aa", "BB") -> false
                                  +-- Có thể lưu/tra cứu hai khóa riêng

hashCode(ProductKey("SKU-A")) = 7 ─┐
                                   ├── Nhóm ứng viên cùng hash
hashCode(ProductKey("SKU-A")) = 7 ─┘          |
                                              +-- equals -> true
                                              +-- Một khóa logic
```

Một số bản hiện thực `HashMap` có thể chuyển nhóm chứa nhiều phần tử va chạm sang cấu trúc cây dưới điều kiện nhất định. Đây là **chi tiết tối ưu của hiện thực**, không phải lý do để dựa vào bucket cụ thể, thứ tự duyệt, số lần gọi `equals` hoặc thời gian thực thi cố định trong mã ứng dụng.

## 6. Hàm hash tốt khác với hàm hash đúng hợp đồng

Hàm `ProductKey.hashCode()` trả về `7` cho mọi trường hợp nên **không vi phạm** quy tắc “đã bằng nhau thì hash phải bằng nhau”. Nhưng nó không phân tán được các giá trị SKU khác nhau, khiến nhiều thao tác phải xử lý chung một nhóm ứng viên.

Trong mã nghiệp vụ, phiên bản hợp lý hơn là dựa trên chính trường tham gia `equals`:

```java
@Override
public int hashCode() {
    return sku.hashCode();
}
```

Đoạn trên là **phương thức thay thế trong lớp `ProductKey` ở ví dụ**, không phải một chương trình `main` độc lập. Vì `sku` là `String` không `null` và bất biến, cách này tuân thủ hợp đồng và thông thường phân phối khóa tốt hơn hằng số `7`. Nó vẫn có thể va chạm, như `"Aa"` và `"BB"` đã chứng minh.

Các tiêu chí cần tách bạch:

| Tiêu chí | Yêu cầu |
| --- | --- |
| **Đúng hợp đồng** | Bất kỳ hai đối tượng `equals == true` đều có hash bằng nhau. |
| **Nhất quán** | Khi dữ liệu tham gia so sánh không đổi, hash không thay đổi giữa các lần gọi theo hợp đồng. |
| **Phân phối hợp lý** | Với dữ liệu thực tế, hạn chế việc quá nhiều khóa không bằng nhau trùng hash để cải thiện hiệu năng trung bình. |
| **Không đòi hỏi duy nhất** | Không thể bảo đảm mỗi giá trị đối tượng có hash riêng với `int` 32 bit. |

Chất lượng phân phối hash phụ thuộc dữ liệu sử dụng. Trong một ứng dụng có dữ liệu đối kháng, nhiều khóa có thể được chọn để gây va chạm hàng loạt; đây là vấn đề hiệu năng cần theo dõi, dù kết quả tra cứu vẫn đúng nếu hợp đồng được giữ.

## 7. Những lỗi dễ mắc khi thực nghiệm

### Lỗi 1: khẳng định hai đối tượng bằng nhau chỉ vì hash trùng

Sai:

```java
boolean same = a.hashCode() == b.hashCode();
```

Đúng khi cần kiểm tra bình đẳng đối tượng theo hợp đồng:

```java
boolean same = Objects.equals(a, b);
```

Với các tham chiếu có thể `null`, `Objects.equals` xử lý trường hợp `null` và gọi `equals` khi cần. Nó không biến một hiện thực `equals` sai thành đúng.

### Lỗi 2: cố sửa `equals()` để “tránh va chạm”

Va chạm không đồng nghĩa `equals()` có lỗi. `equals()` phải biểu đạt **quan hệ bình đẳng có ý nghĩa của lớp**; `hashCode()` phải nhất quán với quan hệ đó. Việc ghép hash vào điều kiện `equals()` có thể làm kết quả phụ thuộc vào một mã băm vốn không duy nhất.

### Lỗi 3: override một phương thức nhưng quên phương thức còn lại

Nếu override `equals()` để hai đối tượng khác địa chỉ bộ nhớ được xem là bằng nhau, cũng phải override `hashCode()` tương thích. Nếu không, `HashMap` hoặc `HashSet` có thể không tìm ra khóa tương đương, hoặc chứa hai phần tử mà `equals()` tuyên bố là bằng nhau.

### Lỗi 4: thay đổi khóa sau khi thêm vào hash collection

Nếu dùng một trường có thể đổi trong `equals/hashCode`, việc cập nhật trường đó có thể đổi hash và khiến tra cứu thất bại. `HashMap` không tự chuyển mục đã lưu sang vị trí mới chỉ vì đối tượng khóa bị sửa. Trong ví dụ, `sku` là `final` để tránh sai lầm này.

### Lỗi 5: dùng thời gian một lần chạy làm kết luận hiệu năng

Bài này chứng minh **tính đúng khi va chạm**, không phải benchmark. Kết quả thời gian của một lần chạy ngắn còn chịu ảnh hưởng JIT, khởi động JVM, GC, kích thước bảng và dữ liệu đầu vào. Nếu cần đánh giá tốc độ giữa hàm hash hằng số và hàm hash tốt, hãy dùng tập khóa đủ lớn, phân phối đầu vào xác định và công cụ benchmark phù hợp (ví dụ JMH) sau khi xác minh tính đúng.

## 8. Bài kiểm tra mở rộng

Có thể chỉnh chương trình để kiểm tra thêm các tình huống sau:

1. **Thêm lại một khóa tương đương:** gọi `productMap.put(sameAsFirst, "gia tri A moi")`. Kích thước map vẫn là `2`; tra cứu bằng `first` trả về giá trị mới vì `first.equals(sameAsFirst) == true`.
2. **Thêm khóa thứ ba không bằng hai khóa còn lại nhưng cũng có hash `7`:** dùng `new ProductKey("SKU-C")`. Kích thước map tăng lên `3` nếu `put` khóa đó; ba khóa vẫn tra cứu độc lập.
3. **Đổi `hashCode()` sang `sku.hashCode()`:** chạy lại cùng ví dụ. Tính bằng nhau, kích thước collection và kết quả tra cứu giữ nguyên. Cặp `"SKU-A"`/`"SKU-B"` không cần cùng hash nữa.
4. **Thử hai khóa chuỗi va chạm khác:** thay các SKU bằng `"Aa"` và `"BB"` trong phiên bản `sku.hashCode()`; cùng hash `2112` nhưng `equals()` vẫn phân biệt được hai đối tượng.

Không cần kiểm tra kích thước bucket hay truy cập cấu trúc nội bộ `HashMap` để xác nhận các kết luận trên. Các kiểm tra có giá trị nhất là **`equals`, `hashCode`, `size`, `get` và giá trị khi cập nhật một khóa tương đương**.

**Kết luận kỹ thuật:** `hashCode()` giúp tổ chức và thu hẹp nhóm ứng viên; **`equals()` quyết định bình đẳng logic**. Va chạm giữa các đối tượng không bằng nhau là bình thường; cấu trúc hash đúng phải giữ được từng khóa riêng biệt, còn thiết kế hash tốt nhằm giảm chi phí xử lý va chạm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-005">Tái hiện lỗi thay đổi khóa sau khi đưa vào HashMap</a>

<details>
<summary>Xem chi tiết</summary>

# Tái hiện lỗi thay đổi khóa sau khi đưa vào HashMap

## 1. Mục tiêu thực hành

Khi một đối tượng được dùng làm **khóa** của `HashMap`, các giá trị tham gia vào `equals()` và `hashCode()` phải giữ ổn định trong suốt thời gian đối tượng nằm trong map. Nếu thay đổi một trường dùng để tính khóa sau khi đã gọi `put()`, map có thể **không tìm được entry**, dù entry vẫn còn đó và ngay cả khi ta sử dụng đúng biến tham chiếu ban đầu.

Trong bài thực hành này, ta sẽ:

1. Tạo một lớp khóa có trường `id` có thể thay đổi.
2. Ghi đè `equals()` và `hashCode()` dựa trên `id`, tuân thủ hợp đồng của hai phương thức tại mỗi trạng thái.
3. Đưa khóa vào `HashMap`, thay đổi `id` rồi quan sát `get()`, `containsKey()`, `remove()` và `size()`.
4. Giải thích tại sao map mất khả năng tra cứu một entry còn tồn tại.
5. Thử thao tác `put()` lần nữa để thấy trạng thái bất thường hơn.
6. Sửa thiết kế bằng khóa bất biến và kiểm chứng kết quả.

**Kết luận cần chứng minh:** việc tham chiếu tới khóa vẫn còn và `map.size()` vẫn lớn hơn 0 **không có nghĩa** `map.get(key)` còn tìm được giá trị. `HashMap` dùng mã băm để xác định vị trí tra cứu; nó không duyệt toàn bộ các entry để tìm chính tham chiếu đó.

## 2. Ôn lại hợp đồng khóa của HashMap

`HashMap<K,V>` triển khai `Map<K,V>` bằng bảng băm. Hai phương thức trên khóa có vai trò chính:

- `hashCode()`: tạo số nguyên dùng để chọn vùng lưu/tra cứu, sau khi `HashMap` xử lý bit và tính chỉ số bucket.
- `equals(Object)`: quyết định hai khóa có được coi là cùng một khóa về mặt logic hay không, khi HashMap đã xét các điều kiện mã băm phù hợp.

Hợp đồng cơ bản giữa hai phương thức:

- Nếu `a.equals(b)` trả `true`, `a.hashCode()` và `b.hashCode()` phải bằng nhau.
- Nếu `a.hashCode() == b.hashCode()`, chưa thể suy ra `a.equals(b)`.
- Với một đối tượng mà thông tin dùng trong `equals()` không thay đổi, `hashCode()` phải ổn định trong quá trình thực thi tương ứng.
- Khi `equals()` trả về `false`, hai khóa có thể cùng hash; HashMap vẫn có khả năng lưu chúng thành hai entry khác nhau.

**Hợp đồng dùng khóa trong map còn cần một điều kiện thực tế:** đừng thay đổi trạng thái của khóa theo cách ảnh hưởng tới so sánh bằng nhau trong khi khóa nằm trong map. Tài liệu `Map` của Java nêu rằng hành vi không được xác định rõ nếu một đối tượng làm khóa bị thay đổi theo cách ảnh hưởng đến so sánh `equals` khi nó đang nằm trong map.

Điều này không có nghĩa lớp khóa mutable luôn vi phạm ngay hợp đồng `equals/hashCode`. Lớp vẫn có thể tính hai phương thức nhất quán tại từng thời điểm, nhưng **map đã lập chỉ mục theo trạng thái cũ** và không được thông báo để tái lập chỉ mục khi trường khóa thay đổi.

## 3. Ví dụ chạy được: khóa đổi hash sau khi put

Tạo tệp `MutableHashMapKeyDemo.java` (Java 17+):

~~~java
import java.util.HashMap;
import java.util.Map;

public class MutableHashMapKeyDemo {
    static final class MutableKey {
        private int id;

        MutableKey(int id) {
            this.id = id;
        }

        void setId(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            return obj instanceof MutableKey other && id == other.id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    public static void main(String[] args) {
        Map<MutableKey, String> map = new HashMap<>();
        MutableKey key = new MutableKey(1);

        map.put(key, "An");
        System.out.println("hash trước: " + key.hashCode());
        System.out.println("get trước: " + map.get(key));
        System.out.println("size trước: " + map.size());

        key.setId(2); // Lỗi thiết kế: sửa trường tham gia equals/hashCode

        System.out.println("hash sau: " + key.hashCode());
        System.out.println("get sau: " + map.get(key));
        System.out.println("containsKey sau: " + map.containsKey(key));
        System.out.println("remove sau: " + map.remove(key));
        System.out.println("size sau remove: " + map.size());
        System.out.println("containsValue An: " + map.containsValue("An"));

        for (Map.Entry<MutableKey, String> entry : map.entrySet()) {
            System.out.println("entry còn lại: id="
                    + entry.getKey().id + ", value=" + entry.getValue());
            System.out.println("cùng tham chiếu key: "
                    + (entry.getKey() == key));
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac MutableHashMapKeyDemo.java
java MutableHashMapKeyDemo
~~~

Kết quả với triển khai `HashMap` của JDK 17:

~~~text
hash trước: 1
get trước: An
size trước: 1
hash sau: 2
get sau: null
containsKey sau: false
remove sau: null
size sau remove: 1
containsValue An: true
entry còn lại: id=2, value=An
cùng tham chiếu key: true
~~~

### Giải thích từng kết quả

1. Khi `put(key, "An")`, `hashCode()` trả `1` và `HashMap` lưu entry tương ứng với hash tại thời điểm chèn.
2. `map.get(key)` trước khi sửa trả `"An"` vì đối tượng có trạng thái khớp với thông tin lập chỉ mục.
3. Sau `key.setId(2)`, chính đối tượng `key` thay đổi `hashCode()` từ `1` sang `2`. Entry đã được lưu trước đó **không tự di chuyển**.
4. `map.get(key)` tra cứu theo hash mới. Nó không tìm entry nằm ở vị trí/hash cũ nên trả `null`.
5. `containsKey(key)` là `false` và `remove(key)` trả `null` vì chúng cũng phụ thuộc vào tra cứu khóa theo mã băm mới.
6. `size()` vẫn trả `1` vì lệnh `remove` không xóa được entry; `containsValue("An")` vẫn là `true` vì thao tác này kiểm tra giá trị trên những entry hiện tồn tại.
7. Vòng lặp `entrySet()` chứng minh map còn giữ **đúng đối tượng khóa ban đầu** (so sánh bằng `==` là `true`), giờ có `id == 2`.

Kết quả `null` của `get` chưa tự nó chứng minh không có entry nếu map cho phép lưu giá trị `null`. Trong ví dụ, ta đã biết giá trị được chèn là `"An"` và xác minh bổ sung bằng `containsKey`, `size`, `entrySet`.

## 4. Nhìn sâu vào nguyên nhân: hash được ghi nhận lúc chèn

Để hình dung lỗi, xét mô hình đơn giản tương ứng với ví dụ trên (JDK `HashMap` thường khởi tạo bảng có 16 bucket khi chèn lần đầu). Với các hash nhỏ như `1` và `2`, phép trộn hash của cách triển khai phổ biến giữ nguyên giá trị, dẫn tới bucket khác nhau.

~~~text
Thời điểm put:
key.id = 1  ---> hashCode() = 1
                    |
                    v
           bucket 1: [hash đã lưu = 1, key -> "An"]

Sau khi key.setId(2):
key.id = 2  ---> hashCode() = 2
                    |
                    v
           tìm ở bucket 2: không có entry

Nhưng bucket 1 vẫn giữ:
           [hash đã lưu = 1, key (hiện id=2) -> "An"]
~~~

Đây là mô hình giải thích hiện tượng với JDK 17, **không phải cam kết API** về số bucket, công thức phân bucket hoặc cấu trúc nội bộ cho mọi phiên bản Java.

Trong triển khai `HashMap` của OpenJDK, mỗi entry/nút lưu hash được tính khi chèn. Khi tra cứu, map tính hash từ đối tượng khóa tại thời điểm gọi và dùng hash đó trong việc xác định entry phù hợp. Thay đổi khóa không kích hoạt `put`/rehash cho entry đã có.

Một hiểu nhầm phổ biến là: “`key.equals(key)` chắc chắn trả `true` nên `get(key)` phải thành công.” Thực tế, **so sánh `equals` không cứu được entry nếu bước tìm theo hash đã dẫn đến vị trí khác**. Việc dùng lại cùng tham chiếu (`==`) cũng không giúp.

Một hiểu nhầm khác là “nếu hash mới vô tình rơi vào cùng bucket thì tra cứu sẽ an toàn”. Trong triển khai HashMap phổ biến, điều kiện so khớp còn xét **hash đã lưu** của entry, nên cùng bucket không tạo bảo đảm khi mã băm thay đổi. Ngoài ra, sửa trường chỉ tham gia `equals()` mà không thay đổi `hashCode()` cũng có thể phá vỡ ngữ nghĩa tra cứu bằng những đối tượng khóa tương đương khác. Quy tắc thiết kế vẫn là **giữ ổn định cả các trường ảnh hưởng `equals()` lẫn `hashCode()`**.

## 5. Thí nghiệm mở rộng: put lại cùng một biến khóa

**Chỉ thực hiện để quan sát lỗi, tuyệt đối không dùng để “chữa” map trong ứng dụng.**

Chèn các dòng sau sau phần `for` trong `main` của ví dụ ở mục 3:

~~~java
map.put(key, "Bình");
System.out.println("size sau put lại: " + map.size());
System.out.println("get sau put lại: " + map.get(key));

for (Map.Entry<MutableKey, String> entry : map.entrySet()) {
    System.out.println("entry value: " + entry.getValue()
            + ", cùng tham chiếu: " + (entry.getKey() == key));
}
~~~

Với trạng thái map sau ví dụ chính, kết quả của đoạn bổ sung trong JDK 17:

~~~text
size sau put lại: 2
get sau put lại: Bình
entry value: An, cùng tham chiếu: true
entry value: Bình, cùng tham chiếu: true
~~~

Ở đây, bảng băm có thể chứa **hai entry cùng giữ một tham chiếu khóa**: entry cũ vẫn nằm theo hash `1`, entry mới được chèn theo hash `2`. `size()` thành `2` mặc dù hai khóa là cùng đối tượng.

Điều này diễn ra vì `HashMap` không thể tìm entry cũ bằng hash hiện tại của khóa và coi `put` như chèn entry mới. Nó minh họa trạng thái bất thường khi vi phạm giả định ổn định khóa. Bài thực hành chọn mã băm nhỏ `1`/`2` để kết quả bucket trong JDK 17 rõ ràng; **không được dựa vào hành vi lỗi cụ thể này như một đặc tính được API `Map` bảo đảm**.

Không nên gọi thêm `put`, `remove` hoặc `compute` rồi giả định trạng thái đã phục hồi; một map đã bị biến đổi khóa trong lúc lưu có thể khó kiểm soát, nhất là khi có nhiều key và quan hệ bằng nhau thay đổi.

## 6. Khắc phục bằng khóa bất biến

Cách khắc phục đáng tin cậy nhất là **dùng khóa có trạng thái nhận diện bất biến**. Với Java 17, `record` chỉ chứa thành phần bất biến như `int`, `long`, `String` là lựa chọn ngắn gọn.

Tạo tệp `StableHashMapKeyDemo.java`:

~~~java
import java.util.HashMap;
import java.util.Map;

public class StableHashMapKeyDemo {
    record UserKey(int id) {}

    public static void main(String[] args) {
        Map<UserKey, String> map = new HashMap<>();

        UserKey oldKey = new UserKey(1);
        map.put(oldKey, "An");

        System.out.println("get bằng key tương đương: "
                + map.get(new UserKey(1)));

        UserKey newKey = new UserKey(2);
        System.out.println("get với id mới: " + map.get(newKey));
        System.out.println("get với id cũ: " + map.get(oldKey));

        String oldValue = map.remove(oldKey);
        map.put(newKey, oldValue);

        System.out.println("get sau chuyển key: " + map.get(newKey));
        System.out.println("size: " + map.size());
        System.out.println("chứa key cũ: " + map.containsKey(oldKey));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac StableHashMapKeyDemo.java
java StableHashMapKeyDemo
~~~

Kết quả:

~~~text
get bằng key tương đương: An
get với id mới: null
get với id cũ: An
get sau chuyển key: An
size: 1
chứa key cũ: false
~~~

**Điểm khác biệt:** `UserKey(1)` không bị sửa thành `UserKey(2)`. Khi nghiệp vụ muốn thay đổi định danh, ứng dụng **xóa entry bằng khóa cũ trước**, rồi chèn bằng **đối tượng khóa mới**. Trạng thái bảng băm và quy tắc tra cứu được duy trì.

Trong tình huống tổng quát, `remove(oldKey)` có thể trả `null` vì không tồn tại key *hoặc* vì key có giá trị `null`. Nếu map cho phép giá trị `null`, kiểm tra `containsKey(oldKey)` trước khi chuyển khóa để phân biệt hai tình huống. Đồng thời xác định rõ chính sách khi `newKey` đã tồn tại, vì `put(newKey, value)` có thể ghi đè dữ liệu cũ. Ví dụ trên có dữ liệu đơn giản nên không gặp hai vấn đề này.

### 6.1. Record không tự tạo bất biến sâu

Không nên suy luận rằng mọi `record` đều là khóa an toàn. Ví dụ:

~~~java
record ArrayKey(int[] values) {}
~~~

Mảng vẫn thay đổi được. Hơn nữa, `equals/hashCode` được sinh tự động cho thành phần kiểu mảng sử dụng cách so sánh/hash của chính tham chiếu mảng, **không phải nội dung mảng**. Nếu nghiệp vụ muốn so sánh theo từng phần tử, cần tự xác định quan hệ bình đẳng và bảo vệ trạng thái theo quan hệ đó. Trường reference dạng collection hoặc bean mutable trong `record` cũng cần xem xét alias và cách tính `equals/hashCode`.

Với định danh đơn giản, `record UserKey(int id)` là ví dụ tốt vì `int` không có đối tượng mutable lồng bên trong.

### 6.2. Giá trị (value) có thể thay đổi; khóa nên ổn định

Một cách thiết kế khác là lưu đối tượng nghiệp vụ mutable làm **value**, và dùng ID bất biến làm **key**:

~~~java
// Đoạn minh họa bên trong một phương thức; account là đối tượng nghiệp vụ.
Map<Long, Account> byId = new HashMap<>();
byId.put(account.getId(), account);
account.setDisplayName("Tên mới"); // Không đổi Long key
~~~

`Account` ở đây chỉ là tên lớp nghiệp vụ minh họa, không phải lớp của JDK hay chương trình chạy độc lập. Nếu ID chính nó cần thay đổi, phải xác định cách di chuyển entry trong map theo khóa cũ và khóa mới. Không được đổi đối tượng khóa đang lưu theo cách ảnh hưởng đến `equals/hashCode`.

## 7. Nếu bắt buộc dùng lớp khóa có trường mutable?

Có những lớp có setter nhưng vẫn có thể được dùng làm khóa nếu **chỉ những trường không ảnh hưởng đến `equals/hashCode` được phép đổi trong lúc làm key**. Ví dụ một `User` có `final long id` dùng cho `equals/hashCode` và `displayName` mutable *không tham gia* hai phương thức: đổi tên không tự phá vỡ tra cứu theo ID.

Tuy nhiên, cần thỏa mãn đồng thời:

- Quy tắc `equals` và `hashCode` dùng chính xác cùng tiêu chí nhận diện và giữ ổn định trong suốt vòng đời làm khóa.
- Không tồn tại setter, alias ngoài lớp hoặc reference lồng bên trong có thể âm thầm làm thay đổi những thông tin nhận diện đó.
- Các trường dùng trong so sánh không phụ thuộc vào trạng thái ngoài đối tượng có thể thay đổi, chẳng hạn cấu hình, thời gian hoặc collection mutable.
- Các đối tượng được coi là bằng nhau luôn cho cùng hash. Tránh sử dụng dữ liệu thay đổi hoặc các quy tắc không ổn định trong tính hash.

**Chú ý:** dùng `final` cho biến giữ khóa, ví dụ `final MutableKey key = ...`, chỉ ngăn gán biến sang đối tượng khác; `key.setId(2)` vẫn hợp lệ. Dùng `final` trên một trường tham chiếu cũng không biến đối tượng được tham chiếu thành bất biến. Với mảng hoặc collection tham gia nhận diện, cần copy phòng vệ và/hoặc dùng cấu trúc thực sự bất biến.

### Khi đã lỡ làm hỏng HashMap

Không có phương thức “làm mới hash cho tất cả khóa đang bị thay đổi” mà `Map` cam kết xử lý mọi trạng thái hỏng. Có thể:

1. Nếu biết chính xác trạng thái nhận diện cũ và chưa phát sinh entry trùng bất thường, phục hồi khóa về trạng thái cũ **chỉ như biện pháp xử lý tình huống được kiểm soát**, xóa entry bằng trạng thái cũ rồi chuyển sang khóa bất biến mới.
2. Nếu còn dữ liệu nguồn đáng tin cậy (database, danh sách đối tượng có ID ổn định, bản snapshot), **tạo map mới** bằng các khóa bất biến được dựng từ nguồn đó. Xác định chính sách khi nhiều entry quy về cùng khóa logic.
3. Nếu phải duyệt `entrySet()` của map lỗi để cứu dữ liệu, phải kiểm tra trùng khóa, giá trị xung đột và alias; đây không phải cách phục hồi tự động bảo đảm không mất dữ liệu.

Ngay cả `new HashMap<>(damagedMap)` cũng **không phải** một bản vá thiết kế: nó không làm khóa thành bất biến và có thể hợp nhất các khóa được xem là bằng nhau ở trạng thái hiện tại hoặc duy trì nguy cơ thay đổi về sau.

## 8. Quy trình gỡ lỗi trong dự án thật

Khi thấy `get(key)` trả `null` bất ngờ, nên kiểm tra theo trình tự:

1. **Kiểm tra đúng instance của map:** biến đang đọc có trỏ tới map mà trước đó đã `put` không? Có ai `clear` hoặc `remove` không?
2. **Phân biệt không tìm được khóa với giá trị null:** dùng `containsKey(key)` và xác định map có cho lưu null value không.
3. **Ghi log các thuộc tính nhận diện trước và sau:** nếu có thể, log ID, các trường tham gia `equals/hashCode` và hash tại hai thời điểm; không chỉ log `toString()`.
4. **Kiểm tra setter và alias:** đối tượng khóa có bị thay đổi từ một chỗ khác sau `put` không? Có collection/mảng lồng bên trong tham gia so sánh không?
5. **Duyệt `entrySet()` phục vụ chẩn đoán:** xem entry còn tồn tại không, nhận diện khóa hiện tại ra sao. Việc tìm thấy đối tượng trong vòng lặp không chứng minh `get(key)` phải tìm được.
6. **Kiểm tra hiện thực `equals/hashCode`:** hai đối tượng được xem là bằng nhau phải có cùng hash; kiểm tra tính đối xứng, bắc cầu, ổn định và xử lý `null` đúng hợp đồng.
7. **Thay đổi thiết kế rồi tái kiểm thử:** dùng key immutable hoặc tách key/value. Đừng vá bằng cách chèn lại cùng khóa mutable để che triệu chứng.

Lỗi này không được giải quyết chỉ bằng cách chuyển sang `ConcurrentHashMap` hay khóa đồng bộ: collection concurrent xử lý truy cập đa luồng, nhưng **không biến trạng thái nhận diện của một đối tượng khóa mutable thành bất biến**. Nếu khóa có thể đổi trong khi đang được dùng, vấn đề thiết kế vẫn còn.

## 9. Bài tập tự kiểm tra

### Câu 1: Cùng object reference có bảo đảm `get` thành công?

**Không.** Ví dụ ở mục 3 dùng cùng biến `key` cả khi `put` và `get`. HashMap không dựa vào việc duyệt tất cả phần tử và so sánh identity để tìm entry.

### Câu 2: Nếu sửa trường không tham gia `equals/hashCode` thì sao?

Nếu trường đó thực sự không ảnh hưởng trực tiếp hoặc gián tiếp tới hash và quan hệ bằng nhau, riêng sự thay đổi đó không làm hỏng lập chỉ mục khóa. Tuy nhiên, bản thân chương trình vẫn cần xử lý đúng các yêu cầu khác như thread safety.

### Câu 3: Có nên dùng `System.identityHashCode(key)` cho khóa?

Không nên xem đây là cách sửa lỗi chung. Identity hash chỉ gắn với nhận diện đối tượng và không phản ánh bình đẳng theo giá trị. Chọn `equals/hashCode` theo hợp đồng nhận diện khóa. Nếu bài toán cần so sánh theo identity thật sự, cân nhắc `IdentityHashMap` với hiểu biết rõ về ngữ nghĩa `==` và các khác biệt so với `HashMap`; nó không phù hợp để thay thế một cách máy móc.

### Câu 4: Vì sao `HashSet` cũng có thể gặp lỗi tương tự?

Trong triển khai Java thông dụng, `HashSet` dựa trên cấu trúc băm. Nếu thêm một đối tượng rồi thay đổi trạng thái ảnh hưởng tới `hashCode/equals`, `contains` hoặc `remove` có thể thất bại ngay cả khi phần tử vẫn còn trong lần duyệt. Đây là cùng lớp lỗi **thay đổi trạng thái nhận diện khi đang lưu trong collection dựa trên hash**.

### Câu 5: Có cần bắt buộc mọi đối tượng làm key phải immutable?

Hợp đồng `Map` không bắt buộc kiểu khóa được khai báo `final` hay hoàn toàn immutable. Điều kiện quan trọng là **thông tin ảnh hưởng đến bình đẳng và mã băm phải giữ ổn định** khi khóa đang trong map. Trong thực hành, thiết kế immutable là cách bảo đảm điều này rõ ràng và dễ review nhất.

## 10. Checklist nhớ nhanh

- Trước `put`: biết những trường nào tham gia `equals/hashCode`.
- Trong thời gian khóa nằm trong map: **không thay đổi** các trường đó hoặc dữ liệu mutable mà chúng phụ thuộc.
- Khi muốn thay “ID khóa”: dùng **khóa mới**, xóa bằng **khóa cũ còn nguyên trạng** rồi chèn lại theo chính sách xử lý xung đột.
- Khi `get` trả `null`: đối chiếu `containsKey`, `size`, `entrySet` và trạng thái khóa; đừng kết luận ngay dữ liệu đã biến mất.
- Trong thiết kế mới: ưu tiên ID primitive/`String`/kiểu immutable hoặc `record` chỉ chứa thành phần nhận diện bất biến.

**Ghi nhớ:** `HashMap` không theo dõi setter để tự lập chỉ mục lại. Khóa có thể vẫn nằm trong map nhưng bị “thất lạc” khi tra cứu vì **trạng thái dùng để xác định hash/equals đã thay đổi sau lúc chèn**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-006">So sánh HashSet và TreeSet với BigDecimal hoặc Comparator theo một khóa</a>

<details>
<summary>Xem chi tiết</summary>

# Thực hành: So sánh HashSet và TreeSet với BigDecimal hoặc Comparator theo một khóa

## 1. Mục tiêu của bài thực hành

Hai tập hợp `HashSet` và `TreeSet` đều triển khai giao diện `Set`: không chứa hai phần tử được coi là trùng nhau theo **quy tắc xác định tính duy nhất** của từng triển khai. Tuy nhiên, chúng có thể cho kết quả khác nhau khi dữ liệu có nhiều cách định nghĩa “bằng nhau”.

Sau bài này, bạn cần có thể:

- Giải thích vì sao `HashSet<BigDecimal>` và `TreeSet<BigDecimal>` có thể giữ **số lượng phần tử khác nhau** với cùng dữ liệu đầu vào.
- Dự đoán `add()`, `contains()` và số lượng phần tử từ `equals()/hashCode()` hoặc `compareTo()/Comparator`.
- Thực hành với `Comparator` chỉ so sánh **một khóa** của đối tượng: hai object khác nhau về các thuộc tính còn lại vẫn có thể bị `TreeSet` coi là một phần tử.
- Phân biệt **định danh theo toàn bộ giá trị đối tượng** và **định danh theo khóa nghiệp vụ**; chọn chiến lược nhất quán cho bài toán thực tế.
- Tránh các lỗi do bất nhất giữa cách sắp xếp và `equals()`, do thay đổi khóa trong tập hợp và do giả định thứ tự của `HashSet`.

Bài tập thuộc chủ đề **Java core – object-contract**, nhấn mạnh hợp đồng `equals`/`hashCode` và quan hệ của chúng với thứ tự so sánh. Không cần hệ cơ sở dữ liệu, framework hay thư viện bên ngoài.

## 2. Điểm khác biệt cốt lõi: “trùng nhau” được quyết định ở đâu?

| Thuộc tính | `HashSet<E>` | `TreeSet<E>` |
| --- | --- | --- |
| Cấu trúc bên dưới | `HashMap` dựa trên hash | Cấu trúc cây có thứ tự dựa trên `TreeMap` |
| Cách xác định phần tử trùng | `equals()`, với `hashCode()` phù hợp để tìm vị trí | Kết quả `compareTo() == 0` hoặc `Comparator.compare(a, b) == 0` |
| Thứ tự duyệt | Không bảo đảm thứ tự | Theo natural ordering hoặc comparator |
| Chi phí tìm/thêm/xóa điển hình | Trung bình O(1) nếu băm tốt; trường hợp xấu có thể cao hơn | O(log n) |
| Cho phép `null` | Có thể lưu một `null` | Natural ordering không nhận `null`; comparator tùy định nghĩa |
| Ràng buộc quan trọng | Hai object `equals()==true` phải có `hashCode()` bằng nhau | Thứ tự so sánh nên nhất quán với `equals()` nếu muốn tuân thủ đầy đủ hợp đồng `Set` |

**`HashSet`:** về ý nghĩa, trước hết một phần tử mới phải có `hashCode()` giúp tìm vị trí thích hợp; nếu có phần tử khác bằng nhau theo `equals()`, thao tác `add()` không đưa thêm một phần tử duy nhất mới vào tập hợp. Không nên hiểu là “chỉ cần cùng hash là trùng”: **hash collision không đồng nghĩa equals**.

**`TreeSet`:** việc thêm, tìm kiếm và xóa dựa vào phép so sánh. Kết quả **0** là “tương đương cho tập hợp có thứ tự” dù `equals()` của hai object trả về `false`. Comparator làm việc theo một khóa có thể biến nhiều đối tượng khác nhau thành cùng một lớp tương đương.

Trong ví dụ bất kỳ: nếu `compare(a,b) == 0` nhưng `!a.equals(b)`, `TreeSet` có thể giữ một trong hai, còn `HashSet` vẫn giữ cả hai (giả sử hashCode/equals của chúng đúng hợp đồng).

### Kết quả boolean của `add()` có ý nghĩa gì?

`set.add(element)` trả về `true` nếu tập hợp thay đổi do thêm phần tử mới, hoặc `false` nếu phần tử đã được coi là tồn tại. Với `TreeSet`, `false` có thể có nghĩa “**đã có phần tử cùng khóa so sánh**”, không phải “đã có đúng object có mọi thuộc tính giống nhau”.

`TreeSet` không tự ghi đè object cũ bằng object mới khi comparator trả về 0. Theo luồng thêm lần lượt, phần tử được giữ là phần tử của lần thêm thành công trước đó, trừ khi bạn chủ động xóa/thay thế.

## 3. BigDecimal: bằng nhau về số học khác bằng nhau theo equals()

`BigDecimal` vừa triển khai `Comparable<BigDecimal>` vừa có `equals()` và `hashCode()` của riêng nó. Với `BigDecimal`, phần **scale** (số chữ số thập phân biểu diễn) là yếu tố quan trọng.

Hai số sau có giá trị số học bằng nhau:

~~~java
BigDecimal a = new BigDecimal("2.0");  // unscaled=20, scale=1
BigDecimal b = new BigDecimal("2.00"); // unscaled=200, scale=2
~~~

Tuy nhiên:

- `a.equals(b)` là **false** vì cả giá trị và scale phải phù hợp theo quy tắc `BigDecimal.equals()`.
- `a.compareTo(b)` là **0** vì thứ tự tự nhiên của `BigDecimal` so sánh **giá trị số học**; hai dạng biểu diễn cùng một giá trị có thể tương đương.
- `HashSet` coi hai đối tượng trên **khác nhau** theo `equals()`.
- `TreeSet` dùng natural ordering của `BigDecimal` nên coi hai đối tượng là **cùng một phần tử theo thứ tự so sánh**.

Đây là một ngoại lệ được công bố trong tài liệu Java: natural ordering của `BigDecimal` **không nhất quán với equals**. Không có lỗi trong Java hay trong hai tập hợp; vấn đề là hai định nghĩa tính duy nhất khác nhau.

## 4. Chương trình Java 17 chạy được: so sánh cả hai tình huống

Chương trình dưới đây gồm hai phần:

1. Thêm `BigDecimal("2.0")` và `BigDecimal("2.00")` vào cả `HashSet` và `TreeSet`.
2. Thêm hai `Product` cùng `sku` nhưng khác `name`; thử `TreeSet` chỉ so sánh `sku` và một comparator có khóa phụ `name`.

Lưu **toàn bộ** chương trình vào file **SetComparisonDemo.java**:

~~~java
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class SetComparisonDemo {

    record Product(String sku, String name) { }

    public static void main(String[] args) {
        compareBigDecimals();
        compareProducts();
    }

    private static void compareBigDecimals() {
        BigDecimal a = new BigDecimal("2.0");
        BigDecimal b = new BigDecimal("2.00");

        Set<BigDecimal> hash = new HashSet<>();
        Set<BigDecimal> tree = new TreeSet<>();

        System.out.println("=== BigDecimal ===");
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("a.compareTo(b): " + a.compareTo(b));

        System.out.println("HashSet add(a): " + hash.add(a));
        System.out.println("HashSet add(b): " + hash.add(b));
        System.out.println("TreeSet add(a): " + tree.add(a));
        System.out.println("TreeSet add(b): " + tree.add(b));

        System.out.println("HashSet size: " + hash.size());
        System.out.println("TreeSet size: " + tree.size());
        System.out.println("TreeSet values: " + tree);
    }

    private static void compareProducts() {
        Product a = new Product("SKU-1", "Notebook");
        Product b = new Product("SKU-1", "Notebook Pro");

        Set<Product> hash = new HashSet<>();
        Set<Product> treeBySku =
                new TreeSet<>(Comparator.comparing(Product::sku));
        Set<Product> treeBySkuAndName = new TreeSet<>(
                Comparator.comparing(Product::sku)
                        .thenComparing(Product::name));

        System.out.println("=== Product ===");
        System.out.println("a.equals(b): " + a.equals(b));

        hash.add(a);
        hash.add(b);
        System.out.println("HashSet size: " + hash.size());

        System.out.println("TreeSet by SKU add(a): " + treeBySku.add(a));
        System.out.println("TreeSet by SKU add(b): " + treeBySku.add(b));
        System.out.println("TreeSet by SKU size: " + treeBySku.size());
        System.out.println("TreeSet by SKU first: "
                + ((TreeSet<Product>) treeBySku).first());
        System.out.println("TreeSet by SKU contains(b): "
                + treeBySku.contains(b));

        treeBySkuAndName.add(a);
        treeBySkuAndName.add(b);
        System.out.println("TreeSet by SKU+name size: "
                + treeBySkuAndName.size());
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 SetComparisonDemo.java
java SetComparisonDemo
~~~

Đầu ra dự kiến (không in trực tiếp `HashSet` vì thứ tự duyệt của nó không được bảo đảm):

~~~text
=== BigDecimal ===
a.equals(b): false
a.compareTo(b): 0
HashSet add(a): true
HashSet add(b): true
TreeSet add(a): true
TreeSet add(b): false
HashSet size: 2
TreeSet size: 1
TreeSet values: [2.0]
=== Product ===
a.equals(b): false
HashSet size: 2
TreeSet by SKU add(a): true
TreeSet by SKU add(b): false
TreeSet by SKU size: 1
TreeSet by SKU first: Product[sku=SKU-1, name=Notebook]
TreeSet by SKU contains(b): true
TreeSet by SKU+name size: 2
~~~

### Đọc kết quả `BigDecimal`

- `HashSet add(b): true`: hai số khác scale nên không bằng nhau theo `BigDecimal.equals()`. `HashSet` tăng lên 2 phần tử.
- `TreeSet add(b): false`: `b.compareTo(a) == 0`; cây đã có một phần tử tương đương theo thứ tự tự nhiên.
- `TreeSet values: [2.0]`: tập giữ object đầu tiên (`a`) đã thêm thành công. Nếu đảo thứ tự thêm `b` rồi `a`, phần tử đại diện có thể hiển thị `2.00`.
- Không dùng khác biệt kích thước trên để kết luận một tập hợp “đúng” còn tập hợp kia “sai”. Phải xem yêu cầu nghiệp vụ coi scale có quan trọng hay không.

### Đọc kết quả `Product`

`record Product(String sku, String name)` tự sinh `equals()` và `hashCode()` xét **cả hai thành phần**. Vì `name` khác nhau, `a.equals(b) == false`, nên `HashSet` giữ cả hai.

Comparator `Comparator.comparing(Product::sku)` lại chỉ xem `sku`. Hai sản phẩm cùng SKU có `compare(a,b) == 0` dù tên khác. Vì vậy `TreeSet` theo SKU chỉ giữ bản ghi thêm trước; `contains(b)` có thể trả về **true** ngay cả khi object `b` chưa từng được giữ làm phần tử trong cây. `contains()` hỏi có **phần tử cùng lớp so sánh** hay không.

Khi thêm `thenComparing(Product::name)`, tên trở thành khóa phụ; hai sản phẩm có cùng SKU nhưng tên khác sẽ được sắp thành hai vị trí, nên kích thước là 2.

**Lưu ý phạm vi:** Comparator theo SKU+name phù hợp với `equals()` do record này chỉ gồm hai trường `String` không null và hai kiểu chuỗi dùng thứ tự tự nhiên nhất quán với equals. Nếu record có thêm trường thuộc hợp đồng equals, comparator chỉ theo SKU+name **không còn chắc** nhất quán với equals.

## 5. Thực hành có chủ đích: dự đoán trước khi chạy

### Bài 1 — nhiều cách biểu diễn của cùng một số

Cho:

~~~java
BigDecimal x = new BigDecimal("10.0");
BigDecimal y = new BigDecimal("10.00");
BigDecimal z = new BigDecimal("9.0");
~~~

Nếu thêm lần lượt `x, y, z` vào hai tập hợp trống:

| Kiểm tra | `HashSet<BigDecimal>` | `TreeSet<BigDecimal>` |
| --- | --- | --- |
| Thêm `x` | `true` | `true` |
| Thêm `y` | `true` | `false` |
| Thêm `z` | `true` | `true` |
| Kích thước | 3 | 2 |
| Thứ tự duyệt | Không cam kết | Tăng dần về giá trị số học |

**Tại sao?** `x.equals(y) == false` nhưng `x.compareTo(y) == 0`; còn `z` có giá trị nhỏ hơn và khác cả hai theo mọi tiêu chí trên.

### Bài 2 — comparator chỉ xét một khóa

Xét các bản ghi:

~~~java
record Employee(String departmentId, String fullName) { }

Employee first = new Employee("D01", "An");
Employee second = new Employee("D01", "Binh");

Set<Employee> staff = new TreeSet<>(
        Comparator.comparing(Employee::departmentId));
~~~

Nếu thêm `first` rồi `second`:

- `staff.size()` là **1**, vì comparator chỉ xét `departmentId`.
- Phần tử còn lại mang tên **An**, vì `first` được thêm trước.
- `staff.contains(second)` trả về **true** theo comparator, mặc dù `second` không được lưu làm object đại diện.

**Câu hỏi:** Nếu yêu cầu là “không có hai nhân viên cùng phòng ban”, đã đủ chưa?

Về tập khóa, comparator đáp ứng phép loại trùng theo department ID. Nhưng về **mô hình nghiệp vụ**, giữ chỉ một nhân viên cho mỗi phòng có thể làm mất dữ liệu và không mô tả đúng quan hệ giữa phòng ban và nhân viên. Nên cân nhắc `Map<String, Employee>` nếu thật sự chỉ có một người đại diện cho mỗi mã phòng, hoặc `Map<String, List<Employee>>` nếu mỗi phòng có nhiều người. Comparator không thể tự quyết định chính sách mất dữ liệu là đúng.

## 6. Chọn tiêu chí đồng nhất theo mục đích nghiệp vụ

Trước khi chọn `HashSet`, `TreeSet` hay `Map`, cần xác định **đơn vị duy nhất**.

### Trường hợp A — phân biệt cả biểu diễn thập phân

Ví dụ hệ thống kiểm tra dữ liệu đầu vào muốn phân biệt người dùng nhập `2.0` với `2.00` để giữ độ chính xác hiển thị hoặc scale theo quy tắc dữ liệu. `HashSet<BigDecimal>` phù hợp hơn nếu muốn phản ánh trực tiếp `equals` của `BigDecimal`; nhớ rằng “phù hợp” ở đây là vì **định nghĩa danh tính bao gồm scale**.

### Trường hợp B — cùng giá trị số học phải được coi là trùng

Nếu `2.0` và `2.00` phải đại diện cho cùng một giá trị:

- `TreeSet<BigDecimal>` theo natural ordering loại trùng dựa trên `compareTo() == 0` và đồng thời sắp xếp số học.
- Nếu cần cấu trúc hash, có thể **chuẩn hóa** giá trị trước khi thêm, ví dụ `value.stripTrailingZeros()` (với chính sách chuẩn hóa được áp dụng thống nhất). Các biểu diễn `2.0` và `2.00` sau chuẩn hóa trở nên `equals() == true`.

Đoạn minh họa trong phương thức Java có `import java.math.BigDecimal`, `import java.util.Set` và `import java.util.HashSet`:

~~~java
Set<BigDecimal> canonical = new HashSet<>();
canonical.add(new BigDecimal("2.0").stripTrailingZeros());
canonical.add(new BigDecimal("2.00").stripTrailingZeros());
System.out.println(canonical.size()); // 1
~~~

Cẩn thận: `stripTrailingZeros()` có thể đổi scale thành **số âm** và đổi cách hiển thị của một số, ví dụ một số nguyên lớn có các chữ số 0 cuối. Nếu định dạng xuất dữ liệu là yêu cầu nghiệp vụ, không được chỉ dựa vào `toString()` của bản đã chuẩn hóa; cần tách giá trị số học và định dạng hiển thị.

### Trường hợp C — tiền tệ có scale cố định

Nếu dữ liệu tiền luôn phải có đúng hai chữ số thập phân, có thể định nghĩa chính sách chuyển về scale 2:

~~~java
BigDecimal amount = new BigDecimal("2.0")
        .setScale(2, java.math.RoundingMode.UNNECESSARY);
System.out.println(amount.toPlainString()); // 2.00
~~~

Dùng `RoundingMode.UNNECESSARY` sẽ ném `ArithmeticException` nếu phải làm tròn để đạt scale yêu cầu. Trong ứng dụng thật, chính sách làm tròn cần được xác định theo loại tiền, quy tắc nghiệp vụ và nguồn dữ liệu. **Không tự làm tròn im lặng** chỉ để khớp khóa.

### Trường hợp D — chỉ duy nhất theo mã nghiệp vụ

Nếu dữ liệu có `id` hoặc `sku` làm khóa duy nhất, `TreeSet<>(Comparator.comparing(Entity::id))` có thể đảm bảo một đại diện cho mỗi khóa, đồng thời sắp tăng theo khóa. Nhưng quan hệ này có thể **không nhất quán với `equals()` của Entity**.

Một `Map<Key, Value>` thường thể hiện ý nghĩa “mỗi khóa có một giá trị” rõ ràng hơn. Ví dụ:

~~~java
Map<String, String> latestNameBySku = new java.util.HashMap<>();
latestNameBySku.put("SKU-1", "Notebook");
latestNameBySku.put("SKU-1", "Notebook Pro");
System.out.println(latestNameBySku.get("SKU-1")); // Notebook Pro
~~~

Trong một phương thức Java, đoạn này cần `import java.util.Map`; `put` đã chủ động chọn chính sách ghi đè giá trị trước bằng giá trị sau. Nếu **không** muốn ghi đè, dùng `putIfAbsent` hoặc xử lý xung đột một cách tường minh.

## 7. “Comparator nhất quán với equals” nghĩa là gì?

Một thứ tự được gọi là **consistent with equals** khi với mọi `a, b` hợp lệ, điều kiện `compare(a,b) == 0` tương đương với `a.equals(b) == true`.

Trong bài này có hai ví dụ **không** thỏa mãn:

- `BigDecimal("2.0")` và `BigDecimal("2.00")`: `compareTo == 0` nhưng `equals == false`.
- Hai `Product` cùng SKU, khác tên: comparator theo SKU trả 0 nhưng record equals trả false.

Theo hợp đồng `SortedSet`/`Set`, thứ tự của sorted set nên nhất quán với equals nếu muốn sorted set tuân thủ **đầy đủ** hợp đồng chung của `Set`. `TreeSet` vẫn hoạt động xác định khi comparator không nhất quán; nhưng một số thao tác kiểm tra bằng nhau giữa các tập hợp, `contains` hay luồng xử lý kết hợp `HashSet` và `TreeSet` có thể khiến người dùng bất ngờ. Đừng giả định hai tập hợp trên có thể thay thế cho nhau ở mọi vị trí chỉ vì cả hai đều là `Set`.

Không cố sửa `BigDecimal.equals()` (đây là kiểu thư viện bất biến). Nếu cần chính sách so sánh khác, áp dụng chiến lược chuẩn hóa, wrapper/domain value object hoặc collection phù hợp.

### Comparator có khóa phụ để tránh mất dữ liệu ngoài ý muốn

Khi muốn tất cả bản ghi được giữ nếu khác nhau ở các trường có ý nghĩa, hãy bổ sung khóa phụ bằng `thenComparing()`:

~~~java
Comparator<Product> bySkuThenName =
        Comparator.comparing(Product::sku)
                .thenComparing(Product::name);
~~~

Đoạn này sử dụng `Product` đã định nghĩa ở chương trình chính và `import java.util.Comparator`.

Nhưng tránh bổ sung khóa phụ chỉ để “không còn trùng” một cách máy móc: nếu chính sách muốn **một sản phẩm trên một SKU**, khóa phụ `name` sẽ làm thay đổi quy tắc nghiệp vụ và có thể không còn đáp ứng yêu cầu.

## 8. Tác động của equals/hashCode tới HashSet

Đối tượng đưa vào `HashSet` phải tuân thủ hợp đồng:

1. `equals` phản xạ, đối xứng, bắc cầu và nhất quán trong lúc các thuộc tính dùng để so sánh không đổi.
2. Nếu `a.equals(b)` là `true`, `a.hashCode() == b.hashCode()` bắt buộc đúng.
3. Hai đối tượng **không bằng nhau** vẫn có thể trùng mã hash; `HashSet` tiếp tục dùng `equals()` khi cần.
4. Khi override `equals()`, hầu như luôn cần override `hashCode()` để duy trì hợp đồng.

Record tạo `equals()/hashCode()` theo các thành phần một cách tự động, nên ví dụ `Product` đã có hợp đồng đồng nhất theo `sku` **và** `name`. Tuy nhiên record có thể chứa thành phần mutable; đừng tự động cho rằng mọi record đều bất biến sâu.

Nếu muốn danh tính của `Product` chỉ dựa vào SKU trong **mọi** cấu trúc, hãy thiết kế một lớp/value object có `equals/hashCode` phù hợp với chính sách đó; không chỉ thay `Comparator` ở một điểm dùng `TreeSet`.

## 9. Tránh thay đổi khóa khi phần tử đang nằm trong Set

Đây là lỗi nguy hiểm với cả hai triển khai, đặc biệt khi đối tượng có trường mutable:

- Với `HashSet`, thay đổi trường tham gia `hashCode()/equals()` có thể làm đối tượng rơi vào bucket không khớp khi tra cứu, khiến `contains()/remove()` không hoạt động như mong đợi.
- Với `TreeSet`, thay đổi trường mà comparator dùng để xếp thứ tự có thể phá cấu trúc thứ tự của cây; tìm kiếm hoặc thêm mới có thể cho kết quả khó hiểu.
- Sửa trường không ảnh hưởng đến equality/comparator có thể vẫn an toàn về tính duy nhất và thứ tự, nhưng cần xét bất biến nghiệp vụ và đồng thời.

**Nguyên tắc:** dùng khóa bất biến cho phần tử trong tập hợp; nếu bắt buộc đổi khóa, nên **remove trước khi sửa, rồi add lại** theo một luồng xử lý có kiểm soát, hoặc sử dụng mô hình dữ liệu khác phù hợp. Không nên thay đổi khóa lén lút qua tham chiếu bên ngoài khi phần tử vẫn đang nằm trong collection.

## 10. Một số khác biệt và giới hạn cần quan sát

### Thứ tự và hiệu năng

`HashSet` không đảm bảo thứ tự duyệt, kể cả khi chạy chương trình nhiều lần có vẻ cho ra một thứ tự ổn định. Không viết test kiểm tra chuỗi in trực tiếp của `HashSet` theo một thứ tự cụ thể.

`TreeSet` cung cấp các thao tác có thứ tự từ `NavigableSet` như `first()`, `last()`, `lower()`, `floor()`, `ceiling()`, `higher()`. Chúng dựa trên comparator hoặc natural ordering; nếu comparator chỉ xét SKU, tất cả hành vi tìm phần tử gần nhất cũng là theo SKU.

`HashSet` thường có thao tác cơ bản trung bình O(1) khi phân phối hash tốt; `TreeSet` là O(log n). Tính duy nhất đúng với yêu cầu và độ ổn định của khóa quan trọng hơn chọn cấu trúc chỉ theo tốc độ.

### Comparator và null

`TreeSet` với natural ordering thường không chấp nhận `null`. Comparator tự định nghĩa có thể xử lý null bằng `Comparator.nullsFirst(...)` hoặc `nullsLast(...)` theo chính sách; tuy nhiên dữ liệu nullable cũng cần được kiểm soát để tránh comparator không xác định. `HashSet` có thể chứa đúng một giá trị `null`.

### Lỗi ClassCastException, so sánh thiếu tính bắc cầu

Nếu đối tượng không triển khai `Comparable` và bạn tạo `new TreeSet<>()` không truyền comparator, thao tác thêm phần tử có thể ném `ClassCastException`. Tương tự, comparator không chấp nhận kiểu được cung cấp cũng gây lỗi so sánh.

Comparator nên xác định thứ tự hợp lệ, nhất quán, đặc biệt về phản xạ, đối xứng dấu và tính bắc cầu theo hợp đồng `Comparator`. Một comparator không bắc cầu có thể làm cấu trúc sắp xếp vận hành không đúng kỳ vọng. Tránh comparator kiểu `return a.someInt - b.someInt;` vì có nguy cơ tràn số nguyên; ưu tiên `Comparator.comparingInt(...)` hoặc `Integer.compare(...)`.

### BigDecimal và giá trị double

Để nhập giá trị thập phân chính xác theo chuỗi, dùng `new BigDecimal("0.1")` hoặc `BigDecimal.valueOf(0.1)` theo nhu cầu; cẩn thận với `new BigDecimal(0.1)` vì chuyển trực tiếp từ biểu diễn nhị phân của `double` có thể tạo giá trị thập phân dài không như người nhập kỳ vọng. Điều này là vấn đề biểu diễn số, **khác** vấn đề equals/compareTo và scale nhưng dễ khiến bài thực hành thêm khó hiểu nếu nhập sai dữ liệu.

## 11. Bảng lỗi thực hành và cách sửa

| Hiện tượng | Nguyên nhân thường gặp | Cách giải quyết |
| --- | --- | --- |
| `HashSet<BigDecimal>` có 2, `TreeSet<BigDecimal>` có 1 | `equals` xét scale, `compareTo` xét giá trị số học | Chọn rõ định nghĩa trùng và áp dụng nhất quán |
| `TreeSet<Product>` mất phần tử khác tên | Comparator chỉ xét SKU | Nếu cần giữ cả hai, thêm khóa phụ; nếu cần duy nhất theo SKU, mô tả chính sách rõ |
| `tree.contains(b) == true` dù object `b` không được lưu | Có phần tử khác được comparator coi tương đương | Hiểu `contains` theo lớp so sánh, không theo object identity |
| So sánh kết quả in `HashSet` bị fail ngẫu nhiên | Không có thứ tự duyệt cam kết | Chỉ assert kích thước/membership hoặc chủ động sắp trước khi in |
| `HashSet` không tìm được object sau khi đổi ID | Thay đổi trường dùng trong equals/hashCode | Khóa bất biến hoặc remove-sửa-add có kiểm soát |
| `TreeSet` tìm sai sau khi đổi khóa sắp xếp | Cây không tự tái sắp xếp sau đột biến | Tránh sửa khóa khi object còn trong tập |
| `TreeSet` ném `ClassCastException` | Thiếu natural ordering/comparator phù hợp | Cung cấp Comparator đúng kiểu |
| Chuẩn hóa `BigDecimal` làm hiển thị khác | `stripTrailingZeros` có thể đổi scale | Tách chính sách giá trị và định dạng hiển thị |

## 12. Checklist kiểm tra một bài toán Set

Khi cần loại phần tử trùng, hãy trả lời theo đúng thứ tự:

1. **“Trùng” có nghĩa gì?** Trùng mọi trường, trùng ID, trùng giá trị số học hay trùng biểu diễn thập phân?
2. **Hợp đồng equals/hashCode là gì?** Hai phần tử nào được coi là bằng nhau theo object?
3. **Comparator hoặc compareTo xét gì?** Khi nào so sánh trả về 0?
4. **Hai quy tắc có nhất quán không?** Nếu không, liệu ứng dụng có chấp nhận sự khác biệt ở `add()`/`contains()`/kết hợp collection?
5. **Có cần thứ tự sắp xếp không?** Nếu không, `HashSet` có thể đơn giản hơn; nếu có, `TreeSet` là lựa chọn nhưng phải chốt comparator.
6. **Khóa có bất biến trong thời gian nằm trong Set không?** Nếu không, cần thiết kế lại cách cập nhật.
7. **Cần một đại diện cho mỗi khóa hay cần giữ toàn bộ bản ghi?** Với một giá trị trên một khóa, `Map<K,V>` thường diễn đạt ý định rõ hơn.

### Tự trả lời

- `HashSet` có dùng `compareTo()` của `BigDecimal` để quyết định trùng không? **Không**.
- `TreeSet` có luôn gọi `equals()` để quyết định trùng không? **Không**; nó dựa vào phép so sánh.
- `BigDecimal("2.0").compareTo(BigDecimal("2.00")) == 0` có suy ra `equals() == true` không? **Không**.
- Nếu comparator chỉ xét `sku`, thêm hai bản ghi cùng SKU nhưng khác tên có giữ cả hai không? **Không**; phần tử được thêm sau bị coi là trùng.
- Nếu thêm khóa phụ `name` thì đã luôn nhất quán với `equals()` chưa? **Chỉ khi** các trường so sánh đủ bao quát hợp đồng `equals` và comparator của từng thành phần nhất quán với equals.

**Kết luận kiến thức:** `HashSet` và `TreeSet` khác nhau ở **hợp đồng xác định tính duy nhất**, không chỉ ở thứ tự hay hiệu năng. Với `BigDecimal` hoặc comparator theo một khóa, hãy xác định trước “bằng nhau” là theo biểu diễn, giá trị số học hay khóa nghiệp vụ. Khi chính sách ấy rõ ràng, thao tác `add()`, `contains()`, kích thước và cách bảo toàn dữ liệu sẽ dự đoán được.

## Tài liệu chính thức để tra cứu

- [Java SE 21 — HashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html)
- [Java SE 21 — TreeSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/TreeSet.html)
- [Java SE 21 — SortedSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/SortedSet.html)
- [Java SE 21 — Comparator](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html)
- [Java SE 21 — BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-007">Thiết kế lớp chứa mảng và kiểm tra sao chép phòng vệ</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế lớp chứa mảng và kiểm tra sao chép phòng vệ

## 1. Bài toán: lớp chứa mảng có thực sự bất biến?

Ta cần xây dựng một lớp **SensorSnapshot** chứa mã thiết bị **String** và dãy số đo **int[]**. Sau khi tạo snapshot, việc caller thay đổi mảng nguồn hoặc mảng nhận từ getter không được làm thay đổi dữ liệu của snapshot. Đây là bài thực hành tổng hợp về **hợp đồng đối tượng (object contract)**: thiết kế dữ liệu có quyền sở hữu rõ ràng, kiểm thử **equals**, **hashCode**, **toString** và tránh phá vỡ tính ổn định khi đối tượng nằm trong **HashSet/HashMap**.

Kết quả cần đạt:
- Giải thích **aliasing**: nhiều tham chiếu cùng trỏ tới một mảng mutable.
- Sao chép phòng vệ ở cả chiều **constructor nhận vào** và **getter trả ra**.
- So sánh/cấp hash cho mảng theo **giá trị phần tử**, không theo danh tính tham chiếu.
- Xử lý đúng **int[][]** bằng sao chép từng hàng; biết vì sao **Object[]** chứa phần tử mutable khó hơn.
- Dùng phép thử chủ động sửa dữ liệu nguồn và dữ liệu getter để chứng minh các cam kết trên.

Mảng là đối tượng mutable. **private** ngăn code ngoài truy cập trực tiếp tới trường, còn **final** chỉ ngăn gán lại tham chiếu trường; cả hai **không cấm sửa phần tử mảng**. Lớp có thể dùng trường **private final int[]** mà vẫn bị thay đổi từ ngoài.

## 2. Xem lỗi trước khi thiết kế bản đúng

~~~java
// Ví dụ cố ý sai, không phải phần của chương trình chính.
final class UnsafeSnapshot {
    private final int[] values;

    UnsafeSnapshot(int[] input) {
        this.values = input;   // Lỗi: giữ alias của caller.
    }

    int[] values() {
        return values;         // Lỗi: làm lộ mảng nội bộ.
    }
}

// Caller có thể thực hiện:
int[] original = {10, 20};
UnsafeSnapshot unsafe = new UnsafeSnapshot(original);
original[0] = 999;         // Dữ liệu bên trong unsafe cũng đổi.
unsafe.values()[1] = 888;  // Getter tạo đường sửa trực tiếp.
~~~

Hai lỗi độc lập:
1. **Đầu vào:** constructor giữ tham chiếu nguồn; caller vẫn sở hữu mảng và có thể thay đổi nó về sau.
2. **Đầu ra:** getter trả tham chiếu nội bộ; code nhận được getter có thể sửa trạng thái đối tượng.

Mẫu đúng với mảng primitive một chiều:

~~~java
this.values = Objects.requireNonNull(input, "input").clone();

// Getter của lớp:
return values.clone();
~~~

Mỗi lần clone tạo một mảng mới. Vì phần tử **int** là primitive, các giá trị được sao chép độc lập; không còn phần tử tham chiếu tới đối tượng mutable cần xử lý tiếp. **Arrays.copyOf** và **System.arraycopy** cũng là lựa chọn hợp lệ khi dùng đúng độ dài/kiểu, nhưng không tự deep copy các phần tử là object.

Hợp đồng của **SensorSnapshot** được xác định rõ: **sensorId** không null/không blank; **readings** không null nhưng có thể rỗng; thứ tự số đo có ý nghĩa; tất cả thao tác đọc theo giá trị phải nhìn thấy cùng dữ liệu từ lúc tạo; caller được tự do sửa các bản sao getter trả về.

## 3. Chương trình Java 17+ hoàn chỉnh

Tạo tệp đúng tên **DefensiveCopyDemo.java**. Hai lớp lồng **SensorSnapshot** và **MatrixSnapshot** mô tả hai mức sao chép. Hàm **check** ném **AssertionError** nếu hợp đồng không đúng, không phụ thuộc việc có bật **-ea** hay không.

~~~java
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DefensiveCopyDemo {
    static final class SensorSnapshot {
        private final String sensorId;
        private final int[] readings;

        SensorSnapshot(String sensorId, int[] readings) {
            this.sensorId = Objects.requireNonNull(sensorId, "sensorId");
            if (sensorId.isBlank()) {
                throw new IllegalArgumentException("sensorId must not be blank");
            }
            this.readings = Objects.requireNonNull(readings, "readings").clone();
        }

        String sensorId() {
            return sensorId;
        }

        int size() {
            return readings.length;
        }

        int readingAt(int index) {
            return readings[index];
        }

        int[] readings() {
            return readings.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SensorSnapshot that)) {
                return false;
            }
            return sensorId.equals(that.sensorId)
                    && Arrays.equals(readings, that.readings);
        }

        @Override
        public int hashCode() {
            return 31 * sensorId.hashCode() + Arrays.hashCode(readings);
        }

        @Override
        public String toString() {
            return "SensorSnapshot{sensorId='" + sensorId
                    + "', readings=" + Arrays.toString(readings) + "}";
        }
    }

    static final class MatrixSnapshot {
        private final int[][] values;

        MatrixSnapshot(int[][] source) {
            values = copyRows(source);
        }

        private static int[][] copyRows(int[][] source) {
            Objects.requireNonNull(source, "source");
            int[][] result = new int[source.length][];
            for (int row = 0; row < source.length; row++) {
                result[row] = Objects.requireNonNull(
                        source[row], "source[" + row + "]").clone();
            }
            return result;
        }

        int get(int row, int column) {
            return values[row][column];
        }

        int[][] toArray() {
            return copyRows(values);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MatrixSnapshot that)) {
                return false;
            }
            return Arrays.deepEquals(values, that.values);
        }

        @Override
        public int hashCode() {
            return Arrays.deepHashCode(values);
        }

        @Override
        public String toString() {
            return "MatrixSnapshot" + Arrays.deepToString(values);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        System.out.println("PASS: " + label);
    }

    public static void main(String[] args) {
        int[] input = {4, 8, 15};
        SensorSnapshot snapshot = new SensorSnapshot("S-1", input);

        input[0] = 999;
        check(snapshot.readingAt(0) == 4, "constructor copy");

        int[] getterResult = snapshot.readings();
        getterResult[1] = 888;
        check(snapshot.readingAt(1) == 8, "getter copy");

        SensorSnapshot same = new SensorSnapshot(
                "S-1", new int[] {4, 8, 15});
        check(snapshot.equals(same) && same.equals(snapshot),
                "value equality");
        check(snapshot.hashCode() == same.hashCode(),
                "equal objects share hashCode");

        Set<SensorSnapshot> set = new HashSet<>();
        set.add(snapshot);
        int[] anotherGetterResult = snapshot.readings();
        anotherGetterResult[2] = -1;
        check(set.contains(same) && set.contains(snapshot),
                "stable HashSet lookup");

        check(snapshot.toString().contains("[4, 8, 15]"),
                "content-based toString");

        int[][] source = {{1, 2}, {3, 4}};
        MatrixSnapshot matrix = new MatrixSnapshot(source);
        source[0][0] = 77;
        source[1] = new int[] {88, 99};
        check(matrix.get(0, 0) == 1 && matrix.get(1, 0) == 3,
                "deep constructor copy");

        int[][] exported = matrix.toArray();
        exported[0][1] = 77;
        exported[1] = new int[] {0};
        check(matrix.get(0, 1) == 2 && matrix.get(1, 0) == 3,
                "deep getter copy");

        MatrixSnapshot sameMatrix = new MatrixSnapshot(
                new int[][] {{1, 2}, {3, 4}});
        check(matrix.equals(sameMatrix)
                        && matrix.hashCode() == sameMatrix.hashCode(),
                "deep value equality and hashCode");

        System.out.println("All 9 checks passed.");
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac DefensiveCopyDemo.java
java DefensiveCopyDemo
~~~

Stdout mong đợi:

~~~text
PASS: constructor copy
PASS: getter copy
PASS: value equality
PASS: equal objects share hashCode
PASS: stable HashSet lookup
PASS: content-based toString
PASS: deep constructor copy
PASS: deep getter copy
PASS: deep value equality and hashCode
All 9 checks passed.
~~~

### Vì sao 9 phép kiểm có ý nghĩa?

- **constructor copy:** giá trị **input[0] = 999** chỉ sửa dữ liệu do caller còn giữ; snapshot vẫn có số đo 4.
- **getter copy:** sửa **getterResult[1]** không thay đổi mảng bên trong; đọc lại được 8. Nếu bỏ copy tại getter, phép kiểm sẽ thất bại.
- **value equality:** hai mảng khác tham chiếu nhưng cùng chuỗi **4, 8, 15** phải tạo ra hai snapshot bằng nhau khi **sensorId** cũng giống nhau. **Arrays.equals** so phần tử, còn **==** trên mảng so tham chiếu.
- **equal objects share hashCode:** hợp đồng Java yêu cầu **a.equals(b)** đúng thì **a.hashCode() == b.hashCode()**. Ngược lại không bắt buộc: hash collision được phép.
- **stable HashSet lookup:** sau khi thêm snapshot vào HashSet, sửa đầu vào và mảng lấy từ getter không được làm thay đổi các trường góp phần vào equals/hashCode. Nếu hashCode của đối tượng trong set thay đổi, tra cứu có thể thất bại.
- **content-based toString:** **Arrays.toString(int[])** hiển thị phần tử như **[4, 8, 15]**; **readings.toString()** chỉ hiển thị mô tả danh tính mảng kiểu **[I@...**.
- **deep constructor copy:** sửa **source[0][0]** hoặc thay toàn bộ hàng **source[1]** không ảnh hưởng ma trận đã lưu.
- **deep getter copy:** sửa ô của mảng xuất ra hay gán lại một hàng ở kết quả getter cũng không ảnh hưởng ma trận bên trong.
- **deep value equality and hashCode:** cùng dữ liệu ma trận tạo ra equals đúng và hashCode tương ứng nhờ **Arrays.deepEquals/deepHashCode**.

## 4. Vì sao cần phân biệt shallow copy và deep copy?

**int[]** chỉ có giá trị primitive nên **clone()** đủ để tạo bản sao độc lập. Nhưng **int[][]** thực chất là *mảng các tham chiếu tới những mảng int[]*. Chỉ clone mảng ngoài sẽ giữ nguyên tham chiếu tới các hàng.

~~~java
int[][] original = {{1, 2}, {3, 4}};
int[][] shallow = original.clone();
shallow[0][0] = 99;
// original[0][0] cũng là 99: cùng tham chiếu tới hàng 0.

int[][] copied = new int[original.length][];
for (int row = 0; row < original.length; row++) {
    copied[row] = original[row].clone();
}
copied[1][0] = 77;
// original[1][0] vẫn là 3.
~~~

**MatrixSnapshot.copyRows** kiểm tra mảng ngoài và từng hàng không null, tạo mảng ngoài mới, rồi clone từng hàng. Hợp đồng của ví dụ chấp nhận hàng rỗng và mảng không đều (*jagged array*), từ chối hàng **null**. Vì từng hàng chứa primitive, độ sâu hai cấp này đã tách hết dữ liệu mutable liên quan.

Với **MutablePoint[]** thì khác: clone mảng chỉ sao chép tham chiếu tới từng Point; caller có thể sửa **points[0].x**. Phải chụp lại dữ liệu từng Point trong đối tượng độc lập, đồng thời sao chép khi getter trả ra; có thể thay Point mutable bằng một record/snapshot chứa các primitive bất biến. Nếu Point lại chứa mảng hay object mutable khác, copy constructor của Point phải xử lý đến các trường đó.

~~~java
// Minh họa, chưa phải chương trình độc lập:
// MutablePoint phải khai báo constructor MutablePoint(MutablePoint).
static MutablePoint[] copyPoints(MutablePoint[] source) {
    Objects.requireNonNull(source, "source");
    MutablePoint[] result = new MutablePoint[source.length];
    for (int i = 0; i < source.length; i++) {
        result[i] = new MutablePoint(
                Objects.requireNonNull(source[i], "point"));
    }
    return result;
}
~~~

Không nên gọi một phương thức là “deep copy” khi chưa biết hợp đồng mutable của từng đối tượng con. Một **List.copyOf(...)** hoặc **Collections.unmodifiableList(...)** cũng không tự tạo deep copy cho các phần tử là object mutable.

| Dữ liệu nội bộ | Sao chép phù hợp | Rủi ro nếu làm thiếu |
| --- | --- | --- |
| **int[]** | Clone một lần cho từng biên nhận/trả | Alias mảng nguồn hoặc getter |
| **String[]** | Clone mảng, vì String immutable | Alias mảng khi không clone |
| **int[][]** | Clone mảng ngoài và từng hàng | Alias hàng bên trong |
| **MutablePoint[]** | Clone mảng và tạo bản sao từng Point mutable | Alias tới đối tượng phần tử |
| Record chứa **int[]** | Copy mảng trong constructor và accessor, điều chỉnh equals/hashCode | Record component vẫn có mảng mutable; equality mặc định không so giá trị phần tử |

## 5. Object contract: equals, hashCode, toString và tính bất biến

Hợp đồng **equals** của **SensorSnapshot** dùng **sensorId.equals** và **Arrays.equals(readings, that.readings)**. Nếu đổi thứ tự số đo thì hai snapshot không bằng nhau; đây là quyết định nghiệp vụ đã công bố. Nếu thực sự muốn ngữ nghĩa tập không thứ tự, phải đổi mô hình và hợp đồng, không chỉ thay code so sánh tùy tiện.

**hashCode** dùng chính các thông tin trong equals: **31 * sensorId.hashCode() + Arrays.hashCode(readings)**. Không dùng **readings.hashCode()** vì đó là băm theo danh tính mảng và có thể khác giữa hai mảng cùng giá trị. **MatrixSnapshot** sử dụng cặp tương ứng **Arrays.deepEquals** và **Arrays.deepHashCode**.

**toString** chỉ là biểu diễn dễ đọc và kiểm tra, không phải định dạng lưu trữ ổn định. Với mảng một chiều dùng **Arrays.toString**, với mảng lồng nhau dùng **Arrays.deepToString**. Không tự động đưa dữ liệu nhạy cảm vào log nếu snapshot thực tế chứa thông tin bí mật.

**Object.clone()** không phải giải pháp mặc định để tạo immutable object: clone mảng tạo mảng ngoài mới, nhưng với mảng tham chiếu vẫn dùng chung đối tượng con. Bài thực hành dùng constructor có validate và sao chép rõ ràng để hợp đồng dễ đọc.

**final** trên class ngăn subclass thay đổi hành vi theo hướng phá giả định về tính bất biến. **final** trên trường bảo vệ danh tính tham chiếu bên trong lớp nhưng không tự freeze đối tượng được trỏ tới. Mọi cách để state mutable thoát ra ngoài đều phải được xem xét.

**record** không tự giải quyết vấn đề: record có component là **int[]** vẫn nhận/trả một mảng mutable theo accessor mặc định, và equality/hashCode sinh sẵn cho component mảng không tự chuyển thành so sánh nội dung. Muốn dùng record, phải bảo vệ constructor/accessor và override hợp đồng so sánh phù hợp.

## 6. Kiểm thử phòng vệ thực sự cần làm gì?

Khi kiểm tra lớp chứa mảng, chỉ test **assertEquals** trên dữ liệu ngay sau constructor là chưa đủ. Cần tấn công cả hai phía và cả các phần tử nested:

1. Tạo snapshot từ mảng nguồn, **sửa nguồn sau khi đã tạo**, rồi kiểm tra snapshot giữ giá trị cũ.
2. Gọi getter, sửa mảng trả về, đọc lại snapshot và xác minh dữ liệu không đổi.
3. Gọi getter hai lần, kiểm tra **first != second** khi hợp đồng quy định mỗi lần đều trả bản sao mới, đồng thời nội dung bằng nhau.
4. Tạo hai snapshot từ hai mảng **khác danh tính nhưng cùng nội dung**, kiểm tra equals hai chiều và hashCode bằng nhau.
5. Thêm snapshot vào HashSet, sửa dữ liệu nguồn/getter, kiểm tra còn tìm được theo snapshot tương đương.
6. Với matrix, sửa **source[0][0]**, gán lại **source[1]**, và thực hiện các thao tác tương tự trên mảng lấy từ getter.
7. Thử **null**, mảng rỗng, mảng không đều, hàng null, chỉ số sai theo quy tắc API đã chọn.
8. Nếu dùng phần tử là object mutable, thay đổi *bên trong phần tử* chứ không chỉ gán phần tử mới vào mảng.

Có thể bổ sung các phép thử sau **bên trong main** của chương trình mục 3 (chúng dùng các lớp và biến đã khai báo ở đó):

~~~java
SensorSnapshot empty = new SensorSnapshot("S-0", new int[0]);
check(empty.size() == 0, "empty readings accepted");

int[] first = snapshot.readings();
int[] second = snapshot.readings();
check(first != second && Arrays.equals(first, second),
        "fresh getter array");

int[][] jagged = {{1}, {2, 3, 4}};
MatrixSnapshot jaggedCopy = new MatrixSnapshot(jagged);
jagged[1][0] = 100;
check(jaggedCopy.get(1, 0) == 2, "jagged rows copied");
~~~

Đây là phần mở rộng **không độc lập**; khi thêm ba lệnh **check**, kết quả số phép thử in cuối chương trình phải được cập nhật từ 9 lên 12.

## 7. Thiết kế API có trách nhiệm về sở hữu dữ liệu

Sao chép mảng dài có chi phí thời gian và bộ nhớ **O(n)**; sao chép ma trận có chi phí tỷ lệ tổng số phần tử. Không phải API nào cũng cần trả toàn bộ bản sao ở mỗi lần đọc: có thể cung cấp **size()**, **readingAt(i)** hoặc một phép tính tổng hợp để caller không phải copy mảng lớn. Nếu nhất định cần trao quyền sở hữu mảng mà không sao chép, phải công bố hợp đồng ownership/transfer thật rõ và chấp nhận rằng không còn đảm bảo “snapshot bất biến” theo nghĩa của bài này.

Cũng cần nêu chính xác miền dữ liệu chấp nhận. Bài mẫu chủ ý từ chối **sensorId null/blank**, **readings null**, matrix null hoặc hàng null; cho phép **int[]** rỗng và matrix không đều. Các quy tắc khác có thể hợp lý trong sản phẩm khác nhưng phải được viết thành contract và test tương ứng.

Với dữ liệu của nhiều thread, defensive copy bảo vệ **sự sở hữu sau khi sao chép hoàn tất**; nó không làm cho việc đọc đồng thời một mảng nguồn đang bị thread khác sửa trở thành snapshot nguyên tử. Caller phải bảo đảm nguồn ổn định trong lúc copy hoặc phối hợp đồng bộ phù hợp trước khi bàn giao.

## 8. Bài tập tự thực hiện và câu hỏi

**Bài A — phá ranh giới:** tạm xóa clone ở constructor của **SensorSnapshot**, dự đoán phép kiểm nào thất bại. Khôi phục constructor, rồi xóa clone ở getter và giải thích vì sao phép kiểm khác thất bại. Kết thúc với toàn bộ 9 kiểm thử qua.

**Bài B — shallow matrix:** thay **MatrixSnapshot.copyRows** bằng **return source.clone()** (sau khi validate null). Thử sửa **source[0][0]** và gán lại **source[1]**. Hai thao tác có ảnh hưởng khác nhau lên snapshot như thế nào? Sửa lại clone từng hàng.

**Bài C — thêm trường vào value object:** bổ sung **String batchId**. Chỉnh constructor, equals, hashCode, toString cho nhất quán; chứng minh khác batchId thì không bằng nhau ngay cả khi mảng giống nhau.

**Bài D — đối tượng phần tử mutable:** tự viết **MutablePoint**, tạo **PointSnapshot** nhận **MutablePoint[]**, rồi cố ý sửa Point trong nguồn và trong getter. Chứng minh phải copy từng Point và phải xem xét field mutable bên trong Point.

Câu hỏi tự kiểm tra:
1. Vì sao **private final int[] readings** chưa bảo đảm dữ liệu bất biến?
2. Chỉ clone ở constructor hoặc chỉ clone ở getter thì còn đường alias nào?
3. **Arrays.equals** khác **==** trên mảng ra sao?
4. Vì sao **equals** so nội dung thì **hashCode** cũng phải băm theo nội dung?
5. Vì sao **int[][]** cần copy từng hàng, còn **int[]** chỉ cần clone một lần?
6. Vì sao một mảng **MutablePoint[]** đòi hỏi thêm quyết định về bản sao của phần tử?
7. Những phép kiểm nào bảo vệ việc lưu value object trong **HashSet**?
8. Defensive copy có tự giải quyết vấn đề thread khác đang sửa nguồn ngay lúc sao chép không?

**Ghi nhớ:** Lớp chứa mảng chỉ có hợp đồng snapshot an toàn khi bản thân lớp **sở hữu dữ liệu đã được sao chép**, không để lộ alias mutable, copy đủ sâu với cấu trúc lồng nhau, và giữ **equals/hashCode/toString** phù hợp với nội dung có thể quan sát. Các phép thử phải chủ động sửa nguồn và getter rồi xác nhận snapshot vẫn không đổi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-008">Rà soát một hệ lớp kế thừa làm mất tính đối xứng</a>

<details>
<summary>Xem chi tiết</summary>

# Rà soát hệ lớp kế thừa làm mất tính đối xứng của equals()

## 1. Vấn đề cần phát hiện

Hợp đồng `Object.equals()` trong Java yêu cầu **tính đối xứng**: với hai tham chiếu khác `null` là `x` và `y`, kết quả `x.equals(y)` phải bằng kết quả `y.equals(x)`.

```text
x.equals(y) == y.equals(x)
```

Lỗi thường gặp khi lớp con bổ sung thuộc tính mới: lớp cha dùng `instanceof Parent` nên vẫn chấp nhận instance lớp con, trong khi lớp con dùng `instanceof Child` nên không chấp nhận instance lớp cha. Hai lời gọi thực hiện hai triển khai `equals()` khác nhau do Java sử dụng **dynamic dispatch**.

Cần phân biệt:
- `a == b` trên hai biến tham chiếu là kiểm tra **cùng đối tượng**.
- `a.equals(b)` là so sánh **bằng nhau theo hợp đồng logic của kiểu**.
- `Object.equals()` mặc định chỉ coi cùng identity là bằng nhau; một lớp ghi đè `equals()` có thể định nghĩa phép bằng nhau theo dữ liệu.

Mục tiêu bài rà soát là tìm cặp cha/con làm mất đối xứng, lần theo nguyên nhân, kiểm tra nguy cơ vi phạm tính bắc cầu và hash, áp dụng một chính sách sửa nhất quán, sau đó viết kiểm thử hồi quy.

## 2. Ví dụ lỗi: Point và ColoredPoint

Giả sử `Point` xác định bằng nhau bằng hai tọa độ `x, y`, còn `ColoredPoint extends Point` bổ sung trường `color` được tính trong phép bằng nhau.

Đây là **trích đoạn minh họa**, không phải tệp Java độc lập:

```java
class Point {
    final int x, y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Point other)) return false;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y);
    }
}

class ColoredPoint extends Point {
    final String color;

    ColoredPoint(int x, int y, String color) {
        super(x, y);
        this.color = java.util.Objects.requireNonNull(color);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ColoredPoint other)) return false;
        return super.equals(other) && color.equals(other.color);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), color);
    }
}
```

Với `Point point = new Point(2, 3);` và `ColoredPoint red = new ColoredPoint(2, 3, "red");`, lần lượt kiểm tra:

| Phép gọi | Triển khai được chọn | Kiểm tra kiểu | Kết quả |
| --- | --- | --- | --- |
| `point.equals(red)` | `Point.equals` | `red instanceof Point` đúng | `true`: trùng tọa độ |
| `red.equals(point)` | `ColoredPoint.equals` | `point instanceof ColoredPoint` sai | `false` |
| `point == red` | Toán tử identity | Hai đối tượng khác nhau | `false` |

`instanceof Point` cho phép cả đối tượng thuộc lớp con của `Point`; chiều ngược lại không đúng. Vì vậy:

```text
point.equals(red) = true
red.equals(point) = false
```

**Đây là phản ví dụ trực tiếp của tính đối xứng.** Thay đổi riêng dòng `super.equals(other)` trong lớp con sẽ không giải quyết được việc hai lớp đang có hai chính sách chấp nhận đối tượng khác nhau.

## 3. Tác hại và các hợp đồng cần rà soát cùng lúc

Một `equals()` đúng phải đáp ứng toàn bộ quy tắc, khi thông tin dùng để so sánh không đổi:

| Tính chất | Yêu cầu |
| --- | --- |
| Phản xạ | `x.equals(x)` luôn `true` với `x != null` |
| Đối xứng | `x.equals(y)` và `y.equals(x)` có cùng kết quả |
| Bắc cầu | `x.equals(y)` và `y.equals(z)` đúng thì `x.equals(z)` đúng |
| Nhất quán | Kết quả ổn định qua nhiều lần gọi nếu dữ liệu liên quan không đổi |
| Khác `null` | `x.equals(null)` trả `false` |
| Hash tương ứng | `x.equals(y)` đúng thì `x.hashCode() == y.hashCode()` |

Trong ví dụ lỗi, hash của `Point` chỉ dùng tọa độ nhưng hash của `ColoredPoint` còn dùng màu. Khi `point.equals(red)` cho `true`, hệ thống phải đảm bảo hai hash bằng nhau; công thức khác nhau ở trên **có thể** tạo hash khác nhau và vi phạm thêm hợp đồng `equals()` / `hashCode()`.

`HashSet` và `HashMap` dựa vào cả hai phương thức. Với lớp vi phạm hợp đồng, thêm/xóa/tìm khóa có thể biểu hiện không nhất quán. Tránh coi một số đo `HashSet.size()` trên bộ dữ liệu bất kỳ là bằng chứng hợp đồng vẫn đúng. **Khác đối tượng không bắt buộc khác hash**: trùng hash là được phép, nhưng bằng nhau theo `equals()` bắt buộc trùng hash.

## 4. Bẫy sửa sai: đối xứng trở lại nhưng mất bắc cầu

Một bản sửa tưởng hợp lý có thể cho `ColoredPoint` bằng `Point` nếu cùng tọa độ, nhưng so thêm màu khi cả hai đều là `ColoredPoint`.

Với ba đối tượng:

```text
base = Point(2, 3)
red  = ColoredPoint(2, 3, "red")
blue = ColoredPoint(2, 3, "blue")
```

Nếu màu bị bỏ qua khi một bên là `Point`, nhưng được xét khi cả hai là `ColoredPoint`, ta nhận:

```text
red.equals(base)  = true
base.equals(blue) = true
red.equals(blue)  = false
```

Như vậy **tính bắc cầu** bị phá vỡ. Dù có sửa hash để các đối tượng trên dùng cùng hash tọa độ, điều đó cũng không cứu được tính bắc cầu. Muốn cho `Point` bằng tất cả `ColoredPoint` cùng tọa độ thì hai `ColoredPoint` khác màu tại tọa độ ấy cũng phải bằng nhau; nếu màu phân biệt giá trị logic, hãy chọn một chính sách khác cho cha/con.

## 5. Chương trình Java 17+ chạy độc lập

Chương trình dưới đây minh họa cả ba trạng thái: lỗi đối xứng, bản vá sai phá bắc cầu và một chính sách sửa nhất quán. Lưu **nguyên vẹn** vào `EqualityInheritanceReview.java`.

```java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class EqualityInheritanceReview {
    static class LoosePoint {
        private final int x;
        private final int y;

        LoosePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LoosePoint other)) return false;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class RestrictedColoredPoint extends LoosePoint {
        private final String color;

        RestrictedColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof RestrictedColoredPoint other)) {
                return false;
            }
            return super.equals(other) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    static final class BridgeColoredPoint extends LoosePoint {
        private final String color;

        BridgeColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LoosePoint)) return false;
            if (!(obj instanceof BridgeColoredPoint other)) {
                return super.equals(obj);
            }
            return super.equals(other) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            // Phù hợp với LoosePoint khi coi chúng bằng nhau.
            return super.hashCode();
        }
    }

    static class ExactPoint {
        private final int x;
        private final int y;

        ExactPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ExactPoint other = (ExactPoint) obj;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static final class ExactColoredPoint extends ExactPoint {
        private final String color;

        ExactColoredPoint(int x, int y, String color) {
            super(x, y);
            this.color = Objects.requireNonNull(color);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            ExactColoredPoint other = (ExactColoredPoint) obj;
            return super.equals(other) && color.equals(other.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    public static void main(String[] args) {
        LoosePoint point = new LoosePoint(2, 3);
        RestrictedColoredPoint restricted =
                new RestrictedColoredPoint(2, 3, "red");

        System.out.println("=== Broken symmetry ===");
        System.out.println("parent -> child: " + point.equals(restricted));
        System.out.println("child -> parent: " + restricted.equals(point));

        BridgeColoredPoint red = new BridgeColoredPoint(2, 3, "red");
        BridgeColoredPoint blue = new BridgeColoredPoint(2, 3, "blue");

        System.out.println("=== Broken transitivity ===");
        System.out.println("red -> parent: " + red.equals(point));
        System.out.println("parent -> blue: " + point.equals(blue));
        System.out.println("red -> blue: " + red.equals(blue));

        ExactPoint exact = new ExactPoint(2, 3);
        ExactColoredPoint exactRed = new ExactColoredPoint(2, 3, "red");
        ExactColoredPoint sameRed = new ExactColoredPoint(2, 3, "red");
        ExactColoredPoint exactBlue = new ExactColoredPoint(2, 3, "blue");

        System.out.println("=== Corrected policy ===");
        System.out.println("parent -> child: " + exact.equals(exactRed));
        System.out.println("child -> parent: " + exactRed.equals(exact));
        System.out.println("equal colored points: " + exactRed.equals(sameRed));
        System.out.println("different colors: " + exactRed.equals(exactBlue));
        System.out.println("equal hash: " +
                (exactRed.hashCode() == sameRed.hashCode()));

        Set<ExactPoint> set = new HashSet<>();
        set.add(exact);
        set.add(exactRed);
        set.add(sameRed);
        set.add(exactBlue);
        System.out.println("HashSet size: " + set.size());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac EqualityInheritanceReview.java
java EqualityInheritanceReview
```

Kết quả xác định:

```text
=== Broken symmetry ===
parent -> child: true
child -> parent: false
=== Broken transitivity ===
red -> parent: true
parent -> blue: true
red -> blue: false
=== Corrected policy ===
parent -> child: false
child -> parent: false
equal colored points: true
different colors: false
equal hash: true
HashSet size: 3
```

**Giải thích mã:**

- `LoosePoint.equals()` dùng `instanceof LoosePoint` nên coi mọi đối tượng con trùng tọa độ là bằng nhau.
- `RestrictedColoredPoint.equals()` chỉ nhận cùng lớp con nên hai phép gọi cha/con đưa ra kết quả trái ngược.
- `BridgeColoredPoint` chấp nhận cha bằng tọa độ và chấp nhận cùng lớp con theo tọa độ + màu: cặp `red` với `point` và `point` với `blue` bằng nhau, trong khi `red` với `blue` không bằng nhau.
- `ExactPoint` dùng `getClass()` yêu cầu cùng lớp thực tế. `ExactColoredPoint` áp dụng cùng quy tắc rồi so thêm `color`. Do đó `exact.equals(exactRed)` và chiều ngược lại **đều `false`**.
- Trong `ExactColoredPoint.equals()`, `super.equals(other)` vẫn hoạt động: cả hai đối tượng được so sánh đều có lớp thực tế `ExactColoredPoint`, vượt qua điều kiện `getClass()` trong `ExactPoint`.
- `sameRed` có nội dung bằng `exactRed` và cùng hash; `HashSet` vì vậy chứa đúng ba giá trị logic: điểm thường, điểm đỏ và điểm xanh.

Chương trình cố tình **in các vi phạm**, thay vì ném lỗi ở lần vi phạm đầu tiên, để quan sát đủ ba phần trong cùng một lượt chạy.

## 6. Ba chiến lược thiết kế hợp lệ theo nghiệp vụ

### A. Chỉ bằng nhau khi cùng lớp thực tế

Sử dụng `getClass()` ở các lớp tham gia, như cặp `ExactPoint`/`ExactColoredPoint`. Cách này hợp lý khi `Point(2,3)` là **kiểu giá trị khác** `ColoredPoint(2,3,"red")`. Tất cả trường tạo nên tính bằng nhau trong từng lớp phải được đưa vào `equals()` và `hashCode()`.

Nhược điểm cần cân nhắc: những đối tượng là subclass/proxy được framework tạo ra có thể có lớp thực tế khác đối tượng gốc. Ví dụ trong một số cơ chế ORM, phải có chính sách riêng phù hợp với proxy và identity của thực thể. Không thay tất cả `instanceof` thành `getClass()` một cách máy móc.

### B. Dùng composition hoặc final value type

Nếu màu không phải một kiểu con cần thay thế điểm thường theo hợp đồng lớp, dùng **composition**:

```java
record PointValue(int x, int y) {}

record ColoredPointValue(PointValue point, String color) {
    ColoredPointValue {
        java.util.Objects.requireNonNull(point);
        java.util.Objects.requireNonNull(color);
    }
}
```

Đây là **đoạn định nghĩa kiểu minh họa** (không phải chương trình độc lập). Trong Java 17, `record` sinh `equals()` và `hashCode()` dựa vào các component và không thể bị subclass. Hai `PointValue(2,3)` bằng nhau; hai `ColoredPointValue` bằng nhau khi cả `point` và `color` bằng nhau. `PointValue` không phải cha của `ColoredPointValue`, nên xung đột đối xứng cha/con bị loại khỏi thiết kế.

Record không bảo đảm **bất biến sâu** nếu component chứa object mutable; ở đây dùng `int`, `String`, `PointValue` nên dữ liệu phục vụ phép bằng nhau ổn định.

### C. Tất cả các lớp so sánh đúng cùng một bộ dữ liệu

Nếu yêu cầu nghiệp vụ thực sự chỉ quan tâm tọa độ, lớp con có thể không đưa `color` vào logic bằng nhau, và `hashCode()` của nó cũng phải theo tọa độ. Khi đó **hai điểm màu khác nhau cùng tọa độ vẫn bằng nhau**. Đây có thể là lựa chọn hợp lệ, nhưng phải chấp nhận hệ quả đó.

Những hệ kế thừa phức tạp đôi khi dùng một cơ chế xác nhận tương thích hai chiều như `canEqual`. Đây không phải cách sửa tự động; cần thiết kế chính sách nhất quán cho toàn hệ và kiểm thử symmetry, transitivity, hash sau khi áp dụng.

## 7. Quy trình rà soát một hệ lớp thật

1. **Lập bản đồ kế thừa.** Tìm tất cả lớp cha/con liên quan và các phương thức `equals(Object)`/`hashCode()` ghi đè. Ghi rõ trường nào lớp con mới bổ sung, có tham gia định nghĩa bằng nhau hay không.
2. **Chốt chính sách bình đẳng.** Một điểm thường và một điểm màu cùng tọa độ có cần được coi là bằng nhau không? Nếu có, màu có còn được dùng để phân biệt các điểm màu không? Câu trả lời phải tuân thủ quan hệ tương đương.
3. **Tạo ba nhóm đối tượng:** cùng tham chiếu; hai instance riêng nhưng cùng giá trị; hai instance riêng khác giá trị. Sau đó tạo cặp cha/con cùng tọa độ, các lớp con cùng/different `color`, và bộ ba điểm thường/đỏ/xanh để kiểm tra quan hệ.
4. **Kiểm tra cả hai chiều.** Với từng cặp `a, b`, tính `a.equals(b)` và `b.equals(a)`. Không chỉ thử `child.equals(parent)` rồi kết luận.
5. **Kiểm tra bộ ba.** Với mọi `a, b, c` mà `a.equals(b)` và `b.equals(c)` đúng, yêu cầu `a.equals(c)` đúng.
6. **Kiểm tra hash và collection.** Mọi cặp bằng nhau phải có hash bằng nhau; thử `HashSet`, `HashMap` và các caller phụ thuộc vào việc gộp đối tượng theo giá trị.
7. **Kiểm tra trạng thái có thể thay đổi.** Nếu key của hash collection bị thay trường dùng trong `equals()`/`hashCode()`, việc tìm lại phần tử có thể sai lệch. Ưu tiên immutable key.
8. **Bổ sung kiểm thử hồi quy.** Phủ cả các lớp cháu hoặc proxy nếu hệ thống thực tế có dùng chúng.

Bảng dữ liệu gợi ý:

| Cặp hoặc bộ ba | Điều cần kiểm tra |
| --- | --- |
| `p` và `alias = p` | Cùng identity, phản xạ |
| `new Point(2,3)`, `new Point(2,3)` | Hai instance cha bằng giá trị |
| Hai `ColoredPoint(2,3,"red")` | Hai instance con bằng giá trị và hash |
| `Point(2,3)`, `ColoredPoint(2,3,"red")` | Hai chiều cha/con |
| Điểm đỏ và điểm xanh cùng tọa độ | Thuộc tính `color` |
| Điểm đỏ, điểm thường, điểm xanh | Bắc cầu |
| Bất kỳ instance và `null` | Kết quả `equals(null)` là `false` |

## 8. Kiểm thử tự động ma trận đối xứng và bắc cầu

Thêm phần sau **vào trong `main`** của chương trình Java 17 bên trên (không phải tệp độc lập). Các đối tượng `ExactPoint` và `ExactColoredPoint` đã có sẵn ở ví dụ:

```java
Object[] samples = {
        new ExactPoint(2, 3),
        new ExactPoint(2, 3),
        new ExactColoredPoint(2, 3, "red"),
        new ExactColoredPoint(2, 3, "red"),
        new ExactColoredPoint(2, 3, "blue")
};

for (Object a : samples) {
    for (Object b : samples) {
        if (a.equals(b) != b.equals(a)) {
            throw new AssertionError("Symmetry violated");
        }
        if (a.equals(b) && a.hashCode() != b.hashCode()) {
            throw new AssertionError("Hash contract violated");
        }
    }
}

for (Object a : samples) {
    for (Object b : samples) {
        for (Object c : samples) {
            if (a.equals(b) && b.equals(c) && !a.equals(c)) {
                throw new AssertionError("Transitivity violated");
            }
        }
    }
}
```

Với mẫu **đã sửa**, không có ngoại lệ phát sinh. Khi đưa các đối tượng lỗi vào ma trận, các điều kiện tương ứng có thể tìm ra phản ví dụ. Với dự án thật, đưa phép kiểm tra này vào test JUnit 5 và tăng mẫu theo ranh giới lớp/giá trị. Đây là kiểm thử trên **bộ mẫu đã chọn**, không chứng minh tự động mọi trạng thái của mọi đối tượng.

## 9. Sai lầm phổ biến

- **Chỉ kiểm tra một chiều:** `child.equals(parent)` đúng/sai chưa nói lên `parent.equals(child)` có giống hay không.
- **Chỉ xem `instanceof` như so sánh lớp chính xác:** thực chất toán tử chấp nhận kiểu con, nên dễ làm lớp cha định nghĩa equality rộng hơn lớp con.
- **Chữa symmetry nhưng không kiểm tra transitivity:** mẫu điểm đỏ/điểm thường/điểm xanh cho thấy bản vá “bỏ qua màu khi so với cha” có thể sai.
- **Ghi đè `equals()` nhưng quên `hashCode()`:** collection dựa trên hash cần hợp đồng giữa hai phương thức.
- **Đòi hai đối tượng khác nhau phải có hash khác nhau:** collision vẫn hợp lệ.
- **Thay đổi key đang nằm trong `HashMap`/`HashSet`:** nếu trường dùng để tính hash thay đổi, tra cứu có thể không còn đúng.
- **Áp dụng một kiểu sửa cho mọi hệ:** chính sách equality phải phản ánh nghiệp vụ và có thể chịu tác động từ ORM proxy, subclass động hoặc kiểu value cụ thể.

## 10. Tự kiểm tra

**Hỏi:** Vì sao `Point.equals(ColoredPoint)` có thể đúng, nhưng `ColoredPoint.equals(Point)` sai?  
**Đáp:** Lớp cha chấp nhận mọi `instanceof Point`, còn lớp con chỉ chấp nhận `instanceof ColoredPoint`.

**Hỏi:** Nếu `red.equals(base)` và `base.equals(blue)` đúng nhưng `red.equals(blue)` sai, vi phạm điều gì?  
**Đáp:** Tính bắc cầu.

**Hỏi:** Vì sao bản sửa `getClass()` làm `ExactPoint` và `ExactColoredPoint` cùng tọa độ không bằng nhau?  
**Đáp:** Chính sách bình đẳng yêu cầu **cùng lớp thực tế** trước khi so thuộc tính.

**Hỏi:** Nếu `x.equals(y)` đúng thì hash hai đối tượng cần thế nào?  
**Đáp:** Bắt buộc bằng nhau; chiều ngược lại không bắt buộc.

**Ghi nhớ:** Khi lớp con bổ sung trạng thái tham gia `equals()`, phải rà soát **toàn bộ hệ lớp**, thử cả hai chiều so sánh lẫn bộ ba bắc cầu, chọn chính sách equality nhất quán rồi đồng bộ `hashCode()` và kiểm thử collection. Một bản sửa chỉ làm cho một cặp đối tượng có kết quả đối xứng chưa đủ để bảo đảm hợp đồng `Object`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="practical-exercises-009">Tổng hợp danh sách câu hỏi trước khi công bố một lớp có hợp đồng giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Checklist trước khi công bố một lớp có hợp đồng giá trị trong Java

## Mục tiêu: điều gì phải chắc chắn trước khi phát hành?

**Hợp đồng giá trị** (*value contract*) nói rõ khi nào hai instance biểu diễn **cùng một giá trị**, đầu vào nào hợp lệ, trạng thái có thể thay đổi ra sao và những hành vi nào caller được quyền dựa vào. Trước khi công bố một lớp kiểu giá trị cho module khác, thư viện hoặc API công khai, hãy trả lời những câu hỏi dưới đây bằng **quyết định cụ thể và phép kiểm chứng**. Đừng chỉ kiểm tra rằng lớp đã override `equals()` và `hashCode()`.

Ví dụ điển hình là `Money`, `Point`, `EmailAddress`, `DateRange` hoặc một mã định danh nghiệp vụ. Hai instance của value object có thể là hai đối tượng khác nhau trong bộ nhớ (`a != b` theo `==`) nhưng vẫn `a.equals(b) == true`. Điều này khác với *entity*, nơi tính đồng nhất có thể phụ thuộc vào một ID riêng và vòng đời tồn tại lâu dài.

**Câu hỏi mở đầu phải chốt:** Lớp này thực sự đại diện cho *một giá trị* hay *một thực thể có danh tính riêng*? Nếu không thể nói rõ quy tắc hai instance được coi là cùng giá trị, chưa nên công bố hợp đồng equality.

## 1. Danh sách câu hỏi theo giai đoạn

### A. Xác định ý nghĩa và phạm vi của giá trị

- [ ] **Đối tượng đại diện cho khái niệm nào?** Viết được một câu rõ ràng, ví dụ: “Một số tiền không âm với mã tiền tệ, có độ chính xác hai chữ số thập phân”.
- [ ] **Những thuộc tính nào tạo nên giá trị?** Liệt kê trường quyết định equality; trường nào chỉ là dữ liệu hiển thị, mô tả, cache, metadata hoặc dữ liệu suy ra?
- [ ] **Hai instance có cùng dữ liệu nhưng được tạo tại hai thời điểm khác nhau có bằng nhau không?** Nếu có, `==` không phải tiêu chí; hãy xác định value equality.
- [ ] **Có trường nào mang danh tính ổn định của entity không?** Nếu có, xem lại việc coi lớp là value object; đừng vô tình gộp hai entity khác nhau vì chúng tình cờ có cùng thuộc tính.
- [ ] **Các giới hạn miền dữ liệu đã được mô tả chưa?** Chẳng hạn đơn giá âm có hợp lệ, khoảng ngày đảo ngược có hợp lệ, mã tiền tệ cho phép tập ký tự nào?
- [ ] **Có những biểu diễn đầu vào khác nhau nhưng tương đương không?** Ví dụ mã `" usd "` và `"USD"`, hay `BigDecimal("10.0")` và `BigDecimal("10.00")`.
- [ ] **Lớp cần triển khai `Comparable` không?** Nếu có, có ý nghĩa thứ tự tổng quát không, và `compareTo(x) == 0` có nhất quán với `equals(x)` không?

**Quyết định cần lưu:** bảng liệt kê mọi thành phần, quy tắc hợp lệ, cách chuẩn hóa và việc có tham gia `equals()`/`hashCode()` hay không. Một quy tắc chưa thống nhất phải được giải quyết trước khi thiết kế API công khai.

### B. Chính sách `null` và bất biến (*invariant*)

- [ ] **Trường nào cấm `null`?** Trường bắt buộc cần được kiểm tra ngay tại constructor/factory, ví dụ `Objects.requireNonNull(name, "name")`.
- [ ] **Trường tùy chọn phân biệt `null`, chuỗi rỗng và giá trị vắng mặt không?** Chỉ gộp các trạng thái này khi nghiệp vụ thật sự cho phép.
- [ ] **Có điều kiện hợp lệ kết hợp giữa nhiều trường không?** Ví dụ `start <= end`, hoặc hai tọa độ phải sử dụng cùng hệ quy chiếu.
- [ ] **Khi đầu vào không hợp lệ, phương thức làm gì?** Công bố loại ngoại lệ, thông điệp hỗ trợ chẩn đoán và liệu có tự chuẩn hóa hay từ chối.
- [ ] **Mọi đường tạo đối tượng đều kiểm tra invariant chưa?** Xem constructor, static factory, builder, parser, JSON mapper, ORM, deserialization và các phương thức tạo bản sao.
- [ ] **Getter có thể trả `null` bất ngờ không?** Phần tài liệu và type contract cần khớp với hành vi thực tế.

Không dùng các sentinel như `0`, `"N/A"`, ngày giả hoặc chuỗi trống để biểu diễn thiếu dữ liệu nếu chúng có thể là giá trị hợp lệ. Quy tắc `null` phải nhất quán giữa đầu vào, trạng thái lưu trữ, equality, `toString()` và API trả về.

### C. Chuẩn hóa và biểu diễn chuẩn

- [ ] **Đã xác định phép chuẩn hóa cụ thể, thứ tự áp dụng và locale chưa?** Ví dụ trim đầu/cuối, chuẩn hóa Unicode, đổi chữ hoa/thường theo `Locale.ROOT`, quy đổi đơn vị.
- [ ] **Chuẩn hóa có làm mất thông tin không?** Chuyển hoa/thường, bỏ dấu hoặc nén khoảng trắng có thể gây trùng tên/ID vốn khác nghĩa; chỉ làm khi đặc tả cho phép.
- [ ] **Phép chuẩn hóa có ổn định và idempotent không?** Một giá trị hợp lệ sau chuẩn hóa lần hai phải giữ nguyên.
- [ ] **Có kiểm tra ký tự hợp lệ trước/sau chuẩn hóa không?** Tránh “sửa” mã sai định dạng thành mã khác một cách âm thầm.
- [ ] **Các giá trị số cần chính xác đến đâu?** Không dùng `double` cho tiền theo mặc định; với `BigDecimal`, đã quyết định rõ `scale`, làm tròn và quan hệ giữa `equals()` với `compareTo()` chưa?
- [ ] **Thời điểm hoặc đơn vị đo có cách biểu diễn thống nhất không?** Nêu rõ vùng giờ/UTC, đơn vị gốc, độ chính xác và giới hạn chuyển đổi.

**Lưu ý về `BigDecimal`:** `new BigDecimal("10.0").compareTo(new BigDecimal("10.00")) == 0`, nhưng `equals()` là `false` vì `scale` khác. Nếu trường tham gia equality của record hoặc class, hãy chuẩn hóa thang đo hoặc thiết kế một phép so sánh và hash tương thích có chủ ý. Không được chỉ sửa `equals()` mà bỏ qua `hashCode()`.

### D. `equals()`, `hashCode()` và thứ tự so sánh

- [ ] **`equals()` có phản xạ không?** Với mọi giá trị hợp lệ `x`, `x.equals(x)` là `true`.
- [ ] **Có đối xứng không?** `x.equals(y)` và `y.equals(x)` phải giống nhau, kể cả khi tham gia phân lớp hoặc proxy.
- [ ] **Có bắc cầu không?** Nếu `x.equals(y)` và `y.equals(z)`, phải có `x.equals(z)`.
- [ ] **Có nhất quán khi trạng thái liên quan không đổi không?** Kết quả không được phụ thuộc cache không ổn định, thời gian hiện tại hoặc trạng thái ngoài bất ngờ.
- [ ] **`x.equals(null)` luôn `false` không?**
- [ ] **Hai instance bằng nhau có hash bằng nhau không?** `x.equals(y) => x.hashCode() == y.hashCode()`; chiều ngược lại không được bảo đảm vì có collision.
- [ ] **Tất cả và chỉ những trường được chọn cho equality đã được dùng nhất quán chưa?** Tránh trường cache, timestamp ghi log, trạng thái mutable hoặc trường suy ra không thuộc ngữ nghĩa giá trị.
- [ ] **Có subclass nào phá tính đối xứng/bắc cầu không?** Nếu không thiết kế đầy đủ equality cho hệ phân cấp, cân nhắc `final` hoặc `record` (record mặc định là `final`).
- [ ] **Nếu triển khai `Comparable`, comparator hoặc dùng `TreeSet`/`TreeMap`, quan hệ thứ tự có được mô tả không?** `compareTo() == 0` có thể khác `equals()` nhưng sẽ khiến collection sắp xếp vận hành khác collection dùng hash; phải biết trước tác động.

Với `record`, các phương thức `equals()` và `hashCode()` được tạo từ **toàn bộ thành phần**. Hãy cân nhắc thành phần trước khi công bố record: thêm một thành phần thường ảnh hưởng constructor, pattern matching, biểu diễn chuỗi và định nghĩa value equality. Nếu giá trị phải bỏ qua một số metadata, có thể cần thiết kế class hoặc override rõ ràng thay cho equality mặc định.

### E. Bất biến, sao chép phòng thủ và dữ liệu suy ra

- [ ] **Đối tượng có thực sự bất biến sau khi tạo không?** `final` trên field tham chiếu không làm `List`, mảng, `Date` hoặc các object con trở thành bất biến.
- [ ] **Constructor có sao chép dữ liệu mutable do caller truyền vào không?** Dùng bản sao phòng thủ, chẳng hạn `List.copyOf(...)` khi phù hợp (lưu ý `List.copyOf` không chấp nhận phần tử `null` và chỉ sao chép cấu trúc, không sao chép sâu phần tử).
- [ ] **Accessor có lộ tham chiếu mutable nội bộ không?** Trả bản sao hoặc view bất biến phù hợp; giữ chính sách nhất quán.
- [ ] **Giá trị suy ra có nguồn chân lý rõ ràng không?** Ví dụ `total = unitPrice * quantity`; tránh lưu và cho sửa độc lập cả nguồn lẫn kết quả.
- [ ] **Nếu có cache, cache được vô hiệu hóa khi nào và có tham gia equality/hash không?** Giá trị cache thường phải bị loại khỏi equality; nếu lazy cache dùng ở nhiều thread, phải có chính sách đồng thời.
- [ ] **Có thể dùng object an toàn làm khóa `HashMap` hoặc phần tử `HashSet` không?** Các thành phần tham gia hash nên bất biến suốt thời gian đối tượng ở trong collection.

**Kiểm tra aliasing:** tạo value object từ collection/mảng, sửa dữ liệu gốc sau khi xây dựng, rồi kiểm tra `equals()`, `hashCode()` và getter của value object không đổi. Lặp lại phép kiểm sau khi lấy tham chiếu từ getter và thử sửa nó.

### F. API công khai, lỗi và khả năng tương thích

- [ ] **Tên kiểu, constructor, factory và phương thức đã thể hiện đúng bất biến chưa?** Chẳng hạn `Money.of(...)`, `plus(...)`, `amount()`; tránh getter hoặc setter làm caller tưởng có thể sửa trường bất biến.
- [ ] **Tính công khai của constructor và helper có chủ ý không?** Cân nhắc validation không thể bị vượt qua qua constructor phụ hay builder.
- [ ] **`toString()` có phù hợp để log không?** Record có `toString()` mặc định in toàn bộ component, có thể vô tình tiết lộ thông tin nhạy cảm.
- [ ] **Có cần biểu diễn JSON, database, text hoặc binary ổn định không?** Chuẩn hóa, schema, tên trường, định dạng số và khả năng nạp lại cần tương thích với hợp đồng.
- [ ] **Các ngoại lệ khi parse, normalize và thao tác số có được công bố không?** Caller phải biết khi nào cần xử lý lỗi.
- [ ] **Nếu công bố thành API thư viện, chính sách phiên bản đã được xác định chưa?** Đổi quy tắc `equals()`, chuẩn hóa, `hashCode()` hoặc constructor có thể gây khác biệt hành vi dù chữ ký Java không đổi.
- [ ] **Tài liệu có ví dụ phân biệt hai instance khác nhau nhưng bằng giá trị không?** Nhắc caller không dùng `==` để so sánh hai value object độc lập.

Không dựa vào `System.identityHashCode()` để xác định value equality, cũng không dùng `toString()` hoặc giá trị `hashCode()` làm ID duy nhất. `hashCode()` phục vụ tổ chức bucket của collection băm và cho phép collision; giá trị băm có thể thay đổi theo implementation/phiên bản và không phải định danh bền vững.

## 2. Ví dụ kiểm tra trước khi công bố: value object `Money`

Chính sách dưới đây được chọn **cho ví dụ**, không áp đặt cho mọi hệ thống tài chính:

1. Tiền gồm mã đơn vị và số tiền không âm. Đây là **value object**, không phải tài khoản hay giao dịch có ID.
2. Mã đơn vị nhận đúng ba chữ cái **ASCII**, không phân biệt kiểu chữ, cắt khoảng trắng hai đầu; chỉ các ký tự ASCII hợp lệ được nhận. Ví dụ dùng quy tắc cú pháp ba ký tự, chưa xác thực mã ấy có trong danh sách ISO 4217 hay không.
3. `amount` bắt buộc, không âm, có tối đa hai chữ số sau dấu thập phân; chuẩn hóa về `scale = 2` **mà không làm tròn âm thầm**.
4. `record` lưu chỉ hai thành phần chuẩn hóa; `equals()` và `hashCode()` mặc định cùng xét cả hai. Không có setter, cache hay trường suy ra.
5. `plus()` yêu cầu cùng mã đơn vị, trả về một value object mới; không tự quy đổi tỷ giá.

Đặt chương trình Java 17+ vào tệp `ValueContractReview.java`:

```java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class ValueContractReview {
    public record Money(String currency, BigDecimal amount) {
        public Money {
            currency = Objects.requireNonNull(currency, "currency").strip();
            if (!currency.matches("[A-Za-z]{3}")) {
                throw new IllegalArgumentException("currency must be three ASCII letters");
            }
            currency = currency.toUpperCase(Locale.ROOT);

            amount = Objects.requireNonNull(amount, "amount");
            try {
                amount = amount.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("amount requires rounding", ex);
            }
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("amount must be nonnegative");
            }
        }

        public Money plus(Money other) {
            Objects.requireNonNull(other, "other");
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("different currencies");
            }
            return new Money(currency, amount.add(other.amount));
        }
    }

    public static void main(String[] args) {
        Money first = new Money(" usd ", new BigDecimal("10.0"));
        Money second = new Money("USD", new BigDecimal("10.00"));
        Money euro = new Money("EUR", new BigDecimal("10.00"));

        System.out.println("same reference: " + (first == second));
        System.out.println("same value: " + first.equals(second));
        System.out.println("same hash: " + (first.hashCode() == second.hashCode()));
        System.out.println("different currency: " + first.equals(euro));
        System.out.println("canonical code: " + first.currency());
        System.out.println("canonical amount: " + first.amount().toPlainString());
        System.out.println("sum: "
                + first.plus(new Money("usd", new BigDecimal("2.50")))
                       .amount().toPlainString());

        Set<Money> values = new HashSet<>();
        values.add(first);
        values.add(second);
        System.out.println("set size: " + values.size());

        try {
            new Money("usd", new BigDecimal("1.234"));
        } catch (IllegalArgumentException ex) {
            System.out.println("fractional precision rejected");
        }
        try {
            first.plus(euro);
        } catch (IllegalArgumentException ex) {
            System.out.println("cross-currency addition rejected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ValueContractReview.java
java ValueContractReview
```

Đầu ra xác định:

```text
same reference: false
same value: true
same hash: true
different currency: false
canonical code: USD
canonical amount: 10.00
sum: 12.50
set size: 1
fractional precision rejected
cross-currency addition rejected
```

**Phân tích:** `first` và `second` được tạo bằng `new` hai lần nên khác tham chiếu, nhưng các thành phần record sau chuẩn hóa bằng nhau. `HashSet` giữ một phần tử vì `equals()` và `hashCode()` nhất quán. `amount` là `BigDecimal` bất biến, giúp record ở ví dụ không lộ trạng thái mutable. Mọi thao tác tạo `Money` đi qua constructor có invariant; phép cộng trả về instance mới. Dữ liệu `"1.234"` bị từ chối vì không thể đặt `scale = 2` mà không làm tròn.

**Giới hạn có chủ ý:** Ví dụ chưa kiểm tra mã tiền tệ là mã ISO thật, không quy đổi tỷ giá, không định nghĩa chia hoặc nhân với số thực, và dùng đúng hai chữ số thập phân cho mọi mã. Hệ thống thực phải điều chỉnh các quy tắc đó theo loại tiền tệ và bài toán. Ví dụ cũng không dùng `Money` làm entity hoặc thay thế thiết kế nghiệp vụ giao dịch.

## 3. Ma trận kiểm thử tối thiểu để duyệt hợp đồng

Chuyển các câu hỏi thành kiểm thử độc lập trên các giá trị hợp lệ và không hợp lệ. Bảng dưới đây nêu kết quả mong đợi; không kiểm tra bằng cách so sánh chuỗi `toString()` trừ khi đó chính là hợp đồng đầu ra.

| Nhóm kiểm tra | Dữ liệu/phép thử | Kết quả cần xác nhận |
| --- | --- | --- |
| Phản xạ | `x.equals(x)` | `true`. |
| Đối xứng | `x.equals(y)` và `y.equals(x)` | Cùng kết quả. |
| Bắc cầu | `x ==value y`, `y ==value z` | `x.equals(z) == true`. |
| `null` trong `equals()` | `x.equals(null)` | `false`. |
| Khác kiểu | `x.equals("other")` | `false`, không ném ngoại lệ. |
| Tương đương qua chuẩn hóa | `" usd ", 10.0` và `"USD", 10.00` | `equals == true`, hash bằng nhau. |
| Cùng hash chưa chắc cùng giá trị | Hai object bất kỳ bị collision | `equals()` vẫn quyết định theo giá trị; không coi hash là ID. |
| Khác thành phần equality | `USD 10.00` và `EUR 10.00` | `equals == false`. |
| Collection băm | Thêm hai giá trị tương đương vào `HashSet` | Kích thước 1. |
| `null` bắt buộc | `currency = null`, `amount = null` | Lỗi sớm và nhất quán (`NullPointerException` trong ví dụ). |
| Mã sai | Mã rỗng, `"US1"`, ký tự ngoài ASCII | `IllegalArgumentException`. |
| Số không hợp lệ | `amount < 0` hoặc cần làm tròn | `IllegalArgumentException`. |
| Tính idempotent | Tạo lại từ `x.currency()` và `x.amount()` | Giá trị chuẩn không đổi. |
| Bất biến | Đọc field và thực hiện `plus()` | `x` không đổi; kết quả là instance mới. |
| Sai ngữ nghĩa phép toán | Cộng hai mã đơn vị khác nhau | Bị từ chối, không cộng số học thuần túy. |
| Chuyển đổi/tuần tự hóa | Serialize rồi deserialize (nếu hỗ trợ) | Invariant và equality còn đúng; mã hóa có phiên bản rõ ràng. |

Với lớp có chứa tham chiếu mutable, bổ sung kiểm thử **sao chép phòng thủ** trước và sau khi khởi tạo. Với lớp tự viết `equals()`/`hashCode()`, kiểm thử tình huống subclass và proxy nếu thiết kế cho phép kế thừa. Với `Comparable`, bổ sung tính phản đối xứng dấu, bắc cầu của thứ tự và tính nhất quán với `equals()` theo hợp đồng đã chọn.

## 4. Các câu hỏi bắt buộc trong code review

Trước khi merge hoặc phát hành API, reviewer nên yêu cầu tác giả chỉ ra ngay trong mã hoặc tài liệu:

1. **“Khi nào hai instance bằng nhau?”** Cho ba ví dụ: bằng nhau sau chuẩn hóa, khác nhau do một trường giá trị, và khác nhau về tham chiếu nhưng bằng nhau về giá trị.
2. **“Mọi đường tạo có duy trì invariant không?”** Chỉ vào constructor/factory và kiểm thử đầu vào biên; xem parser, builder, deserializer.
3. **“Có trạng thái nào có thể làm hash thay đổi khi object đang là key không?”** Nếu có, yêu cầu bất biến hoặc xem lại thiết kế equality.
4. **“Những gì được giữ nguyên và những gì bị chuẩn hóa?”** Kiểm tra khả năng mất thông tin, Unicode, locale, `BigDecimal.scale`, vùng giờ, `null`/rỗng.
5. **“Có vô tình đưa metadata hoặc cache vào equality không?”** Nếu là record, kiểm tra kỹ mọi component.
6. **“Khi giá trị không hợp lệ, API báo gì?”** Ngoại lệ phải rõ và caller có thể dự đoán.
7. **“Có getter nào lộ cấu trúc mutable không?”** Thử sửa collection/mảng ban đầu và đối tượng trả về.
8. **“Thay đổi sau phát hành ảnh hưởng những consumer nào?”** Bao gồm `HashSet`/`HashMap`, cache key, database unique key, JSON schema và phương thức so sánh.

### Ví dụ nhận diện vấn đề trước–sau

**Thiết kế có vấn đề (mã minh họa, không phải chương trình độc lập):**

```java
// amount và currency là trường có thể thay đổi và đã tham gia equals/hashCode.
// Nếu instance đang nằm trong HashSet, setter có thể làm việc tra cứu thất bại.
money.setCurrency("EUR");

// Nếu đồng thời lưu total như một trường cho phép sửa riêng,
// total có thể không còn khớp amount * quantity.
```

**Thiết kế chặt chẽ hơn:** factory/constructor validate mọi dữ liệu đầu vào, chuẩn hóa một lần, lưu trạng thái bất biến và trả instance mới khi thay đổi giá trị. Các giá trị suy ra được tính từ thành phần gốc hoặc cache được quản lý rõ ràng. Lời hứa của API phải khớp với những invariant mã thực sự duy trì.

## 5. Điều kiện chấp nhận phát hành

Đánh dấu **sẵn sàng công bố** khi đồng thời đáp ứng bốn nhóm kết quả sau:

- **Có bản hợp đồng đọc được:** mô tả loại giá trị, trường tham gia equality, quy tắc `null`, chuẩn hóa, độ chính xác, ngoại lệ, bất biến và hành vi liên quan.
- **Có implementation tương ứng:** mọi đường tạo giữ invariant; `equals()`/`hashCode()` đúng hợp đồng; nội bộ không lộ mutable state; các phép toán trả giá trị hợp lệ.
- **Có bằng chứng kiểm chứng:** kiểm thử các quy luật equality/hash, biên dữ liệu, đường tạo/nạp, collection, quan hệ thứ tự nếu có và tính bất biến.
- **Có đánh giá tương thích:** kiểm tra tác động tới API công khai, consumer, serialization, database/caching và quy tắc versioning khi thay đổi.

Nếu bất kỳ câu hỏi cốt lõi nào còn chưa trả lời (đặc biệt là **giá trị nào được xem bằng nhau, đầu vào nào hợp lệ và trạng thái nào có thể đổi**), hãy coi đó là vấn đề thiết kế cần giải quyết trước phát hành, thay vì chữa bằng một `hashCode()` ngẫu nhiên hoặc một lớp kiểm tra ở phía caller.

## 6. Mẫu ghi hợp đồng ngắn kèm lớp

```text
Type: Money (value object, immutable)
Components: currency (ASCII 3 letters, uppercase), amount (>= 0, scale 2)
Null policy: both required; null rejected
Canonicalization: strip currency, validate ASCII, uppercase Locale.ROOT;
                  amount.setScale(2, UNNECESSARY)
Equality/hash: both canonical components, record defaults
Ordering: no Comparable; different currencies have no implicit order
Derived values: none stored; plus() returns new Money
Failure policy: invalid state or mixed currencies -> exception
Compatibility: changes in equality, scale or normalization need review
```

**Ghi nhớ:** Hợp đồng giá trị đáng tin là tập hợp nhất quán giữa **ngữ nghĩa nghiệp vụ, biểu diễn chuẩn, invariant, equality/hash, bất biến, API và kiểm thử**. Checklist chỉ hoàn thành khi mọi câu trả lời đã được chuyển thành hành vi có thể quan sát và kiểm chứng trong mã.

## Tài liệu tham khảo

- [Java SE 17 — `Object.equals()` / `hashCode()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html)
- [Java SE 17 — `Record` và đặc tả record](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10)
- [Java SE 17 — `BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html)
- [Java SE 17 — `Set`, `HashSet`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashSet.html)


</details>

- [Quay lại đầu trang](#back-to-top)
