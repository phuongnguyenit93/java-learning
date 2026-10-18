# Phạm vi truy cập thành viên

Từ bổ nghĩa truy cập là cơ chế ngôn ngữ để đặt **ranh giới truy cập**. Nó hỗ trợ đóng gói bằng cách giới hạn mã nào được phép phụ thuộc trực tiếp vào thành viên hoặc kiểu.

## <a id="access-levels">Các mức truy cập</a>

Java có bốn mức chính cho thành viên:

```text
private
→ chỉ trong lớp khai báo

package-private
→ các kiểu trong cùng package

protected
→ package + một số quyền truy cập qua lớp con

public
→ mọi nơi có thể nhìn thấy kiểu/thành viên theo quy tắc module/package
```

Không có từ khóa `package-private`; đó là mức truy cập khi không ghi từ bổ nghĩa truy cập.

## <a id="protected-cross-package">protected giữa các package</a>

`protected` thường bị hiểu đơn giản là “lớp con truy cập được”. Ngoài package, quy tắc còn phụ thuộc vào **ngữ cảnh lớp con và kiểu tham chiếu dùng để truy cập**.

Một lớp con ở package khác không có quyền tùy ý dùng thành viên `protected` thông qua mọi đối tượng của lớp cha.

Ví dụ, giả sử `Parent` nằm trong package `a` và có một field `protected`:

```java
package a;

public class Parent {
    protected int value;
}
```

Một lớp con trong package `b` có thể truy cập thành viên kế thừa qua đúng ngữ cảnh lớp con, nhưng không thể truy cập tùy ý qua một tham chiếu kiểu `Parent`:

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

Vì vậy không nên ghi nhớ `protected` bằng câu rút gọn “lớp con luôn truy cập được”. Hãy xem nó là một quy tắc truy cập cụ thể của ngôn ngữ.

## <a id="encapsulation-boundary">Kiểm soát truy cập và đóng gói</a>

`private` không tự động tạo thiết kế tốt, nhưng kiểm soát truy cập là công cụ quan trọng để giảm mức phụ thuộc giữa các phần mã.

Một nguyên tắc thực dụng:

```text
thành viên không cần public
→ đừng public chỉ để “tiện gọi”

trạng thái có điều kiện hợp lệ cần bảo vệ
→ hạn chế việc thay đổi trực tiếp từ bên ngoài
```

Phạm vi API công khai càng nhỏ thì càng ít bên gọi phụ thuộc trực tiếp vào chi tiết cách triển khai.

## <a id="java-modifier-map">Phân biệt với các từ bổ nghĩa khác</a>

Từ bổ nghĩa truy cập chỉ là **một nhóm** trong số các từ bổ nghĩa/từ khóa có thể xuất hiện quanh lớp, trường và phương thức. Không nên học tất cả chúng như một danh sách phẳng; mỗi nhóm giải quyết một loại vấn đề khác nhau. Bảng dưới đây chỉ giúp nhận diện ranh giới, không thay thế các mô-đun chuyên sâu tương ứng.

```text
Quyền truy cập
→ public, protected, private

Thành viên thuộc lớp hay từng đối tượng
→ static

Giới hạn gán lại / ghi đè / kế thừa
→ final

Trừu tượng hóa / kế thừa
→ abstract

Lập trình đồng thời
→ synchronized, volatile

Tuần tự hóa
→ transient

Tương tác với mã gốc (native)
→ native

Ngữ nghĩa dấu phẩy động / lịch sử ngôn ngữ
→ strictfp
```

Ý nghĩa ở mức định hướng:

| Từ khóa | Câu hỏi nó giải quyết | Học sâu ở đâu? |
| --- | --- | --- |
| `public`, `protected`, `private` | Mã nào được phép truy cập thành viên/kiểu? | **Class Object → Phạm vi truy cập** |
| `static` | Thành viên thuộc lớp hay từng đối tượng? | **Class Object → static / final** |
| `final` | Có được gán lại, ghi đè hoặc kế thừa tiếp không, tùy vị trí sử dụng? | **Class Object → static / final** |
| `abstract` | Lớp/phương thức nào chỉ định nghĩa hợp đồng một phần và cần kiểu con hoàn thiện? | **Lớp trừu tượng và giao diện** |
| `synchronized` | Nhiều luồng phối hợp khi đi vào vùng mã cần đồng bộ như thế nào? | **Lập trình đồng thời → Luồng → Đồng bộ** |
| `volatile` | Thay đổi của trường được các luồng khác nhìn thấy và sắp thứ tự theo Java Memory Model như thế nào? | **Lập trình đồng thời → Luồng → Java Memory Model** |
| `transient` | Trường nào không tham gia cơ chế tuần tự hóa Java? | **IO → Tuần tự hóa** |
| `native` | Phương thức nào được triển khai bên ngoài mã Java? | **JNI / ranh giới tương tác với mã gốc** |
| `strictfp` | Quy tắc dấu phẩy động nghiêm ngặt được biểu diễn ra sao trong lịch sử Java? | **Kiểu số / ranh giới lịch sử ngôn ngữ** |

### GHI NHỚ — đừng suy luận theo hình thức

Các từ khóa trên có thể cùng đứng trước trường, phương thức hoặc lớp nhưng **không vì thế mà chúng cùng một khái niệm**.

Ví dụ:

```java
private volatile boolean running;
```

Ở đây:

```text
private
→ giới hạn quyền truy cập

volatile
→ liên quan khả năng nhìn thấy thay đổi và thứ tự giữa các luồng
```

`private` không làm trường trở nên an toàn khi nhiều luồng cùng truy cập, và `volatile` cũng không tạo đóng gói.

Tương tự:

```java
public synchronized void update() { ... }
```

`public` nói **ai được gọi phương thức**; `synchronized` nói **các luồng phối hợp khi thực thi phương thức đó như thế nào**.

Phần Class Object chỉ cần giúp bạn nhận diện đúng vai trò của các từ bổ nghĩa. Những từ bổ nghĩa gắn với lập trình đồng thời, tuần tự hóa hoặc mã gốc (native) nên được học sâu tại mô-đun giải thích chính vấn đề mà chúng giải quyết.

Chương tiếp theo tách hai trục khác: **thành viên thuộc lớp hay đối tượng**, và **giá trị/tham chiếu có được gán lại hay không**.
