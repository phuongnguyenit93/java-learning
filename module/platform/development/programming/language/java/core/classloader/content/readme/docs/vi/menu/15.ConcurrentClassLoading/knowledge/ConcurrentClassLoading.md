<a id="back-to-top"></a>

# Nạp đồng thời

## Menu
- [Khóa nạp lớp trong triển khai mặc định](#concurrent-class-loading-001)
- [getClassLoadingLock và khóa theo tên lớp](#concurrent-class-loading-002)
- [Parallel-capable class loader là gì](#concurrent-class-loading-003)
- [registerAsParallelCapable và điều kiện đăng ký](#concurrent-class-loading-004)
- [Tránh race condition giữa findLoadedClass và defineClass](#concurrent-class-loading-005)
- [Deadlock khi nhiều loader ủy quyền chéo](#concurrent-class-loading-006)
- [Phân biệt khóa nạp lớp với khóa khởi tạo lớp](#concurrent-class-loading-007)
- [Thiết kế cache bytecode và nguồn tài nguyên an toàn cho nhiều luồng](#concurrent-class-loading-008)

## <a id="concurrent-class-loading-001">Khóa nạp lớp trong triển khai mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa nạp lớp trong triển khai mặc định của ClassLoader

> **Phạm vi:** Java Core → ClassLoader → Nạp đồng thời. Bài học giải thích vì sao JVM cần đồng bộ khi nhiều thread cùng yêu cầu một lớp, đường đi khóa mặc định của `ClassLoader.loadClass(String, boolean)`, vai trò của `getClassLoadingLock(String)`, sự khác biệt giữa loader thường và *parallel-capable loader*, cùng các trường hợp deadlock, race condition và lỗi `LinkageError` khi tự viết loader. Ví dụ biên dịch với Java 17+.

## 1. Bài toán: hai thread cùng load một tên lớp

Giả sử một chương trình có một custom loader `L`, và hai thread gần như đồng thời gọi:

```java
// Hai lời gọi trên cùng ClassLoader instance L:
L.loadClass("demo.Widget");
L.loadClass("demo.Widget");
```

Nếu đường nạp không được đồng bộ đúng, cả hai thread có thể:

1. Cùng kiểm tra rằng chưa có `demo.Widget`.
2. Cùng đọc `demo/Widget.class` từ disk/JAR.
3. Cùng định gọi `defineClass("demo.Widget", ...)` bằng **cùng defining loader**.
4. Một thread định nghĩa trước; thread kia cố định nghĩa lại cùng binary name và có thể gây **`LinkageError`: duplicate class definition**.

JVM xác định một lớp/interface thông thường bằng cặp **(binary name, defining loader)**. Không thể có hai định nghĩa khác nhau cùng binary name được đăng ký ở **cùng defining loader**, kể cả bytecode của hai lần đọc giống nhau.

Đồng thời, JVM cần cho phép nhiều thread truy cập một lớp đã nạp và nhận lại **cùng object `Class<?>`**. Đó là lý do đường `loadClass` chuẩn có bước kiểm tra class đã được ghi nhận, parent delegation, tìm bytes và đồng bộ theo **class-loading lock**.

**Ý chính:** Không phải mọi việc liên quan tới JVM đều bị khóa toàn cục; khóa mà Java `ClassLoader` dùng phụ thuộc loader có được đăng ký *parallel-capable* hay không.

## 2. Đường đi chuẩn của ClassLoader.loadClass

Hai API:

```java
public Class<?> loadClass(String name) throws ClassNotFoundException;

protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException;
```

Overload public gọi logic tương đương `loadClass(name, false)`. Trong **triển khai mặc định** của overload protected, Javadoc mô tả luồng xử lý như sau:

```text
loadClass("demo.Widget", resolve)
           |
           v
synchronized (getClassLoadingLock("demo.Widget"))
           |
           +--> findLoadedClass("demo.Widget")
           |        |
           |        '--> đã có -> tái sử dụng Class<?> tương ứng
           |
           +--> nếu chưa có, thử parent.loadClass("demo.Widget")
           |        |
           |        '--> parent tìm được -> dùng Class<?> do parent trả
           |
           +--> nếu parent không tìm được, gọi findClass("demo.Widget")
           |        |
           |        '--> subclass tìm bytes và defineClass(...)
           |
           +--> nếu resolve == true: resolveClass(result)
           |
           '--> return Class<?>
        (nhả class-loading lock)
```

Đây là trình tự khái quát theo Java SE 21 `ClassLoader.loadClass(String, boolean)`. Điều quan trọng nhất là khóa bao phủ **toàn bộ chu trình tìm class trong implementation mặc định**, không chỉ câu lệnh `defineClass`.

### Ý nghĩa từng bước

- `getClassLoadingLock(name)`: trả về object để đồng bộ hóa việc load lớp có tên `name`.
- `findLoadedClass(name)`: kiểm tra lớp đã được JVM ghi nhận là tải bởi loader theo semantics của API; nếu có thì không định nghĩa lại.
- **Parent delegation:** hỏi parent trước để giữ các lớp nền tảng và lớp chia sẻ có định danh nhất quán trong chính sách mặc định.
- `findClass(name)`: điểm mở rộng được khuyến nghị cho subclass; đọc class bytes từ nguồn của custom loader và gọi `defineClass`.
- `resolveClass(clazz)`: nếu requested, kích hoạt linking theo hợp đồng method. **Không đồng nghĩa tự chạy static initializer.**

Javadoc nhấn mạnh: **trừ khi bị override**, `loadClass(String, boolean)` đồng bộ trên kết quả của `getClassLoadingLock` trong suốt quá trình tải. Nếu subclass override toàn bộ `loadClass`, nó có trách nhiệm duy trì logic tìm lớp, synchronization, deadlock safety và Java type identity phù hợp.

## 3. Class-loading lock không luôn là chính ClassLoader

Method:

```java
protected Object getClassLoadingLock(String className);
```

Theo Java SE 21:

| Loại ClassLoader instance | Giá trị `getClassLoadingLock(name)` |
| --- | --- |
| **Không được đăng ký parallel-capable** | **Chính object loader (`this`)**, dùng chung cho mọi tên. |
| **Được đăng ký parallel-capable** | **Một khóa gắn với tên lớp cụ thể** cho loader ấy. |

Sơ đồ:

```text
Non-parallel loader L:
    getClassLoadingLock("demo.A") --> L
    getClassLoadingLock("demo.B") --> L
    getClassLoadingLock("demo.C") --> L
                                    cùng một monitor

Parallel-capable loader P:
    getClassLoadingLock("demo.A") --> lock[A]
    getClassLoadingLock("demo.B") --> lock[B]
    getClassLoadingLock("demo.A") --> lock[A]
                                    khác tên khác lock,
                                    cùng tên cùng lock
```

Với non-parallel loader, nhiều thread load **hai lớp khác tên** trên cùng loader vẫn phải chờ cùng monitor nếu dùng triển khai `loadClass` mặc định. Đơn giản nhưng có thể hạn chế throughput.

Với parallel-capable loader, các request **khác tên** có thể đi qua phần load của cùng loader đồng thời. Tuy nhiên, thread cần tải **cùng tên** vẫn đồng bộ trên cùng lock để không double-define.

**Cần hiểu đúng giới hạn:** parallel-capable là *khả năng cho phép* concurrency ở cấp khóa tên lớp, không phải bảo đảm mọi công việc load sẽ thực sự chạy song song với tốc độ tối đa. Disk I/O, parent loader, package metadata, filesystem, network, JVM linking và các khóa khác vẫn có thể gây contention.

### Quan sát getClassLoadingLock từ subclass

Vì `getClassLoadingLock` là `protected`, có thể tạo method wrapper phục vụ chẩn đoán trong subclass:

```java
class DiagnosticLoader extends ClassLoader {
    Object lockFor(String className) {
        return getClassLoadingLock(className);
    }
}
```

Đây là **class có thể biên dịch**, nhưng thiếu `main`. Đừng xuất public lock object vào API sản phẩm để các caller tùy ý khóa lâu; nó có thể làm các request tải lớp khác bị tắc hoặc tạo deadlock.

## 4. Parallel-capable ảnh hưởng gì tới khóa mặc định?
Một subclass `ClassLoader` **chưa đăng ký** dùng khóa chung `this`; loại **parallel-capable** dùng lock theo binary name và có thể tải các tên khác nhau đồng thời. Đăng ký qua `registerAsParallelCapable()` trong static initializer **của từng subclass**, trước khi có instance, và yêu cầu toàn bộ superclass loader cũng đã đăng ký. Kiểm tra `isRegisteredAsParallelCapable()` để tránh giả định sai.
Ví dụ `ParallelLoader` ngay dưới đây cho thấy đầy đủ: cùng tên giữ một khóa ổn định, tám threads trả cùng `Class<?>`, `findClass` chỉ cần định nghĩa một lần. Việc đăng ký **không tự làm an toàn** `HashMap`, I/O stream, package metadata hay code `loadClass` do bạn override. Bài riêng về `registerAsParallelCapable` phân tích sâu quy tắc superclass, caller và thời điểm.

## 5. Ví dụ chạy được trên Java 17+: quan sát lock và 8 thread nạp cùng một lớp

Thí nghiệm tạo một `SerialLoader` không đăng ký parallel-capable và một `ParallelLoader` có đăng ký. Cả hai đều kế thừa trực tiếp `ClassLoader`, nên không bị ảnh hưởng bởi implementation của application loader.

`ParallelLoader` override **`findClass`**, không override `loadClass`. Nhờ đó khóa chuẩn của Java SE vẫn bao phủ kiểm tra đã tải, parent delegation và định nghĩa mới.

### Cấu trúc nguồn

```text
src/
  demo/
    Widget.java
  app/
    LoadingLockDemo.java
plugin-classes/    (javac tạo)
app-classes/       (javac tạo)
```

**File `src/demo/Widget.java`:**

```java
package demo;

public class Widget {
    public int answer() {
        return 42;
    }
}
```

**File `src/app/LoadingLockDemo.java`:**

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadingLockDemo {
    static class SerialLoader extends ClassLoader {
        SerialLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        Object lockFor(String name) {
            return getClassLoadingLock(name);
        }
    }

    static final class ParallelLoader extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "Registration failed");
            }
        }

        private final Path root;
        final AtomicInteger findCalls = new AtomicInteger();

        ParallelLoader(Path root) {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root;
        }

        Object lockFor(String name) {
            return getClassLoadingLock(name);
        }

        @Override
        protected Class<?> findClass(String binaryName)
                throws ClassNotFoundException {
            findCalls.incrementAndGet();

            try {
                byte[] bytes = Files.readAllBytes(
                        root.resolve(
                                binaryName.replace('.', '/') + ".class"));
                return defineClass(
                        binaryName, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(binaryName, e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SerialLoader serial = new SerialLoader();
        ParallelLoader parallel =
                new ParallelLoader(Path.of("plugin-classes"));

        System.out.println("serial registered: "
                + serial.isRegisteredAsParallelCapable());
        System.out.println("serial same lock for A/B: "
                + (serial.lockFor("demo.A")
                        == serial.lockFor("demo.B")));
        System.out.println("parallel registered: "
                + parallel.isRegisteredAsParallelCapable());
        System.out.println("parallel same lock for A/B: "
                + (parallel.lockFor("demo.A")
                        == parallel.lockFor("demo.B")));
        System.out.println("parallel same lock for A: "
                + (parallel.lockFor("demo.A")
                        == parallel.lockFor("demo.A")));

        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Class<?>>> tasks = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                tasks.add(pool.submit(() -> {
                    start.await();
                    return parallel.loadClass("demo.Widget");
                }));
            }

            start.countDown();
            Class<?> first = tasks.get(0).get();

            boolean same = true;
            for (Future<Class<?>> task : tasks) {
                same &= task.get() == first;
            }

            System.out.println("requests: " + requests);
            System.out.println("one Class object: " + same);
            System.out.println("findClass calls: "
                    + parallel.findCalls.get());
            System.out.println("defining loader matches: "
                    + (first.getClassLoader() == parallel));
        } finally {
            pool.shutdownNow();
        }
    }
}
```

### Biên dịch và chạy

Từ thư mục gốc chứa `src`, chạy:

```bash
javac --release 17 -d plugin-classes src/demo/Widget.java
javac --release 17 -d app-classes src/app/LoadingLockDemo.java
java -cp app-classes LoadingLockDemo
```

**Stdout đã được biên dịch bằng `--release 17` và kiểm chứng trên OpenJDK 24:**

```text
serial registered: false
serial same lock for A/B: true
parallel registered: true
parallel same lock for A/B: false
parallel same lock for A: true
requests: 8
one Class object: true
findClass calls: 1
defining loader matches: true
```

### Giải thích từng dòng

1. `SerialLoader` không gọi `registerAsParallelCapable`: trạng thái `false`. Hai tên `demo.A`, `demo.B` cùng dùng monitor của instance `serial`, nên phép so sánh `==` là `true`.
2. `ParallelLoader` gọi đăng ký trong static initializer trước khi được tạo: trạng thái `true`. Hai tên khác nhau dùng hai lock khác nhau; gọi lại cùng tên trả cùng lock.
3. `plugin-classes` **không nằm trong `-cp app-classes`; parent platform loader không định nghĩa `demo.Widget` từ nơi đó. Vì vậy `ParallelLoader.findClass` thực sự đọc bytes và tạo class.
4. `CountDownLatch` làm tám task sẵn sàng rồi đồng thời được phép gọi `parallel.loadClass("demo.Widget")`. Trình lập lịch có thể chạy chúng ở những thời điểm khác nhau; ta **không dựa trên timing** để khẳng định mức song song.
5. Vì cùng loader + cùng binary name, khóa theo tên, `findLoadedClass` và `defineClass` phối hợp để kết quả mọi task là **chính một `Class<?>`**, còn `findClass` chỉ được gọi **một lần trong cấu hình này**.
6. `first.getClassLoader() == parallel` chứng minh custom loader là **defining loader** trong ví dụ. Nếu parent đã chứa `demo.Widget`, kết quả cần diễn giải khác.

**Quan trọng:** test này chứng minh behavior của khóa và việc nạp một tên an toàn. Nó **không** đo tốc độ hay chứng minh `findClass` của hai tên khác nhau luôn chạy song song; muốn đo cần benchmark được kiểm soát.

## 6. Tại sao hai luồng cùng tên không tự tạo hai Class<?>?

Bên trong `loadClass` mặc định, `synchronized (getClassLoadingLock(name))` bảo vệ chuỗi *check–delegate–find–define*.

Một lịch trình minh họa:

```text
Thread A                              Thread B
--------                              --------
lock("demo.Widget") acquired          waits for same lock
findLoadedClass -> null
parent lookup -> missing
findClass -> defineClass -> C
return C; release lock
                                      acquire same lock
                                      findLoadedClass -> C
                                      return C; release lock
```

Mấu chốt là **kiểm tra lại dưới khóa**. Chỉ kiểm tra `findLoadedClass` ở ngoài khóa, sau đó gọi `defineClass` không đồng bộ, là pattern time-of-check/time-of-use dễ phát sinh race.

Với loader parallel-capable, khóa theo tên bảo vệ từng class riêng. Hai tên `demo.Widget` và `demo.Helper` có khóa khác nhau; điều đó không miễn trừ nhu cầu đồng bộ **trạng thái chia sẻ** ở nơi khác:

- Cùng một archive/JAR reader với state mutable.
- Cache ánh xạ resource, metadata package hoặc verification result.
- `definePackage` cho hai class khác tên nhưng **cùng package**.
- Global bytecode transform pipeline, generator cache và class signers.
- Tham chiếu chéo giữa plugin loaders tạo delegation graph.

Nên phân biệt **class-loading lock** của base class với các mutex khác do bạn tự thiết kế. Một loader đã parallel-capable mà `findClass` không thread-safe vẫn có thể lỗi.

## 7. Override findClass, không vội override loadClass

Khuyến nghị của Javadoc: các subclass phù hợp với delegation model thường nên override `findClass` để cung cấp nguồn bytecode mới.

Mẫu tối giản:

```java
@Override
protected Class<?> findClass(String binaryName)
        throws ClassNotFoundException {
    byte[] bytes = obtainVerifiedClassBytes(binaryName);
    if (bytes == null) {
        throw new ClassNotFoundException(binaryName);
    }
    return defineClass(binaryName, bytes, 0, bytes.length);
}
```

Đây là **snippet** đặt trong `ClassLoader` subclass, với `obtainVerifiedClassBytes` là helper phụ thuộc repository/bộ đọc JAR của ứng dụng. Nó không tự biên dịch độc lập.

Ưu điểm:

- `loadClass` mặc định xử lý `getClassLoadingLock` và `findLoadedClass`.
- Parent delegation đã có theo mô hình Java chuẩn.
- `resolveClass` được gọi đúng khi overload protected được dùng với `resolve=true`.
- Subclass chỉ tập trung đọc bytes đúng tên và gọi `defineClass`.

Nếu override `loadClass` để đổi sang child-first, bạn phải tự kiểm tra `findLoadedClass` và tự khóa đồng bộ đúng trước khi định nghĩa. Một ví dụ pseudocode cấu trúc an toàn:

```java
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            // Cài đặt chính sách delegation/fallback hợp lệ tại đây:
            // bảo đảm không define trùng tên và bảo vệ namespaces.
            c = super.loadClass(name, false);
        }
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}
```

Đây là **snippet minh họa**, không phải template child-first hoàn chỉnh. Trên thực tế, với `super.loadClass`, parent-first vẫn áp dụng; muốn child-first phải thiết kế đầy đủ thứ tự nguồn riêng, xử lý protected namespace và các lỗi liên kết. Không copy rồi tưởng đã thay đổi strategy.

Một nguy cơ khác: nếu subclass **cố tình override `loadClass` không đồng bộ**, hợp đồng khóa mặc định của superclass **không tự đảm bảo** độ an toàn cho code override đó.

## 8. Khi ủy quyền chéo có thể gây deadlock
Nếu thread T1 đang giữ monitor loader A rồi đợi loader B, còn T2 đang giữ B rồi đợi A, tạo vòng chờ và deadlock. Khóa theo từng tên class ở loader parallel-capable **giảm những vòng chờ do khóa toàn loader**, nhưng không ngăn mọi dependency cycle. Bài **Deadlock khi nhiều loader ủy quyền chéo** có ví dụ hai loader, thread dump và cách loại bỏ vòng chờ; ở đây cần nhớ không tự giữ `synchronized(this)` quanh các lời gọi sang loader khác.

## 9. Class loading lock không phải khóa khởi tạo lớp

Các giai đoạn cần phân biệt:

| Giai đoạn | Điều gì diễn ra | Khóa nào liên quan? |
| --- | --- | --- |
| **Loading** | Tìm/định nghĩa `Class<?>` từ binary name/bytes. | `ClassLoader.loadClass` mặc định dùng `getClassLoadingLock`. |
| **Linking** | Verification, preparation, resolution theo quy tắc JVM. | JVM có cơ chế riêng; `resolveClass` không phải gọi static initializer. |
| **Initialization** | Chạy `<clinit>` gồm static field initializers/static blocks. | JVM dùng **initialization lock/protocol** cho từng class theo JVMS `5.5. |

Không suy luận rằng hai thread gọi `Class.forName("demo.Widget")` và static initializer chỉ chạy một lần **chỉ do** `getClassLoadingLock`. JVM có cơ chế đồng bộ khởi tạo lớp **riêng**. Đặc biệt, `Class.forName(name, false, loader)` yêu cầu load/link theo API nhưng **không yêu cầu initialization**; `Class.forName(name)` thông thường có thể kích hoạt initialization.

Vì thế:

- Một class có thể đã load nhưng chưa initialized.
- Một class có thể đang initializing trong thread khác; JVM phải phối hợp theo quy tắc `<clinit>`.
- Deadlock liên quan static initializers không được chữa chỉ bằng đăng ký parallel-capable loader.
- Việc monitor theo class name không phải monitor dùng cho toàn bộ method `static synchronized` của class.

Đây là lý do khi debug hang cần biết thread đang kẹt tại **loader delegation/defineClass** hay **class initialization**.

## 10. Race condition ở custom loaders: những lỗi cần phòng

| Thiết kế sai | Điều có thể xảy ra | Cách làm đúng |
| --- | --- | --- |
| Bỏ `synchronized(getClassLoadingLock(name))` khi override `loadClass` | Hai thread cùng `defineClass` một tên → `LinkageError`. | Dùng implement mặc định hoặc khóa theo tên chính xác. |
| Chỉ kiểm tra `findLoadedClass` ở ngoài khóa | Race giữa check và define. | Check và define dưới cùng lock cho tên ấy. |
| Subclass đăng ký parallel-capable nhưng share `HashMap` mutable không đồng bộ | Lỗi race/corruption cache. | Dùng concurrent collections và chính sách visibility phù hợp. |
| Hai tên class khác nhau cùng gọi `definePackage` | Package có thể bị define hai lần → `IllegalArgumentException`. | Đồng bộ state theo package; xác minh metadata/sealing. |
| Dùng một `InputStream`/`JarFile` có thao tác mutable từ nhiều threads mà không có contract thread-safe | Đọc nhầm/cắt bytes hoặc lỗi tài nguyên. | Tách stream, đồng bộ theo nguồn hoặc sử dụng API quản lý concurrency. |
| Gọi sang loader khác trong khi giữ nhiều lock không có thứ tự | Circular wait/deadlock. | Thiết kế DAG delegation hoặc thứ tự lấy khóa rõ ràng. |
| `registerAsParallelCapable` ở subclass nhưng superclass chưa đăng ký | Đăng ký trả false. | Đăng ký đúng từ từng lớp hierarchy. |
| Gọi `registerAsParallelCapable` quá muộn | Đăng ký thất bại vì đã tạo instance. | Thực hiện ở class initialization trước constructor. |
| Tự khóa vào một `new Object()` mỗi lần gọi load | Không hề phối hợp giữa các thread. | Dùng ổ khóa ổn định theo loader/tên. |
| Dùng tên resource path `demo/Widget.class` thay binary name `demo.Widget` | Sai lookup/định nghĩa. | Phân biệt binary name và path trong archive. |

### Không tự gán mọi LinkageError cho double-define

`LinkageError` có nhiều nguyên nhân: loading constraint violation, incompatibility giữa phiên bản thư viện, `NoSuchMethodError`, `ClassFormatError` (subclass của `LinkageError`), duplicate class definition, v.v. Đọc exception message/stack trace trước khi kết luận do race.

Chạy nhiều thread mà **không thấy exception** cũng chưa chứng minh loader thread-safe; bug concurrency có thể phụ thuộc timing, nguồn I/O và thứ tự scheduler.

## 11. Mẫu chuẩn cho loader có nhiều nguồn bytes

Một custom loader phục vụ plugin lớn thường cần những quy tắc sau:

**A. Ownership rõ ràng.** Một loader instance là một không gian tên class và tài nguyên. Đừng chia sẻ trạng thái mutable trên nhiều loader instance nếu không có lý do.

**B. Chỉ override điểm mở rộng cần thiết.** Khi policy vẫn parent-first, ưu tiên `findClass`. Tránh override `loadClass` chỉ để đọc từ database/directory.

**C. Đăng ký parallel-capable có chủ đích.** Nếu throughput hoặc mô hình delegation cần load khác tên đồng thời, dùng `registerAsParallelCapable` **kèm review toàn bộ thread-safety**. Với loader đơn giản, non-parallel có thể dễ suy luận và đủ nhanh.

**D. Lock ngoài lớp là riêng biệt.** Package metadata, cache, JAR handle và class generator cần đồng bộ đúng theo chia sẻ dữ liệu. Không phụ thuộc khóa loader bao trùm toàn bộ khi đã parallel-capable.

**E. Kiểm soát thứ tự parent/plugin.** Một lớp tên trùng ở parent có thể được parent trả về; `Class<?>` trả ra có defining loader là parent, không phải loader đang gọi `loadClass`.

**F. Lỗi phải được phân loại.** `ClassNotFoundException` khi không có class; `IOException` đọc bytes được bọc với cause; `LinkageError` duplicate/incompatibility; `VerifyError` verifier; `ExceptionInInitializerError` ở initialization là giai đoạn khác.

**G. Kiểm tra cả load trùng tên và khác tên.** Test cùng tên để chứng minh không double-define, test khác tên để xem lock contention/các cache, test parent delegation và nhiều loader có dependency cross-reference.

**H. Đừng tắt an toàn để tối ưu sớm.** Mục tiêu là correctness trước; chỉ sau đó đo contention bằng thread dump/JFR/profiling rồi mới tối ưu.

## 12. Debug hang khi ClassLoader tải đồng thời

Khi ứng dụng đứng yên lúc startup hoặc khi kích hoạt plugin:

1. **Lấy thread dump** bằng công cụ JDK phù hợp, ví dụ `jcmd <pid> Thread.dump_to_file -format=text dump.txt` (cú pháp/capability có thể khác phiên bản), hoặc `jstack <pid>` trên JVM có hỗ trợ.
2. Nhìn các thread ở `BLOCKED`/`WAITING` trong `ClassLoader.loadClass`, `getClassLoadingLock`, `findClass`, I/O và `<clinit>`. Không chỉ tìm chuỗi `ClassNotFoundException`.
3. Nếu thread A giữ loader A lock và chờ loader B, trong khi B đợi A, vẽ delegation graph và monitor owner/waiters.
4. Kiểm tra `loader.isRegisteredAsParallelCapable()` và hierarchy thực tế; không suy từ tên class loader là “parallel”.
5. Nếu hai thread cùng tên lớp, xem có override `loadClass` bỏ khóa không; nếu có double-define, tìm stack tại `defineClass` và đường `findLoadedClass`.
6. Nếu khác tên nhưng cùng package, kiểm tra race trong `definePackage`, sealing và shared caches.
7. Nếu static initializer đang chờ, phân biệt **JVM initialization protocol** với monitor của loader; tránh chữa sai bằng `registerAsParallelCapable`.
8. Test tái hiện bằng chương trình tối giản, dùng `CountDownLatch`/`CyclicBarrier` để kích hoạt gần nhau, so sánh `Class<?>` bằng `==` và đếm `findClass` calls.
9. Giữ biểu đồ lock graph đơn giản và loại bỏ cycle; tuyệt đối không ép `Thread.stop()` để “giải” deadlock trong production.

Các kết quả của `ThreadMXBean.findDeadlockedThreads` có thể hỗ trợ chẩn đoán một số deadlock liên quan monitor/synchronizer, nhưng không thay thế phân tích I/O và dependency graph; thread không hoàn thành có thể đang chờ mạng/disk thay vì deadlock.

## 13. Các câu hỏi tự kiểm tra

1. **Với loader không parallel-capable, `getClassLoadingLock("A") == getClassLoadingLock("B")` là gì?**  
   `true`: method trả chính instance loader.
2. **Với loader đã parallel-capable, hai tên khác nhau dùng chung lock?**  
   Không theo implementation requirements; khóa được gắn với tên lớp. Cùng tên trên cùng loader nhận cùng lock.
3. **`ClassLoader` gốc đã parallel-capable, vậy subclass trực tiếp có tự parallel-capable?**  
   Không. Subclass phải đăng ký nếu muốn hỗ trợ.
4. **Đăng ký parallel-capable lúc nào?**  
   Trong quá trình initialization của class loader subclass, trước khi có instance, và mọi superclass ClassLoader cần đã đăng ký.
5. **Tám thread gọi `loader.loadClass("demo.Widget")` đồng thời, có hợp lệ tạo tám `Class<?>` ở cùng defining loader?**  
   Không; phải tái sử dụng class identity. Trong demo cả tám trả một object và `findClass` chỉ chạy một lần.
6. **Loader parallel-capable có tự làm mọi `HashMap` shared thread-safe?**  
   Không; phải đồng bộ state application riêng.
7. **Nên override `loadClass` hay `findClass` khi chỉ muốn nguồn class bytes từ thư mục riêng?**  
   Thường `findClass`; giữ logic khóa và parent delegation của `loadClass` mặc định.
8. **`synchronized(getClassLoadingLock(name))` có bảo đảm static initializer chỉ chạy một lần?**  
   Không phải trực tiếp; JVM có initialization lock/protocol riêng cho class.
9. **Tại sao non-hierarchical delegation dễ deadlock với non-parallel loader?**  
   Mỗi thread có thể giữ monitor toàn loader rồi chờ monitor loader khác theo chu trình.
10. **Đăng ký parallel-capable có loại bỏ mọi deadlock?**  
    Không; lock theo tên khác nhau vẫn có thể tạo circular wait.
11. **`resolveClass` có chạy constructor hoặc static block không?**  
    Không theo nghĩa initialization; nó phục vụ linking.
12. **Vì sao không nên tự khóa `synchronized(this)` trong mọi loader parallel-capable?**  
    Nó có thể đưa coarse lock quay lại, phá lợi ích nạp khác tên đồng thời và tăng rủi ro deadlock.

## 14. Tài liệu chuẩn

- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `getClassLoadingLock`, `findLoadedClass`, `findClass`, `registerAsParallelCapable` và `isRegisteredAsParallelCapable`.
- [Java SE 21 — Concurrent programming / java.util.concurrent](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html): `CountDownLatch`, `ExecutorService`, `Future`, synchronization.
- [JVMS §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): defining vs initiating loader và runtime type identity.
- [JVMS §5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.5): class initialization synchronization/protocol riêng.
- [JLS Chapter 12 — Execution](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html): loading, linking và initialization.
- [Java SE 21 — ThreadMXBean](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html): API hỗ trợ chẩn đoán monitor deadlocks.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-class-loading-002">getClassLoadingLock và khóa theo tên lớp</a>

<details>
<summary>Xem chi tiết</summary>

# getClassLoadingLock và khóa theo tên lớp trong Java ClassLoader

## 1. Bài toán: nhiều thread cùng yêu cầu nạp lớp

Một ứng dụng Java có thể có nhiều thread đồng thời gọi:

~~~java
Class<?> a = loader.loadClass("demo.plugin.Alpha");
~~~

Với một custom `ClassLoader`, thao tác tìm và định nghĩa một lớp không chỉ là đọc file `.class`. JVM phải duy trì **định danh kiểu**, **loading constraints**, mối quan hệ với **parent loader** và trạng thái một lớp đã được nạp hay chưa. Nếu hai thread cùng thấy “chưa có `demo.plugin.Alpha`” rồi đều gọi `defineClass`, một thread có thể thành công, thread còn lại gặp `LinkageError` vì cố định nghĩa trùng tên trong cùng loader.

Tuy nhiên, nếu một thread đang nạp `demo.plugin.Alpha`, tại sao thread khác muốn nạp `demo.plugin.Beta` cũng phải đợi? Java hỗ trợ hai cách khóa:

- **ClassLoader không đăng ký parallel-capable:** thao tác `loadClass` mặc định đồng bộ trên **chính đối tượng ClassLoader**, tức một khóa chung cho mọi tên lớp trong loader đó.
- **ClassLoader đã đăng ký parallel-capable:** `getClassLoadingLock(name)` mặc định trả **một đối tượng khóa dành riêng cho tên class đó**. Hai thread hỏi **cùng tên** sẽ lấy cùng khóa; các tên **khác nhau** có khóa khác nhau.

Hai cơ chế đều có nhiệm vụ bảo vệ tính nhất quán khi nạp class. Khóa mịn hơn theo tên class giúp nhiều tên khác nhau **có thể** được xử lý song song, không có nghĩa đảm bảo nạp nhanh hơn trong mọi chương trình.

**Điểm nhớ ngắn gọn:**

~~~text
Non-parallel-capable loader L:
  getClassLoadingLock("a.A") == L
  getClassLoadingLock("b.B") == L
  => các tên lớp cùng tranh một monitor

Parallel-capable loader L:
  getClassLoadingLock("a.A") == lock_A
  getClassLoadingLock("a.A") == lock_A
  getClassLoadingLock("b.B") == lock_B
  lock_A != lock_B
  => cùng tên vẫn tuần tự, khác tên có thể tiến hành đồng thời
~~~

Không có “một khóa toàn JVM cho tên class”. Các khóa là thành phần của **mỗi ClassLoader instance**. Hai loader khác nhau có thể định nghĩa hai lớp cùng binary name nhưng trở thành hai runtime types khác nhau.

## 2. `ClassLoader.getClassLoadingLock(String className)` là gì?

API được bổ sung từ **Java 7**:

~~~java
protected Object getClassLoadingLock(String className)
~~~

Đây là **protected instance method** của `java.lang.ClassLoader`. Mục đích của nó là trả một đối tượng làm **monitor lock** cho hoạt động nạp class có tên nhị phân `className`.

Theo [Java SE 21 — ClassLoader#getClassLoadingLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#getClassLoadingLock(java.lang.String)):

1. Nếu **instance ClassLoader đã đăng ký parallel-capable**, phương thức trả đối tượng chuyên biệt gắn với tên class chỉ định.
2. Nếu **không parallel-capable**, phương thức trả **`this`**, tức chính ClassLoader instance.
3. Nếu loader parallel-capable và `className == null`, phương thức ném `NullPointerException` theo hợp đồng.
4. Vì đây là protected method, code bên ngoài không thể tùy ý gọi qua một `ClassLoader` bất kỳ. Subclass có thể gọi `getClassLoadingLock` để phục vụ logic nạp lớp của mình.

API trả về đối tượng khóa, **không thực hiện việc nạp lớp** và **không tự động khóa thread ngay khi được gọi**. Chỉ có `synchronized(lock)` hoặc đoạn mã tương đương sử dụng monitor đó mới tạo vùng đồng bộ.

### 2.1. Khóa theo tên được sử dụng khi nào?

Triển khai mặc định `ClassLoader.loadClass(String, boolean)` đồng bộ trên:

~~~java
synchronized (getClassLoadingLock(name)) {
    // kiểm tra class đã nạp;
    // ủy quyền cho parent;
    // nếu cần thì gọi findClass;
    // resolve nếu được yêu cầu;
}
~~~

Đây là **mã giải thích phạm vi khóa**, không phải toàn bộ mã nguồn thực thi của JDK.

Vì JVM có thể được nhiều thread yêu cầu load cùng tên, thao tác **kiểm tra `findLoadedClass` + quyết định `findClass`/`defineClass`** cần bảo vệ nhất quán. Nếu tách phần kiểm tra và phần define bằng hai khóa không liên quan, sẽ xảy ra tình huống race.

### 2.2. Vì sao không chỉ dùng `synchronized(this)`?

`synchronized(this)` là khóa trên toàn ClassLoader instance. Nó phù hợp với loader **không parallel-capable**, nhưng có thể gây **tranh chấp không cần thiết** nếu một loader cần nạp nhiều tên class độc lập.

Ví dụ:

~~~text
Thread-1 muốn plugin.First:
   giữ monitor Loader L
   đọc file/classpath chậm trong khi giữ L

Thread-2 muốn plugin.Second:
   phải đợi monitor L, dù không cùng class name

Nếu L parallel-capable và dùng lock riêng:
   Thread-1 giữ lock_First
   Thread-2 giữ lock_Second
   => có thể cùng tiến hành các bước tìm/nạp tương ứng
~~~

Lợi ích chỉ là **khả năng tiến hành đồng thời**. Chúng vẫn có thể phải chờ nhau khi cùng phụ thuộc parent loader không parallel-capable, dùng cache/JAR/resource lock chung, gặp loading constraints hoặc thực hiện logic đồng bộ khác.

## 3. Loader nào được dùng khóa riêng?
Một `ClassLoader` subclass chỉ trở thành **parallel-capable** khi đăng ký thành công bằng `protected static registerAsParallelCapable()`, thường gọi trong static initializer của chính subclass trước khi tạo instance. Mọi superclass loader trong chuỗi (trừ `Object`) cũng phải đã đăng ký. Bản thân `ClassLoader` gốc đã đăng ký nhưng trạng thái này **không tự truyền cho subclass**.
```java
static {
    if (!registerAsParallelCapable()) {
        throw new ExceptionInInitializerError("Registration failed");
    }
}
```
Đặt block trong custom `ClassLoader`; kiểm chứng ở runtime bằng `loader.isRegisteredAsParallelCapable()`. Nếu một superclass tự viết chưa đăng ký, lời gọi ở child trả `false`. Bài tiếp theo về `registerAsParallelCapable` đào sâu những điều kiện này; trọng tâm ở đây là **`getClassLoadingLock` trả lock nào** và cách phối hợp cùng tên.

## 4. Vì sao phải kiểm tra `findLoadedClass` dưới đúng khóa?

Algorithm chuẩn của `loadClass` có thể hình dung:

~~~text
loadClass(name, resolve)
  synchronized (getClassLoadingLock(name)):
      c = findLoadedClass(name)

      if c == null:
          thử parent.loadClass(name)

      if vẫn null:
          c = findClass(name)

      if resolve:
          resolveClass(c)

      return c
~~~

`findLoadedClass(name)` trả `Class<?>` nếu class cùng binary name đã được JVM ghi nhận cho **loader này như initiating loader**, không nhất thiết chỉ những class do loader này trực tiếp `defineClass`. Khi loader đã tìm thấy class qua parent, nó cũng cần tránh cố định nghĩa tên ấy một lần nữa.

### 4.1. Khi hai threads hỏi cùng một class

~~~text
Thread A: loadClass("pkg.Service")
  lock_Service acquired
  findLoadedClass == null
  parent không thấy
  findClass -> defineClass -> Class C
  release lock_Service

Thread B: loadClass("pkg.Service")
  chờ lock_Service
  sau đó findLoadedClass trả Class C
  không gọi defineClass lần nữa
  trả cùng Class C
~~~

Kết quả đúng là **hai threads nhận cùng `Class<?>`** từ cùng loader; số lần thực sự gọi `findClass` để định nghĩa class đó thường là **một lần** trong cấu hình thí nghiệm phù hợp.

### 4.2. Nếu hai threads hỏi hai tên khác nhau

~~~text
Thread A: lock_Alpha => load Alpha
Thread B: lock_Beta  => load Beta
~~~

Với loader parallel-capable, **khác tên, khác khóa** cho phép các thao tác diễn ra đồng thời. Nếu cả hai cùng cần parent, đọc chung cache hoặc có circular dependencies, việc chờ vẫn có thể xảy ra. Không khẳng định có phép thử thời gian nào sẽ luôn cho speedup 2×.

### 4.3. `getClassLoadingLock` không thay thế kiểm tra cache

Nếu custom loader viết:

~~~java
synchronized (getClassLoadingLock(name)) {
    byte[] bytes = readClassBytes(name);
    return defineClass(name, bytes, 0, bytes.length);
}
~~~

thì đoạn mã trên **chưa đúng**: khi lần gọi thứ hai đã vào khóa, nó vẫn luôn `defineClass` lần nữa nếu không kiểm tra class đã nạp. Đoạn trên là **mã sai có chủ đích**, `readClassBytes` chỉ là tên helper giả, không phải API có sẵn.

Cách đúng là sử dụng `loadClass` mặc định — hoặc, khi override thật sự cần thiết, đảm bảo nguyên tắc **kiểm tra `findLoadedClass` trong cùng vùng khóa**, xử lý delegation, gọi `findClass` chỉ khi cần, rồi resolve phù hợp.

## 5. Thực hành Java 17+: kiểm tra khóa chung và khóa theo tên

Thí nghiệm sau không dùng thư viện bên ngoài, chỉ cần JDK 17+. Chúng ta tạo:

1. `SerialLoader` **không đăng ký parallel-capable** để cho thấy mọi tên class dùng chính loader object làm lock.
2. `ParallelLoader` **đăng ký parallel-capable** để quan sát khóa ổn định theo từng tên class.
3. Tám tasks cùng gọi `ParallelLoader.loadClass("demo.plugin.Alpha")`, chứng minh chúng nhận **cùng `Class<?>`** và custom `findClass` được gọi một lần.

Phần kiểm chứng không dựa vào trình tự scheduling của thread, chỉ so sánh danh tính đối tượng và đếm số lần `findClass` bằng `AtomicInteger`.

### 5.1. Cây thư mục

~~~text
demo/
├── src/
│   ├── app/
│   │   └── LockDemo.java
│   └── plugin/
│       └── demo/
│           └── plugin/
│               └── Alpha.java
└── build/
    ├── app/
    └── plugin/
~~~

**Bắt buộc:** khi chạy, `-cp` chỉ có `build/app`, không có `build/plugin`. Nếu vô tình thêm `build/plugin` vào system classpath, parent có thể tìm `Alpha` trước, khiến `ParallelLoader.findClass` không được gọi và phép đo mất ý nghĩa.

### 5.2. Tệp `src/plugin/demo/plugin/Alpha.java`

~~~java
package demo.plugin;

public class Alpha {
    public String name() {
        return "alpha";
    }
}
~~~

Đây là class mà `ParallelLoader` sẽ nạp bằng cách đọc file bytes từ `build/plugin`.

### 5.3. Tệp `src/app/LockDemo.java`

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LockDemo {
    static final String NAME = "demo.plugin.Alpha";

    static final class SerialLoader extends ClassLoader {
        Object lock(String name) {
            return getClassLoadingLock(name);
        }
    }

    static final class ParallelLoader extends ClassLoader {
        static final boolean REGISTERED = registerAsParallelCapable();
        final Path root;
        final AtomicInteger findCalls = new AtomicInteger();

        ParallelLoader(Path root) {
            super(LockDemo.class.getClassLoader());
            this.root = root;
        }

        Object lock(String name) {
            return getClassLoadingLock(name);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            findCalls.incrementAndGet();
            try {
                byte[] bytes = Files.readAllBytes(
                        root.resolve(name.replace('.', '/') + ".class"));
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SerialLoader serial = new SerialLoader();
        ParallelLoader parallel = new ParallelLoader(Path.of("build/plugin"));

        System.out.println("serialSameLock="
                + (serial.lock(NAME) == serial.lock("demo.plugin.Beta")));
        System.out.println("serialLockIsLoader="
                + (serial.lock(NAME) == serial));
        System.out.println("parallelRegistered="
                + (ParallelLoader.REGISTERED
                        && parallel.isRegisteredAsParallelCapable()));
        System.out.println("parallelSameNameSameLock="
                + (parallel.lock(NAME) == parallel.lock(NAME)));
        System.out.println("parallelDifferentNamesDifferentLock="
                + (parallel.lock(NAME) != parallel.lock("demo.plugin.Beta")));
        System.out.println("parallelLockNotLoader="
                + (parallel.lock(NAME) != parallel));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Class<?>>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return parallel.loadClass(NAME);
                }));
            }
            start.countDown();

            Class<?> first = futures.get(0).get();
            boolean allSame = true;
            for (Future<Class<?>> f : futures) {
                allSame &= (f.get() == first);
            }
            System.out.println("allThreadsSameClass=" + allSame);
            System.out.println("findClassCalls=" + parallel.findCalls.get());
            System.out.println("loaderOwnsClass="
                    + (first.getClassLoader() == parallel));
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Giải thích các phần quan trọng:

- `SerialLoader` không đăng ký parallel capability nên `getClassLoadingLock` trả lại **`this`** cho mọi tên, kể cả khi class chưa được load.
- `ParallelLoader.REGISTERED` được tính khi class được khởi tạo, **trước khi constructor của instance được gọi**. Vì superclass `ClassLoader` đã parallel-capable, registration thành công.
- `lock(...)` chỉ là method package-private cung cấp quyền đọc protected method cho bài thử; **không tự nạp một class**.
- `ParallelLoader.findClass` chỉ xử lý đúng `NAME`, còn `loadClass` vẫn dùng triển khai mặc định của JDK với `findLoadedClass`, delegation và khóa theo tên.
- `AtomicInteger` đếm số lần custom `findClass` được gọi cho `Alpha`; không phải số lần API `loadClass` được gọi.
- `CountDownLatch start` cho tám tasks cùng bắt đầu tìm class sau khi đã được gửi vào thread pool. Do JDK bảo vệ đường `loadClass`, chỉ một task cần tự define class `Alpha`; các task còn lại nhận `Class<?>` đã có.
- `Future.get()` đợi kết quả và làm lộ exceptions thay vì in không theo thứ tự. `finally` đảm bảo executor được shutdown.

### 5.4. Lệnh biên dịch/chạy

Trong thư mục `demo` trên Linux/macOS:

~~~sh
mkdir -p build/plugin build/app
javac --release 17 -d build/plugin src/plugin/demo/plugin/Alpha.java
javac --release 17 -d build/app src/app/LockDemo.java
java -cp build/app LockDemo
~~~

Trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force build/plugin, build/app | Out-Null
javac --release 17 -d build/plugin src/plugin/demo/plugin/Alpha.java
javac --release 17 -d build/app src/app/LockDemo.java
java -cp build/app LockDemo
~~~

**Stdout chính xác đã kiểm chứng với JDK và mức tương thích Java 17:**

~~~text
serialSameLock=true
serialLockIsLoader=true
parallelRegistered=true
parallelSameNameSameLock=true
parallelDifferentNamesDifferentLock=true
parallelLockNotLoader=true
allThreadsSameClass=true
findClassCalls=1
loaderOwnsClass=true
~~~

### 5.5. Tại sao mỗi dòng quan trọng?

| Dòng | Bằng chứng |
|---|---|
| `serialSameLock=true` | Loader chưa đăng ký dùng **một khóa chung** cho hai binary names khác nhau |
| `serialLockIsLoader=true` | Khóa chung đó chính là **đối tượng loader** |
| `parallelRegistered=true` | Loại subclass `ParallelLoader` đã đăng ký parallel-capable thành công |
| `parallelSameNameSameLock=true` | Trong **cùng loader instance**, hỏi cùng tên hai lần nhận cùng lock object |
| `parallelDifferentNamesDifferentLock=true` | Hai tên `Alpha` và `Beta` có hai khóa khác nhau |
| `parallelLockNotLoader=true` | Khóa theo tên không phải toàn bộ instance loader |
| `allThreadsSameClass=true` | Tám tasks đều nhận cùng một định nghĩa runtime `Alpha` |
| `findClassCalls=1` | Chỉ một task đi tới bước đọc bytes/`defineClass` qua custom `findClass` |
| `loaderOwnsClass=true` | Class được định nghĩa bởi `ParallelLoader`, không phải parent/system loader |

**Ý nghĩa kỹ thuật:** cùng tên được bảo vệ để không tạo class trùng, trong khi việc lấy khóa cho tên khác nhau không buộc mọi thread tranh một monitor. Ví dụ không cố đo tốc độ, nên kết quả không phụ thuộc vào máy chạy nhiều hay ít core.

### 5.6. Các thí nghiệm mở rộng

**Thử 1 — Không đăng ký:** bỏ `REGISTERED = registerAsParallelCapable()`, rồi chạy lại trên một JVM mới. `parallel.isRegisteredAsParallelCapable()` sẽ là `false`; các dòng khóa theo tên dự kiến thay đổi: nhiều tên dùng cùng đối tượng loader. Khi không đổi thêm code, việc `parallelRegistered` in `false` là đúng.

**Thử 2 — Hai loader parallel khác nhau:** tạo `ParallelLoader second = new ParallelLoader(root)`, so `parallel.lock(NAME)` với `second.lock(NAME)`. Đây là hai instance nên khóa theo tên **không phải cùng monitor dùng chung toàn JVM**.

**Thử 3 — Tải thêm class Beta:** tạo và biên dịch `demo.plugin.Beta`, điều chỉnh `findClass` hỗ trợ cả Alpha/Beta. Cho hai tasks nạp hai tên khác nhau, chúng có thể cùng qua những khóa riêng; **không lấy thứ tự in log hoặc thời gian chạy để chứng minh chắc chắn parallelism**.

**Thử 4 — Sai cách override:** tự override `loadClass` mà quên `findLoadedClass` hoặc quên khóa, sau đó bắn nhiều requests cùng tên. Bạn có thể gặp `LinkageError` hoặc lỗi không ổn định. Đây là thử nghiệm nên đặt trong sandbox, không làm trên loader production.

## 6. Ghi đè `loadClass`: khi nào cần và phải giữ điều gì?

Thông thường, **chỉ override `findClass(String)`** để cung cấp chiến lược đọc bytes riêng. Khi đó bạn tận dụng được implementation mặc định của `loadClass`: khóa, cache theo JVM, ủy quyền parent và `resolveClass`.

Nếu thật sự cần chiến lược custom như **child-first cho namespace plugin**, có thể phải override `loadClass(String, boolean)`. Lúc ấy, bạn chịu trách nhiệm bảo đảm:

1. Đồng bộ bằng một lock theo **cùng tên class**, ví dụ `synchronized(getClassLoadingLock(name))`.
2. Gọi `findLoadedClass(name)` **bên trong** vùng khóa trước khi cố định nghĩa.
3. Áp dụng đúng delegation policy của ứng dụng (parent-first, child-first có ngoại lệ).
4. Gọi `findClass`/`defineClass` chỉ khi chưa có class phù hợp.
5. Nếu `resolve` đúng, gọi `resolveClass` đúng chỗ.
6. Tránh gọi lại `loadClass` trong những đường khiến hai tên phụ thuộc vòng tròn và khóa bị giữ theo thứ tự đối nghịch.
7. Tuân thủ runtime package/security constraints và loading constraints giữa loaders.

Giả mã minh họa ý tưởng **parent-first** (không phải một custom loader runnable hoàn chỉnh):

~~~java
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);

        if (c == null) {
            try {
                c = getParent().loadClass(name);
            } catch (ClassNotFoundException ex) {
                c = findClass(name);
            }
        }

        if (resolve) {
            resolveClass(c);
        }
        return c;
    }
}
~~~

**Cảnh báo quan trọng:** đoạn trên bỏ qua xử lý hợp đồng parent `null` (bootstrap fallback) và các yêu cầu khác của loader thực tế, nên chỉ là **pseudocode cấu trúc**, **không copy vào production**. Đúng nhất nếu không có yêu cầu đặc biệt là **không override `loadClass`**.

### 6.1. Cùng tên, khác loader có cùng khóa không?

Không. `getClassLoadingLock("pkg.A")` trên `loader1` và `loader2` là hai kết quả gắn với **hai instance** khác nhau. Vì thế, khóa theo tên **không** giải quyết tất cả bài toán giao tiếp giữa các class loaders:

~~~text
Loader A -> class pkg.ServiceA
Loader B -> class pkg.ServiceB

Nếu A giữ lock cho ServiceA rồi chờ B nạp ServiceB,
và B giữ lock cho ServiceB rồi chờ A nạp ServiceA,
có thể xảy ra circular wait/deadlock tùy delegation/call graph.
~~~

Parallel-capable registration **giảm loại deadlock do đồng bộ toàn loader**, đặc biệt trong mô hình delegation không thuần phân cấp, nhưng **không bảo đảm mọi kiểu deadlock đều biến mất**.

## 7. Khóa toàn loader và khóa theo tên: đánh đổi

| Thuộc tính | Loader không parallel-capable | Loader parallel-capable |
|---|---|---|
| `getClassLoadingLock("A")` | Trả **`this`** | Trả đối tượng gắn với tên `A` |
| `getClassLoadingLock("B")` | Cũng **`this`** | Trả đối tượng gắn với tên `B` |
| Hai thread cùng nạp **A** | Tuần tự khi đi qua `loadClass` mặc định | Tuần tự trên cùng `lock_A` |
| Hai thread nạp **A** và **B** | Tranh monitor chung của loader | Có thể xử lý đồng thời |
| Yêu cầu đăng ký | Không | `registerAsParallelCapable` từ subclass trước khi tạo instance |
| Bảo đảm không có deadlock | **Không** | **Cũng không** |
| An toàn nếu override `loadClass` tùy tiện | **Không** | **Không** |
| Khuyến nghị thông thường | Dùng mặc định, override `findClass` nếu cần | Đăng ký đúng rồi vẫn ưu tiên override `findClass` |

Một framework có thể dùng parallel-capable để hỗ trợ plugin/resource loading với nhiều threads. Tuy nhiên, locking granular không biến class loading thành một hoạt động tùy tiện: định danh kiểu theo binary name + defining loader vẫn phải được duy trì.

## 8. Lỗi và ngộ nhận thường gặp

### 8.1. “`getClassLoadingLock(name)` tự động khóa thread”

Sai. Method **trả Object** dùng làm monitor. `loadClass` mặc định dùng `synchronized(getClassLoadingLock(name))`; còn code riêng của bạn phải thực sự đồng bộ trên lock khi tự xử lý quá trình nạp.

### 8.2. “Không parallel-capable thì Java không hỗ trợ đa luồng”

Sai. Nhiều threads vẫn có thể **gọi** `loadClass` của cùng loader. Chúng sẽ đồng bộ trên một monitor chung trong implementation mặc định. Parallel-capable liên quan **độ mịn của khóa**, không phải cấm threads khác.

### 8.3. “Đăng ký parallel-capable cho một instance bằng constructor”

Sai. Registration áp dụng cho **subclass gọi method**, không phải object constructor. Gọi khi chưa có instance của subclass và khi mọi superclass được đăng ký mới hợp lệ.

### 8.4. “`ClassLoader` đã parallel-capable nên mọi subclass tự động parallel-capable”

Sai. JDK `ClassLoader` đã đăng ký, nhưng các subclass custom vẫn cần tự gọi `registerAsParallelCapable`. Đừng suy đoán từ superclass mà bỏ qua trạng thái của subclass.

### 8.5. “Parallel-capable cho phép định nghĩa cùng một tên hai lần”

Sai. Cùng tên phải dùng cùng khóa và `findLoadedClass` để tránh `defineClass` lặp, nếu không JVM có thể ném `LinkageError`. Tạo runtime class mới cho cùng tên có thể cần **ClassLoader instance khác**, với các ràng buộc identity/loading constraints của JVM.

### 8.6. “Có khóa theo tên nên override `loadClass` thế nào cũng được”

Sai. Khóa chỉ là một phần: vẫn phải kiểm tra cache, delegation, linking/resolution, package constraints và phối hợp nhiều loaders.

### 8.7. “Hai tên lớp khác nhau sẽ luôn load song song và chạy nhanh hơn”

Sai. Việc thread thực sự chạy song song phụ thuộc scheduler, CPU, I/O, parent loader, dependencies và các khóa khác. API chỉ cho phép locking mịn hơn, không đảm bảo throughput hay thứ tự.

### 8.8. “Khóa được chia sẻ giữa hai ClassLoader nếu chuỗi tên giống nhau”

Sai. `getClassLoadingLock` là instance method, lock map gắn với loader. Hai class loaders không biến thành một lock registry toàn JVM.

### 8.9. “`findLoadedClass` chỉ tìm class do loader trực tiếp define”

Không đầy đủ. Nó kiểm tra record của JVM đối với **initiating loader**; class có thể đã được parent định nghĩa rồi được child nhận qua delegation.

### 8.10. “Nếu lỗi nạp đồng thời chỉ cần catch `LinkageError`”

Sai. Catch lỗi không sửa race condition. Hãy kiểm tra `findLoadedClass` nằm trong đúng critical section, registration, đường gọi `defineClass`, delegation và cách dùng `getClassLoadingLock`.

## 9. Debug khi gặp deadlock hoặc duplicate definition

### 9.1. Dấu hiệu duplicate definition

Log có thể có:

~~~text
java.lang.LinkageError: loader ... attempted duplicate class definition for ...
~~~

Đây là **ví dụ hình dạng thông báo**, không phải stdout chuẩn. Khi điều tra, cần ghi **tên binary class**, defining/initiating loader liên quan, stack trace đầy đủ, đường `defineClass` và các thread đang load cùng tên.

Checklist:

1. Loader nào đã xử lý tên này trước?
2. Có override `loadClass` không, có giữ `getClassLoadingLock(name)` không?
3. `findLoadedClass(name)` được gọi **trước** `findClass/defineClass` và **trong** cùng lock không?
4. Subclass đã thực sự đăng ký parallel-capable chưa?
5. Hai threads có đi qua **cùng instance loader** không?
6. Parent đã nạp class này rồi hay code cố child-first define tiếp?
7. Có lỗi do **loading constraints giữa hai loaders** thay vì duplicate definition cùng loader không?

### 9.2. Deadlock khi nhiều threads load các tên khác nhau

Đặc biệt với loaders có delegation không theo cây cha-con đơn giản, khi giữ khóa loader chung, một thread có thể chờ loader khác đang bị chiếm; thread kia lại chờ loader đầu, tạo chu trình. Java API note khuyến nghị loader có **non-hierarchical delegation** đăng ký parallel-capable để giảm nguy cơ deadlock do khóa loader giữ suốt thời gian nạp.

Để chẩn đoán:

- Thu **thread dump** bằng công cụ phù hợp, ví dụ `jcmd <pid> Thread.print` hoặc `jstack <pid>` ở môi trường hỗ trợ.
- Tìm các threads đang ở trạng thái `BLOCKED` và monitors liên quan loader.
- Xem call stack `ClassLoader.loadClass`, `findClass` và custom delegation.
- Kiểm tra deadlock do **đảo thứ tự khóa giữa các loader hoặc các class name**.
- Đừng chỉ bật parallel-capable mà không sửa dependency graph tạo vòng lặp; registration không loại hết mọi deadlock.

Lệnh có `<pid>` là **mẫu cú pháp**, thay bằng PID của tiến trình thật khi sử dụng; các công cụ và output khác nhau theo JDK.

### 9.3. Khi performance kém vì contention

Đừng lập tức override `loadClass` để dùng lock theo tên thủ công. Trước tiên:

1. Profile/sampling xem contention có thật sự nằm tại loader monitor hay không.
2. Kiểm tra loader có registered parallel-capable theo `isRegisteredAsParallelCapable()` không.
3. Loại bỏ I/O và thao tác tốn thời gian khỏi đường hot path khi phù hợp.
4. Kiểm tra parent delegation và thư viện đang dùng có giữ khóa khác không.
5. Dùng `findClass` chuẩn và registration đúng trước khi cân nhắc chiến lược phức tạp.

## 10. Tự kiểm tra

1. `getClassLoadingLock(String)` trả gì với loader **chưa đăng ký parallel-capable**?
2. Với loader parallel-capable, hai lần gọi cùng binary name có trả cùng lock object không? Hai binary names khác nhau thì sao?
3. Vì sao `getClassLoadingLock("X")` và `getClassLoadingLock("Y")` của loader không parallel-capable đều là `this`?
4. Khóa theo tên class là khóa global trên JVM hay gắn với ClassLoader instance?
5. `getClassLoadingLock` tự đồng bộ ngay khi được gọi hay chỉ trả object?
6. Trong `loadClass` mặc định, `findLoadedClass`, delegation và `findClass` nằm trong vùng khóa nào?
7. Vì sao `findLoadedClass` phải được kiểm tra khi thread đã giữ lock?
8. Tại sao đăng ký parallel-capable nên nằm ở static initializer, không phải sau khi tạo instance?
9. Điều kiện gì xảy ra nếu một superclass custom ở giữa chưa parallel-capable?
10. `isRegisteredAsParallelCapable()` dùng để xác minh điều gì?
11. Nếu tám threads cùng yêu cầu `Alpha`, tại sao demo chỉ gọi custom `findClass` một lần?
12. Vì sao hai tên class khác nhau **có thể** được nạp đồng thời mà không đảm bảo có speedup?
13. Vì sao chỉ `synchronized(getClassLoadingLock(name))` mà quên `findLoadedClass` vẫn có thể định nghĩa trùng?
14. Khi nào nên override `findClass` thay vì `loadClass`?
15. Điều gì khiến custom delegation giữa loaders có thể gây deadlock dù đã dùng khóa theo tên?
16. `registerAsParallelCapable` có cho phép class cùng tên được định nghĩa hai lần trong cùng loader không?
17. Nếu `Thread A` giữ lock theo tên `Alpha` và `Thread B` giữ lock theo tên `Beta`, có tình huống dependency nào vẫn làm hai thread phải chờ nhau?
18. Trong một stack trace `LinkageError`, bạn cần phân biệt duplicate definition với loại loading-constraint violation như thế nào?

**Tiêu chí hoàn thành:** chạy chương trình Java 17+, giải thích chính xác chín dòng stdout, phân biệt loader lock và per-name lock, dùng đúng `registerAsParallelCapable`/`getClassLoadingLock`, giữ `findLoadedClass` trong critical section khi customize loader và chẩn đoán được contention/race/deadlock.

## 11. Tài liệu chính thức

- [Java SE 21 — ClassLoader#getClassLoadingLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#getClassLoadingLock(java.lang.String)): hợp đồng non-parallel trả `this`, parallel trả lock theo class name.
- [Java SE 21 — ClassLoader#registerAsParallelCapable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#registerAsParallelCapable()): điều kiện đăng ký cho subclass, trạng thái không thể đảo ngược.
- [Java SE 21 — ClassLoader#isRegisteredAsParallelCapable](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#isRegisteredAsParallelCapable()): kiểm tra trạng thái runtime.
- [Java SE 21 — ClassLoader#loadClass](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html#loadClass(java.lang.String,boolean)): thuật toán chuẩn, parent delegation, `findLoadedClass` và `findClass`.
- [JVMS Java SE 21 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): nền tảng class loading, linking, identity và ràng buộc của JVM.

Bài sử dụng API có từ Java 7+; ví dụ Java 17 chạy độc lập không cần Maven/Gradle hoặc thư viện ngoài. Quan sát bằng **object identity** của khóa và class, không dựa trên thời gian thực thi hay chuỗi `toString()` phụ thuộc JDK.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-class-loading-003">Parallel-capable class loader là gì</a>

<details>
<summary>Xem chi tiết</summary>

# Parallel-capable ClassLoader là gì? Nạp lớp đồng thời và khóa theo tên lớp trong Java

## 1. Khái niệm

**Parallel-capable class loader** là một `ClassLoader` được **đăng ký có khả năng hỗ trợ nạp các lớp khác tên một cách đồng thời**. Khi sử dụng triển khai `ClassLoader.loadClass(String, boolean)` mặc định, cơ chế khóa của nó được thu hẹp từ **toàn bộ đối tượng loader** xuống **mỗi binary name đang được tải**.

Nói ngắn gọn:

- **Loader thông thường (không parallel-capable):** hai thread yêu cầu *hai class khác nhau* qua **cùng loader** có thể phải chờ nhau vì cùng sử dụng khóa trên **đối tượng ClassLoader**.
- **Loader parallel-capable:** hai thread yêu cầu *hai tên class khác nhau* qua **cùng loader** có thể đi vào đường nạp đồng thời nhờ **khóa riêng theo tên**. Nếu cùng tên class, chúng vẫn được phối hợp để tránh định nghĩa trùng.
- **Đăng ký không tự làm mọi logic thread-safe:** người viết loader vẫn phải bảo vệ trạng thái mutable, cache, stream, package definition, xử lý classpath và bất kỳ lệnh override `loadClass` nào.
- **Không phải “nạp một class bằng nhiều thread”:** parallel-capable chủ yếu nhằm cho phép **nhiều class khác nhau** được nạp song song, vẫn bảo vệ yêu cầu nạp cùng binary name.

Cơ chế này xuất hiện từ **Java 7** nhằm giảm tình trạng deadlock ở hệ có nhiều class loaders và hỗ trợ nạp đồng thời an toàn hơn.

### Bài học trong module ClassLoader

Mục này thuộc nhóm **Nạp đồng thời**. Điều cần hiểu không chỉ là cách gọi `registerAsParallelCapable()`, mà còn là **khóa thực sự nằm ở đâu**, **vì sao thiết kế loader ảnh hưởng deadlock**, **cách đăng ký hợp lệ**, và **khi nào đăng ký vẫn không đủ**.

## 2. Vì sao ClassLoader cần khóa khi nạp lớp?

Giả sử hai thread đồng thời gọi:

```java
// Trích đoạn: loader là một ClassLoader do ứng dụng quản lý.
Class<?> first = loader.loadClass("plugin.Service");
Class<?> second = loader.loadClass("plugin.Service");
```

Nếu không phối hợp, cả hai có thể cùng thấy class chưa được nạp, đọc cùng file `plugin/Service.class`, rồi cùng gọi `defineClass("plugin.Service", ...)` trên một defining loader. Điều này có thể gây **`LinkageError` vì định nghĩa trùng**.

Triển khai `ClassLoader.loadClass(name, resolve)` mặc định dùng thứ tự:

1. `findLoadedClass(name)`: class đã được ghi nhận chưa?
2. `parent.loadClass(name)` (hoặc bootstrap nếu parent là `null`): hỏi loader cha.
3. `findClass(name)`: loader hiện tại tự tìm và định nghĩa khi parent không tìm thấy.
4. `resolveClass(c)` nếu tham số `resolve` là `true`.

Toàn bộ chuỗi mặc định được đồng bộ trên **`getClassLoadingLock(name)`**. Đây là điểm mấu chốt của parallel-capable: **đối tượng khóa do phương thức này trả về phụ thuộc trạng thái đăng ký của loader**.

Đừng đánh đồng `loadClass` với initialization (`<clinit>`). Một loader có thể nạp hoặc link class mà chưa yêu cầu chạy static initializer. Những khóa dùng cho class initialization của JVM không phải đơn giản là chính khóa do `getClassLoadingLock` trả về.

## 3. Không parallel-capable và parallel-capable khác nhau ở khóa nào?

Theo hợp đồng `ClassLoader.getClassLoadingLock(String)`:

- Nếu **loader không đăng ký parallel-capable**, phương thức **trả về `this`** — đối tượng loader hiện tại.
- Nếu **loader đã đăng ký parallel-capable**, phương thức trả về **một đối tượng khóa riêng gắn với binary name** được yêu cầu.
- Cùng loader, **cùng tên** thì nhận lại cùng khóa thích hợp; các tên khác nhau có thể có khóa khác nhau.
- Khi loader parallel-capable, truyền `null` cho `getClassLoadingLock` sẽ dẫn đến `NullPointerException` theo hợp đồng API. Với tên lớp hợp lệ, luôn truyền binary name không null.

### Hình dung sự khác nhau

```text
Không parallel-capable: một khóa L cho cả loader
-------------------------------------------------
Thread T1: loadClass("plugin.A")
             └── giữ khóa L ─────────────────┐
Thread T2: loadClass("plugin.B")              │
             └── cần cùng khóa L → chờ ◀─────┘


Parallel-capable: khóa theo từng binary name
---------------------------------------------
Thread T1: loadClass("plugin.A")
             └── giữ khóa K_A ───▶ nạp A
Thread T2: loadClass("plugin.B")
             └── giữ khóa K_B ───▶ nạp B
             (K_A và K_B khác nhau; có thể chạy đồng thời)

Thread T3: loadClass("plugin.A")
             └── cần K_A → chờ T1 rồi dùng kết quả đã nạp
```

**Lợi ích:** giảm khóa quá rộng khi nhiều request nạp các class độc lập. Khi loader tìm class trong JAR hoặc directory, phần I/O/tìm kiếm của những class khác tên có cơ hội tiến hành đồng thời.

**Giới hạn:** class A và B có thể phụ thuộc nhau hoặc loader có tài nguyên nội bộ dùng chung; một phần công việc vẫn có thể phải tuần tự hóa. Việc đăng ký không cam kết tốc độ luôn tăng và **không cam kết không bao giờ deadlock**.

## 4. Các API liên quan

| API | Vai trò | Điều cần nhớ |
|---|---|---|
| `ClassLoader.registerAsParallelCapable()` | Đăng ký **class của loader** là parallel-capable | `protected static`; gọi từ initializer của subclass |
| `loader.isRegisteredAsParallelCapable()` | Hỏi instance loader đã có đăng ký hay chưa | `public final`, từ Java 9 |
| `getClassLoadingLock(String name)` | Lấy khóa dùng khi nạp binary name | `protected`; mặc định trả `this` hoặc khóa theo tên |
| `loadClass(String name)` | Đường vào công khai khi yêu cầu class | Mặc định gọi protected `loadClass(name, false)` |
| `loadClass(String name, boolean resolve)` | Thuật toán tra loaded → parent → findClass → resolve | Mặc định đồng bộ trên khóa do `getClassLoadingLock` trả |
| `findLoadedClass(String name)` | Kiểm tra tên đã được JVM ghi nhận với initiating loader | Tránh định nghĩa lại class cũ |
| `findClass(String name)` | Hook custom loader tự tìm và định nghĩa class | Nên override phương thức này khi có thể |
| `defineClass(...)` | Giao bytes cho JVM định nghĩa runtime class | Không được gọi trùng cho cùng tên/defining loader |

`getClassLoadingLock` là phương thức có thể được subclass override nếu cần chính sách khóa đặc biệt. Tuy nhiên, với loader đơn giản, nên **để nguyên cơ chế chuẩn** và chỉ override `findClass`. Override khóa tùy tiện có thể làm mất những bảo đảm vốn có và khó chứng minh đúng ở nhiều thread.

## 5. Đăng ký parallel-capable
Subclass muốn song song phải gọi `registerAsParallelCapable()` từ static initializer của **chính lớp ấy**, khi chưa có instance và mọi superclass loader đã được đăng ký. Giá trị `false` cho biết không đủ điều kiện; nếu ứng dụng phụ thuộc vào parallel loading, báo lỗi sớm. Registration là theo **loader class**, không phải thiết lập trên từng object, và không thể hủy sau khi thành công.
```java
static {
    if (!registerAsParallelCapable()) {
        throw new ExceptionInInitializerError("Parallel registration failed");
    }
}
```
Ví dụ `ParallelLockDemo` và `ParallelDirectoryDemo` sau đây sử dụng chính quy tắc này. Đối với hierarchy nhiều cấp, caller và điều kiện đăng ký muộn, xem bài **registerAsParallelCapable và điều kiện đăng ký**; tại đây mục tiêu là hiểu lợi ích cùng giới hạn của **nạp nhiều tên class đồng thời**.

## 6. Ví dụ Java 17+ số 1: xem chính xác khóa theo tên

Tạo file `ParallelLockDemo.java`:

```java
public final class ParallelLockDemo {
    static class PlainLoader extends ClassLoader {
        PlainLoader() {
            super(ParallelLockDemo.class.getClassLoader());
        }

        Object lockFor(String name) {
            return getClassLoadingLock(name);
        }
    }

    static class ParallelLoader extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "Could not register ParallelLoader"
                );
            }
        }

        ParallelLoader() {
            super(ParallelLockDemo.class.getClassLoader());
        }

        Object lockFor(String name) {
            return getClassLoadingLock(name);
        }
    }

    public static void main(String[] args) {
        PlainLoader plain = new PlainLoader();
        ParallelLoader parallel = new ParallelLoader();

        System.out.println("plain registered: "
                + plain.isRegisteredAsParallelCapable());
        System.out.println("parallel registered: "
                + parallel.isRegisteredAsParallelCapable());

        System.out.println("plain A lock == B lock: "
                + (plain.lockFor("demo.A") == plain.lockFor("demo.B")));
        System.out.println("plain A lock == loader: "
                + (plain.lockFor("demo.A") == plain));

        System.out.println("parallel A lock == B lock: "
                + (parallel.lockFor("demo.A")
                   == parallel.lockFor("demo.B")));
        System.out.println("parallel A lock == A lock: "
                + (parallel.lockFor("demo.A")
                   == parallel.lockFor("demo.A")));
        System.out.println("parallel A lock == loader: "
                + (parallel.lockFor("demo.A") == parallel));
    }
}
```

Biên dịch và chạy bằng **JDK 17+**:

```bash
javac ParallelLockDemo.java
java ParallelLockDemo
```

Stdout:

```text
plain registered: false
parallel registered: true
plain A lock == B lock: true
plain A lock == loader: true
parallel A lock == B lock: false
parallel A lock == A lock: true
parallel A lock == loader: false
```

**Giải thích từng kết quả:**

- `PlainLoader` không gọi `registerAsParallelCapable`. Dù superclass `ClassLoader` đã đăng ký, **chính PlainLoader chưa đăng ký**, nên `getClassLoadingLock` trả đối tượng `plain` cho cả `demo.A` lẫn `demo.B`.
- `ParallelLoader` đăng ký thành công trong static initializer. Mỗi binary name có khóa riêng, nên khóa `demo.A` khác `demo.B`.
- Hai lần hỏi **cùng tên** `demo.A` nhận lại khóa tương ứng, giúp các thread yêu cầu cùng class không định nghĩa trùng.
- Khóa của `demo.A` trong loader parallel-capable không phải đối tượng loader, nên một thread `synchronized(parallel)` không phải chính cơ chế khóa theo tên do triển khai mặc định sử dụng.

Đây là **ví dụ xác minh hợp đồng locking**, không phải benchmark tốc độ. Nó cố ý không tạo thread, vì muốn chứng minh **nguyên nhân cho phép nạp song song** một cách không phụ thuộc scheduler.

## 7. Ví dụ Java 17+ số 2: nạp hai class qua loader parallel-capable

Ở ví dụ trước, ta chỉ quan sát khóa. Ví dụ này nạp bytecode thật từ directory qua hai thread, bảo toàn parent-first mặc định và giữ phần `findClass` đơn giản.

### 7.1. Chuẩn bị class plugin

Trong một thư mục làm việc trống, tạo `A.java`:

```java
package plugin;

public final class A {
    public static String id() {
        return "A";
    }
}
```

Tạo `B.java`:

```java
package plugin;

public final class B {
    public static String id() {
        return "B";
    }
}
```

### 7.2. Tạo ParallelDirectoryDemo.java

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class ParallelDirectoryDemo {
    static final class DirectoryLoader extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "DirectoryLoader registration failed"
                );
            }
        }

        private final Path root;
        private final AtomicInteger definitions = new AtomicInteger();

        DirectoryLoader(Path root, ClassLoader parent) {
            super(parent);
            this.root = root;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            Path file = root.resolve(
                    name.replace('.', '/') + ".class"
            );

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException cause) {
                throw new ClassNotFoundException(name, cause);
            }

            Class<?> defined =
                    defineClass(name, bytes, 0, bytes.length);
            definitions.incrementAndGet();
            return defined;
        }

        int definitions() {
            return definitions.get();
        }
    }

    public static void main(String[] args) throws Exception {
        DirectoryLoader loader = new DirectoryLoader(
                Path.of("plugin-classes"),
                ParallelDirectoryDemo.class.getClassLoader()
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Class<?>> left =
                    pool.submit(() -> loader.loadClass("plugin.A"));
            Future<Class<?>> right =
                    pool.submit(() -> loader.loadClass("plugin.B"));

            Class<?> a = left.get();
            Class<?> b = right.get();

            System.out.println("parallel capable: "
                    + loader.isRegisteredAsParallelCapable());
            System.out.println("A from child: "
                    + (a.getClassLoader() == loader));
            System.out.println("B from child: "
                    + (b.getClassLoader() == loader));
            System.out.println("definitions: "
                    + loader.definitions());
            System.out.println("A reused: "
                    + (loader.loadClass("plugin.A") == a));
        } finally {
            pool.shutdown();
        }
    }
}
```

### 7.3. Biên dịch và chạy

Tạo output directory. Trên Bash:

```bash
mkdir -p plugin-classes
```

Trên Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force plugin-classes | Out-Null
```

Sau đó:

```bash
javac --release 17 -d plugin-classes A.java B.java
javac --release 17 ParallelDirectoryDemo.java
java -cp . ParallelDirectoryDemo
```

Cấu trúc đầu ra:

```text
working-directory/
├── A.java
├── B.java
├── ParallelDirectoryDemo.java
├── ParallelDirectoryDemo.class
├── ParallelDirectoryDemo$DirectoryLoader.class
└── plugin-classes/
    └── plugin/
        ├── A.class
        └── B.class
```

**Không thêm `plugin-classes` vào `-cp`** của chương trình host. Nếu parent (application loader) cũng thấy `plugin.A` hoặc `plugin.B`, nó có thể nạp class đó trước; `findClass` của custom loader sẽ không được gọi cho tên đó, khiến `definitions` không phản ánh ví dụ mong muốn.

Stdout với đúng cấu trúc trên:

```text
parallel capable: true
A from child: true
B from child: true
definitions: 2
A reused: true
```

### 7.4. Ý nghĩa và giới hạn của ví dụ

Hai tác vụ `Future` yêu cầu **hai binary names khác nhau** trên **cùng DirectoryLoader instance**. Loader đã đăng ký parallel-capable và kế thừa `ClassLoader.loadClass` mặc định, nên đường nạp sử dụng khóa theo tên.

- Mỗi class được định nghĩa đúng một lần trong ví dụ.
- `AtomicInteger` làm bộ đếm `definitions` an toàn khi hai thread có thể tăng cùng lúc.
- `findClass` chỉ dùng dữ liệu `root` bất biến và biến byte array cục bộ của từng lời gọi, không dùng một `InputStream` chia sẻ thiếu khóa.
- `loadClass("plugin.A")` lại sau đó dùng trạng thái đã nạp, trả cùng đối tượng `Class<?>`.
- Thứ tự thực tế T1/T2 chạy I/O có thể khác theo scheduler. **Stdout không đo hay chứng minh hai thao tác `Files.readAllBytes` đã thực sự chồng thời gian.** Nó xác nhận loader được đăng ký, kết quả nạp thành công và giữ đúng danh tính kiểu.

Ví dụ này **không chạy các static initializers của A/B** chỉ vì dùng `loadClass`. Chúng không có static initializer quan sát được; nếu thêm `static { ... }`, hãy nhớ initialization là một giai đoạn riêng.

## 8. Tại sao vẫn phải bảo vệ cùng binary name?

Giả sử cả hai thread đều gọi:

```java
loader.loadClass("plugin.A");
```

Với loader parallel-capable mặc định, cả hai sẽ cần **cùng khóa dành cho `plugin.A`**. Một thread vào, kiểm tra `findLoadedClass`, tìm/định nghĩa A nếu cần; thread còn lại chờ, sau đó kiểm tra lại và lấy class đã được ghi nhận. Điều đó tránh hai đường cùng `defineClass` cho **cùng loader + cùng binary name**.

Sơ đồ:

```text
Thread T1                            Thread T2
---------                            ---------
loadClass("plugin.A")                loadClass("plugin.A")
lock K_A                              chờ lock K_A
findLoadedClass → null               ...
parent không tìm thấy                 ...
findClass → defineClass A             ...
unlock K_A                            nhận lock K_A
                                     findLoadedClass → A
                                     return A
```

**Parallel-capable không bỏ đồng bộ**; nó chỉ **thay độ hạt của đồng bộ**. Cơ chế này vẫn cần chính sách khóa đúng với tên class.

Nếu tự override `loadClass` mà bỏ qua `findLoadedClass` và `getClassLoadingLock`, một custom loader có thể thử định nghĩa cùng tên nhiều lần và gặp `LinkageError`. Đăng ký parallel-capable không sửa hộ một `loadClass` sai.

## 9. Vì sao ClassLoader là parallel-capable nhưng subclass có thể không?

Một sai lầm phổ biến là nghĩ:

> “`ClassLoader` vốn parallel-capable; tất cả class `extends ClassLoader` chắc cũng parallel-capable.”

Điều này **sai**. Từng class trong chuỗi subclass cần được đăng ký nếu muốn khả năng đó.

Ví dụ cây kế thừa:

```text
ClassLoader                         [registered]
   │
   ▼
MyBaseLoader                        [registered]
   ├── MyChildA                      [chưa đăng ký]
   │       └── MyGrandchildA         [không thể đăng ký thành công
   │                                   khi superclass chưa đăng ký]
   └── MyChildB                      [registered]
           └── MyGrandchildB         [phải đăng ký riêng nếu muốn]
```

### Ví dụ Java 17+ kiểm tra đăng ký thất bại

Tạo file `RegistrationChainDemo.java`:

```java
public final class RegistrationChainDemo {
    static class LegacyBase extends ClassLoader {
        // Không registerAsParallelCapable().
    }

    static class CannotRegisterChild extends LegacyBase {
        static final boolean REGISTERED =
                registerAsParallelCapable();
    }

    static class GoodBase extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "GoodBase registration failed"
                );
            }
        }
    }

    static class GoodChild extends GoodBase {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "GoodChild registration failed"
                );
            }
        }
    }

    public static void main(String[] args) {
        LegacyBase legacy = new LegacyBase();
        CannotRegisterChild rejected = new CannotRegisterChild();
        GoodChild accepted = new GoodChild();

        System.out.println("legacy: "
                + legacy.isRegisteredAsParallelCapable());
        System.out.println("registration attempt: "
                + CannotRegisterChild.REGISTERED);
        System.out.println("rejected child: "
                + rejected.isRegisteredAsParallelCapable());
        System.out.println("accepted child: "
                + accepted.isRegisteredAsParallelCapable());
    }
}
```

Chạy:

```bash
javac RegistrationChainDemo.java
java RegistrationChainDemo
```

Stdout:

```text
legacy: false
registration attempt: false
rejected child: false
accepted child: true
```

`CannotRegisterChild` đăng ký thất bại vì superclass `LegacyBase` không đăng ký; không có “kế thừa tự động” trạng thái đăng ký từ grandparent `ClassLoader`. Ngược lại, cả `GoodBase` và `GoodChild` đều tự đăng ký trong initializer của chính chúng nên chuỗi hợp lệ.

Đoạn `if (!registerAsParallelCapable()) throw ...` trong các loader production giúp phát hiện sớm giả định sai về hierarchy; không nên lờ đi giá trị `false` rồi tuyên bố loader đã hỗ trợ song song.

## 10. Deadlock khi đường ủy quyền không theo cây
Nếu hai loader có thể gọi sang nhau khi đang giữ các loading locks, hai thread có thể tạo vòng chờ A → B → A. Lock theo tên giảm tắc nghẽn do **các tên khác nhau trên cùng loader**, nhưng vòng chờ giữa những per-name locks cụ thể vẫn có thể xảy ra. Vì vậy loader parallel-capable cần một delegation graph có route kết thúc và lock ordering đúng. Bài **Deadlock khi nhiều loader ủy quyền chéo** chứa thí nghiệm chạy được và quy trình lấy thread dump; không xem `registerAsParallelCapable` là biện pháp sửa vòng phụ thuộc.

## 11. Phân biệt deadlock loading với deadlock initialization

Đây là hai lớp vấn đề khác nhau:

| Phạm vi | Khóa/đồng bộ | Dấu hiệu |
|---|---|---|
| Class loading | `getClassLoadingLock(name)` và khóa custom loader nếu có | Thread chờ khi gọi `loadClass`/`findClass` |
| Class initialization | Đồng bộ initialization do JVM quản lý cho từng runtime class/interface | Thread chờ trong/qua `<clinit>` hoặc code liên quan |
| Lock ứng dụng | `synchronized`, `ReentrantLock`, `Future.get`, `Thread.join`... | Thread chờ nhau do logic ứng dụng |

Nếu thread T1 đang initialize A và cần B, trong khi thread T2 đang initialize B và cần A, deadlock có thể xuất hiện **ngay cả khi ClassLoader đã parallel-capable**. Không “sửa” một vòng static initialization bằng cách chỉ thêm `registerAsParallelCapable`.

Mặt khác, một vòng yêu cầu initialization cùng thread A → B → A có thể đọc static fields chưa được gán, không nhất thiết deadlock. Muốn phân biệt, xem stack traces, các `<clinit>` và đường gọi `loadClass` của từng thread.

**Không dùng `ThreadMXBean.findDeadlockedThreads()` làm phép thử duy nhất**: API đó không bảo đảm phát hiện mọi vòng chờ nội bộ khi JVM điều phối class loading/initialization. Cần thread dump và thông tin class loader cụ thể.

## 12. Trường hợp override loadClass: đừng bỏ mất locking

Khi chỉ override `findClass`, ta dùng được khóa theo tên của triển khai mặc định. Nhưng một số loader muốn **child-first** cho vài package, nên phải override `loadClass`.

Mẫu ở đây là **pseudocode định hướng**, không phải một class Java độc lập và **không** phải cách duy nhất để triển khai loader tùy chỉnh:

```java
// PSEUDOCODE — cần handling parent==null, policy package,
// loader/module constraints và exception rõ ràng.
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
            if (isChildFirstNamespace(name)) {
                try {
                    loaded = findClass(name);
                } catch (ClassNotFoundException ignored) {
                    loaded = getParent().loadClass(name);
                }
            } else {
                try {
                    loaded = getParent().loadClass(name);
                } catch (ClassNotFoundException ignored) {
                    loaded = findClass(name);
                }
            }
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }
}
```

**Cảnh báo bắt buộc:** đoạn này dùng `getParent().loadClass`, nên không xử lý parent `null`; nó không có namespace whitelist/blacklist hoặc module/package checks đầy đủ. **Không copy như implementation production**. Dùng nó để thấy rằng khi override `loadClass` phải giữ **`synchronized(getClassLoadingLock(name))` + `findLoadedClass` + định nghĩa class một lần** theo từng tên.

Khi thiết kế child-first:
- Không cố child-first cho `java.*` hay các platform classes.
- Xác định package nào là API dùng chung phải do parent định nghĩa để tránh hai runtime types cùng tên.
- Không catch mọi `LinkageError` từ parent rồi giả vờ “không tìm thấy” để fallback sang child.
- Kiểm tra vòng lặp delegation trong hệ nhiều loaders.
- Bảo đảm hai yêu cầu cùng tên không đồng thời gọi `defineClass`.
- Không đặt `synchronized(this)` quanh toàn bộ `loadClass` nếu mục tiêu là parallel-capable; khóa này lại tuần tự hóa mọi tên.

### Tại sao synchronized(this) có thể vô hiệu hóa lợi ích?

```java
// ANTI-PATTERN — chỉ minh họa:
@Override
public synchronized Class<?> loadClass(String name)
        throws ClassNotFoundException {
    return super.loadClass(name);
}
```

Một loader dù đã đăng ký parallel-capable vẫn phải lấy **monitor của toàn bộ instance** khi vào method trên; các request khác tên bị tuần tự hóa ngay từ cửa. Đừng dùng `synchronized` trên cả loader chỉ vì “sợ race”, rồi kỳ vọng parallel loading có hiệu quả.

Nếu phải bảo vệ cấu trúc dữ liệu chung, hãy dùng khóa riêng cho dữ liệu đó hoặc concurrent collections; tránh giữ khóa toàn cục trong những thao tác I/O hoặc delegation có thể gọi loader khác.

## 13. Thread-safety của findClass: đăng ký chỉ là lời cam kết

Đăng ký thành công có ý nghĩa rằng **các lời gọi nạp class khác nhau có thể đồng thời tiến vào code loader**. Vì vậy `findClass` phải sẵn sàng cho đồng thời.

### Những phần dễ có race condition

- Một `HashMap<String, byte[]>` mutable được đọc/ghi từ nhiều thread mà không có đồng bộ.
- Dùng **một InputStream hoặc ZipInputStream chia sẻ** và để các thread đọc class entries đồng thời.
- Dùng biến instance kiểu `String currentClassName` hoặc `byte[] scratchBuffer` làm bộ nhớ tạm chung cho mọi yêu cầu nạp.
- Hai thread đồng thời cố định nghĩa cùng package hoặc cập nhật một registry package/certificate không đúng quy tắc.
- Cập nhật classpath/URL list trong lúc nạp mà không có chính sách đồng bộ rõ.
- Giữ một monitor rồi gọi loader khác, mở ra chu trình khóa.
- Không quản lý vòng đời đóng JAR/URLClassLoader khi nhiều thread còn nạp class từ nguồn đó.

Ví dụ `DirectoryLoader` ở mục 7 dùng `Path root` bất biến, `byte[]` cục bộ và `AtomicInteger`. Thiết kế đó nhỏ và an toàn hơn việc chia sẻ một byte array để tiết kiệm allocation bằng cách không có khóa.

### Một cache phù hợp

```java
// Trích đoạn trong custom loader:
private final java.util.concurrent.ConcurrentMap<String, byte[]>
        bytesCache = new java.util.concurrent.ConcurrentHashMap<>();
```

`ConcurrentHashMap` có thể bảo vệ map khỏi race, nhưng **không thay thế khóa của việc định nghĩa class**. Nếu loader override `loadClass` sai và hai thread vẫn cùng `defineClass` một binary name, cache bytecode an toàn cũng không cứu được.

Cũng cần thiết kế lifecycle cache: một cache giữ class loader, `Class<?>` hoặc object plugin lâu hơn cần thiết có thể cản trở class unloading khi redeploy.

## 14. Cùng tên, nhiều defining loaders: phân biệt với song song

Hai thread nạp cùng binary name thông qua **hai loader khác nhau** không nhất thiết tạo ra cùng runtime class:

```text
Thread T1 → Loader-A.loadClass("plugin.Service")
Thread T2 → Loader-B.loadClass("plugin.Service")

Nếu Loader-A và Loader-B tự định nghĩa:
  ("plugin.Service", Loader-A) ≠ ("plugin.Service", Loader-B)
```

Đây là vấn đề **type identity**, không phải bản chất parallel-capable. Khóa theo tên thường **thuộc từng instance loader**, không phải một khóa global chung trên toàn JVM cho mọi loader có cùng binary name.

Ngược lại, một loader con có thể ủy quyền cho parent và nhận lại class do parent định nghĩa; lúc đó defining loader khác initiating loader. Khi kiểm tra:

```java
Class<?> result = loader.loadClass("plugin.Service");
System.out.println(result.getClassLoader());
```

Đây là **trích đoạn**; kết quả `getClassLoader()` cho biết defining loader của runtime class (với bootstrap thường biểu diễn bằng `null`). Không suy diễn từ thread đã gọi loader nào rằng loader đó chắc chắn đã định nghĩa class.

## 15. Parallel-capable có tự tăng hiệu năng không?

Không có bảo đảm. Nó **mở khả năng** chứ không phải lời cam kết tăng tốc ở mọi workload.

Hiệu quả phụ thuộc vào:

1. **Số lượng tên class khác nhau đang được yêu cầu đồng thời.**
2. **Chi phí thực tế của việc tìm và định nghĩa class**: đọc JAR qua file system/network, verify/link, caching.
3. **Mức phụ thuộc giữa các class**: một class có thể cần class khác, dẫn đến chờ.
4. **Tính thread-safe của storage/resource loader**: vẫn có thể có khóa ở Zip/JAR reader, data store hoặc network client.
5. **Parent delegation và module graph**: class được parent trả ngay có thể không đi vào `findClass` của child.
6. **Độ hạt khóa bổ sung** do ứng dụng đặt; `synchronized(this)` và cache lock dài hạn có thể vô hiệu hóa lợi ích.
7. **JVM/OS scheduling và I/O contention**: nhiều thread không chắc nhanh hơn một thread trong môi trường I/O giới hạn.

Không benchmark chỉ bằng việc so hai dòng `System.currentTimeMillis()` trong một lần run. Nên dùng nhiều iteration, warm-up, class names khác nhau hợp lệ, quan sát throughput/latency và thread dumps. Một loader đã nạp tất cả class từ trước thường khiến benchmark chủ yếu đo cache hit `findLoadedClass` chứ không còn đo concurrent class definition.

## 16. Cách debug deadlock/race trong class loading

### Bước 1: xác định loader và trạng thái đăng ký

```java
// Trích đoạn khi đã có một ClassLoader loader:
System.out.println(loader.getClass().getName());
System.out.println(loader.isRegisteredAsParallelCapable());
System.out.println(loader.getParent());
```

`isRegisteredAsParallelCapable()` có từ Java 9; ví dụ ở bài này nhắm Java 17+.

### Bước 2: xác định các tên class cạnh tranh

Log binary name, thread name và ID loader ngay **trước/sau** các điểm:

- `loadClass(name)`
- `findClass(name)`
- `defineClass(name, bytes, ...)`
- Các lời gọi delegation giữa loaders
- Khóa custom và I/O source

Không log mỗi URL có query string chứa credential hoặc đường dẫn nhạy cảm trong hệ production.

### Bước 3: xem thread dump

Trên HotSpot/JDK có công cụ tương ứng:

```bash
jcmd <pid> Thread.print -l
```

`<pid>` là **placeholder cho process ID thật**, không phải chuỗi cần nhập nguyên văn. Tìm thread đang chờ trong `ClassLoader.loadClass`, `getClassLoadingLock`, `findClass`, static initializer hoặc code plugin. Đối chiếu hai thread có đang giữ lock trên cùng loader instance hoặc những per-name locks hay không.

### Bước 4: kiểm tra đường override

Nếu class loader đăng ký parallel-capable nhưng hiệu năng vẫn tuần tự:

- Có override `loadClass` bằng `synchronized` trên cả instance không?
- Có dùng `synchronized(this)`/global lock trong `findClass` không?
- Có mọi thread chờ một `ZipFile`/cache lock duy nhất không?
- Có khóa trước khi gọi loader khác khiến vòng delegation deadlock không?
- Có class initialization/`<clinit>` chờ thread khác khiến tưởng là lỗi class loading không?

### Bước 5: tái hiện và sửa đúng nguyên nhân

- Dùng test nhiều thread với hai nhóm: **nhiều tên khác nhau** và **cùng một tên**.
- Kiểm tra mọi tên được định nghĩa **tối đa một lần trên mỗi defining loader**.
- Sử dụng `CountDownLatch` hoặc `CyclicBarrier` ở test để kiểm soát thời điểm bắt đầu, không dựa vào `Thread.sleep()` tùy tiện.
- Nếu có race do cache/shared resources, sửa đồng bộ hoặc dữ liệu; không chỉ bật registration.
- Nếu có vòng chờ do các loader ủy quyền chéo, đơn giản hóa dependency graph; per-name locking chỉ giảm một lớp rủi ro.

## 17. Một bài tập thử nghiệm đồng thời (không ép stdout cố định)

Có thể mở rộng `DirectoryLoader` ở mục 7 bằng bộ đếm thời điểm vào `findClass` cho hai tên khác nhau:

```java
// Trích đoạn ý tưởng test, không phải file chạy độc lập:
CountDownLatch firstTwoEntered = new CountDownLatch(2);

@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    firstTwoEntered.countDown();
    // Sau đó test cho phép hai thread tiếp tục bằng latch khác
    // trước khi đọc bytes và defineClass.
    ...
}
```

Để kiểm chứng đồng thời:
1. Dùng **một loader instance** và hai thread gọi `loadClass` cho **hai binary names khác nhau**.
2. Chặn cả hai ở đầu `findClass` trên một latch khác; thread chính kiểm tra cả hai đã vào.
3. Lặp lại với loader **không đăng ký**. Với đường `loadClass` mặc định, một thread có thể không vào `findClass` trước khi thread thứ nhất nhả khóa toàn loader.
4. Dùng timeout và giải phóng latch trong `finally` để không làm test treo vĩnh viễn.
5. Không gán stdout hoặc thời gian chờ tuyệt đối cố định; scheduler và machine load khác nhau.

Mục này mô tả phương pháp kiểm thử. Các đoạn có `...` chỉ là **pseudocode**, không phải Java source có thể biên dịch trực tiếp. Hai demo runnable ở mục 6, 7 và ví dụ hierarchy ở mục 9 đã có code hoàn chỉnh, lệnh compile và stdout xác định.

## 18. Những nhầm lẫn phổ biến

| Nhầm lẫn | Sự thật |
|---|---|
| Kế thừa `ClassLoader` thì auto parallel-capable | Không; subclass cần tự đăng ký |
| Đăng ký bằng cách gọi method trong constructor | Sai thời điểm; phải trước khi tạo instance, thường static initializer |
| Chỉ class con đăng ký là đủ | Không; toàn bộ superclass chain liên quan cũng phải đăng ký |
| Parallel-capable nghĩa là nạp một class trên nhiều thread đồng thời | Khóa theo tên vẫn ngăn định nghĩa trùng cùng tên |
| Parallel-capable bỏ hết synchronized | Không; thay khóa toàn loader bằng khóa theo class name |
| Đăng ký làm `findClass` thread-safe tự động | Không; cache, I/O, trạng thái dùng chung vẫn cần bảo vệ |
| `synchronized(this)` quanh `loadClass` vẫn được song song | Nó tuần tự hóa mọi tên, làm giảm lợi ích |
| Chỉ cần parallel-capable là không deadlock | Vòng cùng tên, init và khóa riêng vẫn có thể deadlock |
| Nạp class song song là chạy `<clinit>` song song theo cách không kiểm soát | Loading và initialization có quy tắc riêng |
| Có hai loader nạp cùng tên thì chắc cùng class | Không; defining loader là một phần của runtime identity |
| Có `AtomicInteger` đếm class là đủ an toàn | Không; việc `defineClass` trùng và state khác vẫn cần đúng khóa |
| Loader đã nạp class xong thì sửa file và gọi lại sẽ nhận bản mới | Không; `findLoadedClass` có thể trả class cũ |
| Registration tự biến ClassLoader thành child-first | Không; delegation order không đổi |

## 19. Checklist thiết kế custom loader cho ứng dụng thực tế

**Trước khi triển khai:**

- Xác định cần **nạp nhiều class khác nhau đồng thời** hay chỉ load lần lượt.
- Kiểm tra mọi superclass loader hỗ trợ parallel-capable.
- Quyết định có thể chỉ override `findClass` hay thật sự phải override `loadClass`.
- Làm rõ parent/shared API, namespace riêng của plugin và loader lifecycle.

**Khi triển khai:**

- Đăng ký trong static initializer và xác nhận giá trị `true`.
- Giữ `loadClass` mặc định khi có thể; nó đã phối hợp khóa và caching.
- Chỉ dùng `defineClass` đúng một lần cho một binary name trong cùng defining loader.
- Dùng local variables và concurrent collections cho mutable shared state.
- Không giữ khóa toàn loader qua I/O hoặc delegation nếu không cần.
- Giữ đúng semantics `ClassNotFoundException` so với các `LinkageError` khi bytes hỏng.
- Không thay đổi parent-first ngầm; document rõ phần nào child-first nếu có.

**Khi kiểm thử:**

- Kiểm tra `isRegisteredAsParallelCapable()`.
- Kiểm tra cùng name → một runtime class; khác names → hai class được định nghĩa đúng.
- Kiểm tra race ở package definition, resources, cache, close/reload.
- Kiểm tra dependency chéo và class initialization.
- Dùng thread dump/structured logs để phân biệt loading lock với JVM initialization lock.

**Khi vận hành:**

- Không đóng `URLClassLoader` khi thread khác còn nạp từ nó; điều phối quiescence trước close.
- Dừng task, unregister callbacks và restore Thread Context ClassLoader khi ngừng plugin.
- Xem xét tình trạng classloader còn reachable sau redeploy, không giả định close dẫn đến unload.
- Theo dõi performance bằng workload thực thay vì chỉ nhìn trạng thái registered.

## 20. Câu hỏi tự kiểm tra

1. **Parallel-capable ClassLoader nghĩa là gì?** Loader được đăng ký để có thể nạp các binary names khác nhau đồng thời thông qua khóa theo tên của triển khai chuẩn.
2. **Non-parallel loader dùng khóa gì trong `loadClass` mặc định?** Chính đối tượng loader (`this`).
3. **Parallel loader dùng khóa gì?** Khóa riêng gắn với binary name được yêu cầu.
4. **Hai thread cùng yêu cầu `plugin.A` có cùng lúc được `defineClass` không?** Không theo triển khai đúng; cùng tên chia khóa/caching để tránh định nghĩa trùng.
5. **Đăng ký qua API nào?** `protected static ClassLoader.registerAsParallelCapable()` trong static initializer của subclass.
6. **Có bắt buộc các superclass cũng đăng ký không?** Có, trừ `Object` theo contract.
7. **`ClassLoader` đã đăng ký có làm `extends ClassLoader` tự đăng ký không?** Không.
8. **API nào hỏi một loader instance đã đăng ký chưa?** `isRegisteredAsParallelCapable()` (Java 9+).
9. **Vì sao nên override `findClass` thay vì `loadClass` khi chỉ cần nguồn bytecode mới?** Giữ thuật toán delegation, `findLoadedClass`, locking và `resolveClass` mặc định.
10. **`synchronized(this)` quanh toàn bộ loadClass ảnh hưởng gì?** Chặn nhiều class khác tên cùng lúc trên monitor của loader, làm mất lợi ích của per-name locks.
11. **Đăng ký có tự bảo vệ `HashMap` hoặc shared `InputStream` trong `findClass` không?** Không.
12. **Parallel-capable có ngăn deadlock của `<clinit>` không?** Không; class initialization là cơ chế đồng bộ khác.
13. **Hai defining loaders khác nhau tạo class cùng binary name có cùng runtime type không?** Không.
14. **Lý do chính tránh deadlock khi delegation chéo là gì?** Giảm các vòng chờ gây ra bởi khóa toàn bộ loader; vẫn cần thiết kế dependency và locking hợp lý.
15. **Có cần benchmark sau khi đăng ký không?** Có, nếu mục tiêu là performance; registration chỉ mở khả năng chứ không đảm bảo nhanh hơn.

## Tài liệu chuẩn

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `registerAsParallelCapable`, `isRegisteredAsParallelCapable`, `getClassLoadingLock` và `loadClass`; áp dụng cho toàn bộ ví dụ Java 17 trong bài.
- [Java SE 25 — ClassLoader](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): contract cập nhật của registration, locking, đồng bộ mặc định và loader hierarchy.
- [Oracle — Multithreaded Custom Class Loaders in Java](https://docs.oracle.com/javase/8/docs/technotes/guides/lang/cl-mt.html): hướng dẫn thiết kế custom class loader đa luồng, đăng ký parallel-capable và những cảnh báo khi override `loadClass`.
- [Java Virtual Machine Specification — §5.3 Creation of Classes and Interfaces](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): defining/initiating loaders và runtime class identity.
- [Java Language Specification — §§12.2 Loading, 12.3 Linking, 12.4 Initialization](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html): mối quan hệ giữa loading, linking và initialization.

**Kết luận:** **Parallel-capable** là năng lực đã đăng ký giúp một `ClassLoader` dùng **khóa theo tên class** thay vì khóa toàn bộ loader khi gọi `loadClass` mặc định. Nó vừa duy trì an toàn cho **cùng một binary name**, vừa cho phép **các tên khác nhau** có thể nạp đồng thời. Đăng ký đúng superclass chain là điều kiện cần; code `findClass` thread-safe, delegation không gây chu trình, và vòng đời tài nguyên đúng mới giúp hệ thống thực sự an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-class-loading-004">registerAsParallelCapable và điều kiện đăng ký</a>

<details>
<summary>Xem chi tiết</summary>

# registerAsParallelCapable và điều kiện đăng ký

## 1. Vấn đề: nhiều thread cùng yêu cầu nạp class

Trong ứng dụng Java có plugin, application server, proxy framework hoặc nhiều tác vụ nền, hai thread có thể đồng thời yêu cầu một `ClassLoader` nạp hai lớp khác nhau. Nếu mọi lần nạp đều giữ **cùng một khóa của toàn loader**, những yêu cầu độc lập cũng phải chờ nhau. Điều này có thể giảm throughput và, trong một topology ClassLoader ủy quyền không thuần cây, còn làm tăng nguy cơ deadlock.

Java cho phép tác giả một subclass của `ClassLoader` khai báo rằng loại loader ấy có thể hỗ trợ **parallel class loading** bằng phương thức:

~~~java
protected static boolean ClassLoader.registerAsParallelCapable()
~~~

Dòng trên mô tả chữ ký API, **không phải cú pháp Java để gọi phương thức**. Trong class kế thừa `ClassLoader`, cách thường dùng là gọi `registerAsParallelCapable()` ngay trong **static initializer**, trước khi có bất kỳ instance nào của class đó được tạo.

~~~java
public final class MyLoader extends ClassLoader {
    static {
        if (!registerAsParallelCapable()) {
            throw new ExceptionInInitializerError(
                    "Cannot register MyLoader as parallel capable");
        }
    }

    public MyLoader(ClassLoader parent) {
        super(parent);
    }
}
~~~

Đây là ví dụ lớp có thể biên dịch khi lưu thành `MyLoader.java`, nhưng nó **chưa triển khai nguồn bytecode của riêng mình**; nếu muốn loader nạp class plugin thực sự, cần bổ sung `findClass` hoặc dùng loader chuẩn như `URLClassLoader`. Mẫu này minh họa **thời điểm đăng ký và xử lý kết quả**, không phải một loader plugin hoàn chỉnh.

**Thông điệp chính:** `registerAsParallelCapable()` khai báo khả năng phối hợp nạp đồng thời của **class loader type**, chứ không phải gọi trên một instance để bật tùy chọn runtime cho mỗi loader.

## 2. Hai điều kiện đăng ký theo Javadoc Java 17

Javadoc `ClassLoader.registerAsParallelCapable()` quy định đăng ký thành công **khi và chỉ khi cả hai điều kiện** cùng đúng:

1. **Chưa có bất kỳ instance nào của class loader gọi phương thức đăng ký được tạo.**
2. **Mọi superclass của class loader đó, trừ `Object`, đều đã được đăng ký parallel capable.**

Phương thức trả về:

- `true` nếu đăng ký thành công;
- `false` nếu các điều kiện đăng ký không được đáp ứng.

Một loader type sau khi đã đăng ký parallel capable **không thể hủy đăng ký để chuyển ngược về non-parallel capable**.

`ClassLoader` của Java **đã được đăng ký parallel capable mặc định**, nhưng **mỗi subclass muốn tham gia vẫn phải tự đăng ký**. Không được suy ra “vì class cha `ClassLoader` đã registered nên subclass cũng tự động registered”.

| Trường hợp | Kết quả thường gặp |
|---|---|
| `MyLoader extends ClassLoader` và gọi đăng ký trong `static` block trước khi tạo instance | `true` |
| `ChildLoader extends MyLoader`; `MyLoader` đã registered và `ChildLoader` tự đăng ký sớm | `true` |
| `ChildLoader` quên tự đăng ký dù parent registered | `ChildLoader` vẫn non-parallel capable |
| `ChildLoader` đăng ký nhưng có superclass loader **không** registered | `false` |
| Gọi đăng ký lần đầu khi instance của chính caller class đã được tạo | `false` |
| Đăng ký thành công rồi tạo nhiều instance của cùng loader class | Những instance thuộc loader type đã registered |
| Muốn “tắt” đăng ký sau đó | Không có API chuyển ngược |

Lưu ý thuật ngữ **caller** ở đây là **class loader subclass thực sự gọi phương thức đăng ký**, không phải một object được truyền làm tham số. Phương thức là `protected static`, không phải `public` instance setter.

## 3. Vì sao phải gọi trong `static initializer`?

Static initializer chạy trong giai đoạn **class initialization**, trước khi constructor tạo instance đầu tiên có thể hoàn thành. Đặt đăng ký ở đó phù hợp chính xác với điều kiện “chưa có instance”:

~~~java
class ParallelDirectoryLoader extends ClassLoader {
    static {
        boolean ok = registerAsParallelCapable();
        if (!ok) {
            throw new ExceptionInInitializerError(
                    "Parent hierarchy is not parallel capable");
        }
    }

    ParallelDirectoryLoader(ClassLoader parent) {
        super(parent);
    }
}
~~~

Nếu đăng ký thất bại và bạn **tiếp tục chạy** như không có chuyện gì, loader type vẫn có thể vận hành với cơ chế khóa coarse-grained. Nhưng nếu thiết kế của bạn **phụ thuộc bắt buộc** vào parallel capability, nên fail fast như ví dụ.

Đừng gọi `registerAsParallelCapable()` trong constructor để “đăng ký instance này”, hoặc từ một nút bấm/config runtime để chuyển chế độ sau khi loader đã được sử dụng. Đăng ký thuộc **vòng đời class**, không thuộc vòng đời object.

Một điểm Java căn bản: static initializer của superclass chạy trước initializer của subclass theo quy tắc initialization. Nhờ thế, nếu cha đăng ký trong static initializer và không có instance cha bị tạo trước đó, subclass có thể gọi đăng ký trong static initializer sau khi cha đã được initialized. Nhưng **class cha vẫn phải thực sự đăng ký thành công**; chỉ extends một cha “có ý định đăng ký” không đủ.

## 4. Quan hệ kế thừa: đăng ký phải xuyên suốt chuỗi cha

Hãy xét hai cây:

~~~text
Cây hợp lệ                              Cây thất bại

ClassLoader [registered]                ClassLoader [registered]
       │                                        │
       ▼                                        ▼
BaseLoader [registered]                 LegacyLoader [NOT registered]
       │                                        │
       ▼                                        ▼
ChildLoader [registered]                ChildLoader [attempts registration]
                                                │
                                                ▼
                                           returns false
~~~

Trong cây thứ nhất:

- `ClassLoader` được JDK đăng ký sẵn.
- `BaseLoader` gọi đăng ký sớm và nhận `true`.
- `ChildLoader` cũng gọi đăng ký sớm, thấy tất cả superclass loader đều registered → `true`.

Trong cây thứ hai:

- `LegacyLoader` không đăng ký.
- Dù `ChildLoader` gọi `registerAsParallelCapable()` trong static initializer trước khi tạo instance, **điều kiện superclass vẫn sai**.
- Kết quả là `false`; đăng ký ở một subclass không thể “nhảy qua” superclass chưa registered.

Điều này quan trọng khi bạn kế thừa một thư viện ClassLoader do người khác viết. Nếu base class chưa được thiết kế và đăng ký parallel capable, subclass của bạn không thể sửa chỉ bằng cách tự gọi đăng ký.

### 4.1. Phân biệt parent loader và superclass

Hai khái niệm Java rất dễ bị nhầm:

**Superclass** là quan hệ kế thừa *giữa các class trong mã nguồn*: `MyLoader extends BaseLoader`. Đây là yếu tố trong **điều kiện registerAsParallelCapable**.

**Parent ClassLoader** là **tham chiếu object dùng để delegation** khi loader tải class: `new MyLoader(parent)`. Đây là cấu hình **mỗi instance**.

~~~java
// Mã minh họa:
MyLoader loader = new MyLoader(ClassLoader.getPlatformClassLoader());
~~~

Việc truyền platform loader làm parent **không làm cho một superclass chưa đăng ký trở thành registered**. Ngược lại, việc tất cả superclass registered **không chứng minh** parent loader instance có bất kỳ cấu hình tùy biến nào bạn mong muốn.

| Câu hỏi | Phải kiểm tra |
|---|---|
| “MyLoader có đủ điều kiện registerAsParallelCapable?” | Chuỗi **superclass của MyLoader** và thời điểm tạo instance |
| “Loader A hỏi loader nào trước khi nạp?” | `getParent()` của **instance** A và delegation logic |
| “Hai yêu cầu cùng loader có thể lấy hai khóa riêng?” | Registration trạng thái **loader class**, `getClassLoadingLock(name)` |
| “Hai plugin loaders có thấy cùng interface?” | Parent/delegation và defining loader của shared API |

## 5. Đăng ký thay đổi khóa thế nào?
Đối với `loadClass` mặc định, `getClassLoadingLock(name)` trả **`this`** nếu subclass không registered; khi registered, nó trả **một khóa ổn định theo binary name**. Do đó A/B khác tên có thể load đồng thời, còn hai yêu cầu A vẫn phải dùng cùng khóa và kiểm tra `findLoadedClass` trước khi `defineClass`.
Việc đăng ký không đảm bảo tăng hiệu năng hoặc làm `findClass` thread-safe. Thí nghiệm §6 kiểm tra khóa bằng identity và phân biệt registration thành công, superclass chưa đăng ký và trường hợp đăng ký muộn.

## 6. Chương trình Java 17+ kiểm chứng hai điều kiện đăng ký

Đây là một file **độc lập** minh họa bốn trường hợp:

1. Class trực tiếp extends `ClassLoader` đăng ký sớm: thành công.
2. Subclass của loader đã đăng ký tiếp tục đăng ký: thành công.
3. Subclass của loader chưa đăng ký cố đăng ký: thất bại.
4. ClassLoader đã có instance rồi mới thử đăng ký: thất bại.

Chương trình còn kiểm tra khóa cho cùng tên và khác tên, để nhìn thấy hệ quả của registration mà không dựa vào race đa luồng hoặc đo thời gian không ổn định.

Tạo file **`ParallelRegistrationDemo.java`**:

~~~java
public final class ParallelRegistrationDemo {
    private ParallelRegistrationDemo() {
    }

    static class RegisteredParent extends ClassLoader {
        static final boolean REGISTERED = registerAsParallelCapable();

        RegisteredParent() {
            super(ClassLoader.getSystemClassLoader());
        }

        Object lock(String name) {
            return getClassLoadingLock(name);
        }
    }

    static final class RegisteredChild extends RegisteredParent {
        static final boolean REGISTERED = registerAsParallelCapable();

        RegisteredChild() {
            super();
        }
    }

    static class LegacyParent extends ClassLoader {
        // Không gọi registerAsParallelCapable.
        LegacyParent() {
            super(ClassLoader.getSystemClassLoader());
        }

        Object lock(String name) {
            return getClassLoadingLock(name);
        }
    }

    static final class BlockedChild extends LegacyParent {
        static final boolean REGISTERED = registerAsParallelCapable();

        BlockedChild() {
            super();
        }
    }

    static final class LateAttempt extends ClassLoader {
        // Cố ý không đăng ký trong static initializer.
        LateAttempt() {
            super(ClassLoader.getSystemClassLoader());
        }

        static boolean registerTooLate() {
            return registerAsParallelCapable();
        }
    }

    public static void main(String[] args) {
        RegisteredParent parent = new RegisteredParent();
        RegisteredChild child = new RegisteredChild();
        LegacyParent legacy = new LegacyParent();
        BlockedChild blocked = new BlockedChild();
        LateAttempt late = new LateAttempt();

        System.out.println("RegisteredParent registration: "
                + RegisteredParent.REGISTERED);
        System.out.println("RegisteredChild registration: "
                + RegisteredChild.REGISTERED);
        System.out.println("LegacyParent registered: "
                + legacy.isRegisteredAsParallelCapable());
        System.out.println("BlockedChild registration: "
                + BlockedChild.REGISTERED);
        System.out.println("BlockedChild registered: "
                + blocked.isRegisteredAsParallelCapable());

        System.out.println("RegisteredChild registered: "
                + child.isRegisteredAsParallelCapable());
        System.out.println("Same name uses same lock: "
                + (child.lock("demo.A") == child.lock("demo.A")));
        System.out.println("Different names use different locks: "
                + (child.lock("demo.A") != child.lock("demo.B")));

        System.out.println("Legacy lock is loader: "
                + (legacy.lock("demo.A") == legacy));
        System.out.println("Blocked lock is loader: "
                + (blocked.lock("demo.B") == blocked));

        System.out.println("Late initially registered: "
                + late.isRegisteredAsParallelCapable());
        System.out.println("Late registration after instance: "
                + LateAttempt.registerTooLate());
        System.out.println("Late finally registered: "
                + late.isRegisteredAsParallelCapable());

        // Dùng biến parent để kiểm tra trạng thái của instance base:
        System.out.println("RegisteredParent instance registered: "
                + parent.isRegisteredAsParallelCapable());
    }
}
~~~

### 6.1. Biên dịch và chạy

Dùng JDK **17 trở lên** và chạy trong thư mục chứa `ParallelRegistrationDemo.java`:

~~~bash
javac -encoding UTF-8 ParallelRegistrationDemo.java
java ParallelRegistrationDemo
~~~

Hai lệnh hoạt động trên Bash, PowerShell và Command Prompt khi JDK có trong `PATH`; source không dùng dependency ngoài JDK và không cần `--module-path`.

### 6.2. Stdout chính xác

~~~text
RegisteredParent registration: true
RegisteredChild registration: true
LegacyParent registered: false
BlockedChild registration: false
BlockedChild registered: false
RegisteredChild registered: true
Same name uses same lock: true
Different names use different locks: true
Legacy lock is loader: true
Blocked lock is loader: true
Late initially registered: false
Late registration after instance: false
Late finally registered: false
RegisteredParent instance registered: true
~~~

**Giải thích chi tiết:**

- `RegisteredParent.REGISTERED`: `ClassLoader` registered sẵn, chưa có instance `RegisteredParent` trước khi static initializer đăng ký → `true`.
- `RegisteredChild.REGISTERED`: superclass `RegisteredParent` đã đăng ký, subclass cũng gọi sớm → `true`.
- `LegacyParent`: không gọi đăng ký, nên mặc định **không** registered dù extends `ClassLoader`.
- `BlockedChild.REGISTERED`: không có instance `BlockedChild` trước khi static initializer chạy, nhưng superclass `LegacyParent` không registered → `false`.
- `child.lock("demo.A")` được gọi hai lần trả cùng dedicated lock; `child.lock("demo.B")` trả lock khác, phù hợp default `getClassLoadingLock` cho registered loader.
- `legacy.lock(...) == legacy` và `blocked.lock(...) == blocked` chứng minh default lock trên loader object với non-parallel capable types.
- `LateAttempt` được khởi tạo bằng `new LateAttempt()` trước khi gọi `registerTooLate()`. Điều kiện “chưa có instance” đã bị vi phạm → `false`, loader type vẫn non-parallel capable.

Ví dụ **không** thực hiện benchmark: nó quan sát contract registration/lock identity, không chạy hai thread tranh chấp. Từ kết quả này không nên suy ra cải thiện throughput bao nhiêu phần trăm trên máy thật.

## 7. Một điểm tinh tế về phương thức `static` và caller

`registerAsParallelCapable()` không phải phương thức instance kiểu:

~~~java
// SAI ý nghĩa API, không phải cách bật/tắt cho object:
loader.registerAsParallelCapable();
~~~

Nó là `protected static`, phải được gọi từ phạm vi subclass phù hợp. Cách đăng ký được khuyến nghị là **gọi trực tiếp trong static initializer của chính loader type**:

~~~java
class AppPluginLoader extends ClassLoader {
    static {
        if (!registerAsParallelCapable()) {
            throw new ExceptionInInitializerError(
                    "AppPluginLoader registration failed");
        }
    }
}
~~~

Nếu gọi một **static helper do superclass định nghĩa**, đừng tự suy ra helper đó đang đăng ký subclass theo tên dynamic của object. Java `static` không có dynamic dispatch như instance method; caller của việc đăng ký cần được hiểu theo class định nghĩa lời gọi.

Đó là lý do mỗi subclass muốn parallel capable nên có bước đăng ký **riêng**, thay vì coi constructor hoặc helper chung của base class là đủ cho cả cây kế thừa.

## 8. Registration phải gắn với implementation thực sự thread-safe

Tên gọi “parallel capable” là **lời cam kết của tác giả loader** về khả năng nạp class đồng thời, không phải một phép biến đổi tự động làm mọi đoạn mã an toàn đa luồng.

Ví dụ nếu `findClass` dùng một buffer instance chung và không bảo vệ:

~~~java
// Mã minh họa nguy hiểm, không phải chương trình độc lập.
private byte[] sharedBuffer;

@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    sharedBuffer = readBytes(name); // thread khác có thể ghi đè
    return defineClass(name, sharedBuffer, 0, sharedBuffer.length);
}
~~~

Khi hai thread nạp hai tên khác nhau cùng lúc, dữ liệu `sharedBuffer` có thể bị ghi đè; class A có thể nhận bytecode B, dẫn tới `ClassFormatError`, `NoClassDefFoundError` hoặc lỗi không ổn định.

Thiết kế tốt hơn:

~~~java
// Mã minh họa; readBytes(...) do loader triển khai và có
// quyền truy cập nguồn dữ liệu an toàn khi gọi đồng thời.
@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    byte[] bytes = readBytes(name); // biến cục bộ mỗi lời gọi
    return defineClass(name, bytes, 0, bytes.length);
}
~~~

