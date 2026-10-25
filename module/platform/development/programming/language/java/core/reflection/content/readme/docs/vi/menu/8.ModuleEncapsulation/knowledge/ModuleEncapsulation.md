<a id="back-to-top"></a>

# Module và đóng gói

## Menu
- [JPMS và strong encapsulation](#module-encapsulation-001)
- [Readability, exports và opens](#module-encapsulation-002)
- [Public reflection khác deep reflection](#module-encapsulation-003)
- [Điều kiện truy cập khi caller và lớp đích ở cùng module](#module-encapsulation-004)
- [Named module, unnamed module và open module](#module-encapsulation-005)
- [Module.isExported, isOpen và kiểm tra ngữ cảnh truy cập](#module-encapsulation-006)
- [InaccessibleObjectException và nguyên nhân không thể mở truy cập](#module-encapsulation-007)
- [Phân biệt --add-exports với --add-opens](#module-encapsulation-008)
- [Giới hạn của --illegal-access trong Java hiện đại](#module-encapsulation-009)
- [Thiết kế API công khai thay vì phụ thuộc nội bộ JDK](#module-encapsulation-010)

## <a id="module-encapsulation-001">JPMS và strong encapsulation</a>

<details>
<summary>Xem chi tiết</summary>

# JPMS và strong encapsulation trong Java Reflection

## 1. Bức tranh tổng thể: vì sao reflection vẫn bị giới hạn?

**JPMS (Java Platform Module System)**, xuất hiện từ Java 9, cho phép một ứng dụng tổ chức mã thành các **module có tên** (named module). Mỗi module khai báo những module khác mà nó cần đọc, những package công khai cho mã bên ngoài và những package cho phép truy cập sâu bằng reflection. Điều này bổ sung một ranh giới đóng gói ở cấp module bên cạnh `public`, `protected`, package-private và `private` của ngôn ngữ Java.

**Strong encapsulation (đóng gói chặt)** nghĩa là mã bên ngoài không thể mặc nhiên vượt qua ranh giới package của một named module chỉ vì nó có một `Class`, `Method`, `Field` hay `Constructor`. Đặc biệt, `setAccessible(true)` không còn là “chìa khóa vạn năng” để mở mọi thành viên private. Việc package có được **mở** cho module của bên gọi hay không quyết định khả năng truy cập sâu.

Bài học này tập trung vào tác động của JPMS đối với **Java Reflection**. Mục đích học là:

- Phân biệt `requires`, `exports` và `opens` đúng theo chức năng.
- Hiểu vì sao có thể **tìm thấy** phương thức private bằng `getDeclaredMethod()` nhưng **không gọi được**.
- Dự đoán `canAccess()`, `trySetAccessible()` và `setAccessible(true)` trong hai module khác nhau.
- Chạy một ví dụ có thể tái hiện trên Java 17+ và mở đúng một package khi thực sự cần.
- Đọc lỗi liên quan đến module, sửa ở đúng nơi và tránh mở toàn bộ nội bộ thư viện.

### Ba câu hỏi độc lập cần đặt ra

1. **Bên gọi có đọc được module chứa API không?** Quan hệ đọc module (readability) thường được khai báo bằng `requires` khi biên dịch mã tham chiếu trực tiếp kiểu của module khác.
2. **Package có xuất API công khai ra bên ngoài không?** `exports` cho phép sử dụng các kiểu public và thành viên public có thể truy cập theo quy tắc Java thông thường.
3. **Package có mở cho truy cập sâu bằng reflection không?** `opens` cho phép reflection phá qua kiểm tra truy cập Java trong phạm vi JPMS cấp quyền; quyền này thường dùng cho framework cần đọc constructor/field private.

Một module có thể trả lời “có” cho câu hỏi 1 và 2 nhưng “không” cho câu hỏi 3. Đó là lý do một lời gọi public chạy tốt trong khi một lời gọi private qua reflection bị từ chối.

## 2. Module và package: hai lớp đóng gói

Trước khi dùng JPMS, một thư viện thường đóng gói bằng `public`, `protected`, package-private và `private`. Với JPMS, package nằm trong một named module còn có chính sách được phép xuất/mở ra các module khác.

Ví dụ cấu trúc:

~~~text
com.example.library  (named module)
  ├── module-info.java
  └── com.example.library.api
        └── SecretBox.java

com.example.client   (named module)
  ├── module-info.java
  └── com.example.client
        └── Main.java
~~~

`module-info.java` của thư viện định nghĩa ranh giới module. Module khách muốn gọi `SecretBox` từ mã Java thông thường cần có khả năng đọc module thư viện **và** cần package chứa kiểu đó được xuất phù hợp. Khi muốn dùng reflection vào `private`, việc xuất package chưa đủ.

| Cơ chế | Mục đích chính | Cho phép truy cập thành viên private bằng reflection từ module khác? |
| --- | --- | --- |
| `requires com.example.library;` trong client | Client đọc được module thư viện theo quan hệ phụ thuộc | **Không** tự cấp quyền |
| `exports com.example.library.api;` trong library | Xuất API công khai của package cho các module đọc được | **Không** tự cấp quyền truy cập sâu |
| `exports com.example.library.api to com.example.client;` | Chỉ xuất API công khai cho module được nêu | **Không** tự cấp quyền truy cập sâu |
| `opens com.example.library.api;` | Mở package cho reflection từ các module khác | **Có thể**, khi các điều kiện reflection khác cũng thỏa mãn |
| `opens com.example.library.api to com.example.client;` | Mở cho reflection từ đúng module được nêu | **Có thể**, với client được nêu |
| `open module com.example.library { ... }` | Mở mọi package trong module cho reflection | Có thể, nhưng phạm vi rất rộng; không dùng `opens` riêng bên trong `open module` |
| Không `exports`, không `opens` | Giữ package nội bộ ở mức module | Client khác không có quyền sử dụng API thông thường hoặc truy cập sâu chỉ nhờ reflection |

**`opens` không thay thế `exports` khi mã Java muốn `import` kiểu public lúc biên dịch.** Ngược lại, **`exports` không thay thế `opens` khi framework cần đọc private field**. Một package có thể khai báo cả hai khi thực sự cần cả API thông thường và truy cập sâu.

Ngoài ra, khi khai báo `exports ... to ...` hay `opens ... to ...`, tên sau `to` là **tên module**, không phải tên package và cũng không phải tên lớp. Quyền được cấp theo package, không theo từng phương thức riêng lẻ.

## 3. “Tìm được” và “truy cập được” là hai bước khác nhau

Các API khám phá metadata không có cùng ý nghĩa với các API sử dụng thành viên:

| Thao tác | Bạn đang hỏi gì? | Kết quả cần hiểu |
| --- | --- | --- |
| `getDeclaredMethod("secret")` | Lớp này có tự khai báo phương thức tên `secret` không? | Có thể tìm thấy private method; không đồng nghĩa với được gọi |
| `getMethod("greet")` | Có phương thức public phù hợp theo quy tắc tra cứu của API không? | Có thể tìm thấy public method; quyền sử dụng còn phụ thuộc khả năng truy cập kiểu/package/module |
| `method.getModifiers()` | Khai báo phương thức chứa bit cờ nào? | Trả về metadata; không tự mở ranh giới truy cập |
| `method.canAccess(receiver)` | Với caller hiện tại và receiver này, kiểm tra truy cập có đang cho phép không? | Trả về `boolean`; dùng đúng receiver cho thành viên instance |
| `method.trySetAccessible()` | Có thể tắt kiểm tra quyền Java trên đối tượng reflection này không? | `true` khi yêu cầu thành công, `false` khi không thể do giới hạn truy cập như module |
| `method.setAccessible(true)` | Yêu cầu tắt kiểm tra quyền Java, hoặc báo lỗi | Có thể ném `InaccessibleObjectException` nếu JPMS không cho phép |
| `method.invoke(receiver)` | Thực sự gọi phương thức | Có thể ném `IllegalAccessException` nếu không có quyền; ngoại lệ do chính phương thức ném được bọc trong `InvocationTargetException` |

Ở mức truy cập ngôn ngữ Java, một public class trong exported package và một public member của nó có thể được client truy cập mà không cần `opens`. Nhưng private member của chính class đó vẫn bị giới hạn khi client dùng reflection.

Việc gọi thành công `trySetAccessible()` **không chỉnh sửa bytecode** hoặc chuyển modifier `private` thành `public`. `getModifiers()` vẫn phản ánh khai báo. Sự thay đổi chỉ liên quan đến việc bỏ kiểm tra truy cập khi sử dụng **đối tượng reflection cụ thể** trong phạm vi mà runtime cho phép.

### Những trường hợp có quyền suppress access

Theo hợp đồng của `AccessibleObject`, việc tắt kiểm tra có thể được cho phép trong một số trường hợp, chẳng hạn:

- Caller và kiểu khai báo thành viên thuộc **cùng module**.
- Thành viên public của public class nằm trong package được xuất phù hợp tới module của caller.
- Một số trường hợp `protected static` của public class trong exported package khi caller là subclass.
- Package của kiểu khai báo được **mở** cho module của caller; đây là trường hợp quan trọng nhất khi một framework ở module khác cần đọc/gọi private member.

Đây là các điều kiện quyền của API, không phải mẹo bypass; hãy dựa vào chính sách module của ứng dụng.

## 4. Ví dụ hoàn chỉnh Java 17+: hai module, một package chỉ được exports

Ví dụ có một module thư viện chứa phương thức public và private, một module khách gọi cả hai bằng reflection. **Mặc định, thư viện chỉ `exports` package; chưa `opens`.**

Tạo đúng các tệp sau từ một thư mục làm việc trống.

**Tệp `src/com.example.library/module-info.java`:**

~~~java
module com.example.library {
    exports com.example.library.api;
}
~~~

**Tệp `src/com.example.library/com/example/library/api/SecretBox.java`:**

~~~java
package com.example.library.api;

public class SecretBox {
    public String greet() { return "hello"; }
    private String secret() { return "hidden"; }
}
~~~

**Tệp `src/com.example.client/module-info.java`:**

~~~java
module com.example.client {
    requires com.example.library;
}
~~~

**Tệp `src/com.example.client/com/example/client/Main.java`:**

~~~java
package com.example.client;

import com.example.library.api.SecretBox;
import java.lang.reflect.Method;

public class Main {
    public static void main(String[] args) throws Exception {
        SecretBox box = new SecretBox();
        Class<?> type = box.getClass();

        System.out.println("public call: " + box.greet());
        Method greet = type.getMethod("greet");
        System.out.println("public reflection: " + greet.invoke(box));

        Method secret = type.getDeclaredMethod("secret");
        System.out.println("declared: " + secret.getName());
        System.out.println("trySetAccessible: " + secret.trySetAccessible());
        try {
            System.out.println("invoke secret: " + secret.invoke(box));
        } catch (IllegalAccessException ex) {
            System.out.println("invoke blocked: " + ex.getClass().getSimpleName());
        }
    }
}
~~~

**Biên dịch** từ thư mục chứa `src/` (cần JDK 17 trở lên):

~~~bash
javac --release 17 --module-source-path src -d out -m com.example.library,com.example.client
~~~

Tham số `--module-source-path src` xác định cây mã nguồn theo tên module; `-m` chỉ các module cần biên dịch, và `-d out` tạo các thư mục `out/com.example.library`, `out/com.example.client`. Lệnh này dùng được ở command prompt/PowerShell nếu đang ở đúng thư mục làm việc có `src`.

**Chạy** module client:

~~~bash
java --module-path out -m com.example.client/com.example.client.Main
~~~

**Stdout đã được xác minh bằng cách biên dịch `--release 17` và chạy trên JDK 21:**

~~~text
public call: hello
public reflection: hello
declared: secret
trySetAccessible: false
invoke blocked: IllegalAccessException
~~~

**Vì sao từng dòng xảy ra?**

1. `requires com.example.library` và `exports com.example.library.api` cho phép client biên dịch/làm việc bình thường với `SecretBox`, gọi public `greet()`.
2. `getMethod("greet")` trả về public method; `invoke` được phép qua exported API.
3. `getDeclaredMethod("secret")` vẫn tìm thấy private method và in được tên phương thức. Việc tìm metadata không cần mở package theo cùng cách như truy cập sâu.
4. `trySetAccessible()` trả `false` vì `com.example.library.api` chưa mở cho `com.example.client`.
5. Khi gọi `invoke`, kiểm tra quyền vẫn có hiệu lực; `IllegalAccessException` được bắt và in dưới dạng tên ngoại lệ.

Kết quả trên là hành vi của ví dụ cụ thể với hai **named module** trên module path. Khi chạy mã trên classpath/unnamed module hoặc thay chính sách `opens`, kết quả có thể khác; không khái quát rằng mọi lời gọi private reflection đều thất bại.

## 5. Mở đúng package cho reflection

Có hai cách mở package phù hợp với ví dụ: sửa khai báo module ở mã nguồn thư viện hoặc cấu hình lúc chạy khi bạn có quyền vận hành ứng dụng.

### Cách A — khai báo `opens ... to ...` trong thư viện

Thay nội dung `src/com.example.library/module-info.java` bằng:

~~~java
module com.example.library {
    exports com.example.library.api;
    opens com.example.library.api to com.example.client;
}
~~~

Biên dịch lại và chạy bằng **hai lệnh không đổi** ở phần 4. Giờ stdout là:

~~~text
public call: hello
public reflection: hello
declared: secret
trySetAccessible: true
invoke secret: hidden
~~~

`exports` phục vụ lời gọi public và `import`; `opens ... to com.example.client` cho phép module khách truy cập sâu vào package này bằng reflection. Quyền mở áp dụng cho toàn package được nêu, nên cần cân nhắc trước khi cấp.

### Cách B — thêm `--add-opens` khi chạy

Giữ `module-info.java` ban đầu (chỉ `exports`) và chạy bằng lệnh:

~~~bash
java --module-path out --add-opens com.example.library/com.example.library.api=com.example.client -m com.example.client/com.example.client.Main
~~~

**Sau khi biên dịch với phiên bản chỉ `exports` ở phần 4**, stdout trùng với trường hợp đã mở ở cách A:

~~~text
public call: hello
public reflection: hello
declared: secret
trySetAccessible: true
invoke secret: hidden
~~~

Cú pháp là `--add-opens <module-nguồn>/<package>=<module-đích>`. Trong trường hợp thư viện cần mở cho chương trình classpath (unnamed modules), đích thường là `ALL-UNNAMED`; ví dụ `--add-opens com.example.library/com.example.library.api=ALL-UNNAMED`.

Hãy phân biệt:

- `--add-opens`: mở package cho truy cập sâu bằng reflection ở runtime.
- `--add-exports`: bổ sung quyền nhìn thấy API public của package, không tự cấp quyền đọc private field/method bằng reflection.
- `--add-reads`: bổ sung quan hệ đọc giữa module, không tự cấp `exports` hoặc `opens`.

Các cờ này thay đổi ranh giới runtime theo cấu hình triển khai; chúng không thay đổi các modifier trong class file và không sửa `module-info.java` đã biên dịch. Nếu muốn chính sách lâu dài có thể kiểm soát ở nguồn, hãy ưu tiên thiết kế khai báo module có chủ đích.

## 6. Strong encapsulation đối với JDK nội bộ và thư viện cũ

JPMS không chỉ áp dụng cho module do chính ứng dụng viết. Các package nội bộ của **JDK** cũng được đóng gói trong các named module như `java.base`.

Các mốc lịch sử cần phân biệt:

- **Java 9:** đưa JPMS vào Java; để hỗ trợ chuyển đổi từ ứng dụng cũ, JDK từng có cơ chế nới lỏng truy cập đối với một số package nội bộ.
- **Java 16:** JEP 396 đưa đóng gói chặt các phần nội bộ JDK thành mặc định, vẫn có cơ chế chuyển đổi tạm thời.
- **Java 17:** JEP 403 đóng gói chặt các package nội bộ JDK theo mặc định và khiến lựa chọn `--illegal-access` cũ không còn hiệu lực để mở rộng truy cập. Một số API đặc biệt như `sun.misc.Unsafe` thuộc `jdk.unsupported` có ngoại lệ/chính sách riêng; đừng từ đó suy ra mọi API nội bộ đều được mở.

Một ứng dụng vốn chạy trên JDK 8 có thể bị lỗi sau khi nâng lên JDK 17 nếu framework hay thư viện dùng `setAccessible(true)` để truy cập trường/phương thức private trong package JDK không mở. Cách xử lý bền vững là nâng cấp framework, dùng API Java được hỗ trợ hoặc thay chiến lược truy cập. Nếu có một nhu cầu tương thích chưa thể bỏ ngay, `--add-opens` có mục tiêu có thể giúp chuyển tiếp.

Ví dụ cờ mở `java.lang` của `java.base` cho tất cả unnamed modules:

~~~bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp app.jar example.Main
~~~

Đây là **ví dụ cú pháp cấu hình**, không phải một chương trình chạy độc lập: `app.jar` và `example.Main` đại diện cho ứng dụng thực tế. Không thêm cờ này khi chưa có bằng chứng rằng ứng dụng cần truy cập sâu `java.lang`. Việc mở package nội bộ có thể làm giảm khả năng kiểm soát đóng gói và tăng phụ thuộc vào chi tiết triển khai của JDK.

### Vì sao classpath và module path cho kết quả khác nhau?

| Cách nạp ứng dụng/thư viện | Trạng thái JPMS liên quan |
| --- | --- |
| Các class từ classpath | Thuộc **unnamed module** của class loader liên quan; package của unnamed module được xuất và mở rộng rãi theo hợp đồng module |
| JAR thường đặt trên module path, không có `module-info.class` | Có thể trở thành **automatic module** với tên module được xác định theo JAR; các package của automatic module được xuất/mở rộng rãi để tương thích |
| Named module có `module-info.java` tường minh | Chính sách `requires`, `exports`, `opens` do tác giả xác định; package không tự mở chỉ vì có reflection |
| Các module JDK | Có quy định `exports`/`opens` riêng; việc code ứng dụng nằm trên classpath không tự mở các package JDK bị đóng gói chặt |

Vì vậy, trường hợp một thư viện riêng chạy tốt trên classpath **không chứng minh** rằng truy cập sâu sẽ thành công khi thư viện được chuyển sang named module tường minh. Chiều ngược lại cũng đúng: cần biết module nào khai báo thành viên và module nào gọi reflection trước khi chẩn đoán.

## 7. Kiểm tra module và chính sách mở tại runtime

Để chẩn đoán lỗi, có thể hỏi đối tượng `Module`:

~~~java
Module library = com.example.library.api.SecretBox.class.getModule();
Module client = com.example.client.Main.class.getModule();

String pkg = "com.example.library.api";
System.out.println(library.getName());
System.out.println(client.canRead(library));
System.out.println(library.isExported(pkg, client));
System.out.println(library.isOpen(pkg, client));
~~~

Đây là **đoạn mã minh họa chẩn đoán** đặt trong ngữ cảnh có thể tham chiếu hai lớp, không phải một file Java độc lập. Với module descriptors ở phần 4, lần lượt thu được:

~~~text
com.example.library
true
true
false
~~~

Khi khai báo `opens com.example.library.api to com.example.client;`, dòng cuối thành `true`. Nếu đặt mã này trong một lớp khác, giá trị `canRead`, `isExported`, `isOpen` phải được hiểu theo hai module cụ thể đang kiểm tra.

- `Class#getModule()`: xác định module thật sự chứa class được nạp.
- `Module#isNamed()`: giúp phân biệt named module và unnamed module.
- `Module#canRead(other)`: xem quan hệ đọc thực tế; điều này không tự chứng minh quyền gọi private member.
- `Module#isExported(packageName, target)`: xem package có được xuất cho module đích không.
- `Module#isOpen(packageName, target)`: xem package có được mở cho module đích không.

Đối với các framework dựa vào reflection, **quyền truy cập được xét theo module của mã thực sự gọi API reflection**, thường là module framework, không nhất thiết là module của ứng dụng cuối cùng. Vì vậy, `opens ... to` phải nêu đúng module nhận quyền, ví dụ module framework cần truy cập các trường private của model.

## 8. Phân biệt ngoại lệ thường gặp

| Lỗi / kết quả | Thường xuất hiện khi nào? | Hướng kiểm tra |
| --- | --- | --- |
| `NoSuchMethodException` | `getMethod`/`getDeclaredMethod` không tìm được chữ ký yêu cầu | Kiểm tra tên, kiểu tham số, public-vs-declared và lớp thực sự khai báo |
| `IllegalAccessException` | `invoke`, `Field.get/set` hoặc `Constructor.newInstance` không được phép truy cập | Kiểm tra modifier, lớp gọi, package, `exports`/`opens` và trạng thái accessible |
| `InaccessibleObjectException` | `setAccessible(true)` yêu cầu bỏ kiểm tra nhưng JPMS không cho phép | Xem thông báo lỗi chỉ rõ module/package nguồn và module đích; chỉnh quyền mở có mục tiêu |
| `trySetAccessible() == false` | Không thể bật truy cập sâu trong ngữ cảnh hiện tại | Kiểm tra module đích thực sự và `opens`; ưu tiên API công khai |
| `InvocationTargetException` | Chính phương thức được gọi ném ngoại lệ | Kiểm tra `getCause()`; đây là lỗi của code được gọi, không mặc nhiên là lỗi JPMS |
| Lỗi biên dịch “package ... is not visible” | Mã nhập kiểu từ package không được exported tới module của caller hoặc thiếu khả năng đọc phù hợp | Kiểm tra `requires` và `exports`; chỉ mở package bằng `opens` không đủ cho `import` thông thường |

Ví dụ thông báo `InaccessibleObjectException` có thể nói module A “does not opens package X to module B”. Khi đó sửa module **A** để mở package **X** cho module **B** (hoặc cấu hình `--add-opens A/X=B` nếu phù hợp). Không suy ra rằng chỉ cần thêm `requires A` trong B.

Lưu ý `trySetAccessible()` không phải đảm bảo không bao giờ ném ngoại lệ trong mọi môi trường: các kiểm tra bảo mật hoặc việc dùng API sai có thể có kết quả riêng. Trong ví dụ phần 4, nó trả về `false` do giới hạn JPMS.

## 9. Cách thiết kế hệ thống có reflection mà vẫn giữ đóng gói

**Ưu tiên thứ tự sau khi thiết kế thư viện hoặc framework:**

1. **Công khai đúng API cần dùng.** Khi caller chỉ cần một hành vi, chọn một public method/interface thích hợp thay vì lấy private field bằng reflection.
2. **Tách API public khỏi implementation nội bộ.** Chỉ `exports` các package chứa hợp đồng chính thức. Không xuất toàn bộ package implementation chỉ để đáp ứng một đoạn mã tiện dụng.
3. **Mở đúng package cho đúng framework cần truy cập sâu.** Với một thư viện có quyền sửa `module-info.java`, có thể dùng `opens com.example.model to framework.module;`. Tên này chỉ mang tính ví dụ; phải thay bằng tên module thực tế.
4. **Theo dõi quyền được cấp.** Khi dùng `--add-opens` cho việc chuyển đổi, lưu lý do, phiên bản thư viện, phần cấu hình triển khai và kế hoạch thay thế. Tránh `ALL-UNNAMED` hoặc `open module` khi đã biết tên module cụ thể của bên cần quyền.
5. **Kiểm tra trên chính kiểu triển khai mục tiêu.** Kiểm thử module path nếu sản phẩm triển khai bằng named module; thử cả các ca package mở và đóng để phát hiện giả định ngầm từ classpath.

Trong hệ sinh thái framework, một thư viện có thể hỗ trợ API công khai ở package `api` qua `exports`, còn dữ liệu model dùng để deserialize/inject ở package `model` qua `opens` có mục tiêu. Ranh giới này vừa cho phép framework thực hiện công việc vừa hạn chế phần API triển khai bị ràng buộc vào mã bên ngoài.

### Những sai lầm dễ lặp lại

- **“`setAccessible(true)` luôn ép được private.”** Sai khi named module không cho phép suppress access.
- **“`exports` và `opens` là như nhau.”** `exports` dành cho truy cập API thông thường; `opens` phục vụ reflection truy cập sâu.
- **“Tìm được bằng `getDeclaredField` là có thể `get` hoặc `set`.”** Metadata khác với thao tác lên đối tượng.
- **“Thư viện hoạt động trên classpath thì chắc chắn hoạt động y nguyên trên module path.”** Quyền của unnamed/automatic module khác named module khai báo tường minh.
- **“Mở mọi package để nhanh hết lỗi.”** Cách này bỏ qua giới hạn đóng gói và thường che giấu vấn đề phiên bản hoặc thiết kế API.
- **“Có `requires` thì private reflection tự được phép.”** `requires` là quan hệ đọc module, không phải quyền mở package.
- **“`--illegal-access=permit` sửa được lỗi JDK 17.”** Tùy chọn cũ không còn có tác dụng nới truy cập JDK internals trên Java 17.

## 10. Thực hành và câu hỏi kiểm tra

**Bài tập 1 — Xuất public nhưng không mở private.** Giữ `exports com.example.library.api;` và bỏ mọi `opens`. Dự đoán kết quả hai lời gọi `greet.invoke(box)` và `secret.trySetAccessible()`.

**Đáp án:** Lời gọi public thành công, còn `trySetAccessible()` trả `false` trong ví dụ hai named module. Khám phá được private member không đủ để vượt ranh giới mở package.

**Bài tập 2 — `opens` nhưng không `exports`.** Sửa module library chỉ còn `opens com.example.library.api to com.example.client;`. Giữ nguyên mã client có dòng `import com.example.library.api.SecretBox;`. Dự đoán kết quả biên dịch.

**Đáp án:** Mã client không được phép tham chiếu trực tiếp public type từ package không exported, nên biên dịch thất bại; `opens` không thay `exports`. Nếu muốn client gọi `SecretBox` qua API Java bình thường và cũng dùng private reflection, cần cấp cả hai quyền phù hợp.

**Bài tập 3 — Quyền mở có đích.** `opens com.example.library.api to com.example.client;` được khai báo. Module thứ ba `com.example.other` cũng muốn gọi private member. Module thứ ba được quyền tương tự không?

**Đáp án:** Không tự động; quyền mở được cấp cho đúng `com.example.client`, trừ khi có chính sách mở khác hoặc cấu hình runtime bổ sung.

**Bài tập 4 — Chuyển từ classpath sang module path.** Một thư viện phụ thuộc vào private field reflection, chạy ổn trước khi được chuyển thành named module tường minh; sau đó `trySetAccessible()` trả `false`. Bạn cần kiểm tra gì?

**Đáp án:** Xác định module của class khai báo field, module của code thực sự gọi reflection và package chứa field; kiểm tra `isOpen(package, callerModule)`. Ưu tiên API được hỗ trợ hoặc khai báo `opens ... to ...` chính xác.

**Bài tập 5 — Phân loại lỗi.** `getDeclaredMethod("secret")` thành công; `setAccessible(true)` ném `InaccessibleObjectException`. Nên sửa tên phương thức hay ranh giới module?

**Đáp án:** Việc tìm metadata đã thành công, vì vậy kiểm tra ranh giới mở package và chính sách JPMS; đổi tên phương thức không giải quyết nguyên nhân.

## 11. Ghi nhớ

JPMS bổ sung ranh giới cấp module để kiểm soát package được **đọc**, **xuất API công khai** và **mở cho reflection**. Với Java Reflection, cần tách ba việc: *tìm thành viên*, *đọc modifier*, *thực sự truy cập*. `getDeclared...` có thể nhìn thấy khai báo private, nhưng `trySetAccessible()` chỉ thành công khi quyền module cho phép. Trong thiết kế lâu dài, hãy giữ `exports` cho API chính thức và chỉ `opens` những package thực sự cần truy cập sâu, ưu tiên mở cho một module được chỉ định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-002">Readability, exports và opens</a>

<details>
<summary>Xem chi tiết</summary>

# Readability, exports và opens trong Java Platform Module System (JPMS)

Readability, `exports` và `opens` trả lời ba câu hỏi khác nhau về ranh giới module: **module nào được đọc**, **public API nào được sử dụng từ bên ngoài**, và **package nào cho phép deep reflection**. Bài này đi sâu vào cách các quyền phối hợp, kể cả `requires transitive`, `requires static`, qualified exports/opens và quyền runtime khác quyền khai báo.

Dấu hiệu thực tế: ứng dụng gọi public API bình thường, nhưng framework thất bại ở `Field.setAccessible(true)`. Quan hệ đọc và `exports` có thể đã đầy đủ; chính package chứa field chưa `opens` cho module **thực sự thực hiện Reflection**.

## 1. Module, package và module descriptor

Một named module thường có file module-info.java tại gốc source module. Ví dụ:

~~~java
module com.example.billing {
    requires java.sql;
    exports com.example.billing.api;
    opens com.example.billing.entity to com.example.framework;
}
~~~

Ý nghĩa:

- **requires java.sql**: module billing đọc module java.sql và có thể tham chiếu các public type trong package mà java.sql exports.
- **exports com.example.billing.api**: các public type và public member có khả năng truy cập thông thường từ module đọc billing, theo các quy tắc Java visibility.
- **opens com.example.billing.entity to com.example.framework**: module com.example.framework được phép thực hiện *deep reflection* trên package entity, kể cả private field/constructor, khi API hỗ trợ bỏ kiểm tra truy cập.
- Package không exports cũng không opens được **strongly encapsulated** với các module khác, ngoại trừ quyền riêng của code bên trong module.

JPMS không thay thế public/private/protected. Một class package-private không trở thành public chỉ vì package được exports; một private field không thành thành viên public chỉ vì package được opens. JPMS bổ sung **ranh giới truy cập giữa các module** trên các quy tắc visibility Java có sẵn.

## 2. Readability: quan hệ requires giữa module

**Readability** trả lời câu hỏi: “Module A có thể đọc module B hay không?” Trong phần lớn ứng dụng dạng named module, A khai báo requires B để trình biên dịch cho phép tham chiếu trực tiếp các kiểu của B.

~~~java
module com.example.app {
    requires com.example.library;
}
~~~

- Nếu app requires library, app đọc library; nhưng **đọc được chưa đủ** để import một class trong package không exports.
- Mọi named module đều đọc **java.base** một cách ngầm định; không cần khai báo requires java.base.
- Một module đọc chính nó.
- Module.canRead(...) và quan hệ module graph còn có thể bị bổ sung khi sử dụng module layer hoặc tùy chọn JVM; không nên suy diễn mọi quyền từ file module-info.java mà bỏ qua cấu hình thực tế.

### requires transitive và requires static

Giả sử app dùng facade và facade dùng api:

~~~text
com.example.app -> com.example.facade -> com.example.api
~~~

Nếu facade chỉ khai báo:

~~~java
module com.example.facade {
    requires com.example.api;
    exports com.example.facade;
}
~~~

thì app requires facade nhưng **không tự động đọc api** chỉ vì facade đọc api. Khi API public của facade để lộ các type thuộc api và muốn consumer đọc api qua facade, có thể dùng:

~~~java
module com.example.facade {
    requires transitive com.example.api;
    exports com.example.facade;
}
~~~

Khi đó một module đọc facade cũng đọc api nhờ quan hệ transitive. Điều này **không tự mở** package của api: api vẫn phải exports package cần dùng.

**requires static** biểu thị dependency bắt buộc ở thời điểm biên dịch, nhưng không buộc dependency đó phải có mặt và được giải quyết khi chạy. Nếu chương trình thực sự dùng type từ module tùy chọn trong một đường chạy, thiếu module đó vẫn có thể gây lỗi. Đây không phải cách mở Reflection.

Có thể kiểm tra readability ở runtime bằng **moduleA.canRead(moduleB)**; trả về boolean, và chỉ kiểm tra cạnh đọc module, không kiểm tra exports/opens của từng package.

## 3. Exports: API thông thường có thể truy cập

Mẫu khai báo:

~~~java
module com.example.library {
    exports com.example.library.api;
    exports com.example.library.spi to com.example.plugin;
}
~~~

- **exports p;** cho phép các module có quyền đọc module này truy cập các public type/public member trong package p bằng Java code thông thường.
- **exports p to m;** là *qualified export*: chỉ mở quyền export cho module đích m (hoặc danh sách module được chỉ định); module khác không thể mặc định import API p.
- exports **không cho phép tự ý truy cập private field/private method** của các class trong package.
- exports chỉ tác động đến package đã khai báo; nó không tự động exports subpackage. Ví dụ exports com.example.api **không đồng nghĩa** exports com.example.api.internal.

Cùng một class public, điều kiện truy cập trực tiếp từ một named module khác gồm: module nguồn được đọc, package chứa class được exports phù hợp, bản thân class là public, và thành viên được dùng đáp ứng quy tắc Java visibility.

Nếu package không exported, lệnh import trực tiếp từ module khác thường bị javac báo lỗi kiểu “package ... is not visible”. Thêm requires vào module caller **không giải quyết** lỗi không exports; phải xét lại API boundary hoặc khai báo exports phù hợp.

## 4. Opens: quyền Reflection sâu

Một framework như serializer, dependency injection container hoặc ORM thường cần đọc/gán private field hoặc gọi constructor không public. Khi hai module khác nhau, exports chỉ cho truy cập API public, còn **opens** cho phép Reflection bỏ kiểm tra truy cập đối với mọi type/member trong package đã mở cho module thực hiện Reflection.

~~~java
module com.example.library {
    exports com.example.library.api;
    opens com.example.library.model to com.example.serializer;
}
~~~

Những điểm cần phân biệt:

- **opens p;** mở package p cho mọi module thực hiện deep reflection.
- **opens p to m;** chỉ mở cho module m; đây là lựa chọn có phạm vi hẹp hơn khi đã biết framework.
- **open module name { ... }** mở tất cả package trong module cho deep reflection tại runtime. Trong open module không dùng thêm lệnh opens từng package. Open module **không tự biến các package thành exports cho việc import khi biên dịch**.
- Package chỉ opens vẫn không phải là API để mã nguồn của module khác import trực tiếp. Compiler kiểm soát điều này dựa trên các exports đã khai báo.
- opens không có nghĩa mọi Reflection call đều thành công: vẫn cần đúng đối tượng, kiểu tham số và xử lý lỗi khi gọi; còn những package chưa mở có thể trả false từ trySetAccessible() hoặc ném InaccessibleObjectException khi setAccessible(true).

Các API liên quan:

| API | Mục đích | Lưu ý |
| --- | --- | --- |
| Class.getDeclaredField/getDeclaredMethod/getDeclaredConstructor | Tra cứu cả thành viên non-public | Lấy metadata chưa đồng nghĩa được phép dùng thành viên |
| AccessibleObject.setAccessible(true) | Đề nghị bỏ kiểm tra access cho thao tác Reflection | Có thể ném InaccessibleObjectException nếu JPMS không cho phép |
| AccessibleObject.trySetAccessible() | Thử mở quyền và nhận boolean | false khi không thể bật quyền theo policy thông thường |
| Module.isOpen(package, targetModule) | Package được mở cho module target? | Đúng khi opens qualified/unqualified phù hợp |
| Module.isExported(package, targetModule) | Package có được xem là exported **ở runtime** cho target? | Package được opens cho target cũng được coi là exported ở runtime |
| Module.canRead(otherModule) | Module hiện tại đọc otherModule? | Không thay thế kiểm tra exports/opens |

**Chi tiết dễ nhầm của API:** Theo JDK 17, nếu package được **opens** cho module m, Module.isExported(package, m) có thể trả **true** vì JVM xem package được mở là exported cho module đó **ở runtime**. Điều này **không có nghĩa** file module-info.java có exports hay javac cho phép import class từ package chỉ opens. Để kiểm tra exports được **khai báo**, đọc **Module.getDescriptor().exports()**.

## 5. Ví dụ chạy được: requires + exports + opens trong Java 17+

Ta tạo hai named modules:

- **com.acme.library** exports package API public, đồng thời opens package internal riêng cho client.
- **com.acme.client** requires library; gọi API public theo cú pháp Java bình thường và dùng Reflection để đọc một private field trong package internal.

### 5.1. Cấu trúc thư mục

Tại thư mục dự án, tạo đúng các file sau:

~~~text
src/
├── com.acme.library/
│   ├── module-info.java
│   └── com/acme/library/
│       ├── api/Service.java
│       └── internal/Secret.java
└── com.acme.client/
    ├── module-info.java
    └── com/acme/client/Main.java
~~~

**File src/com.acme.library/module-info.java:**

~~~java
module com.acme.library {
    exports com.acme.library.api;
    opens com.acme.library.internal to com.acme.client;
}
~~~

**File src/com.acme.library/com/acme/library/api/Service.java:**

~~~java
package com.acme.library.api;

public final class Service {
    private Service() {}

    public static String greet() {
        return "hello";
    }
}
~~~

**File src/com.acme.library/com/acme/library/internal/Secret.java:**

~~~java
package com.acme.library.internal;

public final class Secret {
    private final String token = "hidden";
}
~~~

Lớp Secret là public để ví dụ rõ ràng rằng **public class trong package không exports vẫn không thể import trực tiếp từ client**. Constructor của Secret không được khai báo tường minh, nên Java tạo default constructor tương ứng với visibility public của class? **Không**: default constructor có cùng access với class public, vì vậy trong trường hợp này là public. Dù vậy, vì package internal chỉ opens cho client, Reflection vẫn cần bật quyền truy cập khi gọi constructor từ module khác.

**File src/com.acme.client/module-info.java:**

~~~java
module com.acme.client {
    requires com.acme.library;
}
~~~

**File src/com.acme.client/com/acme/client/Main.java:**

~~~java
package com.acme.client;

import com.acme.library.api.Service;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public final class Main {
    public static void main(String[] args) throws Exception {
        Module client = Main.class.getModule();
        Module library = Service.class.getModule();

        System.out.println("reads=" + client.canRead(library));
        System.out.println("api exported="
                + library.isExported("com.acme.library.api", client));

        boolean declaredInternalExport = library.getDescriptor().exports().stream()
                .anyMatch(e -> e.source().equals("com.acme.library.internal"));
        System.out.println("internal declared export=" + declaredInternalExport);
        System.out.println("internal runtime exported="
                + library.isExported("com.acme.library.internal", client));
        System.out.println("internal open="
                + library.isOpen("com.acme.library.internal", client));

        System.out.println("greet=" + Service.greet());

        Class<?> secretType = Class.forName("com.acme.library.internal.Secret");
        Constructor<?> ctor = secretType.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object secret = ctor.newInstance();

        Field field = secretType.getDeclaredField("token");
        field.setAccessible(true);
        System.out.println("token=" + field.get(secret));
    }
}
~~~

### 5.2. Biên dịch và chạy

Trong thư mục chứa src, chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 --module-source-path src -d mods -m com.acme.library,com.acme.client
java --module-path mods --module com.acme.client/com.acme.client.Main
~~~

Cú pháp trên không phụ thuộc glob mở rộng file của shell; có thể chạy tương tự trong terminal Windows PowerShell và các shell phổ biến khác. **--module-source-path src** cho javac biết layout nhiều module, **-d mods** đặt output vào thư mục mods, **-m** chọn module cần compile, còn **--module-path mods** và **--module name/package.Main** xác định module/class để chạy.

Stdout, đã kiểm tra thực tế khi biên dịch mã nguồn với **--release 17** và chạy bằng JDK 21:

~~~text
reads=true
api exported=true
internal declared export=false
internal runtime exported=true
internal open=true
greet=hello
token=hidden
~~~

### 5.3. Vì sao có thể đọc private field?

- **reads=true**: client khai báo requires library.
- **api exported=true**: library exports com.acme.library.api, nên import và gọi Service.greet() thành công.
- **internal declared export=false**: module-info.java không khai báo exports cho package internal.
- **internal runtime exported=true**: Java Module API xem package đã **opens to client** như exported **tại runtime** đối với client. Giá trị này không có nghĩa compiler cho import Secret.
- **internal open=true**: client được mở Reflection sâu vào package internal. Constructor và private field cần setAccessible(true) để vượt qua kiểm tra truy cập Java/JPMS.
- **token=hidden**: sau khi được phép, Field.get() đọc private instance field trên đối tượng Secret.

Nếu bạn thêm dòng import com.acme.library.internal.Secret; vào Main.java, javac không chấp nhận vì package chỉ opens, **không exports**. Đoạn code đang chạy không dùng import này; nó tải class bằng tên và thực hiện Reflection trong phạm vi opens đã cho phép.

## 6. Thử thay đổi quyền và dự đoán lỗi

### Trường hợp A: có requires nhưng thiếu exports API

Đổi module-info.java của library thành:

~~~java
module com.acme.library {
    opens com.acme.library.internal to com.acme.client;
}
~~~

Khi biên dịch lại cùng Main.java, import com.acme.library.api.Service bị từ chối vì package api không exported. Giữ requires trong client **không đủ**. Cách đúng là khôi phục exports com.acme.library.api hoặc đổi kiến trúc sao cho client không phải tham chiếu trực tiếp API đó.

### Trường hợp B: có exports API nhưng thiếu opens internal

Đổi module-info.java của library thành:

~~~java
module com.acme.library {
    exports com.acme.library.api;
}
~~~

Mã nguồn Main vẫn compile (vì nó không import internal.Secret). Nhưng khi chạy và đến ctor.setAccessible(true), JVM không cho client mở quyền constructor của class nằm trong package internal chưa được opens cho client, nên sẽ có **java.lang.reflect.InaccessibleObjectException**. Khôi phục opens ... to com.acme.client, hoặc cấp quyền mở có phạm vi thích hợp khi khởi chạy.

### Trường hợp C: exports internal nhưng không opens internal

~~~java
module com.acme.library {
    exports com.acme.library.api;
    exports com.acme.library.internal;
}
~~~

Client có thể dùng public API của Secret từ Java code thông thường, nhưng **không thể tùy ý đọc private field token** bằng Field.setAccessible(true). Muốn Reflection vào private members từ module khác thì cần opens (hoặc cơ chế quyền khác hợp lệ). Đây là khác biệt căn bản giữa exports và opens.

### Trường hợp D: opens không có to

~~~java
module com.acme.library {
    exports com.acme.library.api;
    opens com.acme.library.internal;
}
~~~

Bất kỳ module nào đủ điều kiện thực hiện Reflection ở runtime đều có thể yêu cầu deep reflection đối với package internal. Chỉ mở rộng như vậy khi cần; với framework đã biết tên module, **opens ... to framework.module** diễn đạt ranh giới quyền rõ hơn.

## 7. Thay đổi quyền ở runtime với --add-reads, --add-exports, --add-opens

Trong quá trình chuyển ứng dụng cũ sang JPMS, đôi lúc cần các tùy chọn launcher/compiler để điều chỉnh module graph và khả năng truy cập:

| Tùy chọn | Ý nghĩa chính | Điều không giải quyết |
| --- | --- | --- |
| --add-reads A=B | Cho module A đọc module B | Không tự exports hay opens package của B |
| --add-exports A/p=B | Cho B quyền truy cập public API trong package p của A | Không cho Reflection vào private members |
| --add-opens A/p=B | Mở package p của A cho deep reflection từ B | Không biến package thành API import trực tiếp khi compile mã nguồn |

Ví dụ nếu thư viện đã biên dịch không có opens phù hợp, có thể thử một lần với tùy chọn **runtime**:

~~~bash
java --module-path mods --add-opens com.acme.library/com.acme.library.internal=com.acme.client --module com.acme.client/com.acme.client.Main
~~~

Câu lệnh cho phép module com.acme.client deep-reflect package internal mà không sửa class/module-info đã biên dịch. Trong ví dụ gốc, opens đã được khai báo nên tùy chọn này **không cần thiết**. Nếu thử Trường hợp B (gỡ opens khỏi module-info), tùy chọn trên sẽ khôi phục quyền Reflection.

Đối với ứng dụng trên classpath, nhóm code đó thuộc *unnamed module*; tên đích đặc biệt **ALL-UNNAMED** dùng để mở cho các unnamed modules, chẳng hạn --add-opens java.base/java.lang=ALL-UNNAMED. Tuy nhiên không nên mở bừa package của JDK hay toàn ứng dụng: hãy xác định đúng module caller, đúng package và lý do cần deep reflection. Từ Java 17, việc truy cập nội bộ JDK bị đóng gói mạnh hơn; lỗi InaccessibleObjectException là dấu hiệu nên ưu tiên API public hoặc cấu hình opens có chủ đích.

Với **--add-exports** phục vụ import trực tiếp, đôi khi cần cấu hình phù hợp **cả lúc biên dịch và khi chạy**; một thay đổi runtime không tự sửa lỗi javac ở bước compile. Tùy chọn **--add-opens** hướng đến kiểm tra Reflection runtime, không thay thế exports để compile import.

## 8. Readability có liên quan gì đến Reflection?

Thông thường, Java source cần module readability cùng exports để tham chiếu một public class bên ngoài. Reflection có đặc tính riêng: khi kiểm tra access của các đối tượng reflective, API **giả định readability**; đó là lý do có thể tra cứu class bằng tên và dùng Reflection trong một số trường hợp mà source code không import được. Nhưng Reflection **không bỏ qua module encapsulation**: package phải exports phù hợp cho public access hoặc opens phù hợp khi muốn deep reflection.

Ví dụ này cố ý khai báo requires trong client để đường phụ thuộc rõ ràng; Class.forName không phải biện pháp thay thế module configuration cho phần mã nguồn phụ thuộc thư viện.

Mở package bằng opens thường cần thiết cho các kỹ thuật như Field.setAccessible(true), Constructor.setAccessible(true), Method.setAccessible(true) và MethodHandles.privateLookupIn khi module caller khác module chứa type. API Module.isOpen(package, callerModule) là phép kiểm tra hữu ích để chẩn đoán.

Đừng nhầm việc **tìm thấy** một Field/Method qua getDeclared... với việc **được phép gọi** nó. getDeclaredMethods() có thể liệt kê private members, nhưng invoke/get/set hoặc setAccessible vẫn phải tuân thủ quy định truy cập.

## 9. Quy trình chẩn đoán lỗi module khi dùng Reflection

1. **Xác định module caller thực sự:** nơi thực hiện setAccessible/MethodHandles lookup, có thể là module của framework chứ không phải module ứng dụng. Dùng SomeClass.class.getModule() hoặc thông tin stack trace.
2. **Xác định module chứa lớp và package:** targetClass.getModule(), targetClass.getPackageName(). Đừng nhầm package của class với tên module.
3. **Kiểm tra readability và exports:** moduleCaller.canRead(moduleTarget) giúp phân tích dependency; package có export phù hợp nếu cần dùng public API từ mã nguồn.
4. **Kiểm tra opens đúng caller:** moduleTarget.isOpen(packageName, moduleCaller) phải phù hợp khi cần private reflective access.
5. **Đối chiếu module-info.java với thực tế runtime:** dùng Module.getDescriptor().exports()/opens() để xem khai báo và nhớ rằng Module.isExported có thể true khi opens tại runtime.
6. **Sửa đúng phạm vi:** ưu tiên khai báo exports cho API thật sự public, opens ... to framework cho data binding/serialization, hoặc API public thay thế. Chỉ dùng launcher flags có mục tiêu khi cần tương thích hoặc kiểm chứng nguyên nhân.

**Ví dụ nhanh:** lỗi “module A does not 'opens p' to module B” chỉ ra B là module thực hiện Reflection; thêm requires A ở B **không** cấp quyền mở private field trong p. Cần xem xét opens p to B ở A hoặc cấu hình runtime tương ứng.

## 10. Sai lầm thường gặp và bài tập tự kiểm tra

- **“requires cho phép dùng mọi package”:** sai, phải xét exports và visibility; private Reflection còn cần opens.
- **“exports cũng cho setAccessible vào private”:** sai. exports phục vụ truy cập thành viên public theo luật Java; opens dành cho deep reflection.
- **“opens cho phép import tại compile time”:** sai. Opens là quyền Reflection/runtime, exports mới công khai API với javac.
- **“Module.isExported true chắc chắn có exports trong module-info.java”:** sai khi package được opens cho caller. Kiểm tra ModuleDescriptor.exports() nếu muốn xác nhận khai báo exports.
- **“Mở com.example.model cũng mở com.example.model.internal”:** sai; mỗi package có ranh giới riêng.
- **“Có thể luôn gọi setAccessible(true) vì member đã tìm được”:** sai, đặc biệt với named modules.
- **“Dùng --add-opens ở mọi nơi sẽ giải quyết modularity”:** cấu hình quá rộng che vấn đề thiết kế API, khó bảo trì và tăng phạm vi truy cập nội bộ.

Tự kiểm tra ba tình huống:

1. Module A requires B, nhưng package b.internal của B không exports. A có thể import b.internal.Secret không? **Không**; A đọc B nhưng không được B exports package đó.
2. B exports b.api, không opens b.api. A có thể gọi public method trên public class trong b.api nếu requires B? **Có**. A có thể dùng setAccessible(true) để truy cập private field của class đó không? **Không**, trừ khi có quyền truy cập hợp lệ khác.
3. B opens b.entity to framework.orm, không exports b.entity. Module framework.orm có thể dùng private reflection với b.entity khi đúng API và quyền mở? **Có**. Module app khác có thể import b.entity.Entity trong source của mình chỉ dựa trên opens đó không? **Không**.

**Ghi nhớ:** **requires** tạo quan hệ đọc module, **exports** cung cấp API public giữa module, **opens** cấp quyền Reflection sâu cho package. Ba yếu tố cần được kiểm tra **riêng**, nhất là khi gọi Reflection từ framework trong module khác.

### Tài liệu tham khảo

- JLS 17 — §7.7 Module Declarations: https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7
- Java SE 17 — Class Module API (canRead/isExported/isOpen): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html
- Java SE 17 — AccessibleObject API và setAccessible: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html
- JEP 261 — Module System: https://openjdk.org/jeps/261
- JEP 403 — Strongly Encapsulate JDK Internals: https://openjdk.org/jeps/403


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-003">Public reflection khác deep reflection</a>

<details>
<summary>Xem chi tiết</summary>

# Public reflection khác deep reflection như thế nào trong Java?

Trong Java Reflection, **tra cứu metadata** (`getMethod`, `getDeclaredMethod`) và **thực sự truy cập thành viên** (`invoke`, `Field.get`, `Constructor.newInstance`) là hai bước khác nhau. Bài này phân biệt **public reflection** — sử dụng API public mà caller vốn có quyền truy cập — và **deep reflection** — thao tác cần bỏ qua kiểm tra truy cập Java bằng `trySetAccessible()` hoặc `setAccessible(true)`.

Với named modules, public API dùng xuyên module thường cần `exports`; truy cập private member của module khác thường cần package `opens` cho caller. Đây là hai cách sử dụng Reflection, không phải hai kiểu `Class` hay hai thư viện API riêng. Ví dụ Java 17+ sẽ cho thấy cùng một chương trình thay đổi kết quả như thế nào khi bổ sung quyền mở package.

## 1. Ba lớp câu hỏi cần tách rời

Khi một framework muốn gọi `Person.secret()` hoặc đọc `Person.name`, hãy trả lời theo đúng thứ tự:

1. **Có tìm được thành viên không?** `getMethod("name")` tìm public method; `getDeclaredMethod("secret")` tìm method khai báo trực tiếp, kể cả private.
2. **Caller có được truy cập thành viên bằng Reflection không?** Quy tắc visibility `public`/`private`, quan hệ giữa class/package, và JPMS `exports`/`opens` quyết định điều này.
3. **Việc sử dụng thực tế có thành công không?** `Method.invoke`, `Field.get`, `Field.set`, `Constructor.newInstance` còn có thể thất bại vì receiver, tham số, trạng thái đối tượng hoặc exception bên trong code được gọi.

Ví dụ:

```java
Method method = type.getDeclaredMethod("secret");
```

Lệnh trên chỉ tra cứu metadata. Nếu `secret` là private và caller ở module khác, `method.invoke(object)` chưa chắc được phép. Cần xét việc package đã được `opens` chưa và caller đã bật quyền reflective access hợp lệ hay chưa.

**Cần nhớ:** `public` là mức truy cập của class/member, còn `exports`/`opens` là quan hệ **package giữa các module**. Một method `public` nằm trong class `public` của package không được export vẫn có thể không gọi được từ module khác theo public reflection.

## 2. So sánh trực tiếp

| Khía cạnh | Public reflection | Deep reflection |
|---|---|---|
| Mục đích | Sử dụng API công khai qua runtime metadata | Truy cập thành viên hoặc kiểu không được phép truy cập theo Java access checks thông thường |
| API tra cứu điển hình | `getMethod`, `getField`, `getConstructor` | `getDeclaredMethod`, `getDeclaredField`, `getDeclaredConstructor` |
| Có thể thấy metadata private? | Các API `get...` không trả về private member | Các API `getDeclared...` có thể trả metadata private; **chỉ tra cứu chưa đủ để truy cập** |
| Bỏ qua kiểm tra Java access? | Không cần với API public hợp lệ và package được export | Cần yêu cầu qua `trySetAccessible()` hoặc `setAccessible(true)` khi access checks bình thường không cho phép |
| Named module cung cấp package | Thường dùng `exports package.name` hoặc `exports package.name to caller.module` | Thường dùng `opens package.name` hoặc `opens package.name to caller.module` |
| Cho phép import class từ module khác ở compile time? | `exports` cho phép sử dụng public types khi có module readability phù hợp | Chỉ `opens` không biến package thành API được import như `exports` |
| Rủi ro thiết kế | Phụ thuộc vào hợp đồng API công khai | Phụ thuộc vào chi tiết nội bộ và có thể phá vỡ encapsulation |

Bảng mô tả tình huống **giữa các named modules** thông thường. Có ngoại lệ trong quy tắc `AccessibleObject` cho cùng module, các package vốn mở, hoặc một số public/protected static member với quan hệ subclass phù hợp. Không suy ra rằng cứ thấy `private` là luôn cần sửa `module-info.java`: nếu caller và lớp đích **cùng module**, yêu cầu bật reflective access có thể hợp lệ dù package không `opens` ra ngoài.

## 3. Tra cứu metadata không đồng nghĩa được gọi

Các API tra cứu trên đối tượng `Class<?>`:

```java
// Các đoạn dưới là ví dụ ký hiệu, giả định Sample.class đã tồn tại.
Method publicMethod = Sample.class.getMethod("execute");
Method declaredMethod = Sample.class.getDeclaredMethod("internal");
Field declaredField = Sample.class.getDeclaredField("state");
Constructor<Sample> ctor = Sample.class.getDeclaredConstructor();
```

Phân biệt:

- `getMethod`, `getField`, `getConstructor` chỉ tìm những thành viên **public** theo quy tắc tương ứng; với method/field có thể bao gồm thành viên được kế thừa, còn constructor thì không được kế thừa.
- `getDeclaredMethod`, `getDeclaredField`, `getDeclaredConstructor` tra cứu các thành viên **khai báo trực tiếp** trên lớp, bao gồm public và non-public; không tự đi qua superclass.
- `getDeclaredMethods()` hay `getDeclaredFields()` có thể liệt kê metadata của private members mà không thực hiện được quyền gọi/đọc chúng. **Không cần `opens` chỉ để dùng `getDeclaredMethod` tìm tên method** trong trường hợp minh họa thông thường.
- `Method.invoke`, `Field.get`/`set`, `Constructor.newInstance` mới thực hiện hành động, áp dụng access rules và các điều kiện khác.

Ví dụ đơn giản nếu toàn bộ code nằm trong một package ứng dụng thông thường:

```java
class Example {
    private String secret() {
        return "secret-data";
    }
}
```

```java
Method m = Example.class.getDeclaredMethod("secret");
// Đã có metadata của secret; chưa có quyền gọi method private từ nơi khác.
// Có thể thử m.trySetAccessible(), nếu quan hệ class/module cho phép.
// Chỉ sau khi quyền hợp lệ mới sử dụng m.invoke(instance).
```

Hai đoạn code là **minh họa ngữ nghĩa**, chưa phải chương trình độc lập có `main` và import. Phần kế tiếp có ví dụ đầy đủ biên dịch và chạy được.

## 4. JPMS: vì sao `exports` khác `opens`?

Từ Java 9, `module-info.java` cho phép một named module công bố rõ package nào là API và package nào được mở cho reflective access.

Giả sử module `demo.target` sở hữu package `demo.api`, module `demo.client` chứa caller.

### `exports`: API được sử dụng từ module khác

```java
module demo.target {
    exports demo.api;
}
```

Với khai báo này, `demo.api` được **export** đến mọi module đọc `demo.target`. Nếu `demo.api.Person` là public class và `displayName()` là public method, `demo.client` có thể import/sử dụng class đó và gọi method công khai. Reflection gọi `displayName()` theo access checks bình thường cũng hợp lệ.

`exports` **không cho quyền** truy cập private field/method bằng `setAccessible(true)` từ một module khác. Export cũng không khiến các member package-private/protected tự trở thành public.

Có thể giới hạn API chỉ cho một module:

```java
module demo.target {
    exports demo.api to demo.client;
}
```

### `opens`: cho phép deep reflection tại runtime

```java
module demo.target {
    exports demo.api;
    opens demo.api to demo.client;
}
```

Khi đó `demo.client` có thể tiếp tục dùng API public, đồng thời có quyền **yêu cầu suppress Java language access checks qua Reflection** đối với members trong package `demo.api` theo cơ chế JPMS. Cách `opens ... to ...` giới hạn người nhận tốt hơn so với mở package cho mọi module.

Nếu chỉ khai báo:

```java
module demo.target {
    opens demo.api to demo.client;
}
```

package được mở cho Reflection từ `demo.client`, nhưng **không được export** như API public để `demo.client` import `demo.api.Person` trong Java source một cách thông thường. Framework vẫn có thể sử dụng runtime metadata dựa trên tên lớp và reflective access khi có điều kiện phù hợp.

Cũng có hai dạng rộng hơn:

- `opens demo.api;`: mở package cho deep reflection từ **mọi module**.
- `open module demo.target { ... }`: mở **các package của cả module** cho Reflection. Không nên dùng chỉ để giải quyết nhu cầu của một package.

| Khai báo trong module đích | Public API cho caller ở module khác | Deep reflection từ caller ở module khác |
|---|---|---|
| Không `exports`, không `opens` | Không được tiếp cận như exported public API | Bị giới hạn bởi encapsulation |
| `exports demo.api;` | Có, với class/member public và readability phù hợp | Không cấp quyền vào private members |
| `opens demo.api;` | Không tạo quyền import public API chỉ nhờ `opens` | Được phép yêu cầu suppress access checks |
| `exports demo.api;` và `opens demo.api;` | Có | Có |
| `exports demo.api to demo.client;` và `opens demo.api to demo.client;` | Có, cho `demo.client` | Có, cho `demo.client` |

Trong bảng, “có deep reflection” là **mở khả năng đáp ứng yêu cầu suppress access checks** của core Reflection; code vẫn có thể bị ràng buộc bởi hành vi thành viên, kiểu đối tượng, các trường `final` không được ghi tùy trường hợp và exception lúc thực thi.

## 5. Thí nghiệm Java 17+: cùng source, hai kết quả theo quyền module

Tạo cấu trúc tệp:

```text
reflection-demo/
└── src/
    ├── demo.target/
    │   ├── module-info.java
    │   └── demo/api/Person.java
    └── demo.client/
        ├── module-info.java
        └── demo/app/Main.java
```

**Tệp `src/demo.target/module-info.java`:**

```java
module demo.target {
    exports demo.api;
}
```

**Tệp `src/demo.target/demo/api/Person.java`:**

```java
package demo.api;

public class Person {
    private final String name;

    public Person(String name) {
        this.name = name;
    }

    public String displayName() {
        return name;
    }

    private String internalId() {
        return "id-" + name;
    }
}
```

**Tệp `src/demo.client/module-info.java`:**

```java
module demo.client {
    requires demo.target;
}
```

**Tệp `src/demo.client/demo/app/Main.java`:**

```java
package demo.app;

import demo.api.Person;
import java.lang.reflect.Method;

public class Main {
    public static void main(String[] args) throws ReflectiveOperationException {
        Module target = Person.class.getModule();
        Module caller = Main.class.getModule();

        System.out.println("exported: " + target.isExported("demo.api", caller));
        System.out.println("opened: " + target.isOpen("demo.api", caller));

        Person person = new Person("Lan");

        Method publicMethod = Person.class.getMethod("displayName");
        System.out.println("public method: " + publicMethod.invoke(person));

        Method privateMethod = Person.class.getDeclaredMethod("internalId");
        System.out.println("private metadata: " + privateMethod.getName());

        boolean deepAllowed = privateMethod.trySetAccessible();
        System.out.println("deep allowed: " + deepAllowed);

        if (deepAllowed) {
            System.out.println("private method: " + privateMethod.invoke(person));
        }
    }
}
```

**Biên dịch hai named modules bằng JDK 17+** (đứng trong thư mục `reflection-demo`):

```bash
javac -d out --module-source-path src -m demo.target,demo.client
```

### Lần 1: chỉ có `exports`, không có `opens`

```bash
java --module-path out -m demo.client/demo.app.Main
```

Stdout chính xác:

```text
exported: true
opened: false
public method: Lan
private metadata: internalId
deep allowed: false
```

Giải thích:

1. `demo.target` export `demo.api` cho `demo.client`; Java source import `Person` được và gọi public API qua `Method.invoke` thành công.
2. `getDeclaredMethod("internalId")` **vẫn tìm ra metadata của private method**.
3. `trySetAccessible()` trả `false` vì package không open cho module gọi, nên code không đi vào nhánh gọi `internalId()`. Đây là minh chứng cho khác biệt giữa metadata discovery và deep access.

### Lần 2: mở package tại runtime, không sửa source

Chạy với tham số JVM `--add-opens`:

```bash
java --module-path out --add-opens demo.target/demo.api=demo.client -m demo.client/demo.app.Main
```

Stdout chính xác:

```text
exported: true
opened: true
public method: Lan
private metadata: internalId
deep allowed: true
private method: id-Lan
```

Lệnh `--add-opens demo.target/demo.api=demo.client` cấp quyền mở package của **target module** cho **caller module** trong lần chạy này. Không cần thay code `Person`, `Main` hoặc biên dịch lại để quan sát khác biệt. Đây là **quyền truy cập runtime**, không phải quyền import thêm class trong quá trình biên dịch.

### Cách làm tương đương bằng `module-info.java`

Thay nội dung `src/demo.target/module-info.java` bằng:

```java
module demo.target {
    exports demo.api;
    opens demo.api to demo.client;
}
```

Biên dịch lại:

```bash
javac -d out --module-source-path src -m demo.target,demo.client
java --module-path out -m demo.client/demo.app.Main
```

Không cần `--add-opens` ở lần chạy này; stdout giống **lần 2**. Với ứng dụng có cấu trúc module rõ ràng, `opens ... to ...` cho framework cụ thể thường dễ kiểm toán hơn một quyền mở rộng áp dụng không cần thiết.

**Lưu ý:** `--add-opens` phải được truyền cho JVM **trước** `-m` trong ví dụ. Phần `demo.target/demo.api` là `module/package`, bên phải dấu `=` là module được mở quyền. Đường dẫn thư mục `out` chứa các module đã biên dịch.

## 6. `trySetAccessible`, `setAccessible` và `canAccess`

Các đối tượng `Field`, `Method`, `Constructor` kế thừa từ `java.lang.reflect.AccessibleObject`; các API tiêu biểu là:

```java
boolean ok = member.trySetAccessible();
member.setAccessible(true);
boolean canUse = member.canAccess(receiver);
```

Các dòng trên là **các lựa chọn API minh họa**, không phải đoạn chương trình cần gọi liên tiếp. `member` là một `AccessibleObject` thực tế có kiểu `Field`, `Method` hoặc `Constructor`; receiver phụ thuộc loại member.

- `trySetAccessible()`: thử bật quyền **suppress Java language access checks**. Trả `true` nếu cho phép; trả `false` nếu không thể mở theo các ràng buộc đó. Phù hợp với framework muốn hỗ trợ nhiều cấu hình module mà không ném exception khi deep access bị chặn.
- `setAccessible(true)`: yêu cầu bật chế độ này; có thể ném **`InaccessibleObjectException`** nếu không thể bật do encapsulation của module. Với mô hình cũ hoặc access đã mở, lệnh có thể thành công.
- `canAccess(receiver)`: kiểm tra quyền hiện có cho member đối với caller tại thời điểm gọi; đây là truy vấn, **không tự mở** quyền. Với instance field/method, `receiver` phải là đối tượng hợp lệ. Với static field/method và constructor, receiver phải là `null`.

Cụm “suppress Java language access checks” không có nghĩa tắt mọi cơ chế bảo vệ JVM. Một số trường `final` thuộc nhóm **non-modifiable final fields** (chẳng hạn record components hay hidden-class final fields) không thể ghi bằng core Reflection kể cả sau khi `setAccessible(true)` thành công; các ràng buộc runtime khác cũng vẫn tồn tại.

### Thực hành xử lý thất bại

Khi framework chủ động hỗ trợ private members:

```java
Method internal = Person.class.getDeclaredMethod("internalId");
if (!internal.trySetAccessible()) {
    throw new IllegalStateException(
            "Module demo.target must open demo.api to demo.client");
}
Object result = internal.invoke(new Person("Lan"));
```

Đây là **đoạn minh họa** nằm trong module `demo.client` của thí nghiệm, không phải tệp chạy độc lập. Cần cung cấp quyền `opens` hợp lệ trước khi gọi. Với một thư viện dùng thực tế, thông báo lỗi nên mô tả cả module đích, package và module caller được lấy từ `Class.getModule()`, tránh hard-code nếu thư viện hỗ trợ nhiều module.

Không dùng `setAccessible(true)` hàng loạt cho mọi field/method chỉ để “đề phòng”: việc yêu cầu truy cập chi tiết nội bộ nên có mục đích rõ, quyền hẹp và đường xử lý lỗi.

## 7. Public member trong package không export vẫn có thể bị chặn

Xét `demo.target` có thêm package `demo.hidden`, nhưng `module-info.java` chỉ export `demo.api`:

```java
module demo.target {
    exports demo.api;
}
```

Bên trong `demo.hidden` tồn tại `public class HiddenApi` với `public static String status()`. Dù class và method đều ghi `public`, module `demo.client` **không thể import `demo.hidden.HiddenApi` như một public API hợp lệ**, vì package không được export cho nó.

Nếu chương trình có được đối tượng `Class` của `HiddenApi` bằng một đường runtime hợp lệ, việc tra cứu `getMethod("status")` có thể tìm ra metadata. Tuy nhiên, việc gọi qua `Method.invoke` với Java access checks bình thường **không tự động được phép**. Đây là lý do không được đánh đồng hai câu “method được khai báo public” và “module caller có quyền gọi method đó”.

Hai tùy chọn JVM hay bị nhầm:

```bash
# Tăng quyền truy cập public của package chưa export cho demo.client:
--add-exports demo.target/demo.hidden=demo.client

# Mở package cho deep reflection từ demo.client:
--add-opens demo.target/demo.hidden=demo.client
```

Hai dòng này là **các JVM options minh họa**, không phải lệnh shell chạy độc lập. `--add-exports` giải quyết quyền tiếp cận public API của package trong tình huống module đích chưa export; riêng nó không cấp quyền xâm nhập private members bằng deep reflection. `--add-opens` mở quyền reflective access sâu cho package. Hãy chọn đúng tùy mục tiêu, ưu tiên khai báo module rõ ràng hoặc API công khai thay vì nới rộng quyền tùy tiện.

## 8. Ảnh hưởng của classpath, unnamed module và framework

Ứng dụng Java có thể dùng cả **module path** và **classpath**:

- Class được nạp từ classpath thông thường thuộc một **unnamed module**; các package của unnamed module được export và open theo cơ chế tương thích.
- Một **open module** cho phép reflective access vào các package trong module của nó theo quy tắc JPMS.
- Trong **named module thông thường**, các package không tự động export/open cho module khác. Việc di chuyển mã đang chạy trên classpath vào named module có thể làm framework từng truy cập private members gặp lỗi `InaccessibleObjectException`.

Ví dụ:

- **JSON serialization / ORM**: framework thường tìm field hoặc constructor non-public và có thể cần quyền deep reflection. Nếu module `app.model` quản lý các class entity, có thể dùng `opens app.model.entity to some.framework.module;` theo module thực của framework.
- **Dependency injection**: nếu container gọi constructor public từ package được export, public reflection có thể đủ. Nếu container cần private field injection hoặc truy cập non-public constructor, cần xét quyền mở package.
- **Công cụ introspection**: nếu chỉ liệt kê tên method, parameter types hoặc annotation metadata, có thể không cần mở private access. Đừng mở package chỉ vì dùng `getDeclaredMethods()`.
- **API client**: với public interface/service API, `exports` và `getMethod` là lựa chọn tự nhiên, giảm phụ thuộc vào chi tiết triển khai.

**Tên module của framework phải chính xác.** Qualified `opens ... to framework.module` chỉ có hiệu lực với module được nêu đúng tên. Với framework chạy trong unnamed module, một số cấu hình launcher cần dùng `ALL-UNNAMED`, ví dụ `--add-opens my.module/my.pkg=ALL-UNNAMED`; đây là lựa chọn runtime để phục vụ công cụ cũ, không phải mặc định nên áp dụng cho mọi module.

Ngoài core Reflection, `MethodHandles.privateLookupIn` cũng là cơ chế truy cập sâu dưới các điều kiện module phù hợp; nó **không cho phép tự vượt** `opens` của module khác.

## 9. Những lỗi thường gặp

| Biểu hiện | Nguyên nhân điển hình | Hướng giải quyết |
|---|---|---|
| `NoSuchMethodException` khi gọi `getMethod` | Method không public, tên/chữ ký tham số sai, hoặc không tồn tại | Xác nhận đúng tên, parameter types; dùng `getDeclaredMethod` nếu cần metadata của method khai báo trực tiếp |
| `NoSuchFieldException` khi `getField` | Field không public hoặc không tồn tại đúng tên | Kiểm tra field thực tế và cân nhắc `getDeclaredField` |
| `IllegalAccessException` khi `Method.invoke` hoặc `Field.get` | Caller không có quyền access theo visibility và/hoặc module encapsulation | Kiểm tra class/member `public`, package export, hoặc quyền deep reflection hợp lệ |
| `InaccessibleObjectException` ở `setAccessible(true)` | Module đích không open package cho caller | Dùng `opens ... to ...` hoặc `--add-opens` có chủ đích; ưu tiên API public khi có thể |
| `trySetAccessible()` trả `false` | Không thể bật suppression theo quy tắc module/encapsulation | Chọn public API hoặc cấu hình `opens` đúng module; xử lý fallback minh bạch |
| `InvocationTargetException` sau khi được phép invoke | **Code được gọi** ném exception | Xem `getCause()`; đây là lỗi khi thực thi method/constructor, không phải bằng chứng thiếu `opens` |
| Compile báo package không visible | Thiếu `requires`, package chưa export hoặc module graph không phù hợp | Kiểm tra `requires` ở caller, `exports` ở target và module path |
| Hoạt động trên classpath nhưng lỗi khi chuyển sang module path | Strong encapsulation của named modules | Xác định nhu cầu public/deep và khai báo export/open ở phạm vi tối thiểu |

Ở Java 17+, đừng dựa vào cơ chế truy cập trái phép tới JDK internals như trên một số runtime Java cũ. Quy tắc strong encapsulation được tăng cường; chuyển đổi framework cũ nên ưu tiên cập nhật thư viện và cấu hình module đúng phạm vi.

## 10. Những nhầm lẫn cần tránh

1. **“`getDeclaredMethod` đã là deep reflection.”** Chưa đúng: chỉ lấy metadata; deep access phải vượt qua ràng buộc quyền truy cập bằng cơ chế hợp lệ khi cần.
2. **“Class public và method public thì module nào cũng gọi được.”** Sai nếu package không được export phù hợp giữa named modules.
3. **“`exports` cho phép mở private members.”** Sai: export phục vụ public API; `opens` dành cho runtime reflective access sâu.
4. **“`opens` và `exports` tương đương.”** Sai: `opens` không tự cho phép Java source ở module khác import public types như exported package.
5. **“`setAccessible(true)` luôn thành công.”** Sai: có thể ném `InaccessibleObjectException` khi module/package không mở phù hợp.
6. **“`trySetAccessible()` trả false nghĩa là member không tồn tại.”** Sai: metadata đã tìm thấy, nhưng không bật được quyền suppress access checks.
7. **“Method.invoke gặp `InvocationTargetException` chứng tỏ bị module chặn.”** Sai: exception này gói lỗi bên trong method/constructor đã chạy.
8. **“Một package open phải mở cho tất cả.”** Sai: qualified `opens pkg to specific.module` cho phép giới hạn quyền.
9. **“Reflection chỉ đọc metadata cũng cần `--add-opens`.”** Không phải mặc định: hãy phân biệt introspection metadata với deep access trước khi cấp thêm quyền.
10. **“Mở một package nghĩa là framework được ghi mọi field final.”** Sai: nhiều hạn chế của JVM vẫn còn hiệu lực.

## 11. Tự kiểm tra

**Câu 1.** Module `catalog.core` export `catalog.api` chứa `public class Item` và `public String code()`. Module `catalog.client` requires `catalog.core`. Public reflection gọi `code()` được không?

**Trả lời:** Có trong điều kiện class/member public hợp lệ và package được export cho caller. `Item.class.getMethod("code")` trả `Method`; `invoke` với instance hợp lệ có thể chạy mà không cần mở package cho deep reflection.

**Câu 2.** Cùng lớp `Item` có field `private String internalKey`. `getDeclaredField("internalKey")` thành công nhưng `trySetAccessible()` trả false. Điều đó nói gì?

**Trả lời:** Chương trình đã tìm được metadata, nhưng không có quyền bật suppress access checks trong quan hệ class/module hiện tại. Với hai named modules, nên kiểm tra `opens catalog.api to catalog.client` nếu deep reflection thực sự cần thiết.

**Câu 3.** Thêm `exports catalog.api;` có đủ để client đọc private field bằng `Field.get` sau `setAccessible(true)` không?

**Trả lời:** Không. Với caller ở module khác, `exports` không thay `opens`; cần cấu hình mở package phù hợp hoặc thiết kế public API thay thế.

**Câu 4.** Nếu chỉ muốn liệt kê tên các method khai báo trực tiếp, cần gọi `setAccessible(true)` trên toàn bộ method không?

**Trả lời:** Không. `getDeclaredMethods()` trả metadata; đọc tên qua `Method.getName()` không phải thực thi method hay bỏ access checks.

**Câu 5.** Tại sao `--add-opens target.module/p=caller.module` có thể giải quyết lỗi deep reflection trong lần chạy nhưng không làm source `import p.SomeType;` biên dịch được?

**Trả lời:** `--add-opens` là quyền reflective access runtime, khác với xuất public API qua `exports`. Compile-time visibility cần cấu hình module graph và exported package tương ứng.

## Tài liệu chính thức

- Java SE API — `java.lang.reflect.AccessibleObject`: `trySetAccessible`, `setAccessible`, `canAccess`, quy tắc access giữa modules.
- Java SE API — `java.lang.Class`: `getMethod`, `getDeclaredMethod`, `getField`, `getDeclaredField` và `getModule`.
- Java SE API — `java.lang.Module`: `isExported`, `isOpen` và quan hệ quyền truy cập package.
- Java Language Specification — Chương 7 và phần về modules, packages, access control.
- Java Platform Module System (`java` launcher, `javac`) — các tùy chọn `--module-path`, `--module-source-path`, `--add-exports`, `--add-opens`.

**Ghi nhớ:** Public reflection dùng **API công khai** mà caller được phép truy cập; deep reflection yêu cầu **truy cập sâu vào phần triển khai** và có thể cần suppress access checks. Trong JPMS, `exports` công bố public API, `opens` mở quyền runtime Reflection. Tìm ra `Method`/`Field` không có nghĩa đã được phép gọi hoặc đọc nó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-004">Điều kiện truy cập khi caller và lớp đích ở cùng module</a>

<details>
<summary>Xem chi tiết</summary>

# Java Reflection và JPMS: điều kiện truy cập khi caller và lớp đích ở cùng module

Trong Java 9+ (bao gồm Java 17, 21), **JPMS — Java Platform Module System** đặt thêm ranh giới giữa các module. Tuy vậy, khi lớp **thực sự thực hiện Reflection** (*caller*) và lớp **khai báo thành viên cần truy cập** cùng nằm trong **một module**, việc kiểm tra và mở quyền Reflection có quy tắc thuận lợi hơn so với tình huống đi qua ranh giới module. Điều quan trọng là không đánh đồng *cùng module* với *được quyền truy cập trực tiếp mọi thành viên `private`*.

Kết luận cốt lõi:

- **Tra cứu metadata** qua `getDeclaredField()`, `getDeclaredMethod()`, `getDeclaredConstructor()` là bước tìm thành viên; tìm thấy thành viên `private` không có nghĩa là đọc/gọi được ngay.
- **Truy cập Java thông thường** vẫn tuân theo `public`, `protected`, package-private và `private`. Hai lớp khác nhau nhưng ở cùng module không tự động được xem là cùng package hoặc cùng nest.
- Với Reflection, nếu **caller và lớp khai báo thành viên ở cùng module**, `AccessibleObject#trySetAccessible()` có thể bật chế độ bỏ qua kiểm tra truy cập Java cho thành viên đó; `setAccessible(true)` cũng được phép theo điều kiện *same module* của JPMS. Việc sử dụng các thao tác sau khi bật quyền vẫn phải tuân theo những giới hạn riêng, như giới hạn sửa `final`.
- Không cần khai báo `exports` hoặc `opens` **chỉ để phục vụ truy cập nội bộ cùng module**. Hai chỉ thị này chủ yếu điều khiển việc package được sử dụng từ **module khác**.

Bài này làm rõ phạm vi "same module", hai tầng kiểm tra truy cập, cách xác minh bằng mã Java có `module-info.java`, và các trường hợp dễ sai khi xây công cụ Reflection.

## 1. "Caller" và "cùng module" chính xác là gì?

Một module là đơn vị đóng gói các package. Named module được khai báo bằng `module-info.java`; còn mã trên classpath thường thuộc **unnamed module** gắn với class loader.

Trong ngữ cảnh Reflection:

- **Target/declarer** là lớp hoặc interface thực sự **khai báo** field, method hoặc constructor cần thao tác; xem `member.getDeclaringClass()`.
- **Caller** là lớp có mã **thực sự gọi** API kiểm tra/mở quyền Reflection, chẳng hạn `trySetAccessible()`. Không nên mặc định caller là lớp khởi đầu một yêu cầu ở tầng ứng dụng.
- Muốn kiểm tra hai lớp có đúng là **cùng một đối tượng module**, so sánh `Class#getModule()` bằng `==`.

Ví dụ:

~~~java
boolean sameModule = Caller.class.getModule()
        == Target.class.getModule();
~~~

Đoạn trên là **mã minh họa**, với `Caller` và `Target` là hai lớp đã định nghĩa ở ứng dụng. So sánh module theo **danh tính** tốt hơn so sánh chuỗi từ `Module#getName()`: unnamed module có tên `null`; đồng thời cùng một tên module không đủ chứng minh hai lớp thuộc cùng một đối tượng module ở các môi trường class loader/layer khác nhau.

**Đối với unnamed module:** đừng kết luận mọi lớp trên mọi class loader đều ở cùng một unnamed module. Mỗi class loader có unnamed module của nó. Vì vậy hãy kiểm tra đối tượng `Module` thực tế khi lớp được tải động hoặc ứng dụng có nhiều class loader.

## 2. Phân biệt ba câu hỏi về quyền truy cập

Có ba câu hỏi độc lập:

| Câu hỏi | API hoặc quy tắc liên quan | Khi hai lớp cùng module |
| --- | --- | --- |
| **Có tìm thấy metadata của thành viên không?** | `Class#getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor` | Có thể tìm cả thành viên `private` khai báo trực tiếp, nếu tên/chữ ký đúng |
| **Có sử dụng được thành viên theo kiểm tra truy cập hiện tại không?** | Quy tắc Java `public`/`protected`/package-private/`private`, `canAccess(receiver)` | Vẫn phải thỏa quyền thông thường, trừ khi đã bật bỏ qua kiểm tra |
| **Có được bật bỏ qua kiểm tra truy cập Java bằng Reflection không?** | `trySetAccessible()`, `setAccessible(true)` | Điều kiện *declaring class cùng module với caller* cho phép bật quyền; không cần package `opens` |

Ví dụ: `app.Main` và `model.Vault` có thể cùng module, nhưng ở **hai package khác nhau**. Nếu `Vault` có field `private String token`, `Vault.class.getDeclaredField("token")` vẫn trả về `Field`. Tuy nhiên `field.canAccess(vault)` thường trả `false` trước khi bật quyền vì `Main` không phải lớp/nest được phép truy cập `private` của `Vault`. Sau khi `field.trySetAccessible()` thành công, `field.get(vault)` có thể đọc giá trị.

### Quy tắc Java thông thường vẫn giữ nguyên

- `public`: cần chú ý mức truy cập của **cả lớp khai báo lẫn thành viên**; việc package không `exports` không cấm mã trong **cùng module** truy cập các khai báo `public` hợp lệ.
- `protected`: được phép theo quy tắc cùng package hoặc quan hệ kế thừa, với các điều kiện bổ sung đối với truy cập ngoài package. Chỉ "cùng module" là chưa đủ.
- Package-private (không ghi modifier): hai lớp ở **khác package** không được tự động truy cập, cho dù thuộc cùng module.
- `private`: tuân theo quy tắc lớp/nest của Java. Hai lớp top-level không cùng nest không có quyền truy cập trực tiếp `private` của nhau chỉ vì ở cùng module.

**Cùng module không có nghĩa là cùng package** và cũng không biến một thao tác truy cập `private` trong mã nguồn thành hợp lệ. Khi thật sự cần thao tác sâu qua Reflection, quyền mở truy cập là một **bước rõ ràng**.

## 3. Named module nội bộ: không cần `exports` và `opens`

Giả sử một dự án có cấu trúc:

~~~text
src/
└── demo.same/
    ├── module-info.java
    └── lesson/
        ├── app/
        │   └── Main.java
        └── domain/
            └── Vault.java
~~~

Tất cả các file `.java` thuộc **cùng named module** `demo.same`, dù phân thành hai package `lesson.app` và `lesson.domain`.

Nội dung `src/demo.same/module-info.java`:

~~~java
module demo.same {
}
~~~

Module này **không khai báo** `exports lesson.domain;` hoặc `opens lesson.domain;`. Đối với mã nằm trong `demo.same`, điều đó không ngăn truy cập Java `public` hợp lệ từ package khác cùng module, cũng không ngăn mở quyền sâu bằng Reflection khi đáp ứng điều kiện *same module*.

### Lớp đích `Vault`

Tệp **`src/demo.same/lesson/domain/Vault.java`**:

~~~java
package lesson.domain;

public final class Vault {
    private String token;

    private Vault(String token) {
        this.token = token;
    }

    public static Vault create() {
        return new Vault("initial");
    }

    public String status() {
        return "ready";
    }

    private String reveal(String prefix) {
        return prefix + token;
    }
}
~~~

Ở đây `Vault` là lớp `public` để `lesson.app.Main` có thể dùng trực tiếp từ **package khác cùng module**. Nhưng constructor `Vault(String)`, field `token` và method `reveal(String)` là `private`; `Main` không thể gọi/đọc trực tiếp những thành viên đó bằng cú pháp Java thông thường.

### Caller thực hiện Reflection

Tệp **`src/demo.same/lesson/app/Main.java`**:

~~~java
package lesson.app;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import lesson.domain.Vault;

public class Main {
    public static void main(String[] args) throws ReflectiveOperationException {
        System.out.println("Same module: "
                + (Main.class.getModule() == Vault.class.getModule()));
        Vault vault = Vault.create();
        System.out.println("Public access: " + vault.status());

        Field token = Vault.class.getDeclaredField("token");
        System.out.println("Field before: " + token.canAccess(vault));
        System.out.println("Field override: " + token.trySetAccessible());
        System.out.println("Field after: " + token.canAccess(vault));
        System.out.println("Field value: " + token.get(vault));

        Method reveal = Vault.class.getDeclaredMethod("reveal", String.class);
        System.out.println("Method override: " + reveal.trySetAccessible());
        System.out.println("Result: " + reveal.invoke(vault, "value="));

        Constructor<Vault> ctor = Vault.class.getDeclaredConstructor(String.class);
        System.out.println("Constructor before: " + ctor.canAccess(null));
        System.out.println("Constructor override: " + ctor.trySetAccessible());
        Vault created = ctor.newInstance("custom");
        System.out.println("Constructed: " + reveal.invoke(created, "value="));
    }
}
~~~

Chạy từ thư mục dự án bằng **JDK 17+**:

~~~bash
javac --module-source-path src -d out -m demo.same
java --module-path out --module demo.same/lesson.app.Main
~~~

**Stdout đã kiểm chứng:**

~~~text
Same module: true
Public access: ready
Field before: false
Field override: true
Field after: true
Field value: initial
Method override: true
Result: value=initial
Constructor before: false
Constructor override: true
Constructed: value=custom
~~~

### Đọc kết quả từng bước

1. So sánh `Main.class.getModule() == Vault.class.getModule()` trả `true`: đây là một named module duy nhất; không suy đoán dựa trên tên package.
2. `Vault.create()` và `vault.status()` là API `public`, có thể gọi trực tiếp từ `lesson.app` dù `module-info.java` không có `exports`. Hai package nằm nội bộ cùng module.
3. `getDeclaredField("token")` tìm thấy metadata field `private`. `token.canAccess(vault)` trả `false` với caller là `Main` vì truy cập Java thông thường không cho đọc `private` đó.
4. `token.trySetAccessible()` trả `true` vì lớp gọi và lớp khai báo ở cùng module. Sau đó `canAccess` là `true` và `get(vault)` trả `"initial"`.
5. `getDeclaredMethod("reveal", String.class)` tra đúng private method. Sau khi bật quyền, `invoke(vault, "value=")` trả `"value=initial"`.
6. `getDeclaredConstructor(String.class)` tra đúng private constructor. `ctor.canAccess(null)` ban đầu `false`; bật quyền xong, `newInstance("custom")` tạo một đối tượng mới.
7. `reveal.invoke(created, "value=")` trả `"value=custom"`, chứng minh đối tượng tạo qua constructor này có state riêng.

Cả ba trường hợp `Field`, `Method`, `Constructor` đều **tra cứu được trước**, sau đó mới bật quyền và dùng đối tượng Reflection. Ví dụ dùng `trySetAccessible()` rõ ràng để không che khuất bước kiểm tra.

## 4. Vì sao `exports`/`opens` không cần trong ví dụ trên?

JPMS dùng hai chỉ thị có mục đích khác nhau:

| Chỉ thị `module-info.java` | Mục đích điển hình | Có bắt buộc cho caller cùng module không? |
| --- | --- | --- |
| `exports lesson.domain;` | Cho module khác dùng API `public` của package qua truy cập Java thông thường | **Không** |
| `opens lesson.domain;` | Cho module khác truy cập sâu qua Reflection vào các thành viên nếu các điều kiện khác phù hợp | **Không** |
| `opens lesson.domain to some.framework;` | Chỉ định module ngoài nào có quyền deep reflection vào package đó | **Không** |
| Không khai báo hai chỉ thị | Package bị đóng đối với module ngoài theo những ranh giới tương ứng | Mã **cùng module** vẫn theo các quy tắc trong bài |

Điều kiện cùng module là một nhánh cấp phép độc lập trong tài liệu của `AccessibleObject#setAccessible` và `trySetAccessible`. Một framework ở **module khác** không tự hưởng quyền này chỉ vì nó nhận đối tượng `Class` hoặc `Field` do mã trong module đích chuyển qua.

**Không nên thêm `opens` cho cả module** chỉ để cho mã nội bộ truy cập Reflection. Việc mở package là quyết định kiến trúc có tác động tới các module khác và không cần thiết trong ví dụ *same module*.

## 5. `canAccess`, `trySetAccessible` và `setAccessible`

Cả `Field`, `Method` và `Constructor` đều kế thừa từ `AccessibleObject`.

### `canAccess(Object obj)`

Kiểm tra liệu thành viên có được truy cập theo trạng thái hiện tại với caller đang gọi hay không. Đối số `obj` phải đúng loại:

- Field hoặc method **instance**: truyền instance chứa thành viên, ví dụ `field.canAccess(vault)`.
- Field hoặc method **static**: truyền `null`.
- Constructor: truyền `null`, ví dụ `ctor.canAccess(null)`.

Đối số không hợp lệ có thể gây `IllegalArgumentException`; không dùng `canAccess(null)` cho instance field/method chỉ để hỏi "có quyền không".

### `trySetAccessible()`

Thử bật khả năng bỏ qua kiểm tra truy cập Java của đối tượng Reflection. Khi caller và declaring class **cùng module**, điều kiện same-module của JPMS cho phép thao tác này. Hàm trả một `boolean` cho biết có bật được hay không, vì vậy mã thư viện nên kiểm tra kết quả thay vì bỏ qua.

~~~java
if (!member.trySetAccessible()) {
    throw new IllegalStateException("Cannot enable reflective access");
}
~~~

Đoạn trên là **mã minh họa**, với `member` là `AccessibleObject` hợp lệ. Trường hợp trong bài sẽ thành công vì caller và target ở cùng module; trong thiết kế tổng quát cần xử lý cả trường hợp từ chối, đặc biệt khi thay đổi kiến trúc module hoặc caller.

### `setAccessible(true)`

Cũng yêu cầu bỏ qua kiểm tra truy cập Java, nhưng khi yêu cầu không được phép, `setAccessible(true)` có thể ném `java.lang.reflect.InaccessibleObjectException`. `trySetAccessible()` thường thuận tiện hơn nếu bạn muốn kiểm tra khả năng truy cập mà không xử lý ngoại lệ cho trường hợp JPMS từ chối. `setAccessible(false)` đưa trạng thái cờ về mặc định.

**Lưu ý:** bật flag trên **đối tượng `Field`/`Method`/`Constructor` cụ thể**; nó không đổi modifier trong khai báo `private`, không chuyển class thành `public`, không sửa `module-info.java` và không làm mọi đối tượng Reflection khác tự động được mở quyền.

## 6. Điều gì được phép và điều gì vẫn bị giới hạn?

| Tình huống | Cùng module thì sao? | Giải thích |
| --- | --- | --- |
| `public` class và `public` member ở hai package khác nhau | Có thể truy cập bình thường theo luật Java | `exports` không bắt buộc giữa các package **nội bộ module** |
| `private` field/method/constructor khai báo ở lớp khác | Không có quyền trực tiếp chỉ vì cùng module | Có thể tìm qua `getDeclared...` và thử bật quyền Reflection |
| Package-private class ở package khác | Mã Java bên ngoài package không thể trực tiếp gọi bằng tên lớp theo cách thông thường | Reflection có thể tìm bằng tên ở runtime và mở quyền sâu khi thỏa *same module* |
| `protected` member ở package khác | Vẫn áp dụng quy tắc `protected` của Java | Cùng module không thay thế yêu cầu kế thừa/cách dùng receiver |
| `final` field | Có thể được cấp quyền đọc sâu; sửa giá trị có các hạn chế riêng | Đặc biệt các field `static final`, field `final` của record và hidden class không thể ghi chỉ nhờ `setAccessible(true)` |
| Thành viên `static` | Tra cứu/bật quyền vẫn theo các quy tắc trên | Các thao tác kiểm tra/gọi với receiver `null` khi API yêu cầu |
| Một utility thực hiện Reflection từ module khác | Phải xét **module của utility thực sự gọi** | Việc request khởi phát từ module đích không đủ để tạo quyền same-module cho utility |

Đối với `final`, đừng coi việc `trySetAccessible()` thành công là bảo đảm `Field#set` sẽ ghi được. Tài liệu Java phân biệt quyền **đọc** với khả năng **ghi** vào field final không thể sửa; ngoài ra thay đổi field final bằng Reflection có thể vi phạm giả định tối ưu hóa và tính bất biến của đối tượng.

### Package-private class ở package khác cùng module

Giả sử có `lesson.domain.Internal` là một **top-level class package-private**. Mã trong `lesson.app` không thể `import lesson.domain.Internal;` rồi dùng trực tiếp kiểu đó. Tuy nhiên một công cụ Reflection chạy trong cùng module có thể tìm class bằng tên runtime phù hợp, lấy `Class<?>`, đọc metadata, rồi xét quyền truy cập phù hợp. Không nên coi việc Reflection cấp quyền là lý do để bỏ thiết kế API rõ ràng khi chỉ cần dữ liệu `public`.

## 7. Chú ý tới module của nơi gọi API, không chỉ luồng nghiệp vụ

Xét một ứng dụng trong module `business.app`:

~~~text
business.app
  ├── business.Secret
  └── business.Main
reflection.helper
  └── helper.Accessor
~~~

Nếu `business.Main` trực tiếp gọi `field.trySetAccessible()` cho field của `business.Secret`, điều kiện module có thể là *same module*. Nhưng nếu `Main` giao đối tượng `Field` cho `helper.Accessor` rồi **chính `Accessor`** gọi `trySetAccessible()`, caller lúc đó thuộc `reflection.helper`. Việc cấp phép bị xét lại theo module của caller ở thời điểm gọi; nếu package đích không mở phù hợp, yêu cầu deep reflection có thể không được chấp thuận.

Ví dụ kiến trúc trên chỉ minh họa sự khác biệt, không phải chương trình chạy độc lập. Khi dùng thư viện serialization, dependency injection, mapper hoặc test framework, cần xác định **lớp trong module nào thực hiện lời gọi Reflection**. Đây là lý do code hoạt động khi viết trực tiếp trong module ứng dụng nhưng có thể thất bại khi được chuyển sang helper library ở module khác.

## 8. Những lỗi thường gặp

1. **Đồng nhất "cùng module" với "cùng package".** JPMS không thay đổi phạm vi package-private và các quy tắc `protected`. Hai package nội bộ là hai package khác nhau.
2. **Cho rằng `getDeclaredField()` trả về `Field` tức là đã được đọc.** Lookup thành công chỉ xác nhận tồn tại metadata; phải xét `canAccess` hoặc bật quyền hợp lệ trước thao tác `get`/`set`.
3. **Cho rằng `getDeclaredMethod()` tự tìm method kế thừa.** API "declared" tìm thành viên do chính lớp khai báo; nếu cần method `public` được kế thừa, cân nhắc `getMethod()`. Điều này độc lập với JPMS.
4. **Thêm `exports` hoặc `opens` chỉ vì có hai package trong cùng module.** Trong cùng module, quyền internal source access và nhánh same-module của Reflection đã có quy tắc riêng.
5. **So sánh `Module#getName()` thay cho đối tượng `Module`.** Với unnamed module, `getName()` có thể là `null` cho nhiều module khác nhau. Hãy dùng `==` nếu mục tiêu là xác minh chính xác same module.
6. **Không kiểm tra giá trị của `trySetAccessible()`.** Hàm trả `boolean`, còn việc đổi module, đổi vị trí helper hoặc dùng module khác có thể khiến yêu cầu không còn đủ điều kiện.
7. **Dùng sai receiver của `canAccess`.** Constructor và static member yêu cầu `null`; instance member yêu cầu đối tượng instance đúng loại.
8. **Cho rằng được bật access là có thể sửa `static final`.** Reflection không biến mọi field bất biến thành field có thể ghi; hạn chế non-modifiable final vẫn tồn tại.
9. **Dựa vào `--add-opens` để chạy ví dụ same-module.** Ví dụ trong bài không cần tham số đó; nếu phải mở package, cần xem lại module chứa caller thực sự và ranh giới ứng dụng.

## 9. Tự kiểm tra

**Câu 1.** Trong named module `demo.same`, `lesson.app.Main` có được gọi `public static Vault.create()` từ `lesson.domain.Vault` khi `module-info.java` không có `exports lesson.domain;`?

**Trả lời:** Có, nếu `Vault` và method là `public` và cả hai package cùng module. `exports` liên quan chủ yếu đến truy cập từ module khác.

**Câu 2.** Nếu `token` là `private`, vì sao `getDeclaredField("token")` tìm được mà `field.canAccess(vault)` ban đầu trả `false`?

**Trả lời:** Lookup trả metadata cả của private member khai báo trực tiếp; còn `canAccess` xét quyền sử dụng theo quy tắc Java và trạng thái accessibility hiện tại. `Main` là lớp khác, không có quyền private thông thường của `Vault`.

**Câu 3.** Khi `Main` và `Vault` cùng module, `trySetAccessible()` trên private field có thể trả gì?

**Trả lời:** Trong điều kiện bình thường của ví dụ, trả `true` do nhánh cấp phép same-module. Sau đó có thể đọc bằng `Field#get`; không suy ra rằng mọi phép ghi vào final field cũng hợp lệ.

**Câu 4.** Nếu helper ở module khác thực hiện `trySetAccessible()` thì vẫn là "same-module" vì `Main` gọi helper không?

**Trả lời:** Không. Điều kiện xét lớp **thực hiện lời gọi** cùng module với declaring class. Phải đánh giá quyền của helper ở module khác theo quy tắc JPMS tương ứng.

**Câu 5.** Có thể kiểm tra chính xác hai lớp cùng module bằng cách nào?

**Trả lời:** `First.class.getModule() == Second.class.getModule()`. Đừng chỉ đối chiếu tên module, nhất là với unnamed module và nhiều class loader.

**Ghi nhớ:** *cùng module* mở ra khả năng Reflection bỏ qua kiểm tra truy cập Java vào các thành viên của module ấy, nhưng **không làm mất quy tắc `private`/`protected`/package-private của Java source**. Luồng đúng là xác minh module của caller, tìm thành viên theo tên/chữ ký, kiểm tra trạng thái truy cập, bật quyền khi thật sự cần và được phép, rồi mới đọc/gọi/khởi tạo.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-005">Named module, unnamed module và open module</a>

<details>
<summary>Xem chi tiết</summary>

# Named module, unnamed module và open module trong Java Reflection

## 1. Mục tiêu và bối cảnh

Từ Java 9, **Java Platform Module System (JPMS)** bổ sung ranh giới module bên cạnh package, class và access modifier. Hai lớp đều là `public` chưa chắc có thể được truy cập từ bất kỳ module nào: ngoài quyền truy cập ở cấp Java, JVM còn xét **đồ thị module**, khả năng đọc module (`readability`), gói được **export** và gói được **open** cho Reflection.

Khi làm việc với `Class<?>`, `Method`, `Field` và `Constructor`, ba khái niệm cần phân biệt:

- **Named module**: module có tên, thường được khai báo bằng `module-info.java`; các JAR dạng *automatic module* trên module path cũng là named module.
- **Unnamed module**: module không có tên, chứa class được nạp từ **class path**; mỗi class loader có unnamed module của riêng nó.
- **Open module**: một **loại named module** được khai báo bằng `open module ...`; mọi package thuộc module đó được mở cho deep reflection ở runtime.

Ba thuật ngữ không cùng một trục phân loại: **open module là named module**, còn unnamed module là một dạng module riêng. Cần phân biệt thêm chỉ thị `opens package.name` trong một named module thông thường với việc biến **toàn bộ** module thành `open module`.

Sau bài này, bạn có thể đọc metadata module qua Reflection, cấu hình `module-info.java` đúng cho cả API công khai và thư viện dùng Reflection, giải thích lỗi `InaccessibleObjectException` và quyết định khi nào dùng `exports`, `opens`, `open module` hoặc tùy chọn dòng lệnh `--add-opens`.

## 2. JPMS nằm ở đâu trong quá trình truy cập?

Ví dụ có hai module:

```text
com.example.inspector
    |
    | requires com.example.target   (đọc module)
    v
com.example.target
    ├── com.example.target.api
    │       exports ...             (API công khai)
    └── com.example.target.internal
            opens ... to com.example.inspector
                                    (deep reflection được cấp phép)
```

Trong sơ đồ, `requires` thiết lập quan hệ đọc, `exports` cấp quyền sử dụng public API với điều kiện Java/module phù hợp, còn `opens` cho phép deep reflection trong phạm vi được mở. Mở package cho Reflection **không tự cho phép import** public type của package ấy lúc biên dịch.

Điểm riêng của bài này là **chính sách mặc định theo loại module**: unnamed module trên classpath, explicit named module, automatic module và explicit open module. Các module có thể hỗ trợ Reflection theo cách khác nhau dù source Java của caller không thay đổi.

## 3. Bảng so sánh chính xác

| Đặc điểm | Explicit named module thông thường | Unnamed module | Explicit open module |
| --- | --- | --- | --- |
| Cách tạo điển hình | `module name { ... }` trong `module-info.java` | Class/JAR trên `--class-path` hoặc `-cp` | `open module name { ... }` trong `module-info.java` |
| `Module.isNamed()` | `true` | `false` | `true` |
| `Module.getName()` | Tên module | `null` | Tên module |
| Quyền export package | Theo `exports` (và quy tắc truy cập runtime thích hợp) | Mọi package được export | Mọi package mở ở runtime, nhưng **không mặc nhiên là API được export để compile** |
| Deep reflection giữa module | Chỉ package được `opens` phù hợp, hoặc trường hợp truy cập đã được cho phép | Mọi package được mở | Mọi package được mở |
| Giao diện bảo vệ bằng `module-info.java` | Có, theo từng package | Không có descriptor nguồn riêng | Có, nhưng mở toàn bộ package |
| Use case | Library/application thiết kế API và Reflection có chọn lọc | Ứng dụng legacy classpath | Ứng dụng/module chủ ý cho phép framework truy cập Reflection rộng |

### 3.1. Explicit named module

Một module tên `com.example.target` có thể khai báo:

```java
module com.example.target {
    exports com.example.target.api;
    opens com.example.target.internal to com.example.inspector;
}
```

- `exports com.example.target.api;` cho phép module đọc phù hợp dùng public API của package này.
- `opens com.example.target.internal to com.example.inspector;` mở package internal **chỉ cho module được nêu** để deep reflection.
- Những package không export và không open được **đóng gói mạnh** đối với module bên ngoài.
- Có thể viết `exports p to m1, m2;` để export có điều kiện hoặc `opens p;` để mở cho mọi module tại runtime.

Với một module muốn sử dụng API:

```java
module com.example.inspector {
    requires com.example.target;
}
```

Lệnh `requires` tạo readability, nhưng **không tự mở** trường private của package bên target.

### 3.2. Unnamed module

Mọi class nằm trên classpath và được nạp bởi một class loader thuộc về unnamed module của class loader đó. Chạy chương trình dạng:

```bash
java -cp classes UnnamedDemo
```

thường có `UnnamedDemo.class.getModule().isNamed() == false` và `getName() == null`.

Unnamed module **đọc được mọi named module có thể quan sát được**, và export/open mọi package của nó. Đây là cơ chế tương thích để nhiều ứng dụng classpath cũ vẫn chạy. Tuy nhiên, việc một class nằm trong unnamed module **không** tự cho phép nó vượt ranh giới private của các named module khác; muốn phản chiếu vào package đóng của named module, phía **named module đích** phải mở gói hoặc runtime phải được cấu hình tương đương.

Chú ý: không có nghĩa tất cả lớp trong toàn bộ JVM đều nằm trong một unnamed module duy nhất. `ClassLoader.getUnnamedModule()` cho biết unnamed module của một class loader cụ thể.

### 3.3. Open module

Nếu mọi package của module được thiết kế cho deep reflection, có thể khai báo:

```java
open module com.example.target {
    exports com.example.target.api;
}
```

- `open module` mở **tất cả package của module** cho runtime Reflection.
- Các chỉ thị `exports` vẫn cần thiết để thiết kế compile-time API; từ khóa `open` **không** thay cho mọi `exports` ở cấp biên dịch.
- **Không được đặt chỉ thị `opens package.name;` trong body của `open module`**, vì toàn bộ package đã mở.
- Module vẫn có **tên**, có thể khai báo `requires`, `exports`, `uses`, `provides` và tham gia đồ thị module.

Chỉ nên dùng `open module` nếu việc mở toàn bộ package là chủ đích. Với thư viện lớn, `opens một.vài.package to module.framework` thường giúp giữ ranh giới ổn định hơn.

### 3.4. Automatic module là một named module khác cần biết

Một JAR không có `module-info.class` khi đặt trên module path có thể trở thành **automatic module**, tên được lấy theo metadata `Automatic-Module-Name` hoặc suy ra từ tên JAR. Automatic module có `isNamed() == true`, export và mở các package của mình; nó không đồng nhất với unnamed module chỉ vì không có `module-info.java`.

Dùng `Module.getDescriptor().isAutomatic()` khi cần xác định automatic module. Trên explicit named module, có thể dùng `Module.getDescriptor().isOpen()` để xác định khai báo `open module`. Với unnamed module, `getDescriptor()` trả `null`, vì vậy phải kiểm tra trước khi gọi các phương thức trên descriptor.

**Ba tình huống runtime điển hình:** classpath → unnamed module; modular JAR rõ `module-info.class` → explicit named module; JAR không có descriptor trên module path → automatic named module.

## 4. Reflection API liên quan

| API | Ý nghĩa thực tế |
| --- | --- |
| `Class.getModule()` | Tìm module chứa kiểu được phản chiếu. |
| `Module.isNamed()` | Kiểm tra module có tên không. |
| `Module.getName()` | Tên module; `null` với unnamed module. |
| `Module.getDescriptor()` | Metadata `ModuleDescriptor`; `null` nếu unnamed module. |
| `Module.canRead(Module other)` | Module hiện tại có quan hệ đọc được `other` hay không. |
| `Module.isExported(String pkg)` | Package được export **không giới hạn bên nhận** theo ngữ nghĩa runtime hay không. |
| `Module.isExported(String pkg, Module other)` | Package được truy cập/export ở runtime đối với module `other` hay không; **opens có thể khiến kết quả là true**. |
| `Module.isOpen(String pkg)` | Package được mở không giới hạn bên nhận tại runtime hay không. |
| `Module.isOpen(String pkg, Module other)` | Package được mở cho module `other` hay không. |
| `AccessibleObject.setAccessible(true)` | Yêu cầu bỏ qua một số kiểm tra truy cập ngôn ngữ; JPMS vẫn có thể từ chối với `InaccessibleObjectException`. |
| `AccessibleObject.trySetAccessible()` | Cố gắng bật quyền truy cập; trả `false` nếu không thể bật vì các kiểm tra truy cập. |

Lưu ý khi chẩn đoán:

- Một package `opens p to m` có thể làm cả `isOpen(p, m)` và **`isExported(p, m)`** trả `true` ở runtime. Điều này **không chứng minh** descriptor có chỉ thị `exports p to m`; hãy đọc `ModuleDescriptor.exports()` nếu cần phân tích **khai báo**.
- Với `isOpen(pkg, thisSameModule)`, kết quả có thể là `true` vì module có quyền truy cập chính nó. Để kiểm tra quyền của framework bên ngoài, phải truyền **đúng module của framework**, không dùng module nguồn làm đối số thứ hai.
- `Module.isOpen(String pkg)` và overload nhận module khác nhau: một lệnh `opens p to m` là **qualified opening**, nên `isOpen(p)` vẫn có thể là `false`, còn `isOpen(p, m)` là `true`.
- `Class.getModule()` hoạt động với các lớp Java thông thường; đối với kiểu mảng, module tương ứng là module của kiểu thành phần; kiểu nguyên thủy và `void` thuộc `java.base` theo API.
- `ModuleLayer.boot()` cung cấp layer của named modules được resolve tại khởi động; unnamed modules không phải một mục để tra như named module trong layer.

## 5. Ví dụ Java 17+ độc lập: named module mở một package cho Reflection

Ta tạo **hai named module**: `com.example.target` chứa dữ liệu và `com.example.inspector` đọc dữ liệu đó bằng Reflection. Cấu trúc thư mục:

```text
src/
├── com.example.target/
│   ├── module-info.java
│   └── com/example/target/
│       ├── api/PublicApi.java
│       └── internal/Secret.java
└── com.example.inspector/
    ├── module-info.java
    └── com/example/inspector/Inspector.java
```

### 5.1. Mã nguồn module target

File **`src/com.example.target/module-info.java`**:

```java
module com.example.target {
    exports com.example.target.api;
    opens com.example.target.internal to com.example.inspector;
}
```

File **`src/com.example.target/com/example/target/api/PublicApi.java`**:

```java
package com.example.target.api;

public class PublicApi {
    public static String greeting() {
        return "hello";
    }
}
```

File **`src/com.example.target/com/example/target/internal/Secret.java`**:

```java
package com.example.target.internal;

public class Secret {
    private final String value = "jpms";
}
```

Lớp `Secret` được khai báo `public` để ví dụ tập trung vào module boundary; **trường `value` là private** và package `internal` không có chỉ thị `exports`. Để đọc trường, module inspector phải dùng deep reflection, nhờ `opens ... to ...`.

### 5.2. Mã nguồn module inspector

File **`src/com.example.inspector/module-info.java`**:

```java
module com.example.inspector {
    requires com.example.target;
}
```

File **`src/com.example.inspector/com/example/inspector/Inspector.java`**:

```java
package com.example.inspector;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class Inspector {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.example.target.internal.Secret");

        Module target = type.getModule();
        Module inspector = Inspector.class.getModule();

        System.out.println("inspector named=" + inspector.isNamed());
        System.out.println("target named=" + target.isNamed());
        System.out.println("target name=" + target.getName());

        String internal = "com.example.target.internal";
        System.out.println("internal open to inspector="
                + target.isOpen(internal, inspector));
        System.out.println("internal unqualified export="
                + target.isExported(internal));
        System.out.println("internal runtime export to inspector="
                + target.isExported(internal, inspector));

        Constructor<?> ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();

        Field field = type.getDeclaredField("value");
        field.setAccessible(true);
        System.out.println("secret=" + field.get(instance));
    }
}
```

### 5.3. Biên dịch, chạy và quan sát stdout

Đứng ở thư mục chứa `src/`, chạy bằng JDK 17 hoặc cao hơn:

```bash
javac --release 17 -d mods --module-source-path src -m com.example.target,com.example.inspector
java --module-path mods -m com.example.inspector/com.example.inspector.Inspector
```

Kết quả:

```text
inspector named=true
target named=true
target name=com.example.target
internal open to inspector=true
internal unqualified export=false
internal runtime export to inspector=true
secret=jpms
```

Các file trong ví dụ đã được biên dịch với `javac --release 17` và chạy thành công trên JDK 21, stdout khớp nội dung trên.

**Giải thích:**

1. `Inspector` và `Secret` đều thuộc named module.
2. `Class.forName` tìm lớp bằng tên trong môi trường đã resolve module; điều này **không đồng nghĩa** mã Java ở module inspector được phép `import Secret` vì package `internal` không export như compile-time API.
3. `isOpen(internal, inspector) == true` vì target khai báo **qualified opens** cho `com.example.inspector`.
4. `isExported(internal) == false` vì package không được export/open **vô điều kiện** trong trường hợp này.
5. `isExported(internal, inspector) == true` là điểm dễ bất ngờ: khi package được mở tới inspector, API phản ánh quyền truy cập runtime, dù source không có `exports internal`. Đây **không phải** bằng chứng rằng `import Secret` được trình biên dịch Java chấp nhận.
6. `setAccessible(true)` trên constructor và private field thành công nhờ package được mở cho **đúng module caller**, nên in `secret=jpms`.

### 5.4. Thử thay đổi quyền để hiểu lỗi

Nếu xóa dòng `opens com.example.target.internal to com.example.inspector;` rồi biên dịch lại, các thao tác `setAccessible(true)` vào non-public member của `Secret` từ module inspector **không còn được cho phép**. Chương trình sẽ thất bại với `java.lang.reflect.InaccessibleObjectException` khi cố bật khả năng truy cập, trừ khi bạn cấp quyền mở bằng cơ chế khác.

Nếu thay bằng:

```java
module com.example.target {
    exports com.example.target.api;
    opens com.example.target.internal;
}
```

package `internal` được mở **cho mọi module**, thay vì chỉ riêng inspector.

Nếu thay khai báo module bằng:

```java
open module com.example.target {
    exports com.example.target.api;
}
```

toàn bộ package trong target sẽ được mở cho deep reflection, bao gồm `internal`. Các chỉ thị `exports` vẫn là chính sách public API ở mức compile-time. Khi chạy biến thể `open module`, một số phép hỏi `isExported(...)` tại runtime có thể thay đổi so với biến thể chỉ mở có điều kiện, vì quyền runtime đã mở rộng.

**Quan trọng:** mỗi biến thể `module-info.java` phía trên là **một cấu hình thay thế**, không ghép tất cả lại vào cùng file.

## 6. Ví dụ unnamed module trên classpath

Một chương trình Java 17+ ngắn có thể xác nhận module của mã classpath.

File **`UnnamedDemo.java`**:

```java
public class UnnamedDemo {
    public static void main(String[] args) {
        Module module = UnnamedDemo.class.getModule();

        System.out.println("named=" + module.isNamed());
        System.out.println("name=" + module.getName());
    }
}
```

Chạy:

```bash
javac --release 17 UnnamedDemo.java
java -cp . UnnamedDemo
```

Output:

```text
named=false
name=null
```

Cũng là mã nguồn trên, nhưng khi ứng dụng được đóng gói thành module tường minh trên module path và chạy bằng `-m`, `Class.getModule()` sẽ phản ánh **named module chứa class** tương ứng.

Đặc biệt, không nên xem `getName() == null` như tên chuỗi `"null"`: API thực sự trả về giá trị Java `null` cho unnamed module; chỉ phép nối chuỗi trong `println` mới biến nó thành chữ `null` trong stdout.

## 7. Di chuyển ứng dụng legacy và xử lý lỗi deep reflection

### 7.1. `InaccessibleObjectException` khác `IllegalAccessException` như thế nào?

Khi công cụ Reflection gọi `field.setAccessible(true)` từ module A tới private field trong module B nhưng package đích không open cho A, việc **bật quyền truy cập** có thể ném `InaccessibleObjectException` (unchecked).

Nếu không bật được quyền và vẫn gọi `Field.get(...)` hoặc `Method.invoke(...)` trong tình huống không được phép, thao tác truy cập có thể gặp `IllegalAccessException` (checked) theo API tương ứng.

Hãy phân biệt **lỗi khi yêu cầu vượt kiểm tra** với **lỗi khi thực hiện truy cập**. Chỉ đổi sang `trySetAccessible()` không làm quyền module rộng hơn: API này giúp thăm dò bằng `boolean` thay vì luôn yêu cầu quyền bằng `setAccessible(true)`.

Ví dụ minh họa có chủ đích (không phải chương trình độc lập):

```java
Field field = targetClass.getDeclaredField("value");
if (field.trySetAccessible()) {
    Object value = field.get(targetObject);
    // xử lý value
} else {
    // yêu cầu mở package đúng module từ phía chủ sở hữu
}
```

Đừng mặc định `setAccessible(true)` sẽ vượt qua JPMS chỉ vì ứng dụng đang chạy với quyền hệ điều hành cao.

### 7.2. Cấu hình mở package mà không đổi source module

Khi module target thuộc thư viện hoặc JDK không thể sửa ngay, runtime có tùy chọn tạm thời:

```bash
java --add-opens com.example.target/com.example.target.internal=com.example.inspector --module-path mods -m com.example.inspector/com.example.inspector.Inspector
```

Cú pháp:

```text
--add-opens <module-nguồn>/<package>=<module-nhận>
```

Nếu ứng dụng gọi Reflection thuộc unnamed module trên classpath, đích thường là `ALL-UNNAMED`:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp classes UnnamedDemo
```

Lệnh này minh họa **cú pháp** mở package `java.lang` của `java.base` cho các unnamed modules; chương trình `UnnamedDemo` ở trên không thực sự cần quyền đó. Đừng thêm tùy chọn chỉ vì thấy ví dụ: phải xác định **đúng module/package đang chặn** và **module caller**.

Các tùy chọn khác có chức năng **khác nhau**:

- `--add-exports nguồn/package=đích` mở quyền truy cập tới public types/members ở runtime; **không phải** thay thế tổng quát cho deep reflection vào private members.
- `--add-reads nguồn=đích` bổ sung quan hệ readability, nhưng **không tự export/open** package.
- `--add-opens nguồn/package=đích` mở package để Reflection có thể yêu cầu quyền truy cập sâu từ module được chỉ định.

Từ Java 17, `--illegal-access` không còn là cơ chế giúp vượt qua strong encapsulation của JDK. Đối với ứng dụng hiện đại, nên ưu tiên API công khai hoặc `opens` có phạm vi vừa đủ thay vì dựa vào tùy chọn toàn cục hoặc truy cập nội bộ của JDK.

### 7.3. Mở package có thực sự an toàn và cần thiết?

Nhiều framework DI, serialization, ORM hay testing phải quan sát constructor, annotation, field hoặc setter qua Reflection. Tuy vậy, **không phải mọi framework đều cần private reflective access**. Có framework chỉ dùng public API, code generation hoặc module-specific integration.

Khi quyết định, nên hỏi:

1. Framework thật sự dùng package nào và chạy trong **named hay unnamed module**?
2. Nó chỉ cần public types/members hay cần đặt `accessible` cho private member?
3. Có thể dùng `opens p to framework.module` để giới hạn module nhận không?
4. Có cần `exports p` cho mã Java thường biên dịch tham chiếu trực tiếp vào API không?
5. Trong triển khai thực tế, tên module của framework có được xác nhận từ `Class.getModule().getName()` hay chưa?

Đây là lý do `open module` đôi khi thuận tiện cho ứng dụng được framework quản lý toàn phần, nhưng không phải lựa chọn mặc định cho một thư viện muốn giữ package nội bộ kín.

## 8. Chẩn đoán lỗi theo tình huống

| Triệu chứng | Khả năng liên quan đến module | Cách kiểm tra |
| --- | --- | --- |
| Không `import` được public class từ module khác | Thiếu `requires` hoặc package không `exports` đúng đối tượng | Xem hai `module-info.java` và module path lúc compile |
| `setAccessible(true)` ném `InaccessibleObjectException` | Package không `opens` cho module gọi | In `targetClass.getModule()` và `callerClass.getModule()`, kiểm tra `isOpen(package, callerModule)` |
| `Class.getModule().getName()` trả `null` | Class ở unnamed module | Kiểm tra `isNamed()`, classpath và class loader |
| Một JAR không có `module-info.java` nhưng `isNamed()==true` | JAR được dùng như automatic module trên module path | Kiểm tra `getDescriptor().isAutomatic()` |
| `open module` mà compile vẫn báo package không visible | Package không được `exports` để compile | Khai báo `exports` cho API cần công khai, tách với `opens` |
| Reflective access hoạt động ở classpath nhưng lỗi khi chuyển sang module path | Thay đổi ranh giới strong encapsulation | Xem package đích cần `opens`, đích của `opens ... to ...` và cấu hình launch |

Các tình huống không phải lỗi JPMS cũng có thể gây `ClassNotFoundException`, `NoClassDefFoundError` hay `IllegalAccessException`. Đừng sửa `module-info.java` một cách suy đoán: kiểm tra tên kiểu, class loader, module resolution, stack trace và đúng API phát sinh lỗi.

## 9. Lỗi thường gặp và cách tránh

**Lỗi 1: Cho rằng unnamed module và open module giống nhau.** Unnamed module không có tên, gắn với class loader/classpath. Open module là named module khai báo mở toàn bộ package. Cả hai có tính mở rộng cho Reflection ở runtime, nhưng cách cấu hình và quan hệ module khác nhau.

**Lỗi 2: Cho rằng named module chỉ có dạng có `module-info.java`.** Automatic module trên module path cũng là named module. Đọc `getDescriptor().isAutomatic()` trước khi kết luận nguồn gốc module.

**Lỗi 3: Cho rằng `exports` cho phép truy cập mọi private field.** `exports` dành cho API công khai và normal access; cần `opens` thích hợp nếu muốn `setAccessible(true)` với private members từ module ngoài.

**Lỗi 4: Cho rằng `opens` khiến `import` compile thành công.** `opens` không công bố package thành compile-time public API. Nếu module khác phải tham chiếu trực tiếp, cân nhắc `exports`.

**Lỗi 5: Đọc `isExported(pkg, other) == true` rồi suy ra module-info có `exports`.** Ở runtime, package được mở cho `other` có thể khiến kết quả đó cũng là `true`. Cần phân biệt **trạng thái runtime** với **chỉ thị trong `ModuleDescriptor`**.

**Lỗi 6: Dùng `isOpen(pkg, targetModule)` để kiểm tra quyền framework.** Một module có quyền truy cập package của chính nó; phải dùng module của **caller** ở phía framework thực tế.

**Lỗi 7: Tưởng rằng classpath code có thể deep reflect tùy ý vào JDK internals.** Java hiện đại đóng gói mạnh nhiều package của named modules; phía target phải open package cần thiết, hoặc launch option phù hợp.

**Lỗi 8: Dùng `open module` để giải quyết mọi lỗi import.** Đây là quyết định runtime Reflection, không thay thế chính sách export/readability cho biên dịch.

## 10. Bài tập và checklist tự kiểm tra

1. Chạy ví dụ `com.example.target`/`com.example.inspector`, đối chiếu cả sáu dòng stdout. Thử xóa `opens ... to ...`, quan sát vị trí lỗi `InaccessibleObjectException`.
2. Thử thêm lại `opens` ở dạng **unqualified** và quan sát sự khác nhau giữa `isOpen(package)` và `isOpen(package, inspector)`.
3. Thay `module com.example.target` bằng `open module com.example.target`, bỏ dòng `opens` riêng, biên dịch lại. Giải thích vì sao reflection vào `Secret` vẫn hoạt động.
4. Dùng `Module.getDescriptor()` kiểm tra `isOpen()` và danh sách `exports()` của module. Phân biệt metadata khai báo với `Module.isExported(pkg, module)` theo quyền runtime.
5. Chạy `UnnamedDemo` trên classpath. Sau đó đóng gói trong một named module, chạy bằng `-m` và so sánh `getName()`.
6. Với thư viện định đưa vào module path, kiểm tra các package thực sự cần public API và package thực sự cần deep reflection trước khi quyết định dùng `exports` hay `opens`.

**Ghi nhớ:** `Class.getModule()` cho biết kiểu thuộc module nào; `Module.isNamed()` phân biệt named/unnamed; `module-info.java` điều khiển **readability**, **exports** và **opens**; còn từ khóa `open module` mở mọi package của một **named module** cho deep reflection. Khi Reflection thất bại, cần tìm chính xác **module sở hữu package**, **module đang gọi**, và **loại quyền đang thiếu**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-006">Module.isExported, isOpen và kiểm tra ngữ cảnh truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# `Module.isExported`, `Module.isOpen` và kiểm tra ngữ cảnh truy cập

## 1. Vấn đề: `public` vẫn có thể chưa đủ

`Module.isExported` và `Module.isOpen` đọc **trạng thái quyền package tại runtime** từ góc nhìn một module cụ thể. Khi chẩn đoán Reflection, hãy xác định đúng module chứa package đích (`source`) và module **thực sự gọi** `setAccessible` (`client`): đó có thể là framework, không phải module ứng dụng khởi tạo yêu cầu.

Khi cần tham chiếu public API bằng mã Java thông thường, xét readability và `exports`; khi muốn bỏ qua Java access checks từ module khác, xét `opens`. Phần dưới tập trung vào cách diễn giải chính xác bốn overload và trường hợp `opens` cũng làm `isExported` trả `true` ở runtime.

Bốn API của `java.lang.Module` là trọng tâm bài:

```java
boolean x1 = source.isExported("example.api");
boolean x2 = source.isExported("example.api", client);
boolean x3 = source.isOpen("example.internal");
boolean x4 = source.isOpen("example.internal", client);
```

Đoạn trên là minh họa với hai biến kiểu `Module` đã được xác định. Mỗi API hỏi về quyền của **một package trong module nguồn**, không trực tiếp hỏi một field/method có thể đọc hoặc gọi được hay không.

**Điểm mấu chốt:** `opens` khác `exports` về mục đích, nhưng **một package mở cho module đích được xem như export cho module đó tại runtime**. Vì thế, `isExported(pn, client)` có thể trả `true` dù `module-info.java` chỉ khai báo `opens pn to client;`. Đừng nhầm kết quả runtime với quyền import package đó tại compile time.

## 2. Xác định đúng source, client và package

Gọi:

- **`source`**: module chứa package/lớp đang được truy cập.
- **`client`** (hay `callerModule`): module chứa mã thực sự thực hiện Reflection.
- **`pn`**: tên package đầy đủ như `"example.internal"`; không phải tên lớp `example.internal.Secret` hay đường dẫn `example/internal`.

Lấy thông tin từ class:

```java
Module source = TargetType.class.getModule();
Module client = MyInspector.class.getModule();
String pn = TargetType.class.getPackageName();
```

Đây là đoạn minh họa, đòi hỏi các class được khai báo ở chương trình phù hợp. Nếu package không export nên mã client không thể import `TargetType` trực tiếp, bạn có thể nhận `source` từ một lớp API public đã export cùng module, từ `Class.forName(...)` khi thích hợp, hoặc tra module đã resolve bằng `ModuleLayer.boot().findModule("ten.module")` nếu nó thuộc boot layer.

| Cách kiểm tra | Ý nghĩa |
| --- | --- |
| `source.isNamed()` | Phân biệt named module và unnamed module |
| `source.getName()` | Tên module; `null` với unnamed module |
| `source.getPackages().contains(pn)` | Package hiện được module báo là của nó? |
| `client.canRead(source)` | Client có thể đọc source? |
| `source.isExported(pn)` | Export không điều kiện tại runtime? |
| `source.isExported(pn, client)` | Export ít nhất đến client tại runtime? |
| `source.isOpen(pn)` | Open không điều kiện tại runtime? |
| `source.isOpen(pn, client)` | Open ít nhất đến client tại runtime? |

**Không đảo hướng:** Khi hỏi quyền truy cập vào package của `source`, gọi `source.isOpen(pn, client)`, không phải `client.isOpen(pn, source)`.

Trong ứng dụng thực tế, `client` có thể là module của framework đang chạy đoạn Reflection, **không nhất thiết là module ứng dụng đã gọi vào framework**. Đây là một nguồn gây lỗi cấu hình `opens ... to ...` rất phổ biến.

## 3. `isExported`: package được nhìn thấy cho mục đích nào?

### 3.1. `isExported(String pn)`

Trả `true` nếu package được export **không điều kiện** tại runtime:

```java
module m.library {
    exports demo.api;
}
```

`source.isExported("demo.api")` sẽ là `true` với module này nếu `demo.api` là package hiện có. Mã trong module khác muốn tham chiếu trực tiếp kiểu `public` từ `demo.api` còn cần các điều kiện Java/module access khác, đặc biệt readability.

Không dùng `isExported(pn)` thay cho kiểm tra `client.canRead(source)`: tài liệu Java quy định các phương thức `isExported` **không kiểm tra** module đang xét có được module client đọc hay không.

Lưu ý quan trọng: package được **open không điều kiện** cũng được xem như export không điều kiện ở runtime, nên `source.isExported(pn)` có thể là `true` khi descriptor có `opens pn;` mà không có `exports pn;`.

### 3.2. `isExported(String pn, Module other)`

Kiểm tra package đã export ít nhất cho module `other` hay chưa. Ví dụ:

```java
module m.library {
    exports demo.spi to m.client;
}
```

Với đúng module `m.client`:

- `source.isExported("demo.spi")` là `false` vì export **có điều kiện**.
- `source.isExported("demo.spi", client)` là `true` vì client có trong danh sách đích.

Trường hợp **qualified opens** còn tinh tế hơn:

```java
module m.library {
    opens demo.internal to m.client;
}
```

Ở **runtime**, `source.isExported("demo.internal", client)` là **`true`** vì package mở cho client được coi là export cho client. Nhưng `source.isExported("demo.internal")` là `false` khi không có export/open không điều kiện.

Đừng suy luận rằng mã nguồn `m.client` có thể viết `import demo.internal.Secret;` chỉ vì overload hai tham số trả `true`. `opens` phục vụ Reflection và quyền runtime; `exports` mới là tuyên bố public API thông thường khi trình biên dịch kiểm tra tính truy cập giữa named modules.

### 3.3. Trường hợp module tự truy cập chính mình

Nếu package thuộc `source`, `source.isExported(pn, source)` trả `true`: Java xem module có quyền tới package của chính nó. Đây **không** phải bằng chứng package được export ra ngoài. Muốn biết quyền của framework khác, truyền đúng `Module` của framework đó.

## 4. `isOpen`: cho phép truy cập Reflection sâu

### 4.1. `isOpen(String pn)`

Kiểm tra package có được open **không điều kiện** hay không:

```java
module m.library {
    opens demo.model;
}
```

Trong cấu hình đó, `source.isOpen("demo.model")` là `true`. Package open cho phép các API Reflection phù hợp có thể yêu cầu bỏ qua Java language access checks đối với member ở package đó, ví dụ `private` constructor hoặc field.

Ngược lại, nếu module chỉ có:

```java
module m.library {
    exports demo.api;
}
```

thì `source.isExported("demo.api")` là `true` nhưng `source.isOpen("demo.api")` vẫn là `false`. Export không tự động cấp quyền deep reflection.

### 4.2. `isOpen(String pn, Module other)`

Kiểm tra package đã open cho **một module cụ thể** hay chưa:

```java
module m.library {
    opens demo.model to m.client;
}
```

Nếu không có thay đổi quyền bổ sung ở runtime:

| Kiểm tra | Kết quả |
| --- | --- |
| `source.isOpen("demo.model")` | `false` |
| `source.isOpen("demo.model", client)` | `true` |
| `source.isExported("demo.model")` | `false` |
| `source.isExported("demo.model", client)` | **`true`** vì open cũng được coi là export tại runtime |
| `client.canRead(source)` | Phải kiểm tra **riêng** |

Tài liệu `Module` cũng quy định `source.isOpen(pn, source)` trả `true` nếu package thuộc chính source. Vì vậy, khi kiểm tra một framework bên ngoài, truyền `source` làm `other` sẽ cho kết quả dễ gây hiểu lầm.

### 4.3. So sánh các chỉ thị

Bảng giả định **explicit named module** và package thật sự thuộc nó, không có thêm `--add-exports`/`--add-opens`:

| `module-info.java` | `isExported(pn)` | `isOpen(pn)` | `isExported(pn, client)` | `isOpen(pn, client)` |
| --- | --- | --- | --- | --- |
| Không có `exports`/`opens` | false | false | false | false |
| `exports p;` | true | false | true | false |
| `exports p to m.client;` | false | false | true | false |
| `opens p;` | **true** | true | **true** | true |
| `opens p to m.client;` | false | false | **true** | true |

Ở hai dòng qualified, `client` là đúng module `m.client`. Nếu truyền một module khác không có trong danh sách, các kết quả qualified tương ứng sẽ là `false`. Các phương thức trả trạng thái access **hiện hành**, không chỉ đọc các dòng khai báo trong nguồn.

## 5. Kiểm tra đủ ngữ cảnh: đọc module, package và member

Để mã Java ở module client có thể **tham chiếu trực tiếp** public API trong source, phải xét đồng thời:

1. **Readability:** `client.canRead(source)`. Lệnh `requires m.library;` trong `module-info.java` tạo quan hệ đọc khi module graph được resolve theo các quy tắc JPMS.
2. **Export:** package chứa public API được export cho client; cần phân biệt export công khai và qualified.
3. **Java access control:** kiểu và member thực sự có quyền phù hợp, ví dụ class `public`, method `public`.

Để **deep reflection** từ một framework, câu hỏi bổ sung là package có **open tới module chứa framework** hay không. Khi thao tác trên `Field`, `Method`, `Constructor`, phương thức ở `java.lang.reflect.AccessibleObject` còn phản ánh khả năng access cụ thể:

| API | Tác dụng |
| --- | --- |
| `getDeclaredField("value")` | Tìm metadata về field **khai báo trực tiếp**, không tự cấp quyền đọc giá trị |
| `canAccess(instance)` | Kiểm tra quyền truy cập hiện tại của **caller** đối với reflected object |
| `trySetAccessible()` | Thử bật chế độ bỏ qua access checks; trả `true` nếu bật được, `false` khi không thể |
| `setAccessible(true)` | Yêu cầu bật chế độ đó; có thể ném `InaccessibleObjectException` khi JPMS cấm |
| `Field.get(instance)` / `Method.invoke(...)` | Thực hiện truy cập/gọi và áp dụng quy tắc phù hợp |

**Ngữ cảnh caller rất quan trọng:** Mã ở module `m.framework` gọi `trySetAccessible()` thì source phải mở package cho `m.framework`. Mở package cho `m.app` là chưa chắc giúp ích, ngay cả khi `m.app` là nơi khởi động framework.

**Một khác biệt kỹ thuật:** Theo tài liệu `AccessibleObject`, việc sử dụng reflected objects để truy cập member có biến thể trong kiểm tra quyền: Reflection **giả định readability** khi kiểm tra access. Vì vậy không được áp dụng máy móc tất cả quy tắc compile-time reference cho thao tác Reflection runtime. Dù vậy, `canRead` vẫn hữu ích khi phân tích module graph và quyền truy cập Java thông thường; bản thân nó không hề đủ để vượt qua package encapsulation.

Không đồng nhất ba việc **tìm thấy `Class`**, **lấy được `Field`/`Method` metadata** và **được phép đọc/gọi member đó**. Ví dụ `Class.forName` có thể tìm một class nhưng `trySetAccessible` vẫn thất bại nếu package không mở cho caller.

## 6. Ví dụ thực hành Java 17+: hai named module

Ví dụ dưới đây cho thấy **qualified opens**: một package nội bộ không export công khai nhưng vẫn mở cho Reflection của client cụ thể. Tạo các tệp:

```text
src/
├── m.library/
│   ├── module-info.java
│   └── demo/
│       ├── api/Api.java
│       └── model/Secret.java
└── m.client/
    ├── module-info.java
    └── demo/client/Main.java
```

**`src/m.library/module-info.java`**

```java
module m.library {
    exports demo.api;
    opens demo.model to m.client;
}
```

**`src/m.library/demo/api/Api.java`**

```java
package demo.api;

public final class Api {
    private Api() {
    }
}
```

**`src/m.library/demo/model/Secret.java`**

```java
package demo.model;

public final class Secret {
    private final String message = "hello";

    private Secret() {
    }
}
```

**`src/m.client/module-info.java`**

```java
module m.client {
    requires m.library;
}
```

**`src/m.client/demo/client/Main.java`**

```java
package demo.client;

import demo.api.Api;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class Main {
    public static void main(String[] args) throws Exception {
        Module source = Api.class.getModule();
        Module client = Main.class.getModule();

        System.out.println("client reads library: "
                + client.canRead(source));
        System.out.println("api exported to all: "
                + source.isExported("demo.api"));
        System.out.println("api open to all: "
                + source.isOpen("demo.api"));
        System.out.println("model exported to all: "
                + source.isExported("demo.model"));
        System.out.println("model exported to client at runtime: "
                + source.isExported("demo.model", client));
        System.out.println("model open to all: "
                + source.isOpen("demo.model"));
        System.out.println("model open to client: "
                + source.isOpen("demo.model", client));

        // Không import trực tiếp package demo.model.
        Class<?> type = Class.forName("demo.model.Secret");
        Constructor<?> constructor = type.getDeclaredConstructor();
        System.out.println("constructor trySetAccessible: "
                + constructor.trySetAccessible());
        Object instance = constructor.newInstance();

        Field field = type.getDeclaredField("message");
        System.out.println("field trySetAccessible: "
                + field.trySetAccessible());
        System.out.println("secret: " + field.get(instance));
    }
}
```

Tại thư mục chứa `src`, biên dịch rồi chạy:

```bash
javac -d out --module-source-path src src/m.library/module-info.java src/m.library/demo/api/Api.java src/m.library/demo/model/Secret.java src/m.client/module-info.java src/m.client/demo/client/Main.java
java --module-path out -m m.client/demo.client.Main
```

**Stdout mong đợi:**

```text
client reads library: true
api exported to all: true
api open to all: false
model exported to all: false
model exported to client at runtime: true
model open to all: false
model open to client: true
constructor trySetAccessible: true
field trySetAccessible: true
secret: hello
```

Phân tích:

- `requires m.library;` giúp client đọc module thư viện; `canRead` là `true`.
- `demo.api` chỉ được `exports` công khai: `isExported` là `true`, `isOpen` là `false`.
- `demo.model` được `opens ... to m.client`: không export/open không điều kiện. Nhưng **`isExported("demo.model", client) == true` tại runtime**, vì qualified open cho client cũng tính là export.
- Client không viết `import demo.model.Secret;`. Thay vào đó, `Class.forName` lấy class theo tên. Đây là đường tra cứu metadata, không cấp sẵn quyền truy cập constructor/field.
- Hai lần `trySetAccessible()` thành công vì package thực sự được mở cho module `m.client` chứa mã gọi, nên code có thể khởi tạo object bằng private constructor và đọc private field.

**Bài thử lỗi có kiểm soát:** Bỏ dòng `opens demo.model to m.client;` trong module nguồn, biên dịch lại. Khi đó `isOpen("demo.model", client)` và `isExported("demo.model", client)` trở thành `false`. `constructor.trySetAccessible()` cũng trả `false`; nếu mã vẫn gọi `constructor.newInstance()` như thể đã được cấp quyền, có thể gặp `IllegalAccessException`. Hãy kiểm tra giá trị trả về và ngừng nhánh truy cập sâu khi thất bại.

## 7. Kiểm tra nhanh từ classpath và `java.base`

Đây là ví dụ riêng, không cần tổ chức nhiều module. Lưu `ModuleProbe.java`:

```java
public class ModuleProbe {
    public static void main(String[] args) {
        Module mine = ModuleProbe.class.getModule();
        Module base = String.class.getModule();

        System.out.println("mine named: " + mine.isNamed());
        System.out.println("mine reads java.base: " + mine.canRead(base));
        System.out.println("java.lang exported: "
                + base.isExported("java.lang"));
        System.out.println("java.lang open: "
                + base.isOpen("java.lang"));
        System.out.println("java.lang exported to mine: "
                + base.isExported("java.lang", mine));
        System.out.println("java.lang open to mine: "
                + base.isOpen("java.lang", mine));
    }
}
```

```bash
javac ModuleProbe.java
java -cp . ModuleProbe
```

**Stdout trên cấu hình JDK 17+ tiêu chuẩn, không thêm `--add-opens java.base/java.lang=ALL-UNNAMED`:**

```text
mine named: false
mine reads java.base: true
java.lang exported: true
java.lang open: false
java.lang exported to mine: true
java.lang open to mine: false
```

`ModuleProbe` chạy trong **unnamed module** của class loader; `String` thuộc named module `java.base`. Package `java.lang` được export để mã dùng public API như `String.length()`, nhưng điều đó không cho phép Reflection từ classpath tùy ý đọc các field private của `String`.

## 8. Các loại module đặc biệt và hệ quả

### Explicit named module

Module có khai báo tường minh `module-info.java` cho phép kiểm soát từng package bằng `exports` và `opens` (kể cả qualified dạng `to m.client`). Đây là trường hợp bảng so sánh ở mục 4 áp dụng trực tiếp.

### `open module`

Khai báo `open module m.library { ... }` mở **mọi package thuộc module** cho Reflection. Đây là đặc tính cấp module, khác với `opens p;` chỉ mở một package. Với package thuộc một open module, `isOpen(pn)` trả `true`; package open không điều kiện cũng được xem như export không điều kiện ở runtime. Trong phần thân `open module` không khai báo thêm các chỉ thị `opens` riêng.

### Unnamed module

Mã nạp từ classpath thường thuộc unnamed module. Theo hợp đồng Java `Module`, các phương thức `isExported` và `isOpen` **khi gọi trên unnamed module** luôn trả `true`; unnamed module cũng đọc mọi module. Nhưng unnamed module không khiến named module khác tự động mở các package của chúng cho mã classpath.

### Automatic module

Một JAR không có explicit module descriptor có thể trở thành **automatic module** nếu được đưa lên module path. Automatic module có tên (`isNamed() == true`), export và open các package theo cơ chế tương thích. Phân biệt nó với unnamed module. Có thể xem `module.getDescriptor().isAutomatic()` khi descriptor tồn tại.

### Module tự truy cập package

`source.isOpen(pn, source)` hoặc `source.isExported(pn, source)` trả `true` với package thuộc source. Nếu vô tình truyền `source` thay cho module framework đang truy cập, kết quả dễ bị diễn giải sai thành quyền mở ra ngoài.

## 9. Quyền runtime có thể khác khai báo nguồn

`Module.isExported` và `Module.isOpen` phản ánh **runtime module state**. Khi chạy Java, có thể bổ sung các quyền:

| Cơ chế | Tác dụng |
| --- | --- |
| `requires m.library;` | Thiết lập module readability theo module graph |
| `exports p;` hoặc `exports p to m.client;` | Public API unqualified / qualified |
| `opens p;` hoặc `opens p to m.client;` | Reflection access unqualified / qualified |
| `--add-reads m.client=m.library` | Thêm read edge lúc chạy |
| `--add-exports m.library/demo.internal=m.client` | Thêm export cho client lúc chạy |
| `--add-opens m.library/demo.model=m.client` | Thêm open cho client lúc chạy |

Giả sử bỏ `opens demo.model to m.client;` trong thư viện, sau khi biên dịch lại có thể bật quyền open ngay khi chạy (nếu bạn kiểm soát launch command):

```bash
java --module-path out --add-opens m.library/demo.model=m.client -m m.client/demo.client.Main
```

`--add-opens` là lựa chọn cho nhu cầu deep reflection. `--add-exports` có ý nghĩa khác, hỗ trợ public access tại runtime; đừng tự động thêm cả hai khi chưa xác định yêu cầu.

Các phương thức `Module.addReads`, `Module.addExports` và `Module.addOpens` cũng tồn tại nhưng **được bảo vệ bởi điều kiện về caller và trạng thái module**. Một framework ở module bất kỳ không thể giả định nó được tự do mở tất cả package của module khác.

Vì vậy, `source.getDescriptor().exports()` hoặc `opens()` biểu diễn mô tả của module và có thể **khác trạng thái runtime** do các lệnh cấu hình hoặc cập nhật. Khi phân tích một lỗi, hãy nhìn đồng thời descriptor, `Module` object thực tế, tham số khởi chạy và module layer liên quan.

## 10. Quy trình debug `InaccessibleObjectException`

Một quy trình ngắn nhưng đầy đủ:

1. **Lớp đích:** xác định `targetClass.getName()` và `targetClass.getPackageName()`.
2. **Module nguồn:** `targetClass.getModule()`, cùng tên và trạng thái `isNamed()`.
3. **Caller thật:** tìm class đang chạy `setAccessible`/`trySetAccessible`; lấy `Accessor.class.getModule()`. Nếu đó là framework, module framework mới là `other`.
4. **Truy vấn quyền package:** so sánh `source.isOpen(pn)` với `source.isOpen(pn, callerModule)`; tương tự cho `isExported`.
5. **Readability:** hỏi `callerModule.canRead(source)` riêng, nhất là khi có lỗi compile/module resolution hay Java access bình thường.
6. **Kiểm tra member cụ thể:** thử `canAccess`/`trySetAccessible` hoặc xem exception thật, lưu ý constructor, instance/static field và method yêu cầu tham số `obj` phù hợp.
7. **Kiểm tra runtime:** `module-info.java`, `--module-path` so với `-cp`, launch flags, agent và các API cập nhật quyền.

Khi exception ghi module `m.library` không `opens demo.model` cho module `m.framework`, hãy kiểm tra quyền open **tới `m.framework`**. Thêm `opens demo.model to m.app;` chỉ vì ứng dụng chính thuộc `m.app` có thể không xử lý đúng caller.

### Những suy luận cần tránh

- `isOpen(pn) == false` **không** có nghĩa `isOpen(pn, client)` luôn false (có qualified opens).
- `isExported(pn, client) == true` **không** có nghĩa package chắc chắn có dòng `exports`; kết quả có thể đến từ `opens` ở runtime.
- `isOpen(pn, client) == true` **không** bảo đảm mọi hành động Reflection thành công vô điều kiện.
- `canRead(source) == true` **không** cho phép đọc field private nếu thiếu các điều kiện Reflection khác.
- `getDeclaredField` tìm thấy field **không** đồng nghĩa caller được phép `Field.get`.
- `isOpen(pn, source) == true` với một package thuộc source **không** chứng minh package đó đã mở cho framework bên ngoài.

## 11. Bài tự kiểm tra

Cho module nguồn:

```java
module m.provider {
    exports demo.api;
    opens demo.internal to m.consumer;
}
```

Giả sử `demo.api` và `demo.internal` là hai package có thật, module `m.consumer` đã resolve và đọc module nguồn, không có runtime override. Dự đoán:

```java
provider.isExported("demo.api");                   // A
provider.isOpen("demo.api");                       // B
provider.isExported("demo.internal");              // C
provider.isOpen("demo.internal");                  // D
provider.isExported("demo.internal", consumer);    // E
provider.isOpen("demo.internal", consumer);        // F
provider.isOpen("demo.internal", provider);        // G
```

**Đáp án:** A = `true`; B = `false`; C = `false`; D = `false`; E = **`true`** (qualified opens cũng có hiệu lực export tại runtime); F = `true`; G = `true` (module được truy cập package của chính mình).

Đây là các mảnh mã để suy luận, không phải tệp Java độc lập có thể biên dịch. Muốn chạy, sử dụng chương trình đầy đủ ở mục 6.

## 12. Ghi nhớ và tài liệu chuẩn

**Đặt câu hỏi từ góc nhìn source và caller:** `source.isExported(package, callerModule)` và `source.isOpen(package, callerModule)`. Overload một tham số hỏi quyền không điều kiện; overload hai tham số hỏi quyền ít nhất cho một module cụ thể. Package mở cho module đích **được xem là exported cho module đó tại runtime**, nhưng `opens` vẫn có mục đích riêng là deep reflection; `exports` dùng cho public API thông thường. Quyền đọc module, quyền package và quyền của member cần đánh giá riêng.

Tài liệu đối chiếu:

- [Java SE 21 — `Module`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html)
- [Java SE 21 — `AccessibleObject`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html)
- [Java Language Specification `7.7 — Module Declarations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-7.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-007">InaccessibleObjectException và nguyên nhân không thể mở truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# `InaccessibleObjectException` và nguyên nhân không thể mở truy cập trong Java Reflection

Một đoạn reflection có thể tìm được `Field`, `Method` hoặc `Constructor` nhưng thất bại ngay tại `setAccessible(true)`:

```text
java.lang.reflect.InaccessibleObjectException:
  Unable to make ... accessible:
  module java.base does not "opens java.lang" to unnamed module ...
```

Đây thường là dấu hiệu của **strong encapsulation (đóng gói mạnh) giữa các module**, nhất là khi thư viện cố phản chiếu sâu vào thành viên `private` của JDK hay của một *named module* khác. Cần phân biệt ba chuyện: **tìm thấy khai báo**, **được phép bỏ qua kiểm tra truy cập của ngôn ngữ Java**, và **thực sự đọc/ghi/gọi thành viên**. Tìm thấy một field bằng `getDeclaredField()` chưa cấp quyền đọc field đó.

Bài này trình bày ngữ nghĩa của `InaccessibleObjectException` trong Java 17+, cách `exports` và `opens` của Java Platform Module System (JPMS) tác động đến reflection, quy trình chẩn đoán, cách sửa đúng từ `module-info.java` và cách dùng `--add-opens` có giới hạn.

## 1. Khi nào xuất hiện `InaccessibleObjectException`?

`java.lang.reflect.InaccessibleObjectException` là **unchecked exception** (kế thừa `RuntimeException`). Nó biểu thị rằng chương trình **không thể yêu cầu bỏ qua Java language access checks** cho một đối tượng reflection, thường là với `AccessibleObject.setAccessible(true)`. Các đối tượng có liên quan gồm:

| Đối tượng | API thường kích hoạt lỗi | Định nghĩa điều muốn làm |
| --- | --- | --- |
| `Field` | `field.setAccessible(true)` | Xin cho phép đọc/ghi field dù kiểm tra truy cập thông thường sẽ chặn |
| `Method` | `method.setAccessible(true)` | Xin cho phép gọi method vượt qua access checks thông thường |
| `Constructor<?>` | `constructor.setAccessible(true)` | Xin cho phép gọi constructor vượt qua access checks thông thường |
| Mảng `AccessibleObject[]` | `AccessibleObject.setAccessible(array, true)` | Xin suppress access checks cho từng phần tử |

Đặc biệt, *exception này không có nghĩa field hoặc method không tồn tại*. Ví dụ `String.class.getDeclaredField("value")` có thể tìm được field `private` nội bộ, nhưng `setAccessible(true)` không nhất thiết thành công.

### Ba API, ba câu hỏi khác nhau

- `getDeclaredField("x")`, `getDeclaredMethod(...)`: **có khai báo như vậy không?** Thường trả về metadata mà chưa cho phép thao tác member.
- `canAccess(receiver)`: **với caller và receiver hiện tại, thao tác reflective có vượt được access check không?** Có thể trả `true` nếu thành viên vốn accessible hoặc việc suppress check đã được cho phép.
- `trySetAccessible()`: **thử yêu cầu suppress Java language access checks**; trả `true` khi thành công, `false` khi không thể mở truy cập theo các quy tắc cho phép của API (một lỗi khác như `SecurityException` vẫn có thể xảy ra trong môi trường áp dụng chính sách bảo mật).
- `setAccessible(true)`: yêu cầu tương tự `trySetAccessible()` nhưng có thể **ném `InaccessibleObjectException`** khi không thể bật quyền truy cập.

Cờ accessibility trên đối tượng `AccessibleObject` là cơ chế reflection; nó **không thay đổi** modifier gốc của class, field hay method. `private` vẫn là `private`. `setAccessible(false)` chỉ bật lại việc kiểm tra truy cập thông thường đối với đối tượng reflection đó.

## 2. Nguyên nhân nền tảng: thiếu quyền mở package cho đúng caller

JPMS (Java 9+) bổ sung ranh giới giữa các named module trên quy tắc `public`/`private` của ngôn ngữ. `requires` tạo quan hệ đọc module, `exports` cung cấp quyền sử dụng public API, còn `opens` cho phép yêu cầu bỏ qua Java access checks khi Reflection cần truy cập sâu. **`InaccessibleObjectException` phát sinh ở bước xin mở quyền**, không phải vì một field đã biến mất.

| Chính sách của package trong named module | Public API từ module khác | `setAccessible(true)` vào private member từ module khác |
| --- | --- | --- |
| Không `exports` hoặc `opens` | Không được phép theo đường Java thông thường | Bị chặn khi không có quyền hợp lệ khác |
| Chỉ `exports p` (có thể `to m`) | Có, với caller được phép và visibility hợp lệ | `exports` không đủ |
| Chỉ `opens p` (có thể `to m`) | Không tự cấp quyền `import` khi biên dịch | Có thể thành công với module caller được mở |
| Cả `exports` và `opens` | Có quyền API public và khả năng deep reflection theo phạm vi đã cấp | Có thể thành công |

Nếu sở hữu module `com.example.domain`, có thể `exports com.example.domain.api;` cho API chính thức và `opens com.example.domain.model to com.example.serializer;` cho serializer phải đọc private members. Nếu chọn `open module`, mọi package sẽ mở cho Reflection; trong body của `open module` không thêm `opens` từng package.

### Vì sao lỗi xuất hiện nhiều từ Java 17?

Các phiên bản JDK trước đây có những cơ chế tương thích để nhiều thư viện tiếp tục phản chiếu vào nội bộ JDK. Từ **Java 17 (JEP 403)**, đóng gói mạnh các internal JDK APIs là hành vi mặc định và tùy chọn legacy `--illegal-access` không còn bật lại cơ chế mở rộng truy cập cũ. Những thư viện vẫn gọi `setAccessible(true)` vào field/method thuộc package JDK chưa mở có thể gặp `InaccessibleObjectException`.

Ví dụ `java.lang` thuộc module `java.base`. Module này có public APIs thông dụng, nhưng việc nhìn thấy lớp `java.lang.String` **không** trao quyền đọc field `private` của `String`. Ngay cả khi sử dụng `String` bình thường, deep reflection vào cấu trúc nội bộ của nó có thể bị chặn.

## 3. Ví dụ Java 17+ có thể chạy và tái hiện lỗi

Lưu đoạn sau thành `DeepReflectionDemo.java`. Ví dụ cố tình thử truy cập field nội bộ của `String` để quan sát khác biệt giữa việc **tìm được khai báo** và việc **được phép mở truy cập**. Chương trình chỉ thử metadata/accessibility, không đọc hay sửa giá trị field `String`.

```java
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;

public class DeepReflectionDemo {
    public static void main(String[] args) throws Exception {
        Class<?> owner = String.class;
        Field field = owner.getDeclaredField("value");
        String receiver = "hello";

        System.out.println("module: " + owner.getModule().getName());
        System.out.println("field private: "
                + Modifier.isPrivate(field.getModifiers()));
        System.out.println("canAccess before: "
                + field.canAccess(receiver));

        boolean opened = field.trySetAccessible();
        System.out.println("trySetAccessible: " + opened);

        try {
            field.setAccessible(true);
            System.out.println("setAccessible: OK");
        } catch (InaccessibleObjectException ex) {
            System.out.println("setAccessible: InaccessibleObjectException");
        }

        System.out.println("canAccess after: "
                + field.canAccess(receiver));
    }
}
```

Biên dịch/chạy thông thường:

```bash
javac DeepReflectionDemo.java
java DeepReflectionDemo
```

Khi chưa có quyền mở package `java.lang` cho code ở *unnamed module* (classpath), kết quả điển hình trên Java 17+ là:

```text
module: java.base
field private: true
canAccess before: false
trySetAccessible: false
setAccessible: InaccessibleObjectException
canAccess after: false
```

- `getDeclaredField("value")` thành công vì API cho phép xem thông tin khai báo.
- `trySetAccessible()` trả `false`: không có quyền suppress access checks tới private field thuộc package JDK đó.
- `setAccessible(true)` yêu cầu cùng quyền nhưng ném exception. Đây là điểm xảy ra lỗi được mô phỏng trong bài.

Chạy lại cùng class **với đúng một package được mở**:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED DeepReflectionDemo
```

Kết quả dự kiến:

```text
module: java.base
field private: true
canAccess before: false
trySetAccessible: true
setAccessible: OK
canAccess after: true
```

Tùy chọn này mở `java.lang` của module `java.base` tới tất cả *unnamed modules*, gồm mã chạy trên classpath của ví dụ. Điều đó cho phép suppress checks tại runtime; **nó không biến field thành public** và không thay đổi `getModifiers()`. Tránh phụ thuộc trực tiếp vào `String.value` trong mã ứng dụng thật: cấu trúc nội bộ JDK không phải public contract ổn định.

## 4. Tách bốn nguyên nhân thường bị nhầm

### 4.1. Package thuộc module khác chưa `opens`

Triệu chứng: tên module và package nguồn xuất hiện trong thông báo, chẳng hạn:

```text
module com.example.domain does not "opens com.example.domain.model"
to module com.example.serializer
```

Đây là lỗi **mở package**. Sửa tại module sở hữu dữ liệu nếu có thể, ví dụ `opens com.example.domain.model to com.example.serializer;`. Đừng sửa ở module gọi bằng cách viết `opens` cho package không thuộc quyền sở hữu của nó.

### 4.2. Package `exports` nhưng không `opens`

Một method `public` của một class `public` có thể gọi theo cách bình thường từ module có quyền đọc/export; đồng thời field `private` của chính class đó vẫn không được truy cập sâu. Các framework JSON, ORM, dependency injection cần khởi tạo/điền field bằng reflection có thể gặp trường hợp này.

Sửa theo nhu cầu thực tế: API công khai → `exports`; truy cập private runtime bởi framework → `opens` thích hợp. Có thể dùng cả hai nếu hai nhu cầu cùng tồn tại.

### 4.3. Code không có quyền truy cập Java thông thường

Field `private` hoặc method package-private từ package/caller khác có thể gây `IllegalAccessException` khi **đọc/ghi/gọi mà không suppress access checks**. Trường hợp này không mặc định đồng nghĩa với `InaccessibleObjectException`.

Ví dụ `field.get(receiver)` có thể ném `IllegalAccessException` nếu checks đang bật. Nhưng `field.setAccessible(true)` mới là yêu cầu suppress; nếu module chặn yêu cầu đó thì `InaccessibleObjectException` xuất hiện.

### 4.4. Chạy bằng classpath, module path hoặc môi trường khác nhau

Trong unnamed module (mã classpath), một module có tên thường thấy caller đích là `ALL-UNNAMED` trong cấu hình `--add-opens`. Với named module, đích phải là **đúng tên module**, ví dụ `com.example.serializer`. Vì vậy một lệnh chạy được trong IDE nhưng không chạy trong production có thể do flags JVM, module path, module descriptors hoặc cách đóng gói khác nhau.

Nhớ rằng cấu hình build hoặc tham số của `javac` không nhất thiết cũng là tham số của JVM đang chạy. Quyền deep reflection cần được cấp trong **process runtime** thật sự có lỗi.

## 5. Chọn cách sửa: `module-info.java` hay runtime flag?

Khi bạn **sở hữu module đích**, cách rõ ràng nhất là công khai hợp đồng phản chiếu cần thiết trong `module-info.java`. Ví dụ module domain:

```java
module com.example.domain {
    exports com.example.domain.api;
    opens com.example.domain.model to com.example.mapper;
}
```

Giải thích: `com.example.mapper` là **tên module**, không phải tên package hoặc tên class. Nếu dự án sử dụng một framework được đóng gói thành named module, cần mở cho **module name mà framework thực sự sử dụng**. Đừng suy ra tên này từ artifact Maven một cách mù quáng: automatic module name và module name khai báo có thể khác artifact file name.

Khi bạn **không sở hữu module đích**, có thể dùng tùy chọn runtime hẹp nhất cho một vấn đề tương thích cụ thể:

```bash
java --add-opens com.example.domain/com.example.domain.model=com.example.mapper \
     --module-path mods \
     --module com.example.app/com.example.app.Main
```

Dấu `\` ở cuối dòng trong ví dụ là cách nối dòng cho shell kiểu POSIX; trên Windows PowerShell, hãy viết lệnh thành một dòng hoặc dùng cú pháp nối dòng của PowerShell. Tên module/package ở đây là **ví dụ cấu trúc**, chỉ dùng sau khi thay bằng tên có thật trong ứng dụng.

Đối với ứng dụng trên classpath:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp app.jar com.example.Main
```

Cú pháp của tùy chọn:

```text
--add-opens SOURCE_MODULE/PACKAGE=TARGET_MODULE
```

Khi nhiều target, có thể khai báo phù hợp theo tài liệu JVM/version; thường chỉ cần module của framework/caller thật sự sử dụng, hoặc `ALL-UNNAMED` nếu thư viện nằm trên classpath. **Chỉ mở package thực sự cần**, vì mỗi `--add-opens` làm rộng phạm vi mã có thể sử dụng reflection để vượt qua các kiểm tra truy cập trong package đó.

### `--add-opens` khác `--add-exports` ra sao?

| Tùy chọn | Mục đích chính | Có tự cho phép suppress private access không? |
| --- | --- | --- |
| `--add-exports source/pkg=target` | Bổ sung quyền truy cập tới public types của package | Không |
| `--add-opens source/pkg=target` | Mở package cho reflection tại runtime | Có thể, theo quy tắc `AccessibleObject` |

**Không thay `--add-opens` bằng `--add-exports`** khi vấn đề là `setAccessible(true)` trên field `private`. Ngược lại, khi chương trình cần *compile/import* public class từ module không export, có thể phải giải quyết exports; `--add-opens` không làm cho mọi import compile-time hợp lệ.

Tốt hơn nữa, nếu có public API chính thức, hãy sử dụng public API hoặc phiên bản thư viện đã được cập nhật để tránh truy cập nội bộ. Flag tương thích nên được xem là giải pháp có kiểm soát, nhất là khi thư viện chạm nhiều gói JDK nội bộ.

## 6. Quy trình đọc stack trace để tìm đúng package và module

Một stack trace điển hình thường có thông điệp chứa:

```text
Unable to make <member> accessible:
module <source-module> does not "opens <source-package>"
to <caller-module>
```

Hãy trích đúng bốn dữ kiện: **thành viên bị truy cập**, **source module**, **source package**, **caller module**. Ví dụ: `java.lang.String.value`, `java.base`, `java.lang` và `unnamed module`. Khi thông báo nói `to unnamed module`, giải pháp runtime thường cần `ALL-UNNAMED`; khi nói `to module com.example.mapper`, cần nhắm tới đúng named module.

Có thể kiểm tra metadata tại runtime:

```java
Class<?> type = String.class;

Module sourceModule = type.getModule();
String packageName = type.getPackageName();
Module callerModule = DeepReflectionDemo.class.getModule();

System.out.println(sourceModule.getName());
System.out.println(packageName);
System.out.println(callerModule.isNamed());
System.out.println(sourceModule.isOpen(packageName, callerModule));
```

Đây là **đoạn minh họa dùng trong class `DeepReflectionDemo`**, không phải chương trình độc lập mới. Với ví dụ classpath chưa mở `java.lang`, `callerModule.isNamed()` là `false` và `sourceModule.isOpen("java.lang", callerModule)` là `false`. Khi thêm flag `--add-opens java.base/java.lang=ALL-UNNAMED`, phép kiểm tra `isOpen(packageName, callerModule)` trả `true`.

Nếu application được chia thành nhiều classloader/module layer hoặc nhiều Java processes, hãy kiểm tra đúng JVM process phát sinh exception. Các flags đã đặt cho process khác sẽ không giải quyết lỗi.

## 7. `canAccess()` và `trySetAccessible()`: cách dùng an toàn

Thay vì luôn gọi `setAccessible(true)` rồi để exception lan tới tận tầng ứng dụng, thư viện có thể chủ động thăm dò khả năng truy cập:

```java
import java.lang.reflect.Field;

public final class SafeFieldRead {
    public static Object read(Field field, Object receiver)
            throws IllegalAccessException {
        if (!field.canAccess(receiver) && !field.trySetAccessible()) {
            throw new IllegalStateException(
                    "Không thể mở reflection cho "
                    + field.getDeclaringClass().getName()
                    + "." + field.getName());
        }
        return field.get(receiver);
    }
}
```

Đây là **class tiện ích độc lập về cú pháp**; nó không bao gồm `main`. `receiver` phải hợp lệ với field: field instance cần một đối tượng thuộc class khai báo hoặc subtype phù hợp; field static dùng `null`. Truyền sai receiver có thể gây `IllegalArgumentException`. `trySetAccessible()` trả `false` khi không thể mở do quy tắc JPMS; không phải mọi lỗi truy cập đều chuyển thành `false`.

Lưu ý về thiết kế: nếu làm thư viện dùng trên Java 17+, đừng bắt `InaccessibleObjectException` rồi tiếp tục giả vờ đọc field thành công. Hãy cung cấp thông báo chẩn đoán có tên module/package và phương án API công khai, `opens` có chủ đích, hoặc cập nhật thư viện.

Với các framework mới, các lựa chọn có cấu trúc như constructor injection, accessors hợp lệ, factory methods, `MethodHandles.privateLookupIn` (nếu điều kiện module cho phép), hoặc cấu hình serialization được hỗ trợ có thể bền vững hơn so với phụ thuộc vào field `private` nội bộ của JDK. **`privateLookupIn` cũng không phải đường vòng để vượt qua package chưa được mở**, vì nó chịu những quy tắc access/module riêng.

## 8. Phân biệt với exception khác của Reflection

| Loại lỗi | Thường xảy ra ở đâu | Ý nghĩa thực tế |
| --- | --- | --- |
| `NoSuchFieldException` / `NoSuchMethodException` | `getDeclaredField` / `getDeclaredMethod` | Không tìm được tên/chữ ký trong phạm vi API tìm kiếm |
| `IllegalAccessException` | `Field.get/set`, `Method.invoke` hoặc constructor invoke | Access checks chưa được đáp ứng khi thực hiện thao tác |
| `InaccessibleObjectException` | `setAccessible(true)` | Không thể bật suppress Java access checks, thường do package/module chưa `opens` |
| `InvocationTargetException` | `Method.invoke` hoặc `Constructor.newInstance` | Thân method/constructor được gọi đã ném exception; xem `getCause()` |
| `IllegalArgumentException` | `Field.get/set`, `canAccess`, `Method.invoke`... | Đối số/receiver/kiểu không hợp lệ |
| `SecurityException` | Khi môi trường bảo mật có cơ chế kiểm soát tương ứng | Một chính sách runtime có thể từ chối thao tác khác với JPMS `opens` |

Từ khóa phân biệt:

- **Lookup failure:** tên thành viên hoặc chữ ký/phạm vi tìm kiếm sai.
- **Access check failure:** member tồn tại nhưng chưa thể thao tác.
- **Suppress failure:** không được bật chế độ vượt qua access checks ⇒ trường hợp `InaccessibleObjectException`.
- **Target exception:** method đã chạy, lỗi phát sinh từ code đích chứ không phải do mở quyền.

Chẩn đoán đúng loại lỗi sẽ tránh giải pháp sai như «thêm `--add-opens`» cho `NoSuchFieldException` hoặc sửa `getDeclaredMethod` khi thực tế package của module đang bị đóng.

## 9. Những lỗi cấu hình thường gặp

**Lỗi A — Dùng `exports` thay cho `opens`.** Thêm `exports p` chỉ giúp dùng public API; không cấp quyền truy cập `private` qua `setAccessible(true)` cho module bên ngoài. Cần `opens p to framework.module` nếu framework thực sự có yêu cầu legitimate deep reflection.

**Lỗi B — Mở sai phía.** Directive `opens` nằm trong module **sở hữu package bị phản chiếu**, không phải trong module caller. Nếu stack trace nói `module java.base does not "opens java.lang"` thì việc chỉnh `module-info.java` của ứng dụng để `opens com.example.app` không giải quyết vấn đề đối với `java.lang`.

**Lỗi C — Dùng sai target.** `ALL-UNNAMED` là tập unnamed modules trên classpath; một thư viện đã nằm trong named module cần quyền hướng tới tên module thật của nó. Thông báo exception là manh mối đáng tin hơn suy đoán từ tên JAR.

**Lỗi D — Đặt cờ ở build mà không đặt ở runtime.** `--add-opens` phải áp dụng lên đúng JVM thực hiện reflection. IDE Run Configuration, lệnh `java`, tiến trình Gradle Test, container và server production có thể có JVM args khác nhau. Tránh giải pháp «đã thêm flag» khi thêm nhầm vào JVM khác.

**Lỗi E — Dùng `--illegal-access=permit` theo hướng dẫn cũ.** Từ Java 17 tùy chọn này đã lỗi thời, không khôi phục được quyền mở package JDK như cơ chế tương thích trước đó. Nên cập nhật thư viện hoặc cấp `--add-opens` chính xác nếu thật sự cần.

**Lỗi F — Mở mọi package JDK để dập log.** Cách đó vừa mở rộng quyền runtime không cần thiết vừa che dấu phụ thuộc vào API nội bộ thiếu ổn định. Chỉ mở package đã xác minh và theo dõi vì sao thư viện còn cần truy cập đó.

**Lỗi G — Nghĩ `public` member luôn đủ.** Một public method bên trong class/package/module không được xuất đúng phạm vi có thể vẫn inaccessible. Cần xét cả accessibility của declaring class, package/module và caller.

## 10. Quy trình xử lý sự cố theo từng bước

1. **Xác định nơi ném lỗi.** Là `setAccessible`, `trySetAccessible`, `getDeclaredXxx` hay `invoke`? Chỉ nhìn câu «reflection failed» chưa đủ.
2. **Đọc chính xác message.** Ghi lại source module, package, caller và member. Nếu không có thông điệp JPMS, xem thêm khả năng khác.
3. **Xác định cách chạy.** Mã caller thuộc unnamed module trên classpath hay named module trên module path? Nơi lỗi phát sinh là đúng JVM/process nào?
4. **Xác minh mục đích.** Framework thực sự cần private field/method, hay có thể gọi public API hoặc cập nhật bản tương thích Java 17+?
5. **Nếu sở hữu module nguồn**, mở package nhỏ nhất tới đúng module đích bằng `opens ... to ...`. Nếu bên sử dụng là unnamed module và phải hỗ trợ trường hợp đó, cân nhắc chính sách opens rộng hơn một cách chủ động.
6. **Nếu không sở hữu module nguồn**, dùng phiên bản thư viện hỗ trợ JDK hiện tại; chỉ khi cần thiết mới thêm `--add-opens source/pkg=target` cho đúng runtime.
7. **Chạy lại test thực tế** trên JDK, runtime args và cách đóng gói giống production; kiểm tra không còn `InaccessibleObjectException` và hành vi chức năng vẫn chính xác.

### Câu hỏi tự kiểm tra

**1.** Vì sao `getDeclaredField("value")` có thể thành công nhưng `setAccessible(true)` thất bại?  
Vì tìm metadata không đồng nghĩa có quyền suppress access checks xuyên ranh giới module.

**2.** `exports com.example.model;` có đủ để framework đọc private field bằng reflection không?  
Không. Khi caller nằm trong module khác, thường cần `opens com.example.model to framework.module;` hoặc quyền runtime tương đương.

**3.** `--add-opens java.base/java.lang=ALL-UNNAMED` tác động đến đâu?  
Mở **package `java.lang`** thuộc **module `java.base`** cho deep reflection từ **unnamed modules** trong runtime JVM có flag ấy; không biến `String.value` thành public, không cho phép mọi compile-time imports từ JDK internals.

**4.** Khi `Method.invoke(...)` ném `InvocationTargetException`, có nên mặc định thêm `--add-opens`?  
Không. Phải đọc `getCause()` vì phương thức đích đã chạy và ném lỗi riêng; chỉ bổ sung opens khi thật sự có bằng chứng lỗi liên quan JPMS.

## Ghi nhớ

- `InaccessibleObjectException` thường báo **không thể bật suppress access checks**; nó không đồng nghĩa `NoSuchFieldException` hay `IllegalAccessException`.
- **`exports` cho public API**, **`opens` cho deep reflection tại runtime**, với khả năng chỉ định module đích.
- **`trySetAccessible()`** giúp kiểm tra khả năng mở quyền mà không phải luôn nhận `InaccessibleObjectException`; **`canAccess()`** trả lời về access với caller/receiver thực tế.
- Trên Java 17+, strong encapsulation bảo vệ phần lớn nội bộ JDK. Ưu tiên public API/thư viện tương thích; nếu buộc phải mở package, chỉ mở đúng nguồn/đích và đúng JVM.

Hiểu rành mạch ba tầng **tìm khai báo → xin suppress checks → thực thi truy cập** là chìa khóa để giải thích và xử lý đúng nguyên nhân của `InaccessibleObjectException`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-008">Phân biệt --add-exports với --add-opens</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `--add-exports` với `--add-opens` trong Java Module System

## 1. Vì sao cần hai tùy chọn này?

Từ Java 9, Java Platform Module System (JPMS) quản lý cả **phụ thuộc module** và **mức độ hiển thị package**. Một class được khai báo `public` vẫn chưa chắc sử dụng được từ module khác: package của class ấy có thể chưa được `exports`. Mặt khác, một framework dùng Reflection đôi khi cần đọc constructor, field hoặc method không public, mà quyền truy cập thông thường vẫn không cho phép.

Hai tùy chọn dòng lệnh cho phép thêm ngoại lệ vào cấu hình module:

- **`--add-exports`**: xuất một package của module nguồn cho module đích, để module đích có thể sử dụng **kiểu public và thành viên truy cập được theo quy tắc Java**, bao gồm truy cập trực tiếp khi biên dịch và khi chạy. Nó **không tự mở quyền vào private field/method**.
- **`--add-opens`**: mở một package cho module đích thực hiện **deep reflection tại runtime**, ví dụ `getDeclaredField(...)` rồi `trySetAccessible()` / `setAccessible(true)`. Nó **không biến package đó thành package được export cho trình biên dịch**.

Cả hai đều là ngoại lệ đối với tính đóng gói mạnh (*strong encapsulation*). Khi thiết kế một module mình sở hữu, nên cân nhắc khai báo `exports` / `opens` có chủ đích trong `module-info.java` trước khi cần đến cờ dòng lệnh. Khi phải tương thích với thư viện cũ, hãy mở đúng package cho đúng module cần dùng.

## 2. So sánh nhanh

| Tiêu chí | `--add-exports` | `--add-opens` |
| --- | --- | --- |
| Mục đích chính | Cho phép sử dụng API `public` của package bị đóng gói | Cho phép Reflection truy cập sâu tới cả thành viên không public |
| Truy cập trực tiếp từ mã nguồn Java bên ngoài module | **Có**, đối với các kiểu/thành viên mà Java visibility cho phép | **Không cấp quyền biên dịch** cho import hoặc gọi trực tiếp |
| Dùng với `javac` | **Có tác dụng** trong biên dịch | Không mở quyền biên dịch; `javac` cảnh báo tùy chọn không có hiệu lực ở compile time |
| Dùng với `java` | **Có tác dụng** tại runtime | **Có tác dụng** tại runtime |
| Cho phép `trySetAccessible()` với private member ở package đích | **Không**, chỉ export chưa đủ | **Có thể**, nếu package đã mở cho module của caller và không có giới hạn khác |
| Giữ các quy tắc `private`, package-private, `protected` khi truy cập trực tiếp | **Có** | **Có**; deep reflection vẫn phải dùng Reflection API |
| Tương ứng trong `module-info.java` | `exports some.package;` hoặc `exports some.package to target.module;` | `opens some.package;` hoặc `opens some.package to target.module;` |

**Không nên rút gọn thành “exports cho public, opens cho private”**: `opens` cũng cho phép Reflection với member public, còn `exports` cho phép những kiểu/thành viên public đã đủ điều kiện truy cập phản chiếu thông thường. Khác biệt thực tế là **quyền truy cập API theo Java language rules** so với **quyền deep reflection**.

## 3. Cú pháp và ý nghĩa từng phần

Cả hai tùy chọn đều nhận mẫu:

~~~text
--add-exports <source-module>/<package>=<target-module>[,<target-module>...]
--add-opens   <source-module>/<package>=<target-module>[,<target-module>...]
~~~

Ví dụ:

~~~bash
java --add-exports=demo.vault/internal.api=demo.client ...
java --add-opens=demo.vault/internal.api=demo.framework ...
java --add-opens=demo.vault/internal.api=ALL-UNNAMED ...
~~~

- `demo.vault` là **module chứa package** cần xuất/mở.
- `internal.api` là **tên package đầy đủ**, không phải tên class hay method.
- `demo.client` hoặc `demo.framework` là **module được cấp quyền**.
- `ALL-UNNAMED` là mục tiêu đặc biệt dành cho **tất cả unnamed modules** (thông thường là mã nạp từ classpath, kể cả các thư viện JAR trên classpath). Nó có phạm vi rộng hơn một named module cụ thể.
- Có thể chỉ định nhiều named module ở vế phải, cách nhau bằng dấu phẩy.
- Dạng `--add-opens=module/package=target` và `--add-opens module/package=target` đều được Java launcher hỗ trợ; dạng có `=` sau tên tùy chọn thuận tiện khi cấu hình JVM arguments trong một số công cụ.

Hai cờ này **không tự thêm quan hệ module readability**. Với named module muốn sử dụng dependency, vẫn cần `requires demo.vault;` trong `module-info.java` hoặc cơ chế thích hợp như `--add-reads`. Với ứng dụng trên classpath, nếu `demo.vault` nằm trên module path nhưng chưa được giải quyết vào module graph, có thể cần `--add-modules demo.vault`.

Lưu ý: tên target trong lệnh là **module của mã tiêu thụ API hoặc mã thực hiện Reflection**, không nhất thiết là module chứa class mà ứng dụng đang xử lý dữ liệu.

## 4. Ví dụ Java 17+ có thể biên dịch và chạy

Ta sẽ tự tạo một named module `demo.vault` chứa public class nhưng **không export package**. Hai chương trình ở classpath sẽ lần lượt thử **gọi trực tiếp public method** và **Reflection vào private method**. Cách này cho phép quan sát chính xác tác dụng từng flag, không cần phụ thuộc vào lớp nội bộ không ổn định của JDK.

### 4.1. Cấu trúc thư mục

~~~text
project/
├── src/
│   └── demo.vault/
│       ├── module-info.java
│       └── internal/
│           └── api/
│               └── PublicTool.java
├── client-src/
│   ├── DirectMain.java
│   └── ReflectMain.java
├── mods/                 (sinh khi biên dịch)
└── out/                  (sinh khi biên dịch)
~~~

File `src/demo.vault/module-info.java`:

~~~java
module demo.vault {
    // Không có exports hay opens cho internal.api.
}
~~~

File `src/demo.vault/internal/api/PublicTool.java`:

~~~java
package internal.api;

public final class PublicTool {
    private PublicTool() { }

    public static String greet() {
        return "hello";
    }

    private static String secret() {
        return "hidden";
    }
}
~~~

File `client-src/DirectMain.java`:

~~~java
import internal.api.PublicTool;

public class DirectMain {
    public static void main(String[] args) {
        System.out.println("direct: " + PublicTool.greet());
    }
}
~~~

File `client-src/ReflectMain.java`:

~~~java
import java.lang.reflect.Method;

public class ReflectMain {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("internal.api.PublicTool");
        Method method = type.getDeclaredMethod("secret");

        boolean opened = method.trySetAccessible();
        System.out.println("private opened: " + opened);

        if (opened) {
            System.out.println("reflect: " + method.invoke(null));
        }
    }
}
~~~

`ReflectMain` không import `PublicTool`: nó chỉ biết tên lớp dạng chuỗi. Phương thức `secret` là `private static`, nên `method.invoke(null)` là đúng kiểu receiver sau khi được mở quyền. `trySetAccessible()` trả `false` khi package chưa được mở cho unnamed module của caller.

### 4.2. Tạo thư mục đầu ra và biên dịch

Trong thư mục `project`, tạo trước các thư mục đầu ra. Ví dụ Bash (Linux/macOS):

~~~bash
mkdir -p mods/demo.vault out
~~~

Trong PowerShell (Windows), có thể dùng:

~~~powershell
New-Item -ItemType Directory -Force -Path mods/demo.vault,out
~~~

Các lệnh `javac` tiếp theo áp dụng như nhau trên cả hai môi trường:

~~~bash
javac -d mods/demo.vault src/demo.vault/module-info.java src/demo.vault/internal/api/PublicTool.java

javac --module-path mods --add-modules demo.vault --add-exports demo.vault/internal.api=ALL-UNNAMED -d out client-src/DirectMain.java client-src/ReflectMain.java
~~~

Ở lần biên dịch thứ hai, `DirectMain` **import trực tiếp** class từ package chưa xuất. Vì vậy phải cung cấp `--add-exports` cho **`javac`**. Nếu bỏ cờ này, trình biên dịch sẽ báo package `internal.api` không hiển thị từ module `demo.vault`.

`--add-modules demo.vault` đảm bảo named module trên module path được giải quyết. Cờ `--add-exports` ở đây chỉ cấp quyền cho unnamed modules, đúng với vị trí của `DirectMain` trên classpath.

### 4.3. Chạy ví dụ truy cập trực tiếp: cần `--add-exports` lúc chạy

~~~bash
java --module-path mods --add-modules demo.vault --add-exports demo.vault/internal.api=ALL-UNNAMED -cp out DirectMain
~~~

Stdout:

~~~text
direct: hello
~~~

**Vì sao vẫn cần cờ tại runtime?** Cờ `javac --add-exports` chỉ ảnh hưởng quá trình biên dịch. Nó không tự ghi một quyền export vĩnh viễn vào `demo.vault`. Nếu chạy chương trình đã biên dịch nhưng bỏ cờ runtime, việc liên kết/truy cập class không được export có thể dẫn đến `IllegalAccessError`.

Dùng **chỉ `--add-opens` ở `javac` không giải quyết lỗi import**, vì mở package để Reflection tại runtime không phải xuất package cho mã nguồn compile time.

### 4.4. Chạy ví dụ Reflection: cần `--add-opens`

Chạy trước **không cấp quyền mở package**:

~~~bash
java --module-path mods --add-modules demo.vault -cp out ReflectMain
~~~

Stdout:

~~~text
private opened: false
~~~

Reflection vẫn tìm được metadata phương thức qua `getDeclaredMethod("secret")`, nhưng không được bật quyền gọi method `private` trong package chưa mở cho caller.

Thêm **`--add-opens`**:

~~~bash
java --module-path mods --add-modules demo.vault --add-opens demo.vault/internal.api=ALL-UNNAMED -cp out ReflectMain
~~~

Stdout:

~~~text
private opened: true
reflect: hidden
~~~

Bây giờ package `internal.api` được mở cho unnamed module; `trySetAccessible()` có thể bật cờ bỏ qua kiểm tra quyền và `Method.invoke` gọi được phương thức `private`. **Không cần `--add-exports`** cho đoạn `ReflectMain` này, bởi nó không import hay tham chiếu tĩnh đến `PublicTool`.

### 4.5. Kết quả tổng hợp

| Tác vụ | Chỉ `--add-exports` | Chỉ `--add-opens` |
| --- | --- | --- |
| Biên dịch `DirectMain` có `import internal.api.PublicTool` | Có, nếu module được resolve và quyền đọc phù hợp | Không |
| Chạy `DirectMain` đã được biên dịch hợp lệ | Đủ quyền truy cập public API ở runtime | Không phải cấu hình cho quyền biên dịch ban đầu; đừng dùng thay `--add-exports` |
| Bật `trySetAccessible()` cho `private secret()` từ `ReflectMain` | Không, nếu không có quyền mở khác | Có trong ví dụ này |
| Truy cập phương thức `public` bằng Reflection thông thường | Có thể, theo quy tắc truy cập Java | Có thể, theo cơ chế reflective access |

Ví dụ được kiểm tra với OpenJDK 21 (mã và tùy chọn tương thích JDK 17): `direct: hello`; `private opened: false`; `private opened: true` rồi `reflect: hidden`.

## 5. So sánh với `exports` và `opens` trong `module-info.java`

Một tác giả module có thể khai báo quyền một cách chủ đích:

~~~java
module demo.library {
    exports demo.library.api;
    exports demo.library.spi to demo.plugin;

    opens demo.library.model to demo.framework;
}
~~~

Đây là **minh họa cấu hình**; muốn biên dịch phải có các package tương ứng và target modules phù hợp.

- `exports demo.library.api;`: mọi module có quyền đọc module này được dùng public API của package.
- `exports ... to demo.plugin;`: chỉ `demo.plugin` có quyền export được chỉ định.
- `opens ... to demo.framework;`: mở package để framework dùng Reflection vào các thành viên, kể cả non-public, ở runtime. Nó không cho phép code của framework import trực tiếp những kiểu trước đó chưa được export lúc biên dịch.
- Có thể **vừa `exports` vừa `opens` một package** khi cần cả API công khai lẫn deep reflection.
- `open module demo.library { ... }` mở mọi package trong module đó cho Reflection. Cách này rộng hơn mở từng package; một `open module` không khai báo thêm các `opens` riêng.

Cờ dòng lệnh thêm các cạnh export/open tương ứng **cho tiến trình đang biên dịch hoặc chạy**, dù module gốc không khai báo như vậy. Chúng không sửa file `module-info.java` đã phát hành.

## 6. Chẩn đoán lỗi và chọn đúng cờ

### Trường hợp A: lỗi import ở compile time

Thông báo thường có ý như:

~~~text
package ... is not visible
(package ... is declared in module ..., which does not export it)
~~~

Nếu mã **thực sự phải tham chiếu trực tiếp public API** ở package ấy, xét `--add-exports` cho `javac` và lệnh chạy. Kiểm tra trước xem có API public được hỗ trợ ở package khác không. `--add-opens` không giúp import compile thành công.

### Trường hợp B: `IllegalAccessError` tại runtime

Thường xảy ra khi mã đã compile với quyền truy cập nhưng JVM chạy không có cùng quyền export, hoặc môi trường module khác bản build. Đối chiếu cấu hình `--add-exports` giữa **compile** và **runtime** và kiểm tra module graph. Đừng vội thêm `--add-opens` chỉ vì thấy chữ “Access”.

### Trường hợp C: `InaccessibleObjectException` hoặc `trySetAccessible() == false`

Nếu stack trace/điều kiện báo package chưa được **opened** cho module gọi Reflection, cờ phù hợp thường là:

~~~bash
java --add-opens=source.module/source.package=target.module ...
~~~

Với một ứng dụng legacy chạy ở classpath, target có thể là `ALL-UNNAMED`. Dù đã mở package, vẫn phải tuân thủ các ràng buộc API khác: kiểu receiver hợp lệ, hành vi `final`, khả năng sửa field và chính sách môi trường.

### Trường hợp D: không tìm được class/module

`ClassNotFoundException` hoặc thông báo không tìm thấy module **không tự động được giải quyết** bằng `--add-exports` / `--add-opens`. Kiểm tra đường dẫn `--module-path`, `-cp`, tên module và việc resolve module (ví dụ `--add-modules`) trước. Nếu named module cần đọc một module khác mà chưa có quan hệ readability, xét `requires` hoặc `--add-reads` thích hợp.

## 7. Quy tắc dùng an toàn, bền vững

1. **Ưu tiên API được hỗ trợ.** Package nội bộ của JDK/thư viện có thể thay đổi giữa các bản nâng cấp; có thể thay code Reflection bằng API chính thức để khỏi duy trì cờ.
2. **Mở quyền tối thiểu.** Chọn một package và target named module cụ thể khi có thể. `ALL-UNNAMED` cấp quyền rộng cho mọi mã trên classpath.
3. **Đặt cờ ở đúng JVM.** Với `java -jar app.jar`, JVM argument cần xuất hiện **trước `-jar`**. Test runner, IDE, Gradle/Maven test fork hoặc ứng dụng trong container có JVM riêng thì cần cấu hình vào tiến trình Java thực sự chạy code.
4. **Phân biệt compile và run.** `--add-exports` có thể cần ở cả hai; `--add-opens` chỉ phục vụ runtime Reflection. Đặt `--add-opens` vào `javac` không tạo quyền compile time.
5. **Không biến cờ thành hợp đồng API.** Có quyền Reflection không đảm bảo thao tác sẽ thành công: còn kiểu đối tượng đích, `final` field, kiểu dữ liệu, exception từ method được gọi và các giới hạn khác.
6. **Kiểm tra phiên bản Java.** Ở JDK 17, cơ chế đóng gói mạnh của JDK được áp dụng mặc định; tùy chọn legacy `--illegal-access` không còn tác dụng mở quyền như ở các phiên bản cũ.

## 8. Tự kiểm tra

**Câu 1.** Một package không export chứa `public class InternalApi`; client dùng `import` để gọi public method. Cần loại quyền nào?  
**Đáp án:** `--add-exports` hoặc một export phù hợp trong `module-info.java`, đồng thời đáp ứng readability. Đối với lựa chọn cờ dòng lệnh, cấp quyền ở cả bước cần biên dịch và bước chạy.

**Câu 2.** Thư viện dùng `getDeclaredField("secret")` và `trySetAccessible()`, nhưng package trong named module chưa được mở. Cờ nào đúng trọng tâm?  
**Đáp án:** `--add-opens` từ module chứa package sang module thực hiện Reflection; `--add-exports` không đủ để mở private field.

**Câu 3.** `--add-opens` có làm `import internal.api.PublicTool` compile được không?  
**Đáp án:** Không. `--add-opens` không cấp quyền truy cập trực tiếp lúc compile.

**Câu 4.** `ALL-UNNAMED` có nghĩa là cấp quyền cho một JAR duy nhất?  
**Đáp án:** Không. Nó bao gồm mọi unnamed module, thường là các thư viện/app trên classpath.

**Câu 5.** Dùng `--add-opens` rồi mọi thao tác ghi `final` field đều hợp lệ?  
**Đáp án:** Không. Mở package để Reflection không xóa các giới hạn về thao tác trên field và những quy tắc API khác.

**Ghi nhớ:** cần **gọi trực tiếp public API bị che bởi ranh giới module** → xét `--add-exports`; cần **Reflection sâu vào thành viên không public** → xét `--add-opens`. Luôn xác định đúng module nguồn, package, module đích, giai đoạn compile/run và quan hệ module readability.

### Tài liệu chính thức

- [Dev.java — Circumventing Strong Encapsulation with `--add-exports` and `--add-opens`](https://dev.java/learn/organizing/modules/add-exports-opens/)
- [Dev.java — Reflective Access with Open Modules and Open Packages](https://dev.java/learn/modules/opening-for-reflection/)
- [Oracle JDK 17 Migration Guide — Strong Encapsulation and Module Options](https://docs.oracle.com/en/java/javase/17/migrate/)
- [Java Language Specification §7.7.2 — Exported and Opened Packages](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7.2)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-009">Giới hạn của --illegal-access trong Java hiện đại</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của `--illegal-access` trong Java hiện đại

## 1. Vấn đề mà `--illegal-access` từng giải quyết

Trước Java 9, nhiều thư viện dùng Reflection để mở những field, method, constructor nội bộ của JDK, ví dụ gọi `setAccessible(true)` đối với member không public trong các package thuộc `java.base`. Cách này dựa vào chi tiết triển khai, dễ bị phá vỡ khi JDK cập nhật. Kể từ Java 9, **Java Platform Module System (JPMS)** đưa ra quy tắc đóng gói mạnh (*strong encapsulation*): mỗi module quyết định package nào được `exports` (truy cập công khai) và package nào được `opens` (cho phép deep reflection).

`--illegal-access` là tùy chọn khởi chạy JVM được đưa vào để hỗ trợ chuyển đổi ứng dụng cũ sử dụng **illegal reflective access vào các package của JDK từng có trong Java 8**. Nó không phải công tắc vô hiệu hóa mọi quy tắc truy cập Java, không tạo quyền truy cập cho toàn bộ ứng dụng/modular library tùy ý và không hợp thức hóa mã dùng API nội bộ.

**Điểm phải nhớ với Java hiện đại: kể từ JDK 17, `--illegal-access=permit`, `warn`, `debug` và `deny` đều không còn tác dụng cấp quyền.** Trong các JDK hiện hành còn chấp nhận cú pháp này, JVM chỉ cảnh báo tùy chọn đã lỗi thời/bị bỏ qua. Không thể dùng `--illegal-access=permit` để chữa `InaccessibleObjectException` trên JDK 17+.

## 2. Dòng thời gian: Java 8 → 9–15 → 16 → 17+

| Phiên bản | Trạng thái đóng gói JDK và `--illegal-access` | Hệ quả thực tế |
| --- | --- | --- |
| Java 8 và cũ hơn | Chưa có JPMS hay tùy chọn `--illegal-access`. | Reflection vẫn chịu kiểm tra quyền truy cập Java và các giới hạn khác; một số thư viện truy cập được chi tiết JDK nội bộ. |
| JDK 9–15 | JPMS được giới thiệu; chế độ mặc định tương đương `--illegal-access=permit` đối với nhiều package JDK đã tồn tại từ Java 8. | Cho phép một số truy cập Reflection cũ trong giai đoạn chuyển tiếp; lần truy cập bất hợp lệ đầu tiên được cảnh báo trong chế độ permit. |
| JDK 16 | JEP 396 đổi mặc định thành `--illegal-access=deny`, bật strong encapsulation theo mặc định. | `--illegal-access=permit` vẫn có thể tạm khôi phục quyền truy cập tương thích trước đó cho phạm vi mà tùy chọn hỗ trợ. |
| JDK 17 trở lên | JEP 403 biến `--illegal-access` thành tùy chọn **obsolete/ignored**. | Các giá trị `permit`/`warn`/`debug`/`deny` không nới hoặc siết thêm quyền; muốn deep reflection phải mở rõ package phù hợp bằng cơ chế JPMS. |

Trong giai đoạn JDK 9–16, các chế độ lịch sử là:

- `permit`: nới quyền Reflection theo phạm vi tương thích cũ; cảnh báo lần đầu.
- `warn`: tương tự về việc cho phép, nhưng cảnh báo cho từng truy cập bất hợp lệ.
- `debug`: như `warn`, kèm stack trace nhằm tìm caller.
- `deny`: không nới quyền; truy cập vẫn có thể được phép bởi những cấu hình tường minh khác như `--add-opens`.

Các cờ trên **không áp dụng chung cho mọi module do lập trình viên tạo**. Đặc biệt, trên JDK 17+, viết `--illegal-access=deny` cũng không phải cách khóa mọi sự mở quyền đang có: JVM bỏ qua tùy chọn này; các `--add-opens` cụ thể vẫn quyết định quyền truy cập được thêm vào.

## 3. Vì sao `setAccessible(true)` không còn vượt được mọi rào cản?

Reflection có thể tìm ra một member nhưng chưa chắc có quyền đọc hoặc gọi member đó. Cần phân biệt:

1. `getDeclaredMethod()`, `getDeclaredField()`, `getDeclaredConstructor()`: **lấy metadata** về member có khai báo trong kiểu, kể cả private. Việc lấy metadata không tự cấp quyền sử dụng.
2. `setAccessible(true)`: yêu cầu bỏ qua một phần kiểm tra truy cập thông thường cho member, **chỉ thành công khi JPMS cho phép**. Nếu package của named module không mở cho module gọi và điều kiện khác không thỏa, có thể ném `java.lang.reflect.InaccessibleObjectException`.
3. `trySetAccessible()`: phiên bản thuận tiện trả `false` khi không thể cho phép truy cập, thay vì `InaccessibleObjectException` trong trường hợp không có quyền; vẫn cần xử lý ngoại lệ liên quan nếu có.
4. `Method.invoke()`, `Field.get()` hoặc constructor invocation: thực sự dùng member. Nếu chưa được phép, các API này có thể ném `IllegalAccessException` (khác với ngoại lệ khi yêu cầu bật quyền).

Ngay cả một public class trong package **đã `exports`** cũng không tự mở private member cho ứng dụng khác. `exports` và `opens` có tác dụng khác nhau:

| Cấu hình | Truy cập public type/member bằng Java thông thường | Deep reflection vào private member từ module khác |
| --- | --- | --- |
| Package `exports` đến module gọi | Được phép, nếu các điều kiện truy cập Java khác cũng thỏa. | Không tự động được phép. |
| Package `opens` đến module gọi | Không tự động cho phép code khác compile tham chiếu public type khi package không `exports`. | Được phép yêu cầu `setAccessible(true)` trong những điều kiện JPMS thích hợp. |
| `exports` và `opens` | Cho phép cả truy cập public thông thường và deep reflection thuộc phạm vi đã cấp. | Có thể hoạt động. |
| Package không export/open cho caller | Không được truy cập thông thường từ caller. | Bị từ chối deep reflection. |

## 4. Ví dụ Java 17+: lỗi thật và cách mở đúng một package

Ví dụ dưới dùng **module tự tạo**, không phụ thuộc field nội bộ của `String` hay lớp nội bộ JDK có thể thay đổi. Module `demo.lib` cho phép lớp ngoài nhìn thấy `Greeter` qua `exports demo.api`, nhưng chưa cho phép truy cập deep reflection tới `private secret()`.

Tạo cấu trúc thư mục:

~~~text
project/
├── src/
│   └── demo.lib/
│       ├── module-info.java
│       └── demo/
│           └── api/
│               └── Greeter.java
└── Main.java
~~~

**Tệp `src/demo.lib/module-info.java`:**

~~~java
module demo.lib {
    exports demo.api;
}
~~~

**Tệp `src/demo.lib/demo/api/Greeter.java`:**

~~~java
package demo.api;

public class Greeter {
    private String secret() {
        return "hello";
    }
}
~~~

**Tệp `Main.java`:** chương trình nằm trên class path, tức thuộc *unnamed module*.

~~~java
import demo.api.Greeter;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;

public class Main {
    public static void main(String[] args) throws Exception {
        Method method = Greeter.class.getDeclaredMethod("secret");

        try {
            method.setAccessible(true);
            System.out.println("result=" + method.invoke(new Greeter()));
        } catch (InaccessibleObjectException e) {
            System.out.println("blocked=" + e.getClass().getSimpleName());
        }
    }
}
~~~

Đứng tại thư mục `project`, biên dịch với JDK 17+:

~~~bash
javac --release 17 -d mods --module-source-path src -m demo.lib
javac --release 17 --module-path mods --add-modules demo.lib -d classes Main.java
~~~

### Chạy 1: không mở package

~~~bash
java --module-path mods --add-modules demo.lib -cp classes Main
~~~

Stdout:

~~~text
blocked=InaccessibleObjectException
~~~

`getDeclaredMethod("secret")` đã tìm được method nhưng `setAccessible(true)` không có quyền mở private member. Vì vậy không in ra `hello`.

### Chạy 2: thêm `--illegal-access=permit`

~~~bash
java --illegal-access=permit --module-path mods --add-modules demo.lib -cp classes Main
~~~

Stdout vẫn là:

~~~text
blocked=InaccessibleObjectException
~~~

Trên OpenJDK 24, JVM còn phát cảnh báo ra stderr, chẳng hạn:

~~~text
OpenJDK 64-Bit Server VM warning: Ignoring option --illegal-access=permit; support was removed in 17.0
~~~

Dòng cảnh báo, định dạng và việc tương lai JVM có chấp nhận cú pháp cũ nữa hay không phụ thuộc phiên bản. **Điểm học tập là kết quả truy cập không thay đổi.** Đồng thời, `--illegal-access` lịch sử vốn không phải cơ chế mở tùy ý module ứng dụng như `demo.lib`.

### Chạy 3: cấp quyền đúng package và đúng caller

~~~bash
java --add-opens demo.lib/demo.api=ALL-UNNAMED --module-path mods --add-modules demo.lib -cp classes Main
~~~

Stdout:

~~~text
result=hello
~~~

`--add-opens demo.lib/demo.api=ALL-UNNAMED` mở package `demo.api` thuộc module `demo.lib` cho các unnamed module (thường là code trên class path). Khi ấy `setAccessible(true)` thành công.

**Kết luận từ ví dụ:** với nhu cầu deep reflection, quyền được thêm theo **module/package/caller**, không phải một cờ toàn cục `--illegal-access`. Cả ba lệnh và kết quả ở trên đã được kiểm tra với mã Java 17 tương thích.

## 5. Cấu hình thay thế đúng mục đích

### `--add-opens`: cho phép deep reflection

Cú pháp runtime:

~~~text
--add-opens <module-nguồn>/<package>=<module-đích>
~~~

Ví dụ cho code trên class path truy cập private member thuộc `java.lang`:

~~~bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp classes Main
~~~

Dòng này chỉ minh họa **cú pháp mở một package của JDK**, không phải lệnh chạy cho module `demo.lib` trong phần 4. Ví dụ phần 4 cần `--add-opens demo.lib/demo.api=ALL-UNNAMED`. Không có cơ sở để mở mọi package `java.base` chỉ vì một thư viện báo lỗi với một package cụ thể.

Nếu caller là **named module**, thay `ALL-UNNAMED` bằng tên module gọi, ví dụ:

~~~text
--add-opens demo.lib/demo.api=app.consumer
~~~

`--add-opens` thường là biện pháp tương thích tạm thời cho thư viện cũ; chọn package thực sự cần mở theo thông báo lỗi và tài liệu của thư viện. Quyền này cho phép caller có khả năng vượt kiểm tra truy cập thông thường với các member thuộc package đó.

### `--add-exports`: cho phép sử dụng public API của package chưa export

Cú pháp:

~~~text
--add-exports <module-nguồn>/<package>=<module-đích>
~~~

Dùng khi thư viện/ứng dụng cần dùng **public type và public member** của package chưa được export. Có thể cần cờ ở cả lúc biên dịch và lúc chạy. `--add-exports` không tương đương với cho phép deep reflection tới private member; trường hợp đó thường cần `--add-opens`.

### Khai báo chính thức trong `module-info.java`

Nếu **sở hữu** module thư viện và chủ động quyết định API, có thể khai báo:

~~~java
module demo.lib {
    exports demo.api;              // Cho code ngoài dùng public API
    opens demo.api to app.consumer; // Chỉ cho named module app.consumer deep reflection
}
~~~

Đây là ví dụ cấu hình cho tình huống **có module `app.consumer`**; đừng thay trực tiếp vào dự án class path ở phần 4 nếu chưa có module đó. Có thể khai báo `opens demo.api;` để mở package cho mọi module, nhưng nên chọn phạm vi hẹp phù hợp với kiến trúc. `open module ...` mở tất cả package của module cho reflection, thường rộng hơn nhu cầu thực tế.

Trong ứng dụng sử dụng framework, tốt nhất cấu hình `opens` (hoặc `opens ... to ...`) tại module mà mình kiểm soát, theo đúng tài liệu framework. Nếu vấn đề nằm ở internals của **JDK**, lập trình viên không sửa được `java.base/module-info.java` để thêm `opens`; phải dùng API công khai, cập nhật thư viện hoặc khai báo cờ runtime hẹp.

### `--add-reads` không thay thế `--add-opens`

`--add-reads` thay đổi quan hệ **readability** giữa module; nó không mở private member của package. Khi debug `InaccessibleObjectException`, thêm `--add-reads` không tự giải quyết thiếu quyền deep reflection.

## 6. Chẩn đoán lỗi khi nâng từ Java 8/11 lên Java 17+

Thông báo điển hình:

~~~text
java.lang.reflect.InaccessibleObjectException:
Unable to make ... accessible: module java.base
does not "opens java.lang" to unnamed module ...
~~~

Văn bản chính xác có thể khác giữa JDK và member. Những phần cần xác định là:

1. **Module nguồn:** ví dụ `java.base`.
2. **Package bị đóng:** ví dụ `java.lang`.
3. **Caller cần quyền:** ví dụ `unnamed module` cho class path hoặc tên một named module.
4. **Tác vụ truy cập:** deep reflection vào non-public member hay tham chiếu thông thường tới public API trong package không export?

Nếu thật sự cần deep reflection và không thể chuyển sang API hỗ trợ chính thức, một tùy chọn thử có phạm vi hẹp là:

~~~text
--add-opens java.base/java.lang=ALL-UNNAMED
~~~

Đây là **ví dụ được suy ra từ thông báo giả định nêu trên**, không phải lời khuyên thêm cờ đó cho mọi dự án.

Quy trình xử lý hợp lý:

- **Xác định thư viện gọi Reflection:** đọc stack trace và phụ thuộc liên quan. Có thể chính thư viện serialize, dependency injection, ORM, bytecode proxy, mocking hoặc công cụ kiểm thử đang truy cập JDK internals.
- **Ưu tiên cập nhật dependency:** bản mới thường đã dùng API Java được hỗ trợ hoặc cơ chế phù hợp với JPMS. Tránh tự duy trì danh sách dài `--add-opens` nếu còn giải pháp đúng cấp API.
- **Nếu module là do mình viết:** chỉnh `module-info.java` theo đúng phạm vi `exports` hoặc `opens` cần thiết.
- **Nếu buộc mở package runtime:** chỉ thêm từng `--add-opens` được chứng minh cần thiết, đặt vào cấu hình JVM chạy chương trình và môi trường test thực sự. Cấu hình cho một tiến trình `java` không tự động áp dụng cho tiến trình JVM khác do công cụ build tạo.
- **Chạy lại trên phiên bản JDK triển khai thực tế:** có thể có thêm lỗi khi code chạm tới package khác. Không kết luận một cờ là đủ cho mọi môi trường từ một stack trace duy nhất.

Cách nhanh để xác định Java runtime đang chạy:

~~~bash
java -version
~~~

Nếu lệnh test/IDE/production dùng JDK khác với terminal, cần xác minh JVM **thực sự khởi chạy ứng dụng**. Đừng dựa vào `javac -version` của một terminal để kết luận toàn bộ hệ thống đã sử dụng cùng runtime.

## 7. Những hiểu lầm quan trọng

| Nhận định sai | Sửa cho đúng |
| --- | --- |
| “Thêm `--illegal-access=permit` là đủ khi nâng lên JDK 17+.” | Cờ đã obsolete và bị bỏ qua từ JDK 17. |
| “`--illegal-access=deny` vẫn đang chặn quyền trên JDK mới.” | Trạng thái quyền trên JDK 17+ không do cờ này quyết định; các tùy chọn cụ thể khác vẫn có tác dụng. |
| “`setAccessible(true)` luôn bỏ qua private bất kể module.” | JPMS có thể từ chối với `InaccessibleObjectException`. |
| “`getDeclaredField()` tìm được field nghĩa là đọc được.” | Khả năng nhìn metadata khác với quyền truy cập giá trị. |
| “`--add-exports` và `--add-opens` giống nhau.” | `exports` liên quan truy cập public API; `opens` hỗ trợ deep reflection. |
| “`ALL-UNNAMED` cho phép một named module gọi.” | `ALL-UNNAMED` nhắm tới code thuộc unnamed modules, thường là class path. |
| “Mở toàn bộ JDK internals là cách sửa bền vững.” | Dùng API chính thức, thư viện tương thích và chỉ mở package cụ thể khi cần. |
| “Mọi lỗi `IllegalAccessException` đều do `--illegal-access`.” | Có thể liên quan Java visibility, module readability, package export/open, caller hoặc đường gọi cụ thể; cần đọc ngoại lệ và stack trace. |

**Ngoại lệ lịch sử cần hiểu đúng:** JEP 403 vẫn cho phép một số API nội bộ quan trọng như `sun.misc.Unsafe` tiếp tục tồn tại theo cơ chế đặc biệt. Điều đó không có nghĩa toàn bộ JDK internals được mở, cũng không biến API không chuẩn thành hợp đồng ổn định.

## 8. Tự kiểm tra

1. Trên JDK 17+, chạy ba lệnh trong phần 4. Giải thích tại sao `Greeter.class.getDeclaredMethod("secret")` thành công nhưng `method.setAccessible(true)` bị từ chối trước khi mở package.
2. Thay `setAccessible(true)` bằng `System.out.println(method.trySetAccessible());`. Dự đoán `false` khi thiếu quyền và `true` khi có `--add-opens`; nhớ bỏ dòng `invoke` hoặc xử lý riêng nếu chỉ muốn thử khả năng mở.
3. Tại sao `exports demo.api` không cho truy cập private method? Nếu caller chuyển thành named module `app.consumer`, cần thay mục tiêu `ALL-UNNAMED` bằng gì?
4. Nếu thư viện chỉ cần public type của package chưa export, nên đánh giá `--add-exports` hay `--add-opens` trước? Nếu cần deep reflection vào private field thì chọn gì?
5. Trên JDK 17+, nếu ứng dụng cũ chạy với `--illegal-access=warn`, cảnh báo cũ có còn đóng vai trò công cụ phát hiện từng truy cập nữa không? Tại sao?

## Tài liệu chính thức

- [JEP 261 — Module System](https://openjdk.org/jeps/261)
- [JEP 396 — Strongly Encapsulate JDK Internals by Default (JDK 16)](https://openjdk.org/jeps/396)
- [JEP 403 — Strongly Encapsulate JDK Internals (JDK 17)](https://openjdk.org/jeps/403)
- [Oracle JDK 24 Migration Guide — Strong Encapsulation in the JDK](https://docs.oracle.com/en/java/javase/24/migrate/migrating-jdk-8-later-jdk-releases.html)
- [Oracle Java Launcher Reference — `--add-opens`, `--add-exports`, obsolete options](https://docs.oracle.com/en/java/javase/24/docs/specs/man/java.html)
- [Java SE 17 API — `AccessibleObject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="module-encapsulation-010">Thiết kế API công khai thay vì phụ thuộc nội bộ JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế API công khai thay vì phụ thuộc nội bộ JDK

Java Reflection cho phép đọc metadata và gọi thành viên khi đáp ứng quy tắc truy cập; nó không biến mọi class nhìn thấy trong JDK thành **API được hỗ trợ lâu dài**. Trong hệ thống module Java (JPMS), một lớp có chữ `public` vẫn có thể nằm trong package không được `exports` và không được dùng như một hợp đồng công khai. Cách thiết kế bền vững là chọn API công khai có tài liệu, đặt ranh giới module rõ ràng, che giấu implementation và dùng Reflection đúng phạm vi quyền truy cập.

Bài học này thuộc chủ đề **Module và đóng gói**: ta sẽ nhận diện API JDK nội bộ, hiểu vì sao việc cưỡng ép truy cập dễ hỏng khi nâng JDK, thiết kế API ổn định cho module của chính mình, kiểm tra bằng Reflection và lập kế hoạch chuyển đổi mã đang dùng nội bộ JDK.

## 1. “Public” về cú pháp khác “public API” về hợp đồng

Một class có thể là `public class Example`, nhưng có ít nhất ba câu hỏi khác nhau:

1. **Class/member có modifier `public` không?** `Modifier.isPublic(type.getModifiers())` hoặc `Modifier.isPublic(method.getModifiers())` kiểm tra một phần metadata.
2. **Package có được module sở hữu `exports` cho module gọi không?** Với named module, JPMS kiểm tra accessibility tại ranh giới module. Package không export không trở thành API dùng trực tiếp chỉ vì class là `public`.
3. **API đó có được JDK hoặc thư viện cam kết hỗ trợ, ghi tài liệu và duy trì không?** Đây là câu hỏi về tính ổn định, khả năng tương thích và chính sách phát hành. JPMS giúp đóng gói, nhưng `exports` tự nó không phải một cam kết tương thích vô hạn.

Các namespace mang tính cảnh báo khi gặp trong ứng dụng là `jdk.internal.*`, nhiều package `sun.*`, `sun.reflect.*` hoặc `sun.security.*`. Chúng có thể thay đổi, bị đóng gói chặt hơn hoặc bị loại khỏi runtime. Một ngoại lệ đáng chú ý là một số API được tài liệu hóa trong package có tiền tố `com.sun.*`, chẳng hạn `com.sun.net.httpserver.HttpServer` thuộc module `jdk.httpserver`: **không thể chỉ dựa vào tiền tố package để kết luận API có được hỗ trợ hay không**. Hãy tra chính tài liệu API và module của phiên bản JDK mà dự án hỗ trợ.

Một ví dụ quen thuộc: mục tiêu mã hóa/giải mã Base64 nên dùng `java.util.Base64` (API chính thức), thay vì dựa vào bộ mã hóa nội bộ cũ của JDK.

```java
import java.nio.charset.StandardCharsets;
import java.util.Base64;

byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
String encoded = Base64.getEncoder().encodeToString(input);
byte[] decoded = Base64.getDecoder().decode(encoded);
String result = new String(decoded, StandardCharsets.UTF_8);
```

Đây là đoạn minh họa để đặt trong một phương thức. API `Base64` có hợp đồng công khai, nằm trong `java.base`; ứng dụng không cần mở package nội bộ hay dùng Reflection để mã hóa Base64.

**Nguyên tắc:** từ khóa `public` chỉ là một phần của câu chuyện. Muốn phụ thuộc lâu dài, cần kiểm tra **package được xuất khẩu cho bên dùng, khả năng truy cập thật sự và tư cách API được hỗ trợ**.

## 2. Ranh giới JPMS: requires, exports và opens

JPMS (Java Platform Module System, Java 9+) mô tả phụ thuộc và truy cập module bằng `module-info.java`.

| Khai báo hoặc cơ chế | Mục đích | Tác động đáng nhớ |
| --- | --- | --- |
| `requires com.example.lib;` | Module hiện tại đọc module `com.example.lib` | Chỉ đọc được module chưa đủ để truy cập các package không export |
| `exports com.example.lib.api;` | Mở package API cho code từ các module đọc module này | Cho phép truy cập kiểu/thành viên public theo quy tắc Java; Reflection thông thường trên API public cũng dùng được |
| `exports com.example.lib.spi to com.example.plugin;` | Export có điều kiện tới module được nêu | Module khác không được import trực tiếp package này |
| `opens com.example.lib.model;` | Cho phép truy cập Reflection sâu ở runtime | Không cấp quyền import package không export ở compile time |
| `opens com.example.lib.model to com.example.serializer;` | Mở package cho một module Reflection cụ thể | Giới hạn phạm vi mở so với `opens` không định danh |
| Không `exports`, không `opens` | Giấu chi tiết implementation | Code bên ngoài không có quyền truy cập trực tiếp hoặc truy cập Reflection sâu thông thường |

`exports` và `opens` **không thay thế nhau**:

- `exports` phục vụ API public. Đọc metadata và gọi public method trên public class thuộc package đã export có thể thực hiện qua Reflection mà không cần mở package để sửa quyền truy cập.
- `opens` cho phép các công cụ như serializer hoặc dependency injection truy cập sâu vào thành viên không public khi truy cập đó thực sự cần thiết và còn phù hợp với các kiểm tra khác. Nó **không** làm một package trở thành package được import bình thường tại compile time.
- `open module com.example.lib { ... }` mở toàn bộ package của module cho Reflection sâu. Đây là lựa chọn rộng; thường nên ưu tiên `opens package to framework.module` chính xác khi làm việc với framework.
- Một package có thể vừa `exports` vừa `opens` nếu có lý do rõ ràng cho cả hai; còn API public thông thường chỉ cần `exports`.

Đối với một class trong named module, cần xét cả việc module gọi **đọc** module sở hữu (readability), package **được export** tới bên gọi và member có quyền truy cập phù hợp. Khả năng tải `Class` bằng tên hay lấy được metadata không đồng nghĩa sẽ gọi hợp lệ được một thành viên.

### Kiểm tra xuất khẩu/mở package bằng API công khai của Module

```java
Module owner = String.class.getModule();
Module caller = MyClass.class.getModule(); // MyClass thuộc ứng dụng của bạn

boolean publicApiVisible = owner.isExported("java.lang", caller);
boolean deepReflectionAllowed = owner.isOpen("java.lang", caller);
```

Đây là đoạn minh họa, `MyClass` là lớp ứng dụng do bạn định nghĩa. Hai hàm `Module.isExported(String, Module)` và `Module.isOpen(String, Module)` phân biệt quyền export và open **đối với module gọi cụ thể**. Với JDK 17 tiêu chuẩn, `java.base` export `java.lang` nhưng không mở không điều kiện package này cho mọi ứng dụng; vì vậy đừng mặc định đọc được mọi field private của `String`.

Kiểm tra `Module.isExported` vẫn chưa đủ để kết luận một API an toàn dùng lâu dài: nó chỉ mô tả chính sách truy cập JPMS của phiên bản runtime đang xét.

## 3. Ví dụ kiến trúc: chỉ xuất khẩu API, giữ implementation trong module

Thiết kế một thư viện `com.example.greeter` với hai package:

```text
src/
├── com.example.greeter/
│   ├── module-info.java
│   └── com/example/greeter/
│       ├── api/
│       │   ├── Greeter.java
│       │   └── Greeters.java
│       └── internal/
│           └── DefaultGreeter.java
└── com.example.app/
    ├── module-info.java
    └── com/example/app/Main.java
```

Mục tiêu: module ứng dụng có thể tạo dịch vụ qua factory và sử dụng interface public. Thư viện được phép thay đổi `DefaultGreeter` mà không bắt người dùng phụ thuộc vào lớp implementation.

### Tệp 1 — src/com.example.greeter/module-info.java

```java
module com.example.greeter {
    exports com.example.greeter.api;
}
```

Cố ý **không export** package `com.example.greeter.internal`. Code của chính module `com.example.greeter` vẫn dùng được class trong package đó theo quyền truy cập Java thông thường.

### Tệp 2 — src/com.example.greeter/com/example/greeter/api/Greeter.java

```java
package com.example.greeter.api;

public interface Greeter {
    String greet(String name);
}
```

### Tệp 3 — src/com.example.greeter/com/example/greeter/api/Greeters.java

```java
package com.example.greeter.api;

import com.example.greeter.internal.DefaultGreeter;

public final class Greeters {
    private Greeters() {
    }

    public static Greeter create() {
        return new DefaultGreeter();
    }
}
```

Factory trả về **interface** thay vì `DefaultGreeter`, giữ hợp đồng của bên gọi nhỏ và rõ.

### Tệp 4 — src/com.example.greeter/com/example/greeter/internal/DefaultGreeter.java

```java
package com.example.greeter.internal;

import com.example.greeter.api.Greeter;

public final class DefaultGreeter implements Greeter {
    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

Từ khóa `public` của `DefaultGreeter` là cần thiết để factory ở package khác trong cùng module gọi constructor công khai. Nhưng class này vẫn không nằm trong public API **đối với module ứng dụng** vì package không được export.

### Tệp 5 — src/com.example.app/module-info.java

```java
module com.example.app {
    requires com.example.greeter;
}
```

Ứng dụng chỉ khai báo phụ thuộc vào module thư viện, rồi dùng các type từ package exported.

### Tệp 6 — src/com.example.app/com/example/app/Main.java

```java
package com.example.app;

import com.example.greeter.api.Greeter;
import com.example.greeter.api.Greeters;
import java.lang.reflect.Method;

public final class Main {
    public static void main(String[] args)
            throws ReflectiveOperationException {

        Greeter service = Greeters.create();
        System.out.println(service.greet("Java"));

        Class<?> apiType = Greeter.class;
        Module owner = apiType.getModule();
        Module caller = Main.class.getModule();

        System.out.println("API type: " + apiType.getName());
        System.out.println("API module: " + owner.getName());
        System.out.println("API exported: "
                + owner.isExported("com.example.greeter.api", caller));
        System.out.println("Internal exported: "
                + owner.isExported("com.example.greeter.internal", caller));

        Method method = apiType.getMethod("greet", String.class);
        Object output = method.invoke(service, "Reflection");
        System.out.println("Reflection: " + output);
    }
}
```

### Biên dịch và chạy

Tại thư mục cha chứa `src`, chạy bằng JDK 17+:

```bash
javac --module-source-path src -d out -m com.example.greeter,com.example.app
java --module-path out -m com.example.app/com.example.app.Main
```

Kết quả:

```text
Hello, Java
API type: com.example.greeter.api.Greeter
API module: com.example.greeter
API exported: true
Internal exported: false
Reflection: Hello, Reflection
```

**Tại sao Reflection vẫn gọi được?** `getMethod("greet", String.class)` tìm ra method public trên **interface `Greeter` thuộc package đã export**. Reflection gọi qua hợp đồng công khai; JVM dispatch đến implementation thật, dù implementation nằm trong package ẩn. Đây là một ví dụ đóng gói đúng cách: bên gọi không cần nhập hoặc thao tác Reflection sâu với `DefaultGreeter`.

Nếu thử thêm vào `Main.java`:

```java
import com.example.greeter.internal.DefaultGreeter;
```

compiler từ chối import package không export trong module `com.example.greeter`. Dù `DefaultGreeter` có modifier `public`, module `com.example.app` không được quyền phụ thuộc trực tiếp vào package này.

**Lưu ý:** mã ví dụ cung cấp đầy đủ các tệp cho modular compilation. Nó cần đúng cấu trúc thư mục và tên module. Cú pháp `--module-source-path`, `-m` và `--module-path` thuộc công cụ JDK, không phụ thuộc shell-specific command substitution.

## 4. Reflection không vượt được đóng gói của JPMS

Những thao tác dưới đây có ý nghĩa khác nhau:

```java
Class<?> type = Class.forName("java.lang.String");
java.lang.reflect.Method method =
        type.getMethod("substring", int.class);
```

`String` nằm trong `java.lang` là package public/exported của `java.base`, nên phương thức public có thể được dùng bằng API thông thường hoặc Reflection theo kiểm tra truy cập.

Còn đây là **mã minh họa kiểu truy cập không nên xây thành tính năng**:

```java
java.lang.reflect.Field internal =
        String.class.getDeclaredField("value");
internal.setAccessible(true);
```

Trên nhiều bản JDK 17+, `String` có field private tên `value`, nhưng **đừng phụ thuộc vào tên hoặc cấu trúc lưu trữ đó**: một JDK khác hoặc phiên bản sau có thể thay đổi. `getDeclaredField` có thể trả metadata, nhưng `setAccessible(true)` có thể ném `java.lang.reflect.InaccessibleObjectException` vì `java.base` không mở `java.lang` cho module gọi. Nếu field không còn tồn tại, có thể gặp `NoSuchFieldException` từ bước tìm metadata.

Hãy chọn API public phù hợp mục đích:

```java
String text = "hello";
char first = text.charAt(0);
int length = text.length();
byte[] utf8 = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
```

Các API trên biểu đạt **ý định** (ký tự, độ dài, encoding), không buộc code phải hiểu storage private của `String`.

### Các lỗi/khả năng cần phân biệt

| Hiện tượng | Nguyên nhân phổ biến | Cách xử lý hợp lý |
| --- | --- | --- |
| Compile error báo package không visible/exported | Package thuộc named module nhưng không được export cho code gọi | Dùng API xuất khẩu chính thức; điều chỉnh module descriptor nếu package thuộc dự án mình và việc export có chủ đích |
| `InaccessibleObjectException` khi `setAccessible(true)` | JPMS từ chối bật truy cập sâu tới package không open | Tránh đọc private JDK fields; với package của chính mình, `opens ... to ...` nếu có lý do |
| `IllegalAccessException` khi `Method.invoke` hoặc `Field.get` | Access check của thành viên/kiểu không thỏa | Dùng public API hợp lệ; đảm bảo receiver, quyền truy cập và module tương ứng |
| `ClassNotFoundException`/`NoSuchMethodException` | Tên class/method hoặc phiên bản triển khai khác với giả định | Không ghim tên internal; dùng API documented và cơ chế phát hiện capability rõ ràng |
| `Class.forName(name)` thành công nhưng invoke thất bại | Class loading và quyền truy cập member là hai bước khác nhau | Không coi việc tải class là “đã được phép sử dụng API” |

`AccessibleObject.trySetAccessible()` (Java 9+) trả về `false` khi không bật truy cập được, thay vì luôn ném exception cho thất bại quyền truy cập; các lỗi khác vẫn có thể xảy ra. Dùng nó để viết code chẩn đoán dễ kiểm soát lỗi, **không phải** để thăm dò rồi cưỡng ép encapsulation của JDK.

## 5. API thay thế phù hợp cho những nhu cầu thường gặp

| Nhu cầu thực tế | Ví dụ phụ thuộc không nên mặc định dùng | Hướng public API nên xem xét |
| --- | --- | --- |
| Mã hóa/giải mã Base64 | Bộ giải mã Base64 nội bộ JDK đời cũ | `java.util.Base64` |
| Atomic update hoặc truy cập biến có memory semantics cụ thể | `sun.misc.Unsafe` chỉ để CAS/read/write field | `java.util.concurrent.atomic` hoặc `java.lang.invoke.VarHandle` khi phù hợp |
| Gọi method/constructor, khám phá metadata | Hack quyền truy cập với `sun.reflect.*` | `java.lang.reflect`, `java.lang.invoke.MethodHandles`, constructor/factory public |
| Tính toán mật mã, chữ ký và secure random | Implementation trực tiếp dưới `sun.security.*` | `java.security`, `javax.crypto`, nhà cung cấp thuật toán qua chuẩn JCA/JCE |
| Truy cập thông tin JVM và môi trường | Đọc field private của lớp runtime JDK | `Runtime`, `ManagementFactory`, `java.lang.management` theo dữ liệu cần |
| Xử lý nội dung `String` | Đọc private `String.value` | `String.charAt`, `codePoints`, `getBytes(Charset)`, `length` |
| Cơ chế tải file, tài nguyên và module | Can thiệp cấu trúc nội bộ class loader | `ClassLoader.getResource*`, `Class.getResource*`, `Module`, `ServiceLoader` theo mục tiêu |

Bảng là **định hướng theo mục đích**, không khẳng định từng API thay thế hoàn toàn mọi khả năng của internal API. Chẳng hạn `sun.misc.Unsafe` có nhiều nhóm tính năng khác nhau; cần phân loại việc sử dụng thực tế để chọn `VarHandle`, atomic classes, API foreign function/memory được hỗ trợ trên JDK mục tiêu, hoặc thiết kế lại.

Tương tự, `MethodHandles.privateLookupIn` là API public nhưng **không phải đường vòng qua quyền module**: để tạo private lookup cho lớp ở module khác, các điều kiện module/open package vẫn phải được đáp ứng. Không thiết kế thư viện bằng giả định lookup có thể truy cập mọi private JDK class.

## 6. Hai cờ khởi chạy thường gây hiểu nhầm: add-exports và add-opens

JDK có các cờ tương thích/chẩn đoán được dùng trong một số hệ thống cũ:

```bash
# Ví dụ dạng cú pháp, không phải cấu hình khuyến nghị lâu dài:
java --add-exports java.base/jdk.internal.misc=ALL-UNNAMED -cp app.jar com.example.Main

java --add-opens java.base/java.lang=ALL-UNNAMED -cp app.jar com.example.Main
```

- `--add-exports module/package=target` mở quyền truy cập package ở mức **API public** đối với target được chỉ định. Nó thường được dùng tạm khi code cần import/link tới public types trong package vốn không export.
- `--add-opens module/package=target` mở package đối với **Reflection sâu** ở runtime, ví dụ cho phép framework cũ thử truy cập private members. Cờ này không biến private member thành public compile-time API.
- `ALL-UNNAMED` áp dụng cho các unnamed modules (thường là code chạy trên classpath). Với named module, target nên là **tên module cụ thể**. Việc mở quyền có thể cần cấu hình tương ứng cả compile time và runtime, tùy trường hợp.
- Cờ mở quyền **không sửa hợp đồng API** và không bảo đảm internal class/method vẫn tồn tại hoặc giữ semantics sau nâng JDK.
- Với Java 17, cơ chế nới truy cập tổng quát `--illegal-access` của thời Java 9–16 đã trở nên lỗi thời/không còn hiệu lực nới encapsulation. Không dùng nó làm giải pháp cho môi trường Java 17+.

Một rule thiết kế thực dụng: nếu hệ thống chỉ chạy được khi mở `java.base/java.lang` để đọc field private của `String`, hãy ghi nhận đó là **nợ tương thích** và thay code bằng API public trước khi coi quá trình migration hoàn tất.

Nếu framework thật sự cần Reflection vào **class do bạn sở hữu**, khai báo module có chủ đích:

```java
module com.example.domain {
    exports com.example.domain.api;
    opens com.example.domain.model to com.example.serializer;
}
```

Đây là ví dụ module descriptor **minh họa**; module `com.example.serializer` phải hiện diện trong đồ thị module khi build/run tương ứng. Nó bảo vệ hợp đồng API xuất khẩu, đồng thời chỉ trao quyền Reflection sâu vào package model cho serializer được nêu. Không nên “mở toàn bộ” chỉ để tránh phải xác định package thật sự cần truy cập.

## 7. Phát hiện phụ thuộc nội bộ JDK trước khi nâng phiên bản

Một bước quan trọng là xác định phụ thuộc theo **cả hai hướng tĩnh và động**.

### Quét tĩnh bằng jdeps

Trên JDK có công cụ `jdeps`, quét file JAR ứng dụng:

```bash
jdeps --jdk-internals app.jar
```

Tùy cấu trúc ứng dụng, bổ sung classpath/module-path cho các thư viện phụ thuộc để `jdeps` phân tích chính xác hơn. Với multi-release JAR, có thể chỉ định phiên bản mục tiêu, ví dụ `--multi-release 17`. `jdeps --jdk-internals` báo các phụ thuộc tĩnh đáng chú ý tới JDK internal API và trong nhiều trường hợp gợi ý API công khai thay thế.

**Giới hạn:** `jdeps` phân tích bytecode và phụ thuộc có thể quan sát tĩnh, nên có thể **bỏ sót truy cập xây tên class bằng chuỗi và Reflection**, code sinh động, plugin nạp về sau hoặc native integration. Không coi kết quả `jdeps` rỗng là bằng chứng toàn bộ đường chạy không dùng internals.

### Rà soát Reflection và cấu hình runtime

Tìm trong chính code/cấu hình dự án:

- Chuỗi tên package `sun.`, `jdk.internal.`, `sun.reflect.`, cùng những lời gọi `Class.forName`/`getDeclared*`/`setAccessible` tới class của JDK.
- Các VM flags `--add-opens`, `--add-exports`; xác định nơi nào đang cần chúng và mục tiêu cụ thể.
- Thư viện JSON, ORM, DI, bytecode agent, proxy hoặc plugin cũ: chúng có thể dùng Reflection sâu trong code bên thứ ba dù code ứng dụng không gọi trực tiếp.
- Các kịch bản chạy quan trọng: module-path so với classpath, test, dev, CI và môi trường production có thể có tập flags khác nhau.

Khi tìm thấy dependency, hãy ghi rõ **mục đích nghiệp vụ**, API nội bộ cụ thể, thay thế public và mức độ rủi ro nâng phiên bản. Đây là cách tránh “chữa” mọi chỗ bằng cùng một flag rồi bỏ sót vấn đề thiết kế.

## 8. Thiết kế API bền vững cho thư viện Java của chính bạn

Các nguyên tắc giúp lớp nội bộ có thể thay đổi mà ứng dụng sử dụng không phải sửa theo:

1. **Định nghĩa hợp đồng trước:** export package `api` chứa interface, factory, record/DTO hoặc class ổn định. Chỉ trả về kiểu có thể truy cập từ bên gọi. Tránh để public method trả về kiểu nội bộ mà consumer không thể gọi tên hoặc xử lý đúng.
2. **Ẩn implementation thực tế:** package `internal`/`impl` không export; khởi tạo qua factory hoặc dependency injection công khai. Đừng hướng dẫn consumer `Class.forName("...internal...")`.
3. **Chỉ mở Reflection tối thiểu:** package nào cần serializer/DI đọc private fields thì cân nhắc `opens ... to ...` cho đúng module. Nếu có thể thiết kế constructor, accessor hoặc adapter public thay cho deep reflection thì API sẽ dễ bảo trì hơn.
4. **Không xuất khẩu ngẫu nhiên package nội bộ chỉ để sửa lỗi biên dịch:** việc thêm `exports` sẽ biến chi tiết implementation thành thứ consumer có thể phụ thuộc. Mọi lần tái cấu trúc package này về sau có thể gây lỗi.
5. **Dùng `ServiceLoader` khi cần plugin/extensibility:** nhà cung cấp có thể được cấu hình bằng `provides ... with ...`, còn bên tiêu thụ dùng `uses ...` và interface service phù hợp. Không bắt caller nhận biết implementation private bằng tên class cố định.
6. **Giữ API tương thích có chủ đích:** khi thay signature, kiểu trả về, yêu cầu module hoặc hành vi được tài liệu hóa, đánh giá tương thích nguồn/nhị phân và kiểm thử consumer. Từ “public” một mình không bảo đảm tương thích.
7. **Đặt chính sách hỗ trợ rõ:** chỉ dựa vào API công khai của phiên bản Java tối thiểu đã công bố; tránh ngầm yêu cầu phiên bản mới hơn qua reflection vào class không có trong baseline.

Trong ví dụ ở mục 3, nếu `DefaultGreeter` chuyển từ tạo chuỗi trực tiếp sang dùng tài nguyên cấu hình, caller vẫn chỉ nhìn thấy `Greeter.greet(String)`. Đây là lợi ích của abstraction kết hợp encapsulation JPMS.

## 9. Migration theo từng bước: từ implementation detail sang hợp đồng

Giả sử một codebase đang dùng Reflection để lấy trực tiếp backing array của `String` rồi xử lý byte. Khi nâng JDK, thao tác này có thể thất bại do field/private layout thay đổi hoặc do `java.base/java.lang` không open.

Quy trình:

1. **Phân tích ý định:** cần duyệt ký tự, Unicode code point, UTF-8 bytes hay chỉ độ dài? Đó là các yêu cầu khác nhau, nên không thể thay tất cả bằng cùng một API.
2. **Chọn API công khai:** `charAt` duyệt UTF-16 code units; `codePoints` làm việc theo Unicode code points; `getBytes(StandardCharsets.UTF_8)` tạo mảng byte UTF-8; `length` cho số UTF-16 code units. Không lẫn code point count với `length()`.
3. **Thay phụ thuộc nội bộ và loại bỏ mở quyền không cần thiết:** xóa reflection hack, cờ `--add-opens` nếu không còn caller nào cần.
4. **Kiểm chứng đúng hành vi:** thêm test với ký tự ngoài BMP, null input nếu nghiệp vụ có, encoding và hiệu năng thực tế nếu đây là đường nóng. Không giả định backing array nội bộ có encoding hoặc bố cục cố định.
5. **Kiểm chứng môi trường deployment:** chạy trên JDK tối thiểu đã hỗ trợ, thử module-path khi module hóa, chạy toàn bộ luồng tích hợp có plugin/agent.
6. **Giám sát tương thích:** dùng `jdeps --jdk-internals` trong quy trình kiểm tra bản build khi phù hợp và review các VM flags mới được thêm.

Với một dependency bên thứ ba bắt buộc dùng internals, ưu tiên nâng lên phiên bản thư viện có hỗ trợ JDK mục tiêu hoặc thay thư viện. Cờ mở module chỉ nên là **biện pháp tạm có phạm vi và kế hoạch gỡ**, nếu vận hành thật sự đòi hỏi.

## 10. Lỗi thường gặp

| Sai lầm | Vì sao nguy hiểm | Cách đúng |
| --- | --- | --- |
| “Class `public` nghĩa là public API” | JPMS còn kiểm tra export; API có thể chỉ là implementation detail | Xem tài liệu hỗ trợ, module sở hữu, package export và mục đích API |
| “`Class.forName` thành công nghĩa là gọi được mọi method” | Class loading tách biệt với quyền access/reflection | Chỉ gọi public method trên API được export hoặc mở quyền có chủ đích cho code mình sở hữu |
| Dùng `setAccessible(true)` cho `java.base` rồi cho rằng nó luôn hoạt động | Strong encapsulation có thể gây `InaccessibleObjectException` | Dùng `String`/collections/JCA/JDK APIs public thay cho private fields |
| Dùng `--add-opens` để sửa lỗi import package không export | Opens chỉ phục vụ reflection runtime, không cấp quyền compile-time import | Thiết kế exported public API; phân biệt với `--add-exports` |
| Thêm `--add-exports` vĩnh viễn | Vẫn khóa chặt ứng dụng vào package nội bộ | Di chuyển sang supported API, giới hạn workaround |
| Tự động thay `Unsafe` bằng một API bất kỳ | Những tính năng khác nhau đòi hỏi giải pháp khác nhau | Phân loại thao tác, chọn `VarHandle`, atomic API hoặc giải pháp phù hợp |
| Coi tên `com.sun.*` đều nội bộ | Một số API dạng này được JDK công bố và tài liệu hóa | Đánh giá theo module/tài liệu, không theo tiền tố đơn thuần |
| Chỉ chạy `jdeps` rồi tuyên bố không phụ thuộc internal API | Reflection động và native có thể không hiện trong static dependency | Kết hợp code review, cấu hình, integration test và jdeps |
| Exports toàn bộ `impl` vì consumer muốn import một class | Đưa implementation thành phụ thuộc của client | Dùng factory/service interface/DTO trong package `api` |
| Dùng `opens` rộng toàn module vì framework | Nới quyền Reflection quá mức cần thiết | Ưu tiên mở package cụ thể, qualified `opens ... to ...` nếu phù hợp |

## 11. Câu hỏi tự kiểm tra

**Câu 1.** `public class DefaultGreeter` ở package không export có thể import từ named module khác chỉ vì class có modifier `public` không?

**Trả lời:** Không. JPMS kiểm tra quyền export của package đối với module gọi; modifier `public` không đủ.

**Câu 2.** `exports package` khác `opens package` ở đâu?

**Trả lời:** `exports` cung cấp quyền truy cập API public từ các module đọc được module sở hữu; `opens` cho phép Reflection sâu tại runtime theo phạm vi mở. `opens` một mình không cấp quyền import thông thường ở compile time.

**Câu 3.** Trong ví dụ `Greeter`, vì sao `method.invoke(service, "Reflection")` hoạt động khi implementation không export?

**Trả lời:** `Method` là thành viên public thuộc interface `Greeter` ở package API được export. JVM dispatch lời gọi instance đến implementation ẩn bên trong module, nên không cần mở package implementation cho caller.

**Câu 4.** `--add-opens java.base/java.lang=ALL-UNNAMED` có biến việc đọc private field `String` thành thiết kế API tốt không?

**Trả lời:** Không. Nó chỉ nới quyền Reflection cho target được chỉ định, không biến representation private thành hợp đồng public ổn định.

**Câu 5.** `jdeps --jdk-internals` không báo gì có nghĩa ứng dụng không dùng internals?

**Trả lời:** Không chắc. Mã có thể tạo tên class ở runtime, gọi Reflection theo chuỗi, nạp plugin hay thực hiện thao tác native ngoài phạm vi phân tích phụ thuộc tĩnh.

**Câu 6.** Có thể dùng `Modifier.isPublic(type.getModifiers())` làm tiêu chí duy nhất để xác nhận “API được hỗ trợ” không?

**Trả lời:** Không. Cần xem module, quyền export, tài liệu, phiên bản Java mục tiêu và chính sách tương thích.

**Ghi nhớ:** Reflection là công cụ khám phá/call metadata trong giới hạn truy cập Java, còn **API công khai** là ranh giới hợp đồng của hệ thống. Hãy dùng supported JDK APIs, export đúng package API của mình, giữ implementation không export và mở Reflection sâu chỉ khi thật sự có nhu cầu hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)
