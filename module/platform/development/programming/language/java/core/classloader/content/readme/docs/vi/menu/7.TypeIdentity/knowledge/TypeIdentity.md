<a id="back-to-top"></a>

# Định danh kiểu

## Menu
- [Định danh lớp bằng tên nhị phân và defining loader](#type-identity-001)
- [Hai lớp cùng tên nhưng khác loader là hai kiểu khác nhau](#type-identity-002)
- [So sánh Class, instanceof, isInstance và isAssignableFrom](#type-identity-003)
- [ClassCastException khi tên lớp nguồn và đích giống nhau](#type-identity-004)
- [Runtime package được xác định bởi tên package và loader](#type-identity-005)
- [Truy cập package-private giữa các lớp do loader khác nhau định nghĩa](#type-identity-006)
- [Loading constraint bảo đảm nhất quán kiểu trong chữ ký](#type-identity-007)
- [Lớp mảng, kiểu phần tử và loader tương ứng](#type-identity-008)
- [Kiểu nguyên thủy, void và giới hạn của loadClass](#type-identity-009)
- [Thiết kế kiểu API dùng chung tại ranh giới nhiều loader](#type-identity-010)

## <a id="type-identity-001">Định danh lớp bằng tên nhị phân và defining loader</a>

<details>
<summary>Xem chi tiết</summary>

# Định danh lớp bằng tên nhị phân và defining loader

> **Chủ đề:** Java Core → ClassLoader → Định danh kiểu. Trong JVM, tên lớp chỉ là **một nửa** định danh kiểu runtime: đối với lớp/interface thông thường, JVM phân biệt bằng cặp **(binary name, defining class loader)**. Đây là cơ sở để hiểu tại sao cùng tệp `.class` nhưng nạp qua hai loader có thể sinh hai kiểu không tương thích.

## 1. Vì sao tên lớp giống nhau vẫn có thể là hai kiểu khác nhau?

Giả sử cả hai plugin đều chứa tệp `demo/Token.class` được biên dịch từ cùng một Java source:

```java
package demo;
public class Token {
    public String value() { return "ok"; }
}
```

Bạn có hai `Class<?>`, gọi là `a` và `b`, cùng trả về `"demo.Token"` khi gọi `getName()`. Chỉ thông tin đó **chưa đủ để khẳng định `a == b`**.

Theo JVM Specification `5.3`, sau khi được tạo, một lớp hoặc interface (không phải array) được xác định bởi:

```text
Runtime type identity = (binary name, defining class loader)

("demo.Token", loader A) != ("demo.Token", loader B)
("demo.Token", loader A) == ("demo.Token", loader A)
```

Hai dòng ký hiệu trên thể hiện quy tắc **định danh của kiểu**, không phải biểu thức Java chạy độc lập. Khi các loader khác nhau **thật sự định nghĩa** cùng binary name, JVM tạo ra hai kiểu runtime khác nhau, dù bytes của `.class` có thể giống hệt, đường dẫn JAR giống nhau và Java source giống nhau.

Vì vậy:

- `a.getName().equals(b.getName())` có thể là `true`.
- `a == b`, `a.equals(b)` có thể là `false` (với `Class`, `equals` dùng identity object).
- Object tạo từ `a` thường không được `b.cast(object)` chấp nhận.
- `a.getClassLoader()` và `b.getClassLoader()` cho biết **defining loader** của từng kiểu, không đơn thuần “loader từng được gọi để tìm class”.

Đây không phải lỗi do hai class khác package. **Ngay cả package và binary name đều giống nhau**, defining loader khác vẫn tạo khác biệt kiểu runtime.

## 2. Binary name: thành phần tên trong cặp định danh

*Binary name* là tên JVM dùng để định danh lớp/interface bình thường theo quy tắc JLS `13.1`:

| Khai báo Java | Binary name | Ghi chú |
| --- | --- | --- |
| `package demo; class Token {}` | `demo.Token` | Top-level class. |
| `package demo; class Outer { static class Inner {} }` | `demo.Outer$Inner` | Member class dùng `$`. |
| `java.util.Map.Entry` | `java.util.Map$Entry` | Trong source là tên kiểu member, còn tên nhị phân chứa `$`. |
| Local/anonymous class | Thường có `$` và chữ số do compiler tạo | Không hardcode số thứ tự do compiler sinh. |

Phân biệt ba dạng tên:

```text
Canonical Java name: demo.Outer.Inner
Binary name:         demo.Outer$Inner
JVM internal name:   demo/Outer$Inner
```

`Class.getName()` trả binary name cho **non-array, non-hidden reference class/interface bình thường**. `Class.forName("demo.Outer$Inner")` hoặc `loader.loadClass("demo.Outer$Inner")` tìm lớp member bằng binary name; chuỗi canonical `"demo.Outer.Inner"` không thay cho binary name trong trường hợp đó.

**Giới hạn:** mảng, primitive/void và hidden classes có biểu diễn/semantics riêng ở API `Class`. Bài này tập trung vào định danh lớp/interface thông thường; phần 8 giải thích mảng và primitive để tránh suy rộng sai.

## 3. Defining loader khác initiating loader

Khi JVM cần tạo kiểu mang tên `N`, nó có thể yêu cầu một loader `L1` đi tìm class. Loader này có thể:

1. **Tự định nghĩa:** đọc/generate bytes và gọi `defineClass(...)`. Khi đó `L1` là *defining loader* của class mới.
2. **Ủy quyền:** gọi loader khác `L2` nạp tên `N`. Nếu `L2` là bên thực sự gọi `defineClass`, thì `L2` là *defining loader*, trong khi `L1` chỉ khởi phát việc nạp qua delegation.

JVMS dùng thuật ngữ:

- **Defining loader:** loader **trực tiếp định nghĩa** class/interface từ binary representation.
- **Initiating loader:** loader mà JVM ghi nhận đã **khởi phát** quá trình nạp lớp tương ứng, có thể trực tiếp hoặc thông qua delegation.

Ví dụ delegation:

```text
Application requests "demo.Token" through pluginChild
                         |
                         v
               pluginChild.loadClass(...)
                         |
                         | parent delegation
                         v
                sharedParent.loadClass(...)
                         |
                         | reads Token.class + defineClass(...)
                         v
                   Class<demo.Token>
                   defining loader = sharedParent

pluginChild: initiating loader (may return Class defined by parent)
sharedParent: defining loader (also initiating loader)
```

Trong phạm vi mô hình JVM, các loader tham gia ở giữa chuỗi delegation **không nhất thiết đều được ghi nhận là initiating loader**; không coi việc “được gọi `loadClass`” là đồng nghĩa 100% với việc JVM đánh dấu nó là initiating loader. Cần nhìn vào kết quả runtime và quy tắc ở JVMS `5.3`.

Đối với một instance `Class<?> c`:

```java
ClassLoader definingLoader = c.getClassLoader();
```

`getClassLoader()` trả loader **định nghĩa** lớp, hoặc `null` cho lớp do bootstrap loader định nghĩa. Nó không trả một “loader theo yêu cầu gần nhất”. Nếu hỏi hai child loaders rồi cả hai delegate tới một parent chung, có thể thu về **chính cùng một `Class<?>`**, vì defining loader không đổi.

### Quan hệ parent không quyết định type identity trực tiếp

`loader.getParent()` cho biết parent trong cây delegation thông thường; **parent loader và defining loader không phải hai từ đồng nghĩa**. Chỉ khi một loader thực sự định nghĩa lớp, identity mới mang loader ấy. Parent-first là chiến lược phổ biến của `ClassLoader.loadClass` mặc định và `URLClassLoader`, nhưng custom class loaders có thể tổ chức delegation khác với mục đích rõ ràng.

## 4. Chứng minh bằng Java 17+: hai URLClassLoader nạp cùng bytes

Thí nghiệm đặt plugin class trong một thư mục **không có trên classpath của ứng dụng khởi động**, rồi tạo hai loader độc lập đọc cùng thư mục đó. Để ví dụ rõ ràng, hai loader dùng platform loader làm parent; plugin chỉ cần các API Java tiêu chuẩn.

Tạo cấu trúc:

```text
plugin-src/demo/Token.java
app-src/IdentityDemo.java
plugin-classes/    (được javac tạo)
app-classes/       (được javac tạo)
```

**File `plugin-src/demo/Token.java`:**

```java
package demo;

public class Token {
    public String value() {
        return "ok";
    }
}
```

**File `app-src/IdentityDemo.java`:**

```java
import java.lang.reflect.Array;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class IdentityDemo {
    public static void main(String[] args) throws Exception {
        URL[] urls = {
                Path.of("plugin-classes").toAbsolutePath().toUri().toURL()
        };
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader first = new URLClassLoader(urls, platform);
             URLClassLoader second = new URLClassLoader(urls, platform);
             URLClassLoader delegated = new URLClassLoader(new URL[0], first)) {

            Class<?> a = Class.forName("demo.Token", false, first);
            Class<?> b = Class.forName("demo.Token", false, second);
            Class<?> c = Class.forName("demo.Token", false, delegated);

            Object value = a.getDeclaredConstructor().newInstance();

            System.out.println("same binary name: "
                    + a.getName().equals(b.getName()));
            System.out.println("same Class: " + (a == b));
            System.out.println("distinct defining loaders: "
                    + (a.getClassLoader() != b.getClassLoader()));
            System.out.println("same loader returns same Class: "
                    + (a == Class.forName("demo.Token", false, first)));
            System.out.println("first accepts object: " + a.isInstance(value));
            System.out.println("second accepts object: " + b.isInstance(value));
            System.out.println("delegation returns first Class: " + (c == a));
            System.out.println("delegated is defining loader: "
                    + (c.getClassLoader() == delegated));
            System.out.println("array classes identical: "
                    + (Array.newInstance(a, 0).getClass()
                    == Array.newInstance(b, 0).getClass()));
            System.out.println("array loader first: "
                    + (Array.newInstance(a, 0).getClass().getClassLoader()
                    == first));

            try {
                b.cast(value);
                System.out.println("unexpected cast success");
            } catch (ClassCastException expected) {
                System.out.println("cross-loader cast: ClassCastException");
            }
        }
    }
}
```

Biên dịch từ **thư mục cha** chứa hai thư mục source:

```bash
javac -d plugin-classes plugin-src/demo/Token.java
javac -d app-classes app-src/IdentityDemo.java
java -cp app-classes IdentityDemo
```

Stdout đã kiểm chứng trên OpenJDK 21:

```text
same binary name: true
same Class: false
distinct defining loaders: true
same loader returns same Class: true
first accepts object: true
second accepts object: false
delegation returns first Class: true
delegated is defining loader: false
array classes identical: false
array loader first: true
cross-loader cast: ClassCastException
```

### Giải thích từng kết quả

1. `first` và `second` nhận **cùng `URL[]` và đọc cùng bytes**; nhưng chúng là **hai object loader khác nhau**. Cả hai đều tự định nghĩa `demo.Token`, tạo hai kiểu runtime khác nhau.
2. `getName()` chỉ kiểm tra **binary name** nên `true`; `a == b` là `false` vì defining loader khác.
3. Gọi lại `Class.forName("demo.Token", false, first)` trả về cùng `Class<?> a`. Một loader vận hành đúng không tạo một định nghĩa thứ hai với cùng binary name của nó.
4. `a.isInstance(value)` là `true` nhưng `b.isInstance(value)` là `false`; `Class.cast` của `b` ném `ClassCastException`. **Object từ một định nghĩa không tự động là instance của định nghĩa cùng tên ở loader khác**.
5. `delegated` có mảng URL rỗng và parent là `first`. Khi tìm `demo.Token`, nó delegate tới `first` và nhận đúng `a`. Vì vậy `c == a` là `true` nhưng `c.getClassLoader() == delegated` là `false`: **initiating không buộc là defining**.
6. Hai `demo.Token[]` cũng khác nhau; array class có identity gắn với **kiểu phần tử**. `Array.newInstance(a, 0)` tạo mảng có defining loader được phản ánh bằng `first`.
7. `URLClassLoader` triển khai `AutoCloseable` nên có thể đóng bằng try-with-resources. `close()` đóng tài nguyên mà loader mở; nó **không buộc JVM unload ngay** những class đã được định nghĩa.

Lưu ý thí nghiệm chỉ nạp plugin qua URLClassLoader ở `plugin-classes`; không thêm `plugin-classes` vào `-cp`. Nếu class được parent nhìn thấy trước, parent-first delegation có thể trả lớp do parent định nghĩa và làm thí nghiệm không còn minh họa hai defining loaders khác nhau.

## 5. Hệ quả thực tế: vì sao cast và instanceof thất bại?

Phép ép kiểu kiểm tra **quan hệ giữa các runtime type**, không kiểm tra chuỗi tên class. Trong thí nghiệm trước, `a.isInstance(value)` là `true`, còn `b.isInstance(value)` là `false` và `b.cast(value)` ném `ClassCastException`: `a` và `b` đều mang tên `demo.Token` nhưng do hai loader định nghĩa.

Ở ứng dụng plugin, lỗi tương tự xảy ra khi host và plugin cùng đóng gói, **tự định nghĩa** hai bản `api.Plugin`. Dù source plugin ghi `implements api.Plugin`, object chỉ implement bản interface mà loader của nó đã liên kết. Cần cho hai phía dùng chung **cùng `Class<?>` của hợp đồng API** từ một loader được chia sẻ, hoặc chuyển dữ liệu qua giao thức trung lập nếu chủ ý cách ly. Bài về `ClassCastException` phân tích stack trace và quy trình khắc phục cụ thể.

## 6. Runtime package và quyền truy cập cũng phụ thuộc defining loader

Runtime package được xác định bằng **(tên package, defining loader)**. Hai class đều khai báo `package com.acme;` nhưng được các loader A và B định nghĩa nằm trong **hai runtime package** khác nhau. Vì thế package-private không tự cho phép truy cập chéo; khi JVM kiểm tra một symbolic reference, truy cập sai có thể dẫn đến `IllegalAccessError`.

`protected`, nestmates và JPMS còn có quy tắc truy cập riêng. `exports`/`opens` của module không thay thế điều kiện cùng runtime package. Các bài về runtime package và package-private triển khai thí nghiệm truy cập chi tiết.

## 7. Defining loader và initiating loader ảnh hưởng linkage ra sao?

JVM dùng **loading constraints** để giữ an toàn kiểu khi liên kết field, method, override hoặc interface xuyên qua các loader. Chẳng hạn hai phía cùng sử dụng tên `com.acme.Message` trong method descriptor, nhưng loader của mỗi bên ánh xạ tên đó tới `Class<?>` khác nhau, có thể vi phạm constraint và gây `LinkageError`.

Phân biệt ba trường hợp: hai định nghĩa cùng tên **có thể cùng tồn tại** khi chưa cần thống nhất; **cast sai** thường báo `ClassCastException`; **linking cần cùng kiểu nhưng bất nhất** có thể báo loading constraint violation. Bài về loading constraints đi sâu điều kiện áp đặt và thời điểm kiểm tra.

## 8. Các trường hợp đặc biệt: array, primitive và bootstrap

Với **array**, JVM tạo lớp mảng khi cần. `getClassLoader()` của mảng phần tử reference phản ánh defining loader của kiểu phần tử cơ sở; mảng primitive có loader `null`. Vì vậy hai lớp `demo.Token` khác identity tạo ra hai kiểu `demo.Token[]` khác identity, dù tên mảng giống nhau.

Các `Class<?>` của primitive và `void` (`int.class`, `void.class`) do JVM cung cấp theo cơ chế riêng; không thể dùng `loadClass("int")` để nạp chúng từ class file. Array descriptors, hidden classes và các kiểu đặc biệt cần áp dụng quy tắc tương ứng, không suy diễn cứng từ cặp binary name + defining loader của class/interface thông thường.

## 9. Những sai lầm khi debug và cách tự kiểm chứng

### Sai lầm 1: Chỉ log getName() là đủ

Hai `Class<?>` có thể có cùng `getName()` nhưng khác defining loader. Hãy log **cả hai thông tin** và kiểm tra identity thực tế:

```java
// Đặt trong method nhận hai biến Class<?> left, right:
System.out.println("left name = " + left.getName());
System.out.println("right name = " + right.getName());
System.out.println("same class object = " + (left == right));
System.out.println("left defining loader = " + left.getClassLoader());
System.out.println("right defining loader = " + right.getClassLoader());
```

Đây là snippet minh họa; `left` và `right` cần đến từ code gọi. `ClassLoader.toString()` chỉ hỗ trợ debug, **không phải định danh bền vững** để lưu trong database. `System.identityHashCode(loader)` cũng chỉ cho một hash phục vụ quan sát; có thể trùng giữa hai object khác nhau nên không thay thế phép so sánh `==`.

### Sai lầm 2: Nghĩ hai URLClassLoader trỏ cùng JAR thì class giống nhau

Hai loader khác nhau cùng `URL` vẫn có thể định nghĩa hai class riêng. Chỉ khi **cả hai yêu cầu được delegate tới cùng defining loader** mới nhận lại cùng class identity. Đường dẫn file, checksum bytecode, package và `Class.getName()` không tự quyết định identity.

### Sai lầm 3: Dùng Class.forName mà không chủ động chọn loader

`Class.forName(String)` dùng quy tắc loader của caller; `Class.forName(String, boolean, ClassLoader)` cho phép truyền loader cụ thể. Trong plugin/container, chọn sai loader dễ gây `ClassNotFoundException` hoặc tạo/nhận nhầm kiểu có cùng tên.

`Thread.currentThread().getContextClassLoader()` (TCCL) là một cơ chế loader khác, hữu ích cho SPI/framework, nhưng **không có nghĩa** nó luôn là defining loader của một class. Muốn biết class do ai định nghĩa, dùng `clazz.getClassLoader()`.

### Sai lầm 4: Cố cast “cùng interface source” giữa hai loader

Nếu host và plugin có hai bản class của cùng API, sửa cấu hình classpath một cách ngẫu nhiên không bảo đảm hợp nhất identity. Nên bố trí **API chung ở loader chia sẻ**, tránh đưa bản copy API vào private/plugin namespace và kiểm tra parent delegation.

### Sai lầm 5: Nghĩ đóng loader là class lập tức biến mất

Đóng `URLClassLoader` không reset các `Class<?>` hay object đã được tạo. Thread context loader, cache, static field, thread hoặc hook có thể giữ strong reference làm loader chưa thể được thu hồi.

## 10. Quy trình giải quyết xung đột loader trong dự án thật

1. **Xác định hai kiểu đang tranh chấp.** Từ `ClassCastException`, `LinkageError` hoặc thông tin plugin/container, lấy hai `Class<?>` nếu có thể và so sánh `getName()`, `==`, `getClassLoader()`.
2. **Tìm nguồn định nghĩa**: đường dẫn JAR/module, dependency graph, đường nạp resource; kiểm tra liệu host và plugin có bundle trùng API. `clazz.getProtectionDomain().getCodeSource()` có thể hỗ trợ nhưng cũng có thể `null` và không quyết định class identity.
3. **Vẽ delegation topology thực tế:** ai gọi `loadClass`, parent là ai, chính sách parent-first/child-first của từng loader, class nào do loader nào thực sự `defineClass`.
4. **Xác định kiểu cần chia sẻ** giữa các ranh giới: interface, DTO, exception và return/parameter types trong phương thức. Chỉ định một định nghĩa dùng chung với một loader chung.
5. **Đảm bảo bytecode/dependency nhất quán:** nâng cấp các phiên bản API, không âm thầm tạo duplicate copies, kiểm tra module readability/exports/opens khi dùng JPMS.
6. **Tái tạo bằng ví dụ nhỏ** như `IdentityDemo` để chứng minh chính xác cặp (name, loader) nào đang khác nhau. Đừng đoán qua tên lớp in ra trong message của lỗi.
7. **Kiểm tra lại bằng object identity và type check**, sau đó xem xét class lifecycle/unloading nếu có reload/hot deploy.

Với HotSpot, log tải lớp có thể hỗ trợ:

```bash
java -Xlog:class+load=info -cp app-classes IdentityDemo
```

Các dòng log class loading cho biết quá trình nạp và nguồn class theo mức logging hiện tại; đây là dữ liệu bổ trợ, không thay thế việc so sánh `Class<?>` và defining loader trong app. Quyền quan sát khác nhau giữa JVM/version.

## 11. Tự kiểm tra

1. **Hai `Class<?>` trả cùng `"com.acme.Plugin"` từ `getName()` có bắt buộc bằng nhau không?** Không. Chúng chỉ cùng binary name; defining loader có thể khác.
2. **Một child loader gọi `parent.loadClass("demo.Token")` rồi trả đúng object `Class<?>` của parent. Defining loader là ai?** Parent (nếu parent trực tiếp định nghĩa class). Child có thể là initiating loader nhưng không phải defining loader.
3. **Hai child loaders cùng delegate một class tới parent chung thì có thể trả cùng class không?** Có, nếu cùng trả class đã được parent định nghĩa.
4. **Hai loader đọc cùng byte-for-byte `Token.class` nhưng mỗi loader tự `defineClass` thì `a == b` là gì?** `false`.
5. **Tại sao `Class.cast` ném `ClassCastException` dù hai tên giống hệt?** Bởi vì kiểm tra kiểu sử dụng identity runtime; binary name trùng chưa đủ.
6. **Hai lớp cùng `package com.acme;` từ hai defining loaders có cùng runtime package không?** Không; runtime package được phân biệt bởi tên package và defining loader.
7. **`Class.getClassLoader() == null` với `String.class` có nghĩa chưa load không?** Không; đó là bootstrap loader.
8. **Hai lớp cùng tên có nhất thiết gây `LinkageError` ngay lập tức không?** Không; chúng có thể cùng tồn tại ở loader khác nhau. Loading constraint vi phạm, lặp define cùng loader hoặc hành động sử dụng không hợp lệ mới gây lỗi tùy trường hợp.
9. **Hai mảng có kiểu phần tử do hai loader định nghĩa khác nhau có cùng kiểu không?** Không; JVM gắn array identity với component type và loader tương ứng.

## 12. Tài liệu chuẩn

- [JVMS `5.3` — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): phân biệt defining/initiating loader và định danh runtime bằng cặp (binary name, defining loader).
- [JVMS `5.3.3` — Creating Array Classes](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.3): array class do JVM tạo và dùng defining loader của component type.
- [JVMS `5.3.4` — Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.4): duy trì an toàn kiểu khi liên kết qua nhiều loader.
- [JLS `13.1` — The Form of a Binary](https://docs.oracle.com/javase/specs/jls/se21/html/jls-13.html#jls-13.1): quy tắc binary names.
- [Java SE 21 `ClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `defineClass`, delegation, defining loader và array class.
- [Java SE 21 `Class` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): `getName`, `getClassLoader`, `isInstance` và `cast`.
- [Java SE 21 `URLClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): URL-based loading, parent delegation và `close`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-002">Hai lớp cùng tên nhưng khác loader là hai kiểu khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Hai lớp cùng tên nhưng khác ClassLoader là hai kiểu khác nhau trong Java

## 1. Quy tắc định danh kiểu ở JVM

Trong Java, tên đầy đủ của một lớp như `com.example.Plugin` rất quan trọng, nhưng **chỉ biết tên lớp chưa đủ để xác định một kiểu ở runtime**. Với một lớp hoặc interface thông thường, JVM xác định danh tính bằng **cặp gồm tên nhị phân (binary name) và class loader đã định nghĩa lớp (defining class loader)**:

~~~text
Runtime type identity = (binary name, defining ClassLoader)
~~~

Ví dụ, giả sử hai loader độc lập thực sự định nghĩa bytecode cho `com.example.Plugin`:

~~~text
Loader A -- defineClass("com.example.Plugin", bytes) --> Class A
Loader B -- defineClass("com.example.Plugin", bytes) --> Class B

Class A.getName() == "com.example.Plugin"
Class B.getName() == "com.example.Plugin"

Class A != Class B
~~~

Kể cả hai mảng bytecode giống hệt nhau, JVM vẫn có thể tạo **hai kiểu runtime khác nhau** nếu chúng được định nghĩa bởi **hai defining loader khác nhau**. Đối tượng tạo từ kiểu thứ nhất không tự động là instance của kiểu thứ hai; cast giữa chúng có thể gây `ClassCastException`.

**Điều kiện quan trọng:** hai đối tượng `ClassLoader` khác nhau *không nhất thiết* sinh ra hai lớp khác nhau. Nếu cả hai **ủy quyền cho cùng một parent** và parent định nghĩa lớp, chúng có thể nhận về **chính một đối tượng `Class<?>`**. Chỉ khi có **hai định nghĩa runtime khác nhau** do hai defining loader khác nhau, kết luận “hai kiểu khác nhau” mới áp dụng.

Bài này giải thích cơ chế, ví dụ chạy được với Java 17+, khác biệt giữa initiating loader và defining loader, kết quả `instanceof` / `isInstance` / cast, trạng thái `static`, cũng như cách chẩn đoán lỗi trong plugin và application server.

## 2. Vì sao chỉ so sánh tên lớp là sai?

Trong mã nguồn Java, import `com.example.Plugin` có vẻ chỉ tới một kiểu. Tuy nhiên môi trường runtime có thể gồm nhiều miền tải lớp:

- Ứng dụng chính nạp API và dependency bằng application/system loader.
- Một container tạo loader riêng cho mỗi ứng dụng được triển khai.
- Mỗi plugin có loader riêng, có thể mang phiên bản thư viện khác nhau.
- Công cụ phát triển, test runner hoặc framework hot reload tạo loader mới khi mã thay đổi.
- Runtime chạy nhiều module layer có thể liên hệ các module với loader khác nhau.

Do đó hai đoạn code cùng thấy chuỗi `"com.example.Plugin"` vẫn có thể đang thao tác với hai đối tượng `Class` khác nhau.

Các phép quan sát cơ bản:

~~~java
Class<?> left = objectA.getClass();
Class<?> right = objectB.getClass();

boolean sameName = left.getName().equals(right.getName());
boolean sameType = left == right;

ClassLoader loaderA = left.getClassLoader();
ClassLoader loaderB = right.getClassLoader();
~~~

Đây là **mã minh họa trong một phương thức có sẵn hai đối tượng**, không phải chương trình độc lập.

- `sameName` trả lời: **tên nhị phân có giống nhau không?**
- `sameType` trả lời: **có đúng là một kiểu runtime không?**
- `getClassLoader()` cho biết loader định nghĩa kiểu. Đối với nhiều lớp lõi Java do bootstrap loader định nghĩa, kết quả là `null`.
- Không dùng `left.toString().equals(right.toString())` để xác định cùng kiểu: một số chuỗi in ra không mang đủ thông tin về defining loader.

Quy tắc này gắn với JVM và bộ xác minh kiểu, chứ không phải chỉ một thói quen đặt tên class của compiler.

## 3. Phân biệt defining loader và initiating loader

**Defining class loader** là loader thực sự **định nghĩa** một lớp bằng dữ liệu class file hoặc cơ chế định nghĩa hợp lệ tương ứng. Danh tính runtime của lớp gắn với defining loader đó.

**Initiating class loader** là loader tham gia khởi phát việc nạp lớp theo yêu cầu của JVM, kể cả khi công việc cuối cùng được ủy quyền cho loader khác.

Xét tình huống:

~~~text
ChildLoader.loadClass("example.Service")
        |
        v
ChildLoader ủy quyền -> ParentLoader.loadClass("example.Service")
                                 |
                                 v
                            ParentLoader định nghĩa
                                 |
                                 v
                  trả về cùng Class<?> cho ChildLoader
~~~

`ChildLoader` có thể là initiating loader của yêu cầu, nhưng **ParentLoader mới là defining loader** của `example.Service`. Khi đó `Service.class.getClassLoader()` (hoặc `loadedClass.getClassLoader()`) cho thấy defining loader là parent.

Các loader có thể duy trì quan hệ ủy quyền và cache kết quả để những lần tải sau trả về cùng `Class<?>`. Vì vậy câu “nạp lớp qua hai loader khác nhau” **không đủ** để suy ra có hai kiểu khác nhau.

### Child-first không phải mặc định

`ClassLoader.loadClass(String, boolean)` mặc định tìm lớp đã tải, thử parent hoặc bootstrap, rồi mới gọi `findClass` của loader hiện tại. Đây là chiến lược **parent-first delegation** phổ biến. Một custom loader có thể thay chiến lược tìm kiếm cho phạm vi được phép, chẳng hạn **child-first** khi cần cô lập thư viện của plugin.

Không được cho rằng cứ tạo `new ClassLoader(parent)` là đã cô lập toàn bộ lớp. Nếu nó không tự định nghĩa tên lớp đang xét và parent tìm thấy lớp ấy, nó thường trả về lớp do parent định nghĩa.

## 4. Ví dụ hoàn chỉnh Java 17+: hai class cùng tên, khác kiểu runtime

Ví dụ sau chủ động tạo **hai loader không dùng application loader làm parent** cho lớp mục tiêu. Cả hai đọc **cùng một file `Payload.class`** đã biên dịch; mỗi loader gọi `defineClass` để tạo một Class riêng. Ứng dụng vẫn có bản `Payload` do loader thông thường tải.

Tạo **hai file trong cùng thư mục**.

### File Payload.java

~~~java
public class Payload {
    private static int counter;

    public static int next() {
        return ++counter;
    }

    public String text() {
        return "hello";
    }
}
~~~

Lớp `Payload` có một constructor mặc định public, phương thức instance `text()` và trường static `counter`. Việc đặt tên file `Payload.java` đúng với tên lớp public là bắt buộc.

### File LoaderIdentityDemo.java

~~~java
import java.nio.file.Files;
import java.nio.file.Path;

public class LoaderIdentityDemo {
    static final class IsolatedLoader extends ClassLoader {
        private final byte[] payloadBytes;

        IsolatedLoader(byte[] payloadBytes) {
            super(null); // Parent là bootstrap, tránh ủy quyền về app loader
            this.payloadBytes = payloadBytes.clone();
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals("Payload")) {
                throw new ClassNotFoundException(name);
            }

            return defineClass(
                    name, payloadBytes, 0, payloadBytes.length
            );
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("Payload.class"));

        Class<?> app = Payload.class;
        Class<?> first = new IsolatedLoader(bytes).loadClass("Payload");
        Class<?> second = new IsolatedLoader(bytes).loadClass("Payload");

        ClassLoader delegating = new ClassLoader(
                LoaderIdentityDemo.class.getClassLoader()) { };
        Class<?> delegated = delegating.loadClass("Payload");

        System.out.println("same name: "
                + app.getName().equals(first.getName()));
        System.out.println("app == first: " + (app == first));
        System.out.println("first == second: " + (first == second));
        System.out.println("delegated == app: " + (delegated == app));

        Object value = first.getDeclaredConstructor().newInstance();

        System.out.println("first.isInstance: "
                + first.isInstance(value));
        System.out.println("app.isInstance: "
                + app.isInstance(value));
        System.out.println("second.isInstance: "
                + second.isInstance(value));

        System.out.println("invoke method: "
                + first.getMethod("text").invoke(value));

        try {
            Payload cast = (Payload) value;
            System.out.println(cast.text());
        } catch (ClassCastException exception) {
            System.out.println("cast: ClassCastException");
        }

        System.out.println("app.next: " + Payload.next());
        System.out.println("first.next: "
                + first.getMethod("next").invoke(null));
        System.out.println("app.next again: " + Payload.next());
        System.out.println("first.next again: "
                + first.getMethod("next").invoke(null));
        System.out.println("second.next: "
                + second.getMethod("next").invoke(null));

        System.out.println("unnamed modules equal: "
                + (first.getModule() == second.getModule()));
    }
}
~~~

### Biên dịch và chạy

Dùng JDK 17 trở lên, mở terminal **tại thư mục chứa hai file**:

~~~bash
javac --release 17 Payload.java LoaderIdentityDemo.java
java LoaderIdentityDemo
~~~

Chương trình đọc `Payload.class` bằng đường dẫn tương đối, nên cần chạy từ đúng thư mục có file đó. Không cần framework, dependency hoặc Java agent.

**Stdout chính xác khi chạy như trên với JVM Java 17+ thông thường:**

~~~text
same name: true
app == first: false
first == second: false
delegated == app: true
first.isInstance: true
app.isInstance: false
second.isInstance: false
invoke method: hello
cast: ClassCastException
app.next: 1
first.next: 1
app.next again: 2
first.next again: 2
second.next: 1
unnamed modules equal: false
~~~

### Phân tích kết quả từng nhóm

**1. Cùng tên nhưng không cùng Class.** `app.getName().equals(first.getName())` là `true` vì cả hai có binary name `Payload`. Tuy nhiên `app == first` và `first == second` đều `false` vì ba lớp được định nghĩa bởi ba defining loader khác nhau.

**2. Parent delegation có thể dẫn tới cùng kiểu.** `delegating` là loader mới với parent chính là loader của `LoaderIdentityDemo`. Nó không tự định nghĩa `Payload` nên ủy quyền cho parent; `delegated == app` là `true`.

**3. Đối tượng chỉ là instance của đúng kiểu.** `value` được tạo bằng constructor của `first`. Vì vậy `first.isInstance(value)` đúng, còn `app.isInstance(value)` và `second.isInstance(value)` đều sai.

**4. Reflection không xóa đi danh tính kiểu.** Gọi `first.getMethod("text").invoke(value)` thành công vì phương thức thuộc `first` và receiver cũng thuộc `first`. Nó trả về `String`, một kiểu lõi chia sẻ được. Nhưng ép kiểu `(Payload) value` yêu cầu `Payload` được tham chiếu bởi code ứng dụng, tức bản `app`; JVM phát hiện khác kiểu và ném `ClassCastException`.

**5. Mỗi lớp có static state của riêng mình.** `counter` tồn tại riêng trong `app`, `first` và `second`. Các lời gọi `next()` cho ra các chuỗi độc lập: `1, 2` ở `app`; `1, 2` ở `first`; `1` ở `second`. Điều này phản ánh trạng thái static gắn với từng lớp runtime đã được định nghĩa và khởi tạo.

**6. Unnamed module cũng riêng theo loader.** Trong Java 9+, mỗi class loader có unnamed module tương ứng. Vì vậy `first.getModule() == second.getModule()` là `false` dù hai class có cùng binary name và bytecode.

Ví dụ này không cố gắng định nghĩa lại các lớp có tên thuộc `java.*`. Custom loader vẫn ủy quyền nạp `java.lang.Object` và các lớp Java nền tảng cho bootstrap loader. JVM có hạn chế bảo vệ đối với một số tên/package được phép định nghĩa.

## 5. Tại sao phải gọi defineClass thật sự?

Phương thức `findClass` trong ví dụ chứa:

~~~java
return defineClass(name, payloadBytes, 0, payloadBytes.length);
~~~

Đây là nơi loader **định nghĩa Class runtime từ dữ liệu bytecode**. Tên `name` phải phù hợp với tên nhị phân thực sự trong class file; cố lấy bytes của `Payload` nhưng đổi tên thành `Other` sẽ không tạo ra một lớp `Other` hợp lệ một cách tùy ý. JVM kiểm tra nhất quán và có thể ném `NoClassDefFoundError` hoặc lỗi định nghĩa lớp thích hợp.

Ngược lại, code sau thường **không** tạo kiểu mới:

~~~java
ClassLoader app = Payload.class.getClassLoader();
ClassLoader childA = new ClassLoader(app) { };
ClassLoader childB = new ClassLoader(app) { };

Class<?> a = childA.loadClass("Payload");
Class<?> b = childB.loadClass("Payload");

System.out.println(a == b); // true nếu cùng delegate tới bản Payload của app
~~~

Đây là **đoạn mã minh họa**, giả sử `Payload` đã có trên application class path, và loader ẩn danh không override `loadClass` / `findClass`. `childA` và `childB` là hai đối tượng loader khác nhau, nhưng **cả hai nhận về cùng Class từ parent**.

Một loader cũng không thể tùy ý định nghĩa cùng binary name nhiều lần trong cùng namespace đã sử dụng thành công. Lặp một định nghĩa trái phép có thể gây `LinkageError` vì JVM áp đặt sự nhất quán trong việc định nghĩa lớp.

## 6. Class.isInstance, instanceof, cast và reflection

Giả sử một đối tượng được tạo bằng bản `Plugin` do loader A định nghĩa:

| Thao tác | Ý nghĩa với hai định nghĩa `Plugin` độc lập |
| --- | --- |
| `pluginA.getName().equals(pluginB.getName())` | Có thể `true` |
| `pluginA == pluginB` | `false` |
| `pluginA.equals(pluginB)` | `false`, vì `Class` phản ánh danh tính kiểu |
| `pluginA.isInstance(objectA)` | `true` với đúng đối tượng |
| `pluginB.isInstance(objectA)` | `false` nếu hai kiểu không có quan hệ tương thích thích hợp |
| Cast `(PluginB) objectA` | Thông thường `ClassCastException` |
| `pluginA.isAssignableFrom(pluginB)` | `false` với hai lớp hoàn toàn độc lập |
| `pluginA.getDeclaredConstructor().newInstance()` | Tạo instance đúng lớp do A định nghĩa, nếu constructor được phép gọi |

**Đừng suy luận về khả năng cast chỉ bằng cách so sánh tên.** Thử nghiệm `Class.isInstance(Object)` hoặc `Class.isAssignableFrom(Class<?>)` trực tiếp trên những `Class` cụ thể.

Một class A có thể là subtype của một interface chung `api.Service` do parent loader định nghĩa, và một class B khác cũng có thể implement **chính interface chung ấy**. Khi đó cả hai object có thể cast sang **`api.Service` chung**, dù hai implementation không cast được lẫn nhau. Đây là thiết kế quan trọng cho hệ thống plugin.

### Kiểu thành viên và mảng

Tên nhị phân của nested class có thể chứa ký tự `$`, ví dụ `example.Outer$Inner`. Quy tắc danh tính kiểu vẫn sử dụng **toàn bộ binary name + defining loader**. Ký tự `$` tự nó không biểu thị một namespace loader khác.

Với kiểu mảng, JVM tạo array class theo cơ chế đặc biệt: `getClassLoader()` của mảng reference tương ứng với loader của element type. Ví dụ nếu hai phiên bản `Plugin` thuộc hai loader khác nhau, hai kiểu mảng `Plugin[]` tương ứng cũng không phải cùng kiểu runtime. Mảng có element primitive như `int[]` được bootstrap biểu diễn với loader `null` theo API.

## 7. Hai bộ static state, hai lượt class initialization

Mỗi class runtime có trạng thái trường static và chu trình khởi tạo riêng. Trong ví dụ, một tên `Payload` được định nghĩa ba lần nên tồn tại ba biến `counter` khác nhau.

Xét một lớp minh họa:

~~~java
public class SessionCounter {
    private static int count = initialize();

    private static int initialize() {
        System.out.println("Initializing SessionCounter");
        return 0;
    }

    public static int next() {
        return ++count;
    }
}
~~~

Nếu loader A và B thực sự định nghĩa hai bản `SessionCounter` từ cùng bytecode, mỗi bản có thể chạy class initializer `<clinit>` của chính mình khi được sử dụng tích cực lần đầu. Như vậy log `Initializing SessionCounter` có thể xuất hiện **một lần cho A và một lần cho B**. Hai chuỗi đếm `count` là độc lập.

Phân biệt đúng hai mốc:

- **Load/link** class không bắt buộc thực thi static initializer.
- **Initialize** thực hiện quy trình khởi tạo lớp theo quy định, tối đa một lần khởi tạo thành công đối với mỗi Class runtime.
- Tạo nhiều instance của cùng Class không làm `<clinit>` chạy lại.
- Định nghĩa class mới qua loader khác có thể có static state và quá trình khởi tạo riêng.

Điều này ảnh hưởng đến singleton static, cache static, logger registry, JDBC driver registry, thread-local registry hoặc thư viện có cờ `initialized`. Một “singleton” dựa trên trường static thực tế thường chỉ duy nhất **trong phạm vi một định nghĩa class**, không phải một singleton tuyệt đối cho toàn JVM khi có nhiều loader.

## 8. Runtime package cũng phụ thuộc vào loader

Ngoài type identity, defining loader còn tham gia xác định **runtime package**: hai class cùng tên package trong source nhưng do loader khác nhau định nghĩa không mặc nhiên có quyền truy cập package-private của nhau. Ví dụ truy cập package-private được kiểm tra ở bài chuyên về runtime package; điểm cần nhớ ở đây là **cùng source package không bảo đảm cùng runtime package**.

## 9. Phân biệt ClassNotFoundException, ClassCastException và LinkageError

Những lỗi này xảy ra ở các khâu khác nhau:

| Hiện tượng | Nguyên nhân thường gặp |
| --- | --- |
| `ClassNotFoundException` | API tìm theo tên không tìm được class. |
| `ClassCastException` | Object không tương thích với runtime type đích, kể cả khi tên class trùng. |
| `NoClassDefFoundError` | JVM không định nghĩa được class cần khi liên kết/chạy hoặc class initialization trước đó thất bại. |
| `LinkageError` | Vi phạm quy tắc liên kết, chẳng hạn loading constraint yêu cầu cùng kiểu. |

Hai định nghĩa cùng tên **không tự động** gây `LinkageError`; hãy xác định thao tác phát sinh lỗi trước khi xử lý.

## 10. Thiết kế plugin đúng cách với API chung

Một hệ thống plugin cần phân biệt **API contract dùng chung** với **implementation riêng từng plugin**. Host và các loader plugin cùng tham chiếu một interface `api.Plugin` đã được định nghĩa bởi shared/parent loader, trong khi mỗi plugin có thể tự định nghĩa `impl.*`. Nếu mỗi plugin tự nạp bản sao `api.Plugin` của riêng mình, phép cast sang interface của host vẫn có thể thất bại.

Khi cần cô lập các model không thể chia sẻ trực tiếp, chuyển đổi sang DTO thuộc API chung hoặc giao thức dữ liệu độc lập. Bài thiết kế ranh giới nhiều loader triển khai các cấu hình parent-first/child-first, ví dụ chạy được, versioning và vòng đời.

## 11. Các trường hợp có thể thấy cùng loader nhưng khác kết quả tìm lớp

### Một loader có thể biết nhiều Class, nhưng không đồng nghĩa tự định nghĩa tất cả

Trong Java, khi gọi `ChildLoader.loadClass(name)`, kết quả có thể là Class do parent định nghĩa. Trong trường hợp đó loader con tham gia quá trình nạp nhưng `Class.getClassLoader()` không trỏ về nó.

### Một loader không định nghĩa hai phiên bản cùng tên song song

Nếu muốn tải phiên bản mới của cùng binary name trong một plugin có bộ nạp riêng, thường tạo một **loader mới** và điều phối chuyển giao vòng đời. Cố gọi `defineClass` lần hai với cùng tên trong loader cũ không phải cách hot reload hoạt động.

### Cùng file class không phải điều kiện “cùng Class”

Hai loader đọc đúng cùng `Payload.class` vẫn có thể định nghĩa hai Class runtime độc lập. Ngược lại, hai đường class path khác nhau cùng tham chiếu một Class đã có thể cho cùng đối tượng Class nhờ ủy quyền.

### Tên lớp và nội dung bytecode đều có giới hạn

JVM yêu cầu class file hợp lệ và ràng buộc tên nhất quán. Hai loader có thể định nghĩa các bytecode **khác nhau** cho cùng binary name, dẫn tới khác API, dữ liệu hoặc hành vi; ngoài `ClassCastException` còn có thể gặp `NoSuchMethodError`, `NoSuchFieldError` hoặc lỗi liên kết nếu các thành phần kỳ vọng phiên bản không phù hợp.

## 12. Class.forName với loader cụ thể

Khi cần chọn loader, sử dụng overload nhận ba tham số:

~~~java
Class<?> type = Class.forName(
        "com.example.Plugin",
        false,
        pluginLoader
);
~~~

Đây là **đoạn minh họa**, giả sử `pluginLoader` là biến ClassLoader có thật và lớp `com.example.Plugin` tồn tại trong phạm vi tìm kiếm.

- Tham số đầu là **binary name** của lớp thông thường.
- Tham số `false` không yêu cầu khởi tạo lớp ngay tại lời gọi.
- Tham số cuối là loader **được dùng để yêu cầu nạp**; kết quả có thể thuộc parent nếu có ủy quyền.

Khi truyền `true` thay vì `false`, API yêu cầu thực hiện class initialization nếu cần. **Initialization không làm hai định nghĩa của cùng tên trở thành một kiểu**.

Cẩn trọng với lời gọi không truyền loader:

~~~java
Class<?> type = Class.forName("com.example.Plugin");
~~~

Overload này sử dụng class loader của caller theo quy tắc API và có yêu cầu khởi tạo kiểu; trong plugin/container phức tạp, nó có thể không tìm lớp mà một plugin loader riêng có thể tìm được. Không được đồng nhất “loader của caller” với “system class loader” hoặc TCCL trong mọi môi trường.

## 13. Mảng, reflection, serialization và module

### Mảng

Nếu `Class<?> a` và `Class<?> b` là hai lớp reference có cùng binary name nhưng khác defining loader, hai kiểu mảng một chiều của chúng cũng tách biệt. JVM tạo array class theo thành phần được định nghĩa; không có cách cast mảng `A[]` sang `B[]` chỉ bằng việc hai thành phần trùng tên.

### Reflection

Reflection hoạt động trên **đối tượng Class cụ thể**. Muốn gọi phương thức từ kiểu A trên object được A tạo, dùng `a.getMethod(...)` và receiver tương ứng. Nếu lấy `b.getMethod(...)` từ kiểu B rồi áp dụng lên object A không tương thích, lời gọi không tự “hợp nhất” hai định nghĩa; API reflection báo lỗi thích hợp.

### Serialization

Serialization Java truyền thống lưu thông tin về tên lớp và cấu trúc dữ liệu, nhưng khi giải tuần tự hóa phải **phân giải tên thành Class runtime** thông qua cơ chế phân giải lớp. Chọn loader sai có thể dẫn tới không tìm thấy lớp hoặc khác loại; `serialVersionUID` giống nhau không tự xóa ranh giới danh tính kiểu. Với ứng dụng plugin, thường cần giao thức dữ liệu trung lập hoặc quy trình giải mã có chọn loader phù hợp.

### JPMS

Từ Java 9, mỗi Class thuộc một runtime module; gọi `type.getModule()` để quan sát. Hai Class runtime từ hai loader khác nhau có thể thuộc hai unnamed module khác nhau; named modules còn có các quy tắc đọc module (`reads`), xuất package (`exports`), mở package (`opens`) và class loading riêng. Không suy rằng module name bằng nhau tự động làm hai Class của hai loader trở thành cùng kiểu.

### Garbage collection và khả năng dỡ lớp

Một lớp có thể được unload khi đáp ứng điều kiện của JVM, đặc biệt liên quan tới khả năng thu hồi loader đã định nghĩa nó và những tham chiếu còn tồn tại. Giữ tham chiếu lâu dài tới Class, ClassLoader, thread, TCCL, static cache hay object của plugin có thể giữ loader cũ sống và ngăn dỡ các lớp sau reload. Không thể “thay bytecode” trong một Class cũ bằng cách xóa file .class khi loader còn sống.

## 14. Checklist điều tra lỗi “cùng tên mà không cast được”

Khi có `ClassCastException` hoặc lỗi liên kết đáng ngờ, đừng chỉ nhìn chuỗi tên class trong stack trace. Thu thập bằng chứng từ **hai đối tượng Class** thực tế:

~~~java
static void describe(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule());
}
~~~

Đây là **đoạn minh họa trong một lớp Java**, không phải chương trình độc lập.

Các bước xử lý:

1. **Lấy hai Class thực tế:** bên tạo object và bên đang cast/call. Khi có thể, so sánh `left == right`.
2. **In binary name:** `left.getName()` và `right.getName()`. Hai tên có thể giống nhau.
3. **In defining loader:** `left.getClassLoader()` và `right.getClassLoader()`. Chú ý loader của lớp lõi có thể là `null`.
4. **In module:** `getModule()`; dùng thêm nguồn JAR/module hoặc log class loading nếu cần.
5. **Kiểm tra đồ thị loader:** parent-first hay child-first, plugin isolation, app server deployment, TCCL.
6. **Tìm bản sao API/JAR:** API host có bị đóng gói riêng trong plugin? Có class cùng package/name được định nghĩa từ hai vị trí không?
7. **Xác định lỗi đang xảy ra ở bước nào:** tìm lớp, định nghĩa, liên kết, khởi tạo static, reflection hay cast.
8. **Sửa phạm vi chia sẻ kiểu:** thống nhất loader của API chung, thay loader khi hot reload, hoặc dùng DTO/protocol cho ranh giới cách ly.

Trong OpenJDK/HotSpot, có thể bật JVM unified logging để quan sát lớp nào được load và nguồn nào cung cấp dữ liệu:

~~~bash
java -Xlog:class+load=info LoaderIdentityDemo
~~~

Lệnh này áp dụng cho ví dụ sau khi đã biên dịch, và log JVM phụ thuộc phiên bản/môi trường, **không có stdout cố định**. Một số log không cho biết toàn bộ quan hệ initiating/defining loader; kết hợp log với `Class.getClassLoader()`.

## 15. Những ngộ nhận thường gặp

| Ngộ nhận | Điều đúng cần nhớ |
| --- | --- |
| “Cùng tên đầy đủ là cùng kiểu Java” | Runtime type identity dùng binary name **và defining loader** |
| “Hai biến ClassLoader khác nhau luôn cho hai Class khác nhau” | Chúng có thể ủy quyền và nhận cùng Class từ parent |
| “File .class có hash giống nhau nên cast được” | Bytecode giống nhau không vượt qua ranh giới defining loader |
| “`obj.getClass().getName()` giống là cast an toàn” | Dùng `isInstance` / `isAssignableFrom` và định danh Class thực |
| “Đổi TCCL đổi luôn loader đã định nghĩa object” | TCCL chỉ đổi loader ngữ cảnh cho API dùng nó; không sửa defining loader của Class có sẵn |
| “Singleton static luôn duy nhất cho cả JVM” | Static state tách theo Class runtime; mỗi loader định nghĩa riêng có trạng thái riêng |
| “Đưa API JAR vào mọi plugin là tốt” | Dễ tạo nhiều bản API và làm cast phía host thất bại |
| “Lỗi cùng tên chỉ xảy ra khi cast” | JVM còn áp đặt loader constraints, có thể lỗi ở lúc liên kết |
| “Nạp lại file class bằng loader cũ là reload an toàn” | Thường cần loader mới và dỡ bỏ tham chiếu tới loader cũ |
| “Chuỗi `ClassLoader.toString()` xác định toàn bộ kiểu” | Dùng chính đối tượng `Class<?>` và defining loader khi debug |

## 16. Câu hỏi tự kiểm tra

1. **Hai đối tượng loader A và B gọi `loadClass("X")`, đều được parent trả về cùng `Class<?>`. Có hai kiểu X không?** Không; chỉ có một Class được parent định nghĩa trong kịch bản này.
2. **Hai loader A/B gọi `defineClass` hợp lệ từ cùng bytes của X. `AClass.getName().equals(BClass.getName())` có thể true không?** Có; tên bằng nhau nhưng `AClass == BClass` vẫn false.
3. **Một object là instance của X do loader A định nghĩa, có tự nhiên cast sang X do B định nghĩa được không?** Không. Hai kiểu độc lập; cast có thể gây `ClassCastException`.
4. **Static int counter trong hai Class runtime X có dùng chung biến không?** Không. Mỗi định nghĩa class có static state riêng.
5. **Hai class khác loader nhưng cùng package trong source có là cùng runtime package không?** Không, vì runtime package còn phụ thuộc defining loader.
6. **Vì sao host và plugin nên dùng chung một interface API do parent loader định nghĩa?** Để cả hai cùng nhận một kiểu runtime và cast/call qua interface đó được kiểm tra nhất quán.
7. **Nếu in `obj.getClass().getClassLoader()`, ta đang nhìn initiating hay defining loader?** Defining loader.
8. **Nếu class literal `X.class` của host và `loader.loadClass("X")` trả hai Class khác nhau, nên kiểm tra gì?** Kiểm tra binary name, defining loaders, parent chain, path/JAR/module, isolation và có bản sao API hay không.

## Tài liệu chuẩn

- **JVM Specification Java 17, `5.3` – Creation and Loading** (định danh runtime, defining/initiating loader): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVM Specification Java 17, `5.3.4` – Loading Constraints**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4
- **Java Language Specification Java 17, `12.2` – Loading of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2
- **Java SE 17 API, java.lang.ClassLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 API, java.lang.Class**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 API, java.lang.reflect.Method**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-003">So sánh Class, instanceof, isInstance và isAssignableFrom</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh `Class`, `instanceof`, `isInstance` và `isAssignableFrom`

> **Ngữ cảnh:** Java Core → ClassLoader → Định danh kiểu. Mục tiêu của bài là biết chính xác đang hỏi về **hai runtime type**, **một object và một runtime type**, hay **một tên class**; từ đó chọn phép so sánh phù hợp, hiểu ảnh hưởng của **defining class loader**, và tránh nhầm lẫn với generic types, primitive conversions, array covariance. Ví dụ dùng Java 17 trở lên, trừ chỗ ghi rõ khác.

## 1. Bốn công cụ đang trả lời những câu hỏi khác nhau

Giả sử `Dog extends Animal`, và một biến `value` đang giữ object `new Dog()`.

| Cú pháp/API | Câu hỏi nó trả lời | Kết quả với `Dog` và `Animal` |
| --- | --- | --- |
| `value.getClass() == Dog.class` | **Runtime class chính xác** của object có đúng là `Dog` không? | `true` |
| `value instanceof Animal` | Object không-null có thuộc kiểu `Animal` (kể cả subtype) không? | `true` |
| `Animal.class.isInstance(value)` | Tương đương phép thử object-vs-type nhưng type được cung cấp **động** bằng `Class<?>`? | `true` |
| `Animal.class.isAssignableFrom(Dog.class)` | Giá trị có runtime type `Dog` có thể **gán bằng widening reference** cho biến kiểu `Animal` không? | `true` |
| `Dog.class.isAssignableFrom(Animal.class)` | Một đối tượng *bất kỳ* có static type `Animal` có bảo đảm gán cho `Dog` không? | `false` |
| `Dog.class == Animal.class` | Hai runtime type có phải đúng cùng một `Class` không? | `false` |

**Câu ghi nhớ:** Với `T.class.isAssignableFrom(S.class)`, hãy đọc **“S gán vào T được không?”**, tức target `T` nằm **bên trái**. Với `T.class.isInstance(object)`, hãy đọc **“object là một instance của T không?”**. Với `==` giữa các `Class`, ta hỏi **“có phải đúng cùng runtime type không?”**, không tính polymorphism.

Minh họa quan hệ:

~~~text
Animal                  object = new Dog()
  ^
  | extends                   |
 Dog                          v
  |                 getClass() == Dog.class  --> true
  +----------------> object instanceof Animal --> true

Animal.class.isAssignableFrom(Dog.class)  --> true
Dog.class.isAssignableFrom(Animal.class)  --> false
~~~

Các thao tác nói trên thuộc **kiểm tra kiểu ở runtime**. Chúng không phải cơ chế so sánh dữ liệu nghiệp vụ (`equals` của object), và không thay thế việc compiler kiểm tra Java source code trước khi chương trình chạy.

## 2. `Class<?>` biểu diễn **kiểu runtime**, không chỉ là chuỗi tên

Mỗi class/interface đã được JVM định nghĩa có một đối tượng `java.lang.Class` biểu diễn nó trong VM. Các dạng khác cũng có `Class` tương ứng, như array, primitive và `void`:

~~~java
Class<String> textType = String.class;
Class<Integer> boxedType = Integer.class;
Class<?> primitiveType = int.class;
Class<?> arrayType = String[].class;
Class<?> voidType = void.class;
~~~

Những cách lấy `Class`:

- `MyType.class`: class literal, type được viết trong mã Java, không đòi hỏi tạo instance.
- `someObject.getClass()`: runtime class **chính xác** của object đang có; `someObject` là `null` thì `NullPointerException`.
- `Class.forName("pkg.MyType")`: tìm class bằng tên qua loader của caller, **có thể kích hoạt initialization** theo overload mặc định.
- `Class.forName(name, false, loader)`: tìm theo loader đã chọn mà **không yêu cầu** initialization lúc đó.
- `loader.loadClass(name)`: nhận `Class<?>` từ loader; kết quả có thể do một parent **định nghĩa**.

### 2.1. Tên class không đủ xác định runtime type

Với class/interface thông thường, JVMS xác định identity theo:

`(binary name, defining class loader)`

Một tên như `com.example.Plugin` có thể được định nghĩa bởi hai loader khác nhau; hai đối tượng `Class` tương ứng **không bằng nhau**, dù chúng đọc bytecode giống hệt nhau. Nếu hai loader chỉ **ủy quyền về cùng parent**, chúng có thể trả về **chính cùng `Class`**.

Vì thế:

~~~java
left.getName().equals(right.getName())
~~~

không suy ra:

~~~java
left == right
~~~

và cũng không suy ra:

~~~java
left.isAssignableFrom(right)
~~~

Đây là trung tâm của mọi lỗi “`X cannot be cast to X`” ở plugin/container. Thêm thông tin `getClassLoader()`, `getModule()`, runtime package và dependency provenance khi debug; đừng chỉ in `getName()`.

### 2.2. Khi nào dùng `==`, `equals` và `getName`?

Với hai biến `Class<?> left, right`:

- `left == right` kiểm tra **chính xác cùng đối tượng `Class`**. Dùng khi cần strict runtime identity.
- `left.equals(right)` có cùng ý nghĩa identity trong `Class` (không so sánh chỉ tên); `Class` không định nghĩa value equality dựa trên `getName()`.
- `left.getName().equals(right.getName())` so sánh **text binary name** (hoặc dạng tên đặc thù của array/hidden class), không chứng minh compatibility.
- `left.getSimpleName()` và `getCanonicalName()` có thể trùng hoặc vắng ở local/anonymous/hidden classes; chúng cũng không phải khóa định danh đáng tin cho plugin.

**Không dùng:** `clazz.toString().contains("...")` để quyết định assignability. Mã nghiệp vụ phải biểu diễn hợp đồng bằng type hierarchy hoặc explicit plugin metadata, không parse tên class.

## 3. `instanceof`: phép thử đối tượng với kiểu tham chiếu

Cú pháp Java 17:

~~~java
Object value = "hello";

boolean isText = value instanceof String;      // true
boolean isNumber = value instanceof Number;    // false
boolean isObject = value instanceof Object;    // true
boolean nullMatches = (null instanceof Object); // false
~~~

Điều cần nhớ:

1. Operand bên trái được tính như **một biểu thức**; nếu giá trị là `null` thì phép thử kiểu tham chiếu luôn trả `false`, không ném `NullPointerException`.
2. Nếu non-null, runtime kiểm tra object có compatible với reference type bên phải theo ngữ nghĩa Java (class, interface hoặc array) hay không.
3. Các subtype được chấp nhận. `instanceof Animal` đúng cho `Dog` nếu `Dog extends Animal`; nó **không** đòi runtime class chính xác bằng `Animal.class`.
4. Kiểu vế phải phải hợp lệ ở thời điểm **biên dịch**. Java 17 không cho bạn truyền động một biến `Class<?>` vào cú pháp `instanceof`.
5. Compiler cũng kiểm tra khả năng cast hợp lệ. Ví dụ `String s = "x"; boolean b = s instanceof Integer;` là lỗi biên dịch với Java 17 vì hai kiểu final không liên hệ kiểu phù hợp; đổi `s` thành `Object` thì phép kiểm tra runtime hợp lệ và trả `false`.

### 3.1. Pattern matching cho `instanceof` từ Java 16

~~~java
static String describe(Object value) {
    if (value instanceof String text) {
        return "text length=" + text.length();
    }
    if (value instanceof Number number) {
        return "number=" + number;
    }
    return "other";
}
~~~

Đây là **method minh họa** được đặt bên trong một class, không phải tệp độc lập. Biến pattern `text` chỉ có thể dùng ở các phạm vi mà compiler xác định phép thử đã thành công. Đặc tính null và runtime assignability vẫn như `instanceof` truyền thống: `null` không match pattern.

**Chọn `instanceof` khi:** kiểu mục tiêu đã biết tại compile time và bạn đang phân nhánh theo kiểu object. Nếu chỉ cần gọi hành vi đa hình, một interface/virtual method thường rõ hơn chuỗi dài `if (obj instanceof ...)`.

### 3.2. `instanceof` khác ép kiểu

~~~java
Object candidate = "hello";
System.out.println(candidate instanceof Integer); // false

// ((Integer) candidate) biên dịch được nhưng sẽ ném ClassCastException
// khi chương trình cố chạy ép kiểu này.
~~~

`instanceof` trả `false` cho object không phù hợp, trong khi cast không hợp lệ ném `ClassCastException`. Từng là kiểu `Object` không làm object thực sự đổi thành `Integer`. Tính hợp lệ của code ở compile time cũng không bảo đảm cast sẽ thành công ở runtime.

## 4. `Class.isInstance(Object)`: `instanceof` với kiểu mục tiêu được chọn động

Chữ ký API:

~~~java
public boolean isInstance(Object obj)
~~~

Giả sử người dùng hoặc plugin chọn được `Class<?> selectedType` từ registry. Khi đó:

~~~java
boolean accepted = selectedType.isInstance(value);
~~~

là tương đương kiểm tra `value instanceof SelectedType` **nếu ta có thể viết `SelectedType` trong mã nguồn** (đối với phép thử kiểu tham chiếu). API phản ánh hierarchy runtime của classes, interfaces và arrays.

Tính chất:

- `String.class.isInstance("hi")` → `true`.
- `CharSequence.class.isInstance("hi")` → `true`.
- `Number.class.isInstance("hi")` → `false`.
- `String.class.isInstance(null)` → `false`; null là giá trị cho phép, không ném exception.
- `int.class.isInstance(Integer.valueOf(1))` → `false`. Primitive class không có object instance mang kiểu primitive; wrapper object có runtime class là `Integer`.
- `String[].class.isInstance(new String[2])` → `true`.
- `Object[].class.isInstance(new int[2])` → `false` (primitive array không phải `Object[]`).

**Trường hợp dùng:** framework cần kiểm tra object theo type cấu hình runtime, annotation metadata, SPI, serializer hoặc plugin contract. Nếu đã có `Class<T>`, có thể dùng `type.cast(value)` để thực hiện cast runtime với kiểm tra tương ứng; `cast` **ném `ClassCastException`** khi object khác null và không compatible, còn `isInstance` chỉ trả boolean.

### 4.1. Thí nghiệm: dynamic `Class<?>` và `isInstance`

Tệp **`DynamicTypeCheck.java`**:

~~~java
public class DynamicTypeCheck {
    public static void main(String[] args) throws Exception {
        Class<?> requested = Class.forName("java.lang.Number");
        Object first = Integer.valueOf(7);
        Object second = "seven";

        System.out.println("first: " + requested.isInstance(first));
        System.out.println("second: " + requested.isInstance(second));
        System.out.println("null: " + requested.isInstance(null));
        System.out.println("Integer type: "
                + requested.isAssignableFrom(Integer.class));
        System.out.println("String type: "
                + requested.isAssignableFrom(String.class));
    }
}
~~~

~~~bash
javac --release 17 DynamicTypeCheck.java
java DynamicTypeCheck
~~~

Stdout:

~~~text
first: true
second: false
null: false
Integer type: true
String type: false
~~~

Lời giải: `requested` là class token cho `Number`. `Integer` kế thừa `Number`, `String` không kế thừa; kiểm tra object trả true/false tương ứng. Dòng thử `null` luôn `false`. Hai dòng cuối so sánh **hai kiểu**, khác với ba dòng trước so sánh một **kiểu và một object**.

## 5. `Class.isAssignableFrom(Class<?>)`: quan hệ gán giữa **hai kiểu**

Chữ ký:

~~~java
public boolean isAssignableFrom(Class<?> cls)
~~~

Hãy đọc:

~~~java
Target.class.isAssignableFrom(Source.class)
~~~

là câu hỏi: **“Một giá trị có kiểu Source có thể gán cho biến kiểu Target bằng identity/widening reference conversion không?”**

~~~java
Number n = Integer.valueOf(1); // hợp lệ
// Integer i = (Number) ...;    // không được gán ngầm theo chiều ngược
~~~

Tương ứng:

~~~java
Number.class.isAssignableFrom(Integer.class); // true
Integer.class.isAssignableFrom(Number.class); // false
Object.class.isAssignableFrom(String.class);  // true
CharSequence.class.isAssignableFrom(String.class); // true
Runnable.class.isAssignableFrom(String.class); // false
String.class.isAssignableFrom(String.class);  // true
~~~

Mọi type đều assignable từ **chính nó**, kể cả interface, array, primitive. Một interface ở đích có thể nhận class implements nó; một class ở đích có thể nhận subtype. Hai interface có quan hệ `extends` được kiểm tra đúng chiều; hai interface không có quan hệ rõ ràng **không** tự tương thích chỉ vì có một class tình cờ implements cả hai.

**Chiều dễ nhầm:**

~~~java
Animal.class.isAssignableFrom(Dog.class) // true: Dog → Animal
Dog.class.isAssignableFrom(Animal.class) // false: Animal → Dog không tự động
~~~

Tên `isAssignableFrom` mô tả **receiver**: class ở vế trái phải “nhận được gán từ” đối số. Đây là phép thử **type-level**, hoàn toàn không cần tạo object.

### 5.1. `isAssignableFrom` và `isInstance` liên hệ thế nào?

Với object `obj != null`, khi biết `source = obj.getClass()`:

~~~java
target.isInstance(obj)
target.isAssignableFrom(obj.getClass())
~~~

có cùng kết quả về khả năng chuyển đổi reference trong các trường hợp class/interface/array thông thường. Nhưng **không thể thay thế một cách máy móc**:

- `obj == null`: `target.isInstance(null)` trả `false`, còn `obj.getClass()` ném `NullPointerException`.
- `isAssignableFrom` cần đầu vào là `Class<?>`, không phải object.
- `isInstance` cần object, không nhận một primitive value trực tiếp mà không boxing.
- Cả hai đều kiểm tra runtime class identity; tên binary giống nhau ở hai defining loaders **không** biến kết quả thành true.

Nếu `cls == null`, lời gọi `target.isAssignableFrom(null)` ném `NullPointerException` theo API, không trả false. Tính chất này khác với `target.isInstance(null)`.

### 5.2. Bảng chân trị điển hình

| Target ở bên trái | Source ở bên phải | `target.isAssignableFrom(source)` | Lý do |
| --- | --- | --- | --- |
| `Object.class` | `String.class` | `true` | `String` là subtype của `Object` |
| `Number.class` | `Integer.class` | `true` | Inheritance |
| `Integer.class` | `Number.class` | `false` | Không widening reference theo chiều này |
| `CharSequence.class` | `String.class` | `true` | `String implements CharSequence` |
| `Runnable.class` | `String.class` | `false` | `String` không implements `Runnable` |
| `Object.class` | `Object.class` | `true` | Cùng một type |
| `Object.class` | `int.class` | `false` | Không tự thực hiện boxing |
| `long.class` | `int.class` | `false` | Không thực hiện primitive widening |
| `int.class` | `int.class` | `true` | Chính cùng primitive type |
| `Object.class` | `int[].class` | `true` | Array là reference object |
| `Object[].class` | `int[].class` | `false` | Primitive array không phải object array |

**Không suy luận ngược:** `target.isAssignableFrom(source) == false` không có nghĩa ta *không bao giờ* cast được một object có compile-time type source sang target. Ví dụ một biến `Animal` có runtime object thực sự là `Dog` có thể cast sang `Dog` sau kiểm tra runtime; nhưng `Dog.class.isAssignableFrom(Animal.class)` vẫn là false vì không đúng cho mọi `Animal`.

## 6. Một ví dụ chạy đầy đủ để so sánh cả bốn phép kiểm tra

Tệp **`TypeCheckComparison.java`**:

~~~java
public class TypeCheckComparison {
    interface Tagged { }

    static class Animal { }

    static class Dog extends Animal implements Tagged { }

    static class Cat extends Animal { }

    public static void main(String[] args) {
        Object pet = new Dog();
        Object absent = null;

        System.out.println("exact Dog: "
                + (pet.getClass() == Dog.class));
        System.out.println("exact Animal: "
                + (pet.getClass() == Animal.class));

        System.out.println("instanceof Animal: "
                + (pet instanceof Animal));
        System.out.println("instanceof Tagged: "
                + (pet instanceof Tagged));
        System.out.println("instanceof Cat: "
                + (pet instanceof Cat));
        System.out.println("null instanceof Animal: "
                + (absent instanceof Animal));

        System.out.println("isInstance Animal: "
                + Animal.class.isInstance(pet));
        System.out.println("isInstance Cat: "
                + Cat.class.isInstance(pet));

        System.out.println("Animal <- Dog: "
                + Animal.class.isAssignableFrom(Dog.class));
        System.out.println("Dog <- Animal: "
                + Dog.class.isAssignableFrom(Animal.class));
        System.out.println("Tagged <- Dog: "
                + Tagged.class.isAssignableFrom(Dog.class));
        System.out.println("Dog <- Dog: "
                + Dog.class.isAssignableFrom(Dog.class));
    }
}
~~~

~~~bash
javac --release 17 TypeCheckComparison.java
java TypeCheckComparison
~~~

Stdout:

~~~text
exact Dog: true
exact Animal: false
instanceof Animal: true
instanceof Tagged: true
instanceof Cat: false
null instanceof Animal: false
isInstance Animal: true
isInstance Cat: false
Animal <- Dog: true
Dog <- Animal: false
Tagged <- Dog: true
Dog <- Dog: true
~~~

Vì `pet` tham chiếu `new Dog()`, `pet.getClass()` là **chính xác `Dog.class`**. Điều đó không ngăn `Dog` trở thành instance của superclass `Animal` và interface `Tagged`. `Cat` là sibling subtype, không tương thích với `Dog`. Phép `isAssignableFrom` ở bốn dòng cuối không đọc `pet`: nó kiểm tra static relation giữa type tokens.

## 7. Null: bốn cách nhìn phải tách bạch

| Thao tác | Với `null` | Kết quả |
| --- | --- | --- |
| `null instanceof String` | Biểu thức `null` là null reference | `false` |
| `String.class.isInstance(null)` | Đối số object null | `false` |
| `String.class.cast(null)` | Cast null tới reference type | Trả về `null` |
| `((Object) null).getClass()` | Gọi instance method trên null | `NullPointerException` |
| `String.class.isAssignableFrom(null)` | Đối số `Class<?>` là null | `NullPointerException` |
| `String.class == null` | Class literal không null | `false` |

Lưu ý `Class<?> target = null; target.isInstance(value)` cũng ném `NullPointerException` vì **receiver** null, không phải vì `value`.

Trong validator động, kiểm tra kiểu thường cần phân biệt “`null` được chấp nhận theo business rule” với “đối tượng có đúng type không”:

~~~java
static boolean matches(Class<?> target, Object value, boolean nullable) {
    if (target == null) {
        throw new IllegalArgumentException("target required");
    }
    return value == null ? nullable : target.isInstance(value);
}
~~~

Đây là **method minh họa**, không phải tệp Java độc lập. `isInstance(null)` mặc định là false là quy tắc Java; việc chấp nhận null là **chính sách ứng dụng**, cần xử lý riêng.

## 8. Interface, abstract class và polymorphism

Các phương thức kiểm tra class/interface không yêu cầu target phải là concrete class:

~~~java
interface Service { }
abstract class BaseService implements Service { }
final class LiveService extends BaseService { }
~~~

Trong ngữ cảnh các khai báo ở trên:

~~~java
Service.class.isAssignableFrom(LiveService.class);     // true
BaseService.class.isAssignableFrom(LiveService.class); // true
LiveService.class.isAssignableFrom(BaseService.class); // false
~~~

`Service.class.isInstance(new LiveService())` → `true`, dù không thể tạo đối tượng trực tiếp của interface `Service`. Một class có thể implements nhiều interface; quan hệ assignability theo **kiểu**, không theo việc target tự tạo instance được hay không.

Với interface `A` và `B` không có `extends` giữa chúng, dù có `class C implements A, B`, `A.class.isAssignableFrom(B.class)` vẫn là `false`. Nhưng `A.class.isInstance(new C())` và `B.class.isInstance(new C())` đều `true`.

Tránh dùng `getClass() == BaseService.class` để kiểm tra “có phải một dịch vụ loại BaseService không?”, vì mọi subclass bị loại. Nếu muốn chấp nhận subclass, dùng `instanceof` hoặc `isInstance`.

## 9. Arrays: covariance, marker interfaces và primitive arrays

Array trong Java là **reference type**, nhưng phải xét **component type**.

### 9.1. Object array có covariance theo component reference type

~~~java
Object[].class.isAssignableFrom(String[].class); // true
Number[].class.isAssignableFrom(Integer[].class); // true
String[].class.isAssignableFrom(Object[].class); // false
~~~

Bởi vì `String` là subtype của `Object` và `Integer` là subtype của `Number`, quan hệ reference component cho phép covariance ở array. Quy tắc này không kéo theo việc ghi bất kỳ object nào vào mảng:

~~~java
Object[] data = new String[1];
// Dòng sau biên dịch nhưng ném ArrayStoreException tại runtime:
// data[0] = Integer.valueOf(10);
~~~

Array vẫn ghi nhớ runtime component type `String`. Compile-time type `Object[]` không cho phép ghi đè giới hạn runtime đó.

### 9.2. Primitive arrays không phải `Object[]`

~~~java
Object.class.isAssignableFrom(int[].class);       // true
Cloneable.class.isAssignableFrom(int[].class);    // true
java.io.Serializable.class.isAssignableFrom(int[].class); // true
Object[].class.isAssignableFrom(int[].class);     // false
int[].class.isAssignableFrom(long[].class);       // false
~~~

Mọi array class kế thừa `Object` và implements marker interfaces `Cloneable`, `Serializable`. Nhưng `int[]` là một mảng chứa **primitive int**, không phải `Object[]`, và primitive array có component type khác nhau không covariance như reference arrays.

### 9.3. Ví dụ runnable

Tệp **`ArrayTypeChecks.java`**:

~~~java
import java.io.Serializable;

public class ArrayTypeChecks {
    public static void main(String[] args) {
        Object textArray = new String[2];
        Object numbers = new int[2];

        System.out.println(textArray instanceof Object[]);
        System.out.println(textArray instanceof Cloneable);
        System.out.println(Object[].class.isInstance(numbers));
        System.out.println(Object.class.isAssignableFrom(int[].class));
        System.out.println(Cloneable.class.isAssignableFrom(int[].class));
        System.out.println(Serializable.class.isAssignableFrom(int[].class));
        System.out.println(Number[].class.isAssignableFrom(Integer[].class));
        System.out.println(Object[].class.isAssignableFrom(int[].class));
        System.out.println(int[].class.isAssignableFrom(long[].class));
    }
}
~~~

~~~bash
javac --release 17 ArrayTypeChecks.java
java ArrayTypeChecks
~~~

Stdout theo thứ tự:

~~~text
true
true
false
true
true
true
true
false
false
~~~

Điểm quan trọng: `int[]` vừa là `Object`, `Cloneable`, `Serializable`, nhưng không phải `Object[]`. Không dùng suy luận “mảng nào cũng Object[]” khi viết APIs nhận dữ liệu động.

## 10. Primitive, wrapper, boxing và widening: vì sao kết quả khác phép gán trong Java source?

Đây là nơi tên `isAssignableFrom` dễ gây ngộ nhận nhất. Phương thức này kiểm tra **identity/widening reference conversion**, hoặc **chính cùng primitive type**; nó **không** tự chạy boxing, unboxing, primitive widening hoặc chuỗi chuyển đổi compile-time để biến primitive `int` thành `long`/`Object`.

Ví dụ Java source **hợp lệ**:

~~~java
long wide = 12;     // primitive widening: int -> long
Object boxed = 12;  // boxing int -> Integer, sau đó widening reference
Number n = 12;      // boxing int -> Integer -> Number
~~~

Nhưng các kiểm tra type token:

~~~java
long.class.isAssignableFrom(int.class);       // false
Object.class.isAssignableFrom(int.class);     // false
Number.class.isAssignableFrom(int.class);     // false
Integer.class.isAssignableFrom(int.class);    // false
int.class.isAssignableFrom(int.class);        // true
Object.class.isAssignableFrom(Integer.class); // true
Number.class.isAssignableFrom(Integer.class); // true
~~~

`int.class.isInstance(Integer.valueOf(12))` → `false`, vì `Integer` là một **reference object**, không phải primitive value object của `int.class`. Trái lại `Integer.class.isInstance(Integer.valueOf(12))` → `true`.

**Không mở rộng cách hiểu `isAssignableFrom` thành “mọi phép gán viết được bằng Java code”.** Compiler có các quy tắc conversion riêng (boxing/unboxing, widening primitive, constant narrowing trong một số trường hợp); `Class.isAssignableFrom` không mô phỏng tất cả.

## 11. Generics và type erasure: `Class` không mang `List<String>` runtime token

Phần lớn generic type arguments không được reified như một runtime class riêng:

~~~java
java.util.List<String> names =
        new java.util.ArrayList<>();
java.util.List<Integer> ages =
        new java.util.ArrayList<>();

System.out.println(names.getClass() == ages.getClass()); // true
System.out.println(java.util.List.class.isInstance(names)); // true
System.out.println(java.util.List.class.isInstance(ages));  // true
~~~

Ba câu in trên thuộc **một đoạn minh họa** có thể đặt trong `main`, không phải tệp độc lập.

- `List.class` đại diện **raw class/interface** tại runtime, không có `List<String>.class`.
- Không thể dùng `value instanceof List<String>` trong Java 17 vì kiểu tham số hóa cụ thể không reifiable; `value instanceof List<?>` thì hợp lệ.
- `List.class.isAssignableFrom(ArrayList.class)` trả `true` cho hierarchy raw types, không kiểm tra phần tử là `String` hay `Integer`.
- `Class<?>` không phân biệt `List<String>` và `List<Integer>` từ object thực; generic signature có thể lưu trong **metadata của khai báo** (ví dụ `Field.getGenericType()` trả `Type`), không biến thành runtime element-type validation.

Nếu một serializer/validator cần phân biệt `List<String>` và `List<Integer>`, hãy truyền token của **kiểu đầy đủ** bằng cơ chế như `ParameterizedType`/type reference, rồi **kiểm tra từng phần tử** khi chính sách đòi hỏi. Đừng suy ra generic argument runtime chỉ từ `getClass()` của `ArrayList`.

**Điểm phân biệt:** Array có runtime component type thật (ví dụ `String[]`), nên store check tạo `ArrayStoreException`. Generic `List<String>` không kiểm tra runtime type argument theo cùng kiểu array covariance.

## 12. Tác động ClassLoader: hai lớp **cùng tên** vẫn không tương thích

Khi một plugin loader và một host loader định nghĩa class có cùng binary name, mỗi lớp là runtime type khác. Khi đó:

~~~java
hostType.getName().equals(pluginType.getName()); // có thể true
hostType == pluginType;                          // false
hostType.isAssignableFrom(pluginType);           // thường false
hostType.isInstance(pluginObject);               // thường false
~~~

Các kết quả assignability/instance còn tùy hierarchy thực của plugin class; trường hợp minh họa dưới đây chủ ý chỉ dùng hai bản riêng biệt của cùng một class **không có inheritance nối hai bản**.

### 12.1. Ví dụ hai defining loaders, thực thi với Java 17+

Tạo hai file cạnh nhau.

**`Widget.java`**:

~~~java
public class Widget {
    public Widget() { }
}
~~~

**`LoaderIdentityDemo.java`**:

~~~java
import java.io.IOException;
import java.io.InputStream;

public class LoaderIdentityDemo {
    static final class IsolatedWidgetLoader extends ClassLoader {
        IsolatedWidgetLoader() {
            super(LoaderIdentityDemo.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals("Widget")) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    result = findClass(name);
                }
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals("Widget")) {
                throw new ClassNotFoundException(name);
            }
            try (InputStream in = LoaderIdentityDemo.class
                    .getClassLoader().getResourceAsStream("Widget.class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Widget hostObject = new Widget();
        Class<?> hostType = Widget.class;

        Class<?> pluginType =
                new IsolatedWidgetLoader().loadClass("Widget");
        Object pluginObject =
                pluginType.getDeclaredConstructor().newInstance();

        System.out.println("same name: "
                + hostType.getName().equals(pluginType.getName()));
        System.out.println("same Class: " + (hostType == pluginType));
        System.out.println("same defining loader: "
                + (hostType.getClassLoader()
                        == pluginType.getClassLoader()));
        System.out.println("host type accepts plugin: "
                + hostType.isInstance(pluginObject));
        System.out.println("plugin type accepts host: "
                + pluginType.isInstance(hostObject));
        System.out.println("assignable across loaders: "
                + hostType.isAssignableFrom(pluginType));
        System.out.println("plugin accepts own object: "
                + pluginType.isInstance(pluginObject));
        System.out.println("pluginObject instanceof Widget: "
                + (pluginObject instanceof Widget));
    }
}
~~~

~~~bash
javac --release 17 Widget.java LoaderIdentityDemo.java
java -cp . LoaderIdentityDemo
~~~

Stdout:

~~~text
same name: true
same Class: false
same defining loader: false
host type accepts plugin: false
plugin type accepts host: false
assignable across loaders: false
plugin accepts own object: true
pluginObject instanceof Widget: false
~~~

**Tại sao?** Loader tùy biến chọn **định nghĩa riêng** chỉ với `Widget`; với tên còn lại, nó ủy quyền cho parent như thông thường. Khi Java code dùng `Widget.class` hoặc `new Widget()` ở phía host, chúng thuộc application loader. Khi `IsolatedWidgetLoader` đọc `Widget.class` bytes và gọi `defineClass`, VM tạo một type **mới** trong namespace loader mới. Cả hai có cùng `getName()`, nhưng `==`/`isInstance`/`isAssignableFrom` theo runtime identity lại khác nhau.

**Giới hạn demo:** Class loader này chỉ để minh họa identity, không phải implementation plugin production. Plugin thực tế cần thiết kế package ownership, dependency delegation, exception/linking, concurrent loading, resource lookup, module access, lifecycle/unloading và ranh giới trust. Đừng chuyển mọi class thành child-first một cách mù quáng: một shared interface/API từ host thường phải được cả hai phía nhìn thấy dưới **cùng defining loader**.

### 12.2. Lỗi “`Widget cannot be cast to Widget`”

Giả sử plugin tạo `pluginObject`. Cast dưới đây có thể compile vì biến nguồn là `Object` nhưng runtime ném `ClassCastException`:

~~~java
// Widget hostView = (Widget) pluginObject;  // thất bại ở runtime
~~~

Thông báo lỗi đôi khi hiển thị tên lớp trùng nhau kèm thông tin loader/module. Đây không phải nghịch lý: tên giống nhau **không** có nghĩa hai runtime types giống nhau. Cách sửa thường là chia sẻ đúng interface/DTO/contract với parent, hoặc tránh ép cast object xuyên classloader boundary; kiểm tra bản dependency trùng giữa host/plugin và vị trí deploy.

## 13. `Class.cast` và `Class.asSubclass`: khi cần chuyển từ kiểm tra sang thao tác

Hai API liên quan, rất hữu ích trong registry/plugin framework:

~~~java
Class<? extends Number> accepted =
        Integer.class.asSubclass(Number.class);

Number n = accepted.cast(Integer.valueOf(5));
~~~

- `Class.cast(Object)` thực hiện checked cast từ object theo runtime class, **trả `null` nếu tham số null**, hoặc ném `ClassCastException` nếu object không compatible.
- `Class.asSubclass(Class<U>)` xác nhận **một `Class` có phải subtype của U**; nếu có, trả token typed `Class<? extends U>`; nếu không, ném `ClassCastException`.
- `isInstance`/`isAssignableFrom` trả boolean để rẽ nhánh; `cast`/`asSubclass` hữu ích khi cần tiếp tục với kiểu generic tĩnh an toàn hơn.

Ví dụ method generic để filter:

~~~java
static <T> T castOrNull(Class<T> target, Object candidate) {
    return target.isInstance(candidate)
            ? target.cast(candidate)
            : null;
}
~~~

Đây là method minh họa không độc lập. Nếu business domain cho phép candidate null có ý nghĩa khác với “type mismatch”, không nên gộp cả hai tình huống thành `null`; hãy dùng `Optional`, kết quả có trạng thái hoặc kiểm tra null tường minh.

### 13.1. Chọn đúng API trong một registry

Giả sử plugin registry nhận `Class<?> providedType` và contract `Service.class`:

~~~java
if (!Service.class.isAssignableFrom(providedType)) {
    throw new IllegalArgumentException("Expected Service subtype");
}
Class<? extends Service> serviceType =
        providedType.asSubclass(Service.class);
~~~

Đây là **snippet phụ thuộc interface `Service` do project định nghĩa**. Kiểm tra này chỉ xác nhận inheritance/interface identity. Nó **không bảo đảm** class có public constructor, class không abstract, module cho phép reflective invocation, dependency có mặt hay plugin đáng tin cậy.

Khi đã có một instance do plugin trả:

~~~java
if (Service.class.isInstance(pluginObject)) {
    Service service = Service.class.cast(pluginObject);
}
~~~

Các đoạn này phụ thuộc đúng **cùng `Service.class`** qua loader boundary. Nếu host và plugin mỗi bên định nghĩa `Service` riêng, `isAssignableFrom` có thể trả `false` dù tên `Service` trên cả hai phía bằng nhau.

## 14. Các ngộ nhận thường gặp

| Ngộ nhận | Điều đúng |
| --- | --- |
| `getClass() == Parent.class` sẽ nhận mọi subtype | Chỉ true với **đúng runtime class**; dùng `instanceof`/`isInstance` cho subtype |
| `a.getClass().getName().equals(b.getClass().getName())` nghĩa là cùng type | Sai nếu defining class loader khác |
| `Target.isAssignableFrom(Source)` đọc theo “Target có thể gán sang Source” | Sai chiều; phải đọc **“Source có thể gán vào Target”** |
| `isAssignableFrom` mô phỏng tất cả Java assignment conversions | Sai: không boxing/unboxing hay widening primitives |
| `int.class.isInstance(1)` phải true | Sai: khi truyền vào API `Object`, `1` được boxing; primitive `Class` không chứa object instances |
| `List.class.isInstance(list)` xác minh phần tử đều là String | Sai: type erasure |
| `null instanceof T` ném lỗi | Sai: trả false; lỗi xảy ra khi dereference null |
| `isAssignableFrom(null)` trả false | Sai: `NullPointerException` |
| `Object[].class.isAssignableFrom(int[].class)` true vì `int[]` là Object | Sai: primitive array là Object nhưng không phải `Object[]` |
| Hai loader khác nhau luôn tạo hai `Class` khác nhau | Sai nếu cả hai ủy quyền cùng defining loader |
| `instanceof` cũng là so sánh classloader? | Kiểm tra runtime type sử dụng identity do loader xác định; không trực tiếp so sánh object loader như giá trị |
| `Class.forName` luôn không khởi tạo class | Sai: overload một chuỗi mặc định yêu cầu initialization; overload có `initialize=false` có thể tránh yêu cầu ấy |

## 15. Quy trình chẩn đoán khi so sánh kiểu bất ngờ trả `false`

1. **Xác minh object thực:** `obj == null`? Nếu không, in `obj.getClass().getName()` và `obj.getClass().getClassLoader()`.
2. **Xác minh target:** in `target.getName()`, `target.getClassLoader()` và `target.getModule()`, so với source/runtime object.
3. **Kiểm tra chiều assignability:** `Target.class.isAssignableFrom(Source.class)`, tránh viết ngược.
4. **Kiểm tra classpath/plugin graph:** JAR trùng FQCN? Có child-first loader hay module layer mới? Có parent delegation trả về type khác dự kiến?
5. **Kiểm tra array/primitive/generics:** primitive wrapper, boxing/unboxing, `Object[]` vs `int[]` và erased generic arguments.
6. **Kiểm tra compilation vs runtime:** compiler có thể cấm `instanceof` vì cast inconvertible, trong khi API dynamic kiểm tra runtime class token có thể trả false; đây không nhất thiết là bug.
7. **Ưu tiên contract thay vì đoán tên:** xác định một interface/abstract parent chung thực sự được define bởi cùng loader; khi cần kiểm tra exact class dùng `==`, khi cần polymorphism dùng `instanceof`/`isInstance`.

Đoạn log hỗ trợ:

~~~java
static void explain(Class<?> target, Object source) {
    if (source == null) {
        System.out.println("source is null");
        return;
    }
    Class<?> actual = source.getClass();
    System.out.println("actual: " + actual.getName());
    System.out.println("actual loader: " + actual.getClassLoader());
    System.out.println("target: " + target.getName());
    System.out.println("target loader: " + target.getClassLoader());
    System.out.println("exact: " + (actual == target));
    System.out.println("instance: " + target.isInstance(source));
    System.out.println("assignable: " + target.isAssignableFrom(actual));
}
~~~

Method minh họa cần đặt trong class và truyền `target != null`. Khi log trong production, hạn chế in giá trị object/đường dẫn có thể chứa thông tin nhạy cảm; metadata loader được dùng để chẩn đoán quan hệ định danh kiểu.

## 16. Bài tập tự kiểm tra

Với:

~~~java
interface Pet { }
class Animal { }
class Dog extends Animal implements Pet { }
class Cat extends Animal { }
Object dog = new Dog();
~~~

Các khai báo trên được đặt trong cùng phạm vi Java thích hợp.

1. `dog instanceof Animal` → **true**, vì Dog extends Animal.
2. `dog.getClass() == Animal.class` → **false**, vì class chính xác là Dog.
3. `Pet.class.isInstance(dog)` → **true**, vì Dog implements Pet.
4. `Animal.class.isAssignableFrom(Dog.class)` → **true**; đảo chiều → **false**.
5. `Cat.class.isInstance(dog)` → **false**; cùng superclass không khiến hai sibling classes tương thích.
6. `Object[].class.isAssignableFrom(String[].class)` → **true**, nhưng `Object[].class.isAssignableFrom(int[].class)` → **false**.
7. `long.class.isAssignableFrom(int.class)` → **false**, dù `long x = 1` hợp lệ.
8. Nếu `Dog` được define riêng ở hai loaders, `Dog` tokens hai bên cùng `getName()` thì `==` có true không? **Không**.
9. Nếu `Class<?> runtimeType = Dog.class`, nên dùng gì để kiểm tra `dog`? **`runtimeType.isInstance(dog)`**.
10. Nếu chỉ có `Class<?> source, target`, nên kiểm tra gì trước khi nhận loại source? **`target.isAssignableFrom(source)`**.

## 17. Tài liệu chuẩn

- [`java.lang.Class` API (JDK 25)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html) — hợp đồng chính thức cho `isInstance`, `isAssignableFrom`, `getClassLoader`, `cast`, `asSubclass`, primitive và array class tokens.
- [JLS `15.20.2` — Type Comparison Operator `instanceof` (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.20.2) — quy tắc compile time, null và kiểm tra runtime ở Java 17.
- [JLS `5` — Conversions and Contexts (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html) — identity, widening reference, boxing/unboxing và casting.
- [JLS `4.7` — Reifiable Types (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7) — vì sao `List<String>` không thể dùng như runtime token và `List<?>` có thể kiểm tra.
- [JVMS `5.3` — Creation and Loading (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3) — type identity theo **binary name + defining loader**, array classes và initiating/defining loaders.
- [`ClassLoader` API (JDK 25)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html) — delegation và `defineClass`, bối cảnh cho plugin isolation.

**Nhớ ba câu:** `==` giữa `Class` kiểm tra **định danh kiểu chính xác**; `instanceof`/`Class.isInstance` kiểm tra **object có thuộc kiểu (kể cả subtype)**; `Target.class.isAssignableFrom(Source.class)` kiểm tra **quan hệ gán Source → Target**. Cả ba đều dựa trên **runtime type identity**, mà với class thông thường identity bao gồm cả **defining class loader**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-004">ClassCastException khi tên lớp nguồn và đích giống nhau</a>

<details>
<summary>Xem chi tiết</summary>

# ClassCastException khi tên lớp nguồn và đích giống nhau

Thông báo **`ClassCastException`: “class A cannot be cast to class A”** nhìn có vẻ mâu thuẫn: tên kiểu nguồn và kiểu đích giống nhau nhưng phép ép kiểu vẫn thất bại. Trong JVM, định danh của một **class/interface** không chỉ là tên mà là **cặp (binary name, defining class loader)**. Hai bộ nạp lớp có thể độc lập định nghĩa cùng tên `com.example.Plugin` và tạo thành **hai kiểu JVM khác nhau**, dù sử dụng một file `.class` có bytecode giống nhau đến từng byte.

Bài này tập trung vào cơ chế định danh kiểu, cách tái hiện bằng Java 17, phân biệt defining/initiating loader, chẩn đoán stack trace và cách thiết kế ranh giới giữa host, plugin hay application server.

## 1. Vì sao tên giống nhau chưa chắc cùng class?

~~~text
                  com.example.Plugin
                    /           \
                   v             v
             Loader A         Loader B
                   |             |
              Plugin(A)       Plugin(B)
                   \             /
                KHÔNG CÙNG KIỂU
~~~

Giả sử `classA` và `classB` là `Class<?>` do hai loader **thực sự định nghĩa**:

~~~java
classA.getName().equals(classB.getName())  // true
classA == classB                           // false
~~~

Đây là **fragment minh họa**, chưa phải một chương trình độc lập. Các quy tắc nền tảng:

- `Class.getName()` trả tên nhị phân; không đủ chứng minh hai kiểu có cùng identity.
- `Class.getClassLoader()` trả **defining loader** của class. Với nhiều class nền tảng do bootstrap loader định nghĩa, API dùng `null` để biểu diễn loader.
- `Object.getClass()` trả định nghĩa class runtime của object; cast dựa vào kiểu đó và quan hệ inheritance/implements.
- `instanceof`, `Class.isInstance(...)`, `Class.cast(...)` và bytecode `checkcast` **không chỉ so sánh chuỗi tên**.
- Một loader không được định nghĩa hai class riêng có cùng binary name trong namespace của chính nó; nỗ lực định nghĩa trùng không hợp lệ có thể gây `LinkageError`. Lỗi trong bài thường đến từ **hai defining loader** khác nhau.
- Hai loader child khác nhau vẫn có thể trả lại **cùng một `Class<?>`** nếu cả hai ủy quyền cho parent đã định nghĩa class đó. Phải kiểm tra defining loader thật, không chỉ so sánh hai object loader ban đầu.

Với kiểu mảng có phần tử là class tham chiếu, identity của kiểu mảng chịu ảnh hưởng identity kiểu phần tử. Đừng coi `Plugin[]` từ hai định nghĩa `Plugin` khác nhau là một kiểu mảng tương thích.

## 2. Vì sao JVM ném ClassCastException?

Một object tạo từ `Message(A)`, trong khi code đích yêu cầu kiểu `Message(B)`:

~~~text
Loader A                  Loader B
   |                         |
   v                         v
Message(A)              Message(B)
   |                         ^
   v                         |
object ------ cast ----------+
                 |
          ClassCastException
~~~

JVM kiểm tra liệu object có phù hợp với kiểu đích dựa trên quan hệ class/interface đã liên kết hay không. Hai định nghĩa cùng tên từ hai loader độc lập **không tự có quan hệ kiểu**, kể cả khi bytecode hoàn toàn giống nhau.

Ví dụ thực tế: một plugin implements `com.acme.api.Task` do loader của plugin định nghĩa, nhưng host yêu cầu `Task` do loader host định nghĩa. Một object của plugin có thể không cast được sang interface của host. Reflection chỉ giúp tìm/gọi method theo quyền truy cập phù hợp; nó không biến hai định nghĩa kiểu thành cùng một.

## 3. Chương trình Java 17: cùng một bytecode, hai class loader và lỗi cast

Tạo **hai file** trong cùng một thư mục. Chương trình chính sử dụng hai `URLClassLoader` cùng URL trỏ vào thư mục có `Payload.class`, nhưng parent của mỗi loader là `null` nên lớp ứng dụng `Payload` không được ủy quyền cho application loader. Các lớp nền tảng như `java.lang.Object` vẫn có thể do bootstrap loader phục vụ.

**Tệp `Payload.java`:**

~~~java
public class Payload {
    public String value() {
        return "same bytecode";
    }
}
~~~

**Tệp `LoaderCollisionDemo.java`:**

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class LoaderCollisionDemo {
    public static void main(String[] args) throws Exception {
        URL directory = Path.of(".")
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toURL();

        try (URLClassLoader loaderA =
                     new URLClassLoader(new URL[] { directory }, null);
             URLClassLoader loaderB =
                     new URLClassLoader(new URL[] { directory }, null)) {

            Class<?> classA = Class.forName("Payload", true, loaderA);
            Class<?> classB = Class.forName("Payload", true, loaderB);

            Object objectA = classA.getDeclaredConstructor().newInstance();

            System.out.println("Names equal: "
                    + classA.getName().equals(classB.getName()));
            System.out.println("Same Class: " + (classA == classB));
            System.out.println("Loader A defines A: "
                    + (classA.getClassLoader() == loaderA));
            System.out.println("Loader B defines B: "
                    + (classB.getClassLoader() == loaderB));
            System.out.println("A accepts object: "
                    + classA.isInstance(objectA));
            System.out.println("B accepts object: "
                    + classB.isInstance(objectA));

            try {
                classB.cast(objectA);
                System.out.println("Unexpected cast");
            } catch (ClassCastException e) {
                System.out.println("Result: ClassCastException");
            }
        }
    }
}
~~~

Biên dịch/chạy bằng **JDK 17 hoặc mới hơn**, tại thư mục chứa các file:

~~~bash
javac --release 17 Payload.java LoaderCollisionDemo.java
java LoaderCollisionDemo
~~~

**Stdout chính xác:**

~~~text
Names equal: true
Same Class: false
Loader A defines A: true
Loader B defines B: true
A accepts object: true
B accepts object: false
Result: ClassCastException
~~~

Phân tích:

1. Chỉ có **một** `Payload.class` được tạo bởi `javac`, nhưng hai loader độc lập định nghĩa hai bản `Payload` khi nhận yêu cầu.
2. `Class.forName("Payload", true, loaderX)` yêu cầu nạp/khởi tạo class qua loader được truyền. Mỗi bản `Payload` ở đây thuộc defining loader tương ứng.
3. `getName()` cùng là `Payload`, nhưng `classA == classB` bằng `false`; hai object `Class<?>` không có cùng identity.
4. `objectA` do constructor `Payload(A)` tạo, vì vậy `classA.isInstance(objectA)` bằng `true`, còn `classB.isInstance(objectA)` bằng `false`.
5. `classB.cast(objectA)` ném `ClassCastException`. Ví dụ bắt exception để in chuỗi cố định; thông báo JVM gốc có thể chứa thông tin loader/module phụ thuộc môi trường.
6. `try-with-resources` gọi `URLClassLoader.close()`; điều này đóng tài nguyên loader, **không buộc JVM unload class ngay**.

Ví dụ dùng default package và parent `null` để dễ thấy hai namespace độc lập, **không phải cấu hình chuẩn cho mọi plugin**.

### Nếu cast trực tiếp từ code ứng dụng?

Fragment sau (không nằm trong chương trình độc lập trên):

~~~java
Payload p = (Payload) objectA;
~~~

cũng có thể thất bại: `Payload` trong source của ứng dụng được liên kết tới kiểu của application loader, còn `objectA` thuộc `Payload(A)`. Nếu application loader đã nạp `Payload` thì có thể tồn tại **ba** định nghĩa class cùng tên trong process.

## 4. Đọc thông báo “A cannot be cast to A”

Một thông báo trên JVM hiện đại có thể có dạng:

~~~text
class com.acme.Plugin cannot be cast to class com.acme.Plugin
(com.acme.Plugin is in unnamed module of loader LoaderA ...;
 com.acme.Plugin is in unnamed module of loader LoaderB ...)
~~~

Đây là **ví dụ minh họa cấu trúc message**, không phải stdout cố định. Tên loader, địa chỉ, module và format có thể thay đổi theo runtime.

| Thành phần | Cách suy luận |
| --- | --- |
| Hai tên class giống nhau | Binary name giống, chưa chứng minh cùng identity. |
| Hai loader khác nhau | Kiểm tra hai defining loader thực sự của source và target. |
| `unnamed module` | Hai loader có thể có unnamed module riêng, không tự chia sẻ class. |
| Tên module rõ ràng | Giúp định vị layer/module, không thay thế kiểm tra Class identity. |
| Stack trace tại cast | Cho biết nơi object đi vào boundary và bên nhận đang yêu cầu kiểu nào. |

Cùng JAR URL, cùng package, cùng tên module, cùng bytecode hay cùng source file **không đủ chứng minh cùng class**. Ngược lại, nhiều loader con cũng có thể cùng nhận lại class do parent định nghĩa.

## 5. Defining loader và initiating loader khác nhau

- **Defining loader:** loader thực sự định nghĩa class từ ClassFile; nó tham gia identity của kiểu. `Class.getClassLoader()` cho biết loader này.
- **Initiating loader:** loader khởi xướng yêu cầu nạp theo tên; có thể ủy quyền cho parent và nhận class do parent định nghĩa.

~~~text
child.loadClass("com.acme.api.Task")
               |
               v
       child hỏi parent
               |
               v
      parent định nghĩa Task
               |
               v
   child trả lại chính Class<?> của Task
~~~

Trong sơ đồ, child là initiating loader nhưng parent là defining loader. Nếu hai child đều ủy quyền cho cùng parent đã định nghĩa `Task`, **chỉ có một kiểu Task**. Nếu parent không thấy `Task` và mỗi child tự định nghĩa, chúng có hai identity khác nhau.

Một custom loader có thể dùng child-first để cô lập dependency, nhưng cần giữ API contract dùng chung theo cơ chế parent/shared loader. Chỉ so `loaderA.getParent() == loaderB.getParent()` **không đủ** để kết luận hai kiểu được chia sẻ.

`Thread.currentThread().getContextClassLoader()` (TCCL) được một số framework và `ServiceLoader` sử dụng để tìm provider. Thay TCCL **không thay đổi identity** của Class đã được định nghĩa. Nó chỉ tác động đến đường tìm lớp nếu API/framework thật sự dùng context loader.

## 6. Những nơi thường gặp lỗi

### 6.1. Plugin đóng gói bản sao API interface của host

~~~text
Host class loader:
  com/acme/api/Processor.class      ← contract gốc

Plugin class loader:
  com/acme/api/Processor.class      ← bản copy API riêng
  com/acme/plugin/ProcessorImpl.class
~~~

`ProcessorImpl` implements `Processor` từ plugin loader. Host cast object thành `Processor` từ host loader và gặp bất tương thích. **Cách sửa:** Host hoặc parent/shared loader định nghĩa đúng **một** `Processor`; plugin chỉ định nghĩa implementation và dùng đúng interface chung.

### 6.2. Application server hot redeploy

Container có thể tạo class loader mới khi deploy lại. Thread, callback, listener, cache hoặc singleton cũ giữ object tạo bởi loader cũ, sau đó truyền vào code mới:

~~~text
Deploy cũ → LoaderOld → com.app.User → object cũ
Deploy mới → LoaderNew → com.app.User → kiểu mới
                         |
               cast object cũ sang kiểu mới
                         |
                 ClassCastException
~~~

Cần dọn lifecycle của thread/cache/listener/callback khi undeploy, không giữ object thuộc class loader cũ qua ranh giới deployment. Đóng loader không bảo đảm object cũ tự mất ngay.

### 6.3. Trùng thư viện qua dependency/shading

Host và plugin có thể cùng đóng gói một `api.jar` hoặc cùng một model class được ship trong nhiều JAR. Có hai JAR trên đĩa **chưa chắc gây lỗi**; lỗi đến khi hai defining loader thực sự tạo hai class khác nhau và code truyền object qua boundary. Shading/relocation có thể phù hợp cho dependency nội bộ nhưng **không nên tạo hai bản API contract** mà host/plugin phải dùng chung.

### 6.4. Reflection, SPI và proxy

`Class.forName(..., loader)`, `ServiceLoader`, proxy factory và discovery framework chọn loader theo cấu hình/TCCL. Provider có thể triển khai interface do loader khác với consumer định nghĩa; việc phản chiếu thành công chưa bảo đảm cast sang interface consumer. Kiểm tra loader thật dùng để nạp **contract**, provider và proxy.

## 7. Chẩn đoán bằng Class<?> thật

Fragment sau giả sử `value` là object không null và `targetType` là `Class<?>` đích của bên nhận:

~~~java
Class<?> actualType = value.getClass();

System.out.println("Actual name: " + actualType.getName());
System.out.println("Target name: " + targetType.getName());
System.out.println("Same Class: " + (actualType == targetType));

System.out.println("Actual loader: " + actualType.getClassLoader());
System.out.println("Target loader: " + targetType.getClassLoader());

System.out.println("Actual module: " + actualType.getModule());
System.out.println("Target module: " + targetType.getModule());

System.out.println("Compatible: " + targetType.isInstance(value));
System.out.println("Assignable: "
        + targetType.isAssignableFrom(actualType));
~~~

**Không kết luận chỉ bằng `actualType == targetType`**: object có thể là subclass hoặc implementation của interface đích, nên hai Class khác nhau mà cast vẫn hợp lệ. `isInstance(value)` phản ánh tương thích với kiểu đích; `isAssignableFrom(actualType)` cũng có ích để kiểm tra quan hệ type.

Có thể định dạng ID loader tạm trong log:

~~~java
ClassLoader loader = actualType.getClassLoader();
String id = loader == null ? "bootstrap"
        : loader.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(loader));
System.out.println(id);
~~~

Đoạn này là fragment phụ thuộc biến `actualType`. `identityHashCode` tiện cho log nhưng có thể trùng; so sánh tham chiếu loader bằng `==` để kết luận cùng object loader.

Thử xem nguồn class từ `ProtectionDomain` và `CodeSource`:

~~~java
var domain = actualType.getProtectionDomain();
var source = domain == null ? null : domain.getCodeSource();
System.out.println(source == null
        ? "(unknown)"
        : source.getLocation());
~~~

Không phải class nào cũng có `CodeSource` hữu ích: bootstrap class, class sinh bytecode trong memory hoặc loader đặc biệt có thể không cung cấp. **Cùng CodeSource chưa chắc cùng identity**.

### Trình tự xử lý sự cố

1. Giữ đầy đủ stack trace và lấy đúng `Class<?>` của object nguồn lẫn kiểu đích.
2. In `getName()`, `getClassLoader()`, `getModule()`, `isInstance(...)` và so sánh `==`.
3. Tìm nơi object tạo ra: factory, constructor, reflection, proxy, deserialize, plugin callback.
4. Kiểm tra API JAR bị duplicate, chiến lược parent-first/child-first, dependency tree và module layer.
5. Nếu lỗi chỉ xảy ra sau redeploy, kiểm tra cache, thread, callback còn giữ loader/object cũ.
6. Đưa **API contract về một defining loader chung** hoặc chuyển dữ liệu qua boundary trung lập.

## 8. Giải pháp và phản ví dụ

| Tình huống | Giải pháp |
| --- | --- |
| Host không cast được plugin sang interface | API interface do parent/shared loader định nghĩa; plugin không định nghĩa lại. |
| Hai plugin trao đổi model nội bộ | Chia sẻ DTO/interface contract hoặc serialize thành định dạng trung lập. |
| Hai phiên bản thư viện phải cùng tồn tại | Giữ implementation trong loader riêng, chỉ chia sẻ contract cần thiết. |
| Lỗi sau redeploy | Hủy cache/thread/listener/callback giữ object/loader của deployment cũ. |
| Framework nạp provider bằng loader sai | Cấu hình discovery/TCCL/loader nhất quán với contract dùng chung. |
| Dependency class duplicate | Căn chỉnh packaging và xác minh defining loader lúc chạy. |

Không nên chỉ bỏ unchecked-cast warning, cast lại, so tên bằng `getName()`, lạm dụng reflection, đổi TCCL sau khi đã tạo object hoặc đưa toàn bộ thư viện vào bootstrap/system loader. **Các cách này không hợp nhất identity của hai định nghĩa class**.

Nếu cần cast trực tiếp qua boundary, hai phía phải tham chiếu tới **cùng định nghĩa interface/base class phù hợp**. Nếu yêu cầu cô lập hoàn toàn, hãy truyền dữ liệu trung lập và tái tạo object hợp lệ ở phía nhận thay vì ép object implementation thuộc miền A sang class của miền B.

## 9. Hiểu nhầm thường gặp

**Hai loader có cùng parent thì chắc chắn nạp một kiểu?** Sai. Parent phải thực sự cung cấp class; nếu không, mỗi child có thể tự định nghĩa.

**Bytecode giống nhau thì JVM hợp nhất?** Sai. Defining loader là phần bắt buộc của identity.

**Interface không bị lỗi này?** Sai. Một interface `Task` do hai loader định nghĩa là hai kiểu khác nhau; implementation của một bản không tự implements bản kia.

**Generics giúp tránh mọi trường hợp?** Không. Type erasure có thể cho một `List` đi qua boundary, nhưng lúc lấy phần tử rồi cast thành model `Payload` của bên nhận, lỗi mới lộ ra.

**Đóng URLClassLoader có unload ngay class không?** Không. `close()` đóng tài nguyên loader; metadata class và object có thể còn được giữ bởi stack, thread, cache hoặc tham chiếu khác, tùy điều kiện unloading/GC.

**ClassCastException có phải NoClassDefFoundError hoặc LinkageError?** Không. `ClassCastException` xảy ra khi cast runtime không hợp lệ; `NoClassDefFoundError` thường liên quan việc JVM không thể có định nghĩa class cần dùng, còn `LinkageError` là nhóm lỗi khi định nghĩa/liên kết. Một sai cấu hình loader có thể hiện thành các lỗi khác nhau ở những pha khác nhau.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Hai loader A và B đọc cùng `Message.class` rồi đều tự định nghĩa `pkg.Message`. `classA.getName().equals(classB.getName())` bằng `true`. `classA == classB` là gì?

**Đáp án:** `false` vì defining loader khác nhau.

**Câu 2.** Hai child loader cùng ủy quyền nạp `api.Task` cho parent đã định nghĩa `Task`. Có hai kiểu không?

**Đáp án:** Không. Cả hai nhận cùng `Class<?>` do parent định nghĩa.

**Câu 3.** Vì sao nên dùng `targetType.isInstance(value)` chứ không chỉ so tên?

**Đáp án:** Vì nó kiểm tra object có tương thích kiểu đích theo quan hệ superclass/interface thực tế không.

**Câu 4.** Host có interface `Task`; plugin tự mang một `Task` khác cùng tên. Sửa thế nào?

**Đáp án:** Chỉ giữ một định nghĩa API contract tại loader dùng chung, để implementation của plugin implements đúng `Task` của host/parent.

**Câu 5.** Lỗi xuất hiện sau redeploy dù source Java không đổi?

**Đáp án:** Loader mới có thể định nghĩa một kiểu khác; object cũ bị cache giữ vẫn thuộc kiểu của loader cũ, nên không cast được sang kiểu mới.

## Ghi nhớ

**Tên lớp giống nhau không bảo đảm ép kiểu được.** Với class/interface, JVM dùng **(binary name, defining loader)** làm định danh. Khi gặp “A cannot be cast to A”, kiểm tra hai `Class<?>` thực, `getClassLoader()`, `isInstance(...)` và đường đi của object; sau đó chia sẻ **API contract từ một loader chung** hoặc trao đổi qua dữ liệu trung lập.

**Tài liệu chuẩn:**

- *Java Virtual Machine Specification*, Java SE 17 `5.3` — https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- *Java Language Specification*, Java SE 17 `12.2` — https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2
- Java SE 17 API, `ClassLoader` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `Class` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-005">Runtime package được xác định bởi tên package và loader</a>

<details>
<summary>Xem chi tiết</summary>

# Runtime package được xác định bởi tên package và class loader

Trong Java, hai lớp cùng khai báo `package demo;` chưa chắc thuộc **cùng runtime package**. Theo *Java Virtual Machine Specification* (JVMS), runtime package của một class/interface được xác định bằng **tên package và defining class loader** (loader thực sự định nghĩa lớp đó). Vì vậy, nếu hai loader khác nhau định nghĩa các lớp có tên package giống hệt nhau, chúng vẫn thuộc **hai runtime packages khác nhau**.

Quy tắc này rất quan trọng với `classloader`, plugin, application server, bytecode generation và các thư viện dùng nhiều phiên bản dependency, vì nó ảnh hưởng trực tiếp đến quyền truy cập **package-private**, class identity và lỗi liên kết lúc chạy.

## 1. Phân biệt source package và runtime package

Trong mã nguồn Java, khai báo:

```java
package com.example.domain;
```

xác định package name `com.example.domain`. Khi lập trình thông thường, các tệp cùng package có thể truy cập các thành viên package-private của nhau nếu các điều kiện khác cũng phù hợp.

Nhưng JVM phải xử lý các lớp có thể được định nghĩa bởi nhiều class loaders. Do đó nó cần biết không chỉ tên package, mà còn loader đã định nghĩa lớp:

```text
Runtime package identity =
    (package name, defining class loader)
```

Ví dụ:

```text
Class A:
  binary name       = demo.One
  package name      = demo
  defining loader   = Loader-Parent

Class B:
  binary name       = demo.Two
  package name      = demo
  defining loader   = Loader-Child

Runtime package A = (demo, Loader-Parent)
Runtime package B = (demo, Loader-Child)

Kết luận: runtime package A != runtime package B
```

Tuy cả hai đều có lệnh `package demo;`, chúng **không** được JVM coi là cùng runtime package.

Ngược lại, nếu cả hai lớp được **cùng loader định nghĩa**, chúng có cùng package name và cùng runtime package, kể cả khi source file nằm ở hai thư mục dự án hoặc hai JAR khác nhau. Việc chia sẻ package trong cùng loader vẫn có thể bị ràng buộc thêm bởi quy tắc sealing, mô-đun và cấu hình môi trường; không có nghĩa được bỏ qua các quy tắc đó.

### Ba khái niệm cần tách rõ

| Khái niệm | Danh tính/cách xác định | Ví dụ |
|---|---|---|
| **Binary name** | Tên nhị phân của class/interface | `demo.One` |
| **Runtime class** | `(binary name, defining loader)` | `(demo.One, Loader-A)` |
| **Runtime package** | `(package name, defining loader)` | `(demo, Loader-A)` |

Điểm khác biệt quan trọng: hai class **khác tên** như `demo.One` và `demo.Two` có thể cùng runtime package nếu cùng defining loader; hai class **cùng tên** như `demo.One` định nghĩa ở hai loader khác nhau vừa là hai runtime classes khác nhau, vừa thuộc hai runtime packages khác nhau.

## 2. Defining loader khác initiating loader

Khi gọi:

```java
Class<?> type = childLoader.loadClass("demo.One");
```

không thể suy ra ngay rằng `childLoader` là defining loader của `type`.

Mặc định, `ClassLoader.loadClass` sử dụng **parent delegation**: nó tìm lớp đã nạp, nhờ parent tìm trước, rồi mới gọi `findClass` ở child nếu parent không nạp được. Vì thế:

```text
childLoader.loadClass("demo.One")
        |
        v
parentLoader.loadClass("demo.One")
        |
        +--> nếu parent tìm thấy:
              type.getClassLoader() == parentLoader
              child chỉ là một initiating loader có thể nhận diện type
```

Trong JVMS:

- **Defining loader** là loader được dùng để thực sự định nghĩa runtime class, ví dụ qua `defineClass`.
- **Initiating loader** là loader đã bắt đầu nạp một lớp qua cơ chế delegation hoặc được JVM ghi nhận trong quá trình tải. Một loader có thể là initiating loader của class do loader khác định nghĩa.
- Danh tính runtime class và runtime package dựa trên **defining loader**, không dựa trên đối tượng loader vừa được gọi ở một biểu thức `loadClass`.

API quan sát hữu ích:

```java
ClassLoader definingLoader = someType.getClassLoader();
String packageName = someType.getPackageName();
```

Đây là các câu lệnh minh họa khi đã có biến `someType` kiểu `Class<?>`. `getClassLoader()` có thể là `null` khi JVM biểu diễn bootstrap loader. `getPackageName()` cho **chuỗi tên package**, không trả toàn bộ danh tính runtime package.

Nếu cần so sánh hai class đã nạp:

```java
boolean sameRuntimePackage =
        first.getPackageName().equals(second.getPackageName())
        && first.getClassLoader() == second.getClassLoader();
```

Đây là cách so sánh minh họa cho **class/interface thông thường** khi `first` và `second` đều đã có giá trị. Với array classes hoặc các loại đặc biệt khác, cần xét quy tắc JVM dành riêng cho loại đó; không áp dụng máy móc mọi phép suy luận về package-private access của một class thông thường.

## 3. Vì sao runtime package ảnh hưởng đến access control?

Java có bốn mức truy cập thường học: `public`, `protected`, *package-private* (không ghi modifier) và `private`.

Một method/field **package-private** thông thường chỉ có thể được truy cập từ code trong **cùng runtime package**. Do vậy, việc **source package name giống nhau** chưa đủ.

Ví dụ mã Java hợp lệ khi biên dịch cùng package:

```java
package demo;

public class PackageApi {
    static String secret() {
        return "private-to-package";
    }
}
```

```java
package demo;

public class PackageClient {
    public static String callSecret() {
        return PackageApi.secret();
    }
}
```

Ở thời điểm biên dịch, compiler thấy hai lớp cùng package `demo` nên cho phép gọi method `secret()`. Nhưng khi chạy, nếu `PackageClient` được loader con tự định nghĩa còn `PackageApi` do loader cha định nghĩa, JVM nhìn thấy:

```text
PackageClient:
    runtime package = (demo, ChildLoader)

PackageApi:
    runtime package = (demo, ParentLoader)

PackageClient.callSecret()
        |
        | invokes PackageApi.secret()  [package-private]
        v
    IllegalAccessError
```

`IllegalAccessError` là một `LinkageError`: lỗi kiểm tra quyền truy cập ở cấp JVM, có thể chỉ xuất hiện khi symbolic method reference được giải quyết hoặc khi JVM thực hiện kiểm tra liên quan. Điều này khác với lỗi compiler `'secret() is not public'` khi code nguồn vốn nằm khác package.

### Bảng phân biệt các tình huống

| Nguồn và runtime | Cùng runtime package? | Truy cập package-private |
|---|---|---|
| Hai class `demo.A`, `demo.B` do cùng defining loader định nghĩa | Có | Có thể truy cập theo quy tắc package-private |
| Hai class `demo.A`, `demo.B` do hai defining loaders khác nhau định nghĩa | Không | Bị từ chối nếu không có cơ chế quyền truy cập khác hợp lệ |
| Hai class có package name khác nhau nhưng cùng loader | Không | Không được package-private |
| Child gọi `loadClass` nhưng parent thực sự định nghĩa cả hai class cùng package | Có | Vẫn thuộc một runtime package của parent |
| Hai class khác loader, tương tác qua member `public` của lớp `public` và quyền module hợp lệ | Không | Public API có thể truy cập, không cần cùng runtime package |

Từ **“có thể”** ở đây là có chủ ý: quyền package-private phải được kiểm tra với đúng class/member và các quy tắc truy cập liên quan; không phải mọi thành viên hay mọi module đều tự động truy cập được chỉ vì cùng tên package.

## 4. Demo Java 17+: cùng package name nhưng IllegalAccessError

Ví dụ sau **biên dịch được bằng Java 17** và cố ý tạo hai runtime packages khác nhau. Loader con chỉ nạp `demo.PackageClient` theo kiểu child-first; `demo.PackageApi` vẫn do loader cha nạp. Kết quả: lời gọi API `public` hoạt động, còn lời gọi method package-private bị JVM từ chối.

Cấu trúc mã nguồn:

```text
src/
└── demo/
    ├── PackageApi.java
    ├── PackageClient.java
    └── RuntimePackageDemo.java
```

**Tệp `src/demo/PackageApi.java`:**

```java
package demo;

public class PackageApi {
    static String secret() {
        return "private-to-package";
    }

    public static String open() {
        return "public-api";
    }
}
```

**Tệp `src/demo/PackageClient.java`:**

```java
package demo;

public class PackageClient {
    public static String callSecret() {
        return PackageApi.secret();
    }

    public static String callPublic() {
        return PackageApi.open();
    }
}
```

**Tệp `src/demo/RuntimePackageDemo.java`:**

```java
package demo;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

public class RuntimePackageDemo {
    static class ChildFirstClientLoader extends ClassLoader {
        ChildFirstClientLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            // Chỉ thay chiến lược tải cho đúng một class thử nghiệm.
            if (!"demo.PackageClient".equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = findClass(name);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            String path = name.replace('.', '/') + ".class";

            // Lấy bytes của class thử nghiệm từ classpath cha.
            try (InputStream input =
                    getParent().getResourceAsStream(path)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }

                byte[] bytes = input.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (java.io.IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader parent = RuntimePackageDemo.class.getClassLoader();
        Class<?> original = PackageClient.class;

        Class<?> child = new ChildFirstClientLoader(parent)
                .loadClass("demo.PackageClient");

        System.out.println("Same binary name: "
                + original.getName().equals(child.getName()));
        System.out.println("Same defining loader: "
                + (original.getClassLoader() == child.getClassLoader()));

        System.out.println("Parent public: "
                + PackageClient.callPublic());
        System.out.println("Child public: "
                + child.getMethod("callPublic").invoke(null));

        try {
            System.out.println(
                    child.getMethod("callSecret").invoke(null));
        } catch (InvocationTargetException ex) {
            System.out.println("Child package-private: "
                    + ex.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa `src` bằng JDK 17+:

```bash
javac --release 17 -d out src/demo/PackageApi.java src/demo/PackageClient.java src/demo/RuntimePackageDemo.java
java -cp out demo.RuntimePackageDemo
```

Đầu ra:

```text
Same binary name: true
Same defining loader: false
Parent public: public-api
Child public: public-api
Child package-private: IllegalAccessError
```

**Tại sao như vậy?**

1. `PackageClient.class` từ application loader được tham chiếu trực tiếp trong `main`, tạo ra một runtime class do parent loader định nghĩa.
2. `ChildFirstClientLoader` cố ý chỉ tự `defineClass("demo.PackageClient", bytes)`. JVM cho phép hai runtime classes cùng binary name tồn tại vì defining loaders khác nhau.
3. Khi `PackageClient` của child gọi `PackageApi.open()`, đường tải thông thường ủy quyền cho parent và lấy `PackageApi` từ parent. Method `open()` là `public` nên lời gọi hợp lệ trong ví dụ classpath này.
4. Khi bản child gọi `PackageApi.secret()`, bytecode tham chiếu đến method không có modifier `public`, `protected` hoặc `private` — tức **package-private**. Hai class có defining loader khác nhau, nên thuộc hai runtime packages khác nhau dù cùng tên `demo`. JVM báo `IllegalAccessError`.
5. Reflection `Method.invoke` bọc throwable do method được gọi ném vào `InvocationTargetException`. Ví dụ lấy `ex.getCause()` để in đúng `IllegalAccessError` ở bên trong.

Đây là loader **dùng để thí nghiệm**, không phải cách cài đặt class loader production. Nó đọc lại bytecode từ parent resource và chủ động thay đổi chính sách cho một binary name; khi làm plugin thật cần giới hạn namespace, giải quyết dependency, package sealing, module boundaries và vòng đời loader.

**Lưu ý:** Đừng sửa `PackageApi.secret()` thành `public` khi đang thử nghiệm lần đầu, vì điều đó làm mất tình huống cần quan sát. Có thể thực hiện thay đổi này **sau** khi thấy lỗi để đối chiếu: cùng khác runtime package, nhưng public access trở nên hợp lệ.

## 5. Khác runtime package vẫn có thể giao tiếp như thế nào?

Việc khác runtime package không cấm mọi tương tác. Điều nó thay đổi ở ví dụ là **access check cho package-private member**.

Một ranh giới hợp lý giữa application và plugin thường là:

```text
Parent/App ClassLoader
    ├── shared.api.Plugin          (public interface)
    └── shared.api.Message         (public DTO/contract)
              ^
              | delegation cho các shared APIs
              |
Child/Plugin ClassLoader
    ├── plugin.impl.ExamplePlugin  (implementation riêng)
    └── plugin.libs.*              (dependency riêng nếu được cấu hình)
```

Plugin loader **ủy quyền cho parent** với `shared.api.Plugin` và `shared.api.Message`. Vì vậy application và plugin làm việc với cùng runtime types của API chung. Code triển khai riêng ở child có thể giao tiếp qua public methods của interface, và application không phải truy cập package-private internals của plugin.

Một interface chia sẻ:

```java
package shared.api;

public interface Plugin {
    String run(String input);
}
```

Đây là **đoạn nguồn API minh họa**, không chứa `main`. Khi sử dụng thật, compile API thành dependency mà parent loader có thể định nghĩa, rồi đảm bảo child ủy quyền cho parent đối với namespace API. Cần đồng thời tính đến rule truy cập module khi dùng named modules.

Nếu hai loader **cùng tự định nghĩa lại** `shared.api.Plugin`, tên giống nhau vẫn không đủ: chúng là hai runtime interface khác nhau và object plugin có thể không cast được sang interface ứng dụng mong đợi.

## 6. Các quy tắc quyền truy cập có liên quan

Runtime package là một phần của cơ chế truy cập; cần phân biệt những trường hợp sau:

### 6.1. Lớp top-level không có `public`

Ví dụ:

```java
package demo;

class Helper {
}
```

`Helper` là **package-private top-level class**. Một lớp trong runtime package khác không có quyền truy cập trực tiếp vào kiểu này chỉ vì có cùng chuỗi tên package. JVM có thể báo `IllegalAccessError` khi cố giải quyết một tham chiếu không hợp lệ.

### 6.2. Trường và phương thức package-private

Thành viên không khai báo access modifier thường có quyền **package access**. Compiler Java kiểm tra theo package trong source; JVM kiểm tra trong bối cảnh runtime types và runtime packages.

Ví dụ:

```java
package demo;

public class Holder {
    static int count = 1;
    static int readCount() {
        return count;
    }
}
```

Nếu caller do loader khác định nghĩa, cùng `package demo;` trong source không bảo đảm lời gọi `Holder.readCount()` qua runtime link sẽ được phép.

### 6.3. `protected` và `private`

- `protected` có quy tắc truy cập khi cùng runtime package **hoặc** qua quan hệ lớp con trong những điều kiện cụ thể; không được hiểu đơn giản là “mọi subclass luôn truy cập được mọi protected member bằng mọi receiver”.
- `private` phụ thuộc đến quyền sở hữu class/nestmates và cơ chế truy cập JVM; **cùng runtime package không đủ** để truy cập private member.
- `public` thường không đòi cùng runtime package, nhưng còn chịu module accessibility và các quy tắc hợp lệ khác.

Vì vậy tránh suy luận rằng biết runtime package là có thể bỏ qua những kiểm tra khác về subclass, declaring class, module hoặc bytecode instruction.

## 7. Java 9+ module system có thay đổi công thức không?

**Không thay công thức cốt lõi của runtime package trong JVMS:** package name cộng defining loader vẫn quyết định runtime package identity của class/interface thông thường. Java Platform Module System (JPMS) bổ sung **module membership** và **module readability/exports/opens** vào mô hình truy cập.

Hãy xét hai lớp cùng package name nhưng ở hai loader:

```text
Loader A defines com.acme.One
Loader B defines com.acme.Two
```

Chúng không cùng runtime package, kể cả khi các module liên quan đều `exports com.acme`. Ngược lại, hai class trong cùng runtime package không có nghĩa mọi tương tác reflective hay truy cập kiểu trong named modules luôn được phép.

Các khái niệm phân biệt:

| Khái niệm | Hỏi điều gì? |
|---|---|
| Runtime package | Class thuộc tên package nào **và do defining loader nào định nghĩa**? |
| Runtime module | Class thuộc module runtime nào (`Class.getModule()`)? |
| Module reads | Module của caller có đọc được module của type được gọi không? |
| Exports | Package có được export cho module cần truy cập public types không? |
| Opens | Có cho phép loại deep reflection phù hợp với rules hiện hành không? |

Ví dụ thử nghiệm phía trên chạy trên **classpath** nên các lớp ứng dụng nằm trong unnamed module. Đây là cách cô lập hiệu ứng runtime-package/loader để học, không có nghĩa bỏ qua JPMS khi ứng dụng dùng module path.

Với named modules, cấu hình split packages và phân công packages cho loaders còn chịu thêm ràng buộc. Đừng dùng thử nghiệm “hai loader cùng khai báo package” như lời khuyên để cố nhét nhiều module xung đột vào một class loader.

## 8. Không nhầm runtime package với Package metadata hoặc JAR layout

Có nhiều cách quan sát một tên package nhưng không cách nào một mình quyết định quyền truy cập:

- `type.getPackageName()` trả **tên package**, ví dụ `demo`. Hai runtime packages khác nhau vẫn có thể trả cùng chuỗi.
- `type.getPackage()` trả metadata kiểu `java.lang.Package` khi thích hợp, có thể chứa thông tin annotation, specification/implementation version hoặc sealing. Đối tượng `Package` không thay thế định nghĩa runtime-package identity của JVMS.
- Đường dẫn tài nguyên `demo/PackageApi.class` biểu diễn cách tổ chức file/JAR. Hai loader có thể đọc cùng entry bytecode nhưng định nghĩa **hai runtime classes**.
- `Class.getProtectionDomain()` mô tả thông tin protection domain liên quan nguồn/quyền trong runtime; nó không phải thành phần của cặp định danh runtime package.
- `type.getClassLoader()` là điểm quan sát defining loader hữu ích; khi trả `null`, Java API dùng quy ước biểu diễn bootstrap loader.

**Package sealing** là ràng buộc riêng có thể được class loader/JAR manifest áp dụng để kiểm soát nguồn định nghĩa class trong một package. Nó không làm các class khác defining loader tự động trở thành cùng runtime package. Một lỗi sealing cũng không đồng nghĩa `IllegalAccessError` do package-private.

## 9. Trường hợp thường gặp khi triển khai

### 9.1. Plugin vô tình nhúng bản sao shared API

Ứng dụng có `com.app.spi.Plugin` do app loader định nghĩa, nhưng plugin JAR cũng đóng gói class cùng tên, và child-first loader định nghĩa bản của nó. Khi code cố cast implementation sang `com.app.spi.Plugin` phía ứng dụng, có thể gặp `ClassCastException` hoặc lỗi linking do loader constraints.

**Hướng giải quyết:** đặt SPI/DTO hợp đồng chung ở loader cha, thiết lập delegation cho namespace của nó và chỉ isolate implementation hoặc dependency cần thiết.

### 9.2. Framework sinh proxy ở loader không phù hợp

Code sinh/proxy có thể cần truy cập package-private constructor, type hoặc method. Nếu proxy bị định nghĩa ở loader khác dù mang cùng package name, lời gọi có thể lỗi `IllegalAccessError`. Các framework thường cần cơ chế định nghĩa class phù hợp để giữ runtime package/nest/module access khi được phép.

**Hướng giải quyết:** xác định proxy class thực tế được định nghĩa bởi loader nào, phương thức nào bị từ chối và access requirements của thư viện. Không sửa bằng cách tự ý mở toàn bộ module hoặc đổi mọi method thành `public` nếu không phù hợp contract.

### 9.3. Thấy “same package” nhưng Java vẫn từ chối

Kiểm tra thêm defining loader, package name, module, exports/opens, class access modifiers và các symbolic references được bytecode dùng.

**Hướng giải quyết:** phân biệt lỗi xuất phát từ *runtime package mismatch*, *module access* hay *binary incompatibility*. Ba vấn đề có thể có biểu hiện liên quan nhưng cách sửa khác nhau.

### 9.4. Hot reload tạo class có cùng tên

Reload thông qua defining loader mới tạo runtime classes **mới**, dù mã nguồn/package name không đổi. Instance cũ và class mới không tự nhiên tương thích. Phần static state, package access và type casting gắn với đúng class identity.

**Hướng giải quyết:** giữ rõ vòng đời loader, tránh trộn instances từ thế hệ cũ/mới vào cùng API nội bộ; dùng shared public contracts ổn định khi giao tiếp xuyên boundary.

## 10. Cách chẩn đoán một IllegalAccessError giữa các loader

Khi log báo lỗi truy cập dù hai class source đều khai báo một package:

1. Ghi nhận **class đang cố truy cập**, class khai báo target member và tên method/field trong stack trace.
2. In `getName()`, `getPackageName()`, `getClassLoader()` của hai `Class<?>` tương ứng. Quan trọng là **defining loader**, không chỉ loader đã gọi `loadClass`.
3. Kiểm tra member có `public`, `protected`, `private` hay package-private, và target declaring class có `public` hay không.
4. Kiểm tra actual class loader policy (`loadClass` override, parent-first/child-first) và xem có duplicate API classes trong các JAR không.
5. Nếu dùng JPMS, kiểm tra module ownership, `exports`/`opens` và các quy tắc access liên quan.
6. Xác minh deployment thực tế: class version, binary name, shaded JAR, generated classes hoặc proxy bytecode. Không chỉ nhìn source hiện tại.
7. Sửa placement/delegation hoặc API boundary: dùng loader chung cho shared contracts, hoặc public API hợp lệ nếu giao tiếp xuyên runtime packages thực sự là chủ đích.

Có thể dùng đoạn hỗ trợ sau trong một chương trình đã có hai đối tượng `Class<?>`:

```java
static void inspect(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("package=" + type.getPackageName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule());
}
```

Đây là **method minh họa**, không phải chương trình độc lập. Chuỗi in cho loader/module phụ thuộc cấu hình môi trường; không gán cứng đầu ra. Trong tình huống có duplicate binary name, nên in cả `System.identityHashCode(type)` hoặc so sánh `Class<?>` trực tiếp nếu cần kiểm tra object identity trong phạm vi một JVM.

## 11. Kiểm tra kiến thức

**Câu 1.** Hai class đều `package demo;`, một class do loader A và một class do loader B định nghĩa. Chúng có cùng runtime package không?

**Trả lời:** Không. Runtime package identity là `(package name, defining loader)`; defining loaders khác nhau nên runtime packages khác nhau.

**Câu 2.** Child loader gọi `loadClass("demo.Api")` nhưng parent có sẵn `demo.Api`, và child kế thừa parent-first. Class này thuộc runtime package của loader nào?

**Trả lời:** Nếu parent là defining loader thực tế, runtime package dựa vào parent, dù lời gọi ban đầu đi qua child.

**Câu 3.** Compiler cho phép gọi package-private method từ một lớp cùng source package. JVM chắc chắn cho phép khi chạy không?

**Trả lời:** Không. Nếu classes được định nghĩa bởi các loaders khác nhau, JVM có thể ném `IllegalAccessError`.

**Câu 4.** Có thể dùng `type.getPackageName()` để kiểm tra hai lớp có chung runtime package không?

**Trả lời:** Không đủ. Phải xét cả defining loader; với lớp bình thường có thể đối chiếu chuỗi package cùng `getClassLoader()`.

**Câu 5.** Hai lớp có cùng binary name nhưng được định nghĩa bởi hai loaders khác nhau có là một kiểu không?

**Trả lời:** Không. Runtime class identity là `(binary name, defining loader)`. Chúng cũng có runtime packages khác nhau vì defining loaders khác nhau.

**Câu 6.** Khi muốn plugin và app dùng cùng interface, nên đặt interface ở đâu?

**Trả lời:** Trong dependency mà loader chung/parent định nghĩa được, và đảm bảo plugin loader ủy quyền namespace API đó để cả hai cùng sử dụng một runtime type.

**Câu 7.** Module `exports` một package có thể tự biến hai runtime packages khác nhau thành một runtime package không?

**Trả lời:** Không. Module rules là tầng bổ sung về khả năng truy cập; chúng không thay đổi cặp package name và defining loader.

## 12. Nguồn chuẩn

- *Java Virtual Machine Specification* `5.3`, “Creation of Classes and Interfaces”: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- *Java Virtual Machine Specification* `5.4.4`, “Access Control”: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.4
- Java SE 17 API, `ClassLoader` và delegation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `Class` (`getPackageName`, `getClassLoader` và `getModule`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html

**Ghi nhớ:** Tên package trong `package ...;` là thông tin cú pháp; runtime package theo JVM còn phụ thuộc **defining class loader**. Đây là lý do hai lớp cùng package name đôi khi không truy cập được package-private members của nhau, và cũng là lý do phải bố trí class loaders cùng shared APIs đúng chỗ trong kiến trúc plugin/module.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-006">Truy cập package-private giữa các lớp do loader khác nhau định nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Truy cập package-private giữa các lớp do ClassLoader khác nhau định nghĩa

## 1. Tình huống và câu trả lời cốt lõi

Giả sử hai lớp Java cùng khai báo `package lab.boundary;`. Khi biên dịch chung, lớp này có thể gọi phương thức **package-private** (không có `public`, `protected` hoặc `private`) của lớp kia. Nhưng nếu tại runtime hai lớp được **định nghĩa bởi hai ClassLoader khác nhau**, quyền truy cập package-private đó **không còn hợp lệ**, dù chuỗi tên package giống hệt nhau.

Điều kiện then chốt không phải chỉ là *tên package trong source* mà là **run-time package**. Theo JVM Specification, run-time package của một class/interface được xác định bởi:

1. **Tên package** (package name).
2. **ClassLoader đã định nghĩa lớp** (defining loader).

Vì vậy:

~~~text
Compile time:
  Host.java  -> package lab.boundary
  Guest.java -> package lab.boundary
  => javac cho phép Guest gọi Host.secret() (package-private)

Runtime:
  Host  -> package lab.boundary, defining loader = L_parent
  Guest -> package lab.boundary, defining loader = L_child

  Tên package giống nhau                  : ĐÚNG
  Định danh run-time package giống nhau    : SAI
  Guest gọi Host.secret() (package-private): IllegalAccessError
~~~

Đây là **quy tắc truy cập ở mức JVM**, không phải lỗi do trình biên dịch tự ý đổi luật. ClassLoader tạo nên một phần ranh giới định danh kiểu và ranh giới package lúc chạy.

**Sau bài này, bạn cần:**

- Phân biệt *package trong source* với *run-time package* của JVM.
- Giải thích tại sao tên package giống nhau nhưng defining loader khác nhau có thể gây `IllegalAccessError`.
- Đọc được bằng chứng `getPackageName()` và `getClassLoader()` trong chương trình chạy độc lập Java 17+.
- Phân biệt lỗi kiểm tra quyền truy cập khi **biên dịch**, khi **link/resolution** và khi dùng **reflection**.
- Biết các cách thiết kế API giữa host và plugin khi mỗi bên có loader riêng.

## 2. Package-private có nghĩa gì ở từng thời điểm?

### 2.1. Trong mã nguồn và khi biên dịch

Một thành viên không khai báo modifier truy cập có **package access**, thường gọi là *package-private* hoặc *default access*:

~~~java
package lab.boundary;

public class Host {
    static String secret() { return "parent-only"; } // package-private
    public static String open() { return "public-ok"; } // public
}
~~~

Một lớp khác trong cùng package nguồn có thể gọi `Host.secret()` nếu các yêu cầu khác đều hợp lệ:

~~~java
package lab.boundary;

public class Guest {
    public static String callPrivate() {
        return Host.secret();
    }
}
~~~

Hai đoạn trên là **hai tệp Java minh họa riêng**, không phải một tệp độc lập. Nếu `Guest.java` biên dịch với `Host.class` trên compile classpath, `javac` thấy cả hai nằm trong `lab.boundary` và cho phép lời gọi. Tuy nhiên, compiler **không biết chắc** hai lớp sẽ được loader nào định nghĩa trong mọi lần chạy tương lai.

Nếu đổi package của `Guest` thành `other.pkg` mà vẫn gọi `Host.secret()`, `javac` sẽ báo lỗi truy cập ngay ở compile time. Đó là tình huống **khác** với bài học: ở đây source code **biên dịch được**, nhưng runtime loading làm thay đổi ranh giới package.

### 2.2. Trong JVM: cùng chuỗi tên chưa đủ

JVM Specification `5.3` định nghĩa một lớp runtime bằng **binary name + defining loader**, và run-time package bằng **package name + defining loader**.

Ký hiệu:

~~~text
Host:
  binary name     = lab.boundary.Host
  package name    = lab.boundary
  defining loader = L_parent
  run-time package = (lab.boundary, L_parent)

Guest:
  binary name     = lab.boundary.Guest
  package name    = lab.boundary
  defining loader = L_child
  run-time package = (lab.boundary, L_child)
~~~

Nếu `L_parent != L_child` thì hai run-time packages **khác nhau**. Truy cập package-private giữa hai lớp không được phép chỉ vì `getPackageName()` trả cùng chuỗi.

Điều này cũng giải thích một vấn đề quen thuộc trong plugin/container: loader riêng bảo đảm cô lập kiểu, nhưng việc cô lập có thể làm hỏng các lời gọi package-private vốn được compiler cho phép khi các source nằm trong cùng một package.

### 2.3. Defining loader và initiating loader không giống nhau

- **Defining loader** là loader thực sự **định nghĩa** class, trực tiếp hoặc qua cơ chế JVM tương ứng. `SomeClass.class.getClassLoader()` phản ánh defining loader (có thể là `null` với bootstrap-defined class).
- **Initiating loader** là loader **khởi xướng việc tra cứu** class, có thể ủy quyền việc định nghĩa cho parent.

Ví dụ: `L_child.loadClass("lab.boundary.Host")` có thể trả về `Host` được **`L_parent` định nghĩa** vì ủy quyền parent-first. Khi xét run-time package, dùng **defining loader** của mỗi lớp, không kết luận dựa trên việc “child từng yêu cầu load”.

Trong ví dụ thực hành bên dưới, parent định nghĩa `Host` còn child định nghĩa `Guest`. Mặc dù child có thể tìm `Host` qua parent, chúng vẫn nằm ở hai run-time packages khác nhau.

## 3. JVM phát hiện vi phạm quyền truy cập như thế nào?

### 3.1. Kiểm tra khi resolving symbolic reference

Trong bytecode của `Guest.callPrivate()`, lời gọi `Host.secret()` là tham chiếu đến một phương thức. Khi JVM **resolution** tham chiếu đó, nó kiểm tra phương thức đích có thể được truy cập từ lớp đang gọi không.

Quy tắc quan trọng trong [JVMS `5.4`.4](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4.4):

- `public` method: có thể truy cập nếu lớp chứa nó cũng truy cập được và đáp ứng các quy tắc có liên quan.
- `protected`: tuân theo quy tắc subclass/same run-time package và các điều kiện bổ sung.
- Không có modifier truy cập (*package-private*): cho phép nếu **lớp khai báo member** và **lớp đang truy cập** thuộc **cùng run-time package**.
- `private`: dựa trên lớp/nest hợp lệ, không phải chỉ chung package.

Khi truy cập method không hợp lệ tại resolution, JVM ném **`java.lang.IllegalAccessError`**. Lỗi này là một `LinkageError` (thuộc nhánh `Error`), **không phải** `IllegalAccessException` của reflection.

**Thời điểm quan sát:** JVM được phép resolve một số tham chiếu sớm hoặc muộn theo đặc tả. Trong ví dụ cụ thể, `IllegalAccessError` xuất hiện khi thực thi lời gọi `Guest.callPrivate()`. Không nên khẳng định mọi JVM luôn ném lỗi đúng ở một opcode nếu các tình huống linking phức tạp khác xảy ra.

### 3.2. Không phải lỗi “không tìm thấy lớp”

Hai lớp trong ví dụ có thể đều đã được load thành công. Tìm thấy `Host` và `Guest` không bảo đảm câu lệnh `Host.secret()` được phép thực thi. Cần phân biệt:

| Hiện tượng | Ý nghĩa |
|---|---|
| `ClassNotFoundException` | API yêu cầu tìm lớp theo tên nhưng loader không tìm được lớp |
| `NoClassDefFoundError` | JVM không có/không dùng được một định nghĩa lớp bắt buộc tại runtime; có nhiều nguyên nhân |
| `IllegalAccessError` | JVM tìm được định nghĩa nhưng **kiểm tra quyền truy cập** tại linking/resolution thất bại |
| `IllegalAccessException` | API reflection báo một truy cập bị từ chối; đây là checked exception thuộc cơ chế reflection |
| `InaccessibleObjectException` | Có thể gặp khi cố thay đổi kiểm tra quyền truy cập bằng deep reflection nhưng bị chặn, nhất là qua ranh giới module |
| `ClassCastException` | Ép kiểu runtime không hợp lệ; trong plugin có thể do hai class cùng tên nhưng khác defining loader |

Sự khác biệt giữa `IllegalAccessError` và `IllegalAccessException` đặc biệt hữu ích: một lỗi là kết quả **kiểm tra truy cập JVM của bytecode**, lỗi kia thường phát sinh từ thao tác **reflection**.

## 4. Sơ đồ ClassLoader của ví dụ

Bố cục có chủ đích:

~~~text
Application / System ClassLoader (parent)
  |
  +-- định nghĩa lab.boundary.Host
  +-- định nghĩa Launcher
  |
  v  (parent-first delegation)
URLClassLoader child
  |
  +-- không định nghĩa lại Host
  +-- định nghĩa lab.boundary.Guest từ build/guest

Guest -> Host.open()   : hợp lệ (public)
Guest -> Host.secret() : IllegalAccessError (package-private)
~~~

**Điểm quyết định:** `Guest.class` **không** nằm trên runtime classpath của parent; ngược lại `Host.class` **chỉ** nằm trong output dành cho parent. Nếu vô tình thêm cả `build/guest` vào system classpath, parent-first delegation có thể khiến **cả Host và Guest đều do parent định nghĩa**, dẫn đến bài thử không còn minh họa ranh giới loader như mong muốn.

Ví dụ dùng `URLClassLoader` tiêu chuẩn và một lớp `Launcher`. Không cần custom subclass hay thư viện ngoài.

## 5. Thực hành Java 17+: ví dụ độc lập kiểm chứng lỗi

### 5.1. Tạo cây thư mục

Từ một thư mục trống tên tùy chọn (ở đây gọi là `demo`), tạo cây source như sau:

~~~text
demo/
  src-host/
    lab/
      boundary/
        Host.java
  src-guest/
    lab/
      boundary/
        Guest.java
  src-launcher/
    Launcher.java
  build/
    host/              # output của Host và Launcher
    guest/             # output của Guest
~~~

Có thể tạo các thư mục `src-*` và `build/*` bằng IDE/file explorer hoặc command line trước khi biên dịch. Hai đầu ra phải **riêng biệt**: chương trình sẽ chạy với system classpath **chỉ** gồm `build/host`.

### 5.2. Tệp `src-host/lab/boundary/Host.java`

~~~java
package lab.boundary;

public class Host {
    static String secret() {
        return "parent-only";
    }

    public static String open() {
        return "public-ok";
    }
}
~~~

`Host` là **public class** nên guest ở run-time package khác có thể tham chiếu chính class này. Nhưng `secret()` không có modifier: nó chỉ cho phép code thuộc cùng **run-time package** gọi. `open()` public được dùng làm phép thử đối chứng.

### 5.3. Tệp `src-guest/lab/boundary/Guest.java`

~~~java
package lab.boundary;

public class Guest {
    public static String callPrivate() {
        return Host.secret();
    }

    public static String callPublic() {
        return Host.open();
    }
}
~~~

Ở compile time, `javac` nhận hai class cùng source package `lab.boundary` nên cho phép cả hai lời gọi. Tình huống vi phạm chỉ phát sinh sau khi `Guest` được loader khác định nghĩa tại runtime.

### 5.4. Tệp `src-launcher/Launcher.java`

~~~java
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import lab.boundary.Host;

public class Launcher {
    public static void main(String[] args) throws Exception {
        URL source = Path.of("build/guest").toUri().toURL();

        try (URLClassLoader child = new URLClassLoader(
                new URL[] { source },
                Host.class.getClassLoader())) {

            Class<?> guest = Class.forName(
                    "lab.boundary.Guest", true, child);

            System.out.println("samePackageName="
                    + Host.class.getPackageName()
                            .equals(guest.getPackageName()));

            System.out.println("sameLoader="
                    + (Host.class.getClassLoader()
                            == guest.getClassLoader()));

            System.out.println("public="
                    + guest.getMethod("callPublic").invoke(null));

            try {
                guest.getMethod("callPrivate").invoke(null);
            } catch (InvocationTargetException ex) {
                System.out.println("failure="
                        + ex.getCause().getClass().getSimpleName());
            }
        }
    }
}
~~~

**Giải thích cấu trúc:**

1. `Path.of("build/guest").toUri().toURL()` đưa directory chứa `Guest.class` vào nguồn tìm kiếm của `URLClassLoader`.
2. Parent là `Host.class.getClassLoader()`: trong cách chạy này, đó là system/application loader, vốn đã định nghĩa `Host`.
3. Parent-first delegation tìm `lab.boundary.Guest` ở parent trước; vì parent không có `Guest.class`, loader con mới tìm trong `build/guest` và định nghĩa `Guest`.
4. `Class.forName(..., true, child)` tìm và khởi tạo `Guest`. `Guest` có public methods; reflection ở đây chỉ dùng để gọi hai methods của một class mà `Launcher` không compile-time phụ thuộc.
5. `Guest.callPublic()` gọi được `Host.open()`: `Host` public, `open()` public; cả hai class thuộc các unnamed modules của loader tương ứng, ở đây vẫn đáp ứng quyền truy cập public.
6. `Guest.callPrivate()` gây `IllegalAccessError` khi liên kết lời gọi `Host.secret()`. Reflection bọc lỗi ném từ invoked method trong `InvocationTargetException`; code lấy `getCause()` và in tên lỗi gốc.

### 5.5. Biên dịch/chạy đúng

Từ thư mục `demo`, tạo hai thư mục output. Trên Bash:

~~~sh
mkdir -p build/host build/guest
javac --release 17 -d build/host src-host/lab/boundary/Host.java src-launcher/Launcher.java
javac --release 17 -cp build/host -d build/guest src-guest/lab/boundary/Guest.java
java -cp build/host Launcher
~~~

Trên Windows PowerShell, câu lệnh tương đương:

~~~powershell
New-Item -ItemType Directory -Force build/host, build/guest | Out-Null
javac --release 17 -d build/host src-host/lab/boundary/Host.java src-launcher/Launcher.java
javac --release 17 -cp build/host -d build/guest src-guest/lab/boundary/Guest.java
java -cp build/host Launcher
~~~

Điều kiện bắt buộc: **không thêm `build/guest` vào lệnh chạy `java -cp build/host Launcher`**. Giữ thư mục làm việc ở `demo` để đường dẫn `build/guest` trong `Launcher` trỏ đúng nơi.

**Stdout đã kiểm chứng với JDK 21 biên dịch mức `--release 17`:**

~~~text
samePackageName=true
sameLoader=false
public=public-ok
failure=IllegalAccessError
~~~

Kết quả có bốn tầng nghĩa:

- `samePackageName=true`: hai class khai báo đúng cùng chuỗi tên package.
- `sameLoader=false`: defining loader **khác nhau**, do đó run-time package khác nhau.
- `public=public-ok`: class vẫn tìm được và public method hợp lệ; không phải lỗi thiếu class hay path.
- `failure=IllegalAccessError`: lời gọi package-private bị từ chối bởi JVM; việc `javac` từng cho qua không bảo đảm runtime access.

Không cần in `URLClassLoader.toString()` vì chuỗi định danh loader phụ thuộc runtime. So sánh đối tượng loader bằng `==` có ý nghĩa đúng hơn.

### 5.6. Kiểm tra bytecode để biết lỗi nằm ở đâu

~~~sh
javap -classpath build/guest -c lab.boundary.Guest
~~~

Trong `callPrivate()`, `javap` hiển thị lệnh gọi `invokestatic` trỏ tới `lab/boundary/Host.secret`. Cú pháp bytecode đã được tạo thành công lúc biên dịch; lỗi xuất hiện khi JVM xử lý quyền truy cập đối với method reference ở runtime.

Một phép thử đối chứng: nếu đưa `Guest.class` lên **system classpath** cùng `Host.class` trước khi khởi chạy, parent-first loader có thể định nghĩa cả hai và phép gọi package-private có thể thành công. Khi đó hãy kiểm tra lại `sameLoader`: nếu bằng `true` thì thí nghiệm không còn tách hai run-time package. Đó là **một môi trường chạy khác**, không phải cách “sửa tắt” cho hệ plugin muốn cô lập loader.

## 6. Phân biệt compile-time package và run-time package

Trình biên dịch Java xem các khai báo `package lab.boundary;` và classpath tại lúc biên dịch để quyết định có cho phép gọi package-private hay không. Khi chạy, JVM liên kết symbolic reference và kiểm tra **runtime package = (tên package, defining loader)**; hai tên package bằng nhau vẫn có thể thuộc hai runtime package khác nhau.

Vì vậy trong thí nghiệm trên, source `Guest` biên dịch được nhưng lời gọi tới thành viên package-private của `Host` bị `IllegalAccessError` khi class do loader khác định nghĩa. Biên dịch thành công không bảo đảm access check runtime thành công.

## 7. Tại sao framework/plugin dễ gặp vấn đề này?

Các hệ thống plugin, application server và container đôi khi tạo ClassLoader cho từng plugin/ứng dụng. Chúng có thể:

- Chia sẻ một API ở parent loader.
- Nạp implementation plugin qua child loader.
- Có hai phiên bản dependency cùng package dưới các child loaders khác nhau.
- Load một số class từ parent, một số từ child, dù nguồn build ban đầu đặt chúng trong cùng package.

Ví dụ:

~~~text
Parent loader:
  api/Service.class
  shared/internal/Helper.class

Plugin loader A:
  shared/internal/PluginTask.class

Plugin loader B:
  shared/internal/AnotherTask.class
~~~

Nếu `PluginTask` trực tiếp gọi `Helper.packagePrivateMethod()` (giả sử package đều là `shared.internal`), lời gọi có thể compile thành công khi build đặt cả hai nguồn trong cùng package. Nhưng runtime `Helper` do parent định nghĩa, còn `PluginTask` do plugin loader A định nghĩa nên **khác run-time package**, dẫn tới lỗi truy cập khi resolution.

Với Plugin A và Plugin B, ngay cả hai class cùng tên trong hai loader riêng cũng là **hai runtime types khác nhau**, có thể gây thêm `ClassCastException` nếu dùng kiểu implementation làm API chung. Đây là vấn đề khác nhưng có chung căn nguyên: **defining loader là thành phần của định danh kiểu/package**.

## 8. Thiết kế sửa đúng nguyên nhân

### 8.1. Sử dụng hợp đồng public dành cho ranh giới loader

Khi **chủ đích** tách lớp bằng nhiều loader, cách bền vững là công khai một hợp đồng giao tiếp cần thiết:

~~~java
package lab.api;

public interface Service {
    String execute();
}
~~~

Đoạn trên minh họa **API contract**, không phải một chương trình runnable riêng. Đặt interface này ở classpath/JAR chung do parent loader định nghĩa, sau đó cho plugin định nghĩa implementation và giao tiếp thông qua `public` methods của interface.

Lợi ích:

- Code từ các loader khác nhau không cần gọi package-private của nhau.
- Host và plugin cùng tham chiếu **một Class object `lab.api.Service`** do parent loader định nghĩa, tránh bất đồng type identity.
- Implementation và dependency nội bộ của plugin vẫn có thể được cô lập.

Nếu bắt buộc cung cấp một hành vi, hãy cân nhắc `public` API nhỏ có kiểm soát, một adapter/bridge công khai, hoặc thay đổi cách phân chia lớp giữa parent/child. Đừng tùy tiện biến mọi internal method thành `public` chỉ để che giấu một lỗi classloader; quyền public là một phần contract cần cân nhắc.

### 8.2. Nếu cần package-private thật sự, đặt các class trong cùng run-time package

Nếu hai class **thực sự phải** chia sẻ package-private logic, chúng phải có cùng tên package **và** cùng defining loader ở runtime. Với ứng dụng dùng nhiều loader, điều này có thể yêu cầu gom chúng vào cùng phía của ranh giới loader hoặc thiết kế lại kiến trúc module/plugin.

Tuy nhiên, chỉ đổi parent loader hoặc thêm JAR vào classpath tùy tiện có thể tạo xung đột dependency, duplicate classes, hoặc phá isolation. Điều kiện thành công phải được kiểm chứng bằng `getClassLoader()` cho **cả hai lớp** sau khi triển khai.

### 8.3. Reflection và mở module không làm mất điều kiện run-time package

- `setAccessible(true)` ảnh hưởng đến **truy cập qua reflection** theo các quy tắc tương ứng; nó **không biến** một bytecode `invokestatic Host.secret()` thành lời gọi package-private hợp lệ qua hai run-time packages.
- Với **named modules**, `requires`, `exports`, `opens` còn điều chỉnh khả năng dùng public types và deep reflection. `exports` hoặc `opens` **không tự cấp** quyền package-private giữa hai defining loaders khác nhau.
- Đừng thay package-private bằng `protected` với giả định “loader khác nhưng subclass sẽ được tất cả quyền”: `protected` có các quy tắc subclass, receiver và runtime package riêng, cần phân tích đúng từng đường gọi.

## 9. Chẩn đoán lỗi trong dự án thực tế

Khi thấy `IllegalAccessError` trỏ tới method/field trông như “cùng package”:

1. **Đọc đúng caller và member đích.** Stack trace thường có tên class tham chiếu, tên member và, trên JVM hiện đại, có thể có thông tin loader/module.
2. **Xác định access modifier của member** trong bytecode đang chạy, không chỉ trong source đã sửa. Có thể dùng `javap -p` hoặc `javap -c -p`, rồi xác nhận đúng JAR/version.
3. **Kiểm tra defining loader của hai class** bằng `SomeClass.class.getClassLoader()` hoặc log/class-loading diagnostics. Chỉ so sánh chuỗi `getPackageName()` là chưa đủ.
4. **Xem parent delegation và classpath nguồn.** Nếu parent đã có cùng một class, child-first/parent-first policy và nguồn class thực tế ảnh hưởng class được định nghĩa ở đâu.
5. **Nếu ứng dụng dùng JPMS**, kiểm tra thêm `clazz.getModule()`, readability, exports/opens. Những quy tắc đó có thể cùng gây lỗi access nhưng là nguyên nhân khác với package-private split runtime packages.
6. **Thử ví dụ rút gọn.** Hai class cùng package, xuất ra hai thư mục, dùng system/child loader tách biệt, cho public call thành công rồi thử package-private call. Đây là cách chứng minh vấn đề access mà không phụ thuộc vào toàn bộ framework.

### Các câu hỏi loại trừ nhanh

| Dấu hiệu | Ưu tiên điều tra |
|---|---|
| `javac` báo `is not public ... cannot be accessed from outside package` | Package/access ngay trong source, chưa đến runtime |
| Runtime `IllegalAccessError` với package-private member | Defining loader và run-time package; sau đó xét version/modifiers |
| `IllegalAccessError` với `public` type trong named module | Module readability/`exports` |
| `IllegalAccessException` khi gọi `Method.invoke` | Quyền reflection, có thể cần `canAccess` hoặc API công khai |
| `ClassCastException` khi class names giống nhau | Hai Class objects cùng tên nhưng defining loaders khác nhau |
| `ClassNotFoundException` | Nguồn class/JAR, đường classpath, loader được truyền vào |
| `NoSuchMethodError` | ABI/version mismatch, dependency runtime khác compile time |

Không xử lý tất cả những lỗi này bằng cách thêm `--add-opens`, `--add-exports` hoặc `setAccessible(true)`. Hãy xác định nguyên nhân trước vì các cờ đó không làm thay đổi defining loader và run-time package của class.

## 10. Những nhầm lẫn phổ biến

1. **“Hai file đều có `package lab.boundary;` nên package-private luôn hoạt động.”** Sai tại runtime nếu defining loader khác nhau.
2. **“`Guest.class.getPackageName().equals(Host.class.getPackageName())` là bằng chứng đủ.”** Sai; cần so defining loader.
3. **“Child có thể load `Host` qua parent nên Host thuộc package của child.”** Sai; initiating loader không thay defining loader của `Host`.
4. **“Biên dịch thành công thì JVM không được ném lỗi access.”** Sai; compiler dựa trên view lúc biên dịch, JVM xác minh runtime access.
5. **“`IllegalAccessError` giống `IllegalAccessException`.”** Sai; loại lỗi, nguồn phát sinh và cách điều tra khác nhau.
6. **“Cứ dùng `setAccessible(true)` là sửa được bytecode call.”** Sai; reflection không viết lại phép kiểm tra access tại resolution của lời gọi trực tiếp.
7. **“Public class đồng nghĩa mọi member của nó đều public.”** Sai; `Host` public không làm `Host.secret()` public.
8. **“Chỉ đổi thứ tự classpath không cần xét isolation.”** Sai; thứ tự/nguồn nạp có thể đổi defining loader và tạo lỗi khác.
9. **“Chỉ cần `opens` một JPMS package là cross-loader package-private hợp lệ.”** Sai; module access và run-time package là hai điều kiện khác nhau.
10. **“`NoClassDefFoundError` là kết quả bắt buộc khi dùng package-private giữa loader khác nhau.”** Sai; ví dụ này tìm thấy cả hai class và ném **`IllegalAccessError`**.

## 11. Câu hỏi tự kiểm tra

1. Run-time package được xác định bởi hai thành phần nào? Hai class cùng tên package nhưng khác defining loader có cùng run-time package không?
2. Tại sao `javac` có thể chấp nhận `Guest.callPrivate()` nhưng JVM lại ném `IllegalAccessError`?
3. Nếu `L_child.loadClass("lab.boundary.Host")` ủy quyền thành công cho parent, `Host` thuộc run-time package của loader nào?
4. Trong ví dụ mục 5, vì sao `samePackageName=true` song `sameLoader=false`?
5. Vì sao `Guest.callPublic()` thành công trước khi `Guest.callPrivate()` thất bại?
6. Nếu tình cờ thêm `build/guest` vào system classpath, điều gì có thể xảy ra với loader định nghĩa `Guest`? Vì sao điều đó làm mất giá trị chứng minh của thí nghiệm?
7. `IllegalAccessError` và `IllegalAccessException` khác nhau thế nào?
8. Vì sao đổi package-private member thành `public` cần đi kèm kiểm soát API contract, thay vì sửa tràn lan?
9. Nếu host và plugin cần trao đổi các đối tượng, tại sao một public interface dùng chung được định nghĩa bởi parent loader thường phù hợp?
10. Với named modules, `exports` hoặc `opens` có cho phép hai lớp khác defining loader chia sẻ package-private member chỉ nhờ cùng tên package không?
11. Làm sao bạn phân biệt lỗi access do package-private split với lỗi `NoSuchMethodError` do version mismatch?
12. Trong một dòng log có hai lớp cùng package và mỗi lớp có ClassLoader khác nhau, câu hỏi đầu tiên bạn cần kiểm tra khi có package-private call là gì?

**Tiêu chí hoàn tất:** dự đoán đúng stdout của ví dụ, chứng minh được hai run-time packages khác nhau dù cùng chuỗi package, giải thích được thời điểm `IllegalAccessError` xuất hiện, và đề xuất được một ranh giới API public phù hợp giữa host/plugin.

## 12. Tài liệu chuẩn để đối chiếu

- [JVMS Java SE 21 `5.3` — Creation of Classes and Interfaces](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): định danh kiểu và run-time package theo package name + defining loader.
- [JVMS Java SE 21 `5.4.4` — Access Control](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4.4): kiểm tra quyền truy cập đối với class, field, method; thời điểm sinh `IllegalAccessError`.
- [JLS Java SE 21 `6.6` — Access Control](https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.6): khái niệm accessibility ở mức Java language, bao gồm package access.
- [Java SE 21 `ClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): defining/initiating loader, parent delegation.
- [Java SE 21 `URLClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): tìm và định nghĩa lớp từ URL/directory/JAR.
- [Java SE 21 `InvocationTargetException` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): reflection bọc exception/error do invoked method ném.

Ví dụ biên dịch với `--release 17` và chạy trên JDK 21 để xác minh stdout; nội dung dùng quy tắc chuẩn áp dụng cho Java 17+. Phạm vi bài là package-private qua các defining loaders, không giả định một framework/plugin cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-007">Loading constraint bảo đảm nhất quán kiểu trong chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# Loading constraint bảo đảm nhất quán kiểu trong chữ ký

## 1. Vấn đề: cùng một tên Java có thể chỉ hai kiểu khác nhau

Trong JVM, class/interface ở runtime được xác định bởi **tên nhị phân (binary name) và defining class loader**. Nếu hai class loader độc lập cùng định nghĩa `com.example.Message`, chúng tạo ra **hai runtime type khác nhau**, dù file class, tên package và tên class giống hệt.

Điều này rất quan trọng ở ranh giới giữa hai loader. Giả sử code thuộc loader `L_A` gọi một phương thức của class do loader `L_B` định nghĩa:

```java
Message transfer(Message input);
```

Nếu `Message` mà mỗi loader thấy là **hai runtime class khác nhau**, cách hai bên hiểu chữ ký sẽ không còn nhất quán: một bên nghĩ tham số/trả về thuộc `Message` của `L_A`, bên kia dùng `Message` của `L_B`. JVM phải bảo vệ tính an toàn kiểu khi giải quyết (*resolve*) những field và method references như vậy.

**Loading constraint** là quy tắc liên kết do JVM áp đặt để bảo đảm các tên kiểu tham chiếu trong field/method descriptor của các class liên quan chỉ tới **cùng runtime type khi sự liên kết đòi hỏi chúng phải là một**.

Điểm mấu chốt:

- Hai loader **được phép** định nghĩa hai class cùng tên nếu chưa có ràng buộc buộc chúng phải được xem như một.
- Khi linking của field/method hoặc quan hệ override/implement đòi cùng một kiểu trong chữ ký, JVM áp đặt loading constraint.
- Nếu loader đã thấy hai định nghĩa không tương thích, hoặc về sau một loader cố ghi nhận class gây vi phạm constraint đã có, thao tác liên quan thất bại bằng **`LinkageError`**.
- Loading constraint thuộc **ngữ nghĩa của JVM**; không phải một Java annotation, dòng cấu hình của loader hay lời gọi API mà ứng dụng cần thực hiện trực tiếp.

Đặc tả nền tảng là **JVMS `5.3.4` — Loading Constraints**; các thời điểm áp đặt được mô tả ở **`5.4.2`** và **`5.4.3`**.

## 2. Ôn lại: defining loader, initiating loader và runtime type identity

Để hiểu loading constraint, cần phân biệt hai vai trò của class loader:

| Khái niệm | Ý nghĩa |
|---|---|
| **Defining loader** | Loader thực sự gọi cơ chế định nghĩa class và sở hữu runtime class identity. |
| **Initiating loader** | Loader đã yêu cầu việc nạp class và được JVM ghi nhận là initiating loader của class đó; có thể nhận class do parent định nghĩa. |
| **Binary name** | Tên ở dạng `com.example.Message` của class, không chứa loader identity. |
| **Runtime class identity** | Cặp tên nhị phân + defining loader; hai class cùng tên nhưng defining loader khác nhau là hai kiểu runtime khác nhau. |

Ví dụ: một custom loader yêu cầu `java.lang.String` thông qua parent delegation. Nó có thể là initiating loader đối với yêu cầu đó, còn bootstrap loader vẫn là defining loader của `String`. `String.class.getClassLoader() == null` trong Java API biểu diễn defining loader bootstrap trên JDK thông thường.

Ký hiệu của JVMS có dạng:

```text
N^L1 = N^L2
```

Đọc là: **tên kiểu N, khi được giải quyết thông qua loader L1 và L2, phải biểu thị cùng một runtime class/interface**. Đây là *loading constraint*, không phải phép so sánh chuỗi tên `N` và cũng không yêu cầu `L1 == L2`.

Nếu hai loader ủy quyền để cùng nhận một class từ loader cha, constraint này thỏa. Nếu hai loader lần lượt định nghĩa hai bản class khác nhau của `N`, constraint đó bị vi phạm.

Một ví dụ trực quan:

```text
                      Shared API
                    (loader Parent)
                        |
          +-------------+-------------+
          |                           |
       Loader A                    Loader B
    impl.A.Service              impl.B.Client
          |                           |
          +------ uses api.Message ---+
                        |
                 Class<Message>
                 do Parent define

Cả A và B cùng resolve api.Message -> một runtime class:
constraint được thỏa mãn.
```

Ngược lại, nếu A và B cùng có bản copy `api.Message` và dùng chính class copy của mình khi tham gia một phương thức chung cần tương thích, JVM có thể phát hiện xung đột qua loading constraint.

## 3. Loading constraint xuất hiện trong chữ ký nào?

JVM lưu kiểu field bằng **field descriptor** và danh sách tham số/kiểu trả về của phương thức bằng **method descriptor**.

Ví dụ:

| Khai báo Java | JVM descriptor |
|---|---|
| `Message message;` | `Lcom/example/Message;` |
| `Message[] messages;` | `[Lcom/example/Message;` |
| `Message transfer(Message input);` | `(Lcom/example/Message;)Lcom/example/Message;` |
| `void accept(Message[][] data);` | `([[Lcom/example/Message;)V` |
| `int count();` | `()I` |

Các tên kiểu tham chiếu như `com/example/Message` là những thành phần có thể chịu loading constraint khi có liên kết yêu cầu tính nhất quán giữa loaders.

Một số chi tiết:

- **Primitive** `int`, `long`, `boolean`... không có danh tính class do custom loader định nghĩa, nên không phải vấn đề “cùng tên nhưng khác defining loader”.
- **Array** gồm các dấu `[` trong descriptor. Đối với mảng của class/interface, ràng buộc được xét theo kiểu phần tử tham chiếu tương ứng; không được kết luận “mảng không liên quan vì tên có `[`”.
- **`void`** là kiểu trả về đặc biệt và không phát sinh một class reference do loader định nghĩa.
- **Generics** như `List<Message>` thông thường bị *erasure* thành `Ljava/util/List;` trong method/field descriptor. Riêng generic argument `Message` **không tự sinh loading constraint chỉ vì nó xuất hiện trong generic signature metadata**. Code khác trực tiếp dùng `Message` vẫn có thể đưa tên đó vào descriptor hoặc symbolic reference riêng.
- Danh sách `throws SomeException` không nằm trong method descriptor; exception declaration **không tự nó** là nguyên nhân gây constraint cùng cách với kiểu tham số/trả về.
- Thông tin annotation, tên parameter và nullability cũng không phải là thành phần của field/method descriptor.

**Đừng nhầm “chuỗi descriptor bằng nhau” với “runtime type giống nhau”.** Hai loader có thể nhìn thấy cùng chuỗi `Lcom/example/Message;` mà giải quyết ra hai class khác nhau. Loading constraint xuất hiện khi JVM cần bảo đảm chúng phải là một cho hoạt động liên kết cụ thể.

## 4. JVM áp đặt constraint ở đâu và khi nào?

JVMS quy định việc áp đặt constraint ở một số hoạt động linking. Không phải cứ gọi `loadClass` là JVM sẽ áp đặt một constraint giữa tất cả loader trên thế giới.

### 4.1. Khi chuẩn bị class có quan hệ override hoặc kế thừa phương thức

Trong **preparation**, nếu class con định nghĩa một instance method có thể override method từ superclass/superinterface, JVM cần bảo đảm các kiểu liên quan trong descriptor của method được hiểu nhất quán giữa defining loader của hai class.

Xét tình huống khái niệm:

```java
// Định nghĩa bởi loader Parent:
package api;

public class Message {}

public class Base {
    public Message echo(Message value) {
        return value;
    }
}
```

```java
// Class Sub thuộc một loader Child:
package plugin;

public class Sub extends api.Base {
    @Override
    public api.Message echo(api.Message value) {
        return value;
    }
}
```

**Hai khối trên là phác thảo mã nguồn của hai thành phần, không phải hai chương trình độc lập đặt trong một file.** Chúng mô tả cách class `Sub` override `Base.echo`.

Method descriptor của hai phương thức là:

```text
(Lapi/Message;)Lapi/Message;
```

JVM cần ràng buộc việc resolve `api.Message` thông qua defining loaders liên quan sao cho cùng chỉ tới **một runtime class**. Nếu Child ủy quyền `api.Message` cho Parent, điều kiện được đáp ứng. Nếu Child cố dùng một bản `api.Message` khác trong ngữ cảnh override này, constraint có thể bị vi phạm khi linking.

Đặc tả còn bao gồm trường hợp class chọn (*select*) method kế thừa để đáp ứng superinterface method; không được đơn giản hóa toàn bộ thành “chỉ method có từ khóa `@Override` mới có constraint”. `@Override` là annotation kiểm tra ở thời điểm biên dịch; JVM quan tâm quan hệ method thực tế trong class file.

### 4.2. Khi resolve field reference

Nếu class do loader `L_Caller` định nghĩa truy cập field được khai báo trong class do loader `L_Owner` định nghĩa, JVM giải quyết symbolic field reference và descriptor của field đó. Các tên class/interface xuất hiện trong **descriptor của field** phải thỏa loading constraints tương ứng giữa hai bên.

Ví dụ:

```java
// Chỉ minh họa việc tham chiếu field qua hai class/loader.
Message value = service.currentMessage;
```

Nếu field descriptor là `Lapi/Message;`, JVM cần bảo đảm việc hiểu `api.Message` của caller và field owner không dẫn đến hai runtime types khác nhau khi resolution áp đặt constraint.

Không phải **mọi** instance field access đều tạo ra xung đột. Nếu hai bên dùng cùng định nghĩa `api.Message` qua parent delegation, constraint được thỏa thuận và không có lỗi.

### 4.3. Khi resolve method/interface method reference

Với lời gọi:

```java
Message output = service.echo(input);
```

symbolic method reference chứa tên method và descriptor, chẳng hạn:

```text
echo:(Lapi/Message;)Lapi/Message;
```

Khi method resolution/interface method resolution thành công về việc tìm method và quyền truy cập, JVM áp đặt loading constraints cho kiểu tham số và kiểu trả về liên quan tới loader khai báo method được chọn và loader của class chứa symbolic reference, theo các quy tắc `5.4.3`.

Điều này bảo đảm caller không sử dụng một `api.Message` khác với ý nghĩa của `api.Message` trong descriptor của method đích.

### 4.4. Constraint được ghi nhận và kiểm tra theo thời điểm

Không nên suy rằng JVM phải load tất cả class có tên trong mọi descriptor ngay khi đọc một `.class`. JVM được phép thực hiện nhiều hoạt động resolution theo nhu cầu. Hệ quả:

1. **Constraint có thể được áp đặt trước khi tên class tham gia đã được một hoặc cả hai loader thực sự load.** Sau đó, việc ghi nhận một initiating loader cho class mới sẽ phải kiểm tra các constraint đang tồn tại.
2. **Nếu cả hai loader đã resolve cùng tên sang hai runtime classes khác nhau**, một constraint mới yêu cầu chúng bằng nhau sẽ thất bại ngay khi được áp đặt.
3. **Nếu constraint tồn tại trước**, hoạt động nạp class không tương thích về sau có thể thất bại khi JVM ghi nhận initiating loader.
4. Khi phát hiện vi phạm, JVM ném `LinkageError`; thao tác tạo constraint hoặc ghi nhận initiating loader tương ứng thất bại, và constraint/ghi nhận mới gây lỗi được rút lại theo đặc tả.

**Thời điểm thông báo lỗi có thể khác nhau** tùy chương trình, đường linking và việc JVM chọn resolution sớm hay muộn. Không nên viết test phụ thuộc một stack trace hoặc một vị trí dòng duy nhất cho mọi JVM.

## 5. Không phải hai loader cùng tên luôn sai

Một ví dụ quan trọng là **cùng binary name vẫn có thể tồn tại hai class runtime khác nhau nếu chưa có ràng buộc bắt chúng đồng nhất**.

Tạo file độc lập **`LoaderIdentityDemo.java`**, chứa class main và một class package-private cùng tệp:

```java
import java.io.IOException;
import java.io.InputStream;

public class LoaderIdentityDemo {
    private static final String TARGET = "Payload";

    static class CopyLoader extends ClassLoader {
        CopyLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = findClass(name);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                throw new ClassNotFoundException(name);
            }

            String resource = name.replace('.', '/') + ".class";
            try (InputStream in =
                         getParent().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader app = LoaderIdentityDemo.class.getClassLoader();
        ClassLoader left = new CopyLoader(app);
        ClassLoader right = new CopyLoader(app);

        Class<?> a = left.loadClass(TARGET);
        Class<?> b = right.loadClass(TARGET);

        System.out.println("Same binary name: "
                + a.getName().equals(b.getName()));
        System.out.println("Same runtime type: " + (a == b));
        System.out.println("Left defined by left: "
                + (a.getClassLoader() == left));
        System.out.println("Right defined by right: "
                + (b.getClassLoader() == right));
        System.out.println("Assignable: " + a.isAssignableFrom(b));
    }
}

class Payload {
}
```

Biên dịch và chạy trên **JDK 17+**, tại thư mục chứa tệp:

```bash
javac LoaderIdentityDemo.java
java LoaderIdentityDemo
```

Stdout:

```text
Same binary name: true
Same runtime type: false
Left defined by left: true
Right defined by right: true
Assignable: false
```

### Tại sao ví dụ không tự ném LinkageError?

Hai `CopyLoader` được viết để ưu tiên **tự định nghĩa** chỉ một class tên `Payload` từ chính bytes có trên application class path; còn các lớp khác vẫn ủy quyền lên parent. Vì `left` và `right` là hai defining loaders, chúng tạo hai class khác nhau có cùng tên `Payload`.

Nhưng chương trình **không** tạo một quan hệ override/field/method resolution liên loader nào yêu cầu `Payload` của hai bên phải cùng một kiểu. Do đó việc class `Payload` được nạp độc lập không tự tạo lỗi loading constraint.

Điều này làm rõ giới hạn quan trọng: **tồn tại hai class trùng tên** và **vi phạm loading constraint** là hai việc khác nhau.

> Code này cố ý child-first với `Payload` để minh họa class identity. Đây không phải khuôn mẫu dùng cho mọi package: không nên áp dụng child-first bừa bãi cho namespace hệ thống, API dùng chung hoặc mọi dependency khi xây dựng plugin.

## 6. Minh họa một tình huống vi phạm constraint

Giả sử host định nghĩa:

```text
Loader Parent:
  api.Message
  api.Service
    public api.Message transfer(api.Message input)
```

Loader plugin `Child` phải override method `transfer` của `api.Service` hoặc gọi method đó qua symbolic reference. Tuy nhiên plugin lại muốn định nghĩa riêng `api.Message` với **cùng binary name** nhưng khác defining loader.

```text
                 Parent defines:
              api.Message [P]
              api.Service [P]
                       |
                       | method descriptor:
                       | (Lapi/Message;)Lapi/Message;
                       |
                Plugin Child
                defines its own:
              api.Message [C]  != [P]
                       |
                       v
       JVM requires api.Message^Child
                 == api.Message^Parent
                       |
                       v
          Conflict: [C] != [P]
                       |
                       v
                  LinkageError
```

Trong tình huống đó, nếu JVM phải áp đặt loading constraint `api.Message^Child = api.Message^Parent` do preparation/method resolution, nó sẽ kiểm tra xem cùng tên có resolve ra cùng runtime class hay không.

Nếu Child đã cố định bản `api.Message` riêng, ràng buộc bị vi phạm. Nếu chưa nạp, constraint được thiết lập có thể khiến lần Child cố định nghĩa bản riêng sau đó thất bại.

Một thông báo trên HotSpot có thể chứa cụm `loader constraint violation`, tên class và mô tả loader liên quan; **không được giả định nội dung hoặc thời điểm chính xác của message trên mọi phiên bản JVM**.

### Cách thiết kế đúng

Nếu `api.Message` là DTO hoặc contract dùng chung:

- Đặt `api.Message` và `api.Service` trong module/JAR API được một loader chia sẻ định nghĩa.
- Cho plugin loader **ủy quyền** tới defining loader của API cho namespace `api.*`.
- Chỉ dùng chiến lược nạp riêng cho các class plugin thực sự cần cách ly; tránh đóng gói/định nghĩa lặp bản API ở plugin khi giao tiếp với host.
- Khi tạo object và truyền qua boundary, xác nhận lớp mà caller và callee dùng là **cùng runtime class**.

Nói cách khác, package trùng tên trong JAR không tạo ra type identity chung; **cách loader resolve class** mới quyết định.

## 7. Loading constraint khác ClassCastException thế nào?

Các lỗi cùng xuất hiện trong hệ thống nhiều class loader nhưng có nguyên nhân và thời điểm khác nhau.

| Hiện tượng | Đặc điểm |
|---|---|
| **`LinkageError` do loading constraint violation** | JVM phát hiện tên kiểu phải nhất quán giữa loaders vì preparation/resolution nhưng thực tế (hoặc thao tác nạp mới) không thể thỏa; thất bại trong loading/linking. |
| **`ClassCastException`** | Tại runtime, một object không thuộc kiểu mà phép ép kiểu yêu cầu; hai class trùng tên nhưng khác defining loader có thể gây lỗi ép kiểu. |
| **`ClassNotFoundException`** | Loader được yêu cầu tìm class nhưng không tìm được; không có nghĩa có hai bản xung đột. |
| **`NoClassDefFoundError`** | JVM không thể tìm hoặc dùng định nghĩa class cần thiết khi executing/linking; cũng có thể liên quan initialization đã lỗi. Không đồng nghĩa mặc định với loading constraint violation. |
| **`NoSuchMethodError`** | Resolution không tìm thấy method có tên/descriptor phù hợp; khác việc tìm được method nhưng descriptor của các bên gây constraint conflict. |

Một ví dụ:

- Nếu app có `Foo` của loader A và đưa object thuộc `Foo` do loader B định nghĩa vào phép ép kiểu sang A, ta có thể gặp **`ClassCastException`**.
- Nếu JVM phải link một lời gọi/override đòi `Foo` của A và B là cùng kiểu trong descriptor nhưng chúng đã khác nhau, ta có thể gặp **`LinkageError`** trước khi có cơ hội thực hiện phép ép kiểu.

Không cần gây cả hai loại lỗi mới kết luận loader bị cấu hình thiếu nhất quán. Hãy tìm chính xác giai đoạn lỗi: **lookup**, **linking**, **initialization** hay **runtime cast**.

## 8. Loading constraints, method overriding và descriptor JVM

### Override ở mức class file

Trong mã nguồn Java, override được kiểm tra theo các quy tắc ngôn ngữ. Ở JVM, việc một instance method có thể override method khác được xác định dựa trên tên, **method descriptor** và các điều kiện access/inheritance tương ứng (JVMS `5.4.5`).

Vì method descriptor chứa **kiểu tham số và kiểu trả về**, việc hai method có cùng descriptor chuỗi chưa bảo đảm đã dùng cùng runtime types nếu class được định nghĩa bởi hai loader khác nhau.

Loading constraints bổ sung điều kiện nhất quán cho các tên tham chiếu trong descriptor khi JVM chuẩn bị class có quan hệ override thích hợp.

### Generic không giải quyết class identity

Ví dụ:

```java
public java.util.List<api.Message> fetch() {
    return java.util.List.of();
}
```

Descriptor JVM sau erasure của method trên là:

```text
()Ljava/util/List;
```

Tên `api.Message` của type argument thường nằm trong metadata generic `Signature`, không nằm trong **method descriptor** này. Vì thế không thể nói JVM bắt buộc tạo loading constraint cho `api.Message` **chỉ vì** nó xuất hiện trong `List<api.Message>`.

Điều này không làm type argument an toàn xuyên loader. Khi code thực sự truy cập, ép kiểu hoặc gọi API dùng `api.Message` trong descriptor, các cơ chế linkage và cast tương ứng vẫn cần được tuân thủ.

### Mảng của kiểu tham chiếu

Method:

```java
api.Message[] copy(api.Message[][] data)
```

có descriptor:

```text
([[Lapi/Message;)[Lapi/Message;
```

Trong ràng buộc liên quan đến method descriptor, tên kiểu phần tử `api.Message` vẫn phải được resolve nhất quán. Không thể dùng khác biệt số chiều mảng để né yêu cầu cùng runtime element type khi JVM áp đặt constraint.

### Primitive không làm nảy sinh xung đột cùng tên loader

Method `int count(long value)` có descriptor `(J)I`. `J` và `I` mô tả primitive được JVM xác định sẵn; không có một custom class loader A định nghĩa “int A” và loader B định nghĩa “int B”.

Tất nhiên một class có method primitive vẫn chịu các quy tắc linking khác; chỉ riêng việc dùng `int`/`long` trong descriptor không đòi loading constraint cho một class reference.

## 9. Constraint không buộc mọi class cùng tên trong JVM phải hợp nhất

Đây là ngộ nhận nguy hiểm khi xây kiến trúc plugin.

**Loading constraints là những quan hệ bình đẳng cụ thể** mà JVM áp đặt khi cần bảo đảm type-safe linkage. Chúng không có nghĩa Java toàn cục chỉ cho phép một `com.example.Widget`.

Hãy xét hai plugin hoàn toàn độc lập:

```text
Plugin A (loader A): com.example.Widget
Plugin B (loader B): com.example.Widget
```

Nếu không có API signature/override/field resolution đòi tên `Widget` ở A và B phải giống nhau, JVM có thể giữ cả hai runtime classes. Điều này có thể là mục đích của classloader isolation.

Nhưng nếu host khai báo:

```text
shared.PluginApi:
  Widget convert(Widget input)
```

và A/B cần thực thi qua API chung, kiểu `Widget` trong chữ ký API phải được thiết kế để các bên resolve thống nhất. Cách an toàn thường là đưa DTO/interface chung vào **shared API loader**. Không cố dùng tên trùng nhau như một hình thức ép hai loader có cùng kiểu.

Cần phân biệt ba tầng:

1. **Tên có trùng nhau không?** So sánh `Class.getName()`.
2. **Runtime class có cùng identity không?** So sánh hai `Class<?>` bằng `==`.
3. **Linking có buộc cùng identity không?** Xét field/method descriptor, override, interface method selection và những symbolic references cụ thể của JVMS.

Hai câu đầu là quan sát trạng thái; câu thứ ba liên quan điều kiện mà JVM phải cưỡng chế khi liên kết.

## 10. Parent delegation hỗ trợ đáp ứng constraints, nhưng không thay thế chúng

Trong mô hình parent-first thông thường:

```text
App loader
    |
    v
Parent loader (định nghĩa api.Message)
    |
    +--> Plugin loader A yêu cầu api.Message
    |
    +--> Plugin loader B yêu cầu api.Message
```

Hai plugin có thể nạp rất nhiều implementation classes riêng biệt, nhưng với namespace `api.Message`, cả hai nhận về cùng định nghĩa của Parent. Khi một loading constraint yêu cầu kiểu này nhất quán, điều kiện được thỏa.

Tuy nhiên:

- **Parent-first không phải luật cứng cho mọi custom loader.** Một loader override `loadClass` có thể ưu tiên tự định nghĩa một số package.
- **Có cùng parent không bảo đảm mọi class cùng tên đều có identity chung.** Nếu hai loader tự override/định nghĩa cùng một tên class trước khi delegation, chúng vẫn có thể tạo hai class.
- **Constraint là quy tắc JVM**, không phải hệ quả tình cờ của việc tuân thủ parent-first.
- **Parent null** thường ủy quyền tới bootstrap với implementation `ClassLoader.loadClass` mặc định. Điều này giúp thống nhất các class lõi, nhưng parent-null không tự nhìn thấy mọi API ở application loader. Nếu DTO/interface dùng chung không thuộc bootstrap, phải chọn loader architecture hợp lý.

Kiến trúc plugin nên có quy tắc package cụ thể: core/JDK từ platform/bootstrap, shared contracts từ một loader chung, implementation riêng từ plugin loader; cân nhắc kỹ module boundaries, dependency visibility và lifecycle.

## 11. Điều tra lỗi loading constraint trong ứng dụng thật

Khi thấy `LinkageError` và thông điệp liên quan `loader constraint violation`, đừng vội đổi tên class hoặc xóa sạch mọi JAR. Phân tích theo đường liên kết:

1. **Đọc tên kiểu trong lỗi**: class/interface nào mà hai loader không thống nhất? Tên đó có nằm trong kiểu field, parameter hoặc return type của API bị liên kết không?
2. **Xác định hai loaders**: ai định nghĩa caller/callee, superclass/subclass hoặc interface/implementation? So sánh class loader identity, không chỉ tên loader hiển thị.
3. **Xem descriptor JVM của field/method**: dùng `javap -s -p` để nhìn kiểu thật sau erasure. Nếu cần override/bridge analysis, dùng `javap -v -p` để xem method, flags và `Signature`.
4. **Kiểm tra dependency duplication**: plugin có đóng gói bản copy của shared interface/DTO/JAR trong chính nó không? Parent delegation có bị override cho namespace chung không?
5. **So sánh `Class<?>` trực tiếp khi có thể**: hai kiểu tên giống nhau nhưng `==` khác nhau là dấu hiệu loader identity khác; cần xác định nơi liên kết đòi đồng nhất.
6. **Đừng phụ thuộc vào lúc lỗi xuất hiện**: resolution có thể lazy. Việc lỗi xảy ra ở lần gọi method đầu tiên không khẳng định nguyên nhân vừa mới được tạo tại lệnh đó.
7. **Sửa ở kiến trúc loader/class path**: thống nhất nơi sở hữu shared API và tránh tạo nhiều bản class có vai trò contract chung. Sau khi sửa, tạo loader/runtime mới phù hợp; class đã định nghĩa trong loader không thể tùy tiện “đổi defining loader” của chính runtime class đó.

### Các công cụ quan sát

Kiểm tra descriptor:

```bash
javap -s -p api.Service
javap -v -p api.Service
```

Thay `api.Service` bằng tên class thật trên class path/module path mà công cụ nhìn thấy. `javap` đọc bytecode và in metadata; nó **không xác nhận** runtime đang dùng định nghĩa từ loader nào.

Quan sát loading của HotSpot JDK 17:

```bash
java -Xlog:class+load=info YourMainClass
```

Đầu ra có thể dài và cách in tùy VM. Log có thể giúp biết class đến từ nguồn nào, nhưng để chứng minh danh tính runtime vẫn cần xét **defining loader** và đường resolve ở chương trình đang chạy.

Với mã có quyền truy cập:

```java
System.out.println(typeA.getName());
System.out.println(typeA.getClassLoader());
System.out.println(typeA == typeB);
```

Đây là đoạn **minh họa**: `typeA`/`typeB` cần là hai giá trị `Class<?>` đã được lấy từ đúng chỗ. Không suy luận từ hai chuỗi `getName()` giống nhau rằng chúng là một `Class<?>`.

## 12. Lỗi phổ biến cần tránh

| Nhầm lẫn | Điều đúng |
|---|---|
| “Hai loader load cùng `Foo` thì JVM luôn ném lỗi.” | Chỉ có xung đột khi ràng buộc liên kết đòi đồng nhất và chúng không thể thỏa. |
| “Cùng tên binary và cùng bytecode tức cùng runtime type.” | Defining loader khác nhau tạo hai runtime class identities khác nhau. |
| “Constraint được tạo cho mọi class name tại thời điểm class file vừa được tìm thấy.” | JVM áp đặt theo hoạt động preparation/resolution được quy định; thời điểm resolution có thể khác nhau. |
| “Có generic `List<Foo>` trong chữ ký là JVM luôn ép các loader dùng chung Foo.” | Descriptor sau erasure có thể chỉ chứa `java.util.List`; generic Signature có ngữ pháp riêng. |
| “Lỗi constraint luôn là `ClassCastException`.” | Vi phạm constraint là lỗi linking/loading dạng `LinkageError`; cast sai xảy ra tại runtime. |
| “Dùng tên class giống nhau trong hai plugin là cách chia sẻ DTO.” | Shared DTO phải dùng chung runtime definition thông qua loader phù hợp. |
| “Dùng parent-first là JVM không còn cần loading constraint.” | Constraint là bảo đảm ở tầng JVM; delegation là chiến lược lookup giúp thỏa nhiều constraint. |
| “Lỗi chỉ có thể xảy ra ngay lúc `new CustomLoader()`.” | Loader object creation khác class linking; lỗi có thể xuất hiện khi nạp, preparation hoặc resolution. |
| “Method descriptor chỉ gồm tham số, không gồm kiểu trả về.” | Method descriptor chứa tham số **và** return type, ví dụ `(LFoo;)LFoo;`. |
| “`LinkageError` nào cũng là loading constraint violation.” | `LinkageError` là nhóm lỗi rộng, cần xem subtype/cause/message và bối cảnh. |

## 13. Bài tập tự kiểm tra

**Câu 1.** Loader A và B cùng định nghĩa `com.example.Event`. Nếu chương trình không giao tiếp nào đòi thống nhất `Event`, việc đó tự động vi phạm loading constraint không?

**Đáp án:** Không. Hai runtime classes cùng tên có thể cùng tồn tại. Constraint chỉ phát sinh ở các tình huống được JVM chỉ định cần type-safe linkage giữa các loader.

**Câu 2.** Method `Event echo(Event input)` được biểu diễn bằng descriptor nào? Nếu hai loader tham gia liên kết method này, tên nào là ứng viên của constraint?

**Đáp án:** Với `Event` thuộc `com.example`, descriptor là `(Lcom/example/Event;)Lcom/example/Event;`. Tên kiểu tham chiếu `com.example.Event` là ứng viên khi JVM áp đặt constraint phù hợp.

**Câu 3.** Hai loader đều gọi `loadClass("java.lang.String")` qua parent delegation và nhận `String.class` của bootstrap. Constraint đòi hai bên thống nhất `String` có thỏa không?

**Đáp án:** Có, vì cùng một runtime class được sử dụng, mặc dù initiating loaders khác nhau.

**Câu 4.** Tại sao không dùng kết quả `clazzA.getName().equals(clazzB.getName())` để chứng minh hai class giống nhau?

**Đáp án:** `getName()` chỉ phản ánh tên, còn runtime identity còn phụ thuộc defining loader. So sánh `clazzA == clazzB` mới xác nhận cùng object `Class` đại diện cùng runtime type.

**Câu 5.** Vì sao `List<Event> fetch()` không nhất thiết áp đặt constraint trực tiếp cho `Event` chỉ từ method descriptor?

**Đáp án:** Generic argument `Event` bị erasure; descriptor JVM thường là `()Ljava/util/List;`. Việc xử lý `Event` ở mã khác vẫn có thể tạo symbolic references/constraints riêng.

**Câu 6.** Một class trong plugin override method dùng `api.Event` được định nghĩa ở loader cha. Cách triển khai nào giúp tránh xung đột?

**Đáp án:** Plugin loader ủy quyền namespace shared API (bao gồm `api.Event`) tới defining loader chung, nhờ vậy cả hai bên resolve cùng một class. Tránh định nghĩa copy `api.Event` bên plugin.

**Câu 7.** Một loading constraint đã được ghi nhận khi một loader chưa load `api.Event`. Loader này sau đó cố định nghĩa bản `api.Event` khác với bên kia. Có thể xảy ra gì?

**Đáp án:** Khi VM ghi nhận initiating loader/class mới và kiểm tra constraints, nếu không thể thỏa quan hệ bình đẳng, hoạt động nạp liên quan thất bại với `LinkageError`. Không cần giả định lỗi chắc chắn nổ ngay ở lần áp đặt constraint trước đó.

## 14. Tóm tắt kiến thức cốt lõi và tham khảo

Loading constraint là **quy tắc bảo toàn tính an toàn kiểu khi linking xuyên class loaders**. JVM có thể cho phép nhiều class cùng binary name tồn tại, nhưng khi các phương thức/field trong mối quan hệ liên kết phải dùng **cùng một kiểu tham chiếu**, VM yêu cầu tên đó resolve thành **cùng runtime class** ở các loader có liên quan.

Nhớ bốn điều:

1. **Class identity** = binary name + defining loader.
2. **Loading constraint** ký hiệu `N^L1 = N^L2`: cùng tên `N` phải chỉ cùng runtime type khi liên kết yêu cầu.
3. JVM áp đặt ràng buộc trong **preparation** (ví dụ override/method selection) và **resolution** (field/method/interface method); vi phạm gây **`LinkageError`** theo quy tắc của JVM.
4. **Shared API types** nên được định nghĩa một lần ở loader chung, để các plugin/caller dùng cùng runtime identity qua delegation thích hợp.

Tài liệu chuẩn:

- [JVMS 17 — `5.3.4` Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4): lý do, ký hiệu, thời điểm ghi nhận class và kiểm tra loading constraints.
- [JVMS 17 — `5.4.2` Preparation](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.2): áp đặt constraint cho phương thức có thể override và phương thức được chọn từ interface.
- [JVMS 17 — `5.4.3` Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3): các rule liên quan symbolic field, method và interface method references.
- [JVMS 17 — `4.3` Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3): ngữ pháp field/method descriptor.
- [Java SE 17 — `ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass` và định nghĩa class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-008">Lớp mảng, kiểu phần tử và loader tương ứng</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp mảng, kiểu phần tử và ClassLoader tương ứng trong Java

## 1. Vì sao mảng cũng có lớp runtime?

Một mảng Java là **đối tượng**. Khi chạy chương trình, mỗi đối tượng mảng có một lớp runtime riêng được JVM biểu diễn bằng `Class<?>`. Ví dụ `int[]`, `String[]`, `String[][]`, `plugin.Widget[]` đều có đối tượng `Class` tương ứng.

Điểm dễ gây nhầm là các lớp mảng **không có tệp `.class` riêng** như lớp thông thường. JVM tạo lớp mảng khi cần. Quan hệ với ClassLoader của lớp phần tử được quy định riêng:

- Nếu **kiểu phần tử là kiểu tham chiếu** (class, interface hoặc một kiểu mảng khác), lớp mảng được JVM tạo ra và gắn với **defining loader của kiểu phần tử đó**.
- Nếu **kiểu phần tử là primitive** (`int`, `boolean`, `byte`, v.v.), lớp mảng được JVM coi là do **bootstrap class loader** định nghĩa. `getClassLoader()` trả `null` để biểu diễn bootstrap.
- Với mảng nhiều chiều, xét **immediate component type**: thành phần trực tiếp của `Widget[][]` là `Widget[]`, rồi thành phần của `Widget[]` là `Widget`. Theo chuỗi này, defining loader của mảng nhiều chiều được xác định từ kiểu phần tử cơ sở.
- JVM không gọi `findClass("[Lplugin.Widget;")` của custom loader chỉ để đọc một tệp bytecode `Widget[].class`, vì **không có tệp như vậy** theo mô hình Java thông thường.

**Quy tắc nhớ:** *Lớp mảng của reference type đi theo defining loader của component type; lớp mảng primitive đi với bootstrap.* Đây là quy tắc về **định danh runtime**, không phải yêu cầu mảng phải được “định nghĩa” bằng `defineClass` như class file bình thường.

## 2. Phân biệt kiểu phần tử trực tiếp và kiểu phần tử cơ sở

Đối với mảng một chiều `T[]`, component type là `T`. Đối với `T[][]`, component type trực tiếp là `T[]`, còn kiểu phần tử cơ sở (base component type) là `T`.

| Kiểu biểu thức Java | Class runtime | `getComponentType()` | Kiểu phần tử cơ sở |
|---|---|---|---|
| `int[]` | `int[].class` | `int.class` | `int` |
| `String[]` | `String[].class` | `String.class` | `String` |
| `String[][]` | `String[][].class` | `String[].class` | `String` |
| `int[][][]` | `int[][][].class` | `int[][].class` | `int` |
| `Widget[][]` | `Widget[][].class` | `Widget[].class` | `Widget` |

`getComponentType()` trả `null` nếu gọi trên `Class` không biểu diễn một kiểu mảng, ví dụ `String.class`. Phương thức `Class.isArray()` cho biết lớp được hỏi có phải là array class.

Lưu ý rằng **kiểu mảng là kiểu tham chiếu**, kể cả `int[]`: biến mảng chứa tham chiếu tới một array object; chỉ **các phần tử bên trong** `int[]` mới là primitive. Vì thế `int[]` có thể gán cho `Object`, trong khi `int` bản thân nó không phải đối tượng.

### Ví dụ Java 17+ kiểm tra component type

Tạo file `ArrayComponentDemo.java`:

```java
public class ArrayComponentDemo {
    private static void describe(Class<?> type) {
        Class<?> component = type.getComponentType();
        System.out.println(type.getTypeName()
                + " | isArray=" + type.isArray()
                + " | component="
                + (component == null ? "<none>" : component.getTypeName()));
    }

    public static void main(String[] args) {
        describe(String.class);
        describe(String[].class);
        describe(String[][].class);
        describe(int[].class);
        describe(int[][][].class);
    }
}
```

Biên dịch và chạy:

```bash
javac ArrayComponentDemo.java
java ArrayComponentDemo
```

Stdout:

```text
java.lang.String | isArray=false | component=<none>
java.lang.String[] | isArray=true | component=java.lang.String
java.lang.String[][] | isArray=true | component=java.lang.String[]
int[] | isArray=true | component=int
int[][][] | isArray=true | component=int[][]
```

Ở đây dùng `Class.getTypeName()` vì biểu diễn thân thiện với người đọc. `Class.getName()` của array class lại dùng cú pháp **array descriptor**, xem mục kế tiếp.

## 3. Tên array class có dạng đặc biệt

`Class.getName()` không trả chuỗi `"java.lang.String[]"` cho mảng. Theo cách biểu diễn nhị phân trong JVM, nó dùng tiền tố `[` và ký hiệu kiểu thành phần.

| Biểu thức | `getName()` |
|---|---|
| `int[].class` | `[I` |
| `boolean[].class` | `[Z` |
| `byte[].class` | `[B` |
| `long[].class` | `[J` |
| `double[].class` | `[D` |
| `String[].class` | `[Ljava.lang.String;` |
| `String[][].class` | `[[Ljava.lang.String;` |
| `int[][].class` | `[[I` |

Các mã primitive trong descriptor là `Z` boolean, `B` byte, `C` char, `D` double, `F` float, `I` int, `J` long, `S` short. `L...;` biểu diễn tên binary của reference type, ví dụ `Ljava.lang.String;`. Mỗi dấu `[` thêm một chiều mảng. Đừng nhầm các chuỗi này với đường dẫn tệp hay tên lớp nguồn để truyền cho `ClassLoader.findClass`.

### Ví dụ in tên và loader

Tạo `ArrayNameDemo.java`:

```java
public class ArrayNameDemo {
    public static void main(String[] args) {
        System.out.println(int[].class.getName());
        System.out.println(String[].class.getName());
        System.out.println(String[][].class.getName());
        System.out.println(int[][].class.getName());
        System.out.println("int[] loader == null: "
                + (int[].class.getClassLoader() == null));
        System.out.println("String[] loader == null: "
                + (String[].class.getClassLoader() == null));
    }
}
```

Chạy:

```bash
javac ArrayNameDemo.java
java ArrayNameDemo
```

Stdout trên JVM Java SE chuẩn:

```text
[I
[Ljava.lang.String;
[[Ljava.lang.String;
[[I
int[] loader == null: true
String[] loader == null: true
```

Trong ví dụ, `String` là lớp nền tảng được bootstrap loader định nghĩa nên cả `String[]` cũng có defining loader tương ứng bootstrap. **Không phải mọi mảng reference type đều có `getClassLoader() == null`**: mảng chứa class do loader ứng dụng hoặc custom loader định nghĩa thường trả chính loader đó.

## 4. JVM tạo array class như thế nào?

Đối với class thông thường, loader có thể nhận bytecode rồi gọi `defineClass(...)` để định nghĩa một runtime class. Đối với **array class**, đặc tả JVMS `5.3.3` quy định JVM **tạo lớp mảng trực tiếp**.

Diễn giải khái niệm:

```text
Yêu cầu dùng array type T[]
            │
            ▼
Xác định component type T
            │
            ├── T là primitive
            │      └── array class do bootstrap định nghĩa
            │
            └── T là reference type
                   ├── T đã được định nghĩa bởi loader L
                   └── array class của T[] gắn với loader L
            │
            ▼
JVM có Class<?> cho array type T[]
            │
            ▼
new T[n] / Array.newInstance(T.class, n)
tạo array object theo lớp mảng tương ứng
```

Các câu trong sơ đồ là quan hệ **kiểu và định danh**, không nhất thiết là chuỗi gọi Java method thật. JVM có thể tạo/lưu array class theo nhu cầu, và dùng lại đối tượng `Class` đã có cho cùng kiểu mảng runtime.

Điểm quan trọng:

- `ClassLoader` hiện tại có thể **khởi xướng** việc yêu cầu một array class, nhưng **defining loader của array class** là loader của component reference type hoặc bootstrap nếu component là primitive.
- Không có bước Java API `defineClass` với bytes của `[L...;` trong cơ chế tạo array class chuẩn. Tự tạo file `[L...;.class` hay tự gọi `defineClass` với một tên mô tả mảng không phải cách hợp lệ để định nghĩa array type chuẩn.
- Hai lớp thành phần cùng binary name nhưng do defining loaders khác nhau tạo ra dẫn tới **hai array classes khác nhau**, ngay cả khi tên `Class.getName()` của hai mảng bằng nhau.
- Cùng runtime component class và cùng số chiều thì `T[]` được nhận diện như cùng một array class runtime; gọi `new T[1]` và `new T[10]` chỉ tạo **hai object**, không tạo hai định nghĩa lớp khác nhau.

## 5. Mối quan hệ với defining loader và initiating loader

**Defining ClassLoader** là loader chịu trách nhiệm định nghĩa class runtime. **Initiating ClassLoader** là loader được JVM ghi nhận khởi xướng việc tải class theo tên; một loader con có thể ủy quyền cho parent và trở thành initiating loader của class do parent định nghĩa.

Đối với array class, quy tắc gắn defining loader với component type vẫn giữ nguyên. Ví dụ:

```text
AppLoader định nghĩa app.Customer
        │
        └── JVM tạo app.Customer[] → defining loader AppLoader

PluginLoader định nghĩa plugin.Widget
        │
        └── JVM tạo plugin.Widget[] → defining loader PluginLoader

Bootstrap định nghĩa java.lang.String
        │
        └── JVM tạo java.lang.String[] → defining loader bootstrap

Kiểu primitive int
        │
        └── JVM tạo int[] → defining loader bootstrap
```

Khi đánh giá `someArray.getClass().getClassLoader()`, kết quả là **defining loader của array class**. Nó không nhất thiết là loader của lớp chứa dòng lệnh đang được thực thi. Nếu một ứng dụng host sử dụng reflection để tạo mảng từ `Class<?>` mà plugin loader định nghĩa, array class tương ứng vẫn thuộc plugin loader.

Ví dụ:

```java
// Trích đoạn giải thích, không phải chương trình độc lập:
Class<?> elementClass = pluginLoader.loadClass("plugin.Widget");
Object array = java.lang.reflect.Array.newInstance(elementClass, 3);
Class<?> arrayClass = array.getClass();

boolean sameLoader =
        arrayClass.getClassLoader() == elementClass.getClassLoader();
```

Với `elementClass` là reference class, `sameLoader` bằng `true` theo quy tắc định nghĩa lớp mảng. Điều này đúng cả khi `array` được tạo từ code của host loader khác.

## 6. Ví dụ đầy đủ: hai custom loaders, một tên phần tử, hai kiểu mảng

Ví dụ này cố ý dùng **hai loader khác nhau** định nghĩa class cùng binary name `demo.Widget`. Kết quả cho thấy tên lớp mảng giống nhau vẫn không làm chúng thành cùng runtime type.

### 6.1. Tệp class thành phần

Trong thư mục làm việc riêng, tạo `Widget.java`:

```java
package demo;

public final class Widget {
    static {
        System.out.println("Widget initialized");
    }

    public Widget() {
    }
}
```

Chương trình host sẽ không import trực tiếp `demo.Widget`: host dùng reflection để tránh cho application loader nạp lớp plugin.

### 6.2. Tệp host

Tạo `ArrayLoaderDemo.java` cạnh `Widget.java`:

```java
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ArrayLoaderDemo {
    static final class DirectoryLoader extends ClassLoader {
        private final Path root;
        private final String label;

        DirectoryLoader(Path root, ClassLoader parent, String label) {
            super(parent);
            this.root = root;
            this.label = label;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            Path classFile = root.resolve(
                    name.replace('.', '/') + ".class"
            );

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(classFile);
            } catch (IOException cause) {
                throw new ClassNotFoundException(name, cause);
            }

            System.out.println("define " + label + ": " + name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("plugin-classes");
        ClassLoader parent = ArrayLoaderDemo.class.getClassLoader();

        DirectoryLoader left = new DirectoryLoader(root, parent, "left");
        DirectoryLoader right = new DirectoryLoader(root, parent, "right");

        Class<?> leftElement = left.loadClass("demo.Widget");
        Class<?> rightElement = right.loadClass("demo.Widget");

        Class<?> leftArray = Array.newInstance(leftElement, 0).getClass();
        Class<?> rightArray = Array.newInstance(rightElement, 0).getClass();

        System.out.println("same element name: "
                + leftElement.getName().equals(rightElement.getName()));
        System.out.println("same element class: "
                + (leftElement == rightElement));
        System.out.println("same array name: "
                + leftArray.getName().equals(rightArray.getName()));
        System.out.println("same array class: "
                + (leftArray == rightArray));

        System.out.println("left array loader is left: "
                + (leftArray.getClassLoader() == left));
        System.out.println("right array loader is right: "
                + (rightArray.getClassLoader() == right));

        System.out.println("left array component is left element: "
                + (leftArray.getComponentType() == leftElement));
        System.out.println("descriptor lookup matches left array: "
                + (Class.forName("[Ldemo.Widget;", false, left) == leftArray));

        System.out.println("before making an element");
        Object instance = leftElement.getDeclaredConstructor().newInstance();
        System.out.println("left instance recognized: "
                + leftElement.isInstance(instance));
    }
}
```

### 6.3. Biên dịch và chạy đúng classpath

Chạy bằng JDK 17+ ở thư mục có hai file `.java`:

```bash
mkdir plugin-classes
javac -d plugin-classes Widget.java
javac ArrayLoaderDemo.java
java -cp . ArrayLoaderDemo
```

Sau compile, cấu trúc có dạng:

```text
working-directory/
├── Widget.java
├── ArrayLoaderDemo.java
├── ArrayLoaderDemo.class
├── ArrayLoaderDemo$DirectoryLoader.class
└── plugin-classes/
    └── demo/
        └── Widget.class
```

**Không thêm `plugin-classes` vào classpath khi chạy host.** Nếu thêm, loader cha có thể tìm `demo.Widget` và cả hai custom loaders cùng ủy quyền thành công cho cha, làm mất tình huống so sánh hai defining loaders độc lập.

### 6.4. Kết quả và diễn giải

Stdout theo chương trình này:

```text
define left: demo.Widget
define right: demo.Widget
same element name: true
same element class: false
same array name: true
same array class: false
left array loader is left: true
right array loader is right: true
left array component is left element: true
descriptor lookup matches left array: true
before making an element
Widget initialized
left instance recognized: true
```

Giải thích:

1. Cả hai loader có cùng root chứa bytecode `demo.Widget`, nhưng **mỗi loader riêng** gọi `defineClass` cho bản riêng. Vì vậy hai đối tượng `Class<?>` của `demo.Widget` khác nhau.
2. `Array.newInstance(leftElement, 0)` yêu cầu JVM sử dụng array class có phần tử `leftElement`. `rightElement` tạo một array class **khác**. Độ dài 0 vẫn là một array object hợp lệ với array class xác định.
3. Tên cả hai class phần tử đều `demo.Widget`; tên cả hai array classes đều `[Ldemo.Widget;`, nhưng `leftArray != rightArray`.
4. `leftArray.getClassLoader() == left` và `rightArray.getClassLoader() == right` cho thấy **defining loader của array class đi theo component reference type**.
5. `Class.forName("[Ldemo.Widget;", false, left)` giải quyết descriptor của array type theo component type mà loader `left` nhận diện; nó trả cùng lớp mảng `leftArray` và **không yêu cầu init component class**.
6. Những thao tác load class, tạo mảng rỗng và tra descriptor không in `Widget initialized`. Dòng đó chỉ in khi tạo instance của `leftElement` bằng reflection, kích hoạt **initialization** của lớp phần tử bên trái.
7. Lớp `demo.Widget` của loader bên phải trong ví dụ vẫn chưa được khởi tạo. Nếu tạo instance của nó sau đó, static initializer của **runtime class khác** này có thể chạy riêng.

**Chú ý:** Vì loader trong ví dụ kế thừa thuật toán parent-first, kết quả hai lớp độc lập dựa trên điều kiện host classpath **không chứa** `demo.Widget`. Đây là điểm cần xác minh nếu thử trong IDE hoặc build tool vốn tự mở rộng classpath.

## 7. Array type khác nhau ảnh hưởng cast và kiểm tra kiểu

Mỗi array object mang class runtime gắn với component class. Do đó việc cast mảng phải dùng **đúng runtime component type**, không chỉ so trùng tên kiểu.

Ví dụ với hai class ở mục 6:

```java
// Trích đoạn để thêm bên trong main của ArrayLoaderDemo:
Object fromLeft = Array.newInstance(leftElement, 1);
Object fromRight = Array.newInstance(rightElement, 1);

System.out.println(leftArray.isInstance(fromLeft));   // true
System.out.println(leftArray.isInstance(fromRight));  // false

try {
    leftArray.cast(fromRight);
} catch (ClassCastException ex) {
    System.out.println("cross-loader array cast rejected");
}
```

Đây là **trích đoạn** của `main`, không phải một file độc lập. Nếu thêm sau phần setup, stdout bổ sung là:

```text
true
false
cross-loader array cast rejected
```

Cả hai object có cùng tên runtime descriptor (ví dụ `[Ldemo.Widget;` khi gọi `getClass().getName()`) nhưng cast vẫn thất bại vì component runtime classes khác nhau. Điều này là bản chất của **type identity** trong JVM, không phải lỗi do Java không so sánh đúng chuỗi class name.

### ArrayStoreException và mảng hiệp biến

Mảng của reference types là **covariant**: `String[]` có thể gán vào biến `Object[]`. JVM kiểm tra store tại runtime để bảo đảm phần tử mới tương thích với component class thực.

Tạo `ArrayStoreDemo.java`:

```java
public class ArrayStoreDemo {
    public static void main(String[] args) {
        Object[] values = new String[2];
        values[0] = "ok";

        System.out.println(values[0]);
        try {
            values[1] = 123;
        } catch (ArrayStoreException ex) {
            System.out.println("rejected incompatible element");
        }
    }
}
```

Chạy:

```bash
javac ArrayStoreDemo.java
java ArrayStoreDemo
```

Stdout:

```text
ok
rejected incompatible element
```

`values` có kiểu biến `Object[]` nhưng object thực là `String[]`. Khi gán một `Integer` (autobox từ `123`), array store check dựa trên component runtime `String` và ném `ArrayStoreException`.

Tương tự, nếu viết code bytecode hoặc reflection cố lưu instance do loader bên phải định nghĩa vào array có component do loader bên trái định nghĩa, JVM sẽ từ chối. Với `java.lang.reflect.Array.set`, lỗi đối với phần tử không phù hợp thường là `IllegalArgumentException` của API reflection; đừng mặc định mọi API đều ném cùng một lớp exception.

**Mảng primitive không hiệp biến theo cùng cách:** `int[]` là đối tượng và là `Object`, nhưng **không phải** `Object[]` vì phần tử trực tiếp là primitive `int`, không phải reference.

## 8. Mảng đa chiều và loader theo kiểu cơ sở

Với `plugin.Widget[][][]`:

```text
plugin.Widget[][][]
   component: plugin.Widget[][]
       component: plugin.Widget[]
           component: plugin.Widget
               defining loader = PluginLoader
```

JVM tạo các array classes tương ứng; mọi cấp mảng tham chiếu ở chuỗi này có defining loader của `plugin.Widget`. Ví dụ:

```java
// Trích đoạn trong main của ví dụ custom loader:
Class<?> oneDimension = Array.newInstance(leftElement, 0).getClass();
Class<?> twoDimensions =
        Array.newInstance(leftElement, 0, 0).getClass();

System.out.println(twoDimensions.getComponentType() == oneDimension);
System.out.println(twoDimensions.getClassLoader() == left);
```

Stdout bổ sung:

```text
true
true
```

`Array.newInstance(component, dimensions...)` tạo array object nhiều chiều có runtime array type phù hợp. Khi dùng nhiều chiều, component type **trực tiếp** không còn `leftElement` mà là array type bớt một chiều; vẫn giữ cùng defining loader theo chuỗi component.

Với `int[][]`, immediate component là `int[]` (một reference array type), nhưng `int[]` do bootstrap định nghĩa, nên `int[][]` cũng có defining loader bootstrap. Không nên áp quy tắc “mảng nhiều chiều là reference component nên loader là AppLoader” nếu component trực tiếp đó vốn là một array class của primitive.

## 9. Mảng có cần khởi tạo lớp phần tử không?

Một nhầm lẫn phổ biến là **tạo mảng `new T[n]` có nghĩa JVM chạy ngay `T.<clinit>`**. Điều đó không đúng. Theo JLS, tạo một mảng có component reference type không tự nó yêu cầu khởi tạo lớp T.

Tạo `ArrayInitializationDemo.java`:

```java
public class ArrayInitializationDemo {
    static final class Element {
        static {
            System.out.println("Element initialized");
        }

        static void ping() {
            System.out.println("Element.ping");
        }
    }

    public static void main(String[] args) {
        System.out.println("before array");
        Element[] elements = new Element[2];
        System.out.println("after array: " + elements.length);
        System.out.println("first element is null: "
                + (elements[0] == null));

        System.out.println("before active use");
        Element.ping();
    }
}
```

Biên dịch và chạy:

```bash
javac ArrayInitializationDemo.java
java ArrayInitializationDemo
```

Stdout:

```text
before array
after array: 2
first element is null: true
before active use
Element initialized
Element.ping
```

Tại `new Element[2]`, JVM tạo array object có hai phần tử reference ban đầu `null`; **không tạo hai instance của Element** và không chạy static initializer của Element. Khi gọi phương thức static `Element.ping()`, JVM cần khởi tạo Element rồi mới chạy thân phương thức.

Với `int[]`, không có class initialization của một “lớp int” theo nghĩa class Java: primitive không có `<clinit>`. Các phần tử số nguyên trong mảng mới có giá trị mặc định `0`.

### Loading, linking, initialization là những chuyện khác nhau

- Để biểu diễn một array type, JVM cần xác định component type và có thể phải **load** class thành phần.
- Việc tạo hoặc lấy `Class<?>` của array type **không tự yêu cầu initialization** của reference component class.
- Khi sau đó gọi static method, đọc static field không phải constant variable hoặc tạo **instance của component class**, JVM có thể phải khởi tạo lớp thành phần.
- Gọi `Class.forName("[Ldemo.Widget;", false, loader)` cho descriptor mảng dùng loader để nhận diện component type nhưng **không chạy `Widget.<clinit>`** theo yêu cầu `initialize=false`. Không nhầm `ClassLoader.loadClass("demo.Widget")` (binary name của lớp) với việc đưa trực tiếp descriptor mảng vào custom loader.

## 10. Type identity, package và class loader

Đối với **class hoặc interface thông thường**, danh tính runtime không chỉ dựa vào binary name mà còn dựa vào defining loader. Với **array class**, danh tính phụ thuộc vào array component runtime type cùng số chiều. Vì vậy:

```text
PluginLoader-1 định nghĩa sample.Item
    └── sample.Item[] ở PluginLoader-1

PluginLoader-2 định nghĩa sample.Item
    └── sample.Item[] ở PluginLoader-2
```

Hai array class có cùng descriptor `[Lsample.Item;` về mặt văn bản nhưng không có cùng `Class<?>` và không cast qua lại như cùng kiểu. Đối với package-private access, một class cùng tên package được định nghĩa bởi loader khác **không tự thuộc cùng runtime package** với class kia. Đây là một quan hệ khác của danh tính kiểu JVM; array class có tính truy cập dựa trên component type của nó, chứ không xóa rào cản giữa các loader.

Có thể kiểm tra với:

```java
Class<?> a = someArray.getClass();
Class<?> c = a.getComponentType();

System.out.println(a.getName());
System.out.println(a.getClassLoader());
System.out.println(c == expectedComponent);
```

Đây là trích đoạn chẩn đoán; các biến `someArray` và `expectedComponent` cần được cung cấp trong chương trình thực. **`getName()` chỉ là chuỗi mô tả**. Để xác nhận cùng runtime array type, dùng so sánh `Class<?>` hoặc `isInstance`/`isAssignableFrom` với đúng đối tượng `Class`.

## 11. Những thuộc tính chung của mọi array class

Dù có component khác nhau, các array classes tuân theo một số quy tắc chung:

1. Superclass trực tiếp của mọi array class là `java.lang.Object`.
2. Mọi array class đều triển khai `java.lang.Cloneable` và `java.io.Serializable`.
3. Độ dài array object được đọc qua `array.length`; không có thao tác đổi kích thước mảng đã tạo.
4. `clone()` trên mảng tạo **bản sao nông**: các phần tử reference bên trong vẫn trỏ đến những object tương ứng ban đầu.
5. Array object có component runtime type cố định; kiểm tra kiểu khi gán vào mảng reference bảo vệ tương thích kiểu phần tử.
6. JVM tạo array class, nên **không có constructor Java bình thường do bạn khai báo** cho `String[]` hay `Widget[]`.

Tạo `ArrayPropertiesDemo.java`:

```java
import java.io.Serializable;

public class ArrayPropertiesDemo {
    public static void main(String[] args) {
        int[] values = {1, 2};

        System.out.println("superclass: "
                + values.getClass().getSuperclass().getName());
        System.out.println("Cloneable: " + (values instanceof Cloneable));
        System.out.println("Serializable: "
                + (values instanceof Serializable));
        System.out.println("as Object: " + (values instanceof Object));

        int[] copy = values.clone();
        System.out.println("distinct object: " + (copy != values));
        System.out.println("same array class: "
                + (copy.getClass() == values.getClass()));
    }
}
```

Chạy:

```bash
javac ArrayPropertiesDemo.java
java ArrayPropertiesDemo
```

Stdout:

```text
superclass: java.lang.Object
Cloneable: true
Serializable: true
as Object: true
distinct object: true
same array class: true
```

Một chỗ dễ nhầm: `new String[10]` không tạo 10 object `String`; nó tạo mảng có 10 ô tham chiếu ban đầu `null`. Tương tự, `new Widget[10]` không gọi 10 constructors của Widget.

## 12. Reflection và cách chọn đúng loader

Reflection tạo array từ đối tượng `Class<?>` giúp kiểm tra quy tắc loader mà không cần cast tĩnh sang tên class plugin:

```java
import java.lang.reflect.Array;

// Trích đoạn trong chương trình đã có componentClass:
Object instanceArray = Array.newInstance(componentClass, 5);
Class<?> arrayClass = instanceArray.getClass();

System.out.println(arrayClass.isArray());
System.out.println(arrayClass.getComponentType() == componentClass);
System.out.println(arrayClass.getClassLoader()
        == componentClass.getClassLoader());
```

Với `componentClass` là class/interface reference, cả ba biểu thức in `true`. Nếu `componentClass` là `int.class`, dòng so loader vẫn `true` vì `int.class.getClassLoader()` và `int[].class.getClassLoader()` đều là `null` trong API, biểu diễn primitive/bootstrap; nhưng không nên nói primitive `int` là class file do bootstrap nạp.

### Dùng Class.forName với descriptor của array type

API `Class.forName` có thể nhận **binary name** của class thông thường hoặc **tên descriptor của array class**, ví dụ:

```java
Class<?> one = Class.forName("[I");
Class<?> two = Class.forName("[Ljava.lang.String;");
Class<?> three = Class.forName("[[Ljava.lang.String;");

System.out.println(one == int[].class);
System.out.println(two == String[].class);
System.out.println(three == String[][].class);
```

Đây là trích đoạn hợp lệ khi đặt trong phương thức cho phép `throws ClassNotFoundException`; ba dòng `println` đều in `true` trên JVM Java SE chuẩn. Khi reference type là class plugin, hãy truyền **loader thích hợp** bằng overload ba tham số:

```java
// Trích đoạn: left là DirectoryLoader trong ArrayLoaderDemo
Class<?> arrayFromName =
        Class.forName("[Ldemo.Widget;", false, left);
```

Nếu truyền loader khác đã định nghĩa một `demo.Widget` riêng, bạn nhận array class của **component runtime class khác**, dù chuỗi descriptor giống nhau. Nếu đưa tên array descriptor cho `ClassLoader.loadClass(String)` của một custom loader, hành vi không phải cách portable để yêu cầu JVM tạo mảng; ưu tiên `Class.forName` hoặc `Array.newInstance` theo đúng mục đích.

## 13. Những lỗi chẩn đoán thường gặp

| Quan sát | Sai lầm dễ mắc | Giải thích đúng |
|---|---|---|
| `String[].class.getClassLoader() == null` | Mọi array class đều do bootstrap định nghĩa | String do bootstrap định nghĩa; mảng plugin reference type đi theo loader plugin |
| Hai array classes có cùng `getName()` | Hai mảng có cùng runtime type | Cần so component runtime class và defining loader; tên chữ không đủ |
| `Array.newInstance(widgetClass, 0)` thành công | Widget đã chạy static initializer | Tạo array không tự init class phần tử |
| `new Widget[3]` | JVM tạo ba Widget objects | JVM chỉ tạo ba ô tham chiếu mặc định null |
| Loader gọi `findClass("[L...;")` | Array class luôn có bytecode riêng trên ổ đĩa | JVM tạo array class trực tiếp; không có file array `.class` tương ứng |
| `int[]` là reference array object | Có thể cast thành `Object[]` | `int[]` là object nhưng component primitive, nên không phải `Object[]` |
| Hai loader nạp cùng file Widget.class | Kết quả vẫn cùng runtime class | Hai defining loaders riêng tạo hai component classes và hai array classes |
| `Class.forName("[Lplugin.Widget;", false, loader)` | Chắc chắn chạy Widget static initializer | Không yêu cầu initialization của Widget |
| `getComponentType()` của `Widget[][]` | Luôn trả `Widget.class` | Trả `Widget[].class`; cần lặp để đến kiểu cơ sở |

### Debug thực tế với plugin

Khi gặp `ClassCastException` hoặc lỗi tương thích API liên quan đến mảng trong hệ plugin/container:

1. In `array.getClass().getName()` để nhìn descriptor và số chiều.
2. In `array.getClass().getClassLoader()` để biết defining loader của array class.
3. Lặp `getComponentType()` để xác định component trực tiếp và component cơ sở; ở mỗi cấp xem `getClassLoader()`.
4. Ở hai phía giao tiếp host/plugin, so sánh **`Class<?>` bằng `==`**, không chỉ chuỗi tên.
5. Kiểm tra liệu cả hai loaders có vô tình định nghĩa lại cùng class API chung hay không. Parent-first cho các API chia sẻ giúp cùng sử dụng một runtime component type.
6. Nếu cần plugin class riêng mà array phải chuyển qua API chung, thiết kế boundary dùng `Object[]`, interface chung do parent định nghĩa hoặc dữ liệu trung gian một cách có kiểm tra kiểu; đừng ép cast mảng giữa hai plugin class khác defining loader.
7. Phân biệt lỗi **array store**, lỗi **cast**, lỗi **load/define class** và lỗi **initialization** để chọn phương án sửa đúng.

## 14. Câu hỏi tự kiểm tra

1. **Ai tạo lớp `Widget[]`?** JVM tạo trực tiếp; nó không đọc một file `Widget[].class`.
2. **`Widget` do loader L định nghĩa thì `Widget[]` có defining loader nào?** L.
3. **`int[]` có `getClassLoader()` là gì?** `null`, biểu diễn bootstrap đối với array class primitive.
4. **Component type của `String[][]` là gì?** `String[].class`; kiểu cơ sở là `String.class`.
5. **Hai lớp `p.Widget` cùng tên do L1 và L2 định nghĩa, `p.Widget[]` của chúng có cùng `Class<?>` không?** Không.
6. **Tên `Class.getName()` của hai mảng `p.Widget[]` ở hai loader có thể giống nhau không?** Có, cùng chuỗi `[Lp.Widget;` nhưng type identity khác.
7. **`new Widget[5]` có gọi constructor Widget 5 lần không?** Không; phần tử reference ban đầu là `null`.
8. **Tạo `Widget[]` có buộc chạy `Widget.<clinit>` không?** Không; thao tác tạo mảng tự nó không yêu cầu khởi tạo Widget.
9. **`int[]` có thể gán cho `Object` không? Có thể cast thành `Object[]` không?** Có thể gán cho `Object`; không thể cast thành `Object[]`.
10. **Khi biết `Class<?>` của component plugin, nên tạo mảng bằng gì?** `Array.newInstance(componentClass, length)`; JVM chọn array type đúng runtime component.
11. **`String[].class.getSuperclass()` là gì?** `Object.class`; array class đồng thời triển khai `Cloneable` và `Serializable`.
12. **Muốn biết chính xác mảng từ plugin loader nào, có đủ dùng `array.getClass().getName()` không?** Không; cần so đối tượng `Class` và xem `getClassLoader()`.

## Tài liệu chuẩn

- [Java Virtual Machine Specification — `5.3.3` Creating Array Classes](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3.3): JVM tạo array classes và chọn defining loader từ component type.
- [Java Virtual Machine Specification — `5.3` Creation of Classes and Interfaces](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): defining loader, initiating loader và runtime identity.
- [Java Language Specification — `10` Arrays](https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html): kiểu mảng, thành phần, hiệp biến, kiểu runtime và array creation.
- [Java Language Specification — `12.4` Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4): phân biệt array creation với active use cần class initialization.
- [Java SE 21 API — `java.lang.Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): `isArray`, `getComponentType`, `getName`, `getTypeName`, `getClassLoader`.
- [Java SE 21 API — `java.lang.reflect.Array`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Array.html): tạo array bằng reflection từ component `Class<?>`.

Khi làm việc với classloader và mảng, hãy xét đồng thời **kiểu phần tử thực ở runtime**, **số chiều mảng** và **defining loader của kiểu phần tử**. Ba thông tin này quyết định array type thực sự; **tên chuỗi của kiểu thôi chưa đủ**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-009">Kiểu nguyên thủy, void và giới hạn của loadClass</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu nguyên thủy, void và giới hạn của loadClass

## 1. Vấn đề: tại sao có int.class nhưng không loadClass("int")?

Trong Java, **kiểu** và **class có thể được nạp từ bytecode** không phải cùng một tập hợp. Ngôn ngữ Java có tám kiểu nguyên thủy (*primitive types*): `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`. JVM còn biểu diễn `void` cho trường hợp phương thức không trả giá trị. Trong khi đó, `java.lang.ClassLoader.loadClass(String)` được thiết kế để tìm class/interface theo **binary name**, ví dụ `java.lang.String` hoặc `com.example.Account`.

Do đó:

- `int.class`, `boolean.class` và `void.class` là các biểu thức Java hợp lệ, trả về đối tượng `Class<?>` đặc biệt do VM hỗ trợ.
- `ClassLoader.loadClass("int")` hoặc `Class.forName("int")` không phải API để nhận đại diện của primitive `int`.
- `ClassLoader.loadClass("void")` hoặc `Class.forName("void")` không phải API để nhận `void.class`.
- `Integer.class` là **wrapper class**, khác `int.class`; `Void.class` là wrapper/reference class, khác `void.class`.
- `int[]` là một **array reference type**: dù phần tử là primitive, bản thân mảng là object và có array class do JVM tạo khi cần.

Bài này làm rõ ngữ nghĩa đó, sự khác biệt giữa `ClassLoader.loadClass` và `Class.forName`, cũng như cách reflection và JVM descriptors biểu diễn primitive, `void` và mảng.

## 2. Tám primitive types và Class<?> đại diện

Các kiểu primitive có tên và descriptor như sau:

| Kiểu Java | JVM descriptor | Ví dụ giá trị |
|---|---|---|
| `boolean` | `Z` | `true` |
| `byte` | `B` | `(byte) 1` |
| `short` | `S` | `(short) 2` |
| `char` | `C` | `'A'` |
| `int` | `I` | `42` |
| `long` | `J` | `42L` |
| `float` | `F` | `3.5f` |
| `double` | `D` | `3.5d` |

Primitive value không phải một object có identity theo cách của reference type. Khi boxing, Java có thể biểu diễn một giá trị bằng một object wrapper như `Integer`, nhưng `Integer` và `int` là hai kiểu khác nhau.

Với reflection, `Class<?>` vẫn đại diện cho primitive types. Java API chuẩn cung cấp:

```java
Class<?> intType = int.class;
Class<?> longType = long.class;
Class<?> boolType = boolean.class;

boolean same = int.class == Integer.TYPE;  // true
boolean wrapper = int.class == Integer.class; // false
```

`Integer.TYPE` là trường `public static final Class<Integer> TYPE`? **Không nên suy ra kiểu generic cụ thể của trường đó**; điều cần dùng là giá trị `Class<?>` đại diện `int`. Java API định nghĩa trường `TYPE` trong wrapper để trả về đối tượng `Class` của primitive tương ứng.

### Vì sao Class có thể đại diện primitive?

`Class` là API phản chiếu của JVM; nó không chỉ đại diện class/interface được nạp từ file `.class`. JVM có các object `Class` dựng sẵn cho tám primitive và `void`. Các object này **không được tạo từ những file có tên `int.class`, `boolean.class`, `void.class` trên class path**.

Điều này giải thích được cả hai sự thật cùng lúc:

```java
System.out.println(int.class.isPrimitive()); // true
System.out.println(int.class.getClassLoader()); // null
```

Trong API `Class.getClassLoader()`, primitive và `void` trả `null`. Đó **không phải bằng chứng có bootstrap loader đã đọc một file int.class**. Giá trị `null` còn có ý nghĩa biểu diễn bootstrap loader đối với các class thực sự được bootstrap định nghĩa như `java.lang.String` trên JDK thông thường. Cùng kết quả API có thể tương ứng các cơ chế hình thành type khác nhau.

## 3. void đặc biệt như thế nào?

`void` là kiểu đặc biệt biểu thị không có giá trị trả về. Nó có thể xuất hiện trong khai báo method:

```java
public static void log(String message) {
    System.out.println(message);
}
```

Nhưng không thể viết `void value;`, `new void[3]` hoặc dùng `void` làm tham số method trong Java source.

JDK vẫn cung cấp:

```java
Class<?> voidType = void.class;
Class<?> viaWrapper = Void.TYPE;
System.out.println(voidType == viaWrapper); // true
System.out.println(void.class.isPrimitive()); // true
```

Theo Java `Class.isPrimitive()`, có **chín đối tượng `Class` dựng sẵn** cho tám primitive và `void`; cả chín đều trả `true` với `isPrimitive()`. Điều đó **không có nghĩa** một biến có thể chứa giá trị `void` như chứa `int`.

Phân biệt:

| Biểu thức | Đại diện cho |
|---|---|
| `void.class` | Kiểu return đặc biệt `void` |
| `Void.TYPE` | Cùng đối tượng `Class` với `void.class` |
| `Void.class` | Reference class `java.lang.Void` |
| `int.class` | Primitive `int` |
| `Integer.class` | Reference class `java.lang.Integer` |

Wrapper `Void` tồn tại trong thư viện chuẩn. `ClassLoader.loadClass("java.lang.Void")` có thể tìm class đó, nhưng đây **không phải** cách lấy `void.class`.

## 4. Giới hạn thực sự của ClassLoader.loadClass(String)

Theo Java SE 17 API, `ClassLoader.loadClass(String)` nhận **binary name của class/interface**, không phải mã descriptor của JVM và không phải Java source expression bất kỳ.

Ví dụ hợp lệ:

```java
ClassLoader loader = ClassLoader.getSystemClassLoader();

Class<?> s = loader.loadClass("java.lang.String");
Class<?> list = loader.loadClass("java.util.ArrayList");
```

Bảng sau dễ nhớ:

| Chuỗi truyền vào | Có phải tên class/interface phù hợp? | Ghi chú |
|---|---|---|
| `java.lang.String` | Có | Binary name của class |
| `java.util.Map$Entry` | Có | Binary name của nested interface |
| `com.example.Service` | Có nếu class đó tồn tại và loader thấy được | Tìm qua loader được chọn |
| `int` | Không | Primitive type, không có ordinary class file tên này |
| `void` | Không | Kiểu return đặc biệt |
| `I` | Không | JVM descriptor của `int` |
| `Ljava/lang/String;` | Không | JVM field descriptor của class |
| `java/lang/String` | Không | Internal name, không phải binary name |
| `[I` | Không thuộc binary-name contract của `loadClass` | Dùng `Class.forName("[I")` hoặc `int[].class` cho mảng |

`loadClass(name)` tương đương `loadClass(name, false)`. Với implementation mặc định, class loader kiểm tra class đã được nạp, thử nhờ parent (hoặc bootstrap nếu parent là `null`), rồi tới `findClass`. Custom loader có thể override lookup, nhưng không làm primitive biến thành ordinary class file.

### Khi loadClass thất bại

Nếu class không nằm trong nguồn tìm kiếm mà loader nhìn thấy, kết quả thông thường là `ClassNotFoundException`:

```java
try {
    loader.loadClass("int");
} catch (ClassNotFoundException ex) {
    System.out.println("Không có class int để load");
}
```

Đây là **đoạn minh họa** cần có biến `loader` trong scope. Nếu muốn chương trình hoàn chỉnh, dùng ví dụ ở phần 7.

Lỗi trên không cho thấy JVM thiếu kiểu `int`. Muốn lấy `Class<?>` của primitive, dùng `int.class` hoặc `Integer.TYPE`.

## 5. Class.forName và các tên mảng: ngoại lệ cần nắm

`Class.forName("java.lang.String")` là cách thường dùng để lấy `Class<?>` từ binary name của một class. Nhưng `Class.forName("int")`, `Class.forName("void")` không biến các từ khóa Java thành type tokens; các lời gọi đó sẽ ném `ClassNotFoundException` trên JDK tiêu chuẩn.

`Class.forName` còn hỗ trợ **tên mảng** theo cú pháp đặc biệt:

```java
Class<?> primitiveArray = Class.forName("[I");
Class<?> referenceArray =
        Class.forName("[Ljava.lang.String;");
```

Kết quả:

- `primitiveArray == int[].class` là `true`.
- `referenceArray == String[].class` là `true`.
- `Class.forName("[[I")` lấy `int[][].class`.
- `Class.forName("[Ljava.lang.String;")` dùng **dấu chấm** trong tên element class.
- `Class.forName("[Ljava/lang/String;")` không phải cú pháp chuẩn để tra cứu mảng tham chiếu này qua `Class.forName`.

Lý do là các array class có **cách đặt tên runtime** riêng mà `Class.getName()` cũng sử dụng. Với ordinary class/interface, `Class.forName` vẫn dùng binary name.

### So sánh Class.forName và loadClass

| API | Trường hợp class thường | Primitive/void | Mảng |
|---|---|---|---|
| `SomeType.class` | Dùng khi biết kiểu ở compile time | `int.class`, `void.class` hợp lệ | `int[].class`, `String[].class` hợp lệ |
| `Class.forName(name)` | Nhận binary name | Không diễn giải `"int"`, `"void"` | Nhận tên mảng như `"[I"`, `"[Ljava.lang.String;"` |
| `ClassLoader.loadClass(name)` | Nhận binary name class/interface | Không có ordinary class file primitive/void | Không dùng array descriptor như hợp đồng API chuẩn |
| `Array.newInstance(component, n)` | Không để load class thường | Có thể dùng primitive component như `int.class` | Tạo array object, array class được runtime cung cấp |

`Class.forName(name, initialize, loader)` còn cho phép chọn cờ initialization và loader dùng để lookup. `initialize=false` **không** biến primitive-name lookup thành hợp lệ; `loader=null` thường chỉ bootstrap lookup đối với class.

## 6. Mảng primitive là object, không phải primitive

Một trong những điểm quan trọng nhất:

```java
int count = 3;        // primitive value
int[] values = new int[3]; // reference tới array object
```

Mảng `int[]` là **reference type** và là object của JVM. Có thể chứng minh:

```java
System.out.println(int.class.isPrimitive());     // true
System.out.println(int[].class.isPrimitive());   // false
System.out.println(int[].class.isArray());       // true
System.out.println(values instanceof Object);   // true
System.out.println(int[].class.getComponentType() == int.class); // true
```

Đoạn mã trên cần biến `values` được khai báo như ví dụ. Nếu cần tạo mảng bằng reflection:

```java
Object array = java.lang.reflect.Array.newInstance(int.class, 3);
System.out.println(array.getClass() == int[].class); // true
```

Không có mảng `void[]`. `Array.newInstance(void.class, 3)` không được phép và ném `IllegalArgumentException`.

### Ai tạo array class?

Java API `ClassLoader` quy định **array classes được runtime tạo khi cần**, thay vì loader tạo từ dữ liệu bytecode của một ordinary class file. `Class.getClassLoader()` của array class tương ứng với loader của **element type** nếu là reference type; nếu element type là primitive thì kết quả là `null`.

Ví dụ:

- `String[].class.getClassLoader()` thường trả `null` trên JDK 17, vì `String` được bootstrap định nghĩa.
- `int[].class.getClassLoader()` trả `null`, nhưng không có nghĩa bootstrap đã đọc `[I.class`.
- Nếu `plugin.Message` do loader P định nghĩa, array class `plugin.Message[]` tương ứng có loader theo element class đó. Hai plugin loaders định nghĩa hai `plugin.Message` riêng có thể dẫn tới hai array types khác nhau.
- `int[][]` vẫn là reference type, có tên dạng `[[I` và component type trực tiếp `int[]`.

## 7. Ví dụ Java 17 đầy đủ: phân biệt primitive, void, array và loader

Tạo file **`PrimitiveAndLoaderDemo.java`**:

```java
public class PrimitiveAndLoaderDemo {
    public static void main(String[] args) throws Exception {
        ClassLoader app = PrimitiveAndLoaderDemo.class.getClassLoader();

        System.out.println("int primitive: " + int.class.isPrimitive());
        System.out.println("void primitive: " + void.class.isPrimitive());
        System.out.println("Integer primitive: "
                + Integer.class.isPrimitive());
        System.out.println("int equals TYPE: "
                + (int.class == Integer.TYPE));
        System.out.println("void equals TYPE: "
                + (void.class == Void.TYPE));

        System.out.println("int loader null: "
                + (int.class.getClassLoader() == null));
        System.out.println("void loader null: "
                + (void.class.getClassLoader() == null));

        System.out.println("int descriptor: "
                + int.class.descriptorString());
        System.out.println("void descriptor: "
                + void.class.descriptorString());

        System.out.println("int[] array: " + int[].class.isArray());
        System.out.println("int[] primitive: "
                + int[].class.isPrimitive());
        System.out.println("int[] loader null: "
                + (int[].class.getClassLoader() == null));
        System.out.println("int[] component is int: "
                + (int[].class.getComponentType() == int.class));

        System.out.println("Find [I: "
                + (Class.forName("[I") == int[].class));
        System.out.println("Find String[]: "
                + (Class.forName("[Ljava.lang.String;")
                        == String[].class));

        try {
            Class.forName("int");
            System.out.println("Unexpected forName success");
        } catch (ClassNotFoundException expected) {
            System.out.println("forName(int): not found");
        }

        try {
            app.loadClass("void");
            System.out.println("Unexpected loadClass success");
        } catch (ClassNotFoundException expected) {
            System.out.println("loadClass(void): not found");
        }

        System.out.println("String class by loader: "
                + (app.loadClass("java.lang.String") == String.class));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên trên class path thông thường:

```bash
javac PrimitiveAndLoaderDemo.java
java PrimitiveAndLoaderDemo
```

Stdout trên JDK 17 chuẩn:

```text
int primitive: true
void primitive: true
Integer primitive: false
int equals TYPE: true
void equals TYPE: true
int loader null: true
void loader null: true
int descriptor: I
void descriptor: V
int[] array: true
int[] primitive: false
int[] loader null: true
int[] component is int: true
Find [I: true
Find String[]: true
forName(int): not found
loadClass(void): not found
String class by loader: true
```

**Cách đọc kết quả:**

1. `int.class` và `void.class` được VM hỗ trợ, còn `Integer.class` là reference class.
2. `int.class == Integer.TYPE` và `void.class == Void.TYPE` đều đúng.
3. `getClassLoader() == null` đối với primitive và `void` không nói rằng có một class file tương ứng đã được bootstrap tìm đọc.
4. `int[]` là array reference type, nhận `Class<?>` của riêng nó.
5. `Class.forName("[I")` và `Class.forName("[Ljava.lang.String;")` hợp lệ do tên mảng theo API.
6. Lookup `"int"` và `"void"` bằng API tìm class theo binary name không cho ra primitive/void tokens.

Nếu dùng custom system class loader với chính sách không chuẩn, một vài phép lookup có thể hoạt động khác. Đoạn mẫu chủ ý sử dụng cách chạy mặc định trên JDK 17.

## 8. JVM descriptor khác Class.getName như thế nào?

Bytecode dùng **field descriptor** và **method descriptor** để biểu diễn kiểu. Với Java SE 17, `Class.descriptorString()` là cách lấy descriptor của một kiểu từ đối tượng `Class<?>`.

| Kiểu | `Class.getName()` | `Class.descriptorString()` |
|---|---|---|
| `int` | `int` | `I` |
| `long` | `long` | `J` |
| `boolean` | `boolean` | `Z` |
| `void` | `void` | `V` |
| `String` | `java.lang.String` | `Ljava/lang/String;` |
| `int[]` | `[I` | `[I` |
| `int[][]` | `[[I` | `[[I` |
| `String[]` | `[Ljava.lang.String;` | `[Ljava/lang/String;` |
| `String[][]` | `[[Ljava.lang.String;` | `[[Ljava/lang/String;` |

Trong field descriptor, `I` biểu diễn `int`. `V` chỉ hợp lệ làm **method return type**, không phải field type hoặc method parameter type. Một method:

```java
public static void print(int count, String[] labels) {
}
```

có descriptor:

```text
(I[Ljava/lang/String;)V
```

Phần trong `(...)` có một tham số `int` và một tham số `String[]`; `V` sau `)` biểu diễn không có giá trị trả về.

### Phân biệt ba cách viết tên String

- `java.lang.String`: binary name thường dùng với `loadClass`.
- `java/lang/String`: internal name trong metadata JVM.
- `Ljava/lang/String;`: field descriptor của reference type `String`.

Không truyền `Ljava/lang/String;` vào `loadClass` rồi kỳ vọng Java tự hiểu thành `java.lang.String`.

## 9. Ví dụ Java 17: phản chiếu method void mà không dùng loadClass("void")

Tạo file **`VoidMethodDemo.java`**:

```java
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class VoidMethodDemo {
    public static void ping(int count) {
    }

    public static void main(String[] args) throws Exception {
        Method method = VoidMethodDemo.class.getDeclaredMethod(
                "ping", int.class);

        System.out.println("Return type is void: "
                + (method.getReturnType() == void.class));

        MethodType type = MethodType.methodType(
                method.getReturnType(), method.getParameterTypes());

        System.out.println("Method descriptor: "
                + type.descriptorString());
    }
}
```

Chạy:

```bash
javac VoidMethodDemo.java
java VoidMethodDemo
```

Stdout:

```text
Return type is void: true
Method descriptor: (I)V
```

Ví dụ cho thấy reflection lấy kiểu trả về `void` bằng `void.class`; `MethodType` biểu diễn được `void` trong vị trí **return**. Không phải có một ordinary class `void` được nạp bằng `ClassLoader.loadClass`.

## 10. Những giới hạn khác của loadClass cần nhớ

### Không tạo instance

`loader.loadClass("com.example.Service")` trả về `Class<?>`; không tự chạy constructor để tạo một `Service` instance. Phải dùng một cơ chế tạo đối tượng phù hợp mới có instance.

### Không mặc định khởi tạo class

`ClassLoader.loadClass(String)` tương đương `loadClass(name, false)`, tức không yêu cầu resolution bằng cờ `resolve=true` và cũng **không yêu cầu class initialization như active use**. Loading, linking, initialization là các mốc khác nhau. `Class.forName(name)` overload một tham số thông thường yêu cầu initialization cho class được tìm.

### Không bỏ qua visibility hoặc module boundaries

Loader không nhìn thấy class/module cần thiết có thể thất bại `ClassNotFoundException`. Lấy `Class<?>` không chứng minh method, constructor hoặc field của class đó truy cập được từ mọi nơi; access checks và JPMS vẫn áp dụng.

### Không biến wrapper thành primitive

`loadClass("java.lang.Integer")` trả về wrapper `Integer.class`, không trả `int.class`. Boxing/unboxing là quy tắc của ngôn ngữ và runtime, không phải “dịch tên” trong class loader.

### Không quyết định mọi giai đoạn linking

Một class có thể được tìm nhưng sau đó lỗi resolution, preparation hoặc initialization khi được dùng, ví dụ thiếu dependency, `NoClassDefFoundError`, `ExceptionInInitializerError`, `LinkageError`. `loadClass` thành công chưa đảm bảo toàn bộ hành vi ứng dụng thành công.

### Không tạo array class từ file .class

Array classes do runtime tạo. Dùng `int[].class`, `Class.forName("[I")` hoặc `Array.newInstance(int.class, length)`; không tự suy rằng tồn tại file `[I.class` hoặc rằng `loadClass("[I")` là contract để truy cập mảng.

## 11. Những lỗi phổ biến

| Nhầm lẫn | Cách hiểu đúng |
|---|---|
| “Có `int.class` tức có file `int.class`.” | Primitive Class objects do VM tạo sẵn, không cần bytecode class file thông thường. |
| “`Class.forName("void")` phải bằng `Void.class`.” | `Void.class` là wrapper reference class; `void.class` và `Void.TYPE` mới là đại diện cho void. |
| “`int.class` và `Integer.class` là cùng kiểu.” | Một primitive, một reference wrapper; `==` là false. |
| “`void.class.isPrimitive()` chắc phải false.” | API tính cả void trong chín primitive-related Class objects; kết quả true. |
| “`int[]` là primitive vì element là int.” | Array là reference type; `int[].class.isPrimitive()` là false. |
| “Mọi `getClassLoader() == null` chứng minh bootstrap đã đọc class file.” | Primitive/void và primitive arrays cũng trả null theo quy tắc API nhưng không có ordinary class file tương ứng. |
| “`loadClass("[I")` là API chuẩn nạp array.” | `loadClass` nhận binary name class/interface; dùng `Class.forName("[I")` để truy cập array class theo cú pháp được hỗ trợ. |
| “`loadClass` thành công đã gọi static initializer.” | Loading và initialization là hai việc khác nhau. |
| “JVM descriptor `Ljava/lang/String;` là binary name.” | Binary name của class đó là `java.lang.String`. |
| “Có thể khai báo `void[]` hoặc `void` parameter.” | Void chỉ dùng đúng ngữ cảnh method return và API type token, không là array component hay parameter type. |

## 12. Bài tập tự kiểm tra

**Câu 1.** `int.class` tồn tại, nhưng vì sao `ClassLoader.loadClass("int")` không phải cách hợp lệ để truy cập nó?

**Đáp án:** Primitive `int` không phải class/interface có bytecode `.class` để loader tìm theo binary name. `int.class` là đối tượng Class đặc biệt của VM; có thể dùng `Integer.TYPE`.

**Câu 2.** `void.class.isPrimitive()` in gì? Có thể tạo `new void[3]` không?

**Đáp án:** In `true` theo `Class.isPrimitive()`. Không, `void[]` không phải array type hợp lệ.

**Câu 3.** `Integer.TYPE == Integer.class` có đúng không?

**Đáp án:** Không. `Integer.TYPE == int.class` là true; `Integer.class` là wrapper reference class.

**Câu 4.** `Class.forName("[I")` trả kiểu gì? `Class.forName("int")` có trả cùng kiểu đó không?

**Đáp án:** `Class.forName("[I")` trả `int[].class`. `Class.forName("int")` tìm không được primitive theo binary-name lookup.

**Câu 5.** `String[].class.getName()` và `String[].class.descriptorString()` khác nhau chỗ nào?

**Đáp án:** Cái thứ nhất là `[Ljava.lang.String;` với dấu chấm; cái thứ hai là `[Ljava/lang/String;` với dấu gạch chéo trong internal name.

**Câu 6.** Khi `int[].class.getClassLoader() == null`, có thể kết luận một file `[I.class` nằm trong bootstrap class path không?

**Đáp án:** Không. Array class được runtime tạo; primitive array có null loader theo quy tắc JVM/API.

**Câu 7.** Descriptor của method `void notify(int count, String[] names)` là gì?

**Đáp án:** `(I[Ljava/lang/String;)V`. `I` cho `int`, `[Ljava/lang/String;` cho `String[]` và `V` là kiểu trả về `void`.

**Câu 8.** Vì sao `ClassLoader.loadClass("java.lang.Integer")` không thay thế được `int.class`?

**Đáp án:** Nó lấy định nghĩa của wrapper class `Integer`, vốn là một reference type khác với primitive `int`.

## 13. Ghi nhớ và tài liệu tham khảo

- **Tám primitive + void** có `Class<?>` đặc biệt do VM tạo; không có binary-name class files tương ứng để nạp qua `loadClass("int")` hoặc `loadClass("void")`.
- **Array** là reference type dù element primitive. Array classes do runtime tạo; `int[]` khác `int`.
- **ClassLoader.loadClass(String)** tìm class/interface bằng binary name và chịu loader visibility; không tự tạo instance hoặc yêu cầu initialization.
- **Class.forName** hỗ trợ ordinary class binary names và tên array như `[I`, `[Ljava.lang.String;`, nhưng không diễn giải primitive token như `"int"`.
- **Descriptor** của primitive `int` là `I`, của `void` ở method return là `V`; khác Java source spelling và binary name.

Nguồn chính thức:

1. [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `isPrimitive`, `getName`, `getClassLoader`, `forName`, `descriptorString`.
2. [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, parent delegation, cách JVM tạo array classes.
3. [Java SE 17 — Void](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Void.html): `Void.TYPE` và wrapper class.
4. [JVMS 17 — Types and Values](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html): primitive, reference và array types.
5. [JVMS 17 — Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3): field/method descriptor, cú pháp primitive/void.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-identity-010">Thiết kế kiểu API dùng chung tại ranh giới nhiều loader</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế kiểu API dùng chung tại ranh giới nhiều loader

Khi một ứng dụng Java có nhiều **class loader** — chẳng hạn host ứng dụng và những loader riêng cho plugin — một lớp có tên `api.Greeter` có thể xuất hiện ở nhiều nơi. Điều quan trọng là: **hai class có cùng binary name nhưng do hai defining loader khác nhau định nghĩa không phải cùng một kiểu ở thời gian chạy**. Đặc tính này quyết định trực tiếp cách thiết kế API dùng chung, gọi callback, truyền DTO, xử lý exception và nạp phiên bản plugin.

**Mục tiêu của bài:** tạo một ranh giới host–plugin mà cả hai phía dùng **cùng đối tượng `Class<?>`** cho contract cần chia sẻ, trong khi implementation và dependency riêng vẫn có thể được định nghĩa trong loader của plugin. Từ nguyên tắc này, ta sẽ thiết kế dependency, chọn parent/delegation, kiểm chứng bằng `==`/`isAssignableFrom`, xem lỗi thường gặp và chạy các ví dụ Java 17+.

## 1. Vấn đề gốc: "cùng tên" chưa đủ để là "cùng kiểu"

Theo mô hình JVM, một reference class/interface ở runtime được định danh bởi cặp:

```text
(binary name, defining class loader)
```

Ví dụ cùng có binary name `api.Greeter`:

```text
AppLoader     defines api.Greeter   → type A
PluginLoader  defines api.Greeter   → type B

A.getName().equals(B.getName())    → true
A == B                              → false
```

Vì thế, một object implements `api.Greeter` phiên bản B **không tự động** là instance của `api.Greeter` phiên bản A. JVM kiểm tra danh tính runtime type; nó không so tên gói hay chữ ký source rồi đoán rằng hai loại có thể ép kiểu cho nhau.

Hậu quả thường gặp:

- `ClassCastException`: object plugin không cast được sang interface host dù thông báo nhìn như `api.Greeter cannot be cast to api.Greeter`.
- `Class.isAssignableFrom(...)` trả `false` dù hai class có tên và method giống nhau.
- `ServiceLoader` hoặc reflection tìm được implementation, nhưng host không chấp nhận nó là subtype của service interface đã biết.
- Method/field signature đi qua hai loader nhận các class cùng tên nhưng khác identity; JVM có thể phát hiện xung đột **loading constraints** và báo `LinkageError`.
- DTO, exception, callback type hoặc enum không tương thích ở runtime vì một phía sử dụng bản định nghĩa khác.

**Quy tắc thiết kế:** *Những kiểu xuất hiện trong hợp đồng trao đổi giữa host và plugin phải được xác định rõ loader sở hữu và được cả hai phía resolve về cùng runtime type.* Không nhất thiết mọi class của hai phía dùng chung; chỉ những **boundary types** cần chung identity.

## 2. Xác định đâu là contract, đâu là implementation

Hãy chia nhiệm vụ của một plugin thành ba vùng:

```text
                  HOST APPLICATION
           ┌──────────────────────────────┐
           │ host orchestration / UI       │
           │ registry, lifecycle, config   │
           └──────────────┬───────────────┘
                          │
                   SHARED API
           ┌──────────────▼───────────────┐
           │ api.Greeter                  │
           │ api.Request / api.Response   │
           │ api.PluginException          │
           │ api.PluginContext (callback)  │
           └──────────────▲───────────────┘
                          │
                   PLUGIN INSTANCE
           ┌──────────────┴───────────────┐
           │ impl.PluginGreeter           │
           │ plugin-private dependencies  │
           │ optional resource/config     │
           └──────────────────────────────┘
```

Ở đây:

- **Host** điều phối, cung cấp API dùng chung và là bên sử dụng kết quả.
- **Shared API** chứa kiểu mà cả host lẫn plugin cần biết. Đây là một **hợp đồng ổn định**, nên thường nằm trong một artifact nhỏ và được định nghĩa bởi **loader chung/ancestor**.
- **Plugin implementation** nằm trong artifact riêng. Loader riêng được quyền định nghĩa các class implementation, miễn là những tham chiếu đến shared API resolve về class mà host đang sử dụng.
- **Plugin-private dependencies** là các thư viện triển khai nội bộ. Tùy thiết kế có thể cách ly phiên bản; nếu một type từ thư viện riêng xuất hiện trong method signature public xuyên boundary, nó lại trở thành một phần của contract và cần chính sách riêng.

Một mốc quan trọng: "shared" ở đây nói về **định danh class tại runtime**, không nhất thiết là "hai bên có hai bản sao cùng tệp JAR". Hai bản JAR có bytes y hệt nhau nhưng bị hai loader tự `defineClass` riêng vẫn có thể tạo hai runtime types khác nhau.

## 3. Nguyên tắc kiến trúc: API chung do một loader được cả hai phía sử dụng

Thiết kế thông dụng nhất là **parent-first cho shared API**:

```text
                     host/application loader
                       defines api.Greeter
                                ▲
                     parent/delegation
                                │
                     plugin URLClassLoader
                      defines impl.PluginGreeter
                                │
               uses api.Greeter defined above
```

Khi `PluginGreeter` khai báo `implements api.Greeter`, plugin loader phải **ủy quyền** việc tìm `api.Greeter` về loader đã định nghĩa interface cho host. Kết quả mong muốn:

```java
Class<?> contract = api.Greeter.class;
Class<?> pluginClass = pluginLoader.loadClass("impl.PluginGreeter");

boolean canUse = contract.isAssignableFrom(pluginClass);
```

Đoạn trên là **snippet minh họa**, cần `pluginLoader` và các lớp tương ứng được khai báo. Nếu ranh giới được cấu hình đúng, `canUse == true`.

Chọn loader chung không nhất thiết luôn là `ClassLoader.getSystemClassLoader()`. Trong application server, môi trường OSGi hoặc module layer tùy biến, host API có thể nằm ở loader chuyên biệt. Điều bất biến cần bảo vệ là: **plugin và host cùng dùng một `Class<?>` cho mỗi type trong contract**.

### Vì sao không chỉ kiểm tra `getName()`?

`getName()` trả binary name. Hai class từ hai loaders đều có thể trả `"api.Greeter"`. Kiểm tra hiệu quả:

```java
hostApiClass == pluginApiClass
hostApiClass.isAssignableFrom(pluginImplementationClass)
hostApiClass.getClassLoader()
pluginApiClass.getClassLoader()
```

Hãy kiểm tra cả **danh tính class** và **loader định nghĩa**. `getClassLoader() == null` có thể biểu diễn bootstrap loader, không phải lỗi.

## 4. Thực hành A — một shared interface, host và plugin dùng chung

Ví dụ hoàn chỉnh dưới đây được thiết kế để chạy trên **JDK 17+**, không cần framework. Ta chủ động biên dịch API, plugin và host vào **các thư mục riêng**, rồi chỉ đưa **API + host** lên application classpath lúc chạy. Plugin implementation sẽ được tải từ thư mục riêng bằng `URLClassLoader`.

Cấu trúc:

```text
shared-api-lab/
├── src/
│   ├── api/
│   │   └── api/
│   │       └── Greeter.java
│   ├── plugin/
│   │   └── impl/
│   │       └── PluginGreeter.java
│   └── host/
│       └── host/
│           └── HostMain.java
├── shared-out/      (tạo bởi javac)
├── plugin-out/      (tạo bởi javac)
└── host-out/        (tạo bởi javac)
```

**Tệp `src/api/api/Greeter.java`:**

```java
package api;

public interface Greeter {
    String greet(String name);
}
```

**Tệp `src/plugin/impl/PluginGreeter.java`:**

```java
package impl;

import api.Greeter;

public final class PluginGreeter implements Greeter {
    public PluginGreeter() {
    }

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

**Tệp `src/host/host/HostMain.java`:**

```java
package host;

import api.Greeter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class HostMain {
    public static void main(String[] args) throws Exception {
        URL pluginLocation =
                Path.of("plugin-out").toAbsolutePath().toUri().toURL();
        ClassLoader hostLoader = HostMain.class.getClassLoader();

        try (URLClassLoader pluginLoader =
                 new URLClassLoader(new URL[] { pluginLocation }, hostLoader)) {
            Class<?> implementation =
                    pluginLoader.loadClass("impl.PluginGreeter");
            Class<?> pluginApi = implementation.getInterfaces()[0];

            System.out.println("plugin defined locally: " +
                    (implementation.getClassLoader() == pluginLoader));
            System.out.println("same API Class: " +
                    (pluginApi == Greeter.class));
            System.out.println("assignable: " +
                    Greeter.class.isAssignableFrom(implementation));

            Object instance =
                    implementation.getDeclaredConstructor().newInstance();
            Greeter service = (Greeter) instance;
            System.out.println("result: " + service.greet("Java"));
        }
    }
}
```

Từ thư mục `shared-api-lab`, biên dịch bằng JDK 17+:

```sh
javac --release 17 -d shared-out src/api/api/Greeter.java
javac --release 17 -cp shared-out -d plugin-out src/plugin/impl/PluginGreeter.java
javac --release 17 -cp shared-out -d host-out src/host/host/HostMain.java
```

Chạy trên **Windows PowerShell/CMD**, nơi classpath dùng dấu `;`:

```powershell
java -cp "host-out;shared-out" host.HostMain
```

Trên **Linux/macOS**, thay dấu `;` bằng `:`:

```sh
java -cp "host-out:shared-out" host.HostMain
```

**Stdout chuẩn của chương trình trên cấu hình đã nêu:**

```text
plugin defined locally: true
same API Class: true
assignable: true
result: Hello, Java
```

### Vì sao ví dụ A thành công?

1. Khi compile `PluginGreeter`, compiler thấy `api.Greeter` từ `shared-out`; class file ghi tham chiếu tới `api.Greeter`.
2. Runtime application classpath chỉ có `host-out` và `shared-out`. Application/host loader nạp `host.HostMain` và `api.Greeter`.
3. `pluginLoader` nhận **hostLoader làm parent** và chỉ có URL tới `plugin-out`. Nó tìm `impl.PluginGreeter` bằng `findClass`/`defineClass` của `URLClassLoader`, nên implementation thuộc plugin loader.
4. Khi cần `api.Greeter` cho `implements`, plugin loader tuân theo parent-first và nhận **cùng interface `Class<?>`** đã có ở host. Điều này làm `pluginApi == Greeter.class` đúng.
5. Object `PluginGreeter` là subtype runtime thật của `api.Greeter` mà host biết, vì thế `Greeter.class.isAssignableFrom(implementation)` đúng và ép kiểu `(Greeter)` thành công.
6. Host không cần compile-time dependency đến `impl.PluginGreeter`; nó chỉ có compile-time dependency đến API. Đây là điểm quan trọng để thay plugin implementation độc lập.

**Phạm vi bài thực hành:** URL từ `Path.of("plugin-out")` phụ thuộc **working directory**. Lệnh cần chạy đúng từ `shared-api-lab`. Ví dụ giữ đơn giản, chưa có cơ chế discovery, quản lý phiên bản, security boundary, exception translation hay quản lý vòng đời phức tạp.

## 5. Thực hành B — cùng interface name nhưng hai defining loaders

Bây giờ giữ nguyên **`Greeter.java`** và **`PluginGreeter.java`** ở thực hành A, nhưng cố tình cho plugin loader nhìn thấy **bản sao API** trong `shared-out` trong khi **parent** chỉ là platform loader.

Tạo thêm **`src/host/host/BrokenBoundary.java`**:

```java
package host;

import api.Greeter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class BrokenBoundary {
    public static void main(String[] args) throws Exception {
        URL pluginLocation =
                Path.of("plugin-out").toAbsolutePath().toUri().toURL();
        URL apiLocation =
                Path.of("shared-out").toAbsolutePath().toUri().toURL();

        try (URLClassLoader isolated = new URLClassLoader(
                new URL[] { pluginLocation, apiLocation },
                ClassLoader.getPlatformClassLoader())) {

            Class<?> implementation =
                    isolated.loadClass("impl.PluginGreeter");
            Class<?> apiSeenByPlugin =
                    implementation.getInterfaces()[0];

            System.out.println("same API name: " +
                    apiSeenByPlugin.getName().equals(Greeter.class.getName()));
            System.out.println("same API Class: " +
                    (apiSeenByPlugin == Greeter.class));
            System.out.println("assignable: " +
                    Greeter.class.isAssignableFrom(implementation));

            Object instance =
                    implementation.getDeclaredConstructor().newInstance();
            try {
                Greeter service = (Greeter) instance;
                System.out.println(service.greet("Java"));
            } catch (ClassCastException e) {
                System.out.println("cast failed: " +
                        e.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch bằng lại đúng shared API; không thêm plugin lên host classpath:

```sh
javac --release 17 -cp shared-out -d host-out src/host/host/BrokenBoundary.java
```

Chạy trên Windows:

```powershell
java -cp "host-out;shared-out" host.BrokenBoundary
```

Hoặc Linux/macOS:

```sh
java -cp "host-out:shared-out" host.BrokenBoundary
```

**Stdout chuẩn:**

```text
same API name: true
same API Class: false
assignable: false
cast failed: ClassCastException
```

**Vì sao thất bại?**

- Host loader định nghĩa `api.Greeter` từ application classpath.
- `isolated` sử dụng **platform loader làm parent**. Trong cách chạy này platform loader không tìm class tự tạo `api.Greeter` trong application classpath. Do URL của `isolated` bao gồm `shared-out`, chính `isolated` định nghĩa **một bản `api.Greeter` thứ hai**.
- Plugin implementation `impl.PluginGreeter` implements bản API do `isolated` định nghĩa, không phải bản API từ host loader.
- Tên cùng là `api.Greeter`, song hai `Class<?>` khác identity. Vì thế kiểu implementation không assignable với interface host và lệnh cast thất bại.

Đây là ví dụ **cố ý hỏng** để làm rõ class identity; nó **không phải mẫu thiết kế plugin đúng**. Không cần sửa `extends` của `PluginGreeter` để cứu cast. Cần làm cho cả host và plugin resolve `api.Greeter` qua **cùng loader sở hữu shared API** — như cấu hình parent ở thực hành A.

### "Nếu hai JAR giống hệt nhau thì sao?"

Kết quả vẫn có thể thất bại. Class identity đến từ cặp **binary name + defining loader**, không lấy hash nội dung file để tự gộp runtime types. Ngay cả bytecode bằng nhau từng byte, hai lần `defineClass` bởi hai loader vẫn tạo hai runtime classes khác nhau.

## 6. Thực hành C — hai plugin loaders, một contract và hai implementation types riêng

Cùng một **`PluginGreeter.class`** có thể được định nghĩa độc lập bởi hai loader instances, nhưng **cả hai implementation vẫn là subtype của cùng API** nếu hai loader cùng delegation contract về host.

Tạo `src/host/host/TwoPlugins.java`:

```java
package host;

import api.Greeter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class TwoPlugins {
    public static void main(String[] args) throws Exception {
        URL location =
                Path.of("plugin-out").toAbsolutePath().toUri().toURL();
        ClassLoader sharedParent = Greeter.class.getClassLoader();

        try (URLClassLoader first = new URLClassLoader(
                     new URL[] { location }, sharedParent);
             URLClassLoader second = new URLClassLoader(
                     new URL[] { location }, sharedParent)) {

            Class<?> one = first.loadClass("impl.PluginGreeter");
            Class<?> two = second.loadClass("impl.PluginGreeter");

            System.out.println("same implementation name: " +
                    one.getName().equals(two.getName()));
            System.out.println("same implementation Class: " + (one == two));
            System.out.println("first matches API: " +
                    Greeter.class.isAssignableFrom(one));
            System.out.println("second matches API: " +
                    Greeter.class.isAssignableFrom(two));

            Greeter a = (Greeter) one.getDeclaredConstructor().newInstance();
            Greeter b = (Greeter) two.getDeclaredConstructor().newInstance();
            System.out.println(a.greet("A"));
            System.out.println(b.greet("B"));
        }
    }
}
```

Biên dịch (giữ `shared-out` và `plugin-out` từ thực hành A):

```sh
javac --release 17 -cp shared-out -d host-out src/host/host/TwoPlugins.java
```

Chạy trên Windows:

```powershell
java -cp "host-out;shared-out" host.TwoPlugins
```

Trên Linux/macOS:

```sh
java -cp "host-out:shared-out" host.TwoPlugins
```

**Stdout chuẩn:**

```text
same implementation name: true
same implementation Class: false
first matches API: true
second matches API: true
Hello, A
Hello, B
```

Điều này cho thấy **API identity dùng chung** và **implementation isolation** có thể tồn tại đồng thời:

```text
                   Host API Loader
                 defines api.Greeter
                      ▲      ▲
                      │      │
              PluginLoader 1  PluginLoader 2
                  │                │
      defines impl.PluginGreeter   defines impl.PluginGreeter
      (implementation type #1)    (implementation type #2)

PluginLoader1.impl.PluginGreeter != PluginLoader2.impl.PluginGreeter

nhưng cả hai đều implements cùng HostLoader.api.Greeter
```

Với nhiều plugin instances, có thể dễ dàng tạo các đối tượng implementation riêng và giao tiếp qua contract chung. Điều đó không làm plugin code an toàn trước rủi ro, không miễn trừ nhu cầu quản lý loader lifecycle và không bảo đảm isolate mọi static state hay native library theo cách giống tiến trình riêng.

## 7. Quy tắc đóng gói artifact: contract đi một đường, implementation đi đường khác

Trong bản triển khai thực tế có thể có:

```text
plugins/
  shared-api.jar           # bản dùng chung do host kiểm soát
  host-app.jar             # code orchestration của host
  plugin-a.jar             # implementation A
  plugin-b.jar             # implementation B
```

Điểm quyết định không phải tên file. Hãy xác định **dependency ownership**:

| Artifact/type | Bên sở hữu class identity | Vì sao |
| --- | --- | --- |
| `api.Plugin`, `api.PluginContext` | Loader dùng chung được host và plugin delegation tới | Plugin objects phải assignable về interface host; callback parameter phải cùng type. |
| `api.Request`, `api.Response` | Thường loader chung, hoặc dùng dạng trao đổi độc lập class identity | DTO cần truyền qua ranh giới một cách an toàn. |
| `api.PluginException` | Loader chung nếu host phải `catch` type này | Exception type là một phần của boundary contract. |
| `impl.PluginA` | Loader của plugin A | Implementation được phép thay độc lập với host. |
| `impl.PluginB` | Loader của plugin B | Có thể cô lập class/static state/dependency với A. |
| Thư viện triển khai nội bộ | Plugin loader, nếu không rò qua contract | Hỗ trợ các plugin dùng những phiên bản dependency khác nhau. |
| Java SE classes | Bootstrap/platform loaders theo JDK | Các type như `String` hoặc `List` là platform types chung, nhưng generic type arguments có thể chứa app/plugin-specific classes. |

**Khuyến nghị build:** Plugin **compile against** shared API nhưng runtime wiring bảo đảm API được resolve từ loader chung. Nếu artifact plugin đóng gói kèm API để dễ build, vẫn cần cơ chế loại bản sao khỏi phần nạp động hoặc đảm bảo chính sách delegation ưu tiên shared API; đừng để plugin child-first tự định nghĩa lại nó.

Với Gradle/Maven, có thể dùng cấu hình dependency compile-time phù hợp để tránh đóng gói trùng shared API vào plugin distribution. Tuy nhiên tên cấu hình như `compileOnly` hoặc `provided` chỉ giải quyết **artifact packaging**; đúng class identity còn phụ thuộc runtime loader graph và cách các loader resolve class.

### Làm sao biết một thư viện phải là shared hay private?

Đặt câu hỏi: **Kiểu từ thư viện ấy có xuất hiện tại ranh giới public mà cả host và plugin cần tương tác qua JVM type system không?**

- Nếu `api.Plugin.execute(Request)` nhận `api.Request`, kiểu `Request` là boundary type.
- Nếu `PluginGreeter` dùng `org.example.json.InternalParser` chỉ trong phương thức private, parser có thể là plugin-private dependency.
- Nếu `api.Plugin.execute(com.vendor.JsonNode)` sử dụng một `JsonNode` ngoài shared API, **phiên bản và identity của `JsonNode` phải được thiết kế chung**, hoặc cần đổi boundary về DTO có nguồn sở hữu rõ ràng.
- Nếu host truyền `java.util.Map<String, Object>`, lớp `Map` là platform type chung nhưng các giá trị `Object` có thể chứa instance plugin-private. Điều này làm contract có vẻ trung lập mà vẫn tiềm ẩn lỗi cast hoặc lifecycle.

**Độ rộng ranh giới càng nhỏ và rõ, nguy cơ leak các kiểu implementation càng ít.**

## 8. Thiết kế một API contract đủ ổn định

Một interface chung có thể được tổ chức như sau:

```java
package api;

public interface Plugin {
    String id();

    Result execute(Request request, PluginContext context);
}
```

Ví dụ trên là **một đoạn minh họa về API thiết kế**, không tự biên dịch cho đến khi khai báo thêm `Request`, `Result` và `PluginContext`. Nó thể hiện ba nhóm dữ liệu thường có trong plugin API:

1. **Thông tin nhận dạng/chức năng**: `id()`, version hoặc capability metadata.
2. **Dữ liệu vào/ra**: request/response được định nghĩa trong shared API.
3. **Callback/facade do host cung cấp**: `PluginContext` cho plugin truy cập những khả năng host cố ý công khai.

### DTO: lớp Java hay dữ liệu trung lập?

Có hai hướng:

**A. Shared DTO Java types.** Host và plugin dùng chung `api.Request`, `api.Result` từ cùng defining loader. Ưu điểm: kiểm tra kiểu compile-time và runtime trực tiếp. Nhược điểm: thay đổi binary API cần kế hoạch version và compatibility; DTO chỉ dùng được khi mỗi bên có cùng class identity.

**B. Protocol trung lập với class loader.** Dùng JSON, byte array, protobuf, message schema hoặc record dữ liệu qua giao diện chuyển đổi được định nghĩa rõ. Ưu điểm: giảm phụ thuộc runtime class identity của payload nội bộ, có thể nối sang plugin chạy trong tiến trình khác. Nhược điểm: cần validation, schema evolution và xử lý lỗi deserialize. Dù payload là bytes/JSON, những Java types ở biên giới **gọi API trực tiếp** (ví dụ interface xử lý `byte[]`) vẫn phải có identity tương thích.

### Exception qua ranh giới

Nếu contract khai báo `throws api.PluginException`, host chỉ có thể catch đúng runtime exception subtype mà nó biết khi plugin dùng cùng class identity của `api.PluginException`. Nếu plugin định nghĩa bản sao cùng tên qua loader khác, catch clause theo kiểu host **không tự bắt bản sao đó**.

Trong thực tế nên thống nhất rõ:

- Checked exception có phải một phần của contract không?
- Runtime exception từ thư viện private có được truyền xuyên boundary, hay được plugin adapter ánh xạ thành lỗi chuẩn?
- Có cần error code, message, causal chain và redaction để host không phụ thuộc plugin implementation?
- Plugin ném `LinkageError`, `OutOfMemoryError` hay lỗi nghiêm trọng khác thì host vận hành thế nào?

Với API public, tránh đưa những type triển khai dễ biến động trực tiếp vào method signature.

### Callback phải đi qua cùng shared API

Nếu host truyền một object implements `api.PluginContext`, plugin sẽ cast và gọi nó. Để callback an toàn, hai phía phải thấy đúng `api.PluginContext` runtime type. Nếu plugin tải riêng bản sao của context interface, lỗi identity xuất hiện ngay ở chiều **host → plugin**, dù chiều trả về plugin object đã được xử lý.

## 9. Method signatures, binary compatibility và loading constraints

Một API contract là **binary contract**. JVM liên kết method/field references bằng tên và **descriptor**; descriptor chứa parameter types và return type theo định dạng JVM.

Ví dụ method Java:

```java
api.Result execute(api.Request request)
```

Phần descriptor khái niệm:

```text
(Lapi/Request;)Lapi/Result;
```

Nếu host compile theo bản API có `execute(Request):Result` nhưng plugin hoặc runtime có signature khác, lỗi có thể là `NoSuchMethodError`, `AbstractMethodError` hoặc loại `LinkageError` phù hợp tùy thay đổi và thời điểm sử dụng.

**Loading constraints** bảo vệ type safety khi các method signature đi qua loaders khác nhau. Ví dụ host và plugin có method cùng descriptor đòi hỏi `api.Request` phải cùng identity nhưng loader mapping lại định nghĩa hai kiểu khác nhau; JVM có thể từ chối liên kết với một `LinkageError` do loader constraint violation. Không nên "sửa" bằng reflection ép gọi method theo tên: vi phạm identity vẫn tồn tại.

### Nâng version API phải có chính sách

Một vài nguyên tắc thực tế:

- Đừng thay đổi return type, parameter types, static/instance hoặc xóa method được plugin cũ sử dụng rồi kỳ vọng binary compatibility tự giữ.
- Thêm abstract method vào interface có thể làm implementation đã biên dịch từ API cũ thiếu implementation khi method mới được gọi; những thay đổi tưởng là source-compatible có thể tạo lỗi runtime.
- Thêm `default` method có thể hỗ trợ một số trường hợp tiến hóa interface, nhưng cũng có nguy cơ xung đột default methods giữa interfaces; cần thử trên các plugin versions hỗ trợ.
- Nếu thay đổi contract lớn, có thể dùng API version riêng, adapter hoặc lifecycle migration thay vì cố chia sẻ nhiều bản `api.Plugin` có cùng binary name trong cùng một ranh giới.
- Dùng integration tests tải **JAR plugin đã phát hành** qua class loader thực tế, không chỉ compile cả host và plugin lại từ source mới nhất.

**Quan trọng:** Hai thư viện có cùng Java API ở source-level không đảm bảo tương thích binary ở runtime khi class files được xây từ các phiên bản khác nhau.

## 10. Thiết kế loader graph và delegation policy

Một kiến trúc plugin phải mô tả được:

```text
                Shared/Host API Loader
                  │  api.*, JDK types
                  ▲
                  │ parent-first cho api.*
          ┌───────┴────────┐
          │                │
      Plugin A           Plugin B
      impl.a.*           impl.b.*
      libs A             libs B
```

Sơ đồ diễn tả mục tiêu **shared API từ parent**, **implementation riêng tại child**. Trên một số nền tảng, shared API do một loader riêng định nghĩa và host cũng sử dụng loader đó; trên kiến trúc khác có thể dùng application loader.

### Parent-first

Mẫu mặc định của `ClassLoader.loadClass` thường thử parent trước rồi mới `findClass`. Nó phù hợp nếu shared API nằm ở parent và plugin không có tên lớp collision ngoài ý muốn. Tuy nhiên parent-first cho **mọi class** có thể khiến plugin không tải được version thư viện private mà nó cần khi host có một class cùng tên.

### Child-first có ngoại lệ cho shared API

Một số hệ thống tự viết `loadClass` để ưu tiên implementation/dependency của plugin, nhưng **dành các package contract và JDK classes cho parent**. Đây là thiết kế có thể dùng, nhưng phải tính đến:

- `java.*` không được tự ý định nghĩa trong loader application.
- API packages phải được ủy quyền nhất quán, kể cả types gián tiếp trong signatures.
- `findLoadedClass`, khóa nạp lớp, tính đồng thời và nguy cơ circular loading cần xử lý.
- Package sealing, signed JAR và module accessibility có thể giới hạn cách định nghĩa classes.
- Resource lookup có thể có chính sách riêng; loader tìm đúng class chưa chắc tìm đúng resource.
- Native libraries và static state có giới hạn isolation khác với Java class namespace.

**Không viết child-first loader chỉ để "sửa" một `ClassCastException`** mà chưa kiểm tra nguyên nhân bản sao của API. Trong nhiều trường hợp, sửa parent graph và dependency placement đơn giản, dễ kiểm thử hơn.

### Vì sao platform loader chưa chắc đủ?

`ClassLoader.getPlatformClassLoader()` nhìn thấy platform classes nhưng không mặc nhiên nhìn thấy shared API của ứng dụng trong unnamed application classpath. Ví dụ B cố ý chọn platform parent để tách khỏi host API. Nếu plugin phải nhận interface do host định nghĩa, parent nên là loader nhìn thấy interface ấy hoặc cơ chế bridging phải rõ ràng.

**Parent của loader là quan hệ object**, không phải kế thừa Java: `new URLClassLoader(urls, hostLoader)` truyền một instance, quyết định delegation. Java class của plugin loader không cần `extends` Java class của host loader.

## 11. JPMS, module layers và khả năng truy cập

Từ Java 9, module là một chiều khác của Java runtime. Class loader, module resolution và module access không thể được coi là một thứ.

Các câu hỏi cần tách:

1. **Module nào chứa shared API?** Ví dụ named module `com.example.plugin.api` hoặc package ở unnamed module.
2. **Module đó được resolve vào layer nào?** Có module trên path không có nghĩa nó đã được resolve.
3. **Module nào đọc module nào?** Named module caller phải có readability phù hợp (thường qua `requires`).
4. **Package nào được export?** Public class không tự động có thể truy cập nếu package không export.
5. **Loader nào định nghĩa mỗi class?** Hai module hay layers có thể dùng các loaders khác nhau; tránh giả định cùng tên module chắc chắn là cùng type.
6. **Có dùng reflection sâu không?** Cần xem `opens` và quyền truy cập tương ứng, không chỉ `exports`.

Ví dụ module descriptor cho một artifact shared API:

```java
module com.example.plugin.api {
    exports api;
}
```

Đây là **module-info.java minh họa**, không phải source độc lập chạy ngay với các thư mục classpath của thực hành A–C. Một plugin named module tiêu thụ API sẽ khai báo `requires com.example.plugin.api;` khi phù hợp. Ở môi trường JPMS thực tế, thiết kế module layer và loader mapping phải giữ cùng contract class identity; chỉ ghi `requires` không tự xử lý việc định nghĩa nhiều bản cùng class trong những loaders khác nhau.

**Sự khác nhau cốt lõi:** loader nhìn thấy class bytes **chưa chắc** module code được phép gọi class ấy do `exports`/readability. Ngược lại, module graph hợp lệ **chưa chắc** loại bỏ các xung đột của plugin dependencies ngoài graph.

## 12. ServiceLoader và việc tìm implementation

`ServiceLoader` là một cơ chế Java hỗ trợ nạp các **service providers**, thường được dùng để tìm plugin. Nó không thay thế nguyên tắc shared class identity.

Ví dụ:

```java
ServiceLoader<Greeter> providers = ServiceLoader.load(Greeter.class);
```

Đây là **snippet minh họa**; cần import `java.util.ServiceLoader` và `api.Greeter`. Provider phải được khai báo hợp lệ trong `META-INF/services/api.Greeter` khi dùng classpath, hoặc `provides ... with ...` trong named module, và phải thuộc những loader/layer mà `ServiceLoader` truy cập. Overload `ServiceLoader.load(Class<S>)` sử dụng **thread context class loader** trong ngữ cảnh thông thường.

Có ba điểm thường gây lỗi:

- **Provider được phát hiện không đồng nghĩa type hợp lệ**: instance vẫn phải là subtype của **đúng `Greeter.class`** mà host truyền cho `ServiceLoader`.
- **TCCL không phải parent loader**: `Thread.currentThread().getContextClassLoader()` có thể trỏ tới plugin loader, system loader hoặc giá trị framework cấu hình. Đặt TCCL chỉ đổi discovery context, không đổi định nghĩa một class đã được nạp.
- **Provider metadata có thể đúng nhưng class thiếu dependency**: runtime có thể phát sinh `ServiceConfigurationError` cùng nguyên nhân sâu hơn như `ClassNotFoundException`, `LinkageError` hoặc provider constructor lỗi.

Khi dùng `ServiceLoader.load(Greeter.class, pluginLoader)`, plugin loader phải nhìn **đúng shared API `Greeter.class`** và nạp provider hợp lệ. Nếu đang dùng named modules, cần hiểu thêm phiên bản `ServiceLoader.load(layer, service)` và phạm vi provider trong module layer.

## 13. Reflection, method handles và các kiểu bị "rò" qua boundary

Reflection có thể gọi method bằng tên, nhưng JVM vẫn có các quy tắc access, assignability và class identity. Ví dụ:

```java
Class<?> type = pluginLoader.loadClass("impl.PluginGreeter");
Object plugin = type.getDeclaredConstructor().newInstance();
```

Đây là snippet minh họa có thể đặt vào method `throws Exception` khi đã khai báo `pluginLoader`. Nếu `plugin` implements bản sao interface do loader khác định nghĩa, cast ở host vẫn thất bại.

Các nguy cơ cần quan sát:

- **Method parameter types:** `getMethod("execute", Request.class)` phải match **đúng `Class<?>` của `Request`** mà method thực sự khai báo. Cùng tên không đủ.
- **Return types:** một `api.Result` của plugin loader không tự là `api.Result` của host loader.
- **Array types:** `api.Result[]` cũng phụ thuộc defining loader của component type; hai array types với component runtime khác nhau không hoán đổi được.
- **Enums và records:** các kiểu này vẫn là Java class runtime có defining loader; các giá trị không tự đồng nhất qua bản sao loaders.
- **Exceptions:** catch clause Java được kiểm tra theo runtime type; exception class cùng tên nhưng khác defining loader không được bắt theo kiểu host.
- **Annotations:** nếu một plugin class mang annotation `api.Extension` do plugin loader định nghĩa riêng, reflection host dùng `api.Extension.class` có thể không nhận cùng annotation type.
- **Proxy và method handles:** generated proxy classes và handle signatures phải tuân thủ khả năng nhìn thấy/access của loader liên quan; không thể giả định cùng tên interface giải quyết mọi thứ.
- **Java generics:** type erasure không bỏ mọi type checks. `List<PluginDto>` có runtime raw `List` chung nhưng object bên trong vẫn có defining loader identity riêng.

Một API "chỉ dùng `Object`" đôi khi che vấn đề đến lúc host cast nội dung payload. Nên dùng DTO contract rõ ràng hoặc định dạng trao đổi độc lập cùng validation tại boundary.

## 14. Quản lý vòng đời loader và tránh giữ plugin mãi trong bộ nhớ

Một plugin loader thường tồn tại cùng các đối tượng/class do nó định nghĩa. Để unload class loader có thể cần giải phóng các tham chiếu giữ sống loader, classes và instances; GC/runtime quyết định thời điểm và khả năng unloading.

Các nguồn tham chiếu dễ bỏ sót:

- Host registry còn lưu plugin instance, `Class<?>`, `Method` hoặc `MethodHandle` của plugin.
- Một static field ở host giữ object plugin hoặc listener được plugin đăng ký.
- Thread pool hoặc thread context class loader vẫn trỏ tới plugin loader.
- Timer/task, callbacks, `ThreadLocal`, metrics registry hoặc logger lưu plugin classes/instances.
- `ServiceLoader` instance/cache hoặc framework cache còn giữ provider/plugin implementation.
- Một thư viện private của plugin tạo non-daemon thread chưa được stop.

`URLClassLoader.close()` đóng các tài nguyên mà loader quản lý (chẳng hạn JAR đang mở); **không bảo đảm JVM unload mọi class ngay** và không tự đóng những thread hay object plugin tạo ra. Cần plugin lifecycle `start/stop/close` có hợp đồng rõ, unregister callbacks, dừng background work, xoá registry và dọn TCCL có kiểm soát.

Khi host giữ một interface contract trong loader chung, bản thân interface đó có thể sống suốt ứng dụng — điều này bình thường. Điều cần tránh là shared API hoặc registry vô tình giữ tham chiếu cứng tới mọi plugin instance sau khi unload.

## 15. Debug khi thấy `ClassCastException` hoặc `LinkageError` tại boundary

**Bước 1: xác định hai kiểu đang so sánh.** Đừng chỉ đọc binary name từ exception; thu thập `Class<?>` thật ở hai phía.

Một helper có thể đặt trong ứng dụng khi có cả host API class và plugin implementation class:

```java
static void inspect(Class<?> hostApi, Class<?> implementation) {
    System.out.println("host API: " + hostApi.getName());
    System.out.println("host loader: " + hostApi.getClassLoader());
    System.out.println("impl: " + implementation.getName());
    System.out.println("impl loader: " + implementation.getClassLoader());
    System.out.println("assignable: " +
            hostApi.isAssignableFrom(implementation));
    for (Class<?> api : implementation.getInterfaces()) {
        System.out.println("implemented: " + api.getName());
        System.out.println("interface loader: " + api.getClassLoader());
        System.out.println("same interface: " + (api == hostApi));
    }
}
```

Đây là **method minh họa**, cần đặt trong class và gọi với types phù hợp; không phải tệp `.java` độc lập. `getInterfaces()` chỉ liệt kê trực tiếp interfaces được class khai báo, vì vậy với hierarchy phức tạp hãy xem thêm superclass và superinterfaces.

**Bước 2: đọc runtime dependency/loader graph.** Kiểm tra thư mục/JAR của shared API có xuất hiện cả ở host và plugin không, parent cụ thể của loader, cùng chiến lược parent-first/child-first. **Không chỉ xóa file trùng** nếu loader vẫn không có kênh tìm shared API.

**Bước 3: xem bytecode/binary descriptors.** Dùng `javap -verbose` và `javap -s` cho các class trong artifact **đang được chạy**, không chỉ source mới nhất:

```sh
javap -classpath shared-out -s api.Greeter
javap -classpath plugin-out -v impl.PluginGreeter
javap -classpath host-out -v host.HostMain
```

Các lệnh áp dụng cho kết quả thực hành A; nội dung output có thể dài và thay đổi theo javac/JDK. `javap` cho thấy class-file references và signatures; nó không tự nói **defining loader nào sẽ được JVM chọn** trong runtime thực tế.

**Bước 4: logging class loading.** Trên HotSpot hỗ trợ Unified Logging:

```sh
java -Xlog:class+load=info -cp "host-out;shared-out" host.HostMain
```

Lệnh dùng classpath **Windows** để nhất quán ví dụ; Linux/macOS thay `;` bằng `:`. Log chứa nguồn class bytes và thông tin loading giúp xác định artifact nào được sử dụng. Kết hợp với `Class.getClassLoader()` để biết identity thật; thời điểm class loading không đồng nghĩa initialization hay resolution hoàn tất.

**Bước 5: phân loại lỗi thay vì chữa theo cảm tính.**

| Triệu chứng | Khả năng cần xem | Hướng sửa chính |
| --- | --- | --- |
| `ClassCastException` với cùng binary name | Hai defining loaders tạo bản sao API | Chia sẻ một contract loader, sửa delegation/packaging. |
| `isAssignableFrom` bằng `false` | Implementation không implements đúng runtime interface | Đọc interface `Class<?>` từ plugin, loader chain, version contract. |
| `ServiceConfigurationError` | Provider không là subtype service, cấu hình service sai, constructor hoặc dependency lỗi | So service `Class<?>`, TCCL/ServiceLoader loader, provider metadata. |
| `NoSuchMethodError` / `NoSuchFieldError` | Binary API không khớp lúc chạy | So descriptors của host/plugin/runtime JAR; version tương thích. |
| `AbstractMethodError` | Implementation runtime thiếu method mà call site yêu cầu | Kiểm tra interface evolution/ABI và plugin được compile từ version nào. |
| `LinkageError` liên quan loader constraint | Signature type identity xung đột giữa loaders | Kiểm tra shared types lồng trong parameter/return và loader delegation. |
| `IllegalAccessError` / `InaccessibleObjectException` | Package/module access/opens không đủ | Sửa module descriptor/exports/opens và luồng reflection hợp lệ. |
| `ClassNotFoundException` | Loader được hỏi không nhìn thấy class | Parent chain, URL/module/layer, runtime packaging. |
| `NoClassDefFoundError` | Class cần dùng không thể được định nghĩa/sử dụng hoặc initialization từng thất bại | Tìm `Caused by`, runtime dependencies, initializer. |

`ClassNotFoundException` khi dùng `loadClass` là checked exception của API lookup; `NoClassDefFoundError` là `LinkageError` có thể xảy ra ở một thời điểm khác khi bytecode cần class. Không đánh đồng chúng.

## 16. Checklist thiết kế trước khi phát hành plugin

Trước khi chọn cách nạp, kiểm tra theo thứ tự:

1. **Liệt kê mọi boundary types.** Interface, DTO, exception, annotation, enum, callback, collections chứa giá trị bên thứ ba, types trong descriptors.
2. **Chỉ định một defining loader hợp lệ cho từng shared type.** Host và mọi plugin liên quan phải resolve về cùng `Class<?>` đó.
3. **Vẽ loader graph.** Loader nào là parent/ancestor của loader nào, có child-first hay ngoại lệ package nào?
4. **Đảm bảo packaging tương ứng.** Shared API được phân phối/version rõ, plugin không vô tình định nghĩa bản sao; implementation được đặt trên plugin path.
5. **Tách dependency riêng.** Không để private dependency type rò qua public contract khi không có kế hoạch chia sẻ identity.
6. **Kiểm tra binary compatibility.** Host API version và các plugin artifacts đã phát hành có hợp đồng version cụ thể.
7. **Kiểm tra JPMS nếu dùng modules.** Readability, exports, opens, layer và defining loader thật.
8. **Kiểm tra discovery.** `ServiceLoader`/TCCL biết được provider, và provider assignable vào service type của host.
9. **Kiểm tra lifecycle.** Có start/stop, cleanup callback/thread/resource và đóng loader thích hợp.
10. **Chạy integration tests qua loaders thật.** Đừng chỉ test với plugin classes trực tiếp trên system classpath, vì cách đó có thể vô tình loại bỏ boundary cần thử.

### Test tối thiểu nên có

Một kiểm thử integration có giá trị phải thực sự tạo plugin loader với classpath tách biệt như ví dụ A; tải plugin theo binary name và khẳng định:

```java
assert api.Plugin.class.isAssignableFrom(implementationClass);
assert implementationClass.getClassLoader() == pluginLoader;
```

Đây là các assertion **minh họa**, không phải code JUnit hoàn chỉnh. Nếu source thuộc module hoặc framework khác, assertion thứ hai có thể phải điều chỉnh cho thiết kế loader cụ thể; assertion về contract identity mới là điều quan trọng. Test thêm các trường hợp plugin dùng JAR API trùng, mismatch version, missing dependency và vòng đời unload khi áp dụng.

## 17. Những ngộ nhận cần tránh

- **"Cùng package và class name thì JVM sẽ tự chia sẻ kiểu."** Sai: defining loader là một phần của runtime type identity.
- **"Thêm `api.jar` vào cả host và plugin classpath là đủ."** Chưa đủ: nếu mỗi loader tự define bản sao, identity tách rời.
- **"Plugin phải kế thừa cùng Java superclass loader với host để cast thành công."** Sai: inheritance của loader implementation class không quyết định contract identity.
- **"Plugin được child-first nên mọi class phải được nạp ở child."** Không nên: shared API thường cần ngoại lệ delegation về loader chung.
- **"Interface dùng chung đúng rồi thì mọi DTO đều an toàn."** Chưa chắc: mọi kiểu lộ ra ở boundary cũng cần thống nhất hoặc được chuyển qua protocol trung lập.
- **"Reflection giải quyết được xung đột class loader."** Sai: việc lấy class/method qua reflection không xoá access/type constraints.
- **"`ServiceLoader` luôn thấy tất cả plugins."** Không: discovery phụ thuộc TCCL, loader/layer, service metadata và khả năng assignability.
- **"`URLClassLoader.close()` khiến mọi plugin class biến mất ngay."** Không: unloading còn phụ thuộc reachability và GC.
- **"Một bản shared API dùng được mãi, không cần versioning."** Sai: việc sửa binary signatures có thể làm plugin cũ không còn liên kết.
- **"Nếu hai plugin dùng dependency cùng version thì phải dùng chung tất cả classes."** Không: mỗi plugin có thể định nghĩa implementation/dependency riêng, miễn các **shared boundary types** tuân thủ identity.

## 18. Câu hỏi tự kiểm tra

**1. JVM dùng yếu tố nào để phân biệt hai reference types cùng tên?** Binary name và defining class loader.

**2. Vì sao `api.Greeter` bản host và bản plugin không thể tự cast cho nhau?** Hai loader định nghĩa hai interface `Class<?>` khác nhau, khiến runtime assignability thất bại dù tên/source giống.

**3. Muốn một plugin implementation assignable với host interface, cần điều kiện gì?** Plugin phải dùng đúng runtime interface mà host dùng trong quan hệ `implements`, thường qua shared parent/delegation.

**4. Vì sao trong thực hành A `PluginGreeter` thuộc plugin loader nhưng `Greeter` được dùng chung?** URLClassLoader tự định nghĩa class implementation từ `plugin-out`, còn contract từ `shared-out` được host loader cung cấp qua parent-first.

**5. Vì sao thực hành B cố ý thất bại dù `Greeter.java` vẫn giống nhau?** Plugin loader dùng platform parent rồi tự định nghĩa API từ `shared-out`, tạo bản sao interface khác host identity.

**6. Hai plugin loaders khác nhau có thể cùng dùng một API mà implementation khác nhau?** Có. Cả hai delegation tới một shared API loader, trong khi tự định nghĩa implementation riêng.

**7. `List<Object>` trong contract có bảo đảm không rò types riêng?** Không. Giá trị trong collection có thể là plugin-private classes; các cast ở host vẫn có thể thất bại.

**8. Vì sao đổi return type có thể phá plugin binary compatibility?** JVM method descriptor bao gồm return type. Bên gọi đã compile chứa symbolic reference theo descriptor cũ.

**9. TCCL liên quan gì tới shared API?** Một số cơ chế discovery như `ServiceLoader` dùng TCCL để tìm provider, nhưng TCCL không tự gộp hai class identities.

**10. Điều gì cần thử trước khi phát hành một plugin API revision?** Test plugin JAR đã biên dịch từ version cũ qua loader graph thực tế, bao gồm subtype checks và method invocations qua mọi boundary types.

## 19. Tài liệu tham khảo để xác minh

- [Java Virtual Machine Specification, Java SE 17 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading, defining/initiating loader, class identity, loading constraints và linking.
- [Java SE 17 `ClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, parent delegation, `getParent` và class loader contracts.
- [Java SE 17 `URLClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): tạo loader từ URL và đóng loader.
- [Java SE 17 `Class` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getClassLoader`, `getInterfaces`, `isAssignableFrom` và runtime types.
- [Java SE 17 `ServiceLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider discovery trên classpath/module path và context loader.
- [Java SE 17 `Module` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): mô hình module readability và access.

**Nguyên tắc kết thúc:** thiết kế nhiều class loaders thành công khi **hợp đồng dùng chung có một danh tính runtime mà mọi bên đều nhìn thấy**, còn implementation có thể giữ namespace riêng. Hãy kiểm tra `Class<?>` thực, defining loader, method descriptors, module access và loader graph — thay vì chỉ so sánh package name hoặc nội dung JAR.


</details>

- [Quay lại đầu trang](#back-to-top)
