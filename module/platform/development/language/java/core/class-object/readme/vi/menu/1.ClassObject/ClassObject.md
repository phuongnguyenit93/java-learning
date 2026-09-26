# Class và Object

Java dùng `class` để mô tả **một kiểu đối tượng có trạng thái và hành vi**, còn `object` là một instance thực sự tồn tại khi chương trình chạy. Cách tổ chức này giúp dữ liệu đi cùng với những hành vi và quy tắc chịu trách nhiệm bảo vệ dữ liệu đó, thay vì để mọi giá trị tồn tại rời rạc và bị thay đổi tùy ý.

Trong module này, hãy giữ mô hình `BankAccount` làm ví dụ xuyên suốt: class định nghĩa cấu trúc và quy tắc chung; mỗi object tài khoản có danh tính (identity) và trạng thái riêng. Các chương sau tiếp tục cùng mô hình đó khi học khởi tạo, vòng đời, sao chép, aliasing và bất biến (immutability).

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
Một tập hằng có kiểu rõ ràng được mô hình hóa ra sao?
Enum
        ↓
Sao chép object thực sự sao chép điều gì?
Copy Semantics
        ↓
Nhiều reference tới cùng một object có thể thay đổi gây gì?
Aliasing & Mutability
        ↓
Làm sao tạo object an toàn để chia sẻ?
Bất biến (Immutability) & Defensive Copy
```

Các thuật ngữ chính liên hệ với nhau như sau:

```text
class
→ định nghĩa kiểu, cấu trúc trạng thái và hành vi

object / instance
→ một thực thể cụ thể tồn tại ở runtime

constructor / initialization
→ thiết lập trạng thái ban đầu hợp lệ cho object

this / super
→ biểu diễn ngữ cảnh của instance hiện tại và superclass

static / instance
→ phân biệt trạng thái/hành vi thuộc class với trạng thái/hành vi thuộc từng object

reference / aliasing / copying / immutability
→ giải thích object được tham chiếu, dùng chung, sao chép hoặc bảo vệ khỏi thay đổi như thế nào
```

## <a id="class-object-model">Class và Object là gì?</a>

### KHÁI NIỆM

`class` là định nghĩa kiểu và phần triển khai chung: field, method, constructor, nested type và logic khởi tạo.

`object` là một instance của class ở runtime, có:

- identity riêng;
- trạng thái instance riêng;
- hành vi được định nghĩa bởi class và runtime type.

```java
BankAccount first = new BankAccount("A-01");
BankAccount second = new BankAccount("A-02");
```

`first` và `second` dùng cùng định nghĩa `BankAccount` nhưng là hai object có identity riêng và có thể mang trạng thái khác nhau.

### VÌ SAO GOM TRẠNG THÁI VÀ HÀNH VI VÀO OBJECT?

Giả sử số dư tài khoản chỉ là một biến số nguyên bị nhiều đoạn mã sửa trực tiếp. Mỗi nơi sử dụng đều phải tự nhớ các quy tắc như “không được rút quá số dư”, và chỉ cần một nơi quên là trạng thái có thể mất hợp lệ. Class cho phép đặt trạng thái cùng các hành vi bảo vệ invariant vào một ranh giới rõ ràng:

```java
class BankAccount {
    private int balance;

    void withdraw(int amount) {
        if (amount <= 0 || amount > balance) {
            throw new IllegalArgumentException();
        }
        balance -= amount;
    }
}
```

Điểm quan trọng không chỉ là Java có cú pháp `class`. Object trở thành nơi sở hữu trạng thái và những quy tắc giữ cho trạng thái đó có ý nghĩa. Module OOP sẽ đi sâu hơn về đóng gói (encapsulation) và thiết kế hướng đối tượng; module này trước hết xây nền về object model và vòng đời của object.

## <a id="fields-methods-state">Trạng thái, hành vi và Identity</a>

Instance field biểu diễn trạng thái riêng của từng object. Instance method làm việc trên object hiện tại thông qua tham chiếu ngầm `this`.

Static member thuộc về class, không phải trạng thái riêng của từng object.

Hai object có thể có cùng giá trị field nhưng vẫn có identity khác nhau. Đây là lý do equality và identity là hai câu hỏi khác nhau; module `object-contract` đi sâu vào phần đó.

## <a id="object-reference-lifecycle">Reference và vòng đời Object</a>

Biến kiểu reference giữ **giá trị tham chiếu**, không chứa object “bên trong biến”. Nhiều biến có thể cùng tham chiếu một object.

Object có thể tiếp tục tồn tại sau khi một biến cục bộ ra khỏi phạm vi nếu vẫn còn đường tham chiếu tới nó. Garbage Collector quyết định thu hồi bộ nhớ dựa trên khả năng còn được tham chiếu tới (reachability), không dựa trên việc một tên biến vừa hết phạm vi.

Vòng đời tài nguyên là vấn đề khác: file/socket phải được đóng có chủ ý; không chờ GC để giải phóng tài nguyên bên ngoài JVM.

Chương tiếp theo bắt đầu từ thời điểm một object được tạo: **constructor làm gì để thiết lập trạng thái hợp lệ?**