Ngoài byte array, phải kiểm tra:

- Bản đồ tên class tới bytes/URL được cache và cập nhật đồng thời.
- `JarFile`, ZIP reader, network client hoặc database connection được chia sẻ.
- State package-definition, code source, security domain, signer/sealing và metadata.
- Hàng đợi callback và event log nếu `findClass` phát event.
- Đường nạp resource và các stream đang mở khi plugin shutdown.
- Việc `close()` hoặc reload loader diễn ra đồng thời với class loading.
- Lock order khi các loader tùy chỉnh gọi chéo nhau hoặc dùng nhiều khóa.

**Không đăng ký chỉ vì muốn “nhanh hơn” nếu implementation hiện tại không thread-safe.** Chuyển từ một khóa loader toàn cục sang khóa theo tên có thể làm lộ race vốn bị serialization tình cờ che giấu.

## 9. Nếu override `loadClass` thì vẫn phải khóa đúng
`registerAsParallelCapable()` giúp triển khai **mặc định** dùng khóa theo tên; nó không chèn khóa vào bất kỳ override nào. Nếu bắt buộc viết child-first, đặt toàn bộ **`findLoadedClass` → delegation/`findClass` → `resolveClass` (khi cần)** trong `synchronized(getClassLoadingLock(name))`. Xử lý parent `null`, shared API và lỗi `ClassNotFoundException` chính xác; không nuốt `LinkageError`. Tránh `public synchronized loadClass` vì khóa toàn instance làm mất lợi ích per-name locks.
Với mục đích bổ sung nguồn bytecode, nên chỉ override `findClass` và để `loadClass` chuẩn quản lý locking/delegation.

