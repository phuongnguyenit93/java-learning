# Cách Java chọn phương thức

Chương này tập trung vào **cơ chế của Java**, không phải thêm một “trụ cột OOP” mới.

Nạp chồng (`Overloading`) được đặt cạnh ghi đè (`Overriding`) vì hai thuật ngữ rất dễ bị nhầm. Cần nhớ ngay từ đầu:

```text
Nạp chồng
→ chủ yếu là lựa chọn phương thức lúc biên dịch
→ không phải một trụ cột OOP

Ghi đè + phân phối động
→ quyết định phần triển khai lúc chạy
→ là cơ chế quan trọng giúp đa hình kiểu con hoạt động
```

## <a id="overloading-compile-time">Nạp chồng (Overloading)</a>

### KHÁI NIỆM

Nạp chồng xảy ra khi nhiều phương thức có cùng tên nhưng khác danh sách tham số.

```java
String select(Parent value) { return "Parent"; }
String select(Child value)  { return "Child"; }
```

### CƠ CHẾ — trình biên dịch chọn phương thức nạp chồng nào?

Trình biên dịch dựa vào **kiểu đã biết tại thời điểm biên dịch của đối số** cùng với các quy tắc chuyển đổi tham số để chọn chữ ký phương thức phù hợp.

```java
Parent value = new Child();
select(value); // select(Parent)
```

Mặc dù đối tượng thực tế lúc chạy là `Child`, biến `value` có kiểu khai báo là `Parent`. Vì vậy phiên bản nạp chồng `select(Parent)` được chọn khi biên dịch.

Java không chờ tới lúc chạy rồi chọn lại phiên bản nạp chồng dựa trên class thực tế của đối tượng.

### RANH GIỚI — phần này không thay thế toàn bộ quy tắc phân giải nạp chồng

Mục tiêu ở OOP là phân biệt **lựa chọn nạp chồng lúc biên dịch** với **phân phối phương thức ghi đè lúc chạy**. Các quy tắc chi tiết như widening, boxing, varargs, phương thức cụ thể nhất và trường hợp mơ hồ thuộc `language-basics → Methods`.

### MINH CHỨNG — `DispatchController#overloadVsOverride()`

Cùng một biến:

```java
Parent x = new Child();
```

cho ra hai cơ chế khác nhau:

```text
select(x)
→ nhìn kiểu lúc biên dịch là Parent
→ gọi select(Parent)

x.call()
→ đối tượng thực tế là Child
→ chạy Child.call()
```

Đây là sự khác biệt quan trọng nhất cần ghi nhớ giữa overloading và overriding.

## <a id="overriding-runtime">Ghi đè (Overriding)</a>

### KHÁI NIỆM

Ghi đè xảy ra khi class con cung cấp phần triển khai mới cho một phương thức đối tượng được kế thừa từ class cha với chữ ký tương thích.

Trong Java, tư duy ghi đè cũng áp dụng khi một class cung cấp phần triển khai cho hợp đồng phương thức đối tượng kế thừa từ `interface` hoặc ghi đè một phương thức `default`. Mô-đun `abstract-interface` sở hữu các quy tắc chi tiết của `interface`; ở đây trọng tâm vẫn là **phần triển khai của phương thức đối tượng được chọn động lúc chạy**.

```java
class Parent {
    String call() { return "Parent"; }
}

class Child extends Parent {
    @Override
    String call() { return "Child"; }
}
```

### CƠ CHẾ

Trình biên dịch trước tiên xác định rằng lời gọi hợp lệ với phương thức `call()`.

Khi chạy, nếu đối tượng thực tế là `Child`, cơ chế phân phối động chọn phần triển khai của `Child`.

Đây là lý do một biến có kiểu `Parent` vẫn có thể biểu hiện hành vi của `Child`.

## <a id="covariant-return">Kiểu trả về hiệp biến (Covariant Return Type)</a>

Phương thức ghi đè được phép trả về một **kiểu tham chiếu cụ thể hơn** so với kiểu trả về của phương thức ở class cha.

```java
class Parent {
    Animal create() { ... }
}

class Child extends Parent {
    @Override
    Dog create() { ... }
}
```

Vì `Dog` là kiểu con của `Animal`, hợp đồng của `Parent` vẫn được giữ. Bên gọi thông qua `Child` lại nhận được kiểu chính xác hơn.

Quy tắc này gọi là **kiểu trả về hiệp biến (covariant return type)**.

Kiểu nguyên thủy không có kiểu trả về hiệp biến theo nghĩa này.

## <a id="override-rules">Quy tắc ghi đè</a>

Không phải cứ class con có phương thức cùng tên là ghi đè.

| Trường hợp | Có ghi đè được phân phối lúc chạy? | Ghi nhớ |
| --- | --- | --- |
| Phương thức đối tượng được kế thừa, có thể ghi đè và có chữ ký tương thích | Có | Phân phối động |
| Phương thức đối tượng `final` | Không | Class con không được ghi đè |
| Phương thức `private` | Không | Không phải phương thức được kế thừa để ghi đè |
| Phương thức `static` ở kiểu con có cùng chữ ký với phương thức `static` được kế thừa từ kiểu cha | Không | Đây là che khuất phương thức |

Ngoài ra:

- phương thức ghi đè không được giảm mức truy cập của phương thức cha;
- checked exception phải tuân quy tắc tương thích;
- nên dùng `@Override` để trình biên dịch kiểm tra giúp quan hệ ghi đè.

### CỤ THỂ HÓA — phạm vi truy cập, kiểu trả về và checked exception

