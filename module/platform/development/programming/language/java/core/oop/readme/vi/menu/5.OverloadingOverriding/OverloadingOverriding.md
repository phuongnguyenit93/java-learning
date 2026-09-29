# Nạp chồng (Overloading) và ghi đè (Overriding)

Chương này tập trung vào **cơ chế của Java**, không phải thêm một “trụ cột OOP” mới.

`Overloading` được đặt cạnh `Overriding` vì hai thuật ngữ rất dễ bị nhầm. Cần nhớ ngay từ đầu:

```text
Overloading
→ chủ yếu là lựa chọn method ở compile time
→ không phải một trụ cột OOP

Overriding + dynamic dispatch
→ quyết định phần triển khai ở runtime
→ là cơ chế quan trọng giúp đa hình kiểu con hoạt động
```

## <a id="overloading-compile-time">Overloading</a>

### KHÁI NIỆM

Overloading xảy ra khi nhiều method có cùng tên nhưng khác danh sách tham số.

```java
String select(Parent value) { return "Parent"; }
String select(Child value)  { return "Child"; }
```

### CƠ CHẾ — compiler chọn overload nào?

Compiler dựa vào **kiểu đã biết tại thời điểm biên dịch của đối số** cùng với các quy tắc chuyển đổi tham số để chọn method signature phù hợp.

```java
Parent value = new Child();
select(value); // select(Parent)
```

Mặc dù object thật ở runtime là `Child`, biến `value` có kiểu khai báo là `Parent`. Vì vậy overload `select(Parent)` được chọn khi biên dịch.

Java không chờ tới runtime rồi chọn lại overload dựa trên class thật của object.

### BOUNDARY — chapter này không thay thế toàn bộ overload-resolution rules

Mục tiêu ở OOP là phân biệt **compile-time overload selection** với **runtime override dispatch**. Các rule chi tiết như widening, boxing, varargs, most-specific method và ambiguity thuộc `language-basics → Methods`.

### MINH CHỨNG — `DispatchController#overloadVsOverride()`

Cùng một biến:

```java
Parent x = new Child();
```

cho ra hai cơ chế khác nhau:

```text
select(x)
→ nhìn kiểu compile-time là Parent
→ gọi select(Parent)

x.call()
→ object thật là Child
→ chạy Child.call()
```

Đây là sự khác biệt quan trọng nhất cần ghi nhớ giữa overloading và overriding.

## <a id="overriding-runtime">Overriding</a>

### KHÁI NIỆM

Overriding xảy ra khi class con cung cấp phần triển khai mới cho một instance method được kế thừa từ class cha với signature tương thích.

Trong Java, tư duy overriding cũng áp dụng khi một class cung cấp implementation cho instance-method contract kế thừa từ interface hoặc override một default method. Module `abstract-interface` sở hữu các rule chi tiết của interface; ở đây trọng tâm vẫn là **instance-method implementation được chọn động ở runtime**.

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

Compiler trước tiên xác định rằng lời gọi hợp lệ với method `call()`.

Khi chạy, nếu object thật là `Child`, cơ chế dynamic dispatch chọn phần triển khai của `Child`.

Đây là lý do một biến có kiểu `Parent` vẫn có thể biểu hiện hành vi của `Child`.

## <a id="covariant-return">Covariant Return Type</a>

Method override được phép trả về một **kiểu tham chiếu cụ thể hơn** so với return type của method ở class cha.

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

Quy tắc này gọi là **covariant return type**.

Primitive type không có covariant return theo nghĩa này.

## <a id="override-rules">Quy tắc Overriding</a>

Không phải cứ class con có method cùng tên là overriding.

| Trường hợp | Có overriding ở runtime? | Ghi nhớ |
| --- | --- | --- |
| Instance method có signature tương thích | Có | Dynamic dispatch |
| `final` instance method | Không | Class con không được override |
| `private` method | Không | Không phải method kế thừa để override |
| `static` method cùng signature | Không | Đây là method hiding |

Ngoài ra:

- override không được giảm mức truy cập của method cha;
- checked exception phải tuân quy tắc tương thích;
- nên dùng `@Override` để compiler kiểm tra giúp quan hệ override.

### CỤ THỂ HÓA — visibility, return type và checked exception

Override không được giảm mức truy cập:

```java
class Parent {
    public Number value() { return 1; }
}

class Child extends Parent {
    @Override
    protected Number value() { return 2; } // compile error
}
```