## 10. Tại sao hai thread cùng tên vẫn cần được tuần tự hóa?

Một loader không được tự do định nghĩa cùng binary name hai lần:

~~~text
Thread A: child.loadClass("plugin.Widget")
Thread B: child.loadClass("plugin.Widget")
             │
             ▼
      Cùng lock của tên Widget
             │
             ▼
     Kiểm tra findLoadedClass
             │
             ▼
    Chỉ một định nghĩa Class<?> hợp lệ
~~~

Nếu cả hai cùng gọi `defineClass` mà không kiểm tra và phối hợp đúng, có thể gặp `LinkageError: duplicate class definition`. `registerAsParallelCapable` **không cho phép** tạo nhiều runtime type cùng tên trong **cùng defining loader**.

Hai loader instance khác nhau có thể tự định nghĩa class cùng binary name và tạo **hai runtime types khác nhau**. Đó là vấn đề class identity theo `(binary name, defining loader)`, không phải mục đích của parallel-capable registration.

## 11. Khi delegation không phân cấp
Trong đồ thị loader có gọi chéo, T1 giữ khóa A chờ B và T2 giữ khóa B chờ A có thể deadlock. Parallel-capable giảm contention khi các tên khác nhau, nhưng không chữa vòng chờ thực sự. Bài **Deadlock khi nhiều loader ủy quyền chéo** tập trung vào cách tái hiện và chẩn đoán; quy tắc đăng ký vẫn là: chọn loader hierarchy đủ điều kiện **và** làm an toàn mọi shared state.

