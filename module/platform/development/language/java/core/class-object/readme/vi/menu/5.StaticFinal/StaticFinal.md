# static và final

## <a id="static-vs-instance">Static member và instance member</a>
Instance member cần object receiver và có thể access state của object đó. Static member thuộc class context và nên được gọi qua class name cho rõ ownership. Static method không trực tiếp dùng `this` hay instance field nếu không có object reference explicit.

## <a id="final-variable-reference">Semantics của final primitive/reference</a>
Biến `final` chỉ assign một lần. Với primitive nó cố định primitive value; với reference nó cố định object mà biến refer tới, không làm object đó immutable.

```java
final List<String> names = new ArrayList<>();
names.add("A"); // hợp lệ
// names = new ArrayList<>(); // không hợp lệ
```

## <a id="static-initialization">Static member initialization</a>
Static field và static initializer block chạy trong class initialization, một lần cho mỗi initialized `Class` object/defining loader. Order theo textual initialization sau default value. Trigger chi tiết thuộc classloader/JVM curriculum.

## <a id="constants-design">Constant và compile-time constant</a>
`static final` không tự động là compile-time constant. Primitive/String field initialized bằng constant expression có thể bị inline vào client bytecode, gây compatibility implication khi library constant đổi. Constant cũng nên thật sự immutable, không chỉ là final reference tới mutable object.
