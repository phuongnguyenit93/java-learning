# Định danh đối tượng và bằng nhau về mặt logic

## <a id="identity-vs-equality">Định danh đối tượng và bằng nhau về mặt logic</a>

### KHÁI NIỆM

**Định danh (identity)** trả lời câu hỏi:

> Hai biến tham chiếu có đang trỏ tới đúng cùng một đối tượng hay không?

**Bằng nhau về mặt logic (logical equality)** trả lời một câu hỏi khác:

> Hai đối tượng khác nhau có nên được xem là cùng một giá trị hoặc cùng một thực thể theo quy tắc nghiệp vụ hay không?

Hai câu hỏi này không loại trừ nhau: hai biến cùng trỏ đến một đối tượng thì đối tượng ấy cũng phải bằng chính nó theo một cách triển khai `equals` tuân thủ quy ước Java. Nhưng **hai đối tượng khác nhau vẫn có thể bằng nhau về mặt logic**. Java giúp ta diễn đạt hai câu hỏi bằng các cơ chế khác nhau:

```text
==
→ với tham chiếu: kiểm tra định danh

equals(...)
→ phương thức có thể ghi đè để định nghĩa phép bằng nhau về mặt logic;
  nếu chưa ghi đè, Object.equals mặc định vẫn so theo định danh
```

### VÌ SAO PHẢI PHÂN BIỆT?

Nếu không phân biệt hai khái niệm này, ta dễ viết mã nguồn đúng cú pháp nhưng sai ý nghĩa nghiệp vụ. Giả sử `UserId` biểu diễn mã người dùng; mỗi lệnh `new` dưới đây tạo một đối tượng mới (đoạn lệnh minh họa nằm trong một phương thức):

Hai đối tượng mà `a` và `b` tham chiếu **có định danh khác nhau** nhưng mang cùng chuỗi mã `U-100`. Biến `same` dùng lại đối tượng mà `a` đang tham chiếu:

```java
UserId a = new UserId("U-100");
UserId b = new UserId("U-100");
UserId same = a;
```

Từ cách tạo biến, ta xác định được kết quả của phép so sánh định danh ngay cả khi chưa biết lớp `UserId` định nghĩa `equals` ra sao:

```text
a == b     → false (hai lần new tạo hai đối tượng)
a == same  → true  (same giữ lại tham chiếu của a)

a.equals(b) → phụ thuộc quy tắc equals mà kiểu UserId cung cấp
```

Nếu nghiệp vụ xem hai mã `U-100` là cùng một giá trị, `a.equals(b)` **nên trả về `true`** sau khi lớp được thiết kế với quy tắc bằng nhau phù hợp; không thể suy ra kết quả đó chỉ từ dữ liệu giống nhau. Đây là khác biệt quan trọng khi cần phát hiện giá trị trùng lặp: `HashSet`/`HashMap` dựa trên `hashCode` và `equals`, còn `TreeSet` dựa trên thứ tự do `Comparable` hoặc `Comparator` xác định. Các cấu trúc ấy không tự đoán ý nghĩa nghiệp vụ của chữ “giống nhau”.

### THAM CHIẾU, ĐỐI TƯỢNG VÀ GIÁ TRỊ

Một biến tham chiếu không phải chính đối tượng mà là nơi giữ tham chiếu đến đối tượng (hoặc `null`, khi không trỏ đến đối tượng nào). Còn chuỗi `U-100` là **dữ liệu mang ý nghĩa nghiệp vụ**. Có thể hình dung ba biến ở ví dụ trên:

```text
UserId a ───────┐
                ├──> UserId("U-100")
UserId same ────┘

UserId b ──────────> UserId("U-100")
```

`a` và `same` cùng trỏ tới một đối tượng; `a` và `b` trỏ tới hai đối tượng khác nhau dù chúng chứa cùng mã. Nhờ vậy, ta không nhầm **tham chiếu**, **đối tượng được tham chiếu** và **giá trị mà đối tượng biểu diễn**.

## <a id="reference-equality">So sánh tham chiếu bằng ==</a>

Với hai toán hạng kiểu tham chiếu tương thích, `==` kiểm tra chúng có cùng trỏ tới một đối tượng, hoặc cả hai cùng `null`, hay không. Nó không đọc các trường dữ liệu hay tự gọi `equals`. Riêng `==` với kiểu nguyên thủy như `int` so sánh **giá trị nguyên thủy**; quy tắc định danh ở đây chỉ áp dụng cho tham chiếu.

Ví dụ các dòng sau nằm trong một phương thức Java:

```java
String a = new String("java");
String b = new String("java");
String same = a;

System.out.println(a == b);       // false
System.out.println(a == same);    // true
System.out.println(a.equals(b));  // true
```

