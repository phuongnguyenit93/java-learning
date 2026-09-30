# Đa kế thừa qua interface và giải quyết xung đột

Java không cho một lớp `extends` nhiều lớp cha, nhưng một lớp có thể `implements` nhiều interface. Các hợp đồng chỉ gồm phương thức abstract thường kết hợp được nếu chữ ký tương thích. Xung đột đáng chú ý xuất hiện khi nhiều interface cung cấp **phương thức `default`** cạnh tranh cho cùng một chữ ký.

Có thể đọc quá trình giải quyết theo thứ tự thay vì ghi nhớ các quy tắc rời rạc:

```text
Phía lớp có thành viên cùng chữ ký tương đương khi ghi đè (override-equivalent)?
→ phương thức `static`: không thể dùng để triển khai phương thức của đối tượng trong interface; lớp không hợp lệ nếu không thể loại bỏ xung đột
→ phương thức của đối tượng được lớp khai báo hoặc kế thừa:
→ cụ thể (concrete) và tương thích: phần triển khai từ phía lớp được dùng
→ trừu tượng (abstract): phương thức `default` của interface không tự thỏa yêu cầu này; lớp cụ thể vẫn phải triển khai
→ cụ thể nhưng không tương thích với hợp đồng interface: lớp phải cung cấp một override tương thích nếu có thể; nếu không sẽ lỗi biên dịch

Nếu phía lớp không có thành viên gây ảnh hưởng như trên, có một phương thức `default` của interface cụ thể hơn các phương thức `default` còn lại?
→ có: phương thức `default` cụ thể hơn được dùng

Nếu vẫn còn nhiều phương thức `default` độc lập cạnh tranh?
→ lớp triển khai phải override để giải quyết rõ ràng
```

## <a id="default-method-conflict">Xung đột phương thức default</a>

Nếu hai interface không có quan hệ kế thừa cung cấp cùng một phương thức `default`:

```java
interface A {
    default String name() { return "A"; }
}

interface B {
    default String name() { return "B"; }
}
```

Nếu phía lớp chưa có một phương thức cụ thể tương thích đã giải quyết chữ ký này, lớp triển khai cả `A` và `B` phải override `name()` để tự quyết định hành vi.

Java không tự đoán default nào “đúng hơn” khi không có interface nào cụ thể hơn interface còn lại.

Ngược lại, nếu interface con override phương thức `default` của interface cha, khai báo ở interface con cụ thể hơn:

```java
interface Parent {
    default String name() { return "parent"; }
}

interface Child extends Parent {
    @Override
    default String name() { return "child"; }
}
```

Một lớp chỉ triển khai `Child` không phải chọn lại giữa `Parent.name()` và `Child.name()`; `Child` đã cung cấp hành vi cụ thể hơn.

Xung đột cũng có thể xuất hiện ngay trong **hệ phân cấp interface**, chưa cần có lớp triển khai. Nếu một interface con kế thừa hai phương thức `default` có chữ ký tương đương từ hai interface cha không có quan hệ cụ thể-hơn với nhau, interface con phải tự khai báo một phương thức override để giải quyết; nếu không, khai báo interface con bị lỗi biên dịch:

```java
interface Left {
    default String name() { return "left"; }
}

interface Right {
    default String name() { return "right"; }
}

interface Combined extends Left, Right {
    @Override
    default String name() {
        return Left.super.name();
    }
}
```

Việc giải quyết ở interface con làm hợp đồng trở nên rõ ràng trước khi bất kỳ lớp nào triển khai `Combined`.

## <a id="class-wins-rule">Phương thức từ hệ phân cấp lớp được ưu tiên</a>

Nếu hệ phân cấp lớp đã cung cấp một **phương thức cụ thể của đối tượng** tương thích, phương thức đó được ưu tiên hơn phương thức `default` của interface:

```java
class Named {
    public String name() {
        return "class";
    }
}

interface NamedContract {
    default String name() {
        return "interface";
    }
}

class CardPayment extends Named implements NamedContract {
}
```

Với `new CardPayment().name()`, phần triển khai từ `Named` được dùng. Phương thức `default` của interface không âm thầm thay thế hành vi cụ thể đã được thừa hưởng từ hệ phân cấp lớp.

Điểm cần phân biệt: nếu lớp cha chỉ khai báo một phương thức abstract cùng chữ ký, lớp cụ thể vẫn phải cung cấp một phần triển khai hợp lệ; quy tắc **lớp được ưu tiên** không nên được hiểu như một phép chọn ở thời điểm chạy giữa hai phần thân luôn có sẵn.

Một phương thức `static` từ phía lớp cũng không thể “nhường chỗ” để phương thức `default` của interface trở thành phương thức của đối tượng. Nếu chữ ký xung đột với hợp đồng interface, Java báo lỗi biên dịch thay vì chọn `default`.

## <a id="explicit-super-interface">Gọi InterfaceName.super</a>

Khi lớp phải override để giải quyết xung đột, nó có thể gọi tường minh phương thức `default` của **interface cha trực tiếp**:

```java
@Override
public String name() {
    return A.super.name() + B.super.name();
}
```

`InterfaceName.super.method()` cho phép lớp kết hợp hành vi mặc định thay vì phải viết lại toàn bộ.

## <a id="diamond-interface">Kế thừa interface dạng kim cương</a>

Hình kim cương tự thân không đồng nghĩa với xung đột.

Nếu nhiều đường kế thừa cuối cùng cùng dẫn đến một phương thức `default` cụ thể nhất (most-specific), hợp đồng vẫn rõ ràng. Xung đột chỉ xuất hiện khi nhiều phương thức `default` độc lập cạnh tranh mà không có phương thức nào cụ thể hơn.

Mô hình tư duy của phần này là:

```text
đa kế thừa qua interface
→ kết hợp nhiều kiểu/hợp đồng
→ có thể nhận hành vi default
→ interface không đóng góp trạng thái riêng cho từng đối tượng; đây không phải đa kế thừa lớp
→ khi các default cạnh tranh, Java dùng độ cụ thể + ưu tiên hệ phân cấp lớp + ghi đè tường minh
```

Sau khi hiểu các quy tắc xung đột, bước cuối là nối toàn bộ module thành một mô hình thiết kế: chọn cơ chế nào, kết hợp chúng ra sao và cần kiểm tra gì khi interface bắt đầu có hệ phân cấp và hành vi mặc định.
