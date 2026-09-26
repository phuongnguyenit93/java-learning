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

Trong module này, hãy hình dung một **value object** như `Money`, `UserId` hoặc `BookKey`. Ta sẽ xem cùng object đó khi đi qua `HashSet`, `HashMap`, `TreeSet` và các thao tác sắp xếp.

## <a id="identity-vs-equality">Identity và Equality</a>

### KHÁI NIỆM

**Identity** trả lời câu hỏi:

> Hai biến tham chiếu có đang trỏ tới đúng cùng một object hay không?

**Logical equality** trả lời một câu hỏi khác:

> Hai object khác nhau có nên được xem là cùng một giá trị hoặc cùng một thực thể theo quy tắc của domain hay không?

Java dùng:

```text
==
→ kiểm tra identity của reference

equals(...)
→ method có thể override để định nghĩa logical equality
```

Hai object có thể có cùng dữ liệu nhưng vẫn là hai identity khác nhau.

## <a id="reference-equality">So sánh tham chiếu bằng ==</a>

Với reference, `==` chỉ kiểm tra xem hai reference có nhận diện cùng object hay cả hai cùng `null` hay không.

```java
String a = new String("java");
String b = new String("java");

a == b      // false
a.equals(b) // true
```

`==` phù hợp khi ta thật sự quan tâm identity, ví dụ enum constant, singleton hoặc sentinel object. Nó thường không phù hợp cho object được nhận diện theo giá trị như `String`, money value hoặc ID.

## <a id="value-object-equality">Equality của Value Object</a>

Value object thường được nhận diện bởi **giá trị có ý nghĩa**, không phải bởi identity của object.

Ví dụ hai object `Money(100, "USD")` có thể được xem là bằng nhau nếu amount và currency giống nhau, dù chúng được tạo ở hai thời điểm khác nhau.

Một thiết kế equality tốt thường cần:

- dựa trên các thành phần thực sự định nghĩa giá trị;
- ổn định khi object đang được dùng làm key;
- giữ các quy tắc đối xứng và bắc cầu;
- tránh phụ thuộc vào trạng thái mutable nếu object sẽ nằm trong hash/sorted collection.

chương tiếp theo đi sâu vào chính hợp đồng của `equals`.
