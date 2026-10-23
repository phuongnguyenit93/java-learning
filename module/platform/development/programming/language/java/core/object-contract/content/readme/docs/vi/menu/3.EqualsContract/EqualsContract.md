<a id="back-to-top"></a>

# Hợp đồng equals

## Menu
- [equals() định nghĩa quan hệ tương đương trên tham chiếu khác null](#equals-contract-001)
- [Tính phản xạ: đối tượng bằng chính nó](#equals-contract-002)
- [Tính đối xứng khi đổi vị trí hai đối tượng](#equals-contract-003)
- [Tính bắc cầu giữa ba đối tượng](#equals-contract-004)
- [Tính nhất quán khi dữ liệu dùng để so sánh không thay đổi](#equals-contract-005)
- [So sánh với null phải trả về false](#equals-contract-006)
- [Phân biệt x.equals(null) với gọi equals() trên tham chiếu null](#equals-contract-007)
- [Chia các đối tượng thành những nhóm có cùng giá trị theo hợp đồng](#equals-contract-008)

## <a id="equals-contract-001">equals() định nghĩa quan hệ tương đương trên tham chiếu khác null</a>

<details>
<summary>Xem chi tiết</summary>

# `equals()` định nghĩa quan hệ tương đương trên các tham chiếu khác `null`

## 1. Mục tiêu và phạm vi

Trong Java, `Object.equals(Object)` là phương thức để một đối tượng trả lời câu hỏi: “Đối tượng này có được xem là bằng đối tượng kia theo quy tắc của kiểu dữ liệu hay không?”. Triển khai mặc định trong `Object` chỉ coi hai tham chiếu bằng nhau khi chúng cùng trỏ đến **một đối tượng**. Lớp có ngữ nghĩa giá trị thường ghi đè `equals` để hai đối tượng khác nhau vẫn bằng nhau khi các thuộc tính có ý nghĩa tương ứng bằng nhau.

Xét tập `D` gồm các tham chiếu đối tượng Java **khác `null`**. Định nghĩa quan hệ `~` trên `D`:

- `x ~ y` khi và chỉ khi `x.equals(y)` trả về `true`.
- Để `~` thực sự là **quan hệ tương đương**, `equals` phải có tính **phản xạ, đối xứng, bắc cầu**.
- Hợp đồng của `Object.equals` còn yêu cầu **nhất quán** trong điều kiện trạng thái liên quan không đổi và `x.equals(null)` luôn là `false` đối với `x != null`.

Cần phân biệt: các tiên đề của *quan hệ tương đương* áp dụng cho các phần tử **khác `null`** trong `D`; hai yêu cầu “nhất quán” và “khác `null`” là phần bổ sung của **hợp đồng phương thức `equals`**, không phải hai tiên đề toán học của quan hệ tương đương.

## 2. Năm điều kiện của hợp đồng `equals`

Với các tham chiếu `x`, `y`, `z` khác `null`:

| Điều kiện | Biểu thức / phát biểu | Ý nghĩa |
| --- | --- | --- |
| **Phản xạ (reflexive)** | `x.equals(x) == true` | Một đối tượng luôn bằng chính nó. |
| **Đối xứng (symmetric)** | `x.equals(y)` và `y.equals(x)` phải cho cùng kết quả | Thứ tự hai đối tượng không được quyết định kết quả bằng nhau. |
| **Bắc cầu (transitive)** | Nếu `x.equals(y)` và `y.equals(z)` thì `x.equals(z)` | Ba đối tượng không thể tạo thành chuỗi “bằng, bằng, nhưng không bằng”. |
| **Nhất quán (consistent)** | Gọi nhiều lần cho cùng cặp đối tượng phải có cùng kết quả, nếu thông tin dùng bởi `equals` không thay đổi | Kết quả không được phụ thuộc ngẫu nhiên vào lần gọi, thời gian hay trạng thái ngoại lai không thuộc định nghĩa bằng nhau. |
| **Khác `null` (non-null)** | `x.equals(null) == false` | Một tham chiếu tới đối tượng thực không bằng `null`. |

### Phản xạ

`x.equals(x)` phải đúng ngay cả khi `x` là đối tượng mới tạo hoặc thuộc lớp có logic so sánh phức tạp. Một cách triển khai thường gặp là kiểm tra `this == other` trước; nhưng đây chỉ là kỹ thuật tối ưu, không thay thế việc thiết kế toàn bộ quan hệ bằng nhau.

### Đối xứng

Nếu `a.equals(b)` là `true` thì `b.equals(a)` cũng phải là `true`. Lỗi thường xuất hiện khi một lớp chấp nhận so sánh với kiểu khác nhưng kiểu đó không có quy tắc tương ứng, hoặc khi lớp con thay đổi tiêu chí so sánh của lớp cha.

Ví dụ về **thiết kế sai**: một lớp `FlexibleText` cho `equals` chấp nhận một `String` có chữ hoa/thường khác nhau. Nếu `FlexibleText("java").equals("JAVA")` trả về `true`, thì `"JAVA".equals(FlexibleText("java"))` vẫn trả về `false` bởi `String.equals` chỉ chấp nhận `String`. Hai hướng không đối xứng. Giải pháp là định nghĩa cùng miền kiểu cho cả hai phía hoặc dùng một API so sánh riêng (ví dụ `equalsIgnoreCase`) nếu muốn quy tắc khác.

### Bắc cầu

Giả sử một thiết kế so sánh hai số nguyên là bằng nhau khi khoảng cách của chúng không quá 1. Khi đó `1 ~ 2` đúng, `2 ~ 3` đúng, nhưng `1 ~ 3` sai. Quy tắc “gần bằng” này **không phù hợp để triển khai `equals`**. Nó có thể là một phép kiểm tra nghiệp vụ với tên riêng như `isNearTo`, nhưng không thể thay thế quan hệ bằng nhau có tính bắc cầu.

### Nhất quán và `null`

`equals` cần dựa trên những dữ liệu xác định ngữ nghĩa đối tượng. Nếu các dữ liệu ấy không đổi thì kết quả không được tự đổi giữa các lần gọi. Khi dữ liệu dùng để so sánh **thực sự thay đổi**, kết quả `equals` được phép thay đổi; điều này vẫn dễ gây rủi ro khi đối tượng đã được dùng làm khóa trong cấu trúc băm.

`x.equals(null)` phải trả về `false`. Ngược lại, `null.equals(x)` **không thể gọi được** và sẽ gây `NullPointerException`. Hãy phân biệt hai biểu thức đó thay vì diễn đạt chung rằng “`null` tự so sánh được”.

## 3. Phân biệt `==`, `equals` và `Objects.equals`

- Với **tham chiếu đối tượng**, `a == b` kiểm tra **đồng nhất tham chiếu**: cả hai cùng trỏ đến một đối tượng (hoặc đều là `null`).
- `a.equals(b)` áp dụng quy tắc **bằng nhau do lớp `a` định nghĩa**; nó yêu cầu `a` khác `null`. Nếu lớp không ghi đè `Object.equals`, hành vi mặc định là so sánh đồng nhất như `==`.
- `Objects.equals(a, b)` kiểm tra an toàn khi có `null`: nếu cả hai `null` thì trả về `true`; nếu chỉ một bên `null` thì `false`; nếu `a` khác `null` thì ủy quyền tới `a.equals(b)`.

`Objects.equals(null, null) == true` không mâu thuẫn với `x.equals(null) == false`: biểu thức đầu là tiện ích so sánh hai tham chiếu có thể `null`, biểu thức sau là hợp đồng của phương thức trên **một đối tượng có thật**.

Các phương thức hoặc miền giá trị khác nhau có thể định nghĩa khái niệm bằng nhau khác nhau. Khi thảo luận hợp đồng ở bài này, luôn xét kết quả của **`equals(Object)`**, không thay bằng `==` hay một phương thức gần giống tên.

## 4. Ví dụ Java 17+ đúng hợp đồng

Thiết kế `Coordinate` là một **lớp giá trị bất biến** với hai thành phần số nguyên `x`, `y`. Hai tọa độ bằng nhau khi cả `x` lẫn `y` bằng nhau. Khai báo `final` làm rõ rằng lớp không có lớp con bổ sung trạng thái so sánh.

Lưu toàn bộ chương trình sau trong tệp `EqualsContractDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class EqualsContractDemo {
    static final class Coordinate {
        private final int x;
        private final int y;

        Coordinate(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Coordinate that)) {
                return false;
            }
            return x == that.x && y == that.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    public static void main(String[] args) {
        Coordinate a = new Coordinate(2, 5);
        Coordinate b = new Coordinate(2, 5);
        Coordinate c = new Coordinate(2, 5);
        Coordinate different = new Coordinate(2, 6);

        System.out.println("a == b: " + (a == b));
        System.out.println("a.equals(b): " + a.equals(b));
        System.out.println("reflexive: " + a.equals(a));
        System.out.println("symmetric: " + (a.equals(b) == b.equals(a)));
        System.out.println("transitive: "
                + (!a.equals(b) || !b.equals(c) || a.equals(c)));
        System.out.println("null: " + a.equals(null));
        System.out.println("different: " + a.equals(different));
        System.out.println("same hash: " + (a.hashCode() == b.hashCode()));

        Set<Coordinate> coordinates = new HashSet<>();
        coordinates.add(a);
        System.out.println("set contains b: " + coordinates.contains(b));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 EqualsContractDemo.java
java EqualsContractDemo
```

Kết quả chuẩn:

```text
a == b: false
a.equals(b): true
reflexive: true
symmetric: true
transitive: true
null: false
different: false
same hash: true
set contains b: true
```

Giải thích các bước:

1. `a` và `b` được tạo bằng hai lệnh `new` riêng, nên `a == b` là `false`; chúng có cùng cặp tọa độ nên `a.equals(b)` là `true`.
2. Kiểm tra `this == other` xử lý nhanh trường hợp chính nó và bảo đảm phản xạ; mẫu `instanceof Coordinate that` (pattern matching có trong Java 17) trả về `false` khi `other` là `null` hoặc khác kiểu.
3. So sánh hai trường `int` bằng `==` là thích hợp vì ở đây chúng là các **giá trị nguyên thủy**. Định nghĩa bằng nhau không dựa vào địa chỉ đối tượng.
4. Biểu thức “transitive” trong chương trình kiểm tra một bộ ba cụ thể: nếu hai tiền đề đúng thì kết luận phải đúng. Một ví dụ trả về `true` **không chứng minh** mọi đầu vào đều thỏa hợp đồng; cần kiểm tra thêm bằng test có hệ thống.
5. `HashSet.contains(b)` thành công dù chỉ thêm `a`, bởi `a` và `b` được coi là cùng giá trị, đồng thời mã băm tương thích.

## 5. Hợp đồng giữa `equals` và `hashCode`

Khi ghi đè `equals`, gần như luôn phải ghi đè `hashCode` cùng lúc. Hợp đồng quy định:

- Nếu `a.equals(b)` là `true`, **bắt buộc** `a.hashCode() == b.hashCode()`.
- Nếu `a.equals(b)` là `false`, mã băm **có thể giống hoặc khác nhau**; va chạm hash là hợp lệ.
- Trong một lần chạy, các lần gọi `hashCode` trên cùng đối tượng phải nhất quán khi dữ liệu dùng trong `equals` không thay đổi.

Ví dụ tọa độ sử dụng `31 * x + y`. Đây là cách phối hợp đơn giản cho hai trường `int`; phép tràn số nguyên Java vẫn được xác định, không tự làm vi phạm hợp đồng. Lưu ý hai tọa độ khác nhau **có thể** tạo cùng hash, điều đó không khiến chúng bằng nhau. Có thể chọn `Objects.hash(x, y)` khi ưu tiên cách viết tổng quát, chấp nhận chút chi phí bổ sung.

`HashMap`, `HashSet` thường tìm ứng viên trong nhóm theo `hashCode` rồi dùng `equals` để quyết định bằng nhau. Vi phạm chiều “bằng nhau ⇒ cùng hash” có thể làm thao tác `contains`, `get` hoặc phát hiện khóa trùng bị sai. Dù `equals` đúng, thay đổi dữ liệu ảnh hưởng tới hash của một khóa **sau khi đã đưa vào** cấu trúc băm cũng có thể khiến việc tra cứu không còn tìm thấy nó. Vì vậy, ưu tiên khóa **bất biến**.

## 6. Chọn các thuộc tính và miền kiểu để so sánh

Một triển khai tốt trả lời trước hai câu hỏi: **điều gì xác định danh tính giá trị** và **những kiểu nào được phép xem là bằng nhau**?

Ví dụ một mã sách có trường `isbn` và mô tả hiển thị `displayTitle`: nếu nghiệp vụ xác định sách bằng ISBN, hai đối tượng có cùng ISBN vẫn có thể bằng nhau dù tiêu đề hiển thị khác. Ngược lại, tọa độ `(x, y)` phải dùng cả hai thành phần. Cần chọn tiêu chí nhất quán với ý nghĩa dữ liệu, không tự động đưa mọi trường vào so sánh.

### Kế thừa và vấn đề đối xứng/bắc cầu

Nếu `Point` so sánh `x, y`, còn `ColorPoint extends Point` muốn so sánh thêm `color`, rất dễ có hai kết quả trái ngược: `Point.equals(ColorPoint)` chấp nhận do cùng tọa độ, nhưng `ColorPoint.equals(Point)` từ chối vì không có màu. Nếu cố cho phép mọi hướng, có thể lại phá bắc cầu giữa hai điểm có màu khác nhau và một điểm không màu.

Các phương án tùy ngữ nghĩa:

- Dùng lớp giá trị `final` khi thiết kế không cần kế thừa: đơn giản nhất để giữ một quy tắc `equals` ổn định.
- Dùng `getClass() == other.getClass()` khi muốn **chỉ bằng nhau trong cùng lớp runtime**, áp dụng nhất quán trong thứ bậc lớp; điều này cố ý không xem lớp cha và lớp con bằng nhau.
- Nếu thực sự cần bình đẳng xuyên các lớp trong quan hệ kế thừa, phải thiết kế quy tắc chung cho mọi phía và kiểm chứng đủ các tính chất. Đừng chỉ đổi `instanceof` thành `getClass` một cách máy móc.

Trong ví dụ `Coordinate` là `final` nên mẫu `instanceof Coordinate` vừa ngắn gọn vừa xác định rõ tập đối tượng có thể bằng nhau.

### Những trường hợp cần chú ý

- **Mảng Java:** hai mảng riêng có cùng phần tử vẫn không bằng nhau theo `array1.equals(array2)` (thừa hưởng so sánh tham chiếu). Dùng `Arrays.equals` cho mảng một chiều, hoặc `Arrays.deepEquals` cho mảng chứa mảng cần so sánh sâu.
- **`BigDecimal`:** `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` là `false` vì `equals` còn xét scale; trong khi `compareTo` có thể trả về `0`. Quy tắc sắp thứ tự và `equals` khác nhau có thể dẫn đến khác biệt giữa `TreeSet` và `HashSet`.
- **Số thực:** `double` có `NaN`, `-0.0`, `+0.0`; tránh chọn trực tiếp tiêu chí “khoảng sai số epsilon” cho `equals` vì dễ vi phạm bắc cầu. Khi cần biểu diễn giá trị số thực theo hợp đồng nhất quán, xem xét `Double.compare` / `Double.hashCode` hoặc quy tắc chuẩn hóa phù hợp với miền dữ liệu.
- **`record`:** Java tự sinh `equals` và `hashCode` theo các thành phần của record. Đó là điểm khởi đầu hữu ích cho mô hình giá trị, nhưng thành phần tham chiếu tới đối tượng có thể thay đổi vẫn cần được cân nhắc về tính bất biến thực tế.

## 7. Hai phản ví dụ giúp nhận ra lỗi thiết kế

### Sai đối xứng: so sánh sang kiểu khác

Đoạn **minh họa rút gọn, không phải chương trình Java độc lập** sau giả định lớp `FlexibleText` có trường `String value`:

```java
@Override
public boolean equals(Object other) {
    if (other instanceof FlexibleText text) {
        return value.equalsIgnoreCase(text.value);
    }
    if (other instanceof String text) {
        return value.equalsIgnoreCase(text); // SAI: String.equals không nhận FlexibleText
    }
    return false;
}
```

Với `FlexibleText("java")` và `"JAVA"`, lời gọi từ phía `FlexibleText` có thể đúng nhưng chiều ngược lại sai. Không dùng quy tắc này làm `equals`. Nếu muốn kiểm tra chuỗi không phân biệt hoa/thường, hãy công khai phương thức nghiệp vụ riêng hoặc chuẩn hóa dữ liệu trước khi tạo một kiểu giá trị có miền so sánh thống nhất.

### Sai bắc cầu: “gần bằng”

Giả sử `NearbyNumber.equals` trả về `true` khi hiệu tuyệt đối của hai giá trị không quá `1`. Khi giá trị là `1`, `2`, `3` thì:

```text
1 ~ 2: true
2 ~ 3: true
1 ~ 3: false
```

Hai tiền đề đều đúng nhưng kết luận sai, nên quan hệ không bắc cầu. Test chỉ một cặp có thể không thấy lỗi; cần test một **bộ ba**. Nếu ứng dụng cần khái niệm “gần nhau”, viết một hàm như `withinDistance(other, tolerance)` và không tuyên bố đó là `equals`.

## 8. Cách kiểm thử hợp đồng

Đối với lớp giá trị của bạn, chuẩn bị ít nhất ba đối tượng cùng giá trị nhưng được tạo độc lập (`a, b, c`), một đối tượng khác giá trị (`d`), một đối tượng khác kiểu và `null`. Xác nhận:

1. `a.equals(a)` đúng; với các đối tượng hợp lệ khác cũng tương tự.
2. `a.equals(b) == b.equals(a)`; thử cả cặp bằng nhau và không bằng nhau, kể cả khi khác kiểu.
3. Nếu `a.equals(b) && b.equals(c)` thì `a.equals(c)`; thử nhiều bộ ba, nhất là khi có kế thừa hoặc làm tròn số.
4. Gọi cùng cặp nhiều lần khi các trường liên quan không đổi cho cùng kết quả.
5. `a.equals(null)` sai; việc kiểm tra khác kiểu trả về `false` mà không ép kiểu sai.
6. Với mọi cặp bằng nhau, mã băm phải bằng nhau; kiểm tra thêm cách dùng thực tế trong `HashSet` và `HashMap`.
7. Nếu lớp cho phép thay đổi trạng thái, xác định rõ trường nào ảnh hưởng `equals`/`hashCode` và tránh dùng các đối tượng có hash thay đổi làm khóa.

Các lệnh kiểm tra trong `main` ở trên là ví dụ quan sát hành vi, chưa phải kiểm định đầy đủ. Với thư viện hoặc domain model quan trọng, nên có unit test theo tính chất (property-based tests) trên nhiều dữ liệu, đặc biệt test cặp kiểu cha/con và các giá trị biên nếu có.

## 9. Lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Hướng sửa |
| --- | --- | --- |
| Viết `equals(Coordinate other)` thay vì `equals(Object other)` | Đây là **overload**, không ghi đè `Object.equals`; các collection gọi phiên bản `equals(Object)` vẫn giữ hành vi cũ. | Dùng `@Override public boolean equals(Object other)`; annotation giúp trình biên dịch phát hiện chữ ký sai. |
| So sánh trường tham chiếu bằng `==` khi cần so sánh giá trị | Hai đối tượng nội dung giống nhau có thể bị coi là khác nhau. | Dùng `Objects.equals(field1, field2)` hoặc `field.equals(...)` khi chắc chắn không `null`. |
| Quên kiểm tra `null`/kiểu trước khi ép kiểu | Có thể gây `NullPointerException` hoặc `ClassCastException` trong quá trình so sánh. | Kiểm tra `this == other` và `instanceof` (hoặc kiểm tra class) trước khi đọc trường của đối tượng kia. |
| Ghi đè `equals` nhưng không ghi đè `hashCode` tương ứng | Cấu trúc băm có thể xử lý sai hai khóa bằng nhau. | Dùng cùng tập trường quan trọng cho cả hai phương thức. |
| Thêm điều kiện bằng nhau chỉ ở lớp con | Có thể phá đối xứng/bắc cầu khi so với lớp cha. | Chọn miền kiểu và chính sách kế thừa rõ ràng; ưu tiên value class `final` nếu phù hợp. |
| Dùng ngưỡng gần đúng làm `equals` | Có thể phá bắc cầu. | Tách thao tác “gần bằng” thành phương thức riêng. |
| Dùng trường mutable của khóa trong `hashCode` | Tra cứu trong `HashMap`/`HashSet` có thể thất bại sau sửa đổi. | Dùng khóa bất biến hoặc không thay đổi các trường tham gia so sánh khi đã lưu trong cấu trúc băm. |

## 10. Ghi nhớ

Một `equals(Object)` hợp lệ trên các đối tượng khác `null` phải tạo ra **quan hệ tương đương**: phản xạ, đối xứng, bắc cầu. Hợp đồng Java còn đòi hỏi nhất quán khi dữ liệu so sánh không đổi và `x.equals(null) == false`. Nếu đã định nghĩa bằng nhau theo giá trị, hãy bảo đảm `hashCode` tương thích. Điểm quan trọng nhất khi cài đặt là xác định **đúng ý nghĩa bằng nhau và miền kiểu**, sau đó dùng test cho cả các cặp và bộ ba đối tượng, không chỉ một ví dụ có kết quả mong đợi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-contract-002">Tính phản xạ: đối tượng bằng chính nó</a>

<details>
<summary>Xem chi tiết</summary>

# Tính phản xạ của `equals(Object)` trong Java

## 1. Tính phản xạ là gì?

Trong hợp đồng của `Object.equals(Object)`, **tính phản xạ (reflexivity)** yêu cầu: với mọi tham chiếu `x` khác `null`, lời gọi `x.equals(x)` phải trả về `true`.

```text
Với mọi x != null: x.equals(x) == true
```

Ở đây, hai vế `x` đều chỉ **cùng một đối tượng đang tồn tại**. Đây là quy tắc tối thiểu mà mọi cách cài đặt lại `equals` phải tôn trọng: một đối tượng không được tự tuyên bố rằng nó khác chính nó.

Điểm cần phân biệt:

- `x == x` luôn là `true` đối với một tham chiếu `x`: toán tử `==` so sánh danh tính tham chiếu.
- `x.equals(x)` **phải** là `true` theo hợp đồng, nhưng phương thức `equals` có thể bị người lập trình override sai.
- Hai đối tượng **khác tham chiếu** vẫn có thể bằng nhau về mặt logic nếu lớp định nghĩa `equals` theo các thuộc tính có ý nghĩa. Đây là câu hỏi khác với việc một đối tượng bằng chính nó.
- `null` không phải một đối tượng để gọi phương thức: `null.equals(null)` ném `NullPointerException`. Hợp đồng phản xạ chỉ phát biểu cho tham chiếu khác `null`.

## 2. Vì sao quy tắc này tồn tại?

`equals` biểu diễn một quan hệ bình đẳng theo nghĩa logic do lớp định nghĩa. Nếu quan hệ đó cho phép `x.equals(x) == false`, ngay cả trường hợp so sánh dễ nhất cũng không còn đáng tin cậy. Các phép kiểm tra, thuật toán và API dùng bình đẳng đối tượng thường xây dựng trên giả định rằng một cách cài đặt `equals` tuân thủ hợp đồng chuẩn.

Cài đặt `Object.equals` mặc định dùng bình đẳng danh tính: chỉ cùng một đối tượng mới bằng nhau. Vì vậy nó đã có tính phản xạ. Khi override để so sánh theo giá trị, lập trình viên phải **giữ lại** đặc tính ấy.

Tính phản xạ không có nghĩa là mọi đối tượng cùng lớp đều bằng nhau. Nó cũng không đòi hỏi tất cả trường dữ liệu phải khác `null`, bất biến hay có một giá trị nhất định; đối tượng nào cũng phải bằng chính nó.

## 3. Cài đặt `equals` đúng và kiểm chứng bằng Java 17

Ví dụ dưới đây đặt nhánh `this == other` lên đầu phương thức. Đây là cách viết rõ ràng và thường hiệu quả để bảo đảm trường hợp hai tham chiếu trỏ tới cùng một đối tượng trả về `true` ngay lập tức.

Tạo tệp **`ReflexivityDemo.java`**:

```java
import java.util.Objects;

public class ReflexivityDemo {
    static final class Person {
        private final String name;
        private final int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true; // Cùng một đối tượng: luôn bằng chính nó.
            }
            if (!(other instanceof Person person)) {
                return false; // Bao gồm cả trường hợp other == null.
            }
            return age == person.age
                    && Objects.equals(name, person.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age);
        }
    }

    static final class BrokenPerson {
        private final String name;

        BrokenPerson(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return false; // SAI: vi phạm tính phản xạ.
            }
            return other instanceof BrokenPerson person
                    && Objects.equals(name, person.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    public static void main(String[] args) {
        Person p = new Person(null, 20);
        Person q = new Person(null, 20);

        System.out.println("p == p: " + (p == p));
        System.out.println("p.equals(p): " + p.equals(p));
        System.out.println("p.equals(q): " + p.equals(q));
        System.out.println("p.equals(null): " + p.equals(null));

        BrokenPerson broken = new BrokenPerson("An");
        System.out.println("broken.equals(broken): "
                + broken.equals(broken));
        System.out.println("Objects.equals(broken, broken): "
                + Objects.equals(broken, broken));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 ReflexivityDemo.java
java ReflexivityDemo
```

Kết quả chính xác:

```text
p == p: true
p.equals(p): true
p.equals(q): true
p.equals(null): false
broken.equals(broken): false
Objects.equals(broken, broken): true
```

Giải thích từng trường hợp:

1. `p == p` là `true` vì hai vế là cùng tham chiếu.
2. `p.equals(p)` là `true` vì `this == other` trả về ngay, đáp ứng tính phản xạ.
3. `p.equals(q)` cũng là `true`, **nhưng vì hai đối tượng khác nhau có cùng `name` và `age`**. Điều này thuộc cách lớp định nghĩa bình đẳng theo giá trị; không phải phép kiểm tra tính phản xạ. Ví dụ cố ý dùng `name == null` để cho thấy trường `null` không làm mất tính phản xạ.
4. `p.equals(null)` là `false` theo kiểm tra `instanceof`. Điều này khác với việc gọi phương thức trên tham chiếu `null`.
5. `broken.equals(broken)` là `false` do nhánh sai trong `BrokenPerson.equals`, chứng minh rằng một override thực sự có thể phá vỡ hợp đồng.
6. `Objects.equals(broken, broken)` lại là `true`, vì tiện ích `Objects.equals(a, b)` kiểm tra `a == b` trước khi gọi `a.equals(b)`. **Không dùng `Objects.equals(x, x)` làm phép thử duy nhất để phát hiện `equals` thiếu tính phản xạ**: phép thử đó có thể che lỗi.

Hai lớp trong ví dụ đều override `hashCode` theo các trường so sánh để duy trì quy tắc liên quan đến các đối tượng bằng nhau. Tuy vậy, việc có `hashCode` hợp lý **không thể cứu** một cài đặt `equals` đã vi phạm tính phản xạ.

## 4. Có bắt buộc viết `if (this == other) return true;` không?

**Không bắt buộc về cú pháp.** Bắt buộc là **kết quả hành vi**: `x.equals(x)` phải là `true`.

Ví dụ, nếu phần còn lại của `equals` so sánh đầy đủ và đúng các trường với chính chúng, phương thức vẫn có thể phản xạ mà không cần nhánh tối ưu đầu tiên. Tuy nhiên, nhánh `this == other` có ba lợi ích rõ ràng:

- Làm hợp đồng dễ nhận ra khi đọc code.
- Tránh phải đi qua các phép so sánh thuộc tính khi hai tham chiếu đã là một.
- Giảm nguy cơ trường hợp tự so sánh đi vào những nhánh xử lý sai.

Nhánh này không thay thế việc kiểm tra kiểu, so sánh đúng các thuộc tính khi hai đối tượng khác nhau, hoặc tuân thủ các điều khoản khác của hợp đồng `equals`. Một phương thức có thể phản xạ nhưng vẫn sai ở những khía cạnh khác.

## 5. Lỗi thường gặp và cách kiểm tra

**Lỗi 1 — trả về `false` khi hai tham chiếu giống nhau.** Ví dụ `BrokenPerson` ở trên đảo ngược điều kiện cần có. Cách sửa trực tiếp là trả `true` khi `this == other`.

**Lỗi 2 — dùng `==` thay cho `equals` rồi kết luận toàn bộ phương thức đúng.** `x == x` chỉ kiểm tra danh tính tham chiếu. Nó không kiểm tra implementation của `equals`, nên một lớp có `equals` sai vẫn vượt qua phép thử `x == x`.

**Lỗi 3 — kiểm tra gián tiếp bằng một tiện ích tối ưu hóa.** `Objects.equals(x, x)` trả `true` ngay khi hai tham chiếu giống nhau. Hãy gọi thẳng `x.equals(x)` để kiểm tra hợp đồng của lớp.

**Lỗi 4 — nhầm `null` với một đối tượng đặc biệt.** Không thể gọi `null.equals(null)`; đừng đưa `null` vào miền giá trị của phát biểu phản xạ. Nếu cần so sánh hai tham chiếu có thể `null`, `Objects.equals(a, b)` là API tiện lợi, nhưng đó là mục đích khác.

**Lỗi 5 — cho điều kiện tự so sánh phụ thuộc dữ liệu.** Chẳng hạn, chỉ trả về `true` khi trường `name` khác `null` là sai: đối tượng với `name == null` vẫn phải bằng chính nó.

Có thể tạo một phép kiểm tra hồi quy ngắn, không cần thư viện kiểm thử:

```java
static void checkReflexive(Object value) {
    Objects.requireNonNull(value, "value");
    if (!value.equals(value)) {
        throw new AssertionError("equals vi pham tinh phan xa");
    }
}
```

Đây là **đoạn phương thức minh họa**, đặt bên trong một lớp Java thích hợp rồi gọi với nhiều đối tượng đại diện (các trường hợp bình thường, trường có `null`, giá trị biên). Nó kiểm tra trực tiếp đúng điều khoản phản xạ. Nếu đang dùng JUnit, ý tưởng tương đương là `assertTrue(value.equals(value))` đối với từng giá trị khác `null`.

## 6. Ghi nhớ

- Phát biểu chuẩn: **với mọi tham chiếu `x` khác `null`, `x.equals(x)` phải trả về `true`**.
- Đây là yêu cầu đối với implementation `equals` của lớp, không chỉ là tính chất của toán tử `==`.
- `if (this == other) return true;` là cách thực hiện thông dụng, rõ ràng, nhưng không phải cú pháp bắt buộc.
- Kiểm tra trực tiếp `x.equals(x)`; `Objects.equals(x, x)` có thể che lỗi vì tối ưu hóa so sánh tham chiếu.
- Nếu một đối tượng có thể `equals` với chính nó trả về `false`, implementation đã vi phạm hợp đồng và cần được sửa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-contract-003">Tính đối xứng khi đổi vị trí hai đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Tính đối xứng khi đổi vị trí hai đối tượng trong hợp đồng `equals()`

## 1. Khái niệm và yêu cầu của hợp đồng

Trong Java, `Object.equals(Object)` diễn tả việc hai đối tượng có được xem là **bằng nhau về mặt logic** hay không. Một yêu cầu bắt buộc của hợp đồng `equals` là **tính đối xứng (symmetry)**:

> Với hai tham chiếu `a` và `b` không null, `a.equals(b)` phải trả về cùng kết quả với `b.equals(a)`.

Công thức kiểm tra:

```java
a.equals(b) == b.equals(a)
```

Yêu cầu này đúng khi cả hai kết quả là `true` **hoặc** cả hai là `false`. Nếu `a.equals(b)` là `true` nhưng `b.equals(a)` là `false`, lớp đó vi phạm hợp đồng. “Đổi vị trí” tức là hoán đổi **đối tượng nhận lời gọi** và **đối số**, chứ không phải đổi thứ tự so sánh các thuộc tính trong cùng đối tượng.

Chẳng hạn, hai đối tượng cùng mô tả tọa độ (3, 4) phải thống nhất kết luận bằng nhau ở cả hai chiều. Nếu một đối tượng còn biểu diễn màu sắc mà đối tượng kia không có màu sắc, thiết kế quan hệ bằng nhau phải giải quyết rõ việc chúng có được coi là cùng một giá trị hay không.

Symmetry là một trong năm yêu cầu của hợp đồng `equals`, cùng với tính phản xạ, bắc cầu, nhất quán và so sánh với `null` luôn trả về `false`. Đạt symmetry **chưa đủ** để khẳng định `equals` hợp lệ toàn diện. Nếu ghi đè `equals`, cũng phải bảo đảm các đối tượng bằng nhau có cùng `hashCode()`.

## 2. Tại sao đổi chiều lại cho kết quả khác?

Lỗi xuất hiện rõ nhất khi lớp cha và lớp con sử dụng **hai tiêu chí bằng nhau không tương thích**:

1. Lớp cha `Point` chỉ so sánh tọa độ `x`, `y` và sử dụng `instanceof Point`, nên chấp nhận cả `ColoredPoint` là một `Point`.
2. Lớp con `ColoredPoint` bổ sung trường `color` và chỉ chấp nhận đối tượng `ColoredPoint`.
3. Khi gọi `point.equals(coloredPoint)`, lớp cha nhìn thấy cùng tọa độ và trả về `true`; khi gọi theo chiều ngược lại, lớp con nhìn thấy kiểu không tương thích và trả về `false`.

Java cho phép mã này biên dịch, vì trình biên dịch không kiểm chứng được mọi tính chất của quan hệ bằng nhau.

## 3. Ví dụ Java 17: sai ở đâu, sửa thế nào?

Lưu chương trình đầy đủ sau trong tệp **`EqualsSymmetryDemo.java`**. Ví dụ đầu cố tình vi phạm đối xứng. Ví dụ sau biểu diễn điểm có màu bằng **composition**: một đối tượng chứa tọa độ và màu, thay vì kế thừa một lớp với định nghĩa bằng nhau khác.

```java
import java.util.Objects;

public class EqualsSymmetryDemo {
    static class Point {
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
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ColoredPoint p)) return false;
            return super.equals(p) && color.equals(p.color);
        }

        // Kế thừa Point.hashCode() để tập trung minh họa
        // lỗi symmetry; trùng hash giữa hai màu khác nhau là hợp lệ.
    }

    record SafePoint(int x, int y) {}

    record SafeColoredPoint(SafePoint point, String color) {
        SafeColoredPoint {
            Objects.requireNonNull(point);
            Objects.requireNonNull(color);
        }
    }

    static void assertSymmetric(Object a, Object b) {
        boolean forward = Objects.equals(a, b);
        boolean backward = Objects.equals(b, a);
        if (forward != backward) {
            throw new AssertionError("equals is not symmetric");
        }
    }

    public static void main(String[] args) {
        Point base = new Point(3, 4);
        ColoredPoint red = new ColoredPoint(3, 4, "red");

        System.out.println("base.equals(red) = " + base.equals(red));
        System.out.println("red.equals(base) = " + red.equals(base));

        SafePoint plain = new SafePoint(3, 4);
        SafeColoredPoint colored = new SafeColoredPoint(plain, "red");
        SafeColoredPoint copy =
                new SafeColoredPoint(new SafePoint(3, 4), "red");

        System.out.println("plain.equals(colored) = " + plain.equals(colored));
        System.out.println("colored.equals(plain) = " + colored.equals(plain));
        System.out.println("colored.equals(copy) = " + colored.equals(copy));

        assertSymmetric(plain, colored);
        assertSymmetric(colored, copy);
        assertSymmetric(plain, new SafePoint(3, 4));
        assertSymmetric(plain, null);
        System.out.println("safe symmetry checks = PASS");
    }
}
```

Tại thư mục chứa tệp, thực thi:

```bash
javac EqualsSymmetryDemo.java
java EqualsSymmetryDemo
```

Kết quả trên Java 17+:

```text
base.equals(red) = true
red.equals(base) = false
plain.equals(colored) = false
colored.equals(plain) = false
colored.equals(copy) = true
safe symmetry checks = PASS
```

**Diễn giải:**

- `base.equals(red)` là `true` vì `red instanceof Point` đúng và cả hai tọa độ bằng nhau. Phương thức lớp cha bỏ qua màu.
- `red.equals(base)` là `false` vì `base instanceof ColoredPoint` sai. Đây chính là vi phạm đối xứng: cùng một cặp nhưng hai chiều trái ngược.
- `plain.equals(colored)` và `colored.equals(plain)` cùng `false`. Hai kiểu `record` độc lập biểu diễn hai loại giá trị khác nhau, nên hai phía không coi nhau là bằng nhau.
- `colored.equals(copy)` là `true`: hai đối tượng `SafeColoredPoint` có điểm và màu tương ứng bằng nhau. `record` tự cung cấp `equals` và `hashCode` dựa trên các thành phần.
- `assertSymmetric` kiểm tra hai hướng kể cả trường hợp `null`; nếu gọi `assertSymmetric(base, red)`, hàm sẽ ném `AssertionError`.

Trong phần mã lỗi, `ColoredPoint` giữ `hashCode` kế thừa chỉ dựa vào tọa độ. Hai điểm khác màu có thể cùng hash dù không bằng nhau, và **va chạm hash là hợp lệ**; việc đó giúp minh họa riêng lỗi symmetry. Tuy vậy, trong thiết kế hoàn chỉnh, hãy chọn thành phần hash theo mô hình bằng nhau hợp lệ và đảm bảo hai đối tượng bằng nhau luôn có cùng hash.

## 4. Thiết kế `equals` để bảo toàn tính đối xứng

### Giải pháp A: lớp giá trị `final`, `record` và composition

Nếu đối tượng là một giá trị có tập thuộc tính xác định, lớp `final` ngăn lớp con thay đổi cách hiểu quan hệ bằng nhau. `record` phù hợp với đối tượng dữ liệu bất biến theo các thành phần: Java tự sinh `equals`/`hashCode` theo thành phần và kiểu `record`. Trong ví dụ, `SafeColoredPoint` chứa `SafePoint` nên màu sắc không bị lẫn vào hợp đồng bằng nhau của `SafePoint`.

Composition đặc biệt hữu ích khi một kiểu mới **có thêm thuộc tính quyết định danh tính**. Nếu kế thừa kiểu cũ chỉ để tái sử dụng dữ liệu mà rồi sửa cách so sánh, có thể làm hỏng đối xứng hoặc bắc cầu.

### Giải pháp B: xác nhận cùng kiểu runtime bằng `getClass()`

Nếu các đối tượng thuộc **hai lớp runtime khác nhau không được xem là bằng nhau**, có thể thay `Point.equals` trong chương trình trên bằng phương thức sau:

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    Point p = (Point) other;
    return x == p.x && y == p.y;
}
```

Đây là **đoạn thay thế trong lớp `Point`**, không phải chương trình độc lập. Khi ấy, `Point.equals(ColoredPoint)` và `ColoredPoint.equals(Point)` cùng trả về `false`. Với hai đối tượng đều là `ColoredPoint`, lời gọi `super.equals(p)` trong lớp con vẫn có thể so sánh tọa độ rồi kiểm tra màu.

Cần lưu ý: `getClass()` coi lớp runtime là một phần của danh tính logic. Nếu bài toán thật sự yêu cầu đối tượng thuộc các lớp khác nhau có thể bằng nhau, kiểm tra chặt kiểu có thể không phù hợp. Không có một lệnh thay thế máy móc cho mọi mô hình phân cấp.

### Giải pháp C: một quy tắc bằng nhau thống nhất trong toàn hệ phân cấp

Nếu cần cho phép so sánh bằng nhau xuyên lớp, toàn bộ hệ phân cấp phải chia sẻ cách xác định danh tính và duy trì **cả tính đối xứng lẫn tính bắc cầu**. Không thể để lớp cha coi tọa độ là đủ, còn lớp con vừa yêu cầu thêm màu, vừa kỳ vọng so sánh qua lớp cha vẫn giữ hợp đồng trong mọi trường hợp.

Một lựa chọn là đặt mọi thuộc tính quyết định bình đẳng vào cùng một mô hình giá trị thống nhất; lựa chọn khác là định nghĩa các kiểu giá trị độc lập và chuyển dữ liệu theo một chuẩn chung trước khi so sánh. Khi không có lý do nghiệp vụ rõ ràng để bằng nhau xuyên kiểu, `final`/`record` và composition thường dễ duy trì hơn.

## 5. Cách kiểm thử và các lỗi thường gặp

Kiểm thử theo cặp phải quan sát **hai lời gọi riêng biệt**:

```java
boolean leftToRight = Objects.equals(a, b);
boolean rightToLeft = Objects.equals(b, a);
if (leftToRight != rightToLeft) {
    throw new AssertionError("Broken symmetry");
}
```

Đoạn trên sử dụng `a`, `b` có sẵn; hàm `assertSymmetric` trong chương trình đầy đủ là phiên bản chạy được. `Objects.equals(a, b)` chỉ xử lý null an toàn rồi dựa vào `a.equals(b)`; **nó không tự sửa quan hệ bằng nhau thiếu đối xứng**. Vì thế cần so với `Objects.equals(b, a)`.

Nên lập bộ cặp gồm: cùng lớp/cùng giá trị; cùng lớp/khác giá trị; đối tượng cha–con ở cả hai hướng; hai lớp con khác nhau; đối tượng với `null`; và hai đối tượng trỏ cùng tham chiếu. Cặp cha–con rất quan trọng vì nó làm lộ tiêu chí kiểm tra kiểu không thống nhất. Ngoài symmetry, hãy kiểm tra phản xạ, bắc cầu, nhất quán và điều kiện hash của các đối tượng bằng nhau.

| Lỗi | Vì sao nguy hiểm | Cách xử lý |
| --- | --- | --- |
| Cha dùng `instanceof` rộng, con chỉ nhận đúng lớp con | Một chiều `true`, chiều kia `false` | Thống nhất phạm vi kiểu được coi là bằng nhau |
| Chỉ kiểm tra `a.equals(b)` | Bỏ sót kết quả trái chiều | Kiểm tra cả `b.equals(a)` |
| Viết `equals(Point)` thay vì `equals(Object)` | Đây là overload, không ghi đè phương thức của `Object` | Dùng đúng chữ ký và `@Override` |
| Ghi đè `equals` nhưng quên `hashCode` | Hai đối tượng bằng nhau có thể cho hash khác nhau | Xây dựng cả hai theo cùng quan hệ bằng nhau |
| Dùng `==` để so đối tượng cần bằng nhau theo giá trị | So sánh cùng tham chiếu thay vì cùng giá trị | Xác định rõ so identity hay logical equality |
| Dùng thuộc tính thay đổi được trong equality/hash của khóa `HashMap` | Có thể không tìm lại khóa sau khi thay đổi | Ưu tiên khóa giá trị bất biến |

**Bài tự kiểm tra:** `A.equals` chỉ xét `id` và chấp nhận `instanceof A`; lớp con `B.equals` chỉ chấp nhận `B`, xét cả `id` và `role`. Với `new A(7)` và `new B(7, "admin")`, chiều `A → B` sẽ là `true`, còn chiều `B → A` sẽ là `false`. Cách sửa phải làm cho cả hai chiều thống nhất và vẫn giữ các yêu cầu còn lại của `equals`.

Trước khi triển khai `equals`, hãy xác định rõ **hai kiểu nào được phép bằng nhau và dựa trên các thuộc tính nào**, sau đó kiểm tra mỗi cặp theo cả hai hướng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-contract-004">Tính bắc cầu giữa ba đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Tính bắc cầu giữa ba đối tượng trong `equals()`

## 1. Tính bắc cầu là gì?

Trong Java, `Object.equals(Object)` xác định hai đối tượng có được xem là **bằng nhau về mặt logic** hay không. Một yêu cầu bắt buộc của hợp đồng `equals()` là **tính bắc cầu (transitivity)**:

> Với ba tham chiếu khác `null` là `a`, `b`, `c`: nếu `a.equals(b)` là `true` và `b.equals(c)` là `true` thì `a.equals(c)` cũng phải là `true`.

Có thể viết:

```text
a ==(equals)== b  và  b ==(equals)== c
                    => a ==(equals)== c
```

Ở đây, “bằng nhau” là kết quả của `equals()`, **không nhất thiết là cùng một đối tượng trong bộ nhớ** (`a == b`). Ba đối tượng khác nhau vẫn có thể mang cùng giá trị và `equals()` trả về `true` cho cả ba cặp.

Ví dụ đúng: ba đối tượng đại diện cùng mã định danh bất biến `"A-101"`. Khi `a` bằng `b` theo mã này và `b` bằng `c` theo cùng mã, `a` bắt buộc bằng `c`. Quy tắc phải áp dụng nhất quán cho **mọi bộ ba** thuộc kiểu đối tượng đó, chứ không chỉ bộ ba được chọn để kiểm thử.

Tính bắc cầu là một thành phần trong hợp đồng đầy đủ của `equals()`: phản xạ, đối xứng, bắc cầu, nhất quán và trả về `false` khi so sánh với `null`. Bài này tập trung vào tính bắc cầu; các tính chất còn lại vẫn phải được đảm bảo khi triển khai thực tế.

## 2. Phản ví dụ: coi hai số “đủ gần” là bằng nhau

Giả sử một lớp đo khoảng cách quyết định hai giá trị bằng nhau khi chênh lệch không quá **10 mm**. Với ba đối tượng lưu lần lượt `0 mm`, `8 mm` và `16 mm`:

| Cặp đối tượng | Độ lệch | Kết quả “chênh ≤ 10 mm” |
| --- | ---: | --- |
| `a(0)` và `b(8)` | `8 mm` | `true` |
| `b(8)` và `c(16)` | `8 mm` | `true` |
| `a(0)` và `c(16)` | `16 mm` | `false` |

Ta có `a.equals(b) && b.equals(c)` là `true` nhưng `a.equals(c)` là `false`. Đây là **vi phạm trực tiếp tính bắc cầu**.

Nguyên nhân không nằm ở sai số số thực: ví dụ sử dụng số nguyên. Quan hệ “cách nhau tối đa 10 đơn vị” cho phép chuỗi các bước nhỏ tích lũy thành khoảng cách lớn hơn 10; vì thế nó không tạo thành quan hệ bằng nhau đúng nghĩa. Thay `10` bằng bất kỳ dung sai dương cố định nào vẫn có nguy cơ tương tự.

## 3. Chương trình Java 17 chạy được: lỗi và hai cách thiết kế đúng

Lưu toàn bộ mã sau vào tệp `EqualsTransitivityDemo.java`. Lớp `NearReading` **cố ý sai về tính bắc cầu** để minh họa; đừng dùng cách định nghĩa `equals()` này trong ứng dụng. Hai kiểu `ExactReading` và `BucketReading` bên dưới minh họa các quan hệ bằng nhau hợp lệ.

```java
import java.util.List;

public class EqualsTransitivityDemo {

    // PHẢN VÍ DỤ: equals() dựa trên khoảng cách tối đa 10 mm.
    static final class NearReading {
        private final int millimeters;

        NearReading(int millimeters) {
            this.millimeters = millimeters;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof NearReading that)) {
                return false;
            }
            return Math.abs((long) millimeters - that.millimeters) <= 10;
        }

        @Override
        public int hashCode() {
            return 0; // Chỉ để cô lập lỗi bắc cầu; hash kém hiệu quả.
        }
    }

    // CÁCH ĐÚNG 1: giá trị mm phải bằng chính xác.
    record ExactReading(int millimeters) {}

    // CÁCH ĐÚNG 2: so sánh một khóa phân nhóm đã định nghĩa rõ.
    record BucketReading(int bucket) {
        static BucketReading fromMillimeters(int millimeters) {
            return new BucketReading(Math.floorDiv(millimeters, 10));
        }
    }

    // Kiểm tra TẤT CẢ bộ ba trong một tập mẫu hữu hạn.
    static <T> boolean isTransitiveOn(List<T> values) {
        for (T a : values) {
            for (T b : values) {
                for (T c : values) {
                    if (a.equals(b) && b.equals(c) && !a.equals(c)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        NearReading a = new NearReading(0);
        NearReading b = new NearReading(8);
        NearReading c = new NearReading(16);
        System.out.println("near a=b: " + a.equals(b));
        System.out.println("near b=c: " + b.equals(c));
        System.out.println("near a=c: " + a.equals(c));
        System.out.println("near transitive: "
                + isTransitiveOn(List.of(a, b, c)));

        ExactReading p = new ExactReading(8);
        ExactReading q = new ExactReading(8);
        ExactReading r = new ExactReading(8);
        System.out.println("exact p=q: " + p.equals(q));
        System.out.println("exact q=r: " + q.equals(r));
        System.out.println("exact p=r: " + p.equals(r));
        System.out.println("exact transitive: "
                + isTransitiveOn(List.of(p, q, r)));

        BucketReading u = BucketReading.fromMillimeters(0);
        BucketReading v = BucketReading.fromMillimeters(8);
        BucketReading w = BucketReading.fromMillimeters(16);
        System.out.println("bucket u=v: " + u.equals(v));
        System.out.println("bucket v=w: " + v.equals(w));
        System.out.println("bucket transitive: "
                + isTransitiveOn(List.of(u, v, w)));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac EqualsTransitivityDemo.java
java EqualsTransitivityDemo
```

Kết quả chuẩn:

```text
near a=b: true
near b=c: true
near a=c: false
near transitive: false
exact p=q: true
exact q=r: true
exact p=r: true
exact transitive: true
bucket u=v: true
bucket v=w: false
bucket transitive: true
```

Giải thích:

- `NearReading` dùng `Math.abs((long) millimeters - that.millimeters)` để tránh tràn số khi trừ hai giá trị `int` cực trị. Dù phép tính khoảng cách an toàn, **quy tắc bằng nhau vẫn sai** vì không bắc cầu.
- `ExactReading` là `record`; Java tự cung cấp `equals()` và `hashCode()` dựa trên thành phần `millimeters`. Ba bản ghi cùng giá trị `8` bằng nhau theo mọi cặp.
- `BucketReading` chuyển dữ liệu thành **khóa phân nhóm cố định**: `Math.floorDiv(value, 10)`. Hai bản ghi bằng nhau khi có cùng `bucket`. `0` và `8` cùng nhóm `0`; `16` ở nhóm `1`. Khi `bucket(a) = bucket(b)` và `bucket(b) = bucket(c)` thì chắc chắn `bucket(a) = bucket(c)`.
- Phép kiểm `isTransitiveOn` xét mọi bộ ba của **tập mẫu truyền vào**, có chi phí `O(n³)` nên phù hợp với bài kiểm thử nhỏ. Kết quả `true` trên một tập mẫu **không chứng minh** lớp đúng với mọi giá trị có thể có; cần kết hợp lý luận về quy tắc so sánh.

Chú ý: phân nhóm có biên cứng. Ví dụ `9 mm` và `10 mm` rất gần nhưng thuộc hai nhóm khác nhau. Chỉ chọn mô hình này khi ý nghĩa nghiệp vụ thực sự là “cùng nhóm”; không dùng nó để giả vờ tái tạo quy tắc “cách nhau tối đa 10 mm”.

## 4. Thiết kế `equals()` thế nào để giữ tính bắc cầu?

### Cách A — so sánh khóa hoặc trạng thái bất biến, nhất quán

Nếu hai đối tượng biểu diễn cùng thực thể khi có cùng mã định danh bất biến, hãy so sánh **chính mã đó**. Với value object, so sánh đầy đủ tập thuộc tính được định nghĩa là bản sắc logic. Dùng `record` khi dữ liệu phù hợp với ngữ nghĩa giá trị; Java sẽ tạo các phương thức bằng nhau theo thành phần.

Ví dụ một value object đơn giản:

```java
record DocumentKey(String namespace, long id) {}
```

`new DocumentKey("docs", 7)` bằng một `DocumentKey` khác có cùng `namespace` và `id`. Nếu `a`, `b` và `c` có cùng cặp thuộc tính đó thì tính bắc cầu suy ra từ tính bắc cầu của phép so sánh thành phần. Mã trên là **đoạn minh họa**, không phải chương trình độc lập.

Nếu tự viết `equals()`, cần kiểm tra kiểu đối tượng và so sánh **cùng một bộ tiêu chí** ở mọi lần gọi. Tránh thay đổi chính sách dựa vào phía nào của phép so sánh, trạng thái toàn cục, thời điểm, hoặc nhánh kiểu lớp khác nhau.

### Cách B — chuẩn hóa thành dạng đại diện (canonical form)

Nếu nghiệp vụ xem các cách biểu diễn là giống nhau (ví dụ mã không phân biệt chữ hoa/thường), chuẩn hóa **một lần theo quy tắc ổn định**, rồi so sánh các giá trị chuẩn hóa. Một hàm chuẩn hóa xác định `f` tạo quan hệ `a ~ b` khi `f(a).equals(f(b))`. Vì phép bằng nhau trên giá trị chuẩn hóa là bắc cầu, quan hệ này cũng bắc cầu nếu `f` ổn định và có ngữ nghĩa đúng.

Các quy tắc chuẩn hóa phải được quy định rõ: chuẩn hóa Unicode, khoảng trắng, chữ hoa/thường theo locale phù hợp, đơn vị đo hoặc mức làm tròn cố định. Không nên tùy tiện cắt chữ, làm tròn hay đổi locale vì có thể vô tình gộp những giá trị cần phân biệt.

### Cách C — tách “gần nhau” khỏi “bằng nhau”

Nếu cần hỏi hai phép đo có gần nhau trong một sai số cho phép, hãy tạo một phương thức riêng như `withinTolerance(other, tolerance)`. Đây là **quan hệ xấp xỉ**, không có nghĩa vụ bắc cầu, và không nên được sử dụng làm `equals()`.

```java
static boolean withinTolerance(long a, long b, long tolerance) {
    if (tolerance < 0) {
        throw new IllegalArgumentException("tolerance must be nonnegative");
    }
    // Ví dụ giới hạn đầu vào để tránh tràn khi trừ:
    return Math.abs((double) a - (double) b) <= tolerance;
}
```

Đoạn minh họa trên dùng `double` cho hiệu để tránh tràn số nguyên nhưng **có thể mất độ chính xác với số `long` rất lớn**. Trong code cần chính xác tuyệt đối cho mọi `long`, hãy dùng `BigInteger` hoặc phép so sánh trừ có kiểm soát. Với dữ liệu đo lường thực tế, còn phải xác định sai số tuyệt đối, tương đối, đơn vị và cách xử lý `NaN`/vô cực nếu dùng số thực. Việc dùng API xấp xỉ độc lập giúp không làm sai hợp đồng `equals()`.

## 5. Vì sao lỗi này ảnh hưởng đến Java Collections?

`HashSet`, `HashMap` và nhiều thao tác tìm kiếm/deduplicate dựa trên việc `equals()` biểu diễn **một quan hệ bằng nhau ổn định**. Nếu `a` bằng `b`, `b` bằng `c` nhưng `a` khác `c`, ý nghĩa “một phần tử tương đương” trở nên mâu thuẫn: thao tác thêm, tìm, loại trùng có thể cho kết quả khó dự đoán theo dữ liệu và thứ tự thao tác. Vì thế lỗi không chỉ làm hỏng một biểu thức `if`.

Bắc cầu và `hashCode()` là hai yêu cầu liên quan nhưng **khác nhau**:

1. Nếu `x.equals(y)` là `true` thì `x.hashCode() == y.hashCode()` phải đúng.
2. Mã băm giống nhau **không bắt buộc** đồng nghĩa hai đối tượng bằng nhau.
3. Sửa `hashCode()` không thể chữa một quan hệ `equals()` không bắc cầu.

Trong `NearReading` ở ví dụ, `hashCode()` cố ý trả về `0` cho mọi đối tượng để **không vi phạm quy tắc “equals ⇒ hash bằng nhau”** trong khi phơi bày lỗi bắc cầu. Cách dùng hằng số này làm tất cả phần tử rơi vào cùng nhóm băm và có thể giảm hiệu suất nghiêm trọng; không phải cách cài đặt nên áp dụng. Với kiểu hợp lệ, hãy tính hash từ đúng các thuộc tính góp phần xác định `equals()`, hoặc để `record` sinh hộ.

Một lưu ý khác: những thuộc tính được dùng làm khóa trong `HashMap`/`HashSet` nên ổn định khi đối tượng đang nằm trong collection. Thay đổi các trường tham gia `equals()`/`hashCode()` sau khi chèn có thể khiến việc tìm kiếm thất bại, ngay cả khi quy tắc so sánh về mặt toán học vẫn bắc cầu.

## 6. Những trường hợp dễ nhầm với lỗi bắc cầu

- **Tính đối xứng khác tính bắc cầu.** Nếu `a.equals(b)` khác `b.equals(a)`, đó là lỗi đối xứng. Các kiểu cha/con tự viết `equals()` theo những trường khác nhau có thể mắc lỗi đối xứng, bắc cầu hoặc cả hai; phải kiểm tra từng tính chất riêng.
- **`==` khác `equals()`.** `==` so sánh hai tham chiếu có cùng chỉ đến một đối tượng hay không; nó không phải định nghĩa bản sắc logic của value object.
- **`BigDecimal.compareTo()` và `BigDecimal.equals()` có tiêu chí khác nhau.** Ví dụ `new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0`, trong khi hai đối tượng đó không `equals()` vì khác scale. Đây **không phải bằng chứng vi phạm tính bắc cầu**: chỉ là hai quan hệ có ngữ nghĩa khác nhau. Đừng trộn tiêu chí của hai phương thức trong một lập luận.
- **`Objects.equals(a, b)` chỉ hỗ trợ null-safe.** Nó gọi `a.equals(b)` khi `a` không null; không tự sửa logic bất bắc cầu của lớp.
- **Tính nhất quán theo thời gian cũng quan trọng.** Nếu `equals()` phụ thuộc dữ liệu biến đổi, đồng hồ hoặc ngữ cảnh không ổn định, cùng cặp đối tượng có thể cho kết quả khác nhau ở các lần gọi; đó là vấn đề cần xử lý bên cạnh bắc cầu.

## 7. Cách kiểm tra khi review hoặc viết test

Với một lớp có `equals()` tự viết, hãy lần lượt:

1. **Xác định bản sắc logic:** cụ thể thuộc tính/khóa nào làm hai đối tượng bằng nhau? Định nghĩa đó có nhất quán cho tất cả đối tượng, lớp con và dữ liệu biên không?
2. **Tạo bộ ba có chủ đích:** chọn `a`, `b`, `c` sao cho `a.equals(b)` và `b.equals(c)` đều `true`; khẳng định `a.equals(c)` cũng `true`. Đặc biệt thử các điểm sát ngưỡng, khác kiểu lớp, trạng thái rỗng và giá trị cực trị nếu liên quan.
3. **Kiểm tra cả hợp đồng:** `a.equals(a)` đúng; `a.equals(b) == b.equals(a)`; lặp lại kết quả với trạng thái không đổi; `a.equals(null)` sai; `equals` đúng thì hash phải bằng nhau.
4. **Kiểm tra với collection:** thử các tình huống thêm/tìm/xóa với các đối tượng tương đương, đồng thời giữ khóa bất biến. Test collection bổ sung bằng chứng nhưng không thay thế phép kiểm bộ ba.
5. **Lý giải tổng quát:** đối chiếu thuật toán với định nghĩa quan hệ tương đương. Ví dụ “cùng mã bất biến” hoặc “cùng khóa chuẩn hóa” có lập luận bắc cầu rõ ràng; “chênh không quá epsilon” thì không.

Một mẫu assertion để dùng trong test (giả sử đã tạo sẵn ba đối tượng `a`, `b`, `c`):

```java
if (a.equals(b) && b.equals(c) && !a.equals(c)) {
    throw new AssertionError("equals() vi phạm tính bắc cầu");
}
```

Đây là **fragment cho test**, không phải một file Java độc lập. Khi `a`, `b`, `c` là ba `NearReading(0)`, `NearReading(8)`, `NearReading(16)` trong chương trình phía trên, assertion sẽ ném lỗi như mong đợi; khi dùng định nghĩa bằng nhau đúng, không được có bộ ba phản ví dụ.

## 8. Ghi nhớ

Tính bắc cầu nói rằng **`a.equals(b)` và `b.equals(c)` cùng đúng thì `a.equals(c)` bắt buộc đúng**. Nó bảo đảm các đối tượng được xem là “cùng giá trị” tạo thành những nhóm tương đương nhất quán. Không dùng khoảng sai số, quy tắc “gần giống”, hoặc tiêu chí so sánh thay đổi theo đối tượng để định nghĩa `equals()`. Hãy định nghĩa khóa/trạng thái bằng nhau rõ ràng; nếu cần xấp xỉ, tách nó thành một thao tác riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-contract-005">Tính nhất quán khi dữ liệu dùng để so sánh không thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Hợp đồng equals: tính nhất quán khi dữ liệu dùng để so sánh không thay đổi

## 1. Tính nhất quán nghĩa là gì?

Trong Java, phương thức **Object.equals(Object)** có một yêu cầu về *consistency* (tính nhất quán):

> Với hai tham chiếu đối tượng không null là x và y, những lần gọi liên tiếp x.equals(y) phải nhất quán trả về true hoặc nhất quán trả về false, **miễn là không có thông tin nào được dùng để so sánh bằng equals trên các đối tượng bị thay đổi**.

Đây là một điều kiện có tiền đề, không phải lời hứa rằng kết quả equals không bao giờ thay đổi trong suốt vòng đời đối tượng. Nếu trạng thái thực sự được dùng để so sánh thay đổi, một kết quả khác ở lần gọi sau có thể hoàn toàn hợp lệ.

Xét hai đối tượng cùng đại diện cho mã sách và số phiên bản:

- Lần 1: x.equals(y) trả về true.
- Không thay đổi mã sách, phiên bản hoặc bất kỳ thông tin so sánh nào của x và y.
- Lần 2, lần 3, ...: x.equals(y) vẫn phải trả về true.

Nếu lúc đầu x.equals(y) là false và mọi thông tin so sánh giữ nguyên, các lần gọi tiếp theo cũng phải là false. Không có quy định rằng kết quả phải là true; yêu cầu là **ổn định với cùng dữ liệu so sánh**.

Tính nhất quán là một trong các điều kiện của hợp đồng equals, bên cạnh phản xạ (reflexive), đối xứng (symmetric), bắc cầu (transitive) và x.equals(null) luôn bằng false. Bài này tập trung vào điều kiện nhất quán.

## 2. “Dữ liệu dùng để so sánh” bao gồm những gì?

Giả sử equals dùng các trường code và revision:

~~~java
return revision == other.revision
        && Objects.equals(code, other.code);
~~~

Khi code và revision ở **cả hai đối tượng** không thay đổi, kết quả so sánh phải giữ nguyên. Khi một trong hai trường đó thay đổi, điều kiện “không có thông tin so sánh nào thay đổi” không còn đúng.

Cần phân biệt:

| Tình huống | Có thay đổi thông tin dùng bởi equals? | Kết quả equals có được phép thay đổi? |
| --- | --- | --- |
| Gọi x.equals(y) nhiều lần, mọi dữ liệu so sánh giữ nguyên | Không | Không; phải nhất quán |
| Sửa một trường mà equals có so sánh trong x hoặc y | Có | Có thể thay đổi |
| Sửa trường mô tả/ghi chú không hề được equals sử dụng | Không | Không được đổi chỉ vì trường đó |
| Đối tượng giữ nguyên tham chiếu tới một List, nhưng phần tử của List dùng trong equals bị sửa | Có | Có thể thay đổi |
| Không sửa dữ liệu mà equals lại dùng thời gian, ngẫu nhiên hoặc biến đếm tự tăng để cho kết quả khác nhau | Kết quả phụ thuộc yếu tố không ổn định | Thiết kế vi phạm kỳ vọng nhất quán |

Cụm “không thay đổi” liên quan tới **giá trị/thông tin đem so sánh**, không chỉ việc biến tham chiếu x và y vẫn trỏ tới hai đối tượng cũ. Một đối tượng mutable có thể giữ cùng địa chỉ nhưng dữ liệu bên trong đã thay đổi. Ngược lại, thay đổi một trường hoàn toàn không liên quan tới equals không phải lý do hợp lệ để kết quả equals đổi khác.

Với trường là đối tượng lồng nhau, phải xét trạng thái mà phép equals của trường đó sử dụng. Ví dụ, nếu equals so sánh một List bằng List.equals, việc thay đổi phần tử có thể thay đổi kết quả dù tham chiếu List vẫn là một.

## 3. Ví dụ Java 17+ chạy được: dữ liệu ổn định và dữ liệu bị sửa

Tạo tệp **EqualsConsistencyDemo.java**:

~~~java
import java.util.Objects;

public class EqualsConsistencyDemo {
    public static void main(String[] args) {
        BookCode fixedA = new BookCode("JAVA", 17);
        BookCode fixedB = new BookCode("JAVA", 17);

        System.out.println("fixed #1: " + fixedA.equals(fixedB));
        System.out.println("fixed #2: " + fixedA.equals(fixedB));

        MutableLabel left = new MutableLabel("ready");
        MutableLabel right = new MutableLabel("ready");

        System.out.println("before edit: " + left.equals(right));
        right.setText("busy");
        System.out.println("after edit: " + left.equals(right));
        right.setText("ready");
        System.out.println("after restore: " + left.equals(right));
    }

    static final class BookCode {
        private final String code;
        private final int revision;

        BookCode(String code, int revision) {
            this.code = Objects.requireNonNull(code);
            this.revision = revision;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof BookCode that)) {
                return false;
            }
            return revision == that.revision
                    && code.equals(that.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, revision);
        }
    }

    static final class MutableLabel {
        private String text;

        MutableLabel(String text) {
            this.text = text;
        }

        void setText(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof MutableLabel that)) {
                return false;
            }
            return Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(text);
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac EqualsConsistencyDemo.java
java EqualsConsistencyDemo
~~~

Kết quả chuẩn:

~~~text
fixed #1: true
fixed #2: true
before edit: true
after edit: false
after restore: true
~~~

**Giải thích:**

1. BookCode có các trường final và đối tượng String bất biến; sau khi khởi tạo, code và revision không bị sửa. So sánh cùng cặp fixedA, fixedB lặp lại luôn trả true.
2. Trước khi sửa, left và right đều có text = "ready", nên equals trả true.
3. Sau lệnh right.setText("busy"), dữ liệu so sánh của right thay đổi. Kết quả false **không vi phạm** tính nhất quán.
4. Sau khi đặt lại "ready", dữ liệu lại bằng nhau, kết quả true cũng hợp lệ.
5. Cả hai lớp ghi đè hashCode tương thích với các trường đang dùng trong equals. Điều này phục vụ một điều kiện quan trọng khác khi đối tượng được dùng trong cấu trúc băm.

Không nên diễn giải ví dụ rằng mutable luôn sai hoặc final tự động khiến equals đúng. Một lớp mutable vẫn có thể thực hiện hợp đồng nhất quán: mỗi lần kiểm tra phải dựa trên dữ liệu đang có, không tự đổi kết quả khi dữ liệu so sánh đứng yên. final giúp việc giữ trạng thái so sánh ổn định dễ hơn, nhưng chất lượng triển khai equals vẫn cần được kiểm tra.

## 4. Ví dụ sai: kết quả tự đổi dù không sửa dữ liệu đối tượng

Đoạn mã sau **chỉ minh họa cách triển khai sai**, không phải một chương trình độc lập; phương thức còn vi phạm các điều kiện khác của equals trong một số tình huống:

~~~java
final class BadKey {
    private static boolean flip;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BadKey)) {
            return false;
        }
        flip = !flip;
        return flip;
    }
}
~~~

Với hai đối tượng BadKey khác tham chiếu, những lần so sánh liên tiếp có thể luân phiên true và false dù không thay đổi thông tin đại diện cho hai đối tượng. Biến flip là trạng thái dùng để làm kết quả dao động, không phải thuộc tính giá trị của đối tượng được so sánh. Đây là cách thiết kế không phù hợp cho equals.

Các dấu hiệu lỗi cùng bản chất:

- Đọc giá trị ngẫu nhiên, đồng hồ hệ thống hoặc dữ liệu ngoài thường xuyên thay đổi để quyết định bằng nhau khi bản chất giá trị đối tượng không đổi.
- Tăng bộ đếm, đổi cờ hoặc sửa trường so sánh bên trong equals, khiến chính lần kiểm tra trước làm kết quả lần kiểm tra sau thay đổi.
- So sánh các trường được tính toán với logic không ổn định hoặc phụ thuộc trạng thái toàn cục không được kiểm soát.
- Gọi getter có tác dụng phụ làm thay đổi dữ liệu so sánh; tên getter không bảo đảm rằng nó chỉ đọc dữ liệu.

Ngược lại, tính toán một giá trị suy ra theo cách **thuần túy và xác định** từ các trường ổn định, rồi dùng giá trị đó trong equals, vẫn có thể nhất quán. Ví dụ chuẩn hóa mã bằng một quy tắc cố định sẽ ổn định nếu dữ liệu đầu vào và quy tắc không đổi.

## 5. Vì sao tính nhất quán quan trọng khi dùng hashCode và HashSet/HashMap?

Hai hợp đồng liên quan nhưng không đồng nhất:

- **equals:** Nếu không sửa thông tin dùng để so sánh, lặp lại phép so sánh trên cùng đối tượng phải cho cùng kết quả.
- **hashCode:** Trong một lần chạy Java, nếu không sửa thông tin được dùng trong equals, gọi hashCode nhiều lần trên cùng đối tượng phải cho cùng số nguyên. Hai đối tượng bằng nhau theo equals **bắt buộc** có cùng hashCode; hai đối tượng khác nhau vẫn có thể trùng hashCode.

Trong ví dụ BookCode, equals xét code và revision nên hashCode cũng dựa trên hai trường đó. Nếu chỉ override equals mà bỏ qua hashCode, các collection dựa trên băm như HashSet và HashMap có thể hoạt động sai với kỳ vọng “hai khóa bằng nhau là một khóa”.

Ngay cả khi equals và hashCode được viết đúng, **sửa trường dùng trong equals/hashCode của một đối tượng đã làm khóa trong HashMap hoặc đã thêm vào HashSet** có thể phá vỡ khả năng tìm lại phần tử vì bucket/giá trị băm lúc lưu không còn phù hợp. Đây là vấn đề khi sử dụng khóa mutable, không có nghĩa mọi thay đổi kết quả equals sau mutation đều vi phạm tính nhất quán.

Quy tắc thực hành: ưu tiên khóa bất biến; nếu bắt buộc sửa thuộc tính định danh của một mục đã nằm trong tập băm, thường cần gỡ mục theo khóa/trạng thái cũ trước, sửa, rồi đưa lại vào tập. Không nên kỳ vọng collection tự định vị lại phần tử sau khi trạng thái khóa đổi.

## 6. Những hiểu lầm dễ gặp

**Hiểu lầm 1: “equals từng trả true thì mãi mãi phải true.”**

Sai. Tiền đề của hợp đồng là dữ liệu dùng để so sánh không bị thay đổi. Nếu y đổi từ "ready" sang "busy", phép so sánh sau có thể là false.

**Hiểu lầm 2: “Không gán lại biến x, y nên dữ liệu không đổi.”**

Sai. Các biến vẫn tham chiếu cùng đối tượng nhưng trường mutable, phần tử mảng, phần tử List hoặc đối tượng con được dùng trong equals có thể đã đổi.

**Hiểu lầm 3: “Chỉ sửa một trường không được so sánh thì equals có thể đổi.”**

Sai. Nếu equals thực sự chỉ dựa trên code và revision, sửa description không được làm kết quả thay đổi. Nếu kết quả đổi, hãy kiểm tra triển khai có vô tình dựa vào description, trạng thái ngoài hay getter có tác dụng phụ hay không.

**Hiểu lầm 4: “Dùng final cho biến tham chiếu là đủ.”**

Sai. final ngăn gán tham chiếu trường sang đối tượng khác, nhưng nếu trường trỏ tới đối tượng mutable (chẳng hạn ArrayList), nội dung đối tượng đó vẫn có thể đổi. Muốn dữ liệu so sánh bất biến phải xét cả những đối tượng con có liên quan.

**Hiểu lầm 5: “equals nhất quán thì tự động an toàn khi nhiều luồng cùng truy cập.”**

Sai. Hợp đồng equals không thay thế cơ chế đồng bộ. Nếu một luồng sửa dữ liệu so sánh trong khi luồng khác gọi equals, việc đọc có thể quan sát trạng thái thay đổi/không đồng bộ. Hãy dùng dữ liệu bất biến hoặc thiết kế đồng bộ phù hợp khi cần truy cập đồng thời.

**Hiểu lầm 6: “Chỉ cần equals đúng, hashCode không quan trọng.”**

Sai với đối tượng làm khóa băm. Khi override equals, phải override hashCode tương thích. Việc làm này không tự động giải quyết vấn đề khóa mutable sau khi chèn vào HashSet/HashMap.

## 7. Cách kiểm tra một equals có nhất quán

Khi review hoặc viết kiểm thử, hãy trả lời lần lượt:

1. Phương thức quyết định bằng nhau dựa trên trường và giá trị dẫn xuất nào của **cả hai** đối tượng?
2. Những lần gọi lại với cùng giá trị của toàn bộ dữ liệu đó có cho cùng kết quả không? Thử cả cặp bằng nhau (true) lẫn khác nhau (false).
3. Có sự phụ thuộc không cần thiết vào thời gian, random, dữ liệu toàn cục, thứ tự gọi hoặc thao tác có tác dụng phụ không?
4. Nếu các đối tượng được phép thay đổi, khi **chỉ** sửa trường liên quan, kết quả mới có được giải thích bằng thay đổi dữ liệu không? Khi chỉ sửa trường không liên quan, kết quả có được giữ nguyên không?
5. Nếu lớp dùng làm khóa HashMap/HashSet, các trường tham gia equals có được giữ ổn định trong lúc khóa ở trong collection và hashCode có dùng thông tin tương ứng không?

Một phép kiểm tra nhanh cho đối tượng không bị sửa trong khoảng thử:

~~~java
boolean first = x.equals(y);
boolean second = x.equals(y);
if (first != second) {
    throw new AssertionError("equals không nhất quán khi dữ liệu so sánh giữ nguyên");
}
~~~

Đây là đoạn minh họa đặt trong ngữ cảnh đã có x và y; nó không chứng minh toàn bộ hợp đồng equals, và chỉ có ý nghĩa khi dữ liệu so sánh thực sự không thay đổi trong khoảng giữa hai lời gọi. Kiểm thử tốt nên lặp lại với nhiều cặp trạng thái, đồng thời kiểm tra phản xạ, đối xứng, bắc cầu, null và quan hệ equals/hashCode.

## 8. Ghi nhớ

**Điều kiện chính xác:** *Cùng hai đối tượng + không đổi thông tin mà equals dùng để so sánh → kết quả equals lặp lại phải giữ nguyên.*

Thay đổi dữ liệu so sánh có thể làm đổi kết quả một cách hợp lệ. Điều cần tránh là để equals cho kết quả thất thường khi dữ liệu giá trị vẫn nguyên vẹn. Ưu tiên cách so sánh xác định, không có tác dụng phụ; với khóa của cấu trúc băm, giữ các thuộc tính định danh ổn định trong suốt thời gian lưu khóa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-contract-006">So sánh với null phải trả về false</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh với null phải trả về false — hợp đồng equals(Object)

## 1. Quy tắc và phạm vi áp dụng

Trong Java, lớp java.lang.Object quy định rằng: **với mọi tham chiếu x khác null, biểu thức x.equals(null) phải trả về false**. Đây là một điều khoản chính thức trong hợp đồng của phương thức equals(Object), bên cạnh tính phản xạ, đối xứng, bắc cầu và nhất quán.

Ý nghĩa: null biểu thị sự vắng mặt của tham chiếu đến đối tượng; nó không phải một đối tượng có thể bằng với một đối tượng đang tồn tại. Dù lớp định nghĩa bình đẳng theo mã định danh, dữ liệu nghiệp vụ hay toàn bộ trạng thái, không được coi null là bằng một đối tượng khác null.

Cần phân biệt ba trường hợp:

| Biểu thức | Kết quả | Lý do |
| --- | --- | --- |
| x.equals(null), với x khác null và triển khai đúng | false | Điều khoản “non-nullity” của equals |
| x.equals(y), với x và y đều khác null | Phụ thuộc quy tắc bình đẳng của lớp | Kiểm tra dữ liệu hoặc danh tính theo hợp đồng |
| missing.equals(x), với missing == null | Ném NullPointerException | Không thể gọi phương thức thể hiện trên tham chiếu null |

Quy tắc chỉ nói về **đối số null khi đối tượng nhận lời gọi khác null**. Nó không có nghĩa là mọi phép so sánh có null đều phải trả về false: hai tham chiếu cùng null có thể được một hàm tiện ích xác định là “cùng vắng mặt”.

## 2. Triển khai equals(Object) đúng và an toàn

Trong Java 17+, phép kiểm tra kiểu bằng instanceof có một thuộc tính rất hữu ích: **null instanceof MộtKiểu luôn là false**. Vì vậy, kiểm tra kiểu trước khi đọc thuộc tính đối tượng vừa đáp ứng điều khoản null, vừa xử lý đối số không cùng kiểu.

~~~java
import java.util.Objects;

public class EqualsNullDemo {
    static final class Account {
        private final String number;

        Account(String number) {
            this.number = Objects.requireNonNull(number, "number");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Account account)) {
                return false; // Bao gồm other == null
            }
            return number.equals(account.number);
        }

        @Override
        public int hashCode() {
            return number.hashCode();
        }
    }

    public static void main(String[] args) {
        Account a = new Account("A1");
        Account same = new Account("A1");
        Account different = new Account("B2");
        Account missing = null;
        String status = null;

        System.out.println("a.equals(null): " + a.equals(null));
        System.out.println("a.equals(same): " + a.equals(same));
        System.out.println("a.equals(different): " + a.equals(different));
        System.out.println("Objects.equals(a, null): " + Objects.equals(a, null));
        System.out.println("Objects.equals(null, null): " + Objects.equals(null, null));
        System.out.println("constant.equals(status): " + "READY".equals(status));
        System.out.println("missing == null: " + (missing == null));
    }
}
~~~

Lưu với tên **EqualsNullDemo.java**, rồi thực thi trên JDK 17 trở lên:

~~~bash
javac EqualsNullDemo.java
java EqualsNullDemo
~~~

Kết quả chuẩn:

~~~text
a.equals(null): false
a.equals(same): true
a.equals(different): false
Objects.equals(a, null): false
Objects.equals(null, null): true
constant.equals(status): false
missing == null: true
~~~

Diễn giải theo đúng thứ tự xử lý của equals:

1. Khi other trỏ tới chính a, biểu thức this == other trả về true; điều này bảo đảm tính phản xạ.
2. Khi other là null, this == other là false; instanceof cũng trả về false, nên phương thức dừng an toàn.
3. Khi other là Account khác, việc so sánh number quyết định hai đối tượng có bằng nhau hay không.
4. Việc ghi đè hashCode là bắt buộc về mặt hợp đồng khi ghi đè equals: các đối tượng bằng nhau phải có cùng giá trị hashCode. Trong ví dụ, cùng number luôn dẫn tới cùng hashCode.

Vì Account là final nên không có lớp con gây bất đồng về quy tắc bình đẳng. Trong thiết kế cho phép kế thừa, cần chủ động xem xét chính sách so sánh kiểu và tính đối xứng: lựa chọn instanceof hay getClass() có thể ảnh hưởng tới tính tương thích của các lớp cha/con, nhưng **không thay đổi** quy tắc đối số null phải cho kết quả false.

Một cách triển khai khác cũng hợp lệ là kiểm tra null tường minh trước:

~~~java
@Override
public boolean equals(Object other) {
    if (other == null) {
        return false;
    }
    if (this == other) {
        return true;
    }
    if (!(other instanceof Account account)) {
        return false;
    }
    return number.equals(account.number);
}
~~~

Đây là **đoạn thay thế riêng phương thức equals trong lớp Account bên trên**, không phải chương trình Java độc lập. Nhánh other == null giúp người đọc dễ nhận thấy hợp đồng; trong trường hợp này nó không cần thiết về mặt kỹ thuật vì instanceof đã xử lý null. Nếu thuộc tính dùng làm khóa bình đẳng cho phép null, có thể dùng Objects.equals(number, account.number) thay cho number.equals(account.number) để so sánh thuộc tính an toàn.

## 3. equals(Object) khác với Objects.equals(a, b)

**Objects.equals(a, b)** là phương thức tiện ích tĩnh giúp so sánh hai tham chiếu có thể null. Về mặt hành vi, nó tương đương với:

~~~java
return a == b || (a != null && a.equals(b));
~~~

Đây là **biểu thức minh họa phần thân logic của phương thức tiện ích**, không phải phương thức tự chạy. Trường hợp hai đối số đều null, a == b trả về true. Trường hợp chỉ một đối số null, kết quả là false. Nếu a khác null và hai tham chiếu không trùng nhau, tiện ích gọi a.equals(b).

Vì thế:

- **new Account("A1").equals(null)** → false: đối tượng không bằng null.
- **Objects.equals(new Account("A1"), null)** → false: chỉ một tham chiếu null.
- **Objects.equals(null, null)** → true: cùng tham chiếu null, không gọi equals trên null.
- **"READY".equals(status)** khi status == null → false: mẫu so sánh an toàn khi chuỗi hằng ở bên trái.

Không có mâu thuẫn giữa việc Objects.equals(null, null) là true và hợp đồng của Object.equals(Object): điều khoản của hợp đồng chỉ xét lời gọi phương thức trên một đối tượng **khác null**.

Khi chỉ muốn kiểm tra tham chiếu có vắng mặt hay không, viết **value == null** hoặc **value != null** cho rõ ý. Khi cần so sánh bình đẳng giữa hai tham chiếu có thể null, dùng Objects.equals(left, right); khi chắc chắn đối tượng nhận khác null, có thể dùng receiver.equals(value).

## 4. Những lỗi thường gặp

### 4.1. Trả về true cho đối số null

~~~java
@Override
public boolean equals(Object other) {
    if (other == null) {
        return true; // Sai hợp đồng equals
    }
    // ... so sánh phần còn lại
}
~~~

Đây là **mã minh họa lỗi**, không phải ví dụ chạy độc lập. Dù lớp có coi các trường dữ liệu vắng mặt là tương đương, một đối tượng tồn tại vẫn không được bằng chính tham chiếu null. Hãy trả về false và chỉ so sánh dữ liệu sau khi đã kiểm tra kiểu.

### 4.2. Ép kiểu hoặc truy cập trường trước khi kiểm tra null

~~~java
@Override
public boolean equals(Object other) {
    Account account = (Account) other;
    return number.equals(account.number);
}
~~~

Đây cũng là **mã minh họa lỗi** bên trong Account. Khi other == null, phép ép kiểu vẫn cho null nhưng việc truy cập account.number sẽ gây NullPointerException. Khi other là đối tượng khác kiểu, phép ép kiểu có thể gây ClassCastException. Cả hai trường hợp đều cần được loại trừ bằng kiểm tra kiểu trước khi truy cập thuộc tính.

### 4.3. Chỉ nạp chồng equals(Account) thay vì ghi đè equals(Object)

~~~java
public boolean equals(Account other) {
    return number.equals(other.number);
}
~~~

Đoạn mã trên khai báo **một overload**, không ghi đè Object.equals(Object). Lời gọi a.equals(null) có thể được trình biên dịch chọn overload nhận Account (vì tham số cụ thể hơn Object), rồi gây NullPointerException; trong khi lời gọi qua tham chiếu kiểu Object lại đi theo phương thức khác. Hãy dùng đúng chữ ký **public boolean equals(Object other)** kèm **@Override** để trình biên dịch kiểm tra.

### 4.4. Nhầm lẫn với lời gọi trên null

~~~java
Account missing = null;
Account a = new Account("A1");
// missing.equals(a); // Ném NullPointerException nếu bỏ dấu chú thích
boolean safe = Objects.equals(missing, a); // false
~~~

Đây là **đoạn minh họa đặt trong ngữ cảnh có lớp Account**, không phải chương trình độc lập. Việc viết đúng equals trong Account không thể làm cho missing.equals(a) trở nên an toàn: Java phải tìm đối tượng nhận phương thức trước khi thực thi thân equals.

Ngoài ra, không nên viết **return Objects.equals(this, other)** bên trong chính equals(Object): khi this và other đều khác null, không cùng tham chiếu, phương thức tiện ích sẽ gọi lại this.equals(other), dẫn tới đệ quy vô hạn cho cặp không bằng nhau và cuối cùng là StackOverflowError.

## 5. Kiểm tra hợp đồng khi tự viết equals

Một phép kiểm tra tối thiểu, chạy trên mọi đối tượng khác null của lớp, là:

~~~java
Account a = new Account("A1");
if (a.equals(null)) {
    throw new AssertionError("equals(null) phải trả về false");
}
~~~

Đoạn kiểm tra này cần đặt trong phương thức main hoặc một phương thức kiểm thử có quyền truy cập Account. Với JUnit 5, ý tưởng tương đương là **assertFalse(a.equals(null))**.

Nên kiểm tra cùng lúc các thuộc tính khác của equals: a.equals(a) là true; hai Account có number giống nhau phải bằng nhau ở cả hai chiều và có hashCode bằng nhau; number khác nhau cho false; đầu vào không cùng kiểu cho false; lặp lại so sánh khi trạng thái không thay đổi phải cho kết quả ổn định. Đặc biệt, nếu dùng các đối tượng làm khóa HashMap hoặc phần tử HashSet, thuộc tính tham gia equals/hashCode nên ổn định trong suốt thời gian ở trong cấu trúc dữ liệu.

**Ghi nhớ:** Với mọi x khác null, **x.equals(null) == false**. Đặt kiểm tra null/kiểu ở đầu phương thức equals(Object), hoặc dùng instanceof một cách an toàn; chỉ dùng Objects.equals khi bạn cần so sánh hai giá trị mà mỗi bên có thể null.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-contract-007">Phân biệt x.equals(null) với gọi equals() trên tham chiếu null</a>

<details>
<summary>Xem chi tiết</summary>

# `x.equals(null)` khác gì gọi `equals()` trên tham chiếu `null`?

## 1. Hai vị trí `null` mang ý nghĩa khác nhau

Trong biểu thức `x.equals(y)`, `x` là **tham chiếu nhận lời gọi phương thức** (receiver), còn `y` là **đối số**. Java phải có một đối tượng làm receiver để thực thi phương thức thể hiện (instance method). Vì vậy cần phân biệt:

- **`x` khác `null`, đối số là `null`:** `x.equals(null)` gọi được phương thức `equals(Object)` trên đối tượng `x`. Theo hợp đồng của `Object.equals`, kết quả phải là `false` với mọi `x` khác `null`.
- **`x` là `null`:** `x.equals(null)` hoặc `x.equals(y)` đều ném `NullPointerException` trước khi thân phương thức `equals` của đối tượng nào đó có thể chạy. Lỗi xuất phát từ **receiver `x`**, không phải vì đối số là `null`.

Nhìn nhanh: `x.equals(null)` **không phải cách kiểm tra xem `x` có bằng `null` hay không**. Cách kiểm tra tham chiếu có `null` là `x == null` (hoặc `x != null`).

## 2. Liên hệ với hợp đồng `equals`

`equals` là phương thức thể hiện được khai báo trong `java.lang.Object`, có chữ ký `public boolean equals(Object obj)`. Một yêu cầu của hợp đồng là: với mọi tham chiếu `x` khác `null`, `x.equals(null)` phải trả về `false`.

Điều này là **quy tắc dành cho cách triển khai `equals`**, không có nghĩa JVM tự thay lời gọi thành `false` trong mọi hoàn cảnh. Lớp ghi đè `equals` sai hợp đồng, chẳng hạn truy cập `obj` khi chưa kiểm tra `null`, có thể tự gây `NullPointerException` ngay cả khi receiver `x` không `null`. Khi đó lỗi thuộc về bản cài đặt `equals`; không làm thay đổi yêu cầu của hợp đồng.

Trong khi đó, khi receiver `x == null`, Java không có đối tượng để gọi `equals`. Không có bất kỳ triển khai `equals` nào nhận được cuộc gọi này; kết quả là `NullPointerException`.

## 3. Ví dụ Java 17+ chạy độc lập

Lưu đoạn mã sau vào tệp `EqualsNullDemo.java`:

```java
import java.util.Objects;

public class EqualsNullDemo {
    public static void main(String[] args) {
        String present = "Java";
        String absent = null;

        System.out.println("present.equals(null): " + present.equals(null));
        System.out.println("absent == null: " + (absent == null));
        System.out.println("Objects.equals(absent, null): " + Objects.equals(absent, null));
        System.out.println("Objects.equals(present, absent): " + Objects.equals(present, absent));
        System.out.println("literal.equals(absent): " + "Java".equals(absent));

        try {
            absent.equals(null);
        } catch (NullPointerException ex) {
            System.out.println("absent.equals(null): NullPointerException");
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac EqualsNullDemo.java
java EqualsNullDemo
```

Kết quả chuẩn:

```text
present.equals(null): false
absent == null: true
Objects.equals(absent, null): true
Objects.equals(present, absent): false
literal.equals(absent): false
absent.equals(null): NullPointerException
```

Giải thích từng trường hợp:

- `present.equals(null)` chạy vì `present` trỏ tới một `String`; `String.equals` trả `false` với đối số `null`.
- `absent == null` chỉ so sánh tham chiếu, không gọi phương thức, nên trả `true` và không ném lỗi.
- `Objects.equals(absent, null)` trả `true` vì **cả hai đối số đều `null`**; khác với việc gọi phương thức trên `absent`.
- `Objects.equals(present, absent)` trả `false` vì một đối số có giá trị, một đối số `null`.
- `"Java".equals(absent)` trả `false` vì receiver là chuỗi hằng chắc chắn không `null`.
- `absent.equals(null)` ném `NullPointerException` do receiver `absent == null`; chương trình bắt lỗi chỉ để minh họa, không phải cách kiểm tra `null` nên dùng trong ứng dụng.

## 4. So sánh các cách viết thường gặp

Giả sử biến `x` có kiểu `String` và nhận một trong hai giá trị `"Java"` hoặc `null`:

| Biểu thức | `x = "Java"` | `x = null` | Ý nghĩa |
| --- | --- | --- | --- |
| `x == null` | `false` | `true` | Kiểm tra tham chiếu có `null` hay không |
| `x.equals(null)` | `false` | `NullPointerException` | Gọi `equals` trên receiver `x` với đối số `null` |
| `Objects.equals(x, null)` | `false` | `true` | So sánh hai giá trị có xử lý `null` |
| `"Java".equals(x)` | `true` | `false` | Gọi `equals` trên receiver luôn khác `null` |

`Objects.equals(a, b)` (trong `java.util.Objects`) có logic tương đương `a == b || (a != null && a.equals(b))`. Nhờ điều kiện `a != null`, phương thức xử lý an toàn khi một hoặc cả hai đối số `null`. Khi **cả hai khác `null`**, nó vẫn sử dụng `a.equals(b)`, vì vậy vẫn phụ thuộc vào tính đúng đắn của triển khai `equals`.

`x == null` kiểm tra riêng tham chiếu `null`; còn với hai đối tượng khác `null`, `==` so sánh chúng có phải cùng một đối tượng hay không, trong khi `equals` thường so sánh sự bằng nhau theo ý nghĩa của lớp.

## 5. Viết `equals` đúng khi đối số là `null`

Ví dụ dưới đây là **một phần của lớp** để minh họa cách ghi đè, không phải chương trình độc lập:

```java
final class Book {
    private final String isbn;

    Book(String isbn) {
        this.isbn = java.util.Objects.requireNonNull(isbn);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Book book)) return false;
        return isbn.equals(book.isbn);
    }

    @Override
    public int hashCode() {
        return isbn.hashCode();
    }
}
```

Biểu thức `other instanceof Book book` trả về `false` khi `other == null`, nên không cần gọi phương thức trên đối số `null`. Trường hợp đó, `bookInstance.equals(null)` trả `false`. Cài đặt `hashCode()` nhất quán với `equals()` cũng là một phần quan trọng của hợp đồng đối tượng, nhất là khi dùng trong `HashMap` hoặc `HashSet`.

Ngược lại, mã như `other.getClass()` hoặc `other.equals(this)` **trước khi kiểm tra `other == null`** có thể ném `NullPointerException` trong `bookInstance.equals(null)`, vi phạm hợp đồng.

## 6. Cách chọn biểu thức trong thực tế

- Muốn biết một tham chiếu có `null` không: dùng `x == null` hoặc `x != null`.
- Muốn so sánh hai giá trị có thể đều là `null`: dùng `Objects.equals(x, y)`; hai giá trị `null` được coi là bằng nhau trong phép so sánh này.
- Muốn gọi `equals` khi `x` có thể `null`: dùng `x != null && x.equals(y)` nếu muốn kết quả `false` khi `x == null`; lưu ý cách này khác `Objects.equals(x, y)` khi **cả hai cùng `null`**.
- So sánh với một giá trị cố định chắc chắn không `null`: có thể dùng `"Java".equals(x)` để tránh receiver `null`.
- Khi ghi đè `equals`, bảo đảm mọi đối tượng khác `null` đều trả `false` với đối số `null`, đồng thời duy trì các phần còn lại của hợp đồng `equals` và `hashCode`.

Điểm then chốt: **`null` ở vị trí đối số** là trường hợp `equals` phải xử lý khi receiver tồn tại; **`null` ở vị trí receiver** khiến việc gọi phương thức thể hiện thất bại với `NullPointerException`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="equals-contract-008">Chia các đối tượng thành những nhóm có cùng giá trị theo hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Chia các đối tượng thành những nhóm có cùng giá trị theo hợp đồng equals

## 1. Bài toán và ý nghĩa của “cùng giá trị”

Cho một tập hoặc danh sách đối tượng Java, ta muốn chia chúng thành các nhóm sao cho **hai đối tượng nằm trong cùng nhóm khi và chỉ khi chúng được xem là bằng nhau theo quy tắc đã chọn**. Đây là phép phân hoạch (partition): mỗi phần tử đầu vào thuộc đúng một nhóm; không bỏ sót, không đưa một phần tử vào hai nhóm. Một nhóm có thể chứa nhiều **instance** khác nhau nhưng có cùng **giá trị logic**.

Trong ngữ cảnh hợp đồng **equals**, quan hệ “cùng giá trị” thường được định nghĩa bằng **a.equals(b)**, không phải **a == b**:

- **==** trên các tham chiếu hỏi chúng có trỏ tới cùng một đối tượng trong bộ nhớ hay không.
- **equals(Object)** hỏi chúng có tương đương về giá trị logic hay không, theo cách lớp đó cài đặt.
- **hashCode()** hỗ trợ tìm kiếm và gom nhóm hiệu quả trong các cấu trúc dựa trên băm như **HashMap**, **LinkedHashMap** và **HashSet**; nó không tự định nghĩa thế nào là bằng nhau.

Ví dụ, hai bản ghi nhân viên khác instance nhưng cùng mã định danh nghiệp vụ có thể thuộc cùng nhóm, nếu lớp quy định **equals** chỉ xét mã nhân viên. Ngược lại, nếu nghiệp vụ coi cả mã và phiên bản là một phần của giá trị, chúng phải nằm ở nhóm khác nhau. **Không tồn tại một cách nhóm “đúng” cho mọi hệ thống**: trước tiên phải xác định chính xác tiêu chí bằng nhau.

### Điều kiện của một phép phân nhóm đúng

Đặt a ~ b khi a.equals(b) là true. Để chia thành những lớp tương đương nhất quán, quan hệ này cần thỏa:

1. **Phản xạ (reflexive):** a.equals(a) luôn true với a khác null.
2. **Đối xứng (symmetric):** nếu a.equals(b) thì b.equals(a).
3. **Bắc cầu (transitive):** nếu a.equals(b) và b.equals(c) thì a.equals(c).
4. **Nhất quán (consistent):** gọi lặp lại cho cùng hai đối tượng phải cho cùng kết quả chừng nào các thông tin dùng để so sánh chưa đổi.
5. **Không bằng null:** a.equals(null) luôn false khi a khác null.

Đây là những điều kiện chính của hợp đồng **Object.equals**. Nếu vi phạm tính đối xứng hoặc bắc cầu, khái niệm “nhóm” sẽ trở nên mâu thuẫn: phần tử có thể thuộc nhóm này khi so với đại diện A nhưng lại thuộc nhóm khác khi so với B. Nếu vi phạm tính nhất quán, kết quả phân nhóm có thể thay đổi ngay trong lúc xử lý.

**Quy tắc đi kèm:** nếu a.equals(b) là true thì a.hashCode() **bắt buộc** bằng b.hashCode(). Hai đối tượng có cùng mã băm **không bắt buộc** bằng nhau (va chạm băm là hợp lệ). Khi khóa không thay đổi về những trường tham gia **equals/hashCode**, giá trị hashCode cũng phải ổn định trong thời gian được dùng làm khóa.

## 2. Chọn đối tượng khóa: nhóm theo equals của phần tử hay theo khóa nghiệp vụ?

Có hai bài toán liên quan nhưng khác nhau:

| Cách nhóm | Tiêu chí cùng nhóm | Khi phù hợp |
| --- | --- | --- |
| Dùng chính đối tượng làm khóa | Hai phần tử có **equals** bằng true | **equals** của lớp đã diễn đạt đúng ý nghĩa “cùng giá trị” |
| Rút ra một khóa bất biến từ phần tử | Hai khóa có **equals** bằng true | Muốn nhóm theo một số trường cụ thể nhưng **không muốn đổi** định nghĩa equals của cả đối tượng |

Ví dụ: đối tượng đơn hàng có thể dùng **equals** theo mã đơn hàng, nhưng báo cáo lại muốn nhóm theo mã khách hàng. Lúc đó phải trích xuất mã khách hàng làm khóa; không sửa **Order.equals** chỉ để phục vụ một báo cáo.

Với khóa HashMap/LinkedHashMap, Java dùng cả **hashCode** lẫn **equals** để xác định khóa tương đương. Khi đưa hai khóa bằng nhau vào map, map giữ **một entry cho nhóm**. Giá trị của entry có thể là **List<T>** lưu đầy đủ mọi phần tử thuộc nhóm, kể cả các phần tử trùng giá trị.

## 3. Ví dụ đầy đủ: phân nhóm theo equals của đối tượng

Giả sử hệ thống quy định **nhân viên có cùng id thì là cùng một nhân viên về mặt logic**. Tên hiển thị không thuộc định danh: tên có thể thay đổi, nhưng các phiên bản mang cùng id cần được nhóm chung.

Tạo tệp **GroupByEqualsDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class GroupByEqualsDemo {
    static final class Employee {
        private final String id;
        private final String name;

        Employee(String id, String name) {
            this.id = Objects.requireNonNull(id, "id");
            this.name = Objects.requireNonNull(name, "name");
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Employee other)) {
                return false;
            }
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    public static void main(String[] args) {
        List<Employee> input = List.of(
            new Employee("E02", "An"),
            new Employee("E01", "Bình"),
            new Employee("E02", "An mới"),
            new Employee("E03", "Chi"),
            new Employee("E01", "Bình mới")
        );

        Map<Employee, List<Employee>> groups = new LinkedHashMap<>();

        for (Employee employee : input) {
            groups.computeIfAbsent(employee, key -> new ArrayList<>())
                  .add(employee);
        }

        for (Map.Entry<Employee, List<Employee>> entry : groups.entrySet()) {
            String names = entry.getValue().stream()
                .map(employee -> employee.name)
                .collect(Collectors.joining(", "));

            System.out.println(entry.getKey().id + " -> [" + names + "]");
        }
    }
}
~~~

Biên dịch và chạy với Java 17 trở lên:

~~~bash
javac GroupByEqualsDemo.java
java GroupByEqualsDemo
~~~

Kết quả chính xác:

~~~text
E02 -> [An, An mới]
E01 -> [Bình, Bình mới]
E03 -> [Chi]
~~~

**Giải thích từng bước:**

1. Hai instance có **id = "E02"** trả về true khi so bằng **equals**, đồng thời có cùng **hashCode**. Chúng trở thành **một khóa logic** trong map.
2. Với nhân viên đầu tiên của một nhóm, **computeIfAbsent** tạo ArrayList mới. Với nhân viên cùng id đến sau, nó trả lại danh sách đã có và thêm phần tử mới.
3. **List<Employee>** lưu **từng lần xuất hiện**, nên nhóm E02 gồm hai đối tượng, không bị mất bản ghi “An mới”.
4. **LinkedHashMap** giữ thứ tự các khóa theo **lần chèn đầu tiên**: E02, rồi E01, rồi E03. Mỗi danh sách giữ thứ tự các phần tử được thêm vào.
5. Đối tượng khóa đại diện của nhóm là lần chèn đầu tiên. Vì vậy, không được tùy tiện hiểu khóa đại diện là bản ghi mới nhất hay bản ghi “chuẩn”; nếu cần chọn bản ghi đại diện theo thời điểm/phiên bản, hãy xử lý quy tắc đó riêng.

**Vì sao cài đặt Employee như trên?** Lớp được khai báo **final** để không phát sinh vấn đề đối xứng do subclass thêm tiêu chí bằng nhau; **id** là **final** và khác null để định danh ổn định. Tên không tham gia cả hai phương thức. Tùy miền nghiệp vụ, có thể cần khóa tổ hợp (ví dụ tenantId + employeeId) nhằm tránh coi hai mã trùng ở hai tenant là một.

### Biến thể bằng Stream

Với danh sách **input** và lớp **Employee** trong ví dụ trên, có thể thay vòng lặp bằng:

~~~java
Map<Employee, List<Employee>> groups = input.stream().collect(
    Collectors.groupingBy(
        employee -> employee,
        LinkedHashMap::new,
        Collectors.toList()
    )
);
~~~

Đây là **đoạn mã thay thế bên trong main**, không phải một chương trình Java độc lập. **groupingBy** lấy chính phần tử làm khóa nên vẫn dựa vào **equals/hashCode** của Employee. Truyền **LinkedHashMap::new** là chủ ý để giữ thứ tự xuất hiện của các nhóm khi chạy stream tuần tự; **Collectors.groupingBy** một tham số không hứa hẹn kiểu map/thứ tự duyệt khóa cụ thể. Danh sách kết quả vẫn chứa tất cả phần tử trong nhóm.

## 4. Ví dụ đầy đủ: nhóm theo khóa giá trị thay vì sửa equals của đối tượng

Giả sử **Customer** là một bản ghi đầy đủ, nhưng báo cáo phải nhóm theo **(tenantId, customerCode)**. Quy tắc nghiệp vụ của báo cáo nói rõ rằng khoảng trắng ngoài cùng và chữ hoa/thường trong **customerCode** không mang ý nghĩa. Ta tạo **CustomerKey** chỉ chứa hai thành phần khóa đã chuẩn hóa, còn Customer giữ nguyên định nghĩa giá trị đầy đủ của record.

Tạo tệp **GroupByBusinessKeyDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupByBusinessKeyDemo {
    record Customer(String tenantId, String customerCode, String displayName) {}

    record CustomerKey(String tenantId, String normalizedCode) {}

    public static void main(String[] args) {
        List<Customer> input = List.of(
            new Customer("A", " c123 ", "Lan"),
            new Customer("B", "C123", "Hà"),
            new Customer("A", "C123", "Lan mới"),
            new Customer("A", "x7", "Minh")
        );

        Map<CustomerKey, List<Customer>> groups = new LinkedHashMap<>();

        for (Customer customer : input) {
            String normalized = customer.customerCode()
                .strip()
                .toUpperCase(Locale.ROOT);
            CustomerKey key = new CustomerKey(customer.tenantId(), normalized);

            groups.computeIfAbsent(key, ignored -> new ArrayList<>())
                  .add(customer);
        }

        for (Map.Entry<CustomerKey, List<Customer>> entry : groups.entrySet()) {
            CustomerKey key = entry.getKey();
            System.out.printf("%s/%s -> %d%n",
                key.tenantId(), key.normalizedCode(), entry.getValue().size());
        }
    }
}
~~~

Chạy:

~~~bash
javac GroupByBusinessKeyDemo.java
java GroupByBusinessKeyDemo
~~~

Kết quả:

~~~text
A/C123 -> 2
B/C123 -> 1
A/X7 -> 1
~~~

**Giải thích:** Hai Customer của tenant A có code khác cách viết nhưng sau chuẩn hóa trở thành cùng **CustomerKey("A", "C123")**; khách hàng tenant B vẫn ở nhóm riêng. **record** tự cung cấp equals/hashCode dựa trên **tất cả các component**, nên key tổ hợp ngắn gọn và ít lỗi hơn viết thủ công. Chuẩn hóa chỉ đúng khi nghiệp vụ cho phép; không mặc định đổi chữ hoa/thường cho mọi mã, email, tên hoặc định danh.

Không nên tạo record chứa những trường thay đổi rồi dùng nguyên record đó làm khóa nhóm nếu mục đích chỉ là nhóm theo ID. **record Customer** ở trên có equals theo cả tenantId, customerCode và displayName, nên nhóm theo chính Customer sẽ cho kết quả khác nhóm theo CustomerKey.

## 5. Các cách biểu diễn kết quả và mức độ phù hợp

**Map<K, List<T>>** là biểu diễn thuận tiện nhất cho bài toán giữ toàn bộ thành viên trong từng nhóm: khóa đại diện cho một giá trị logic và list giữ những phần tử tương đương. Có thể dùng **LinkedHashMap** nếu muốn các nhóm được duyệt theo thứ tự xuất hiện, **HashMap** nếu không cần thứ tự, hoặc cấu trúc khác khi yêu cầu thay đổi.

Các biến thể khác phục vụ mục đích khác:

- **Set<T>** (ví dụ **LinkedHashSet**) chỉ giữ một phần tử đại diện cho mỗi giá trị logic, hữu ích để loại trùng. Nó **không giữ danh sách đầy đủ** các phần tử của nhóm.
- **Map<K, Long>** hoặc **Map<K, Integer>** đếm tần suất theo từng giá trị khóa, nhưng không giữ các đối tượng thành viên.
- **List<List<T>>** giữ từng nhóm mà không cần đối tượng khóa map; có thể xây bằng cách so phần tử với đại diện mỗi nhóm. Cách quét tuyến tính này thường tốn **O(n²)** phép so sánh ở trường hợp xấu, trong khi gom nhóm bằng hash map thường gần **O(n)** trung bình nếu hash phân phối tốt.
- **TreeMap** hoặc **TreeSet** xác định trùng khóa theo **Comparator.compare(a, b) == 0** hay **compareTo(a, b) == 0**, không trực tiếp theo equals. Nếu comparator không nhất quán với equals, kết quả nhóm có thể khác nhóm theo hợp đồng equals. Chỉ dùng khi đó chính là quy tắc mong muốn.

Nếu chọn **Map<K, List<T>>**, cần quyết định rõ liệu output phải giữ **thứ tự đầu vào**, **thứ tự sắp xếp theo khóa**, hay không quan tâm thứ tự. Bản thân việc hai đối tượng bằng nhau không hàm ý một thứ tự cụ thể.

## 6. Lỗi thường gặp và vì sao nhóm bị sai

### 6.1. Override equals nhưng quên hashCode

~~~java
@Override
public boolean equals(Object obj) {
    // Logic so sánh theo id
    // (chỉ minh họa lỗi thiết kế, không phải chương trình độc lập)
}
~~~

Đây là **minh họa bằng chú thích**, không phải hàm có thể biên dịch: nếu lớp override equals theo id nhưng để hashCode kế thừa từ Object, hai đối tượng bằng nhau có thể có mã băm khác nhau. **HashMap/HashSet** có thể lưu chúng ở các vị trí khác nhau và tạo ra **hai nhóm sai**. Luôn override **equals** và **hashCode** cùng nhau, dùng cùng tập thuộc tính định danh.

### 6.2. Dùng == để kiểm tra giá trị của object

Hai object độc lập **new Employee("E02", ...)** thường có tham chiếu khác nhau. **==** trả false cho hai tham chiếu đó dù equals trả true. Với String và các wrapper, hiện tượng cache/intern càng khiến dùng == có vẻ “thỉnh thoảng đúng”, vì vậy phải dùng đúng phép so sánh theo ý nghĩa mong muốn.

### 6.3. Dùng trường có thể thay đổi làm khóa hash

Nếu id (hoặc một component tham gia hashCode) thay đổi **sau khi** đã chèn đối tượng vào HashMap, entry vẫn nằm ở vị trí tổ chức theo hash cũ; tra cứu/gom nhóm sau đó có thể thất bại hoặc bất nhất. Nên ưu tiên **khóa bất biến** (record key, String ID không đổi, final fields); nếu cần đổi định danh, xây lại map bằng khóa mới.

### 6.4. Chỉ dựa vào hashCode để kết luận bằng nhau

**a.hashCode() == b.hashCode()** không đủ chứng minh **a.equals(b)**. Va chạm hash xảy ra hợp lệ, và HashMap vẫn gọi equals để phân biệt các khóa có cùng hash.

### 6.5. Nhầm equals với compareTo / Comparator

~~~java
new java.math.BigDecimal("1.0").equals(
    new java.math.BigDecimal("1.00"))  // false
~~~

Với BigDecimal, **compareTo** cho kết quả 0 giữa hai giá trị này nhưng **equals** trả false vì scale khác nhau. Vì thế nhóm bằng **HashMap<BigDecimal, ...>** có thể tách chúng; nhóm bằng **TreeMap** theo thứ tự tự nhiên có thể gộp chúng. Nếu nghiệp vụ chỉ xét giá trị số, cần quyết định và biểu diễn khóa được chuẩn hóa một cách nhất quán, chẳng hạn cân nhắc **stripTrailingZeros()** cùng cách xử lý giá trị zero và giới hạn miền dữ liệu.

### 6.6. Nhầm equals của array với equals theo phần tử

Mảng không override Object.equals để so nội dung: **new int[]{1,2}.equals(new int[]{1,2})** là false. Nếu khóa là mảng, muốn so giá trị theo phần tử phải dùng **Arrays.equals** (mảng một chiều) hoặc **Arrays.deepEquals** (mảng lồng nhau), đồng thời xây khóa có **hashCode** tương thích (Arrays.hashCode/deepHashCode). Cách thuận tiện khác là chuyển về cấu trúc giá trị bất biến thích hợp.

### 6.7. Dùng quan hệ “gần giống” nhưng không bắc cầu

Ví dụ quy tắc **|a - b| < 2** cho các số 1, 2, 3: 1 gần 2 và 2 gần 3, nhưng 1 không gần 3. Đây không phải quan hệ tương đương; không nên đưa trực tiếp vào **equals** để gom nhóm theo từng lớp tương đương. Bài toán phân cụm theo khoảng cách/độ gần phải dùng thuật toán và quy tắc nhóm riêng.

### 6.8. Cạm bẫy với null, entity và kế thừa

- **a.equals(null)** phải false; nếu a có thể null thì không được gọi trực tiếp **a.equals(...)**. Có thể dùng **Objects.equals(a, b)** để so sánh null-safe. **List.of** không cho phép phần tử null; **Collectors.groupingBy** của JDK từ chối khóa phân nhóm null. Nếu dữ liệu có null, cần đặt chính sách loại bỏ, tách riêng hay dùng sentinel có ý nghĩa.
- Các entity có ID được tạo sau khi lưu (ví dụ entity chưa persisted) không nên tự động coi mọi ID null là cùng một giá trị: nhiều entity mới có thể là các thực thể khác nhau. Cần thiết kế equals/hashCode đúng vòng đời định danh.
- So sánh giữa lớp cha và lớp con có thêm trạng thái dễ làm hỏng đối xứng hoặc bắc cầu; cân nhắc lớp final, record, hoặc quy tắc kế thừa được thiết kế chặt chẽ.

## 7. Kiểm chứng hợp đồng trước khi tin kết quả nhóm

Với mỗi mẫu **a, b, c**, hãy kiểm tra bằng ví dụ cụ thể:

1. **a.equals(a)** là true; **a.equals(null)** là false.
2. **a.equals(b)** đúng bằng **b.equals(a)**.
3. Nếu **a.equals(b)** và **b.equals(c)** đều true, thì **a.equals(c)** cũng true.
4. Nếu **a.equals(b)** là true, **a.hashCode() == b.hashCode()** cũng phải true.
5. Nếu **a** và **b** được coi là khác giá trị, phép nhóm tạo hai nhóm riêng khi cần; nếu bằng nhau, chúng phải ở cùng một nhóm.
6. Thêm phần tử trùng hoặc xáo thứ tự đầu vào không được làm thay đổi **quan hệ thành viên cùng nhóm**; nếu dùng LinkedHashMap thì thứ tự hiển thị nhóm có thể đổi theo thứ tự xuất hiện.
7. Tổng số **phần tử trong tất cả danh sách nhóm** phải bằng số phần tử đầu vào đã được giữ lại sau chính sách lọc; số nhóm phải bằng số giá trị khóa phân biệt.

Có thể thử thêm ba instance với cùng khóa logic, các khóa có hash collision, khóa ở hai tenant khác nhau, null, và các giá trị được chuẩn hóa. Trong kiểm thử tự động, sử dụng các assertion rõ ràng hoặc thư viện kiểm tra equals/hashCode nếu dự án đã có. Một vài ví dụ chạy đúng không chứng minh đầy đủ hợp đồng với mọi đầu vào, nhưng giúp phát hiện nhiều lỗi thiết kế phổ biến.

## 8. Quy trình áp dụng vào dữ liệu thật

1. **Viết rõ định nghĩa “cùng giá trị”** của trường hợp nghiệp vụ: cùng ID, cùng tổ hợp tenant + ID, hay trùng toàn bộ giá trị.
2. **Chọn khóa:** dùng chính đối tượng nếu equals của lớp phù hợp; nếu không, trích xuất khóa bất biến chuyên biệt. Đảm bảo equals/hashCode của khóa tuân hợp đồng.
3. **Chuẩn hóa có chủ đích**, trước khi gom nhóm và chỉ đối với những khác biệt nghiệp vụ muốn bỏ qua (ví dụ khoảng trắng bao ngoài, cách viết hoa/thường).
4. **Chọn dạng kết quả:** danh sách thành viên, số lần xuất hiện hay chỉ tập giá trị duy nhất; chọn cấu trúc map/set phù hợp với yêu cầu thứ tự.
5. **Thực hiện phân nhóm và kiểm tra:** mỗi phần tử vào đúng một nhóm; không thất lạc phần tử, không gộp nhầm các khóa khác nhau; đặc biệt kiểm chứng khóa null và khóa biến đổi.
6. **Giữ hợp đồng ổn định trong thời gian dùng khóa:** không thay đổi trường định danh của khóa khi đang lưu trong map.

**Ghi nhớ:** khả năng chia đối tượng thành các nhóm cùng giá trị dựa trên **một quan hệ tương đương hợp lệ**. Trong cấu trúc hash, **equals quyết định bằng nhau** và **hashCode phải nhất quán với equals**. Khi yêu cầu nhóm chỉ theo một phần dữ liệu, hãy thiết kế **khóa giá trị riêng** thay vì làm sai ý nghĩa equals của đối tượng gốc.


</details>

- [Quay lại đầu trang](#back-to-top)
