# this và super

Trong instance ngữ cảnh, Java cần cách nói rõ **object hiện tại** và **phần hành vi/trạng thái thuộc superclass**. Hai keyword `this` và `super` phục vụ đúng vai trò đó.

## <a id="this-reference">this</a>

`this` là reference tới object hiện tại trong instance ngữ cảnh.

Nó thường được dùng để:

- phân biệt field với parameter cùng tên;
- truyền object hiện tại sang method khác;
- gọi constructor khác bằng `this(...)`;
- trả về current instance trong fluent API nếu design phù hợp.

```java
this.name = name;
```

Không có `this` trong static ngữ cảnh vì static member không gắn với một instance cụ thể.

## <a id="super-access">super</a>

`super` cho phép truy cập constructor/member của superclass theo quy tắc của Java.

```java
super(provider);
super.toString();
```

`super` không phải một object thứ hai nằm bên trong subclass. Object vẫn là một instance duy nhất; keyword này chỉ thay đổi cách member/constructor lookup được diễn đạt trong source.

## <a id="constructor-chaining-order">Quy tắc this()/super()</a>

Mỗi constructor cuối cùng phải dẫn tới một superclass constructor.

```text
this(...)
→ constructor khác cùng class
→ cuối cùng phải tới super(...)
```

Constructor invocation này phải xuất hiện theo quy tắc đặc biệt của Java ở đầu constructor chain.

Hiểu chain này là nền tảng để đọc initialization order ở các chương sau.

Trước đó, chương tiếp theo trả lời: **những member nào bên ngoài hoặc subclass được phép truy cập?**
