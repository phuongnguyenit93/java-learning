<a id="back-to-top"></a>

# Tên và bytecode

## Menu
- [Tên nhị phân, tên chuẩn và tên nội bộ của lớp](#names-and-bytecode-001)
- [Tên lớp lồng, lớp cục bộ và lớp vô danh](#names-and-bytecode-002)
- [Chuyển tên lớp thành đường dẫn tài nguyên .class](#names-and-bytecode-003)
- [Cấu trúc ClassFile tối thiểu cần biết](#names-and-bytecode-004)
- [Phiên bản major, minor và giới hạn JDK hỗ trợ bytecode](#names-and-bytecode-005)
- [Constant pool và tham chiếu ký hiệu tới kiểu, field, method](#names-and-bytecode-006)
- [Descriptor của kiểu, phương thức và lớp mảng](#names-and-bytecode-007)
- [Vì sao trình nạp phải nhận đúng tên và bytecode tương ứng](#names-and-bytecode-008)

## <a id="names-and-bytecode-001">Tên nhị phân, tên chuẩn và tên nội bộ của lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Tên nhị phân, tên chuẩn và tên nội bộ của lớp trong Java

> **Phạm vi:** Java Core → ClassLoader → Tên và bytecode. Bài này giải thích ba cách biểu diễn tên của lớp/interface trong mã nguồn, Java Reflection và định dạng `.class`, cùng cách dùng đúng chúng khi nạp lớp hoặc đọc bytecode. Các ví dụ chạy với Java 17+.

## 1. Vì sao cùng một lớp có nhiều tên?

Xét một lớp thành viên nằm trong `package demo`:

```java
package demo;

public class Outer {
    public static class Inner {}
}
```

Cùng một kiểu `Inner` có thể được nhắc đến bằng nhiều cách, tùy ngữ cảnh:

| Ngữ cảnh | Chuỗi | Ý nghĩa |
| --- | --- | --- |
| Mã nguồn Java / tên chuẩn (canonical) | `demo.Outer.Inner` | Cách viết tên kiểu bằng dấu chấm, phù hợp cách tham chiếu trong Java source. |
| Tên nhị phân (binary) | `demo.Outer$Inner` | Tên định danh lớp sau biên dịch; `$` nối lớp bao và lớp thành viên. |
| Tên nội bộ JVM (internal) | `demo/Outer$Inner` | Dạng tên lớp dùng trong cấu trúc class file và nhiều phần của bytecode. |
| Field descriptor (kiểu tham chiếu) | `Ldemo/Outer$Inner;` | Mô tả **kiểu** đối tượng trong descriptor; thêm `L` và `;` bao quanh internal name. |
| Tên tài nguyên class file | `demo/Outer$Inner.class` | Đường dẫn tài nguyên tương ứng, thường tìm qua classpath/module. |

**Không có một phép thay thế ký tự dùng được cho mọi trường hợp.** Cụ thể, dấu `.` giữa `Outer` và `Inner` trong tên chuẩn chuyển thành `$` ở tên nhị phân, còn dấu `.` ngăn *package* chuyển thành `/` trong tên nội bộ. ClassLoader nhận tên nhị phân; bộ phân tích bytecode thường cần tên nội bộ hoặc descriptor.

## 2. Tên chuẩn (canonical name)

Tên chuẩn là tên của kiểu theo quy tắc đặt tên trong ngôn ngữ Java (JLS §6.7):

- Lớp/interface cấp cao nhất: `java.util.Map`, `demo.Outer`.
- Lớp/interface thành viên: `java.util.Map.Entry`, `demo.Outer.Inner`.
- Kiểu mảng có phần tử có tên chuẩn: `java.lang.String[]`, `demo.Outer.Inner[][]`.
- Kiểu nguyên thủy: `int`, `boolean`; `void` cũng có cách hiển thị riêng qua đối tượng `Class`.

Trong Reflection, `Class.getCanonicalName()` trả về tên chuẩn **hoặc `null`** nếu kiểu không có tên chuẩn. Lớp local (khai báo trong phương thức/khối lệnh) và lớp anonymous thông thường **không có tên chuẩn**; vì vậy không được mặc định kết quả luôn là chuỗi.

Tên chuẩn hữu ích để giải thích cho người đọc hoặc tạo code Java. Nó không phải tham số chung cho `Class.forName`: với lớp thành viên, chuỗi `demo.Outer.Inner` là tên chuẩn, không phải tên nhị phân mà API này cần.

## 3. Tên nhị phân (binary name)

Theo JLS §13.1, tên nhị phân của các loại lớp/interface thông thường có quy tắc:

| Loại khai báo | Ví dụ tên nhị phân | Điểm cần nhớ |
| --- | --- | --- |
| Top-level | `demo.Outer` | Trùng tên chuẩn. |
| Member class/interface | `demo.Outer$Inner` | Ghép tên nhị phân lớp bao, `$`, tên đơn của member. |
| Local class | Dạng `demo.Outer$1Local` | Có phần chữ số do trình biên dịch tạo; **không dựa vào số cụ thể**. |
| Anonymous class | Dạng `demo.Outer$1` | Không có tên đơn/canonical; thứ tự đánh số không phải giao kèo ổn định để code phụ thuộc. |

`Class.getName()` trả về tên nhị phân cho lớp/interface thông thường không phải array và không phải hidden class. `Class.forName("demo.Outer$Inner")` hay `ClassLoader.loadClass("demo.Outer$Inner")` sử dụng tên nhị phân để tìm lớp đó (các overload khác nhau có cơ chế khởi tạo/loader khác nhau).

Lưu ý thêm:

- **Static nested class và non-static inner class** đều dùng `$` trong tên nhị phân; chữ `static` không thay đổi dạng tên.
- Ký tự `$` có thể nằm trong identifier Java hợp lệ. Do đó **không thể xác định chắc quan hệ lồng nhau chỉ bằng cách cắt chuỗi tại `$`**. Khi cần biết cấu trúc khai báo, dùng metadata/Reflection như `getDeclaringClass()`, `getEnclosingClass()`, `isMemberClass()`, `isLocalClass()` và `isAnonymousClass()`.
- Tên nhị phân của local/anonymous class là chi tiết trình biên dịch; sửa mã nguồn có thể đổi các file `$1`, `$2`. Tránh lưu cứng chúng làm API hay định danh bền vững.
- Một lớp còn có **định danh khi chạy** gắn với class loader đã định nghĩa nó: hai loader khác nhau có thể định nghĩa hai lớp khác nhau cùng tên nhị phân. Tên không tự bảo đảm hai `Class<?>` là cùng kiểu.

## 4. Tên nội bộ (internal name) trong JVM

JVMS §4.2.1 quy định *internal form* của tên nhị phân lớp/interface: thay dấu chấm **phân tách các thành phần package** bằng dấu gạch chéo `/`. Phần `$Inner` của tên member được giữ nguyên:

```text
Tên nhị phân:  java.util.Map$Entry
Tên nội bộ:   java/util/Map$Entry
```

Trong tệp `.class`, mục `CONSTANT_Class_info` của constant pool tham chiếu chuỗi tên nội bộ, chẳng hạn `demo/Outer$Inner`. Lệnh thao tác kiểu như `new`, `checkcast` hay `instanceof` cũng tham chiếu biểu diễn kiểu thông qua constant pool. Khi nhìn `javap -v` hoặc dùng thư viện thao tác bytecode, dấu `/` là chuyện bình thường.

**Không lẫn internal name với descriptor:**

- Internal name của lớp: `java/lang/String`.
- Field descriptor của tham chiếu đến lớp đó: `Ljava/lang/String;`.
- Descriptor của kiểu `int`: `I` (không có internal name của một *class* nguyên thủy).
- Descriptor của `String[]`: `[Ljava/lang/String;`.
- Method descriptor của phương thức nhận `String` và `int`, trả về `void`: `(Ljava/lang/String;I)V`.

Trong descriptor: `B` byte, `C` char, `D` double, `F` float, `I` int, `J` long, `S` short, `Z` boolean, `V` void (chỉ hợp lệ ở vị trí kiểu trả về của method descriptor), `L<internal-name>;` là reference, còn mỗi dấu `[` tăng một chiều mảng. Java generic bị xóa kiểu ở descriptor thông thường: `List<String>` có field descriptor `Ljava/util/List;`; nếu còn thông tin generic, đó là metadata `Signature` riêng.

Có thể hình dung đường đi của một lớp member như sau:

```text
Mã Java:             demo.Outer.Inner
                           |
                           | javac
                           v
Binary name:         demo.Outer$Inner
                           |
                           | JVM class-file encoding
                           v
Internal name:       demo/Outer$Inner
                           |
                           | field/method descriptor
                           v
Reference descriptor: Ldemo/Outer$Inner;
```

Đây là **các cách biểu diễn trong từng ngữ cảnh**, không phải các loại lớp khác nhau.

## 5. Quan sát bằng chương trình Java 17+

Tạo file `demo/NamesDemo.java`:

```java
package demo;

public class NamesDemo {
    static class Member {}

    public static void main(String[] args) throws Exception {
        show("top", NamesDemo.class);
        show("member", Member.class);
        show("String[]", String[].class);
        show("int[][]", int[][].class);
        show("primitive", int.class);

        class Local {}
        Object anonymous = new Object() {};
        System.out.println("local canonical null: "
                + (Local.class.getCanonicalName() == null));
        System.out.println("anonymous simple empty: "
                + anonymous.getClass().getSimpleName().isEmpty());
        System.out.println("loaded member: "
                + Class.forName("demo.NamesDemo$Member").getName());
    }

    static void show(String label, Class<?> type) {
        System.out.println(label + ": name=" + type.getName()
                + ", canonical=" + type.getCanonicalName()
                + ", simple=" + type.getSimpleName()
                + ", type=" + type.getTypeName());
    }
}
```

Biên dịch/chạy từ thư mục chứa `demo/`:

```bash
javac -d out demo/NamesDemo.java
java -cp out demo.NamesDemo
```

Đầu ra xác định trên Java 17+:

```text
top: name=demo.NamesDemo, canonical=demo.NamesDemo, simple=NamesDemo, type=demo.NamesDemo
member: name=demo.NamesDemo$Member, canonical=demo.NamesDemo.Member, simple=Member, type=demo.NamesDemo$Member
String[]: name=[Ljava.lang.String;, canonical=java.lang.String[], simple=String[], type=java.lang.String[]
int[][]: name=[[I, canonical=int[][], simple=int[][], type=int[][]
primitive: name=int, canonical=int, simple=int, type=int
local canonical null: true
anonymous simple empty: true
loaded member: demo.NamesDemo$Member
```

Diễn giải các method `Class`:

| Method | Công dụng | Lưu ý |
| --- | --- | --- |
| `getName()` | Binary name cho lớp/interface thường; mã dạng dấu `[` cho array; tên keyword cho primitive/void | **Không** luôn là tên nhị phân của lớp thường. |
| `getCanonicalName()` | Tên chuẩn để biểu diễn kiểu trong source | Có thể là `null` cho local, anonymous hoặc loại không có canonical name. |
| `getSimpleName()` | Tên ngắn; với anonymous class là chuỗi rỗng | Với array, thêm `[]` vào tên đơn của phần tử. |
| `getTypeName()` | Cách hiển thị tên kiểu dễ đọc hơn với array | Cho `String[]` là `java.lang.String[]`, không phải `[Ljava.lang.String;`. |
| `getPackageName()` | Tên package của kiểu | Không được dùng để suy diễn quan hệ member/local bằng cách cắt chuỗi. |

Ví dụ in `getName()` của local/anonymous class không được đưa vào đầu ra cố định vì phần số là chi tiết do compiler sinh; chương trình chỉ kiểm tra đặc tính được quy định.

## 6. Trường hợp mảng, primitive và hidden class

Với **mảng**, `Class.getName()` dùng một dạng gần với descriptor nhưng **tên lớp thành phần có dấu chấm**:

| Kiểu Java | `Class.getName()` | JVM field descriptor |
| --- | --- | --- |
| `int` | `int` | `I` |
| `int[]` | `[I` | `[I` |
| `int[][]` | `[[I` | `[[I` |
| `java.lang.String` | `java.lang.String` | `Ljava/lang/String;` |
| `java.lang.String[]` | `[Ljava.lang.String;` | `[Ljava/lang/String;` |
| `demo.Outer.Inner[][]` | `[[Ldemo.Outer$Inner;` | `[[Ldemo/Outer$Inner;` |

Bởi vậy, công thức `name.replace('.', '/')` chỉ giúp đổi tên **lớp/interface bình thường** từ dạng binary sang internal; với array, bạn đang làm việc với một biểu diễn kiểu khác. Khi triển khai framework hay bytecode tooling, hãy sử dụng bộ phân tích descriptor phù hợp thay vì ghép hoặc cắt chuỗi tùy tiện.

Với **primitive/void**, `int.class.getName()` trả `"int"` và `void.class.getName()` trả `"void"`; không thể nạp chúng bằng `Class.forName("int")` hay `Class.forName("void")`. Chúng không có file `int.class` hoặc `void.class` thông thường để ClassLoader tìm.

Từ các phiên bản Java hiện đại còn có **hidden class** (chẳng hạn được tạo qua `MethodHandles.Lookup.defineHiddenClass`). Theo API `Class.getName()` Java 17, tên hidden class có dạng `N/suffix`; tên này không phải binary name thông thường dùng để `Class.forName` tìm lại. Không suy ra mọi tên được in bởi `getName()` đều có thể nạp bằng tên đó.

## 7. ClassLoader và đường dẫn tài nguyên: chọn đúng tên

Ba thao tác phổ biến sử dụng ba hình thức khác nhau:

```java
// Binary name, với $ cho lớp thành viên:
Class<?> type = Class.forName("demo.NamesDemo$Member");

// ClassLoader nhận tên nhị phân của lớp thường:
Class<?> same = NamesDemo.class.getClassLoader()
        .loadClass("demo.NamesDemo$Member");

// Tên tài nguyên .class dùng dấu /, không dùng tên canonical:
var url = NamesDemo.class.getClassLoader()
        .getResource("demo/NamesDemo$Member.class");
```

Đoạn trên **minh họa cách gọi khi lớp `NamesDemo` có trên classpath**, không phải chương trình độc lập. `type == same` là `true` nếu cả hai lời gọi trả về cùng lớp do cùng defining loader; nếu lựa chọn loader khác đi thì không thể suy luận như vậy chỉ từ tên. `getResource` có thể trả `null` nếu tài nguyên không được tìm thấy hoặc không thể truy cập trong cấu hình/module cụ thể.

Một vài quy tắc cần phân biệt:

1. `Class.forName(String)` dùng tên nhị phân cho lớp thường; tên canonical của member như `"demo.NamesDemo.Member"` không nạp được lớp `Member` ở ví dụ. Với array, API còn chấp nhận dạng array name do `Class.getName()` trả về, ví dụ `"[I"`, `"[Ljava.lang.String;"`; nó không nhận cú pháp Java `"java.lang.String[]"`.
2. `ClassLoader.loadClass(String)` nhận tên nhị phân của lớp/interface để nạp, không phải đường dẫn `demo/NamesDemo$Member.class` hay internal name `demo/NamesDemo$Member`. Đừng giả định nó xử lý array descriptor như `Class.forName`.
3. `ClassLoader.getResource("demo/NamesDemo$Member.class")` dùng đường dẫn với `/`, **không có dấu `/` đầu tiên**; còn `NamesDemo.class.getResource("/demo/NamesDemo$Member.class")` cho đường dẫn tuyệt đối theo API `Class`.
4. Việc `loadClass` tìm được lớp nhưng `getResource` không trả file là điều có thể xảy ra ở loader tự định nghĩa, class được tạo động hoặc cấu hình module. Nạp lớp và tìm tài nguyên là hai tác vụ khác nhau.

## 8. Kiểm tra trực tiếp tên trong bytecode

Với file đã tạo ở mục 5, chạy:

```bash
javap -v -classpath out 'demo.NamesDemo$Member'
```

Lệnh trên dùng cách quote phù hợp với shell Unix để giữ ký tự `$` khỏi bị mở rộng. Trên Windows PowerShell, cũng có thể dùng dấu nháy đơn như vậy. Tìm các vị trí như:

```text
this_class: ... // demo/NamesDemo$Member
```

Dòng trên là **mẫu rút gọn minh họa**, vì số chỉ mục constant pool phụ thuộc nội dung bytecode và phiên bản compiler. Thử tiếp `javap -v -classpath out demo.NamesDemo` để quan sát `InnerClasses`, `EnclosingMethod` (nếu có) và các chuỗi descriptor trong constant pool. Dòng lệnh `javap` nhận tên lớp theo cú pháp của công cụ; phần annotation/comment trong output có thể hiển thị internal name.

Quan hệ giữa file name và class name cũng giúp debug:

- Source: `demo/NamesDemo.java`.
- Output top-level class: `out/demo/NamesDemo.class`.
- Output member: `out/demo/NamesDemo$Member.class`.
- Đường dẫn resource: `demo/NamesDemo$Member.class`.
- Chuỗi cần cho `Class.forName`: `demo.NamesDemo$Member`.

Đường dẫn vật lý hoặc nội dung JAR/module phụ thuộc cách build và class loader, nên không đồng nhất tên file với định danh kiểu lúc chạy.

## 9. Lỗi thường gặp và cách xử lý

| Biểu hiện | Nguyên nhân thường gặp | Cách kiểm tra/sửa |
| --- | --- | --- |
| `ClassNotFoundException` khi dùng `"demo.Outer.Inner"` | Truyền canonical thay vì binary name của member | Dùng `"demo.Outer$Inner"`, xác minh có đúng class trên classpath/module path. |
| Không tìm được resource `demo.Outer$Inner.class` | Resource name phải dùng `/` | Dùng `demo/Outer$Inner.class` với `ClassLoader.getResource`. |
| Descriptor `Ljava/lang/String;` không được API loadClass chấp nhận | Descriptor khác binary name | Dùng `java.lang.String` với API load lớp thường. |
| `getCanonicalName()` gây `NullPointerException` khi thao tác chuỗi | Local/anonymous/hidden class có thể không có canonical name | Kiểm tra `null`, hoặc dùng API name phù hợp mục đích. |
| Thay `$` thành `.` rồi nạp lớp thất bại | Cắt chuỗi nhị phân theo cấu trúc source mà loader không hiểu | Giữ binary name cho nạp lớp, chỉ dùng canonical name để hiển thị. |
| Hai lớp in cùng `getName()` nhưng cast thất bại | Khác defining class loader | Kiểm tra `clazz.getClassLoader()` và định danh runtime của từng `Class<?>`. |
| `NoClassDefFoundError` trong quá trình liên kết/nạp | Có thể thiếu dependency transitively hoặc lỗi xác định/khởi tạo lớp; không đủ bằng chứng chỉ từ dấu `/` | Xem nguyên nhân gốc (`Caused by`), classpath/module, loader và tên được JVM báo. |

**Quy trình debug thực dụng:** xác định API đang cần *binary name*, *internal name*, *descriptor* hay *resource path* → xem `getName()`/`getCanonicalName()` và loader của lớp → dùng `javap -v` để xác nhận tên nội bộ/descriptor → mới kiểm tra classpath, module path, quyền truy cập và class loader.

## 10. Tự kiểm tra

1. Một member interface `com.acme.Api.Response` có binary name, internal name và descriptor là gì?  
   **Đáp án:** `com.acme.Api$Response`; `com/acme/Api$Response`; `Lcom/acme/Api$Response;`.
2. `String[][].class.getName()` và field descriptor của kiểu này khác nhau thế nào?  
   **Đáp án:** `[[Ljava.lang.String;` so với `[[Ljava/lang/String;`.
3. `Local.class.getCanonicalName()` phải trả tên gì khi `Local` là lớp khai báo trong phương thức?  
   **Đáp án:** `null`. `Local.class.getName()` vẫn có tên do compiler tạo nhưng không nên phụ thuộc số thứ tự.
4. Vì sao `Class.forName("java.util.Map.Entry")` không nạp member interface `Entry`?  
   **Đáp án:** Chuỗi đó là canonical name; API yêu cầu binary name `java.util.Map$Entry`.
5. Hai class loader nạp cùng binary name có bắt buộc tạo một lớp duy nhất không?  
   **Đáp án:** Không; định danh lớp lúc chạy còn phụ thuộc defining loader.

## 11. Nguồn chuẩn để tra cứu

- [JLS §6.7 — Fully Qualified Names and Canonical Names](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.7): các quy tắc tên chuẩn.
- [JLS §13.1 — The Form of a Binary](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.1): định nghĩa tên nhị phân và cấu trúc class file.
- [JVMS §4.2.1 — Binary Class and Interface Names](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.2.1): biểu diễn tên nội bộ.
- [JVMS §4.3 — Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3): descriptor của field/method.
- [Java SE 17 API — `java.lang.Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getName`, `getCanonicalName`, `getSimpleName` và `getTypeName`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="names-and-bytecode-002">Tên lớp lồng, lớp cục bộ và lớp vô danh</a>

<details>
<summary>Xem chi tiết</summary>

# Tên lớp lồng, lớp cục bộ và lớp vô danh trong Java

## 1. Vì sao cần phân biệt lớp thành viên, cục bộ và vô danh?

Một lớp thành viên có thể được viết là `Outer.Inner` trong Java nhưng mang binary name `Outer$Inner` ở class file. Với **lớp cục bộ** và **lớp vô danh**, compiler còn tạo thành phần đánh số trong tên; chỉnh sửa mã hoặc đổi compiler có thể làm phần này thay đổi. Chỉ nhìn chuỗi tên `.class` không đủ xác định loại lớp, quan hệ bao ngoài hay cách khởi tạo.

Bài này phân biệt thành viên static, inner member, local class và anonymous class bằng khai báo Java, API Reflection và metadata bytecode; sau đó kiểm tra cách bắt biến, tạo instance và ranh giới với lambda.

## 2. Phân loại kiểu khai báo lồng nhau

Xét lớp ngoài Outer:

~~~java
public class Outer {
    static class StaticMember { }  // Lớp thành viên static

    class InnerMember { }           // Lớp thành viên không static

    void work() {
        class LocalTask { }         // Lớp cục bộ có tên, trong thân phương thức

        Runnable task = new Runnable() { // Lớp vô danh
            @Override
            public void run() { }
        };
    }
}
~~~

- **Lớp thành viên static (static nested class):** khai báo trực tiếp trong thân một kiểu với từ khóa static. Là kiểu thành viên của Outer, không gắn với một đối tượng Outer chỉ vì cách khai báo này. Ví dụ tạo đối tượng: new Outer.StaticMember() nếu quyền truy cập cho phép.
- **Lớp thành viên không static (inner member class):** khai báo trực tiếp trong kiểu bao ngoài và không có static. Một đối tượng InnerMember thường mang liên kết với đối tượng Outer bao ngoài; ví dụ tạo trong ngữ cảnh phù hợp: outer.new InnerMember().
- **Lớp cục bộ (local class):** khai báo có tên trong một khối, chẳng hạn thân phương thức, constructor hoặc khối khởi tạo. Phạm vi tên theo quy tắc lexical scope của nơi khai báo; không thể tham chiếu đơn giản từ bên ngoài phạm vi đó như một lớp thành viên.
- **Lớp vô danh (anonymous class):** khai báo và tạo đối tượng qua biểu thức new với thân lớp; không có tên lớp do người lập trình đặt. Ví dụ new Runnable() { ... } tạo một lớp triển khai Runnable; new SomeBase() { ... } tạo lớp con của SomeBase. Không dùng anonymous class để khai báo một kiểu độc lập có thể gọi tên trong mã nguồn.

Theo thuật ngữ JLS, **nested class** bao gồm lớp thành viên, lớp cục bộ và lớp vô danh. **Inner class** là các lớp lồng không được khai báo static theo các điều kiện của ngôn ngữ, bao gồm inner member, local class và anonymous class trong trường hợp thông thường. Đừng đồng nhất mọi nested class với inner member class.

## 3. Bốn kiểu tên cần phân biệt

| Khái niệm | Dùng để làm gì? | Ví dụ với lớp thành viên Outer.InnerMember |
| --- | --- | --- |
| **Simple name** | Tên ngắn không kèm kiểu bao ngoài; lấy bằng Class.getSimpleName() | InnerMember |
| **Canonical name** | Tên chuẩn tắc theo dạng tên Java khi kiểu có canonical name; lấy bằng Class.getCanonicalName() | Outer.InnerMember |
| **Binary name** | Tên định danh kiểu trong cơ chế liên kết và tải lớp; Class.getName() trả tên này với các lớp thường | Outer$InnerMember |
| **JVM internal name** | Tên trong cấu trúc class file/constant pool, phân tách package bằng dấu / | demo/Outer$InnerMember |

Nếu Outer nằm trong package demo, tên chuẩn tắc là demo.Outer.InnerMember và tên nhị phân là demo.Outer$InnerMember. Ký tự $ nối tên lớp bao ngoài với lớp thành viên trong **binary name**; dấu chấm vẫn là cú pháp được viết trong Java source.

Tên file do javac thường tạo ra tương ứng với binary name, thay các dấu chấm package bằng đường dẫn thư mục: demo/Outer$InnerMember.class. Với kiểu reference, JVM descriptor dùng dạng Ldemo/Outer$InnerMember; (có dấu chấm phẩy cuối). **Tên class file, binary name và descriptor là các khái niệm liên quan nhưng không thể hoán đổi cú pháp.**

Lưu ý: dấu $ cũng có thể xuất hiện hợp lệ trong tên định danh do lập trình viên đặt. Vì vậy không được tự tách một chuỗi chỉ dựa vào mọi ký tự $ rồi khẳng định cấu trúc nguồn. Cũng không được dùng tên hiển thị từ toString() làm giao thức định danh lớp.

## 4. Cách javac đặt tên cho từng dạng

Giả sử lớp ngoài là demo.Outer. Bảng dưới minh họa cách đặt tên thường gặp khi biên dịch bằng javac; phần số do trình biên dịch quyết định, không phải tên nguồn ổn định.

| Dạng lớp | Biểu thức/khai báo trong source | Binary name minh họa | Canonical name | Simple name |
| --- | --- | --- | --- | --- |
| Lớp trên cùng (top-level) | class Outer | demo.Outer | demo.Outer | Outer |
| Thành viên static | static class StaticMember | demo.Outer$StaticMember | demo.Outer.StaticMember | StaticMember |
| Thành viên không static | class InnerMember | demo.Outer$InnerMember | demo.Outer.InnerMember | InnerMember |
| Cục bộ có tên | class LocalTask trong phương thức | demo.Outer$1LocalTask | null | LocalTask |
| Vô danh | new Runnable() { ... } | demo.Outer$1 | null | chuỗi rỗng |

**Nhận xét quan trọng:**

1. Lớp thành viên có tên được ghép với lớp bao ngoài bằng dấu $ trong tên nhị phân; tên chuẩn tắc của lớp thành viên vẫn dùng dấu chấm. Tính static không quyết định sự có mặt của dấu $.
2. Lớp cục bộ có **simple name** là tên đã viết (LocalTask), nhưng **không có canonical name**. Tên nhị phân chứa phần số giữa tên lớp bao ngoài và tên cục bộ theo quy tắc JLS; ví dụ có thể là Outer$1LocalTask.
3. Lớp vô danh không có tên đơn giản trong source: Class.getSimpleName() trả chuỗi rỗng; Class.getCanonicalName() trả null. Binary name ví dụ Outer$1, Outer$2.
4. **Không dựa vào số 1, 2, 3 để suy ra thứ tự thực thi hay ý nghĩa nghiệp vụ.** Chỉnh sửa source, đổi phạm vi lồng nhau hoặc thay compiler có thể làm thay đổi tên phát sinh. Dữ liệu cấu trúc từ reflection và thuộc tính class file đáng tin hơn việc đoán bằng regex.
5. Với lớp cục bộ hoặc vô danh nằm sâu trong lớp lồng khác, tiền tố binary name có thể chứa nhiều dấu $. Tên phát sinh là hiện vật biên dịch, không nên ghi cứng làm API công khai, cấu hình dài hạn hay khóa lưu trữ.

## 5. Ví dụ Java 17+ chạy được: quan sát Reflection

Tạo file **ClassNameDemo.java** với toàn bộ mã sau (không cần package để lệnh chạy ngắn gọn):

~~~java
public class ClassNameDemo {
    static class StaticMember { }

    class InnerMember { }

    private static void inspect(String label, Class<?> type) {
        System.out.printf(
                "%s | simple='%s' | canonical=%s | member=%b | local=%b | anonymous=%b%n",
                label,
                type.getSimpleName(),
                type.getCanonicalName(),
                type.isMemberClass(),
                type.isLocalClass(),
                type.isAnonymousClass()
        );
    }

    private static void demonstrate() {
        class Local { }

        Runnable anonymous = new Runnable() {
            @Override
            public void run() { }
        };

        inspect("static", StaticMember.class);
        inspect("inner", InnerMember.class);
        inspect("local", Local.class);
        inspect("anonymous", anonymous.getClass());
    }

    public static void main(String[] args) {
        demonstrate();
    }
}
~~~

Biên dịch tương thích Java 17 rồi chạy (máy cần JDK 17 trở lên):

~~~bash
javac --release 17 ClassNameDemo.java
java ClassNameDemo
~~~

**Stdout chính xác:**

~~~text
static | simple='StaticMember' | canonical=ClassNameDemo.StaticMember | member=true | local=false | anonymous=false
inner | simple='InnerMember' | canonical=ClassNameDemo.InnerMember | member=true | local=false | anonymous=false
local | simple='Local' | canonical=null | member=false | local=true | anonymous=false
anonymous | simple='' | canonical=null | member=false | local=false | anonymous=true
~~~

Chương trình không in phần số trong binary name, vì phần số đó không phù hợp làm kết quả cố định giữa các trường hợp biên dịch. Muốn xem tên nhị phân đang có ở máy mình, thêm lệnh **System.out.println(type.getName());** trong inspect; với javac bạn sẽ thấy các dạng như ClassNameDemo$StaticMember, ClassNameDemo$InnerMember, ClassNameDemo$1Local và ClassNameDemo$1 (con số cụ thể phụ thuộc kết quả biên dịch).

**Đọc kết quả:** cả static member và inner member trả isMemberClass() = true, nhưng một lớp member có static hay không cần kiểm tra modifier khi thực sự quan tâm. Local và anonymous đều không phải member class. Giá trị null của canonical name không có nghĩa lớp không có binary name hoặc không có class file.

## 6. Reflection: xác định quan hệ bao ngoài một cách có cấu trúc

Các API trên java.lang.Class giúp trả lời các câu hỏi khác nhau:

| API | Ý nghĩa | Đối với ví dụ trên |
| --- | --- | --- |
| getName() | Tên nhị phân cho lớp reference thông thường | ClassNameDemo$InnerMember; local/anonymous có tên do compiler phát sinh |
| getSimpleName() | Tên đơn giản, có thể rỗng | Local → Local; anonymous → "" |
| getCanonicalName() | Tên chuẩn tắc nếu tồn tại | Member → ClassNameDemo.Member; local/anonymous → null |
| isMemberClass() | Có được khai báo làm kiểu thành viên trực tiếp của kiểu khác? | true cho hai lớp thành viên |
| isLocalClass() | Là lớp cục bộ? | true cho Local |
| isAnonymousClass() | Là lớp vô danh? | true cho biểu thức new Runnable() { ... } |
| getDeclaringClass() | Kiểu khai báo thành viên này, nếu có | ClassNameDemo với member; null cho local/anonymous |
| getEnclosingClass() | Kiểu bao ngoài về mặt khai báo, nếu có | ClassNameDemo cho cả bốn dạng đang xét |
| getEnclosingMethod() | Phương thức chứa khai báo lớp local/anonymous, nếu có | demonstrate với Local và anonymous; null với hai member |

Nếu lớp local/anonymous được khai báo trong **constructor**, có thể xem getEnclosingConstructor() thay vì getEnclosingMethod(). Một lớp thành viên có enclosing class nhưng không có enclosing method. Class.getDeclaredClasses() trả các lớp/interface **thành viên được khai báo trực tiếp** bởi lớp đó, không liệt kê mọi local class và anonymous class có thể xuất hiện trong thân phương thức; đừng dùng nó để khẳng định đã tìm hết mọi file Outer$*.class.

Trường hợp cần phát hiện thành viên static, có thể dùng **java.lang.reflect.Modifier.isStatic(type.getModifiers())** cùng với isMemberClass(). Điều này phân biệt tường minh tính static của thành viên và việc lớp có một dấu $ trong binary name.

## 7. Lớp cục bộ và vô danh có thể bắt biến như thế nào?

Lớp local và anonymous có thể dùng các biến cục bộ và tham số từ phạm vi bao quanh nếu chúng là **final hoặc effectively final** (giá trị không bị gán lại sau khi được khởi tạo). Hành vi này giải thích vì sao bytecode của lớp phát sinh đôi khi có trường tổng hợp (synthetic field) để giữ giá trị đã bắt. Ngoài ra inner class khai báo trong ngữ cảnh instance có thể cần tham chiếu tới instance bao ngoài.

Ví dụ **CaptureDemo.java** độc lập:

~~~java
public class CaptureDemo {
    void demo() {
        int base = 10; // effectively final

        class LocalAdder {
            int add(int value) {
                return base + value;
            }
        }

        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println(base);
            }
        };

        System.out.println(new LocalAdder().add(5));
        anonymous.run();
    }

    public static void main(String[] args) {
        new CaptureDemo().demo();
    }
}
~~~

Chạy bằng **javac --release 17 CaptureDemo.java** rồi **java CaptureDemo**:

~~~text
15
10
~~~

Nếu thêm **base++** sau khi khởi tạo, lớp local và anonymous đang truy cập biến đó sẽ gây lỗi biên dịch vì base không còn effectively final. Tính ràng buộc này thuộc ngữ nghĩa của Java source; nó không được suy ra đơn thuần từ tên có $.

Một đối tượng của inner member class được tạo từ instance ngoài khác với đối tượng static member. Tuy nhiên không nên khẳng định rằng *mọi* file lớp lồng luôn có trường this$0 hoặc val$...: compiler và tối ưu có thể thay đổi biểu diễn, nhất là khi không cần giữ tham chiếu. Kiểm tra bytecode thực tế khi phân tích.

## 8. Từ source sang file .class: quan sát bằng javap

Sau khi biên dịch ClassNameDemo.java, thư mục đầu ra thường có các file dạng sau:

~~~text
ClassNameDemo.class
ClassNameDemo$StaticMember.class
ClassNameDemo$InnerMember.class
ClassNameDemo$1Local.class
ClassNameDemo$1.class
~~~

Đây là **minh họa cho đầu ra thường gặp của javac**, không phải hợp đồng rằng phần số luôn bằng 1 ở mọi phiên bản compiler hay sau mọi chỉnh sửa mã. Mỗi lớp local/anonymous dùng trong source có thể được biên dịch thành class file riêng. Không được hiểu rằng chỉ file ClassNameDemo.class mới chứa toàn bộ mã bytecode của tất cả các lớp lồng.

Quan sát cấu trúc class file:

~~~bash
javap -p -v ClassNameDemo
~~~

Bạn có thể thấy các thuộc tính **InnerClasses**, **NestMembers** hoặc thông tin lớp lồng liên quan. Khi dùng javap -v trên file của một lớp cục bộ hay vô danh, có thể thấy thêm **EnclosingMethod**. Các metadata này giúp JVM và reflection mô tả quan hệ khai báo.

- **InnerClasses** lưu mô tả các lớp được lồng vào nhau theo cấu trúc class file, gồm thông tin inner/outer và inner name khi áp dụng.
- **EnclosingMethod** có thể cho biết lớp local/anonymous được khai báo ở kiểu và phương thức/constructor nào.
- **NestHost/NestMembers** (cơ chế nestmates từ Java 11) hỗ trợ quan hệ truy cập private giữa các thành viên cùng nest ở cấp JVM. Đây là khái niệm kỹ thuật của JVM, không phải phép thay thế cho phân loại isLocalClass/isAnonymousClass.
- Các tên trường/hàm synthetic và chi tiết đánh số phụ thuộc compiler; tránh xây ứng dụng dựa trên một bố cục bytecode quan sát được ở một lần biên dịch.

Lưu ý khi đọc bằng PowerShell: tên file chứa dấu $ nên dùng dấu nháy đơn nếu truyền tên file cụ thể cho công cụ hoặc lệnh; trong nhiều shell dấu $ có thể bị diễn giải như biến.

## 9. ClassLoader, Class.forName và đường dẫn tài nguyên

Với lớp thành viên **demo.Outer.InnerMember**, ba chuỗi sau có ý nghĩa khác nhau:

~~~text
Tên trong Java source:          demo.Outer.InnerMember
Binary name dùng để tải lớp:     demo.Outer$InnerMember
Resource path của class file:    demo/Outer$InnerMember.class
~~~

**Class.forName("demo.Outer$InnerMember")** nhận tên nhị phân của lớp reference bình thường; **Class.forName("demo.Outer.InnerMember")** không tự hiểu dấu chấm cuối là lớp thành viên, nên thông thường sẽ dẫn tới ClassNotFoundException khi không tồn tại lớp có đúng binary name ấy. Với ClassLoader.loadClass, tên đầu vào cho lớp thông thường cũng là binary name, còn ClassLoader.getResource/getResourceAsStream sử dụng tên tài nguyên với dấu / như cấu trúc đường dẫn.

Ví dụ sau là **đoạn mã minh họa**, giả sử đã có lớp demo.Outer.InnerMember trên classpath:

~~~java
Class<?> member = Class.forName("demo.Outer$InnerMember");
String binary = member.getName(); // "demo.Outer$InnerMember"
String resource = "demo/Outer$InnerMember.class";
var stream = member.getClassLoader().getResourceAsStream(resource);
~~~

Nếu dùng **SomeClass.class.getResource("...")**, quy tắc tương đối so với package và dấu / đầu đường dẫn khác với **ClassLoader.getResource("...")**. Khi viết công cụ đọc class file, chọn đúng API và đúng đường dẫn để tránh tìm nhầm.

Đối với local/anonymous class, nếu biết binary name thực tế thì cơ chế tải lớp có thể xử lý tên đó, nhưng **không nên hardcode** các tên compiler sinh như Outer$1 hoặc Outer$2 làm định danh bền vững. Khi bạn đã có đối tượng, hãy dùng obj.getClass(); khi có Class<?> hãy dùng getName() để lấy giá trị thực tế. Đồng thời nhớ rằng danh tính một lớp runtime phụ thuộc **cả binary name và defining class loader**: hai loader khác nhau có thể định nghĩa hai Class<?> khác nhau từ cùng tên.

**Class.forName(String)** mặc định khởi tạo lớp được tìm theo quy tắc của API; tải/tra cứu lớp và việc tạo instance không phải cùng hành động. Inner member class có thể được load thành công, trong khi việc gọi constructor của nó vẫn đòi instance bao ngoài và tuân thủ quy tắc truy cập tương ứng.

## 10. Đừng nhầm anonymous class với lambda

Hai đoạn này đều cung cấp hành vi của Runnable, nhưng không cùng cơ chế ngôn ngữ:

~~~java
Runnable a = new Runnable() {
    @Override
    public void run() {
        System.out.println("A");
    }
};

Runnable b = () -> System.out.println("B");
~~~

**a** được tạo bởi một biểu thức khai báo lớp vô danh; Class.isAnonymousClass() trả true cho lớp sinh từ nó. **b** là biểu thức lambda; JVM/compiler có thể hiện thực bằng invokedynamic và lớp ẩn hoặc kỹ thuật khác, tùy phiên bản/triển khai. Không được coi lambda là một anonymous class có tên ổn định Outer$N hay suy diễn rằng b.getClass().isAnonymousClass() phải true. Các bytecode và tên lớp triển khai lambda không nên là thành phần API của ứng dụng.

Tương tự, method reference (ví dụ System.out::println) là biểu thức theo cơ chế lambda, không phải cú pháp khai báo anonymous class.

## 11. Lỗi thường gặp và cách kiểm tra

1. **Dùng dấu chấm để Class.forName tải inner member:** viết demo.Outer.InnerMember vì nhìn giống source. Hãy tra tên nhị phân thực bằng Class.getName(); với member class trong ví dụ là demo.Outer$InnerMember.
2. **Tin rằng mọi lớp có $ là anonymous:** member class, local class, tên định danh chứa $ đều có thể tạo binary name chứa $. Dùng isMemberClass/isLocalClass/isAnonymousClass cùng metadata.
3. **Cho rằng local class có canonical name vì có tên trong source:** getSimpleName() có thể là Local, nhưng getCanonicalName() trả null.
4. **Lấy thứ tự $1/$2 làm thứ tự chạy:** tên được tạo lúc biên dịch, không phản ánh số đối tượng đã tạo hay thứ tự thực thi.
5. **Xem getDeclaredClasses() là danh sách mọi lớp con trong file source:** API này phản ánh kiểu thành viên trực tiếp, không phải toàn bộ lớp local/anonymous nằm trong phương thức.
6. **Đánh đồng static member với inner member:** static nested không mặc nhiên cần instance Outer; inner member thường có ngữ cảnh instance Outer khi tạo.
7. **Phụ thuộc tên file synthetic hay trường this$0:** các chi tiết javac có thể biến đổi; chỉ dùng để debug/quan sát, không làm hợp đồng tương thích.
8. **Đổi anonymous class sang lambda rồi mong cùng reflection:** lambda có cơ chế triển khai khác, không bảo đảm tên Class, binary class file hay isAnonymousClass() giống nhau.
9. **Tự ghép đường dẫn resource từ canonical name:** với member, canonical name có dấu chấm ở ranh giới lớp; resource path phải phản ánh binary/internal name thực tế.

Một quy trình debug hiệu quả: (1) xác định khai báo trong source, (2) in Class.getName(), getSimpleName(), getCanonicalName() và các cờ phân loại, (3) kiểm tra thư mục output .class, (4) dùng javap -v xem metadata khi cần, (5) kiểm tra classpath và class loader nếu việc tải lớp thất bại. Các bước này giúp nối source với bytecode mà không phỏng đoán dựa vào phần số trong tên.

## 12. Tự kiểm tra kiến thức

**Tình huống A.** Outer có static class Entry và class Worker. Cả hai có thể cho Class.getName() chứa ký tự $? **Có.** Tính static ảnh hưởng quan hệ instance, không loại bỏ quy tắc binary name của lớp thành viên.

**Tình huống B.** Một local class tên Handler trả getSimpleName() bằng Handler. Có thể suy ra getCanonicalName() = Outer.Handler? **Không.** Local class không có canonical name dù có simple name.

**Tình huống C.** Bạn thấy file Outer$2.class. Có thể kết luận đó là đối tượng anonymous thứ hai được tạo lúc chạy? **Không.** Đây là tên hiện vật biên dịch; số không biểu diễn số đối tượng runtime. Xác minh bằng metadata/reflection.

**Tình huống D.** Có thể tải Outer$Inner bằng Class.forName nhưng không thể viết new Outer.Inner() nếu Inner là inner member và chưa có instance Outer? **Đúng.** Tải kiểu khác với cấp phát đối tượng; tạo inner member cần ngữ cảnh instance bao ngoài phù hợp.

**Tình huống E.** Dùng lambda thay new Runnable() { ... } có bảo đảm vẫn tạo file Outer$1.class? **Không.** Cơ chế dịch lambda và chi tiết lớp triển khai không phải anonymous class cố định.

## Tài liệu chuẩn để đối chiếu

- Java Language Specification, §8.1.3 — Inner Classes and Enclosing Instances: https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.3
- Java Language Specification, §13.1 — The Form of a Binary: https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.1
- Java Language Specification, §15.9.5 — Anonymous Class Declarations: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9.5
- Java 17 API, java.lang.Class: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- JVM Specification, §4.7 — Attributes of class file: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="names-and-bytecode-003">Chuyển tên lớp thành đường dẫn tài nguyên .class</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển tên lớp Java thành đường dẫn tài nguyên `.class`

Trong Java, khi biết **tên nhị phân (binary name)** của một lớp hoặc interface, ta thường có thể suy ra **tên tài nguyên tương đối** của tệp bytecode bằng quy tắc:

```text
tên nhị phân:  com.example.service.UserService
tên tài nguyên: com/example/service/UserService.class
```

Công thức cốt lõi là **thay mỗi dấu chấm (`.`) phân tách package bằng dấu gạch chéo xuôi (`/`), rồi nối `.class`**. Đây là quy ước đặt tệp class trong thư mục hoặc JAR; kết quả là *tên tài nguyên* dùng cho các API lookup, không phải đường dẫn hệ điều hành tuyệt đối và cũng không chứng minh tài nguyên thực sự tồn tại.

## 1. Hiểu đúng các loại tên

- **Tên nhị phân (binary name):** tên dùng để định danh lớp khi nạp qua các API như `ClassLoader.loadClass(String)`; ví dụ `com.example.Outer$Inner`. Lấy từ `clazz.getName()` đối với một lớp/interface có tên thông thường.
- **Tên nội bộ JVM (internal name):** dùng `/` giữa các thành phần package, chẳng hạn `com/example/Outer$Inner`. Nó xuất hiện trong mô tả bytecode/class file và gần với phần tên tài nguyên trước hậu tố `.class`.
- **Tên tài nguyên class:** tên nội bộ tương ứng cộng `.class`, ví dụ `com/example/Outer$Inner.class`. Các API tài nguyên thông thường dùng dấu `/` trên mọi hệ điều hành.
- **Tên canonical:** `clazz.getCanonicalName()` thường cho tên dễ đọc trong mã Java, ví dụ `com.example.Outer.Inner`. Dùng chuỗi này để thay `.` sẽ *sai* đối với lớp lồng nhau, vì nó sẽ tạo ra `Outer/Inner.class` thay cho `Outer$Inner.class`.
- **Tên đơn giản:** `clazz.getSimpleName()` trả về `Inner` hoặc `UserService`, nhưng thiếu package và tên lớp bao ngoài; không đủ để suy ra đường dẫn đầy đủ.

Ví dụ đối chiếu:

| Loại | Tên nhị phân | Đường dẫn tài nguyên thường gặp |
|---|---|---|
| Lớp top-level trong package | `org.acme.App` | `org/acme/App.class` |
| Lớp top-level ở package mặc định | `App` | `App.class` |
| Lớp thành viên static hoặc non-static | `org.acme.Outer$Inner` | `org/acme/Outer$Inner.class` |
| Interface | `org.acme.Task` | `org/acme/Task.class` |
| Lớp anonymous do trình biên dịch đặt tên | Ví dụ `org.acme.Outer$1` | Ví dụ `org/acme/Outer$1.class` |

Tên của lớp local/anonymous phụ thuộc cấu trúc chương trình và trình biên dịch; hãy lấy trực tiếp từ `Class.getName()` nếu cần thay vì tự đoán chỉ số `$1`, `$2`.

## 2. Quy tắc chuyển đổi

Với một **binary name hợp lệ của lớp/interface có tên**, phép chuyển đổi thông dụng là:

```java
String resourcePath = binaryName.replace('.', '/') + ".class";
```

Các bước có ý nghĩa rõ ràng:

1. Bắt đầu với binary name, ví dụ `com.acme.Outer$Worker`.
2. Đổi dấu `.` ngăn cách package thành `/`: `com/acme/Outer$Worker`.
3. **Giữ nguyên ký tự `$`** trong tên nhị phân của lớp lồng nhau; `$` là một phần tên tệp đã được biên dịch.
4. Nối chính xác hậu tố `.class`: `com/acme/Outer$Worker.class`.

Không dùng `File.separator` để thay `/`: tên tài nguyên Java có quy ước dùng dấu `/` ngay cả trên Windows. Không dùng `replace(".", File.separator)` hoặc `Path.of(...)` nếu đích là tham số `ClassLoader.getResource`. Khi cần truy cập tệp vật lý trên file system, hãy xử lý riêng việc ánh xạ thư mục, URL và đường dẫn tương ứng.

Điều kiện quan trọng: nếu chuỗi đầu vào vốn đã là `com/acme/Foo.class` thì **không** áp dụng thêm công thức. Phải xác định đầu vào là *binary name*, không phải đường dẫn tài nguyên, tên canonical hoặc chuỗi hiển thị tùy ý.

## 3. Ví dụ Java 17+ chạy được

Lưu nguồn vào `src/demo/path/ResourcePathDemo.java` (tạo trước các thư mục cần thiết):

```java
package demo.path;

public class ResourcePathDemo {
    static class Nested {
    }

    static String classFilePath(Class<?> type) {
        if (type.isPrimitive() || type.isArray()) {
            throw new IllegalArgumentException(
                    "Primitive and array types have no own .class resource");
        }
        return type.getName().replace('.', '/') + ".class";
    }

    public static void main(String[] args) {
        Class<?> type = Nested.class;
        String path = classFilePath(type);
        ClassLoader loader = type.getClassLoader();

        System.out.println("Binary name: " + type.getName());
        System.out.println("Canonical name: " + type.getCanonicalName());
        System.out.println("Resource path: " + path);
        System.out.println("Via loader: " + (loader.getResource(path) != null));
        System.out.println("Via class absolute: "
                + (type.getResource("/" + path) != null));
        System.out.println("Via class relative: "
                + (type.getResource("ResourcePathDemo$Nested.class") != null));
    }
}
```

Biên dịch và chạy từ thư mục chứa `src`:

```bash
javac -d out src/demo/path/ResourcePathDemo.java
java -cp out demo.path.ResourcePathDemo
```

Kết quả khi chạy với các class vừa biên dịch trên classpath `out`:

```text
Binary name: demo.path.ResourcePathDemo$Nested
Canonical name: demo.path.ResourcePathDemo.Nested
Resource path: demo/path/ResourcePathDemo$Nested.class
Via loader: true
Via class absolute: true
Via class relative: true
```

`javac -d out` đặt `ResourcePathDemo.class` và `ResourcePathDemo$Nested.class` vào thư mục `out/demo/path/`. Lệnh `java -cp out` đưa thư mục `out` lên classpath, do đó tài nguyên được tìm thấy. Nếu bạn thay đổi classpath, đóng gói vào JAR/module hoặc sử dụng bộ nạp lớp đặc biệt, trạng thái tìm được tài nguyên có thể khác.

Trong hàm `classFilePath`, sử dụng `Class<?>` giúp lấy trực tiếp binary name của lớp đang xét, tránh nhầm với tên canonical. Hàm chủ động từ chối kiểu nguyên thủy và kiểu mảng vì chúng không có tệp `.class` độc lập tương ứng với chính kiểu đó. Với các loại lớp sinh động/hidden, đường dẫn xây được vẫn chưa đảm bảo có tài nguyên để đọc.

## 4. Tra cứu tài nguyên: `ClassLoader` và `Class`

Hai kiểu lookup dưới đây có quy tắc tên đầu vào khác nhau:

| API | Đầu vào | Cách hiểu |
|---|---|---|
| `loader.getResource("demo/path/ResourcePathDemo.class")` | Không có `/` đầu | Tên tài nguyên tính từ **gốc lookup của class loader** |
| `loader.getResourceAsStream("demo/path/ResourcePathDemo.class")` | Không có `/` đầu | Tìm tài nguyên và mở stream để đọc byte |
| `ResourcePathDemo.class.getResource("/demo/path/ResourcePathDemo.class")` | Có `/` đầu | Tên tuyệt đối từ gốc tài nguyên theo API của `Class` |
| `ResourcePathDemo.class.getResource("ResourcePathDemo.class")` | Không có `/` đầu | Tên tương đối so với package `demo.path` |
| `ResourcePathDemo.class.getResourceAsStream("ResourcePathDemo.class")` | Không có `/` đầu | Mở stream tương đối so với package |

Đối với ví dụ trên, gọi `type.getResource("demo/path/ResourcePathDemo$Nested.class")` sẽ tìm tương đối trong `demo/path/`, tức thực chất tìm đường dẫn bắt đầu bằng `demo/path/demo/path/...`, nên thường trả `null`. Nếu đã có đường dẫn đầy đủ, thêm `/` phía trước khi gọi `Class.getResource(...)`; **không** thêm `/` khi gọi `ClassLoader.getResource(...)`.

Các API `getResource` trả `URL` hoặc `null`, còn `getResourceAsStream` trả `InputStream` hoặc `null`. Với stream, phải đóng bằng try-with-resources. URL có thể là `file:`, `jar:`, `jrt:` hoặc dạng do loader cung cấp; không ép mọi URL thành `Path` trên đĩa.

```java
String path = MyType.class.getName().replace('.', '/') + ".class";
try (java.io.InputStream input =
             MyType.class.getResourceAsStream("/" + path)) {
    if (input == null) {
        throw new java.io.FileNotFoundException(path);
    }
    byte[] bytes = input.readAllBytes(); // Java 9+
    System.out.println("Read " + bytes.length + " bytes");
}
```

Đây là **đoạn minh họa dùng trong một phương thức có xử lý/khai báo `IOException` và có lớp `MyType`**, không phải tệp Java độc lập. Số byte phụ thuộc bản biên dịch nên không có stdout cố định. Cách gọi qua `MyType.class` còn tiện khi class loader của lớp là bootstrap loader: `MyType.class.getClassLoader()` có thể trả `null`, còn `MyType.class.getResource(...)` vẫn là API hợp lệ để tra cứu tài nguyên của lớp.

## 5. Phân biệt việc tạo tên, tìm tài nguyên và nạp lớp

Ba thao tác này khác nhau:

```text
Binary name
    |
    | replace('.', '/') + ".class"
    v
Resource name              (biến đổi chuỗi, chưa có I/O)
    |
    | getResource / getResourceAsStream
    v
URL / InputStream / null   (tìm hoặc đọc tài nguyên)
    
ClassLoader.loadClass(binaryName)
    |
    v
Class<?>                   (cơ chế nạp/định nghĩa lớp)
```

- `ClassLoader.loadClass("demo.path.ResourcePathDemo")` nhận **binary name** có dấu `.`; truyền `demo/path/ResourcePathDemo.class` vào API này là sai loại đầu vào.
- Chuyển tên chỉ là xử lý văn bản. Nó không gọi `loadClass`, không mở JAR, không đọc bytecode và không khởi tạo lớp.
- Nếu đã có đối tượng `Class<?>`, lớp tương ứng đã được định nghĩa trong JVM, nhưng điều đó không có nghĩa ta đọc được tài nguyên `.class` ban đầu.
- Nếu `getResource(path) != null`, ta mới biết lookup tìm thấy một tài nguyên ứng với tên đó. Chỉ riêng việc tìm thấy tài nguyên chưa chứng minh JVM đang chạy đúng những byte từ tài nguyên này.

Từ góc độ class loader, một tên lớp có thể được giải quyết qua delegation, module, JAR, thư mục, hoặc bytecode định nghĩa trực tiếp từ mảng byte. `findClass` của custom loader có thể dùng quy tắc đường dẫn này để tìm bytecode, nhưng có thể chọn chiến lược khác. Vì thế **quy tắc đặt tên là quy ước thông dụng, không phải cam kết tồn tại tài nguyên**.

## 6. Các trường hợp dễ nhầm

### 6.1. Sai đường dẫn do dùng canonical name

Nếu lấy `sample.Outer.Inner` rồi chạy `replace('.', '/') + ".class"`, chương trình sẽ tìm nhầm `sample/Outer/Inner.class` và thường nhận `null`. Resource đúng của member `Inner` là `sample/Outer$Inner.class`; hãy lấy từ `Outer.Inner.class.getName()` hoặc binary name thực tế. Đây là lỗi chọn đầu vào, không chứng minh ClassLoader không hỗ trợ lớp lồng nhau.

### 6.2. Kiểu nguyên thủy, mảng và descriptor

- `int.class.getName()` trả `int`, nhưng không có tài nguyên lớp riêng `int.class`.
- `String[].class.getName()` trả `[Ljava.lang.String;`; đây là cú pháp tên kiểu mảng của JVM, không thể biến `.` thành `/` rồi thêm `.class` để tìm class file của chính mảng.
- `int[].class.getName()` trả `[I`. JVM tạo kiểu mảng trong runtime; nó không tương ứng với `[I.class` trong JAR.

Nếu muốn class file của **kiểu thành phần** của mảng đối tượng, cần lấy `getComponentType()` (có thể nhiều lần với mảng đa chiều), rồi xét trường hợp component đó có tên và tài nguyên hay không. Không coi mảng là lớp có tệp bytecode riêng.

### 6.3. Lớp local, anonymous, hidden hoặc sinh trong runtime

Lớp local/anonymous thường có tên nhị phân chứa `$` cùng thành phần do compiler sinh, nhưng không nên tự tổng hợp tên. Hidden class và các lớp được custom loader định nghĩa từ byte có thể không có tài nguyên class tương ứng, dù tồn tại một đối tượng `Class<?>`. Với một số hidden class, `Class.getName()` còn chứa hậu tố đặc thù không phải binary name thông thường. Tránh giả định mọi `Class<?>` đều chuyển ngược thành một URL `.class`.

### 6.4. Classpath, JAR và Java Platform Module System

Khi biên dịch lớp `pkg.Example` vào `out`, đường dẫn vật lý thường là `out/pkg/Example.class`, còn tên resource vẫn chỉ là `pkg/Example.class`. Trong JAR, đó là **tên entry** như `pkg/Example.class`. Với multi-release JAR, loader/runtime có thể chọn phiên bản class khác nhau theo phiên bản Java; đừng giả định entry đang thấy trong archive đồng nhất với bytecode đã nạp.

Khi chạy với **named modules** (JPMS), việc tìm tài nguyên còn phụ thuộc module và cách loader thực hiện lookup; tài nguyên có thể không truy cập được qua cách gọi hiện tại. Không suy ra chắc chắn một file thiếu chỉ từ kết quả `null` nếu chưa xác minh module, loader và lookup path.

### 6.5. Tên không tin cậy

Nếu đầu vào là chuỗi bên ngoài như đường dẫn người dùng nhập, `replace('.', '/') + ".class"` **không** thay thế việc xác thực. Cần phân biệt binary name với path, áp dụng danh sách package/class được phép và tránh dùng đường dẫn nhận từ người dùng để tự động đọc, định nghĩa hoặc thực thi lớp. Truyền `Class<?>` đã được lựa chọn rõ ràng an toàn hơn việc ghép tùy ý một tên từ chuỗi lạ.

## 7. Checklist xử lý sự cố

Khi code không tìm được `.class`, kiểm tra theo thứ tự:

1. **Đầu vào có phải binary name không?** Dùng `type.getName()`; tránh `getCanonicalName()` với nested class.
2. **Dấu phân tách đúng chưa?** Chỉ đổi `.` của package thành `/`; giữ `$`; có đúng một hậu tố `.class`.
3. **Đang gọi API nào?** `ClassLoader.getResource` không dùng `/` đầu; `Class.getResource` dùng `/` đầu nếu muốn tên tuyệt đối.
4. **Classpath/module path và loader nào?** Xác nhận lớp/tài nguyên thực sự nằm trong thư mục, JAR hoặc module mà loader đang sử dụng.
5. **Đây có phải primitive, array, hidden hoặc generated class không?** Những kiểu đó có thể không có tệp tài nguyên độc lập.
6. **Có kiểm tra `null` và đóng stream không?** Lookup không thành công là tình huống hợp lệ; đừng gọi `openStream` hoặc `readAllBytes` khi chưa kiểm tra.

**Ghi nhớ:** Với lớp hoặc interface thông thường, `type.getName().replace('.', '/') + ".class"` là công thức suy ra **tên resource của bytecode theo quy ước**. Dùng đường dẫn này với API tra cứu phù hợp, đồng thời tách bạch việc hình thành tên với việc tài nguyên tồn tại hoặc bytecode đang chạy đến từ đâu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="names-and-bytecode-004">Cấu trúc ClassFile tối thiểu cần biết</a>

<details>
<summary>Xem chi tiết</summary>

# Cấu trúc ClassFile tối thiểu cần biết

**ClassFile** là cấu trúc nhị phân chuẩn dùng để biểu diễn định nghĩa một class hoặc interface cho Java Virtual Machine (JVM). Khi biên dịch mã Java, trình biên dịch thường tạo tệp có đuôi `.class`; class loader có thể cung cấp những byte tương ứng để JVM nạp, kiểm tra, liên kết rồi sử dụng định nghĩa kiểu.

Muốn hiểu class loader tìm và định nghĩa lớp như thế nào, cần biết trong một ClassFile có những gì: **phiên bản**, **constant pool**, **thông tin định danh và quan hệ kiểu**, **trường**, **phương thức** và **thuộc tính**. Không cần tự viết một JVM hay học thuộc toàn bộ opcode để đọc cấu trúc ở mức nền tảng.

> **Phân biệt:** “ClassFile” là định dạng dữ liệu do đặc tả JVM quy định. “Tệp `.class`” là một cách lưu dữ liệu định dạng ấy. Bytecode cũng có thể được cung cấp từ JAR, bộ nhớ hoặc nguồn khác; không bắt buộc JVM phải đọc một tệp rời có sẵn trên đĩa.

## 1. Nhìn tổng thể: ClassFile chứa gì?

Đặc tả JVM mô tả cấu trúc cơ bản như sau (ký pháp mô tả định dạng, **không phải code Java để chạy**):

~~~text
ClassFile {
    u4             magic;
    u2             minor_version;
    u2             major_version;
    u2             constant_pool_count;
    cp_info        constant_pool[constant_pool_count - 1];
    u2             access_flags;
    u2             this_class;
    u2             super_class;
    u2             interfaces_count;
    u2             interfaces[interfaces_count];
    u2             fields_count;
    field_info     fields[fields_count];
    u2             methods_count;
    method_info    methods[methods_count];
    u2             attributes_count;
    attribute_info attributes[attributes_count];
}
~~~

Các phần xuất hiện **đúng theo thứ tự trên** trong luồng byte của ClassFile. Một số phần có độ dài cố định, còn constant pool, danh sách interfaces/fields/methods và attributes có độ dài phụ thuộc dữ liệu.

| Thành phần | Ý nghĩa cốt lõi |
| --- | --- |
| `magic` | Dấu hiệu nhận biết định dạng ClassFile. |
| `minor_version`, `major_version` | Phiên bản định dạng bytecode mà JVM phải hỗ trợ. |
| `constant_pool_count`, `constant_pool` | Bảng chứa tên, descriptor, chuỗi/hằng và tham chiếu ký hiệu tới lớp, trường, phương thức. |
| `access_flags` | Cờ mô tả loại/thuộc tính của kiểu: class, interface, public, abstract, final… |
| `this_class` | Chỉ mục trỏ vào constant pool để chỉ ra tên nhị phân của kiểu hiện tại. |
| `super_class` | Chỉ mục chỉ superclass trực tiếp khi có; có trường hợp bằng 0. |
| `interfaces_count`, `interfaces` | Danh sách interface trực tiếp được lớp implements hoặc interface extends. |
| `fields_count`, `fields` | Metadata của các trường được khai báo trong chính kiểu. |
| `methods_count`, `methods` | Metadata của các phương thức được khai báo trong chính kiểu, gồm constructor/method khởi tạo tĩnh khi hiện diện. |
| `attributes_count`, `attributes` | Metadata mở rộng ở mức ClassFile như thông tin nguồn, record, nestmate, sealed class… |

**Điểm dễ nhầm:** Bảng trên chỉ trình bày các thành phần cấp cao nhất. Một `field_info`, `method_info` hay `Code` cũng có các thuộc tính con, chứ không phải tất cả đều nằm trực tiếp trong `ClassFile.attributes`.

### Kiểu `u1`, `u2`, `u4` là gì?

Đây là **số nguyên không dấu** dùng trong đặc tả cấu trúc nhị phân:

- `u1`: 1 byte, từ 0 đến 255.
- `u2`: 2 byte, từ 0 đến 65.535.
- `u4`: 4 byte, từ 0 đến 4.294.967.295.

Các đại lượng nhiều byte được lưu theo thứ tự **big-endian** (byte có trọng số lớn nhất trước). Ví dụ, `CA FE BA BE` là bốn byte nối tiếp, không phải chuỗi ký tự “CAFEBABE” lưu theo UTF-8.

## 2. Phần đầu file: Magic number và phiên bản

Bốn byte đầu tiên có giá trị cố định:

~~~text
Offset (hex)  Byte                 Giải thích
0x00          CA FE BA BE          magic = 0xCAFEBABE
0x04          00 00                minor_version = 0
0x06          00 3D                major_version = 61 (ví dụ class target Java 17)
0x08          ...                  constant_pool_count, rồi constant_pool
~~~

Hai cặp `00 00` và `00 3D` là ví dụ điển hình khi biên dịch với **`javac --release 17`** bằng bản JDK hỗ trợ tùy chọn đó; không phải mọi tệp ClassFile đều có cùng phiên bản.

Một vài phiên bản phổ biến:

| Mục tiêu bytecode | `major_version` |
| --- | ---: |
| Java 8 | 52 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

Các bản Java thường tăng major version khi phát hành. JVM không hỗ trợ major version của ClassFile có thể ném **`UnsupportedClassVersionError`** lúc nạp. Ví dụ, JVM chỉ hỗ trợ đến Java 17 sẽ không nạp được một class thực sự được biên dịch target Java 21 (major 65).

**Lưu ý:** Phiên bản ClassFile phản ánh **mục tiêu biên dịch**, không nhất thiết bản JDK mà người lập trình đã dùng để chạy `javac`. Khi xây dựng thư viện cho JVM cũ, `javac --release N` còn giới hạn API theo release đích, phù hợp hơn việc suy diễn chỉ từ tên tệp hoặc môi trường máy lập trình.

## 3. Constant pool: bảng liên kết bằng chỉ mục

**Constant pool** là phần trung tâm của ClassFile: nhiều vị trí trong file lưu một **chỉ mục** trỏ tới một mục ở bảng này thay vì lặp lại toàn bộ tên hoặc tham chiếu. Vì thế, cần hiểu nó như một bảng có kiểu bản ghi khác nhau; chữ “constant” không có nghĩa bảng chỉ chứa số hay chuỗi hằng.

Những loại mục thường gặp:

| Tag / loại mục | Dùng để biểu diễn |
| --- | --- |
| `CONSTANT_Utf8` | Chuỗi dùng cho tên, descriptor và văn bản metadata (mã hóa theo **modified UTF-8** của class file). |
| `CONSTANT_Class` | Tham chiếu tên kiểu, thường đi qua một mục `Utf8`. |
| `CONSTANT_NameAndType` | Cặp tên + descriptor của một trường/phương thức. |
| `CONSTANT_Fieldref` | Tham chiếu ký hiệu tới trường được khai báo trên một lớp/interface thích hợp. |
| `CONSTANT_Methodref` / `CONSTANT_InterfaceMethodref` | Tham chiếu ký hiệu tới phương thức. |
| `CONSTANT_String` | Hằng chuỗi dựa vào mục `Utf8`. |
| `CONSTANT_Integer`, `Float`, `Long`, `Double` | Giá trị số được mã hóa trong ClassFile. |
| `CONSTANT_MethodHandle`, `MethodType`, `InvokeDynamic`, `Dynamic` | Hỗ trợ method handle, lời gọi động và hằng động. |
| `CONSTANT_Module`, `Package` | Metadata module/package trong ClassFile phù hợp. |

Ví dụ với lời gọi `System.out.println(...)`, constant pool có thể chứa chuỗi/tham chiếu ký hiệu phục vụ việc tìm `java/lang/System`, trường `out`, kiểu `java/io/PrintStream` và phương thức `println`. **Các chỉ mục `#1`, `#2`… do trình biên dịch tạo và có thể khác nhau giữa các lần biên dịch/công cụ**; không nên học thuộc số thứ tự từ một bản `javap`.

### Quy tắc đánh chỉ mục dễ sai

- `constant_pool_count` là số **lớn hơn chỉ mục hợp lệ lớn nhất một đơn vị**; mục 0 không phải một `cp_info` bình thường có thể truy cập.
- Phần lớn mục chiếm một chỉ mục. `CONSTANT_Long` và `CONSTANT_Double` chiếm **hai vị trí chỉ mục liên tiếp**, nhưng vị trí thứ hai **không chứa một `cp_info` độc lập**. Vì vậy, không thể lặp đơn giản `count - 1` lần đọc bản ghi rồi cho rằng offset đúng với mọi ClassFile.
- Một bản ghi tham chiếu tên/kiểu thường chứa **chỉ mục trỏ tới bản ghi khác**, không nhúng nguyên văn tên ngay trong bản ghi tham chiếu.
- Constant pool không phải bảng object Java đã được khởi tạo. Các tham chiếu ký hiệu được JVM **resolve** theo quy tắc linking khi cần; nhiều mục vẫn chỉ là dữ liệu biểu diễn ký hiệu trong ClassFile.

Ví dụ chuỗi `"Hello"` trong mã nguồn có thể dẫn đến mục `CONSTANT_String` tham chiếu `CONSTANT_Utf8` chứa ký tự `Hello`. Nhưng string literal, field descriptor và generic metadata không phải cùng một thứ chỉ vì chúng đều xuất hiện trong constant pool.

## 4. Metadata nhận diện kiểu và quan hệ kế thừa

Sau constant pool là:

~~~text
access_flags
this_class
super_class
interfaces_count
interfaces[]
~~~

**`access_flags`** là tập cờ bit. Ở lớp thông thường, có thể gặp `ACC_PUBLIC`, `ACC_FINAL`, `ACC_SUPER`, `ACC_ABSTRACT`; interface có `ACC_INTERFACE` cùng những cờ phù hợp khác. `ACC_SUPER` là cờ có ý nghĩa lịch sử/liên quan ngữ nghĩa gọi phương thức; **không phải** “đường dẫn đến superclass”.

**`this_class`** trỏ tới một `CONSTANT_Class` trong constant pool, từ đó lấy **tên nội bộ** của lớp, ví dụ `com/example/Task`. Khi viết Java ta thường dùng tên nhị phân `com.example.Task`. Dấu `/` trong tên nội bộ khác dấu `.` trong code Java.

**`super_class`** thông thường trỏ đến `CONSTANT_Class` của lớp cha trực tiếp, kể cả `java/lang/Object` khi lớp không khai báo `extends`. Với chính `java/lang/Object`, giá trị là 0; ClassFile module (`ACC_MODULE`) cũng có ràng buộc đặc biệt đối với trường này.

**`interfaces`** chứa các chỉ mục `CONSTANT_Class` mô tả interface được khai báo trực tiếp. Đây không phải danh sách đệ quy tất cả interface mà kiểu nhận từ cha.

Ví dụ, đoạn Java:

~~~java
interface Runner {
    void run();
}

class Job implements Runner {
    @Override
    public void run() {}
}
~~~

sẽ dẫn đến các ClassFile riêng cho `Runner` và `Job`. Ở định nghĩa `Job`, metadata sẽ chỉ ra tên `Job`, superclass trực tiếp `java/lang/Object`, và interface trực tiếp `Runner`. Các tên nằm trong constant pool của **ClassFile tương ứng**, không phải một constant pool dùng chung cho mọi lớp trong cùng JAR.

## 5. Fields: lưu cấu trúc trường, không lưu sẵn object instance

Một phần tử `field_info` có cấu trúc khái quát:

~~~text
field_info {
    u2             access_flags;
    u2             name_index;
    u2             descriptor_index;
    u2             attributes_count;
    attribute_info attributes[attributes_count];
}
~~~

Ví dụ khai báo:

~~~java
private int count;
private static final int LIMIT = 10;
~~~

Metadata trường có tên `count`, `LIMIT`, cờ quyền truy cập, cờ `static`/`final` tương ứng và **descriptor**:

- `I` là `int`; `Z` là `boolean`; `J` là `long`; `D` là `double`.
- `Ljava/lang/String;` là tham chiếu tới `java.lang.String`.
- `[I` là mảng `int[]`; `[Ljava/lang/String;` là `String[]`.

Trường hằng phù hợp có thể có thuộc tính `ConstantValue`, chẳng hạn `static final int LIMIT = 10`. Điều này **không** có nghĩa mọi phép gán field được ghi thành byte dữ liệu object trong ClassFile. Giá trị của trường instance thuộc về **đối tượng khi chạy**; các bước khởi tạo và phép gán không hằng có thể được thực thi bằng bytecode.

Ngoài ra, **descriptor** là mô tả kiểu JVM dùng khi liên kết. Kiểu generic như `List<String>` không có dạng generic đầy đủ trong field descriptor; metadata generic nếu cần có thể nằm ở thuộc tính `Signature`. Vì vậy, trình xem ClassFile thường có cả descriptor kiểu xóa (`erasure`) và thuộc tính bổ sung.

## 6. Methods: tên, descriptor và bytecode thực thi

Một `method_info` cũng chứa `access_flags`, `name_index`, `descriptor_index` và tập thuộc tính. Những tên đặc biệt cần nhận ra:

- `<init>`: phương thức khởi tạo instance do JVM gọi khi thực hiện quá trình tạo instance; đây là tên đặc biệt ở mức ClassFile cho constructor.
- `<clinit>`: phương thức khởi tạo lớp/interface (nếu được sinh ra) chứa logic khởi tạo tĩnh cần thực thi.
- Những method thông thường: ví dụ `sum`, `main`, `run`.

Một **method descriptor** có dạng `(tham_số)kiểu_trả_về`:

| Khai báo Java | Descriptor JVM |
| --- | --- |
| `int sum(int value)` | `(I)I` |
| `void say(String text)` | `(Ljava/lang/String;)V` |
| `static void main(String[] args)` | `([Ljava/lang/String;)V` |
| `long total(int[] numbers, boolean active)` | `([IZ)J` |

Ký hiệu `V` biểu thị `void`. Descriptor ghi **kiểu tham số và kiểu trả về**, không ghi tên biến; generic type arguments không có trong descriptor thông thường.

### Thuộc tính `Code`: bytecode nằm ở đâu?

Phương thức Java có phần thân bytecode trong ClassFile thông thường dùng một thuộc tính **`Code`** nằm bên trong `method_info.attributes`. Cấu trúc rút gọn:

~~~text
method_info
  ├── access_flags / name_index / descriptor_index
  └── attributes[]
       └── Code
            ├── max_stack
            ├── max_locals
            ├── code_length + code[]      ← các opcode và operand
            ├── exception_table[]         ← vùng xử lý ngoại lệ
            └── attributes[]              ← ví dụ LineNumberTable, StackMapTable
~~~

- `max_stack`: độ sâu tối đa của **operand stack** cần thiết để thực thi method.
- `max_locals`: số slot tối đa của **local variables** dành cho method; `long`/`double` chiếm hai slot.
- `code[]`: dãy bytecode instruction và operand; ví dụ `iload`, `iadd`, `ireturn`, `invokevirtual`.
- `exception_table[]`: bảng ánh xạ vùng code và handler, dùng cả cho cấu trúc `try`/`catch`/`finally` sau biên dịch.
- Thuộc tính con giúp kiểm tra kiểu và debug, tùy trình biên dịch/tùy chọn biên dịch.

**Không phải method nào cũng có `Code`.** Method `abstract` hoặc `native` không có thân bytecode trong ClassFile theo cách này. Constructor và static initializer được biên dịch thành method có code khi tương ứng hiện diện.

## 7. Attributes: cơ chế mở rộng định dạng

`attribute_info` có dạng chung:

~~~text
attribute_info {
    u2 attribute_name_index;  // trỏ đến Utf8 trong constant pool
    u4 attribute_length;      // số byte dữ liệu của attribute
    u1 info[attribute_length];
}
~~~

**Tên attribute** được tra trong constant pool. Mỗi loại attribute lại định nghĩa cấu trúc riêng của `info[]`. Vì vậy, trình đọc ClassFile cần dựa vào tên và độ dài để giải mã; không được giả định mọi attribute đều chứa bytecode.

Các attribute có ý nghĩa học tập:

| Attribute | Vị trí thường gặp | Vai trò |
| --- | --- | --- |
| `Code` | Method | Bytecode, stack/locals, exception handlers và attribute lồng nhau. |
| `ConstantValue` | Field | Giá trị hằng của trường tĩnh phù hợp. |
| `Exceptions` | Method | Danh sách loại exception được khai báo trong `throws` (khác exception table xử lý tại runtime). |
| `SourceFile` | Class | Tên tệp nguồn để hỗ trợ công cụ. |
| `LineNumberTable`, `LocalVariableTable` | Bên trong `Code` | Metadata debug, có thể bị lược bỏ. |
| `StackMapTable` | Bên trong `Code` | Frame phục vụ kiểm tra type của bytecode theo quy tắc JVM. |
| `Signature` | Class/Field/Method | Metadata kiểu generic khi cần, bổ sung cho descriptor. |
| `RuntimeVisibleAnnotations` | Nhiều vị trí hợp lệ | Annotation có khả năng truy cập tại runtime theo quy tắc retention. |
| `BootstrapMethods` | Class | Dữ liệu bootstrap phục vụ `invokedynamic` và dynamic constants. |
| `Record`, `PermittedSubclasses` | Class | Metadata record và sealed hierarchy khi áp dụng. |

Cấu trúc ClassFile phát triển qua các phiên bản JVM; **không phải tệp nào cũng có tất cả attribute**. Một số attribute đặc biệt chỉ hợp lệ tại vị trí nhất định hoặc với phiên bản ClassFile tương ứng. Java reflection cũng không phơi bày nguyên vẹn mọi byte trong ClassFile.

## 8. Thực hành Java 17+: biên dịch và đọc bằng `javap`

Tạo tệp **`ClassFileLayout.java`**:

~~~java
public class ClassFileLayout {
    private static final int BASE = 7;
    private int total;

    public int sum(int amount) {
        total += amount + BASE;
        return total;
    }

    public static void main(String[] args) {
        ClassFileLayout example = new ClassFileLayout();
        System.out.println(example.sum(5));
    }
}
~~~

Dùng JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 ClassFileLayout.java
java ClassFileLayout
javap -v -p ClassFileLayout
~~~

Đầu ra của lệnh `java ClassFileLayout` **chính xác** là:

~~~text
12
~~~

Lệnh `javap -v -p` in nhiều thông tin phụ thuộc trình biên dịch/công cụ, vì vậy thay vì giả định mọi chỉ mục `#...` giống nhau, hãy **tìm các thành phần sau** trong output:

1. **Header** với `major version: 61` do đã dùng `--release 17`; tên class `ClassFileLayout` và superclass `java/lang/Object`.
2. **Constant pool** gồm tên class, tên trường, tên method, descriptor và tham chiếu cần thiết cho `System.out.println`, constructor… Các mục có thứ tự/đánh số do công cụ quyết định.
3. **Fields** của `BASE` và `total`. `BASE` có descriptor `I`, cờ `ACC_PRIVATE`, `ACC_STATIC`, `ACC_FINAL` và có thể xem `ConstantValue: int 7`.
4. **Methods** bao gồm constructor `<init>`, `sum` với descriptor `(I)I`, và `main` với `([Ljava/lang/String;)V`.
5. **Code** của `sum`: tải trạng thái/đối số, tính `amount + BASE`, ghi lại `total` rồi trả số nguyên. Do `BASE` là compile-time constant, bytecode có thể dùng trực tiếp giá trị `7`; không cần luôn đọc trường `BASE` bằng `getstatic`.
6. **Attributes**: các mục như `Code`, `LineNumberTable` hoặc `SourceFile` có thể xuất hiện theo tùy chọn biên dịch.

Nếu chỉ muốn xem bytecode từng method cho dễ đọc, chạy thêm:

~~~bash
javap -c -p ClassFileLayout
~~~

Lưu ý: `javap` là **công cụ giải mã và trình bày**, không phải JVM đang thực thi các opcode vừa in ra. Định dạng hiển thị (comment, chỉ mục, offset và chi tiết metadata) có thể thay đổi giữa công cụ/JDK.

### Đọc 10 byte đầu bằng Java (tùy chọn)

Nếu muốn tự xác nhận header bằng API chuẩn, tạo tệp **`ClassFileHeader.java`** trong cùng thư mục:

~~~java
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;

public class ClassFileHeader {
    public static void main(String[] args) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new FileInputStream("ClassFileLayout.class"))) {
            int magic = in.readInt();
            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            int poolCount = in.readUnsignedShort();

            System.out.printf("Magic: %08X%n", magic);
            System.out.println("Minor: " + minor);
            System.out.println("Major: " + major);
            System.out.println("Constant pool count: " + poolCount);
        }
    }
}
~~~

Chạy sau khi đã có `ClassFileLayout.class`:

~~~bash
javac --release 17 ClassFileHeader.java
java ClassFileHeader
~~~

**Ba dòng đầu** được xác định khi target là Java 17 và bytecode không dùng chế độ preview:

~~~text
Magic: CAFEBABE
Minor: 0
Major: 61
~~~

Chương trình còn in `Constant pool count: <một số nguyên dương>`; không cố định số này vì có thể khác nhau giữa các trình biên dịch. `DataInputStream` đọc dữ liệu nhiều byte theo big-endian, phù hợp cấu trúc header ClassFile. Đoạn này mới chỉ minh họa việc **đọc header**, chưa xác minh mọi tag, độ dài và chỉ mục trong file.

## 9. ClassFile liên hệ với class loader như thế nào?

Một luồng xử lý khái quát:

~~~text
.java --javac--> bytecode có cấu trúc ClassFile
                       │
                 tệp .class / JAR
                 hoặc bytecode từ nguồn khác
                       │
                       ▼
               class loader cung cấp byte
                       │
                       ▼
                 JVM định nghĩa class
                       │
                  verification
                preparation/resolution
                       │
                initialization khi cần
                       ▼
                    Sử dụng kiểu
~~~

Class loader giúp JVM **tìm dữ liệu để định nghĩa lớp**; còn JVM chịu trách nhiệm diễn giải đúng định dạng ClassFile, kiểm tra tính hợp lệ, liên kết và thực hiện khởi tạo theo các quy tắc của nó. Mức độ thực hiện sớm hay muộn của một số bước có thể khác nhau giữa JVM miễn giữ đúng ngữ nghĩa bắt buộc.

- `ClassNotFoundException`: thường xuất hiện khi mã gọi API tìm lớp theo tên nhưng loader không tìm được.
- `ClassFormatError`: dữ liệu biểu diễn class sai định dạng mà JVM yêu cầu.
- `UnsupportedClassVersionError`: major/minor version không được JVM đang dùng hỗ trợ.
- `VerifyError`: bytecode không đáp ứng quy tắc kiểm tra kiểu/cấu trúc tại giai đoạn verification.
- `NoClassDefFoundError`: việc JVM cần một định nghĩa lớp đã tham chiếu không thành công, có thể do thiếu dependency hoặc lớp đã khởi tạo thất bại trước đó.

Đọc **cause** và pha lỗi thực tế giúp phân biệt không tìm thấy tệp/JAR với định dạng ClassFile sai hoặc bytecode không hợp lệ.

## 10. Các hiểu nhầm phổ biến

**“ClassFile chỉ là danh sách opcode.”** Sai. Opcode nằm trong thuộc tính `Code` của method phù hợp. ClassFile còn có version, constant pool, metadata kiểu, fields, methods và nhiều attributes khác.

**“Constant pool là vùng chứa mọi giá trị runtime của chương trình.”** Sai. Đây là bảng biểu diễn ký hiệu/hằng trong định dạng class; dữ liệu instance và trạng thái thực thi không được lưu nguyên vẹn tại đây.

**“Đọc `major_version` là biết ứng dụng chạy được trên JVM ấy.”** Chưa đủ. Major version hợp lệ là điều kiện cần để nạp một class, nhưng bytecode vẫn có thể sai, dependency không có, API không tương thích, hoặc module/access không cho phép hoạt động mong muốn.

**“Một file ClassFile chứa tất cả class lồng và các lớp được tham chiếu.”** Sai. Class/interface thường có định nghĩa ClassFile riêng; class lồng xuất hiện dưới tên như `Outer$Inner`, và quan hệ giữa chúng có thể có metadata bổ sung. Tham chiếu tới lớp khác trong constant pool **không nhúng** bytecode của lớp ấy vào ClassFile đang xét.

**“Mọi method đều có `Code` và mọi attribute đều là debug.”** Sai. Method abstract/native không có Code theo cách bình thường; nhiều attribute mang ý nghĩa JVM như `StackMapTable`, `BootstrapMethods`, `NestHost` hoặc `PermittedSubclasses`.

**“Thứ tự các mục constant pool và bytecode luôn giống nhau nếu source giống nhau.”** Sai. Các bản trình biên dịch và cấu hình build có thể sinh biểu diễn khác mà vẫn tuân thủ đặc tả và giữ cùng hành vi cần thiết.

## 11. Tự kiểm tra

1. **Bốn byte đầu của ClassFile là gì?** — `CA FE BA BE`, tức magic `0xCAFEBABE`.
2. **Java 17 target thông thường có major version bao nhiêu?** — `61`.
3. **Vì sao `constant_pool_count` không nhất thiết bằng số mục cp_info được lưu cộng 1 theo cách đếm đơn giản?** — Vì các mục `Long`/`Double` chiếm hai slot chỉ mục và slot thứ hai không chứa cp_info độc lập.
4. **Một method `int sum(int x)` có descriptor nào?** — `(I)I`; tên tham số `x` không nằm trong descriptor.
5. **Bytecode của method ở đâu?** — Trong `Code` attribute thuộc `method_info` khi method có code; không phải ở `ClassFile.attributes` chung.
6. **Làm sao xem phiên bản ClassFile, constant pool và metadata bằng công cụ chuẩn?** — Biên dịch `javac --release 17`, rồi dùng `javap -v -p TenLop`.

## Ghi nhớ

Một **ClassFile** có phần đầu `CAFEBABE + version`, theo sau là **constant pool**, **flags + định danh/quan hệ kiểu**, **fields**, **methods** và **attributes**. Constant pool giúp tham chiếu ký hiệu bằng chỉ mục; `Code` attribute lưu bytecode của những method có thân phù hợp; còn class loader và JVM phối hợp đưa định nghĩa này vào môi trường thực thi. Nắm được cấu trúc trên và biết đọc `javap -v` là đủ để bắt đầu tìm hiểu class loading, linking, bytecode và các lỗi định dạng/phiên bản trong Java.

**Tài liệu chuẩn để tra cứu khi cần chi tiết từng tag/attribute:** *Java Virtual Machine Specification, Chapter 4 — The class File Format* (Java SE 17): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="names-and-bytecode-005">Phiên bản major, minor và giới hạn JDK hỗ trợ bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# Phiên bản major, minor và giới hạn JDK hỗ trợ bytecode

## 1. Vì sao phiên bản bytecode quan trọng?

Khi biên dịch Java, `javac` chuyển mã nguồn thành **class file** (thường có đuôi `.class`). JVM/ClassLoader đọc định dạng nhị phân của class file để nạp, kiểm tra và sử dụng lớp. Mỗi class file tự khai báo **phiên bản định dạng** dưới dạng `major.minor`; đây là thông tin để JVM quyết định nó có hiểu class file đó hay không.

Cần tách biệt ba khái niệm:

- **Phiên bản JDK dùng để biên dịch:** phiên bản công cụ `javac` đang chạy, chẳng hạn JDK 21.
- **Phiên bản class file:** cặp số `major_version` và `minor_version` thực sự ghi trong từng `.class`, chẳng hạn `61.0` (Java 17).
- **Phiên bản JVM/JRE chạy chương trình:** môi trường phải nạp class file, chẳng hạn Java 17 hoặc Java 21.

Ví dụ, dùng `javac` của **JDK 21** với `--release 17` thường tạo class file **61.0**, có thể được JVM Java 17 nạp nếu các class và thư viện khác cũng tương thích. Nếu biên dịch mặc định trên JDK 21, class file thông thường có phiên bản **65.0** và JVM Java 17 không hiểu nó.

**Quy tắc cốt lõi:** JVM phải hỗ trợ phiên bản *của từng class file được nạp*, không chỉ phiên bản JDK trên máy lập trình. ClassLoader tùy chỉnh không tự làm JVM hiểu định dạng mới hơn.

## 2. Vị trí major và minor trong cấu trúc `.class`

Đầu mỗi class file chuẩn có cấu trúc:

```text
Offset (byte)    Kích thước     Trường           Ví dụ cho class Java 17
0                4 byte         magic            CA FE BA BE
4                2 byte         minor_version    00 00  (= 0)
6                2 byte         major_version    00 3D  (= 61)
8                2 byte         constant_pool_count
...              ...            các cấu trúc còn lại
```

Theo Java Virtual Machine Specification (JVMS), các số `u2` là **số nguyên không dấu 16-bit** và được ghi theo thứ tự **big-endian**. Vì thế 8 byte mở đầu `CA FE BA BE 00 00 00 3D` tương ứng magic hợp lệ, minor 0 và major 61. Trường `minor_version` đứng **trước** `major_version`, dù khi nói phiên bản ta viết **61.0** (major trước minor).

- `magic` xác nhận dấu hiệu class file Java; có magic đúng vẫn chưa đảm bảo phần còn lại hợp lệ.
- `major` xác định thế hệ định dạng class file JVM cần hỗ trợ.
- `minor` bổ sung quy tắc tương thích, đặc biệt có ý nghĩa với **preview features** từ Java 12.
- Bytecode instruction, constant pool, attribute và các cấu trúc mới có thể đòi hỏi JVM của phiên bản Java tương ứng. Không thể chỉ sửa 2 byte major để biến một class mới thành class cũ hợp lệ.

## 3. Bảng quy đổi Java ↔ major version

Bảng dưới đây cho biết **major được giới thiệu** ở mỗi phiên bản Java; phiên bản Java cũ 1.x dùng cách đặt tên khác từ Java 5 trở đi.

| Java/JDK | Major | Java/JDK | Major | Java/JDK | Major |
|---|---:|---|---:|---|---:|
| JDK 1.0.2, 1.1 | 45 | Java 9 | 53 | Java 18 | 62 |
| JDK 1.2 | 46 | Java 10 | 54 | Java 19 | 63 |
| JDK 1.3 | 47 | Java 11 | 55 | Java 20 | 64 |
| JDK 1.4 | 48 | Java 12 | 56 | Java 21 | 65 |
| Java 5 | 49 | Java 13 | 57 | Java 22 | 66 |
| Java 6 | 50 | Java 14 | 58 | Java 23 | 67 |
| Java 7 | 51 | Java 15 | 59 | Java 24 | 68 |
| Java 8 | 52 | Java 16 | 60 | Java 25 | 69 |
| Java 17 | 61 | Java 26 | 70 | — | — |

Đối với **Java 5 trở lên**, có thể ghi nhớ công thức:

```text
major = số phiên bản Java + 44
Java 8  → 52
Java 17 → 61
Java 21 → 65
Java 26 → 70
```

Công thức này không áp dụng máy móc cho các tên phiên bản lịch sử `1.0`–`1.4`: chẳng hạn cả JDK 1.0.2 và 1.1 gắn với major 45. Đây cũng không phải cách tính minor.

Tính đến Java SE 26, JVMS quy định JVM phù hợp Java SE 26 hỗ trợ **major 45 đến 70**. Mỗi phiên bản JVM có giới hạn tối đa của chính nó: Java 8 là 52, Java 11 là 55, Java 17 là 61, Java 21 là 65, Java 25 là 69, Java 26 là 70. Các bản JVM chuẩn cũng hỗ trợ nhiều major cũ (theo bảng hỗ trợ của JVMS), nhưng khả năng *nạp được class file* không bảo đảm mọi thư viện/API cũ hoặc mọi hành vi chương trình còn tương thích.

## 4. Minor version và preview bytecode

### 4.1. Class file thông thường

Trong các bản Java hiện đại, class file biên dịch không phụ thuộc preview thường có `minor=0`, ví dụ `52.0`, `61.0`, `65.0`. Tuy nhiên, không nên khẳng định rằng **mọi** class file Java trong lịch sử đều có minor 0:

- Với major **45–55** (đến Java 11), JVMS cho phép minor từ 0 đến 65535. Lịch sử JDK 1.0.2 hỗ trợ `45.0`–`45.3`; các phiên bản đầu có quy tắc tương thích riêng.
- Với major **56 trở lên** (từ Java 12), minor **chỉ được là 0 hoặc 65535**. Một minor khác, chẳng hạn `61.1`, không hợp lệ theo quy tắc này.

### 4.2. Giá trị 65535 (0xFFFF) dành cho preview

Từ Java 12, class file có `major=M` (M ≥ 56) và `minor=65535` biểu thị rằng nó **phụ thuộc các preview features của đúng bản Java gắn với M**. Ví dụ:

- `61.65535` gắn với preview của **Java 17**.
- `65.65535` gắn với preview của **Java 21**.
- `70.65535` gắn với preview của **Java 26**.

Để nạp class file preview, JVM phải **đúng phiên bản Java đó** và preview phải được bật ở runtime (`--enable-preview`). JVM Java 21 không được nạp `61.65535` của Java 17, **kể cả khi bật** `--enable-preview`. JVM Java 17 cũng không được nạp `65.65535`. Đây là khác biệt lớn so với `61.0` thông thường, vốn có thể chạy trên JVM mới hơn khi các điều kiện khác đáp ứng.

Lệnh mẫu khi biên dịch **mã thực sự dùng preview feature mà JDK 21 cung cấp** (tệp và cú pháp phải phù hợp Java 21):

```bash
javac --enable-preview --release 21 PreviewDemo.java
java --enable-preview PreviewDemo
```

Khi muốn dùng `--release 21`, bản `javac` phải hỗ trợ target đó. Đây là ví dụ cách gọi lệnh, chưa phải chương trình chạy độc lập vì nội dung `PreviewDemo.java` phụ thuộc preview feature bạn chọn. Không phải mọi lần thêm `--enable-preview` đều có nghĩa mọi class trong kết quả đều có minor 65535: cần kiểm tra class file thực tế.

**Cảnh báo tương thích:** Preview API và preview language feature có thể thay đổi hoặc bị loại khỏi phiên bản Java sau. Không dùng quy tắc "JVM mới hơn chạy mọi class cũ" cho class file preview.

## 5. Kiểm tra major/minor bằng công cụ JDK

### 5.1. Dùng `javap -verbose`

Tạo tệp `VersionDemo.java`:

```java
public final class VersionDemo {
    public static void main(String[] args) {
        System.out.println("Bytecode target demo");
    }
}
```

Trên **JDK 17 hoặc mới hơn** có hỗ trợ `--release 17`, chạy:

```bash
javac --release 17 VersionDemo.java
javap -verbose VersionDemo
java VersionDemo
```

Trong phần đầu kết quả `javap -verbose`, hãy tìm hai dòng:

```text
  minor version: 0
  major version: 61
```

Đây là **trích đoạn** của kết quả `javap`; phần constant pool và các metadata khác đã lược bỏ. Chương trình Java cho stdout đúng một dòng:

```text
Bytecode target demo
```

Có thể dùng `javap -verbose -classpath out ten.goipackage.VersionDemo` khi `.class` nằm trong thư mục `out` và có `package`. `javap` đọc class file để xem metadata; nó không phải cách xác minh toàn bộ runtime dependency đều tương thích.

### 5.2. Đọc trực tiếp 8 byte đầu bằng Java 17+

Một chương trình nhỏ cho thấy major/minor thực sự nằm trong file, không cần dựa vào tên JDK:

Tạo `ClassVersionReader.java`:

```java
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClassVersionReader {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: java ClassVersionReader <file.class>");
        }

        try (DataInputStream in =
                     new DataInputStream(Files.newInputStream(Path.of(args[0])))) {
            int magic = in.readInt();
            if (magic != 0xCAFEBABE) {
                throw new IOException("Not a Java class file");
            }

            int minor = in.readUnsignedShort();
            int major = in.readUnsignedShort();
            System.out.printf("major=%d, minor=%d%n", major, minor);
        }
    }
}
```

Trong cùng thư mục với `VersionDemo.class` của mục 5.1, chạy:

```bash
javac --release 17 ClassVersionReader.java
java ClassVersionReader VersionDemo.class
```

Stdout chính xác khi `VersionDemo.class` được tạo bằng lệnh `javac --release 17` ở trên và không dùng preview:

```text
major=61, minor=0
```

`DataInputStream.readInt()` đọc magic dạng 4 byte, `readUnsignedShort()` đọc từng `u2` big-endian thành số nguyên 0–65535. Chương trình trên chỉ đọc **header**, không kiểm tra tính hợp lệ của constant pool, method, verifier hay khả năng chạy của class.

### 5.3. Đọc lỗi và xác nhận công cụ đang dùng

Kiểm tra cả compiler và JVM trên môi trường thực tế:

```bash
javac -version
java -version
```

Hai lệnh có thể trỏ đến **hai bản JDK/JRE khác nhau**, đặc biệt trong IDE, CI/CD, Docker, IDE Gradle daemon hay cấu hình `PATH`. Thấy compiler là JDK 21 **không có nghĩa** bytecode chắc chắn là major 65: hãy kiểm tra `--release` và `javap`. Tương tự, chọn Java 17 trong IDE chưa chắc mọi tác vụ build hoặc launcher đang chạy JVM 17.

## 6. JDK hỗ trợ bytecode đến đâu?

Quy tắc đơn giản cho class file **không preview**: JVM Java N chỉ nạp được class file có major nằm trong tập hỗ trợ của N. Nhìn vào **major** để xét giới hạn định dạng, sau đó phải xét thêm **minor**, cấu trúc file, các thư viện và API lúc chạy.

| Class file | JVM Java 8 | JVM Java 17 | JVM Java 21 | JVM Java 26 |
|---|---|---|---|---|
| `52.0` (Java 8) | Hỗ trợ | Hỗ trợ | Hỗ trợ | Hỗ trợ |
| `61.0` (Java 17) | Không | Hỗ trợ | Hỗ trợ | Hỗ trợ |
| `65.0` (Java 21) | Không | Không | Hỗ trợ | Hỗ trợ |
| `70.0` (Java 26) | Không | Không | Không | Hỗ trợ |
| `61.65535` (preview Java 17) | Không | Chỉ khi bật preview | Không | Không |

"Hỗ trợ" trong bảng là **hỗ trợ phiên bản class file** theo JVM chuẩn; không phải lời hứa ứng dụng chạy thành công. Ví dụ, một class 52.0 gọi thư viện không hiện diện lúc chạy vẫn có thể gặp `ClassNotFoundException`, `NoClassDefFoundError` hay `NoSuchMethodError`. Cấu trúc file bị lỗi cũng có thể dẫn đến `ClassFormatError` hoặc lỗi verification.

Một JVM **không tự chuyển ngược** định dạng mới thành cũ. Khi cùng quá trình chạy có 100 class file, chỉ cần một class được nạp có major vượt mức JVM hỗ trợ là ứng dụng có thể lỗi ở thời điểm nạp class đó; một số lỗi vì thế không xuất hiện ngay lúc khởi động.

## 7. Tạo bytecode phù hợp JVM đích

### 7.1. Ưu tiên `--release`

Nếu triển khai trên Java 17 nhưng máy build dùng JDK 21, nên chọn:

```bash
javac --release 17 VersionDemo.java
```

`--release 17` phối hợp **ngôn ngữ nguồn**, **target class file** và **API chuẩn được expose cho Java 17**. Nó giúp tránh tình huống code sinh major 61 nhưng gọi nhầm API chuẩn chỉ có từ Java 21. Điều này vẫn không thay thế việc kiểm tra tương thích các thư viện bên thứ ba và chạy test thực tế trên Java 17.

Ví dụ với JDK 21:

```bash
javac VersionDemo.java
```

Nếu không có cấu hình target khác, class thường là **65.0**. Còn:

```bash
javac --release 17 VersionDemo.java
```

tạo **61.0**. Bạn có thể đối chiếu bằng `javap -verbose`.

### 7.2. Phân biệt `--source`, `--target`, `--release`

- `--source 17`: áp dụng mức cú pháp/ngôn ngữ Java 17 khi biên dịch.
- `--target 17`: yêu cầu phiên bản class file đích tương ứng Java 17.
- `--release 17`: đồng bộ source, target và tập API nền tảng Java 17 mà compiler cho phép sử dụng; thường là lựa chọn an toàn nhất cho cross-compilation.

**Chỉ** đặt `--target 17` không chứng minh ứng dụng sẽ chạy trên Java 17: mã có thể tham chiếu API chuẩn xuất hiện ở bản Java mới hơn nếu không khống chế API lúc compile; thư viện phụ thuộc cũng có thể có class version cao hơn. `--release` không tự biên dịch lại thư viện đã đóng gói hoặc sửa lỗi tương thích hành vi giữa hai JVM.

Với Maven/Gradle, cấu hình compiler release tương đương cần áp dụng cho module build thích hợp; sau build hãy xác nhận artifact `.class` thật, đồng thời chạy integration test bằng **JVM thấp nhất được hỗ trợ**.

## 8. Khi ClassLoader nạp bytecode không tương thích

Quá trình nạp class có thể nhìn khái quát:

```text
ClassLoader được yêu cầu nạp tên lớp
    ↓
Tìm / lấy bytes của class file
    ↓
JVM đọc header (magic, minor, major) và cấu trúc class
    ↓
Kiểm tra định dạng và giới hạn JVM
    ├─ Không hỗ trợ phiên bản → UnsupportedClassVersionError
    ├─ Cấu trúc không hợp lệ → ClassFormatError / lỗi liên quan
    └─ Hợp lệ → tạo lớp; liên kết, verification và khởi tạo theo thời điểm tương ứng
```

Đây là sơ đồ *khái niệm*; trình tự và thời điểm load/link/verify/init cụ thể phụ thuộc tình huống. Một custom ClassLoader có thể đọc bytes từ tệp, mạng, JAR hoặc dữ liệu sinh động và gọi `defineClass(...)`, nhưng cuối cùng JVM vẫn kiểm tra định dạng theo giới hạn phiên bản hiện hành.

**Ví dụ lỗi:** class biên dịch bằng JDK 21 theo mặc định (major 65), chạy trên JVM Java 17 (tối đa major 61) sẽ không nạp được. Thông báo thường nhắc `UnsupportedClassVersionError`, `class file version 65.0` và JVM chỉ nhận đến `61.0`; lời văn chính xác khác giữa phiên bản hoặc nhà cung cấp JVM. Cách xử lý là **chạy trên JVM phù hợp** hoặc **biên dịch lại class và mọi dependency liên quan cho Java 17**, nếu mã/API cho phép. Không sửa thủ công header để "hạ version".

Phân biệt nhanh các họ lỗi thường nhầm:

| Hiện tượng | Điểm cần kiểm tra |
|---|---|
| `UnsupportedClassVersionError` | Major/minor của `.class` so với giới hạn JVM; preview có đúng Java release và được bật không |
| `ClassFormatError` | Dữ liệu class file lỗi cấu trúc hoặc không đúng định dạng; `UnsupportedClassVersionError` là một dạng cụ thể trong họ này |
| `VerifyError` | Bytecode vi phạm quy tắc verifier; không nhất thiết do class file quá mới |
| `ClassNotFoundException` / `NoClassDefFoundError` | Thiếu lớp trên classpath/module path, lỗi khi liên kết/nạp hoặc lỗi trước đó; không quy ngay cho version |
| `NoSuchMethodError` | Lệch phiên bản API/thư viện ở runtime dù major của class có thể hợp lệ |

### Tình huống thực tế: Java 17 service dùng thư viện Java 21

Giả sử service được compile `--release 17`, nhưng một dependency JAR chứa `SomeLibrary.class` có major 65. Build *có thể* không thể dùng dependency đó tùy cách biên dịch; nếu artifact vẫn được đóng gói và JVM Java 17 phải nạp class 65.0, runtime sẽ lỗi version. Kiểm tra các class bên trong JAR hoặc dùng công cụ dependency analysis/build compatibility; chỉ kiểm tra entry-point class là chưa đủ.

Một **Multi-Release JAR** có thể mang class khác nhau trong `META-INF/versions/<N>/` và chọn biến thể thích hợp theo JVM đang chạy khi JAR được cấu hình multi-release đúng cách. Điều này giúp thư viện cung cấp nhiều mức tương thích, nhưng không làm một JVM cũ tự hiểu phiên bản class file mới.

## 9. Quy trình chẩn đoán khi gặp lỗi bytecode version

1. Ghi nhận **JVM thực tế** của process lỗi (terminal, service, test runner, Docker/CI); chạy `java -version` đúng môi trường.
2. Tìm **tên class bị lỗi** trong stack trace; xác định class đó nằm trong module của bạn hay JAR phụ thuộc.
3. Xem `major/minor` của **chính class file đó** bằng `javap -verbose` hoặc chương trình đọc header; không suy luận chỉ từ `pom.xml`/Gradle target.
4. So phiên bản class với giới hạn JVM và kiểm tra `minor=65535` cho preview.
5. Nếu class quá mới, nâng runtime theo yêu cầu hệ thống hoặc build/publish artifact theo target phù hợp; kiểm tra cả dependency transitives.
6. Kiểm tra khả năng dùng API trên runtime thấp nhất và chạy smoke/integration tests trên đúng JVM deploy.

**Lỗi phổ biến cần tránh:** nhầm version JDK với version bytecode; cho rằng minor không quan trọng; nghĩ `major thấp` bảo đảm chạy được mọi API; tin rằng bật `--enable-preview` ở JVM mới sẽ chạy class preview của JVM cũ; sửa hex của major thay cho biên dịch lại; chỉ nâng JDK build mà quên JVM production.

## 10. Câu hỏi tự kiểm tra

1. 8 byte đầu `CA FE BA BE 00 00 00 41` chỉ ra phiên bản nào? **Đáp án:** major `0x41=65`, minor 0, tức class file Java 21 thông thường.
2. JVM Java 17 có nạp được class `65.0` không? **Không**; Java 17 hỗ trợ tối đa major 61.
3. JVM Java 21 với `--enable-preview` có chạy được class `61.65535` không? **Không**; preview class Java 17 chỉ được nạp trên Java 17 khi preview được bật.
4. JDK 21 có tạo được class `61.0` không? **Có**, chẳng hạn với `javac --release 17` cho mã phù hợp.
5. Class `61.0` chạy được trên JVM Java 21 có chứng minh app chắc chắn hoạt động không? **Không**; còn phải đáp ứng dependency, API, liên kết và hành vi runtime.
6. Tại sao `minor=1` của class `61.1` là bất hợp lệ? **Vì** với major từ 56 trở lên, minor chỉ được phép 0 hoặc 65535.

## Tài liệu chuẩn để tra cứu

- [Java Virtual Machine Specification, Java SE 26 — §4.1: The ClassFile Structure](https://docs.oracle.com/javase/specs/jvms/se26/html/jvms-4.html): định dạng header, bảng major, tập hỗ trợ và quy tắc minor/preview.
- [JVMS Java SE 26 — §1.5: Preview Features](https://docs.oracle.com/javase/specs/jvms/se26/html/jvms-1.html): điều kiện bật và sự ràng buộc phiên bản preview.
- [Java `javac` tool reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html): ý nghĩa `--release`, `--source`, `--target`.
- [Java `javap` tool reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html): cách xem chi tiết class file.

Một class file **thông thường** được thiết kế để JVM cùng phiên bản hoặc phiên bản mới hơn hỗ trợ; một class file **preview** bị ràng buộc vào đúng release. Trong cả hai trường hợp, phiên bản class file chỉ là **điều kiện cần** của khả năng nạp và chạy mã Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="names-and-bytecode-006">Constant pool và tham chiếu ký hiệu tới kiểu, field, method</a>

<details>
<summary>Xem chi tiết</summary>

# Constant pool và tham chiếu ký hiệu tới kiểu, field, method trong Java

Một file `.class` không lưu trực tiếp con trỏ tới mọi lớp, trường (field) và phương thức (method) mà chương trình sử dụng. Thay vào đó, nó chứa **constant pool** — bảng các hằng và bản ghi mô tả tham chiếu bằng **tên + kiểu/chữ ký**. Trong quá trình liên kết (linking) và thực thi, JVM dùng các mô tả này để tìm thực thể tương ứng. Đây là cầu nối giữa bytecode, cơ chế nạp lớp và hành vi gọi phương thức ở runtime.

Bài này thuộc chủ đề **Tên và bytecode** trong Java core – classloader. Mục tiêu là đọc được một tham chiếu trong constant pool, biết vì sao cùng tên lớp chưa chắc là cùng kiểu, phân biệt *resolution* với *dispatch* và chẩn đoán lỗi liên kết sau khi thay đổi thư viện.

## 1. Constant pool nằm ở đâu, chứa gì?

Trong cấu trúc `ClassFile`, phần đầu gồm `magic`, phiên bản class file, rồi đến `constant_pool_count` và mảng `constant_pool`. Mỗi phần tử có một `tag` cho biết loại thông tin. Chỉ số trong constant pool thường bắt đầu từ **#1**; `#0` không phải một phần tử thông thường và chỉ được dùng như giá trị đặc biệt tại một số vị trí do định dạng class file quy định.

- **Compile time:** `javac` ghi vào constant pool các chuỗi, giá trị số và mô tả biểu tượng của lớp/field/method mà bytecode cần. Không nhất thiết mỗi biểu thức nguồn sinh ra một mục riêng; compiler có thể tái sử dụng mục.
- **Load time:** JVM đọc định dạng class file và tạo biểu diễn runtime, gồm *run-time constant pool* của từng class hoặc interface.
- **Link time / runtime:** tham chiếu ký hiệu có thể được giải quyết thành thực thể tương ứng. JVM được phép resolve sớm hoặc trì hoãn đến lúc cần, miễn hành vi lỗi tuân thủ quy định JVM.
- **Không đồng nghĩa:** constant pool trong `.class`, *run-time constant pool* và *String intern pool* là những khái niệm khác nhau. Ví dụ `"hello"` có thể dẫn tới một `CONSTANT_String` trong class file, còn đối tượng `String` được JVM quản lý theo cơ chế riêng.

`constant_pool_count` là số lượng mục cộng một. `CONSTANT_Long` và `CONSTANT_Double` chiếm hai vị trí chỉ số trong bảng theo định dạng class file (vị trí thứ hai không được tham chiếu như mục độc lập), một chi tiết dễ gây nhầm khi tự phân tích byte.

### Các loại mục liên quan trực tiếp

| Tag | Mục đích | Ví dụ ý nghĩa |
| --- | --- | --- |
| `CONSTANT_Utf8` | Lưu văn bản mã hóa theo *modified UTF-8* của JVM | `java/lang/String`, `length`, `()I` |
| `CONSTANT_Class` | Biểu diễn tên nội bộ của class/interface hoặc descriptor kiểu mảng | `java/lang/String` |
| `CONSTANT_NameAndType` | Cặp tên thành viên và descriptor | `count:I`, `run:()V` |
| `CONSTANT_Fieldref` | Lớp chủ khai báo trong tham chiếu + `NameAndType` của field | `pkg/Box.value:I` |
| `CONSTANT_Methodref` | Lớp + `NameAndType` của method theo quy tắc class-method | `pkg/Box.get:()I` |
| `CONSTANT_InterfaceMethodref` | Interface + `NameAndType` cho tham chiếu method của interface | `java/util/List.size:()I` |
| `CONSTANT_String` | Tham chiếu tới nội dung chuỗi ký tự | `"hello"` |
| `CONSTANT_Integer/Float/Long/Double` | Hằng số nguyên/thực ở dạng phù hợp | `42`, `3.14f` |
| `CONSTANT_MethodHandle`, `CONSTANT_MethodType` | Mô tả method handle và kiểu phương thức | Dùng với `invokedynamic`/bootstrap |
| `CONSTANT_Dynamic`, `CONSTANT_InvokeDynamic` | Hằng và điểm gọi được bootstrap liên kết động | `condy`, lambda, chuỗi nối |
| `CONSTANT_Module`, `CONSTANT_Package` | Tên module/package phục vụ cấu trúc module | `module-info.class` |

Không phải mọi hằng ở ngôn ngữ Java đều bắt buộc có mục riêng trong constant pool. Chẳng hạn, compiler có thể phát lệnh `iconst_1` hoặc `bipush` thay vì tạo `CONSTANT_Integer` cho một số nguyên nhỏ.

## 2. Tham chiếu ký hiệu được ghép như thế nào?

Một `Fieldref` hoặc `Methodref` không chứa địa chỉ bộ nhớ. Nó tham chiếu gián tiếp qua nhiều mục trong bảng:

```text
CONSTANT_Fieldref / CONSTANT_Methodref / CONSTANT_InterfaceMethodref
    ├── class_index ──────> CONSTANT_Class
    │                          └── name_index ──> CONSTANT_Utf8: tên kiểu
    └── name_and_type_index -> CONSTANT_NameAndType
                                   ├── name_index ──────> CONSTANT_Utf8: tên thành viên
                                   └── descriptor_index -> CONSTANT_Utf8: descriptor
```

Ví dụ bytecode cần truy cập `pkg/Counter.total:I`:

- `pkg/Counter` là **internal name** của lớp, dùng dấu `/` thay dấu `.` trong tên nhị phân quen thuộc `pkg.Counter`.
- `total` là tên field.
- `I` là **field descriptor** chỉ kiểu `int`.

Một lời gọi `pkg/Counter.increment:(I)V` nói rằng phương thức tên `increment` nhận một `int` và trả `void`. Descriptor là một phần của tham chiếu ký hiệu, không chỉ là chú thích.

### Đọc descriptor đủ để lần theo tham chiếu

Descriptor là phần chữ ký kiểu đi cùng tên thành viên trong constant pool. Một số mẫu cần nhận ra ngay khi xem `javap`:

| Mẫu | Ý nghĩa |
| --- | --- |
| `I`, `J`, `Z` | `int`, `long`, `boolean` |
| `Ljava/lang/String;` | Tham chiếu `java.lang.String` |
| `[I`, `[[Ljava/lang/String;` | `int[]`, `String[][]` |
| `(I)V` | Method nhận `int`, trả `void` |
| `(Ljava/lang/String;)I` | Method nhận `String`, trả `int` |

**Field descriptor** mô tả một kiểu; **method descriptor** ghép các kiểu tham số giữa `(` và `)`, rồi thêm kiểu trả về. `V` chỉ hợp lệ làm kiểu trả về `void`. Descriptor không chứa tên biến, tên tham số hay generic type argument: `List<String>` sau erasure thường có field descriptor `Ljava/util/List;`. Bài chuyên về descriptor của chương này giải thích đầy đủ các mã primitive, mảng nhiều chiều và quy tắc parse.

Constructor có tên bytecode `<init>`, còn bộ khởi tạo tĩnh có tên `<clinit>`. Cả hai có ngữ nghĩa riêng, nên không áp dụng máy móc cách gọi method thông thường.

## 3. Từ tham chiếu trong bytecode đến lớp, field, method thật

Khi bytecode gặp một lệnh cần class/field/method, JVM dùng mục constant pool được lệnh tham chiếu. Quy trình tư duy hữu ích:

1. **Đọc mục constant pool:** xác định tên kiểu, tên thành viên và descriptor.
2. **Resolve kiểu chủ:** tìm hoặc nạp class/interface được tham chiếu trong ngữ cảnh class loader thích hợp; kiểm tra định dạng, tính hợp lệ và khả năng truy cập theo quy tắc JVM.
3. **Resolve field hoặc method:** tìm thành viên phù hợp theo quy tắc tra cứu trong kiểu, superclass/superinterface thích hợp; kiểm tra kiểu thành viên, tính truy cập và các ràng buộc tương thích.
4. **Thực hiện lệnh:** đọc/ghi field, tạo object, kiểm tra kiểu, hoặc chọn method body để gọi. Với lời gọi ảo, bước chọn implementation khi chạy là phần riêng.

Cần phân biệt các mốc vòng đời của lớp:

- **Loading:** tạo class/interface từ định dạng nhị phân với một defining loader.
- **Linking:** gồm **verification** (kiểm chứng), **preparation** (chuẩn bị vùng lưu trữ static và giá trị mặc định) và **resolution** (giải quyết các symbolic reference).
- **Initialization:** thực hiện khởi tạo class/interface theo điều kiện JVM, bao gồm các static initializer tương ứng. *Resolution không đồng nghĩa initialization.* Một `.class` literal thường không chủ động khởi tạo class được nhắc đến.

Thông thường JVM thực hiện các bước cần thiết theo yêu cầu của lệnh và ngữ cảnh. Không nên dạy rằng toàn bộ constant pool được resolve đúng một lần ngay khi load, cũng không nên khẳng định chỉ có lazy resolution: đặc tả cho phép chiến lược sớm hoặc muộn trong giới hạn hành vi quan sát được.

### Tại sao tên lớp còn phụ thuộc class loader?

Trong JVM, danh tính của một class/interface được xác định bởi **tên nhị phân + defining class loader**. Hai loader độc lập có thể định nghĩa hai class đều mang tên `com.example.Plugin` nhưng chúng là **hai kiểu khác nhau**. Một `CONSTANT_Class` chỉ lưu tên biểu tượng; việc resolve đặt tên ấy vào ngữ cảnh loading cụ thể.

Điều này quan trọng trong plugin, application server, hot deployment và framework dùng loader tùy biến. Trình tự thường là loader của lớp đang tham chiếu yêu cầu load tên phụ thuộc; quá trình ủy quyền (delegation) có thể đưa tới loader cha hoặc tự định nghĩa, tùy chính sách loader. JVM còn áp dụng **loading constraints** khi cần để bảo đảm các kiểu xuất hiện trong descriptor tương thích giữa các loader. Vì vậy không thể suy từ chuỗi tên bằng nhau rằng hai tham chiếu nhất định chỉ tới cùng một `Class`.

Một lỗi `ClassCastException` giữa hai tên class trông giống hệt nhau có thể do khác defining loader; đó là vấn đề danh tính kiểu ở runtime, không phải do `CONSTANT_Utf8` hỏng.

## 4. Đọc bằng `javap`: ví dụ Java 17+ chạy được

Tạo **một file duy nhất** tên `Main.java`:

```java
public class Main {
    static class Parent {
        public String label = "parent";

        public String describe() {
            return "Parent.describe";
        }
    }

    static class Child extends Parent {
        public String label = "child";

        @Override
        public String describe() {
            return "Child.describe";
        }
    }

    static String prefix = "cp";

    public static void main(String[] args) {
        Parent obj = new Child();
        System.out.println(obj.label);
        System.out.println(obj.describe());
        System.out.println(prefix);
        System.out.println(String.class.getName());
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac --release 17 Main.java
java Main
javap -v -p Main
javap -c -p Main
```

Stdout của lệnh `java Main` chính xác là:

```text
parent
Child.describe
cp
java.lang.String
```

Điểm cần đọc trong output `javap`:

- Mục dạng `Class ... Main$Parent` và `Class ... Main$Child` mô tả các kiểu được tạo/tham chiếu.
- Mục dạng `Fieldref ... Main$Parent.label:Ljava/lang/String;` gắn lệnh `getfield` với **field của `Parent`**. Dù object thực tế là `Child`, field Java không override như method. Do biến `obj` có kiểu compile time `Parent`, kết quả là `parent`.
- Mục dạng `Methodref ... Main$Parent.describe:()Ljava/lang/String;` gắn lệnh `invokevirtual` với method được tham chiếu từ `Parent`. Khi thực thi, dispatch động chọn override trong `Child` nên kết quả là `Child.describe`.
- Mục dạng `Fieldref ... Main.prefix:Ljava/lang/String;` phục vụ `getstatic`, trả giá trị đã khởi tạo `cp`.
- `String.class` tạo tham chiếu kiểu `java/lang/String`. `String.class.getName()` trả tên nhị phân có dấu chấm: `java.lang.String`.
- Những lời gọi `println` thường có các `Methodref` tới overload `java/io/PrintStream.println:(Ljava/lang/String;)V`.

Ví dụ các dòng **minh họa dạng** bytecode (bỏ chỉ số `#n` vì chỉ số có thể thay đổi theo compiler):

```text
getfield       #...  // Field Main$Parent.label:Ljava/lang/String;
invokevirtual  #...  // Method Main$Parent.describe:()Ljava/lang/String;
getstatic      #...  // Field Main.prefix:Ljava/lang/String;
```

Các dòng trên là **trích dạng lệnh để nhận diện**, không phải bản chép đầy đủ `javap -c` của chương trình. Quan sát output thật để đối chiếu số thứ tự, offset, những lệnh trung gian và vị trí xuất hiện. Nếu cần đọc constant pool của class lồng nhau, có thể chạy `javap -v -p 'Main$Parent'` trong shell hỗ trợ dấu nháy đơn; trên Windows Command Prompt dùng `javap -v -p "Main$Parent"`.

### Cách xem một mục `#n` cho đúng

Trong `javap -v`, phần `Constant pool:` ghi các mục dạng:

```text
#n = Methodref          #a.#b     // pkg/Service.work:(I)V
#a = Class              #c        // pkg/Service
#b = NameAndType        #d:#e     // work:(I)V
#c = Utf8                         pkg/Service
#d = Utf8                         work
#e = Utf8                         (I)V
```

`#n`, `#a`, ... ở đây là **ký hiệu giả định**, không phải chỉ số cụ thể mà `javap` bắt buộc in ra. Hãy lần theo chỉ số thực từ `Methodref` đến `Class` và `NameAndType`, rồi tới các `Utf8`. Sau đó tìm lệnh `invoke*` nào dùng `#n` để hiểu tác dụng runtime.

## 5. Lệnh JVM nào thường dùng các tham chiếu này?

| Lệnh bytecode | Tham chiếu hoặc ý nghĩa |
| --- | --- |
| `new`, `anewarray`, `checkcast`, `instanceof` | Dùng tham chiếu class/interface hoặc kiểu mảng thích hợp |
| `multianewarray` | Dùng tham chiếu đến kiểu mảng |
| `getstatic` / `putstatic` | Đọc/ghi static field qua `Fieldref` |
| `getfield` / `putfield` | Đọc/ghi instance field qua `Fieldref` |
| `invokestatic` | Gọi static method thích hợp |
| `invokespecial` | Constructor, private method và các lời gọi đặc biệt, gồm `super` |
| `invokevirtual` | Gọi instance method theo class-method reference và dispatch ảo |
| `invokeinterface` | Gọi method qua interface reference với cơ chế chọn implementation |
| `invokedynamic` | Dùng `InvokeDynamic` và bootstrap method để liên kết call site |
| `ldc` / `ldc_w` / `ldc2_w` | Nạp các hằng thích hợp; `ldc` cũng có thể dùng class literal, method handle/type và dynamic constant, tùy loại/kiểu |

Điều then chốt: **resolve symbolic reference không giống chọn method body**. Ví dụ `invokevirtual Parent.describe` cần một method reference hợp lệ tại bước resolution; sau đó receiver `new Child()` quyết định implementation `Child.describe` khi lời gọi chạy. Với `getfield`, không có dispatch override field tương tự; field hiding tạo các field riêng biệt.

`Methodref` hay `InterfaceMethodref` nào hợp lệ còn phụ thuộc opcode và quy tắc JVM. Không nên gán máy móc “`Methodref` = `invokevirtual`” bởi `Methodref` cũng được dùng trong một số lời gọi `invokestatic` và `invokespecial`. Đồng thời, `invokedynamic` có cơ chế bootstrap riêng, không thể giải thích đơn thuần bằng tìm method trực tiếp theo tên.

### Một câu hỏi thường gặp: `final static` có luôn tạo `Fieldref` không?

Không. Với **constant variable** của Java, ví dụ `static final int LIMIT = 10`, compiler của class sử dụng có thể *inline* giá trị vào bytecode, nên thao tác đọc tại call site không nhất thiết dùng `getstatic` hoặc `Fieldref` tới field ấy. Đây là lý do khi đổi hằng compile time trong một thư viện mà không biên dịch lại client, client có thể vẫn thấy giá trị cũ. Với static field không phải constant variable, bytecode thường thực hiện `getstatic`.

## 6. Resolution, lỗi liên kết và thay đổi binary compatibility

Tham chiếu ký hiệu được tạo khi compile dựa vào API lúc đó. Nếu ở runtime nạp phiên bản thư viện khác, bytecode của client có thể vẫn chứa tên/descriptor cũ. Những tình huống tiêu biểu:

| Triệu chứng | Nguyên nhân cần kiểm tra |
| --- | --- |
| `NoClassDefFoundError` | JVM không thể tìm/định nghĩa class cần dùng hoặc lỗi khi initialization trước đó; thông báo và nguyên nhân gốc rất quan trọng |
| `ClassNotFoundException` | API nạp lớp chủ động (ví dụ `Class.forName` / `ClassLoader.loadClass`) báo không tìm thấy; đây là **checked exception**, khác với linkage error mà bytecode có thể gây ra |
| `NoSuchFieldError` | Field mà bytecode tham chiếu theo **tên + descriptor** không còn được tìm thấy theo quy tắc resolution |
| `NoSuchMethodError` | Method với tên/descriptor mong đợi không còn phù hợp/tìm thấy khi liên kết |
| `IllegalAccessError` | Truy cập từng hợp lệ khi biên dịch nhưng không còn hợp lệ tại runtime (ví dụ thay đổi visibility/module access) |
| `IncompatibleClassChangeError` | Loại/thực thể bytecode kỳ vọng khác cấu trúc runtime, ví dụ thay đổi static ↔ instance hoặc class ↔ interface; một số lỗi trên là subclass của lỗi này |
| `VerifyError` | Bytecode không hợp lệ theo các quy tắc kiểm chứng, không nhất thiết là thiếu class/field/method |
| `ClassCastException` | Cast tại runtime thất bại; khi có nhiều loader, cần kiểm tra danh tính lớp |

Chẳng hạn client đã biên dịch lời gọi `Api.answer:()I`. Nếu thư viện mới đổi phương thức thành `long answer()`, descriptor thành `()J`. Ngay cả khi tên phương thức vẫn là `answer`, bytecode client có thể phát sinh `NoSuchMethodError` khi lời gọi cũ được liên kết. Rebuild client và kiểm tra tương thích API là hướng sửa đúng hơn việc giả định JVM tự chuyển chữ ký cũ sang mới.

Tương tự, xóa/chuyển tên field hoặc thay đổi kiểu field thường làm tham chiếu `Fieldref` cũ mất hiệu lực. Chuyển một method từ instance thành static (hoặc ngược lại) có thể gây lỗi dạng `IncompatibleClassChangeError` vì opcode gọi không còn phù hợp.

**Đừng quy mọi lỗi vào thời điểm “load class”.** Một class có thể đã load thành công nhưng việc dùng một tham chiếu cụ thể mới làm lộ lỗi resolution/linking. Thời điểm biểu hiện có thể khác nhau tùy cách JVM chuẩn bị các tham chiếu.

### Quy trình chẩn đoán thực tế

1. Xác định **class client** phát lệnh lỗi từ stack trace; tìm đúng artifact `.class` thực tế đã chạy.
2. Xem `javap -v -p <TenClass>` và `javap -c -p <TenClass>` để tìm **opcode**, `#index`, tên owner, tên thành viên và descriptor.
3. So sánh chính xác **runtime artifact** với phiên bản biên dịch: classpath/module path, dependency conflict, shaded JAR, deployment cũ, bản sao class trong nhiều JAR.
4. Nếu lỗi liên quan class loader, xem **defining loader** của các kiểu liên quan, đường nạp class và boundary giữa các plugin/module.
5. Kiểm tra thay đổi API: field/method có tồn tại đúng chữ ký không, là static/instance đúng lệnh không, access có hợp lệ không.
6. Sửa lại tập dependency hoặc phục hồi binary compatibility; rebuild các client phụ thuộc khi chữ ký nguồn đã thay đổi. Chạy lại và xác minh cả đường code trước đây gây lỗi.

Để kiểm tra class loader khi có đối tượng thực tế:

```java
Object value = new Main.Child();
System.out.println(value.getClass().getName());
System.out.println(value.getClass().getClassLoader());
```

Đây là **đoạn minh họa chèn vào môi trường đã có `Main`**, không phải một chương trình độc lập. Output dòng thứ hai tùy JVM và class loader. Đừng dựa vào cách `toString()` của loader để khẳng định danh tính; khi phân tích sâu có thể so sánh trực tiếp các instance `ClassLoader` và `Class`.

## 7. Hiểu đúng ranh giới giữa source, class file và runtime

| Ở mã nguồn Java | Ở class file / bytecode | Ở JVM runtime |
| --- | --- | --- |
| `Box b` hoặc `new Box()` | Internal name kiểu, `CONSTANT_Class`, có thể là `new` | Resolve tên kiểu trong ngữ cảnh loader, tạo object khi `new` thực thi |
| `b.value` | `Fieldref` với owner/name/descriptor; `getfield` hoặc `putfield` | Resolve đúng field đã mô tả; field hiding không dispatch ảo |
| `b.run()` | `Methodref` hoặc `InterfaceMethodref`; opcode `invoke*` | Resolve method và, nếu áp dụng, chọn implementation theo receiver |
| `List<String>` | Descriptor thường là `Ljava/util/List;`, metadata generic ở `Signature` | JVM kiểm tra phần lớn thao tác theo kiểu đã erasure; cast do compiler chèn vẫn có thể xảy ra |
| `String.class` | Class reference, thường nạp bằng `ldc` | Trả `Class<?>` mà bản thân thao tác này không bắt buộc initialize `String` |
| Lambda / một số cách nối chuỗi | Có thể có `InvokeDynamic` và `BootstrapMethods` | Bootstrap tạo/liên kết call site; chiến lược compiler/JDK quyết định cấu trúc cụ thể |

Không nhầm lẫn **descriptor** và **generic signature**, **symbolic reference** và **địa chỉ runtime**, **method resolution** và **virtual dispatch**, hay **class loading** và **class initialization**.

## 8. Tự kiểm tra

**Câu 1.** Vì sao `obj.label` trong ví dụ in `parent` nhưng `obj.describe()` in `Child.describe`?

**Đáp án:** `obj` có kiểu compile time `Parent`, nên field access tham chiếu `Parent.label`. Field hiding không có cơ chế override. `describe()` dùng lời gọi ảo, runtime receiver là `Child` và có override, vì vậy chọn `Child.describe`.

**Câu 2.** `CONSTANT_Methodref` tới `Service.run:()V` đã có trong class file thì có bảo đảm method tồn tại, gọi được ngay không?

**Đáp án:** Không. Constant pool lưu mô tả biểu tượng do compiler ghi. Khi resolution, JVM còn phải tìm được class/method đúng descriptor và kiểm tra điều kiện liên kết/truy cập. Sau đó opcode và đối tượng nhận lời gọi (nếu có) quyết định hành vi runtime.

**Câu 3.** Hai plugin cùng chứa `com.acme.Task` có thể cast object qua lại không?

**Đáp án:** Chỉ dựa vào tên là chưa đủ. Nếu hai class được **định nghĩa** bởi hai defining loader khác nhau thì chúng là hai kiểu khác nhau; cast có thể thất bại. Nếu cả hai bên thực sự dùng cùng một định nghĩa class qua delegation hoặc API chung, tình huống khác.

**Câu 4.** Sau khi đổi `int find()` thành `long find()` trong thư viện, vì sao client cũ có thể vỡ dù tên vẫn là `find`?

**Đáp án:** Descriptor đã đổi từ `()I` thành `()J`. Binary reference của client còn giữ chữ ký cũ, nên method resolution có thể không tìm thấy method tương ứng và ném `NoSuchMethodError`.

**Câu 5.** `new Counter()` và `Counter.class` có đồng nghĩa về thời điểm initialization không?

**Đáp án:** Không. Việc tạo instance bằng `new` là một tác nhân dẫn đến initialization thích hợp trước khi tạo object; chỉ lấy class literal `Counter.class` không tự nó buộc chạy static initializer của `Counter`.

## Ghi nhớ để đọc bytecode hiệu quả

Mỗi lần thấy `getfield`, `invokevirtual`, `invokestatic` hay `new`, hãy đi từ **opcode → chỉ số constant pool → loại entry → owner/name/descriptor → quy tắc resolution → hành vi runtime**. Trong môi trường có nhiều class loader, bổ sung câu hỏi **kiểu đó được định nghĩa bởi loader nào?**. Chuỗi truy vết này giải thích được phần lớn lỗi tên lớp, liên kết và gọi method mà chỉ đọc mã nguồn không thấy.

Tài liệu chuẩn để tra cứu sâu hơn: *Java Virtual Machine Specification*, mục **Class File Format / Constant Pool**, **Loading, Linking, and Initializing**, và **Method Invocation and Return Instructions**; đối chiếu phiên bản JVM đang dùng khi cần chi tiết opcode hoặc điều kiện lỗi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="names-and-bytecode-007">Descriptor của kiểu, phương thức và lớp mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Descriptor của kiểu, phương thức và lớp mảng trong JVM

## 1. Descriptor giải quyết vấn đề gì?

Ở mã nguồn Java, ta viết `int`, `java.lang.String`, `String[][]` hoặc `String join(int, String[])`. Trong bytecode và cấu trúc `.class`, JVM cần cách biểu diễn **kiểu dữ liệu** bằng chuỗi ngắn, có cú pháp xác định để đọc được chính xác từng kiểu mà không phụ thuộc cách trình bày của mã nguồn. Chuỗi đó gọi là **descriptor**.

Có hai dạng chính:

- **Field descriptor**: mô tả **một kiểu** của field, biến, tham số hoặc giá trị. Ví dụ: `I` là `int`, `Ljava/lang/String;` là `java.lang.String`, `[[I` là `int[][]`.
- **Method descriptor**: mô tả **danh sách kiểu tham số theo thứ tự và kiểu trả về**. Ví dụ: `(I[Ljava/lang/String;)V` là phương thức nhận `int` và `String[]`, trả về `void`.

Descriptor **không chứa** tên biến, tên phương thức, tên tham số, generic type argument, access modifier, `throws` hay nội dung thân hàm. Tên phương thức và descriptor là hai thông tin riêng trong class file. Đây là nền tảng để đọc kết quả `javap -s`, phân biệt các phương thức overload, dùng reflection/method handle và hiểu lúc JVM liên kết các tham chiếu đến lớp.

> Phân biệt ngay từ đầu: `java.lang.String` là tên nhị phân (*binary name*) dùng nhiều ở Java API; `java/lang/String` là dạng tên nội bộ (*internal name*) ở class file; `Ljava/lang/String;` mới là descriptor cho kiểu tham chiếu ấy.

## 2. Cú pháp field descriptor

Theo JVMS §4.3.2, một field descriptor biểu diễn đúng một kiểu:

```text
FieldType = BaseType | ObjectType | ArrayType
BaseType  = B | C | D | F | I | J | S | Z
ObjectType = L InternalClassName ;
ArrayType  = [ FieldType
```

Bảng ánh xạ kiểu nguyên thủy:

| Kiểu Java | Descriptor | Ghi nhớ |
|---|---|---|
| `byte` | `B` | byte |
| `char` | `C` | char |
| `double` | `D` | double |
| `float` | `F` | float |
| `int` | `I` | int |
| `long` | `J` | long, dùng ký hiệu `J` |
| `short` | `S` | short |
| `boolean` | `Z` | boolean |

`V` biểu diễn `void`, **chỉ hợp lệ làm kiểu trả về của method descriptor**; `V` không phải descriptor hợp lệ cho field, tham số hoặc phần tử mảng.

### Kiểu class hoặc interface

Cú pháp là `L` + tên nội bộ + `;`:

| Kiểu mã nguồn | Field descriptor |
|---|---|
| `String` | `Ljava/lang/String;` |
| `java.lang.Object` | `Ljava/lang/Object;` |
| `java.util.List` | `Ljava/util/List;` |
| `java.util.Map.Entry` | `Ljava/util/Map$Entry;` |

Trong tên nội bộ, dấu `/` ngăn cách package; ký hiệu `$` thường nằm trong binary name của nested class. Hai dấu `L` và `;` là thành phần bắt buộc của descriptor tham chiếu; chỉ viết `java/lang/String` là tên nội bộ, chưa phải field descriptor.

Descriptor không biểu diễn chi tiết kiểu generic. Ví dụ, khi xét kiểu đã bị xóa (*erasure*), `List<String>` và `List<Integer>` cùng có descriptor `Ljava/util/List;`. Thông tin generic có thể được giữ ở thuộc tính `Signature`, vốn có ngữ pháp khác với descriptor.

## 3. Descriptor của lớp mảng

Mỗi dấu `[` thể hiện **một chiều mảng**; sau các dấu `[` là descriptor của kiểu phần tử cuối cùng.

| Kiểu Java | Descriptor |
|---|---|
| `int[]` | `[I` |
| `long[][]` | `[[J` |
| `boolean[][][]` | `[[[Z` |
| `String[]` | `[Ljava/lang/String;` |
| `String[][]` | `[[Ljava/lang/String;` |
| `java.util.Map.Entry[][]` | `[[Ljava/util/Map$Entry;` |

Cách đọc `[[Ljava/lang/String;`: hai dấu `[` cho biết mảng hai chiều; `Ljava/lang/String;` cho biết phần tử cuối cùng là `String`. Khi bỏ một dấu `[`, ta được kiểu thành phần trực tiếp: `String[][]` có component type là `String[]`; chỉ sau khi bỏ dấu `[` thứ hai mới tới `String`.

Một số điều dễ nhầm:

- `String[]` và `String[][]` là **hai lớp mảng runtime khác nhau**, tương ứng hai đối tượng `Class<?>` khác nhau. JVM tạo lớp mảng khi cần; không cần một file nguồn riêng mang tên `[Ljava/lang/String;`.
- Với mảng tham chiếu, lớp mảng có defining loader tương ứng với defining loader của kiểu phần tử tham chiếu; mảng của kiểu nguyên thủy gắn với bootstrap loader (`getClassLoader()` có thể trả `null`). Cùng descriptor chưa đủ xác định duy nhất một lớp runtime nếu cùng tên tham chiếu được định nghĩa bởi các class loader khác nhau.
- Mảng Java là object; lớp mảng có superclass trực tiếp là `java.lang.Object` và thực thi `java.lang.Cloneable`, `java.io.Serializable`. Các đặc điểm này không xuất hiện trong chuỗi descriptor.
- Mảng nhiều chiều của Java thực chất là **mảng của mảng**; ví dụ `new int[2][]` và `new int[2][3]` đều có kiểu runtime `int[][]`, tức descriptor `[[I`. Descriptor mô tả **kiểu**, không mô tả kích thước hay số phần tử.
- JVMS giới hạn field descriptor của kiểu mảng tối đa **255 chiều**; đây là giới hạn cấu trúc class file, không phải cú pháp khai báo thông thường cần sử dụng trong ứng dụng.

## 4. Cú pháp method descriptor

Theo JVMS §4.3.3:

```text
MethodDescriptor = ( ParameterType* ) ReturnType
ParameterType    = FieldType
ReturnType       = FieldType | V
```

Từng parameter type viết **liền nhau**, không dấu phẩy, không khoảng trắng; đặt toàn bộ trong cặp ngoặc tròn. Sau `)` phải có **đúng một** kiểu trả về, kể cả khi phương thức trả về `void`.

| Khai báo Java | Method descriptor |
|---|---|
| `void ping()` | `()V` |
| `int square(int n)` | `(I)I` |
| `boolean contains(String s)` | `(Ljava/lang/String;)Z` |
| `String format(int n, String[] tags)` | `(I[Ljava/lang/String;)Ljava/lang/String;` |
| `long[][] convert(byte[] input, double factor)` | `([BD)[[J` |
| `void accept(String... values)` | `([Ljava/lang/String;)V` |

Ví dụ tách chuỗi `(I[Ljava/lang/String;)Ljava/lang/String;`:

1. `(`: bắt đầu danh sách tham số.
2. `I`: tham số thứ nhất là `int`.
3. `[Ljava/lang/String;`: tham số thứ hai là `String[]`; ký tự `;` chấm dứt descriptor của phần tử tham chiếu.
4. `)`: kết thúc tham số.
5. `Ljava/lang/String;`: kiểu trả về là `String`.

**Không có tên method trong descriptor.** Hai khai báo `int sum(int a, int b)` và `int add(int x, int y)` đều có `(II)I`. Trong class file, tên kết hợp với descriptor mới tham gia nhận diện phương thức; việc hai method có cùng tham số nhưng khác kiểu trả về không tạo thành overload hợp lệ trong mã nguồn Java.

**Không ghi `this` vào descriptor.** Method instance `String label(int id)` vẫn có `(I)Ljava/lang/String;`, mặc dù lúc gọi JVM cần object receiver. Tương tự, constructor `Person(String name)` có tên đặc biệt `<init>` trong bytecode và descriptor `(Ljava/lang/String;)V`; `V` ở đây không có nghĩa constructor khai báo trả về `void` trong mã nguồn. Static initializer `<clinit>` dùng `()V`.

**Varargs là mảng** ở descriptor: `void log(String... lines)` và `void log(String[] lines)` có cùng descriptor `([Ljava/lang/String;)V` (vì vậy không thể khai báo cả hai để overload). `throws IOException` không đổi descriptor; exception là thông tin khác của method.

Với method descriptor, JVMS còn quy định tổng độ dài tham số không quá 255 *units*: `long`/`double` đóng góp hai units, kiểu khác một unit; đối với method instance, tính thêm một unit cho receiver. Đây là ràng buộc định dạng/kiểm tra bytecode, không phải cú pháp nhìn thấy trong source.

## 5. Vì sao descriptor quan trọng khi đọc bytecode?

Ở class file, `field_info` và `method_info` tham chiếu chuỗi descriptor trong constant pool. Các tham chiếu symbolic đến field hoặc method cũng gắn với tên và descriptor thông qua các cấu trúc constant pool tương ứng. Nhờ vậy JVM biết đúng loại dữ liệu và chữ ký cần liên kết.

Xét đoạn mã minh họa:

```java
class Example {
    String[][] names;

    int count(String[] values) {
        return values.length;
    }
}
```

Trong class file, field `names` có descriptor `[[Ljava/lang/String;` và method `count` có descriptor `([Ljava/lang/String;)I`. Ta có thể kiểm tra sau khi biên dịch bằng `javap -s -p Example`. Những dòng tương ứng sẽ có dạng:

```text
java.lang.String[][] names;
  descriptor: [[Ljava/lang/String;

int count(java.lang.String[]);
  descriptor: ([Ljava/lang/String;)I
```

`javap` hiển thị khai báo theo cách dễ đọc và đồng thời in descriptor thực tế từ class file. Dùng thêm `javap -c -p Example` nếu cần quan sát bytecode lệnh; `-s` mới là tùy chọn tập trung vào descriptor.

Descriptor tuân theo *erasure* của Java generics. Ví dụ:

```java
java.util.List<String> normalize(java.util.List<Integer> input)
```

Descriptor của phương thức trên là:

```text
(Ljava/util/List;)Ljava/util/List;
```

`String` và `Integer` là generic type arguments nên không có trong descriptor này. Kiểu biến generic có upper bound cũng bị xóa về kiểu bound phù hợp (hoặc `Object` nếu không có bound tương ứng). Nếu cần biết type arguments ở runtime, phải xét metadata generic khi có, chẳng hạn `Signature` và reflection `getGenericParameterTypes()`; đừng suy ra chúng từ descriptor.

Khi compiler tạo **bridge method** để giữ tính tương thích sau erasure, bridge là một method thật trong class file và có descriptor riêng; đây là lý do kết quả `javap -v` đôi khi có nhiều method hơn số method thấy trực tiếp trong source.

## 6. Tên lớp mảng trong reflection khác descriptor ở chỗ nào?

Java có nhiều API trả về **cách viết tên khác nhau**; đừng chuyển đổi bằng cách thay dấu `.` thành `/` một cách mù quáng.

| Biểu thức Java 17 | Kết quả |
|---|---|
| `String.class.getName()` | `java.lang.String` |
| `String.class.descriptorString()` | `Ljava/lang/String;` |
| `int.class.getName()` | `int` |
| `int.class.descriptorString()` | `I` |
| `String[][].class.getName()` | `[[Ljava.lang.String;` |
| `String[][].class.getTypeName()` | `java.lang.String[][]` |
| `String[][].class.descriptorString()` | `[[Ljava/lang/String;` |
| `String[][].class.getComponentType().descriptorString()` | `[Ljava/lang/String;` |

Điểm tinh tế: `Class.getName()` đối với **mảng tham chiếu** cho dạng tên mảng có `[` và `L...;`, nhưng dùng **dấu chấm** trong tên phần tử (`[[Ljava.lang.String;`); descriptor trong class file dùng **dấu gạch chéo** (`[[Ljava/lang/String;`).

Khi cần lấy `Class<?>` qua `Class.forName`:

- `Class.forName("java.lang.String")`: class thường, dùng binary name.
- `Class.forName("[[Ljava.lang.String;")`: lớp mảng `String[][]`, dùng dạng tên mà `Class.getName()` trả về cho mảng.
- `Class.forName("[[I")`: lớp mảng nguyên thủy `int[][]`.
- `Class.forName("Ljava/lang/String;")` và `Class.forName("[Ljava/lang/String;")` **không phải cách truyền tên hợp lệ** cho các lớp trên.

Trong Java 17, `Class.descriptorString()` là cách trực tiếp lấy descriptor cho kiểu, còn `java.lang.invoke.MethodType.descriptorString()` cung cấp method descriptor. `MethodType.fromMethodDescriptorString(descriptor, loader)` chuyển ngược method descriptor thành một `MethodType` và phân giải các tên lớp tham chiếu bằng loader được cung cấp.

## 7. Ví dụ Java 17 chạy độc lập

Tạo file `DescriptorDemo.java`:

```java
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class DescriptorDemo {
    static String format(int id, String[] tags) {
        return id + ":" + tags.length;
    }

    public static void main(String[] args) throws Exception {
        System.out.println(int.class.descriptorString());
        System.out.println(String.class.descriptorString());
        System.out.println(String[][].class.descriptorString());
        System.out.println(String[][].class.getName());
        System.out.println(
                String[][].class.getComponentType().descriptorString());

        Method method = DescriptorDemo.class.getDeclaredMethod(
                "format", int.class, String[].class);
        MethodType type = MethodType.methodType(
                method.getReturnType(), method.getParameterTypes());
        System.out.println(type.descriptorString());

        Class<?> arrayClass = Class.forName("[[Ljava.lang.String;");
        System.out.println(arrayClass == String[][].class);

        MethodType parsed = MethodType.fromMethodDescriptorString(
                type.descriptorString(),
                DescriptorDemo.class.getClassLoader());
        System.out.println(parsed.parameterCount());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac DescriptorDemo.java
java DescriptorDemo
```

Stdout chính xác:

```text
I
Ljava/lang/String;
[[Ljava/lang/String;
[[Ljava.lang.String;
[Ljava/lang/String;
(I[Ljava/lang/String;)Ljava/lang/String;
true
2
```

Ý nghĩa:

- Năm dòng đầu đối chiếu primitive, class, mảng hai chiều, cách viết của `getName()` và component type.
- Dòng method descriptor sinh từ kiểu trả về `String` cùng hai tham số `int`, `String[]`. Vì method `format` là static nên không có receiver để nhầm với tham số.
- Giá trị `true` cho thấy chuỗi tên mảng theo `getName()` có thể đưa vào `Class.forName` để nhận chính đối tượng `Class` đó.
- `2` là số **tham số method**, khác với số chiều mảng và số ký tự trong descriptor.

Xem trực tiếp metadata đã biên dịch:

```bash
javap -s -p DescriptorDemo
```

Trong output, method `format` sẽ có dòng:

```text
descriptor: (I[Ljava/lang/String;)Ljava/lang/String;
```

Lưu ý: `MethodType` biểu diễn danh sách tham số và kiểu trả về; khi sử dụng method handle cho một phương thức instance, loại method handle cụ thể có thể bao gồm receiver trong *method type của handle*. Điều đó không thay đổi quy tắc descriptor của method khai báo trong class file.

## 8. Descriptor, tên và class loader khi liên kết kiểu

Descriptor cho biết **cần loại kiểu nào**, nhưng một tham chiếu như `Lcom/example/Plugin;` còn phải được giải quyết trong **ngữ cảnh class loader**. JVM không coi hai lớp có cùng binary name do hai defining class loader khác nhau tạo ra là cùng một runtime type. Vì thế hai `Class<?>` có thể có `descriptorString()` giống nhau nhưng `==` là `false`.

Với mảng `[Lcom/example/Plugin;`, kiểu phần tử tham chiếu `com.example.Plugin` cũng chịu quy tắc này. Hai loader khác nhau có thể dẫn đến hai lớp mảng khác nhau dù chuỗi descriptor giống hệt. Ngược lại, descriptor mảng `[[I` chỉ chứa primitive `int` nên không có tên class người dùng cần resolve.

Điều cần nhớ khi xử lý bytecode hoặc API động:

1. Dùng **descriptor** để hiểu chữ ký ở mức JVM; dùng **binary name** đúng chuẩn khi gọi API yêu cầu tên lớp như `Class.forName`.
2. Khi parse method descriptor, đi từng kiểu theo ngữ pháp. Không thể cắt tham số theo dấu phẩy (descriptor không có dấu phẩy) hay lấy từng ký tự (một object type có nhiều ký tự tới dấu `;`).
3. Khi cần biến descriptor thành runtime `Class`/`MethodType`, phải tính tới loader có thể resolve class được nhắc đến; chỉ có chuỗi descriptor chưa xác nhận class có tồn tại hoặc loader hiện tại truy cập được nó.
4. Không coi descriptor là nơi lưu thông tin generic, tên parameter, nullability hay annotations.

## 9. Cách đọc descriptor nhanh và lỗi hay gặp

Một quy trình đọc có thể dùng ngay:

1. Nếu chuỗi bắt đầu bằng `(`, đọc như **method descriptor**: parse lần lượt các tham số tới `)`, sau đó parse đúng một kiểu trả về.
2. Với mỗi kiểu: `B/C/D/F/I/J/S/Z` là primitive; `L` yêu cầu đọc tới dấu `;`; mỗi `[` tăng thêm một chiều mảng, rồi parse **một** kiểu phần tử.
3. Chuyển `/` trong tên nội bộ sang package dạng dấu chấm để giải thích theo Java; giữ `$` khi cần nói chính xác binary name của nested class.
4. Kiểm tra `V` chỉ xuất hiện khi làm method return type, và bảo đảm không có ký tự thừa sau kiểu cuối.

| Lỗi thường gặp | Vì sao sai | Dạng đúng |
|---|---|---|
| Viết `Lint;` | `int` là primitive, không phải class | `I` |
| Viết `Ljava.lang.String;` trong class-file descriptor | Tên nội bộ dùng `/` | `Ljava/lang/String;` |
| Quên `;` sau object type | Không biết nơi kết thúc kiểu tham chiếu | `Ljava/lang/String;` |
| Viết `[V` | Không tồn tại mảng `void[]` | Mảng cần kiểu phần tử hợp lệ như `[I` |
| Viết `(I, J) V` | Descriptor không có dấu phẩy/khoảng trắng | `(IJ)V` |
| Viết `(String[])String` | Đó là ký pháp gần với source, chưa phải descriptor | `([Ljava/lang/String;)Ljava/lang/String;` |
| Ghi receiver vào descriptor instance | Receiver `this` được JVM xử lý ngầm | `int size()` → `()I` |
| Nghĩ `List<String>` giữ `String` trong descriptor | Generic arguments bị erasure | `Ljava/util/List;` |

### Tự kiểm tra

Hãy tự giải mã trước khi xem đáp án:

1. `[[D` mô tả kiểu nào?
2. `(Ljava/lang/String;[[I)Z` mô tả chữ ký nào?
3. Descriptor của `void print(long id, String... labels)`?
4. Descriptor của field kiểu `java.util.Map<String, Integer>[]`?
5. Descriptor của `Object[][] build(byte[] bytes, double factor)`?

**Đáp án:**

1. `double[][]`.
2. Method nhận `String` và `int[][]`, trả về `boolean`.
3. `(J[Ljava/lang/String;)V`, vì varargs được biểu diễn là mảng.
4. `[Ljava/util/Map;`, vì arguments `String` và `Integer` bị erasure.
5. `([BD)[[Ljava/lang/Object;`: `[B` là `byte[]`, `D` là `double` và `[[Ljava/lang/Object;` là `Object[][]`.

## 10. Ghi nhớ và tài liệu chuẩn

- **Kiểu nguyên thủy:** `B C D F I J S Z`; `V` chỉ dành cho kiểu trả về `void`.
- **Class/interface:** `L` + internal name + `;`.
- **Mảng:** một `[` cho mỗi chiều, sau đó là descriptor kiểu phần tử.
- **Method:** `(` + các descriptor tham số ghép liền + `)` + descriptor trả về.
- **Class loader:** descriptor là mô tả cú pháp; danh tính runtime còn phụ thuộc nơi và cách tên lớp được resolve.

Tham khảo chính thức:

- [Java Virtual Machine Specification, §4.2–4.3: Names và Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3).
- [Java SE 17 `Class.descriptorString()`, `getName()` và `getComponentType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html).
- [Java SE 17 `MethodType` và `fromMethodDescriptorString`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodType.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="names-and-bytecode-008">Vì sao trình nạp phải nhận đúng tên và bytecode tương ứng</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao trình nạp phải nhận đúng tên và bytecode tương ứng?

Khi JVM yêu cầu nạp một lớp có tên `com.example.Order`, bộ nạp lớp (*class loader*) phải cung cấp bytecode thực sự mô tả **`com.example.Order`**. Một tệp có đuôi `.class` hợp lệ chưa đủ: bytecode bên trong có **tên lớp được khai báo trong cấu trúc class file**. Nếu tên JVM đang yêu cầu và tên nằm trong bytecode không khớp, lớp không thể được định nghĩa dưới tên đó.

Quy tắc này bảo vệ tính nhất quán của danh tính lớp, các liên kết kiểu dữ liệu, lời gọi phương thức và quá trình tải động. Nó đặc biệt quan trọng khi viết `ClassLoader` tùy biến, đọc bytecode từ JAR, thư mục, mạng, cơ sở dữ liệu hoặc sau bước biến đổi bytecode.

## 1. Ba khái niệm tên cần phân biệt

Giả sử mã Java:

~~~java
package com.example;

public class Order {
}
~~~

| Khái niệm | Giá trị tương ứng | Ý nghĩa |
| --- | --- | --- |
| *Binary name* (tên nhị phân) | `com.example.Order` | Tên mà các API như `ClassLoader.loadClass(String)` và `defineClass(String, …)` sử dụng cho lớp thông thường. |
| *Internal name* (tên nội bộ JVM) | `com/example/Order` | Dạng dấu gạch chéo xuất hiện trong thông tin `this_class` của class file. |
| Đường dẫn tài nguyên thông dụng | `com/example/Order.class` | Vị trí thường dùng để tìm bytecode theo quy ước thư mục/JAR; đường dẫn không tự quyết định tên lớp bên trong. |

Trong class file, trường `this_class` trỏ đến một mục `CONSTANT_Class_info` trong constant pool, từ đó lấy tên nội bộ của chính lớp đang được định nghĩa. JVM có thể đổi dạng tên nội bộ `com/example/Order` thành binary name `com.example.Order` để đối chiếu với yêu cầu.

**Điểm mấu chốt:** đổi tên tệp `Another.class` thành `Order.class` hoặc di chuyển tệp sang đúng thư mục *không sửa* `this_class`. Bytecode vẫn mô tả tên cũ. Tên lớp cũng phân biệt chữ hoa/chữ thường: `Order` khác `order`.

Các lớp lồng nhau có binary name dạng `com.example.Outer$Inner` (nếu đó là tên do trình biên dịch tạo ra). Không tự thay `$` bằng `.` trong tên truyền cho `loadClass`.

## 2. Từ tên được yêu cầu đến bytecode

Luồng khái quát đối với **lớp có tên thông thường**:

~~~text
Yêu cầu nạp "com.example.Order"
             |
             v
ClassLoader.loadClass("com.example.Order")
             |
             +--> Lớp đã được nạp? / parent delegation có tìm thấy?
             |                   |
             |                   +--> Có: trả về Class đã tìm được
             |
             v
findClass("com.example.Order") của loader tùy biến
             |
             v
Tìm/đọc đúng nguồn bytecode và kiểm tra khả năng đọc
             |
             v
defineClass("com.example.Order", bytes, 0, bytes.length)
             |
             +--> Bytecode không hợp lệ/phiên bản không hỗ trợ: lỗi tương ứng
             +--> this_class mô tả tên khác: NoClassDefFoundError
             |
             v
Class<?> đại diện cho lớp được định nghĩa
~~~

Sơ đồ giản lược: `loadClass` thường kiểm tra lớp đã nạp và ủy quyền cho loader cha trước khi gọi `findClass`; một triển khai tùy biến có thể thay đổi chính sách này. Do đó, **không phải mọi lần gọi `loadClass` đều dẫn đến `defineClass`**, nhất là khi lớp đã được parent loader tìm thấy.

Một triển khai `findClass` phổ biến sẽ chuyển binary name sang đường dẫn tài nguyên:

~~~java
String resourcePath = binaryName.replace('.', '/') + ".class";
~~~

Phép chuyển đổi đó giúp **tìm đúng tài nguyên**, nhưng nó không xác nhận danh tính bytecode. Bước định nghĩa lớp vẫn phải đối chiếu tên yêu cầu với dữ liệu class file.

Với `defineClass(String name, byte[] b, int off, int len)`, tham số `name` là **binary name được kỳ vọng**. Theo hợp đồng API, nếu `name` khác `null` mà không khớp tên được mã hóa trong bytecode, JVM ném `NoClassDefFoundError`. JVM cũng kiểm tra class file có cấu trúc và phiên bản hợp lệ.

## 3. Vì sao JVM phải kiểm tra đúng tên?

### 3.1. Giữ danh tính kiểu nhất quán

Một lớp runtime không được nhận diện chỉ bằng nội dung tệp hay tên đơn lẻ. Với lớp thông thường, danh tính kiểu phụ thuộc vào **binary name và defining class loader**. Hai loader khác nhau có thể định nghĩa hai lớp đều tên `com.example.Order`, nhưng chúng là hai kiểu khác nhau trong JVM.

Ngược lại, trong *cùng một lần định nghĩa lớp*, nếu JVM cho phép yêu cầu tên `com.example.Order` nhưng chấp nhận bytecode khai báo `com.example.Customer`, cùng một đối tượng `Class` sẽ có hai cách gọi tên trái ngược nhau. Những nơi giữ tên hoặc liên kết lớp sẽ mất tính nhất quán.

### 3.2. Giữ các tham chiếu kiểu và liên kết chính xác

Bytecode có thể chứa tham chiếu biểu tượng tới superclass, interface, field, method và các kiểu khác theo tên. Quá trình *linking/resolution* dựa vào các tham chiếu và ngữ cảnh class loader để tìm chính xác kiểu được nói đến. Nếu có thể gán tùy ý tên cho một class file khác, mã dùng tên `com.example.Order` có nguy cơ liên kết tới cấu trúc hoàn toàn khác mong đợi.

So khớp tên giúp phát hiện sớm việc nạp nhầm lớp; nó không đảm bảo toàn bộ bytecode đều hợp lệ. JVM còn thực hiện các kiểm tra định dạng, quyền truy cập, ràng buộc tải lớp và xác minh bytecode tại các giai đoạn phù hợp.

### 3.3. Phát hiện sớm lỗi đóng gói và biến đổi bytecode

Sai tên thường là dấu hiệu của một thao tác bị lỗi: copy/đổi tên tệp `.class`, đóng gói sai JAR, áp dụng quy tắc *relocation/shading* nhưng không cập nhật bytecode, hoặc ánh xạ tên sang tài nguyên không đúng. Thay vì chạy một lớp khác dưới tên đang yêu cầu, JVM báo lỗi ngay khi định nghĩa.

Đây là kiểm tra **tính nhất quán**, không phải phép xác thực chữ ký số hay bằng chứng rằng bytecode an toàn. Quyền truy cập, nguồn mã và kiểm tra bảo mật khác là trách nhiệm riêng.

## 4. Ví dụ Java chạy được: cùng bytecode, tên đúng và tên sai

Lưu toàn bộ đoạn sau vào **`NameMismatchDemo.java`**. Lớp `Actual` không có `package` nên binary name của nó là `Actual`.

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class NameMismatchDemo {
    static final class ByteArrayLoader extends ClassLoader {
        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] bytecode = Files.readAllBytes(Path.of("Actual.class"));

        try {
            new ByteArrayLoader().define("Expected", bytecode);
            System.out.println("Khong phat hien loi");
        } catch (NoClassDefFoundError e) {
            System.out.println("Sai ten: " + e.getClass().getSimpleName());
        }

        Class<?> clazz = new ByteArrayLoader().define("Actual", bytecode);
        System.out.println("Ten dung: " + clazz.getName());
    }
}

class Actual {}
~~~

Mở terminal **ngay tại thư mục chứa tệp nguồn**, sử dụng JDK 17 trở lên:

~~~bash
javac NameMismatchDemo.java
java NameMismatchDemo
~~~

Kết quả tiêu chuẩn:

~~~text
Sai ten: NoClassDefFoundError
Ten dung: Actual
~~~

Lý giải từng phần:

1. `javac` tạo `NameMismatchDemo.class` và `Actual.class`. Tệp thứ hai có `this_class` ứng với tên `Actual`.
2. Lần gọi `define("Expected", bytecode)` chủ động yêu cầu tên `Expected` trong khi bytecode tự khai báo `Actual`. `defineClass` phát hiện không khớp và ném `NoClassDefFoundError`. Chương trình chỉ in **tên loại lỗi** vì thông báo chi tiết có thể khác giữa các JVM.
3. Lần gọi `define("Actual", bytecode)` sử dụng đúng tên nên tạo lớp thành công và in `Actual`.
4. Hai đối tượng `ByteArrayLoader` độc lập giúp ví dụ tách biệt hai lần định nghĩa. Mã cố ý gọi trực tiếp `defineClass` qua một phương thức của lớp con để kiểm tra chính hợp đồng giữa tên và bytecode; nó không mô phỏng toàn bộ thuật toán ủy quyền của `loadClass`.

**Lưu ý khi dùng thực tế:** `defineClass` là phương thức `protected final`. Ứng dụng thông thường không gọi trực tiếp từ bên ngoài; code tùy biến đặt lời gọi trong lớp kế thừa `ClassLoader`, thường ở `findClass`. Việc tạo hai lớp có cùng binary name bởi hai defining loader khác nhau vốn được JVM cho phép, nhưng đối tượng của hai kiểu đó không tự động ép kiểu qua lại.

## 5. Phân biệt lỗi tên với các lỗi tải lớp khác

| Tình huống | Kết quả thường gặp | Ý nghĩa |
| --- | --- | --- |
| Không tìm được lớp theo tên trong cơ chế tra cứu của `loadClass` | `ClassNotFoundException` | Loader không cung cấp được lớp được yêu cầu; đây là checked exception thường xuất hiện từ `loadClass`/`Class.forName`. |
| Có class file hợp lệ nhưng `this_class` không khớp `name` được truyền vào `defineClass` | `NoClassDefFoundError` | Bytecode mô tả **một lớp khác** tên mà JVM đang định nghĩa. |
| Bytecode không theo cấu trúc class file hợp lệ | `ClassFormatError` | Vấn đề định dạng nội dung, chưa chắc là sai tên. |
| Class file được biên dịch theo phiên bản JVM mà runtime không hỗ trợ | `UnsupportedClassVersionError` | Phiên bản bytecode không tương thích; thuộc họ `ClassFormatError`. |
| Lớp đã được định nghĩa không vượt qua quy tắc xác minh | `VerifyError` | Vấn đề ràng buộc kiểu/luồng bytecode trong quá trình xác minh, không đồng nghĩa sai tên. |
| Cố định nghĩa lại cùng binary name trong cùng defining loader | `LinkageError` | Trùng định nghĩa hoặc vi phạm ràng buộc tải lớp; khác với việc có hai loader riêng biệt. |

`NoClassDefFoundError` còn có các nguyên nhân khác, ví dụ khi một phụ thuộc đã có lúc biên dịch nhưng không thể tìm/nạp khi runtime. Vì vậy **không thể kết luận chỉ từ tên exception rằng `this_class` sai**; phải xem thông điệp, stack trace và đường đi nạp lớp.

## 6. Áp dụng khi tự viết ClassLoader

Một loader tùy biến cần duy trì các bất biến sau:

1. **Giữ nguyên binary name đang được yêu cầu.** Trong `findClass(String name)`, dùng `name` làm đầu vào định tuyến tới JAR, thư mục hoặc nguồn bytecode. Chuyển dấu chấm thành dấu gạch chéo chỉ khi tạo đường dẫn tài nguyên thông thường.
2. **Lấy đúng bytes của tệp class, đọc trọn dữ liệu.** Không gán tên yêu cầu dựa riêng trên tên file hoặc kết quả tìm kiếm gần đúng. Bytecode không có nghĩa là một đoạn source Java hoặc một chuỗi text UTF-8.
3. **Truyền đúng tên vào `defineClass(name, bytes, 0, bytes.length)`.** API cho phép truyền `null` khi không biết tên; tuy nhiên truyền binary name mong đợi tạo kiểm tra danh tính rõ ràng và là cách nên dùng cho loader dựa trên tên.
4. **Giữ cơ chế ủy quyền và phạm vi loader có chủ đích.** Nếu parent đã nạp một lớp, `loadClass` thông thường trả về lớp đó. Nếu thiết kế child-first, phải hiểu rằng cùng binary name dưới defining loader khác tạo ra một kiểu runtime riêng.
5. **Phân biệt lỗi tra cứu với lỗi định nghĩa.** Khi nguồn không chứa tên yêu cầu, `findClass` nên báo `ClassNotFoundException`; khi `defineClass` nhận bytecode không phù hợp, không nên che mất `LinkageError` thành thông báo “không tìm thấy file”.
6. **Quan sát đủ ngữ cảnh để chẩn đoán.** Khi lỗi, ghi nhận binary name được yêu cầu, nguồn tài nguyên/JAR, defining loader dự kiến và nguyên nhân gốc. Không cần ghi toàn bộ bytecode vào log.

### Hai lỗi thực tế dễ mắc

**Đổi tên file mà không sửa nội dung class file.** Một tệp chứa `com/vendor/OldName` được chép thành `com/app/NewName.class`. Loader tìm ra tài nguyên qua đường dẫn mới, nhưng `defineClass("com.app.NewName", bytes, …)` vẫn thất bại. Cần build/rewrite bytecode hợp lệ để cập nhật tên thực sự, chứ không chỉ đổi tên tệp.

**Lấy sai entry từ JAR.** Loader đang xử lý `com.example.Order` nhưng lỡ đọc entry `com/example/Customer.class`. Việc cả hai entry tồn tại và đều là class file hợp lệ không giúp sửa lỗi: `this_class` trong entry được đọc vẫn là `com/example/Customer`. Kiểm tra ánh xạ binary name → resource path → class-file content.

## 7. Những điều cần nhớ và tự kiểm tra

- **Yêu cầu một tên thì phải định nghĩa đúng lớp mang tên ấy:** `name` tại `defineClass` phải khớp binary name được mô tả bởi `this_class` khi `name` khác `null`.
- **Đường dẫn không phải danh tính:** tên file hoặc thư mục chỉ là cách lưu trữ/tra cứu; cấu trúc class file chứa thông tin danh tính thực sự.
- **Tên đúng vẫn chưa đủ:** bytecode còn phải đúng định dạng, phiên bản, quy tắc xác minh và các ràng buộc liên kết khác.
- **Tên cộng defining loader tạo danh tính runtime:** cùng tên dưới hai loader có thể biểu thị hai kiểu khác nhau; khi ủy quyền thành công, loader con có thể trả về lớp do loader cha định nghĩa.

Thử suy luận: loader nhận `loadClass("com.example.Invoice")`, tìm được tài nguyên `com/example/Invoice.class`, nhưng trường `this_class` trỏ đến `com/example/Receipt`. Có thể định nghĩa `Invoice` bằng bytecode này không? **Không.** Đường dẫn khớp nhưng tên bên trong bytecode sai; nếu gọi `defineClass("com.example.Invoice", …)` thì nhận `NoClassDefFoundError`. Sửa dữ liệu hoặc ánh xạ tới đúng bytecode; không đổi tên yêu cầu để “lách” hợp đồng với caller.

## Tài liệu chuẩn để đọc thêm

- [Java SE 21 – `ClassLoader.defineClass`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): hợp đồng `name`, `null`, các lỗi định nghĩa và ràng buộc gói.
- [JVM Specification, §5.3 và §5.3.5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): định danh lớp theo loader và nguyên tắc tạo lớp từ class file.
- [JVM Specification, §4.1 – The `ClassFile` Structure](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html): trường `this_class` và cách class file biểu diễn tên.

</details>

- [Quay lại đầu trang](#back-to-top)
