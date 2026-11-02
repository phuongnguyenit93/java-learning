<a id="back-to-top"></a>

# 21. An toàn mã Java

## Menu
- [21.1 Giới hạn bảo vệ của type safety, bytecode verification và đóng gói bằng module](#java-secure-coding-001)
- [21.2 Kiểm tra dữ liệu tại ranh giới tin cậy trước khi cấp phát hoặc thực hiện thao tác](#java-secure-coding-002)
- [21.3 Chống injection bằng API có tham số và tách dữ liệu khỏi lệnh hoặc biểu thức](#java-secure-coding-003)
- [21.4 Đường dẫn, tệp tạm, symlink và điều kiện tranh chấp khi truy cập tài nguyên](#java-secure-coding-004)
- [21.5 Giới hạn kích thước, độ sâu, thời gian và tài nguyên để tránh từ chối dịch vụ](#java-secure-coding-005)
- [21.6 Defensive copy, dữ liệu thay đổi được và việc để lộ tham chiếu có quyền](#java-secure-coding-006)
- [21.7 Deserialization, ObjectInputFilter và giới hạn của danh sách lớp được phép](#java-secure-coding-007)
- [21.8 Cấu hình JAXP secure processing và kiểm soát truy cập tài nguyên XML bên ngoài](#java-secure-coding-008)
- [21.9 SecurityManager, Policy, Permission và AccessController trong mã cũ đã deprecated](#java-secure-coding-009)
- [21.10 Cách ly tiến trình, quyền hệ điều hành và bảo vệ bí mật khi xử lý mã không tin cậy](#java-secure-coding-010)

## <a id="java-secure-coding-001">21.1 Giới hạn bảo vệ của type safety, bytecode verification và đóng gói bằng module</a>

<details>
<summary>Xem chi tiết</summary>

# 21.1. Giới hạn bảo vệ của type safety, bytecode verification và đóng gói bằng module

> **Mục tiêu:** Hiểu ba lớp bảo vệ khác nhau của Java — kiểm tra kiểu dữ liệu, xác minh bytecode và đóng gói qua Java Platform Module System (JPMS) — cùng giới hạn bảo mật thực tế của từng lớp. Sau bài này, bạn có thể giải thích vì sao một tệp `.class` hợp lệ hoặc một thư viện nằm trong module riêng **không** đồng nghĩa thư viện đó an toàn hay được chạy trong sandbox.

## 1. Đặt đúng mô hình đe dọa

Java đưa ra các cơ chế giúp chương trình không tùy tiện phá vỡ hệ thống kiểu và quy tắc truy cập của ngôn ngữ. Tuy nhiên, cần phân biệt:

- **An toàn kiểu (type safety):** hạn chế diễn giải dữ liệu của kiểu này như dữ liệu của kiểu không tương thích; kết hợp kiểm tra lúc biên dịch và lúc chạy.
- **Bytecode verification:** JVM kiểm tra các ràng buộc cấu trúc và kiểu của `.class` trước khi thực thi phần bytecode liên quan, kể cả khi tệp không được tạo bằng `javac`.
- **Đóng gói module (JPMS):** module quy định dependency nào được đọc, package nào là API công khai, package nào được mở cho reflection.
- **Sandbox/ủy quyền tài nguyên:** quyết định một đoạn code có được đọc tệp, gọi mạng, sinh tiến trình, lấy bí mật, sử dụng CPU/bộ nhớ hoặc truy cập dữ liệu của tenant khác hay không. Ba cơ chế trên **không tự thiết lập ranh giới này**.

Một ví dụ dễ nhầm: thư viện tải từ Internet có thể chứa bytecode hợp lệ, không vi phạm type safety và tuân thủ module boundaries, nhưng vẫn gọi `Files.readString` hoặc `HttpClient.send` bằng chính quyền của tiến trình chủ. Không lớp nào trong ba lớp trên mặc nhiên từ chối những lời gọi hợp lệ đó.

| Câu hỏi | Cơ chế liên quan | Không suy ra được |
| --- | --- | --- |
| `String` có thể bị đối xử như `Integer` mà bỏ qua kiểm tra kiểu không? | Type system + runtime casts | Dữ liệu đầu vào đã đáng tin, không có bug nghiệp vụ |
| Bytecode cố làm tràn operand stack hoặc gọi method với sai kiểu có được chấp nhận không? | Bytecode verifier | Bytecode không độc hại hoặc đã được ký bởi tác giả đáng tin |
| Một module khác có được import class `public` nằm trong package nội bộ không? | `exports`, `requires`, kiểm tra access | Module nội bộ không thể đọc file/network hoặc không thể bị điều khiển qua API |
| Có thể chạy code của người dùng không tin cậy trong cùng JVM không? | Cần thiết kế cô lập riêng | JPMS/class loader/verifier tự tạo sandbox |

## 2. Type safety thực sự bảo vệ điều gì?

Java có kiểu tĩnh: compiler từ chối nhiều phép gán, lời gọi method và phép chuyển kiểu không hợp lệ. Nhưng code vẫn có các kiểm tra quan trọng lúc chạy:

- **Ép kiểu tham chiếu:** phép cast không phù hợp dẫn đến `ClassCastException`, thay vì biến một object thành object của kiểu tùy ý.
- **Mảng tham chiếu:** mảng có thông tin kiểu phần tử lúc chạy; cố gán sai loại có thể dẫn đến `ArrayStoreException`.
- **Truy cập mảng:** kiểm tra chỉ số biên; truy cập sai gây `ArrayIndexOutOfBoundsException` thay vì truy cập byte nhớ tùy ý.
- **Tham chiếu null:** nhiều thao tác dereference gây `NullPointerException` thay vì biến `null` thành con trỏ thô.
- **Khởi tạo và truy cập field/method:** compiler cùng JVM tuân thủ nhiều ràng buộc kiểu và access; không phải mọi bytecode đều có thể bỏ qua chúng.

Ví dụ độc lập cho Java 17+. Tạo **`TypeSafetyDemo.java`**:

```java
public final class TypeSafetyDemo {
    public static void main(String[] args) {
        Object text = "Java";
        try {
            Integer number = (Integer) text;
            System.out.println(number);
        } catch (ClassCastException ex) {
            System.out.println("cast=blocked");
        }

        Object[] words = new String[1];
        try {
            words[0] = Integer.valueOf(42);
        } catch (ArrayStoreException ex) {
            System.out.println("array-store=blocked");
        }

        System.out.println("program=continues");
    }
}
```

Biên dịch và chạy:

```bash
javac TypeSafetyDemo.java
java TypeSafetyDemo
```

Kết quả chuẩn:

```text
cast=blocked
array-store=blocked
program=continues
```

Ở ví dụ này, JVM **từ chối thao tác không tương thích kiểu**. Nó không đánh giá rằng `"Java"` là dữ liệu đầu vào hợp lệ cho mọi nghiệp vụ, hay tác giả code có quyền đọc dữ liệu bí mật. Chương trình vẫn tiếp tục vì hai exception đã được bắt.

### 2.1. Giới hạn của generic và type erasure

`List<String>` giúp compiler bắt nhiều lỗi, nhưng tham số kiểu generic thông thường bị **erasure** trong bytecode. Khi trộn raw types, unchecked casts hoặc các API thiếu type information, bạn có thể tạo *heap pollution* và gặp `ClassCastException` ở bước đọc dữ liệu sau đó. Đừng hiểu `List<String>` như một container có cơ chế bảo mật riêng tại runtime.

Phân biệt ba khái niệm:

- **Type-safe**: các thao tác được kiểm tra theo quy tắc kiểu, với các giới hạn đã biết.
- **Memory-safe trong phạm vi managed Java**: mã Java thông thường không được thực hiện phép toán địa chỉ tùy ý như con trỏ thô C/C++; JVM vẫn có thể phát sinh lỗi tài nguyên và phụ thuộc vào tính đúng đắn của runtime.
- **Secure**: chương trình không tiết lộ dữ liệu, không vượt quyền và xử lý đúng mục tiêu bảo mật trước đầu vào hoặc dependency độc hại.

Hai tính chất đầu hỗ trợ tính chất thứ ba, nhưng **không đủ để chứng minh tính chất thứ ba**. Code kiểu hợp lệ vẫn có thể chứa SQL injection, path traversal, lỗi logic phân quyền, side channel, race condition, denial of service hoặc dùng sai thuật toán mật mã.

### 2.2. Các đường đi nằm ngoài giả định Java thuần

JNI và thư viện native hoạt động dưới các quy tắc của native code, có thể can thiệp bộ nhớ và tài nguyên tiến trình. Các API truy cập native/foreign memory hoặc các API nội bộ như `sun.misc.Unsafe` (nếu có quyền sử dụng) cũng cần được đánh giá riêng; không được suy rằng compiler và verifier của Java kiểm soát được hành vi máy native. Ngay cả mã managed bình thường cũng được phép gọi API I/O hoặc tạo luồng nếu quyền hệ điều hành của tiến trình cho phép.

## 3. Bytecode verifier bảo vệ điều gì?

JVM có thể nạp `.class` từ JAR, class loader tùy biến, code generation hoặc các nguồn không qua `javac`. Vì vậy, kiểm tra lúc biên dịch nguồn Java **không thể là lớp bảo vệ duy nhất**. Theo Java Virtual Machine Specification (JVMS), verification là một phần của quá trình *linking*.

Ở mức khái quát, verifier kiểm tra:

1. **Class-file format và cấu trúc hợp lệ:** constant pool, descriptor, cờ truy cập và các ràng buộc cấu trúc áp dụng.
2. **Kiểu trên operand stack/local variables:** instruction cần những kiểu đầu vào phù hợp; không tự tạo stack underflow/overflow trong bytecode hợp lệ.
3. **Luồng điều khiển:** nhánh phải trỏ đến instruction hợp lệ và trạng thái kiểu phải tương thích tại các điểm hợp luồng.
4. **Khởi tạo đối tượng:** ràng buộc về giá trị chưa được khởi tạo và việc gọi constructor.
5. **StackMapTable/type checking:** các định dạng class file hiện đại sử dụng thông tin frame để kiểm tra tính nhất quán của các trạng thái kiểu theo specification.

Khi bytecode vi phạm yêu cầu verification, JVM có thể ném `java.lang.VerifyError` tại thời điểm xác minh class. **Đó không phải tên chung cho mọi lỗi nạp class.** Một số lỗi khác xảy ra ở resolution hoặc runtime, ví dụ `NoClassDefFoundError`, `IllegalAccessError` hay `ClassCastException`.

```text
Nguồn Java hoặc công cụ tạo bytecode
                 |
                 v
           Tệp .class
                 |
                 v
     JVM: loading -> linking
                    | verification
                    | preparation
                    | resolution (có thể trì hoãn)
                 |
                 v
       Chạy instruction hợp lệ
                 |
                 v
      Runtime checks + API calls
                 |
                 v
     Quyền thực tế của tiến trình OS
```

**Verification là kiểm tra tính hợp lệ của bytecode, không phải kiểm toán ý định.** Ví dụ, một class gọi `Files.delete` với đường dẫn do kẻ tấn công kiểm soát có thể được xác minh hoàn toàn thành công nếu các kiểu, stack và truy cập bytecode hợp lệ. Verifier không chứng minh rằng lời gọi đó được người dùng cấp quyền.

### 3.1. Những điều verifier không làm

- Không chứng minh chương trình không có lỗi logic, deadlock, race, vòng lặp vô tận hoặc rò rỉ tài nguyên.
- Không xác minh chữ ký JAR, danh tính nhà xuất bản, provenance hay độ tin cậy của class file. **Integrity/authenticity** của artifact là kiểm tra khác.
- Không tự hạn chế truy cập filesystem/network, câu lệnh SQL, môi trường, token truy cập hoặc API của hệ thống.
- Không phân tích tính chính xác của chính sách xác thực/ủy quyền nghiệp vụ.
- Không ngăn code dùng các khả năng mà runtime/OS đã cấp, kể cả gọi JNI/native nếu được phép.

Một class loader riêng vẫn phải vận hành theo quy tắc JVM khi định nghĩa class thông thường. **Class loader isolation** hữu ích cho namespace và vòng đời thư viện; nó không tương đương phân tách tiến trình hoặc chặn I/O. Tương tự, `java -Xverify:all` trên HotSpot giúp yêu cầu kiểm tra bytecode rộng hơn trong một số bối cảnh, nhưng **không phải lệnh bật sandbox**.

## 4. JPMS: đọc module, export package và open để reflection

Từ Java 9, Java Platform Module System định nghĩa module bằng `module-info.java`. Các quy tắc chính:

| Cơ chế | Tác dụng | Giới hạn |
| --- | --- | --- |
| `requires m;` | Module khai báo phụ thuộc và có quan hệ *readability* với `m`. | Đọc module không đồng nghĩa truy cập mọi package của nó. |
| `exports p;` | Cho các module đọc được truy cập trực tiếp đến **public types/public members** đủ điều kiện của package `p`. | Không cho phép deep reflection mặc định vào private fields. |
| Không `exports p` | Dù type ghi `public`, bên ngoài module không được sử dụng trực tiếp như API thông thường. | Class vẫn có thể tồn tại/được tìm thấy; không phải cơ chế giấu byte hoặc mã hóa. |
| `exports p to a,b;` | Chỉ export package cho các module được chỉ định. | Không tạo quyền tài nguyên hay trust level cho `a,b`. |
| `opens p;` | Cho phép deep reflection lúc chạy với package `p`. | Không tự làm package thành API import trực tiếp khi biên dịch. |
| `opens p to framework;` | Chỉ mở package cho module reflector được nêu tên. | Framework được mở có thể truy cập private members; phải đánh giá phạm vi tin cậy. |
| `open module m { ... }` | Mở các package của cả module cho reflection. | Mở rộng bề mặt introspection so với mở package cần thiết. |

**Ba kiểm tra thường gặp:**

1. **Đọc được module chưa?** Quan hệ readability được hình thành từ module graph (và có ngoại lệ runtime).
2. **Package đã export chưa?** Khi code từ module khác tham chiếu trực tiếp public API.
3. **Package đã open chưa?** Khi reflection muốn truy cập sâu các member không public.

`public`, `private` và `protected` vẫn có vai trò của chúng; JPMS bổ sung ranh giới **package/module**. Một type `public` trong package không export vẫn không trở thành public API cho module khác.

### 4.1. Strong encapsulation có thể được nới bằng cấu hình

Java launcher cung cấp, trong các tình huống tương thích cần thiết:

- `--add-exports source.module/package=target.module`: mở quyền truy cập **public types/members** của package cho module đích. Khi dùng tại compile time để phụ thuộc internals, thường phải cấu hình tương ứng lúc chạy.
- `--add-opens source.module/package=target.module`: cho phép **deep reflection** trên package đó ở runtime, kể cả các member không public, theo giới hạn Java reflection.
- `ALL-UNNAMED`: đích đặc biệt chỉ mọi unnamed module, thường là code từ class path. Nên tránh nới toàn bộ class path khi không thật sự cần.

Các cơ chế runtime khác như instrumentation/agent hoặc cấu hình module layer cũng có thể thay đổi tình trạng truy cập theo quyền mà môi trường chạy cấp. Một ứng dụng có quyền lựa chọn launcher flags/agents không bị “khóa lại” chỉ vì một tác giả khác dùng `module-info.java`.

Lưu ý về JDK: từ **JDK 17**, phần lớn internal API của JDK được strongly encapsulated theo JEP 403. Một số critical internal APIs vẫn được cung cấp qua `jdk.unsupported` cho mục đích tương thích; **không nên coi việc còn truy cập được API nội bộ là cam kết an toàn hay ổn định**.

## 5. Thực hành Java 17+: cùng một module, hai kết quả reflection

Ví dụ gồm hai *named modules*: `com.example.vault` cung cấp API công khai, còn package `com.example.vault.internal` không được export/open; `com.example.app` là bên dùng. Đoạn code minh họa **ranh giới truy cập Java**, không được dùng làm nơi cất bí mật thật.

Tạo cây thư mục:

```text
src/
├── com.example.vault/
│   ├── module-info.java
│   └── com/example/vault/
│       ├── api/VaultApi.java
│       └── internal/InternalPolicy.java
└── com.example.app/
    ├── module-info.java
    └── com/example/app/Main.java
```

**`src/com.example.vault/module-info.java`**

```java
module com.example.vault {
    exports com.example.vault.api;
}
```

**`src/com.example.vault/com/example/vault/api/VaultApi.java`**

```java
package com.example.vault.api;

public final class VaultApi {
    private VaultApi() { }

    public static String status() {
        return "ready";
    }
}
```

**`src/com.example.vault/com/example/vault/internal/InternalPolicy.java`**

```java
package com.example.vault.internal;

public final class InternalPolicy {
    private static final String DETAIL = "implementation";

    private InternalPolicy() { }
}
```

**`src/com.example.app/module-info.java`**

```java
module com.example.app {
    requires com.example.vault;
}
```

**`src/com.example.app/com/example/app/Main.java`**

```java
package com.example.app;

import com.example.vault.api.VaultApi;
import java.lang.reflect.Field;

public final class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("api=" + VaultApi.status());

        Class<?> internal = Class.forName(
            "com.example.vault.internal.InternalPolicy"
        );
        Field field = internal.getDeclaredField("DETAIL");

        if (field.trySetAccessible()) {
            System.out.println("reflection=" + field.get(null));
        } else {
            System.out.println("reflection=blocked");
        }
    }
}
```

Từ thư mục chứa `src/`, biên dịch và chạy với JDK 17+:

```bash
javac --module-source-path src -d mods -m com.example.vault,com.example.app
java --module-path mods -m com.example.app/com.example.app.Main
```

**Stdout chính xác của lần chạy mặc định:**

```text
api=ready
reflection=blocked
```

Vì `VaultApi` thuộc package được `exports`, ứng dụng gọi được API. Ngược lại, `InternalPolicy` thuộc package không export/open: việc `Class.forName` tìm được class **không** có nghĩa reflection được đọc field private. `trySetAccessible()` trả `false` vì module không cho phép deep reflection.

Bây giờ **người vận hành tiến trình chủ động mở đúng một package cho đúng module**:

```bash
java --module-path mods \
  --add-opens=com.example.vault/com.example.vault.internal=com.example.app \
  -m com.example.app/com.example.app.Main
```

**Stdout chính xác khi có `--add-opens`:**

```text
api=ready
reflection=implementation
```

Chữ ký class, bytecode và kiểu dữ liệu **không thay đổi**. Chỉ chính sách truy cập khi khởi động tiến trình thay đổi, từ đó deep reflection thành công. Ví dụ cũng chứng minh **giới hạn của module encapsulation**: nó giúp tránh truy cập nội bộ vô ý hoặc không được cấu hình, không phải lớp bảo mật chống lại chính chủ tiến trình có quyền mở module.

Nếu thêm `import com.example.vault.internal.InternalPolicy;` vào `Main.java` rồi tham chiếu trực tiếp class, compiler sẽ báo package không được export tới `com.example.app`. Khi cần phản xạ trên một package cho framework hợp lệ, thường dùng `opens ... to <framework-module>` trong module descriptor; không nên export toàn bộ internals chỉ để phục vụ reflection.

### 5.1. Vì sao không dùng private field làm “vault” bí mật?

Ở trên, `DETAIL` chỉ là chuỗi demo. Không được lưu private key, token hoặc mật khẩu vào hằng `private static final` với kỳ vọng JPMS che giấu được: bytecode/JAR có thể được phân tích, runtime trong cùng tiến trình có nhiều cơ chế introspection, và người vận hành có thể cấu hình agent/reflection/native access. Bí mật thực tế cần nằm trong secret manager/OS keystore/quy trình cấp quyền phù hợp, với vòng đời, phân quyền và kiểm toán riêng.

## 6. Ranh giới giữa tính hợp lệ của code và quyền hệ thống

Xét một ứng dụng plugin có khả năng nạp JAR do bên thứ ba cung cấp:

```text
Bên thứ ba gửi plugin.jar
             |
             v
  [Kiểm tra nguồn + hash/chữ ký]   <-- quyết định tin artifact nào
             |
             v
   [JVM kiểm tra .class hợp lệ]    <-- bytecode verification
             |
             v
 [JPMS kiểm tra module access]    <-- exports/opens/reads
             |
             v
    [Plugin thực thi Java code]
             |
             v
  [API Files, HTTP, ProcessBuilder]
             |
             v
    [Quyền của JVM/OS process]
```

Verifier và module check **không tự tạo một nguyên tắc “plugin được làm X nhưng không được làm Y”**. Nếu plugin gọi API công khai như `Files.readString`, type system và verifier đều có thể chấp nhận. Với quyền OS phù hợp, lời gọi có thể thành công. Sự giới hạn thật sự phải đến từ **cách host thiết kế API, chỗ chạy plugin và quyền hệ điều hành**, kết hợp kiểm tra nguồn code.

| Tình huống | Ba lớp bảo vệ chặn được gì? | Kiểm soát cần bổ sung |
| --- | --- | --- |
| Plugin cung cấp bytecode có stack kiểu sai | Verifier từ chối class vi phạm; có thể phát sinh `VerifyError`. | Vẫn kiểm tra nguồn/artifact và log lỗi nạp. |
| Plugin có bytecode hợp lệ nhưng cố đọc file nhạy cảm bằng API Java | Type/verifier/JPMS không quyết định được quyền file đó. | Chạy bằng identity hạn chế, chỉ mount dữ liệu cần thiết, filesystem ACL. |
| Plugin mở nhiều kết nối mạng hoặc truyền dữ liệu ra ngoài | Module encapsulation không có network policy. | Network egress policy, xác thực/ủy quyền, rate limit, giám sát. |
| Plugin truy cập internal package của module khác | Có thể bị JPMS từ chối nếu package không export/open. | Tối thiểu hóa exports/opens, kiểm soát flags và agents. |
| Code có vòng lặp vô hạn, tạo quá nhiều threads hay cấp phát quá mức | Hoàn toàn có thể type-safe và verified. | Giới hạn CPU, memory, process/thread/time ở mức cô lập phù hợp. |
| Code gọi JNI/native, Unsafe hoặc FFM được cấp quyền | Có thể vượt ngoài giả định managed memory safety. | Loại bỏ native không cần thiết, hạn chế native access và cô lập tiến trình. |

### 6.1. Một JVM không tương đương một ranh giới tenant

Hai module có thể chia sẻ heap, thread, process credentials và tài nguyên runtime. JPMS không thực hiện phân quyền theo tenant hoặc theo user đã đăng nhập. Nếu hệ thống cần tách dữ liệu của khách hàng A/B, việc kiểm tra tenant/ownership phải nằm ở **service/repository/API authorization** và cơ sở dữ liệu; module boundaries không thay bạn viết các kiểm tra đó.

Custom `ClassLoader` cũng không tự biến một plugin thành sandbox: class loader cho phép tìm/nạp/định danh class khác nhau, nhưng code được nạp vẫn có thể gọi những Java API công khai mà tiến trình cho phép.

## 7. Security Manager hiện nay không phải phương án dựa vào

Trước đây, Java từng có **Security Manager** và permission policy cho một số mô hình chạy code ít tin cậy. Trạng thái phiên bản cần nhớ:

- **JDK 17–23:** Security Manager đã bị deprecated for removal (JEP 411); không nên thiết kế sandbox mới phụ thuộc vào nó.
- **Từ JDK 24:** Security Manager **bị vô hiệu hóa vĩnh viễn** (JEP 486). Không thể bật lại bằng launcher option hoặc `System.setSecurityManager`; một số API còn tồn tại nhưng hành vi đã thay đổi và có kế hoạch loại bỏ.
- **JPMS không thay thế Security Manager.** JPMS vẫn có vai trò quan trọng trong bảo trì và encapsulation, nhưng không cung cấp một bộ quyền tài nguyên tương đương.

Với code **không tin cậy thực sự**, hãy cân nhắc một tiến trình riêng với user/identity tối thiểu, filesystem và network rules, giới hạn CPU/memory/time, resource quotas, giao thức IPC rõ ràng và giám sát. Container có thể hỗ trợ triển khai nhưng không phải lá chắn tự động; phải cấu hình quyền, isolation và host policies. Nếu vẫn chạy code cùng JVM, hãy mặc định code đó được tin cậy ở mức tương đương tiến trình, hoặc chứng minh bằng thiết kế riêng rằng mọi hành vi nguy hiểm đã được chặn.

## 8. Cách thiết kế phòng thủ theo từng lớp

### 8.1. Đối với API Java và nội dung đầu vào

1. Dùng kiểu dữ liệu cụ thể, tránh raw types/unchecked cast khi không có lý do rõ ràng; bật và xử lý cảnh báo compiler thích hợp.
2. Xác thực giá trị theo **quy tắc nghiệp vụ**, không chỉ dựa vào kiểu Java: giới hạn kích thước, ký tự, khoảng giá trị, định dạng, quyền sở hữu dữ liệu.
3. Tránh path traversal bằng cách ràng buộc đường dẫn trong thư mục cho phép, xem xét symbolic links và kiểm tra trên đường đi thực tế.
4. Áp dụng parameterized SQL, output encoding đúng ngữ cảnh, kiểm tra phân quyền ở server side.
5. Kiểm soát deserialization, các loại class được phép và độ phức tạp dữ liệu; các dữ liệu “đúng kiểu” vẫn có thể nguy hiểm.

### 8.2. Đối với JAR/bytecode được nạp động

1. Chỉ nhận artifact từ nguồn được phê duyệt; pin phiên bản/hash và đối chiếu chữ ký nếu có; quản lý dependency và provenance. Chữ ký chứng minh publisher/integrity trong phạm vi phù hợp, **không chứng minh mã không độc**.
2. Dùng JDK được cập nhật, quản lý chính sách nạp module/class, loại bỏ cơ chế tải động từ input không tin cậy.
3. Ghi log rõ lỗi `VerifyError`, `LinkageError` hoặc `IllegalAccessError`; đừng gộp mọi lỗi thành “bị hack” hoặc tùy tiện vô hiệu hóa kiểm tra.
4. Audit thư viện bytecode manipulation và agent, vì mã biến đổi động phải chịu các ràng buộc JVM và có thể làm thay đổi hành vi được thực thi.

### 8.3. Đối với JPMS và runtime

1. Dùng `module-info.java` để định nghĩa API thật sự; chỉ `exports` những package cần công khai.
2. Chỉ `opens` những package thật sự cần reflection; cân nhắc `opens ... to ...` theo framework; tránh `open module` diện rộng theo thói quen.
3. Giữ `--add-exports`/`--add-opens` ở mức package và target module tối thiểu; review tất cả launcher flags trong production.
4. Đánh giá các thư viện dùng reflection, `MethodHandles.Lookup`, instrumentation, native access/`Unsafe`; kiểm tra phiên bản JDK và chính sách native access hiện hành.
5. Không đặt secret hoặc authorization logic chỉ sau `private` hay non-exported package. Phân quyền dữ liệu vẫn phải nằm ở các boundary có chủ thể tin cậy rõ ràng.

## 9. Bảng chẩn đoán thường dùng

| Dấu hiệu | Cơ chế hoặc nguyên nhân thường gặp | Hướng xử lý |
| --- | --- | --- |
| Compile báo `package ... is not visible` | Module đích không export package, hoặc không có readability phù hợp. | Dùng API được export, bổ sung `requires` nếu cần; chỉ mở export có chủ đích. |
| `java.lang.VerifyError` | Class file vi phạm quy tắc structural/type verification; cũng có thể do bytecode transformation không tạo frame hợp lệ. | Kiểm tra bytecode generator và tính tương thích class-file; không vô hiệu hóa verifier. |
| `java.lang.IllegalAccessError` | Truy cập field/method/class bị từ chối trong quá trình runtime/linkage, ví dụ binary mismatch/module access. | Xác minh dependency version, module exports và classpath/module path. |
| `java.lang.reflect.InaccessibleObjectException` | Phản xạ sâu muốn vượt quyền mở package. | Dùng API công khai, sửa cấu hình `opens` có chủ đích; tránh mở mọi package. |
| `ClassCastException` / `ArrayStoreException` | Kiểm tra kiểu runtime từ chối phép cast/ghi mảng. | Sửa luồng dữ liệu và contract kiểu; không coi đây là bytecode verification fail. |
| Ứng dụng bị đọc file trái ý dù mọi class đều verified | Code hợp lệ có quyền I/O theo process. | Kiểm tra authorization, process identity, filesystem ACL và isolation. |

Không phải lỗi nào ở đây cũng là lỗ hổng: nhiều lỗi là **cơ chế bảo vệ đang hoạt động đúng**. Ngược lại, “không có exception” cũng **không chứng minh chương trình an toàn**.

## 10. Câu hỏi tự kiểm tra

**1. Java 17 đã xác minh thành công bytecode của plugin. Có nghĩa plugin không thể gọi network API không?**

Không. Verifier kiểm tra tính hợp lệ của bytecode. Plugin vẫn có thể gọi API mạng hợp lệ với quyền mà tiến trình cho phép.

**2. Một class là `public` nhưng package không export. Module khác đã `requires` module đó thì dùng trực tiếp class được không?**

Không. `requires` tạo readability; cần package được `exports` đến module bên sử dụng mới có thể truy cập trực tiếp public API theo quy tắc thông thường.

**3. Tại sao `Class.forName` thấy class nội bộ nhưng `Field.trySetAccessible()` lại trả `false`?**

Tìm được/đã nạp class khác với quyền reflection truy cập sâu. JPMS chặn deep reflection từ module khác khi package không được mở tương ứng.

**4. Dùng `--add-opens` có làm class mất type safety hoặc bỏ qua bytecode verifier không?**

Không. Flag này nới quyền deep reflection của một package tới module đích, không hủy việc kiểm tra bytecode và các quy tắc kiểu. Tuy nhiên, việc mở thêm member private có thể làm tăng rủi ro thao tác nội bộ và phá invariant của ứng dụng.

**5. Đặt mã của nhiều tenant ở nhiều module trong cùng JVM có đủ để bảo đảm tenant A không đọc được dữ liệu của tenant B không?**

Không. Cần authorization thực sự trên API/dữ liệu và, khi mô hình rủi ro yêu cầu, ranh giới tiến trình/OS riêng. Module không cung cấp quyền filesystem/network hay tenant isolation.

**6. Có thể bật Security Manager như sandbox mặc định trên JDK 24+ không?**

Không. JDK 24 đã vô hiệu hóa vĩnh viễn Security Manager; cần kiến trúc isolation khác.

## Tài liệu chuẩn

- **JVMS Java SE 25** — [§4.10: Verification of class Files](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10)
- **JVMS Java SE 25** — [§5.4.1: Verification trong Linking](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4.1)
- **JLS Java SE 25** — [§5: Conversions and Contexts](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html)
- **dev.java**, Introduction to Modules: https://dev.java/learn/modules/intro/
- **dev.java**, Strong Encapsulation: https://dev.java/learn/modules/strong-encapsulation/
- **dev.java**, `--add-exports` và `--add-opens`: https://dev.java/learn/organizing/modules/add-exports-opens/
- **Oracle**, Security Manager Is Permanently Disabled (JDK 24+): https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html

**Điểm mấu chốt cần nhớ:** Type safety ngăn nhiều thao tác sai kiểu; bytecode verification từ chối nhiều class file không hợp lệ; JPMS làm rõ API và hạn chế truy cập nội bộ. Cả ba giúp giảm lỗi và bề mặt tấn công, nhưng **không tự cấp hoặc thu hồi quyền tài nguyên của code đang chạy**. Để xử lý code không tin cậy, cần trust policy và isolation dựa trên ranh giới phù hợp với mô hình đe dọa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-002">21.2 Kiểm tra dữ liệu tại ranh giới tin cậy trước khi cấp phát hoặc thực hiện thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# 21.2. Kiểm tra dữ liệu tại ranh giới tin cậy trước khi cấp phát hoặc thực hiện thao tác

## Mục tiêu

Sau bài này, bạn có thể nhận diện **ranh giới tin cậy** trong chương trình Java, xây dựng hợp đồng kiểm tra dữ liệu theo **định dạng, ý nghĩa và giới hạn tài nguyên**, xác minh kích thước **trước** khi cấp phát bộ nhớ hoặc mở tài nguyên, và kiểm tra **quyền + điều kiện nghiệp vụ** trước khi ghi file, cập nhật cơ sở dữ liệu, gọi service khác hay chạy tác vụ. Bạn cũng sẽ biết tại sao kiểm tra input là cần thiết nhưng không thể thay thế kiểm soát quyền, truy vấn tham số hóa, output encoding hoặc chống race condition.

**Nguyên tắc trung tâm:** Mọi giá trị bắt nguồn từ phía có quyền kiểm soát thấp hơn phải được coi là *chưa được chứng minh an toàn cho thao tác hiện tại*. Hãy kiểm tra nó trước khi dùng làm kích thước cấp phát, chỉ số, số lần lặp, tên tài nguyên, tham số thực thi hoặc đầu vào của một phép biến đổi tốn tài nguyên.

## 1. Ranh giới tin cậy là gì?

Ranh giới tin cậy (trust boundary) là nơi dữ liệu đi từ một phạm vi mà chương trình **không thể dựa vào tính đúng đắn, tính toàn vẹn hoặc quyền hạn** sang một phạm vi chuẩn bị dùng dữ liệu đó để làm việc có rủi ro.

Ví dụ:

```text
Untrusted source
    HTTP body / query / headers / cookies
    file upload / ZIP / ảnh / XML / JSON
    message queue / database / cache / config do bên khác quản lý
    plugin / callback / JNI / API từ hệ thống khác
             |
             v
[ Giới hạn bytes và tài nguyên tại cửa vào ]
             |
             v
[ Decode/parse có giới hạn -> validate schema + semantics ]
             |
             v
[ Kiểm tra identity, quyền, quota, invariant hiện hành ]
             |
             v
[ Tính toán kích thước/đích đến an toàn ]
             |
             v
Trusted operation: allocate, loop, write file, mutate DB,
                   send network request, execute command
```

Dữ liệu đến từ “mạng nội bộ”, “service của chúng ta”, database, cache, biến môi trường hoặc file cấu hình **không mặc nhiên đáng tin**. Nó có thể bị sửa, lưu từ request cũ, hoặc không tuân thủ hợp đồng của thành phần nhận. Kiểm tra ở biên vào giúp loại bỏ sớm giá trị xấu; **kiểm tra gần thao tác nhạy cảm** giúp chống trường hợp dữ liệu, ngữ cảnh hoặc quyền đã thay đổi.

| Dữ liệu từ phía ngoài | Thao tác có rủi ro | Điều phải xác minh trước |
| --- | --- | --- |
| `Content-Length`, length prefix, kích thước mảng | `new byte[n]`, `ByteBuffer.allocate(n)`, `readNBytes(n)` | Không âm, không tràn số, không vượt giới hạn byte thực tế |
| `count`, `limit`, `pageSize` | `new ArrayList<>(count)`, truy vấn DB nhiều bản ghi, vòng lặp | Min/max, quota tổng, giới hạn đồng thời và độ sâu xử lý |
| `width`, `height`, số frame | Cấp phát ảnh hoặc xử lý media | Tích nhiều chiều bằng phép toán kiểm tra overflow, ngân sách CPU/RAM |
| Multipart/ZIP entry/JSON array | Nạp vào RAM, giải nén, tạo file | Giới hạn dữ liệu đầu vào, độ sâu, số phần tử và lượng đầu ra sau giải nén |
| Tên file/path | `Files.newOutputStream`, `Files.delete` | Tên hợp lệ, nằm trong vùng cho phép, tránh link/race và kiểm tra quyền |
| `userId`, `documentId`, `action` | Đọc/sửa/xóa tài nguyên | Kiểu/range hợp lệ **và** quyền trên chính tài nguyên |
| URL/host | HTTP client gọi endpoint | Scheme, đích/port allowlist, chống SSRF và redirect ngoài chính sách |
| Chuỗi cho SQL/process | JDBC, `ProcessBuilder` | Hợp đồng dữ liệu và cách sử dụng API an toàn tại sink |

**Untrusted không có nghĩa tất cả dữ liệu đều độc hại:** trạng thái đúng là “chưa được xác nhận cho mục đích cụ thể”. Kiểm tra phải phù hợp với thao tác, không cấm bừa ký tự hợp lệ.

## 2. Bốn lớp kiểm tra trước khi sử dụng

### 2.1. Tính hợp lệ cú pháp (syntax)

Phải biết và mô tả format được chấp nhận: kiểu số hay text, encoding, tập ký tự, tên field, số field, độ sâu mảng/object, khoảng độ dài, chuỗi bắt buộc. Ví dụ một trường `pageSize` nên là chuỗi chữ số ASCII theo định dạng đã công bố, không phải mọi chuỗi mà `Integer.parseInt` hoặc một parser lỏng lẻo tình cờ chấp nhận.

- Chọn **allowlist** tập giá trị hợp lệ, ví dụ `action ∈ {read, export}`; tránh denylist “chặn chuỗi nguy hiểm” vì không liệt kê hết cách biểu diễn.
- Dùng phép so khớp toàn chuỗi cho định danh có format hẹp. Với văn bản tự do (tên người, nhận xét), áp dụng giới hạn độ dài, encoding hợp lệ và quy tắc nghiệp vụ; **không** chỉ cho phép A–Z nếu sản phẩm cần tiếng Việt, dấu câu hoặc các hệ chữ khác.
- Xác định rõ `null`, field vắng mặt, chuỗi rỗng, khoảng trắng, số 0, giá trị âm, số có dấu `+` và leading zero có được chấp nhận hay không. Không để thư viện tự chọn thay hợp đồng.

### 2.2. Tính hợp lệ ngữ nghĩa (semantics)

Một giá trị parse được chưa chắc dùng được. Ví dụ:

- `pageSize=1000000` là số nguyên hợp lệ nhưng vượt ngân sách truy vấn.
- `startDate`, `endDate` đều đúng ngày nhưng `startDate > endDate`.
- `width=100000` và `height=100000` là hai số dương nhưng phép nhân kích thước ảnh sẽ quá lớn.
- `status=APPROVED` nằm trong enum nhưng người dùng ở trạng thái hiện tại không có quyền thực hiện chuyển đổi đó.

Kiểm tra **quan hệ giữa các field** trước khi tạo object tốn tài nguyên hoặc thay đổi trạng thái.

### 2.3. Giới hạn chi phí (resource budget)

Chi phí đến từ **không chỉ độ dài input**: giải nén, parse sâu, regex backtracking, sắp xếp, tạo object, truy vấn DB và log đều có thể khuếch đại chi phí. Cần ngân sách cho bytes vào, bytes sau giải mã/giải nén, số item, độ sâu, CPU, thời gian, số file, tổng dung lượng, số kết nối và số request đồng thời.

Mỗi request đạt giới hạn riêng vẫn có thể gây DoS nếu hàng nghìn request chạy đồng thời. Bổ sung rate limit, concurrency limit, backpressure, quota người dùng/tenant và timeout.

### 2.4. Quyền và invariant nghiệp vụ (authorization + policy)

Sau khi định dạng và semantics hợp lệ, xác minh **principal, tenant, ownership, role, scope, điều kiện giao dịch**. Một ID hợp lệ không có nghĩa caller được sửa bản ghi có ID đó. Các quyền có thể đổi giữa lúc kiểm tra và commit; cần kiểm tra tại transaction hoặc thực thi có điều kiện ngay ở thao tác ghi.

## 3. Quan trọng nhất: kiểm tra kích thước **trước** khi cấp phát

### 3.1. Anti-pattern: lấy độ dài do client khai báo làm kích thước

```java
// SAI: ví dụ không dùng trong production.
int itemCount = Integer.parseInt(requestCount);
int bytes = itemCount * bytesPerItem;       // Có thể overflow.
byte[] data = new byte[bytes];              // Cấp phát trước khi áp quota.
```

Các lỗi thường gặp:

1. `requestCount` có thể âm, bằng 0 khi nghiệp vụ không cho phép, hoặc rất lớn. Lỗi `NegativeArraySizeException`/`OutOfMemoryError` là hậu quả, **không phải lớp kiểm soát an toàn**.
2. `itemCount * bytesPerItem` có thể tràn `int`, biến kết quả thành số âm **hoặc số dương nhỏ sai lệch**, qua mặt kiểm tra thiếu chặt chẽ.
3. Kiểm tra số phần tử mà quên tổng byte; mỗi phần tử “đúng giới hạn” nhưng tổng toàn request vẫn quá lớn.
4. `Content-Length` hay length prefix chỉ là **khai báo**. Có thể không hiện diện (chunked), không khớp nội dung thực, hoặc không phản ánh độ phình sau giải nén.
5. Đã trót parse toàn bộ JSON/object graph vào RAM rồi mới validate schema: bộ nhớ có thể cạn **trước** khi hàm validation chạy.

### 3.2. Quy tắc số học

Với `count` và `bytesPerItem`:

1. Parse bằng kiểu an toàn và kiểm tra định dạng gốc.
2. Kiểm tra từng giá trị ở miền hợp lệ, ví dụ `1 ≤ count ≤ MAX_ITEMS`.
3. Tính kích thước dẫn xuất bằng `long` và/hoặc `Math.multiplyExact`; dùng `Math.addExact` khi cộng header + body + metadata.
4. Kiểm tra **ngân sách tổng** trước khi chuyển về `int` bằng `Math.toIntExact`.
5. Chỉ sau đó mới được gọi `new byte[...]` hay API tương đương. Nếu không thật sự cần buffer toàn bộ, ưu tiên stream theo chunk nhỏ.

Đừng viết `(long) (width * height)`: phép nhân `int` **đã overflow trước khi cast**. Đúng hơn là `Math.multiplyExact((long) width, (long) height)`; với phép nhân thứ ba tiếp tục dùng `multiplyExact`. Cũng cần nhớ một mảng Java có overhead, dữ liệu giải mã có thể tạo nhiều bản sao, và `String.length()` đo **UTF-16 code units**, không phải byte UTF-8 hay số code point.

### 3.3. Ví dụ Java 17 chạy độc lập: xác minh trước khi cấp phát

Tệp: `BoundaryAllocation.java`. Chương trình mô phỏng request có số item, byte mỗi item và loại thao tác. Nó tạo buffer **chỉ sau** khi mọi giá trị vượt qua validation; ví dụ cố ý đặt ngưỡng thấp để quan sát được đường từ chối.

```java
// BoundaryAllocation.java
import java.util.Set;

public final class BoundaryAllocation {
    private static final int MAX_ITEMS = 1_000;
    private static final int MAX_BYTES_PER_ITEM = 4_096;
    private static final int MAX_TOTAL_BYTES = 1_048_576;
    private static final Set<String> ALLOWED =
            Set.of("report", "receipt");

    private record CheckedRequest(
            int items, int bytesPerItem, String operation, int totalBytes) {}

    private static int positiveAsciiDecimal(
            String raw, int maximum, String field) {
        if (raw == null || raw.isEmpty() || raw.length() > 10) {
            throw new IllegalArgumentException("Invalid " + field);
        }

        int value = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new IllegalArgumentException("Invalid " + field);
            }
            // value <= maximum tại đầu mỗi vòng: không thể overflow int.
            value = value * 10 + (ch - '0');
            if (value > maximum) {
                throw new IllegalArgumentException("Excessive " + field);
            }
        }
        if (value == 0) {
            throw new IllegalArgumentException("Zero " + field);
        }
        return value;
    }

    private static CheckedRequest validate(
            String rawItems, String rawBytes, String rawOperation) {
        int items = positiveAsciiDecimal(
                rawItems, MAX_ITEMS, "items");
        int bytesPerItem = positiveAsciiDecimal(
                rawBytes, MAX_BYTES_PER_ITEM, "bytesPerItem");
        if (rawOperation == null || rawOperation.length() > 16
                || !ALLOWED.contains(rawOperation)) {
            throw new IllegalArgumentException("Invalid operation");
        }

        long total = Math.multiplyExact(
                (long) items, (long) bytesPerItem);
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Total too large");
        }
        return new CheckedRequest(
                items, bytesPerItem, rawOperation, Math.toIntExact(total));
    }

    private static void expectRejected(
            String label, String items, String bytes, String operation) {
        try {
            validate(items, bytes, operation);
            throw new AssertionError("Unexpected accept: " + label);
        } catch (IllegalArgumentException expected) {
            System.out.println("REJECT " + label);
        }
    }

    public static void main(String[] args) {
        CheckedRequest valid = validate("8", "512", "report");
        byte[] buffer = new byte[valid.totalBytes()];
        System.out.println("ACCEPT bytes=" + buffer.length);

        expectRejected("negative", "-1", "512", "report");
        expectRejected("over-budget", "1000", "4096", "report");
        expectRejected("unknown-operation", "8", "512", "delete");
        expectRejected("unicode-digit", "８", "512", "report");
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac BoundaryAllocation.java
java BoundaryAllocation
```

Stdout chính xác:

```text
ACCEPT bytes=4096
REJECT negative
REJECT over-budget
REJECT unknown-operation
REJECT unicode-digit
```

Điểm cần hiểu:

- Hàm `validate` trả kiểu `CheckedRequest` nội bộ: đây là bằng chứng rằng các điều kiện **đã được kiểm tra trong đường đi này**; không nhận object chưa validate từ code ngoài rồi tin nó.
- `positiveAsciiDecimal` giới hạn chiều dài chuỗi trước khi duyệt; mỗi vòng kiểm tra `value > maximum` ngay, nên phép nhân `value * 10` ở vòng tiếp theo vẫn nhỏ. Đây là lựa chọn format **cho trường số giao thức**, không phải quy tắc cấm mọi chữ số Unicode trong mọi ứng dụng.
- `Math.multiplyExact` diễn đạt ý định không chấp nhận overflow; `long` tránh overflow `int` ở phép nhân hai số đã bị giới hạn. Kiểm tra `MAX_TOTAL_BYTES` vẫn bắt buộc dù phép nhân không tràn.
- Bài tập không cấp phát theo input không kiểm soát. Trong hệ thống thật, vẫn phải kiểm tra **ngân sách RAM của toàn hệ thống và số request chạy cùng lúc**; phép cấp phát có thể thất bại vì thiếu bộ nhớ dù độ dài hợp lệ.

### 3.4. Offset, slice và chuyển kiểu

Một trường `offset` và `length` có thể từng cái đều không âm nhưng tổng `offset + length` tràn. Với buffer có `capacity`, dùng điều kiện không overflow:

```java
// Fragment dùng ở nơi đã có byte[] buffer, int offset, int length.
if (offset < 0 || length < 0
        || offset > buffer.length
        || length > buffer.length - offset) {
    throw new IllegalArgumentException("Invalid slice");
}
// Bây giờ [offset, offset + length) nằm trong buffer.
```

Đối với kích thước từ `long` sang `int`, tránh ép kiểu trực tiếp `(int) longSize`. Dùng `Math.toIntExact(longSize)` **sau** khi áp quota; giá trị hợp lệ về mặt kiểu nhưng vượt RAM/disk/quota vẫn phải bị từ chối.

## 4. Biên giới dữ liệu dạng luồng: đo byte thực, không tin metadata

Với HTTP upload, WebSocket, socket hoặc file stream, cách an toàn thường là **cố định buffer nhỏ** rồi đếm lượng byte thực tế. Giới hạn ở web server/reverse proxy là lớp đầu tiên; handler Java phải có giới hạn riêng. Tránh `InputStream.readAllBytes()` trên input tùy ý, `Files.readAllBytes()` cho file không bị giới hạn, hay `InputStream.available()` để đo tổng độ dài: `available()` chỉ là ước lượng byte có thể đọc mà không block, **không phải kích thước stream**.

Fragment có thể dùng trong một service đã cấu hình output là **vùng staging/quarantine**; method dưới đây chỉ giới hạn byte thực khi copy, **chưa công bố dữ liệu ra nơi tin cậy**:

```java
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// Phương thức minh họa; không phải một class Java độc lập.
static long copyBounded(
        InputStream input, OutputStream staging, long maxBytes)
        throws IOException {
    if (maxBytes < 0) {
        throw new IllegalArgumentException("Negative limit");
    }

    byte[] buffer = new byte[8 * 1024]; // Kích thước tin cậy, cố định.
    long total = 0;
    int n;
    while ((n = input.read(buffer)) != -1) {
        if (n == 0) {
            throw new IOException("Input stream made no progress");
        }
        if (n > maxBytes - total) {
            throw new IOException("Upload limit exceeded");
        }
        staging.write(buffer, 0, n);
        total += n;
    }
    return total;
}
```

**Lý do so sánh `n > maxBytes - total`:** khi giữ bất biến `0 ≤ total ≤ maxBytes`, hiệu này không âm và không tràn, trong khi `total + n > maxBytes` dễ gặp vấn đề nếu dữ liệu/tổng là số lớn không được quản lý. Khi vượt giới hạn, hủy/quarantine tệp tạm; không để bản ghi ứng dụng trỏ tới một upload bị cắt giữa chừng.

Quy trình upload an toàn hơn:

1. **Ở gateway:** giới hạn kích thước body, số multipart part, timeout, số request và connection đồng thời. Không tin mỗi `Content-Length` để kết luận body hợp lệ.
2. **Ở handler:** xác minh tên field, kích thước metadata và type đã khai báo **trước** việc parse/cấp phát lớn; stream với bộ đếm byte thực. Tệp tạm nằm ở thư mục do service kiểm soát, quota disk riêng.
3. **Trước khi công bố:** kiểm tra file signature/content thật theo định dạng cho phép, số phần tử, kết quả giải nén, malware scanning nếu hệ thống yêu cầu, quyền và tenant. Đặt tên tệp do server sinh; chỉ chuyển từ staging sang vị trí sử dụng sau khi đạt toàn bộ điều kiện.
4. **Khi lỗi:** không publish/commit; cleanup temp và đóng stream trong `finally` hoặc try-with-resources. Thiết lập giới hạn dung lượng staging tổng và dọn orphan file.
5. **Khi thành công:** đồng bộ record metadata, tên tệp và thao tác publish theo cơ chế transaction/compensation thích hợp; thao tác move chỉ atomic khi filesystem/provider hỗ trợ. Đừng tự giả định DB transaction bao gồm cả file move.

### JSON, XML, ảnh, nén và parser là những nơi “validate sau parse” có thể quá muộn

Khi parse JSON/XML, có khả năng parser phải tạo cây object trước khi gọi Bean Validation. Cần **giới hạn body, độ sâu object/array, số token/key, chiều dài chuỗi và số lượng đối tượng** ngay trong parser hoặc trước parser. Với XML từ nguồn không tin, cấu hình chống external entity/DTD (XXE) và entity expansion theo thư viện; với Java native deserialization không tin cậy, ưu tiên **không dùng** và dùng format dữ liệu rõ schema, bởi một object graph có thể tốn chi phí lớn hay kích hoạt hành vi không mong muốn trong lúc deserialize. Nếu buộc phải dùng serialization, áp object filter và giới hạn depth, references, bytes, lớp được phép; filter không thay việc giảm bề mặt tấn công.

Khi xử lý ZIP/GZIP/ảnh, kích thước file đã nén hoặc header kích thước **không chứng minh** bộ nhớ/disk cần dùng. Ví dụ tệp ZIP vài KB có thể bung thành nhiều GB. Hạn chế số entry, tổng bytes **sau giải nén**, byte tối đa từng entry, độ sâu thư mục, tỷ lệ nén/cost CPU, và tên đích trước khi giải nén/gán tên file. Trường `ZipEntry.getSize()` có thể chưa xác định hoặc không đáng tin: đếm dữ liệu giải nén thật khi đọc, không coi header là quota.

Với ảnh, kiểm tra dimension `width × height × channels × bytesPerChannel` bằng số học không overflow **trước** khi decode đầy đủ, nhưng decoder vẫn cần giới hạn CPU/bộ nhớ và xử lý lỗi metadata.

## 5. Kiểm tra trước khi thao tác với file/path

Đường dẫn do request cung cấp là dữ liệu chưa tin cậy, không phải bằng chứng người gọi được quyền đọc, ghi hay xóa. Trước side effect, kiểm tra ai sở hữu resource, thao tác nào được phép và liệu tên file có thể do server tự cấp được không. Ưu tiên lưu file theo ID do server sinh; tên hiển thị chỉ là metadata đã kiểm tra.

### 5.1. Tên hợp lệ và giới hạn lexical

Nếu bắt buộc nhận tên file đơn, giới hạn grammar và không nhận dấu phân tách, đường dẫn tuyệt đối, dấu chấm cha `..` hoặc định dạng drive-relative. Dùng `Path.resolve(...).normalize()` và `Path.startsWith(root)` thay vì so tiền tố chuỗi. Đây là kiểm tra **cú pháp**; nó không phân giải symlink và chưa khóa đối tượng file thực.

Ví dụ anti-pattern cần từ chối: `Path.of("/srv/uploads/" + userName)` rồi gọi `Files.write(...)` ngay khi chưa kiểm tra quyền, kiểu tên và ranh giới thư mục. Cũng không chữa bằng cách cắt chuỗi `../`: việc decode, grammar và kiểm tra đích phải theo đúng filesystem và giao thức.

### 5.2. Kiểm tra gần thao tác file

Directory gốc phải do service kiểm soát với ACL phù hợp; yêu cầu tạo mới nên dùng `CREATE_NEW` thay cho `exists` rồi `write`. Dùng `NOFOLLOW_LINKS` khi API hỗ trợ để hạn chế final symlink; tùy chọn này không tự bảo vệ parent directory/hard link. `toRealPath()` chỉ kiểm tra tại một thời điểm, chưa chặn TOCTOU nếu actor khác được đổi path. Khi directory có thể bị thay đồng thời, cần cơ chế directory handle như `SecureDirectoryStream` hoặc cô lập file system đã kiểm chứng.

**Giới hạn phạm vi bài 21.2:** kiểm tra input, quyền và kích thước trước thao tác. Bài 21.4 là nơi phân tích đầy đủ symlink, tên tệp tạm, TOCTOU, filesystem provider, atomic move và test race.

## 6. Kiểm tra trước thao tác nghiệp vụ và side effect

### 6.1. Biên của API: parse ID chưa đủ

Ví dụ một API nhận `documentId` rồi thực hiện `DELETE /documents/{id}`. Dù `id` parse thành số dương, hệ thống còn phải kiểm tra tenant/owner/role và trạng thái tài liệu. Dữ liệu hợp lệ không mặc nhiên được phép.

**Sai về thứ tự và quyền:**

```java
// Pseudocode: repository và request thuộc ứng dụng.
long id = Long.parseLong(request.documentId());
repository.deleteById(id); // Xóa trước khi kiểm tra người gọi có quyền.
if (!user.canDelete(id)) {
    throw new SecurityException();
}
```

**Đúng hướng:** validate `documentId` (format, range), xác thực principal trên server, xác minh quyền trên **chính tài nguyên**, rồi mới thực hiện thao tác. Với DB nhiều tenant, ràng buộc phạm vi ngay trong câu lệnh ghi để giảm khoảng cách check/use:

```sql
DELETE FROM documents
WHERE id = ? AND tenant_id = ? AND owner_id = ?;
```

Dùng `PreparedStatement` đặt parameter đúng kiểu, không nối SQL từ chuỗi user; kiểm tra số dòng bị ảnh hưởng, trạng thái và transaction isolation theo yêu cầu. Câu SQL trên chỉ là **một ví dụ policy** (owner phải khớp): hệ thống có thể có quyền admin/shared collaborator khác và phải điều chỉnh theo policy thực. Nếu cần kiểm tra thêm trạng thái, đưa điều kiện trạng thái vào cùng operation/transaction. Không “check-then-act” ngoài transaction rồi giả định dữ liệu không đổi.

### 6.2. Hai hướng kiểm tra không thay thế nhau

- **Input validation:** giá trị có đúng schema, range, semantics và ngân sách không?
- **Authentication:** principal nào gọi?
- **Authorization:** principal ấy được phép thao tác với **tài nguyên cụ thể** không?
- **Sink protection:** API tương ứng có được dùng an toàn không, ví dụ prepared query, output encoding, tạo file an toàn, kiểm soát lệnh shell?
- **Consistency:** quyền/phiên bản/dữ liệu có còn đúng ở thời điểm ghi không?

Ví dụ “SQL injection” không được chữa dứt điểm bằng cách cấm dấu nháy đơn. Tên người có dấu nháy có thể hợp lệ; phải dùng prepared query. Tương tự XSS cần **encode khi render theo ngữ cảnh HTML/attribute/JS/CSS**, dù input đã được validate. Gọi process với `ProcessBuilder` để tách executable và argument vẫn cần allowlist option/đích nếu user được chọn tùy ý.

### 6.3. Gọi HTTP theo URL người dùng đưa

Chuỗi URL có cú pháp hợp lệ vẫn có thể đưa server tới metadata endpoint, loopback, dải IP nội bộ hay host do attacker điều khiển (SSRF). Trước khi gửi request, áp allowlist cho endpoint được hỗ trợ (scheme, host, port, path), chính sách DNS/địa chỉ đích, redirect và network egress. Kiểm tra cả **đích kết nối thực sau resolve/redirect**, không chỉ tiền tố ký tự hay hostname ban đầu. Tránh nhận một URL bất kỳ rồi phát `HttpClient.send()` dưới quyền của service.

### 6.4. Thao tác lặp lại, quota và TOCTOU

Xác nhận quyền đúng lúc T0 không chắc đúng ở T1 nếu tài nguyên chuyển tenant, bị thu hồi, hết hạn hoặc bị cập nhật bởi request khác. Với side effect quan trọng, dùng transaction/optimistic lock (`version`), điều kiện `WHERE`, idempotency key và authorization gần thao tác. Rate limit và quota phải được thực thi nhất quán khi nhiều request chạy song song; kiểm tra quota rồi cấp phát đồng thời có thể khiến tổng vượt giới hạn nếu không có cơ chế reserve/release nguyên tử.

## 7. Canonicalization, encoding và input bị biến đổi

Một input có nhiều cách biểu diễn: URL percent-encoding, JSON escape, UTF-8, Unicode composition, case folding, dấu phân cách path hoặc locale. Hãy xác định **dạng sẽ thật sự được sử dụng** và kiểm tra trên dạng đó sau bước decode/normalization phù hợp với hợp đồng.

- **Decode có chủ đích:** HTTP framework có thể đã percent-decode path/query. Đừng decode thủ công lần hai khi downstream không decode; đừng validate chuỗi encoded rồi gửi chuỗi decoded sang sink.
- **UTF-8:** byte length khác số ký tự hiển thị. Nếu policy giới hạn dữ liệu truyền, đếm **byte thực** trước/during decode; nếu policy giới hạn tên hiển thị, định nghĩa rõ code point, grapheme hay UTF-16 unit. `String.length()` trả UTF-16 code units; kiểm tra số code point dùng `codePointCount` khi phù hợp.
- **Unicode normalization:** có thể cần NFC để so sánh username/label theo policy, nhưng không được tự ý thay đổi nội dung mật khẩu, chữ ký hay định danh mà protocol đòi hỏi khớp byte. NFKC có thể thay đổi ý nghĩa ký tự; chỉ dùng khi có lý do rõ ràng.
- **Regular expressions:** giới hạn length **trước** khi áp regex; dùng pattern đơn giản với so khớp toàn giá trị, tránh kiểu `(a+)+` gây backtracking lũy tiến. Không cho input tạo regex tùy ý trừ khi có sandbox/quota thích hợp.
- **Logging:** bản thân log là sink. Ghi mã lỗi, loại vi phạm, request ID và số liệu đã kiểm tra; không log toàn bộ payload, secret, token hoặc chuỗi có ký tự xuống dòng không được escape.

Một giá trị đã được normalize/parse chỉ chứng minh “đúng hợp đồng ở bước này”. Nếu dữ liệu được biến đổi lần nữa trước sink, cần kiểm tra lại theo chính semantics của sink đó.

## 8. Một mẫu thiết kế Java: dữ liệu thô → kiểu đã kiểm tra → thao tác

Với API không tầm thường, nên tách rõ các lớp:

```text
Controller/adapter
  |
  | raw String / bytes / DTO từ bên ngoài
  v
Input limits & strict parser
  |
  | checked field types + sizes + cross-field invariants
  v
Validated command / domain value object
  |
  | identity + resource authorization + quota/reservation
  v
Application service
  |
  | transaction, safe API, bounds, idempotency
  v
Filesystem / DB / network / external process
```

Thiết kế tốt có các đặc tính:

1. **Hợp đồng đầu vào nêu rõ ngưỡng** và được đặt theo cấu hình đáng tin cậy. Ví dụ `MAX_PAGE_SIZE=200` là giới hạn nghiệp vụ, không lấy từ request.
2. **Đầu vào đã kiểm tra có kiểu riêng** (record/class có constructor/factory kiểm tra bất biến, chỉ tạo được từ đường validation). Không truyền mãi `Map<String,Object>`/String thô qua nhiều tầng rồi tùy nơi diễn giải khác nhau.
3. **Không thực hiện side effect trong lúc còn validate.** Nhiều thao tác chỉ nên bắt đầu sau khi toàn bộ command hợp lệ. Với streaming lớn, ghi vào vùng tạm cô lập trước, commit/publish sau.
4. **Dữ liệu thay đổi được** (array, List, object mutable, file path, external resource) phải được snapshot/copy an toàn khi thích hợp, kiểm tra **bản sao sẽ dùng**; nếu copy bản thân nó tốn RAM, phải áp kích thước trước khi copy. Không dựa vào việc caller sẽ không sửa object sau validation.
5. **Context-aware:** một chuỗi “đúng format” cho tên report không có nghĩa an toàn để làm tên SQL column, đường dẫn hay tham số shell. Mỗi sink có policy riêng.
6. **Thất bại có kiểm soát:** API trả lỗi validation 400/422 hoặc status phù hợp; 403 cho authorization failure theo chính sách; trả thông điệp có ích nhưng không leak stack trace, path nội bộ hoặc nội dung bí mật. Thiết lập quota cả cho chi phí log và error handling.

### Chỗ đặt validation

| Tầng | Kiểm tra |
| --- | --- |
| Reverse proxy/API gateway | Dung lượng request, header, timeout, rate/concurrency |
| Parser/serializer | Encoding, max nesting/tokens/string/array, schema, cấm dangerous features |
| DTO/domain factory | Field constraints, enum allowlist, quan hệ field, số học/chỉ số |
| Service | Quyền, tenant, quota, invariant, mức chi phí |
| Sink (file/DB/network/process) | Kiểm tra đích và ngữ cảnh hiện tại, transaction/race, API bảo vệ đặc thù |

Không nên nhồi mọi logic vào một regex hoặc một annotation kiểu `@Valid` rồi cho rằng công việc đã xong. Bean Validation chạy **sau deserialize** nên không ngăn được mọi DoS trước parser. Server-side validation vẫn cần ngay cả khi trình duyệt đã validate để cải thiện trải nghiệm.

## 9. Lỗi phổ biến và vì sao sai

| Anti-pattern | Vấn đề | Hướng thay thế |
| --- | --- | --- |
| `new byte[Integer.parseInt(header)]` | Tin length chưa kiểm tra, RAM DoS | Parse + cap + tính toán an toàn trước allocate |
| `if (a*b < MAX)` với `int` | Overflow làm sai phép so sánh | Cast trước nhân, `multiplyExact`, total budget |
| `readAllBytes()` rồi kiểm tra `length` | Đã tiêu RAM trước kiểm tra | Giới hạn ingress, đọc stream theo chunk |
| `available()` làm “file length” | Chỉ là dữ liệu có thể đọc không block | Count bytes thực; dựa vào size API chỉ khi đáng tin và vẫn áp cap |
| Tin `Content-Length`/ZIP entry size | Metadata sai/không có, giải nén phình to | Đếm bytes đã đọc/đã giải nén, số entry và quota |
| Chỉ validate ID rồi gọi `delete` | Thiếu authorization | Kiểm tra principal/tenant/owner và trạng thái trong transaction |
| Xóa chuỗi `../` rồi gọi `Files.write` | Bypass encoding, separator, symlink/race | Tên server sinh, Path API, thư mục khóa quyền, thao tác an toàn |
| Chặn ký tự `'` để “ngừa SQL injection” | Vừa làm hỏng dữ liệu hợp lệ vừa không đủ | Prepared statements và allowlist SQL identifier |
| Validate sau object deserialization lớn | Parser đã có thể cạn RAM/CPU | Limit trước & trong parse; hạn chế object graph |
| Chỉ validate lần đầu, sau đó object bị sửa | TOCTOU / stale invariant | Immutable snapshot, recheck gần sink |
| Thấy `IOException`/`OutOfMemoryError` rồi “xử lý” | Hệ thống đã tiêu tài nguyên, side effect dang dở | Preflight budget, staging, cleanup, giới hạn đồng thời |
| Log payload nguyên văn khi reject | Lộ secret, log injection, disk DoS | Structured log có kiểm soát kích thước, mask sensitive data |

## 10. Tự kiểm thử với bảng trường hợp biên

Dùng bảng này khi review API mới hoặc thêm parser/resource:

| Ca thử | Kỳ vọng |
| --- | --- |
| Giá trị nhỏ hợp lệ: `items=8, bytes=512` | Chấp nhận, tính `total=4096` rồi mới cấp phát |
| `null`, field thiếu, chuỗi rỗng hoặc khoảng trắng trái hợp đồng | Từ chối trước thao tác |
| `-1`, `0`, số có dấu `+`, chữ số fullwidth khi format chỉ ASCII | Xử lý đúng policy; không chuyển âm thành kích thước |
| `Integer.MAX_VALUE`, `Long.MAX_VALUE`, chuỗi số dài | Từ chối khi parse/range/budget, không allocate |
| Hai số đơn lẻ hợp lệ nhưng tích vượt max | Từ chối trước khi nhân/cấp phát bằng `int` |
| Offset sát cuối, length vượt buffer hoặc tổng có thể tràn | Từ chối trước `copy/read/write` |
| HTTP body không `Content-Length`, chunked, length khai sai | Giới hạn theo byte **thực**; không tin header |
| JSON sâu/hàng triệu phần tử | Parser/ngưỡng từ chối sớm; không build toàn cây |
| ZIP nhỏ nhưng bung rất lớn, `entry.getSize()=-1` | Giới hạn theo output uncompressed; hủy staging nếu vượt |
| Tên file `../x`, `..\x`, path tuyệt đối, symlink | Không thoát được thư mục được phép; không ghi đè |
| ID hợp lệ nhưng sai tenant hoặc thiếu quyền | Không có side effect; ủy quyền phải kiểm tra ở server |
| Quyền/tài nguyên bị đổi đồng thời | Transaction/optimistic lock/conditional write phát hiện |
| 100 request mỗi request trong hạn mức | Quota tổng và số job đồng thời vẫn được giữ |
| Input ác ý có CR/LF/secret trong log message | Không làm hỏng cấu trúc log hoặc lộ thông tin |

**Kỹ thuật kiểm thử:** property-based test cho mọi giá trị số ở biên `min-1, min, max, max+1` và các phép nhân/cộng; fuzz parser với encoding khác nhau, nested array và dữ liệu bị cắt; integration test đo heap, disk, thời gian, timeout và concurrency. Cùng một bộ dữ liệu nên kiểm tra cả “không có side effect khi reject” (không tạo file, không ghi DB, không gửi request ra ngoài).

## 11. Checklist code review ngắn gọn

Trước khi merge đoạn code nhận input ngoài và làm việc có chi phí hoặc đặc quyền, trả lời được:

1. Dữ liệu đến từ ai và có thể bị sửa ở đâu? Các byte/field có được giới hạn **trước khi parse/copy** không?
2. Có chấp nhận đúng kiểu, định dạng, chiều dài, miền giá trị, enum và quan hệ giữa các field không?
3. Mọi phép cộng/nhân/offset/chuyển kiểu dùng để cấp phát, lặp hoặc tạo số bản ghi đã được kiểm tra overflow và quota tổng chưa?
4. Sau decode, normalization hoặc biến đổi khác, code có kiểm tra đúng biểu diễn sẽ đến sink không?
5. Có thực hiện network/file/DB/process operation **chỉ sau** authorization và business invariant chưa?
6. Nếu có streaming, metadata không khớp, timeout, người dùng hủy hay parser lỗi, dữ liệu dở dang có được cách ly và cleanup không?
7. Có ngăn DoS ở mức request **và tổng hệ thống** (RAM/disk/CPU, đồng thời, tốc độ) không?
8. Quyền/đích dùng cho thao tác có thể đổi giữa check và use không; nếu có, đã dùng transaction/handle/conditional operation chưa?
9. Có tránh log secret và chọn đúng API chống injection (prepared SQL, output encoding, an toàn path/URL) không?
10. Đã kiểm thử các đầu vào bất thường và xác nhận hệ thống không tạo side effect khi reject chưa?

## 12. Tóm tắt mô hình tư duy

**Chỉ sau khi biết một giá trị có hợp lệ cho việc đang làm mới được phép biến nó thành chi phí hoặc quyền hạn.** Kiểm tra sớm giúp giảm tác động của input xấu; kiểm tra gần điểm sử dụng giúp ngăn sai khác do biến đổi dữ liệu, trạng thái và race. Với dữ liệu lớn, **giới hạn trước và trong quá trình xử lý** là điều kiện cần; với file, DB hoặc mạng, **kiểm tra quyền và đích thực tại thời điểm thao tác** quan trọng không kém format của input.

Một workflow đáng tin là:

```text
RECEIVE bounded
  -> DECODE strictly
  -> PARSE with limits
  -> VALIDATE format + semantics
  -> CHECK budget + arithmetic
  -> AUTHORIZE current action
  -> EXECUTE with safe sink and transaction
  -> COMMIT/PUBLISH only on success
```

### Tài liệu chuẩn

- [Oracle — Secure Coding Guidelines for Java SE](https://www.oracle.com/java/technologies/javase/seccodeguide.html), nhất là các phần Denial of Service, Input Validation, Access Control, Serialization.
- [OWASP — Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html): syntax/semantic validation, allowlist, parser limits, Unicode và validation ở backend.
- [Java 17 — `Math`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html): `multiplyExact`, `addExact`, `toIntExact`.
- [Java 17 — `InputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html): `readAllBytes`, `readNBytes`, `available` và giới hạn bộ nhớ.
- [Java 17 — `Files`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html), [`SecureDirectoryStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html): xử lý file/path và thao tác hạn chế race.
- [CWE-20 — Improper Input Validation](https://cwe.mitre.org/data/definitions/20.html), [CWE-190 — Integer Overflow](https://cwe.mitre.org/data/definitions/190.html), [CWE-400 — Uncontrolled Resource Consumption](https://cwe.mitre.org/data/definitions/400.html), [CWE-22 — Path Traversal](https://cwe.mitre.org/data/definitions/22.html): phân loại lỗi để lập test case.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-003">21.3 Chống injection bằng API có tham số và tách dữ liệu khỏi lệnh hoặc biểu thức</a>

<details>
<summary>Xem chi tiết</summary>

# 21.3 — Chống injection bằng API có tham số và tách dữ liệu khỏi lệnh hoặc biểu thức

## Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích vì sao injection xảy ra khi dữ liệu không đáng tin được đưa vào **cú pháp** của SQL, lệnh hệ điều hành, LDAP filter, XPath, biểu thức động hoặc ngôn ngữ truy vấn khác.
2. Dùng API có tham số hoặc API tạo cấu trúc để giữ **câu lệnh do ứng dụng kiểm soát** tách khỏi **giá trị do người dùng cung cấp**.
3. Viết truy vấn JDBC với `PreparedStatement`, xử lý các phần SQL không thể dùng dấu `?` (tên cột, bảng, `ORDER BY`), và nhận ra các lỗi “parameterization giả”.
4. Phân biệt việc truyền từng đối số cho tiến trình với việc nhúng đầu vào vào chuỗi lệnh shell; biết rủi ro *argument injection* còn lại.
5. Dùng đối số có kiểu cho bộ lọc LDAP, XPath variable cho biểu thức XPath, và tránh thực thi SpEL/EL do người dùng viết.
6. Kiểm thử cả đầu vào cố ý chứa ký tự điều khiển cú pháp lẫn các lỗi phân quyền, giới hạn kích thước, quyền của tiến trình và cơ sở dữ liệu.

Đây là một bài về **nguyên tắc và kỹ thuật ngăn injection trong ứng dụng Java**. Những đoạn mã minh họa chỉ xử lý cú pháp của *sink* được nêu; chúng không tự giải quyết xác thực, phân quyền hay mọi rủi ro bảo mật khác.

## 1. Injection là gì?

**Injection** xảy ra khi dữ liệu từ bên ngoài vượt qua ranh giới tin cậy và được một bộ thông dịch (*interpreter*) hiểu như **một phần của chương trình, lệnh hoặc biểu thức** thay vì một giá trị dữ liệu.

Ví dụ ứng dụng cần tìm tài khoản theo tên `alice`, nhưng lại ghép tên vào câu SQL:

~~~java
// KHÔNG AN TOÀN: biến username có thể đến từ HTTP request.
String sql = "SELECT id FROM accounts WHERE username = '" + username + "'";
~~~

Nếu `username` chứa `alice' OR '1'='1`, nội dung được ghép sẽ thay đổi cấu trúc điều kiện `WHERE`. Vấn đề không nằm ở riêng dấu nháy đơn, mà nằm ở việc **một chuỗi dữ liệu đang được trình phân tích cú pháp SQL đọc như mã SQL**.

Mô hình luồng dữ liệu:

~~~text
Nguồn không đáng tin
(HTTP, message, file, DB, config, biến môi trường)
                 │
                 ▼
      Chuỗi dữ liệu của ứng dụng
                 │
       [ghép vào câu lệnh?] ── Có ──► Interpreter đọc như cú pháp
                 │                           │
                Không                  Injection có thể xảy ra
                 │
                 ▼
   API truyền giá trị riêng / cấu trúc được kiểm soát
                 │
                 ▼
     Interpreter chỉ diễn giải dữ liệu ở vị trí giá trị
~~~

Các cặp ví dụ:

| Bộ thông dịch / ngôn ngữ | Dạng chuỗi nguy hiểm | Cách xử lý ưu tiên |
| --- | --- | --- |
| SQL (JDBC) | Ghép dữ liệu vào `SELECT`, `UPDATE`, `DELETE` | `PreparedStatement` và bind giá trị |
| JPQL/HQL | Ghép vào chuỗi câu truy vấn ORM | Named parameter/positional parameter, Criteria API |
| Shell / CLI | `sh -c`, `cmd /c` với nội dung người dùng | Tránh shell; API Java chuyên dụng, `ProcessBuilder` với argv riêng và kiểm soát đối số |
| LDAP filter | Ghép trực tiếp vào `(&(uid=...))` | `DirContext.search(..., filterArgs, ...)` |
| XPath/XQuery | Ghép giá trị vào XPath expression | XPath variable hoặc API truy cập DOM |
| SpEL/EL/template | Đánh giá biểu thức do người dùng kiểm soát | Biểu thức cố định, dữ liệu là biến/đối tượng; không parse expression tùy ý |
| Regex | Dùng chuỗi dữ liệu làm regex | `Pattern.quote` hoặc `Pattern.LITERAL` nếu mục tiêu là tìm literal |
| NoSQL/query DSL | Ghép chuỗi thành JSON/query/operator | API builder tạo cấu trúc truy vấn với giá trị tách biệt |

**Dữ liệu đã lưu trong database cũng có thể không đáng tin.** Một giá trị nhập hôm nay được lưu nguyên vẹn, rồi ngày mai bị ghép vào lệnh khác, là *second-order injection*. Không mặc định “đã lưu trong DB” đồng nghĩa “an toàn”.

## 2. Nguyên tắc bảo vệ: cấu trúc cố định, giá trị truyền riêng

Một biện pháp chống injection tốt cần trả lời được hai câu:

- **Ai quyết định cấu trúc câu lệnh?** Mã ứng dụng, enum hoặc mapping từ danh sách giá trị cho phép.
- **Giá trị không đáng tin đi qua kênh nào?** Parameter binding, object/AST builder, variable resolver hoặc một đối số độc lập, thay vì chuỗi lệnh đang được nối thủ công.

Thứ tự ưu tiên:

1. **Không gọi bộ thông dịch nếu không cần.** Ví dụ dùng `Files`/`Path` thay cho lệnh shell đọc tệp, hoặc API đối tượng thay cho tự dựng XPath/SQL.
2. **Dùng API có tham số thật sự.** Cấu trúc câu lệnh được tạo độc lập; giá trị được bind theo đúng vị trí và kiểu.
3. **Với phần cú pháp không bind được, dùng allowlist/mapping do ứng dụng sở hữu.** Ví dụ cột sắp xếp chỉ có `name` hoặc `created_at`.
4. **Nếu ngôn ngữ/API không có binding, dùng mã hóa/escaping đúng *ngữ cảnh* và chuẩn của bộ thông dịch**, kèm kiểm tra đầu vào và thiết kế hạn chế cú pháp. Đây thường là lựa chọn sau cùng; escaping cho SQL không dùng thay `PreparedStatement`.
5. **Defense in depth:** phân quyền, tài khoản DB quyền tối thiểu, thời gian chạy tối đa, giới hạn kết quả, kiểm soát file/host, logging và kiểm thử.

> **Phân biệt quan trọng:** “Đầu vào hợp lệ về mặt định dạng” không đồng nghĩa “an toàn để ghép vào code”. Ngay cả một giá trị chỉ gồm chữ và số cũng có thể mang ý nghĩa cú pháp trong ngữ cảnh khác; ngược lại dấu nháy hoặc dấu sao có thể là dữ liệu hoàn toàn hợp lệ.

## 3. SQL injection: từ nối chuỗi sang `PreparedStatement`

### 3.1. Ví dụ trước – sau

Mã sai:

~~~java
// Ví dụ nhận một Connection và một giá trị người dùng; không phải chương trình độc lập.
String sql = "SELECT id, username FROM accounts WHERE username = '" + username + "'";
try (Statement statement = connection.createStatement();
     ResultSet rs = statement.executeQuery(sql)) {
    while (rs.next()) {
        System.out.println(rs.getString("username"));
    }
}
~~~

Mã đúng:

~~~java
// connection: java.sql.Connection đang mở; username: giá trị không đáng tin.
String sql = "SELECT id, username FROM accounts WHERE username = ?";
try (PreparedStatement ps = connection.prepareStatement(sql)) {
    ps.setString(1, username);
    try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
            System.out.println(rs.getString("username"));
        }
    }
}
~~~

`?` là **parameter marker** ở vị trí giá trị. `setString(1, username)` bind tham số thứ nhất (JDBC đánh số từ **1**, không phải 0). Driver và DB thực hiện binding theo giao thức của chúng; đầu vào `alice' OR '1'='1` được xem là **một giá trị chuỗi**, không trở thành toán tử `OR`.

Trong một prepared statement, bạn cũng có thể bind:

~~~java
ps.setLong(1, accountId);
ps.setInt(2, limit);
ps.setBigDecimal(3, amount);
ps.setNull(4, java.sql.Types.VARCHAR);
~~~

Đây là ví dụ các phương thức bind, **không phải bốn lệnh áp dụng đồng thời vào câu SQL có một tham số**. Chọn đúng phương thức và SQL type tương thích với cột. Dùng `try-with-resources` đóng `PreparedStatement` và `ResultSet`.

**Quan trọng:** mã sau **vẫn sai** dù sử dụng lớp `PreparedStatement`:

~~~java
String sql = "SELECT id FROM accounts WHERE username = '" + username + "'";
try (PreparedStatement ps = connection.prepareStatement(sql)) {
    try (ResultSet rs = ps.executeQuery()) {
        // SQL đã bị ghép dữ liệu trước khi prepare.
    }
}
~~~

Sự an toàn đến từ **tách SQL syntax và bound values**, chứ không đến từ tên class `PreparedStatement`.

### 3.2. Demo JDBC chạy được trên Java 17 + Maven + H2

Bài demo này dùng H2 in-memory để bạn không cần máy chủ DB riêng. Cần JDK 17+ và Maven; Maven tải dependency H2 khi chạy lần đầu. Cấu trúc:

~~~text
jdbc-binding-demo/
├── pom.xml
└── src/main/java/demo/JdbcBindingDemo.java
~~~

Tạo **pom.xml**:

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>demo</groupId>
    <artifactId>jdbc-binding-demo</artifactId>
    <version>1.0.0</version>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>2.5.252</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.0</version>
                <configuration><release>17</release></configuration>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.6.4</version>
                <configuration><mainClass>demo.JdbcBindingDemo</mainClass></configuration>
            </plugin>
        </plugins>
    </build>
</project>
~~~

Tạo **src/main/java/demo/JdbcBindingDemo.java**:

~~~java
package demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class JdbcBindingDemo {
    public static void main(String[] args) throws SQLException {
        try (Connection connection =
                     DriverManager.getConnection("jdbc:h2:mem:learning")) {
            // SQL tĩnh với dữ liệu mẫu do chương trình sở hữu.
            try (Statement setup = connection.createStatement()) {
                setup.execute("CREATE TABLE users (id INT PRIMARY KEY, username VARCHAR(80))");
                setup.executeUpdate("INSERT INTO users VALUES (1, 'alice'), (2, 'bob')");
            }

            System.out.println("alice=" + countByUsername(connection, "alice"));
            System.out.println("payload=" +
                    countByUsername(connection, "alice' OR '1'='1"));
        }
    }

    private static int countByUsername(Connection connection, String username)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
~~~

Trong thư mục có `pom.xml`, chạy:

~~~bash
mvn -q compile exec:java
~~~

Stdout của chính chương trình (không tính thông báo tải dependency nếu Maven hiển thị):

~~~text
alice=1
payload=0
~~~

Tại sao dòng thứ hai bằng 0? Không có username nào bằng nguyên chuỗi `alice' OR '1'='1`; toán tử và dấu nháy trong chuỗi không được diễn giải thành SQL. Ví dụ dùng đầu vào có hình thức tấn công chỉ để kiểm tra **tính bất biến của cấu trúc truy vấn**; không nên học bằng cách thử dữ liệu thật trong hệ thống sản xuất.

H2 chỉ là môi trường thực hành. Khi đổi sang PostgreSQL, MySQL, Oracle hoặc DBMS khác, kiểm tra dialect, kiểu dữ liệu và driver tương ứng; nguyên tắc binding không đổi.

## 4. SQL có phần động: không phải chỗ nào cũng dùng `?`

### 4.1. Cột, bảng, hướng sắp xếp và keyword

Placeholder `?` thường chỉ dùng cho **giá trị**, không thay được tên bảng, tên cột hay từ khóa. Không viết:

~~~sql
SELECT * FROM accounts ORDER BY ?
~~~

với kỳ vọng `?` trở thành tên cột. Tùy DBMS, câu này có thể lỗi hoặc chỉ mang nghĩa một biểu thức giá trị cố định, **không tạo ra thứ tự sắp xếp mong muốn**.

Thay vào đó, ánh xạ lựa chọn bên ngoài sang token SQL cố định do ứng dụng định nghĩa:

~~~java
// sortBy đến từ request; chỉ trả về SQL fragment do ứng dụng sở hữu.
static String orderByClause(String sortBy) {
    return switch (sortBy) {
        case "name" -> "username ASC";
        case "recent" -> "created_at DESC";
        default -> throw new IllegalArgumentException("Unsupported sort option");
    };
}

// Hợp lệ vì orderByClause không thể trả về nội dung SQL tùy ý từ người dùng.
String sql = "SELECT id, username FROM accounts ORDER BY " + orderByClause(sortBy);
~~~

Nếu client yêu cầu một tên cột không có trong mapping, **từ chối**, không trả nguyên chuỗi client. Tương tự, muốn cho chọn tên bảng, tên field JSON hoặc toán tử so sánh thì chỉ chọn trong tập do ứng dụng định nghĩa; cân nhắc enum thay vì chuỗi rời rạc.

### 4.2. `LIKE`: binding an toàn cú pháp nhưng wildcard vẫn mang nghĩa tìm kiếm

Với `LIKE ?`, ký tự `%` và `_` **không biến thành SQL injection**, nhưng vẫn có nghĩa wildcard trong biểu thức `LIKE`. Nếu chức năng yêu cầu tìm một cụm từ **theo nghĩa đen**, bạn phải escape ký tự wildcard theo dialect.

Ví dụ theo cú pháp `ESCAPE '!'` được DB hỗ trợ:

~~~java
static String escapeLikeLiteral(String input) {
    return input.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
}

String sql = "SELECT id FROM products WHERE title LIKE ? ESCAPE '!'";
try (PreparedStatement ps = connection.prepareStatement(sql)) {
    ps.setString(1, "%" + escapeLikeLiteral(searchTerm) + "%");
    try (ResultSet rs = ps.executeQuery()) {
        // Đọc kết quả; câu SQL cố định, searchTerm được bind.
    }
}
~~~

Ví dụ này là snippet cần một `Connection connection` và `String searchTerm` có sẵn. Tại đây, escaping phục vụ **ngữ nghĩa tìm kiếm literal trong LIKE**, không thay thế parameter binding. Nếu ứng dụng thật sự cho phép người dùng dùng wildcard, hãy xác định hợp đồng API và giới hạn truy vấn rõ ràng.

### 4.3. Số lượng tham số thay đổi trong `IN`

Không thể bind nguyên danh sách vào một dấu `?` rồi kỳ vọng DB tự mở rộng thành nhiều placeholder trong cú pháp `IN (?, ?, ...)`. Ta tạo **số lượng dấu `?` từ độ dài danh sách đã kiểm soát**, và bind từng giá trị:

~~~java
// ids: List<Long>, connection: Connection đang mở.
if (ids.isEmpty()) {
    return; // Xử lý trường hợp rỗng theo quy tắc của hàm gọi.
}
if (ids.size() > 100) {
    throw new IllegalArgumentException("Too many ids");
}
String markers = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
String sql = "SELECT id, username FROM accounts WHERE id IN (" + markers + ")";

try (PreparedStatement ps = connection.prepareStatement(sql)) {
    for (int i = 0; i < ids.size(); i++) {
        ps.setLong(i + 1, ids.get(i));
    }
    try (ResultSet rs = ps.executeQuery()) {
        // Xử lý các hàng đã được chọn.
    }
}
~~~

Đây là đoạn thân hàm minh họa, không phải file Java độc lập. Phần chuỗi `markers` chỉ chứa các dấu `?` do chương trình tạo; **ID thực vẫn bind riêng**. Giới hạn số phần tử để tránh truy vấn quá lớn; với danh sách rất dài, dùng array parameter, temporary table hoặc API tương ứng DBMS khi phù hợp.

### 4.4. `NULL`, type conversion và phân quyền

Dùng `setNull(index, sqlType)` với `Types` phù hợp; xử lý các quy tắc `IS NULL` khác với `= NULL` ở SQL. Không tự ghép `"NULL"` hoặc giá trị chuỗi do người dùng cung cấp.

Prepared statements chống việc một **giá trị** thay đổi cú pháp SQL. Chúng **không** ngăn người dùng yêu cầu ID của bản ghi người khác nếu ứng dụng thiếu kiểm tra quyền. Ví dụ multi-tenant nên kết hợp `WHERE tenant_id = ? AND id = ?` với `tenant_id` lấy từ định danh đã xác thực và chính sách phía server, không tin `tenant_id` do client tự khai.

## 5. ORM, JPA/JPQL/HQL, stored procedure và NoSQL

Dùng ORM không tự loại bỏ injection. Câu JPQL/HQL được dựng bằng cách ghép chuỗi vẫn bị tác động nếu giá trị không đáng tin trở thành phần syntax.

**JPA đúng** (snippet trong ứng dụng có `EntityManager em` và entity `Account`; cần Jakarta Persistence, không thuộc JDK thuần):

~~~java
var accounts = em.createQuery(
        "SELECT a FROM Account a WHERE a.owner = :owner", Account.class)
    .setParameter("owner", ownerInput)
    .getResultList();
~~~

**JPA sai:**

~~~java
var accounts = em.createQuery(
        "SELECT a FROM Account a WHERE a.owner = '" + ownerInput + "'",
        Account.class)
    .getResultList();
~~~

Các thư viện có named parameter, query builder, Criteria API hoặc DSL có typed values đều giúp tách cú pháp và dữ liệu **nếu dùng đúng phương thức bind**. Đọc API thực tế: một số query builder chấp nhận fragment SQL thô, string templates hoặc dynamic path; chỗ đó vẫn phải kiểm soát như cú pháp.

Stored procedure cũng không mặc nhiên an toàn. Bạn có thể gọi procedure bằng `CallableStatement` và bind tham số, nhưng procedure phía server vẫn có thể nối chuỗi tạo dynamic SQL. Kiểm tra **cả bên trong procedure**.

Với MongoDB hoặc hệ truy vấn NoSQL, tránh tạo JSON query bằng nối chuỗi và tránh cho user gửi trực tiếp object cấu trúc operator (ví dụ key bắt đầu bằng `$`) nếu hợp đồng API chỉ muốn một giá trị. Ưu tiên API như builder cho **equality của field cố định với value riêng**; kiểm tra cách driver serialize kiểu và có thể sinh operator nào. NoSQL query builder cũng có thể nguy hiểm nếu cho người dùng chọn toán tử hoặc đường dẫn field tùy ý.

## 6. OS command injection: API Java và đối số riêng

### 6.1. Tại sao shell rất nhạy cảm với dữ liệu?

Khi dùng `sh -c` hoặc `cmd /c`, các ký tự như dấu phân cách lệnh, chuyển hướng và pipe có thể được shell phân tích như cú pháp. Đừng tạo chuỗi:

~~~java
// Không an toàn nếu filename là dữ liệu bên ngoài.
new ProcessBuilder("sh", "-c", "cat " + filename).start();
~~~

Trên Windows, lệnh tương đương dựa vào `cmd /c` cũng có nguy cơ tương tự. Lưu ý `Runtime.exec(String)` của Java **không tự động tương đương** với gọi `/bin/sh -c`; nó tách chuỗi thành token theo cơ chế của Java và vẫn có nguy cơ tách sai đối số. Vì vậy không dùng tính chất này làm biện pháp bảo vệ.

**Ưu tiên dùng API Java chuyên dụng**:

~~~java
// Tránh gọi lệnh "cat"; Java có API đọc file trực tiếp.
// filepath cần được kiểm soát theo chính sách quyền truy cập file riêng.
String content = java.nio.file.Files.readString(
        java.nio.file.Path.of(filepath));
~~~

Khi buộc phải gọi chương trình ngoài, truyền chương trình và từng argument riêng:

~~~java
// Minh họa trên môi trường có lệnh "grep" (thường là Linux/macOS).
// term là một đối số duy nhất, không đi qua shell.
ProcessBuilder pb = new ProcessBuilder(
        "grep", "-F", "--", term, "records.txt");
Process process = pb.start();
int exitCode = process.waitFor();
~~~

Ý nghĩa:
- `grep` là executable cố định do ứng dụng chọn.
- `-F` yêu cầu tìm literal string thay vì regular expression.
- `--` là ký hiệu kết thúc option parsing **nếu chương trình hỗ trợ**; `term` được truyền là một argument riêng.
- `ProcessBuilder` không chạy `sh -c`, nên ký tự điều khiển shell trong một argument không tự trở thành câu lệnh shell khác.

**Nhưng phân tách argv không tự ngăn *argument injection*.** Nếu chương trình đích hiểu một giá trị bắt đầu `-` là option, hoặc nhận input điều khiển cách chọn file/host, nó vẫn có thể thực hiện hành vi không mong muốn. Hãy cố định executable, option và thứ tự; áp dụng allowlist cho lựa chọn có thể đổi cú pháp, xem tài liệu các option của chương trình; dùng `--` khi có hỗ trợ và vẫn kiểm tra đường dẫn, host, quyền thực thi, đầu ra, timeout.

Nếu xử lý file do người dùng chọn, **path traversal** là vấn đề riêng: kiểm soát thư mục gốc, normalize/resolve path và xem xét symbolic link, quyền hệ điều hành. Không coi truyền đường dẫn dưới dạng một argv là đủ để hạn chế truy cập file.

### 6.2. Mẫu thiết kế an toàn

Thay vì cho request gửi cả chuỗi `command`, định nghĩa những thao tác ứng dụng hỗ trợ:

~~~text
Request: { action: "preview", reportId: 42 }
      │
      ▼
Controller kiểm tra quyền với reportId=42
      │
      ▼
Service gọi Java API/đường dẫn cố định,
hoặc xây argv từ action enum + reportId đã kiểm tra
      │
      ▼
Tiến trình chạy với tài khoản quyền tối thiểu và giới hạn tài nguyên
~~~

Không tạo endpoint kiểu `POST /execute?cmd=<chuỗi tùy ý>`. Sự phân biệt giữa **cho chọn thao tác cố định** và **cho cung cấp nguyên lệnh** là cốt lõi của thiết kế.

## 7. LDAP injection: filter argument và tên DN

LDAP filter có cú pháp riêng: `(&(...)(...))`, `(|(...)(...))`, ký tự `*` và dấu ngoặc có ý nghĩa cấu trúc. Nối chuỗi trực tiếp có thể biến giá trị tìm kiếm thành phần của filter:

~~~java
// Không an toàn nếu uid đến từ request.
String filter = "(&(objectClass=person)(uid=" + uid + "))";
~~~

JNDI cung cấp overload **có filter argument**. Snippet sau giả định `DirContext context` đã kết nối LDAP với cơ chế xác thực và kết nối an toàn thích hợp:

~~~java
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

SearchControls controls = new SearchControls();
controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
controls.setReturningAttributes(new String[] {"uid", "cn"});

NamingEnumeration<SearchResult> results = context.search(
        "ou=people,dc=example,dc=com",
        "(&(objectClass=person)(uid={0}))",
        new Object[] {uid},
        controls);
try {
    while (results.hasMore()) {
        SearchResult result = results.next();
        // Xử lý kết quả; vẫn phải kiểm tra quyền với dữ liệu.
    }
} finally {
    results.close();
}
~~~

Trong overload `search(..., filterExpr, Object[] filterArgs, ...)`, provider thực hiện escaping các ký tự đặc biệt trong filter argument theo ngữ cảnh filter. Cú pháp `{0}` ở đây **không phải** `String.format` và cũng không phải JDBC `?`. Phải chọn đúng overload có `Object[]`.

**Phân biệt LDAP filter với Distinguished Name (DN).** Ví dụ giá trị `uid` dùng làm một thành phần DN lại chịu quy tắc escaping khác. Nếu xây DN, sử dụng `javax.naming.ldap.Rdn` và `LdapName` thay vì ghép raw DN string. Không lấy escaping của SQL, HTML hoặc LDAP filter áp dụng sang DN.

Ngay cả khi filter binding an toàn, hãy giới hạn search scope, số kết quả, timeout và tài khoản bind. Đừng cho user tự chọn tùy ý base DN, attribute hoặc toán tử mà không có allowlist.

## 8. XPath injection: biến thay vì nối biểu thức

XPath có cú pháp truy vấn riêng: đường dẫn node, predicate, so sánh, hàm. Một cách sai là nhét giá trị người dùng vào predicate dạng `/users/user[name='...']`; chuỗi có dấu nháy và toán tử có thể đổi nghĩa biểu thức.

JAXP hỗ trợ `XPathVariableResolver`: biểu thức cố định chứa `$needle`, còn dữ liệu gán cho biến.

### 8.1. Chương trình Java 17 tự chạy, không cần thư viện ngoài

Tạo **XPathBindingDemo.java**:

~~~java
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public final class XPathBindingDemo {
    private static String findId(Document doc, String name) throws Exception {
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setXPathVariableResolver(variable ->
                "needle".equals(variable.getLocalPart()) ? name : null);

        // Expression cố định; giá trị name chỉ là XPath variable.
        String id = xpath.evaluate(
                "string(/users/user[name=$needle]/@id)", doc);
        return id.isEmpty() ? "(none)" : id;
    }

    public static void main(String[] args) throws Exception {
        String xml =
                "<users><user id='101'><name>Alice</name></user>"
              + "<user id='102'><name>Bob</name></user></users>";

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));

        System.out.println("Alice=" + findId(doc, "Alice"));
        System.out.println("payload=" + findId(doc, "Alice' or '1'='1"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac XPathBindingDemo.java
java XPathBindingDemo
~~~

Stdout chính xác:

~~~text
Alice=101
payload=(none)
~~~

Payload có dấu nháy và toán tử `or` được xử lý như nguyên một **giá trị chuỗi**, không được parse thành biểu thức XPath. Trong ứng dụng thật, cấu hình parser XML chống XXE/DTD, giới hạn kích thước XML và chọn nguồn XML tin cậy là các yêu cầu **riêng**; binding XPath không tự chống XXE.

Nếu user được quyền chọn đường dẫn hoặc field cần tìm, không bind nó thành cú pháp XPath tùy ý. Dùng mapping chẳng hạn `"name" → biểu thức cố định 1`, `"email" → biểu thức cố định 2`.

## 9. Expression language, template và regex

### 9.1. SpEL/EL: không thực thi biểu thức do người dùng viết

Một antipattern phổ biến:

~~~java
// KHÔNG AN TOÀN: expressionText lấy trực tiếp từ người dùng.
// Đây là snippet Spring SpEL, cần dependency Spring.
ExpressionParser parser = new SpelExpressionParser();
Object value = parser.parseExpression(expressionText).getValue();
~~~

SpEL và nhiều expression engine có khả năng truy cập property, gọi method hoặc tạo object tùy cấu hình. **Không dùng `StandardEvaluationContext` để đánh giá expression lấy từ nguồn không tin cậy.** `SimpleEvaluationContext` thu hẹp tính năng, nhưng vẫn không bảo đảm expression tùy ý do user cung cấp sẽ an toàn.

Thiết kế ưu tiên: ứng dụng quyết định expression/luật hợp lệ; đầu vào chỉ được truyền dưới dạng biến/giá trị vào luật cố định. Nếu sản phẩm thực sự cần “bộ xây dựng luật”, hãy thiết kế grammar nhỏ, parse thành AST có các node và operator cho phép, giới hạn độ sâu/chi phí và dùng evaluator bị giới hạn tính năng, thay vì mở nguyên ngữ pháp SpEL/OGNL/MVEL để client gửi chuỗi tùy ý.

Template engine cũng tương tự: render dữ liệu với cơ chế binding/escaping của engine, không biến nội dung do người dùng cung cấp thành **source template được đánh giá**. Với HTML, *contextual output encoding* chống XSS là lớp bảo vệ riêng; SQL parameterization không chống XSS và HTML escaping không chống SQL injection.

### 9.2. Regex: literal và biểu thức khác nhau

Nếu chức năng là tìm **chuỗi nguyên văn**, đừng dùng đầu vào như một regex không giới hạn. Java hỗ trợ `Pattern.quote` hoặc flag `Pattern.LITERAL`.

Tạo **RegexLiteralDemo.java**:

~~~java
import java.util.regex.Pattern;

public final class RegexLiteralDemo {
    public static void main(String[] args) {
        String input = "a+b";
        Pattern asRegex = Pattern.compile(input);
        Pattern asLiteral = Pattern.compile(Pattern.quote(input));

        System.out.println("regex on a+b=" +
                asRegex.matcher("a+b").matches());
        System.out.println("literal on a+b=" +
                asLiteral.matcher("a+b").matches());
        System.out.println("regex on aaab=" +
                asRegex.matcher("aaab").matches());
    }
}
~~~

~~~bash
javac RegexLiteralDemo.java
java RegexLiteralDemo
~~~

Stdout:

~~~text
regex on a+b=false
literal on a+b=true
regex on aaab=true
~~~

Ở đây `+` là toán tử của regex trong `asRegex`, còn `Pattern.quote` khiến nó chỉ là ký tự dữ liệu. Nếu yêu cầu nghiệp vụ phải chấp nhận regex do user cung cấp, cần kiểm soát tập tính năng, giới hạn độ dài, input size và thời gian thực thi; việc chặn **regex injection** không tự giải quyết **ReDoS** do regex có độ phức tạp cao.

### 9.3. Dữ liệu cấu trúc: JSON/XML/query AST

Khi nhận JSON, xây object/typed DTO và validate field được phép thay vì nối raw text thành một JSON mang cú pháp truy vấn khác. Khi tạo XML, dùng DOM/StAX/SAX API thích hợp thay vì ghép các mảnh tag và attribute có đầu vào tùy ý. Một chuỗi đã “escape cho JSON” có thể vẫn trở thành SQL/LDAP/SpEL nguy hiểm nếu tiếp tục được ghép vào **ngôn ngữ khác** ở bước sau.

Dữ liệu có thể đi qua nhiều lớp: HTTP JSON → Java String → SQL hoặc LDAP filter → template HTML. **Mỗi sink có ngữ pháp và cơ chế bảo vệ khác nhau**, không có một hàm `sanitize(String)` dùng chung cho mọi nơi.

## 10. Validation, escaping, canonicalization và authorization

| Kỹ thuật | Giải quyết gì? | Không giải quyết gì? |
| --- | --- | --- |
| Parameter binding | Giữ một giá trị ở vị trí dữ liệu, không trở thành cú pháp của sink tương ứng | Kiểm tra quyền truy cập, cấu trúc SQL động, resource exhaustion |
| Allowlist cho cú pháp | Cho chọn một tập nhỏ các cột, toán tử, lệnh, field, workflow hợp lệ | Không thay bind cho các giá trị dữ liệu |
| Kiểm tra kiểu, độ dài, phạm vi | Từ chối dữ liệu vô nghĩa/quá lớn, giới hạn chi phí | Không khiến nối chuỗi vào interpreter trở nên an toàn |
| Escaping đúng ngữ cảnh | Mã hóa ký tự đặc biệt khi API không có binding hoặc phục vụ ngữ nghĩa literal | Không thay một API có tham số khi nó tồn tại; dễ sai dialect/ngữ cảnh |
| Least privilege và authorization | Giảm tác động và giới hạn người dùng được phép thao tác trên tài nguyên nào | Không sửa lỗi interpreter đang parse dữ liệu thành cú pháp |
| Timeout/limit/rate limit | Giới hạn lạm dụng và chi phí xử lý | Không thay thế việc tách dữ liệu khỏi lệnh |

**Canonicalization**: nếu hợp đồng API quy định một dạng Unicode, đường dẫn hay URL chuẩn, hãy chuẩn hóa theo quy tắc cụ thể trước khi kiểm tra allowlist. Không tự động decode hoặc thay thế ký tự nhiều lần; việc decode hai lần ở các lớp khác nhau có thể làm lọt chuỗi qua bộ lọc. Đừng “xóa hết ký tự xấu” vì làm mất dữ liệu hợp lệ mà vẫn có thể sót cú pháp nguy hiểm.

**Authorization là kiểm tra theo từng đối tượng**, ví dụ tài khoản A không được truy cập hóa đơn của B dù SQL đều dùng parameter. Đừng nhầm “truy vấn không bị injection” với “người gọi được phép xem dữ liệu”.

**Nơi giữ dữ liệu bí mật:** không log mật khẩu, token, chuỗi kết nối hoặc toàn bộ câu SQL đã ghép giá trị nhạy cảm; ưu tiên log template, thời gian chạy, loại truy vấn và mã lỗi đã được lọc.

## 11. Kiểm thử âm tính và tiêu chí chấp nhận

Các kiểm thử cần bảo vệ được **cú pháp cố định**, **phạm vi dữ liệu** và **quyền thực thi**. Nên kiểm thử cả ở service layer lẫn integration test tại driver/interpreter thật.

| Sink | Giá trị kiểm thử | Kỳ vọng |
| --- | --- | --- |
| SQL equality | `alice' OR '1'='1` | Được bind thành một username duy nhất; không mở rộng kết quả |
| SQL identifier | `sortBy` không nằm trong enum/map | Từ chối hoặc dùng mặc định đã định nghĩa rõ; không nối raw field |
| SQL `LIKE` literal | `100%_` | Nếu tìm literal, wildcard được escape theo dialect; tham số vẫn được bind |
| SQL `IN` | Danh sách rỗng, 1 phần tử, nhiều phần tử, quá hạn mức | Query hợp lệ hoặc từ chối theo policy; không nối ID thành SQL text |
| LDAP filter | `*)(|(uid=*))` | Chuỗi được xem là giá trị filter argument; không mở rộng điều kiện tìm kiếm |
| XPath | `Alice' or '1'='1` | Không chuyển thành toán tử `or`; kết quả đúng với so sánh literal |
| Regex literal | `a+b`, `.*` | Matcher hiểu ký tự nguyên văn nếu hợp đồng yêu cầu literal |
| OS process | Đối số bắt đầu bằng `-` hoặc chứa dấu cách/dấu chấm phẩy | Không đi qua shell; option/path được ràng buộc theo quy tắc chương trình |
| Phân quyền | ID bản ghi có tồn tại nhưng thuộc người khác | Trả từ chối hoặc không thấy dữ liệu, dù truy vấn là parameterized |
| Second-order | Giá trị được lưu trước rồi dùng làm filter/lệnh sau đó | Mọi lần tới sink đều bind hoặc dùng API cấu trúc đúng ngữ cảnh |

Mẫu kiểm thử cho logic SQL (pseudocode, cần repository và database test thực tế):

~~~java
// Khi username chứa ký tự SQL, query vẫn chỉ tìm username bằng đúng chuỗi đó.
assertEquals(0, accountRepository.countByUsername("alice' OR '1'='1"));

// Sort không thuộc allowlist bị từ chối.
assertThrows(IllegalArgumentException.class,
        () -> queryService.findAll("username; DROP TABLE accounts"));
~~~

Đây là **pseudocode kiểm thử theo JUnit**, không phải file Java độc lập: tên repository/service và setup JUnit cần được định nghĩa trong dự án. Không cần gửi chuỗi thử nghiệm đến dữ liệu sản xuất.

Với integration test, xác minh số hàng bị ảnh hưởng, quyền DB user, transaction rollback, tốc độ chạy và trường hợp đầu vào đặc biệt hợp lệ (Unicode, apostrophe trong tên người, ký tự `%`) để tránh “sửa lỗ hổng” bằng cách cấm dữ liệu hợp lệ.

## 12. Mùi mã cần phát hiện khi review

Những mẫu sau đáng được truy vết từ đầu vào đến sink; bản thân một dấu `+` không phải lúc nào cũng là lỗ hổng, nhưng phải xác định thứ gì đang được nối:

- `"... WHERE name = '" + input + "'"` hoặc `String.format("SELECT ... %s", input)`.
- `connection.prepareStatement(sqlFromStringConcatenation)`.
- `entityManager.createQuery("... " + userValue)` hoặc ORM query cho phép fragment do client chọn.
- `new ProcessBuilder("sh", "-c", userControlledCommand)`; `cmd /c`; executable/option do client chỉ định.
- `ctx.search(base, "(uid=" + uid + ")", controls)` mà không dùng overload có `filterArgs`.
- `xpath.evaluate("/users/user[name='" + name + "']", doc)`.
- `parser.parseExpression(request.getParameter("expr"))`.
- `Pattern.compile(userText)` khi chức năng chỉ yêu cầu so khớp literal.
- `jsonQuery = "{... " + clientInput + " ...}"` hoặc chuyển trực tiếp object query chứa operator tùy ý từ client sang driver.

Câu hỏi review nên đặt: **Nguồn dữ liệu đến từ đâu? Nó trở thành syntax ở đâu? Có API binding/AST không? Phần nào phải dùng allowlist? Người gọi có quyền trên đối tượng kết quả không?**

## 13. Những ngộ nhận thường gặp

**“Tôi đã escape dấu nháy đơn, vậy SQL an toàn.”** Không đủ. Escape theo dialect, encoding và chế độ DB rất dễ sai, đặc biệt trong SQL động nhiều ngữ cảnh. Ưu tiên parameterization cho giá trị.

**“Dữ liệu đã validate chỉ còn chữ số, nên nối vào query cũng được.”** Chỉ cho phép chữ số có thể giảm nguy cơ trong một sink cụ thể, nhưng không chứng minh ranh giới code/data được tách, và có thể bỏ qua lỗi kiểu, phạm vi, integer overflow hoặc quyền truy cập. Bind theo đúng kiểu.

**“Có PreparedStatement là an toàn.”** Không nếu bạn nối giá trị trước khi prepare hoặc dùng raw SQL fragments trong ORM/procedure.

**“Có dấu `?` nên bind được tên bảng, cột và `ORDER BY`.”** Không. Dùng mapping cú pháp cố định; bind các giá trị riêng.

**“ProcessBuilder luôn an toàn.”** Không. Nó giúp truyền argv riêng, nhưng vẫn có *argument injection*, lệnh con nguy hiểm, path traversal, lỗ hổng chương trình đích và quyền thực thi quá rộng.

**“SimpleEvaluationContext cho phép chạy mọi SpEL của user.”** Không. Đây chỉ là bộ context ít tính năng hơn; Spring cảnh báo expression từ nguồn không tin cậy vẫn có thể nguy hiểm. Tránh thiết kế cho người dùng gửi nguyên SpEL/EL.

**“HTML escaping bảo vệ cả SQL và shell.”** Không. Mỗi interpreter có một ngữ cảnh; escaping sai ngữ cảnh có thể vô hiệu.

**“Dữ liệu do hệ thống gửi qua message hoặc lấy lại từ DB thì trusted.”** Không nhất thiết. Tính tin cậy thuộc về nguồn, quyền kiểm soát và đường đi cụ thể, không phải vị trí lưu trữ tạm thời.

## 14. Checklist trước khi merge/deploy

1. Xác định **mọi sink** có khả năng thực thi SQL, shell, LDAP, XPath, expression engine, regex và query DSL.
2. Thay nối chuỗi ở **vị trí giá trị** bằng API bind/typed builder; test được đầu vào có cú pháp đặc biệt.
3. Với **syntax không bind được**, triển khai mapping/enum/allowlist do ứng dụng sở hữu; từ chối option lạ.
4. Với SQL, kiểm tra cả query trong ORM, stored procedure, raw native SQL, `LIKE` và `IN`.
5. Với OS process, ưu tiên Java API; nếu phải spawn, cố định executable và arguments, không đi qua shell; kiểm soát quyền, path, timeout và stdout/stderr.
6. Với LDAP, dùng `filterArgs` và phân biệt filter escaping với DN escaping.
7. Với XPath và expression engine, cố định biểu thức, bind giá trị; không đánh giá expression tùy ý của user.
8. Kiểm tra input type/length, giới hạn số kết quả và chi phí; bảo vệ bí mật và áp dụng least privilege.
9. Chạy integration test với payload mẫu và dữ liệu hợp lệ chứa ký tự đặc biệt; thêm test authorization, second-order injection.
10. Xem lại telemetry/error: không tiết lộ query nội bộ, credential hoặc dữ liệu riêng tư trong thông báo lỗi.

## 15. Câu hỏi tự kiểm tra

**1. Có thể ghép `"ORDER BY " + sortBy` nếu `sortBy` đến từ HTTP request không?** Không. Mapping tên sắp xếp từ request sang fragment cố định trong code là cách phù hợp khi DB không hỗ trợ bind tên cột như một giá trị.

**2. Tại sao `PreparedStatement` vẫn có thể bị SQL injection?** Vì lớp này không sửa được câu SQL đã bị ghép đầu vào vào syntax *trước khi* gọi `prepareStatement`.

**3. Khi nào `Pattern.quote` cần thiết?** Khi nghiệp vụ mong muốn so khớp chuỗi literal nhưng API nhận tham số dạng regex. Nó không phải phương pháp tổng quát để sandbox regex do người dùng viết.

**4. `ProcessBuilder("tool", input)` đã đủ an toàn chưa?** Chưa. Argument của `input` không được shell phân tích nếu không cố ý dùng shell, nhưng chương trình `tool` vẫn có thể coi đầu vào là option, path hoặc URI nguy hiểm. Cần kiểm soát hành vi tool và đầu vào.

**5. Khác biệt giữa LDAP `filterArgs` và XPath variable là gì?** Chúng thuộc hai ngôn ngữ khác nhau nhưng cùng giữ giá trị không đáng tin tách khỏi cú pháp truy vấn do ứng dụng kiểm soát.

**6. Nếu request gửi bản ghi ID=42 và SQL sử dụng `WHERE id = ?` thì chắc chắn không lộ dữ liệu?** Không. SQL injection được chặn nhưng vẫn phải kiểm tra quyền người gọi với ID=42.

**7. Tại sao dữ liệu đã lưu cũng cần parameterization khi đọc ra và dùng tiếp?** Vì nơi lưu trữ không triệt tiêu khả năng dữ liệu có cú pháp độc hại; mỗi lần chuyển sang một interpreter mới đều là một ranh giới code/data mới.

## Tài liệu đối chiếu

- OWASP — SQL Injection Prevention Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html
- OWASP — Injection Prevention Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Injection_Prevention_Cheat_Sheet.html
- OWASP — OS Command Injection Defense Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/OS_Command_Injection_Defense_Cheat_Sheet.html
- Java SE 17 — `PreparedStatement`: https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/PreparedStatement.html
- Java SE 17 — `ProcessBuilder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.html
- Java SE 17 — `DirContext.search`: https://docs.oracle.com/en/java/javase/17/docs/api/java.naming/javax/naming/directory/DirContext.html
- Java SE 17 — `XPath`/`XPathVariableResolver`: https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/xpath/XPath.html
- Java SE 17 — `Pattern.quote`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
- Spring Framework — SpEL EvaluationContext và Security Considerations: https://docs.spring.io/spring-framework/reference/core/expressions/evaluation.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-004">21.4 Đường dẫn, tệp tạm, symlink và điều kiện tranh chấp khi truy cập tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# 21.4. Đường dẫn, tệp tạm, symlink và điều kiện tranh chấp khi truy cập tài nguyên trong Java

## 1. Vì sao đường dẫn tệp là một ranh giới bảo mật?

Một ứng dụng Java có thể nhận tên tệp từ URL, tham số HTTP, nội dung ZIP, cấu hình, dữ liệu hàng đợi, tên bản đính kèm hoặc request nội bộ. Nếu dữ liệu đó được nối trực tiếp vào đường dẫn để **đọc, ghi, giải nén, thay thế hoặc xóa**, người điều khiển đầu vào có thể khiến ứng dụng sử dụng tài nguyên không được phép. Nguy cơ còn lớn hơn khi tiến trình Java có quyền đọc/ghi file hệ thống hoặc dữ liệu của nhiều người dùng.

Bốn nhóm vấn đề cốt lõi:

1. **Path traversal:** đầu vào chứa `..`, đường dẫn tuyệt đối, root khác, hoặc dạng đường dẫn đặc thù hệ điều hành để thoát khỏi thư mục cho phép.
2. **Symbolic link (symlink) và alias:** một tên bên trong thư mục được phép có thể trỏ tới tài nguyên **nằm bên ngoài**. Trên một số hệ thống còn có hard link, junction/reparse point hoặc mount point làm phức tạp cách xác định đích.
3. **Temporary-file attack:** sử dụng tên tạm đoán được, thư mục tạm chia sẻ hoặc quyền truy cập quá rộng khiến dữ liệu bị đọc, thay thế hay ghi đè ngoài ý muốn.
4. **TOCTOU (time-of-check to time-of-use):** trạng thái tệp hoặc thư mục thay đổi **giữa bước kiểm tra và bước sử dụng**. Việc kiểm tra đúng tại thời điểm A không tự bảo vệ thao tác tại thời điểm B.

**Mô hình đe dọa luôn phải rõ:** ai kiểm soát tên, ai có quyền sửa entry trong thư mục, ai có thể tạo link hoặc thay tệp, ứng dụng dùng quyền hệ điều hành nào và liệu file system là cục bộ, mạng, container volume hay provider tùy biến. Nếu attacker có thể sửa parent directory, chiến lược chỉ dựa vào chuỗi đường dẫn thường không đủ.

Ví dụ sai khi nhận `name` từ request:

```java
Path target = Path.of("/srv/app/reports").resolve(name);
byte[] content = Files.readAllBytes(target);
```

Đây là **đoạn minh họa không độc lập**. Với `name="../config/secrets.yml"`, việc `resolve` không kiểm soát quyền truy cập. Với một đường dẫn tuyệt đối, `Path.resolve` có thể **bỏ qua base** theo ngữ nghĩa của provider. Ngay cả `name="summary.csv"` cũng có thể là symlink dẫn ra ngoài thư mục nếu attacker được tạo entry ở đó.

### Ranh giới tin cậy

- **Base/trusted root:** thư mục ứng dụng định trước và có quyền hệ điều hành phù hợp; đường dẫn của nó phải xuất phát từ cấu hình đáng tin cậy.
- **User-controlled path:** tên hoặc đường dẫn đi vào từ bên ngoài; coi là dữ liệu chưa tin cậy kể cả request đến từ dịch vụ nội bộ.
- **Directory-entry control:** quyền tạo, đổi tên, xóa tệp/link trong parent directory. Đây là khả năng quan trọng trong tấn công symlink/race; quyền sửa file và quyền sửa directory không giống nhau.
- **Operation semantics:** `READ`, `WRITE`, `CREATE_NEW`, `TRUNCATE_EXISTING`, `DELETE`, `MOVE` có hậu quả khác nhau; mã ghi đè cần chính sách nghiêm ngặt hơn đọc.

## 2. Hiểu đúng `Path`, `normalize()`, `toRealPath()` và `startsWith()`

Java NIO.2 trong `java.nio.file` làm việc với `Path` cùng `Files`. Cần phân biệt **xử lý cú pháp đường dẫn** và **truy cập hệ thống tệp thực**:

| API | Điều nó thực hiện | Điều nó không bảo đảm |
| --- | --- | --- |
| `Path.of(text)` | Parse đường dẫn theo provider/hệ điều hành hiện tại | Không xác minh tính an toàn, tồn tại, quyền truy cập hay đích symlink |
| `base.resolve(child)` | Ghép đường dẫn theo quy tắc của `Path` | Không bắt buộc `child` phải nằm dưới `base`; đường dẫn tuyệt đối có thể thay thế base |
| `path.normalize()` | Loại bỏ phần dư như `.` và giải quyết `..` **theo cú pháp** | Không truy cập file system và không giải quyết symlink |
| `path.toAbsolutePath()` | Tạo biểu diễn tuyệt đối theo môi trường hiện hành | Không giải quyết symlink, không bảo vệ khỏi race |
| `path.toRealPath()` | Xác định đường dẫn thực của **đối tượng đang tồn tại**, mặc định đi theo symlink | Không giữ cố định đối tượng cho thao tác sau; kết quả có thể lỗi thời ngay lập tức |
| `candidate.startsWith(base)` với cả hai là `Path` | Kiểm tra tiền tố theo **thành phần đường dẫn** | Không chứng minh đường dẫn thực ở trong base nếu có link/race |

Ví dụ lexical containment kiểm tra đúng kiểu đối tượng:

```java
Path base = Path.of("/srv/app/reports").toAbsolutePath().normalize();
Path input = Path.of(userProvidedName);
if (input.isAbsolute() || input.getRoot() != null) {
    throw new IllegalArgumentException("Absolute/rooted path denied");
}
Path candidate = base.resolve(input).normalize();
if (!candidate.startsWith(base) || candidate.equals(base)) {
    throw new IllegalArgumentException("Path outside report directory");
}
```

Đây là **mẫu kiểm tra cú pháp**, chưa đủ để bảo vệ thao tác trên directory tree mà attacker có thể sửa. Nếu `reports/2026` là symlink trỏ ra `/etc` hoặc thư mục bên dưới bị thay giữa kiểm tra và mở file, lexical containment vẫn có thể hợp lệ nhưng file thực lại nằm ngoài base.

**Không dùng `candidate.toString().startsWith(base.toString())`:** chuỗi `/srv/app/reports-evil` bắt đầu bằng `/srv/app/reports` nhưng không phải thư mục con. `Path.startsWith` so sánh theo phân đoạn và provider. Không lấy path chưa `normalize` để so `..`; cũng không nghĩ chỉ thêm `normalize` là chặn được symlink.

### Khác biệt giữa Linux/macOS và Windows

- Dấu phân cách và root phụ thuộc provider. Linux có `/`; Windows có ổ đĩa, UNC, cú pháp drive-relative như `C:secret.txt`. Một đường dẫn Windows drive-relative không nhất thiết được `isAbsolute()` phân loại là tuyệt đối; vì vậy kiểm tra thêm `getRoot()` hoặc dùng **allowlist một segment**.
- Ký tự `\` có thể là dấu phân cách trên Windows nhưng là ký tự tên hợp lệ trên một số Unix file system; tên gửi từ client không nên được diễn giải khác nhau mà không có chính sách nhất quán.
- File system có thể phân biệt hoặc không phân biệt chữ hoa/chữ thường, áp dụng quy tắc Unicode khác nhau, hỗ trợ ACL khác nhau, hoặc có các tên dành riêng. Không tự đổi tên bằng `toLowerCase()` rồi coi là đã kiểm tra an toàn.
- Nếu tên tệp là định danh nghiệp vụ, ưu tiên **ID do server sinh** và một bảng ánh xạ ID → đối tượng; tránh phụ thuộc trực tiếp vào path string do người dùng gửi.

### Khi nào nên dùng `toRealPath()`?

Với các thư mục do ứng dụng **độc quyền quản lý và không bị attacker sửa**, `toRealPath()` giúp quan sát và so sánh đích thật:

```java
Path realBase = trustedRoot.toRealPath();
Path realCandidate = candidate.toRealPath();
if (!realCandidate.startsWith(realBase)) {
    throw new SecurityException("Resolved outside trusted root");
}
```

Nó chỉ là **kiểm tra tại một thời điểm**: giữa `toRealPath()` và `Files.readAllBytes(...)`, entry có thể bị thay. `toRealPath()` cần đối tượng tồn tại nên không phải công cụ phù hợp để quyết định trước tên tệp sắp tạo. Với `NOFOLLOW_LINKS`, ngữ nghĩa giải quyết symlink thay đổi; đừng coi nó là xác minh containment toàn bộ cây.

## 3. Symlink, hard link và việc theo liên kết

Symlink là một entry có nội dung trỏ tới đường dẫn khác. Khi Java truy cập qua nhiều API của `Files`, symlink thường được **follow theo mặc định**, trừ khi API/options quy định khác. Có hai vị trí cần xem xét:

- **Link ở thành phần cuối:** `reports/result.csv` là symlink trỏ tới `/private/key.txt`.
- **Link ở thành phần cha:** `reports/tenant-a/summary.csv` trong đó `tenant-a` là symlink tới thư mục ngoài vùng tin cậy.

`Files.isSymbolicLink(path)` cho biết trạng thái **tại thời điểm gọi**; không giữ entry bất biến. `Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)` đọc thuộc tính của link thay vì target (nếu có), nhưng việc đọc thuộc tính và mở nội dung vẫn là hai thao tác riêng biệt. `Files.exists(path, NOFOLLOW_LINKS)` không giải quyết TOCTOU.

### `LinkOption.NOFOLLOW_LINKS` giải quyết được gì?

`LinkOption.NOFOLLOW_LINKS` áp dụng cho API hỗ trợ nó. Ví dụ, khi mở bằng:

```java
try (SeekableByteChannel in = Files.newByteChannel(
        file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
    // Đọc từ channel đã mở.
}
```

Trên provider tuân thủ hợp đồng tương ứng, tùy chọn này chặn việc đi theo **symlink ở entry cuối** khi mở. Nhưng nó **không tự khóa các directory ancestor**, không chặn hard link, không thay thế ACL và không bảo vệ một đường dẫn gốc mà attacker đang có quyền đổi tên. Nếu provider không hỗ trợ option hoặc không cung cấp ngữ nghĩa cần thiết, ứng dụng bảo mật phải **từ chối** thay vì im lặng bỏ qua.

**Hard link** là một tên khác của cùng đối tượng tệp, không phải symlink. Vì vậy `isSymbolicLink` và `NOFOLLOW_LINKS` không phát hiện mọi hard link. Trên Windows, junction/reparse point và các cơ chế redirection cũng cần kiểm thử đúng provider. Giới hạn quyền trên các parent directory và chỉ tin cậy vùng lưu trữ do ứng dụng kiểm soát là phần thiết yếu của thiết kế.

### Những cách “bảo vệ” không đủ

```java
// KHÔNG an toàn nếu attacker có quyền thay đổi entry trong directory:
if (!Files.isSymbolicLink(file) && Files.isRegularFile(file)) {
    return Files.readString(file); // Entry có thể bị tráo sau kiểm tra.
}
```

Tương tự, việc so sánh `BasicFileAttributes.fileKey()` từ hai lần đọc có thể giúp **chẩn đoán** thay đổi danh tính tệp khi provider hỗ trợ, nhưng không phải authorization gắn chặt với thao tác mở file. Khi có attacker chủ động cạnh tranh, cần giảm số thao tác phụ thuộc tên và dùng directory handle/handle file được mở từ anchor tin cậy.

## 4. TOCTOU: tại sao “kiểm tra trước rồi dùng” thất bại?

**Time-of-check to time-of-use** là cửa sổ giữa kết quả kiểm tra và hành động dùng tài nguyên. Trên file system, một tiến trình khác có thể tạo, xóa, đổi tên, thay symlink hoặc sửa dữ liệu trong khoảng này. Ngay cả trong cùng JVM, thread khác vẫn có thể làm thay đổi trạng thái.

Ví dụ điển hình:

```java
if (!Files.exists(target)) {
    Files.writeString(target, payload);
}
```

Diễn tiến có thể là:

```text
Thời điểm  | Tiến trình Java                       | Attacker
-----------|---------------------------------------|------------------------------------
T1         | Files.exists(target) -> false         |
T2         |                                       | Tạo target là symlink tới tài nguyên khác
T3         | Files.writeString(target, payload)    | Theo đường dẫn mới; có thể ghi sai đối tượng
```

`Files.exists` và `Files.notExists` đều có thể trả `false` khi không xác định được trạng thái; chúng không phải hai phép phủ định hoàn hảo. Kết quả `exists`, `isRegularFile`, `isReadable` hay `toRealPath` **hết giá trị bảo đảm ngay sau thao tác kiểm tra**. Không thể sửa race chỉ bằng cách gọi thêm nhiều lần những API này.

### Thay check-then-create bằng thao tác tạo nguyên tử

Khi yêu cầu **chỉ tạo mới, tuyệt đối không ghi đè**, dùng `StandardOpenOption.CREATE_NEW` cùng `WRITE` và giữ **channel trả về** để ghi:

```java
try (SeekableByteChannel out = Files.newByteChannel(
        target, Set.of(StandardOpenOption.WRITE,
                       StandardOpenOption.CREATE_NEW,
                       LinkOption.NOFOLLOW_LINKS))) {
    out.write(StandardCharsets.UTF_8.encode(payload));
}
```

Theo tài liệu JDK, `CREATE_NEW` làm việc kiểm tra sự tồn tại và tạo file trở thành **một thao tác nguyên tử** đối với các thao tác file system khác; nó thất bại nếu tên đã tồn tại hoặc là symbolic link. Điều này giải quyết race ở **entry cuối khi tạo mới**. Nó không tự bảo vệ các thành phần cha bị tráo, không cấm một tiến trình khác sửa nội dung qua đường dẫn khác hoặc thay chính entry đó sau khi tạo. Vì vậy phải kết hợp với **trusted directory có quyền phù hợp**.

Nếu file đã tồn tại và cần **đọc**, mở kênh đúng option rồi xử lý trên handle; tránh kiểm tra `exists` hoặc `isRegularFile` trước rồi mở bằng một API khác như bằng chứng duy nhất. Nếu file cần **cập nhật**, chính sách xung đột, quyền sửa và atomic replace phải được thiết kế riêng.

### Bảng chiến lược theo thao tác

| Tình huống | Rủi ro thường gặp | Cách xử lý có cơ sở |
| --- | --- | --- |
| Tạo mới tên do ứng dụng quyết định | Hai tiến trình chọn cùng tên; overwrite | `CREATE_NEW` hoặc `Files.createFile`; parent directory đáng tin |
| Tệp tạm có dữ liệu nhạy cảm | Tên đoán được, temp chia sẻ, quyền rộng | `Files.createTempFile` trong private directory, quyền/ACL lúc tạo khi hỗ trợ |
| Đọc tên từ người dùng | `..`, absolute path, link, thay entry | Ánh xạ ID/allowlist + root tin cậy; mở qua anchor/handle an toàn nếu directory không ổn định |
| Duyệt nhiều cấp thư mục có khả năng bị tráo | Intermediate symlink và rename race | `SecureDirectoryStream` và bước xuống từng directory không follow, nếu provider hỗ trợ; nếu không, fail closed/thiết kế lại |
| Thay thế bản xuất báo cáo | File ghi dở, ghi đè nhầm bản cùng tên | Ghi staging trên cùng FileStore, kiểm tra quyền và chính sách replacement; chọn move/atomic move đúng ngữ nghĩa provider |
| Xóa dữ liệu theo tên | Entry bị tráo giữa tìm và xóa | Ràng buộc quyền thư mục, dùng operation tương đối với directory handle khi có; không tin vào đường dẫn đã xác minh từ trước |

## 5. Tệp tạm: tạo đúng cách và quản lý vòng đời

Một cách dễ sai là tự ghép tên tạm từ thời gian, PID hoặc tên người dùng, sau đó dùng `Files.exists` để kiểm tra rồi `Files.writeString`. Tên đoán được và thư mục dùng chung tạo cơ hội cho **pre-create**, symlink swap hoặc việc vô tình lộ dữ liệu.

**Ưu tiên API sinh và tạo tệp nguyên tử:**

- `Files.createTempFile(dir, "prefix-", ".tmp")`: tạo **tệp mới thực sự**, trả về `Path`, thất bại nếu không tạo được. Tên được sinh theo implementation; không cần tự đoán độ khó đoán của tên.
- `Files.createTempDirectory(parent, "job-")`: tạo directory mới cho một job/session, thuận tiện cô lập các tệp trung gian. Nó không chứng minh quyền truy cập của directory riêng tư theo mọi provider.
- `Files.createFile(path)` hoặc `newByteChannel(path, CREATE_NEW, WRITE)`: phù hợp khi cần một tên cụ thể và phải từ chối overwrite.
- `Files.createDirectories(...)` tạo các thành phần còn thiếu dần, **không phải một giao dịch nguyên tử trên toàn cây**; không gọi nó để tin rằng parent do attacker kiểm soát bỗng an toàn.

### Quyền truy cập khi tạo, không chờ “chmod” sau đó

Trên file system hỗ trợ POSIX, có thể yêu cầu quyền tại lúc tạo:

```java
FileAttribute<Set<PosixFilePermission>> ownerOnly =
        PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------"));
Path temp = Files.createTempFile(privateDir, "secret-", ".part", ownerOnly);
```

Đây là **đoạn tích hợp POSIX**, cần import `java.nio.file.attribute.*` và `java.util.Set`, cùng `Path/Files`. Thuộc tính được yêu cầu thiết lập **atomically khi tạo**, nếu provider không hỗ trợ có thể ném `UnsupportedOperationException`. Trên Windows/ACL hoặc provider khác cần phương án quyền tương đương; không dùng POSIX API như thể chạy được ở mọi nền tảng. Với directory POSIX riêng tư thường yêu cầu `rwx------` cho owner; một tệp `rw-------` không thay thế việc bảo vệ directory chứa nó.

**Không dựa vào** mặc định `java.io.tmpdir` để giữ bí mật. Thư mục temp hệ thống thường có phạm vi chia sẻ; ngay cả khi tên tạm khó đoán, quyền tệp, quyền directory, chính sách của service account, container mounts và khả năng đọc của user khác vẫn cần kiểm tra. Đối với dữ liệu rất nhạy cảm, cân nhắc xử lý trong bộ nhớ, filesystem mã hóa hoặc kênh lưu trữ được bảo vệ thay vì lưu plaintext tạm.

### Dọn dẹp

Sử dụng `try/finally` hoặc `try-with-resources` cho handle, sau đó xóa các tệp tạm **chỉ trong directory thuộc quyền quản lý của job**. Đừng xóa đệ quy một đường dẫn bắt nguồn từ request hoặc một cây có entry do attacker có thể kiểm soát mà không có thiết kế chuyên biệt.

`DELETE_ON_CLOSE` là best effort phụ thuộc provider; `File.deleteOnExit()` giữ danh sách đến lúc JVM dừng, có thể gây tích lũy với service chạy lâu, và không xử lý an toàn mọi tình huống dừng bất thường. Với tiến trình server, cần cleanup chủ động và cơ chế dọn rác tệp mồ côi có giới hạn thời gian, phạm vi và owner đã xác minh.

## 6. Chương trình Java 17+ chạy được: path policy và tạo tệp nguyên tử

Bài thực hành sau sử dụng **chính sách chỉ cho phép một tên file đơn** từ đầu vào và một directory tạm mới cho mỗi lượt chạy. Tên hợp lệ chỉ chứa ASCII chữ/số, dấu chấm, gạch dưới, gạch ngang; bắt đầu bằng chữ/số và dài tối đa 64 ký tự. Đây là **quy tắc của ví dụ**, không phải chuẩn tên file toàn cầu. Nó loại bỏ dấu phân cách, `..` ở dạng parent segment, đường dẫn tuyệt đối, drive-relative và nhiều ký tự hệ điều hành đặc thù.

Chương trình không thực hiện network request, không thử symlink attack và không tự chứng minh directory có quyền/ACL phù hợp trên mọi hệ điều hành. `CREATE_NEW` và `NOFOLLOW_LINKS` minh họa thao tác tạo file an toàn hơn trong một **directory được kiểm soát**.

Tạo tệp `SafePathTempDemo.java`:

```java
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;

public final class SafePathTempDemo {
    private static boolean safeLeaf(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            return false;
        }
        Path part = Path.of(raw);
        return !part.isAbsolute()
                && part.getRoot() == null
                && part.getNameCount() == 1
                && raw.equals(part.getFileName().toString());
    }

    public static void main(String[] args) throws Exception {
        System.out.println("report-allowed=" + safeLeaf("report.txt"));
        System.out.println("parent-blocked=" + !safeLeaf("../secret.txt"));
        System.out.println("absolute-blocked=" + !safeLeaf("/etc/passwd"));
        System.out.println("drive-blocked=" + !safeLeaf("C:secret.txt"));

        Path work = Files.createTempDirectory("safe-path-demo-");
        Path file = work.resolve("report.txt");
        Path staging = null;
        try {
            try (SeekableByteChannel channel = Files.newByteChannel(
                    file, Set.of(StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE_NEW,
                            LinkOption.NOFOLLOW_LINKS))) {
                channel.write(StandardCharsets.UTF_8.encode("data"));
            }
            staging = Files.createTempFile(work, "stage-", ".tmp");
            System.out.println("created=" +
                    Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS));
            System.out.println("temp-created=" +
                    Files.isRegularFile(staging, LinkOption.NOFOLLOW_LINKS));
        } finally {
            if (staging != null) Files.deleteIfExists(staging);
            Files.deleteIfExists(file);
            Files.deleteIfExists(work);
        }
    }
}
```

Biên dịch/chạy bằng JDK 17 hoặc cao hơn:

```bash
javac -encoding UTF-8 SafePathTempDemo.java
java SafePathTempDemo
```

Stdout dự kiến **đã kiểm thử trên JDK**; tên directory/tệp sinh tự động không được in nên kết quả ổn định:

```text
report-allowed=true
parent-blocked=true
absolute-blocked=true
drive-blocked=true
created=true
temp-created=true
```

**Giải thích quan trọng:**

1. `safeLeaf` chỉ xác minh **tên đơn** trước khi ghép. Ứng dụng thực vẫn cần giới hạn số lượng, kích thước, loại nội dung, quyền theo người dùng và tránh đụng độ tên.
2. `Files.createTempDirectory` tạo directory mới trong temp hệ thống. Trước khi lưu **bí mật thật**, kiểm tra quyền/ACL của directory và chính sách temp root trên nền tảng triển khai.
3. `Files.newByteChannel(..., CREATE_NEW, WRITE, NOFOLLOW_LINKS)` tạo và mở file bằng một hành động; ghi qua channel đó tránh một lần đóng-mở theo tên trong luồng tạo mới.
4. `Files.createTempFile(work, ...)` tạo file staging bằng API dành riêng cho temp, tránh kiểm tra `exists` trước khi tạo.
5. `Files.isRegularFile(...)` ở ví dụ chỉ để **in trạng thái sau thao tác**, không được dùng như bước authorization trước một thao tác khác.
6. `finally` giải phóng tài nguyên sau khi hoàn thành. Dọn dẹp dựa trên tên trong directory riêng vẫn giả định directory không bị actor khác sửa; nếu có cạnh tranh hoặc bị thay parent thì cần thiết kế dọn dẹp bằng handle/quyền chặt chẽ hơn.

## 7. Khi có attacker thay entry: `SecureDirectoryStream`

Java NIO.2 cung cấp `SecureDirectoryStream<Path>`, cho phép một số thao tác **tương đối với một directory đã được mở**, thay vì liên tục phân giải đường dẫn đầy đủ từ đầu. Oracle mô tả API này dành cho ứng dụng cần duyệt cây hoặc thao tác tránh race. Việc có thể dùng hay không **phụ thuộc hệ điều hành và file-system provider**; `Files.newDirectoryStream(dir)` không bảo đảm luôn trả `SecureDirectoryStream`.

Ví dụ sau là **class hỗ trợ có thể biên dịch**, không có `main` và không giả định provider nào cũng hỗ trợ. Hàm mở tên file đơn trong directory tin cậy và đọc tối đa 32 byte đầu tiên, kiểm tra `NOFOLLOW_LINKS` ngay ở thao tác mở:

```java
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.util.Set;

final class SecureReadExample {
    static int readHeader(Path trustedDir, String leaf) throws IOException {
        if (!leaf.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Invalid leaf");
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(trustedDir)) {
            if (!(ds instanceof SecureDirectoryStream<?>)) {
                throw new IOException("SecureDirectoryStream unavailable");
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) ds;
            try (SeekableByteChannel in = secure.newByteChannel(
                    Path.of(leaf),
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                return in.read(ByteBuffer.allocate(32));
            }
        }
    }
}
```

Các điểm cần hiểu:

- `SecureDirectoryStream.newByteChannel(relativeName, ...)` mở **tương đối với directory handle** thay vì tra cứu lại toàn bộ đường dẫn `trustedDir/name` sau khi directory đã được mở. `NOFOLLOW_LINKS` yêu cầu thất bại nếu entry cuối là symlink.
- Dùng `instanceof SecureDirectoryStream<?>` để kiểm tra khả năng cung cấp. Nếu môi trường không hỗ trợ, **fail closed** hoặc chuyển sang thiết kế lưu trữ/permission khác đã được đánh giá, không giả vờ có cùng bảo đảm khi fallback sang `Files.readAllBytes(path)`.
- Với cây **nhiều cấp**, có thể mở từng directory con qua `secure.newDirectoryStream(relativeComponent, NOFOLLOW_LINKS)` để tránh theo symlink ở từng bước, sau đó xử lý file tương đối với handle directory con. Chỉ truyền component đã được kiểm soát, không truyền lại path tự do gồm nhiều segment rồi nghĩ mọi segment được bảo vệ.
- **Anchor ban đầu vẫn phải đáng tin cậy.** Nếu attacker thay được `trustedDir` hoặc parent trước khi gọi `Files.newDirectoryStream(trustedDir)`, bạn vẫn có thể mở nhầm directory. Dùng cấu hình tin cậy và quyền hệ điều hành chặn thay thế parent; trong threat model mạnh hơn có thể cần native OS APIs/môi trường sandbox hỗ trợ.
- API không tự giải quyết hard link, file content bị ghi đồng thời, thay đổi quyền truy cập sau khi mở, thay thế dữ liệu bởi actor có quyền trên handle, hay mọi cơ chế alias trên mọi provider. Đây là lớp giảm race trong phạm vi tương đối của directory handle, không phải một sandbox tổng quát.

## 8. Các thao tác dễ gặp: đọc, ghi đè, move, giải nén và xóa

### 8.1. Đọc tài nguyên theo ID

Một phương án thường **dễ bảo đảm hơn** việc nhận đường dẫn tự do là dùng một ID opaque do server tạo:

```text
Client: GET /documents/42f8...
          |
  Xác minh quyền xem document ID
          |
  DB tra cứu metadata + storage key đã kiểm soát
          |
  Mở đối tượng tại vùng storage có owner/ACL phù hợp
          |
  Stream có giới hạn kích thước; đóng handle khi xong
```

Đảm bảo authorization theo **đối tượng nghiệp vụ**, không chỉ theo tên file. Kể cả khi tên đi qua kiểm tra path, người dùng A không được tự đoán tên để đọc tài liệu của người B. Đặt giới hạn kích thước đọc; `Files.readAllBytes` có thể gây sử dụng bộ nhớ quá lớn khi đầu vào không đáng tin.

### 8.2. Ghi mới hoặc xuất báo cáo

Tách ba trường hợp: **tạo mới không ghi đè**, **cập nhật object hiện có có quyền**, và **thay thế toàn bộ theo chính sách**. `Files.writeString(target, value)` mặc định có thể tạo mới/ghi đè theo các open option của nó; không dùng như một thao tác “chỉ ghi nếu chưa có” khi attacker có thể chọn tên hoặc sửa entry.

- Với “chỉ tạo mới”: `CREATE_NEW` và directory do ứng dụng kiểm soát.
- Với “cập nhật”: phải xác minh quyền trên object, tránh thao tác theo tên có thể đổi đích giữa kiểm tra và sử dụng; dùng handle và cơ chế đồng bộ/phiên bản hóa phù hợp.
- Với “thay thế file hoàn chỉnh”: thường ghi ra staging **cùng FileStore**, `fsync`/độ bền khi hệ thống yêu cầu, rồi đổi tên/move có chính sách rõ. `Files.move(source, target, ATOMIC_MOVE)` có thể tạo thao tác rename nguyên tử nếu provider hỗ trợ; nếu target đã tồn tại, Java API nói rõ hành vi **ghi đè hay thất bại là implementation-specific** khi dùng `ATOMIC_MOVE`. Nó không tự bảo đảm “không overwrite”, không bảo đảm durable commit, và có thể ném `AtomicMoveNotSupportedException`. Không tự fallback từ atomic sang non-atomic trong hệ thống mà tính nguyên tử là yêu cầu cứng.
- `Files.copy` không phải thao tác nguyên tử tổng quát; nếu lỗi có thể để lại target chưa đầy đủ. Phải có quy trình cleanup hợp lý.

### 8.3. Giải nén ZIP (Zip Slip)

Tên entry trong ZIP/TAR cũng là đầu vào chưa tin cậy. `../../outside.txt` có thể thoát khỏi thư mục giải nén nếu ghép trực tiếp bằng `resolve`. Giới hạn lexical bằng `normalize` và `Path.startsWith` là cần thiết, nhưng **không đủ** nếu cây extract có symlink hoặc actor khác kiểm soát: đường dẫn có thể bị chuyển hướng trong lúc giải nén. Với dữ liệu không đáng tin cậy, dùng directory tạm dành riêng cho quá trình extract, quyền hạn chế, không follow symlink, giới hạn tổng số file, tổng số byte giải nén, tỷ lệ nén, depth và hậu xử lý. Đừng auto-follow entries biểu diễn link mà chưa đánh giá. Sau khi kiểm tra và scan, di chuyển artifact theo chính sách publish đã xác minh.

### 8.4. Xóa và dọn cây thư mục

`Files.delete(path)`/`deleteIfExists(path)` khi target là symlink thường xóa **link**, không xóa nội dung đích mà link trỏ tới; tuy nhiên xóa theo tên vẫn có race nếu attacker thay entry. `Files.walk` và `walkFileTree` không tạo snapshot bất biến khi hệ thống file bị sửa đồng thời. `walkFileTree` theo mặc định không follow symlink nếu không yêu cầu `FOLLOW_LINKS`, nhưng điều đó **không đủ** để an toàn khi xóa đệ quy trong cây mà attacker có thể sửa. Với cleanup nhạy cảm, hạn chế phạm vi trên tree do service sở hữu và dùng cơ chế tương đối với directory handle khi môi trường hỗ trợ.

## 9. Kiểm thử khả năng phòng vệ và quan sát lỗi

Kiểm thử chỉ với chuỗi `../secret` là quá ít. Nên lập bảng theo **mức tin cậy của đầu vào** và **thao tác file system**:

| Test | Kết quả mong muốn |
| --- | --- |
| `../secret.txt`, `sub/../../secret.txt` | Bị từ chối trước thao tác file nếu policy chỉ cho leaf hoặc confinement bị vi phạm |
| Đường dẫn tuyệt đối, Windows drive-relative, UNC, ký tự đặc biệt | Không được dùng để thay base; bộ quy tắc input xử lý nhất quán theo môi trường |
| `reports-evil` cùng prefix chữ với `reports` | Không đi qua kiểm tra bằng `String.startsWith` |
| Final symlink trỏ ra ngoài, kể cả dangling symlink | Mở file theo chính sách `NOFOLLOW_LINKS` phải thất bại thay vì âm thầm follow |
| Symlink ở directory cha hoặc parent bị rename | Thiết kế handle/permission phải từ chối hoặc vẫn bám directory đã tin cậy; không tin vào lexical check |
| Hai request cùng tạo tên | Với `CREATE_NEW`, chỉ một thao tác tạo thành công; request còn lại nhận lỗi tồn tại |
| Thay file giữa `exists` và `write` | Đường đi bảo mật không dựa vào kết quả `exists`; không có overwrite ngoài ý muốn |
| Không có `SecureDirectoryStream` | Chương trình từ chối chế độ yêu cầu chống directory-race hoặc dùng phương án đã đánh giá |
| Tạo temp chứa bí mật; service user khác cố đọc | Bị giới hạn bởi quyền/ACL đúng, không dựa vào việc tên khó đoán |
| File khổng lồ, link loop, lỗi IO, disk full, restart | Có giới hạn, đóng channel, cleanup phù hợp và báo lỗi an toàn |

### Cách chạy test race có ý nghĩa

Dựng **test directory cách ly** có quyền phù hợp; thread/process “attacker” thử thay entry hoặc symlink liên tục, còn thread “victim” mở/tạo file theo code đang kiểm thử. Đặt một file canary bên ngoài root, ghi hash trước/sau và xác minh không bị sửa. Chạy cả trên Linux và Windows/provider triển khai thực nếu có thể; các tính năng link và `SecureDirectoryStream` không giống nhau trên mọi môi trường. Tính không tái hiện của race trong một lần chạy **không chứng minh** không tồn tại lỗ hổng: xem xét tính nguyên tử và điểm gắn handle ở cấp API/hệ điều hành vẫn bắt buộc.

Ghi log **operation, loại lỗi, storage ID/tenant ID không nhạy cảm, kết quả kiểm tra chính sách**; không log nội dung file hoặc secret path chi tiết công khai. Phân biệt lỗi policy/authorization, input không hợp lệ, không có provider capability và lỗi I/O tạm thời. Chặn truy cập không đồng nghĩa trả thông điệp tiết lộ path tuyệt đối hoặc file hệ thống cho client.

## 10. Checklist áp dụng trong ứng dụng Java

1. **Giới hạn đầu vào:** ưu tiên server-generated ID, tên một segment theo allowlist; từ chối path tuyệt đối/drive-relative/root khác và mọi `..` ngoài phạm vi.
2. **Dùng API `Path` đúng:** `normalize` và `Path.startsWith` chỉ là confinement **theo cú pháp**; `toRealPath` xác định đích tại thời điểm kiểm tra, không loại bỏ race.
3. **Chốt trust boundary:** base directory và parent không được attacker thay đổi; chạy service với quyền tối thiểu, áp dụng ACL/permission theo tenant và workload.
4. **Kiểm soát symlink:** `NOFOLLOW_LINKS` tại thao tác mở nếu provider hỗ trợ; nhớ kiểm tra intermediate directory, hard link và provider-specific reparse behavior.
5. **Tránh TOCTOU:** không dựa vào `exists`/`isSymbolicLink`/`isRegularFile` trước thao tác mở; dùng `CREATE_NEW`, handle đã mở và `SecureDirectoryStream` khi cần.
6. **Tạo temp chuẩn:** `Files.createTempFile/Directory`; directory dành riêng, quyền bảo vệ từ lúc tạo; không tự ghép tên đoán được hoặc dùng file tạm chia sẻ nhạy cảm.
7. **Quản lý vòng đời:** `try-with-resources`, `finally`, cleanup job-owned có kiểm soát; xử lý crash, disk full và file mồ côi.
8. **Không đánh đồng rename với authorization:** `ATOMIC_MOVE` hỗ trợ atomicity khi có, không cam kết luôn không overwrite hoặc chống actor thay parent.
9. **Giải nén/đọc file lớn:** giới hạn số lượng, dung lượng, depth và memory; không follow link tùy tiện; bảo vệ giai đoạn publish.
10. **Kiểm thử trên môi trường thực:** quyền của service account, container mounts, Windows/POSIX, NFS/SMB/custom FileSystemProvider và các tình huống concurrency.

## 11. Câu hỏi tự kiểm tra

**Tình huống A.** `candidate.normalize().startsWith(base)` trả `true`, nhưng thư mục `base/user` là symlink sang `/private`. Có được coi là truy cập hợp lệ? **Không.** Kiểm tra lexical không biết symlink; phải giải quyết rủi ro bằng vùng lưu trữ tin cậy và thao tác mở thích hợp.

**Tình huống B.** `Files.exists(file)` trả `false`. Có bảo đảm `Files.writeString(file, data)` sẽ tạo file mới đúng không? **Không.** Actor khác có thể tạo/thay entry sau bước kiểm tra; dùng `CREATE_NEW` cho yêu cầu không ghi đè.

**Tình huống C.** Đã dùng `NOFOLLOW_LINKS` khi mở `reports/tenant-a/result.csv`. Nếu `tenant-a` bị tráo thành symlink có an toàn không? **Không thể khẳng định.** Option ở thao tác mở cuối không tự pin mọi parent. Cần parent tin cậy hoặc duyệt bằng `SecureDirectoryStream` theo từng component.

**Tình huống D.** Tạo `/tmp/export-1793459995832.zip` rồi kiểm tra tên chưa tồn tại có tốt không? **Không.** Tên đoán được và check-then-use có race; dùng API temp file tạo mới nguyên tử trong directory có quyền phù hợp. (Cú pháp trên là ví dụ chuỗi minh họa, không phải Java.)

**Tình huống E.** Có thể dùng `ATOMIC_MOVE` để vừa publish một file, vừa bảo đảm không bao giờ ghi đè file đích trên mọi provider? **Không.** Java API cho phép hành vi khi target tồn tại phụ thuộc implementation; cần thiết kế chính sách và test provider cụ thể.

## 12. Tài liệu gốc

- [Oracle Java SE 21: `Path`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html) — `resolve`, `normalize`, `startsWith`, `toRealPath`.
- [Oracle Java SE 21: `Files`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html) — `createTempFile`, `createTempDirectory`, `createFile`, `newByteChannel`, `readAttributes`, `move`, `walkFileTree`, và cảnh báo kết quả `exists` lỗi thời.
- [Oracle Java SE 21: `SecureDirectoryStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/SecureDirectoryStream.html) — directory-relative operations và giảm race.
- [Oracle Java SE 21: `LinkOption`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/LinkOption.html) và [`StandardOpenOption`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardOpenOption.html) — `NOFOLLOW_LINKS`, `CREATE_NEW`, `WRITE`, `READ`, `DELETE_ON_CLOSE`.
- [Oracle Java SE 21: `PosixFilePermissions`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/attribute/PosixFilePermissions.html) — thiết lập quyền POSIX khi tạo nếu hệ thống hỗ trợ.
- [CWE-22: Improper Limitation of a Pathname to a Restricted Directory](https://cwe.mitre.org/data/definitions/22.html), [CWE-59: Improper Link Resolution Before File Access](https://cwe.mitre.org/data/definitions/59.html), [CWE-367: TOCTOU Race Condition](https://cwe.mitre.org/data/definitions/367.html), [CWE-377: Insecure Temporary File](https://cwe.mitre.org/data/definitions/377.html) — mô hình hóa rủi ro và biện pháp phòng vệ.

**Ghi nhớ:** đường dẫn là tên có thể thay đổi, còn quyền truy cập tài nguyên phải gắn với trust boundary, thao tác mở/tạo nguyên tử và quyền hệ điều hành. `normalize` giúp kiểm tra cú pháp; `NOFOLLOW_LINKS` kiểm soát việc theo liên kết ở nơi API hỗ trợ; `CREATE_NEW` chống race tại lúc tạo tên mới; `SecureDirectoryStream` cung cấp thao tác tương đối với directory đã mở khi provider hỗ trợ. Kết hợp các lớp này mới hạn chế path traversal, symlink và TOCTOU trong ứng dụng Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-005">21.5 Giới hạn kích thước, độ sâu, thời gian và tài nguyên để tránh từ chối dịch vụ</a>

<details>
<summary>Xem chi tiết</summary>

# 21.5. Giới hạn kích thước, độ sâu, thời gian và tài nguyên để tránh từ chối dịch vụ

**Từ chối dịch vụ (Denial of Service, DoS)** không nhất thiết cần lưu lượng mạng cực lớn. Chỉ một request hợp lệ về mặt cú pháp cũng có thể khiến ứng dụng Java dùng hết heap, CPU, thread, file descriptor, đĩa hoặc kết nối nếu chương trình chấp nhận dữ liệu và công việc không có giới hạn. Ví dụ: một body vài KB giải nén thành hàng GB, một JSON lồng hàng nghìn cấp, một biểu thức chính quy gây thời gian chạy bùng nổ, hoặc một kết nối gửi từng byte rất chậm.

Nguyên tắc bảo vệ là đặt **ngân sách tài nguyên (resource budget)** cụ thể cho mỗi đơn vị công việc, buộc chương trình dừng khi vượt ngân sách và đặt thêm giới hạn tổng để nhiều request riêng lẻ không làm cạn hệ thống. Bài học trình bày các hạn mức về **kích thước**, **độ sâu/độ phức tạp**, **thời gian** và **tài nguyên đồng thời**, cách thực hiện trong Java, cùng các tình huống kiểm chứng.

## 1. Vì sao chỉ kiểm tra dữ liệu “hợp lệ” là chưa đủ?

Giả sử một API nhận tệp XML. Tài liệu có thể hoàn toàn đúng cú pháp nhưng chứa hàng triệu phần tử; nếu ứng dụng dùng DOM để tải toàn bộ cây vào bộ nhớ, số đối tượng Java có thể lớn hơn nhiều lần dung lượng tệp. Hoặc một API nhận tệp GZIP nhỏ nhưng khi giải nén tạo ra lượng dữ liệu khổng lồ. Ngay cả request bị từ chối sau khi parse xong cũng đã tiêu tốn tài nguyên trước đó.

Một số tác nhân gây DoS thường gặp:

- **Đầu vào quá lớn:** request body, header, multipart part, file upload, message từ queue, chuỗi Base64, response của API khác hoặc bản ghi truy vấn.
- **Khuếch đại tài nguyên:** ZIP/GZIP bomb, XML entity expansion, tài liệu với nhiều node/thuộc tính, ảnh có số pixel rất lớn, chuỗi ký tự tạo nhiều đối tượng trung gian.
- **Độ sâu hoặc cấu trúc quá phức tạp:** JSON/XML/YAML lồng sâu, graph có chu trình, cây lớn, regex có trường hợp xử lý tốn thời gian.
- **Công việc kéo dài:** kết nối gửi quá chậm, truy vấn DB lâu, HTTP dependency không trả lời, vòng lặp hoặc thuật toán có độ phức tạp không phù hợp.
- **Quá nhiều việc cùng lúc:** số request active, job queue, thread, connection, file đang mở hoặc tác vụ nền tăng vô hạn; một request có thể hợp lệ nhưng tổng tải vượt khả năng hệ thống.

Hạn mức nên được kiểm tra **sớm nhất có thể**, trước các thao tác đắt đỏ như `readAllBytes()`, `readAllLines()`, `StringBuilder` append khổng lồ, deserialize toàn bộ graph, giải nén vào RAM, tạo thread hoặc cấp phát bộ đệm theo số lượng do client khai báo.

## 2. Xây dựng ngân sách theo nhiều chiều

Không có con số mặc định phù hợp mọi ứng dụng: giới hạn upload ảnh khác với JSON đăng nhập, luồng telemetry khác với giao dịch thanh toán. Hãy dựa vào nhu cầu thật, thống kê kích thước/thời lượng bình thường, khả năng hệ thống và tải cao điểm. Cấu hình cần có đơn vị và quy tắc xử lý khi vượt ngưỡng.

| Loại giới hạn | Kiểm soát cái gì? | Ví dụ giá trị minh họa (không phải chuẩn chung) |
| --- | --- | --- |
| Kích thước mạng | Header, body thô, từng multipart part, số part, kích thước message | JSON nghiệp vụ tối đa 1 MiB, tệp riêng có hạn mức khác |
| Kích thước sau xử lý | Byte đã giải nén, số ký tự/token, số bản ghi được tạo | GZIP giải nén tối đa 8 MiB dù tệp nén nhỏ |
| Độ sâu và bề rộng | Tầng JSON/XML, số node, số phần tử mảng, thuộc tính, graph edges | XML tối đa 64 tầng và 10.000 phần tử |
| Thời gian | Connect/read/idle timeout, deadline toàn request, DB timeout, worker timeout | Deadline xử lý nghiệp vụ 3 giây |
| Đồng thời và hàng đợi | Số tác vụ active, kết nối, worker, kích thước queue | Tối đa 50 tác vụ tốn CPU, queue tối đa 100 |
| Tài nguyên vật lý | Heap, off-heap/native memory, CPU, FDs, đĩa, network I/O | Budget container theo tải và SLO |

Những con số minh họa trên **không nên sao chép nguyên xi**. Có thể tách profile theo endpoint, người dùng/tenant và loại nội dung; ngưỡng phải đủ thấp để chặn lạm dụng nhưng đủ cao cho dữ liệu kinh doanh hợp lệ. Hệ thống cần quy định cách phản hồi khi vượt giới hạn và cách đo để điều chỉnh.

Một request có thể vượt **một trong nhiều ngân sách** trước: body chỉ 100 KiB nhưng JSON sâu hơn giới hạn; body nhỏ nhưng tốn CPU quá deadline; 50 request nhỏ đều trong hạn mức riêng nhưng tổng số request đang chạy vượt concurrency cap. Vì vậy cần kiểm tra các giới hạn **độc lập**, không thay thế lẫn nhau.

## 3. Giới hạn kích thước ngay khi đọc dữ liệu

### 3.1. Đếm **byte thực nhận**, không chỉ tin header

`Content-Length` hữu ích để từ chối sớm nếu client khai báo quá giới hạn; nhưng header có thể không có (ví dụ truyền chunked), không phản ánh kích thước sau giải nén, hoặc không bao hàm toàn bộ dữ liệu tạo trong bước xử lý. Do đó phải **đếm byte trong lúc đọc stream** và dừng ngay khi vượt hạn mức.

Thực hành:

1. Cấu hình giới hạn ở reverse proxy, web server/container và tầng ứng dụng (gồm header, request body, multipart upload).
2. Kiểm tra `Content-Length` khi có thể, nhưng vẫn giới hạn thực tế ở luồng đọc.
3. Đọc từng block có kích thước cố định, cộng bộ đếm kiểu `long`, kiểm tra giới hạn **trước khi** cấp phát hoặc ghi dữ liệu mới.
4. Không tải cả input vào `byte[]`/`String` nếu dữ liệu lớn; stream tới nơi nhận có hạn mức và cơ chế xóa dữ liệu dở dang khi lỗi.
5. Đặt **timeout đọc** hoặc deadline riêng: byte limit không giúp nếu nguồn giữ kết nối mở mà không gửi byte nào.

Java `String.length()` đếm số đơn vị mã UTF-16, **không phải số byte UTF-8**. Ví dụ ký tự `ắ` có thể dài 1 đơn vị UTF-16 nhưng cần 3 byte UTF-8. Khi ngưỡng là kích thước request trên đường truyền, đo byte của stream; khi ngưỡng là số ký tự/token/ngữ nghĩa đã parse, đo đúng đại lượng đó ở giai đoạn sau.

Có thể dùng cách sao chép giới hạn sau, với `maxBytes >= 0`. Hàm đọc tối đa từng block, so sánh `count > maxBytes - total` để tránh tràn số nguyên khi cộng; không dựa vào `InputStream.available()` (phương thức đó **không** phải tổng độ dài stream):

~~~java
static long copyLimited(InputStream source, OutputStream destination,
                        long maxBytes) throws IOException {
    if (maxBytes < 0) throw new IllegalArgumentException("maxBytes");
    byte[] buffer = new byte[4096];
    long total = 0;
    int count;
    while ((count = source.read(buffer)) != -1) {
        if (count == 0) throw new IOException("Input stream made no progress");
        if (count > maxBytes - total) {
            throw new IOException("Byte limit exceeded");
        }
        destination.write(buffer, 0, count);
        total += count;
    }
    return total;
}
~~~

Ở đúng ranh giới, stream dài `maxBytes` được chấp nhận, nhưng dài hơn một byte bị từ chối. Hàm không tự đóng hai stream vì chủ gọi sở hữu vòng đời tài nguyên; hãy dùng `try-with-resources`. Nếu ghi tới file và vượt ngưỡng, **xóa file tạm đã ghi dở**. Nếu ghi tới `ByteArrayOutputStream`, phần lớn dữ liệu vẫn nằm trong heap; chỉ phù hợp khi chính `maxBytes` là một ngưỡng nhỏ đã tính cả chi phí bộ nhớ.

Không tính sai giới hạn bằng phép cộng `int` kiểu `total += count` khi dữ liệu có thể rất lớn, hoặc cấp phát thẳng `new byte[(int)clientSuppliedSize]`. Dùng `long`, giới hạn trên hợp lý và `Math.addExact`/`Math.multiplyExact` khi tính kích thước từ nhiều chiều (ví dụ `width * height * bytesPerPixel` của ảnh).

### 3.2. Giới hạn **trước và sau** khi giải nén

Tệp GZIP/ZIP nhỏ có thể tạo ra lượng byte lớn khi giải nén; chỉ kiểm tra chiều dài tệp nén sẽ bỏ sót “zip bomb”. Với nén HTTP, archive hay message queue, nên giới hạn:

- **Byte đầu vào đã nén** và **byte thực tế sau giải nén**; kiểm tra cả tổng byte và, nếu hữu ích, tỷ lệ giải nén.
- **Số entry**, kích thước mỗi entry, tổng kích thước mọi entry, độ sâu archive lồng nhau, số tệp/đường dẫn và dung lượng đĩa tạm.
- **Thời gian giải nén** và concurrency của các tác vụ giải nén.
- Đối với file ZIP, không tin hoàn toàn các trường kích thước được công bố sẵn; hãy tính số byte thực sự khi stream đọc entry. Giải nén không kiểm soát đường dẫn còn tạo rủi ro path traversal riêng.

Nếu `GZIPInputStream` trả ra dữ liệu đã giải nén, hãy truyền **chính stream đó** vào bộ đọc có byte limit; chỉ giới hạn `FileInputStream` chứa bytes nén là chưa đủ. Khi cần xử lý nhiều lớp, mỗi lớp phải có ngưỡng và tổng ngân sách riêng. Nên dừng ngay khi phát hiện vượt quá, thay vì giải nén hết mới đo.

### 3.3. Giới hạn đầu ra cũng quan trọng

Response do server phát ra, báo cáo CSV/PDF, kết quả JSON, log và payload xuất file đều có thể tăng vô hạn dù input nhỏ. Đặt giới hạn số bản ghi trang, số byte xuất, số phần tử được serialize, số dòng log và timeout thao tác. Với các truy vấn phân trang, đặt `max page size` ở server; không cho client yêu cầu “trả toàn bộ” nếu tài nguyên không đủ.

## 4. Giới hạn độ sâu và độ phức tạp trong parser

**Kích thước byte nhỏ không đảm bảo dễ xử lý.** JSON lồng sâu có thể làm tràn stack trong parser đệ quy; XML nhiều node tạo nhiều đối tượng; YAML alias có thể mở rộng cấu trúc rất lớn; một biểu thức regex hoặc cấu trúc graph có thể tạo chi phí xử lý lớn hơn nhiều lần kích thước đầu vào.

Hạn mức nên đo theo ngữ nghĩa của kiểu dữ liệu: độ sâu nesting, tổng số phần tử, độ dài chuỗi, số thuộc tính, số entry/object, số cạnh graph và số phần tử từng collection. Khi dùng parser của bên thứ ba, tìm cơ chế `max depth / max tokens / max string length` của phiên bản đang sử dụng và cấu hình **trước khi deserialize**.

### 4.1. XML: vừa chặn XXE vừa chặn tài liệu quá sâu

Ví dụ dưới đây dùng StAX (`javax.xml.stream`), đọc lần lượt sự kiện thay vì tạo toàn bộ DOM. Nó cấm DTD và external entities, không tải external resource, đồng thời đếm số tầng và số start element:

~~~java
static void checkXmlLimits(InputStream source, int maxDepth,
                           long maxElements) throws XMLStreamException {
    if (maxDepth < 1 || maxElements < 1)
        throw new IllegalArgumentException("Limits must be positive");

    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
        throw new XMLStreamException("External resource forbidden");
    });

    XMLStreamReader xml = factory.createXMLStreamReader(source, "UTF-8");
    try {
        int depth = 0;
        long elements = 0;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (++depth > maxDepth) {
                    throw new XMLStreamException("XML depth limit exceeded");
                }
                if (++elements > maxElements) {
                    throw new XMLStreamException("XML element limit exceeded");
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    } finally {
        xml.close();
    }
}
~~~

**Cần thêm byte limit và read timeout cho `source`**: giới hạn depth/element không chặn một text node rất dài, nguồn đọc vô hạn, tên/thẻ/thuộc tính cực dài hoặc parser nội bộ có thể giữ dữ liệu; luôn kiểm tra giới hạn đầu vào **trước hoặc trong** lúc parser đọc. Nếu provider StAX không hỗ trợ thuộc tính bảo vệ bắt buộc, xử lý lỗi khởi tạo và **không lặng lẽ bỏ cấu hình**. Với DOM/JAXP, dùng `FEATURE_SECURE_PROCESSING`, hạn mức JAXP (ví dụ entity expansion và max element depth), chặn DTD/external entities và hạn mức tổng byte; riêng `FEATURE_SECURE_PROCESSING` không thể thay thế mọi kiểm soát.

### 4.2. JSON, YAML, cấu trúc dữ liệu đệ quy

Với JSON/XML/YAML, ưu tiên giới hạn do parser cung cấp vì parser có thể gặp cấu trúc nguy hiểm **trước khi** đoạn kiểm tra hậu xử lý được gọi. Ví dụ, nếu parse hết dữ liệu thành một cây rồi mới đi qua cây để đếm độ sâu, việc tiêu tốn heap/stack đã xảy ra. Các bộ deserialize nên có cấu hình cho nesting depth, chuỗi dài, số token và collection size; test bằng chính phiên bản thư viện đang triển khai.

Với graph/tệp cấu hình tham chiếu, cần tính cả **số node, số cạnh, số lần duyệt** và chu trình. Thuật toán duyệt đệ quy không giới hạn dễ gây `StackOverflowError`; vòng lặp trên graph có chu trình dễ không kết thúc. Dùng DFS/BFS với stack/queue rõ ràng, visited set khi thích hợp, kiểm tra số node/cạnh và deadline trong quá trình duyệt; không chỉ đặt giới hạn độ sâu.

### 4.3. Thuật toán tốn CPU và ReDoS

Regex có quantifier lồng nhau trên input đặc biệt có thể cần số bước khổng lồ (ReDoS). Giới hạn độ dài input là cần nhưng **không đủ** nếu lựa chọn biểu thức/thuật toán có trường hợp xấu. Hãy viết regex đơn giản có thể dự đoán chi phí, tránh cấu trúc backtracking nguy hiểm, dùng parser chuyên biệt cho dữ liệu có cấu trúc, và giới hạn cả kích thước lẫn thời gian xử lý.

Ví dụ, nếu hệ thống cho phép người dùng tự gửi regex hay template, đó là **chương trình do người dùng định nghĩa** theo nghĩa tiêu tốn tài nguyên; cần giới hạn riêng hoặc engine/isolated worker phù hợp. Không kỳ vọng timeout bên ngoài một hàm Java thuần CPU tự động dừng ngay nếu hàm không hợp tác kiểm tra interrupt/deadline.

## 5. Giới hạn thời gian: timeout, deadline và hủy công việc

**Timeout** giới hạn thời gian chờ của một thao tác; **deadline** là mốc kết thúc của toàn request/job. Phân biệt chúng:

- **Connect timeout**: thời gian tối đa để thiết lập kết nối TCP/TLS hoặc giai đoạn tương ứng.
- **Read/idle timeout**: thời gian tối đa giữa các lần nhận dữ liệu; ngăn client/dependency mở kết nối rồi gửi dữ liệu rất chậm.
- **Operation timeout**: giới hạn một truy vấn DB, lời gọi HTTP, thao tác cache, tác vụ xử lý.
- **End-to-end deadline**: thời gian tối đa cho **toàn bộ** quy trình gồm queue, parse, DB, HTTP, retry và response. Nếu mỗi bước đều được cho 3 giây thì một chuỗi nhiều bước có thể vượt deadline dự kiến.

Nên đo ngân sách thời gian bằng đồng hồ đơn điệu, chẳng hạn `System.nanoTime()`, và truyền **thời gian còn lại** cho các bước phụ thuộc. Tránh tạo một timeout đầy đủ mới mỗi lần retry; tổng thời gian gồm retry và backoff phải nằm trong deadline chung.

~~~java
long start = System.nanoTime();
long budgetNanos = java.time.Duration.ofSeconds(3).toNanos();

// Trước một thao tác tốn thời gian (snippet minh họa):
long elapsed = System.nanoTime() - start;
long remaining = budgetNanos - elapsed;
if (remaining <= 0) {
    throw new java.util.concurrent.TimeoutException("Request deadline exceeded");
}
// Dùng remaining làm timeout của lời gọi tiếp theo khi API hỗ trợ.
~~~

Trong Java, `Future.get(timeout, unit)` giới hạn thời gian **thread gọi phải chờ**, chứ không bảo đảm tác vụ đã thật sự ngừng ở mốc đó. `future.cancel(true)` yêu cầu ngắt thread thực thi; nếu công việc bỏ qua interrupt, mắc kẹt trong I/O không phản ứng hoặc chạy native code, nó có thể tiếp tục chiếm tài nguyên. Vì vậy:

1. Dùng timeout ở chính HTTP client/DB driver/socket và xử lý kết quả timeout.
2. Viết tác vụ hợp tác ngắt: khi `InterruptedException` xảy ra, ngừng xử lý và giữ trạng thái interrupt nếu phải bắt để chuyển tầng; kiểm tra deadline giữa các bước CPU dài.
3. Đóng `InputStream`/socket/DB resources đúng lúc bằng `try-with-resources` hoặc finally.
4. Nếu cần **hard limit** CPU/RAM, thực thi tác vụ rủi ro trong process/container có giới hạn và khả năng terminate từ bên ngoài; không coi `Future.cancel` là cơ chế cách ly tuyệt đối.
5. Tránh job mồ côi sau khi client hủy request; lan truyền tín hiệu cancellation trong pipeline khi được hỗ trợ.

Java `HttpClient.Builder.connectTimeout(...)` và `HttpRequest.Builder.timeout(...)` chỉ giải quyết các giai đoạn tương ứng của HTTP; với `BodyHandlers.ofInputStream()`, ứng dụng còn phải giới hạn việc **đọc body** sau khi có response, đóng stream và tuân thủ deadline toàn thao tác. Với `Socket#setSoTimeout`, timeout đọc thường áp cho một lần blocking read, không tự tạo deadline toàn bộ payload.

### Retry có thể khuếch đại DoS

Retry ngay lập tức, retry vô hạn hoặc tất cả client cùng retry khi downstream chậm gây “retry storm”. Đặt số lần retry, exponential backoff có jitter, timeout cho từng lần và kiểm tra tổng deadline. Chỉ retry lỗi phù hợp và thao tác có tính idempotent/được thiết kế chống trùng. Cần circuit breaker/bulkhead khi phụ thuộc bên ngoài đang quá tải.

## 6. Giới hạn đồng thời, queue và tài nguyên hệ thống

Mỗi request có thể nhỏ và nhanh khi chạy một mình, nhưng hàng nghìn request đồng thời vẫn làm cạn tài nguyên. **Bounded concurrency** và **backpressure** bảo vệ tổng thể:

- Giới hạn số tác vụ thực thi cùng lúc bằng thread pool có kích thước thích hợp, semaphore hoặc cơ chế concurrency limit của runtime. Thread pool **không** nên có hàng đợi vô hạn; `Executors.newFixedThreadPool` tạo queue không giới hạn mặc định, nên cần cấu hình `ThreadPoolExecutor` với bounded queue và chính sách từ chối phù hợp khi dùng trong server.
- Cân bằng CPU-bound và I/O-bound: số worker cho phép không nhất thiết bằng số CPU cho I/O; nhưng tăng thread thiếu kiểm soát tạo thêm bộ nhớ, context switch và làm xấu tail latency.
- Java 21+ virtual threads giảm chi phí một số thread đang chờ, **không tự hạn chế** số request, socket, DB connection, heap hoặc downstream QPS. Java 17 vẫn thường sử dụng platform threads. Cần giới hạn tài nguyên downstream ở cả hai mô hình.
- Giới hạn queue length và thời gian chờ trong queue. Nếu full, **từ chối sớm** thay vì tích lũy vô hạn; trả lỗi có kiểm soát hoặc `429`/`503` tùy semantics.
- Áp quota/rate limit theo user/tenant/API key/IP phù hợp, kèm hạn mức toàn hệ thống. Rate limit ngăn tốc độ request nhưng **không thay thế** concurrency cap: request chậm có thể tích tụ dù QPS thấp.
- Dùng connection pool có kích thước giới hạn, timeout khi xin connection, batch size và concurrency cap cho các downstream; thao tác ghi file cần hạn mức file descriptor, dung lượng đĩa tạm và cleanup.
- Thiết lập giới hạn process/container (CPU/memory, FD, disk, số process) là hàng rào cuối. `-Xmx` chỉ giới hạn Java heap, không trực tiếp khóa mọi native/off-heap memory, thread stack, direct buffer hoặc dung lượng file.

Ví dụ minh họa cách dùng `Semaphore` ở **biên** của tác vụ đắt đỏ; đây là snippet nằm trong một service/handler đã có semaphore dùng chung, không phải chương trình độc lập:

~~~java
private final java.util.concurrent.Semaphore heavyWorkSlots =
        new java.util.concurrent.Semaphore(8, true);

public Result process(Request request) {
    if (!heavyWorkSlots.tryAcquire()) {
        throw new BusyException("Service at capacity");
    }
    try {
        return doBoundedWork(request);
    } finally {
        heavyWorkSlots.release();
    }
}
~~~

`tryAcquire()` ở đây từ chối ngay nếu đã có 8 tác vụ cùng sử dụng tài nguyên; nó không tự giới hạn hàng đợi HTTP, thread đang chờ hoặc tốc độ truy cập vào các endpoint khác. Chỉ gọi `release()` nếu thật sự `acquire` thành công; giữ semaphore là thành viên dùng chung theo toàn instance, không khởi tạo semaphore mới cho từng request. Áp timeout/cancellation trong `doBoundedWork` và chọn số slot theo benchmark/tải thực tế.

## 7. Ví dụ Java 17+ chạy độc lập: bốn lớp giới hạn

Chương trình dưới đây dùng **chỉ Java SE**, không cần thư viện ngoài hay kết nối mạng. Nó minh họa:

1. Chấp nhận đúng số byte cho phép và từ chối nếu vượt;
2. Từ chối XML có nesting depth lớn hơn cấu hình dù văn bản ngắn;
3. Đọc **byte đã giải nén** từ GZIP và từ chối khi vượt output limit;
4. Chờ tác vụ có timeout rồi yêu cầu hủy nếu quá thời gian.

Mã nguồn hoàn chỉnh, lưu vào `DosLimitsDemo.java`:

~~~java
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.xml.stream.*;

public class DosLimitsDemo {
    static final class LimitExceeded extends IOException {
        LimitExceeded(String message) { super(message); }
    }

    static long copyLimited(InputStream source, OutputStream destination,
                            long maxBytes) throws IOException {
        if (maxBytes < 0) throw new IllegalArgumentException("maxBytes");
        byte[] buffer = new byte[4096];
        long total = 0;
        int count;
        while ((count = source.read(buffer)) != -1) {
            if (count == 0) throw new IOException("Input stream made no progress");
            if (count > maxBytes - total) {
                throw new LimitExceeded("Byte limit exceeded");
            }
            destination.write(buffer, 0, count);
            total += count;
        }
        return total;
    }

    static void checkXmlLimits(InputStream source, int maxDepth,
                               long maxElements) throws XMLStreamException {
        if (maxDepth < 1 || maxElements < 1)
            throw new IllegalArgumentException("Limits must be positive");

        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External resource forbidden");
        });

        XMLStreamReader xml = factory.createXMLStreamReader(source, "UTF-8");
        try {
            int depth = 0;
            long elements = 0;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if (++depth > maxDepth) {
                        throw new XMLStreamException("XML depth limit exceeded");
                    }
                    if (++elements > maxElements) {
                        throw new XMLStreamException("XML element limit exceeded");
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                }
            }
        } finally {
            xml.close();
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] body = "ABC".getBytes(StandardCharsets.UTF_8);
        long accepted = copyLimited(
                new ByteArrayInputStream(body),
                OutputStream.nullOutputStream(), 3);
        System.out.println("body accepted: " + accepted + " bytes");

        try {
            copyLimited(new ByteArrayInputStream(body),
                    OutputStream.nullOutputStream(), 2);
        } catch (LimitExceeded ex) {
            System.out.println("oversized body: rejected");
        }

        String xml = "<a><b><c/></b></a>";
        try {
            checkXmlLimits(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                    2, 100);
        } catch (XMLStreamException ex) {
            System.out.println("deep XML: rejected");
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(new byte[10_000]);
        }
        try (GZIPInputStream expanded = new GZIPInputStream(
                new ByteArrayInputStream(compressed.toByteArray()))) {
            copyLimited(expanded, OutputStream.nullOutputStream(), 1024);
        } catch (LimitExceeded ex) {
            System.out.println("expanded gzip: rejected");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> work = executor.submit(() -> {
                Thread.sleep(1000);
                return "done";
            });
            try {
                work.get(20, TimeUnit.MILLISECONDS);
                System.out.println("work completed");
            } catch (TimeoutException ex) {
                work.cancel(true);
                System.out.println("slow work: cancelled");
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac DosLimitsDemo.java
java DosLimitsDemo
~~~

Output dự kiến:

~~~text
body accepted: 3 bytes
oversized body: rejected
deep XML: rejected
expanded gzip: rejected
slow work: cancelled
~~~

Giải thích:

- `copyLimited` dùng buffer 4 KiB, chỉ ghi số byte đã được chấp nhận. Trường hợp `"ABC"` có 3 byte nên đúng giới hạn 3 được chấp nhận, giới hạn 2 bị từ chối. Trong server, cung cấp stream gốc từ HTTP/multipart và đích nhận có cleanup; dùng read timeout riêng.
- `checkXmlLimits` kiểm tra nesting trong quá trình StAX phát `START_ELEMENT`/`END_ELEMENT`; tài liệu `<a><b><c/></b></a>` có độ sâu 3 và bị từ chối nếu giới hạn là 2. Tắt DTD/external entities giúp tránh một nhóm XML expansion và external-resource attacks.
- GZIP chứa 10.000 byte zero trước khi nén; phần kiểm tra dùng `GZIPInputStream`, tức là đếm byte **sau khi nở** chứ không chỉ dung lượng tệp nén. Đặt thêm giới hạn file nén ở tầng nhận input khi dùng trong hệ thống thật.
- `Future.get(20, MILLISECONDS)` chờ tác vụ ngủ 1 giây và đưa ra kết quả `slow work: cancelled` sau khi quá hạn. Đây là tác vụ hợp tác ngắt vì `Thread.sleep` phản ứng với interruption. Ví dụ khởi tạo executor cho một lần chạy demo; server thực tế phải tái sử dụng pool đã giới hạn, không tạo một executor mới cho mỗi request.
- Các kiểm tra minh họa **từng lớp** độc lập. Một pipeline production phải áp chúng cùng nhau, gồm raw byte cap, parse cap, decoded byte cap, timeout và concurrency cap, đồng thời xử lý giải phóng tài nguyên trên đường lỗi.

Nếu một `InputStream` khác có thể không trả dữ liệu, byte cap sẽ **không** tự phát sinh timeout; nếu bộ giải nén chạy thuật toán rất tốn CPU, output cap cũng không thay thế giới hạn thời gian và CPU. Các ngưỡng 2, 3, 1024 byte và 20 ms trong demo cố ý rất nhỏ để kiểm chứng hành vi, không phải cấu hình triển khai.

## 8. Triển khai giới hạn theo từng tầng hệ thống

Xem giới hạn như nhiều vòng bảo vệ liên tiếp, vì mỗi tầng phát hiện một kiểu rủi ro trước khi nó lan sâu:

~~~text
Client
  |
  v
Edge / Reverse proxy
  - Header/body cap, tốc độ/timeout, connection cap, rate limit
  |
  v
Java web server
  - Request size, multipart part/count, connection/worker/queue limits
  |
  v
Application input
  - Raw byte cap -> decode/decompress cap -> parser depth/node/string caps
  |
  v
Business operation
  - Deadline -> bounded concurrency -> query/page/result/CPU caps
  |
  v
DB / HTTP / file / queue
  - Connection pool, query/read/write timeouts, output size, cleanup
  |
  v
Process / container / host
  - CPU, memory (heap và native), FD, disk, network quotas
~~~

Một công việc nền lấy message từ Kafka/RabbitMQ hoặc batch từ ổ đĩa cũng cần kiểm soát tương đương; không có HTTP reverse proxy đứng trước không có nghĩa là dữ liệu đáng tin. Các giới hạn phải xuất hiện gần nguồn dữ liệu **trước khi** library/framework ngầm deserialize vào bộ nhớ.

### Ví dụ thiết kế endpoint nhập dữ liệu

Giả sử API nhập chứng từ gửi file GZIP chứa XML. Quy trình an toàn có thể là:

1. Xác thực và kiểm tra policy endpoint/tenant; từ chối sớm nếu vượt concurrency/rate quota.
2. Giới hạn header, body nén và tốc độ đọc ở gateway/server, đồng thời đếm bytes thực nhận của upload.
3. Dùng `GZIPInputStream` streaming; đếm tổng byte đã nở, số tài liệu và kích thước từng tài liệu, không giải nén vào RAM theo kích thước do client công bố.
4. Dùng parser XML streaming với DTD/external entities bị chặn, giới hạn nesting, số element/attribute, text length và tổng số sự kiện. Kiểm tra schema/nghiệp vụ sau các hạn mức nền tảng.
5. Giới hạn số mục tạo trong DB, transaction/DB timeout, batch size, số record trả ra; kiểm tra end-to-end deadline trước thao tác đắt đỏ.
6. Dọn file tạm, stream, connection khi thành công, lỗi, client disconnect hoặc timeout; thống kê nguyên nhân từ chối mà không log nguyên payload.
7. Nếu hàng đợi/pool đầy, trả lời có kiểm soát và không tiếp tục nhận công việc nặng làm tăng áp lực.

Không nên để client tự quyết `maxDepth`, `timeout` hoặc `maxBytes` tối đa của máy chủ. Client có thể xin ngưỡng thấp hơn khi hợp lý, nhưng giới hạn trần là cấu hình phía server và được cấp quyền nếu có ngoại lệ theo hợp đồng.

## 9. Từ chối an toàn, log và theo dõi

Khi vượt ngưỡng, xử lý phải **dừng ở đúng ranh giới**, thu hồi tài nguyên và trả lỗi nhất quán. Một số mapping HTTP thường dùng:

| Tình huống | Phản hồi gợi ý |
| --- | --- |
| Body lớn hơn mức cho phép | `413 Content Too Large` (còn gặp tên cũ “Payload Too Large”) |
| XML/JSON vượt độ sâu hoặc cấu trúc không hợp lệ theo hợp đồng | `400 Bad Request` hoặc `422 Unprocessable Content` theo API contract |
| Rate quota/user quota vượt | `429 Too Many Requests`, có thể kèm `Retry-After` khi phù hợp |
| Bị từ chối do server concurrency/queue full | `503 Service Unavailable`, có thể kèm `Retry-After` |
| Timeout do upstream gateway | `504 Gateway Timeout` khi server thực sự đóng vai trò gateway |
| Client gửi request quá chậm, server áp request timeout | `408 Request Timeout` khi đúng hoàn cảnh |

Không trả `500` chung cho một trường hợp input bị giới hạn mà đã dự kiến. Đồng thời tránh tiết lộ đường dẫn nội bộ, stack trace, limit bảo mật nhạy cảm hoặc payload của người dùng. Chọn mã lỗi theo tầng phát hiện và contract; không coi bảng trên là quy tắc bắt buộc cho mọi API.

Nên có metric/trace cho: số request bị từ chối theo loại hạn mức, byte nhận/nở, nesting depth, CPU time, queue wait, thời gian tổng, số active tasks, heap/native memory, pool saturation, connection timeouts và tốc độ retry. Theo dõi cả p95/p99 latency và số lần cleanup thất bại. Giữ tag metric có cardinality thấp (route/group, loại giới hạn) thay vì nhét dữ liệu người dùng hoặc ID tùy ý vào label.

**Chặn DoS vẫn phải bảo toàn tính sẵn sàng hợp lệ:** limit quá thấp tạo “từ chối dịch vụ do cấu hình”. Tạo cấu hình phân tầng cho các workflow lớn được cấp quyền, thử tải và điều chỉnh theo dữ liệu thực tế, không mở vô hạn cho một request đặc biệt.

## 10. Kiểm thử các biên và tình huống xấu

Các kiểm thử cần xác minh **cả lỗi trả về lẫn tài nguyên được giải phóng**. Với giới hạn bất kỳ `L`, test `L-1`, `L` và `L+1`. Ngoài ra:

| Tình huống thử | Kỳ vọng |
| --- | --- |
| Body đúng ngưỡng và vượt đúng 1 byte | Đúng ngưỡng thành công, vượt bị từ chối trước khi cấp phát/ghi quá mức |
| Header `Content-Length` thiếu hoặc không phản ánh dữ liệu thực đọc | Vẫn kiểm tra byte thực nhận |
| GZIP nhỏ nhưng nở quá ngưỡng | Dừng giải nén sớm; không giữ output khổng lồ |
| XML depth vừa ngưỡng và thêm một cấp; XML nhiều sibling | Vừa ngưỡng cho phép; quá depth hoặc maxElements bị từ chối |
| JSON/YAML nhiều phần tử, chuỗi cực dài, graph có chu trình | Parser/duyệt bị chặn theo giới hạn tương ứng và không treo |
| Body gửi từng byte chậm, socket không kết thúc | Read/idle timeout hoặc total deadline cắt request |
| Dependency chậm, retry liên tiếp | Thời gian tổng vẫn trong deadline; retry có cap/backoff |
| Nhiều request cùng lúc, queue và connection pool đầy | Không tăng queue vô hạn, có backpressure/rejection hợp lý |
| Client hủy upload hoặc request vượt budget giữa chừng | Stream, temp file, DB connection, permit được giải phóng |
| Regex/thuật toán có input gây thời gian chạy tệ | Thời gian/tác vụ bị ràng buộc; tránh pattern/thuật toán không an toàn |

Khi load test, tăng đồng thời dần theo kịch bản có kiểm soát và theo dõi memory/CPU/GC, latency, FD/connection count, temp disk, retry volume và tỉ lệ lỗi, thay vì chỉ đo thông lượng trung bình. Kiểm tra đặc biệt failure path: lỗi ở giữa copy/parse, timeout sau khi nhận permit, downstream disconnect và deployment với nhiều instance.

## 11. Những sai lầm cần tránh

- **`Content-Length <= limit` nên chắc chắn an toàn:** sai nếu header thiếu, dữ liệu bị nén hoặc cấu trúc nhỏ gây xử lý đắt. Đếm byte thực tế, giới hạn sau giải nén và parser.
- **`input.length()` là số byte request:** sai với Unicode khi `String.length()` đo UTF-16 units. Giới hạn byte ở stream, giới hạn ký tự/code point riêng nếu cần.
- **Parse xong mới gọi `validateDepth()`:** quá muộn nếu parser đã tạo cây lớn hoặc tràn stack; cấu hình parser và kiểm tra ngay khi đọc.
- **Timeout ở `Future.get` là hard kill:** không chính xác; `cancel(true)` chỉ yêu cầu interrupt. Kiểm soát I/O timeout, cooperative cancellation và isolation khi cần.
- **Dùng `readAllBytes()` rồi kiểm tra kích thước:** đã cấp phát tài nguyên trước khi kiểm tra. Đọc theo block và kiểm soát số byte.
- **Thread pool fixed size thì an toàn tuyệt đối:** `newFixedThreadPool` mặc định có unbounded queue; cần giới hạn queue, admission và xử lý rejection.
- **Chỉ giới hạn số request mỗi giây:** không ngăn số active request tích tụ do xử lý lâu; cần cả concurrency cap và timeout.
- **Tạo semaphore mới trong từng request:** mỗi request có semaphore riêng nên không có giới hạn toàn cục; semaphore/pool phải được chia sẻ theo scope phù hợp.
- **Chỉ giới hạn `-Xmx`:** native memory, thread stack, direct buffers, disk và FDs vẫn có thể cạn. Phải phối hợp JVM/application và container/OS limits.
- **Nuốt lỗi vượt limit rồi tiếp tục với dữ liệu một phần:** dễ sinh kết quả sai và tài nguyên rò rỉ. Trả lỗi rõ ràng, rollback/hủy thao tác, cleanup file tạm.
- **Chỉ test dữ liệu bình thường:** cần các ca biên, malformed input, dữ liệu nén, nesting sâu, client chậm và peak concurrent load.

Một giải pháp phòng DoS đúng nghĩa xác định rõ *mỗi request được phép tiêu thụ tối đa bao nhiêu byte, bao nhiêu cấu trúc, trong bao lâu, và bao nhiêu request được xử lý đồng thời*; kiểm tra các hạn mức **trước và trong khi** xử lý, kết thúc có kiểm soát, đo đạc để điều chỉnh và giới hạn tài nguyên hạ tầng như hàng rào cuối cùng.

## Tài liệu tham khảo

- [OWASP Denial of Service Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Denial_of_Service_Cheat_Sheet.html) — các dạng DoS và cơ chế phòng vệ theo nhiều tầng.
- [OWASP XML External Entity Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) — cấu hình parser XML giảm entity expansion/XXE.
- [Java SE 17 — InputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/InputStream.html), [Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html), [ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html) — hợp đồng API về đọc stream, timeout và cancellation.
- [Java SE 17 — XMLInputFactory](https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html), [GZIPInputStream](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html) — parse streaming và giải nén.
- [Java SE 17 — HttpRequest.Builder.timeout](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html#timeout(java.time.Duration)) — timeout cho HTTP request; vẫn cần giới hạn xử lý body và deadline nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-006">21.6 Defensive copy, dữ liệu thay đổi được và việc để lộ tham chiếu có quyền</a>

<details>
<summary>Xem chi tiết</summary>

# 21.6 Defensive copy, dữ liệu thay đổi được và việc để lộ tham chiếu có quyền

## 1. Mục tiêu của bài học

Khi thiết kế Java API liên quan đến dữ liệu nhạy cảm, **`private` và `final` chưa đủ để bảo vệ trạng thái bên trong** nếu các đối tượng có thể thay đổi được (*mutable*) vẫn bị chia sẻ qua constructor, getter, callback hoặc phương thức trả về.

Sau bài này, bạn có thể:

1. Phân biệt *aliasing* (nhiều tham chiếu trỏ cùng một đối tượng), *representation exposure* (lộ biểu diễn/trạng thái nội bộ) và *capability leak* (lộ tham chiếu có quyền thao tác).
2. Thực hiện **defensive copy khi nhận đầu vào và khi xuất dữ liệu**, duy trì các bất biến bảo mật của đối tượng.
3. Phân biệt bản sao dữ liệu, bản sao nông (*shallow copy*), bản sao sâu (*deep copy*), collection *unmodifiable view* và snapshot độc lập.
4. Đánh giá các trường hợp thường gặp trong Java: mảng, `List`, `Map`, `Date`, `ByteBuffer`, record và các đối tượng tự định nghĩa.
5. Thiết kế API trả về đúng lượng thông tin/quyền cần thiết, không làm lộ đối tượng có khả năng thực hiện thao tác đặc quyền.
6. Viết phép thử chứng minh người gọi không thể sửa trạng thái bên trong qua tham chiếu đầu vào/đầu ra.

**Ngữ cảnh:** một dịch vụ xác thực nhận cấu hình quyền và dữ liệu bằng chứng, lưu trữ chúng trong đối tượng Java rồi cung cấp getter cho tầng khác. Nếu dịch vụ giữ trực tiếp `byte[]` hoặc `List` của người gọi, hoặc trả lại đối tượng quản trị nội bộ, người gọi có thể sửa dữ liệu hay cấp quyền ngoài lối đi kiểm soát dự kiến.

Đây là một bài về **biên tin cậy và thiết kế đối tượng an toàn**, không chỉ là kỹ thuật sao chép để tránh lỗi lập trình.

## 2. Gốc rễ: biến tham chiếu khác với đối tượng được tham chiếu

Java truyền giá trị tham số; với kiểu tham chiếu, giá trị được truyền là **tham chiếu đến đối tượng**. Sao chép biến tham chiếu không sao chép đối tượng.

```java
byte[] a = {1, 2, 3};
byte[] b = a;      // Cùng một mảng
b[0] = 99;
System.out.println(a[0]); // 99
```

Hai tên `a` và `b` cùng trỏ đến một mảng. Đổi tên biến, khai báo `private` hay đánh dấu `final` cho trường giữ tham chiếu **không làm mảng trở thành bất biến**:

```java
private final byte[] keyBytes;
```

`final` chỉ ngăn gán lại `keyBytes` sang tham chiếu khác sau khi khởi tạo; nó **không ngăn** `keyBytes[0] = ...` và cũng không ngăn chủ sở hữu một alias khác sửa cùng mảng.

Mô hình dễ nhớ:

```text
Người gọi                  Đối tượng an ninh
input --------------------> private final data
           cùng tham chiếu        |
                                  v
                            [mảng/collection mutable]

Người gọi sửa input --> trạng thái nội bộ thay đổi
Getter trả data ------> người gọi sửa trực tiếp trạng thái nội bộ
```

**Representation exposure** có hai đường thường gặp:

- **Inbound exposure:** constructor/setter lưu nguyên tham chiếu từ người gọi, khiến người gọi giữ khả năng thay đổi trạng thái nội bộ.
- **Outbound exposure:** getter hoặc callback trả đúng tham chiếu nội bộ, tạo cho người gọi một đường sửa trạng thái hoặc dùng một tài nguyên có quyền.

Hậu quả có thể là thay đổi quyền, cấu hình bảo mật, dữ liệu kiểm tra chữ ký/hash, trạng thái phiên hoặc giao dịch mà không qua validation/audit dự kiến. Hãy xác định **ai còn giữ tham chiếu** và **đối tượng đó cho phép thao tác gì** ở mọi biên API.

## 3. Defensive copy là gì? Đặt ở những biên nào?

**Defensive copy** tạo một đối tượng hoặc cấu trúc dữ liệu đủ độc lập trước khi tin tưởng hay công bố nó. Đối với kiểu đơn giản:

- **Lúc nhận:** tạo snapshot từ đầu vào trước, kiểm tra chính snapshot rồi lưu snapshot.
- **Lúc trả:** chỉ trả snapshot, giá trị immutable hoặc DTO giới hạn thông tin; không để người gọi nhận alias của trạng thái mutable được bảo vệ.
- **Lúc chia sẻ với code không tin cậy:** truyền bản sao của dữ liệu có thể thay đổi hoặc một API có quyền giới hạn; đừng chuyển đối tượng quản trị đầy đủ.

Ví dụ với mảng:

```java
private final byte[] evidence;

public byte[] evidence() {
    return evidence.clone(); // Trả mảng mới, không lộ mảng nội bộ
}
```

Trong constructor cũng phải sao chép:

```java
public EvidenceHolder(byte[] input) {
    byte[] snapshot = Objects.requireNonNull(input, "input").clone();
    if (snapshot.length == 0) {
        throw new IllegalArgumentException("Empty evidence");
    }
    this.evidence = snapshot;
}
```

Hai đoạn trên minh họa **hai phần trong cùng một lớp** (cần import `java.util.Objects`, khai báo lớp và trường tương ứng); không phải hai chương trình độc lập. Phần quan trọng là **copy → validate bản copy → store**, rồi khi trả dữ liệu lại **copy ở đầu ra**.

Nếu chỉ copy ở một phía, phía còn lại vẫn có thể làm lộ trạng thái:

| Constructor | Getter | Kết quả |
| --- | --- | --- |
| Giữ mảng đầu vào | Trả mảng nội bộ | Người gọi sửa được cả từ đầu vào và đầu ra. |
| **Copy** đầu vào | Trả mảng nội bộ | Người gọi không còn sửa bằng đầu vào cũ nhưng vẫn sửa qua getter. |
| Giữ mảng đầu vào | **Copy** khi trả | Alias đầu vào vẫn sửa được trạng thái. |
| **Copy** đầu vào | **Copy** khi trả | Mỗi biên đều tránh chia sẻ trực tiếp mảng nội bộ. |

## 4. Ví dụ Java 17+ hoàn chỉnh: nhìn thấy lỗi aliasing

Tạo tệp **`DefensiveCopyDemo.java`**:

```java
import java.util.*;

public final class DefensiveCopyDemo {

    static final class UnsafeTicket {
        private final byte[] proof;

        UnsafeTicket(byte[] proof) {
            this.proof = proof;          // Lỗi: giữ alias từ bên ngoài
        }

        byte[] proof() {
            return proof;               // Lỗi: xuất alias nội bộ
        }
    }

    static final class SafeTicket {
        private final byte[] proof;

        SafeTicket(byte[] input) {
            byte[] snapshot = Objects.requireNonNull(input, "input").clone();
            if (snapshot.length != 3) {
                throw new IllegalArgumentException("Length must be 3");
            }
            this.proof = snapshot;      // Lưu giá trị đã kiểm tra
        }

        byte[] proof() {
            return proof.clone();       // Không lộ tham chiếu nội bộ
        }
    }

    static final class MutableRule {
        private String scope;

        MutableRule(String scope) {
            this.scope = scope;
        }

        String scope() {
            return scope;
        }

        void setScope(String scope) {
            this.scope = scope;
        }
    }

    record Rule(String scope) {}       // String immutable

    public static void main(String[] args) {
        byte[] input = {1, 2, 3};
        UnsafeTicket bad = new UnsafeTicket(input);
        SafeTicket safe = new SafeTicket(input);

        input[0] = 9;
        System.out.println("input -> unsafe: " + bad.proof()[0]);
        System.out.println("input -> safe: " + safe.proof()[0]);

        bad.proof()[1] = 8;
        safe.proof()[1] = 8;
        System.out.println("getter -> unsafe: " + bad.proof()[1]);
        System.out.println("getter -> safe: " + safe.proof()[1]);

        List<String> source = new ArrayList<>(List.of("read"));
        List<String> liveView = Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);

        source.add("write");
        System.out.println("view: " + liveView);
        System.out.println("snapshot: " + snapshot);

        List<MutableRule> mutable = new ArrayList<>();
        mutable.add(new MutableRule("read"));

        List<MutableRule> shallow = List.copyOf(mutable);
        List<Rule> values = mutable.stream()
                .map(r -> new Rule(r.scope()))
                .toList();

        mutable.get(0).setScope("admin");
        System.out.println("shallow element: " + shallow.get(0).scope());
        System.out.println("immutable element: " + values.get(0).scope());
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac DefensiveCopyDemo.java
java DefensiveCopyDemo
```

Kết quả:

```text
input -> unsafe: 9
input -> safe: 1
getter -> unsafe: 8
getter -> safe: 2
view: [read, write]
snapshot: [read]
shallow element: admin
immutable element: read
```

**Diễn giải các phép thử:**

1. `UnsafeTicket` giữ nguyên tham chiếu `input`. Sửa `input[0]` thành `9` thay đổi cả dữ liệu trong ticket.
2. `SafeTicket` đã sao chép đầu vào khi tạo; thay đổi `input` sau đó không ảnh hưởng ticket.
3. `bad.proof()` trả mảng nội bộ; sửa phần tử của mảng trả về làm thay đổi ticket. `safe.proof()` tạo mảng mới, nên sửa mảng trả về không ảnh hưởng ticket.
4. `Collections.unmodifiableList(source)` chỉ chặn sửa **qua view**; khi danh sách nguồn thêm `"write"`, view vẫn phản ánh thay đổi. `List.copyOf(source)` có snapshot cấu trúc danh sách tại thời điểm gọi, không có phần tử vừa thêm.
5. `List.copyOf(mutable)` sao chép **cấu trúc** danh sách nhưng phần tử `MutableRule` vẫn là cùng đối tượng mutable, nên thay `scope` thành `"admin"` hiện lên qua `shallow`. Ngược lại, `values` chứa các record mới có `String` immutable, bảo toàn giá trị `"read"`.

**Kết luận từ ví dụ:** một collection không cho gọi `add`/`set` vẫn có thể biểu lộ trạng thái thay đổi thông qua backing collection hoặc thông qua phần tử mutable. Cần đặt câu hỏi cả ở **cấp container** lẫn **cấp phần tử**.

## 5. Unmodifiable view, snapshot và shallow/deep copy

Các API hay bị nhầm:

| API/cách làm | Có tách container khỏi nguồn? | Có ngăn sửa qua kết quả? | Còn chia sẻ phần tử mutable? |
| --- | --- | --- | --- |
| `return originalList;` | Không | Không | Có |
| `Collections.unmodifiableList(originalList)` | **Không**, chỉ là view | Có, qua chính view | Có |
| `new ArrayList<>(originalList)` | Có, ở cấp list | Không; bản mới vẫn sửa được | Có |
| `Collections.unmodifiableList(new ArrayList<>(originalList))` | Có, ở cấp list | Có | Có |
| `List.copyOf(originalList)` | Snapshot nội dung list theo hợp đồng API | Có | Có |
| `originalList.stream().map(this::toImmutableValue).toList()` | Có, nếu mapper tạo giá trị độc lập | Có | Không, **nếu** mọi phần tử kết quả thực sự immutable/đã copy đủ sâu |

**Lưu ý chính xác với `List.copyOf`:** API trả một list không chỉnh sửa được, và các thay đổi cấu trúc tiếp theo ở collection đầu vào không được phản ánh. JDK có thể **tái sử dụng** đối tượng nếu đầu vào đã là một list immutable phù hợp, vì thế không dựa vào `copyOf` để bảo đảm **`result != input`**. Ngay cả khi container được tách, các đối tượng phần tử có thể vẫn được tham chiếu chung. `List.copyOf` từ chối `null` collection/phần tử; `Map.copyOf` từ chối key/value `null`.

**Shallow copy** tạo container/đối tượng ngoài mới nhưng vẫn trỏ tới các object con. **Deep copy** phải tạo lại những object con cần tách ở **mọi mức mutable có liên quan**. Không nhất thiết sao chép vô hạn: với kiểu immutable chuẩn như `String`, `Integer`, `Instant` hoặc record thực sự chỉ chứa thành phần immutable, chia sẻ tham chiếu thường an toàn.

Ví dụ cấu trúc lồng nhau:

```java
Map<String, List<String>> original = new HashMap<>();
original.put("team-A", new ArrayList<>(List.of("read")));

Map<String, List<String>> shallow = Map.copyOf(original);
original.get("team-A").add("admin");
// shallow.get("team-A") vẫn chứa "admin".
```

Để tách cấu trúc này, tạo copy ở cả hai lớp:

```java
Map<String, List<String>> snapshot = new HashMap<>();
for (var entry : original.entrySet()) {
    snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
}
Map<String, List<String>> safeSnapshot = Map.copyOf(snapshot);
// String immutable; cả hai cấp map/list giờ là cấu trúc không thể sửa qua API.
```

Hai snippet là minh họa độc lập cần các import `java.util.*` và đặt trong method hoặc khối chạy phù hợp; không phải tệp Java đầy đủ. Nếu phần tử của list là đối tượng mutable (thay vì `String`), còn phải chuyển chúng thành giá trị immutable hoặc tự sao chép. Nếu dữ liệu đầu vào có thể thay đổi đồng thời trong lúc copy, cần thêm giao thức đồng bộ hoặc immutable handoff (xem mục 9).

### Trường hợp Set và Map

`Set.copyOf(source)` và `Map.copyOf(source)` tạo collection không thể cập nhật thông qua kết quả theo hợp đồng API, nhưng **không deep-copy** phần tử, key hoặc value. Key mutable còn nguy hiểm với `HashMap`: nếu `hashCode`/`equals` phụ thuộc vào trạng thái thay đổi, việc tra cứu có thể hoạt động sai. Với cấu hình quyền, chọn key/value immutable hoặc thiết kế lớp value object bất biến.

## 6. Record có tự bảo vệ thành phần mutable không?

**Không.** Record tự tạo trường `private final` và accessor, nhưng accessor mặc định trả trực tiếp tham chiếu thành phần. Record chứa `byte[]`, `Date` hoặc `List<MutableRule>` vẫn có thể lộ trạng thái. Định nghĩa:

```java
public record UnsafeBytes(byte[] data) {}
```

sẽ cho `UnsafeBytes.data()` trả chính mảng đã nhận. Để làm snapshot ở hai phía, tạo tệp **`SafeBytes.java`**:

```java
import java.util.Objects;

public record SafeBytes(byte[] data) {
    public SafeBytes {
        data = Objects.requireNonNull(data, "data").clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
```

**Compact canonical constructor** nhận tham số `data`, gán lại tham số bằng bản sao và Java tự gán trường record sau khi khối constructor kết thúc. Accessor tùy chỉnh tạo thêm bản sao khi trả. Đây là một **kiểu dữ liệu có thể biên dịch**, nhưng không có `main` riêng; dùng từ chương trình ứng dụng hoặc test.

Còn một chi tiết: `equals`/`hashCode` mặc định của record chứa mảng dùng ngữ nghĩa so sánh mảng theo tham chiếu, không tự dùng `Arrays.equals`. Vì thế nếu cần *value equality* cho byte array thì phải thiết kế rõ hợp đồng so sánh/hash (hoặc thay mảng bằng value type phù hợp). Defensive copy giải quyết aliasing, **không tự giải quyết mọi ngữ nghĩa của record**.

## 7. Dữ liệu thường gặp và cách chọn kỹ thuật

| Loại dữ liệu | Rủi ro | Mẫu xử lý khi cần snapshot |
| --- | --- | --- |
| `byte[]`, `char[]`, `int[]` | Mảng mutable ở mọi thời điểm. | `input.clone()` hoặc `Arrays.copyOf(input, input.length)` khi nhận; clone/copy khi trả. |
| `byte[][]`, mảng của object mutable | `clone()` của mảng ngoài chỉ shallow-copy. | Copy từng hàng/mỗi object cần tách. |
| `Date` | `setTime` thay đổi thời điểm. | `new Date(input.getTime())` khi nhận/trả, hoặc chuyển sang `Instant` immutable. |
| `Calendar` | Chứa trạng thái mutable. | Tạo snapshot dữ liệu cần thiết hoặc copy có kiểm soát; thận trọng với subtype/`clone()`. |
| `List<String>`, `Set<String>` | Container mutable, phần tử immutable. | `List.copyOf`/`Set.copyOf` thường đủ để bảo vệ cấu trúc. |
| `List<MutableRule>` | Container và từng phần tử mutable. | Ánh xạ từng rule thành immutable record/DTO hoặc deep copy đã định nghĩa. |
| `Map<String, List<String>>` | Mutable map và nested lists. | Copy mỗi nested list rồi `Map.copyOf` toàn map. |
| `ByteBuffer` | Nội dung vùng đệm có thể chung backing storage. | Copy byte sang buffer/mảng độc lập; `asReadOnlyBuffer()` chỉ là view read-only **cùng nội dung lưu trữ**. |
| Đối tượng nắm tài nguyên/quyền | Giữ `Connection`, stream, quản trị user, handle/HSM, service có method nguy hiểm. | Không trả reference; trả giá trị bất biến hoặc facade chỉ có thao tác được phép. |

Một ví dụ nhỏ với `Date`:

```java
private final Date expiresAt;

public Session(Date input) {
    this.expiresAt = new Date(Objects.requireNonNull(input).getTime());
}

public Date expiresAt() {
    return new Date(expiresAt.getTime());
}
```

Đây là các thành viên minh họa cho cùng lớp `Session` (cần import `java.util.Date` và `java.util.Objects`). Nếu kiểm tra hết hạn bằng `expiresAt`, việc một bên sửa `Date` gốc/`Date` được trả về không thể thay đổi thời điểm đã lưu. Với API mới, cân nhắc `java.time.Instant` vì immutable và dễ biểu đạt giá trị thời gian.

Với `ByteBuffer`, **đừng coi `asReadOnlyBuffer()` hay `slice()` là defensive copy**. Chúng có thể chia sẻ cùng byte storage với buffer gốc. Nếu không tin các bên còn giữ buffer gốc, copy dữ liệu sang vùng riêng; cần tính rõ `position`/`limit` và phần dữ liệu cần bảo vệ, không mặc nhiên dùng toàn capacity.

## 8. Để lộ tham chiếu có quyền: vấn đề sâu hơn dữ liệu mutable

Một reference trong Java có thể đại diện cho **quyền thực hiện hành động**, không chỉ quyền xem dữ liệu. Ví dụ đối tượng quản trị tài khoản có `grantAdmin`, đối tượng ghi tệp đã được mở, service quản lý khóa hoặc một handle kết nối có thể thực hiện thao tác nhạy cảm. Trả nguyên reference cho bên nhận cũng trao cho họ khả năng gọi các thao tác public mà reference đó cho phép trong ngữ cảnh thực thi.

Đây thường được gọi là **object capability**: sở hữu tham chiếu đến một đối tượng có thể đủ để dùng các khả năng của nó. Dùng `private final` để giấu trường không giúp gì nếu getter vẫn trả lại đối tượng. Copy `List` dữ liệu cũng không giải quyết việc lộ một **service có quyền**.

Ví dụ hoàn chỉnh trong tệp **`CapabilityDemo.java`**:

```java
import java.util.HashSet;
import java.util.Set;

public final class CapabilityDemo {

    static final class AdminService {
        private final Set<String> admins = new HashSet<>();

        public void grantAdmin(String username) {
            admins.add(username);
        }

        public boolean isAdmin(String username) {
            return admins.contains(username);
        }
    }

    static final class UnsafeDirectory {
        private final AdminService admin = new AdminService();

        public AdminService administration() {
            return admin; // Lộ tham chiếu có quyền grantAdmin()
        }
    }

    static final class SafeDirectory {
        private final AdminService admin = new AdminService();

        SafeDirectory() {
            admin.grantAdmin("root");
        }

        public boolean isAdmin(String username) {
            return admin.isAdmin(username); // Chỉ công khai truy vấn
        }
    }

    public static void main(String[] args) {
        UnsafeDirectory unsafe = new UnsafeDirectory();
        unsafe.administration().grantAdmin("linh");
        System.out.println("unsafe grant: "
                + unsafe.administration().isAdmin("linh"));

        SafeDirectory safe = new SafeDirectory();
        System.out.println("safe query: " + safe.isAdmin("root"));
        System.out.println("safe query: " + safe.isAdmin("linh"));
    }
}
```

Chạy:

```bash
javac CapabilityDemo.java
java CapabilityDemo
```

Đầu ra:

```text
unsafe grant: true
safe query: true
safe query: false
```

Ở `UnsafeDirectory`, method getter không chỉ lộ trạng thái `admins`: nó cung cấp **đúng đối tượng có method cấp quyền**. Caller gọi `grantAdmin("linh")` mà không qua một endpoint kiểm tra quyền. `SafeDirectory` giữ service ở bên trong và chỉ công bố khả năng truy vấn `isAdmin`, nên người gọi không có đường gọi `grantAdmin` thông qua API đó.

Ví dụ đơn giản hóa phần xác thực để làm rõ **thiết kế tham chiếu**; một dịch vụ thực tế còn phải thực thi authorization ở **nơi thao tác được phép diễn ra**, không dựa vào việc Java class ẩn method khỏi một số caller để thay thế kiểm tra danh tính hoặc chính sách. Nếu cần cho phép thay đổi quyền, cung cấp **một method kiểm tra danh tính/quyền từ ngữ cảnh đáng tin**, thực hiện thay đổi bên trong service và ghi audit, thay vì trả thẳng service quản trị.

### Vì sao chỉ "trả interface chỉ đọc" đôi khi vẫn chưa đủ?

Nếu một đối tượng implement cả interface đọc và interface quản trị, việc trả nó qua kiểu `Reader` không nhất thiết thu hồi khả năng quản trị: code biết kiểu triển khai cụ thể có thể thử cast ngược lại. Chọn một **facade/wrapper riêng** chỉ ủy quyền các truy vấn được phép, và không để đối tượng quyền cao bị xuất qua field, getter, callback, logger, event payload hay exception.

Ví dụ định hướng:

```java
interface UserReader {
    boolean isAdmin(String name);
}

// Trả một wrapper chỉ ủy quyền thao tác đọc.
UserReader limited = name -> privilegedService.isAdmin(name);
```

Khối này **không tự chạy**: `privilegedService` là đối tượng riêng trong tầng nội bộ. Ý nghĩa của wrapper là caller nhận được **một đối tượng khác chỉ cung cấp method đọc**, không nhận chính `privilegedService`. Đây là biện pháp giới hạn capability ở ranh giới API; nó không biến Java object model thành sandbox chống mã độc có toàn quyền trong cùng JVM.

## 9. Copy trước khi validate để hạn chế TOCTOU

Giả sử một policy không cho phép quyền `"admin"` được đưa vào danh sách quyền của user bình thường. Code sau không an toàn khi người gọi vẫn giữ và có thể thay đổi collection:

```java
// Sai: kiểm tra đối tượng của caller rồi mới copy.
if (input.contains("admin")) {
    throw new IllegalArgumentException("Forbidden role");
}
this.roles = List.copyOf(input);
```

Nếu một thread khác sửa `input` giữa hai thao tác, dữ liệu **đã kiểm tra** và dữ liệu **được lưu** có thể khác nhau: *time-of-check to time-of-use (TOCTOU)*.

Ưu tiên:

```java
// Đúng hơn: kiểm tra chính dữ liệu định lưu.
List<String> snapshot = List.copyOf(input);
if (snapshot.contains("admin")) {
    throw new IllegalArgumentException("Forbidden role");
}
this.roles = snapshot;
```

Hai snippet là code minh họa bên trong constructor/phương thức có trường `roles` và tham số `input`, không phải chương trình độc lập.

Tuy nhiên, **defensive copy không phải phép snapshot nguyên tử cho mọi đầu vào concurrent**. Một `ArrayList` đang bị thread khác sửa không đồng bộ trong lúc `List.copyOf` duyệt có thể khiến hành vi không nhất quán hoặc phát sinh lỗi; với các cấu trúc mutable phức tạp, bản sao có thể đã bị đọc giữa các cập nhật. Khi nguồn được nhiều thread dùng chung, thiết lập rõ **ownership và giao thức đồng bộ**:

- Chuyển giao dữ liệu immutable/đã hoàn chỉnh giữa các bên.
- Nếu tất cả bên tham gia đồng ý, lock đúng đối tượng/quy trình trong thời gian đọc, copy và tạo snapshot; đừng khóa một phía trong khi phía còn lại sửa không dùng cùng lock.
- Với shared mutable state, áp dụng cơ chế đồng bộ hoặc snapshot do chính owner phát hành.
- Đối tượng sau khi xây dựng vẫn phải có chính sách cập nhật nhất quán; copy lúc constructor không thay thế thread safety cho các thao tác khác.

Trong bảo mật, phải xác minh **dữ liệu snapshot** chính là dữ liệu được lưu và dùng trong quyết định, không quay lại đọc collection đầu vào sau khi validate.

## 10. Các bẫy thường gặp

### 10.1 `final`, `private` và `Collections.unmodifiableList` không tương đương với immutable

- `final` bảo vệ phép gán lại tham chiếu, không bảo vệ object mutable.
- `private` chặn một số truy cập trực tiếp theo Java access control; getter trả object nội bộ phá vỡ mục tiêu này.
- `Collections.unmodifiableList` tạo view read-only, không đóng băng backing list hay phần tử bên trong.
- `List.copyOf` và `Map.copyOf` không deep-copy mutable elements, keys hay values.

### 10.2 Dùng `clone()` mà không hiểu cấu trúc

Với mảng một chiều chứa primitive, `clone()` sao chép đủ sâu **ở mức byte/primitive element**. Với mảng đối tượng hoặc object đồ thị lồng nhau, `clone()` thường chỉ sao chép một cấp; các object con vẫn bị chia sẻ. Với class tự định nghĩa hoặc subtype không tin cậy, `clone()` có thể có cách triển khai khác mong đợi, thậm chí gọi code bên ngoài. Ưu tiên copy constructor/mapper rõ ràng cho value object thay vì giả định mọi `clone()` đều tạo deep copy.

### 10.3 Copy nhầm loại đối tượng hoặc trả object "chỉ đọc" có cùng backing storage

`ByteBuffer.asReadOnlyBuffer()`, `Collections.unmodifiableMap(...)` và các view tương tự có thể vẫn phản ánh thay đổi từ nguồn. Một `List<MutableRule>` dù không có `add()` vẫn cho phép sửa `rule.setScope(...)`.

Khi **cần snapshot cố định**, hãy copy dữ liệu; khi **chỉ cần ngăn caller gọi mutator trên một cửa sổ quan sát live**, view có thể hợp lý nhưng phải ghi rõ hợp đồng.

### 10.4 Getter trên thực thể đặc quyền

Nếu getter trả `Connection`, `OutputStream`, service cấp role, key-management handle hoặc đối tượng có phương thức cập nhật cấu hình, không có "bản sao dữ liệu" đơn giản nào bảo đảm thu hồi quyền. Thiết kế một thao tác được ủy quyền chặt chẽ, immutable DTO hoặc wrapper chỉ có method tối thiểu.

Việc chuyển kiểu về một interface ít method mà vẫn trả **cùng đối tượng quyền cao** có thể không ngăn downcast khi implementation có thể bị caller truy cập.

### 10.5 Làm rò rỉ `this` lúc đối tượng chưa khởi tạo xong

Đăng ký callback, tạo thread có quyền truy cập `this` hoặc đưa `this` cho code bên ngoài từ constructor có thể làm lộ trạng thái chưa xây dựng đủ và khiến logic bảo mật bị gọi trước khi kiểm tra bất biến. Ưu tiên hoàn tất construction rồi mới công bố đối tượng; không gọi method overridable từ constructor khi có thể chạy code của subclass chưa khởi tạo.

### 10.6 Serialization, deserialization và thư viện bên ngoài

Một class bảo vệ trường bằng constructor/getter vẫn có thể bị ảnh hưởng khi object được tạo qua cơ chế deserialization/binding không đi qua cùng lối khởi tạo. Với cơ chế Java Serialization mặc định, phải kiểm soát representation, validation và escape của object mutable trong quá trình đọc/ghi nếu class hỗ trợ serialization; tránh mặc định coi getter an toàn là đủ. Với DTO từ JSON hoặc ORM, xác minh rõ converter nào sở hữu các collection/array.

### 10.7 Sao chép bí mật có chi phí và vòng đời

Sao chép `byte[]` hoặc `char[]` chứa bí mật tạo **thêm bản sao trong bộ nhớ**, có ảnh hưởng đến hiệu năng và thời gian lưu bí mật. Chọn ranh giới sở hữu rõ ràng, giảm số bản copy không cần thiết, xóa mảng nhạy cảm thuộc sở hữu của mình khi phù hợp và không trả raw secret nếu API có thể thay bằng phép kiểm tra/ủy quyền ở bên trong. Xóa array không bảo đảm loại bỏ mọi bản sao do JVM, native library hoặc hệ điều hành tạo. Nếu mục tiêu là **không bao giờ tiết lộ private key**, tốt hơn là không trả key bytes, mà cung cấp operation ký thông qua keystore/HSM theo chính sách cho phép.

## 11. Thiết kế API theo mức rủi ro

Trước khi chọn kiểu trả về, tự đặt ba câu hỏi: **người gọi cần dữ liệu gì, cần thao tác gì, và họ có nên sở hữu dữ liệu này không?**

| Nhu cầu thật | Thiết kế nên cân nhắc | Cần tránh |
| --- | --- | --- |
| Trả danh sách tên quyền | `List.copyOf` từ chuỗi immutable, hoặc getter trả immutable snapshot. | `return mutableRoles;` hoặc unmodifiable view của danh sách vẫn bị sửa từ phía khác ngoài policy. |
| Nhận hash/mã bằng chứng ở dạng mảng | Clone khi nhận, kiểm tra snapshot, clone khi trả nếu buộc phải trả. | Kiểm tra mảng nguồn rồi giữ alias của caller. |
| Trả cấu hình lồng nhau | Immutable DTO/value object và copy ở mọi cấp mutable. | `Map.copyOf` rồi cho rằng nested `List` đã immutable. |
| Bên ngoài chỉ cần biết người dùng có quyền | `boolean isAllowed(...)` hoặc DTO tối thiểu, kiểm tra ủy quyền ở đúng biên. | Getter trả `RoleAdminService` để caller tự dò quyền. |
| Cần thực hiện một thao tác đặc quyền | Method giới hạn thao tác + kiểm tra quyền/điều kiện ở bên server/owner, audit cần thiết. | Cấp quyền truy cập toàn bộ kết nối/service/handle. |
| Cần quan sát collection live có thay đổi | Unmodifiable view khi **cố ý** cho thấy thay đổi từ owner và đảm bảo không lộ nội dung nhạy cảm. | Gọi view live là "immutable snapshot". |

Không nhất thiết deep-copy mọi thứ. Sao chép có chọn lọc theo **ownership** và cấu trúc mutable sẽ rõ ràng hơn, nhanh hơn và ít lộ bí mật hơn. Có thể chia sẻ giá trị immutable an toàn khi đáp ứng hợp đồng; còn một đối tượng quyền cao không trở nên vô hại chỉ vì bản thân nó không có setter dữ liệu.

## 12. Kiểm thử bảo vệ trạng thái và quyền

Một phương thức getter tốt cần chịu được **hai phép thử độc lập**: sửa dữ liệu người gọi giữ từ trước và sửa dữ liệu người gọi nhận sau đó. Với nested container, thêm phép thử thay đổi **phần tử con**. Với privileged reference, kiểm thử bề mặt API không cho caller truy cập method có quyền.

Checklist kiểm thử:

1. **Inbound mutation:** tạo object bằng `byte[]`/`List` mutable, sửa nguồn sau constructor, kiểm tra object giữ đúng snapshot ban đầu.
2. **Outbound mutation:** lấy dữ liệu từ getter, thử sửa bản nhận được, xác nhận trạng thái nội bộ không đổi; nếu getter trả immutable collection, kỳ vọng mutator ném `UnsupportedOperationException`.
3. **Nested mutation:** đặt một mutable element trong collection, sửa thuộc tính của phần tử từ alias bên ngoài và từ phần tử lấy qua getter; nếu policy yêu cầu immutable snapshot, phải không đổi.
4. **Identity và owner:** xác nhận API không vô tình trả reference nội bộ cần giữ riêng; với `copyOf` không yêu cầu object identity khác khi đầu vào đã immutable.
5. **Validation boundary:** đưa vào giá trị bị cấm, sửa đầu vào trước/sau kiểm tra và xác nhận quyết định dựa trên snapshot thực sự được lưu. Với concurrency, thử theo giao thức đồng bộ đã thiết kế.
6. **Capability boundary:** từ API công khai, bên không có quyền không lấy được object quản trị/handle hoặc gọi operation đặc quyền; authorization phải kiểm tra quyền trong operation thực tế.
7. **Record/serializer:** xác minh defensive copy cả ở compact constructor và accessor; nếu dùng deserialization, kiểm tra các đường khởi tạo/binding tương ứng.
8. **Regression:** thử nhiều lần, dữ liệu rỗng, `null`, dữ liệu lớn và các trường hợp nhiều cấp mutable theo hợp đồng API.

### Bài tập tự làm

**Bài 1 — Sửa API:** cho lớp:

```java
final class AuditPolicy {
    private final List<String> allowed;

    AuditPolicy(List<String> allowed) {
        this.allowed = allowed;
    }

    List<String> allowed() {
        return allowed;
    }
}
```

Hãy sửa sao cho người dùng không thể thay đổi nội dung danh sách đã lưu qua nguồn hoặc qua getter, giả định mỗi phần tử là `String`. Gợi ý: `List.copyOf` trong constructor và có thể trả trực tiếp **list immutable** đã lưu. Nêu rõ tại sao trường hợp này không cần copy ở getter như mảng.

**Bài 2 — Đánh giá nested values:** thay `List<String>` bằng `List<MutableRule>`. Giải thích vì sao `List.copyOf` chưa đủ và sửa kiểu lưu trữ thành `List<Rule>` hoặc DTO bất biến theo hợp đồng.

**Bài 3 — Tìm capability leak:** kiểm tra một lớp có `getConnection()`, `getRoleManager()` hoặc `getOutputStream()`. Xác định thao tác caller có thể gọi, nhu cầu nghiệp vụ thực sự, và một API thay thế giới hạn quyền.

**Bài 4 — Race và bất biến:** với một input list do thread khác có thể sửa, giải thích vì sao "validate nguồn rồi copy" sai và khi nào "copy rồi validate" vẫn cần đồng bộ.

Bạn nắm vững bài này khi có thể chỉ ra **đường đi của từng tham chiếu**, xác định mỗi object nào mutable hoặc mang quyền, rồi chứng minh rằng dữ liệu được kiểm tra chính là dữ liệu được dùng và caller không thể vượt qua biên API để sửa trạng thái hay thao tác đặc quyền.

## 13. Tài liệu đối chiếu

- Oracle Java 17 Javadoc: [`Collection` – unmodifiable views](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html), [`List.copyOf`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)), [`Map.copyOf`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html#copyOf(java.util.Map)).
- Oracle Java 17 Javadoc: [`Collections.unmodifiableList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#unmodifiableList(java.util.List)), [`ByteBuffer.asReadOnlyBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html#asReadOnlyBuffer()), [`Record`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html).
- Oracle Java 17 Javadoc: [`Date`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Date.html), [`Instant`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Instant.html).
- MITRE CWE: [CWE-374 – Passing Mutable Objects to an Untrusted Method](https://cwe.mitre.org/data/definitions/374.html), [CWE-375 – Returning a Mutable Object to an Untrusted Caller](https://cwe.mitre.org/data/definitions/375.html).

Các đặc tả API mô tả hành vi của collection và kiểu dữ liệu; **threat model, ownership, quyền gọi operation và chính sách validation** phải được xác định riêng cho hệ thống đang triển khai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-007">21.7 Deserialization, ObjectInputFilter và giới hạn của danh sách lớp được phép</a>

<details>
<summary>Xem chi tiết</summary>

# 21.7 Deserialization, ObjectInputFilter và giới hạn của danh sách lớp được phép

## 1. Deserialization là gì và vì sao phải xem là một ranh giới tin cậy?

**Serialization** của Java chuyển một đồ thị đối tượng thành luồng byte bằng `ObjectOutputStream.writeObject(...)`; **deserialization** tái tạo các đối tượng từ luồng đó bằng `ObjectInputStream.readObject()`. Một đối tượng có thể tham chiếu đến nhiều đối tượng khác, tạo mảng, collection và những quan hệ dùng chung/cyclic references. Java native serialization thường liên quan `Serializable`/`Externalizable`, mô tả lớp, trường và trạng thái được mã hóa trong Java Object Serialization Stream Protocol.

Điểm khác với việc chỉ parse dữ liệu đơn giản: khi `readObject()` xử lý một luồng, runtime **chọn và tạo các lớp thực**, phục hồi trường, xử lý tham chiếu và có thể chạy logic do lớp cung cấp như `readObject`, `readResolve`, `readExternal`, `readObjectNoData` hoặc callback kiểm tra dữ liệu. Vì vậy, byte đầu vào không chỉ chứa “giá trị”: nó còn có thể điều khiển những loại đối tượng sẽ được khởi tạo và các hành vi phát sinh trong lúc đọc.

```text
Byte input (network/file/message)
          |
          v
   ObjectInputStream.readObject()
          |
          +--> resolve class / object graph
          +--> ObjectInputFilter checks (nếu được cấu hình)
          +--> allocate objects/arrays + restore references
          +--> class-specific deserialization hooks
          |
          v
     application receives object
          |
          v
  type check + field/business validation
```

**Quy tắc ưu tiên:** tránh native Java deserialization từ nguồn không tin cậy. Khi bắt buộc tương tác với một hệ thống legacy, phải đặt bộ lọc lớp + giới hạn tài nguyên + xác minh dữ liệu + giới hạn lớp giao thức/vận chuyển. `ObjectInputFilter` là lớp **giảm rủi ro** hữu ích, nhưng không biến Java deserialization thành trình phân tích dữ liệu an toàn tuyệt đối.

Ví dụ tình huống rủi ro: server nhận `byte[]` từ một request mạng, gọi `readObject()`, rồi mới cast kết quả sang `OrderRequest`. Việc cast xảy ra **sau khi quá trình deserialization đã bắt đầu**, nên `(OrderRequest) in.readObject()` **không chặn** những lớp không mong muốn được xử lý trong quá trình đọc.

## 2. Các nhóm rủi ro cần phòng vệ

| Nhóm rủi ro | Cơ chế thường gặp | Biện pháp trọng tâm |
| --- | --- | --- |
| Hành vi ngoài dự kiến trong lúc khôi phục đối tượng | Một lớp có hook `readObject`, `readResolve`, `readExternal` hoặc thư viện chứa object graph với hành vi không mong muốn | Không nhận serialized data không tin cậy; allowlist nhỏ theo từng endpoint; tránh thư viện/lớp có hook rủi ro. |
| “Gadget chain” | Nhiều lớp vốn hợp lệ kết hợp thành chuỗi tác dụng phụ khi deserialization | Kiểm soát classpath, cập nhật thư viện, giảm số lớp được chấp nhận; không coi allowlist package rộng là an toàn. |
| Từ chối dịch vụ (DoS) | Đồ thị quá sâu/nhiều tham chiếu, mảng lớn, luồng byte lớn hoặc xử lý tùy biến tốn CPU | Giới hạn `maxdepth`, `maxrefs`, `maxarray`, `maxbytes`; đồng thời giới hạn input, thời gian, CPU, bộ nhớ. |
| Dữ liệu sai quy tắc nghiệp vụ | Object có trường `null`, giá trị ngoài miền, chuỗi quá dài, ID sai quyền | Kiểm tra bất biến trong logic đọc và sau khi đọc; xác thực ủy quyền riêng. |
| Thay đổi phụ thuộc và schema | Cập nhật thư viện khiến đồ thị đối tượng có lớp mới; đổi `serialVersionUID` hoặc kiểu trường | Kiểm thử tương thích, đánh giá lại allowlist, versioning dữ liệu và cơ chế chuyển đổi. |
| Tin nhầm dữ liệu “từ nội bộ” | File cache, queue hoặc backup có thể bị sửa, thay thế hoặc đến từ producer không đáng tin | Xác thực nguồn, kiểm tra toàn vẹn, phân quyền truy cập, kiểm tra dữ liệu đầu vào như nguồn ngoài. |

Transport TLS bảo vệ một số thuộc tính của kết nối, **không chứng minh nội dung đối tượng đã được kiểm tra hợp lệ**. Một thư viện được đặt trên allowlist cũng chưa chắc an toàn trước mọi payload.

## 3. ObjectInputFilter: chức năng và thời điểm áp dụng

Từ Java 9, `java.io.ObjectInputFilter` cho phép yêu cầu `ObjectInputStream` kiểm tra **lớp được nạp, mảng và độ phức tạp của đồ thị** trong quá trình deserialization. Java 17 bổ sung các cơ chế tiện ích và filter factory theo ngữ cảnh (JEP 415). Trong Java 17+, các loại cơ bản gồm:

- `ObjectInputFilter`: interface hàm `checkInput(FilterInfo info)`.
- `ObjectInputFilter.FilterInfo`: cung cấp `serialClass()`, `arrayLength()`, `depth()`, `references()` và `streamBytes()`.
- `ObjectInputFilter.Status`: `ALLOWED`, `REJECTED`, `UNDECIDED`.
- `ObjectInputFilter.Config`: tạo filter theo pattern, đọc/thiết lập filter JVM-wide và cấu hình filter factory.
- `ObjectInputStream.setObjectInputFilter(filter)`: yêu cầu đặt filter cho **một stream** trước khi gọi các thao tác đọc object.

Các giá trị `FilterInfo` cần hiểu chính xác:

| Thuộc tính | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `serialClass()` | `Class<?>` đang được kiểm tra; có thể là `null` ở những lần chỉ kiểm tra metadata/tham chiếu | `null` **không đồng nghĩa “class được phép”**; vẫn cần xét giới hạn đồ thị. |
| `arrayLength()` | Số phần tử của mảng hiện tại | Thường là `-1` khi không kiểm tra mảng. Giới hạn theo số phần tử, không đồng nghĩa số byte bộ nhớ. |
| `depth()` | Độ sâu hiện tại của quá trình đọc object graph | Không phải số cấp kế thừa; càng sâu có thể càng tốn stack/tài nguyên. |
| `references()` | Số lượng tham chiếu/đối tượng lũy kế mà stream xử lý | Không phải số đối tượng phân biệt trong heap; reference lặp cũng có thể ảnh hưởng. |
| `streamBytes()` | Số byte đã tiêu thụ từ luồng tính tại thời điểm kiểm tra | Không phải một bộ giới hạn tuyệt đối có thể thay thế input framing. |

**Không mặc định filter được gọi cho mọi giá trị.** Java native serialization không gọi filter cho primitive values, và không gọi khi một `String` được mã hóa trực tiếp theo dạng string trong stream. Mảng (kể cả mảng primitive/String/object) có các điểm kiểm tra class/length; kiểm tra tham chiếu đã đọc có thể mang `serialClass() == null`. Không được dùng callback filter như nơi kiểm tra nội dung từng chuỗi, giá trị trường hay mọi method do lớp thực thi.

Khi filter trả `REJECTED`, `ObjectInputStream` chặn thao tác đọc và thường phát sinh `InvalidClassException`. Khi filter trả `UNDECIDED`, **điều đó tự nó không phải từ chối**: nếu không có bộ lọc khác đưa ra `REJECTED`, quá trình đọc có thể tiếp tục. `ALLOWED` chỉ nói kiểm tra tại thời điểm đó đã chấp thuận; không phải cấp quyền cho toàn bộ phần còn lại của stream.

### 3.1. Ba trạng thái và cách dùng “fail closed”

```text
checkInput(info)
   +--> REJECTED  -> dừng deserialization (InvalidClassException)
   +--> ALLOWED   -> tiếp tục kiểm tra những phần tiếp theo
   +--> UNDECIDED -> không quyết định; có thể tiếp tục nếu không ai từ chối
```

Một bộ lọc kiểu **allowlist đóng** nên từ chối mọi class không thuộc tập cho phép, đồng thời vẫn đánh giá giới hạn depth/refs/bytes ở những lần `serialClass()==null`. Java 17 có `ObjectInputFilter.rejectUndecidedClass(filter)` để chuyển các quyết định `UNDECIDED` về class sang từ chối theo quy tắc của API. Tuy nhiên, helper này xử lý primitive/array có ngoại lệ được mô tả trong tài liệu; vẫn cần thử với đồ thị thực.

Nếu dùng nhiều filter, cần hiểu filter nào quyết định cuối cùng. Ví dụ, `ObjectInputFilter.merge(a, b)` cho phép ghép kiểm tra; kết quả `REJECTED` của một nhánh phải thắng `ALLOWED` của nhánh khác, còn `UNDECIDED` cần chính sách xử lý phù hợp. Một bộ lọc rộng hoặc factory thay thế filter cũ có thể làm yếu chính sách ngoài ý muốn.

## 4. Giới hạn tài nguyên: lớp được phép chưa đủ

Một filter bảo vệ tốt phải kiểm tra **hai chiều độc lập**:

1. **Loại đối tượng:** chỉ các lớp cần cho schema đang được đọc, bao gồm lớp con, mảng, superclass/collection cụ thể mà serialization thực sự yêu cầu.
2. **Hình dạng đồ thị và chi phí xử lý:** giới hạn độ sâu, số reference, kích thước mảng và số byte.

Các giới hạn theo pattern thường là:

```text
maxdepth=8
maxrefs=100
maxarray=1000
maxbytes=65536
```

Đây chỉ là giá trị để **minh họa**, không phải ngưỡng bảo mật chung. Một DTO nhỏ có thể cần `maxarray=16`; một batch hợp lệ có thể cần giới hạn khác. Thử dữ liệu bình thường lẫn dữ liệu gần ngưỡng để chọn giới hạn; **không đặt rất cao cho “dễ chạy”** rồi coi như đã phòng DoS.

Ngay cả với `maxbytes`, filter chỉ quan sát lúc được gọi; nếu code đọc riêng frame rất lớn, giữ `byte[]` trong RAM, giải nén hoặc thực thi callback tốn CPU, chi phí đã phát sinh trước/ngoài một số điểm kiểm tra. Hãy:

- Giới hạn kích cỡ request/file/message **trước** khi tạo `ObjectInputStream`.
- Cấu hình timeout và hạn mức bộ nhớ/CPU theo tiến trình; chặn dữ liệu nén gây phình bất thường.
- Đảm bảo số lượng kết nối và deserialization đồng thời có giới hạn.
- Kiểm tra số lượng phần tử, độ dài chuỗi, mối quan hệ khóa/giá trị và tính hợp lệ của từng trường.
- Không ghi log toàn bộ serialized payload hoặc thông tin nhạy cảm.

## 5. Filter pattern: cú pháp, lợi ích và bẫy thường gặp

Có thể tạo filter từ chuỗi pattern mà không cần viết callback thủ công:

```java
ObjectInputFilter f = ObjectInputFilter.Config.createFilter(
    "maxdepth=8;maxrefs=100;maxarray=1000;maxbytes=65536;"
  + "com.example.dto.SafeMessage;java.base/java.util.ArrayList;!*");
```

**Đoạn này minh họa cú pháp**, vì `com.example.dto.SafeMessage` không có sẵn trong bài. Dấu `;` phân tách pattern; các giới hạn `max...` được áp dụng trước; các pattern class được xét **từ trái sang phải**, match đầu tiên quyết định cho class đó.

| Pattern | Nghĩa |
| --- | --- |
| `com.example.dto.SafeMessage` | Cho phép class có tên chính xác này. |
| `com.example.dto.*` | Cho phép các class trực tiếp trong package này; **không** tự bao gồm subpackage. |
| `com.example.dto.**` | Cho phép package này **và các subpackage**. |
| `!com.example.dto.LegacyDanger` | Từ chối class có tên đã chỉ định. |
| `java.base/java.util.ArrayList` | Match class `java.util.ArrayList` trong module `java.base`. |
| `!*` | Từ chối các class còn lại; đặt sau các allow-pattern nếu muốn allowlist đóng. |

**Điểm dễ sai nhất:** bỏ `!*` thì class không match có thể nhận `UNDECIDED`; `UNDECIDED` không tự tương đương từ chối. Ví dụ chuỗi `com.example.dto.*` **không tự tạo whitelist an toàn** nếu các class khác vẫn được xét là undecided.

Một số giới hạn của pattern:

- `java.util.*` hoặc `java.base/*` thường quá rộng nếu mục tiêu là hạn chế những lớp có thể thực thi logic ngoài dự kiến.
- Pattern chỉ match **tên lớp/module**, không đánh giá constructor/hook, annotation, cấp quyền, giá trị dữ liệu, trạng thái của object hoặc nội dung từng field.
- Với array, pattern dùng **kiểu phần tử** để match; nhiều chiều mảng cùng cơ sở phần tử được xử lý theo cách đó. `maxarray` không đặt được ngưỡng **khác nhau theo từng class** bằng một pattern đơn giản.
- Pattern không tự mở rộng theo interface/superclass mà một lớp implement/kế thừa; runtime chỉ kiểm tra các class/interface thực sự xuất hiện theo cơ chế serialization.
- Khoảng trắng trong chuỗi pattern **có ý nghĩa**: không tùy tiện chèn khoảng trắng sau dấu `;`.
- Khi stream phức tạp chứa collection, proxy, comparator, map implementation hoặc nested type, phải liệt kê đầy đủ những loại **thực sự cần thiết**; một wildcard rộng để “chữa lỗi” có thể phá vỡ chủ đích allowlist.

Để dùng filter pattern từ lệnh khởi động của ứng dụng, có thể đặt JVM system property (ví dụ shell POSIX):

```bash
java '-Djdk.serialFilter=maxdepth=8;maxrefs=100;maxarray=1000;maxbytes=65536;com.example.dto.SafeMessage;!*' com.example.Main
```

Lệnh trên chỉ là **mẫu khởi động**: cần thay `com.example.Main` bằng ứng dụng thật, xác định các lớp trong object graph và quote phù hợp shell. Cũng có thể khai báo `jdk.serialFilter=...` dưới dạng security property trong file `java.security` của JDK. Nếu cùng cấu hình cả hai, **system property có ưu tiên** hơn security property.

**Không dùng `System.setProperty("jdk.serialFilter", "...")` sau khi deserialization đã được khởi tạo để hy vọng filter JVM-wide cập nhật**. Việc nạp property/thiết lập filter thuộc giai đoạn khởi tạo của cơ chế lọc.

## 6. Ví dụ Java 17+ chạy được: allowlist + giới hạn đồ thị + kiểm tra dữ liệu

Ví dụ độc lập này cố ý dùng **hai lớp bình thường** để chứng minh việc chấp thuận và từ chối class; nó không mô phỏng gadget hay payload khai thác. Luồng byte được tạo trong cùng tiến trình để quan sát API; không phải mô hình trao đổi dữ liệu không tin cậy trong production.

- Chấp nhận duy nhất `AllowedMessage` và mảng `int[]`.
- Chặn class `UnexpectedMessage`, dù class đó cũng implement `Serializable`.
- Chặn mảng dài hơn năm phần tử; giới hạn thêm depth, references và bytes.
- Khi đọc xong, kiểm tra root type và các trường của `AllowedMessage`.
- Đặt filter **trước** lần `readObject()` đầu tiên; đóng stream bằng try-with-resources.

Lưu toàn bộ thành **`DeserializationFilterDemo.java`**:

```java
import java.io.*;
import java.util.Arrays;

public class DeserializationFilterDemo {
    private static final int MAX_BYTES = 1024;

    private static final class AllowedMessage implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        final String user;
        final int[] scores;

        AllowedMessage(String user, int[] scores) {
            this.user = user;
            this.scores = scores;
        }
    }

    private static final class UnexpectedMessage implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        final String note = "unexpected";
    }

    static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    static AllowedMessage readTrusted(byte[] bytes)
            throws IOException, ClassNotFoundException {
        if (bytes.length > MAX_BYTES) {
            throw new InvalidObjectException("Serialized frame too large");
        }

        ObjectInputFilter filter = info -> {
            if (info.depth() > 6 || info.references() > 32
                    || info.streamBytes() > MAX_BYTES
                    || info.arrayLength() > 5) {
                return ObjectInputFilter.Status.REJECTED;
            }

            Class<?> type = info.serialClass();
            if (type == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            if (type == AllowedMessage.class || type == int[].class) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            return ObjectInputFilter.Status.REJECTED;
        };

        try (ObjectInputStream in =
                     new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(filter);
            Object value = in.readObject();

            if (!(value instanceof AllowedMessage message)) {
                throw new InvalidObjectException("Unexpected root type");
            }
            if (message.user == null || message.user.length() > 20
                    || message.scores == null || message.scores.length > 5) {
                throw new InvalidObjectException("Invalid message fields");
            }
            return message;
        }
    }

    public static void main(String[] args) throws Exception {
        AllowedMessage accepted =
            readTrusted(serialize(new AllowedMessage("lan", new int[] {2, 3})));
        System.out.println("Accepted: " + accepted.user + " "
            + Arrays.toString(accepted.scores));

        try {
            readTrusted(serialize(new UnexpectedMessage()));
            System.out.println("Unexpected class: ALLOWED");
        } catch (InvalidClassException exception) {
            System.out.println("Unexpected class: REJECTED");
        }

        try {
            readTrusted(serialize(new AllowedMessage("lan", new int[7])));
            System.out.println("Oversized array: ALLOWED");
        } catch (InvalidClassException exception) {
            System.out.println("Oversized array: REJECTED");
        }
    }
}
```

Biên dịch và chạy với **JDK 17+**:

```bash
javac --release 17 DeserializationFilterDemo.java
java DeserializationFilterDemo
```

Stdout **đã được kiểm chứng khi biên dịch với `--release 17` và chạy trên JDK 24**:

```text
Accepted: lan [2, 3]
Unexpected class: REJECTED
Oversized array: REJECTED
```

### 6.1. Vì sao ba kết quả đó xuất hiện?

**Trường hợp hợp lệ:** `AllowedMessage` là lớp được chỉ định rõ; mảng `int[]` có hai phần tử; `String` trong trường `user` được xử lý theo serialization của Java và **không đảm bảo filter class sẽ thấy nó dưới dạng một class check riêng**. Các giới hạn tài nguyên không vượt ngưỡng; ứng dụng kiểm tra trường sau `readObject` rồi in thông tin.

**Trường hợp class lạ:** runtime gặp `UnexpectedMessage` và gọi filter; lớp không nằm trong hai type cho phép nên trả `REJECTED`. `ObjectInputStream` ném `InvalidClassException`. Class này chỉ dùng mô phỏng việc từ chối một type không mong đợi; không chứa mã nguy hiểm.

**Trường hợp mảng quá lớn:** root `AllowedMessage` có thể được xét là lớp hợp lệ, nhưng khi gặp `int[7]`, `arrayLength() > 5` làm filter trả `REJECTED`. Điều này cho thấy **cho phép class không đồng nghĩa cho phép mọi kích cỡ dữ liệu của class ấy**.

**Vì sao không chỉ cast kết quả?** `instanceof AllowedMessage` sau `readObject()` chỉ kiểm tra kết quả cuối cùng; nó không thể thay thế kiểm soát những class được xử lý trước đó. Vẫn phải giữ cả hai: filter trong quá trình đọc và validation ứng dụng sau đọc.

**Vì sao check `bytes.length` thêm lần nữa?** `streamBytes()` chỉ được kiểm tra tại những thời điểm gọi filter, trong khi `byte[]` đầu vào đã được tải vào bộ nhớ. Chặn khung dữ liệu lớn **trước khi tạo stream** là lớp bảo vệ riêng. Trong ứng dụng mạng, giới hạn kích thước phải nằm ở tầng đọc request/frame, không chỉ ở mảng đã được cấp phát.

### 6.2. Điều chỉnh demo cho ứng dụng thật

Không copy nguyên allowlist hai class trên vào endpoint bất kỳ. Trước hết phải xác định **schema đồ thị** và sở hữu tất cả các class được phép:

1. Dùng DTO riêng, đơn giản, chỉ chứa kiểu trường cần thiết; ưu tiên immutable design và ít phụ thuộc.
2. Thêm đúng collection/array/loại dữ liệu hợp lệ. Mỗi type bổ sung phải được xem xét, đặc biệt các lớp có custom deserialization hooks.
3. Đặt giới hạn resource theo payload hợp lệ và ngân sách hệ thống; test các giá trị biên.
4. Xác minh trường trong lớp DTO hoặc validator sau đọc; nếu đối tượng có thể có side effect ngay khi deserialization thì **validation sau đọc là quá muộn để bảo vệ side effect đó**.
5. Xử lý `InvalidClassException`, `InvalidObjectException`, `StreamCorruptedException`, `ClassNotFoundException` và các `IOException` bằng đường từ chối hợp lệ; không retry cùng payload bằng cách bỏ filter.

Trong trường hợp có filter JVM-wide/factory riêng, cần xác minh chính sách được chọn và kết hợp đúng; demo dùng một stream riêng trong JVM không có chính sách tùy biến để minh họa cơ chế cơ bản.

## 7. Ba phạm vi cấu hình filter: per-stream, JVM-wide và factory

### 7.1. Stream-specific filter

```java
try (ObjectInputStream in = new ObjectInputStream(inputStream)) {
    in.setObjectInputFilter(localFilter);
    Object value = in.readObject();
}
```

Đây là **snippet minh họa**; `inputStream` và `localFilter` phải do ứng dụng chuẩn bị. Gọi `setObjectInputFilter` trước khi đọc object, đúng một lần theo hợp đồng API. Phạm vi của filter là **stream đó**, phù hợp khi một ứng dụng có nhiều endpoint với schema khác nhau.

### 7.2. JVM-wide filter

Thiết lập bằng `-Djdk.serialFilter=...` trước khởi động, security property `jdk.serialFilter`, hoặc `ObjectInputFilter.Config.setSerialFilter(filter)` tại giai đoạn khởi tạo (nếu chưa thiết lập). `setSerialFilter` không phải một công tắc thay qua lại tùy request; gọi lại sau khi đã cấu hình sẽ thất bại.

```java
// Snippet minh họa chạy rất sớm ở bootstrap, khi chưa có filter JVM-wide.
ObjectInputFilter global =
    ObjectInputFilter.Config.createFilter("maxdepth=20;maxrefs=1000;maxarray=10000");
ObjectInputFilter.Config.setSerialFilter(global);
```

Vì pattern trên **chỉ giới hạn tài nguyên, không xét class**, nó không thay cho allowlist cụ thể của một endpoint. Nếu JVM đã được quản trị bằng `jdk.serialFilter`, không thử gọi `setSerialFilter` lần nữa.

### 7.3. Filter factory theo ngữ cảnh (Java 17+, JEP 415)

**Filter factory** chọn hoặc phối hợp filter khi `ObjectInputStream` được tạo và khi một stream gọi `setObjectInputFilter`. Nó có thể dựa trên caller, module, thread context hoặc policy ứng dụng, nên thích hợp cho hệ thống có nhiều thư viện/endpoint cần các tập class khác nhau.

Một điểm rất quan trọng: **factory mặc định có thể thay filter JVM-wide bằng filter stream-specific khi gọi `setObjectInputFilter`**. Do đó không được mặc định rằng filter toàn JVM và filter cục bộ luôn được áp dụng đồng thời. Phải kiểm tra factory hiện hành và cách nó kết hợp filter; nếu chính sách cần cả hai, cấu hình một factory đáng tin để không làm mất ràng buộc toàn cục. Có thể dùng API `ObjectInputFilter.merge` và `rejectUndecidedClass`, nhưng phải hiểu thứ tự/điều kiện kết hợp và viết test xác minh.

`ObjectInputFilter.Config.setSerialFilterFactory(...)` chỉ được thiết lập theo hợp đồng API ở giai đoạn rất sớm, **trước khi có `ObjectInputStream` đầu tiên**. Ngoài API, có các property `jdk.serialFilterFactory` để thiết lập factory tại startup. Đây là một cấu hình **toàn JVM**, có thể tác động cả thư viện bên thứ ba; triển khai thử nghiệm trước khi áp dụng.

Đừng dùng filter factory như nơi thực thi logic nghiệp vụ hoặc cấp quyền người dùng. Mục đích của nó là **chọn chính sách lọc deserialization thích hợp**, còn xác thực/ủy quyền và tính hợp lệ của dữ liệu do các lớp khác chịu trách nhiệm.

## 8. Giới hạn thực sự của allowlist: “lớp được phép” chưa có nghĩa “đối tượng an toàn”

Allowlist là biện pháp phòng thủ **theo loại lớp**, không phải kiểm chứng hành vi hoặc dữ liệu. Những lỗ hổng hiểu nhầm sau cần đặc biệt chú ý.

### 8.1. Class được phép vẫn có thể có hành vi nguy hiểm

Một lớp nằm trong allowlist có thể chứa `readObject`, `readResolve` hoặc callback khôi phục trạng thái có tác dụng phụ: truy cập file, thay đổi trạng thái hệ thống, tạo đối tượng khác, gọi mã thư viện, hoặc xử lý dữ liệu quá tốn kém. Filter không chứng minh những phương thức đó an toàn.

Đừng tạo allowlist bằng cách cho phép cả package `com.company.**` chỉ vì đó là mã “nội bộ”. Mã nội bộ cũng có thể gọi thư viện phức tạp, thay đổi theo phiên bản hoặc chứa những hook chưa được review.

### 8.2. Filter không kiểm tra giá trị từng trường

Filter không biết `price` âm có hợp lệ không, `userId` có được quyền xem dữ liệu không, hoặc một `String` có chứa biểu thức/tài liệu nguy hiểm ở tầng ứng dụng hay không. `String` được mã hóa trực tiếp thường **không tạo sự kiện class filtering**, nên allowlist `java.lang.String` không phải cách giới hạn nội dung/độ dài chuỗi.

Phải kiểm tra **schema, range, định dạng, độ dài, enum, quyền và quan hệ bất biến** bằng validation của ứng dụng. Khi dùng custom `readObject` để kiểm tra invariants, logic đó cũng phải được thiết kế an toàn bởi nó được thực thi trong quá trình deserialization.

### 8.3. Không phải mọi loại DoS đều bị chặn bởi bốn giới hạn

Một đối tượng nhỏ trên wire vẫn có thể kích hoạt xử lý CPU tốn kém hoặc tạo dữ liệu thứ cấp; dữ liệu nén có thể phình sau giải nén; một `String` cực dài có thể tiêu thụ bộ nhớ; một graph nằm dưới ngưỡng giới hạn vẫn có thể gây chi phí lớn nếu có rất nhiều kết nối đồng thời. Các chỉ số filter không phải **CPU budget**, transaction limit hay full memory sandbox.

Dùng thêm quota, timeout, giới hạn connection/concurrency, điều kiện cấp phát bộ nhớ, kích thước input/chuỗi/collection ở các lớp nhận dữ liệu và tài nguyên của tiến trình.

### 8.4. Không phải filter callback nào cũng xảy ra ở mọi thời điểm

Filter nhận các sự kiện do Object Serialization Protocol phát sinh; callback không phải một hook tổng quát “trước mọi lệnh Java”. Những primitive values và `String` mã hóa trực tiếp không đi qua kiểm tra class thông thường. Mã custom deserialization, runtime class resolution và tương tác library có các đặc tính riêng.

Không dùng `FilterInfo` như công cụ xác minh đã quan sát **tất cả** dữ liệu hay “mọi instance cùng lớp”. Dùng nó làm cơ chế chặn bổ sung cho type và các metrics mà API thực sự cung cấp.

### 8.5. Khó bảo trì whitelist khi object graph phức tạp

Một `HashMap` có key/value/superclass/helper khác nhau; một class mới trong dependency có thể làm payload cũ không đọc được. Nới lọc bằng `java.*` để ứng dụng chạy lại sẽ tạo vùng cho phép quá rộng. Khi cập nhật thư viện hoặc thay DTO, phải quan sát các class xuất hiện trong **payload được phép**, sửa whitelist có kiểm soát và chạy regression tests. Không biến `UNDECIDED` thành “cho phép mặc định” do sợ ảnh hưởng tương thích.

### 8.6. ClassLoader, proxy, subclass và định danh class

Tên trên allowlist không nhất thiết đủ để xác nhận nguồn gốc/trust của bytecode trong môi trường có custom class loader. Proxy, dynamic types, array và polymorphism có thể tạo object graph khác mong đợi. Với dữ liệu từ nguồn ngoài, giới hạn các cơ chế đa hình và chỉ dùng loại DTO/thư viện đã được kiểm tra. Không coi một package pattern là danh tính bất biến của mã.

### 8.7. Lọc object không thay thế xác thực nguồn và quyền truy cập

Một payload dùng toàn class hợp lệ vẫn có thể yêu cầu thao tác trái phép. Chỉ sau khi đọc và xác minh dữ liệu, ứng dụng mới được áp dụng authorization dựa trên principal đã xác thực, tenant, resource và hành động. Không tin trường `isAdmin` hoặc `role` trong serialized payload chỉ vì class của nó được cho phép.

## 9. Khi vẫn cần native deserialization: quy trình phòng vệ nhiều lớp

```text
   Data source / peer identity / integrity
                    |
                    v
       Max message size + timeout
                    |
                    v
     Trusted protocol / frame validation
                    |
                    v
  ObjectInputStream with effective filters
    class allowlist + graph resource limits
                    |
                    v
     DTO type + invariant validation
                    |
                    v
        Authorization + business rules
                    |
                    v
             Application action
```

Nên triển khai theo thứ tự:

1. **Loại trừ native serialization nếu có thể.** Đối với API mới, cân nhắc JSON/CBOR/Protocol Buffers hoặc format có schema rõ, parser có giới hạn và binding vào một tập DTO định nghĩa trước. Format khác vẫn phải được kiểm tra dữ liệu và cấu hình tránh polymorphic deserialization tùy ý.
2. **Xác định trust boundary.** Ai tạo file/queue/message, ai có quyền thay byte, khi nào cache có thể bị ghi đè và liệu producer có thuộc cùng chính sách an toàn.
3. **Giới hạn dữ liệu ngay khi nhận.** Giới hạn frame và tốc độ/size đọc, kích thước file, timeout, đồng thời số phiên; không chờ đến khi có `byte[]` cực lớn.
4. **Chỉ cho phép những class thực sự cần.** Dùng policy riêng theo endpoint/consumer, không dùng allowlist toàn package khi có thể dùng danh sách class cụ thể; chặn class lạ và giới hạn các mảng.
5. **Kết hợp đúng filter.** Nếu có JVM-wide filter, library filter và stream filter, xác minh factory cuối cùng không vô tình thay/bỏ chính sách chặt hơn.
6. **Kiểm tra object invariants.** Độ dài trường, giá trị, số phần tử, version, quan hệ và kiểm tra quyền trước tác động nghiệp vụ.
7. **Quản lý dependency.** Cập nhật runtime và thư viện, giảm classpath và những class có hook deserialization rủi ro; theo dõi các thay đổi schema.
8. **Giám sát thất bại.** Ghi lại loại lỗi/filter status, endpoint, thống kê kích thước theo cách không lộ dữ liệu nhạy cảm; tránh in toàn bộ payload hoặc bí mật.
9. **Chạy test tích cực và tiêu cực** cùng dữ liệu phiên bản thực: class hợp lệ, class lạ, quá nhiều reference/depth/bytes, mảng dài, stream hỏng, type root sai, field sai, dữ liệu không tương thích.

Đặc biệt với những thư viện gọi `ObjectInputStream` **ẩn bên trong**, một filter cục bộ đặt ở điểm không sở hữu stream có thể không bao phủ được. Đánh giá cơ chế factory/global của JDK và khả năng cấu hình chính thức của thư viện; nếu không thể xác minh filter áp dụng đúng, không tiếp nhận Java serialized data từ nguồn không tin cậy.

## 10. Kiểm thử hiệu quả

| Test | Điều cần xác minh |
| --- | --- |
| Happy path | Đọc được đúng DTO/schema hợp lệ; các trường có giá trị mong đợi. |
| Unknown class | Từ chối `InvalidClassException` hoặc đường thất bại tương đương; không tạo tác động nghiệp vụ. |
| Array vượt ngưỡng | `maxarray` hoặc custom rule từ chối trước khi cấu trúc lớn được sử dụng. |
| Depth/references vượt ngưỡng | Dừng đọc ở trạng thái từ chối; không làm cạn stack/bộ nhớ toàn hệ thống. |
| Input quá lớn | Lớp nhận dữ liệu từ chối **trước** khi tạo luồng deserialization. |
| Trường sai | Từ chối dù class trên allowlist; ví dụ `null`, chuỗi quá dài hoặc số lượng không hợp lệ. |
| Global và stream filter cùng tồn tại | Việc đặt stream filter không làm mất chính sách toàn cục; factory được kiểm chứng. |
| Dependency/runtime update | Danh sách class thực tế và behavior vẫn đáp ứng policy sau nâng cấp JDK/thư viện. |
| Nhiều kết nối đồng thời | Hạn mức request và timeout không cho phép DoS dễ dàng. |

Khi kiểm tra log, phân biệt lỗi `InvalidClassException` do filter từ chối với lỗi không tương thích `serialVersionUID` hoặc class descriptor; cả hai đều có thể ném cùng loại exception, nhưng nguyên nhân và hướng xử lý khác nhau. Không được “chữa” bằng cách tắt filter.

## 11. Những lỗi phổ biến cần tránh

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “Payload sẽ được cast ngay nên class lạ không chạy.” | Cast diễn ra sau quá trình đọc; filter phải hoạt động trong deserialization, trước khi dữ liệu đến nghiệp vụ. |
| “Chỉ cần set `jdk.serialFilter=com.example.*` là đã có whitelist.” | Class không match có thể là `UNDECIDED`; muốn allowlist đóng phải có `!*` hoặc chính sách từ chối tương đương. |
| “`UNDECIDED` nghĩa là `REJECTED`.” | `UNDECIDED` tự nó không từ chối; cần filter phối hợp hoặc quy tắc từ chối class còn lại. |
| “Allowlist một class nên tất cả trạng thái của nó đều an toàn.” | Class có thể có custom deserialization hooks/fields bất hợp lệ/logic nặng; kiểm tra code và dữ liệu. |
| “`maxbytes` thay được HTTP body limit.” | Chỉ quan sát byte tại điểm filter được gọi; giới hạn request/frame vẫn bắt buộc. |
| “Cho phép `java.util.*` là luôn an toàn.” | Một package lớn chứa nhiều implementation và dependency; chọn tập class đúng schema. |
| “Filter kiểm tra nội dung mọi `String`.” | `String` được mã hóa trực tiếp không được kiểm tra như class thường; cần giới hạn và validation riêng. |
| “Đặt filter một stream chắc chắn kết hợp với global filter.” | Factory mặc định có thể thay filter toàn cục; phải xác minh policy cuối cùng. |
| “Filter JVM-wide có thể reset tùy request.” | Cấu hình toàn JVM là quyết định sớm, không nên dùng để định tuyến policy theo từng request. |
| “Dữ liệu đến từ máy chủ tin cậy nên không cần kiểm tra.” | Kiểm tra quyền/độ toàn vẹn/chi phí và schema vẫn cần, kể cả khi truyền qua TLS. |
| “Đã catch `ClassCastException` thì hết rủi ro.” | Trạng thái và side effect có thể đã phát sinh khi `readObject()` chạy; check type không phòng ngừa quá trình. |

## 12. Tự kiểm tra hiểu bài

1. **Vì sao `(TrustedType) in.readObject()` không phải biện pháp chặn gadget?** Vì `readObject()` xử lý object graph và hook trước khi phép cast kết quả được thực hiện.
2. **Khác nhau giữa `ALLOWED`, `REJECTED` và `UNDECIDED`?** `REJECTED` dừng đọc; `ALLOWED` chấp nhận kiểm tra hiện tại; `UNDECIDED` không tự chặn và cần chính sách xử lý bổ sung.
3. **Vì sao cuối allowlist pattern nên có `!*`?** Để những class không khớp các pattern được phép bị từ chối thay vì giữ `UNDECIDED`.
4. **`maxarray=20` có kiểm tra `String` dài 10 triệu ký tự không?** Không theo cách giới hạn array: string content phải được kiểm tra ở lớp nhận/ứng dụng và có giới hạn byte input.
5. **Tại sao demo còn check `bytes.length`?** Giúp từ chối frame quá lớn trước khi xây `ObjectInputStream`, không trông chờ những lần filter callback.
6. **Vì sao `AllowedMessage` được cho phép vẫn cần kiểm tra `user` và `scores`?** Vì lọc class không biết các quy tắc nghiệp vụ hay giá trị trường hợp lệ.
7. **Tại sao global filter có thể không bảo vệ cùng lúc với stream filter?** Vì factory quyết định filter hiệu lực; factory mặc định có thể thay global bằng stream-specific.
8. **Nếu ứng dụng cần deserialization theo nhiều endpoint thì nên làm gì?** Xác định schema/allowlist theo context và dùng stream filter hoặc factory theo ngữ cảnh được thiết kế, test và kiểm chứng rõ.
9. **Native Java deserialization có nên là định dạng API công khai mới không?** Thông thường không; ưu tiên định dạng dữ liệu có schema với kiểm tra strict types và tài nguyên.

## 13. Tài liệu tham khảo

- [Oracle Java SE 21 — Serialization Filtering](https://docs.oracle.com/en/java/javase/21/core/serialization-filtering1.html)
- [Oracle Java SE 21 — Creating Pattern-Based Filters](https://docs.oracle.com/en/java/javase/21/core/creating-pattern-based-filters.html)
- [Oracle Java SE 21 — Creating Custom Filters](https://docs.oracle.com/en/java/javase/21/core/creating-custom-filters.html)
- [Java SE 21 — ObjectInputFilter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.html)
- [Java SE 21 — ObjectInputFilter.Config](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputFilter.Config.html)
- [Java SE 21 — ObjectInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectInputStream.html)
- [OpenJDK JEP 290 — Filter Incoming Serialization Data](https://openjdk.org/jeps/290)
- [OpenJDK JEP 415 — Context-Specific Deserialization Filters](https://openjdk.org/jeps/415)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-008">21.8 Cấu hình JAXP secure processing và kiểm soát truy cập tài nguyên XML bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

# 21.8 — Cấu hình JAXP secure processing và kiểm soát truy cập tài nguyên XML bên ngoài

## 1. Vì sao xử lý XML cần giới hạn bảo mật?

**JAXP (Java API for XML Processing)** là các API chuẩn của Java để đọc XML (DOM/SAX/StAX), kiểm tra XML Schema (XSD), biến đổi XSLT và đánh giá XPath. Trong Java 17+, những API này chủ yếu nằm trong module `java.xml`. Khi XML đến từ HTTP request, upload, message broker hoặc hệ thống bên ngoài, parser có thể **tự đọc tệp hoặc gọi mạng** chỉ vì nội dung XML tham chiếu đến tài nguyên khác. Đây là nhóm vấn đề **XXE (XML External Entity)**, SSRF, truy cập tệp trái phép và từ chối dịch vụ (DoS).

Ví dụ XML **chỉ để minh họa payload cần chặn**:

```xml
<?xml version="1.0"?>
<!DOCTYPE root [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<root>&xxe;</root>
```

Nếu parser xử lý external entity, `&xxe;` có thể trở thành nội dung tệp theo quyền tiến trình. URI dạng `http:` có thể kích hoạt SSRF tới dịch vụ nội bộ. Các **external parameter entity** (`%name;`) và external DTD còn có thể tải tài nguyên khác dù đã tắt external general entity. Entity lồng nhau cũng có thể gây tăng số lượng/kích thước dữ liệu theo cấp số nhân (*Billion Laughs*).

Mục tiêu bài học: thiết lập chính sách **deny by default**, chỉ mở quyền khi có nhu cầu nghiệp vụ được kiểm soát; cấu hình **đúng từng factory/processor**; kiểm thử bằng dữ liệu vô hại. Sau bài, bạn cần phân biệt rõ secure processing, external access restriction, cấm DTD, resolver/catalog và resource limits.

### Những nơi XML có thể gây truy cập ngoài

| Cấu trúc đầu vào | Nơi cần kiểm soát |
|---|---|
| `<!DOCTYPE ... SYSTEM "uri">`, external general/parameter entities | DOM, SAX, StAX và parser nội bộ |
| `xi:include` (XInclude) | DOM/SAX khi XInclude được bật |
| `xsi:schemaLocation`, `xs:include`, `xs:import`, `xs:redefine` | `SchemaFactory` và `Validator` |
| `xsl:include`, `xsl:import`, hàm `document()` | `TransformerFactory` và `Transformer` |
| Extension functions trong XPath/XSLT | `XPathFactory` và `TransformerFactory` |

**Không chỉ khóa `DocumentBuilderFactory`** nếu ứng dụng còn validate XSD hoặc chạy XSLT: đó là các đường parse và resolve bổ sung.

## 2. FSP và ba thuộc tính External Access Properties

### 2.1. FSP có tác dụng gì?

`XMLConstants.FEATURE_SECURE_PROCESSING` (FSP) là yêu cầu processor áp dụng các giới hạn xử lý bảo mật theo khả năng của implementation:

```java
factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
```

JDK 17 mặc định kích hoạt FSP cho nhiều DOM, SAX và schema factory, nhưng **trạng thái FSP mặc định không đồng nghĩa external access đã bị cấm**. Theo hướng dẫn của Oracle, nên **bật FSP tường minh** rồi đặt các thuộc tính external-access cụ thể. FSP còn liên quan đến giới hạn entity expansion, xử lý grammar và extension functions. **StAX `XMLInputFactory` không hỗ trợ FSP theo cách của DOM/SAX**, vì vậy dùng các property StAX riêng.

### 2.2. Ba EAP cần thuộc

| Hằng `XMLConstants` | System property | Chặn quyền truy cập |
|---|---|---|
| `ACCESS_EXTERNAL_DTD` | `javax.xml.accessExternalDTD` | External DTD và entity reference |
| `ACCESS_EXTERNAL_SCHEMA` | `javax.xml.accessExternalSchema` | XSD tham chiếu ngoài qua `schemaLocation` / `import` / `include` |
| `ACCESS_EXTERNAL_STYLESHEET` | `javax.xml.accessExternalStylesheet` | Stylesheet, `xsl:include/import` và `document()` |

Giá trị là danh sách protocol: `""` = **không protocol nào được phép**; `"file"` = cho phép file URI; `"http,https"` = cho phép truy cập mạng; `"jar:file"` = tài nguyên trong JAR trên file; `"all"` = cho phép mọi protocol. Với XML không tin cậy, **đặt `""`**. `"file"` không tự an toàn: process có thể đọc được tệp nhạy cảm.

Một cách đặt các property (đây là các snippet minh họa, chưa có import):

```java
// DOM: setAttribute
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

// SchemaFactory/Validator: setProperty
SchemaFactory sf = SchemaFactory.newInstance(
        XMLConstants.W3C_XML_SCHEMA_NS_URI);
sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

// TransformerFactory: setAttribute
TransformerFactory tf = TransformerFactory.newInstance();
tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
```

Với JDK, mức ưu tiên các thuộc tính thường là **API factory/processor > System properties > `<java-home>/conf/jaxp.properties` > defaults**. Cấu hình bằng System property như `-Djavax.xml.accessExternalDTD=` là lớp bảo vệ thêm cho JVM nhưng không thay thế cấu hình tại các factory riêng. Không thay đổi `System.setProperty` trong từng request vì nó ảnh hưởng toàn JVM.

Các setter phải chạy **trước khi factory tạo parser/schema/transformer**. Nếu provider không hỗ trợ feature/property bắt buộc, nó có thể ném `ParserConfigurationException`, `SAXNotRecognizedException`, `SAXNotSupportedException`, `IllegalArgumentException` hoặc `TransformerConfigurationException`. Hãy **fail closed**: không bắt lỗi rồi âm thầm parse theo defaults.

## 3. Chính sách cần áp dụng cho từng API

| API | Cấu hình trọng tâm | Lỗi thường gặp |
|---|---|---|
| **DOM** `DocumentBuilderFactory` | FSP; EAP DTD/SCHEMA = `""`; **cấm DOCTYPE**; tắt external general/parameter entities, load external DTD, XInclude | `setExpandEntityReferences(false)` chỉ thay đổi DOM representation, **không đủ** chống XXE. |
| **SAX** `SAXParserFactory` + `SAXParser` | FSP, cấm DOCTYPE, tắt external entities/load DTD; EAP DTD/SCHEMA đặt tại parser | Chỉ khóa factory mà quên processor `SAXParser`. |
| **StAX** `XMLInputFactory` | `SUPPORT_DTD=false`, `IS_SUPPORTING_EXTERNAL_ENTITIES=false`, EAP DTD = `""`, `XMLResolver` chặn | Không có `setFeature(FSP)` tương đương DOM/SAX. |
| **XSD** `SchemaFactory` + `Validator` | FSP ở factory, EAP DTD/SCHEMA ở cả factory và validator, kiểm soát `LSResourceResolver` | XSD có thể tải include/import **trong lúc tạo Schema**, trước khi validate XML. |
| **XSLT** `TransformerFactory` + `Transformer` | FSP, EAP DTD/STYLESHEET = `""`, `URIResolver` hạn chế | `xsl:include/import` xử lý lúc compile stylesheet; `document()` lúc transform. |
| **XPath** `XPathFactory` | FSP, đầu vào là DOM đã parse an toàn, kiểm soát XPath expression/function resolver | XPath không tự thay thế việc khóa XML parser. |

### DOM/SAX: cấm DOCTYPE và mọi external entity

Đối với parser Xerces/JDK 17, các feature URI quan trọng là:

```text
http://apache.org/xml/features/disallow-doctype-decl
http://apache.org/xml/features/nonvalidating/load-external-dtd
http://xml.org/sax/features/external-general-entities
http://xml.org/sax/features/external-parameter-entities
```

Đặt `disallow-doctype-decl=true`; ba feature còn lại đặt `false`. Khi cấm DOCTYPE, parser từ chối **cả internal DTD**, vì không nên phân biệt entity “tạm thời trông vô hại” với đường mở rộng nguy hiểm. `setXIncludeAware(false)` bổ sung bảo vệ đối với XInclude. Khi không cần DTD, đây là đường cấu hình dễ kiểm thử nhất.

Các feature URI có thể phụ thuộc provider. Nếu ứng dụng dùng implementation khác Xerces mặc định trong JDK, kiểm tra tương thích lúc startup và không bỏ qua lỗi cấu hình.

### StAX: cấu hình khác DOM/SAX

```java
XMLInputFactory xif = XMLInputFactory.newFactory();
xif.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                Boolean.FALSE);
xif.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
xif.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
    throw new XMLStreamException("External entity denied");
});
```

Đây là snippet yêu cầu import `javax.xml.stream.*` và `javax.xml.XMLConstants`. Tùy provider, StAX có thể bỏ qua DTD hoặc ném lỗi khi gặp DOCTYPE; mục tiêu quan trọng là **không đọc tài nguyên ngoài và không mở rộng entity**. Không suy diễn rằng mọi JAXP implementation có cùng thông điệp exception.

### XSD, XSLT và XPath

`SchemaFactory` cần khóa trước `newSchema(...)`, vì `xs:include/import` được giải quyết trong giai đoạn biên dịch schema. Sau đó `Validator` cũng cần EAP DTD/SCHEMA riêng nếu nó đọc tài liệu XML hoặc khai thác schemaLocation.

`TransformerFactory` cần EAP DTD/STYLESHEET trước khi biên dịch XSLT. `Transformer` phải có chính sách `URIResolver` phù hợp để chặn `document()` trong quá trình thực thi. Bật FSP của transformer cũng giúp hạn chế extension functions của XSLT.

`XPathFactory` nên bật FSP nếu biểu thức hoặc DOM không hoàn toàn tin cậy; chỉ đánh giá XPath trên `Document` được tạo từ parser an toàn. XPath expression quá phức tạp vẫn có thể gây tốn tài nguyên dù XML đã được parse an toàn.

## 4. Chương trình Java 17+ chạy độc lập

Ví dụ sau tạo các factory riêng đã khóa cấu hình, đọc cùng một XML bình thường qua DOM/SAX/StAX, validate XSD, chuyển đổi XSLT và kiểm tra một XML chứa `DOCTYPE` bị từ chối. **Không có HTTP hay truy cập tệp thật**; URI của payload chỉ là marker.

```java
// File: SecureXmlDemo.java
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import javax.xml.stream.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.validation.*;
import org.w3c.dom.Document;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

public final class SecureXmlDemo {
    private SecureXmlDemo() {}

    private static final String DISALLOW_DTD =
            "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String NO_EXTERNAL_GENERAL =
            "http://xml.org/sax/features/external-general-entities";
    private static final String NO_EXTERNAL_PARAMETER =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String NO_EXTERNAL_DTD_LOADING =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static DefaultHandler throwOnParseError() {
        return new DefaultHandler() {
            @Override public void error(SAXParseException e)
                    throws SAXException { throw e; }
            @Override public void fatalError(SAXParseException e)
                    throws SAXException { throw e; }
        };
    }

    private static DocumentBuilder newSecureDomBuilder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setValidating(false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature(DISALLOW_DTD, true);
        f.setFeature(NO_EXTERNAL_GENERAL, false);
        f.setFeature(NO_EXTERNAL_PARAMETER, false);
        f.setFeature(NO_EXTERNAL_DTD_LOADING, false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder b = f.newDocumentBuilder();
        b.setEntityResolver((publicId, systemId) -> {
            throw new SAXException("External entity denied");
        });
        b.setErrorHandler(throwOnParseError());
        return b;
    }

    private static String domText(String xml) throws Exception {
        Document doc = newSecureDomBuilder().parse(
                new InputSource(new StringReader(xml)));
        return doc.getDocumentElement().getTextContent();
    }

    private static String saxText(String xml) throws Exception {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(true);
        f.setValidating(false);
        f.setXIncludeAware(false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature(DISALLOW_DTD, true);
        f.setFeature(NO_EXTERNAL_GENERAL, false);
        f.setFeature(NO_EXTERNAL_PARAMETER, false);
        f.setFeature(NO_EXTERNAL_DTD_LOADING, false);
        SAXParser parser = f.newSAXParser();
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        XMLReader reader = parser.getXMLReader();
        reader.setEntityResolver((publicId, systemId) -> {
            throw new SAXException("External entity denied");
        });
        reader.setErrorHandler(throwOnParseError());
        StringBuilder text = new StringBuilder();
        reader.setContentHandler(new DefaultHandler() {
            @Override public void characters(char[] ch, int start, int len) {
                text.append(ch, start, len);
            }
        });
        reader.parse(new InputSource(new StringReader(xml)));
        return text.toString();
    }

    private static String staxText(String xml) throws Exception {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                Boolean.FALSE);
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML source denied");
        });
        XMLStreamReader reader = f.createXMLStreamReader(new StringReader(xml));
        try {
            StringBuilder text = new StringBuilder();
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {
                    text.append(reader.getText());
                }
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    private static void validateXsd(String xml) throws Exception {
        String xsd = "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>"
                + "<xs:element name='root' type='xs:string'/>"
                + "</xs:schema>";
        SchemaFactory f = SchemaFactory.newInstance(
                XMLConstants.W3C_XML_SCHEMA_NS_URI);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setResourceResolver((type, ns, pub, sys, base) -> {
            throw new IllegalStateException("External schema denied");
        });
        Schema schema = f.newSchema(new StreamSource(new StringReader(xsd)));
        Validator v = schema.newValidator();
        v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        v.setResourceResolver((type, ns, pub, sys, base) -> {
            throw new IllegalStateException("External schema denied");
        });
        v.validate(new StreamSource(new StringReader(xml)));
    }

    private static String transformXslt(String xml) throws Exception {
        String xsl = "<xsl:stylesheet version='1.0' "
                + "xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>"
                + "<xsl:output method='text'/>"
                + "<xsl:template match='/'>"
                + "<xsl:value-of select='/root'/>"
                + "</xsl:template></xsl:stylesheet>";
        TransformerFactory f = TransformerFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        URIResolver deny = (href, base) -> {
            throw new TransformerException("External stylesheet denied");
        };
        f.setURIResolver(deny);
        Transformer transformer = f.newTransformer(
                new StreamSource(new StringReader(xsl)));
        transformer.setURIResolver(deny);
        StringWriter output = new StringWriter();
        transformer.transform(
                new StreamSource(new StringReader(xml)),
                new StreamResult(output));
        return output.toString().trim();
    }

    public static void main(String[] args) throws Exception {
        String good = "<root>Hello &amp; Java</root>";
        System.out.println("DOM=" + domText(good));
        System.out.println("SAX=" + saxText(good));
        System.out.println("StAX=" + staxText(good));
        validateXsd(good);
        System.out.println("XSD=PASS");
        System.out.println("XSLT=" + transformXslt(good));

        // An toàn: DOCTYPE bị chặn trước mọi thao tác đọc tệp.
        String evil = "<!DOCTYPE root ["
                + "<!ENTITY xxe SYSTEM 'file:///no-real-file'>"
                + "]><root>&xxe;</root>";
        boolean refused;
        try {
            domText(evil);
            refused = false;
        } catch (SAXException blocked) {
            refused = true;
        }
        System.out.println("DOCTYPE_REJECTED=" + refused);
    }
}
```

Lưu tệp tên `SecureXmlDemo.java`, chạy từ thư mục chứa tệp:

```bash
javac -encoding UTF-8 SecureXmlDemo.java
java SecureXmlDemo
```

Stdout chính xác trên JAXP mặc định của JDK 17+:

```text
DOM=Hello & Java
SAX=Hello & Java
StAX=Hello & Java
XSD=PASS
XSLT=Hello & Java
DOCTYPE_REJECTED=true
```

**Ý nghĩa:** XML bình thường được xử lý đúng qua năm thành phần; XML có DOCTYPE bị DOM từ chối trước khi parser có cơ hội đọc external entity. Đoạn Java này dùng các API có mặt từ Java 17 và đã được kiểm thử tương tác trên JDK bằng một trường hợp thành công và một trường hợp từ chối. Với provider bên thứ ba, hãy chạy lại các test trên môi trường thực tế.

- **DOM/SAX:** dùng phối hợp FSP, cấm DOCTYPE, tắt external general/parameter entities, chặn external DTD và đặt EAP rỗng. `EntityResolver` trong ví dụ ném exception nếu có yêu cầu giải quyết entity ngoài.
- **StAX:** vô hiệu DTD/external entity và dùng `XMLResolver` từ chối. Không giả định StAX luôn ném cùng exception như DOM.
- **SchemaFactory + Validator:** cấu hình EAP ở cả **lúc biên dịch schema** và **lúc validate XML**, dùng `LSResourceResolver` hạn chế.
- **TransformerFactory + Transformer:** khóa DTD/STYLESHEET và dùng `URIResolver` ở cả giai đoạn compile/transform, chặn tham chiếu ngoài do XSLT tạo ra.

### XPath trên tài liệu đã parse an toàn

Snippet sau **không độc lập** vì biến `doc` phải là `Document` được tạo từ parser an toàn và cần import `javax.xml.xpath.*`:

```java
XPathFactory f = XPathFactory.newInstance();
f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
XPath xpath = f.newXPath();
String value = xpath.evaluate("/root/text()", doc);
```

Không cho phép người dùng gửi XPath expression không giới hạn độ phức tạp mà không có chính sách kiểm soát, kể cả khi tài liệu XML đã an toàn.

## 5. Khi nghiệp vụ cần tham chiếu XML/XSD/XSLT ngoài

Một số chuẩn cần external DTD hoặc schema/stylesheet tách file. Thay vì bật `ACCESS_EXTERNAL_DTD="all"` hay `ACCESS_EXTERNAL_SCHEMA="http,https"`, hãy:

1. Liệt kê chính xác URI, phiên bản, checksum được phép và **đóng gói bản tin cậy** trong classpath/bundle.
2. Ánh xạ URI đã phê duyệt sang tài nguyên này bằng `EntityResolver` (DOM/SAX), `XMLResolver` (StAX), `LSResourceResolver` (XSD), `URIResolver` (XSLT) hoặc **Java XML Catalog API** (`javax.xml.catalog.CatalogManager`, `CatalogResolver`, `CatalogFeatures`).
3. Từ chối mọi identifier không trong allowlist, chặn fallback fetch và giới hạn số vòng import/include.
4. Giữ EAP `""` khi processor không cần tự mở URI; nếu buộc mở protocol, áp hạn chế mạng/filesystem và xác minh mọi đường resolution trên provider thực tế.

EAP **không** chặn đoạn mã ứng dụng đã tự gọi `URL.openStream()` trước khi truyền `InputStream` cho parser. Resolver tùy biến tự tải URL từ input XML cũng có thể vượt qua mục tiêu chính sách nếu không kiểm soát allowlist. Không nên tải schema động từ URL do người dùng khai báo trong `xsi:schemaLocation`.

Cũng cần phân biệt `DOMSource` (tài liệu đã parse) với `StreamSource` (processor có thể tự tạo parser nội bộ). Cấu hình của một `DocumentBuilderFactory` có sẵn **không áp dụng tự động** cho parser nội bộ của validator/transformer. Vì vậy, mỗi giai đoạn cần đặt FSP/EAP đúng nơi.

## 6. Giới hạn tài nguyên bổ sung

FSP của JDK áp một số hạn mức, nhưng XML hợp lệ vẫn có thể rất lớn hoặc XSLT/XSD vẫn có thể tốn CPU. Các JDK properties đáng biết:

| Property | Mục đích |
|---|---|
| `jdk.xml.entityExpansionLimit` | Số lần mở rộng entity tối đa |
| `jdk.xml.totalEntitySizeLimit` | Tổng kích thước entity mở rộng |
| `jdk.xml.maxParameterEntitySizeLimit` | Kích thước một parameter entity |
| `jdk.xml.elementAttributeLimit` | Số thuộc tính trong một element |
| `jdk.xml.maxElementDepth` | Độ sâu tối đa của XML |
| `jdk.xml.maxOccurLimit` | Complexity của grammar XSD với `maxOccurs` |
| `jdk.xml.xpathExprOpLimit`, `jdk.xml.xpathTotalOpLimit` | Complexity XPath/XSLT expression theo JDK |

Ví dụ cấu hình DOM (snippet cần các import của chương trình trước):

```java
DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
f.setAttribute("jdk.xml.entityExpansionLimit", "2000");
f.setAttribute("jdk.xml.totalEntitySizeLimit", "100000");
f.setAttribute("jdk.xml.maxElementDepth", "100");
```

Các con số là ví dụ, phải chọn dựa trên **XML hợp lệ lớn nhất** của dịch vụ. Property `jdk.xml.*` thuộc implementation JDK và cần xác minh khi thay provider. Ở tầng ứng dụng, luôn giới hạn **request body bytes, decompression ratio, xử lý đồng thời, thời gian parse/validate/transform và kích thước output**.

## 7. Ma trận kiểm thử và lỗi thường gặp

| Ca thử | Kết quả mong đợi |
|---|---|
| XML nhỏ hợp lệ, không tham chiếu ngoài | DOM/SAX/StAX/XSD/XSLT thành công. |
| `DOCTYPE` có internal entity | DOM/SAX với `disallow-doctype-decl` từ chối. |
| External general entity `file:` | Không lấy nội dung tệp, không đưa dữ liệu tệp vào kết quả. |
| External parameter entity / DTD `http:` | Không outbound request; parser từ chối hoặc lỗi. |
| `xi:include` | Không tự include khi XInclude tắt; có thể vẫn là element thường trong DOM. |
| `xs:include`/`xs:import` ngoài | `SchemaFactory` không tải tài nguyên và báo lỗi nếu schema phụ thuộc nó. |
| `xsi:schemaLocation` ra mạng | `Validator` không tự lấy schema ngoài; kết quả validation phụ thuộc schema đã cấu hình. |
| `xsl:import`/`xsl:include` và `document()` | Factory/transformer từ chối theo EAP/resolver. |
| XML bomb, XML quá sâu, request lớn | DTD ban hoặc processing limits/request limits từ chối trước khi cạn tài nguyên. |
| Provider không nhận security feature/property | Fail fast và không parse XML. |

Khi negative test, dùng canary file không chứa secret và server giả để đo số lần kết nối; chạy test trong sandbox hạn chế outbound. **Chỉ thấy parser ném exception chưa chứng minh không có kết nối nào được tạo trước đó.**

Lỗi cấu hình cần tránh:

- **Chỉ dựa vào FSP mặc định:** có thể chưa cấm external access; đặt FSP và EAP một cách tường minh.
- **Chỉ gọi `setExpandEntityReferences(false)`:** không bảo đảm vô hiệu hóa external entity resolution.
- **Chỉ khóa DOM:** bỏ ngỏ parser do validation/XSLT tạo thêm.
- **Cho phép `file` vì nghĩ “không ra Internet”:** tệp cục bộ cũng là dữ liệu nhạy cảm.
- **Cố gọi FSP trên StAX như DOM:** dùng đúng StAX properties.
- **Nuốt exception cấu hình rồi xử lý tiếp:** biến bảo mật thành best-effort, cần fail closed.
- **Resolver mở bất kỳ URL nào do XML gửi:** mã resolver có thể tự thực hiện I/O ngoài quyền kiểm soát EAP.
- **Cho rằng FSP thay thế request limits:** hệ thống vẫn phải có giới hạn byte, depth, thời gian và concurrency.

## 8. Checklist triển khai và tài liệu

Kiểm tra trước khi đưa dịch vụ vào production:

1. Có cần DTD/XInclude không? Nếu không, cấm DOCTYPE trên DOM/SAX, tắt DTD và external entities trên StAX, tắt XInclude.
2. EAP liên quan đã đặt `""` ở **tất cả** factory/processor chịu trách nhiệm parse, validate, transform chưa?
3. Schema/XSLT ngoài (nếu có) được resolve bằng allowlist/catalog nội bộ thay vì protocol rộng chưa?
4. Provider/JDK thực tế có chấp nhận mọi security setting không? Có fail fast khi không hỗ trợ không?
5. Đã có giới hạn body bytes, CPU/time, depth/concurrency và các negative tests XXE/SSRF/DoS chưa?

**Tài liệu đối chiếu:**

- Oracle Java 17 JAXP Security Guide: https://docs.oracle.com/en/java/javase/17/security/java-api-xml-processing-jaxp-security-guide.html
- Java 17 `XMLConstants` (FSP/EAP): https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/XMLConstants.html
- Java 17 `XMLInputFactory` (StAX): https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/stream/XMLInputFactory.html
- Java 17 `SchemaFactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/validation/SchemaFactory.html
- Java 17 `Validator`: https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/validation/Validator.html
- Java 17 `TransformerFactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/transform/TransformerFactory.html

**Ghi nhớ:** **FSP** thiết lập giới hạn xử lý; **EAP `""`** từ chối truy cập tài nguyên ngoài; **cấm DTD/XInclude** vô hiệu các đường rủi ro không cần thiết; **resolver/catalog allowlist** phục vụ ngoại lệ được kiểm soát; cấu hình và kiểm thử riêng cho mọi factory/processor là điều kiện để XML thực sự được xử lý an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-009">21.9 SecurityManager, Policy, Permission và AccessController trong mã cũ đã deprecated</a>

<details>
<summary>Xem chi tiết</summary>

# 21.9. SecurityManager, Policy, Permission và AccessController trong mã Java cũ đã deprecated

## 1. Bối cảnh và mục tiêu học tập

Một ứng dụng Java cũ có thể khai báo `-Djava.security.manager` khi khởi động, dùng tệp `.policy` để cấp quyền đọc file hoặc truy cập mạng, gọi `System.getSecurityManager()` để kiểm tra quyền, và bọc code bằng `AccessController.doPrivileged(...)`. Những đoạn mã đó xuất hiện trong thư viện, plugin host, ứng dụng desktop, middleware và hệ thống chạy nhiều module trong một JVM.

Điều dễ gây nhầm lẫn là **API vẫn có thể tồn tại để biên dịch nhưng cơ chế bảo vệ bên dưới đã không còn hoạt động**. Từ **JDK 17**, Security Manager và nhiều API phụ thuộc được đánh dấu *deprecated for removal* (JEP 411). Từ **JDK 24**, Security Manager bị **vô hiệu hóa vĩnh viễn** (JEP 486), dù một số lớp/phương thức vẫn còn trong API để hỗ trợ chuyển tiếp.

Sau bài học, bạn có thể:

1. Giải thích mục đích lịch sử của `SecurityManager`, `Policy`, `Permission`, `ProtectionDomain`, `AccessControlContext` và `AccessController`.
2. Phân biệt chính xác **deprecated**, **disabled** và **removed**, cũng như khác biệt hành vi giữa JDK 17–23 và JDK 24+.
3. Đọc được các mẫu code cũ như `checkPermission`, `getSecurityManager`, `setSecurityManager`, `doPrivileged` và tệp `java.policy`; nhận biết code nào đang bỏ qua kiểm soát hoặc sẽ ném exception.
4. Dùng `jdeprscan`, cảnh báo biên dịch và integration test để tìm phụ thuộc thực sự; chuyển mỗi mục đích bảo mật sang cơ chế phù hợp thay vì đơn giản xóa các dòng kiểm tra.
5. Áp dụng **authorization trong ứng dụng** và **isolation bằng hệ điều hành/process/container** khi cần kiểm soát quyền hoặc chạy mã không đáng tin.

**Lưu ý quan trọng về từ “deprecated” trong tiêu đề:** `SecurityManager`, `java.security.Policy` và `AccessController` được đánh dấu deprecated for removal từ JDK 17. Lớp trừu tượng **`java.security.Permission` không được đánh dấu deprecated toàn bộ trong API JDK 25**, dù chức năng kiểm tra bảo vệ cũ dựa vào Security Manager đã thay đổi và **nhiều lớp permission cụ thể** (như `FilePermission`, `RuntimePermission`, `SecurityPermission`) bị deprecated for removal ở JDK 25. Không nên viết “mọi lớp Permission bị xóa” hay “Permission không còn trong JDK” nếu tài liệu API chưa nói vậy.

## 2. Cơ chế lịch sử: Java Security Manager giải quyết bài toán nào?

Security Manager được thiết kế để kiểm soát một số hành động nhạy cảm của code chạy **cùng JVM**: đọc/ghi tệp, mở socket, truy cập thuộc tính hệ thống, gọi các thao tác runtime nhạy cảm, v.v. Khi được bật, các API tương ứng ở Java platform có thể tham vấn security checks và từ chối thao tác bằng `SecurityException` nếu quyền không cho phép.

Các thành phần chính:

| Khái niệm/API | Vai trò trong mô hình cũ | Điểm cần nhớ khi bảo trì |
| --- | --- | --- |
| `java.lang.SecurityManager` | Điểm thực thi nhiều kiểm tra quyền, gồm các phương thức `check*`. | Không đồng nghĩa hệ điều hành đã cô lập tiến trình; chỉ bảo vệ các thao tác được cơ chế này kiểm soát. |
| `java.security.Permission` | Mô hình hóa một quyền: loại tài nguyên, tên mục tiêu, hành động. | Permission là **mô tả quyền**; tạo đối tượng Permission không tự chặn code. |
| `java.io.FilePermission`, `java.net.SocketPermission`, `RuntimePermission` | Các permission cụ thể cho file, socket và hoạt động runtime. | Ý nghĩa được xác định theo lớp; nhiều lớp đã deprecated ở các bản JDK mới. |
| `java.security.Policy` | Nguồn chính sách cấp quyền cho code theo nguồn tải, signer/`CodeSource` và các điều kiện khác. | Policy của Security Manager khác với authorization rule của ứng dụng. |
| `ProtectionDomain` / `CodeSource` | Gắn code với miền bảo vệ, nguồn và các thông tin quyền theo mô hình cũ. | Không dùng nó như cơ chế cấp quyền cho user/resource trong ứng dụng hiện đại. |
| `AccessControlContext` | Bối cảnh kiểm tra quyền qua chuỗi lời gọi. | Phụ thuộc cơ chế stack-based access control của Security Manager. |
| `AccessController.checkPermission(...)` | Đánh giá xem permission được yêu cầu có được chấp nhận trong bối cảnh truy cập không. | Từ JDK 24: luôn ném `AccessControlException`, không còn ra quyết định quyền hữu ích. |
| `AccessController.doPrivileged(...)` | Đặt ranh giới đặc quyền cho một đoạn code trong cơ chế lịch sử. | Không phải API tạo quyền tùy ý; trên JDK 24+ chỉ chạy action như bình thường. |

### 2.1. Policy file và permission không phải RBAC

Ví dụ **cú pháp minh họa của hệ thống cũ**, không dùng làm hướng dẫn cấu hình cho JDK 24+:

```java
grant codeBase "file:/opt/legacy/plugins/-" {
    permission java.io.FilePermission "/var/app/cache/-", "read";
    permission java.net.SocketPermission "api.example.com:443", "connect,resolve";
};
```

Đây là **nội dung tệp policy**, không phải file `.java` để biên dịch. Trong những môi trường cũ có Security Manager hoạt động, ý nghĩa là cấp một số quyền Java platform cho code đến từ `codeBase` được chỉ định. `"/var/app/cache/-"` là cách diễn đạt đường dẫn con theo cú pháp FilePermission lịch sử.

Điều này **khác** với việc quyết định người dùng nào được xem tài liệu nào, nhân viên nào được xóa đơn hàng nào, hoặc tenant nào có thể đọc dữ liệu của tenant khác. Các quyết định nghiệp vụ đó cần authentication và authorization ở tầng ứng dụng (RBAC, ABAC, ownership check, service policy); Security Manager chưa bao giờ là phương án thay thế tự động cho mọi quyền nghiệp vụ.

### 2.2. Ý nghĩa của `doPrivileged` trước khi bị vô hiệu hóa

Trong mô hình cũ, phép kiểm tra có thể dựa trên bối cảnh của các frame trong call stack và các ProtectionDomain liên quan. Khi thư viện tin cậy cần một thao tác hẹp mà code gọi bên ngoài không được tự thực hiện, thư viện có thể dùng `doPrivileged` để đặt **ranh giới cho kiểm tra call stack** trong đoạn code đó; hiệu lực vẫn giới hạn bởi quyền của code được thực thi và chính sách áp dụng.

```java
// Mảnh mã lịch sử, chỉ minh họa; không phải chương trình độc lập.
String home = java.security.AccessController.doPrivileged(
        (java.security.PrivilegedAction<String>)
                () -> System.getProperty("user.home"));
```

**Không được hiểu `doPrivileged` là “bật quyền root/admin” hoặc “cấp mọi Permission”.** Nó chỉ có ý nghĩa trong mô hình Security Manager có hiệu lực và không vượt qua quyền của OS, container, process hay các kiểm soát nghiệp vụ.

Một thư viện có thể bọc đọc property hoặc file bằng `doPrivileged` chỉ để tương thích với môi trường có Security Manager; thư viện khác có thể dựa vào nó cho ranh giới bảo vệ thật. **Cần phân loại mục đích trước khi xóa**.

## 3. Dòng thời gian và trạng thái API theo JDK

| Phiên bản | Trạng thái chính | Hệ quả cần hiểu |
| --- | --- | --- |
| **JDK 8–16** | Security Manager còn được hỗ trợ; có thể bật theo startup/configuration hoặc API thích hợp. | Tệp `.policy`, permission checks và `doPrivileged` có thể có hiệu lực khi manager được bật. |
| **JDK 17** | **JEP 411:** Security Manager và nhiều API liên quan **deprecated for removal**; đưa ra cảnh báo khi bật Security Manager. | Mã cũ có thể vẫn chạy, nhưng cần chuẩn bị di trú; deprecated không có nghĩa đã bị xóa. |
| **JDK 18–23** | Mặc định không cho cài Security Manager động bằng `System.setSecurityManager(...)`; một số cấu hình cho phép tiếp tục dùng để chuyển tiếp. | `System.setSecurityManager` có thể ném `UnsupportedOperationException` nếu không có tùy chọn cho phép; không nên coi đây là công nghệ mới để triển khai. |
| **JDK 24+** | **JEP 486:** Security Manager **bị vô hiệu hóa vĩnh viễn**; không thể bật lại bằng flag hoặc `setSecurityManager`. | Các API còn tồn tại có hành vi cố định: trả `null/false`, chạy action bình thường, hoặc luôn ném exception theo từng API. |
| **JDK 25** | Nhiều **permission subclass** được đánh dấu deprecated for removal, bổ sung vào nhóm API cũ. | Xem Javadoc của đúng lớp để biết deprecation; lớp cơ sở `Permission` không bị deprecated toàn bộ chỉ vì cơ chế Security Manager đã ngừng. |

**Ba thuật ngữ khác nhau:**

- **Deprecated / deprecated for removal:** API còn tồn tại nhưng bị cảnh báo không nên tiếp tục phụ thuộc; phiên bản tương lai có thể loại bỏ.
- **Disabled:** API có thể còn tồn tại trong JDK nhưng cơ chế hành vi chính đã bị cố định/vô hiệu hóa.
- **Removed:** API không còn trong bản JDK đó; code có thể không biên dịch hoặc gặp lỗi liên kết ở runtime.

Vì vậy lời nói “SecurityManager đã bị xóa từ Java 17” là **sai**. Đúng là **deprecated for removal từ JDK 17** và **permanently disabled từ JDK 24**. Khi cần nâng runtime, kiểm tra phiên bản chính xác bằng `java -version` và `javac -version`, không suy từ phiên bản source/bytecode được khai báo trong build.

## 4. JDK 24+: hành vi chi tiết quan trọng nhất khi đọc code cũ

Cùng cú pháp Java nhưng kết quả thay đổi rất mạnh. Các hành vi dưới đây dựa trên tài liệu JDK 24/25 về việc vô hiệu hóa Security Manager:

| API hoặc cấu hình | Hành vi trên JDK 24+ | Hậu quả khi duy trì code |
| --- | --- | --- |
| `System.getSecurityManager()` | Luôn trả `null`. | Mẫu `if (sm != null) sm.checkPermission(...)` luôn bỏ qua nhánh kiểm tra. |
| `System.setSecurityManager(...)` | Luôn ném `UnsupportedOperationException`, kể cả truyền `null`. | Mã cài manager lúc runtime có thể làm khởi tạo ứng dụng thất bại. |
| Khởi động với `-Djava.security.manager=allow` hoặc cấu hình cố bật manager | Lỗi khi khởi động VM; không bật được. | Cần bỏ cấu hình startup cũ; không thể “bật lại cho tương thích”. |
| `new SecurityManager().checkPermission(...)` hoặc các phương thức `check*` | `check*` luôn ném `SecurityException`. | Gọi trực tiếp khác với mẫu có kiểm tra `getSecurityManager()!=null`; có thể thành lỗi luôn từ chối. |
| `AccessController.doPrivileged(...)` / `doPrivilegedWithCombiner(...)` | Thực thi action ngay; không còn nâng hoặc hạn chế quyền theo stack. | Code có thể chạy tiếp, nhưng **không còn ranh giới an ninh cũ**. |
| `AccessController.checkPermission(p)` | Luôn ném `AccessControlException`. | Không phải cách thay thế permission check trong ứng dụng hiện đại. |
| `AccessController.getContext()` / `AccessControlContext.checkPermission(...)` | Context được trả về không cấp quyền; `checkPermission` luôn ném exception. | Không thể khôi phục context/stack policy bằng cách lấy snapshot. |
| `Policy.getPolicy()` | Trả policy mặc định không cấp permission; `implies` của policy đó là `false`. | Không còn đọc policy hệ thống cũ để xác định quyền cho ứng dụng. |
| `Policy.setPolicy(...)` | Luôn ném `UnsupportedOperationException`. | Không thể cài lại policy process-wide theo cơ chế cũ. |
| `Permission.checkGuard(...)` | Luôn ném `SecurityException`. | Đừng dùng `GuardedObject/Permission.checkGuard` như cơ chế cấp quyền bình thường. |
| `Permission.implies(other)` | Vẫn là phép so sánh ý nghĩa giữa hai Permission do subclass định nghĩa. | Kết quả `true` chỉ là quan hệ permission, **không chứng minh caller được phép thao tác**. |
| `$JAVA_HOME/conf/security/java.policy` | Tệp policy mặc định của JDK bị loại bỏ. | Quy tắc `grant` của Security Manager không còn nguồn enforcement. |
| `-Djava.security.policy=...` | Thuộc nhóm property Security Manager không còn được hỗ trợ/được bỏ qua. | Truyền đường dẫn policy không tái tạo sandbox; cũng có thể tạo cảm giác an toàn giả. |

**Cảnh báo hai kiểu lỗi trái ngược:**

1. **Fail open ở logic ứng dụng:** `getSecurityManager()==null` khiến code bỏ qua một kiểm tra mà đội phát triển tưởng vẫn đang có.
2. **Fail closed ngoài ý muốn:** gọi trực tiếp `AccessController.checkPermission` hoặc `Permission.checkGuard` khiến thao tác luôn bị từ chối, dù application role/tenant thực tế được phép.

Đừng “sửa nhanh” bằng cách nuốt mọi `SecurityException` hoặc đổi sang `new SecurityManager().checkPermission(...)`. Hãy xác định kiểm tra đó cần chặn **quyền của người dùng**, **quyền của tiến trình**, hay chỉ là **mã tương thích**.

## 5. Thực hành 1: quan sát legacy API trên JDK 24+

### 5.1. Chương trình có thể biên dịch và chạy

Tạo file `LegacySecurityAfter24.java` (chương trình **chỉ có stdout dự kiến dưới đây khi chạy trên JDK 24+**):

```java
import java.io.FilePermission;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Permission;
import java.security.Policy;

public class LegacySecurityAfter24 {
    @SuppressWarnings("removal") // Chỉ dành cho demo API cũ.
    public static void main(String[] args) {
        Permission readFile = new FilePermission("demo.txt", "read");

        System.out.println("Security Manager active: "
                + (System.getSecurityManager() != null));

        String result = AccessController.doPrivileged(() -> "action-ran");
        System.out.println("doPrivileged result: " + result);

        Permission samePermission = new FilePermission("demo.txt", "read");
        System.out.println("Permission.implies match: "
                + readFile.implies(samePermission));

        System.out.println("Policy grants read: "
                + Policy.getPolicy().implies(
                        LegacySecurityAfter24.class.getProtectionDomain(),
                        readFile));

        try {
            AccessController.checkPermission(readFile);
            System.out.println("AccessController check: ALLOWED");
        } catch (AccessControlException expected) {
            System.out.println("AccessController check: DENIED");
        }

        try {
            System.setSecurityManager(null);
            System.out.println("setSecurityManager: OK");
        } catch (UnsupportedOperationException expected) {
            System.out.println("setSecurityManager: UNSUPPORTED");
        }
    }
}
```

Chạy trong terminal có **JDK 24 trở lên**:

```shell
java -version
javac LegacySecurityAfter24.java
java LegacySecurityAfter24
```

**stdout dự kiến cho chương trình trên, trên JDK 24+:**

```text
Security Manager active: false
doPrivileged result: action-ran
Permission.implies match: true
Policy grants read: false
AccessController check: DENIED
setSecurityManager: UNSUPPORTED
```

Giải thích từng dòng:

- `getSecurityManager()` không phản ánh một chính sách active nữa vì không thể có Security Manager.
- `doPrivileged` chỉ gọi lambda và trả lại giá trị; không mở thêm quyền.
- `Permission.implies` trả `true` vì đây là hai `FilePermission` có cùng mục tiêu và hành động. **Nó không nói rằng JVM cấp quyền đọc file thực tế cho người gọi.**
- `Policy.getPolicy().implies(...)` trả `false` với policy mặc định không cấp quyền của JDK 24+.
- `AccessController.checkPermission` ném `AccessControlException` bất kể kết quả so sánh `Permission.implies` trước đó.
- `System.setSecurityManager(null)` ném `UnsupportedOperationException` dù chỉ truyền `null`.

**Giới hạn ví dụ:** chương trình không đọc file, không mã hóa dữ liệu và không chạy mã không đáng tin. Nó dùng API deprecated để **quan sát sự khác biệt** giữa metadata permission và enforcement thực tế; không phải mã mẫu để đưa vào production. `@SuppressWarnings("removal")` được dùng cục bộ để phần diễn giải không bị chìm trong cảnh báo, **không phải gợi ý tắt warning trong dự án thật**.

### 5.2. Vì sao không chạy cùng ví dụ để suy luận trên JDK 17–23?

Trên các JDK đó, `Policy.getPolicy()`, `checkPermission`, trạng thái Security Manager và `setSecurityManager` phụ thuộc cấu hình thực tế. `Policy.getPolicy().implies(...)` có thể khác; `System.setSecurityManager(null)` có thể khác; JDK còn có những đường bật manager để chuyển tiếp. Do vậy output trên **không được gắn nhãn bất biến cho JDK 17+**.

Nếu đang duy trì hệ thống trên JDK 17–23, hãy kiểm thử trong môi trường có cấu hình tương ứng và không khẳng định “doPrivileged luôn vô hiệu” trước khi xét phiên bản. Việc kích hoạt Security Manager chỉ để thử nghiệm legacy nên được thực hiện ở **môi trường cô lập**, không coi là cách xây dựng giải pháp mới.

## 6. Hai mẫu code cũ và ý nghĩa di trú khác nhau

### Mẫu A — Chỉ kiểm tra Security Manager nếu có

```java
// Đoạn mã cũ, minh họa:
SecurityManager sm = System.getSecurityManager();
if (sm != null) {
    sm.checkRead(path);
}
readFile(path);
```

Đây là **mảnh mã**, chưa đầy đủ import, kiểu `path` và định nghĩa `readFile` nên không biên dịch độc lập. Trong mô hình cũ, khi có Security Manager, nó kiểm tra quyền trước khi tiếp tục; nếu manager vắng mặt, nhánh kiểm tra đã được thiết kế để không chạy. Trên JDK 24+, manager luôn vắng, vì vậy **mọi lần chạy đều bỏ qua nhánh**.

Nếu mục đích chỉ là mã tương thích và app còn có lớp authorization độc lập, có thể loại bỏ phần legacy. Nếu mục đích là chặn người dùng hoặc plugin truy cập tài nguyên, phải thay bằng kiểm soát phù hợp ở ranh giới bảo mật: xác thực principal, xác minh quyền trên resource, quyền OS và isolation khi cần.

### Mẫu B — Gọi thẳng AccessController.checkPermission

```java
// Đoạn mã cũ, minh họa:
AccessController.checkPermission(
        new java.io.FilePermission("/var/app/private/data.txt", "read"));
readSensitiveData();
```

Đây cũng là **mảnh mã**, `readSensitiveData` chưa được định nghĩa. Trên JDK 24+, `checkPermission` **luôn ném** `AccessControlException`, nên `readSensitiveData` không chạy; đây không phải phép xác minh user authorization hợp lệ.

**Không thay** một permission check bằng `catch (AccessControlException ignored) { ... }` rồi mặc định cho qua. Phải trả lời: ai được phép đọc, tài nguyên nào, trong điều kiện nào, danh tính được xác minh ở đâu, và hành động bị từ chối như thế nào.

### Mẫu C — Bọc thao tác bằng doPrivileged

```java
// Đoạn mã cũ, minh họa:
String value = AccessController.doPrivileged(
        (java.security.PrivilegedAction<String>)
                () -> System.getProperty("app.mode"));
```

Trên JDK 24+, nó gần tương đương với:

```java
String value = System.getProperty("app.mode");
```

Chỉ thay tương đương khi wrapper thực sự làm **mã tương thích cho môi trường Security Manager** và không có cam kết an ninh khác. Với các hệ thống trước đây dựa vào stack/permission để làm ranh giới trust, xóa wrapper **không tạo ra một sandbox mới**; phải thiết kế lại boundary phù hợp.

## 7. Thực hành 2: thay permission check cũ bằng authorization tường minh

Security Manager kiểm soát hành động của code trong JVM theo cơ chế lịch sử. Một web service cần hỏi câu khác: **principal đã xác thực có được truy cập đối tượng này không?** Ví dụ này minh họa kiểm tra ownership hoặc vai trò auditor mà không phụ thuộc Security Manager.

Tạo file `ExplicitAuthorization.java`:

```java
import java.util.Set;

public class ExplicitAuthorization {
    record Caller(String userId, Set<String> roles) {}

    static String viewDocument(Caller caller, String ownerId) {
        if (caller == null || caller.userId() == null
                || caller.roles() == null || ownerId == null) {
            throw new SecurityException("Missing authenticated identity");
        }

        boolean owner = ownerId.equals(caller.userId());
        boolean auditor = caller.roles().contains("AUDITOR");

        if (!owner && !auditor) {
            throw new SecurityException("Forbidden");
        }
        return "VISIBLE";
    }

    public static void main(String[] args) {
        Caller alice = new Caller("alice", Set.of("USER"));
        Caller auditor = new Caller("reviewer", Set.of("AUDITOR"));

        System.out.println("Own document: "
                + viewDocument(alice, "alice"));

        try {
            viewDocument(alice, "bob");
            System.out.println("Cross-owner document: ALLOWED");
        } catch (SecurityException expected) {
            System.out.println("Cross-owner document: DENIED");
        }

        System.out.println("Auditor document: "
                + viewDocument(auditor, "bob"));
    }
}
```

Biên dịch và chạy với **JDK 17+**, không cần Security Manager:

```shell
javac ExplicitAuthorization.java
java ExplicitAuthorization
```

`stdout` chính xác:

```text
Own document: VISIBLE
Cross-owner document: DENIED
Auditor document: VISIBLE
```

Điểm tiến bộ so với mẫu Permission cũ:

- Quyền truy cập dựa trên **ngữ cảnh đã xác thực**, action và tài nguyên có owner cụ thể, không phải sự tồn tại của Java Security Manager.
- Trường hợp không có danh tính hoặc caller không có quyền bị **từ chối rõ ràng**.
- Dễ viết unit/integration tests cho những trường hợp khác owner, thiếu role, thiếu principal; hành vi không thay đổi khi nâng JDK.

**Điều kiện để ví dụ trở thành thiết kế an toàn:** `Caller` trong ví dụ là đối tượng giả lập được chương trình tạo sẵn. Trong production phải được lấy từ authentication context **đáng tin**, đã xác minh token/session/credential, và role/tenant/owner phải được xác minh từ nguồn phía server. Nếu nhận `userId` hoặc `roles` trực tiếp từ JSON do client gửi rồi tin luôn, code vẫn có lỗ hổng. Ngoài ra, kiểm tra quyền phải diễn ra **sát tài nguyên thật và nhất quán ở mọi entry point**; DB query/row-level security, service policy, thao tác file và authorization phải cùng phạm vi.

### Khi nào authorization tường minh là không đủ?

Nếu bài toán là “chạy JAR của bên thứ ba không đáng tin và cấm nó đọc filesystem/network”, một check `viewDocument` **không cô lập** code có khả năng gọi thẳng `Files.readString(...)`, tạo socket hoặc dùng native APIs. Đây là bài toán **sandbox/isolation**, nên cần tiến trình riêng, identity/quyền OS, giới hạn mount, filesystem và network, container/VM/OS sandbox phù hợp threat model. Application authorization và process isolation **bảo vệ các ranh giới khác nhau** và thường cần được dùng cùng nhau.

## 8. Chiến lược thay thế theo mục đích sử dụng thực tế

| Phụ thuộc legacy phát hiện được | Câu hỏi cần hỏi | Hướng thay thế thích hợp |
| --- | --- | --- |
| `System.getSecurityManager()` rồi `checkPermission` | Đoạn này chỉ tương thích với môi trường SM, hay đang thực sự chặn hành động? | Nếu là compatibility, loại bỏ sau kiểm thử; nếu là bảo vệ, thay bằng authorization/OS control đúng scope. |
| `AccessController.doPrivileged(...)` | Wrapper chỉ để đọc property của thư viện, hay từng là privilege boundary? | Wrapper không còn giá trị trên JDK 24+; có thể gọi action trực tiếp nếu không mất ranh giới, hoặc tách boundary thành service/process. |
| `AccessController.checkPermission` / `AccessControlContext.checkPermission` | Đang dùng Java Permission làm policy engine cho người dùng/app? | Thiết kế authorization engine rõ ràng, có input được xác thực, rule, enforcement và audit. |
| Tệp `.policy` hoặc `Policy.setPolicy` | Ai cấp quyền và phần nào là ranh giới không tin cậy? | Dịch policy sang cơ chế OS/container/network và ứng dụng; không dùng `-Djava.security.policy` như phép hồi sinh. |
| `SecurityManager` subclass override `checkExit`/`checkRead`/... | Muốn sandbox hay chỉ muốn chặn một API như `System.exit`? | Sandbox: isolation ở OS/process. Interception API cụ thể: sửa code, API wrapper, static analysis hoặc instrumentation có kiểm soát (không tự coi là sandbox). |
| Plugin loader và JAR bên thứ ba | Plugin là trusted, semi-trusted hay untrusted? | Trusted plugin: kiểm soát distribution và API. Untrusted plugin: tách process/container có giới hạn; cân nhắc chặn filesystem/network và giới hạn tài nguyên. |
| `Permission.implies`, `Permissions` trong logic riêng | Được dùng như cấu trúc dữ liệu so sánh quyền hay enforcement Security Manager? | Có thể giữ phần mô hình hóa nếu API còn hỗ trợ và lý do rõ ràng, nhưng nên chuyển domain policy sang kiểu dữ liệu riêng để giảm phụ thuộc cũ. |

### 8.1. Không có “drop-in replacement” cho Security Manager

Các biện pháp dưới đây chỉ giải quyết **một số threat model**, không tương đương toàn bộ với Security Manager:

- **OS/process boundaries:** tài khoản hệ điều hành có quyền tối thiểu, thư mục/tài nguyên cho phép, giới hạn file descriptor và network.
- **Container:** namespace, mount read-only, drop capabilities, network policy, seccomp và cấu hình runtime; container nếu chạy đặc quyền rộng vẫn không phải sandbox hiệu quả.
- **VM/hypervisor và sandbox do OS cung cấp:** khi mức cô lập cần cao hơn; phải cấu hình/kiểm thử quyền thật.
- **Application authorization:** kiểm tra owner, tenant, role/attributes ở service/resource, dùng principal đã xác thực và audit.
- **Secure software architecture:** giảm bề mặt API của plugin, loại bỏ khả năng gọi API nhạy cảm không cần thiết, validate input, tránh chạy mã không đáng tin cùng tiến trình.
- **Java Module System (JPMS) và strong encapsulation:** hữu ích để quản lý dependency và mức truy cập vào package nội bộ, nhưng **không phải sandbox** ngăn hoàn toàn file/network của code tùy ý.
- **Instrumentation/Java agent:** có thể intercept hoặc sửa bytecode đối với API cụ thể; không nên quảng cáo là biện pháp cô lập hoàn chỉnh trước code có khả năng dùng nhiều đường tấn công khác.

Quy tắc thiết kế: xác định **trust boundary trước**, sau đó chọn control phù hợp. Nếu một plugin không đáng tin có thể gọi tùy ý API Java trong cùng JVM, chỉ bỏ qua các API Security Manager deprecated sẽ không khôi phục bảo vệ.

## 9. Quy trình audit và migration có thể áp dụng

### Bước 1 — Xác định phiên bản JDK và đường khởi động

Kiểm tra:

```shell
java -version
javac -version
```

Đọc cấu hình JVM trong startup script, Gradle/Maven, application server, service manager, Docker image/entrypoint và biến môi trường có thể bổ sung JVM options. Tìm:

- `-Djava.security.manager` và các biến thể `=allow`, `=default`, tên custom manager.
- `-Djava.security.policy=...`, tệp `.policy`, `Policy.setPolicy`.
- Việc cài `System.setSecurityManager(...)` lúc runtime.
- Các thư viện/plugin có code permission/privileged ở các module phụ thuộc.

Trên JDK 24+, loại bỏ các cờ bật Security Manager khỏi startup; chúng không phải cách cấu hình bảo mật khả dụng. Khi cần đo phụ thuộc trong JDK 17–23, dùng môi trường thử riêng với Security Manager disallowed để phát hiện code cần cài manager.

### Bước 2 — Quét source và bytecode

Nếu workspace có `rg` (ripgrep), ví dụ tìm trong một thư mục source của ứng dụng:

```shell
rg -n "SecurityManager|AccessController|AccessControlContext|Policy\.getPolicy|Policy\.setPolicy|checkPermission|doPrivileged|java\.security\.manager|java\.security\.policy" src
```

Đây là **lệnh tìm kiếm mẫu**, không chứng minh mọi kết quả đều là phụ thuộc gây rủi ro, và cũng không phát hiện đầy đủ reflection hoặc code được sinh tự động. Bổ sung code review theo các kết quả.

`jdeprscan` hỗ trợ phát hiện tham chiếu đến API Java SE đã deprecated trong class/JAR được biên dịch. Ví dụ chạy bằng JDK phù hợp:

```shell
jdeprscan --for-removal app.jar
```

Với JDK hỗ trợ tùy chọn release tương ứng, có thể chỉ định release cần đánh giá; ví dụ `jdeprscan --release 25 --for-removal app.jar` trên JDK 25. Ngoài ra, biên dịch source với cảnh báo removal:

```shell
javac -Xlint:removal LegacySecurityAfter24.java
```

`jdeprscan` **chỉ phát hiện Java SE API deprecated**, không phát hiện mọi API bên thứ ba, mọi call qua reflection hoặc các ràng buộc runtime của application server. `javac` phát hiện mã source đang được biên dịch, không rà được toàn bộ binary dependency tự động. Vì vậy quét source, scan dependency và integration test phải bổ trợ lẫn nhau.

**Dùng `@SuppressWarnings("removal")` trong ví dụ lab** có thể che compiler warning; đừng dùng suppression tràn lan để xem việc migration là hoàn tất.

### Bước 3 — Phân loại theo mức độ ảnh hưởng

Tạo inventory riêng cho những nơi còn API legacy:

| Nhóm | Dấu hiệu | Mức độ ưu tiên | Cách xác minh |
| --- | --- | --- | --- |
| **Khởi động không chạy** | Startup có `-Djava.security.manager=allow` hoặc gọi `setSecurityManager` trong init. | Rất cao | Smoke test trên JDK mục tiêu; kiểm tra startup error/exception. |
| **Bỏ qua kiểm soát** | `if (System.getSecurityManager()!=null)` là chỗ duy nhất chặn hành động. | Rất cao | Test user/plugin không có quyền vẫn không đọc/ghi/thoát/network được sau migration. |
| **Từ chối mọi truy cập** | Gọi trực tiếp `AccessController.checkPermission`, `checkGuard` hoặc `SecurityManager.check*`. | Cao | Integration test nhánh hợp lệ/bất hợp lệ; xác định exception trên JDK 24+. |
| **Privilege wrapper tương thích** | `doPrivileged` chỉ bọc đọc config/property và không có policy boundary khác. | Trung bình | Review mục đích thư viện; test behavior trước/sau và loại bỏ API deprecated. |
| **Mô hình hóa quyền** | `Permission.implies` được dùng như phép so sánh riêng. | Tùy trường hợp | Đánh giá xem business authorization có được enforce ngoài Permission không. |
| **Chạy mã không đáng tin** | Nạp plugin/script/JAR third-party vào JVM và dựa vào `.policy`. | Rất cao | Threat model + isolation test ở process/OS boundary; không dựa vào in-JVM Security Manager. |

### Bước 4 — Kiểm thử theo hành vi cần bảo vệ

Ví dụ kế hoạch thử nghiệm khi chuyển ứng dụng cũ:

1. **Startup:** app lên được trên JDK mục tiêu, không còn cờ Security Manager và không gọi `setSecurityManager`.
2. **Happy path:** hành động hợp lệ vẫn chạy, property/config đọc được, quyền OS/certificate/keystore được cấp đúng nơi.
3. **Negative path (quan trọng):** principal/tenant không có quyền không đọc/xóa được resource; plugin không được truy cập file/mạng ngoài policy thực tế.
4. **Error path:** lỗi `AccessControlException`, `UnsupportedOperationException` hoặc `SecurityException` không bị bắt rồi bỏ qua theo cách làm hỏng cơ chế bảo vệ.
5. **Runtime controls:** chạy dưới tài khoản OS tối thiểu, kiểm tra filesystem, mount, network và process restrictions thật sự có hiệu lực.
6. **Dependency compatibility:** JAR bên thứ ba không phụ thuộc cơ chế manager cũ; nâng cấp hoặc thay dependency khi cần.
7. **Release gate:** chỉ đánh dấu migration đạt khi hệ thống chứng minh **hành vi deny/allow cần thiết** trên đúng JDK, environment và cấu hình phát hành.

Ví dụ “test pass khi app khởi động” là **chưa đủ** để kết luận bảo mật không suy giảm; một nhánh `getSecurityManager()==null` có thể giúp app chạy trong khi silent skip permission check.

## 10. Các lỗi suy luận thường gặp

| Kết luận sai | Sửa đúng |
| --- | --- |
| “Deprecated từ 17 nghĩa là SecurityManager không còn class.” | Deprecated for removal khác removed; JDK 24 vô hiệu hóa cơ chế, nhiều API còn tồn tại để chuyển tiếp. |
| “Có `SecurityManager` class nên có thể bật lại bằng flag.” | JDK 24+ không hỗ trợ bật manager; flag bật manager làm startup thất bại. |
| “`getSecurityManager()==null` tức mọi thao tác đã được kiểm tra rồi.” | Nó luôn null trên JDK 24+; nhánh bảo vệ có thể bị bỏ qua. |
| “`doPrivileged` luôn cấp quyền cao hơn OS.” | Trong hệ thống cũ, hiệu lực bị ràng buộc bởi policy/stack; từ JDK 24+ nó chỉ chạy action. |
| “`Permission.implies()==true` là user được quyền.” | Đó là quan hệ giữa hai mô tả Permission, không phải authorization từ principal và resource. |
| “`Policy.getPolicy()` sẽ đọc tệp cấu hình policy và enforce như trước.” | Từ JDK 24+, policy mặc định không cấp quyền và tệp policy mặc định bị bỏ. |
| “`AccessController.checkPermission` trả true/false.” | Method trả `void`; trước đây thành công hoặc ném exception; từ JDK 24+ luôn ném `AccessControlException`. |
| “Mọi Permission đều deprecated từ JDK 17.” | Lớp cơ sở `java.security.Permission` không bị đánh dấu như vậy trên JDK 25; nhiều subclass lại bị deprecated for removal ở JDK 25. |
| “Dùng JPMS, signed JAR hoặc ClassLoader riêng đã sandbox plugin.” | Các công cụ đó không thay cho isolation của mã không tin cậy. |
| “Chỉ cần chuyển sang `if (authorized)` là chặn được file/network của untrusted Java code.” | Cần enforcement ở boundary phù hợp: app authorization cho nghiệp vụ, OS/process/container cho tài nguyên hệ thống. |
| “Cứ catch `SecurityException` rồi cho qua để hết lỗi JDK 24.” | Có thể làm lộ quyền truy cập hoặc che lỗi logic; phải đánh giá lại yêu cầu bảo vệ. |
| “`jdeprscan` không báo gì nghĩa là runtime chắc chắn an toàn.” | Nó chỉ quét tham chiếu API deprecated trong phạm vi đối tượng được scan; không thay threat modeling và integration tests. |

## 11. Câu hỏi tự kiểm tra

1. **`SecurityManager` bị deprecated và permanently disabled ở những mốc nào?** Deprecated for removal từ **JDK 17**, permanently disabled từ **JDK 24**.
2. **Vì sao `System.getSecurityManager()==null` có thể là rủi ro?** Code chỉ kiểm tra quyền khi manager tồn tại sẽ luôn bỏ qua kiểm tra trên JDK 24+, dù đội phát triển từng xem nó là ranh giới bảo mật.
3. **`AccessController.doPrivileged` có cấp quyền admin trên JDK 24+ không?** Không. Nó chỉ chạy action; không can thiệp OS privileges hay chính sách authorization.
4. **`AccessController.checkPermission(new FilePermission(...))` ra sao trên JDK 24+?** Luôn ném `AccessControlException`; khác với `FilePermission.implies` vốn chỉ so sánh quyền.
5. **`Policy.setPolicy` có cài được policy trong JDK 24+ không?** Không, luôn ném `UnsupportedOperationException`.
6. **Vì sao không nên nói mọi `Permission` đã deprecated?** `java.security.Permission` là base class không bị đánh dấu deprecated toàn bộ trong JDK 25; nhiều subclass cụ thể được đánh dấu từ JDK 25.
7. **Thư viện chỉ dùng `doPrivileged` để đọc property có thể migrate ra sao?** Xác minh không có security boundary phụ thuộc wrapper; bỏ wrapper và đọc trực tiếp, kiểm thử compatibility.
8. **Plugin không đáng tin cần chặn network/file theo Security Manager cũ thì làm gì?** Thiết kế isolation ở process/OS/container/VM và policy tài nguyên theo threat model; không coi một lệnh Java API là sandbox.
9. **Vì sao cùng một code có thể “fail open” hoặc “fail closed” sau nâng JDK?** Nhánh kiểm tra qua `getSecurityManager` không chạy, còn gọi trực tiếp `checkPermission` lại luôn ném; phải kiểm tra từng call site.
10. **Bộ test migration cần điều gì ngoài khởi động thành công?** Kiểm tra deny/allow đúng tài nguyên, thiếu quyền, runtime restrictions, dependency, negative tests, và policy của môi trường mục tiêu.

## 12. Checklist vận dụng

- [ ] Biết Security Manager là mô hình cũ kiểm soát một số thao tác code trong JVM, không phải RBAC của người dùng.
- [ ] Phân biệt JDK 17 deprecated, JDK 18–23 giai đoạn chuyển tiếp và JDK 24+ disabled.
- [ ] Nhận diện hành vi `getSecurityManager`, `setSecurityManager`, `AccessController.doPrivileged`, `checkPermission`, `Policy.getPolicy/setPolicy`, `Permission.checkGuard` trên JDK 24+.
- [ ] Giải thích sự khác nhau giữa `Permission.implies` và quyết định access control có hiệu lực.
- [ ] Chạy `LegacySecurityAfter24.java` trên JDK 24+ và đối chiếu 6 dòng stdout.
- [ ] Chạy `ExplicitAuthorization.java` trên JDK 17+ và đối chiếu 3 dòng stdout.
- [ ] Quét source/build/dependencies, phân nhóm compatibility vs security boundary, kiểm tra negative path.
- [ ] Di trú quyền nghiệp vụ sang authorization minh bạch, và sandbox untrusted code sang isolation ngoài JVM.
- [ ] Không dùng `-Djava.security.manager` hoặc `java.policy` để tuyên bố đã bảo vệ ứng dụng mới.
- [ ] Kết luận migration dựa trên behavior thực tế, đúng phiên bản JDK và phạm vi triển khai.

**Tài liệu chính thức:**

- [JEP 411 — Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411): động cơ, phạm vi deprecation từ Java 17.
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): thay đổi từ Java 24.
- [Oracle JDK 25 — The Security Manager Is Permanently Disabled](https://docs.oracle.com/en/java/javase/25/security/security-manager-is-permanently-disabled.html): hành vi cố định của từng API, migration và biện pháp thay thế.
- [Oracle JDK 25 — java.security.Permission API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Permission.html): base class không mang `@Deprecated`; `checkGuard` luôn ném.
- [Oracle JDK 25 — java.security.Policy API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Policy.html) và [AccessController API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/AccessController.html): các API deprecated for removal và hành vi sau Java 24.
- [Oracle JDK 25 — Consolidated Release Notes](https://www.oracle.com/java/technologies/javase/25all-relnotes.html): nhiều lớp Permission cụ thể deprecated for removal trong JDK 25.
- [Oracle JDK 26 — jdeprscan command](https://docs.oracle.com/en/java/javase/26/docs/specs/man/jdeprscan.html): cú pháp quét deprecated API trong bytecode.

Đối với code production, luôn đối chiếu **Javadoc và release notes của đúng JDK vendor/version đang chạy**; đặc biệt không suy luận rằng cơ chế kiểm tra quyền cũ còn bảo vệ ứng dụng chỉ vì code vẫn biên dịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="java-secure-coding-010">21.10 Cách ly tiến trình, quyền hệ điều hành và bảo vệ bí mật khi xử lý mã không tin cậy</a>

<details>
<summary>Xem chi tiết</summary>

# 21.10. Cách ly tiến trình, quyền hệ điều hành và bảo vệ bí mật khi xử lý mã không tin cậy

> **Mục tiêu:** Thiết kế được ranh giới thực thi cho mã Java hoặc artifact chưa đáng tin: tách quy trình nhận yêu cầu khỏi worker, chạy worker dưới quyền hệ điều hành tối thiểu, giới hạn filesystem/network/CPU/RAM/process và không để worker tiếp cận bí mật. Biết vì sao Java type safety, bytecode verification, class loader, JPMS và `ProcessBuilder` không tự tạo sandbox bảo mật.

## 1. “Mã không tin cậy” nghĩa là gì và phải bảo vệ những gì?

Mã không tin cậy có thể là file `.java` người dùng gửi để chấm bài, JAR/plugin bên thứ ba, script, bytecode sinh động, thư viện có nguồn gốc chưa được xác nhận, hoặc chương trình do một dịch vụ bên ngoài tạo ra. Ngay cả khi file chạy được, được ký số hoặc vượt qua kiểm tra bytecode, hành vi của nó vẫn có thể bất lợi cho hệ thống.

Hãy xét một nền tảng cho phép người dùng nộp chương trình Java, biên dịch rồi chạy để lấy stdout. Tài sản cần bảo vệ gồm:

- **Máy chủ/host và kernel:** không để mã khách truy cập tài nguyên hoặc khai thác đặc quyền của dịch vụ chấm bài.
- **Dữ liệu của người dùng khác:** không đọc được thư mục, database, file kết quả hay bộ nhớ của tenant khác.
- **Bí mật hệ thống:** không lộ API token, mật khẩu, private key, cookie, cloud credentials, endpoint nội bộ hoặc service-account token.
- **Tính sẵn sàng:** không để mã vô hạn chiếm CPU, RAM, disk, số tiến trình, luồng, socket hay băng thông.
- **Tính toàn vẹn của kết quả:** không để worker ghi trực tiếp bảng điểm/quyền truy cập, sửa chương trình host, giả mạo telemetry hoặc kết quả chấm.
- **Chuỗi cung ứng:** dependency/build tool, annotation processor hoặc script build cũng có thể chạy mã, không chỉ lệnh `java` cuối cùng.

Bắt đầu bằng **threat model**, không bắt đầu bằng lựa chọn Docker/JVM flag. Câu hỏi quan trọng là: Ai điều khiển input? Họ có thể cung cấp mã máy tùy ý hay chỉ dữ liệu? Có cần kết nối mạng, đọc tệp, ghi tệp, gọi native library, sinh tiến trình con hoặc dùng credential không? Mức ảnh hưởng chấp nhận được nếu worker bị chiếm quyền là gì?

| Nguồn mã / dữ liệu | Mối nguy đáng kể | Hướng xử lý |
| --- | --- | --- |
| JAR đã kiểm duyệt của chính đội phát triển | Bug, lỗi logic, dependency bị xâm phạm | Kiểm thử, ký/pin artifact, phân quyền dịch vụ |
| Plugin bên thứ ba, độ tin cậy hạn chế | Đọc dữ liệu, gọi API ngoài ý muốn, chiếm tài nguyên | Process isolation, quyền tối thiểu, API cấp phép rõ |
| Java/JAR do người dùng bất kỳ tải lên | Hành vi cố ý độc hại, resource exhaustion, escape attempt | Sandbox OS/VM có ranh giới rõ; không chia sẻ secrets |
| File dữ liệu không phải executable | Parser bug, decompression bomb, path traversal, injection | Giới hạn input, parser an toàn, xử lý trong môi trường hạn chế |

**Quy tắc gốc:** một worker chạy mã bất kỳ phải được giả định **có thể làm mọi điều mà OS credentials, network, mount và khả năng của tiến trình đó cho phép**, trừ khi một ranh giới độc lập thực sự chặn hành vi.

## 2. Ba lớp khác nhau: tính hợp lệ Java, tiến trình và chính sách OS

### 2.1. Điều JVM bảo vệ được

Compiler, type safety, kiểm tra bytecode và kiểm soát truy cập module giúp đảm bảo nhiều bất biến của mô hình Java. Chúng không tự hạn chế chương trình gọi các API hợp lệ như `Files.readAllBytes`, `HttpClient.send`, `ProcessBuilder.start`, chạy CPU đến hết quota hoặc tải native code khi quyền runtime cho phép.

`ClassLoader` riêng hữu ích để tổ chức class namespace và nạp/unload code; một class loader riêng **không** tạo định danh OS riêng, filesystem riêng hay quyền network riêng. JPMS `exports`/`opens` phân chia API và reflection, **không** cấp hoặc từ chối quyền đọc tệp và gọi mạng theo từng module.

Trước đây có Security Manager/policy cho một số trường hợp hạn chế quyền chạy Java. Nhưng **JDK 17–23** đã deprecate Security Manager for removal; từ **JDK 24** Security Manager bị **vô hiệu hóa vĩnh viễn** (JEP 486). Không thiết kế sandbox Java hiện đại dựa vào `System.setSecurityManager` hoặc `-Djava.security.manager`.

### 2.2. Tiến trình riêng giúp gì?

Một tiến trình Java con có địa chỉ bộ nhớ/heap riêng với host. Lỗi JVM, crash hay memory corruption trong tiến trình con thường không trực tiếp biến thành thao tác trên heap của tiến trình cha. Tách process giúp đặt timeout, giám sát exit code và thu hồi tài nguyên thuận tiện hơn.

Nhưng nếu cha và con chạy **cùng tài khoản OS**, chúng thường có nhiều quyền file/network như nhau. Process riêng **chưa đồng nghĩa sandbox**. Tiến trình con còn có thể sinh tiến trình cháu; việc chỉ gọi `Process.destroyForcibly()` lên PID trực tiếp chưa bảo đảm dọn sạch toàn bộ process tree.

### 2.3. Ranh giới OS hoặc VM mới thực thi quyền tài nguyên

Hệ điều hành và hạ tầng cô lập mới có thể đặt giới hạn độc lập với sự hợp tác của đoạn mã khách:

- Danh tính/nhóm người dùng riêng, filesystem ACL, policy SELinux/AppArmor hoặc sandbox OS tương đương.
- Mount namespace, thư mục chỉ đọc và vùng ghi tạm; không mount dữ liệu host không cần thiết.
- Network namespace, firewall/egress policy, cấm truy cập metadata service và mạng nội bộ.
- cgroups/Job Objects/quotas cho RAM, CPU, số process; deadline ở hệ thống điều phối.
- Seccomp và giảm Linux capabilities để thu hẹp syscall/privilege surface.
- Container, VM hoặc microVM với mức cô lập thích hợp cho threat model.

**Bảng phân biệt nhanh:**

| Biện pháp | Bảo vệ chính | Giới hạn còn lại |
| --- | --- | --- |
| `javac`/verifier/JPMS | Quy tắc kiểu, định dạng class, package access | Không giới hạn đọc file, network, CPU hay secret |
| `new ClassLoader(...)` | Namespace và cơ chế load class | Không có quyền OS riêng |
| `new ProcessBuilder(...)` | Tách bộ nhớ/process lifecycle | Nếu cùng identity thì vẫn có thể cùng quyền host |
| User OS ít quyền + filesystem ACL | Giới hạn tài nguyên mà identity truy cập | Chưa giới hạn đủ CPU/network nếu không thêm policy |
| Container với namespace, cgroup, seccomp, LSM | Cô lập nhiều tài nguyên trên cùng kernel | Chia sẻ kernel, phụ thuộc cấu hình/runtime, vẫn có rủi ro escape |
| VM/microVM chuyên dụng | Ranh giới kernel/guest riêng, tăng cô lập | Vẫn cần hardening, cập nhật và kiểm soát hypervisor/quản trị |

Không có cơ chế đơn lẻ nào đủ cho mọi trường hợp. Dùng nhiều lớp vì một lỗi cấu hình có thể làm suy yếu lớp khác.

## 3. Kiến trúc đúng: control plane đáng tin và worker ít quyền

Mô hình thực tế:

```text
                         TRUSTED CONTROL PLANE
┌─────────────────┐      ┌───────────────────────┐
│ API / Upload    │ ───> │ Validate + Quarantine │
│ Auth + RateLimit│      │ metadata, hash, quota  │
└─────────────────┘      └──────────┬────────────┘
                                   │ job ID + input đã giới hạn
                                   v
                        ┌───────────────────────┐
                        │ Scheduler / Broker    │
                        │ policy + quota + TTL  │
                        └──────────┬────────────┘
                                   │ tạo worker, KHÔNG cấp secret
                         ISOLATION BOUNDARY
                                   v
                  ┌──────────────────────────────────┐
                  │ Worker process / container / VM  │
                  │ - UID riêng; no root/capability  │
                  │ - input read-only                │
                  │ - scratch bị giới hạn            │
                  │ - no network hoặc egress allowlist│
                  │ - CPU/RAM/PID/time/output limits │
                  └──────────────────┬───────────────┘
                                     │ kết quả dữ liệu có giới hạn
                                     v
                        ┌───────────────────────┐
                        │ Result validator      │
                        │ scan, parse, cap size │
                        └──────────┬────────────┘
                                   v
                         Storage / Results API
```

Một công việc nên có **job ID và identity riêng** (tùy threat model có thể dùng một worker per job, namespace/VM riêng), đầu vào immutable, timeout và quota bắt buộc. Worker trả lại **dữ liệu kết quả**, không nắm quyền trực tiếp cập nhật quyền người dùng, điểm số hay dữ liệu tenant.

Đầu vào/đầu ra của boundary cũng là đối tượng tấn công. Ví dụ output JSON, JAR, ZIP, HTML hoặc log có thể chứa chuỗi kiểm soát terminal, path traversal, mã HTML/script hoặc file nén giải phóng ra dung lượng khổng lồ. Result validator phải xử lý output như dữ liệu không tin cậy: giới hạn byte, kiểm tra schema, không giải nén vào đường dẫn tùy ý, escape khi hiển thị và không tin metadata do worker khai báo.

### 3.1. Compiling cũng có thể là chạy code

Mã nguồn được nộp có thể kích hoạt compiler plugins, annotation processors, Maven/Gradle plugins, build scripts, test runners hoặc native tooling. Không nên cho giai đoạn **build** chạy cùng quyền với dịch vụ API. Nếu chỉ cần compile file Java thông thường và không dùng annotation processing, `javac -proc:none` là một cách vô hiệu hóa annotation processing của `javac`; nhưng tùy chọn này **không** tự hạn chế plugin/build script khác và không thay sandbox. Cả compilation lẫn execution đều cần đặt trong boundary phù hợp.

Không tự động tải dependency từ Internet bằng quyền host. Nếu bài chấm cho phép dependency, dùng cache/mirror có kiểm soát, pin phiên bản/hash, kiểm soát repository và tách giai đoạn resolver đáng tin khỏi execution của code khách.

## 4. Quyền hệ điều hành tối thiểu: ai có thể đọc gì?

Nguyên tắc **least privilege**: nếu chỉ cần đọc bài nộp và ghi một file kết quả, worker không cần quyền đọc home directory của host, cloud credentials, Docker socket, SSH agent, Kubernetes token hay thư mục chứa secret của dịch vụ.

### 4.1. Danh tính của worker

- Dùng UID/GID hoặc security identity chuyên biệt, **không phải root** và không phải tài khoản có quyền của API server.
- Không chia sẻ UID giữa các tenant nhạy cảm nếu dữ liệu cần tách biệt bằng DAC; cân nhắc worker identity per job/tenant.
- Không thêm worker vào group có thể đọc socket quản trị, device nhạy cảm hoặc file secret.
- Thu hồi Linux capabilities không cần thiết, áp dụng `no_new_privs`/chính sách cấm privilege escalation và kiểm tra setuid binaries.
- Với Linux container, cân nhắc rootless runtime/user namespace remapping; chạy dưới UID non-root bên trong container là hữu ích nhưng **không tự làm host runtime thành rootless**.

Một thư mục được mount “read-only” ngăn sửa **trong mount đó**, nhưng worker vẫn có thể **đọc** bất kỳ secret nào nằm trong mount. Read-only **không** thay thế kiểm soát bí mật.

### 4.2. Filesystem và mount

Giới hạn worker tới:

- Code/input đã phê duyệt trong một mount read-only.
- Scratch directory riêng, có giới hạn dung lượng/quota, quyền truy cập phù hợp, xóa sau job.
- Output location do broker quản lý, giới hạn dung lượng, không cho worker tự chọn path bên ngoài sandbox.

Không mount `/` của host, `/home`, `/var/run/docker.sock`, `/run/containerd`, thư mục chứa key, hoặc cloud credential paths. Không trao `--privileged` và không cho hostPath bừa bãi. Khi cần mount thư mục có thể ghi, tránh mount dữ liệu tenant khác vào cùng container.

Filesystem namespace, chroot hoặc container path isolation không tự ngăn khai thác kernel, syscall độc hại hay truy cập secret do vô tình mount. `chroot` một mình không phải sandbox hoàn chỉnh.

### 4.3. Network và nguồn rò rỉ

Thiết kế mặc định nên **chặn outbound network** nếu việc chấm/biên dịch không yêu cầu. Nếu phải gọi mạng, dùng proxy/egress gateway chỉ cho phép đích và giao thức cần thiết; áp dụng DNS/network policy ở mức thực thi, tránh SSRF, truy cập localhost/mạng private và cloud instance metadata như `169.254.169.254` (bao gồm các đường IPv6/địa chỉ tương đương theo môi trường).

Một lệnh `--network=none` trong Docker Linux bỏ kết nối mạng ngoài container theo network namespace cấu hình, nhưng không sửa quyền file, không chặn IPC được cố ý cấp, và không tạo phân quyền bí mật. Với Kubernetes, NetworkPolicy chỉ có tác dụng khi plugin mạng thực sự thực thi policy; cấu hình YAML một mình không đủ.

### 4.4. Giới hạn tài nguyên và timeout

Cần đặt giới hạn **độc lập với JVM**:

| Tài nguyên | Điều cần giới hạn | Vì sao |
| --- | --- | --- |
| Wall-clock time | Deadline trên job, kill khi quá hạn | Vòng lặp vô hạn hoặc tiến trình treo |
| CPU | CPU quota/weight và concurrency per tenant | Không để một job chiếm cả node |
| Memory | cgroup/Job Object/VM limit, OOM behavior | `-Xmx` chỉ giới hạn một phần heap Java, không phải tổng RSS/native/process tree |
| Process/thread | PID quota, cgroup pids, OS job limits | Fork bomb, sinh nhiều JVM/threads |
| File/disk/temp | Disk quota, tempfs/volume limit, số lượng inode | Ghi log/output khổng lồ, archive bomb |
| Stdout/stderr | Giới hạn số byte, tốc độ, thời gian lưu | Không bị tràn log, RAM, storage |
| Network | Egress policy, request/byte caps nếu dùng | Exfiltration hoặc outbound denial-of-service |

**Không xem `-Xmx128m` là memory sandbox 128 MB.** Heap, metaspace, direct buffers, native stacks, JIT code cache, process con và memory mapping đều có thể góp vào mức sử dụng bộ nhớ. Bên ngoài JVM phải có giới hạn tổng thể.

Timeout chỉ có ý nghĩa nếu hệ thống thực thi cleanup. Đừng chỉ ném exception ở host rồi để tiến trình con/cháu sống tiếp. Với Linux, dùng cgroup/container lifecycle để quản lý nhóm; với Windows, dùng Job Objects/process group phù hợp. `ProcessHandle.descendants()` hữu ích để quan sát nhưng có race condition, không thay cơ chế OS bảo đảm thu hồi cả nhóm.

## 5. Linux: kết hợp namespaces, cgroups, seccomp và MAC

Các lớp thường phối hợp trong container/sandbox Linux:

1. **User namespace + UID/GID:** ánh xạ danh tính và giảm quyền hiệu dụng lên host. Phải hiểu quyền của container runtime, quyền cấp vào socket quản lý và trạng thái user namespace.
2. **Mount namespace + read-only root filesystem:** giới hạn cây filesystem nhìn thấy, chỉ mount input/scratch bắt buộc.
3. **PID namespace + PID limits:** thu hẹp không gian PID nhìn thấy; cgroup `pids.max` bảo vệ chống sinh quá nhiều process/thread.
4. **Network namespace + firewall/egress policy:** cách ly network stack; không cấp host network khi không cần.
5. **Seccomp:** lọc một tập syscall theo policy. Seccomp **không** phải cơ chế kiểm tra path file hoặc quyền truy cập file; cần DAC/LSM/mounts cho việc đó.
6. **SELinux/AppArmor:** mandatory access control để hạn chế hành vi ngoài quyền UNIX thông thường khi được bật và cấu hình đúng.
7. **cgroups:** giới hạn và theo dõi CPU/memory/pids; cùng với quota filesystem và wall-clock timeout.

Để giảm rủi ro thoát container, cập nhật kernel/runtime, sử dụng seccomp profile thích hợp, tránh privilege/capabilities quá mức, kiểm soát device và không chia sẻ host namespaces. Rootless container có thể giảm tác động của một số tình huống, nhưng **vẫn chia sẻ kernel** nên phải chọn mức cô lập theo rủi ro. Với code cố ý độc hại và tenant không tin cậy nhau, VM/microVM hoặc sandbox runtime tăng cô lập có thể phù hợp hơn container thông thường.

## 6. Thực hành Docker Linux: cấu hình hạn chế có thể kiểm tra

Ví dụ này dùng image JRE công khai để kiểm tra **các lớp OS**, không thực thi code độc hại. Cần Docker Engine hỗ trợ các tính năng bên dưới và kết nối mạng cho bước pull image trước khi chạy:

```bash
docker pull eclipse-temurin:17-jre

docker run --rm \
  --network=none \
  --read-only \
  --user=65534:65534 \
  --cap-drop=ALL \
  --security-opt=no-new-privileges \
  --pids-limit=64 \
  --memory=256m \
  --memory-swap=256m \
  --cpus=0.5 \
  --tmpfs /tmp:rw,nosuid,size=16m \
  --workdir=/tmp \
  eclipse-temurin:17-jre \
  java -version
```

Lệnh chạy Java trong container và in **phiên bản Java được đóng gói trong image**; văn bản/patch version cụ thể thay đổi theo image nên không cố định stdout. `java -version` thường in ra stderr. Giá trị của ví dụ là những hạn chế runtime, không phải output của lệnh.

| Flag | Ý nghĩa | Không nên hiểu lầm |
| --- | --- | --- |
| `--network=none` | Không kết nối container vào network bên ngoài theo cấu hình mặc định Linux | Không chặn đọc file đã mount; loopback nội bộ vẫn tồn tại |
| `--read-only` | Root filesystem của container chỉ đọc | Không ngăn đọc secret nằm trong image/mount |
| `--user=65534:65534` | Chạy process với UID/GID non-root trong container | Chưa đủ để bảo đảm host runtime không có quyền cao |
| `--cap-drop=ALL` | Bỏ các Linux capabilities được cấp cho container | Không thay syscall filter/mount/OS identity |
| `--security-opt=no-new-privileges` | Không cho tiến trình lấy thêm đặc quyền qua exec theo cơ chế OS | Không tự chặn network hoặc file I/O |
| `--pids-limit=64` | Giới hạn số task/process theo cgroup, nếu được hỗ trợ | Không giới hạn CPU hay disk |
| `--memory=256m`, `--memory-swap=256m` | Giới hạn RAM và tổng RAM/swap theo cgroup hỗ trợ | Không thay giới hạn stdout/disk |
| `--cpus=0.5` | Đặt CPU quota khoảng nửa lõi | Không đặt wall-clock deadline |
| `--tmpfs /tmp:...size=16m` | Tạo scratch tạm có giới hạn, không lưu trên image layer | Bản thân mount không kiểm soát mọi loại disk output |
| `--rm` | Xóa container sau khi dừng | Không thay cleanup khi job spawn tiến trình bên ngoài boundary |

**Mở rộng cho JAR thật:** chỉ sau khi kiểm tra chính sách, có thể mount **một thư mục input cụ thể** read-only, chỉ truyền tên file trong namespace của container, và dùng image đã kiểm duyệt/pin bằng immutable digest. Không mount kho source, SSH keys, service account hay socket Docker.

Ví dụ cấu trúc với file `input/submission.jar` đã tồn tại (các bước mount không áp dụng nếu thư mục host chưa có), cần quyền đọc cho UID trong container:

```bash
docker run --rm \
  --network=none --read-only \
  --user=65534:65534 \
  --cap-drop=ALL --security-opt=no-new-privileges \
  --memory=256m --pids-limit=64 --cpus=0.5 \
  --tmpfs /tmp:rw,nosuid,size=16m \
  --mount "type=bind,source=$PWD/input,target=/input,readonly" \
  eclipse-temurin:17-jre \
  java -Xmx128m -Djava.io.tmpdir=/tmp -jar /input/submission.jar
```

Đây là **mẫu câu lệnh cho Linux/Bash, không phải bằng chứng rằng cấu hình đã an toàn cho mọi loại code**. Artifact thật phải có metadata và dependency cần thiết trong image/mount; nhiều ứng dụng cần quyền ghi vào các thư mục khác. Chỉ cấp những thư mục đã đánh giá, không sửa bằng cách tự động bỏ `--read-only` hoặc tăng quyền.

Trong production cần thêm **deadline do bộ điều phối cưỡng chế**, giới hạn stdout/stderr, logging không chứa secret, artifact digest pinning và chính sách dọn sạch container khi job kết thúc/lỗi. Đánh giá cả quyền của Docker daemon hoặc Kubernetes node; người có quyền điều khiển Docker socket có thể sở hữu năng lực quản trị host rất cao. Không giao socket này cho worker.

### 6.1. Container không có nghĩa tự động cách ly tuyệt đối

Một container bình thường chia sẻ Linux kernel với host. Container escape, kernel bug, cấu hình `--privileged`, mount host path hoặc runtime/socket lộ vẫn có thể biến thành host compromise. Với workload nhiều tenant thực sự không tin cậy, cân nhắc VM/microVM, sandbox runtime tương ứng và worker node pool riêng; cập nhật security patch thường xuyên. Namespace không thay quy trình rà soát image/base OS hoặc kiểm soát secrets.

## 7. Thực hành Java 17+: `ProcessBuilder` chỉ là lớp điều phối

Ví dụ độc lập sau minh họa cách host khởi động **tiến trình Java con**, thiết lập thư mục làm việc riêng, chọn môi trường biến rõ ràng và đặt timeout. Mục tiêu là hiểu **giới hạn** của Java API: đoạn code này không triển khai OS sandbox và **chỉ được chạy với chương trình demo tin cậy**.

Tạo file **`ProbeWorker.java`**:

```java
import java.nio.file.Files;
import java.nio.file.Path;

public final class ProbeWorker {
    public static void main(String[] args) throws Exception {
        boolean visible = System.getenv("DEMO_SECRET") != null;
        Files.writeString(Path.of("probe.txt"), Boolean.toString(visible));
    }
}
```

Tạo file **`HostLauncher.java`** trong cùng thư mục:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class HostLauncher {
    public static void main(String[] args) throws Exception {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path java = Path.of(
            System.getProperty("java.home"),
            "bin",
            windows ? "java.exe" : "java"
        );
        Path classes = Path.of(
            HostLauncher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()
        ).toAbsolutePath();
        Path work = Files.createTempDirectory("java-worker-demo-");

        try {
            ProcessBuilder builder = new ProcessBuilder(
                java.toString(), "-cp", classes.toString(), "ProbeWorker"
            );
            builder.directory(work.toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);

            Map<String, String> childEnv = builder.environment();
            childEnv.clear();
            // Với Windows, giữ đường dẫn hệ thống tối thiểu khi cần.
            if (windows && System.getenv("SystemRoot") != null) {
                childEnv.put("SystemRoot", System.getenv("SystemRoot"));
            }

            Process child = builder.start();
            boolean completed = child.waitFor(5, TimeUnit.SECONDS);
            if (!completed) {
                child.destroyForcibly().waitFor();
                throw new IllegalStateException("worker timed out");
            }

            String visible = Files.readString(work.resolve("probe.txt"));
            System.out.println("exit=" + child.exitValue());
            System.out.println("secret_visible=" + visible);
        } finally {
            Files.deleteIfExists(work.resolve("probe.txt"));
            Files.deleteIfExists(work);
        }
    }
}
```

Tại thư mục chứa hai file, dùng JDK 17 trở lên:

```bash
javac HostLauncher.java ProbeWorker.java
java HostLauncher
```

**Stdout của bản demo ở điều kiện thông thường:**

```text
exit=0
secret_visible=false
```

Các điểm cần đọc đúng:

- `ProcessBuilder` gọi executable bằng danh sách đối số, không tự mở shell; điều này giúp tránh một số kiểu shell injection nếu bạn không cố ý gọi shell. Nhưng input vẫn có thể nguy hiểm cho chương trình đích nếu nó tự phân tích options/path.
- `builder.environment().clear()` bỏ phần lớn biến môi trường kế thừa, bao gồm `DEMO_SECRET` trong ví dụ. Trên Windows giữ `SystemRoot` khi cần để khởi động bình thường. Môi trường trống **không** cấm con đọc file, gọi network hoặc khai thác quyền OS mà nó có.
- `builder.directory(work.toFile())` thay working directory; đây **không** phải chroot, jail hay sandbox. Worker vẫn có thể dùng đường dẫn tuyệt đối tới file mà tài khoản OS của nó có quyền.
- `waitFor(5, TimeUnit.SECONDS)` đặt một ngưỡng quan sát. Nếu con quá hạn, host gọi `destroyForcibly()` với **process trực tiếp**; code này không quản lý đáng tin toàn bộ descendants, CPU/memory quota hoặc cgroup.
- Redirect stdout/stderr sang `DISCARD` giúp demo không tích lũy output vào RAM hay file; trong production cần cơ chế **thu log hữu hạn** để chẩn đoán và chặn log flooding. Không dùng `readAllBytes()` không giới hạn trên stdout từ chương trình không tin cậy.
- Giới hạn `-Xmx`, nếu thêm vào Java command, chỉ hỗ trợ quản lý heap, không thay OS process/memory limit.

Bởi vậy `HostLauncher` chỉ là ví dụ về **process lifecycle và environment hygiene**. Muốn thực thi mã không tin cậy thật, host phải yêu cầu sandbox service/container/VM tạo worker **trước khi** code được chạy, rồi chỉ giao job ID/input cho worker đó. Không chạy JAR ngoài sandbox rồi mới cố điều chỉnh quyền bằng Java API.

## 8. Bảo vệ bí mật: tốt nhất là không giao bí mật cho worker

Bí mật có thể xuất hiện ở nhiều nơi mà lập trình viên không để ý:

| Nơi bí mật đi qua | Rủi ro | Hướng phòng ngừa |
| --- | --- | --- |
| Environment variables | Process và thư viện của nó có thể đọc; một số công cụ chẩn đoán, dump hoặc child process có thể làm lộ | Không đưa secret vào môi trường của worker không tin cậy |
| Command-line arguments | Có thể bị lộ qua process listing, log lệnh, telemetry hoặc script history | Không truyền password/token qua argv |
| File mount/secret volume | Worker có thể đọc nội dung khi mount, kể cả read-only | Không mount secret của host; dùng tài khoản không có quyền đọc |
| Docker/Kubernetes service account | Có thể cho quyền gọi control plane/cloud API | Không mount token tự động khi không cần; dùng service account tối thiểu |
| Image/JAR/config file | Layer, history hoặc constant có thể chứa credential | Không bake secret vào image/repository/artifact |
| Stdout/stderr, exception, heap/thread dump | Rò rỉ khi debug hoặc ghi log | Redact, hạn chế log, phân quyền và retention |
| Network/metadata service | Có thể truy xuất cloud credentials hoặc gọi internal services | Egress default-deny; chặn metadata và internal network |

### 8.1. Mẫu “broker giữ quyền”

Nếu worker cần thao tác có đặc quyền (ví dụ gửi kết quả chấm lên backend), **broker đáng tin** thực hiện việc đó sau khi xác thực job/tenant và kiểm tra kết quả. Worker chỉ gửi kết quả dạng dữ liệu có giới hạn qua một kênh do host tạo.

Nếu một task thực sự cần gọi API bên ngoài, hãy cân nhắc cấp credential **ngắn hạn, scope nhỏ, chỉ dùng cho đúng hành động**, qua broker có kiểm tra chính sách; tránh cho worker sở hữu API token có thể đọc/sửa mọi tenant. Đây là quyết định ngoại lệ dựa trên threat model, không phải mặc định. Khi dữ liệu bí mật đã bị gửi cho mã độc, việc xóa env hay đổi quyền về sau **không** thu hồi được dữ liệu đã lộ; cần xử lý sự cố và xoay vòng credential.

### 8.2. Chia sẻ đúng lượng dữ liệu

Tạo **view đã rút gọn** hoặc input file riêng theo job, không cung cấp database connection string của hệ thống, toàn bộ bucket, thư mục home hay log chứa secret. Kiểm tra quyền sở hữu tenant **trước** khi tạo input. Output phải qua bộ kiểm tra schema trước khi nhập vào database của host.

Không tin các tuyên bố bảo mật do chính worker in ra như “đã sandbox” hay “đã xóa secret”. Enforcement phải có bằng chứng từ OS/container orchestrator và quan sát độc lập ở broker.

## 9. Hệ thống Windows, Kubernetes và lựa chọn mức cô lập

### 9.1. Windows

Tương đương việc “đổi UID Linux” không chỉ là `ProcessBuilder` trên Windows. Các công cụ có thể phối hợp gồm **restricted access token**, Windows integrity levels, AppContainer theo khả năng môi trường, quyền ACL và **Job Objects** để nhóm/giới hạn tài nguyên và quản lý lifecycle. Job Object đơn lẻ **không** thiết lập toàn bộ chính sách đọc file/network; phải cấu hình identity và access policy riêng. Windows containers có các chế độ process isolation/hypervisor isolation với các mức ranh giới khác nhau, tùy môi trường hỗ trợ.

### 9.2. Kubernetes

Một Pod chạy worker không tin cậy nên được đánh giá ở cả Pod specification, cluster/network và node pool. Ví dụ các ý trong `securityContext` và policy:

- `runAsNonRoot: true`, UID/GID được xác định; `allowPrivilegeEscalation: false`.
- `readOnlyRootFilesystem: true` cùng writable scratch riêng có quota hợp lý.
- `capabilities.drop: ["ALL"]`, `seccompProfile.type: RuntimeDefault` hoặc policy được kiểm duyệt.
- Không dùng `privileged`, `hostPID`, `hostNetwork` hoặc hostPath trừ khi có lý do/đánh giá riêng.
- `automountServiceAccountToken: false` nếu worker không dùng Kubernetes API; RBAC tối thiểu.
- Resource requests/limits, quota/concurrency, runtime deadline và cleanup Job/Pod.
- NetworkPolicy default-deny egress/ingress khi CNI có thực thi policy; cân nhắc node pool/VM riêng cho workload không tin cậy.

Pod Security Standards **Restricted** giúp định nghĩa baseline bảo mật nhưng không tự chặn mọi data exfiltration, không thay quota/NetworkPolicy và không biến mọi container thành VM cách ly mạnh.

### 9.3. Khi nào cân nhắc VM/microVM?

Nếu nhiều tenant có thể gửi **code tùy ý có chủ đích tấn công**, giá trị host cao hoặc yêu cầu ranh giới giữa các khách hàng mạnh, VM/microVM mỗi job hoặc pool ít chia sẻ có thể tốt hơn container dùng chung kernel. Đổi lại là chi phí khởi động, bộ nhớ, giám sát và quản lý image. Cần tính chi phí theo số job và rủi ro, đồng thời thực hiện cập nhật kernel/hypervisor và kiểm soát credentials giống như với container.

## 10. Giao thức input/output và xử lý lỗi phải chống được input ác ý

Một sandbox có quyền đúng vẫn có thể gây sự cố nếu broker xử lý output không an toàn.

**Trước chạy:** giới hạn bytes/entry/depth của archive và code, từ chối đường dẫn `../` hoặc absolute paths khi giải nén, kiểm tra symlink/hardlink, nén lồng nhau và file bẫy. Không giải nén dữ liệu trực tiếp vào source tree hay thư mục chứa dữ liệu tenant khác. Tránh parser chạy code hoặc deserialize object Java từ nguồn không tin cậy.

**Trong khi chạy:** ràng buộc wall-clock, quotas và số job đồng thời; log stream phải có byte limit, giới hạn kích thước/tần suất; nếu parser nhận binary hoặc log lớn, phải dừng hoặc truncate theo policy. Không để pipes chặn worker vô thời hạn, cũng không đọc toàn bộ stdout vào RAM vô hạn.

**Sau chạy:** chấm điểm/phân tích kết quả dưới quyền broker đáng tin. Exit code 0 không có nghĩa kết quả đúng hay sạch. Không cho output tự chỉ định nơi ghi tùy ý, không render HTML/Markdown không tin cậy thành nội dung có đặc quyền, không thực thi script từ output. Dọn scratch/workspace/credentials, thu hồi token ngắn hạn và thu thập evidence về quota/timeout/exit reason.

**Khi lỗi:** phân biệt rõ lỗi môi trường, lỗi code người dùng, timeout, OOM kill, vi phạm policy và lỗi broker. Không lộ exception nội bộ chứa path/token cho người dùng cuối. Dù job bị hủy, hệ thống phải thu hồi cả container/process group và các handle dữ liệu tương ứng.

## 11. Những hiểu nhầm cần loại bỏ

| Nhận định sai | Sửa lại |
| --- | --- |
| “JAR có chữ ký nên chạy an toàn.” | Chữ ký hỗ trợ integrity và xác định signer theo trust policy; không chứng minh mã không độc hại. |
| “Bytecode verifier/JPMS sẽ chặn mã Java đọc file bí mật.” | Chúng không thay quyền file của OS. |
| “Đưa plugin vào class loader khác là sandbox.” | Class loader không cấp UID/network/filesystem policy riêng. |
| “Chạy `ProcessBuilder` là đã cô lập.” | Process riêng vẫn có thể dùng cùng tài khoản, quyền và mạng của host. |
| “`-Xmx128m` bảo đảm không dùng hơn 128 MB RAM.” | Native memory, metaspace, thread stacks và process con không nằm hoàn toàn trong heap cap. |
| “Container non-root + read-only rootfs là đủ.” | Vẫn phải hạn chế mounts, network, capabilities, kernel attack surface, quotas và runtime ownership. |
| “Secret trong env an toàn vì chỉ tồn tại khi process chạy.” | Code trong process có thể đọc env; diagnostic và child process có thể làm lộ. |
| “Nếu timeout thì kill PID cha là xong.” | Descendants có thể sống tiếp; cần cơ chế job/cgroup/container quản lý cả nhóm. |
| “Không có stdout nghĩa là không có exfiltration.” | Exfiltration có thể xảy ra qua network, filesystem, IPC hoặc side channel. |
| “Có NetworkPolicy YAML tức là network bị chặn.” | Cần CNI/runtime thực thi và kiểm tra policy từ trong môi trường thật. |

## 12. Checklist nghiệm thu môi trường chạy mã không tin cậy

Thực hiện kiểm tra theo lớp, ghi lại bằng chứng thay vì chỉ nhìn cấu hình:

1. **Identity:** Worker chạy bằng user/identity riêng có quyền tối thiểu; không thể truy cập secret/host management socket.
2. **Filesystem:** Worker chỉ thấy input cần thiết và scratch bounded; không đọc được thư mục/credential của host hay tenant khác; không thể ghi ra ngoài vùng được phép.
3. **Network:** Egress bị chặn khi không cần; nếu cần, có allowlist thực thi, không truy cập được metadata/internal endpoints ngoài policy.
4. **Resource limits:** Kiểm thử chương trình tạo vòng lặp CPU, cấp RAM, sinh process/thread và ghi stdout/disk; timeout/OOM/quota có phản ứng đúng và không ảnh hưởng host rộng hơn mức cho phép.
5. **Process cleanup:** Job bị hủy phải chấm dứt cả nhóm process, giải phóng mount/scratch và không còn subprocess sống sót.
6. **Secrets:** Không có secret trong image, env, argv, mount, stdout, log hoặc input của worker; broker giữ quyền privileged operations.
7. **Artifact supply chain:** Pin version/digest của runtime image; kiểm tra nguồn mã/dependency, cập nhật base image, JDK và kernel.
8. **I/O protocol:** Giới hạn input/output, kiểm tra schema/path/symlink, chống archive bomb và xử lý kết quả dưới quyền broker.
9. **Multi-tenancy:** Tài nguyên và giới hạn concurrency được gắn theo tenant/job; không tái dùng workspace chứa dữ liệu của tenant khác.
10. **Operations:** Ghi audit job ID, policy version, runtime image digest, exit reason, resource usage và timeout **mà không ghi secret**; kiểm tra lại chính sách sau mỗi thay đổi hạ tầng.

Không thử tấn công có tác động mạnh trên production. Hãy dùng worker sandbox thử nghiệm riêng để xác nhận file bị từ chối, network bị chặn, quotas được thực thi và cleanup hoàn tất.

## 13. Tự kiểm tra

**Câu 1.** Worker chạy bằng `java -Xmx128m` trong process riêng cùng user với API server. Worker có đọc được file mà API server có quyền đọc không?

**Đáp:** Có thể. Nếu cùng danh tính OS và đường dẫn/tài nguyên truy cập được, Java process riêng và heap limit không tạo quyền file riêng. Phải tách identity, mount/ACL và kiểm soát tài nguyên OS.

**Câu 2.** Worker không có network nhưng được mount read-only thư mục chứa private key. Bí mật có được bảo vệ không?

**Đáp:** Chưa. Worker vẫn có thể **đọc** private key, có thể đưa vào output/log hoặc khai thác kênh khác. Không mount bí mật; broker giữ credential.

**Câu 3.** Vì sao chỉ gọi `Process.destroyForcibly()` sau timeout chưa đủ?

**Đáp:** Nó xử lý process đích, không bảo đảm mọi descendant và tài nguyên của nhóm bị thu hồi. Cần cơ chế quản lý OS như container/cgroup hoặc Job Object và cleanup được kiểm chứng.

**Câu 4.** `--network=none`, `--read-only`, `--cap-drop=ALL` có thay việc xem xét security của image và host kernel không?

**Đáp:** Không. Các flag giảm bề mặt tấn công, nhưng vẫn có chung kernel, image có thể chứa dữ liệu nhạy cảm và cấu hình khác có thể tạo lối thoát. Cần cập nhật runtime, hạn chế mount và kiểm tra boundary.

**Câu 5.** JVM bytecode verification có nhận biết rằng code đang gửi `System.getenv("API_KEY")` lên một server không?

**Đáp:** Không. Verifier quan tâm tính hợp lệ của bytecode. Quyền đọc env/network phải được ngăn bằng kiến trúc dữ liệu, OS policy và egress rules, không trông chờ verifier.

**Câu 6.** Một hệ thống cần dùng secret để xác nhận kết quả bài chấm. Nên chuyển secret vào worker dưới dạng environment variable không?

**Đáp:** Không phải mặc định. Đưa kết quả từ worker qua kênh hạn chế tới broker đáng tin; broker dùng secret sau khi kiểm tra job và output. Chỉ cấp credential cực hẹp, ngắn hạn cho worker nếu threat model và nhu cầu đã được xem xét rõ ràng.

## 14. Tài liệu tham khảo

- Oracle, **ProcessBuilder** (Java SE 17): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.html
- Oracle, **Process / ProcessHandle** (Java SE 17): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Process.html và https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessHandle.html
- OpenJDK, **JEP 486 – Permanently Disable the Security Manager**: https://openjdk.org/jeps/486
- Oracle, **Security Manager Is Permanently Disabled** (JDK 24): https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html
- Docker, **Running Containers**: https://docs.docker.com/engine/containers/run/
- Docker, **docker container run options**: https://docs.docker.com/reference/cli/docker/container/run/
- Kubernetes, **Configure a Security Context for a Pod or Container**: https://kubernetes.io/docs/tasks/configure-pod-container/security-context/
- Kubernetes, **Pod Security Standards**: https://kubernetes.io/docs/concepts/security/pod-security-standards/

**Ghi nhớ:** Chạy mã không tin cậy là một bài toán về **OS privileges, tài nguyên và dữ liệu được cấp cho process**, chứ không chỉ về Java bytecode hợp lệ. Tách worker, giữ secret ở broker và cưỡng chế các giới hạn bằng boundary độc lập với chính mã khách là nền tảng của một hệ thống an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)