Override không được giảm mức truy cập:

```java
class Parent {
    public Number value() { return 1; }
}

class Child extends Parent {
    @Override
    protected Number value() { return 2; } // lỗi biên dịch
}
```

Kiểu trả về tham chiếu có thể hiệp biến:

```java
class Parent {
    Number value() { return 1; }
}

class Child extends Parent {
    @Override
    Integer value() { return 2; } // OK
}
```

Với checked exception, phương thức ghi đè có thể giữ nguyên, thu hẹp hoặc bỏ exception đã khai báo, nhưng không được mở rộng thành checked exception rộng hơn hợp đồng cha:

```java
class Parent {
    void load() throws IOException {}
}

class Child extends Parent {
    @Override
    void load() throws FileNotFoundException {} // OK
}
```

`throws Exception` trong `Child.load()` ở ví dụ này sẽ không hợp lệ vì làm bên gọi của hợp đồng `Parent` phải đối mặt với một checked exception rộng hơn.

### PHÂN LOẠI — các thành viên thường bị nhầm với ghi đè

```text
phương thức private
→ không phải mục tiêu ghi đè được kế thừa

phương thức đối tượng final
→ có thể được kế thừa nhưng không được ghi đè

phương thức static được kế thừa
→ phương thức static cùng chữ ký ở kiểu con là che khuất, không phải ghi đè lúc chạy

hàm khởi tạo
→ không được kế thừa, không bị ghi đè
```

Ngoài ra Java không cho đổi một phương thức `static` thành phương thức đối tượng hoặc ngược lại trong kiểu con với cùng chữ ký. Đây là xung đột lúc biên dịch, không phải một biến thể của ghi đè.

## <a id="static-method-hiding">Che khuất phương thức `static`</a>

Phương thức `static` thuộc về kiểu/class chứ không tham gia phân phối động theo đối tượng thực tế lúc chạy như phương thức đối tượng.

```java
class Parent {
    static String call() { return "Parent"; }
}

class Child extends Parent {
    static String call() { return "Child"; }
}

Parent value = new Child();
value.call(); // Parent.call()
```

Việc chọn phương thức `static` đi theo kiểu được biết lúc biên dịch của biểu thức `value`, tức là `Parent`.

Gọi phương thức `static` thông qua đối tượng có thể biên dịch được trong một số trường hợp nhưng dễ gây hiểu nhầm. Nên gọi bằng tên class để ý nghĩa rõ ràng hơn.

`super.someMethod()` là một lời gọi **được chỉ định rõ phần triển khai của kiểu cha**, nên nó không có ý nghĩa giống lời gọi động thông thường qua `this.someMethod()`. `super` thường được dùng khi phương thức ghi đè muốn mở rộng thay vì thay thế hoàn toàn hành vi cha.

## <a id="field-hiding">Che khuất trường dữ liệu</a>

Trường dữ liệu không có phân phối động.

```java
class Parent { String name = "parent"; }
class Child extends Parent { String name = "child"; }

Parent value = new Child();
System.out.println(value.name); // parent
```

`value.name` được quyết định từ kiểu khai báo của `value`, tức là `Parent`.

Đây là **che khuất trường**, không phải đa hình của trạng thái.

Hai trường cùng tên trong `Parent` và `Child` là **hai thành viên/vị trí lưu trữ khác nhau**, không phải một trường duy nhất được phân phối động. Ép kiểu hoặc kiểu khai báo khác nhau có thể làm cùng một đối tượng được quan sát qua các trường khác nhau, vì vậy che khuất trường rất dễ gây nhầm và thường nên tránh.

Việc dùng cùng một tên trường ở cả class cha và class con thường làm mã khó hiểu, nên tránh nếu không có lý do mạnh.

## <a id="dispatch-vs-hiding">Phân phối động và che khuất thành viên</a>

Có thể ghi nhớ nhanh bằng bảng sau:

| Thành phần | Thời điểm quyết định chính | Với `Parent x = new Child()` |
| --- | --- | --- |
| Chữ ký phương thức nạp chồng | Lúc biên dịch | Dựa trên kiểu của đối số |
| Phương thức đối tượng bị ghi đè | Lúc chạy | Có thể chạy phần thân của `Child` |
| Phương thức `static` bị che khuất | Lúc biên dịch | Theo kiểu `Parent` |
| Trường bị che khuất | Lúc biên dịch | Theo kiểu `Parent` |

### MINH CHỨNG — `DispatchController#dispatchVsHiding()`

Ví dụ thực thi trả về cùng lúc:

```text
runtime        = Child
instanceMethod = Child.call
staticMethod   = Parent.staticCall
field          = parent-field
```

Cùng một đối tượng thực tế lúc chạy là `Child`, nhưng các loại thành viên khác nhau lại được chọn theo các quy tắc khác nhau.

Đây là minh chứng rõ nhất để tránh hiểu nhầm:

> “Gọi qua đối tượng thì mọi thứ đều được chọn động lúc chạy.”

Điều đó **không đúng**.

### MỐI LIÊN HỆ — từ kế thừa sang kết hợp đối tượng

Kế thừa kết hợp với ghi đè rất mạnh, nhưng cây kế thừa càng sâu thì các class càng phụ thuộc nhau chặt.

Nếu mục tiêu chỉ là thay đổi một hành vi, ta có thể không cần tạo thêm class con. **Kết hợp đối tượng (composition)** cho phép thay đối tượng cộng tác thay vì kéo dài cây kế thừa.
