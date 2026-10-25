<a id="back-to-top"></a>

# Loader tích hợp

## Menu
- [Bootstrap class loader và vai trò của JVM](#built-in-class-loaders-001)
- [Platform class loader trong JDK hiện đại](#built-in-class-loaders-002)
- [System class loader và application class loader](#built-in-class-loaders-003)
- [Quan hệ cha và phạm vi lớp mà mỗi loader phụ trách](#built-in-class-loaders-004)
- [Class.getClassLoader và cách hiểu kết quả null](#built-in-class-loaders-005)
- [ClassLoader.getPlatformClassLoader và getSystemClassLoader](#built-in-class-loaders-006)
- [Extension class loader trong Java 8 và thay đổi từ Java 9](#built-in-class-loaders-007)
- [Vì sao không nên ép application class loader thành URLClassLoader](#built-in-class-loaders-008)
- [Runtime image, jrt: và việc loại bỏ rt.jar](#built-in-class-loaders-009)
- [Giới hạn của sơ đồ ba loader khi làm việc với named module](#built-in-class-loaders-010)
- [Tùy chỉnh system class loader bằng java.system.class.loader](#built-in-class-loaders-011)

## <a id="built-in-class-loaders-001">Bootstrap class loader và vai trò của JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Bootstrap class loader và vai trò của JVM

## 1. Bootstrap class loader là gì?

**Bootstrap class loader** (bộ nạp lớp khởi động) là cơ chế nạp các lớp nền tảng thiết yếu để Java Virtual Machine (JVM) có thể vận hành và thực thi chương trình Java. Các kiểu như `java.lang.Object`, `java.lang.String`, `java.lang.Class` và nhiều kiểu thuộc module `java.base` được định nghĩa bởi bootstrap class loader trên JDK 17 thông thường.

Nói ngắn gọn: **JVM là môi trường thực thi**, còn **bootstrap class loader là một phần cơ chế nạp lớp ở tầng nền tảng của JVM**. Bootstrap loader cung cấp những định nghĩa lớp cốt lõi để JVM và các class loader cấp cao hơn cùng sử dụng. Không nên coi hai khái niệm này là một:

- **JVM** thực thi bytecode, kiểm tra và liên kết lớp, quản lý bộ nhớ/GC, quản lý threads và thực hiện quy tắc khởi tạo lớp.
- **Bootstrap class loader** đảm nhiệm việc tìm và định nghĩa các lớp được giao cho tầng bootstrap. Nó chỉ là **một mắt xích của hệ thống nạp lớp**, không phải toàn bộ bộ máy thực thi Java.
- **Java class loader khác** (platform, application/system, class loader do người dùng tạo) phối hợp để nạp những lớp mà bootstrap loader không định nghĩa.

Sự phân biệt này quan trọng khi đọc `Class.getClassLoader()`, khi gặp `ClassNotFoundException`, khi thiết kế plugin, hoặc khi điều tra hai lớp **trùng tên** nhưng không thể ép kiểu cho nhau.

## 2. Tại sao JVM cần một bộ nạp lớp ở tầng bootstrap?

Một chương trình Java rất nhỏ cũng cần các định nghĩa kiểu nền tảng. Chẳng hạn:

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello");
    }
}
```

Trong quá trình thực thi, JVM cần định nghĩa của `java.lang.Object`, `java.lang.Class`, `java.lang.String` và nhiều thành phần thư viện runtime liên quan. Nếu mọi class loader đều phải là một object Java do một class loader khác nạp trước, quá trình khởi động sẽ rơi vào vòng phụ thuộc: muốn tạo class loader đầu tiên lại phải có các lớp thiết yếu đã được nạp.

Bootstrap class loader giải quyết nhu cầu **khởi tạo hệ sinh thái kiểu ban đầu**. Trên các triển khai JVM phổ biến như HotSpot, bộ nạp này được triển khai ở tầng JVM/native và **không có một object `java.lang.ClassLoader` thông thường đại diện trực tiếp cho nó** trong API Java.

Điều đó lý giải tại sao:

```java
System.out.println(String.class.getClassLoader()); // null
```

Trên JDK 17 thông thường, dòng này in:

```text
null
```

Giá trị `null` là **quy ước API biểu thị bootstrap class loader**, không có nghĩa “lớp không được nạp” hay “JVM không dùng class loader”.

> Không gán `null` cho một class loader object rồi hiểu đó là một object tên BootstrapClassLoader. Trong Java API, null biểu thị tầng bootstrap ở những vị trí được tài liệu quy định.

## 3. Bootstrap, platform và application class loader trên JDK 17

Từ Java 9, hệ thống module (JPMS) thay đổi cách JDK tổ chức thư viện nền tảng. Trên JDK 17, ba tầng built-in class loader cần phân biệt là:

| Tầng | Vai trò điển hình | Quan sát trên Java 17 |
|---|---|---|
| **Bootstrap** | Định nghĩa những lớp nền tảng cốt lõi, bao gồm phần lớn các lớp trong `java.base` | `String.class.getClassLoader() == null` |
| **Platform** | Định nghĩa các lớp thuộc một số Java SE/JDK platform modules khác, theo ánh xạ module của runtime | `ClassLoader.getPlatformClassLoader()` |
| **Application / System** | Nạp lớp ứng dụng trên class path và các module ứng dụng tương ứng | `ClassLoader.getSystemClassLoader()` |

Đây là **mô hình hữu ích để học**, nhưng không phải luật “package `java.*` thuộc bootstrap, package khác thuộc application”. Ví dụ, không được suy rằng mọi class trong tất cả module `java.*` đều được bootstrap loader định nghĩa. Tầng nào định nghĩa class cụ thể phụ thuộc vào cấu hình runtime/module và cách JDK triển khai.

Các loader built-in của JDK 17 thường có quan hệ parent như sau:

```text
Bootstrap class loader
  (không có đối tượng ClassLoader thông thường)
                ^
                | parent ở cấp API biểu diễn bằng null
                |
Platform class loader
                ^
                |
Application / System class loader
                ^
                |
Custom ClassLoader thường dùng app loader làm parent
```

Mũi tên trên thể hiện **hướng delegation thông thường**, không phải quan hệ kế thừa Java (`extends`) của các class loader và cũng không có nghĩa JVM buộc mọi loader tùy biến phải tuân thủ một chính sách parent-first tuyệt đối.

**Mốc thay đổi theo phiên bản:**

- **JDK 8 và trước:** thường nói bootstrap loader tìm lớp lõi trong `rt.jar`/bootstrap class path, extension loader xử lý phần mở rộng, application loader xử lý class path.
- **JDK 9 trở đi:** không còn mô hình `rt.jar`/extension mechanism như cũ. JDK cung cấp runtime image và named modules; extension loader đã được thay bằng **platform class loader**. Vì vậy việc tìm `$JAVA_HOME/jre/lib/rt.jar` trên JDK 17 là kỳ vọng lỗi thời.
- **JDK 17:** các lớp nền tảng nằm trong runtime image của JDK, có thể được duyệt dưới dạng `jrt:/` qua công cụ và API thích hợp. Không được xem `jrt:/` là một đường dẫn file JAR vật lý thông thường.

Các lệnh hỗ trợ quan sát cấu trúc module trên JDK 17:

```bash
java --list-modules
java --describe-module java.base
```

Chúng in danh sách/chi tiết module của runtime hiện tại. Thông tin module **bổ sung** cho kiến thức class loader, không thay thế việc xác định defining loader thực tế của từng class.

## 4. Vai trò cụ thể của bootstrap loader trong cơ chế JVM

Một hình dung theo quá trình:

1. JVM được khởi động bởi chương trình launcher `java` và chuẩn bị runtime.
2. JVM cần các kiểu nền tảng để có thể định nghĩa, kiểm tra, liên kết và thực thi các class khác. Bootstrap loader đảm nhiệm việc định nghĩa tập kiểu cốt lõi tương ứng.
3. Khi cần một class ứng dụng, runtime sử dụng cơ chế loader phù hợp. Class loader ứng dụng thường phối hợp với parent loaders; nếu bootstrap có thể tìm và định nghĩa một class nền tảng được yêu cầu, ứng dụng sẽ dùng chung định nghĩa đó.
4. Khi đã có định nghĩa class, JVM tiếp tục xử lý **linking** (verification, preparation và resolution theo quy tắc) rồi **initialization** nếu cần, sau đó thực thi code.

Điều cần tránh: **loading không đồng nghĩa với initialization**. Nạp/định nghĩa `String` là hoạt động khác với chạy phần khởi tạo class khi một thao tác Java yêu cầu. Bootstrap class loader không thay thế các quy tắc verification, initialization, execution hay garbage collection của JVM.

### Class loader tìm và định nghĩa; JVM còn làm gì?

- **Loading:** tạo hoặc tìm một class/interface runtime từ dạng nhị phân thích hợp qua class loader.
- **Verification:** kiểm tra class file/bytecode theo quy tắc an toàn và cấu trúc JVM.
- **Preparation:** chuẩn bị static fields và giá trị ban đầu theo quy tắc VM.
- **Resolution:** đổi symbolic references thành tham chiếu runtime; JVM được phép làm một phần theo nhu cầu.
- **Initialization:** thực hiện `<clinit>` theo điều kiện kích hoạt initialization.
- **Execution:** trình thông dịch/JIT và runtime thực hiện phương thức/lệnh bytecode.
- **Garbage collection và class unloading:** quản lý khả năng thu hồi bộ nhớ, class metadata và các object khi đủ điều kiện.

**Bootstrap loader chủ yếu liên quan đến giai đoạn định nghĩa các lớp nền tảng.** Các giai đoạn tiếp theo thuộc ngữ nghĩa và cơ chế thực thi của JVM, áp dụng cho class bất kể defining loader nào.

## 5. Parent delegation: bootstrap đứng ở đâu?

Mô hình nạp lớp thông thường của `ClassLoader.loadClass(String)` trong Java có thể tóm tắt như sau:

```text
loadClass("com.example.Service")
          |
          v
Đã được nạp bởi loader này? -- Có --> trả Class<?> đã nạp
          |
         Chưa
          v
Đề nghị parent nạp (nếu parent == null: dùng bootstrap)
          |
          +-- Parent nạp thành công --> dùng định nghĩa parent trả về
          |
          +-- Parent không tìm thấy
                  |
                  v
            Loader hiện tại tự tìm/định nghĩa (findClass)
                  |
                  v
            Trả Class<?> hoặc ClassNotFoundException
```

Đây là thuật toán **mặc định điển hình** của `ClassLoader.loadClass`. Một class loader tùy biến có thể override `loadClass` để dùng chiến lược khác, và loader built-in ở hệ module có thể xử lý lookup theo ánh xạ module; không nên nói mọi class loader trên mọi JVM luôn tìm bootstrap trước mọi loại class trong mọi trường hợp.

### Vì sao thường ưu tiên parent?

Giả sử ứng dụng hoặc plugin chứa một file đặt tên `java/lang/String.class`. Không thể suy rằng vì file này nằm trên class path nên JVM sẽ dùng nó thay cho `java.lang.String` vốn đã được runtime định nghĩa. Cơ chế delegation, ràng buộc package/runtime và các quy tắc bảo vệ class/platform cùng tham gia ngăn việc tùy tiện giả mạo các lớp lõi.

Lợi ích thực tế:

- **Chia sẻ kiểu nền tảng:** đa số class ứng dụng cùng tham chiếu tới định nghĩa `java.lang.String` từ bootstrap, tránh vô tình tạo các kiểu lõi không tương thích.
- **Tính nhất quán:** các API ở các tầng khác nhau có thể truyền/nhận cùng runtime type của thư viện nền tảng.
- **Ranh giới tải lớp:** một loader ứng dụng không có quyền mặc nhiên thay thế hoặc định nghĩa lại namespace nền tảng chỉ bằng cách thêm một JAR.
- **Hỗ trợ cách ly:** class loader tùy biến có thể tách các kiểu do chính nó định nghĩa, nhưng thường vẫn chia sẻ nền tảng JVM với parent.

**Parent delegation không phải hệ thống bảo mật hoàn chỉnh.** Với Java hiện đại, class loader, JPMS, access checks và bytecode verification đảm nhiệm các chức năng riêng; không nên xem việc có parent-first đồng nghĩa ứng dụng đã được sandbox an toàn.

## 6. Danh tính runtime của class và ảnh hưởng của bootstrap loader

Trong JVMS, một class/interface runtime được xác định về danh tính bởi **binary name và defining class loader**. Chẳng hạn:

- `java.lang.String` do bootstrap loader định nghĩa là kiểu nền tảng dùng chung.
- `com.example.Plugin` do loader A định nghĩa có thể khác `com.example.Plugin` do loader B định nghĩa, dù bytecode hoặc binary name trùng nhau.
- Việc cả hai loại class đều dùng `java.lang.String` làm parameter không làm chúng tự động tương thích với nhau.

Phân biệt hai khái niệm:

- **Defining loader**: loader thực sự định nghĩa runtime class.
- **Initiating loader**: loader đã khởi xướng yêu cầu nạp class đó; có thể nhận về định nghĩa do parent thực hiện.

Ví dụ, application loader yêu cầu `java.lang.String` qua delegation; bootstrap loader cung cấp định nghĩa. Khi gọi `String.class.getClassLoader()`, ta thấy **defining loader** ở tầng bootstrap (biểu diễn `null`), dù thao tác khởi xướng có thể đến từ code ứng dụng.

Nếu một lớp do custom loader định nghĩa dùng field `String name`, bản thân field vẫn dùng định nghĩa `String` phù hợp từ bootstrap; custom loader không cần định nghĩa lại `java.lang.String`.

### Static state và class identity

Static fields gắn với từng runtime class identity. Với một class do loader A và loader B định nghĩa riêng, mỗi định nghĩa có thể có static state của chính nó. Ngược lại, `String` từ bootstrap là cùng một runtime class mà các tầng ứng dụng thường sử dụng; các instance thuộc kiểu nền tảng này không trở thành các kiểu khác chỉ vì nằm trong nhiều plugin.

## 7. Kiểm tra bootstrap loader bằng Java 17

Ví dụ sau sử dụng đúng API công khai, tránh in tên triển khai nội bộ có thể khác theo JDK.

Tạo file **`BootstrapLoaderDemo.java`**:

```java
public class BootstrapLoaderDemo {
    static class Sample { }

    public static void main(String[] args)
            throws ClassNotFoundException {

        ClassLoader app = BootstrapLoaderDemo.class.getClassLoader();
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        System.out.println("String bootstrap: "
                + (String.class.getClassLoader() == null));
        System.out.println("Object bootstrap: "
                + (Object.class.getClassLoader() == null));
        System.out.println("String[] bootstrap: "
                + (String[].class.getClassLoader() == null));

        System.out.println("Main class via app: "
                + (app == ClassLoader.getSystemClassLoader()));
        System.out.println("Nested class via app: "
                + (Sample.class.getClassLoader() == app));
        System.out.println("Platform parent is bootstrap: "
                + (platform.getParent() == null));
        System.out.println("App parent is platform: "
                + (app.getParent() == platform));

        Class<?> resolved = Class.forName(
                "java.lang.String", false, null);
        System.out.println("Bootstrap resolves String: "
                + (resolved == String.class));

        System.out.println("String module: "
                + String.class.getModule().getName());
    }
}
```

Biên dịch và chạy trong **cấu hình class path mặc định của JDK 17 HotSpot/OpenJDK phổ biến**, không dùng custom system class loader:

```bash
javac BootstrapLoaderDemo.java
java BootstrapLoaderDemo
```

Stdout:

```text
String bootstrap: true
Object bootstrap: true
String[] bootstrap: true
Main class via app: true
Nested class via app: true
Platform parent is bootstrap: true
App parent is platform: true
Bootstrap resolves String: true
String module: java.base
```

Giải thích:

1. `String`, `Object` thuộc lõi runtime và do bootstrap loader định nghĩa. `getClassLoader()` trả `null` cho các class đó.
2. `String[]` là **một array class runtime khác `String`**, nhưng array class của kiểu tham chiếu có defining loader tương ứng defining loader của component type; vì thế `String[].class.getClassLoader() == null`.
3. Class `BootstrapLoaderDemo` và nested class `Sample` được ứng dụng biên dịch/chạy từ class path và do loader ứng dụng mặc định định nghĩa.
4. `platform.getParent() == null` phản ánh việc platform loader có bootstrap loader làm parent theo quy ước API; `app.getParent() == platform` đúng cho chain built-in mặc định trong ví dụ.
5. `Class.forName("java.lang.String", false, null)` truyền **`null` ở tham số loader**: API dùng bootstrap loader cho yêu cầu này. `false` có nghĩa **không yêu cầu initialization**, không phải “không load”.
6. `String.class.getModule().getName()` in `java.base`, giúp xác nhận class thuộc module lõi.

Lưu ý về tính di động: class loader là thành phần có thể cấu hình hoặc tùy biến. Nếu dùng `-Djava.system.class.loader`, agent đặc biệt hoặc runtime khác, một số phép so sánh **app parent** có thể khác. Các phép so sánh ở ví dụ là bằng chứng cho **cấu hình đã nêu**, không phải cam kết cho mọi JVM có thể tồn tại.

### Tại sao `String[].class` cũng báo bootstrap?

Theo quy tắc JVM, với array có component type là reference type, defining loader của array gắn với defining loader của component type. Với array của primitive, chẳng hạn `int[]`, JVM tạo array class và không có component class do người dùng định nghĩa; `int[].class.getClassLoader()` cũng thường cho `null` trong Java. Đừng từ `null` suy ra có một file `int[].class` trong runtime image.

## 8. Parent bằng null không tạo ra một bootstrap loader mới

Một điểm dễ nhầm khi đọc Java API: constructor của `ClassLoader` chấp nhận `null` làm parent **không làm instance đó trở thành bootstrap loader**.

Tạo file **`BootstrapParentDemo.java`**:

```java
public class BootstrapParentDemo {
    public static void main(String[] args) throws Exception {
        ClassLoader isolated = new ClassLoader(null) { };

        System.out.println("Parent is null: "
                + (isolated.getParent() == null));

        Class<?> core = isolated.loadClass("java.lang.String");
        System.out.println("Delegated String: "
                + (core == String.class));

        try {
            isolated.loadClass("BootstrapParentDemo");
            System.out.println("Unexpectedly loaded app class");
        } catch (ClassNotFoundException expected) {
            System.out.println("Application class not visible");
        }
    }
}
```

Chạy từ class path thông thường bằng JDK 17:

```bash
javac BootstrapParentDemo.java
java BootstrapParentDemo
```

Stdout:

```text
Parent is null: true
Delegated String: true
Application class not visible
```

Đây là một **đối tượng class loader mới**, subclass ẩn danh của `ClassLoader`, có parent API là `null`. Nó có thể nhờ bootstrap nạp `String`, nhưng mặc định không có cơ chế `findClass` tùy chỉnh để tìm file `BootstrapParentDemo.class` trên class path. Do vậy lời gọi nạp class ứng dụng thất bại trong môi trường đã mô tả.

Điểm cần nhớ: **bootstrap loader không phải một instance tạo bằng `new ClassLoader(null)`**; instance đó chỉ **ủy quyền tới bootstrap** khi parent bằng `null`.

Muốn nạp class ứng dụng, custom loader phải có chiến lược nạp thích hợp: được gán parent phù hợp, hoặc tự implement việc tìm/định nghĩa các class mà nó quản lý. Không tùy tiện dùng `new ClassLoader(null)` như “phương án cô lập an toàn” vì lỗi visibility và module access có thể phát sinh.

## 9. Bootstrap loader có tự khởi tạo mọi lớp nó nạp không?

**Không.** Cần tách rõ ba bước thường bị trộn:

1. **Loading**: class runtime được tìm/định nghĩa bởi loader.
2. **Linking**: JVM kiểm tra và chuẩn bị, resolve tham chiếu theo nhu cầu.
3. **Initialization**: JVM chạy `<clinit>` khi có điều kiện active use thích hợp.

Một class có defining loader là bootstrap **không có nghĩa** bootstrap trực tiếp điều khiển thời điểm static initializer của nó chạy; initialization tuân theo JVM/JLS và phụ thuộc cách class được sử dụng.

Tương tự với class ứng dụng:

```java
Class<?> c = Class.forName(
        "com.example.SomeType", false, loader);
```

Giá trị `false` có ý nghĩa trì hoãn việc yêu cầu initialization. Nếu về sau chương trình dùng `new SomeType()`, truy cập một static field không phải compile-time constant, gọi static method hoặc thao tác khác kích hoạt initialization, JVM mới phải khởi tạo theo quy tắc.

Sự phân biệt giúp tránh tác dụng phụ khi chỉ quét metadata của class. Ví dụ, công cụ phân tích class path hoặc hệ thống plugin có thể cần biết class có tồn tại trước khi cho phép static initializer thực thi.

## 10. Bootstrap loader, platform module và runtime image

### Không dùng mô hình `rt.jar` cho Java 17

Trên JDK 8, tài liệu thường minh họa bootstrap class loader đọc lõi Java từ `rt.jar`. Cách giải thích đó **phụ thuộc phiên bản**. Từ JDK 9, modular runtime image thay cách đóng gói thư viện. JDK 17 cung cấp các module của runtime, trong đó `java.base` là module bắt buộc chứa các API cơ bản.

Bạn có thể xem chi tiết trực tiếp:

```bash
java --describe-module java.base
```

Các tool và provider phù hợp có thể hiển thị tài nguyên trong runtime bằng URL dạng `jrt:/java.base/...`. Đó là một **URI scheme của runtime image**, không phải lời khẳng định rằng tất cả class được Java đọc từ một JAR tên `jrt.jar`.

### Không phải mọi class JDK đều được bootstrap nạp

Ví dụ `java.lang.String` trong `java.base` thuộc bootstrap. Nhưng JDK còn những module Java SE và module công cụ/triển khai khác, có thể được gán cho bootstrap, platform hoặc application loader tùy cấu hình. Đừng lập bảng phân loại chỉ dựa theo tiền tố package `java.`, `javax.`, `jdk.` hoặc theo tên module đoán chừng.

Khi cần kết luận trong chương trình cụ thể, hãy kiểm tra **actual runtime class**:

```java
Class<?> type = Class.forName("java.lang.String");
ClassLoader definingLoader = type.getClassLoader();
Module module = type.getModule();
```

Đây là đoạn **minh họa** cần nằm trong một method có khai báo/khử `ClassNotFoundException`. `definingLoader == null` nghĩa class do bootstrap định nghĩa. `module.getName()` cung cấp tên named module nếu có, nhưng **module name và class loader là hai thuộc tính khác nhau**.

### Không thay thế thư viện lõi bằng cách đưa JAR lên đầu class path

Thêm một JAR chứa bytecode trùng tên class lõi vào class path không phải phương pháp hợp lệ để “override `java.lang.String`”. JDK áp dụng cơ chế loader/module/package và các ràng buộc JVM tương ứng.

Có những tùy chọn JVM dành cho **instrumentation, patching, nghiên cứu nội bộ** như patch module hoặc bootstrap class path append trên một số JVM; chúng có điều kiện, giới hạn riêng và **không có nghĩa có thể tùy ý ghi đè mọi class của `java.base`**. Khi làm việc với JDK 17, hãy đọc tài liệu chính xác của VM và tùy chọn mình dùng thay vì bê nguyên hướng dẫn `-Xbootclasspath/p` thời JDK 8.

## 11. Class loader không đồng nghĩa với mọi cơ chế bảo vệ JVM

Bootstrap loader thường được giải thích cùng các lợi ích “an toàn” vì nó giúp lớp nền tảng dùng chung không bị ứng dụng thay tùy tiện. Tuy nhiên cần hiểu đúng ranh giới:

- **Bytecode verification** thuộc cơ chế kiểm tra tính hợp lệ class file của JVM, không đơn giản là “bootstrap duyệt bytecode rồi cho chạy”.
- **Access control** (`public`, `protected`, package access, module exports/opens, nestmates...) thực hiện theo ngữ nghĩa Java/JVM; loader identity có tác động tới runtime package và khả năng tương thích kiểu.
- **JPMS** quy định readability, exports, opens giữa các module; “do bootstrap định nghĩa” không có nghĩa code ở mọi module được phép truy cập mọi internal API của lớp đó.
- **Security Manager** không phải cơ chế được khuyến nghị để sandbox code không tin cậy trên Java hiện đại. Đừng cho rằng parent delegation đủ để cô lập bytecode độc hại.

Vì vậy, cần dùng class loader để hiểu **visibility, identity, delegation**, dùng các cơ chế API/module/runtime khác cho các yêu cầu truy cập, kiểm tra và cô lập phù hợp.

## 12. Lỗi thực tế và cách điều tra

### Lỗi 1: “`getClassLoader() == null` nghĩa class chưa load”

Sai. Với `String.class`, `null` chính là dấu hiệu defining loader được API biểu diễn là bootstrap. Class đã có mặt và `Class<?>` đang đại diện cho runtime type đó.

### Lỗi 2: “Cứ tên bắt đầu bằng `java.` là bootstrap”

Sai. Tập module của JDK hiện đại rộng hơn `java.base`; phải kiểm tra defining loader và module mapping thực tế. Ngoài ra JDK cấm nhiều cách định nghĩa sai lớp thuộc namespace hệ thống.

### Lỗi 3: “Bootstrap loader chính là `ClassLoader.getSystemClassLoader()`”

Sai. `ClassLoader.getSystemClassLoader()` trả về system/application loader, thường dùng cho ứng dụng. `ClassLoader.getPlatformClassLoader()` trả platform loader. Bootstrap loader không được trả về như một object `ClassLoader` bình thường.

### Lỗi 4: “Parent bằng null là không có cơ chế delegation”

Sai. Trong contract mặc định của `ClassLoader`, parent `null` làm delegation tới bootstrap. Ví dụ `BootstrapParentDemo` cho thấy chính điểm này.

### Lỗi 5: “Application loader và bootstrap định nghĩa hai bản `String`”

Trong chương trình JDK 17 thông thường, các loader khác ủy quyền/được cấu hình để dùng định nghĩa `String` chung từ bootstrap. Không suy rằng mỗi loader giữ một bản class lõi riêng. Khả năng định nghĩa cùng tên ở hai custom loaders chủ yếu liên quan tới class ứng dụng/namespace được phép.

### Lỗi 6: “Bị `ClassNotFoundException` tức bootstrap bị hỏng”

Không đúng. `ClassNotFoundException` có thể đến từ việc loader được yêu cầu không nhìn thấy class/path/module tương ứng. Một custom loader parent-null như ví dụ chỉ thấy bootstrap và những gì nó tự định nghĩa; việc không thấy `BootstrapParentDemo` từ class path không phải dấu hiệu JVM hỏng.

Các lỗi liên quan khác:

- `NoClassDefFoundError` có thể xuất hiện khi class cần lúc linking/runtime không tìm được hoặc initialization trước đó thất bại; tên lỗi không chứng minh bootstrap loader bị lỗi.
- `ClassCastException` giữa hai class cùng tên có thể liên quan hai **defining loaders** khác nhau.
- `LinkageError` có thể phản ánh sai khác không tương thích trong quá trình loading/linking/resolution. Cần xem stack trace đầy đủ, không chỉ dòng đầu.

### Quy trình chẩn đoán ngắn

1. Ghi lại **binary name chính xác** của kiểu, ví dụ `com.example.Plugin`, kèm cách program gọi loader.
2. Kiểm tra `clazz.getClassLoader()`, `clazz.getModule()` và `clazz.getProtectionDomain()` ở nơi hợp lệ, tránh giả định dựa trên tên package. `getProtectionDomain()` có thể có cấu hình/quyền truy cập khác nhau và không phải luôn cần thiết.
3. Kiểm tra parent chain và module/class path của loader **đang thực sự được gọi**.
4. Nếu có hai `Class<?>`, so sánh trực tiếp `classA == classB`; nếu false, xem defining loader của từng class.
5. Với HotSpot, dùng log loading, nhưng phân biệt **dòng class được load** với **dòng class được initialized**.

Lệnh logging class loading trên HotSpot JDK 17:

```bash
java -Xlog:class+load=info BootstrapLoaderDemo
```

Log có thể rất dài vì JDK và thư viện tải nhiều class. Chạy với và không chạy ứng dụng để điều tra đường nạp lớp, tránh kết luận sai khi chỉ nhìn log không có thông tin class initialization.

## 13. Bài tập tự kiểm tra

**Câu 1.** `String.class.getClassLoader()` trả `null` trên JDK 17 thông thường. Điều đó có ý nghĩa gì?

**Đáp án:** `String` được định nghĩa bởi bootstrap class loader; API dùng `null` để biểu diễn defining loader này, không có nghĩa không có class.

**Câu 2.** Trên JDK 17, tìm mã `java.lang.String` ở `rt.jar` có đúng cách không?

**Đáp án:** Không. Từ Java 9, runtime được tổ chức thành module image; `java.lang.String` thuộc `java.base`. Mô hình `rt.jar` là đặc trưng tài liệu/hệ JDK trước đây.

**Câu 3.** Nếu class ứng dụng có field `java.lang.String title`, nó có cần tự định nghĩa `String` không?

**Đáp án:** Không. Loader ứng dụng thường dùng định nghĩa `String` từ bootstrap qua cơ chế phù hợp; JVM xác định kiểu theo runtime class identity.

**Câu 4.** `ClassLoader.getPlatformClassLoader().getParent() == null` có nghĩa platform loader không còn parent?

**Đáp án:** Ở cấp API Java, `null` biểu thị bootstrap; platform loader có thể dùng bootstrap làm parent theo cơ chế này.

**Câu 5.** Hai custom class loader định nghĩa `com.example.Widget` riêng biệt. Hai runtime classes có bằng nhau không?

**Đáp án:** Không, nếu defining loader khác nhau. Binary name bằng nhau là chưa đủ; `Widget.class` của hai định nghĩa khác nhau không có cùng runtime identity.

**Câu 6.** Vì sao `new ClassLoader(null) {}` không phải “khởi tạo lại bootstrap class loader”?

**Đáp án:** Nó tạo **một object loader Java riêng** có parent được biểu diễn bằng `null`. Loader đó có thể ủy quyền cho bootstrap nhưng không trở thành cơ chế bootstrap của JVM.

**Câu 7.** Nạp `java.lang.String` qua bootstrap đã đồng nghĩa “JVM chạy constructor của một đối tượng `String`” chưa?

**Đáp án:** Chưa. Class loading/initialization là chuyện của định nghĩa lớp; object construction là chuyện của từng instance cụ thể. JVM có thể định nghĩa một lớp mà không thực thi constructor để tạo một object của chính lớp đó.

## 14. Ghi nhớ và tài liệu chuẩn

- **Bootstrap** là tầng định nghĩa các lớp lõi; ở Java API, `getClassLoader() == null` thường biểu thị defining loader bootstrap.
- **JVM** là hệ thống rộng hơn, thực hiện linking, initialization, bytecode execution, quản lý bộ nhớ và nhiều hoạt động khác.
- **JDK 17:** dùng runtime module image, có các tầng bootstrap/platform/application; đừng đồng nhất với mô hình `rt.jar` và extension loader của JDK 8.
- **Loader identity:** runtime class identity phụ thuộc cả binary name và defining class loader. Delegation giúp class ứng dụng dùng chung lõi runtime.
- **Kiểm tra thực nghiệm:** dùng `Class.getClassLoader()`, `getModule()` và ``Class.forName(name, false, null)`` để hiểu từng trường hợp theo đúng API.

Tài liệu chính thức:

1. [Java Virtual Machine Specification 17, §5 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html).
2. [Java SE 17 — `java.lang.ClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, platform/system loader.
3. [Java SE 17 — `java.lang.Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getClassLoader()`, `forName`, `getModule()`.
4. [JEP 261 — Module System](https://openjdk.org/jeps/261): hệ module và hành vi built-in loaders từ Java 9.
5. [JEP 220 — Modular Run-Time Images](https://openjdk.org/jeps/220): thay đổi tổ chức runtime image và mô hình `rt.jar`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-002">Platform class loader trong JDK hiện đại</a>

<details>
<summary>Xem chi tiết</summary>

# Platform class loader trong JDK hiện đại

**Platform class loader** là một trong các class loader tích hợp của Java runtime từ **Java 9 trở đi**. Nó giúp mã Java và các class loader tùy biến truy cập các lớp thuộc **nền tảng Java** mà không cần giả định rằng tất cả lớp của Java SE/JDK đều do **bootstrap class loader** định nghĩa. Trên Java 17+, API để lấy loader này là `ClassLoader.getPlatformClassLoader()`.

Đây là điểm khác biệt quan trọng sau khi Java chuyển sang **Java Platform Module System (JPMS)**: một phần lớp nền tảng được định nghĩa bởi bootstrap loader, một phần bởi platform loader. Vì vậy, **một lớp nhìn thấy được từ platform loader chưa chắc do chính platform loader định nghĩa**. Bài học giải thích vị trí của loader tích hợp này, cơ chế ủy quyền, mối quan hệ với module, cách quan sát trên JDK 17+ và các lỗi thiết kế thường gặp.

## 1. Vì sao Java cần platform class loader?

Trước Java 9, người học thường nhìn thấy mô hình:

```text
Bootstrap ClassLoader
       ↑
Extension ClassLoader
       ↑
Application/System ClassLoader
       ↑
Custom ClassLoader
```

Trong các JDK hiện đại, cách trình bày cơ bản trở thành:

```text
Bootstrap loader (được JVM cung cấp; Java thường biểu diễn là null)
             ↑
Platform loader (ClassLoader.getPlatformClassLoader())
             ↑
Application/System loader (ClassLoader.getSystemClassLoader())
             ↑
Custom loader có parent là system loader, nếu chọn như vậy
```

Mũi tên `↑` biểu diễn **quan hệ parent/delegation điển hình**; không phải quan hệ `extends` của class Java và cũng không chứng minh mọi lookup thực tế đi đúng một đường thẳng. Loader tùy biến có thể chọn parent khác; các loader tích hợp có xử lý đặc biệt để hỗ trợ module.

Những thay đổi chính từ Java 9:

1. **Extension mechanism cũ bị loại bỏ.** Không còn dùng `jre/lib/ext` hoặc `java.ext.dirs` làm cơ chế cài extension tự động như JDK 8.
2. **Extension class loader được thay thế về vai trò/tên bằng platform class loader.** Đừng hiểu tên mới chỉ là đổi nhãn trong khi cơ chế extension directory vẫn còn hoạt động.
3. **Nhiều lớp Java SE/JDK nằm trong các module**, không nằm trong một `rt.jar` duy nhất. Các module nền tảng có thể được định nghĩa cho bootstrap hoặc platform loader.
4. **Application loader trong các JDK phổ biến từ Java 9 không còn được bảo đảm là `URLClassLoader`.** Không ép kiểu `(URLClassLoader) ClassLoader.getSystemClassLoader()` để thêm JAR.
5. **API công khai `ClassLoader.getPlatformClassLoader()` được bổ sung từ Java 9** để ứng dụng chọn một parent phù hợp khi muốn thấy các lớp nền tảng nhưng không mặc nhiên lấy application classpath làm namespace cha.

Platform loader là một thành phần nội bộ của **hệ thống nạp lớp ở runtime**. Nó không phải một JAR, không phải một JVM mới, không phải một class loader được ứng dụng tạo riêng cho từng package và không phải tên khác của thread context class loader.

## 2. Ba loader tích hợp và vai trò khác nhau

| Thành phần | API nhận diện | Vai trò chủ yếu | Lưu ý |
| --- | --- | --- | --- |
| **Bootstrap loader** | `SomeClass.class.getClassLoader() == null` thường biểu diễn bootstrap | Nạp/định nghĩa nhiều lớp nền tảng cốt lõi, trong đó có `java.lang.String` | Bootstrap không có object `ClassLoader` thông thường được expose cho Java; `null` ở đây có ý nghĩa đặc biệt. |
| **Platform loader** | `ClassLoader.getPlatformClassLoader()` | Cho phép truy cập toàn bộ các lớp nền tảng; định nghĩa một số lớp trong các module nền tảng, đồng thời thấy lớp do bootstrap định nghĩa | Từ Java 9; có tên triển khai built-in là `platform` trong JDK thông dụng. |
| **Application/System loader** | `ClassLoader.getSystemClassLoader()` | Thường định nghĩa các class trên application classpath/module path, cùng một số công cụ JDK | Loader hệ thống có thể tùy biến qua `java.system.class.loader`; đừng luôn đồng nhất với tên lớp triển khai cụ thể. |

**Tính nhìn thấy được (visibility)** và **định nghĩa lớp (defining loader)** là hai khái niệm khác nhau. Khi gọi `platform.loadClass("java.lang.String")`, platform loader có thể ủy quyền về bootstrap. Kết quả `Class<?>` là lớp `String` đã được bootstrap định nghĩa; không phải platform vừa định nghĩa một bản `String` mới.

Tương tự, application loader nhận `loadClass("java.lang.String")` có thể ủy quyền qua platform đến bootstrap. `ClassLoader.loadClass` được gọi trên loader nào không đủ để kết luận **defining loader** của kết quả là loader đó.

### Initiating loader và defining loader

Giả sử application loader yêu cầu nạp `java.lang.String`:

```text
Yêu cầu đến application loader
            │
            ▼
Application loader  ──ủy quyền──> Platform loader
                                    │
                                    └──ủy quyền──> Bootstrap loader
                                                       │
                                                       ▼
                                                 java.lang.String
                                                 được định nghĩa
```

- **Initiating loader:** loader mà JVM hoặc mã ứng dụng dùng để yêu cầu nạp một class; nó có thể được ghi nhận là đã khởi xướng nạp class ấy.
- **Defining loader:** loader thực sự định nghĩa class từ dữ liệu nhị phân hoặc cơ chế built-in. Runtime type identity phụ thuộc **tên nhị phân + defining loader**.
- **Kết quả `loadClass`:** object `Class<?>` của định nghĩa thực tế đã tìm được; việc gọi method trên một loader không tạo bản sao class ở loader đó.

Sơ đồ mô tả parent-first thông thường. `ClassLoader` cho phép các mô hình tùy biến; loader built-in biết về module nên còn có thể thực hiện delegation theo quan hệ module, không chỉ theo đường parent tuyến tính.

## 3. Chính xác platform class loader bảo đảm điều gì?

Javadoc Java 17 của `ClassLoader.getPlatformClassLoader()` quy định: **tất cả các platform classes đều nhìn thấy được từ platform loader**. Nhóm này gồm API nền tảng Java SE, lớp triển khai của chúng và các lớp runtime JDK thích hợp, được định nghĩa bởi platform loader **hoặc một ancestor của nó**.

Từ khẳng định này có thể suy ra:

- `platform.loadClass("java.lang.String")` tìm được `String` trên một JDK bình thường, nhưng `String.class.getClassLoader()` thường là `null` (bootstrap).
- Các lớp nền tảng khác có thể do platform loader định nghĩa. Phân công **từng module/từng class** là chi tiết có thể thay đổi giữa JDK releases hoặc implementation; không nên mã hóa bảng tên class cố định trong logic nghiệp vụ.
- `ClassLoader.getPlatformClassLoader()` trả về **loader**, không trả về tập các module hay toàn bộ tên class được định nghĩa bởi loader.
- Không có bảo đảm rằng mọi class nằm trên **application classpath** đều nhìn thấy được từ platform loader.
- Khả năng loader tìm được một `Class<?>` không thay thế các quy tắc **module readability**, `exports` và kiểm soát truy cập runtime của JPMS.

Một hiểu nhầm hay gặp: "**platform class loader là loader trực tiếp định nghĩa tất cả lớp của gói `java.*`**". Điều này sai. Chẳng hạn `java.lang.String` thuộc `java.base` và thường do bootstrap định nghĩa. Ngược lại, tên package bắt đầu bằng `java.` cũng không cung cấp API chính thức để xác định mọi defining loader; hãy quan sát đối tượng `Class` cụ thể.

### Quan hệ parent có phải là quy tắc tuyệt đối?

Với cấu hình JDK 17 phổ biến, platform loader là parent của application loader; parent của platform loader được biểu diễn như bootstrap (`null`). Tuy nhiên:

- `ClassLoader.getSystemClassLoader()` có thể được thay bằng loader do cấu hình hệ thống cung cấp qua `-Djava.system.class.loader=...`; không giả định mọi trường hợp `getSystemClassLoader().getParent() == getPlatformClassLoader()`.
- **Platform loader có thể phải delegate đến loader khác, kể cả application loader**, khi xử lý các module đã được nâng cấp/ghi đè và phụ thuộc module. Đây là ngoại lệ đã được ghi trong Javadoc `ClassLoader`.
- Trên hệ module, quan hệ **loader parent** và quan hệ **module reads** là hai đồ thị khác nhau. `Module X reads Module Y` không đồng nghĩa `class loader của X là con của class loader của Y`.
- Dù một loader có thể thấy class, quyền **link trực tiếp hoặc reflection** vẫn chịu ràng buộc của ngôn ngữ và JPMS.

## 4. Thực hành A — quan sát platform, system và bootstrap bằng Java 17+

Tạo một chương trình chạy độc lập duy nhất trong cây:

```text
platform-loader-lab/
└── src/
    └── demo/
        └── PlatformOverview.java
```

Nội dung **`src/demo/PlatformOverview.java`**:

```java
package demo;

public class PlatformOverview {
    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader system = ClassLoader.getSystemClassLoader();

        Class<?> stringFromPlatform =
                platform.loadClass("java.lang.String");

        System.out.println("platform exists: " + (platform != null));
        System.out.println("platform != system: " + (platform != system));
        System.out.println("String reachable: " +
                (stringFromPlatform == String.class));
        System.out.println("String bootstrap: " +
                (String.class.getClassLoader() == null));
        System.out.println("app loaded by system: " +
                (PlatformOverview.class.getClassLoader() == system));
    }
}
```

Trong thư mục `platform-loader-lab`, với **JDK 17+ thông dụng, launcher mặc định**:

```sh
javac --release 17 -d out src/demo/PlatformOverview.java
java -cp out demo.PlatformOverview
```

**Stdout trong cấu hình mặc định của OpenJDK/HotSpot 17+**:

```text
platform exists: true
platform != system: true
String reachable: true
String bootstrap: true
app loaded by system: true
```

Giải thích theo từng dòng:

1. `ClassLoader.getPlatformClassLoader()` luôn trả về một platform loader hợp lệ cho JDK hỗ trợ API.
2. Platform và system là **hai loader phân biệt**. Không dùng tên class thực thi nội bộ để kiểm tra điều này.
3. `platform.loadClass("java.lang.String")` có thể nhìn thấy lớp do bootstrap định nghĩa; kết quả cùng identity với `String.class`.
4. `String.class.getClassLoader() == null` là cách Java thường biểu diễn việc `String` được định nghĩa bởi bootstrap trên JDK tương ứng.
5. `PlatformOverview` nằm trong `out`, được `-cp out` đưa vào application classpath; với launcher/default built-in system loader, defining loader của chương trình bằng system loader.

Nếu ứng dụng sử dụng custom system loader, agent, module layer khác hoặc một JVM implementation đặc biệt, một số chi tiết quan sát về defining loader có thể khác; đọc code và phạm vi stdout là **JDK OpenJDK/HotSpot 17+ mặc định**, không phải lời hứa độc lập mọi JVM.

### Đừng nhầm tên hiển thị với hợp đồng API

Trên nhiều bản OpenJDK hiện đại:

```java
System.out.println(ClassLoader.getPlatformClassLoader().getName());
System.out.println(ClassLoader.getSystemClassLoader().getName());
```

thường hiện:

```text
platform
app
```

Đây là đoạn **minh họa chèn vào `main` nếu muốn quan sát**, không phải chương trình độc lập. Tên `platform` và `app` là implementation notes trong Javadoc built-in loaders; phân tích ứng dụng nên dựa vào API và object identity, không vào `loader.getClass().getName()` hoặc chuỗi tên hiển thị.

## 5. Vì sao Java 9 thay đổi cách các lớp nền tảng được nạp?

Trước Java 9, cơ chế extension cho phép đặt JAR trong một thư mục của JRE để class loader mở rộng nhìn thấy mà ứng dụng không khai báo trong `-cp`. Điều này dẫn tới thói quen coi bootstrap + extension là một vùng classes ngoài application classpath.

Từ Java 9:

- JDK dùng **module image** runtime thay vì mô hình phân phối dựa trên `rt.jar` truyền thống. Trong JDK cài đặt thông dụng, nhiều class nền tảng được cung cấp qua hệ thống module và có thể quan sát qua URL `jrt:/...`; không nên cố tìm mọi class bằng cách mở `rt.jar`.
- **Platform class loader** được cung cấp qua API ổn định, phục vụ khả năng truy cập các lớp nền tảng một cách phù hợp mô hình module.
- Cơ chế `lib/ext`/`java.ext.dirs` để triển khai extension đã bị loại bỏ. Thư viện bên thứ ba phải được cấu hình thông qua **classpath**, **module path** hoặc cơ chế load/module layer được thiết kế rõ ràng.
- Legacy code giả định `getSystemClassLoader() instanceof URLClassLoader` hoặc `new URLClassLoader(urls, null)` nhìn thấy mọi Java SE classes cần được xem lại.

Không nên mô tả platform loader là "thư mục chứa extension JAR mới". Từ "platform" ở đây chỉ **vai trò của loader trong nền tảng Java**, không chỉ một đường dẫn tìm file.

## 6. Platform loader và JPMS: ba tầng cần tách

Có ba câu hỏi khác nhau khi ứng dụng chạy với modules:

1. **Module có được resolve vào boot layer hoặc layer phù hợp hay không?** Một module trong image/path không có nghĩa mọi module đều được chọn vào graph.
2. **Module được định nghĩa cho class loader nào?** Bootstrap/platform/application có thể tham gia; mapping phụ thuộc runtime.
3. **Một class/module khác được phép dùng API hay không?** Điều này liên quan `requires`/readability, `exports` và `opens`, không chỉ loader có tìm thấy bytes.

Ví dụ một module `app.module` khai báo:

```java
module app.module {
    requires java.sql;
}
```

Đây là **tệp `module-info.java` minh họa**, không độc lập để chạy. `requires java.sql` tạo quan hệ readability để mã trong module `app.module` sử dụng API exported của `java.sql`. Không có nghĩa class trong `java.sql` bắt buộc phải được application loader định nghĩa, cũng không có nghĩa platform loader sẽ tải mọi database driver do bên thứ ba phát hành.

Mở rộng góc nhìn:

| Thuật ngữ | Câu hỏi trả lời | Cần tránh suy luận |
| --- | --- | --- |
| Module resolution | Module nào tham gia cấu hình runtime? | "Có trên module path" = "đã resolved". |
| Readability | Module caller có đọc module đích? | Loader load được = compiler/runtime chắc chắn cho đọc. |
| Exports | Package có cho phép module ngoài truy cập public types? | Public class = luôn dùng được ở mọi module. |
| Opens | Package có mở cho deep reflection phù hợp? | Load class thành công = `setAccessible` luôn thành công. |
| Defining loader | Loader nào tạo runtime class identity? | Module name hoặc package name tự quyết định loader. |
| Parent delegation | Khi loader không xử lý, có ủy quyền cho loader khác? | Đây là đồ thị giống hệt `requires`. |

### Platform loader có phải chỉ nhìn thấy các module do chính nó định nghĩa?

Không. Javadoc quy định platform loader **nhìn thấy platform classes**, kể cả các lớp do ancestor (bootstrap) định nghĩa. Hơn nữa, trong một số tình huống **upgraded modules** cần tham chiếu module được định nghĩa bởi loader khác, platform loader có thể delegate ngoài parent chain (thậm chí tới application loader). Do đó sơ đồ bootstrap ← platform ← app vẫn rất hữu ích để học nhưng không phải mô hình tra cứu module tuyệt đối.

## 7. Thực hành B — dùng platform loader làm parent của loader tùy biến

Một lý do cụ thể để lấy platform loader là **làm parent của class loader do ứng dụng tự tạo**. Khi plugin chỉ cần các lớp nền tảng và muốn tránh vô tình nhìn thấy mọi class của application classpath qua parent, có thể chọn platform loader.

Tạo file:

```text
platform-parent-lab/
└── src/
    └── demo/
        ├── AppOnly.java
        └── ParentVisibility.java
```

**`src/demo/AppOnly.java`**:

```java
package demo;

public class AppOnly {
    public static String value() {
        return "in app classpath";
    }
}
```

**`src/demo/ParentVisibility.java`**:

```java
package demo;

public class ParentVisibility {
    public static void main(String[] args) throws Exception {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        ClassLoader plugin = new ClassLoader(platform) { };

        Class<?> core = plugin.loadClass("java.lang.String");
        System.out.println("platform API visible: " +
                (core == String.class));

        try {
            plugin.loadClass("demo.AppOnly");
            System.out.println("app class visible: true");
        } catch (ClassNotFoundException e) {
            System.out.println("app class visible: false");
        }

        Class<?> app = ClassLoader.getSystemClassLoader()
                .loadClass("demo.AppOnly");
        System.out.println("system sees app: " +
                (app == AppOnly.class));
    }
}
```

Biên dịch và chạy bằng JDK 17+ từ thư mục `platform-parent-lab`:

```sh
javac --release 17 -d out src/demo/AppOnly.java src/demo/ParentVisibility.java
java -cp out demo.ParentVisibility
```

**Stdout trên JDK OpenJDK/HotSpot mặc định với classpath như trên:**

```text
platform API visible: true
app class visible: false
system sees app: true
```

**Vì sao?**

- `new ClassLoader(platform) { }` tạo một subclass ẩn danh dùng **default parent-first** của `ClassLoader`, không ghi đè `findClass` và không có logic đọc file/JAR riêng.
- Đối với `java.lang.String`, loader mới ủy quyền lên platform/bootstrap và tìm thấy class.
- Đối với `demo.AppOnly` trên **unnamed application classpath**, platform loader không dùng application classpath làm nguồn tra cứu theo cơ chế mặc định; loader mới cũng không tự đọc file. Kết quả là `ClassNotFoundException`.
- System loader có thể tìm `demo.AppOnly` từ `-cp out`, do đó trả về cùng `Class` với `AppOnly.class`.

**Phạm vi của ví dụ:** Đó là một loader không có cách tự nạp plugin, dùng để minh họa **ranh giới parent visibility**. Nó **chưa phải plugin loader hoàn chỉnh**. Plugin loader thực tế phải có nguồn bytecode, định nghĩa package/module hợp lệ, quản lý dependency và cơ chế chia sẻ API thích hợp.

### Vì sao không luôn chọn parent là `null`?

Constructor `ClassLoader(null)` dùng bootstrap làm parent theo cơ chế `ClassLoader` thông thường. Nhưng từ Java 9, **không bảo đảm tất cả platform classes đều nhìn thấy được qua bootstrap**. Plugin cần dùng Java SE API thuộc module do platform loader định nghĩa có thể thất bại khi parent chỉ là bootstrap.

Chọn `ClassLoader.getPlatformClassLoader()` làm parent giúp loader con có quyền **nhìn thấy đầy đủ platform classes** theo Javadoc. Nếu plugin cần đồng thời dùng các application classes như `com.myapp.spi.Plugin` được định nghĩa bởi application loader, parent là platform có thể không phù hợp: cần thiết kế rõ class loader dùng chung cho SPI hoặc cơ chế module layer/delegation phù hợp. Đừng thay parent tùy tiện với kỳ vọng chỉ thay hiệu năng.

### Khi nào lại chọn system loader làm parent?

Nếu plugin cần truy cập nhiều API của chính ứng dụng đang chạy, system loader (hoặc một loader chứa shared API) có thể phù hợp hơn. Đổi lại, nếu plugin có dependency cùng tên với ứng dụng, parent-first có thể khiến class của ứng dụng được ưu tiên; từ đó gây version conflict.

Không có một parent duy nhất phù hợp cho mọi framework. Tiêu chí chọn là **những lớp nào cần chia sẻ identity** giữa core và plugin, những lớp nào cần cách ly, và module nào cần được nhìn thấy.

## 8. Thực hành C — quan sát module và defining loader mà không dựa vào tên nội bộ

Tạo một file độc lập `src/demo/ModuleView.java`:

```java
package demo;

public class ModuleView {
    public static void main(String[] args) {
        Class<?> platformClass = java.sql.Driver.class;

        System.out.println("module: " +
                platformClass.getModule().getName());

        ClassLoader loader = platformClass.getClassLoader();
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader system = ClassLoader.getSystemClassLoader();

        String owner = loader == null ? "bootstrap"
                : loader == platform ? "platform"
                : loader == system ? "system" : "other";

        System.out.println("owner: " + owner);
        System.out.println("visible from platform: " +
                canLoad(platform, platformClass.getName(), platformClass));
    }

    private static boolean canLoad(ClassLoader loader,
                                   String name,
                                   Class<?> expected) {
        try {
            return loader.loadClass(name) == expected;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
```

Biên dịch và chạy từ thư mục bài thực hành bằng JDK 17+ đầy đủ, hỗ trợ module `java.sql`:

```sh
javac --release 17 -d out src/demo/ModuleView.java
java -cp out demo.ModuleView
```

Output được thiết kế để **phân biệt thông tin ổn định và thông tin tùy JVM**:

```text
module: java.sql
owner: <bootstrap hoặc platform, tùy phân công của JDK>
visible from platform: true
```

Dòng `owner` trên đây là **sơ đồ kết quả minh họa**, ký hiệu trong dấu `<...>` không phải stdout nguyên văn. Hãy chạy chương trình để xem loader thực tế; trên các bản OpenJDK phổ biến, `java.sql` thường do platform loader định nghĩa, nhưng bài học không gắn một module cụ thể với defining loader như hợp đồng ổn định. Dòng module `java.sql` và khả năng nhìn thấy qua platform là đặc tính được quan sát/đảm bảo khi chạy trên JDK đầy đủ như mô tả. Với runtime image tạo qua `jlink` hoặc cấu hình giới hạn module, thiếu module `java.sql` có thể khiến chương trình biên dịch/chạy không phù hợp; cần đảm bảo module đó hiện diện.

Giá trị của chương trình: nó hỏi **chính đối tượng `Class<?>`** xem module và loader, sau đó dùng `platform.loadClass` để thử visibility. Đừng so sánh tên JVM internal loader class, vì không có bảo đảm tương thích nhị phân khi nhà cung cấp JDK đổi implementation.

## 9. Phân biệt platform loader với Thread Context Class Loader (TCCL)

Platform loader là **một loader built-in** xác định qua API static `ClassLoader.getPlatformClassLoader()`. Trong khi đó, **Thread Context Class Loader** là thuộc tính thay đổi được của từng thread:

```java
ClassLoader context = Thread.currentThread().getContextClassLoader();
```

Đây là đoạn code **minh họa** có thể đặt trong `main`. Trên JVM launcher thông dụng, context loader ban đầu của main thread thường là system loader. Nhưng framework có thể thay nó để thực hiện discovery/nạp plugin.

Không nên suy luận:

```text
Thread.currentThread().getContextClassLoader()
    == ClassLoader.getPlatformClassLoader()
```

là điều mặc định, hay mọi lệnh `Class.forName` tự động dùng context loader. Có sự khác biệt quan trọng:

- `Class.forName(String)` dùng loading context của lớp gọi theo API Java, đồng thời **yêu cầu initialization**.
- `Class.forName(String, boolean, ClassLoader)` dùng loader được truyền trực tiếp, có thể chọn không initialization.
- `ServiceLoader.load(Class<S>)` thường dùng **context class loader của thread hiện tại** để tìm provider; `ServiceLoader.load(Class<S>, ClassLoader)` cho phép truyền loader.
- `ClassLoader.getPlatformClassLoader()` chỉ cung cấp **object loader nền tảng**; không tự thay đổi TCCL của thread.

Ví dụ JDBC: `java.sql.Driver` là **API thuộc nền tảng**; driver do nhà cung cấp ứng dụng đưa vào classpath/module path là **mã bên thứ ba**. Tìm thấy `java.sql.Driver` qua platform loader không đảm bảo platform loader thấy tất cả các class triển khai driver. Nếu framework tìm driver/provider theo TCCL hoặc ServiceLoader, loader dùng để discovery có thể là system/plugin loader. Chỉ cần thay TCCL cũng có thể đổi tập provider tìm thấy, dù platform loader không đổi.

## 10. Những khác biệt quan trọng khi chọn parent của custom loader

Có thể minh họa quyết định qua bảng sau:

| Mục tiêu | Parent đáng cân nhắc | Rủi ro phải kiểm soát |
| --- | --- | --- |
| Plugin cần Java platform API, muốn tách khỏi ứng dụng | `ClassLoader.getPlatformClassLoader()` | Không thấy application SPI/SDK nếu không tạo kênh chia sẻ rõ ràng. |
| Plugin cần interface/core libraries của ứng dụng | System loader hoặc loader chứa các shared API | Parent-first có thể khiến plugin dùng phiên bản dependency ở core. |
| Cô lập tập class tùy biến ở mức thấp | Loader riêng với kế hoạch delegation cụ thể | Type identity, package sealing, module access, resource lookup, TCCL. |
| Legacy loader từng dùng parent `null` cho mọi class Java | Chuyển sang platform parent nếu thực sự cần toàn bộ Java SE/JDK API | Giữ nguyên `null` có thể thiếu platform-defined classes sau Java 9. |

**Không thể chỉ thay platform/system parent để tạo sandbox bảo mật.** Class loader chủ yếu giải quyết định nghĩa và không gian tên của lớp. Ranh giới bảo mật, quyền truy cập file/network và thực thi mã không đáng tin cần cơ chế độc lập; class loader cách ly tên không tự làm code trở nên an toàn.

**Không dùng platform loader như kho lưu thư viện ứng dụng.** `ClassLoader.getPlatformClassLoader()` không cung cấp API để thêm tùy ý JAR vào platform path. Nếu ứng dụng muốn hot-load JAR, hãy thiết kế loader riêng hoặc module layer và vòng đời phù hợp.

### Class identity là điểm phải kiểm tra

JVM định danh runtime class/interface bằng **(binary name, defining loader)**. Nếu `com.example.spi.Plugin` xuất hiện trong hai JAR và bị hai loaders định nghĩa riêng, hai class có thể cùng `getName()` nhưng **không assignable cho nhau**. Điều này gây `ClassCastException` hoặc `LinkageError` tùy ngữ cảnh.

Một plugin dùng platform parent nhưng tự nạp bản sao interface SPI của ứng dụng có thể không cast được sang interface SPI mà app loader định nghĩa. Hãy đặt **shared SPI** dưới một loader chung trong cấu trúc mà cả hai phía thật sự dùng, hoặc có lớp chuyển dữ liệu qua ranh giới. Đừng lấy `getName()` giống nhau làm bằng chứng identity giống nhau.

## 11. Quan sát ứng dụng thực tế bằng công cụ JDK

### 11.1. Xem class đến từ module/JAR nào

Với ví dụ A:

```sh
java -Xlog:class+load=info -cp out demo.PlatformOverview
```

Đây là **Unified Logging** trên các JVM hỗ trợ, tiêu biểu là HotSpot. Log có nhiều dòng và thứ tự phụ thuộc runtime, nên không có stdout cố định cho lệnh này. Các dòng load thường kèm tên class và nguồn như `jrt:/java.base` hoặc đường dẫn classpath. **Nguồn class bytes không tự cho biết toàn bộ quan hệ parent**; kết hợp với `getClassLoader()` để nhận diện defining loader.

### 11.2. Kiểm tra các module có trong JDK

```sh
java --list-modules
java --describe-module java.sql
```

Dùng `java --list-modules` để biết module nào có trong runtime image hiện tại; `java --describe-module` cho thông tin module. Danh sách xuất ra là dữ liệu cấu hình, **không trực tiếp nói loader nào định nghĩa từng class**. Nếu đang dùng `jlink` để tạo custom image, hãy kiểm tra module có hiện diện trước khi chạy ví dụ C.

### 11.3. In quan hệ parent trong môi trường đang chạy

Có thể thêm vào `main` của ví dụ A:

```java
System.out.println(
        ClassLoader.getSystemClassLoader().getParent()
                == ClassLoader.getPlatformClassLoader());
System.out.println(
        ClassLoader.getPlatformClassLoader().getParent() == null);
```

Trong OpenJDK/HotSpot mặc định, thường in hai dòng `true`. Đây là đoạn **chèn bổ sung**, không phải file Java độc lập. Với custom system loader hoặc cấu hình đặc biệt, không dựa vào giả định quan hệ parent luôn giống ví dụ.

## 12. Tình huống lỗi thường gặp và cách chẩn đoán

| Hiện tượng | Nguyên nhân có thể | Kiểm tra/khắc phục |
| --- | --- | --- |
| `ClassNotFoundException` khi dùng loader với parent `null` | Lớp Java SE mong muốn được platform, không phải bootstrap, định nghĩa; hoặc phụ thuộc khác không thấy được | Kiểm tra `getPlatformClassLoader()`; đối chiếu module hiện diện trong runtime; cân nhắc platform parent. |
| `ClassNotFoundException` với app class khi dùng platform parent | Platform loader không tìm class tùy ý trong unnamed application classpath | Thiết kế parent phù hợp hoặc thêm nguồn class riêng cho child loader; không "nhét" app JAR vào platform. |
| `ClassCastException` dù binary name giống | Hai defining loaders độc lập định nghĩa cùng API | Quan sát `getClassLoader()` của hai `Class`; tổ chức lại shared SPI và delegation. |
| `LinkageError` với loading constraint | Descriptor qua các loader yêu cầu cùng identity nhưng nhận class không tương thích | Kiểm tra full stacktrace, defining loaders, phiên bản API và module graph. |
| `IllegalAccessError` / `InaccessibleObjectException` | Ràng buộc runtime access/module exports/opens | Kiểm tra `requires`, `exports`, `opens` và cách sử dụng reflection. |
| Legacy `java.ext.dirs` không còn hoạt động | Extension mechanism của JDK 8 đã bị loại bỏ | Di chuyển dependency sang `-cp`, `--module-path`, hoặc loader riêng. |
| `ClassCastException` khi ép system loader thành `URLClassLoader` | Ứng dụng phụ thuộc implementation JDK 8 | Bỏ ép kiểu; dùng API loader thích hợp hoặc thiết kế nạp riêng. |
| ServiceLoader không tìm driver/provider | TCCL, module `provides/uses`, classpath hoặc layer khác dự tính | Kiểm tra discovery loader, module graph và cấu hình provider. |
| Có module trên path nhưng class vẫn không truy cập | Module chưa resolved hoặc thiếu quyền readability/exports | Kiểm tra `--add-modules`/root modules và descriptor; không chỉ xem class loader. |

**Cách debug có trình tự:**

1. Xác định **tên class được yêu cầu**, loader nào khởi tạo yêu cầu nạp và API phát sinh lỗi.
2. In **defining loader** của `Class` (nếu đã lấy được), dùng `getClassLoader()`; `null` biểu diễn bootstrap trong ngữ cảnh này.
3. Kiểm tra **classpath/module path thực sự lúc chạy**, kể cả custom runtime image và module graph.
4. Đối chiếu **parent của custom loader**: `null`, platform, system hay loader cung cấp shared API?
5. Nếu class tìm thấy mà vẫn lỗi, kiểm tra **resolution**, binary API, access rules, module exports/opens và TCCL.
6. Thực hiện lại từ quy trình chạy tối giản xác định JDK, tránh phán đoán chỉ dựa vào tên file JAR.

### Phân biệt những lệnh gọi hay bị nhầm

```java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
Class<?> clazz = platform.loadClass("java.lang.String");
Class<?> loaded = Class.forName("java.lang.String", false, platform);
```

Đây là đoạn minh họa có thể đặt trong method có `throws ClassNotFoundException`. Cả hai API đều cho phép nhận lớp theo tên nhị phân và loader; `Class.forName` có tham số `initialize=false` để không yêu cầu initialization. `loadClass(String)` thường cũng không yêu cầu initialization. Truy xuất đối tượng `Class<?>` **không đồng nghĩa đã tạo instance**.

## 13. Lỗi hiểu nhầm cần tránh

- **"Platform class loader chính là bootstrap loader."** Không: platform là object loader riêng, còn bootstrap thường được biểu diễn bằng `null`.
- **"Mọi lớp `java.*` do platform định nghĩa."** Không: có lớp do bootstrap định nghĩa và vẫn nhìn thấy từ platform.
- **"Platform loader tải mọi thư viện ứng dụng."** Không: classpath ứng dụng thông thường do application/system loader xử lý.
- **"Platform loader là Extension loader y nguyên với thư mục `lib/ext`."** Không: cơ chế extension directory cũ đã bị loại bỏ ở Java 9.
- **"Loader parent hierarchy luôn trùng module dependency graph."** Không: JPMS readability, định nghĩa module và class loader là các quan hệ khác nhau.
- **"Platform chỉ có thể hỏi bootstrap, không bao giờ delegate ngược."** Không: Javadoc có trường hợp module upgrade cần nhìn class ở application loader.
- **"`getPlatformClassLoader()` thay thế TCCL."** Không: TCCL là thuộc tính thread có thể thay đổi, dùng bởi một số discovery APIs.
- **"Có thể dùng platform parent để cách ly bảo mật hoàn toàn plugin."** Không: class loader chủ yếu phân chia namespace/visibility; security cần cơ chế khác.
- **"Class cùng tên là cùng kiểu."** Không: identity còn phụ thuộc defining loader.
- **"Platform loader nào cũng là `URLClassLoader` nên thêm URL được."** Không: không phụ thuộc implementation nội bộ.

## 14. Câu hỏi tự kiểm tra

**1. Platform loader được giới thiệu từ Java nào?** Từ Java 9, qua `ClassLoader.getPlatformClassLoader()`.

**2. Vì sao platform loader nhìn thấy `java.lang.String` dù defining loader của `String` là bootstrap?** Vì platform có thể ủy quyền lên bootstrap; visibility khác defining identity.

**3. Tại sao parent `null` có thể gây lỗi sau khi nâng cấp từ Java 8 lên Java 17?** Vì Java 9+ không bảo đảm mọi lớp nền tảng đều được bootstrap định nghĩa; một số nằm ở platform loader.

**4. Chọn platform làm parent của custom plugin loader có tự nạp class trên application classpath không?** Không. Loader con phải có nguồn tự tìm class, hoặc quan hệ delegation được thiết kế để thấy application classes.

**5. Hai class cùng tên nạp từ hai custom loaders khác nhau có luôn tương đương không?** Không. Defining loader là một phần của runtime type identity.

**6. Mã chạy trong module `app.module` thấy API `java.sql` thì có phải app loader định nghĩa tất cả class `java.sql`?** Không. Module readability và loader mapping là hai vấn đề riêng.

**7. Vì sao không ép kiểu system loader về `URLClassLoader`?** Vì implementation của system loader không được đặc tả phải là `URLClassLoader`, và các JDK từ Java 9 đã thay đổi.

**8. Khi nào dùng TCCL thay vì platform loader?** Khi API/framework cần khám phá provider hoặc tài nguyên trong context của thread/plugin; điều này có mục đích khác với lấy platform loader làm parent.

## 15. Tài liệu chuẩn và hướng mở rộng

- [Java SE 17 `ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): mục **Run-time Built-in Class Loaders**, `getPlatformClassLoader()`, `getSystemClassLoader()`, `getParent()` và delegation.
- [Java SE 17 `Class` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getClassLoader()`, `getModule()` và runtime class metadata.
- [Java SE 17 `Module` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html): module identity, readability và quan hệ module.
- [JDK Migration Guide — New Class Loader Implementations](https://docs.oracle.com/en/java/javase/15/migrate/): migration từ extension mechanism sang platform class loader và cảnh báo về parent `null`.
- [JEP 261 — Module System](https://openjdk.org/jeps/261): bối cảnh module path, runtime image và built-in loader từ Java 9.

**Ghi nhớ:** platform class loader là **điểm truy cập ổn định đến các lớp nền tảng** và là một lựa chọn parent hữu ích cho loader tùy biến trên JDK hiện đại. Khi phân tích một class, hãy tách bạch **nó thuộc module nào**, **loader nào định nghĩa nó**, **loader nào nhìn thấy nó**, và **caller có quyền truy cập nó hay không**. Bốn câu hỏi này giúp tránh phần lớn nhầm lẫn khi di chuyển ứng dụng Java 8 lên 17+ hoặc thiết kế hệ thống plugin.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-003">System class loader và application class loader</a>

<details>
<summary>Xem chi tiết</summary>

# System Class Loader và Application Class Loader trong Java

## 1. Mục tiêu và câu trả lời cốt lõi

Khi học về các **class loader tích hợp** của Java, hai cách gọi dễ gây nhầm lẫn là **system class loader** và **application class loader (app class loader)**. Trong cách chạy ứng dụng phổ biến trên OpenJDK/HotSpot từ Java 9 trở lên, chúng **thường chỉ cùng một loader**: loader ứng dụng tích hợp, chịu trách nhiệm nạp nhiều lớp của ứng dụng, đặc biệt là các lớp trên **application class path**.

Tuy nhiên, đây không phải hai thuật ngữ luôn đồng nghĩa trong mọi cấu hình:

- **System class loader** nhấn mạnh **vai trò do API JVM/JDK công bố**: đối tượng được trả về bởi `ClassLoader.getSystemClassLoader()`. Nhiều API tĩnh như `ClassLoader.getSystemResource(...)` sử dụng loader hệ thống này.
- **Application class loader** nhấn mạnh **loader tích hợp của môi trường chạy tiêu chuẩn** thường nạp lớp của ứng dụng. Trong cách triển khai OpenJDK hiện đại, tên lớp triển khai thường có hậu tố `AppClassLoader`, nhưng đây là **chi tiết triển khai**, không phải tên kiểu công khai bảo đảm bởi Java SE.
- Nếu ứng dụng chỉ định thuộc tính khởi động `java.system.class.loader`, JVM có thể dùng **một class loader tùy chỉnh** làm system class loader. Khi đó không nên kết luận “system loader = đối tượng AppClassLoader mặc định”.
- Một ứng dụng có thể dùng thêm nhiều **custom class loader** cho plugin, ứng dụng web, bộ kiểm thử, module động hoặc cơ chế hot reload. Những lớp do loader con định nghĩa không nhất thiết được định nghĩa bởi system loader.

Bài học này tập trung vào vai trò, API, quan hệ ủy quyền, class path/module path, quan sát thực nghiệm và cách debug hai khái niệm trên ở Java 17+.

## 2. Vị trí của Application/System Loader trong các loader tích hợp

Trong môi trường JDK 17 thông thường, chuỗi parent delegation có thể hình dung như sau:

~~~text
Bootstrap Class Loader                (được biểu diễn bằng null trong nhiều API)
         ↑  được ủy quyền tìm lớp trước
Platform Class Loader                 ClassLoader.getPlatformClassLoader()
         ↑
Application Class Loader              thường = ClassLoader.getSystemClassLoader()
         ↑
Custom Child Class Loader             nếu chương trình tạo và đặt parent thích hợp
~~~

Mũi tên biểu thị **quan hệ parent và đường ủy quyền mặc định**, không phải “lớp con” trong hệ thống kế thừa Java. Class loader là đối tượng Java (trừ bootstrap được JVM triển khai theo cơ chế riêng). Quan hệ parent của class loader cũng **không đồng nghĩa** quan hệ `extends` của các lớp mà nó định nghĩa.

### Bootstrap loader

Bootstrap loader phụ trách nhiều lớp lõi của Java runtime, ví dụ `java.lang.String` trong JDK thông thường. Khi gọi `String.class.getClassLoader()`, kết quả thường là **null** để biểu diễn bootstrap loader; null không có nghĩa là lớp chưa được load.

### Platform loader

Từ Java 9, **platform class loader** thay vai trò tên gọi trước đây của extension class loader (`ExtClassLoader`). Lấy bằng `ClassLoader.getPlatformClassLoader()`; trong cấu hình JDK 17 chuẩn, parent của nó là bootstrap loader. Nó liên quan đến các module thuộc nền tảng Java và việc ủy quyền nạp lớp theo cơ chế module hiện đại. Không dùng sơ đồ Java 8 với `lib/ext` để giải thích máy chạy Java 17.

### Application loader

Application loader tích hợp thường nằm dưới platform loader và nạp các lớp ứng dụng. Trong một chương trình chạy bằng `java -cp out Main`, lớp `Main` thường do application loader định nghĩa và đó cũng là loader mà `ClassLoader.getSystemClassLoader()` trả về, nếu không có tùy chỉnh hệ thống.

Các lớp trên **class path** thường thuộc **unnamed module** tương ứng với loader định nghĩa chúng. Trong Java 9+, một loader còn có thể liên quan tới **named module**. Vì vậy “application loader chỉ biết class path” là mô tả không đầy đủ trong hệ thống module hiện đại.

> Quy tắc an toàn: gọi API để kiểm tra **đối tượng loader cụ thể** đang chạy; đừng suy luận chỉ từ tên `AppClassLoader`, `Launcher` hoặc cách tổ chức file bên trong JDK.

## 3. System class loader là tên một vai trò API

API chính:

~~~java
ClassLoader system = ClassLoader.getSystemClassLoader();
~~~

Đây là một **static method** của `java.lang.ClassLoader`. Theo hợp đồng API, nó trả về system class loader dùng cho cơ chế nạp lớp của Java runtime. Trong môi trường Java thông thường, lời gọi trả về một đối tượng không null.

Một số API liên quan:

| API | Mục đích |
| --- | --- |
| `ClassLoader.getSystemClassLoader()` | Lấy system class loader đang được JVM sử dụng |
| `ClassLoader.getPlatformClassLoader()` | Lấy platform class loader (Java 9+) |
| `ClassLoader.getSystemResource(String)` | Tìm một tài nguyên thông qua system class loader |
| `ClassLoader.getSystemResources(String)` | Liệt kê các URL tài nguyên có cùng tên qua system loader |
| `ClassLoader.getSystemResourceAsStream(String)` | Mở tài nguyên bằng system loader |
| `ClassLoader.loadClass(String)` | Yêu cầu một loader cụ thể tìm/định nghĩa lớp theo tên nhị phân |
| `Class.getClassLoader()` | Biết **defining loader** của một kiểu runtime cụ thể |
| `Thread.currentThread().getContextClassLoader()` | Lấy **thread context class loader (TCCL)**; có thể khác system loader |

Ba thao tác sau trả lời ba câu hỏi **khác nhau**:

~~~java
ClassLoader a = ClassLoader.getSystemClassLoader();        // Loader hệ thống
ClassLoader b = MyApp.class.getClassLoader();               // Loader định nghĩa MyApp
ClassLoader c = Thread.currentThread().getContextClassLoader(); // TCCL
~~~

Đây là **đoạn minh họa**, giả sử `MyApp` là lớp có thật. Trong lối chạy phổ biến, a, b và c có thể cùng đối tượng. Trong runtime phức tạp hoặc với custom system loader, các đối tượng này **có thể khác nhau**. Không nên thay thế lẫn nhau một cách tùy tiện trong thư viện.

## 4. Application loader: tên phổ biến của loader tích hợp

Một JVM/JDK phải hỗ trợ hệ thống nạp lớp theo đặc tả, nhưng **tên class Java nội bộ** của loader mặc định không phải API tương thích của ứng dụng. Với OpenJDK/HotSpot hiện đại, một dòng quan sát bằng `getClass().getName()` có thể có dạng:

~~~text
jdk.internal.loader.ClassLoaders$AppClassLoader
~~~

Dòng này là **ví dụ đầu ra phụ thuộc JDK**, không phải hợp đồng rằng mọi JVM hoặc bản phát hành luôn in y hệt. Phần `jdk.internal.*` biểu thị lớp triển khai nội bộ; không import nó, không ép kiểu sang nó và không viết logic dựa vào tên chuỗi này.

Trong một ứng dụng chạy thông thường:

1. JDK launcher chuẩn bị môi trường chạy, bao gồm class path hoặc module path theo tham số được truyền.
2. Loader ứng dụng tìm/định nghĩa những lớp thích hợp theo cấu hình đó.
3. `ClassLoader.getSystemClassLoader()` thường trả về chính loader ứng dụng mặc định.
4. Lớp của thư viện trên class path cũng thường do loader này nạp nếu chưa được parent hoặc loader khác xử lý.
5. Lớp thuộc runtime Java có thể do bootstrap hoặc platform loader định nghĩa dù được tham chiếu từ code ứng dụng.

Do đó **“lớp được app sử dụng” không đồng nghĩa “lớp được application loader định nghĩa”**. Ví dụ ứng dụng sử dụng `java.lang.String`, nhưng lớp `String` thuộc loader lõi, không vì được gọi từ `main` mà chuyển sang app loader.

## 5. Thí nghiệm Java 17+: in loader và quan hệ parent

Tạo file **SystemLoaderDemo.java** đúng như sau:

~~~java
public class SystemLoaderDemo {
    static class Marker {
    }

    private static String name(ClassLoader loader) {
        return loader == null ? "<bootstrap>" : loader.getClass().getName();
    }

    public static void main(String[] args) {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader mainLoader = SystemLoaderDemo.class.getClassLoader();
        ClassLoader markerLoader = Marker.class.getClassLoader();
        ClassLoader stringLoader = String.class.getClassLoader();
        ClassLoader context = Thread.currentThread().getContextClassLoader();

        System.out.println("system type: " + name(system));
        System.out.println("platform type: " + name(platform));
        System.out.println("main type: " + name(mainLoader));
        System.out.println("String: " + name(stringLoader));

        System.out.println("main == system: " + (mainLoader == system));
        System.out.println("Marker == main: " + (markerLoader == mainLoader));
        System.out.println("system parent == platform: "
                + (system.getParent() == platform));
        System.out.println("context == system: " + (context == system));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 SystemLoaderDemo.java
java SystemLoaderDemo
~~~

**Đầu ra minh họa trên OpenJDK/HotSpot 17 ở cấu hình launcher mặc định** (mục `type` có thể thay đổi theo JDK):

~~~text
system type: jdk.internal.loader.ClassLoaders$AppClassLoader
platform type: jdk.internal.loader.ClassLoaders$PlatformClassLoader
main type: jdk.internal.loader.ClassLoaders$AppClassLoader
String: <bootstrap>
main == system: true
Marker == main: true
system parent == platform: true
context == system: true
~~~

Giải thích:

- `main == system` so sánh **danh tính đối tượng ClassLoader**, phù hợp để xác nhận hai biến đang trỏ đến cùng loader.
- `Marker == main`: lớp thành viên `Marker` được javac phát sinh thành class riêng và thường do cùng loader định nghĩa như lớp chính trong cách chạy này.
- `String: <bootstrap>`: dấu hiệu lớp lõi được bootstrap loader nạp.
- `system parent == platform`: đúng trong cấu hình mặc định nêu trên. Nếu có custom system loader, parent trực tiếp có thể là application loader gốc; biểu thức có thể là false.
- `context == system` là giá trị thường gặp với luồng main từ launcher chuẩn. Một framework có thể thay TCCL theo từng thread hoặc theo từng thao tác.

**Đừng viết test tính đúng đắn nghiệp vụ yêu cầu class name bắt buộc bằng `jdk.internal.loader.ClassLoaders$AppClassLoader`.** Đây chỉ là công cụ quan sát môi trường.

## 6. Parent delegation hoạt động ra sao?

Với cách triển khai `ClassLoader.loadClass(String, boolean)` mặc định, luồng xử lý khái quát là:

~~~text
loadClass("com.example.Widget")
        |
        v
Class đã từng được loader này nạp chưa? -- Có --> trả Class<?>
        |
       Chưa
        v
Gọi parent.loadClass(...) nếu parent != null;
nếu không có parent, tìm bằng bootstrap loader
        |
   tìm được? ---- Có --> trả Class<?>
        |
       Không
        v
Gọi findClass(...) trên loader hiện tại
        |
        +-- tìm được --> có thể resolve --> trả Class<?>
        |
        +-- không --> ClassNotFoundException
~~~

Cần phân biệt:

- **Parent delegation** là chiến lược tìm lớp mặc định được `ClassLoader` cung cấp. Một custom loader có thể override `loadClass` để áp dụng chính sách khác (ví dụ child-first) trong những giới hạn hợp lệ.
- **Loading** không tự đồng nghĩa với **initialization**. `ClassLoader.loadClass("pkg.Type")` thường trả về `Class<?>` mà không chạy static initializer của Type chỉ vì lời gọi này.
- **Parent** là quan hệ giữa các loader, không phải quan hệ `extends` giữa hai loại dữ liệu.
- Loader gọi `loadClass` là **initiating loader** của yêu cầu. Loader thực sự định nghĩa một lớp cụ thể được gọi là **defining loader**. Class.getClassLoader() giúp biết defining loader; nó có thể là parent nếu quá trình ủy quyền thành công.
- Nếu parent đã định nghĩa `java.lang.String`, app loader thường chỉ nhận lại `Class` từ parent. Nó không cần tự định nghĩa bản `String` thứ hai.

Sơ đồ trên là cách hiểu cơ bản cho **class path và ClassLoader.loadClass**. Khi nạp lớp thuộc **named modules**, JVM áp dụng thêm các ánh xạ và quy tắc module; không nên khẳng định mọi class từ module đều trải qua một chuỗi parent-first tuyến tính đúng như sơ đồ.

## 7. System loader, class path và module path

### Class path

Chương trình truyền thống thường dùng:

~~~bash
javac -d out src/com/example/Main.java
java -cp out com.example.Main
~~~

Đây là **mẫu lệnh**, chỉ hoạt động khi file source thực sự tồn tại và package/class khớp như đường dẫn. `-cp` (hoặc `--class-path`) chỉ các thư mục/JAR mà ứng dụng cho phép tìm lớp. Những lớp tìm thấy trên application class path thường được định nghĩa bởi app/system loader mặc định và thuộc **unnamed module** của loader tương ứng.

Trong Java 17, nếu có một thư viện JAR trên class path, cách gọi loader của lớp đó vẫn phụ thuộc việc class có thể đã được parent nạp, có loader tùy chỉnh, hoặc quy tắc riêng của môi trường chạy. Không có bảo đảm “mọi thứ trên đường chạy đều phải do system loader định nghĩa”.

### Module path

Ứng dụng dạng JPMS có thể chạy với cấu hình module path:

~~~bash
java --module-path mods --module com.example.app/com.example.Main
~~~

Đây cũng là **mẫu lệnh**, yêu cầu module thích hợp đã được biên dịch và đặt trong thư mục `mods`. Với named modules, JDK có thể gắn các module với builtin loader và áp dụng quy tắc resolution, readability, exports và việc ánh xạ package/module. Vì vậy:

- **class path** và **module path** không phải cùng một danh sách;
- **system loader** không đồng nghĩa “bất kỳ JAR nào trên class path”;
- **module visibility** và **class loading** liên quan nhưng là những cơ chế khác nhau: load được một class theo tên không có nghĩa mã nguồn ở mọi module được phép truy cập public API của nó;
- `ClassLoader.getUnnamedModule()` cho biết unnamed module gắn với một loader, không phải toàn bộ named modules trong hệ thống.

Trong dự án sử dụng Gradle/Maven, class path thực tế do công cụ build hoặc launcher thiết lập. Lỗi “IDE chạy được nhưng `java -cp` không tìm thấy lớp” thường xuất phát từ class path/module path thiếu thành phần, không phải vì thiếu một lớp tên `AppClassLoader`.

## 8. System loader có thể được thay bằng custom loader

Java hỗ trợ cấu hình thuộc tính hệ thống tại **lúc khởi động JVM**:

~~~bash
java -Djava.system.class.loader=com.example.CustomSystemLoader -cp out com.example.Main
~~~

Đây là **minh họa cú pháp**, chưa thể chạy nếu chưa có lớp `com.example.CustomSystemLoader` tương thích trên đường nạp phù hợp. Một custom system loader hợp lệ phải đáp ứng yêu cầu API, trong đó có **constructor nhận một ClassLoader parent**, để JVM tạo instance. API khởi tạo system loader theo cơ chế riêng; cấu hình thiếu constructor, lớp không tìm thấy hoặc ném lỗi trong khởi tạo sẽ khiến quá trình thiết lập thất bại.

Ví dụ **phác họa thành phần tùy chỉnh**, không phải chương trình có thể chạy độc lập nếu chưa biên dịch/đặt đúng class path:

~~~java
package com.example;

public class CustomSystemLoader extends ClassLoader {
    public CustomSystemLoader(ClassLoader parent) {
        super(parent);
    }
}
~~~

Đoạn này chỉ kế thừa hành vi ủy quyền mặc định; nó chưa thêm cơ chế tìm bytecode của riêng mình. Mục đích là thể hiện **hình dạng constructor** mà system-loader customization yêu cầu, không phải hướng dẫn bắt buộc mọi ứng dụng phải dùng loader tùy chỉnh.

Trong JDK chuẩn, khi bật cấu hình này, loader được trả về từ `getSystemClassLoader()` là instance tùy chỉnh, còn loader ứng dụng tích hợp dùng để tải lớp tùy chỉnh thường đóng vai trò parent trong quá trình khởi tạo nó. Vì thế, so sánh:

~~~java
ClassLoader system = ClassLoader.getSystemClassLoader();
ClassLoader defining = Main.class.getClassLoader();
boolean same = (system == defining);
~~~

là **đoạn minh họa**; `same` không được mặc định là true trong mọi môi trường. Quan hệ và thứ tự nạp lớp phụ thuộc JDK launcher, custom loader và cách lớp Main được định nghĩa.

Không cố gắng “thay system class loader” sau khi JVM đã khởi chạy bằng việc gán một biến Java thông thường: API không cung cấp setter động cho vai trò đó. Muốn chọn system loader khác, cấu hình hợp lệ phải sẵn sàng tại thời điểm JVM thiết lập system class loader.

## 9. Ví dụ Java 17+ kiểm tra nạp lớp mà chưa khởi tạo

Tạo file **SystemLoadVsInitialize.java**:

~~~java
public class SystemLoadVsInitialize {
    static class Service {
        static {
            System.out.println("Service initialized");
        }

        static void hello() {
            System.out.println("Hello from Service");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader system = ClassLoader.getSystemClassLoader();

        Class<?> type = Class.forName(
                "SystemLoadVsInitialize$Service",
                false,
                system
        );

        System.out.println("Found: " + type.getSimpleName());
        System.out.println("Defined by system: "
                + (type.getClassLoader() == system));

        Service.hello();
    }
}
~~~

Biên dịch và chạy theo cách thông thường, không truyền `-Djava.system.class.loader`:

~~~bash
javac --release 17 SystemLoadVsInitialize.java
java SystemLoadVsInitialize
~~~

**Stdout dự kiến chính xác khi chạy trên JDK 17+ thông thường với class path mặc định:**

~~~text
Found: Service
Defined by system: true
Service initialized
Hello from Service
~~~

Cơ chế:

1. **Class.forName(binaryName, false, system)** yêu cầu tìm kiểu qua system loader với `initialize=false`. Đó không phải lời gọi constructor và không yêu cầu chạy mã `static { ... }`.
2. `type.getSimpleName()` chỉ đọc metadata class; không làm static initializer chạy trong ví dụ.
3. Khi `Service.hello()` được gọi (tương ứng `invokestatic`), JVM bảo đảm `Service` đã initialized nên in dòng **Service initialized** trước thân phương thức.
4. Chỉ thị truy cập class literal, lấy Class<?> hay nạp class và thực thi static initializer là các giai đoạn **khác nhau**.
5. Nếu custom system loader chủ động định nghĩa lớp theo chính sách riêng, `type.getClassLoader() == system` có thể đổi kết quả; do đó điều kiện `true` được ghi rõ cho cách chạy chuẩn của ví dụ.

Tên `SystemLoadVsInitialize$Service` là **binary name** của nested class; dấu chấm theo cú pháp source `SystemLoadVsInitialize.Service` không thay thế được dấu `$` trong yêu cầu `Class.forName(String,boolean,ClassLoader)`.

## 10. Thread Context Class Loader không mặc định là System Loader

Một số framework, cơ chế SPI, JNDI, service discovery hoặc runtime plugin cần tìm lớp/tài nguyên mà loader của chính thư viện không biết. JVM cung cấp **thread context class loader** (TCCL) như một tham chiếu ngữ cảnh cho thread:

~~~java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
ClassLoader system = ClassLoader.getSystemClassLoader();
~~~

Đoạn này là mã minh họa. Nhiều launcher đặt TCCL ban đầu của main thread bằng system loader, nhưng:

~~~java
thread.setContextClassLoader(customLoader);
~~~

có thể thay nó cho thread hiện tại (nếu quy tắc bảo mật môi trường cho phép), mà **không thay đổi đối tượng mà `ClassLoader.getSystemClassLoader()` trả về**. Thread khác có thể kế thừa TCCL của thread tạo nó hoặc được framework cấu hình lại.

Khi một thư viện hỗ trợ SPI hay plugin, việc cố lấy mọi class bằng system loader có thể bỏ sót các class chỉ có ở loader riêng của ứng dụng hoặc plugin. Nếu API khuyến nghị dùng context loader, nên tuân thủ hợp đồng API và bảo tồn/khôi phục TCCL khi tạm thay:

~~~java
ClassLoader old = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(customLoader);
    // Gọi API cần loader theo ngữ cảnh của plugin.
} finally {
    Thread.currentThread().setContextClassLoader(old);
}
~~~

Đây là **đoạn minh họa**, giả sử `customLoader` đã được tạo và luồng đang giữ quyền chỉnh ngữ cảnh. Khôi phục TCCL là quan trọng trong thread pool: các tác vụ sau có thể dùng lại cùng thread.

## 11. System resource và quy tắc tên tài nguyên

Với class loader, tên tài nguyên thường dùng dấu **/** phân tách thư mục tương đối từ gốc class path/module resource, không dùng tên binary có dấu chấm:

~~~java
ClassLoader system = ClassLoader.getSystemClassLoader();

java.net.URL first = system.getResource("config/app.properties");
java.net.URL viaStatic = ClassLoader.getSystemResource("config/app.properties");
~~~

Đây là **đoạn minh họa**, phụ thuộc việc `config/app.properties` thực sự nằm trong đường tìm tài nguyên của system loader. Khi system là loader được API sử dụng, hai cách tìm đều mô tả đường tra cứu tài nguyên hệ thống, nhưng các chi tiết về module/resource visibility còn phụ thuộc Java runtime.

So sánh:

| API | Quy tắc tên tài nguyên cần chú ý |
| --- | --- |
| `ClassLoader.getResource("pkg/file.txt")` | Tên đường dẫn từ gốc; thông thường **không có dấu /** ở đầu |
| `SomeClass.class.getResource("file.txt")` | Nếu không bắt đầu bằng /, tìm tương đối với package của SomeClass |
| `SomeClass.class.getResource("/pkg/file.txt")` | Nếu bắt đầu bằng /, tìm theo đường dẫn tuyệt đối tính từ gốc tìm kiếm của Class resource |
| `ClassLoader.getSystemResource("pkg/file.txt")` | Yêu cầu tìm tài nguyên qua system loader |

Đừng nhầm **file .class** với **tên binary**: lớp `demo.Widget` có resource path thông thường là `demo/Widget.class`, còn `Class.forName` dùng tên binary `demo.Widget`.

Khi có nhiều JAR chứa tài nguyên cùng tên, kết quả `getResource` có thể phụ thuộc đường tìm và chính sách của loader. `getResources` giúp liệt kê nhiều ứng viên; không dựa vào thứ tự duy nhất như một hợp đồng nghiệp vụ khi đặc tả không bảo đảm.

## 12. Danh tính lớp phụ thuộc cả tên nhị phân và defining loader

Trong Java, hai file có bytecode biểu diễn cùng class `com.example.Plugin` không nhất thiết tạo **một kiểu runtime**:

~~~text
Loader A --defines--> com.example.Plugin  --> Class object A
Loader B --defines--> com.example.Plugin  --> Class object B
~~~

Khi A và B thực sự **tự định nghĩa** cùng một tên nhị phân, JVM coi đây là **hai loại runtime khác nhau**. Một đối tượng kiểu A không thể tự động cast sang kiểu B chỉ vì tên package và tên class giống nhau; trường hợp không tương thích có thể dẫn đến `ClassCastException` hoặc lỗi liên kết tương ứng.

Từ đó suy ra:

- `Class.getName()` giống nhau **chưa đủ** để xác nhận cùng kiểu.
- `Class.getClassLoader()` trả về defining loader, giúp phân biệt.
- Parent-first delegation thường làm hai loader con cùng nhận một `Class` do parent định nghĩa, hạn chế việc tạo nhiều phiên bản của một API cốt lõi.
- Plugin loader đôi khi cố ý định nghĩa class riêng để cô lập dependency, nhưng cần thống nhất những interface/API mà plugin chia sẻ với host.
- Trạng thái khởi tạo static cũng gắn với **mỗi định nghĩa lớp runtime**, không chỉ chuỗi tên. Hai defining loader khác nhau có thể có hai bộ static state độc lập cho cùng binary name.

System/application loader thường là **một phần trong hệ thống danh tính lớp**, không phải nguồn gốc duy nhất của tất cả class được chạy trên JVM.

## 13. Xử lý ClassNotFoundException, NoClassDefFoundError và vấn đề loader

### ClassNotFoundException

Khi gọi:

~~~java
ClassLoader system = ClassLoader.getSystemClassLoader();
Class<?> plugin = system.loadClass("com.example.Plugin");
~~~

đây là **đoạn minh họa**: nếu plugin chỉ có trong một plugin loader khác, system loader có thể báo `ClassNotFoundException`. Cách xử lý là dùng **loader có khả năng tìm thấy lớp**, thiết lập đường tìm phù hợp, hoặc dùng API framework hỗ trợ TCCL; không mặc định sửa bằng cách copy JAR không kiểm soát sang nhiều nơi.

### NoClassDefFoundError

JVM có thể ném `NoClassDefFoundError` khi một lớp từng có sẵn lúc biên dịch nhưng không thể có được định nghĩa tương ứng tại runtime hoặc khi quá trình initialization trước đó đã thất bại (“Could not initialize class ...”). Cần xem **cause và thông điệp chính xác** để phân biệt thiếu dependency runtime, dependency transitively missing và lỗi static initializer.

### ClassCastException giữa các loader

Hai class cùng binary name nhưng do hai loader khác nhau định nghĩa có thể không cast được. Khi debug, nên in:

~~~java
static void describe(Class<?> type) {
    System.out.println(type.getName());
    System.out.println(type.getClassLoader());
    System.out.println(type.getModule());
}
~~~

Đây là **đoạn minh họa trong một lớp Java**, không phải file chạy độc lập. Để tránh nhầm, so sánh bằng `type1 == type2` khi cả hai đã là `Class<?>`, xem defining loader và cấu hình parent delegation. Trong môi trường JPMS cũng nên kiểm tra module và phạm vi exports/opens của package.

## 14. Khi nào dùng loader nào?

| Nhu cầu | Loader/API thích hợp và lý do |
| --- | --- |
| Quan sát system loader của JVM | `ClassLoader.getSystemClassLoader()` |
| Quan sát platform loader | `ClassLoader.getPlatformClassLoader()` |
| Biết loader thực sự định nghĩa một kiểu | `SomeType.class.getClassLoader()` |
| Tìm tài nguyên từ cùng package với lớp thư viện | `SomeType.class.getResource(...)` (xem quy tắc đường dẫn) |
| Tìm class ở cùng miền nạp với thư viện/đối tượng | Loader của class đó khi phù hợp với API |
| Framework SPI/plugin yêu cầu TCCL | `Thread.currentThread().getContextClassLoader()` theo hợp đồng framework |
| Tải class tùy biến mà hệ thống không biết | Loader riêng có cấu hình parent và cơ chế `findClass` phù hợp |
| Nạp class theo binary name và kiểm soát initialization | `Class.forName(binaryName, initialize, loader)` |
| Đọc resource thuộc system loader | `ClassLoader.getSystemResource(...)` / `getSystemResourceAsStream(...)` |

Sự lựa chọn loader phụ thuộc **lớp/tài nguyên thuộc phạm vi nào**, không chỉ phụ thuộc việc code được chạy từ `main`, từ server hay từ một JAR.

## 15. Lỗi thường gặp và cách tránh

1. **Khẳng định system loader luôn là `AppClassLoader`.** Trong cấu hình chuẩn hai vai trò thường trùng nhau; hệ thống cho phép custom system loader. Kiểm tra bằng API.
2. **Ép kiểu sang `jdk.internal.loader.ClassLoaders$AppClassLoader`.** Đây là triển khai nội bộ. Chỉ làm việc với API công khai `java.lang.ClassLoader`.
3. **Nghĩ system loader nạp mọi lớp của JVM.** Lớp lõi thường do bootstrap loader, một số lớp nền tảng do platform loader và plugin có thể do custom loader định nghĩa.
4. **Nghĩ `getSystemClassLoader() == Thread.currentThread().getContextClassLoader()` luôn true.** Framework/thread pool có thể thay TCCL.
5. **Cho rằng parent của loader là superclass của các lớp nó định nghĩa.** Parent delegation là cơ chế giữa những loader, độc lập với kế thừa kiểu Java.
6. **Dùng `getName()` của loader như một chuẩn nghiệp vụ.** Tên lớp triển khai có thể thay đổi giữa các JVM/JDK.
7. **Xem `loadClass` đồng nghĩa khởi tạo static.** Loading/linking và initialization là các giai đoạn khác nhau.
8. **Truyền tên canonical của nested class vào `loadClass`.** Với nested member class, thường cần binary name như `pkg.Outer$Inner`.
9. **Bỏ qua JPMS.** Named modules có các quy tắc tìm lớp/khả năng truy cập riêng; class path và module path không phải cùng nguồn.
10. **Thay đổi TCCL rồi không khôi phục.** Thread pool có thể tái sử dụng thread, khiến tác vụ sau tìm lớp/tài nguyên sai ngữ cảnh.
11. **Thấy `NoClassDefFoundError` và kết luận chắc chắn thiếu file class.** Lỗi initialization trước đó cũng có thể khiến lớp trở thành erroneous.
12. **Cho rằng hai lớp cùng fully-qualified name chắc chắn cast được.** Cần tính tới defining loader và class identity.

## 16. Quy trình debug ngắn cho một lỗi class loading

Khi một lớp/tài nguyên tìm được ở IDE nhưng thất bại khi chạy thực tế:

1. In `ClassLoader.getSystemClassLoader()`, `ClassLoader.getPlatformClassLoader()`, `Thread.currentThread().getContextClassLoader()`, và `ProblemType.class.getClassLoader()` nếu loại đã load được.
2. Ghi cách launch: `-cp` / `--class-path`, `--module-path`, `--module`, các VM arguments, và có `-Djava.system.class.loader` hay không.
3. Kiểm tra **binary name** có đúng package và dấu `$` cho nested class không; tài nguyên có đúng đường dẫn `/` không.
4. Xác định nơi JAR/class được cung cấp: class path, module path, plugin directory hay custom loader.
5. Kiểm tra parent delegation và TCCL mà thư viện thực sự sử dụng.
6. Nếu lớp được load nhưng không truy cập được, kiểm tra exports/opens/module access, lỗi liên kết hoặc initialization failure. Không gom mọi sự cố vào “class loader không tìm thấy”.
7. Khi nghi nhiều phiên bản của class, in **tên + defining loader + module** và so sánh `Class<?>` thực tế.

Trong JDK hiện đại, tùy bản Java có thể bật log JVM với `-Xlog:class+load=info` để quan sát tên lớp và nguồn nạp. Thông tin hiển thị phụ thuộc JVM và tùy chọn logging; kết hợp nó với reflection để hiểu **đối tượng loader cụ thể** thay vì chỉ suy từ đường dẫn file.

## 17. Tự kiểm tra

**Câu 1.** Trong ứng dụng Java 17 chạy chuẩn trên class path, `ClassLoader.getSystemClassLoader()` thường là loader nào?  
**Đáp án:** application class loader tích hợp, thường có tên triển khai `AppClassLoader`. Đây là hành vi phổ biến của JDK, không phải bảo đảm về tên class nội bộ.

**Câu 2.** `String.class.getClassLoader() == null` có nghĩa gì trong JDK chuẩn?  
**Đáp án:** `String` được bootstrap loader định nghĩa; null là cách API đại diện bootstrap loader, không phải không có loader.

**Câu 3.** Nếu thread có TCCL trỏ tới plugin loader, `getSystemClassLoader()` có đổi theo không?  
**Đáp án:** Không. Thay TCCL của thread không thay vai trò system loader toàn JVM.

**Câu 4.** Một custom loader được đặt làm system loader có đồng nghĩa mọi class của ứng dụng sẽ luôn được nó định nghĩa?  
**Đáp án:** Không. Việc định nghĩa class phụ thuộc cách launcher tìm class, parent delegation và chính sách loader; nhiều class có thể do parent định nghĩa.

**Câu 5.** Khi `loadClass("com.example.Plugin")` thành công, có chắc static block của Plugin đã chạy?  
**Đáp án:** Không. Loading không tự động là initialization; cần xét điểm kích hoạt initialization.

**Câu 6.** Hai plugin loader độc lập định nghĩa `com.example.Api` với bytecode giống nhau có tạo cùng runtime class không?  
**Đáp án:** Không. Danh tính kiểu gồm binary name và defining loader; phải chia sẻ một định nghĩa chung nếu cần dùng chung API runtime.

**Câu 7.** Cần đọc `config/service.properties` bằng system loader. Dùng dấu chấm theo package hay dấu / theo đường dẫn?  
**Đáp án:** Dùng `config/service.properties`, không dùng `config.service.properties`, và thường không đặt dấu / đầu tên đối với ClassLoader.getResource.

## Tài liệu chính thức để đối chiếu

- Java SE 17 API — **ClassLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API — **Class**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 API — **Thread** (context class loader): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html
- Java SE 17 API — **Module**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java Virtual Machine Specification, §5.3 **Creation of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- Java Virtual Machine Specification, §5.5 **Initialization**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- Java Language Specification, §12.2 **Loading of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-004">Quan hệ cha và phạm vi lớp mà mỗi loader phụ trách</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ cha và phạm vi lớp mà mỗi loader phụ trách

Trong Java, **class loader** nhận yêu cầu tìm một kiểu theo tên nhị phân, có thể chuyển yêu cầu lên **parent loader** hoặc tự tìm bytecode để định nghĩa kiểu. Trên Java 17+, hệ thống thường có ba tầng loader tích hợp: **bootstrap**, **platform** và **application/system**. Việc hiểu quan hệ cha và phạm vi trách nhiệm của từng tầng giúp giải thích vì sao `java.lang.String` và class tự viết có loader khác nhau, tại sao loader con thường dùng được lớp do loader cha định nghĩa, và vì sao hai class trùng tên đôi khi không ép kiểu được cho nhau.

Bài học thuộc nhóm **Loader tích hợp** của Java core – classloader. Trọng tâm là: xác định **ai là parent**, **ai thực sự định nghĩa class**, **mỗi loader tìm lớp ở đâu**, các ngoại lệ của mô hình delegation và cách kiểm tra bằng Java 17+.

## 1. Ba loader tích hợp và sơ đồ quan hệ cha

Trong cấu hình khởi chạy Java SE thông thường, sơ đồ parent delegation có thể hình dung như sau:

```text
Bootstrap class loader  [tích hợp trong JVM; thường biểu diễn bằng null]
          ↑ parent của
Platform class loader   [ClassLoader.getPlatformClassLoader()]
          ↑ parent của
Application/system      [ClassLoader.getSystemClassLoader()]
          ↑ parent của
Custom class loader     [nếu bạn tạo với parent = system loader]
```

Mũi tên ở đây biểu thị **quan hệ parent để hỏi tìm class**, không phải quan hệ kế thừa `extends`, không biểu thị loader nào tạo instance loader khác và cũng không khẳng định mọi yêu cầu luôn đi đúng tuyến này.

| Loader | Cách lấy/nhận biết | Trách nhiệm điển hình | Parent |
| --- | --- | --- | --- |
| **Bootstrap** | Thường quan sát là `null` qua `SomeCoreClass.class.getClassLoader()`; không có một instance `ClassLoader` Java thông thường để giữ | Định nghĩa các lớp trong một số module nền tảng quan trọng, ví dụ phần cốt lõi `java.base` | Không có |
| **Platform** | `ClassLoader.getPlatformClassLoader()` | Các lớp nền tảng Java SE và JDK được phân cho platform loader hoặc loader tổ tiên | Trong cấu hình chuẩn, chuyển yêu cầu tới bootstrap |
| **Application / system** | `ClassLoader.getSystemClassLoader()` | Lớp ứng dụng trên application class path, application module path và các lớp/công cụ JDK thích hợp | Platform (hoặc một loader tổ tiên tương đương trong cấu hình tích hợp) |
| **Custom loader** | Instance do chương trình/framework tạo, ví dụ `new URLClassLoader(urls, parent)` | Plugin, mã tải động, vùng namespace riêng hoặc nguồn bytecode khác | Do ứng dụng chọn; mặc định constructor `ClassLoader()` lấy system loader làm parent |

**Bootstrap không đồng nghĩa platform.** Từ Java 9, hệ thống module thay đổi cách phân phối các lớp JDK: bootstrap xác định một số module nền tảng, trong khi platform và system định nghĩa/khởi đầu nhiều class của các module khác. Tránh ghi nhớ quy tắc cũ “bootstrap tải tất cả `java.*`, extension tải `javax.*`, app tải phần còn lại”: tên package không phải bảng phân quyền chính xác.

Trong Java 8 và cũ hơn thường gặp thuật ngữ **extension class loader** và `jre/lib/ext`. Từ Java 9 trở đi, extension mechanism đã bị loại bỏ, thay bằng **platform class loader** trong phân chia loader tích hợp. Không dùng `ExtClassLoader` hay `rt.jar` như giả định kiến trúc của Java 17+.

## 2. Parent loader là gì và có phải là nơi chứa class không?

Mỗi `ClassLoader` có thể tham chiếu tới một **parent loader** để ủy quyền yêu cầu nạp lớp. API `getParent()` trả quan hệ delegation được cấu hình, không phải “loader đã tạo ra object này”. Loader con có thể hỏi cha trước để tránh định nghĩa lại các lớp cha đã cung cấp.

Ba khái niệm cần tách bạch:

1. **Requesting loader:** loader mà ứng dụng hoặc JVM gọi để yêu cầu class, ví dụ `child.loadClass("java.lang.String")`.
2. **Defining loader:** loader thực sự tạo định nghĩa class bằng dữ liệu class tương ứng (thường qua `defineClass`), hoặc bootstrap do JVM phụ trách. Xem bằng `SomeType.class.getClassLoader()`.
3. **Initiating loader:** loader mà JVM ghi nhận đã khởi đầu quá trình loading cho tên class đó; loader con có thể là initiating loader dù class được parent định nghĩa.

Nếu gọi `child.loadClass("java.lang.String")` trong mô hình parent-first, parent có thể trả lại cùng `Class<String>` do bootstrap định nghĩa. **Kết quả trả về không đồng nghĩa child đã định nghĩa `String`.** Gọi `getClassLoader()` trên class trả về mới cho biết defining loader trong biểu diễn Java.

Một lớp Java được nhận diện về mặt runtime bằng **tên nhị phân + defining class loader**, chứ không phải chỉ tên. Hai loader khác nhau có thể định nghĩa hai class cùng tên `com.example.Plugin` nhưng đó là **hai kiểu độc lập**; cast có thể thất bại. Trái lại, nếu hai loader con cùng delegate đến một định nghĩa do parent cung cấp, cả hai có thể nhận **cùng đối tượng `Class<?>`**.

### Parent của một loader có thể là `null`

`ClassLoader.getParent() == null` thường biểu diễn parent bootstrap vì bootstrap không phải instance `ClassLoader` thông thường. Điều đó **không có nghĩa** loader đang xét chính là bootstrap. Ví dụ platform loader trong cấu hình chuẩn có thể có `getParent() == null`, nhưng bản thân platform loader vẫn là object `ClassLoader` riêng.

Khi tự tạo `new URLClassLoader(urls, null)`, `null` có nghĩa delegate tới bootstrap, **không phải vô hiệu hóa toàn bộ delegation**. Lưu ý từ JDK 9: bootstrap không cung cấp tất cả platform classes, nên custom loader có parent `null` có thể không thấy các API vốn được platform loader định nghĩa; nếu plugin cần các API ấy, cân nhắc đặt parent là `ClassLoader.getPlatformClassLoader()` hoặc system loader theo kiến trúc mong muốn.

## 3. Phạm vi thực tế của từng loader

### 3.1 Bootstrap: nền tảng tối thiểu để JVM vận hành

Bootstrap do JVM cung cấp. Nó định nghĩa các class cốt lõi như `java.lang.Object`, `java.lang.String` và `java.lang.Class` trong các module nền tảng liên quan. Với các kiểu thông dụng này:

```java
System.out.println(String.class.getClassLoader() == null);
System.out.println(Object.class.getClassLoader() == null);
```

Đây là **đoạn minh họa hai lệnh**, cần đặt trong method chạy được. Ở Java SE 17 thông thường, cả hai dòng in `true`. Đừng viết `ClassLoader bootstrap = String.class.getClassLoader(); bootstrap.loadClass(...)`: biến `bootstrap` có giá trị `null`, gọi method sẽ gây `NullPointerException`.

Giới hạn cần hiểu:

- Bootstrap không mặc nhiên nạp từng file người dùng hay mọi class trong JDK.
- Một custom loader có parent bootstrap không chắc truy cập được tất cả platform APIs.
- Class được bootstrap định nghĩa thường có `Class.getClassLoader() == null`; cách biểu diễn này là một quy ước API, không cho phép thao tác trực tiếp với một đối tượng bootstrap.

### 3.2 Platform: phần nền tảng rộng hơn lớp bootstrap

Platform loader chịu trách nhiệm các **platform classes** mà runtime gán cho nó hoặc cho các loader tổ tiên. Platform classes có thể là Java SE API, class triển khai hoặc các lớp JDK đặc thù. API trả loader này là:

```java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
```

Platform không chỉ “tải tất cả thư viện ngoại vi”. Nhiều thư viện của người dùng nằm trên application class path và được application loader định nghĩa. Cũng không được suy định nghĩa chỉ từ tiền tố `java.`, `javax.` hay `jdk.`: hệ thống module và cấu hình runtime quyết định cách phân phối.

**Ngoại lệ delegation quan trọng:** tài liệu `ClassLoader` cho phép platform loader trong một số tình huống **upgraded modules** phải delegate sang loader khác, kể cả application loader, dù application không phải ancestor của platform. Vì thế sơ đồ parent ở đầu bài là **mô hình mặc định cho quan hệ cha**, không phải một định luật cứng rằng mọi lần tra cứu chỉ đi từ con lên cha mà không bao giờ có cạnh khác.

### 3.3 Application/system: điểm tựa mặc định của ứng dụng

`ClassLoader.getSystemClassLoader()` thường trả loader dùng để khởi động ứng dụng. Với cấu hình launcher mặc định, nó định nghĩa lớp từ **application class path** (`-cp`/`--class-path`) và những lớp/module phù hợp từ **application module path**. Trên JDK built-in, system loader thường có tên `"app"`.

Ví dụ lệnh chạy class path:

```bash
javac --release 17 Hello.java
java -cp . Hello
```

Nếu `Hello` là class đơn giản trong thư mục hiện tại và không có system loader tùy biến, `Hello.class.getClassLoader()` thường chính là `ClassLoader.getSystemClassLoader()`.

Phạm vi này có giới hạn:

- Một class không xuất hiện trên class path/module path thích hợp thường không được app loader tìm thấy.
- **Parent không biết tự động class do child định nghĩa**. Nếu plugin class chỉ do plugin loader định nghĩa, system loader không tự tìm được nó.
- System loader có thể được tùy biến qua thuộc tính `java.system.class.loader`. Vì vậy không khẳng định tên class implementation, chuỗi `toString()` hay thậm chí quan hệ trực tiếp với platform luôn cố định trong mọi ứng dụng.
- Class path và module path không phải một khối tương đương: named modules còn tuân theo module readability, exports và opens.

## 4. Chạy thử Java 17+: quan hệ cha và loader định nghĩa

Tạo file `LoaderRolesDemo.java`:

```java
public class LoaderRolesDemo {
    static class Sample {
    }

    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader system = ClassLoader.getSystemClassLoader();
        ClassLoader sampleLoader = Sample.class.getClassLoader();

        System.out.println("String by bootstrap: "
                + (String.class.getClassLoader() == null));
        System.out.println("Platform parent bootstrap: "
                + (platform.getParent() == null));
        System.out.println("System parent platform: "
                + (system.getParent() == platform));
        System.out.println("Sample defined by system: "
                + (sampleLoader == system));

        Class<?> viaSystem = system.loadClass("java.lang.String");
        System.out.println("Same String class: "
                + (viaSystem == String.class));

        Class<?> viaPlatform = platform.loadClass("java.lang.String");
        System.out.println("Platform delegates String: "
                + (viaPlatform == String.class));
    }
}
```

Biên dịch/chạy bằng JDK 17+ trên class path, không đặt `java.system.class.loader`:

```bash
javac --release 17 LoaderRolesDemo.java
java -cp . LoaderRolesDemo
```

Trên JDK chuẩn với ba loader built-in và launcher mặc định, stdout dự kiến:

```text
String by bootstrap: true
Platform parent bootstrap: true
System parent platform: true
Sample defined by system: true
Same String class: true
Platform delegates String: true
```

Giải thích:

- `String` được bootstrap định nghĩa nên `getClassLoader()` trả `null`.
- `platform.getParent() == null` **không** có nghĩa platform là bootstrap; nó biểu diễn parent delegation tới bootstrap.
- `system.getParent() == platform` mô tả cạnh parent chuẩn giữa app và platform.
- `Sample` là một class được compile cùng chương trình và load từ class path hiện tại, vì vậy app/system là defining loader trong cấu hình nêu trên.
- Hai lời gọi `loadClass("java.lang.String")` vào system/platform cùng trả đối tượng `String.class`, vì các loader này delegate để dùng định nghĩa sẵn từ bootstrap.

Nếu chạy với một system loader tùy biến, sửa class path/module path hoặc đổi launcher/runtime, một số dòng đối chiếu có thể thay đổi. Điều chính yếu là kiểm tra **quan hệ và danh tính** bằng API, tránh học thuộc tên class implementation bên trong JDK.

### In chuỗi parent để chẩn đoán

Một đoạn util (đặt trong method Java) có thể duyệt từ system lên parent:

```java
ClassLoader loader = ClassLoader.getSystemClassLoader();
while (loader != null) {
    System.out.println(loader.getName());
    loader = loader.getParent();
}
System.out.println("<bootstrap represented as null>");
```

Đây là **đoạn mã minh họa chèn vào method**, không phải một file chạy độc lập. Trên JDK tích hợp phổ biến, tên `app` và `platform` có thể xuất hiện, nhưng tên của custom loader có thể `null`; không suy đoán quyền hạn/chỗ lưu class chỉ dựa trên tên. Vòng lặp đi theo **parent chain** được cấu hình; nó không liệt kê mọi mối liên hệ module hay delegation phi cây.

## 5. Parent-first delegation hoạt động như thế nào?

`ClassLoader.loadClass(String)` gọi quá trình tìm class theo thuật toán mặc định của `loadClass(String, boolean)`:

1. **Đã tải chưa?** Dùng `findLoadedClass(name)` trong ngữ cảnh loader để tránh xử lý lại.
2. **Hỏi parent trước.** Gọi `parent.loadClass(name, false)`; nếu parent là `null`, dùng cơ chế bootstrap để tìm.
3. **Parent không tìm được:** loader hiện tại gọi `findClass(name)` để tự định nghĩa/lấy class từ nguồn nó quản lý.
4. **Nếu có yêu cầu resolve:** JVM liên kết qua `resolveClass`; bản thân `loadClass` không mặc nhiên khởi tạo static initializer.

Sơ đồ:

```text
child.loadClass("demo.Feature")
       |
       v
Đã load trước đó? ----- có ------> trả Class
       |
      chưa
       v
Hỏi parent.loadClass(...) --- tìm thấy --> trả Class của parent
       |
     không thấy
       v
child.findClass(...) ------- tìm thấy --> child định nghĩa Class
       |
   không thấy
       v
ClassNotFoundException
```

Đây là **default parent-first**, không phải yêu cầu tuyệt đối cho mọi loader tùy biến. Subclass có thể override `loadClass` để thực hiện chính sách khác, nhưng phải tuân thủ các ràng buộc JVM và xử lý các class quan trọng an toàn. Theo khuyến nghị API, đa số custom loader nên override `findClass`, giữ thuật toán `loadClass` mặc định khi không có lý do mạnh để thay.

Parent-first giúp tái sử dụng class nền tảng, tránh vô ý định nghĩa bản sao không tương thích và cho phép nhiều child dùng chung kiểu do parent cung cấp. Tuy nhiên nó không tự giải quyết version conflict: nếu parent đã thấy một dependency phiên bản cũ, child sẽ được trả class của parent theo thuật toán mặc định.

### Ví dụ chạy được: yêu cầu qua child nhưng định nghĩa ở parent

Tạo file `DelegationDemo.java`:

```java
public class DelegationDemo {
    static class ChildLoader extends ClassLoader {
        ChildLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            System.out.println("findClass: " + name);
            throw new ClassNotFoundException(name);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader child = new ChildLoader(
                ClassLoader.getSystemClassLoader());

        Class<?> core = child.loadClass("java.lang.String");
        System.out.println("String shared: "
                + (core == String.class));

        Class<?> local = child.loadClass("DelegationDemo");
        System.out.println("App class shared: "
                + (local == DelegationDemo.class));

        try {
            child.loadClass("demo.Missing");
        } catch (ClassNotFoundException expected) {
            System.out.println("Missing class");
        }
    }
}
```

```bash
javac --release 17 DelegationDemo.java
java -cp . DelegationDemo
```

Stdout:

```text
String shared: true
App class shared: true
findClass: demo.Missing
Missing class
```

Điểm mấu chốt:

- Child yêu cầu `String` nhưng bootstrap cung cấp định nghĩa: `findClass` của child **không được gọi**.
- Child yêu cầu `DelegationDemo` nhưng app/system loader đã định nghĩa class đó: child nhận **cùng `Class<?>`**.
- Với `demo.Missing`, parent không tìm thấy, child mới thử `findClass` rồi báo `ClassNotFoundException`.
- Ví dụ cố ý không tạo bytecode cho class thiếu; mục tiêu là quan sát **thứ tự delegation**, không mô phỏng plugin loader hoàn chỉnh.

Nếu muốn child tự định nghĩa class, phải cung cấp bytecode hợp lệ và gọi `defineClass(...)` trong `findClass`; cần bảo đảm quan hệ loader, package, module, visibility và vòng đời phù hợp. Không được tùy ý định nghĩa các class trong namespace cấm như `java.*`, và không nên phá parent-first chỉ để né dependency clash mà chưa phân tích danh tính kiểu.

## 6. Quan hệ loader không phải quan hệ kế thừa class Java

Nhầm lẫn thường gặp:

- `class P extends ClassLoader` chỉ nói **P là một loại loader** trong hệ thống class của Java.
- `new P(parent)` chỉ nói **instance P này sẽ hỏi parent nào** khi delegate, nếu code constructor và override tuân theo tham số đó.
- `MyService extends BaseService` là quan hệ kế thừa của **hai class ứng dụng**. `MyService` và `BaseService` có thể được định nghĩa bởi loader khác nhau, miễn JVM có thể resolve và áp dụng đúng các ràng buộc.
- Loader của một class không nhất thiết là loader của mọi class mà nó tham chiếu. JVM có thể dùng loader của class đang tham chiếu để yêu cầu kiểu phụ thuộc rồi nhận về định nghĩa từ parent.

Một parent loader không tự nhiên có quyền tìm thấy class mà child đã định nghĩa. Quan hệ delegation thông thường là **child hỏi parent trước**, không phải mọi loader đều chia sẻ chung một kho class vô hạn. Nếu một framework cần nạp plugin do child định nghĩa, framework phải biết cách nhận được plugin loader hoặc giao cho một cơ chế discovery phù hợp.

## 7. Phạm vi lớp, module và class path là ba khái niệm khác nhau

Trên Java 17, loader tích hợp hoạt động cùng **Java Platform Module System (JPMS)**:

- **Class path:** mã chạy theo cơ chế class path thường thuộc **unnamed module** của loader tương ứng. Application loader là nơi phổ biến định nghĩa class ứng dụng từ `-cp`.
- **Module path:** các named modules được runtime xác định, resolve và gắn với loader theo cơ chế module system; không suy ra rằng “cứ named module là platform loader” hoặc “cứ module người dùng là một loader riêng”.
- **Module readability:** module có thể đọc module nào liên quan tới phụ thuộc tĩnh/động của module.
- **Exports/opens:** quyết định truy cập API và reflection tới package theo quy tắc module. Class có thể **được nạp** mà code khác vẫn bị chặn truy cập thành viên do module encapsulation.
- **Resource lookup:** `getResource` hoặc `getResources` còn bị ảnh hưởng bởi path, thứ tự tìm và cơ chế đóng gói/module. Việc biết một class load được không đảm bảo tìm được mọi resource cùng package theo đúng cách cũ.

**Phạm vi loader trả lời câu hỏi “bộ định nghĩa class nào được thấy/khởi đầu theo yêu cầu?”**; **module boundary trả lời thêm “code nào được quyền đọc, truy cập và phản chiếu?”**. Hai kiểm tra có thể liên quan nhưng không thay thế nhau.

Một class có thể thuộc một named module nhưng lại được platform hoặc app loader định nghĩa tùy module và runtime. Khi cần biết thực tế, kiểm tra trực tiếp:

```java
Class<?> type = java.util.ArrayList.class;
System.out.println(type.getModule().getName());
System.out.println(type.getClassLoader());
```

Đây là **mã minh họa chèn trong một method**. `getModule().getName()` cho biết module của class; `getClassLoader()` cho biết defining loader. Không đoán loader bằng cách nhìn tên module.

### Khi nào chọn parent bootstrap, platform hay app cho custom loader?

| Mục tiêu | Parent thường cân nhắc | Đánh đổi |
| --- | --- | --- |
| Plugin cần API Java SE mở rộng và không muốn tự thấy toàn bộ app | **Platform loader** | Vẫn phải tổ chức API chung, module visibility và phụ thuộc bổ sung |
| Plugin cần interface/API do chính ứng dụng khai báo | **System loader** hoặc loader đã định nghĩa API chung | Giúp plugin nhận cùng định nghĩa interface; có thể bị ảnh hưởng bởi version của dependency ở parent |
| Loader cách ly tối đa, chỉ dựa một số class nền tảng bootstrap | Parent `null` | Không được bảo đảm thấy mọi platform class; chi phí quản lý phụ thuộc cao |
| Framework có hệ sinh thái loader riêng | Parent do framework hợp đồng hóa | Cần kiểm tra contract/framework vì parent-first/child-first có thể biến đổi |

Chọn parent dựa vào **những kiểu phải dùng chung**, nguồn class cần truy cập và policy xung đột phiên bản. Đừng dùng `null` chỉ vì muốn “bảo mật hơn” hoặc muốn làm loader nhẹ hơn; class loader isolation tự nó không phải security boundary đầy đủ.

## 8. Thread context class loader khác loader định nghĩa class hiện tại

Có một khái niệm khác dễ nhầm với parent: **thread context class loader (TCCL)**.

```java
ClassLoader context = Thread.currentThread()
        .getContextClassLoader();
ClassLoader defining = MyType.class.getClassLoader();
```

Đây là **đoạn minh họa**, giả định có `MyType`. `context` do thread giữ để framework/service discovery chọn nguồn class hoặc resource. `defining` là loader đã định nghĩa `MyType`. Chúng có thể khác nhau và TCCL **không tự thay đổi** defining loader của class đã tồn tại.

Trong `main` của ứng dụng khởi chạy bình thường, context loader thường là system loader, nhưng executor, app server hoặc framework có thể gán khác. Một pattern an toàn khi tạm thay TCCL là:

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Thực hiện thao tác discovery cần pluginLoader.
} finally {
    thread.setContextClassLoader(previous);
}
```

Đây là **đoạn minh họa**, giả định `pluginLoader` đã tồn tại. Khôi phục context trong `finally` để công việc sau của cùng thread không vô tình nhìn namespace plugin. Trong môi trường có thread pool, giữ TCCL cũ sau request rất dễ gây rò rỉ tham chiếu tới plugin loader hoặc nạp nhầm class.

TCCL có thể cho framework mã hóa ở loader “cao hơn” tìm provider do loader “thấp hơn” quản lý, nhưng chỉ khi framework **chủ động dùng TCCL**. Nó không đảo ngược toàn bộ cơ chế parent delegation của mọi lớp.

## 9. Cùng tên nhưng khác loader: vì sao có lỗi ép kiểu?

Giả sử hai plugin loader độc lập đều định nghĩa tên nhị phân `demo.api.Task`:

```text
system loader -> định nghĩa demo.api.Task (nếu có API chung)
     |
     +--> PluginLoader A -> định nghĩa demo.impl.Service
     |
     +--> PluginLoader B -> định nghĩa demo.impl.Service
```

Hai `demo.impl.Service` có thể cùng bytecode nhưng là hai class khác nhau vì **defining loader khác nhau**. Không được coi object của A là instance của class B chỉ vì tên giống. Muốn chia sẻ object qua ranh giới plugin, cần **API/interface chung** do loader dùng chung (thường parent) định nghĩa, để code cả hai phía resolve về **cùng Class object** của hợp đồng API.

Nếu mỗi plugin định nghĩa riêng cả interface `demo.api.Task` thì tên interface giống nhau vẫn là hai kiểu không tương thích. Trong log, thông báo `ClassCastException` có thể hiển thị hai tên lớp giống nhau nhưng thêm định danh loader/module khác nhau. Khi điều tra, hãy in:

```java
System.out.println(instance.getClass().getName());
System.out.println(instance.getClass().getClassLoader());
System.out.println(instance.getClass().getModule());
```

Đây là **đoạn minh họa**, với `instance` là object đang điều tra. Cần kiểm tra bằng identity của `Class<?>`/`ClassLoader`, thay vì chỉ so `getName()` hoặc dựa hoàn toàn vào `toString()`.

## 10. Debug loader nào đang nạp và định nghĩa class

Có ba hướng kiểm tra bổ trợ nhau:

**A. Đọc metadata tại runtime**

```java
static void inspect(Class<?> type) {
    System.out.println("class=" + type.getName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule().getName());
}
```

Đây là **method minh họa**, cần đặt trong class đang biên dịch. Nếu `getClassLoader()` trả `null`, đó là biểu diễn bootstrap của JVM. Nếu `getModule().getName()` là `null`, module không có tên (unnamed module).

**B. Xem dấu vết class loading trên HotSpot**

```bash
java -Xlog:class+load=info LoaderRolesDemo
```

Unified logging ở nhiều bản HotSpot in thông tin các class đã load và nguồn liên quan. Format, nguồn được báo và mức độ chi tiết phụ thuộc runtime; **log source không nhất thiết hiển thị đầy đủ defining loader**. Dùng nó để bổ trợ việc kiểm tra qua `Class.getClassLoader()` và cấu hình class path/module path. Trên runtime không hỗ trợ tùy chọn này, tham khảo flag logging tương đương của nhà cung cấp.

**C. Xem đường tìm class**

```bash
java -cp ".;libs/*" LoaderRolesDemo
```

Dấu chấm phẩy `;` trong ví dụ trên là **separator class path trên Windows**. Trên Linux/macOS dùng dấu hai chấm `:`: `java -cp ".:libs/*" LoaderRolesDemo`. Kiểm tra lỗi do JAR thiếu, trùng class hoặc version khác nhau trong class path là việc khác với việc đọc parent chain. Với named modules, xem thêm `--module-path`, `--add-reads`, `--add-exports` khi phù hợp để chẩn đoán quyền truy cập module; đừng sửa flag bừa bãi chỉ để làm lỗi biến mất.

### Phân biệt lỗi phổ biến

| Hiện tượng | Kiểm tra trước tiên |
| --- | --- |
| `ClassNotFoundException` từ `loadClass` hoặc `Class.forName` | Tên nhị phân, class path/module path thực tế, parent, custom `findClass`, source JAR |
| `NoClassDefFoundError` khi code tham chiếu class | Class/dependency runtime thiếu, lỗi linking hoặc một khởi tạo class trước đó đã thất bại; đọc `cause`/stack trace |
| `ClassCastException` giữa hai tên giống nhau | Có phải hai class do hai defining loaders khác nhau tạo? |
| `NoSuchMethodError` / `NoSuchFieldError` | Binary compatibility của dependency được loader thật sự trả về; tên/descriptor của method/field |
| `IllegalAccessError` / `InaccessibleObjectException` | Access level, package/module readability, exports/opens, reflective access |
| `ServiceLoader` không tìm thấy provider | Context loader được dùng, module service declarations, file `META-INF/services`/cấu hình module, provider visibility |
| Plugin unload rồi nhưng loader vẫn sống | Object/thread/TCCL/static cache/stack đang giữ tham chiếu tới class, instance hoặc loader của plugin |

Một vài lưu ý chẩn đoán:

- `ClassNotFoundException` là checked exception thường gặp với API nạp lớp chủ động; `NoClassDefFoundError` là một linkage error, không luôn đồng nghĩa file vật lý vắng mặt.
- `ClassLoader.loadClass(String)` không mặc nhiên **initialize** class; `Class.forName(String)` overload một tham số yêu cầu initialization. Đây là khác biệt vòng đời, không phải khác biệt parent tree.
- Nếu dependency nằm ở parent với phiên bản không phù hợp, child parent-first thường nhận bản ở parent. Cần xử lý kiến trúc dependency/version thay vì mong Java chọn “JAR gần hơn” theo cảm tính.
- Không dùng kết quả `getResources()` như danh sách chính xác tất cả class một loader có thể định nghĩa, vì resource lookup có quy tắc khác class loading và có module encapsulation.

## 11. Tình huống thiết kế và quyết định phù hợp

**Tình huống A – ứng dụng Java đơn giản:** mã của bạn và dependency trên `-cp`. System loader định nghĩa đa số class ứng dụng, còn JDK core class thường tới từ bootstrap/platform. Thường không cần tự tạo custom loader.

**Tình huống B – plugin cùng sử dụng `PluginApi`:** đặt `PluginApi` vào classpath/module của **parent chung**, còn implementation của từng plugin để loader plugin định nghĩa. Khi host gọi method qua `PluginApi`, các bên cùng dùng một định nghĩa interface. Cẩn thận nếu host cũng đóng gói bản sao API bên trong JAR plugin, đặc biệt khi child-first.

**Tình huống C – plugin cần các API JDK rộng hơn bootstrap nhưng không cần thấy toàn bộ ứng dụng:** platform loader có thể phù hợp làm parent; dùng kiểm tra thực tế với module/service mà plugin cần. Nếu plugin phải cast sang interface của host, cần giải quyết việc chia sẻ defining loader của interface.

**Tình huống D – application server/container:** container có thể cung cấp loader tùy biến, có policy child-first cho một số thư viện và parent-first cho nền tảng. Không áp dụng máy móc cây loader của `java -cp . Main` cho toàn hệ thống; kiểm tra tài liệu framework và runtime objects.

**Tình huống E – plugin framework dùng TCCL:** provider do plugin loader định nghĩa nhưng discovery code ở lớp cha. Framework có thể chọn context loader để tìm provider. Cần đặt và khôi phục context tại phạm vi tác vụ, nhất là thread pool.

## 12. Kiến thức cần tự kiểm tra

**Câu 1.** `ClassLoader.getSystemClassLoader().loadClass("java.lang.String")` trả về `String.class`. Có thể kết luận system loader định nghĩa `String` không?

**Đáp án:** Không. Trong cấu hình JDK chuẩn, system loader delegate qua platform tới bootstrap; defining loader của `String` được biểu diễn bằng `null`. Loader nhận yêu cầu khác loader định nghĩa class.

**Câu 2.** `ClassLoader.getPlatformClassLoader().getParent() == null` có nghĩa platform loader là bootstrap loader không?

**Đáp án:** Không. Platform vẫn là instance `ClassLoader`; `null` ở đây biểu diễn bootstrap trong quan hệ parent.

**Câu 3.** Vì sao custom loader parent `null` không chắc tìm được mọi lớp API Java SE?

**Đáp án:** Nó chỉ delegate tới bootstrap theo cơ chế chuẩn. Từ Java 9, nhiều platform classes được định nghĩa ngoài bootstrap, nên parent `null` không đảm bảo toàn bộ nền tảng hiện diện.

**Câu 4.** PluginLoader A và B đều tìm `java.lang.String`. Vì sao `Class<?>` có thể giống nhau? Nhưng hai `demo.impl.Service` lại không thể cast qua nhau?

**Đáp án:** Cùng delegate tới một định nghĩa `String` của bootstrap nên nhận cùng `Class<?>`. Còn mỗi plugin có thể tự định nghĩa `Service`, tạo hai kiểu riêng do khác defining loader.

**Câu 5.** Khi nào `findClass` trong custom loader mặc định được gọi?

**Đáp án:** Sau khi `findLoadedClass` không trả class sẵn có và parent không tìm thấy class được yêu cầu. Nếu parent tìm ra class, mặc định không gọi `findClass` của child.

**Câu 6.** `getParent()`, `getContextClassLoader()` và `SomeClass.class.getClassLoader()` trả lời ba câu hỏi nào?

**Đáp án:** `getParent()`: loader nào được hỏi như parent. `getContextClassLoader()`: context loader hiện được thread giữ cho các API dùng nó. `Class.getClassLoader()`: defining loader của class (bootstrap có thể là `null`).

**Câu 7.** Chỉ cần class load được thì mọi module khác đều gọi reflection vào private member được?

**Đáp án:** Không. Loader visibility khác quyền truy cập Java/module; JPMS readability, exports và opens cùng cơ chế reflection còn áp dụng.

**Câu 8.** Muốn cùng một API interface dùng được giữa host và hai plugin độc lập, nên đặt định nghĩa interface ở đâu?

**Đáp án:** Trong nguồn được **loader chung** của host và plugin nhìn thấy, thường là parent/API loader; tránh để mỗi plugin tự định nghĩa bản sao riêng của cùng tên interface.

## Ghi nhớ thực hành

Để giải thích đúng một sự kiện nạp lớp, lần lượt hỏi:

1. **Tên nhị phân** của class đang được yêu cầu là gì?
2. **Ai nhận yêu cầu** và **parent** của loader ấy là ai?
3. Loader có **delegate parent-first** hay policy khác? Những nguồn class/module nào mỗi loader quản lý?
4. **Ai thực sự định nghĩa** class trả về (`Class.getClassLoader()`)?
5. Các class phải tương tác có dùng **cùng định nghĩa API** không, hay bị tách qua defining loaders?
6. Module readability/exports/opens, resource lookup và TCCL có ảnh hưởng tới việc đang làm không?

Đừng suy ra quyền truy cập class chỉ từ dấu `.` trong package hoặc tên JAR. **Quan hệ parent giải thích hướng ủy quyền; defining loader xác định danh tính runtime của kiểu; module và thread context bổ sung các quy tắc nhìn thấy/tìm kiếm cần thiết.**

Nguồn chuẩn để kiểm tra thêm:

- [Java SE 17 API – `java.lang.ClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): ba loader built-in, parent, `loadClass`, `findClass` và các ngoại lệ delegation.
- [Java SE 17 API – `java.lang.Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getClassLoader()` và `getModule()`.
- [JVM Specification 17 – §5 Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): danh tính kiểu, defining/initiating loader và liên kết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-005">Class.getClassLoader và cách hiểu kết quả null</a>

<details>
<summary>Xem chi tiết</summary>

# Class.getClassLoader và cách hiểu kết quả null

## 1. `Class.getClassLoader()` trả về điều gì?

Mỗi đối tượng `Class<?>` trong Java là biểu diễn tại runtime của một lớp, interface, array type, primitive type hoặc `void`. Với **class hoặc interface thông thường**, phương thức `Class.getClassLoader()` cho biết **class loader đã định nghĩa kiểu đó** (*defining class loader*). Điều quan trọng là nó trả về loader gắn với **kiểu đang xét**, không nhất thiết là loader của dòng code đang gọi phương thức này.

Ví dụ:

~~~java
Class<?> type = String.class;
ClassLoader definingLoader = type.getClassLoader();
~~~

Trên các JDK thông dụng như OpenJDK 17+, `String` được định nghĩa bởi **bootstrap class loader** và `definingLoader` nhận giá trị `null`. Điều này hoàn toàn bình thường: kiểu `java.lang.String` vẫn tồn tại, vẫn dùng được; `null` ở đây chỉ là **cách API biểu diễn loader bootstrap**. Nó không có nghĩa JVM không tìm được lớp hoặc phương thức thất bại.

Ngược lại, khi chạy một ứng dụng Java thông thường từ classpath, class của ứng dụng thường được định nghĩa bởi **application/system class loader**, nên `MyApp.class.getClassLoader()` trả về một đối tượng `ClassLoader` khác `null`. Tên lớp cài đặt cụ thể và chuỗi `toString()` của loader có thể thay đổi theo JDK; không nên viết logic phụ thuộc vào chuỗi đó.

**Quy tắc nền tảng:** `getClassLoader()` là thao tác *quan sát loader của kiểu đã có*, không phải lệnh yêu cầu tạo một loader hoặc tải lại class file.

## 2. Tại sao bootstrap class loader thường được biểu diễn bằng `null`?

Các JDK hiện đại có ba vai trò loader tích hợp chính:

| Vai trò | Ý nghĩa | Giá trị liên quan trong Java |
| --- | --- | --- |
| **Bootstrap** | Loader nền tảng tích hợp sâu vào JVM, thường định nghĩa các lớp cốt lõi trong module `java.base` như `java.lang.String` và `java.lang.Object`. | Thường được biểu diễn bằng `null` trong `Class.getClassLoader()` và quan hệ parent. |
| **Platform** | Loader cho các lớp nền tảng và module thích hợp của Java/JDK; không thể giả định mọi lớp JDK đều do bootstrap định nghĩa. | Có thể lấy đối tượng qua `ClassLoader.getPlatformClassLoader()` (Java 9+). |
| **System/Application** | Thường định nghĩa lớp của ứng dụng trên classpath và một số module/tool phù hợp. | Có thể lấy qua `ClassLoader.getSystemClassLoader()`. |

Hình dung mô hình ủy quyền thông dụng:

~~~text
Application/System ClassLoader
           |
           | parent / delegate
           v
    Platform ClassLoader
           |
           | parent / delegate
           v
     Bootstrap Loader
      (API dùng null)
~~~

Đây là **mô hình ý niệm**, không phải cam kết rằng mọi loader đều có cấu trúc cha-con đơn giản, hoặc mọi lớp Java SE đều được bootstrap định nghĩa. Sau Java 9, module system cho phép những cách phân phối/ủy quyền phức tạp hơn; lớp thuộc nền tảng có thể do bootstrap, platform hoặc loader thích hợp khác định nghĩa. Cần quan sát **lớp cụ thể**, thay vì suy từ package bắt đầu bằng `java.` hoặc từ chuỗi tên loader.

Bootstrap là cơ chế đặc biệt của JVM, **không phải một đối tượng `ClassLoader` Java bình thường để ứng dụng có thể gọi phương thức**. Vì vậy, API dùng `null` để biểu diễn nó trong các trường hợp được tài liệu đặc tả. Đừng cố gọi `getParent()` hay `loadClass()` trên kết quả `null`.

Phân biệt hai đoạn code:

~~~java
ClassLoader loader = String.class.getClassLoader(); // thường là null
ClassLoader system = ClassLoader.getSystemClassLoader(); // đối tượng loader
~~~

Biến `system` thường khác `null`, nhưng **không phải** vì `String.class.getClassLoader() == null` mà bạn cần "thay thế" kết quả bằng `system`. Hai loader đại diện cho hai trách nhiệm khác nhau.

## 3. Không phải mọi kết quả `null` đều là một lớp do bootstrap định nghĩa

Đây là điểm dễ nhầm nhất của chủ đề. `Class.getClassLoader()` còn trả về `null` cho **primitive type** và **`void`**. Các kiểu này không được định nghĩa từ một class file và không có defining loader theo cách class thông thường có.

Với **array class**, đối tượng `Class` được JVM tạo tự động khi cần; loader mà `getClassLoader()` báo cáo là loader của **kiểu phần tử** (*element type*) của mảng. Nếu phần tử cuối cùng là primitive, mảng có loader bằng `null`. Nếu kiểu phần tử là class/interface do bootstrap định nghĩa, mảng cũng trả `null`.

| Biểu thức | Kết quả thường thấy | Vì sao? |
| --- | --- | --- |
| `String.class.getClassLoader()` | `null` | `String` là class cốt lõi do bootstrap định nghĩa trên OpenJDK thông dụng. |
| `Object.class.getClassLoader()` | `null` | `Object` do bootstrap định nghĩa trên OpenJDK thông dụng. |
| `int.class.getClassLoader()` | `null` | `int` là primitive type; không có class loader định nghĩa từ bytecode. |
| `boolean.class.getClassLoader()` | `null` | Tương tự primitive type. |
| `void.class.getClassLoader()` | `null` | `void` không phải class file. |
| `int[].class.getClassLoader()` | `null` | Mảng có phần tử cuối là `int`. |
| `int[][].class.getClassLoader()` | `null` | Mảng nhiều chiều với phần tử cuối là `int`. |
| `String[].class.getClassLoader()` | `null` | Loader báo cáo cho array tương ứng loader của `String`. |
| `User.class.getClassLoader()` | Thường khác `null` | `User` do application loader định nghĩa trong ứng dụng classpath thông thường. |
| `User[].class.getClassLoader()` | Bằng loader của `User.class` | Array class do JVM tạo nhưng gắn với loader của phần tử. |

**Cảnh báo khi diễn giải:** nếu kết quả `null` đến từ `int.class` hoặc `void.class` thì không nên nói “bootstrap đã nạp class file `int.class` hoặc `void.class`”. Không có những class file ấy theo cơ chế bình thường.

Với mảng nhiều chiều, có thể đi từ `clazz.getComponentType()` qua từng tầng tới kiểu phần tử cuối cùng:

~~~java
Class<?> component = String[][].class;
while (component.isArray()) {
    component = component.getComponentType();
}
// component == String.class
~~~

Đoạn này chỉ để minh họa thao tác trên đối tượng `Class`; nó không tự tạo instance hay nạp lại class. Các array type có đối tượng `Class` riêng, nhưng `getClassLoader()` tuân theo loader của kiểu phần tử.

## 4. Defining loader và initiating loader là hai khái niệm khác nhau

Khi bạn gọi:

~~~java
Class<?> type =
        ClassLoader.getSystemClassLoader().loadClass("java.lang.String");
ClassLoader definingLoader = type.getClassLoader();
~~~

Đối tượng `type` vẫn đại diện cho **cùng lớp `java.lang.String`** do bootstrap định nghĩa, nên `definingLoader` thường vẫn là `null`. System loader có thể tham gia quá trình tìm lớp qua delegation, nhưng nó **không trở thành defining loader** của `String` chỉ vì ta gọi `loadClass` từ nó.

- **Defining loader:** loader thực sự định nghĩa kiểu thông thường từ biểu diễn class; là thành phần của danh tính kiểu runtime.
- **Initiating loader:** loader được JVM ghi nhận là bên bắt đầu việc nạp kiểu theo một tên; loader này có thể chuyển yêu cầu cho parent và cuối cùng nhận lại lớp do loader khác định nghĩa.
- **Requested loader:** đối tượng mà code trực tiếp gọi `loadClass(name)` hoặc đưa vào `Class.forName(name, false, loader)`. Đây có thể là initiating loader, nhưng kết quả `Class.getClassLoader()` vẫn phản ánh defining loader.

Một nguyên tắc liên quan: danh tính class/interface thông thường được xác định bởi **binary name và defining loader**. Hai lớp cùng tên nhưng do hai defining loader độc lập định nghĩa có thể là **hai kiểu runtime khác nhau**; ép kiểu giữa chúng có thể ném `ClassCastException`. Ngược lại, loader con gọi `loadClass` và được loader cha trả về lớp đã định nghĩa thì nó đang dùng **cùng đối tượng `Class`**, không tạo một định nghĩa mới.

### Vì sao `ClassLoader.getParent() == null` cũng thường hợp lệ?

Hai câu lệnh sau trả lời **hai câu hỏi khác nhau**:

~~~java
ClassLoader owner = String.class.getClassLoader();
ClassLoader child = new ClassLoader(null) {};
boolean parentIsNull = child.getParent() == null;
~~~

- `owner == null`: `String` trên JDK thông dụng do bootstrap định nghĩa.
- `parentIsNull == true`: loader `child` đã được tạo với **parent delegation tương ứng bootstrap**; điều đó **không biến `child` thành bootstrap loader**. `child` vẫn là một đối tượng Java hoàn toàn có thật.

Nếu `child.loadClass("java.lang.String")`, loader con thường có thể ủy quyền tới bootstrap và nhận lại `String.class`. Do đó kết quả `getClassLoader()` của lớp trả về vẫn là `null`, bất kể đối tượng `child` được dùng để bắt đầu lời gọi.

Lưu ý Java 9+: khi tạo custom loader với parent `null`, **không được mặc định rằng mọi class nền tảng đều nhìn thấy được**. Nếu cần các module lớp nền tảng, hãy cân nhắc `ClassLoader.getPlatformClassLoader()` làm parent theo hợp đồng API, tùy mục tiêu thiết kế.

## 5. Ví dụ Java 17+ độc lập và đầu ra chính xác

Tạo file **`LoaderNullDemo.java`** với nội dung:

~~~java
public class LoaderNullDemo {
    static final class BusinessType {}

    static String describe(Class<?> type) {
        return type.getClassLoader() == null ? "null" : "non-null";
    }

    public static void main(String[] args) throws Exception {
        System.out.println("String: " + describe(String.class));
        System.out.println("int: " + describe(int.class));
        System.out.println("void: " + describe(void.class));
        System.out.println("String[]: " + describe(String[].class));
        System.out.println("int[][]: " + describe(int[][].class));
        System.out.println("BusinessType: " + describe(BusinessType.class));

        System.out.println("BusinessType[] same loader: "
                + (BusinessType[].class.getClassLoader()
                == BusinessType.class.getClassLoader()));

        Class<?> strViaSystem =
                ClassLoader.getSystemClassLoader()
                        .loadClass("java.lang.String");
        System.out.println("system.loadClass(String): "
                + describe(strViaSystem));

        ClassLoader child = new ClassLoader(null) {};
        System.out.println("child parent null: "
                + (child.getParent() == null));
        System.out.println("child.loadClass(String): "
                + describe(child.loadClass("java.lang.String")));
    }
}
~~~

Tại thư mục chứa file, chạy:

~~~bash
javac LoaderNullDemo.java
java LoaderNullDemo
~~~

Đầu ra trên OpenJDK 17+ khi chạy ứng dụng từ classpath thông thường (đã biên dịch và chạy kiểm tra):

~~~text
String: null
int: null
void: null
String[]: null
int[][]: null
BusinessType: non-null
BusinessType[] same loader: true
system.loadClass(String): null
child parent null: true
child.loadClass(String): null
~~~

Cách đọc kết quả:

1. `String`: bootstrap được đại diện bằng `null`.
2. `int`, `void` và mảng primitive: `null` nhưng **không có nghĩa chúng được định nghĩa từ class file bởi bootstrap**.
3. `String[]`: mảng trả cùng loader với kiểu phần tử `String`.
4. `BusinessType`: là class do ứng dụng định nghĩa, thường có defining loader khác `null` trong cách khởi chạy này.
5. `BusinessType[]` có cùng loader được báo cáo như `BusinessType`, mặc dù JVM tạo array class tự động.
6. `system.loadClass(String)`: mặc dù yêu cầu khởi đi từ system loader, lớp được trả về vẫn thuộc bootstrap.
7. `child parent null`: dùng `null` làm parent cho custom loader, **không** có nghĩa đối tượng `child` bằng `null`.
8. `child.loadClass(String)`: theo parent delegation chuẩn, lớp cốt lõi vẫn được bootstrap định nghĩa.

Ví dụ cố tình **không in** `loader.toString()`, tên class loader cụ thể hoặc danh sách module vì các chuỗi đó có thể khác giữa bản phân phối JDK. Mã hoạt động độc lập, không cần JAR bổ sung.

## 6. Phân biệt `getClassLoader()` với các API hay bị lẫn

| API | Câu hỏi mà API trả lời | Lưu ý khi thấy `null` |
| --- | --- | --- |
| `someClass.getClassLoader()` | Ai là defining loader của lớp/interface hoặc loader báo cáo cho array? | Có thể đại diện bootstrap, primitive/void hoặc mảng có phần tử tương ứng. |
| `ClassLoader.getSystemClassLoader()` | System/application loader hiện tại là gì? | Không thể dùng nó làm “bootstrap loader thay thế”. |
| `ClassLoader.getPlatformClassLoader()` | Đối tượng platform loader hiện tại là gì? | API tồn tại từ Java 9; platform khác bootstrap. |
| `someLoader.getParent()` | Parent để delegation của loader này là gì? | Thường `null` khi parent là bootstrap; parent không đồng nghĩa defining loader của class đang xét. |
| `Thread.currentThread().getContextClassLoader()` | Loader ngữ cảnh được gắn vào luồng này là gì? | Có thể `null`; có thể khác loader đã định nghĩa class của code đang chạy. |
| `Class.forName(name, false, loader)` | Yêu cầu tìm kiểu bằng loader cụ thể mà chưa chủ động khởi tạo nó. | `loader == null` thường chỉ định dùng bootstrap; không phải “chọn application loader mặc định”. |

Context class loader hữu ích với framework và thư viện nạp plugin/SPI vì code framework có thể chạy trong một loader khác loader chứa class của ứng dụng. Do đó không được kết luận:

~~~java
Thread.currentThread().getContextClassLoader()
        == getClass().getClassLoader()
~~~

luôn luôn là `true`. Đây là **hai nguồn thông tin khác nhau**. Khi xử lý tài nguyên hoặc service provider, cần lựa chọn loader theo hợp đồng của API đang sử dụng.

## 7. Ứng dụng thực tế: xử lý `null` như thế nào?

### 7.1. In tên loader an toàn

Nếu chỉ muốn log để chẩn đoán, một mẫu tối giản là:

~~~java
static String describeLoader(Class<?> type) {
    ClassLoader loader = type.getClassLoader();

    if (loader != null) {
        return loader.getClass().getName();
    }
    if (type.isPrimitive()) {
        return "primitive/void: không có defining loader";
    }
    if (type.isArray()) {
        return "array có loader null theo element type";
    }
    return "bootstrap (biểu diễn null trên JDK hiện tại)";
}
~~~

Đây là **phương thức minh họa**, có thể đặt bên trong lớp Java của bạn; không phải chương trình `main` độc lập. Nó phân biệt ít nhất ba nguồn `null` thường gặp. Để phân tích array chính xác hơn, truy ngược `getComponentType()` đến kiểu phần tử cuối cùng rồi xác định đó là primitive hay class/interface nào.

Nếu thực sự cần nêu identity của loader cho log, có thể ghi thêm `System.identityHashCode(loader)` khi `loader != null`, nhưng đó là mã nhận diện theo tiến trình, không phải ID ổn định xuyên lần chạy.

### 7.2. Không gọi phương thức trên `null`

Mã sau có lỗi khi `clazz` là `String.class` trên OpenJDK thông thường:

~~~java
ClassLoader loader = String.class.getClassLoader();
loader.getResource("config.properties"); // NullPointerException
~~~

Cần chọn API tài nguyên phù hợp với yêu cầu:

- Muốn tìm tài nguyên **tương đối với một lớp hoặc trên module tương ứng**, ưu tiên `clazz.getResource(...)` / `clazz.getResourceAsStream(...)` với quy tắc đường dẫn của `Class`.
- Muốn tìm tài nguyên từ **system loader**, có thể dùng `ClassLoader.getSystemResource(...)` hoặc `getSystemClassLoader().getResource(...)`.
- Muốn dùng loader của một plugin do custom loader định nghĩa, kiểm tra `clazz.getClassLoader()` có khác `null` rồi mới gọi API trên loader ấy; tránh tự ý thay thế bằng system loader, vì system loader có thể không thấy plugin.

**Không có một fallback loader duy nhất đúng cho mọi tình huống.** Quyết định phụ thuộc phạm vi tài nguyên/lớp cần nạp. Nếu loader đích thực sự là bootstrap, hãy dùng API có ngữ nghĩa hỗ trợ bootstrap như `Class.forName(binaryName, false, null)` khi phù hợp, thay vì cố gọi phương thức trên `null`.

### 7.3. Chẩn đoán lỗi nạp lớp

Nếu ứng dụng gặp `ClassNotFoundException` hoặc `NoClassDefFoundError`, câu lệnh `Class.getClassLoader() == null` **không chứng minh class thiếu**; đối tượng `Class` đã có thì lớp/kiểu được xác định. Cần phân biệt:

- `ClassNotFoundException`: yêu cầu tìm một binary name bằng cơ chế cụ thể không thành công.
- `NoClassDefFoundError`: JVM không thể cung cấp định nghĩa cần thiết tại runtime hoặc gặp lỗi liên kết/khởi tạo khác; xem thông điệp và nguyên nhân gốc.
- `NullPointerException` sau `getClassLoader().loadClass(...)`: caller quên xử lý kết quả `null` trước khi gọi phương thức trên nó.

Để điều tra class bị nạp nhầm hoặc khác phiên bản, log **tên nhị phân** `clazz.getName()`, class loader thực sự khi khác `null`, **module** `clazz.getModule()` và thông tin vị trí bytecode nếu có thể xác định một cách an toàn. Không suy luận danh tính một loader chỉ qua cách tên package hiển thị.

## 8. Lỗi tư duy phổ biến và câu hỏi tự kiểm tra

| Nhận định | Đúng hay sai? | Lý do |
| --- | --- | --- |
| “`String.class.getClassLoader() == null` nghĩa là `String` chưa được nạp.” | **Sai** | Đã có `String.class`, còn `null` biểu diễn bootstrap. |
| “Mọi kiểu có loader `null` đều là class do bootstrap định nghĩa.” | **Sai** | Primitive, `void` và array của chúng cũng báo `null`. |
| “Loader con gọi `loadClass("java.lang.String")` thì loader con sẽ định nghĩa `String`.” | **Sai** | Delegation thường trả về lớp do bootstrap định nghĩa. |
| “`new ClassLoader(null) {}` tạo ra một biến `null`.” | **Sai** | Nó tạo một đối tượng loader có parent `null`. |
| “Mọi class trong `java.*` phải do bootstrap định nghĩa.” | **Sai** | Java Platform Module System dùng các loader tích hợp khác nhau. |
| “Loader của `Foo[].class` không liên quan loader của `Foo.class`.” | **Sai** | Loader được báo cáo cho array bằng loader của kiểu phần tử. |
| “`Class.getClassLoader()` và thread context class loader có thể khác nhau.” | **Đúng** | Chúng trả lời hai câu hỏi khác nhau. |

**Câu hỏi 1:** App loader gọi `loadClass("java.lang.Object")` rồi trả lại `Class<?>`. Nếu kết quả `getClassLoader() == null`, có phải `loadClass` thất bại không?

**Trả lời:** Không. `loadClass` đã trả về lớp; đối tượng `Class` đại diện cho `Object` do bootstrap định nghĩa.

**Câu hỏi 2:** `int[][].class.getClassLoader() == null` có phải bằng chứng tồn tại file `int[][].class` do bootstrap tải không?

**Trả lời:** Không. JVM tự tạo array class theo yêu cầu; phần tử cuối cùng là primitive `int`.

**Câu hỏi 3:** Làm sao tránh hiểu nhầm trong log?

**Trả lời:** Ghi rõ `null (bootstrap/primitive/void/array tùy loại Class)`, kiểm tra `isPrimitive()` và `isArray()` trước, và nêu rõ đang kiểm tra defining loader hay context loader.

## Tài liệu chuẩn

- [Java SE 21 – `Class.getClassLoader()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getClassLoader()): hợp đồng trả về `null` với bootstrap, primitive và `void`.
- [Java SE 21 – `ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): bootstrap/platform/system loader, delegation, loader của array class.
- [JVM Specification §5.3 – Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): defining loader, initiating loader và nhận diện kiểu runtime.
- [Oracle JDK Migration Guide – New Class Loader Implementations](https://docs.oracle.com/en/java/javase/21/migrate/migrating-jdk-8-later-jdk-releases.html): thay đổi loader từ Java 9 và chú ý về platform loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-006">ClassLoader.getPlatformClassLoader và getSystemClassLoader</a>

<details>
<summary>Xem chi tiết</summary>

# ClassLoader.getPlatformClassLoader và getSystemClassLoader

## 1. Hai API dùng để làm gì?

Khi tìm hiểu cách JVM nạp lớp, ta thường gặp hai phương thức `static` của `java.lang.ClassLoader`:

~~~java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
ClassLoader system = ClassLoader.getSystemClassLoader();
~~~

Cả hai **trả về đối tượng ClassLoader**, nhưng chúng giữ vai trò khác nhau:

- `getPlatformClassLoader()` trả về **platform class loader**, loader tích hợp dùng cho các lớp của nền tảng Java và JDK. Phương thức xuất hiện từ Java 9.
- `getSystemClassLoader()` trả về **system class loader** (thường gọi là **application class loader**), loader mặc định thường được dùng để khởi chạy ứng dụng và tải lớp nằm trên application classpath hoặc application module path.

Không có tham số: các phương thức này không nhận tên class, không nạp trực tiếp một class cụ thể và không tự tạo loader mới mỗi lần được gọi. Chúng cung cấp **tham chiếu tới loader đã được JVM thiết lập**. Sau khi có loader, ta có thể gọi `loadClass(...)`, `getResource(...)` hoặc dùng loader làm parent cho loader tùy biến.

Trong JVM thông thường từ Java 9 trở lên, sơ đồ ủy quyền là:

~~~text
                       ┌───────────────────────────┐
                       │ Bootstrap loader          │
                       │ Lõi JVM / java.base       │
                       │ Java biểu diễn bằng null  │
                       └─────────────▲─────────────┘
                                     │ parent
                       ┌─────────────┴─────────────┐
                       │ Platform class loader     │
                       │ API Java SE / JDK modules │
                       └─────────────▲─────────────┘
                                     │ parent
                       ┌─────────────┴─────────────┐
                       │ System / Application      │
                       │ class loader              │
                       │ Mã ứng dụng               │
                       └─────────────▲─────────────┘
                                     │ parent
                       ┌─────────────┴─────────────┐
                       │ ClassLoader do ứng dụng   │
                       │ tạo (nếu có)              │
                       └───────────────────────────┘
~~~

Mũi tên chỉ **quan hệ parent/delegation**, không có nghĩa mọi class được nạp vào bộ nhớ theo thứ tự từ trên xuống dưới. Thông thường loader con kiểm tra đã nạp chưa, thử ủy quyền cho parent, rồi mới tìm bytecode của mình nếu parent không tìm thấy.

Sơ đồ trên mô tả **loader tích hợp mặc định**. JVM và công cụ chạy ứng dụng có thể sử dụng loader riêng; tùy chọn `java.system.class.loader` cũng có thể thay thế đối tượng system loader. Đối với named modules, khả năng nhìn thấy class có thêm quy tắc module và một số đường ủy quyền đặc biệt; sơ đồ parent đơn giản không đủ để mô tả mọi trường hợp.

## 2. Phân biệt bootstrap, platform và system

| Tiêu chí | Bootstrap loader | Platform loader | System loader |
|---|---|---|---|
| Cách lấy | Không có API trả về một đối tượng `ClassLoader` thông thường; thường biểu diễn bằng `null` | `ClassLoader.getPlatformClassLoader()` | `ClassLoader.getSystemClassLoader()` |
| Vai trò chính | Nạp các lớp nền tảng cốt lõi, như `java.lang.String` trong JDK 17 | Nạp các lớp nền tảng và module JDK được gán cho platform loader | Thường nạp lớp ứng dụng từ classpath/application module path |
| Quan hệ parent mặc định | Không có parent | Bootstrap | Platform |
| Kết quả `Class.getClassLoader()` | `null` cho lớp do bootstrap định nghĩa | Đối tượng platform loader cho lớp do nó định nghĩa | Đối tượng system loader cho lớp do nó định nghĩa |
| Có phải một loader mới cho mỗi lần gọi? | Không áp dụng | Không | Không |
| Có thể làm parent cho loader tùy biến? | Có thể truyền `null` để chỉ bootstrap (với giới hạn tầm nhìn lớp) | Có, phù hợp nếu chỉ cần API nền tảng | Có, phù hợp nếu plugin cần nhìn thấy API của ứng dụng |

**Điểm rất quan trọng:** `ClassLoader` mà ta gọi `loadClass` **không nhất thiết** là ClassLoader *đã định nghĩa* class được trả về.

Ví dụ, system loader có thể thực hiện:

~~~java
Class<?> type = ClassLoader.getSystemClassLoader()
        .loadClass("java.lang.String");
~~~

Dù yêu cầu xuất phát từ system loader, `String.class.getClassLoader() == null` vẫn là `true` trên JVM Java 17 tiêu chuẩn: bootstrap loader đã định nghĩa `String` và system loader chỉ chuyển yêu cầu lên parent theo cơ chế delegation.

Tương tự, class do platform loader định nghĩa có thể được hệ thống tìm thấy thông qua system loader. Do đó đừng dùng câu “class được yêu cầu qua loader X” thay cho “class do loader X định nghĩa”.

## 3. `getPlatformClassLoader()`

### 3.1. Chức năng và giá trị trả về

~~~java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
~~~

Phương thức `public static` này có từ **Java 9** và trả về platform class loader. Theo API Java 17, các **platform classes** có thể được nhìn thấy qua loader này. Chúng bao gồm API Java SE, class triển khai của chúng và những class runtime riêng của JDK do platform loader hoặc tổ tiên của nó quản lý.

Thông thường trên JDK 17:

- `platform != null`.
- `platform.getParent() == null`, vì parent là bootstrap loader được biểu diễn bằng `null`.
- `platform != ClassLoader.getSystemClassLoader()`.

Lưu ý **không phải** mọi lớp thuộc các gói `java.*` đều do cùng một loader cụ thể định nghĩa; module nào do loader nào quản lý là một phần của cơ chế runtime và cấu hình module. Không nên tự suy ra “cứ tên package bắt đầu bằng `java.` thì `getClassLoader() == null`”.

### 3.2. Vì sao dùng platform loader làm parent?

Giả sử muốn nạp JAR mở rộng nhưng **không tự động cấp quyền nhìn thấy toàn bộ classpath của host**. Ta có thể tạo loader con với parent là platform loader:

~~~java
ClassLoader parent = ClassLoader.getPlatformClassLoader();

// Mã minh họa, không phải chương trình đầy đủ:
// URL[] urls = ... các URL tới JAR tùy biến ...
// try (URLClassLoader pluginLoader = new URLClassLoader(urls, parent)) {
//     ...
// }
~~~

Khi đó plugin loader có thể ủy quyền việc tìm các lớp nền tảng cho platform loader. Nó **không mặc nhiên nhìn thấy** các class ứng dụng do system loader định nghĩa, vì system loader không phải tổ tiên của platform loader.

Nếu plugin cần triển khai một interface riêng của ứng dụng như `com.example.api.Plugin`, parent nên là **loader đã định nghĩa interface đó** hoặc một loader có thể ủy quyền tới class ấy, ví dụ:

~~~java
ClassLoader sharedApiParent = Plugin.class.getClassLoader();
~~~

Đoạn trên chỉ có ý nghĩa trong dự án đã định nghĩa `Plugin`; đây là **mã minh họa**, không phải file Java chạy độc lập. Dùng parent là loader của hợp đồng API giúp host và plugin chia sẻ đúng **cùng runtime type**, tránh hai loader tự định nghĩa các lớp API giống tên rồi phát sinh `ClassCastException` hoặc `ServiceConfigurationError`.

**Không nhầm tầm nhìn với bảo mật:** Đặt platform loader làm parent có thể hạn chế những class ứng dụng mà plugin nhìn thấy qua cơ chế ủy quyền; điều này **không tạo sandbox đáng tin cậy** cho mã plugin. Plugin chạy trong cùng JVM vẫn có thể thực thi mã với quyền của tiến trình.

### 3.3. Ghi chú về JPMS

Một số module nâng cấp (upgraded modules) có quan hệ đọc module khác; vì vậy triển khai platform loader có thể thực hiện ủy quyền tới loader **không phải tổ tiên** trong những tình huống module nhất định. Đây là điểm được chính Javadoc lưu ý. Không nên viết một quy tắc tuyệt đối rằng platform loader “không bao giờ ủy quyền tới system loader” hoặc chỉ dựa vào quan hệ `getParent()` để kết luận toàn bộ tầm nhìn module.

## 4. `getSystemClassLoader()`

### 4.1. Chức năng và giá trị trả về

~~~java
ClassLoader system = ClassLoader.getSystemClassLoader();
~~~

Phương thức trả về system class loader, thường dùng để tải class khởi động ứng dụng. Đây cũng là **parent mặc định** của instance `ClassLoader` được tạo bằng constructor không nhận parent:

~~~java
class MyLoader extends ClassLoader {
    // super() ngầm định: parent mặc định là system class loader.
}
~~~

Với Java 17 chạy ứng dụng classpath thông thường:

- Loader định nghĩa class chứa phương thức `main` thường là system/application loader.
- `system.getParent() == ClassLoader.getPlatformClassLoader()` thường đúng với loader tích hợp mặc định.
- `Thread.currentThread().getContextClassLoader() == system` thường đúng trong luồng `main` mà Java launcher tạo.

Ba nhận xét trên mô tả **mặc định**, không phải lời hứa cho mọi application server, IDE, agent, plugin host, container Java tùy biến hoặc luồng mà ứng dụng tự đặt context loader.

### 4.2. System loader tìm các class ở đâu?

Trong một ứng dụng khởi chạy bằng classpath:

~~~bash
java -cp "build/classes:lib/example.jar" com.example.Main
~~~

System loader thường tìm class ứng dụng từ các thư mục/JAR trên application classpath. Trên Windows, dấu phân tách classpath là `;`; trên Linux/macOS thường là `:`.

Khi chạy ứng dụng JPMS với `--module-path` và `--module`, class của các module ứng dụng thường được hệ thống loader tương ứng quản lý. Tuy vậy không thể suy ra đơn giản rằng mọi module trên module path đều do **cùng một** ClassLoader định nghĩa: cấu hình module layers và quy tắc loader vẫn quan trọng.

Khác với Java 8, Java 9+ không đảm bảo system loader là `java.net.URLClassLoader`. Mã như:

~~~java
// Không an toàn trên JDK hiện đại:
// URLClassLoader urls = (URLClassLoader) ClassLoader.getSystemClassLoader();
~~~

có thể gây `ClassCastException`. Muốn xem classpath, hãy xem `System.getProperty("java.class.path")` (nếu phù hợp) hoặc API build/runtime đang dùng; muốn thêm URL cho plugin, tạo một `URLClassLoader` riêng. Không dựa vào class triển khai nội bộ như `jdk.internal.loader.ClassLoaders$AppClassLoader`.

### 4.3. Custom system loader

JVM cho phép đặt system property **ngay khi khởi động**:

~~~bash
java -Djava.system.class.loader=com.example.CustomLoader -cp ... com.example.Main
~~~

Tên class ở đây chỉ là ví dụ về cấu hình; lệnh **không chạy được nếu chưa viết và cung cấp** `com.example.CustomLoader` trên classpath thích hợp. Custom system loader phải có **public constructor nhận đúng một tham số kiểu `ClassLoader`**. JVM nạp class cấu hình bằng default system loader rồi truyền default system loader làm parent khi khởi tạo instance custom.

Hệ quả:

1. `getSystemClassLoader()` có thể trả về **custom loader**, không phải loader tích hợp ban đầu.
2. `getSystemClassLoader().getParent()` có thể là default system loader thay vì platform loader trực tiếp.
3. Trong constructor của custom loader, **không gọi ngược `getSystemClassLoader()`**: vòng lặp khởi tạo có thể gây `IllegalStateException`.
4. Nếu class cấu hình không thể nạp, constructor không phù hợp hoặc constructor lỗi, JVM có thể báo `Error` khi thiết lập system loader.
5. Runtime chỉ xét property này lúc khởi tạo system loader. Đổi `System.setProperty(...)` về sau không phải cách thay loader của JVM đang chạy.

Đây là cấu hình đặc biệt. Ứng dụng bình thường **không cần** thay thế system loader chỉ để tải plugin hoặc một JAR động.

## 5. Chương trình Java 17+ so sánh trực tiếp hai loader

Tạo **một file** `LoaderComparison.java` ở thư mục bất kỳ:

~~~java
public final class LoaderComparison {
    private LoaderComparison() {
    }

    static final class AppType {
    }

    private static String definedBy(
            Class<?> type, ClassLoader platform, ClassLoader system) {
        ClassLoader loader = type.getClassLoader();
        if (loader == null) {
            return "bootstrap";
        }
        if (loader == platform) {
            return "platform";
        }
        if (loader == system) {
            return "system";
        }
        return "other";
    }

    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader system = ClassLoader.getSystemClassLoader();

        System.out.println("Platform exists: " + (platform != null));
        System.out.println("System differs from platform: " + (system != platform));

        System.out.println("Platform parent is bootstrap: "
                + (platform.getParent() == null));
        System.out.println("System parent is platform: "
                + (system.getParent() == platform));

        System.out.println("String defined by: "
                + definedBy(String.class, platform, system));
        System.out.println("AppType defined by: "
                + definedBy(AppType.class, platform, system));

        Class<?> delegated = system.loadClass("java.lang.String");
        System.out.println("Delegated String identical: "
                + (delegated == String.class));

        boolean platformCanFindApp;
        try {
            platform.loadClass("LoaderComparison$AppType");
            platformCanFindApp = true;
        } catch (ClassNotFoundException ex) {
            platformCanFindApp = false;
        }
        System.out.println("Platform finds application class: "
                + platformCanFindApp);

        System.out.println("Main context loader is system: "
                + (Thread.currentThread().getContextClassLoader() == system));
    }
}
~~~

**Biên dịch và chạy** bằng JDK 17+ từ thư mục chứa file, trong một phiên Java bình thường, **không** dùng `-Djava.system.class.loader`, `--patch-module` hay công cụ thay đổi loader:

~~~bash
javac -encoding UTF-8 LoaderComparison.java
java -cp . LoaderComparison
~~~

**Stdout dự kiến trên OpenJDK/Oracle JDK 17 tiêu chuẩn với các giả định trên:**

~~~text
Platform exists: true
System differs from platform: true
Platform parent is bootstrap: true
System parent is platform: true
String defined by: bootstrap
AppType defined by: system
Delegated String identical: true
Platform finds application class: false
Main context loader is system: true
~~~

Lệnh `-cp .` làm code ứng dụng nằm trên application classpath. Nếu môi trường chạy đã cấu hình loader tùy biến, module patching hoặc context ClassLoader khác, một số so sánh về parent, nơi định nghĩa class hoặc context loader có thể thay đổi. Khi đó kết quả là thông tin chẩn đoán hữu ích, không phải JVM “sai”.

### Giải thích từng phép kiểm tra

**`platform != null` và `system != platform`.** Platform loader là đối tượng loader riêng, khác system loader trong hệ loader tích hợp. Bootstrap loader mới là loader thường được biểu diễn bằng `null`.

**`getParent()`.** Parent của platform là bootstrap trong cấu hình mặc định, nên `platform.getParent() == null`. Parent của system tích hợp là platform. Nếu system loader được custom, so sánh parent trực tiếp cần xét lại.

**`String.class.getClassLoader()`.** `java.lang.String` được bootstrap định nghĩa, vì vậy `definedBy` trả về `bootstrap`. Việc gọi `system.loadClass("java.lang.String")` vẫn nhận đúng cùng đối tượng `Class<?>` nhờ delegation; system loader **không tự định nghĩa lại** `String`.

**`AppType.class.getClassLoader()`.** `AppType` là nested class thuộc source ứng dụng, được system loader nạp trong lần chạy classpath chuẩn; kết quả là `system`. Class lồng này không được JVM tự động coi là class của nền tảng chỉ vì nó nằm trong cùng file.

**`platform.loadClass("LoaderComparison$AppType")`.** Tên nhị phân của class lồng sử dụng `$`. Platform loader thông thường không có application classpath của chương trình và không tìm class ứng dụng này; do đó bắt `ClassNotFoundException` rồi in `false`.

**`Thread.currentThread().getContextClassLoader()`.** Java launcher thường gán system loader làm context loader của luồng `main`. Đây là trạng thái **của thread**, có thể sửa bởi framework và code ứng dụng, không phải một phương thức lấy loader định nghĩa class `LoaderComparison`.

## 6. Ba cách lấy ClassLoader khác nhau và khi nào dùng

Các API sau dễ bị nhầm lẫn nhưng câu hỏi mà chúng trả lời khác nhau:

~~~java
ClassLoader platform = ClassLoader.getPlatformClassLoader();

ClassLoader system = ClassLoader.getSystemClassLoader();

ClassLoader defining = LoaderComparison.class.getClassLoader();

ClassLoader context = Thread.currentThread().getContextClassLoader();
~~~

| Cách lấy | Ý nghĩa chính | Khi phù hợp |
|---|---|---|
| `getPlatformClassLoader()` | Loader nền tảng tích hợp | Làm parent khi một loader con chỉ cần chia sẻ API của nền tảng |
| `getSystemClassLoader()` | System/application loader được JVM chọn | Tìm class/resource ứng dụng trong cấu hình runtime tiêu chuẩn; parent mặc định |
| `SomeApi.class.getClassLoader()` | Loader **đã định nghĩa** đúng runtime type `SomeApi` | Thiết kế host/plugin cần dùng chung interface và DTO |
| `Thread.currentThread().getContextClassLoader()` | Loader do thread giữ để framework tra cứu theo ngữ cảnh | `ServiceLoader` và cơ chế khám phá class/resource của một số thư viện |

**Ví dụ với `ServiceLoader`:** `ServiceLoader.load(MyService.class)` thường tra cứu provider qua thread context loader. Ngược lại, `ServiceLoader.load(MyService.class, explicitLoader)` nhận loader do ứng dụng truyền vào. Vì vậy một provider trong plugin loader có thể không được tìm thấy nếu thread context loader vẫn là system loader không thấy plugin JAR.

Nếu tạm đổi context loader, luôn khôi phục bằng `finally`:

~~~java
// Mã minh họa: cần khai báo pluginLoader tại chương trình thực tế.
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi API thư viện cần phát hiện class bằng context loader.
} finally {
    thread.setContextClassLoader(previous);
}
~~~

Đừng thay context loader vĩnh viễn cho toàn bộ ứng dụng nếu không hiểu ảnh hưởng của nó trên thread pool hoặc framework khác.

## 7. Vì sao “class loading” không giống “class initialization”?

Hai phương thức `getPlatformClassLoader()` và `getSystemClassLoader()` chỉ lấy tham chiếu loader. Khi gọi `loadClass("com.example.Widget")`, loader thường yêu cầu nạp class (và có thể resolve theo đường API khác) nhưng **không tự động thực thi static initializer của class đó**.

Phân biệt:

~~~java
Class<?> a = ClassLoader.getSystemClassLoader()
        .loadClass("com.example.Widget");

// Mã minh họa: nếu Widget tồn tại và cần kích hoạt initialization:
Class<?> b = Class.forName(
        "com.example.Widget",
        true,
        ClassLoader.getSystemClassLoader()
);
~~~

Trong overload ba tham số của `Class.forName`, `initialize = true` yêu cầu initialization nếu class chưa khởi tạo. Có thể dùng `false` khi muốn tìm class mà không chủ động kích hoạt initialization.

Mỗi runtime type được nhận diện bằng **tên nhị phân + loader định nghĩa**, nên hai loader tự định nghĩa hai class cùng FQCN có thể tạo hai kiểu không tương thích. `loadClass` có thể trả về class từ parent; **không thể** xác định nơi class được định nghĩa chỉ bằng tên ClassLoader mà caller đã dùng.

## 8. Thực tế sử dụng: chọn parent cho một loader plugin

Giả sử ứng dụng chủ khai báo interface `com.acme.api.Plugin` và plugin cần implements interface ấy.

~~~text
Application classpath
  └── com.acme.api.Plugin       ← do loader của host định nghĩa
Plugin JAR
  └── com.vendor.HelloPlugin    ← do loader con định nghĩa
~~~

Hai lựa chọn parent mang ý nghĩa khác nhau:

**A. Chia sẻ API của host:**

~~~java
// Đây là mẫu thiết kế, không phải chương trình chạy độc lập.
// Plugin là interface đã định nghĩa trong ứng dụng.
ClassLoader parent = Plugin.class.getClassLoader();
// URLClassLoader loader = new URLClassLoader(jarUrls, parent);
~~~

Loader con ủy quyền tới loader của `Plugin`, qua đó có thể resolve interface chung đúng bản sắc class. Điều này thường hợp lý cho plugin trong cùng JVM.

**B. Chỉ ủy quyền trực tiếp tới platform loader:**

~~~java
ClassLoader parent = ClassLoader.getPlatformClassLoader();
// URLClassLoader loader = new URLClassLoader(jarUrls, parent);
~~~

Nếu interface `Plugin` chỉ có ở classpath do system loader định nghĩa và không nằm trong plugin JAR, loader con **thường không tìm được** interface ấy. Hệ quả có thể là `NoClassDefFoundError` hoặc lỗi provider khi load class triển khai; việc platform loader là parent không làm classpath của system trở thành tầm nhìn của con.

Đây là lý do **chọn parent theo nhu cầu chia sẻ type** thay vì chọn máy móc platform hoặc system. Đối với plugin không tin cậy, cần giới hạn đặc quyền ở cấp tiến trình/container và giao thức tách biệt; ClassLoader không cung cấp ranh giới bảo mật đáng tin.

## 9. Lỗi thường gặp và cách kiểm tra

| Triệu chứng | Nguyên nhân hoặc hiểu lầm | Cách xử lý |
|---|---|---|
| `ClassCastException` khi ép system loader sang `URLClassLoader` | Java 9+ không cam kết system loader có kiểu `URLClassLoader` | Dùng API `ClassLoader`, hoặc tự tạo `URLClassLoader` dành cho JAR cần nạp |
| `platform.getParent() == null` rồi kết luận platform cũng là bootstrap | `null` ở đây **biểu diễn parent bootstrap**, không nói platform là bootstrap | So sánh trực tiếp `platform == null` (false), hiểu ý nghĩa `getParent()` |
| System loader trả `String.class` rồi kết luận nó định nghĩa `String` | Delegation trả class do bootstrap định nghĩa | Kiểm tra `String.class.getClassLoader()` |
| `ClassNotFoundException` khi platform loader tìm class host | Application classpath không phải nguồn tìm class thông thường của platform loader | Dùng loader của API host hoặc system loader khi phù hợp |
| `NoClassDefFoundError` trong plugin | Plugin loader không thấy dependency hoặc interface cần resolve | Kiểm tra parent và URL chứa JAR phụ thuộc, cùng danh tính loader của interface |
| `getSystemClassLoader().getParent()` khác platform | Custom system loader hoặc cấu hình runtime khác | Kiểm tra `java.system.class.loader` và thứ bậc loader thực tế |
| `ServiceLoader` không tìm provider | Thread context loader không nhìn thấy JAR plugin hoặc thiếu `META-INF/services` | Chỉ rõ loader khi load service; kiểm tra descriptor và resource |
| Khởi tạo custom system loader gây lỗi vòng lặp | Constructor gọi lại `getSystemClassLoader()` | Dùng parent constructor được JVM truyền vào; tránh truy cập system loader trong quá trình khởi tạo |
| `getResource(...)` không thấy resource trong named module | Module đóng gói tài nguyên hoặc khác loader/tầm nhìn | Kiểm tra tên resource, loader, module và quy tắc `opens` của module |
| Code chạy được từ IDE nhưng không chạy từ CLI | IDE dùng classpath/module path hoặc context loader khác | So sánh lệnh chạy, classpath/module path và kết quả `getClassLoader()` |

Để debug, ưu tiên kiểm tra **object identity** của loader và class, ví dụ:

~~~java
System.out.println(Example.class.getClassLoader());
System.out.println(Example.class.getClassLoader()
        == ClassLoader.getSystemClassLoader());
~~~

Đây chỉ là mã minh họa khi đã có class `Example`. Không dùng chuỗi `toString()` của loader làm định danh ổn định: tên hiển thị và cấu trúc của lớp triển khai phụ thuộc runtime. Trong môi trường ứng dụng thực tế, còn nên kiểm tra tên module, `Class.getModule()`, và nguồn bytecode nếu liên quan.

## 10. Câu hỏi tự kiểm tra

**1. Vì sao `ClassLoader.getSystemClassLoader().loadClass("java.lang.String")` vẫn trả class có `getClassLoader() == null`?**

Vì yêu cầu nạp được system loader **ủy quyền** cho ancestor. Bootstrap định nghĩa `String`, còn system loader chỉ đưa ra class đã được nạp/định nghĩa đúng nơi.

**2. Tại sao `ClassLoader.getPlatformClassLoader().getParent() == null` không chứng minh rằng platform loader không tồn tại?**

`getParent()` trả về loader cha; `null` đại diện bootstrap trong cấu hình tiêu chuẩn. Bản thân platform loader vẫn là một instance mà `getPlatformClassLoader()` trả về.

**3. Dùng loader nào làm parent khi plugin cần implements một interface riêng của host?**

Thường là `HostApi.class.getClassLoader()`, hoặc loader khác được chứng minh có thể tìm đúng **cùng interface runtime type**. Không mặc nhiên chọn platform loader vì nó thường không thấy classpath host.

**4. Vì sao không ép kiểu system loader sang `URLClassLoader` trên Java 17?**

API chỉ hứa trả một `ClassLoader`. Java 9+ sử dụng triển khai loader tích hợp khác với Java 8, nên cast có thể thất bại.

**5. Nếu `getSystemClassLoader()` trả về custom loader, quan hệ parent mặc định có còn là “system → platform” trực tiếp không?**

Không chắc. Custom loader nhận default system loader làm parent; sơ đồ có thể là **custom system → default system → platform → bootstrap**.

**6. Thread context loader có luôn bằng system loader không?**

Không. Luồng `main` thường bắt đầu với system loader, nhưng framework, application server và code ứng dụng có thể đặt context loader khác. Muốn biết class do ai định nghĩa, dùng `SomeClass.class.getClassLoader()`.

## 11. Nguồn tham khảo

- [Java SE 17: ClassLoader — Built-in Class Loaders, getPlatformClassLoader và getSystemClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17: Class — getClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getClassLoader())
- [Java SE 17: ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [Java SE 17: Class.forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader))

**Ghi nhớ:** `getPlatformClassLoader()` lấy loader dành cho API/runtime nền tảng; `getSystemClassLoader()` lấy loader hệ thống thường dùng cho ứng dụng. Quan hệ parent giúp delegation, nhưng **loader được gọi** không nhất thiết là **loader định nghĩa class**. Khi thiết kế plugin, chọn parent dựa vào class/interface mà plugin thực sự cần chia sẻ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-007">Extension class loader trong Java 8 và thay đổi từ Java 9</a>

<details>
<summary>Xem chi tiết</summary>

# Extension Class Loader trong Java 8 và thay đổi từ Java 9

> **Ngữ cảnh:** Java Core → ClassLoader → Loader tích hợp. Bài này giải thích **extension class loader của Java 8**, cơ chế tìm JAR trong extension directories, và vì sao từ **Java 9** cơ chế đó bị xóa trong khi **platform class loader** vẫn tồn tại. Trọng tâm là cách đọc hệ phân cấp loader, chẩn đoán lỗi và chuyển một ứng dụng từ JDK 8 sang JDK 9+ mà không lệ thuộc chi tiết nội bộ HotSpot.

## 1. Câu trả lời cần nhớ trước tiên

**JDK 8:** Java cung cấp cơ chế *installed/optional extensions*. Đặt JAR vào một **extension directory** của Java runtime cho phép JDK tìm thấy thư viện ngoài mà ứng dụng không cần liệt kê JAR ấy trên application class path. Một built-in loader thường gọi là **extension class loader** đóng vai trò nạp chúng.

**JDK 9 trở đi:** Cơ chế **extension directories** đã bị **loại bỏ**. Cách cũ như đặt JAR vào `lib/ext` hoặc truyền `-Djava.ext.dirs=...` không còn được hỗ trợ. Thư viện ứng dụng cần được khai báo rõ bằng **class path**, **module path** hoặc một cơ chế nạp do ứng dụng quản lý.

**Nhưng:** Bản thân vị trí loader ở giữa bootstrap và application **không biến mất**. Kể từ Java 9, vai trò built-in loader này được gọi là **platform class loader**, cung cấp công khai qua `ClassLoader.getPlatformClassLoader()`. Nó phục vụ các lớp nền tảng/JDK theo thiết kế runtime modular, **không tiếp tục làm trình quét JAR trong `lib/ext`**.

Đây là ba điều khác nhau cần tách bạch:

1. **Tên/role loader:** Extension loader (JDK 8) → platform loader (JDK 9+).
2. **Cơ chế triển khai thư viện tùy ý:** `java.ext.dirs` / `lib/ext` (JDK 8) → **bị xóa** từ JDK 9.
3. **Bộ tổ chức class của JDK:** `rt.jar` cùng các JAR nội bộ (JDK 8) → **modular runtime image** và JPMS (JDK 9+).

Đổi tên loader **không** có nghĩa `lib/ext` đã chuyển sang một thư mục “platform/ext” mới.

## 2. Java 8: extension class loader ở đâu trong delegation chain?

Với launcher và loader built-in mặc định của **Oracle/OpenJDK 8**, mô hình đơn giản:

~~~text
                       Bootstrap Class Loader
                    (thường biểu diễn bằng null)
                                |
                                v
              Extension Class Loader (ExtClassLoader)
                 JRE_HOME/lib/ext và extension dirs
                                |
                                v
            Application / System Class Loader (AppClassLoader)
                  -cp / -classpath / CLASSPATH
                                |
                                v
                 Custom loaders do ứng dụng tạo
~~~

Mũi tên trong hình đi từ **parent** xuống **child**; khi cần tìm lớp, phương thức `loadClass` mặc định thường *delegate lên parent trước* rồi mới `findClass` ở child. Do đó search precedence điển hình của launcher JDK 8 là:

1. Bootstrap: nhiều class nền tảng từ JDK/JRE, ví dụ phần lớn class lõi trong `rt.jar`.
2. Extension loader: thư viện được cài ở extension directories.
3. Application loader: class của ứng dụng và dependency trên `-cp`.

**Hệ quả:** Nếu một class cùng binary name nằm cả trong extension JAR và application class path, bản mà parent extension loader tìm thấy có thể **che** bản ứng dụng mong đợi. Đây là một nguồn “chạy đúng trên máy A, sai trên máy B” vì thư viện được cài sẵn ở JRE khác nhau.

### 2.1. Extension directories là gì?

Trong cách đóng gói JDK/JRE 8 truyền thống, JAR được đặt vào các vị trí như:

- `<JRE_HOME>/lib/ext` (trên nhiều cài đặt JDK 8, Java runtime nằm dưới `<JDK_HOME>/jre`).
- Extension directory dùng chung trên một số hệ điều hành/distribution.
- Vị trí do thuộc tính hệ thống `java.ext.dirs` quy định theo cơ chế Java 8.

Tên “installed extension” biểu thị cách triển khai **mức runtime**, không phải khái niệm extension giống browser plugin hay Java module. Ứng dụng không cần truyền `-cp` riêng cho các JAR mà extension loader tìm được trong cấu hình runtime đó.

Hai cấu hình cần phân biệt:

| Cấu hình Java 8 | Ai thường tìm thấy? | Phạm vi ảnh hưởng |
| --- | --- | --- |
| `<JRE_HOME>/lib/ext/vendor.jar` | Extension loader | Những ứng dụng dùng cùng JRE có thể thấy thư viện; gây xung đột toàn runtime |
| `-Djava.ext.dirs=some/extdir` | Cơ chế extension loader của JDK 8 | Phụ thuộc đúng cơ chế JDK 8; không có ý nghĩa trên JDK 9+ |
| `java -cp app.jar:vendor.jar Main` | Application class loader theo launcher mặc định | Chỉ tiến trình được cấu hình class path đó |
| Custom `URLClassLoader` nạp URL JAR | Loader do ứng dụng tạo | Do ứng dụng quản lý phạm vi, quyền và vòng đời |

Lưu ý path separator của `-cp`: trên Unix/Linux/macOS là dấu **`:`**, trên Windows là dấu **`;`**. Đừng sao chép nguyên lệnh giữa hệ điều hành mà bỏ qua dấu này.

**Không nên chỉnh sửa thư mục JRE cài đặt chung để làm thí nghiệm.** Các bước thực hành ở phần sau dùng thư mục thư viện riêng trong workspace và chỉ chạy với một JDK 8 kiểm soát được.

### 2.2. Extension loader có phải là một class trong Java SE API?

Các class triển khai như `sun.misc.Launcher$ExtClassLoader` (JDK 8 HotSpot) là **chi tiết nội bộ**. Đặc tả API không yêu cầu mọi JVM giữ chính xác tên, package hay cấu trúc class đó. Dùng `getParent()`, `getClassLoader()` và API chuẩn để quan sát hành vi; không import `sun.misc.Launcher` và không so sánh chuỗi `getClass().getName()` để quyết định logic.

Trong JDK 8, public API `ClassLoader` **chưa có** `getPlatformClassLoader()`. Thử gọi method ấy trong mã được biên dịch cho `--release 8` sẽ không hợp lệ vì API này ra đời từ Java 9.

## 3. Vì sao extension mechanism bị xóa từ Java 9?

Java 9 đưa vào **Java Platform Module System (JPMS)** với tập module có tên, quan hệ `requires` / `exports` và runtime image modular. Thay đổi này có nhiều động cơ: mô tả dependency rõ ràng, đóng gói và kiểm soát API nội bộ, thiết lập runtime image có thể tùy chọn module, hỗ trợ công cụ như `jlink`.

Extension mechanism cũ tạo ra một “kênh cài đặt ngầm”:

- JAR tự động được tìm qua JRE mà không xuất hiện trong dependency/app launch configuration.
- Phiên bản JAR có thể khác trên từng máy, làm kết quả class loading khó tái hiện.
- Extension JAR có thể chiếm precedence trước JAR của ứng dụng.
- Sao chép JAR vào image JDK/JRE phá vỡ tính bất biến của runtime và làm việc quản lý cập nhật/phát hành phức tạp.

Từ **JDK 9**, extension directories không còn là nơi runtime tự động quét để nạp thư viện. Cấu hình bằng `java.ext.dirs` bị từ chối, thay vì ngầm giữ hành vi cũ. Điều này là thay đổi **được công bố trong hướng dẫn migration**, không chỉ một tối ưu tùy ý ở HotSpot.

### 3.1. Đối chiếu trước và sau

| Chủ đề | Java 8 | Java 9+ |
| --- | --- | --- |
| Built-in loader trung gian | Thường gọi `ExtClassLoader` | **Platform class loader** |
| API công khai nhận loader trung gian | Chủ yếu quan sát `getParent()` trong cấu hình mặc định; không có `getPlatformClassLoader()` | `ClassLoader.getPlatformClassLoader()`, từ Java 9 |
| Cơ chế tự tìm JAR trong `lib/ext` | Có | **Đã xóa** |
| `-Djava.ext.dirs` | Cơ chế legacy của JDK 8 | Không được hỗ trợ; launcher báo lỗi |
| Hệ class nền tảng | Nhiều class trong `rt.jar` và JAR JRE | Các **modules** trong modular runtime image |
| Nguồn URL thường thấy của `java/lang/Class.class` | `jar:file:.../rt.jar!...` | `jrt:/java.base/java/lang/Class.class` |
| Application loader | Trên HotSpot 8 thường là một `URLClassLoader` | Trên OpenJDK 9+ là loại nội bộ khác, **không mặc định là `URLClassLoader`** |
| Thư viện ngoài ứng dụng | Class path hoặc cài extension JAR | Class path, module path hoặc custom loader |
| Khả năng truy cập code trong module | Chưa có JPMS | Chịu thêm kiểm tra readability, exports, opens |
| Override phần chuẩn của JDK | Có cơ chế endorsed standards cũ, tách khỏi extensions | Endorsed directories cũng bị xóa; một số module được nâng cấp qua **upgrade module path** |

**Chú ý:** Không suy ra “Java 9 đổi toàn bộ Java runtime thành modules nên mọi JAR đều là named module”. JAR chạy trên **class path** vẫn thuộc **unnamed module**; JAR đặt trên **module path** có thể là **automatic module** hoặc explicit named module tùy nội dung và cách resolve.

### 3.2. “Extension loader được giữ lại dưới tên platform loader” nghĩa chính xác là gì?

Oracle migration guide giải thích: extension class loader được **đổi tên thành platform class loader** trong hệ built-in loaders. Câu này nói về **vai trò lớp loader** chứ không hồi sinh cơ chế “JAR cài vào `lib/ext` được tìm tự động”.

Mô hình Java 9+ điển hình:

~~~text
Bootstrap loader (VM built-in, API có thể trả null)
       |
       v
Platform loader   <-- ClassLoader.getPlatformClassLoader()
       |
       v
Application/System loader <-- ClassLoader.getSystemClassLoader()
       |
       v
Custom loader nếu ứng dụng tạo
~~~

Các điểm chính:

- Mọi **platform class** đều phải **visible** qua platform loader, nhưng không nhất thiết do platform loader **define**. Một số class thuộc bootstrap loader; API `getClassLoader()` của những class này trả `null`.
- Bootstrap trong JDK 9+ định nghĩa ít class/module hơn mô hình JDK 8. Nếu custom loader trước kia chọn parent `null` và ngầm tin rằng nó nhìn thấy **toàn bộ** class nền tảng, giả định đó có thể sai sau migration.
- Platform loader có thể phải thực hiện **delegation đặc biệt** cho một số module đã được nâng cấp. Vì vậy sơ đồ parent-child chỉ là mô hình nền; không nên khẳng định mọi class chỉ được nhìn thấy qua một đường parent-first duy nhất.
- Runtime module readability/access và classloader delegation là hai cơ chế **khác nhau**. Class có thể tìm được nhưng caller vẫn không được phép truy cập package của module ấy.

## 4. Hai chương trình quan sát loader — không phụ thuộc tên internal class

### 4.1. Một source chạy được trên JDK 8 và JDK 9+

Tệp **`LoaderCompatibility.java`**, không dùng trực tiếp API mới tại compile time:

~~~java
public final class LoaderCompatibility {
    public static void main(String[] args) {
        ClassLoader system = ClassLoader.getSystemClassLoader();
        ClassLoader caller = LoaderCompatibility.class.getClassLoader();

        boolean hasPlatformApi;
        try {
            ClassLoader.class.getMethod("getPlatformClassLoader");
            hasPlatformApi = true;
        } catch (NoSuchMethodException ex) {
            hasPlatformApi = false;
        }

        System.out.println("platform API available: " + hasPlatformApi);
        System.out.println("caller uses system: " + (caller == system));
        System.out.println("system has parent: " + (system.getParent() != null));
        System.out.println("String bootstrap: "
                + (String.class.getClassLoader() == null));
    }
}
~~~

Biên dịch trên **JDK 8**:

~~~bash
javac LoaderCompatibility.java
java -cp . LoaderCompatibility
~~~

Hoặc biên dịch trên **JDK 9+** nhưng giữ API/bytecode mục tiêu Java 8:

~~~bash
javac --release 8 LoaderCompatibility.java
java -cp . LoaderCompatibility
~~~

Output kỳ vọng với **JDK 8 Oracle/OpenJDK mặc định và class path**:

~~~text
platform API available: false
caller uses system: true
system has parent: true
String bootstrap: true
~~~

Output kỳ vọng khi chạy **cùng class đã biên dịch** trên **JDK 17/21/25 OpenJDK mặc định và class path**:

~~~text
platform API available: true
caller uses system: true
system has parent: true
String bootstrap: true
~~~

Dòng đầu khác nhau vì `ClassLoader.getPlatformClassLoader()` chỉ xuất hiện từ Java 9. Ba dòng còn lại kiểm tra đặc tính của **môi trường chạy mặc định**; nếu launch bằng custom system loader, module layer hay môi trường container đặc biệt, không xem chúng là output bất biến của mọi JVM.

**Vì sao dùng reflection?** Viết thẳng `ClassLoader.getPlatformClassLoader()` trong source này sẽ khiến `javac --release 8` không biên dịch được. Reflection ở đây chỉ dùng để **phát hiện tồn tại phương thức**, không có nhu cầu mở quyền truy cập module nội bộ.

### 4.2. Sử dụng API được hỗ trợ từ JDK 9

Tệp **`PlatformLoaderProbe.java`** (chỉ biên dịch từ Java 9 trở lên):

~~~java
public final class PlatformLoaderProbe {
    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader application = ClassLoader.getSystemClassLoader();

        System.out.println("platform exists: " + (platform != null));
        System.out.println("app sees platform as parent: "
                + (application.getParent() == platform));
        System.out.println("platform sees String: "
                + (platform.loadClass("java.lang.String") == String.class));
        System.out.println("String defined by bootstrap: "
                + (String.class.getClassLoader() == null));
    }
}
~~~

~~~bash
javac PlatformLoaderProbe.java
java -cp . PlatformLoaderProbe
~~~

Output điển hình với **OpenJDK 17/21/25, system loader mặc định**:

~~~text
platform exists: true
app sees platform as parent: true
platform sees String: true
String defined by bootstrap: true
~~~

Dòng thứ ba và tư minh họa sự khác nhau giữa **visible to** và **defined by**. Platform loader **nhìn thấy `java.lang.String` qua bootstrap**, chứ không tự định nghĩa một bản `String` khác. Chưa có cơ sở nào để khẳng định “mọi class Java SE đều do platform loader định nghĩa”.

Không cast `platform` hay `application` về `URLClassLoader` để lấy danh sách URL. Trên nhiều JDK 9+ HotSpot cast sẽ thất bại:

~~~java
// Phản ví dụ: KHÔNG dùng trong mã chuyển đổi JDK 9+.
java.net.URLClassLoader urls =
        (java.net.URLClassLoader) ClassLoader.getSystemClassLoader();
~~~

Nếu cần class path đã truyền cho launcher, hãy lưu cấu hình launch/build của ứng dụng. Nếu thực sự cần nạp JAR theo URL động, ứng dụng có thể **tự tạo** `URLClassLoader` với parent phù hợp; đây là một loader **do ứng dụng sở hữu**, không phải built-in system loader.

## 5. Thực hành migration: đưa một extension JAR sang class path

Ví dụ có một thư viện nhỏ trong package `legacy.greeting` và chương trình client. Cấu trúc thư mục minh họa:

~~~text
demo/
├─ src/
│  ├─ legacy/
│  │  └─ greeting/
│  │     └─ Greeting.java
│  └─ LegacyClient.java
└─ build/             (được tạo bởi lệnh biên dịch)
~~~

Tệp **`src/legacy/greeting/Greeting.java`**:

~~~java
package legacy.greeting;

public final class Greeting {
    private Greeting() { }

    public static String message() {
        return "hello from library";
    }
}
~~~

Tệp **`src/LegacyClient.java`**:

~~~java
import legacy.greeting.Greeting;

public final class LegacyClient {
    public static void main(String[] args) {
        System.out.println(Greeting.message());

        ClassLoader library = Greeting.class.getClassLoader();
        ClassLoader application = LegacyClient.class.getClassLoader();
        System.out.println("same defining loader: "
                + (library == application));
    }
}
~~~

### 5.1. Chuẩn bị JAR và class của client

Các lệnh sau chạy từ thư mục `demo` trong **POSIX shell**. Để thử cả Java 8 và Java 9+, hãy xây JAR và client bằng JDK 8 để không bị lỗi bytecode version khi chạy bản cũ:

~~~bash
mkdir -p build/libclasses build/clientclasses build/extensions
javac -d build/libclasses src/legacy/greeting/Greeting.java
jar cf build/greeting.jar -C build/libclasses .
javac -cp build/greeting.jar -d build/clientclasses src/LegacyClient.java
~~~

Các đường dẫn tương đối có thể thay bằng đường dẫn tương ứng trong PowerShell hoặc Gradle/Maven. Bước trên chỉ tạo artifact của **bài thí nghiệm**, không đụng tới `JAVA_HOME`.

### 5.2. Java 8: dùng extension directory riêng, có kiểm soát

Trong môi trường thực hành Java 8, sao chép `greeting.jar` vào `build/extensions`. Sau đó chạy:

~~~bash
cp build/greeting.jar build/extensions/
java -Djava.ext.dirs=build/extensions -cp build/clientclasses LegacyClient
~~~

Output kỳ vọng với **Oracle/OpenJDK 8 launcher mặc định**:

~~~text
hello from library
same defining loader: false
~~~

**Giải thích:** client được application loader định nghĩa; `Greeting` được extension loader (parent của application loader trong JDK 8) tìm thấy. Hai lớp không có cùng **defining loader**. Cách này cố ý mô phỏng cơ chế extension cũ mà không sao chép thư viện vào JRE chia sẻ.

Nếu chuyển dòng chạy trên sang JDK 9+, launcher sẽ **từ chối** `-Djava.ext.dirs=...`. Đừng chẩn đoán nhầm là `ClassNotFoundException` trong ứng dụng: lỗi có thể xảy ra **trước cả khi `main` chạy**.

### 5.3. Java 9+: chuyển JAR sang application class path

Sau khi **bỏ** property cũ, chạy với hai mục trên class path.

Linux/macOS:

~~~bash
java -cp "build/clientclasses:build/greeting.jar" LegacyClient
~~~

Windows PowerShell / Command Prompt:

~~~powershell
java -cp "build/clientclasses;build/greeting.jar" LegacyClient
~~~

Output kỳ vọng trên JDK 9+ launcher mặc định:

~~~text
hello from library
same defining loader: true
~~~

Không có extension mechanism: client và library đều nằm trong **application class path**, nên cùng system/application loader trong cấu hình thông thường. **API và hành vi nghiệp vụ vẫn đúng** dù class loader trả về đã đổi.

**Đừng ép hai loader phải khác nhau sau migration.** Thay đổi role/identity của loader khi chuyển deployment mode là điều mong đợi. Logic nghiệp vụ không nên phụ thuộc một JAR được nạp bởi `ExtClassLoader` để hoạt động.

### 5.4. Bài học thực tế từ ví dụ

- Thư viện dùng chung không nên được “cài toàn hệ thống” một cách bí mật; project nên khai báo dependency có version.
- Parent-first khiến extension JAR cũ có thể thắng phiên bản trên application class path trong JDK 8; di chuyển sang class path có thể làm thay đổi bản class được chọn khi JAR trùng lặp.
- Khi class identity đổi, `instanceof`, cast, serialized metadata, reflective lookup và caches có thể cho kết quả khác; kiểm tra dữ liệu trao đổi qua ranh giới loader.
- Nếu build bằng JDK mới rồi chạy bằng JDK 8, phải đặt target tương thích (`javac --release 8` trên compiler hỗ trợ) **và** bảo đảm dependency/API tương thích. JDK 8 hiểu class file major **52**, trong khi JDK 17 mặc định tạo major **61**.

## 6. Ba cách triển khai thư viện đúng trên Java 9+

### Cách A — Class path: phù hợp ứng dụng JAR legacy

Lệnh chạy rõ JAR chứa dependency:

~~~bash
# Linux/macOS
java -cp "app.jar:libs/*" com.example.Main
~~~

~~~powershell
# Windows
java -cp "app.jar;libs/*" com.example.Main
~~~

Khi viết chương trình thực, thay `com.example.Main` bằng main class trong ứng dụng. Lệnh dùng **wildcard class path** để lấy JAR nằm trực tiếp trong `libs`; nó **không quét đệ quy** các thư mục con. Build tool như Gradle/Maven thường quản lý dependency và tạo class path thay cho việc viết thủ công.

Ưu điểm: chuyển đổi tương đối trực tiếp từ JDK 8; JAR không bắt buộc có `module-info.class`. Hạn chế: các class trên class path cùng nằm trong unnamed module tương ứng loader; không nhận ngay strong encapsulation của named modules.

### Cách B — Module path: cho ứng dụng dùng JPMS

Một JAR có explicit `module-info.class` được chạy như named module, ví dụ sau khi bạn đã xây `app.jar` và các dependency thích hợp:

~~~bash
java --module-path "mods:libs" \
     --module com.example.app/com.example.Main
~~~

Windows thay ký tự phân tách trong module path thành `;` (hoặc dùng cách build tool sinh lệnh tương ứng). Module name và main class ở lệnh trên biểu thị **cách viết cú pháp**; dự án thật phải dùng đúng module descriptor và tên package của mình.

Đối với JAR legacy không có module descriptor, Java có cơ chế **automatic modules** khi đặt JAR lên module path. Nhưng để module được **resolved**, ứng dụng phải tạo graph phù hợp (chẳng hạn `requires` hoặc cấu hình `--add-modules`); đặt một JAR bất kỳ lên module path **không** có nghĩa tất cả class tự động visible.

Đường classpath và module path không thay thế nhau một cách máy móc: package conflict, split packages, exports/opens, service provider và module readability đều cần kiểm tra khi chuyển.

### Cách C — Custom loader khi có nhu cầu plugin isolation

Nếu ứng dụng chủ động nạp plugin theo phiên bản/runtime, custom loader hoặc `URLClassLoader` do ứng dụng tạo vẫn có thể hợp lệ:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class PluginLoaderExample {
    public static void main(String[] args) throws Exception {
        URL jar = Path.of("plugins", "plugin.jar")
                      .toUri().toURL();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                new URL[] { jar },
                ClassLoader.getPlatformClassLoader())) {
            // Chỉ minh họa tạo loader và giải phóng resource.
            System.out.println(pluginLoader.getParent()
                    == ClassLoader.getPlatformClassLoader());
        }
    }
}
~~~

Tệp **`PluginLoaderExample.java`**, biên dịch/chạy với Java 17+:

~~~bash
javac PluginLoaderExample.java
java PluginLoaderExample
~~~

Output:

~~~text
true
~~~

Bài toán ở đây minh họa **parent được chọn bằng API công khai**; ví dụ **không yêu cầu** `plugins/plugin.jar` có tồn tại để tạo một URL. Nếu cần tải lớp từ JAR, ứng dụng phải cung cấp file thật, gọi `loadClass` hoặc dùng module layer, và quản lý vòng đời/các dependency.

**Lưu ý lớn:** Chọn `ClassLoader.getPlatformClassLoader()` làm parent cho plugin sẽ **không mặc nhiên cấp quyền thấy các interface do application loader định nghĩa**. Nếu plugin cần dùng chung SPI/interface của host, thiết kế ranh giới parent phù hợp (thường dùng loader nhìn thấy contract chia sẻ), tránh duplicate class identity. Chọn platform parent khi mục tiêu là chỉ kế thừa platform classes và bạn hiểu dependency cụ thể.

`URLClassLoader.close()` giải phóng tài nguyên có thể đóng; nó **không bảo đảm class unloading tức thời**. Thread context class loader, cache, threads, listeners và static registry có thể tiếp tục giữ loader.

## 7. Không nhầm extension, endorsed và upgrade module path

Trong JDK 8 từng tồn tại ít nhất **hai cơ chế cũ khác nhau**:

1. **Extension mechanism**: các JAR của thư viện/optional packages tại `lib/ext`, cấu hình `java.ext.dirs`. Đã xóa từ JDK 9; thay bằng explicit class path/module path hoặc custom loading theo nhu cầu.
2. **Endorsed standards override mechanism**: `lib/endorsed`, `java.endorsed.dirs`, dùng thay phiên bản một số standard APIs được phép. Cũng đã xóa từ JDK 9; khi cần **nâng cấp đúng module được Java cho phép**, tìm hiểu `--upgrade-module-path` theo JPMS.

`--upgrade-module-path` **không** phải “tên mới của `java.ext.dirs`”. Nó chỉ áp dụng trong mô hình module, theo quy tắc module nào upgradeable, không có nhiệm vụ quét JAR utility tùy ý. Tương tự:

- `--patch-module` là cơ chế can thiệp nội dung **một named module cụ thể**; không dùng thay dependency bình thường.
- `--add-exports` hỗ trợ quyền truy cập package public không export trong tình huống migration; **không thêm** class/JAR bị thiếu.
- `--add-opens` liên quan deep reflection theo module; **không** bật lại extension class loader.
- `-Xbootclasspath/a` nếu được JVM hỗ trợ trong một số tình huống không phải giải pháp thay thế tổng quát cho extension mechanism.

Đặt đúng vấn đề trước khi chọn option: “Không thấy JAR” là vấn đề **deployment/class path/module path**; “thấy class nhưng bị cấm truy cập” là vấn đề **module/access**.

## 8. Chi tiết cần biết khi nâng cấp từ Java 8 sang 9, 11, 17, 21+

| Bản JDK | Điểm quan trọng trực tiếp liên quan extension/class loading |
| --- | --- |
| **8** | Có extension directories và cơ chế `java.ext.dirs`. Loader trung gian thường gọi `ExtClassLoader`. Runtime truyền thống chứa `rt.jar`. |
| **9** | **Loại bỏ extension & endorsed mechanisms**; xuất hiện platform class loader, `getPlatformClassLoader()`, JPMS, modular image và `jrt:/`; system/application loader của OpenJDK không còn là `URLClassLoader`. |
| **10** | Tiếp tục mô hình JDK 9. Kiểm tra launch scripts cũ vì các flag/thư mục extension không hợp lệ. |
| **11** | Đã loại các module Java EE/CORBA theo JEP 320, nên một số class/API có trong JDK 8 không còn nằm trong JDK; không thể “sửa loader” để JDK 11 tự cung cấp lại những class đã bị loại. |
| **17** | JEP 403 áp dụng strong encapsulation đối với nhiều internal JDK APIs; mã reflection cũ có thể gặp `InaccessibleObjectException` — đây là vấn đề quyền truy cập module, tách biệt với cơ chế extension bị xóa từ 9. |
| **21, 25 và bản mới hơn** | Tiếp tục dùng built-in bootstrap/platform/application loaders với JPMS; không xây logic dựa vào tên internal class hoặc layout `lib/ext`. Các option công cụ, module graph và class file version cần được kiểm tra theo bản runtime cụ thể. |

**Cần tách phiên bản lúc biên dịch và chạy:** `ClassLoader.getPlatformClassLoader()` thuộc **API từ Java 9**, nên một class được biên dịch với API Java 8 không thể gọi trực tiếp. Ngược lại, việc class file tương thích Java 8 không bảo đảm mọi JAR/Java SE API tồn tại trên JDK mới. Migration phải kiểm tra cả API, module graph, thư viện và cấu hình start.

## 9. Chẩn đoán khi ứng dụng nâng JDK bị lỗi

### Triệu chứng A: JVM từ chối start vì `java.ext.dirs`

Ví dụ thông điệp được Oracle ghi nhận:

~~~text
<java-home>/lib/ext exists, extensions mechanism no longer supported; Use -classpath instead.
Error: Could not create the Java Virtual Machine.
~~~

Hoặc thông báo tương tự với `-Djava.ext.dirs=...`. Cách xử lý:

1. Tìm cấu hình trong start script, IDE VM options, Dockerfile, Gradle/Maven launch task, application server và biến môi trường liên quan.
2. **Bỏ** `-Djava.ext.dirs`, không tái tạo thư mục `lib/ext` trong image JDK mới.
3. Đưa JAR cần thiết vào dependency/class path/module path với phiên bản xác định.
4. Chạy lại bằng đúng runtime và kiểm tra loader thực định nghĩa class.

### Triệu chứng B: `ClassNotFoundException` / `NoClassDefFoundError` sau migration

Nguyên nhân có thể là JAR từng nằm trong extension directory nhưng không còn trên classpath/module path. `ClassNotFoundException` thường xảy ra khi explicit class lookup thất bại; `NoClassDefFoundError` có thể xảy ra khi bytecode cần class không được giải tại runtime hoặc có một nguyên nhân initialization/linkage khác. Đừng kết luận thiếu JAR chỉ từ một tên exception; đọc đầy đủ cause.

Kiểm tra artifact:

~~~bash
jar tf build/greeting.jar
java -version
java -verbose:class -cp "build/clientclasses:build/greeting.jar" LegacyClient
~~~

Mã `jar tf` liệt kê entries trong JAR; `-verbose:class` cho biết các sự kiện class loading trên nhiều JDK, nhưng format log phụ thuộc VM/version. Trong lệnh trên dấu `:` dành cho Linux/macOS; trên Windows đổi thành `;`.

JDK 9+ HotSpot hỗ trợ unified logging:

~~~bash
java -Xlog:class+load=info -cp "build/clientclasses:build/greeting.jar" LegacyClient
~~~

Dùng `java -Xlog:help` để xem tag và option VM hiện có. Đây là **công cụ chẩn đoán HotSpot**, không phải một phần giao diện ổn định của mọi JVM implementation.

### Triệu chứng C: Cast system loader sang `URLClassLoader` bị lỗi

`ClassCastException` sau upgrade thường do mã đã giả định `ClassLoader.getSystemClassLoader()` là `URLClassLoader` dựa trên HotSpot 8. Hướng xử lý: thay bằng API `ClassLoader`/`Class` khi chỉ cần nạp class/resource, hoặc tạo `URLClassLoader` riêng nếu cần URL dynamic. Không cố cast theo tên class JDK nội bộ.

### Triệu chứng D: Custom loader có parent `null` không thấy class platform

Ở JDK 8, một số mã dùng `new URLClassLoader(urls, null)` vì tin bootstrap đã thấy mọi platform class. JDK 9+ một số class nền tảng được platform loader định nghĩa chứ không phải bootstrap; chọn `ClassLoader.getPlatformClassLoader()` làm parent có thể phù hợp nếu cần nhìn thấy lớp platform. Nếu plugin cần thêm **host API**, phải chọn parent/contract phù hợp hơn.

### Triệu chứng E: `IllegalAccessError` hoặc `InaccessibleObjectException`

Có thể liên quan named modules, package exports/opens và strong encapsulation. Cần kiểm tra module path, `module-info.java` và reflective call. Chuyển `lib/ext` sang classpath **không tự giải quyết** mọi lỗi module access.

## 10. Checklist migration theo từng bước

1. **Khóa môi trường trước khi đổi:** ghi `java -version`, launcher command, `java.class.path`, build dependencies, JDK vendor, mã nguồn và image runtime đang dùng.
2. **Tìm extension assumptions:** `lib/ext`, `java.ext.dirs`, `-extdirs`, `ExtClassLoader`, `sun.misc.Launcher`, cast system loader sang `URLClassLoader` và hard-coded `rt.jar`.
3. **Lập inventory extension JAR:** tên, version, source, license, API public, các transitive dependencies và class/package overlap. Đừng đưa nguyên thư mục JDK 8 sang JDK mới.
4. **Chọn deployment mới:** class path cho ứng dụng legacy thông thường; module path khi có thiết kế JPMS; custom loader nếu thực sự quản lý plugin động.
5. **Sửa launcher/build:** bỏ `java.ext.dirs` và các tùy chọn đã xóa; khai báo dependency trong build; xác thực path separator và JARs được đóng gói cùng artifact deploy.
6. **Kiểm tra class identity:** nếu có plugin/framework, xem defining loader của interface chung, module và binary name; tránh trùng thư viện giữa host/plugin nếu chúng phải chia sẻ type.
7. **Kiểm tra tương thích bytecode:** chạy bằng JDK mục tiêu, inspect major version với `javap -verbose` khi thấy `UnsupportedClassVersionError`.
8. **Kiểm tra module access:** phân biệt missing dependency với access errors; dùng module config chính xác.
9. **Chạy lại integration tests:** đặc biệt service provider, JDBC drivers, app servers, reflection, serialization, resource lookup và nạp plugin.
10. **Triển khai tái lập:** runtime image cố định, dependency version cố định, start command rõ ràng; không yêu cầu người vận hành copy JAR thủ công vào JDK.

## 11. Câu hỏi tự kiểm tra

1. **Java 9 có xóa platform loader không?** Không. Nó có tên/API công khai mới; thứ bị xóa là cơ chế extension directories.
2. **Có thể đổi `-Djava.ext.dirs=libs` thành `-Djava.platform.dirs=libs` không?** Không có option API như vậy.
3. **Một platform class phải do platform loader định nghĩa?** Không. Nó có thể do bootstrap định nghĩa nhưng vẫn visible từ platform loader.
4. **Đặt `foo.jar` lên module path là đủ để ứng dụng chắc chắn thấy module?** Không. Module cần nằm trong graph được resolved, và package phải cho phép access.
5. **Vì sao ứng dụng Java 8 đọc đúng class nhưng JDK 9+ đọc class phiên bản khác?** Có thể do extension JAR từng đứng trước application class path; sau migration deployment/loader precedence đổi.
6. **JDK 9+ có thể dùng `URLClassLoader` cho plugin không?** Có, bằng cách **ứng dụng tự tạo loader**, không cast built-in application loader.
7. **Dùng parent `null` trong custom loader có bảo đảm thấy mọi class Java SE không?** Không; trên JDK modular, chọn platform parent nếu cần các class nền tảng mà bootstrap không define.
8. **`java.endorsed.dirs` có phải tên khác của `java.ext.dirs`?** Không. Đây là hai cơ chế legacy riêng, cùng bị loại từ JDK 9.
9. **Khi thấy `NoClassDefFoundError` có luôn là JAR missing?** Không; phải xem cause, class file compatibility và initialization/linking.
10. **Có nên viết unit test assert tên nội bộ `jdk.internal.loader.ClassLoaders$PlatformClassLoader`?** Không; kiểm tra hành vi và API được hỗ trợ.

## 12. Tài liệu gốc nên tham khảo

1. [Oracle JDK Migration Guide — New Class Loader Implementations, Removed Extension Mechanism](https://docs.oracle.com/en/java/javase/26/migrate/migrating-from-jdk-8-later-jdk-releases.html): xác nhận system loader mới, platform loader và cách xử lý `lib/ext` / `java.ext.dirs`.
2. [Oracle Java SE 8 Extension Mechanism](https://docs.oracle.com/javase/8/docs/technotes/guides/extensions/spec.html): mô tả optional packages/installed extensions và precedence cũ.
3. [Oracle Java Tutorial JDK 8 — Understanding Extension Class Loading](https://docs.oracle.com/javase/tutorial/ext/basics/load.html): mô hình bootstrap → extension → class path ở Java 8; **tài liệu lịch sử, không áp dụng triển khai thư mục `lib/ext` trên JDK mới**.
4. [`ClassLoader` Java SE 9 API](https://docs.oracle.com/javase/9/docs/api/java/lang/ClassLoader.html): tài liệu chuẩn đầu tiên có `getPlatformClassLoader()` và built-in loaders.
5. [JEP 261 — Module System](https://openjdk.org/jeps/261): module path, readability/access và thay đổi class loading để hỗ trợ JPMS.
6. [JEP 220 — Modular Run-Time Images](https://openjdk.org/jeps/220): image modular, `rt.jar` không còn như trước và các hệ quả với công cụ đọc class/resource.
7. [JDK 9 Release Notes](https://www.oracle.com/java/technologies/javase/9-relnotes.html): xác nhận removal flags và migration recommendations.
8. [JEP 320 — Remove Java EE and CORBA Modules](https://openjdk.org/jeps/320), [JEP 403 — Strongly Encapsulate JDK Internals](https://openjdk.org/jeps/403): các lỗi nâng JDK khác dễ bị nhầm thành lỗi classloader.

**Kết luận thực hành:** Khi nâng từ Java 8, hãy loại bỏ mọi đường cài JAR vào `lib/ext` và những giả định vào class loader nội bộ. Trên Java 9+, dùng **platform class loader** qua API được hỗ trợ khi cần delegation tới các lớp nền tảng, còn dependency của ứng dụng phải được mô tả rõ trong **class path/module path** hoặc trong custom loader của ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-008">Vì sao không nên ép application class loader thành URLClassLoader</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không nên ép application class loader thành URLClassLoader?

> **Phạm vi:** Java Core → ClassLoader → Loader tích hợp. Trọng tâm là giả định sai về kiểu cụ thể của *application/system class loader*, thay đổi từ JDK 9, hậu quả của phép ép kiểu, và các API an toàn để tra cứu tài nguyên hoặc nạp lớp từ đường dẫn riêng. Ví dụ sử dụng Java 17+.

## 1. Tình huống: đoạn code từng chạy trên JDK 8 nhưng lỗi trên Java hiện đại

Một đoạn code cũ có thể lấy application class loader bằng `ClassLoader.getSystemClassLoader()`, ép nó thành `URLClassLoader` rồi lấy danh sách URL hoặc cố thêm JAR:

```java
import java.net.URLClassLoader;

public class OldCode {
    public static void main(String[] args) {
        URLClassLoader loader =
                (URLClassLoader) ClassLoader.getSystemClassLoader();
        System.out.println(loader.getURLs().length);
    }
}
```

Biên dịch/chạy:

```bash
javac OldCode.java
java OldCode
```

Với bộ khởi chạy OpenJDK 21 thông thường, **phép ép kiểu ném `ClassCastException`** trước khi kịp gọi `getURLs()`. Không cần thiếu JAR hay sai đường dẫn: object do `getSystemClassLoader()` trả về đơn giản không phải một instance của `URLClassLoader`.

Vì sao code cũ đôi khi có vẻ hợp lệ? Trên một số JDK 8 (đặc biệt các bản Oracle/OpenJDK mặc định), application loader được triển khai bằng một subclass của `URLClassLoader`. Nhiều chương trình đã vô tình dựa vào chi tiết này. **Java SE API chưa bao giờ cam kết** system loader phải kế thừa `URLClassLoader`. Từ JDK 9, application loader tích hợp trên JDK dùng một lớp triển khai nội bộ khác; việc ép sang `URLClassLoader` theo thói quen không còn hoạt động.

**Kết luận thực hành:** biến cần dùng để tương tác với application loader thường phải có kiểu `ClassLoader`, và thao tác nên dựa trên hợp đồng `ClassLoader`. Chỉ dùng các tính năng riêng của `URLClassLoader` khi bạn **thực sự đã tạo hoặc được cung cấp** một instance của loại loader ấy.

## 2. Phân biệt tên gọi với kiểu runtime

Có ba khái niệm dễ bị nhầm:

- **Application (app) class loader tích hợp:** loader mặc định mà bộ khởi chạy JDK dùng để tải lớp ứng dụng từ các nguồn phù hợp (classpath và/hoặc application modules theo cấu hình).
- **System class loader:** object trả về từ `ClassLoader.getSystemClassLoader()`; thường là app loader cho chương trình chuẩn, **có thể bị thay thế** bằng custom loader qua thuộc tính `java.system.class.loader`.
- **`java.net.URLClassLoader`:** một lớp API công khai **có khả năng tải class/resource từ chuỗi URL** trỏ đến directory hoặc JAR. Đây là **một implementation cụ thể** của `ClassLoader`, không đồng nghĩa với mọi application loader.

Định hướng kiểu:

```text
java.lang.ClassLoader                      (API khái quát)
       |
       +-- java.security.SecureClassLoader
                   |
                   '-- java.net.URLClassLoader       (một subclass cụ thể)

ClassLoader.getSystemClassLoader()
       |
       '-- trả về một ClassLoader do runtime quy định
           (không có bảo đảm instanceof URLClassLoader)
```

Sơ đồ chỉ diễn tả **cấu trúc kế thừa API và kiểu trả về**, không minh họa cây parent delegation. Parent loader và quan hệ kế thừa class là hai vấn đề khác nhau.

Trên các JDK OpenJDK 9+ thường gặp, kiểu triển khai cụ thể có tên tương tự `jdk.internal.loader.ClassLoaders$AppClassLoader`. Tên package nội bộ đó dùng để chẩn đoán, **không phải lớp API công khai để ứng dụng import, ép kiểu hay truy cập qua reflection**.

### Vì sao JDK 9 thay đổi?

JDK 9 đưa vào Java Platform Module System (JPMS), cùng tổ chức lại các built-in class loader. Release Notes chính thức nêu rõ application loader không còn là instance của `URLClassLoader`; đồng thời extension loader cũ được thay bằng *platform class loader*. Runtime có thể tìm lớp từ classpath **và** môi trường module, nên suy diễn toàn bộ cơ chế class loading từ một mảng URL là không đầy đủ.

Bảng so sánh:

| Môi trường | Đặc điểm thường gặp | Có thể ép system loader thành `URLClassLoader`? |
| --- | --- | --- |
| Oracle/OpenJDK 8 mặc định | App loader từng là subclass của `URLClassLoader` | Có thể thành công trong cấu hình đó, nhưng vẫn phụ thuộc implementation. |
| JDK 9+ mặc định (Oracle/OpenJDK) | Built-in app loader không phải `URLClassLoader` | **Không**; ép kiểu gây `ClassCastException`. |
| JDK 9+ có `-Djava.system.class.loader=...` | System loader là implementation do ứng dụng chỉ định | Phụ thuộc đúng instance được tạo; **không thể giả định**. |
| Runtime/launcher chuyên biệt | Cơ chế loader có thể được tùy chỉnh | Dựa vào API/contract của môi trường thực tế. |

Không nên diễn đạt “mọi Java 9+ đều tuyệt đối không có `URLClassLoader`”: **bạn vẫn có thể tự tạo `URLClassLoader`** và dùng bình thường. Thay đổi nói tới kiểu của **application loader tích hợp mặc định**.

## 3. Tại sao ép kiểu là lỗi thiết kế, kể cả khi tình cờ chạy được?

### 3.1. `ClassCastException` lúc runtime

Ép kiểu `(URLClassLoader) someLoader` chỉ hợp lệ nếu `someLoader instanceof URLClassLoader`. Kiểu tĩnh mà method khai báo trả về là `ClassLoader` **không tạo ra quan hệ kế thừa ngược**.

Vì compiler thấy `URLClassLoader` là subclass hợp lệ của `ClassLoader` nên đoạn cast có thể **biên dịch thành công** trên Java 17 hoặc 21. Tuy nhiên, khi chạy, JVM kiểm tra kiểu thật của object và có thể ném `ClassCastException`. Đây là lỗi kiểu runtime, không phải lỗi `ClassNotFoundException` hay `NoClassDefFoundError` do thiếu dependency.

### 3.2. Lấy danh sách URL không phải cách chuẩn để biết mọi nơi JVM tìm class

`URLClassLoader.getURLs()` chỉ nói về các URL của **instance URLClassLoader cụ thể**. Nó không phải API hỏi “toàn bộ classpath/module path của JVM” hay “tất cả class có thể được tải”, và không cung cấp inventory các module, các nguồn được parent delegate, hoặc tài nguyên sinh động.

`System.getProperty("java.class.path")` cho một **thuộc tính khởi động** hữu ích khi chẩn đoán classpath, nhưng cũng không phải phép liệt kê đầy đủ mọi nguồn class/resource trong ứng dụng dùng modules, manifest `Class-Path`, custom class loaders hoặc container. Không coi nó là bản sao khả biến của runtime classpath.

### 3.3. Cố thêm JAR vào application loader bằng API không được bảo đảm

`URLClassLoader.addURL(URL)` là **protected**, không phải method public dành cho caller tùy ý mở rộng loader hiện có. Code cũ đôi khi dùng subclass, reflection hoặc truy cập implementation nội bộ để thêm đường dẫn vào system loader.

Trên Java 9+:

- Built-in app loader không có nghĩa vụ cung cấp method hoặc cấu trúc dữ liệu của `URLClassLoader`.
- Strong encapsulation của JPMS hạn chế reflection vào các package nội bộ JDK; ép `setAccessible(true)` không biến một API nội bộ thành hợp đồng bền vững.
- Cờ kiểu `--add-opens` để “lách” truy cập không sửa được giả định **sai loại object** và có thể tạo sự phụ thuộc khó bảo trì.
- Java SE/JDK không cung cấp **API ứng dụng chung** để tùy ý mở rộng application classpath trong lúc runtime đang chạy.

Nếu bài toán thật sự là nạp plugin/JAR động, hãy sở hữu một loader riêng hoặc dùng cơ chế plugin/agent được thiết kế cho mục đích ấy.

## 4. Kiểm tra runtime đúng cách: nhìn loại object mà không ép bừa

Tạo file `InspectLoader.java`:

```java
import java.net.URLClassLoader;

public class InspectLoader {
    public static void main(String[] args) {
        ClassLoader system = ClassLoader.getSystemClassLoader();

        System.out.println("system is URLClassLoader: "
                + (system instanceof URLClassLoader));
        System.out.println("system loader type: "
                + system.getClass().getName());
    }
}
```

Chạy:

```bash
javac InspectLoader.java
java InspectLoader
```

Đầu ra **đã quan sát trên OpenJDK 21 mặc định**:

```text
system is URLClassLoader: false
system loader type: jdk.internal.loader.ClassLoaders$AppClassLoader
```

Dòng thứ hai là **tên chi tiết implementation**, có thể khác trên các JVM/cấu hình. Dòng thứ nhất cũng có thể khác nếu system loader được tùy biến; ví dụ minh họa tình huống thường gặp, không phải một API guarantee trên mọi môi trường.

Nếu nhận `ClassLoader` từ API không kiểm soát, pattern matching `instanceof` của Java 17 cho phép dùng khả năng `URLClassLoader` **chỉ khi có**:

```java
// Đoạn minh họa đặt trong method có biến ClassLoader loader:
if (loader instanceof URLClassLoader urlLoader) {
    for (var url : urlLoader.getURLs()) {
        System.out.println(url);
    }
} else {
    System.out.println("Loader does not expose URLClassLoader APIs");
}
```

Đoạn này **không phải chương trình độc lập** vì `loader` là biến trong ngữ cảnh bên ngoài. Đây là biện pháp tương thích khi một interface cho phép nhiều loại loader; nó **không** phải cách chung để tìm classpath hay thêm JAR. Khi không phải `URLClassLoader`, nên dùng API phù hợp với mục tiêu thay vì cố ép.

## 5. Chọn API theo nhu cầu thực sự

| Bạn cần làm gì? | API / cách phù hợp | Tránh |
| --- | --- | --- |
| Nạp lớp bằng tên đã biết | `Class.forName(binaryName)`, overload có loader, hoặc `ClassLoader.loadClass(binaryName)` | Suy ra loader phải là `URLClassLoader`. |
| Tìm tài nguyên trong những nguồn loader nhìn thấy | `loader.getResource(name)` / `getResources(name)` / `getResourceAsStream(name)` | Ép loader rồi tự ghép đường dẫn URL/JAR. |
| Đọc file cấu hình trong cùng JAR của một lớp | `SomeClass.class.getResourceAsStream("/config.properties")` | Giả định tài nguyên là `java.io.File` trên đĩa. |
| Chẩn đoán command-line classpath | `System.getProperty("java.class.path")` cùng launch command/build metadata | Coi chuỗi ấy là toàn bộ module graph và dynamic class loading. |
| Nạp JAR/directory mở rộng lúc chạy | **Tạo `URLClassLoader` riêng** có URL và parent thích hợp; hoặc custom loader | Thay đổi system loader qua cast/reflection. |
| Tìm provider/plugin theo hợp đồng SPI | `ServiceLoader` với class loader phù hợp, module `uses/provides` khi dùng JPMS | Hardcode đường dẫn tới implementation rồi ép system loader. |
| Làm việc với module runtime | `ModuleLayer` và module APIs khi cần tải từ một cấu hình module mới | Đồng nhất module path với các URL của app loader. |

Một ví dụ tìm resource không cần biết loader là subclass nào:

```java
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ReadResource {
    public static void main(String[] args) throws Exception {
        try (InputStream stream =
                ReadResource.class.getResourceAsStream("/message.txt")) {
            if (stream == null) {
                System.out.println("Resource not found");
                return;
            }
            System.out.println(new String(
                    stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
```

Đây là **chương trình độc lập**, nhưng để in nội dung phải có file `message.txt` tại classpath root (ví dụ đặt cạnh `ReadResource.class` khi chạy từ thư mục chứa class). Nếu không có resource, stdout chính xác là:

```text
Resource not found
```

Tên resource dùng `/`; `Class.getResourceAsStream("/message.txt")` là đường dẫn tuyệt đối trong phạm vi tìm kiếm tài nguyên của lớp. API trả về `InputStream` để mã vẫn phù hợp khi tài nguyên nằm trong JAR, không phải file vật lý.

## 6. Nạp plugin đúng cách bằng URLClassLoader do ứng dụng sở hữu

Trong tình huống plugin nằm ở thư mục hoặc JAR ngoài classpath khởi động, ta có thể **tạo một `URLClassLoader` mới**. Loader này chủ động nhận URL và có vòng đời quản lý rõ ràng.

Cấu trúc ví dụ:

```text
plugin-src/
  plugin/
    Greeting.java
app-src/
  LoaderDemo.java
plugin-out/     (tạo bởi javac)
app-out/        (tạo bởi javac)
```

**File `plugin-src/plugin/Greeting.java`:**

```java
package plugin;

public class Greeting {
    public static String message() {
        return "Hello from plugin";
    }
}
```

**File `app-src/LoaderDemo.java`:**

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class LoaderDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of("plugin-out").toAbsolutePath();
        URL pluginUrl = directory.toUri().toURL();
        ClassLoader app = ClassLoader.getSystemClassLoader();

        try (URLClassLoader pluginLoader = new URLClassLoader(
                new URL[]{pluginUrl}, ClassLoader.getPlatformClassLoader())) {
            Class<?> type = Class.forName(
                    "plugin.Greeting", true, pluginLoader);
            String message = (String) type.getMethod("message").invoke(null);

            System.out.println("message: " + message);
            System.out.println("owned loader: "
                    + (type.getClassLoader() == pluginLoader));
            System.out.println("app loader unchanged: "
                    + (ClassLoader.getSystemClassLoader() == app));
        }
    }
}
```

Từ thư mục cha chứa `plugin-src` và `app-src`, chạy:

```bash
javac -d plugin-out plugin-src/plugin/Greeting.java
javac -d app-out app-src/LoaderDemo.java
java -cp app-out LoaderDemo
```

**Stdout chính xác trên OpenJDK 21 (đã biên dịch/chạy kiểm tra):**

```text
message: Hello from plugin
owned loader: true
app loader unchanged: true
```

Những điểm quan trọng:

1. `plugin-out` chứa `plugin/Greeting.class` và **không nằm trong `-cp app-out`**, nên application loader của ví dụ không trực tiếp nhìn thấy plugin qua launch classpath.
2. `directory.toUri().toURL()` tạo một `file:` URL kết thúc bằng `/` cho directory, phù hợp với `URLClassLoader`. Để nạp từ JAR, truyền `file:` URL của JAR thay vì URL thư mục.
3. Ta truyền `ClassLoader.getPlatformClassLoader()` làm parent để ví dụ chỉ cần Java platform APIs và tránh parent application loader nhận diện plugin trùng tên. Đây là **lựa chọn mô phỏng isolation cho ví dụ**.
4. `Class.forName(name, true, pluginLoader)` dùng loader được chỉ định để tìm class và yêu cầu initialization; `Class#getClassLoader() == pluginLoader` xác nhận **defining loader** ở ví dụ này.
5. `try-with-resources` gọi `URLClassLoader.close()` sau khi dùng để giải phóng các file/JAR handle do loader mở, giúp quản lý vòng đời. `close()` **không bảo đảm unload class đã nạp**; việc unload phụ thuộc khả năng JVM thu hồi class loader và các object/tham chiếu liên quan.
6. Chương trình không ép, thay đổi hoặc sửa internals của system loader. Việc tạo loader mới cho plugin là hành động tách biệt và được hỗ trợ qua API công khai.

### Chọn parent của plugin loader cho đúng

`URLClassLoader` mặc định thực hiện **parent-first delegation**: trước khi tự tìm lớp từ URL của mình, nó thường để parent thử tải class. Trong thực tế có hai tình huống:

- **Plugin độc lập chỉ dùng Java platform:** parent `ClassLoader.getPlatformClassLoader()` như ví dụ đủ cho `java.base` và các API Java SE phù hợp; hạn chế nhìn thấy lớp ứng dụng.
- **Plugin phải implement interface của host:** parent thường cần là loader nhìn thấy interface dùng chung, ví dụ `LoaderDemo.class.getClassLoader()`. Nếu host và plugin mỗi bên tự định nghĩa `com.example.Plugin` bằng loader khác nhau, object mang cùng *binary name* vẫn có thể **không cast được** qua interface của host.

Ví dụ cấu hình parent khi plugin cần host API (chỉ minh họa, không phải chương trình chạy độc lập):

```java
ClassLoader hostLoader = HostPluginApi.class.getClassLoader();
try (URLClassLoader pluginLoader =
        new URLClassLoader(pluginUrls, hostLoader)) {
    // Load implementation từ plugin; parent cung cấp cùng HostPluginApi.
}
```

Ở đoạn này `HostPluginApi` và `pluginUrls` là các symbol giả định thuộc hệ thống plugin đang phát triển. Đừng copy độc lập mà không định nghĩa chúng. Parent-first cũng có nghĩa nếu parent đã thấy một class cùng tên, parent có thể thắng plugin; cần thiết kế package boundary rõ ràng và kiểm soát trùng lớp, không âm thầm dựa vào thứ tự URL.

## 7. Built-in loader, classpath, module path và context loader

Khi debug, cần phân biệt một vài loader/nguồn lớp:

| Thành phần | Cách lấy/ý nghĩa | Lưu ý |
| --- | --- | --- |
| Bootstrap loader | `String.class.getClassLoader()` thường trả `null`. | `null` là cách Java API biểu diễn bootstrap loader, không phải lỗi. |
| Platform loader | `ClassLoader.getPlatformClassLoader()` (Java 9+). | Có thể nhìn thấy Java platform classes thông qua delegation; không kết luận class nào nhất định được định nghĩa trực tiếp bởi platform loader. |
| System/application loader | `ClassLoader.getSystemClassLoader()`. | Implementation-dependent; có thể custom qua `java.system.class.loader`. |
| Loader của một lớp cụ thể | `SomeClass.class.getClassLoader()`. | Thường đáng tin hơn giả định loader toàn cục khi tìm resource liên quan lớp. |
| Thread context class loader (TCCL) | `Thread.currentThread().getContextClassLoader()`. | Có thể khác system loader hoặc `null`; frameworks/SPI hay sử dụng nó để tìm provider. |

`ServiceLoader.load(MyService.class)` thường dùng TCCL theo API; `ServiceLoader.load(MyService.class, loader)` cho phép chọn loader chủ động. Trong ứng dụng modular, `uses` và `provides` hoặc module layer quyết định thêm yếu tố dịch vụ. **ServiceLoader giải bài toán khám phá provider**, còn `URLClassLoader` giải bài toán tìm class/resource từ các URL; không tráo hai mục đích cho nhau.

Với JPMS, named modules có ranh giới đọc module, package exports/opens và nguồn module path. Việc một loader có thể tìm thấy bytecode **chưa chắc** chương trình được quyền truy cập mọi class/member qua reflection. Không thể giải quyết mọi vấn đề access chỉ bằng cách thêm một URL.

## 8. Nếu thật sự cần mở rộng classpath ở runtime thì sao?

Đầu tiên phân loại yêu cầu:

**A. Chỉ muốn thêm dependency cố định khi khởi động.** Cấu hình launcher/build tool bằng `-cp`, `--class-path` hoặc `--module-path` trước khi khởi động JVM. Không cần class loader động.

**B. Muốn plugin mới được phát hiện sau startup.** Tạo một class loader ứng dụng kiểm soát riêng cho plugin và API giao tiếp với host; cần xét phiên bản, quyền truy cập, resource, đóng loader và unload. Không có bảo đảm mọi thư viện trên system loader tự nhận plugin mới.

**C. Muốn plugin có vòng đời, dependency và isolation đầy đủ.** Dùng mô hình plugin/SPI, `ServiceLoader`, hoặc xây/module layer thích hợp; với hệ thống nhiều plugin, thiết kế quản lý parent và class identity riêng.

**D. Muốn Java agent thay đổi classpath phục vụ instrumentation.** `java.lang.instrument.Instrumentation` có các API giới hạn như `appendToSystemClassLoaderSearch(JarFile)` trong ngữ cảnh agent hợp lệ. Đây là cơ chế dành cho agent, **không phải API chung của code ứng dụng** và không biến system loader thành `URLClassLoader`. Nó mang điều kiện riêng về class visibility và tìm class.

**E. Muốn nhìn danh sách nơi một class đang được nạp.** Sử dụng công cụ JVM phù hợp, ví dụ log class loading (`-Xlog:class+load=info` trên HotSpot), build/deploy metadata, `clazz.getProtectionDomain().getCodeSource()` khi có thể; code source có thể `null` và vẫn không đại diện đầy đủ cho mọi resource của loader. Không dựa một phép cast để đoán cả classpath.

## 9. Những lỗi phổ biến khi nâng cấp JDK hoặc viết framework

| Triệu chứng | Nguyên nhân khả dĩ | Cách xử lý |
| --- | --- | --- |
| `ClassCastException` giữa `AppClassLoader` và `URLClassLoader` | Ép system/app loader sang subclass không đúng kiểu. | Bỏ cast; dùng API `ClassLoader` hoặc tạo `URLClassLoader` riêng. |
| `InaccessibleObjectException` sau khi gọi `setAccessible(true)` | Truy cập trái phép implementation nội bộ do encapsulation. | Chuyển sang API công khai; không coi `--add-opens` là chiến lược thiết kế. |
| Tự sửa `java.class.path` nhưng `ClassNotFoundException` vẫn xảy ra | Đổi chuỗi system property không thay đổi nguồn tìm kiếm của loader đã thiết lập. | Cấu hình lúc startup hoặc dùng loader riêng. |
| Plugin nạp được nhưng cast về interface của host lỗi | Host và plugin có các bản class/interface do defining loader khác nhau. | Dùng một API chung từ parent loader được chia sẻ, phân tích class identity. |
| Resource chạy trong IDE nhưng không tìm được trong JAR | Giả định `file:` path hoặc đường dẫn tài nguyên sai. | Dùng `getResourceAsStream`; kiểm tra JAR contents và tên resource. |
| `NoClassDefFoundError` khi nạp plugin | Thiếu dependency ở loader/parent, hoặc class từng khởi tạo thất bại. | Đọc `Caused by`, dependency graph, loader boundaries và thời điểm initialization. |
| Plugin không unload dù gọi `close()` | Thread, static field, cache hoặc object vẫn giữ tham chiếu tới loader/class. | Kiểm tra vòng đời/references; `close()` chỉ đóng tài nguyên mở. |
| Không tìm thấy class trong named module dù có file | Module graph/loader/access khác suy đoán từ classpath. | Kiểm tra module configuration, layer, `exports`/`opens` và launch options. |

**Quy trình debug:** xác định chính xác loader của **class đang lỗi**, xem kiểu runtime bằng `getClass().getName()` để chẩn đoán (không để điều khiển logic), kiểm tra `instanceof` khi thực sự cần API subclass, kiểm tra module/classpath và resource. Sau đó chọn giải pháp theo mục tiêu: lookup, resource, plugin, SPI hay module.

## 10. Câu hỏi tự kiểm tra

1. **`ClassLoader.getSystemClassLoader()` khai báo trả về loại gì?**  
   `ClassLoader`. Không có contract Java SE rằng object runtime phải là `URLClassLoader`.
2. **Vì sao một cast từng chạy trên Oracle/OpenJDK 8 có thể lỗi trên Java 17/21?**  
   JDK 9 đã thay implementation built-in application loader, vốn không còn kế thừa `URLClassLoader`.
3. **`instanceof URLClassLoader` giải quyết được mọi yêu cầu tìm classpath không?**  
   Không. Nó chỉ kiểm tra khả năng của một instance; `getURLs()` không phản ánh mọi module, parent delegation hoặc loader khác.
4. **Nếu muốn tìm `config/app.properties` đóng gói trong JAR, nên dùng gì?**  
   `SomeClass.class.getResourceAsStream("/config/app.properties")` (hoặc loader-specific resource API), không ép class loader thành `URLClassLoader`.
5. **Nếu muốn nạp thư mục plugin sau khi app đã chạy, phải đổi system loader không?**  
   Không. Tạo và quản lý loader riêng hoặc thiết kế cơ chế plugin/module theo yêu cầu.
6. **Nếu plugin implement interface của host, nên chọn parent nào?**  
   Một parent có thể thấy **cùng định nghĩa interface** với host; thường loader định nghĩa host API, không mặc định chọn platform loader.
7. **`URLClassLoader.close()` có nghĩa là class đã unload ngay lập tức?**  
   Không. Nó đóng tài nguyên mở, còn class unloading phụ thuộc việc còn reachable references hay không.

## 11. Tài liệu chuẩn

- [Oracle JDK 9 Release Notes — Class Loader Changes](https://www.oracle.com/java/technologies/javase/9-relnotes.html): xác nhận application loader tích hợp không còn là `URLClassLoader` từ JDK 9.
- [Java SE 21 — `ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): kiểu trả về/hợp đồng của `getSystemClassLoader`, `getPlatformClassLoader`, `getResource` và delegation.
- [Java SE 21 — `URLClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): constructor URL/parent, `getURLs`, `addURL`, `close`.
- [JEP 261 — Module System](https://openjdk.org/jeps/261): bối cảnh JPMS và việc tổ chức lại loader.
- [Java SE 21 — `ServiceLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): khám phá service provider có chỉ định class loader.
- [Java SE 21 — `Instrumentation`](https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/Instrumentation.html): cơ chế dành cho Java agent, `appendToSystemClassLoaderSearch`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-009">Runtime image, jrt: và việc loại bỏ rt.jar</a>

<details>
<summary>Xem chi tiết</summary>

# Runtime image, jrt: và việc loại bỏ rt.jar

Từ **Java 9**, JDK chuyển sang mô hình **modular runtime image**: các lớp và tài nguyên của chính nền tảng Java được tổ chức trong những **module** của runtime, thay cho việc cung cấp phần lớn thư viện lõi qua tệp **`rt.jar`** như Java 8 trở về trước. Khi cần tham chiếu một tài nguyên của JDK mới, ta thường gặp URL có scheme **`jrt:`**, chẳng hạn `jrt:/java.base/java/lang/Object.class`.

Đây là thay đổi về **cách đóng gói và truy cập lớp của runtime**, liên quan trực tiếp tới cách các **built-in class loader** tìm các lớp nền tảng. Nó **không có nghĩa class loader bị loại bỏ**, JVM hết dùng bytecode `.class`, hay mọi JAR của ứng dụng cũng biến mất.

Bài này giải thích vì sao `rt.jar` không còn tồn tại, `jrt:` thực sự đại diện cho điều gì, sự khác nhau giữa module/runtime image/JAR/JMOD và cách quan sát bằng các API, công cụ Java 17 trở lên.

## 1. Runtime image là gì?

**Runtime image** là tập các thành phần cần thiết để chạy chương trình Java, bao gồm Java launcher, JVM, các module chứa thư viện và tài nguyên runtime cùng những thành phần native/configuration phù hợp.

Từ Java 9, runtime image được tổ chức theo **module**. Chẳng hạn:

- `java.base`: các API nền tảng như `java.lang`, `java.util`, `java.io`, `java.nio`; mọi module Java đều đọc `java.base` theo quy tắc của module system.
- `java.logging`: API ghi log chuẩn của Java.
- `java.sql`: các API JDBC.
- `java.xml`: các API XML chuẩn.
- Một số module `jdk.*`: công cụ hoặc API thuộc JDK, không nhất thiết hiện diện trong mọi runtime image.

Không phải runtime image nào cũng có cùng tập module. Một JDK đầy đủ thường bao gồm nhiều module/công cụ; **custom runtime image** do `jlink` tạo có thể chỉ bao gồm các module cần cho ứng dụng và phụ thuộc của chúng.

### So sánh mô hình trước và sau Java 9

~~~text
JDK 8 và trước (đơn giản hóa)
└─ JRE / thư mục runtime
   └─ lib/
      └─ rt.jar             ← nhiều lớp nền tảng, ví dụ java.lang.String
   └─ các thư viện, file và thành phần native khác

Java 9+ (đơn giản hóa)
└─ runtime image
   ├─ bin/java
   ├─ lib/modules           ← container runtime image của implementation JDK phổ biến
   ├─ conf/                 ← cấu hình runtime
   └─ các thư viện, file và thành phần native khác
           ↓
     module java.base, java.logging, java.sql, ...
~~~

Sơ đồ là **hình dung chức năng**, không phải cam kết rằng mọi bản phân phối Java/OS có chính xác cây thư mục trên. Đặc tả module và các API chuẩn quan trọng hơn việc mã ứng dụng giả định tên tệp nội bộ của JVM. Với HotSpot/OpenJDK, `lib/modules` thường dùng định dạng runtime image riêng, không phải một file ZIP/JAR có thể mở bằng `JarFile`.

**Phân biệt “runtime image” và “module”**: runtime image là toàn bộ môi trường chạy; mỗi module là một đơn vị tên và ranh giới phụ thuộc/đóng gói bên trong môi trường ấy. Một `module-info.class` mô tả module có chứa package nào, yêu cầu module nào, export/open package nào, v.v. Runtime image chứa nhiều module, không phải chỉ một “JAR khổng lồ” thay tên.

## 2. Vì sao `rt.jar` bị loại bỏ?

Ở Java 8, các lớp thuộc phần lớn thư viện runtime cốt lõi được lưu trong `rt.jar`. Công cụ cũ thường đi thẳng đến:

~~~text
<JAVA_HOME>/jre/lib/rt.jar
~~~

để tìm `java/lang/Object.class`, `java/lang/String.class` hoặc dùng `JarFile` duyệt tất cả lớp chuẩn.

Từ Java 9, **JEP 220 — Modular Run-Time Images** thay đổi cấu trúc runtime image để hỗ trợ mô hình module, đóng gói linh hoạt và quản lý nội bộ tốt hơn. Các tệp như **`rt.jar`**, **`tools.jar`**, **`dt.jar`** không còn tồn tại dưới vai trò cũ trong JDK modular. Cấu trúc `jre/` lồng bên trong thư mục cài đặt JDK cũng không còn theo cách cũ.

Thay đổi giải quyết nhiều vấn đề:

1. **Tổ chức theo module**: thư viện chuẩn có danh tính, phụ thuộc và quy tắc truy cập rõ ràng hơn so với một tập class phẳng.
2. **Tạo runtime vừa đủ**: `jlink` có thể ghép các module cần thiết để phân phối một runtime image riêng cho ứng dụng.
3. **Cho JVM và công cụ linh hoạt về biểu diễn nội bộ**: không buộc mọi implementation phải dùng cấu trúc ZIP của `rt.jar`.
4. **Hạn chế code phụ thuộc đường dẫn triển khai**: ứng dụng và công cụ có giao diện như `jrt:` và Java API để truy cập module/resource theo cách phù hợp.

**Điều không thay đổi:** bytecode vẫn dựa trên định dạng **ClassFile**; Java vẫn nạp lớp, xác minh bytecode, liên kết và khởi tạo lớp. JAR vẫn rất phổ biến để đóng gói thư viện/ứng dụng, cả ở classpath và module path. Việc bỏ `rt.jar` chỉ áp dụng cho **cách đóng gói lớp runtime của JDK**, không xóa bỏ định dạng `.jar`.

## 3. `jrt:` là gì?

`jrt:` viết tắt theo cách hiểu thực hành là **Java runtime image protocol/scheme**. Nó cung cấp một **không gian địa chỉ logic** cho module, lớp và tài nguyên lưu trong runtime image, giúp công cụ dùng URI/URL để truy cập mà không biết file container nội bộ được tổ chức ra sao.

Ví dụ:

~~~text
jrt:/java.base/java/lang/Object.class
│    │         └─ đường dẫn resource/class bên trong module
│    └─ tên module java.base
└─ scheme jrt
~~~

Với JDK 17 tiêu chuẩn, `ClassLoader.getSystemResource("java/lang/Object.class")` có thể trả URL:

~~~text
jrt:/java.base/java/lang/Object.class
~~~

trong khi trước Java 9, cùng mục đích truy cập tài nguyên có thể tạo URL dạng:

~~~text
jar:file:/.../jre/lib/rt.jar!/java/lang/Object.class
~~~

Điểm quan trọng là **`jrt:` không phải đường dẫn file thông thường trên ổ đĩa**. `jrt:/java.base/java/lang/Object.class` không có nghĩa một thư mục vật lý tên `jrt:` nằm trên ổ đĩa, cũng không phải lệnh HTTP tải class từ mạng.

### Scheme `jrt:` khác `jar:` và `file:`

| Scheme | Ý nghĩa thường gặp | Ví dụ |
| --- | --- | --- |
| `file:` | File hoặc thư mục được hệ điều hành cung cấp. | `file:/opt/app/classes/Demo.class` |
| `jar:` | Entry nằm bên trong một JAR/ZIP được tham chiếu qua URL. | `jar:file:/opt/app/lib/api.jar!/com/example/Api.class` |
| `jrt:` | Module và resource bên trong **runtime image**. | `jrt:/java.base/java/lang/Object.class` |

`jrt:` giải quyết bài toán **định danh/truy cập resource**, còn việc lớp thực sự do bootstrap, platform hay application loader định nghĩa là khía cạnh **class loading**. Không được nhìn scheme `jrt:` rồi suy ra “tất cả các lớp qua `jrt:` đều thuộc bootstrap loader”.

## 4. Runtime image cung cấp một hệ thống file ảo `jrt`

Bên cạnh URL `jrt:`, JDK hiện đại có **NIO FileSystem Provider** cho runtime image. Gốc filesystem mang URI `jrt:/`.

Một cấu trúc thường gặp khi duyệt bằng provider:

~~~text
jrt:/
├── modules/
│   ├── java.base/
│   │   ├── module-info.class
│   │   ├── java/lang/Object.class
│   │   └── ...
│   ├── java.logging/
│   └── ...
└── packages/
    ├── java.lang/
    ├── java.util/
    └── ...
~~~

**Cực kỳ dễ nhầm hai loại đường dẫn**:

- **URL đến resource**: `jrt:/java.base/java/lang/Object.class`.
- **Path trong `jrt` FileSystem**: `/modules/java.base/java/lang/Object.class`.

Đây là **hai cách biểu diễn cùng nguồn dữ liệu logic** theo quy ước của hai API khác nhau. Khi dùng `Files.exists` hoặc `Files.readAllBytes` với JRT NIO FileSystem, lấy `Path` từ provider và dùng cấu trúc `/modules/...`; không tự tạo `Path.of("jrt:/...")` rồi mong hệ thống file mặc định hiểu scheme ấy.

Các đường dẫn `/packages/...` có thể hỗ trợ tra cứu package → module trong cấu trúc ảo; nó không có nghĩa các package bắt buộc tồn tại thành thư mục thật. Nội dung mỗi image phụ thuộc tập module được liên kết vào image đó.

## 5. Ví dụ Java 17: đọc lớp trong `java.base` qua URL và JRT FileSystem

Tạo tệp **`RuntimeImageExplorer.java`**:

~~~java
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class RuntimeImageExplorer {
    public static void main(String[] args) throws Exception {
        Module module = Object.class.getModule();
        URL resource = Object.class.getResource("Object.class");

        System.out.println("Module: " + module.getName());
        System.out.println("Resource scheme: " + resource.getProtocol());

        URI imageUri = URI.create("jrt:/");
        try (FileSystem image = FileSystems.newFileSystem(imageUri, Map.of())) {
            Path classFile = image.getPath(
                    "/modules/java.base/java/lang/Object.class");

            System.out.println("Class exists: " + Files.isRegularFile(classFile));

            byte[] bytes = Files.readAllBytes(classFile);
            System.out.printf(
                    "ClassFile magic: %02X%02X%02X%02X%n",
                    bytes[0] & 0xFF,
                    bytes[1] & 0xFF,
                    bytes[2] & 0xFF,
                    bytes[3] & 0xFF);
        }
    }
}
~~~

Biên dịch/chạy bằng **JDK 17 hoặc mới hơn**, từ thư mục chứa file:

~~~bash
javac --release 17 RuntimeImageExplorer.java
java RuntimeImageExplorer
~~~

Trên **JDK 17 tiêu chuẩn có provider `jrt`**, stdout là:

~~~text
Module: java.base
Resource scheme: jrt
Class exists: true
ClassFile magic: CAFEBABE
~~~

Giải thích:

1. `Object.class.getModule()` trả module định nghĩa `java.lang.Object`, đó là `java.base`.
2. `Object.class.getResource("Object.class")` định vị resource cùng package; URL thuộc scheme `jrt` trên runtime modular tiêu chuẩn.
3. `FileSystems.newFileSystem(URI.create("jrt:/"), Map.of())` mở một `FileSystem` do JRT provider cung cấp. `try` bảo đảm filesystem được đóng sau khi dùng.
4. `image.getPath("/modules/java.base/java/lang/Object.class")` trỏ tới ClassFile trong cấu trúc ảo của runtime image.
5. Bốn byte đầu ClassFile là `CA FE BA BE` (magic number của định dạng ClassFile); `%02X` và phép `& 0xFF` hiển thị từng byte theo hệ 16.

**Giới hạn của ví dụ:** Nó cần một môi trường JDK modular thực tế và provider `jrt`; nếu image tùy biến hoặc JVM khác không có resource/provider mong đợi, chương trình có thể không hoạt động như trên. Trong ứng dụng thật, nên kiểm tra `resource != null` trước khi dùng và xử lý `IOException`/lỗi provider phù hợp. Không hard-code rằng mọi class ứng dụng đều thuộc module `java.base`; đó chỉ là trường hợp của `Object.class`.

### Nếu chỉ cần xác định module của một lớp

Bạn không cần đọc bytecode:

~~~java
System.out.println(String.class.getModule().getName());
System.out.println(String.class.getClassLoader());
~~~

Đoạn trên là **fragment**, có thể đặt trong method. Trên JDK thông thường, `String.class` thuộc `java.base` và `String.class.getClassLoader()` trả `null` để biểu diễn **bootstrap loader** trong API. `null` không đồng nghĩa JVM không dùng cơ chế nạp lớp. Một class ứng dụng nằm trên classpath thông thường thuộc **unnamed module** của defining class loader và không nhất thiết có URL `jrt:`.

## 6. Runtime image có liên quan thế nào tới ba built-in class loader?

Trong Java hiện đại, ba loader thường được nhắc tới:

| Loader | Vai trò khái quát |
| --- | --- |
| **Bootstrap class loader** | Loader gắn với JVM, định nghĩa các lớp nền tảng cốt yếu, trong đó có phần lớn `java.base`; Java API thường trả `null` cho loader này. |
| **Platform class loader** | Định nghĩa nhiều lớp trong các module nền tảng khác của JDK; truy cập qua `ClassLoader.getPlatformClassLoader()`. |
| **Application/system class loader** | Phụ trách các lớp ứng dụng và nhiều thư viện quan sát được trên classpath/module path; truy cập qua `ClassLoader.getSystemClassLoader()`. |

Trước Java 9, nhiều tài liệu giải thích bootstrap loader đọc class từ `rt.jar`, extension loader đọc từ `lib/ext`, application loader đọc từ classpath. **Không dùng nguyên xi mô hình vị trí lưu trữ đó cho Java 9+**. Cơ chế extension loader và thư mục `lib/ext` thuộc mô hình cũ; platform loader thay thế vai trò trong thiết kế mới, với tổ chức module khác trước.

Một sơ đồ đúng ở mức tổng quan:

~~~text
             Runtime image (nhiều module)
                  │
                  ├── java.base
                  ├── java.logging
                  ├── java.sql
                  └── module JDK khác
                        │
                        ▼
             Built-in loader thích hợp
                        │
                  định nghĩa Class
                        ▼
               JVM liên kết/khởi tạo

     Classpath và module path của ứng dụng
                        │
                        ▼
         Application hoặc custom loader
                        │
                  định nghĩa Class
~~~

Sơ đồ **không có nghĩa tất cả module được nạp đồng loạt lúc khởi động**. Module resolution thiết lập đồ thị module cho một lần chạy; việc nạp từng class và thời điểm khởi tạo class là hoạt động khác.

**Parent delegation vẫn có vai trò**, nhưng bạn không thể suy từ đường dẫn `lib/modules` rằng bootstrap loader nạp tất cả mọi module. Một số module được gán cho platform hoặc application loader; các custom loader có thể cùng tồn tại. Ngoài ra, khả năng module đọc module khác (`requires`), export/open package là các quy tắc truy cập cấp module, không phải tên gọi khác của parent delegation.

## 7. `rt.jar` khác `lib/modules`, `jmods` và JAR thư viện thế nào?

Ba vị trí/định dạng này thường bị hiểu sai là tương đương:

| Mục | Mục đích | Có trực tiếp dùng như JAR classpath không? |
| --- | --- | --- |
| **`rt.jar`** (Java 8 trở về trước) | Chứa nhiều lớp nền tảng của Java trong JAR runtime cũ. | Là JAR, nhưng thuộc runtime do JVM quản lý, không phải thư viện ứng dụng thông thường. |
| **`lib/modules`** (JDK modular phổ biến) | Container triển khai nội bộ cho module trong **runtime image** đang chạy. | **Không.** Không coi là ZIP/JAR hay dependency ứng dụng để đưa vào `-cp`. |
| **`jmods/*.jmod`** (JDK đầy đủ, khi có) | Các artifact module đầu vào thường dùng bởi công cụ như `jlink`. | **Không.** JMOD khác JAR và không phải định dạng đặt trực tiếp trên runtime module path. |
| **JAR thường**, kể cả **modular JAR** | Phân phối thư viện hoặc ứng dụng. | Có thể dùng trên classpath hoặc module path, tùy cấu hình và tính chất JAR. |

Đặc biệt, một **JMOD** có thể đóng gói cả class và tài nguyên/native file để hỗ trợ lắp ghép runtime image. Còn `jrt:` biểu diễn dữ liệu **đã nằm trong runtime image**, không phải một scheme dẫn đến tệp `.jmod` bất kỳ trên ổ đĩa.

Java 9+ không buộc mọi ứng dụng phải tự viết `module-info.java` hoặc chuyển mọi dependency thành JMOD. **Classpath và unnamed module vẫn được hỗ trợ**. Việc thư viện bên ngoài nằm trong `.jar` không mâu thuẫn với việc `rt.jar` của JDK đã bị loại bỏ.

## 8. Công cụ quan sát runtime image

Các lệnh dưới đây dùng **JDK 17+** khi công cụ tương ứng được cài cùng JDK. Chúng là ví dụ công cụ CLI và không chỉnh sửa runtime hiện có.

### 8.1. Liệt kê module có sẵn

~~~bash
java --list-modules
~~~

Kết quả có thể chứa:

~~~text
java.base@17...
java.logging@17...
java.sql@17...
~~~

Dấu `...` là **ký hiệu lược bớt phiên bản và các dòng khác trong ví dụ**, không phải phần cố định của output. Danh sách thật phụ thuộc bản JDK/runtime image. `--list-modules` giúp trả lời “runtime hiện tại có module nào?”, nhưng không nói những class nào đã được tải vào JVM.

### 8.2. Xem chi tiết module

~~~bash
java --describe-module java.base
~~~

Lệnh cho biết metadata `java.base`: package export, module requirements và thông tin module khác theo format của Java launcher. Đây là thông tin **module system**, không phải toàn bộ nội dung bytecode của mỗi `.class`.

### 8.3. Xem một ClassFile chuẩn bằng javap

~~~bash
javap -c java.lang.String
~~~

`javap` phân tích định nghĩa lớp/bytecode để trình bày các phương thức. Nếu muốn chi tiết constant pool/attributes, dùng `javap -v java.lang.String` (output rất dài). Không cần tìm `rt.jar` và trích xuất `String.class` thủ công.

### 8.4. Xem container runtime bằng jimage

~~~text
jimage list <path-to-JDK>/lib/modules
~~~

Thay `<path-to-JDK>` bằng đường dẫn cài đặt JDK thực tế. `jimage` là công cụ trên JDK hỗ trợ định dạng container triển khai do JDK cung cấp; đường dẫn ví dụ có thể khác theo runtime. Đây là lựa chọn cho **công cụ phân tích**, không phải giao diện mà ứng dụng nên phụ thuộc để tìm class.

### 8.5. Tạo image tối giản bằng jlink

Ví dụ trên một JDK có `jlink` và các module đầu vào mặc định:

~~~bash
jlink --add-modules java.base --output minimal-runtime
~~~

Sau đó, trên Linux/macOS:

~~~bash
./minimal-runtime/bin/java --list-modules
~~~

Hoặc trên Windows PowerShell:

~~~powershell
.\minimal-runtime\bin\java.exe --list-modules
~~~

Image mới chỉ gồm `java.base` và phụ thuộc cần thiết (nếu có), nên nhiều module khác của JDK đầy đủ không hiện diện. `jlink` **tạo thư mục output mới**; nếu tên thư mục đã tồn tại, cần chọn tên output khác. Ví dụ chỉ nhằm minh họa khả năng cắt gọn runtime; không phải chỉ dẫn đóng gói đầy đủ một ứng dụng phức tạp. Ứng dụng thực tế phải đưa vào đầy đủ các module và phụ thuộc cần thiết, kể cả dịch vụ được nạp động khi phù hợp.

## 9. Di chuyển code cũ từng phụ thuộc `rt.jar`

Một pattern ở Java 8 có thể như sau:

~~~java
// Ví dụ anti-pattern: code cũ tự dò rt.jar từ thư mục cài Java.
String path = System.getProperty("java.home") + "/lib/rt.jar";
~~~

Đây là **fragment minh họa lỗi thiết kế**, không phải chương trình chạy độc lập và cũng không phải quy tắc đúng cho mọi layout JDK 8. Nếu sau đó mở đường dẫn bằng `new JarFile(path)`, nó sẽ thất bại trên JDK 9+ vì `rt.jar` không tồn tại.

Hãy chọn giải pháp theo mục đích thật:

| Nhu cầu | Cách tiếp cận phù hợp trên JDK hiện đại |
| --- | --- |
| **Nạp hoặc dùng class chuẩn** | Dùng API Java thông thường hoặc `Class.forName(...)` phù hợp; JVM và loader quyết định nguồn class. |
| **Tìm resource của một lớp** | `SomeClass.class.getResource(...)` hoặc `getResourceAsStream(...)`. |
| **Tìm resource qua system class loader** | `ClassLoader.getSystemResource(...)`, đồng thời xử lý kết quả `null` và URL `jrt:`. |
| **Duyệt class/resource của module trong runtime image** | JRT FileSystem Provider (`FileSystems.newFileSystem(URI.create("jrt:/"), ...)`) hoặc công cụ `jimage`. |
| **Tìm module nào chứa class/định nghĩa** | `SomeClass.class.getModule()`, `ModuleLayer` hoặc các công cụ mô-đun khi phù hợp. |
| **Xây runtime phân phối cùng ứng dụng** | `jlink`, sau khi phân tích module dependencies và provider cần dùng. |
| **Phân tích bytecode** | `javap` cho nhu cầu cơ bản; thư viện đọc ClassFile thích hợp cho công cụ chuyên sâu. |

Không nên chuyển mọi đoạn `rt.jar` sang đường dẫn hard-code `$JAVA_HOME/lib/modules`. Mục tiêu là **loại bỏ sự phụ thuộc vào đường dẫn/định dạng container nội bộ**. Cũng không nên coi `jrt:/` là đường dẫn classpath cần bổ sung thủ công: các lớp runtime đã được hệ thống module và built-in loader quản lý.

### Đọc tài nguyên an toàn bằng InputStream

Nếu chỉ cần đọc bytes của một resource, API trên chính class thường dễ dùng hơn thao tác đường dẫn:

~~~java
try (var stream = Object.class.getResourceAsStream("Object.class")) {
    if (stream == null) {
        throw new IllegalStateException("Cannot locate Object.class");
    }
    byte[] bytes = stream.readAllBytes();
    System.out.println(bytes.length > 4);
}
~~~

Đây là **fragment** để đặt trong method có khả năng ném/điều khiển `IOException`. Với JDK chuẩn, nó in `true`; dùng `getResourceAsStream` tránh giả định rằng URL phải có `file:` hoặc `jar:` scheme. Đọc nguyên ClassFile chỉ phù hợp khi thực sự cần bytes; sử dụng API của lớp không cần tự đọc `.class`.

## 10. Những giới hạn và lỗi thường gặp

### 10.1. `FileSystems` mặc định không tự hiểu `jrt:`

`Path.of("jrt:/java.base/java/lang/Object.class")` tạo path theo **default filesystem** (có thể trở thành chuỗi tên file bình thường), không đại diện cho JRT resource. Cần dùng `Path` từ JRT provider như ví dụ ở mục 5.

### 10.2. Không được cast system/platform loader thành URLClassLoader

Trên Java 9+, built-in application và platform loader có triển khai nội bộ khác JDK 8; code kiểu:

~~~java
// Sai giả định trên JDK hiện đại:
java.net.URLClassLoader loader =
        (java.net.URLClassLoader) ClassLoader.getSystemClassLoader();
~~~

có thể ném `ClassCastException`. Đây là đoạn anti-pattern minh họa **có thể biên dịch**, nhưng không nên thực thi để quản lý classpath. Dùng API `ClassLoader`/module chuẩn hoặc custom loader được thiết kế rõ ràng thay cho ép kiểu.

### 10.3. Đường dẫn resource có thể thay đổi hoặc không truy cập được

Không phải mọi resource đều công khai để tra cứu từ mọi module/loader. Module encapsulation có thể ảnh hưởng `getResource` hoặc `getResourceAsStream` trong các trường hợp không phải lớp/bao bọc API công khai. Các custom runtime image có thể thiếu module mà bạn đang tìm. Luôn kiểm tra `null`, quyền truy cập và điều kiện module thay vì coi có JDK là tồn tại mọi module.

### 10.4. `jrt:` không chứng minh module đang khởi tạo

Có đường dẫn `jrt:/java.base/...class` chỉ xác nhận resource/định nghĩa tương ứng có thể được cung cấp; điều đó không nói class đang initialized, đã tạo object hay đã được JIT compile. **Resource resolution**, **class loading**, **class linking**, **class initialization** là các hoạt động khác nhau.

### 10.5. Không “patch” `lib/modules` bằng sửa byte thủ công

Container runtime image là chi tiết triển khai, không phải JAR tương thích để người dùng trực tiếp thay entry. Việc sửa thủ công có thể làm hỏng runtime hoặc phá các giả định của JVM/công cụ. Khi cần bổ sung thư viện, hãy dùng classpath/module path và quy trình build; khi cần image mới, hãy dùng `jlink` với tập module phù hợp.

### 10.6. Không nhầm JMOD, JAR và runtime image

- **Modular JAR** có `module-info.class` và có thể được đưa trên module path.
- **JMOD** thường dùng để cung cấp nội dung cho linking và các công cụ JDK; không dùng như dependency JAR trên runtime module path.
- **Runtime image** là môi trường đã được tổ hợp để thực thi, với danh sách module và cấu trúc phù hợp implementation.

## 11. Tự kiểm tra

**Câu 1.** Java 17 có cần `rt.jar` để dùng `java.lang.String` không?

**Trả lời:** Không. Các lớp nền tảng nằm trong module của runtime image, và JVM cùng built-in loader cung cấp việc định nghĩa lớp theo cơ chế hiện đại.

**Câu 2.** URL `jrt:/java.base/java/lang/Object.class` là đường dẫn vật lý tới một file JAR?

**Trả lời:** Không. Đây là URL logic có scheme `jrt:`, chỉ resource thuộc module `java.base` trong runtime image. API/provider che giấu cấu trúc container triển khai.

**Câu 3.** Vì sao khi dùng JRT FileSystem lại lấy `/modules/java.base/java/lang/Object.class`?

**Trả lời:** JRT NIO FileSystem trình bày một cây ảo gồm `/modules/...`. Quy ước `Path` này khác với cú pháp URL tài nguyên `jrt:/java.base/...`.

**Câu 4.** Có thể thêm `$JAVA_HOME/lib/modules` vào classpath để thay `rt.jar` không?

**Trả lời:** Không nên và không đúng ý nghĩa: `lib/modules` là file/container của runtime image, không phải JAR dependency trên classpath. Loader của JVM đã quản lý các module hệ thống.

**Câu 5.** `jlink` tạo gì và ứng dụng cần chú ý gì?

**Trả lời:** Nó tạo custom runtime image gồm module gốc được chọn và các phụ thuộc được giải quyết. Ứng dụng phải xác định đúng những module cần lúc chạy, kể cả các provider chỉ xuất hiện qua nạp động.

**Câu 6.** `Object.class.getClassLoader() == null` trên JDK phổ biến có nghĩa là không có class loading?

**Trả lời:** Không. `null` là cách API biểu diễn bootstrap loader; class vẫn được JVM nạp/định nghĩa theo cơ chế thích hợp.

## 12. Ghi nhớ và tài liệu chuẩn

**Nắm ba ý chính:**

1. Từ Java 9, **`rt.jar` bị loại bỏ**, các lớp chuẩn thuộc module trong **modular runtime image**; đây là sự thay đổi cấu trúc phân phối JDK, không phải xóa JAR của thư viện ứng dụng.
2. **`jrt:`** là scheme truy cập logic tới resource của runtime image. Để duyệt như filesystem, dùng **JRT NIO provider** và `/modules/<module>/...`; không phụ thuộc đường dẫn vật lý của container.
3. **Built-in class loader vẫn định nghĩa lớp**; module graph, loader và class initialization là những khái niệm riêng. Để sửa code cũ, thay thao tác trực tiếp trên `rt.jar` bằng **API resource/module phù hợp**, `javap`, `jimage` hoặc `jlink` tùy mục đích.

**Tài liệu chính thức:**

- OpenJDK, **JEP 220: Modular Run-Time Images** — https://openjdk.org/jeps/220
- Oracle, **Migrating From JDK 8 to Later JDK Releases**, mục *Removed rt.jar and tools.jar* — https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html
- Oracle Java SE 17 API, **module `java.base`**, phần JRT file system provider — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/module-summary.html
- Oracle Java SE 17, **jlink** — https://docs.oracle.com/en/java/javase/17/docs/specs/man/jlink.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-010">Giới hạn của sơ đồ ba loader khi làm việc với named module</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của sơ đồ ba loader khi làm việc với named module

## 1. Vấn đề: sơ đồ ba ClassLoader giải thích được gì?

Trong các bài nhập môn Java, ClassLoader thường được vẽ như một chuỗi:

~~~text
Bootstrap ClassLoader
          ^
          |
Platform ClassLoader
          ^
          |
Application / System ClassLoader
~~~

Sơ đồ này **hữu ích để nhận diện ba loader tích hợp** trong Java 9+ và hình dung quan hệ *parent* phổ biến. Tuy nhiên, khi một chương trình dùng **named module** (module có tên, thuộc Java Platform Module System — JPMS), sơ đồ trên **không đủ** để giải thích lớp nào nằm trong module nào, module nào đọc được module nào, package nào được phép truy cập, hoặc ClassLoader nào thực sự định nghĩa một lớp.

Các giới hạn quan trọng:

1. **Module không đồng nghĩa ClassLoader.** Một ClassLoader có thể định nghĩa lớp của nhiều named module; một named module có ClassLoader tương ứng, nhưng không bắt buộc có một loader riêng.
2. **Không chỉ có ba ClassLoader trong toàn bộ JVM.** Ba tên trong sơ đồ là những loader **tích hợp**. Các `ModuleLayer` do ứng dụng tạo thêm có thể dùng những loader mới; ứng dụng cũng có thể dùng custom ClassLoader ngoài cơ chế này.
3. **Đồ thị module khác cây quan hệ parent của loader.** `requires`/readability, `exports`, `opens` và cấu hình `ModuleLayer` không được mô tả bằng ba mũi tên trên.
4. **Named module trong boot layer không có nghĩa mọi lớp đều do cùng một loader định nghĩa.** Các module được gán vào những built-in loader tùy vai trò và cấu hình runtime; không nên suy ra loader của mọi module chỉ từ tên module.
5. **“Tìm được class” và “được phép truy cập class” là hai câu hỏi khác nhau.** Loader xử lý tra cứu/định nghĩa; JPMS còn có quy tắc readability và package accessibility.

Mục tiêu của bài là biết lúc nào sơ đồ ba loader còn hữu ích, lúc nào phải mở rộng cách quan sát sang **module + layer + loader + access rules**, và đọc được bằng chứng runtime qua Java 17+.

## 2. Nhắc lại các khái niệm trước khi xem giới hạn

### 2.1. Ba loader tích hợp nói về cơ chế định nghĩa lớp

| Built-in loader | Vai trò thường gặp | Quan sát API |
|---|---|---|
| **Bootstrap** | Loader do JVM tích hợp; định nghĩa các lớp rất cơ bản, ví dụ `java.lang.String` từ `java.base` | `String.class.getClassLoader() == null` thường trả về `true`; `null` là cách API biểu diễn bootstrap |
| **Platform** | Định nghĩa nhiều lớp thuộc các module nền tảng; đôi khi cần ủy quyền không thuần một chiều để hỗ trợ module nâng cấp | `ClassLoader.getPlatformClassLoader()` |
| **Application/System** | Thường định nghĩa lớp trên application classpath, application module path và lớp công cụ JDK liên quan | `ClassLoader.getSystemClassLoader()` |

Trong môi trường khởi chạy Java thông thường, application loader có platform loader là parent hoặc ancestor; platform loader liên quan bootstrap. Nhưng **parent delegation** chỉ là một chiều giải thích việc tìm class, không phải bản đồ module.

Đặc biệt, [Java 21 `ClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html) nêu rằng platform loader có thể cần ủy quyền sang loader khác, kể cả application loader, trong một số trường hợp module được nâng cấp. Do đó, phát biểu “mọi lần tìm class đều đi đúng ba tầng từ dưới lên rồi xuống” là giản lược quá mức đối với JPMS.

### 2.2. Named module, unnamed module và automatic module

- **Named module** là module có tên trong JPMS, chẳng hạn `java.base` hay `lab.app`. Nó có descriptor (mô tả module); descriptor có thể là khai báo tường minh bằng `module-info.java` hoặc được tạo cho **automatic module** từ một JAR trên module path.
- **Unnamed module** gắn với một ClassLoader và chứa các lớp mà loader định nghĩa từ classpath, ngoài named modules. Mỗi ClassLoader có unnamed module của chính nó; `Class.getModule().isNamed()` giúp xác định một lớp đang thuộc named hay unnamed module.
- **Automatic module** vẫn là **named module**. Nó không phải “module vô danh”, và không được hiểu là một loader mới. Những cơ chế đọc/xuất của automatic module có quy tắc riêng trong JPMS.

Hai đường truyền lớp thường gặp:

~~~text
Classpath
  -> các lớp ứng dụng thông thường
  -> unnamed module của defining loader

Module path + module resolution
  -> named modules được resolve
  -> mỗi module ánh xạ tới ClassLoader trong layer
  -> readability / exports / opens quyết định quyền truy cập
~~~

`--module-path` không tự bảo đảm **mọi** module trong đường dẫn đã được resolve. Runtime phải tạo một cấu hình module từ root modules và quan hệ phụ thuộc. Một module JAR có mặt trên ổ đĩa nhưng không được resolve không mặc nhiên trở thành module trong graph đang chạy.

### 2.3. Hai loại “cây” cần tách biệt

**Loader delegation** mô tả đường tìm kiếm/ủy quyền khi load class. **Module readability** mô tả việc một module có quyền đọc module khác hay không; thêm `exports`, `opens` để điều khiển phạm vi package có thể sử dụng.

Ví dụ module A khai báo `requires module.b;`: điều đó thể hiện quan hệ phụ thuộc/readability. Nó **không có nghĩa** loader của A bắt buộc là con trực tiếp của loader của B, cũng không tạo một ClassLoader mới cho module B.

## 3. Mô hình đúng hơn: ModuleLayer + Module + ClassLoader

### 3.1. ModuleLayer ánh xạ module tới loader

`ModuleLayer` là lớp biểu diễn một **layer các module**. Mỗi layer được tạo từ một `Configuration` (đồ thị module đã resolve) và cách ánh xạ mỗi module vào một ClassLoader.

Một JVM Java 9+ có **boot layer** (lấy bằng `ModuleLayer.boot()`). Boot layer chứa `java.base` và những module được resolve lúc JVM khởi động. Các module trong boot layer được ánh xạ đến bootstrap hoặc các built-in loaders.

Ứng dụng có thể tạo layer bổ sung. Trong Java 17+, những API quan trọng gồm:

| API | Ý nghĩa thực tế |
|---|---|
| `ModuleLayer.boot()` | Layer được tạo khi JVM khởi động |
| `ModuleLayer.findModule("name")` | Tìm module trong layer hoặc các parent layers; trả về `Optional<Module>` |
| `ModuleLayer.findLoader("name")` | Tìm ClassLoader của named module tương ứng trong layer/parent layers |
| `ModuleLayer.defineModulesWithOneLoader(...)` | Tạo layer với **một loader mới** cho các module trong configuration |
| `ModuleLayer.defineModulesWithManyLoaders(...)` | Tạo layer với **mỗi module một loader mới** |
| `ModuleLayer.defineModules(...)` | Tạo layer với ánh xạ module→ClassLoader do ứng dụng cung cấp, cho tình huống nâng cao |
| `Module.getLayer()` | Cho biết layer chứa named module; với module không thuộc layer có thể là `null` |
| `Class.getModule()` | Trả về runtime `Module` của lớp; áp dụng cả named/unnamed module |

Chính API `defineModulesWithOneLoader` chứng minh **nhiều named module có thể chia sẻ một loader**; `defineModulesWithManyLoaders` chứng minh **có thể có thêm nhiều loader bên ngoài ba loader tích hợp**.

### 3.2. Minh họa hai cách tạo layer

~~~text
Boot layer
  java.base --> bootstrap loader
  ...       --> built-in loaders (theo mapping của runtime)

                    |
                    v
          Layer ứng dụng tạo thêm

Cách A: defineModulesWithOneLoader
  plugin.alpha ---------+
                        +--> loader L (loader mới)
  plugin.beta ----------+

Cách B: defineModulesWithManyLoaders
  plugin.alpha ------------> loader L1 (loader mới)
  plugin.beta -------------> loader L2 (loader mới)
~~~

Hình minh họa thể hiện **quy tắc mapping của hai API**, không dự đoán cấu hình cụ thể của mọi JDK. `L`, `L1`, `L2` là tên đại diện; Java không hứa lớp loader sẽ có tên hoặc `toString()` cố định như vậy.

### 3.3. Layer parent không đồng nghĩa ClassLoader parent

Một layer có **parent layer** theo ngữ nghĩa cấu hình/module lookup. Loader có `getParent()` theo ngữ nghĩa ủy quyền. Hai quan hệ này có thể được thiết kế tương thích nhưng **không phải cùng một quan hệ**.

Đặc biệt, API `defineModulesWithOneLoader(cf, parentLoader)` nhận rõ một `parentLoader` để gắn cho loader **mới tạo**, còn layer parent được xác định từ ngữ cảnh tạo layer. Đừng suy ra “module layer là con thì mọi loader trong đó chắc chắn là con của loader gán cho module trong parent layer”.

## 4. Những giới hạn thực tế của sơ đồ ba loader

### Giới hạn 1 — Không xác định được module nào chứa class

`Class#getClassLoader()` cho biết defining loader; nó **không tự trả lời** lớp đó thuộc named module nào.

Ví dụ, nếu `lab.app.Main` và `lab.app.Helper` cùng do application loader định nghĩa, điều đó chưa đủ kết luận chúng thuộc cùng một named module. Ngược lại, hai module khác nhau trong boot layer hoặc một custom layer có thể được map vào cùng loader.

Hãy đọc **cả**:

~~~java
Class<?> type = java.lang.String.class;
Module module = type.getModule();
ClassLoader loader = type.getClassLoader();
System.out.println(module.getName());
System.out.println(module.isNamed());
System.out.println(loader == null);
~~~

Đoạn trên là **minh họa đặt trong một phương thức Java**, không phải chương trình độc lập. Đối với `String` trên JDK phổ biến: module là `java.base`, module có tên, và loader bootstrap được biểu diễn bằng `null`.

### Giới hạn 2 — Không diễn đạt readability, exports và opens

Ba loader không cho biết một module có quyền dùng public API trong module khác hay không. Ví dụ:

~~~java
module lab.service {
    exports lab.service.api;
}
~~~

Module khác muốn dùng API trên phải có readability phù hợp, ví dụ `requires lab.service;` trong descriptor và dependency được resolve. Package `lab.service.internal` nếu không exported sẽ không trở thành public API khả dụng chỉ vì lớp có thể được một loader tìm thấy.

Tóm tắt:

- `requires` tạo quan hệ đọc ở mức module (với các biến thể như `transitive`/`static`).
- `exports` cho phép truy cập public/protected API tương ứng trong package theo quy tắc JPMS; có thể export có điều kiện tới module cụ thể.
- `opens` cho phép dạng **deep reflection** thích hợp tới package; không tương đương với việc mở rộng static compile-time access như `exports`.
- **ClassLoader** giải quyết việc tìm/định nghĩa class; **JPMS** thêm quy tắc kiểm soát truy cập.

Một `IllegalAccessError` hoặc `InaccessibleObjectException` liên quan module/package không được mặc định xử lý bằng việc đổi parent loader. Cần kiểm tra `requires`, `exports`, `opens` và cách ứng dụng được khởi chạy.

### Giới hạn 3 — Không thấy module layer do framework/plugin tạo

JVM có thể có **nhiều layer**; các layer mới tạo thường dành cho plugin, ứng dụng chia phần hoặc hệ thống cần quản lý tập module động. Sơ đồ ba built-in loaders không có chỗ cho loader của các layer này.

Có thể có:

- Các module trong **cùng một layer** chia sẻ một loader.
- Các module trong **cùng một layer** được gán mỗi module một loader.
- Các layer khác nhau chứa module có cùng tên theo cấu hình riêng, nhưng đó là các runtime module object khác nhau; không nên kết luận hai class là cùng kiểu chỉ nhờ tên module hoặc tên binary class.

Tại runtime, định danh class gắn với **binary name + defining class loader**. Module membership/layer là thông tin bổ sung cần kiểm tra khi debug, đặc biệt khi hai plugin cùng chứa lớp có tên giống nhau.

### Giới hạn 4 — Không ánh xạ cố định “module A → platform, module B → system”

Trong boot layer, các module platform/JDK/application được định nghĩa bởi các loader tương ứng với cấu hình thực tế. **Không có quy tắc đáng tin cậy rằng mọi named module đều thuộc system loader, hoặc mọi module mang tiền tố `java.` đều thuộc bootstrap loader.** Chi tiết chọn loader còn phụ thuộc nền tảng, phiên bản JDK, module resolution và cơ chế nâng cấp module.

Đừng gán loader dựa trên tên module. Thay vào đó, hỏi runtime:

~~~java
Class<?> clazz = java.sql.Driver.class;
System.out.println(clazz.getModule().getName());
System.out.println(clazz.getClassLoader());
~~~

Đoạn trên là **mã minh họa**. Chuỗi xuất ra bởi `getClassLoader()` là implementation detail; không yêu cầu nó giống nhau giữa JDK/vendor. Ngoài ra, module `java.sql` phải có mặt trong cấu hình runtime sử dụng lớp này.

### Giới hạn 5 — Parent-first là nguyên tắc mặc định, không phải toàn bộ JPMS

Mô hình `ClassLoader.loadClass` mặc định thường kiểm tra lớp đã nạp, ủy quyền cho parent, rồi dùng `findClass`. Tuy vậy, việc ánh xạ module đến loader và các trường hợp đặc biệt của built-in loaders tạo thêm chi tiết so với “cứ tìm từng tầng theo ba ô”.

Chẳng hạn, tài liệu `ClassLoader` của Java 21 ghi rõ **platform loader có thể cần ủy quyền tới một loader khác, kể cả application loader**, khi module nâng cấp đọc module được định nghĩa bởi các loader nằm ngoài chuỗi tổ tiên thông thường. Custom loader còn có thể được thiết kế với chiến lược không giống mặc định.

Vì thế, đừng dùng sơ đồ ba loader để chứng minh thứ tự nạp của mọi class trong một modular application.

## 5. Thực hành A — Một named module trong boot layer

Thực hành này chứng minh **named module + boot layer + loader mapping** là ba thông tin khác nhau có thể quan sát trực tiếp.

Tạo cấu trúc:

~~~text
project/
  src/
    lab.app/
      module-info.java
      lab/
        app/
          Main.java
~~~

Tệp **`src/lab.app/module-info.java`**:

~~~java
module lab.app {
}
~~~

Tệp **`src/lab.app/lab/app/Main.java`**:

~~~java
package lab.app;

public class Main {
    public static void main(String[] args) {
        Module module = Main.class.getModule();

        System.out.println("module=" + module.getName());
        System.out.println("named=" + module.isNamed());
        System.out.println("bootLayer="
                + (module.getLayer() == ModuleLayer.boot()));
        System.out.println("loaderMatches="
                + (Main.class.getClassLoader()
                == ModuleLayer.boot().findLoader("lab.app")));
        System.out.println("javaBase="
                + String.class.getModule().getName());
    }
}
~~~

Từ thư mục **`project`**, biên dịch và chạy bằng JDK 17+:

~~~sh
javac --release 17 -d mods/lab.app src/lab.app/module-info.java src/lab.app/lab/app/Main.java
java --module-path mods --module lab.app/lab.app.Main
~~~

Lưu ý: `javac -d` tạo cây thư mục output khi cần; ở đây `mods/lab.app` là thư mục chứa lớp của module `lab.app` theo *exploded module* layout.

**Stdout đã kiểm chứng với chương trình trên:**

~~~text
module=lab.app
named=true
bootLayer=true
loaderMatches=true
javaBase=java.base
~~~

**Giải thích:**

1. `module=lab.app` đến từ **`Main.class.getModule().getName()`**, không phải tên class loader.
2. `named=true` cho thấy `Main` thuộc named module được khai báo bởi `module-info.java`.
3. Khi launcher khởi động bằng `--module lab.app/...`, module main này thuộc **boot layer**.
4. `findLoader("lab.app")` cho loader ánh xạ từ module `lab.app`; so sánh với defining loader của `Main` là `true`.
5. `java.base` cũng là named module, nhưng điều đó **không đòi** nó dùng loader riêng chỉ vì có tên module; trong môi trường này `String` được định nghĩa bởi bootstrap.

Không cần khẳng định loader của `lab.app` có chuỗi tên in ra nhất định. Ví dụ dựa trên **quan hệ identity** có thể kiểm chứng được.

## 6. Thực hành B — Tạo loader ngoài sơ đồ ba built-in loader

Thực hành thứ hai chứng minh việc tạo **named module trong child layer** có thể sinh ClassLoader **khác system loader**. Nó cũng minh họa rằng một module được resolve nhưng chưa chắc nằm trong boot layer.

Tạo cấu trúc:

~~~text
project/
  src/
    plugin.one/
      module-info.java
      plugin/
        one/
          Feature.java
    launcher/
      LayerDemo.java
~~~

Tệp **`src/plugin.one/module-info.java`**:

~~~java
module plugin.one {
    exports plugin.one;
}
~~~

Tệp **`src/plugin.one/plugin/one/Feature.java`**:

~~~java
package plugin.one;

public class Feature {
    public static String label() {
        return "feature";
    }
}
~~~

Tệp **`src/launcher/LayerDemo.java`** (launcher chạy từ classpath, tức trong unnamed module của loader tương ứng):

~~~java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

public class LayerDemo {
    public static void main(String[] args) throws Exception {
        ModuleLayer boot = ModuleLayer.boot();

        ModuleFinder finder = ModuleFinder.of(Path.of("build/mods"));
        Configuration cf = boot.configuration().resolve(
                finder, ModuleFinder.of(), Set.of("plugin.one"));

        ModuleLayer child = boot.defineModulesWithOneLoader(
                cf, ClassLoader.getSystemClassLoader());
        ClassLoader loader = child.findLoader("plugin.one");
        Class<?> feature = loader.loadClass("plugin.one.Feature");

        System.out.println("name=" + feature.getModule().getName());
        System.out.println("inChild="
                + (feature.getModule().getLayer() == child));
        System.out.println("loaderMatches="
                + (feature.getClassLoader() == loader));
        System.out.println("differentFromSystem="
                + (loader != ClassLoader.getSystemClassLoader()));
    }
}
~~~

Từ thư mục **`project`**, chạy đúng các lệnh:

~~~sh
javac --release 17 -d build/mods/plugin.one src/plugin.one/module-info.java src/plugin.one/plugin/one/Feature.java
javac --release 17 -d build/launcher src/launcher/LayerDemo.java
java -cp build/launcher LayerDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
name=plugin.one
inChild=true
loaderMatches=true
differentFromSystem=true
~~~

### Cơ chế của ví dụ

1. `ModuleFinder.of(Path.of("build/mods"))` quan sát được exploded module `plugin.one` tại đường dẫn build; finder **chưa tự tải lớp**.
2. `boot.configuration().resolve(...)` tạo configuration cho named module `plugin.one` dựa trên boot configuration làm parent; `Set.of("plugin.one")` chọn root module cần resolve.
3. `boot.defineModulesWithOneLoader(cf, systemLoader)` tạo **child module layer** cùng **loader mới** cho module được resolve trong configuration. Argument `systemLoader` là **parent** của loader mới, không phải bản thân loader chứa `plugin.one`.
4. `child.findLoader("plugin.one")` lấy đúng loader được gán cho module. `loader.loadClass("plugin.one.Feature")` tìm lớp; ta chưa cần tạo instance hoặc kích hoạt initialize `Feature`.
5. `feature.getModule().getLayer() == child` chứng minh module nằm trong layer mới; `loader != systemLoader` chứng minh đã có loader nằm **ngoài ba tên trong sơ đồ built-in**.

Khi dùng `defineModulesWithManyLoaders(cf, systemLoader)` với configuration chứa **nhiều** named module, API sẽ tạo **một loader mới cho mỗi module**. Ngược lại, `defineModulesWithOneLoader` tạo **một loader** dùng cho các module trong configuration. Không cần tự viết subclass ClassLoader để quan sát hiệu ứng này.

> **Lưu ý về tính chạy được:** `LayerDemo` dùng đường dẫn tương đối `build/mods`; hãy chạy lệnh từ đúng thư mục `project`. Nếu chạy ở thư mục khác, sửa đường dẫn cho đúng. Thử nghiệm là ví dụ độc lập Java 17+, không cần Maven, Gradle hoặc IDE.

## 7. Khi nào dùng thông tin nào để phân tích vấn đề?

| Câu hỏi thực tế | Quan sát phù hợp | Vì sao sơ đồ ba loader chưa đủ |
|---|---|---|
| Lớp do loader nào định nghĩa? | `clazz.getClassLoader()` | Biết loader nhưng chưa biết JPMS module |
| Lớp thuộc module nào, named không? | `clazz.getModule()`, `isNamed()`, `getName()` | Một loader có thể gắn với nhiều module |
| Module thuộc layer nào? | `clazz.getModule().getLayer()` | Boot layer chỉ là một trong các layer có thể có |
| Một module trong layer dùng loader nào? | `layer.findLoader(moduleName)` | Module-to-loader mapping không suy ra từ quan hệ `requires` |
| Vì sao module A không dùng được public class của B? | Module readability (`requires`), package `exports` và resolution | Class loading và package access là hai cơ chế khác nhau |
| Vì sao framework reflection bị chặn? | Kiểm tra package `opens` và quyền reflective access | Có class trong loader chưa đủ để deep reflection |
| Vì sao hai plugin có cùng binary name nhưng không ép kiểu được? | So sánh `getName()` **và** `getClassLoader()`, thêm module/layer | Có thể là hai lớp khác defining loader |
| Vì sao một module JAR hiện diện nhưng không tải được? | Module path, root modules, `Configuration`, resolution và mapping | Có file không đồng nghĩa module đã được resolve |
| Vì sao thêm `--module-path` chưa đủ? | Kiểm tra module được resolve từ roots/`requires` | Module path cung cấp vị trí, không thay việc resolve graph |

### Các API quan sát dùng chung

~~~java
static void inspect(Class<?> clazz) {
    Module module = clazz.getModule();
    ClassLoader loader = clazz.getClassLoader();

    System.out.println("class=" + clazz.getName());
    System.out.println("module=" + module.getName());
    System.out.println("named=" + module.isNamed());
    System.out.println("layer=" + module.getLayer());
    System.out.println("loader=" + loader);
}
~~~

Đây là **phương thức minh họa**, không phải tệp Java độc lập. Với unnamed module, `module.getName()` trả về `null`; với bootstrap-defined classes, `loader` có thể là `null`. Đừng xem các giá trị `null` ấy là ngoại lệ. In `ModuleLayer` hoặc `ClassLoader` có thể cho chuỗi phụ thuộc JVM/JDK nên không sử dụng chuỗi đó làm assert cố định.

### Lệnh công cụ nên biết

- `java --list-modules`: liệt kê các module hệ thống khả dụng; **không** chứng minh tất cả đã được resolve cho ứng dụng đang chạy.
- `java --show-module-resolution --module-path mods --module lab.app/lab.app.Main`: quan sát quá trình resolve module khi khởi chạy (thứ tự/số dòng phụ thuộc JDK, không dùng làm stdout cố định).
- `jar --describe-module --file mylib.jar`: xem mô tả module của một JAR phù hợp.
- `jdeps --module-path mods --module lab.app`: phân tích dependency trong ngữ cảnh có module; điều chỉnh path tùy cấu trúc build thực tế.

Các công cụ trên bổ sung bằng chứng về **module graph**; `Class.getClassLoader()` và `ModuleLayer.findLoader()` bổ sung bằng chứng về **loader mapping**.

## 8. Lỗi suy luận phổ biến và cách sửa

1. **“Java chỉ có đúng ba class loaders.”** Sai. Ba loader trong sơ đồ là các **built-in loaders**; custom loaders và loaders cho module layers có thể tồn tại.
2. **“Named module nào cũng có classloader riêng.”** Sai. `defineModulesWithOneLoader` cho phép nhiều modules dùng chung một loader.
3. **“Mọi named module chạy bằng system loader.”** Sai. Cách ánh xạ phụ thuộc layer và cấu hình; có bootstrap, platform, application và loader khác.
4. **“`requires` nghĩa là loader của module A là con loader của B.”** Sai. Đây là quan hệ **module readability**, không phải classloader parent.
5. **“Nếu loader tìm thấy lớp thì code ở module khác đương nhiên dùng được.”** Sai. JPMS áp dụng điều kiện readability và package accessibility.
6. **“`exports` và `opens` tương đương.”** Sai. Export phục vụ truy cập thông thường theo quy tắc; opens cấp quyền reflective access phù hợp, nhất là deep reflection.
7. **“Module JAR nằm trên module path thì đã load ngay.”** Sai. Module cần được resolve; từng lớp lại được load khi JVM cần. Resolution không có nghĩa mọi lớp trong module đã initialized.
8. **“Cùng tên module hoặc cùng tên class tức là cùng class object.”** Sai. Module có thể tồn tại ở nhiều layers; identity của class xét binary name cùng defining loader.
9. **“`getClassLoader() == null` là bug.”** Sai với bootstrap-defined classes như `String`.
10. **“Parent-first luôn đủ giải thích thứ tự tìm lớp trong ứng dụng modular.”** Sai. Các built-in loader có trường hợp ủy quyền bổ sung; module mapping và custom strategies cần được kiểm tra thực tế.

## 9. Tự kiểm tra

1. Vì sao sơ đồ bootstrap → platform → application vẫn có ích nhưng không phải bản đồ đầy đủ của JPMS?
2. Một loader có thể định nghĩa nhiều named module không? API nào trong `ModuleLayer` minh họa điều đó?
3. Tạo child layer bằng `defineModulesWithManyLoaders` với ba module thì có bao nhiêu loader mới theo hợp đồng của API?
4. Quan hệ `requires` và quan hệ `ClassLoader.getParent()` khác nhau ở bản chất nào?
5. Nếu một lớp được loader tìm thấy nhưng module hiện tại không truy cập được package chứa nó, nên kiểm tra những quy tắc JPMS nào?
6. Dùng API nào để biết lớp thuộc named/unnamed module và layer nào?
7. `ClassLoader.getSystemClassLoader()` có nhất thiết là loader của mọi class trên module path không?
8. Vì sao `loader.loadClass("plugin.one.Feature")` trong ví dụ B không chứng minh module thuộc boot layer?
9. Trong ví dụ B, `differentFromSystem=true` chứng minh điều gì? Có nhất thiết phải viết subclass custom ClassLoader không?
10. Vì sao cần xác định module resolution trước khi kết luận một named module “có mặt” trong ứng dụng?
11. Nếu hai plugin đưa hai lớp tên `plugin.api.Service` vào các loader khác nhau, tên class giống nhau có bảo đảm cast được không?
12. `Module.getLayer() == null` hoặc `Class.getClassLoader() == null` có luôn là lỗi không? Nêu trường hợp hợp lệ cho từng kết quả.

**Tiêu chí hoàn thành:** bạn giải thích đúng sự khác nhau giữa **module graph**, **module layer** và **loader delegation**; tự chạy hai thí nghiệm, dự đoán đúng stdout và dùng kết hợp `Class#getModule`, `ModuleLayer.findLoader`, `Class#getClassLoader` để chẩn đoán thực tế.

## 10. Tham khảo chính thức

- [Java SE 21 — `ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): ba built-in loaders, parent delegation, ngoại lệ ủy quyền trong ứng dụng module.
- [Java SE 21 — `ModuleLayer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): boot layer, cấu hình module, mapping module→ClassLoader và các API một/nhiều loaders.
- [Java SE 21 — `Module`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html): module identity, named/unnamed, layer, readability, exports/opens.
- [Java SE 21 — `Configuration`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/Configuration.html): module resolution và quan hệ phụ thuộc.
- [Java SE 21 — `ModuleFinder`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/module/ModuleFinder.html): khám phá module definitions trên đường dẫn.

Các liên kết API là nguồn tra cứu Java 21; hai ví dụ biên dịch ở mức tương thích Java 17 và không dựa vào thứ tự chuỗi `toString()` của loader.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="built-in-class-loaders-011">Tùy chỉnh system class loader bằng java.system.class.loader</a>

<details>
<summary>Xem chi tiết</summary>

# Tùy chỉnh system class loader bằng `java.system.class.loader`

Trong Java, **system class loader** là bộ nạp lớp mặc định mà `ClassLoader.getSystemClassLoader()` trả về. Ở cách khởi chạy thông thường của JDK, đó là bộ nạp lớp ứng dụng (application loader); nó thường tham gia nạp các lớp ứng dụng và là parent mặc định của `new ClassLoader()`.

Khi cần thay đổi đối tượng được dùng làm **system class loader của JVM**, có thể đặt system property `java.system.class.loader` **lúc khởi động JVM**:

```bash
java -Djava.system.class.loader=CustomSystemLoader -cp . SystemLoaderDemo
```

Trong lệnh này, `CustomSystemLoader` là **tên nhị phân đầy đủ của một lớp Java** do ứng dụng cung cấp (nếu có package thì viết cả package). JVM tìm và tạo một instance của lớp đó thông qua cơ chế quy định trong `ClassLoader.getSystemClassLoader()`; instance mới trở thành system class loader.

Đây là tính năng cấp JVM có thể tác động tới các thư viện gọi `ClassLoader.getSystemClassLoader()`, chứ không chỉ tới một lời gọi `loadClass` đơn lẻ. Vì vậy cần hiểu rõ **cơ chế khởi tạo, quan hệ parent delegation, giới hạn áp dụng và cách chẩn đoán** trước khi sử dụng.

## 1. Ba đối tượng cần phân biệt

```text
                JVM startup
                    |
                    v
      Built-in default system class loader
        (thường là application loader)
                    |
     nạp class CustomSystemLoader từ đây
                    |
     new CustomSystemLoader(defaultLoader)
                    |
                    v
          CustomSystemLoader instance
          được công bố làm system loader
                    |
       ClassLoader.getSystemClassLoader()
                    |
                    v
       trả về custom instance này
```

Chú ý quan hệ dưới đây khi constructor gọi `super(parent)`:

```text
CustomSystemLoader        <-- ClassLoader.getSystemClassLoader()
        |
        | getParent()
        v
Default built-in app loader
        |
        v
Platform loader (môi trường JDK thông thường)
        |
        v
Bootstrap loader (không có object ClassLoader Java như loader khác)
```

| Khái niệm | Vai trò |
|---|---|
| **Default system class loader** | Bộ nạp lớp tích hợp mà JVM sử dụng để tìm/nạp lớp provider `CustomSystemLoader` và truyền cho constructor của nó |
| **Custom system loader** | Instance được khởi tạo từ tên lớp trong `java.system.class.loader`, thay cho đối tượng system loader mặc định được công bố qua `getSystemClassLoader()` |
| **Defining loader của một lớp cụ thể** | Loader thực sự gọi cơ chế định nghĩa lớp runtime; có thể là parent tích hợp, dù việc `loadClass` bắt đầu từ custom loader |
| **Thread context class loader (TCCL)** | Loader gắn với từng thread, truy vấn bằng `Thread.currentThread().getContextClassLoader()`; thread có thể thay đổi context loader độc lập |
| **Bootstrap/platform loaders** | Các loader khác trong kiến trúc nạp lớp; không bị thay thế đồng loạt chỉ vì thay system loader |

**Không hiểu nhầm:** thay system loader **không tự động chuyển quyền sở hữu định nghĩa của mọi lớp** sang custom loader, và không làm thay đổi tất cả các tham chiếu `ClassLoader` đang tồn tại trong JVM.

## 2. JVM thiết lập system loader tùy chỉnh như thế nào?

Theo tài liệu `java.lang.ClassLoader.getSystemClassLoader()` (Java 17+), khi property `java.system.class.loader` đã được thiết lập tại thời điểm JVM lần đầu khởi tạo system loader, quy trình về cơ bản là:

1. JVM biết **default system class loader** do môi trường cung cấp.
2. Lấy giá trị property `java.system.class.loader` làm tên lớp provider, ví dụ `com.example.loader.CustomSystemLoader`.
3. **Nạp lớp provider bằng default system class loader**, không dùng một instance custom chưa được tạo.
4. Yêu cầu lớp provider có **public constructor nhận đúng một tham số kiểu `ClassLoader`**.
5. Gọi constructor đó và truyền vào **default system class loader**.
6. Instance được tạo trở thành **system class loader**; những lời gọi `ClassLoader.getSystemClassLoader()` sau khi thiết lập thành công trả instance đó.

Quy trình trong Java API quy định rằng tham số constructor là loader mặc định, dự kiến được dùng làm **delegation parent**. Để tuân thủ ý nghĩa đó, constructor thường gọi `super(parent)` hoặc truyền `parent` cho constructor superclass tương ứng.

### Hợp đồng constructor quan trọng

```java
public final class CustomSystemLoader extends ClassLoader {
    public CustomSystemLoader(ClassLoader parent) {
        super(parent);
    }
}
```

Đây là **lớp provider tối thiểu**, chưa thay đổi cách tìm lớp: do không override cơ chế tìm kiếm, nó kế thừa mô hình delegation mặc định của `ClassLoader`.

Điều kiện bắt buộc:

- Tên trong property phải là tên lớp mà **default system loader có thể nạp được** khi JVM khởi động.
- Constructor cần có chữ ký chính xác **`public CustomSystemLoader(ClassLoader parent)`**.
- Constructor thực hiện được thành công, và nên thiết lập parent bằng tham số JVM cấp.
- Provider phải là một `ClassLoader` hợp lệ; không thể cung cấp một lớp không kế thừa/không phải `ClassLoader`.
- Nếu provider nằm trong package, property phải chứa tên đủ package; ví dụ `-Djava.system.class.loader=com.example.loader.CustomSystemLoader`.

**Không đủ:** constructor `public CustomSystemLoader()` không tham số, một constructor `protected`, constructor nhận `URL[]` hoặc constructor nhận `Object`. Những chữ ký ấy không đáp ứng hợp đồng của property.

## 3. Ví dụ Java 17+ chạy được: thay system loader và quan sát delegation

Ví dụ gồm **hai tệp Java**, không cần Maven, Gradle hoặc thư viện bên ngoài. Bộ nạp tùy chỉnh sẽ ghi log khi được JVM tạo và khi ta yêu cầu nạp một lớp có tên `DemoTarget`. Nó **ủy quyền cho parent**, vì vậy ví dụ còn phân biệt được *loader nhận yêu cầu* và *loader thực sự định nghĩa lớp*.

### Tệp 1 — `CustomSystemLoader.java`

```java
public final class CustomSystemLoader extends ClassLoader {

    // Bắt buộc là public và nhận đúng một ClassLoader.
    public CustomSystemLoader(ClassLoader parent) {
        super(parent);
        System.out.println("CustomSystemLoader constructed");
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        if ("DemoTarget".equals(name)) {
            System.out.println("CustomSystemLoader requested DemoTarget");
        }

        // Giữ parent-first delegation do ClassLoader cung cấp.
        return super.loadClass(name, resolve);
    }
}
```

**Giải thích:**

- JVM sẽ nạp chính lớp `CustomSystemLoader` bằng default system loader trước khi tạo custom instance.
- `super(parent)` giữ đúng parent do JVM cấp, tránh tự thiết kế lại chuỗi loader.
- Override `loadClass(String, boolean)` ở đây chỉ để quan sát yêu cầu đối với `DemoTarget`. Cuối cùng, `super.loadClass` vẫn ưu tiên lớp đã nạp hoặc parent trước khi gọi `findClass`.
- Vì `DemoTarget` ở classpath của loader tích hợp, nó được parent định nghĩa. Việc nhận yêu cầu qua custom loader không chứng minh rằng class được *defined by custom loader*.

### Tệp 2 — `SystemLoaderDemo.java`

```java
public class SystemLoaderDemo {
    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader system = ClassLoader.getSystemClassLoader();

        System.out.println("custom system loader: "
                + (system instanceof CustomSystemLoader));

        // Yêu cầu một cách tường minh qua system loader.
        Class<?> type = system.loadClass("DemoTarget");

        System.out.println("target class: " + type.getSimpleName());
        System.out.println("defined by custom: "
                + (type.getClassLoader() == system));
        System.out.println("system has parent: "
                + (system.getParent() != null));
    }
}

// javac tạo DemoTarget.class từ lớp top-level package-private này.
class DemoTarget {
}
```

Lệnh biên dịch trong thư mục có hai file:

```bash
javac CustomSystemLoader.java SystemLoaderDemo.java
```

### Chạy bình thường, không đặt property

```bash
java -cp . SystemLoaderDemo
```

**Stdout của ví dụ trên JDK thông thường:**

```text
custom system loader: false
target class: DemoTarget
defined by custom: true
system has parent: true
```

Tại đây, `getSystemClassLoader()` là loader ứng dụng tích hợp. Biểu thức `type.getClassLoader() == system` trả `true` vì chính loader tích hợp đã nạp/định nghĩa `DemoTarget`.

### Chạy với custom system loader

```bash
java -Djava.system.class.loader=CustomSystemLoader -cp . SystemLoaderDemo
```

**Stdout dự kiến:**

```text
CustomSystemLoader constructed
custom system loader: true
CustomSystemLoader requested DemoTarget
target class: DemoTarget
defined by custom: false
system has parent: true
```

Một số bản HotSpot/JDK có thể ghi thêm **cảnh báo về CDS/Class Data Sharing ra stderr** khi dùng `java.system.class.loader`. Cảnh báo phụ thuộc runtime và **không thuộc stdout liệt kê ở trên**.

**Vì sao có dòng `defined by custom: false`?**

`system.loadClass("DemoTarget")` gọi method đã override trên custom loader, nhưng `super.loadClass` ủy quyền cho **default built-in app loader**, nơi đã nhìn thấy `DemoTarget.class` trên `-cp .`. Vì parent định nghĩa kiểu, `type.getClassLoader()` trả về parent, không phải custom instance.

Do đó:

```text
Ai nhận yêu cầu?         CustomSystemLoader
Ai trả về đối tượng Class? CustomSystemLoader.loadClass(...)
Ai định nghĩa DemoTarget? Default application loader (parent)
Ai là system loader?     CustomSystemLoader
```

Đây là một kết quả bình thường của **parent delegation**, không phải dấu hiệu thay system loader thất bại.

### Đọc đúng đầu ra

- `CustomSystemLoader constructed`: custom instance đã được tạo trong giai đoạn startup.
- `custom system loader: true`: `getSystemClassLoader()` trả về instance thuộc lớp provider.
- `CustomSystemLoader requested DemoTarget`: một lời gọi `loadClass` thật sự đi qua instance custom.
- `defined by custom: false`: parent mới là **defining loader** của `DemoTarget`.
- `system has parent: true`: custom loader giữ parent mà JVM cung cấp.

Bài học quan trọng: **thay system loader quyết định đối tượng mặc định được công bố, còn quyền định nghĩa mỗi kiểu phụ thuộc chính sách load/delegation thực tế**.

## 4. Cần làm gì để custom loader tự định nghĩa những lớp riêng?

Nếu mục tiêu là nạp plugin hoặc bytecode từ nguồn riêng, chỉ override để ghi log như ví dụ trên vẫn chưa đủ. Có thể bổ sung khả năng tìm lớp bằng cách kế thừa `URLClassLoader` hoặc tự triển khai `findClass` để đọc bytecode và gọi `defineClass`.

Ví dụ **phác thảo cấu hình**, chỉ dùng khi đã có thư mục `plugins` chứa bytecode đúng tên package:

```java
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class DirectorySystemLoader extends URLClassLoader {
    public DirectorySystemLoader(ClassLoader parent)
            throws MalformedURLException {
        super(
            "directory-system-loader",
            new URL[] { Path.of("plugins").toUri().toURL() },
            parent
        );
    }
}
```

Đây là **một provider khác**, phải lưu trong `DirectorySystemLoader.java` và biên dịch riêng; nó **không thuộc ví dụ đầu tiên**, và cần dữ liệu `plugins` tương ứng mới có tác dụng. Cấu hình:

```bash
java -Djava.system.class.loader=DirectorySystemLoader -cp . SystemLoaderDemo
```

Ý nghĩa: URLClassLoader sẽ thử parent theo chính sách ủy quyền trước. **Chỉ khi parent không tìm được tên lớp và URL loader có bytecode phù hợp**, nó mới có thể định nghĩa lớp từ URL đã cung cấp. Class đã hiện diện ở parent không tự động chuyển sang được custom loader định nghĩa. Thứ tự này giúp hạn chế việc vô tình tách một API dùng chung thành hai định nghĩa kiểu khác nhau.

Đối với bài toán plugin có vòng đời riêng, việc tạo **một URLClassLoader chuyên dụng cho plugin** thường dễ quản lý hơn so với thay system loader của cả ứng dụng. `java.system.class.loader` thích hợp khi thực sự cần thay cách các thành phần ứng dụng/framework tìm system loader mặc định.

**Vấn đề bảo vệ cần cân nhắc khi triển khai loader bytecode tùy chỉnh:** xác nhận đường dẫn/JAR tin cậy, xử lý package/sealing khi có, không tùy tiện định nghĩa lớp trong không gian package bị hạn chế, và không xem cơ chế class loader là sandbox bảo mật.

## 5. Khác gì với tạo một custom ClassLoader thông thường?

Xét hai cách cấu hình:

| Cách | Phạm vi | Ví dụ | Ưu tiên dùng khi |
|---|---|---|---|
| Tạo loader tường minh | Chỉ nơi code giữ/tham chiếu đến loader | `new URLClassLoader(urls, parent)` | Plugin, mô-đun riêng, dynamic loading có vòng đời độc lập |
| Đặt `java.system.class.loader` khi startup | Thay instance được `ClassLoader.getSystemClassLoader()` trả về | `-Djava.system.class.loader=...` | Muốn thay hành vi system loader cho chương trình/thư viện dựa vào system loader |
| Đặt thread context loader | Theo từng thread, kế thừa hoặc thay đổi theo cơ chế Thread | `Thread.currentThread().setContextClassLoader(loader)` | Service/provider lookup theo context hoặc tích hợp thư viện cần TCCL |

`new MyLoader(parent)` **không** biến nó thành system loader. `Thread.currentThread().setContextClassLoader(myLoader)` cũng **không** làm `ClassLoader.getSystemClassLoader()` đổi thành `myLoader`.

Tương tự, `System.setProperty("java.system.class.loader", "...")` bên trong `main` **không phải cách đáng tin để thay loader**: thông thường JVM đã thiết lập system loader trong quá trình khởi động. Đặt property từ lệnh `java -D...` ngay khi mở JVM.

### Liên hệ với thread context class loader

Tài liệu `ClassLoader.getSystemClassLoader()` mô tả system loader là context class loader cho main application thread khi runtime tạo nó. Nhưng context loader có thể được thay đổi độc lập:

```java
ClassLoader system = ClassLoader.getSystemClassLoader();
ClassLoader context = Thread.currentThread().getContextClassLoader();
System.out.println(system == context);
```

Đoạn này chỉ minh họa kiểm tra; **không được khẳng định `true` cho mọi môi trường**: framework hoặc ứng dụng có thể đã thay TCCL. Thread pool còn có thể giữ context loader được thiết lập ở thời điểm thread tạo ra.

Nếu thư viện sử dụng `Thread.currentThread().getContextClassLoader()`, việc tùy chỉnh system loader **không bảo đảm** thư viện ấy luôn sử dụng custom loader trong mọi thread.

## 6. Các giới hạn: thay system loader không thay mọi classloader của JVM

### 6.1. Không thay bootstrap hay platform loader

`java.system.class.loader` tác động tới **system loader**; nó không làm bootstrap loader trở thành loader Java tùy chỉnh. Với `String.class`:

```java
ClassLoader loader = String.class.getClassLoader();
System.out.println(loader == null);
```

Trong JVM Java thông thường, kết quả `true` vì `java.lang.String` do bootstrap loader định nghĩa. Đây là mảnh code minh họa độc lập với đoạn demo chính.

Tương tự, các lớp nền tảng/module có thể do platform hoặc bootstrap loader phục vụ theo cơ chế runtime; không nên kỳ vọng custom system loader sẽ định nghĩa lại `java.lang.String`, `java.util.List` hay các lớp trong `java.base`.

### 6.2. “System loader được sử dụng để chạy ứng dụng” không có nghĩa mọi Class thuộc về nó

Loader thực sự định nghĩa một class có thể được kiểm tra:

```java
System.out.println(SystemLoaderDemo.class.getClassLoader());
System.out.println(DemoTarget.class.getClassLoader());
System.out.println(ClassLoader.getSystemClassLoader());
```

Đây là ba thông tin **có thể khác nhau** theo launch mode, classpath/module path, parent delegation và implementation. Không phải mọi JVM đều hiển thị cùng chuỗi `toString()` hoặc cùng tên class cho loader.

Cách so sánh đáng tin hơn với một class cụ thể:

```java
ClassLoader system = ClassLoader.getSystemClassLoader();
ClassLoader defining = DemoTarget.class.getClassLoader();
boolean definedBySystem = defining == system;
```

Với custom parent-first ví dụ trên, `definedBySystem` là `false`, và đó là kết quả có chủ đích.

### 6.3. Runtime module, classpath và tính nhìn thấy kiểu

Provider phải nằm ở vị trí **default system loader** có thể tìm thấy khi khởi động. Chạy với classpath không chứa `CustomSystemLoader.class` thì JVM không thể tự “cứu” bằng custom loader, vì nó còn chưa tồn tại.

Đối với dự án modular (JPMS), còn phải xét cơ chế tìm class theo module path, module readability và khả năng truy cập. Ví dụ `-cp .` đơn giản ở trên dùng unnamed module nên tránh các ràng buộc module phức tạp. Không áp dụng nguyên xi mọi ví dụ classpath cho chương trình đóng gói thành named modules hoặc custom runtime image.

### 6.4. Framework/JAR launcher có thể quản lý loader riêng

Một ứng dụng chạy bằng launcher riêng, nested JAR hoặc framework có thể thiết lập classloader và TCCL riêng. Việc chỉ đặt `java.system.class.loader` **không bảo đảm** thay cơ chế nạp mọi thư viện/framework. Hãy xác định thư viện đang gọi:

- `ClassLoader.getSystemClassLoader()`;
- `Thread.currentThread().getContextClassLoader()`;
- `SomeClass.class.getClassLoader()`;
- hoặc một loader do framework tự giữ.

Mỗi cách có thể trả đối tượng khác nhau trong cùng một tiến trình.

### 6.5. Quy tắc parent-first không phải bắt buộc cho mọi loader tùy biến

`ClassLoader.loadClass` mặc định dùng delegation, nhưng subclass có thể thay chính sách. Child-first giúp nạp phiên bản thư viện riêng, song dễ gặp lỗi type identity/`ClassCastException`/`LinkageError` nếu cùng API được định nghĩa hai lần.

Với system loader, thay chính sách quá rộng có thể tác động các thư viện vốn mong các kiểu phổ biến được chia sẻ qua parent. Nên giới hạn override ở những package đã xác định, dùng hợp đồng type chung từ loader cha khi cần.

## 7. Những lỗi khởi động thường gặp

### 7.1. Không tìm thấy lớp provider

Ví dụ chạy:

```bash
java -Djava.system.class.loader=com.example.Missing -cp . SystemLoaderDemo
```

Khi property đã khai báo nhưng lớp không thể nạp, theo `getSystemClassLoader()` JVM có thể ném một **`Error`**, thường kèm nguyên nhân gốc trong `getCause()`. Kiểm tra chính tả tên nhị phân, package, classpath/module path, phiên bản bytecode và tính sẵn có của class provider.

**Không chờ tới khi `main` chạy mới phát hiện:** vì setup diễn ra ở quá trình startup, chương trình có thể dừng **trước `main`**.

### 7.2. Constructor sai chữ ký

```java
// Sai hợp đồng java.system.class.loader:
public CustomSystemLoader() {
    super();
}
```

Đây chỉ là mảnh minh họa chữ ký **không hợp lệ cho provider**. JVM cần đúng `public CustomSystemLoader(ClassLoader parent)`. Nếu constructor phù hợp không tồn tại hoặc không public, tạo system loader thất bại và lỗi có thể xuất hiện trước `main`.

### 7.3. Constructor ném exception

```java
public CustomSystemLoader(ClassLoader parent) {
    super(parent);
    throw new IllegalStateException("bad configuration");
}
```

Đây là mảnh minh họa lỗi, **không phải code nên sử dụng**. Theo JDK API, thất bại từ constructor dẫn tới `Error` ở lúc tạo system loader, với nguyên nhân gốc có thể truy ra từ `getCause()`. Tránh làm quá nhiều logic nặng, phụ thuộc cấu hình chưa sẵn sàng hoặc load plugin phức tạp ngay trong constructor.

### 7.4. Gọi `getSystemClassLoader()` trong constructor: vòng khởi tạo

```java
public CustomSystemLoader(ClassLoader parent) {
    super(parent);

    // SAI: system loader đang được tạo, có nguy cơ vòng khởi tạo.
    ClassLoader wrong = ClassLoader.getSystemClassLoader();
}
```

Đoạn này là **ví dụ phản diện**, không biên dịch độc lập. JDK quy định nếu phát hiện gọi đệ quy lúc đang tạo system loader từ property, JVM ném `IllegalStateException`. Thay vì gọi `getSystemClassLoader()`, constructor đã có **parent** mà JVM cung cấp.

Một sai lầm tương tự là dùng constructor `super()` không tham số. Constructor này mặc định tìm `getSystemClassLoader()` làm parent, nên có thể kích hoạt đúng đường vòng đang cần tránh. **Luôn dùng `super(parent)` cho provider đơn giản.**

### 7.5. Đổi property sau khi khởi động

```java
System.setProperty(
    "java.system.class.loader", "OtherSystemLoader");
```

Không xem thao tác trên là thay instance system loader đã thiết lập. Hợp đồng `getSystemClassLoader()` kiểm tra property khi khởi tạo system loader; thao tác `setProperty` về sau chỉ thay dữ liệu property, không phải API “hot swap” toàn bộ loader.

### 7.6. Lỗi cast khi dùng lại class cùng tên

Giả sử `com.example.Api` đã được parent định nghĩa, nhưng custom loader lại tự định nghĩa thêm một kiểu mang đúng tên đó. Java coi chúng là **hai kiểu khác nhau** nếu defining loader khác. Cast, method linkage hoặc interface assignment có thể thất bại dù log in cùng class name.

Khi chẩn đoán, log cả tên và loader:

```java
Class<?> type = obj.getClass();  // obj phải khác null
System.out.println(type.getName());
System.out.println(type.getClassLoader());
```

Không coi `ClassLoader.getSystemClassLoader() == type.getClassLoader()` là điều mặc định phải đúng.

## 8. Khi nào nên chọn `java.system.class.loader`?

**Nên cân nhắc** khi cần một instance system loader có hành vi khác với bộ nạp lớp mặc định và các thành phần liên quan **thực sự truy cập qua system loader**: instrumentation/development tooling đặc thù, một runtime tích hợp phức tạp, hoặc ứng dụng chủ động xây dựng lớp tải mặc định có kiểm soát.

**Nên dùng loader tường minh trước** nếu chỉ cần đọc plugin JAR từ một thư mục, cô lập một extension, hoặc tải lớp theo yêu cầu trong một phần ứng dụng. Cách này giới hạn tác động và thuận lợi cho vòng đời unload: sử dụng `URLClassLoader` và đóng tài nguyên thích hợp khi không dùng nữa, giải phóng mọi tham chiếu đến class/loader/thread/context nếu muốn có cơ hội unload.

Một checklist quyết định:

1. Thư viện cần thay **có gọi `getSystemClassLoader()`** không, hay dùng TCCL/custom loader?
2. Có cần thay loader **ngay khi startup** không?
3. Provider có thể được default system loader nạp và có **public constructor(ClassLoader)** đúng không?
4. Chính sách ủy quyền sẽ giữ **các API dùng chung** ở parent chứ?
5. Có kế hoạch kiểm chứng đường nạp trong môi trường packaging thực tế (classpath, modules, Spring Boot launcher, test runner) không?
6. Lỗi của custom loader có khiến cả JVM khởi động thất bại không, và ứng dụng có chấp nhận rủi ro đó không?

Nếu câu trả lời chủ yếu là “chỉ muốn load plugin theo đường dẫn riêng”, việc thay global system loader thường không cần thiết.

## 9. Kiểm chứng bằng những công cụ sẵn có của JVM

### Xác định system loader đang dùng

```java
ClassLoader loader = ClassLoader.getSystemClassLoader();
System.out.println(loader.getClass().getName());
System.out.println(loader.getParent());
```

Kết quả phụ thuộc VM; phần class name và parent cung cấp bằng chứng tốt hơn việc nhìn giá trị property đơn thuần. Trong ví dụ của bài, custom loader tự in log khi được JVM xây dựng.

### Xác định class được loader nào định nghĩa

```java
Class<?> type = Class.forName(
        "DemoTarget",
        false,
        ClassLoader.getSystemClassLoader());

System.out.println(type.getClassLoader());
```

Đây là mảnh code minh họa sử dụng Class có sẵn trong hai-file demo. `Class.forName(name, false, loader)` chủ động tra cứu/nạp và liên kết nhưng không yêu cầu initialize lớp; phần `getClassLoader()` trả về **defining loader**, không phải chắc chắn loader được truyền vào.

### Ghi log hoạt động tải lớp trên HotSpot

```bash
java -Xlog:class+load=info -Djava.system.class.loader=CustomSystemLoader -cp . SystemLoaderDemo
```

HotSpot có thể ghi log kiểu đã nạp và nguồn nạp. Nội dung/thứ tự chi tiết khác nhau giữa các bản Java và launcher; các log này là công cụ chẩn đoán, không phải stdout cố định của ví dụ học tập.

### Thử tình huống lỗi có kiểm soát

Có thể thay tạm property bằng tên provider không tồn tại để xem lỗi khởi động, rồi trở về ví dụ hợp lệ. Không cần sửa mã nguồn `SystemLoaderDemo`; vấn đề nằm ở property/tính tìm thấy provider.

## 10. Các ngộ nhận phổ biến

1. **“`java.system.class.loader` là tên class loader Java đã tồn tại sẵn.”** Sai. Giá trị là **tên lớp provider**, JVM nạp nó bằng default loader và gọi constructor phù hợp để tạo instance.
2. **“Chỉ cần subclass `ClassLoader` là đủ.”** Chưa đủ. Provider phải có **public constructor(ClassLoader)**.
3. **“Custom provider tự nạp chính bản thân mình.”** Sai. JVM nạp provider bằng **default system loader** trước khi tạo custom instance.
4. **“System loader mới bắt buộc phải tự định nghĩa mọi class ứng dụng.”** Sai. Parent-first delegation có thể để parent định nghĩa class.
5. **“`loadClass` được gọi qua custom, nên `clazz.getClassLoader()` phải bằng custom.”** Sai. `getClassLoader()` trả defining loader; caller/initiating loader có thể khác.
6. **“Đặt `System.setProperty` trong `main` là đổi system loader.”** Sai. System loader thường đã được tạo trước `main`; phải đặt property khi JVM startup.
7. **“Dùng `super()` trong provider constructor vẫn ổn như `super(parent)`.”** Có nguy cơ vòng khởi tạo vì `super()` tìm system loader làm parent. Dùng tham số `parent` được truyền.
8. **“Property này thay bootstrap/platform loader.”** Sai. Nó chọn system loader; các tầng loader còn lại vẫn có vai trò riêng.
9. **“TCCL của mọi thread luôn bằng system loader.”** Sai. TCCL gắn với từng thread và có thể được ứng dụng/framework thay đổi.
10. **“Tính năng này là hot reload class toàn JVM.”** Sai. Thay system loader ở startup không tự tải lại định nghĩa lớp đã có; hot reload/redefinition là cơ chế khác với điều kiện riêng.
11. **“Không có rủi ro nếu constructor làm việc phức tạp.”** Sai. Lỗi nạp provider, sai constructor hoặc khởi tạo vòng có thể chặn startup trước `main`.
12. **“Custom system loader mặc định có quyền mở mọi package/module.”** Sai. Việc truy cập module/bytecode và phân giải kiểu vẫn chịu quy tắc JVM, module và visibility.

## 11. Tự kiểm tra

**Câu 1.** `CustomSystemLoader` cần constructor nào để JVM chọn bằng `-Djava.system.class.loader=CustomSystemLoader`?

**Đáp án:** `public CustomSystemLoader(ClassLoader parent)`. Lớp provider phải được default system loader tìm thấy; constructor dùng parent đó làm delegation parent.

**Câu 2.** Tại sao `system.loadClass("DemoTarget")` trong ví dụ chạy custom loader nhưng `type.getClassLoader() == system` lại là `false`?

**Đáp án:** Vì `super.loadClass` ủy quyền tìm `DemoTarget` cho parent tích hợp; parent định nghĩa lớp và trở thành defining loader. Custom chỉ khởi đầu và trả kết quả của lời gọi.

**Câu 3.** Có thể gọi `System.setProperty("java.system.class.loader", "...")` sau khi chạy `main` để đổi system loader tức thì không?

**Đáp án:** Không. Java không cung cấp cơ chế thay instance system loader bằng việc sửa property sau khi nó đã được thiết lập.

**Câu 4.** Trong constructor provider, tại sao nên dùng `super(parent)` và tránh gọi `ClassLoader.getSystemClassLoader()`?

**Đáp án:** Vì instance system loader mới đang được tạo; lấy system loader đệ quy có thể kích hoạt `IllegalStateException`. Constructor đã nhận sẵn default loader cần dùng làm parent.

**Câu 5.** Một thư viện gọi `Thread.currentThread().getContextClassLoader()` nhưng vẫn dùng loader cũ. Đã chắc property không hoạt động chưa?

**Đáp án:** Chưa. TCCL là trạng thái theo từng thread và có thể bị framework/app thay đổi; phải kiểm tra TCCL, system loader và defining loader riêng biệt.

**Câu 6.** Bạn muốn tải một plugin JAR khác đường dẫn mà không thay class loading mặc định của ứng dụng. Nên ưu tiên lựa chọn nào?

**Đáp án:** Một custom/`URLClassLoader` chuyên biệt được truyền tường minh tới thành phần plugin, thay vì thay global system loader chỉ cho nhu cầu cục bộ.

## 12. Sơ đồ ra quyết định

```text
Cần nạp class từ nguồn / đường dẫn tùy chỉnh?
                         |
                         v
     Có thành phần nào bắt buộc dùng getSystemClassLoader()
          và cần tác động trên toàn ứng dụng không?
              /                         \
            Không                        Có
              |                          |
   Tạo loader chuyên biệt          Đặt property lúc startup
   và dùng trực tiếp                 java.system.class.loader
              |                          |
   Quản lý plugin/lifecycle       Provider tìm được bởi default loader?
                                      |
                                      v
                              public constructor(ClassLoader)?
                                      |
                                      v
                              super(parent), thiết kế delegation
                                      |
                                      v
                                Chạy demo/quan sát loader
                                và defining loader thực tế
```

**Ghi nhớ ba ý chính:** `java.system.class.loader` chọn **instance system loader được công bố**, JVM tạo instance qua **public constructor(ClassLoader parent)** và truyền **default system loader** làm parent, còn **loader nào thực sự định nghĩa từng lớp** phụ thuộc cách ủy quyền và tìm bytecode. Nắm vững ba điểm này sẽ tránh được phần lớn sai lầm khi đổi loader cấp ứng dụng.

### Tài liệu tham khảo

- [Java SE 17 — ClassLoader.getSystemClassLoader()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#getSystemClassLoader())
- [Java SE 21 — ClassLoader API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — URLClassLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [JVMS §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)


</details>

- [Quay lại đầu trang](#back-to-top)
