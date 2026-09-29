# Mô hình Type System

Sau toàn bộ module, cần phân biệt hai thế giới thường bị trộn lẫn: **compiler biết gì từ static type** và **runtime biết gì từ object thật**.

## <a id="compile-time-vs-runtime-type">Compile-time Type và Runtime Type</a>

```java
Animal animal = new Dog();
```

Ở đây:

```text
compile-time / declared type
→ Animal

runtime object type
→ Dog
```

Compiler dùng `Animal` để kiểm tra member nào được gọi hợp lệ và nhiều chuyển đổi/overload quy tắc. Runtime object `Dog` tham gia dynamic dispatch cho overridden instance method.

Hai type này trả lời hai câu hỏi khác nhau và cả hai đều cần thiết.

### Static type thuộc về expression/reference view

Compiler không "nhìn xuyên" arbitrary runtime object để cho phép mọi member của subtype:

```java
Animal animal = new Dog();
animal.eat();
// animal.bark(); // không compile nếu Animal không khai báo bark
```

Dù runtime object là `Dog`, expression `animal` có static type `Animal`. Muốn gọi API chỉ có ở `Dog`, code cần type relationship/check/cast phù hợp — hoặc thiết kế abstraction tốt hơn.

### Runtime type ảnh hưởng những check/hành vi nào?

Runtime object type tham gia dynamic dispatch của overridden instance method, downcast checking và array component checking. Nó không làm overload resolution "chạy lại" ở runtime.

## <a id="assignment-compatibility">Assignment Compatibility</a>

Java chỉ cho assignment khi quan hệ giữa value và type phù hợp với các quy tắc của ngôn ngữ:

```java
Dog dog = new Dog();
Animal animal = dog; // upcast hợp lệ
```

Chiều ngược lại cần explicit cast và runtime check:

```java
Dog again = (Dog) animal;
```

Primitive assignment có bộ chuyển đổi quy tắc khác reference assignment.

Type system giúp loại bỏ nhiều lỗi trước runtime, nhưng không chứng minh mọi cast/reference thao tác đều an toàn ở runtime.

### Assignment là một compile-time contract

```java
Object value = "java"; // widening reference conversion
String text = (String) value; // explicit downcast + runtime check
```

Primitive/reference conversion có rule khác nhau:

```java
long n = 10;       // primitive widening
Integer boxed = 10; // boxing
```

Không nên dùng một từ "cast" cho mọi conversion. Phân biệt widening, narrowing, boxing, unboxing và reference cast giúp dự đoán compile/runtime behavior chính xác hơn.

### Một value có thể được convert khác nhau tùy context

Một trong những nguyên nhân Java dễ gây nhầm là **cùng một source value nhưng mỗi context cho phép bộ conversion khác nhau**.

| Context | Mental model | Ví dụ |
|---|---|---|
| assignment | assignment conversion | `long x = 10;` |
| arithmetic/operator | numeric promotion | `byte + byte -> int` |
| method invocation | invocation conversion | `use(long)` nhận argument `int` qua widening |
| explicit cast | casting conversion | `byte b = (byte) x;` |
| object/primitive boundary | boxing / unboxing | `Integer n = 10; int x = n;` |

Điểm quan trọng: một conversion hợp lệ ở context này **không nhất thiết** được compiler tự áp dụng ở context khác.

Ví dụ constant assignment có rule đặc biệt:

```java
byte a = 1; // hợp lệ: constant value fit byte

void use(byte value) { }
// use(1);   // không compile: method invocation không tự dùng narrowing constant conversion này
use((byte) 1);
```

Và operator có promotion riêng:

```java
byte x = 1;
byte y = 2;
// byte z = x + y; // x + y có type int
int z = x + y;
```

Khi gặp câu hỏi "tại sao dòng này compile nhưng dòng gần giống lại không?", hãy hỏi trước: **đây là assignment, operator expression, method invocation hay explicit cast context?**

## <a id="overload-vs-override-dispatch">Overload và Override</a>

Hai cơ chế rất dễ nhầm:

```text
overload selection
→ compile time
→ dựa trên tập method + kiểu đối số/chuyển đổi ở compile time

override dispatch
→ runtime
→ dựa trên runtime receiver type sau khi signature đã được xác định
```

Ví dụ `Parent x = new Child()` có thể chọn overload theo `Parent` đối số type nhưng gọi overridden instance body của `Child`.

OOP module sẽ đi sâu hơn vào dynamic dispatch; ở đây chỉ cần khóa mô hình tư duy compile-time vs runtime.

Ví dụ nối hai phase:

```java
class Parent {
    void speak() { System.out.println("Parent"); }
}

class Child extends Parent {
    @Override
    void speak() { System.out.println("Child"); }
}

void use(Parent x) { x.speak(); }
void use(Object x) { System.out.println("Object"); }

Parent p = new Child();
use(p);
```

Compiler chọn `use(Parent)` dựa trên static type của `p`. Bên trong method, call `x.speak()` dispatch runtime tới `Child.speak()` vì receiver object thật là `Child`.

```text
compile time: chọn signature
        ↓
runtime: dispatch overridden instance body
```

## <a id="type-system-boundaries">Ranh giới Compile Time và Runtime</a>

Compiler có thể kiểm tra:

- name/type resolution;
- assignment compatibility;
- overload applicability;
- definite assignment;
- nhiều access/cast constraints.

Runtime vẫn phải xử lý những điều compiler không thể biết chắc:

- downcast object thật có đúng type không;
- array store có đúng runtime component type không;
- reference có null không;
- index có nằm trong phạm vi hợp lệ của array không.

Đó là lý do Java vừa có static type checking vừa có runtime exception như `ClassCastException`, `ArrayStoreException`, `NullPointerException`.

### Bảng tổng kết các boundary đã gặp

| Tình huống | Compile time biết/kiểm tra | Runtime còn phải kiểm tra |
|---|---|---|
| local variable | definite assignment | value cụ thể |
| overload | applicability + most-specific | không chọn lại overload |
| override | signature đã được chọn | receiver type để dispatch body |
| downcast | cast relationship hợp lệ | object thật có target type không |
| array store | static assignment có vẻ hợp lệ | runtime component type |
| array index | expression là integer-compatible | index có trong bounds không |
| reference dereference | member tồn tại trên static type | reference có null không |

### Một chuỗi suy luận khi đọc Java code

Khi gặp một expression khó, hỏi theo thứ tự:

1. Variable/expression đang có **static type** gì?
2. Value đang là primitive hay reference?
3. Đang ở conversion context nào: assignment, operator, method invocation hay explicit cast?
4. Compiler quyết định gì ngay lúc compile?
5. Runtime còn cần biết object/value thật nào?
6. Mutation đang tác động variable slot hay object được nhiều reference chia sẻ?

Chuỗi câu hỏi này nối gần như toàn bộ `language-basics` thành một mental model thống nhất thay vì các rule rời rạc.

Kết thúc module, hãy giữ chuỗi tư duy:

```text
value model
→ scope/lifetime
→ chuyển đổi/biểu thức
→ control flow
→ method call
→ pass-by-value/reference sharing
→ arrays/packages/null
→ kiểu compile-time và hành vi runtime
```
