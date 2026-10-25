<a id="back-to-top"></a>

# Interface hàm

## Menu
- [Khái niệm functional interface và một hợp đồng hàm duy nhất](#functional-interfaces-001)
- [@FunctionalInterface và kiểm tra tại thời điểm biên dịch](#functional-interfaces-002)
- [Cách xác định phương thức abstract từ các interface cha](#functional-interfaces-003)
- [Loại trừ phương thức public của Object khi xác định hợp đồng hàm](#functional-interfaces-004)
- [Default, static và private method không tạo thêm yêu cầu hàm](#functional-interfaces-005)
- [Lambda expression như một cách cung cấp triển khai](#functional-interfaces-006)
- [Method reference và kiểu đích của biểu thức](#functional-interfaces-007)
- [Kiểu tham số, kiểu trả về và checked exception của hợp đồng hàm](#functional-interfaces-008)
- [Giới hạn với sealed interface và phương thức hàm có kiểu tổng quát](#functional-interfaces-009)
- [Chọn interface riêng hay dùng Runnable, Callable và java.util.function](#functional-interfaces-010)

## <a id="functional-interfaces-001">Khái niệm functional interface và một hợp đồng hàm duy nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Functional interface và hợp đồng hàm duy nhất trong Java

**Functional interface** (interface hàm) là interface có một phương thức trừu tượng chức năng duy nhất theo quy tắc của Java. Hợp đồng này cho biết một hành vi cần nhận tham số gì và tạo kết quả nào; lambda expression hoặc method reference có thể cung cấp phần triển khai cho nó.

## 1. Một hợp đồng được dùng như thế nào?

Ví dụ, hợp đồng cộng hai số nguyên có thể được mô tả bằng interface:

```java
@FunctionalInterface
interface IntOperation {
    int apply(int left, int right);
}

public class FunctionalInterfaceConceptDemo {
    public static void main(String[] args) {
        IntOperation add = (left, right) -> left + right;
        System.out.println(add.apply(3, 4));
    }
}
```

Lưu thành `FunctionalInterfaceConceptDemo.java`, sau đó biên dịch và chạy:

```bash
javac FunctionalInterfaceConceptDemo.java
java FunctionalInterfaceConceptDemo
```

Kết quả:

```text
7
```

Kiểu biến `IntOperation` là **kiểu đích** của lambda. Nhờ chữ ký `apply(int, int)`, compiler biết lambda phải nhận hai số nguyên và trả về một kết quả tương thích với `int`. Lambda cung cấp hành vi cho hợp đồng; nó không tạo thêm phương thức hay thay đổi khai báo interface.

## 2. “Một phương thức” không có nghĩa chỉ có một dòng khai báo

Khi xác định functional interface, Java xét các phương thức abstract được khai báo và kế thừa cùng những quy tắc về chữ ký:

- Các phương thức abstract độc lập như `read()` và `write(String)` tạo ra hai nghĩa vụ khác nhau. Một lambda đơn lẻ không thể triển khai cả hai.
- Những khai báo abstract kế thừa có chữ ký tương đương và kiểu trả về tương thích có thể cùng đại diện cho một hợp đồng chức năng.
- Các phương thức `default`, `static` và `private` có thân không tạo thêm nghĩa vụ abstract.
- Phương thức abstract tương ứng với public instance method của `Object`, như `equals(Object)`, được xử lý theo quy tắc riêng và không tự tạo thêm SAM.
- Một `sealed interface` không thể làm kiểu đích cho lambda, dù chỉ khai báo một phương thức abstract.

Các bài về kế thừa interface, phương thức của `Object`, các loại method và giới hạn của `sealed interface` đi sâu vào từng trường hợp. Điểm cần nhớ ở đây là compiler xác định **một hợp đồng chức năng hợp lệ**, chứ không đếm đơn giản số dòng có từ khóa `abstract`.

Ví dụ sau có hai hợp đồng abstract độc lập nên không phải functional interface:

```java
interface ReadWrite {
    String read();
    void write(String value);
}
```

Interface này vẫn hợp lệ trong Java và có thể được triển khai bằng một lớp cụ thể. Tuy nhiên, không thể dùng một lambda để cung cấp đồng thời cả `read()` và `write(String)`.

## 3. Vai trò của `@FunctionalInterface`

Gắn `@FunctionalInterface` lên interface để yêu cầu compiler kiểm tra ý định thiết kế đó. Annotation là tùy chọn: interface đáp ứng quy tắc Java vẫn có thể làm kiểu đích của lambda dù không ghi annotation. Khi có annotation, thay đổi khiến interface không còn functional sẽ bị phát hiện ngay lúc biên dịch.

Annotation không tự tạo hành vi, không sinh phần thân cho phương thức abstract và không chứng minh thuật toán của lambda là đúng. Bài tiếp theo tập trung vào cách compiler kiểm tra annotation và cách nó giúp bảo vệ API khi được sửa đổi.

## 4. Khi nào nên dùng functional interface?

Dùng functional interface khi API cần nhận một hành vi đơn lẻ, chẳng hạn quy tắc so sánh, điều kiện lọc, phép biến đổi, callback hoặc tác vụ. Với hành vi phổ biến, ưu tiên kiểu chuẩn như `Predicate<T>`, `Function<T,R>` hoặc `Runnable` khi chữ ký phù hợp. Tạo interface riêng khi tên miền nghiệp vụ, tham số hoặc hợp đồng ngoại lệ cần được diễn đạt rõ hơn.

Trước khi chọn kiểu, hãy xác định hành vi cần đầu vào nào, kết quả ra sao và API nào sẽ gọi nó. Nếu một interface đòi hỏi nhiều hành vi abstract độc lập, hãy dùng một interface thông thường hoặc tách trách nhiệm thành các hợp đồng riêng.

## 5. Tự kiểm tra

1. Vì sao `IntOperation add = (a, b) -> a + b;` biên dịch được?  
   **Đáp án:** `IntOperation` cung cấp kiểu đích và quy định hai tham số `int` cùng kết quả `int`.

2. Interface có một `default` method và một abstract method có thể là functional interface không?  
   **Đáp án:** Có. Phương thức default đã có thân nên không tạo thêm nghĩa vụ abstract.

3. Vì sao `ReadWrite` không thể được triển khai bằng một lambda duy nhất?  
   **Đáp án:** Nó có hai phương thức abstract độc lập cần hai phần triển khai.

**Ghi nhớ:** Functional interface xác định một hợp đồng hàm mà lambda hoặc method reference có thể thực hiện. Hãy xét quy tắc chữ ký và kế thừa của Java, rồi chọn một kiểu đích diễn đạt đúng hành vi mà API cần nhận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-002">@FunctionalInterface và kiểm tra tại thời điểm biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# `@FunctionalInterface` và kiểm tra tại thời điểm biên dịch

`@FunctionalInterface` là annotation dùng để thể hiện rằng một interface được thiết kế làm **functional interface** và yêu cầu compiler xác minh điều đó. Nếu khai báo không đáp ứng quy tắc của Java, compiler báo lỗi ngay tại interface, giúp phát hiện thay đổi làm hỏng hợp đồng lambda trước khi chương trình chạy.

## 1. Annotation xác nhận điều gì?

```java
@FunctionalInterface
interface TextOperation {
    String apply(String input);
}

public class FunctionalInterfaceAnnotationDemo {
    public static void main(String[] args) {
        TextOperation upper = text -> text.toUpperCase(java.util.Locale.ROOT);
        System.out.println(upper.apply("java"));
    }
}
```

Lưu thành `FunctionalInterfaceAnnotationDemo.java`, rồi chạy:

```bash
javac FunctionalInterfaceAnnotationDemo.java
java FunctionalInterfaceAnnotationDemo
```

Kết quả:

```text
JAVA
```

Compiler chấp nhận khai báo vì `TextOperation` có một phương thức abstract chức năng là `apply(String)`. Lambda được gán cho kiểu đó nên phải đáp ứng chữ ký nhận `String` và trả về `String`.

Annotation **không bắt buộc** để dùng lambda. Nếu bỏ `@FunctionalInterface`, `TextOperation` vẫn là functional interface và ví dụ vẫn biên dịch. Lợi ích của annotation là biến ý định thiết kế thành một điều kiện compiler có thể kiểm tra.

## 2. Khi thêm phương thức abstract thứ hai

Đoạn sau cố ý không biên dịch:

```java
@FunctionalInterface
interface InvalidOperation {
    String apply(String input);
    String label(String input);
}
```

Hai phương thức có hai mục đích độc lập, nên interface không có một hợp đồng hàm duy nhất. Compiler từ chối annotation. Nếu chỉ bỏ annotation, khai báo có thể tồn tại như interface thông thường, nhưng vẫn không thể dùng một lambda đơn giản để triển khai cả `apply` lẫn `label`.

Khi API cần thêm hành vi, hãy chọn theo ý nghĩa thiết kế:

- Giữ phương thức abstract mới nếu mọi implementation thực sự phải cung cấp hành vi đó; khi ấy interface không còn phù hợp làm kiểu đích lambda.
- Dùng `default` chỉ khi có một cách triển khai mặc định đúng và có ý nghĩa cho mọi implementation.
- Dùng `static` cho tiện ích thuộc về interface, hoặc tách trách nhiệm thành interface riêng nếu hai hành vi không nên bị buộc chung.

Đừng biến một phương thức thành default chỉ để giữ annotation nếu phần thân mặc định không đúng về nghiệp vụ.

## 3. Interface không có phương thức abstract cũng không hợp lệ

Ví dụ này cũng không biên dịch:

```java
@FunctionalInterface
interface NoOperation {
    default void run() {
        System.out.println("running");
    }
}
```

`run()` đã có thân, vì vậy không còn phương thức abstract nào để lambda thực hiện. `@FunctionalInterface` không có nghĩa “interface có thể được gọi bằng lambda”; nó khẳng định interface phải thỏa quy tắc functional interface của Java.

## 4. Compiler áp dụng quy tắc Java đầy đủ

Việc xác định hợp đồng chức năng bao gồm các phương thức abstract được kế thừa, chữ ký tương đương và kiểu trả về. Những khai báo tương ứng với public instance methods của `Object`, cũng như các phương thức `default`, `static` và `private`, được xử lý theo quy tắc ngôn ngữ riêng. Vì vậy, không nên chỉ đếm số dòng khai báo trong interface hiện tại.

Các bài về kế thừa interface, phương thức của `Object` và các loại method trình bày chi tiết các trường hợp đó. Ở đây, điều cần kiểm tra là compiler có chấp nhận toàn bộ khai báo như một functional interface hay không.

## 5. Annotation không kiểm thử logic của chương trình

`@FunctionalInterface` kiểm tra **hình dạng của hợp đồng**, không chứng minh nội dung phương thức là đúng. Compiler vẫn có thể chấp nhận một lambda trả kết quả sai về nghiệp vụ, xử lý null không phù hợp hoặc ném unchecked exception khi chạy.

Ví dụ, cả hai lambda dưới đây đều có thể phù hợp với `TextOperation`, nhưng chỉ một lambda có thể đáp ứng yêu cầu thực tế của ứng dụng:

```java
TextOperation upper = text -> text.toUpperCase(java.util.Locale.ROOT);
TextOperation constant = text -> "JAVA";
```

Annotation cũng không sinh implementation, không tự xử lý ngoại lệ và không bảo đảm hiệu năng. Hãy dùng kiểm thử và tài liệu hợp đồng để kiểm tra hành vi; dùng annotation để giữ đúng cấu trúc functional interface.

## 6. Cách đọc lỗi compiler

Thông báo cụ thể thay đổi theo JDK, nhưng các tình huống thường gặp gồm:

| Tình huống | Nguyên nhân cần kiểm tra |
| --- | --- |
| Annotation bị từ chối vì có nhiều phương thức abstract | Có một phương thức abstract độc lập được thêm vào hoặc được kế thừa |
| Annotation bị từ chối vì không có functional method | Không còn phương thức abstract chức năng sau khi áp dụng quy tắc Java |
| Annotation được đặt trên class thay vì interface | `@FunctionalInterface` chỉ dành cho khai báo interface |
| Lambda dùng interface nhưng không biên dịch | Kiểm tra kiểu đích và chữ ký; bỏ annotation không biến interface nhiều phương thức thành SAM |

Khi sửa lỗi, hãy xem lại hợp đồng và các phương thức được kế thừa trước. Xóa annotation có thể che mất ý định ban đầu mà không khôi phục khả năng sử dụng lambda.

## 7. Checklist khi định nghĩa API

1. Interface có thực sự được thiết kế để nhận lambda hoặc method reference không?
2. Có một phương thức abstract chức năng hợp lệ theo quy tắc Java không?
3. Phương thức bổ sung có thực sự cần là abstract, hay một default/static method có ngữ nghĩa đúng hơn?
4. Tham số, kết quả và checked exception có diễn đạt rõ hợp đồng cần thiết không?
5. Có cần kiểm thử riêng cho logic, dữ liệu biên và lỗi runtime không?

**Ghi nhớ:** `@FunctionalInterface` là một xác nhận được compiler kiểm tra, không phải cơ chế tự tạo hành vi. Hãy dùng nó để bảo vệ ý định thiết kế interface, đồng thời kiểm tra logic và hợp đồng runtime bằng các phương tiện phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-003">Cách xác định phương thức abstract từ các interface cha</a>

<details>
<summary>Xem chi tiết</summary>

# Cách xác định phương thức abstract được kế thừa từ các interface cha

Trong Java, một interface có thể `extends` nhiều interface. Khi đó interface con kế thừa các phương thức thành viên từ toàn bộ hệ interface cha, kể cả những phương thức được khai báo ở các interface tổ tiên xa hơn. Để biết một lớp cụ thể phải cài đặt những phương thức nào, cần xét chữ ký phương thức, quan hệ kế thừa, kiểu trả về và các phương thức `default` đã có phần thân. Không thể chỉ đếm số dòng khai báo có từ khóa `abstract`.

## 1. Phương thức nào trong interface là abstract?

Một phương thức instance được khai báo trong interface mà không có thân thường là phương thức `public abstract` ngầm định. Vì vậy, hai khai báo sau tương đương về ý nghĩa:

```java
interface Reader {
    String read();
}
```

và

```java
interface Reader {
    public abstract String read();
}
```

Các loại phương thức cần phân biệt:

- **Phương thức abstract:** không có thân; lớp cụ thể phải cung cấp một triển khai phù hợp, trừ khi đã có triển khai hợp lệ được kế thừa từ lớp cha hoặc phương thức `default` thích hợp.
- **Phương thức default:** có thân và được kế thừa như một triển khai instance. Lớp có thể dùng luôn hoặc ghi đè.
- **Phương thức static:** thuộc về chính interface khai báo; không được kế thừa như phương thức instance và không tạo nghĩa vụ triển khai cho lớp.
- **Phương thức private:** chỉ phục vụ bên trong interface khai báo, không phải phương thức được interface con kế thừa.

Trong interface, phương thức instance không có thân và không được khai báo `private` hoặc `default` là abstract theo quy tắc của Java, kể cả khi không viết từ khóa `abstract`.

## 2. Xác định nghĩa vụ qua toàn bộ cây interface

Hãy đọc từ interface đang xét rồi lần theo tất cả interface cha trực tiếp và gián tiếp. Ví dụ:

```java
interface HasId {
    String id();
}

interface HasName {
    String name();
}

interface Person extends HasId, HasName {
}
```

`Person` không khai báo phương thức nào trực tiếp, nhưng vẫn kế thừa hai phương thức abstract:

- `String id()` từ `HasId`.
- `String name()` từ `HasName`.

Một lớp cụ thể triển khai `Person` phải đáp ứng cả hai nghĩa vụ. Chương trình độc lập sau chạy được trên Java 17 trở lên:

```java
interface HasId {
    String id();
}

interface HasName {
    String name();
}

interface Person extends HasId, HasName {
}

final class User implements Person {
    @Override
    public String id() {
        return "u-1";
    }

    @Override
    public String name() {
        return "An";
    }
}

public class Main {
    public static void main(String[] args) {
        Person person = new User();
        System.out.println("id=" + person.id());
        System.out.println("name=" + person.name());
    }
}
```

Lưu mã vào `Main.java`, rồi chạy:

```text
javac Main.java
java Main
```

Kết quả chuẩn:

```text
id=u-1
name=An
```

Tên file phải là `Main.java` vì lớp `public` là `Main`. Nếu `User` thiếu một trong hai phương thức, trình biên dịch báo lỗi vì lớp cụ thể chưa thực hiện đầy đủ hợp đồng của `Person`.

## 3. Gom các khai báo có cùng chữ ký

Nhiều interface cha có thể khai báo cùng một phương thức. Nếu các khai báo tương thích và cùng biểu diễn một chữ ký phương thức, lớp chỉ cần một triển khai đáp ứng chúng; không cần tạo một phương thức riêng cho từng interface.

```java
interface A {
    void reset();
}

interface B {
    void reset();
}

interface C extends A, B {
}

final class Device implements C {
    @Override
    public void reset() {
        System.out.println("reset");
    }
}
```

`C` kế thừa hai khai báo `reset()`, nhưng một phương thức `public void reset()` của `Device` đáp ứng cả hai.

Khi so sánh phương thức, hãy chú ý:

- Tên và danh sách kiểu tham số quyết định chữ ký; kiểu trả về không tạo một overload mới.
- Hai phương thức `m(int)` và `m(String)` là hai chữ ký khác nhau, nên có thể tạo hai nghĩa vụ riêng.
- Hai phương thức cùng tên và cùng kiểu tham số nhưng có kiểu trả về không tương thích không thể được xử lý bằng hai phương thức chỉ khác kiểu trả về. Java không cho phép overload chỉ bằng kiểu trả về.
- Điều khoản `throws` không tạo ra một chữ ký khác. Khi ghi đè, kiểu ngoại lệ checked được khai báo phải tuân thủ quy tắc của phương thức bị ghi đè, thường là giữ nguyên hoặc thu hẹp phạm vi ngoại lệ được phép.
- Với generic, phải xét kiểu đã được thay thế theo ngữ cảnh kế thừa, không chỉ so sánh nguyên văn tên kiểu trong khai báo.

### Kiểu trả về covariant

Các khai báo cùng chữ ký có thể có kiểu trả về khác nhau nếu kiểu trả về được chọn tương thích theo quy tắc Java. Ví dụ, `Integer` là kiểu con của `Number`:

```java
interface A {
    Number value();
}

interface B {
    Integer value();
}

interface C extends A, B {
}

final class NumberValue implements C {
    @Override
    public Integer value() {
        return 7;
    }
}

public class Main {
    public static void main(String[] args) {
        C value = new NumberValue();
        System.out.println(value.value());
    }
}
```

Kết quả:

```text
7
```

`Integer value()` có thể đáp ứng cả hai khai báo. Ngược lại, nếu một interface cha yêu cầu `String value()` và interface khác yêu cầu `Integer value()` với cùng tham số, hai kiểu trả về không tương thích; interface con không thể hợp nhất chúng thành một hợp đồng hợp lệ. Đây là lỗi biên dịch, không phải tình huống có thể khắc phục bằng cách viết hai phương thức cùng tên và cùng danh sách tham số.

## 4. Generic và phương thức được kế thừa

Khi interface cha có tham số kiểu, hãy thay tham số kiểu theo cách interface con mở rộng nó:

```java
interface Box<T> {
    T get();
}

interface TextBox extends Box<String> {
}

final class MessageBox implements TextBox {
    @Override
    public String get() {
        return "hello";
    }
}
```

Trong `TextBox`, hợp đồng `T get()` được áp dụng với `T = String`. Vì vậy, triển khai phù hợp trong `MessageBox` là `String get()`.

Với hệ interface generic phức tạp, cần xét cả việc thay tham số kiểu và quy tắc xóa kiểu (type erasure). Các khai báo có thể gây xung đột sau khi xóa kiểu hoặc không có kiểu trả về tương thích sẽ bị trình biên dịch từ chối. Không nên kết luận rằng hai phương thức khác nhau chỉ vì chúng dùng tên tham số kiểu khác nhau trong mã nguồn.

## 5. Phân biệt abstract với default

Phương thức `default` có phần thân nên có thể cung cấp triển khai cho lớp. Nếu một interface con kế thừa một default method từ interface cha cụ thể hơn, triển khai đó thường được ưu tiên hơn một khai báo cùng chữ ký từ interface tổ tiên của nó.

```java
interface A {
    default String label() {
        return "A";
    }
}

interface B extends A {
    @Override
    default String label() {
        return "B";
    }
}

interface C extends B {
}

final class Item implements C {
}

public class Main {
    public static void main(String[] args) {
        System.out.println(new Item().label());
    }
}
```

Kết quả là:

```text
B
```

`Item` không phải tự triển khai `label()` vì có một default method phù hợp được kế thừa qua `B`.

Có hai tình huống quan trọng khác:

1. **Hai default method xung đột:** nếu hai interface cha không có quan hệ cha-con cùng cung cấp default method có chữ ký tương đương, Java không tự chọn một bên. Interface con hoặc lớp triển khai phải ghi đè để giải quyết xung đột. Ví dụ:

   ```java
   interface Left {
       default String name() {
           return "left";
       }
   }

   interface Right {
       default String name() {
           return "right";
       }
   }

   final class Both implements Left, Right {
       @Override
       public String name() {
           return Left.super.name();
       }
   }
   ```

   Ở đây `Both` chọn rõ ràng triển khai của `Left` bằng `Left.super.name()`. Lớp có thể viết phần thân riêng thay vì gọi một default method.

2. **Interface con khai báo lại một default method thành abstract:** interface con có thể cố ý bỏ phần triển khai mặc định đối với hợp đồng đó. Khi ấy lớp cụ thể triển khai interface con phải cung cấp phương thức tương ứng:

   ```java
   interface Base {
       default void run() {
           System.out.println("base");
       }
   }

   interface RequiresRun extends Base {
       @Override
       void run();
   }

   final class Job implements RequiresRun {
       @Override
       public void run() {
           System.out.println("job");
       }
   }
   ```

   `RequiresRun` tái khai báo `run()` mà không có thân, nên lớp `Job` phải triển khai phương thức đó.

Khi abstract method và default method cùng xuất hiện từ các nhánh kế thừa khác nhau, hãy xét quy tắc ghi đè và tính cụ thể của khai báo. Đặc biệt, không suy ra kết quả chỉ từ việc nhìn thấy một phương thức có thân: phải xác định phương thức nào thực sự được kế thừa sau khi áp dụng các quy tắc của Java. Nếu các khai báo xung đột hoặc không tương thích, trình biên dịch sẽ báo lỗi và cần sửa quan hệ khai báo hoặc ghi đè rõ ràng.

## 6. Khi nào lớp đã có triển khai mà không cần viết lại?

Nghĩa vụ abstract có thể được đáp ứng bởi một phương thức instance công khai phù hợp được kế thừa từ lớp cha. Vì thế, không phải lúc nào cũng cần thấy phương thức được viết trực tiếp trong lớp đang xét.

```java
interface Identifiable {
    String id();
}

class BaseRecord {
    public String id() {
        return "base-1";
    }
}

final class Record extends BaseRecord implements Identifiable {
}

public class Main {
    public static void main(String[] args) {
        Identifiable record = new Record();
        System.out.println(record.id());
    }
}
```

Kết quả:

```text
base-1
```

Phương thức `public String id()` được kế thừa từ `BaseRecord` đáp ứng hợp đồng `Identifiable`. Phương thức của lớp cha phải có khả năng truy cập và tương thích với hợp đồng interface; một phương thức `protected` không đáp ứng yêu cầu `public` của phương thức interface.

Quy tắc ưu tiên phương thức của lớp cũng cần được nhớ: một phương thức instance được kế thừa từ lớp cha có vai trò quan trọng khi trùng chữ ký với default method. Nếu lớp cha có phương thức abstract, việc lớp cha khai báo phương thức đó vẫn có thể khiến lớp con phải cung cấp phần thân, dù một interface cũng có default method cùng chữ ký.

Một lớp được khai báo `abstract` có thể giữ lại các nghĩa vụ chưa triển khai. Lớp cụ thể cuối cùng trong chuỗi kế thừa phải đáp ứng tất cả phương thức abstract còn lại.

## 7. Quy trình kiểm tra thực tế

Khi được giao xác định các phương thức abstract mà một interface hoặc lớp kế thừa, hãy làm theo thứ tự sau:

1. **Xác định nút cần xét.** Đó là interface con đang `extends` hay lớp đang `implements` interface nào?
2. **Liệt kê toàn bộ interface cha.** Theo các nhánh cha trực tiếp và gián tiếp, tránh bỏ sót tổ tiên xa.
3. **Phân loại phương thức.** Ghi lại các phương thức instance abstract, default, static và private; static/private không tạo nghĩa vụ instance cho lớp triển khai.
4. **Chuẩn hóa chữ ký.** Xét tên, tham số, generic đã thay thế và kiểu trả về. Gom các khai báo tương đương, rồi kiểm tra chúng có tương thích hay không.
5. **Giải quyết kế thừa và ghi đè.** Xác định khai báo nào cụ thể hơn, default nào có thể được dùng, khai báo abstract nào ghi đè default, và có xung đột từ nhiều nhánh hay không.
6. **Kiểm tra triển khai từ lớp cha.** Một phương thức instance `public` đã được kế thừa có thể đáp ứng hợp đồng; không chỉ tìm trong phần thân của lớp hiện tại.
7. **Liệt kê nghĩa vụ còn lại.** Với lớp cụ thể, mỗi phương thức abstract còn lại phải có triển khai phù hợp và công khai. Với lớp abstract, có thể giữ lại nghĩa vụ để lớp con thực hiện.
8. **Để trình biên dịch xác nhận.** Biên dịch toàn bộ các interface và lớp liên quan. Lỗi biên dịch giúp phát hiện chữ ký không tương thích, xung đột default, thiếu triển khai hoặc lỗi generic mà việc đọc từng tệp riêng lẻ dễ bỏ qua.

Có thể dùng bảng kiểm nhanh sau:

| Dấu hiệu | Cách xử lý |
| --- | --- |
| Interface khai báo `void m();` | Xem là phương thức abstract ngầm định |
| Hai interface cha cùng khai báo `m()` tương thích | Một triển khai có thể đáp ứng cả hai |
| Các kiểu trả về cùng chữ ký tương thích theo quan hệ subtype | Chọn triển khai có kiểu trả về đáp ứng tất cả hợp đồng |
| Kiểu trả về cùng chữ ký không tương thích | Lỗi biên dịch; không thể tạo hai overload chỉ khác kiểu trả về |
| Có default method phù hợp từ interface cha | Có thể dùng triển khai được kế thừa nếu không bị ghi đè hoặc xung đột |
| Hai default method không liên quan xung đột | Ghi đè để giải quyết rõ ràng |
| Interface con khai báo lại default method mà không có thân | Nghĩa vụ abstract được khôi phục cho lớp triển khai |
| Lớp cha đã có phương thức `public` tương thích | Phương thức kế thừa có thể đáp ứng hợp đồng |
| Lớp được khai báo `abstract` | Có thể giữ lại các phương thức chưa triển khai |

## 8. Những lỗi suy luận thường gặp

- **Chỉ đọc interface trực tiếp:** phương thức có thể được kế thừa từ tổ tiên xa hơn nhiều cấp.
- **Đếm từng khai báo thành một nghĩa vụ riêng:** nhiều khai báo cùng chữ ký và tương thích có thể được đáp ứng bởi một triển khai.
- **Bỏ qua `default`:** phương thức có thân có thể đáp ứng hợp đồng, nhưng vẫn phải kiểm tra quan hệ cụ thể và xung đột.
- **Coi static/private là phương thức phải triển khai:** hai loại này không phải nghĩa vụ instance được kế thừa cho lớp.
- **Dùng kiểu trả về để phân biệt overload:** Java không cho phép hai phương thức chỉ khác kiểu trả về.
- **Bỏ qua generic:** phải thay tham số kiểu theo interface con trước khi xác định chữ ký và kiểu trả về.
- **Cho rằng mọi lớp đều phải viết lại phương thức:** triển khai phù hợp có thể đã được kế thừa từ lớp cha hoặc default method.
- **Cho rằng lớp abstract phải hoàn tất mọi phương thức:** lớp abstract có thể chuyển nghĩa vụ còn lại cho lớp con, còn lớp cụ thể phải đáp ứng chúng.
- **Tự đoán khi các nhánh kế thừa xung đột:** kiểm tra quy tắc override và kết quả biên dịch; không tùy ý chọn một khai báo.

Điểm cốt lõi là xác định **tập phương thức thực sự được kế thừa sau khi áp dụng quy tắc chữ ký, kiểu trả về, ghi đè và default method**. Tập phương thức abstract còn lại mới là danh sách nghĩa vụ cần triển khai, chứ không phải tổng số dòng khai báo trong các interface cha.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-004">Loại trừ phương thức public của Object khi xác định hợp đồng hàm</a>

<details>
<summary>Xem chi tiết</summary>

# Loại trừ phương thức public của `Object` khi xác định hợp đồng hàm

Trong Java, một **functional interface** (interface hàm) cung cấp một hợp đồng gồm một phương thức trừu tượng để lambda expression hoặc method reference có thể biểu diễn hành vi của nó. Khi xác định hợp đồng đó, Java **không tính các khai báo abstract override-equivalent với phương thức public của `Object`** như `equals(Object)`, `hashCode()` và `toString()` vào phương thức chức năng.

Quy tắc này cho phép interface khai báo lại phương thức phổ biến của mọi đối tượng mà vẫn giữ được một phương thức chức năng duy nhất. Java xét chữ ký và quan hệ override-equivalent, không chỉ xét tên phương thức.

## 1. Hợp đồng hàm là gì?

Một functional interface có một phương thức chức năng (functional method, thường gọi là SAM — *Single Abstract Method*). Chữ ký của phương thức này xác định tham số, kiểu kết quả và các ngoại lệ mà lambda hoặc method reference phải đáp ứng.

```java
@FunctionalInterface
interface TextConsumer {
    void accept(String value);
}
```

`TextConsumer` có một phương thức abstract là `accept(String)`, vì vậy có thể làm kiểu đích cho lambda:

```java
TextConsumer printer = value -> System.out.println(value);
printer.accept("Java");
```

Khi biên dịch, Java kiểm tra kiểu đích và xác nhận lambda phù hợp với chữ ký của phương thức chức năng.

## 2. Vì sao phải loại trừ phương thức public của `Object`?

Mọi đối tượng Java đều có các phương thức công khai kế thừa từ `Object`. Những ví dụ thường gặp là:

- `boolean equals(Object other)` — so sánh tính bằng nhau.
- `int hashCode()` — cung cấp mã băm.
- `String toString()` — cung cấp biểu diễn dạng chuỗi.

Một interface có thể khai báo lại các phương thức này để thể hiện hợp đồng mà nó mong muốn. Tuy nhiên, các khai báo đó không tạo thêm phương thức chức năng, vì chúng tương ứng với phương thức public đã có trên mọi đối tượng Java.

```java
@FunctionalInterface
interface TextConsumer {
    void accept(String value);

    boolean equals(Object other);
    int hashCode();
    String toString();
}
```

Interface trên vẫn là functional interface. `accept(String)` là phương thức chức năng duy nhất; `equals(Object)`, `hashCode()` và `toString()` không được tính thành phương thức chức năng bổ sung. Lớp triển khai hoặc đối tượng lambda vẫn có các phương thức công khai tương ứng của `Object`.

Quy tắc dựa trên việc khai báo có *override-equivalent* với phương thức public của `Object` hay không. Ba phương thức trên là ví dụ phổ biến, không phải quy tắc chỉ dựa vào tên.

## 3. Ví dụ hoàn chỉnh có thể biên dịch và chạy

Lưu nội dung sau vào tệp `FunctionalObjectMethodsDemo.java`:

```java
@FunctionalInterface
interface TextConsumer {
    void accept(String value);

    // Các khai báo này không làm tăng số phương thức chức năng.
    boolean equals(Object other);
    int hashCode();
    String toString();
}

public class FunctionalObjectMethodsDemo {
    public static void main(String[] args) {
        TextConsumer printer =
                value -> System.out.println("Đã nhận: " + value);

        printer.accept("Java");
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```text
javac -encoding UTF-8 FunctionalObjectMethodsDemo.java
java FunctionalObjectMethodsDemo
```

Kết quả chuẩn:

```text
Đã nhận: Java
```

Lambda chỉ cung cấp hành vi cho `accept(String)`. Ba khai báo còn lại không yêu cầu lambda viết thêm phần thân phương thức. Chúng không thay đổi chữ ký mà lambda phải đáp ứng.

Có thể dùng method reference thay cho lambda trong cùng kiểu đích:

```java
TextConsumer printer = System.out::println;
printer.accept("Java");
```

Method reference cũng được kiểm tra theo phương thức chức năng `accept(String)`.

## 4. Chữ ký quan trọng hơn tên phương thức

Quy tắc loại trừ không áp dụng cho mọi phương thức chỉ vì nó có tên `equals`, `hashCode` hoặc `toString`. Chữ ký phải tương ứng theo quy tắc override-equivalent.

Ví dụ sau **không** phải functional interface:

```java
@FunctionalInterface
interface InvalidAction {
    void run();

    boolean equals(String other);
}
```

`equals(String)` không override-equivalent với `equals(Object)` của `Object` vì kiểu tham số khác nhau. Do đó, interface có hai phương thức abstract độc lập: `run()` và `equals(String)`. Chú thích `@FunctionalInterface` khiến trình biên dịch báo lỗi vì interface không đáp ứng điều kiện.

Tương tự, một phương thức có cùng tên nhưng thêm tham số, đổi kiểu tham số hoặc có chữ ký khác không tự động được loại trừ. Hãy xét toàn bộ chữ ký và quan hệ kế thừa thay vì chỉ tìm tên phương thức.

## 5. Interface chỉ khai báo lại một phương thức của `Object` thì sao?

```java
@FunctionalInterface
interface EqualityOnly {
    boolean equals(Object other);
}
```

Interface này không còn phương thức chức năng nào sau khi áp dụng quy tắc loại trừ. Vì vậy, nó không phải functional interface và trình biên dịch sẽ từ chối `@FunctionalInterface`. Không thể dùng nó làm kiểu đích cho lambda như thể nó có một SAM.

Điều này khác với `TextConsumer` ở ví dụ trước: `TextConsumer` còn có `accept(String)`, nên sau khi loại trừ các khai báo tương ứng với `Object` vẫn còn đúng một phương thức chức năng.

## 6. Phương thức kế thừa có cùng chữ ký

Khi interface kế thừa nhiều khai báo abstract có cùng chữ ký và kiểu trả về tương thích, các khai báo đó có thể quy về cùng một phương thức chức năng. Vì vậy, không nên chỉ đếm số dòng khai báo abstract trong mã nguồn.

```java
interface HasName {
    String value();
}

interface HasLabel {
    String value();
}

@FunctionalInterface
interface NamedValue extends HasName, HasLabel {
}
```

`NamedValue` vẫn có thể là functional interface vì hai khai báo `value()` tương thích và quy về cùng một chữ ký. Trường hợp này khác với việc loại trừ phương thức của `Object`: ở đây, các khai báo được hợp nhất do chữ ký và kế thừa; còn phương thức public của `Object` được bỏ qua khi xác định phương thức chức năng.

Nếu các khai báo kế thừa không tương thích, chẳng hạn kiểu trả về không thể dung hòa, bản thân interface có thể không biên dịch được. Quy tắc loại trừ phương thức của `Object` không giải quyết các xung đột kiểu như vậy.

## 7. Vai trò của `@FunctionalInterface`

`@FunctionalInterface` là chú thích kiểm tra tại thời điểm biên dịch. Nó yêu cầu trình biên dịch xác nhận interface đáp ứng quy tắc functional interface. Chú thích này không biến một interface bất kỳ thành functional interface và cũng không thay đổi cách xác định phương thức chức năng.

Nên dùng chú thích này khi chủ đích thiết kế interface cho lambda hoặc method reference. Nếu sau này thêm một phương thức abstract không tương thích với phương thức chức năng hiện có, lỗi biên dịch giúp phát hiện thay đổi làm hỏng hợp đồng.

Các phương thức `default`, `static` và `private` trong interface không phải phương thức abstract để tính vào SAM. Khi kiểm tra, hãy tập trung vào các khai báo abstract và phương thức abstract được kế thừa.

## 8. Cách xác định một interface có phải functional interface hay không

Có thể kiểm tra theo thứ tự sau:

1. Liệt kê các phương thức abstract được khai báo trực tiếp và kế thừa từ interface cha.
2. Loại trừ những khai báo override-equivalent với phương thức public của `Object`, chẳng hạn `equals(Object)`, `hashCode()` và `toString()`.
3. Xem các khai báo còn lại có cùng chữ ký và kiểu trả về tương thích để quy về một phương thức chức năng hay không.
4. Kiểm tra rằng kết quả có đúng một phương thức chức năng.
5. Thêm `@FunctionalInterface` để trình biên dịch xác nhận ý định này.

Khi lambda báo lỗi không phải functional interface, hãy kiểm tra chữ ký thực tế của các phương thức abstract, kể cả phương thức đến từ interface cha. Đừng giả định rằng các phương thức cùng tên luôn được gộp, hoặc rằng khai báo lại `equals(Object)` sẽ tạo thêm một SAM.

## 9. Những nhầm lẫn thường gặp

- **“Interface có nhiều khai báo abstract thì chắc chắn không phải functional interface.”** Không đúng. Khai báo override-equivalent với phương thức public của `Object` bị loại trừ; các khai báo kế thừa có chữ ký tương thích cũng có thể quy về cùng một phương thức chức năng.
- **“Cứ có tên `equals` là được loại trừ.”** Không đúng. `equals(String)` khác chữ ký với `equals(Object)` và vẫn có thể là một phương thức abstract độc lập.
- **“Thêm `@FunctionalInterface` sẽ làm interface hợp lệ.”** Không đúng. Chú thích chỉ yêu cầu kiểm tra và báo lỗi nếu điều kiện không được đáp ứng.
- **“Lambda phải cung cấp phần thân cho `equals`, `hashCode` và `toString` nếu interface khai báo chúng.”** Không đúng. Các khai báo tương ứng với phương thức public của `Object` không được tính vào phương thức chức năng.
- **“Phải có đúng một dòng khai báo abstract trong tệp.”** Không đúng. Điều cần xác định là phương thức chức năng sau khi xét chữ ký, kế thừa và quy tắc loại trừ.

## Kết luận

Khi xác định hợp đồng hàm của một interface, hãy tìm phương thức abstract còn lại sau khi áp dụng quy tắc chữ ký và kế thừa. Các khai báo override-equivalent với phương thức public của `Object` không làm tăng số phương thức chức năng. Nhờ đó, interface có thể khai báo lại `equals(Object)`, `hashCode()` hoặc `toString()` mà vẫn dùng được với lambda, miễn là nó có đúng một phương thức chức năng thực sự.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-005">Default, static và private method không tạo thêm yêu cầu hàm</a>

<details>
<summary>Xem chi tiết</summary>

# Default, static và private method trong functional interface

Trong Java, một **functional interface** (giao diện hàm) có đúng một phương thức trừu tượng theo quy tắc của ngôn ngữ. Phương thức trừu tượng này là hợp đồng mà lớp triển khai phải cung cấp; nó cũng là phương thức được dùng khi gán lambda hoặc method reference cho kiểu giao diện.

Interface vẫn có thể khai báo thêm nhiều phương thức `default`, `static` và `private`. Các phương thức này có phần thân nên không tạo thêm yêu cầu phương thức trừu tượng. Vì vậy, một interface có nhiều phương thức được khai báo vẫn có thể là functional interface.

## 1. Phương thức trừu tượng là phần hợp đồng bắt buộc

Phương thức khai báo trong interface mà không có phần thân thường là phương thức trừu tượng. Phương thức đó phải được lớp triển khai thực hiện, trừ khi lớp triển khai tiếp tục là `abstract`.

```java
String process(String text);
```

Với phương thức trên, một lớp cụ thể triển khai interface phải cung cấp cách xử lý `process`. Nếu interface có thêm một phương thức trừu tượng độc lập khác, nó thường không còn đáp ứng điều kiện functional interface.

`@FunctionalInterface` giúp trình biên dịch kiểm tra điều kiện này. Annotation không bắt buộc để viết lambda, nhưng rất hữu ích vì trình biên dịch sẽ báo lỗi nếu các thay đổi sau này làm interface không còn là functional interface.

## 2. `default` method cung cấp hành vi mặc định

Phương thức `default` là phương thức instance có phần thân trong interface. Lớp triển khai được thừa hưởng hành vi mặc định và có thể ghi đè nó khi cần.

```java
default String processOrEmpty(String text) {
    return process(text == null ? "" : text);
}
```

Ở ví dụ này, `processOrEmpty` gọi phương thức trừu tượng `process` và xử lý trường hợp đầu vào `null`. Lớp triển khai chỉ cần định nghĩa `process`; không bắt buộc ghi đè `processOrEmpty`. Vì vậy, phương thức `default` không làm tăng số phương thức trừu tượng.

`default` method phù hợp khi muốn bổ sung hành vi dùng chung cho các lớp triển khai mà vẫn giữ khả năng tương thích với những lớp cũ. Nếu hai interface cung cấp các `default` method xung đột cùng chữ ký, lớp triển khai có thể phải tự ghi đè để giải quyết xung đột.

## 3. `static` method thuộc về interface

Phương thức `static` có phần thân và được gọi bằng tên interface. Nó không phải hành vi instance mà lớp triển khai phải cung cấp, cũng không được thừa hưởng như một phương thức instance thông thường.

```java
TextProcessor.trimmed();
```

Có thể đặt các hàm tạo, tiện ích hoặc factory method trong interface. Chẳng hạn, một static method có thể trả về một lambda mang kiểu interface. Bản thân static method không phải phương thức trừu tượng và không làm mất tính chất functional của interface.

## 4. `private` method là hàm trợ giúp nội bộ

Interface hỗ trợ `private` method từ Java 9. Phương thức private phải có phần thân và dùng để chia sẻ đoạn xử lý giữa các phương thức khác trong cùng interface.

- `private` instance method có thể được gọi từ phương thức instance có quyền truy cập trong interface, chẳng hạn một `default` method.
- `private static` method có thể được gọi từ các phương thức static khác trong interface.
- Lớp triển khai và interface con không thừa hưởng hay gọi trực tiếp các phương thức private này.

Vì là phương thức có phần thân và chỉ phục vụ triển khai nội bộ, `private` method không tạo thêm hợp đồng trừu tượng cho lớp triển khai.

## 5. Ví dụ Java hoàn chỉnh

Ví dụ sau có một phương thức trừu tượng `process` cùng các phương thức `default`, `static`, `private` và `private static`. Interface vẫn được đánh dấu `@FunctionalInterface` và có thể nhận lambda.

```java
import java.util.Locale;

public class FunctionalInterfaceMethodsDemo {

    @FunctionalInterface
    interface TextProcessor {
        String process(String text);

        default String processOrEmpty(String text) {
            return process(text == null ? "" : text);
        }

        default String processWrapped(String text) {
            return "[" + trimForDisplay(process(text)) + "]";
        }

        static TextProcessor trimmed() {
            return text -> normalize(text);
        }

        static TextProcessor normalized() {
            return text -> normalize(text).toLowerCase(Locale.ROOT);
        }

        private String trimForDisplay(String text) {
            return text == null ? "" : text.trim();
        }

        private static String normalize(String text) {
            return text == null ? "" : text.trim();
        }
    }

    public static void main(String[] args) {
        TextProcessor upper = text -> text.toUpperCase(Locale.ROOT);
        System.out.println(upper.process("java"));
        System.out.println(upper.processOrEmpty(null));
        System.out.println(TextProcessor.trimmed().process("  Java  "));
        System.out.println(TextProcessor.normalized().process("  JaVa  "));
        System.out.println(upper.processWrapped(" java "));
    }
}
```

Lưu đoạn mã vào tệp `FunctionalInterfaceMethodsDemo.java`, sau đó chạy bằng JDK 17 trở lên:

```bash
javac FunctionalInterfaceMethodsDemo.java
java FunctionalInterfaceMethodsDemo
```

Kết quả stdout:

```text
JAVA

Java
java
[JAVA]
```

Dòng thứ hai là dòng trống: `processOrEmpty(null)` chuyển `null` thành chuỗi rỗng rồi truyền chuỗi đó cho lambda `upper`.

Trong ví dụ:

- `process` là phương thức trừu tượng duy nhất mà lambda phải thực hiện.
- `processOrEmpty` và `processWrapped` là các phương thức instance `default` được thừa hưởng. `processWrapped` dùng `trimForDisplay`, một private instance helper.
- `trimmed` và `normalized` là static factory method, gọi bằng `TextProcessor`. Cả hai dùng private static helper `normalize`.
- `trimForDisplay` và `normalize` có phần thân và chỉ hỗ trợ triển khai bên trong interface.

## 6. So sánh nhanh

| Loại phương thức | Có phần thân | Lớp triển khai phải tự cung cấp | Được tính là phương thức trừu tượng |
| --- | --- | --- | --- |
| Phương thức trừu tượng | Không | Có, nếu lớp là concrete | Có |
| `default` | Có | Không; có thể ghi đè | Không |
| `static` | Có | Không | Không |
| `private` instance | Có | Không | Không |
| `private static` | Có | Không | Không |

Bảng trên nói về các loại phương thức trong interface. Quy tắc xác định functional interface còn xét cả phương thức được kế thừa và các chữ ký tương đương nhau; không nên chỉ đếm số dòng khai báo trong một interface.

## 7. Những điểm dễ nhầm

1. **Functional interface không có nghĩa là chỉ được khai báo một phương thức tổng cộng.** Nó có thể có nhiều phương thức `default`, `static` và `private`, miễn vẫn đáp ứng quy tắc về phương thức trừu tượng.
2. **Thêm một phương thức trừu tượng độc lập có thể làm hỏng hợp đồng lambda.** Khi interface có `@FunctionalInterface`, trình biên dịch sẽ báo lỗi nếu thay đổi đó khiến interface không còn phù hợp.
3. **Gọi static method qua tên interface.** Ví dụ là `TextProcessor.trimmed()`. Phương thức này không trở thành phương thức instance của đối tượng `TextProcessor`.
4. **Private interface method yêu cầu Java 9 trở lên.** Nếu cần tương thích với Java 8, không thể dùng private method trong interface.
5. **`default` method là hành vi được cung cấp sẵn.** Lớp triển khai có thể dùng trực tiếp hoặc ghi đè, nên nó không tạo thêm yêu cầu triển khai trừu tượng.

Các phương thức public của `Object` như `equals(Object)` cũng được xử lý theo quy tắc riêng khi xác định functional interface; đừng kết luận rằng mọi chữ ký trừu tượng nhìn thấy đều tạo thêm một yêu cầu lambda độc lập.

## Kết luận

Điều quyết định functional interface là hợp đồng phương thức trừu tượng, không phải tổng số phương thức được khai báo. `default` cung cấp hành vi instance có sẵn, `static` cung cấp hành vi thuộc về interface, còn `private` và `private static` là các hàm trợ giúp nội bộ. Những phương thức này có phần thân nên không tạo thêm yêu cầu phương thức trừu tượng cho lớp triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-006">Lambda expression như một cách cung cấp triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Lambda expression như một cách cung cấp triển khai

Lambda expression (biểu thức lambda) là cú pháp được Java giới thiệu từ Java 8 để truyền một đoạn hành vi vào nơi cần một đối tượng có kiểu **functional interface**. Lambda thường là cách ngắn gọn để cung cấp phần thân cho phương thức trừu tượng duy nhất của interface đó.

Lambda không tự tạo ra một kiểu dữ liệu độc lập. Trình biên dịch xác định kiểu đích từ ngữ cảnh, chẳng hạn biến được gán, tham số phương thức hoặc phép ép kiểu. Vì vậy, một lambda chỉ hợp lệ khi ngữ cảnh xác định được functional interface tương ứng.

## 1. Functional interface là nền tảng của lambda

Functional interface là interface có đúng một phương thức trừu tượng theo quy tắc của Java. Phương thức này còn được gọi là **SAM** (*Single Abstract Method*). Lambda cung cấp phần triển khai cho phương thức đó.

~~~java
@FunctionalInterface
interface Greeter {
    String greet(String name);
}
~~~

Có thể cung cấp cách triển khai bằng lambda:

~~~java
Greeter greeter = name -> "Xin chào, " + name;
System.out.println(greeter.greet("An"));
~~~

Kết quả:

~~~text
Xin chào, An
~~~

Chú thích **@FunctionalInterface** không bắt buộc để dùng lambda, nhưng nên dùng vì trình biên dịch sẽ báo lỗi nếu interface không còn đáp ứng quy tắc functional interface. Interface vẫn có thể khai báo phương thức **default** và **static**; chúng không làm tăng số phương thức trừu tượng. Các phương thức trừu tượng kế thừa có cùng chữ ký cũng có thể được xem là một phương thức duy nhất. Những phương thức tương ứng với phương thức public của **Object**, chẳng hạn **equals(Object)**, không làm tăng số phương thức SAM.

Interface có hai phương thức trừu tượng độc lập không thể được triển khai bằng một lambda đơn lẻ:

~~~java
interface InvalidHandler {
    void start();
    void stop();
}

// Không hợp lệ:
// InvalidHandler handler = () -> System.out.println("run");
~~~

Nếu cần cả hai hành vi, hãy thiết kế lại interface hoặc dùng một lớp triển khai đầy đủ.

## 2. Cú pháp và cách đọc lambda

Các dạng phổ biến:

~~~java
() -> doSomething()
value -> value * 2
(left, right) -> left + right
(String value) -> value.trim()
(value) -> {
    String normalized = value.trim();
    return normalized.toUpperCase();
}
~~~

Quy tắc đọc:

- Phần bên trái **->** là danh sách tham số; phần bên phải là biểu thức hoặc khối lệnh.
- Không có tham số thì dùng **()**.
- Với một tham số có kiểu được suy luận, có thể bỏ cặp ngoặc đơn: **value -> ...**.
- Có nhiều tham số thì đặt trong ngoặc đơn và phân tách bằng dấu phẩy.
- Có thể ghi rõ kiểu tham số, nhưng nếu ghi kiểu thì phải ghi nhất quán cho các tham số trong cùng danh sách.
- Thân lambda là một biểu thức thì giá trị biểu thức được trả về khi phương thức có kiểu trả về. Với thân dạng khối **{ ... }**, phải dùng **return** khi phương thức cần trả về giá trị; khối của phương thức **void** không trả về giá trị.
- Kiểu tham số thường được suy luận từ kiểu đích, vì vậy không cần lặp lại kiểu nếu không giúp mã rõ hơn.

Ví dụ, hai cách sau có thể tương đương khi kiểu đích đã là **Function&lt;Integer, Integer&gt;**:

~~~java
Function<Integer, Integer> doubled = value -> value * 2;
Function<Integer, Integer> alsoDoubled = (Integer value) -> value * 2;
~~~

Trình biên dịch không thể suy luận kiểu đích nếu chỉ viết một lambda đứng riêng. Chẳng hạn, **var f = value -> value + 1;** không hợp lệ vì không có kiểu đích để suy ra kiểu của lambda. Hãy khai báo kiểu functional interface rõ ràng hoặc cung cấp lambda trong một ngữ cảnh đã xác định kiểu.

## 3. Chương trình hoàn chỉnh: tự định nghĩa interface và dùng interface có sẵn

Tạo tệp **LambdaAsImplementationDemo.java**:

~~~java
import java.util.Locale;
import java.util.function.Predicate;

public class LambdaAsImplementationDemo {
    @FunctionalInterface
    interface TextFormatter {
        String format(String value);
    }

    public static void main(String[] args) {
        TextFormatter upper = value -> value.toUpperCase(Locale.ROOT);
        TextFormatter wrapped = value -> "[" + value + "]";

        System.out.println(upper.format("java"));
        System.out.println(wrapped.format("lambda"));

        Predicate<String> nonBlank =
                value -> value != null && !value.isBlank();

        System.out.println(nonBlank.test("hello"));
        System.out.println(nonBlank.test("   "));
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac LambdaAsImplementationDemo.java
java LambdaAsImplementationDemo
~~~

Kết quả stdout chính xác:

~~~text
JAVA
[lambda]
true
false
~~~

Ở ví dụ trên, biến **upper** và **wrapped** có kiểu **TextFormatter**; mỗi lambda cung cấp triển khai cho phương thức **format**. Biến **nonBlank** có kiểu **Predicate&lt;String&gt;**; lambda cung cấp triển khai cho phương thức **test**, trả về **true** hoặc **false**. Điều kiện kiểm tra **value != null** phải đứng trước **value.isBlank()** để tránh gọi phương thức trên giá trị null. **String.isBlank()** có từ Java 11.

## 4. Các functional interface thường gặp trong thư viện Java

| Interface | Phương thức trừu tượng | Ý nghĩa thường dùng |
|---|---|---|
| **Runnable** | **void run()** | Hành động không tham số, không trả kết quả |
| **Supplier&lt;T&gt;** | **T get()** | Cung cấp một giá trị |
| **Consumer&lt;T&gt;** | **void accept(T)** | Nhận và xử lý một giá trị |
| **Function&lt;T, R&gt;** | **R apply(T)** | Chuyển một giá trị kiểu T thành kiểu R |
| **Predicate&lt;T&gt;** | **boolean test(T)** | Kiểm tra một điều kiện |
| **UnaryOperator&lt;T&gt;** | **T apply(T)** | Nhận và trả về cùng kiểu T |
| **BiFunction&lt;T, U, R&gt;** | **R apply(T, U)** | Kết hợp hai đầu vào để tạo kết quả |
| **BinaryOperator&lt;T&gt;** | **T apply(T, T)** | Kết hợp hai giá trị cùng kiểu để tạo giá trị cùng kiểu |
| **Comparator&lt;T&gt;** | **int compare(T, T)** | Xác định thứ tự so sánh hai đối tượng |

Các interface phổ biến trên nằm trong **java.lang** hoặc **java.util.function**; **Comparator** thuộc **java.util**. Những kiểu này cho phép API nhận hành vi dưới dạng tham số thay vì buộc người gọi tạo một lớp riêng cho từng trường hợp.

~~~java
Predicate<String> startsWithJ = text -> text.startsWith("J");
Function<String, Integer> length = text -> text.length();
Consumer<String> print = text -> System.out.println(text);
Supplier<String> defaultName = () -> "Guest";

System.out.println(startsWithJ.test("Java")); // true
System.out.println(length.apply("Java"));     // 4
print.accept(defaultName.get());              // Guest
~~~

Đoạn trên minh họa cú pháp; khi đặt vào một chương trình, cần thêm các import tương ứng cho **Predicate**, **Function**, **Consumer** và **Supplier** từ **java.util.function**.

## 5. Lambda dùng làm callback cho API

Một lợi ích chính của lambda là truyền cách xử lý vào API. Ví dụ, **List.sort** nhận một **Comparator**, còn **forEach** nhận một **Consumer**.

Tạo tệp **LambdaSortDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class LambdaSortDemo {
    record Course(String name, int durationHours) {}

    public static void main(String[] args) {
        List<Course> courses = new ArrayList<>(List.of(
                new Course("Streams", 4),
                new Course("Lambda", 2),
                new Course("Generics", 3)
        ));

        courses.sort((left, right) ->
                Integer.compare(left.durationHours(), right.durationHours()));

        courses.forEach(course ->
                System.out.println(course.name() + ": " + course.durationHours()));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LambdaSortDemo.java
java LambdaSortDemo
~~~

Kết quả stdout:

~~~text
Lambda: 2
Generics: 3
Streams: 4
~~~

Lambda truyền vào **sort** so sánh thời lượng của hai khóa học; số âm, số 0 hoặc số dương lần lượt biểu thị thứ tự trước, bằng nhau hoặc sau theo hợp đồng của **Comparator**. Lambda truyền vào **forEach** in từng phần tử. Bản thân lambda không tự tạo luồng hay chạy bất đồng bộ; API gọi nó khi và theo cách mà API quy định.

## 6. Thân lambda dạng khối và giá trị trả về

Dùng thân dạng biểu thức khi hành vi ngắn gọn. Dùng thân dạng khối khi cần nhiều bước, biến cục bộ, điều kiện hoặc xử lý lỗi.

~~~java
import java.util.function.IntUnaryOperator;

IntUnaryOperator addTenPercent = price -> {
    int tax = Math.round(price * 0.1f);
    return price + tax;
};

System.out.println(addTenPercent.applyAsInt(100));
~~~

Kết quả là **110**. Đây là đoạn minh họa một biểu thức có thể đặt trong phương thức; nếu muốn chạy độc lập, hãy đặt phần khai báo và câu lệnh in bên trong **main** của một lớp Java.

Với functional interface có kiểu trả về khác **void**, mọi nhánh thực thi của thân khối phải trả về giá trị phù hợp. Không thể bỏ **return** chỉ vì lambda được viết nhiều dòng. Với interface trả về **void**, thân khối có thể chứa nhiều câu lệnh nhưng không được trả về một giá trị.

## 7. Biến cục bộ được lambda sử dụng

Lambda có thể đọc biến cục bộ trong phạm vi bao quanh nếu biến đó là **final** hoặc **effectively final**: biến được gán một lần và không bị gán lại sau đó.

~~~java
int offset = 10;
IntUnaryOperator addOffset = value -> value + offset;
System.out.println(addOffset.applyAsInt(5)); // 15
~~~

Ví dụ sau không hợp lệ vì **offset** bị gán lại sau khi được dùng trong lambda:

~~~java
int offset = 10;
IntUnaryOperator addOffset = value -> value + offset;
offset = 20; // Lỗi biên dịch: offset không còn effectively final
~~~

Quy tắc này áp dụng cho biến cục bộ và tham số phương thức được capture. Lambda vẫn có thể đọc hoặc thay đổi trạng thái của một đối tượng mà biến tham chiếu tới, nếu quyền truy cập cho phép; tuy nhiên, tính bất biến của tham chiếu không đồng nghĩa đối tượng được tham chiếu là bất biến. Cần cẩn thận với trạng thái dùng chung trong xử lý đa luồng.

## 8. Phạm vi của this và tên biến

Trong lambda, **this** tham chiếu tới đối tượng bao quanh nơi lambda được viết. Lambda không tạo một lớp lồng ẩn danh với một đối tượng **this** riêng. Đây là điểm khác biệt quan trọng so với lớp ẩn danh, nơi **this** bên trong phần thân thường tham chiếu tới instance của lớp ẩn danh.

Tham số lambda không được trùng tên với biến cục bộ đã khai báo trong cùng phạm vi bao quanh. Hãy chọn tên tham số rõ nghĩa và tránh che khuất tên biến khiến mã khó đọc.

## 9. Kiểm tra ngoại lệ

Kiểu phương thức SAM quyết định các ngoại lệ checked mà thân lambda được phép ném. Nếu phương thức trừu tượng không khai báo một ngoại lệ checked nào, lambda không thể ném trực tiếp ngoại lệ checked đó mà không xử lý hoặc chuyển đổi nó. Ví dụ, **Runnable.run()** không khai báo ngoại lệ checked, trong khi **Callable.call()** có thể ném **Exception**.

Khi tích hợp API có thể phát sinh ngoại lệ, hãy xử lý ở nơi phù hợp hoặc định nghĩa một functional interface có chữ ký ngoại lệ phù hợp. Không nên bọc mọi ngoại lệ thành ngoại lệ runtime một cách máy móc, vì việc đó làm mất thông tin về cách gọi cần xử lý lỗi.

## 10. Lambda và method reference

Khi lambda chỉ gọi một phương thức với các tham số tương ứng, **method reference** có thể biểu đạt cùng ý định gọn hơn:

~~~java
Function<String, String> trimWithLambda = text -> text.trim();
Function<String, String> trimWithReference = String::trim;
~~~

Một số dạng method reference là **TênLớp::phươngThứcTĩnh**, **đốiTượng::phươngThứcInstance** và **TênLớp::phươngThứcInstance**. Method reference cũng cần một kiểu functional interface làm kiểu đích; đây là cú pháp liên quan và có thể thay thế lambda khi chữ ký tương thích.

## 11. Lambda so với lớp ẩn danh

Lambda phù hợp khi cần cung cấp một hành vi duy nhất cho functional interface. Nó ngắn gọn, giúp thể hiện trực tiếp hành vi được truyền vào API và sử dụng ngữ cảnh **this** của lớp bao quanh.

Lớp ẩn danh vẫn hữu ích khi cần khai báo nhiều phương thức, ghi đè thêm hành vi, hoặc cần một lớp con của lớp trừu tượng thay vì triển khai functional interface. Không thể thay thế mọi lớp ẩn danh bằng lambda vì lambda chỉ nhắm tới functional interface.

Không nên dựa vào việc hai lambda có cùng phần thân thì chắc chắn bằng nhau hoặc là cùng một instance; định danh của đối tượng lambda không được đảm bảo theo cách đó. Nếu cần so sánh kết quả, hãy so sánh đầu ra hoặc dữ liệu miền nghiệp vụ.

## 12. Lỗi thường gặp và cách khắc phục

- **Interface có nhiều phương thức trừu tượng:** chỉ dùng lambda khi kiểu đích là functional interface; rà soát thiết kế và dùng **@FunctionalInterface** để trình biên dịch kiểm tra.
- **Không có kiểu đích:** khai báo biến bằng kiểu functional interface, truyền lambda vào tham số có kiểu rõ ràng hoặc ép kiểu khi thực sự cần.
- **Sai kiểu tham số hoặc kiểu trả về:** so khớp chữ ký lambda với phương thức SAM, kể cả kiểu **void** và kiểu trả về.
- **Thiếu return trong thân khối:** thêm **return** cho mọi nhánh khi phương thức SAM yêu cầu kết quả.
- **Gán lại biến cục bộ đã capture:** giữ biến final/effectively final hoặc thiết kế lại cách truyền dữ liệu.
- **Mơ hồ khi gọi phương thức overload:** nếu nhiều overload nhận các functional interface có hình dạng tương thích, trình biên dịch có thể không chọn được; cung cấp kiểu tham số rõ ràng hoặc ép kiểu về interface đích thích hợp.
- **Giả định lambda tự chạy song song:** lambda chỉ mô tả hành vi; luồng thực thi do đoạn mã gọi lambda quyết định.
- **Đưa quá nhiều xử lý vào một lambda:** tách logic phức tạp thành phương thức có tên để dễ kiểm thử và bảo trì.

## 13. Quy trình chọn và viết lambda

1. Xác định API cần một hành vi nào và tìm kiểu tham số functional interface của API.
2. Xem chữ ký SAM: số lượng tham số, kiểu tham số, kiểu trả về và ngoại lệ được khai báo.
3. Viết lambda khớp chữ ký đó; để trình biên dịch suy luận kiểu khi ngữ cảnh đã rõ.
4. Chọn thân biểu thức cho xử lý đơn giản hoặc thân khối cho nhiều bước.
5. Kiểm tra quy tắc capture, phạm vi tên, ngoại lệ và các overload có thể gây mơ hồ.
6. Chạy ví dụ với đầu vào đại diện, kiểm tra stdout hoặc kết quả thực tế và giữ phần xử lý đủ rõ để bảo trì.

Lambda giúp Java truyền hành vi như một giá trị thông qua functional interface. Khi hiểu kiểu đích và chữ ký SAM, có thể dùng lambda để triển khai callback, lọc, ánh xạ, sắp xếp và nhiều thao tác API khác mà không cần tạo một lớp riêng cho mỗi hành vi nhỏ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-007">Method reference và kiểu đích của biểu thức</a>

<details>
<summary>Xem chi tiết</summary>

# Method reference và kiểu đích của biểu thức

Method reference là cách viết gọn để chuyển một phương thức hoặc constructor thành hành động có thể truyền như một giá trị, bằng toán tử `::`. Ví dụ, `Integer::parseInt` có thể thay cho lambda `text -> Integer.parseInt(text)`. Tuy nhiên, bản thân method reference **không tự mang một kiểu hàm độc lập**: trình biên dịch cần biết kiểu đích để xác định tham số, kiểu trả về và phương thức cụ thể nào phù hợp.

## 1. Kiểu đích cung cấp điều gì?

Kiểu đích (target type) thường là một functional interface: interface có đúng một phương thức trừu tượng cần triển khai (SAM, *single abstract method*). Những phương thức `default` và `static` không làm tăng số SAM.

Ví dụ:

~~~java
Function<String, Integer> parser = Integer::parseInt;
~~~

Ở đây, `Function<String, Integer>` là kiểu đích. Phương thức trừu tượng `apply(String)` nhận một `String` và trả về một `Integer`. Vì vậy trình biên dịch tìm một cách gọi tương thích với chữ ký đó; `Integer.parseInt(String)` nhận chuỗi và trả về `int`, có thể được boxing thành `Integer`.

Kiểu đích thường đến từ một trong các ngữ cảnh sau:

- **Khai báo hoặc phép gán:** `Function<String, Integer> parser = Integer::parseInt;`
- **Tham số phương thức:** nếu `map` nhận `Function<String, Integer>`, đối số `Integer::parseInt` được kiểm tra theo kiểu đó.
- **Giá trị trả về:** phương thức trả về `Function<String, Integer>` có thể dùng `return Integer::parseInt;`.
- **Ép kiểu tường minh:** `(Function<String, Integer>) Integer::parseInt` cung cấp kiểu đích khi cần chọn một cách diễn giải cụ thể.

Không có kiểu đích, biểu thức như `var parser = Integer::parseInt;` không hợp lệ: `var` cần suy ra kiểu từ giá trị khởi tạo, nhưng method reference không có kiểu độc lập để suy ra theo cách đó. Hãy khai báo kiểu functional interface hoặc cung cấp ngữ cảnh kiểu rõ ràng.

## 2. Các dạng method reference và cách đọc

| Dạng | Ví dụ | Kiểu đích có thể dùng | Ý nghĩa |
|---|---|---|---|
| Phương thức static | `Integer::parseInt` | `Function<String, Integer>` | Tham số hàm được truyền làm đối số cho phương thức static. |
| Phương thức instance trên một đối tượng đã có (bound) | `System.out::println` | `Consumer<String>` | Đối tượng nhận lời gọi đã được xác định là `System.out`. |
| Phương thức instance trên một đối tượng được lấy từ tham số đầu (unbound) | `String::trim` | `Function<String, String>` | Tham số đầu tiên trở thành đối tượng nhận lời gọi. |
| Constructor | `StringBuilder::new` | `Function<String, StringBuilder>` | Gọi constructor nhận một chuỗi. |
| Constructor mảng | `String[]::new` | `IntFunction<String[]>` | Nhận độ dài và tạo mảng chuỗi. |
| Phương thức hiện tại hoặc lớp cha | `this::validate`, `super::toString` | Functional interface tương thích | Gọi phương thức trên đối tượng hiện tại hoặc phần triển khai của lớp cha. |

Dạng `String::trim` cần đặc biệt chú ý: dù cú pháp có tên lớp, đây là tham chiếu đến **phương thức instance**. Với kiểu đích `Function<String, String>`, tham số `String` của `apply` trở thành receiver để gọi `trim()`. Tương tự, `String::concat` có thể phù hợp với `BiFunction<String, String, String>`: tham số đầu là receiver, tham số thứ hai là đối số truyền cho `concat`.

Kiểu đích cũng quyết định số tham số mà hàm nhận. Constructor không tham số thường dùng với `Supplier<T>`; constructor một tham số có thể dùng với `Function<T, R>`; constructor mảng thường dùng với `IntFunction<T[]>`. Nếu không có functional interface chuẩn phù hợp, có thể định nghĩa một functional interface riêng.

## 3. Ví dụ Java 17+ chạy được

Lưu mã sau thành `TargetTypeMethodReference.java`. Ví dụ minh họa nhiều dạng method reference và cho thấy kiểu đích ảnh hưởng đến việc chọn overload.

~~~java
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

public class TargetTypeMethodReference {
    static String surround(String value) {
        return "[" + value + "]";
    }

    static String render(Object value) {
        return "Object";
    }

    static String render(String value) {
        return "String";
    }

    public static void main(String[] args) {
        Function<String, Integer> parse = Integer::parseInt;
        Function<String, String> trim = String::trim;
        Function<String, String> surrounder =
                TargetTypeMethodReference::surround;
        BiFunction<String, String, String> concat = String::concat;
        Function<String, StringBuilder> builder = StringBuilder::new;
        IntFunction<String[]> arrayFactory = String[]::new;

        Function<String, String> renderString =
                TargetTypeMethodReference::render;
        Function<Object, String> renderObject =
                TargetTypeMethodReference::render;

        System.out.println("parse=" + parse.apply("42"));
        System.out.println("trim='" + trim.apply("  Java  ") + "'");
        System.out.println("surround=" + surrounder.apply("Java"));
        System.out.println("concat=" + concat.apply("Java", " 17"));
        System.out.println("builder=" + builder.apply("code"));
        System.out.println("arrayLength=" + arrayFactory.apply(3).length);
        System.out.println("renderString=" + renderString.apply("x"));
        System.out.println("renderObject=" + renderObject.apply("x"));
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~text
javac TargetTypeMethodReference.java
java TargetTypeMethodReference
~~~

Kết quả chuẩn:

~~~text
parse=42
trim='Java'
surround=[Java]
concat=Java 17
builder=code
arrayLength=3
renderString=String
renderObject=Object
~~~

Giải thích các kiểu đích chính:

- `parse` có kiểu `Function<String, Integer>`, nên `Integer::parseInt` được dùng như hàm nhận chuỗi và trả về số nguyên.
- `trim` có kiểu `Function<String, String>`. Với `String::trim`, tham số chuỗi trở thành receiver.
- `concat` có kiểu `BiFunction<String, String, String>`, nên cần hai tham số: chuỗi nhận lời gọi và chuỗi ghép thêm.
- `builder` yêu cầu hàm nhận `String` và trả về `StringBuilder`, phù hợp với constructor `StringBuilder(String)`.
- `arrayFactory` có kiểu `IntFunction<String[]>`, vì constructor mảng nhận độ dài kiểu `int`.
- `renderString` và `renderObject` cùng trỏ đến tên phương thức overloaded `render`, nhưng kiểu tham số đầu vào của functional interface khác nhau. Với `Function<String, String>`, overload `render(String)` phù hợp hơn; với `Function<Object, String>`, chữ ký cần nhận `Object` nên overload `render(Object)` được chọn. Việc chọn overload diễn ra lúc biên dịch, dựa trên kiểu tĩnh trong ngữ cảnh, không dựa trên kiểu thực tế của đối tượng khi chạy.

## 4. Kiểu đích và overload resolution

Khi tên phương thức có nhiều overload, trình biên dịch sử dụng chữ ký hàm của kiểu đích để tìm overload phù hợp. Các yếu tố quan trọng gồm:

1. **Số lượng tham số:** kiểu đích có một tham số không thể dùng một overload cần hai tham số, trừ khi có dạng tham chiếu khác phù hợp.
2. **Kiểu tham số:** các kiểu tham số của functional interface phải tương thích với tham số/receiver của phương thức được tham chiếu.
3. **Kiểu trả về:** kết quả của phương thức phải tương thích với kiểu trả về của SAM, kể cả các chuyển đổi hợp lệ như boxing, unboxing và widening primitive.
4. **Tính mơ hồ:** nếu nhiều overload vẫn tương thích mà không có lựa chọn cụ thể nhất, biểu thức không biên dịch; cần làm rõ kiểu đích.

Ví dụ về tình huống mơ hồ:

~~~java
import java.util.function.Function;
import java.util.function.ToIntFunction;

static void accept(Function<String, Integer> parser) {}
static void accept(ToIntFunction<String> parser) {}

// Có thể mơ hồ: Integer::parseInt tương thích với cả hai kiểu đích.
accept(Integer::parseInt);

// Làm rõ kiểu đích bằng ép kiểu:
accept((Function<String, Integer>) Integer::parseInt);
accept((ToIntFunction<String>) Integer::parseInt);
~~~

`Function<String, Integer>` trả về kiểu tham chiếu `Integer`; `ToIntFunction<String>` trả về kiểu nguyên thủy `int`. Cả hai đều có thể sử dụng `Integer.parseInt(String)`, vì vậy chỉ nhìn vào tên phương thức là chưa đủ để chọn overload của `accept`. Hai lời gọi có ép kiểu làm rõ functional interface mong muốn. Đoạn này là ví dụ riêng về overload resolution, không phải phần tiếp nối bên trong lớp chạy ở mục trước.

## 5. Kiểu đích trong generic và lời gọi phương thức

Kiểu đích có thể tham gia vào quá trình suy luận kiểu generic. Ví dụ:

~~~java
import java.util.function.Function;

static <T, R> R map(T value, Function<? super T, ? extends R> mapper) {
    return mapper.apply(value);
}

// T được suy ra là String; R được suy ra là Integer.
Integer result = map("123", Integer::parseInt);
~~~

Đối số `Integer::parseInt` được kiểm tra theo tham số `mapper`, còn kiểu nhận của `result` cung cấp thêm ngữ cảnh cho kiểu trả về `R`. Trình biên dịch suy luận các kiểu sao cho toàn bộ lời gọi tương thích. Trong các pipeline Stream, cơ chế tương tự xuất hiện ở những lời gọi như `names.stream().map(String::trim)`: phương thức `map` nhận một hàm, nên kiểu phần tử của stream giúp xác định kiểu đích.

Nếu có thể diễn đạt cùng một ý bằng lambda, hãy so sánh chữ ký để hiểu method reference:

~~~java
Function<String, Integer> a = Integer::parseInt;
Function<String, Integer> b = text -> Integer.parseInt(text);
~~~

Hai biểu thức trên tương đương về mục đích trong ví dụ này. Lambda thể hiện rõ tham số và phần thân; method reference ngắn gọn hơn khi phần thân chỉ gọi một phương thức với các tham số tương ứng.

## 6. Lỗi thường gặp và cách xử lý

- **Thiếu kiểu đích:** `var f = String::trim;` không biên dịch. Khai báo `Function<String, String> f = String::trim;`.
- **Nhầm static và instance:** `Integer::parseInt` gọi phương thức static; `String::trim` là dạng unbound của phương thức instance và cần một đối số chuỗi làm receiver.
- **Sai số tham số:** `String::concat` cần receiver và đối số nối thêm, vì vậy phù hợp với kiểu hàm hai tham số như `BiFunction<String, String, String>`.
- **Overload mơ hồ:** nhiều phương thức cùng tên có thể cùng phù hợp với kiểu đích. Cung cấp kiểu functional interface cụ thể hoặc ép kiểu method reference khi cần.
- **Kiểu trả về không tương thích:** kiểm tra kiểu trả về của phương thức so với kiểu trả về của SAM; chỉ các chuyển đổi được Java cho phép mới được áp dụng.
- **Receiver có thể null:** với dạng bound như `object::method`, biểu thức receiver được đánh giá khi tạo method reference. Nếu receiver là `null`, việc tạo tham chiếu gây `NullPointerException`; hãy kiểm tra đối tượng trước khi tạo.
- **Checked exception:** phương thức được tham chiếu không thể làm cho checked exception biến mất. Kiểu SAM phải cho phép exception đó, hoặc mã gọi phải xử lý theo quy tắc Java.

## 7. Cách tự kiểm tra khi đọc một method reference

Khi gặp biểu thức `TypeOrObject::method`, hãy xác định lần lượt:

1. Functional interface nào cung cấp kiểu đích?
2. SAM nhận bao nhiêu tham số, mỗi tham số có kiểu gì và trả về kiểu gì?
3. Đây là phương thức static, phương thức instance có receiver cố định, phương thức instance dùng tham số đầu làm receiver, hay constructor?
4. Có overload nào tương thích với chữ ký SAM? Nếu có nhiều overload, quy tắc chọn overload có xác định được duy nhất một phương thức không?
5. Kiểu trả về, boxing/unboxing và exception có tương thích không?

Nếu trả lời được những câu hỏi này, có thể dự đoán cách trình biên dịch diễn giải method reference trước khi chạy chương trình.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-008">Kiểu tham số, kiểu trả về và checked exception của hợp đồng hàm</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu tham số, kiểu trả về và checked exception trong hợp đồng hàm

Một **functional interface** (interface hàm) định nghĩa một hợp đồng có thể được hiện thực bằng lambda hoặc method reference. Hợp đồng này cho biết hàm nhận những đối số nào, phải trả về giá trị gì và được phép để những checked exception nào truyền ra ngoài. Khi gán lambda cho một kiểu functional interface, Java dùng kiểu đích để kiểm tra ba phần đó ngay lúc biên dịch.

## 1. Phân biệt chữ ký phương thức và hợp đồng hàm

Một functional interface có đúng một phương thức trừu tượng (SAM — *single abstract method*). Có thể dùng **@FunctionalInterface** để nhờ trình biên dịch kiểm tra điều kiện này. Phương thức SAM là kiểu mô tả (function descriptor) mà lambda hoặc method reference phải đáp ứng.

Trong thuật ngữ của Java Language Specification, chữ ký phương thức chủ yếu dựa trên tên, tham số kiểu và kiểu của các tham số hình thức. **Kiểu trả về và mệnh đề throws không tạo ra một chữ ký khác để overload**, nhưng vẫn là phần quan trọng của hợp đồng và được trình biên dịch kiểm tra khi override hoặc gán lambda.

Ví dụ, nếu interface khai báo phương thức **String convert(String input)**, không thể khai báo thêm một overload chỉ bằng cách đổi kiểu trả về thành **Object**, hoặc chỉ thêm **throws IOException**. Muốn overload, cần khác danh sách tham số theo quy tắc của Java.

## 2. Kiểu tham số

- **Số lượng và thứ tự:** lambda phải nhận đúng số tham số của phương thức SAM và dùng chúng theo đúng thứ tự. **BiFunction&lt;String, Integer, String&gt;** có hai tham số: **String** rồi **Integer**.
- **Kiểu tham số được suy luận:** với biểu thức **s -> s.trim()**, kiểu của **s** được suy luận từ kiểu đích. Có thể ghi tường minh, chẳng hạn **(String s) -> s.trim()**, khi muốn làm rõ kiểu hoặc khi cú pháp yêu cầu.
- **Phương thức triển khai phải khớp:** khi một lớp implement interface hoặc override phương thức, không được thay tham số bằng kiểu rộng hơn hay hẹp hơn. Nếu hợp đồng yêu cầu **load(String id)**, phương thức **load(Object id)** không triển khai hợp đồng đó; nó là một overload khác.
- **Generics:** kiểu tham số còn phụ thuộc vào cách tham số hóa interface. Chẳng hạn **Consumer&lt;String&gt;** nhận **String**, còn **Consumer&lt;Integer&gt;** nhận **Integer**. Trình biên dịch kiểm tra kiểu sau khi áp dụng tham số kiểu phù hợp.

Không nên nhầm việc một kiểu có thể nhận được giá trị của kiểu khác với việc nó có thể thay thế kiểu tham số trong một phương thức override. Quy tắc override yêu cầu tham số tương ứng khớp với chữ ký được override.

## 3. Kiểu trả về

Nếu phương thức SAM trả về giá trị, thân lambda phải tạo ra một giá trị tương thích với kiểu trả về đó. Với thân dạng khối, dùng **return** ở các nhánh có thể kết thúc bình thường. Nếu phương thức SAM trả về **void**, lambda có thể thực hiện một câu lệnh phù hợp mà không trả về giá trị.

Khi override một phương thức:

- Với **kiểu tham chiếu**, kiểu trả về của phương thức triển khai có thể là kiểu con tương thích (*covariant return type*). Ví dụ, phương thức interface trả về **CharSequence** có thể được triển khai bằng phương thức trả về **String**, vì **String** là một **CharSequence**.
- Với **kiểu nguyên thủy**, kiểu trả về phải khớp; không thể đổi **int** thành **long** trong phương thức override.
- Với **void**, phương thức override cũng phải trả về **void**.
- Không thể overload chỉ bằng cách đổi kiểu trả về. Java chọn overload dựa trên danh sách tham số, không dựa trên kiểu trả về.

Với lambda, biểu thức trả về phải tương thích theo quy tắc chuyển đổi kiểu của Java. Ví dụ, lambda có thể trả về một **String** khi kiểu đích yêu cầu **CharSequence**.

## 4. Checked exception trong hợp đồng

Checked exception là ngoại lệ mà trình biên dịch yêu cầu mã xử lý bằng **try/catch** hoặc khai báo tiếp bằng **throws**. Mệnh đề **throws** không xử lý ngoại lệ; nó công bố ngoại lệ có thể truyền đến bên gọi.

### Quy tắc khi triển khai hoặc override

Giả sử phương thức interface khai báo **throws IOException**:

- Phương thức triển khai có thể khai báo **throws IOException**.
- Có thể khai báo một checked exception hẹp hơn, chẳng hạn **FileNotFoundException**, vì nó là lớp con của **IOException**.
- Có thể bỏ mệnh đề **throws** nếu phương thức triển khai không cần để checked exception nào truyền ra.
- Không được khai báo một checked exception rộng hơn như **Exception**, hoặc một checked exception không tương thích như **SQLException**.
- Có thể phát sinh **RuntimeException** hoặc lớp con của nó mà không cần khai báo trong **throws**, vì đây là unchecked exception.

Nếu phương thức interface không khai báo checked exception, phương thức override không thể tự thêm checked exception mới vào hợp đồng. Quy tắc này giúp bên gọi tiếp tục sử dụng interface mà không phải xử lý những ngoại lệ mới mà hợp đồng ban đầu không hứa hẹn.

### Quy tắc với lambda và method reference

Lambda không có cú pháp **throws** riêng. Các checked exception có thể thoát ra từ thân lambda phải tương thích với mệnh đề **throws** của phương thức SAM, hoặc phải được bắt ngay trong thân lambda. Method reference cũng tuân theo quy tắc này.

Ví dụ, với một functional interface có phương thức **load(String id) throws IOException**, lambda gọi **Files.readString(Path.of(id))** có thể truyền **IOException** ra ngoài. Nhưng cùng lời gọi đó không thể được gán trực tiếp cho **Runnable**, vì **Runnable.run()** không khai báo checked exception. Khi cần dùng API không khai báo checked exception, phải xử lý ngoại lệ trong lambda hoặc chủ động chuyển đổi sang một unchecked exception phù hợp; không nên nuốt ngoại lệ một cách im lặng.

### Bên gọi phải tuân theo kiểu đích

Trình biên dịch kiểm tra lời gọi dựa trên kiểu được khai báo của biến, không dựa trên việc đối tượng thực tế có thể ném ít ngoại lệ hơn. Nếu biến có kiểu **TextLoader** và phương thức SAM khai báo **throws IOException**, bên gọi vẫn phải bắt hoặc khai báo **IOException**, ngay cả khi đối tượng hiện tại là một lớp triển khai không bao giờ ném ngoại lệ đó.

## 5. Ví dụ chạy được trên Java 17+

Lưu mã sau trong tệp **ContractDemo.java**. Ví dụ cho thấy lambda, lớp triển khai có kiểu trả về hiệp biến (**String** thay cho **CharSequence**) và checked exception được khai báo trong hợp đồng.

    import java.io.IOException;
    
    @FunctionalInterface
    interface TextLoader {
        CharSequence load(String id) throws IOException;
    }
    
    final class CachedLoader implements TextLoader {
        @Override
        public String load(String id) {
            return "cached:" + id;
        }
    }
    
    public class ContractDemo {
        public static void main(String[] args) throws IOException {
            TextLoader lambda = id -> "lambda:" + id;
            TextLoader cached = new CachedLoader();
            TextLoader checked = id -> {
                if (id.isBlank()) {
                    throw new IOException("empty id");
                }
                return "loaded:" + id;
            };
    
            System.out.println(lambda.load("A-1"));
            System.out.println(cached.load("B-2"));
            try {
                checked.load("");
            } catch (IOException e) {
                System.out.println("checked: " + e.getMessage());
            }
        }
    }

Biên dịch và chạy:

    javac ContractDemo.java
    java ContractDemo

Kết quả chuẩn (stdout):

    lambda:A-1
    cached:B-2
    checked: empty id

Lời gọi **lambda.load(...)** và **cached.load(...)** vẫn được viết trong **main** có **throws IOException**, vì kiểu khai báo của cả hai biến là **TextLoader**. Lớp **CachedLoader** được phép bỏ **throws**, nhưng điều đó không thay đổi hợp đồng nhìn thấy qua kiểu interface. Lambda **checked** có thể ném **IOException** vì SAM đã khai báo ngoại lệ này; bên gọi bắt ngoại lệ và in thông báo.

## 6. Những cách triển khai không hợp lệ

Với **TextLoader** ở ví dụ trên, các khai báo sau không hợp lệ:

    // Sai: Object rộng hơn CharSequence.
    // @Override public Object load(String id) { return id; }

    // Sai: tham số Object không override load(String).
    // @Override public CharSequence load(Object id) { return id.toString(); }

    // Sai: Exception rộng hơn IOException.
    // @Override public CharSequence load(String id) throws Exception { return id; }

Ngược lại, những cách sau phù hợp với hợp đồng:

    // Kiểu trả về String là kiểu con của CharSequence.
    @Override
    public String load(String id) {
        return "value:" + id;
    }

    // Không khai báo throws vẫn hợp lệ.
    @Override
    public CharSequence load(String id) {
        return id;
    }

Các đoạn trên minh họa quy tắc; không đặt tất cả chúng vào cùng một lớp vì sẽ tạo ra các khai báo trùng hoặc xung đột.

## 7. Generics, interface kế thừa và các kiểu hàm có sẵn

- **SAM có tham số kiểu:** một interface như **Decoder&lt;T, E extends Exception&gt;** có thể trả về **T** và khai báo **throws E**. Khi dùng **Decoder&lt;String, IOException&gt;**, kiểu trả về và checked exception được xác định theo các tham số kiểu đã chọn.
- **Nhiều interface cha:** nếu các interface cha khai báo những phương thức trừu tượng có chữ ký tương thích, kiểu trả về phải tương thích với tất cả khai báo liên quan. Phương thức triển khai cũng phải tuân thủ giới hạn checked exception của từng hợp đồng; không thể dùng một **throws** rộng hơn để phá vỡ hợp đồng của một interface cha.
- **Phương thức default:** có phần thân mặc định nhưng vẫn là hành vi công khai của interface; nếu override, lớp triển khai phải tuân theo quy tắc tham số, kiểu trả về và checked exception.
- **Phương thức static và private trong interface:** chúng không phải phương thức trừu tượng SAM mà lớp triển khai phải cài đặt.
- **java.util.function.Function&lt;T, R&gt;:** phương thức **apply** không khai báo checked exception. Vì vậy, không thể truyền thẳng một lambda để ngoại lệ **IOException** thoát ra từ **apply**. Nếu checked exception là một phần cần thiết của API, cân nhắc định nghĩa functional interface riêng có **throws IOException**, hoặc dùng một kiểu hàm khác có hợp đồng ngoại lệ phù hợp, chẳng hạn **Callable&lt;V&gt;** (khai báo **throws Exception**).

## 8. Lỗi thường gặp và cách tự kiểm tra

1. **Đổi kiểu tham số khi override:** hãy kiểm tra chữ ký SAM và bảo đảm kiểu tham số khớp; thay bằng **Object** hoặc một kiểu con không làm phương thức trở thành override hợp lệ.
2. **Cố overload bằng kiểu trả về hoặc throws:** cần một danh sách tham số khác hợp lệ; return type và **throws** không tạo ra overload riêng.
3. **Trả về kiểu quá rộng:** khi override, chọn kiểu trả về tương thích và chỉ dùng kiểu con cho kiểu tham chiếu.
4. **Khai báo checked exception quá rộng:** đối chiếu từng ngoại lệ với mệnh đề **throws** của interface; có thể bỏ hoặc thu hẹp ngoại lệ, nhưng không thể mở rộng tùy ý.
5. **Quên xử lý ngoại lệ ở nơi gọi:** đọc hợp đồng qua kiểu biến và bắt hoặc khai báo các checked exception mà kiểu đó cho phép.
6. **Dùng Function cho API ném checked exception:** xử lý ngoại lệ trong lambda hoặc thiết kế functional interface có mệnh đề **throws** rõ ràng.
7. **Cho rằng throws làm ngoại lệ biến mất:** **throws** chỉ chuyển trách nhiệm xử lý cho bên gọi; chỉ **catch** mới bắt ngoại lệ tại chỗ.

Khi thiết kế hợp đồng hàm, hãy chọn kiểu tham số đủ cụ thể để xác định đầu vào, kiểu trả về thể hiện kết quả hữu ích cho bên gọi và mệnh đề **throws** chỉ công bố những checked exception mà người dùng API thực sự cần biết. Điều này giúp lambda, method reference, lớp triển khai và bên gọi cùng tuân theo một hợp đồng rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-009">Giới hạn với sealed interface và phương thức hàm có kiểu tổng quát</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn với sealed interface và phương thức hàm có kiểu tổng quát

Bài này tập trung vào hai cơ chế thường bị nhầm khi thiết kế API Java: `sealed interface` để giới hạn hệ phân cấp kiểu, và phương thức trừu tượng có tham số kiểu tổng quát trong một interface dạng hàm. Ví dụ dùng Java 17 trở lên; phần pattern matching cho `switch` cần Java 21 trở lên.

## 1. Phân biệt hai mục tiêu

- **Sealed interface** xác định những kiểu nào được phép triển khai trực tiếp interface. Nó giúp mô hình hóa một tập biến thể có giới hạn, chẳng hạn kết quả thành công hoặc thất bại.
- **Functional interface** cung cấp một hợp đồng hàm để có thể truyền lambda hoặc method reference. Hợp đồng này cần có một phương thức trừu tượng tương thích với cách biểu diễn hàm của Java.
- **Generic interface** có thể nhận tham số kiểu ở cấp interface, ví dụ `Converter<S, T>`. Sau khi chọn một kiểu cụ thể như `Converter<String, Integer>`, phương thức hàm có kiểu đầu vào và đầu ra rõ ràng.
- **Generic method** khai báo tham số kiểu ngay trên phương thức, ví dụ `<T> T identity(T value)`. Đây là trường hợp có giới hạn khi dùng lambda, vì lambda không thể khai báo tham số kiểu của phương thức đích.

Các cơ chế này giải quyết vấn đề khác nhau. Một interface có thể là generic và dùng lambda rất tự nhiên; một interface sealed có thể generic; nhưng không nên dùng sealed interface làm kiểu đích cho lambda, và một phương thức trừu tượng tự khai báo tham số kiểu không thể được triển khai bằng lambda thông thường.

## 2. Quy tắc của sealed interface

Một sealed interface dùng từ khóa `sealed` và thường liệt kê các kiểu được phép triển khai trực tiếp bằng `permits`.

~~~java
sealed interface Outcome<T> permits Success, Failure {
}

record Success<T>(T value) implements Outcome<T> {
}

record Failure<T>(String message) implements Outcome<T> {
}
~~~

Trong ví dụ, `Outcome<T>` chỉ cho phép `Success` và `Failure` triển khai trực tiếp. Các record tự động là `final`, nên chúng đáp ứng quy tắc dành cho kiểu con trực tiếp của sealed type. Danh sách `permits` dùng tên kiểu, không viết tham số kiểu như `Success<T>`.

Các quy tắc quan trọng:

1. Mỗi kiểu con trực tiếp được liệt kê trong `permits` phải thật sự kế thừa hoặc triển khai sealed type đó.
2. Nếu sealed type nằm trong named module, các kiểu con trực tiếp phải thuộc cùng module. Nếu không nằm trong named module, chúng phải thuộc cùng package.
3. Mỗi kiểu con trực tiếp phải khai báo `final`, `sealed` hoặc `non-sealed`. Record vốn đã final nên không cần ghi thêm `final`.
4. `final` đóng nhánh kế thừa tại kiểu đó. `sealed` tiếp tục giới hạn các kiểu con ở tầng sau. `non-sealed` mở lại nhánh đó để có thể phát sinh các kiểu con khác.
5. Có thể bỏ `permits` nếu compiler suy ra được danh sách kiểu con từ các khai báo trong cùng compilation unit. Khi các kiểu nằm ở file khác, hãy khai báo `permits` tường minh.
6. Sealed type giới hạn quan hệ kế thừa trực tiếp; nó không tự kiểm tra tính hợp lệ của dữ liệu hay bảo đảm mọi nhánh đều xử lý đúng nghiệp vụ.

Một sealed interface có thể có tham số kiểu. Việc sealed hay không sealed kiểm soát hệ phân cấp lớp/kiểu; generic kiểm soát quan hệ kiểu giữa giá trị và API. Hai cơ chế này bổ sung cho nhau.

## 3. Generic functional interface có thể dùng lambda

Cách thường phù hợp nhất là đặt tham số kiểu ở cấp interface, trong khi phương thức trừu tượng không tự khai báo tham số kiểu riêng.

~~~java
@FunctionalInterface
interface Converter<S, T> {
    T convert(S source);
}

class ConverterDemo {
    public static void main(String[] args) {
        Converter<String, Integer> length = String::length;
        System.out.println(length.convert("Java"));
    }
}
~~~

Lệnh biên dịch và chạy:

~~~text
javac ConverterDemo.java
java ConverterDemo
~~~

Kết quả:

~~~text
4
~~~

Với `Converter<String, Integer>`, compiler xác định phương thức cần tương thích với đầu vào `String` và đầu ra `Integer`. `String::length` trả về `int`, được boxing thành `Integer`, nên method reference này tương thích.

Lambda cũng dùng được:

~~~java
Converter<String, Integer> length = text -> text.length();
~~~

Nếu compiler không suy ra được kiểu tham số do overload hoặc ngữ cảnh phức tạp, có thể khai báo kiểu tường minh, ví dụ `(String text) -> text.length()`.

## 4. Giới hạn của phương thức trừu tượng tự khai báo kiểu tổng quát

Xét một interface có phương thức trừu tượng khai báo `<T>` ngay trên phương thức:

~~~java
interface UniversalIdentity {
    <T> T identity(T value);
}

final class Identity implements UniversalIdentity {
    @Override
    public <T> T identity(T value) {
        return value;
    }
}
~~~

Lớp `Identity` hợp lệ vì phương thức của nó khai báo lại tham số kiểu `<T>` và hoạt động với mọi kiểu T. Tuy nhiên, không thể thay việc triển khai này bằng lambda thông thường:

~~~java
UniversalIdentity identity = value -> value; // Không biên dịch
~~~

Compiler từ chối lambda vì phương thức đích có descriptor tổng quát: bản thân phương thức khai báo tham số kiểu. Cú pháp lambda không cung cấp chỗ để khai báo tham số kiểu của phương thức đích. Một lambda không thể giả định rằng kiểu của `value` chỉ là một kiểu cố định; hợp đồng yêu cầu phương thức hoạt động với mọi T.

Đây là khác biệt quan trọng:

- `Converter<String, Integer>` đã chốt các kiểu cho một lần dùng cụ thể, nên lambda có thể khớp với `String -> Integer`.
- `<T> T identity(T value)` yêu cầu một phương thức có tham số kiểu riêng và phải giữ đúng quan hệ kiểu cho mọi T. Hãy triển khai bằng lớp cụ thể hoặc lớp ẩn danh thay vì lambda.
- Việc interface có đúng một phương thức trừu tượng không tự động có nghĩa mọi biểu thức lambda đều có thể triển khai phương thức đó; descriptor của phương thức cũng phải tương thích với lambda.

Nếu mục tiêu chỉ là tạo một hàm chuyển đổi có kiểu đầu vào và đầu ra được chọn khi khai báo biến, hãy ưu tiên tham số kiểu ở cấp interface. Chỉ dùng generic method khi mỗi lần gọi cần tự chọn một kiểu T độc lập.

## 5. Vì sao nên tách sealed model khỏi callback lambda

Sealed interface thích hợp để biểu diễn các biến thể nghiệp vụ đã biết. Lambda lại cần một kiểu đích có thể được triển khai bằng lớp do compiler/runtime tạo ra; lớp triển khai đó không thể được liệt kê trước trong danh sách `permits`. Vì vậy, không thiết kế sealed interface làm đích cho lambda hoặc method reference.

Mẫu thiết kế phù hợp là để callback là một functional interface thông thường, còn kết quả do callback trả về có thể là sealed interface:

~~~java
sealed interface Outcome<T> permits Success, Failure {
}

record Success<T>(T value) implements Outcome<T> {
}

record Failure<T>(String message) implements Outcome<T> {
}

@FunctionalInterface
interface Parser<T> {
    Outcome<T> parse(String input);
}
~~~

Ở đây `Parser<T>` có thể nhận lambda vì chính `Parser` không bị sealed. Kết quả của hàm vẫn được giới hạn trong hai dạng `Success<T>` và `Failure<T>`. Thiết kế này tách hai trách nhiệm rõ ràng: callback có thể thay thế linh hoạt, còn các biến thể kết quả được kiểm soát.

## 6. Ví dụ Java hoàn chỉnh

Lưu đoạn sau thành `OutcomeDemo.java`. Tất cả các kiểu đều nằm trong cùng compilation unit để danh sách `permits` rõ ràng.

~~~java
sealed interface Outcome<T> permits Success, Failure {
}

record Success<T>(T value) implements Outcome<T> {
}

record Failure<T>(String message) implements Outcome<T> {
}

@FunctionalInterface
interface Parser<T> {
    Outcome<T> parse(String input);
}

interface UniversalIdentity {
    <T> T identity(T value);
}

final class Identity implements UniversalIdentity {
    @Override
    public <T> T identity(T value) {
        return value;
    }
}

public class OutcomeDemo {
    public static void main(String[] args) {
        Parser<Integer> parser = text -> {
            try {
                return new Success<>(Integer.parseInt(text));
            } catch (NumberFormatException ex) {
                return new Failure<>("Not an integer: " + text);
            }
        };

        System.out.println(parser.parse("42"));
        System.out.println(parser.parse("x"));

        UniversalIdentity identity = new Identity();
        String word = identity.identity("Java");
        Integer number = identity.identity(17);
        System.out.println(word + " " + number);
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac OutcomeDemo.java
java OutcomeDemo
~~~

Kết quả stdout chính xác:

~~~text
Success[value=42]
Failure[message=Not an integer: x]
Java 17
~~~

Ví dụ minh họa đồng thời ba điều: `Outcome<T>` giới hạn các dạng kết quả; `Parser<Integer>` là generic functional interface mà lambda có thể triển khai; `UniversalIdentity` giữ generic method và cần một lớp triển khai có khai báo `<T>` tường minh.

## 7. Generic, type erasure và pattern matching

Tham số kiểu generic chủ yếu được kiểm tra ở thời điểm biên dịch. Sau type erasure, không thể dựa vào runtime để phân biệt mọi kiểu tham số generic. Ví dụ, kiểm tra `instanceof Success<String>` thường không hợp lệ vì `Success<String>` không phải kiểu có thể kiểm tra đầy đủ tại runtime. Có thể kiểm tra dạng tổng quát bằng `instanceof Success<?> success`, rồi xử lý giá trị theo kiểu mà API cho phép.

Tính invariant của generic cũng cần được nhớ: `Outcome<Integer>` không phải là `Outcome<Number>`, dù `Integer` là subtype của `Number`. Khi API cần nhận nhiều kiểu con, hãy cân nhắc wildcard như `Outcome<? extends Number>` theo đúng hướng dữ liệu được đọc hoặc ghi.

Từ Java 21, pattern matching cho `switch` có thể tận dụng sealed hierarchy để compiler kiểm tra tính bao phủ của các nhánh. Khả năng kiểm tra exhaustiveness phụ thuộc vào kiểu selector, các kiểu con được phép và các pattern; khi hierarchy có generic hoặc nhánh `non-sealed`, cần kiểm tra kỹ kiểu pattern thay vì giả định compiler luôn suy ra được mọi trường hợp. Sealed hierarchy cũng không thay thế xử lý lỗi runtime hoặc xác thực dữ liệu đầu vào.

## 8. Lỗi thường gặp và cách chọn thiết kế

| Tình huống | Nguyên nhân | Hướng xử lý |
|---|---|---|
| Thêm một kiểu triển khai mới nhưng quên cập nhật `permits` | Kiểu mới chưa thuộc tập subtype được cho phép | Cập nhật danh sách và kiểm tra modifier của subtype |
| Subtype trực tiếp không có `final`, `sealed` hoặc `non-sealed` | Vi phạm quy tắc sealed hierarchy | Chọn rõ nhánh đóng hay nhánh được mở rộng |
| Gán lambda cho sealed interface | Sealed type không phù hợp làm target type của lambda | Tạo functional interface không sealed cho callback |
| Viết lambda cho phương thức trừu tượng `<T> T apply(T value)` | Phương thức đích tự khai báo type parameter | Dùng lớp triển khai hoặc thiết kế lại tham số kiểu ở cấp interface |
| Dùng `instanceof` với `Success<String>` | Kiểu tham số generic không reifiable ở runtime | Kiểm tra `Success<?>` và xử lý theo hợp đồng kiểu an toàn |
| Coi `Outcome<Integer>` là subtype của `Outcome<Number>` | Generic Java invariant | Dùng wildcard phù hợp khi API cần chấp nhận họ kiểu |
| Dùng `non-sealed` nhưng vẫn giả định toàn bộ cây con đóng | `non-sealed` cho phép mở rộng nhánh | Xem nhánh đó như một điểm mở rộng và thiết kế xử lý tương ứng |

Quy tắc chọn nhanh:

- Chọn **sealed interface** khi miền dữ liệu có tập biến thể trực tiếp được kiểm soát.
- Chọn **generic functional interface** khi muốn truyền lambda và kiểu đầu vào/đầu ra được xác định bởi tham số kiểu của interface.
- Chọn **generic method** khi mỗi lần gọi phải làm việc với kiểu T do chính lời gọi suy ra; nếu phương thức đó là phương thức trừu tượng, đừng mặc định rằng có thể triển khai nó bằng lambda.
- Kết hợp hai cơ chế bằng cách để callback không sealed trả về một sealed result khi cần vừa linh hoạt trong cách thực thi, vừa giới hạn các dạng kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="functional-interfaces-010">Chọn interface riêng hay dùng Runnable, Callable và java.util.function</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn interface riêng hay dùng Runnable, Callable và java.util.function

Khi cần truyền một hành vi vào method khác, Java thường cho phép dùng lambda hoặc method reference. Câu hỏi quan trọng là kiểu interface nào mô tả đúng hợp đồng của hành vi đó. Hãy ưu tiên kiểu mà API đang yêu cầu; nếu tự thiết kế API, chọn kiểu chuẩn khi ý nghĩa đã khớp và tạo interface riêng khi hợp đồng nghiệp vụ cần tên gọi hoặc quy tắc riêng.

## 1. Phân biệt interface thông thường và functional interface

**Functional interface** là interface có đúng một phương thức trừu tượng cần triển khai. Có thể đánh dấu bằng @FunctionalInterface để trình biên dịch kiểm tra điều kiện này. Interface vẫn có thể có các phương thức default và static.

~~~java
@FunctionalInterface
interface DiscountPolicy {
    long finalPrice(long originalPrice);
}
~~~

Vì chỉ có một thao tác trừu tượng, ta có thể truyền lambda như `price -> price * 90 / 100`. Tên interface như DiscountPolicy cho người đọc biết lambda đang biểu diễn chính sách giảm giá nào.

Interface thông thường có thể có nhiều phương thức trừu tượng và biểu diễn một hợp đồng gồm nhiều thao tác. Lambda không thể tự triển khai một interface có nhiều phương thức trừu tượng; trường hợp đó thường cần một lớp triển khai hoặc anonymous class.

## 2. Khi nào dùng các kiểu chuẩn?

| Nhu cầu | Kiểu phù hợp | Hợp đồng |
| --- | --- | --- |
| Một tác vụ không nhận đầu vào, không trả kết quả | Runnable | `void run()` |
| Một tác vụ có kết quả và có thể ném checked exception | Callable&lt;T&gt; | `T call() throws Exception` |
| Biến đổi một giá trị T thành R | Function&lt;T, R&gt; | `R apply(T value)` |
| Kiểm tra một giá trị và trả về đúng/sai | Predicate&lt;T&gt; | `boolean test(T value)` |
| Nhận một giá trị và thực hiện hành động | Consumer&lt;T&gt; | `void accept(T value)` |
| Cung cấp giá trị mà không cần đầu vào | Supplier&lt;T&gt; | `T get()` |
| Biến đổi một giá trị thành cùng kiểu | UnaryOperator&lt;T&gt; | Là dạng chuyên biệt của Function&lt;T, T&gt; |
| Kết hợp hai giá trị cùng kiểu thành một giá trị cùng kiểu | BinaryOperator&lt;T&gt; | Là dạng chuyên biệt của BiFunction&lt;T, T, T&gt; |

Các kiểu trong bảng thuộc những API chuẩn của Java; Runnable nằm trong java.lang, Callable trong java.util.concurrent, còn Function, Predicate, Consumer, Supplier và các biến thể nằm trong java.util.function. Chúng phù hợp khi hợp đồng của bạn thực sự giống với ý nghĩa và chữ ký của kiểu đó.

### Ví dụ: dùng java.util.function

Tệp `StandardFunctionsDemo.java`:

~~~java
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class StandardFunctionsDemo {
    public static void main(String[] args) {
        Function<String, Integer> length = String::length;
        Predicate<Integer> isPositive = n -> n > 0;
        Supplier<String> greeting = () -> "Xin chao";
        Consumer<String> print = System.out::println;

        System.out.println(length.apply("Java"));
        System.out.println(isPositive.test(3));
        System.out.println(greeting.get());
        print.accept("Hello");
    }
}
~~~

Chạy bằng Java 17 trở lên:

~~~bash
javac StandardFunctionsDemo.java
java StandardFunctionsDemo
~~~

Kết quả:

~~~text
4
true
Xin chao
Hello
~~~

Trong ví dụ này, Function biến chuỗi thành độ dài, Predicate kiểm tra số dương, Supplier tạo lời chào và Consumer in một chuỗi. Không cần tự định nghĩa interface mới vì ý nghĩa đã được các kiểu chuẩn diễn đạt rõ.

## 3. Khi nào nên tạo functional interface riêng?

Tạo interface riêng khi tên kiểu giúp thể hiện ý nghĩa nghiệp vụ, khi hợp đồng cần quy tắc đặc thù, hoặc khi phương thức cần khai báo checked exception mà kiểu chuẩn không hỗ trợ. Ví dụ, DiscountPolicy diễn đạt rõ đây là chính sách tính giá cuối cùng; Function&lt;Long, Long&gt; chỉ cho biết có một phép biến đổi số.

Tệp `DiscountDemo.java`:

~~~java
@FunctionalInterface
interface DiscountPolicy {
    long finalPrice(long originalPrice);
}

public class DiscountDemo {
    public static void main(String[] args) {
        DiscountPolicy memberDiscount = price -> price * 90 / 100;
        System.out.println(memberDiscount.finalPrice(100_000L));
    }
}
~~~

Chạy:

~~~bash
javac DiscountDemo.java
java DiscountDemo
~~~

Kết quả:

~~~text
90000
~~~

Tên DiscountPolicy làm rõ mục đích của tham số mà method nhận vào. Trong API công khai hoặc mã nghiệp vụ, kiểu có tên miền cụ thể thường dễ đọc hơn khi hợp đồng đó thực sự có ý nghĩa riêng.

Một trường hợp khác là checked exception. Các phương thức của Function và phần lớn kiểu trong java.util.function không khai báo checked exception. Nếu hành vi cần ném IOException, có thể xử lý ngoại lệ ngay trong lambda, bọc ngoại lệ thành unchecked exception, hoặc định nghĩa hợp đồng riêng:

~~~java
@FunctionalInterface
interface IOFunction<T, R> {
    R apply(T input) throws java.io.IOException;
}
~~~

Chỉ nên tạo kiểu như IOFunction khi việc truyền checked exception là một phần hữu ích của API. Tránh tạo các interface như MyFunction&lt;T, R&gt; chỉ để lặp lại Function&lt;T, R&gt; mà không bổ sung ý nghĩa hay hành vi nào.

## 4. Runnable và Callable trong tác vụ bất đồng bộ

Runnable và Callable đều mô tả tác vụ, nhưng hợp đồng khác nhau:

- **Runnable** không trả kết quả và phương thức run() không khai báo checked exception. Dùng khi cần thực hiện một hành động, chẳng hạn ghi log hoặc cập nhật trạng thái.
- **Callable&lt;T&gt;** trả về giá trị kiểu T và phương thức call() có thể khai báo Exception. Dùng khi tác vụ cần trả kết quả hoặc truyền checked exception về nơi lấy kết quả.
- Khi nộp tác vụ bằng ExecutorService.submit(...), kết quả là Future. Future.get() chờ tác vụ hoàn tất; với Callable, nó trả kết quả. Với Runnable, Future.get() trả về null khi tác vụ hoàn tất bình thường.
- Future.get() có thể ném InterruptedException hoặc ExecutionException. Mã thực tế nên xử lý hai trường hợp này phù hợp; nếu bắt InterruptedException mà không tiếp tục ném ra, thường cần khôi phục cờ ngắt bằng Thread.currentThread().interrupt().

Tệp `ExecutorTasksDemo.java`:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ExecutorTasksDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> completed = pool.submit(
                () -> System.out.println("Runnable ran")
            );
            Future<Integer> answer = pool.submit(() -> 40 + 2);

            completed.get();
            System.out.println(answer.get());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy:

~~~bash
javac ExecutorTasksDemo.java
java ExecutorTasksDemo
~~~

Kết quả:

~~~text
Runnable ran
42
~~~

Executor đơn luồng thực hiện hai tác vụ theo thứ tự đã nộp, nên đầu ra trên ổn định. finally bảo đảm gọi shutdown() kể cả khi lấy kết quả gặp lỗi. shutdown() ngừng nhận tác vụ mới và cho các tác vụ đã nộp có cơ hội hoàn tất; nó không tự chờ như awaitTermination().

Đừng nhầm Callable với Supplier&lt;T&gt;. Cả hai đều có thể trả về T, nhưng Supplier&lt;T&gt; là kiểu cung cấp giá trị tổng quát, không khai báo checked exception; Callable&lt;T&gt; được thiết kế cho tác vụ có kết quả trong API concurrency và có thể ném Exception. Hãy dùng đúng kiểu theo API mà bạn đang gọi.

## 5. Những biến thể hữu ích

Khi thao tác với kiểu nguyên thủy thường xuyên, dùng các interface chuyên biệt có thể tránh boxing/unboxing không cần thiết, ví dụ IntPredicate, IntConsumer, IntSupplier, IntUnaryOperator, LongUnaryOperator hoặc ToIntFunction&lt;T&gt;. Khi nhận hai tham số, cân nhắc BiFunction&lt;T, U, R&gt;, BiPredicate&lt;T, U&gt; hoặc BiConsumer&lt;T, U&gt;. Đừng chọn kiểu phức tạp hơn nếu Function, Predicate, Consumer hoặc Supplier đã mô tả đủ hợp đồng.

Một số functional interface chuẩn còn hỗ trợ ghép hành vi: Function có compose() và andThen(), Predicate có and(), or() và negate(), Consumer có andThen(). Những phương thức này hữu ích khi cần kết hợp nhiều bước mà vẫn giữ kiểu dữ liệu rõ ràng.

## 6. Quy trình chọn kiểu

1. **Kiểm tra chữ ký API trước.** Nếu method yêu cầu Runnable, Callable&lt;T&gt; hoặc một kiểu chuẩn khác, hãy cung cấp đúng kiểu đó.
2. **So khớp số lượng tham số và kiểu kết quả.** Không đầu vào/không kết quả thường là Runnable; không đầu vào/có kết quả có thể là Supplier&lt;T&gt; hoặc Callable&lt;T&gt; tùy API và quy tắc ngoại lệ; T thành R là Function&lt;T, R&gt;; T thành boolean là Predicate&lt;T&gt;.
3. **Xem ý nghĩa nghiệp vụ.** Nếu tên kiểu như DiscountPolicy, RetryPolicy hoặc AuthorizationRule làm hợp đồng rõ hơn đáng kể, interface riêng có thể phù hợp.
4. **Xem ngoại lệ và các ràng buộc đặc thù.** Nếu cần khai báo checked exception hoặc API cần hợp đồng riêng, cân nhắc interface tùy chỉnh.
5. **Phân biệt một hành vi với nhiều thao tác.** Một thao tác trừu tượng phù hợp với lambda; nhiều thao tác độc lập thường cần interface thông thường.
6. **Ưu tiên đơn giản và nhất quán.** Đừng tạo nhiều kiểu tương đương chỉ khác tên nếu chúng không diễn đạt khác biệt có ích.

## 7. Lỗi thường gặp

- Chọn Runnable cho công việc cần trả dữ liệu, rồi cố lấy kết quả từ Future của Runnable. Hãy dùng Callable&lt;T&gt; khi submit cần kết quả.
- Dùng Supplier&lt;T&gt; cho tác vụ nộp vào API yêu cầu Callable&lt;T&gt;. Hai kiểu có chữ ký gần nhau nhưng không thay thế tự động cho nhau; hãy dùng lambda phù hợp với kiểu đích mà API yêu cầu.
- Dùng Function&lt;T, Boolean&gt; cho điều kiện đúng/sai. Predicate&lt;T&gt; thể hiện mục đích rõ hơn và cung cấp các phép kết hợp điều kiện.
- Tạo interface riêng trùng hoàn toàn với Function hoặc Consumer mà không có ý nghĩa bổ sung. Việc này làm API có nhiều kiểu phải học và chuyển đổi hơn.
- Quên rằng lambda cần kiểu đích. Khai báo kiểu rõ ràng hoặc truyền lambda vào method có tham số functional interface; không thể suy luận kiểu đích cho một lambda độc lập như một giá trị thông thường.
- Cho nhiều overload chỉ khác nhau bởi các functional interface có chữ ký tương tự. Một số lambda có thể trở nên khó chọn hoặc gây mơ hồ; tên method riêng hoặc kiểu tham số rõ ràng giúp API dễ sử dụng hơn.
- Quên xử lý lỗi khi gọi Future.get(). Lỗi trong tác vụ thường được bọc trong ExecutionException; cần kiểm tra nguyên nhân gốc khi chẩn đoán.

Nguyên tắc thực hành: dùng interface chuẩn khi nó biểu đạt chính xác hợp đồng và phù hợp với API; dùng interface riêng khi tên miền, checked exception hoặc quy tắc đặc thù làm hợp đồng trở nên rõ ràng hơn. Với tác vụ concurrency, phân biệt rõ hành động không kết quả (Runnable) và tác vụ có kết quả (Callable&lt;T&gt;).

</details>

- [Quay lại đầu trang](#back-to-top)
