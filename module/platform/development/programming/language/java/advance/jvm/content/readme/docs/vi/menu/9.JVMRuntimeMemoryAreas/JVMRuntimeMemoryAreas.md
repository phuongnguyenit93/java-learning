<a id="back-to-top"></a>

# Các vùng bộ nhớ

## Menu
- [Bộ nhớ riêng của luồng và bộ nhớ dùng chung trong đặc tả JVM](#jvm-runtime-memory-areas-001)
- [Thanh ghi pc và JVM stack trong quá trình thực thi một luồng](#jvm-runtime-memory-areas-002)
- [Heap lưu đối tượng và mảng; phân biệt đối tượng với biến tham chiếu](#jvm-runtime-memory-areas-003)
- [Method area và run-time constant pool trong mô hình trừu tượng](#jvm-runtime-memory-areas-004)
- [Native method stack và ranh giới lời gọi mã native](#jvm-runtime-memory-areas-005)
- [HotSpot Metaspace, compressed class space và sự khác biệt với PermGen lịch sử](#jvm-runtime-memory-areas-006)
- [Code cache lưu mã máy do JVM tạo ra](#jvm-runtime-memory-areas-007)
- [Bộ nhớ ngoài heap: direct buffer, thư viện native và cấu trúc nội bộ](#jvm-runtime-memory-areas-008)
- [Phân biệt bộ nhớ đã dùng, đã cam kết, đã dành trước và bộ nhớ tiến trình](#jvm-runtime-memory-areas-009)

## <a id="jvm-runtime-memory-areas-001">Bộ nhớ riêng của luồng và bộ nhớ dùng chung trong đặc tả JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Bộ nhớ riêng của luồng và bộ nhớ dùng chung trong đặc tả JVM

## 1. Bức tranh tổng thể

Java Virtual Machine Specification (JVMS) định nghĩa nhiều **run-time data areas**. Có vùng tồn tại cho toàn bộ JVM và được mọi luồng sử dụng, có vùng được tạo riêng cho từng Java thread. Phân biệt chính xác giúp giải thích vì sao hai luồng có local variables độc lập nhưng vẫn có thể cùng sửa một object; vì sao đệ quy sâu thường liên quan tới stack; và vì sao OutOfMemoryError không nhất thiết có nghĩa Java heap đã đầy.

**Theo JVMS §2.5:**

| Vùng | Phạm vi | Vai trò |
| --- | --- | --- |
| **`pc` register** | **Mỗi thread riêng** | Ghi địa chỉ instruction JVM hiện tại khi chạy Java method không phải native |
| **JVM stack** | **Mỗi thread riêng** | Chứa các frame tương ứng những lời gọi method đang hoạt động |
| **Native method stack** | **Thường theo thread nếu implementation có sử dụng** | Hỗ trợ native methods và thành phần VM cần conventional native stacks |
| **Heap** | **Chung cho mọi JVM threads** | Cấp phát class instances và arrays, được quản lý bằng GC |
| **Method area** | **Chung cho mọi JVM threads** | Per-class structures, field/method information, code, run-time constant pools |
| **Run-time constant pool** | **Mỗi class/interface một pool**, cấp phát trong method area dùng chung | Constants và symbolic references để liên kết |
| **Frame** | **Mỗi method invocation trong thread riêng** | Local-variable array, operand stack, tham chiếu tới run-time constant pool |

Cần phân biệt **phạm vi logic** của JVMS với **vị trí vật lý trong RAM**. JVM implementation, JIT compiler và garbage collector có quyền chọn layout/technique khác nhau miễn giữ đúng hành vi đã được đặc tả. Ví dụ, HotSpot Metaspace là kỹ thuật hiện thực class metadata, **không phải tên của vùng bắt buộc trong JVMS**.

### Sơ đồ liên hệ

~~~text
                         ONE JVM
+---------------------------------------------------------+
|                                                         |
| THREAD A                              THREAD B           |
| +--------------------+                +----------------+ |
| | pc A               |                | pc B           | |
| | JVM stack A        |                | JVM stack B    | |
| |   frame run()      |                |   frame run()  | |
| |   - locals         |                |   - locals     | |
| |   - operand stack  |                |   - operand    | |
| |   - pool reference |                |     stack      | |
| |   frame helper()   |                |   - pool ref   | |
| | native stack (*)   |                | native stack(*)| |
| +--------------------+                +----------------+ |
|                  |                        |             |
|                  v                        v             |
| +-----------------------------------------------------+ |
| | HEAP: objects, arrays                              | |
| |   shared Counter object, int[] results, ...        | |
| +-----------------------------------------------------+ |
| +-----------------------------------------------------+ |
| | METHOD AREA: per-class structures, method code     | |
| |   constant pool of Class X                         | |
| |   constant pool of Class Y                         | |
| +-----------------------------------------------------+ |
+---------------------------------------------------------+

(*) Chỉ nếu implementation dùng native method stacks.
~~~

Trong sơ đồ, hai frames khác nhau có thể cùng tham chiếu một pool vì chúng thực thi method thuộc cùng runtime class; điều đó **không khiến hai operand stacks hoặc local arrays thành một**.

## 2. pc register: trạng thái điều khiển riêng cho từng thread

Theo **JVMS §2.5.1**, mỗi JVM thread có một **pc register logic**. Khi thread chạy method Java không phải native, pc giữ địa chỉ instruction JVM đang thực thi; khi chạy native method, giá trị pc trong mô hình JVMS **không được xác định**. Hai threads chạy cùng method có thể ở hai bytecode offsets khác nhau. Pc **không** phải source line và không nhất thiết trùng CPU instruction pointer; `javap -c` và `LineNumberTable` cho thông tin liên quan ở **class file**, không phải API đọc pc chính xác của thread bất kỳ.

## 3. JVM stack: chuỗi frames riêng theo thread

Theo **JVMS §2.5.2**, mỗi JVM thread có một **JVM stack** chứa các frames của những method invocations đang hoạt động. Nếu thread A gọi `main → calculate → add`, ba frames logic nằm trên JVM stack A. Thread B có stack và frames riêng, dù có thể cùng gọi `add`.

~~~text
Thread A                      Thread B
pc A                          pc B
JVM stack A                   JVM stack B
  ├── frame add                  └── frame run
  ├── frame calculate
  └── frame main

Shared heap: references trong hai stacks có thể cùng trỏ tới một object.
~~~

**JVM stack không phải operand stack.** JVM stack chứa nhiều frames; mỗi frame lại chứa một operand stack riêng. JVMS không buộc tất cả frames có một layout native RAM cố định, và một implementation có thể áp dụng JIT/deoptimization hoặc các cách biểu diễn khác mà vẫn giữ đúng ngữ nghĩa. Đệ quy quá sâu có thể gây `StackOverflowError`; thiếu tài nguyên cấp/mở rộng stack có thể gây `OutOfMemoryError`. Cả hai không tự chứng minh Java heap đã đầy.

## 4. Frame: locals, operand stack và tham chiếu tới pool

Theo **JVMS §2.6**, một frame có **local variables array**, **operand stack** và tham chiếu tới **run-time constant pool** phục vụ dynamic linking. Mỗi invocation giữ locals của chính nó; một local chứa reference có thể trỏ tới object trên **shared heap**.

| Method | Local slot 0 | Các slots tiếp theo |
| --- | --- | --- |
| `static int add(int a,int b)` | `a` | `b`, rồi có thể local result |
| `int add(int a,int b)` | `this` | `a`, `b` |
| `static long sample(long x)` | phần đầu `x` | `x` chiếm tổng cộng hai slots |

`iload` nạp giá trị local lên operand stack; `iadd` lấy hai `int` trên đỉnh rồi đẩy kết quả; `ireturn` trả về caller. `long`/`double` chiếm hai local-variable slots hoặc hai units depth của operand stack. `max_locals` và `max_stack` trong `Code` attribute là thông số của **một method**, không phải giới hạn số frames trên stack. `LocalVariableTable` là metadata debug tùy chọn; `StackMapTable` hỗ trợ bytecode verification chứ không phải snapshot stack đang chạy.

Bài chuyên sâu về pc/JVM stack ngay sau đây sẽ đi vào **call/return, exception unwinding, recursion và StackWalker**; mục tiêu ở bài tổng quan này là nhận diện **ranh giới giữa bộ nhớ per-thread và bộ nhớ shared**.

## 5. Native method stacks: thường riêng thread, không nhất thiết có trên mọi JVM

**JVMS §2.5.6** cho phép implementation dùng conventional native stacks để hỗ trợ native methods (thường viết bằng C/C++) hoặc một số phần của VM/interpreter. Nếu có, chúng **thường được cấp phát theo thread**.

Nhưng đặc tả **không bắt buộc mọi JVM phải có native method stacks**. JVM implementation không hỗ trợ native methods và không dựa vào conventional native stacks có thể không cung cấp chúng.

Khi có native stacks:

- Nếu computation cần native stack lớn hơn mức cho phép, VM có thể ném **`StackOverflowError`** theo quy tắc.
- Nếu không cấp hoặc mở rộng được native stack theo các điều kiện đã mô tả, có thể ném **`OutOfMemoryError`**.
- Cách ánh xạ Java frames và native frames sang native memory là chi tiết implementation.

Đoạn minh họa cú pháp Java:

~~~java
public class NativeDeclaration {
    public native int transform(int input);
}
~~~

Đây **không phải chương trình có thể gọi thành công** nếu chưa có native implementation và library tương ứng. Không lấy lỗi `UnsatisfiedLinkError` do chưa triển khai native code làm bằng chứng stack JVM bị thiếu.

## 6. Heap: object/array dùng chung nhưng reference có thể riêng

Theo **JVMS §2.5.3**, JVM tạo heap khi startup. Heap **dùng chung cho mọi JVM threads** và cấp phát **class instances cùng arrays**. Garbage collector tự động quản lý việc thu hồi storage khi phù hợp; Java source không có thao tác `free(object)` như C.

~~~text
Thread A frame                         Thread B frame
 local[1] = ref ------+       +------ local[0] = ref
                      v       v
                  HEAP OBJECT
                +----------------+
                | Counter        |
                | count=2        |
                +----------------+
~~~

Hai reference locals **nằm ở hai frames riêng** nhưng có thể cùng trỏ đến một Counter object. Nếu Counter có field mutable, cả hai threads có thể truy cập field ấy.

Xét:

~~~java
Counter c = new Counter();
int number = 7;
~~~

Nếu đoạn này nằm trong một method:

- Local `number` có một **primitive value** của invocation đó.
- Local `c` giữ một **reference value**, không chứa nguyên object.
- `new Counter()` tạo **Counter object trên heap** theo mô hình JVM.
- Khi frame kết thúc, việc mất local reference **không có nghĩa object lập tức bị GC thu hồi**; còn các references/roots khác hoặc runtime liveness.

**Không được suy** “bất cứ object nào tạo trong method đều nằm trên JVM stack” hoặc “object không còn dùng sẽ bị thu hồi ngay đúng lúc method return”.

### Heap chung không có nghĩa thread-safe

Ví dụ (đoạn minh họa trong class):

~~~java
class UnsafeCounter {
    int count;
    void increment() {
        count++; // read + add + write: không phải atomic increment
    }
}
~~~

Nếu nhiều threads gọi `increment` trên **cùng object** mà không có synchronization phù hợp, có thể có lost updates. Shared heap chỉ cho phép chia sẻ; quy tắc visibility/ordering/happens-before thuộc **Java Memory Model, JLS §17**, không tự sinh ra vì object nằm trên heap.

## 7. Method area: dữ liệu class dùng chung

Theo **JVMS §2.5.4**, method area:

- Là vùng **chia sẻ giữa mọi JVM threads**, được tạo khi VM startup.
- Chứa **per-class structures**: run-time constant pool, field/method data và code cho methods/constructors, kể cả special initialization methods.
- Có thể fixed size hoặc mở rộng/thu hẹp; không bắt buộc là một vùng memory liên tục.
- Có thể phát sinh **`OutOfMemoryError`** nếu không đủ memory cấp cho nhu cầu method area.

JVMS nói method area là **một phần của heap về mặt logic**, nhưng **không ấn định vị trí vật lý** hay chính sách quản lý compiled code. Đây không phải lời hứa rằng toàn bộ method-area structures luôn bị ràng buộc bởi giới hạn `-Xmx` của HotSpot.

### Method area khác Metaspace và PermGen

| Tên gọi | Tầng khái niệm |
| --- | --- |
| **Method area** | Vùng trừu tượng được JVMS quy định |
| **Metaspace** | Cách HotSpot hiện đại quản lý class metadata, thường sử dụng native memory |
| **PermGen** | Cơ chế lịch sử của HotSpot cũ, không phải thành phần bắt buộc của JVMS |
| **Java heap trong HotSpot** | Vùng quản lý các Java objects, thường cấu hình `-Xms`/`-Xmx` |

Không kết luận “JVMS định nghĩa Metaspace” hoặc “mọi JVM đều có PermGen”. Cũng không coi vị trí physical của mọi `static` field, `Class` object hay string literal là điều đã được JVMS `2.5` đóng đinh.

### Static fields chia sẻ theo runtime class identity

Một `static` field thuộc về **runtime class**, không thuộc một frame hay từng thread. Các threads đang dùng **cùng một runtime class identity** chia sẻ static state.

~~~java
class Registry {
    static int total;
}
~~~

Tuy nhiên:

- `Registry.total++` **không tự đồng bộ** trong multi-threaded code.
- Nếu hai defining class loaders khác nhau cùng định nghĩa class tên `Registry`, chúng tạo **hai runtime class identities** và mỗi class có static state riêng.
- “Method area shared” không có nghĩa static fields của mọi class trùng tên ở mọi plugin được hợp nhất.

## 8. Run-time constant pool: per-class, không phải per-thread

**JVMS §2.5.5** mô tả run-time constant pool là representation runtime của `constant_pool` trong file class. JVM tạo một pool **cho mỗi class/interface** trong method area khi loại đó được tạo. Pool chứa constants và symbolic references (classes, fields, methods, method handles…).

Ví dụ `javap -c -v` có thể hiển thị:

~~~text
  #N = Methodref ... // Example.add:(II)I
 ...
  invokestatic #N     // gọi Example.add(int,int)
~~~

`#N` là **constant-pool index**, không phải stack slot, BCI hay địa chỉ heap. Dynamic linking dùng symbolic reference này để resolution thành phần runtime phù hợp.

Các frames của những lời gọi `Example.calculate` ở hai threads có **local arrays/operand stacks khác nhau**, nhưng cùng giữ **reference tới run-time constant pool của runtime class Example**. Không cần sao chép toàn bộ constant pool cho mỗi invocation.

**Phạm vi chính xác:**
- JVM-wide: method area dùng chung.
- Per runtime class/interface: từng run-time constant pool.
- Per invocation/thread: frame reference tới pool, locals và operand stack.

## 9. Ví dụ Java 17+: hai threads có locals riêng và object chung

Tạo file **`MemoryScopeDemo.java`**:

~~~java
public class MemoryScopeDemo {
    static final class Counter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int get() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter shared = new Counter();
        Counter alias = shared;
        int[] results = new int[2];

        Thread first = new Thread(() -> {
            int local = 10;
            results[0] = local + 1;
            shared.increment();
        }, "first");

        Thread second = new Thread(() -> {
            int local = 20;
            results[1] = local + 1;
            shared.increment();
        }, "second");

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("first-local=" + results[0]);
        System.out.println("second-local=" + results[1]);
        System.out.println("shared-count=" + shared.get());
        System.out.println("same-reference=" + (shared == alias));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -g MemoryScopeDemo.java
java MemoryScopeDemo
~~~

**Stdout chính xác khi chạy bình thường, không bị interruption hoặc lỗi tài nguyên:**

~~~text
first-local=11
second-local=21
shared-count=2
same-reference=true
~~~

### Tại sao kết quả này có ý nghĩa?

1. Mỗi lambda body là một lần thực thi trên thread riêng; `int local` thuộc invocation tương ứng. Hai local values 10 và 20 **không phải một slot được chia sẻ**.
2. `results` trỏ tới **một int array object trên heap**. Worker thứ nhất ghi index 0; worker thứ hai ghi index 1.
3. `shared` và `alias` cùng trỏ tới **một Counter object**, bởi vậy `shared == alias` là true.
4. `increment` là `synchronized` để hai cập nhật vào `value` không bị lost update; sau hai calls `shared.get()` trả 2.
5. `join` đợi hai threads kết thúc và theo Java Memory Model giúp main nhìn thấy writes mà hai workers thực hiện. Chỉ **main in kết quả sau join**, nên **thứ tự các dòng output là xác định** dù lịch chạy threads khác nhau.

**Điều không được suy:** `local` bắt buộc được lưu ở một địa chỉ native stack riêng cố định. Compiler/JIT có thể giữ value trong register hoặc loại bỏ storage vật lý khi tối ưu; JVM vẫn phải bảo đảm semantics của mỗi invocation.

### Quan sát với `javap`

~~~bash
javap -c -v -p MemoryScopeDemo
javap -c -v -p 'MemoryScopeDemo$Counter'
~~~

Trong class `Counter`, method `increment` mang `ACC_SYNCHRONIZED`; trong class ngoài, lambdas có thể được triển khai bằng `invokedynamic`/synthetic methods. `javap` cho thấy **bytecode và class metadata** chứ không dump native memory của từng Java thread.

## 10. Đệ quy: nhiều frames cho cùng một method

Nếu `sumDown(3)` gọi `sumDown(2)` rồi `sumDown(1)`, mỗi invocation có **frame và tham số `n` riêng**, dù cùng chạy bytecode của một method. Một loop trong cùng invocation không tạo thêm frame chỉ vì lặp. Không cần cố ý gây đệ quy vô hạn để quan sát `StackOverflowError`; bài tiếp theo trình bày code và trace chi tiết cho recursion.

## 11. `ThreadLocal` không phải local variable trong frame

`java.lang.ThreadLocal<T>` là **API** cho phép mỗi thread lưu một value riêng gắn với cùng ThreadLocal key, có thể tồn tại qua **nhiều method invocations** trên thread ấy.

| Khái niệm | Phạm vi |
| --- | --- |
| Method local | Một invocation/frame |
| `ThreadLocal` value | Một thread đối với key tương ứng, có thể qua nhiều calls |
| Instance field | Một object, có thể nhiều threads cùng truy cập |
| Static field | Một runtime class, có thể nhiều threads truy cập |
| Constant pool | Một runtime class/interface, dùng chung khi frames tham chiếu |

Ví dụ **`ThreadLocalSample.java`**:

~~~java
public class ThreadLocalSample {
    static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void main(String[] args) {
        CONTEXT.set("request-1");
        System.out.println(CONTEXT.get());
        CONTEXT.remove();
    }
}
~~~

~~~bash
javac ThreadLocalSample.java
java ThreadLocalSample
~~~

**Stdout:**

~~~text
request-1
~~~

`ThreadLocal` **không được đồng nhất với địa chỉ local variable trên JVM stack**. Đặc biệt trong thread pools, `remove()` giúp không giữ lại context ngoài vòng đời mong muốn. Nếu dùng virtual threads hoặc các APIs khác, hãy kiểm tra hướng dẫn riêng của bản Java đang chạy.

## 12. Shared memory phải đi cùng Java Memory Model

Shared heap chỉ nói **những object có thể được nhiều threads truy cập**, không nói mọi thao tác trên object đều tự động atomic hoặc luôn visible ngay.

Các nguyên tắc cần nhớ:

- `synchronized` cung cấp mutual exclusion và memory-visibility ordering theo monitor.
- `volatile` có các guarantees về visibility/order của reads/writes, nhưng không biến `count++` thành atomic increment.
- `AtomicInteger.incrementAndGet()` là lựa chọn đơn giản cho atomic counting.
- `Thread.start()` và `Thread.join()` có các happens-before rules liên quan việc truyền dữ liệu giữa threads.
- Objects immutable hoặc dữ liệu confined có thể giảm chia sẻ mutable state và nguy cơ race.

Nguồn chuẩn: **JLS §17.4 Memory Model**. Đừng thay toàn bộ Java Memory Model bằng hình vẽ “stack riêng / heap chung”: hình đó mô tả **tổ chức lưu trữ trừu tượng**, không đầy đủ các quy tắc visibility/synchronization.

## 13. Vì sao OutOfMemoryError không phải lúc nào cũng là heap đầy?

| Vấn đề | Triệu chứng có thể gặp | Giải thích |
| --- | --- | --- |
| Call depth vượt JVM stack limit | `StackOverflowError` | Đệ quy sâu, chuỗi gọi không dừng |
| Không thể cấp/mở rộng JVM stack | `OutOfMemoryError` | Native/runtime memory cho stack không đủ theo điều kiện |
| Heap không đủ allocation cho object | `OutOfMemoryError` | Garbage collector và heap không cấp đủ storage |
| Method area không đủ memory | `OutOfMemoryError` | Metadata/code/constant-pool allocations thiếu |
| Native method stack vượt giới hạn | `StackOverflowError` | Nếu implementation sử dụng native method stack |
| Không tạo/mở rộng native stacks được | `OutOfMemoryError` | Native resource exhaustion |
| Quá nhiều platform threads/native resources | Có thể `OutOfMemoryError` hoặc lỗi tạo thread | Phụ thuộc OS, giới hạn và JVM |

Trên HotSpot, messages như `Java heap space`, `Metaspace`, `unable to create native thread` là gợi ý nguyên nhân, nhưng không phải JVMS bắt buộc mọi triển khai phải trả đúng wording đó.

**Không thực hiện thí nghiệm cố ý dùng vô hạn memory/threads** trên máy làm việc hoặc production; quan sát thống kê và tái hiện có giới hạn trong môi trường test sẽ an toàn hơn.

## 14. Cách theo dõi vùng nhớ trên HotSpot

Một số công cụ/flags phổ biến:

~~~bash
java -version
java -XshowSettings:vm -version
java -Xlog:help
~~~

| Cấu hình/công cụ | Có thể xem hoặc tác động | Không được kết luận |
| --- | --- | --- |
| `-Xmx512m` | Giới hạn Java object heap trên HotSpot | Toàn bộ memory của process bị giới hạn đúng 512 MB |
| `-Xss1m` | Kích thước stack thread theo runtime/platform phù hợp | Mỗi frame Java chiếm đúng 1 MB |
| `-XX:MaxMetaspaceSize=...` | Quản lý giới hạn Metaspace trên HotSpot | Metaspace là vùng bắt buộc của mọi JVM |
| `jcmd <pid> GC.heap_info` | Thông tin heap/collector | Run-time constant pool chi tiết của từng class |
| `jcmd <pid> Thread.print` | Call stacks và threads | Nội dung mọi operand stack theo slot |
| `jcmd <pid> VM.native_memory summary` | Native Memory Tracking nếu bật | Mọi memory category đều là vùng trong JVMS `2.5` |

Native Memory Tracking (NMT) của HotSpot có thể được bật khi khởi chạy với:

~~~bash
java -XX:NativeMemoryTracking=summary MemoryScopeDemo
~~~

**Ví dụ `MemoryScopeDemo` kết thúc quá nhanh để attach** là điều bình thường. Trong server chạy lâu, xác định PID bằng `jcmd -l`, rồi dùng `jcmd <pid> VM.native_memory summary` khi có quyền và JVM bật tracking. Không mặc định lệnh NMT hoạt động trên mọi JVM.

**RSS/Process memory ≠ Java heap limit**: process còn metadata, native stacks, code cache, JVM internals… `-Xmx` không phải “giới hạn RAM tuyệt đối của process”.

## 15. Các ngộ nhận dễ gặp

| Ngộ nhận | Điều đúng |
| --- | --- |
| “Chỉ có một JVM stack cho cả process.” | Mỗi JVM thread có **stack riêng**. |
| “Operand stack và JVM stack là một.” | Operand stack thuộc **frame**; JVM stack chứa **frames**. |
| “Một local reference nghĩa object được tạo nằm trong stack.” | Local chứa **reference**, object cấp phát trên **heap** theo mô hình. |
| “Mất local reference thì GC thu object ngay.” | Object có thể còn reachable; GC không bị ràng buộc đúng thời điểm method return. |
| “`ThreadLocal` là frame local variable.” | ThreadLocal là **API per-thread value**, khác local array per-invocation. |
| “Hai threads cùng thấy object thì locals của chúng cũng dùng chung.” | Locals riêng, có thể chứa references tới cùng object. |
| “Static field có một bản cho tất cả classloaders.” | Static state thuộc runtime class identity; class cùng tên do defining loaders khác nhau vẫn riêng. |
| “Method area = Metaspace trong mọi JVM.” | Method area thuộc JVMS; Metaspace thuộc HotSpot. |
| “Method area luôn vật lý nằm trong `-Xmx` heap.” | Đặc tả chỉ nói *logically part of heap*, không ấn định implementation layout. |
| “Runtime constant pool là per-thread.” | **Per-class/interface**, nằm trong method area dùng chung. |
| “Native method stack chắc chắn có trên mọi JVM.” | Tùy implementation, không luôn bắt buộc. |
| “`pc` của native method luôn trỏ Java opcode.” | Theo JVMS, pc của JVM **undefined** khi current method native. |
| “Shared heap tự bảo đảm thread-safe.” | Cần Java Memory Model và synchronization phù hợp. |
| “`max_stack` là số method calls tối đa.” | Độ sâu operand stack của **một method**, không phải số frames. |
| “Mọi OutOfMemoryError là Java heap hết chỗ.” | Còn có metadata, stack/native resources và nguyên nhân khác. |

## 16. Tự kiểm tra

**1.** Những vùng nào per-thread theo JVMS?  
**Đáp án:** `pc`, JVM stack; native method stacks thường per-thread **nếu implementation cung cấp**.

**2.** Những vùng nào JVM-wide?  
**Đáp án:** Heap và method area. Run-time constant pool là **per-runtime-class/interface** nhưng được cấp trong method area.

**3.** Hai threads cùng chạy `add(3,4)` có một operand stack chung không?  
**Đáp án:** Không. Mỗi invocation tạo frame riêng với operand stack riêng.

**4.** Một local `Person p` chứa cả Person object trên JVM stack?  
**Đáp án:** Không. Nó chứa reference value; Person object được cấp phát trên heap theo mô hình.

**5.** Vì sao `static int count` không mặc định an toàn khi hai threads dùng `count++`?  
**Đáp án:** `count++` là read–modify–write có thể tranh chấp; static field shared không có nghĩa atomic.

**6.** `ThreadLocal<String>` có tương đương một local biến Java không?  
**Đáp án:** Không. ThreadLocal value gắn với thread qua nhiều calls; method local gắn với frame/invocation.

**7.** Run-time constant pool có được tạo mới mỗi khi call method?  
**Đáp án:** Không. Pool thuộc class/interface; frame của lần gọi chỉ giữ reference tới pool.

**8.** Method area có luôn được triển khai bằng HotSpot Metaspace?  
**Đáp án:** Không. Đặc tả không bắt buộc một cách triển khai hoặc physical layout.

**9.** Một frame return khiến object được cấp phát trong method tự hủy ngay?  
**Đáp án:** Không. Object có thể còn reachable từ nơi khác và GC không nhất thiết xảy ra ngay.

**10.** Tại sao `MemoryScopeDemo` luôn in shared-count=2 trong execution bình thường?  
**Đáp án:** `increment()` đồng bộ để không mất update; `join()` bảo đảm main đợi hai worker xong mới đọc.

**11.** Lệnh `-Xmx` có giới hạn tuyệt đối RAM của JVM process?  
**Đáp án:** Không. Còn native memory, metadata, stack và các nguồn khác.

**12.** Khi chạy native method, JVMS định nghĩa pc là địa chỉ nào?  
**Đáp án:** Giá trị pc của JVM **không xác định** trong native method.

## 17. Nguồn chuẩn

- [JVMS Java SE 25 — §2.5 Run-Time Data Areas](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5): cấu trúc memory JVM.
- [JVMS §2.5.1 pc Register](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.1).
- [JVMS §2.5.2 JVM Stacks](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.2).
- [JVMS §2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.3).
- [JVMS §2.5.4 Method Area](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.4).
- [JVMS §2.5.5 Run-Time Constant Pool](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.5).
- [JVMS §2.5.6 Native Method Stacks](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.6).
- [JVMS §2.6 Frames](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.6).
- [JVMS §4.7.3 Code Attribute](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.3): `max_stack`/`max_locals`.
- [JLS Java SE 25 — Chapter 17 Threads and Locks](https://docs.oracle.com/javase/specs/jls/se25/html/jls-17.html): memory model và happens-before.
- [JDK 25 — javap](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): quan sát bytecode.
- [JDK 25 — jcmd](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html): quan sát runtime trên JVM phù hợp.

**Ghi nhớ cuối:** `pc`/JVM stack/native stacks (nếu có) theo thread; heap/method area chung toàn JVM; runtime constant pools gắn với từng runtime class; locals/operand stack gắn với từng frame. **Per-thread không đồng nghĩa đối tượng không thể được chia sẻ**, và **shared heap không tự tạo thread-safety**. Khi học JVM, luôn tách **mô hình đặc tả**, **Java Memory Model** và **chi tiết physical memory của implementation**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-002">Thanh ghi pc và JVM stack trong quá trình thực thi một luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Thanh ghi pc và JVM stack trong quá trình thực thi một luồng

Khi JVM chạy một phương thức Java, nó phải biết **instruction bytecode nào đang được thực thi** và **trạng thái lời gọi phương thức đang nằm ở đâu**. Hai thành phần nền tảng trong mô hình JVM là:

- **`pc register` (program counter register)**: trạng thái **riêng của mỗi thread** dùng để biểu diễn vị trí instruction JVM đang thực thi.
- **`JVM stack` (Java Virtual Machine stack)**: ngăn xếp **riêng của mỗi thread**, chứa các **frame** của những lần gọi phương thức đang hoạt động.

Một thread đi vào `main`, gọi `service()`, rồi gọi `calculate()` sẽ chuyển qua các frame khác nhau. `pc` cho biết instruction hiện tại của thread; JVM stack giữ local variables, operand stack và thông tin cần thiết để trở lại caller hoặc xử lý exception. Hiểu cả hai mới giải thích được lời gọi lồng nhau, đệ quy, stack trace, `StackOverflowError`, chuyển context giữa threads và vì sao `-Xss` không đồng nghĩa kích thước heap.

Bài sử dụng mô hình từ **JVMS Java SE 17 §2.5.1–§2.5.2 và §2.6**; ví dụ biên dịch với Java 17+. Các thuật ngữ `pc` và frame dưới đây trước hết là **ngữ nghĩa JVM**, không phải lời khẳng định HotSpot luôn lưu chúng vào một thanh ghi CPU vật lý hoặc một vùng RAM liên tục duy nhất.

## 1. Đặt pc register và JVM stack trong toàn cảnh runtime data areas

JVMS phân biệt vùng dữ liệu **theo thread** và **được chia sẻ giữa các threads**:

| Vùng dữ liệu JVM | Phạm vi | Vai trò trong bài |
| --- | --- | --- |
| **pc register** | **Mỗi thread có một** | Theo dõi instruction JVM đang chạy trong thread đó |
| **JVM stack** | **Mỗi thread có một** | Chứa các frame của các lời gọi đang hoạt động |
| **Frame** | Thuộc một lần gọi method trong một thread | Local variables, operand stack, dynamic linking, return/exception state |
| **Heap** | Chia sẻ trong JVM | Chứa objects và arrays; nhiều frame có thể giữ reference tới cùng object |
| **Method area** | Chia sẻ trong JVM | Dữ liệu lớp, method code và những thông tin liên quan |
| **Run-time constant pool** | Dữ liệu liên quan tới từng class/interface, trong vùng do JVM quản lý | Symbolic references được bytecode trong frame sử dụng |
| **Native method stacks** | Phụ thuộc JVM/triển khai và thread/native invocation | Phục vụ thực thi native code nếu implementation sử dụng |

Không gộp **operand stack** trong một frame với **JVM stack** chứa các frame: chúng có quan hệ lồng nhau nhưng khác cấp độ.

```text
                       Một JVM
     +------------------------------------------+
     | Heap, method area, runtime constant pools|
     |        (các threads có thể chia sẻ)      |
     +------------------------------------------+
           ^                         ^
           | references / metadata   |
    +-----------------+      +-----------------+
    | Thread A        |      | Thread B        |
    | pc register A   |      | pc register B   |
    | JVM stack A     |      | JVM stack B     |
    |  [frame m3]     |      |  [frame n2]     |
    |  [frame m2]     |      |  [frame n1]     |
    |  [frame m1]     |      |                 |
    +-----------------+      +-----------------+
```

Ví dụ, local slot 1 của frame thuộc thread A và local slot 1 của frame thuộc thread B **không phải cùng một slot**. Nhưng nếu chúng chứa reference trỏ đến **cùng một object trên heap**, hai thread vẫn có thể đọc/ghi trạng thái object đó. **Thread-private stacks không có nghĩa mọi dữ liệu được tham chiếu đều thread-safe.**

## 2. pc register: mô hình chính xác là gì?

Theo JVMS §2.5.1, **mỗi JVM thread có pc register riêng**, được tạo cùng thread. Ở bất kỳ thời điểm nào, một thread đang thực thi code của một method. Trong trường hợp method đó **không phải native**, giá trị pc register là **địa chỉ instruction JVM hiện đang được thực thi** trong method tương ứng.

Điều này giúp JVM có mô hình điều khiển riêng cho các threads: thread A có thể đang ở opcode `iadd` của `compute`, trong khi thread B đang ở opcode `invokevirtual` của `service`.

### Nếu thread đang thực thi native method?

Khi method hiện tại là **native**, JVMS quy định giá trị pc register là **undefined**. Đây là một câu rất quan trọng:

- Không được khẳng định JVM pc của thread đang chạy JNI/native method luôn chỉ tới “bytecode cuối cùng”.
- Địa chỉ instruction **mã máy native** và JVM pc register trong ngữ nghĩa JVMS là **hai khái niệm khác nhau**.
- Một native call có thể tương tác với C/C++, OS stack hoặc cơ chế native của VM, nhưng cách triển khai ấy **không thay đổi** việc JVM pc register được quy định undefined khi đang ở native method.

### pc register có phải con trỏ instruction phần cứng?

Không nhất thiết. JVM là một **máy ảo trừu tượng**. Interpreter có thể quản lý vị trí bytecode bằng BCI (bytecode index)/con trỏ nội bộ; JIT có thể biên dịch method sang mã máy. CPU vật lý có instruction pointer riêng, còn contract JVMS định nghĩa **trạng thái pc logic của JVM thread**. Không thể yêu cầu `Thread.currentThread()` trả trực tiếp giá trị pc register này như một trường Java public.

### pc, bytecode index (BCI) và dòng source

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `pc` của JVM thread | Vị trí instruction JVM **đang thực thi** trong mô hình JVM | Instruction thuộc `calculate()` của frame hiện tại |
| **BCI / bytecode offset** | Offset tính bằng **byte** từ đầu `code[]` của một method | `0: iload_0`, `1: iconst_1`, `2: iadd` |
| **Source line** | Dòng trong file Java theo debug metadata | `Calculator.java:12` |
| CPU instruction pointer | Địa chỉ instruction native của CPU/JIT/interpreter | Chi tiết triển khai, không phải BCI Java trực tiếp |

Một dòng Java source có thể ánh xạ thành **nhiều** instructions; một instruction có thể ứng với một source line qua `LineNumberTable`, hoặc class không có debug line numbers. `javap -c` hiển thị BCI; `javap -l` có thể hiển thị line/local tables. Các bảng này **không** phải API đọc pc chính xác của một thread bất kỳ ở mọi thời điểm.

## 3. JVM stack: một ngăn xếp các frame, không phải heap

Theo JVMS §2.5.2, mỗi JVM thread có **JVM stack riêng**, được tạo cùng thread, lưu các **frames**. JVM stack không trực tiếp cho phép tùy ý lấy một biến local thuộc frame của thread khác bằng mã Java bình thường.

Một số đặc tính:

1. **LIFO**: gọi một method mới thường dẫn đến frame mới trở thành frame hiện tại; kết thúc method thì frame ấy được rời khỏi và caller tiếp tục.
2. **Mỗi invocation có một frame**: cùng method được gọi lại đệ quy ba lần là ba lời gọi, do đó có ba frame riêng với locals riêng.
3. **Mỗi frame có operand stack riêng**: không có một operand stack chung cho mọi method/thread.
4. **Stack lưu trạng thái phương thức, không lưu toàn bộ object**: các local/reference operands có thể trỏ tới objects trên heap.
5. **JVM stacks có thể cố định hoặc thay đổi kích thước tùy triển khai**, và vùng nhớ frame có thể được cấp phát từ heap hoặc nơi khác trong triển khai phù hợp. Đừng giả định JVMS bắt buộc frame là các khối memory vật lý nằm liên tiếp.
6. **Stack kết thúc cùng thread** ở cấp mô hình vòng đời. Việc JVM/OS thu hồi native memory và tối ưu lưu trữ là chi tiết implementation.

### Khung lời gọi lồng nhau

Giả sử:

```java
public static int first(int x) {
    return second(x) + 1;
}
public static int second(int x) {
    return third(x) * 2;
}
public static int third(int x) {
    return x + 3;
}
```

Đây là **ba method minh họa** cần đặt trong một class để biên dịch. Khi `first(4)` gọi `second(4)` rồi `third(4)`:

```text
Đỉnh JVM stack
+---------------------+
| frame third(4)      | <- CURRENT khi third thực thi
| locals: x=4         |
| operand stack ...   |
+---------------------+
| frame second(4)     | <- tạm dừng chờ third trả giá trị
| locals: x=4         |
| operand stack ...   |
+---------------------+
| frame first(4)      | <- tạm dừng chờ second
| locals: x=4         |
| operand stack ...   |
+---------------------+
Đáy JVM stack
```

Khi `third` trả `7`, frame `third` không còn là frame hiện tại; `second` nhận kết quả, tính `14` rồi trả cho `first`; `first` cộng 1 thành `15`. Mọi `x` đều thuộc frame riêng, dù các giá trị cùng bằng `4`.

### “Thread stack”, “call stack”, “operand stack” dễ bị đánh đồng

- **JVM stack / Java call stack**: mô hình quản lý các frames method đang hoạt động trong thread.
- **Operand stack**: vùng bên trong **mỗi frame**, dùng cho tính toán bytecode; được giới hạn bởi `max_stack`.
- **Native/OS stack**: vùng mà native code và bản thân VM có thể sử dụng theo hệ thống; mối quan hệ vật lý với Java frames phụ thuộc JVM và kiểu thread.
- **Heap**: nơi chứa objects/arrays được cấp phát theo ngữ nghĩa JVM. Reference đến object có thể nằm trong locals/operand stack; không có quy tắc “mọi primitive chỉ nằm trên stack, mọi reference chỉ nằm trên heap”.

## 4. Bên trong một frame có gì?

JVMS §2.6 mô tả một **frame** chứa:

- **Local variable array**: các slot được đánh số, lưu receiver `this` (nếu method instance), tham số và các giá trị cục bộ.
- **Operand stack**: vùng LIFO chứa toán hạng và các kết quả trung gian cho JVM instructions.
- **Tham chiếu tới run-time constant pool** của lớp chứa method: dùng hỗ trợ **dynamic linking**, phân giải symbolic references ở những lệnh như truy cập field, gọi method và tạo object.
- **Thông tin cho normal/abrupt method completion**, bao gồm trả về caller hay xử lý/lan truyền exception theo quy tắc JVM.

Tệp `.class` cung cấp `Code` attribute với các giới hạn `max_locals` và `max_stack` cho method có code. Mỗi lần gọi method tạo trạng thái frame riêng theo những giới hạn này.

| Phần trong frame | Đọc từ bytecode thế nào? | Sai lầm phổ biến |
| --- | --- | --- |
| Local variables | `iload`, `istore`, `aload`, `astore`, `lload`... | Cho rằng local index luôn bằng thứ tự tham số trong source |
| Operand stack | `iconst`, `iadd`, `invoke*`, `dup`, `pop`... | Cho rằng mỗi thread chỉ có một operand stack chung |
| Dynamic linking | `getfield`, `invokevirtual`, `invokestatic`... thông qua constant pool | Cho rằng frame chứa sẵn địa chỉ native cố định cho mọi symbolic reference |
| Return state | `ireturn`, `areturn`, `return`, v.v. | Cho rằng callee tiếp tục chạy sau return |
| Exception handling | Exception table, `athrow`, lỗi JVM instructions | Cho rằng exception luôn kết thúc cả thread |

### Local slot: giá trị category 1 và category 2

- `int`, `float`, reference thuộc loại tính toán **category 1**, mỗi giá trị cần **một slot**.
- `long` và `double` thuộc **category 2**, mỗi giá trị cần **hai slots** trong local-variable array và đóng góp hai đơn vị độ sâu trên operand stack.
- `byte`, `short`, `char` và `boolean` được biểu diễn/xử lý dưới dạng tính toán thích hợp, thường theo `int` trong operand stack/locals.
- Method **instance** thường đặt receiver `this` ở local slot **0**, rồi tham số; method **static** không có receiver ẩn.
- `long` ở slot 1 chiếm slot 1–2; tham số tiếp theo không thể dùng slot 2 như một giá trị độc lập.

Ví dụ method:

```java
static long mix(int count, long amount, double factor) {
    return amount + count;
}
```

Đây là **method minh họa**. Trong static method, `count` ở slot 0, `amount` dùng slot 1–2, `factor` dùng slot 3–4 dù không được dùng trong thân. Compiler có thể thêm local slots nếu cần; `max_locals` tối thiểu phải bao trùm toàn bộ tham số theo descriptor. Operand stack cho `ladd` có thể chứa hai giá trị `long` cùng lúc, tổng **4 slot**.

## 5. pc và JVM stack phối hợp khi gọi method như thế nào?

Mô hình một lần invocation:

1. Caller đang ở **current frame** và pc phản ánh vị trí instruction gọi, ví dụ `invokestatic`.
2. Caller đẩy các arguments (và receiver nếu là instance method) lên **operand stack của caller** theo đúng descriptor.
3. JVM tiến hành invocation, xác định method đích qua cơ chế resolution/selection phù hợp, và thiết lập một **frame mới** cho callee (trừ khi implementation dùng tối ưu vẫn bảo toàn hành vi).
4. Callee trở thành **current method/current frame**; arguments được chuyển vào locals của frame callee theo chỉ số slot tương ứng.
5. pc logic của thread biểu diễn instruction đang thực thi trong callee. Caller tạm dừng tại điểm gọi trong lúc callee thực hiện.
6. Khi callee **normal return**, frame callee được rời khỏi; nếu có return value, caller nhận nó trên **operand stack của caller**; sau đó caller tiếp tục tại phần kế tiếp sau lời gọi.
7. Nếu callee kết thúc **abruptly** vì exception, JVM tìm exception handler trong frame hiện tại hoặc unwind ra caller theo exception table và quy tắc lan truyền.

Điểm chính: pc **thuộc thread**, không phải một pc riêng có thể chạy đồng thời cho mỗi frame. Trạng thái caller cần để resume được bảo toàn qua cơ chế invocation/return; pc của thread đi cùng dòng thực thi chuyển qua callee.

### Một method có thể trả về bằng opcode nào?

| Return type | Lệnh thường dùng | Hiệu ứng |
| --- | --- | --- |
| `void` | `return` | Không đẩy một giá trị trả về |
| `int` và các kiểu primitive nhỏ theo biểu diễn JVM | `ireturn` | Trả một giá trị category 1 |
| `long` | `lreturn` | Trả category 2 |
| `float` | `freturn` | Trả category 1 |
| `double` | `dreturn` | Trả category 2 |
| Object/array reference | `areturn` | Trả reference category 1 |

Callee không “copy nguyên frame” sang caller. Thường chỉ **return value** được truyền theo ngữ nghĩa stack, trong khi locals/operand stack và các trạng thái method riêng của callee kết thúc theo frame ấy.

## 6. Java 17 thực hành: hai lời gọi lồng nhau và bytecode

Tạo file **`FrameCallDemo.java`**:

```java
public class FrameCallDemo {
    static int square(int value) {
        return value * value;
    }

    static int addSquare(int first, int second) {
        int temp = square(first);
        return temp + second;
    }

    public static void main(String[] args) {
        System.out.println("result=" + addSquare(3, 4));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 -g FrameCallDemo.java
java FrameCallDemo
javap -c -v -p FrameCallDemo
```

**Stdout của lệnh `java FrameCallDemo`**:

```text
result=13
```

Vì `square(3) = 9` và `addSquare(3,4) = 9 + 4 = 13`.

### Đi theo frames từ `main` đến `square`

```text
A. main
   operand stack: ... arguments (3, 4) cho addSquare
   invokedynamic/println cho phép nối/in có thể xuất hiện tùy compiler

B. vào addSquare
   locals: [first=3, second=4, temp chưa có]
   iload_0  -> [3]
   invokestatic square:(I)I
       |
       v
C. vào square
   locals: [value=3]
   iload_0  -> [3]
   iload_0  -> [3, 3]
   imul     -> [9]
   ireturn  -> caller nhận 9

D. trở lại addSquare
   operand stack sau lời gọi square: [9]
   istore_2 -> []; locals[temp]=9
   iload_2  -> [9]
   iload_1  -> [9,4]
   iadd     -> [13]
   ireturn  -> main nhận 13
```

Đây là **mô hình về các opcode liên quan**. `javap` sẽ có offsets, constant-pool indices, các lệnh chính xác trong `main`; phần nối chuỗi `"result=" + ...` có thể được compiler Java 17 hạ thành `invokedynamic` với `StringConcatFactory`. Tính đúng của `square` và `addSquare` không phụ thuộc vào lựa chọn concat này.

### Trích cấu trúc disassembly thường thấy

```text
static int square(int);
  Code:
    stack=2, locals=1, args_size=1
       0: iload_0
       1: iload_0
       2: imul
       3: ireturn

static int addSquare(int, int);
  Code:
    stack=2, locals=3, args_size=2
       0: iload_0
       1: invokestatic  #...  // Method square:(I)I
       4: istore_2
       5: iload_2
       6: iload_1
       7: iadd
       8: ireturn
```

Các `#...` trong bảng là **chỗ đứng cho constant-pool index**, không phải output nguyên văn có một chỉ số cố định. `javac` phiên bản khác có thể chọn chuỗi opcode tương đương. Tuy vậy:

- `square` cần đồng thời hai `int` trên operand stack trước `imul`: độ sâu tối đa **2**; local có một tham số **1 slot**.
- `addSquare` có hai tham số `int` và một local `temp`, nên `locals=3` là hợp lý cho output điển hình; operand stack tối đa là **2**.
- Opcode `invokestatic` trong `addSquare` làm thread chuyển execution sang frame `square`; opcode `ireturn` chuyển kết quả **9** về stack của caller.
- BCI `1` sau `iload_0` không phải một “thanh ghi pc của square”: đó là offset instruction trong **code của addSquare**.

### Theo dõi từng thời điểm

| Thời điểm | Current method | Frames đang hoạt động (đáy → đỉnh) | Điều pc logic đại diện |
| --- | --- | --- | --- |
| Trước lời gọi `addSquare` | `main` | `main` | Instruction trong `main` |
| Sau invocation `addSquare` | `addSquare` | `main → addSquare` | Instruction thuộc `addSquare` |
| Sau invocation `square` | `square` | `main → addSquare → square` | Instruction thuộc `square` |
| Sau `square.ireturn` | `addSquare` | `main → addSquare` | Instruction tiếp tục trong `addSquare` |
| Sau `addSquare.ireturn` | `main` | `main` | Instruction tiếp tục trong `main` |

Bảng tập trung vào **logical call stack**, không yêu cầu HotSpot phải tạo năm cấu trúc stack máy vật lý như bảng cho từng dòng.

## 7. Đệ quy: mỗi lời gọi cùng một method vẫn là frame mới

Với recursion, một method gọi lại chính nó. Tên method trên stack có thể lặp lại nhưng **locals riêng**, pc phản ánh instruction trong invocation đang chạy của thread.

Tạo file **`RecursiveFrames.java`**:

```java
public class RecursiveFrames {
    static int countDown(int n) {
        if (n == 0) {
            return 0;
        }
        return 1 + countDown(n - 1);
    }

    public static void main(String[] args) {
        System.out.println("depth=" + countDown(3));
    }
}
```

```bash
javac --release 17 RecursiveFrames.java
java RecursiveFrames
```

**Stdout**:

```text
depth=3
```

Khi đi sâu nhất:

```text
main()
  countDown(3)  locals[n=3]
    countDown(2) locals[n=2]
      countDown(1) locals[n=1]
        countDown(0) locals[n=0] -> return 0
      -> return 1
    -> return 2
  -> return 3
```

Nếu cùng một phương thức được gọi vô hạn mà JVM không thể cấp thêm frame, kết quả có thể là **`StackOverflowError`**. Độ sâu tại thời điểm lỗi **không cố định**, phụ thuộc JVM, stack settings, biểu diễn frame, JIT, hệ thống và tình huống runtime. Không cần chạy đệ quy vô hạn để minh họa cơ chế; ví dụ hữu hạn đã chứng minh mỗi invocation có local state riêng.

### Vì sao `StackOverflowError` không đồng nghĩa “heap hết bộ nhớ”?

Đó là lỗi tài nguyên liên quan đến stack của JVM thread, không phải kết luận mặc định rằng heap đã đầy. Ngược lại, chương trình có thể bị `OutOfMemoryError` vì không thể tạo/giãn stack hoặc thiếu tài nguyên native; **loại exception phải được đọc cùng message và ngữ cảnh**.

## 8. Exception làm current frame thay đổi như thế nào?

Một method có thể kết thúc:

- **Normal completion**: một return instruction đưa caller tiếp tục và truyền kết quả nếu có.
- **Abrupt completion**: instruction ném exception, hoặc method gọi tới method khác mà exception chưa được xử lý ở callee.

Khi exception xuất hiện, JVM **không nhất thiết bỏ ngay toàn bộ call stack**. Nó tìm entry phù hợp trong **exception table** của method hiện tại. Nếu có handler, tiếp tục tại **`handler_pc`** trong cùng frame; nếu không, frame kết thúc đột ngột, exception lan ra caller và JVM thử tìm handler tại call site của caller.

### Ví dụ exception được bắt ở caller ngoài

Tạo **`ExceptionFrames.java`**:

```java
public class ExceptionFrames {
    static int quotient(int numerator, int denominator) {
        return numerator / denominator;
    }

    static int transform(int value) {
        return quotient(10, value) + 1;
    }

    public static void main(String[] args) {
        try {
            System.out.println("good=" + transform(2));
            transform(0);
        } catch (ArithmeticException ex) {
            System.out.println("caught=" + ex.getClass().getSimpleName());
        }
        System.out.println("after");
    }
}
```

```bash
javac --release 17 ExceptionFrames.java
java ExceptionFrames
javap -c -v -p ExceptionFrames
```

**Stdout**:

```text
good=6
caught=ArithmeticException
after
```

Giải thích:

1. `transform(2)` gọi `quotient(10,2)` nhận `5`, cộng 1 thành `6`.
2. `transform(0)` gọi `quotient(10,0)`; `idiv` ném `ArithmeticException`.
3. `quotient` không có handler phù hợp, nên frame `quotient` kết thúc đột ngột.
4. `transform` không có handler phù hợp, nên frame `transform` cũng unwind.
5. Trong `main` có `try/catch` bảo vệ vị trí lời gọi; JVM chuyển tới bytecode handler của `main` và thực thi khối `catch`.
6. `main` tiếp tục in `after`.

**pc** lúc ở `quotient` phản ánh instruction của `quotient`; khi exception được bắt trong `main`, current frame và pc logic chuyển tới handler của `main`. Không được suy “`idiv` lỗi nghĩa là JVM thread buộc phải dừng”.

### Khi handler nằm ngay trong cùng method

```java
static int safeDivide(int a, int b) {
    try {
        return a / b;
    } catch (ArithmeticException ex) {
        return -1;
    }
}
```

Đây là **method minh họa**, đặt vào class để biên dịch. Nếu `b=0`, JVM chọn exception handler **trong chính frame `safeDivide`**, không phải tự động pop frame ấy. Khi handler trả `-1`, method mới kết thúc bình thường về caller.

### Exception table dùng bytecode offsets

Đối với `Code_attribute`, mỗi handler entry có dạng `start_pc`, `end_pc`, `handler_pc`, `catch_type`:

- Vùng bảo vệ là **`[start_pc, end_pc)`** — bao gồm start, không bao gồm end.
- `handler_pc` chỉ đầu instruction xử lý exception.
- Handler nhận exception reference trên operand stack theo quy tắc JVM; instruction đầu thường là `astore` để lưu reference vào local.
- Các entries được xét theo thứ tự được định nghĩa trong exception table; entry phù hợp đầu tiên quyết định xử lý.

Những `start_pc/end_pc` trong class file là **BCI offsets**, không phải cùng một ô nhớ “JVM pc register” cấp thread.

## 9. Hai threads: hai pc registers và hai JVM stacks

Đặc tả nói rõ một JVM có thể hỗ trợ nhiều threads thực thi đồng thời; mỗi thread có **pc riêng** và **stack riêng**.

Ví dụ:

```text
Thread "worker"
  pc -> instruction của calculate()
  JVM stack: run() -> calculate()

Thread "main"
  pc -> instruction của main()
  JVM stack: main()
```

Thread main không thể đọc trực tiếp operand stack của worker như đọc một `List` Java. Tuy nhiên, cả hai có thể có references cùng trỏ tới một object trên heap và cần đồng bộ khi truy cập state chung.

### Java 17 chạy được: worker giữ frame trong khi main vẫn chạy

Tạo **`PerThreadStacks.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class PerThreadStacks {

    static int twice(int value) {
        return value * 2;
    }

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            int value = twice(21);
            ready.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.println("worker=" + value);
        }, "worker-example");

        worker.start();
        ready.await();
        System.out.println("main=ready");

        release.countDown();
        worker.join();
        System.out.println("main=joined");
    }
}
```

```bash
javac --release 17 PerThreadStacks.java
java PerThreadStacks
```

**Stdout của đường chạy bình thường, không bị interrupt**:

```text
main=ready
worker=42
main=joined
```

`CountDownLatch` khiến worker chờ sau khi báo ready; main in `main=ready` **trước khi** release worker. `join()` khiến main chỉ in `main=joined` sau khi worker kết thúc. Trong khoảng worker đợi ở `release.await()`, JVM thread worker vẫn có call stack riêng liên quan tới task và các phương thức thư viện chờ; thread main có frame riêng ở code `main`.

Đây là ví dụ về **hai stacks và đồng bộ**, không phải cách đo độ lớn stack: `CountDownLatch` và `join` chủ yếu điều phối threads, không phơi bày pc register thật.

### Thread-private local state và shared heap object

`int value = twice(21)` là giá trị local của frame chạy trên worker, không tự động trở thành biến local của main. Nếu lambda capture một reference tới object shared, dữ liệu object đó vẫn có thể là state chung. JVM không có phép bảo vệ tự động ngăn data race chỉ vì mỗi thread sở hữu stack riêng. Khi thiết kế concurrency, dùng đúng Java Memory Model, volatile, locks, immutable objects hoặc concurrent utilities.

## 10. JVM stack có thể hết chỗ: `StackOverflowError` và `OutOfMemoryError`

JVMS §2.5.2 nêu hai nhóm lỗi quan trọng:

- **`StackOverflowError`**: một phép tính trong thread yêu cầu JVM stack lớn hơn giới hạn được cho phép. Đệ quy sâu là ví dụ phổ biến nhưng không phải nguồn duy nhất.
- **`OutOfMemoryError`**: nếu một JVM implementation cho phép stack mở rộng động nhưng không thể cấp memory đủ, hoặc không thể tạo initial stack cho thread mới vì thiếu memory, lỗi có thể xuất hiện.

### `-Xss` là gì?

Trên nhiều OpenJDK/HotSpot builds, `-Xss` cấu hình kích thước stack của Java threads theo đơn vị như `k`, `m` (cách áp dụng cụ thể tùy runtime/kiểu thread và nền tảng).

Ví dụ câu lệnh minh họa:

```bash
java -Xss1m RecursiveFrames
```

Đây chỉ là ví dụ **cấu hình**, không hứa một method có thể đệ quy được chính xác bao nhiêu lần. `-Xss` không trực tiếp đặt `max_stack` của bytecode; `max_stack` là **độ sâu operand stack theo slot** cho một method frame trong `Code` attribute, còn `-Xss` liên quan tới tài nguyên stack cấp thread của implementation.

### Vì sao không lấy `StackOverflowError` làm nhánh nghiệp vụ?

- Thời điểm lỗi thay đổi theo JVM build, OS, optimization, frame shape và cách gọi.
- Khi stack đã gần cạn, ngay cả việc tạo log hoặc xây dựng exception message cũng có thể gặp hạn chế tài nguyên.
- Bắt `StackOverflowError` rồi cố tiếp tục như không có gì xảy ra có thể bỏ qua lỗi thuật toán hoặc trạng thái không còn đáng tin cậy.
- Cách sửa tốt hơn là giới hạn recursion depth, dùng thuật toán lặp, tail-recursion transformation do lập trình viên chủ động thực hiện khi phù hợp, hoặc điều chỉnh workload/stack settings sau khi đo.

**Java không bảo đảm tự động tối ưu tail-call/tail-recursion** để loại bỏ stack growth. Đừng viết hàm recursive không giới hạn chỉ vì “JIT chắc sẽ tối ưu đuôi”.

## 11. So sánh JVM stack với heap: object ở đâu?

Đoạn Java:

```java
static int lengthOf(String value) {
    int n = value.length();
    return n;
}
```

Đây là **method minh họa**, không phải file độc lập.

- Tham số `value` là một **reference** trong local slot của frame `lengthOf`.
- `String` object được quản lý theo ngữ nghĩa heap, không bị “copy toàn bộ” vào local slot chỉ vì có tham số.
- `n` là giá trị primitive local; mô hình JVM có local slot chứa kết quả. Với JIT, tối ưu có thể giữ nó trong register CPU hoặc loại bỏ thao tác lưu vật lý không cần thiết.
- Gọi `value.length()` dùng operand stack để truyền receiver, rồi trả `int` về caller. Nếu `value == null`, dereference có thể ném `NullPointerException`; điều đó không làm pc/stack trở thành vùng nhớ đối tượng.
- Nếu một object/reference “escape” khỏi method qua return/store, object có thể tiếp tục tồn tại sau khi frame method kết thúc. Vòng đời heap object được GC quản lý theo reachability chứ không bằng việc một local frame nhất định còn hay mất.

Không đơn giản hóa thành “`new` luôn buộc một heap allocation vật lý trong mọi bản JIT” hoặc “mọi primitive bắt buộc ở thread stack memory”. JVM có thể tối ưu allocation bằng escape analysis/scalar replacement mà vẫn bảo đảm ngữ nghĩa quan sát được.

### Bảng tránh nhầm ba kích thước

| Thuộc tính | Thuộc về | Đơn vị/ý nghĩa |
| --- | --- | --- |
| `max_stack` | `Code_attribute` của **một method** | Slot tối đa trên operand stack của frame method |
| `max_locals` | `Code_attribute` của **một method** | Số slot local variables cần cho frame |
| `-Xss` | Cấu hình JVM implementation | Kích thước/tài nguyên stack của thread, không phải số method hoặc số opcode |
| `-Xmx` | Cấu hình heap trên nhiều JVMs | Giới hạn heap, không quyết định trực tiếp `max_stack` của method |

## 12. Current frame, stack trace và công cụ quan sát

### Stack trace nhìn thấy gì?

`Throwable.getStackTrace()` và `Thread.getStackTrace()`/`StackWalker` cho phép ứng dụng quan sát **thông tin lời gọi**, thường là tên class, tên method, file và source line nếu có debug metadata. Chúng **không** cung cấp quyền đọc trực tiếp toàn bộ local variables/operand stack của frame hay raw pc register của thread.

- Stack trace là một **ảnh chụp logic**, không phải dãy instruction liên tục.
- Trường `StackTraceElement.getLineNumber()` đại diện source line nếu có, có thể là số âm khi không biết/hoạt động native; **không phải BCI tuyệt đối**.
- `StackWalker.StackFrame.getByteCodeIndex()` có thể cung cấp BCI khi có sẵn theo contract của API, nhưng đó **không phải API lấy một địa chỉ pc vật lý của mọi thread**.
- JIT/inlining hoặc hidden frames có thể làm cách hiển thị khác nhau theo công cụ/option, nhưng thread execution vẫn phải tuân theo semantics Java/JVMS.

### Java 17: `StackWalker` quan sát chuỗi frames logic

Tạo **`StackWalkDemo.java`**:

```java
import java.util.stream.Collectors;

public class StackWalkDemo {
    static String snapshot() {
        return StackWalker.getInstance().walk(frames ->
            frames.filter(f -> f.getClassName().equals("StackWalkDemo"))
                  .map(StackWalker.StackFrame::getMethodName)
                  .limit(4)
                  .collect(Collectors.joining(" -> "))
        );
    }

    static String inner() {
        return snapshot();
    }

    static String outer() {
        return inner();
    }

    public static void main(String[] args) {
        System.out.println(outer());
    }
}
```

```bash
javac --release 17 StackWalkDemo.java
java StackWalkDemo
```

**Stdout mong đợi khi chạy class như trên**:

```text
snapshot -> inner -> outer -> main
```

`StackWalker` duyệt các frame từ phương thức gần nhất (`snapshot`) đi ra caller; filter chọn những frames của đúng class này. Cú pháp `Collectors.joining(" -> ")` chỉ định dạng chuỗi; **không phải** JVM stack thực được lưu như một Java `String`.

**Giới hạn quan sát:** Công cụ stack walking không nhất thiết hiện mọi frame ẩn hoặc native frames theo cùng cách; hoạt động được kiểm tra bằng API này không chứng minh HotSpot lưu một frame vật lý cố định cho mỗi dòng in. Không dùng tên/độ sâu trace làm hợp đồng binary layout.

### Công cụ khi JVM bị treo hoặc stack sâu

Các lệnh thông dụng trên JDK hỗ trợ công cụ:

```bash
jcmd <PID> Thread.print
jstack <PID>
javap -c -v -p FrameCallDemo
```

`<PID>` là **placeholder** cho process ID của Java process cần kiểm tra, không phải chuỗi lệnh được chạy nguyên dạng. `jcmd`/`jstack` hiển thị các threads và stack traces (cùng trạng thái/locks tùy công cụ), giúp thấy thread đang chờ lock, sleep, native call hay đang ở method nào. `javap` là disassembler của **class file**, không phải profiler theo thời gian thực.

Để soi bytecode instruction cụ thể, `javap -c` hiển thị các **BCI offsets**; để đối chiếu source line/locals, dùng `javap -l` hoặc `javap -v` khi đã compile với debug info.

### JIT và pc register: đừng trộn ba lớp quan sát

| Điều có thể thấy | Công cụ | Kết luận hợp lệ |
| --- | --- | --- |
| Bytecode offset của một method | `javap -c` | Vị trí instruction trong `Code.code[]` của class file |
| Call frames đang hoạt động | Stack trace, `StackWalker`, `jcmd Thread.print` | Cấu trúc lời gọi logic tại thời điểm quan sát |
| Native instruction address đang chạy | Native debugger/profiler chuyên dụng | Chi tiết cấp OS/CPU/JIT, **không đồng nhất** JVM pc |
| Runtime stack memory size | JVM flags, native diagnostics | Tài nguyên memory của thread, không phải độ sâu `max_stack` |

## 13. Khi native method tham gia call stack

Trong Java 17, nhiều API chuẩn cuối cùng có thể gọi native code (thông qua JNI hoặc cơ chế runtime của JDK). Ví dụ `Thread.sleep(...)` trên một số triển khai dùng native logic nội bộ, nhưng điều đó **không** cho phép suy ra bất kỳ opcode bytecode nào ở native method body.

Trong mô hình JVMS:

1. Khi đang thực thi method **không native**, JVM pc register chứa địa chỉ instruction JVM đang chạy.
2. Khi current method là **native**, JVM pc register được quy định **undefined**.
3. Native invocation có thể dùng native method stack hoặc cấu trúc OS khác theo implementation.
4. Sau native call trở lại Java code, thread lại thực thi các JVM instructions với ngữ nghĩa frame/pc bình thường.

Đừng viết công cụ dựa vào giả định “pc luôn là một BCI kể cả khi thread đang ở JNI”. Khi xem stack dump, một dòng báo `Native Method` thường mô tả **vị trí theo tool**, không cung cấp một JVM BCI có nghĩa như ở method bytecode bình thường.

## 14. Điều gì là bảo đảm của JVMS, điều gì là tùy triển khai?

| Khẳng định | Mức bảo đảm | Lý do |
| --- | --- | --- |
| Mỗi JVM thread có pc register riêng | **JVMS §2.5.1** | Trạng thái điều khiển riêng mỗi thread |
| JVM pc có giá trị undefined khi current method native | **JVMS §2.5.1** | Native code không phải JVM bytecode |
| Mỗi JVM thread có stack riêng | **JVMS §2.5.2** | Quản lý frames method |
| Mỗi invocation có frame riêng về mặt ngữ nghĩa | **JVMS §2.6** | Locals, operands và return/exception state độc lập |
| `long/double` chiếm hai stack slots theo mô hình JVM | **JVMS `2.6.2`** | Cách đếm `max_stack` |
| Normal return truyền return value cho caller | **JVMS `2.6.4`** | Method completion |
| Exception có thể làm unwind qua nhiều frames | **JVMS `2.6.5`** | Abrupt completion |
| JVM stack bắt buộc là block memory liền kề theo layout cố định | **Không được bảo đảm** | JVMS cho phép nhiều chiến lược implementation |
| pc register luôn trùng với CPU instruction pointer | **Không được bảo đảm** | JIT/interpreter/CPU là tầng khác |
| Một Java method luôn tương ứng một native frame có layout cố định | **Không được bảo đảm** | JIT/inlining/optimization có thể thay biểu diễn |
| `-Xss1m` cho phép recursion đúng N lần | **Không được bảo đảm** | Platform, code shape, runtime/JIT |
| Stack traces phải in mọi hidden/native frame chính xác như nhau | **Không được bảo đảm** | API/tool options và cách triển khai |

## 15. Những lỗi suy luận thường gặp

**Nhầm 1: “Mỗi method có một pc register.”** Sai: **mỗi thread có một pc register** theo JVMS; current method/frame thay đổi qua invocation.

**Nhầm 2: “Mỗi thread có một operand stack duy nhất.”** Sai: JVM stack của thread chứa **nhiều frames**, và **mỗi frame có operand stack riêng**.

**Nhầm 3: “Bất kỳ nơi nào có từ stack đều là JVM stack.”** Sai: JVM call stack, frame operand stack và native stack khác vai trò.

**Nhầm 4: “`max_stack = 8` nghĩa thread stack có đúng 8 frames.”** Sai: đó là **độ sâu tối đa 8 slot trong operand stack của một method frame**.

**Nhầm 5: “Mọi object nằm trong JVM stack.”** Sai: frame có thể giữ reference tới objects; bản thân objects có ngữ nghĩa heap và JVM có thể tối ưu vật lý.

**Nhầm 6: “Reference nằm trong local nên object đó thread-private.”** Sai: cùng object trên heap có thể được nhiều threads tham chiếu.

**Nhầm 7: “Trong native method, pc luôn chỉ tới bytecode ngay sau lời gọi native.”** Sai: JVM pc register **undefined** khi current method native.

**Nhầm 8: “Source line number là bytecode PC.”** Sai: line number map qua metadata; BCI là offset trong `code[]`.

**Nhầm 9: “Exception luôn pop mọi frames và kết thúc thread.”** Sai: JVM tìm handler trong current frame rồi các frames ngoài, có thể tiếp tục chương trình.

**Nhầm 10: “StackOverflowError nghĩa là heap đã đầy.”** Sai: đó là tình huống stack vượt giới hạn; heap exhaustion là chuyện khác, dù OOME có thể xảy ra với cấp phát stack trong một số implementation.

**Nhầm 11: “Tăng `-Xss` là sửa triệt để đệ quy vô hạn.”** Sai: nó có thể trì hoãn lỗi, không thay đổi tính đúng của thuật toán.

**Nhầm 12: “Thread context switch làm hai threads chia sẻ pc/register/locals.”** Sai: thread states riêng được quản lý theo ngữ nghĩa thread; việc CPU đổi thread chạy không khiến frame locals nhập làm một.

**Nhầm 13: “Mỗi `ireturn` trả về toàn bộ operand stack của callee.”** Sai: chỉ return value cần thiết được truyền theo quy tắc completion; frame callee kết thúc.

**Nhầm 14: “`jstack` cho thấy toàn bộ local variable values của mọi frame.”** Sai: stack trace chủ yếu mô tả method/call positions, có thể có locks/state; không phải snapshot tùy ý của từng locals/operands.

## 16. Quy trình phân tích lỗi thực thi bằng pc và stack

Khi một Java thread treo, lặp sâu hoặc ném exception:

1. **Xác định thread**: thread nào gây vấn đề, state hiện tại là `RUNNABLE`, `WAITING`, `BLOCKED` hay trạng thái khác? Tránh suy luận mọi `RUNNABLE` đang dùng 100% CPU.
2. **Lấy stack trace**: `jcmd <PID> Thread.print`, `jstack` hoặc logging thích hợp. Tìm các method lặp lại (đệ quy), method chờ lock/I/O và chuỗi caller/callee.
3. **Hiểu frame hiện tại**: method đầu trong phần trace thường là vị trí quan sát được gần nhất; các phương thức bên dưới là callers còn đang hoạt động.
4. **Nếu có exception**, xác định exception ném ở method nào và catch handler nằm ở method hiện tại hay caller bên ngoài.
5. **Kiểm tra bytecode khi nghi ngờ compiler/agent instrumentation**: `javap -c -v -p Class`, xem BCI, `max_stack`/`max_locals`, exception table và `StackMapTable`.
6. **Nếu `StackOverflowError`**, tìm recursion không có điều kiện dừng, vòng gọi hai method (A→B→A), hoặc call chain quá sâu; không chỉ tăng stack size.
7. **Nếu `OutOfMemoryError` khi tạo threads**, điều tra số lượng threads và tài nguyên native/stack, đừng lập tức kết luận cần tăng `-Xmx`.
8. **Nếu thấy native frames**, phân biệt JVM pc undefined ở current native method với native instruction address của tooling.
9. **Nếu debug race/concurrency**, nhớ mỗi thread có stack riêng nhưng references từ locals có thể trỏ tới **shared heap objects**; kiểm tra locks/volatile/happens-before.
10. **Phân biệt contract và implementation**: điều JVM bắt buộc làm khác cách JIT/OS lưu giữ frame vật lý và pc trong thực tế.

## 17. Bài tự kiểm tra

**Câu 1.** Một thread gọi `A() → B() → C()`. Có ba pc registers không?  
**Đáp:** Không. Theo JVMS mỗi thread có **một pc register**; ba method đang hoạt động tạo ba **frames logic**.

**Câu 2.** `B` đang gọi `C`. Locals của `B` bị xóa ngay khi `C` bắt đầu?  
**Đáp:** Không. Frame `B` tạm dừng và giữ trạng thái cần thiết để resume sau `C`; `C` có locals/operand stack riêng.

**Câu 3.** Khi `C` có current method là native, JVM pc phải chứa BCI của caller `B`?  
**Đáp:** Không. JVMS quy định JVM pc register **undefined** khi current method native.

**Câu 4.** `max_stack=4` và `max_locals=6` nói điều gì?  
**Đáp:** Một frame của method cần **tối đa 4 operand-stack slots** và **6 local-variable slots** theo `Code` attribute; không cho số lượng frames của thread.

**Câu 5.** `long` và `double` góp mấy slots vào JVM operand stack?  
**Đáp:** Mỗi giá trị chiếm **hai slots** theo mô hình JVM, dù là một giá trị logic.

**Câu 6.** Một method ném exception nhưng caller có catch phù hợp. Thread nhất thiết chết?  
**Đáp:** Không. JVM có thể unwind frames rồi tiếp tục tại handler trong caller.

**Câu 7.** Hai threads cùng gọi `calculate()`, chúng dùng chung locals slot 0?  
**Đáp:** Không. Mỗi invocation trong mỗi thread có frame và locals riêng, nhưng có thể cùng tham chiếu một object heap.

**Câu 8.** `StackTraceElement.getLineNumber()` có phải địa chỉ pc register vật lý?  
**Đáp:** Không. Nó là source line metadata (nếu có), không phải instruction pointer của CPU hoặc raw JVM pc.

**Câu 9.** Vì sao `-Xss` không bằng `max_stack`?  
**Đáp:** `-Xss` là cấu hình tài nguyên stack của thread theo JVM implementation; `max_stack` là độ sâu operand-stack slots của **một frame**.

**Câu 10.** Có nên tin một đoạn `javap` của JDK 17 là máy trạng thái vật lý HotSpot duy nhất được phép dùng?  
**Đáp:** Không. `javap` mô tả **class file bytecode**; JIT/interpreter có thể triển khai khác nhưng giữ đúng ngữ nghĩa JVM.

## Tài liệu chuẩn Java SE 17

- [JVMS §2.5.1 — The pc Register](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.1): pc riêng mỗi JVM thread và native method.
- [JVMS §2.5.2 — Java Virtual Machine Stacks](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.2): JVM stack per-thread, khả năng mở rộng và các lỗi `StackOverflowError`/`OutOfMemoryError`.
- [JVMS §2.5.3 — Heap](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): heap dùng chung.
- [JVMS §2.6 — Frames](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6): local variables, operand stack, dynamic linking, normal/abrupt completion.
- [JVMS `2.6.1 — Local Variables](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.1) và [`2.6.2 — Operand Stacks](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.2): slot indexes và category-2 values.
- [JVMS §4.7.3 — Code Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3): `max_stack`, `max_locals`, `code_length`, exception table.
- [JVMS §6.5 — JVM Instruction Set](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5): `iload`, `iadd`, `invokestatic`, `ireturn`, `athrow` và các opcode khác.
- [Java SE 17 `StackWalker` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackWalker.html) và [`StackTraceElement` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StackTraceElement.html): stack inspection ở mức Java.
- [Java SE 17 `Thread` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): threads, stack traces và lifecycle.
- [Java SE 17 `javap`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html) và [`jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): disassembly và thread diagnostics.

**Ghi nhớ:** **pc register thuộc từng thread** và biểu diễn vị trí instruction JVM hiện tại; **JVM stack cũng thuộc từng thread**, nhưng chứa nhiều **method frames** có locals/operand stack riêng. Lời gọi/return/exception làm **current frame** thay đổi; stack traces mô tả những frames ấy, không phải direct memory dump của một PC register CPU. Phân biệt rõ ngữ nghĩa JVMS với cách HotSpot/JIT lưu state thực tế sẽ giúp tránh lỗi phân tích bộ nhớ, performance và concurrency.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-003">Heap lưu đối tượng và mảng; phân biệt đối tượng với biến tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# JVM Heap: nơi lưu đối tượng, mảng và sự khác nhau giữa đối tượng với biến tham chiếu

## Mục tiêu

Trong mô hình thực thi của JVM, **heap** là vùng dữ liệu runtime được chia sẻ giữa các thread, nơi tồn tại **class instances và arrays**. Tuy nhiên, một biến Java có kiểu tham chiếu như `Person person` không phải chính đối tượng `Person`. Biến chứa **giá trị tham chiếu**, có thể trỏ tới một đối tượng hoặc chứa `null`. Các biến tham chiếu có thể xuất hiện trong local variables của stack frame, trong field của object khác, trong static fields hoặc trong các phần tử mảng tham chiếu.

Sau bài học, bạn sẽ:
- Phân biệt rõ **object/array**, **reference value** và **reference variable**, không đồng nhất vùng nhớ của biến với vùng nhớ của object.
- Hiểu vai trò của **heap**, **JVM stack/frame**, **local variables**, **static fields** và các mối liên hệ giữa chúng.
- Mô tả `new`, khởi tạo field, mảng primitive/mảng tham chiếu, mảng đa chiều, aliasing, identity và equality.
- Dự đoán được khi nào việc gán, gọi phương thức, thay đổi field hoặc reassign biến làm thay đổi trạng thái quan sát được.
- Hiểu Java **luôn truyền tham số theo giá trị** — với tham số tham chiếu, bản sao được truyền là giá trị tham chiếu.
- Giải thích chính xác mối quan hệ giữa **reachability** và garbage collection, không cho rằng hết scope/đặt `null` sẽ lập tức giải phóng heap.
- Phân biệt **mô hình được JVMS bảo đảm** với chi tiết triển khai/tối ưu JIT như escape analysis và scalar replacement.
- Dùng Java 17+ và `javap` để khảo sát cơ chế này qua ví dụ chạy được.

## 1. Mô hình tổng quan: heap, stack frame và giá trị tham chiếu

Theo Java Virtual Machine Specification (JVMS), JVM có **heap** được chia sẻ giữa các thread, chứa các **class instances và arrays**. Mỗi thread có JVM stack riêng, gồm các **frame** của những phương thức đang được gọi; trong frame có **local variables** và **operand stack**. Bản thân static fields gắn với trạng thái của class/interface runtime, không phải với mỗi object được tạo.

Ví dụ:

~~~java
Person p = new Person("Lan");
~~~

Đây là đoạn minh họa, giả sử đã có class `Person` phù hợp. Về ngữ nghĩa:

1. Biểu thức `new Person("Lan")` tạo một instance `Person` mới, theo mô hình bộ nhớ JVM.
2. Constructor khởi tạo trạng thái instance đó; string `"Lan"` cũng là một đối tượng `String` theo ngữ nghĩa Java, với cơ chế literal/interning riêng.
3. Giá trị mà biểu thức tạo object trả về là **tham chiếu** đến instance.
4. Local variable `p` lưu **giá trị tham chiếu** ấy, không chứa toàn bộ bytes của Person ngay trong biến.
5. Nếu sau đó `p = null;` thì giá trị biến đổi thành null; **không có nghĩa object lập tức bị xóa**.

Sơ đồ khái niệm:

~~~text
Thread A / JVM stack
  frame main()
  local variable "p"
        |
        | reference value
        v
            SHARED HEAP
        +-------------------+
        | Person instance   |
        | name ----+        |
        +----------|--------+
                   | reference
                   v
             +-------------+
             | String "Lan"|
             +-------------+

Thread B / JVM stack
  frame other()
  local variable "another" ----> (có thể cùng Person instance)
~~~

Các mũi tên biểu thị quan hệ **tham chiếu logic**, **không phải** địa chỉ RAM thô mà JVM bắt buộc phơi bày. Object có thể sở hữu field primitive (lưu giá trị primitive) và field reference (lưu tham chiếu đến một object/array khác).

### Bảng phân biệt

| Khái niệm | Mô tả | Ví dụ | Vị trí theo mô hình |
| --- | --- | --- | --- |
| **Class instance** | Object có class, identity và instance fields | `new Person()` | Heap |
| **Array object** | Object có kiểu mảng, chiều dài cố định, các elements | `new int[3]` | Heap |
| **Local reference variable** | Slot chứa giá trị tham chiếu hoặc null | `Person p` trong method | Local variables của frame |
| **Instance reference field** | Thành phần của object chứa tham chiếu | `person.address` | Trong object chứa field |
| **Static reference field** | Trạng thái của class/interface runtime chứa tham chiếu | `static Person current` | Thuộc class runtime; không phải instance |
| **Reference-array element** | Một ô phần tử chứa tham chiếu/null | `people[0]` | Trong array object |
| **Primitive local variable** | Slot chứa giá trị số/boolean/char theo kiểu JVM thích hợp | `int count` | Local variables của frame |
| **Primitive instance field** | Giá trị primitive thuộc object | `person.age` | Trong object |
| **`null`** | Giá trị tham chiếu đặc biệt không tham chiếu object nào | `p = null` | Nằm ở nơi lưu reference value |

Không khẳng định mọi biến Java luôn có một “ô RAM vật lý” có thể truy cập: JVM/JIT được tối ưu hóa, có thể giữ giá trị trong register hoặc loại bỏ một số lưu trữ không quan sát được. Bảng trên là **mô hình ngữ nghĩa JVM**, không phải cam kết về bố cục vật lý của HotSpot trên mọi nền tảng.

## 2. Heap là gì? Heap không phải JVM stack

**Heap** được JVM tạo khi JVM khởi động. Các thread trong cùng JVM dùng chung heap theo mô hình runtime, và vùng này là đối tượng của cơ chế quản lý bộ nhớ/garbage collection. JVMS không yêu cầu một thuật toán GC, heap layout, generation hay thuật toán cấp phát cụ thể.

**JVM stack** là vùng dữ liệu riêng cho mỗi thread, với các frame được tạo/hủy theo lời gọi/phương thức. Frame chứa local variables và operand stack, cùng những thông tin cần thiết cho phương thức. Một **reference value trong frame** có thể trỏ tới object tồn tại trên heap.

Ví dụ:

~~~java
static int lengthOf(String text) {
    return text.length();
}
~~~

Đây là phương thức minh họa:
- Tham số `text` là một local variable kiểu tham chiếu trong frame `lengthOf`.
- Giá trị của `text` chỉ tới String object do caller cung cấp (nếu không null).
- Lệnh `text.length()` tác động đến String instance qua tham chiếu; JVM không nhân bản String object chỉ vì truyền tham số.
- Khi phương thức kết thúc, **frame bị hủy** theo mô hình gọi, nhưng String object **không nhất thiết** bị hủy: caller hoặc object khác có thể còn tham chiếu đến nó.

Hai phát biểu thường bị diễn giải quá mức:
- “Biến local ở stack, object ở heap” là mô tả hữu ích cho bytecode, **không phải** lời hứa của mọi tối ưu JIT về vị trí RAM thật.
- “Kết thúc phương thức thì object bị thu hồi” là **sai**: thời gian sống của một object phụ thuộc **reachability**, không phụ thuộc duy nhất scope của một reference variable.

## 3. Biến tham chiếu khác đối tượng ra sao?

Xét đoạn code:

~~~java
Box first = new Box(5);
Box second = first;
~~~

Giả sử `Box` là class có instance field `int value`.

- `new Box(5)` tạo **một** Box instance.
- `first` chứa tham chiếu đến instance đó.
- `second = first` **sao chép giá trị tham chiếu**, không cấp phát một Box instance mới.
- Hai biến khác nhau có thể cùng chỉ đến một object — gọi là **aliasing**.

Sơ đồ:

~~~text
first  -----+
            |
            +----> [Box object: value=5]
            |
second -----+
~~~

Nếu chạy `second.value = 12;`, lần đọc `first.value` sẽ thấy 12 trong trường hợp tuần tự thông thường: hai biến truy cập **cùng một object**.

Ngược lại:

~~~java
Box a = new Box(5);
Box b = new Box(5);
~~~

có hai object riêng với field `value` ban đầu cùng bằng 5. Việc hai object có trạng thái giống nhau **không bắt buộc có cùng identity**. `a == b` trả false khi hai phép `new` tạo hai object riêng biệt.

### Một phép gán tham chiếu không sao chép object

| Mã nguồn | Điều thay đổi | Có tạo object mới? |
| --- | --- | --- |
| `b = a;` | Giá trị tham chiếu trong biến `b` | Không |
| `b = null;` | Biến `b` không còn tham chiếu object | Không; cũng không tự hủy object |
| `b = new Box(7);` | Tạo Box mới, đưa tham chiếu mới vào `b` | Có theo ngữ nghĩa biểu thức `new` |
| `a.value = 10;` | Field `value` bên trong object được `a` tham chiếu | Không chỉ vì gán field primitive |
| `a.child = b;` | Field tham chiếu `child` của object `a` trỏ tới object của `b` | Không tạo copy object `b` |
| `Box[] items = new Box[2];` | Tạo **array object** chứa hai reference elements mặc định null | Có một array object, chưa có Box element object |

**Gán một reference khác** và **đột biến trạng thái object** là hai hành động khác nhau. Đây là điểm then chốt khi viết collection, API và mã đồng thời.

## 4. Reference values, null và equality

Java có **primitive values** và **reference values**. Một reference value là tham chiếu đến object/array hoặc giá trị `null`.

~~~java
String text = null;
System.out.println(text == null); // true
~~~

Đây là snippet trong một phương thức. `null` không phải đối tượng `Null` được tạo trên heap. Việc dùng null như receiver của phần lớn thao tác đọc field/gọi method sẽ dẫn đến `NullPointerException`, ví dụ:

~~~java
String text = null;
// text.length(); // Ném NullPointerException khi chạy.
~~~

Một reference có thể tham chiếu instance của class con, object implement interface, hoặc array có kiểu tương thích theo quy tắc Java.

### So sánh identity với equality

- `a == b` với hai **reference operands** kiểm tra chúng là **cùng một tham chiếu object** (hoặc cả hai null), không tự so nội dung field.
- `a.equals(b)` gọi method, có thể thực hiện **so sánh nội dung** nếu class override `equals` đúng cách. Nếu không override, `Object.equals` mặc định so theo identity.
- `Objects.equals(a, b)` xử lý null an toàn rồi dùng `equals`, **không mặc định là deep comparison** của object graph.
- `Arrays.equals(int[], int[])` hoặc `Arrays.deepEquals(Object[], Object[])` có hợp đồng riêng; array object thường không override `Object.equals` để so phần tử.
- `System.identityHashCode(object)` có thể hỗ trợ điều tra identity, nhưng **không bảo đảm mọi object có hash khác nhau** và không phải địa chỉ heap.

Ví dụ:

~~~java
String a = new String("hi");
String b = new String("hi");

System.out.println(a == b);      // false
System.out.println(a.equals(b)); // true
~~~

Đây là đoạn minh họa bên trong method. Hai biểu thức `new String(...)` tạo hai String instances khác identity, nhưng String override `equals` để so nội dung.

### Reference equality với String literal

Hai literal cùng nội dung `"java"` được intern theo quy tắc String literal, nên trong một ví dụ đơn giản:

~~~java
String first = "java";
String second = "java";
System.out.println(first == second); // true
~~~

Không dùng kết luận đó để suy ra mọi hai String object có cùng text đều identity-equal: `new String("java")` tạo object riêng. **String intern pool không phải loại object thứ ba nằm ngoài heap theo một quy tắc đơn giản của Java source**; JVM vẫn quản lý String objects theo semantics runtime. Cơ chế lưu trữ intern/cache là chi tiết triển khai và phiên bản.

## 5. Chương trình Java 17+: aliasing, rebind và equality

Tạo file **`HeapReferenceDemo.java`**:

~~~java
import java.util.Arrays;

public class HeapReferenceDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    static void mutate(Box box) {
        box.value = 12;
    }

    static void rebind(Box box) {
        box = new Box(99);
    }

    public static void main(String[] args) {
        Box first = new Box(5);
        Box alias = first;
        Box other = new Box(5);

        System.out.println("sameRef=" + (first == alias));
        System.out.println("otherRef=" + (first == other));
        System.out.println("sameValue=" + (first.value == other.value));

        alias.value = 8;
        System.out.println("afterAlias=" + first.value);

        mutate(first);
        System.out.println("afterMutate=" + first.value);

        rebind(first);
        System.out.println("afterRebind=" + first.value);

        Box empty = null;
        System.out.println("isNull=" + (empty == null));

        int[] numbers = {1, 2, 3};
        int[] shared = numbers;
        shared[1] = 20;

        int[] copy = numbers.clone();
        copy[0] = 99;

        System.out.println("arraySame=" + (numbers == shared));
        System.out.println("cloneSame=" + (numbers == copy));
        System.out.println("numbers=" + Arrays.toString(numbers));
        System.out.println("copy=" + Arrays.toString(copy));
    }
}
~~~

Biên dịch và chạy (JDK 17+):

~~~bash
javac --release 17 HeapReferenceDemo.java
java HeapReferenceDemo
~~~

**Stdout chính xác:**

~~~text
sameRef=true
otherRef=false
sameValue=true
afterAlias=8
afterMutate=12
afterRebind=12
isNull=true
arraySame=true
cloneSame=false
numbers=[1, 20, 3]
copy=[99, 20, 3]
~~~

Giải thích:

1. `alias = first` sao chép reference, nên `sameRef=true`.
2. `new Box(5)` trong `other` tạo object khác; trường `value` bằng nhau nhưng identity khác.
3. `alias.value = 8` sửa **object chung**, nên đọc từ `first` trả 8.
4. `mutate(first)` nhận bản sao reference và sửa object, nên `first.value` thành 12.
5. `rebind(first)` chỉ thay đổi **biến tham số cục bộ `box`** bên trong phương thức; `first` ở caller vẫn trỏ Box cũ, giá trị vẫn là 12.
6. `empty = null` không liên quan đến identity của các Box khác.
7. `shared = numbers` tạo alias cho cùng array; thay phần tử qua `shared` làm `numbers` thay đổi.
8. `numbers.clone()` tạo array object mới; vì array `int[]` chứa primitive values, các phần tử được sao chép theo giá trị. Thay `copy[0]` không làm đổi `numbers[0]`.

**Phạm vi xác định:** chương trình thực thi tuần tự, không phụ thuộc GC timing hay object address, vì vậy kết quả in ổn định theo Java 17+.

## 6. Java truyền tham số theo giá trị, kể cả khi là reference

Một câu nói gây nhầm lẫn là “Java truyền object theo tham chiếu”. Cách chính xác:

> Java truyền **giá trị của biểu thức đối số** vào tham số. Đối với biến tham chiếu, giá trị được sao chép là **reference value** tới object; cả caller và callee có thể thao tác trên cùng object.

Từ đó có hai hiện tượng:

~~~text
Caller: first ------> [Box value=5]

mutate(first):
  parameter box ----> cùng Box
  box.value = 12     -> sửa object chung

rebind(first):
  parameter box ----> cùng Box (lúc bắt đầu)
  box = new Box(99)  -> chỉ box cục bộ đổi sang object khác
  caller first -----> vẫn trỏ Box ban đầu
~~~

**Không phải call-by-reference** theo nghĩa callee có thể gán lại biến gốc của caller chỉ bằng việc gán tham số. Muốn caller đổi biến tham chiếu, có thể return giá trị tham chiếu mới rồi caller tự gán:

~~~java
static Box replace(Box original) {
    return new Box(99);
}

// Trong một phương thức:
first = replace(first);
~~~

Đây là đoạn code **minh họa** cần đặt trong cùng class có kiểu `Box`. Biến của caller chỉ đổi khi tự thực hiện `first = ...`. Việc return reference không copy toàn bộ object.

### Ví dụ swap không hoán đổi biến gốc

~~~java
static void swap(Box a, Box b) {
    Box temp = a;
    a = b;
    b = temp;
}
~~~

Đây là method minh họa. Nó hoán đổi **hai local reference values trong frame của swap**, không sửa hai biến caller. Nếu mục tiêu là hoán đổi nội dung Box, có thể thay đổi fields (điều đó đổi trạng thái object), hoặc caller hoán đổi biến trực tiếp.

## 7. Array là object trên heap

Mảng trong Java không chỉ là một đoạn dữ liệu đặc biệt “ngoài hệ object”; mỗi mảng là **object** với:
- Một **runtime array class** phù hợp kiểu component và chiều mảng.
- **Identity** riêng.
- Một độ dài `length` cố định sau khi cấp phát.
- Các phần tử có giá trị primitive hoặc reference theo component type.
- Các phần tử ban đầu nhận **default values**.
- Khả năng được tham chiếu bởi local variables, fields hoặc các mảng khác.

### Mảng kiểu primitive

~~~java
int[] scores = new int[3];
System.out.println(scores.length);  // 3
System.out.println(scores[0]);      // 0
~~~

Đây là snippet trong phương thức. `new int[3]` tạo một array object chứa ba **int values** mặc định 0; **không tạo ba đối tượng `Integer`**.

Ví dụ:
- `new boolean[2]`: hai phần tử `false`.
- `new double[2]`: hai phần tử `+0.0d`.
- `new char[2]`: hai phần tử NUL (code unit 0).

### Mảng kiểu tham chiếu

~~~java
Box[] boxes = new Box[2];
System.out.println(boxes[0] == null); // true

boxes[0] = new Box(10);
boxes[1] = boxes[0];
System.out.println(boxes[0] == boxes[1]); // true
~~~

Đây là đoạn minh họa với class `Box` đã định nghĩa, cần đặt trong method. `new Box[2]` **chỉ tạo mảng chứa hai reference elements mặc định null**. Phải gọi `new Box(...)` riêng nếu muốn tạo Box instances. Gán phần tử thứ hai bằng phần tử thứ nhất tạo **aliasing giữa hai slots trong một array**.

Sơ đồ:

~~~text
boxes ------> [Box[] array length=2]
                   [0] ---+
                           +--> [Box value=10]
                   [1] ---+
~~~

Việc thay `boxes[0].value = 20` khiến lần đọc `boxes[1].value` trả 20, do cùng object.

### Độ dài mảng và kích thước dữ liệu

- `array.length` có giá trị cố định cho **array object đó** sau cấp phát.
- Ta có thể gán lại biến tham chiếu: `array = new int[10];` trỏ sang array object mới, không làm “nở” chính array cũ.
- `new int[-1]` ném `NegativeArraySizeException`; không tạo array.
- Truy cập ngoài đoạn `0..length-1` ném `ArrayIndexOutOfBoundsException`.
- Truy cập `arr.length` khi `arr == null` ném `NullPointerException`.
- Array object cũng có type và các members theo quy tắc Java. `length` không phải một mutable instance field user-defined; ở bytecode nó được đọc bằng opcode **`arraylength`**, không phải `getfield` thông thường.

## 8. Mảng hai chiều thực chất là mảng của mảng

Khi viết:

~~~java
int[][] table = new int[2][3];
~~~

theo mô hình Java, `table` tham chiếu **array ngoài** gồm hai references tới **hai mảng int[] bên trong**, mỗi mảng trong dài 3. Java hỗ trợ các mảng không đều (*jagged arrays*).

~~~text
table ---> int[][] outer array, length=2
                |
                +-- [0] ---> int[] length=3: [0, 0, 0]
                |
                +-- [1] ---> int[] length=3: [0, 0, 0]
~~~

Có **ba array objects** về mặt ngữ nghĩa: một outer array và hai inner arrays. Không được suy luận chắc chắn rằng dữ liệu mảng hai chiều phải nằm thành một ma trận chữ nhật liên tục về địa chỉ vật lý.

Đặc biệt:

~~~java
int[][] rows = new int[2][];
System.out.println(rows[0] == null); // true
rows[0] = new int[] {7};
rows[1] = new int[] {8, 9};
System.out.println(rows[0].length);  // 1
System.out.println(rows[1].length);  // 2
~~~

Đây là snippet trong method. `new int[2][]` chỉ tạo **outer array**, chưa tự cấp phát inner arrays. Cách này tạo mảng các hàng có độ dài khác nhau.

### Array covariance và runtime store check

Reference arrays trong Java là **covariant**. Ví dụ, `String[]` có thể được gán vào biến kiểu `Object[]`:

~~~java
Object[] items = new String[1];
items[0] = "ok";
~~~

Điều đó hợp lệ; nhưng:

~~~java
Object[] items = new String[1];
// items[0] = Integer.valueOf(3); // ArrayStoreException ở runtime.
~~~

Đây là đoạn minh họa, dòng exception được comment để không làm hỏng một chương trình chạy bình thường. Dù **biến** khai báo là `Object[]`, **object mảng thực tế** có component type `String`. JVM kiểm tra việc gán phần tử tham chiếu theo kiểu mảng runtime, nên cố đặt Integer sẽ ném `ArrayStoreException`.

Mảng primitive không có covariance kiểu tương tự: `int[]` không thể gán vào `Object[]`, dù chính `int[]` là một object và có thể gán vào biến `Object`.

## 9. Ví dụ Java 17+ hoàn chỉnh: mảng primitive, tham chiếu và mảng nhiều chiều

Tạo tệp **`HeapArrayDemo.java`**:

~~~java
import java.util.Arrays;

public class HeapArrayDemo {
    static final class Node {
        int number;

        Node(int number) {
            this.number = number;
        }
    }

    public static void main(String[] args) {
        int[] primitives = new int[3];
        Node[] references = new Node[2];

        System.out.println("primitiveDefault=" + primitives[1]);
        System.out.println("referenceDefault=" + (references[0] == null));

        Node node = new Node(6);
        references[0] = node;
        references[1] = node;
        references[0].number = 10;

        System.out.println("sameNode=" + (references[0] == references[1]));
        System.out.println("nodeValue=" + references[1].number);

        int[][] rows = new int[2][];
        System.out.println("unallocatedRow=" + (rows[0] == null));

        rows[0] = new int[] {7};
        rows[1] = new int[] {8, 9};
        System.out.println("rowSizes=" + rows[0].length + "," + rows[1].length);
        System.out.println("row0=" + Arrays.toString(rows[0]));

        Object[] covariant = new String[1];
        covariant[0] = "ok";

        try {
            covariant[0] = Integer.valueOf(3);
        } catch (ArrayStoreException expected) {
            System.out.println("storeCheck=ArrayStoreException");
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 HeapArrayDemo.java
java HeapArrayDemo
~~~

**Stdout chính xác:**

~~~text
primitiveDefault=0
referenceDefault=true
sameNode=true
nodeValue=10
unallocatedRow=true
rowSizes=1,2
row0=[7]
storeCheck=ArrayStoreException
~~~

Giải thích:
- `new int[3]` cấp phát array object với int elements có default 0.
- `new Node[2]` cấp phát array object với reference elements default null; chỉ `new Node(6)` mới tạo Node instance.
- Hai reference elements cùng giữ tham chiếu đến một Node, nên thay qua phần tử 0 nhìn thấy qua phần tử 1.
- Outer array `new int[2][]` có phần tử null cho đến khi được gán inner arrays.
- Runtime type của `covariant` là `String[]` dù kiểu biến `Object[]`, nên Integer store bị chặn.

**Không phụ thuộc** vào GC hay JIT mode để có stdout như trên.

## 10. Biến `final` không khiến object bất biến

Một reference variable/field có thể là `final`, ví dụ:

~~~java
final int[] numbers = {1, 2, 3};
numbers[0] = 99; // Hợp lệ.
// numbers = new int[3]; // Không hợp lệ: reassign biến final.
~~~

Đây là snippet trong một phương thức. `final` đối với biến tham chiếu **ngăn gán lại chính biến**, không tự bảo đảm object mà nó tham chiếu là immutable.

Với class:

~~~java
class Holder {
    final int[] data = new int[2];
}
~~~

Một biến/field khác vẫn có thể nắm reference đến cùng array và thay đổi phần tử, trừ khi thiết kế phòng thủ. Để cung cấp bất biến thực tế, cần xem toàn bộ thiết kế: defensive copy, API không phơi bày mutable internals, immutable value types và quy tắc đồng thời thích hợp.

Tương tự, `final Box box` có thể giữ reference ổn định nhưng `box.value` vẫn có thể thay đổi nếu `Box` là kiểu mutable.

## 11. Sao chép mảng và sao chép object

**Sao chép reference** ≠ **sao chép object**. Và **clone array** thường là **shallow copy** với reference elements.

~~~java
Node[] original = {new Node(1)};
Node[] copy = original.clone();

System.out.println(original == copy);        // false
System.out.println(original[0] == copy[0]);  // true
~~~

Đây là snippet với class `Node` có constructor phù hợp trong một method. `clone()` trên array tạo array object mới và sao chép các element values. Vì element type là reference, value được sao chép chính là **reference tới Node**, nên cả hai arrays cùng trỏ tới Node đầu tiên.

Để deep-copy một object graph, cần chính sách sao chép các object bên trong theo domain. Không có một hành vi mặc định “gọi clone hoặc gán bằng” luôn deep-copy cả cây references. Với graph có chu trình hoặc shared subobjects, deep copy còn cần quy tắc giữ/không giữ sharing và theo dõi identity.

### Khác biệt giữa `Arrays.copyOf` và assignment

- `copied = original;` chỉ alias, không tạo array mới.
- `copied = Arrays.copyOf(original, original.length);` tạo array mới (đối với kiểu mảng phù hợp), nhưng reference elements vẫn là reference values được copy.
- `copied = original.clone();` tạo array mới với các element values copy.
- `new int[10]` tạo array giá trị mặc định, không copy dữ liệu cũ nếu không gán/copy phần tử.

## 12. GC và reachability: lúc nào object có thể thu hồi?

**Garbage collection (GC)** quản lý các object không còn cần thiết theo khả năng tiếp cận (*reachability*). Một object có thể được thu hồi khi không còn reachable từ các **GC roots** theo mô hình/thuật toán VM. JVM **không cam kết một thời điểm cụ thể** mà memory của một object sẽ được reclaim.

Các root hoặc nguồn giữ tham chiếu điển hình trong triển khai JVM gồm references còn sống trong các thread stacks/frames, static fields reachable qua class runtime, JNI/native handles và các cấu trúc runtime thích hợp. Danh sách root cụ thể và thuật toán liveness thuộc về VM/GC implementation; không coi một sơ đồ đơn giản là specification bắt buộc cho mọi collector.

Ví dụ:

~~~java
Box a = new Box(1);
Box b = a;            // Hai references cùng object
a = null;             // b vẫn trỏ đến Box.
b.value = 2;          // Box vẫn sử dụng được.
b = null;             // Không còn reference qua hai biến này.
~~~

Đây là đoạn minh họa trong phương thức. Sau `a = null`, object **vẫn reachable qua b**. Sau `b = null`, object *có thể* không còn reachable, **nếu không còn tham chiếu khác** và JVM không giữ reference live vì một mục đích phù hợp. Không được kết luận rằng ngay tại dấu chấm phẩy ấy heap memory phải lập tức được trả về hệ điều hành.

### Những hiểu nhầm về GC

- `reference = null` **không gọi trực tiếp deallocation**; nó chỉ thay đổi một reference variable/field.
- Một object không nhất thiết mất đi khi biến local ra khỏi scope: reference có thể được lưu ở static field, object khác, array hoặc nơi khác.
- Một object còn nằm trong biến trên source không nhất thiết buộc JVM luôn giữ object tới hết method nếu variable không còn được dùng về mặt thực thi; GC/JIT có thể phân tích liveness.
- Java không bảo đảm thứ tự hay thời điểm finalization hoặc GC; **không thiết kế logic nghiệp vụ dựa trên thời điểm thu hồi**.
- `System.gc()` chỉ là **yêu cầu/gợi ý** GC theo API/VM configuration, không phải cam kết thu hồi ngay một object cụ thể.
- Memory leak vẫn có thể xảy ra theo nghĩa **reference vô tình giữ object còn reachable**, ví dụ static cache không dọn, collection tích lũy, listener không unregister, thread-local giữ dữ liệu lâu.

### WeakReference không phải phép đo thời gian thu gom

`WeakReference<T>` cho phép tham chiếu yếu với semantics riêng, nhưng thời điểm referent bị clear/collect **không xác định trước**. Vì vậy không dùng:

~~~java
System.gc();
// System.out.println(weak.get() == null); // Không có kết quả stdout bảo đảm.
~~~

Đây là snippet minh họa và dòng kiểm tra được comment. Nếu cần quản lý **resource bên ngoài heap** như socket/file, dùng try-with-resources hoặc API close phù hợp; **đừng chờ GC** để đóng tài nguyên đúng hạn.

## 13. Object graph, vùng nhớ và memory leak

Object chứa reference fields và reference arrays tạo một **đồ thị tham chiếu** trên heap:

~~~text
GC root: static Registry
          |
          v
      [Registry object]
          |
          +--> list --------> [ArrayList backing array]
                                  |
                                  +--> entry0 -> [Session object]
                                  +--> entry1 -> [Session object]
                                  +--> entry2 -> [Session object]
~~~

Nếu application quên remove các Session hết hạn khỏi registry, các object có thể vẫn reachable và GC không thể thu hồi chúng như “rác”, dù business logic không còn cần. Đây là **logical memory leak**.

Nguyên tắc:
1. Xác định **ai sở hữu** references tới object.
2. Tránh giữ các đối tượng lớn trong static caches không giới hạn.
3. Giới hạn eviction/TTL cho cache và xóa listeners/registrations khi không dùng.
4. Xem heap dump bằng profiler/diagnostic tools khi cần để tìm retainers và đường đi từ GC roots.
5. Không xem việc đặt một local reference về null là giải pháp chữa leak nếu còn references lâu dài ở nơi khác.

Với quy trình kiểm tra, dùng dữ liệu thực tế (số instances, retained heap, GC roots) thay vì chỉ nhìn số lần mã nguồn gọi `new`.

## 14. JIT, escape analysis và giới hạn của câu “object luôn ở heap”

**Ở cấp đặc tả JVM**, heap là vùng runtime mà các class instances và arrays được cấp phát về mặt ngữ nghĩa. Nhưng triển khai JVM hiện đại có thể tối ưu khi kết quả **không làm thay đổi hành vi quan sát được**.

Ví dụ:

~~~java
static int total(int a, int b) {
    Pair pair = new Pair(a, b);
    return pair.first + pair.second;
}

static final class Pair {
    final int first;
    final int second;

    Pair(int first, int second) {
        this.first = first;
        this.second = second;
    }
}
~~~

Đây là **đoạn minh họa cần đặt trong class**. Trong mô hình Java, `new Pair` tạo instance. Một JVM JIT có thể phân tích escape và loại bỏ cấp phát object vật lý bằng **scalar replacement**, vì chỉ cần giá trị hai fields cho kết quả. Tuy nhiên:
- **Không được khẳng định** JIT chắc chắn tối ưu ví dụ này trong mọi lần chạy, JVM, cấu hình hoặc compilation tier.
- Sự tối ưu hóa không làm sai identity/equality hay thay đổi side effects cần quan sát.
- Không nên mô tả chắc chắn “object Pair chuyển sang stack” chỉ vì escape analysis. JVM có thể loại bỏ allocation, scalarize hoặc dùng chiến lược khác; “stack allocation” không phải một ngữ nghĩa Java công khai bắt buộc.
- Khi object escape sang thread khác, được trả về hoặc được dùng theo cách identity có thể quan sát, điều kiện tối ưu hóa khác nhau.

Vì vậy câu học liệu chính xác là: **JVM mô hình hóa class instances và arrays ở heap; nơi lưu trữ vật lý thực tế chịu các tối ưu hợp lệ**. Cách nói “mọi object Java bắt buộc luôn chiếm một khối RAM heap riêng” là quá cứng đối với JIT.

## 15. Static reference cũng có thể trỏ đến object trên heap

Ví dụ:

~~~java
class AppState {
    static final int LIMIT = 100;
    static Box current = new Box(7);
}
~~~

Đây là class minh họa. Hãy phân biệt:
- `LIMIT` là static primitive field (và có thể là constant variable), không phải heap object Integer.
- `current` là **static reference field** thuộc class runtime.
- Biểu thức `new Box(7)` tạo Box object theo ngữ nghĩa trên heap.
- Field `current` giữ giá trị reference tới Box, không chứa toàn bộ object Box.
- Nếu `current` tiếp tục giữ reference tới Box, object đó có thể còn reachable qua static state, làm ảnh hưởng thời gian sống.

Việc field static có thể được JVM quản lý trong runtime data structures nào là chi tiết thực thi. Không được coi biến static như “một object nằm trên heap” chỉ vì static field có kiểu reference.

### Object references có thể nằm trong heap

Với:

~~~java
class Node {
    Node next;
    int value;
}
~~~

một Node object có field `next` kiểu reference **nằm trong chính Node object**; Node object theo mô hình JVM nằm trên heap. Vì vậy không phải **mọi reference** đều “nằm trên stack”. Reference values có thể nằm trong object fields, array elements, static fields hoặc local frames.

## 16. Bytecode Java liên quan đến heap và reference values

Compiler Java thường sinh các opcode sau khi tạo/truy cập object và array:

| Opcode | Vai trò | Ví dụ Java |
| --- | --- | --- |
| `new` | Tạo class instance chưa hoàn tất constructor initialization | `new Box(5)` |
| `invokespecial` | Gọi constructor `<init>` trong quy tắc tương ứng | `new Box(5)` |
| `newarray` | Tạo array một chiều của primitive | `new int[3]` |
| `anewarray` | Tạo array của reference component type | `new Box[2]` |
| `multianewarray` | Tạo mảng nhiều chiều với những chiều được chỉ định | `new int[2][3]` |
| `aload` / `astore` | Nạp/lưu **reference value** từ/vào local variable | `Box b = ...` |
| `getfield` / `putfield` | Đọc/ghi instance field theo symbolic reference | `b.value` |
| `getstatic` / `putstatic` | Đọc/ghi static field | `AppState.current` |
| `aaload` / `aastore` | Đọc/ghi reference elements trong array | `boxes[0]` |
| `iaload` / `iastore` | Đọc/ghi int elements trong array | `numbers[0]` |
| `arraylength` | Đọc độ dài array | `arr.length` |
| `if_acmpeq` / `if_acmpne` | So sánh hai reference values theo identity | `a == b` |
| `ifnull` / `ifnonnull` | Kiểm tra reference bằng null | `b == null` |
| `checkcast` / `instanceof` | Kiểm tra/chuyển kiểu reference theo quy tắc JVM | `(Box)obj`, `obj instanceof Box` |

Một đoạn disassembly minh họa:

~~~text
new           #7     // class Box
dup
iconst_5
invokespecial #9     // Box.<init>:(I)V
astore_1             // Lưu reference vào local variable
aload_1              // Nạp reference từ local variable
getfield      #12    // Box.value:I
~~~

Chỉ số `#7/#9/#12` là **minh họa**, không phải chỉ số cố định. Instruction `new` tạo instance; `dup` giữ reference phù hợp để constructor call và assignment; `astore` lưu reference value trong slot, **không sao chép object Box**.

Với array:

~~~text
iconst_3
newarray int
astore_2

aload_2
iconst_0
iaload
~~~

Đây cũng là sơ đồ bytecode, không phải dump exact. Nó cho thấy `newarray` tạo array object; `astore_2` chỉ lưu reference; `iaload` đọc int element.

### Thực hành bằng javap

Sau khi biên dịch các ví dụ trên:

~~~bash
javap -c -p HeapReferenceDemo
javap -c -p HeapArrayDemo
javap -v -p HeapReferenceDemo
~~~

Quan sát trong `main`:
1. Các instruction `new`/`invokespecial` để tạo Box/Node.
2. `astore`/`aload` khi gán hoặc truyền reference values.
3. `getfield`/`putfield` khi đọc/ghi field primitive của Box/Node.
4. `newarray`, `anewarray` và các opcode nạp/ghi phần tử.
5. `if_acmp*` hoặc `ifnull/ifnonnull` trong các phép kiểm tra reference.
6. Các lời gọi như `Arrays.toString` hay `PrintStream.println` dùng opcode invoke, không được nhầm với instruction field/array access.

Đừng ép quan hệ “mỗi biểu thức Java = một opcode JVM”: compiler có thể constant-fold, dùng các chuỗi instruction phụ, hoặc JIT tối ưu khi thực thi. Điều JVM cần bảo đảm là **semantics** của chương trình.

## 17. Heap sizing, GC và các công cụ chẩn đoán

Trong HotSpot/JDK phổ biến, bạn có thể cấu hình giới hạn heap bằng các tham số như:
- `-Xms<size>`: heap initial size (theo JVM implementation).
- `-Xmx<size>`: heap maximum size.
- `-Xlog:gc` hoặc `-Xlog:gc*`: log hoạt động GC (với unified logging của HotSpot/JDK hiện đại).

Ví dụ minh họa (không có stdout cố định vì phụ thuộc collector và chương trình):

~~~bash
java -Xms64m -Xmx256m -Xlog:gc -cp . HeapReferenceDemo
~~~

Thông tin giám sát có thể dùng thêm:
- **JFR (Java Flight Recorder)** để khảo sát allocation và hành vi GC.
- **JDK Mission Control** hoặc profiler phù hợp để xem heap usage và retained objects.
- **jcmd** với diagnostic commands hỗ trợ trên JVM cụ thể; cú pháp/khả dụng phụ thuộc phiên bản.
- **Heap dump** để điều tra object graph, classloader và đường đến GC roots; cần chú ý dữ liệu nhạy cảm trong dump.

Cần phân biệt các tình huống:
- `java.lang.OutOfMemoryError: Java heap space` thường chỉ ra heap không cấp phát đủ cho yêu cầu tại thời điểm đó, nhưng nguyên nhân có thể là heap hạn chế, allocation peak hoặc object còn được giữ lâu.
- Không phải mọi `OutOfMemoryError` đều xuất phát từ Java heap: native memory, metaspace, direct buffers, thread creation... có thể liên quan.
- Tăng `-Xmx` có thể che triệu chứng chứ không giải quyết object retention/leak; cần đo **retained heap** và reference paths.
- Memory “không giảm ngay trên OS” không chứng minh object còn alive: JVM có thể giữ lại committed heap để tái sử dụng.

## 18. Những ngộ nhận phổ biến

| Nhận định | Cách hiểu đúng |
| --- | --- |
| “`Box a = new Box()` nghĩa là toàn bộ Box nằm ngay trong biến a trên stack.” | Biến a giữ **reference value**; object được cấp phát theo ngữ nghĩa heap. |
| “Gán `b = a` tạo một bản copy Box mới.” | Chỉ sao chép reference; a và b có thể alias cùng object. |
| “Java truyền object theo reference nên callee gán tham số sẽ đổi biến caller.” | Java **pass-by-value**; callee chỉ nhận bản sao reference. |
| “Hai object có field bằng nhau nên `==` là true.” | `==` trên references kiểm tra identity; equality logic phải dùng method thích hợp. |
| “`new Box[5]` tạo năm Box instances.” | Chỉ tạo một array chứa 5 references default null. |
| “Array primitive chứa các Integer/Double objects.” | `int[]` chứa int values; `double[]` chứa double values, không boxing mỗi phần tử. |
| “Array 2D là một object ma trận duy nhất.” | Theo mô hình Java, là mảng của references tới các mảng con; độ dài hàng có thể khác nhau. |
| “`final Box b` làm Box immutable.” | Final ngăn reassign biến/field; Box có thể mutable. |
| “Clone reference array là deep copy toàn bộ objects.” | Mặc định shallow copy element references. |
| “`reference = null` lập tức giải phóng heap.” | Chỉ thay reference; GC quyết định lúc thu gom dựa reachability. |
| “Ra khỏi method thì object bị hủy cùng frame.” | Frame kết thúc, object có thể vẫn reachable từ nơi khác. |
| “Mọi reference luôn ở stack.” | Reference values có thể nằm trong fields, static fields và array elements. |
| “Mọi object vật lý bắt buộc ở heap với một địa chỉ cố định.” | JVM semantics đặt objects ở heap, JIT có thể tối ưu allocation/scalar replacement. |
| “Một static reference ở class là chính object mà nó chỉ tới.” | Static field chứa reference; referent là object riêng. |
| “Heap shared có nghĩa đa luồng sửa object luôn thread-safe.” | Shared heap không bảo đảm atomicity/visibility; cần Java Memory Model và synchronization phù hợp. |
| “`System.gc()` bảo đảm object biến mất ngay.” | Chỉ là gợi ý; GC timing không xác định. |

## 19. Tự kiểm tra kiến thức

**Câu 1.** `Box a = new Box(1); Box b = a;` có bao nhiêu Box instances mới được tạo?

**Đáp án:** **Một**, từ biểu thức `new Box(1)`. `b = a` chỉ sao chép reference value.

**Câu 2.** Nếu `b.value = 5`, `a.value` sẽ bằng bao nhiêu trong ví dụ trên?

**Đáp án:** **5**, vì cả hai variables tham chiếu cùng Box object (khi chạy tuần tự, không có thay đổi khác).

**Câu 3.** Tại sao method `rebind(Box b) { b = new Box(9); }` không đổi tham chiếu caller?

**Đáp án:** Java pass-by-value: tham số b chỉ là **bản sao reference**. Gán lại tham số không đổi biến nguồn trong caller.

**Câu 4.** `Box[] boxes = new Box[3];` tạo ba Box objects hay không?

**Đáp án:** **Không.** Nó tạo một Box[] array object với ba element references mặc định null.

**Câu 5.** `int[] xs = new int[4];` chứa bốn Integer objects?

**Đáp án:** **Không.** Chứa bốn primitive int values có default 0.

**Câu 6.** `int[][] grid = new int[2][3];` có bao nhiêu array objects theo semantics thông thường?

**Đáp án:** **Ba**: một outer array length 2 và hai inner int arrays length 3.

**Câu 7.** `int[][] grid = new int[2][];` thì sao?

**Đáp án:** Ban đầu có **một outer array**, hai reference elements của nó là null, chưa có inner arrays.

**Câu 8.** `a == b` và `a.equals(b)` có cùng ý nghĩa đối với references không?

**Đáp án:** Không. `==` so identity; `equals` là phương thức có thể được override để so nội dung, tùy class.

**Câu 9.** Đặt biến `a = null` khi vẫn có `b` trỏ cùng object sẽ giải phóng object chứ?

**Đáp án:** Không. Object vẫn reachable qua b; dù không còn reference nào, GC cũng không cam kết thu hồi ngay.

**Câu 10.** Reference nào có thể nằm trong heap?

**Đáp án:** Reference values trong **instance fields và reference-array elements** có thể nằm trong heap objects. Không phải mọi reference đều chỉ nằm trong stack frames.

**Câu 11.** JIT scalar replacement có làm Java “không còn heap” không?

**Đáp án:** Không. Heap là thành phần của mô hình JVM; các tối ưu chỉ được thực hiện khi bảo toàn semantics quan sát được, và không có bảo đảm áp dụng mọi object.

**Câu 12.** `final int[] numbers` làm gì?

**Đáp án:** Ngăn gán lại reference variable/field phù hợp, nhưng **không ngăn thay đổi elements** của array.

**Câu 13.** Vì sao `Object[] objects = new String[1]` có thể ném `ArrayStoreException`?

**Đáp án:** Runtime array object thực sự có component type String; JVM kiểm tra element store, không chỉ dựa vào kiểu khai báo Object[] của biến.

**Câu 14.** Biến `static Box shared` có phải Box object được đặt trong static field không?

**Đáp án:** Không. Static field lưu **reference value**. Referent Box object là một thực thể riêng theo mô hình heap.

**Câu 15.** Heap shared giữa các thread có bảo đảm `counter++` trên field của object được thực thi nguyên tử?

**Đáp án:** Không. Phép đọc–tính–ghi cần đồng bộ hoặc atomic operations khi có concurrent updates.

## 20. Bản đồ ghi nhớ

~~~text
                    JVM RUNTIME DATA AREAS
                +----------------------------+
                | HEAP (được các thread share)|
                |  - Class instances         |
                |  - Array objects           |
                |  - Object graphs           |
                +----------------------------+
                     ^                ^
                     |                |
       reference value                reference value
                     |                |
   +-----------------+----+       +---+------------------+
   | THREAD A / FRAME     |       | STATIC FIELD /      |
   | local variable       |       | INSTANCE REF FIELD  |
   | operand stack        |       | / ARRAY REF ELEMENT |
   +----------------------+       +----------------------+

new Box()       -> class instance trên heap (ngữ nghĩa)
new Box[3]      -> array object, ba refs null
new int[3]      -> array object, ba primitive ints 0
b = a           -> copy reference, KHÔNG copy object
b.value = 10    -> mutate object được reference chỉ tới
method(b)       -> copy reference value vào parameter
b = null        -> đổi reference, KHÔNG immediate free
GC              -> dựa reachability, không theo 1 dấu chấm phẩy
JIT             -> có thể tối ưu allocation, bảo toàn semantics
~~~

**Kết luận:** **Đối tượng/mảng** và **biến tham chiếu** là hai khái niệm tách biệt. Heap lưu class instances và arrays theo mô hình JVM; references có thể nằm ở local frames, fields hoặc array elements. Hiểu aliasing, truyền tham số theo giá trị, identity, array semantics và GC reachability giúp giải thích đúng hành vi Java và tránh lỗi thiết kế bộ nhớ.

## Tài liệu tham khảo chuẩn

- [JVMS Java SE 21 — §2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5.3).
- [JVMS Java SE 21 — §2.5.2 Java Virtual Machine Stacks](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5.2).
- [JVMS Java SE 21 — §2.6 Frames](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.6).
- [JVMS Java SE 21 — §2.4 Reference Types and Values](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.4).
- [JVMS Java SE 21 — §6.5 Instructions](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5).
- [JLS Java SE 21 — §4.3 Reference Types and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.3).
- [JLS Java SE 21 — §10 Arrays](https://docs.oracle.com/javase/specs/jls/se21/html/jls-10.html).
- [JLS Java SE 21 — §15.9 Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.9).
- [JLS Java SE 21 — §12.6 Finalization of Class Instances](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.6).
- [JDK 21 — javap](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-004">Method area và run-time constant pool trong mô hình trừu tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Method area và run-time constant pool trong mô hình trừu tượng JVM

## 1. Mục tiêu bài học

Trong Java Virtual Machine Specification (JVMS), **method area** và **run-time constant pool** là những thành phần của **run-time data areas**. Method area là vùng logic chia sẻ giữa các thread, phục vụ cấu trúc dữ liệu cấp lớp. Run-time constant pool là một phần của method area theo mô hình đặc tả: **mỗi runtime class/interface có một pool riêng**, tạo từ constant pool trong tệp class, chứa hằng và tham chiếu biểu tượng cần cho thực thi/liên kết.

Điểm quan trọng: đây là **mô hình trừu tượng**, không phải cam kết rằng mọi JVM phải lưu metadata tại cùng một địa chỉ, cùng một vùng heap vật lý hoặc cùng công nghệ cấp phát. Với HotSpot Java 17, nhiều loại class metadata được quản lý trong **Metaspace**; Metaspace là **chi tiết implementation**, không phải tên thay thế được đặc tả bắt buộc cho toàn bộ method area.

Học xong, bạn có thể:

- Phân biệt method area với heap, JVM stack, PC và native method stack.
- Kể các dữ liệu cấp class tiêu biểu nằm trong method area theo JVMS, cùng ý nghĩa chia sẻ giữa threads.
- Phân biệt **class-file constant pool**, **run-time constant pool**, **String intern pool**, **static field state** và **String objects**.
- Đọc các tag thường gặp trong class-file constant pool: Utf8, Class, String, Integer, Methodref, Fieldref, NameAndType, MethodHandle, MethodType, Dynamic và InvokeDynamic.
- Giải thích vai trò của constant-pool indexes trong **ldc**, **getstatic**, **invokevirtual**, **invokestatic** và cơ chế **resolution**.
- Phân biệt loading, linking, initialization; không nhầm việc resolve Class với chạy static initializer.
- Hiểu mối liên hệ của class loader, một runtime class và pool riêng; tại sao cùng tên class nhưng khác defining loader có thể tạo các pools khác nhau.
- Quan sát bằng Java 17+, javap và những công cụ HotSpot khi cần, đồng thời tránh suy diễn memory layout từ bytecode.

## 2. Bản đồ các vùng dữ liệu JVM

Theo JVMS §2.5, các vùng có phạm vi khác nhau:

~~~text
                        JVM đang thực thi
+----------------------------------------------------------------+
| Các vùng logic dùng chung giữa threads                         |
|                                                                |
|  HEAP                             METHOD AREA                  |
|  +--------------------------+    +--------------------------+  |
|  | Instances                |    | Cấu trúc cấp class       |  |
|  | Arrays                   |    | Thông tin fields/methods |  |
|  | String objects           |    | Code methods/ctors       |  |
|  | ...                      |    | Runtime pool của class A |  |
|  +--------------------------+    | Runtime pool của class B |  |
|                                  +--------------------------+  |
|                                                                |
| Mỗi thread:                                                    |
|  +-----------------------+  +-----------------------+          |
|  | PC register           |  | PC register           |          |
|  | JVM stack             |  | JVM stack             |          |
|  |   frame: locals       |  |   frame: locals       |          |
|  |   frame: operand stack|  |   frame: operand stack|          |
|  | native stack nếu dùng |  | native stack nếu dùng |          |
|  +-----------------------+  +-----------------------+          |
+----------------------------------------------------------------+
~~~

**Sơ đồ logic, không phải ảnh chụp RAM.** JVM được phép tổ chức nhiều thành phần bằng các cấu trúc nội bộ khác nhau miễn tuân thủ ngữ nghĩa chuẩn.

| Vùng | Thuộc phạm vi | Mục đích |
| --- | --- | --- |
| **Heap** | Dùng chung | Nơi tạo class instances và arrays theo mô hình JVM |
| **Method area** | Dùng chung | Cấu trúc cấp lớp, run-time constant pools, method/field data và code |
| **Run-time constant pool** | Một pool logic cho mỗi runtime class/interface | Constants và symbolic references phục vụ thực thi/linking |
| **PC register** | Riêng mỗi thread | Vị trí instruction JVM hiện hành theo quy tắc của VM |
| **JVM stack** | Riêng mỗi thread | Frames của các lời gọi method; mỗi frame có locals, operand stack |
| **Native method stack** | Theo thread nếu implementation cần | Thực thi native code |

Một **method call** tạo frame riêng; frame **không tự chứa bản sao toàn bộ metadata của class**. Method code/metadata có thể được các threads cùng sử dụng, nhưng locals và operand stack của từng lời gọi là trạng thái riêng.

### 2.1. Frame có tham chiếu tới runtime pool

JVMS §2.6.3 về dynamic linking quy định mỗi frame chứa tham chiếu tới **run-time constant pool của class chứa method đang được thực thi**. Khi method ở class A gọi method của class B, instruction trong A có thể dùng pool entry của A để resolve tới B. Khi phương thức B chạy, frame mới dùng pool của **B**.

~~~text
Class A runtime pool
  #n: symbolic Methodref B.work:()V
       |
       v
Frame đang chạy A.execute()
  - locals
  - operand stack
  - reference -> A runtime pool
       |
       v
invoke B.work
       |
       v
Frame mới của B.work()
  - locals
  - operand stack
  - reference -> B runtime pool
~~~

**Không được nhầm pool owner với method owner**: entry trong caller A mô tả B.work có thể nằm ở pool của A, không phải do đó được lưu trong pool của B.

## 3. Method area theo đặc tả

JVMS §2.5.4 mô tả method area:

- Tồn tại ở cấp JVM và **dùng chung giữa tất cả JVM threads**.
- Được tạo khi JVM khởi động.
- Lưu **per-class structures**, như runtime constant pool, field/method data và code methods/constructors; có cả code dùng cho class/interface initialization và instance initialization.
- **Logically part of heap**, nhưng **đặc tả không bắt buộc vị trí vật lý hay chính sách thu gom/compact cụ thể**.
- Có thể cố định kích thước hoặc mở rộng khi cần, tùy implementation.
- Nếu không cấp phát đủ vùng cần thiết, JVM có thể ném **OutOfMemoryError**.

### 3.1. Per-class metadata không phải object instance

Ví dụ:

~~~java
class Customer {
    private int id;
    static int created;

    Customer(int id) {
        this.id = id;
        created++;
    }
}
~~~

Theo ý nghĩa logic:

- **Thông tin của class Customer**: tên, fields/methods, descriptors, code constructor và những cấu trúc liên quan thuộc per-class metadata.
- **Một instance Customer**: object có instance field **id** theo mô hình heap.
- **Static field created**: trạng thái cấp runtime class; metadata mô tả field khác với giá trị hiện tại có thể thay đổi.
- **Frame khi chạy constructor Customer(int)**: locals/operand stack thuộc JVM stack của thread đang gọi.

Không thể kết luận từ bảng khái niệm này rằng byte của static field chắc chắn nằm đúng một offset trong method area trên mọi JVM; **bố trí storage vật lý là chi tiết VM**.

### 3.2. “Logically part of heap” cần đọc chính xác

Câu của JVMS là mô tả trừu tượng, không có nghĩa:

- **-Xmx** tự động kiểm soát toàn bộ class metadata của HotSpot.
- Object heap và method area phải là một vùng native memory liên tục.
- PermGen là tên chuẩn của JVM method area.
- Một heap dump luôn thể hiện nguyên trạng run-time pool như văn bản trong javap.
- JVM bắt buộc GC method area giống Java object heap.

Trong HotSpot Java 17, metadata thường thuộc các cơ chế Metaspace/native memory; nhiều String objects vẫn được cấp phát trên managed heap. Không gắn mọi thành phần JVM abstraction với một vùng cấp phát duy nhất do JVM implementation có thể thay đổi.

## 4. Run-time constant pool theo đặc tả

JVMS §2.5.5: **mỗi runtime class hoặc interface có run-time constant pool** được tạo khi lớp đó được tạo trong quá trình loading. Pool là representation lúc chạy của **constant_pool** trong class file, chứa cả những hằng số đã biết lúc biên dịch lẫn các tham chiếu tượng trưng được resolve khi cần.

Nhìn hai tầng:

~~~text
Trước runtime:
  Example.class
    constant_pool_count
    constant_pool[]:
      #1 Utf8       "demo/Example"
      #2 Class      -> #1
      #3 Utf8       "value"
      #4 Utf8       "I"
      #5 NameAndType -> #3,#4
      #6 Fieldref   -> Class+NameAndType
      ...

Khi tạo runtime class:
  ("demo.Example", defining loader)
    -> per-class metadata
    -> run-time constant pool
       - symbolic references
       - literal constants / loadable constants
       - trạng thái liên kết thích hợp
    -> methods/fields/code và các cấu trúc cần thiết
~~~

Các chỉ số ví dụ ở trên **không đại diện cho class file thật**. Đặc tả mô tả nghĩa của các entry, không quy định JVM phải clone nguyên mảng cp_info vào RAM rồi thay từng string bằng pointer.

### 4.1. Pool không chỉ lưu số và String literal

| Entry thường gặp | Dữ liệu/vai trò |
| --- | --- |
| **CONSTANT_Utf8** | Chuỗi ký tự metadata: tên class, field/method, descriptors... |
| **CONSTANT_Integer / Float / Long / Double** | Numeric constants |
| **CONSTANT_Class** | Tham chiếu tượng trưng tới class/interface hoặc array class |
| **CONSTANT_String** | String constant mô tả qua Utf8 reference |
| **CONSTANT_Fieldref** | Symbolic field reference |
| **CONSTANT_Methodref** | Symbolic method reference |
| **CONSTANT_InterfaceMethodref** | Symbolic interface-method reference |
| **CONSTANT_NameAndType** | Tên member cùng descriptor |
| **CONSTANT_MethodHandle** | Method-handle constant, reference kind và target |
| **CONSTANT_MethodType** | Method type constant |
| **CONSTANT_Dynamic** | Dynamically-computed constant theo bootstrap resolution |
| **CONSTANT_InvokeDynamic** | Dynamically-computed call site |
| **CONSTANT_Module / Package** | Dữ liệu module/package theo ngữ cảnh class file |

**CONSTANT_Utf8 không đồng nghĩa một String object đã được tạo cho mỗi entry.** Nó là dữ liệu biểu diễn text trong class-file constant pool. Với String literal, pool metadata có thể dẫn đến String reference trong runtime theo ngữ nghĩa intern/literal, nhưng metadata và object là hai thực thể có vai trò khác nhau.

### 4.2. Giới hạn của class-file pool

Tệp class dùng:

~~~text
u2 constant_pool_count;
cp_info constant_pool[constant_pool_count - 1];
~~~

Vì vậy giá trị lớn nhất có thể biểu diễn cho **constant_pool_count** là **65.535**; các vị trí entry thường có index **1 đến count-1**. Chỉ số **0 không phải entry thông thường**. **CONSTANT_Long/Double** chiếm hai index slots trong việc đánh số, dù là một cp_info entry được mã hóa.

**CONSTANT_Utf8** dùng u2 length, tối đa **65.535 byte Modified UTF-8**. Đây là giới hạn **số byte đã mã hóa**, không phải 65.535 Unicode code points hay số ký tự Java.

Không suy:

- JVM chỉ có một constant pool 65.535 slots cho tất cả classes.
- JVM chỉ có thể nạp 65.535 classes.
- Một class-file constant pool nhỏ chắc chắn nghĩa là ứng dụng không thể bị Metaspace OOME.
- Một String object ở runtime chỉ dài tối đa 65.535 byte.
- Toàn bộ runtime pool có kích thước cố định bằng số entries nhân một số byte cứng.

Giới hạn định dạng **một class file** là vấn đề khác với giới hạn **tài nguyên bộ nhớ của tiến trình**.

## 5. Phân biệt bốn kiểu “pool” và storage thường bị trộn lẫn

| Khái niệm | Nó là gì? | Sai lầm phổ biến |
| --- | --- | --- |
| **Class-file constant pool** | Dữ liệu nhị phân cp_info trong .class | Coi đây là object array Java đang chạy |
| **Run-time constant pool** | Pool logic thuộc runtime class/interface trong method area | Coi toàn JVM chỉ có một pool chung |
| **String intern pool** | Canonical String identity qua literals và String.intern() | Coi đây là toàn bộ CONSTANT_Utf8/Methodref/Class entries |
| **Static field storage** | Trạng thái cấp lớp của static fields | Coi mọi giá trị static là constant-pool entries |

Ngoài ra **JIT code cache** phục vụ compiled native machine code trong HotSpot; nó không đồng nghĩa class-file bytecode hoặc run-time constant pool.

### 5.1. “String literal nằm trong constant pool” nghĩa chính xác là gì?

Một string literal như **"hello"** có representation phù hợp trong class file constant pool. Khi instruction cần giá trị, JVM cung cấp một **reference tới String instance** theo ngữ nghĩa của ngôn ngữ Java và String intern mechanism.

Do đó cần phân biệt ba thứ:

1. **Metadata** mô tả literal trong class file/runtime pool.
2. **String object** được Java code quan sát.
3. **Reference** tới String object nằm trên operand stack/local variable hoặc một field.

Nếu dùng **ldc** nạp String reference, đừng kết luận String object bắt buộc nằm trong method area chỉ vì ldc sử dụng runtime constant pool.

## 6. Resolution: từ symbolic reference tới entity runtime

Giả sử mã Java:

~~~java
// Minh họa trong một method:
int length = text.length();
~~~

Caller class có thể chứa symbolic method reference:

~~~text
Owner internal name: java/lang/String
Method name:          length
Method descriptor:    ()I
Kind:                 Methodref
~~~

Bytecode tương ứng có thể là:

~~~text
aload <slot>
invokevirtual #n    // Method java/lang/String.length:()I
istore <slot>
~~~

**#n** là index vào constant pool của **class chứa instruction**. Khi reference được resolve, JVM xác định class/method phù hợp, kiểm tra loại reference, access rules, class loaders và những constraints bắt buộc.

Việc resolution có thể được cache/biểu diễn theo bất cứ cách nào JVM triển khai cho phép. **Không có yêu cầu** runtime phải lưu một native pointer ngay trong bytes của constant pool giống tất cả JVM khác.

### 6.1. Resolution không đồng nghĩa initialization

Một class có thể:

- Đã được load và tạo run-time constant pool.
- Đã hoàn tất verification và preparation.
- Có một số symbolic references đã resolve.
- Nhưng **chưa chạy static initializer (<clinit>)**.

Ngược lại, khi active use yêu cầu initialization, JVM đảm bảo các bước chuẩn bị và thứ tự khởi tạo thích hợp; một số symbolic references vẫn có thể resolve về sau. JVMS cho phép resolution eager/lazy theo các quy tắc của nó, không bắt buộc “resolve tất cả trước lúc bắt đầu <clinit>”.

### 6.2. Những lỗi liên quan symbolic references

| Lỗi | Nhóm nguyên nhân có thể có |
| --- | --- |
| **NoSuchMethodError** | Class runtime không còn method với name/descriptor như caller kỳ vọng |
| **NoSuchFieldError** | Không tìm thấy field với định danh như caller yêu cầu |
| **IllegalAccessError** | Truy cập member/class không hợp lệ theo JVM access/module rules |
| **IncompatibleClassChangeError** | Sự khác biệt về class/interface/static/instance không phù hợp |
| **NoClassDefFoundError** | JVM không lấy được class cần dùng hoặc class đã ở trạng thái erroneous |
| **BootstrapMethodError** | Bootstrap cho dynamic constant/invokedynamic có vấn đề tương ứng |

Các lỗi này không chứng minh run-time pool “đầy”. Nếu thiếu Metaspace/native memory, thông thường cần chẩn đoán **OutOfMemoryError** riêng; nếu class file vượt số slots được phép, compiler/generator có thể báo lỗi định dạng **trước runtime**.

## 7. Những lệnh bytecode thường dùng constant-pool index

| Opcode | Nội dung được liên kết qua pool | Lưu ý |
| --- | --- | --- |
| **ldc** | Loadable constants: int/float, String, Class, MethodHandle, MethodType, dynamic constant phù hợp | Index 1 byte; không nạp long/double thông thường |
| **ldc_w** | Tương tự ldc | Index 2 byte |
| **ldc2_w** | long/double hoặc dynamic constant dạng category 2 thích hợp | Nạp giá trị loại 2 |
| **getstatic** | Fieldref | Đọc field static, có quy tắc initialization |
| **putstatic** | Fieldref | Ghi field static |
| **getfield / putfield** | Fieldref | Đọc/ghi instance field qua object reference |
| **invokevirtual** | Methodref | Virtual method dispatch |
| **invokestatic** | Methodref/InterfaceMethodref phù hợp | Gọi static method, có thể trigger initialization |
| **invokeinterface** | InterfaceMethodref | Interface method dispatch |
| **invokedynamic** | InvokeDynamic entry | Liên kết dynamic call site qua bootstrap |
| **new** | Class reference | Tạo instance, phải tuân initialization |
| **checkcast / instanceof** | Class reference | Kiểm tra type, không tự init class mục tiêu chỉ bởi cast/test |

**Điểm dễ nhầm:** opcode dùng constant-pool entry không có nghĩa operand stack chứa cả pool. **ldc** đẩy giá trị/tham chiếu phù hợp; **invokevirtual** gọi method sau resolution; **getstatic** đọc trạng thái field — các thao tác có tác dụng khác nhau.

### 7.1. Constant pool và dynamic linkage trong Java hiện đại

Java 17 có thể dùng **invokedynamic** cho string concatenation hoặc lambdas tùy cách compiler sinh bytecode. Constant-pool entry tương ứng liên kết với thông tin **BootstrapMethods attribute** và bootstrap method thích hợp.

**Bootstrap method của invokedynamic không đồng nghĩa bootstrap class loader.** Đây là hai thuật ngữ cùng có từ “bootstrap” nhưng mang vai trò hoàn toàn khác:

- Bootstrap **class loader** là một nguồn định nghĩa lớp lõi.
- Bootstrap **method** của invokedynamic/condy tham gia tính toán call site/constant qua cơ chế dynamic linkage.

Đừng giả định mọi string concatenation trong mọi class file Java 17 bắt buộc dùng đúng một dãy indy instructions; xem javap trên artifact thực tế.

## 8. Ví dụ Java 17+: pool, hằng và class initialization

Tạo file **RuntimePoolDemo.java**:

~~~java
public class RuntimePoolDemo {
    static class Target {
        static final int LIMIT = 40 + 2;
        static final String LABEL = "java" + "-vm";
        static int COMPUTED = loadComputed();

        static int loadComputed() {
            System.out.println("Target:<clinit>");
            return 7;
        }

        static String greet(String name) {
            return "Hello, " + name;
        }
    }

    public static void main(String[] args) {
        System.out.println("begin");

        Class<?> type = Target.class;
        System.out.println("class=" + type.getSimpleName());

        System.out.println("limit=" + Target.LIMIT);
        System.out.println("label=" + Target.LABEL);
        System.out.println("before-active-use");

        System.out.println("computed=" + Target.COMPUTED);
        System.out.println("greet=" + Target.greet("JVM"));
        System.out.println("end");
    }
}
~~~

Lệnh chạy:

~~~bash
javac --release 17 -encoding UTF-8 RuntimePoolDemo.java
java RuntimePoolDemo
~~~

**Stdout chính xác:**

~~~text
begin
class=Target
limit=42
label=java-vm
before-active-use
Target:<clinit>
computed=7
greet=Hello, JVM
end
~~~

Giải thích:

1. **Target.class** sử dụng class literal (có thể tương ứng ldc Class entry trong bytecode), nhưng không bắt buộc chạy **Target.<clinit>**.
2. **LIMIT** và **LABEL** là compile-time constant variables nên caller có thể dùng hằng đã tính sẵn.
3. **COMPUTED** không là constant variable; đọc nó là active use khiến Target initialization chạy lần đầu, in **Target:<clinit>**.
4. Gọi **Target.greet** sau đó không chạy lại static initializer.
5. Trên compiler Java 17 thông dụng, nối chuỗi với tham số có thể qua **invokedynamic**, nhưng điều đó là chi tiết bytecode cần quan sát thực tế.

**Không được dùng stdout trên để suy ra thời điểm chính xác mọi pool entry đã được resolve**. Không in static message không chứng minh class chưa load hoặc link.

### 8.1. Quan sát bằng javap

~~~bash
javap -v -p RuntimePoolDemo
javap -v -p 'RuntimePoolDemo$Target'
javap -c -p RuntimePoolDemo
javap -c -p 'RuntimePoolDemo$Target'
~~~

Trong output:

- **Constant pool** là bảng class-file entries gồm Utf8, Class, String, Fieldref, Methodref, NameAndType...
- **Code** có các instructions và indexes; **#n** là constant-pool index.
- Bạn có thể thấy **ldc** cho Class literal hoặc String constant; không phải mọi literal đều cần ldc riêng vì có tối ưu hằng.
- Với **Target.COMPUTED**, caller có thể có **getstatic RuntimePoolDemo$Target.COMPUTED:I**.
- Với **Target.LIMIT**, bytecode của caller có thể chứa trực tiếp **42** hoặc gộp sẵn vào chuỗi in, không cần getstatic.
- Nếu thấy **BootstrapMethods**, hãy xem các references tới dynamic linkage cho indy.

Output offset, index và trình tự mã lệnh có thể thay đổi theo compiler/JDK. Không coi **javap -v** là bản dump run-time pool trong bộ nhớ của tiến trình đang chạy; javap đọc **class file**.

## 9. Ví dụ Java 17+: String literal, new String và intern

Tạo file **StringIdentityDemo.java**:

~~~java
public class StringIdentityDemo {
    public static void main(String[] args) {
        String literal = "abc";
        String folded = "ab" + "c";
        String constructed = new String("abc");
        String canonical = constructed.intern();

        System.out.println("foldedSame=" + (literal == folded));
        System.out.println("newDifferent=" + (literal != constructed));
        System.out.println("internSame=" + (literal == canonical));
        System.out.println("contentSame=" + literal.equals(constructed));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 StringIdentityDemo.java
java StringIdentityDemo
~~~

**Stdout:**

~~~text
foldedSame=true
newDifferent=true
internSame=true
contentSame=true
~~~

**Lý do:**

- **"ab" + "c"** là compile-time constant expression, tương ứng string literal có nội dung **abc**.
- **new String("abc")** tạo object String khác về identity, dù nội dung bằng literal.
- **intern()** trả canonical String reference cho nội dung **abc**.
- **equals()** so nội dung, **==** so reference identity.

Thí nghiệm này giúp tách **metadata constant pool** khỏi **String object** được application code sử dụng. Với HotSpot hiện đại, String objects được quản lý trên Java heap; run-time constant pool không phải một mảng chứa nguyên các java.lang.String instances bất biến.

## 10. Class loader và nhiều run-time pools có cùng binary name

Runtime class identity không chỉ là binary name, mà gắn với **defining class loader**.

~~~text
Loader A defines demo.Widget
   -> Runtime class A
   -> Class metadata A
   -> Run-time constant pool A

Loader B defines demo.Widget
   -> Runtime class B
   -> Class metadata B
   -> Run-time constant pool B

Hai tên demo.Widget giống nhau; hai runtime classes khác nhau.
~~~

Nếu child loaders ủy quyền cho cùng parent đã định nghĩa class, kết quả có thể là **cùng** runtime class và pool, không tự nhân bản. Chỉ khi **defining loaders thực sự khác nhau**, các runtime classes mới là những kiểu khác nhau.

Điều này quan trọng khi:

- Application server có nhiều deployments/plugins dùng các loader riêng.
- Cùng tên thư viện được nạp ở các isolated loader namespaces.
- Framework sinh proxy classes/dynamic classes.
- Cần phân tích **ClassCastException** với hai tên kiểu nhìn y hệt.
- Theo dõi class unloading và memory retention.

Method area là vùng logic dùng chung giữa JVM threads; điều đó **không xóa ranh giới kiểu** giữa những class do loaders khác nhau định nghĩa.

## 11. HotSpot Metaspace và method area không đồng nhất

| Thuật ngữ | Cấp độ | Ý nghĩa |
| --- | --- | --- |
| **Method area** | JVMS abstraction | Vùng logic cấp lớp, gồm run-time pool và code/metadata theo đặc tả |
| **Metaspace** | HotSpot Java 8+ | Cơ chế cấp phát/quản lý nhiều loại class metadata trong native memory |
| **PermGen** | HotSpot lịch sử | Cơ chế cũ trước Java 8; không là vùng đang dùng của HotSpot Java 17 |
| **Compressed class space** | HotSpot implementation | Vùng metadata phụ thuộc cấu hình compressed class pointers |
| **Code cache** | JIT implementation | Nơi giữ machine code compiled bởi JIT; không phải class-file constant pool |
| **Java heap** | JVM abstraction và implementation | Nơi lưu objects/arrays, gồm String instances theo HotSpot hiện đại |

**Lưu ý:** Mô hình JVMS nói method area logically part of heap nhưng không bắt buộc layout, GC/compaction hoặc location. Mô tả “Metaspace lưu metadata bằng native memory” là mô tả **HotSpot implementation**, không làm sửa lại câu của đặc tả.

### 11.1. Điều gì không nên suy ra?

Không được kết luận:

- Run-time constant pool luôn có vị trí chính xác trong một native block.
- Tất cả numeric constants được lưu nguyên một cấu trúc Java object.
- Mỗi class có metadata kích thước bằng đúng tổng byte file .class.
- -Xmx là giới hạn chính của Metaspace.
- Bytecode trong .class và native instructions sau JIT nằm cùng khu vực vật lý.
- JVM nào cũng có **PermGen**, **Metaspace** hoặc cùng tên logging category.

## 12. Method area có được thu hồi bộ nhớ không?

JVMS **không bắt buộc** method area được garbage-collected hoặc compact theo cách cụ thể. Điều này **không** đồng nghĩa JVM bị cấm thu hồi metadata hoặc class unloading.

Trên HotSpot, khả năng class unloading/reclaim metadata phụ thuộc class loader reachability, GC, runtime state và implementation. Trong một hệ plugin:

~~~text
PluginLoader-v1 -> định nghĩa nhiều classes -> metadata/pools tương ứng
PluginLoader-v2 -> định nghĩa nhiều classes -> metadata/pools mới
PluginLoader-v3 -> ...
~~~

Nếu các plugin loaders cũ bị giữ sống qua **ThreadLocal, thread context loader, callback registry, static cache, executor hoặc MBean**, metadata của classes cũ có thể chưa được giải phóng. Metaspace usage có thể tăng theo số deployments.

**Không tự kết luận memory leak** chỉ vì Metaspace committed memory tăng sau một đợt class loading: JVM có chiến lược commit/reserve/cache và GC riêng. Phải đo xu hướng, số loaders/classes còn sống và các GC/unloading events.

### 12.1. Phân biệt OOME categories

| Thông báo/nhóm lỗi | Nên điều tra |
| --- | --- |
| **OutOfMemoryError: Java heap space** | Object retention, allocations, -Xmx, GC/heap |
| **OutOfMemoryError: Metaspace** | Class metadata, class loading/unloading, -XX:MaxMetaspaceSize và native budget |
| **OutOfMemoryError: Compressed class space** | Cấu hình compressed class metadata space trên HotSpot |
| **ClassFormatError / too many constants** | Class-file format/compiler/generator, không phải heap object usage |
| **NoSuchMethodError** | Linking/reference/binary compatibility, không phải cạn bộ nhớ |

Các chuỗi OOME là cách báo thường thấy trên HotSpot, không phải cam kết tất cả JVM implementations có wording y hệt.

## 13. Công cụ quan sát trên JDK 17

### 13.1. Đọc class-file constant pool

~~~bash
javac --release 17 RuntimePoolDemo.java
javap -v -p RuntimePoolDemo
javap -v -p 'RuntimePoolDemo$Target'
~~~

**javap -v** thể hiện class-file constant pool và attributes; **không cho phép kết luận chính xác kích thước RAM runtime pool**. Quan sát loại entry, name/descriptor và mối quan hệ các indices.

### 13.2. Xem class loading trên HotSpot

~~~bash
java -Xlog:class+load=info RuntimePoolDemo
java -Xlog:help
~~~

Dòng đầu hiển thị class-loading events và có thể in rất nhiều logging lines. Dòng thứ hai liệt kê logging options hỗ trợ. **Class load logs không phải log có <clinit> đã chạy**. Dùng stdout thực nghiệm cùng log để biết sự khác biệt.

### 13.3. Native Memory Tracking và jcmd

Có thể khởi chạy JVM HotSpot với:

~~~bash
java -XX:NativeMemoryTracking=summary RuntimePoolDemo
~~~

Dùng **jcmd** trên một tiến trình Java đang chạy đủ lâu:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> VM.native_memory summary
jcmd <PID> VM.classloaders
jcmd <PID> VM.metaspace
~~~

**<PID>** là placeholder cần thay bằng process ID thực. Những diagnostic commands có thể khác theo bản JDK/JVM; hãy kiểm tra **jcmd <PID> help** trước. **NMT** thường phải được bật khi startup; chương trình demo ở trên thoát rất nhanh, vì vậy muốn attach có thể cần một ứng dụng chạy lâu hơn hoặc quy trình thử nghiệm phù hợp.

NMT giúp xem native allocations theo category nhưng **không phải công cụ đọc từng run-time pool entry**. Khi cần xem object retention, dùng profiler/heap dump; khi cần class-file structure, dùng javap; khi cần native metadata, dùng NMT/metaspace tools tương ứng.

### 13.4. -XX:MaxMetaspaceSize không phải -Xmx

Ví dụ cấu hình chỉ để minh họa cú pháp:

~~~bash
java -XX:MaxMetaspaceSize=128m RuntimePoolDemo
~~~

Không coi **128m** là khuyến nghị production. Đặt quá thấp có thể gây lỗi không cần thiết. Nếu gặp Metaspace OOME, điều tra class loaders/classes, dynamic bytecode generation, unloading/GC và tổng native memory trước khi chọn ngân sách mới.

## 14. Các lỗi nhận thức phổ biến

1. **“Method area chính là Java heap -Xmx trong mọi JVM.”** Sai: “logically part of heap” là abstraction; layout không được bắt buộc.
2. **“Metaspace là một mục bắt buộc trong JVMS.”** Sai: đây là implementation detail của HotSpot.
3. **“Mỗi JVM chỉ có một run-time constant pool duy nhất.”** Sai: một pool logic trên mỗi runtime class/interface.
4. **“Pool chỉ gồm literal strings.”** Sai: có numeric entries và references đến class/field/method, dynamic data...
5. **“CONSTANT_Utf8 là java.lang.String object.”** Sai: đó là constant-pool text metadata.
6. **“String intern pool là toàn bộ run-time constant pool.”** Sai: intern chỉ liên quan canonical String identity.
7. **“Một frame chứa bản copy toàn bộ pool.”** Sai: frame có reference đến pool của class chứa method.
8. **“Javap cho biết địa chỉ pool trong RAM.”** Sai: javap đọc artifact.
9. **“Class literal tự khởi tạo lớp được tham chiếu.”** Sai: việc có Class object không tự chạy <clinit>.
10. **“Resolution và initialization là một.”** Sai: resolution là phần linking, initialization có điều kiện riêng.
11. **“Tăng -Xmx luôn chữa được OOME Metaspace.”** Sai: HotSpot quản lý metadata bằng cơ chế native-memory riêng.
12. **“Method area tuyệt đối không thu hồi.”** Sai: JVMS không bắt buộc GC, không cấm class unloading.
13. **“Cùng tên class chỉ có một runtime pool.”** Sai khi different defining class loaders tạo các runtime classes riêng.
14. **“Constant pool entry count cho biết số object instances.”** Sai: metadata entries và runtime objects là các tập khác nhau.
15. **“Mọi class-file pool có giới hạn 65.535 entries thực.”** Chưa chính xác: count là u2, index 0 dành riêng, Long/Double chiếm hai index slots.
16. **“Run-time pool tối đa 65.535 byte.”** Sai: giới hạn 65.535 áp dụng cho u2 constant_pool_count và từng Utf8 payload (byte MUTF-8); không phải hard cap bộ nhớ runtime pool.

## 15. Bài tập tự kiểm tra

**Bài 1.** Method area chia sẻ giữa threads hay riêng mỗi thread? JVM stacks thì sao?

**Đáp án:** Method area dùng chung; mỗi JVM thread có stack của chính mình. Mỗi frame có reference tới run-time pool tương ứng chứ không chứa bản sao toàn bộ pool.

**Bài 2.** Class A gọi B.work() qua invokestatic. Methodref thường ở class-file constant pool của A hay B?

**Đáp án:** Nằm trong pool của class chứa instruction, tức A; runtime resolution trỏ đến method ở B.

**Bài 3.** Hai loaders định nghĩa cùng binary name C từ cùng bytes. Có mấy runtime pools?

**Đáp án:** Hai pool logic tương ứng hai runtime classes khác defining loader.

**Bài 4.** Việc đọc **C.class** có bảo đảm static initializer C chạy không?

**Đáp án:** Không. Class literal không yêu cầu initialization chỉ do thao tác đó; Class object có thể đã tồn tại sau loading/linking.

**Bài 5.** Nếu javap hiện **CONSTANT_String "abc"**, có phải một String object đang được lưu trong mảng cp_info ở heap không?

**Đáp án:** Không. Entry mô tả string constant; String object/reference runtime là thực thể khác. Javap đọc class file, không dump heap.

**Bài 6.** Một chương trình có 10.000 classes và mỗi class dùng 200 pool slots. Có vi phạm giới hạn một class-file constant pool không?

**Đáp án:** Không chỉ từ các con số đó; mỗi class có pool riêng. Tổng metadata có thể tăng, nhưng đó là bài toán memory khác.

**Bài 7.** Có thể kết luận từ **constant_pool_count=500** rằng runtime metadata đúng 500 × 8 byte không?

**Đáp án:** Không. Entries có loại khác nhau, có Long/Double two-slot rules, và runtime layout tùy JVM.

**Bài 8.** **NoSuchMethodError** liên quan điều gì?

**Đáp án:** Thường do symbolic method reference cần resolve không khớp binary class/method runtime; không chứng minh pool đầy.

**Bài 9.** Vì sao một plugin redeploy nhiều lần có thể làm Metaspace tăng?

**Đáp án:** Nếu loaders cũ và classes của chúng còn được giữ tham chiếu, runtime class metadata/pools không được unloading/reclaimed kịp thời.

**Bài 10.** **-Xmx** có phải tùy chọn chuẩn để giới hạn toàn bộ method area của JVM trên HotSpot Java 17 không?

**Đáp án:** Không. Nó chủ yếu giới hạn Java heap. Metaspace/class metadata có bộ cơ chế và giới hạn riêng ở HotSpot.

**Bài 11.** Class bị load và runtime pool đã hình thành nhưng static block chưa in log. Điều đó mâu thuẫn không?

**Đáp án:** Không. Class loading/linking không đồng nghĩa class initialization.

**Bài 12.** **String.intern()** có resolve tất cả Methodref entries trong pool của class đang gọi nó không?

**Đáp án:** Không. String intern chỉ giải quyết canonical String identity, không thay dynamic linking.

**Bài 13.** Vì sao phải phân biệt **BootstrapMethods attribute** và **bootstrap class loader**?

**Đáp án:** BootstrapMethods cung cấp dữ liệu cho dynamic linkage như invokedynamic; bootstrap loader phục vụ định nghĩa nhiều core classes. Chúng là hai cơ chế khác.

**Bài 14.** JVM có bắt buộc method area nằm trong một vùng contiguous native memory và được compact khi GC không?

**Đáp án:** Không. JVMS không bắt buộc vị trí, cấu trúc native allocation hoặc GC/compaction policies cụ thể.

## 16. Checklist vận dụng

- Xác định khi nào đang nói về **JVMS abstraction** và khi nào nói về **HotSpot implementation**.
- Khi xem memory, tách **method area logic**, **Metaspace**, **Java heap**, **code cache** và **native memory**.
- Nhớ **run-time pool gắn với runtime class/interface**, không với toàn process; xét defining class loader.
- Khi đọc bytecode, biết **pool index thuộc caller class chứa instruction**, không tự chuyển thành owner class của target.
- Khi nhìn **ldc**, phân biệt metadata entry, giá trị trên operand stack và object được tham chiếu.
- Khi nhìn **getstatic/invoke**, xem symbolic resolution, access constraints và quy tắc initialization.
- Khi nói tới constants, tách class-file Utf8/String entries khỏi String.intern và static fields.
- Khi chẩn đoán giới hạn, phân biệt **pool slots per class file**, **Utf8 MUTF-8 byte length**, **runtime metadata budget** và **Java heap**.
- Khi gặp linkage errors, kiểm tra descriptor, runtime dependency, module, class loader và class version.
- Khi nghi ngờ Metaspace leak, đo xu hướng class loaders/classes và kiểm tra references giữ loaders còn sống.
- Khi đưa dẫn chứng, ưu tiên **JVMS/JLS** cho semantics; chỉ dùng tài liệu HotSpot/JDK cho flags, Metaspace và các công cụ native.

### Tài liệu chính thức

- **JVMS Java SE 17, §2.5.3 — Heap:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3
- **JVMS Java SE 17, §2.5.4 — Method Area:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.4
- **JVMS Java SE 17, §2.5.5 — Run-Time Constant Pool:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.5
- **JVMS Java SE 17, §2.6.3 — Dynamic Linking:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.3
- **JVMS Java SE 17, §4.4 — The Constant Pool:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4
- **JVMS Java SE 17, §5.4.3 — Resolution:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3
- **JVMS Java SE 17, §6.5 — Instructions:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
- **Oracle Java SE 17 VM Guide, Native Memory Tracking:** https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html

**Ghi nhớ cốt lõi:** **Method area** là vùng logic chứa metadata/method code và được chia sẻ giữa JVM threads; mỗi runtime class/interface có **run-time constant pool** riêng như một phần của vùng này. Pool chứa literals lẫn symbolic references và được dùng bởi instructions/dynamic linking. **String intern pool**, **static field values**, **heap objects** và **HotSpot Metaspace** là các khái niệm liên quan nhưng **không đồng nghĩa**. Muốn hiểu đúng, hãy phân biệt **mô hình chuẩn JVM** với **bố trí bộ nhớ của implementation cụ thể**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-005">Native method stack và ranh giới lời gọi mã native</a>

<details>
<summary>Xem chi tiết</summary>

# Native method stack và ranh giới lời gọi mã native trong JVM

Khi một thread Java gọi phương thức viết bằng Java, JVM cung cấp mô hình **Java Virtual Machine stack**, các **frames**, local variables và operand stack. Khi lời gọi đi vào một phương thức triển khai bằng C/C++ hoặc thư viện hệ điều hành, thread vượt qua **ranh giới Java ↔ native**. Một JVM implementation có thể dùng **native method stack** (thường được gọi là “C stack”) để hỗ trợ phần thực thi ngoài Java bytecode.

Điểm mấu chốt: **native method stack khác Java heap, khác bộ nhớ off-heap nói chung, và không bắt buộc tồn tại như một stack vật lý thứ hai độc lập với Java stack trên mọi JVM**. JVM Specification (JVMS) mô tả hành vi và các lựa chọn triển khai, không quy định duy nhất một layout của stack trong hệ điều hành. Với một JVM phổ biến như HotSpot, native calls thường sử dụng stack và ABI của OS thread đang chạy; mã JIT/compiler có thể giữ dữ liệu trên thanh ghi, inline functions và tổ chức frames theo cách riêng.

Bài này đi từ khái niệm vùng nhớ đến ranh giới **JNI**, cách truyền tham số và exception, cách tương tác với GC, quản lý native threads, một ví dụ **Java 17 + C đầy đủ** và quy trình chẩn đoán lỗi.

## 1. Native method stack là gì theo JVMS?

JVMS §2.5.6 cho phép implementation dùng conventional stacks (thường gọi là **C stacks**) để hỗ trợ:

- **Native methods**: phương thức triển khai bằng ngôn ngữ khác Java và được JVM gọi.
- Mã bên trong JVM/interpreter nếu được triển khai bằng một ngôn ngữ sử dụng conventional call stacks.

Nếu một JVM không hỗ trợ nạp native methods và không cần conventional stack cho implementation của chính nó, đặc tả **không bắt buộc** cung cấp native method stacks. Khi có, chúng thường gắn với **thread** lúc thread được tạo. Stack có thể có kích thước cố định hoặc mở rộng/co lại theo khả năng implementation.

**Hai kết luận đúng:** Java có khái niệm trừu tượng native method stack; native code thực tế cần ngữ cảnh thực thi và storage tương ứng với ABI/platform.

**Kết luận không đúng:** mỗi method Java gọi native chắc chắn tạo một native stack vùng riêng biệt với Java stack, hoặc mỗi lời gọi native tạo một OS thread mới.

### 1.1. Các giới hạn bộ nhớ

JVMS liên kết native method stacks với những trạng thái lỗi:

- Khi tính toán cần stack lớn hơn kích thước được cho phép, có **`StackOverflowError`** theo quy tắc vùng nhớ này.
- Nếu stack cần mở rộng hoặc cần tạo cho thread mới nhưng không thể cấp phát memory thích hợp, có **`OutOfMemoryError`**.

Đây là mô hình lỗi của JVM Specification. Nó **không cam kết** mọi lỗi trong C — stack smash, buffer overrun, use-after-free, dereference địa chỉ không hợp lệ — đều chuyển thành Java exception dễ `catch`. Mã C lỗi vẫn có thể gây **SIGSEGV/access violation và làm sập toàn bộ tiến trình**.

## 2. Native method stack, JVM stack, Java heap và native memory

| Khái niệm | Vai trò | Phạm vi | Không được nhầm với |
| --- | --- | --- | --- |
| JVM stack | Các frames, local variables, operand stacks, dynamic linking/return logic theo mô hình JVM | Per JVM thread | Java heap |
| Native method stack | Stack dùng cho native methods/JVM implementation nếu dùng conventional stack | Thường per thread | “toàn bộ native memory” |
| Java heap | Java objects và arrays do GC quản lý | Chia sẻ trong JVM | C call frames |
| Method area / metaspace | Class metadata và runtime structures tương ứng; cách cấp phát phụ thuộc JVM | Do VM quản lý | Native method call stack |
| Native/off-heap memory | Native stacks, `malloc`, direct buffers, JIT code cache, VM structures và nhiều thành phần khác | Tùy cấp phát | Chỉ một native method stack |
| Registers | Values/call state do compiler/CPU quản lý | Lúc thực thi | Java local-variable slot vật lý cố định |

Ví dụ, `String input` trong Java chứa **reference**, không chép toàn bộ nội dung String vào frame của Java caller. Khi truyền qua JNI, native function nhận **`jstring` handle/reference**, không phải bản sao bắt buộc của Java object trên C stack.

JNI có hàm lấy dữ liệu chuỗi, nhưng việc tạo copy hay pin dữ liệu tùy API và JVM. Cần phân biệt **object nằm trên Java heap**, **handle mà native code giữ**, và **buffer cấp phát tạm**.

### 2.1. JVM frame không nhất thiết tương ứng một C frame

Một Java method có thể có `Code` attribute với `max_stack` và `max_locals`. C function tuân theo calling convention/ABI, và native compiler có thể dùng registers, machine stack, tối ưu tail/inlining.

- Java local slot 0 ở instance method là `this` theo JVM, không phải một offset C stack luôn cố định.
- Biến C `int result` có thể hoàn toàn nằm trong register.
- Java stack trace và native debugger backtrace không phải cùng một định dạng hoặc lúc nào cũng in cùng chuỗi frames.
- Đừng suy dung lượng native stack chỉ bằng cách đếm số dòng Java source.

## 3. Đi qua ranh giới lời gọi Java → native

Java khai báo:

```java
static native int sum(int left, int right);
```

Đây là **declaration**, không có method body Java. Trong class file, phương thức mang flag **`ACC_NATIVE`**, và không có `Code` attribute chứa Java bytecode để làm phép cộng. JVM liên kết lời gọi này đến một **native entrypoint** thông qua cơ chế tương tác phù hợp, ví dụ JNI.

```text
Java method (caller)
   │ invoke* tới native declaration
   │ arguments theo descriptor JVM
   ▼
JVM/JNI transition
   │ chọn native implementation và calling convention
   │ truyền JNIEnv*, receiver/class, arguments
   ▼
Native C function
   │ C function calls / native stack hoặc CPU registers
   │ có thể gọi JNI API hoặc callback vào Java
   ▼
Return JNI primitive/reference hoặc exception pending
   │
   ▼
JVM khôi phục ngữ nghĩa Java caller
   ├─ normal result → caller nhận giá trị
   └─ Java exception → tìm catch/unwind frames
```

Không có opcode bytecode tên `jni_call`. Những opcode như `invokestatic`, `invokevirtual` hoặc `invokespecial` có thể gọi phương thức khai báo `native`; việc thực thi mã native là trách nhiệm của JVM và native linkage, không có body bytecode Java để `javap -c` liệt kê.

### 3.1. `JNIEnv*` và đối số ngầm

Với C JNI entrypoint:

- `JNIEnv *env` là **interface pointer hợp lệ trên thread hiện tại**.
- Static native method nhận thêm `jclass clazz`.
- Instance native method nhận thêm `jobject receiver`.
- Các Java parameters được ánh xạ thành `jint`, `jlong`, `jdouble`, `jstring`, `jobject`, `jarray` và những JNI types thích hợp.

| Java declaration | C signature (phần cốt lõi) |
| --- | --- |
| `static native int add(int a, int b)` | `(JNIEnv*, jclass, jint, jint) → jint` |
| `native long scale(long n)` | `(JNIEnv*, jobject, jlong) → jlong` |
| `static native void log(String value)` | `(JNIEnv*, jclass, jstring) → void` |

Đây là cách ánh xạ **tại API boundary**, không phải lời hứa compiler C đẩy tất cả parameters vào native stack ở mọi hệ điều hành/CPU.

### 3.2. Primitive values và object references

Các primitive arguments đi qua JNI như **giá trị** theo kiểu tương ứng. Với reference, JNI dùng **opaque handles** thay vì cho phép native code đọc internal Java heap object layout bằng phép dereference tùy ý.

Một `jstring` không phải `char*`. Một `jobject` không phải con trỏ ổn định vào object được phép cast thành `struct*` của C. GC có thể di chuyển object, nên muốn đọc field, gọi method hoặc lấy data cần dùng JNI functions được hỗ trợ.

## 4. Ví dụ JNI đầy đủ: Java gọi C, C gọi ngược Java và ném lỗi

Ví dụ gồm hai file: **`NativeBoundaryDemo.java`** và **`boundarydemo.c`**. Phần Java biên dịch bằng JDK 17; phần C cần native compiler cùng architecture/ABI với JDK. Chương trình in stdout xác định khi biên dịch và tải thư viện thành công.

### 4.1. Java: `NativeBoundaryDemo.java`

```java
public class NativeBoundaryDemo {
    static {
        System.loadLibrary("boundarydemo");
    }

    private static native int add(int left, int right);

    private static native int twiceViaJava(int value);

    private static native void requireNonNegative(int value);

    private static int doubleValue(int value) {
        return value * 2;
    }

    public static void main(String[] args) {
        System.out.println("sum=" + add(5, 7));
        System.out.println("callback=" + twiceViaJava(9));

        try {
            requireNonNegative(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("caught=" + ex.getMessage());
        }

        System.out.println("done");
    }
}
```

`System.loadLibrary("boundarydemo")` dùng **tên logic**, không kèm `lib` prefix hay đuôi `.so`, `.dylib`, `.dll`. Ở ví dụ này class không khai báo package để C symbol dễ đọc.

### 4.2. Sinh header JNI và bytecode Java

Trong thư mục chứa Java source:

```bash
javac --release 17 -h . -d out NativeBoundaryDemo.java
```

`javac -h .` tạo file **`NativeBoundaryDemo.h`** với JNI function prototypes tương ứng; `-d out` đặt `NativeBoundaryDemo.class` vào thư mục `out`. Không sửa các prototype JNI bằng tay khi đã có header do compiler sinh.

### 4.3. C: `boundarydemo.c`

```c
#include <jni.h>

static jint add_in_c(jint a, jint b) {
    const jint result = a + b;
    return result;
}

JNIEXPORT jint JNICALL
Java_NativeBoundaryDemo_add(JNIEnv *env, jclass clazz,
                            jint left, jint right) {
    (void)env;
    (void)clazz;
    return add_in_c(left, right);
}

JNIEXPORT jint JNICALL
Java_NativeBoundaryDemo_twiceViaJava(JNIEnv *env, jclass clazz,
                                     jint value) {
    jmethodID method =
        (*env)->GetStaticMethodID(env, clazz, "doubleValue", "(I)I");

    if (method == NULL) {
        return 0; /* Có thể tồn tại JNI exception pending. */
    }

    return (*env)->CallStaticIntMethod(env, clazz, method, value);
}

JNIEXPORT void JNICALL
Java_NativeBoundaryDemo_requireNonNegative(JNIEnv *env, jclass clazz,
                                           jint value) {
    (void)clazz;

    if (value >= 0) {
        return;
    }

    jclass type =
        (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (type == NULL) {
        return; /* FindClass có thể đã báo exception pending. */
    }

    (*env)->ThrowNew(env, type, "negative value");
    (*env)->DeleteLocalRef(env, type);
}
```

Cách đọc:

- `JNIEXPORT` và `JNICALL` theo `jni.h` hỗ trợ visibility/calling convention.
- `add_in_c` mô tả một C helper call. Native compiler có thể tạo stack frame, inline hoặc giữ giá trị trong registers; đây là chi tiết tối ưu hóa.
- `GetStaticMethodID` lấy handle cho method `doubleValue` với descriptor **`(I)I`**.
- `CallStaticIntMethod` đi từ native sang Java rồi quay lại native cùng thread.
- Nếu `GetStaticMethodID` thất bại, code trả về với pending exception; `0` không đồng nghĩa JVM bỏ qua exception để Java caller nhận số 0.
- `ThrowNew` thiết lập Java exception pending. `DeleteLocalRef` giải phóng local reference cho `type` và là thao tác được phép trong điều kiện pending exception.
- C library **không** dùng C `throw` hay truyền `errno` sang Java tự động; muốn Java catch một lỗi nghiệp vụ, code native chủ động giao tiếp qua JNI.

### 4.4. Biên dịch thư viện native

**Linux với GCC**, khi `JAVA_HOME` trỏ tới JDK đầy đủ:

```bash
gcc -fPIC -shared -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" -o libboundarydemo.so boundarydemo.c
```

**macOS với Clang**:

```bash
cc -dynamiclib -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -o libboundarydemo.dylib boundarydemo.c
```

**Windows với Visual Studio Developer Command Prompt** (sử dụng dấu nháy cho biến có dấu cách):

```bat
cl /LD /I"%JAVA_HOME%/include" /I"%JAVA_HOME%/include/win32" boundarydemo.c /Fe:boundarydemo.dll
```

Mỗi lệnh chỉ dành cho **OS và native compiler đã nêu**; cần JDK headers tương ứng và cùng kiến trúc CPU (64-bit/arm64/x86_64) cho JVM và shared library.

### 4.5. Chạy và stdout

Ở thư mục chứa shared library và thư mục `out`:

```bash
java -Djava.library.path=. -cp out NativeBoundaryDemo
```

**Stdout khi biên dịch/liên kết thành công:**

```text
sum=12
callback=18
caught=negative value
done
```

Diễn tiến:

1. `main` gọi native `add(5,7)`. C gọi `add_in_c` và trả `jint 12`; caller Java in `sum=12`.
2. `main` gọi native `twiceViaJava(9)`. C gọi `CallStaticIntMethod` vào Java `doubleValue(9)`, nhận `18`, trả lại caller Java và in `callback=18`.
3. `main` gọi `requireNonNegative(-1)`. C dùng `ThrowNew`; khi JNI method quay về, JVM điều khiển theo đường exception và `main` bắt `IllegalArgumentException` rồi in message.
4. Sau handler Java tiếp tục in `done`.

**Ranh giới thực sự:** một lời gọi Java→native→Java callback có thể tạo chuỗi Java/native execution context đan xen. Không tự kết luận có một native OS thread mới hay một physical stack thứ hai được tạo cho mỗi transition.

## 5. JNI local references, global references và GC

JNI phân biệt **lifetime của reference** với lifetime của biến C chứa nó.

| JNI reference | Lifetime/ownership | Cách quản lý |
| --- | --- | --- |
| **Local** | Có hiệu lực trong JNI local frame/native invocation tương ứng và chỉ thread thích hợp | Thường được giải phóng sau native return; có thể `DeleteLocalRef` sớm |
| **Global** | Giữ được qua nhiều native calls tới khi xóa | `NewGlobalRef` và `DeleteGlobalRef` |
| **Weak global** | Không giữ object sống mạnh như global ref; có thể bị GC clear | `NewWeakGlobalRef`, kiểm tra hợp lệ khi dùng và `DeleteWeakGlobalRef` |
| **Native pointer từ `malloc`** | Theo native allocator/ownership | Phải `free` hoặc release theo library contract |

Ví dụ **sai**, vì C static storage sống lâu hơn một JNI local reference:

```c
/* Minh họa anti-pattern, không phải chương trình độc lập */
static jobject saved;

JNIEXPORT void JNICALL
Java_Example_bad(JNIEnv *env, jclass clazz, jobject value) {
    (void)env;
    (void)clazz;
    saved = value; /* Local JNI reference mất hiệu lực sau native call */
}
```

Nếu thực sự phải giữ object nhiều lời gọi, native code cần **`NewGlobalRef(env, value)`**, kiểm tra lỗi và cuối vòng đời cần **`DeleteGlobalRef`**. Một JNI global reference còn tồn tại có thể giữ Java object reachable trên heap, dẫn tới heap pressure nếu bị rò.

### 5.1. Một `jobject` không phải con trỏ thô

JNI handles được JVM theo dõi. GC có thể di chuyển object phù hợp implementation; không cast `jobject` trực tiếp thành `void*` rồi đọc member layout như C struct.

Ngay cả khi `jobject` được lưu trong C stack slot, **lifetime của handle do JNI quản lý**, không do việc C local biến còn trong scope hay không.

### 5.2. Strings/arrays và quy tắc release

Một snippet minh họa cách lấy dữ liệu `jstring` đúng API:

```c
/* Trong native function có JNIEnv *env, jstring text;
   chỉ là snippet, không phải function độc lập. */
const char *buffer = (*env)->GetStringUTFChars(env, text, NULL);
if (buffer == NULL) {
    return; /* Có thể đang pending OutOfMemoryError. */
}

/* Đọc buffer trong phạm vi hợp lệ */
(*env)->ReleaseStringUTFChars(env, text, buffer);
```

Cần ghi nhớ:

- `GetStringUTFChars` dùng **Modified UTF-8** của JNI, không phải mọi trường hợp giống UTF-8 chuẩn.
- Không dùng C `free(buffer)` cho memory mà JNI function yêu cầu **`ReleaseStringUTFChars`**.
- Các hàm `Get<PrimitiveType>ArrayElements` có hàm release tương ứng; có thể nhận copy hoặc vùng được giữ theo implementation.
- Dùng `GetPrimitiveArrayCritical` nên hạn chế nghiêm ngặt thao tác làm block giữa `Get` và `Release` theo JNI contract.
- Trong native loop tạo hàng nghìn JNI local refs, nên quản lý refs theo lượt hoặc dùng `PushLocalFrame`/`PopLocalFrame` thay vì tích lũy vô hạn.

## 6. `JNIEnv*` gắn với thread, không được chia sẻ tùy tiện

JNI `JNIEnv*` chỉ hợp lệ trên **thread hiện tại**. Không dùng một pointer `env` lấy ở thread A để gọi JNI từ thread B.

```text
Java thread A → native f() → JNIEnv* envA
Java thread B → native f() → JNIEnv* envB

envA KHÔNG được dùng trong C function thực thi ở thread B.
JNI local references cũng có thread/lifetime constraints riêng.
```

C code có C static/global variables chia sẻ được giữa threads trong một process. Nhưng điều đó không làm `JNIEnv*` hoặc JNI local refs trở thành thread-safe globals; phần data C chia sẻ cần synchronization đúng.

### 6.1. Khi native library tự tạo OS thread

Một thread do `pthread_create` hoặc Windows native API tạo **không tự động có quyền gọi JNI APIs của JVM**. Native thread muốn gọi Java phải:

1. Lấy `JavaVM*` thích hợp, chẳng hạn qua `GetJavaVM` trong JNI context hợp lệ.
2. Từ native thread mới, gọi **`AttachCurrentThread`** để nhận `JNIEnv*` của chính thread đó.
3. Sử dụng JNI theo những quy tắc local refs và exception state của thread.
4. Gọi **`DetachCurrentThread`** khi hoàn tất và trước lúc thread kết thúc, theo API contract.

Thread được attach phải có stack capacity phù hợp, được cấu hình theo OS thread API/ABI. `DetachCurrentThread` không được thực hiện trong một trạng thái còn Java frames trên stack trái API restriction.

**Một Java call vào native method không tự tạo thread mới.** Tạo thread là quyết định riêng của native library nếu nó có code làm việc đó.

## 7. Pending Java exception khác lỗi C làm sập process

JNI có `Throw` / `ThrowNew`, `ExceptionCheck` / `ExceptionOccurred` và `ExceptionClear`.

### 7.1. `ThrowNew` không tương đương C++ stack unwinding

Khi `ThrowNew` thành công, JNI đánh dấu **Java exception đang pending**. Native function **không tự nhảy vào Java `catch` ngay giữa execution của C**. Nó cần quay về JVM hoặc xử lý pending exception theo JNI restrictions; nhiều JNI functions không được gọi khi có exception pending.

```text
C: ThrowNew(...)
     │
     ▼
pending Java Throwable ở thread JNI
     │
     ▼
C method trả quyền thực thi về JVM
     │
     ▼
JVM tìm Java exception handler hoặc unwind Java frames
```

Trong ví dụ, Java `requireNonNegative(-1)` không hoàn tất bình thường; JVM chạy `catch` trong `main`.

### 7.2. C `SIGSEGV` khác Java `NullPointerException`

Nếu C dereference sai địa chỉ, use-after-free, buffer overflow hoặc trộn sai allocator, hậu quả có thể là:

- Unix signal như `SIGSEGV`.
- Windows access violation.
- Fatal error log `hs_err_pid...log` do HotSpot ghi nếu có thể.
- Crash toàn tiến trình hoặc memory corruption khó phát hiện.

**Java `try/catch(Exception)` không được bảo đảm cứu một process native crash.** Java bytecode verifier không có khả năng kiểm tra các phép truy cập con trỏ C tùy ý.

## 8. Native stack overflow, OOM và giới hạn Java heap

Các platform threads có thể tiêu tốn native address space và memory đáng kể vì stacks và thread management structures. Ứng dụng tạo quá nhiều threads có thể báo lỗi không cấp phát được native thread dù Java heap vẫn còn.

Công cụ JVM phổ biến:

```bash
java -Xss1m MyApp
```

`-Xss` cấu hình Java thread stack size trong các JVM hỗ trợ, nhưng **không bảo đảm native C stack của mọi thread — đặc biệt threads do thư viện C tự tạo — đúng 1 MiB**. OS thread stack layout, guard pages và stack reservation khác nhau theo implementation.

Đừng mặc định tăng `-Xmx` giải quyết native OOM. `-Xmx` dành cho Java heap; native stack resources, direct memory, JNI allocations và giới hạn process là những hạng mục cần đánh giá riêng.

### 8.1. Java recursion và C recursion

- Java recursion có thể làm JVM stack cạn và ném `StackOverflowError`.
- C recursion sâu có thể vượt native stack, dẫn tới crash hoặc lỗi khác tùy OS/JVM.
- Chuỗi JNI callbacks có thể xen kẽ Java/native code và cùng tạo áp lực lên execution stack resources.
- Không nên dùng bài thử “đệ quy vô hạn native” trong ứng dụng production hoặc IDE session chung để đo stack size; có nguy cơ giết toàn JVM.

## 9. Chẩn đoán native boundary thực tế

Các lệnh Java/HotSpot hữu ích:

```bash
java -version

# Xem Java thread dump, không giả định sẽ thấy mọi native C frame:
jcmd <pid> Thread.print

# Kiểm tra thêm một số hành vi JNI (HotSpot):
java -Xcheck:jni -Djava.library.path=. -cp out NativeBoundaryDemo

# Native Memory Tracking phải bật lúc JVM khởi động:
java -XX:NativeMemoryTracking=summary -Djava.library.path=. -cp out NativeBoundaryDemo

# Trong khi process có NMT vẫn đang chạy:
jcmd <pid> VM.native_memory summary
```

**Các giới hạn quan trọng:**

- `jcmd Thread.print` giúp xem Java frames/trạng thái threads, không thay thế GDB/LLDB/WinDbg để xem toàn bộ native C stack frames.
- `-Xcheck:jni` có thể phát hiện nhiều lỗi dùng JNI APIs nhưng **không chứng minh mọi mã C không có buffer overflow/data race**.
- HotSpot Native Memory Tracking (**NMT**) chỉ theo dõi những nguồn native memory thuộc phạm vi HotSpot tracking; **không hứa thấy mọi `malloc` từ bên trong mọi native library bên thứ ba**.
- `jcmd VM.native_memory` chỉ hoạt động đúng khi JVM đã bật NMT với chế độ phù hợp lúc khởi động.
- Khi JVM crash, đọc **`hs_err_pid...log`, core dump, native backtrace và debugger symbols** thay vì chỉ tìm Java exception stack trace.

### 9.1. `UnsatisfiedLinkError`

Thường xảy ra khi:

- Tên/đường dẫn shared library không đúng với `System.loadLibrary` hoặc `java.library.path`.
- `.dll` / `.so` / `.dylib` sai architecture/ABI so với JVM.
- JNI C symbol không khớp tên Java class, package, method hoặc overloaded signature.
- Thiếu các shared library dependencies khác.
- File library bị OS loader từ chối do policy/permission tương ứng.

`UnsatisfiedLinkError` thuộc nhóm `LinkageError`; nó không báo rằng Java heap thiếu memory. Khi đổi Java signature, phải biên dịch lại **cả generated header và C library** hoặc cập nhật `RegisterNatives` nếu dùng đăng ký động.

## 10. JNI, DirectByteBuffer và FFM API không đồng nghĩa

JNI là một cơ chế tương tác lâu đời để Java chạy code native và callback vào JVM. Java cũng có `ByteBuffer.allocateDirect` để sử dụng direct buffers, nhưng một direct buffer không phải là một “native method stack”; nó chủ yếu liên quan storage bên ngoài heap và các API quản lý buffer.

Trong Java hiện đại, **Foreign Function & Memory (FFM) API** cung cấp hướng tiếp cận khác tới native functions/memory. FFM trở thành tính năng chuẩn trong **Java 22**. Không nên lấy ví dụ FFM Java 22 rồi quảng bá như API chuẩn có thể dùng y hệt trên Java 17 mà không có khác biệt về trạng thái feature, module, flags và lifecycle.

Dù API nào, code native vẫn phải tôn trọng **ABI, memory ownership, stack/thread context, resource lifetime và quy tắc an toàn**. Không có cơ chế nào tự biến mọi raw C pointer accesses thành Java GC-safe operations.

## 11. Các lỗi hiểu nhầm thường gặp

1. **“Native method stack nằm trong Java heap.”** Sai: native stacks và Java heap có vai trò/runtime allocation khác nhau.
2. **“Mọi JVM có một native method stack vật lý độc lập tuyệt đối.”** Sai: JVMS cho phép nhiều cách triển khai.
3. **“Mỗi native call tạo một thread mới.”** Sai: bình thường Java thread hiện tại đi qua boundary.
4. **“Native method vẫn có `Code` chứa body Java bytecode.”** Sai: `ACC_NATIVE` method không có body `Code` như method Java bình thường.
5. **“`jobject` là C pointer có thể dereference vào Java object layout.”** Sai: đó là JNI reference theo contract VM.
6. **“`JNIEnv*` dùng chung giữa các threads.”** Sai: interface pointer thuộc thread hiện tại.
7. **“C global chứa `jobject` thì reference tự có lifetime global.”** Sai: phải dùng `NewGlobalRef` để kéo dài JNI reference hợp lệ.
8. **“Native method stack là toàn bộ native/off-heap memory.”** Sai: off-heap còn nhiều vùng như `malloc` và direct buffers.
9. **“GC sẽ tự `free` mọi C allocation.”** Sai: `malloc` cần `free`/library-specific release.
10. **“`jstring` luôn trỏ vào C UTF-8 null-terminated.”** Sai: dùng JNI string APIs và hiểu Modified UTF-8.
11. **“`ThrowNew` ngay lập tức thoát khỏi C function bằng C++ exception.”** Sai: pending Java exception là cơ chế riêng.
12. **“Java catch bắt được mọi `SIGSEGV` của JNI.”** Sai: C native crash có thể kết thúc JVM process.
13. **“`-Xmx` càng lớn càng sửa được native stack OOM.”** Sai: heap và native resources có giới hạn riêng.
14. **“`-Xss` buộc stack của mọi C thread có cùng size trên mọi OS.”** Không có cam kết xuyên platform.
15. **“`Thread.print` chứa toàn bộ C frames.”** Sai: Java thread dump không thay native debugger.
16. **“`-Xcheck:jni` phát hiện hết memory corruption.”** Sai: chỉ có phạm vi JNI checks nhất định.
17. **“JNI local references còn sống vĩnh viễn nếu C không gọi `DeleteLocalRef`.”** Sai: thường hết hiệu lực sau native return.
18. **“Truyền `String` qua JNI tức copy toàn bộ object lên native stack.”** Sai: thông thường truyền JNI handle; data access dùng API khác.
19. **“Native method chỉ có thể trả số, không gọi ngược Java.”** Sai: JNI `Call...Method` hỗ trợ callback theo contract.
20. **“Trên Java 17 FFM đã là Java standard API tương đương Java 22.”** Sai: cần phân biệt trạng thái API theo release.

## 12. Tự kiểm tra và đáp án

**Câu 1.** Native method stack có phải tồn tại như một bộ nhớ riêng trên mọi JVM?

**Đáp án:** Không. JVMS cho phép implementation dùng conventional stacks để hỗ trợ native methods và không bắt buộc cấu trúc vật lý riêng.

**Câu 2.** Với `static native int add(int,int)`, C JNI function nhận gì trước hai `jint`?

**Đáp án:** `JNIEnv *env` và `jclass clazz`. Instance native method nhận `jobject receiver` thay cho `jclass`.

**Câu 3.** Trong ví dụ, `twiceViaJava(9)` có quay lại mã Java từ C không?

**Đáp án:** Có. JNI `CallStaticIntMethod` gọi `doubleValue(9)`, trả `18` rồi C trả `18` về Java caller.

**Câu 4.** `ThrowNew` trong C hoạt động như `areturn` không?

**Đáp án:** Không. Nó báo pending Java exception, dẫn tới đường exceptional khi quyền điều khiển quay về JVM.

**Câu 5.** Tại sao lưu local `jobject` vào C static variable không an toàn khi dùng sau native method return?

**Đáp án:** JNI local reference chỉ hợp lệ trong thời gian sống local-reference frame/thread phù hợp, không tự kéo dài theo storage duration của C variable.

**Câu 6.** Nếu muốn native thread tự tạo gọi Java, cần xử lý gì?

**Đáp án:** Dùng `JavaVM*` và `AttachCurrentThread` để nhận `JNIEnv*` của chính thread đó; dùng đúng APIs và `DetachCurrentThread` khi hoàn tất theo hợp đồng.

**Câu 7.** JNI library `malloc` 2 GiB rồi quên `free`. Java heap `-Xmx` thấp có tự quyết định giải phóng 2 GiB đó không?

**Đáp án:** Không. Native memory ownership là độc lập; JVM GC không tự `free` arbitrary C allocations.

**Câu 8.** C truy cập pointer đã được free và gây `SIGSEGV`. Java `try/catch(RuntimeException)` có cứu được chắc chắn không?

**Đáp án:** Không. Native crash có thể làm toàn bộ process kết thúc; cần native diagnostics và sửa C memory safety.

**Câu 9.** Tại sao `jcmd Thread.print` và GDB native backtrace có thể khác nhau?

**Đáp án:** Một bên tập trung Java thread/logical frames, bên kia dựa trên native machine call stack, ABI và debug symbols.

**Câu 10.** `-Xcheck:jni` bảo đảm ứng dụng native an toàn hoàn toàn không?

**Đáp án:** Không; đây là hỗ trợ phát hiện một số JNI API violations, không phải memory-safety verifier tổng quát cho C/C++.

**Câu 11.** `jstring` có thể đưa thẳng cho C `strlen` bằng cast không?

**Đáp án:** Không. Phải dùng JNI string API phù hợp và release; `GetStringUTFChars` dùng Modified UTF-8.

**Câu 12.** Thêm `-Xmx` có giải quyết triệt để lỗi không tạo được native thread không?

**Đáp án:** Không. Cần kiểm tra native address space, OS/process thread limits, native stacks và memory allocations thực tế.

## 13. Ghi nhớ bằng sơ đồ

```text
Java platform thread
   │
   ├─ Java method frames (JVM stack model)
   │       └─ invoke native declaration
   │
   ▼
JVM / JNI transition
   ├─ JNI interface gắn với thread: JNIEnv*
   ├─ primitive arguments và opaque JNI object handles
   └─ native function link / platform ABI
   │
   ▼
C/native function execution
   ├─ C stack/registers (tùy implementation/compiler)
   ├─ C malloc/free cho native heap
   ├─ JNI refs tới Java heap objects
   ├─ có thể callback Java qua JNI
   └─ return value hoặc Java exception pending
   │
   ▼
JVM trả quyền điều khiển về Java caller
   ├─ normal typed result
   └─ exception handler/unwinding

GC quản lý Java heap; JNI quản lý contract references;
native allocator và OS quản lý phần việc riêng của chúng.
```

**Chốt kiến thức:** Native method stack hỗ trợ việc chạy native/VM code khi implementation dùng conventional stacks. Nó không phải nơi chứa toàn bộ Java objects, cũng không phải synonym của mọi vùng off-heap. Qua ranh giới Java ↔ native, JVM/JNI quản lý chuyển đối số, JNI handle lifetime, trạng thái exception và thread identity. Mã native có thể tương tác với JVM nhưng vẫn chịu những rủi ro memory-safety/ABI vốn không tồn tại trong bytecode Java được verify. Khi chẩn đoán, kết hợp Java thread dump, JNI checks, NMT đúng giới hạn và native debugger.

### Tài liệu tham khảo

- [JVMS Java SE 17, §2.5.6 — Native Method Stacks](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.6).
- [JVMS Java SE 17, `2.5.2 / `2.6 — JVM Stacks và Frames](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6).
- [JVMS Java SE 17, §4 — ACC_NATIVE và Code attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html).
- [Java SE 17 JNI Specification — Design Overview](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/design.html).
- [Java SE 17 JNI Specification — JNI Functions](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html).
- [Java SE 17 JNI Specification — Invocation API](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/invocation.html).
- [JDK 17 javac Documentation, `-h`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html).
- [JDK 17 Troubleshooting, `-Xcheck:jni`](https://docs.oracle.com/en/java/javase/17/troubleshoot/command-line-options1.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-006">HotSpot Metaspace, compressed class space và sự khác biệt với PermGen lịch sử</a>

<details>
<summary>Xem chi tiết</summary>

# HotSpot Metaspace, compressed class space và sự khác biệt với PermGen lịch sử

## 1. Mục tiêu và ranh giới của bài

Trong một ứng dụng Java chạy trên **HotSpot JVM**, bộ nhớ không chỉ có **Java heap** để chứa các đối tượng ứng dụng. JVM còn dùng những vùng nhớ khác cho stack của thread, JIT code cache, cấu trúc native và đặc biệt là **metadata của những class/interface đã nạp**.

Từ **JDK 8**, HotSpot loại bỏ **Permanent Generation (PermGen)** vốn là cách triển khai cũ của vùng chứa class metadata và thay bằng **Metaspace**, sử dụng bộ nhớ native thay vì PermGen được GC quản lý theo cách lịch sử. Trên các HotSpot 64-bit có **compressed class pointers**, một phần metadata liên quan cấu trúc class được đặt trong **compressed class space**, tách về mặt logic với vùng metadata còn lại.

Ba khái niệm phải phân biệt:

1. **PermGen:** thế hệ cũ, thuộc triển khai HotSpot trước JDK 8, không phải thành phần bắt buộc trong Java Virtual Machine Specification (JVMS).
2. **Metaspace:** cơ chế cấp phát và quản lý bộ nhớ cho class metadata trong HotSpot từ JDK 8, sử dụng **native memory**.
3. **Compressed class space:** khu vực địa chỉ dành cho phần metadata class có thể truy cập bằng **compressed class pointers**, chỉ có ý nghĩa ở các cấu hình HotSpot hỗ trợ và bật tính năng này.

Học xong, bạn cần trả lời được:

- Metaspace chứa gì, khác Java heap và method area khái niệm ở đâu?
- Vì sao `-Xmx` không trực tiếp giới hạn Metaspace?
- Compressed class pointers liên quan đến `CompressedClassSpaceSize` như thế nào?
- Số **reserved** và **committed** khác nhau ra sao?
- Khi nào class metadata được giải phóng và vì sao class loader leak có thể gây `OutOfMemoryError: Metaspace`?
- Làm thế nào kiểm tra bằng `jcmd`, Native Memory Tracking, logs và các cờ của HotSpot?
- Cần điều chỉnh flag hay sửa vòng đời class loader khi sử dụng Metaspace tăng liên tục?

**Phạm vi:** đây là bài về **HotSpot implementation** và lịch sử triển khai, không phải lời khẳng định mọi JVM của mọi nhà cung cấp bắt buộc có vùng tên Metaspace hoặc compressed class space.

## 2. Method area là khái niệm của JVM; Metaspace là triển khai HotSpot

### 2.1. JVMS nói gì về method area?

**Method area** là vùng dữ liệu runtime mà **các JVM threads chia sẻ**, lưu cấu trúc theo lớp như thông tin class, runtime constant pool, mã phương thức và trường, cũng như những dữ liệu phục vụ JVM theo mô hình đặc tả. JVMS mô tả *trách nhiệm/ngữ nghĩa* của method area, nhưng **không yêu cầu một cách quản lý bộ nhớ vật lý duy nhất**.

Cách triển khai có thể thay đổi theo bản JVM, phiên bản, kiến trúc, GC và các tối ưu. Vì vậy:

> **Không nên viết “method area = Metaspace” như một đẳng thức áp dụng cho mọi JVM.**

Cách diễn đạt chính xác hơn: **HotSpot dùng cơ chế Metaspace để cấp phát nhiều cấu trúc native class metadata phục vụ các chức năng của method area trong JVM**.

### 2.2. Bản đồ khái niệm

```text
                      Tiến trình HotSpot JVM
┌─────────────────────────────────────────────────────┐
│ Java heap (quản lý bởi Java GC)                    │
│  - instance Java, array object                     │
│  - java.lang.Class mirror (đối tượng Java)         │
│  - String, collection và object ứng dụng           │
├─────────────────────────────────────────────────────┤
│ Native / non-heap memory của VM                     │
│  - Metaspace: class metadata, non-class metadata   │
│  - Compressed class space (khi bật)                 │
│  - Code cache, thread/native stacks                │
│  - Cấu trúc VM/native khác                           │
└─────────────────────────────────────────────────────┘
```

Sơ đồ **không mô tả chính xác layout các địa chỉ bộ nhớ**. Nó chỉ phân biệt trách nhiệm các loại bộ nhớ.

Điều rất dễ nhầm:

- `java.lang.Class` là một **Java object mirror**, không phải bản thân toàn bộ dữ liệu native mô tả class.
- Những **instance** được tạo bằng `new MyClass()` thuộc **Java heap**, không di chuyển vào Metaspace chỉ vì đó là instance của một class đã load.
- Những **metadata structures** của HotSpot, như cấu trúc đại diện thông tin class, có thể nằm trong Metaspace.
- Không thể kết luận **mọi static field value** đều nằm trong Metaspace. Đặc biệt, object mà static reference trỏ tới vẫn là Java object trong heap; chi tiết bố trí static fields và mirrors thuộc triển khai runtime.

### 2.3. “Non-heap” trong MXBean không đồng nghĩa một vùng nhớ duy nhất

Java Management API có `MemoryType.NON_HEAP` và `MemoryPoolMXBean`. Các công cụ có thể báo những pool như **Metaspace**, **Compressed Class Space** và **CodeHeap/code cache**, tùy HotSpot/JDK/cấu hình.

Nhưng **tổng native memory của tiến trình** không bằng đúng “Metaspace + code cache” hoặc chỉ bằng `MemoryMXBean.getNonHeapMemoryUsage()`. Thread stacks, GC native structures, symbol tables, allocator overhead, thư viện native và vùng khác có thể làm bộ nhớ resident của process tăng.

Do đó, khi hệ điều hành báo RSS tăng, **không quy ngay toàn bộ phần tăng cho Metaspace**.

## 3. PermGen trước JDK 8 là gì?

### 3.1. Permanent Generation là một cấu trúc HotSpot cũ

Trong các HotSpot phiên bản trước JDK 8, metadata của class được quản lý trong một vùng tên **Permanent Generation**, thường gọi là **PermGen**. Nó là một thế hệ/vùng bộ nhớ trong cách tổ chức GC và VM thời kỳ đó, khác với thế hệ chứa những Java application objects thông thường.

Một số cờ lịch sử:

```text
-XX:PermSize=...
-XX:MaxPermSize=...
```

Những cờ này dùng để điều chỉnh PermGen ở các bản HotSpot cũ phù hợp. Khi giới hạn bị vượt trong bối cảnh không giải phóng đủ, có thể thấy lỗi:

```text
java.lang.OutOfMemoryError: PermGen space
```

**Không dùng `PermSize`/`MaxPermSize` để cấu hình HotSpot JDK 17+.** PermGen không còn là cơ chế lưu class metadata của HotSpot từ JDK 8.

### 3.2. Sự chuyển đổi không phải “toàn bộ PermGen chuyển y nguyên vào Metaspace”

Diễn giải “JDK 8 chỉ đổi tên PermGen thành Metaspace” là sai.

Về thực chất:

- HotSpot **loại bỏ Permanent Generation**.
- Class metadata được chuyển sang **vùng quản lý metadata trong native memory**.
- Cơ chế allocator, điều chỉnh ngưỡng, accounting và giải phóng bộ nhớ thay đổi.
- Các thành phần từng được gộp chung theo cách mô tả cũ **không tất cả nằm nguyên xi trong Metaspace**.

Ví dụ quan trọng: **interned strings** đã được chuyển khỏi PermGen vào Java heap từ một số bản HotSpot trước JDK 8, đặc biệt từ JDK 7. Không đúng nếu nói “JDK 8 chuyển toàn bộ string pool từ PermGen sang Metaspace”. Các đối tượng `String` Java thuộc heap; String table và các cấu trúc tra cứu lại có chi tiết native riêng.

### 3.3. Mốc lịch sử

| Giai đoạn | Cơ chế quản lý metadata trong HotSpot | Lưu ý |
| --- | --- | --- |
| JDK 6/7 và trước JDK 8 | **PermGen**, với giới hạn/flag phù hợp bản JVM | Class metadata liên quan Permanent Generation |
| **JDK 8** | **PermGen bị loại bỏ; dùng Metaspace native** | Nội dung chính của OpenJDK **JEP 122** |
| JDK 9–15 | Metaspace tiếp tục phát triển | Hành vi chi tiết tùy phiên bản và nền tảng |
| **JDK 16 trở lên** | **Elastic Metaspace** từ **JEP 387** | Cải thiện tái sử dụng và trả memory đã commit về OS khi unloading |
| JDK 17/21/25+ | Metaspace/Compressed class space theo HotSpot hiện đại | Cờ, ergonomics và cách accounting có thể khác giữa JDK |

**“Elastic Metaspace” không có nghĩa Metaspace không bao giờ OOME**; nó cải thiện khả năng quản lý/reclaim nhưng không loại bỏ giới hạn metadata hay lỗi giữ class loader.

## 4. Metaspace lưu gì và được cấp phát theo cơ chế nào?

### 4.1. Class metadata gồm những loại thông tin nào?

HotSpot cần dữ liệu native để hiểu và thực thi class đã load, chẳng hạn:

- Thông tin mô tả một lớp/giao diện, class hierarchy và các method/field descriptors.
- Metadata phục vụ runtime method/constant-pool access, liên kết và dispatch.
- Những cấu trúc liên quan method và class mà HotSpot duy trì bên ngoài Java object heap.
- Thông tin liên quan class-loader data và các allocator dành cho metadata.

Không nên mô tả đơn giản “Metaspace chứa toàn bộ bytecode, mọi object `Class`, static object và JIT compiled code”. **JIT compiled machine code** chủ yếu được quản lý trong **code cache** chứ không phải Metaspace; các Java object mirrors tồn tại trên heap.

### 4.2. Gắn với class loader và các chunks

Ở mức khái niệm, HotSpot cấp phát metadata theo những cấu trúc gắn với **class loaders/class-loader data**, sử dụng các **chunks/arenas** được quản lý bởi metaspace allocator:

```text
ClassLoader A
   |
   +--> metadata allocations / arena A
             ├── class metadata A1
             ├── method metadata A1
             └── class metadata A2

ClassLoader B
   |
   +--> metadata allocations / arena B
             ├── class metadata B1
             └── ...
```

Sơ đồ là **mô hình quản lý**, không phải một heap dump chính xác. Mục đích là nhấn mạnh class metadata có vòng đời liên quan tới **class loader/data**. Khi class loader và các class của nó có thể unload, HotSpot có cơ hội tái sử dụng hoặc trả lại chunks/phần bộ nhớ.

### 4.3. Một class name không quyết định duy nhất metadata

Trong JVM, một nonarray runtime class được định danh bởi **binary name và defining class loader**. Hai loader khác nhau có thể định nghĩa hai class mang cùng tên, dẫn đến hai runtime classes và **hai tập metadata riêng**.

Nếu một ứng dụng liên tục tạo custom loaders và định nghĩa cùng một class trong mỗi loader nhưng giữ tất cả loaders sống, Metaspace có thể tiếp tục tăng dù file `.class` trên đĩa không đổi.

Đây là một nguồn phổ biến của **class-loader retention** và Metaspace pressure trong plugin systems, application servers, test runners hoặc bytecode generation.

### 4.4. Metadata không giải phóng ngay chỉ vì một biến bằng null

```java
Object instance = new SomeType();
instance = null;
```

Đây là **đoạn mã minh họa**, giả định `SomeType` tồn tại. Xóa một reference tới instance **không đồng nghĩa unload class SomeType**. Class có thể còn được sử dụng bởi nhiều object, threads, static references, loaders, reflection caches, methods đang chạy và các cấu trúc VM.

Để class metadata được thu hồi, các điều kiện về **class unloading** phải thỏa; quá trình này gắn với GC và reachability thực tế. Không có API Java chuẩn “free this class metadata immediately”.

## 5. Compressed class pointers và compressed class space

### 5.1. “Compressed class pointers” là gì?

Trên một số cấu hình **HotSpot 64-bit**, VM có thể dùng biểu diễn con trỏ tham chiếu tới **class metadata** dưới dạng compressed encoding. Cờ thường gặp là:

```text
-XX:+UseCompressedClassPointers
```

Điều này **khác** với **compressed ordinary object pointers (compressed oops)**, thường điều chỉnh bằng:

```text
-XX:+UseCompressedOops
```

Hai loại pointer cần phân biệt:

| Cơ chế | Mục đích | Ví dụ tham chiếu |
| --- | --- | --- |
| **Compressed oops** | Nén biểu diễn reference từ Java object này tới Java object khác | Một field chứa reference tới `String` |
| **Compressed class pointers** | Nén cách object header/runtime cấu trúc tham chiếu tới metadata class | Con trỏ class metadata tương ứng kiểu runtime của object |

Dù có mối liên hệ trong cấu hình HotSpot, đây **không phải cùng một loại dữ liệu**. Không nên nói “bật compressed oops là mọi cấu trúc metadata tự động thu nhỏ theo một tỷ lệ cố định”.

### 5.2. Compressed class space nằm ở đâu?

Khi HotSpot bật compressed class pointers, VM sử dụng vùng riêng, gọi là **compressed class space**, để giữ những cấu trúc metadata class cần nằm trong vùng có thể được biểu diễn bởi cơ chế compressed class pointers.

```text
Native memory dành cho class metadata (mô hình)
┌───────────────────────────────────────────┐
│ Non-class metadata / Metaspace allocator   │
│  - nhiều metadata không thuộc class-space  │
├───────────────────────────────────────────┤
│ Compressed class space (khi bật)           │
│  - metadata cần compressed class pointers │
│  - reserved address range                 │
│  - committed dần theo nhu cầu             │
└───────────────────────────────────────────┘
```

Tên **“class space”** không có nghĩa vùng đó chứa các object Java `Class` trên heap. Nó chứa metadata native được HotSpot quản lý.

**Đừng đồng nhất compressed class space với toàn bộ Metaspace.** Đó là một **không gian metadata riêng về mặt logic** trong cấu hình có nén class pointers.

### 5.3. `-XX:CompressedClassSpaceSize`

Cờ:

```text
-XX:CompressedClassSpaceSize=128m
```

điều chỉnh **kích thước không gian địa chỉ class space** được dành cho biểu diễn compressed class pointers, tùy giới hạn và điều chỉnh của VM. Trên nhiều HotSpot 64-bit truyền thống, mặc định gần **1 GiB**, nhưng **không phải giá trị bất biến cho mọi JVM, kiến trúc hoặc JDK**. VM có thể điều chỉnh kích thước thực khi flag khác giới hạn hoặc cấu hình không hỗ trợ.

Quan trọng:

- **Reserved 1 GiB không có nghĩa process đã chiếm 1 GiB RAM vật lý** cho class metadata.
- Class space **commit** những trang cần sử dụng theo nhu cầu.
- Khi class space được commit đủ giới hạn khả dụng và không thể giải phóng/đáp ứng thêm, có thể xuất hiện `OutOfMemoryError: Compressed class space`.
- Cờ này không trực tiếp giới hạn số instance Java được tạo trên heap.

### 5.4. Quan hệ với `-XX:MaxMetaspaceSize`

Trong cấu hình HotSpot có class space, `MaxMetaspaceSize` được áp dụng cho **tổng mức committed metadata** cần quản lý, bao gồm metadata non-class và phần class space đã commit, theo cơ chế/giới hạn thực tế của runtime.

Không suy luận phép cộng đơn giản:

```text
MaxMetaspaceSize = ReservedCompressedClassSpaceSize + MaxNonClassMetaspace
```

Đây là **công thức sai nếu dùng như đẳng thức bắt buộc**. `CompressedClassSpaceSize` thiên về một **reserved address range/capacity** của class-space, còn `MaxMetaspaceSize` là **trần metadata committed** theo HotSpot, và VM có thể điều chỉnh giới hạn nội bộ để bảo đảm phù hợp.

Ví dụ, bạn có thể nhìn thấy:

```text
class space reserved:  ~1 GiB
class space committed: 20 MiB
non-class committed:   80 MiB
```

Đây chỉ là **con số minh họa**. Tổng metadata committed là khoảng 100 MiB, không phải khoảng 1.1 GiB. Muốn kết luận bộ nhớ thực tế, cần xem **committed và used**, không chỉ reserved.

## 6. Reserved, committed, used và capacity khác nhau ra sao?

Dữ liệu Metaspace rất dễ bị đọc sai, đặc biệt khi giám sát container memory.

| Khái niệm | Ý nghĩa khái quát | Điều không nên suy luận |
| --- | --- | --- |
| **Reserved** | Không gian địa chỉ ảo được đặt trước cho vùng dữ liệu | Không đồng nghĩa đã sử dụng lượng RAM vật lý tương ứng |
| **Committed** | Phần bộ nhớ VM/OS đã commit để có thể dùng theo cơ chế nền tảng | Không nhất thiết là số byte metadata thực sự đã sử dụng |
| **Used** | Bộ nhớ metadata đang được sử dụng để giữ dữ liệu theo cách accounting của VM | Không đồng nghĩa tổng native memory của process |
| **Capacity** | Năng lực cấp phát hiện có trong các chunks/arenas theo cách thống kê tương ứng | Không phải một ngưỡng cấu hình giống nhau cho mọi phiên bản JVM |

Ví dụ hư cấu:

```text
Metaspace:
  used       80 MiB
  capacity   90 MiB
  committed  96 MiB
  reserved   1200 MiB
```

Trong mô hình đó:

- Có 80 MiB metadata thực tế đang được dùng.
- VM đã commit 96 MiB cho các metadata areas tương ứng.
- Phần reserved 1200 MiB là virtual address reservation, **không nên cộng nguyên vào RAM thực đang dùng**.
- Sự khác biệt giữa used/capacity/committed có thể liên quan allocator chunks, internal fragmentation và dư địa cấp phát.

**Các field/định nghĩa cụ thể phụ thuộc output của phiên bản HotSpot.** Khi debug, đọc phần chú giải của công cụ và version, không tự lấy một định nghĩa báo cáo cũ áp sang output mới.

### 6.1. Tại sao “Metaspace reserved” lớn vẫn có thể bình thường?

Một compressed class space có thể **reserve** vùng địa chỉ ảo tương đối lớn để hỗ trợ compressed pointers nhưng chỉ **commit** một phần nhỏ. Công cụ memory map của OS có thể hiển thị một reservation lớn, trong khi memory resident thực tế nhỏ hơn nhiều.

Nếu thấy “1 GiB reserved class space” rồi kết luận “JVM đang ngốn 1 GiB heap”, bạn đã nhầm **địa chỉ ảo**, **native metadata** và **Java heap**.

### 6.2. Committed tăng nhưng used không tăng tương ứng?

Có thể liên quan metadata allocator giữ lại chunks phục vụ tái sử dụng, fragmentation hoặc chiến lược reclamation. Trên HotSpot hiện đại sau JEP 387, VM cải thiện khả năng trả committed memory về OS khi class unload, nhưng không có bảo đảm mọi chênh lệch phải về 0 ngay.

Hãy đo theo thời gian, đọc các số **used/committed/reserved** cùng với class-loading statistics, GC và các chỉ số native memory khác.

## 7. Các cờ cấu hình HotSpot thường gặp

| Cờ | Vai trò | Lỗi hiểu nhầm phổ biến |
| --- | --- | --- |
| `-Xmx512m` | Giới hạn tối đa **Java heap** | Không phải trần tổng memory process hoặc Metaspace |
| `-XX:MetaspaceSize=128m` | Ngưỡng khởi đầu liên quan metadata occupancy để thúc đẩy GC/class unloading; có thể thay đổi | **Không** phải “khởi tạo Metaspace đúng 128 MiB” hay hard limit |
| `-XX:MaxMetaspaceSize=256m` | Giới hạn trên đối với metadata memory theo HotSpot | Không phải tổng resident process cap |
| `-XX:CompressedClassSpaceSize=128m` | Điều chỉnh class-space reservation/khả năng sử dụng khi bật compressed class pointers | Không phải lượng committed ngay lúc khởi động |
| `-XX:+UseCompressedClassPointers` | Cho phép compressed class pointers khi môi trường hỗ trợ | Không phải nén Java object references một cách tổng quát |
| `-XX:-UseCompressedClassPointers` | Vô hiệu class-pointer compression khi VM hỗ trợ | Không có nghĩa mọi class metadata biến mất |
| `-XX:+UseCompressedOops` | Dùng compressed ordinary object references khi môi trường hỗ trợ | Không đồng nhất compressed oops với class-space |
| `-XX:MetaspaceReclaimPolicy=balanced` | Chính sách reclamation trên HotSpot từ JEP 387 | Không giải quyết class-loader leak đang còn reachable |
| `-XX:NativeMemoryTracking=summary` | Thu thập số liệu native memory phục vụ `jcmd VM.native_memory` | Tăng chi phí tracking; cần bật đúng thời điểm JVM khởi động |

Các cờ trên là **HotSpot-specific**, không phải option bắt buộc của mọi JVM. Phiên bản, kiến trúc, GC và ergonomics có thể thay đổi giá trị mặc định, tương tác flag hoặc khả năng hỗ trợ.

### 7.1. Ví dụ đặt ngưỡng, không coi là cấu hình chuẩn cho production

```bash
java -Xmx512m -XX:MaxMetaspaceSize=256m -XX:CompressedClassSpaceSize=128m MyApp
```

Đây là **lệnh minh họa** khi `MyApp` đã được build thành công và nằm trên classpath. Nó không phải bài tập Java độc lập; không nên copy vào production mà không đo class-loading behavior, container memory limit và workload.

Tác dụng:

- `-Xmx512m` giới hạn Java heap khoảng mức cấu hình 512 MiB (chi tiết JVM).
- `-XX:MaxMetaspaceSize=256m` đặt trần metadata.
- `-XX:CompressedClassSpaceSize=128m` điều chỉnh vùng compressed class space khi khả dụng.

**Không suy luận tổng RAM tối đa của tiến trình là `512 + 256 + 128 = 896 MiB`**. Vì class space đã liên quan giới hạn metadata, còn thread stacks, code cache, GC native memory và vùng khác cũng chiếm tài nguyên ngoài heap. Tính tổng bằng cách cộng các flag như trên là sai.

### 7.2. MetaspaceSize khác MaxMetaspaceSize

Tên dễ làm người học lẫn:

- `MetaspaceSize` ảnh hưởng **ngưỡng liên quan GC** cho metadata, có thể được runtime điều chỉnh.
- `MaxMetaspaceSize` là **giới hạn trần** cho metadata allocation/commit mà VM thực thi theo chính sách của nó.

Tăng `MetaspaceSize` có thể giảm những lần GC sớm do vượt ngưỡng metadata trong một số workload, nhưng **không tự sửa việc giữ class loader**. Tăng `MaxMetaspaceSize` có thể kéo dài thời gian trước khi OOME nếu metadata không còn giải phóng được; **không tự giải quyết root cause**.

### 7.3. Nên xem cờ thực tế của JVM, đừng suy từ lệnh ghi trên wiki

Dùng:

```bash
java -XX:+PrintFlagsFinal -version
```

Sau đó tìm các flag `MetaspaceSize`, `MaxMetaspaceSize`, `CompressedClassSpaceSize`, `UseCompressedClassPointers`, `UseCompressedOops` khi được runtime hỗ trợ. Output rất dài và thay đổi theo JDK; không dùng một snapshot từ máy khác làm thông số mặc định cho mọi container.

Với JVM đang chạy, `jcmd <pid> VM.flags` cho phép xem một phần flag thực tế.

## 8. Vì sao class unloading quan trọng với Metaspace?

### 8.1. Class được nạp không tự biến mất khi method return

Một class được nạp có thể tồn tại trong JVM suốt thời gian ứng dụng nếu defining loader của nó và class metadata vẫn còn reachable theo các quy tắc JVM/GC.

Các tình huống phổ biến:

- Class của bootstrap/platform/system/application loader có vòng đời dài.
- Plugin được load qua một custom `ClassLoader`.
- Framework sinh nhiều class động (proxies, bytecode enhancement).
- Reload triển khai ứng dụng tạo những loaders mới.
- Thread, `ThreadLocal`, listener, cache hoặc static registry vô tình giữ reference tới loader cũ.

**Metaspace tăng không nhất thiết chứng minh leak.** Một ứng dụng có warm-up, lazily loaded classes hoặc thêm module mới có thể tăng metadata rồi ổn định. Cần xem mức tăng **qua nhiều chu kỳ hoạt động/GC/unloading**, không phán đoán chỉ từ một điểm đo.

### 8.2. Vòng đời metadata và unload

```text
Load class / define class
          |
          v
Metadata cấp phát cho class-loader data
          |
          v
Class còn được dùng / reachable? ---- có ----> tiếp tục giữ metadata
          |
         không
          v
GC có thể unload class / loader khi phù hợp
          |
          v
Metaspace chunks được tái sử dụng hoặc
committed memory được reclaim (tùy JVM)
```

Class unloading gắn với các điều kiện reachability, phiên bản, GC và cài đặt thực tế. **Không có cam kết cứ gọi `System.gc()` là unload toàn bộ class loader đang không còn dùng**; JVM có quyền xử lý lời gợi ý GC và chưa chắc unload xảy ra ngay.

### 8.3. Ví dụ một class-loader leak

Giả sử ứng dụng plugin có code dạng:

```java
// Sơ đồ minh họa, chưa phải ứng dụng độc lập:
static final java.util.List<ClassLoader> KEEP_FOREVER =
        new java.util.ArrayList<>();

void reloadPlugin() {
    ClassLoader loader = createPluginLoader();
    KEEP_FOREVER.add(loader);        // Cố tình giữ mọi loader đã tạo.
    // Nạp class của plugin bằng loader.
}
```

Nếu lần reload nào cũng thêm loader vào `KEEP_FOREVER`, những loaders cũ có thể tiếp tục reachable. Class metadata không được thu hồi như kỳ vọng.

Thêm bộ nhớ vào `-Xmx` không trực tiếp sửa vấn đề class metadata native được giữ. Tăng `MaxMetaspaceSize` cũng chỉ dời giới hạn nếu việc tạo loader không dừng.

### 8.4. Giải pháp về vòng đời

- Gỡ plugin khỏi registries/caches đúng cách.
- Đóng những loader có resource quản lý cần đóng, ví dụ `URLClassLoader.close()`, nhưng nhớ rằng **đóng URLClassLoader không tự ép unload mọi class**.
- Dừng threads của plugin, bỏ context class loader không còn cần.
- Xóa listeners, scheduled tasks, ThreadLocals và references xuyên từ host sang plugin.
- Giảm sinh class/proxy không giới hạn và dùng caching đúng scope.
- Đo lại unload/classloader counts sau mỗi chu kỳ reload.

Cần **quy trách nhiệm sở hữu object/loader** rõ ràng hơn là chỉ đặt một dung lượng Metaspace cực lớn.

## 9. Hai thông báo OutOfMemoryError dễ bị nhầm

### 9.1. `OutOfMemoryError: Metaspace`

Thông báo thường gặp:

```text
java.lang.OutOfMemoryError: Metaspace
```

Nó gợi ý **metadata allocation** không thể tiếp tục trong điều kiện runtime thực tế: có thể do `MaxMetaspaceSize`, native memory pressure, class-loader retention hoặc yếu tố khác. Thông báo và hành vi phụ thuộc phiên bản HotSpot.

Phân tích:

1. Kiểm tra cờ `MaxMetaspaceSize` có bị đặt quá thấp hay không.
2. Xem used/committed, số lượng classes/loaders và xu hướng theo thời gian.
3. Tìm các loaders tăng mãi hoặc tồn tại sau redeploy.
4. Kiểm tra native memory/container giới hạn; không chỉ heap.
5. Sửa nguồn tăng bất thường, sau đó mới điều chỉnh trần cho workload thật.

### 9.2. `OutOfMemoryError: Compressed class space`

Thông báo thường gặp khi dùng compressed class pointers:

```text
java.lang.OutOfMemoryError: Compressed class space
```

Nó gợi ý vùng **class metadata được quản lý trong compressed class space** đã không thể đáp ứng cấp phát, không đơn giản là “Java heap đầy”. Điều tra `CompressedClassSpaceSize`, cờ pointer compression, số lượng classes/loaders và metadata pressure.

Có thể tăng class-space limit khi VM và tài nguyên cho phép, hoặc xem xét cấu hình pointer compression theo tài liệu JVM cụ thể, **nhưng phải đánh giá tác động memory/performance và compatibility**. Không khuyến cáo tùy tiện tắt compressed class pointers để che giấu vòng đời loader sai.

### 9.3. So sánh với lỗi heap

| Dấu hiệu | Vùng nghi vấn trước tiên | Điều cần kiểm tra |
| --- | --- | --- |
| `OutOfMemoryError: Java heap space` | Java heap | Object retention, `-Xmx`, allocation profile |
| `OutOfMemoryError: Metaspace` | Class metadata native | Metadata limits, class loaders/classes, unloading |
| `OutOfMemoryError: Compressed class space` | Compressed class metadata region | Class space limit, compressed pointers, loader churn |
| OS/container kill do vượt RAM | **Toàn bộ resident process** | Heap + metaspace + stacks + code cache + GC/native allocations |

Lỗi thực tế có thể phức tạp; đây là **hướng điều tra ban đầu**, không phải phép chứng minh nguyên nhân duy nhất.

## 10. Quan sát Metaspace bằng các công cụ JDK

Các công cụ bên dưới phù hợp với **HotSpot/JDK hiện đại**. Cú pháp/khả năng hỗ trợ tùy runtime. Trước khi dùng trên sản xuất, cân nhắc quyền truy cập, tác động của lệnh diagnostic và lượng dữ liệu in ra.

### 10.1. Bước một: xác định Java process

```bash
jcmd -l
```

Lệnh có thể in danh sách process Java và PID. Nếu có nhiều JVM, chọn **đúng PID mục tiêu**; không gửi diagnostic tới nhầm ứng dụng.

### 10.2. Xem những diagnostic commands được hỗ trợ

```bash
jcmd <pid> help
```

`<pid>` là placeholder được thay bằng PID thực tế. Không gõ dấu `<` và `>` theo nghĩa cú pháp lệnh trong shell; trong tài liệu chúng chỉ biểu thị vị trí tham số. HotSpot 17+/21 thường hỗ trợ `VM.metaspace` nhưng luôn kiểm tra lệnh thực tế.

### 10.3. Xem metadata usage

```bash
jcmd <pid> VM.metaspace
jcmd <pid> VM.metaspace basic
```

`VM.metaspace` hiển thị thống kê metadata memory. Trong các phiên bản hỗ trợ, `basic` cho tóm tắt với ít yêu cầu dừng/đồng bộ hơn; các tùy chọn khác cho breakdown theo loader, class, chunk hoặc space.

```bash
jcmd <pid> VM.metaspace show-loaders
```

Lệnh này có thể nhiều output, nhất là khi ứng dụng nạp số class lớn. Không xem output là dữ liệu đầu ra cố định; chú ý sự khác nhau của **used/committed/reserved**, các số liên quan compressed class space và từng loader.

### 10.4. Xem class loader statistics

```bash
jcmd <pid> VM.classloader_stats
```

Dùng khi nghi ngờ plugin reload/framework sinh class không thu hồi được. Theo dõi:

- Số loaders.
- Số classes mỗi loader.
- Chunks/blocks metadata hoặc dữ liệu thống kê mà phiên bản HotSpot in ra.
- Những loader type giữ nhiều class hơn mong đợi.
- Những loader vẫn tồn tại sau khi plugin lẽ ra đã được unload.

**Không lấy “loaded class count tăng” làm bằng chứng leak duy nhất.** Khi warm-up, tăng là bình thường; quan trọng là **tốc độ tăng, mức ổn định, xu hướng unloading** và liên hệ với các bước nghiệp vụ.

### 10.5. Native Memory Tracking (NMT)

Khi muốn có breakdown native memory tổng thể, khởi động JVM với:

```bash
java -XX:NativeMemoryTracking=summary -cp . MetaspaceInspectDemo
```

**Lưu ý:** đây là cờ `-XX:NativeMemoryTracking=summary` của HotSpot, không phải `-XX:+NativeMemoryTracking`. Tracking có overhead và thường phải được bật khi JVM khởi động để có số liệu cần thiết.

Sau đó:

```bash
jcmd <pid> VM.native_memory summary
```

NMT có thể giúp phân biệt các nhóm như Class, Thread, Code, GC và những native subsystems khác. Số liệu NMT không nhất thiết bao gồm **mọi allocation từ thư viện native bên ngoài JVM**, và không trực tiếp bằng RSS của process.

Trong những trường hợp phân tích theo chu kỳ, có thể dùng các thao tác baseline và summary.diff khi phiên bản HotSpot hỗ trợ:

```bash
jcmd <pid> VM.native_memory baseline
jcmd <pid> VM.native_memory summary.diff
```

Dùng baseline ngay trước một workload cụ thể, thực hiện workload rồi xem diff; điều này hữu ích hơn việc chỉ đọc số tổng một lần.

### 10.6. GC và class-loading logs

Có thể chạy HotSpot với:

```bash
java -Xlog:gc+metaspace=info -cp . MetaspaceInspectDemo
```

Tùy JVM/JDK, logs cung cấp thêm dữ liệu liên quan metaspace usage khi GC hoặc ở các thời điểm liên quan. Muốn kiểm tra tags và levels thực tế:

```bash
java -Xlog:help
```

Để quan sát class loading, một số cấu hình dùng:

```bash
java -Xlog:class+load=info -cp . MetaspaceInspectDemo
```

Class-load logs thường rất nhiều. Đừng cho rằng logging chi tiết miễn phí hoặc mọi dòng load đều chứng minh một classloader leak.

## 11. Ví dụ Java 17+ độc lập để kết hợp MXBean và `jcmd`

Tạo tệp duy nhất **`MetaspaceInspectDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

public final class MetaspaceInspectDemo {
    static final class Marker {
        static final String NAME = "Marker";
    }

    public static void main(String[] args) throws Exception {
        Class<?> loaded = Class.forName(Marker.class.getName());
        ClassLoadingMXBean classes =
                ManagementFactory.getClassLoadingMXBean();

        System.out.println("same runtime class: "
                + (loaded == Marker.class));
        System.out.println("class loading bean available: "
                + (classes != null));
        System.out.println("loaded class count positive: "
                + (classes.getLoadedClassCount() > 0));

        System.out.println("press Enter to finish");
        new BufferedReader(new InputStreamReader(System.in)).readLine();
        System.out.println("finished");
    }
}
```

Biên dịch/chạy bằng **JDK 17 trở lên**:

```bash
javac --release 17 MetaspaceInspectDemo.java
java MetaspaceInspectDemo
```

**Stdout chính xác khi chạy trên HotSpot/JDK tiêu chuẩn và bấm Enter ở cuối:**

```text
same runtime class: true
class loading bean available: true
loaded class count positive: true
press Enter to finish
finished
```

Mẫu trên đã được biên dịch và chạy bằng JDK 21 với `--release 17`; kết quả khớp. Những giá trị **số lượng class thực tế** và **dung lượng Metaspace** không được hardcode trong stdout vì chúng tùy runtime, workload và class-loading activity.

### 11.1. Vì sao demo chờ Enter?

Chương trình giữ tiến trình sống để bạn có thể chạy `jcmd` từ **một terminal khác**:

```bash
jcmd -l
jcmd <pid> VM.metaspace basic
jcmd <pid> VM.classloader_stats
jcmd <pid> VM.flags
```

Sau khi chụp thống kê, quay lại terminal chạy `MetaspaceInspectDemo` và nhấn Enter để chương trình in `finished` rồi thoát.

Nếu đang chạy dưới IDE/CI không cấp tương tác stdin, lời gọi `readLine()` có thể nhận EOF và chương trình kết thúc ngay. Trong tình huống đó, chạy trực tiếp từ terminal tương tác khi cần inspection.

### 11.2. MXBean hiển thị gì?

`ClassLoadingMXBean` là API chuẩn để truy xuất những số đếm class loading tổng thể, ví dụ:

```java
// Minh họa bên trong phương thức:
ClassLoadingMXBean bean =
        ManagementFactory.getClassLoadingMXBean();
int current = bean.getLoadedClassCount();
long total = bean.getTotalLoadedClassCount();
long unloaded = bean.getUnloadedClassCount();
```

Các số này **có thể thay đổi trong lúc chạy**, nên không nên công bố một “stdout chính xác” cho chúng. Chúng hữu ích để theo dõi xu hướng cùng thời gian/GC/logs, nhưng không đủ để biết chi tiết từng loader hoặc mỗi metadata allocation.

### 11.3. MemoryPoolMXBean có thể hiện Metaspace và class space

Trên HotSpot có thể truy vấn:

```java
// Đoạn code minh họa trong phương thức:
for (java.lang.management.MemoryPoolMXBean pool :
        ManagementFactory.getMemoryPoolMXBeans()) {
    System.out.println(pool.getName() + ": " + pool.getUsage());
}
```

Các pool names và values **không cố định theo mọi JVM**. Có thể thấy `Metaspace` và `Compressed Class Space` nếu cấu hình phù hợp; khi compressed class pointers không sử dụng, pool class-space có thể không xuất hiện theo cách bạn kỳ vọng.

Không dùng output một lần để quyết định đặt flag cho production. Cần so sánh theo thời gian và workload.

## 12. So sánh PermGen, Metaspace và compressed class space

| Tiêu chí | **PermGen (lịch sử)** | **Metaspace (HotSpot 8+)** | **Compressed class space (khi bật)** |
| --- | --- | --- | --- |
| Tồn tại trong HotSpot hiện đại? | **Không** từ JDK 8 | **Có** | **Có điều kiện** trên cấu hình hỗ trợ |
| Vai trò chính | Cấp phát class metadata theo mô hình cũ | Cơ chế metadata allocation ở native memory | Vùng metadata class cho compressed class pointers |
| Quan hệ với Java heap | Vùng/GC generation riêng trong HotSpot cũ; không phải Java heap ứng dụng thông thường | **Ngoài Java heap**, dùng native memory | **Ngoài Java heap**, là vùng riêng liên quan Metaspace |
| Cờ điển hình | `PermSize`, `MaxPermSize` | `MetaspaceSize`, `MaxMetaspaceSize` | `CompressedClassSpaceSize` |
| Lỗi OOME thường gặp | `PermGen space` | `Metaspace` | `Compressed class space` |
| Giải phóng metadata | Theo chính sách GC/class unloading cũ | Tái sử dụng/reclaim gắn với unloading và allocator | Cũng phụ thuộc metadata/class unloading và cơ chế class-space |
| Diễn giải kích thước | Giới hạn vùng theo thế hệ cũ | Used/committed/reserved cần phân biệt | Reservation thường lớn nhưng commit theo nhu cầu |
| Đặc tả JVMS bắt buộc tên này? | Không | Không | Không |

Điểm cần nhớ: **Compressed class space không phải hậu duệ riêng của PermGen**; nó là chi tiết triển khai **bên trong thiết kế HotSpot Metaspace có compressed class pointers**. Tên gọi dễ gây nhầm, nhưng mục đích và cách cấp phát khác nhau.

### 12.1. Vì sao JDK 8 không loại bỏ OutOfMemoryError metadata?

Việc loại bỏ một vùng giới hạn cứng kiểu PermGen không làm class metadata trở nên vô hạn. Native memory vẫn có giới hạn thực tế, JVM có cờ trần, container có giới hạn bộ nhớ, và class loader leak có thể tiếp tục giữ metadata.

Một ứng dụng chỉ tăng `-XX:MaxPermSize` ở JDK 7 rồi nâng lên JDK 17 mà không thay cách giám sát có thể gặp tình huống giám sát “PermGen” bị mất, trong khi **Metaspace** mới là nơi cần quan sát.

### 12.2. “Metaspace nằm trên native heap” có chính xác tuyệt đối không?

Nói **Metaspace sử dụng native memory** là phù hợp. Nhưng “mọi byte Metaspace cấp phát bằng `malloc` trên C heap” không chính xác đối với cách HotSpot thường quản lý metadata: nó có cơ chế reserve/commit các vùng địa chỉ ảo, thường sử dụng ánh xạ bộ nhớ và allocator/chunks riêng. Vì vậy thuật ngữ **native memory**, **reserved address space** và **committed pages** thường chính xác hơn câu “được malloc hết ngay”.

## 13. JEP 387 — Elastic Metaspace và ý nghĩa thực tế

**JEP 387: Elastic Metaspace** đi vào JDK 16. Mục tiêu là cải thiện quản lý native memory dành cho metadata để **tái sử dụng hiệu quả hơn** và **trả bộ nhớ đã commit về hệ điều hành kịp thời hơn**, nhất là những workload có nhiều class loaders nhỏ hoặc liên tục load/unload.

Trước đây, class metadata không còn dùng có thể dẫn đến việc allocator giữ lại không gian committed lớn hơn mong muốn. Elastic Metaspace thiết kế lại một số cấu trúc quản lý và reclamation để giảm footprint.

### 13.1. Các chính sách reclamation

Một cờ trên HotSpot hiện đại:

```text
-XX:MetaspaceReclaimPolicy=balanced
```

Tùy bản JDK, các lựa chọn có thể gồm:

- `balanced`: chính sách cân bằng (thường là mặc định).
- `aggressive`: reclamation tích cực hơn, có thể tăng chi phí quản lý.
- `none`: tắt một số cơ chế reclamation như chính sách tương ứng mô tả.

Các option là **chi tiết HotSpot**, cần xác nhận phiên bản và mục tiêu tuning.

### 13.2. Elastic không đồng nghĩa “chắc chắn trả hết RAM”

Nếu loader và class vẫn reachable thì metadata vẫn **cần tồn tại**. Elastic Metaspace không thay đổi các quy tắc reachability. Nếu một cache giữ hàng trăm loader cũ, policy aggressive cũng không thể biến các class đang sống thành class đã unload.

Ngay cả khi class được unload, tài nguyên runtime khác, fragmentation, allocator management và hoạt động GC khiến committed memory có thể không trở lại chính xác mức khởi động.

## 14. Một quy trình thực tế khi Metaspace tăng liên tục

Giả sử JVM server có heap vẫn ổn định nhưng RSS và Metaspace committed tăng sau mỗi lần reload plugin.

### Bước 1 — Ghi nhận môi trường

Thu thập:

```bash
java -version
jcmd <pid> VM.command_line
jcmd <pid> VM.flags
```

Xác định JVM vendor/build, kiến trúc, GC, container limits và những flag memory được đặt.

### Bước 2 — Phân biệt các vùng bộ nhớ

Thu:

```bash
jcmd <pid> VM.metaspace
jcmd <pid> VM.classloader_stats
jcmd <pid> GC.heap_info
```

Nếu NMT đã bật từ đầu:

```bash
jcmd <pid> VM.native_memory summary
```

Không gộp heap, Metaspace, compressed class space và resident process thành một con số không nguồn gốc.

### Bước 3 — So sánh trước/sau workload lặp lại

Lấy baseline trước khi reload plugin nhiều lần. Ghi nhận:

- Số class loader hiện tồn tại và loại loader.
- Số classes của các loaders.
- Metaspace used/committed (nếu phân tách class-space thì ghi riêng).
- Số unloaded classes theo thời gian nếu có.
- Heap/RSS cùng các hoạt động GC.

Nếu sau mỗi vòng, số loaders vẫn tăng, metadata used tăng và không có dấu hiệu unload tương ứng, điều tra **class loader retention**.

### Bước 4 — Tìm reference giữ loaders

Khảo sát:

- Static registries, callback listeners, JMX registrations.
- Executor threads và thread context class loaders.
- ThreadLocal values/keys, scheduled tasks.
- Caches của framework, reflection/proxy frameworks.
- Resources được giữ bởi plugin hoặc class loader.
- Đối tượng host giữ instance của class do plugin loader định nghĩa.

Không giả định “`loader.close()` đã gọi” là đủ; phải xem những reference nào còn khiến loader reachable.

### Bước 5 — Điều chỉnh cờ khi có bằng chứng

Nếu class loading là hợp lý, ổn định nhưng giới hạn metadata đặt quá nhỏ, xem xét tăng `MaxMetaspaceSize` hoặc class-space cap phù hợp. Nếu là leak, **sửa leak trước**; giới hạn lớn chỉ kéo dài thời gian trước OOME.

### Bước 6 — Xác minh sau sửa

Chạy lại cùng workload với cùng JDK/config:

- Số loaders ổn định hoặc giảm sau unload.
- Metaspace **used** không tăng không giới hạn.
- Committed memory có thể giảm hoặc tái sử dụng theo policy.
- Application heap, native memory và RSS phù hợp ngân sách container.
- Không xuất hiện lỗi runtime mới do thay đổi loader lifecycle.

## 15. Những sai lầm và phát biểu cần sửa

1. **“Metaspace là một phần của Java heap.”** Sai với HotSpot 8+: class metadata của Metaspace dùng native memory ngoài Java heap.
2. **“JVMS bắt buộc mọi JVM phải có Metaspace.”** Sai. JVMS mô tả method area; Metaspace là cách triển khai HotSpot.
3. **“Tăng `-Xmx` là tăng Metaspace.”** Sai. `-Xmx` áp dụng cho Java heap.
4. **“JDK 8 chỉ đổi tên PermGen.”** Sai. Cơ chế cấp phát/accounting và vị trí metadata thay đổi đáng kể.
5. **“String intern pool chuyển sang Metaspace từ JDK 8.”** Sai. Không phải mọi cấu trúc cũ của PermGen được chuyển nguyên vào Metaspace; interned Strings đã có thay đổi từ JDK 7.
6. **“`MaxMetaspaceSize` luôn bằng `CompressedClassSpaceSize` cộng phần còn lại.”** Sai. Hai cờ điều khiển loại giới hạn khác nhau, phụ thuộc ergonomics/committed và reserved.
7. **“Compressed class space 1 GiB reserved nghĩa là 1 GiB RAM đã sử dụng.”** Sai. Reserved khác committed/used.
8. **“Compressed oops và compressed class pointers là một.”** Sai. Loại tham chiếu và mục đích khác.
9. **“Static object instances được cất trong Metaspace.”** Sai. Java objects thuộc heap; metadata class là dữ liệu native.
10. **“Mỗi instance `new` tạo một bản metadata class mới.”** Sai. Các instances thông thường của cùng runtime class chia sẻ metadata của class đã được nạp.
11. **“Class loader leak luôn làm Java heap space OOME trước.”** Sai. Metadata native cũng có thể hết trước, tùy cấu hình/workload.
12. **“`System.gc()` bảo đảm unload mọi class cũ ngay.”** Sai. Không có bảo đảm như vậy.
13. **“Elastic Metaspace đã loại bỏ mọi native memory pressure.”** Sai. Nó tối ưu reclamation, không tạo bộ nhớ vô hạn.
14. **“`MetaspaceSize` là maximum heap của metadata.”** Sai. Nó chủ yếu liên quan GC high-water mark, không là hard cap.
15. **“NMT class memory là tất cả RSS JVM.”** Sai. NMT/VM accounting và RSS phục vụ phép đo khác nhau, có phạm vi khác nhau.
16. **“Không có `Compressed Class Space` trong MXBean là JVM lỗi.”** Không. Feature/pool có thể không xuất hiện ở cấu hình tắt compressed class pointers hoặc JVM khác.
17. **“Một snapshot class count tăng chứng minh leak.”** Sai. Cần quan sát xu hướng, unloading và workload.
18. **“Đặt `-XX:-UseCompressedClassPointers` là cách chữa mọi OOME.”** Sai. Việc tắt nén có trade-offs và không thay vòng đời class loader.

## 16. Bài tập tự kiểm tra có đáp án

**Câu 1.** HotSpot JDK 17 dùng PermGen để lưu metadata lớp không?

**Đáp án:** Không. HotSpot bỏ PermGen từ JDK 8; class metadata được cấp phát qua Metaspace/native memory.

**Câu 2.** Method area của JVMS có phải luôn chính là Metaspace?

**Đáp án:** Không. Method area là khái niệm trong JVM Specification; Metaspace là cách HotSpot triển khai và cấp phát nhiều loại class metadata.

**Câu 3.** `-Xmx1g` đặt trần Metaspace bằng 1 GiB không?

**Đáp án:** Không. `-Xmx` giới hạn Java heap, không trực tiếp giới hạn native Metaspace.

**Câu 4.** `CompressedClassSpaceSize=1g` có nghĩa JVM vừa khởi động đã sử dụng 1 GiB RAM cho metadata không?

**Đáp án:** Không. Flag liên quan virtual address space reservation/capacity; committed/used có thể thấp hơn nhiều.

**Câu 5.** Tại sao `MaxMetaspaceSize=256m` và class-space reservation gần 1 GiB không phải mâu thuẫn?

**Đáp án:** Vì MaxMetaspaceSize tác động lên metadata committed/allocated theo giới hạn runtime, còn class-space reservation là **địa chỉ ảo được dành trước**, không đồng nghĩa đã commit hết.

**Câu 6.** Hai custom class loaders định nghĩa hai bản của `example.Plugin`. Có thể cần hai tập metadata không?

**Đáp án:** Có. Runtime class identity phụ thuộc cả binary name và defining loader, nên hai định nghĩa độc lập có metadata riêng.

**Câu 7.** Sau khi `plugin = null`, metadata của plugin class chắc chắn được giải phóng chưa?

**Đáp án:** Chưa chắc. Class loader/classes có thể còn reachable bởi caches, threads, static registries, listeners hoặc những tham chiếu khác. Class unloading không tức thì.

**Câu 8.** Lệnh nào cho thống kê chi tiết Metaspace của một HotSpot JVM đang chạy?

**Đáp án:** `jcmd <pid> VM.metaspace` khi JVM hỗ trợ. Với loader breakdown có thể dùng `jcmd <pid> VM.classloader_stats`.

**Câu 9.** Muốn dùng `VM.native_memory summary` cần chuẩn bị gì?

**Đáp án:** Trong HotSpot thường phải bật NMT từ lúc khởi động, ví dụ `-XX:NativeMemoryTracking=summary`; công cụ có overhead và không bao gồm mọi memory do native libraries bên ngoài VM tự quản lý.

**Câu 10.** Vì sao `MemoryPoolMXBean` không thể luôn dùng để tính RSS của process?

**Đáp án:** Pool metrics thể hiện những memory pools do JVM báo cáo, không phải toàn bộ OS resident memory gồm mọi native allocations và thread stacks.

**Câu 11.** Tại sao JEP 387 không tự sửa class-loader leak?

**Đáp án:** Elastic Metaspace giúp reclaim **metadata của classes đã unload**. Nếu loader vẫn reachable, class chưa unload nên metadata vẫn cần tồn tại.

**Câu 12.** `OutOfMemoryError: Java heap space` và `OutOfMemoryError: Metaspace` có hướng điều tra khác nhau thế nào?

**Đáp án:** Lỗi heap ưu tiên xem object heap retention và `-Xmx`; lỗi Metaspace ưu tiên xem metadata limits, class counts/loaders, unloading và native memory pressure.

**Câu 13.** `java.lang.Class` object ở heap hay Metaspace?

**Đáp án:** `java.lang.Class` mirror là Java object trong heap trên HotSpot, còn VM có các native metadata structures riêng liên kết với nó.

**Câu 14.** Vì sao không dùng `MaxPermSize` để điều chỉnh HotSpot JDK 21?

**Đáp án:** Vì PermGen đã bị loại bỏ; phải xem các cờ Metaspace/Compressed Class Space được hỗ trợ bởi bản JVM đích.

**Câu 15.** Một lần redeploy khiến Metaspace committed tăng, sau vài lần GC committed không giảm. Có kết luận chắc chắn leak không?

**Đáp án:** Chưa. Cần xem **used, số loaders/classes vẫn tồn tại, khả năng unloading, allocator caching và chính sách reclamation**. Committed có thể ổn định/được giữ để tái sử dụng; cần trend và bằng chứng reachability.

## 17. Checklist khi triển khai HotSpot trong container

Metaspace là một phần của **native memory budget**. Khi một JVM nằm trong container có giới hạn RAM, cần dành chỗ cho:

```text
Tổng giới hạn bộ nhớ container
    ├── Java heap (-Xmx)
    ├── Metaspace + compressed class space (committed)
    ├── JIT code cache
    ├── Thread stacks
    ├── GC/native VM structures
    ├── Direct buffers và native I/O
    └── Những native allocations khác
```

Các mục trên chỉ mang tính **kiểm kê nguồn tiêu thụ**; không giả định mọi field NMT là nhóm độc lập không chồng lấp hay có thể cộng các reservation thành RSS thực.

Gợi ý thực hành:

- Theo dõi RSS, heap used/committed và metadata used/committed riêng.
- Đánh giá warm-up class loading và peak loader count.
- Thử build/runtime đúng JDK đang triển khai.
- Dùng NMT ở môi trường kiểm thử hoặc production có cân nhắc overhead.
- Không đặt `-Xmx` sát toàn bộ RAM container rồi bỏ qua native memory.
- Không giảm quá thấp class-space cap nếu ứng dụng dùng nhiều dynamically generated classes.
- Tránh đặt trần quá lớn vô căn cứ, vì điều đó có thể che dấu tăng metadata bất thường.
- Xác minh bằng monitoring và workload thật, không dùng một default rule cho mọi service.

## 18. Tóm tắt

```text
HotSpot trước JDK 8:
    PermGen
      |
      +-- class metadata theo cơ chế cũ

HotSpot JDK 8+:
    Native class metadata management
      |
      +-- Metaspace (non-class metadata)
      |
      +-- Compressed class space (nếu bật compressed class pointers)
              reserved address space ≠ committed memory ≠ used bytes

Class unloading:
    class/loader không còn cần và đủ điều kiện unload
      |
      +-- metadata allocator có thể tái sử dụng/reclaim
      +-- JEP 387 (JDK 16+) cải thiện trả committed pages
```

**Kết luận trọng tâm:**

- **PermGen là lịch sử của HotSpot trước JDK 8; Metaspace là cơ chế native metadata của HotSpot từ JDK 8.**
- **Compressed class space chỉ là khu vực class metadata đặc thù khi bật compressed class pointers**, không phải toàn bộ Metaspace.
- **Reserved, committed và used phải được đọc khác nhau.** Reservation lớn chưa phải một lượng RAM đã dùng.
- **Metaspace không phải Java heap:** `-Xmx` không trực tiếp giới hạn Metaspace, và OOME Metaspace có hướng chẩn đoán khác OOME heap.
- **Class metadata có vòng đời liên quan class loading/unloading và defining class loaders.** Nếu giữ loader mãi, Metaspace có thể tăng mãi.
- **JEP 387 cải thiện độ đàn hồi nhưng không tự chữa memory leak.** Theo dõi `jcmd VM.metaspace`, `VM.classloader_stats`, NMT và workload có kiểm soát để đưa ra quyết định.

### Tài liệu chính thức và nguồn kiểm chứng

- [Oracle JDK 21 GC Tuning Guide — Class Metadata](https://docs.oracle.com/en/java/javase/21/gctuning/other-considerations.html): Metaspace native memory, compressed class pointers, class space, flags và các chỉ số memory.
- [OpenJDK JEP 122 — Remove the Permanent Generation](https://openjdk.org/jeps/122): loại bỏ PermGen khi chuyển sang JDK 8.
- [OpenJDK JEP 387 — Elastic Metaspace](https://openjdk.org/jeps/387): cải thiện allocator và reclamation từ JDK 16.
- [Oracle JDK 21 — java command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html): các tùy chọn `-XX:MaxMetaspaceSize`, `-XX:MetaspaceSize` và những JVM flags.
- [Oracle JDK 21 — jcmd command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html): `VM.metaspace`, `VM.classloader_stats`, `VM.native_memory` và cách sử dụng diagnostic commands.
- [Oracle JDK 21 — JVM Troubleshooting Guide](https://docs.oracle.com/en/java/javase/21/troubleshoot/): chẩn đoán native memory và các lỗi liên quan class metadata.
- [JVMS §2.5.4 — Method Area](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5.4): vùng khái niệm trong đặc tả JVM, không bắt buộc chi tiết HotSpot.
- [ClassLoadingMXBean — JDK 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ClassLoadingMXBean.html): số lượng classes được load/unload.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-007">Code cache lưu mã máy do JVM tạo ra</a>

<details>
<summary>Xem chi tiết</summary>

# Code cache lưu mã máy do JVM tạo ra

**Code cache** trong HotSpot JVM là vùng bộ nhớ dùng để giữ **mã máy native đã được JVM tạo ra**, nổi bật là các phương thức Java được **JIT (Just-In-Time) compiler** biên dịch, cùng một số đoạn mã phục vụ bản thân VM như runtime stubs và adapters. Khi một method trở nên đủ “hot”, JVM có thể biên dịch bytecode của nó thành instruction cho CPU đang chạy rồi lưu phần mã máy đó ở code cache để dùng lại.

Đây là thành phần rất quan trọng trong chủ đề **các vùng bộ nhớ JVM**, nhưng cần phân biệt: **Java heap giữ objects; metaspace giữ class metadata; thread stack giữ frames; code cache giữ mã có thể được CPU thực thi**. Code cache không phải tên một vùng bộ nhớ bắt buộc với cùng cách hiện thực trên mọi JVM theo JVM Specification. Những cấu trúc như **nmethod**, **CodeHeap**, **C1/C2 tiered compilation** và các option `-XX:...` trong bài chủ yếu là **chi tiết HotSpot/OpenJDK**, không phải cam kết phổ quát cho mọi JVM.

## 1. Từ `.java` tới mã máy nằm trong code cache

Khi biên dịch và chạy một class Java thông thường, hai thời điểm khác nhau xuất hiện:

```text
        Compile time                                Runtime
  ┌─────────────────────┐                 ┌──────────────────────────┐
  │ Hello.java          │                 │ JVM / ClassLoader        │
  │ javac               │                 │ đọc Hello.class          │
  └─────────┬───────────┘                 └────────────┬─────────────┘
            │                                          │
            ▼                                          ▼
       Hello.class                         JVM bytecode + metadata
      JVM bytecode                                   │
                                          ┌──────────┴──────────────┐
                                          │                         │
                                          ▼                         ▼
                                    Interpreter                 JIT compiler
                                    thực thi bytecode          biên dịch method
                                                                     │
                                                                     ▼
                                                              Native machine code
                                                                     │
                                                                     ▼
                                                                 CODE CACHE
                                                                     │
                                                                     ▼
                                                             CPU thực thi mã JIT
```

Sơ đồ chỉ mô tả các đường thực thi có thể có. **Không phải một method luôn luôn được JIT compile**: code ít được dùng có thể tiếp tục chạy bằng interpreter; một method có thể được biên dịch nhiều lần ở mức tối ưu khác nhau; và JVM có thể bỏ mã máy đã biên dịch để trở lại interpreter hoặc biên dịch lại.

**Ba lớp mã cần phân biệt:**

1. **Java source**: ký tự trong `.java`, biên dịch bởi `javac`.
2. **JVM bytecode**: các opcode như `iload`, `iadd`, `invokevirtual` trong `.class`, về nguyên tắc độc lập với CPU đích.
3. **Native machine code**: instruction x86-64, AArch64... do JIT của JVM tạo cho nền tảng hiện tại. Những đoạn này được đặt trong code cache.

`javap -c` hiển thị **JVM bytecode trong class file**, **không phải native instructions trong code cache**. JIT có thể tạo mã khác hẳn cấu trúc bytecode vì inlining, loop optimization, vectorization và những phép tối ưu khác.

### Ví dụ ngắn về cùng một lời gọi

```java
static int square(int x) {
    return x * x;
}
```

Đây là một method minh họa trong một class. Bytecode thường có dạng:

```text
iload_0
iload_0
imul
ireturn
```

Các instruction này là **bytecode**. Nếu method được JIT compile, CPU có thể thực thi native code tương ứng, hoặc mã đã được **inline** vào caller mà không còn một lời gọi method độc lập. Vì vậy không thể chỉ đếm số Java methods để kết luận code cache nhất thiết có đúng ngần ấy đoạn mã máy.

## 2. Code cache nằm ở đâu so với những vùng bộ nhớ khác?

| Vùng | Nội dung điển hình | Cơ chế / ghi chú |
| --- | --- | --- |
| **Java heap** | Object instances, arrays, phần lớn dữ liệu ứng dụng | Chịu GC; thường chịu ảnh hưởng `-Xmx` |
| **Metaspace** (HotSpot) | Class metadata, thông tin runtime của class/interface | Dùng native memory; chịu các giới hạn metaspace riêng |
| **Java thread stack** | Stack frames, local variables và operand stacks | Mỗi thread có stack riêng; ảnh hưởng bởi `-Xss` |
| **Code cache** (HotSpot) | JIT-compiled machine code và một số VM-generated code | Native/executable memory, quản lý code blobs/nmethods |
| **Direct/native buffers** | Buffer off-heap, JNI/native allocations và tài nguyên khác | Không đồng nhất với code cache |
| **ClassFile trên đĩa/JAR** | JVM bytecode và constant pool đã biên dịch | Không phải vùng code cache trong tiến trình |

Code cache có thể được mô tả dưới **non-heap memory** khi xem management API, nhưng “non-heap” **không có nghĩa một vùng duy nhất**. Metaspace và code cache đều nằm ngoài Java heap theo các cấu trúc quản lý khác nhau.

**Quan trọng về bộ nhớ tổng của tiến trình:**

```text
Bộ nhớ tiến trình JVM
  = heap thực dùng / committed
  + metaspace, class metadata
  + thread stacks
  + code cache và các vùng native khác
  + GC/JIT/compiler buffers, thư viện native, ...
```

Đây là công thức phân loại, **không phải phép cộng chính xác dự báo RSS**, vì reserved virtual address space, committed pages, shared mappings và hệ điều hành có những cách hạch toán khác nhau. Nếu container có giới hạn bộ nhớ, chỉ điều chỉnh `-Xmx` mà bỏ qua native allocations có thể vẫn khiến tiến trình vượt giới hạn.

### Code cache không phải heap objects

Ví dụ `new byte[10_000_000]` tạo **array object trên Java heap**; nó không yêu cầu 10 MB code cache. Ngược lại, method thực hiện vòng lặp nhiều lần có thể làm JIT tạo thêm mã máy dù dữ liệu mà method tính toán khá nhỏ.

Việc gọi `System.gc()` không phải lệnh “xóa code cache”. GC có thể phối hợp với các cơ chế làm sạch compiled code khi mã không còn hợp lệ hoặc có class unloading, nhưng chính sách quản lý code cache khác với quá trình thu gom các objects của Java heap.

## 3. Những loại mã được lưu trong HotSpot code cache

HotSpot dùng những cấu trúc nội bộ như **CodeBlob** để đại diện các đơn vị code/data liên quan trong code cache; **nmethod** là một dạng chứa compiled code của Java methods.

| Thành phần | Mục đích |
| --- | --- |
| **JIT-compiled nmethods** | Mã máy cho một phương thức/phiên bản tối ưu hóa cụ thể |
| **OSR nmethods** | Mã biên dịch cho phép chuyển vào vòng lặp đã biên dịch khi method đang chạy |
| **Runtime stubs** | Mã cầu nối/tiện ích JVM tạo cho các thao tác runtime đặc biệt |
| **Adapters** | Mã hỗ trợ chuyển tiếp giữa các calling conventions và đường gọi |
| **Interpreter-related generated code** | Một số đoạn code cần thiết cho engine interpreter trong HotSpot |
| **Compiler/VM buffers, auxiliary code** | Thành phần được HotSpot quản lý dưới code cache tùy cấu hình |

**Không nên hiểu toàn bộ code cache chỉ gồm “mỗi Java method đúng một nmethod”.** Một method có thể có phiên bản do C1 và phiên bản do C2 biên dịch, có OSR version riêng, hoặc được inline trong mã của method khác. JVM còn cần stubs và adapters không phải Java method biên dịch thông thường.

### Mã native ở code cache có cố định vĩnh viễn?

Không. JVM có thể:

- Thay mã C1 bằng một bản tối ưu hơn khi có profiling thích hợp.
- Đánh dấu nmethod **not entrant** để không nhận các lời gọi mới.
- Thực hiện **deoptimization** khi giả định tối ưu không còn đúng.
- Dọn mã cũ/không còn được dùng qua **code cache sweeper/code cache flushing** theo chính sách HotSpot.
- Giải phóng code liên quan tới class unloading hoặc dependency không còn hợp lệ khi đủ điều kiện.

Những hoạt động trên phụ thuộc thời gian chạy, trạng thái các thread và điều kiện an toàn. Không suy luận code cache **chỉ tăng không bao giờ giảm** hoặc **GC luôn xóa compiled code ngay khi một instance bị thu gom**.

## 4. Tiered compilation: vì sao HotSpot cần nhiều dạng mã?

HotSpot thường dùng **tiered compilation** để cân bằng **tốc độ khởi động**, **chi phí biên dịch**, **profiling** và **hiệu năng dài hạn**.

Mô hình khái quát:

```text
Chạy method lần đầu
          │
          ▼
 Interpreter + thu thập thông tin
          │
          ▼
 Method trở nên "hot"
          │
          ▼
   C1 biên dịch nhanh
   có thể bổ sung profiling
          │
          ▼
   C2 tối ưu chuyên sâu
   khi heuristics phù hợp
          │
          ▼
   Native code ở code cache
          │
          └── có thể deopt/recompile
```

Đây không phải pipeline bắt buộc “tất cả method đều đi đủ C1 rồi C2”. HotSpot có nhiều compilation levels, policy chuyển tầng, compilation thresholds và tình huống chỉ dùng một compiler hoặc không JIT. Các chi tiết thay đổi theo version và JVM flags.

### Interpreter không phải “bytecode nằm trong code cache”

- **Bytecode** vẫn là các instruction định nghĩa trong class file / runtime metadata.
- **Interpreter** đọc và thực thi ý nghĩa bytecode bằng cơ chế VM; một số interpreter stubs/mã phục vụ VM có thể ở code cache.
- **JIT** tạo mã máy chuyên biệt cho method/đường thực thi rồi quản lý nó ở code cache.

Vì interpreter có thể tự là mã native do VM cung cấp, đừng nói “mọi byte native trong code cache đều do C2 compile từ một Java method”. Ngược lại, code cache có thể chứa mã không phải bytecode Java source được JIT dịch trực tiếp.

## 5. Segmented code cache: nhiều CodeHeaps thay vì một khối duy nhất

HotSpot hỗ trợ **segmented code cache** theo JEP 197. Khi bật phân đoạn, code cache thường có ba **CodeHeaps** theo loại mã:

| Phân đoạn | Nội dung chính | Tên quan sát có thể gặp |
| --- | --- | --- |
| **Non-method** | VM-generated code không thuộc compiled Java method, ví dụ adapters/stubs/interpreter hỗ trợ | `CodeHeap 'non-nmethods'` |
| **Profiled methods** | Mã compiled có profiling, thường ngắn hạn và có thể được biên dịch lại | `CodeHeap 'profiled nmethods'` |
| **Non-profiled methods** | Mã compiled ít/không mang profiling, thường tối ưu dài hạn | `CodeHeap 'non-profiled nmethods'` |

Sơ đồ logic:

```text
HotSpot Code Cache (native executable code region)
┌───────────────────────────────────────┐
│ CodeHeap: non-nmethods                │
│  VM stubs / interpreter code / ...   │
├───────────────────────────────────────┤
│ CodeHeap: profiled nmethods           │
│  tiered compilation + profiling      │
├───────────────────────────────────────┤
│ CodeHeap: non-profiled nmethods       │
│  tối ưu cao, code dùng dài hạn        │
└───────────────────────────────────────┘
```

Việc phân đoạn nhằm cải thiện locality, giảm fragmentation và phân biệt vòng đời của những loại compiled code khác nhau. **Các CodeHeaps không tương đương ba Java heaps và không được GC Java như object collections.**

**Không phải JVM nào cũng có đúng ba phân đoạn trong mọi lúc.** Nếu segmentation tắt, có thể chỉ thấy `Code Cache` chung. Cấu hình tiered compilation, kích thước code cache, version HotSpot và các tùy chọn chuyên biệt có thể làm số phân đoạn khác. Một số phiên bản JVM tương lai có thể có thay đổi mới; cách chắc chắn nhất là **đọc thống kê từ JVM thực tế**.

### Những flags liên quan phân đoạn

```text
-XX:+SegmentedCodeCache
-XX:NonNMethodCodeHeapSize=<size>
-XX:ProfiledCodeHeapSize=<size>
-XX:NonProfiledCodeHeapSize=<size>
```

Các tham số trên là **HotSpot-specific**; không nên sao chép các giá trị tùy ý khi chưa đo tải và kiểm tra hỗ trợ trên phiên bản JDK. Với JDK 17 HotSpot, tên/khả năng của các flags có thể kiểm tra bằng `java -XX:+PrintFlagsFinal -version`, nhưng output rất dài và default có thể phụ thuộc môi trường.

**Cảnh báo cấu hình:** phân đoạn có các giới hạn bộ nhớ được phân chia theo loại; tăng tổng `ReservedCodeCacheSize` chưa chắc sửa được mất cân đối nếu đã tùy chỉnh riêng kích thước từng CodeHeap. Cần xem code heap nào đầy và VM có bật segmentation không trước khi chọn phương án chỉnh.

## 6. OSR (On-Stack Replacement) cũng dùng code cache

Một vòng lặp có thể chạy rất lâu **trong cùng một lần gọi method**. Nếu JVM chỉ JIT giữa các lần gọi, phương thức một lần chạy nhưng lặp hàng triệu vòng có thể mãi ở interpreter. **OSR** cho phép JVM biên dịch một đường vào vòng lặp và chuyển thực thi từ frame đang chạy sang mã compiled tại **OSR entry point** thích hợp.

```text
Method bắt đầu (interpreter)
          │
          ▼
     loop iteration...
          │ đủ nóng
          ▼
 JIT biên dịch OSR nmethod
          │
          ▼
    chuyển vào loop
       compiled code
          │
          ▼
  tiếp tục vòng lặp
```

Mã OSR có thể cùng tồn tại với phiên bản compiled thông thường của method. `-XX:+PrintCompilation` trong HotSpot có thể hiển thị ký hiệu **`%`** liên quan tới OSR compilation, nhưng không nên coi mọi vòng lặp trong chương trình demo chắc chắn tạo OSR ở mọi máy/chế độ.

**OSR không phải stack overflow**, cũng không có nghĩa JVM chép toàn bộ Java thread stack sang code cache. Nó là một cơ chế chuyển **đường thực thi** vào compiled code cho phần method đang hoạt động.

## 7. Nmethod có gì ngoài machine instructions?

Một compiled method cần tương tác với VM và runtime:

- **Entry points** để gọi compiled code theo calling convention thích hợp.
- **Machine instructions** thực hiện tính toán và các kiểm tra cần thiết.
- **Metadata** giúp xác định vị trí/quan hệ bytecode, inlining và trạng thái deoptimization.
- **Safepoints/polling** và những cơ chế giúp GC, stack walking, deoptimization phối hợp.
- **Oop maps**, relocation và những mô tả cần thiết để runtime biết nơi có object references và cập nhật chúng theo điều kiện áp dụng.
- **Exception handling/unwind information** phù hợp với code đã biên dịch.

Danh sách phản ánh ý tưởng thiết kế HotSpot; **không phải mô tả layout byte-for-byte của mọi `nmethod`**. Cách đặt những dữ liệu đi kèm có thể thay đổi giữa builds/architectures.

### Tại sao compiled code không thể xem như tệp EXE độc lập?

Một đoạn JIT-compiled code sử dụng runtime assumptions, class metadata, helper routines và địa chỉ trong **cùng JVM process**. Nó còn phải có khả năng deopt và phối hợp với GC/class loading. Vì vậy không thể lấy raw bytes từ code cache rồi dùng nó như `.exe` portable chạy trên máy khác mà không có VM context.

Code cache không phải một “ổ đĩa cache” để lần sau mở ứng dụng tự load lại tất cả native code giống hệt. HotSpot có các công nghệ khác như **Class Data Sharing (CDS)**, AppCDS và những cơ chế AOT tùy bản JDK, nhưng chúng **không đồng nhất** với code cache JIT thông thường.

## 8. Reserved, committed, used và free: bốn khái niệm dễ nhầm

HotSpot thường **reserve** một khoảng địa chỉ ảo tối đa cho code cache rồi **commit** bộ nhớ khi cần. Các thống kê có thể báo:

| Chỉ số | Ý nghĩa khái quát |
| --- | --- |
| **reserved** | Khoảng địa chỉ/khả năng bộ nhớ đã dành riêng cho code cache |
| **committed** | Phần vùng reserved đã được hệ thống/JVM cam kết sử dụng theo cơ chế quản lý |
| **used** | Dung lượng code blobs và cấu trúc đã chiếm chỗ theo thống kê của VM |
| **free** | Phần dung lượng còn trống theo cách đo của code heap |
| **max** | Giới hạn được báo qua management API, có thể tương ứng reservation/giới hạn VM |
| **largest free block / fragmentation** | Các yếu tố khả năng cấp phát code mới dù tổng free vẫn còn |

Các giá trị phụ thuộc thời điểm đọc và không nhất thiết trùng tổng memory RSS của tiến trình. **Reserved 240 MB không chứng minh JVM đang dùng 240 MB RAM vật lý**; cũng không thể lấy `used` từ các công cụ khác nhau rồi mặc định chúng cùng tính metadata/overhead một cách giống hệt.

### Tại sao code cache “còn free” mà cấp phát vẫn thất bại?

Một số trường hợp code cache bị **fragmentation** hoặc từng CodeHeap riêng bị đầy. Một code blob mới cần **vùng phù hợp**, nên dung lượng trống tổng cộng không đảm bảo mọi yêu cầu cấp phát mã đều thành công. Các chính sách sweeper/flushing, phân đoạn và compiler cũng ảnh hưởng khả năng cấp phát.

Không thể kết luận chỉ bằng một số `free=...`; cần xem thống kê theo heap, code cache state và compile activity.

## 9. Các JVM options cần biết

| Option HotSpot | Vai trò | Ghi chú |
| --- | --- | --- |
| `-XX:ReservedCodeCacheSize=<size>` | Đặt giới hạn vùng code cache được reserve | Ví dụ `64m` hoặc `256m`; hiệu quả phụ thuộc workload |
| `-XX:InitialCodeCacheSize=<size>` | Đặt kích thước ban đầu/commit khởi điểm | Phải nhất quán với giới hạn tổng |
| `-XX:+SegmentedCodeCache` | Cho phép phân đoạn code cache | Không phải JVM nào cũng mặc định như nhau |
| `-XX:+PrintCodeCache` | In thống kê code cache từ HotSpot | Thường xuất hiện khi JVM kết thúc |
| `-XX:+PrintCompilation` | In sự kiện method compilation | Logging thay đổi theo tải, phiên bản, compiler |
| `-XX:-TieredCompilation` | Tắt tiered compilation | Ảnh hưởng compiler strategy và code cache footprint |
| `-Xint` | Chạy ở chế độ thông dịch chủ yếu, không dùng JIT compilation của methods | Không có nghĩa code cache/VM native support code bằng 0 |
| `-Xcomp` | Ưu tiên biên dịch sớm các methods theo chính sách VM | Dùng thận trọng khi thử nghiệm; không phản ánh tải bình thường |

Ví dụ cách xem giá trị flag **thực tế**:

```bash
java -XX:+PrintFlagsFinal -version
```

Tìm các tên `ReservedCodeCacheSize`, `InitialCodeCacheSize`, `SegmentedCodeCache`, `TieredCompilation` trong output. Lệnh trả về rất nhiều flags; trên Linux/macOS có thể lọc:

```bash
java -XX:+PrintFlagsFinal -version 2>&1 | grep -E 'ReservedCodeCacheSize|InitialCodeCacheSize|SegmentedCodeCache|TieredCompilation'
```

Trên PowerShell:

```powershell
java -XX:+PrintFlagsFinal -version 2>&1 | Select-String 'ReservedCodeCacheSize|InitialCodeCacheSize|SegmentedCodeCache|TieredCompilation'
```

Những lệnh này **không thay cấu hình hệ thống**. Giá trị mặc định của các flags có thể thay đổi theo release JDK, kiến trúc, bộ nhớ, chế độ JVM và ergonomics. Không hard-code “mọi Java đều có mặc định đúng 240 MB”; nếu đề cập con số đó, hãy kiểm tra phiên bản HotSpot đang dùng.

### Phân biệt sizing và tuning

Tăng `ReservedCodeCacheSize` có thể giúp khi **thực sự code cache thiếu** và workload cần giữ nhiều compiled code. Nhưng tăng giới hạn cũng dành thêm **virtual address space** và có thể tăng native memory commitment theo tải; nó không sửa memory leak ở Java heap, classloader leak ở metaspace, hay CPU bottleneck không liên quan JIT.

Giảm code cache quá mạnh có thể tăng **compilation failures**, code flushing hoặc gây giảm performance. Đừng áp một giá trị cố định cho mọi ứng dụng/production server chỉ vì thấy ví dụ trong bài.

## 10. Khi code cache đầy, JVM và ứng dụng bị ảnh hưởng như thế nào?

HotSpot có thể báo thông điệp tương tự:

```text
CodeCache is full. Compiler has been disabled.
Try increasing the code cache size using -XX:ReservedCodeCacheSize=
```

Đây là **mẫu thông báo thường gặp, không phải log chính xác bắt buộc ở mọi JDK**. Khi không thể cấp phát thêm code, JVM có thể ngừng hoặc hạn chế các tác vụ compilation phù hợp cho đến khi có điều kiện giải phóng/khôi phục theo chính sách. Ứng dụng **không nhất thiết dừng ngay** chỉ vì xuất hiện cảnh báo; nó có thể vẫn chạy bằng mã interpreter hoặc mã compiled còn hợp lệ nhưng throughput/latency giảm.

Những triệu chứng có thể quan sát:

- **Warm-up** không đạt tốc độ mong đợi dù method được gọi nhiều lần.
- Thời gian xử lý/CPU tăng hoặc latency p95/p99 xấu đi do compiler không tạo mã mới.
- Compiler queue tăng, các phương thức nóng chưa được tối ưu như bình thường.
- Thống kê code cache có used gần giới hạn hoặc một CodeHeap riêng cạn chỗ.
- Log cảnh báo liên quan code cache, code cache sweeper, compilation disabled hoặc code heap full.

**Không suy luận chỉ từ một triệu chứng:** CPU cao cũng có thể do GC, locking, dữ liệu lớn, I/O hoặc vòng lặp ứng dụng; latency cao có thể do network. Code cache metrics cần được kết hợp với **JIT compilation metrics, GC logs, thread profiles và tải thực tế**.

### Phân biệt `OutOfMemoryError` với code cache full

Code cache đầy **không bắt buộc** báo `OutOfMemoryError: Java heap space`. Đây là một vấn đề về vùng HotSpot dành cho generated code; VM có thể giảm/ngừng compilation trong khi Java heap còn trống. Tuy nhiên các lỗi native allocation hoặc cấu hình cực đoan có thể có biểu hiện nghiêm trọng riêng. Hãy đọc **log cụ thể** của JVM thay vì quy mọi lỗi cấp phát vào một exception giống nhau.

## 11. Code cache sweeper, deoptimization và class unloading

Mã đã biên dịch không bất biến mãi mãi:

```text
Method hot → JIT compile → nmethod trong CodeHeap
                             │
                   chạy và được profiling
                             │
          ┌──────────────────┼─────────────────┐
          │                  │                 │
          ▼                  ▼                 ▼
      vẫn dùng           deopt/recompile    không còn dùng
          │                  │                 │
          ▼                  ▼                 ▼
      giữ lại          nmethod cũ         sweeper/code cache
                       not entrant        cleanup khi an toàn
```

- **Deoptimization:** JIT có thể tối ưu dựa trên giả định về runtime types, class hierarchy, branch frequencies... Khi giả định cần thay đổi, VM có thể chuyển về trạng thái ít tối ưu hơn và tiếp tục thực thi đúng ngữ nghĩa.
- **Not entrant/zombie:** các trạng thái quản lý compiled methods của HotSpot biểu thị compiled code không còn dùng cho lời gọi mới hoặc đang chờ điều kiện dọn dẹp phù hợp.
- **Code cache sweeper:** có thể giải phóng compiled methods không còn cần thiết theo điều kiện an toàn; không được loại bỏ mã mà thread vẫn cần thực thi một cách phá vỡ correctness.
- **Class unloading:** có thể khiến compiled code liên quan mất tính hợp lệ hoặc được dọn, nhưng class unloading còn phụ thuộc khả năng sống của class loader, Class objects, instances và các reference liên quan.

**Code cache flushing** không giống với **evict entries của một Java `Map`**. Nó tác động vào **mã máy có thể đang được các threads gọi** và phải phối hợp với VM runtime state.

## 12. Thực hành Java 17+: tạo workload có thể được JIT tối ưu

Tạo file **`CodeCacheDemo.java`**:

```java
public class CodeCacheDemo {
    static long checksum(int rounds) {
        long total = 0;
        for (int i = 1; i <= rounds; i++) {
            total += i % 7;
        }
        return total;
    }

    public static void main(String[] args) {
        long result = 0;
        int repeats = 50;
        for (int j = 0; j < repeats; j++) {
            result = checksum(700_000);
        }

        System.out.println("checksum: " + result);
        System.out.println("repeats: " + repeats);
    }
}
```

Chạy trên JDK 17+:

```bash
javac -encoding UTF-8 CodeCacheDemo.java
java CodeCacheDemo
```

**Standard output chính xác khi chạy không bật logging bổ sung:**

```text
checksum: 2100000
repeats: 50
```

Lý do `checksum` có kết quả **2.100.000**: dãy phần dư của `i % 7` qua 7 giá trị liên tiếp có tổng `0+1+2+3+4+5+6 = 21`. `700_000` chia hết cho 7 và chứa `100_000` chu kỳ, nên tổng là `21 × 100_000`. Biến `result` được gán từ kết quả mỗi lần gọi; không cộng 50 lần kết quả vào stdout.

**Lưu ý:** chương trình này tạo cơ hội cho JIT quan sát method/vòng lặp hot, nhưng **không cam kết JVM phải compile method** ở một cấp cụ thể, cũng không cam kết code cache tăng đúng số byte nào. Quyết định JIT là việc của VM.

### 12.1. Đọc JVM bytecode

```bash
javap -c -p CodeCacheDemo
```

Tìm `checksum(int)` với các opcode như `iload`, `irem`, `i2l`, `ladd`, `lstore`, nhánh điều khiển vòng lặp, `lreturn`. Đây là **bytecode trong class file**. Dù output `javap` xuất hiện, nó **không chứng minh** method đã có native compiled version trong code cache.

### 12.2. In hoạt động compilation

```bash
java -XX:+PrintCompilation CodeCacheDemo
```

Bên cạnh hai dòng chương trình, JVM có thể in nhiều dòng về compiled methods. Dạng của output HotSpot thường có các chỉ báo **compile id**, **tier/level**, **method name**, **kích thước** và ký hiệu biểu thị trạng thái. Thứ tự/giá trị **không cố định**, vì còn có JIT compiler threads và các classes thư viện được VM khởi tạo/compile.

Nếu thấy `CodeCacheDemo::checksum` trong logging, đó là bằng chứng JVM đã biên dịch một phiên bản nào đó của method, **không chứng minh mỗi lần gọi đều tiếp tục chạy đúng bản đó**: deopt/recompile có thể xảy ra.

### 12.3. Xem thống kê code cache ở lúc VM kết thúc

```bash
java -XX:+PrintCodeCache CodeCacheDemo
```

Trên HotSpot hỗ trợ flag này, ngoài output của chương trình, JVM in thống kê code cache khi kết thúc. Bạn có thể thấy các dòng tương tự:

```text
CodeHeap 'non-profiled nmethods': size=... used=... max_used=... free=...
CodeHeap 'profiled nmethods':     size=... used=... max_used=... free=...
CodeHeap 'non-nmethods':          size=... used=... max_used=... free=...
```

Đây là **hình dạng log minh họa**, không phải output stdout chính xác: dấu `...` có nghĩa giá trị thực cần lấy từ máy đang chạy; JVM không phân đoạn có thể chỉ in một nhóm thống kê khác. Dòng code cache là thông tin HotSpot, không phải lời in từ `System.out.println` trong source.

### 12.4. Thử thay đổi kích thước code cache

```bash
java -XX:ReservedCodeCacheSize=64m -XX:+PrintCodeCache CodeCacheDemo
```

Con số `64m` là **tham số thí nghiệm**, không phải khuyến nghị cấu hình production. Nếu một số CodeHeaps không đủ dung lượng hoặc flag không tương thích với chế độ JVM, cấu hình có thể lỗi hoặc thay đổi cách segmentation. So sánh **code cache size/used**, **compilation behavior** và hiệu năng giữa các lần chạy, không chỉ dùng một phép đo thời gian đơn lẻ.

**Không cố tình đặt code cache cực nhỏ để làm JVM thất bại** khi đang chạy ứng dụng quan trọng. Thử nghiệm code cache full nên tiến hành riêng trong môi trường test được phép restart, với logs/metrics và khả năng trở về cấu hình ổn định.

## 13. Quan sát code cache của tiến trình đang chạy bằng `jcmd`

`jcmd` là công cụ chẩn đoán JDK, có thể gửi yêu cầu tới **JVM process khác đang chạy** nếu quyền hạn/attach được phép.

Liệt kê tiến trình:

```bash
jcmd -l
```

Sau khi xác định PID thực sự của Java process cần kiểm tra:

```bash
jcmd <PID> help
jcmd <PID> Compiler.codecache
jcmd <PID> Compiler.codelist
jcmd <PID> Compiler.queue
```

Trong lệnh, **`<PID>` là placeholder cho mã tiến trình thật**, ví dụ PID hiển thị bởi `jcmd -l`. Phải thay nó bằng số thích hợp, không sao chép nguyên `<PID>` vì shell có thể hiểu dấu `<` và `>` là cú pháp điều hướng.

| Diagnostic command | Tác dụng |
| --- | --- |
| `Compiler.codecache` | In layout/ranh giới và trạng thái code cache |
| `Compiler.codelist` | Liệt kê compiled methods còn alive trong code cache (có thể dài) |
| `Compiler.queue` | Cho biết methods đang chờ compilation |
| `help` | Xác minh commands JVM đó hỗ trợ |

Có thể dùng thêm `Compiler.CodeHeap_Analytics` trên những HotSpot có hỗ trợ, nhưng hãy **kiểm tra `jcmd PID help` trước** và cân nhắc độ dài output/chi phí chẩn đoán của production process.

**Những giới hạn thực tế:**

- `jcmd` thường cần chạy trên **cùng máy** với Java process và dưới **quyền hệ điều hành thích hợp**.
- Container/PID namespaces hoặc attach settings có thể ảnh hưởng khả năng liệt kê/đính kèm.
- Một JVM ngoài HotSpot hoặc bản JDK khác có thể không cung cấp cùng các commands.
- `CodeCacheDemo` chạy quá nhanh để bạn nhập lệnh `jcmd` trong khi còn chạy; dùng ứng dụng/service đang hoạt động hoặc một tiến trình test tồn tại đủ lâu để quan sát.

`jcmd Compiler.codecache` cho số liệu live, hữu ích hơn việc cố suy ra code cache chỉ từ `javap` output.

## 14. Quan sát theo JMX `MemoryPoolMXBean`

HotSpot có thể công bố các **non-heap memory pools** cho code cache thông qua Java Management Extensions (JMX). Tên pool có thể là `Code Cache` hoặc bắt đầu bằng `CodeHeap`, tùy JVM/segmentation. API `MemoryPoolMXBean` cho phép lấy các số như `used`, `committed`, `max` khi JVM báo được.

Tạo file **`CodeCachePools.java`**:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

public class CodeCachePools {
    public static void main(String[] args) {
        boolean found = false;
        for (MemoryPoolMXBean pool :
                ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName();
            boolean codePool = name.equals("Code Cache")
                    || name.startsWith("CodeHeap");

            if (!codePool || pool.getType() != MemoryType.NON_HEAP) {
                continue;
            }

            found = true;
            System.out.println("pool: " + name);
            MemoryUsage usage = pool.getUsage();
            if (usage == null) {
                System.out.println("usage: unavailable");
            } else {
                System.out.println("used(bytes): " + usage.getUsed());
                System.out.println("committed(bytes): "
                        + usage.getCommitted());
                System.out.println("max(bytes): " + usage.getMax());
            }
        }
        System.out.println("code pools found: " + found);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CodeCachePools.java
java CodeCachePools
```

**Output phụ thuộc JVM:** ví dụ trên HotSpot có phân đoạn, các tên pool có thể là `CodeHeap 'non-nmethods'`, `CodeHeap 'profiled nmethods'`, `CodeHeap 'non-profiled nmethods'`, kèm những số byte khác nhau. Không đưa một bộ số cụ thể vì **tải, cấu hình, JDK và thời điểm đo** sẽ làm chúng thay đổi.

Đọc kết quả:

- `used(bytes)`: dung lượng được pool báo hiện dùng, **không nhất thiết là số bytes mã máy thuần**.
- `committed(bytes)`: dung lượng đã committed theo API.
- `max(bytes)`: giới hạn pool báo, có thể mang giá trị `-1` nếu không xác định theo hợp đồng `MemoryUsage`.
- `code pools found: true`: thường gặp trên HotSpot JDK 17; `false` có thể xảy ra trên JVM không expose pool cùng tên, vì tên pool **không chuẩn hóa** cho mọi triển khai.

Nếu muốn monitoring lâu dài, lấy samples định kỳ qua JMX metrics/exporter hoặc dùng công cụ observability phù hợp; một lần đọc ngay lúc JVM vừa khởi động thường chưa phản ánh code cache của service sau warm-up.

## 15. Native Memory Tracking (NMT) và RSS: liên hệ nhưng không đồng nghĩa

HotSpot **Native Memory Tracking** có thể giúp quan sát native memory theo nhóm khi được bật ngay từ lúc JVM khởi động:

```bash
java -XX:NativeMemoryTracking=summary CodeCacheDemo
```

Lệnh này **chỉ bật NMT cho tiến trình demo**, không tự in mọi thống kê NMT nếu chưa có yêu cầu. Với tiến trình đang chạy và có NMT, bạn có thể dùng:

```bash
jcmd <PID> VM.native_memory summary
```

Trong output có thể có nhóm **Code** với reserved/committed. Tuy nhiên NMT là công cụ hạch toán native memory **theo phân loại của HotSpot**, không phải chương trình phân tích chi tiết từng `nmethod`. Các nhóm, cách thống kê và chi phí NMT phụ thuộc chế độ `summary`/`detail` và phiên bản JVM.

**Khác biệt quan trọng:**

- **`-Xmx`** giới hạn Java heap, không kiểm soát trực tiếp toàn bộ native footprint.
- **NMT Code** giúp thấy vùng native liên quan đến code được JVM ghi nhận.
- **Code cache pool metrics** giúp thấy mức dùng của các CodeHeaps.
- **RSS của process** được OS đo theo physical memory resident và còn chứa nhiều phần khác.

Không so NMT `reserved` với container RSS rồi kết luận có “rò rỉ ảo” nếu chưa hiểu virtual reservation và committed pages.

## 16. Khi nào nên tăng `ReservedCodeCacheSize`?

Chỉ cân nhắc sau khi **đo** có dấu hiệu code cache thật sự là nút thắt.

Một quy trình điều tra:

1. **Ghi nhận JVM thực tế:** `java --version`, vendor, HotSpot build, flags, chế độ tiered compilation.
2. **Quan sát code cache theo thời gian:** dùng `jcmd Compiler.codecache`, JMX pools hoặc monitoring của hệ thống.
3. **Xem compiler activity:** `Compiler.queue`, compile logs, cảnh báo `CodeCache is full`, code sweeper/flushing.
4. **Phân loại memory:** Java heap, metaspace, direct/native buffers, thread stacks và code cache; tránh sửa nhầm vùng.
5. **Xác định CodeHeap bị áp lực:** profiled/non-profiled/non-method hoặc code cache không phân đoạn.
6. **Thử cấu hình trong staging:** tăng code cache một mức hợp lý, giữ cùng workload, warm-up, giới hạn container.
7. **Đo lại throughput, latency và memory footprint:** xác nhận có cải thiện thay vì chỉ nhìn một số used giảm.
8. **Theo dõi dài hạn:** code cache có thể đạt plateau, hoặc thay đổi do class loading động, warm-up và compilation lifecycle.

### Các tình huống thường cần chú ý

- Server chạy lâu và có **rất nhiều Java methods** được gọi/biên dịch.
- Framework tạo nhiều classes lúc runtime hoặc class loaders/plugin systems, khiến số compiled methods thay đổi đáng kể.
- Ứng dụng dùng dynamic proxies, generated bytecode, expression engines, ORM hoặc rule engines với nhiều đường code.
- Chế độ tiered compilation tạo các versions khác nhau của methods và nhiều compiled code theo thời gian.
- Kích thước code cache đã bị cấu hình quá nhỏ so với load thực tế.

**Không có một kích thước code cache tối ưu phổ quát.** App nhỏ chạy một tác vụ rồi thoát khác hẳn server nhiều giờ với hàng nghìn endpoints. Cấu hình phù hợp phải cân bằng CPU, compiler threads, native memory budget, startup warm-up và hiệu năng ổn định.

## 17. Các hiểu nhầm thường gặp

| Hiểu nhầm | Điều đúng |
| --- | --- |
| “`.class` đã chứa mã x86 nên được copy vào code cache.” | ClassFile chuẩn chứa **JVM bytecode**; JIT có thể tạo mã native cho CPU đang chạy |
| “Code cache là Java heap.” | Nó là vùng native executable code, thường được HotSpot báo trong non-heap pools |
| “`-Xmx` càng lớn thì code cache càng lớn.” | `-Xmx` điều khiển heap; code cache có flags/ergonomics riêng |
| “Mỗi Java object có một mã máy copy riêng.” | Nhiều instances cùng class/method dùng chung compiled code, không tạo code copy cho từng object |
| “Mọi Java method đều được JIT compile.” | Interpreter có thể xử lý cold code; JIT dùng heuristics |
| “Một method chỉ có duy nhất một bản compiled code.” | Có thể có C1/C2 versions, OSR nmethods hoặc recompile |
| “Code cache chỉ chứa nmethods.” | HotSpot có thể chứa stubs, adapters, interpreter-related code và CodeBlobs khác |
| “Mã đã biên dịch không thể bị xóa.” | Code sweeping, deopt, unloading có thể làm code không còn hợp lệ và được thu hồi |
| “ReservedCodeCacheSize 240m nghĩa là JVM đang dùng 240 MB RAM.” | Reserved virtual address space không đồng nghĩa committed hay RSS |
| “Code cache full chắc chắn làm JVM exit ngay.” | HotSpot có thể disable/giới hạn compilation và chương trình vẫn chạy nhưng chậm; hành vi cụ thể tùy tình huống |
| “`javap -c` hiển thị nội dung code cache.” | `javap` hiển thị JVM bytecode, không phải live JIT machine code |
| “Có đúng ba CodeHeaps trên mọi JVM.” | Phân đoạn/triển khai phụ thuộc HotSpot configuration, version và flags |
| “Chạy `System.gc()` là xóa hết code cache.” | GC quản lý heap objects; compiled-code lifecycle có cơ chế riêng và điểm phối hợp |
| “NMT Code reserved bằng RSS.” | Hai chỉ số có định nghĩa, phạm vi và cách hạch toán khác nhau |
| “Một benchmark chạy 1 lần là đủ để chọn code cache size.” | Cần warm-up, monitoring và so sánh theo tải/phiên bản có kiểm soát |

## 18. Bài tập có đáp án

### Bài 1 — Xác định vùng bộ nhớ

Một chương trình Java tạo một `new long[10_000]` và chạy method tính toán nhiều lần. **Giá trị mảng và mã máy JIT nằm đâu?**

**Đáp án:** Array object nằm trên **Java heap**; nếu method được JIT biên dịch, native code tương ứng có thể nằm trong **code cache**. Mảng không “di chuyển sang code cache” vì method của nó trở nên hot.

### Bài 2 — Phân biệt bytecode với machine code

`javap -c Example` hiển thị `imul`. Điều đó có chứng minh code cache có instruction x86 `imul` không?

**Đáp án:** **Không.** `imul` ở đây là opcode JVM. JIT có thể tạo native instruction theo CPU và optimization; có thể inline hoặc chọn cách tính tương đương khác.

### Bài 3 — Sizing

Ứng dụng có Java heap `-Xmx512m`, code cache reserved 128 MB. Có phải tổng RSS tối đa luôn bằng 640 MB?

**Đáp án:** **Không.** Ngoài heap và code cache còn metaspace, thread stacks, GC/native allocations và các vùng khác; reserved code cache còn khác committed/RSS.

### Bài 4 — Tiered code

Cùng một method có thể tạo C1 code và sau đó C2 code không?

**Đáp án:** **Có**, nếu policy/heuristics lựa chọn; không phải tất cả methods đều đi đủ các tiers.

### Bài 5 — OSR

Một method chỉ được gọi một lần nhưng chạy một vòng lặp cực dài có cơ hội chạy mã JIT không?

**Đáp án:** **Có thể.** JVM có thể dùng **OSR** để biên dịch/chuyển vào compiled loop khi vòng lặp trở nên hot.

### Bài 6 — Phân đoạn

Khi `CodeHeap 'profiled nmethods'` áp sát giới hạn nhưng `non-profiled nmethods` còn nhiều chỗ, có thể kết luận code cache không có vấn đề?

**Đáp án:** **Không.** Phải xét khả năng cấp phát theo từng CodeHeap, segmentation và flushing policy của phiên bản HotSpot.

### Bài 7 — Code cache full

Sau khi thấy log “CodeCache is full”, có nên tăng `-Xmx` trước không?

**Đáp án:** **Không mặc định.** Kiểm tra code cache metrics và compiler activity trước. Tăng Java heap không trực tiếp giải quyết code cache capacity.

### Bài 8 — Quan sát thực tế

Muốn biết code cache layout của một Java process đang chạy, công cụ nào phù hợp?

**Đáp án:** Với HotSpot JDK 17, dùng `jcmd PID Compiler.codecache` (kiểm tra `jcmd PID help` trước), kết hợp `Compiler.queue`, JMX hoặc logs phù hợp.

### Bài 9 — Độ tin cậy của số liệu

`CodeCachePools` in `max(bytes)=-1`. Có chứng minh code cache không giới hạn không?

**Đáp án:** **Không.** Trong hợp đồng `MemoryUsage`, `-1` có nghĩa **không xác định theo API**; nên xem JVM flags và công cụ chẩn đoán chuyên biệt.

### Bài 10 — Lifecycle

GC thu gom một object `User` thì nmethod `User.getName()` chắc chắn lập tức bị xóa?

**Đáp án:** **Không.** Compiled method gắn với runtime code/class dependencies, không phải mỗi instance. Điều kiện dọn code và class unloading phức tạp hơn việc một object bị GC.

### Bài 11 — Biểu hiện bottleneck

Nếu ứng dụng chậm hơn sau warm-up, nhưng code cache chỉ dùng 10% capacity, có nên đổ lỗi cho code cache full?

**Đáp án:** **Chưa có bằng chứng.** Cần đo JIT, GC, locks, I/O, CPU profiling, memory pressure; code cache 10% không tự giải thích slowdown.

### Bài 12 — Phân biệt VM-specific

Mọi JVM implementer có bắt buộc hỗ trợ `-XX:ReservedCodeCacheSize` không?

**Đáp án:** **Không.** Đây là option thuộc hệ sinh thái HotSpot. JVM Specification quy định ngữ nghĩa bytecode/runtime, không chuẩn hóa cấu trúc CodeHeap/flag HotSpot.

## 19. Checklist khi điều tra code cache

- Có xác nhận JVM vendor/version và `TieredCompilation`/segmentation chưa?
- Đã tách Java heap, metaspace, code cache và các native allocations khác chưa?
- Đã lấy thống kê `used`/`committed`/`reserved` từ đúng công cụ chưa?
- Đã xem từng CodeHeap khi segmentation bật chưa?
- Có bằng chứng compiler bị hạn chế, queue cao hoặc `CodeCache is full` không?
- Đã so sánh workload sau warm-up và trước/sau thay đổi cấu hình chưa?
- Đã xác nhận tổng native memory vẫn phù hợp container/OS limit chưa?
- Có kế hoạch quay về cấu hình cũ nếu performance regression không?
- Có hiểu số liệu đo được từ JMX/NMT/`jcmd` có phạm vi khác nhau không?

**Ghi nhớ cuối:** code cache là nơi **HotSpot lưu và quản lý native executable code do JVM tạo**, chứ không chứa Java objects hay bytecode nguồn theo cách hiểu thông thường. Hiệu năng liên quan code cache phụ thuộc việc JIT compile, tiered levels, OSR, deoptimization, code sweeping, phân đoạn và khả năng cấp phát. Quan sát bằng **`-XX:+PrintCodeCache`, `-XX:+PrintCompilation`, `jcmd Compiler.codecache`, JMX và NMT** trước khi chỉnh sizing. Một code cache đủ chỗ không bảo đảm ứng dụng nhanh; một code cache đầy có thể làm JIT mất cơ hội tối ưu dù Java heap vẫn còn.

## Tài liệu tham khảo

- **OpenJDK — JEP 197: Segmented Code Cache:** https://openjdk.org/jeps/197/
- **Oracle JDK 17 — The java Command:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html (tùy chọn HotSpot như `ReservedCodeCacheSize`, `SegmentedCodeCache`, `PrintCompilation`).
- **Oracle JDK 17 — The jcmd Command:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html (các commands `Compiler.codecache`, `Compiler.codelist`, `Compiler.queue`).
- **OpenJDK HotSpot source — CodeCache:** https://github.com/openjdk/jdk/blob/master/src/hotspot/share/code/codeCache.hpp và https://github.com/openjdk/jdk/blob/master/src/hotspot/share/code/codeCache.cpp (chi tiết implementation có thể thay đổi theo branch/release).
- **Java SE 17 — `MemoryPoolMXBean`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html (giao diện quản lý memory pools ở mức API).

Tài liệu OpenJDK/HotSpot nói về **một triển khai JVM cụ thể**; khi làm việc với OpenJ9, GraalVM hoặc một JVM khác, cần dùng tài liệu/options tương ứng thay vì mặc nhiên áp các flags và cấu trúc CodeHeap của HotSpot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-008">Bộ nhớ ngoài heap: direct buffer, thư viện native và cấu trúc nội bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Bộ nhớ ngoài heap: direct buffer, thư viện native và cấu trúc nội bộ JVM

## 1. Java heap không phải toàn bộ bộ nhớ của tiến trình Java

Trong JVM, **Java heap** là vùng mà garbage collector thường quản lý để chứa những Java object và array thông thường. Nhưng tiến trình Java còn cần nhiều loại memory khác: nội dung **direct buffers**, dữ liệu cấp phát bởi **native libraries**, metadata của class, mã máy do JIT compiler sinh, native thread stacks, các cấu trúc nội bộ GC, những vùng file được map và vùng nhớ do OS/runtime sử dụng.

**Off-heap memory** thường chỉ dung lượng nằm ngoài Java heap do GC quản lý trực tiếp. Trong thực tế, thuật ngữ có hai phạm vi cần tách rõ:

- **Application off-heap:** byte storage cho NIO direct buffers, memory từ JNI/JNA/FFI hoặc thư viện native khác; ứng dụng cần hiểu owner, scope và cleanup của từng nguồn.
- **Native/non-heap process memory:** phạm vi rộng hơn, gồm cả vùng nội bộ JVM như HotSpot Metaspace, code cache, native thread stacks, GC work areas, shared libraries và memory mappings.

Không đồng nhất hai phạm vi trên. **Một direct buffer không phải là toàn bộ native memory** và các native allocation không nhất thiết nằm dưới cùng một quota.

~~~text
Java process (sơ đồ khái niệm, không phải layout địa chỉ thật)

  Java heap (GC quản lý object)
  ├── Business objects, arrays, String...
  ├── HeapByteBuffer wrapper ────> byte[] trong Java heap
  └── DirectByteBuffer wrapper ──┐
                                 │
  Bộ nhớ ngoài Java heap         │
  ├── Nội dung direct buffer <───┘
  ├── Native C/C++ allocations từ JNI/JNA
  ├── Memory-mapped file regions
  ├── Class metadata / Metaspace (HotSpot)
  ├── JIT code cache
  ├── Native thread stacks
  └── GC/runtime/internal structures
~~~

**Thông điệp quan trọng:** `ByteBuffer.allocateDirect(n)` trả về một **Java object `ByteBuffer`** được quản lý như object khác; phần **nội dung byte** mà nó biểu diễn thường được lưu ngoài heap GC thông thường. Không nói “cả DirectByteBuffer object nằm ngoài heap”.

### Ngoài heap không có nghĩa ngoài RAM

Native allocations thường nằm trong **không gian địa chỉ ảo** của process. OS ánh xạ những trang tương ứng tới physical RAM hoặc file backing khi cần. `virtual reserved`, `committed` và `resident (RSS)` là những đại lượng khác nhau. Một JNI pointer thông thường là địa chỉ ảo, **không phải địa chỉ vật lý RAM**, và Java không có API portable để đọc địa chỉ vật lý của một object.

## 2. Tại sao cần off-heap và khi nào không nên dùng?

Các lý do chính:

1. **Native I/O:** direct buffer cho phép JVM cố gắng dùng dữ liệu byte trực tiếp cho một số thao tác I/O hệ điều hành, giảm copy qua buffer trung gian.
2. **Tích hợp native:** thư viện mã hóa, nén, cơ sở dữ liệu, xử lý hình ảnh, networking hoặc thư viện thiết bị có thể cấp phát memory bằng C/C++.
3. **File mapping:** đọc/ghi file lớn theo trang nhờ cơ chế memory-mapped file, đôi khi có lợi với random access.
4. **Nhu cầu của chính VM:** class metadata, compiled code, thread stacks và GC bookkeeping luôn tiêu thụ tài nguyên ngoài Java heap theo implementation tương ứng.
5. **Kiểm soát vòng đời dữ liệu:** một số hệ thống cần buffer pools với giới hạn dung lượng/ownership rõ, giảm churn của heap arrays.

Nhưng **direct không tự đồng nghĩa nhanh hơn**, **off-heap không tự đồng nghĩa zero-copy** và **thay arrays bằng direct buffers không loại bỏ GC**. API Java SE 17 mô tả việc cấp/giải phóng direct buffer thường tốn chi phí hơn heap buffer; chỉ nên dùng khi workload có lợi ích đo được.

| Đặc tính | Heap ByteBuffer | Direct ByteBuffer |
| --- | --- | --- |
| Tạo bằng | `ByteBuffer.allocate(n)`, `wrap(byte[])` | `ByteBuffer.allocateDirect(n)` |
| `isDirect()` | `false` | `true` |
| Java wrapper | Có | Có |
| Nơi nội dung byte nằm | Thường trong Java `byte[]` | Có thể nằm ngoài heap GC thông thường |
| Dùng backing array qua `array()` | Có backing array với `allocate` | Không mặc định được hỗ trợ; kiểm tra `hasArray()` |
| Native I/O | Có thể cần intermediate copy | JVM best effort để giảm intermediate copy |
| Thời điểm memory được thu hồi | GC của heap backing array khi unreachable | GC/reference processing/native cleanup tùy implementation |
| Chỉ định ownership | Thông thường theo Java object reachability | Cần quan tâm thêm pool/view/native user lifetime |

## 3. Cách sử dụng `ByteBuffer` và ý nghĩa của các view

Các thao tác quan trọng trong Java NIO:

- `capacity()`: tổng sức chứa **byte** của buffer.
- `position()`: vị trí đọc/ghi tương đối tiếp theo.
- `limit()`: ranh giới hợp lệ của các thao tác tương đối.
- `remaining()`: số phần tử từ position tới limit.
- `put`, `get`, `putInt`, `getInt`: đọc/ghi nội dung theo offset logic.
- `order(ByteOrder.BIG_ENDIAN)` và `LITTLE_ENDIAN`: chọn thứ tự byte khi đọc/ghi primitive nhiều byte.
- `duplicate()`: tạo wrapper mới với trạng thái position/limit/mark riêng nhưng **chia sẻ nội dung**.
- `slice()` hoặc `slice(int, int)`: tạo view một vùng con mà **không copy** nội dung.
- `asReadOnlyBuffer()`: tạo read-only view; không làm storage vật lý trở nên immutable với những view khác có quyền ghi.

### Buffer state không phải lifetime của allocation

~~~text
0 ----- position ----- limit ----- capacity

clear():  position=0, limit=capacity
flip():   limit=position, position=0
rewind(): position=0
~~~

`clear()` **không zero-fill**, **không free native allocation**, **không xóa dữ liệu nhạy cảm**. `flip()` chủ yếu thay đổi con trỏ logic để có thể đọc dữ liệu đã ghi; nó không flush file và không gọi `free`. Khi pool tái sử dụng buffer, phải xử lý dữ liệu nhạy cảm và ownership riêng.

### `duplicate()`/`slice()` có thể làm memory sống lâu

Một view 100 byte từ direct allocation 64 MiB có thể tiếp tục giữ backing region lớn **reachable**. Điều này có thể làm direct-memory usage cao dù ứng dụng “chỉ giữ một slice nhỏ”. Khi cần giữ vài byte lâu, một bản copy độc lập đôi khi dễ quản lý hơn alias vào buffer lớn.

## 4. Ví dụ Java 17 hoàn chỉnh: direct, heap và aliasing

Tạo file **`DirectMemoryDemo.java`**:

~~~java
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class DirectMemoryDemo {
    public static void main(String[] args) {
        ByteBuffer heap = ByteBuffer.allocate(8);
        ByteBuffer direct = ByteBuffer.allocateDirect(8);

        heap.order(ByteOrder.BIG_ENDIAN);
        direct.order(ByteOrder.BIG_ENDIAN);
        heap.putInt(0, 0x01020304);
        direct.putInt(0, 0x01020304);

        System.out.println("heap.isDirect=" + heap.isDirect());
        System.out.println("direct.isDirect=" + direct.isDirect());
        System.out.println("sameInt=" +
                (heap.getInt(0) == direct.getInt(0)));

        ByteBuffer alias = direct.duplicate();
        alias.putInt(0, 0x0A0B0C0D);
        System.out.println("duplicateSharesBytes=" +
                (direct.getInt(0) == 0x0A0B0C0D));
        System.out.println("heapUnaffected=" +
                (heap.getInt(0) == 0x01020304));

        ByteBuffer firstFour = direct.slice(0, 4);
        System.out.println("slice.isDirect=" + firstFour.isDirect());
        System.out.println("slice.capacity=" + firstFour.capacity());
        System.out.println("sliceSharesBytes=" +
                (firstFour.getInt(0) == direct.getInt(0)));
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac --release 17 DirectMemoryDemo.java
java DirectMemoryDemo
~~~

**Stdout chính xác đã được biên dịch/chạy kiểm chứng bằng JDK 21 với target Java 17:**

~~~text
heap.isDirect=false
direct.isDirect=true
sameInt=true
duplicateSharesBytes=true
heapUnaffected=true
slice.isDirect=true
slice.capacity=4
sliceSharesBytes=true
~~~

Phân tích:

1. Heap buffer và direct buffer đều được cấp 8 byte, nhưng `isDirect()` khác nhau.
2. Lệnh `putInt(0, ...)` ghi bốn byte ở byte index 0; `sameInt=true` chỉ chứng minh **giá trị** đang bằng nhau, không phải hai buffers trỏ cùng địa chỉ.
3. `duplicate()` tạo view wrapper khác nhưng chia sẻ vùng byte. Ghi qua `alias` khiến `direct` thấy `0x0A0B0C0D`.
4. Buffer `heap` độc lập, không thay đổi.
5. `slice(0, 4)` giữ một view 4 byte và vẫn là direct, chứa bốn byte đang chia sẻ với `direct`.

Không được suy ra buffer trực tiếp có API chính thức trả **physical address**. Với `hasArray() == false`, gọi `array()` không được phép. Không dùng reflection/Unsafe đọc field private để suy đoán layout hay địa chỉ native ổn định.

## 5. Direct memory được thu hồi lúc nào?

Đối với `ByteBuffer.allocateDirect`, **Java 17 không cung cấp public `ByteBuffer.close()` hoặc `ByteBuffer.free()`**. Trong HotSpot và các JVM phổ biến, có những cơ chế cleaner/reference processing để quản lý native storage tương ứng khi Java wrapper và những liên kết giữ storage không còn cần thiết. Nhưng **thời điểm GC thực hiện cleanup không được bảo đảm**.

Những khẳng định cần tránh:

- **`direct = null`** không đồng nghĩa memory đã free; có thể còn view khác, queued I/O operation hoặc reference từ object khác.
- **`System.gc()`** chỉ là yêu cầu GC; JVM không bắt buộc reclaim đúng direct allocation ở một thời điểm xác định.
- **`clear()`** chỉ reset position/limit, không deallocate memory.
- **Tự gọi internal cleaner** bằng reflection/`sun.misc.Unsafe` không phải API portable; có nguy cơ use-after-free khi buffer/view/native code còn truy cập memory.
- **Direct memory do JNI cung cấp** không tự chứng minh JVM có trách nhiệm gọi native `free`.

Hãy phân biệt **managed direct allocation** do runtime Java kiểm soát với **native allocation ứng dụng sở hữu**. Nếu native code tự cấp memory và đưa pointer cho Java, ownership/lifetime phải được định nghĩa rõ. Memory phải còn hợp lệ đến khi mọi user đã hoàn tất; sau đó đúng chủ sở hữu mới free.

### Khi cần lifetime xác định

Một API/library có `AutoCloseable` hoặc lifetime scope phù hợp có thể giúp thu hồi tài nguyên chủ động. Java 17 có các foreign-memory API từng ở trạng thái incubating, trong khi cú pháp/contract của FFM ở những JDK mới hơn thay đổi nhiều. **Không chép ví dụ FFM của bản JDK mới vào dự án Java 17 và mặc định nó compile.**

Nếu triển khai wrapper native handle `AutoCloseable`, phải bảo đảm:

1. `close()` không double-free.
2. Không có async operation đang giữ pointer khi close.
3. Native callback/worker được dừng trước deallocation.
4. Không gửi pointer đã free cho Java wrapper hoặc thread khác.
5. Exception trong code Java vẫn đi qua cleanup `try-with-resources`/`finally`.
6. Thư viện có quy tắc đồng bộ khi hai thread cùng truy cập vùng bytes.

## 6. `-Xmx` và `-XX:MaxDirectMemorySize` là hai giới hạn khác nhau

Các tùy chọn HotSpot/JDK 17 thường gặp:

~~~bash
java -Xmx512m -XX:MaxDirectMemorySize=256m DirectMemoryDemo
~~~

- `-Xmx` là giới hạn max **Java heap** theo cơ chế JVM/HotSpot.
- `-XX:MaxDirectMemorySize` đặt giới hạn tổng cho các **NIO direct-buffer allocations** được tính theo cơ chế HotSpot.
- Không được cộng hai flag rồi gọi tổng đó là **mọi memory của process**.
- JNI/JNA native `malloc`, thread stacks, Metaspace, JIT code cache, GC structures và memory mappings có những cơ chế cấp phát/quota khác.
- Native allocator overhead và alignment khiến logical buffer capacity không nhất thiết đúng bằng native bytes đã reserve/commit.
- Flag `-XX:` thuộc VM implementation, **không bảo đảm mọi Java runtime đều hỗ trợ/diễn giải giống nhau**.

### OOME vì direct memory không giống OOME vì Java heap

Một HotSpot có thể ném `OutOfMemoryError` với thông báo dạng `Cannot reserve ... bytes of direct buffer memory` khi không thể reserve theo giới hạn/quỹ native buffer. Thông báo cụ thể phụ thuộc version/implementation, **không phải API contract để parse**.

`OutOfMemoryError: Java heap space` lại là tình huống khác. Nếu heap còn dư nhưng native memory cạn hoặc container vượt limit, tăng `-Xmx` thậm chí có thể khiến tổng memory pressure tệ hơn.

### Container memory limit

Nếu container giới hạn 1 GiB, không nên đặt `-Xmx` sát 1 GiB rồi quên direct buffers, thread stacks, class metadata, code cache và native libraries. OS/cgroup OOM killer có thể dừng process **không có Java stack trace**, khác với exception `OutOfMemoryError` do JVM ném.

Không có tỷ lệ heap/native universal áp dụng cho mọi service. Memory budget phải dựa vào workload: concurrent requests, payload sizes, connection buffers, số threads, số class loaders, library allocations và GC/JIT runtime footprint.

## 7. Memory-mapped files: `MappedByteBuffer`

`FileChannel.map(...)` tạo `MappedByteBuffer`, một loại direct buffer dùng memory mapping của file. OS quản lý các trang ánh xạ; **không phải file 10 GiB chắc chắn resident 10 GiB RAM ngay sau map**.

Một đoạn **minh họa, không phải file Java hoàn chỉnh**:

~~~java
try (FileChannel channel = FileChannel.open(
        Path.of("data.bin"),
        StandardOpenOption.READ,
        StandardOpenOption.WRITE)) {
    MappedByteBuffer mapped = channel.map(
            FileChannel.MapMode.READ_WRITE, 0, 4096L);
    mapped.putInt(0, 123);
    mapped.force();
}
~~~

Muốn chạy đoạn này cần imports `java.nio.*`, `java.nio.channels.*`, `java.nio.file.*`, file hợp lệ và quyền truy cập phù hợp. Việc map vùng file vượt kích thước file/các giới hạn tương ứng phải tuân theo contract `FileChannel.map` và OS.

Những phân biệt bắt buộc:

- `mapped.isDirect()` là `true`, nhưng mapping khác với direct buffer cấp bytes bằng `allocateDirect`.
- `mapped.force()` có ý nghĩa **yêu cầu đẩy thay đổi tới file/storage** theo API; không có nghĩa free hoặc unmap.
- `FileChannel.close()` đóng channel **không đồng nghĩa mapping biến mất tức thời** nếu mapped buffer vẫn sống.
- OS có thể page-fault khi truy cập; file mapping memory accounting khác native heap allocations.
- Thay đổi/truncate file từ nơi khác khi mapping đang sử dụng có thể khiến một số vùng không còn truy cập hợp lệ; cần quản lý file ownership.
- Việc delete/rename file khi còn mapped region có thể khác giữa các hệ điều hành, đáng chú ý ở Windows.
- Buffer pools thường phân biệt `direct` và `mapped`; đừng trộn chúng khi đo đạc.

Map file hữu ích cho một số workload ngẫu nhiên/lớn; không chắc nhanh hơn `FileChannel.read()` cho mọi file, mức cache hay pattern I/O.

## 8. Native libraries: JNI/JNA, native allocators và pointers

Mã Java có thể làm việc với C/C++ hoặc hệ thống bên ngoài bằng JNI, JNA hay cơ chế foreign-function phù hợp phiên bản JDK. Những thư viện này có thể tự sử dụng:

- `malloc`, `calloc`, `realloc`, `free` trong C runtime.
- `mmap`/OS virtual memory allocations.
- Native sockets, file descriptors, GPU/device buffers, crypto/compression contexts.
- Native-created threads, native stacks.
- JNI global references giữ Java object/class.
- Direct buffers biểu diễn vùng native memory.

Vì vậy Java heap dump có thể trông bình thường trong khi C/C++ heap leak khiến process RSS tăng mạnh.

### 8.1. JNI method không tự tạo memory ownership

~~~java
public final class NativeCodec {
    static {
        System.loadLibrary("codec");
    }

    public static native int compress(
            byte[] input, int inputLength);
}
~~~

Đây là **đoạn minh họa không chạy độc lập**: cần thư viện native thật, JNI implementation đúng signature, build ABI tương thích và cấu hình library path. Nó không đủ để tạo stdout.

Nếu implementation cấp phát temporary output buffer trong native memory, phải có nơi free đúng. JNI global reference cần được delete theo API khi hết dùng. Một global ref quên dọn có thể giữ Java object hoặc class loader sống ngoài dự kiến.

### 8.2. Native direct buffers không mặc định được JVM sở hữu

JNI có các API như `NewDirectByteBuffer` để Java xem một vùng native memory. **Không suy ra** JVM sẽ tự `free` vùng do thư viện ứng dụng cấp phát chỉ vì một `ByteBuffer` wrapper đã được tạo.

Hai lỗi nghiêm trọng:

1. **Use-after-free:** native code free vùng bytes khi Java buffer vẫn còn dùng.
2. **Double-free:** hai chủ sở hữu cùng free một vùng allocation.

Kết quả có thể là crash, dữ liệu sai, corrupt memory hoặc vulnerability, không nhất thiết Java exception đẹp. Trong đa luồng, phải có quy tắc memory visibility/atomicity bên cạnh lifetime.

### 8.3. Direct buffer không chứng minh zero-copy

JVM có thể tránh **một số** intermediate copies khi đưa direct buffer vào native I/O. Nhưng giữa application, TLS, compression, network kernel, device drivers và file systems vẫn có thể có copies. **Zero-copy là tính chất của toàn bộ data path**, không phải bảo đảm chỉ dựa vào `isDirect()`. Hãy đo syscall, throughput và CPU trước/sau.

## 9. Cấu trúc native/non-heap nội bộ JVM

Ở góc nhìn HotSpot, nhiều vùng memory khác ngoài Java heap:

| Vùng hoặc cơ chế | Vai trò | Dấu hiệu/điểm cần đo |
| --- | --- | --- |
| **Metaspace / class metadata** | Class/interface/loader-related VM structures | Class loading count, unloading, metaspace stats, NMT |
| **JIT code cache** | Native code được compiler tạo ra | Code cache usage, compiler/code metrics, NMT |
| **Native thread stacks** | Stack/resource của platform threads | Thread count, `-Xss`, OS/native tracking |
| **GC internal structures** | Card tables, remembered sets, bitmap, runtime bookkeeping | GC logs, NMT categories |
| **JNI/JVMTI/native code** | Native library allocations, global refs, C/C++ data | Native profilers, heap retaining paths, OS metrics |
| **Direct NIO buffers** | Off-heap byte content, I/O | `BufferPoolMXBean`, direct memory quota |
| **Memory-mapped files** | OS mappings của file | Mapped pool, page-fault, process mappings |
| **Shared libraries/mappings** | Native executable/library code/data | OS mapping tools, process metrics |

**Metaspace là cơ chế của HotSpot**, không phải tên bắt buộc trong JVM Specification. Đặc tả JVM định nghĩa vùng abstract **method area**, thread stacks và heap; một JVM implementation khác có thể tổ chức cấu trúc thực tế khác.

### Metaspace và class loader leak

Nếu mỗi lần hot-reload tạo loader mới nhưng host giữ plugin object, thread context class loader, listener hoặc reflection cache, loader cũ có thể không đủ điều kiện GC/class unloading. Class metadata theo phiên tích lũy và có thể gây Metaspace OOME. Đây là một **vấn đề lifecycle của loaders/classes**, không phải vấn đề `ByteBuffer.allocateDirect`.

### Code cache

JIT compiler cần vùng native memory để lưu machine code của những method đã compile và cấu trúc liên quan. Kích thước/cache policy phụ thuộc VM flags và workload. JIT có thể deoptimize hoặc thu hồi/reuse code. Đừng gộp code cache vào Java heap used khi lập ngân sách.

### Thread stacks

Một process với hàng nghìn platform threads có thể reserve/commit native stack memory đáng kể. `-Xss` liên quan stack size trên HotSpot nhưng giảm quá thấp có thể làm `StackOverflowError` nhiều hơn; không thay thế việc sửa thread leak và bounded concurrency. Lỗi như `unable to create native thread` có thể do native memory, giới hạn số threads/processes hoặc OS resources, không nhất thiết vì Java heap đầy.

## 10. Theo dõi direct/mapped buffers bằng BufferPoolMXBean

Java SE 17 có interface `java.lang.management.BufferPoolMXBean`, thường cho các pool tên `direct`, `mapped`, và những pool khác tùy runtime.

Tạo file **`BufferStatsDemo.java`**:

~~~java
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;

public final class BufferStatsDemo {
    public static void main(String[] args) {
        for (BufferPoolMXBean pool :
                ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            System.out.printf(
                    "pool=%s count=%d capacity=%d used=%d%n",
                    pool.getName(),
                    pool.getCount(),
                    pool.getTotalCapacity(),
                    pool.getMemoryUsed());
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 BufferStatsDemo.java
java BufferStatsDemo
~~~

**Không có stdout dạng số cố định** cho chương trình này: số pool, buffer count và bytes thay đổi theo JVM/workload/timing. Ví dụ trên đã được biên dịch với `javac --release 17` và chạy bằng JDK 21; một lần chạy có pool `direct`, `mapped`, nhưng **không biến quan sát của lần chạy đó thành hợp đồng chung**.

| API | Ý nghĩa |
| --- | --- |
| `getName()` | Tên pool |
| `getCount()` | **Ước lượng** số buffers trong pool |
| `getTotalCapacity()` | **Ước lượng** logical capacity cộng dồn, theo byte |
| `getMemoryUsed()` | **Ước lượng** memory VM dùng cho pool; có thể khác capacity vì alignment/allocator |
| `getMemoryUsed() == -1` | JVM không có số ước lượng khả dụng |

Buffer pool stats **không phải thống kê tất cả JNI native malloc**, Metaspace, code cache hoặc thread stacks. Một số view như `slice()`/`duplicate()` có chung backing bytes; đừng suy luận số bytes resident bằng `count * size` nếu không biết phương thức accounting của implementation.

## 11. Native Memory Tracking (NMT) của HotSpot

NMT là công cụ quan sát nhiều **allocation nội bộ HotSpot VM** theo category như Thread, Class, Code, GC, Compiler và Internal. Nó có các chế độ `summary` và `detail` cùng overhead đáng cân nhắc.

Ví dụ bật NMT **ngay khi khởi động** ứng dụng thật:

~~~bash
java -XX:NativeMemoryTracking=summary -cp app.jar com.example.Main
~~~

Ở đây `app.jar` và `com.example.Main` **chỉ là tên minh họa**, cần thay bằng artifact và main class của ứng dụng.

Khi process có PID thực:

~~~text
jcmd PID VM.native_memory summary
jcmd PID VM.native_memory baseline
jcmd PID VM.native_memory summary.diff
~~~

`PID` ở đây chỉ chỗ thay bằng **số PID**, không gõ nguyên chữ `PID` nếu shell/application không có biến cùng tên.

Quy trình:

1. Khởi động JVM với `-XX:NativeMemoryTracking=summary` hoặc `detail`.
2. Ghi baseline lúc app ổn định.
3. Chạy workload có direct buffers/native code/class loading/threads.
4. Dùng `summary.diff` để xem category nào tăng.
5. Đối chiếu thêm `BufferPoolMXBean`, heap/GC logs, thread count và OS/process metrics.
6. Khi nghi third-party native leak, dùng **native allocator profiler** hoặc công cụ của thư viện tương ứng.

**Giới hạn quyết định:** Theo tài liệu Oracle JDK 17, NMT quan sát **JVM/HotSpot internal native memory**, nhưng **không bao quát toàn bộ memory do third-party native code và nhiều class-library allocations**. Vì vậy NMT không tăng **không đủ** để khẳng định C++ thư viện không leak.

Không mặc định NMT có thể bật bất cứ lúc nào trên một process đã chạy. Muốn NMT thu thập số liệu có ý nghĩa, cần bật khi JVM khởi động; có các lệnh `jcmd` để xem/baseline và một số thao tác quản lý NMT, nhưng không nên hứa bật lại một NMT chưa được cấu hình lúc startup.

## 12. Vì sao process RSS có thể lớn hơn heap used rất nhiều?

Một cách lập **memory budget** tương đối:

~~~text
Process memory footprint (mô hình gần đúng)
  Java heap resident pages
+ direct/native application buffers
+ Metaspace / class metadata
+ JIT compiled code / code cache
+ thread stacks
+ GC runtime structures
+ native libraries / device allocations
+ file mappings và OS-related pages
~~~

Đây **không phải** phép cộng tuyệt đối của các báo cáo memory khác nhau: các số liệu có thể double-count shared mappings hoặc đo reservation/commit/residency bằng đơn vị khác.

Phân biệt:

- **Heap used:** dung lượng GC nhìn thấy trong Java heap.
- **Heap committed:** dung lượng VM đã commit để phục vụ heap, không nhất thiết mọi trang đều resident vật lý.
- **Virtual reserved:** dải địa chỉ ảo đã đặt; không tương đương physical memory.
- **Process RSS:** các trang resident theo hệ thống đo đạc OS, có thể bao gồm shared pages.
- **Cgroup/container usage:** memory tính theo container accounting, có thể gồm những khoản khác process RSS.
- **NMT reserved/committed:** thông tin VM allocations theo categories, không bao gồm mọi native code bên ngoài.

Chỉ số nào phù hợp phụ thuộc câu hỏi bạn đang hỏi: Java object leak, direct buffer quota, native malloc leak, cgroup OOM, Metaspace tăng, hay thread stack pressure.

## 13. Chẩn đoán theo triệu chứng

### A. `OutOfMemoryError: Java heap space`

Kiểm tra Java heap allocations, live objects và GC. Đừng mặc định tăng `MaxDirectMemorySize` để sửa một heap OOME. Tuy nhiên cũng cần xem memory budget tổng khi điều chỉnh `-Xmx`.

### B. Direct buffer memory OOME

Có thể do:

- Pool không giới hạn; quá nhiều buffers đang sống.
- View (`slice`, `duplicate`) hoặc async request giữ backing allocations.
- Allocate quá thường xuyên làm reference cleanup chưa theo kịp.
- Direct-buffer quota quá nhỏ so workload thật.
- Native/OS memory pressure ngăn allocation.

Hướng xử lý: đo BufferPoolMXBean, total capacity, active requests, lifecycle buffer, quota và memory limit. **Không chỉ tăng limit rồi bỏ qua nguồn giữ references**.

### C. RSS tăng nhưng Java heap used ổn định

Nghi native allocations, mapped pages, direct buffers, Metaspace, JIT code hoặc nhiều native thread stacks. Bật NMT có kiểm soát, so BufferPoolMXBean và OS metrics; nếu NMT không bao phủ C/C++ memory, dùng native tools.

### D. Container OOMKilled, JVM không ném `OutOfMemoryError`

OS/cgroup có thể kill process trước khi JVM kịp log OOME/shutdown hooks. Kiểm tra container events, exit code và total resident/working set, thay vì chỉ xem heap GC.

### E. `unable to create native thread`

Kiểm tra thread count, executor leaks, OS process/thread limits, `-Xss` và native memory. Đừng vội tăng `-Xmx` khi Java heap chưa phải nguyên nhân.

### F. Metaspace OOME sau nhiều lần hot reload

Kiểm tra class-loader retention, ThreadLocal/TCCL, static caches, reflection metadata và class unloading. Direct buffer tuning không tự xử lý leak metadata của loaders.

### G. Native library crash

Khi có use-after-free, double-free, out-of-bounds hoặc ABI mismatch, JVM có thể crash cả process. Java heap dump không nhất thiết chỉ ra native allocation. Cần crash log, native debug/sanitizers trong môi trường an toàn, và kiểm tra ownership contract.

## 14. Thiết kế ứng dụng dùng off-heap an toàn

### 14.1. Bounded capacity và backpressure

Ví dụ mỗi request dùng một direct buffer 1 MiB và có 500 concurrent requests, riêng nội dung buffers có thể lên tới **khoảng 500 MiB**, trước allocator overhead, async retention và các vùng khác.

Nên giới hạn:

- Tổng số buffers.
- Tổng bytes direct capacity.
- Số requests/operations giữ buffers cùng lúc.
- Kích thước tối đa cho một buffer.
- Thời gian một buffer được giữ trong pool và timeout cho operations chưa trả buffer.
- Hành vi khi pool cạn: backpressure, queue bounded hoặc trả lỗi phù hợp thay vì cấp phát vô hạn.

### 14.2. Ownership và cleanup

~~~text
acquire / allocate
       |
       v
write bytes, pass to I/O/native operation
       |
       v
wait until operation REALLY completes
       |
       v
release back to bounded pool / close native owner
~~~

Đừng return buffer vào pool trong khi TLS/socket/async channel còn tham chiếu nó. Đừng giữ `slice` nhỏ làm toàn bộ region lớn sống lâu khi không cần. Đừng dùng `clear()` để xóa dữ liệu bí mật đã xử lý. Nếu dùng vùng native do thư viện sở hữu, chỉ close/free khi **mọi phía** đã kết thúc sử dụng.

### 14.3. Benchmark thay vì dựa vào tên API

So sánh heap/direct/mapped theo throughput, tail latency, CPU, GC pause, page fault và total process memory ở workload thực. Một `ByteBuffer.allocateDirect` lớn có thể phù hợp cho long-lived native I/O, nhưng hàng triệu direct allocations nhỏ sống ngắn có thể làm allocator overhead, cleanup cost và memory pressure tăng.

### 14.4. Giới hạn các quyền truy cập native

Native pointers hoặc foreign memory APIs có sức mạnh khác với Java memory-safe object access. Kiểm tra bounds, alignment, thread-safety, ownership và đặc quyền/module access. Tránh gọi internal APIs không được hỗ trợ để lấy địa chỉ memory hoặc ép cleanup của một direct buffer chưa chắc đã không còn user.

## 15. Sai lầm thường gặp

1. **“Off-heap nghĩa là không dùng RAM.”** Sai: đó là phạm vi memory ngoài Java heap, không nói trang có resident hay không.
2. **“DirectByteBuffer object ở ngoài Java heap.”** Sai: wrapper là Java object, byte storage mới thường off-heap.
3. **“Direct buffer luôn zero-copy.”** Sai: JVM cam kết best effort cho native I/O thích hợp.
4. **“`clear()` free native memory hoặc xóa dữ liệu.”** Sai.
5. **“`slice()` và `duplicate()` copy nguyên byte content.”** Sai: chia sẻ content.
6. **“Không còn reference gốc là buffer đã được free.”** Sai: có thể còn view hoặc I/O user.
7. **“`System.gc()` đảm bảo native cleanup ngay.”** Sai.
8. **“Mọi direct buffer Java 17 có `close()`.”** Sai: API ByteBuffer cơ bản không có.
9. **“`-Xmx` giới hạn tổng memory process.”** Sai.
10. **“`MaxDirectMemorySize` giới hạn mọi JNI `malloc`.”** Sai.
11. **“Mapped file chắc chắn vào RAM toàn bộ khi map.”** Sai.
12. **“`MappedByteBuffer.force()` là unmap.”** Sai.
13. **“Đóng FileChannel là mapped buffer đã unmap.”** Sai.
14. **“Metaspace là tên vùng bắt buộc trong mọi JVM.”** Sai: là implementation detail của HotSpot.
15. **“BufferPoolMXBean thống kê mọi native allocation.”** Sai.
16. **“NMT thống kê toàn bộ third-party native code.”** Sai.
17. **“RSS không giảm tức là chắc chắn leak.”** Sai: allocator/OS có thể giữ resident/committed capacity.
18. **“Native pointer là physical RAM address.”** Sai: thông thường là virtual address.
19. **“GC tự sửa mọi native lifetime lỗi.”** Sai: use-after-free có thể crash process.
20. **“Direct buffer nhanh hơn heap ở mọi workload.”** Sai: đo thực tế mới quyết định.

## 16. Câu hỏi tự kiểm tra

1. `ByteBuffer.allocate(16).isDirect()` là gì? **`false`**.
2. `ByteBuffer.allocateDirect(16).isDirect()` là gì? **`true`**.
3. `duplicate()` ghi byte, buffer gốc có thể thấy không? **Có** vì cùng content.
4. `clear()` zero-fill hay free memory không? **Không**.
5. Heap GC vẫn dùng khi có direct buffers không? **Có**, vì Java wrappers vẫn là objects.
6. `-Xmx1g` có ngăn process RSS vượt 1 GiB không? **Không**.
7. `-XX:MaxDirectMemorySize` có quota mọi JNI malloc không? **Không**.
8. Vì sao `getMemoryUsed()` khác `getTotalCapacity()`? **Overhead/alignment/cách đo**, cả hai là estimate.
9. `getMemoryUsed()` có thể bằng `-1` không? **Có**, khi không có estimate.
10. NMT có chứng minh không có C++ memory leak không? **Không**.
11. `MappedByteBuffer.force()` có thu hồi mapping không? **Không**.
12. Vì sao giữ slice 100 byte có thể giữ vùng 64 MiB? **Slice chia sẻ backing region**.
13. Thread count tăng mạnh làm native memory tăng do đâu? **Thread stacks** và runtime/thread resources.
14. Class-loader leak thường tác động vùng nào? **Class metadata/Metaspace**.
15. Vì sao `System.gc()` không là deadline cleanup? **Collector không bị buộc thực hiện đúng tại đó**.
16. Khi OOMKilled bởi container nhưng không có Java OOME, kiểm tra gì? **OS/cgroup memory limit và process footprint**.
17. Nếu native free vùng direct memory trong khi Java còn buffer wrapper, chuyện gì xảy ra? **Use-after-free có thể crash/corrupt data**.
18. Một giải pháp off-heap an toàn cần gì? **Bounded allocations, ownership, cleanup, backpressure và monitoring đa tầng**.

## 17. Tài liệu chuẩn

- Java SE 17 `ByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html
- Java SE 17 `MappedByteBuffer`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/MappedByteBuffer.html
- Java SE 17 `FileChannel`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/channels/FileChannel.html
- Java SE 17 `BufferPoolMXBean`: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/BufferPoolMXBean.html
- JDK 17 `java` launcher flags, gồm `MaxDirectMemorySize` và `NativeMemoryTracking`: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Oracle JDK 17 Native Memory Tracking: https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- Java SE 17 `System.gc`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#gc()
- Java SE 17 `Cleaner`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html
- JVMS 17 Run-Time Data Areas: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5

**Kết luận kỹ thuật:** **Java heap không phải toàn bộ JVM memory footprint.** `ByteBuffer.allocateDirect` tạo Java wrapper với byte storage có thể ngoài heap, native libraries có thể sở hữu allocations riêng, và bản thân JVM phải quản lý class metadata, compiled-code cache, stacks, GC structures. Đừng xem `-Xmx`, `MaxDirectMemorySize`, BufferPoolMXBean hay NMT là bức tranh đầy đủ một mình. Hãy thiết kế bounded buffers và lifetime rõ, theo dõi đồng thời heap/native/thread/class loading/OS metrics, và phân biệt GC của Java objects với việc thu hồi native resources.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-runtime-memory-areas-009">Phân biệt bộ nhớ đã dùng, đã cam kết, đã dành trước và bộ nhớ tiến trình</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt bộ nhớ đã dùng, đã cam kết, đã dành trước và bộ nhớ tiến trình trong JVM

Khi theo dõi ứng dụng Java, bạn có thể thấy **heap used = 400 MiB**, **heap committed = 768 MiB**, **heap max = 2 GiB**, trong khi hệ điều hành báo **RSS = 1,1 GiB** và **virtual memory = nhiều GiB**. Những số này **không mâu thuẫn**: chúng đang đo những khía cạnh khác nhau và đôi khi còn thuộc những phạm vi khác nhau.

Muốn phân tích đúng việc dùng bộ nhớ JVM cần phân biệt ít nhất năm câu hỏi:

1. **Used**: phần bộ nhớ JVM **đang được ghi nhận đã dùng** trong một vùng quản lý cụ thể là bao nhiêu?
2. **Committed**: JVM đã **cam kết khả năng sử dụng** bao nhiêu dung lượng cho vùng ấy?
3. **Reserved**: JVM đã **dành trước không gian địa chỉ ảo** bao nhiêu, có thể lớn hơn phần committed?
4. **Process memory**: tiến trình Java thực tế có những vùng bộ nhớ nào bên ngoài Java heap, và hệ điều hành ghi nhận resident/virtual memory ra sao?
5. **Limit/maximum**: giới hạn do Java heap, native allocations, hệ điều hành hoặc container đặt ra là giới hạn của **phần nào**, không phải một con số dùng chung?

Bài lấy **Java SE 17 và HotSpot/OpenJDK phổ biến** làm bối cảnh. Các định nghĩa Java API và JVM memory areas là phần đặc tả; những thuật ngữ của **Native Memory Tracking (NMT)**, RSS/VSZ, tham số `-Xms`/`-Xmx` và hành vi container phụ thuộc implementation/hệ điều hành. Cần đọc đúng nguồn và phiên bản trước khi so sánh.

## 1. Tổng quan: một tiến trình Java không chỉ có Java heap

Trong một JVM đang chạy, có thể tồn tại đồng thời:

- **Java heap**: vùng phục vụ cấp phát objects/arrays theo ngữ nghĩa JVM, được GC quản lý. Đây là phần thường được giới hạn bằng `-Xmx`.
- **Metaspace/class metadata**: dữ liệu lớp và metadata mà HotSpot quản lý bằng bộ nhớ native (từ Java 8 trở đi), không đơn giản nằm trong giới hạn heap `-Xmx`.
- **Thread stacks**: vùng liên quan đến stack của các threads, cùng tài nguyên native dành cho từng thread.
- **Code cache/JIT**: mã máy đã biên dịch và cấu trúc hỗ trợ compiler/runtime.
- **Direct buffers và native allocations**: ví dụ `ByteBuffer.allocateDirect`, JNI, thư viện C/C++, thư viện nén/mã hóa, các native allocators.
- **Memory-mapped files, shared libraries, file-backed mappings**: cách hệ điều hành ánh xạ nội dung file vào không gian địa chỉ của tiến trình.
- **Cấu trúc của JVM và các thành phần khác**: GC, symbol/string structures, class loaders, agents, profiler, allocator fragmentation, runtime libraries.

Một số thành phần trên được JVM quản lý, một số do thư viện native hoặc hệ điều hành quản lý. **Không phải tất cả đều có mặt trong `MemoryMXBean.getHeapMemoryUsage()`**, và không phải mọi byte process RSS đều thuộc heap.

```text
                        Tiến trình Java
+---------------------------------------------------------------+
| Không gian địa chỉ ảo của tiến trình (VSZ / virtual size)      |
|                                                               |
|    Java heap reserved                                         |
|      +--------------------------------------+                 |
|      | Heap committed                       |                 |
|      | +-----------------------+             |                 |
|      | | Heap used              |             |                 |
|      | +-----------------------+             |                 |
|      +--------------------------------------+                 |
|                                                               |
|    Metaspace / class metadata                                  |
|    Thread stacks / code cache / GC native / direct buffers     |
|    Shared libraries / mapped files / JVM runtime / native libs |
+---------------------------------------------------------------+
   ^
   | OS resident pages (RSS) là một phép đo KHÁC:
   | những pages hiện cư trú trong RAM của tiến trình tại thời điểm đo
   | không phải diện tích của hình chữ nhật trên.
```

Sơ đồ này là **mô hình khái niệm**, không phải sơ đồ chính xác các khoảng địa chỉ VM, thứ tự cấp phát hay cam kết trang vật lý của mọi JVM.

## 2. “Used” là gì?

Trong Java Management API, `java.lang.management.MemoryUsage` mô tả tình trạng của **một vùng bộ nhớ** bằng `init`, `used`, `committed`, `max`. Trường **`used`** là lượng bộ nhớ **được ghi nhận đã sử dụng trong vùng ấy** tại thời điểm chụp snapshot.

Ví dụ:

- **Heap used**: dung lượng heap đang có các allocations/objects và cấu trúc được vùng heap quản lý ghi nhận là đã sử dụng theo cách GC/JVM đo.
- **Non-heap used**: dung lượng các vùng non-heap mà API quản lý ghi nhận; **không phải** tổng toàn bộ native allocations của process.
- **Memory pool used**: lượng dùng của một pool cụ thể như một heap generation hoặc metaspace, với ngữ nghĩa và thời điểm cập nhật tùy pool.

### Used không hoàn toàn đồng nghĩa live objects

**Used có thể khác lượng bộ nhớ của các object còn sống sau một full GC.**

- Trước GC, heap có thể chứa các object đã **không còn reachable** nhưng chưa được reclaim. Chúng vẫn có thể ảnh hưởng used cho đến khi GC xử lý.
- Sau GC, mức used có thể giảm; không nên kỳ vọng mức giảm đúng bằng số bytes của các object đã mất reference.
- GC có thể có region, allocation buffer, overhead và các giới hạn hạch toán riêng. Không nên tính chính xác retained heap từ một con số used đơn lẻ.
- Muốn biết object nào giữ memory, dùng **heap histogram, heap dump hoặc profiler phù hợp**; muốn biết heap đang tăng hay giảm, theo dõi used theo **thời gian và qua các lần GC**.

Ví dụ ứng dụng có heap used tăng từ 350 lên 600 MiB rồi về 380 MiB sau GC không nhất thiết bị memory leak. Cần xem **đường xu hướng sau GC**, allocation rate, GC overhead và retained objects.

## 3. “Committed” là gì?

`MemoryUsage.getCommitted()` mô tả lượng bộ nhớ JVM **đã cam kết sẵn sàng sử dụng** cho vùng được báo cáo. Với Java heap, đây là mức dung lượng heap đang được JVM/GC quản lý và đã đưa vào phạm vi dùng được, thường nằm giữa used và max nếu max được xác định.

Bất biến cơ bản trong `MemoryUsage`:

```text
0 <= used <= committed

Nếu max >= 0:
    committed <= max
```

Đừng áp dụng bất biến ấy một cách mù quáng giữa **hai nguồn số liệu khác nhau**: heap used trong MXBean và NMT reserved của toàn process không phải bốn trường của cùng một `MemoryUsage` object.

### Committed không phải RSS

**Committed không đồng nghĩa mỗi byte đã có page vật lý resident trong RAM ngay lúc đo.**

Lý do:

- Hệ điều hành quản lý virtual memory và cấp phát/touch physical pages theo cơ chế của nó. Một khoảng memory có thể đã committed nhưng chưa được chạm tới hoặc đã bị paging.
- Runtime và OS có thể dùng các kỹ thuật như demand paging, transparent huge pages, swap hay lazy physical backing.
- Ngược lại, RSS có thể bao gồm **mmap file pages, native allocations, code cache, thread stacks** và thành phần khác ngoài Java heap.
- Vì vậy, heap committed = 800 MiB **không suy ra** process RSS phải đúng 800 MiB, lớn hơn 800 MiB hay nhỏ hơn 800 MiB trong mọi môi trường.

**Committed của JVM** cũng không nên đồng nhất máy móc với thuật ngữ **commit charge** trong công cụ Windows: hệ điều hành có một hệ thống hạch toán riêng, không cùng tập vùng và không nhất thiết đo cùng điều kiện.

### Committed trừ used nói điều gì?

Trong cùng một `MemoryUsage` snapshot:

```text
committed - used = phần dung lượng đã cam kết nhưng chưa được ghi nhận là đang dùng
```

Con số đó **không** cho biết:

- Hệ điều hành còn bao nhiêu RAM vật lý.
- JVM chắc chắn cấp phát được thêm đúng từng đó object bytes mà không GC.
- Số byte live objects sẽ còn lại sau GC.
- Bao nhiêu memory phải trả lại hệ điều hành nếu ngừng tạo object.

Đây là một phép chênh lệch hạch toán **bên trong vùng JVM**; việc cấp phát thực còn phụ thuộc layout, GC, contiguous allocation/region constraints và overhead.

## 4. “Reserved” là gì?

Ở mức virtual memory, **reserved** thường là không gian **địa chỉ ảo** mà runtime dành cho một mục đích như Java heap, thread stack hoặc code cache; **committed** là phần bên trong reservation được cam kết sử dụng.

Trên HotSpot, công cụ **Native Memory Tracking (NMT)** có thể hiển thị các categories với:

```text
reserved = ... KB
committed = ... KB
```

Điều quan trọng:

1. **Reserved không phải số RAM đã cấp thật**; dành địa chỉ ảo không có nghĩa đã có physical pages backing cho toàn bộ khoảng.
2. **Reserved có thể lớn hơn committed**; ví dụ heap có thể dành không gian địa chỉ gần heap maximum để có chỗ tăng sau này nhưng chỉ cam kết một phần.
3. **Committed có thể lớn hơn used** nếu vùng có metric used tương ứng; nhưng NMT không phải lúc nào cũng báo một trường used tương ứng với cùng ý nghĩa của heap MXBean.
4. **Reserved không đồng nghĩa mọi virtual address range của process**; NMT chỉ bao phủ bộ nhớ mà cơ chế theo dõi ấy ghi nhận, không phải bất kỳ mapping nào có thể xuất hiện.
5. Reservation lớn có thể khiến **VSZ/VmSize** trông cao, nhưng một giá trị VSZ lớn không tự chứng minh ứng dụng đang tiêu thụ nhiều RAM vật lý.

### Ví dụ số *giả định* để hiểu khái niệm

Giả sử ở một thời điểm, một ứng dụng có các số **minh họa**:

| Số đo | Giá trị giả định | Ý nghĩa |
| --- | ---: | --- |
| Heap max | 2.048 MiB | Giới hạn heap có thể dùng của cấu hình runtime |
| Heap reserved (NMT) | 2.048 MiB | Không gian địa chỉ ảo dành trước cho heap trong ví dụ |
| Heap committed | 768 MiB | Phần heap đã cam kết cho sử dụng |
| Heap used | 400 MiB | Phần heap đang được JVM ghi nhận đã dùng |
| Process RSS | 1.100 MiB | Tổng resident pages được hệ điều hành hạch toán cho tiến trình |
| Process virtual size | 6.000 MiB | Tổng không gian địa chỉ ảo được ánh xạ trong tiến trình |

Đây là **bộ số giả định để giảng giải**, không phải kết quả thực đo trên một JVM. Có thể kiểm tra `400 <= 768 <= 2048` trong heap metric, nhưng **không thể lấy `1100 - 768 = 332` rồi khẳng định chính xác đó là toàn bộ native memory**. RSS và heap committed không đo cùng điều, còn shared/file-backed mappings, unused committed pages và các hiệu ứng OS khiến phép trừ đó không có ý nghĩa kết toán chuẩn.

## 5. “Max” khác reserved và committed như thế nào?

Trong `MemoryUsage`, **`max`** là dung lượng lớn nhất vùng ấy có thể dùng theo contract của metric; **`-1`** nghĩa là giá trị tối đa **không xác định**.

Với Java heap trên HotSpot:

- `-Xmx` thường giới hạn **heap maximum**, chứ không phải toàn bộ resident memory/virtual memory của process.
- `-Xms` thường đặt **initial heap size** hoặc mức heap khởi đầu mà JVM quản lý.
- Runtime có thể **tăng/giảm committed heap** giữa mức đang dùng và cấu hình/giới hạn, tùy collector, flags và điều kiện OS.
- Heap **reserved** có thể liên quan vùng địa chỉ dành cho heap maximum, nhưng **không phải lời bảo đảm số NMT reserved luôn bằng đúng `-Xmx`** trong mọi build/GC.
- `max` của một non-heap memory pool có thể trả `-1`, hoặc có giới hạn riêng không liên quan `-Xmx`.

Vì vậy cách diễn giải:

```text
-Xmx2g
  không có nghĩa:
    process RSS <= 2 GiB
    process VSZ <= 2 GiB
    total native + heap memory <= 2 GiB
```

Nếu ứng dụng dùng heap 1,4 GiB, direct buffers 500 MiB, metaspace, code cache, threads và native libraries, **process memory có thể vượt mức `-Xmx`** dù heap vẫn nằm dưới giới hạn.

### Tránh nhập nhằng giữa `-Xms` và “used lúc khởi động”

`-Xms512m` **không** nói 512 MiB object đã được tạo ngay khi JVM bắt đầu. Heap **committed** khởi đầu có thể gần cỡ cấu hình, nhưng heap **used** phản ánh lượng đã được sử dụng. Có thể JVM còn pre-touch memory nếu bật các flag liên quan (ví dụ `-XX:+AlwaysPreTouch` trong môi trường hỗ trợ), ảnh hưởng resident footprint; đừng suy runtime RSS chỉ từ `-Xms`.

## 6. Bộ nhớ tiến trình: RSS, VSZ/VmSize và PSS

Các công cụ hệ điều hành không dùng cùng tập metrics với JVM.

| Chỉ số OS | Khái niệm | Điều KHÔNG thể kết luận chỉ từ một số |
| --- | --- | --- |
| **RSS** (Resident Set Size) | Số physical resident pages hiện được gán cho process theo phương pháp đếm của OS | Không phải heap used, không phải heap committed, không phải private memory thuần túy |
| **VSZ/VIRT/VmSize** | Tổng kích thước các virtual memory mappings của process | Không phải RAM đã sử dụng thực tế |
| **PSS** (Proportional Set Size; Linux) | Resident memory được quy đổi theo tỷ lệ chia sẻ các pages dùng chung | Không phải heap used; cũng không phải một loại NMT reserved |
| **Private dirty / anonymous RSS** | Các góc nhìn OS vào resident private/anonymous pages | Không thể tự tách chính xác heap khỏi mọi loại native allocation |
| **Peak RSS / VmHWM** | Mức resident high-water mark đã ghi nhận | Không phải giá trị RSS hiện tại |
| **Swap** | Những pages/charges đã nằm ở swap theo công cụ OS | Không tự cho biết kiểu Java object nào bị paging |

### Tại sao RSS có thể cao hơn heap committed?

Vì RSS chứa resident pages của **nhiều vùng ngoài heap**, ví dụ native buffers, mã máy/JIT, metaspace, thread stacks, memory-mapped files và shared libraries. Không có ràng buộc “RSS phải nhỏ hơn `-Xmx`”.

### Tại sao RSS có thể thấp hơn heap committed?

Một vùng heap đã committed có thể chưa được chạm hết hoặc một số pages không đang resident. OS memory policy và collector khiến những vùng committed/virtual và resident không trùng nhau.

### Tại sao VSZ có thể rất lớn?

Mỗi reserved virtual range đóng góp địa chỉ ảo. Heap, thread stacks, code cache, mmap và các reservations khác có thể làm VSZ lớn hơn nhiều RAM vật lý. Chỉ số VSZ cao **tự nó không phải bằng chứng memory leak**. Phải phân tích thêm tốc độ tăng, RSS/PSS, native allocations, container limit và trạng thái ứng dụng.

### Shared memory làm phép cộng RSS nhiều tiến trình dễ sai

Nếu hai processes ánh xạ cùng một shared library page, RSS của mỗi process có thể **đếm toàn bộ page ấy**. Tổng RSS hai processes có thể **đếm trùng shared pages**. PSS là góc nhìn bổ sung trên Linux khi muốn quy phần shared theo tỷ lệ; nó cũng không trở thành metric used của JVM.

## 7. MemoryMXBean và MemoryUsage của Java 17

Java Management API cung cấp metric theo **heap** và **non-heap**:

- `ManagementFactory.getMemoryMXBean().getHeapMemoryUsage()`
- `ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage()`
- `ManagementFactory.getMemoryPoolMXBeans()`, với `MemoryPoolMXBean.getUsage()` cho từng pool.

Các đối tượng `MemoryUsage` có bốn trường:

| Method | Ý nghĩa |
| --- | --- |
| `getInit()` | Dung lượng khởi tạo JVM yêu cầu, hoặc `-1` nếu không xác định |
| `getUsed()` | Dung lượng được ghi nhận đang dùng |
| `getCommitted()` | Dung lượng JVM đã cam kết sẵn sàng dùng |
| `getMax()` | Giới hạn vùng nếu biết, có thể là `-1` |

**Đây là các metric theo vùng JVM**, không phải mọi native allocations của process; đọc chúng không yêu cầu parsing OS `/proc`.

### Ví dụ Java 17 hoàn toàn xác định: bốn trường của MemoryUsage

Tạo **`MemoryUsageExample.java`**:

```java
import java.lang.management.MemoryUsage;

public class MemoryUsageExample {
    private static final long MIB = 1024L * 1024L;

    public static void main(String[] args) {
        // Số liệu giả định: không đo JVM hiện tại.
        MemoryUsage sample = new MemoryUsage(
                0L, 40L * MIB, 96L * MIB, 512L * MIB);

        System.out.println("used=" + sample.getUsed() / MIB + "MiB");
        System.out.println("committed=" + sample.getCommitted() / MIB + "MiB");
        System.out.println("max=" + sample.getMax() / MIB + "MiB");
        System.out.println("committedMinusUsed="
                + (sample.getCommitted() - sample.getUsed()) / MIB
                + "MiB");
        System.out.println("invariant="
                + (sample.getUsed() <= sample.getCommitted()
                && sample.getCommitted() <= sample.getMax()));
    }
}
```

```bash
javac --release 17 MemoryUsageExample.java
java MemoryUsageExample
```

**Stdout chính xác:**

```text
used=40MiB
committed=96MiB
max=512MiB
committedMinusUsed=56MiB
invariant=true
```

Lưu ý: constructor `MemoryUsage` trong bài tạo **một snapshot giả định có số cố định**, không cấp phát thật 512 MiB hay thay đổi `-Xmx`. Không lấy các số này để kết luận về máy đang chạy.

### Ví dụ Java 17 lấy số liệu JVM thật

Tạo **`LiveJvmMemory.java`**:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

public class LiveJvmMemory {
    private static final long MIB = 1024L * 1024L;

    private static String amount(long bytes) {
        return bytes < 0 ? "undefined"
                : String.format("%.1f MiB", bytes / (double) MIB);
    }

    private static void show(String name, MemoryUsage usage) {
        System.out.println(name
                + ": used=" + amount(usage.getUsed())
                + ", committed=" + amount(usage.getCommitted())
                + ", max=" + amount(usage.getMax())
                + ", init=" + amount(usage.getInit()));
    }

    public static void main(String[] args) {
        show("heap", ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage());
        show("non-heap", ManagementFactory.getMemoryMXBean()
                .getNonHeapMemoryUsage());

        for (MemoryPoolMXBean pool :
                ManagementFactory.getMemoryPoolMXBeans()) {
            MemoryUsage usage = pool.getUsage();
            if (usage != null) {
                show("pool " + pool.getName(), usage);
            }
        }
    }
}
```

```bash
javac --release 17 LiveJvmMemory.java
java -Xms128m -Xmx512m LiveJvmMemory
```

**Stdout phụ thuộc JVM, GC, thời điểm chạy và OS** — không có một bộ số bytes chính xác chung cho mọi máy. Mẫu **chỉ minh họa định dạng**, không phải kết quả đã đo:

```text
heap: used=<số> MiB, committed=<số> MiB, max=<số> MiB, init=<số> MiB
non-heap: used=<số> MiB, committed=<số> MiB, max=undefined, init=<số> MiB
pool <tên pool>: used=<số> MiB, committed=<số> MiB, max=<số hoặc undefined>, init=<số> MiB
```

**Không dùng mẫu trên làm regex yêu cầu output chính xác**: ví dụ dấu `<...>` ở đây là ký hiệu giải thích, giá trị non-heap max không bắt buộc lúc nào cũng `undefined`, danh sách pool phụ thuộc collector.

### Vì sao cộng tất cả memory pools không phải lúc nào khớp một snapshot heap?

- Các giá trị có thể được chụp **không đồng thời**, GC hoặc allocation xảy ra giữa những lần gọi.
- GC chia heap thành các pools/regions theo chính sách của collector; giá trị maximum một pool có thể không xác định hoặc biến động.
- Những metrics được cập nhật ở các thời điểm khác nhau và theo cấu trúc pool; không dùng tổng thủ công làm tiêu chuẩn bắt buộc chính xác từng byte bằng heap aggregate trong mọi release/collector.
- Non-heap MXBean không là phép đo trọn vẹn RSS hoặc mọi native allocation.

## 8. Runtime.totalMemory/freeMemory/maxMemory: cùng hướng, nhưng là snapshot khác

Một API quen thuộc:

```java
Runtime runtime = Runtime.getRuntime();
long total = runtime.totalMemory();
long free = runtime.freeMemory();
long max = runtime.maxMemory();
long approximateUsed = total - free;
```

Đây là **fragment minh họa** đặt trong một method.

- `totalMemory()`: dung lượng heap mà JVM đang có thể sử dụng theo contract của runtime, thường được so sánh với **heap committed**.
- `freeMemory()`: phần dung lượng heap còn trống **trong mức total hiện tại**, không phải RAM vật lý còn rảnh.
- `maxMemory()`: giới hạn tối đa JVM cố gắng sử dụng cho heap, không phải process RSS limit.
- `total - free` là phép ước lượng **heap currently used** tại thời điểm gọi, nhưng mỗi method là một lần quan sát riêng; allocation/GC có thể diễn ra giữa các lần gọi nên không đòi nó trùng tuyệt đối với `MemoryMXBean` đọc sau.

Không dùng `Runtime.freeMemory()` làm “máy còn bao nhiêu GB RAM” và không suy rằng `Runtime.maxMemory()` giới hạn cả direct buffers, metaspace và thread stacks.

## 9. Native Memory Tracking (NMT): nhìn reserved/committed phía HotSpot

**Native Memory Tracking** là một khả năng chẩn đoán của HotSpot/OpenJDK để theo dõi những allocation/mapping thuộc phạm vi JVM hỗ trợ. NMT có thể phân loại theo những categories như **Java Heap**, **Class**, **Thread**, **Code**, **GC**, **Compiler**, **Internal**, v.v., với tổng **reserved** và **committed**.

NMT là công cụ *implementation-specific*, không phải `MemoryMXBean` chuẩn bắt buộc mọi JVM triển khai giống nhau. Các category names và thống kê có thể khác theo JDK build/collector.

### Bật NMT từ lúc khởi chạy

Ví dụ:

```bash
java -XX:NativeMemoryTracking=summary -Xms128m -Xmx512m LiveJvmMemory
```

Chương trình `LiveJvmMemory` trong ví dụ trước kết thúc rất nhanh nên có thể không còn process để attach. Trong ứng dụng thật, hãy bật NMT ở **JVM process chạy đủ lâu** (server hoặc chương trình có thời gian chờ hợp lệ) rồi dùng process ID của nó.

Khi process đang chạy:

```bash
jcmd <PID> VM.native_memory summary scale=MB
```

Đây là lệnh **minh họa với placeholder `<PID>`**; thay bằng số PID thật và thực hiện khi VM còn chạy. Nếu NMT chưa bật ở **startup**, không thể mặc định yêu cầu `summary` rồi đợi có dữ liệu; nó phải được cấu hình phù hợp lúc bắt đầu JVM.

### Dạng output NMT minh họa

```text
Native Memory Tracking:
Total: reserved=...MB, committed=...MB

- Java Heap (reserved=...MB, committed=...MB)
- Class     (reserved=...MB, committed=...MB)
- Thread    (reserved=...MB, committed=...MB)
- Code      (reserved=...MB, committed=...MB)
- GC        (reserved=...MB, committed=...MB)
```

Các dòng này **chỉ mô tả cách đọc**, không đại diện cho một kết quả NMT thực tế, và không cố gắng liệt kê mọi category. Với một số vùng được JVM theo dõi bằng `malloc`, NMT có thể tính phần allocated như reserved và committed tương ứng; các memory maps có thể có reserved lớn hơn committed. Đừng áp đặt cùng một mô hình reservation đơn giản lên tất cả dòng.

### Theo dõi *delta* thay vì chỉ một ảnh chụp

Một cách dùng phổ biến:

```bash
jcmd <PID> VM.native_memory baseline
# Cho workload đi qua giai đoạn cần quan sát.
jcmd <PID> VM.native_memory summary.diff scale=MB
```

Nếu NMT khởi động ở chế độ detail, có thể dùng thêm các báo cáo chi tiết phù hợp như `detail`/`detail.diff` khi công cụ hỗ trợ. Vì `summary` ít overhead hơn `detail`, cần cân đối chi phí theo môi trường vận hành.

**Đọc delta đúng cách:** NMT native committed tăng đáng kể ở mục Thread có thể gợi ý nhiều threads hơn; mục Class tăng có thể gợi ý class metadata/classloaders; mục Code có thể tăng theo JIT. Đây là **dấu hiệu điều tra**, không tự chứng minh nguồn leak hay chỉ ra Java object nào giữ memory.

### NMT không đo toàn bộ native memory của mọi thư viện

Phạm vi NMT không bao trùm một cách đầy đủ những native allocations do các thư viện bên thứ ba tự thực hiện ngoài cơ chế JVM được theo dõi. Nó cũng không phải RSS trực tiếp. Vì vậy:

- **NMT committed != process RSS** là điều bình thường.
- **NMT reserved != process VSZ** là điều bình thường.
- Process RSS tăng mà NMT committed ít thay đổi có thể xuất phát từ mmap/file-backed pages, thư viện native/JNI, allocator behavior hoặc phần không được NMT ghi nhận.
- Cần dùng công cụ OS/native profiler tương ứng để điều tra tiếp, không suy “RSS - NMT committed = memory leak bytes” như công thức chính xác.

## 10. Direct buffers, metaspace và thread stacks nằm ngoài câu chuyện heap used

### Direct ByteBuffer

`ByteBuffer.allocateDirect(size)` cấp phát buffer với backing memory ngoài Java heap theo cơ chế JVM/runtime. **Object `ByteBuffer` wrapper/reference vẫn có thể tồn tại trên heap**, còn backing memory có thể được quản lý native; do đó:

- Java heap used tăng ít **không** chứng minh direct memory không tăng.
- `-Xmx` không phải giới hạn duy nhất cho việc cấp phát direct buffers.
- HotSpot có tùy chọn liên quan như `-XX:MaxDirectMemorySize`; mặc định và cách enforcement cần đối chiếu JDK/build.
- Thời điểm giải phóng backing memory có thể liên quan reference reachability, cleaners và GC, không nhất thiết lập tức ngay lúc biến local ra khỏi scope.

### Chương trình Java 17 minh họa: direct allocation và heap wrapper

Tạo **`DirectMemoryDemo.java`**:

```java
import java.nio.ByteBuffer;

public class DirectMemoryDemo {
    public static void main(String[] args) {
        ByteBuffer heap = ByteBuffer.allocate(1024);
        ByteBuffer direct = ByteBuffer.allocateDirect(1024);

        System.out.println("heapDirect=" + heap.isDirect());
        System.out.println("nativeDirect=" + direct.isDirect());
        System.out.println("heapCapacity=" + heap.capacity());
        System.out.println("nativeCapacity=" + direct.capacity());
    }
}
```

```bash
javac --release 17 DirectMemoryDemo.java
java DirectMemoryDemo
```

**Stdout chính xác:**

```text
heapDirect=false
nativeDirect=true
heapCapacity=1024
nativeCapacity=1024
```

`isDirect()` cho biết buffer thuộc kiểu direct theo API, **không** đo RSS chính xác. Demo này không chứng minh sẽ tăng đúng 1024 bytes resident vì wrapper, alignment, native allocator và policies của OS còn tham gia.

### Metaspace

HotSpot từ Java 8 dùng **native memory cho class metadata** trong metaspace. Khi load nhiều class hoặc giữ nhiều class loaders sống, native committed cho class metadata có thể tăng trong khi **heap used không tăng tương ứng**. Vẫn có các objects trên heap liên quan tới Class/class loaders; nói “mọi thứ về class đều không ở heap” cũng sai.

- `-XX:MaxMetaspaceSize` là một ví dụ cấu hình giới hạn metaspace trên HotSpot; không phải tổng native memory limit.
- `OutOfMemoryError: Metaspace` gợi ý vấn đề class metadata và giới hạn/tài nguyên, không kết luận ngay “phải tăng `-Xmx`”.
- Nếu nghi class loader leak, xem số lượng classes/loaders, unloading behavior, references giữ class loader và NMT/ClassLoader-related diagnostics.

### Thread stacks

Nhiều threads có thể tăng nhu cầu stack/native memory ngay cả khi heap used không đổi nhiều:

- Tham số như `-Xss` ảnh hưởng tài nguyên stack của threads (mức độ áp dụng tùy JVM/loại thread và OS).
- NMT có thể hiển thị **Thread reserved lớn hơn committed** vì reserved stack space không nhất thiết toàn bộ được cam kết/touch.
- Việc tạo thread còn tốn tài nguyên OS khác, và container có thể có giới hạn PID/thread count.
- `java.lang.OutOfMemoryError: unable to create native thread` không nhất thiết do heap đầy; có thể liên quan memory native, OS process/thread limits hoặc resource controls.

Không lấy `Threads × -Xss` làm phép đo RSS chính xác. Đó có thể là ước lượng không gian stack virtual đã reserved, không phải resident pages thật.

## 11. Đọc số liệu hệ điều hành trên Linux

Nếu Java chạy trên Linux, có thể kiểm tra thông tin tiến trình với PID hợp lệ:

```bash
ps -o pid,rss,vsz,comm -p <PID>
grep -E 'VmSize|VmRSS|VmHWM|RssAnon|RssFile|VmSwap|Threads' /proc/<PID>/status
cat /proc/<PID>/smaps_rollup
```

Đây là **các lệnh minh họa**; thay `<PID>` bằng PID thật, và `smaps_rollup` có thể bị hạn chế bởi quyền truy cập hoặc không khả dụng trên kernel cũ. `ps` thường hiển thị RSS/VSZ theo KiB trên Linux; đọc manual của môi trường thay vì giả định đơn vị trên mọi OS.

Ý nghĩa thực hành:

- `VmSize` liên quan **virtual address space**, không phải RAM đã tiêu thụ.
- `VmRSS` gần góc nhìn RSS của process, gồm nhiều loại resident pages, có thể chia sẻ.
- `RssAnon` và `RssFile` giúp phân biệt anonymous/file-backed pages theo OS bookkeeping, **không trực tiếp chỉ ra Java heap vs non-heap**.
- `smaps_rollup` có thể cho RSS/PSS và private/shared metrics tổng hợp; hữu ích nếu RSS có nhiều file-backed/shared mappings.
- Số liệu ở những nguồn khác nhau chụp vào **những thời điểm khác nhau**; CPU/GC/allocator có thể thay đổi memory giữa các lần đo.

### OS memory và JVM metrics cần ghép theo cùng PID, cùng thời điểm

Nếu chạy nhiều JVM trên máy, hãy xác định PID của **đúng ứng dụng**. `jcmd -l` có thể liệt kê một số Java processes khả dụng; `ps` giúp xem process; container PID namespace có thể khiến PID quan sát bên trong và ngoài container **khác nhau**.

Đừng so heap used của JVM A với RSS của JVM B hoặc tổng RSS của cgroup/container rồi kết luận có leak trong JVM A.

## 12. Container memory: không phải Java heap maximum

Trong container hoặc Kubernetes, cgroup giới hạn **tài nguyên toàn cgroup/container**, không chỉ Java heap. Với Linux cgroup v2, các tệp hay gặp:

```bash
cat /sys/fs/cgroup/memory.current
cat /sys/fs/cgroup/memory.max
cat /sys/fs/cgroup/memory.stat
cat /sys/fs/cgroup/memory.events
```

**Đường dẫn chỉ là ví dụ cho môi trường cgroup v2 phổ biến**; hệ thống có thể mount ở nơi khác, dùng cgroup v1 hoặc giới hạn quyền truy cập.

- `memory.current`: bộ nhớ đang được cgroup hạch toán, có thể gồm **anonymous memory, page cache, kernel memory** và các thành phần tùy cơ chế kernel.
- `memory.max`: hard limit của cgroup, hoặc chuỗi `max` nếu không có giới hạn hữu hạn tại tệp ấy.
- `memory.stat`: phân rã các nguồn sử dụng cgroup theo kernel.
- `memory.events`: các sự kiện như chạm giới hạn, OOM/OOM-kill trong scope phù hợp.

**Container memory usage không bằng process RSS** nếu cgroup có nhiều processes, cache và các vùng kernel được hạch toán. Một container còn có thể bị **OOM killed** bởi kernel dù JVM chưa từng ném `OutOfMemoryError: Java heap space`, vì tổng tiêu thụ cgroup vượt limit.

### Ví dụ bài toán sizing

Giả sử limit container là **2 GiB** và đặt `-Xmx2g`. Cấu hình này **không chừa ngân sách** cho metaspace, stacks, code cache, GC native allocations, direct buffers, agents, mapped memory, OS/page-cache accounting. Do đó nguy cơ container memory pressure rất cao, kể cả khi Java heap used chưa chạm 2 GiB.

Cách tính budget cần bao gồm:

```text
Container / process budget (ước lượng, không phải đẳng thức RSS)
    = heap target
    + direct/native buffers
    + metaspace/classes
    + thread stacks
    + code cache + GC/JVM internals
    + native libraries, agents
    + headroom cho runtime và kernel accounting
```

**Không** cộng số heap used, NMT reserved và RSS lại với nhau: chúng là những góc nhìn chồng lấn. Ưu tiên đo số liệu thật theo peak workload và có headroom, thay vì ghép các metric không cùng đơn vị/ngữ nghĩa.

### Đừng đoán nguyên nhân exit code đơn độc

Exit status như `137` trong một số môi trường thường liên quan signal `SIGKILL`, có thể do container OOM killer hoặc bị kill bên ngoài. **Một exit code riêng lẻ không chứng minh kernel OOM**; kiểm tra Kubernetes events, cgroup `memory.events`, logs và thông tin orchestrator.

## 13. Bảng đối chiếu các công cụ quan sát

| Công cụ/API | Có thể thấy | Không cung cấp trực tiếp |
| --- | --- | --- |
| `MemoryMXBean.getHeapMemoryUsage()` | Heap used/committed/max/init | Process RSS/VSZ và tất cả native allocations |
| `MemoryMXBean.getNonHeapMemoryUsage()` | Non-heap managed usage | Toàn bộ native memory của thư viện bên thứ ba |
| `MemoryPoolMXBean` | Usage của pool theo collector/manager | Một snapshot OS resident không sai lệch |
| `Runtime.totalMemory/freeMemory/maxMemory` | Các thông tin heap ở mức API | RAM còn trống của máy |
| `jcmd <PID> GC.heap_info` | Thông tin heap/GC tùy JVM | Toàn bộ process memory mapping |
| `jstat -gc <PID>` | GC/heap pool counters tùy collector | Tổng RSS chính xác |
| `jcmd <PID> VM.native_memory summary` | NMT reserved/committed thuộc phần JVM theo dõi | Mọi native allocation và RSS vật lý |
| `ps`, `/proc/<PID>/status` | Process VSZ/RSS/thread counters theo Linux | Heap objects reachable hoặc ownership Java |
| `/proc/<PID>/smaps_rollup` | PSS/RSS, private/shared memory rollup | Object histogram hoặc GC reachability |
| cgroup `memory.current`/`memory.max` | Usage/limit của cgroup | Mỗi method/object Java đã dùng bao nhiêu |
| Heap dump/profiler | Objects/retained heap/allocations tùy chế độ | Mặc định không cho full native RSS breakdown |

**Tránh “một metric làm nguồn sự thật duy nhất”**. Hãy bắt đầu từ câu hỏi chẩn đoán cụ thể: heap leak, direct/native leak, nhiều threads, metaspace growth hay OS/container pressure.

## 14. Một quy trình đo có thể lặp lại trên Java 17

### Bước 1: chốt môi trường và giới hạn

Ghi ít nhất: JVM vendor/build, Java version, GC, `-Xms`/`-Xmx`, `-Xss`, NMT flag, container memory limit và PID. Nếu có thể, bật GC logging/NMT ngay từ startup cho lần quan sát có kế hoạch.

Ví dụ chạy một **server thật** (không phải chương trình thoát ngay):

```bash
java -Xms256m -Xmx1024m -XX:NativeMemoryTracking=summary -jar app.jar
```

`app.jar` chỉ là tên minh họa của ứng dụng đã build phù hợp. Đừng thêm `-jar` vào lệnh của những ví dụ Java file `.class` nếu bạn không có JAR.

### Bước 2: quan sát cùng lúc theo cùng PID

```bash
jcmd <PID> VM.flags
jcmd <PID> GC.heap_info
jcmd <PID> VM.native_memory summary scale=MB
ps -o pid,rss,vsz,comm -p <PID>
```

Nếu chạy trong cgroup v2, ghi thêm `memory.current` và `memory.max` ở **cgroup thực chứa process**. Đọc kỹ units: bytes, KiB, MiB và số được làm tròn dễ tạo các chênh lệch tưởng là lỗi.

### Bước 3: đặt baseline để xem delta

```bash
jcmd <PID> VM.native_memory baseline
# Chạy một workload đủ đại diện, có tải ổn định và peak.
jcmd <PID> VM.native_memory summary.diff scale=MB
```

Đồng thời ghi **heap used sau GC** hoặc xu hướng heap pools, RSS/PSS, số threads và cgroup memory. NMT delta cho biết category nào tăng từ baseline; nó không tự cho biết object nào còn reachable.

### Bước 4: phân loại biểu hiện

| Biểu hiện | Giả thuyết ưu tiên kiểm tra |
| --- | --- |
| Heap used sau GC tăng đều, gần heap max | Java heap retention, cache không giới hạn, object leak |
| Heap committed lớn, heap used thấp | Heap chưa shrink hoặc JVM giữ committed headroom; không tự chứng minh leak |
| NMT Thread reserved/committed tăng và thread count tăng | Nhiều threads, stacks/native thread resources |
| NMT Class/Metaspace tăng kèm số class loaders tăng | Dynamic class loading, class loader retention |
| NMT Code tăng rồi ổn định | JIT code cache warming; tăng không đồng nghĩa leak |
| RSS tăng, heap used ổn định, NMT native tăng | Điều tra native categories, direct buffers, agents |
| RSS tăng nhưng NMT ít thay đổi | Đối chiếu mmap, third-party native, allocator fragmentation, OS pages |
| Container memory.current tăng nhanh nhưng process RSS không tương ứng | Cgroup có processes/cache/kernel accounting khác; kiểm tra memory.stat |
| OOMKilled mà heap dùng chưa cao | Container/native budget pressure, không chỉ Java heap |

### Bước 5: xác nhận bằng đúng công cụ

- Nghi **Java objects leak**: allocation profiler, heap dump, dominator/retained-size analysis và GC logs.
- Nghi **native memory leak**: NMT trend kết hợp native profiler/OS mappings, đặc biệt thư viện C/JNI không được NMT bao phủ.
- Nghi **nhiều threads**: thread dump, thread count theo thời gian, stack reservation, thread pool configuration.
- Nghi **container limit**: cgroup events, kubelet/orchestrator events, memory pressure và total budget.
- Nghi **GC/commit hành vi**: GC logs, heap occupancy trước/sau collection, shrink/uncommit behavior của GC thực tế.

Không chủ động gọi `System.gc()` trong production chỉ để “làm đẹp metric”. Kết quả GC không bảo đảm hoàn tất hoặc làm giảm footprint ngay; việc ép GC có thể ảnh hưởng latency.

## 15. Dùng số đo để tránh suy luận sai

### Tình huống A: heap committed cao nhưng used thấp

Ví dụ (giả định) heap used 250 MiB, committed 1.000 MiB, max 2.000 MiB. Có thể JVM giữ heap đã committed để tránh mở rộng lại, hoặc collector chưa/không thu nhỏ sau GC. **Không đủ dữ liệu để kết luận leak**. Hãy xem heap occupancy qua GC, memory pressure và collector flags.

### Tình huống B: NMT reserved cao nhưng RSS thấp

Ví dụ NMT heap reserved 4 GiB, heap committed 512 MiB, RSS 900 MiB. Không có gì mâu thuẫn: phần reserved là **address space**, không phải 4 GiB physical RAM. Kiểm tra giới hạn virtual address space (nhất là môi trường 32-bit cũ) chỉ khi phù hợp, không nâng RAM dựa duy nhất vào reserved.

### Tình huống C: RSS tăng trong khi heap dùng gần như không đổi

Các thủ phạm khả dĩ: direct buffers, JNI/native library allocations, thêm threads, JIT code cache, metaspace, file-backed memory touched hoặc native allocator fragmentation. Dùng NMT + OS + profiler; **không tự kết luận “memory leak ở Java heap”**.

### Tình huống D: process RSS lớn hơn `-Xmx`

Hoàn toàn có thể: `-Xmx` chỉ giới hạn heap. Process có memory khác và RSS là resident metric của tổng process. Sửa bằng việc hiểu native budget; tăng `-Xmx` mù quáng có thể làm container OOM **dễ hơn**.

### Tình huống E: một container có memory.current cao hơn Java process RSS

Cgroup có thể tính thêm page cache, kernel memory hoặc processes khác. Xem `memory.stat` và process list; đừng ép hai chỉ số phải bằng nhau từng byte.

### Tình huống F: VSZ cao bất thường nhưng RSS/PSS ổn định

Có thể có nhiều reserved address ranges, thread stacks, mmap. Theo dõi xu hướng và failures thực tế. Trên nền tảng 64-bit, không gian địa chỉ ảo rất lớn; một con số VSZ cao không tự nói memory physical sắp hết.

## 16. Sai lầm phổ biến và cách sửa tư duy

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Heap used = RSS của Java process” | Heap used chỉ là một vùng; RSS gồm nhiều mapped/resident pages |
| “Heap committed = RAM đã chiếm” | Committed là số liệu vùng JVM; physical residency đo bằng RSS/PSS theo OS |
| “Heap reserved = RAM đã tiêu thụ” | Reserved thường là địa chỉ ảo; chưa chắc backed bằng physical pages |
| “`-Xmx` = giới hạn mọi memory của process” | `-Xmx` giới hạn heap, không phải native/direct/stacks/metaspace |
| “`-Xms` = heap objects đang dùng ban đầu” | `-Xms` liên quan initial heap, không tạo từng ấy objects |
| “`Runtime.freeMemory()` là RAM trống của máy” | Là vùng heap free trong mức total hiện tại |
| “NMT committed bằng RSS” | NMT tracking và OS residency khác phạm vi/ngữ nghĩa |
| “NMT thấy hết memory của JNI/C libraries” | NMT không bảo đảm theo dõi toàn bộ allocations bên ngoài cơ chế của JVM |
| “RSS của nhiều processes cộng lại bằng RAM physical đã dùng” | Shared pages có thể bị đếm trùng; PSS cung cấp một góc nhìn khác |
| “VSZ cao chứng minh leak” | Có thể chỉ là virtual address reservation |
| “Heap used thấp nên chắc chắn không thể OOM” | Native/container/thread/metaspace allocations vẫn có thể OOM |
| “Heap used đang tăng là chắc chắn leak” | Cần so sau GC và đối chiếu objects/retention |
| “Mọi `MemoryPoolMXBean.getMax()` đều là số dương hữu hạn” | Max có thể là `-1` (undefined) |
| “Các công cụ đo khác nhau phải khớp từng byte” | Scope, thời điểm snapshot và đơn vị khác nhau |
| “Nâng `-Xmx` sửa được container OOM” | Có thể làm native budget thiếu hơn; phải phân tích tổng footprint |
| “Một mẫu số đo chứng minh nguyên nhân root cause” | Cần trend, GC/allocation/OS evidence và tái hiện workload |

## 17. Câu hỏi tự kiểm tra

**Câu 1:** Heap used = 300 MiB, committed = 600 MiB, max = 2 GiB. Có nghĩa process đang dùng đúng 300 MiB RAM?

**Không.** 300 MiB là heap used theo metric, không tính mọi resident native/file-backed pages.

**Câu 2:** Heap reserved = 8 GiB nhưng máy chỉ có 4 GiB RAM vật lý; dữ liệu này chắc chắn sai?

**Không.** Reserved có thể là khoảng địa chỉ ảo, không đòi 8 GiB physical RAM ngay.

**Câu 3:** Java process RSS = 1,5 GiB, `-Xmx1g`. Có nhất thiết JVM vi phạm giới hạn?

**Không.** `-Xmx` chỉ đặt giới hạn Java heap; non-heap/native vẫn tiêu thụ process memory.

**Câu 4:** `getCommitted() - getUsed()` trong cùng một `MemoryUsage` nói gì?

**Phần dung lượng đã committed nhưng chưa được metric ghi nhận used**. Nó không phải RAM OS còn trống.

**Câu 5:** Một memory pool có `getMax() == -1`. Có phải vùng ấy đã dùng hết memory?

**Không.** `-1` biểu thị maximum **không xác định** theo API.

**Câu 6:** NMT summary không tăng, nhưng RSS tăng. Có thể khẳng định không có native leak?

**Không.** NMT không bao phủ mọi thư viện/mapping; còn cần native profiler/OS evidence.

**Câu 7:** Trong container, heap used chưa cao nhưng kernel OOM kill process. Nguyên nhân có thể là gì?

**Tổng cgroup memory** gồm heap, native memory, stacks, file cache/kernel allocations và những processes khác vượt limit. Phải kiểm tra cgroup events/metrics.

**Câu 8:** Nên lấy một thời điểm đo heap used trước GC để kết luận object leak?

**Không.** Ưu tiên xu hướng occupancy sau GC và retained object analysis, cùng workload tương ứng.

**Câu 9:** RSS của hai processes cùng dùng một shared library page có thể tính trùng không?

**Có.** RSS tính resident pages theo process; PSS là một cách phân bổ shared pages theo tỷ lệ trên Linux.

**Câu 10:** Dùng lệnh `jcmd <PID> VM.native_memory` mà công cụ báo NMT disabled. Có sửa bằng cách thêm `summary` vào lệnh không?

**Không.** NMT phải được cấu hình từ JVM startup phù hợp; chạy `jcmd` không tự bật theo dõi retroactively.

## Tài liệu đối chiếu và hướng tìm hiểu thêm

- [Java SE 17 `MemoryUsage`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html): định nghĩa `init`, `used`, `committed`, `max` và tính hợp lệ của snapshot.
- [Java SE 17 `MemoryMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html): heap vs non-heap memory usage.
- [Java SE 17 `MemoryPoolMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html): per-pool usage và thresholds.
- [Java SE 17 `Runtime`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html): `totalMemory`, `freeMemory`, `maxMemory`.
- [JVMS Java SE 17, Chương 2 — Run-Time Data Areas](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5): heap, method area, JVM stack, native stacks.
- [Java SE 17 `ByteBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): direct và heap buffers.
- [Java SE 17 `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `GC.heap_info` và `VM.native_memory`.
- [Java SE 17 `jstat`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): GC/memory utilization statistics.
- [Oracle Java Troubleshooting Guide — Native Memory Tracking](https://docs.oracle.com/en/java/javase/17/troubleshoot/diagnostic-tools.html): cơ chế theo dõi và các giới hạn của NMT.
- [Linux proc_pid_status(5)](https://man7.org/linux/man-pages/man5/proc_pid_status.5.html): `VmSize`, `VmRSS`, `RssAnon`, `RssFile`, `VmHWM`.
- [Linux proc_pid_smaps(5)](https://man7.org/linux/man-pages/man5/proc_pid_smaps.5.html): RSS/PSS và private/shared mappings.
- [Linux cgroup v2 memory controller](https://docs.kernel.org/admin-guide/cgroup-v2.html#memory): `memory.current`, `memory.max`, `memory.stat`, `memory.events`.

**Ghi nhớ:** **Used** là lượng JVM ghi nhận đã dùng trong **một vùng**; **committed** là lượng vùng ấy đã cam kết có thể sử dụng; **reserved** thường nói về **địa chỉ ảo dành trước**, không phải RAM resident. **RSS/VSZ/PSS** là góc nhìn **tiến trình/hệ điều hành**, còn **cgroup memory** là góc nhìn **nhóm tài nguyên**. Chỉ so sánh chúng khi đã thống nhất **scope, đơn vị, thời điểm, JVM/OS implementation**; không dùng `-Xmx` hay một số đo heap làm đại diện cho toàn bộ process memory.


</details>

- [Quay lại đầu trang](#back-to-top)