## 12. Trạng thái có thể quan sát bằng `isRegisteredAsParallelCapable()`

Java 9+ cung cấp:

~~~java
public final boolean isRegisteredAsParallelCapable()
~~~

Phương thức là **instance API**, trả về `true` khi **class của loader object** đã đăng ký parallel capable. Đây là cách dễ kiểm tra trạng thái:

~~~java
// Mã minh họa, cần một đối tượng customLoader:
System.out.println(customLoader.isRegisteredAsParallelCapable());
~~~

Không nhầm với `registerAsParallelCapable()`, là **protected static** để đăng ký class. Sự khác biệt:

| API | Static hay instance? | Công dụng |
|---|---|---|
| `registerAsParallelCapable()` | `protected static` | Đăng ký **loader type**; thường trong static initializer |
| `isRegisteredAsParallelCapable()` | `public final` instance method | Đọc trạng thái đăng ký của **loader object/type** |
| `getClassLoadingLock(name)` | `protected` instance method | Lấy khóa mà default `loadClass` sẽ dùng |
| `loadClass(name)` | Instance method | Yêu cầu nạp class với delegation và locking |

Ví dụ `ParallelRegistrationDemo` dùng cả hai loại API để kiểm tra status và lock identity mà không cần reflection vào internals của JDK.

## 13. Những lỗi thường gặp

| Sai lầm | Hậu quả | Cách đúng |
|---|---|---|
| “`ClassLoader` registered nên mọi subclass tự registered” | Subclass vẫn non-parallel capable nếu không tự đăng ký | Đăng ký trong từng loader subclass cần parallel |
| Chỉ gọi register trong subclass, bỏ qua base loader chưa đăng ký | `registerAsParallelCapable()` trả `false` | Đăng ký toàn bộ chuỗi superclass trước |
| Gọi đăng ký sau khi đã tạo instance | Điều kiện thời điểm không còn đúng | Đăng ký ở static initializer trước khi tạo instance |
| Coi `getParent()` là superclass | Phân tích sai điều kiện đăng ký | Phân biệt type inheritance và instance delegation |
| Bỏ qua giá trị boolean của đăng ký | Tưởng đang parallel nhưng thực tế khóa toàn loader | Kiểm tra `true` và fail fast nếu cần |
| Cho rằng đăng ký làm mọi field tự thread-safe | Race khi `findClass` dùng shared state | Rà soát mutable state và concurrency |
| `synchronized loadClass(...)` trên toàn object | Serializes mọi class name dù đã registered | Dùng default `loadClass`/`getClassLoadingLock(name)` |
| Hai thread nạp cùng class name rồi define hai lần | `LinkageError` do duplicate definition | Đồng bộ theo tên và `findLoadedClass` đúng cách |
| Dùng parallel capability làm bảo đảm không deadlock | Có thể còn lock inversion ở parent/dependency | Kiểm tra topology delegation và thứ tự lock |
| Nhầm parallel loading với class initialization song song | Nhầm giai đoạn class lifecycle | Tách loading, linking và initialization |
| Benchmark thấy không nhanh hơn nên cho rằng API vô dụng | Bị giới hạn bởi I/O, parent lock hoặc workload | Quan sát contention và phân tích loader thực tế |

## 14. Khi nào nên đăng ký và khi nào không cần?

**Nên cân nhắc đăng ký** khi:

- Viết custom loader thực sự cần nạp nhiều class từ nhiều thread.
- Loader có thể được dùng bởi plugin host, application server hoặc framework có đường ủy quyền phức tạp.
- `findClass` được thiết kế thread-safe, sử dụng trạng thái/lưu trữ được đồng bộ phù hợp.
- Toàn bộ superclass chain đã registered và bạn kiểm soát được vòng đời static initialization.

**Không nhất thiết tự đăng ký** khi:

- Chỉ sử dụng loader chuẩn đã hỗ trợ việc cần làm; ví dụ `URLClassLoader` có thiết kế/đăng ký của JDK, không cần tạo subclass chỉ để “bật parallel”.
- Viết ứng dụng Java bình thường không tạo subclass của `ClassLoader`.
- Chưa kiểm chứng thread safety, đặc biệt nếu loader dùng shared byte buffer, resource handles hoặc cache mutable.
- Một superclass trong thư viện cũ không registered và bạn không thể thay class đó; gọi đăng ký ở subclass vẫn thất bại.

Nếu đơn giản chỉ cần nạp plugin JAR, ưu tiên `URLClassLoader` hoặc module/plugin mechanism phù hợp trước khi tự thiết kế ClassLoader cùng concurrency.

## 15. Lời khuyên kiểm thử concurrency

Trước khi đưa custom loader parallel capable vào production:

1. Xác nhận `registerAsParallelCapable()` **trả `true`** ở mỗi loader subclass cần thiết.
2. Xác nhận instance trả `isRegisteredAsParallelCapable() == true`.
3. Kiểm thử nạp **hai tên class khác nhau** đồng thời, và cả **cùng tên class** từ nhiều thread.
4. Kiểm tra không có `LinkageError` do define cùng tên, không có dữ liệu class bị lẫn giữa requests.
5. Kiểm thử các dependency cross-loader có thể tạo vòng chờ, với timeout và thread dump khi cần.
6. Rà soát `findClass`, `findResource(s)`, cache, filesystem/JAR/network read, close/shutdown lifecycle.
7. Không dùng số mili-giây của một lần chạy để kết luận thread safety hoặc mức tăng hiệu năng.
8. Nếu có lỗi, phân biệt **deadlock**, contention chậm, class initialization lock, I/O lock và circular class dependency.
9. Tránh đo đạc bằng cách override synchronization ngoài ý muốn làm thay đổi tính chất cần kiểm chứng.

**Giám sát:** thread dump, Java Flight Recorder, class-loading logs và các phép đo contention có thể giúp xác định nút thắt; không cần phỏng đoán dựa trên một console log.

## 16. Câu hỏi tự kiểm tra

**1. Điều kiện nào phải đúng để `registerAsParallelCapable()` thành công?**

**Đáp án:** Chưa có instance của caller loader class và mọi superclass loader của class đó (trừ `Object`) đã đăng ký parallel capable.

**2. `ClassLoader` đã registered. Vì sao subclass `MyLoader` vẫn cần đăng ký?**

**Đáp án:** Registration không tự kế thừa. Mỗi subclass có ý định parallel capable cần đăng ký riêng và thỏa điều kiện hierarchy.

**3. `Child extends LegacyParent`, LegacyParent không registered. Child gọi đăng ký trong static initializer; kết quả?**

**Đáp án:** `false` dù Child chưa có instance, vì superclass chưa registered.

**4. Một registered loader nạp `A` và `B`. Default lock cho hai tên có giống nhau không?**

**Đáp án:** Không. Default `getClassLoadingLock` trả khóa riêng theo class name; hai lời gọi cùng tên được phối hợp bằng cùng khóa.

**5. `registerAsParallelCapable()` có giúp code `findClass` dùng chung mutable byte buffer trở nên thread-safe không?**

**Đáp án:** Không. Tác giả loader phải tự bảo đảm shared state được truy cập an toàn. Registration chỉ ảnh hưởng cơ chế khóa loading của `ClassLoader`.

**6. Muốn bật parallel cho một loader object đã tồn tại bằng cách gọi instance method có được không?**

**Đáp án:** Không. Registration là static theo loader type và phải xảy ra khi chưa có instance; `isRegisteredAsParallelCapable()` chỉ dùng để kiểm tra trạng thái.

**7. Nếu parent ClassLoader instance là platform loader, superclass không registered có được bỏ qua không?**

**Đáp án:** Không. Điều kiện đăng ký xét **superclass của loader class**, không xét object parent được truyền vào constructor.

