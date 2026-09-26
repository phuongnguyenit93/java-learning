# Access Modifier

Access modifier là cơ chế ngôn ngữ để đặt **ranh giới truy cập**. Nó hỗ trợ encapsulation bằng cách giới hạn mã nào được phép phụ thuộc trực tiếp vào member/type.

## <a id="access-levels">Các mức truy cập</a>

Java có bốn mức chính cho member:

```text
private
→ chỉ trong declaring class

package-private
→ các type trong cùng package

protected
→ package + một số quyền truy cập qua subclass

public
→ mọi nơi có thể nhìn thấy type/member theo quy tắc module/package
```

Không có keyword `package-private`; đó là trạng thái khi không ghi modifier.

## <a id="protected-cross-package">protected khác Package</a>

`protected` thường bị hiểu đơn giản là “subclass truy cập được”. Ngoài package, quy tắc còn phụ thuộc **subclass ngữ cảnh và qualifying reference**.

Một subclass ở package khác không có quyền tùy ý dùng protected member thông qua mọi superclass instance.

Ví dụ, giả sử `Parent` nằm trong package `a` và có một field `protected`:

```java
package a;

public class Parent {
    protected int value;
}
```

Một subclass trong package `b` có thể truy cập member kế thừa qua đúng ngữ cảnh subclass, nhưng không thể truy cập tùy ý qua một reference kiểu `Parent`:

```java
package b;

import a.Parent;

class Child extends Parent {
    void allowed(Child other) {
        this.value = 1;
        other.value = 2;
    }

    void rejected(Parent other) {
        // other.value = 3; // lỗi biên dịch
    }
}
```

Vì vậy không nên ghi nhớ `protected` bằng câu rút gọn “subclass luôn truy cập được”. Hãy xem nó là một quy tắc truy cập cụ thể của ngôn ngữ.

## <a id="encapsulation-boundary">Access Control và Encapsulation</a>

`private` không tự động tạo thiết kế tốt, nhưng access control là công cụ quan trọng để giảm mức phụ thuộc (coupling).

Một nguyên tắc thực dụng:

```text
member không cần public
→ đừng public chỉ để “tiện gọi”

trạng thái có invariant
→ hạn chế raw mutation từ bên ngoài
```

Phạm vi API công khai càng nhỏ thì càng ít bên gọi phụ thuộc trực tiếp vào chi tiết cách triển khai.

## <a id="java-modifier-map">Bản đồ Modifier trong Java</a>

Access modifier chỉ là **một nhóm** trong số các modifier/keyword có thể xuất hiện quanh class, field và method. Không nên học tất cả chúng như một danh sách từ khóa rời rạc; mỗi nhóm tồn tại để giải quyết một loại vấn đề khác nhau.

```text
Quyền truy cập
→ public, protected, private

Class/Object structure
→ static, final

Abstraction / inheritance
→ abstract

Concurrency
→ synchronized, volatile

Serialization
→ transient

Native interoperability
→ native

Floating-point semantics / lịch sử ngôn ngữ
→ strictfp
```

Ý nghĩa ở mức định hướng:

| Keyword | Câu hỏi nó giải quyết | Học sâu ở đâu? |
| --- | --- | --- |
| `public`, `protected`, `private` | Mã nào được phép truy cập member/type? | **Class Object → Access Modifier** |
| `static` | Member thuộc class hay từng object? | **Class Object → Static / Final** |
| `final` | Có được gán lại, override hoặc kế thừa tiếp không, tùy vị trí sử dụng? | **Class Object → Static / Final** |
| `abstract` | Class/method nào chỉ định nghĩa hợp đồng một phần và cần subtype hoàn thiện? | **Abstract Interface** |
| `synchronized` | Nhiều thread phối hợp quyền truy cập vào critical section/monitor như thế nào? | **Concurrency → Thread → Synchronization** |
| `volatile` | Thay đổi của field được các thread khác nhìn thấy và sắp thứ tự theo Java Memory Model như thế nào? | **Concurrency → Thread → Java Memory Model** |
| `transient` | Field nào không tham gia Java native serialization? | **IO → Serialization** |
| `native` | Method nào được triển khai bên ngoài mã Java? | **JNI / native interoperability boundary** |
| `strictfp` | Floating-point strictness được biểu diễn ra sao trong lịch sử Java? | **Numbers / language-history boundary** |

### GHI NHỚ — đừng suy luận theo hình thức

Các keyword trên có thể cùng đứng trước field, method hoặc class nhưng **không vì thế mà chúng cùng một khái niệm**.

Ví dụ:

```java
private volatile boolean running;
```

Ở đây:

```text
private
→ giới hạn quyền truy cập

volatile
→ liên quan visibility/ordering giữa các thread
```

`private` không làm field thread-safe, và `volatile` cũng không tạo encapsulation.

Tương tự:

```java
public synchronized void update() { ... }
```

`public` nói **ai được gọi method**; `synchronized` nói **các thread phối hợp khi thực thi method đó như thế nào**.

Phần Class Object chỉ cần giúp bạn nhận diện đúng vai trò của các modifier. Những modifier gắn với concurrency, serialization hoặc native code nên được học sâu tại module giải thích chính vấn đề mà chúng giải quyết.

Chương tiếp theo tách hai trục khác: **member thuộc class hay object**, và **giá trị/reference có được gán lại hay không**.
