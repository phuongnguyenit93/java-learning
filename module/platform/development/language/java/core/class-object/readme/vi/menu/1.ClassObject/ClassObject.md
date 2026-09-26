# Class và Object

Java dùng `class` để mô tả **một kiểu đối tượng có trạng thái và hành vi**, còn `object` là một instance thực sự tồn tại khi chương trình chạy. Đây là nền tảng để hiểu constructor, `this`, `static`, initialization order, aliasing, copy và immutability ở các chương sau.

Hãy giữ một ví dụ xuyên suốt như `Profile` hoặc `BankAccount`: class định nghĩa cấu trúc và quy tắc chung; mỗi object có identity và trạng thái riêng.

Lộ trình:

```text
Class khác Object thế nào?
Class & Object
        ↓
Object được tạo với trạng thái hợp lệ bằng cách nào?
Constructor
        ↓
this / super đại diện cho ngữ cảnh nào?
this / super
        ↓
Ai được phép thấy hoặc sửa member?
Access Modifier
        ↓
Member nào thuộc class, member nào thuộc từng object?
static / final
        ↓
Initialization diễn ra ở đâu và theo thứ tự nào?
Initialization Blocks → Initialization Order
        ↓
Toàn bộ object creation diễn ra thế nào?
Object Creation Lifecycle
        ↓
Nested/inner class giữ ngữ cảnh gì?
Nested / Inner Class
        ↓
Mọi object kế thừa hợp đồng nào?
Object Class
        ↓
Một tập hằng typed được mô hình hóa ra sao?
Enum
        ↓
Copy object thực sự copy gì?
Copy Semantics
        ↓
Nhiều reference tới cùng mutable object gây gì?
Aliasing & Mutability
        ↓
Làm sao tạo object an toàn để chia sẻ?
Immutability & Defensive Copy
```

## <a id="class-object-model">Class và Object là gì?</a>

### KHÁI NIỆM

`class` là định nghĩa kiểu và phần triển khai chung: field, method, constructor, nested type và logic khởi tạo.

`object` là một instance của class ở runtime, có:

- identity riêng;
- trạng thái instance riêng;
- hành vi được định nghĩa bởi class và runtime type.

```java
Profile first = new Profile("An");
Profile second = new Profile("Binh");
```

`first` và `second` dùng cùng một định nghĩa class nhưng là hai object khác nhau với trạng thái khác nhau.

## <a id="fields-methods-state">Trạng thái, hành vi và Identity</a>

Instance field biểu diễn trạng thái của từng object. Instance method làm việc trên object hiện tại thông qua tham chiếu ngầm `this`.

Static member thuộc class-level ngữ cảnh, không phải trạng thái riêng của mỗi object.

Hai object có thể có cùng giá trị field nhưng vẫn có identity khác nhau. Đây là lý do equality và identity là hai câu hỏi khác nhau; module `object-contract` đi sâu vào phần đó.

## <a id="object-reference-lifecycle">Reference và vòng đời Object</a>

Biến kiểu reference giữ **giá trị tham chiếu**, không chứa object “bên trong biến”. Nhiều biến có thể cùng tham chiếu một object.

Object có thể tiếp tục tồn tại sau khi một biến cục bộ ra khỏi scope nếu vẫn còn đường tham chiếu tới nó. Garbage Collector quyết định thu hồi bộ nhớ dựa trên reachability, không dựa trên việc một tên biến vừa hết scope.

Vòng đời tài nguyên là vấn đề khác: file/socket phải được đóng có chủ ý; không chờ GC để giải phóng tài nguyên bên ngoài JVM.

chương tiếp theo bắt đầu từ thời điểm một object được tạo: **constructor làm gì để thiết lập trạng thái hợp lệ?**