## 17. Tài liệu đối chiếu

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): tổng quan parallel capable loaders, `registerAsParallelCapable`, `isRegisteredAsParallelCapable`, `getClassLoadingLock` và `loadClass`.
- [Java SE 17 — registerAsParallelCapable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#registerAsParallelCapable()): điều kiện “chưa có instance” và “mọi superclass đã registered”.
- [Java SE 17 — getClassLoadingLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#getClassLoadingLock(java.lang.String)): khóa toàn loader so với khóa theo tên.
- [Java SE 17 — isRegisteredAsParallelCapable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#isRegisteredAsParallelCapable()): kiểm tra trạng thái ở runtime.
- [JVMS 17 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): class identity, loading và linking.

**Ghi nhớ:** `registerAsParallelCapable()` phải được gọi **sớm trong static initializer của từng loader subclass**; chỉ thành công khi chưa có instance của caller và **mọi superclass loader đã registered**. Nó cho default `loadClass` dùng **khóa theo tên class** thay vì một khóa chung cho loader, nhưng **không tự làm custom loader thread-safe** và không bảo đảm mọi class loading sẽ chạy song song hoặc không deadlock.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-class-loading-005">Tránh race condition giữa findLoadedClass và defineClass</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh race condition giữa `findLoadedClass` và `defineClass`

> **Java Core → ClassLoader → Nạp đồng thời.** Bài học tập trung vào một lỗi rất cụ thể của custom class loader: **hai thread cùng thấy một binary name chưa được nạp và cùng cố định nghĩa class ấy trong cùng một loader**. Mục tiêu là hiểu bất biến cần giữ, cách `ClassLoader.loadClass` mặc định phối hợp concurrency, khi nào dùng `getClassLoadingLock`, `registerAsParallelCapable`, cách tự tái hiện lỗi có kiểm soát, và cách kiểm thử khi triển khai plugin/child-first loader.

## 1. Bất biến quan trọng nhất

Với một runtime class/interface thông thường, định danh kiểu dựa trên cặp **(binary name, defining class loader)**. Đối với mỗi binary name, một defining loader phải tránh cố `defineClass` cùng class **hai lần** trong namespace của chính nó. Việc nạp một tên bằng hai thread cùng lúc phải đưa đến **cùng một đối tượng `Class<?>`** nếu cả hai thành công, thay vì hai lần định nghĩa trùng tên.

Các API:

- `findLoadedClass(String name)`: trả `Class<?>` đã được VM ghi nhận với loader này là **initiating loader** của tên tương ứng, hoặc `null` nếu chưa thấy. Không phải phép **giữ chỗ** hay lệnh khóa tên class.
- `defineClass(String name, byte[] b, int off, int len)`: chuyển bytecode thành runtime `Class<?>` được loader hiện tại định nghĩa. Định nghĩa trùng binary name trong cùng loader không được phép; JVM thường ném `LinkageError`.
- `loadClass(String name, boolean resolve)`: theo implementation mặc định, làm một chuỗi thao tác gồm kiểm tra đã nạp → delegation tới parent → `findClass` nếu cần → `resolveClass` khi flag `resolve=true`, được đồng bộ bằng `getClassLoadingLock(name)`.
- `getClassLoadingLock(String name)`: cung cấp lock phù hợp cho **toàn bộ critical section** của một yêu cầu nạp class. Nếu loader **không parallel capable**, lock là chính loader; nếu đã đăng ký parallel capable, thường là lock riêng cho binary name.
- `registerAsParallelCapable()`: khai báo subclass hỗ trợ nạp class đồng thời với locking đúng. Nó **không tự sửa một override `loadClass` thiếu đồng bộ**.

**Ý chính:** `findLoadedClass(name) == null` tại một thời điểm **không cấp độc quyền** gọi `defineClass(name, ...)`. Check và define phải được phối hợp trong một giao thức khóa/ownership nhất quán.

## 2. Race check-then-act xảy ra thế nào?

Mẫu sai:

~~~java
// CHỈ MINH HỌA LỖI: nằm trong subclass ClassLoader.
// Không dùng pattern này trong loader production.
Class<?> c = findLoadedClass(name);
if (c == null) {
    byte[] bytes = fetchClassBytes(name);
    c = defineClass(name, bytes, 0, bytes.length);
}
return c;
~~~

`fetchClassBytes` là hàm giả định. Khi hai thread đi vào `loadClass` của **cùng một loader instance**, có thể xảy ra:

~~~text
                 Thread T1                       Thread T2
                    |                               |
findLoadedClass(X) -> null                         |
                    |               findLoadedClass(X) -> null
                    |                               |
       fetch bytes(X)                       fetch bytes(X)
                    |                               |
         defineClass(X) OK                    |
                    |                          defineClass(X)
                    |                               |
                    |                            LinkageError
                    v
          Class<?> X đã tồn tại
~~~

Đây là race **time-of-check to time-of-use (TOCTOU)**: kết quả `null` chỉ đúng tại thời điểm đọc; hai thread không được phân công ai là **người duy nhất** định nghĩa class.

**Không coi `LinkageError` là cách đồng bộ thay thế.** JVM bảo vệ bất biến định danh kiểu bằng cách từ chối việc định nghĩa trùng, nhưng ứng dụng có thể thất bại ngẫu nhiên dưới tải. Đặc biệt, không nên bắt `LinkageError` rồi coi như `ClassNotFoundException` và âm thầm thử loader khác: như thế có thể che lấp bytecode hỏng, loader constraints hoặc version conflict.

### 2.1. Điều không giúp sửa race

- `volatile boolean loaded` không làm chuỗi “check → bytes → define” trở thành atomic.
- `ConcurrentHashMap<String, byte[]>` chỉ làm map bytes thread-safe; nó không tự bảo vệ thao tác `defineClass` nếu hai bên đồng thời đi qua check.
- Gọi `Thread.sleep(...)` để giãn thời điểm không phải fix; nó chỉ thay xác suất xuất hiện lỗi.
- Chỉ đặt `synchronized` trên `findClass` không chắc đủ nếu nơi khác gọi `defineClass` ngoài cùng lock, hoặc `loadClass` tự gọi recursion/delegation sai.
- Đăng ký `registerAsParallelCapable` nhưng tự override `loadClass` và bỏ `synchronized(getClassLoadingLock(name))` vẫn sai.
- `resolveClass(c)` chỉ link class; nó không thay thế việc bảo vệ `defineClass` và không chạy initializer.

## 3. Cách mặc định của `ClassLoader` giải quyết vấn đề

Java `ClassLoader` khuyến nghị **override `findClass` khi có thể**, không override toàn bộ `loadClass`. Theo Javadoc, implementation mặc định của `loadClass(name, resolve)` về cơ bản:

~~~java
// PSEUDOCODE: minh họa ý tưởng; không phải source để copy.
synchronized (getClassLoadingLock(name)) {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
        try {
            c = parentLoadClass(name);
        } catch (ClassNotFoundException notInParent) {
            // Sẽ tìm trong loader hiện tại.
        }
        if (c == null) {
            c = findClass(name);
        }
    }

    if (resolve) {
        resolveClass(c);
    }
    return c;
}
~~~

`parentLoadClass` ở đây là **ký hiệu giả** để giải thích delegation tới parent/VM bootstrap; không phải method trong API `ClassLoader`. Điểm học cần giữ là **toàn bộ** check, delegation, find và optional link nằm dưới **cùng loading lock**.

Với hai thread cùng yêu cầu binary name X:

1. T1 lấy lock của X.
2. T2 chờ lock của X, thay vì cũng thấy `findLoadedClass(X)==null` bên trong cùng vùng bảo vệ.
3. T1 tìm class từ parent hoặc gọi `findClass` để tạo một class, rồi trả.
4. T2 lấy lock, kiểm tra lại `findLoadedClass(X)` sau khi T1 hoàn thành và nhận đúng class đã được tải/ghi nhận.
5. Cả hai thành công trả **cùng `Class<?>`**. Nếu có lỗi thực về bytecode/parent/linking, lỗi thích hợp vẫn phải được xử lý, không bị khóa “nuốt” đi.

Đây là lý do các bước **check và define** phải giữ nguyên trong critical section, không phải:

~~~java
// SAI: check ở ngoài khóa và không recheck trước define.
Class<?> c = findLoadedClass(name);
if (c == null) {
    synchronized (getClassLoadingLock(name)) {
        c = defineClass(name, bytes, 0, bytes.length);
    }
}
~~~

Cả T1 và T2 có thể đã thấy `null` trước lock; khi mỗi thread lần lượt lấy lock, T2 vẫn gọi `defineClass` lần nữa. Nếu dùng mô hình double-check, **bắt buộc check lại** bên trong đúng lock, nhưng với custom classloader thường viết nguyên đoạn dưới lock rõ ràng hơn.

## 4. Cách an toàn nhất: chỉ override `findClass`

Nếu chỉ có nhu cầu đọc bytecode từ thư mục/JAR/service riêng, hãy giữ `loadClass` mặc định:

~~~java
public final class BytesLoader extends ClassLoader {
    public BytesLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        byte[] bytes = readBytesFromTrustedStore(name);
        return defineClass(name, bytes, 0, bytes.length);
    }

    private byte[] readBytesFromTrustedStore(String name)
            throws ClassNotFoundException {
        throw new ClassNotFoundException(name); // Minh họa điểm mở rộng.
    }
}
~~~

Tệp `BytesLoader.java` trên Java 17+ biên dịch được, nhưng method `readBytesFromTrustedStore` được để ở trạng thái **chỉ minh họa**: nó luôn báo chưa tìm thấy, không phải implementation load bytes thật. Đối với project thực, thay method này bằng logic đọc bytes theo binary name, xác minh nguồn, đóng streams và chuyển lỗi I/O phù hợp.

Lợi ích: `ClassLoader.loadClass` mặc định đã quản lý locking theo `getClassLoadingLock` và gọi `findLoadedClass`. Bạn ít phải tự viết các chi tiết concurrency nguy hiểm.

Tuy nhiên **không** gọi trực tiếp `customLoader.findClass(name)` từ phần còn lại của ứng dụng để “bỏ qua delegation”: `findClass` là protected hook, không phải public entrypoint chuẩn. Kể cả bạn public hóa một wrapper gọi thẳng `findClass`, nó có thể **bỏ qua locking và kiểm tra đã nạp** nếu thiết kế sai. Phía caller nên dùng `loadClass(name)`.

## 5. Nếu bắt buộc override `loadClass`: khóa quanh toàn bộ check/define

Một child-first loader có thể cần load package sở hữu trước khi delegate; phải đặc biệt thận trọng. Skeleton:

~~~java
public final class PackageLoader extends ClassLoader {
    static {
        if (!registerAsParallelCapable()) {
            throw new ExceptionInInitializerError(
                    "parallel-capable registration failed");
        }
    }

    public PackageLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);

            if (c == null) {
                if (name.startsWith("myplugin.impl.")) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException missingLocally) {
                        c = super.loadClass(name, false);
                    }
                } else {
                    c = super.loadClass(name, false);
                }
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        throw new ClassNotFoundException(name); // Tìm bytes tại đây.
    }
}
~~~

Tệp `PackageLoader.java` là **skeleton biên dịch được Java 17+** nhưng `findClass` chỉ là placeholder hành vi (luôn ném `ClassNotFoundException`) nên không nạp plugin thật. Phần đáng học là khóa theo `getClassLoadingLock(name)` và check `findLoadedClass` **bên trong** critical section.

**Lưu ý đặc biệt về child-first + parallel loading:**

- `registerAsParallelCapable()` phải được gọi trong quá trình **class initialization của chính subclass**, trước khi tạo instance; mọi superclass classloader cũng phải đăng ký phù hợp. `ClassLoader` gốc đã đăng ký, nhưng một superclass custom trung gian có thể chưa đăng ký, làm registration của child thất bại.
- Nếu loader **chưa parallel capable**, `getClassLoadingLock(name)` trả chính loader object. Khi đăng ký thành công, lock theo name giúp tải **các tên khác nhau** đồng thời, nhưng với **cùng name** vẫn phải tuần tự.
- Không nên dùng `synchronized(this)` nếu mục đích là nạp song song các tên khác nhau: khóa toàn loader có thể làm giảm concurrency hoặc gây deadlock khi delegation graph không thuần thứ bậc. Tuy vậy dùng khóa chung vẫn có thể phù hợp nếu loader không hỗ trợ parallel loading.
- `getClassLoadingLock` là protected, và trả lock khác nhau tùy loại loader; không lấy lock vào một map riêng không đồng bộ với các đường nạp khác.
- `super.loadClass(name,false)` ở skeleton là cách dùng semantics parent-first mặc định khi đã quyết định fallback, nhưng phải đảm bảo không tạo vòng delegation giữa các loader và không có code tự gọi lại vòng không kết thúc.
- Catch **chỉ `ClassNotFoundException`** khi cần fallback có chủ đích. Không catch mọi `Throwable`/`LinkageError` để giả vờ class không tồn tại.
- `resolveClass(c)` thực hiện linking nếu `resolve=true`, **không** làm `<clinit>` chạy; static initialization là một giai đoạn khác.

Nếu không có yêu cầu child-first rất rõ, ưu tiên `findClass` với implementation `loadClass` mặc định.

## 6. Thí nghiệm reproducible: cố tạo race và sửa bằng lock đúng

Thí nghiệm dùng **hai file Java 17+**. Chúng ta dùng cùng bytecode của `PluginTask` nhưng tạo các loader riêng để thử hai chính sách. `UnsafeLoader` cố ý sai và đặt `CyclicBarrier` sau `findLoadedClass` để **hai thread đều kiểm tra trước khi ai gọi `defineClass`**; đây là kỹ thuật tạo race có kiểm soát trong bài lab, không dành cho production.

### 6.1. File `PluginTask.java`

~~~java
public class PluginTask {
    public String result() {
        return "ok";
    }
}
~~~

Class này nằm ở default package để lệnh chạy đơn giản. Nó không chủ động tạo threads, không cần dependency ngoài JDK.

### 6.2. File `RaceDemo.java`

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RaceDemo {
    private static final String TARGET = "PluginTask";

    // SAI CÓ CHỦ Ý: thiếu class-loading lock.
    static class UnsafeLoader extends ClassLoader {
        private final byte[] bytes;
        private final CyclicBarrier twoChecks = new CyclicBarrier(2);

        UnsafeLoader(byte[] bytes) {
            super(RaceDemo.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class<?> found = findLoadedClass(name);
            if (found == null) {
                try {
                    // Cả hai threads tới đây khi class vẫn chưa define.
                    twoChecks.await();
                } catch (Exception ex) {
                    throw new ClassNotFoundException(name, ex);
                }
                found = defineClass(name, bytes, 0, bytes.length);
            }

            if (resolve) {
                resolveClass(found);
            }
            return found;
        }
    }

    // ĐÚNG: hai yêu cầu cùng tên chỉ một thread được define.
    static class SafeLoader extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "parallel registration failed");
            }
        }

        private final byte[] bytes;

        SafeLoader(byte[] bytes) {
            super(RaceDemo.class.getClassLoader());
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    found = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(found);
                }
                return found;
            }
        }
    }

    static void test(String label, ClassLoader loader)
            throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Class<?>> request = () -> {
            start.await();
            return loader.loadClass(TARGET);
        };

        try {
            Future<Class<?>> first = workers.submit(request);
            Future<Class<?>> second = workers.submit(request);
            start.countDown();

            int successes = 0;
            int linkageErrors = 0;
            Class<?> firstResult = null;
            boolean sameClass = true;

            for (Future<Class<?>> f :
                    java.util.List.of(first, second)) {
                try {
                    Class<?> result = f.get();
                    successes++;
                    if (firstResult == null) {
                        firstResult = result;
                    } else {
                        sameClass &= firstResult == result;
                    }
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof LinkageError) {
                        linkageErrors++;
                    } else {
                        throw ex;
                    }
                }
            }

            System.out.println(label + ": success=" + successes
                    + ", linkageError=" + linkageErrors
                    + ", sameClass=" + sameClass);
        } finally {
            workers.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("PluginTask.class"));
        test("unsafe", new UnsafeLoader(bytes));
        test("safe", new SafeLoader(bytes));
    }
}
~~~

**Lệnh** từ thư mục chứa cả hai source:

~~~bash
javac --release 17 PluginTask.java RaceDemo.java
java -cp . RaceDemo
~~~

**Output kỳ vọng khi `UnsafeLoader` bị ép qua barrier**:

~~~text
unsafe: success=1, linkageError=1, sameClass=true
safe: success=2, linkageError=0, sameClass=true
~~~

Ở dòng unsafe, một thread thành công, một thread bị VM từ chối do duplicate definition; giá trị `sameClass=true` chỉ vì **một** thread trả được `Class` nên không có hai kết quả thành công để so sánh. Ở dòng safe, cả hai đều thành công và trả **đúng cùng `Class<?>`**, vì thread thứ hai thấy class đã nạp dưới lock.

**Giới hạn thí nghiệm:**

- `CyclicBarrier` trong unsafe chỉ để ép đúng race; nếu thay bằng `sleep` hay `yield`, output có thể ngẫu nhiên, không chứng minh nguyên nhân.
- Mã intentionally bỏ parent-first cho **chính xác tên `PluginTask`** để loader tự định nghĩa class; mọi tên khác đi qua `super.loadClass`. Không dùng cách này cho toàn bộ `java.*` hoặc shared API packages.
- `PluginTask.class` có thể nằm trên application classpath vì `javac` tạo file; override riêng một binary name khiến loader thí nghiệm vẫn tự define. Không mở rộng kết luận rằng child-first toàn hệ thống là chính sách an toàn.
- File `PluginTask.class` cần tồn tại tại **current working directory** khi chạy. Lệnh trên đáp ứng điều đó nếu dùng đúng cấu trúc.
- Việc đếm `LinkageError` chỉ phục vụ minh họa; code production nên xử lý lỗi đúng loại, không bỏ qua.
- Khi đo performance/concurrency thật, phải kiểm tra nguồn bytes, I/O, delegation, class initialization và JIT warm-up; bài lab này chỉ cô lập check-then-define race.

### 6.3. Vì sao `findLoadedClass` vẫn cần nằm trong lock?

Giả sử `SafeLoader` bỏ bước `findLoadedClass` bên trong lock:

~~~java
synchronized (getClassLoadingLock(name)) {
    return defineClass(name, bytes, 0, bytes.length);
}
~~~

Cả T1 và T2 có thể lần lượt đi qua lock rồi **cả hai đều gọi `defineClass`**. Lock chỉ bảo đảm không đồng thời, **không** tự bảo đảm class chỉ được define một lần. Phải thực hiện **check lại khi giữ khóa**, và trả `Class<?>` đã có.

Ngược lại, nếu chỉ kiểm tra ngoài lock rồi vào lock nhưng không check lại, bạn vẫn bị duplicate definition. Cặp **same lock + recheck** mới giải quyết đúng vấn đề.

## 7. Race cùng tên khác với nạp nhiều tên song song
`getClassLoadingLock(name)` bảo vệ từng binary name khi loader đã parallel-capable; nếu subclass chưa đăng ký, default lock là chính loader. Điều kiện đăng ký là **trước khi tạo instance** và **mọi superclass loader phải registered**. Hai tên X/Y có thể tiến hành đồng thời, nhưng hai thread cùng tên X vẫn phải có **lock + `findLoadedClass` bên trong lock**.
```text
T1: lock(X) → findLoadedClass(X)==null → defineClass(X) → unlock
T2: đợi lock(X) → findLoadedClass(X)==Class X → reuse
```
Nếu loader A nạp X trong lúc chờ loader B nạp Y, và B lại chờ A, có thể xuất hiện deadlock kể cả dùng per-name locks. Hãy thiết kế graph delegation có điểm kết thúc. Đây là vấn đề riêng với race check-then-define mà bài đang xử lý.

## 8. Phân biệt race định nghĩa class với race khởi tạo class

**Loading/linking** và **initialization** là hai giai đoạn khác nhau:

- Hai thread đồng thời gọi `loadClass("X")` cần điều phối để **một runtime `Class`** được trả và không duplicate `defineClass`.
- Hai thread đồng thời thực hiện active use (ví dụ `new X()` hoặc static nonconstant field) được JVM phối hợp **class initialization** theo JLS `12.4.2`. `<clinit>` của một runtime class thành công chỉ chạy một lần, và thread khác chờ khi cần.
- `resolveClass(c)` chỉ yêu cầu linking, **không thay thế** cả hai cơ chế ở trên.
- `synchronized (getClassLoadingLock(name))` trong custom loader giải quyết class definition/lookup race, **không** phải lock bạn nên dùng để kiểm soát mutable static state sau initialization.

Vì vậy có thể có code load class thread-safe nhưng service bên trong class vẫn có **data race** ở các static fields khi update sau init. Hai loại lỗi dùng biện pháp khác nhau.

## 9. Identity, initiating loader và parent-first

`findLoadedClass` trả về class mà loader hiện tại đã được ghi nhận là **initiating loader**, không nhất thiết loader đó trực tiếp define class. Nếu loader L delegate tên `java.lang.String` lên bootstrap, `String.class.getClassLoader()` thường trả `null`; L vẫn có thể tham gia initiating loader record.

**Không tự define lại class đã được parent cung cấp** chỉ vì bạn muốn child-first toàn cục; điều này có thể phá type identity. Với plugin system:

- Các API chung như `host.api.PluginContract` nên được định nghĩa ở **shared parent**.
- Implementation riêng `plugin.impl.Handler` có thể do plugin loader định nghĩa.
- Nếu hai plugin loaders cùng define `plugin.impl.Handler`, đó là **hai runtime types** khác nhau, hợp lệ khi chủ đích là plugin isolation.
- Nhưng một loader **không được define hai phiên bản của cùng binary name** chỉ để hot reload. Tạo loader thế hệ mới, chuyển request routing có kiểm soát, dọn loader cũ khi không còn references.

Kiểm thử type contract bằng `sharedTypeFromHost == sharedTypeFromPlugin` và `SharedApi.class.isAssignableFrom(implType)`; đừng dùng `getName().equals` làm bằng chứng type compatible.

## 10. Một số lỗi cạnh tranh khác trong custom classloader

### 10.1. Cache bytes an toàn khác cache Class an toàn

Có thể dùng immutable `byte[]` được đọc trước, hoặc map cache bytes thread-safe để giảm I/O, nhưng **đảm bảo một người định nghĩa class** vẫn cần đúng lock. Nếu bytes có thể bị cập nhật đồng thời, bạn còn phải đảm bảo mỗi lần định nghĩa đọc **một bản nguyên vẹn**, không dùng `byte[]` bị mutate sau khi verify/checksum.

### 10.2. Đồng thời đóng loader và nạp class

Với `URLClassLoader.close()`, Javadoc không bảo đảm kết quả class loading khi `close` chạy cạnh tranh với `loadClass`. Đây là race **lifecycle** khác với duplicate `defineClass`. Cần ngăn công việc mới, drain tasks đang chạy, sau đó close loader; khóa `getClassLoadingLock(name)` chỉ bảo vệ loading, không tự quản lý toàn bộ vòng đời plugin.

### 10.3. Exception classification

| Lỗi | Ý nghĩa thường gặp | Không nên làm |
| --- | --- | --- |
| `ClassNotFoundException` | Loader không tìm class theo tên được yêu cầu | Nuốt lỗi rồi trả `null` từ `loadClass` |
| `LinkageError` khi define trùng | Bug phối hợp nạp hoặc xung đột runtime identity | Bắt lỗi để giả vờ chưa tìm thấy |
| `ClassFormatError`/`VerifyError` | Bytecode không đúng hoặc vi phạm verification | Retry vô hạn với cùng bytes |
| `UnsupportedClassVersionError` | Bytecode quá mới so với runtime | Dùng khóa thay vì sửa runtime/target |
| `ClassCircularityError` | Quan hệ superclass/interface vòng không hợp lệ | Bỏ khóa để cố vượt qua |
| `NoClassDefFoundError` | Thiếu class runtime, initialization trước đó thất bại hoặc nguyên nhân linkage | Kết luận ngay rằng chỉ thiếu JAR |

**Lưu ý:** Các lỗi ở bảng không buộc xuất hiện tại một thời điểm duy nhất do JVM có thể eager/lazy link/resolve. Hãy đọc nguyên nhân gốc và log ban đầu.

### 10.4. Thử đăng ký parallel capable nhưng không kiểm tra kết quả

`registerAsParallelCapable()` trả boolean. Nếu false, `getClassLoadingLock(name)` có thể không cho per-name lock bạn mong đợi. Không khẳng định performance song song chỉ vì có static call này; kiểm tra điều kiện superclass hierarchy và test.

### 10.5. Đổi chính sách nạp khi đang chạy

Một loader đã trả/define `foo.Bar` không thể “định nghĩa lại” `foo.Bar` ngay trong cùng namespace khi file JAR đổi trên disk. Nên coi package ownership/delegation policy là **bất biến** cho đời sống của loader, không để một thread đổi map prefix giữa lúc thread khác tìm class.

## 11. Test thực tế ngoài bài lab

Một bản custom loader production nên có các kiểm thử:

1. **Concurrent same name:** khởi tạo một loader mới, dùng `CountDownLatch` phát nhiều tác vụ đồng thời cùng gọi `loadClass(X)`; xác minh tất cả thành công, đối tượng `Class<?>` cùng identity.
2. **Concurrent different names:** dùng nhiều classes `X,Y,Z` và kiểm tra loader parallel capable cho phép nạp hợp lệ không deadlock; không đánh giá bằng output order cố định.
3. **Duplicate name fail-fast:** cố ý tạo broken implementation trong test riêng; assert nó thất bại thay vì chôn lỗi.
4. **Delegation:** shared API do parent define; plugin class do child define; `Class.getClassLoader()` và `isAssignableFrom` đúng.
5. **I/O errors:** bytes thiếu/hỏng, JAR đang đóng, mismatch name trong class file; exception được phân loại đúng.
6. **Lifecycle:** stop plugin trong lúc có worker; chắc chắn không đóng URLClassLoader trước khi in-flight loads kết thúc.
7. **Repeated reload:** tạo loader mới theo mỗi generation, không giữ TCCL/thread cache/host registry làm loader cũ còn reachable.
8. **Module/access:** nếu dùng JPMS, test readability/exports/opens; khóa đúng không sửa được module access violation.
9. **Stress:** chạy nhiều vòng, bật logging và kiểm tra stack traces; không dựa trên một lần test race không tái hiện.
10. **Timeout/deadlock:** test cancellation, lấy thread dump, tránh treo vô thời hạn trong CI.

### 11.1. Không sử dụng lệnh ngủ để “kiểm thử race”

Một chương trình chạy 100.000 lần không thấy lỗi **không chứng minh** code không race; scheduler có thể chưa tạo interleaving bất lợi. Dùng barrier trong test như `RaceDemo` buộc hai thread cùng đi qua check để minh họa lỗi một cách có kiểm soát. Với production, dựa vào **bất biến và khóa đúng**, rồi dùng stress/observability như bổ trợ.

### 11.2. Quan sát bằng công cụ JDK

~~~bash
java -version
javap -c -p RaceDemo
java -Xlog:class+load=info -cp . RaceDemo
~~~

`-Xlog` là chẩn đoán của JVM hỗ trợ unified logging như HotSpot; đầu ra và tag support phụ thuộc runtime. Khi nghi deadlock, có thể lấy thread dump (`jcmd <pid> Thread.print` nếu runtime hỗ trợ) và xem thread nào đang giữ loading lock; không thay bằng parse thứ tự `class+load` log như một contract của JLS.

## 12. Checklist khi review `loadClass`

- [ ] Có cần override `loadClass` không, hay chỉ `findClass` là đủ?
- [ ] Mỗi lần nạp cùng name có dùng đúng `getClassLoadingLock(name)`?
- [ ] `findLoadedClass(name)` **nằm trong critical section**, và được kiểm tra trước khi gọi `defineClass`?
- [ ] Có bỏ việc recheck bên trong lock không? Nếu có, sửa.
- [ ] `resolveClass(c)` chỉ gọi khi `resolve=true` và class token đã hợp lệ?
- [ ] Parent delegation/child-first package policy nhất quán, không tạo vòng?
- [ ] Có catch rộng `Throwable`/`LinkageError` rồi retry mù quáng không?
- [ ] `registerAsParallelCapable` được gọi đúng thời điểm và superclass hierarchy hỗ trợ?
- [ ] Các task load class đồng thời với close/unload được phối hợp lifecycle?
- [ ] Test nhiều threads cùng name trả đúng **một `Class<?>`**?
- [ ] Shared API không bị custom loader define lại?
- [ ] Code có phân biệt class loading, linking và initialization không?

## 13. Câu hỏi tự kiểm tra

1. Vì sao `findLoadedClass(name)==null` không đảm bảo thread được gọi `defineClass`? **Vì thread khác có thể define cùng tên ngay sau check.**
2. Vì sao `synchronized` chỉ quanh `defineClass` vẫn có thể sai? **Nếu không recheck dưới lock, thread thứ hai vẫn define trùng khi đến lượt.**
3. API nào cấp lock cho đúng tên? **`getClassLoadingLock(name)`.**
4. Không parallel capable, lock ấy là gì? **Chính ClassLoader object** theo API.
5. Parallel capable, hai class khác tên có thể load đồng thời không? **Có**, theo per-name locks nhưng không bảo đảm hoàn toàn không chờ do dependencies.
6. Có nên override toàn bộ `loadClass` nếu chỉ cần đọc bytes riêng? **Thường không; override `findClass`**.
7. `registerAsParallelCapable` có tự thêm synchronization vào override sai? **Không**.
8. Khi hai threads định nghĩa cùng binary name trong một loader, lỗi nào thường thấy? **`LinkageError`** do duplicate definition.
9. Khi hai loaders khác nhau cố ý định nghĩa cùng binary name để plugin isolation, có luôn là lỗi? **Không**, vì runtime identity khác; vấn đề sẽ phát sinh nếu phải chia sẻ type qua boundary.
10. `resolveClass` có chạy `static{}` không? **Không**, nó link chứ không initialize.
11. Vì sao `ClassNotFoundException` và `LinkageError` cần xử lý khác? **Một cái có thể là lookup miss, cái kia thường là vấn đề verification/binary identity/lỗi trong liên kết.**
12. Đóng `URLClassLoader` trong lúc worker đang nạp class có an toàn mặc định không? **Không**, phải phối hợp shutdown.
13. Test có barrier buộc hai threads cùng thấy null giúp gì? **Tái hiện TOCTOU, thay vì phụ thuộc xác suất scheduler.**

## 14. Tài liệu chuẩn