Hai lệnh `new String("java")` tạo hai đối tượng `String` riêng biệt nên `a == b` cho `false`. `same` nhận đúng tham chiếu của `a` nên `a == same` cho `true`. Dòng cuối cho `true` vì `String` **ghi đè `equals` để so nội dung ký tự**; ba kết quả này không mâu thuẫn nhau.

`null` là giá trị tham chiếu đặc biệt, **không phải một đối tượng**. Vẫn trong một phương thức có biến `a` khác `null` ở trên:

```java
String missing = null;
String alsoMissing = null;

System.out.println(missing == alsoMissing);                  // true
System.out.println(missing == a);                            // false
System.out.println(a.equals(null));                          // false
System.out.println(java.util.Objects.equals(missing, null)); // true
System.out.println(java.util.Objects.equals(missing, a));    // false
```

Phép `==` kiểm tra được `null` mà không gọi phương thức. Ngược lại, gọi `missing.equals(a)` sẽ ném `NullPointerException` vì không có đối tượng nhận lời gọi. `Objects.equals(x, y)` là cách so sánh có xử lý `null`: hai giá trị `null` trả về `true`, chỉ một giá trị `null` trả về `false`; khi `x` khác `null`, nó dùng `x.equals(y)`. Điều này không biến `null` thành một đối tượng có định danh hay giá trị nghiệp vụ.

### `Object.equals()` MẶC ĐỊNH LÀ GÌ?

Nếu lớp của bạn **không ghi đè (`override`) `equals`**, phần triển khai kế thừa từ `Object` sử dụng phép bằng nhau theo định danh.

Chương trình Java độc lập sau dùng phiên bản `UserId` thông thường, chưa có quy tắc bằng nhau theo mã:

```java
public class DefaultEqualsDemo {
    static final class UserId {
        private final String value;

        UserId(String value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        UserId a = new UserId("U-100");
        UserId b = new UserId("U-100");
        UserId same = a;

        System.out.println(a == b);         // false
        System.out.println(a == same);      // true
        System.out.println(a.equals(b));    // false: Object.equals mặc định
        System.out.println(a.equals(same)); // true
    }
}
```

`a` và `b` cùng chứa `value = "U-100"`, nhưng `Object.equals` mặc định kiểm tra định danh như `==`, nên dòng thứ ba vẫn in `false`. Dòng cuối in `true` vì `same` là bí danh của `a`. Đây là lý do một lớp thông thường cần ghi đè `equals` nếu nghiệp vụ đòi hỏi phép bằng nhau theo giá trị: **Java không tự suy ra trường nào tạo nên ý nghĩa “bằng nhau” của lớp**.

### KHI NÀO `==` LÀ ĐÚNG?

`==` phù hợp khi ta thật sự quan tâm định danh, ví dụ:

- hằng enum;
- đối tượng đơn nhất (`singleton`);
- đối tượng đánh dấu (`sentinel`) dùng làm dấu hiệu đặc biệt;
- kiểm tra hai tham chiếu có đang chia sẻ đúng cùng một đối tượng hay không.

Nó thường không phù hợp để so **nội dung** của đối tượng được nhận diện theo giá trị như `String`, giá trị tiền tệ hoặc đối tượng giá trị dùng làm ID.

> Lưu ý: Java dùng chung đối tượng cho các chuỗi viết trực tiếp trong mã nguồn (string literal) và biểu thức hằng chuỗi được xác định lúc biên dịch, thông qua **String pool**. Vì vậy, hai literal cùng nội dung có thể cho `==` là `true`. Chuỗi tạo bằng `new String(...)` vẫn là một đối tượng mới; chuỗi tạo lúc chạy cũng không mặc nhiên được dùng chung. Do đó, **không dựa vào `==` để so nội dung `String`**.

## <a id="value-object-equality">Phép bằng nhau của đối tượng giá trị</a>

**Đối tượng giá trị (value object)** thường được nhận diện bởi **giá trị có ý nghĩa**, không phải bởi định danh của đối tượng.

Ví dụ hai đối tượng `Money(100, "USD")` có thể được xem là bằng nhau nếu số tiền và đơn vị tiền tệ giống nhau, dù chúng được tạo ở hai thời điểm khác nhau.

Để nhìn thấy kết quả của `UserId` khi **bằng nhau được định nghĩa theo `value`**, ta dùng một chương trình độc lập khác. Trong Java 21, khai báo `record UserId(String value)` tự cung cấp `equals` và `hashCode` dựa trên thành phần `value`; đây là **một phiên bản khác** với lớp `UserId` chưa ghi đè `equals` ở ví dụ trước:

```java
public class ValueEqualityDemo {
    record UserId(String value) {}

    public static void main(String[] args) {
        UserId a = new UserId("U-100");
        UserId b = new UserId("U-100");
        UserId other = new UserId("U-200");
        UserId same = a;

        System.out.println(a == b);
        System.out.println(a == same);
        System.out.println(a.equals(b));
        System.out.println(a.equals(other));
        System.out.println(a.equals(null));
    }
}
```

Kết quả theo đúng thứ tự các lệnh in:

```text
false
true
true
false
false
```

Theo từng bước: `new` tạo `a` và `b` là hai đối tượng nên `a == b` vẫn là `false`; gán `same = a` khiến dòng thứ hai là `true`. Nhưng khi gọi `a.equals(b)`, phương thức do `record` cung cấp xét cùng kiểu `UserId` và so thành phần `value`: hai chuỗi đều là `U-100` nên dòng thứ ba là `true`. `other` mang `U-200` nên dòng tiếp theo là `false`, còn `null` không phải một `UserId` nên dòng cuối cũng `false`. **Thay quy tắc bằng nhau không làm hai đối tượng trở thành cùng một đối tượng.**

Ví dụ chỉ có một thành phần `String` bất biến nên phù hợp để minh họa. Một `record` mặc định xét **tất cả** các thành phần của nó và không tự kiểm tra mã người dùng có hợp lệ hay không; lớp thông thường cũng có thể định nghĩa `equals` theo `value` nếu thiết kế yêu cầu. Cách tự cài đặt và kiểm chứng hợp đồng `equals` được học ở chương sau.

### ĐỐI TƯỢNG GIÁ TRỊ KHÁC THỰC THỂ Ở ĐÂU?

Một mô hình tư duy hữu ích:

```text
Đối tượng giá trị
→ "nó mang giá trị gì?"

Thực thể
→ "nó là thực thể nào?"
```

Ví dụ `Money(100, "USD")` thường so sánh bằng nhau theo số tiền **và** đơn vị tiền tệ. Một `User` là thực thể có thể dùng mã định danh nghiệp vụ ổn định như `userId` để xác định bằng nhau, chứ không nhất thiết theo toàn bộ trạng thái có thể thay đổi như tên hiển thị hoặc địa chỉ. Điều này phụ thuộc quy tắc của từng mô hình, không phải mọi thực thể trong mọi ứng dụng đều bắt buộc ghi đè `equals` theo ID.

Điều quan trọng là **chọn quy tắc bằng nhau theo ý nghĩa nghiệp vụ**, không phải máy móc đưa mọi trường dữ liệu vào công cụ sinh mã của IDE.

### TRẠNG THÁI DÙNG ĐỂ XÁC ĐỊNH BẰNG NHAU NÊN ỔN ĐỊNH

Một thiết kế phép bằng nhau tốt thường cần:

- dựa trên các thành phần thực sự định nghĩa giá trị hoặc định danh nghiệp vụ;
- giữ các tính chất phản xạ, đối xứng, bắc cầu và nhất quán;
- giữ hành vi của `hashCode` nhất quán với phép bằng nhau, để các đối tượng được xem là bằng nhau luôn tạo cùng mã băm (chiều ngược lại không được bảo đảm);
- tránh thay đổi các trường tham gia `equals`/`hashCode` khi đối tượng đang làm khóa `HashMap` hoặc phần tử `HashSet`; với `TreeMap`/`TreeSet`, cần giữ ổn định **các trường quyết định thứ tự so sánh** theo `Comparable`/`Comparator` (chúng có thể khác trường dùng trong `equals`).

Ví dụ nếu `UserId` được định nghĩa chỉ bởi `value`, `equals` và `hashCode` cũng nên dựa trên đúng `value` đó. Nếu `value` thay đổi sau khi `UserId` làm khóa `HashMap`, phép tra cứu có thể không tìm lại được khóa. Tương tự, thay đổi trường mà `TreeSet` dùng để xác định thứ tự có thể khiến việc tìm kiếm phần tử không còn đúng như mong đợi, ngay cả khi `equals` không dựa trên trường đó. Lớp `record` với thành phần là `String` tránh việc thay đổi trực tiếp chính thành phần ấy, nhưng `record` không tự làm bất biến mọi đối tượng có thể thay đổi được mà nó tham chiếu tới.

### CHUYỂN TIẾP

Ta đã biết **phép bằng nhau về mặt logic là quyết định của lớp và nghiệp vụ**, không phải của định danh tham chiếu. Chương tiếp theo đi vào chính xác `equals` phải giữ những quy tắc nào để thư viện Java có thể tin quyết định đó.
