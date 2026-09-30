# Định danh đối tượng và bằng nhau về mặt logic

## <a id="identity-vs-equality">Định danh đối tượng và bằng nhau về mặt logic</a>

### KHÁI NIỆM

**Định danh (identity)** trả lời câu hỏi:

> Hai biến tham chiếu có đang trỏ tới đúng cùng một đối tượng hay không?

**Bằng nhau về mặt logic (logical equality)** trả lời một câu hỏi khác:

> Hai đối tượng khác nhau có nên được xem là cùng một giá trị hoặc cùng một thực thể theo quy tắc nghiệp vụ hay không?

Java dùng hai cơ chế khác nhau:

```text
==
→ với tham chiếu: kiểm tra định danh

equals(...)
→ phương thức có thể ghi đè để định nghĩa phép bằng nhau về mặt logic
```

### VÌ SAO PHẢI PHÂN BIỆT?

Nếu không phân biệt hai khái niệm này, ta dễ viết mã nguồn đúng cú pháp nhưng sai ý nghĩa nghiệp vụ.

Ví dụ hai đối tượng sau là **hai đối tượng riêng biệt, có định danh khác nhau**, nhưng có thể đại diện cho cùng một `UserId`:

```java
UserId a = new UserId("U-100");
UserId b = new UserId("U-100");
```

Ta có hai câu hỏi độc lập:

```text
a và b có phải đúng cùng đối tượng không?
→ định danh

a và b có đại diện cùng mã người dùng không?
→ bằng nhau về mặt logic
```

Các cấu trúc dữ liệu như `HashSet`, `HashMap` hoặc `TreeSet` không thể tự đoán nghiệp vụ của bạn muốn hiểu chữ “giống nhau” theo nghĩa nào. Chúng dựa vào các quy ước mà kiểu dữ liệu hoặc đoạn mã sử dụng cung cấp.

### THAM CHIẾU, ĐỐI TƯỢNG VÀ GIÁ TRỊ

Một biến tham chiếu không phải chính đối tượng. Có thể hình dung:

```text
UserId a ───────┐
                ├──> UserId("U-100")
UserId same ────┘

UserId b ──────────> UserId("U-100")
```

`a` và `same` có cùng định danh vì cùng trỏ tới một đối tượng. `a` và `b` có định danh khác nhau dù dữ liệu giống nhau.

## <a id="reference-equality">So sánh tham chiếu bằng ==</a>

Với tham chiếu, `==` chỉ kiểm tra xem hai tham chiếu có cùng trỏ tới một đối tượng, hoặc cả hai cùng `null`, hay không.

```java
String a = new String("java");
String b = new String("java");
String same = a;

System.out.println(a == b);       // false
System.out.println(a == same);    // true
System.out.println(a.equals(b));  // true
```

### `Object.equals()` MẶC ĐỊNH LÀ GÌ?

Nếu lớp của bạn **không ghi đè (`override`) `equals`**, phần triển khai kế thừa từ `Object` sử dụng phép bằng nhau theo định danh.

```java
final class UserId {
    private final String value;

    UserId(String value) {
        this.value = value;
    }
}

UserId a = new UserId("U-100");
UserId b = new UserId("U-100");

System.out.println(a == b);       // false
System.out.println(a.equals(b));  // false: vẫn là Object.equals mặc định
```

Đây là lý do ta ghi đè `equals` khi nghiệp vụ cần phép bằng nhau về mặt logic: **mặc định Java không biết trường dữ liệu nào tạo nên ý nghĩa “bằng nhau” của lớp**.

### KHI NÀO `==` LÀ ĐÚNG?

`==` phù hợp khi ta thật sự quan tâm định danh, ví dụ:

- hằng enum;
- đối tượng đơn nhất (`singleton`);
- đối tượng đánh dấu (`sentinel`) dùng làm dấu hiệu đặc biệt;
- kiểm tra hai tham chiếu có đang chia sẻ đúng cùng một đối tượng hay không.

Nó thường không phù hợp cho đối tượng được nhận diện theo giá trị như `String`, giá trị tiền tệ hoặc đối tượng giá trị dùng làm ID.

> Lưu ý: chuỗi literal và biểu thức chuỗi hằng tại thời điểm biên dịch được intern theo semantics của Java, nên các literal bằng nhau có thể chủ đích cùng trỏ tới một instance trong **String pool**. Chuỗi được tạo ở thời gian chạy không tự động có bảo đảm đó. Dù vậy, `==` vẫn không phải cách đúng để so nội dung `String`.

## <a id="value-object-equality">Phép bằng nhau của đối tượng giá trị</a>

**Đối tượng giá trị (value object)** thường được nhận diện bởi **giá trị có ý nghĩa**, không phải bởi định danh của đối tượng.

Ví dụ hai đối tượng `Money(100, "USD")` có thể được xem là bằng nhau nếu số tiền và đơn vị tiền tệ giống nhau, dù chúng được tạo ở hai thời điểm khác nhau.

### ĐỐI TƯỢNG GIÁ TRỊ KHÁC THỰC THỂ Ở ĐÂU?

Một mô hình tư duy hữu ích:

```text
Đối tượng giá trị
→ "nó mang giá trị gì?"

Thực thể
→ "nó là thực thể nào?"
```

Ví dụ `Money(100, "USD")` thường so sánh bằng nhau theo giá trị. Một `User` có thể so sánh bằng nhau theo mã định danh ổn định như `userId`, chứ không nhất thiết theo toàn bộ trạng thái có thể thay đổi như tên hiển thị hoặc địa chỉ.

Điều quan trọng là **chọn quy tắc bằng nhau theo ý nghĩa nghiệp vụ**, không phải máy móc đưa mọi trường dữ liệu vào công cụ sinh mã của IDE.

### TRẠNG THÁI DÙNG ĐỂ XÁC ĐỊNH BẰNG NHAU NÊN ỔN ĐỊNH

Một thiết kế phép bằng nhau tốt thường cần:

- dựa trên các thành phần thực sự định nghĩa giá trị hoặc định danh nghiệp vụ;
- giữ các tính chất phản xạ, đối xứng, bắc cầu và nhất quán;
- giữ hành vi của `hashCode` nhất quán với phép bằng nhau, để các đối tượng được xem là bằng nhau luôn tạo cùng mã băm;
- tránh thay đổi trạng thái đó khi đối tượng đang làm khóa hoặc phần tử của cấu trúc dữ liệu băm/có thứ tự.

Ví dụ nếu `UserId` được định nghĩa chỉ bởi `value`, `equals` và `hashCode` cũng nên dựa trên đúng `value` đó.

### CHUYỂN TIẾP

Ta đã biết **phép bằng nhau về mặt logic là quyết định của lớp và nghiệp vụ**, không phải của định danh tham chiếu. Chương tiếp theo đi vào chính xác `equals` phải giữ những quy tắc nào để thư viện Java có thể tin quyết định đó.