Return type tham chiếu có thể covariant:

```java
class Parent {
    Number value() { return 1; }
}

class Child extends Parent {
    @Override
    Integer value() { return 2; } // OK
}
```

Với checked exception, override có thể giữ nguyên, thu hẹp hoặc bỏ exception đã khai báo, nhưng không được mở rộng thành checked exception rộng hơn hợp đồng cha:

```java
class Parent {
    void load() throws IOException {}
}

class Child extends Parent {
    @Override
    void load() throws FileNotFoundException {} // OK
}
```

`throws Exception` trong `Child.load()` ở ví dụ này sẽ không hợp lệ vì làm caller của hợp đồng `Parent` phải đối mặt với một checked exception rộng hơn.

### PHÂN LOẠI — các member thường bị nhầm với overriding

```text
private method
→ không phải override target được kế thừa

final instance method
→ có thể được kế thừa nhưng không được override

static method
→ cùng signature ở subtype là hiding, không runtime override

constructor
→ không được kế thừa, không override
```

Ngoài ra Java không cho đổi một static method thành instance method hoặc ngược lại trong subtype với cùng signature. Đây là conflict ở compile time, không phải một biến thể của overriding.

## <a id="static-method-hiding">Static Method Hiding</a>

Static method thuộc về type/class chứ không tham gia dynamic dispatch theo object runtime như instance method.

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

Việc chọn static method đi theo kiểu được biết ở compile time của biểu thức `value`, tức là `Parent`.

Gọi static method thông qua object có thể compile trong một số trường hợp nhưng dễ gây hiểu nhầm. Nên gọi bằng tên class để ý nghĩa rõ ràng hơn.

`super.someMethod()` là một lời gọi **được chỉ định rõ implementation của supertype**, nên nó không có ý nghĩa giống lời gọi virtual thông thường qua `this.someMethod()`. `super` thường được dùng khi override muốn mở rộng thay vì thay thế hoàn toàn hành vi cha.

## <a id="field-hiding">Field Hiding</a>

Field không có dynamic dispatch.

```java
class Parent { String name = "parent"; }
class Child extends Parent { String name = "child"; }

Parent value = new Child();
System.out.println(value.name); // parent
```

`value.name` được quyết định từ kiểu khai báo của `value`, tức là `Parent`.

Đây là **field hiding**, không phải đa hình của trạng thái.

Hai field cùng tên trong `Parent` và `Child` là **hai member/slot khác nhau**, không phải một field duy nhất được dispatch động. Cast hoặc static type khác nhau có thể làm cùng object được quan sát qua field khác nhau, vì vậy field hiding rất dễ gây nhầm và thường nên tránh.

Việc dùng cùng một tên field ở cả class cha và class con thường làm code khó hiểu, nên tránh nếu không có lý do mạnh.

## <a id="dispatch-vs-hiding">Dispatch và Hiding</a>

Có thể ghi nhớ nhanh bằng bảng sau:

| Thành phần | Thời điểm quyết định chính | Với `Parent x = new Child()` |
| --- | --- | --- |
| Overloaded method signature | Compile time | Dựa trên kiểu của đối số |
| Overridden instance method | Runtime | Có thể chạy body của `Child` |
| Hidden static method | Compile time | Theo kiểu `Parent` |
| Hidden field | Compile time | Theo kiểu `Parent` |

### MINH CHỨNG — `DispatchController#dispatchVsHiding()`

Ví dụ thực thi trả về cùng lúc:

```text
runtime        = Child
instanceMethod = Child.call
staticMethod   = Parent.staticCall
field          = parent-field
```

Cùng một object runtime là `Child`, nhưng các loại member khác nhau lại được chọn theo các quy tắc khác nhau.

Đây là minh chứng rõ nhất để tránh hiểu nhầm:

> “Gọi qua object thì mọi thứ đều được chọn động ở runtime.”

Điều đó **không đúng**.

### MỐI LIÊN HỆ — từ inheritance sang composition

Inheritance kết hợp với overriding rất mạnh, nhưng cây kế thừa càng sâu thì các class càng phụ thuộc nhau chặt.

Nếu mục tiêu chỉ là thay đổi một hành vi, ta có thể không cần tạo thêm class con. **Composition** cho phép thay đối tượng cộng tác thay vì kéo dài cây kế thừa.