- [ClassLoader — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findClass`, `findLoadedClass`, `defineClass`, `getClassLoadingLock`, `registerAsParallelCapable`; implementation mặc định đồng bộ trên class-loading lock.
- [ClassLoader — Java SE 26 API](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/ClassLoader.html): hợp đồng parallel loading và locking được duy trì trên JDK mới.
- [JVMS §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): defining/initiating loader, runtime type identity, loading constraints.
- [JVMS §5.4 — Linking](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4): verification, preparation, resolution.
- [JLS §12.4 — Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4): initialization được phối hợp giữa threads, khác với duplicate class definition.
- [CyclicBarrier — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CyclicBarrier.html) và [CountDownLatch](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/CountDownLatch.html): công cụ ép interleaving trong test concurrency.

**Kết luận:** `findLoadedClass` chỉ **quan sát trạng thái**, `defineClass` **thay đổi trạng thái**. Hai bước phải nằm trong cùng cơ chế **locking + recheck** cho từng tên class. Cách an toàn nhất thường là giữ `loadClass` mặc định và chỉ override `findClass`; nếu bắt buộc tự override, dùng `getClassLoadingLock(name)` cho **toàn bộ** check/delegation/define/resolve và kiểm tra `registerAsParallelCapable` đúng điều kiện. 


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-class-loading-006">Deadlock khi nhiều loader ủy quyền chéo</a>

<details>
<summary>Xem chi tiết</summary>

# Deadlock khi nhiều loader ủy quyền chéo

Một `ClassLoader` tùy chỉnh có thể hỏi class loader khác để tìm một class. Điều này cần thiết trong hệ plugin, application server, OSGi hoặc những kiến trúc có nhiều không gian nạp lớp. Nhưng nếu **loader A đang giữ khóa nạp lớp của mình và gọi loader B**, đồng thời **loader B đang giữ khóa của mình và gọi ngược loader A**, hai thread có thể chờ nhau mãi: **deadlock**.

Khác với `ClassNotFoundException` (tìm không thấy class) hay `ClassCastException` (không tương thích kiểu), deadlock thường khiến chương trình **treo tại bước nạp lớp**, không tiến đến câu lệnh cần thực thi, đôi khi không in exception. Sự cố có thể chỉ xuất hiện khi tải plugin đồng thời hoặc khi startup được tối ưu chạy nhiều thread.

Trọng tâm của bài là **quan hệ giữa class-loading lock, thứ tự ủy quyền và đồ thị phụ thuộc loader**. Cần hiểu rõ cả rủi ro ở loader thông thường (khóa toàn loader) và loader **parallel-capable** (khóa theo tên class): đăng ký parallel-capable có thể giảm tranh chấp không cần thiết, nhưng **không tự khắc phục một chu trình ủy quyền thực sự**.

## 1. Khóa nào góp phần tạo deadlock?
`ClassLoader.loadClass(name, resolve)` mặc định đồng bộ trên `getClassLoadingLock(name)` suốt chu trình `findLoadedClass` → parent/bootstrap → `findClass` → optional `resolveClass`. Loader chưa đăng ký parallel-capable dùng **monitor trên chính loader instance**; loader đã đăng ký dùng **khóa theo từng binary name**. Cả hai vẫn phải phối hợp để không `defineClass` hai lần cùng tên.
Deadlock xảy ra khi **hai thread giữ hai khóa mà bên kia cần**. Trong mô hình ủy quyền chéo, request có thể đi từ loader A sang B trong lúc vẫn giữ khóa A, và từ B quay lại A trong lúc giữ khóa B. Những mục tiếp theo chứng minh vòng chờ bằng chương trình có kiểm soát, rồi phân tích vì sao khóa theo tên chỉ giảm một phần rủi ro.

## 2. Deadlock hình thành khi hai loader ủy quyền chéo

Giả sử A và B có chính sách tìm class qua nhau:

~~~text
           Thread T1                              Thread T2

     giữ lock loader A                      giữ lock loader B
             |                                      |
             v                                      v
    A.loadClass("lab.A")                    B.loadClass("lab.B")
             |                                      |
             v                                      v
    gọi B.loadClass("lab.B")                gọi A.loadClass("lab.A")
             |                                      |
             v                                      v
      CHỜ lock B                              CHỜ lock A
             |                                      |
             +---------------+----------------------+
                             |
                      CIRCULAR WAIT
                         DEADLOCK
~~~

T1 không nhả khóa A cho đến khi B trả kết quả. T2 không nhả khóa B cho đến khi A trả kết quả. Nếu cả hai monitor được giữ và hai lời gọi chéo xảy ra đồng thời, không thread nào tiếp tục được.

Một cách mô hình hóa là **wait-for graph**:

~~~text
T1 giữ A, muốn B:    T1 ──wait-for──► T2
T2 giữ B, muốn A:    T2 ──wait-for──► T1

Chu trình T1 → T2 → T1 → deadlock
~~~

Chữ “đồng thời” ở đây quan trọng: với một thread, một vòng ủy quyền logic A→B→A có thể biểu hiện thành **đệ quy vô hạn / StackOverflowError** thay vì monitor deadlock, tùy cách các loader kiểm tra cache và trả lỗi. Với hai thread giữ khóa theo thứ tự ngược nhau, cùng đồ thị phụ thuộc có thể thành deadlock.

Không phải mọi lần A gọi B rồi B gọi A đều lập tức deadlock. Nếu một yêu cầu được giải quyết bởi class đã load, parent hợp lệ hoặc fallback kết thúc, có thể không chờ chéo. Vấn đề nằm ở **đồ thị ủy quyền không bảo đảm tiến trình kết thúc** và thứ tự giữ khóa giữa các thread.

## 3. Ví dụ Java 17: tái hiện deadlock bằng hai loader tùy chỉnh

Ví dụ này **cố ý tạo deadlock thật** ở cấp Java monitor để minh họa cơ chế; nó không cần ClassFile bên ngoài và không định nghĩa class ứng dụng nào. Chúng ta tạo hai subclass `ClassLoader` **không đăng ký parallel-capable**, mỗi loader giữ monitor khi gọi loader kia.

Để chương trình **không treo khi chạy thử**, hai thread tham gia deadlock được đánh dấu `daemon`. Thread chính chỉ xác nhận cả hai đã vào vùng khóa và in kết quả, rồi JVM có thể thoát dù hai daemon thread đang BLOCKED. Đây là minh họa để học tập, **không được dùng cấu trúc ủy quyền chéo này trong ứng dụng thật**.

Tạo tệp **`CrossLoaderDeadlockDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

public class CrossLoaderDeadlockDemo {
    static final class CrossingLoader extends ClassLoader {
        private final String requestedName;
        private final CountDownLatch bothHoldingLocks;
        private CrossingLoader peer;

        CrossingLoader(String requestedName,
                       CountDownLatch bothHoldingLocks) {
            super(null);
            this.requestedName = requestedName;
            this.bothHoldingLocks = bothHoldingLocks;
        }

        void setPeer(CrossingLoader peer) {
            this.peer = peer;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (name.equals(requestedName)) {
                    bothHoldingLocks.countDown();

                    try {
                        bothHoldingLocks.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new ClassNotFoundException(name, e);
                    }

                    // Gọi chéo trong lúc vẫn giữ monitor của chính loader.
                    return peer.loadClass(peer.requestedName);
                }

                return super.loadClass(name, resolve);
            }
        }
    }

    private static void request(CrossingLoader loader, String name) {
        try {
            loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch bothHoldingLocks = new CountDownLatch(2);

        CrossingLoader loaderA =
                new CrossingLoader("lab.A", bothHoldingLocks);
        CrossingLoader loaderB =
                new CrossingLoader("lab.B", bothHoldingLocks);

        loaderA.setPeer(loaderB);
        loaderB.setPeer(loaderA);

        Thread threadA = new Thread(
                () -> request(loaderA, "lab.A"), "load-A");
        Thread threadB = new Thread(
                () -> request(loaderB, "lab.B"), "load-B");

        // Daemon: tránh giữ JVM sống mãi trong ví dụ cố ý deadlock.
        threadA.setDaemon(true);
        threadB.setDaemon(true);

        threadA.start();
        threadB.start();

        // Chỉ tiếp tục khi cả hai thread đang giữ lock riêng của mình.
        bothHoldingLocks.await();

        // Thread A phải lấy lock B, thread B phải lấy lock A.
        // Cả hai không thể hoàn thành nếu vẫn giữ lock hiện tại.
        threadA.join(100);
        threadB.join(100);

        System.out.println("Crossed delegation deadlocked: "
                + (threadA.isAlive() && threadB.isAlive()));

        ClassLoader sharedParent =
                ClassLoader.getPlatformClassLoader();

        ClassLoader safeA = new ClassLoader(sharedParent) { };
        ClassLoader safeB = new ClassLoader(sharedParent) { };

        boolean sameRuntimeType =
                safeA.loadClass("java.lang.String")
                        == safeB.loadClass("java.lang.String");

        System.out.println("Shared parent String identity: "
                + sameRuntimeType);
    }
}
~~~

Biên dịch và chạy với **JDK 17+**:

~~~bash
javac --release 17 CrossLoaderDeadlockDemo.java
java CrossLoaderDeadlockDemo
~~~

**Stdout dự kiến theo logic đồng bộ trong ví dụ:**

~~~text
Crossed delegation deadlocked: true
Shared parent String identity: true
~~~

**Giải thích từng phần:**

1. Hai loader `loaderA` và `loaderB` thuộc subclass không có `registerAsParallelCapable`, nên `getClassLoadingLock(name)` trả khóa loader. Mỗi thread nhận một monitor khác nhau.
2. `CountDownLatch(2)` bảo đảm cả hai thread đã tiến vào monitor của loader riêng trước khi đi tiếp. Đây không phải vòng chờ trên latch: lần thứ hai `countDown()` làm latch về 0 và **đánh thức cả hai**.
3. Sau latch, thread A vẫn giữ lock A và gọi `loaderB.loadClass("lab.B")`; thread B vẫn giữ lock B và gọi `loaderA.loadClass("lab.A")`. Mỗi lời gọi chéo cần monitor đang bị phía kia giữ: hình thành **deadlock**.
4. `join(100)` chỉ đợi tối đa 100 ms mỗi thread, **không giải quyết deadlock**. Cả hai thread vẫn còn sống, nên dòng đầu là `true`.
5. Hai thread được đặt `daemon` **trước khi start**, giúp JVM thoát khi thread main kết thúc. Trong một ứng dụng thật, đánh dấu thread daemon **không phải cách sửa deadlock**.
6. Hai loader `safeA` và `safeB` đều dùng chung một parent để nạp `java.lang.String`. Chúng được viết bằng `ClassLoader` mặc định, không tạo ủy quyền chéo; class được trả là cùng `String.class`, nên dòng thứ hai là `true`.

Lưu ý mục tiêu demo: **chứng minh tranh chấp khóa**, không xây một ClassFile hợp lệ hay class loader phục vụ production. Các tên `lab.A` / `lab.B` được dùng làm trigger trước khi tra cứu ClassFile; hai thread bị chặn ở monitor, không cần có `A.class` hoặc `B.class` trên đĩa.

### Tại sao thread chính không treo theo hai loader?

Thread main không gọi hai loader đang deadlock. Nó chỉ chờ latch được mở và thử `join` có timeout. Do T1/T2 là daemon, JVM có thể kết thúc khi chỉ còn daemon threads. Nếu đổi chúng thành **non-daemon** mà không hủy vòng ủy quyền, JVM thường không thoát. Đây là lý do ví dụ cố ý cô lập sự cố trong daemon thread; đừng suy ra cách này bảo vệ hệ thống thật.

## 4. registerAsParallelCapable có giúp không?

Java cung cấp:

~~~java
static {
    ClassLoader.registerAsParallelCapable();
}
~~~

Đây là **fragment trong thân một subclass ClassLoader**; ở ứng dụng thật, nên gọi một lần trong static initializer của subclass và kiểm tra điều kiện các superclass theo API. Phương thức protected static này không phải lời gọi công khai từ tùy ý một class không liên quan.

**Parallel-capable giải quyết vấn đề gì?**

- Khi loader không parallel-capable, nhiều thread tải **hai tên class khác nhau** vẫn có thể phải chờ **một monitor của loader**.
- Khi parallel-capable, `getClassLoadingLock(name)` cung cấp **khóa theo tên** giúp cho phép tiến hành song song các tên khác nhau, giảm một số trường hợp nghẽn cổ chai hoặc khóa theo toàn loader.
- Nó không tự thay đổi chính sách `parent-first`/`child-first`, không tạo đường đi mới giữa các loader và không sửa vòng phụ thuộc class.

**Quan trọng: parallel-capable không phải thuốc chữa mọi deadlock.** Nếu T1 giữ khóa cho `A` của loader A và cần `B` của loader B, còn T2 giữ khóa cho `B` của loader B và cần `A` của loader A, thì ngay cả khóa **theo tên** vẫn tạo vòng chờ trên đúng hai khóa ấy. Ngoài ra custom loader có thể tự giữ monitor, `ReentrantLock`, global cache hoặc lock của framework khi gọi sang loader khác.

Không nên đăng ký parallel-capable mà chưa kiểm tra thread-safety của:

- Bộ nhớ đệm bytecode, map ClassFile, metadata package/`ProtectionDomain` và nguồn tài nguyên.
- `findClass`, `loadClass` override, các hàm fallback/resolve và khóa tùy chỉnh.
- Chính sách load đúng một lần cho mỗi binary name, tránh race dẫn tới `LinkageError`.
- Deadlock do loader A/B gọi chéo, cùng các lock ngoài `getClassLoadingLock`.

Quy tắc thực dụng: **thiết kế đồ thị ủy quyền không có chu trình** trước, sau đó mới xem parallel loading như tối ưu/đúng đắn đồng thời khi cần.

## 5. Một chiều parent delegation và vòng gọi chéo khác gì?

**Đồ thị an toàn điển hình** là cây/hệ DAG một chiều:

~~~text
Plugin-A loader ──► Shared API loader ──► Platform/Bootstrap
Plugin-B loader ──► Shared API loader ──► Platform/Bootstrap

Không có cạnh Shared API loader quay về Plugin-A/Plugin-B.
~~~

Cả hai plugin có thể cô lập implementation của chúng nhưng cùng sử dụng một định nghĩa API. Với cùng tên contract, parent loader là **defining loader chung**, tránh `ClassCastException` do duplicate API.

**Đồ thị nguy hiểm:**

~~~text
Plugin-A loader ──► Plugin-B loader
        ▲                 |
        |                 ▼
        +─────────────────+
~~~

Ngay cả khi có logic fallback, cơ chế “A không thấy thì hỏi B, B không thấy thì hỏi A” có thể không có điểm dừng. Nếu có hai thread nạp đồng thời, rủi ro deadlock rất lớn; nếu trên một thread, có thể đệ quy vô tận.

Một số hệ plugin cần **cross-plugin visibility**. Thay vì để hai loader hỏi nhau tự do:

1. Đặt **API chia sẻ** trong một loader parent chung, không chứa implementation riêng.
2. Định nghĩa một **registry/route lookup** theo tên package hoặc module, đảm bảo mỗi yêu cầu có một **chủ sở hữu** hoặc lộ trình tra cứu có điểm kết thúc.
3. Nếu cần chuyển tiếp giữa nhiều loader, duy trì **visited set / cycle detection** ở tầng điều phối thích hợp và fail rõ ràng khi gặp vòng, thay vì gọi đệ quy vô hạn.
4. Không gọi code tùy ý của plugin khi đang giữ khóa nạp lớp rộng.
5. Không tự đổi type identity bằng cách nhờ loader khác rồi lại `defineClass` cùng bytes trong loader hiện tại; đó sẽ là **định nghĩa thứ hai** có identity khác.

**Chú ý:** Java không yêu cầu mọi custom loader luôn có đúng một parent path và không có tương tác bên ngoài. Tuy nhiên, khi thiết kế đường tìm class xuyên loader, cần bảo đảm các nhánh tìm kiếm **có thể kết thúc** và có quy tắc ownership, đồng bộ đúng.

## 6. Tình huống thực tế nào dễ phát sinh deadlock?

### 6.1. Hai plugin tham chiếu trực tiếp implementation của nhau

Plugin A cố tải `plugin.b.Helper` thông qua loader B; cùng lúc B cố tải `plugin.a.Adapter` thông qua loader A. Nếu mỗi loader giữ lock rộng trong khi gọi sang bên kia, có thể hình thành vòng chờ.

Cách cải thiện: để cả hai gọi qua **interface/API chung**, chuyển dữ liệu qua DTO chia sẻ, hoặc dùng service registry với lifecycle rõ. Tránh phụ thuộc implementation chéo khó kiểm soát.

### 6.2. Ủy quyền “peer loader” tự viết trong child-first loader

Một custom loader có thể viết:

~~~java
// Fragment anti-pattern: chưa có cơ chế chống lặp/cycle.
try {
    return findClass(name);
} catch (ClassNotFoundException missing) {
    return peerLoader.loadClass(name);
}
~~~

Nếu `peerLoader` cũng làm vậy và hỏi ngược loader đầu khi không tìm thấy class, tạo vòng A→B→A. Đoạn fragment không biểu diễn đầy đủ xử lý `findLoadedClass`, lock hoặc `resolveClass`, chỉ minh họa chính sách có nguy cơ.

**Đừng nuốt tất cả `LinkageError` rồi fallback sang peer**: lỗi định nghĩa/liên kết không đồng nghĩa với “class không có ở nguồn này”. Bắt lỗi quá rộng có thể che bytecode hỏng hoặc xung đột type identity.

### 6.3. Class initialization chồng với class loading

`static initializer` có thể gọi code/plugin khác, kích hoạt nạp lớp mới. Deadlock cũng có thể đến từ **khóa khởi tạo class**, static cache lock hoặc application lock, không chỉ monitor của class loader. Ví dụ một thread giữ application lock rồi dùng class cần khởi tạo, trong khi thread khác đang khởi tạo class đó và muốn application lock.

Vì vậy cần phân biệt **class-loading lock deadlock** với **class-initialization deadlock**. Hai loại có thể trông giống nhau trong log “treo khi dùng class” nhưng thread dump và khóa chờ có thể khác.

### 6.4. Thread pool và startup song song

Trong ứng dụng có nhiều worker, lỗi có thể khó tái hiện:

- Khi chạy đơn luồng, một nhánh tìm class có thể hoàn tất hoặc thất bại trước nhánh khác.
- Khi start nhiều plugin cùng lúc, hai thread có thể đồng thời giữ lock loader tương ứng và gọi nhau.
- Những lần chạy khác nhau có thời điểm khởi tạo lớp/cache khác, khiến lỗi xuất hiện không đều.
- Thay đổi thread count hoặc timing có thể làm lỗi “tạm biến mất” nhưng **không loại bỏ chu trình ủy quyền**.

Không nên sửa bằng `Thread.sleep`, tăng timeout hoặc giảm pool size rồi coi hết lỗi; đó chỉ thay đổi lịch chạy. Mục tiêu là **loại vòng chờ hoặc định rõ lock ordering**.

## 7. Chẩn đoán bằng thread dump và JVM tools

Khi ứng dụng treo lúc nạp class:

1. Ghi nhận thread nào đang BLOCKED/WAITING và stack trace tại `ClassLoader.loadClass`, `findClass`, `getClassLoadingLock`, synchronized block hoặc code plugin.
2. Thu **thread dump** lúc treo. Với HotSpot/JDK tiêu chuẩn, có thể dùng lệnh:

~~~bash
jcmd <PID> Thread.print -l
~~~

Thay `<PID>` bằng process ID của JVM cần kiểm tra; đây là placeholder trong **câu lệnh công cụ**, không phải một đoạn Java để biên dịch. Một lựa chọn khác:

~~~bash
jstack -l <PID>
~~~

Các công cụ có thể thay đổi khả dụng theo phân phối JDK, OS và quyền truy cập process. Không nên chạy trong môi trường có hạn chế giám sát mà chưa được phép.

3. Tìm đoạn mô tả thread đang chờ monitor mà thread khác giữ, rồi đối chiếu monitor của `LoaderA` và `LoaderB`.
4. Nếu JVM phát hiện vòng chờ Java-level, thread dump có thể có phần đại ý **“Found one Java-level deadlock”**. Không phải mọi tình huống treo đều được công cụ này phát hiện tự động.
5. Kiểm tra mô hình class loader và policy fallback: loader A muốn name nào từ B, loader B muốn name nào từ A; có cách kết thúc hoặc class owner rõ không?
6. Xem các khóa **ngoài loader** (registry cache lock, framework synchronization, `<clinit>`, lock của plugin); class-loading lock không nhất thiết là nguyên nhân duy nhất.
7. Tìm cách tái hiện trong test bằng latch/barrier thay vì `sleep`, để đảm bảo thứ tự hai thread giữ lock như ví dụ ở trên.

### Dùng ThreadMXBean khi cần

Java cung cấp `java.lang.management.ThreadMXBean` để truy vấn deadlock liên quan **object monitor** và, tùy runtime/API, **ownable synchronizer**.

Ví dụ **fragment** (chỉ minh họa, không tự thiết lập deadlock):

~~~java
java.lang.management.ThreadMXBean bean =
        java.lang.management.ManagementFactory.getThreadMXBean();

long[] deadlocked = bean.findDeadlockedThreads();

if (deadlocked != null) {
    System.out.println("Deadlocked thread count: "
            + deadlocked.length);
}
~~~

`findDeadlockedThreads()` có thể trả `null` nếu không phát hiện chu trình tại thời điểm gọi. Nó không bảo đảm phát hiện mọi dạng vòng chờ ở mức ứng dụng, JNI, I/O, `CountDownLatch` hoặc class initialization, nên cần đọc stack trace và hệ thống khóa thực tế. Đặc biệt, trong ví dụ `CrossLoaderDeadlockDemo`, các thread chết treo do **hai monitor của loader**; việc lấy snapshot đúng thời điểm có thể phát hiện nhưng không cần để chứng minh deadlock.

**Các chi tiết giúp phân biệt:**

| Triệu chứng | Gợi ý điều tra |
| --- | --- |
| Hai thread đều `BLOCKED` và mỗi thread giữ monitor loader kia cần | Deadlock monitor/cross-delegation đáng nghi. |
| `WAITING` trên class initialization | Có thể là chu trình khởi tạo class hoặc chờ tác vụ khác, không nên vội kết luận loader lock. |
| Một thread chạy dài với stack `loadClass→findClass→peer.loadClass` lặp | Có thể là **đệ quy ủy quyền vô hạn** trên một thread. |
| `ClassNotFoundException` nhanh và xác định | Thường là đường tìm không thấy class, không phải deadlock. |
| `LinkageError` khi nạp đồng thời | Có thể định nghĩa trùng hoặc loader constraints; đừng coi là deadlock chỉ vì đều xảy ra lúc loading. |
| Chỉ treo sau redeploy | Kiểm tra class loader cũ bị giữ bởi thread, cache, registry và callback. |

## 8. Cách phòng tránh và khắc phục

### 8.1. Ưu tiên đồ thị nạp không chu trình

Parent delegation từ plugin tới shared API/runtime thường tạo một đồ thị có hướng một chiều, dễ kiểm soát. Đưa class/API dùng chung về loader **cao hơn hoặc độc lập và ổn định**, không bắt loader cha phải hỏi ngược loader con để tìm dependency.

### 8.2. Thống nhất ownership cho mỗi binary name

Với `com.acme.api.*`, xác định một defining loader chung. Với `plugin.a.impl.*` hoặc `plugin.b.impl.*`, xác định loader riêng. Nếu một yêu cầu không thuộc phạm vi loader và không có lộ trình kết thúc, báo `ClassNotFoundException` thay vì hỏi peer vô hạn.

### 8.3. Không giữ khóa rộng khi gọi loader không kiểm soát

Nếu code tự viết thêm `synchronized (this)`, `synchronized (registry)` hoặc global lock bao quanh lookup chéo, hãy cân nhắc thu hẹp critical section. Nhưng **không tùy tiện bỏ khóa nạp lớp chuẩn**: thao tác kiểm tra đã nạp, định nghĩa và resolve vẫn cần tuân theo hợp đồng JVM. Cách tốt hơn là thiết kế lộ trình một chiều, tránh callback tùy ý khi đang giữ khóa.

### 8.4. Dùng parallel-capable khi thread-safety thực sự đáp ứng

Đăng ký parallel-capable có thể giảm contention cho những tên class **khác nhau**, nhưng phải kiểm tra mọi state dùng chung của loader và **không coi là fix cho vòng A→B→A**. Nếu một chu trình tồn tại trên chính những class đang được nạp, per-name lock vẫn có thể deadlock.

### 8.5. Chặn lặp ở tầng điều phối và fail fast

Nếu kiến trúc phải có lookup qua nhiều nguồn, có thể xây một resolver với đồ thị được validate trước, hoặc theo dõi đường đi `A → B → C` và từ chối khi gặp lại loader/name theo logic phù hợp. Đừng dựa vào một `ThreadLocal` đơn giản làm biện pháp duy nhất cho deadlock **giữa hai thread**: mỗi thread có thể nhìn một nửa chu trình và không thấy quan hệ chờ xuyên thread.

### 8.6. Viết test concurrency

- Tạo tình huống hai thread bắt đầu yêu cầu hai loader cùng lúc bằng `CountDownLatch`/`CyclicBarrier`.
- Dùng timeout ở **test harness** và thu thread dump khi vượt thời gian.
- Xác nhận lớp được trả đúng defining loader và không có `LinkageError` do định nghĩa trùng.
- Kiểm tra cả đường thành công, class không tồn tại, plugin restart/redeploy và shutdown.
- Dùng một mô hình với API dùng chung và hai implementation riêng để đảm bảo class loader không hỏi nhau vô hạn.

Đối với các test cố tình tạo deadlock, nên chạy trong **process cô lập** hoặc daemon thread như ví dụ, để không làm treo toàn bộ bộ test. Không đưa code cố ý deadlock vào loader production.

## 9. Những hiểu nhầm cần tránh

**“ClassLoader chỉ có một khóa toàn cục của JVM.”** Không. Cơ chế nạp lớp và locking phụ thuộc loader/parallel-capable; ở loader không parallel-capable, khóa mặc định gắn với **instance loader**, không phải một monitor duy nhất cho mọi loader của JVM.

**“registerAsParallelCapable chữa mọi deadlock.”** Không. Nó cho phép lock theo tên và giảm tranh chấp, nhưng **không giải quyết vòng chờ giữa hai lock cụ thể** hoặc lock bên ngoài.

**“Có parent chung thì mọi kiểu luôn cùng identity.”** Không. Parent phải thực sự định nghĩa class đó. Nếu mỗi child vẫn tự định nghĩa cùng binary name, đó là hai kiểu riêng; policy delegation phải được áp dụng nhất quán.

**“Nếu chỉ một thread thì không có vấn đề gì.”** Không. Có thể không xảy ra *deadlock giữa hai thread*, nhưng vòng ủy quyền không có điểm dừng có thể gây đệ quy vô hạn hoặc failure khác.

**“Chỉ cần nâng timeout hoặc giảm số thread.”** Điều này có thể che lịch chạy dẫn tới deadlock, không sửa đồ thị phụ thuộc vòng.

**“Class initialization lock cũng chính là monitor của loader.”** Không. Thủ tục khởi tạo class có cơ chế đồng bộ riêng theo JVM. Hãy đọc thread dump để xác định loại khóa liên quan.

**“Bắt mọi lỗi load rồi thử loader khác là an toàn.”** Không. `ClassFormatError`, `LinkageError`, `NoClassDefFoundError` hoặc lỗi initialization có thể báo sai code/dependency nghiêm trọng, không tương đương `ClassNotFoundException` để fallback mù quáng.

## 10. Tự kiểm tra

**Câu 1.** T1 giữ monitor loader A rồi đợi monitor B; T2 giữ monitor B rồi đợi monitor A. Đây là hiện tượng gì?

**Đáp án:** **Deadlock** do circular wait; cả hai không thể hoàn thành khi mỗi thread giữ khóa mà thread kia cần.

**Câu 2.** `ClassLoader` không parallel-capable thường trả loại khóa nào từ `getClassLoadingLock(name)`?

**Đáp án:** Chính đối tượng loader, tức mọi tên class trong loader đó dùng cùng monitor mặc định.

**Câu 3.** Chuyển sang parallel-capable có bảo đảm loại deadlock chéo A/B không?

**Đáp án:** Không. Nếu các yêu cầu tạo vòng chờ trên hai khóa theo tên, deadlock vẫn có thể xảy ra.

**Câu 4.** Nếu A hỏi B, B không thấy rồi hỏi ngược A trên một thread, chắc chắn có monitor deadlock không?

**Đáp án:** Không. Khóa monitor của cùng thread có tính reentrant; đường đi không có điểm dừng có thể đệ quy mãi và cuối cùng gây `StackOverflowError` hoặc failure khác.

**Câu 5.** Thiết kế hai plugin có API chung và implementation riêng nên đặt lớp ở đâu?

**Đáp án:** API chung do shared/parent loader định nghĩa; implementation được đặt tại loader riêng của từng plugin. Hạn chế lookup implementation chéo và bảo đảm đồ thị ủy quyền có hướng không tạo vòng.

**Câu 6.** Có nên bỏ `synchronized` quanh mọi `loadClass` để chữa treo không?

**Đáp án:** Không. Bỏ đồng bộ tùy tiện có thể gây race/định nghĩa trùng. Phải sửa đồ thị gọi chéo và thực hiện thread-safety, parent delegation/parallel loading đúng hợp đồng.

## Ghi nhớ

**Deadlock khi nhiều loader ủy quyền chéo** xảy ra khi các thread nạp lớp giữ những **class-loading lock** khác nhau và lần lượt chờ khóa của loader còn lại. Để tránh, hãy **loại chu trình ủy quyền**, xác định defining loader sở hữu mỗi class/API, dùng parent/shared loader cho contract chung, giới hạn cross-loader lookup và kiểm tra concurrency bằng thread dump. **Parallel-capable cải thiện khả năng nạp song song nhưng không thay thế thiết kế đồ thị không chu trình.**

**Tài liệu chính thức:**

- Java SE 17 API, `ClassLoader`: `loadClass`, `getClassLoadingLock` và `registerAsParallelCapable` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java Virtual Machine Specification §5.3 — *Creation of Classes and Interfaces* — https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- Java SE 17 API, `ThreadMXBean` — https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html
- Java SE 17 API, `CountDownLatch` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- Java SE 17 API, `Thread` — https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-class-loading-007">Phân biệt khóa nạp lớp với khóa khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt khóa nạp lớp với khóa khởi tạo lớp trong Java

Khi nhiều thread đồng thời sử dụng một class, Java cần xử lý hai vấn đề khác nhau: **không cho hai thread định nghĩa trùng một class trong cùng class loader**, và **không cho nhiều thread thực thi static initializer của cùng runtime class một cách độc lập**. Hai vấn đề này được giải quyết bằng **khóa nạp lớp** và **cơ chế khóa khởi tạo lớp** — chúng có liên quan tới vòng đời class nhưng **không phải cùng một lock**.

- **Class-loading lock** thuộc cơ chế của `java.lang.ClassLoader`, thường được lấy qua `getClassLoadingLock(String name)` trong `loadClass`. Nó điều phối `findLoadedClass`, parent delegation và `findClass/defineClass`, đặc biệt khi nhiều threads yêu cầu cùng binary name.
- **Class-initialization lock `LC`** được Java Language Specification `12.4.2 mô tả là khóa nội bộ **theo từng runtime class hoặc interface `C`**. JVM dùng nó để điều phối trạng thái `not initialized / initializing / initialized / erroneous` và việc chạy `<clinit>`. Không có API công khai để ứng dụng lấy `LC` như một `java.util.concurrent.locks.Lock`.

**Điểm cần nhớ ngay:** `ClassLoader.loadClass(...)` thông thường **không yêu cầu static initialization**; `Class.forName(name, true, loader)`, gọi static method hoặc tạo instance có thể yêu cầu initialization. Việc một loader được đăng ký **parallel-capable** giúp nạp những *tên class khác nhau* song song, nhưng không khiến **`<clinit>` của cùng runtime class** chạy hai lần.

Bài học thuộc **Java core – classloader / Nạp đồng thời**, dùng ví dụ Java 17+ có thể biên dịch/chạy, bảng so sánh, mô hình trạng thái JVM và những tình huống deadlock/initialization lỗi thường gặp.

## 1. Hai loại khóa bảo vệ hai bất biến khác nhau

| Góc nhìn | Khóa nạp lớp | Khóa khởi tạo lớp |
| --- | --- | --- |
| Mục tiêu | Không có lookup/định nghĩa trùng gây race trong một loader | Phối hợp đúng một quá trình initialization cho mỗi runtime class/interface |
| Phạm vi | Class loader instance; lock có thể chung mọi tên hoặc riêng cho mỗi binary name | Một runtime `Class<?>`/interface `C` và trạng thái initialization do JVM quản lý |
| API/đặc tả | `ClassLoader.getClassLoadingLock` và `loadClass` | JLS §12.4.2 với khóa `LC` |
| Khi được dùng | Trong đường xử lý loading/delegation của loader | Khi active use cần initialize class chưa initialized |
| Loader parallel-capable ảnh hưởng? | Có: thường có locks theo tên class | Không: JVM vẫn giữ semantics initialization |
| Có bảo vệ mọi dòng `static { ... }`? | Không | Khóa dùng cho điều phối trạng thái; không nhất thiết được giữ suốt body `<clinit>` |
| Lỗi đặc trưng | `ClassNotFoundException`, `LinkageError` khi lookup/definition/linking | `ExceptionInInitializerError`, class erroneous, active use sau có `NoClassDefFoundError` |
| Ai điều khiển? | Tác giả subclass loader có thể ảnh hưởng cách lấy lock khi override `loadClass` | JVM, theo JLS/JVMS; ứng dụng không được thay trực tiếp |

**Đừng nhầm khóa nạp lớp với monitor của `Class<?>` object.** `synchronized (SomeClass.class)` khóa một monitor mà Java code nhìn thấy; đặc tả không bảo đảm object monitor đó chính là `LC` nội bộ cho mọi JVM/optimization.

### Một dòng thời gian có hai cơ chế

```text
T1 cần demo.Widget
  │
  ├── Loading: loader.lookup / defineClass
  │       └── class-loading lock phối hợp khi nhiều threads cùng nạp
  │
  ├── Linking: verification / preparation / resolution
  │
  └── Active use: demo.Widget.perform()
          └── JVM kiểm tra trạng thái initialization của Widget
                ├── thread khác đang initialize → đợi
                ├── chưa initialize → thread này chạy <clinit>
                └── đã initialized → sử dụng bình thường
```

Các bước JVM có thể đan xen hoặc trì hoãn một số resolution; sơ đồ nhấn mạnh **mục đích và ranh giới**, không áp một thứ tự giữ khóa cứng cho mọi JVM.

## 2. Nhắc lại loading, linking, initialization và constructor

**Loading:** xác định class theo **binary name và defining loader**, đọc/định nghĩa bytecode nếu cần và có đối tượng `Class<?>`. Với cùng binary name trong cùng defining loader, JVM không cho `defineClass` hợp lệ diễn ra lần thứ hai như một class mới.

**Linking:** verification, preparation, resolution theo JVM specification. Preparation chuẩn bị static fields với giá trị mặc định và các cơ chế riêng cho constant values. Linking không đồng nghĩa body `static { ... }` đã chạy.

**Initialization:** thực thi các static field initializers và static initializer blocks (class initialization method `<clinit>` khi compiler/JVM cần) theo Java rules. Chỉ một quá trình initialization thành công cho mỗi runtime class.

**Instantiation:** tạo instance bằng `new` hoặc reflective constructor. Instance constructor (`<init>`) không phải `<clinit>`. Sau khi class được initialized, rất nhiều instances có thể được tạo đồng thời; `LC` không serialize mọi constructor về sau.

Ví dụ:

```java
ClassLoader loader = Example.class.getClassLoader();
Class<?> type = loader.loadClass("demo.Widget");
Class<?> noInit = Class.forName("demo.Widget", false, loader);
Class<?> initialized = Class.forName("demo.Widget", true, loader);
```

Đây là **snippet minh họa** cần class `Example`, `demo.Widget` và xử lý `ClassNotFoundException`. Hai lời gọi đầu **không bắt buộc static initializer chạy**; lời gọi cuối có `initialize=true` yêu cầu initialization trước khi trả về thành công. Cả ba trả `Class<?>`, không tự tạo instance của `Widget`.

## 3. Khóa nạp lớp bảo vệ giai đoạn nào?
Trong triển khai mặc định, `ClassLoader.loadClass(name, resolve)` giữ `getClassLoadingLock(name)` khi kiểm tra `findLoadedClass`, delegation lên parent/bootstrap, gọi `findClass` nếu cần và `resolveClass` khi yêu cầu. Đường này đảm bảo loader không tự định nghĩa trùng một tên dưới concurrent requests.
**`loadClass` không tự yêu cầu chạy `<clinit>`**. Nếu JVM sau đó cần initialize runtime class, nó sử dụng thủ tục và trạng thái `LC` riêng như §7. Chẩn đoán phải tách lỗi loading/linking khỏi lỗi initialization.

## 4. Parallel-capable chỉ thay khóa của loader
Loader chưa đăng ký dùng monitor `this` cho mọi tên; loader registered parallel-capable dùng lock riêng theo tên. Điều này cho phép nhiều tên được load đồng thời nhưng không làm thay đổi trạng thái initialization theo runtime `Class<?>`. `registerAsParallelCapable` phải được gọi từ static initializer của subclass khi toàn bộ superclass loader đã registered. Thí nghiệm A kiểm chứng đối tượng khóa, còn B chứng minh cùng một tên được định nghĩa một lần.

## 5. Thực hành A — kiểm tra object của class-loading lock

Cấu trúc:

```text
lock-scope-lab/
└── src/demo/LockScopeDemo.java
```

**`src/demo/LockScopeDemo.java`:**

```java
package demo;

public final class LockScopeDemo {
    static final class SerialLoader extends ClassLoader {
        SerialLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        boolean same(String a, String b) {
            return getClassLoadingLock(a) == getClassLoadingLock(b);
        }

        boolean lockIsLoader(String name) {
            return getClassLoadingLock(name) == this;
        }
    }

    static final class ParallelLoader extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "Parallel registration failed");
            }
        }

        ParallelLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        boolean same(String a, String b) {
            return getClassLoadingLock(a) == getClassLoadingLock(b);
        }
    }

    public static void main(String[] args) {
        SerialLoader serial = new SerialLoader();
        ParallelLoader parallel = new ParallelLoader();

        System.out.println("serial same A/B: " +
                serial.same("demo.A", "demo.B"));
        System.out.println("serial lock is loader: " +
                serial.lockIsLoader("demo.A"));
        System.out.println("parallel different A/B: " +
                !parallel.same("demo.A", "demo.B"));
        System.out.println("parallel same A/A: " +
                parallel.same("demo.A", "demo.A"));
    }
}
```

Chạy trên **JDK 17+** từ `lock-scope-lab`:

```sh
javac --release 17 -d out src/demo/LockScopeDemo.java
java -cp out demo.LockScopeDemo
```

**Stdout dự kiến:**

```text
serial same A/B: true
serial lock is loader: true
parallel different A/B: true
parallel same A/A: true
```

Phân tích:

1. `SerialLoader` không đăng ký, nên các tên dùng cùng lock là **instance `serial`**.
2. `ParallelLoader` đăng ký trong static initializer trước khi tạo đối tượng; locks theo hai tên khác nhau có thể khác nhau.
3. Cùng tên trên cùng parallel-capable loader trả lock tương ứng nhất quán.
4. Chương trình chỉ hỏi lock object và so sánh identity; nó **không** nạp `demo.A`, `demo.B` hay chạy static initializers của chúng.
5. `getClassLoadingLock` là API `protected` cho loader implementer; ứng dụng không có cách dùng nó để lấy `LC` JVM.

**Không suy luận:** đăng ký parallel-capable sẽ buộc JVM chạy static blocks đồng thời. Nó chỉ ảnh hưởng synchronization trong hoạt động class loading của loader.

## 6. Thực hành B — hai threads cùng tải một class, chỉ một definition thành công

Ta tạo custom loader với `findClass` đọc bytes từ một output directory không thuộc application classpath. Hai threads cùng gọi `loadClass("payload.Target")` sau latch.

Cây tệp:

```text
load-once-lab/
├── src/payload/Target.java
└── src/demo/LoadOnceDemo.java
```

**`src/payload/Target.java`:**

```java
package payload;

