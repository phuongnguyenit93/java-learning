# Identity và Equality

Module này nói về một nhóm **hợp đồng mà các API Java khác dựa vào để hiểu object của bạn**. `equals`, `hashCode`, `toString`, `Comparable` và `Comparator` không phải những method tiện ích rời rạc. Chúng ảnh hưởng trực tiếp tới cách collection tìm kiếm, cách sắp xếp, logging và nhiều framework xử lý object.

Lộ trình học:

```text
Cùng object hay cùng giá trị?
Identity vs Equality
        ↓
Một class định nghĩa “bằng nhau” như thế nào?
equals
        ↓
Hash collection tìm object hiệu quả bằng cách nào?
hashCode
        ↓
Vì sao equals và hashCode phải đi cùng nhau?
equals + hashCode
        ↓
Object nên tự mô tả ra sao cho log/debug?
toString
        ↓
Một kiểu có thứ tự tự nhiên như thế nào?
Comparable
        ↓
Nếu cần nhiều cách sắp xếp khác nhau thì sao?
Comparator
```

Trong module này, hãy hình dung một value object đơn giản như `UserId`, cùng một domain object như `Book`, rồi theo dõi chúng khi đi qua `HashSet`, `HashMap`, `TreeSet` và các thao tác sắp xếp. Mục tiêu không phải học thuộc method signature, mà hiểu **Java library tin object của bạn sẽ giữ những lời hứa nào**.

## <a id="identity-vs-equality">Identity và Equality</a>

### KHÁI NIỆM

**Identity** trả lời câu hỏi:

> Hai biến tham chiếu có đang trỏ tới đúng cùng một object hay không?

**Logical equality** trả lời một câu hỏi khác:

> Hai object khác nhau có nên được xem là cùng một giá trị hoặc cùng một thực thể theo quy tắc của domain hay không?

Java dùng hai cơ chế khác nhau:

```text
==
→ với reference: kiểm tra identity

equals(...)
→ method có thể override để định nghĩa logical equality
```

### VÌ SAO PHẢI PHÂN BIỆT?

Nếu không phân biệt hai khái niệm này, ta dễ viết code đúng cú pháp nhưng sai ý nghĩa domain.

Ví dụ hai object sau là **hai object khác nhau trong heap**, nhưng có thể đại diện cho cùng một `UserId`:

```java
UserId a = new UserId("U-100");
UserId b = new UserId("U-100");
```

Ta có hai câu hỏi độc lập:

```text
a và b có phải đúng cùng object không?
→ identity

a và b có đại diện cùng user id không?
→ logical equality
```

Collection như `HashSet`, `HashMap` hoặc `TreeSet` không thể tự đoán domain của bạn muốn hiểu chữ “same” theo nghĩa nào. Chúng dựa vào các contract bạn cung cấp.

### THAM CHIẾU, OBJECT VÀ GIÁ TRỊ

Một reference variable không phải chính object. Có thể hình dung:

```text
UserId a ───────┐
                ├──> UserId("U-100")
UserId same ────┘

UserId b ──────────> UserId("U-100")
```

`a` và `same` có cùng identity vì cùng trỏ tới một object. `a` và `b` có identity khác nhau dù dữ liệu giống nhau.

## <a id="reference-equality">So sánh tham chiếu bằng ==</a>

Với reference, `==` chỉ kiểm tra xem hai reference có nhận diện cùng object, hoặc cả hai cùng `null`, hay không.

```java
String a = new String("java");
String b = new String("java");
String same = a;

System.out.println(a == b);       // false
System.out.println(a == same);    // true
System.out.println(a.equals(b));  // true
```

### `Object.equals()` MẶC ĐỊNH LÀ GÌ?

Nếu class của bạn **không override `equals`**, implementation kế thừa từ `Object` sử dụng equality theo identity.

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

Đây là lý do ta override `equals` khi domain cần logical equality: **mặc định Java không biết field nào tạo nên ý nghĩa “bằng nhau” của class**.

### KHI NÀO `==` LÀ ĐÚNG?

`==` phù hợp khi ta thật sự quan tâm identity, ví dụ:

- enum constant;
- singleton instance;
- sentinel object;
- kiểm tra hai reference có đang chia sẻ đúng cùng object hay không.

Nó thường không phù hợp cho object được nhận diện theo giá trị như `String`, money value hoặc ID value object.

> Lưu ý: String pool có thể khiến một số string literal tình cờ có cùng identity. Điều đó không biến `==` thành cách đúng để so nội dung `String`.

## <a id="value-object-equality">Equality của Value Object</a>

Value object thường được nhận diện bởi **giá trị có ý nghĩa**, không phải bởi identity của object.

Ví dụ hai object `Money(100, "USD")` có thể được xem là bằng nhau nếu amount và currency giống nhau, dù chúng được tạo ở hai thời điểm khác nhau.

### VALUE OBJECT KHÁC THỰC THỂ Ở ĐÂU?

Một mental model hữu ích:

```text
Value object
→ "nó mang giá trị gì?"

Entity
→ "nó là thực thể nào?"
```

Ví dụ `Money(100, "USD")` thường có equality theo value. Một `User` có thể có equality theo stable identifier như `userId`, chứ không nhất thiết theo toàn bộ trạng thái mutable như tên hiển thị hoặc địa chỉ.

Điều quan trọng là **chọn equality theo domain meaning**, không phải máy móc đưa mọi field vào IDE generator.

### TRẠNG THÁI DÙNG CHO EQUALITY NÊN ỔN ĐỊNH

Một thiết kế equality tốt thường cần:

- dựa trên các thành phần thực sự định nghĩa giá trị hoặc identity domain;
- giữ reflexive/symmetric/transitive/consistent contract;
- dùng cùng equality-relevant state với `hashCode`;
- tránh thay đổi equality state khi object đang làm key hoặc member của hash/sorted collection.

Ví dụ nếu `UserId` được định nghĩa chỉ bởi `value`, `equals` và `hashCode` cũng nên dựa trên đúng `value` đó.

### CHUYỂN TIẾP

Ta đã biết **logical equality là quyết định của class/domain**, không phải của reference identity. Chương tiếp theo đi vào chính xác `equals` phải giữ những luật nào để Java library có thể tin quyết định đó.
