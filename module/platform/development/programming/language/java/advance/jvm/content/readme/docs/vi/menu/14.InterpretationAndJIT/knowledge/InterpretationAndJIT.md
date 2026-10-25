<a id="back-to-top"></a>

# Thông dịch và JIT

## Menu
- [Thông dịch bytecode và biên dịch lúc chạy (JIT) giải quyết vấn đề gì](#interpretation-and-jit-001)
- [Đường đi từ phương thức ít chạy đến đoạn mã nóng](#interpretation-and-jit-002)
- [Thu thập hồ sơ thực thi từ số lần gọi, vòng lặp và kiểu đối tượng](#interpretation-and-jit-003)
- [Biên dịch phân tầng trong HotSpot và vai trò của C1, C2](#interpretation-and-jit-004)
- [Biên dịch phương thức và thay thế khi đang chạy vòng lặp (OSR)](#interpretation-and-jit-005)
- [Warm-up, trạng thái ổn định và khác biệt giữa thời gian khởi động với hiệu năng đỉnh](#interpretation-and-jit-006)
- [Giả định tối ưu, deoptimization và biên dịch lại khi giả định không còn đúng](#interpretation-and-jit-007)
- [Code cache, luồng biên dịch và chi phí tài nguyên của JIT](#interpretation-and-jit-008)
- [Đối chiếu chế độ -Xint với thực thi kết hợp trong thí nghiệm có kiểm soát](#interpretation-and-jit-009)

## <a id="interpretation-and-jit-001">Thông dịch bytecode và biên dịch lúc chạy (JIT) giải quyết vấn đề gì</a>

<details>
<summary>Xem chi tiết</summary>

# Thông dịch bytecode và biên dịch lúc chạy (JIT) giải quyết vấn đề gì?

## 1. Bài toán mà JVM phải giải quyết

`javac` thường biên dịch Java source thành **JVM bytecode** trong file `.class`, không phải native machine code gắn cứng với CPU hiện tại. Khi chạy, JVM phải thực hiện các chỉ thị máy ảo ấy trên một nền tảng phần cứng cụ thể.

Có hai cách thực thi bổ sung cho nhau:

- **Thông dịch (interpretation):** một bytecode interpreter trực tiếp xử lý các lệnh JVM. Nó cho phép bắt đầu chạy phương thức mà không nhất thiết phải tạo native code tối ưu cho phương thức đó.
- **Biên dịch lúc chạy (Just-In-Time, JIT):** JVM nhận diện method hoặc loop được thực thi nhiều, rồi có thể chuyển bytecode thành **native machine code** đã tối ưu cho trạng thái runtime/CPU hiện tại.
- **Profiling và adaptive optimization:** JVM quan sát code path, call frequency, loop iterations, receiver types và hành vi thường gặp để tập trung công biên dịch vào vùng hot.
- **Deoptimization:** nếu các giả định dùng khi tối ưu không còn đúng, runtime có thể quay về một execution mode tương thích và bảo toàn Java semantics.

**Mục tiêu chính là cân bằng thời gian bắt đầu chạy, thời gian khởi động/warmup và hiệu năng lâu dài.** Interpreter có chi phí compilation ban đầu thấp, nhưng phải xử lý bytecode lặp lại. JIT tốn tài nguyên để compile, bù lại native code có thể chạy nhanh hơn và được tối ưu liên phương thức.

Đây là mô hình điển hình của **HotSpot**, không phải quy tắc bắt buộc mọi JVM implementation phải có bộ compiler C1/C2 hoặc cùng chính sách JIT.

## 2. Nhìn toàn bộ đường thực thi

~~~text
Java source
  |
  v
javac
  |
  v
Class file: bytecode + constant pool + class metadata
  |
  v
JVM: loading -> verification/linking -> initialization khi cần
  |
  v
Interpreter chạy bytecode
  |        |
  |        +-- theo dõi call counts, loop backedges, type profiles
  |
  +-- cold method -> có thể tiếp tục thông dịch
  |
  +-- hot method/loop -> yêu cầu JIT
                          |
                          v
                     C1/C2 compiler
                          |
                          v
                     Native code trong code cache
                          |
                          v
                     CPU thực thi compiled code
                          |
                          +-- assumptions invalid?
                                 |
                                 v
                          guard/fallback/deoptimization
~~~

**Không đọc sơ đồ như một thứ tự cưỡng bức cho mọi method.** Một phương thức có thể không được biên dịch; một phương thức khác có thể đi qua nhiều tiers; long-running loop có thể chuyển sang native code bằng OSR mà không cần return. Flags `-Xint` và `-Xcomp` cũng làm execution strategy khác.

### Ba dạng mã khác nhau

| Dạng | Ví dụ | Đơn vị thực thi |
| --- | --- | --- |
| Java source | `return a + b;` | Chương trình nguồn, theo Java Language Specification |
| JVM bytecode | `iload_0`, `iload_1`, `iadd`, `ireturn` | JVM instruction set |
| Native machine code | Instructions của x86-64/AArch64 | CPU thực |

**`javac` không đồng nghĩa JIT.** `javac` thường chạy trước khi ứng dụng chạy; JIT là compiler hoạt động trong JVM lúc ứng dụng đang chạy.

## 3. Interpreter thực thi bytecode như thế nào?

Ví dụ Java method:

~~~java
static int add(int a, int b) {
    return a + b;
}
~~~

Đây là **đoạn method minh họa**, chưa phải một file Java độc lập có `main`. `javac` thông dụng tạo bytecode tương tự:

~~~text
0: iload_0
1: iload_1
2: iadd
3: ireturn
~~~

Với `a=4` và `b=9`:

~~~text
locals: [4, 9], operand stack = []
iload_0 -> [4]
iload_1 -> [4, 9]
iadd    -> [13]
ireturn -> đưa 13 về caller
~~~

Ở mức khái niệm, interpreter lặp lại thao tác “lấy bytecode, xác định opcode, thực hiện semantics và cập nhật pc”. Một pseudo-code:

~~~text
while (method is active):
    opcode = readInstruction(pc)
    dispatch(opcode)
    performJVMOperation()
    advancePcAsRequired()
~~~

Đây **không phải nguyên văn mã nguồn HotSpot interpreter**. HotSpot có template interpreter và machine-code stubs được tối ưu; diễn giải “một switch trong C” chỉ là mô hình học ban đầu.

### Interpreter giải quyết vấn đề gì?

1. Chạy những methods lạnh với ít chi phí chuẩn bị/native-code generation.
2. Hỗ trợ chạy sớm trong quá trình startup/class initialization theo các quy tắc JVM.
3. Ghi nhận profiling data để chính sách compilation quyết định khi nào code trở nên hot.
4. Cung cấp đường fallback khi optimized code bị invalidated hoặc phải deoptimize.
5. Duy trì tính linh hoạt khi class loading, type behavior và method call sites thay đổi.

**Chi phí:** các vòng dispatch/bytecode operations lặp đi lặp lại có thể chậm hơn native optimized code, đặc biệt trong hot loops. Tuy nhiên không phải mọi method đều đáng biên dịch: với method chỉ chạy một lần, compilation cost có thể lớn hơn lợi ích.

## 4. JIT giải quyết vấn đề gì?

Thay vì giải mã các lệnh bytecode lặp lại, JIT có thể chuyển một đoạn bytecode đang hot sang native instructions theo pipeline khái niệm:

~~~text
Bytecode + class metadata + runtime profiles
           |
           v
Compiler intermediate representation
           |
           v
Data-flow/control-flow analysis
Inlining, constant folding, guard insertion,
range-check optimization, escape analysis ...
           |
           v
Register allocation + machine-code generation
           |
           v
Native compiled code -> code cache
~~~

Những lợi ích tiềm năng:

- Giảm interpreter dispatch overhead trên đường nóng.
- **Inlining** loại overhead của nhiều lời gọi method nhỏ và mở ra tối ưu xuyên methods.
- **Devirtualization** tận dụng observed receiver types để tối ưu lời gọi ảo có guard/fallback thích hợp.
- **Constant folding, dead-code elimination** và common-subexpression optimizations.
- **Loop optimizations** như hoisting/unrolling khi an toàn.
- **Range-check elimination** khi chứng minh bounds hợp lệ.
- **Escape analysis/scalar replacement** khi một object không cần materialize thành heap object thực.

**JIT không được vi phạm Java semantics.** Các null/bounds/access checks cần thiết vẫn phải được bảo toàn; `int` overflow, exception paths, Java Memory Model, class loading, dynamic dispatch và GC reachability đều có những ràng buộc đúng đắn.

Một đoạn được JIT compile không bảo đảm chạy nhanh hơn mọi trường hợp: overhead của guards, code size, cache locality và dữ liệu runtime có thể khiến lợi ích thay đổi.

## 5. Hotness: method nóng và loop nóng

HotSpot thường dùng nhiều thông tin để quyết định compile:

- **Invocation count:** method được gọi nhiều lần.
- **Backedge count:** vòng lặp lặp lại nhiều lần, đi qua backward branch trong bytecode.
- **Type profiles:** loại receiver xuất hiện tại call sites, hỗ trợ inlining/devirtualization.
- **Branch behavior:** các đường nóng/lạnh và xác suất thực tế.
- **Compiler queues, code cache, tier, runtime workload:** chọn việc compile nào mang lại hiệu quả.

Một loop chạy lâu có thể làm method trở nên hot **dù method chỉ được gọi một lần**.

~~~java
static long countUp(int limit) {
    long sum = 0;
    for (int i = 0; i < limit; i++) {
        sum += i;
    }
    return sum;
}
~~~

Đây là đoạn method minh họa. Gọi `countUp(1_000_000)` một lần có thể đủ làm loop hot tùy JVM/settings, nhưng **không có lời hứa** “đúng 1.000.000 vòng thì JIT chắc chắn compile”.

**Đừng dùng con số thần kỳ** như “mọi method compile sau đúng 10.000 calls”. Thresholds và decisions thay đổi theo tiered policy, backedges, code characteristics, JVM flags và version. Flag `-XX:CompileThresholdScaling` của HotSpot có thể ảnh hưởng thresholds, nhưng không biến quá trình này thành một phép đếm đồng nhất.

## 6. Tiered Compilation: vì sao cần C1 và C2?

HotSpot thông dụng có **tiered compilation**, tận dụng interpreter và nhiều compiler tiers để cân bằng startup, profiling overhead và peak throughput.

| Tier thường gặp | Engine | Vai trò |
| --- | --- | --- |
| Level 0 | Interpreter | Bytecode execution; có thể thu thập profile |
| Level 1 | C1 | Compiled code với ít/không có profiling |
| Level 2 | C1 | Compiled code với limited profiling |
| Level 3 | C1 | Compiled code thu thập profiling đầy đủ hơn |
| Level 4 | C2 | Optimized compiled code dựa trên profile |

Level numbers là **chi tiết của HotSpot tiered policy**, không được JVMS quy định. Một số builds/configurations dùng compiler khác hoặc chỉ một phần tiers.

**C1 và C2 đều là JIT compilers.** C1 ưu tiên tốc độ biên dịch và hỗ trợ profiling phù hợp; C2 đầu tư tối ưu mạnh hơn cho hot code có đủ dữ liệu.

Không mặc định mọi method đều đi **0→1→2→3→4**. Một số methods chỉ interpreted, một số được C1 compile, một số lên C2, một số được OSR, hoặc bị deopt/recompiled. Chính sách chọn đường phụ thuộc đặc tính workload.

### Vì sao không compile hết bằng C2 ngay lúc startup?

Compilation tiêu tốn CPU, code-cache/native memory và thời gian. Một chương trình có thể load hàng nghìn methods nhưng chỉ sử dụng tỷ lệ nhỏ thường xuyên. **Adaptive optimization** giúp HotSpot chỉ đầu tư vào nơi có khả năng hoàn vốn. C2 compile sớm mà chưa có runtime profile có thể bỏ lỡ thông tin quan trọng để tối ưu.

## 7. OSR: chuyển vào compiled code ngay giữa vòng lặp

**On-Stack Replacement (OSR)** cho phép JVM chuyển trạng thái của **method đang thực thi** từ interpreter (hoặc compiled tier phù hợp) sang OSR compiled code tại điểm bytecode thích hợp.

~~~text
Một lần gọi method có loop rất dài
     |
     v
Interpreter thực thi vài vòng đầu
     |
     v
Loop backedge counter làm method/loop trở nên hot
     |
     v
JIT tạo OSR entry phù hợp
     |
     v
Runtime chuyển execution state tới OSR compiled entry
     |
     v
Các vòng sau có thể chạy native code
~~~

**OSR khác compilation từ method entry:** entry thông thường phục vụ lần gọi bắt đầu method; OSR entry phải thích nghi trạng thái locals/operand stack tại một loop BCI khi method đã chạy dở.

OSR **không bắt buộc** xảy ra với mọi loop. Nếu quá ít iterations, compilation chưa kịp, VM bật interpreter-only hoặc JIT policy không chọn, method có thể hoàn toàn chạy interpreted.

## 8. Speculative optimization và deoptimization

JIT có thể thấy một call site gần như chỉ gọi một implementation của interface. Nó có thể tối ưu đường phổ biến nhờ inlining cùng một **type guard**. Nếu về sau runtime gặp implementation mới, guard có thể thất bại.

~~~text
Profile: receiver type thường là AddOne
         |
         v
Compiled hot path có type guard
         |
         +-- AddOne -> fast inlined behavior
         |
         +-- Other type -> generic path hoặc deoptimization
                               |
                               v
                         correct Java behavior
~~~

**Deoptimization** giúp HotSpot khôi phục/di chuyển một compiled execution state về dạng tương thích để tiếp tục chạy theo Java semantics, khi speculative assumptions không còn phù hợp.

- Deoptimization không tự động là Java exception.
- Không phải cứ receiver type mới thì deoptimization **chắc chắn** xảy ra: compiled code có thể đã có generic fallback hoặc polymorphic inline cache.
- JVM có thể cập nhật profile rồi recompile code phù hợp hơn.
- Dynamic class loading và dynamic dispatch khiến cơ chế này đặc biệt quan trọng.

**Sai lầm thường gặp:** nói “C2 đoán type nên có thể trả kết quả sai”. Runtime guards và deoptimization tồn tại chính để giữ correctness ngay cả khi giả định bị bác bỏ.

## 9. Runnable Java 17+: cùng kết quả ở chế độ interpreter/JIT

Tạo file **`InterpreterJitDemo.java`**:

~~~java
public class InterpreterJitDemo {
    private static volatile long published;

    static int transform(int x) {
        return x * 3 + 1;
    }

    static long sumTransformed(int limit) {
        long total = 0;
        for (int i = 0; i < limit; i++) {
            total += transform(i);
        }
        return total;
    }

    public static void main(String[] args) {
        published = sumTransformed(100_000);
        System.out.println("sum=" + published);
        System.out.println("example=" + transform(7));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -g InterpreterJitDemo.java
java InterpreterJitDemo
~~~

**Stdout chính xác khi chương trình hoàn tất bình thường (không mở compilation diagnostics):**

~~~text
sum=14999950000
example=22
~~~

Vì:

~~~text
transform(i) = 3*i + 1
sum(i=0..99999) = 3*(99999*100000/2)+100000
                 = 14,999,950,000
transform(7) = 3*7+1 = 22
~~~

`total` là `long` nên tổng không bị overflow trong bài này; với `0 <= i < 100000`, `transform(i)` cũng nằm trong miền `int`.

### Thử ba JVM modes

Trên HotSpot hỗ trợ flags:

~~~bash
java -Xint InterpreterJitDemo
java -Xmixed InterpreterJitDemo
java -Xcomp InterpreterJitDemo
~~~

| Mode | Công dụng |
| --- | --- |
| `-Xint` | Interpreter-only: không JIT compile thành native methods |
| `-Xmixed` | Interpreter kết hợp JIT cho hot code |
| `-Xcomp` | Testing mode ưu tiên compilation; không được Oracle khuyến nghị dùng production |

**Java semantics và hai dòng kết quả phải giống nhau** trong các lần chạy thành công. Thời gian chạy, compilation events và GC logs có thể khác. Với chương trình ngắn, không được kết luận chỉ từ stdout rằng method `sumTransformed` đã OSR hay C2 compile.

**Điều ví dụ chứng minh:** cách thực thi có thể khác nhưng kết quả logic Java giữ nguyên. **Không chứng minh:** compiler nào chắc chắn được chọn hoặc throughput cải thiện bao nhiêu lần.

### Xem bytecode bằng javap

~~~bash
javap -c -p InterpreterJitDemo
javap -v -p InterpreterJitDemo
~~~

Những thứ cần tìm:

- `transform` có arithmetic bytecodes tương ứng.
- `sumTransformed` có loop và branch backward.
- `main` có method invocation và output code.

`javap` hiển thị **JVM bytecode trong class file**, **không phải native machine code JIT đã phát sinh**. Vì vậy thấy `invokestatic` ở output `javap` không chứng minh JIT sẽ luôn giữ nguyên call instruction sau inlining.

## 10. Runnable Java 17+: một call site với hai receiver types

Tạo file **`ReceiverProfileDemo.java`**:

~~~java
public class ReceiverProfileDemo {
    interface Operation {
        int apply(int n);
    }

    static final class AddOne implements Operation {
        public int apply(int n) {
            return n + 1;
        }
    }

    static final class DoubleIt implements Operation {
        public int apply(int n) {
            return n * 2;
        }
    }

    static int invoke(Operation op, int n) {
        return op.apply(n);
    }

    public static void main(String[] args) {
        Operation common = new AddOne();
        Operation other = new DoubleIt();

        long sum = 0;
        for (int i = 0; i < 20_000; i++) {
            sum += invoke(common, 3);
        }

        System.out.println("common-sum=" + sum);
        System.out.println("other-receiver=" + invoke(other, 3));
    }
}
~~~

~~~bash
javac ReceiverProfileDemo.java
java ReceiverProfileDemo
~~~

**Stdout:**

~~~text
common-sum=80000
other-receiver=6
~~~

20.000 calls với `AddOne.apply(3)` trả 4 cho tổng 80.000; lần gọi `DoubleIt.apply(3)` trả 6. JIT có thể quan sát receiver type của các calls trước đó và tối ưu common path, nhưng **hai dòng này không chứng minh deoptimization đã xảy ra**. Runtime có thể chưa compile method, hoặc compiled code xử lý polymorphism/fallback.

## 11. Quan sát compiler bằng HotSpot logs

Oracle JDK 25 hướng dẫn các flags:

~~~bash
java -XX:+PrintCompilation InterpreterJitDemo
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -XX:+PrintCompilation InterpreterJitDemo
~~~

Mở diagnostics sẽ khiến output toàn JVM **có thêm dòng log**. Vì thế hai dòng stdout ở phần 9 là **output của chương trình không bật diagnostics**, không phải toàn bộ output khi thêm flags ở đây.

`PrintCompilation` có thể cho biết:

- Compile task id và tên method.
- Compiler tier, OSR BCI trong những trường hợp được biểu diễn.
- Thời điểm VM ghi compilation event.
- Method trở nên non-entrant hoặc code bị thay thế theo lifecycle.

Một **dòng giả định để đọc định dạng**, không phải output chính xác của lần chạy:

~~~text
  94   67 %  3   InterpreterJitDemo::sumTransformed @ 4 (25 bytes)
~~~

Ký hiệu `%` thường gắn với OSR entry trong format HotSpot; các số chỉ là ví dụ. Không giả định cùng ID, tier, BCI hoặc order trên mọi JDK/máy.

`PrintInlining` có thể giải thích method nào được inlined hoặc bị từ chối do code size, profile, hotness, compiler budget. Nếu `transform` được inline vào caller, một invocation riêng có thể không còn ở optimized machine code nhưng source semantics vẫn được giữ.

**`-Xcomp` là testing mode**, có thể tăng compilation overhead và làm startup chậm. **`-Xint`** là công cụ useful để cô lập sự ảnh hưởng của JIT trong chẩn đoán, không phải cách làm production nhanh nhất.

## 12. Code Cache và chi phí của native compilation

Compiled machine code cần memory để tồn tại. HotSpot dùng **Code Cache** để lưu compiled methods cùng các code artifacts của JVM. Đây **không phải Java heap chứa POJO objects**.

| Tùy chọn | Mục đích |
| --- | --- |
| `-XX:ReservedCodeCacheSize=...` | Giới hạn code cache theo implementation |
| `-XX:+SegmentedCodeCache` | Chia code cache thành những segments khi phù hợp |
| `-XX:-TieredCompilation` | Vô hiệu hóa tiered policy của HotSpot |
| `-XX:CompileThresholdScaling=...` | Scale compilation thresholds |
| `-XX:+PrintCompilation` | In compiler events |
| `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` | In decision inlining |

Compiler sử dụng CPU threads, queues và một lượng memory cho intermediate representation; compiled code còn chiếm Code Cache và có thể ảnh hưởng instruction cache. Khi cache chật, runtime có thể phải reclaim compiled nmethods hoặc hạn chế compiling. Vì vậy “JIT compile mọi bytecode ngay lập tức” không phải cách miễn phí để tăng hiệu năng.

## 13. Interpreter/JIT không làm thay đổi ngữ nghĩa JVM

Đây là một invariant quan trọng:

- `new` vẫn phải cung cấp object identity đúng.
- `getfield`, `putfield`, `invokestatic`, `invokevirtual` phải đáp ứng đúng linking, access và dispatch rules.
- `NullPointerException`/`ArrayIndexOutOfBoundsException` xảy ra khi Java semantics yêu cầu; JIT chỉ được bỏ kiểm tra sau khi chứng minh an toàn hoặc chèn guard tương đương.
- `synchronized`/`volatile` và các quy tắc happens-before phải được bảo toàn.
- GC phải nhận diện references còn live ngay cả trong compiled frames.
- Dynamic class loading và interface polymorphism phải giữ behavior nhất quán.

JIT **có thể hợp nhất, sắp xếp lại hoặc loại những thao tác không quan sát được** khi an toàn, nhưng không được thay đổi observable program behavior được spec bảo vệ.

**Deoptimization là phần của lời giải correctness**, không phải lời thú nhận JIT “đoán rồi trả bừa”. Assumptions có guard và fallback đúng đắn.

## 14. Cách đo hiệu năng JIT mà không mắc bẫy benchmark

Một đo đạc kiểu:

~~~java
long start = System.nanoTime();
long answer = sumTransformed(100_000);
long duration = System.nanoTime() - start;
System.out.println(duration);
~~~

Đây chỉ là **đoạn minh họa cần đặt trong class có method `sumTransformed`**, không phải benchmark đáng tin tự thân.

Các sai số thường gặp:

1. **Startup và class loading** chi phối một chương trình quá ngắn.
2. **JIT warmup** chưa hoàn tất; compile jobs có thể đang chạy đồng thời.
3. **Dead-code elimination:** nếu kết quả không được sử dụng, optimizer có thể xóa công việc.
4. **Constant folding:** input cố định có thể được đơn giản hóa nhiều hơn workload thật.
5. **GC/scheduling/CPU frequency** tạo nhiễu.
6. **Compilation tiers/OSR** đổi giữa các iterations.
7. **Diagnostic logging** tăng overhead đo lường.
8. **Đo một lần** không đủ thông tin về phân bố latency.

**Quy trình phù hợp:** dùng JMH cho microbenchmarks với warmup, forks, measurement iterations, sink/Blackhole và profiler phù hợp. Nếu mục tiêu là server, xem startup, first-request latency, steady-state throughput, P95/P99, CPU và memory trên workload đại diện. Đừng tuyên bố “JIT nhanh hơn chính xác N lần” từ ví dụ số cộng.

## 15. Khi nào Interpreter quan trọng, khi nào JIT quan trọng?

| Tình huống | Diễn giải |
| --- | --- |
| CLI chỉ chạy một task nhỏ rồi thoát | Compilation cost có thể không hoàn vốn; startup có vai trò lớn |
| Server chạy lâu với vài hot handlers | JIT có thể tối ưu hiệu quả, cải thiện throughput sau warmup |
| Ứng dụng có rất nhiều code paths hiếm | Interpreter giữ đường chạy cho cold code mà không compile mọi thứ |
| Method có loop lặp nhiều nhưng chỉ gọi một lần | OSR có thể giúp chuyển hot loop sang compiled code |
| Call site chủ yếu thấy một receiver type | JIT có thể guarded-inline đường thường gặp |
| Runtime nạp implementation mới | Guard/fallback/deopt giúp giữ semantics |
| Code Cache hoặc compiler CPU hạn chế | Biên dịch quá nhiều có chi phí, cần quan sát runtime |
| Lỗi chỉ thấy khi bật JIT | Có thể so `-Xint` để chẩn đoán; không mặc định lỗi do JIT trước khi có chứng cứ |

Một service “chạy nhanh sau 30 giây” **chưa chắc hoàn toàn do JIT**: caches, network/database connections, JIT, class initialization và GC có thể đều tham gia. Cần công cụ đo để xác định.

## 16. Các hiểu nhầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`javac` sinh machine code cho CPU hiện tại.” | `javac` thường sinh JVM bytecode. |
| “Interpreter thực thi source Java từng dòng.” | Nó thực thi bytecode JVM. |
| “Java chỉ interpreted, không native.” | HotSpot thường kết hợp interpreter với JIT. |
| “JIT compile toàn bộ classpath lúc startup.” | HotSpot dùng adaptive profiling/policy; nhiều cold methods không compile. |
| “Hot method = được gọi đúng 10.000 lần.” | Invocation/backedge counts, tiers, policies và runtime state chi phối. |
| “Method gọi đúng một lần không thể được JIT.” | Long loop có thể dùng OSR. |
| “C1 là interpreter, C2 mới là JIT.” | C1 và C2 đều là JIT compilers. |
| “Tiered luôn buộc method qua tất cả levels 0 đến 4.” | Có nhiều đường transitions/skip tiers. |
| “JIT tối ưu bằng cách bỏ hết null/bounds checks bất chấp dữ liệu.” | Chỉ được tối ưu nếu giữ đúng semantics/guards. |
| “JIT compile một method rồi code luôn bất biến.” | Có recompile/invalidation/deoptimization. |
| “Deoptimization là exception Java.” | Đây là cơ chế runtime để bảo toàn thực thi. |
| “OSR là khi method gọi lại từ đầu.” | OSR có thể chuyển method đang chạy tại loop BCI. |
| “`javap -c` cho thấy native machine code.” | Nó cho biết JVM bytecode class file. |
| “`-Xint` tắt GC.” | `-Xint` vô hiệu JIT, GC vẫn cần hoạt động. |
| “`-Xcomp` luôn nhanh hơn mixed mode.” | Testing mode có overhead/thiếu profile; không phải mặc định production. |
| “Code Cache chính là Java heap.” | Code Cache lưu compiled code, khác Java object heap. |
| “PrintCompilation ID/tier luôn giống nhau giữa mọi runs.” | Events phụ thuộc VM settings/runtime. |
| “Một benchmark chạy một lần đủ chứng minh peak throughput.” | Cần warmup, forks, đo noise và JIT state. |
| “JIT làm Java không còn portable.” | Bytecode portable trong phạm vi Java platform; native compilation do runtime thích nghi CPU. |
| “AOT và JIT là cùng một thứ.” | AOT biên dịch/chuẩn bị trước; JIT compile lúc chạy. |

## 17. Tự kiểm tra có đáp án

**1.** `javac` thường tạo gì?  
**Đáp án:** Java class file chứa JVM bytecode và metadata; không phải native CPU instructions cho mọi nền tảng.

**2.** Interpreter giải quyết nhu cầu gì?  
**Đáp án:** Cho phép thực thi bytecode với chi phí compilation ban đầu thấp, hữu ích cho cold code/startup và profiling.

**3.** JIT mang lại lợi ích gì?  
**Đáp án:** Tạo native compiled code cho hot code, tối ưu dựa trên runtime profile và giảm execution overhead.

**4.** Vì sao compiler không tối ưu toàn bộ methods ngay?  
**Đáp án:** Compilation tiêu tốn CPU/memory/code-cache; nhiều methods không chạy đủ để hoàn vốn.

**5.** Hai tín hiệu hotness phổ biến là gì?  
**Đáp án:** Method invocation counts và loop backedge counts, ngoài type/branch profiles.

**6.** Level 3 và Level 4 khác nhau thế nào trong HotSpot tiered mode thông dụng?  
**Đáp án:** Level 3 thường là C1 với profiling đầy đủ hơn; Level 4 thường là C2 optimized code.

**7.** Một loop rất dài có thể dùng JIT dù chỉ gọi method một lần không?  
**Đáp án:** Có thể, qua OSR nếu runtime chọn/biên dịch kịp.

**8.** Receiver type mới nhất thiết gây Java exception?  
**Đáp án:** Không. Generic dispatch, guards và deoptimization vẫn phải cho Java behavior đúng.

**9.** Vì sao chương trình InterpreterJitDemo luôn in cùng kết quả với `-Xint` và `-Xmixed`?  
**Đáp án:** Execution strategies khác nhưng phải giữ cùng Java semantics.

**10.** Để xem compiler đang compile methods nào trên HotSpot, dùng gì?  
**Đáp án:** Thử `-XX:+PrintCompilation`, lưu ý diagnostic output không cố định.

**11.** `javap` đủ chứng minh một method được inline lúc runtime?  
**Đáp án:** Không, nó hiển thị bytecode, không phải optimized machine code.

**12.** JIT có thể ảnh hưởng startup latency không?  
**Đáp án:** Có. Compilation/profiling có overhead; tiered policy cân bằng startup và throughput.

**13.** Có nên dùng `-Xcomp` trong production vì “compile là luôn tốt”?  
**Đáp án:** Không. Oracle đánh dấu testing mode; cần đo workload thực.

**14.** Deoptimization có nhiệm vụ gì?  
**Đáp án:** Giữ execution đúng khi assumptions cho optimized code không còn thỏa, có thể fallback/recompile.

## 18. Nguồn chính thức

- [Oracle Java SE 25 — Java Virtual Machine Guide](https://docs.oracle.com/en/java/javase/25/vm/index.html): JVM/HotSpot runtime, tiered compilation và performance.
- [Oracle JDK 25 — java Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-Xint`, `-Xmixed`, `-Xcomp`, `PrintCompilation`, `PrintInlining`, `TieredCompilation`, `CompileThresholdScaling` và Code Cache options.
- [Oracle HotSpot Performance Enhancements](https://docs.oracle.com/en/java/javase/21/vm/java-hotspot-virtual-machine-performance-enhancements.html): tiered compilation, escape analysis và optimization.
- [Oracle — Java HotSpot Performance Engine Architecture](https://www.oracle.com/java/technologies/whitepaper.html): adaptive optimization, hot-spot detection và deoptimization; whitepaper là tài liệu kiến trúc lịch sử, không cam kết chính sách mỗi bản JVM giống hệt.
- [OpenJDK HotSpot — compilationPolicy.hpp](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compilationPolicy.hpp): compiler tiers và policy implementation; nhánh master có thể khác bản đang dùng.
- [JVMS Java SE 25 — `2.11` Instruction Set Summary](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.11): khái niệm bytecode instructions.
- [JVMS Java SE 25 — Chapter 6 Instruction Set](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html): semantics của `iadd`, `invokevirtual`, `ireturn` và những bytecodes khác.
- [JDK 25 — javap](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javap.html): xem bytecode/constant pool từ class file.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): benchmarking JVM đáng tin hơn một lần `nanoTime`.
- [Microsoft Java Developers — Tiered Compilation in OpenJDK](https://devblogs.microsoft.com/java/how-tiered-compilation-works-in-openjdk/): cách đọc C1/C2, profiling và compilation logs; đối chiếu cùng Oracle/OpenJDK khi cần xác nhận version.

**Kết luận:** Interpreter cho phép JVM **thực thi bytecode sớm và rẻ về compilation**; JIT **đầu tư sinh native code tối ưu cho các đoạn hot** để tăng hiệu năng khi chạy lâu. Tiered compilation, runtime profiling, OSR, deoptimization và Code Cache là các thành phần giúp HotSpot cân bằng **startup, warmup, throughput, latency, CPU/memory overhead** trong khi luôn phải bảo toàn Java semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-002">Đường đi từ phương thức ít chạy đến đoạn mã nóng</a>

<details>
<summary>Xem chi tiết</summary>

# Đường đi từ phương thức ít chạy đến đoạn mã nóng trong HotSpot JVM

## 1. Câu hỏi cốt lõi: vì sao Java không biên dịch tối ưu mọi phương thức ngay?

Khi chạy một chương trình Java trên **HotSpot JVM**, các class files mang **bytecode**. HotSpot thường bắt đầu thực thi code mới bằng **interpreter**, quan sát hành vi runtime, rồi có thể sử dụng **Just-In-Time (JIT) compilation** để chuyển những phương thức/vòng lặp được dùng thường xuyên thành **machine code**.

Một phương thức ít chạy (*cold method*) có thể không bao giờ cần được JIT biên dịch. Một phương thức được gọi nhiều lần hoặc có vòng lặp chạy dài có thể trở thành **hot code** và đáng chi trả chi phí biên dịch để những lần chạy sau nhanh hơn.

```text
.class / bytecode
       |
       v
Class loading + verification + linking khi cần
       |
       v
Interpreted execution (tier 0)
       |
       +--- ít gọi, ít chạy ---> giữ interpreted hoặc chiến lược khác
       |
       +--- nhiều invocations / loop backedges
       |             |
       |             v
       |       profiling + compilation policy
       |             |
       |             v
       |       compilation queue / compiler threads
       |             |
       |             v
       |          C1 compiled code
       |             |
       |         thêm runtime profiles,
       |         compiler nhận thấy đáng tối ưu
       |             |
       |             v
       |          C2 compiled code
       |             |
       |       guard/assumption thất bại?
       |             |
       |             v
       +------ deoptimization / interpreter / recompile
```

Đây là **sơ đồ trực giác**, không phải một chuỗi bắt buộc cho mọi phương thức. Có đường nhảy cấp, có phương thức chỉ qua C1, có phương thức không được biên dịch, có compilation xảy ra trong vòng lặp qua **OSR**, và có trường hợp JIT hủy hoặc thay một bản machine code cũ.

### Cần phân biệt ba khái niệm

1. **Warm-up:** thời gian đầu chương trình chạy khi classes được nạp, thông tin runtime được tích lũy và JIT có thể bắt đầu tối ưu.
2. **Hotness:** đánh giá nội bộ của VM rằng một method/loop đủ quan trọng để biên dịch hoặc biên dịch lại.
3. **Tiered compilation:** chính sách HotSpot dùng các cấp thực thi/biên dịch khác nhau để cân bằng **startup time**, **profiling overhead**, **peak performance** và **chi phí compilation**.

**Không có hợp đồng của Java Language Specification** rằng “lần gọi thứ 10.000 thì phương thức chắc chắn chuyển sang C2”. Hotness thresholds và các đường chuyển cấp thuộc **HotSpot implementation**, phụ thuộc JDK build, JVM flags, runtime load và workload.

## 2. Java source, bytecode và machine code là ba thứ khác nhau

Ví dụ:

```java
static int square(int x) {
    return x * x;
}
```

Sau khi dùng `javac`, class file có bytecode biểu diễn phép lấy tham số, nhân số nguyên và trả kết quả. Khi chạy, HotSpot có nhiều lựa chọn để thực hiện đúng semantics của các instructions đó:

- **Interpreter** đọc/thực thi bytecode theo runtime engine.
- **C1 compiler** tạo machine code nhanh, có thể thu thập thêm profiling tùy tier.
- **C2 compiler** dùng các phân tích/tối ưu phức tạp hơn để tạo machine code tối ưu.
- JIT có thể **inline** `square` vào caller, vì vậy không phải lúc nào bạn cũng thấy một lời gọi native tới compiled code riêng biệt của `square`.

```text
Java source: square(x)
        |
      javac
        |
        v
Bytecode in square.class
        |
   HotSpot runtime
        |
        +--> Interpreter executes bytecode
        +--> C1 creates compiled machine code
        +--> C2 creates aggressively optimized machine code
                  |
                  +--> có thể inline square vào caller
```

**JIT không thay đổi file `.class` trên đĩa khi tối ưu trong process.** Machine code do JIT tạo được lưu trong các cấu trúc runtime (như **code cache/nmethods**), được dùng khi phù hợp và có thể bị thay thế/hủy.

### 2.1. “Compiled” ở đây nghĩa gì?

`javac` đã biên dịch **source → bytecode** trước khi chạy. JIT là bước khác: **bytecode/runtime IR → machine code cho CPU cụ thể**, thực hiện trong runtime.

Vì vậy hai câu sau đều có thể đúng:

- “Java được biên dịch trước khi chạy” — đúng ở giai đoạn `javac` tạo bytecode.
- “HotSpot có thể thông dịch bytecode rồi JIT biên dịch khi đang chạy” — đúng về thực thi runtime.

### 2.2. Không phải mọi JVM đều bắt buộc C1/C2

JVMS quy định **hành vi bytecode**, không bắt buộc có interpreter tên HotSpot, C1/C2, tiered compilation hoặc số lần gọi phải được đếm theo một cách cụ thể.

Bài này tập trung **HotSpot với tiered compilation được hỗ trợ**, đặc biệt dễ thực hành bằng **JDK 17+**. JVM khác như OpenJ9/GraalVM hay những cấu hình HotSpot khác có thể có compiler pipeline riêng.

## 3. Tại sao phương thức ít chạy thường bắt đầu từ interpreter?

**Interpreter** có lợi thế **không cần trả toàn bộ chi phí tối ưu native code trước lần dùng đầu tiên**. Nếu một phương thức chỉ chạy một lần trong startup, compile C2 đắt đỏ có thể lãng phí nhiều CPU/memory hơn thời gian tiết kiệm được.

Ví dụ:

```java
static void printHelp() {
    System.out.println("Usage: ...");
}
```

Nếu `printHelp()` chỉ chạy một lần khi người dùng nhập `--help`, interpreter có thể thực thi đủ nhanh; một JIT compilation nhiều vòng phân tích khó tạo giá trị ròng.

Ngược lại:

```java
static int decode(int x) {
    return (x * 31) ^ (x >>> 3);
}
```

Nếu `decode` được gọi hàng triệu lần ở đường xử lý dữ liệu, chi phí JIT có thể được bù bằng lợi ích lặp lại.

### 3.1. Startup time vs steady-state performance

- **Startup:** chạy được ứng dụng và trả kết quả đầu tiên nhanh.
- **Warm-up:** JIT thu thập profile, compile/recompile; tốc độ có thể thay đổi.
- **Steady state:** các đường hot có thể chạy compiled code tối ưu hơn.

Nhưng **không hứa steady state đến đúng sau N giây**. Một web service có thể luôn gặp những phương thức mới được gọi lần đầu sau mỗi request mới, plugin mới hoặc class loader mới.

### 3.2. Interpreted không có nghĩa “không an toàn”

Dù interpreted hay compiled, code Java vẫn phải tuân thủ semantics (kiểu, exceptions, concurrency rules, access checks) phù hợp. JIT không được hợp lệ hóa một hành vi Java sai chỉ vì nó chạy nhanh hơn.

### 3.3. Interpreter không phải một cấp thấp vĩnh viễn

Khi phương thức/vòng lặp trở nên hot, compilation policy có thể yêu cầu tạo compiled entry; lần gọi tiếp theo có thể đi vào native code, hoặc với loop dài JVM có thể chuyển giữa chừng qua OSR.

## 4. Hotness: invocation counters và loop backedges

HotSpot theo dõi **số lần gọi phương thức** và **số lần quay lại trong một vòng lặp** (backedges) cùng thông tin profiling khác để quyết định có nên compile/recompile.

Hai nguồn tín hiệu rất khác nhau:

| Tín hiệu | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Method invocation count** | `process()` được gọi liên tục từ request handler | Method hot vì **nhiều lần vào method** |
| **Loop backedge count** | `process()` chỉ được gọi một lần nhưng vòng lặp có hàng triệu iterations | Một method/loop hot dù **số invocation ít** |

### 4.1. Nhiều lần gọi, mỗi lần ít việc

```java
static int addOne(int x) {
    return x + 1;
}

static int callManyTimes() {
    int result = 0;
    for (int i = 0; i < 100_000; i++) {
        result += addOne(i);
    }
    return result;
}
```

Đây là **mã minh họa** trong class, chưa phải chương trình `main` độc lập. `addOne` được gọi nhiều lần, nhưng JIT có thể **inline** nó vào caller; do đó profiling log không nhất thiết cho thấy một compiled native method độc lập ở mọi hoàn cảnh.

### 4.2. Một lần gọi, vòng lặp rất dài

```java
static long bigLoop(int n) {
    long sum = 0;
    for (int i = 0; i < n; i++) {
        sum += i;
    }
    return sum;
}
```

Nếu `bigLoop(1_000_000)` được gọi một lần, **invocation count chỉ là một**, nhưng **backedge count** có thể rất lớn. Điều đó có thể kích hoạt **on-stack replacement (OSR)**.

### 4.3. “Hot method” và “hot loop” không nhất thiết giống nhau

Một method có thể nổi bật vì số calls; loop trong một method khác nổi bật vì số iterations. HotSpot có thể compile method entry riêng, compile OSR entry riêng, hoặc một trong hai theo policy/runtime state.

**Không coi số source-code dòng lặp hay độ dài phương thức là thước đo trực tiếp của hotness.** Runtime execution frequency và compiler heuristics quan trọng hơn.

## 5. Tiered compilation: năm mức thực thi của HotSpot truyền thống

Mô hình tiered compilation trong HotSpot C1/C2 thường mô tả **mức 0–4**:

| Level | Engine | Điểm cốt lõi |
| --- | --- | --- |
| **0** | **Interpreter** | Thực thi bytecode; có thể thu thông tin runtime |
| **1** | **C1, simple compilation** | C1 native code, **không thu full profiling** |
| **2** | **C1, limited profiling** | C1 có các counters cơ bản, ít overhead hơn full profiling |
| **3** | **C1, full profiling** | C1 có instrumentation/profile phong phú hơn để hỗ trợ lựa chọn và C2 |
| **4** | **C2 optimization** | Native code tối ưu mạnh, dùng profile/assumptions khi phù hợp |

Tên trong OpenJDK có thể gặp:

```text
CompLevel_none                  (0)
CompLevel_simple                (1)
CompLevel_limited_profile       (2)
CompLevel_full_profile          (3)
CompLevel_full_optimization     (4)
```

Các tên này là **thông tin triển khai HotSpot**. **Không suy ra luôn phải đi tuần tự 0 → 1 → 2 → 3 → 4.** Thực tế một đường rất thường gặp là:

```text
Interpreter (0) ---> C1 with profiling (3) ---> C2 (4)
```

Và cũng có thể:

```text
Interpreter (0) ---> C1 limited profiling (2)
                        |
                        v
                     C1 full profiling (3)
                        |
                        v
                        C2 (4)
```

Mọi mô hình trên **chỉ là lộ trình có thể**, không phải timeline cứng cho từng method.

### 5.1. Tại sao có nhiều tier C1?

C1 thường biên dịch nhanh hơn C2, vì vậy có thể cung cấp native code sớm hơn. Level 3 thu profile chi tiết hơn để C2 có dữ liệu cho speculative optimizations, nhưng việc thu profile cũng có overhead.

Level 2 có thể được dùng khi compilation queues/load tình huống khiến việc chạy C1 full profiling quá lâu chưa đáng, rồi có thể chuyển tiếp sau. Level 1 có thể phù hợp với những methods không cần profile hoặc chính sách đặc biệt.

### 5.2. C2 có phải lúc nào cũng nhanh hơn C1 không?

Không có bảo đảm **mọi method riêng lẻ** chạy C2 sẽ nhanh hơn C1 trong mọi microbenchmark. C2 thường hướng đến peak optimization, nhưng code size, profile quality, JIT decisions, CPU microarchitecture, warm-up và compiler overhead ảnh hưởng.

Các performance claims cần đo end-to-end theo workload, không chỉ nhìn “level 4” rồi tuyên bố nhanh hơn chính xác một tỷ lệ.

### 5.3. Tier names có thể không xuất hiện giống nhau trên JVM khác

Các tools có thể hiện level numbers trong HotSpot log; JVM khác hoặc HotSpot build thiếu C2 có thể có cách mapping/compilation policy riêng. Khi đọc log, cần kiểm tra `java -version` trước.

## 6. Từ invocation/backedge counters đến quyết định compile

Một compilation policy phải cân bằng hai loại chi phí:

1. **Chi phí chạy interpreted hoặc C1 profiling** ở mỗi lần thực thi.
2. **Chi phí biên dịch, code cache, compile threads, profile storage** và rủi ro compiled code không còn được dùng nhiều.

Ngưỡng kích hoạt có thể bị ảnh hưởng bởi:

- Invocation/backedge counts.
- Tốc độ tăng của các counts và method activity.
- Hàng đợi C1/C2, số compiler threads và tải CPU.
- Tier đang thực thi và quality của profile hiện có.
- Compile directives, excluded methods, JVM flags.
- Code cache availability.
- Deoptimization/uncommon traps hoặc trạng thái compiled versions.

**Không viết “`CompileThreshold=10000` nghĩa đúng lời gọi thứ 10000 mọi method sẽ thành C2”.** Tiered policy có nhiều ngưỡng và quy tắc kết hợp counts/counters, cộng thêm queue/load heuristics.

### 6.1. Threshold không là hợp đồng API

Có các cờ HotSpot như:

```text
-XX:CompileThreshold=...
-XX:TieredStopAtLevel=...
-XX:+TieredCompilation
-XX:-TieredCompilation
```

Nhưng tương tác các cờ khác nhau theo JDK/VM. Với **tiered compilation** bật, không nên coi **`CompileThreshold` đơn lẻ** là cách chính xác điều khiển mọi đường chuyển tier.

### 6.2. Compilation threshold không đo đơn thuần theo wall time

Một method chạy 10 giây vì chờ I/O có thể có rất ít invocations và không có loop backedges nóng. Ngược lại, một method tính toán trong vài trăm mili giây có thể đã vượt nhiều counters.

“Hot” không đồng nghĩa “method xuất hiện lâu nhất trong thread dump” hay “method tốn nhiều wall-clock vì đang blocked”.

### 6.3. Compilation queues

Khi policy quyết định một method đáng compile, yêu cầu có thể vào **compiler queue**. Compiler thread làm việc độc lập với mutator thread, tạo nmethod trong **code cache**. Trong lúc chờ, application có thể tiếp tục chạy ở interpreted/C1 code.

```text
Application thread:
  interpreted / C1 execution --- tiếp tục công việc --->
                    |
                    +-- enqueue method for C2 compilation
                                      |
Compiler thread:                  compile, optimize
                                      |
                                      v
                                publish nmethod
                                      |
Application later:                    use compiled entry
```

Vì compilation có thể diễn ra bất đồng bộ, **thời điểm method chuyển cấp không được xác định bởi một dòng source duy nhất**.

## 7. Method Data và profiling: JIT học từ runtime như thế nào?

Trong HotSpot, **MethodData** và các runtime profiling structures có thể ghi nhận những tín hiệu như:

- Lượng invocations/backedges ở mức cần thiết.
- Tần suất đường nhánh thường được chọn.
- Kiểu runtime xuất hiện ở những call sites.
- Loại exception hoặc hình dạng control flow theo nơi có instrumentation.
- Thông tin phục vụ inlining, virtual-call optimization, loop optimization.

Ví dụ:

```java
interface Handler {
    int apply(int x);
}

static int dispatch(Handler h, int x) {
    return h.apply(x);
}
```

Đây là **code minh họa chưa có `main`**. Nếu mọi lần chạy thực tế `h` là một implementation duy nhất, profile có thể hỗ trợ JIT tối ưu call site. Nếu về sau một implementation khác xuất hiện, giả định tối ưu có thể không còn phù hợp, dẫn tới deoptimization/recompilation khi cần.

### 7.1. Monomorphic, bimorphic, megamorphic — hiểu theo call site

Một call site có thể thấy:

- **Monomorphic:** một receiver type là chủ yếu/duy nhất trong profile.
- **Bimorphic:** hai receiver types nổi bật.
- **Megamorphic:** nhiều receiver types khiến các chiến lược type-specialized optimization ít hiệu quả hơn.

Đây là **mô tả runtime profile**, không phải thuộc tính cố định của cú pháp `interface`. Không có một công thức số implementations duy nhất đảm bảo chính xác mọi quyết định của JIT.

### 7.2. Profile-guided optimization có thể speculative

HotSpot có thể tối ưu dựa trên thông tin quan sát được kèm **guards**. Nếu runtime đi theo đường ít gặp hoặc xuất hiện receiver type mới, VM phải duy trì hành vi đúng — bằng slow path, guard failure, deoptimization hoặc recompilation.

**Speculation không cho phép JIT làm sai Java semantics.**

### 7.3. Profiling không đồng nghĩa mọi nhánh đều đã được chạy

Nếu một nhánh exception hiếm chưa từng xuất hiện trong warm-up, C2 có thể tối ưu cho đường phổ biến, nhưng nó vẫn phải có cơ chế đúng để xử lý exception khi phát sinh. Benchmark chỉ làm nóng “happy path” không chứng minh production code luôn ở điều kiện đó.

## 8. C1 vs C2: chi phí biên dịch và chất lượng code

### 8.1. C1 — native code sớm, có thể gắn instrumentation

C1 thường được mô tả là **client compiler**, thiết kế tương đối nhanh và dùng trong các tier thấp để cải thiện startup/warm-up mà chưa trả toàn bộ chi phí C2. Khi ở profiling tier, C1 có thể chèn instrumentation thu type/branch/counter data.

### 8.2. C2 — tối ưu sâu hơn

C2 thường được mô tả là **server/optimizing compiler**. Những kỹ thuật có thể gồm:

- Inlining qua method boundaries khi profile/size cho phép.
- Devirtualization/speculative type-check optimization.
- Common subexpression elimination, constant propagation.
- Loop optimizations (unrolling, range-check elimination… khi an toàn).
- Escape analysis và scalar replacement trong những điều kiện phù hợp.
- Dead-code elimination và những IR/low-level optimizations khác.

Không phải mọi kỹ thuật đều được áp dụng vào mọi method; optimization pipeline phụ thuộc compiler IR, flags, method shape, CPU capabilities và runtime assumptions.

### 8.3. Một method có thể có nhiều compiled versions

Một method có thể có:

- Compiled C1 entry (tier 3).
- Compiled C2 entry (tier 4).
- OSR nmethod cho một loop backedge.
- Những compiled versions cũ ở trạng thái không còn được dùng để vào method.
- Code được inline ở những callers khác.

**Không gộp “một Java method” với “duy nhất một đoạn machine code bất biến”.**

### 8.4. “JIT chuyển interpreter thành C2 rồi hết” là sơ đồ thiếu

JIT không phải chỉ chạy một lần. Runtime có thể tiếp tục profiling, nâng tier, deoptimize và recompile nếu môi trường thay đổi, hoặc code cache cần quản lý.

## 9. OSR — chuyển sang compiled code ngay giữa vòng lặp

**On-Stack Replacement (OSR)** đặc biệt quan trọng với bài “từ phương thức ít chạy thành đoạn nóng”.

Giả sử một method được gọi đúng một lần nhưng chạy loop lâu:

```java
static long aggregate(int n) {
    long result = 0;
    for (int i = 0; i < n; i++) {
        result += (i & 255);
    }
    return result;
}
```

Nếu interpreter/level thấp phát hiện **loop backedges** nóng, HotSpot có thể compile một **OSR version** của method cho một **bytecode index (BCI)** cụ thể, rồi thay execution state ngay khi loop đang chạy.

```text
Thread chạy aggregate(n)
     |
     v
Interpreter đi vào loop
     |
     +-- iterations rất nhiều --> backedge counter vượt ngưỡng thích hợp
     |
     +-- yêu cầu OSR compilation ở loop-entry/backedge BCI
                         |
                         v
                  OSR compiled nmethod
                         |
             thread đang ở loop chuyển vào code mới
                         |
                         v
                  tiếp tục đúng trạng thái local/stack
```

### 9.1. OSR khác method-entry compilation

- **Normal compiled entry:** những lần gọi method sau có thể vào machine code từ đầu.
- **OSR compiled entry:** một frame đang thực thi một loop có thể được chuyển sang compiled execution **mà không cần return rồi gọi lại method**.

**Một compiled OSR version không tự đồng nghĩa normal method entry đã có level 4.** Log thường có ký hiệu riêng cho OSR, ví dụ ký tự **`%`** và `@ bytecodeIndex`.

### 9.2. Log mẫu, không phải đầu ra cố định

```text
27   15 %     3       HotMethodDemo::loop @ 4 (25 bytes)
```

Đây là **mẫu hình giống sự kiện quan sát được trong thử nghiệm HotSpot**, không phải cam kết timestamp/compile ID/tier trong mọi lần chạy. Ký hiệu `%` cho biết OSR compilation; `@ 4` cho biết vị trí bytecode index có liên quan.

### 9.3. Khi nào OSR không xuất hiện?

- Loop chưa chạy đủ nhiều để policy thấy đáng compile.
- Method đã được compiled ở mức phù hợp ngay từ đầu.
- JIT bị tắt hoặc compilation bị hạn chế bằng flags.
- Workload/JIT optimizer làm đổi shape loop hoặc code path.
- VM chọn chiến lược khác hoặc compile chưa hoàn tất trước khi loop kết thúc.

**Không viết test assert “mọi vòng lặp trên 10.000 iterations đều phải có OSR”.**

## 10. Deoptimization — khi tối ưu từng đúng nhưng giả định không còn phù hợp

JIT thường tối ưu cho **đường được quan sát nhiều**. Nhưng runtime có thể xuất hiện tình huống mới.

Ví dụ:

```java
interface Worker {
    int work(int x);
}

final class FastWorker implements Worker {
    public int work(int x) { return x + 1; }
}

final class AlternateWorker implements Worker {
    public int work(int x) { return x * 2; }
}
```

Nếu một call site thường chỉ gặp `FastWorker`, JIT có thể tối ưu/inlining dựa trên profile. Khi `AlternateWorker` xuất hiện, một guard/class hierarchy assumption có thể cần được kiểm tra và runtime có thể **deoptimize** một nmethod nếu assumptions của optimized version bị invalidated.

### 10.1. Deoptimization thực hiện gì?

Ở mức trực giác:

1. Runtime phát hiện compiled code không còn phù hợp với điều kiện mà nó giả định.
2. JVM tạo/khôi phục **interpreter-compatible execution state** khi cần.
3. Thread có thể tiếp tục bằng interpreter/code khác mà không mất Java semantics.
4. Policy có thể compile lại với profile/assumptions phù hợp hơn.

Không phải mọi receiver type mới đều làm deoptimization. Optimized code có thể đã chứa checks/slow paths; việc deopt cụ thể phụ thuộc phiên bản và compiler strategy.

### 10.2. Uncommon traps

HotSpot có cơ chế **uncommon traps** cho những paths giả định hiếm gặp, như một nhánh/kiểu không được profile trước hoặc một tình huống cần quay về runtime.

Trên logs diagnostics, có thể thấy khái niệm `uncommon_trap`; điều đó không tự chứng minh ứng dụng có exception hay bug.

### 10.3. “Made not entrant” và “made zombie”

Một bản compiled code có thể được đánh dấu **not entrant**: runtime không dùng nó cho những lần method entry mới nữa, ví dụ vì đã có bản tối ưu hơn hoặc cần vô hiệu hóa code cũ. Quản lý lifecycle code cache về sau có thể chuyển code cũ sang trạng thái **zombie** khi không còn frames dùng phù hợp.

Các trạng thái này **không phải Java object bị GC thu hồi** theo kiểu application heap, và dòng log đó không nhất thiết là lỗi.

### 10.4. Deopt không được thay đổi kết quả đúng của chương trình

Nếu compiler tối ưu với assumptions, JVM vẫn phải bảo toàn Java semantics, bao gồm return values, exceptions, memory visibility và những side effects theo đặc tả.

## 11. Code cache — machine code JIT nằm ở đâu?

HotSpot lưu compiled methods ở **code cache**, một tập vùng bộ nhớ runtime cho code do JVM/JIT sinh. Nó khác Java heap chứa những object ứng dụng.

```text
Java heap:
   Object instances, arrays, class mirrors

Native/runtime memory:
   Code cache:
     - compiled C1 methods
     - compiled C2 methods
     - runtime/adapters/stubs theo VM
   Metaspace:
     - class metadata, method metadata theo HotSpot
```

Sơ đồ chỉ là cách phân loại khái niệm; các segment code cache cụ thể và phạm vi từng nhóm thay đổi theo JDK/build.

### 11.1. Compilation không miễn phí

Chi phí JIT bao gồm:

- CPU cho compiler threads.
- Memory cho compiler data structures và profiling.
- Code cache space cho compiled code.
- Instruction-cache pressure của machine code.
- Deoptimization/recompilation nếu profile thay đổi.

Không hợp lý nếu ép compile mọi method ngay startup chỉ vì compiled code có thể chạy nhanh hơn interpreted code.

### 11.2. Code cache đầy ảnh hưởng gì?

Nếu code cache đạt giới hạn khả dụng, JVM có thể hạn chế hoặc vô hiệu hóa thêm JIT compilation; app vẫn có thể tiếp tục nhưng throughput/latency có thể giảm. Ngữ nghĩa chính xác phụ thuộc HotSpot version/options.

Kiểm tra logs và metrics trước khi tăng code cache tùy tiện.

### 11.3. Thông số theo runtime

Một số flags để nhận diện:

```text
-XX:ReservedCodeCacheSize=...
-XX:+SegmentedCodeCache
```

Đây là HotSpot-specific options, không là phần bắt buộc trong Java Specification. Khi nghiên cứu, xem `java -XX:+PrintFlagsFinal -version` để biết giá trị thực trên JVM mục tiêu.

## 12. Java 17+ demo độc lập: chứng minh tính đúng, quan sát đường nóng qua JIT log

Tạo tệp **`HotMethodDemo.java`**:

```java
public final class HotMethodDemo {
    private static volatile long blackhole;

    static int transform(int x) {
        return (x * 31 ^ (x >>> 3)) & 1023;
    }

    static long loop(int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += transform(i);
        }
        return sum;
    }

    public static void main(String[] args) {
        long calls = 0;
        for (int i = 0; i < 20_000; i++) {
            calls += transform(i & 255);
        }
        long loops = loop(1_000_000);
        blackhole = calls + loops;
        System.out.println("method calls=20000");
        System.out.println("call checksum=" + calls);
        System.out.println("loop checksum=" + loops);
        System.out.println("combined checksum=" + blackhole);
    }
}
```

### 12.1. Biên dịch/chạy theo chế độ mặc định

```bash
javac --release 17 HotMethodDemo.java
java HotMethodDemo
```

**Stdout chính xác của chương trình khi chạy thành công:**

```text
method calls=20000
call checksum=10169104
loop checksum=511499680
combined checksum=521668784
```

Tệp trên đã được thử bằng `javac --release 17` và chạy trên OpenJDK/HotSpot; kết quả trên cũng khớp khi chạy với `-Xint` và `-XX:-TieredCompilation`.

### 12.2. Giải thích ý đồ của demo

- `transform(int)` là method nhỏ được gọi nhiều lần.
- Vòng lặp ngoài `main` gọi `transform` **20.000 lần**, truyền dữ liệu trong khoảng `0..255`.
- `loop(1_000_000)` chỉ được gọi **một lần** nhưng có vòng lặp dài với nhiều backedges.
- `volatile blackhole` nhận kết quả để tạo một side effect có thể quan sát, giảm khả năng compiler bỏ hẳn tính toán do nó cho rằng không cần.
- `checksum` và `loop checksum` dùng phép tính số nguyên xác định; chương trình không lấy clock/timing làm đầu ra.

**Điều quan trọng:** `volatile` không tự bảo đảm **tất cả lời gọi đều còn là machine-level calls độc lập** hoặc JIT chắc chắn biên dịch level 4. JIT vẫn có thể inline/tối ưu miễn giữ đúng observable behavior.

### 12.3. Vì sao demo cố ý không in “số lần JIT”?

Số compilations, compile IDs, tiers, OSR events và thứ tự xuất hiện phụ thuộc HotSpot/JDK, platform, CPU và scheduling. Java Specification không cố định chúng.

Chương trình chỉ in kết quả logic ổn định. Để quan sát JIT, sử dụng **runtime diagnostics** riêng — đừng hardcode “sẽ thấy 3 lần compile” vào stdout.

## 13. Thực hành `-XX:+PrintCompilation`

Chạy:

```bash
java -XX:+PrintCompilation HotMethodDemo
```

Ở HotSpot JDK 17+, option này thường ghi các compilation events theo thời gian, gồm cả những methods của JDK. Output **không cố định** và có thể xen vào stdout của ứng dụng.

Để nghiên cứu, tìm những dòng chứa:

```text
HotMethodDemo::transform
HotMethodDemo::loop
HotMethodDemo::main
```

### 13.1. Cách hiểu các trường thường thấy

Một bản ghi có thể dạng:

```text
23   13       3       HotMethodDemo::transform (13 bytes)
26   15       4       HotMethodDemo::transform (13 bytes)
26   14 %     3       HotMethodDemo::loop @ 4 (25 bytes)
```

**Đây là các ví dụ định dạng được quan sát trên một lần thử HotSpot, KHÔNG phải output chuẩn cho mọi máy.** Các trường thường có:

- **Timestamp** tính từ khởi động VM (đơn vị/format theo output).
- **Compile ID** của nmethod/task.
- **Marker** như `%` cho OSR.
- **Compilation level** (chẳng hạn 3 cho C1 profiling, 4 cho C2).
- **Class::method** và phần bytecode size.
- **`@ bci`** trong trường hợp OSR.

Không giải thích `(13 bytes)` là **13 byte machine code**: đó thường là bytecode size của method, không phải compiled machine code footprint.

### 13.2. Nhìn thấy cả C1 và C2 cho cùng method

Nếu log có:

```text
... 3 ... HotMethodDemo::transform ...
... 4 ... HotMethodDemo::transform ...
```

thì đây là một bằng chứng runtime đã yêu cầu/hoàn tất compilation ở những tiers tương ứng trong lần chạy này. Nhưng **không có guarantee mỗi lần chạy đều xuất hiện cả hai bản hoặc đúng thứ tự trong stdout**.

### 13.3. OSR được đánh dấu bằng `%`

Một ví dụ:

```text
... % 3 HotMethodDemo::loop @ 4 ...
```

cho thấy phương thức có một **OSR compilation** cho loop tại BCI được ghi, chứ không phải “`loop` đã được gọi lại lần thứ ba”.

### 13.4. Dòng `made not entrant`

Bạn có thể thấy:

```text
... HotMethodDemo::transform ... made not entrant
```

Đây có thể là nmethod cũ được đưa khỏi compiled method entry khi có code tối ưu hơn, không phải fatal error. Cần xem timeline và runtime state trước khi đánh giá.

### 13.5. Log nhiều không có nghĩa chương trình sai

JVM cũng JIT compile một số methods của runtime/JDK trong cùng process. Nếu log có hàng trăm dòng không chứa `HotMethodDemo`, đó là bình thường.

## 14. Tách chế độ interpreter, tiered JIT và non-tiered để so sánh

### 14.1. Interpreter-only với `-Xint`

```bash
java -Xint HotMethodDemo
```

Trên HotSpot, `-Xint` yêu cầu thực thi Java bytecode theo interpreted mode, hữu ích làm đối chứng khi khảo sát hiệu ứng JIT. Nó **không có nghĩa mọi native runtime stub hoặc library call biến thành Java bytecode interpreted**.

**Stdout kết quả ứng dụng vẫn phải:**

```text
method calls=20000
call checksum=10169104
loop checksum=511499680
combined checksum=521668784
```

Chạy `-Xint` thường chậm hơn đối với compute-intensive workloads, nhưng không có quy luật một tỷ lệ cố định.

### 14.2. Tắt tiered compilation

```bash
java -XX:-TieredCompilation HotMethodDemo
```

Cờ này làm HotSpot không dùng chính sách tiered C1→C2 như mặc định. **Không diễn giải rằng tắt tiered là tắt toàn bộ JIT**: các compiler configurations vẫn có thể biên dịch methods bằng chế độ khác, tùy VM build.

### 14.3. Giới hạn tier bằng `TieredStopAtLevel`

```bash
java -XX:+TieredCompilation -XX:TieredStopAtLevel=1 HotMethodDemo
```

Trong HotSpot C1/C2 cấu hình phù hợp, cách đặt level 1 có thể giúp nghiên cứu đường C1 mà không đạt C2 level 4. Nhưng **nội dung “level 1” là chi tiết HotSpot**, không phải option portable mọi Java VM.

### 14.4. Bảng so sánh

| Cách chạy | Ý đồ | Điều không được suy |
| --- | --- | --- |
| `java HotMethodDemo` | Mặc định HotSpot/JDK hiện tại | Chắc chắn mọi hot method phải level 4 |
| `java -Xint HotMethodDemo` | Đối chứng interpreted execution | Mọi runtime/native code bị tắt |
| `java -XX:-TieredCompilation HotMethodDemo` | Không dùng tiered policy mặc định | JIT bị vô hiệu hoàn toàn |
| `java -XX:TieredStopAtLevel=1 HotMethodDemo` | Giới hạn tier trên build hỗ trợ | Tất cả methods đều compiled C1 level 1 |
| `java -XX:+PrintCompilation HotMethodDemo` | Ghi compilation events | Event count/timestamps cố định |

**Khi kiểm chứng một flag, đọc `java -version` và tài liệu JVM đang dùng.**

## 15. `PrintInlining`, `LogCompilation` và các công cụ chẩn đoán

### 15.1. PrintInlining

Trên các HotSpot builds hỗ trợ, có thể bật thông tin inlining khi compile:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining HotMethodDemo
```

Output có thể đề cập những thông tin như `inline (hot)`, `callee is too large` hoặc những lý do theo compiler heuristic. Cách in thay đổi theo JDK; **không dùng một chuỗi log cố định làm API**.

Nếu muốn kết hợp:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation \
     -XX:+PrintInlining HotMethodDemo
```

Dấu `\` ở cuối dòng là **cú pháp nối dòng của shell kiểu Bash**, không dùng y nguyên trên Windows PowerShell. Trên Windows, viết lệnh **một dòng** hoặc dùng cú pháp nối dòng phù hợp PowerShell.

### 15.2. PrintAssembly cần hỗ trợ bổ sung

Tùy bản HotSpot, các cờ kiểu `-XX:+PrintAssembly` hoặc compiler command để in machine code có thể cần tool/disassembler hỗ trợ, ví dụ thư viện `hsdis` tương ứng. Không giả định mọi JDK bundle đều hiển thị assembly có chú giải đầy đủ.

**Không dùng binary assembly dump làm ví dụ “stdout chuẩn”** vì khác CPU architecture, compiler/code layout và runtime.

### 15.3. `-XX:+LogCompilation`

HotSpot hỗ trợ logging chi tiết một số compilation metadata vào file với options diagnostic thích hợp. Việc đọc log thường kết hợp công cụ phân tích riêng.

Ví dụ gợi ý:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation HotMethodDemo
```

Log có thể tạo tệp tùy VM options/current directory; nên kiểm tra `java -XX:+PrintFlagsFinal -version`, quyền ghi và nơi output được tạo. Đừng sử dụng log này mà không cân nhắc kích thước/tác động lên workload production.

### 15.4. Java Flight Recorder (JFR)

JFR có thể ghi sự kiện liên quan JIT compilation/code cache, CPU usage và execution samples trên cấu hình hỗ trợ. Cần kiểm tra event settings trong build mục tiêu; không phải mọi compilation event đều bật trong mọi recording profile.

JFR giúp nối runtime behavior với application performance; `PrintCompilation` thường tiện hơn cho bài tập đơn giản vì log trực tiếp theo method.

### 15.5. `jcmd`

Nếu cần xem JVM đang chạy:

```bash
jcmd -l
jcmd <pid> Compiler.codecache
jcmd <pid> Compiler.queue
jcmd <pid> Compiler.codelist
```

Lệnh availability thay đổi theo JDK; thay `<pid>` bằng PID thực tế. Có thể kiểm tra:

```bash
jcmd <pid> help
```

Những lệnh này giúp quan sát compilation queue, code cache và compiled methods trong HotSpot; không phải API cần chạy ở mọi Java application.

## 16. Về thresholds và compilation policy: tránh ghi “một con số thần kỳ”

Một số tài liệu cũ nói “HotSpot compile sau 10.000 lần gọi”. Đó là cách diễn đạt **quá đơn giản**, đặc biệt với tiered compilation.

### 16.1. Các ngưỡng thực tế có thể phức tạp hơn

OpenJDK tiered policy có các ngưỡng điều khiển:

- Tần suất cập nhật và kiểm tra counters.
- Invocation thresholds theo tier.
- Backedge thresholds theo tier cho OSR.
- Min invocation thresholds.
- Tổng invocation + backedge activity.
- Queue-length factors làm điều chỉnh compilation policy.

Các tên option như `Tier3InvocationThreshold` hoặc `Tier4BackEdgeThreshold` thuộc phiên bản/cấu hình HotSpot, không nên xem là public stable API.

### 16.2. Thay ngưỡng thấp để “ép JIT” có đúng không?

Trong môi trường thử nghiệm, có thể chỉnh compilation policy flags để nghiên cứu. Nhưng **không coi threshold thấp hơn luôn làm ứng dụng nhanh hơn**:

- Compiler threads dùng thêm CPU.
- Method có thể không đủ profile tốt, dẫn đến speculation kém.
- Code cache có thể chịu áp lực.
- Compilation/recompilation chiếm tài nguyên startup.
- Benchmark dễ bị nhiễu.

### 16.3. Tại sao queue pressure ảnh hưởng cấp biên dịch?

Nếu hàng đợi C2 quá dài, việc đẩy quá nhiều methods lên C1 full profiling rồi chờ C2 có thể gây overhead profiling không đáng. Chính sách tiered có thể lựa chọn các lộ trình C1 phù hợp hơn theo tải compiler.

Tức “hotness” không chỉ là một counter độc lập; nó được kết hợp với **chi phí tài nguyên biên dịch**.

## 17. Inlining khiến method “nóng” khó nhìn thấy trong stack trace

Một call site hot có thể được tối ưu bằng **inlining**: body của callee được tích hợp vào caller, giúp giảm call overhead và mở khóa constant propagation, bounds-check elimination hoặc các tối ưu xuyên qua method boundary.

```java
static int twice(int x) {
    return x + x;
}

static int runMany(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        sum += twice(i);
    }
    return sum;
}
```

Code này chỉ là **snippet**. JIT có thể inline `twice` vào `runMany`. Không bắt buộc kết quả phải có một call instruction riêng đến `twice` trong compiled machine code.

### 17.1. Tại sao nhìn runtime profile có thể khác hình dạng source?

- Một method Java có thể xuất hiện logic trong machine code của caller.
- Một method dài có thể được chia thành những compiled hot/cold paths.
- Speculative guard và uncommon-trap edges có thể không giống `if` source.
- OSR entry vào giữa loop khác normal method entry.

Thêm/bớt một method Java không tự quyết định chi phí method-call cuối cùng sau JIT.

### 17.2. Đừng dùng “trừu tượng hóa thành nhiều method nhỏ luôn chậm”

Java method boundaries có thể được inlined. Thiết kế source nên ưu tiên tính rõ ràng/correctness; những tối ưu hóa micro cần benchmark và profiling thật, không dựa vào số lời gọi nhìn thấy trong source.

## 18. Warm-up ảnh hưởng phép đo hiệu năng thế nào?

Một microbenchmark đo ngay lần gọi đầu:

```java
long start = System.nanoTime();
someMethod();
long elapsed = System.nanoTime() - start;
```

không chỉ đo “tốc độ của someMethod ở steady state”. Nó còn có thể gồm:

- Class loading/linking/initialization.
- Interpreter execution và runtime resolution.
- JIT compilation/warm-up đang diễn ra.
- GC và safepoints.
- Thread scheduling, CPU scaling/JIT code cache activity.
- Dead-code elimination, constant folding nếu phép đo không chống tối ưu đúng cách.

### 18.1. Một vòng lặp timing tự viết dễ sai

```java
for (int i = 0; i < 1_000_000; i++) {
    compute(42);
}
```

Snippet này chưa độc lập; `compute` cần khai báo. JIT có thể inline `compute`, hoist các computations bất biến hoặc loại bỏ những kết quả không được dùng. Đây là lý do “một triệu vòng” không tự là benchmark đáng tin.

### 18.2. JMH là lựa chọn ưu tiên cho microbenchmark

**JMH (Java Microbenchmark Harness)** của OpenJDK cung cấp warm-up iterations, measurement iterations, forks, blackholes và những cơ chế giảm bẫy benchmark. Nếu mục tiêu là đo mức tăng tốc do JIT/C1/C2, nên dùng JMH thay vì viết một vòng `nanoTime` tùy tiện.

Trang dự án: https://openjdk.org/projects/code-tools/jmh/

### 18.3. Benchmark cần ổn định environment

Giữ cùng:

- JDK vendor/build và VM flags.
- CPU quota/affinity, host load và power policies.
- Heap sizing và GC.
- Dữ liệu đầu vào/tỷ lệ nhánh/hình dạng đối tượng.
- Warm-up, forks và lượng samples.

Một khác biệt chỉ xảy ra ở một run chưa đủ để chứng minh C2 luôn cải thiện hiệu năng.

## 19. Một timeline học tập từ cold method đến hot path

Giả sử một service có `parse(int)` ở request handler:

```text
t0: JVM bắt đầu
    parse() chưa từng được gọi

t1: request đầu
    parse() được executed interpreted
    JVM thu các profiling/counter data phù hợp

t2: requests tăng
    invocation count tăng
    policy coi parse() đủ nóng
    enqueue C1 compile

t3: C1 version được published
    requests về sau có thể dùng C1
    profiling tier tiếp tục thu branch/type data

t4: requests tiếp tục
    policy có đủ profile, enqueue C2
    trong lúc đợi, application vẫn tiếp tục

t5: C2 version available
    requests mới có thể dùng optimized code
    parse() có thể inline vào handler

t6: kiểu input/call path mới xuất hiện
    guards/assumptions có thể bị invalidated
    deoptimization/recompilation có thể xảy ra
```

Đây là **timeline minh họa**, không nói rằng mọi service nhất định đi qua t2–t6 hoặc mỗi bước cần đúng một request. Một phương thức ít chạy mãi có thể không đến t2. Một loop dài có thể OSR mà method invocation count không lớn.

## 20. So sánh Java 17 HotSpot với các trường hợp ngoại lệ

### 20.1. Native methods

`native` methods thường thực thi qua native implementation/ABI/JNI, không phải bytecode Java thông thường để HotSpot C1/C2 biên dịch y hệt một Java method body.

### 20.2. Abstract methods

Một abstract method không có bytecode body để JIT compile độc lập. Những concrete implementations/call sites có thể được biên dịch và optimized.

### 20.3. Large or complex methods

Một số methods quá lớn hoặc không phù hợp với một compiler tier có thể không được compile ở cấp đó; VM có thể chọn tier khác, fallback hoặc để interpreted tùy policy. Không phải mọi hot method đều chắc chắn C2 level 4.

### 20.4. Synchronized methods

JIT có thể optimize synchronization trong các điều kiện hợp lệ, nhưng phải bảo đảm Java Memory Model. Không phải vì method hot mà JIT được xóa synchronization làm thay đổi correctness.

### 20.5. Exceptions

Các exception paths ít gặp có thể ảnh hưởng profiling/uncommon traps. Không nên cố tăng exception rate để “làm nóng” method mà không xét business correctness và GC/allocation overhead.

### 20.6. Multiple class loaders

Hai class cùng binary name do hai defining class loaders khác nhau tạo thành hai runtime classes khác. Compilation metadata/code và profiling có thể được tổ chức riêng theo method identity; không suy rằng một method được hot ở loader A thì loader B đã được compile tương tự.

## 21. Những hiểu lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Java luôn interpreted, không có machine code.” | HotSpot có JIT C1/C2 và chạy compiled native code khi phù hợp. |
| “`javac` tạo code C2.” | `javac` tạo bytecode; HotSpot JIT là bước runtime riêng. |
| “Mọi method chạy lần đầu đều được compile ngay.” | Interpreter có thể là điểm khởi đầu, compilation dựa policy/hotness. |
| “Hot = method có thời gian wall-clock lâu nhất.” | Invocation/backedge activity, profile, queue và heuristics quan trọng. |
| “Method được gọi 10.000 lần chắc chắn level 4.” | Không có ngưỡng tuyệt đối portable; tiered policy phức tạp hơn. |
| “Tier luôn đi lần lượt 0→1→2→3→4.” | Đường thường gặp là 0→3→4; có thể bỏ cấp/đổi đường. |
| “C1 là interpreter.” | C1 là JIT compiler tạo native code; level 0 là interpreter. |
| “C2 phải nhanh hơn C1 đúng một tỷ lệ.” | Tùy method/profile/CPU và benchmark. |
| “Chỉ method gọi nhiều lần mới hot.” | Một method gọi một lần có loop dài có thể cần OSR. |
| “OSR là một request gọi method mới.” | OSR có thể thay execution của frame đang chạy tại loop BCI. |
| “`%` trong PrintCompilation nghĩa lỗi.” | `%` thường là marker OSR compilation. |
| “`@ 4` trong OSR log là số lần gọi thứ tư.” | Nó chỉ một bytecode index (BCI), không phải invocation count. |
| “`13 bytes` trong log là kích thước machine code.” | Thường là bytecode method size. |
| “`made not entrant` nghĩa app hỏng.” | Thường là trạng thái compiled code không dùng cho entry mới. |
| “Deoptimization làm Java trả kết quả khác.” | JVM phải bảo toàn semantics khi chuyển execution/recompile. |
| “-Xint giống -XX:-TieredCompilation.” | `-Xint` yêu cầu interpreter; tắt tiered không đồng nghĩa tắt JIT. |
| “Thêm volatile field buộc từng call không inline.” | Volatile side effects phải bảo toàn, nhưng inlining vẫn có thể xảy ra. |
| “PrintCompilation luôn có cùng số dòng.” | Compilation events phụ thuộc JVM/load/timing, không deterministic. |
| “Inlining làm mọi API abstraction luôn free.” | Chỉ khi JIT thực sự inline/tối ưu theo điều kiện. |
| “Code cache là Java heap.” | Code cache là runtime memory khác với Java objects trong heap. |
| “JIT compiler chỉ chạy trước main().” | JIT có thể chạy bất kỳ lúc nào trong runtime khi method hot. |
| “Benchmark một run cold có thể kết luận steady-state performance.” | Cần warm-up/forks/JMH và đo nhiều runs. |

## 22. Tự kiểm tra kèm đáp án

**Câu 1.** `javac` biên dịch Java thành gì và C1/C2 biên dịch gì?

**Đáp án:** `javac` tạo bytecode trong `.class`; C1/C2 là các HotSpot runtime JIT compilers tạo machine code phù hợp CPU/runtime.

**Câu 2.** Vì sao method chỉ chạy một lần có thể không được JIT compile?

**Đáp án:** Chi phí compile/profiling có thể lớn hơn lợi ích từ một lần chạy; policy giữ interpreted là một lựa chọn hợp lý.

**Câu 3.** Có thể hot dù chỉ gọi một lần không?

**Đáp án:** Có. Một vòng lặp rất dài tăng backedge activity và có thể kích hoạt OSR.

**Câu 4.** Tier 0 tương ứng với gì?

**Đáp án:** Interpreter execution trong mô hình HotSpot tiered compilation.

**Câu 5.** Level 3 và level 4 khác nhau thế nào?

**Đáp án:** Level 3 là C1 với full profiling; level 4 là C2 optimized compiled code trong mô hình HotSpot thông thường.

**Câu 6.** Mọi method luôn đi qua C1 level 1 rồi mới level 2/3/4 không?

**Đáp án:** Không. Có thể đi 0→3→4 hoặc các đường khác tùy policy.

**Câu 7.** Invocation count và backedge count có cùng ý nghĩa không?

**Đáp án:** Không. Invocation đếm lần vào method; backedge liên quan loop iterations và có thể làm nóng loop trong một invocation.

**Câu 8.** OSR là gì?

**Đáp án:** On-Stack Replacement: runtime có thể chuyển frame đang chạy loop từ interpreted/compiled tier thấp sang OSR compiled code tại một vị trí bytecode phù hợp.

**Câu 9.** C2 dùng runtime profiles để làm gì?

**Đáp án:** Chọn/tối ưu những đường thường gặp, ví dụ inlining hoặc devirtualization, với guards và deoptimization khi cần để bảo toàn semantics.

**Câu 10.** Deoptimization có nghĩa “GC thu hồi compiled code” không?

**Đáp án:** Không. Nó liên quan việc vô hiệu hóa/rời optimized compiled execution và khôi phục trạng thái runtime phù hợp; code cache management là vấn đề riêng.

**Câu 11.** Vì sao sau warm-up hiệu năng có thể tăng?

**Đáp án:** Hot methods/loops có thể được JIT compile và tối ưu dựa trên profiling, nhưng mức tăng phụ thuộc workload.

**Câu 12.** `-Xint` có tắt JIT compilation cho bytecode ứng dụng trên HotSpot không?

**Đáp án:** Có, nó yêu cầu interpreted mode cho Java bytecode; nhưng không làm mọi thành phần native runtime biến mất.

**Câu 13.** `-XX:-TieredCompilation` có nghĩa giống `-Xint` không?

**Đáp án:** Không. Nó tắt tiered policy, không nhất thiết tắt mọi JIT compiler.

**Câu 14.** `PrintCompilation` có giúp chứng minh event compilation được log trong lần chạy đó không?

**Đáp án:** Có, đối với events/log format mà JVM báo; nhưng không cam kết tất cả compiler decisions sẽ luôn có cùng số dòng/timing.

**Câu 15.** Trong log `HotMethodDemo::loop @ 4`, số 4 thường là gì?

**Đáp án:** Bytecode index (BCI) dùng cho OSR compilation trong kiểu log đó.

**Câu 16.** Tại sao một method rất nhỏ có thể không xuất hiện như một machine-level call riêng biệt trong hot path?

**Đáp án:** JIT có thể inline nó vào caller.

**Câu 17.** Vì sao chỉ lấy `System.nanoTime` quanh một lời gọi cold không phải benchmark steady state?

**Đáp án:** Nó có thể bao gồm class initialization/interpreter/warm-up/JIT/GC/OS noise và bị compiler optimization đánh lừa.

**Câu 18.** Công cụ nào phù hợp hơn để xây Java microbenchmark có warm-up và forks?

**Đáp án:** OpenJDK **JMH**.

**Câu 19.** Khi code cache đầy, kết quả Java có được phép đổi không?

**Đáp án:** Không. JVM phải giữ semantics; có thể hạn chế compilation/tác động hiệu năng theo runtime.

**Câu 20.** Có thể ép mọi method hot sang level 4 trong một khoảng thời gian cố định không?

**Đáp án:** Không có guarantee API portable như vậy; policy, compiler queues, profile quality và constraints khác ảnh hưởng.

## 23. Checklist thực hành

1. Kiểm tra **`java -version`** để biết JVM vendor/build và tiered compiler availability.
2. Đọc class bytecode bằng `javap -c` khi cần hiểu method shape/loop backedges.
3. Phân biệt **method-entry compilation** với **OSR compilation**.
4. Sử dụng `-XX:+PrintCompilation` để nhìn compilation events, nhận diện levels 3/4, `%` và `@ BCI`.
5. Đừng so sánh timestamp/log count giữa hai runs như một hợp đồng.
6. Dùng `-Xint` làm đối chứng interpreted behavior, hiểu khác `-XX:-TieredCompilation`.
7. Quan sát inlining khi có công cụ, không đoán từ source.
8. Tránh tối ưu microbenchmark theo kết quả tính toán bị compiler loại bỏ.
9. Dùng JMH nếu mục tiêu là so sánh hiệu năng.
10. Đo compilation CPU, code cache và application latency khi chỉnh policy flags.
11. Ghi lại warm-up/forks/heap/CPU/GC để có kết quả lặp lại.
12. Tránh cài compilation thresholds trên production nếu chưa có bằng chứng đo lường.
13. Nếu throughput giảm sau khởi động lâu, kiểm tra code cache, deoptimizations và profile shifts.
14. Nếu application có class loaders/redeploy liên tục, nhớ compilation state và runtime class identity có vòng đời riêng.
15. Phân biệt **optimized machine code** với bảo đảm ngôn ngữ Java; correctness phải giống nhau qua interpreter/C1/C2.

### Tóm tắt cuối

```text
Cold method
     |
     v
HotSpot interpreter (tier 0)
     |
     +-- số calls thấp và loops ngắn --> có thể vẫn interpreted
     |
     +-- nhiều calls / loop backedges --> hotness / profiling
                                             |
                                             v
                                      JIT compilation policy
                                             |
                     +-----------------------+------------------+
                     |                                          |
                   C1 tiers                                 OSR entry
                  (1,2,3)                                cho loop nóng
                     |
                thu profile
                     |
                     v
               C2 (tier 4)
                     |
            guards / assumptions
                     |
                  deopt?
                     |
                     +--> interpreter / reprofile / recompile

GC, JIT và Java app vẫn là một tiến trình;
mọi đường thực thi phải giữ cùng Java semantics.
```

**Kết luận:** HotSpot không tối ưu mọi bytecode ngay lập tức. Nó **thực thi**, **quan sát**, **xác định code nóng**, rồi **biên dịch có chọn lọc** để cân bằng startup và throughput. Một method gọi nhiều lần có thể được C1 rồi C2 compile; một loop chạy dài có thể chuyển sang compiled code giữa chừng bằng OSR; tối ưu speculative có thể được hủy khi assumptions thay đổi. **Các bước này là chính sách HotSpot, không phải lịch cố định của ngôn ngữ Java.**

### Nguồn chính thức và tài liệu kỹ thuật

- [Oracle Java HotSpot VM FAQ — HotSpot, JIT và OSR](https://www.oracle.com/java/technologies/hotspotfaq.html): giải thích interpreter, JIT và on-stack replacement.
- [Oracle JDK 17 — `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xint`, `PrintCompilation`, `CompileCommand`, `TieredCompilation`, JIT/compiler flags.
- [OpenJDK — Tiered compilation policy sources](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compilationPolicy.hpp): cách policy sử dụng invocation/backedge counters và compilation thresholds.
- [OpenJDK — Tiered threshold policy](https://cr.openjdk.org/~xliu/8165056/v4/webrev/src/hotspot/share/compiler/tieredThresholdPolicy.hpp.html): mô hình levels 0–4 và các đường transition.
- [Microsoft for Java Developers — How Tiered Compilation Works in OpenJDK](https://devblogs.microsoft.com/java/how-tiered-compilation-works-in-openjdk/): phân biệt C1 levels, C2, profiling và deoptimization.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): thiết kế microbenchmark đúng với đặc điểm JIT và warm-up.
- [JVMS `2` — Structure of the JVM](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html): runtime model, bytecode và những gì JVM Specification quy định, không bắt buộc C1/C2.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-003">Thu thập hồ sơ thực thi từ số lần gọi, vòng lặp và kiểu đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Thu thập hồ sơ thực thi từ số lần gọi, vòng lặp và kiểu đối tượng

## 1. Tại sao HotSpot phải quan sát chương trình khi đang chạy?

Một file Java `.class` chứa bytecode có thể kiểm tra được về mặt cú pháp và kiểu dữ liệu, nhưng nó **không nói trước** method nào sẽ được gọi hàng triệu lần, vòng lặp nào chạy rất lâu, nhánh nào thường xuyên được đi qua hoặc một lời gọi interface thực tế thường nhận object thuộc class nào.

HotSpot sử dụng **execution profiling** để thu thập một phần hành vi thực thi, từ đó quyết định **mã nào đáng JIT compile** và **các compiler nên tối ưu theo giả định nào**. Ba nhóm dữ liệu quan trọng:

- **Invocation counters**: tín hiệu về số lần method được gọi, phục vụ đánh giá method hotness.
- **Backedge/loop counters**: tín hiệu về hoạt động lặp trong bytecode; giúp phát hiện method chỉ được gọi một lần nhưng có vòng lặp hàng triệu iterations, phục vụ **On-Stack Replacement (OSR)**.
- **Type profiles**: thống kê các kiểu receiver thực sự gặp tại những bytecode call sites hoặc những vị trí profiling tương ứng; hỗ trợ suy đoán kiểu, **devirtualization**, **inlining** và điều phối những đường chạy hiếm.

HotSpot cũng có thể thu thập **branch frequencies**, **type-check feedback**, information về **uncommon traps** và những dữ liệu nội bộ khác; tuy nhiên trọng tâm bài này là lời gọi, vòng lặp và kiểu object.

Bài sử dụng **HotSpot/OpenJDK Java 17**, interpreter, **C1**, **C2** và tiered compilation. Tên metadata, counters, thresholds và compilation levels dưới đây là **implementation details của HotSpot** chứ không phải hợp đồng của Java Language Specification (JLS) hay Java Virtual Machine Specification (JVMS). Một JVM tương thích khác có thể tối ưu khác nhưng vẫn phải bảo toàn kết quả Java.

**Phân biệt ngay:** “profile” là thông tin cho runtime đưa ra quyết định, không phải bằng chứng chắc chắn rằng một method đã được inline, một object đã đổi loại, hoặc Java method hiện có một số lời gọi chính xác bằng số counter hiển thị trong cấu trúc nội bộ.

## 2. Từ bytecode tới mã máy: bức tranh tổng thể

```text
.java
  |
  | javac
  v
.class (bytecode)
  |
  | class loading / verification / linking
  v
Interpreter + counters/profile instrumentation
  |
  | hotness, branch and receiver feedback
  v
Tiered compilation policy
  |
  +--> C1: compiled code sớm, profiling tùy tier
  |
  +--> C2: optimization sâu dựa trên static + dynamic facts
           |
           v
       compiled machine code
           |
           | assumptions/guards
           v
    Nếu giả định không còn đúng:
      fallback / deoptimization / reprofile / recompile
```

Các bước có thể chồng lấn về thời gian:

1. Application thread đang chạy bytecode trong interpreter hoặc nmethod đã compiled.
2. JVM cập nhật counters hoặc profile records tùy mức execution.
3. Compilation policy quyết định enqueue một compile task.
4. Một **compiler thread** (khác application thread) có thể xử lý task từ compile queue.
5. Khi compiled code hợp lệ, VM có thể cho những lần gọi sau sử dụng compiled entry, hoặc đang chạy trong loop thì có thể dùng OSR entry.
6. Một method có thể được compile nhiều lần, với các compilation levels và giả định khác nhau.

Không thể dựa vào tên method “hot” rồi khẳng định **luôn** có C2 code sau một số lần gọi cố định. Thời điểm biên dịch còn phụ thuộc compile queue load, profile maturity, flags, code cache, compiler availability và JVM build.

## 3. Invocation counter: biết method được gọi nhiều lần

Xét method:

```java
static int twice(int value) {
    return value * 2;
}

static long applyManyTimes() {
    long total = 0;
    for (int i = 0; i < 100_000; i++) {
        total += twice(i % 10);
    }
    return total;
}
```

Đây là **hai method fragments**, chưa phải file biên dịch độc lập. Trong mô hình này, `twice` được gọi nhiều lần từ một vòng lặp. HotSpot có thể quan sát những lời gọi để tăng **invocation counter** hoặc cập nhật tín hiệu sử dụng method phù hợp với compilation tier.

### Counter giúp JVM quyết định điều gì?

- Method có đủ “hot” để compiler đầu tư thời gian hay không?
- Nên chuyển từ interpreter sang C1 ở mức profiling nào?
- Khi nào dữ liệu đã đủ và hiệu quả dự kiến biện minh cho C2 compilation?
- Một method có khả năng trở nên cold hoặc chưa có ích để tiếp tục enqueue compile request không?

**Invocation count không phải CPU profiler.** Một getter rất ngắn được gọi nhiều lần có thể ít tốn thời gian hơn một method chỉ chạy một lần nhưng duyệt hàng triệu elements. Một code path đã được inline có thể không có số máy-level function calls bằng số lời gọi viết trong Java source.

### Những giới hạn của bộ đếm

Các counters của HotSpot là **VM-internal**, không phải `long invocationCount` đặt trong mỗi Java object. Chúng có thể được đọc/điều chỉnh bởi tiered compilation policy và cập nhật khác nhau tùy execution engine. Không có API Java SE quy định một threshold như “gọi đúng 10.000 lần thì chắc chắn thành C2”; những ngưỡng/tần suất notifications có thể thay đổi theo runtime state và JDK update.

Không dùng `AtomicLong` trong application để giả làm số raw JVM invocation counter. Nó là **business instrumentation**, có overhead và semantics riêng; JIT có thể inline calls hoặc tối ưu code nhưng bộ đếm nghiệp vụ phải phản ánh những thao tác mà ứng dụng yêu cầu.

## 4. Backedge counter: một lời gọi vẫn có thể là điểm rất nóng

Một method có thể được gọi một lần nhưng bên trong có vòng lặp dài:

```java
static long accumulate(int n) {
    long total = 0;
    for (int i = 0; i < n; i++) {
        total += i;
    }
    return total;
}
```

Đây cũng là **method fragment**. Về bytecode, một compiler Java thông thường tạo một phép kiểm tra điều kiện và một nhánh quay về bytecode offset (BCI) thấp hơn — gọi là **backward branch/backedge**.

```text
BCI 0:   init i = 0
BCI 4:   check i < n      <--- loop header
BCI 9:   body total += i
BCI 18:  increment i
BCI 21:  goto BCI 4       <--- backedge
```

Các BCIs và instruction descriptions trong sơ đồ là **minh họa**, không phải nguyên văn `javap`. Nội dung bytecode thực có thể khác tùy compiler.

HotSpot có thể dựa vào **backedge notifications/counters** để phát hiện vòng lặp chiếm nhiều thời gian, ngay cả khi invocation counter không cao.

### “Backedge count = số vòng lặp” có phải luôn đúng?

Không. Nó phụ thuộc control-flow graph của bytecode và vị trí branches. Một `for` source có thể biến thành nhiều bytecode edges; nested loops có nhiều backedges; JIT có thể **unroll**, **peel**, **vectorize**, **eliminate** hoặc biến đổi loops khi sinh machine code. Vì vậy không đọc backedge count như một API đếm source iterations chính xác cho mọi tình huống.

## 5. On-Stack Replacement (OSR): JIT khi loop vẫn đang chạy

**OSR** giúp JVM đổi từ interpretation/compiled representation hiện tại sang một compiled entry cho **một vị trí trong method**, thông thường tại loop backedge, **ngay khi method invocation ấy vẫn chưa kết thúc**.

```text
Method longTask() được gọi một lần
    |
    v
Interpreter bắt đầu loop
    |
    | số lần qua backedge tăng
    v
Runtime yêu cầu OSR compilation tại BCI của loop
    |
    | compiler thread chuẩn bị machine code
    v
Application chuyển sang OSR entry khi điều kiện cho phép
    |
    v
Tiếp tục loop đã compiled -> return method
```

Nếu không có OSR, một method có vòng lặp dài có thể phải đợi tới **lần gọi thứ hai** mới dùng compiled entry, điều không có lợi cho batch job chỉ gọi một lần. OSR là một cách giảm sự hạn chế này.

**OSR khác normal compilation:**

| Normal entry compilation | OSR compilation |
| --- | --- |
| Entry thường dùng khi invocation bắt đầu tại đầu method | Entry dùng để tiếp tục ở một BCI thuộc invocation đang chạy |
| Thường liên quan invocation hotness | Thường liên quan loop/backedge hotness |
| Có thể áp dụng cho những lời gọi tiếp theo | Có thể giúp một lời gọi rất dài đang diễn ra |
| Một compiled version có entry tại đầu method | Có thể có nmethod/entry chuyên cho BCI của loop |

Đừng cho rằng OSR chắc chắn xuất hiện trong **mọi** loop dài. JVM có thể compile trước khi loop chạy, code có thể được tối ưu rất mạnh, hoặc compile policy/thresholds không chọn OSR trong lần thử nghiệm cụ thể.

## 6. Java 17 chạy được: quan sát vòng lặp dài

Tạo **`BackedgeOsrDemo.java`**:

```java
public class BackedgeOsrDemo {
    static volatile long observed;

    static long calculate(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += (i & 7);
        }
        return sum;
    }

    public static void main(String[] args) {
        observed = calculate(8_000_000);
        System.out.println("iterations=8000000");
        System.out.println("sum=" + observed);
    }
}
```

```bash
javac --release 17 BackedgeOsrDemo.java
java BackedgeOsrDemo
```

**Stdout xác định của chính chương trình:**

```text
iterations=8000000
sum=28000000
```

Vì mỗi nhóm 8 giá trị `(i & 7)` là `0 + 1 + ... + 7 = 28`, có 1.000.000 nhóm nên tổng bằng 28.000.000.

Để nhìn compiler events trên HotSpot:

```bash
java -XX:+PrintCompilation BackedgeOsrDemo
```

**Không có stdout compiler cố định:** log có thể chứa nhiều methods thuộc JDK, các compilation levels khác nhau hoặc không thấy OSR đúng cách bạn kỳ vọng. Ký hiệu `%` thường liên quan OSR trong `PrintCompilation` của HotSpot và một đoạn `@ BCI` thể hiện điểm bắt đầu; phải đọc compiler log thực tế, không dùng kết quả hai dòng `System.out` làm bằng chứng OSR đã xảy ra.

### Dùng javap để tìm backedge

```bash
javap -c -p BackedgeOsrDemo
```

`javap -c` hiện offsets và bytecode instructions. Tìm conditional branch và jump quay về BCI trước để hiểu vòng lặp của bytecode. Đây không phải disassembler mã máy C2; JIT có thể tối ưu machine loop khác với cấu trúc trong `.class`.

## 7. Tiered compilation: C1, C2 và các mức profiling

HotSpot Java 17 thường sử dụng các **compilation levels 0–4**:

| Level | Execution engine | Mục tiêu phổ biến |
| --- | --- | --- |
| **0** | Interpreter | Thực thi bytecode, có thể cập nhật counters/profiling |
| **1** | C1 optimized code, không full profiling | Native code sớm khi profiling sâu không cần thiết |
| **2** | C1 với limited profiling | Invocation/backedge counting và thông tin hotness hạn chế |
| **3** | C1 với fuller profiling | Đóng góp MethodData/receiver, branch profiles để C2 dùng |
| **4** | C2 | Optimized native code sâu dựa trên static information và profile feedback |

Một đường thường gặp là `0 → 3 → 4`, nhưng các đường `0 → 2 → 3 → 4`, `0 → 1` hoặc đường khác đều có thể hợp lệ, tùy compilation policy. Không phải **mọi** method đi qua đủ các levels, và các compilation requests có thể nằm đợi trong compiler queues.

### Vì sao tier 2 và tier 3 khác nhau?

Full profiling có **runtime overhead**: cập nhật metadata và counters mất CPU/cache bandwidth, đôi khi còn ảnh hưởng compiled code size. C1 tier 2 cung cấp một mức chạy nhanh với limited notifications; tier 3 thu thêm feedback giúp C2 suy đoán và tối ưu có chất lượng hơn.

Tự suy rằng “C1 = không profiling” sẽ sai, nhưng “mọi C1 method luôn có full receiver type profile” cũng sai.

### Những factors khác ngoài counter

Tier transition còn xét compiler queue load, method kích thước lớn/nhỏ, code cache, tình trạng compiler, method đã compile hay chưa, profiling maturity và policy/flags. Có thể xuất hiện C2 compile cho method có nhiều invocations, hoặc không xuất hiện trong một test chạy rất ngắn.

## 8. MethodCounters và MethodData (MDO) khác nhau thế nào?

HotSpot có các cấu trúc VM-internal phục vụ quản lý method activity và dynamic profile:

- **MethodCounters** hoặc các counters nằm trong runtime method metadata: tín hiệu invocation, backedge và một số trạng thái phục vụ compilation policy.
- **MethodData / MethodDataObject (MDO)**: hồ sơ phong phú hơn gắn với method, trong đó có những **records liên hệ với BCIs** cụ thể, ví dụ type profiles tại virtual call site, branch-related counts và các data kinds khác.
- Data collection có thể bắt đầu từ interpreter và tiếp tục trong compiled code có instrumentation ở level phù hợp.
- Không có Java SE API cho ứng dụng đọc trực tiếp raw MDO receiver rows của HotSpot như một `Map<Class<?>, Long>`.

```text
Method com.example.Worker.process
   |
   +-- invocation/backedge counters
   |
   +-- MethodData (khi được tạo):
       |
       +-- @BCI 10: branch taken/not-taken tendencies
       |
       +-- @BCI 24: invokevirtual
       |            observed receiver: FastImpl, SafeImpl ...
       |
       +-- @BCI 52: invokeinterface
                    observed receiver distribution khác BCI 24
```

Đây là sơ đồ cấu trúc **ý niệm**, không phải layout/histogram được trích từ một JVM đang chạy. MDO có các record kinds/row counts giới hạn theo build, và nội dung có thể bị cập nhật/aging/reprofile; nó **không phải bản ghi chính xác đầy đủ của mọi object type từng đến call site**.

## 9. Type profile được thu thập ở đâu?

Ở **virtual dispatch**, method đích phụ thuộc **runtime class của receiver**. Ví dụ:

```java
interface Converter {
    int convert(int x);
}

final class FastConverter implements Converter {
    public int convert(int x) {
        return x + 1;
    }
}

final class SafeConverter implements Converter {
    public int convert(int x) {
        return x * 2;
    }
}
```

Đây là fragments của interface/classes; chưa có `main` để chạy độc lập.

Tại một bytecode `invokeinterface Converter.convert`, HotSpot có thể thu thập feedback receiver type đã quan sát. Một site nhận `FastConverter` gần như toàn bộ lần chạy có thể phù hợp cho **speculative inlining**; một site nhận nhiều implementations có thể dẫn tới guarded multimorphic paths hoặc generic dispatch.

### Monomorphic, bimorphic và megamorphic

| Thuật ngữ | Mô hình tại **một call site** | Hệ quả có thể |
| --- | --- | --- |
| **Monomorphic** | Một class receiver phổ biến/duy nhất đã quan sát | Dễ specialize, direct/guarded call hoặc inline |
| **Bimorphic** | Hai classes receiver phổ biến | Có thể inline một/vài đường với guards |
| **Polymorphic** | Nhiều receiver classes | Tối ưu phụ thuộc tần suất và inline budget |
| **Megamorphic** | Rất nhiều receiver classes | Một giả định đơn type thường kém hiệu quả |

Đây là **mô hình diễn giải**, không phải các kiểu bytecode do JVMS định nghĩa. “Monomorphic” không bảo đảm compiler luôn inline; “megamorphic” không bảo đảm chương trình luôn chậm. JIT còn xét receiver types, method size, call-site hotness, code-cache budget, CHA/static type information và machine-code properties.

**Profile theo site, không chỉ theo interface:** hai call sites cùng gọi `Converter.convert` có thể thấy type distributions hoàn toàn khác nhau. Type profile có gắn với vị trí bytecode/method chứ không phải bảng đếm tất cả `Converter` instances trong toàn heap.

## 10. Java 17 chạy được: một call site gặp hai kiểu receiver

Tạo file **`ReceiverProfileDemo.java`**:

```java
public class ReceiverProfileDemo {
    interface Operation {
        int apply(int x);
    }

    static final class AddOne implements Operation {
        public int apply(int x) {
            return x + 1;
        }
    }

    static final class DoubleIt implements Operation {
        public int apply(int x) {
            return x * 2;
        }
    }

    static int dispatch(Operation operation, int x) {
        return operation.apply(x);
    }

    static long run(Operation operation, int count) {
        long sum = 0;
        for (int i = 0; i < count; i++) {
            sum += dispatch(operation, i % 10);
        }
        return sum;
    }

    public static void main(String[] args) {
        long first = run(new AddOne(), 20_000);
        long second = run(new DoubleIt(), 5);

        System.out.println("addOne=" + first);
        System.out.println("double=" + second);
    }
}
```

```bash
javac --release 17 ReceiverProfileDemo.java
java ReceiverProfileDemo
```

**Stdout xác định:**

```text
addOne=110000
double=20
```

`AddOne` cho các giá trị từ 0 đến 9 tổng `1+2+...+10=55` trong một chu kỳ, có 2.000 chu kỳ nên được 110.000; `DoubleIt` cho 5 input 0–4, tổng bằng `0+2+4+6+8=20`.

Tại **cùng một site** `operation.apply(x)` trong `dispatch`, runtime được đưa vào một receiver type nhiều lần rồi một type khác. **Java semantics luôn phải đúng**, ngay cả nếu compiler đã chọn đường specialized dựa vào profile trước đó.

Điều mà output **không chứng minh**:

- Method `dispatch` đã được C2 compile.
- JIT chắc chắn đã inline `AddOne.apply`.
- Gặp `DoubleIt` chắc chắn gây một deoptimization.
- Dữ liệu receiver trong MDO có đúng tỷ lệ 20.000:5.

Các điều ấy phải được chứng minh bằng compiler diagnostics **trên JVM đang chạy**; JIT có thể inline vào caller, không compile trước type switch hoặc chọn fallback hợp lệ.

## 11. Type profiling và speculative inlining

Nếu một call site phần lớn gặp receiver class `AddOne`, C2 có thể tối ưu đường hot theo mô hình:

```text
Bytecode:
  invokeinterface Operation.apply

Observed at site:
  AddOne thường xuyên
  DoubleIt hiếm

Optimized fast path (mô hình):
  if receiver is AddOne:
      inline AddOne.apply(x)
  else:
      đúng hành vi dynamic dispatch (fallback/deopt)
```

**Không bắt buộc JIT phải sinh đúng một lệnh `if`** như sơ đồ. Compiler có thể dùng inline caches, devirtualization nhờ class hierarchy, exact types hoặc guards ở cấp mã máy; loại guard và cách fallback tùy build.

### Tại sao dữ liệu profile có thể không còn đại diện?

Application warmup chỉ chạy `AddOne`; sau rollout production bắt đầu nhận requests dùng `DoubleIt` hoặc thêm implementations mới. Profile ban đầu không còn đại diện cho phân phối hiện tại.

Nếu một speculative assumption trở nên sai, JVM có thể:

- Đi qua **generic fallback** đã sinh sẵn.
- Thực hiện **deoptimization** để trả execution về representation an toàn.
- Tiếp tục profiling và **recompile** với dữ liệu mới.
- **Invalidate/make not entrant** nmethod có dependency không còn đúng.

**Không bao giờ được cho kết quả sai** chỉ vì profile cũ. Guards/deopt/fallback là cách HotSpot khai thác đoán định nhưng giữ **Java-visible semantics**. Deopt là hành vi VM bình thường, không phải một Java exception nghiệp vụ.

## 12. Class Hierarchy Analysis (CHA) khác dynamic type profiling

Một lời gọi interface có thể được tối ưu vì:

1. Compiler thấy kiểu đích **exact/final** qua thông tin static.
2. **Class Hierarchy Analysis** nhận thấy một hierarchy/implementation phù hợp ở thời điểm compile và ghi dependency cần thiết.
3. **Dynamic type profile** cho thấy receiver type thường gặp nhất tại call site.
4. Những phân tích khác (inlining, escape, call graph) cho thấy đích gọi đủ đơn giản.

| Nguồn kiến thức | Ví dụ | Giới hạn |
| --- | --- | --- |
| Bytecode/static type | Variable được khai báo `Operation` | Không luôn xác định implementation |
| `final`/exact inferred type | Receiver chắc chắn class cụ thể trong path | Không quyết định một mình inline |
| CHA | Hierarchy ở VM thời điểm compile | Class mới có thể làm dependency cần invalidation |
| Type profile | Site thấy nhiều `AddOne` hơn `DoubleIt` | Profile có thể chưa đầy đủ hoặc lệch warmup |
| Compiler heuristic | Callee nhỏ, call hot | Có thể thay đổi theo JDK/flags |

Do đó, log cho biết một call được inline **không đủ chứng minh** rằng đó là thành quả trực tiếp của MDO type profile, nếu JIT đã biết đích gọi từ static/CHA.

## 13. Branch profile và type-check feedback bổ sung điều gì?

Bài tập trung ba tín hiệu, nhưng một MDO có thể có thống kê khác giúp chọn hot path:

```java
static int normalize(int value) {
    if (value >= 0) {
        return value + 1;
    }
    return -1;
}
```

Đây là **method fragment**. Nếu hầu hết input dương, compiler có thể bố trí code để đường hot ít overhead hơn. Tuy nhiên:

- Một Java `if` không tương đương một raw branch counter ổn định; bytecode và machine code có thể có CFG khác.
- Profile đo các paths đã quan sát, không chứng minh input tương lai vẫn cùng phân phối.
- Branch profiling không thay thế data validation; mọi input hợp lệ vẫn phải được xử lý đúng.
- Một số type-check records và uncommon-trap feedback giúp compiler tránh tiếp tục đặt giả định đã bị phá quá nhiều.

Không coi “99% true trong test” là license để xóa nhánh còn lại khỏi Java behavior. Nếu deoptimization/fallback xảy ra, code vẫn phải cho kết quả đúng.

## 14. Compiler levels 0–4 không phải dấu xác nhận tốc độ

Một method đã được compile level 4 có thể vẫn chậm vì:

- Algorithm có độ phức tạp lớn.
- Code phải truy cập dữ liệu không nằm trong CPU cache.
- Làm I/O, locks hoặc các safepoints.
- Có nhiều allocations/GC pressure.
- Receiver profile đa dạng và compiled code cần generic virtual calls.
- Method chịu frequent deoptimization hoặc code cache pressure.
- Startup JIT compilation cost cao so với thời gian tồn tại workload.

Ngược lại, một helper ngắn ở C1 hoặc được inline vào caller có thể đủ nhanh. **Tier là trạng thái compilation**, không phải xếp hạng hiệu năng trên dữ liệu thực.

### Một method có thể được compile nhiều lần

Các trường hợp hợp lệ:

- Promotion từ C1 tier 3 lên C2 tier 4.
- OSR entry cho loop đang chạy.
- Recompilation sau khi profile đủ mature hoặc dữ liệu thay đổi.
- Invalidation/deoptimization do class hierarchy dependency.
- Thay thế một compiled version không còn entrant.

Đừng đọc mỗi dòng `made not entrant` rồi gọi là memory leak hoặc crash. Cần timeline và lý do compile/deopt.

## 15. Quan sát bằng PrintCompilation

Trên HotSpot Java 17:

```bash
java -XX:+PrintCompilation BackedgeOsrDemo
```

Thông tin có thể xuất hiện: compile ID, timestamp, level, method name, bytecode size, marker `%` cho OSR, `@ BCI`, trạng thái `made not entrant` hoặc `made zombie`. Định dạng chính xác và các markers phụ thuộc build.

| Quan sát | Suy luận có căn cứ | Không thể kết luận |
| --- | --- | --- |
| Level 3 | C1 đã compile ở tier có profiling phù hợp | C2 chắc chắn sẽ xảy ra ngay |
| Level 4 | Có compilation task/result C2 | Future calls luôn chỉ dùng đúng nmethod này |
| `%` và `@ ...` | Có OSR compilation tại BCI | Vòng lặp Java luôn có OSR |
| `made not entrant` | Nmethod bị rút khỏi đường nhập mới | Chương trình có lỗi semantics |
| Compile IDs liên tục | Có activity của compiler threads | CPU bottleneck chính là JIT |

**Cảnh báo:** output của `PrintCompilation` thường có các methods nội bộ JDK, không chỉ class do bạn viết. Đừng tạo test assert thứ tự chính xác của compiler events; HotSpot scheduling/tiering/JIT thresholds đều biến động theo machine và version.

## 16. Quan sát inlining với PrintInlining

Một phương thức có thể hot nhưng JIT không inline vì code quá lớn, thiếu thông tin receiver, call not hot enough hoặc inlining budget. Dùng diagnostic options:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining ReceiverProfileDemo
```

Đây là một dòng lệnh Bash, PowerShell hoặc CMD đều có thể dùng trên HotSpot build hỗ trợ các options ấy. `PrintInlining` thường được khóa bởi **diagnostic VM options**; đặt `UnlockDiagnosticVMOptions` trước nó.

Đọc các dòng liên quan `ReceiverProfileDemo::dispatch`, `AddOne::apply`, `DoubleIt::apply` rồi đối chiếu compilation level. Thấy `inline (hot)` hay một lý do không inline có thể giúp giải thích decision, nhưng **không được bịa một log cố định**. Compiler có thể inline toàn bộ `dispatch` vào `run` và phân bố receiver profile theo inline context.

### Log nhiều không phải lúc nào cũng tốt

Diagnostic logging có thể tạo nhiều I/O, ảnh hưởng startup và benchmark; chỉ bật trong môi trường phù hợp, lọc/thu thập có giới hạn, không khẳng định “bật PrintInlining là cách làm app nhanh hơn”.

## 17. LogCompilation để phân tích sâu hơn

Một số HotSpot JDK 17 builds hỗ trợ log XML từ compiler:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -XX:LogFile=jit-log.xml ReceiverProfileDemo
```

Bạn có thể phân tích với **JITWatch** hoặc công cụ hiểu HotSpot compilation logs để quan sát tier transitions, inline attempts, failure reasons, trap/deoptimization patterns.

**Giới hạn cần nhớ:**

- Đây không phải API Java SE portable.
- File có thể lớn, tốn CPU/I/O và chứa thông tin về class/method names.
- Tên/định dạng các records có thể khác theo build.
- Compilation log không phải profiler CPU sampled stack và không cung cấp per-business-request invocation counts.
- Không nên diễn giải “không có method trong log” là method không từng chạy; có thể method chưa được JIT compile hoặc đã inline vào caller khác.

## 18. JFR, JMC và jcmd: xem compilation trong timeline thật

**Java Flight Recorder (JFR)** của JDK 17 có thể ghi sự kiện liên quan compiler, JVM runtime, GC, CPU và application time. Ví dụ trên một **server còn chạy** với PID thật:

```bash
jcmd <PID> JFR.start name=jit-observe settings=profile
jcmd <PID> JFR.dump name=jit-observe filename=jit-observe.jfr
jcmd <PID> JFR.stop name=jit-observe
```

`<PID>` là placeholder; thay bằng PID của process đang chạy, có quyền attach và JFR enabled. File `jit-observe.jfr` có thể được phân tích bằng Java Mission Control (JMC). Configuration `settings=profile` giúp bật những categories/events phù hợp với bộ setting của JDK, nhưng các event thresholds/sampling cũng phụ thuộc build và recording configuration.

JFR hữu ích để nối compiler events với **CPU samples, allocation/GC**, nhưng **không** phải một API công khai hiển thị **mọi raw MethodData counter/receiver record** từ interpreter/C1.

### Vì sao JFR có thể không thấy compiler event bạn muốn?

- Application chạy quá nhanh và kết thúc trước khi recording hoạt động.
- JVM đã compile method từ giai đoạn startup trước khi bắt đầu record.
- Profile settings không ghi một event type/threshold nào đó.
- Method được inline hoặc code chưa đủ hot để compile.
- Logging/sampling chi phí và data-loss conditions làm việc quan sát không tuyệt đối.

Không lấy sự vắng mặt của một JFR event làm bằng chứng rằng Java bytecode chưa bao giờ được chạy.

## 19. Dùng javap để nhận diện invoke và backedges

`javap -c -p` hiển thị **bytecode** trong class file, không phải machine code đã được JIT sinh:

```bash
javap -c -p ReceiverProfileDemo
javap -c -p BackedgeOsrDemo
```

Trong `ReceiverProfileDemo.dispatch`, thường có `invokeinterface` tới `Operation.apply`, nhưng **constant-pool index và BCI** tùy build. Trong `BackedgeOsrDemo.calculate`, có thể thấy branch/quay ngược tới loop header.

**Phân biệt rõ:**

| Công cụ | Quan sát được |
| --- | --- |
| `javap` | Class-file bytecode, BCI, method descriptors, metadata |
| `PrintCompilation` | JIT compile events, levels, OSR hints |
| `PrintInlining` | Inlining decisions/reasons của compiler |
| `LogCompilation`/JITWatch | Timeline và compiler diagnostics chi tiết hơn |
| JFR/JMC | Runtime activity, compilation-related events và sampled performance |
| CPU profiler | Code paths đang tiêu CPU theo sampling/instrumentation |
| Heap dump | Object graph/reachability, không phải invocation/type callsite profile |

Một log của C2 có thể nhắc BCI được lấy từ bytecode của method, nhưng nó **không** là Java source line. Một dòng source có thể tạo nhiều bytecode instructions; cần `LineNumberTable` nếu muốn đối chiếu source.

## 20. Vai trò của speculative optimization và deoptimization

Khi C2 dùng profile, nó có thể ưu tiên một hot path với **guards**:

```text
C2 thấy site đa số AddOne:
   |
   v
Guard receiver type == AddOne
   | true                   | false
   v                        v
Inlined fast path      Generic call / deopt
   |                        |
   +------> correct Java result
```

Nếu assumption bị phá, JVM có thể dùng **uncommon trap** để phục hồi interpreter-compatible state, sau đó reprofile/recompile. Những sự kiện này có cost nhưng là một phần của cơ chế **giữ an toàn cho speculative optimization**.

Ngoài receiver types, HotSpot có thể invalidate compiled code khi một assumption về **class hierarchy** không còn đúng sau khi class mới được load. Vì vậy đừng luôn quy một `made not entrant` cho receiver type profile sai; có thể do dependency khác.

Một deoptimization event **không phải Java exception** và không đồng nghĩa Java application trả kết quả sai. Nếu nó diễn ra liên tục trong workload ổn định, mới nên điều tra tính bền vững của profile và compile policies.

## 21. Profile pollution: dữ liệu warmup khác production

Ví dụ một server ban đầu chỉ xử lý JSON qua interface `Serializer`, rồi 30 phút sau nhận thêm Binary và XML formats. Callsite type profile của một đoạn code có thể biến đổi:

```text
Khởi động:
  Serializer.encode at BCI K
  JSON 100%

Sau rollout:
  JSON 50%
  Binary 30%
  XML 20%
```

Các tỷ lệ này là **mô hình**, không phải số liệu JVM đã quan sát. Nếu JIT đã special-case một receiver, workload shift có thể làm generic fallback tăng hoặc gây reoptimization.

Một kết luận sai thường gặp: “JVM profile sai, vậy Java interface là vấn đề.” Thực tế interface dispatch là ngữ nghĩa đúng; cách tối ưu profile-guided chỉ có lợi khi dữ liệu thống kê phù hợp. Có thể cải thiện bằng thiết kế callsite ổn định hơn **nếu CPU profiler chứng minh đó là bottleneck**, nhưng không phá abstraction chỉ để ép monomorphic.

## 22. Tránh nhầm Java benchmark với JIT profile

Một vòng lặp tự đo `System.nanoTime()` quanh cùng một method có thể sai lệch vì:

- **Warmup chưa đủ:** class loading, C1/C2 compilation, profile collection trộn vào thời gian đo.
- **Dead-code elimination (DCE):** result không được dùng nên JIT bỏ bớt logic.
- **Constant folding:** input cố định có thể được tính trước.
- **Loop unrolling/vectorization:** source iteration count không tương ứng machine operations.
- **Inlining:** số call instructions ở máy khác số lời gọi source.
- **OSR:** giữa lúc loop chạy có thể đổi sang compiled representation khác.
- **GC, CPU frequency, OS scheduling và background work** làm nhiễu.
- **Diagnostic logging** làm lệch thời gian.
- **Workload distribution** khác thực tế làm type profile không đại diện.

Dùng **JMH** (Java Microbenchmark Harness) với warmup, measurement, forks và result consumption thích hợp cho microbenchmark. Dùng **load test/JFR** cho end-to-end performance. Một microbenchmark chỉ kiểm tra một implementation type có thể không đại diện cho một polymorphic production callsite.

### Điều gì có thể suy ra từ output ví dụ?

- `BackedgeOsrDemo` in 28.000.000: vòng lặp tính đúng.
- `ReceiverProfileDemo` in 110.000 và 20: dynamic dispatch tính đúng cho cả hai receiver types.
- **Không** thể kết luận C2 đã inline, OSR đã diễn ra, hay một object đã đổi loại. Những điều đó cần **compiler diagnostics trên bản JDK cụ thể**.

## 23. Hạn chế của phương pháp đếm và lấy mẫu

**Invocation/backedge counters** hỗ trợ tiering, nhưng HotSpot có thể thay đổi cách cập nhật do execution tier. **Type profiles** lưu distributions tại một số sites, nhưng không phải mọi event đều phải được giữ thành một row. **JFR/JMC** và profilers lại dùng event/sampling semantics riêng.

Những yếu tố làm khác số đo:

1. Interpreter, C1 và C2 có instrumentation khác nhau.
2. Một method có thể được inline hoặc compiled theo nhiều versions.
3. Counters có thể được tổng hợp/decay/reset theo chính sách.
4. MDO record capacity cho receiver types hữu hạn.
5. Compile policy có thể giảm profiling overhead ở tiers nhất định.
6. JFR event sampling không phản ánh mọi invocation theo cùng nghĩa.
7. Java bytecode sites và machine-level control flow không trùng nhau sau optimization.
8. Workload thay đổi sau giai đoạn collecting profile.

Đừng so “100.000 calls” trong application metric với “hotness counter” trong internal diagnostic rồi quy mọi sai khác cho bug JVM. Hai số có **mục đích và definition khác nhau**.

## 24. Khi nào cân nhắc `-Xint` và `TieredStopAtLevel`?

Trong môi trường học tập, có thể thử:

```bash
java -Xint BackedgeOsrDemo
java -XX:TieredStopAtLevel=1 BackedgeOsrDemo
java -XX:+PrintCompilation BackedgeOsrDemo
```

- `-Xint` ưu tiên interpreted-only mode, nên không áp dụng JIT compilation thông thường.
- `-XX:TieredStopAtLevel=1` giới hạn compile levels ở C1 level 1, không phải tắt JIT.
- `PrintCompilation` quan sát compilation activity mà vẫn dùng tiering policy mặc định.

Những flags này thuộc HotSpot/launcher implementation; không phải lời khuyên production tuning tổng quát. Đừng lấy một lần đo rồi kết luận interpreter luôn chậm hơn chính xác một hệ số cố định; CPU/JVM/benchmark setup ảnh hưởng mạnh.

### Đừng sửa thresholds bừa bãi

Giảm threshold có thể compile quá sớm với profile chưa đủ đại diện, tăng compiler CPU/code cache overhead và dễ làm việc profiling khác đi. Tăng threshold có thể khiến ứng dụng ngắn hạn không bao giờ dùng C2 trong giai đoạn đo. Trước khi tuning, ưu tiên xem **real CPU hotspots, compile queue, inlining và deoptimization evidence**.

## 25. Một tình huống kiểm chứng từng bước

Giả sử service Java 17 có interface `PricingRule`, mỗi request áp dụng một rule:

- **Triệu chứng:** CPU tăng sau khi thêm nhiều rule implementations.
- **Giả thuyết A:** callsite `rule.evaluate` từ monomorphic thành polymorphic, nên C2 inline ít hơn.
- **Giả thuyết B:** thuật toán ở một rule mới phức tạp hơn, dù dispatch không đổi.
- **Giả thuyết C:** các requests mới allocate/parse nhiều dữ liệu hơn; bottleneck ở GC chứ không phải virtual call.
- **Giả thuyết D:** lúc rollout code bị deopt/recompile tạm thời, sau warmup ổn định.

Quy trình kiểm chứng:

1. Cố định input mix và đo p95/p99 latency, CPU, GC, throughput.
2. Dùng CPU profiler/JFR xác định code path thực sự đang chiếm CPU.
3. Bật `PrintCompilation`/`PrintInlining` ở môi trường chẩn đoán phù hợp.
4. Xem `PricingRule.evaluate` có hot, được compiled/inlined hay không.
5. So compilation profile trước và sau rollout **trên cùng JVM version**.
6. Kiểm tra if/branches và call site type distributions bằng evidence phù hợp.
7. Tách CPU của method implementation ra khỏi dispatch overhead bằng benchmark riêng.
8. Chỉ refactor/tune nếu có cải thiện đo được, không chỉ vì thấy một log “virtual call”.

Một callsite có nhiều receiver types không tự là bug. **Độ đa hình chỉ là một biến** trong performance của Java; thiết kế đúng và khả năng bảo trì vẫn quan trọng.

## 26. Checklist đọc compiler diagnostics có kỷ luật

| Câu hỏi | Công cụ/đầu mối | Lưu ý |
| --- | --- | --- |
| Method được gọi nhiều nhưng có compile không? | `PrintCompilation`, JFR | Compiler event không đếm từng invocation |
| Loop dài có OSR compilation không? | `PrintCompilation` dấu `%`/`@ BCI` | Không phải mọi loop chắc OSR |
| Bytecode site nào chứa invoke? | `javap -c -p` | Bytecode không có dynamic receiver distribution |
| C2 có inline không? | `PrintInlining`, LogCompilation/JITWatch | Output thay đổi theo thresholds/call graph |
| Type profile có ổn định không? | JVM-specific diagnostics, workload tracing | Không có Java SE API đọc raw MDO |
| Có deoptimization/recompile quá nhiều không? | Compilation logs, JFR events/VM diagnostics | Không tự coi deopt là bug |
| Method chậm do CPU hay GC? | CPU profiler, JFR GC/CPU data | Compiler log không phải CPU flame graph |
| Compiler bị nghẽn queue/code cache? | VM/compilation diagnostics | Hot method có thể chờ compiler |
| Tối ưu thực sự nhanh hơn không? | JMH hoặc end-to-end load test | Warmup/forks/input mix quyết định |

## 27. Những hiểu lầm thường gặp

| Khẳng định sai | Điều đúng |
| --- | --- |
| “JIT chỉ đếm method calls” | HotSpot còn quan sát loop backedges, receiver types, branches |
| “Method gọi N lần chắc chắn thành C2” | Tiers/thresholds/queues là dynamic VM policy |
| “Loop phải gọi method hàng nghìn lần mới compile” | Một long-running loop có thể kích hoạt OSR |
| “Backedge = source-level loop iteration” | Liên quan bytecode CFG, không phải contract source counter |
| “C1 không hỗ trợ profiling” | C1 tiers có limited/full profiling tùy level |
| “C2 luôn tiếp tục ghi toàn bộ profile như interpreter” | Profiling strategy khác nhau theo tier |
| “MDO là một Java Map public” | MDO là HotSpot internal profiling metadata |
| “Type profile chỉ có một bảng cho cả interface” | Nó có tính **site-specific** |
| “Monomorphic luôn được inline” | Còn cost model, static type, code size và budget |
| “Megamorphic luôn chậm” | Cost thực phải đo, nhất là khi callsite không hot |
| “Inlining dựa trên type profile nghĩa type khác sẽ tính sai” | Guards/fallback/deoptimization bảo toàn Java semantics |
| “Deoptimization là lỗi runtime” | Có thể là phần bình thường của speculative JIT |
| “OSR là gọi method lại từ đầu” | OSR tiếp tục invocation đang chạy ở một BCI |
| “`javap` hiển thị mã máy của JIT” | `javap` disassemble **bytecode** |
| “PrintCompilation là per-call trace” | Nó ghi compiler events và state |
| “PrintInlining output ổn định cho mọi JDK” | Diagnostic output và decisions khác build |
| “Chạy ra đúng kết quả chứng minh JIT đã OSR” | Java semantics đúng không chứng minh compiler path |
| “JFR profile là raw MDO dump” | JFR có sự kiện/sampling riêng |
| “Cứ bật nhiều log thì app sẽ nhanh hơn” | Logging có overhead, chỉ để chẩn đoán |
| “Một benchmark không warmup đủ để kết luận” | Warmup, DCE, JIT/OSR và CPU noise có thể làm sai |

## 28. Bài tập và câu hỏi tự kiểm tra

**Câu 1.** Một method được gọi một lần nhưng có vòng lặp 50 triệu iterations. Tại sao JVM vẫn có thể muốn compile nó?

**Đáp:** Backedge activity báo loop nóng, có thể kích hoạt **OSR**.

**Câu 2.** Method invocation counter có bắt buộc bằng đúng số lần gọi Java source?

**Không.** Nó là VM-internal hotness/profiling signal và còn phụ thuộc execution tiers, compilation/inlining.

**Câu 3.** C1 level 2 và 3 khác nhau cơ bản ở đâu?

Level 2 thường thu **invocation/backedge signals hạn chế**; level 3 tạo **fuller profiling**, gồm type/branch feedback.

**Câu 4.** Cùng interface nhưng hai callsites có thể có receiver distributions khác nhau không?

**Có.** Type profiling tại từng callsite/BCI cung cấp dữ liệu theo site.

**Câu 5.** Profile cho thấy 99% `AddOne`. C2 chắc chắn phải inline `AddOne.apply`?

**Không.** Inlining còn phụ thuộc hotness, code size, CHA, budget và compiler policy.

**Câu 6.** Gặp receiver class mới có được phép cho kết quả cũ của class khác không?

**Không.** JVM phải dùng guards/fallback/deopt để bảo đảm Java dynamic dispatch semantics.

**Câu 7.** Dấu `%` trong HotSpot compilation log thường gợi ý gì?

Một **OSR compilation event**, thường kèm vị trí `@ BCI`.

**Câu 8.** `javap -c` có hiển thị C2 machine code không?

**Không.** Nó hiện JVM bytecode từ class file.

**Câu 9.** `BackedgeOsrDemo` in tổng đúng có chứng minh OSR đã xảy ra?

**Không.** Chỉ chứng minh phép tính đúng; cần diagnostics để biết OSR.

**Câu 10.** `PrintInlining` có thể dùng làm benchmark latency trực tiếp không?

**Không.** Nó là compiler diagnostic và có logging overhead; dùng JMH/load tests để đo latency.

**Câu 11.** Deoptimization có luôn là Java exception hoặc memory leak?

**Không.** Nó là một phần của speculation/fallback hợp lệ.

**Câu 12.** Warmup chỉ có một receiver type, production nhiều types. Điều gì có thể đổi?

Type profile, inline guards, deoptimization/recompilation và throughput; không có kết luận tốc độ cố định.

## Tài liệu chuẩn và nguồn kiểm chứng

- [Oracle Java SE 17 — `java` launcher](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-XX:+PrintCompilation`, `-XX:+LogCompilation`, flags JIT và diagnostic options.
- [Oracle Java SE 17 — `javap`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): bytecode BCI và invoke/branch instructions.
- [JVMS Java SE 17 — Instruction Set (`6.5`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5): `invokeinterface`, `invokevirtual`, `goto` và control flow.
- [JVMS Java SE 17 — JVM Frames (`2.6`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6): locals/operand stack phục vụ hiểu OSR và deopt.
- [OpenJDK 17 MethodData source](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/oops/methodData.hpp): receiver/branch profile data và counters, chi tiết thay đổi theo bản build.
- [OpenJDK 17 tiered compilation policy](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/compiler/tieredThresholdPolicy.hpp): các levels/notification/counter heuristics thuộc runtime.
- [How Tiered Compilation Works in OpenJDK — Microsoft Java Engineering](https://devblogs.microsoft.com/java/how-tiered-compilation-works-in-openjdk/): diễn giải C1/C2, profile và transitions.
- [Java SE 17 — JFR](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html) và [`jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): timeline quan sát compilation/runtime.
- [JITWatch](https://github.com/AdoptOpenJDK/jitwatch): công cụ phân tích compilation logs và inline decisions của HotSpot.

**Ghi nhớ:** HotSpot không tối ưu chỉ dựa vào “method nào được gọi nhiều”. **Invocation counters** phát hiện repeated calls; **backedge counters** phát hiện loops nóng và giúp chọn **OSR**; **receiver type profiles** cung cấp thông tin tại các callsites phục vụ guarded/inlined dispatch. Tiered C1/C2 cân bằng profiling cost với tốc độ máy mã, còn **fallback/deoptimization** bảo đảm kết quả Java vẫn đúng khi profile hoặc class hierarchy thay đổi. Khi muốn chứng minh JIT đang làm gì, dùng compiler diagnostics trên **đúng JVM Java 17** và benchmark hợp lệ, không phán đoán từ source loop count hay output một lần chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-004">Biên dịch phân tầng trong HotSpot và vai trò của C1, C2</a>

<details>
<summary>Xem chi tiết</summary>

# Biên dịch phân tầng trong HotSpot và vai trò của C1, C2

**Tiered compilation (biên dịch phân tầng)** là chiến lược của **HotSpot JVM** kết hợp **interpreter** và các cấp độ **JIT compilation** để cân bằng hai mục tiêu có phần mâu thuẫn:

- **Khởi động và warm-up nhanh:** không tốn quá nhiều CPU để tối ưu sâu mọi method ngay từ đầu.
- **Hiệu năng ổn định dài hạn:** khi JVM đã quan sát đủ cách chương trình chạy, có thể tạo mã máy tối ưu hơn dựa trên thông tin thực tế.

Trong mô hình HotSpot phổ biến của **JDK 17**, hai bộ biên dịch đóng vai trò chủ đạo:

- **C1** (*client compiler*): biên dịch tương đối nhanh, có các chế độ tạo mã với **không profiling**, **profiling giới hạn** hoặc **profiling đầy đủ**. C1 giúp method nóng chuyển từ interpreter sang native code mà chưa phải trả hết chi phí tối ưu của C2.
- **C2** (*server compiler*): bộ tối ưu hóa mạnh hơn, dùng những thông tin runtime đã thu thập để tối ưu method, inlining, loops, speculative type optimizations và nhiều phép chuyển đổi khác; thường tạo **level 4** code.

**Quan trọng:** mô tả C1/C2 và năm mức biên dịch là kiến trúc **HotSpot**, không phải một phần bắt buộc của Java Language Specification hoặc JVM Specification. Một JVM khác, một bản HotSpot đặc thù hay một backend JIT khác có thể khác. Học cấp độ để hiểu runtime, nhưng **không viết ứng dụng dựa vào việc một method chắc chắn được JIT ở level nào**.

## 1. Từ Java source tới interpreter và mã máy JIT

```text
           javac (trước lúc chạy)
   MyApp.java ─────────────────────► MyApp.class
                                     JVM bytecode
                                          │
                                  class loading/linking
                                          │
                                          ▼
                          HotSpot interpreter (level 0)
                          đọc/thực thi bytecode
                          và thu thập thông tin
                                          │
                                method đủ "hot"?
                                          │
                          ┌───────────────┴───────────────┐
                          ▼                               ▼
                   C1 compiler                       C2 compiler
                  levels 1,2,3                         level 4
                  biên dịch nhanh                   tối ưu chuyên sâu
                          │                               │
                          └──────────┬────────────────────┘
                                     ▼
                            native machine code
                           trong HotSpot code cache
                                     │
                                     ▼
                               CPU thực thi
```

Sơ đồ chỉ thể hiện **các thành phần**, không áp đặt một con đường duy nhất “interpreter → C1 → C2” cho mọi method. Một method có thể:

- Chạy ở interpreter suốt đời tiến trình nếu ít sử dụng.
- Được C1 compile rồi không bao giờ chuyển lên C2.
- Có nhiều phiên bản code ở các mức và/hoặc cho OSR.
- Được C2 compile trực tiếp trong một số tình huống.
- Bị deoptimize, quay về interpreter hoặc code ở cấp thấp hơn rồi được biên dịch lại.

### Ba loại mã khác nhau

| Thành phần | Lưu ở đâu / vai trò | Dễ nhầm |
| --- | --- | --- |
| **Source Java** | Tệp `.java`, đầu vào `javac` | Không phải thứ CPU thực thi trực tiếp |
| **JVM bytecode** | `.class` / runtime metadata của class | `javap -c` xem bytecode, **không** xem JIT machine code |
| **Machine code JIT** | Native executable code do VM tạo, trong code cache | Có thể bị thay thế, vô hiệu hóa, tối ưu lại |

Lệnh `javac` chạy **trước** JVM execution khi build ứng dụng. **C1 và C2 không phải hai lựa chọn backend thay `javac` trong quá trình biên dịch source**; chúng hoạt động khi code đã chạy dưới JVM.

## 2. Vì sao không dùng C2 cho mọi method ngay lập tức?

Tối ưu sâu là công việc tốn CPU và thường cần **profile của chương trình thực tế**:

- Method được gọi bao nhiêu lần?
- Vòng lặp chạy bao nhiêu backedges/iterations?
- Những runtime types nào thường xuất hiện tại call site?
- Các nhánh `if` nào thường được chọn?
- Giá trị hoặc trạng thái nào cho phép giảm checks?
- Một lời gọi có nên được inline không?
- Có thể biến đổi loop, loại bỏ bounds checks hay hoist code không?

**Nếu tối ưu quá sớm:** JVM mất nhiều thời gian/CPU compiler cho code chưa chắc được dùng; startup và throughput giai đoạn đầu có thể xấu. **Nếu không bao giờ tối ưu:** method nóng chạy chậm dài hạn dù JVM đã có nhiều cơ hội làm tốt hơn.

Tiered compilation dùng **C1 như cầu nối nhanh**, đồng thời thu thập profile đủ để **C2 có đầu vào hữu ích**. Thực tế policy còn cân nhắc **compile queues**, compiler thread availability, call/backedge rates, method size, code cache, class loading và nhiều giới hạn khác.

### Hot code không chỉ là lời gọi method nhiều lần

Method có thể chỉ được gọi **một lần** nhưng chứa vòng lặp khổng lồ. Backedge counters giúp runtime phát hiện nóng ngay trong một lần thực thi và có thể dùng **on-stack replacement (OSR)** để chuyển sang compiled loop. Ngược lại, method được gọi lặp lại nhưng rất ngắn/trivial có thể được inline vào caller, khiến code cuối cùng không còn lời gọi method độc lập như source.

## 3. Năm execution levels trong HotSpot tiered policy

Trong HotSpot JDK 17 theo sơ đồ tiered thường dùng, có **năm cấp độ** được đánh số 0–4:

| Level | Engine/compiler | Profiling | Mục tiêu chính |
| ---: | --- | --- | --- |
| **0** | **Interpreter** | Có thể ghi nhận invocation/backedge counters và method data | Thực thi sớm, quan sát runtime |
| **1** | **C1 simple** | **Không tạo full profiling instrumentation** | Compiled code C1 tương đối nhẹ; hợp đường code không cần profile thêm |
| **2** | **C1 limited profile** | Chủ yếu invocation/backedge counting; ít hơn level 3 | Chạy code nhanh hơn level 3 khi C2 queue chịu tải, vẫn nhận biết độ nóng |
| **3** | **C1 full profile** | Thu thập profile phong phú, có method data liên quan | Chuẩn bị thông tin để quyết định và tối ưu level 4 |
| **4** | **C2 full optimization** | Tận dụng profile đã có, thường không mang instrumentation như C1 level 3 | Mã tối ưu cho hot path |

**Không hiểu sai tên “full optimization”:** level 1 có thể dùng các tối ưu của **C1** nhưng **không tương đương khả năng tối ưu của C2 level 4**. Level 3 không nhất thiết chạy nhanh hơn level 2, vì profiling instrumentation cũng có chi phí. Level 4 không có nghĩa VM từ nay chắc chắn dùng mã này mãi mãi.

### 3.1. Level 0 — interpreter

- Giúp chương trình **chạy ngay** mà chưa cần compile từng method.
- Có thể ghi nhận invocation counters, backedge events, method profile theo chính sách runtime.
- Chưa đặt một JIT-compiled nmethod tương ứng cho mọi method.
- Có overhead diễn giải bytecode, nhưng cold code có thể không đáng để compile.

### 3.2. Level 1 — C1 không profiling

- Tạo code C1 có tối ưu phù hợp nhưng **không gắn bộ thu thập profile đầy đủ**.
- Có thể chọn cho những methods mà policy thấy không cần tiếp tục lên C2, hoặc khi C2 không phù hợp.
- **Không phải một checkpoint bắt buộc** trước level 2 hay 3; đường chuyển có thể là 0 → 3 trực tiếp.

### 3.3. Level 2 — C1 limited profiling

- Chủ yếu duy trì thông tin invocation/backedge để policy ra quyết định.
- Có thể được chọn khi queue C2 dài khiến phương án level 3 full profiling không hấp dẫn trong giai đoạn chờ.
- Không tự tạo đủ loại profile call site như level 3 trong cùng mô hình.
- Level 2 cũng không phải tầng bắt buộc mọi method phải vượt qua.

### 3.4. Level 3 — C1 full profiling

- Thực thi compiled code của C1 đồng thời thu thập nhiều runtime profile hơn.
- **MethodData / MethodDataOop lịch sử, MethodData trong HotSpot hiện đại** có thể lưu những thống kê dùng cho adaptive optimization.
- Có thể cung cấp branch/type profiles hữu ích cho C2.
- Tốn thêm chi phí instrumentation/metadata, nên không nên khẳng định level 3 luôn là compiled code nhanh nhất trong giai đoạn chờ.

### 3.5. Level 4 — C2 tối ưu chuyên sâu

- Tận dụng profile như các call site types thường gặp, branch probabilities và hot loops.
- Có thể tạo speculative optimizations kèm guard/deoptimization fallback.
- Chi phí compilation cao hơn nhưng hướng tới **steady-state performance** tốt khi code thực sự nóng.
- Không bảo đảm luôn thắng C1 trong mọi microbenchmark; hiệu quả phụ thuộc method shape, target CPU, workload, profiling và compile budget.

## 4. Các đường chuyển level không phải tuyến tính

Mô hình chuyển phổ biến:

```text
                          ┌───────────────────┐
                          │ Level 0           │
                          │ Interpreter       │
                          └──────┬────────────┘
                                 │ method nóng
                  ┌──────────────┼─────────────────────┐
                  ▼              ▼                     ▼
        ┌────────────────┐ ┌────────────────┐  ┌────────────────┐
        │ C1 level 3     │ │ C1 level 2     │  │ C1 level 1     │
        │ full profiling │ │ limited profile│  │ no profiling   │
        └────────┬───────┘ └────────┬───────┘  └────────────────┘
                 │                  │
                 ▼                  ▼
          ┌────────────────────────────┐
          │ C2 level 4                 │
          │ full optimization          │
          └────────────────────────────┘

Deoptimization có thể chuyển đường thực thi về interpreter/
mã compiled khác, để sau đó recompile nếu chính sách phù hợp.
```

Những ví dụ đường đi trong tiered policy:

- **0 → 3 → 4:** đường thường được dùng khi profiler cần tích lũy dữ liệu ở level 3 rồi C2 compile.
- **0 → 2 → 3 → 4:** có thể dùng khi C2 queue cao và VM muốn giảm phí full profiling trong lúc chờ.
- **0 → 3 → 1**, hoặc **0 → 2 → 1:** một số methods có thể nhận code C1 ít instrumentation do compiler/policy đánh giá không cần C2.
- **0 → 4:** có thể xảy ra trong trường hợp thích hợp, ví dụ C1 compilation không thành công hoặc code/profile sẵn.
- **3 → 4 → deopt → 0/3/...:** optimized assumptions có thể mất hiệu lực, JVM khôi phục trạng thái execution đúng và biên dịch lại.

Đường đi chịu ảnh hưởng version và policy. **Các chuyển mũi tên trên là ví dụ của HotSpot**, không phải máy trạng thái bất biến cho mọi application run. Không đặt assertion rằng một method đi qua đủ 5 cấp theo thứ tự.

## 5. Profiling: dữ liệu nào dẫn tới quyết định JIT?

### 5.1. Invocation counters và backedge counters

- **Invocation counter:** tín hiệu một method được gọi nhiều lần.
- **Backedge counter:** tín hiệu nhánh quay về trong loop chạy nhiều lần.
- **Rate / event frequency:** policy có thể quan tâm số lần sự kiện trên một đơn vị thời gian, không chỉ tổng counters.
- **Method Data Object (MDO):** metadata chứa profiling information cho các bytecode sites phù hợp.

```java
static long sum(int n) {
    long result = 0;
    for (int i = 0; i < n; i++) {
        result += i;
    }
    return result;
}
```

Đây là một **method minh họa**, không có `main`. Gọi method một lần với `n` rất lớn vẫn có thể tăng backedge counter đủ để runtime xem xét OSR. Gọi method liên tục với `n=0` lại tạo invocation count lớn dù loop body không chạy.

### 5.2. Type profiles và inlining

Một lời gọi ảo:

```java
interface Worker { int work(int x); }

static int apply(Worker w, int x) {
    return w.work(x);
}
```

Đây là **đoạn minh họa**, cần đặt trong các types đầy đủ để chạy. Nếu trong runtime phần lớn `w` có đúng một concrete class, VM có thể ghi nhận **monomorphic call site**, inline method phù hợp và thêm guards để giữ correctness.

Nếu sau này xuất hiện nhiều types hoặc nạp subclass mới làm giả định không còn hợp lệ, optimized code có thể **deoptimize**. Điều này không có nghĩa JIT biên dịch sai; speculative optimization là hợp lệ nếu VM có cơ chế rút lui đúng.

### 5.3. Branch profiles và loop optimizations

Giả sử:

```java
if (x >= 0) {
    fastPath();
} else {
    rarePath();
}
```

Đây là **pseudocode Java**; `fastPath` và `rarePath` phải được định nghĩa mới biên dịch. Khi một nhánh hiếm, JIT có thể đặt hot path thuận tiện hơn trong machine code. Nhưng **không thể** từ đó suy rằng nhánh hiếm bị xóa bỏ và không bao giờ cần thực thi. VM vẫn phải bảo toàn tính đúng đắn khi điều kiện thay đổi.

### 5.4. Profile không bất biến

Profile được ghi từ các inputs thực tế và có thể khác giữa warm-up với production workload. Những hành vi làm thay đổi profile:

- Dữ liệu đầu vào không còn cùng phân bố.
- Cache warms up, request traffic thay đổi.
- Dynamic proxies hoặc subclasses mới được nạp.
- Class loaders khác làm tăng polymorphism.
- Code hot thay đổi theo workload phase.
- Recompilation, code cache pressure hoặc compilation limits.

Vì vậy không dự đoán machine code cuối cùng chỉ từ Java source; nó phụ thuộc **runtime evidence**.

## 6. C1 và C2 khác nhau ở chi phí và tối ưu

| Khía cạnh | C1 | C2 |
| --- | --- | --- |
| Định hướng | Biên dịch tương đối nhanh, hỗ trợ tiered warm-up | Tối ưu chuyên sâu cho code nóng |
| Compilation cost | Thường thấp hơn | Thường cao hơn |
| Runtime profiling | Level 2/3 có instrumentation theo mức | Tận dụng profiles; optimized output ít profiling hơn |
| Compilation levels | 1, 2, 3 trong sơ đồ HotSpot | 4 |
| Tối ưu | Các tối ưu C1 phù hợp, hướng thời gian compile nhanh | Inlining sâu, loop optimization, speculative optimizations, vectorization khi áp dụng |
| Startup | Có thể đưa method hot sang native code sớm | Có thể tạo compile latency/CPU nếu dùng quá sớm |
| Steady-state | Đủ tốt cho nhiều methods | Có thể đạt thông lượng cao hơn trên hot paths |
| Code cache | Tạo C1 compiled nmethods, có bản profiled | Tạo C2 compiled nmethods, thường non-profiled |
| Rủi ro hiểu nhầm | C1 level 3 luôn nhanh hơn level 2 | C2 luôn nhanh nhất cho mọi method |

Từ “C1” lịch sử gắn với **client compiler**, “C2” gắn với **server compiler**, nhưng trong HotSpot hiện đại tiered compilation có thể dùng cả hai **trong cùng một Server VM**. Đừng nghĩ cần chạy `-client` để có C1 trên JDK 17 64-bit.

### C2 không biến Java thành unsafe code

Tối ưu hóa có thể loại bỏ allocation vật lý, inline virtual calls, biến đổi loops hay bỏ checks đã chứng minh dư thừa **miễn vẫn bảo toàn ngữ nghĩa quan sát được**: exceptions, synchronization, Java Memory Model, class initialization, safepoints và các điều kiện bắt buộc.

Nếu một giả định speculative sai, VM có thể deopt và khôi phục trạng thái method/frames ở một điểm hợp lệ. **Speculation không cho phép JIT trả kết quả sai tùy ý**.

## 7. Compilation policy, thresholds và compile queues

HotSpot không JIT method ngay khi invocation count chạm một con số “ma thuật” cố định trên mọi JVM.

Policy có thể cân nhắc:

- Invocation count và backedge count.
- Rate của các events, tốc độ method trở nên hot.
- Profile data đã đủ hữu ích chưa.
- Compiler queue length và compiler threads.
- Method size/bytecode complexity.
- Code cache space.
- Compilation exclusions và compiler failure cases.
- JVM flags, tiered policy và version.

### Compile queue không phải một danh sách FIFO đơn giản

Các compile requests có thể vào hàng chờ của C1/C2, có mức ưu tiên theo Hotness/estimated benefit, và một request có thể trở nên **stale** khi workload thay đổi. VM có thể điều chỉnh thresholds khi compiler bận để tránh biên dịch hàng loạt methods không còn hot.

```text
Method A đang được gọi rất nhiều ─► C1 queue ─► C1 worker
                                        │
                                        ▼
                                   C1 nmethod
                                        │ profiling
                                        ▼
                                     C2 queue ─► C2 worker
                                                    │
                                                    ▼
                                              C2 nmethod
```

Sơ đồ là mô hình, không phải đảm bảo mỗi method luôn có đúng một request trong mỗi queue.

### Compiler threads là tài nguyên runtime

C1/C2 compilation dùng CPU và memory; chúng có thể chạy **bất đồng bộ so với application threads**. Ứng dụng tiếp tục chạy interpreted/C1 code trong khi C2 đang compile. Compile activity ảnh hưởng:

- CPU vào giai đoạn warm-up.
- Tốc độ startup/ready.
- Code cache occupancy.
- Throughput dài hạn sau khi hot methods được tối ưu.

Vì vậy “JVM còn chậm ở 10 giây đầu” có thể do chưa warm up, nhưng cũng có thể do I/O, class loading, cache misses, GC, allocations hay external services. Cần đo đa chiều.

## 8. On-Stack Replacement (OSR) cho vòng lặp đang chạy

Khi một method được gọi **một lần** nhưng loop chạy lâu, sẽ không hiệu quả nếu VM phải chờ method return mới dùng compiled code trong lần gọi sau. **OSR** cho phép biên dịch một entry tương ứng với một **loop/backedge bytecode index** rồi chuyển execution từ interpreter/C1 sang optimized code phù hợp khi method vẫn đang chạy.

```text
Method gọi lần đầu ở interpreter
              │
              ▼
        loop rất dài
              │
       backedge đủ nóng
              │
              ▼
         OSR compile
              │
              ▼
         OSR nmethod
       (entry ở loop BCI)
              │
              ▼
     tiếp tục loop ở code compiled
```

- OSR compiled code có thể **khác** normal-entry compiled code của cùng method.
- `PrintCompilation` có thể đánh dấu OSR bằng ký hiệu `%`; những code/notations khác tùy HotSpot version.
- OSR không đơn giản là “chuyển toàn bộ Java stack sang code cache” — đó là chuyển **execution state** tại điểm hợp lệ.
- Một JVM có thể chọn không OSR trong một lần chạy cụ thể; việc loop nóng không bảo đảm thấy đúng một OSR compile event.

### OSR và tiered levels

Một OSR nmethod có thể được tạo ở cấp độ thích hợp theo VM policy, không nhất thiết chờ tất cả các compilation levels. Khi xem log, phải phân biệt **OSR compile của method** với **normal-entry compile** thay vì cộng chúng như hai method source khác nhau.

## 9. Deoptimization: khi optimized assumptions không còn phù hợp

C2 có thể tối ưu dựa trên type/branch profiles và **guards**. Nếu cần quay về execution path tổng quát, HotSpot có thể thực hiện **deoptimization**.

```text
C2 optimized method
      │
      ├─ assumptions còn hợp lệ ──► tiếp tục mã tối ưu
      │
      └─ guard thất bại/class thay đổi
                    │
                    ▼
            deoptimization
                    │
                    ▼
         interpreter frame state
         hoặc đường thực thi khác
                    │
                    ▼
            profile/recompile nếu cần
```

Những ví dụ:

- Call site từng chỉ nhận class `Dog` nhưng sau đó nhận `Cat`.
- Class hierarchy thay đổi khi một subclass mới được load.
- Rare branch/exceptions xuất hiện nhiều hơn.
- Optimized code phụ thuộc giả định về synchronization hoặc reference state được VM kiểm tra.

**Deoptimization không đồng nghĩa exception hoặc JVM crash.** Nó là cơ chế **duy trì tính đúng đắn khi đã sử dụng speculative optimization**.

### Uncommon traps và recompile

HotSpot có thể đưa đường “hiếm” qua **uncommon trap** để trở về execution tổng quát. Nếu đường đó về sau phổ biến, profile có thể thay đổi và JVM biên dịch lại code ít giả định hơn hoặc tối ưu lại theo dữ liệu mới.

Đọc code JIT đã in ra phải chú ý **từng compile ID/level/BCI và trạng thái phiên bản**, không khẳng định một method mãi chỉ có một compiled body.

## 10. Code cache giữ kết quả biên dịch C1/C2

Mã máy do C1 hoặc C2 tạo được quản lý trong **HotSpot code cache**, thường dưới dạng **nmethods**. Code cache ở vùng native executable memory, **khác Java heap**, metaspace và stack frames.

Một HotSpot cấu hình **segmented code cache** có thể có:

| CodeHeap | Mục đích |
| --- | --- |
| **non-nmethods** | VM-generated runtime stubs, adapters và code không phải compiled Java method |
| **profiled nmethods** | Code C1 có profiling, thường phục vụ giai đoạn warm-up và chuyển level |
| **non-profiled nmethods** | Code ít/không profiling, gồm nhiều compiled methods tối ưu dài hạn |

Không đánh đồng một CodeHeap với một compilation level tuyệt đối; phân bổ thật phụ thuộc version/mode. C1 level 1 có thể nằm ở vùng non-profiled, trong khi C1 level 3 mang instrumentation phù hợp với vùng profiled.

### Khi code cache đầy

Nếu code cache không còn đủ không gian cho nmethod mới, HotSpot có thể **giảm/ngừng compilation** và in cảnh báo dạng:

```text
CodeCache is full. Compiler has been disabled.
```

Đây là **mẫu thông báo thường gặp**, không phải string cam kết của mọi release. Ứng dụng có thể tiếp tục chạy ở interpreter/mã đã có nhưng mất cơ hội tối ưu thêm. Không kết luận chắc chắn code cache đầy là nguyên nhân chỉ vì throughput thấp; cần thống kê code heap, queues và logs.

Các thao tác **code sweeping**, **not entrant/zombie nmethods**, **class unloading** và **deoptimization** giúp quản lý vòng đời compiled code. Không phải cứ JIT compile một method thì native code tồn tại vĩnh viễn tới khi process thoát.

## 11. Bật/tắt tiered compilation trên HotSpot JDK 17

Các lệnh sau là ví dụ dùng đúng **HotSpot**, không phải Java SE flags chuẩn mọi JVM:

| Option | Ý nghĩa | Cẩn trọng |
| --- | --- | --- |
| `-XX:+TieredCompilation` | Bật tiered compilation nếu JVM/build hỗ trợ | Trong nhiều Server VM hiện đại đã bật mặc định |
| `-XX:-TieredCompilation` | Tắt tiered compilation policy | **Không đồng nghĩa chỉ dùng interpreter**; JVM có thể vẫn JIT |
| `-XX:TieredStopAtLevel=1` | Giới hạn mức compilation, thường giữ C1 no-profiling | Không chứng minh mọi method đều được compile |
| `-XX:TieredStopAtLevel=3` | Cho phép các level tới 3, không dùng level 4 của sơ đồ thường | Compiler/mode/runtime flags có thể ảnh hưởng |
| `-Xint` | Chế độ **interpreted-only** cho Java methods | Phù hợp thí nghiệm correctness/perf, thường chậm |
| `-Xcomp` | Ưu tiên aggressively compile methods sớm | Có chi phí startup; không phản ánh chạy bình thường |
| `-XX:+PrintCompilation` | In những compilation events | Output biến động theo runtime |
| `-XX:+PrintCodeCache` | In thống kê code cache | VM/build dependent |
| `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` | Chẩn đoán quyết định inlining (nếu hỗ trợ) | Rất nhiều output, không bật bừa trên production |

**Một bẫy phổ biến:** `-XX:-TieredCompilation` **không phải** một cách viết dài của `-Xint`. `-Xint` rõ ràng hơn khi mục tiêu là so sánh interpreter-only.

### Chú ý về `CompileThreshold`

Không thể khẳng định “trên mọi HotSpot, cứ gọi method đúng 10.000 lần thì được C2 biên dịch”. Trong tiered mode, thresholds chịu tác động counters, rate, compile queues, tier transitions và các JVM flags khác; `CompileThreshold` đơn lẻ không mô tả toàn bộ policy.

Nếu muốn học ý nghĩa từng threshold flag, đọc chính xác **JDK version/source** và dùng compiler logs. Thay threshold có thể làm profile chưa đủ, compile queue quá tải hoặc warm-up khác đi; không nên dùng để tối ưu production khi chưa benchmark.

## 12. Lab Java 17+: method nóng và vòng lặp có thể OSR

Tạo file duy nhất **`TieredCompilationDemo.java`**:

```java
public class TieredCompilationDemo {
    private static volatile long lastValue;

    static long score(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += (i & 7);
        }
        return sum;
    }

    static long longLoop(int n) {
        long sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i % 13;
        }
        return sum;
    }

    public static void main(String[] args) {
        long checksum = 0;
        int repeats = 80;

        for (int j = 0; j < repeats; j++) {
            long result = score(1_000_000);
            checksum += result;
            lastValue = result;
        }

        long osrCandidate = longLoop(1_300_000);

        System.out.println("score: " + lastValue);
        System.out.println("iterations: " + repeats);
        System.out.println("checksum: " + checksum);
        System.out.println("long loop: " + osrCandidate);
        System.out.println("done");
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac -encoding UTF-8 TieredCompilationDemo.java
java TieredCompilationDemo
```

**Standard output chính xác khi chạy không bật compiler/GC logging:**

```text
score: 3500000
iterations: 80
checksum: 280000000
long loop: 7800000
done
```

### Giải thích vì sao kết quả cố định

- `i & 7` cho dãy tuần hoàn `0,1,2,3,4,5,6,7`, tổng **28** mỗi 8 giá trị.
- `1_000_000 / 8 = 125_000` chu kỳ nên mỗi `score(1_000_000) = 125_000 × 28 = 3_500_000`.
- Lặp `80` lần và cộng dồn nên `checksum = 80 × 3_500_000 = 280_000_000`.
- `i % 13` cho một chu kỳ từ 0 đến 12, tổng **78**. `1_300_000 / 13 = 100_000` chu kỳ nên `longLoop = 7_800_000`.
- Dùng `long` cho tổng để tránh hiểu nhầm overflow khi bài tập thay số lần lặp.
- `lastValue` là `volatile`: việc ghi/đọc giá trị được thiết kế có hiệu ứng observable, ngăn suy diễn source loop chắc chắn bị loại bỏ hoàn toàn; dù vậy VM vẫn được tối ưu các đường thực thi bảo toàn semantics.

**Không có guarantee JIT:** loop nhiều iterations tạo **cơ hội** cho profiling/OSR, không bảo đảm level hay số compilation events cố định trên mọi CPU/JVM/config.

## 13. Quan sát level của method bằng `PrintCompilation`

Trên HotSpot:

```bash
java -XX:+PrintCompilation TieredCompilationDemo
```

Ngoài năm dòng output của chương trình, JVM có thể in nhiều compiler events cho cả **methods của JDK libraries**. Một log có thể có dạng:

```text
...  3   TieredCompilationDemo::score (N bytes)
...  4   TieredCompilationDemo::score (N bytes)
...  %   4   TieredCompilationDemo::longLoop @ BCI (N bytes)
```

Đây là **mẫu cấu trúc**, không phải log được đo và không phải những dòng bắt buộc xuất hiện. `N bytes` là kích thước bytecode/method được VM báo, còn **BCI** là bytecode index ở OSR entry. Output thực tế có compile ID, timestamp, flags và thông tin version-specific khác.

### Cách đọc đúng

- **Compile ID:** định danh sự kiện compilation trong VM process.
- **Level** như 1,2,3,4: cấp compilation theo tiered policy (khi hiện cột đó).
- **Method name:** `Class::method` được biên dịch hoặc liên quan sự kiện.
- **`%`:** thường báo một sự kiện **OSR compilation**.
- **`made not entrant`:** version compiled code không còn nhận lời gọi mới trong tình huống được đánh dấu.
- **`made zombie`:** trạng thái nội bộ của compiled code không còn được dùng theo vòng đời VM.

**Không suy luận mọi method đều phải in 3 rồi 4.** Nếu interpreter hoặc C1 policy khác, quá trình kết thúc trước khi C2 biên dịch, compile queue chưa tiêu hoặc JVM đã tối ưu theo cách khác, log có thể thiếu level 4.

### Thử các chế độ khác nhau

```bash
java -Xint TieredCompilationDemo
java -XX:TieredStopAtLevel=1 -XX:+PrintCompilation TieredCompilationDemo
java -XX:TieredStopAtLevel=3 -XX:+PrintCompilation TieredCompilationDemo
java -XX:+PrintCompilation TieredCompilationDemo
java -XX:-TieredCompilation -XX:+PrintCompilation TieredCompilationDemo
```

Những lệnh này chạy **năm JVM processes riêng**. **Kết quả tính toán của chương trình phải giữ nguyên** ở tất cả các lệnh được runtime hỗ trợ; nhưng compilation logs và thời gian chạy khác nhau.

**Mục tiêu của bài tập** là nhìn thấy **đường execution thay đổi**, chứ không khẳng định level 1 phải có đúng X events hay level 4 nhất định xuất hiện khi chạy một lần ngắn.

## 14. `javap` chỉ hiển thị bytecode, không hiển thị C1/C2 output

Lệnh:

```bash
javap -c -p TieredCompilationDemo
```

cho các opcode bytecode như `iload`, `iand`, `i2l`, `ladd`, `iinc`, conditional branches, `lreturn`, và lời gọi `invokestatic` phù hợp. `javap` đọc class file, **không chụp code cache** của JVM đang chạy.

### Nếu muốn xem native assembly do JIT sinh

HotSpot có diagnostic mechanisms như:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly TieredCompilationDemo
```

Nhưng `PrintAssembly` thường yêu cầu thư viện **hsdis** đúng OS/architecture/JDK build, và output có thể rất lớn hoặc không có assembly nếu bộ disassembler chưa sẵn. **Không coi việc không thấy assembly là bằng chứng C2 không hoạt động.** Dùng `PrintCompilation` hoặc công cụ profiler trước khi đi vào assembly.

Một số build/tooling có thể dùng `CompileCommand=print` để giới hạn methods, nhưng cú pháp và requirements phải kiểm tra trên đúng HotSpot version; đừng copy một cấu hình dài vào production khi chưa thử.

## 15. Quan sát compile queues, code cache bằng `jcmd`

Trên JDK/HotSpot hỗ trợ tool `jcmd`, một JVM process đang chạy có thể cung cấp chẩn đoán JIT:

```bash
jcmd -l
jcmd PID help
jcmd PID Compiler.queue
jcmd PID Compiler.codelist
jcmd PID Compiler.codecache
```

`PID` là **placeholder**, phải thay bằng mã số tiến trình thực. Đừng gõ nguyên dấu `<PID>` trong shell vì nó có thể bị hiểu là cú pháp redirect.

| Lệnh | Cách dùng |
| --- | --- |
| `Compiler.queue` | Quan sát những methods đang chờ compile |
| `Compiler.codelist` | Liệt kê nhiều compiled methods/code đang sống trong VM |
| `Compiler.codecache` | Thống kê bố trí/dung lượng code cache |
| `VM.flags` | Kiểm tra effective flags của JVM |
| `help` | Xác minh một diagnostic command có được build hỗ trợ |

**Lưu ý:** chương trình `TieredCompilationDemo` có thể chạy quá nhanh để bạn gõ `jcmd` đúng lúc. Dùng một process test lâu sống hoặc service đang vận hành với quyền truy cập cho phép.

### Code cache và compile queue quá tải khác nhau

- **C2 queue dài:** C2 chưa xử lý hết compilation requests; JVM có thể tiếp tục chạy interpreter/C1 code.
- **Code cache đầy:** không còn chỗ phù hợp cho code blobs mới, compilation có thể bị hạn chế.
- **Profile chưa đủ:** policy chưa gửi method tới compile hoặc chưa chọn level 4.
- **Method bị loại khỏi compilation:** có compiler exclusion/limit/failure, hoặc mode `-Xint`.
- **CPU bị throttle:** compiler threads khó nhận CPU, khiến warm-up lâu.

Không chỉ thấy throughput thấp là lập tức tăng code cache. Phải đo **queue lengths, effective flags, code cache use, CPU throttling và load**.

## 16. Compilation MXBean và công cụ monitoring

Java Management API có `java.lang.management.CompilationMXBean`, **nếu JVM expose**. Nó có thể báo tên JIT compiler và `totalCompilationTime`, nhưng không cung cấp một standardized log của mọi compilation level:

```java
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;

public class CompilerInfoDemo {
    public static void main(String[] args) {
        CompilationMXBean bean =
                ManagementFactory.getCompilationMXBean();

        if (bean == null) {
            System.out.println("compiler bean: unavailable");
            return;
        }

        System.out.println("compiler: " + bean.getName());
        if (bean.isCompilationTimeMonitoringSupported()) {
            System.out.println("time(ms): "
                    + bean.getTotalCompilationTime());
        } else {
            System.out.println("time(ms): unsupported");
        }
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 CompilerInfoDemo.java
java CompilerInfoDemo
```

**Output có cấu trúc rõ nhưng giá trị biến động:** compiler name và compilation time phụ thuộc JVM/mode, có thể `compiler bean: unavailable` hoặc `time(ms): unsupported`. Không đưa một con số ms cố định, vì thời điểm khởi tạo VM, workload và JIT threads khác nhau.

### Vì sao JMX không thay compiler log?

`CompilationMXBean` giúp biết tình trạng JIT tổng quát, nhưng **không** trực tiếp chứng minh method `score` đã lên level 4. Muốn phân tích từng method, dùng `PrintCompilation`, JIT logs, JFR compiler events hoặc công cụ profiler được hỗ trợ; muốn phân tích machine instructions phải dùng công cụ decode đúng VM.

## 17. Các đánh đổi về hiệu năng

### Startup vs steady state

Một ứng dụng CLI 2 giây có thể **kết thúc trước khi hưởng lợi nhiều từ C2**. Dành CPU cho profile/compile method mà không được tái dùng đủ lần có thể làm startup hoặc tổng thời gian kém hơn. Một server chạy nhiều ngày với hot methods ổn định có thể có lợi đáng kể từ C2.

**Tiered compilation tồn tại để tìm điểm cân bằng**, không phải vì C2 luôn “mạnh nhất” mà lúc nào cũng nên bật chế độ compile tất cả methods ngay lập tức.

### Warm-up và benchmark sai

Nếu đo:

```java
long start = System.nanoTime();
for (int i = 0; i < 100; i++) {
    targetMethod();
}
long elapsed = System.nanoTime() - start;
```

Đây là **pseudocode Java** có method `targetMethod()` chưa được định nghĩa. Kết quả dễ bị nhiễu bởi class loading, interpreter, C1/C2 transitions, code cache, GC, CPU scheduling và dead-code elimination. Không dùng phép đo một lần để chọn flags.

Với microbenchmark, nên dùng **JMH**, warm-up đủ, nhiều forks, tránh constant folding/dead-code elimination và quan sát JVM flags. Nhưng **cũng không nên warm-up vô hạn** nếu chính startup latency là thứ bạn cần đo. Hãy định nghĩa mục tiêu trước.

### Khi nào cân nhắc `TieredStopAtLevel=1`?

Trên một số serverless/short-lived workloads, giới hạn C1 có thể làm giảm compilation overhead, cải thiện readiness hoặc tiết kiệm code cache. Nhưng steady-state throughput có thể giảm. Không chọn theo cảm giác; đo request latency/warm-up/CPU với application thực.

### Khi nào có thể cân nhắc tắt tiered?

Chỉ khi có lý do benchmark/tuning cụ thể và đã chứng minh trade-off. `-XX:-TieredCompilation` có thể làm compiler policy dùng đường khác (không phải `-Xint`). Đừng cố dùng nó để “tắt JIT” khi điều cần kiểm tra là interpreter-only.

## 18. Troubleshooting các trường hợp thường gặp

| Triệu chứng | Khả năng | Kiểm tra |
| --- | --- | --- |
| Method nóng không lên level 4 | Profile chưa đủ, C2 queue, exclusions, stop level, C2 không hoạt động, code cache | `PrintCompilation`, `Compiler.queue`, effective flags |
| Log chỉ có level 1 | `TieredStopAtLevel=1` hoặc policy chọn C1 no-profiling | `VM.flags` / startup command |
| Không có JIT events | `-Xint`, JVM/tooling không hỗ trợ log, workload quá ngắn | VM mode, logger, lệnh chạy |
| Throughput tăng dần sau startup | JVM warm-up/profile/JIT; cũng có thể cache/app effects | JIT logs, metrics, warm-up phase |
| CPU tăng lúc khởi động | JIT compilation, class loading, GC hoặc app initialization | CPU profiles, compiler events |
| Thời gian compile quá lớn | C2 queue/compilation cost, quá nhiều hot methods | CompilationMXBean, JFR, method-level logs |
| Code cache warning | Native code region cạn capacity/fragmentation | `Compiler.codecache`, flags, code cache logs |
| Method `made not entrant` | Version compiled code bị thay/không còn thích hợp | Deopt/recompilation, class loading |
| `PrintAssembly` không hiển thị assembly | Không có hsdis hoặc thiếu diagnostic setup | JDK architecture/build và hsdis |
| Cùng source có compile level khác | Profile, inputs, CPU quota, JDK build, timing | So môi trường/lần chạy |
| Long loop không thấy OSR | Loop chưa đủ hot theo policy hoặc mode không hỗ trợ | Backedge/workload, `PrintCompilation` |
| `-XX:-TieredCompilation` vẫn JIT | Đó là thay policy, không phải interpreter-only | Dùng `-Xint` khi muốn tắt JIT |

### JVM versions và distributions

Các mức và flags mô tả ở đây nhằm minh họa **HotSpot JDK 17**. Những bản HotSpot mới có thể thay đổi policy, code cache, compiler internals, hoặc bổ sung AOT mechanisms. Một số JVM có compiler khác như **Graal compiler** tại level tối ưu trong cấu hình phù hợp. **OpenJ9** cũng có JIT nhưng không áp trực tiếp năm mức C1/C2 HotSpot này.

Nếu một flag báo không nhận diện, đừng tự kết luận bản Java hỏng: cần xem implementation, JDK version và release docs của binary đó.

## 19. Bảng phân biệt các khái niệm dễ nhầm

| Nhầm lẫn | Thực tế |
| --- | --- |
| “`javac` chính là C1 hoặc C2.” | `javac` biên dịch source thành bytecode trước khi chạy; C1/C2 tạo native code khi runtime |
| “JVM luôn thông dịch, không bao giờ chạy native code.” | HotSpot có interpreter và JIT compiled native code |
| “C1 chỉ chạy trên Client VM, C2 chỉ trên Server VM.” | HotSpot tiered mode có thể dùng cả hai trong Server VM |
| “Mọi method phải đi level 0→1→2→3→4.” | Policy chọn đường khác tùy profile và queue |
| “Level 1 nghĩa là C1 không tối ưu.” | Có C1 optimizations nhưng không full profiling instrumentation |
| “Level 3 luôn nhanh hơn level 2.” | Profiling có runtime cost, phụ thuộc method |
| “Level 4 code sẽ không bao giờ thay đổi.” | Deopt, recompilation, unloading/code sweeping có thể xảy ra |
| “Gọi method nhiều lần là điều kiện duy nhất của hotness.” | Backedge counters cho loop dài cũng quan trọng |
| “Một method chỉ có một compiled version.” | C1/C2/OSR/recompile có thể sinh nhiều code versions |
| “C2 tối ưu sâu hơn nên mọi chương trình khởi động nhanh hơn.” | C2 compile cost/profile có thể làm startup chậm |
| “`javap -c` là native assembly sau C2.” | Đó là JVM bytecode trong class file |
| “`-XX:-TieredCompilation` tắt JIT.” | Không nhất thiết; `-Xint` mới là interpreter-only |
| “`TieredStopAtLevel=1` đảm bảo mọi method có C1 code.” | Cold methods có thể mãi interpreted |
| “C2 queue rỗng nghĩa là mọi hot method đã tối ưu.” | Có thể compilation bị vô hiệu, profile chưa đủ hoặc stop level |
| “`PrintCompilation` event count luôn xác định.” | Phụ thuộc JDK, CPU, workload, timing và compiler state |
| “HotSpot tiered levels là tính năng chuẩn của mọi JVM.” | Đó là implementation detail |
| “Code cache là Java heap.” | Mã native nằm trong vùng HotSpot code cache, ngoài heap objects |
| “Deoptimization chứng minh compiler sai.” | Đó là cơ chế rút lui hợp lệ khỏi speculative assumption |

## 20. Bài tập có đáp án

**Bài 1.** HotSpot level 0 là gì?  
**Đáp án:** Interpreter; JVM có thể thực thi bytecode trước khi method được JIT compile.

**Bài 2.** C1 thường tạo compiled code ở levels nào?  
**Đáp án:** 1, 2, 3; khác nhau về mức profiling.

**Bài 3.** C2 thường tương ứng level nào trong mô hình tiered HotSpot?  
**Đáp án:** Level 4, hướng tới full optimization.

**Bài 4.** Level 3 có phải “tối ưu sâu nhất” vì số 3 lớn hơn 2?  
**Đáp án:** Không. Nó là C1 với profiling đầy đủ; level 4 mới là C2 tối ưu sâu hơn.

**Bài 5.** Vì sao level 2 có thể hữu ích khi C2 queue dài?  
**Đáp án:** Nó tránh một phần profiling overhead của level 3 trong thời gian chờ, vẫn theo dõi invocation/backedge counters.

**Bài 6.** Một method được gọi đúng một lần nhưng loop rất dài có thể được JIT khi còn đang chạy?  
**Đáp án:** Có thể, qua OSR nếu backedges và policy phù hợp.

**Bài 7.** `PrintCompilation` có dấu `%` thường báo gì?  
**Đáp án:** Một OSR compilation event trong format HotSpot thường gặp.

**Bài 8.** Một optimized call site từng chỉ thấy một type, sau đó thấy type khác, có thể xảy ra gì?  
**Đáp án:** Deoptimization, profile update và recompilation; chương trình vẫn phải giữ đúng semantics.

**Bài 9.** `-XX:TieredStopAtLevel=1` có bắt JVM compile mọi method không?  
**Đáp án:** Không; nó giới hạn mức nếu compile, còn cold methods có thể interpreted.

**Bài 10.** Tắt tiered bằng `-XX:-TieredCompilation` có đồng nghĩa tắt JIT?  
**Đáp án:** Không; dùng `-Xint` khi muốn interpreter-only.

**Bài 11.** `javap -c` có cho biết C2 đã inline method chưa?  
**Đáp án:** Không. Nó hiển thị bytecode; cần JIT logs/profiler/native code diagnostics.

**Bài 12.** Vì sao level 4 không phải code vĩnh viễn?  
**Đáp án:** Assumptions có thể thay đổi, deoptimization hoặc compiled-code lifecycle khiến VM thay version.

**Bài 13.** C2 queue dài có thể gây gì?  
**Đáp án:** Warm-up lâu hơn, method tiếp tục chạy interpreter/C1 trong khi chờ C2.

**Bài 14.** `TieredCompilationDemo.score(1_000_000)` trả bao nhiêu?  
**Đáp án:** 3.500.000 vì `i & 7` có chu kỳ 8 với tổng 28.

**Bài 15.** `TieredCompilationDemo` in tổng checksum bao nhiêu với 80 lần lặp?  
**Đáp án:** 280.000.000.

**Bài 16.** `longLoop(1_300_000)` có tổng bao nhiêu khi cộng `i % 13`?  
**Đáp án:** 7.800.000.

**Bài 17.** Nếu một app CLI kết thúc trong hai giây, ép JIT sớm nhất có luôn lợi?  
**Đáp án:** Không. Compilation cost có thể lớn hơn lợi ích nếu code chưa tái dùng đủ.

**Bài 18.** Nếu code cache đầy, có phải Java heap thiếu chỗ?  
**Đáp án:** Không nhất thiết. Code cache là vùng native riêng, dù cả hai cộng vào resource footprint.

**Bài 19.** Dùng gì để kiểm tra compile events của một method trong HotSpot JDK 17?  
**Đáp án:** `-XX:+PrintCompilation`, log compilation/JFR và các công cụ diagnostic phù hợp; đối chiếu flags và workload.

**Bài 20.** Có thể áp sơ đồ HotSpot 0–4 trực tiếp sang OpenJ9 không?  
**Đáp án:** Không. Đây là cách tổ chức tiered JIT của HotSpot, không phải JVM-spec requirement.

## 21. Checklist khi điều tra JIT warm-up

- [ ] Đã xác nhận vendor, JVM implementation, version và architecture?
- [ ] Đã xác minh `TieredCompilation`, `TieredStopAtLevel` và effective flags?
- [ ] Đã phân biệt interpreter-only (`-Xint`) với tiered disabled?
- [ ] Có quan sát compile events theo **method + level + OSR status**?
- [ ] Có đo C1/C2 compile queues, code cache và compiler threads khi cần?
- [ ] Có warm-up và đo nhiều lần để giảm nhiễu?
- [ ] Có phân biệt startup latency với steady-state throughput?
- [ ] Có xem khả năng inlining, profile và deoptimization khi performance đổi bất thường?
- [ ] Có tránh kết luận mỗi method phải tới level 4?
- [ ] Có phân biệt Java bytecode và machine code trong HotSpot code cache?
- [ ] Có tránh cấu hình tier threshold/compilers tùy tiện trước khi có baseline?
- [ ] Có chú ý CPU quota, GC, I/O, class loading và memory footprint?
- [ ] Có lưu lại runtime flags trong báo cáo benchmark?
- [ ] Có kiểm tra code sau thay đổi vẫn cho output đúng như Java semantics?
- [ ] Có xem JDK release notes khi một flag/log format thay đổi?

## 22. Tổng kết và tài liệu đối chiếu

Mô hình mental model hữu ích:

```text
INTERPRETER (level 0)
  │ chạy bytecode + nhận biết độ nóng
  ▼
C1 (levels 1–3)
  │ compiled code nhanh, profiling theo mức
  ▼
C2 (level 4)
  │ dùng runtime profile để tối ưu sâu
  ▼
CODE CACHE giữ compiled machine code
  │
  └─ deopt/OSR/recompile khi runtime cần

Không phải mọi method đều phải đi đủ các ô trong sơ đồ.
```

**C1** tạo compiled code tương đối sớm, cung cấp nhiều modes thu thập profiling; **C2** dùng profile để tối ưu sâu những đoạn đáng đầu tư. **Tiered policy** quyết định **khi nào, ở cấp nào và có đáng compile hay không** dựa trên invocation/backedge counters, call/type profiles, compiler queues và chi phí biên dịch. **OSR** giải quyết loops nóng trong method đang thực thi; **deoptimization** duy trì correctness khi speculative assumptions thay đổi; **code cache** quản lý nhiều compiled nmethods qua vòng đời JVM.

### Tài liệu tham khảo đáng tin

1. **Microsoft for Java Developers — How Tiered Compilation Works in OpenJDK**, trình bày levels 0–4 và C1/C2: https://devblogs.microsoft.com/java/how-tiered-compilation-works-in-openjdk/
2. **OpenJDK HotSpot source — compilationPolicy.hpp** (tiered policy, queues, threshold decisions): https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compilationPolicy.hpp
3. **OpenJDK HotSpot JDK 17 source** (để đối chiếu cùng release): https://github.com/openjdk/jdk/tree/jdk-17%2B35/src/hotspot/share/compiler
4. **Oracle JDK 17 — The java Command** (flags `TieredCompilation`, `PrintCompilation`, `PrintInlining`, code cache): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
5. **Oracle JDK 17 — jcmd** (Compiler.queue, Compiler.codelist, Compiler.codecache): https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
6. **OpenJDK JEP 197 — Segmented Code Cache** (profiled vs non-profiled nmethods): https://openjdk.org/jeps/197/
7. **Java SE 17 — CompilationMXBean**: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/CompilationMXBean.html

**Thông điệp chính:** Biên dịch phân tầng không đơn thuần “C1 nhanh, C2 chậm” mà là cơ chế **đầu tư compiler resources theo mức độ nóng và profile runtime**. Khi tối ưu Java, hãy đo mục tiêu cụ thể — startup, warm-up, throughput, latency và compile overhead — thay vì coi một compilation level hay một set flags là tối ưu trong mọi môi trường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-005">Biên dịch phương thức và thay thế khi đang chạy vòng lặp (OSR)</a>

<details>
<summary>Xem chi tiết</summary>

# Biên dịch phương thức và thay thế khi đang chạy vòng lặp (OSR) trong HotSpot JVM

## Mục tiêu

Một chương trình Java 17+ không nhất thiết thực thi mã bytecode bằng cùng một cơ chế trong suốt vòng đời. **HotSpot** có thể bắt đầu bằng **interpreter**, thu thập thông tin hành vi khi chạy rồi **JIT-compile** những phần mã đủ “nóng” thành machine code. Với phương thức thường được gọi đi gọi lại, JIT có thể tạo mã **bắt đầu từ method entry** để những lần gọi sau đi vào native code ngay. Nhưng một phương thức **chỉ được gọi một lần mà thực thi một vòng lặp rất dài** vẫn có thể tốn hàng triệu lần lặp trong interpreter. **On-Stack Replacement (OSR)** giải quyết chính tình huống đó: chuyển **lần gọi đang hoạt động** từ phiên bản interpreter hoặc compiled tier thấp hơn sang một phiên bản compiled có **điểm vào ở bên trong phương thức**, thường tại đầu vòng lặp (*loop header/backedge target*).

Sau bài này, bạn sẽ có thể:

- Phân biệt **normal method compilation** với **OSR compilation**, và biết vì sao một method có thể có **nhiều nmethods** hoặc mã ở nhiều compilation levels.
- Giải thích **method invocation counter**, **backedge counter**, **profiling**, **compile queue**, **C1**, **C2**, **tiered compilation** và **nmethod** theo ngữ cảnh HotSpot.
- Đọc ký hiệu **`%`**, **`@ bci`**, compilation level và **made not entrant** trong `-XX:+PrintCompilation` mà không coi log là kết quả chạy bất biến.
- Hiểu chuyển trạng thái giữa **interpreter frame / compiled frame**, các **local variables**, operand stack, stack maps và safepoints, cùng giới hạn của việc mô phỏng ở Java source.
- Phân biệt OSR với **deoptimization**, **inlining**, **uncommon trap**, **OSR bci** và **GC safepoint**.
- Viết Java 17+ chạy được để nghiên cứu sự khác nhau giữa một vòng lặp dài và một phương thức được gọi nhiều lần.
- Biết sử dụng `javap`, `PrintCompilation`, `LogCompilation`, `jcmd`, JFR và các tham số như `-Xint`, `-XX:TieredStopAtLevel`, không “ép” OSR bằng một ngưỡng cố định thiếu cơ sở.
- Đánh giá warmup, code-cache pressure, deoptimization và benchmark pitfalls bằng dữ liệu thực tế.

## 1. Interpreter, JIT và nmethod

Java source được `javac` chuyển thành **class file bytecode**. Khi JVM thực thi:
1. Class loading/linking/initialization xử lý class theo quy tắc.
2. Method bắt đầu thực thi trong **interpreter** hoặc vào mã compiled đã sẵn có, tùy trạng thái HotSpot.
3. Runtime quan sát những phương thức hoặc loops thực thi nhiều, tích lũy counters và profile data.
4. **Compilation policy** quyết định có gửi một yêu cầu biên dịch tới **compile queue**.
5. Compiler thread sử dụng thông tin kiểu, call sites, branches, loop behavior và runtime assumptions thích hợp để tạo machine code.
6. Nếu mã compiled được cài đặt thành công, JVM có thể điều hướng những lần thực thi thích hợp vào nó.

**`nmethod`** là khái niệm nội bộ HotSpot cho một khối mã native đã biên dịch gắn với method và metadata như entry points, scope/deoptimization information và runtime dependencies. Nó không phải một method Java source mới.

Sơ đồ:

~~~text
.java -> javac -> .class / bytecode
                         |
                         v
                    HotSpot JVM
                         |
            +------------+-------------+
            |                          |
       INTERPRETER                COMPILED CODE
       bytecode dispatch        C1/C2 nmethod(s)
            |                          ^
            +-- profiling/counters ----+
                         |
                  compile queue
                         |
                  compiler threads
~~~

**Phân biệt:** Bytecode trong .class file không bị “ghi đè” bởi assembly khi JIT hoạt động. JVM quản lý native compiled code riêng, vẫn cần metadata bytecode cho verification, stack traces, deoptimization và những mục đích khác.

## 2. Normal method compilation là gì?

Giả sử:

~~~java
static int plusOne(int value) {
    return value + 1;
}
~~~

Đây là **đoạn method minh họa**, phải đặt trong một class để biên dịch. Với một vòng ngoài gọi `plusOne` liên tục, HotSpot có thể thấy invocation count cao và biên dịch mã có **entry ở đầu method**.

~~~text
Caller gọi plusOne() nhiều lần:
  call #1    -> interpreter
  call #2    -> interpreter
  ...
  HotSpot nhận diện hot method
            |
            v
      compile plusOne
      entry BCI chuẩn / method entry
            |
  call #K+1 -> compiled nmethod
  call #K+2 -> compiled nmethod
~~~

**Đây là trình tự minh họa**. Số K không có giá trị cố định và JIT có thể inline lời gọi, sử dụng tier khác hoặc bị giới hạn bởi flags/code cache. Không có guarantee một method “được gọi đúng 10.000 lần là biên dịch”.

### Điểm vào method entry

Normal compilation tạo mã có thể dùng khi **bắt đầu một lần gọi mới**. Caller chuyển tới compiled entry theo runtime dispatch/inlining decisions. Nếu một lần gọi đã vào interpreter và còn chạy bên trong vòng lặp trong thời gian dài, chỉ có compiled method-entry code **không đủ** để tự chuyển lần gọi đó sang native code mà không có cơ chế thích hợp.

### JIT không bắt buộc biên dịch mọi method

Method hiếm khi gọi, quá lớn, không đủ profile, có restrictions hoặc chỉ thực hiện rất ít công việc có thể luôn chạy interpreted. Một số method được inline vào caller, khiến chúng không xuất hiện như một compiled method riêng theo kỳ vọng. Compilation có thể bị trì hoãn khi compiler threads bận hoặc code cache căng.

## 3. Vấn đề đặc biệt của một vòng lặp chạy rất lâu

Xét:

~~~java
static long oneLongCall(int limit) {
    long sum = 0L;
    for (int i = 0; i < limit; i++) {
        sum += (i & 7);
    }
    return sum;
}
~~~

Đây là snippet minh họa. Phương thức có thể chỉ được gọi **một lần**, nhưng `limit` có thể đạt hàng chục triệu hoặc hàng trăm triệu iterations. Invocation count không cao, song **loop backedges** (các nhánh quay về đầu vòng lặp) xảy ra liên tục.

Nếu chỉ dựa trên phương thức được gọi lặp lại, HotSpot có thể không biên dịch kịp một method chỉ được gọi một lần. Nếu biên dịch “thông thường”, mã mới ở method entry sẽ chỉ được dùng khi gọi lại từ đầu, **không giúp phần lớn vòng lặp đang thực thi**.

**OSR cho phép chuyển chính lần gọi hiện tại sang compiled code ở một BCI bên trong method.** Đây là điểm khác biệt cốt lõi với normal compilation.

### Ví dụ thời gian

~~~text
T0   Method bắt đầu trong interpreter
T1   Loop chạy 100.000 lần; backedge counter tăng
T2   JVM quyết định gửi OSR compilation request
T3   Compile thread tạo OSR nmethod (có thể chạy song song)
T4   Method vẫn đang thực thi trong interpreter
T5   Khi có điểm chuyển thích hợp và OSR code đã sẵn:
       interpreter frame/state
                |
                v
       OSR entry trong compiled nmethod
       tiếp tục từ loop BCI hiện tại
T6   Loop và method hoàn tất; caller nhận return value
~~~

Các mốc T chỉ là **mô tả logic**. JVM không bảo đảm request được gửi sau đúng 100.000 iterations, compilation luôn kết thúc trước khi loop xong, hay OSR luôn được chọn.

## 4. BCI và tại sao output có dấu `@`

**BCI (bytecode index)** là vị trí offset tính theo byte trong vùng bytecode của một method. Lệnh ở offset đó là một phần của **một method**, không phải line number trong Java source.

Khi HotSpot OSR-compile một method, compilation task có **OSR entry BCI** xác định nơi compiled code có thể nhận một **activation đang chạy**. Trong các loop thông dụng, nó thường tương ứng một điểm tại loop header hoặc vị trí backedge target.

Ví dụ output minh họa của `-XX:+PrintCompilation`:

~~~text
    75   33 %     3       OsrDemo::longLoop @ 4 (37 bytes)
   111   55       4       OsrDemo::longLoop (37 bytes)
   145   33 %     3       OsrDemo::longLoop @ 4 (37 bytes)   made not entrant
~~~

**Các số millis, compile ID, level, BCI và số byte ở đây hoàn toàn minh họa**, không phải log đúng cho một chương trình cụ thể. Trong định dạng HotSpot thông thường:
- Con số đầu thường là **thời gian từ JVM startup** (ms), tùy format.
- Một số tiếp theo là **compilation ID**.
- **`%`** đánh dấu **OSR compilation**.
- Số như **3** hoặc **4** có thể là compilation level trong tiered policy.
- `OsrDemo::longLoop` là tên class/method.
- **`@ 4`** là **OSR BCI**, không phải số iteration.
- `(37 bytes)` nói đến độ dài **bytecode method**, không phải bytes của machine code.
- **`made not entrant`** là trạng thái một nmethod không còn được lựa chọn cho những entry mới, thường do một bản compiled khác hoặc invalidation.

Không suy ra từ một dòng `%` rằng OSR **thực sự đã được sử dụng** cho mọi invocation đang chạy: dòng này chủ yếu xác nhận **có compilation request/compilation tương ứng**. Mã có thể sẵn nhưng loop đã kết thúc trước thời điểm chuyển hoặc tier khác được chọn.

### OSR BCI khác Java line

Một lệnh Java `for` có thể sinh nhiều bytecodes: khởi tạo index, so sánh limit, nhảy có điều kiện, tải locals, toán tử arithmetic, tăng index, `goto` về đầu. Vì vậy BCI=4 không có nghĩa “dòng 4”. Dùng `javap -c -p` để xem offsets bytecode và lệnh branch.

## 5. Backedge counter và compilation policy

Một vòng lặp bytecode thường có nhánh ngược về offset nhỏ hơn. JVM có thể sử dụng **backedge counts** và profile information để xác định vòng lặp “nóng” dù method invocation count thấp.

Hai tín hiệu khác nhau:

| Tín hiệu | Thường tăng khi nào | Trường hợp có giá trị |
| --- | --- | --- |
| **Invocation count** | Gọi một method nhiều lần | Method ngắn, thường được gọi |
| **Backedge count** | Nhánh quay lại vùng code trước đó nhiều lần | Một hoặc vài lần gọi có loop rất dài |
| **Branch/profile data** | Runtime quan sát tỷ lệ nhánh, call receivers, types... | C1 profiling và tối ưu theo dữ liệu thực |
| **Hotness/rate heuristics** | JVM kết hợp counts, tốc độ, compile-queue load... | Lựa tier và thời điểm compile |

**Cẩn thận:** đây không phải hai đồng hồ đơn giản độc lập với thresholds cố định. HotSpot tiered policy thay đổi theo version, compiler queue, profile maturity và số active methods. Các flags như `CompileThreshold` hoặc `OnStackReplacePercentage` có ý nghĩa phụ thuộc chế độ tiered/non-tiered; dùng một công thức threshold cũ của Java 8 để dự đoán Java 17+ là sai.

### Compilation có thể chạy bất đồng bộ

Khi interpreter phát hiện hot backedge, nó có thể gửi compile task rồi **tiếp tục chạy loop trong interpreter** trong lúc compiler thread làm việc. Nếu loop kết thúc trước khi OSR code sẵn, chương trình không được chuyển sang OSR ở lần đó. Cũng có các chế độ **blocking compilation** hoặc các tình huống policy khác; đừng mặc định mọi JIT compilation luôn background và không bao giờ chặn application thread.

### OSR có nhất thiết xảy ra tại mỗi backedge?

Không. Runtime phải có một checkpoint/safepoint hoặc cơ chế kiểm tra thích hợp để nhận biết code đã sẵn, bảo đảm frame transition hợp lệ. Nó không phải phép “đổi địa chỉ lệnh CPU ngay giữa một expression Java” tùy tiện. JIT cần chuyển trạng thái tại nơi có đủ metadata để khôi phục semantics.

## 6. Tiered compilation: interpreter, C1 và C2

HotSpot server JVM thông dụng triển khai **tiered compilation** để cân bằng thời gian warmup và hiệu quả machine code. Một mô hình các levels thường gặp:

| Level | Hình thức | Vai trò |
| --- | --- | --- |
| **0** | Interpreter | Bắt đầu thực thi và/hoặc thu profile theo policy |
| **1** | C1 compiled, tối ưu không bổ sung profiling nhiều như tiers sau | Method có đường đi được lựa chọn phù hợp |
| **2** | C1 compiled với profiling giới hạn | Thu thập một phần dữ liệu hỗ trợ tier tiếp |
| **3** | C1 compiled với profiling đầy đủ hơn | Phổ biến trên đường tiến lên optimized tier |
| **4** | C2 compiled, tối ưu mạnh theo profile | Tối ưu steady-state throughput/latency khi thích hợp |

**Đây là diễn giải HotSpot C1/C2 truyền thống**, không phải cấu trúc bắt buộc của JVMS hoặc mọi build/platform. Một số JVM có JVMCI compiler, tuning options và tier policy khác.

Trong thực tế:
- Một method có thể chạy ở level 0 rồi sang C1 level 3, sau đó C2 level 4.
- Một method rất đơn giản có thể vào level 1.
- JVM có thể skip level, dừng ở C1, không compile hoặc recompile khi assumptions thay đổi.
- Một method có thể có **normal nmethod** và **OSR nmethod**; cả hai có thể khác levels.
- Những dấu level trong PrintCompilation chỉ phản ánh compilation task/nmethod, không phải “mọi bytecode lập tức ở level đó” trên mọi invocation.

### C1 và C2 không phải hai phiên bản Java source

C1/C2 là **compilers nội bộ VM**. Mục tiêu của JVM vẫn là thực thi đúng Java semantics. C2 có thể inline methods, eliminate allocations, hoist invariant computations hoặc vectorize loops nếu điều kiện phù hợp. Những tối ưu có thể phải được vô hiệu hóa khi runtime observations không còn đúng.

### C1 OSR và C2 OSR có thể tồn tại đồng thời

Một vòng lặp rất dài có thể:
1. Bắt đầu interpreted.
2. Được OSR-compile ở C1 để sớm nhận native execution.
3. Tiếp tục được quan sát và gửi C2 OSR compilation khi đủ profile.
4. Chuyển tới mã tier cao hơn theo đường VM cho phép.

Không suy ra việc chuyển luôn trực tiếp từ C1 OSR frame sang C2 OSR frame. Có thể cần deoptimization/re-entry/intermediate state; chi tiết tùy HotSpot version và compiler/runtime policy.

## 7. So sánh method-entry compilation và OSR

| Khía cạnh | Normal compilation | OSR compilation |
| --- | --- | --- |
| **Mục tiêu** | Tăng tốc **những lần gọi mới** bắt đầu tại method entry | Tăng tốc **lần thực thi hiện tại** đã ở trong vòng lặp |
| **Tín hiệu điển hình** | Method invocations/hotness | Loop backedges/hotness |
| **Entry point** | Standard method entry | Một BCI bên trong method (thường loop header) |
| **Compile task** | Standard nmethod | OSR nmethod với osr_bci |
| **PrintCompilation** | Thường không có `%` và không có `@ bci` | Có `%`, thường có `@ bci` |
| **Activation được tối ưu** | Chủ yếu future invocations | Có thể current activation |
| **State chuyển đổi** | Caller truyền arguments tới normal entry | Runtime chuyển interpreter/compiled state của activation hiện hành |
| **Độ tối ưu** | Có thể dùng thông tin ở method entry | Có những ràng buộc vì phải chấp nhận state ở OSR entry |
| **Có thể có thêm bản compiled khác?** | Có, khi tiering/recompilation | Có, nhiều osr_bci hoặc levels |
| **Bảo đảm sẽ xảy ra?** | Không | Không |

**OSR không phải một Java syntax feature.** Source code `for`/`while` giống hệt; OSR là quyết định và cơ chế nội bộ JVM nhằm tối ưu execution mà không thay đổi kết quả chương trình.

## 8. Trạng thái đang chạy cần được chuyển như thế nào?

Một interpreted frame đang ở trong loop có trạng thái không thể “bỏ đi”:
- **Local variables**: tham số, loop index, counters, object references, temporaries.
- **Operand stack** tại bytecode state liên quan.
- **Java monitors/locks** nếu method hoặc block được synchronized và OSR entry phải bảo toàn trạng thái khóa.
- **Exception handlers** và thông tin control flow.
- **GC map/reference metadata** cần cho collector biết vị trí object references.
- Thông tin liên quan stack traces, inlining/deoptimization nếu chuyển tiếp từ compiled frames.

Ví dụ một loop:

~~~java
static long countEven(int n) {
    long sum = 0L;
    int accepted = 0;
    for (int i = 0; i < n; i++) {
        if ((i & 1) == 0) {
            sum += (i % 10);
            accepted++;
        }
    }
    return sum + accepted;
}
~~~

Đây là snippet minh họa. Nếu chuyển sang OSR ở giữa loop, compiled code cần tiếp tục với **giá trị hiện tại** của `i`, `sum`, `accepted` và `n`. Không được reset chúng về 0. Cũng không được lặp lại những side effects đã xảy ra trước điểm chuyển.

### Sơ đồ frame state

~~~text
Interpreter activation (đang tại loop backedge):
  locals: [n=8,000,000, i=3,450,123,
           sum=..., accepted=...]
  operand stack: trạng thái tại OSR checkpoint
  monitor state, metadata, method/BCI

                |
                | OSR runtime state transfer
                v

Compiled OSR entry @ loop BCI:
  reconstruct/register-map relevant locals,
  operand values, references and monitor state
  -> continue from current loop iteration
  -> preserve Java semantics
~~~

Các số giả lập. Trạng thái vật lý có thể nằm trong interpreter frame, compiled frame, register/spill slots hoặc buffers; HotSpot cần metadata để tái lập trạng thái đúng. **Không khẳng định OSR luôn “copy toàn bộ stack”** hay giữ mọi local variable trong RAM theo layout bất biến.

### OSR có thể bị từ chối

Các conditions có thể khiến một OSR request không được thực hiện: method/bci không phù hợp để compile tại level hiện tại, compiler busy, code cache constraints, insufficient profile, JVM flags và policy. Một method có loop lớn vẫn có thể chạy hoàn toàn trong interpreter.

## 9. Ví dụ đầy đủ Java 17+: một loop dài và nhiều lần gọi nhỏ

Tạo file **`OsrVsMethodDemo.java`**:

~~~java
public class OsrVsMethodDemo {
    static volatile long observable;

    static long longLoop(int limit) {
        long sum = 0L;
        for (int i = 0; i < limit; i++) {
            sum += (i & 7);
        }
        return sum;
    }

    static int plusOne(int value) {
        return value + 1;
    }

    public static void main(String[] args) {
        long loopResult = longLoop(16_000_000);
        observable = loopResult;

        long callSum = 0L;
        for (int i = 0; i < 100_000; i++) {
            callSum += plusOne(i);
        }
        observable = callSum;

        System.out.println("loopSum=" + loopResult);
        System.out.println("callSum=" + callSum);
    }
}
~~~

Biên dịch/chạy JDK 17+:

~~~bash
javac --release 17 OsrVsMethodDemo.java
java OsrVsMethodDemo
~~~

**Stdout nghiệp vụ chính xác:**

~~~text
loopSum=56000000
callSum=5000050000
~~~

Giải thích toán học:
- Dãy `i & 7` lặp lại 0,1,…,7, tổng mỗi nhóm 8 là **28**. 16.000.000 / 8 = 2.000.000 nhóm, tổng = **56.000.000**.
- `plusOne(i)` trả 1,2,…,100.000; tổng = `100000×100001/2 = 5.000.050.000`, chứa trong `long`.
- `observable` là volatile field giữ kết quả có ý nghĩa quan sát, nhưng không cam kết mọi intermediate allocation/instruction phải tồn tại trong machine code.

**Ý tưởng so sánh:** `longLoop` là **một method invocation** với rất nhiều backedges, trong khi `plusOne` có rất nhiều calls. Tuy nhiên compiler có thể inline `plusOne` vào `main`, làm số compilation tasks khác mô hình đơn giản. Chương trình không hứa “chắc chắn có đúng một OSR compilation”.

### Quan sát PrintCompilation

Trên HotSpot hỗ trợ flags:

~~~bash
java -XX:+PrintCompilation OsrVsMethodDemo
~~~

**Cảnh báo:** đầu ra console sẽ có **các dòng compilation events không cố định** trước, giữa hoặc sau hai dòng nghiệp vụ trên; JVM còn biên dịch JDK library methods. Vì vậy **không đưa ra stdout nguyên chương trình cố định** cho lệnh này.

Tìm những dòng chứa:
- `OsrVsMethodDemo::longLoop` và dấu `%`.
- `OsrVsMethodDemo::main` và có thể dấu `%` vì vòng gọi bên trong main cũng dài.
- `OsrVsMethodDemo::plusOne` nếu compiler biên dịch riêng; nếu được inline, không nhất thiết xuất hiện thành một normal compilation.
- `@ <number>` sau method để nhận biết OSR entry BCI.
- Level 3/4 để xem đường tiered compilation trong run ấy.

### Chạy ở chế độ interpreted

~~~bash
java -Xint OsrVsMethodDemo
~~~

Lệnh này dùng interpreter-only mode trên HotSpot; chương trình vẫn in hai dòng **cùng giá trị nghiệp vụ**. Thời gian thực thi có thể khác rất lớn nhưng **không hứa một hệ số tăng tốc cố định**. Một số thành phần VM/library có xử lý đặc biệt, song với code Java của ví dụ này `-Xint` là đối chứng để tránh JIT optimization.

### Chạy chỉ C1 để quan sát thay đổi tier

~~~bash
java -XX:TieredStopAtLevel=1 -XX:+PrintCompilation OsrVsMethodDemo
~~~

`TieredStopAtLevel=1` giới hạn compiled tiers theo HotSpot version có hỗ trợ. Quan sát compilation entries và so với tiering mặc định, nhưng không đọc đây là “buộc OSR level 1 chắc chắn xảy ra”. Việc thay tier có thể đổi warmup, compiler overhead và cả loại compilation request.

## 10. Ví dụ thứ hai: OSR phải bảo toàn locals, branch và loop exit

Tạo file **`OsrStateDemo.java`**:

~~~java
public class OsrStateDemo {
    static long countEven(int n) {
        long sum = 0L;
        int accepted = 0;

        for (int i = 0; i < n; i++) {
            if ((i & 1) == 0) {
                sum += i % 10;
                accepted++;
            }
        }
        return sum + accepted;
    }

    public static void main(String[] args) {
        System.out.println("result=" + countEven(8_000_000));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 OsrStateDemo.java
java OsrStateDemo
~~~

**Stdout chính xác:**

~~~text
result=20000000
~~~

Giải thích:
- Trong 8 triệu số `0..7,999,999` có đúng 4 triệu số chẵn, nên `accepted=4.000.000`.
- Đối với `i` chẵn, `i % 10` lặp 0,2,4,6,8; tổng 20 cho 5 phần tử và average 4. Qua 4 triệu phần tử, `sum=16.000.000`.
- Kết quả = `sum+accepted=20.000.000`.

Nếu JVM quyết định OSR vào giữa loop, values của `sum`, `accepted` và `i` phải tiếp tục đúng để chương trình luôn in kết quả này. Nếu compiled code mất trạng thái hoặc lặp lại một nhánh đã chạy, kết quả sẽ sai — nhưng một JVM đúng không được làm vậy.

### Dùng javap kiểm tra BCI

~~~bash
javap -c -p OsrStateDemo
~~~

Ở đoạn `countEven`, bạn sẽ thấy những lệnh như `iload`, `iand`, `irem`, `iinc` và các nhánh `if...`/`goto` với bytecode offsets. Vị trí `@ bci` trong PrintCompilation cần đối chiếu với **offset bytecode thực tế** của file .class, không với số dòng Java.

## 11. Mô tả OSR chính xác hơn: compile request không đồng nghĩa chuyển thành công

Quá trình có thể được chia thành các mốc độc lập:

~~~text
(A) Hot backedge observed
           |
(B) Compilation policy quyết định request?
       /                  \
     NO                    YES
     |                      |
  interpret         Enqueue OSR compile task
                           |
                    Compiler thread xử lý
                           |
                    Code generated/installed?
                     /               \
                   NO                 YES
                   |                   |
          vẫn interpret        OSR nmethod sẵn
                                       |
                                Activation còn đang
                                ở checkpoint phù hợp?
                                  /           \
                                NO             YES
                                |               |
                             Không chuyển    Runtime có thể
                             lần này         OSR transition
~~~

Phân biệt rõ:
- **OSR task requested**: policy đã yêu cầu compile.
- **OSR compiled**: native code đã tạo/cài đặt thành công.
- **OSR activated**: current execution đã chuyển sang OSR entry.
- **Compiled at higher tier**: code được nâng chất lượng/tier, chưa chắc current activation dùng phiên bản đó.

`-XX:+PrintCompilation` chủ yếu hữu ích để **quan sát compilation events**, không thay thế bộ profiling runtime chứng minh activation path ở mức cực chi tiết. Với nghiên cứu sâu có thể dùng JITWatch + HotSpot compilation logs hoặc các công cụ profiling/diagnostic được hỗ trợ.

## 12. Deoptimization: hướng chuyển ngược lại OSR

JIT có thể tối ưu dựa trên **assumptions** từ type/branch profiles. Ví dụ, compiler thấy một call site thường chỉ có receiver thuộc một class, rồi inline method tương ứng. Nếu sau đó loại receiver khác xuất hiện, một guard có thể fail.

**Deoptimization** chuyển execution từ compiled code về một trạng thái tương thích với interpreter (hoặc đường thực thi phù hợp khác), phục hồi các JVM frames/locals/operand values để tiếp tục đúng Java semantics.

So sánh:

| Hiện tượng | Chiều chuyển | Vì sao xảy ra |
| --- | --- | --- |
| **OSR** | Interpreter / lower tier → compiled OSR entry | Loop nóng, muốn tăng tốc **activation hiện tại** |
| **Deoptimization** | Optimized compiled frame → deoptimized/interpreted state | Assumption không còn đúng, uncommon trap, dependency invalidation hoặc lý do runtime khác |
| **Normal compilation** | Tạo compiled code cho method entry mới | Hot method/hot calls |
| **Recompilation** | Có code compiled tier/optimization mới | Profile trưởng thành, invalidation hoặc tiering |
| **Inlining** | Đặt thân callee vào mã compiled caller | Giảm dispatch/cho phép optimize xuyên method boundary |

**Không đồng nhất OSR với deoptimization.** Hai cơ chế đều yêu cầu metadata để diễn dịch/chuyển trạng thái frames, nhưng mục đích và hướng chuyển khác nhau.

### Uncommon trap và loop exit

Một JIT compiler có thể quan sát một vòng lặp cực dài mà exit condition hầu như chưa xảy ra trong profile. Nó có thể tối ưu luồng “thường gặp” và dùng **uncommon trap** cho nhánh hiếm. Khi loop cuối cùng kết thúc, nhánh exit có thể kích hoạt deoptimization. Đây là một khả năng HotSpot, **không phải chuyện mọi OSR loop đều deopt ở vòng cuối**.

### Ví dụ thay đổi profile theo loại receiver

Tạo tệp **`ProfileShiftDemo.java`**:

~~~java
public class ProfileShiftDemo {
    static int read(Number value) {
        return value.intValue();
    }

    public static void main(String[] args) {
        long total = 0L;

        for (int i = 0; i < 50_000; i++) {
            total += read(Integer.valueOf(1));
        }

        total += read(Double.valueOf(2.5));

        System.out.println("total=" + total);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ProfileShiftDemo.java
java ProfileShiftDemo
~~~

**Stdout chính xác:**

~~~text
total=50002
~~~

Phần lớn calls đi qua Integer, rồi một lần qua Double. JVM **có thể** tối ưu dựa trên profile loại receiver và deopt khi call site gặp Double, nhưng **không bảo đảm** vì compiler có thể không compile, có thể đã thấy polymorphic profile hoặc chọn generic dispatch. Kết quả nghiệp vụ phải đúng dù không có deoptimization nào xảy ra.

## 13. Compilation logs: đọc dấu `%` và mức độ tin cậy

### Flag `PrintCompilation`

~~~bash
java -XX:+PrintCompilation OsrStateDemo
~~~

Nó in các compile events của **cả ứng dụng lẫn nhiều library methods**, có thể làm lẫn stdout. Khi tìm dòng `OsrStateDemo::countEven`, một OSR compilation thường trông dạng:

~~~text
   120   67 %     3       OsrStateDemo::countEven @ 4 (49 bytes)
   160   88 %     4       OsrStateDemo::countEven @ 4 (49 bytes)
   166   67 %     3       OsrStateDemo::countEven @ 4 (49 bytes)   made not entrant
~~~

**Đây là log minh họa, không phải output được dự đoán chính xác**. Số bytecodes, BCI, IDs và order phải lấy từ run thật. HotSpot output còn có attributes như `s` (synchronized), `!` (exception handlers), `b` (blocking), `n` (native), hoặc trạng thái khác tùy release; không diễn giải các ký hiệu chỉ dựa vào một ví dụ mà không xem bản JDK.

### Ý nghĩa của `made not entrant` và `made zombie`

- **Made not entrant:** compiled nmethod không còn được JVM chọn cho **những lần entry mới**, nhưng các active frames có thể tiếp tục sử dụng code đó hoặc cần chuyển/cleanup thích hợp.
- **Made zombie:** nmethod không còn active ở trạng thái thích hợp và có thể chờ reclaimed code-cache storage theo cơ chế CodeCache/sweeper.

**Không đồng nhất “made not entrant” với Java object bị GC thu gom.** Đây là lifecycle của **compiled machine code** trong code cache, không phải lifecycle của một heap object ứng dụng.

### JIT compilation output không cố định theo code

Thời điểm code compiled chịu ảnh hưởng từ:
- JDK build và HotSpot implementation;
- compile thresholds và profiling heuristics;
- CPU speed/core counts, compile queue contention;
- JVM startup mode, tiering flags và code cache;
- loop trip count, type profiles và inlining;
- interpreter performance, JIT warmup và background compilation.

Vì vậy giáo trình phải tách:
1. **Stdout nghiệp vụ** của các ví dụ — có thể dự đoán chính xác.
2. **JVM diagnostic output** — không thể hứa cùng compile IDs, mức tier hoặc số OSR tasks qua mọi máy.

## 14. Compilation log chi tiết với `LogCompilation`

Một số nghiên cứu JIT cần log dạng XML để phân tích compilation tasks, inlining, traps và mã OSR. Trên JDK 17 HotSpot có hỗ trợ:

~~~bash
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -XX:LogFile=jit-log.xml OsrStateDemo
~~~

Các options `LogCompilation`/`LogFile` là **diagnostic**, không nhất thiết khả dụng/hoạt động giống nhau trên các vendor JVM. Trong XML logs có thể thấy thuộc tính tương tự:

~~~xml
<task compile_id='67' method='OsrStateDemo countEven (I)J'
      compile_kind='osr' osr_bci='4' level='3'>
</task>
~~~

Đây là **XML minh họa về kiểu metadata**, không phải fragment schema đầy đủ hay output bắt buộc. Cần dùng file log thực và JDK tools/JITWatch phù hợp để phân tích.

Các từ khóa có ích:
- `compile_kind='osr'` / `osr_bci`: xác định task OSR.
- `compile_id` và `level`: biết identity/tier của compilation.
- `task_done`: biết task đã hoàn tất với trạng thái nào.
- Inlining decisions/failed optimizations: tìm vì sao một đoạn không tối ưu như dự kiến.
- Traps và invalidations: kiểm tra deoptimization/recompilation.

**Cẩn thận:** nội dung XML, tag details và log availability là HotSpot-specific và có thể thay đổi; không coi nó là Java SE public contract.

## 15. Thử nghiệm các chế độ JIT một cách có kiểm soát

| Lệnh/flag | Mục đích học tập | Giới hạn |
| --- | --- | --- |
| `java -Xint Demo` | Chủ yếu chạy Java code interpreted | Không đại diện performance của HotSpot ở production tiered mode |
| `java -XX:+PrintCompilation Demo` | Liệt kê JIT compilation events | Nhiều library methods và timing không cố định |
| `java -XX:TieredStopAtLevel=1 ...` | Giới hạn JIT tối đa tới C1 level 1 trên bản hỗ trợ | Không buộc OSR một method cụ thể |
| `java -XX:-TieredCompilation ...` | So behavior khi tắt tiered compilation | Thay đổi compilation policy và thresholds mạnh, không phải chỉ “tắt C1” |
| `java -Xcomp ...` | Yêu cầu compile mạnh mẽ hơn khi thực thi | Thay đổi startup, blocking, profile quality, có thể giảm performance |
| `java -XX:CompileThresholdScaling=...` | Chỉnh thresholds heuristic | Không phải công tắc OSR đơn giản, không nên dùng để tạo số liệu benchmark |
| `java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation ...` | Ghi log compilation chi tiết | Diagnostic overhead và XML format không bất biến |
| `javap -c -p Demo` | Xem bytecode và offsets | Không hiển thị machine code JIT hay chứng minh OSR activated |

### Vì sao `-Xcomp` không phải “chạy JIT giống production nhưng nhanh hơn”?

`-Xcomp` khuyến khích biên dịch mạnh thay vì interpreter-warmup profile thông thường; JVM có thể biên dịch **trước khi có profile tốt**, tăng startup/compile overhead và đôi khi làm ứng dụng chậm hơn. Không dùng nó làm bằng chứng “JIT luôn tốt hơn interpreter” hoặc để đo lựa chọn tier production.

### Chỉ C1 không giống chỉ C2

Tiered compilation có chi phí và chiến lược thu profile. Khi đặt `TieredStopAtLevel=1`, JVM có thể tiết kiệm compile CPU, cải thiện startup cho một số workload nhưng bỏ lỡ tối ưu C2. **Không suy ra** mọi chương trình chậm hơn; phải so startup time, steady-state throughput và tail latency.

### Có nên cố ép OSR bằng threshold?

Thường **không**. Các thresholds khác nhau theo tiered policy và JVM version. Với mục đích học, chọn loop đủ dài, bật compilation logging rồi **quan sát**. Nếu cần nghiên cứu chuyên sâu, ghi tất cả flags và exact JDK build, dùng compiler directives/JITWatch/JFR; không xây tutorial dựa vào một “magic number” mà không kiểm chứng.

## 16. Method invocation counter và backedge counter không phải hai public API

Không có Java SE API tiêu chuẩn cho:

~~~java
// Ví dụ giả định KHÔNG PHẢI Java API:
int count = JVM.getBackedgeCounter(MyClass.class, "method");
~~~

Đoạn trên là **pseudo-code bị comment**, không biên dịch thành công nếu bỏ dấu comment vì API không tồn tại. HotSpot có counters/profiling metadata nội bộ; giá trị, dạng lưu trữ và lifetime tùy VM build/tier.

**Đừng dùng Reflection** để đoán vị trí backedge counters trong method/class. Tương tự, `Thread.getStackTrace()` không tiết lộ trạng thái compiled vs interpreted của từng frame theo contract phổ thông.

Nếu cần quan sát HotSpot internals:
- JVM logging/profiling phù hợp.
- JFR compilation events nếu recording/event set có hỗ trợ.
- `jcmd` diagnostics.
- JITWatch hoặc tooling nghiên cứu trên đúng JDK.
- OpenJDK HotSpot source cho bản JDK tương ứng.

Một số diagnostic VM flags chỉ có trong debug build; không dùng chúng như hướng dẫn chạy phổ quát trên production binary.

## 17. OSR và safepoint: liên quan nhưng không đồng nghĩa

JVM cần những điểm thích hợp để:
- kiểm tra requests từ runtime;
- đồng bộ GC/safepoints;
- chuyển interpreter/compiled execution khi code mới sẵn;
- deopt hoặc giải quyết một số runtime conditions.

Backedges thường là vị trí tự nhiên cho một số checks. Tuy nhiên:

- **OSR entry** là điểm chuyển trạng thái vào compiled nmethod, được xác định bởi BCI.
- **GC safepoint** là trạng thái/phối hợp VM giúp runtime thực hiện các hoạt động cần biết object references và thread states.
- **Deoptimization point** là nơi VM có đủ thông tin để tái tạo state tương thích khi rời optimized code.
- Những khái niệm này liên quan ở mức metadata/checkpoint nhưng **không đồng nghĩa một OSR transition = một GC pause**.

Một loop có thể không có object allocations vẫn đủ “nóng” để được JIT chú ý. OSR không chỉ được kích hoạt do garbage collector, và GC không phải điều kiện bắt buộc để compile một method.

## 18. OSR trong synchronized method và exception handling

Một method đang chạy có thể có:
- `synchronized` method monitor;
- `synchronized(lock)` block;
- `try/catch/finally`;
- local references cần GC chính xác;
- object state bị mutate qua mỗi loop iteration.

OSR phải giữ đúng Java semantics liên quan locks, exception flow và locals.

Ví dụ (snippet minh họa):

~~~java
static synchronized long count(int n) {
    long total = 0;
    for (int i = 0; i < n; i++) {
        total += i;
    }
    return total;
}
~~~

Nếu HotSpot OSR-compile loop, quyền sở hữu monitor **không thể tự biến mất** trong quá trình chuyển. Khi method kết thúc hoặc ném exception, Java synchronization semantics vẫn phải đúng.

OSR không phải “bỏ qua phần code trước vòng lặp”. Nó tiếp tục từ điểm tương thích với **state được tạo bởi phần code đã thực hiện**, và phải bảo toàn những side effects đó.

## 19. OSR và inlining của call sites

Khi compiler biên dịch `caller()` và có thể inline `callee()`, HotSpot có thể tạo **compiled code chứa callee logic ngay bên trong caller**. Vì thế:
- Log của `callee()` không nhất thiết có normal nmethod riêng.
- A method có thể nằm trong một compiled caller frame, và stack traces vẫn cần hiển thị Java-level frames đúng.
- Counters/hotness có thể gắn với method đang inline, không chỉ owner top-level.
- OSR policy có thể xét loop/inlinee phù hợp để chọn compile requests; chi tiết thuộc compiler policy.

**Ví dụ quan trọng:** `plusOne` trong chương trình ở mục 9 quá nhỏ nên rất dễ inline. Nếu không thấy `plusOne` trong PrintCompilation, **không kết luận nó “không được tối ưu”**. Nó có thể đã được inline và tối ưu mạnh vào loop bên ngoài.

### Muốn ép một method không inline?

Có thể dùng compiler directives hoặc `-XX:CompileCommand=dontinline,...` trên HotSpot được hỗ trợ cho một nghiên cứu riêng, nhưng điều đó **thay đổi profile và code shape**. Không đưa flags ép inline/no-inline làm kết luận hiệu năng production; hãy đo cả baseline và experimental run.

## 20. Warmup: tại sao một benchmark rất ngắn dễ đánh giá sai JIT

HotSpot có thời gian warmup do:
- class loading/verification/initialization;
- interpreter profiling;
- C1 compilation và OSR;
- C2 compilation/recompilation;
- dynamic linking, caches và data structure initialization;
- JIT recompilation/deoptimization và code-cache management.

Một single-shot microbenchmark:

~~~java
long before = System.nanoTime();
// Run a loop and print elapsed...
~~~

là **snippet minh họa**, chưa phải benchmark đủ tốt. Vòng đầu có thể chịu compilation overhead hoặc OSR transition giữa chừng, vòng sau đi thẳng vào optimized nmethod. Đo một lần rồi kết luận “loop được OSR nên nhanh gấp 10 lần” là không chắc chắn.

### JMH và đo đúng

**OpenJDK JMH** là dự án dành cho microbenchmark có warmup iterations, measurement iterations, forks, controlled state và blackholes. Khi đánh giá:
1. Viết workload tái hiện logic ứng dụng.
2. Bảo đảm kết quả được tiêu thụ, tránh dead-code elimination.
3. Tách class loading/setup khỏi timed region khi mục tiêu là steady-state.
4. Dùng nhiều forks/runs và so distributions.
5. Ghi JDK build, flags, CPU quota và compiler modes.
6. Đo performance lẫn compilation events nếu câu hỏi là warmup/OSR.
7. Với live services, đối chiếu request latency và throughput thật, không chỉ microbenchmark.

**Một loop rất dài trong một method** có thể tận dụng OSR, nhưng benchmark còn chịu branch prediction, vectorization, CPU caches, scheduling và profile type. Không đoán rằng OSR là nguồn duy nhất của speedup.

## 21. Code cache và vòng đời compiled code

Native JIT output phải được lưu ở **code cache**. HotSpot có thể dùng **segmented code cache**, chia các vùng lưu non-method/profiled/non-profiled code theo configuration.

Khi nhiều methods và OSR variants được compile:
- code cache memory tăng;
- compiler phải duy trì metadata và nmethods;
- code sweeper có thể làm sạch code đã không còn active;
- có thể xuất hiện states như `made not entrant`, `made zombie`;
- khi code cache đầy hoặc bị giới hạn, JIT compilation có thể bị hạn chế/tạm ngưng, làm performance khác.

**Không nhầm code cache với Java heap.** Code cache lưu machine code và runtime metadata, không phải nơi `new Person()` tạo Java object.

Lệnh quan sát trên HotSpot có hỗ trợ (thay PID thật):

~~~bash
jcmd PID Compiler.codecache
jcmd PID Compiler.codelist
jcmd PID Compiler.queue
~~~

Những diagnostic commands có availability/output thay đổi theo JDK; kiểm tra bằng `jcmd PID help` trên exact runtime. Một số lệnh có thể cần quyền attach và có tác động đến hệ thống; không chạy tùy tiện với production khi chưa có quyền.

### Khi code cache hết

Một cảnh báo về code cache có thể giải thích tại sao compilation dừng, chứ không có nghĩa OSR algorithm sai. Kiểm tra code cache usage, `ReservedCodeCacheSize` và compiled-method churn trước khi đổi heap `-Xmx` — vì **-Xmx điều khiển Java heap, không trực tiếp là giới hạn code cache**.

## 22. Dùng JFR để quan sát compilation

**Java Flight Recorder (JFR)** có các event types liên quan compilation/code cache/tiered execution tùy JDK/event configuration. Có thể dùng JFR để phân tích:
- compilation activity và compiler threads;
- durations của compilation;
- code cache statistics;
- CPU profile trong warmup;
- deoptimization/code-cache events khi được bật/hỗ trợ;
- latency hoặc throughput bên ngoài đối chiếu với giai đoạn warmup.

Ví dụ chạy chương trình trong một recording:

~~~bash
java -XX:StartFlightRecording=filename=jit-study.jfr,dumponexit=true,settings=profile OsrVsMethodDemo
~~~

Đầu ra binary `jit-study.jfr` không có nội dung cố định. Đọc bằng JDK Mission Control hoặc `jfr print` trên JDK phù hợp. Trên workload quá ngắn, recording có thể không có đủ events để diễn giải. JFR sampling không chứng minh mọi OSR activation đã xảy ra, đặc biệt khi event set không ghi transition cụ thể.

Muốn lấy dữ liệu trong process đang chạy:

~~~bash
jcmd PID JFR.start name=JitStudy settings=profile duration=60s filename=jit-study.jfr
jcmd PID JFR.check
~~~

`PID` là placeholder cho process ID, thay bằng số thực. Diagnostic data có thể chứa thông tin ứng dụng; bảo vệ files.

## 23. Quy trình chẩn đoán “vòng lặp nóng mà không nhanh”

Nếu loop chạy chậm dù tưởng rằng OSR/JIT sẽ tối ưu:

1. **Kiểm tra đúng JVM**: `java -version`, vendor/HotSpot build, architecture, flags.
2. **Xác định chế độ**: đang có `-Xint`, `-XX:-TieredCompilation`, `TieredStopAtLevel`, hoặc flags giới hạn compilation không?
3. **Xem PrintCompilation**: có method/OSR compile task không, level nào, có `made not entrant`?
4. **Đối chiếu javap**: `@ bci` ứng với loop nào?
5. **Kiểm tra compile queue**: compiler threads có bận, code cache gần đầy hay method bị loại khỏi compile không?
6. **Profiling actual hot path**: method có được inline vào caller? Có virtual calls/megapolymorphic receivers, bounds checks hoặc memory stalls không?
7. **Xem deoptimization/traps**: assumptions thay đổi khiến optimized code phải invalidated nhiều lần?
8. **Kiểm tra CPU/GC**: loop có bị tranh CPU, blocked, memory bandwidth hay GC pauses không?
9. **Đo sau warmup**: sử dụng JMH/service metrics, không kết luận từ một lần chạy.
10. **Tối ưu thuật toán/dữ liệu**: nếu algorithm O(n²) hoặc cache locality tệ, JIT/OSR không thay thế được thiết kế thuật toán.

### Dấu hiệu thường gặp và cách suy luận

| Hiện tượng | Điều cần kiểm tra | Không nên vội kết luận |
| --- | --- | --- |
| Không thấy ký hiệu `%` | OSR task không được in/không xảy ra, JIT flags, threshold/policy, loop ngắn, inlining | “JVM không hỗ trợ OSR” |
| Có `%` nhưng method vẫn chậm | OSR code có được activated, workload, memory/GC/CPU, optimizer | “OSR tự động làm nhanh” |
| Có nhiều levels 3 và 4 | Tiered C1→C2 progression, recompile | “Vòng lặp chạy nhiều bản đồng thời trên cùng thread” |
| Có `made not entrant` | New tier/invalidation/nmethod lifecycle | “Ứng dụng bị lỗi/GC thu object” |
| Có deoptimization | Guard/profile change, uncommon traps | “Kết quả Java bị sai” |
| `-Xint` chậm hơn | Interpreter overhead và JIT optimizations thiếu | “OSR alone tạo mọi speedup” |
| Method nhỏ không xuất hiện riêng | Inlining, compile policy | “Method không được tối ưu” |
| Compilations giảm khi service chạy lâu | Warmup hoàn tất hoặc code cache pressure | “JIT bị tắt” |
| Một số runs không có OSR | Timing/queue/loop finishes before compilation | “PrintCompilation bị lỗi” |

## 24. Những ngộ nhận cần tránh

| Ngộ nhận | Sự thật |
| --- | --- |
| “Normal method compilation làm lần gọi đang chạy tự chuyển sang code mới.” | Standard entry code chủ yếu cho invocations mới; OSR giải quyết current activation ở loop. |
| “OSR là compiler khác C1/C2.” | OSR là **kiểu compilation/entry**; C1/C2 có thể sinh OSR nmethod. |
| “OSR chỉ cần thay pointer trong biến Java.” | VM phải chuyển frame state/locals/operand/monitors đúng semantics. |
| “OSR bci là iteration number.” | BCI là bytecode offset trong method. |
| “Dấu % trong PrintCompilation là tỷ lệ CPU.” | Dấu `%` là OSR compilation marker. |
| “Cứ loop 10.000 lần là OSR.” | Policy/threshold/timing khác theo JVM và workload. |
| “Method có normal nmethod thì không thể có OSR nmethod.” | Có thể có cả hai variants. |
| “C1 OSR và C2 OSR luôn được dùng theo thứ tự tất định.” | Tier policy, queue, compilation latency, deopt và profiling quyết định. |
| “OSR là GC pause.” | OSR liên quan JIT execution transition, không phải tên một GC event. |
| “OSR là deoptimization.” | Hướng/mục đích khác; OSR đi vào optimized code, deopt rời optimized code khi cần. |
| “`-Xcomp` phản ánh production throughput.” | Nó thay compile timing/profile, có thể giảm performance. |
| “`-Xint` cũng có OSR native code.” | Interpreter-only mode không dùng JIT cho Java methods theo cách thông thường. |
| “Log `made zombie` báo object Java chết.” | Đây là nmethod/code-cache lifecycle. |
| “`javap` hiển thị C2 assembly.” | `javap -c` xem bytecode; JIT machine code cần công cụ/flags khác. |
| “Không thấy compiled callee tức JIT bỏ qua.” | Callee có thể được inline vào caller. |
| “Có OSR task nghĩa OSR chắc chắn activated.” | Compilation và transition là hai thời điểm/điều kiện riêng. |
| “OSR luôn tăng tốc mọi loop.” | Biên dịch/transition có overhead và loop có thể kết thúc sớm. |

## 25. Câu hỏi tự kiểm tra

**1. Method được gọi một lần nhưng chạy 50 triệu iterations có thể được JIT chú ý không?**

Có. Backedge counts/profiling có thể kích hoạt một **OSR compilation**, dù invocation count thấp.

**2. Vì sao normal compiled method entry chưa đủ để tăng tốc lần gọi đang chạy?**

Vì compiled entry phục vụ bắt đầu method; current invocation đã ở sâu trong loop. OSR cung cấp điểm vào ở BCI phù hợp cùng state transfer.

**3. Dấu `%` trong PrintCompilation là gì?**

OSR compilation marker, không phải phần trăm tải CPU.

**4. `@ 8` trong dòng OSR log thường chỉ gì?**

Một **OSR BCI** trong bytecode method, không phải Java line 8 hay vòng lặp thứ 8.

**5. C1/C2 liên quan OSR thế nào?**

C1/C2 là compilers; cả hai có thể tạo OSR variants ở compilation levels thích hợp.

**6. Tại sao phải chuyển giá trị biến `sum` vào OSR code?**

Để không mất phần kết quả đã cộng trong interpreter và để method tiếp tục từ trạng thái hiện tại.

**7. OSR có phải Java syntax feature không?**

Không. Source `for` hoặc `while` không đổi; OSR do VM quản lý.

**8. Dùng `-Xint` sẽ thấy JIT method compilation bình thường không?**

Không trong execution Java interpreted-only thường gặp; lệnh này chủ yếu dùng làm đối chứng interpreter.

**9. Vì sao một method nhỏ được gọi nhiều không xuất hiện thành compilation entry riêng?**

Có thể được inline vào caller hoặc không đủ điều kiện biên dịch riêng.

**10. OSR task đã compile là bảo đảm current loop chuyển sang compiled code?**

Không. Loop có thể đã kết thúc hoặc policy/transition không chọn mã OSR ấy.

**11. Tại sao nmethod có thể `made not entrant`?**

Khi mã không còn được dùng cho new entries, ví dụ do new tier hoặc dependency invalidation. Đây không phải Java heap object GC.

**12. OSR và deoptimization khác gì?**

OSR nhằm đi **vào compiled code** giữa activation; deoptimization phục hồi execution state khi phải **rời optimized code**.

**13. Nếu loop có synchronized monitor, OSR được phép bỏ qua lock không?**

Không. Monitor ownership và Java synchronization semantics phải được bảo toàn.

**14. Vì sao dùng JMH thay vì tính time của một vòng for lần đầu?**

Vì warmup, OSR/JIT compilation latency, JIT dead-code elimination, cache và scheduling làm số đo đơn lẻ thiếu tin cậy.

**15. Phải nhìn metric nào khi compiler queue quá tải?**

Compile events/queue, compiler CPU/time, tiering progression, code cache và workload actual CPU/latency, cùng trạng thái JVM flags.

**16. Tại sao PrintCompilation số thời gian/method ID không thể là expected stdout?**

Vì compilation được quyết định runtime, chịu JDK build, thread scheduling, CPU, profile và flags khác nhau.

## 26. Bản đồ ghi nhớ

~~~text
                 JAVA BYTECODE METHOD
                          |
                      INTERPRETER
                          |
             +------------+-----------+
             |                        |
        Many method calls      One long-running loop
             |                        |
       invocation counters        backedge counters
             |                        |
             v                        v
     NORMAL COMPILATION         OSR COMPILATION
     method entry code          osr_bci inside method
             |                        |
       future invocations      current active invocation
             |                        |
             +-------------+----------+
                           |
                       TIERED JIT
               0 interpreter -> C1 -> C2
                           |
               nmethod(s) in CODE CACHE
                           |
             +-------------+--------------+
             |                            |
       OPTIMIZED EXECUTION           DEOPTIMIZATION
                                    (if assumptions fail)
             |                            |
             +----------------------------+
                           |
                 JAVA SEMANTICS UNCHANGED
                           |
                  DIAGNOSTICS / MEASUREMENT
           javap -c; PrintCompilation (% / @bci)
           LogCompilation; JFR; jcmd; JITWatch
                           |
                  JMH / service metrics
~~~

**Kết luận:** **Normal method compilation** tăng tốc các lần gọi mới từ method entry; **OSR** cho phép một lần gọi đã ở giữa **vòng lặp nóng** đi vào compiled native code tại một **BCI** thích hợp mà vẫn giữ đúng locals/operand/monitor state. OSR là một cơ chế **JIT execution transition**, không phải một compiler riêng hay một hoạt động GC. Hãy nhận diện nó qua `%` và `@ bci` trong HotSpot logs, rồi đo hiệu năng thực tế với warmup và diagnostics thay vì hứa thresholds hoặc speedups cố định.

## Tài liệu tham khảo chuẩn

- [Oracle JDK 17 — The java Command: PrintCompilation, TieredCompilation, LogCompilation, CompileThresholdScaling, CompileCommand](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [JVMS Java SE 17 — §2 Runtime Data Areas, JVM Frames and Operand Stacks](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html).
- [JVMS Java SE 17 — §6 Java Virtual Machine Instruction Set, BCI/branches](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html).
- [OpenJDK HotSpot — compilationPolicy.hpp, tiered compilation levels, backedge events](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compilationPolicy.hpp).
- [OpenJDK HotSpot — compileBroker.cpp, OSR compilation tasks and statistics](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compileBroker.cpp).
- [OpenJDK HotSpot — compiler_globals.hpp, OnStackReplacePercentage and diagnostics](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compiler_globals.hpp).
- [OpenJDK HotSpot — C2 parse1.cpp, OSR BCI and type flow](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/opto/parse1.cpp).
- [OpenJDK — Java Microbenchmark Harness (JMH)](https://openjdk.org/projects/code-tools/jmh/).
- [Oracle JDK 17 — javap](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).
- [OpenJDK JITWatch project](https://github.com/AdoptOpenJDK/jitwatch).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-006">Warm-up, trạng thái ổn định và khác biệt giữa thời gian khởi động với hiệu năng đỉnh</a>

<details>
<summary>Xem chi tiết</summary>

# Warm-up, trạng thái ổn định và khác biệt giữa thời gian khởi động với hiệu năng đỉnh

## 1. Tại sao cùng một phương thức Java chạy lúc đầu chậm hơn lúc sau?

Với **HotSpot JVM**, bytecode Java có thể được thực thi bằng **interpreter**, sau đó những phương thức hoặc vòng lặp có lợi được **JIT (Just-in-Time) compiler** dịch thành mã máy tối ưu. Trong quá trình chương trình chạy, JVM còn thu thập thông tin về kiểu đối tượng, nhánh điều kiện và tần suất gọi, nạp class, giải quyết liên kết, khởi tạo dữ liệu, mở kết nối, làm nóng cache và thực hiện garbage collection (GC).

Do đó, hai lần chạy **cùng một đoạn source code** có thể có thời gian khác nhau:

- **Cold start:** tiến trình JVM mới bắt đầu, nhiều class chưa nạp/khởi tạo, nhiều method chưa được JIT compile, business caches chưa sẵn sàng.
- **Warm-up:** một hoặc nhiều thành phần đang chuyển đổi trạng thái: JIT biên dịch và tái biên dịch, profiling trưởng thành, code cache tăng, thread pools và connection pools hình thành, dữ liệu hoặc OS page cache được làm nóng.
- **Steady state — trạng thái ổn định:** hệ thống hoạt động trong một khoảng đo mà phân bố throughput/latency, allocation, GC, CPU, compilation và workload không còn xu hướng chuyển tiếp lớn; vẫn có nhiễu và biến động tự nhiên.
- **Peak performance — hiệu năng đỉnh:** throughput lớn nhất hoặc latency thấp nhất theo một **định nghĩa và tài nguyên cụ thể**, đạt được ở một giai đoạn thí nghiệm. Nó **không tự chứng minh** hệ thống ổn định, đáp ứng SLO, hoặc duy trì được mức ấy trong production.

**Đề bài không chỉ hỏi “JIT có nhanh hơn interpreter không”.** Cần hiểu *khi nào* hệ thống bắt đầu phục vụ, *khi nào* kết quả đo đáng tin, *tại sao* một lần chạy nhanh không phải kết quả bền vững và *mục tiêu nào* cần tối ưu cho loại ứng dụng cụ thể.

> **Quy tắc vàng:** `time to first useful result`, `time to readiness`, `time to steady-state throughput` và `peak throughput` là **bốn phép đo khác nhau**. Không đánh đồng thời gian khởi động với tốc độ của một vòng lặp đã nóng.

## 2. Một timeline điển hình từ process launch đến trạng thái ổn định

~~~text
Thời gian →
OS start Java process
      |
      +-- JVM initialization, class loading, verification/linking
      |
      +-- Application/bootstrap/static initializers
      |
      +-- First successful request / first useful result
      |         |
      |         +-- Class/method nạp thêm theo đường xử lý thực
      |         +-- Interpreter/C1, profiling, C2 compilation (tùy HotSpot)
      |         +-- Cache, connection pool, GC/heap occupancy thay đổi
      |         |
      +-- Readiness signal (do ứng dụng định nghĩa)
      |         |
      +-- Warm-up workload, JIT/code cache/GC tiến tới nhịp vận hành
      |         |
      +-- Candidate steady-state measurement windows
      |         |
      +-- Peak throughput point hoặc latency mục tiêu
~~~

**Các mốc không bắt buộc có thứ tự tuyệt đối như sơ đồ.** Một service có thể báo readiness trước khi mọi route nóng; một hệ khác chỉ readiness sau khi preload dữ liệu; background class loading hoặc recompilation có thể diễn ra **sau** một khoảng tưởng như đã ổn định. Đỉnh throughput có thể xuất hiện trước hoặc sau steady-state window, thậm chí ở điểm saturate làm p99 latency rất xấu.

### 2.1. Startup latency — thời gian khởi động

Định nghĩa phải có **hai mốc rõ ràng**:

- `process_start → main entry`: từ lệnh khởi chạy đến khi bước vào `main`.
- `process_start → first result`: từ process launch đến request thành công đầu tiên / kết quả nghiệp vụ đầu tiên.
- `process_start → ready`: đến khi readiness probe đáp ứng contract hệ thống.
- `deploy → ready`: còn chứa image pull, container create, scheduling, network và orchestration; **không còn chỉ là JVM startup**.

Một JVM có thể vào `main` rất nhanh nhưng application mất nhiều giây để tạo connection pool hoặc tải model. Ngược lại, ứng dụng báo ready sớm nhưng first requests sau ready bị chậm do class/JIT còn lạnh.

**Đừng gọi `main` in `READY` là bằng chứng toàn bộ service đã sẵn sàng**, trừ khi chương trình thật có protocol readiness phản ánh dependencies, routes và khả năng phục vụ mục tiêu.

### 2.2. Warm-up duration — khoảng chuyển tiếp

Có thể định nghĩa warm-up là **một tập requests hoặc một khoảng thời gian** được chạy trước khi thu thập số liệu chính. Nhưng `warmup = 5s` chỉ là *chính sách thí nghiệm*, **không phải cam kết JVM đã JIT compile mọi method sau 5 giây**.

Một service có hàng chục route có thể làm nóng route A rồi vẫn lạnh route B khi traffic mix đổi. Một method với input loại `Cat` được profile trước có thể bị deoptimize khi lần đầu gặp `Dog`. Warm-up hợp lệ phải đi cùng **representative workload** và kiểm tra hoạt động thực tế, không chỉ chờ đồng hồ.

### 2.3. Steady state — phải được đo và kiểm nghiệm

Một khoảng đo là ứng viên steady state khi ít nhất:

1. **Offered workload ổn định** (arrival rate, request mix, payload, concurrency, data sizes).
2. **Throughput, p50/p99, CPU/GC metrics** không có trend chuyển pha rõ rệt ngoài noise dự kiến.
3. **Số lượng JIT compilations và deoptimizations** không xuất hiện các cơn bùng phát kéo dài tác động lớn.
4. **Live set và heap occupancy** có nhịp tương đối ổn định; không âm thầm tích lũy leak.
5. **External state** như DB cache, TLS sessions, page cache, thread pools đã đi vào trạng thái đại diện.
6. Đã đo qua **nhiều cửa sổ thời gian**, không chỉ một average gộp từ lúc app khởi động.

Đây là **định nghĩa thực dụng có kiểm chứng**, không phải một flag JVM bật lên báo `STEADY_STATE=true`. Một service dùng nhiều feature theo giờ/ngày có thể **không bao giờ đạt một steady state toàn cục duy nhất**; khi đó đo riêng từng regime tải.

## 3. Cơ chế interpreter và tiered compilation trên HotSpot

### 3.1. Interpreter giúp bắt đầu thực thi ngay

Bytecode được JVM thực thi theo contract ngôn ngữ, và HotSpot có interpreter. Interpreter không cần biên dịch thành native optimized code cho mọi method trước khi chạy. Điều đó thuận lợi cho startup/fallback, nhưng các đoạn code được gọi lặp lại nhiều thường có cơ hội được JIT tối ưu.

Không suy luận rằng **toàn bộ một ứng dụng luôn interpreter trong X requests đầu**, rồi chuyển đồng loạt sang compiled. Các methods có thể được JIT compile ở **những thời điểm khác nhau**; một phần code đang interpreter trong khi phần khác đã compiled.

### 3.2. C1 và C2 — trade-off compilation cost và code quality

Trong mô hình HotSpot tiered compilation thường dùng với JDK 17:

- **C1** hướng tới compilation tương đối nhanh, có những tiers thu thập hoặc tận dụng profiling ở các mức khác nhau.
- **C2** có thể sử dụng thông tin profile để thực hiện optimization mạnh hơn, tốn CPU/thời gian compile hơn nhưng thường tạo code tốt hơn cho những hot paths.
- JVM lựa chọn compilation policy dựa trên số lần gọi, loop backedges, profile data, compiler queue và các điều kiện runtime.

Có thể hình dung đơn giản:

~~~text
                 Bytecode / interpreter (tier 0)
                              |
                    profiling và hotness
                              |
               C1 tiers (1 / 2 / 3, tùy đường đi)
                              |
                   thêm profiling / hotness
                              |
                C2 optimized code (tier 4)

Sau đó vẫn có thể:
  - deoptimization → interpreter / compiled tier khác
  - recompilation sau khi profile thay đổi
  - code invalidation / code cache cleanup
~~~

**Đây không phải pipeline cứng bắt buộc cho mỗi method.** Có thể có tier transitions khác nhau, inlining và OSR, phương thức rất nhỏ, code được sử dụng hiếm, VM flags khác biệt. Không đòi “mọi method đều phải đi qua đủ 0→1→2→3→4”.

JDK/implementation có thể dùng compiler khác hoặc cách tổ chức khác; **C1/C2/tiered** là các chi tiết nổi bật của HotSpot, không phải cấu trúc bắt buộc của Java Virtual Machine Specification.

### 3.3. Hot code, call counters, backedges và OSR

HotSpot dùng profiling và hotness estimates, trong đó có **method invocation count** và **loop backedge activity**. Một vòng lặp chạy rất lâu trong một lần gọi có thể trở nên hot ngay khi method chỉ gọi một lần. **On-Stack Replacement (OSR)** giúp VM chuyển một loop đang chạy sang optimized compiled code tại vị trí thích hợp, thay vì phải đợi method kết thúc rồi gọi lại.

Vì vậy:

- “Chỉ gọi method 1 lần nên không bao giờ JIT” là **sai** với loops đủ hot.
- “Gọi 10.000 lần thì chắc C2” cũng **sai**; compiler thresholds/policy không phải constant portable.
- `-XX:CompileThreshold` và các JIT flags có ngữ nghĩa/phạm vi phụ thuộc tiered mode và JDK; không dùng một giá trị như định luật tổng quát.

### 3.4. Speculative optimization và deoptimization

JIT có thể dựa vào profile hiện tại. Ví dụ một virtual call site trong warm-up chỉ nhận object của một class; runtime có thể tối ưu devirtualization và inlining theo giả định có kiểm tra. Khi input kiểu khác xuất hiện hoặc giả định không còn đúng, code có thể **deoptimize** và chạy an toàn theo semantics Java, rồi được recompile khi cần.

~~~text
Warm-up: 99.9% PaymentCard
        |
        v
JIT có profile gần đơn hình, tối ưu nhánh phổ biến
        |
New traffic: PaymentCrypto hoặc edge-case chưa từng thấy
        |
        v
Uncommon trap / deoptimization / recompilation có thể xảy ra
        |
        v
Latency spike ngắn hạn, hành vi sau đó đổi
~~~

Đây là **tình huống mô hình**, không tuyên bố rằng mỗi thay đổi input type chắc chắn deoptimize hoặc luôn tạo một spike có cùng duration.

**Warm-up bằng dữ liệu không đại diện** có thể cho ra một optimized code path đẹp nhưng làm kết quả đo sau đó nhiễu hoặc còn tệ hơn dưới traffic thực.

## 4. Warm-up chịu ảnh hưởng của nhiều yếu tố ngoài JIT

| Thành phần | Khi lạnh | Trong warm-up | Điều có thể ổn định |
| --- | --- | --- | --- |
| **Class loading/linking** | Chưa nạp những classes ít sử dụng | Load khi route/feature được gọi | Ít class loads bất ngờ trên hot paths |
| **Static initialization** | Một số `<clinit>` chưa chạy | Khởi tạo lazy singletons, registries | Ít init đột biến ở request path |
| **Interpreter/JIT** | Nhiều methods interpreted/C1 | Compilation/profiling, OSR, inlining | Ít transitions có ảnh hưởng lớn |
| **Code cache** | Native code cache còn ít compiled methods | Compiled code được thêm/swept | Số đo không tăng bùng phát liên tục |
| **GC/heap** | Heap ít live objects | Promotion, aging, caches được giữ, GC frequency thay đổi | Live set/GC pattern đại diện |
| **Application caches** | Empty/cold | Load DB rows, templates, regex, config, class metadata | Cache-hit rate đại diện |
| **Thread/connection pools** | Chưa tạo hoặc ít active | Spawn, handshake, kết nối, TLS sessions | Capacity/queueing tương đối ổn |
| **OS page cache** | Disk pages chưa resident | Files/resources được truy cập | Fewer disk misses ở workload tương tự |
| **External dependencies** | DB/cache/server ở trạng thái ban đầu | Connection establishment/prepared statements | Response times hợp tải thực |

**Một hiện tượng latency giảm trong 20 giây đầu không đủ chứng minh do JIT.** Cần phân rã thời gian và sử dụng compilation logs/JFR, allocation profiles, class-load logs và dependency metrics.

### `static` và lazy initialization làm first-hit khác steady-state

Một expression như `Pattern.compile`, load JDBC driver, initialize JSON serializer, mở TLS connection hoặc lần đầu gọi một dependency phức tạp có thể chiếm CPU/I/O và class loading. JIT có thể chưa phải phần chi phối.

Điều này đặc biệt rõ với ứng dụng serverless, command-line tool và autoscaling burst: **mỗi process mới lại có startup/cold paths**. “Service đã nhanh sau 5 phút” có thể không cứu first-response SLO khi instance bị tạo và huỷ liên tục.

### CDS và AOT: không suy luận quá phiên bản

**Class Data Sharing (CDS)** và ứng dụng preload/class sharing có thể cải thiện một số phần class loading/startup, nhưng không mặc nhiên đưa tất cả methods của ứng dụng tới **C2 peak**. Các kỹ thuật AOT và VM warm startup tiếp tục tiến hóa giữa JDK releases; **không sử dụng tính năng JDK đời mới như thể JDK 17 có cùng behavior**.

Nếu bật CDS/AppCDS hay flags đặc thù, cần benchmark **cùng JVM build, cùng artifact và cùng cấu hình**; đo startup riêng và peak riêng.

## 5. Java 17 runnable lab: nhìn các batches trước và sau warm-up

Chương trình dưới đây:

1. Chạy **4 warm-up batches**.
2. Đo **6 measured batches** dùng cùng hàm tính toán, cùng số operations.
3. Công bố số iterations không đổi, checksum đầu ra cố định và thời gian từng batch **phụ thuộc môi trường**.
4. Công khai rằng một batch nhanh hơn **không tự chứng minh đã đạt steady state**.

Tạo file **`WarmupLab.java`**:

~~~java
import java.util.Arrays;
import java.util.Locale;

public final class WarmupLab {
    private static volatile long published;

    static long compute(int seed) {
        long x = seed + 0x9E3779B97F4A7C15L;
        for (int i = 0; i < 256; i++) {
            x ^= x >>> 27;
            x *= 0x3C79AC492BA7B653L;
            x ^= x >>> 33;
        }
        return x & 0xffL;
    }

    static long runBatch(int firstSeed, int ops) {
        long sum = 0;
        for (int i = 0; i < ops; i++) {
            sum += compute(firstSeed + i);
        }
        published = sum;
        return sum;
    }

    public static void main(String[] args) {
        final int warmupBatches = 4;
        final int measuredBatches = 6;
        final int operationsPerBatch = 6000;
        final long[] elapsedNs = new long[measuredBatches];

        System.out.println("warmupBatches=" + warmupBatches);
        System.out.println("measuredBatches=" + measuredBatches);
        System.out.println("operationsPerBatch=" + operationsPerBatch);

        for (int i = 0; i < warmupBatches; i++) {
            long start = System.nanoTime();
            runBatch(100_000 + i * operationsPerBatch, operationsPerBatch);
            long elapsed = System.nanoTime() - start;
            System.out.println("warmup" + i + "Ns=" + elapsed);
        }

        long checksum = 0;
        for (int i = 0; i < measuredBatches; i++) {
            long start = System.nanoTime();
            checksum += runBatch(
                    200_000 + i * operationsPerBatch, operationsPerBatch);
            elapsedNs[i] = System.nanoTime() - start;
            System.out.println("measured" + i + "Ns=" + elapsedNs[i]);
        }

        long[] ordered = elapsedNs.clone();
        Arrays.sort(ordered);
        long totalNs = 0;
        for (long n : elapsedNs) totalNs += n;

        System.out.printf(Locale.ROOT, "avgBatchMs=%.3f%n",
                totalNs / (measuredBatches * 1_000_000.0));
        System.out.println("checksum=" + checksum);
        System.out.println("lastBatchPublished=" + published);
    }
}
~~~

### 5.1. Biên dịch/chạy

~~~bash
javac --release 17 WarmupLab.java
java WarmupLab
~~~

**Các dòng output có giá trị xác định theo chương trình:**

~~~text
warmupBatches=4
measuredBatches=6
operationsPerBatch=6000
~~~

Sau đó chương trình in `warmup0Ns=...` đến `warmup3Ns=...`, `measured0Ns=...` đến `measured5Ns=...`, và `avgBatchMs=...`. **Các thời gian này là số động, không có một stdout nanosecond cố định** trên mọi CPU/JIT/OS.

**Hai dòng kết quả tính toán cố định:**

~~~text
checksum=4596631
lastBatchPublished=763004
~~~

Phần mã đã được **biên dịch với `javac --release 17` và chạy trên OpenJDK 24**, ở chế độ HotSpot JIT mặc định và `-Xint`. Hai chế độ đều cho ba dòng cấu hình và hai dòng checksum nói trên.

**Một lần đo cục bộ đã quan sát** khoảng `avgBatchMs=2.023` với JIT mặc định và `avgBatchMs=18.018` với `-Xint`. Đây chỉ là minh họa rằng execution mode có thể ảnh hưởng performance; **không phải benchmark chuẩn của Java 17 runtime, không phải tốc độ trung bình mọi máy và không đủ khẳng định peak/steady state**. Các lần chạy lại có thể khác.

### 5.2. Vì sao có `volatile` và checksum?

`compute` trả về một giá trị phụ thuộc `seed`; `runBatch` cộng kết quả và ghi vào trường `volatile published`, làm phép tính có kết quả có thể quan sát. Điều này giảm nguy cơ JVM loại bỏ toàn bộ nội dung test như dead code. Tuy nhiên `volatile` **không biến một vòng lặp tự viết thành microbenchmark chuẩn**; profiling, inlining, loop optimization, cache, scheduling và độ phân giải `nanoTime()` vẫn có thể làm số đo lệch.

`Arrays.sort(ordered)` diễn ra **sau** các measured batches, không được tính vào các elapsed durations đã lưu; nó giúp minh họa việc có thể xử lý mẫu sau khi đo, dù demo này chỉ in average và không công bố percentile. Nếu muốn so **p99 của từng request** phải đo từng request, không diễn giải `avgBatchMs` là p99.

### 5.3. Những suy luận có thể và không thể rút ra

**Có thể kết luận:** trong môi trường/lần chạy cụ thể, HotSpot mixed-mode và interpreter-only có throughput/timing khác nhau; program output business logic như checksum không đổi.

**Không thể kết luận:** bốn warm-up batches đã chắc chắn làm C2 compile mọi method; sáu batches chứng minh steady state; JIT sẽ nhanh đúng 8.9× ở production; first request chỉ chậm vì interpreter; hoặc `-Xint` là mô phỏng chính xác cold start mặc định.

## 6. Quan sát JIT bằng cờ chẩn đoán thay vì phỏng đoán

### 6.1. `-XX:+PrintCompilation`

Trên HotSpot/JDK 17:

~~~bash
java -XX:+PrintCompilation WarmupLab
~~~

JVM có thể in dòng báo methods được biên dịch cùng các trường như thời gian kể từ JVM start, compilation ID, tier level và method name. **Format/IDs/timing khác nhau theo JVM build và trạng thái tải**, không thể đưa stdout nguyên văn bất biến. Output chẩn đoán có thể xen với `System.out` của ứng dụng, nên **không dùng một lần chạy logging để suy ra benchmark throughput chuẩn**.

Hãy tìm những compilation entries liên quan:

~~~text
WarmupLab::compute
WarmupLab::runBatch
WarmupLab::main
~~~

Sự xuất hiện của method trong log không đảm bảo method được compile **đúng một lần**, compile ở tier cuối hoặc tất cả code ngay lập tức dùng phiên bản ấy. JVM có thể in nhiều entries, OSR compilations, made-not-entrant hoặc deoptimization events.

### 6.2. So với interpreter-only `-Xint`

~~~bash
java -Xint WarmupLab
~~~

`-Xint` buộc interpreter-only theo HotSpot/Java launcher contract và vô hiệu hóa lợi ích JIT compilation. Nó có thể chạy chậm hơn nhiều trên loops tính toán.

**Không dùng `-Xint` như phép đo cold startup production**: ở cold JVM chạy mặc định, VM vẫn có thể compile hot methods trong quá trình phục vụ. `-Xint` chủ động tắt cơ chế đó, nên đây chỉ là **thí nghiệm cơ chế**, không phải cấu hình production được suy ra.

### 6.3. Chỉ dừng tiered compilation ở mức C1

Với HotSpot và cấu hình cho phép:

~~~bash
java -XX:TieredStopAtLevel=1 WarmupLab
~~~

Flag này thường được dùng để giới hạn compiler pipeline ở mức C1 tương ứng, hữu ích khi muốn xem trade-off **thời gian compilation so với chất lượng compiled code**. Nó **không phải công tắc “startup luôn nhanh hơn”**, không cam kết output/timing, và semantics có thể thay đổi giữa releases. Cần đo cả first-response latency lẫn sustained throughput.

### 6.4. Không dùng `-Xcomp` để tự động tuyên bố peak

`-Xcomp` yêu cầu compilation-first ở HotSpot và có thể làm thời gian khởi động/compilation rất khác bình thường. Nó **không đồng nghĩa** một JVM đã được warm-up bằng workload đại diện hoặc mọi branch profile đã chín. Không dùng `-Xcomp` là đường tắt tính “maximum possible Java speed”.

### 6.5. Code cache và compiler queues

JIT-compiled machine code nằm trong **code cache**. Nếu code cache bị giới hạn quá mức hoặc compiler queues quá tải, những hot paths có thể mất cơ hội compile/recompile như kỳ vọng; throughput và warm-up profile bị ảnh hưởng.

Có thể dùng `jcmd` để xem các commands VM thực sự hỗ trợ; ví dụ một số HotSpot có thông tin compiler/code cache:

~~~text
jcmd
jcmd <PID> help
jcmd <PID> Compiler.codecache
~~~

**`<PID>` là ký hiệu trong hướng dẫn**, thay bằng process ID thật trước khi chạy, không dán nguyên dấu `< >` vào shell. Tên command và quyền attach phụ thuộc JDK/VM. Không suy đoán một `jcmd` subcommand có ở mọi vendor.

## 7. Đo startup từ ngoài JVM: phải chọn mốc chính xác

Java code bên trong `main` không thể một mình đo đầy đủ **OS launch → JVM initialization → application startup** bằng cách bắt đầu `System.nanoTime()` khi main chạy: các bước trước main đã qua.

### 7.1. Đo toàn thời gian chạy của một CLI process

Bash/Linux/macOS:

~~~bash
time java -cp . WarmupLab
~~~

PowerShell:

~~~powershell
Measure-Command { java -cp . WarmupLab | Out-Null }
~~~

**Lưu ý**: các lệnh trên đo **cả thời gian warmup và measured batches bên trong `main`**, cộng JVM startup và process shutdown. Chúng **không** tách được từng mốc startup/ready/steady state. Trong PowerShell, `Measure-Command` còn có overhead shell/pipeline; nếu so các run, giữ cùng wrapper.

Để đo **time to first response** của service, nên dùng load generator/client có timestamp trước khi gửi request và sau khi nhận kết quả. Để đo **time to readiness**, đọc sự kiện readiness do service thực sự phát trong lifecycle, không chỉ đo lúc port mở hay process tồn tại.

### 7.2. Đo lần chạy mới mỗi lần

Mỗi lần gọi `java ...` là một process mới với một JIT/code cache state mới. Điều này giúp so cold process, nhưng **OS filesystem page cache, JIT artifacts trong shared/runtime facilities, containers và external databases có thể vẫn ấm**. Cold JVM process không đồng nghĩa cold toàn bộ hệ thống.

Nếu thử startup nhiều lần:

1. Cố định **JDK build, image, flags, classpath, environment**.
2. Dùng process mới mỗi lượt, không tái sử dụng một JVM đã phục vụ.
3. Giữ dataset và external dependencies giống nhau, hoặc ghi rõ warm/cold external cache.
4. Chạy đủ repetitions, đảo thứ tự ứng viên để giảm ảnh hưởng OS/noisy neighbor.
5. Báo median, p95/p99, variability và outliers; không chỉ lượt nhanh nhất.

**Không xoá OS caches trên production** hoặc thực hiện thao tác hệ thống nguy hiểm để “làm lạnh” bài test. Có thể tổ chức node/VM được kiểm soát riêng hoặc benchmark warm-OS-cache và cold-OS-cache thành hai scenario có ghi nhãn.

## 8. Vì sao warm-up không chỉ là “chạy vòng lặp đủ nhiều lần”?

### 8.1. Profile mismatch

Warm-up chạy 100% input ngắn, đo input rất dài có nhánh/allocations khác. JIT có thể đã optimize code chủ yếu cho hot branch ngắn, còn đường dài vẫn lạnh hoặc cần deoptimization. Hãy giữ **data shape và type distribution đại diện**.

### 8.2. Dead-code elimination

~~~java
for (int i = 0; i < 1000000; i++) {
    Math.sqrt(123.456);
}
~~~

Đây chỉ là **đoạn minh họa**, không có class/main. Kết quả `Math.sqrt` không được sử dụng; optimizer có thể bỏ lời gọi hoặc cả vòng lặp. Đo thời gian của đoạn này không đo đúng 1 triệu lần căn bậc hai.

### 8.3. Constant folding

~~~java
double value = 100 * 200.0;
~~~

Compiler có thể tính giá trị hằng ngay từ lúc biên dịch/optimization. Benchmark “một triệu phép nhân” với các operands compile-time constant có thể trở thành benchmark assignment, không phải phép nhân runtime.

### 8.4. Escape analysis và allocation elimination

Một object được tạo ra rồi chỉ dùng nội bộ, không thoát khỏi phạm vi mà optimizer quan sát được, có thể được scalar replace hoặc loại bỏ allocation trong một số trường hợp. Nếu ứng dụng thật có object sống qua request boundaries, benchmark với object dễ escape-analyze có thể không đại diện allocation pressure production.

### 8.5. Inlining và call-site profile

Một method rất nhanh khi JIT inline vào caller có thể chậm hơn khi đo như một virtual interface call với nhiều concrete receiver types. Cần mô phỏng **call-site polymorphism** của workload thực.

### 8.6. Warm-up tác động GC/lifetime

Heap occupancy, object ages, young/old generation và GC frequency có thể thay đổi sau hàng nghìn requests. Một benchmark chỉ xét JIT compilation nhưng lại có object allocations lớn có thể đo xen lẫn GC; cần bật GC logs để giải thích.

### 8.7. OS, CPU và JIT background compilation

OS scheduler, CPU turbo/frequency scaling, thermal throttling, CPU affinity, process priority, heap page faults và compiler threads chạy background đều gây noise. Những yếu tố này có thể tạo ra “tăng tốc” hoặc “chậm đi” mà code machine-level không đổi.

**Một phép đo tốt luôn mô tả workload và environment, không chỉ mã Java.**

## 9. JMH: công cụ phù hợp hơn tự viết vòng `nanoTime()`

**Java Microbenchmark Harness (JMH)** là công cụ từ dự án OpenJDK để benchmark những đoạn Java nhỏ. Nó hỗ trợ forks, warm-up iterations, measurement iterations, modes, output units, parametrization và một số cơ chế tránh sai lệch phổ biến.

Ví dụ **minh họa API JMH, không phải file Java SE 17 độc lập** vì cần JMH dependencies/build plugin:

~~~java
@State(Scope.Thread)
public class ComputeBenchmark {
    private int seed = 12_345;

    @Benchmark
    public long compute() {
        return transform(seed++);
    }

    private static long transform(int s) {
        long x = s;
        for (int i = 0; i < 256; i++) {
            x = (x ^ (x >>> 13)) * 31 + i;
        }
        return x;
    }
}
~~~

Để biên dịch đoạn JMH này cần `org.openjdk.jmh.annotations.*` và thư viện JMH trên classpath; snippet trên chỉ cho thấy cấu trúc `@State`/`@Benchmark`, **không hứa `javac` của JDK trần compile được**.

**Tại sao forks quan trọng?** Mỗi fork có JVM riêng, tránh chạy thử nghiệm ứng viên B trên heap/code cache đã bị ứng viên A warm-up trong cùng process. JMH warm-up cũng có thể cấu hình theo thời gian/iterations để xác định rõ phase nào được bỏ qua khỏi số đo chính.

**Nhưng JMH không giải quyết mọi thứ:**

- Microbenchmark không tự mô phỏng HTTP queueing, DB, network, thread contention hoặc load distribution.
- Warm-up trong JMH cũng phải có input distribution đúng.
- JMH không tự định nghĩa business SLO/production readiness.
- Benchmark tốt vẫn cần nhiều forks/repetitions và thống kê hợp lý.

Với service, sử dụng **JMH cho hàm nóng** và **end-to-end load testing cho application latency/throughput**.

## 10. Throughput, average latency và tail latency trong warm-up

Giả sử một service vừa start:

~~~text
Window 1 (0–10s): throughput  700 req/s, p99 300ms
Window 2 (10–20s): throughput 1400 req/s, p99 120ms
Window 3 (20–30s): throughput 1800 req/s, p99  75ms
Window 4 (30–40s): throughput 1850 req/s, p99  72ms
Window 5 (40–50s): throughput 1830 req/s, p99  78ms
~~~

**Bảng số chỉ minh họa, không phải kết quả benchmark đo thật.** Bạn có thể xem windows 4–5 là những ứng viên trạng thái ổn định, nhưng **không thể xác nhận chỉ từ năm số này**: còn phải xem offered load, CPU, GC, JIT events, memory retention và tính thống kê.

Nếu chỉ lấy **average throughput của toàn 50 giây**, bạn sẽ đo cả warm-up lẫn thời gian gần ổn định. Đó có thể là đúng nếu bài toán hỏi **số request xử lý trong vòng đời ngắn**, nhưng lại là phép đo sai nếu muốn so **steady-state capacity**.

Ngược lại, nếu loại bỏ mọi lần chạy đầu tiên rồi báo **best stable 10 seconds**, có thể tạo cherry-picking và lạc quan quá mức. Cần tiêu chí tách warm-up/measurement được định trước.

### Peak throughput có thể phá vỡ latency SLO

Một service có thể đạt **3.000 req/s tối đa** nhưng p99 latency lúc ấy 900 ms vì queueing. Nếu yêu cầu p99 ≤ 100 ms, có thể throughput **bền vững đúng SLO** chỉ là 2.100 req/s. Do đó:

- **Unconstrained peak throughput:** mức ops/s cao nhất đo được, có thể tại điểm latency xấu hoặc error rate cao.
- **SLO-constrained sustainable throughput:** tải cao nhất đạt p99/error/memory/CPU constraints trong khoảng đo đủ lâu.
- **Steady-state throughput:** mức xử lý trong một regime tương đối ổn định; có thể thấp hơn peak.
- **Startup average throughput:** số work trong cửa sổ bắt đầu ở process launch, gồm cả cold/warm transitions.

Đừng viết bảng performance chỉ một cột `ops/s` mà không ghi phase, SLO và thời gian đo.

### Coordinated omission

Load generator **closed-loop** gửi request mới sau khi request cũ hoàn thành, nên khi server tạm dừng, client có thể phát yêu cầu ít hơn, che tail latency do queueing. Với mục tiêu đo một arrival rate cố định, dùng workload **open-loop/rate-controlled**, ghi offered requests, completed, failed và timed out; áp histogram recording theo thời điểm scheduled arrival phù hợp để giảm coordinated omission.

Không loại các timeouts/failed requests rồi chỉ công bố p99 của requests thành công, vì service xử lý chậm/đánh rơi requests có thể nhìn “nhanh” một cách giả.

## 11. JIT logs và JFR để kiểm chứng warm-up thật

### `-XX:+PrintCompilation`

Phù hợp cho lab, kiểm tra **method nào** được biên dịch và tier activity. Nhưng output có thể rất nhiều và làm thay đổi timing; chạy riêng một lượt **diagnostic**, còn các lượt benchmark dùng logging nhất quán và overhead được kiểm soát.

### Java Flight Recorder

JFR trên JDK 17 có thể ghi các events về compilation, deoptimization, CPU samples, GC, allocation, classes và thread activity khi **được bật theo settings phù hợp**. Các event như `jdk.Compilation` hoặc `jdk.Deoptimization` có thể hữu ích tùy JDK build và recording configuration. **Không mặc định tất cả events đều được bật** trong `default.jfc`/`profile.jfc`.

Ví dụ khởi động recording cho một application thật:

~~~bash
java '-XX:StartFlightRecording=filename=warmup.jfr,settings=profile' \
  -jar app.jar
~~~

Lệnh minh họa này cần `app.jar` thật của ứng dụng; với `WarmupLab`, có thể dùng:

~~~bash
java '-XX:StartFlightRecording=filename=warmup.jfr,settings=profile' WarmupLab
~~~

Xem metadata/events bằng:

~~~bash
jfr summary warmup.jfr
jfr print --events jdk.Compilation,jdk.Deoptimization warmup.jfr
~~~

JFR recording ngắn có thể ít hoặc không có event mong muốn do threshold/settings. Không dùng “không có event” như bằng chứng “JIT hoàn toàn không chạy” khi chưa xác nhận event enablement.

### GC và class loading

~~~bash
java '-Xlog:gc=info,class+load=info:file=runtime.log:uptime,level,tags' WarmupLab
~~~

Log có thể cho biết GC events/class loading activity trong process. **Class load không đồng nghĩa class initialization**; GC elapsed pause cũng không phải toàn bộ stop interval. Khi cần safepoint timings, thêm tag `safepoint=info` nếu JVM hỗ trợ.

### `CompilationMXBean` có giới hạn

`java.lang.management.CompilationMXBean` có method `getTotalCompilationTime()`, nhưng JVM có thể không hỗ trợ monitoring phần này (API cho biết khi không hỗ trợ). Giá trị tổng cũng **không tự chứng minh mọi hot methods đã ở tier 4**, hay ứng dụng đạt steady state. Không dùng tổng compilation milliseconds làm điều kiện readiness đơn lẻ.

## 12. Thiết kế benchmark ba bài riêng thay vì một số tổng

### Bài A — Cold-start / readiness latency

**Câu hỏi:** một instance mới mất bao lâu từ process launch đến khi xử lý được yêu cầu đầu tiên ở mức chất lượng chấp nhận?

- Mỗi sample: **process JVM mới**.
- Mốc đo: timestamp từ ngoài process; xác định rõ probe/response thành công.
- Giữ cùng JDK, image, memory/CPU budget, dependency state.
- Đo cold image/cache scenario riêng nếu quan trọng cho autoscaling/serverless.
- Báo p50/p95/p99 startup + first response; ghi failure rate.

**Không dùng** `WarmupLab.avgBatchMs` làm time-to-ready.

### Bài B — Time to steady state

**Câu hỏi:** sau bao lâu ứng dụng đạt throughput/latency tương đối ổn định dưới workload đại diện?

- Phát requests theo arrival rate kiểm soát, bắt đầu từ mốc ứng dụng sẵn sàng.
- Đo liên tục qua những fixed windows (ví dụ mỗi 5 hoặc 10 giây).
- Kiểm tra throughput, p99, JIT/deopt events, GC, CPU và memory.
- Đặt tiêu chí ổn định trước experiment, ví dụ ba cửa sổ liên tiếp không có trend lớn và SLO pass; ngưỡng phải phù hợp variability.
- Báo **thời gian đạt điều kiện** và tỷ lệ các runs thực sự đạt, không chỉ mẫu chạy đẹp.

**Cẩn trọng:** việc runtime không còn compile một lúc **không đủ** để gọi steady state nếu DB cache vẫn tăng, live set vẫn trôi hoặc load generator đang thay đổi offered rate.

### Bài C — Sustainable steady-state throughput / peak

**Câu hỏi:** trong cùng ngân sách CPU/memory, dưới SLO đã đặt, hệ thống phục vụ được tải ổn định bao nhiêu?

- Warm-up với representative traffic.
- Dùng nhiều offered rates: dưới target, target, gần saturation.
- Không xóa request timeout/failed khi tính kết quả.
- Đo p95/p99/p99.9, achieved throughput, CPU, RSS/cgroup và GC/JIT cost.
- Chạy long soak để thấy rare GC, native retention, thermal/CPU throttling, leaks.
- Công bố **SLO-constrained sustained throughput** và peak không ràng buộc như hai cột riêng.

Nếu ứng dụng sống ngắn như CLI chạy 2 giây rồi kết thúc, bài B/C có thể ít ý nghĩa hơn **total job elapsed** ở cold process. Chọn KPI theo vòng đời sản phẩm.

## 13. Warm-up trong hệ microservices/autoscaling

Ứng dụng Java chạy theo deployment có một bài toán đặc biệt: không thể lấy steady-state capacity của **một instance đã chạy 3 giờ** để suy ra cold instance mới tự động phục vụ full traffic.

### Readiness, liveness và traffic shifting

- **Liveness:** process/service còn hoạt động về mặt kiểm tra cơ bản.
- **Readiness:** instance đủ điều kiện để **nhận traffic** theo giao thức deployment; không mặc định mọi JIT path đã peak.
- **Warm-up status nội bộ:** optional signal theo route/request mix và khả năng phục vụ workload mục tiêu.
- **Traffic ramp:** tăng tải dần để tránh nhiều cold instances cùng lúc gây request latency spike hoặc compiled-code CPU pressure.

Không nên trì hoãn readiness vô hạn chỉ để chờ “C2 compile hết mọi thứ”, vì đó không phải mốc JVM có thể đảm bảo. Thay vào đó, đặt **acceptance criteria hữu hạn** cho chức năng cần sẵn sàng, preload những dependencies quan trọng, warm representative hot routes nếu đáng giá và dùng traffic ramp có giám sát.

### Scale-out và warm-up cost

Khi workload đột ngột tăng, autoscaler tạo nhiều pods/JVM mới. Chúng có thể cùng:

- Nạp classes và tạo connection pools.
- Compile code trong background, tranh CPU quota với requests.
- Làm nóng DB/cache, tạo burst tải vào downstream.
- Gặp GC heap occupancy transitions.
- Chỉ đạt throughput thấp hơn trong vài chục giây đầu.

Nếu capacity planner lấy **steady-state req/s mỗi pod** nhân ngay với số pods mới, dự báo công suất thời gian đầu có thể quá lạc quan. Cần profile **warm-up ramp curve** và autoscaler readiness strategy phù hợp.

### Serverless, short-lived jobs và peak JIT

Một CLI task 300 ms có thể kết thúc trước khi nhiều hot paths được tối ưu mạnh. Trong workload như vậy, **minimize class loading/bootstrapping** có thể quan trọng hơn C2 peak throughput. Đó là lý do “cấu hình tăng peak throughput” không mặc nhiên giúp startup.

## 14. Nhiều kịch bản cần phân biệt khi báo kết quả

### Kịch bản 1 — Bản A startup nhanh hơn nhưng đỉnh chậm hơn

Bản A ready sau 700 ms và xử lý ổn định 2.000 req/s; bản B ready sau 1.8 giây nhưng sau warm-up xử lý 2.500 req/s. Nếu serverless chỉ chạy 4 giây, A có thể thắng total work; nếu chạy 24 giờ với CPU budget phù hợp, B có thể thắng. Không có kết luận chung khi chưa có **lifecycle và SLO**.

### Kịch bản 2 — Request đầu tiên chậm do lazy class initialization

Một class chỉ được load/init khi route `/generate` lần đầu được gọi. Readiness probe `/health` trả ngay, nhưng `/generate` request đầu mất thêm chi phí. Cần warm-up đúng route hoặc đo cold-path riêng; đừng quy mọi chi phí cho JIT.

### Kịch bản 3 — Warm-up input khác production

Trong warm-up, 100% requests sử dụng JSON nhỏ. Production chứa 30% payload lớn và nhiều receiver types. C2 optimization có thể thiên về profile cũ, workload thực làm đường chạy khác; warm-up 5.000 requests vẫn không đảm bảo representative.

### Kịch bản 4 — Đỉnh đẹp nhưng GC/CPU không bền

Thí nghiệm 30 giây đạt 100k ops/s, nhưng sau 30 phút heap live set tăng và Full GC xuất hiện. Đỉnh 30 giây không phải sustainable throughput; cần soak để phát hiện nonstationarity/memory leak.

### Kịch bản 5 — C1-only làm first request cải thiện

Nếu `TieredStopAtLevel=1` làm first response nhanh hơn một lần chạy, chưa được kết luận flag luôn tốt. Có thể giảm compilation overhead ban đầu nhưng làm steady peak xấu hơn. Chạy A/B nhiều lần với startup và sustained SLO riêng.

### Kịch bản 6 — `-Xint` rất chậm

Điều này cho thấy JIT mang lại lợi ích với hot kernel cụ thể, không chứng minh production cold start là interpreter-only, cũng không chứng minh toàn bộ application speedup bằng kernel speedup.

### Kịch bản 7 — JIT log còn compilation trong steady measurement

Có thể có route ít dùng mới nóng lên hoặc runtime bù đắp recompilation. Xem magnitude và sự liên quan với p99; không nhất thiết mọi compilation đều vi phạm steady state, nhưng một burst lớn có thể cần phân đoạn lại window.

### Kịch bản 8 — CPU quota khiến warm-up kéo dài

JIT compiler threads và Java workers tranh một quota 1 vCPU; instance có thể cần nhiều thời gian hơn để đạt candidate steady-state so với benchmark trên 16 cores không giới hạn. Đo trong cùng container CPU quota và ghi CPU throttling; `ActiveProcessorCount` không tự đặt cgroup quota.

## 15. Checklist một kết quả warm-up/peak có thể tin

- [ ] Định nghĩa rõ `process start`, `first useful result`, `ready`, `warm-up end`, `measurement start/end`.
- [ ] Ghi JDK vendor/build, VM flags, OS, vCPU quota, heap và memory cap.
- [ ] Không lẫn cold startup với steady-state throughput.
- [ ] Representative request mix, object lifetime và data shapes.
- [ ] Không bỏ failures/timeouts/dropped requests khỏi latency accounting.
- [ ] Có nhiều process forks/repetitions và randomized run order.
- [ ] Có warm-up time windows/criteria từ trước; không chọn thủ công đoạn nhanh nhất.
- [ ] Kiểm tra JIT compilation/OSR/deoptimization khi phân tích.
- [ ] Kiểm tra GC, class loading, pools, cache, external dependencies và CPU.
- [ ] Phân biệt `-Xint` diagnostic với cold startup thực tế.
- [ ] Dùng JMH cho microbenchmark, production-like load generator cho service.
- [ ] Báo achieved vs offered load; tránh coordinated omission.
- [ ] Báo p50/p95/p99/p99.9 cùng throughput và resource footprint.
- [ ] Có cold-path, warm-up ramp và long steady-state experiments riêng.
- [ ] Không kết luận từ một JVM ngắn có 4 warm-up batches rằng đã C2 peak.
- [ ] Mọi output thời gian và compilation logs được gắn nhãn **environment-dependent**.

## 16. Câu hỏi tự kiểm tra

1. **Warm-up là gì?** Quá trình runtime/application thay đổi trạng thái khi thực thi workload: JIT profiling/compilation, class/init, caches, pools, heap/GC...
2. **Một method Java sau 10.000 calls chắc chắn ở C2?** Không; VM policy, JIT tiers, hotness, compiler queue và flags khác nhau.
3. **Một vòng loop rất dài nhưng chỉ gọi một method một lần có thể được JIT không?** Có; HotSpot có thể dùng OSR/backedge hotness.
4. **`-Xint` tương đương first request của JVM JIT mặc định?** Không; nó chủ động tắt JIT.
5. **C1 và C2 có mục tiêu giống nhau?** Không hoàn toàn; C1 compile nhanh hơn, C2 có thể tối ưu sâu hơn, đổi lại compilation cost.
6. **Compiled code có luôn tồn tại bất biến tới process exit?** Không; có deoptimization, code invalidation, recompile và code cache management.
7. **Readiness thành công đồng nghĩa JIT đã peak?** Không.
8. **Steady state có phải flag JVM có thể kiểm tra 1 lần là biết?** Không; cần nhiều metrics/windows và định nghĩa workload.
9. **Peak throughput có chắc đạt request p99 SLO?** Không; peak có thể tại saturation với queueing/latency xấu.
10. **Warm-up chỉ bằng 5 giây có được JVM bảo đảm không?** Không.
11. **JIT compilation log im lặng có chứng minh không còn JIT?** Không nếu chưa kiểm tra settings và timing.
12. **Một benchmark chứa phép tính không dùng kết quả đáng tin không?** Không; dead-code elimination có thể bỏ work.
13. **Tại sao dùng process forks?** Để mỗi thí nghiệm có trạng thái JVM/JIT/heap mới.
14. **`time java WarmupLab` đo điều gì?** Tổng OS process invocation đến exit, gồm startup + toàn bộ work của main + shutdown/overhead.
15. **JMH có thay thế HTTP end-to-end load test không?** Không; JMH đo micro-workload, không tự phản ánh mạng/queueing/DB.
16. **Nếu warm-up route A nhưng production route B, JIT đã đại diện chưa?** Chưa chắc; type/branch/class paths có thể khác.
17. **GC có góp phần vào warm-up không?** Có; live-set/heap occupancy/GC cadence có thể thay đổi.
18. **Một số `avgBatchMs` trong lab là p99 request latency?** Không, chỉ là trung bình duration các batches đã chọn.
19. **Khi JDK 17 benchmark code trên JDK 24 với `--release 17`, runtime đã là JDK 17 chưa?** Chưa; bytecode/API target là 17, compiler/runtime VM vẫn JDK 24.
20. **Với CLI sống 0.3 giây, ưu tiên startup hay C2 peak?** Tuỳ SLO; startup/total job time thường quan trọng hơn peak của hot method.
21. **CDS có bảo đảm C2 peak ngay khi process mới chạy?** Không.
22. **Tại sao cần CPU quota giống production?** Compiler threads và app threads tranh tài nguyên; warm-up/throughput thay đổi theo quota.
23. **Một app đạt p99 ổn định nhưng heap live set cứ tăng?** Chưa đáng xem là trạng thái ổn định lâu dài; cần soak và leak analysis.
24. **Cấu hình có throughput cao nhưng vi phạm memory/CPU/latency budget có phải thắng?** Không; cần xem SLO-constrained sustained performance.

## 17. Tài liệu chuẩn và tham khảo sâu

- Oracle JDK 17 `java` launcher: `-Xint`, `-XX:+PrintCompilation`, `-XX:-TieredCompilation`, `-Xlog`, JFR và các VM flags: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Oracle — Understanding Java JIT Compilation with JITWatch (C1/C2, tiered compilation, optimization): https://www.oracle.com/technical-resources/articles/java/architect-evans-pt1.html
- OpenJDK HotSpot — JIT compilation policy và compiler infrastructure (JDK source): https://github.com/openjdk/jdk17/tree/master/src/hotspot/share/compiler
- Oracle JDK 17 `jcmd`: compiler diagnostics, JFR và process introspection: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- Oracle JDK 17 `jfr`: inspection của Java Flight Recorder recordings: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- Java SE 17 JFR API/module: https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html
- Java SE 17 `CompilationMXBean`: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/CompilationMXBean.html
- OpenJDK Java Microbenchmark Harness (JMH): https://openjdk.org/projects/code-tools/jmh/
- Oracle JDK 17 Garbage Collection Tuning Guide (hiệu ứng GC, throughput/latency): https://docs.oracle.com/en/java/javase/17/gctuning/

**Kết luận kỹ thuật:** **Khởi động nhanh không đồng nghĩa hiệu năng đỉnh cao; hiệu năng đỉnh cao không đồng nghĩa trạng thái ổn định; trạng thái ổn định không đồng nghĩa đạt SLO**. HotSpot đi từ interpreter qua profiling và tiered JIT, có thể OSR, deoptimize và recompile, trong khi class loading, heap/GC, caches và dependencies cùng ảnh hưởng warm-up. Hãy đo **cold process startup, time-to-ready, warm-up ramp, steady-state throughput và SLO-constrained peak** thành những KPI rõ ràng, dùng nhiều repetitions, workload đại diện, JMH/JFR và tracing phù hợp. Đừng coi một batch chạy nhanh hoặc một JIT compilation event là bằng chứng đã đạt hiệu năng tối ưu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-007">Giả định tối ưu, deoptimization và biên dịch lại khi giả định không còn đúng</a>

<details>
<summary>Xem chi tiết</summary>

# Giả định tối ưu, deoptimization và biên dịch lại khi giả định không còn đúng

**JIT compiler** (Just-In-Time) của JVM không chỉ dịch bytecode thành mã máy. Trong HotSpot, JIT có thể **quan sát chương trình đang thực thi**, học kiểu đối tượng hay gặp, nhánh thường được đi qua và những method đang “nóng”, rồi sinh mã máy chuyên biệt để chạy nhanh hơn. Điểm tinh tế: một số phép tối ưu dựa trên **giả định có vẻ đúng ở thời điểm biên dịch nhưng không phải chân lý bất biến**.

Ví dụ: trong 100.000 lần gọi `operator.apply(value)`, receiver đều thuộc class `AddOne`. JIT có thể đặt một **type guard** và inline lời gọi như `AddOne.apply`, thay vì thực hiện virtual dispatch theo cách tổng quát. Nhưng ở lần thứ 100.001, chương trình có thể truyền `TimesTwo`; JVM vẫn phải thực thi đúng `TimesTwo.apply`. Một cách xử lý là **deoptimization**: rời phiên bản mã máy quá chuyên biệt, tái tạo trạng thái Java cần thiết và tiếp tục bằng interpreter hoặc một mức compiled code phù hợp; JVM có thể thu thập profile mới và **biên dịch lại** với giả định phù hợp hơn.

> **Speculative optimization không có nghĩa JVM đoán rồi chấp nhận kết quả sai.** Mọi phép tối ưu hợp lệ phải bảo toàn hành vi mà Java Language Specification và JVM Specification yêu cầu, kể cả khi assumption thất bại.

Bài học thuộc nhóm **Thông dịch và JIT**. Trọng tâm là phân biệt **profiling → speculation → guard/dependency → deoptimization → interpreter/fallback → recompilation**, hiểu uncommon traps, type profile và class hierarchy assumptions, phân biệt **mã biên dịch bị invalidated** với **object Java bị garbage-collected**, và thực hành trên **Java 17/HotSpot** mà không bịa số lượng JIT events.

## 1. Java bytecode, interpreter và compiled nmethod

Java source thường được `javac` dịch thành **class file bytecode**. Tại runtime, HotSpot có thể:

- Thực thi bytecode bằng **interpreter**.
- Thu thập **invocation counts, backedge counts và type/branch profiles** khi chương trình chạy.
- Biên dịch những method/loops “hot” bằng **C1** (client compiler, có các mức instrumentation/optimization) hoặc **C2** (server optimizing compiler), tùy cấu hình tiered compilation.
- Chuyển từ interpreted sang compiled code, hoặc giữa các mức compiled code, khi profile đủ thông tin và biên dịch hoàn tất.
- Nếu compiled code không còn an toàn/hợp lệ hoặc một nhánh “hiếm” bất ngờ xuất hiện, **deoptimize**, chuyển control sang cách thực thi vẫn giữ Java semantics.
- Có thể **reprofile và recompile** nếu method còn nóng và JVM nhận thấy phiên bản code khác sẽ phù hợp hơn.

**Tên `nmethod`** trong HotSpot thường chỉ một đơn vị **native machine code** được sinh để thực thi Java method (bao gồm cả các phiên bản tối ưu/hỗ trợ OSR phù hợp). Nó nằm trong **Code Cache**, không phải một “Java source method” mới và không phải một instance trên Java heap.

```text
Java source
    │ javac
    ▼
Java bytecode  ───────────────────────────────────────┐
    │                                                │
    ▼                                                │
Interpreter / tiered runtime                         │
    │ thu thập profile (hotness, types, branches)     │
    ▼                                                │
C1 / C2 JIT compilation                              │
    │                                                │
    ▼                                                │
Optimized machine code (nmethod)                     │
    │                                                │
    ├── giả định còn đúng → đường chạy nhanh           │
    │                                                │
    └── guard/dependency invalidated                 │
            │                                        │
            ▼                                        │
        DEOPTIMIZATION                               │
          ├─ dựng lại Java-level execution state     │
          ├─ resume interpreter/fallback             │
          └─ profile/recompile khi thích hợp ─────────┘
```

**Cảnh báo:** Đây là hình ảnh logic cho việc học. Compiler decisions, code entrypoints, safepoints, on-stack replacement và khả năng quay về một mức compiled code khác có chi tiết implementation-specific; không có quy tắc “mỗi deopt chắc chắn có một lần compile mới ngay lập tức”.

## 2. Tối ưu có chứng minh và tối ưu dựa trên giả định

Không phải mọi JIT optimization đều speculative.

| Loại | Ý tưởng | Khi nào cần fallback? |
| --- | --- | --- |
| **Tối ưu được chứng minh an toàn** | Compiler biết phép rút gọn luôn giữ semantics, ví dụ constant folding hợp lệ hoặc elimination dưới conditions đã được chứng minh | Không cần deopt chỉ vì giả định profile về nhánh không còn đúng |
| **Speculative optimization theo type profile** | “Phần lớn receivers tại call site này là `AddOne`” | Nếu receiver thuộc type khác, guard phải xử lý đường tổng quát hoặc deopt |
| **Giả định từ class hierarchy** | “Hiện tại chưa có subtype/override làm thay đổi tính duy nhất của target” | Khi class loading/linking làm dependency invalid, VM phải ngừng dùng compiled assumptions không còn đúng |
| **Nhánh hiếm, uncommon path** | “Thông thường điều kiện không bao giờ true trong profile quan sát” | Khi path xuất hiện cần xử lý đúng; có thể dùng uncommon trap |
| **Hoisting/check elimination có guard** | “Đối tượng/giá trị thỏa điều kiện này ở vùng code tối ưu” | Nếu guard fails, tránh thực thi tiếp trên cơ sở giả định sai |
| **Escape-analysis-based optimization** | Allocation/lock có thể được giảm hoặc loại bỏ nếu không ảnh hưởng hành vi quan sát | Tại deopt, JVM cần giữ Java state tương đương, có thể materialize đối tượng ảo theo metadata |

Một giả định **đã được chứng minh** dựa trên quy tắc ngôn ngữ/bytecode không cùng bản chất với giả định **đúng phần lớn do profile**. Lấy `-XX:-TieredCompilation` hoặc tắt JIT để so sánh không làm Java bytecode đổi semantics; nhưng nó có thể làm thay đổi cách runtime thu thập profile, thời gian warm-up và hiệu năng.

### 2.1. Speculation không phải đoán bừa

C2 không “đoán class bất kỳ”. Nó có các nguồn thông tin:

- **MethodData/profile information** thu thập tại một số bytecode sites.
- Tần suất các target receiver types tại `invokevirtual`/`invokeinterface`.
- Nhánh thường đúng/sai ở một điều kiện.
- Lịch sử throw của một số checks, ví dụ null/type checks.
- Trạng thái class hierarchy/dependency mà VM có thể theo dõi.
- Những invariant được chứng minh bằng phân tích bytecode và các tối ưu của compiler.

Dù một profile nói “100% calls là type A” trong giai đoạn warm-up, nó chỉ mô tả quá khứ. JVM phải duy trì **guard hoặc dependency** phù hợp để chương trình còn đúng khi tương lai xuất hiện type B.

## 3. Type profiling, monomorphic và polymorphic call site

Xét Java:

```java
interface Operation {
    int apply(int value);
}

final class AddOne implements Operation {
    public int apply(int value) { return value + 1; }
}

final class TimesTwo implements Operation {
    public int apply(int value) { return value * 2; }
}

static int evaluate(Operation op, int value) {
    return op.apply(value);
}
```

Đây là **snippet minh họa**. Một file chạy độc lập nằm ở mục 10.

- **Monomorphic profile:** ở một call site, runtime chủ yếu quan sát **một receiver type**. JIT có thể devirtualize và inline target.
- **Bimorphic/polymorphic profile:** site quan sát hai hoặc nhiều receiver types; compiler có thể chọn một số guarded inlines, type tests, hoặc virtual dispatch tùy thresholds/heuristics.
- **Megamorphic site:** nhiều receiver types khiến việc chuyên biệt hóa bằng hàng loạt guarded inlines có thể kém hiệu quả; generic dispatch có thể hợp lý.

Các từ “mono/bi/mega” mô tả đặc điểm của **call site và profile** trong một ngữ cảnh, không phải thuộc tính vĩnh viễn của một interface. Cùng method interface được gọi tại nhiều bytecode sites có thể cho profiles khác nhau.

### 3.1. Minh họa guard-based speculative inlining

Giả sử mọi `evaluate` lúc đầu nhận `AddOne`:

```text
Bytecode:
  load op, value
  invokeinterface Operation.apply(I)I

Mã tối ưu MÔ HÌNH:
  if (receiver.type == AddOne) {
      return value + 1;         // inline AddOne.apply
  } else {
      uncommon_trap_or_slow_path(); // không được trả bừa value + 1
  }

Lần đầu gặp TimesTwo:
  fast-path guard không đúng
  → fallback/deopt phải đảm bảo trả TimesTwo.apply(value)
```

Đây là **pseudocode diễn giải chiến lược**, không phải Java source/assembly thực tế của một bản HotSpot. C2 có thể chọn slow path thay cho uncommon trap hoặc không speculate tại site đó; có thể inline cả hai targets nếu profile/heuristics thích hợp.

### 3.2. `final` và `sealed` có bảo đảm không deopt?

Không. Một `final class` không thể bị subclass theo Java rules, có thể làm một số phân tích type dễ hơn. Nhưng một method gọi virtual có thể vẫn nhận những object classes khác nhau cùng triển khai interface, hoặc compilation phụ thuộc những assumptions khác. `sealed` giới hạn direct permitted subclasses theo ngôn ngữ nhưng không tự biến mọi dynamic call thành monomorphic.

Compiler có thể chứng minh target call statically hoặc dùng speculation tùy thông tin; không suy “method trong final class luôn inline” hay “sealed class thì chắc chắn không có deoptimization”.

## 4. Nhánh hiếm (uncommon branch) và uncommon trap

Một method thường xuyên gọi với số dương:

```java
static int normalize(int n) {
    if (n < 0) {
        return -n;
    }
    return n + 1;
}
```

Nếu trong thời gian dài chỉ xuất hiện `n >= 0`, JIT có thể tối ưu cho nhánh chính và giảm gánh nặng của nhánh âm. Một chiến lược HotSpot C2 là **uncommon trap**: khi nhánh/điều kiện ít gặp được kích hoạt, compiler cho JVM chuyển từ optimized code sang runtime path có thể thực hiện lại logic bytecode đúng.

**Điểm quan trọng:** có `if` và dữ liệu chủ yếu dương **không đảm bảo** C2 sẽ tạo uncommon trap. Compiler có thể sinh hai nhánh máy bình thường, dùng conditional move, hoặc tối ưu theo cách khác. Đây là ví dụ **tình huống có khả năng** tạo speculation, không phải hứa về trace của mọi JVM.

### 4.1. Guard failure không nhất thiết là lỗi ứng dụng

Một guard kiểm tra receiver type không đúng không đồng nghĩa `ClassCastException` trong Java source. Đó có thể là tín hiệu **JIT-generated code cần quay về phiên bản tổng quát hơn**.

```text
Fast path giả định: n >= 0
                   │
                   ├─ true  → optimized calculation
                   │
                   └─ false → uncommon trap / general fallback
                               │
                               ▼
                        Java negative branch
                        chạy đúng kết quả
```

Với `normalize(-7)`, kết quả phù hợp Java semantics là `7` (vì `-(-7)==7`), dù method trước đó được biên dịch tối ưu cho các số dương. Deoptimization là **cơ chế giữ correctness**, không có nghĩa application phải catch một Java exception tên “DeoptimizationException”.

### 4.2. Deoptimization synchronous và asynchronous

Hai trường hợp thường được phân biệt trong HotSpot discussions:

- **Synchronous deoptimization:** execution thực sự tới một guard/uncommon path và yêu cầu runtime hỗ trợ deopt. Việc kích hoạt gắn với đang thực thi compiled code.
- **Asynchronous invalidation/deoptimization:** một sự kiện từ thread khác, ví dụ class loading làm hỏng dependency về class hierarchy, khiến compiled method không còn được dùng và active frames phải được xử lý theo cơ chế VM.

“Asynchronous” ở đây không có nghĩa arbitrary instruction có thể bị thay đổi vô điều kiện; JVM phối hợp các điểm an toàn, dependency tracking và metadata để chuyển trạng thái hợp lệ. Tài liệu chi tiết là **HotSpot implementation**, không phải public method `Thread.deoptimize()`.

## 5. Class Hierarchy Analysis (CHA) và assumption bị vô hiệu hóa

C2 có thể quan sát ở thời điểm compile rằng một interface/abstract method chỉ có một receiver target khả dĩ trong hierarchy đã được nạp/liên kết, hoặc target phù hợp đã được xác định bằng profile. Nó có thể tận dụng điều này để inline và giảm virtual dispatch.

Một dependency dựa trên **tập classes hiện có** có thể không còn đúng sau khi JVM nạp thêm class hợp lệ.

```text
Ban đầu:
  interface Formatter
      └── PlainFormatter

C2 có thể chuyên biệt call formatter.format(...) cho PlainFormatter
khi profile/hierarchy cho phép.

Sau đó:
  interface Formatter
      ├── PlainFormatter
      └── HtmlFormatter       (mới được nạp/liên kết)

Nếu assumptions phụ thuộc unique target không còn đúng:
  JVM invalidates/deoptimizes compiled code liên quan.
  Call tiếp theo thực hiện đúng dispatch theo runtime type.
```

**Không khẳng định** mọi class load đều khiến toàn JVM deopt. JVM theo dõi **dependencies cụ thể**, và chỉ những nmethods có assumptions bị ảnh hưởng mới cần xử lý thích hợp. Nếu compiled code vốn có guard và slow path xử lý type mới, nó có thể vẫn hợp lệ.

### 5.1. Ví dụ hierarchy không có nghĩa nạp class là exception

Các class được nạp qua class loaders, reflection hoặc quá trình chạy bình thường. Class loading hợp lệ không khiến Java program phải thất bại chỉ vì JIT từng thấy ít type hơn. Correct implementation giữ phép `invokevirtual`/`invokeinterface` đúng theo ngữ nghĩa bytecode.

Các lỗi `NoClassDefFoundError`, `LinkageError` hoặc `ClassCastException` nếu xảy ra có **nguyên nhân ngôn ngữ/linkage thực**, không phải “JIT thấy class mới nên ném exception”.

### 5.2. Recompilation sau khi hierarchy thay đổi

Sau invalidation, HotSpot có thể:

1. Ngừng cho phiên bản nmethod cũ nhận thêm lời gọi (`not entrant`).
2. Xử lý các compiled frames đang active theo quy tắc deoptimization.
3. Tiếp tục interpreter hoặc code thích hợp.
4. Thu thập profile mới với class A và B.
5. Khi method vẫn đủ “hot” và compiler policy cho phép, biên dịch lại; phiên bản mới có thể chứa **hai guarded targets** hoặc generic dispatch.

Recompile **không nhất thiết diễn ra ngay tại thời điểm class được nạp**. Có các queues, thresholds, compiler threads, profiling heuristics và những giới hạn như code cache.

## 6. Deoptimization thực chất là gì?

**Deoptimization** là quá trình rời thực thi compiled code đang dựa trên assumptions/optimization state và khôi phục một **execution state phù hợp để tiếp tục Java semantics**. Điều này khác hoàn toàn với:

- Garbage collector thu hồi object không còn reachable.
- Java code ném `RuntimeException`.
- JVM dừng hẳn process.
- Build system chạy lại `javac`.
- HotSpot chỉ đơn giản “xóa cache CPU”.

Một compiled frame tối ưu có thể **không có cấu trúc 1–1** với các interpreter frames trong Java source. Compiler có thể:

- Inline nhiều Java methods vào một machine frame.
- Giữ local values trong registers thay vì stack slots như trong bytecode interpreter model.
- Loại bỏ những trung gian chưa escape hoặc biểu diễn objects theo dạng scalarized/virtual.
- Thực hiện loop transformations hoặc hoisting hợp lệ.
- Xóa hay kết hợp những checks trong vùng có assumptions được guard.

Khi deopt, JVM phải dựng lại những **logical frames**, local variables, operand stack values và object states cần thiết cho correct resumption. HotSpot lưu metadata/debug information tại những điểm có thể deopt để phục vụ quá trình này.

### 6.1. On-stack replacement và deoptimization không đồng nghĩa

**OSR (On-Stack Replacement)** cho phép một loop đang được interpreter thực thi chuyển sang compiled code **trong khi loop chưa kết thúc**, thay vì chỉ chờ lần gọi method kế tiếp.

```text
Interpreter bắt đầu một loop chạy rất lâu
      │ backedge counter / hotness
      ▼
JIT sinh OSR nmethod tương thích
      │
      ▼
runtime chuyển execution sang optimized loop body

Deoptimization:
optimized execution → interpreter/fallback
khi giả định không còn phù hợp hoặc theo VM policy
```

OSR là một hướng chuyển **sang compiled execution**, deopt là cơ chế thoát khỏi optimization state. Chúng có thể cùng xuất hiện trong đời sống của một loop/method nhưng không phải một thao tác ngược nhau hoàn toàn ở mọi chi tiết.

### 6.2. Inline stack frames được tái tạo như thế nào?

Giả sử C2 đã inline:

```text
Java source call stack:
  main()
    → service()
       → validate()
          → compute()

JIT optimized:
  một compiled frame chứa code inlined của
  service(), validate(), compute()
```

Khi deopt, VM cần có thể khôi phục những logical frames liên quan để exception handling, bytecode execution và stack traces tiếp tục đúng semantics. Việc inline không được làm mất thông tin Java cần quan sát trong trường hợp fallback hợp lệ.

Đây là một trong các lý do **deoptimization có chi phí**: cần chuyển control, unpack/reconstruct frames, đôi khi materialize data đã được compiler tối ưu bỏ đi.

### 6.3. Escape analysis và materialization

Một object tưởng như được tạo với `new` có thể bị JIT **scalar replace** nếu compiler chứng minh nó không escape khỏi phạm vi phù hợp, và các field được giữ như scalar values thay vì một heap allocation độc lập.

```java
record Point(int x, int y) {}

static int compute() {
    Point p = new Point(2, 3);
    return p.x() + p.y();
}
```

Đây là **snippet minh họa**. Compiler có thể tối ưu `p` đi khi an toàn; nó không bảo đảm loại bỏ trong mọi build/config. Nếu deoptimization/Java state cần object tương đương, VM phải đảm bảo semantics thông qua metadata và materialization thích hợp.

Không quan sát `new` trong bytecode rồi suy mỗi lần Java source `new` tạo một allocation trên heap đã được GC nhìn thấy. Tương tự, không khẳng định mọi object scalar-replaced đều sẽ materialize ở mọi deopt: chỉ khi trạng thái thực thi/quan sát yêu cầu.

## 7. Uncommon trap, invalidation và `nmethod` lifecycle

Ba thuật ngữ thường bị trộn:

| Thuật ngữ | Ý nghĩa khái niệm | Điều không nên suy ra |
| --- | --- | --- |
| **Uncommon trap** | Nhánh/check hiếm kích hoạt VM path có thể gây deoptimization | Không đồng nghĩa Java exception |
| **Deoptimization** | Chuyển một active optimized execution sang trạng thái có thể tiếp tục đúng | Không bắt buộc nmethod bị xóa ngay |
| **Invalidation / made not entrant** | Phiên bản compiled code không còn được chọn làm entrypoint cho calls mới | Không có nghĩa mọi active frame hoặc memory được xóa tức thì |
| **Recompilation** | Compiler tạo code mới theo profile/assumptions mới | Không đảm bảo diễn ra ngay sau mỗi deopt |
| **Code cache reclamation** | VM thu dọn compiled code không còn sử dụng theo lifecycle | Không phải GC reclaim Java object heap |

Trong output `-XX:+PrintCompilation`, có thể thấy trạng thái liên quan compiled methods như `made not entrant` hoặc `made zombie`, tùy HotSpot build/runtime event. Chúng **không phải một dấu hiệu 1–1** chứng minh đúng loại uncommon trap nào đã xảy ra; có nhiều lý do khiến compiled code bị thay thế/không còn được dùng.

### 7.1. Có phải deopt là “trở lại interpreter từ đầu method”?

Không. Deoptimization phải tiếp tục **đúng vị trí và trạng thái ngữ nghĩa thích hợp**, không được tự ý chạy lại side effects từ đầu method. Nếu method đã ghi file, cập nhật database, hoặc tăng counter, deopt không thể tùy tiện làm lại chúng chỉ vì runtime cần đổi implementation.

Một **uncommon trap** có thể dẫn tới việc reexecute một bytecode tại điểm được chọn, nhưng việc đó phải phù hợp với JVM state và chỉ những thao tác được cho phép reexecute an toàn. Không suy “deopt chạy lại cả function” từ từ “reexecute” trong tài liệu thấp cấp của HotSpot.

### 7.2. Java exceptions khác trap nội bộ

Khi Java source đáng lẽ ném `NullPointerException`, `ArrayIndexOutOfBoundsException`, `ClassCastException` hay `ArithmeticException`, compiled execution phải giữ hành vi Java tương đương.

HotSpot có thể:

- Sinh explicit machine check và throw đúng exception.
- Sử dụng một implicit hardware fault an toàn theo runtime contract.
- Đưa một path hiếm về interpreter qua deoptimization để interpreter thực thi exception semantics.

Những lựa chọn này là implementation details; **không nên đọc “uncommon trap” trong compiler log như một exception đã bị chương trình bỏ qua**.

## 8. Vì sao recompilation không phải lúc nào cũng tốt?

Một hot method có thể trải qua các trạng thái:

```text
interpreter → profiled C1 → optimized C2
                   │             │
                   │             ├─ trap/invalidated → fallback
                   │             │                       │
                   │             │                       ▼
                   │             └──────────► new profile/recompile
                   │                                     │
                   └──────────────────────► optimized code khác
```

JIT compilation tiêu tốn CPU, memory và code cache. Deoptimization thường có chi phí **chuyển execution + reconstruct state** và có thể mất lợi ích của compiled code trong một khoảng thời gian. Nếu chương trình liên tục đổi type distributions, compiler có thể “đánh đổi” giữa nhiều versions mà không thấy một profile ổn định.

HotSpot có các policy để hạn chế tối ưu lại vô tận một điểm liên tục gây traps; một số paths có thể được xem là không phù hợp để speculate nữa hoặc compiler sẽ phát sinh code tổng quát hơn.

**Không** cho rằng mỗi nmethod invalidation là một lỗi JVM. Trong dynamic workloads, thay đổi tối ưu là phần bình thường của runtime adaptive compilation.

### 8.1. Profile pollution và warm-up

Một service warm-up với 99% requests từ data type A có thể dẫn JIT chọn fast path A. Sau khi rollout, production traffic chứa tỷ lệ đáng kể type B/C, và code có thể deopt, recompile hoặc chuyển sang generic dispatch.

Điều này lý giải:

- Vì sao microbenchmark với một type duy nhất có thể lạc quan.
- Vì sao A/B testing workload distribution ảnh hưởng latency.
- Vì sao performance “đang nhanh rồi đột nhiên chậm” đôi khi do class loading, profiles hoặc invalidation.
- Vì sao không nên định nghĩa performance regression chỉ từ 100 ms đầu hoặc một vài requests.

Warm-up nên phản ánh **độ đa dạng receiver types và branch outcomes** của production, không chỉ số iterations.

### 8.2. Tiered compilation không phải các mức cố định theo từng giây

HotSpot tiered compilation ở cấu hình phổ biến gồm:

- **Interpreter (level 0)**.
- Các mức **C1** khác nhau có hoặc không có profiling đầy đủ.
- **C2 optimized compilation** (thường level 4 trong tiered design).

Compiler policy quyết định transitions theo hotness, compiler availability, code size, profiles và các heuristics. Không có hợp đồng “sau đúng 10.000 lần gọi thì C2 compile” hoặc “sau 2 giây JIT bật” xuyên mọi JVM/build.

`-XX:-TieredCompilation` thay chính sách compilation, không phải “tắt mọi JIT”. Để chạy hầu hết code bytecode bằng interpreter và bỏ JIT cho thí nghiệm, HotSpot hỗ trợ `-Xint`; hiệu năng có thể giảm rất mạnh.

## 9. Trình tự một lần assumption thất bại

Một timeline hợp lý:

1. Interpreter/C1 quan sát `Operation.apply` nhận `AddOne` hầu hết các lần.
2. C2 dùng profile để phát sinh fast path và có thể inline `AddOne.apply`.
3. Runtime bắt đầu nhận `TimesTwo` ở cùng call site.
4. Một guard fail hoặc compiler dependency bị vô hiệu, tùy cấu trúc code thực.
5. Nếu đường đã compile không xử lý trực tiếp được trường hợp mới, HotSpot có thể deopt, reconstruct frames/state và tiếp tục logic đúng.
6. C2 nmethod cũ có thể không còn entrant nếu policy thấy assumptions không hợp lệ.
7. Profiling có thể phản ánh thêm type `TimesTwo`.
8. Nếu method vẫn hot, JIT có thể compile phiên bản mới: type checks cho cả hai, virtual dispatch, hoặc chiến lược khác.
9. Mọi request vẫn phải nhận **cùng kết quả ngữ nghĩa Java** với execution không được tối ưu.

```text
Time
  │
  ├─ profile A A A A A A A ...
  │
  ├─ compile specialized code for A
  │
  ├─ call with B
  │    └─ type guard fails
  │          └─ deopt / fallback if needed
  │
  ├─ profile A and B ...
  │
  ├─ potential recompile for A+B / generic path
  │
  └─ continue same Java semantics
```

Đó là một **kịch bản được phép xảy ra**, không phải chuỗi 9 events bắt buộc và đồng bộ do JVM “phải” in ra ở mỗi lần chạy.

## 10. Java 17 thực hành: làm nóng một type rồi đổi sang type khác

File độc lập **`SpeculationDemo.java`**:

```java
public class SpeculationDemo {
    interface Operation {
        int apply(int value);
    }

    static final class AddOne implements Operation {
        @Override
        public int apply(int value) {
            return value + 1;
        }
    }

    static final class TimesTwo implements Operation {
        @Override
        public int apply(int value) {
            return value * 2;
        }
    }

    // Call site này có thể nhận nhiều receiver types.
    static int evaluate(Operation op, int value) {
        return op.apply(value);
    }

    public static void main(String[] args) {
        Operation first = new AddOne();
        Operation second = new TimesTwo();

        long phaseOne = 0;
        for (int i = 0; i < 100_000; i++) {
            phaseOne += evaluate(first, i);
        }

        long phaseTwo = 0;
        for (int i = 0; i < 1_000; i++) {
            phaseTwo += evaluate(second, i);
        }

        System.out.println("phaseOne=" + phaseOne);
        System.out.println("phaseTwo=" + phaseTwo);
        System.out.println("semanticsOK=" +
                (phaseOne == 5_000_050_000L && phaseTwo == 999_000L));
    }
}
```

Biên dịch:

```bash
javac --release 17 SpeculationDemo.java
```

Chạy bình thường:

```bash
java SpeculationDemo
```

**Stdout của chương trình Java (không bật JIT diagnostic output):**

```text
phaseOne=5000050000
phaseTwo=999000
semanticsOK=true
```

**Giải thích kết quả:**

- Phase one tính tổng `1+2+...+100000 = 5_000_050_000` trong biến `long`, tránh tràn `int` ở tổng.
- Phase two tính tổng `2×0+2×1+...+2×999 = 999_000`.
- `evaluate` là call site có thể monomorphic trong phase one rồi nhận type thứ hai ở phase two.
- Kết quả **Java-level** phải giống nhau dù `evaluate` được interpreter, C1 hay C2 thực thi.

**Giới hạn thí nghiệm:** Không đảm bảo có `deoptimization` chỉ vì đổi `Operation` từ AddOne sang TimesTwo. Compiler có thể dự đoán sớm, xử lý đa hình bằng slow path, inline cả hai, không compile kịp trước phase two, hoặc tối ưu loops/call sites theo cách khác. Muốn khẳng định một trap cụ thể đã xảy ra phải có **diagnostic evidence** từ đúng phiên chạy.

### 10.1. Chạy với interpreter để đối chiếu semantics

```bash
java -Xint SpeculationDemo
```

Chạy `-Xint` trên HotSpot khiến thực thi bytecode thông thường dựa vào interpreter thay vì JIT compiled application methods. **Stdout vẫn đúng ba dòng ở trên**, nhưng thời gian chạy có thể khác lớn.

Không dùng chênh lệch thời gian của **một lần chạy** với vòng lặp nhỏ để kết luận “JIT tăng tốc X lần”: overhead startup, interpreter/JIT policy và các tối ưu biến thiên; dùng JMH hoặc benchmark có thiết kế tốt nếu mục tiêu là đo microperformance.

### 10.2. Không nhầm output chẩn đoán với stdout nghiệp vụ

Khi bật `PrintCompilation`, JVM có thể in nhiều dòng compiler events trước/giữa các dòng ứng dụng. Đừng coi log số compilation events là stdout cố định của class `SpeculationDemo`.

## 11. Java 17 thực hành thứ hai: nhánh hiếm phải vẫn đúng

Tạo file **`RareBranchDemo.java`**:

```java
public class RareBranchDemo {
    static int normalize(int value) {
        if (value < 0) {
            return -value;
        }
        return value + 1;
    }

    public static void main(String[] args) {
        long checksum = 0;
        for (int i = 0; i < 100_000; i++) {
            checksum += normalize(i);
        }

        System.out.println("warmupSum=" + checksum);
        System.out.println("rareNegative=" + normalize(-7));
        System.out.println("nextPositive=" + normalize(10));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 RareBranchDemo.java
java RareBranchDemo
```

**Stdout chính xác:**

```text
warmupSum=5000050000
rareNegative=7
nextPositive=11
```

Kết quả của nhánh `-7` vẫn đúng bất kể C2 đã đoán “nhánh âm không bao giờ xảy ra” hay compiler vốn vẫn giữ code cho nhánh đó. Ví dụ chỉ thể hiện **correctness xuyên các execution paths**; nó không xác nhận số lần `uncommon_trap`.

**Cảnh báo biên của `int`:** `normalize(Integer.MIN_VALUE)` trả `Integer.MIN_VALUE` do Java `int` overflow theo two's complement semantics. Đây là behavior của Java, **không phải lỗi deoptimization**. Nếu nghiệp vụ cần trị tuyệt đối toán học của mọi `int`, phải thiết kế xử lý overflow bằng `long` hoặc `Math.negateExact` phù hợp.

## 12. Quan sát JIT bằng các cờ HotSpot Java 17

### 12.1. `-XX:+PrintCompilation`

```bash
java -XX:+PrintCompilation SpeculationDemo
```

Cờ này in thông tin mỗi khi HotSpot biên dịch method theo tài liệu `java` của JDK 17. Các dòng có thể chứa:

- Thời điểm tương đối.
- Compilation identifier.
- Compilation level (tùy tiered mode).
- Class và method.
- Trạng thái compiled nmethod (như made not entrant), tùy event.

**Không có một output cố định**. Nếu không thấy `evaluate`, chưa chắc code sai: VM có thể inline/optimize hoặc sample run kết thúc trước khi method được compile ở tier mong muốn.

### 12.2. Inlining decisions — cần diagnostic unlock

```bash
java -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintCompilation -XX:+PrintInlining \
     SpeculationDemo
```

`PrintInlining` có thể giải thích vì sao compiler inline một method hoặc không (code too big, hotness, profile, call site...). Chú ý:

- Output của từng HotSpot build có thể khác.
- PrintInlining cho biết **quyết định compile**, không tự chứng minh có deopt khi type distribution đổi.
- Một method `final` vẫn có thể không inline vì compiler budgets/cost model.
- Method được inline có thể không xuất hiện dưới dạng một machine stack frame riêng ở optimized execution.

### 12.3. `LogCompilation` ghi thông tin chi tiết

Oracle JDK 17 hỗ trợ diagnostic compilation logging dạng XML, thường dùng để điều tra uncommon traps và compile decisions:

```bash
java -XX:+UnlockDiagnosticVMOptions \
     -XX:+LogCompilation -XX:LogFile=jit-hotspot.xml \
     SpeculationDemo
```

File `jit-hotspot.xml` được ghi trong thư mục đang chạy khi JVM/runtime hỗ trợ. Phải đọc **XML/compiler log thực** trước khi kết luận:

- Có `uncommon_trap` hay không?
- Trap reason/bytecode index là gì?
- Method đã bị `made not entrant` theo sự kiện nào?
- Compiler có tạo lại code sau event hay không?
- Call site được tối ưu/inline theo những types nào?

**Quan trọng:** Tên trường, tags và reason/action codes là **HotSpot internals**, không phải stable public logging API. Một số flags chỉ hiện diện ở một số builds, diagnostic builds hoặc yêu cầu `-XX:+UnlockDiagnosticVMOptions`. Nếu JVM báo “Unrecognized VM option”, hãy kiểm tra vendor/release; **không tự bịa rằng mọi JVM luôn có một cờ `TraceDeoptimization` trong production build**.

### 12.4. So sánh interpreter, tiered và non-tiered

```bash
# Interpreter-oriented baseline:
java -Xint RareBranchDemo

# HotSpot tiered compilation mặc định ở các cấu hình phổ biến:
java RareBranchDemo

# Thay đổi compilation policy, KHÔNG phải tắt toàn JIT:
java -XX:-TieredCompilation RareBranchDemo
```

Ba lệnh có **cùng Java-level stdout** trong ví dụ. Nhưng không cần có cùng compiler events, code cache usage hay thời gian thực thi.

`-XX:-TieredCompilation` **không** tương đương `-Xint`: nó thay đổi chính sách tiering, và HotSpot vẫn có thể dùng JIT compiler.

### 12.5. Không dựa vào flag nội bộ làm API production

Tài liệu công khai của JDK 17 liệt kê `-XX:+PrintCompilation`, `-XX:+LogCompilation`, `-XX:+PrintInlining`, `-XX:+UnlockDiagnosticVMOptions`. Những cờ khác như `PrintOptoAssembly`, `TraceDeoptimization` hay instrumentation từ debug builds có khả năng phụ thuộc rất mạnh vào HotSpot flavor/platform. Chỉ dùng chúng khi đã xác nhận `java -XX:+PrintFlagsFinal -version`/build docs; một số flag sẽ không xuất hiện trong production JVM.

### 12.6. JFR và profile timeline

**Java Flight Recorder** hỗ trợ quan sát compilation activities và application runtime events theo những event types được cấu hình, qua JDK Mission Control hoặc tooling tương ứng. Nó hữu ích để liên hệ thời gian:

- Compiler warm-up/compilation queue.
- CPU pressure khi nhiều compiled tasks chạy.
- Request latency spikes.
- Class loading và safepoints.
- GC events, heap allocations, code cache và runtime behavior.

Không mặc định mỗi JFR recording đều chứa mọi deopt event hoặc chi tiết C2 speculation: event availability, settings và JVM build khác nhau. Để xem chi tiết một uncommon trap cụ thể, `LogCompilation`/compiler diagnostic metadata thường phù hợp hơn nếu HotSpot hỗ trợ.

## 13. Cần phân biệt “đã thấy compilation” và “đã thấy deoptimization”

| Quan sát | Kết luận được phép | Không thể kết luận chỉ từ đó |
| --- | --- | --- |
| `PrintCompilation` có tên method | JVM đã có compilation event cho method | Compiler chắc chắn speculative inline một receiver type |
| `PrintInlining` báo inline target | Compiler quyết định inline một call/target trong compilation | Lúc runtime đã xảy ra uncommon trap |
| Log có `made not entrant` | Một phiên bản nmethod không còn được dùng làm entrypoint bình thường | Tất cả active frames đã mất ngay hoặc chỉ có lý do type profile |
| Log có `uncommon_trap` reason/action | HotSpot ghi trap metadata/event theo reason tương ứng | Java code đã sai semantics hoặc cần ném exception cho caller |
| Không thấy traps trong một run | Run đó không có evidence traps theo mức logging | Chương trình không thể deopt trong tương lai |
| Runtime p99 chậm tăng sau rollout | Có performance change | Deopt chắc chắn là nguyên nhân duy nhất |
| `javap -c` có `invokeinterface` | Bytecode theo semantics có lời gọi interface | JIT không thể inline target vào machine code |

**`javap` quan sát bytecode**, còn compiled nmethod behavior cần compiler/runtime diagnostics. Java source không có từ khóa `deoptimize`, và một method Java không tự biết nó đang chạy bằng C1 hay C2 thông qua public Java API chuẩn.

## 14. Deoptimization có chi phí gì?

Các nguồn overhead có thể gồm:

- Chuyển từ compiled code sang runtime handler/fallback.
- Xác định vị trí bytecode cần tiếp tục và **reconstruct deoptimized frames**.
- Materialize objects hoặc undo/reconcile trạng thái scalarized khi cần.
- Thao tác invalidation/marking compiled code nếu dependency hết hợp lệ.
- Thu thập profile mới trong interpreter/C1.
- CPU và memory để JIT **compile lại**.
- Code cache footprint và công tác thu dọn obsolete nmethods.
- Mất hiệu quả của một số optimizations cho đến khi runtime có profile phù hợp.

**Một lần deopt không nhất thiết làm ứng dụng chậm đến mức đáng kể**. Việc chuyển execution có overhead, nhưng có thể tiết kiệm rất nhiều CPU trước đó nhờ fast path. Khi deopt xảy ra hiếm, tổng lợi ích vẫn dương.

Điều đáng điều tra là **liên tục deopt/recompile cùng điểm trong hot code**, profile không ổn định hoặc compiled code bị invalidated thường xuyên gây CPU spikes/tail latency.

### 14.1. Tại sao một benchmark có thể bị nhiễu bởi warm-up?

Microbenchmark chỉ chạy vài chục milliseconds có thể gồm cả:

- Class loading, bytecode verification và interpreter startup.
- JIT compilation chạy song song với work đang đo.
- Thay đổi tốc độ khi C1/C2 code trở thành active.
- OSR khi loop nóng.
- Deoptimization/recompilation khi thay data distribution.
- GC, CPU scheduling và thermal throttling.

Đó là lý do **JMH** thường được dùng cho Java microbenchmarks: framework hỗ trợ warm-up, forks, multiple iterations và tránh một số compiler pitfalls. Nhưng JMH cũng không thể thay workload production hay tự chứng minh deadline/latency SLO.

### 14.2. Profiling overhead và tier transitions

C1 code có instrumentation/profiling để cung cấp dữ liệu cho C2. Khi method đủ nóng, tiered policy có thể thay code profiled bằng code tối ưu hơn. Các transitions này không phải “đề bài bị compile lại vì compiler biết sai”; có thể chỉ là **lộ trình bình thường** tới performance tốt hơn.

Ngược lại, một C2 nmethod đã được tối ưu theo assumption cũ có thể bị invalidated, và code được biên dịch lại theo assumption khác. Chúng là **hai nguyên nhân khác nhau để có nhiều compilation events trên cùng method**.

## 15. Tình huống thực tế: service, plugin và đa hình

### 15.1. Service với nhiều triển khai chiến lược

Một ứng dụng dùng interface `PaymentStrategy`, ban đầu phần lớn calls thuộc `CardStrategy`. Sau khi thêm một plugin `WalletStrategy`, số lượng receiver types tăng. JIT có thể:

- Trước đó inline `CardStrategy`.
- Gặp type profile khác và deopt/fallback hoặc dùng polymorphic dispatch.
- Compile phiên bản phù hợp cho traffic mới.
- Giảm hoặc tăng throughput tùy inlining, code size, CPU caches và branch predictions.

Không phải việc load plugin **luôn** gây deopt. Chỉ những compiled dependencies/specializations có liên quan mới có rủi ro; code đã handle generic types thì có thể không phải thay đổi.

### 15.2. Workload thay đổi sau warm-up

Kịch bản:

```text
T0-T1  warm-up chỉ dữ liệu loại A
T1-T2  production traffic A + B + C
T2     p99 latency spike, compiler activity tăng
T3     throughput ổn định sau khi runtime thích ứng
```

Đây là **mô hình giả lập**. Để xác định nguyên nhân cần correlate:

- Compilation logs gần T2.
- Deopt/trap reasons nếu có.
- Class loading timeline và method code cache transitions.
- Request traces và CPU/GC metrics.
- Changes trong input mix (receiver types/branch distributions).

Không nên tắt toàn JIT chỉ để loại bỏ một spike rồi bỏ qua regression throughput dài hạn. Hãy đo và sửa nguyên nhân thay đổi data shape/inlining profile nếu thực sự quan trọng.

### 15.3. Proxy, reflection, lambda, dynamic languages

Dynamic proxies, nhiều call site adapters, lambdas và method handles có thể tạo hình thái dispatch khác với Java class call trực tiếp. JIT vẫn có nhiều cơ chế tối ưu, nhưng type profile hoặc hierarchy assumptions có thể phức tạp hơn.

Không kết luận “dùng reflection thì JIT không tối ưu gì” hoặc “interface calls luôn chậm hơn static calls”. Method handles, inlining và profile-guided optimizations có thể tạo kết quả khác trực giác. Benchmark đúng API path trên JDK đang chạy.

## 16. An toàn ngữ nghĩa: deopt không được làm sai chương trình

Ba invariant quan trọng:

1. **Giá trị trả về đúng:** `TimesTwo.apply(7)` phải cho `14` dù trước đó `evaluate` chỉ gặp `AddOne`.
2. **Side effects không bị chạy lại tùy ý:** nếu `counter++` hoặc `socket.write` đã xảy ra, deopt không được tự nhân đôi tác động.
3. **Java exceptions và synchronization vẫn đúng:** optimized code phải giữ contract về NPE, bounds checks, object monitors, happens-before và exception handlers theo JVM rules.

```java
static int compute(int value, java.util.concurrent.atomic.AtomicInteger c) {
    c.incrementAndGet();   // side effect quan sát được
    return value < 0 ? -value : value + 1;
}
```

Đây là **snippet minh họa**. Nếu method được JIT compile và deopt ở nhánh âm, Java logic vẫn phải ghi nhận `c.incrementAndGet()` đúng **một lần mỗi invocation**, không phải hai lần vì compiler phải quay về interpreter.

### 16.1. Tại sao deopt phức tạp hơn generic fallback?

Nếu compiler chỉ sinh hai code paths tổng quát đã guard, nó có thể xử lý type mới **ngay trong compiled code** mà không deopt. Nhưng khi C2 đã aggressively inline, scalar-replace và reorder hợp lệ, một nhánh hiếm có thể cần **đầy đủ interpreter state** để tiếp tục đúng. Deoptimization xử lý tình huống đó bằng cooperation giữa compiler metadata và runtime.

Vì vậy “guard failed ⇒ luôn deopt” cũng sai: một guard có thể dẫn tới compiled slow path thông thường. Chỉ khi compiler/runtime quyết định bỏ về đường thực thi tổng quát hơn mới có deopt theo nghĩa đang xét.

### 16.2. On-stack compiled frames và invalidation

Một method có nhiều active invocations trong các threads. Khi VM phát hiện dependency invalid:

- Không thể giả định bỏ tức thì mọi frames đang chạy trên tất cả threads.
- VM phải dùng mechanisms phù hợp để đảm bảo không frames nào tiếp tục thực hiện **sai assumption**.
- Nmethod có thể được đánh dấu **not entrant** cho calls mới.
- Những active frames cần deopt/invalidation có thể được xử lý tại các điểm VM hỗ trợ.
- Code cache reclaim chỉ xảy ra khi code không còn được sử dụng theo lifecycle.

Không được tự đồng nhất “class loader đã nạp class mới” với “GC đã thu hồi nmethod trong cùng millisecond”.

## 17. Gỡ lỗi hiệu năng khi thấy quá nhiều deopt

**Bước 1 — Xác nhận symptom.** Thu thập p95/p99 latency, throughput, CPU và compiler activity; phân biệt startup với steady state.

**Bước 2 — Ghi JDK và cờ.** Cố định `java -version`, vendor, architecture, tiered compilation policy, heap/code cache settings và CPU quota.

**Bước 3 — Quan sát compilation.** Bật `-XX:+PrintCompilation` ở môi trường thử nghiệm và kiểm tra methods nóng đang được compile nhiều lần.

**Bước 4 — Quan sát inlining.** Bật `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` để thấy nhận định compiler về targets và code size; output lớn nên log/tách file.

**Bước 5 — Lấy compiler logs nếu cần.** Dùng `-XX:+LogCompilation` trên HotSpot build hỗ trợ, đối chiếu uncommon trap reason, compile id, bci và not-entrant transitions.

**Bước 6 — Correlate event timeline.** Xem class load/unload, JFR/compiler activity, GC, safepoints và workload mix gần thời điểm latency spike.

**Bước 7 — Tách nguyên nhân.** Tier transitions bình thường, speculative guard failures, code cache pressure, class hierarchy invalidation hay benchmark noise khác nhau.

**Bước 8 — Benchmark có kiểm soát.** Dùng JMH hoặc representative load, warm-up có A/B/C types và các branch outcomes tương tự production.

**Bước 9 — Điều chỉnh tối thiểu có bằng chứng.** Ví dụ giảm pathological megamorphic call sites trong hot loops, ổn định dispatch data hoặc thay đổi cấu trúc pipeline; không tắt toàn inlining/JIT trừ khi có lý do đo đạc cụ thể.

**Bước 10 — Kiểm tra correctness.** Chạy unit/integration tests với interpreter và HotSpot flags phù hợp để chắc logic không vô tình phụ thuộc vào timing/profiling.

### 17.1. `made not entrant` không luôn là vấn đề lớn

Khi C1 profile code bị thay bằng C2 code tối ưu hơn trong tiered pipeline, một nmethod cũ có thể không còn entrypoint phù hợp. Trạng thái tương tự cũng có thể xuất hiện khi class hierarchy dependency invalid. Chỉ thấy chữ `not entrant` **không cho biết nguyên nhân chính xác**; cần phối hợp compile id, level và log events.

### 17.2. Tránh microbenchmark “tự đo JIT” sai cách

```java
long start = System.nanoTime();
for (int i = 0; i < 10_000; i++) {
    normalize(i);
}
System.out.println(System.nanoTime() - start);
```

Đây là **snippet phản mẫu** để học benchmarking. Nếu kết quả `normalize(i)` không được dùng, JIT có thể loại bỏ phép tính; 10.000 iterations có thể không đủ warm-up hoặc profile thay đổi, và `System.nanoTime` có overhead của nó. Không lấy kết quả một lần chạy rồi tuyên bố compiler đã hoặc chưa deopt.

## 18. Những nhầm lẫn thường gặp

1. **“JIT chỉ là chuyển bytecode sang machine code một lần.”** Sai. HotSpot adaptive profiling, tiering, optimization và có thể recompile.
2. **“JIT speculation đồng nghĩa chấp nhận kết quả sai khi giả định thất bại.”** Sai. Guard/dependency/deopt giữ Java semantics.
3. **“Một call site ban đầu monomorphic sẽ luôn monomorphic.”** Sai. Workload hoặc class loading có thể đổi type distribution.
4. **“Dùng interface chắc chắn không inline.”** Sai. Profiling và CHA có thể cho phép devirtualization/inlining.
5. **“Mọi lần truyền receiver type mới đều bắt buộc deopt.”** Sai. Compiler có thể đã có generic fallback, polymorphic inline hoặc chưa compile.
6. **“`final` class không bao giờ cần fallback.”** Sai. Có nhiều assumptions khác và interface call site vẫn có thể đa hình.
7. **“Deoptimization là Java exception.”** Sai. Đây là runtime/compiler mechanism.
8. **“Uncommon trap nghĩa JIT crash.”** Sai. Đây có thể là đường chuyển trạng thái an toàn cho nhánh hiếm.
9. **“Nếu JIT bỏ null check thì Java NPE biến mất.”** Sai. Optimized code vẫn phải bảo toàn semantics.
10. **“Deopt chạy lại từ đầu method, kể cả side effects.”** Sai. VM phục hồi state để tiếp tục đúng.
11. **“Inline 3 Java methods tạo 3 machine frames riêng.”** Không nhất thiết. Một compiled frame có thể chứa logical inlined scopes.
12. **“Sau deopt JVM không thể dựng lại objects bị scalar replacement.”** Sai. VM/compiler phối hợp metadata/materialization khi cần.
13. **“`made not entrant` = compiled code memory được thu hồi ngay.”** Sai. Nmethod lifecycle và active frames phải được xử lý.
14. **“Compilation event nào cũng do deoptimization.”** Sai. Tiered level upgrades/OSR cũng tạo compilations.
15. **“Một uncommon trap sẽ luôn tái biên dịch ngay.”** Sai. Runtime policy quyết định.
16. **“Một class mới được load khiến toàn bộ JIT code invalid.”** Sai. Chỉ dependencies liên quan.
17. **“OSR chính là deoptimization.”** Sai. OSR cho phép vào compiled code ở loop, deopt thoát code tối ưu.
18. **“`-XX:-TieredCompilation` tắt mọi JIT.”** Sai. `-Xint` mới là chế độ interpreter-oriented.
19. **“`PrintCompilation` luôn báo chính xác trap reason.”** Sai. Cần diagnostics sâu hơn.
20. **“`javap -c` in mã máy đã JIT compile.”** Sai. Nó in Java bytecode.
21. **“Không thấy traps trong một lần chạy chứng minh sẽ không bao giờ deopt.”** Sai. Tùy profile/JDK/cấu hình và logging.
22. **“Performance tốt trong warm-up một type đại diện production đa type.”** Không nhất thiết.
23. **“JIT compile là GC thu gom code cũ.”** Sai. Code cache management khác Java heap GC.
24. **“Đổi interface implementation luôn làm source phải build lại.”** Sai. Java dynamic dispatch và runtime/JIT hỗ trợ type khác.
25. **“JIT có quyền thay đổi kết quả `System.identityHashCode` sau deopt.”** Không theo Java semantics của object identity.
26. **“Tăng `-Xmx` bảo đảm giảm mọi deopt.”** Sai. Heap GC memory và JIT assumptions/profiles khác nhau.
27. **“Tăng số compiler threads vô hạn sẽ tăng throughput.”** Sai. CPU/cache/memory và compilation policy có trade-offs.
28. **“`-XX:+LogCompilation` là Java Language Specification API.”** Sai. HotSpot diagnostic interface riêng.
29. **“Khi JIT inline thì stack trace Java không còn hiển thị được logical methods.”** Sai. VM có scope metadata để support Java behavior tại điểm thích hợp.
30. **“Speculation là lỗi tối ưu.”** Không. Nó là một kỹ thuật có chủ đích, với cost/correctness trade-offs.

## 19. Tự kiểm tra và đáp án

**Câu 1.** Compiler profile thấy receiver luôn là `AddOne`. Tại sao không được giả sử mọi lời gọi tương lai đều là `AddOne` mà không có safeguard?

**Đáp án:** Một receiver `TimesTwo` có thể xuất hiện hợp lệ sau này. Guard/valid dependency hoặc generic fallback bảo đảm correct dynamic dispatch.

**Câu 2.** Nếu speculative guard fails, người dùng Java có phải catch `DeoptimizationException` không?

**Đáp án:** Không. Deopt là HotSpot runtime mechanism, không phải checked/unchecked Java exception công khai.

**Câu 3.** Deopt có thể trở lại đúng giữa một method đang chạy thay vì chạy lại method từ đầu không?

**Đáp án:** Có. JVM/compiler metadata phục vụ reconstruction và resumption tại trạng thái/ngữ nghĩa thích hợp.

**Câu 4.** Tại sao JIT cần metadata cho inlined methods?

**Đáp án:** Một compiled frame có thể chứa nhiều logical Java scopes; khi deopt/exception/stack walking cần dựng lại scopes và values đúng.

**Câu 5.** Tại sao `made not entrant` không có nghĩa method đã bị compiler xóa tất cả active frames?

**Đáp án:** Nó là trạng thái entrypoint/lifecycle của một compiled nmethod; active frames cần được xử lý riêng an toàn trước reclaim.

**Câu 6.** `-XX:-TieredCompilation` có bằng `-Xint` không?

**Đáp án:** Không. Non-tiered vẫn có thể JIT compile; `-Xint` chủ yếu dùng interpreter cho thí nghiệm.

**Câu 7.** `SpeculationDemo` in `phaseTwo` bằng bao nhiêu?

**Đáp án:** **999000**, là tổng `2×i` với `i=0..999`.

**Câu 8.** Thấy `-XX:+PrintInlining` báo một target inline. Điều đó đã chứng minh deopt khi B xuất hiện chưa?

**Đáp án:** Chưa. Cần actual trap/deopt evidence; code có thể có guard + generic fallback.

**Câu 9.** Class loading có thể invalid compiled CHA assumption không?

**Đáp án:** Có nếu dependency tương ứng bị ảnh hưởng; không phải bất kỳ class load nào đều invalid mọi nmethod.

**Câu 10.** Tại sao compiler có thể materialize object sau escape-analysis-based scalar replacement khi deopt?

**Đáp án:** Java-level state yêu cầu object tương đương còn được quan sát/sử dụng sau khi trở về interpreter; compiler lưu metadata phục vụ reconstruction.

**Câu 11.** Nếu JIT bị deopt, liệu kết quả phép tính phải đổi so với `-Xint` không?

**Đáp án:** Không. Với code hợp lệ, interpreter và optimized code phải giữ Java semantics.

**Câu 12.** Vì sao thí nghiệm `RareBranchDemo` không đảm bảo uncommon trap?

**Đáp án:** C2 có thể không compile kịp hoặc sinh nhánh compiled bình thường; type/branch speculation là lựa chọn heuristic, không là nghĩa vụ.

**Câu 13.** Khi benchmark hot method đa hình, nên warm-up với loại input nào?

**Đáp án:** Với phân phối types/branch outcomes đại diện workload thật, tránh warm-up chỉ một type rồi suy ra hiệu năng production.

**Câu 14.** Có nên xem một lần `made not entrant` trên startup là memory leak không?

**Đáp án:** Không. Đây thường là biến đổi hợp lệ của compiled code lifecycle; xem mức độ lặp, code cache/CPU và event reasons trước.

## 20. Tổng kết bằng bản đồ cơ chế

```text
           Java bytecode / interpreter
                      │
                      ▼
             Runtime profiling
          receiver types, branches,
           invocations, backedges
                      │
                      ▼
              C1/C2 compiler
                      │
               speculative code
                      │
       ┌──────────────┴────────────────┐
       │                               │
   Assumption OK                  Assumption FAIL
       │                               │
       ▼                               ▼
   Optimized fast path        guard fails / invalid dependency
                                       │
                              ┌────────┴─────────┐
                              │                  │
                          compiled           DEOPTIMIZE
                          slow path          reconstruct state
                              │                  │
                              │                  ▼
                              │           interpreter / fallback
                              │                  │
                              │           new profiles/heat
                              │                  │
                              │           possible recompile
                              │                  │
                              └────────┬─────────┘
                                       ▼
                            SAME JAVA SEMANTICS

Trong mọi nhánh:
  - return values đúng
  - exceptions đúng
  - side effects đúng
  - object identity đúng
```

**Ghi nhớ:** JIT tối ưu dựa trên **profile và assumptions**, vì biết những đường chạy thường gặp có thể nhanh hơn generic bytecode execution. HotSpot duy trì **guards và class-dependency checks** để không chạy sai khi thế giới thay đổi. **Deoptimization** phục hồi Java execution state từ compiled/inlined/scalarized representation và cho phép tiếp tục đúng bằng interpreter/fallback; runtime có thể reprofile và **compile lại**, nhưng không bắt buộc sau mỗi trap. Khi tìm vấn đề hiệu năng, hãy xem **compile levels, inlining, actual uncommon traps, class loading, code-cache events và workload input mix** thay vì đoán chỉ từ kết quả một lần chạy.

### Tài liệu tham khảo

- [Oracle JDK 17 — `java` command / JIT flags](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `PrintCompilation`, `PrintInlining`, `LogCompilation`, `TieredCompilation`, `Xint`.
- [OpenJDK HotSpot Wiki — Compiler](https://wiki.openjdk.org/display/HotSpot/Compiler): overview optimizing compilers và HotSpot internals.
- [OpenJDK HotSpot Wiki — Deoptimization](https://wiki.openjdk.org/display/HotSpot/Deoptimization): khái niệm deoptimization trong HotSpot.
- [Red Hat Developer — How JIT Compiler Boosts Java Performance](https://developers.redhat.com/articles/2021/06/23/how-jit-compiler-boosts-java-performance-openjdk): type profiles, speculation, traps và class hierarchy invalidation.
- [Red Hat Developer — Runtime Profiling in OpenJDK's HotSpot JVM](https://developers.redhat.com/articles/2021/11/18/runtime-profiling-openjdks-hotspot-jvm): branch/type profiling phục vụ C2 speculation.
- [JVMS Java SE 17 — `invokevirtual` và `invokeinterface`](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html): bytecode invocation semantics mà JIT phải giữ.
- [Java SE 17 — `javap` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): xem bytecode/class file, không phải JIT native code.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-008">Code cache, luồng biên dịch và chi phí tài nguyên của JIT</a>

<details>
<summary>Xem chi tiết</summary>

# Code cache, luồng biên dịch và chi phí tài nguyên của JIT

## 1. Tổng quan

**Just-in-time (JIT) compilation** cho phép HotSpot JVM biên dịch các phương thức Java bytecode thường được sử dụng thành **native machine code** trong khi chương trình đang chạy. Interpreter có thể bắt đầu thực thi method nhanh mà chưa phải biên dịch tối ưu; sau khi thu thập thông tin về những đoạn code thực sự nóng, JIT tạo native code để cải thiện hiệu năng.

JIT không miễn phí: nó tiêu tốn **CPU trên những compiler threads**, dùng **native memory** cho quá trình biên dịch, cần **code cache** để lưu machine code, có thời gian **warm-up**, và có thể phải **deoptimize/recompile** khi các giả định tối ưu không còn chính xác. Ở ứng dụng lớn, JIT có thể gây áp lực tài nguyên ngay cả khi Java heap chưa đầy hoặc GC hoạt động bình thường.

**Code cache** là vùng bộ nhớ nội bộ của HotSpot để lưu generated machine code và những VM code blobs phù hợp; **khác Java heap, Metaspace, class-file bytecode và run-time constant pool**. Đây là chi tiết implementation của HotSpot, không phải một vùng tên Code Cache được yêu cầu giống nhau trên mọi JVM trong Java Virtual Machine Specification (JVMS).

Học xong bài, bạn có thể:

- Mô tả pipeline **bytecode → interpreter/profiling → compilation queue → C1/C2 compiler threads → compiled nmethod → code cache**.
- Giải thích **tiered compilation levels**, sự khác biệt C1/C2, OSR, inlining, speculative optimization và deoptimization.
- Phân biệt các phân vùng của **segmented code cache**: non-method, profiled methods, non-profiled methods.
- Nêu được nguồn CPU/native memory của compiler threads và tác động lên startup, throughput, latency.
- Hiểu **code cache full**, fragmentation, flushing, sweeping, code unloading và vì sao có thể bị giảm hiệu năng dù Java heap vẫn còn chỗ.
- Hiểu các cờ HotSpot **ReservedCodeCacheSize**, **InitialCodeCacheSize**, **CICompilerCount**, **TieredCompilation**, **TieredStopAtLevel** và những công cụ kiểm tra cấu hình.
- Sử dụng **jcmd Compiler.codecache/queue/codelist**, **PrintCompilation**, **JFR**, **Native Memory Tracking** và benchmark với đúng phạm vi thông tin của từng công cụ.
- Không nhầm **reserved/committed/used**, CPU JIT với CPU application, compiler queue với request queue.

Ví dụ định hướng **HotSpot 64-bit Java 17**, dùng Java 17+ cho các chương trình chạy được. Flags, code-cache layout, thread policy và log formats có thể đổi theo vendor/JDK/platform; đọc JVM cụ thể trước khi kết luận.

## 2. Sơ đồ bytecode đến native code

~~~text
                     javac
  MyCode.java --------------------> MyCode.class
                                       |
                                       | Java bytecode / method metadata
                                       v
                               Class loading/linking
                                       |
                                       v
                                JVM Interpreter
                                       |
                           Invocation/backedge counts
                           Type/branch profiling
                                       |
                                       v
                               Compile Task Queues
                                /             \
                               v               v
                         C1 Compiler       C2 Compiler
                         (faster, tiers)  (optimizing)
                               \               /
                                v             v
                                 Native code
                                 nmethod blobs
                                      |
                                      v
                                   Code Cache
                                      |
                                      v
                          CPU executes compiled code
                                      |
                    assumption invalid / uncommon trap?
                                      v
                              Deoptimization
                              Fallback / Recompile
~~~

Sơ đồ là **mô hình hành vi**, không cam kết method nào cũng đi qua tất cả các bước. Một method có thể chỉ interpreted; một loop có thể được compile bằng **on-stack replacement (OSR)**; một callee có thể được inline thay vì có entry native code độc lập. Những VM stubs phục vụ runtime cũng có thể được lưu trong code cache.

### 2.1. Java class file và code cache khác nhau

| Thành phần | Ý nghĩa | Nơi lưu/quan sát |
| --- | --- | --- |
| **Java source** | Code do người viết chương trình cung cấp | .java |
| **Bytecode** | Instructions portable do javac/compiler ghi | Code attribute trong .class; đọc bằng javap -c |
| **Class metadata** | Tên, fields, methods, constant pool, descriptors và runtime structures | Metaspace/VM structures trong HotSpot |
| **JIT native code** | CPU-specific instructions đã compile từ bytecode | **Code cache** |
| **VM/interpreter stubs** | Generated code phục vụ thực thi/chuyển trạng thái | Code cache theo loại code blob |
| **Java objects/arrays** | Business data/instance state | Java heap |

**javap -c** hiển thị **bytecode**, không phải C2 machine instructions sinh ra sau runtime. Một nmethod trong code cache không phải file .class mới và không có Java source API cho phép dump nó như một Java object.

### 2.2. Code cache khác method area

JVMS §2.5.4 mô tả **method area** ở mức trừu tượng chứa per-class structures và code cho methods/constructors. HotSpot triển khai class metadata và JIT native-code memory bằng nhiều cơ chế riêng. Không suy rằng JVMS bắt buộc mọi JVM có **Metaspace** và **Code Cache** giống HotSpot.

## 3. Tại sao cần compiler threads?

Optimizing compilation có thể rất tốn CPU do:

- Parsing bytecode và xây dựng intermediate representation (IR).
- Control-flow/data-flow analysis và speculative type profiling.
- Inlining, devirtualization, constant propagation.
- Escape analysis, scalar replacement trong các trường hợp phù hợp.
- Loop transformations, range-check elimination và dead-code elimination.
- Instruction selection, scheduling, register allocation.
- Sinh machine code và metadata phục vụ deoptimization/GC/debugging.

Nếu application thread luôn bị buộc chờ compile đầy đủ, response latency sẽ phụ thuộc trực tiếp complexity của method mới. **Background compilation** giúp HotSpot tiếp tục chạy interpreter/compiled version cũ trong khi compiler threads xử lý các requests trong queue.

~~~text
Application Thread A       Application Thread B
  calls hot()                runs requests
       |                          |
       +-- counters/profile       |
               |                  |
               v                  |
          Compile Queue           |
             |                    |
      C1 / C2 CompilerThread      |
             |                    |
       compiled code installed    |
             |                    |
             +-- next hot() calls execute compiled version
~~~

Các compiler threads vẫn thuộc **cùng process JVM**; chúng cần CPU và native stacks, có thể cạnh tranh cache, memory bandwidth và cgroup CPU quota với mutators và GC threads. Background không đồng nghĩa không ảnh hưởng ứng dụng.

### 3.1. Compiler thread không phải javac

**javac** chạy trong build toolchain, tạo bytecode .class. **C1/C2 compiler threads** chạy lúc JVM thực thi ứng dụng để tạo code native cho CPU cụ thể. Biên dịch javac thành công không chứng minh khi runtime JIT đã compile tất cả methods.

### 3.2. Compile task lifecycle

Một hot method muốn chạy bằng JIT code thường phải:

1. Có invocation/backedge counts hoặc profiling information phù hợp.
2. Được compilation policy cho phép và chọn để compile.
3. Vào compilation queue, có mức tier/mode tương ứng.
4. Được một compiler thread chọn để xử lý.
5. Hoàn thành biên dịch không lỗi/không bị bỏ tác vụ.
6. Có code-cache capacity để cài đặt nmethod mới.
7. Được VM dùng như entry point cho lần gọi tương ứng.

**Hot method chưa chắc lập tức compiled**. Nếu CPU quota thấp, queue backlog hoặc code cache full, interpreter/C1 version có thể tiếp tục được dùng lâu hơn.

## 4. Tiered compilation: Level 0 đến Level 4

HotSpot Java 17 thường sử dụng **tiered compilation**, kết hợp interpreter, **C1 (client/compiler1)** và **C2 (server/compiler2)** để cân bằng tốc độ compile và độ tối ưu.

| Level điển hình | Công cụ/thao tác | Vai trò |
| --- | --- | --- |
| **0** | Interpreter | Thực thi bytecode, thu thập counters |
| **1** | C1 code không đầy đủ profiling | Biên dịch nhanh, overhead profile thấp |
| **2** | C1 với limited profiling | Một phần dữ liệu profile |
| **3** | C1 với full profiling | Mã C1 đồng thời tích lũy dữ liệu cho C2 |
| **4** | C2 optimizing compiler | Code tối ưu chuyên sâu cho hot paths |

Đây là mapping của **HotSpot tiered compiler** trong điều kiện phù hợp, **không phải thứ tự bắt buộc 0→1→2→3→4 cho mọi method**. JVM có thể bỏ qua levels, chuyển thẳng một tier theo heuristics hoặc dùng OSR compilation cho loops.

### 4.1. Vì sao không compile C2 mọi thứ ngay từ đầu?

C2 có thể tạo machine code nhanh khi thực thi nhưng tốn đáng kể compile CPU/thời gian/native memory. Một method chỉ chạy hai lần không đáng biên dịch tối ưu tốn 30 ms nếu interpreted execution chỉ cần microseconds.

C1 sinh code sớm với chi phí thấp, tăng warm-up throughput; C2 tập trung vào những methods mà lợi ích lâu dài lớn hơn compile cost.

### 4.2. C1 vs C2 không phải hoàn toàn tách biệt

C1 có thể tạo profiled code, phục vụ loại thông tin mà C2 dựa vào. C2 dùng profiling và assumptions để inline/devirtualize/tối ưu mạnh hơn. Một cùng method có thể được compile ở nhiều tiers trong vòng đời runtime.

### 4.3. TieredStopAtLevel

Ví dụ thử nghiệm:

~~~bash
java -XX:+TieredCompilation -XX:TieredStopAtLevel=1 MyMain
~~~

**MyMain** là main class phải có thật. Giới hạn tier thấp có thể rút ngắn CPU warm-up nhưng làm steady-state throughput kém hơn trên hot methods. Không coi cờ này là khuyến nghị mặc định.

Tắt tiered bằng **-XX:-TieredCompilation** không đồng nghĩa tắt JIT. Trong HotSpot Server VM, non-tiered compiler vẫn có thể chạy. **-Xint** dùng để quan sát interpreter-only execution trong môi trường hỗ trợ; **-Xcomp** ưu tiên compile rất mạnh, có thể tăng startup/resource costs.

## 5. Profile-guided speculative optimization

Giả sử method xử lý interface:

~~~java
// Đoạn minh họa, không phải file chạy độc lập:
long total = 0;
for (Shape shape : shapes) {
    total += shape.area();
}
~~~

Nếu một call site phần lớn thấy implementation Circle, C2 có thể sử dụng profile để tối ưu đường gọi, thậm chí inline các thao tác của Circle với runtime guards. Về sau xuất hiện một shape implementation mới làm giả định type profile không còn phù hợp, JVM phải bảo toàn semantics, có thể **deoptimize và recompile**.

### 5.1. Inlining: lợi và hại

Inlining giúp bỏ call overhead và tạo cơ hội optimization xuyên method boundary. Nhưng nhúng code của nhiều callees vào caller có thể làm compiled code lớn, tăng:

- Thời gian biên dịch.
- Native code-cache occupation.
- Instruction cache/iTLB pressure.
- Chi phí deoptimization metadata.
- Register pressure ở một số paths.

Vì vậy inlining được HotSpot kiểm soát bằng heuristics/cost models; “inline tất cả methods” không phải luôn tốt.

### 5.2. Deoptimization không nhất thiết là lỗi

Compiled code dựa trên những assumptions có thể bị invalidated do class loading, uncommon control flow hoặc thông tin runtime khác. VM chuyển về trạng thái ít tối ưu/interpret rồi compile lại nếu cần.

~~~text
Optimized nmethod
      |
      +-- assumption invalidated / uncommon trap
                 |
                 v
              Deopt
                 |
           fallback execution
                 |
          more profile data
                 |
             Recompile
~~~

Điều này giữ đúng Java semantics. Một vài deopts sau warm-up là bình thường; nếu xảy ra liên tục cùng workload có thể làm code cache churn/CPU compiler tăng.

### 5.3. Vì sao một method có nhiều compiled versions?

Cùng method có thể có C1 profiled nmethod, C2 optimized nmethod, một hoặc nhiều OSR versions và bản cũ marked not entrant. Mã callee còn có thể được inline trong caller, nên **số nmethods không bằng số methods trong source**.

## 6. On-Stack Replacement (OSR)

Một method có vòng lặp dài có thể nóng **trước khi method return lần đầu**. **OSR** cho phép HotSpot chuyển việc thực thi giữa interpreted/C1 và một compiled version tại một điểm thích hợp trong loop.

~~~java
long value = 0;
for (int i = 0; i < 10_000_000; i++) {
    value += i % 7;
}
~~~

Backedge counters có thể vượt threshold khi method còn đang ở trong loop. VM có thể yêu cầu OSR compilation, nhưng không bảo đảm mọi lần chạy source trên đều có OSR: JIT heuristics, method size, CPU, compile queue, flags và optimizations có thể thay đổi.

**-XX:+PrintCompilation** đôi khi hiển thị OSR markers; không dựa vào một compilation ID/hàng log cố định. Phân biệt **OSR compiled version** với **regular method-entry compiled version**.

## 7. Code cache lưu cái gì?

**Code cache** là vùng HotSpot quản lý để lưu các **code blobs**, ví dụ:

- Nmethods chứa machine instructions do C1/C2 sinh.
- Runtime stubs/adapters.
- Một số generated code cho interpreter/transition paths.
- Metadata đi kèm compiled code phục vụ execution, GC và deoptimization.

Code cache thường là native memory với những yêu cầu bảo vệ trang thực thi (executable mappings), không phải Java byte[] hay String trong managed heap.

~~~text
HotSpot process
  Java Heap       -> objects, arrays
  Metaspace       -> class/method metadata
  Code Cache      -> executable compiled code / VM stubs
  Compiler Memory -> IR graphs, temporary buffers, arenas
  Thread Stacks   -> compiler/application/VM thread execution
  GC Native Mem   -> collector data structures
  Other Native    -> libraries, direct buffers, VM internals
~~~

### 7.1. Reserved, committed, used, free

Đây là các kích thước cần tách:

| Thuật ngữ | Ý nghĩa |
| --- | --- |
| **Reserved** | Virtual address space dành cho code cache |
| **Committed** | Các pages đã được commit theo cơ chế VM/OS |
| **Used** | Space do active code blobs và metadata liên quan chiếm |
| **Free** | Capacity còn dùng được theo allocator/code heap |
| **Fragmented free** | Space trống chia thành nhiều blocks, có thể khó cấp cho một code blob lớn |

Nếu Code Cache có **reserved=240 MiB** và **committed=40 MiB**, không được nói cache đã dùng 240 MiB RAM vật lý. Virtual reservation, committed memory và process RSS là các metrics khác nhau.

### 7.2. Code cache khác instruction cache CPU

**Code cache** do JVM quản lý trong virtual memory. **CPU instruction cache (I-cache)** là phần cứng lưu cache lines instructions khi CPU fetch; **iTLB** lưu translations cho virtual instruction addresses.

JEP 197 nói segmentation có thể cải thiện instruction cache/iTLB locality; không có nghĩa JVM code cache và CPU L1 I-cache là cùng một vùng bộ nhớ.

## 8. Segmented Code Cache: ba nhóm trong JDK 17

**OpenJDK JEP 197 — Segmented Code Cache** tách cache thành ba code heaps với lifetime/usage khác nhau:

| Code heap | Loại code | Ý nghĩa |
| --- | --- | --- |
| **Non-method** | VM-generated stubs, interpreter/adapter code và loại non-nmethod | Thường giữ lâu, khó reclaim bằng nmethod sweeper thông thường |
| **Profiled method** | Methods compiled có profiling, thường là một số C1 tiers | Nhiều mã có thể bị thay bởi tier cao sau warm-up |
| **Non-profiled method** | Methods không dùng profiling ở code version hiện tại, thường C2 optimized và C1 non-profiled tùy mode | Tập trung code hữu ích lâu, locality quan trọng |

~~~text
                 Reserved Code Cache
+-------------------------------------------------------+
|                                                       |
|  +--------------+ +---------------+ +--------------+ |
|  | Non-method   | | Profiled      | | Non-profiled | |
|  | stubs/runtime| | C1 profiled   | | optimized    | |
|  +--------------+ +---------------+ +--------------+ |
|                                                       |
+-------------------------------------------------------+
~~~

**Không** đồng nhất profiled heap với mọi C1 nmethod: C1 level 1 có thể là non-profiled code. Không hứa mọi code blob có lifetime cố định theo màu ô trên sơ đồ.

### 8.1. Tại sao phân vùng?

- Giảm fragmentation khi compiled methods ngắn hạn được thay/loại bỏ.
- Tách loại non-method không thể tùy tiện quét bỏ.
- Giảm một số chi phí scanning/code sweeper.
- Cải thiện locality của compiled hot code đối với CPU instruction cache.
- Giúp quản lý budget code heap theo types.

Theo tài liệu Java 17, **SegmentedCodeCache** được bật mặc định khi **TieredCompilation** bật và **ReservedCodeCacheSize từ 240 MB trở lên**, trong cấu hình hỗ trợ. Nếu thay flags, cần kiểm tra trạng thái thực.

### 8.2. Các code heap không nhất thiết được chia đều

Các flags HotSpot liên quan gồm:

- **NonNMethodCodeHeapSize**.
- **ProfiledCodeHeapSize**.
- **NonProfiledCodeHeapSize**.
- **ReservedCodeCacheSize**.

Kích thước có giới hạn và alignment; cấu hình cho từng segment tùy version/ergonomics. **Không giả định 240 MB chia thành ba phần bằng nhau**; phải quan sát final flags/jcmd.

### 8.3. Segmentation không loại bỏ cạn chỗ

Một heap cụ thể có thể thiếu space hoặc bị fragmentation ngay cả khi những heaps khác còn capacity. Có những VM stubs bắt buộc cần được cấp phát, nên cache exhaustion ở non-method segment có thể gây hậu quả nghiêm trọng hơn một nmethod optimized không được cài đặt.

## 9. Nmethod: vòng đời native code

Nmethod đại diện cho compiled Java method code/OSR code trong HotSpot, kèm metadata để GC/deoptimization/runtime có thể quản lý an toàn.

~~~text
Compile request
     |
     v
Compiled nmethod installed
     |
     v
Entrant (new invocations may execute)
     |
     +-- hot tier recompilation / OSR version
     |
     +-- invalid assumption / deoptimization
     |
     v
Not-entrant / obsolete
     |
     | runtime ensures no unsafe active use
     v
Reclaimed by sweeper/unloading when safe
~~~

HotSpot có các trạng thái nội bộ như **in_use**, **not_entrant**, **zombie** và **unloaded** theo version. Đây **không phải Java object states** và không tương đương Young/Old GC generations.

### 9.1. Không xóa code cũ ngay lập tức

Một Java thread có thể đang thực thi compiled frame trong nmethod cũ khi VM quyết định không dùng version ấy cho calls mới. Nếu giải phóng machine code ngay sẽ phá memory safety. VM cần cơ chế an toàn để deopt/finish stack frames và reclaim khi không còn được sử dụng.

### 9.2. Nmethod sweeper/code cache flushing

HotSpot có **nmethod sweeper** và cơ chế **code cache flushing** để thu hồi cold/obsolete nmethods khi an toàn. Flag **UseCodeCacheFlushing** có liên quan trên JDK hỗ trợ. Không nên tắt nó chỉ để “giữ mã tối ưu mãi mãi”: như thế có thể tăng code cache pressure.

**Không được suy:** GC heap vừa chạy thì compiled nmethods cũ chắc chắn được xóa ngay. Class unloading, code unloading/sweeping và Java heap reclamation có liên quan nhưng không phải cùng sự kiện hay tài nguyên.

## 10. Khi code cache đầy

Một số HotSpot versions có thể ghi warning dạng:

~~~text
CodeCache is full. Compiler has been disabled.
Try increasing the code cache size using -XX:ReservedCodeCacheSize=
~~~

Đây chỉ là **dạng thông điệp minh họa**, không phải khẳng định mọi JDK báo y hệt hay compiler luôn disabled vĩnh viễn.

Những biểu hiện có thể xuất hiện:

- Compile task bị bỏ hoặc không cài đặt được nmethod mới.
- Java methods vẫn chạy bằng interpreter/C1 thay vì tier tối ưu.
- Application throughput giảm dù số requests không tăng.
- CPU có thể tăng vì code chạy ở tier thấp.
- p99 latency tăng sau một thời gian chạy, đặc biệt khi nhiều methods mới được gọi.
- Sweeper/code flushing có thể reclaim chỗ và JVM có thể phục hồi compilation theo chính sách thực tế.

**Code cache full không nhất thiết gây Java heap OutOfMemoryError.** Đôi khi ứng dụng tiếp tục phục vụ traffic nhưng chậm đi, khiến sự cố khó phát hiện nếu chỉ nhìn heap metrics.

### 10.1. Tại sao free bytes dương vẫn không đủ?

Một code blob cần đoạn space phù hợp về size/alignment và code heap type. Khi fragmentation hoặc một segment đã cạn, tổng free của cả cache không bảo đảm cấp được blob. Cần đọc **per-code-heap used/free/fragmentation** thay vì chỉ tổng cache.

### 10.2. Tại sao tăng Xmx không chữa?

- **-Xmx** giới hạn Java heap object memory.
- **ReservedCodeCacheSize** liên quan code cache.
- **Metaspace** liên quan nhiều loại class metadata.
- **CICompilerCount** ảnh hưởng compiler threads/native stacks.
- Container RSS là tổng kết quả từ các vùng và OS accounting.

Tăng -Xmx không tự cấp thêm chỗ cho nmethods. Thậm chí tăng heap quá nhiều trong container có thể làm ngân sách native nhỏ hơn.

## 11. ReservedCodeCacheSize và InitialCodeCacheSize

Hai tùy chọn JDK17:

- **-XX:ReservedCodeCacheSize=<size>**: định cấu hình maximum/reserved code cache budget.
- **-XX:InitialCodeCacheSize=<size>**: định cấu hình initial code cache size/commit theo implementation.

Theo Oracle Java 17 manual, code cache mặc định thông dụng có **ReservedCodeCacheSize=240 MB** với tiered compilation; khi **-XX:-TieredCompilation**, default tương ứng được tài liệu mô tả là **48 MB**. Manual JDK17 còn nêu giới hạn code cache reservation 2 GB. Các default/giới hạn này **là thông tin theo phiên bản**, không phải định luật xuyên JDK 11–26.

Ví dụ:

~~~bash
java -XX:ReservedCodeCacheSize=128m -XX:+PrintFlagsFinal -version
~~~

Cấu hình quá nhỏ hoặc mâu thuẫn kích thước segmented heaps có thể bị JVM từ chối. Nếu tăng cache để thử nghiệm, phải đo thực tế đã hết chỗ chưa, không đặt giá trị tùy ý chỉ vì ứng dụng có nhiều classes.

### 11.1. Reserved không phải committed

Giả sử code cache:

~~~text
Reserved:  240 MiB
Committed:  48 MiB
Used:       35 MiB
Free committed: 13 MiB (đơn giản hóa)
~~~

Các con số chỉ minh họa. Reserved thường là vùng virtual address space; committed là phần có OS backing/commit; used là space của code. RSS còn phụ thuộc mapping/physical pages touched. Không cộng reserved vào used Java heap rồi báo “process đang ăn từng ấy RAM”.

## 12. Chi phí CPU của compilation threads

C1/C2 compiler threads tiêu thụ cycles, CPU cache, memory bandwidth và có thể cạnh tranh với:

- HTTP worker threads.
- GC threads.
- Database client/event loop threads.
- JFR/profiling/monitoring threads.
- OS scheduler trên CPU quota nhỏ.

Ví dụ một container có **2 vCPU quota**, JVM có vài compiler threads cùng C1/C2 tasks: load cao khi warm-up có thể khiến request threads không được scheduler cấp CPU đúng lúc.

### 12.1. CICompilerCount

**-XX:CICompilerCount=<n>** điều chỉnh số compiler threads theo khả năng và các ràng buộc HotSpot. JVM có thể chọn số dựa trên CPU ergonomics/tiered policy.

Thử kiểm tra:

~~~bash
java -XX:CICompilerCount=2 -XX:+PrintFlagsFinal -version
~~~

Đừng suy compiler throughput tỷ lệ tuyến tính với threads: contention, queue mix, native memory, thread stacks và compiler IR allocations có thể giới hạn. Tăng threads có thể làm JIT nhanh hơn nhưng khiến service latency xấu do CPU bị tranh chấp. Giảm quá mạnh có thể kéo dài warm-up.

### 12.2. BackgroundCompilation

Thông thường HotSpot compile ở background. Các tùy chọn thay đổi foreground/background compilation nên được dùng cho nghiên cứu hơn là tối ưu mặc định; nếu buộc mutator chờ compiler, có thể thêm latency lớn vào request path.

### 12.3. Compile queue

~~~text
Hot methods create compilation requests
               |
               v
        Pending Compile Tasks
               |
        C1/C2 CompilerThreads
               |
               v
      Compiled code installation
~~~

Nếu arrival rate của tasks > service rate, queue tăng, hot methods có thể chạy ở interpreted/C1 tier lâu hơn. Queue dài tạm thời trong startup không nhất thiết là lỗi; queue dài mãi ở steady state cùng compiler CPU saturation là tín hiệu cần điều tra.

Một **compiler queue** không phải **HTTP request queue**: request thường không được chuyển thành compile task trực tiếp rồi chờ. Ảnh hưởng performance có thể gián tiếp qua code chưa tối ưu và CPU contention.

## 13. Native memory: compiler, code và những vùng khác

| Native/resource group | Có gì? | Điều cần tránh |
| --- | --- | --- |
| **Code cache** | Machine instructions, nmethods, stubs | Đừng gọi là Java heap |
| **Compiler arenas** | IR graphs, temporary buffers, optimizer data | Không tính trực tiếp bằng used code-cache |
| **Thread stacks** | CompilerThread C1/C2 stacks | Không tính trong live Java object graph |
| **Profiling metadata** | Method profiles, counters, receiver data | Không đồng nghĩa mọi data ở code-cache memory |
| **Compilation queues** | Tasks và bookkeeping | Không phải Java request queues |
| **Metaspace** | Runtime class/method metadata | Không phải toàn bộ native compiler allocation |
| **Java heap** | Java objects/arrays | Không tự cấp thêm code cache qua Xmx |

Một service có -Xmx=2g vẫn có thể tiêu thụ nhiều hơn 2 GiB process memory vì code cache, class metadata, stacks, direct buffers và GC overhead. Khi chạy trong container, cần giữ native memory headroom bên cạnh heap max.

### 13.1. Chi phí biên dịch đổi lấy lợi ích lâu dài

Một compilation task tốn vài chục milliseconds CPU có thể đáng giá nếu method chạy hàng triệu lần và tiết kiệm nhiều cycles mỗi invocation. Nhưng nếu method chỉ chạy vài lần, code cache và compiler resources có thể lớn hơn lợi ích.

Đó là lý do compilation thresholds và tiered heuristics cần tồn tại. Không ép JVM “compile mọi thứ” để đảm bảo performance.

## 14. Warm-up và steady-state

~~~text
Startup:
  Class loading / init
  Interpreter + initial profiles
  Many C1 requests
  C2 requests for increasingly hot methods
  Code cache occupancy grows

Warm/steady state:
  Most hot paths compiled when beneficial
  Queue may mostly drain
  Deopt/recompile may continue after workload changes
~~~

Warm-up không phải lúc nào kết thúc tại một thời điểm cố định. Workload thay đổi, new class loaders, generated proxies, uncommon traps có thể sinh tasks mới sau hàng giờ.

**Benchmark startup khác steady-state**: chạy microbenchmark 500 ms có thể chủ yếu đo interpreter/C1, trong khi production hot paths sau 30 phút là C2.

### 14.1. JMH và lỗi đo JIT

Tự đo System.nanoTime quanh một loop rồi in kết quả không tự đảm bảo benchmark chính xác. JIT có thể constant-fold, dead-code-eliminate, inline hoặc scalar-replace code. Dùng **JMH** khi benchmark micro-level cần warm-up/forks/blackhole chuẩn, và dùng load test với realistic data để đánh giá service.

## 15. Demo Java 17+ số 1: hot loop, kết quả độc lập JIT

Tạo **JitWarmupDemo.java**:

~~~java
public class JitWarmupDemo {
    static long hotSum(int limit) {
        long total = 0;
        for (int i = 0; i < limit; i++) {
            total += i % 7;
        }
        return total;
    }

    public static void main(String[] args) {
        long checksum = 0;
        for (int round = 0; round < 20; round++) {
            checksum += hotSum(100_000);
        }

        System.out.println("checksum=" + checksum);
        System.out.println("sameResult=true");
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 JitWarmupDemo.java
java JitWarmupDemo
~~~

**Stdout của chương trình:**

~~~text
checksum=5999900
sameResult=true
~~~

Lý do: mỗi nhóm 7 số i%7 có tổng 21. Trong 100.000 iterations có 14.285 nhóm trọn vẹn và 5 phần dư 0+1+2+3+4. Tổng hotSum là **299.995**; gọi 20 lần thành **5.999.900**.

Nếu chạy interpreter:

~~~bash
java -Xint JitWarmupDemo
~~~

Kết quả Java-level phải giống. Nhưng running time có thể khác nhiều; chương trình quá ngắn để quyết định collector/JIT option production.

### 15.1. Bật PrintCompilation

~~~bash
java -XX:+PrintCompilation JitWarmupDemo
~~~

Diagnostic compiler log có thể xen với stdout application. Compile IDs, compiler levels, OSR markers và timings **không cố định** giữa các máy/lần chạy/JDK. Không được coi một log bất kỳ là expected stdout chuẩn.

### 15.2. Giới hạn C1 tier để thử nghiệm

~~~bash
java -XX:+TieredCompilation -XX:TieredStopAtLevel=1 JitWarmupDemo
~~~

Application checksum vẫn 5999900 khi hoàn thành; pipeline JIT có thể khác. **-XX:-TieredCompilation** không đồng nghĩa **-Xint**; muốn tránh JIT trong thí nghiệm so interpreter, dùng -Xint.

## 16. Demo Java 17+ số 2: giữ JVM sống để chạy jcmd

Tạo **CodeCacheTarget.java**:

~~~java
public class CodeCacheTarget {
    static volatile long sink;

    static long work(int value) {
        long x = value;
        for (int i = 0; i < 10_000; i++) {
            x = (x * 31 + i) & 0x7fff_ffffL;
        }
        return x;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("ready");
        for (int round = 0; round < 2_000; round++) {
            sink = work(round);
            Thread.sleep(10);
        }
        System.out.println("completed");
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 CodeCacheTarget.java
java -XX:+TieredCompilation CodeCacheTarget
~~~

**Stdout khi ứng dụng hoàn thành bình thường:**

~~~text
ready
completed
~~~

Trong lúc chương trình chạy, mở terminal khác:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> Compiler.codecache
jcmd <PID> Compiler.queue
jcmd <PID> Compiler.codelist
~~~

Thay **<PID>** bằng PID thực. jcmd chỉ attach được khi JVM vẫn còn chạy và quyền phù hợp. Các lệnh **Compiler.codecache**, **Compiler.queue**, **Compiler.codelist** có trong tài liệu jcmd Java 17 cho HotSpot; mức độ output và names phụ thuộc build.

### 16.1. Compiler.codecache

In code-cache layout/bounds và thông tin usage. Với segmented mode, dùng nó để phân biệt code heaps thay vì chỉ quan sát Java heap.

### 16.2. Compiler.queue

Cho biết compilation requests đang chờ xử lý tại thời điểm xem. Queue trống không có nghĩa chưa từng JIT compile; tasks có thể đã hoàn tất.

### 16.3. Compiler.codelist

Liệt kê compiled methods đang sống trong code cache theo tool. Một method có thể có nhiều compiled versions, nhiều Java methods chưa từng được JIT compile; không dùng count này như số source methods.

### 16.4. CodeHeap Analytics

Một số HotSpot builds có **Compiler.CodeHeap_Analytics**. Kiểm tra:

~~~bash
jcmd <PID> help
jcmd <PID> help Compiler.CodeHeap_Analytics
~~~

Khi command có mặt, có thể xem thống kê fragmentation, method space/code heap. Không giả định nó luôn khả dụng trên mọi JDK vendor hoặc có cùng subcommands.

## 17. Dùng compiler logs và PrintInlining

HotSpot hỗ trợ:

~~~bash
java -XX:+PrintCompilation JitWarmupDemo
~~~

Với diagnostic options:

~~~bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining JitWarmupDemo
~~~

Thông tin hữu ích:

- Compilation IDs và tier.
- OSR vs regular compilation.
- Các phương thức được compile.
- Các quyết định inline thành công/không.
- Khi nmethod made not entrant.
- Compile task load trong warm-up.

Nhưng **-XX:+PrintInlining** có thể tạo output lớn và tăng overhead. Không bật tràn lan production nếu chưa quản lý log volume.

### 17.1. Unified JVM Logging

~~~bash
java -Xlog:help
~~~

Nếu tag codecache được JVM hỗ trợ:

~~~bash
java -Xlog:codecache=debug JitWarmupDemo
~~~

Bản JDK có thể khác về tag names và mức chi tiết, nên kiểm tra -Xlog:help trước. Đừng chế log format cố định cho codecache ở mọi HotSpot release.

## 18. Dùng CompilationMXBean

Java SE có **java.lang.management.CompilationMXBean**; bean có thể cho compiler name và aggregate compilation time khi JVM hỗ trợ.

Tạo **CompilerBeanDemo.java**:

~~~java
import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;

public class CompilerBeanDemo {
    public static void main(String[] args) {
        CompilationMXBean bean =
                ManagementFactory.getCompilationMXBean();

        System.out.println("beanPresent=" + (bean != null));

        if (bean != null) {
            System.out.println("compiler=" + bean.getName());
            System.out.println("timeSupported="
                    + bean.isCompilationTimeMonitoringSupported());
            if (bean.isCompilationTimeMonitoringSupported()) {
                System.out.println("totalCompileMs="
                        + bean.getTotalCompilationTime());
            }
        }
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 -encoding UTF-8 CompilerBeanDemo.java
java CompilerBeanDemo
~~~

**Không có stdout đầy đủ cố định**: compiler name và totalCompileMs phụ thuộc JVM/time; bean có thể null khi compiler facility không có, time monitoring có thể không được hỗ trợ. Đây là ví dụ quan sát API, không nên bịa một expected result.

## 19. JFR và Native Memory Tracking

### 19.1. Java Flight Recorder

**JFR** có thể cung cấp compilation events, compiler statistics và CPU/thread observations khi recording settings bật các events tương ứng.

~~~bash
java -XX:StartFlightRecording=filename=jit-lab.jfr,duration=20s,settings=profile CodeCacheTarget
~~~

Khi có recording:

~~~bash
jfr summary jit-lab.jfr
jfr print --events jdk.Compilation jit-lab.jfr
~~~

Không có jdk.Compilation event **không đồng nghĩa JIT đã tắt**: sự kiện có thể chưa bật hoặc không xuất hiện trong khoảng recording. JFR và PrintCompilation trả lời những câu hỏi khác nhau; để xem code cache hiện tại dùng jcmd Compiler.codecache.

### 19.2. Native Memory Tracking (NMT)

Trên HotSpot Java 17:

~~~bash
java -XX:NativeMemoryTracking=summary CodeCacheTarget
~~~

Từ terminal khác:

~~~bash
jcmd <PID> VM.native_memory summary
jcmd <PID> VM.native_memory baseline
jcmd <PID> VM.native_memory summary.diff
~~~

NMT thường phải bật từ JVM startup và có overhead. Các categories có thể gồm **Code**, **Compiler**, **Thread**, **Class**, **GC**. Không gộp category Code thành cả Java heap, và không suy mọi compiler IR memory nằm trong Code category.

Ví dụ minh họa:

~~~text
Code:
  reserved: 240 MiB
  committed: 35 MiB

Compiler:
  committed: 8 MiB
~~~

Các số chỉ là giả định, không phải log thật. Chúng nhấn mạnh cần nhìn **reserved vs committed** và native allocations ngoài Java heap.

## 20. Sự cố code cache full

Một tình huống có thể:

~~~text
Startup -> many methods get compiled
After warmup -> C2 code cache grows
After redeploy -> more generated proxies/classes
Code cache near capacity -> sweeper cannot keep up
Log warns: code cache full
Compiler stops installing optimized code
Application throughput declines
~~~

Lúc này:

1. Xác nhận **code cache full** qua log/jcmd.
2. Phân biệt **which code heap** đầy nếu segmented.
3. Đo **code cache committed/used/free** và fragmentation.
4. Kiểm tra **Compiler.queue** có backlog không.
5. Dùng JFR/PrintCompilation xem compilation churn/deopts/OSR.
6. Xem **generated classes**, dynamic proxies, lambdas và application redeploy.
7. Kiểm tra class-loader retention/unloading.
8. Kiểm tra ReservedCodeCacheSize và per-heap sizing thực.
9. Đo process RSS, NMT Code/Compiler, CPU quota.
10. Chỉ sau đó mới tăng cache budget hoặc thay compile policy.

### 20.1. Không chỉ tăng CodeCache vô hạn

- Cache quá nhỏ có thể đầy, nhưng cache quá lớn không tự làm compiler nhanh hơn.
- Native memory budget hữu hạn; tăng code-cache capacity có thể tăng memory usage của JVM.
- Nếu problem là high compiler CPU/queue, tăng cache không giải quyết task service rate.
- Nếu class generation liên tục, phải sửa root cause.
- Nếu code heap fragmentation/coarsening, cần xem phân vùng và sweeper activity.

### 20.2. Code cache đầy không đồng nghĩa OOME Java heap

JVM có thể tiếp tục interpreted execution/C1 code, tạo **performance degradation** thay vì ném Java heap space OOME. Native-code allocation failure trong một số điều kiện có thể nghiêm trọng hơn; luôn dựa vào thông báo và trạng thái tiến trình thực.

## 21. Compiler CPU cao và warm-up lâu

Một service mới startup có thể:

- CPU process tăng do C1/C2.
- Latency dao động trong warm-up.
- Code cache usage tăng nhanh rồi ổn định.
- Compiler.queue đông một thời gian.
- Sau khi hot methods compiled, throughput cải thiện.

Đây thường là hiệu ứng bình thường. Dấu hiệu đáng quan tâm là **compiler CPU tiếp tục cao trong steady state** do code churn, class generation, deoptimization/recompilation liên tục, hoặc tasks cực lớn.

Một số hướng:

- Warm-up representative traffic trong staging trước khi nhận full load.
- Đo CPU quotas trên container thực.
- So sánh compilation queue snapshots trước/sau warm-up.
- Không ép mọi method C2 ngay khi load.
- Cân nhắc TieredStopAtLevel cho short-lived workloads **sau benchmark**.
- Không giảm CICompilerCount ngay khi compiler threads xuất hiện trên CPU profiler.

## 22. Chi phí deoptimization và code churn

Một hệ framework tạo classes/proxies liên tục hoặc trải qua sự thay đổi runtime type profiles có thể gây:

~~~text
Profile -> Compile C2 -> Assumption changes
          |
          v
       Deopt
          |
          v
    Recompile new nmethod
          |
          v
    Old code becomes not entrant
          |
          v
     Sweeper later reclaims
~~~

Nhiều versions sống chồng nhau có thể tăng code cache occupancy và compiler CPU. Một vài deopts là bình thường; chuỗi lặp không dứt mới đáng phân tích.

**Không gán mọi code cache growth cho memory leak**. Cache thường tăng trong warm-up rồi đạt plateau. Muốn chẩn đoán leak/churn cần observe trend, live compiled code, cache pressure và class-loader lifetime.

## 23. Các hiểu lầm cần tránh

1. **“Code cache là Java heap.”** Sai: đây là HotSpot native/generated code memory.
2. **“JVMS bắt buộc mọi JVM có code cache giống HotSpot.”** Sai.
3. **“JIT compilation không tốn CPU vì chạy background.”** Sai: compiler threads dùng CPU thật.
4. **“Compiler threads chính là javac.”** Sai: javac compile trước runtime; C1/C2 compile trong runtime.
5. **“Mọi method đều được C2 compile.”** Sai: hotness/heuristics/resources quyết định.
6. **“Tiered là lúc nào cũng 0→1→2→3→4.”** Sai: policy có shortcuts.
7. **“Tắt TieredCompilation là tắt JIT.”** Sai: dùng -Xint để thử interpreted mode.
8. **“C1 code luôn ở profiled heap.”** Sai: còn non-profiled C1 tiers.
9. **“Code cache có ba phần kích thước bằng nhau.”** Sai: sizing theo flags/ergonomics.
10. **“Inlining luôn làm code nhỏ hơn.”** Sai: có thể tăng code cache footprint.
11. **“Một Java method bằng một nmethod.”** Sai: OSR/tiered/recompilation/inlining.
12. **“Deoptimization chứng minh có JVM bug.”** Sai: thường bảo toàn speculative assumptions.
13. **“nmethod bị not entrant thì bytes được xóa tức thì.”** Sai: an toàn compiled frames/sweeper.
14. **“GC heap xong thì code cache phải giảm.”** Sai: lifecycle khác nhau.
15. **“Code cache đầy luôn ném OutOfMemoryError ngay.”** Sai: có thể compiler disabled và interpreter fallback.
16. **“Tăng Xmx là tăng ReservedCodeCacheSize.”** Sai: hai budgets khác.
17. **“Reserved 240 MiB là 240 MiB RSS đang dùng.”** Sai: reserved, committed, used khác nhau.
18. **“Compiler.queue trống có nghĩa JIT chưa hoạt động.”** Sai: tasks có thể đã hoàn thành.
19. **“Compiler.codelist là toàn bộ Java source methods.”** Sai: chỉ compiled code entries còn sống.
20. **“PrintCompilation output luôn cố định.”** Sai: compilation timing/IDs/levels thay đổi.
21. **“NMT Code bao gồm toàn bộ Compiler memory.”** Sai: category khác nhau.
22. **“CICompilerCount tăng gấp đôi thì compile nhanh gấp đôi.”** Sai: CPU contention/queues/memory.
23. **“Warm-up benchmark 1 giây đại diện steady state.”** Sai: JIT tiering cần thời gian/profile.
24. **“Code cache đầy có thể chữa bằng System.gc().”** Không bảo đảm.
25. **“Một class-file Code attribute là một nmethod trong code cache.”** Sai: code bytecode khác machine code.
26. **“Cache free dương nghĩa chắc cấp được mọi blob.”** Sai: fragmentation/segment type có thể cản.
27. **“Compiled code không thể bị invalidated.”** Sai: deopt/unloading/recompile là cơ chế chuẩn HotSpot.
28. **“Tăng code cache không có memory overhead.”** Sai: allocation/native budget vẫn hữu hạn.
29. **“Một service thiếu CPU compiler có thể chữa bằng tăng heap.”** Không trực tiếp.
30. **“JIT logs không cần đúng JDK version.”** Sai: flags/log fields thay đổi.

## 24. Bài tập có đáp án

**Bài 1.** Code cache chứa gì và có phải Java heap không?

**Đáp án:** Chứa native code/code blobs của HotSpot, khác managed Java heap chứa objects/arrays.

**Bài 2.** Javac và C2 khác nhau ra sao?

**Đáp án:** Javac tạo Java bytecode; C2 tối ưu/biên dịch bytecode hot thành CPU-specific machine instructions khi runtime.

**Bài 3.** Vì sao HotSpot dùng background compiler threads?

**Đáp án:** Để mutator có thể tiếp tục thực thi while compilation diễn ra, giảm blocking trên đường nóng; vẫn có CPU/native costs.

**Bài 4.** Tại sao C1 và C2 cùng tồn tại?

**Đáp án:** C1 giúp compile sớm/warm-up/profile, C2 tốn CPU hơn nhưng có thể tăng steady-state performance.

**Bài 5.** Dùng -XX:-TieredCompilation có tắt JIT không?

**Đáp án:** Không nhất thiết; vẫn có non-tiered JIT, còn -Xint dùng để so interpreter.

**Bài 6.** OSR là gì?

**Đáp án:** Cho phép chuyển sang compiled code khi một method đang chạy một hot loop mà chưa return.

**Bài 7.** Segmented code cache gồm các phân vùng nào ở HotSpot JDK17?

**Đáp án:** Non-method, profiled method, non-profiled method heaps khi segmentation được bật.

**Bài 8.** Tại sao non-method heap không được sweep tùy tiện giống mọi C1 nmethod?

**Đáp án:** Chứa runtime/interpreter/compiler stubs có thể phải tồn tại lâu để JVM hoạt động đúng.

**Bài 9.** Compiler code bị deopt có thể còn trong cache không?

**Đáp án:** Có; reclaim phải đảm bảo code không còn unsafe active use.

**Bài 10.** Reserved 240 MiB, committed 40 MiB; code cache có chắc chiếm 240 MiB physical RAM không?

**Đáp án:** Không; reserved virtual address space khác committed/used.

**Bài 11.** Code cache full thì JVM nhất định OOME Java heap không?

**Đáp án:** Không; có thể compiler disabled, methods fallback interpreter và performance giảm.

**Bài 12.** Tại sao code cache capacity lớn hơn không luôn tăng throughput?

**Đáp án:** Nếu chưa cạn, bottleneck nằm ở compiler CPU, application processing hoặc memory bandwidth; tăng capacity không tự làm biên dịch nhanh.

**Bài 13.** Compilation queue dài nhưng cache còn free, nguyên nhân khả dĩ?

**Đáp án:** Compiler tasks nhiều/phức tạp, CICompilerCount thấp, CPU starvation hoặc quota hạn chế.

**Bài 14.** Dùng lệnh nào xem code cache thực của HotSpot?

**Đáp án:** jcmd <PID> Compiler.codecache.

**Bài 15.** Xem compiler tasks chờ xử lý bằng lệnh nào?

**Đáp án:** jcmd <PID> Compiler.queue.

**Bài 16.** Làm sao xem compiled methods đang sống?

**Đáp án:** jcmd <PID> Compiler.codelist.

**Bài 17.** Với hotSum(100_000) chạy 20 lần, checksum là bao nhiêu?

**Đáp án:** 5.999.900.

**Bài 18.** Nếu đổi -Xint mà Java stdout đổi khác (không do timing/concurrency), điều gì đáng nghi?

**Đáp án:** Chương trình có data race/nondeterministic behavior, native/undefined assumptions hoặc lỗi runtime; JIT không được đổi sequential Java semantics.

**Bài 19.** NMT dùng để đo gì?

**Đáp án:** Native allocations theo VM categories như Code, Compiler, Thread, Class; không thay CPU profiler hoặc heap dump.

**Bài 20.** Vì sao phải đo p99 cả trước/sau JIT tuning?

**Đáp án:** Giảm compilation CPU có thể kéo dài interpreted execution; tăng compiler threads có thể tranh CPU; chỉ đo throughput trung bình có thể bỏ qua tail latency.

## 25. Quy trình triển khai/tuning thực tế

1. **Xác nhận runtime:** HotSpot vendor, JDK17 build, architecture, container limits.
2. **Đo baseline:** p95/p99, throughput, CPU usage, startup time, heap/RSS.
3. **Kiểm tra final flags:** SegmentedCodeCache, ReservedCodeCacheSize, TieredCompilation, CICompilerCount.
4. **Quan sát code-cache segments:** Compiler.codecache và per-heap usage.
5. **Quan sát compilation backlog:** Compiler.queue theo nhiều mốc.
6. **Đọc compilation events:** PrintCompilation/JFR khi cần.
7. **Phân biệt warm-up bình thường với code churn bất thường.**
8. **Xem deoptimization/recompilation do runtime profiles/classes mới.**
9. **Đo native memory:** NMT Code/Compiler/Thread/Class và RSS thực.
10. **Nếu code cache full:** điều tra which heap/fragmentation; tăng budget chỉ khi có evidence.
11. **Nếu CPU compiler quá cao:** phân biệt load khởi động và sustained backlog; không tùy tiện hạ/tăng compiler threads.
12. **Benchmark bằng workload thật và JMH khi micro-level**, có warm-up, nhiều runs.
13. **Chỉ đổi một biến tuning mỗi lần**, ghi kết quả và rollback nếu regression.
14. **Không dùng -Xmx làm thay code-cache budget.**
15. **Kiểm tra lại khi nâng JDK**, vì compiler implementations/flags/defaults có thể thay đổi.

### Tài liệu chính thức

- **Oracle JDK 17 — java command, ReservedCodeCacheSize, SegmentedCodeCache, TieredCompilation:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle JDK 17 — jcmd Compiler.codecache/queue/codelist:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- **Oracle JDK 17 — Native Memory Tracking:** https://docs.oracle.com/en/java/javase/17/vm/native-memory-tracking.html
- **OpenJDK JEP 197 — Segmented Code Cache:** https://openjdk.org/jeps/197
- **Oracle HotSpot Performance Enhancements — Tiered Compilation/Code Cache:** https://docs.oracle.com/en/java/javase/17/vm/java-hotspot-virtual-machine-performance-enhancements.html
- **OpenJDK JDK 17 HotSpot Code Cache source:** https://github.com/openjdk/jdk17u/tree/master/src/hotspot/share/code
- **OpenJDK JDK 17 HotSpot Compiler source:** https://github.com/openjdk/jdk17u/tree/master/src/hotspot/share/compiler
- **Java SE 17 — CompilationMXBean:** https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/CompilationMXBean.html
- **Java SE 17 — jfr command:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- **JVMS 17 — Method Area abstraction:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.4

**Kết luận:** JIT tăng tốc hot code nhưng tiêu tốn **compiler CPU, native temporary memory và code-cache capacity**. HotSpot JDK17 thường dùng tiered C1/C2 compilation và segmented code cache gồm non-method, profiled, non-profiled heaps. Native nmethods có thể được thay thế, deoptimize và reclaim an toàn. **Code cache đầy có thể khiến ứng dụng chạy chậm mà không gây Java heap OOME.** Muốn tuning phải quan sát **code cache, compilation queues, warm-up, GC/native memory và CPU contention**, không chỉ thay -Xmx hoặc đo một lần chạy nhỏ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="interpretation-and-jit-009">Đối chiếu chế độ -Xint với thực thi kết hợp trong thí nghiệm có kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

# Đối chiếu chế độ -Xint với thực thi kết hợp trong thí nghiệm có kiểm soát

## 1. Mục đích: đo một thay đổi JVM mà không đánh đồng tốc độ với tính đúng

Trong **HotSpot JVM**, cùng một tệp bytecode có thể được chạy bằng **trình thông dịch (interpreter)**, **mã máy do JIT biên dịch**, hoặc phối hợp cả hai. Hai tùy chọn quan trọng khi quan sát sự khác biệt là:

- **`-Xint`:** yêu cầu HotSpot thực thi **Java bytecode chỉ bằng interpreter**, tắt JIT compilation cho bytecode. Không còn lợi ích của machine code do JIT sinh cho các Java methods.
- **`-Xmixed`:** chế độ thực thi **kết hợp interpreter + JIT**: bytecode được diễn giải khi thích hợp; những methods/loops đủ nóng có thể được compile thành native machine code. Đây là **mặc định** ở HotSpot thông thường; có thể ghi tường minh để dễ đối chiếu.

Các modes này **không làm thay đổi ý nghĩa của ngôn ngữ Java**. Nếu một chương trình hợp lệ, không phụ thuộc data races hoặc những hành vi ngoài đặc tả, kết quả logic phải nhất quán dù bytecode chạy interpreted hay JIT-compiled. Điều có thể khác là **thời gian**, **CPU usage**, **warm-up**, **compilation activity** và cách JVM phân bố tài nguyên.

### Câu hỏi thí nghiệm cần trả lời

1. Hai chế độ có cho **cùng checksum/output** với cùng chương trình và input không?
2. Một workload tính toán lặp nhiều lần có **độ trễ** khác ra sao trong hai chế độ?
3. Vì sao lần khởi động đầu, giai đoạn warm-up và steady state phải **đo riêng**?
4. Dấu hiệu nào cho thấy JIT thực sự compile methods trong `-Xmixed`?
5. Có thể kết luận gì từ một lần đo, và điều gì **không thể kết luận**?
6. `-Xcomp`, `-XX:-TieredCompilation` và `-XX:TieredStopAtLevel` khác `-Xint` như thế nào?

**Giới hạn:** bài này hướng dẫn **thí nghiệm nhỏ có thể tái lập về mặt chương trình** trên HotSpot JDK 17+, không hứa cùng thời gian chạy, tỷ lệ tăng tốc hay cùng JIT compilation events trên mọi máy.

## 2. Hai lệnh gốc và điều thực sự khác nhau

Sau khi đã biên dịch một tệp Java thành class file:

```bash
java -Xint ExecutionModeDemo
java -Xmixed ExecutionModeDemo
```

Ở mức trực giác:

```text
                     Same .class bytecode
                              |
                     HotSpot JVM startup
                              |
              +---------------+----------------+
              |                                |
            -Xint                            -Xmixed
              |                                |
    Interpreter executes                 Interpreter starts
    application bytecode                  executing bytecode
              |                                |
      No JIT compilation                  invocation/backedge
      of Java bytecode                    hotness/profile data
              |                                |
              |                        sufficiently hot?
              |                           /          \
              |                         no            yes
              |                         |              |
              |                    interpreter     C1 / C2 JIT
              |                                         |
              +----------------+------------------------+
                               |
                         Same Java semantics
                               |
                      Different CPU and timing
```

Sơ đồ minh họa **không phải một lịch compile cố định**: JVM có thể tạo C1/C2 code, OSR code cho loop, inline methods, deoptimize code hoặc không bao giờ JIT compile những methods chạy quá ít.

### 2.1. `-Xint` có tắt toàn bộ native code của tiến trình không?

**Không.** HotSpot vẫn có runtime code viết bằng C/C++, native stubs, VM services, system calls, JNI và những phần JVM không phải bytecode Java thuần túy. `-Xint` nói rằng **Java bytecode được thực thi theo interpreter**, không phải “CPU không được chạy bất kỳ native instruction nào”.

Một ứng dụng gọi native library vẫn dùng library đó; đừng mô tả `-Xint` là chế độ “mọi thao tác đều chạy dưới interpreter”.

### 2.2. `-Xmixed` có nghĩa tất cả methods đều JIT ngay không?

**Không.** Một cold method có thể vẫn interpreted. JIT compilation được quyết định bởi **HotSpot compilation policy**, các counters, profiling và tài nguyên compiler/code cache. Trong HotSpot tiered configuration, những methods nóng có thể qua C1 rồi C2, và những loop dài có thể kích hoạt on-stack replacement (OSR).

### 2.3. Cả hai modes vẫn dùng GC

Interpreter-only không vô hiệu hóa garbage collection, class loading, verifier, synchronization, heap management hay memory safety của Java. Một lần đo `-Xint` có thể chậm do interpreted execution và vẫn có GC pauses; một lần đo `-Xmixed` có thể xuất hiện JIT compilation và GC.

**Chỉ kết luận nguyên nhân thời gian chậm khi có bằng chứng**, không tự gán mọi millisecond chênh lệch cho JIT.

## 3. Định nghĩa “thí nghiệm có kiểm soát” trong JVM

Đối với bài này, **biến độc lập** là execution mode `-Xint` hoặc `-Xmixed`. Những yếu tố còn lại cố gắng giữ nguyên:

| Thành phần | Thiết lập thí nghiệm |
| --- | --- |
| JVM vendor/build | Cùng đường dẫn `java`, cùng `java -version` |
| Java class files | Cùng `ExecutionModeDemo.class` do `javac --release 17` tạo |
| Input | Cùng warm-up calls, measured calls, steps per call |
| Heap flags | Ban đầu để JVM defaults giống nhau; nếu đặt `-Xms/-Xmx` thì cả hai modes cùng dùng |
| Machine | Cùng host/container, CPU quota và workload nền càng ổn định càng tốt |
| Chế độ chạy | Từng process riêng, mỗi lần chỉ đổi `-Xint`/`-Xmixed` |
| Đo | Cùng cách gọi `System.nanoTime`, không in ra console bên trong vòng tính |
| Kết quả | So sánh checksum đúng trước, latency sau |
| Lặp | Nhiều runs độc lập, đổi/thay phiên thứ tự để giảm bias |

**Vì sao cần process riêng?** Một JVM đang chạy có trạng thái profiling/compiled methods, heap và caches tích lũy theo thời gian. Chạy hai chế độ trong hai processes mới giúp không dùng nhầm compiled code đã làm nóng của lần thử trước.

### 3.1. Biến phụ thuộc cần quan sát

- **Correctness:** checksum và các giá trị logic in ra.
- **Elapsed time:** thời gian *measured section* tính bằng `System.nanoTime()`.
- **Time per call:** có thể tính từ elapsed time và số calls, nhưng phải hiểu đó là average của workload đang đo, không phải thời gian từng invocation.
- **Compilation activity:** log `-XX:+PrintCompilation` khi dùng `-Xmixed`.
- **Whole-process time:** có thể đo riêng từ bên ngoài, bao gồm JVM startup, class loading và teardown.
- **CPU/memory:** thông số OS, nếu mục tiêu thí nghiệm mở rộng.

### 3.2. Thứ không thể kiểm soát tuyệt đối

- CPU frequency/boost, nhiệt độ và power management.
- Scheduling, JVM compiler threads, background services.
- GC timing và safepoints.
- Cache state, JIT inlining choices, deoptimization.
- JDK ergonomics và available CPUs của container.
- Native code/intrinsics sử dụng bởi Java standard library.

Vì vậy phải **đo nhiều lần và báo phân phối kết quả**, thay vì một “giá trị chuẩn cho mọi máy”.

## 4. Chuẩn bị môi trường Java 17+

Đầu tiên xác nhận runtime:

```bash
java -version
javac -version
```

Để biết JVM có hỗ trợ HotSpot flags nào:

```bash
java -Xint -version
java -Xmixed -version
```

Những lệnh trên **không phải benchmark**: chúng chỉ kiểm tra parser/runtime nhận các options và in thông tin JVM. Không suy rằng `java -Xint -version` nhanh/chậm hơn `java -Xmixed -version` là kết quả cho một ứng dụng thực.

Nên dùng một JDK với đủ `java` và `javac` tương thích. Nếu IDE cấu hình nhiều JDK, **xác minh chính xác binary** đang dùng để chạy hai modes; nếu không, sự khác biệt phiên bản có thể lớn hơn sự khác biệt execution mode.

### 4.1. Cần cùng JVM implementation

`-Xint` và `-Xmixed` trong bài này là **HotSpot-specific execution mode switches**. Những VM khác có thể hỗ trợ, từ chối hoặc triển khai khác. Không tuyên bố mọi JVM buộc có hai options theo Java Language Specification.

### 4.2. Không thêm logging vào vòng đo ngay lúc đầu

Các flags `PrintCompilation`, `PrintInlining` và logging chi tiết có thể phát sinh I/O, thay đổi timing/scheduling. Trước tiên đo baseline **không bật diagnostic output**, sau đó **chạy lại riêng** để quan sát compiler events.

## 5. Ví dụ Java 17+ hoàn chỉnh cho cả hai chế độ

Tạo tệp **`ExecutionModeDemo.java`** với toàn bộ nội dung sau:

```java
public final class ExecutionModeDemo {
    private static volatile long sink;

    static int mix(int x) {
        x ^= x >>> 13;
        x *= 0x5bd1e995;
        x ^= x >>> 15;
        return x;
    }

    static long work(int calls, int steps) {
        long total = 0;
        for (int c = 0; c < calls; c++) {
            int value = c + 1;
            for (int i = 0; i < steps; i++) {
                value = mix(value + i);
            }
            total += Integer.toUnsignedLong(value);
        }
        sink = total;
        return total;
    }

    public static void main(String[] args) {
        final int warmupCalls = 800;
        final int measuredCalls = 1500;
        final int steps = 1024;

        work(warmupCalls, steps);
        long start = System.nanoTime();
        long checksum = work(measuredCalls, steps);
        long nanos = System.nanoTime() - start;

        System.out.println("warmupCalls=" + warmupCalls);
        System.out.println("measuredCalls=" + measuredCalls);
        System.out.println("stepsPerCall=" + steps);
        System.out.println("checksum=" + checksum);
        System.out.println("sinkMatches=" + (sink == checksum));
        System.out.printf(java.util.Locale.ROOT,
                "elapsedMs=%.3f%n", nanos / 1_000_000.0);
    }
}
```

Chương trình có:

- Một **method nhỏ `mix`** đủ đơn giản để JIT có thể cân nhắc inlining.
- Một **`work`** chứa hai vòng lặp; workload đủ rõ để đếm số lần thực hiện.
- **Data dependency**: kết quả iteration trước là đầu vào iteration sau, giảm nguy cơ toàn bộ loop trở thành phép tính bất biến tầm thường.
- **`volatile sink`** làm giá trị tính cuối trở thành một side effect có thể quan sát; không tự đảm bảo mọi machine instruction giống nhau giữa hai modes.
- **Warm-up trước đo** và sử dụng `System.nanoTime` cho **khoảng đo**.
- **Không in log trong vòng lặp tính**, tránh I/O áp đảo computation.
- **`Locale.ROOT`** để định dạng dấu thập phân trong dòng timing nhất quán hơn giữa các locale.

### 5.1. Biên dịch

```bash
javac --release 17 ExecutionModeDemo.java
```

Sử dụng cùng class file cho mọi lần chạy. Không cần build bằng các flags `-Xint`/`-Xmixed` vì chúng là **runtime options** của lệnh `java`, không phải tùy chọn compile source của `javac`.

### 5.2. Chạy hỗn hợp mặc định và tường minh

```bash
java ExecutionModeDemo
java -Xmixed ExecutionModeDemo
```

Trên HotSpot, `-Xmixed` là execution mode mặc định điển hình, nhưng thực tế cần xem JVM options/environment nếu có cấu hình đặc biệt.

### 5.3. Chạy interpreter-only

```bash
java -Xint ExecutionModeDemo
```

### 5.4. Các dòng stdout xác định

Khi chạy thành công ở cả hai modes, **năm dòng đầu** phải là:

```text
warmupCalls=800
measuredCalls=1500
stepsPerCall=1024
checksum=3215795315003
sinkMatches=true
```

**Dòng cuối là số đo biến thiên**, ví dụ định dạng:

```text
elapsedMs=2.291
```

Giá trị `2.291` là **một phép đo cục bộ của một lần chạy** `-Xmixed` khi thử nghiệm, **không phải stdout chính xác cố định**. Chạy lại hoặc dùng `-Xint` sẽ cho elapsedMs khác. Cần phân biệt **kết quả logic đã được kiểm chứng** và **thời gian đo không mang guarantee**.

### 5.5. Kiểm tra thực nghiệm đã thực hiện

Ví dụ đã được biên dịch với `javac --release 17`, sau đó chạy trên OpenJDK/HotSpot ở bốn cấu hình:

- `-Xmixed`
- `-Xint`
- `-XX:-TieredCompilation`
- `-Xcomp`

Cả bốn lần chạy đều in cùng **checksum `3215795315003`** và `sinkMatches=true`. Việc chúng có thời gian khác nhau không chứng minh rằng mỗi cấu hình sẽ có cùng thứ hạng tốc độ trên mọi hệ thống; cách triển khai JDK/build và lượng warm-up có ảnh hưởng lớn.

## 6. Giải thích chính xác phép đo của chương trình

### 6.1. Workload đã làm những gì?

`work(warmupCalls, steps)` gọi `mix` bên trong 800 × 1024 iterations. Tiếp đó `work(measuredCalls, steps)` làm thêm 1500 × 1024 iterations dưới khoảng `nanoTime`.

Tổng số thao tác `mix` theo cấu trúc vòng lặp Java:

```text
Warm-up:   800 × 1024 =   819,200 iterations
Measured: 1500 × 1024 = 1,536,000 iterations
Total:                = 2,355,200 iterations
```

Đây là **số lần đánh giá logic** từ cấu trúc Java, không nhất thiết là số machine-level call instructions. JIT có thể inline `mix` vào `work`.

### 6.2. Vì sao checksum giống nhau?

Phép tính dùng integer arithmetic với Java semantics:

- Dịch bit không dấu `>>>`.
- Nhân `int` theo quy tắc overflow/wrap-around modulo \(2^{32}\).
- XOR bitwise.
- `Integer.toUnsignedLong` chuyển `int` thành giá trị long không dấu tương ứng.

Dù interpreter hay compiled code thực thi, **kết quả Java phải giống nhau** nếu JVM hợp lệ.

### 6.3. `sinkMatches=true` có tác dụng gì?

`sink` là static volatile field được gán trong `work`. Sau khi đo, code đọc lại nó để kiểm tra kết quả được giữ. Việc viết volatile là một **observable side effect** theo Java Memory Model, tránh một số tình huống benchmark đơn giản trong đó JIT có thể loại hẳn phép tính vì kết quả không được sử dụng.

Tuy nhiên đây **không phải blackhole hoàn hảo của JMH** và không ngăn mọi hình thức compiler optimization. Bài demo thích hợp để học chế độ thực thi; không thay công cụ benchmark chuyên dụng.

### 6.4. `System.nanoTime()` đo gì?

Nó trả giá trị thời gian đơn điệu phù hợp để đo **elapsed durations** khi lấy hiệu, không phải đồng hồ wall-clock có ngày/tháng/năm. Phép đo:

```java
long start = System.nanoTime();
long result = work(1500, 1024);
long elapsed = System.nanoTime() - start;
```

là đoạn minh họa lấy từ chương trình. `elapsed` là **thời gian trôi qua giữa hai lần đọc clock**, bao gồm những gián đoạn có thể xảy ra như scheduler preemption, GC pauses hoặc JIT compilation xen vào. Nó không phải thời gian CPU thuần của riêng hàm `work`.

### 6.5. Vì sao không in “thời gian chuẩn” cố định?

Trên mỗi JDK/OS/CPU, elapsed time có thể thay đổi. Ngay trên cùng một máy, lần đầu chạy, các lần sau, CPU load và compilation timing cũng khác. **Không bao giờ so elapsedMs với một hằng số cố định để chấm pass/fail**.

## 7. Warm-up của `-Xmixed` và `-Xint` có ý nghĩa khác nhau

Ở **`-Xmixed`**, warm-up có thể:

- Nạp, verify, link và initialize những classes cần thiết.
- Thực thi những paths bằng interpreter.
- Tăng invocation/backedge counters.
- Thu branch/type profiles.
- Kích hoạt C1/C2 compilation và OSR khi đủ điều kiện.
- Làm nóng code/data cache.

Ở **`-Xint`**, warm-up vẫn có thể làm những việc như **class loading, initialization, cache warming và GC activity**, nhưng **không chuyển bytecode Java sang JIT-compiled native code** theo chế độ đó.

Do vậy:

> Warm-up là một bước kiểm soát tình trạng khởi động, nhưng “warm-up cùng số iterations” **không có nghĩa JVM đạt cùng trạng thái tối ưu hóa**.

### 7.1. Warm-up không tự bảo đảm steady state

800 × 1024 iterations trong demo là mức thực hành, **không cam kết** JIT đã đạt C2 level 4 cho mọi method trước khi lấy `start = System.nanoTime()`. Các compiler threads có thể vẫn đang xử lý công việc trong lúc measured phase bắt đầu.

Muốn nghiêm túc phân tích steady state, cần quan sát JIT logs/traces hoặc tăng warm-up bằng những điều kiện benchmark được thiết kế đúng, thay vì chỉ giả định số lần cố định là đủ.

### 7.2. Khi nào `-Xint` có thể tỏ ra tương tự `-Xmixed`?

Nếu chương trình **rất ngắn**, khởi động/chấm dứt process và I/O chiếm phần lớn thời gian, sự khác biệt ở phần bytecode được compile có thể rất nhỏ. Chi phí JVM startup cũng có thể che khuất khác biệt computation.

Vì vậy nếu chỉ đo `java -Xint -version` vs `java -Xmixed -version`, bạn chưa đo lợi ích thực tế của JIT trên code nóng.

### 7.3. Ngược lại, công việc rất nóng có thể làm chênh lệch lớn

Khi cùng một phép tính chạy hàng triệu lần, interpreter phải xử lý bytecode trong nhiều iterations, còn JIT có thể optimize/inlining/strength reduction theo dữ liệu runtime. Nhưng mức tăng tốc cụ thể **không được Java Specification cam kết** và phụ thuộc workload.

## 8. Một kết quả thử nghiệm cục bộ và cách diễn giải

Trong một lần chạy bằng OpenJDK/HotSpot, chương trình trả những elapsed times sau:

| Chế độ | Measured section | Correctness |
| --- | ---: | --- |
| `-Xmixed` | **2.291 ms** | PASS — checksum giống |
| `-Xint` | **25.124 ms** | PASS — checksum giống |
| `-XX:-TieredCompilation` | **2.230 ms** | PASS — checksum giống |
| `-Xcomp` | **4.327 ms** | PASS — checksum giống |

**Đây là một mẫu đo cục bộ duy nhất, chỉ minh họa cách đọc dữ liệu**, không phải dữ liệu benchmark đủ để tuyên bố `-Xmixed` nhanh hơn `-Xint` đúng 10.97 lần cho mọi chương trình. Không dùng bảng này làm SLA, threshold hay guideline chọn production flag.

### 8.1. Cách hiểu đúng

Trong mẫu cục bộ, measured section với `-Xmixed` ngắn hơn `-Xint`. Điều này **phù hợp** với dự đoán rằng hot computation được hưởng lợi từ JIT. Tuy nhiên, chỉ từ elapsedMs chưa thể phân tách chính xác số millisecond dành cho compilation, GC, CPU scheduling và machine-code execution.

### 8.2. Cách hiểu sai

Không được kết luận:

- “Mọi Java service đều chậm gấp mười nếu bật `-Xint`.”
- “`-Xcomp` luôn nhanh hơn `-Xint` nhưng chậm hơn `-Xmixed`.”
- “`-XX:-TieredCompilation` luôn là cấu hình production nhanh nhất.”
- “Không có GC/JIT overhead vì chương trình chỉ in sáu dòng.”
- “Một lần đo đủ để xếp hạng các VM flags.”

**Mọi kết luận hiệu năng cần repetitions, mô tả môi trường và phân phối số đo.**

## 9. Lặp thí nghiệm và chống sai lệch thứ tự

Một cách học có kiểm soát:

1. Biên dịch tệp **một lần**, không rebuild giữa hai modes.
2. Chạy **ít nhất vài vòng độc lập** cho mỗi chế độ.
3. Không mở IDE build/indexing nặng song song nếu có thể.
4. Xen kẽ thứ tự thay vì luôn chạy tất cả `-Xint` trước rồi tất cả `-Xmixed`.
5. Ghi lại từng `elapsedMs`, không chỉ chọn lần nhanh nhất.
6. So sánh median, độ phân tán (p25/p75 hoặc p95) và kiểm tra outliers.
7. Khi số liệu không ổn định, tăng repetitions hoặc điều tra CPU/GC/I/O.

### 9.1. Lệnh trên shell kiểu Bash

Một vòng chạy thử, mỗi mode là một process riêng:

```bash
for mode in -Xmixed -Xint; do
  echo "MODE: $mode"
  for run in 1 2 3 4 5; do
    java "$mode" ExecutionModeDemo
  done
done
```

Đây là **lệnh Bash/POSIX-like shell**, không áp dụng nguyên xi cho PowerShell.

### 9.2. Lệnh trên Windows PowerShell

```powershell
foreach ($mode in @("-Xmixed", "-Xint")) {
    Write-Output "MODE: $mode"
    foreach ($run in 1..5) {
        java $mode ExecutionModeDemo
    }
}
```

Lệnh trên chỉ thực hiện nhiều lần và hiển thị kết quả, **chưa tự tính median**. Có thể xuất `elapsedMs` sang file rồi phân tích bằng spreadsheet/script nếu cần.

### 9.3. Cần giữ cả stdout correctness

Đừng chỉ lưu dòng `elapsedMs` mà bỏ checksum. Nếu chương trình tính sai, bị OOME, thất bại do flag, hoặc input bị thay đổi, số thời gian ấy không còn so sánh cùng công việc.

### 9.4. Vì sao nên đảo thứ tự?

Nếu JVM/OS vừa khởi động hoặc CPU chuyển power state, các lần chạy về sau có thể nhanh/chậm khác. Khi luôn chạy một mode trước, **mode order** trở thành biến gây nhiễu. Xen kẽ `-Xint` và `-Xmixed` giúp giảm một số bias, dù không loại bỏ được mọi yếu tố ngoại lai.

### 9.5. JVM startup vs measured section

`elapsedMs` của chương trình **không bao gồm việc tạo process và phần startup trước `main`**; nó đo vùng code giữa hai `nanoTime`. Nếu mục tiêu là thời gian end-to-end của CLI, cần thêm một phép đo từ bên ngoài process:

```powershell
Measure-Command { java -Xint ExecutionModeDemo }
Measure-Command { java -Xmixed ExecutionModeDemo }
```

Output của `Measure-Command` khác dòng `elapsedMs`, vì có **JVM startup + class loading + program + shutdown + shell/process overhead**. Đây là hai metrics khác nhau, không nên cộng hoặc thay thế nhau.

## 10. Kiểm chứng đường JIT bằng `PrintCompilation`

Để biết `-Xmixed` có thực sự kích hoạt JIT hay không, chạy một lượt **diagnostic riêng**:

```bash
java -Xmixed -XX:+PrintCompilation ExecutionModeDemo
```

Trên HotSpot có hỗ trợ, option này in **các sự kiện compilation**, thường có:

- Timestamp tương đối từ lúc khởi động VM.
- Compile ID.
- Compilation level (nếu tiered).
- Marker `%` khi dùng OSR.
- Class/method, bytecode size.
- Những sự kiện đánh dấu compiled code không còn được dùng cho method entry.

Lệnh có thể trộn compilation logs với stdout của chương trình. **Không dùng trực tiếp thời gian của diagnostic run làm baseline sạch**, vì log I/O có thể ảnh hưởng scheduling/performance.

### 10.1. Một số dòng kiểu mẫu đã được quan sát

```text
27   13       3       ExecutionModeDemo::mix (21 bytes)
27   14       4       ExecutionModeDemo::mix (21 bytes)
28   15 %     3       ExecutionModeDemo::work @ 20 (62 bytes)
28   17 %     4       ExecutionModeDemo::work @ 20 (62 bytes)
```

Đây là **trích mẫu từ một lần chạy thử** trong môi trường kiểm tra. Số đầu dòng, ID, cấp compilation và thứ tự events **không là đầu ra chuẩn cố định**. Một máy khác hoặc lần chạy khác có thể không sinh đúng các dòng này.

### 10.2. Cách đọc level 3 và 4

Trong mô hình HotSpot tiered C1/C2 thông thường:

- **Level 0:** Interpreter.
- **Level 1:** C1 simple (no profiling).
- **Level 2:** C1 với limited profiling.
- **Level 3:** C1 với full profiling.
- **Level 4:** C2 tối ưu hóa mạnh.

Một method **có thể đi theo đường 0 → 3 → 4**, nhưng không bắt buộc phải qua mọi levels hay compile lên level 4.

### 10.3. `%` là gì?

Trong `PrintCompilation`, ký hiệu `%` thường cho biết **OSR (On-Stack Replacement)** compilation, tức JVM có thể chuyển execution state của một loop đang chạy sang compiled code tại một **bytecode index (BCI)** phù hợp.

Ví dụ `ExecutionModeDemo::work @ 20` cho thấy một vị trí BCI liên quan loop, **không phải invocation thứ 20**.

### 10.4. `(21 bytes)` có phải độ dài machine code không?

Không. Trong dòng `PrintCompilation`, kích thước hiển thị thông thường là **bytecode size của method**, không phải tổng machine instructions được sinh. C2-compiled code có thể dài hơn hoặc ngắn hơn tùy optimization/code shape.

### 10.5. Vì sao có dòng `made not entrant`?

Một nmethod cũ có thể bị đưa vào trạng thái không còn dùng cho những entry mới sau khi phiên bản compiled code tốt hơn xuất hiện hoặc assumption không còn hợp lệ. Đây **không tự là lỗi ứng dụng**.

## 11. Kiểm tra sự khác biệt giữa `-Xint` và `-Xmixed` trong logs

### 11.1. `-Xmixed`

```bash
java -Xmixed -XX:+PrintCompilation ExecutionModeDemo
```

Có thể thấy `ExecutionModeDemo::mix`, `ExecutionModeDemo::work` và những methods của JDK được JIT compile. Nhưng **không cam kết mọi method xuất hiện trong log**: policy, inlining, startup và compilation timing có thể thay đổi.

### 11.2. `-Xint`

```bash
java -Xint -XX:+PrintCompilation ExecutionModeDemo
```

Trên HotSpot interpreter-only, JVM không JIT compile Java bytecode của demo, nên bạn **không nên mong chờ các C1/C2 compilation events** cho những methods Java như trong `-Xmixed`. Diagnostics khác hoặc thông tin JVM có thể vẫn được in tùy options/runtime.

**Tránh suy luận quá mức từ “log rỗng”**: log chỉ cho thấy thông tin công cụ bật và VM ghi nhận, không phải một phép chứng minh tuyệt đối về tất cả code/native instructions đang thực thi.

### 11.3. Dòng in compiler không quyết định mức tăng tốc

Nhìn thấy `level 4` có thể cho thấy method đã được C2 compile trong lần chạy ấy, nhưng tốc độ thật còn chịu ảnh hưởng inlining, CPU, GC, code cache, data distributions và workload.

Không nên xếp hạng code chất lượng chỉ bằng “bao nhiêu methods level 4”.

## 12. Khác biệt quan trọng với `-Xcomp`

**`-Xcomp`** là chế độ dùng để **stress/test compiler** bằng cách ưu tiên compile methods thay vì chạy interpreted. Theo tài liệu OpenJDK/Oracle, đây là **testing mode**, không phải option được khuyến nghị dùng trong production để tăng tốc.

```bash
java -Xcomp ExecutionModeDemo
```

### 12.1. Vì sao `-Xcomp` có thể khởi động chậm hơn?

Một ứng dụng rất ngắn có nhiều methods chỉ chạy một lần. Biên dịch những methods này có thể tốn nhiều thời gian hơn lợi ích thu được từ machine code khi chúng gần như không được tái sử dụng.

Vì thế không nên coi “compile sớm mọi thứ” chắc chắn nhanh hơn interpreter + selective JIT.

### 12.2. `-Xcomp` không bảo đảm C2 level 4 cho mọi method

Flag này thay chính sách thực thi/biên dịch để stress JIT; nó **không cam kết** compiler level 4 trong mọi trường hợp. JVM có thể có những giới hạn/compiler policies khác, hoặc một số methods không phù hợp để compile theo cách mong đợi.

### 12.3. Không đưa `-Xcomp` vào baseline production

Trong một thí nghiệm mở rộng, bạn có thể chạy `-Xcomp` để quan sát chi phí compilation/startup, nhưng **trọng tâm so sánh hợp lý nhất vẫn là `-Xint` vs `-Xmixed`** khi phân tích lợi ích selective JIT của HotSpot.

## 13. `-XX:-TieredCompilation` không đồng nghĩa `-Xint`

Hai flags:

```bash
java -XX:-TieredCompilation ExecutionModeDemo
java -Xint ExecutionModeDemo
```

**không cùng nghĩa**.

- `-Xint`: tắt JIT compilation cho Java bytecode.
- `-XX:-TieredCompilation`: tắt **tiered compilation policy**, không nhất thiết tắt JIT; tùy HotSpot build, một optimizing compiler vẫn có thể compile methods.

Nếu hai lần chạy vô tình có elapsedMs gần bằng nhau, **không thể kết luận chúng dùng cùng engine**.

### 13.1. `-XX:TieredStopAtLevel=1`

```bash
java -XX:+TieredCompilation -XX:TieredStopAtLevel=1 ExecutionModeDemo
```

Với HotSpot tiered C1/C2 hỗ trợ flag, cấu hình này giới hạn mức compilation tối đa ở level tương ứng, hữu ích khảo sát C1. Nó **không phải** interpreter-only vì C1 là JIT compiler sinh machine code.

### 13.2. Tại sao không so sánh tất cả flags trong một bảng “hơn/thua” tuyệt đối?

Các flags khác nhau ở mục đích:

| Mode | Câu hỏi thí nghiệm | Giới hạn |
| --- | --- | --- |
| **`-Xint`** | Bytecode interpreted tốn bao lâu? | Không có JIT hot-path optimizations |
| **`-Xmixed`** | Interpreter + selective JIT hoạt động ra sao? | Có warm-up và compiler scheduling |
| **`-XX:-TieredCompilation`** | Không dùng tiered heuristics thì sao? | JIT vẫn có thể hoạt động |
| **`-XX:TieredStopAtLevel=1`** | C1-only-like path ra sao? | Không dùng C2 level 4; tùy build |
| **`-Xcomp`** | Compiler stress/testing có gì xảy ra? | Startup và compilation cost có thể lớn |

Nếu mục đích chỉ là so `-Xint` với default JVM, không cần tự động kết luận mode thứ ba nào đó “tốt hơn”.

## 14. Inlining: tại sao Java method calls không tương đương machine-code calls?

Trong demo:

```java
static int mix(int x) {
    x ^= x >>> 13;
    x *= 0x5bd1e995;
    x ^= x >>> 15;
    return x;
}
```

Một loop trong `work` gọi method `mix` mỗi iteration. JIT có thể **inline body của `mix` vào loop**, tức machine code không nhất thiết phải thực hiện một native method call riêng cho mỗi iteration.

### 14.1. Vì sao điều này quan trọng?

Với interpreter, JVM thực hiện theo bytecode execution model; HotSpot JIT có thể loại bỏ overhead gọi method và tối ưu xuyên qua method boundary. Đây là một lý do compute-intensive repeated workloads có thể hưởng lợi từ `-Xmixed`.

Nhưng cùng một code có thể cho kết quả khác nếu JIT quyết định không inline vì size, profile, code cache hoặc heuristics.

### 14.2. `PrintInlining`

Trên một số HotSpot builds:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining ExecutionModeDemo
```

Output có thể in những quyết định inlining và lý do. Đây là **run diagnostic**, không nên trộn vào phép đo baseline cần độ sạch.

### 14.3. Đừng kết luận mỗi lần gọi `mix` mất đúng một fixed nanosecond

Thời gian có thể phụ thuộc instruction scheduling, CPU cache, inlining, arithmetic throughput, branch predictors và đo lường. Method-call overhead không bằng một con số cố định cho mọi JVM.

## 15. OSR: vòng lặp có thể nóng dù method không được gọi nhiều

**On-Stack Replacement (OSR)** cho phép HotSpot chuyển một loop đang chạy interpreted/C1 sang một entry native compiled tương ứng trong quá trình method chưa return.

Trong demo, phương thức `work` có vòng lặp trong. Một lần gọi `work(800, 1024)` thực hiện nhiều iterations mà invocation count của chính `work` có thể ít. **Backedge counts** của loop có thể kích hoạt OSR.

```text
work() method invocation
      |
      v
Interpreter / tier thấp
      |
      v
loop backedge activity tăng
      |
      +---- compiler policy requests OSR at BCI
                           |
                           v
                      OSR nmethod
                           |
                   execution chuyển state
                           |
                           v
                    tiếp tục loop đúng
```

### 15.1. `%` trong `PrintCompilation`

Một bản ghi:

```text
28   15 %     3       ExecutionModeDemo::work @ 20 (62 bytes)
```

là ví dụ một **OSR compilation event**, không phải “method `work` được gọi 15 lần” hay “method nóng sau 28 ms đúng trên mọi máy”.

### 15.2. OSR không buộc xuất hiện

Khi vòng lặp quá ngắn, interpreter-only được bật, hoặc compiler chọn đường khác, OSR có thể không xảy ra. Không viết unit test assert “phải thấy ký hiệu `%` đúng một lần”.

### 15.3. OSR khác compiled method entry

Một OSR nmethod cho bytecode index trong loop **khác** một nmethod cho normal method entry. Có thể quan sát cả hai cho cùng Java method.

## 16. Những sai lệch benchmark cần tránh

### 16.1. Constant folding

Nếu benchmark luôn gọi một phép tính với cùng constant và kết quả không được dùng, JIT có thể **fold** toàn bộ expression thành hằng số hoặc loại đi khi hợp lệ. Lúc đó `-Xmixed` tưởng như nhanh vô lý, nhưng thí nghiệm không còn đo công việc mong muốn.

Demo sử dụng input thay đổi qua `c`, `i` và phép mixing phụ thuộc state trước đó để giảm bẫy này, nhưng vẫn không tạo guarantee “không có tối ưu nào”.

### 16.2. Dead-code elimination

Nếu benchmark tính giá trị không được quan sát:

```java
for (int i = 0; i < 1_000_000; i++) {
    expensiveButPureCalculation(i);
}
```

đây là **snippet minh họa**, method `expensiveButPureCalculation` chưa định nghĩa. Compiler có thể loại bỏ computation nếu kết quả không có tác dụng quan sát được. Dùng result consumption/blackhole đúng, tốt nhất bằng JMH.

### 16.3. Class loading và JIT warm-up

Nếu chỉ đo lần đầu gọi `work`, bạn có thể đo cả interpreter execution và compilation scheduling. Một phép đo steady-state cần warm-up đủ và phương pháp đo thích hợp, không chỉ đặt timer quanh first call.

### 16.4. GC noise

Nếu workload cấp phát nhiều objects, GC pauses có thể xen vào khoảng đo; ta có thể vô tình so sánh GC behavior thay vì engine execution. Demo chủ yếu dùng arithmetic và không tạo mảng lớn trong inner loop, nhằm **giảm chứ không loại trừ** ảnh hưởng GC.

### 16.5. Console I/O

In từng iteration làm I/O áp đảo phép tính, khiến dữ liệu không đại diện hot numeric loop. Demo chỉ in sau timed section.

### 16.6. Clock granularity và measurement overhead

Nếu measured section quá ngắn (gần độ phân giải clock), noise từ `System.nanoTime`/scheduling có thể tương đối lớn. Tăng workload/repetitions đúng mức, hoặc dùng JMH có thiết kế timer/sample tốt hơn.

### 16.7. Biên dịch và chạy đồng thời với tiến trình nặng

IDE indexing, antivirus, JVM khác hoặc CPU thermal throttling có thể ảnh hưởng kết quả. Một giá trị elapsedMs bất thường không nhất thiết do Java optimizer.

### 16.8. Benchmark dựa vào nội dung của GC log

Đừng dùng `-XX:+PrintCompilation` khi đo baseline rồi so với `-Xint` không logging: đó là thay đổi **hai yếu tố cùng lúc** — engine và log I/O.

## 17. Mở rộng: đo cold startup riêng với steady-state

Hai bài toán cần phân biệt:

1. **Cold CLI:** người dùng chạy một lệnh, JVM startup, làm một việc ngắn rồi thoát. `-Xmixed` có thể chưa hưởng lợi nhiều từ JIT vì workload kết thúc trước khi code đủ hot.
2. **Long-lived service:** ứng dụng chạy hàng giờ, xử lý nhiều requests tương tự, profile đủ để JIT compile hot paths. `-Xmixed` có thể có lợi ích lớn hơn so với interpreter-only.

### 17.1. “Thực thi kết hợp” giúp startup vì sao?

Với cold methods, JVM tránh biên dịch chúng trước khi biết chúng đáng tối ưu. C1 có thể compile nhanh hơn C2 trên những hot paths ban đầu, còn C2 được sử dụng sau khi có profile đủ chất lượng.

Do vậy HotSpot tiered compilation cố gắng cân bằng **startup time và peak performance**, khác với quan niệm “compile toàn bộ trước khi chạy”.

### 17.2. Không chuyển trực tiếp kết quả CLI demo sang web service

Một request-based service có:

- JIT profile thay đổi theo traffic.
- Nhiều classes/frameworks/threads.
- GC và allocation pattern.
- Database/network I/O.
- Thread scheduling và container CPU quota.

Nếu chỉ dùng compute demo để kết luận “service sẽ nhanh gấp N lần”, bạn đã bỏ qua những thành phần chi phối end-to-end latency.

## 18. Đọc kết quả như một nhà thực nghiệm

Một báo cáo kết quả đáng tin cần ghi:

| Nội dung | Ví dụ cách ghi |
| --- | --- |
| Hypothesis | “Với numeric loop nóng, mixed JIT có thể giảm measured elapsed time so với interpreter-only” |
| Java/JVM | `java -version` của máy chạy |
| Input | warm-up 800 calls, measured 1500 calls, 1024 steps/call |
| Process isolation | Mỗi run khởi động process Java mới |
| Modes | `-Xint` và `-Xmixed` |
| Correctness | Checksum bằng nhau: `3215795315003` |
| Data collection | Từng elapsedMs, ít nhất vài runs/mode |
| Summary | Median, spread, số runs, outliers |
| Diagnostics | `PrintCompilation` chạy riêng, không trộn baseline |
| Conclusion | Phạm vi kết luận giới hạn trong workload/JVM/host đã đo |

### 18.1. Phải kiểm tra correctness trước khi so tốc độ

Nếu một mode ra checksum khác, ưu tiên điều tra code/VM flags, version và state khác biệt. Đừng coi một lần nhanh hơn mà sai checksum là “optimization thành công”.

### 18.2. Cần báo hiệu ứng bằng tỷ lệ có đơn vị đúng

Nếu cả hai timed sections đo cùng số operations, tỷ lệ *elapsedMs_Xint / elapsedMs_Xmixed* có thể dùng như **speedup quan sát trong mẫu**. Nhưng hãy báo kèm điều kiện thí nghiệm và interval/spread; đừng biến tỷ lệ một lần thành general performance claim.

### 18.3. Cân nhắc CPU quota và thermal effects

Nếu container chỉ có 1 vCPU, compiler threads của `-Xmixed` cạnh tranh CPU với application. Nếu CPU quota khác giữa các runs, thí nghiệm không còn giữ các điều kiện tương đương.

### 18.4. Nếu nhu cầu là so độ nhanh thật, dùng JMH

**OpenJDK JMH — Java Microbenchmark Harness** là công cụ thích hợp cho microbenchmark với warm-up, measurement iterations, forks và blackholes. Demo trong bài giúp quan sát khái niệm và kiểm tra cả correctness, không thay một benchmark tối ưu cho quyết định production.

Tham khảo: https://openjdk.org/projects/code-tools/jmh/

## 19. JMH mở rộng: nên kiểm tra những gì?

Để dùng JMH cho so sánh execution modes, nên:

1. Viết benchmark method trả về một kết quả được JMH tiêu thụ (hoặc dùng `Blackhole`) nhằm tránh DCE.
2. Có `@Warmup` với đủ thời gian/iterations cho môi trường.
3. Có `@Measurement` với đủ runs.
4. Chạy **forks riêng** với `-Xint` và `-Xmixed`.
5. Dùng cùng inputs, JDK build, heap và CPU quota.
6. Ghi rõ **score units** như operations/second hoặc ns/op.
7. Đọc variance và error bars.
8. Kiểm tra JIT compilation profile nếu muốn giải thích nguyên nhân tốc độ.
9. Đừng quên startup và compiler CPU khi đánh giá ứng dụng thực tế.

**Không nhầm `ns/op` của một method riêng với p99 request latency** của toàn hệ thống.

### 19.1. Cẩn trọng với `-Xint` khi warm-up quá dài

Trong interpreter-only, một phép tính có thể chậm hơn đáng kể. JMH với số iterations/measurement time được thiết kế cho JIT mode có thể mất nhiều thời gian nếu dùng interpreter-only. Điều chỉnh benchmark plan để vừa đủ dữ liệu, nhưng **giữ comparable operations và reporting rõ ràng**.

### 19.2. Tại sao chạy hai modes không chỉ là thay một annotation?

Execution mode là **VM option ở thời điểm khởi động process**. Với JMH, nên truyền JVM args phù hợp cho các forks; không cố thay `-Xint` và `-Xmixed` giữa chừng trong cùng một JVM đang chạy.

## 20. Những nhầm lẫn thường gặp

| Nhận định sai | Diễn giải đúng |
| --- | --- |
| “`-Xint` tắt mọi native instruction của JVM.” | Nó tắt JIT cho Java bytecode; runtime/native services vẫn tồn tại. |
| “`-Xmixed` biên dịch mọi method trước main.” | It chọn selective JIT cho hot paths; cold code có thể interpreted. |
| “Chạy cùng checksum nghĩa thời gian phải giống nhau.” | Correctness giống, performance có thể khác. |
| “`-Xint` làm tắt GC.” | GC và heap management vẫn hoạt động. |
| “`-Xmixed` có nghĩa mỗi method luôn vừa interpreted vừa compiled.” | Một method có thể chỉ interpreted, hoặc được compile theo tier/policy. |
| “`-Xcomp` là chế độ production tối ưu nhất.” | Testing mode; có thể tăng startup/compilation cost lớn. |
| “`-XX:-TieredCompilation` giống `-Xint`.” | Tiered off không đồng nghĩa JIT off. |
| “`TieredStopAtLevel=1` là interpreter-only.” | Level 1 dùng C1 compiled code trên HotSpot phù hợp. |
| “PrintCompilation luôn xuất hiện đúng bốn dòng cho demo.” | Compile events phụ thuộc runtime/timing; không deterministic. |
| “Level 4 là thời gian chính xác bốn nanoseconds.” | Đó là compilation tier, không một đơn vị thời gian. |
| “`%` nghĩa CPU utilization percent.” | Trong compilation logs thường là OSR marker. |
| “`@ 20` nghĩa 20 lời gọi method.” | Là bytecode index (BCI) trong OSR entry. |
| “Warm-up 800 calls bảo đảm steady state.” | Không có guarantee đó. |
| “Một lần đo cho speedup phổ quát.” | Chỉ là một mẫu có nhiều biến gây nhiễu. |
| “Thêm `PrintInlining` không ảnh hưởng đo.” | Diagnostic output có overhead/log I/O. |
| “`System.nanoTime` trả thời gian CPU thuần.” | Nó đo elapsed interval, gồm scheduling delays. |
| “Giữ sink volatile thì mọi compiler optimization biến mất.” | Volatile giữ side effects, JIT vẫn tối ưu phần có thể. |
| “Tất cả JIT speedups đến từ method call overhead.” | Inlining, loop optimizations, branch/type profile và nhiều yếu tố khác. |
| “Method được gọi một lần không thể nóng.” | Một loop dài có thể kích hoạt OSR bằng backedge counts. |
| “CLI benchmark đủ để kết luận HTTP server p99.” | Startup, I/O, workload và concurrency rất khác. |

## 21. Bài tập tự kiểm tra có đáp án

**Câu 1.** `-Xint` và `-Xmixed` khác nhau ở điểm chính nào?

**Đáp án:** `-Xint` thực thi Java bytecode interpreted-only, tắt JIT; `-Xmixed` cho phép interpreter và JIT compiled hot methods phối hợp.

**Câu 2.** `-Xint` có tắt GC, native libraries và OS system calls không?

**Đáp án:** Không. Nó tác động execution mode của Java bytecode, không loại bỏ runtime services.

**Câu 3.** Vì sao cùng bytecode chạy ở hai modes phải cho cùng checksum?

**Đáp án:** Java semantics không được đổi bởi việc VM chọn interpreter hay compiler; modes chỉ là cách thực hiện đúng các instructions.

**Câu 4.** Dòng `checksum=3215795315003` có là thời gian đo không?

**Đáp án:** Không. Nó là giá trị logic của workload, dùng kiểm tra correctness.

**Câu 5.** `elapsedMs` có được kỳ vọng giống nhau giữa mọi runs không?

**Đáp án:** Không. Timing phụ thuộc CPU, JIT, GC, scheduling, warm-up và môi trường.

**Câu 6.** Vì sao cần khởi động process riêng cho từng mode?

**Đáp án:** Vì compiled/profile/cache state của một JVM đang chạy không thể reset đơn giản giữa hai chế độ; mode là VM startup option.

**Câu 7.** Warm-up có ích gì cho `-Xmixed`?

**Đáp án:** Tạo opportunity cho class loading/profiling và JIT compile hot code trước measured section; không bảo đảm mọi method đã C2.

**Câu 8.** Warm-up `-Xint` có làm bytecode được C2 compile không?

**Đáp án:** Không, vì mode này tắt JIT compilation cho bytecode.

**Câu 9.** Làm sao quan sát JIT events trên HotSpot?

**Đáp án:** Chạy riêng với `-XX:+PrintCompilation`; tìm class/method của demo và các compilation levels/OSR markers.

**Câu 10.** Nếu `PrintCompilation` in level 3 và 4, điều đó thường biểu thị gì?

**Đáp án:** C1 full-profiling compilation và C2 optimized compilation trong mô hình tiered HotSpot tương ứng.

**Câu 11.** `%` trong log thường có nghĩa gì?

**Đáp án:** OSR compilation cho một điểm bytecode trong loop.

**Câu 12.** Vì sao không đo elapsedMs khi bật hàng nghìn dòng `PrintCompilation`?

**Đáp án:** Logging/I/O làm thay đổi điều kiện thực thi và có thể gây nhiễu latency.

**Câu 13.** `-Xcomp` có nghĩa “mọi method đều level 4” không?

**Đáp án:** Không. Nó ưu tiên compilation như một testing mode nhưng không cam kết cấp tối ưu cụ thể.

**Câu 14.** `-XX:-TieredCompilation` có tắt JIT hoàn toàn không?

**Đáp án:** Không nhất thiết. Nó tắt tiered policy, khác interpreter-only.

**Câu 15.** Một workload có nhiều I/O có chắc `-Xmixed` nhanh hơn `-Xint` nhiều lần không?

**Đáp án:** Không. Nếu thời gian chủ yếu chờ I/O, lợi ích tối ưu bytecode có thể nhỏ so với end-to-end latency.

**Câu 16.** Vì sao code trong `mix` có thể không còn là một native call riêng mỗi iteration?

**Đáp án:** JIT có thể inline `mix` vào vòng lặp của caller.

**Câu 17.** Vì sao dùng `System.nanoTime` thay `System.currentTimeMillis` cho khoảng đo ngắn?

**Đáp án:** `nanoTime` được thiết kế cho đo elapsed durations; `currentTimeMillis` là wall-clock có độ phân giải/điều chỉnh không phù hợp bằng cho microtiming.

**Câu 18.** Tại sao nên báo median/spread thay vì lấy lần nhanh nhất?

**Đáp án:** Giá trị min có thể thiên lệch do may mắn scheduling/cache; distribution giúp hiểu biến động và outliers.

**Câu 19.** Nếu checksum khác giữa modes, có thể kết luận JIT nhanh hơn không?

**Đáp án:** Không. Phải xác minh chương trình/môi trường/correctness trước khi diễn giải performance.

**Câu 20.** Công cụ nào thích hợp hơn demo `nanoTime` tự viết nếu muốn microbenchmark nghiêm túc?

**Đáp án:** OpenJDK JMH, với forks, warm-up, measurement và blackhole.

## 22. Checklist thí nghiệm thực hành

1. Cố định cùng tệp `ExecutionModeDemo.java` và compile một lần bằng `javac --release 17`.
2. Ghi `java -version` và `javac -version`.
3. Dùng `-Xint` và `-Xmixed` ở **hai processes** độc lập.
4. Giữ warm-up calls, measured calls và steps per call bằng nhau.
5. Kiểm tra năm dòng stdout xác định và checksum trước khi đo timing.
6. Ghi `elapsedMs` của từng lần chạy, không lấy một lần làm luật.
7. Lặp nhiều runs và xen kẽ execution mode order.
8. Đo whole-process startup riêng khi đó là mục tiêu.
9. Ghi CPU/container quotas nếu có.
10. Chạy `PrintCompilation` riêng để quan sát interpreter vs C1/C2/OSR.
11. Đừng áp số compile events hay C2 thresholds cố định cho mọi HotSpot.
12. Không gộp `-Xint`, `-Xcomp`, tiered-off vào một khái niệm.
13. Không bật thêm heap/GC/JIT tuning flags khác khi chưa có giả thuyết rõ.
14. Tránh output I/O trong timed computation.
15. Dùng JMH nếu kết quả cần phục vụ quyết định performance production.
16. Đọc profiler/JFR/JIT logs khi muốn giải thích nguyên nhân khác biệt.
17. Giữ code đúng Java semantics; không viết code dựa vào timing race/undefined assumptions.
18. Công bố giới hạn thí nghiệm: CPU, JVM, build, số runs, warm-up, inputs.

### Tổng kết

```text
                  SAME JAVA BYTECODE
                          |
                +---------+----------+
                |                    |
              -Xint               -Xmixed
                |                    |
        Interpreter only    Interpreter + selective JIT
                |                 /        \
         No C1/C2 for       Cold paths    Hot paths
         Java bytecode     interpreted   C1/C2 / OSR
                |                    |
                +----------+---------+
                           |
                 SAME LOGICAL RESULTS
                           |
                Different elapsed time,
                compiler CPU, warm-up
                           |
            Measure multiple isolated runs
            Check correctness before timing
```

**Kết luận cốt lõi:** `-Xint` là đối chứng hữu ích để hiểu chi phí interpreted execution, còn `-Xmixed` cho HotSpot **thông dịch cold code và JIT-biên dịch code nóng khi có lợi**. Một thí nghiệm đúng phải giữ cùng bytecode/input/JVM conditions, phân biệt **stdout logic cố định** với **timing thay đổi**, đo nhiều lần và dùng compiler logs để xác nhận các sự kiện JIT. Tốc độ đo được trên một máy không trở thành lời cam kết của Java.

### Tài liệu tham khảo

- [Oracle JDK 17 — `java` command, các mode `-Xint`, `-Xmixed`, `-Xcomp`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): định nghĩa interpreter-only, mixed (mặc định) và testing compilation mode.
- [OpenJDK — `java` command source documentation](https://github.com/openjdk/jdk/blob/master/src/java.base/share/man/java.md): các option HotSpot với mô tả giải nghĩa.
- [Microsoft OpenJDK Engineering — How Tiered Compilation Works in OpenJDK](https://devblogs.microsoft.com/java/how-tiered-compilation-works-in-openjdk/): C1/C2 profiling, các compilation levels và những cảnh báo về `-Xint`/`-Xcomp`.
- [Oracle — HotSpot Compilation Optimization](https://docs.oracle.com/javacomponents/jrockit-hotspot/migration-guide/comp-opt.htm): interpreter/JIT và tiered compilation dưới góc nhìn runtime.
- [OpenJDK — Compiler configuration/source](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/compiler/compiler_globals.hpp): những cờ liên quan compiler và tiered policy, phụ thuộc version.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): benchmark có warm-up, forks, instrumentation và thống kê.
- [JVMS `2` — The Structure of the Java Virtual Machine (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html): Java execution semantics không yêu cầu tất cả JVM dùng HotSpot C1/C2.


</details>

- [Quay lại đầu trang](#back-to-top)