public final class Target {
    public static String message() {
        return "payload";
    }
}
```

**`src/demo/LoadOnceDemo.java`:**

```java
package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoadOnceDemo {
    static final class CountingLoader extends ClassLoader {
        final AtomicInteger findCalls = new AtomicInteger();

        CountingLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals("payload.Target")) {
                throw new ClassNotFoundException(name);
            }

            try {
                byte[] bytes = Files.readAllBytes(
                        Path.of("payload-out", "payload", "Target.class"));
                findCalls.incrementAndGet();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        CountingLoader loader = new CountingLoader();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Class<?>> task = () -> {
            go.await();
            return loader.loadClass("payload.Target");
        };

        try {
            Future<Class<?>> first = pool.submit(task);
            Future<Class<?>> second = pool.submit(task);
            go.countDown();

            Class<?> a = first.get();
            Class<?> b = second.get();

            System.out.println("same Class: " + (a == b));
            System.out.println("defined by child: " +
                    (a.getClassLoader() == loader));
            System.out.println("findClass calls: " +
                    loader.findCalls.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

**Biên dịch** từ thư mục `load-once-lab`:

```sh
javac --release 17 -d payload-out src/payload/Target.java
javac --release 17 -d app-out src/demo/LoadOnceDemo.java
```

**Chạy** (chỉ đưa `app-out` lên classpath, không thêm `payload-out`):

```sh
java -cp app-out demo.LoadOnceDemo
```

**Stdout:**

```text
same Class: true
defined by child: true
findClass calls: 1
```

Vì sao?

- Parent là platform loader, nên không nhìn thấy `payload.Target` trong thư mục riêng `payload-out`.
- Hai tasks được khởi động từ cùng latch và cùng yêu cầu một loader instance nạp đúng một binary name.
- `CountingLoader` không đăng ký parallel-capable, nên đường `loadClass` mặc định khóa trên loader.
- Một thread đọc bytes và gọi `defineClass`. Thread kia qua `findLoadedClass` nhận class đã được ghi nhận, không define trùng.
- `findClass calls: 1` xảy ra do lần định nghĩa đầu thành công. Nếu class luôn vắng mặt, nhiều lần lookup thất bại **có thể gọi `findClass` nhiều lần**.
- Chương trình không gọi `Target.message()`; không có lý do coi kết quả là bằng chứng static initializer chạy.

**Lưu ý môi trường:** tất cả các lệnh chạy từ project root vì `Path.of("payload-out",...)` tương đối với working directory. Trên Windows và Linux/macOS, lệnh `java -cp app-out` giống nhau khi chỉ có một mục.

## 7. Khóa `LC` và trạng thái khởi tạo của JVM

JLS §12.4.2 quy định một thủ tục cho mỗi runtime class/interface `C`, cùng khóa initialization `LC`. Mục tiêu của nó là bảo đảm:

1. Nếu `C` **đã initialized**, các active uses không chạy `<clinit>` lại.
2. Nếu `C` **chưa initialized và chưa được thread nào initialize**, một thread được ghi nhận là thread đang thực hiện initialization.
3. Nếu **chính thread đang initialize** lại yêu cầu initialization của `C` đệ quy, thuật toán nhận ra same-thread recursion và không chờ chính mình.
4. Nếu **thread khác** yêu cầu initialization khi `C` đang initializing, thread đó **đợi** kết quả thích hợp thay vì thực thi body `<clinit>` song song.
5. Nếu initialization kết thúc thành công, trạng thái chuyển **initialized** và các threads đang chờ được phép tiếp tục phù hợp.
6. Nếu initialization thất bại, class trở thành **erroneous**; các active uses sau trên cùng runtime class không tự chạy lại initializer.

Mô hình:

```text
C đã load và prepared
          │
          ▼
   not initialized
          │
          ▼
 initializing by thread T
    ┌──────┴────────┐
    │               │
 thành công       thất bại
    │               │
    ▼               ▼
 initialized      erroneous
    │               │
active use sau   active use sau
bình thường      có thể NoClassDefFoundError
```

**Tinh tế nhưng quan trọng:** `LC` được dùng để kiểm tra/cập nhật trạng thái và phối hợp chờ; **JLS không yêu cầu giữ monitor `LC` suốt mọi dòng user code trong static initializer**. JVM implementation được phép tối ưu synchronization miễn semantics và happens-before tương ứng được bảo toàn.

Do đó, `synchronized (C.class)` của ứng dụng **không được xem là giao diện công khai để nắm `LC`**. Nếu cần đồng bộ mutable application state, hãy dùng `synchronized`/`Lock`/atomics của chính ứng dụng.

## 8. Khi nào class initialization được yêu cầu?

Các hoạt động như sau thường là active use:

- `new C()`: tạo instance class `C`.
- `C.staticMethod()`: gọi static method được khai báo bởi `C`.
- Đọc/ghi static field của `C` trong các trường hợp JLS quy định; **đọc constant variable** có thể được inline và không buộc initialize.
- `Class.forName("pkg.C", true, loader)`: yêu cầu initialization.
- Một số reflection operations và JVM bootstrap behaviors được đặc tả.

Những thao tác **không tự yêu cầu initialization** của target theo cách thông thường:

- `loader.loadClass("pkg.C")`.
- `Class.forName("pkg.C", false, loader)`.
- `C.class` hoặc truy cập metadata nhất định.
- Chỉ biết tên class trong constant pool nhưng chưa active-use.

Trong một class hierarchy, initialization superclass và những superinterfaces thích hợp có quy tắc thứ tự riêng. Không coi mọi interface được implement đều chắc chắn initialized chỉ vì class được initialized.

**Không nhầm static constructor với instance constructor:** `<clinit>` nói về initialization của class/interface; `<init>` là constructors được thực thi khi tạo từng instance. Khóa `LC` không tự serialize mọi lời gọi `new C()` sau khi class đã initialized.

## 9. Thực hành C — hai tasks gọi static method nhưng `<clinit>` chỉ chạy một lần

Bài tập này dùng latches để quan sát initialization đang diễn ra. Ta cố ý cho static initializer **đợi main thread** trước khi kết thúc; một worker khác cũng chuẩn bị gọi static method.

Tệp **`src/demo/InitializationDemo.java`:**

```java
package demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class InitializationDemo {
    private static final CountDownLatch INIT_ENTERED =
            new CountDownLatch(1);
    private static final CountDownLatch LET_FINISH =
            new CountDownLatch(1);
    private static final CountDownLatch SECOND_READY =
            new CountDownLatch(1);
    private static final AtomicInteger CALLS = new AtomicInteger();

    static final class SlowInit {
        static {
            System.out.println("<clinit>: begin");
            INIT_ENTERED.countDown();
            try {
                LET_FINISH.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new ExceptionInInitializerError(error);
            }
            System.out.println("<clinit>: end");
        }

        static void ping() {
            CALLS.incrementAndGet();
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        System.out.println("main: start");
        try {
            Future<?> first = pool.submit(() -> SlowInit.ping());
            INIT_ENTERED.await();

            Future<?> second = pool.submit(() -> {
                SECOND_READY.countDown();
                SlowInit.ping();
            });
            SECOND_READY.await();

            System.out.println("main: release initializer");
            LET_FINISH.countDown();

            first.get();
            second.get();
            System.out.println("method calls=" + CALLS.get());
        } finally {
            LET_FINISH.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy trong một JVM mới bằng JDK 17+:

```sh
javac --release 17 -d out src/demo/InitializationDemo.java
java -cp out demo.InitializationDemo
```

**Stdout:**

```text
main: start
<clinit>: begin
main: release initializer
<clinit>: end
method calls=2
```

Giải thích:

1. Main in `main: start` trước khi submit, nên thông báo này xuất hiện đầu.
2. Worker đầu active-use `SlowInit.ping()` và kích hoạt initialization; `<clinit>` in `begin`, báo cho main rồi đợi latch.
3. Main chỉ tạo worker thứ hai sau khi biết initializer đã bắt đầu. Thread thứ hai sẵn sàng gọi `ping()`, nhưng không được hoàn tất active use không phù hợp khi class còn initializing.
4. Main in `release`, mở latch; initializer in `end` và kết thúc thành công.
5. Hai lời gọi `ping()` cuối cùng đều hoàn tất, nên `CALLS == 2`. Static initializer chỉ thực thi một lần dù có hai tasks.

**Đây không phải cách lấy `LC`.** `CountDownLatch` là synchronization **do chương trình dựng trong body initializer** để tạo các dấu mốc quan sát được. `SECOND_READY` báo thread đã đến ngay trước lệnh gọi; nó **không bảo đảm** đúng tại thời điểm main mở latch thread ấy đã đứng chờ trong cơ chế `LC`. Không viết assertion phụ thuộc trạng thái thread tức thời hoặc `Thread.sleep`.

## 10. Class loading và class initialization có thể nối tiếp trong một thao tác

Một lời gọi:

```java
Class<?> c = Class.forName("demo.Widget", true, loader);
```

có thể yêu cầu cả **loading** lẫn **initialization**:

```text
Nếu Widget chưa được nạp:
    loader lookup và define/link theo quy tắc
    └── class-loading synchronization phù hợp

Khi Widget phải initialized:
    JVM kiểm tra initialization state của runtime Widget
    └── điều phối qua LC theo JLS 12.4.2
        rồi chạy <clinit> khi phù hợp
```

Tuy nhiên **không thể suy ra** “một lock giữ xuyên suốt hai pha”. Hành vi linking/resolution có thể không trùng một timeline cứng và custom loaders có thể làm thêm công việc. Với triển khai mặc định, `loadClass` không tự thực thi `<clinit>` để chuẩn bị ứng dụng.

### Loader locks khác nhau theo instance

Nếu `loaderA` và `loaderB` tự định nghĩa cùng binary name `pkg.C`, chúng có thể có những class-loading locks độc lập. Hai runtime classes được định nghĩa cũng có **initialization states riêng**:

```text
(pkg.C, loaderA) → Class<?> A → trạng thái khởi tạo A
(pkg.C, loaderB) → Class<?> B → trạng thái khởi tạo B
```

Một class cùng tên có thể được initialized một lần **trong mỗi runtime definition**. Đừng mô tả “mỗi binary name chỉ có một `<clinit>` trong cả JVM”.

Nếu `loaderB` hỏi parent và nhận bản `C` do `loaderA` định nghĩa, thì hai callers vẫn sử dụng **cùng Class<?>** và cùng trạng thái initialization. Defining loader mới là thành phần của type identity, không phải loader chỉ được dùng để khởi xướng lookup.

## 11. Same-thread initialization recursion và giá trị static mặc định

JLS còn mô tả trường hợp **thread đang initialize `C` lại yêu cầu initialize `C` lần nữa**. JVM nhận biết cùng thread và không tự chờ chính mình.

Ví dụ **minh họa** (cần một caller active-use để chạy):

```java
class Recursive {
    static int x = compute();

    private static int compute() {
        return Recursive.x;
    }
}
```

Trong preparation, `x` có giá trị mặc định `0`. Khi `compute()` chạy trong initialization, nó có thể đọc `Recursive.x` trước khi phép gán cuối hoàn tất. Yêu cầu initialization đệ quy cùng thread không gây chờ; giá trị trả về có thể đến từ trạng thái preparation/initializers đã chạy một phần.

Điều này chứng minh **thứ tự phụ thuộc static state rất quan trọng**, không có nghĩa JVM khóa toàn bộ class ở một monitor để không ai được đụng đến bất kỳ field nào.

**Tránh cyclic static initialization** khi code không chấp nhận giá trị default hay state chưa hoàn chỉnh. Chuyển công việc phụ thuộc vòng sang factory/startup phase có thứ tự rõ ràng.

## 12. Cross-thread class initialization deadlock

Một kịch bản điển hình khi hai threads cùng khởi tạo **hai classes khác nhau**:

```text
Thread A                           Thread B
initialize Alpha                   initialize Beta
    │                                  │
    ▼                                  ▼
Alpha.<clinit>                    Beta.<clinit>
    │                                  │
needs Beta                        needs Alpha
    │                                  │
    └── waits for Beta                └── waits for Alpha

          Vòng chờ A ↔ B
```

Đây là **sơ đồ deadlock**, không phải một chương trình demo nên chạy (vì có thể treo vô thời hạn). Cơ chế chờ class initialization có thể góp phần tạo deadlock dù **cả hai classes đã load xong**. Đổi loader sang parallel-capable không tự chữa vòng chờ của `<clinit>`.

Các nguồn nguy cơ:

- Static initializer gọi I/O chậm, network hoặc chờ asynchronous tasks.
- Hai classes static-initialize dựa vào nhau qua locks/futures.
- Class initializer giữ một application lock rồi active-use class khác; thread kia giữ lock theo thứ tự ngược.
- Plugin callbacks được chạy trong static initialization trong khi host đợi plugin readiness.

**Thiết kế tốt hơn:** giữ `<clinit>` nhẹ, tránh khởi chạy thread/callback lồng nhau, đặt startup có dependencies rõ ràng sau khi class được initialized và quy định lock ordering cho application locks.

Khóa `LC` của hai runtime classes khác nhau **không phải một mutex chung** bảo vệ toàn bộ global mutable state. Nếu hai static initializers cùng ghi một shared map, vẫn cần synchronization của ứng dụng.

## 13. Initialization thất bại và trạng thái erroneous

Khi class initializer ném exception, JVM xử lý initialization failure theo JLS/JVMS. Nếu initializer kết thúc bất thường bởi exception không phải `Error`, lần dùng đầu thường nhận `ExceptionInInitializerError`. Runtime class có thể được đánh dấu **erroneous**, và các active uses sau có thể nhận `NoClassDefFoundError` thay vì chạy `<clinit>` lại.

Điểm rất dễ nhầm: `NoClassDefFoundError` ở lần sau **không nhất thiết** có nghĩa file `.class` không còn trên classpath.

## 14. Thực hành D — initializer ném lỗi, lần sau không được chạy lại

Tệp **`src/demo/InitializationFailureDemo.java`:**

```java
package demo;

public final class InitializationFailureDemo {
    static final class Broken {
        static int value = fail();

        static int fail() {
            System.out.println("initializer executes");
            throw new IllegalStateException("bad configuration");
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                int value = Broken.value;
                System.out.println(value);
            } catch (LinkageError error) {
                System.out.println("attempt " + attempt + ": " +
                        error.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch và chạy trong **một tiến trình JVM mới**:

```sh
javac --release 17 -d out src/demo/InitializationFailureDemo.java
java -cp out demo.InitializationFailureDemo
```

**Stdout:**

```text
initializer executes
attempt 1: ExceptionInInitializerError
attempt 2: NoClassDefFoundError
```

Phân tích:

1. Lần truy cập `Broken.value` đầu buộc initialize `Broken`; `fail()` được chạy và ném `IllegalStateException`.
2. Vì exception ném ra không phải `Error`, JVM bọc thành `ExceptionInInitializerError`, rồi class ở trạng thái erroneous.
3. Lần truy cập sau trên cùng runtime class không chạy lại `fail()`. JVM báo `NoClassDefFoundError`.
4. `catch (LinkageError)` ở đây chỉ nhằm in nhãn kết quả để học. Ứng dụng thật cần giữ stacktrace/cause và thường không nên tiếp tục xử lý nghiệp vụ nếu một class bắt buộc đã initialization hỏng.
5. Đừng “sửa” bằng cách gọi `loader.loadClass` lần nữa: `loadClass` không reset trạng thái erroneous của runtime definition.

Để có runtime class mới với vòng đời initialization khác, cần cơ chế class loading đúng đắn với **defining loader identity mới**; đó là vấn đề plugin/version lifecycle, không phải một kỹ thuật retry lỗi tùy tiện.

## 15. `<clinit>` và `<init>` khác nhau như thế nào?

| Thuộc tính | `<clinit>` | `<init>` |
| --- | --- | --- |
| Vai trò | Class/interface initialization | Object constructor/instance initialization |
| Số lần trong vòng đời | Một lần thành công cho runtime class khi initialized | Theo mỗi instance construction |
| Mối quan hệ với JVM `LC` | Trạng thái/chờ do JVM điều phối | Không bị `LC` serialize cho mọi instance sau khi class initialized |
| Có thể được gọi trực tiếp như method Java? | Không | Qua `new` hoặc reflection constructor theo Java access rules |
| Có gây lỗi riêng? | `ExceptionInInitializerError`, class erroneous | Constructor body có thể ném exception; reflection có thể bọc `InvocationTargetException` |

Một class đã initialized có thể được nhiều threads tạo instances đồng thời. Nếu constructor thao tác counter hoặc collection mutable, caller phải bảo vệ state bằng primitives thích hợp. Không dùng class-loading lock để synchronize business code trong constructor.

## 16. Khác biệt giữa lỗi loading, linking và initialization

| Hiện tượng | Giai đoạn hay gặp | Cần kiểm tra |
| --- | --- | --- |
| `ClassNotFoundException` khi gọi `loadClass` | Lookup không tìm class | Binary name, parent graph, classpath/JAR/URL |
| `ClassFormatError` | Class bytes/verification | Format, bytecode version, source JAR |
| `LinkageError` duplicate definition | Custom loader race khi `defineClass` | `getClassLoadingLock`, `findLoadedClass`, concurrency |
| `NoSuchMethodError` / `NoSuchFieldError` | Resolution/linking với binary API sai version | Descriptors, dependency collision |
| `ExceptionInInitializerError` | `<clinit>` thất bại | Cause đầu tiên, static blocks, I/O/callback |
| `NoClassDefFoundError` ở lần active use sau lỗi init | Runtime class erroneous | Tìm exception lúc initialization đầu |
| Program hang chỉ khi nhiều threads | Loading lock, class-init state hoặc application lock/deadlock | Thread dumps, lock ordering, stacktraces |
| Static block chạy lại sau plugin reload | Runtime class khác do defining loader mới | Class identity, class loader lifecycle |
| `ClassCastException` với cùng binary name | Hai runtime type identities khác nhau | In defining loaders, shared API graph |

**Không áp một biện pháp cho mọi lỗi.** Nạp lớp đúng không bảo đảm binary compatibility. Khởi tạo class thành công không thay thế synchronization cho mutable state trong ứng dụng.

## 17. Debug: quan sát class loading và class initialization độc lập

### API xem defining loader

```java
static void describe(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("defining loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule().getName());
}
```

Đây là **method minh họa**, cần được đặt vào class. `getClassLoader() == null` thường biểu diễn bootstrap loader; `getModule().getName() == null` có thể là unnamed module.

### JVM logging

Trên HotSpot hỗ trợ Unified Logging:

```sh
java -Xlog:class+load=info -cp out demo.InitializationDemo
java -Xlog:class+init=info -cp out demo.InitializationDemo
java -Xlog:help
```

Tag availability phụ thuộc JVM/JDK build; `class+load` giúp xem loading events, `class+init` có thể giúp xem initialization events nếu runtime hỗ trợ. Output log dài và thứ tự phụ thuộc runtime, nên **không có stdout cố định** cho lệnh log này. `-Xlog:help` liệt kê tags/mức hỗ trợ.

### Thread dumps

Dùng `jcmd <pid> Thread.print` trên JVM hỗ trợ hoặc công cụ thread dump tương đương. Khi xem dump:

1. Tìm threads trong `ClassLoader.loadClass`, `findClass` hoặc loader callback.
2. Tìm threads trong static initializer `<clinit>` hoặc chờ initialization của class khác.
3. Đối chiếu parent loader relationships và những application locks đang được giữ.
4. Thu nhiều snapshots nếu có thể để tránh kết luận từ một trạng thái tạm thời.
5. Không giả định JVM `LC` luôn hiển thị như một Java monitor trùng `getClassLoadingLock`.

### Bytecode

```sh
javap -classpath out -c -p demo.InitializationFailureDemo
javap -classpath out -c -p 'demo.InitializationFailureDemo$Broken'
```

`javap` có thể hiện `<clinit>`, field operations và invocation bytecode; nó **không** chứng minh ở thời điểm nào một runtime thread đã nắm initialization coordination. Ở PowerShell và POSIX shell, single quotes giữ ký tự `$` của nested binary name khỏi expansion.

## 18. Quy tắc thiết kế class loader đa luồng

- **Ưu tiên override `findClass`** khi chỉ cần đọc bytecode từ nguồn riêng; giữ `loadClass` chuẩn nếu không cần chính sách delegation tùy biến.
- Khi override `loadClass`, dùng `synchronized (getClassLoadingLock(name))` phù hợp, kiểm tra `findLoadedClass` và chỉ `defineClass` một lần cho một binary name trong loader instance.
- Nếu cần nạp những class names khác nhau đồng thời, hiểu `registerAsParallelCapable` và điều kiện superclass; không bật chỉ để chạy `<clinit>` nhanh hơn.
- Tránh giữ loader lock trong các hoạt động I/O/blocking/callback kéo dài nếu kiến trúc cho phép tách trách nhiệm.
- Không nuốt `Throwable` rồi thử nạp lớp khác: có thể che `LinkageError`, security failure hoặc lỗi tài nguyên nghiêm trọng.
- Đảm bảo shared API/DTO/exception từ host được cả hai bên resolve về **cùng runtime `Class<?>`**, bất kể chính sách parent-first hay child-first.
- Tránh side effects nặng và circular dependencies trong static initializers; tách initialization của type khỏi lifecycle startup của plugin.
- Kiểm thử với **loader graph thực** và nhiều threads, không chỉ chạy tất cả classes trên cùng system classpath trong IDE.
- Quản lý loader lifecycle và references; `URLClassLoader.close()` đóng resource handles nhưng không bảo đảm class unload ngay.
- Nếu thấy một thread chờ, xác định nó đang chờ **loading lock**, **class initialization state** hay **application synchronization** trước khi sửa code.

## 19. Những ngộ nhận cần tránh

- **“Khóa nạp lớp bảo vệ static initializer.”** Không phải vai trò của class-loading lock.
- **“Khóa `LC` là object mà `getClassLoadingLock` trả về.”** Không có hợp đồng API nào nói vậy.
- **“`synchronized (MyClass.class)` luôn chặn được JVM initialization.”** Không nên giả định; JVM có quy tắc `LC` nội bộ riêng.
- **“Parallel-capable nghĩa là hai threads cùng chạy `<clinit>` một class.”** Sai.
- **“Loading hoàn tất thì `<clinit>` đã chạy.”** Không nhất thiết.
- **“`findClass` chỉ gọi một lần trong mọi trường hợp.”** Sai nếu những lần lookup đều thất bại.
- **“Static initializer chạy một lần theo binary name trên toàn JVM.”** Sai nếu có hai defining loaders tạo hai runtime class identities.
- **“`<clinit>` thất bại thì gọi lại `loadClass` để thử initialization lại.”** Không; class đã có thể ở trạng thái erroneous.
- **“Khóa `LC` được giữ trong toàn bộ body static block.”** Không được suy luận từ JLS như vậy.
- **“Class-init deadlock sẽ hết nếu bật parallel-capable.”** Không; đây là hai mechanisms khác nhau.
- **“Constructor instance được serialize bằng class-init lock.”** Không sau khi class đã initialized.
- **“Thread dump có `BLOCKED` chắc chắn là loading-lock deadlock.”** Không: cần inspect stacks/monitor relationships.
- **“Khóa class initialization bảo vệ mọi shared mutable field của ứng dụng.”** Không; ứng dụng vẫn phải tự synchronize state.

## 20. Câu hỏi tự kiểm tra có đáp án

**1. API nào gắn với class-loading lock?** `ClassLoader.getClassLoadingLock(String)`.

**2. Loader chưa parallel-capable thường dùng khóa gì cho các tên khác nhau?** Chính loader instance.

**3. Parallel-capable giúp ích gì?** Cho phép khóa theo class name, giúp những class names khác nhau có thể nạp đồng thời.

**4. Ai quản lý class-initialization lock `LC`?** JVM theo thủ tục JLS §12.4.2, gắn với một runtime class/interface.

**5. `ClassLoader.loadClass` có tự chạy static block không?** Thông thường không yêu cầu initialization.

**6. Hai threads cùng active-use class chưa initialized có chạy `<clinit>` hai lần không?** Không; JVM điều phối cho một quá trình initialization đúng semantics.

**7. Có public Java API để lấy `LC` không?** Không.

**8. JVM có buộc giữ `LC` suốt body `<clinit>` không?** Không nên xem đó là bảo đảm; `LC` điều phối trạng thái và chờ.

**9. `registerAsParallelCapable` có thay đổi initialization semantics không?** Không.

**10. Vì sao loader có thể gọi `findClass` hai lần cho cùng tên?** Nếu lookup không tìm được class, lần sau có thể thử lại; chỉ successful definition mới tạo class để reuse.

**11. Khi `<clinit>` ném exception, lần active use sau có thể thấy gì?** `NoClassDefFoundError` do class ở trạng thái erroneous.

**12. Hai loaders định nghĩa cùng binary name có cùng initialization state không?** Không; chúng tạo hai runtime type identities.

**13. Tại sao hai static initializers có thể deadlock?** Hai threads có thể chờ nhau khi mỗi initializer active-use class mà thread còn lại đang initialize.

**14. `<init>` khác `<clinit>` ở đâu?** `<init>` là constructor instance, `<clinit>` là initialization của runtime class/interface.

**15. Debug thread hang nên xem gì trước?** Thread stack, loader object/parent, `<clinit>` state, application locks và runtime class identity.

## 21. Tài liệu chuẩn

- [Java SE 17 `ClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, `getClassLoadingLock` và `registerAsParallelCapable`.
- [Java Language Specification 17 — §12.4.2 Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): `LC`, same-thread recursion, waiting, initialized và erroneous states.
- [Java Language Specification 17 — §12.4.1 When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1): active use, constant variables và initiation.
- [Java Virtual Machine Specification 17 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading, linking, initialization, defining loader và runtime type identity.
- [Java SE 17 `CountDownLatch`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): synchronization tự thiết kế trong ví dụ để có quan sát xác định.

**Kết luận:** **Class-loading lock** phối hợp công việc **tra cứu/định nghĩa class qua một loader**, có thể tuần tự toàn loader hoặc khóa theo tên khi parallel-capable. **Class-initialization lock `LC`** là **cơ chế JVM theo runtime class/interface**, bảo đảm initialization tuân thủ trạng thái và thread ownership. Hai locks có thể xuất hiện gần nhau trong một lần sử dụng class, nhưng **khác đối tượng, khác phạm vi, khác lỗi và không thay thế nhau**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-class-loading-008">Thiết kế cache bytecode và nguồn tài nguyên an toàn cho nhiều luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế cache bytecode và nguồn tài nguyên an toàn cho nhiều luồng trong Java ClassLoader

Khi nhiều thread cùng yêu cầu nạp class hoặc resource từ JAR, thư mục, bộ nhớ hay nguồn từ xa, một custom `ClassLoader` phải giải quyết **hai bài toán đồng thời nhưng khác nhau**:

1. **Định nghĩa runtime class an toàn:** một defining loader không thể định nghĩa cùng một binary class name hai lần. Các thread cạnh tranh cần nhận **cùng một `Class<?>`**, không cùng gọi `defineClass`.
2. **Tìm và đọc bytecode/resource hiệu quả:** nhiều thread có thể cần cùng dữ liệu `.class`, ảnh, template, cấu hình. Cache phải bảo đảm không rò rỉ dữ liệu chưa hoàn tất, không đọc lặp vô ích, quản lý lỗi và giữ đúng version/generation của nguồn.

Điểm tinh tế: **cache `byte[]` không phải cache `Class<?>`**. JVM/ClassLoader đã giữ trạng thái class được nạp theo defining/initiating loader; cache bytes chỉ lưu **nguyên liệu để định nghĩa**, trong khi cache resource thường lưu **dữ liệu bất biến** hoặc metadata nguồn. Không được lấy một `Class<?>` do loader A định nghĩa rồi “tái sử dụng như class của loader B”.

Bài này giải thích hợp đồng đồng thời của `ClassLoader`, cách chọn cache key, safe publication, single-flight, read-through, invalidation, fail/retry, resource stream, performance, class unloading và ví dụ Java 17+ chạy được.

## 1. Ba tầng bộ nhớ không nên nhập làm một

~~~text
                   Nhiều application threads
                       |              |
                 loadClass(...)    readResource(...)
                       |              |
                       v              v
                  ClassLoader      Resource service
                       |              |
            findLoadedClass(name)   Cache data/metadata
                       |              |
             tìm bytecode .class    tìm bytes/resource
                       |              |
              defineClass(...)       InputStream mỗi caller
                       |
                       v
                    Class<?>
                       |
                 JVM type identity
            (binary name, defining loader)
~~~

| Tầng | Thực thể lưu | Mục đích | Lưu ý |
|---|---|---|---|
| JVM/class-loading state | Thông tin class đã load/define | Tránh định nghĩa lại kiểu trong cùng loader, phục vụ liên kết kiểu | `findLoadedClass` liên quan initiating loader; dùng đúng quy tắc delegation |
| Bytecode source cache | `byte[]` hoặc kết quả đọc bytecode | Hạn chế I/O/transform/verify nguồn lặp | Phải gắn với source version/loader generation |
| Resource cache | Bytes, nội dung đã parse, metadata hoặc handle có vòng đời | Tránh đọc resource lặp | Không chia sẻ một `InputStream` đang bị consume |
| External cache (tùy chọn) | JAR index, HTTP artifact, disk cache | Giảm chi phí đọc từ xa | Cần integrity, expiration và policy version |

**Sai lầm phổ biến:** dùng `ConcurrentHashMap<String, Class<?>>` làm kho toàn cục cho tất cả loaders. Key chỉ có tên class không đủ, vì **type identity = (binary name, defining class loader)**. Ngoài ra, giữ tham chiếu mạnh tới `Class<?>` có thể vô tình giữ cả loader cũ sống lâu.

## 2. Tránh định nghĩa class trùng dù đã cache bytes
Cache `byte[]` chỉ lưu dữ liệu nguồn; nó **không có quyền thay thế** trạng thái class được JVM ghi nhận. Với `loadClass` mặc định, cùng tên được bảo vệ bởi `getClassLoadingLock(name)`, rồi `findLoadedClass` kiểm tra class đã được nạp trước khi `findClass` có cơ hội `defineClass`. Do đó hai thread cùng load `plugin.Task` thành công sẽ nhận một `Class<?>`, dù cache bytes có một hay nhiều lượt đọc.
Không gọi trực tiếp `findClass` từ nhiều threads để “sử dụng cache”; phương thức đó không thay thế synchronization của `loadClass`. Bài **Tránh race condition giữa findLoadedClass và defineClass** minh họa race bằng barrier.

## 3. Parallel loading và cache là hai tầng riêng
Subclass đăng ký **parallel-capable** từ static initializer cho phép `loadClass` mặc định khóa theo binary name thay vì toàn loader. Điều này chỉ giúp các **tên khác nhau** có cơ hội tiến hành đồng thời; không làm `ConcurrentHashMap`, `FutureTask`, stream hay resource service tự an toàn. Trong `findClass`, nên giữ buffer cục bộ và state chia sẻ đồng bộ đúng.
Nếu chỉ đổi nguồn bytes và vẫn parent-first, **chỉ override `findClass`**. Khi bắt buộc override `loadClass`, kiểm tra class đã nạp dưới cùng lock trước khi định nghĩa; xem các bài locking và race trước đó.

## 4. Cache key phải mô tả nguồn và version

Với **cache riêng cho một loader instance bất biến**, một class name có thể đủ làm key:

~~~text
class cache key = "plugin.impl.Task"
resource key    = "plugin/config.txt"
~~~

Tuy nhiên, với **cache chia sẻ giữa nhiều loader hoặc nhiều plugin generations**, key cần bổ sung:

~~~text
CacheKey = (
    artifact/source identity,
    artifact version or content hash,
    relative path / binary name,
    transformation pipeline version,
    possibly runtime target version
)
~~~

Vì sao?

- Hai plugin có thể chứa `plugin.impl.Task` cùng tên nhưng bytecode khác nhau.
- Cùng JAR path có thể bị ghi đè từ v1 sang v2; **đường dẫn không chứng minh nội dung bất biến**.
- Class transformer/instrumentation có thể thay bytes theo cấu hình khác.
- Multi-release JAR có thể phục vụ bytes khác nhau theo phiên bản runtime.
- Resource trong nhiều plugin có thể cùng path `config.properties` nhưng khác nội dung.
- Nếu file nguồn thay đổi giữa hai lần đọc, cache không nên âm thầm tạo lớp và resource thuộc hai versions khác nhau.

Với cache local của một loader gắn với một deployment bất biến, cách đơn giản nhất thường là **tạo loader mới mỗi plugin generation**, không hot-swap bytes của class đã định nghĩa bên trong một loader cũ.

## 5. Safe publication và immutability của dữ liệu cached

Một `ConcurrentHashMap` giúp các thread cùng truy cập map an toàn, nhưng **không tự biến giá trị mutable thành immutable**:

~~~java
ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();
~~~

`byte[]` có thể bị code khác chỉnh sửa sau khi lưu. Nếu một thread biến đổi nội dung trong khi thread khác gọi `defineClass` hoặc đọc resource, runtime có thể gặp bytecode hỏng hoặc dữ liệu không nhất quán.

Quy tắc:

1. Khi nhận byte[] từ nguồn ngoài, tạo **snapshot** trước khi công bố nếu bên ngoài vẫn có thể thay đổi array.
2. Không trả trực tiếp mutable `byte[]` từ public cache API cho caller không tin cậy.
3. Nếu resource phải có stream, tạo **một `ByteArrayInputStream` riêng cho mỗi caller** từ snapshot bất biến, thay vì cache/đưa cùng một `InputStream` cho nhiều thread.
4. Giữ metadata gắn với snapshot như source URI, content hash, generation; không cập nhật từng trường rời rạc khiến reader nhìn thấy “metadata v2 + bytes v1”.
5. Dùng object immutable (ví dụ record chứa bản sao bytes/metadata) hoặc cache entry không còn bị sửa sau công bố.

### Vì sao không cache một `InputStream` dùng chung?

`InputStream` có trạng thái vị trí đọc. Hai thread đọc cùng một stream sẽ tranh chấp con trỏ và có thể nhận dữ liệu thiếu, trùng hoặc xen kẽ. Một thread đóng stream còn làm thread kia thất bại.

**Nên cache bytes hoặc thông tin nguồn**, rồi cấp stream mới:

~~~java
byte[] bytes = immutableCachedBytes;
try (InputStream in = new ByteArrayInputStream(bytes)) {
    // Mỗi caller có vị trí đọc độc lập.
}
~~~

Đây là mảnh minh họa; bảo đảm `bytes` thật sự không bị mutation bên ngoài trong suốt thời gian stream tồn tại.

## 6. Single-flight: nhiều request, một lần tải nguồn

Có ba chiến lược caching thường gặp:

| Cách | Có ngăn duplicate I/O khi cache miss? | Ghi chú |
|---|---|---|
| `HashMap` thường, không khóa | Không; còn có data races | Không dùng chia sẻ đa luồng |
| `ConcurrentHashMap.get` rồi `putIfAbsent` sau khi I/O | **Không chắc**: hai thread vẫn đọc I/O trước khi một thread thắng `putIfAbsent` | Safe publication có thể có, nhưng duplicate work |
| `ConcurrentHashMap.computeIfAbsent` với I/O trong mapping function | Atomic cho key, có thể ngăn duplicate computation | Mapping function phải ngắn, không update lại map; I/O dài gây blocking |
| `ConcurrentHashMap<Key, FutureTask<byte[]>>` và `putIfAbsent` trước khi chạy task | Có: nhiều caller chờ cùng một future | Kiểm soát lỗi/retry và không chạy I/O dưới CHM mapping lock |
| `CompletableFuture` placeholder + producer | Có nếu dùng put-if-absent trước khi producer tải | Cần complete/completeExceptionally, timeout/cancel/lifecycle |

### Vì sao không đặt network I/O tùy ý trong `computeIfAbsent`?

Theo `ConcurrentHashMap.computeIfAbsent` JDK 17, lệnh tính mapping chạy atomically; một số update khác có thể bị block trong lúc đang tính, do đó mapping function nên **ngắn và đơn giản**. Mapping function không được sửa chính map trong khi chạy; update đệ quy có thể ném `IllegalStateException`.

Ví dụ không nên dùng cho network I/O nặng:

~~~java
byte[] b = cache.computeIfAbsent(key, k -> {
    // Không khuyến nghị: HTTP/disk I/O lâu trong mapping function.
    return slowRemoteDownload(k);
});
~~~

Đây là **pseudocode minh họa** với `cache`, `key` và `slowRemoteDownload` tự triển khai, không phải chương trình độc lập. Ngoài blocking, ngoại lệ checked từ nguồn cũng phải chuyển đổi và quản lý cẩn thận.

**Phương án minh bạch hơn:** map từ key sang `FutureTask<byte[]>`. Thread thắng `putIfAbsent` thực hiện đọc I/O bên ngoài thao tác update map, các thread còn lại chờ cùng task. Nếu task thất bại, remove đúng `(key, future)`, tránh cache mãi lỗi tạm thời.

## 7. Ví dụ Java 17+ hoàn chỉnh: cache bytecode và resource đa luồng

Chương trình dưới đây dùng:

- Một loader kế thừa trực tiếp `ClassLoader` và đăng ký `parallel-capable`.
- Method `findClass` đọc `plugin/Task.class` qua cache **single-flight**.
- Một API riêng `openResource` đọc `plugin/config.txt` qua cache tương tự, trả stream mới cho mỗi caller.
- `ConcurrentHashMap<String, FutureTask<byte[]>>` để chia sẻ một lần đọc theo key.
- `Executors.newFixedThreadPool(8)` cho **24 tác vụ**, tất cả yêu cầu cùng `plugin.Task` và cùng resource.
- Counter cho **số lần đọc nguồn thành công**, không phải số lượt gọi API.

Mục tiêu là chứng minh **các thread nhận cùng runtime class, dữ liệu resource đầy đủ và chỉ một lần đọc class file + một lần đọc resource file** trên cache miss thành công.

### 7.1. Cấu trúc thư mục

~~~text
project/
  src/
    plugin/Task.java
    host/CacheDemo.java
  plugin-out/
    plugin/
      Task.class
      config.txt
  host-out/
    CacheDemo.class
    CacheDemo$DirectoryLoader.class
~~~

Hai thư mục output sinh từ lệnh `javac`. Plugin class **không được thêm vào application classpath** khi chạy host.

### 7.2. File `src/plugin/Task.java`

~~~java
package plugin;

public final class Task {
    public String message() {
        return "task-ok";
    }
}
~~~

### 7.3. File `src/host/CacheDemo.java`

~~~java
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CacheDemo {

    static final class DirectoryLoader extends ClassLoader {
        static {
            if (!registerAsParallelCapable()) {
                throw new ExceptionInInitializerError(
                        "Cannot register parallel class loader");
            }
        }

        private final Path root;
        private final ConcurrentHashMap<String, FutureTask<byte[]>> flights =
                new ConcurrentHashMap<>();

        private final AtomicInteger classReads = new AtomicInteger();
        private final AtomicInteger resourceReads = new AtomicInteger();

        DirectoryLoader(Path root) {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root.toAbsolutePath().normalize();
        }

        private byte[] loadOnce(String key, String relativePath,
                                AtomicInteger successfulReads)
                throws IOException {

            Path source = root.resolve(relativePath).normalize();
            if (!source.startsWith(root)) {
                throw new IOException("Outside configured directory");
            }

            FutureTask<byte[]> future = flights.get(key);

            if (future == null) {
                FutureTask<byte[]> candidate = new FutureTask<>(() -> {
                    byte[] data = Files.readAllBytes(source);
                    successfulReads.incrementAndGet();
                    return data;
                });

                FutureTask<byte[]> previous =
                        flights.putIfAbsent(key, candidate);

                if (previous == null) {
                    future = candidate;
                    candidate.run();
                } else {
                    future = previous;
                }
            }

            try {
                return future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for " + key, ex);
            } catch (ExecutionException ex) {
                flights.remove(key, future);
                throw new IOException("Failed loading " + key, ex.getCause());
            }
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!"plugin.Task".equals(name)) {
                throw new ClassNotFoundException(name);
            }

            try {
                byte[] bytes = loadOnce(
                        "class:plugin.Task",
                        "plugin/Task.class",
                        classReads);

                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }

        // API resource riêng: không giả vờ thay ClassLoader.getResource.
        InputStream openResource(String name) throws IOException {
            if (!"plugin/config.txt".equals(name)) {
                throw new FileNotFoundException(name);
            }

            byte[] data = loadOnce(
                    "resource:" + name, name, resourceReads);

            // Mỗi caller có một con trỏ stream đọc riêng.
            return new ByteArrayInputStream(data);
        }

        int classReads() {
            return classReads.get();
        }

        int resourceReads() {
            return resourceReads.get();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: CacheDemo <plugin-directory>");
        }

        DirectoryLoader loader =
                new DirectoryLoader(Path.of(args[0]));

        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Class<?>>> results = new ArrayList<>();

        try {
            for (int i = 0; i < 24; i++) {
                Callable<Class<?>> action = () -> {
                    ready.countDown();
                    go.await();

                    Class<?> type = loader.loadClass("plugin.Task");

                    try (InputStream stream =
                            loader.openResource("plugin/config.txt")) {
                        String content = new String(
                                stream.readAllBytes(),
                                StandardCharsets.UTF_8).strip();

                        if (!"config-ok".equals(content)) {
                            throw new IllegalStateException(
                                    "Unexpected resource: " + content);
                        }
                    }

                    return type;
                };

                results.add(workers.submit(action));
            }

            // Đợi 8 worker threads bắt đầu rồi cùng mở cổng.
            ready.await();
            go.countDown();

            Class<?> first = results.get(0).get();
            boolean sameClass = true;

            for (Future<Class<?>> result : results) {
                if (result.get() != first) {
                    sameClass = false;
                }
            }

            System.out.println("parallel capable: "
                    + loader.isRegisteredAsParallelCapable());
            System.out.println("same runtime class: " + sameClass);
            System.out.println("defined by custom loader: "
                    + (first.getClassLoader() == loader));
            System.out.println("successful class reads: "
                    + loader.classReads());
            System.out.println("successful resource reads: "
                    + loader.resourceReads());
            System.out.println("tasks completed: " + results.size());
        } finally {
            go.countDown(); // Không để task đợi cổng nếu main lỗi.
            workers.shutdown();
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        }
    }
}
~~~

### 7.4. Biên dịch và chạy

Các lệnh dùng Bash, chạy trong thư mục `project`:

~~~bash
mkdir -p plugin-out/plugin host-out

javac -d plugin-out src/plugin/Task.java

printf 'config-ok\n' > plugin-out/plugin/config.txt

javac -d host-out src/host/CacheDemo.java

java -cp host-out CacheDemo plugin-out
~~~

**Lưu ý:** lệnh chạy chỉ đưa `host-out` vào application classpath. `plugin-out` là thư mục dữ liệu/bytecode mà custom loader sẽ tìm; không thêm nó vào `-cp`, nếu không ví dụ không còn tách bạch host/plugin loading.

**Stdout dự kiến khi mọi file có sẵn và các tác vụ hoàn tất thành công:**

~~~text
parallel capable: true
same runtime class: true
defined by custom loader: true
successful class reads: 1
successful resource reads: 1
tasks completed: 24
~~~

Đây là kết quả có chủ đích của cache key cố định, source files không đổi, single-flight và class loader locking. Thứ tự hoàn thành task bên trong thread pool không xác định, nhưng chương trình **không in log per thread**, vì vậy các dòng trên vẫn theo thứ tự in của `main`.

### 7.5. Vì sao đúng dù 24 tác vụ chạy đồng thời?

- `registerAsParallelCapable` thành công khi loader class được khởi tạo, vì kế thừa trực tiếp `ClassLoader` đã parallel-capable.
- Các lời gọi `loadClass("plugin.Task")` trên cùng loader dùng class-loading lock cho tên `plugin.Task`; một lần `findClass` thành công sẽ `defineClass`, các lần khác dùng class đã nạp.
- `loadOnce` dùng `putIfAbsent` để công bố một `FutureTask` cho mỗi key **trước** khi bắt đầu đọc file; chỉ một thread chạy task cho cache miss.
- Các thread khác gọi `future.get()` để đợi; completion của FutureTask công bố data an toàn cho các thread nhận kết quả.
- `resourceReads == 1` vì mọi lời gọi mở `plugin/config.txt` dùng cùng key `resource:plugin/config.txt`.
- Mỗi lời gọi `openResource` trả **ByteArrayInputStream mới**; các task không giành một con trỏ stream dùng chung.
- Cả 24 kết quả là cùng `Class<?>` do custom loader định nghĩa. Không thread nào tự gọi `defineClass` bên ngoài đường `loadClass` có lock.

**Cần nói rõ giới hạn ví dụ:** loader cố ý chỉ nạp **một binary class name** và cho đọc **một resource name** đã biết. Việc dùng whitelist tránh đưa bytecode path tùy ý vào demo. Đây là mẫu học về concurrency/caching, chưa phải loader production tổng quát hỗ trợ package sealing, code signing, mọi định dạng nguồn hay sandbox.

### 7.6. Kiểm tra lỗi I/O và retry

Nếu `plugin/config.txt` không có, `Files.readAllBytes` làm `FutureTask` thất bại. `ExecutionException` được chuyển thành `IOException` và mapping lỗi bị loại bằng `flights.remove(key, future)`.

Tác dụng:

- Kết quả lỗi tạm thời **không bị cache vĩnh viễn**.
- Request tiếp theo có cơ hội thử lại nếu file xuất hiện.
- `remove(key, future)` không xóa nhầm một future khác được thêm sau đó.
- Nhiều request cùng chờ một future thất bại có thể nhận cùng lỗi; khi future bị remove, request mới có thể khởi tạo **một lần tải mới**. Vì vậy không nên khẳng định “tối đa một lần I/O trong suốt đời loader” **khi có lỗi và retry**.
- Một hệ thống production nên có backoff/jitter và phân biệt lỗi vĩnh viễn (file không hợp lệ, tên không được phép) với lỗi tạm thời (I/O, timeout).

### 7.7. Vì sao không gọi `defineClass` trong FutureTask?

Cache future trong ví dụ chỉ quản lý **bytes đọc từ nguồn**. `defineClass` được gọi trong `findClass` và thực hiện dưới sự điều phối của `loadClass`. Nếu đặt `defineClass` vào task chia sẻ chung giữa loaders hoặc ngoài classloading lock, rất dễ làm lẫn bytecode caching với type definition và gặp duplicate definition.

Đặc biệt, **không cache `Class<?>` toàn cục** rồi trả về từ loader khác chỉ dựa vào tên binary. JVM yêu cầu class identity và loading constraints được xử lý đúng theo loader hierarchy.

## 8. Bytecode cache không cần tồn tại mãi

Ví dụ giữ `FutureTask<byte[]>` trong `flights` suốt vòng đời loader. Nó phù hợp demo nhỏ nhưng có thể lãng phí trong hệ thống nhiều class:

- Sau khi JVM đã định nghĩa class, thường **không cần giữ bytecode `byte[]` mãi** trong cache của loader chỉ để phục vụ `loadClass` sau này; `findLoadedClass` và JVM đã có trạng thái runtime class.
- Nếu transformation/verification cần bytes để audit hoặc nhiều source loader dùng lại một artifact, cache có lợi; cần memory budget.
- Dùng **bounded cache**, policy LRU/weight-based hoặc eviction rõ ràng cho source bytes/resources lớn.
- Nếu tính năng `reload` được yêu cầu, **thay bytes trong cache không thay định nghĩa class đã có**. Một plugin generation mới thường cần **loader instance mới**.
- Với cache dùng chung nhiều loaders, key phải chứa source version/content hash. Không trộn bytes v1/v2 của cùng class path.
- Thực hiện snapshot artifact bất biến trong một generation thay vì đọc từng file của deployment đang bị ghi đè.

Một chiến lược thường tốt hơn cache bytes không giới hạn là **cache index và immutable artifact metadata**, để mỗi loader giữ trạng thái runtime của chính nó; dữ liệu lớn có thể chia sẻ có kiểm soát theo content hash và bị evict khi không dùng.

## 9. Cache tài nguyên: byte[] hay parsed object?

| Dạng cache | Phù hợp khi | Rủi ro |
|---|---|---|
| `byte[]` snapshot | Resource nhỏ, đọc lặp, cần stream độc lập | Heap tăng; phải tránh mutation và giới hạn kích thước |
| Immutable parsed config | Dữ liệu có cấu trúc, nhiều thread cùng đọc | Parser/representation phải thread-safe và immutable |
| URI/Path + metadata | File lớn, ít đọc, muốn tránh giữ bytes | Mỗi caller vẫn làm I/O, có race nếu source đổi |
| Open `InputStream` | **Không nên chia sẻ giữa threads** | Con trỏ đọc chung, close race, handle leak |
| Mmap/FileChannel (chuyên dụng) | Tệp lớn, random access được thiết kế có kiểm soát | Lifetime mapping/channel, OS resources, bounds/cleanup |
| Cache future của bytes | Nhiều concurrent misses cùng key | Phải xử lý failure, cancellation, invalidation và wait cycles |

Đối với resource lớn, `readAllBytes()` có thể tốn bộ nhớ. Dùng stream riêng từng request hoặc giới hạn byte count; không cache mọi dữ liệu chỉ vì có nhiều thread.

### Reader/writer đồng thời khi cấu hình thay đổi

Nếu cần reload resource đang được đọc, tránh sửa **byte[] tại chỗ**:

~~~text
generation 1 -> immutable snapshot v1
generation 2 -> immutable snapshot v2

Reader A lấy v1 và đọc nhất quán đến hết.
Reader B bắt đầu sau publish mới lấy v2.
~~~

Có thể dùng một immutable snapshot object được thay atomically qua `AtomicReference<Snapshot>` hoặc map versioned. Đối với nhiều resource cần cập nhật nhất quán, thay **toàn bộ snapshot/generation** trong một bước, thay vì invalidate từng key không có giao thức.

**Không dùng cùng quy tắc để “reload” class đã định nghĩa.** JVM không thay class identity chỉ vì source bytes thay; cần lớp cơ chế khác như loader generation mới hoặc instrumentation/redefinition với giới hạn riêng.

## 10. Cẩn thận với tương tác locks, Future và I/O

### Lock ordering

Per-class load lock giúp tránh duplicate definition theo tên, nhưng nếu `findClass(A)` giữ lock A rồi chờ một thao tác khác cần lock B, trong khi thread khác giữ lock B rồi chờ lock A, có thể deadlock.

~~~text
Thread 1: lock A -> waits on B
Thread 2: lock B -> waits on A
=> cyclic wait
~~~

Parallel-capable **giảm** việc chặn toàn loader, nhưng không tự loại bỏ mọi deadlock nếu delegation graph, transformation, cache callbacks hoặc plugin initialization tạo quan hệ chờ vòng.

Nên giữ lock duration gọn, ưu tiên hierarchical delegation đơn giản, tránh callbacks gọi nạp class khác khi đang xử lý key trong cache, và không dùng synchronization global không cần thiết.

### FutureTask và self-wait

Trong ví dụ, producer chỉ gọi `Files.readAllBytes` cho path cố định; nó không đệ quy gọi `loadOnce` với chính key đang chạy. Trong hệ thống phức tạp, nếu code của producer lại gọi `future.get()` lên future của chính nó, thread sẽ **tự đợi vô hạn**. Vì vậy cần cấm recursive load của cùng key hoặc phát hiện cycle khi xử lý dependencies từ xa.

### Interruption và cancellation

Một waiter bị interrupt nên:

- Khôi phục interrupt flag bằng `Thread.currentThread().interrupt()`.
- Trả lỗi theo hợp đồng gọi.
- **Không tùy ý cancel future chung**, vì nhiều caller khác còn chờ nó.

Timeout có thể hữu ích cho remote I/O, nhưng chỉ áp dụng ở tầng thật sự điều khiển network read/cancellation. `Future.get(timeout)` ở waiter không có nghĩa producer đã ngừng chạy.

### Race khi close source

Đừng đóng `URLClassLoader` hay file/resource provider trong lúc thread khác đang nạp class. Javadoc `URLClassLoader.close()` nêu rằng nếu thread khác đang load khi gọi close, kết quả load ấy **không được xác định**.

Một plugin runtime có thể dùng lifecycle state:

~~~text
ACTIVE -> DRAINING -> CLOSED

ACTIVE: nhận request mới
DRAINING: ngừng nhận mới, đợi in-flight operations xong
CLOSED: đóng loader, stream và underlying sources
~~~

Với cache có background executor hoặc remote download, phải dừng producer, cancel nhiệm vụ không còn dùng theo hợp đồng, và không công bố dữ liệu của generation đã đóng vào loader mới.

## 11. Cache negative results và sai lầm khi retry

Không tìm thấy class/resource cũng là một kết quả có ý nghĩa, nhưng cache lỗi không đúng có thể tạo bug:

- Nếu cache vĩnh viễn `ClassNotFoundException` trong loader mà source URL còn có thể bổ sung file, plugin mới sẽ không bao giờ được tìm thấy dù file đã xuất hiện.
- Nếu cache `null` trong `ConcurrentHashMap`, thao tác put sẽ ném `NullPointerException` vì ConcurrentHashMap không chấp nhận null key/value.
- Nếu file bị lỗi tạm thời, retry ngay ở mọi thread có thể tạo **thundering herd** I/O/network. Dùng backoff/timeout hoặc cache lỗi có TTL ngắn khi thích hợp.
- Nếu artifact bất biến (generation đã đóng băng) và một class chắc chắn không tồn tại, negative cache có thể hợp lý, nhưng phải gắn với **artifact generation**.
- Nếu lỗi bytecode `ClassFormatError`, `VerifyError` hoặc `SecurityException`, không xử lý như lỗi network tạm thời; cần báo lỗi đúng và kiểm tra nguồn.
- Đừng nuốt `LinkageError` rồi thử định nghĩa class khác cùng tên như cách “retry”.

**Bài học:** policy invalidation cần theo **loại lỗi**, **độ tin cậy của nguồn** và **tính bất biến của generation**, không thể chỉ dựa vào “có catch exception”.

## 12. Performance: đo đúng bottleneck

Tăng parallelism không phải lúc nào cũng tăng throughput. Một số giới hạn:

| Chi phí | Nếu làm sai | Gợi ý |
|---|---|---|
| Disk/network reads | Nhiều thread cùng đọc một file | Single-flight cho cache misses; kiểm soát concurrency |
| CPU transform/verify | Làm lại bytecode transform nhiều lần | Cache kết quả theo hash + transform version khi hợp lệ |
| Class-loading locks | Giữ global loader lock cho mọi class | Đăng ký parallel-capable nếu thiết kế hỗ trợ |
| Cache memory | Giữ vô hạn `byte[]`, duplicate trong heap/metaspace | Giới hạn memory và eviction |
| Resource streams | Chia sẻ một stream hoặc quên close | Một stream mỗi caller, try-with-resources |
| Source snapshot | Đọc class v2 nhưng resource v1 khi deploy | Pin immutable generation và publish atomically |
| Logging | Log chi tiết mỗi class mỗi thread | Dùng sampling/structured metrics để giảm overhead |
| Retry | Lỗi I/O làm mọi thread cùng retry | Backoff + single-flight + error taxonomy |

### Số liệu nên thu thập

- `cache_hits` / `cache_misses` theo class/resource và generation.
- `inflight_loads`, số waiter, thời gian đợi future.
- `source_read_count` và latency của file/JAR/network.
- `class_load_failures` theo `ClassNotFoundException`, `LinkageError` và lỗi I/O.
- `resource_read_failures`, `resource_bytes_cached` và eviction count.
- CPU, heap pressure, metaspace và số lượng classloader đang sống.
- Nếu cần: phân tích JFR/JVM Unified Logging `-Xlog:class+load=info,class+unload=info` trên JDK thích hợp.

Không log toàn bộ bytecode, resource secrets hoặc path chứa credentials. Khi chẩn đoán, log source identifier/hash và loader identity có kiểm soát.

## 13. Test concurrency: những trường hợp bắt buộc

**Test 1 — Nhiều thread, cùng một class name:** trả cùng `Class<?>`, không có duplicate `defineClass` hay `LinkageError`.

**Test 2 — Nhiều thread, class names khác nhau:** với parallel-capable loader, có thể tiến hành cùng lúc nếu source/cache hỗ trợ; không gây race/deadlock.

**Test 3 — Cùng resource path:** mọi stream đọc trọn nội dung, không chia sẻ con trỏ đọc.

**Test 4 — Missing resource:** thất bại có nguyên nhân rõ, xử lý retry theo policy, không cache null vĩnh viễn.

**Test 5 — Missing class/dependency:** phân biệt `ClassNotFoundException` và `NoClassDefFoundError`; không mù quáng retry những lỗi linkage.

**Test 6 — Corrupt bytecode:** truyền đúng `ClassFormatError`/lỗi xác thực, không nuốt và ghi cache như dữ liệu hợp lệ.

**Test 7 — Source cập nhật giữa hai lần đọc:** xác định generation bất biến; không trộn bytes/config từ hai release.

**Test 8 — Cache pressure:** giới hạn memory, eviction đúng, không đuổi một future đang cần bởi loader trước khi trạng thái được xử lý.

**Test 9 — Shutdown và in-flight load:** ngừng request, drain, rồi close; không để thread khác gọi closed loader.

**Test 10 — Plugin redeploy:** loader cũ không giữ reference thừa qua cache tĩnh, TCCL, background executor; loader mới có generation riêng.

**Test 11 — Parent delegation:** nếu parent có class, custom child trả đúng parent class, không cố define lại không cần thiết.

**Test 12 — Resource duplicate names:** class policy và resource lookup policy được kiểm tra riêng nếu parent/child có cùng path.

**Test 13 — Interruption:** waiter được báo/carry interrupt đúng, không cancel shared producer vô ý.

**Test 14 — Cache failures:** sau failure tạm thời, một load mới có thể retry; metrics phản ánh số lần attempt thay vì tưởng cache luôn exactly once.

**Test 15 — Malicious/untrusted path:** xác thực tên class/resource và nguồn; tránh path traversal, symlink surprises, zip-slip khi giải nén JAR và tải bytecode chưa tin cậy.

## 14. Các lỗi thường gặp và cách nhận diện

| Triệu chứng | Nguyên nhân điển hình | Hướng xử lý |
|---|---|---|
| `LinkageError: duplicate class definition` | Hai đường gọi `defineClass` cùng tên/loader | Đưa vào `loadClass` lock, tránh gọi `findClass` trực tiếp |
| Deadlock class loading | Vòng delegation hoặc lock ordering giữa A/B | Thread dump, phân tích locks, parallel capability |
| `ClassNotFoundException` dù file tồn tại | Parent/source path/cache negative sai | Xem binary name, URL/JAR, generation |
| `NoClassDefFoundError` | Dependency vắng, init thất bại hoặc lỗi liên kết | Đọc `Caused by` và nguồn bytecode |
| Resource đọc thiếu trong nhiều threads | Chia sẻ một `InputStream` | Cache bytes + stream mới per caller |
| Resource không đổi sau deploy | Cache key không có version/generation | Invalidate/generation switch đúng thời điểm |
| GC giữ plugin cũ | Cache toàn cục giữ `Class<?>`/ClassLoader | Giảm strong references, quản lý lifecycle |
| `OutOfMemoryError: Java heap space` | Cache giữ bytes lớn vô hạn | Giới hạn kích thước, eviction/streaming |
| `IllegalStateException` trong `computeIfAbsent` | Recursive map update trong mapping function | Không update cùng map từ mapping callback |
| Nhiều lần tải file sau lỗi | Future bị remove để retry, nhiều request đến | Backoff và single-flight cho mỗi attempt |
| Load fail khi shutdown | Close source đúng lúc thread còn đọc | ACTIVE/DRAINING/CLOSED protocol |
| Class cache trả sai type | Cache global keyed chỉ theo class name | Key theo loader identity/generation; đừng cache Class xuyên loaders |

## 15. Quy tắc thực hành

**Nên làm:**

1. Kế thừa `ClassLoader` và override **`findClass`** nếu vẫn dùng delegation mặc định.
2. Đăng ký `parallel-capable` một lần ở static initialization khi thực sự thiết kế cho concurrency.
3. Để `getClassLoadingLock(name)` phối hợp class loading theo tên; không phát minh lock thứ hai bao trùm tất cả nếu không cần.
4. Sử dụng `ConcurrentHashMap` và immutable cache entries; single-flight khi cache miss tốn kém.
5. Phân biệt `Class<?>` runtime identity với `byte[]` source artifact.
6. Trả stream mới cho mỗi caller, đóng streams sớm và quản lý memory budget.
7. Pin version/content hash/generation, định nghĩa quy tắc invalidation và retry.
8. Log class/resource source cùng loader identity và nguyên nhân lỗi.
9. Đảm bảo shutdown đợi các operations đang chạy trước khi đóng source provider.
10. Test cạnh tranh dữ liệu, lỗi, thay version và lifecycle trên workload thực tế.

**Tránh làm:**

- Chia sẻ `HashMap`/`ArrayList` mutable không khóa cho loader cache giữa nhiều threads.
- Dùng `if (!map.containsKey(k)) map.put(k, read())` rồi tưởng thao tác atomic.
- Chia sẻ `InputStream` như một value của resource cache.
- Gọi `defineClass` lặp lại chỉ vì muốn “cache class”, hoặc giữ `Class<?>` vô hạn trong global static map.
- Đặt network I/O lâu và callback phức tạp trong `computeIfAbsent`.
- Coi `registerAsParallelCapable` là bảo đảm tự động cho mọi shared state của subclass.
- Coi `close` tương đương unloading ngay lập tức.
- Dựa vào classloader để chạy mã không tin cậy trong sandbox.

## 16. Tự kiểm tra

**Câu 1.** Hai thread đồng thời gọi `loader.loadClass("p.Task")`. Vì sao không nên để cả hai cùng tự gọi `defineClass` từ ngoài `loadClass`?

**Đáp án:** JVM không chấp nhận hai definition cùng binary name trong một defining loader; có thể gây `LinkageError`. Hãy dùng locking trong `loadClass` và kiểm tra class đã nạp.

**Câu 2.** Đăng ký `registerAsParallelCapable` có làm `HashMap` riêng của loader thread-safe không?

**Đáp án:** Không. Nó chỉ thay cơ chế class-loading locks; mọi cache mutable của subclass vẫn cần synchronization/safe publication.

**Câu 3.** `ConcurrentHashMap<String, byte[]>` đã bảo vệ byte[] khỏi bị thread khác chỉnh sửa chưa?

**Đáp án:** Chưa. Map thread-safe nhưng array vẫn mutable. Dùng snapshot bất biến, không trao mảng gốc cho code có thể mutate, hoặc cấp bản sao khi cần.

**Câu 4.** Vì sao dùng `FutureTask<byte[]>` với `putIfAbsent` thay vì để nhiều thread cùng tải bytes rồi mới `putIfAbsent`?

**Đáp án:** Placeholder future được công bố **trước khi** I/O bắt đầu, nên các thread cùng key chờ một task thay vì cùng làm duplicate I/O.

**Câu 5.** Có thể cache `InputStream` và đưa cùng một stream cho 24 thread không?

**Đáp án:** Không nên: stream có trạng thái đọc và vòng đời đóng, nên một thread có thể làm hỏng kết quả thread khác. Cache bytes bất biến rồi tạo stream mới.

**Câu 6.** Nếu muốn cập nhật `plugin.Task` từ bytecode v1 sang v2, sửa bytes trong cache của cùng ClassLoader có thay `Class<?>` đang dùng không?

**Đáp án:** Không. Runtime class đã định nghĩa không được tự thay bằng bytes mới qua cơ chế classloader thường. Dùng loader generation mới hoặc cơ chế class redefinition chuyên dụng với giới hạn riêng.

**Câu 7.** Vì sao `URLClassLoader.close()` cần phối hợp với in-flight loads?

**Đáp án:** API không xác định kết quả nếu một thread đang load class khi thread khác close loader. Nên dừng nhận tải mới và drain trước khi close.

**Câu 8.** Nếu `computeIfAbsent` chạy một network request mất vài giây, đó có phải luôn là thiết kế tốt không?

**Đáp án:** Không. JDK khuyến nghị mapping function ngắn/đơn giản vì có thể chặn các update khác; dùng future placeholder/single-flight và quản lý timeout/retry bên ngoài map operation.

**Câu 9.** Cache key chỉ chứa `plugin.Task` có đủ cho cache chia sẻ giữa hai plugin generations không?

**Đáp án:** Không. Cần cả artifact/source identity và version/hash/generation để tránh dùng nhầm bytes khác nội dung.

## 17. Sơ đồ thiết kế đề xuất

~~~text
                 Concurrent requests for key K
                           |
                           v
                  Check loader/generation
                           |
                           v
             CHM<K, FutureTask<bytes>>
                  /                \
           Future tồn tại         Chưa có
                 |                   |
             future.get()      putIfAbsent(new task)
                 |                   |
              nhận bytes        Người thắng run task
                 |                   |
                 +---------+---------+
                           |
                     Immutable bytes
                           |
           +---------------+----------------+
           |                                |
    Class-loading path                Resource path
           |                                |
      getClassLoadingLock(name)         new InputStream
           |                           per caller
       findLoadedClass
           |
       findClass / defineClass
           |
       Runtime Class<?>
           |
    Theo vòng đời loader riêng
~~~

**Ghi nhớ ba nguyên tắc:**

1. **Khóa class definition là trách nhiệm của class loading path.** Dùng `loadClass`/`getClassLoadingLock` đúng và chỉ định nghĩa một lần trong cùng loader.
2. **Bytecode/resource cache là một tầng khác.** Dùng key đúng version, dữ liệu bất biến, single-flight và stream độc lập.
3. **Lifecycle mới là yếu tố quyết định tính an toàn lâu dài.** Xử lý failure/retry, invalidation, memory budget và shutdown trước khi đóng loader/source.

### Tài liệu chính thức

- [Java SE 17 — ClassLoader: parallel-capable, getClassLoadingLock, loadClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — ConcurrentHashMap: computeIfAbsent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html#computeIfAbsent(K,java.util.function.Function))
- [Java SE 17 — FutureTask: concurrent computation và get](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/FutureTask.html)
- [Java SE 17 — URLClassLoader: close concurrency caveat](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html#close())
- [JVMS 17 §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)


</details>

- [Quay lại đầu trang](#back-to-top)
