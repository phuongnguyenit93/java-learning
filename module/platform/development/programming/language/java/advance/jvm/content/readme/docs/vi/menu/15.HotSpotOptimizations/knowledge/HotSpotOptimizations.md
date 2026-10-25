<a id="back-to-top"></a>

# Tối ưu HotSpot

## Menu
- [Chèn thân phương thức (inlining) và mở rộng phạm vi phân tích](#hot-spot-optimizations-001)
- [Khử lời gọi ảo dựa trên thông tin kiểu và giới hạn của giả định](#hot-spot-optimizations-002)
- [Gấp hằng, loại mã không cần thiết và tác động đến phép đo](#hot-spot-optimizations-003)
- [Tối ưu vòng lặp và loại kiểm tra dư thừa trong điều kiện phù hợp](#hot-spot-optimizations-004)
- [Phân tích đối tượng thoát (escape analysis) và thay thế bằng giá trị thành phần](#hot-spot-optimizations-005)
- [Phân biệt loại bỏ cấp phát với giả định đối tượng luôn được chuyển lên stack](#hot-spot-optimizations-006)
- [Loại bỏ khóa không cần thiết khi đối tượng không được chia sẻ](#hot-spot-optimizations-007)
- [Intrinsic và phụ thuộc vào kiến trúc CPU, JDK, cách triển khai](#hot-spot-optimizations-008)
- [Vì sao thay đổi mã nguồn nhỏ có thể làm kết quả hiệu năng thay đổi lớn](#hot-spot-optimizations-009)

## <a id="hot-spot-optimizations-001">Chèn thân phương thức (inlining) và mở rộng phạm vi phân tích</a>

<details>
<summary>Xem chi tiết</summary>

# Chèn thân phương thức (inlining) và mở rộng phạm vi phân tích trong HotSpot

## 1. Inlining giải quyết vấn đề gì?

Java khuyến khích tách chương trình thành nhiều methods nhỏ để code rõ ràng, tái sử dụng được và dễ kiểm thử:

~~~java
static int twice(int value) {
    return value * 2;
}

static int plusOne(int value) {
    return value + 1;
}

static int pipeline(int value) {
    return plusOne(twice(value));
}
~~~

Ba methods trên là **đoạn minh họa cần nằm trong một class**. Ở mức source, `pipeline` gọi hai methods. Ở mức bytecode, `javac` thường vẫn sinh các lệnh `invokestatic`. Nhưng khi HotSpot JIT compile một caller hot, nó có thể **chèn logic của callee vào intermediate representation (IR) của caller** ngay tại call site: đó là **method inlining**.

Một cách hình dung:

~~~text
Trước inlining (mô hình):
  pipeline(x)
      |
      +--> twice(x)     : 2*x
      |
      +--> plusOne(...) : +1

Sau khi JIT inline vào IR của pipeline (mô hình):
  result = 2*x + 1
~~~

**Inlining không chỉnh sửa source Java và cũng không nhất thiết chỉnh sửa file `.class` trên đĩa.** Nó là quyết định của compiler tại runtime khi tạo **native compiled code**. Bytecode gốc vẫn có thể chứa lời gọi methods.

**Hai lợi ích chính:**

1. **Giảm overhead của lời gọi:** bớt các bước call/return, setup arguments và đường chuyển giao control khi compiler thực sự loại lời gọi.
2. **Mở rộng phạm vi phân tích:** compiler nhìn thấy code ở **hai phía của method boundary** trong cùng IR, nên có thể thực hiện constant propagation, dead-code elimination, value/range analysis, escape analysis, scalar replacement, devirtualization và các tối ưu khác vượt qua ranh giới methods.

**Lợi ích thứ hai thường quan trọng hơn lợi ích “tiết kiệm một lệnh call”.** Trong nhiều chương trình, một method nhỏ chỉ có vài bytecode; việc chèn nó vào caller có thể mở ra phép tối ưu mạnh hơn nhiều so với chi phí invocation ban đầu.

### Một cảnh báo từ đầu

HotSpot **không bảo đảm inline mọi method**, kể cả `private`, `static`, `final` hoặc method chỉ có một dòng. Quyết định phụ thuộc compiler tier, bytecode size, call-site frequency, recursion, call depth, profile types, compilation graph budget và constraints khác.

## 2. Các tầng cần phân biệt: source, bytecode, IR, machine code

| Tầng | Điều thể hiện | Có bị thay đổi khi JIT inline? |
| --- | --- | --- |
| **Java source** | `pipeline` gọi `twice` và `plusOne` | Không; source giữ nguyên |
| **Class-file bytecode** | `invokestatic`, `invokevirtual`, `invokeinterface`… | Không nhất thiết; `javap -c` vẫn thấy calls |
| **Compiler IR / call graph** | Nodes cho calls, operations, types, control flow | **Có thể**: callee body được mở vào caller graph |
| **Compiled native code** | Instructions CPU đã tối ưu | Có thể không còn lời gọi riêng tại site ấy |
| **Runtime semantics** | Kết quả, exceptions, synchronization, type safety | **Phải được bảo toàn** |

Không lấy kết quả `javap -c` để kết luận “JIT chưa inline”: `javap` đọc class file, không disassemble native nmethods của JVM hiện đang chạy. Muốn biết quyết định HotSpot cần xem **`-XX:+PrintInlining`** hoặc các công cụ compiler/JIT phù hợp.

### “Chèn thân phương thức” không phải sao chép text Java

JIT không đơn giản copy đoạn source giữa hai dấu ngoặc. Nó phân tích **bytecode/IR và context tại call site**, tạo IR tương đương, ánh xạ arguments/receiver và locals, nối control-flow edges, return values, exceptions và những hiệu ứng liên quan.

Ví dụ, inlining hai methods có thể mang ý nghĩa:

~~~text
IR caller:
    parameter x
    call twice(x)
    call plusOne(result)

Inline twice:
    v1 = x * 2

Inline plusOne:
    v2 = v1 + 1

Further optimization:
    v2 = x * 2 + 1
~~~

Đây là **sơ đồ IR giả định**, không phải dump chính xác từ C2 hoặc tên nodes trong OpenJDK. Compiler thật còn quản lý safepoints, GC maps, debug state, memory effects, alias information và speculative guards.

## 3. Call site quan trọng hơn tên method

Inlining thường là **quyết định tại một call site cụ thể trong một compilation**.

Ví dụ:

~~~java
static int helper(int value) {
    return value * 5;
}

static int hotCaller(int x) {
    return helper(x) + 1;
}

static int coldCaller(int x) {
    return helper(x) - 1;
}
~~~

Đây là các methods minh họa. JIT có thể inline `helper` ở **`hotCaller`** vì profile cho thấy call site rất hot, nhưng không inline nó trong **`coldCaller`** vì method lạnh, budget khác hoặc chưa được compile. Cũng có thể chọn compile cả hai hoặc không compile một method nào.

**Không nói “method `helper` đã được đánh dấu inline trên toàn JVM”.** Cùng một callee có thể được inline vào nhiều callers, một số call sites vẫn giữ call, và những lần recompile sau có quyết định khác. Các log `PrintInlining` gắn với compilation của caller và chuỗi inlining tree cụ thể.

### Inlining và dynamic dispatch

Một lời gọi `invokestatic` có target rõ ràng theo bytecode/linking; `invokevirtual` và `invokeinterface` thường cần runtime method selection theo receiver type. Nhưng compiler vẫn có thể inline virtual calls khi có đủ chứng cứ đúng đắn, ví dụ:

- Declared target là effectively single implementation phù hợp.
- Type profile của call site chủ yếu thấy một concrete class.
- Class hierarchy analysis (CHA) cho biết tại thời điểm compilation một hướng call khả dụng.
- Compiler thêm **type guard** hoặc dependency để xử lý khi assumption không còn đúng.

**Java interface không tự ngăn inlining.** Và **`final` không bắt buộc đảm bảo inline**: biết target chỉ loại một trở ngại, còn budgets/size/profitability vẫn phải xét.

## 4. Mở rộng phạm vi phân tích: điều cốt lõi

Một optimizer chỉ thấy caller có thể phải bảo thủ về hành vi của callee: method có thể đọc/ghi field, throw exception, escape reference, gọi method khác hoặc có side effects.

Nếu inlining đưa body callee vào cùng IR, compiler có thể phân tích các thao tác **nối tiếp qua nhiều methods**, làm lộ ra các quan hệ mà trước đó bị che bởi call boundary.

### 4.1. Constant propagation qua method

~~~java
static int square(int x) {
    return x * x;
}

static int compute() {
    return square(7) + 3;
}
~~~

Đoạn methods minh họa.

~~~text
Trước mở rộng:
  call square(7) -> kết quả chưa rõ trong caller IR

Sau inline (mô hình):
  (7 * 7) + 3

Constant folding có thể tạo:
  52
~~~

**`compute()` trả 52** theo Java semantics. Inlining giúp compiler thấy argument là constant ngay trong body của `square`. Compiler đôi khi vẫn có thể suy luận kết quả bằng kỹ thuật khác mà không inline, nên không được diễn đạt “chỉ inlining mới có thể constant-fold”.

### 4.2. Dead-code elimination và branch simplification

~~~java
static int choose(boolean enabled, int value) {
    return enabled ? value * 2 : value * 3;
}

static int fastPath(int input) {
    return choose(true, input);
}
~~~

Nếu inline `choose(true, input)`, điều kiện `enabled` đã biết, compiler có thể chỉ giữ nhánh `input * 2`, thay vì giữ cả hai nhánh. Điều này giảm code/runtime work và mở ra tối ưu cho những operations kế tiếp.

**Không suy** rằng một branch có side effects có thể bị loại nếu điều kiện vẫn có thể chọn nhánh đó ở runtime. Optimizer phải chứng minh đường bị loại không thể chạy trong context compiled.

### 4.3. Range check elimination

~~~java
static int readFirst(int[] numbers) {
    return numbers[0];
}

static int readKnownLength() {
    int[] data = new int[] {4, 8, 12};
    return readFirst(data);
}
~~~

Trong context này, sau khi inlining `readFirst`, compiler có thể thấy array length và index 0 đã biết, cho phép loại **bounds check không cần thiết** nếu chứng minh an toàn. Null checks cũng chỉ bị bỏ khi có chứng cứ object non-null hoặc được bảo vệ bằng guard hợp lệ.

**Không bảo đảm** JIT luôn loại cả allocation lẫn mọi checks trong ví dụ; compiler tier, profile, deoptimization requirements và điều kiện implementation quyết định.

### 4.4. Loop optimization

Giả sử một loop gọi helper nhỏ ở mỗi vòng. Inline helper có thể cho optimizer nhìn thấy arithmetic/field reads trong cùng loop body để thực hiện:

- hoisting một phép tính bất biến ra ngoài loop;
- common-subexpression elimination;
- strength reduction;
- loop unrolling hoặc vectorization **nếu hợp lệ và được hỗ trợ**.

Inlining **không phải điều kiện đủ** để loop tự động được vectorize. Memory aliasing, data dependencies, exceptions và architecture constraints vẫn có thể ngăn tối ưu.

## 5. Escape analysis và scalar replacement qua call boundary

Đây là ví dụ nổi bật về lý do inlining quan trọng.

~~~java
static final class Pair {
    final int left;
    final int right;

    Pair(int left, int right) {
        this.left = left;
        this.right = right;
    }
}

static int score(Pair pair) {
    return pair.left * 2 + pair.right;
}

static int makeScore(int base) {
    Pair pair = new Pair(base, base + 1);
    return score(pair);
}
~~~

Đoạn này minh họa các declarations trong cùng class.

Nếu compiler nhìn `makeScore` mà không hiểu effects của `score`, nó có thể phải giả định `pair` được dùng theo một cách đòi object tồn tại. Sau inlining `score` (và những phần liên quan), compiler thấy `Pair` chỉ được đọc fields trong scope đã biết; **escape analysis** có thể xác định không cần materialize object trên heap.

~~~text
Before inline (mô hình):
  p = new Pair(base, base+1)
  result = call score(p)
  return result

After inline:
  p = new Pair(base, base+1)
  result = p.left*2 + p.right
  return result

After scalar replacement nếu đủ điều kiện:
  left = base
  right = base+1
  return left*2 + right

Further simplify:
  return base*3 + 1
~~~

Trên HotSpot C2, **scalar replacement** có thể loại heap allocation và những loads/stores đối với fields; không phải “chuyển mọi object không escape vào JVM stack”. Tài liệu Oracle HotSpot Performance Enhancements giải thích rõ hướng tiếp cận loại allocation và associated locks trong những điều kiện phù hợp.

**Không cam kết** chương trình nhỏ nêu trên chắc chắn được JIT compile hoặc scalar-replace ở lần chạy đầu. Với bytecode `new Pair` vẫn hiện diện trong `javap`, JIT có thể loại allocation **ở runtime compiled machine code**; ngược lại nếu object escape thật, VM phải giữ identity/visibility cần thiết.

### Lợi ích lan truyền qua nhiều methods

~~~text
Caller -> createPair() -> scorePair() -> normalize()

Nếu các lời gọi nhỏ cùng được inline:
  Compiler có thể nhìn graph tạo object -> đọc fields -> tính toán cuối.

Kết quả tiềm năng:
  constant propagation
  scalar replacement
  dead-store elimination
  elimination of redundant null/bounds checks
  register-based execution
~~~

Vì thế inlining **mở rộng biên giới phân tích liên thủ tục (interprocedural analysis)** trong một compilation. Cũng chính vì mở rộng IR mà nó có **chi phí**, buộc compiler dùng heuristics thay vì inline vô hạn.

## 6. Cơ chế speculative inlining với virtual/interface calls

Xem một call site dùng interface:

~~~java
interface Operation {
    int apply(int x);
}

static final class AddOne implements Operation {
    @Override public int apply(int x) { return x + 1; }
}

static final class MultiplyTwo implements Operation {
    @Override public int apply(int x) { return x * 2; }
}

static int execute(Operation operation, int value) {
    return operation.apply(value);
}
~~~

Các declarations trên là **minh họa**, không phải file Java độc lập.

Tại runtime, call site của `operation.apply` có thể:

- **Monomorphic**: gần như chỉ thấy một receiver type, chẳng hạn `AddOne`.
- **Bimorphic/polymorphic**: thấy hơn một receiver type với tần suất khác nhau.
- **Megamorphic**: thấy rất nhiều receiver types khiến inline một target cụ thể có thể không đem lại lợi ích.

HotSpot JIT có thể dùng **profile-based type guards** để inline target phổ biến:

~~~text
if (receiver matches AddOne):        // kiểm tra thực tế tùy compiler
    result = value + 1                // AddOne.apply inlined
else:
    result = genericVirtualDispatch(receiver, value)
~~~

Đây là sơ đồ **guarded inlining** khái niệm. VM có thể dùng deoptimization thay cho generic fallback hoặc nhiều guarded targets khác nhau, miễn giữ đúng Java dynamic-dispatch semantics.

### Nếu sau đó xuất hiện class mới?

Giả sử chương trình trước đây chỉ chạy với `AddOne`, sau đó class `MultiplyTwo` được nạp và một object mới được truyền vào call site. JVM **không được phép** trả `x+1` cho receiver `MultiplyTwo` chỉ vì đã inline `AddOne`. Nó phải:

- cho guard thất bại và dùng đường gọi đúng;
- hoặc **deoptimize** compiled code khi assumptions không còn đúng;
- hoặc áp dụng cơ chế invalidation/recompilation/virtual dispatch tương đương.

**Deoptimization không phải lỗi Java nghiệp vụ**. Nó là phần của adaptive optimization giúp compiled code không sai ngay cả khi profile trước đó trở nên không còn đại diện.

### Class Hierarchy Analysis (CHA)

CHA cho phép optimizer dùng thông tin class hierarchy đang được load/known để nhận ra một call site có target duy nhất. Nhưng với class loading động, giả định có thể mất hiệu lực khi subclass/implementation mới xuất hiện; HotSpot có dependency tracking và deoptimization phù hợp.

Không được kết luận `invokevirtual` luôn quá đắt để inline hoặc mọi lời gọi interface luôn phải tạo native indirect call.

## 7. C1 và C2 cùng có thể inline, nhưng khác budget/policy

HotSpot hỗ trợ **tiered compilation** theo những configurations thường gặp:

| Execution tier | Vai trò | Inlining |
| --- | --- | --- |
| Interpreter (level 0) | Chạy bytecode, tích lũy profiling | Không tạo JIT native code/inlining theo nghĩa compiler |
| C1 (levels 1–3 phổ biến) | Compile nhanh hơn, có các mức profiling | Có inlining với constraints/heuristics riêng |
| C2 (level 4 phổ biến) | Optimizing compiler, đầu tư sâu cho hot code | Có inlining sâu và liên kết nhiều global optimizations |

Không cần mọi method đi đủ 0→1→2→3→4. Một compilation của caller có thể inline callee ngay cả khi callee chưa được compile riêng; ngược lại callee đã compile thành nmethod không bắt buộc caller phải inline nó.

**Một call site có thể có quyết định khác ở các lần compile lại.** C1 và C2 có policies khác nhau; code nóng hơn, profile receiver types khác hoặc kích thước graph thay đổi có thể làm kết quả inlining thay đổi.

## 8. Vì sao HotSpot có thể từ chối inline?

Inlining giúp tối ưu nhưng cũng **tăng kích thước code tại call site**. Nếu caller gọi cùng callee ở nhiều nơi, inlining lặp lại có thể làm graph/machine code phình lớn.

Chi phí tiềm ẩn:

- **Compilation CPU/time:** IR nhiều nodes, optimizer xử lý lâu hơn.
- **Code cache pressure:** native machine code nhiều hơn, tốn bộ nhớ JVM process.
- **Instruction-cache locality:** code quá lớn làm CPU instruction cache kém hiệu quả.
- **Register pressure:** graph phức tạp có thể tăng spills/reloads.
- **Deoptimization metadata:** compiler cần lưu thông tin state/debug mappings để phục hồi execution đúng.
- **Recursion/call depth:** compiler phải có limits để không inline đệ quy vô hạn.
- **Exception/monitor/control-flow complexity:** body callee quá phức tạp có thể không đáng inline hoặc không hợp lệ với một optimization path cụ thể.

### Inlining budget và kích thước method

Oracle JDK 25 `java` manual mô tả các flags:

| Flag HotSpot | Ý nghĩa tổng quát | Lưu ý |
| --- | --- | --- |
| `-XX:+Inline` / `-XX:-Inline` | Bật/tắt method inlining | Flag theo HotSpot; thường bật mặc định |
| `-XX:MaxInlineSize=N` | C2: kích thước bytecode tối đa cho **cold method** được xem xét inline theo cơ chế liên quan | Không phải quy tắc duy nhất quyết định mọi inline |
| `-XX:FreqInlineSize=N` | C2: giới hạn size cho **hot method** | Có thể lớn hơn cold threshold |
| `-XX:InlineSmallCode=N` | C2: giới hạn code size của already-compiled callee trong một số điều kiện inline | Khác kích thước bytecode |
| `-XX:C1MaxInlineSize=N` | C1: kích thước cold method theo policy | Không áp nguyên C2 sang C1 |
| `-XX:MaxTrivialSize=N` | C2: giới hạn cho phương thức rất nhỏ/trivial | Không đồng nghĩa method luôn inline |
| `-XX:MaxNodeLimit=N` | Giới hạn nodes trong một single method compilation | Thay đổi có thể làm compile chậm và tăng memory use |

Các flags có thể thay đổi theo JVM release và không phải portable Java APIs. Những values trong manual là **defaults/examples cho version/configuration cụ thể**, không phải số luật học thuộc để áp mọi HotSpot instance.

### Những reason phổ biến trong `PrintInlining`

Log có thể diễn đạt những lý do như:

- `inline (hot)`: inline vì hot/profile phù hợp.
- `inline (hot)` sau virtual call analysis hoặc `accessor` trong những cases.
- `callee is too large`: callee quá lớn theo budget hiện tại.
- `hot method too big` hoặc các biểu thức tương tự: method hot nhưng vượt giới hạn.
- `already compiled into a big method`: compiled body lớn.
- `recursive inlining too deep`: giới hạn inline recursion.
- `virtual call` / target chưa đủ xác định: compiler có thể giữ dispatch.
- `not inlineable` hoặc những constraints khác: compiler không inline vì method/body policy.

**Các chuỗi reason trong log không được đảm bảo có format cố định qua mọi JDK.** Nhìn cả **inlining tree của caller và compile tier**; không đọc một dòng rời rồi kết luận mọi call sites đều giống nhau.

## 9. Runnable Java 17+: hai methods nhỏ được gọi trong hot loop

Tạo file **`InliningLab.java`**:

~~~java
public class InliningLab {
    private static volatile long result;

    static int twice(int x) {
        return x * 2;
    }

    static int plusOne(int x) {
        return x + 1;
    }

    static int pipeline(int x) {
        return plusOne(twice(x));
    }

    static long calculate(int iterations) {
        long total = 0;
        for (int i = 0; i < iterations; i++) {
            total += pipeline(i);
        }
        return total;
    }

    public static void main(String[] args) {
        result = calculate(100_000);
        System.out.println("total=" + result);
        System.out.println("single=" + pipeline(7));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -g InliningLab.java
java InliningLab
~~~

**Stdout chính xác trong execution bình thường, không bật diagnostics:**

~~~text
total=10000000000
single=15
~~~

Vì:

~~~text
pipeline(i) = 2*i + 1
sum(i=0..99999) = 2*(99999*100000/2) + 100000
                = 10,000,000,000
pipeline(7) = 2*7+1 = 15
~~~

`total` là `long` nên đủ sức chứa 10 tỷ; `twice(i)` là `int` nhưng `i < 100000` nên không overflow.

### Thử hai chiến lược runtime

Trên HotSpot hỗ trợ các flags:

~~~bash
java -Xint InliningLab
java -Xmixed InliningLab
java -XX:-Inline InliningLab
~~~

**Kết quả chương trình vẫn phải là hai dòng trên** khi chạy thành công. Trong `-Xint`, không có JIT compilation; `-XX:-Inline` điều chỉnh HotSpot JIT policy nhưng không đổi phương pháp gọi methods ở source và không phải cờ “chạy mọi thứ qua interpreter”.

Không thể dùng chênh lệch thời gian của ba lần chạy ngắn này để khẳng định inlining “tăng tốc N lần”. JVM startup, tiered warmup, loop OSR, compiler queues và OS scheduling đều nhiễu phép đo.

### So sánh `javap` với JIT

~~~bash
javap -c -p InliningLab
javap -v -p InliningLab
~~~

Trong `pipeline`, bạn thường thấy hai `invokestatic` gọi `twice`/`plusOne` ở class file. Sau khi HotSpot compiler inline ở runtime, native code có thể chứa arithmetic trong caller **mà không còn hai native calls tại call sites ấy**.

`javap` **không thể xác nhận** inlining đã hoặc chưa xảy ra ở execution thực tế, vì nó không hiển thị C1/C2 machine code.

## 10. Runnable Java 17+: mở rộng phạm vi giúp phân tích object

Tạo file **`ScopeExpansionDemo.java`**:

~~~java
public class ScopeExpansionDemo {
    static final class Pair {
        final int left;
        final int right;

        Pair(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    static int score(Pair pair) {
        return pair.left * 2 + pair.right;
    }

    static int makeScore(int base) {
        Pair pair = new Pair(base, base + 1);
        return score(pair);
    }

    public static void main(String[] args) {
        long total = 0;
        for (int i = 0; i < 10_000; i++) {
            total += makeScore(i);
        }

        System.out.println("total=" + total);
        System.out.println("one=" + makeScore(7));
    }
}
~~~

~~~bash
javac -g ScopeExpansionDemo.java
java ScopeExpansionDemo
~~~

**Stdout chính xác:**

~~~text
total=149995000
one=22
~~~

Vì:

~~~text
makeScore(base) = 2*base + (base+1) = 3*base+1
sum(i=0..9999) = 3*(9999*10000/2)+10000
               = 149,995,000
makeScore(7) = 3*7+1 = 22
~~~

**Điểm cần học:** nếu HotSpot inline `score` và phân tích object `Pair` không escape, nó **có thể** scalar-replace Pair, bỏ allocation, field loads và một phần operations liên quan trong optimized machine code.

**Không suy** từ kết quả `149995000` rằng scalar replacement chắc chắn xảy ra. Chương trình ngắn có thể chạy interpreter/C1/C2 tùy workload và JIT thresholds. Output chỉ chứng minh Java semantics và đường tính toán.

Nếu muốn khảo sát inlining, có thể chạy chương trình với `PrintCompilation`/`PrintInlining` và quan sát compile của `makeScore`/`score`; nếu muốn chứng minh elimination of allocation, cần profiler/instrumentation phù hợp, JIT compilation state và đủ warmup.

## 11. Runnable Java 17+: virtual call và receiver profile

Tạo file **`ReceiverInliningDemo.java`**:

~~~java
public class ReceiverInliningDemo {
    interface Adjuster {
        int apply(int value);
    }

    static final class AddTwo implements Adjuster {
        public int apply(int value) {
            return value + 2;
        }
    }

    static final class TimesThree implements Adjuster {
        public int apply(int value) {
            return value * 3;
        }
    }

    static int call(Adjuster adjuster, int n) {
        return adjuster.apply(n);
    }

    public static void main(String[] args) {
        Adjuster common = new AddTwo();
        Adjuster other = new TimesThree();

        int sum = 0;
        for (int i = 0; i < 10_000; i++) {
            sum += call(common, 4);
        }

        System.out.println("common-sum=" + sum);
        System.out.println("other=" + call(other, 4));
    }
}
~~~

~~~bash
javac -g ReceiverInliningDemo.java
java ReceiverInliningDemo
~~~

**Stdout chính xác:**

~~~text
common-sum=60000
other=12
~~~

`AddTwo.apply(4)` trả 6, lặp 10.000 lần có tổng 60.000. `TimesThree.apply(4)` trả 12.

**Điều chương trình thể hiện:** một call site qua interface có thể gặp receiver types khác nhau và phải giữ đúng dynamic dispatch.

**Điều chương trình chưa chứng minh:** call site đã monomorphic-optimized, đã inline `AddTwo`, hay đã deopt khi gặp `TimesThree`. Một JVM có thể chưa compile method hoặc có compiled fallback; logs/compiled-code instrumentation mới giúp xác minh.

## 12. Quan sát HotSpot inlining trực tiếp

Theo Oracle JDK 25 `java` manual, bật diagnostics:

~~~bash
java -XX:+PrintCompilation InliningLab
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -XX:+PrintCompilation InliningLab
~~~

Nếu method chưa đủ hot để compiler ghi sự kiện, **không có lời hứa** log `pipeline` hay `twice` sẽ xuất hiện. Có thể thử tăng workload/iterations trong môi trường test và bảo đảm đủ compilation time, nhưng đừng làm benchmark kết luận từ một run duy nhất.

### Nhìn inlining tree

Một log **minh họa hình thái**, không phải output bắt buộc:

~~~text
   @ 3   InliningLab::twice (4 bytes)   inline (hot)
   @ 6   InliningLab::plusOne (4 bytes) inline (hot)
~~~

Thông thường thụt lề thể hiện nested inlining, `@ BCI` thể hiện call site offset trong caller, còn lý do đi kèm cho biết compiler đánh giá như thế nào. Bytecode size số bytes ở ví dụ là **giả định minh họa**, không học thuộc; `javac` version và class contents có thể sinh số khác.

**Phải ghép log `PrintInlining` với compilation đang xét**: nếu một compiler đang compile `calculate`, tree có thể hiển thị `pipeline`, rồi `twice`/`plusOne` lồng bên trong. Đừng hiểu indentation như runtime Java call stack.

### Đọc lý do từ chối

Ví dụ dạng log giả định:

~~~text
@ 12  SomeClass::largeHelper (350 bytes) callee is too large
@ 21  SomeClass::virtualCall (20 bytes)  virtual call
@ 33  SomeClass::recursive (8 bytes)     recursive inlining too deep
~~~

Các reasons là gợi ý **vì sao một candidate cụ thể bị từ chối ở một call site**. Không có nghĩa method ấy “không thể inline trong mọi chương trình”.

### Log compilation có thể ghi ra file

Với HotSpot có diagnostic support:

~~~bash
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation InliningLab
~~~

Thường VM ghi compilation XML/log vào file do `-XX:LogFile` chỉ định hoặc default theo phiên bản. Cách này hữu ích với công cụ phân tích inlining trees/JITWatch thích hợp; log có thể rất lớn, nên đừng bật liên tục trên production mà không quản lý retention/disk.

### Compiler Control (advanced)

Oracle JDK 25 VM Guide có **Compiler Control** cho phép áp directives tại mức method/pattern, gồm các chỉ thị inline hoặc không inline trong phạm vi áp dụng. Đây là công cụ nghiên cứu/diagnostics, **không phải cách mặc định tối ưu Java code ở production**.

Đừng viết code Java phụ thuộc vào một annotation “`@Inline`” giả định là public Java standard. HotSpot có những internal annotations/intrinsics, nhưng không phải API portable mà ứng dụng nên lấy làm hợp đồng ngôn ngữ.

## 13. Inlining và lựa chọn target: `static`, `private`, `final`, virtual, interface

| Loại lời gọi | Target thường rõ đến mức nào? | Còn yếu tố gì? |
| --- | --- | --- |
| `invokestatic` | Target được resolution xác định theo bytecode | Compiler budget, side effects, class-init/linking semantics |
| `invokespecial` | Calls constructor/private/super với semantics phù hợp | Method size, recursion, exceptions và costs |
| `invokevirtual` | Runtime selection theo receiver | Type profiles, class hierarchy, guards, fallback |
| `invokeinterface` | Runtime interface dispatch | Receiver types, devirtualization và guards |
| `invokedynamic` | Dynamically linked call site từ bootstrap | Call-site target stability, MethodHandle graphs, compiler rules |

**Đây không phải bảng “opcode nào được inline / opcode nào không được”.** Các dạng gọi có thể được compiler inline khi thỏa correctness và profitability. Sự khác biệt chính là thông tin chắc chắn về target và điều kiện bảo vệ giả định.

Một `final` method có thể dễ xác định target hơn, nhưng HotSpot vẫn có thể không inline nếu method quá lớn. Ngược lại một interface call có receiver type profile ổn định có thể inline rất hiệu quả.

**`@Override` không phải hint inline.** Annotation này cho compiler kiểm tra Java overriding correctness; không chỉ thị HotSpot phải inline.

## 14. Inlining không được phép thay đổi hành vi Java

Ví dụ:

~~~java
static int safelyDivide(int a, int b) {
    return a / b;
}

static int caller(int value) {
    return safelyDivide(value, 0);
}
~~~

Đây là method minh họa, không phải file độc lập. Nếu compiler inline `safelyDivide(value,0)`, **`ArithmeticException`** vẫn phải xảy ra khi đường code được chạy theo Java semantics. Compiler không được vì inlining mà âm thầm trả 0.

Những quy tắc khác cũng phải được bảo toàn:

- **Null checks:** dereference `null` phải tuân Java `NullPointerException` semantics khi thích hợp.
- **Array bounds:** access ngoài bounds vẫn phải phát sinh `ArrayIndexOutOfBoundsException`.
- **Class initialization/linkage:** compiler phải giữ các hành động và lỗi có thể quan sát đúng theo JVM specification.
- **`synchronized`/monitors:** chỉ được loại monitor operations khi có chứng minh tương đương theo Java Memory Model.
- **`volatile`:** không thể xóa tùy ý các memory ordering/visibility effects.
- **Exceptions và stack traces:** runtime có debug/scope metadata để phục hồi logical Java frames khi cần, kể cả khi native frame được inline.

### Stack traces và inlined frames

Một method có thể được inlined nhưng debugger/profiler vẫn hiển thị nó như một **logical Java frame**. JIT duy trì metadata để khôi phục bối cảnh lời gọi, exceptions và deoptimization. Do đó, **thấy method trong stack trace không chứng minh JIT không inline**.

Cũng đừng xem số lượng Java stack frames trong một profiler sample là số native call instructions thực tế sau inlining.

## 15. Inlining quá mức có thể làm chậm chương trình

Giả sử `BigHelper` dài và được gọi ở 20 call sites khác nhau. Nếu JIT copy logic của nó vào mọi callers:

~~~text
Caller1  +  Body(BigHelper)
Caller2  +  Body(BigHelper)
...
Caller20 +  Body(BigHelper)
~~~

Tổng native code size có thể tăng nhiều so với giữ shared nmethod và calls. Hệ quả:

- Code Cache nhanh đầy hơn.
- CPU instruction cache misses và iTLB pressure có thể tăng.
- Register allocation khó hơn, spills nhiều hơn.
- JIT compilation time và CPU sử dụng tăng.
- Deoptimization metadata phức tạp hơn.
- Startup/warmup latency có thể xấu đi.

Do vậy câu **“inline càng nhiều càng nhanh”** là sai. Một callee nhỏ được gọi cực nhiều thường là ứng viên tốt; một callee khổng lồ có nhiều nhánh lạnh có thể không đáng duplicate.

**Compiler heuristics là compromise thực dụng**, không phải lời hứa tối ưu toàn cục. Các flags `MaxInlineSize` và `FreqInlineSize` là công cụ tuning/diagnostics, không phải giá trị nên tăng tùy tiện.

## 16. Khác biệt giữa inlining và các tối ưu dễ nhầm

| Khái niệm | Bản chất | Có đồng nghĩa inlining? |
| --- | --- | --- |
| **Inlining** | Đưa IR/body callee vào call site của caller | Chính khái niệm đang học |
| **Devirtualization** | Chọn/giới hạn target của virtual call theo type info | Không; giúp mở đường inline nhưng có thể chỉ trực tiếp hóa lời gọi |
| **Tail-call optimization** | Biến tail call theo chiến lược khác | Không; Java/HotSpot không hứa general TCO cho mọi method |
| **Escape analysis** | Xác định object/reference có escape không | Không; inlining có thể mở rộng phân tích |
| **Scalar replacement** | Thay object bằng thành phần fields/value, loại allocation | Không; là tối ưu downstream |
| **Constant folding** | Tính trước biểu thức với constants | Không; inlining thường làm xuất hiện constants |
| **Intrinsic** | Compiler/runtime dùng implementation chuyên biệt cho một method/operation | Không đồng nghĩa JIT copy body bytecode |
| **Loop unrolling** | Lặp lại loop body trong compiled representation | Không; thao tác trên loop, có thể xảy ra sau inlining |
| **OSR** | Chuyển vào compiled loop khi method đang chạy | Không; OSR compilation có thể hoặc không inline callees |
| **AOT** | Chuẩn bị code trước runtime execution theo công nghệ cụ thể | Không; thời điểm biên dịch là khác |
| **`javac` optimization** | Các biến đổi của compiler Java source-to-bytecode | Không tương đương C2 inlining at runtime |

**Inlining thường mang tính “enabler”:** bản thân nó có thể giảm call overhead, nhưng điểm đáng giá là giúp compiler nhìn được những phép tối ưu vốn trước đó bị call boundary che khuất.

## 17. Cách điều tra nếu một method “không inline như mong đợi”

Một quy trình an toàn:

1. **Xác nhận method được compile:** dùng `-XX:+PrintCompilation` hoặc runtime profiler. Không thấy method trong log có thể vì method lạnh hoặc đã bị inline vào compilation của caller khác; cần phân tích đúng.
2. **Xác định caller và call site:** compiler quyết định theo từng site, không phải chỉ tên callee.
3. **Bật `PrintInlining` trong môi trường test:** đọc tree và reasons, phân biệt compile tier C1/C2.
4. **Kiểm tra profiles:** caller có hot không, receiver types mono/poly/mega, method invocation/backedge frequency?
5. **Kiểm tra code size/budget:** bytecode callee dài, graph phức tạp, recursion, inline depth, native code-size.
6. **Kiểm tra JIT state:** method đã warmup chưa, code cache có chật, compilation jobs có bị trì hoãn?
7. **Đo outcome quan trọng:** CPU cycles, throughput, P95/P99 latency, allocation/GC metrics, code cache, compilation time.
8. **Chỉ chỉnh flags khi có chứng cứ:** `-XX:-Inline` để so sánh có kiểm soát; thay size thresholds cần benchmark và kiểm chứng trên JDK/GC/workload thực.

**Không dùng một lần `System.nanoTime()` rồi kết luận inline cải thiện chính xác N%.** Startup, tiered compilation, OSR, dead-code elimination, constant folding, GC và OS scheduling đều ảnh hưởng. Dùng **JMH** cho microbenchmark với warmup/forks, và workload server thực để đo end-to-end impact.

## 18. Các ngộ nhận thường gặp

| Phát biểu | Điều đúng |
| --- | --- |
| “Inline là copy Java source vào method khác.” | JIT mở rộng **IR/body** khi compile, không rewrite source Java. |
| “`javap -c` không còn `invoke` nghĩa đã inline.” | `javap` đọc class-file bytecode; JIT inlining nằm ở runtime compiled code. |
| “`static` method chắc chắn được inline.” | Target rõ hơn nhưng vẫn có compiler budgets và profitability. |
| “`final` hoặc `private` là tín hiệu bắt buộc.” | Không. HotSpot không hứa inline các methods đó. |
| “Java interface call không bao giờ inline.” | Có thể profile-guided devirtualization và guarded inlining. |
| “Inlining chỉ tiết kiệm overhead một lời gọi.” | Nó còn mở phạm vi IR analysis, thường có lợi lớn. |
| “C1 không inline, chỉ C2 inline.” | C1 cũng có inlining policy. |
| “Mọi call site của một callee có cùng inlining decision.” | Quyết định thường per-call-site, per-compilation. |
| “Compile callee riêng xong thì caller buộc phải inline.” | Không. Compilation độc lập khác inlining tại caller. |
| “Sau inline, mọi checks/exceptions bị bỏ.” | Java semantics, guards, exceptions vẫn phải bảo toàn. |
| “JIT luôn cần thể hiện stack frame native riêng cho mỗi inlined method.” | Debug/deopt giữ logical frames, không buộc native call frame riêng. |
| “Có method trong stack trace chứng minh nó chưa inline.” | Không. JIT có thể tái dựng logical Java frames. |
| “Mọi non-escaping object được chuyển lên native stack.” | HotSpot có thể scalar-replace và **loại allocation**, không hứa stack allocation tổng quát. |
| “Tăng MaxInlineSize sẽ chắc chắn tăng throughput.” | Code growth/compile cost/cache pressure có thể làm chậm. |
| “Một caller được tối ưu thì không cần deoptimization nữa.” | Guard assumptions có thể bị invalidated. |
| “Inlining thay đổi nghĩa của synchronized/volatile.” | Compiler phải bảo toàn Java Memory Model. |
| “`-XX:-Inline` biến JVM thành interpreter-only.” | Nó tắt JIT method inlining theo HotSpot policy, không tắt JIT compilation. |
| “`PrintInlining` có output cố định ở mọi JDK.” | Compiler decisions/format tùy JDK, tier, profile và workload. |
| “Source chỉ có một dòng là method luôn trivial.” | Bytecode và side effects, intrinsic/VM behavior vẫn quan trọng. |
| “Chỉ cần một benchmark lạnh là biết inlining đáng giá.” | Phải phân biệt startup, warmup và steady-state. |

## 19. Tự kiểm tra có đáp án

**Câu 1.** Vì sao inlining có thể đem lại lợi ích ngoài việc giảm call overhead?  
**Đáp án:** Nó mở IR giữa caller và callee, cho constant propagation, dead-code elimination, range analysis, escape analysis và những tối ưu khác nhìn xuyên qua method boundary.

**Câu 2.** `private static int f(int)` chắc chắn inline không?  
**Đáp án:** Không. Target có thể rõ nhưng compiler xét hotness, code size, budget, depth và profitability.

**Câu 3.** `javap -c` vẫn thấy `invokestatic` sau khi chương trình từng chạy với JIT. Có chứng minh không inline không?  
**Đáp án:** Không. `javap` đọc class file gốc; JIT inlining tác động compiled IR/native code ở runtime.

**Câu 4.** Hai call sites gọi cùng callee có thể quyết định inline khác nhau không?  
**Đáp án:** Có, dựa trên caller context/profile/budget và compiler tier.

**Câu 5.** Vì sao inlining `score(Pair)` vào `makeScore` có thể giúp loại object allocation?  
**Đáp án:** Sau inlining compiler thấy Pair chỉ được đọc fields mà không escape, có thể áp dụng escape analysis và scalar replacement.

**Câu 6.** Một virtual/interface call có thể được inline không?  
**Đáp án:** Có, nhờ devirtualization/receiver type profiling, guards hoặc CHA khi hợp lệ.

**Câu 7.** Receiver type mới xuất hiện sau optimized compilation thì điều gì đảm bảo correctness?  
**Đáp án:** Runtime guards, generic fallback/deoptimization, dependency invalidation và recompile thích hợp.

**Câu 8.** Inlining C1 và C2 có giống hệt policy không?  
**Đáp án:** Không. Chúng có budgets, optimization goals và profiles khác nhau.

**Câu 9.** `-XX:FreqInlineSize` và `-XX:MaxInlineSize` khác nhau thế nào trong C2?  
**Đáp án:** Cờ đầu liên quan hot-method inline size limit; cờ sau là cold-method bytecode inline size limit trong policy được tài liệu hóa.

**Câu 10.** Vì sao inline một callee rất lớn vào 20 callers có thể làm chậm?  
**Đáp án:** Native code growth, instruction cache pressure, compile CPU/memory overhead và register spills.

**Câu 11.** `InliningLab` cho output nào?  
**Đáp án:** `total=10000000000` và `single=15` trong execution bình thường, bất kể decisions inline.

**Câu 12.** `ReceiverInliningDemo` có chứng minh deoptimization đã xảy ra không?  
**Đáp án:** Không. Output chỉ chứng minh đúng dynamic dispatch; compiler có thể chưa compile hoặc đã có fallback path.

**Câu 13.** Dùng công cụ nào để quan sát HotSpot inline decisions?  
**Đáp án:** `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -XX:+PrintCompilation` trên HotSpot hỗ trợ.

**Câu 14.** JIT có thể bỏ exception trong method sau inline không?  
**Đáp án:** Không nếu exception vẫn có thể xảy ra theo Java semantics; chỉ được loại checks/paths chứng minh không cần.

## 20. Tài liệu chính thức

- [Oracle JDK 25 — `java` Command, Advanced JIT Compiler Options](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-XX:+Inline`, `-XX:-Inline`, `MaxInlineSize`, `FreqInlineSize`, `InlineSmallCode`, `C1MaxInlineSize`, `PrintCompilation` và `PrintInlining`.
- [Oracle JDK 25 — Java HotSpot Virtual Machine Performance Enhancements](https://docs.oracle.com/en/java/javase/25/vm/java-hotspot-virtual-machine-performance-enhancements.html): Tiered Compilation, Code Cache, escape analysis và scalar replacement.
- [Oracle JDK 25 — Java Virtual Machine Guide](https://docs.oracle.com/en/java/javase/25/vm/index.html): HotSpot/compiler technology, Compiler Control và performance.
- [JVMS Java SE 25 — Chapter 6, Instruction Set](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html): `invokestatic`, `invokevirtual`, `invokeinterface`, `invokespecial` và JVM bytecode semantics.
- [JVMS Java SE 25 — §4.7.3 Code Attribute](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.3): code bytes của method và metadata cần cho execution.
- [OpenJDK HotSpot — C2 compiler source](https://github.com/openjdk/jdk/tree/master/src/hotspot/share/opto): compiler optimization and inlining implementation; nhánh master có thể khác phiên bản chạy thực.
- [OpenJDK HotSpot — C1 compiler source](https://github.com/openjdk/jdk/tree/master/src/hotspot/share/c1): C1 inlining and compiler policies.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): đo hiệu năng Java đáng tin hơn benchmark lạnh đơn lẻ.

**Ghi nhớ:** Inlining trong HotSpot **đưa code của callee vào phạm vi phân tích của caller tại một call site**, vừa có thể giảm invocation overhead, vừa mở đường cho **các tối ưu liên thủ tục** như constant propagation, escape analysis và scalar replacement. Nhưng inline quá mức có thể gây **code bloat**, tốn compilation time và làm cache locality xấu đi. HotSpot vì vậy dùng **profiling, C1/C2 heuristics, budgets, guards và deoptimization** để cân bằng lợi ích với chi phí mà vẫn giữ nguyên Java semantics.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-002">Khử lời gọi ảo dựa trên thông tin kiểu và giới hạn của giả định</a>

<details>
<summary>Xem chi tiết</summary>

# Khử lời gọi ảo dựa trên thông tin kiểu và giới hạn của giả định

**Devirtualization (khử lời gọi ảo)** là một nhóm tối ưu hóa của JIT compiler nhằm biến một lời gọi có khả năng **dispatch động** đến nhiều implementation thành **đường gọi trực tiếp** tới đích phù hợp, hoặc thành **các nhánh có kiểm tra kiểu** mà nhánh phổ biến có thể chạy trực tiếp. Khi đã biết đích, JIT có thêm cơ hội **method inlining**, constant propagation, dead-code elimination, escape analysis và những tối ưu khác.

Trong **HotSpot JVM**, thông tin được dùng có thể đến từ:

1. **Kiến thức tĩnh chắc chắn**, chẳng hạn receiver có exact runtime type đã được chứng minh hoặc method không thể bị override.
2. **Class Hierarchy Analysis (CHA)**: phân tích các class/hierarchy **đang được JVM biết tới** để tìm một method target duy nhất; nếu chương trình có thể nạp class mới, compiled code phải có cơ chế theo dõi/thu hồi giả định.
3. **Type profiling**: dữ liệu HotSpot thu thập tại **call site** về concrete receiver classes thực tế, ví dụ một site gần như chỉ thấy một type.
4. **Các ràng buộc từ flow analysis**: type checks, casts, allocations và kiểm tra nhánh có thể thu hẹp tập receiver types trên một execution path.

**Không được hiểu** devirtualization là thay đổi semantics của Java. Java vẫn phải gọi đúng implementation theo **runtime class của receiver**, tuân thủ khả năng nạp class động, overload/override, null checks, exceptions và Java Memory Model. Tối ưu hóa phỏng đoán có thể bị hủy qua **deoptimization** hoặc đi sang một **fallback dispatch path** khi giả định sai.

Bài tập trung vào các tối ưu này trong **HotSpot với JDK 17+**. CHA, type-profile-based speculation, inline caches, compiled nmethods và C1/C2 là **implementation details**, không phải một cam kết rằng mọi JVM sử dụng cùng thuật toán hoặc luôn khử được mọi lời gọi ảo.

## 1. Dispatch động là gì?

Xét một interface:

```java
interface Payment {
    int calculate(int amount);
}

final class StandardPayment implements Payment {
    @Override
    public int calculate(int amount) {
        return amount + 2;
    }
}

final class PremiumPayment implements Payment {
    @Override
    public int calculate(int amount) {
        return amount * 2;
    }
}
```

Đây là các class/interface hợp lệ, nhưng đoạn này **chưa có `main`**. Một method sử dụng interface:

```java
static int pay(Payment payment, int amount) {
    return payment.calculate(amount);
}
```

Nếu đặt method vào một class hợp lệ, kiểu tĩnh của `payment` là `Payment`. Nhưng trong runtime, `payment` có thể trỏ đến `StandardPayment` hoặc `PremiumPayment`; hai implementation trả về các giá trị khác nhau.

```text
Call site: payment.calculate(amount)
                    │
                    ▼
          runtime receiver class?
                /       \
               /         \
    StandardPayment   PremiumPayment
       amount + 2       amount * 2
```

**Java-level semantics:** đích phụ thuộc receiver runtime type. Không được suy từ **khai báo biến kiểu `Payment`** rằng JVM chắc chắn gọi một class cụ thể.

### Đặc tính của polymorphism

- **Overloading**: chọn chữ ký method theo luật compile-time của Java; là vấn đề khác.
- **Overriding**: subclass/interface implementation định nghĩa method tương ứng; runtime dispatch chọn implementation phù hợp.
- **Dynamic dispatch**: điều khiển lựa chọn method theo runtime type khi lệnh gọi có dynamic-dispatch semantics.
- **Devirtualization**: tối ưu cách thực thi dispatch; **không xóa quyền override** mà Java cho phép.

**Điểm mấu chốt:** cùng một call site bytecode có thể gọi những concrete methods khác nhau ở những thời điểm khác nhau nếu receiver types khác nhau.

## 2. Bytecode liên quan: invokevirtual và invokeinterface

JVM có các instruction gọi method khác nhau:

| JVM opcode | Vai trò chính | Liên quan tới devirtualization |
| --- | --- | --- |
| `invokevirtual` | Lời gọi instance method có dispatch theo runtime class | Có thể được HotSpot khử dispatch khi đích xác định |
| `invokeinterface` | Lời gọi method thông qua interface; tìm implementation phù hợp | Có thể được optimize bằng CHA/type profile |
| `invokespecial` | Gọi constructor, `super` và một số instance method theo luật đặc biệt | Không tương đương generic virtual dispatch |
| `invokestatic` | Gọi static method | Không phải lời gọi receiver động |
| `invokedynamic` | Dynamic call site được link qua bootstrap method | Cơ chế liên kết riêng; không đánh đồng với `invokevirtual` |

Ví dụ trên, `payment.calculate(amount)` thường được `javac` chuyển thành **`invokeinterface`** vì chữ ký call site dùng interface. Nếu dùng kiểu tĩnh là một non-interface class, lời gọi instance method có thể thể hiện bằng **`invokevirtual`**.

**Quan trọng về từ ngữ:** một bytecode `invokevirtual` **không chứng minh** CPU phải làm vtable lookup ở mọi lần chạy. JIT có thể biến nó thành direct call hoặc inline; `javap -c` vẫn in bytecode cũ vì JIT không phải sửa class file.

### Direct binding khác JIT devirtualization

Nếu method là `static`, JVM đã dùng `invokestatic`; đấy **không phải** bài toán loại bỏ dynamic dispatch của một call site vốn có thể đa hình. Tương tự, `super.method()` và constructor dùng `invokespecial` theo luật riêng.

Một method `final` hoặc class `final` có thể khiến compiler xác định target tĩnh ngay cả khi bytecode vẫn là `invokevirtual`. Cái cần xét là **khả năng override/đích thực tế**, không chỉ tên opcode.

## 3. Đường thực thi trước và sau tối ưu

**Trước tối ưu (mô hình logic):**

```text
load receiver
  │
  ├─ null? → NullPointerException theo semantics
  │
  ▼
resolve/dispatch đến concrete target
  │
  ▼
call implementation đúng
```

**Sau khi JIT có căn cứ cho một target phổ biến:**

```text
load receiver
  │
  ├─ null semantics vẫn phải đúng
  │
  ▼
check receiver type == HotClass?
       │              │
      yes             no
       │              │
       ▼              ▼
inlined/direct     generic virtual dispatch
HotClass path      hoặc uncommon trap
       │              │
       └──────────────┘
                 │
                 ▼
            đúng kết quả Java
```

Đây là sơ đồ conceptual. Trong machine code thực tế, null check có thể dùng **implicit null check**, guard có thể được hoán đổi/hợp nhất, và nhánh miss có thể dùng **deoptimization**, **runtime stub** hoặc fallback virtual call. Không có cam kết tất cả HotSpot versions chèn đúng một phép `instanceof` hoặc check `getClass()` theo thứ tự như hình.

### Khử lời gọi ảo không nhất thiết là inlining

- **Devirtualization**: xác định hoặc thu hẹp **callee target** để tránh dispatch tổng quát ở hot path.
- **Inlining**: đặt logic method được gọi **vào code của caller**, loại bỏ nhu cầu một call boundary riêng ở path đó.
- Có thể **devirtualize nhưng không inline**, nếu callee quá lớn, compiler budget hết, hoặc heuristic không thấy lợi.
- Có thể **inline nhiều target** trong các nhánh guarded polymorphic call site.
- Inlining mở đường cho nhiều tối ưu xuyên method, nhưng làm code size tăng, có thể gây áp lực lên **code cache** và instruction cache.

Đừng xem log “`inline (hot)`” là một chứng nhận rằng **mọi** lời gọi qua interface trong ứng dụng đã chuyển thành direct/inlined code.

## 4. Trường hợp có thể xác định đích chắc chắn

Có những thông tin giúp JIT **chứng minh** một method call không có nhiều target hợp lệ trên đường thực thi liên quan.

### 4.1. Class `final`

```java
final class FixedCalculator {
    int compute(int x) {
        return x + 1;
    }
}
```

`FixedCalculator` không thể có subclass hợp lệ theo luật Java. Nếu receiver có exact runtime class này và method cụ thể không có dispatch biến thể khác, việc xác định đích rất thuận lợi. Nhưng **tối ưu vẫn phụ thuộc compiler policy**: không có hợp đồng rằng method luôn được inline hay code chỉ gồm một phép cộng.

### 4.2. Method `final`

```java
class Base {
    final int stable(int x) {
        return x + 3;
    }
}
```

Subclass không được override `stable(int)`; compiler có thêm thông tin về target. **Không phải** vì keyword `final` tự động tạo ra machine code nhanh hơn ở mọi lần gọi: JIT còn cân nhắc kích thước method, tần suất chạy, inlining budgets và profiling.

### 4.3. Exact runtime type được chứng minh từ flow

```java
static int use() {
    FixedCalculator c = new FixedCalculator();
    return c.compute(10);
}
```

Đây là method minh họa, cần đặt trong class có thể truy cập `FixedCalculator`. Trong trường hợp trên, compiler có thể suy ra exact type của `c` ở call site, dù tạm bỏ qua việc một bản build có chọn inline hay không.

Nhưng nếu viết `Base b = loadFromExternalSource();`, chưa có bằng chứng exact type. Tên kiểu tĩnh `Base` không tự khẳng định runtime type luôn đúng `Base`.

### 4.4. Sealed types trong Java 17

```java
sealed interface Token permits TextToken, NumberToken {}

final class TextToken implements Token {}

final class NumberToken implements Token {}
```

Java 17 hỗ trợ sealed classes/interfaces để giới hạn những kiểu **trực tiếp** được phép extend/implement. Đây là dữ kiện cấu trúc hữu ích, nhưng **không đủ để tuyên bố JIT chắc chắn biết một method target duy nhất**: sealed hierarchy vẫn có thể có nhiều concrete classes, non-sealed branches trong hierarchy thích hợp, và JIT policy có thể không khai thác thông tin đó trong mọi runtime.

**Đừng đồng nhất “closed hierarchy” với “monomorphic call site”.** Một sealed interface có hai implementation hoàn toàn có thể nhận cả hai types ở cùng một site.

## 5. Class Hierarchy Analysis (CHA)

**CHA** phân tích mối quan hệ giữa class/interface và những concrete method targets mà JVM **đã biết hoặc có thể chứng minh theo constraints đang quản lý**.

Giả sử:

```java
abstract class Handler {
    abstract int run(int x);
}

final class OnlyHandler extends Handler {
    @Override
    int run(int x) {
        return x * 3;
    }
}
```

Trong một thời điểm JVM chỉ đang biết `OnlyHandler` là concrete subtype của `Handler`, một lời gọi `handler.run(x)` qua `Handler` có thể **hiện đang có một concrete target duy nhất**. HotSpot có thể dùng thông tin ấy để devirtualize/inline.

### Nhưng class hierarchy có thể thay đổi

Java cho phép **dynamic class loading** theo quy tắc class loader/runtime. Một class mới được nạp sau khi code đã JIT compile có thể tạo thêm một concrete implementation.

```text
Thời điểm T1:
  Handler
     └── OnlyHandler    (unique loaded concrete target)

JIT ở T1: dùng giả định CHA → optimized call path

Thời điểm T2:
  Handler
     ├── OnlyHandler
     └── AnotherHandler (nạp sau)

JVM phải làm compiled assumptions phù hợp lại với
hierarchy mới: invalidate/deopt/recompile khi cần.
```

**Nguyên tắc an toàn:** nếu JIT tối ưu dựa trên giả định “chỉ có một concrete target **hiện được biết**”, giả định đó **không mang nghĩa bất biến suốt đời tiến trình**. HotSpot theo dõi các **compiled-code dependencies** liên quan và có thể đánh dấu nmethod không còn entrant, deoptimize hoặc biên dịch lại khi hierarchy invalidation xảy ra.

Không thể lấy một ảnh snapshot lúc T1 rồi tuyên bố JVM sẽ gọi `OnlyHandler` khi nhận object `AnotherHandler` ở T2. Nếu Java semantics yêu cầu gọi override mới, VM phải làm đúng.

### CHA khác devirtualization dựa trên type profiling

| Nguồn thông tin | Tính chất | Giới hạn |
| --- | --- | --- |
| **CHA** | Dựa vào class hierarchy và tập targets biết được | Dynamic loading có thể làm giả định cũ mất hiệu lực |
| **Runtime type profile** | Dựa trên những receiver types thực sự quan sát tại **call site** | Workload/traffic có thể thay đổi, profile có thể thiếu type hiếm |
| **Exact type proof** | Dựa vào constraints tĩnh/dataflow chắc chắn trên một path | Chỉ áp dụng nơi compiler chứng minh được |
| **Final method/class** | Ngăn override hoặc subclassing theo Java semantics phù hợp | Không tự bảo đảm compiler chọn inline |

Các nguồn thông tin **có thể được dùng phối hợp**, không phải bốn algorithms loại trừ nhau.

## 6. Type profiling: một call site có thể monomorphic hoặc polymorphic

Type profile mô tả **hành vi tại một vị trí gọi**, không đơn giản mô tả “ứng dụng có bao nhiêu implementation của interface”.

| Kiểu call site | Quan sát thường gặp | Cơ hội tối ưu |
| --- | --- | --- |
| **Monomorphic** | Site hầu như chỉ thấy một concrete receiver type | Guarded/direct devirtualization và inlining thuận lợi |
| **Bimorphic/polymorphic nhỏ** | Site thấy một số ít concrete types, có thể hai | Có thể tạo nhiều guarded paths/inlining targets |
| **Megamorphic** | Site thấy nhiều receiver types, profile không đại diện tốt cho một tập nhỏ | Generic dispatch có thể kinh tế và ổn định hơn |
| **Unprofiled/cold** | Chưa có đủ runtime evidence | Interpreter/C1 hoặc đường gọi tổng quát |

**Không có ngưỡng bất biến cho “megamorphic = đúng 3 classes”.** HotSpot có khả năng/trần ghi type profiles tùy build/flags, đồng thời quyết định inline dựa trên budget, probabilities và hotness. Một framework có hàng trăm implementation ở toàn bộ process nhưng mỗi site chỉ nhận một type vẫn có thể có **monomorphic sites**.

### Ví dụ hai call sites khác nhau

```java
static int siteA(Payment p, int x) {
    return p.calculate(x);
}

static int siteB(Payment p, int x) {
    return p.calculate(x);
}
```

Đây là các methods minh họa trong một class. Nếu `siteA` chỉ nhận `StandardPayment` còn `siteB` nhận cả hai implementation, **type profiles có thể khác** dù cả hai có cùng Java signature và cùng source-level interface call.

JIT có thể inline `siteA` và giữ dispatch ở `siteB`. Điều đó **không mâu thuẫn**: tối ưu theo call site là một thuộc tính của runtime path, không phải quyết định toàn cục cho mọi invocation của interface.

### Call site còn có thể nằm trong compiled caller sau inlining

Nếu method `route(Payment p)` được inline vào một caller khác, các compiler transformations có thể tạo những cơ hội exact type specialization mới. Vì vậy khi đọc logs, hãy xét **bối cảnh inlining tree và BCI**, không chỉ một tên method.

## 7. Guarded devirtualization và polymorphic inline caches

Khi profile nói rằng một type `FastPayment` xuất hiện phần lớn thời gian, JIT có thể dùng mô hình:

```text
if (receiver is FastPayment) {
    // direct call hoặc inline FastPayment.calculate()
} else {
    // fallback virtual/interface dispatch
    // hoặc uncommon trap → deopt/reprofile
}
```

Đây là **pseudocode**, không phải Java source thực tế HotSpot phải tạo. Quan trọng là **guard protects speculation**:

- Nếu receiver đúng type dự đoán, chạy fast path.
- Nếu receiver khác, **không được gọi nhầm** implementation.
- Nếu receiver null, phải giữ kết quả `NullPointerException` theo semantics.
- Nếu class hierarchy thay đổi, compiled dependencies phải được xử lý an toàn.
- Các exceptions/side effects của target method phải không bị thay đổi về ngữ nghĩa.

**Polymorphic Inline Cache (PIC)** là một khái niệm thường dùng cho đường dispatch có một số receiver shapes/types được lưu/kiểm tra để tăng tốc. HotSpot có cơ chế inline caches, profile-based type guards và compiled call optimizations, nhưng **không nên khẳng định mọi optimized call site được hiện thực bằng một cấu trúc PIC giống nhau**. Thuật ngữ mô tả họ kỹ thuật; machine code thật tùy VM/CPU/JDK.

### Hai target hot

Nếu site thường thấy `FastPayment` và `DiscountPayment`, VM có thể chọn logic minh họa:

```text
receiver == FastPayment klass?
  yes → optimized FastPayment path
  no  → receiver == DiscountPayment klass?
           yes → optimized DiscountPayment path
           no  → general dispatch / deoptimization
```

Bộ biên dịch **không có nghĩa vụ tạo đúng hai guards**. Nếu code size/branch prediction/inline budget không thuận lợi, nó có thể chọn generic virtual dispatch dù profile thấy ít types.

## 8. Giới hạn của giả định: tại sao cần deoptimization?

Một giả định profile có thể sai theo thời gian. Ví dụ:

- Một endpoint trước đó chỉ nhận `StandardPayment`.
- Runtime bắt đầu nhận cả `PremiumPayment` khi traffic thay đổi.
- Một plugin loader nạp implementation mới.
- Dynamic proxies hoặc generated classes tạo nhiều receiver types.
- Optimized inlining đã dự đoán một hierarchy target duy nhất, nhưng class loading làm hierarchy thay đổi.

Đó không phải bug của Java hay bằng chứng compiler biên dịch sai: **HotSpot phải có đường rút lui**.

### Uncommon trap và code invalidation

JIT có thể tạo **uncommon trap** khi một nhánh ít xảy ra hoặc một assumption không còn phù hợp. JVM có thể:

1. Dừng execution của compiled path tại điểm hợp lệ.
2. Tái dựng trạng thái bytecode frames/locals/operand stacks cần thiết.
3. Tiếp tục qua interpreter hoặc compiled code khác.
4. Cập nhật profile/trap history và biên dịch lại nếu method vẫn hot.

Mã native trước đó có thể bị đánh dấu **not entrant** để không nhận những calls mới; việc dọn bỏ compiled code cần tuân theo an toàn các thread đang thực thi.

### Guard failure không nhất thiết bằng “deopt”

Một compiler cũng có thể bố trí **fallback virtual dispatch** khi guard thất bại, không cần deopt mọi lần. Do đó thấy callsite trở nên polymorphic **không chứng minh** đã xảy ra một uncommon trap cụ thể; cần compiler diagnostics của JVM thực tế.

### Deoptimization không làm chương trình trả sai kết quả

Ví dụ:

```java
Payment p = obtainPayment();
int result = p.calculate(10);
```

Đây là đoạn trong một method; `obtainPayment()` chưa định nghĩa. Dù JIT từng quan sát phần lớn `p` là `StandardPayment`, nếu lần này nhận `PremiumPayment`, JVM vẫn phải trả **20** (theo định nghĩa `PremiumPayment` ở ví dụ trên), không phải **12**. Nếu guard nhận ra khác type, nó chọn implementation hợp lệ.

**Điểm cốt lõi:** giả định được phép **tăng tốc path thường gặp**, không được phép **thu hẹp tập hành vi Java hợp lệ**.

## 9. Class loading động và giới hạn của CHA

Trên JVM hỗ trợ dynamic loading, một class chưa được load có thể tồn tại trong classpath/JAR hoặc được tạo lúc runtime. Sự vắng mặt của nó trong hierarchy hiện tại **không chứng minh không thể xuất hiện trong tương lai**.

Các điểm lưu ý:

- CHA dựa vào những facts và dependency management mà VM có khả năng duy trì.
- Thay đổi inheritance tree có thể buộc invalidate compiled code.
- Một class được load không nhất thiết tạo instance thực sự đi vào hot call site.
- Ngược lại, một type mới có thể xuất hiện ở call site dù đã được load từ trước; khi đó type profile dùng để tối ưu có thể thay đổi.
- Class loader identity là một phần của **runtime type identity**. Cùng tên class ở hai class loaders không luôn là cùng runtime class.
- Reflection, dependency injection và plugin systems có thể làm hierarchy “mở” hơn theo thời gian.

**Không nên** vì có plugin architecture mà mặc nhiên kết luận JIT hoàn toàn không thể devirtualize. Một call site vẫn có thể monomorphic trong đa số traffic; VM dùng guards/dependency checks để bảo toàn correctness.

### Interface default methods và method resolution

Java interfaces có thể có `default` methods và những luật resolution phức tạp hơn một class-only hierarchy. HotSpot phải tuân theo method lookup/linkage của JVM, kể cả interface resolution và các errors được định nghĩa khi chương trình không hợp lệ sau thay đổi binary tương thích.

Do đó “biết class của receiver” là phần rất quan trọng nhưng **không có nghĩa một `invokeinterface` luôn được tối ưu chỉ bằng phép so một class name string**. VM còn giải quyết target method theo runtime metadata và các JVM linkage rules.

## 10. Inlining khuếch đại lợi ích của devirtualization

Giả sử compiler xác định `calculate` luôn gọi implementation:

```java
final class AddFee {
    int calculate(int x) {
        return x + 2;
    }
}

static int finalAmount(AddFee fee, int base) {
    return fee.calculate(base) * 3;
}
```

Đây là mã minh họa đặt trong một class/compilation unit hợp lệ. Nếu JIT inline method nhỏ, phép toán có thể tối ưu gần như:

```text
result = (base + 2) * 3
```

Nhưng đây **không phải native assembly đã đo**, mà chỉ là **ngữ nghĩa tính toán tương đương**. Compiler phải bảo toàn null checking nếu `fee` có thể null: gọi `fee.calculate(...)` phải ném `NullPointerException` ở đường cần thiết. Không thể tùy tiện thay lời gọi bằng biểu thức số học nếu việc thay sẽ xóa lỗi hoặc side effect quan sát được.

### Tối ưu xuyên ranh giới method

Sau inlining, JIT có thể thấy:

- Constants của caller truyền vào callee.
- Branches không cần thiết trên một hot path.
- Array bounds checks có thể chứng minh an toàn.
- Allocations không escape, tạo cơ hội scalar replacement.
- Loop computation có thể hoist/vectorize theo điều kiện.
- Giá trị trả về có thể constant-fold khi inputs cố định.

**Tuy nhiên:** Java Memory Model, volatile operations, locking, exception ordering và các side effects vẫn là ràng buộc correctness. JIT không được xóa side effect chỉ vì method bị inline.

### Devirtualization không tự làm call bằng 0 chi phí

Một guarded path có thể thêm check/branch, code cache pressure, instruction cache pressure và deoptimization work. Nếu call target nhỏ và hot, inlining có thể thắng; nếu callsite megamorphic hoặc targets quá lớn, dispatch chung có thể kinh tế hơn.

## 11. C1, C2 và warm-up: vì sao trạng thái JIT thay đổi theo thời gian?

HotSpot tiered compilation có thể cho method chạy ở interpreter rồi C1, sau đó C2 khi hot và đủ profile. Thông thường:

- **Interpreter / C1 profiling** thu thập invocation/backedge counters, type/branch data.
- **C1** có thể tạo mã nhanh để giảm overhead interpretation trong lúc vẫn profile.
- **C2** có thể sử dụng type profiles/CHA để devirtualize và inline hot calls.
- Nếu assumptions đổi, **deopt/recompile** có thể xảy ra.

Không phải mọi JIT optimization chỉ có ở C2; C1 cũng có các tối ưu và cơ chế guarded calls của nó. Nhưng C2 thường có ngân sách và cấu trúc tối ưu chuyên sâu hơn.

### Một benchmark có thể trải qua nhiều “đời” compiled code

```text
Startup:
  call site còn cold → interpreter/generic dispatch

Warm-up A:
  receiver = ClassA nhiều → profile monomorphic
  → C1/C2 có thể tạo optimized ClassA fast path

Traffic phase B:
  receiver = ClassB xuất hiện
  → guard fail/fallback hoặc deopt

Warm-up B:
  profile nhiều types hơn
  → recompile hoặc generic dispatch
```

**Không suy** kết quả performance sau 1.000 calls bằng trạng thái của cùng method sau 1 triệu calls. Compiler thresholds, queue length, code cache và CPU quotas cũng ảnh hưởng.

## 12. Java 17+ runnable: cùng một call site nhận nhiều receiver types

Để quan sát nguyên tắc, tạo file **`DevirtualizationDemo.java`**:

```java
public class DevirtualizationDemo {
    interface Operation {
        int apply(int x);
    }

    static final class PlusTwo implements Operation {
        @Override
        public int apply(int x) {
            return x + 2;
        }
    }

    static final class TimesTwo implements Operation {
        @Override
        public int apply(int x) {
            return x * 2;
        }
    }

    static final class MinusOne implements Operation {
        @Override
        public int apply(int x) {
            return x - 1;
        }
    }

    private static volatile long sink;

    // Cùng một invokeinterface call site ở bytecode của invoke.
    static int invoke(Operation operation, int value) {
        return operation.apply(value);
    }

    static long phase(Operation operation) {
        long sum = 0;
        for (int i = 0; i < 100_000; i++) {
            sum += invoke(operation, i % 10);
        }
        return sum;
    }

    public static void main(String[] args) {
        Operation plus = new PlusTwo();
        Operation times = new TimesTwo();
        Operation minus = new MinusOne();

        long first = phase(plus);
        long second = phase(times);

        int a = invoke(plus, 10);
        int b = invoke(times, 10);
        int c = invoke(minus, 10);

        sink = first + second + a + b + c;

        System.out.println("first phase: " + first);
        System.out.println("second phase: " + second);
        System.out.println("plus(10): " + a);
        System.out.println("times(10): " + b);
        System.out.println("minus(10): " + c);
        System.out.println("checksum: " + sink);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 DevirtualizationDemo.java
java DevirtualizationDemo
```

**Standard output chính xác (khi không bật JIT/GC logging):**

```text
first phase: 650000
second phase: 900000
plus(10): 12
times(10): 20
minus(10): 9
checksum: 1550041
```

### Giải thích số học

- Trong mỗi `phase`, `i % 10` cho dãy **0..9**, lặp lại 10.000 chu kỳ.
- `PlusTwo.apply(i % 10)` cho 2..11; tổng một chu kỳ là **65**; `first = 65 × 10.000 = 650.000`.
- `TimesTwo.apply(i % 10)` cho 0,2,...,18; tổng một chu kỳ **90**; `second = 900.000`.
- `PlusTwo.apply(10) = 12`; `TimesTwo.apply(10) = 20`; `MinusOne.apply(10) = 9`.
- `checksum = 650.000 + 900.000 + 12 + 20 + 9 = 1.550.041`.
- Field `volatile sink` bảo đảm assignment và read tương ứng có nghĩa quan sát được; tuy nhiên compiler **vẫn có thể thực hiện nhiều optimization hợp lệ khác**.

### Điều demo chứng minh và không chứng minh

**Chứng minh:**

- Một **interface type** có nhiều implementations hợp lệ.
- Method `invoke` có cùng source-level call site nhưng tạo kết quả phù hợp từng runtime receiver type.
- Một phase dùng một implementation nhiều lần; phase khác dùng implementation khác.
- Java semantics vẫn đúng dù JIT thay đổi chiến lược dispatch.

**Không chứng minh:**

- `invoke` chắc chắn đã được compiled ở C2 level 4.
- HotSpot chắc chắn đã dùng CHA, một guard cụ thể hay uncommon trap.
- JVM bắt buộc đi qua monomorphic → bimorphic → megamorphic theo đúng ba đoạn.
- Máy khác sẽ in cùng số JIT compilation events.
- Một time measurement ngắn là bằng chứng chi phí virtual call trong production.

Kết quả tính toán **ổn định** nhưng việc JIT có devirtualize/inlining/OSR hay không **phụ thuộc runtime**, không đoán từ số lượng vòng lặp đơn thuần.

## 13. Kiểm tra bytecode bằng javap

Chạy:

```bash
javap -c -p DevirtualizationDemo
```

Tìm method `invoke(DevirtualizationDemo.Operation, int)` (cách in tên đầy đủ phụ thuộc `javap`). Bạn sẽ thấy lời gọi `Operation.apply` thể hiện bằng **`invokeinterface`**.

Điều này xác nhận **bytecode có dynamic-dispatch semantics**. Nó **không** bác bỏ việc C1/C2 tạo machine code trực tiếp/inline. `javap` đọc classfile; nó **không** đọc code cache của JVM đã chạy một method hàng nghìn lần.

### So sánh opcode

Trên một Java class khác nếu có:

```java
static int twice(int x) {
    return x * 2;
}

static int callStatic() {
    return twice(10);
}
```

Đây là snippet method minh họa trong cùng class. `callStatic` thường dùng `invokestatic` vì method `twice` là static, không có receiver polymorphism. **Không gọi đó là thành quả devirtualization**, vì dynamic dispatch chưa từng là yêu cầu tại call site này.

## 14. Quan sát quyết định inline của HotSpot

Với HotSpot JDK 17 hỗ trợ diagnostic flags:

```bash
java -XX:+PrintCompilation DevirtualizationDemo
```

Để xem thêm quyết định inlining:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining DevirtualizationDemo
```

Output có thể **rất dài**: JVM compile cả các methods của JDK libraries, logging xen với `System.out.println`. Các dạng thông tin bạn có thể tìm:

```text
... DevirtualizationDemo::invoke ...
... DevirtualizationDemo$PlusTwo::apply ... inline (hot)
... TypeProfile (... receiver klass ...)
... virtual call ...
... made not entrant ...
```

**Đây là những mẫu cách diễn đạt, không phải stdout cố định và không phải tất cả lines bắt buộc xuất hiện.** Một run chỉ dùng interpreter/C1 hoặc phương án optimization khác có thể không có các dòng tương ứng.

### Giải thích các dấu hiệu thường gặp

| Log fragment | Gợi ý, không là định luật |
| --- | --- |
| `inline (hot)` | JIT đã quyết định inline một target trong compilation context được in |
| `TypeProfile` / receiver class info | Compiler đang dùng/hiển thị thông tin runtime type tại một vị trí |
| `virtual call` | Call site có thể chưa được inline hoặc đường dispatch còn tổng quát |
| `made not entrant` | Một nmethod không còn nhận entry mới theo trạng thái VM |
| `made zombie` | Một trạng thái nội bộ trong lifecycle compiled code |
| Compilation level 3/4 | Tiered JIT level phù hợp nếu format log có level |
| `%` trong `PrintCompilation` | Thường liên quan OSR compilation |

**Cẩn trọng:** `PrintInlining` ghi quyết định **ở thời điểm compile một version**, không là lịch sử toàn bộ những receiver types đã từng gọi trong mọi JVM state. Một method có thể được compile lại nhiều lần.

### Vì sao output thay đổi?

- Tiered policy chọn compiler level khác.
- JVM version/hardware/compiler flags khác.
- C1/C2 compilation queue đang bận.
- Code cache đầy hoặc compiler policy hạn chế.
- Workload chưa đủ hot hoặc bị tối ưu gộp caller/callee.
- Class loading, receiver profile và timing khác.
- Startup classes/JDK library methods được compile trước, chen vào log.

**Không dùng “log phải chứa inline (hot)” làm unit test bắt buộc cho source code**. Muốn benchmark hãy đo kết quả/throughput/latency bằng công cụ phù hợp.

## 15. Chạy các chế độ để phân biệt bytecode và JIT

Các lệnh sau thử trên **những JVM processes riêng**:

```bash
java -Xint DevirtualizationDemo
java -XX:TieredStopAtLevel=1 DevirtualizationDemo
java -XX:+PrintCompilation DevirtualizationDemo
java -XX:-TieredCompilation -XX:+PrintCompilation DevirtualizationDemo
```

- `-Xint` chọn chế độ chủ yếu **interpreter-only cho Java methods**, hữu ích làm baseline correctness; thường chậm với workload tính toán.
- `TieredStopAtLevel=1` giới hạn compilation level trong HotSpot tiered policy, không bắt mọi cold method phải compile.
- `PrintCompilation` giúp quan sát compilation events.
- `-XX:-TieredCompilation` thay **JIT compilation policy**, **không** đồng nghĩa vô hiệu JIT như `-Xint`.

**Cả bốn lần chạy**, nếu JVM hỗ trợ flags và chương trình hoàn tất bình thường, output tính toán **phải giống** mục 12. Số JIT events, compiler decisions và tốc độ thì khác.

### Tắt inlining không chứng minh mọi devirtualization đều tắt

HotSpot có `-XX:-Inline` để điều chỉnh inlining trên những builds hỗ trợ. Nhưng **devirtualization và inlining là hai việc khác nhau**. Tắt inline không nhất thiết buộc mọi call site trở về generic interface dispatch: VM vẫn có thể biết target và gọi trực tiếp.

Đừng dùng `-XX:-Inline` trong production như một “cách sửa lỗi đa hình” nếu chưa có bằng chứng profiler và benchmark. Flag can thiệp rộng vào tối ưu JIT.

## 16. JIT compilation log chi tiết và machine code diagnostics

Nếu cần chứng cứ sâu hơn (và được phép tạo log file trong môi trường thí nghiệm):

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -XX:LogFile=jit-devirt.xml DevirtualizationDemo
```

HotSpot có thể ghi log JIT ở định dạng XML phục vụ phân tích, không phải stdout gọn như `PrintCompilation`. Mức chi tiết, tên events và công cụ đọc phụ thuộc JDK version. Một số events liên quan `uncommon_trap`, inlining, type profile hoặc dependency; **không khẳng định mọi event đã log là một deoptimization runtime thật** khi chưa phân biệt parse/compile/runtime actions.

### In assembly

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly DevirtualizationDemo
```

Phụ thuộc **hsdis** đúng JDK/OS/architecture và diagnostic setup. Nếu không có disassembler, có thể không thấy machine instructions dù JIT đã compile bình thường. Assembly còn có thể rất dài; nên dùng trên isolated test process hoặc công cụ phân tích thích hợp.

### jcmd đối với tiến trình đang chạy

```bash
jcmd -l
jcmd PID help
jcmd PID Compiler.queue
jcmd PID Compiler.codelist
jcmd PID Compiler.codecache
```

Thay `PID` bằng process ID thật, không gõ nguyên placeholder. Chương trình demo có thể kết thúc quá nhanh để attach; muốn xem live trạng thái JIT, dùng service hoặc test process tồn tại đủ lâu. `jcmd` chỉ có các commands mà binary hỗ trợ và đòi hỏi quyền attach thích hợp.

**Những lệnh này không trực tiếp nói “method X đã dùng CHA”**; chúng cung cấp bối cảnh compile/code cache, cần kết hợp logs/assembly khi muốn biết devirtualization thật.

## 17. Lời gọi qua interface trong code thực tế: không tự kết luận quá sớm

Các code bases dùng interface polymorphism rất nhiều:

- Service interfaces và dependency injection.
- Strategy/State/Command patterns.
- Plugin APIs với runtime-loaded implementations.
- Collections và iterators qua interfaces.
- Logger interfaces, adapters và generated proxies.
- ORM/session abstractions, proxy subclasses.
- Lambda/method references qua functional interfaces.

**Sai lầm phổ biến:** “Java interface luôn chậm vì virtual call”. HotSpot có type profile/CHA/inlining rất mạnh; một callsite interface có receiver type ổn định có thể được tối ưu gần direct/static code.

**Sai lầm ngược lại:** “JIT luôn tối ưu được mọi interface call”. Một site thật sự megamorphic, targets lớn, code cache pressure, latency warm-up hoặc class loading phức tạp có thể ngăn/thay đổi tối ưu.

### Framework proxy có thể làm callsite thay đổi kiểu

Một codebase có nhiều decorators/proxies khác runtime classes thực nhận. Ví dụ:

```text
Service s = JDK dynamic proxy
hoặc
Service s = concrete implementation
```

Đây là **mô hình**, không phải Java source đầy đủ. Tùy nơi caller nhận được reference, type profile có thể thấy concrete class, một proxy class hoặc nhiều classes. Điều đó ảnh hưởng khả năng inline từng đường dispatch. Nhưng **không tự suy rằng cứ có proxy là không thể inlining**.

### Tối ưu cấu trúc code nên dựa vào mục tiêu

- Ưu tiên **API clarity, testability và correct polymorphism**.
- Tránh tự thay mọi interface bằng `switch(instanceof)` chỉ để “giúp JIT”.
- Với hot path thật sự, profile trước, xác định type distribution và measured cost.
- Nếu một polymorphic site là bottleneck, cân nhắc giảm số target types, tách callsites theo đường nghiệp vụ hoặc làm dataflow exact type rõ hơn **khi vẫn giữ đúng thiết kế**.
- Benchmark end-to-end và dùng microbenchmarks có thiết kế đúng khi cần kiểm chứng cost riêng.

## 18. Compiler assumptions còn chịu những ràng buộc ngữ nghĩa khác

### Null receiver

```java
Payment p = null;
p.calculate(10);
```

Đây là đoạn trong method, sẽ ném `NullPointerException` khi thực thi lời gọi instance. Nếu JIT inline/khử dispatch, **nó vẫn phải bảo toàn kết quả exception phù hợp**, kể cả khi null check được implicit hoặc kết hợp với một kiểm tra khác.

### Side effects, exceptions và locks

Callee có thể:

- Ghi field `volatile`.
- Thực hiện `synchronized`.
- Ném checked/unchecked exceptions.
- Chạm class initialization/field access.
- Gọi native method.
- Thực hiện I/O, cập nhật dữ liệu chia sẻ.

Đích lời gọi có thể được inline hoặc gọi trực tiếp, nhưng Java Memory Model và observable behavior **không bị miễn trừ**. Compiler không được biến một interface call có side effect thành phép toán không tương đương.

### Class loader identity và visibility

Hai classes cùng binary name nhưng từ các defining class loaders khác nhau có thể là runtime types khác nhau. CHA/type profiles làm việc với **runtime class identity/metadata**, không so string class name rồi coi là cùng đích.

### Exceptions do linkage

`invokevirtual`/`invokeinterface` còn chịu các luật resolution/linking của JVM. JIT phải giữ ngữ nghĩa hợp lệ khi method resolution hoặc class loading dẫn tới các lỗi linkage theo JVM Specification. Không được “đoán” target rồi bỏ qua mọi kiểm tra cần thiết.

## 19. Devirtualization và escape analysis có quan hệ như thế nào?

**Devirtualization** biết target; **inlining** đưa callee vào caller; sau đó **escape analysis** và optimizations khác có thể thấy nhiều dữ kiện hơn.

```text
Virtual/interface call
    │
    ▼
JIT chứng minh hoặc dự đoán target (devirtualization)
    │
    ▼
Inline implementation nếu đáng giá
    │
    ▼
Có thể thấy allocation/field access xuyên method
    │
    ▼
Escape analysis / scalar replacement / constant folding
    │
    ▼
Ít calls / ít allocations / ít loads trên path thích hợp
```

Đây **không** có nghĩa “devirtualization tự động bỏ mọi object allocations”. Escape analysis phụ thuộc object có escape hay không, aliasing, safepoints, exceptions, intrinsics, size/budget và policy của JIT. Một allocation trong source có thể vẫn tồn tại trên heap.

**Lợi ích thực tiễn:** đôi khi tiết kiệm dispatch call chỉ là phần nhỏ; giá trị lớn hơn là mở đường tối ưu toàn bộ chuỗi tính toán qua nhiều method.

## 20. Hiệu năng: đừng suy trực tiếp từ số lượng virtual calls

Một site có 1 triệu virtual calls chưa chắc là bottleneck. CPU cost phụ thuộc:

- Target method có lớn hơn overhead dispatch hàng trăm lần không?
- Branch predictor có dự đoán được patterns của receiver types không?
- Call site được JIT inline trên đường nóng chưa?
- Instruction cache footprint sau inlining.
- Code cache occupancy và compilation overhead.
- Tỷ lệ khác nhau của receiver types trong production.
- GC/allocation pressure và cache locality.
- JVM warm-up/OSR/deoptimization frequency.
- CPU architecture, frequency scaling, container throttling.

### Benchmark đáng tin hơn “inlining folklore”

Với microbenchmark, dùng **JMH** hoặc harness tương đương để kiểm soát warm-up, forks, kết quả observable (tránh dead-code elimination), inputs và JVM flags. Với service, đo **throughput, p50/p95/p99 latency và CPU profiles** trong điều kiện thực.

Một trường hợp JIT devirtualization giúp rất nhiều ở một microbenchmark có thể ít ảnh hưởng end-to-end latency nếu phần lớn thời gian là network I/O/database. Đừng đổi cả public API thành concrete classes chỉ vì một lần nhìn `invokeinterface` trong `javap`.

### Warm-up có thể thay đổi receiver distribution

Nếu trong warm-up bạn chỉ dùng `MockPayment` còn production dùng `StandardPayment`, compiler có thể tối ưu theo profile không đại diện. Khi production đổi distribution, có thể thấy guard failures/deopt/recompile và tốc độ đầu giờ tải khác kỳ vọng.

Warm-up hợp lý nên phản ánh **những shapes/receiver types quan trọng của production**, nhưng vẫn cần xác nhận bằng metrics thay vì hard-code số lần gọi cố định.

## 21. Những nhầm lẫn thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`invokeinterface` lúc nào cũng chậm hơn `invokestatic`.” | JIT có thể devirtualize/inline interface call khi đủ dữ kiện; performance tùy path |
| “Devirtualization và inlining là cùng một việc.” | Một việc xác định callee, một việc đưa code vào caller |
| “Có interface nghĩa là không thể inline.” | Runtime profile có thể monomorphic |
| “Một interface có 20 implementations nên mọi site megamorphic.” | Polymorphism được đánh giá **theo call site** |
| “Monomorphic nghĩa là interface có đúng một implementation trong source.” | Một site có thể chỉ **quan sát** một type dù hierarchy lớn |
| “CHA thấy một target thì giả định luôn đúng mãi.” | Dynamic class loading có thể làm dependency invalid, cần deopt/invalidation |
| “Class `final` làm code luôn nhanh hơn.” | Giúp xác định target nhưng compiler không bảo đảm inline/perf thắng |
| “`sealed` bảo đảm chỉ một runtime target.” | Có thể cho phép nhiều concrete subclasses |
| “Guard fail làm JIT trả sai method.” | JVM phải dispatch/fallback/deopt đúng theo semantics |
| “Deopt là crash hoặc exception.” | Đây là cơ chế runtime chuyển về execution tổng quát |
| “`javap -c` chứng minh JIT không khử dispatch.” | `javap` chỉ hiển thị bytecode class file |
| “`PrintInlining` không có output nghĩa là JIT không hoạt động.” | Có thể logs/flags/workload/version chưa phù hợp |
| “C2 luôn devirtualize mọi hot call.” | Profile, budget, method size, code cache và policy là giới hạn |
| “Dynamic proxy ngăn JIT tối ưu mọi nơi.” | Cần quan sát receiver profile cụ thể |
| “Tắt inlining đồng nghĩa tắt devirtualization.” | Hai optimization không đồng nhất |
| “Mỗi lần JVM nhìn thấy type mới chắc chắn deopt.” | Có thể có fallback/guarded path không cần deopt |
| “Callee có side effects vẫn có thể loại bỏ tùy ý sau inline.” | Observable Java semantics phải giữ đúng |
| “Đọc Class.getName() là đủ biết class identity.” | Class loaders khác nhau có thể tạo runtime types khác |
| “JIT quyết định chỉ dựa trên source-code keyword.” | Runtime types/profiles/hierarchy/flags/CPU đều ảnh hưởng |
| “Benchmark một lần sau khi chạy là đã đo steady state.” | Warm-up và recompilation tạo nhiều pha hiệu năng khác nhau |

## 22. Bài tập có lời giải

### Bài 1 — Dispatch theo runtime receiver

Có `Payment p = new StandardPayment();` và `p.calculate(10)` theo định nghĩa mục 1.  
**Đáp án:** **12**. Variable type `Payment` không quyết định một method implementation riêng ngoài runtime receiver type.

### Bài 2 — Đổi receiver

Nếu gán `p = new PremiumPayment()` rồi gọi `p.calculate(10)`?  
**Đáp án:** **20**. JVM phải bảo toàn dynamic dispatch; JIT không được ép gọi method của StandardPayment nếu runtime type khác.

### Bài 3 — Devirtualization khác inlining

JIT biết target `StandardPayment.calculate` nhưng không inline vì method quá lớn. Có được gọi là devirtualization?  
**Đáp án:** Có thể. Nếu dispatch động được khử thành direct call thì đã devirtualize, dù còn call boundary.

### Bài 4 — Interface lớn nhưng site chỉ có một type

Interface có 12 concrete implementations. Một hot call site chỉ nhận một implementation. Site đó có phải tự động megamorphic?  
**Đáp án:** Không. Type profiling ở call site có thể monomorphic.

### Bài 5 — Hai site có cùng signature

Một site chỉ nhận `StandardPayment`; site khác nhận cả Standard và Premium. JVM có bắt buộc tối ưu giống nhau?  
**Đáp án:** Không. Callsite profiles và optimization context khác nhau.

### Bài 6 — Class final

Vì sao class `final` giúp JIT suy ra target?  
**Đáp án:** Nó không thể có subclass, giảm khả năng method bị override; nhưng không bảo đảm inline.

### Bài 7 — Sealed hierarchy

`sealed interface` có hai permitted final implementations. Chắc chắn một target cho mọi interface call?  
**Đáp án:** Không. Site có thể nhận cả hai implementations.

### Bài 8 — CHA và class loading

CHA đã tối ưu theo một concrete target rồi một subclass mới được nạp. Có được giữ code cũ không kiểm tra?  
**Đáp án:** Không nếu compiled assumption phụ thuộc hierarchy không còn hợp lệ. HotSpot phải quản lý dependency/invalidation/deoptimization hoặc phương án bảo đảm semantics khác.

### Bài 9 — Type profiling có rủi ro gì?

Warm-up toàn type A, traffic thực chuyển type B.  
**Đáp án:** Guard miss, fallback hoặc deopt/recompile có thể xảy ra; profile không phải lời hứa các future inputs chỉ có A.

### Bài 10 — Mục đích guard

Nếu JIT chỉ inline A path, tại sao cần class/type guard trong speculative case?  
**Đáp án:** Để đường thực thi nhận B không chạy nhầm method A, bảo toàn dynamic dispatch.

### Bài 11 — Null

Nếu receiver `null` và JIT inline target, có được trả số mặc định thay vì lỗi?  
**Đáp án:** Không. Phải bảo toàn `NullPointerException` theo luật JVM.

### Bài 12 — Opcode

`javap -c` thấy `invokeinterface`. Có chắc CPU phải thực hiện interface lookup mỗi call?  
**Đáp án:** Không. HotSpot JIT có thể devirtualize/inline machine code.

### Bài 13 — Static call

`invokestatic` được gọi là “khử lời gọi ảo” sau JIT có hợp lý không?  
**Đáp án:** Không theo nghĩa đang bàn; static call không yêu cầu receiver virtual dispatch.

### Bài 14 — Mã máy không còn được dùng

Nếu code bị `made not entrant`, có chứng minh JVM sai?  
**Đáp án:** Không. Đó có thể là phần bình thường của nmethod lifecycle, deoptimization/recompilation.

### Bài 15 — Cùng một method nhiều compiled versions

Một method có thể có phiên bản JIT khác nhau qua thời gian không?  
**Đáp án:** Có. C1/C2 levels, type profiles, OSR, deopt và recompilation ảnh hưởng mã đã compile.

### Bài 16 — Dữ liệu `DevirtualizationDemo`

Giá trị `first phase` trong demo mục 12?  
**Đáp án:** **650000**, do mỗi chu kỳ 0..9 cộng 2 là tổng 65, có 10.000 chu kỳ.

### Bài 17 — Phase thứ hai

Giá trị `second phase`?  
**Đáp án:** **900000**, do hai lần tổng 0..9 = 90 cho một chu kỳ, lặp 10.000 lần.

### Bài 18 — Checksum

Giá trị cuối của `sink`?  
**Đáp án:** **1550041 = 650000 + 900000 + 12 + 20 + 9**.

### Bài 19 — Không thấy log inline

JVM không in `inline (hot)` trong một lần chạy demo. Có chứng minh HotSpot không hỗ trợ devirtualization?  
**Đáp án:** Không. JIT level, hotness, compiler flags, logging và runtime policy có thể khác.

### Bài 20 — Tắt `-XX:-Inline`

Điều này có bảo đảm mọi callsite giữ generic virtual dispatch?  
**Đáp án:** Không. Tắt inline khác với tắt devirtualization; VM có thể direct call nhưng không inline.

### Bài 21 — Reflection và class loaders

Hai class cùng tên nhưng được định nghĩa bởi hai different class loaders có thể được coi là cùng runtime klass vì name giống?  
**Đáp án:** Không. Runtime class identity gắn với defining loader, không chỉ binary name.

### Bài 22 — Tối ưu API

Có nên sửa toàn bộ interface thành concrete class chỉ vì benchmark nhỏ thấy virtual call overhead?  
**Đáp án:** Không mặc định. Đo workload, xác định bottleneck, kiểm tra compiler logs và đánh giá chi phí về thiết kế trước khi đổi API.

## 23. Checklist điều tra một hot virtual call

- [ ] Xác định đúng **bytecode call site** bằng `javap -c` và JIT context, không chỉ method name.
- [ ] Phân biệt opcode `invokevirtual`/`invokeinterface`/`invokestatic`/`invokespecial`.
- [ ] Biết receiver **runtime types** nào thật sự xuất hiện tại site?
- [ ] Site monomorphic, vài types hay megamorphic trên workload thực?
- [ ] Class hierarchy hiện tại và khả năng dynamic class loading ra sao?
- [ ] Method/class có `final`, exact type hoặc sealed constraints nào?
- [ ] Biết optimized assumption dựa trên CHA hay type profile (nếu logs đủ)?
- [ ] JIT có tạo guards, direct call, inline hoặc generic dispatch không?
- [ ] Có thể xảy ra class loading invalidation/deoptimization không?
- [ ] Đã kiểm tra code/branch/exception semantics sau optimization?
- [ ] Có tách devirtualization với method inlining và escape analysis?
- [ ] Có đọc hiệu lực của `TieredCompilation` và JIT flags?
- [ ] Có quan sát `PrintCompilation`, `PrintInlining` trong môi trường test?
- [ ] Có xem code cache/compile queue nếu C2 compilation chậm?
- [ ] Đã so performance sau warm-up lẫn ngay lúc startup?
- [ ] Benchmark có đủ warm-up, forks và không bị dead-code elimination?
- [ ] Chẩn đoán có xét CPU, GC, allocations, I/O thay vì chỉ virtual call?
- [ ] Không biến JIT implementation details thành Java API correctness conditions?

## 24. Tóm tắt

**Devirtualization** tận dụng thông tin về **runtime receiver type** để tránh cost của generic dynamic dispatch ở một hot path. **CHA** dùng class hierarchy và compiled dependencies; **type profiling** dùng những concrete types xuất hiện tại từng call site. **Exact type/final semantics** cho phép những proof mạnh hơn; **speculative assumptions** cần guarded paths, fallback hoặc deoptimization khi inputs/class hierarchy thay đổi.

Khi devirtualization tạo target cụ thể, HotSpot có thể **inline** và tối ưu xuyên method; nhưng **khử dispatch ≠ inline**, và không có compiler nào phải tối ưu mọi site trong mọi tình huống. Khả năng nạp class động, polymorphism, receiver null, exception ordering và Java Memory Model đặt ranh giới mà optimized machine code **không được vượt qua**.

### Tài liệu tham khảo

- **Java Virtual Machine Specification (Java SE 17), §2.11.8 Method Invocation and Return Instructions:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.8
- **Java Virtual Machine Specification (Java SE 17), Chapter 6 Instruction Set:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
- **Oracle JDK 17 — `java` command, `PrintCompilation` / `PrintInlining` / compiler controls:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **OpenJDK HotSpot — Optimizations in HotSpot JVM** (class hierarchy analysis, devirtualization, type speculation): https://cr.openjdk.org/~vlivanov/talks/2015_JIT_Overview.pdf
- **OpenJDK HotSpot C2 — `doCall.cpp`** (guarded call, CHA target, type profile, virtual/direct call decisions; source varies by release): https://github.com/openjdk/jdk/blob/jdk-17%2B35/src/hotspot/share/opto/doCall.cpp
- **OpenJDK — Integrating Profile Caching into the HotSpot Multi-Tier Compilation System** (runtime profiling, deoptimization and class hierarchy changes): https://cr.openjdk.org/~thartmann/papers/2017-ManLang-Profile_Caching.pdf
- **Java SE 17 — JLS `8.4.3.3 Final Methods và `8.1.1.2 Final Classes:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html
- **OpenJDK JEP 197 — Segmented Code Cache:** https://openjdk.org/jeps/197/

**Thông điệp cuối:** không nên sợ interface vì một opcode `invokeinterface`, cũng không nên tin rằng C2 sẽ luôn biến mọi lời gọi ảo thành direct inline. **Hãy đo profile của chính call site, kiểm tra compiler decisions trên đúng HotSpot/JDK và luôn giữ ranh giới giữa giả định tối ưu với Java semantics bắt buộc.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-003">Gấp hằng, loại mã không cần thiết và tác động đến phép đo</a>

<details>
<summary>Xem chi tiết</summary>

# Gấp hằng, loại mã không cần thiết và tác động đến phép đo trong HotSpot JVM

## Mục tiêu

Khi lập trình Java, hai phiên bản mã có thể trông khác nhau nhưng sau tối ưu lại dẫn tới **cùng một chuỗi lệnh máy**. Ngược lại, một phép tính xuất hiện trong source và thậm chí trong bytecode có thể **không còn tồn tại** trong machine code sau JIT. Đây là lý do một microbenchmark ngây thơ có thể báo rằng một phép tính tốn gần như **0 ns**, hoặc thuật toán A nhanh hơn thuật toán B hàng trăm lần chỉ vì compiler nhận thấy kết quả của A không cần thiết.

Hai cơ chế trung tâm của bài học:

- **Constant folding (gấp hằng):** thay biểu thức mà kết quả đã biết bằng một giá trị cố định, khi phép biến đổi bảo toàn ngữ nghĩa Java. Ví dụ `6 * 7` có thể thành `42` ngay khi biên dịch source, hoặc một biểu thức với đầu vào thực tế được JIT biết có thể được tính trước trong optimized code.
- **Dead-code elimination (DCE, loại mã chết):** bỏ những phép tính, lệnh gán, nhánh, thậm chí object allocations mà không ảnh hưởng đến **hành vi chương trình được phép quan sát**. Nếu kết quả không được dùng và không có side effects/exception/ordering cần bảo toàn, JIT có thể loại bỏ phần công việc bạn tưởng đang đo.

Sau bài này bạn có thể:

1. Phân biệt **javac compile-time constant expressions** theo JLS với **runtime JIT constant propagation/folding** trong HotSpot.
2. Hiểu **dead assignments**, **unreachable branches**, **unused results**, **redundant checks** và sự tương tác với inlining, common-subexpression elimination, loop invariant code motion và escape analysis.
3. Nhận biết giới hạn do **exceptions, volatile accesses, synchronization, I/O, floating-point behavior, integer overflow, aliasing và Java Memory Model**.
4. Dùng `javap -c -p` để phân biệt mã bị `javac` xử lý từ trước và mã còn bytecode nhưng có thể được JIT tối ưu sau.
5. Tạo ví dụ Java 17+ chạy được có stdout xác định và mô tả điều gì **không thể** kết luận chỉ từ output.
6. Thiết kế benchmark với **JMH**, sử dụng `@State`, `@Param`, giá trị trả về hoặc `Blackhole` để hạn chế DCE/constant folding ngoài ý muốn.
7. Tránh những cách “chống tối ưu” làm thay đổi phép đo, chẳng hạn thêm `System.out.println` vào timed loop hoặc dùng volatile một cách máy móc.
8. Diễn giải kết quả benchmark theo **workload thật**, warmup, JIT tiering, dead-code elimination, allocation rate và các công cụ chẩn đoán.

## 1. Các tầng biến đổi từ Java source đến CPU

~~~text
Java source
    |
    | javac
    v
.class bytecode
    |
    | HotSpot interpreter / profiling
    v
JIT intermediate representation (C1/C2 theo build/chế độ)
    |
    +-- inlining
    +-- constant folding / constant propagation
    +-- branch simplification
    +-- dead-code elimination
    +-- loop optimizations
    +-- escape analysis / scalar replacement
    +-- machine-specific lowering/scheduling
    |
    v
Optimized machine code
    |
    v
Actual program behavior (phải phù hợp Java semantics)
~~~

Các giai đoạn không bắt buộc diễn ra một lần và theo đúng thứ tự giản lược ở trên. HotSpot có nhiều compiler passes, phase ordering và recompilation; một tối ưu có thể tạo điều kiện cho tối ưu khác. C1/C2 và các representation nội bộ không thuộc Java SE public API.

### Hai loại gấp hằng dễ nhầm

**Gấp hằng trong `javac`:**

~~~java
static final int ANSWER = 6 * 7;
static int getAnswer() {
    return ANSWER;
}
~~~

Đây là đoạn class-member minh họa, phải đặt trong một class. `ANSWER` là một **constant variable**: `final`, kiểu primitive, initialized bằng constant expression. `javac` có thể đặt giá trị 42 ngay trong code của method sử dụng nó. Thậm chí `getAnswer()` có thể có bytecode dạng tải hằng và `ireturn` mà không cần `getstatic ANSWER`.

**Gấp hằng ở HotSpot JIT:**

~~~java
static int multiply(int x) {
    return x * 7;
}

static int callWithKnownValue() {
    return multiply(6);
}
~~~

Đây là các method minh họa. Bytecode của caller vẫn có thể chứa `invokestatic multiply` và bytecode callee có phép nhân. Nhưng sau khi JIT inline `multiply(6)`, compiler có thể biết argument là 6, gấp phép nhân thành **42**. Java source không cần đánh dấu `x` là `final` để một JIT optimization nhận ra constant ở một call site cụ thể.

**Điểm mấu chốt:** xem `javap` thấy phép nhân không đồng nghĩa CPU bắt buộc chạy phép nhân; ngược lại `javap` chỉ là class-file bytecode, không phải machine code cuối cùng.

## 2. Constant expression theo JLS khác với mọi biểu thức mà JIT có thể tối ưu

JLS Java SE 17 §15.29 định nghĩa một tập biểu thức hằng của **primitive** hoặc **String**, sử dụng literals, phép toán được phép, casts và references đến constant variables. Điều này quyết định nhiều quy tắc ngôn ngữ, ví dụ compile-time constant variables, switch case labels, narrowing constant conversions và class initialization.

Ví dụ:

~~~java
static final int A = 40 + 2;
static final String LABEL = "J" + "VM";
static final int B = Integer.parseInt("42");
static final Integer C = 42;
static int D = 21 * 2;
~~~

Đây là **class fields minh họa**, không độc lập:
- `A` là constant variable primitive.
- `LABEL` là constant variable String.
- `B` là `final int` nhưng **không phải constant variable theo JLS**, vì initializer gọi method `Integer.parseInt` chứ không phải constant expression.
- `C` không phải constant variable theo JLS: kiểu `Integer` không phải primitive hay String.
- `D` không phải constant variable vì thiếu `final`, mặc dù initializer có dạng tính được tại compile time.
- **JIT vẫn có thể suy luận một số giá trị** từ B, C hoặc D nếu các điều kiện runtime đủ chặt, nhưng đó là **suy luận/tối ưu runtime**, không biến các fields này thành constant variables theo ngữ nghĩa JLS.

### Java constant variables có tác động tới binary compatibility

Nếu thư viện công khai:

~~~java
public static final int TIMEOUT = 30;
~~~

và một client được biên dịch dựa trên constant variable đó, mã client có thể chứa trực tiếp hằng **30** thay vì field read runtime. Sau này đổi thư viện sang `TIMEOUT=60` nhưng **không biên dịch lại client**, client có thể vẫn sử dụng 30. Đây là **constant inlining qua compile boundary**, không phải lỗi của JIT.

Nếu giá trị cần đổi mà clients luôn đọc lúc runtime, một API method/getter hoặc một field không thuộc constant-variable category có thể phù hợp hơn. Cũng cần xét initialization, access semantics và binary compatibility của API. Đừng dùng `static final` primitive constant làm “dynamic configuration”.

### Compile-time unreachable code khác JIT DCE

Một đoạn source có thể bị Java compiler từ chối do quy tắc **unreachable statements** theo JLS. Đó là **lỗi compile-time**. Trong khi đó, HotSpot DCE chủ yếu loại các nodes/instructions khỏi machine code sau khi source và bytecode đã hợp lệ; Java source vẫn biên dịch bình thường.

Không suy ra `javac` sẽ làm mọi tối ưu mà JIT làm, và không cho rằng JVM có quyền thay đổi ngữ nghĩa observable vì performance.

## 3. Ví dụ đầy đủ Java 17+: phân biệt folding và dead expression

Tạo file **`FoldingAndDceDemo.java`**:

~~~java
public class FoldingAndDceDemo {
    private static final int ANSWER = 6 * 7;

    private static final int RUNTIME_VALUE =
            Integer.parseInt("42");

    private static volatile int observed;

    static int compileTimeConstant() {
        return ANSWER;
    }

    static int runtimeField() {
        return RUNTIME_VALUE;
    }

    static int unusedArithmetic(int x) {
        int temporary = (x + 2) * (x + 2);
        return x + 1;
    }

    static int mayThrow(int divisor) {
        int unused = 100 / divisor;
        return 7;
    }

    static int visibleWrite(int x) {
        observed = x * 2;
        return 1;
    }

    public static void main(String[] args) {
        System.out.println("constant=" + compileTimeConstant());
        System.out.println("runtime=" + runtimeField());
        System.out.println("unused=" + unusedArithmetic(7));

        try {
            mayThrow(0);
            System.out.println("unexpected");
        } catch (ArithmeticException ex) {
            System.out.println("division=ArithmeticException");
        }

        visibleWrite(5);
        System.out.println("volatile=" + observed);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 FoldingAndDceDemo.java
java FoldingAndDceDemo
~~~

**Stdout chính xác:**

~~~text
constant=42
runtime=42
unused=8
division=ArithmeticException
volatile=10
~~~

Giải thích:

1. `ANSWER` thuộc compile-time constant-variable category. `javac` có thể đặt `42` vào bytecode của `compileTimeConstant`.
2. `RUNTIME_VALUE` được khởi tạo bằng method call, do đó không phải compile-time constant variable. `runtimeField` thông thường đọc field với `getstatic` trong bytecode, dù HotSpot runtime có thể tối ưu thêm khi phù hợp.
3. `unusedArithmetic(7)` khai báo một local `temporary` nhưng **không dùng nó** để tạo return value. HotSpot có thể loại toàn bộ việc tính `(x+2)*(x+2)` vì phép toán int ở đây không tạo side effects/exception observable.
4. `mayThrow(0)` có phép chia nguyên cho 0. Dù biến `unused` không được đọc, biểu thức vẫn **phải ném ArithmeticException**. JIT không được xóa một operation nếu việc xóa làm biến mất exception observable trong cùng ngữ cảnh.
5. `visibleWrite(5)` ghi vào một **volatile field** rồi main đọc lại, nên kết quả 10 phải được bảo toàn. Không được suy ra việc gán field volatile đã biến thành “dead store” vô hại.

### So sánh bytecode

~~~bash
javap -c -p FoldingAndDceDemo
~~~

Nhìn vào `compileTimeConstant`, thường gặp instruction đẩy trực tiếp 42 rồi `ireturn`. Trong `runtimeField`, thường gặp `getstatic RUNTIME_VALUE`. Trong `unusedArithmetic`, bytecode của `javac` có thể vẫn chứa phép nhân và `istore` local tạm. Trong `mayThrow`, thường gặp `idiv` — phép chia có thể ném exception.

**Không coi vị trí bytecode/constant-pool indexes là hằng số.** Compiler build khác nhau có thể cho output khác. `javap` **không đủ** để chứng minh C2 đã xóa phép tính khỏi machine code; nó chỉ cho thấy bước sau `javac`, trước tối ưu JIT.

## 4. Dead-code elimination: “không dùng kết quả” nghĩa gì?

Một phép tính có thể bị xóa khi **giá trị tạo ra không góp phần vào behavior observable**, và việc tính nó không mang side effects/exception/ordering nào buộc phải giữ.

~~~java
static void example(int x) {
    int a = x * 31;
    int b = a + 7;
    // Không ai dùng a hay b.
}
~~~

Đây là **method minh họa**. Một tối ưu phù hợp có thể loại các assignments và arithmetic instructions. Nếu benchmark gọi `example(123)` nhiều lần rồi đo thời gian, rất có thể phép “toán học” mong muốn đã không còn tồn tại ở mức machine code.

### Side effects và control-flow phải được bảo toàn

Các hoạt động thường cần xét:
- Ghi/đọc `volatile` theo Java Memory Model.
- Khóa monitor bằng `synchronized` và synchronization order.
- Thao tác I/O có tác động ra bên ngoài, ví dụ in console hoặc ghi file.
- Ghi bộ nhớ có thể được truy cập từ đường code khác hoặc threads khác theo semantics.
- Exceptions có thể quan sát, như `ArithmeticException`, `NullPointerException`, `ArrayIndexOutOfBoundsException`, một số errors tại những điểm phù hợp.
- Native calls hoặc JNI code có side effects.
- Object identity, locking, references thoát khỏi method, finalization/reference-processing constraints khi thích hợp.

**Không phải** tất cả những operation này đều buộc phải tồn tại nguyên vẹn từng machine instruction. JIT chỉ cần giữ **Java-level observable behavior**, có thể kết hợp/bỏ thao tác dư thừa nếu chứng minh hợp lệ.

### DCE khác compile-time dead branch pruning

~~~java
static int choose(int x) {
    if (x > 0) {
        return 1;
    }
    return 2;
}
~~~

Đây là method hợp lệ. Nếu JIT thấy caller luôn truyền x=5 và inline, nó có thể kết luận `x>0` luôn true tại **call site đã được specialized** và bỏ nhánh còn lại. Nhưng ở một caller tổng quát truyền x âm, compiler không được biến toàn method thành return 1 trừ khi guard/deoptimization bảo đảm semantics đúng.

**Constant propagation có thể mở đường cho DCE:** khi điều kiện được chứng minh true, nhánh false thành dead; từ đó các phép tính chỉ thuộc nhánh false cũng được loại.

## 5. Constant folding là phép thay tương đương, không phải “giản lược bất chấp ngữ nghĩa”

Ví dụ với số nguyên:

~~~java
static int zeroProduct(int x) {
    return x * 0 + 9;
}
~~~

Với `int`, phép nhân 0 luôn cho 0 theo quy tắc Java, kể cả khi x âm hoặc phép nhân khác có thể overflow. Vì vậy kết quả method này luôn bằng 9; compiler có thể gấp thành constant nếu không có side effect trong phần đọc x.

Nhưng với `double`:

~~~java
static double floatingZero(double x) {
    return x * 0.0d;
}
~~~

**Không được** biến thành literal `0.0d` với mọi x:
- `Double.POSITIVE_INFINITY * 0.0` là **NaN**.
- `-3.0d * +0.0d` là **-0.0d**.
- NaN propagation và signed zero phải được tôn trọng.
- Floating-point strict semantics của Java 17+ không cho phép những biến đổi làm mất kết quả observable này chỉ vì “nhân 0 thì bằng 0”.

### Ví dụ chạy được: số nguyên và dấu zero IEEE 754

Tạo file **`SemanticBoundaryDemo.java`**:

~~~java
public class SemanticBoundaryDemo {
    static int zeroInt(int x) {
        return x * 0 + 9;
    }

    static double zeroDouble(double x) {
        return x * 0.0d;
    }

    public static void main(String[] args) {
        System.out.println("int=" + zeroInt(Integer.MIN_VALUE));

        double infinityResult =
                zeroDouble(Double.POSITIVE_INFINITY);
        System.out.println("infinityNaN=" +
                Double.isNaN(infinityResult));

        double negativeZero = zeroDouble(-3.0d);
        System.out.println("negativeZeroBits=" +
                Long.toHexString(
                        Double.doubleToRawLongBits(negativeZero)));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 SemanticBoundaryDemo.java
java SemanticBoundaryDemo
~~~

**Stdout chính xác:**

~~~text
int=9
infinityNaN=true
negativeZeroBits=8000000000000000
~~~

Giá trị hex `8000000000000000` là bit pattern của **-0.0d** trong IEEE 754 double. Đây là lý do không dùng quy tắc đại số của số nguyên một cách máy móc cho phép toán floating point.

### Integer overflow cũng là một ranh giới

Java `int` arithmetic overflow theo quy tắc two's-complement wraparound đối với các phép toán thông thường; không được giả định kiểu số nguyên vô hạn. Phép biến đổi `(a+b)-b → a` có thể vẫn đúng theo modulo 2^32 cho int nhưng những đổi chỗ hay biến đổi với phép chia, shifts, compare, casts có thể khác. Compiler phải chứng minh đúng dưới Java rules, không dựa vào đại số thực rồi bỏ qua overflow.

## 6. Constant propagation và inlining làm benchmark “biến mất” thế nào?

Một phép tính có đầu vào không hằng tại source method vẫn có thể được JIT thấy hằng **sau khi inline**.

~~~java
static int expensive(int x) {
    int y = x;
    for (int i = 0; i < 10; i++) {
        y = y * 31 + i;
    }
    return y;
}

static int benchmarkLike() {
    return expensive(7);
}
~~~

Đây là **hai method minh họa**. Tại `benchmarkLike`, JIT có thể inline `expensive(7)`, biết trước x và loop bounds rồi fold/unroll/constant-propagate đủ để thu một kết quả cố định. Một phép đo rất nhanh lúc này phản ánh optimized **constant call site**, không phản ánh hiệu năng trên inputs production thay đổi.

Nếu đo `expensive(7)` một cách cố ý để đánh giá **chính caller có input hằng**, tối ưu là hợp lý. Nếu mục tiêu là đo phép tính với inputs không biết trước, benchmark sai về mô hình workload.

### Cách bảo vệ fairness khi input phải biến đổi

- Cung cấp input qua **`@State` fields** trong JMH để không chỉ truyền literal cố định.
- Dùng `@Param` để chạy nhiều tình huống đại diện.
- Nếu cần input distribution rộng hoặc thay đổi theo operation, quản lý dataset và indexing có kiểm soát.
- Không dùng `final int x=7` trong benchmark body nếu mục tiêu là đo dynamic-input path.
- **Không lạm dụng Random.nextInt() trong vùng đo** nếu mục tiêu chỉ là phép toán; chi phí RNG có thể áp đảo phép tính cần đo.
- Không cố “ẩn” dữ liệu bằng reflection/volatile/synchronized nếu production không chịu chi phí tương tự.

**Bài học:** JIT có nhiệm vụ tối ưu code thực thi, không phục vụ kỳ vọng rằng microbenchmark phải chạy nguyên mọi instruction trong source.

## 7. Loop-invariant code motion và loop elimination

Ngoài constant folding/DCE, JIT có thể **hoist** phép tính không đổi qua mỗi iteration ra ngoài vòng lặp (loop-invariant code motion) hoặc biến đổi loop khi semantics cho phép.

Ví dụ:

~~~java
static long sum(int n, int factor) {
    long total = 0L;
    for (int i = 0; i < n; i++) {
        int repeated = factor * factor;
        total += repeated;
    }
    return total;
}
~~~

Đây là method minh họa. `factor*factor` không phụ thuộc `i`; nếu không có exception/side effect cần giữ tại mỗi iteration, compiler có thể tính một lần rồi dùng trong loop. Đây là tối ưu **hợp lệ**, nhưng nếu bạn muốn đo “chi phí của phép nhân một triệu lần”, mã này không nhất thiết đo thứ bạn tưởng.

### Những trường hợp loop có thể rút gọn mạnh

~~~java
static long sumNumbers(int n) {
    long s = 0L;
    for (int i = 0; i < n; i++) {
        s += 1L;
    }
    return s;
}
~~~

Về mặt toán học, khi n không âm và không có side effects liên quan, kết quả bằng n. Compiler **có thể** nhận ra các mô hình đơn giản và thay đổi loop. Không hứa HotSpot **luôn** biến mọi loop thành công thức; các phép tối ưu phụ thuộc JDK, compiler, profile, loại dữ liệu, loop shape và constraints.

Đừng chỉnh benchmark đến mức vòng lặp chỉ kiểm tra compiler có nhận ra một đẳng thức hay không, trừ khi đó chính là mục tiêu nghiên cứu. Workload thật có data dependencies, branches, arrays và cache behavior cần được tái hiện.

### Loop body không có tác dụng quan sát

~~~java
static void pointless(int n) {
    for (int i = 0; i < n; i++) {
        int tmp = i * 17;
    }
}
~~~

Đây là code minh họa. Nếu JIT có thể loại loop mà vẫn giữ các ràng buộc Java có liên quan, code có thể trở nên gần như rỗng. Tuy nhiên không được khẳng định **mọi loop không side effects luôn có thể xóa mà không xét termination/safepoints/runtime constraints**. Những loop không kết thúc, ngoại lệ có thể xảy ra hoặc yêu cầu VM progress/safepoint có thể giới hạn transformation.

## 8. DCE không đồng nghĩa escape analysis, nhưng chúng phối hợp với nhau

Một đoạn code:

~~~java
static int calculate(int a, int b) {
    Pair pair = new Pair(a, b);
    return pair.left + pair.right;
}
~~~

Đây là method minh họa, cần định nghĩa class `Pair` có fields/constructor phù hợp. Với HotSpot, **escape analysis** có thể chứng minh `pair` không escape; **scalar replacement** có thể biểu diễn fields thành các scalar values và loại **allocation vật lý** khi semantics cho phép.

Đây không chỉ là xóa “lệnh `new` vì kết quả không dùng”: các field values **vẫn** đóng góp vào return value. Tối ưu giữ phép tính cần thiết và loại đại diện object trung gian.

| Cơ chế | Ví dụ công việc bị bớt | Vì sao đúng |
| --- | --- | --- |
| Constant folding | `6*7` thành `42` | Kết quả xác định, không mất behavior |
| Constant propagation | Truyền giá trị đã biết qua assignments/calls | Có thể chứng minh giá trị tại điểm dùng |
| Dead-code elimination | Xóa phép tính chỉ tạo result không dùng | Không có side effect/exception observable cần giữ |
| Common-subexpression elimination | Tái sử dụng kết quả biểu thức lặp lại | Dependencies/aliasing/side effects cho phép |
| Loop-invariant code motion | Đưa phép tính bất biến khỏi loop | Không thay đổi ordering/exception/side effects |
| Escape analysis/scalar replacement | Loại allocation của temporary object | Identity/escape không cần biểu diễn vật lý |
| Inlining | Thay method call bằng logic callee | Tạo điều kiện tối ưu qua method boundary |
| Branch pruning | Bỏ nhánh đã biết không thể xảy ra | Có guard/profile hoặc chứng minh từ input |

**Không nên** coi DCE và escape analysis là cùng một tối ưu chỉ vì cả hai có thể làm số allocations do profiler ghi nhận giảm.

### Object identity và synchronization giới hạn tối ưu

Nếu object được đưa ra global state, dùng làm lock, identity hash hoặc được JNI code quan sát, compiler phải bảo toàn behavior. Ngược lại, một object chỉ giữ hai numbers bên trong một method không nhất thiết cần tồn tại vật lý nếu không ai có thể phân biệt.

Trạng thái HotSpot có thể được phục hồi qua **deoptimization** nếu optimized assumptions không còn đúng; JIT phải bảo đảm tính đúng đắn cả khi cần quay về interpreter.

## 9. Những phép tính không được xóa vì ngoại lệ quan sát được

Xét:

~~~java
static int division(int denominator) {
    int ignored = 100 / denominator;
    return 7;
}
~~~

Với `denominator=0`, method **phải ném ArithmeticException**, không phải return 7. Kể cả `ignored` không bao giờ đọc, phép chia vẫn có observable exceptional behavior.

Các trường hợp tương tự:

~~~java
static int arrayRead(int[] array, int i) {
    int unused = array[i];
    return 7;
}

static int dereference(String text) {
    int unused = text.length();
    return 7;
}
~~~

Đây là method snippets. Nếu `array` null hoặc index ngoài biên, array read có thể ném `NullPointerException`/`ArrayIndexOutOfBoundsException`. Nếu `text` null, `text.length()` ném NPE. Compiler **không thể xóa bừa** những checks nếu người gọi có thể quan sát exception.

Nhưng ở một call site mà JIT chứng minh `array != null` và index hợp lệ, hoặc `text != null`, các checks có thể được loại bỏ/hoist theo conditions đó. **Tính đúng đắn được xét theo toàn bộ đường thực thi và các assumptions được bảo vệ**, không theo một statement tách rời.

### Thứ tự ngoại lệ cũng quan trọng

Java quy định evaluation order cho expressions và method calls. Đổi thứ tự hai phép tính có thể thay exception nào xảy ra trước hoặc thay side effects. JIT chỉ được reorder khi hành vi **được phép quan sát** vẫn phù hợp Java semantics. Điều này giải thích vì sao một phép toán nhìn có vẻ không được dùng vẫn có thể không thể xóa.

## 10. Volatile: “chống DCE” nhưng có thể đổi bài toán đang đo

Một mẹo thường thấy:

~~~java
static volatile long sink;

static void measuredOperation(int x) {
    long result = x * 31L;
    sink = result;
}
~~~

`volatile` field write mang semantics ordering/visibility; việc ghi làm kết quả “observable” hơn đối với optimizer. Nhưng **mỗi iteration giờ có volatile memory operation**, không còn đo thuần phép nhân nữa. Trên multi-core hardware và dưới contention, overhead của store/visibility/order có thể chi phối.

Do đó:
- Với **JMH** có thể ưu tiên `return result` cho benchmark method hoặc dùng **`Blackhole.consume(result)`**.
- Không thêm một `System.out.println` sau mỗi phép toán: console I/O chậm và rất biến thiên.
- Nếu production thật sự có volatile write, nó **thuộc workload** và phải đo.
- Một sink dùng chung còn có thể gây contention/false sharing, ảnh hưởng phép so sánh.
- Volatile field không phải “tắt toàn bộ constant folding”; compiler vẫn có thể fold phép toán tạo kết quả trước khi volatile write nếu đầu vào đã biết.

**Không có cách nào yêu cầu JIT làm công việc thừa chỉ để benchmark đẹp mà vẫn gọi đó là đo mã production**. Benchmark nên được thiết kế sao cho tính toán có ý nghĩa và input/output phản ánh workload.

## 11. JMH: kiểm soát DCE và constant folding

**JMH (Java Microbenchmark Harness)** là công cụ thuộc OpenJDK để viết microbenchmarks trên JVM. Nó hỗ trợ warmup, measurement iterations, forks, states, parameters, kết quả đo và cơ chế giảm một số bẫy JVM/compiler.

### Benchmark sai: kết quả bị bỏ đi

~~~java
@Benchmark
public void discarded() {
    expensive(42);
}
~~~

Đây là **fragment JMH minh họa**, cần imports/project JMH và method `expensive`. Nếu callee không có observable side effects và result bị bỏ qua, compiler có thể xóa phần lớn code sau inlining. Thời gian đo có thể gần như chỉ còn chi phí gọi/framework hay phép toán đã được tối ưu rỗng.

### Tốt hơn: return hoặc Blackhole

~~~java
@Benchmark
public int returned() {
    return expensive(value);
}

@Benchmark
public void consumed(Blackhole bh) {
    bh.consume(expensive(value));
}
~~~

Đây là **fragment JMH minh họa**; `value` cần thuộc JMH state. JMH tiêu thụ returned result hoặc `Blackhole.consume` để hạn chế DCE ở biên benchmark. Nhưng:

- **Return result không tự ngăn constant folding** nếu input vẫn là hằng.
- Blackhole không bảo đảm mọi instruction ở source còn tồn tại; JIT vẫn tối ưu hợp lệ những phần trung gian.
- Blackhole có overhead, vì vậy dùng theo đúng JMH contract.
- Không tự đặt tên một method `blackhole()` rồi giả định có hiệu lực như công cụ JMH.
- Nếu benchmark tạo một object nhưng chỉ consume một scalar field, escape analysis vẫn có thể loại object allocation vật lý; đó là tối ưu hợp lệ cho **đúng workload được đo**.

### @State và @Param giải quyết bẫy input hằng

Để không truyền literal cố định tại call site, tạo state với fields và nhận parameter từ JMH harness. Dùng các parameter values đại diện cho production, không luôn chọn cùng 7 nếu hệ thống chạy nhiều phân phối.

## 12. Ví dụ JMH đầy đủ (Java 17+) để nghiên cứu folding

Đặt file **`FoldingBench.java`** trong **một dự án JMH đã cấu hình thư viện JMH** ở package `org.example`:

~~~java
package org.example;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FoldingBench {
    @Param({"11", "17", "103"})
    public int input;

    static int transform(int x) {
        int result = x;
        for (int i = 0; i < 8; i++) {
            result = result * 31 + i;
        }
        return result;
    }

    @Benchmark
    public int stateInput() {
        return transform(input);
    }

    @Benchmark
    public int constantInput() {
        return transform(11);
    }

    @Benchmark
    public void blackholeInput(Blackhole bh) {
        bh.consume(transform(input));
    }
}
~~~

**Điều kiện biên dịch:** class này có dependencies `jmh-core` và JMH annotation processor từ **JMH Maven archetype** hoặc một dự án JMH được cấu hình tương đương. Không thể biên dịch độc lập bằng `javac FoldingBench.java` với Java SE thuần vì annotations/JMH runtime là thư viện ngoài JDK.

Một workflow thông dụng sau khi tạo project JMH bằng Maven archetype chính thức:
1. Dùng JDK 17+ và Maven tương thích.
2. Thêm file theo đúng package vào `src/main/java/org/example/FoldingBench.java`.
3. Tạo artifact bằng `mvn clean verify` từ thư mục dự án JMH đã có `pom.xml`.
4. Chạy JMH runner từ JAR của project:

~~~bash
mvn clean verify
java -jar target/benchmarks.jar FoldingBench -wi 5 -i 5 -f 2
~~~

Các options `-wi 5`, `-i 5`, `-f 2` là **ví dụ warmup iterations, measurement iterations, forks**. Nên xem `java -jar target/benchmarks.jar -h` để biết chi tiết theo JMH version. **Không đưa bảng kết quả đo “chính xác”** vì ns/op phụ thuộc CPU, JDK, optimization, load và noise.

### Cách đọc ba benchmark

- `stateInput`: input đọc từ non-final JMH state field. Điều đó làm việc chứng minh một hằng duy nhất khó hơn ở những điểm tối ưu thông thường và phản ánh nhiều @Param scenarios.
- `constantInput`: gọi `transform(11)` với literal. Đây là phép đo cho tình huống **input cố định**; JIT có thể inline/fold mạnh.
- `blackholeInput`: cùng dynamic-input path nhưng tiêu thụ kết quả bằng Blackhole. Đo overhead và so sánh với `stateInput` giúp hiểu cách JMH giữ result observable; không mặc định hai đường có nanoseconds bằng nhau.

**Một điều tinh tế:** `@Param` gán giá trị trước benchmark run, nhưng mỗi fork/iteration có thể thấy một giá trị ổn định; JIT vẫn có thể suy luận thêm nếu state và code shape cho phép. @State không là cam kết “không tối ưu gì”, mà là thiết kế hạn chế **benchmark vô tình chuyển thành một phép tính hằng**. Để đo inputs thay đổi từng operation, có thể dùng một array dataset và iterator/index state thích hợp.

### Tại sao không dùng một vòng lặp 100 triệu lần trong @Benchmark?

Nếu bạn đặt ngay một vòng for cố định và đo average time của toàn loop, JIT có thể unroll/hoist/fold mạnh, làm công việc bên trong không còn như kỳ vọng. Hơn nữa một JMH invocation có độ dài không đồng nhất với operation thực. Nếu cần đo một batch, xem JMH mechanisms như `@OperationsPerInvocation` hoặc batch size theo semantics phù hợp và document rõ measurement unit.

## 13. Bẫy benchmark: nguồn input đã biết và dead output

Bốn trường hợp hay gặp:

| Benchmark body | Rủi ro | Điều chỉnh |
| --- | --- | --- |
| `void bench(){ expensive(10); }` | Result bỏ đi, DCE có thể xóa | Trả result hoặc Blackhole |
| `int bench(){ return expensive(10); }` | Result được dùng nhưng input hằng, folding | Dùng JMH @State/@Param nếu cần dynamic input |
| `int bench(){ return expensive(state.value); }` | Hợp lý hơn, nhưng profile có thể vẫn chuyên biệt | Đảm bảo dataset/parameter distribution phản ánh production |
| `void bench(){ sink = expensive(value); }` với volatile sink | Volatile store làm workload khác | Tránh nếu chỉ muốn đo phép tính |
| `void bench(){ System.out.println(expensive(value)); }` | I/O áp đảo CPU, side effects ngoài mục tiêu | Không in trong timed section |
| `int bench(){ return cachedValue; }` | Đo field load/cache hit thay vì thuật toán | Tách setup/caching và định nghĩa mục tiêu |
| `Object bench(){ return new Tiny(); }` | Allocation có thể được optimize khác khi kết quả escape | Benchmark allocation semantics vs object construction/capture rõ ràng |

**Với một service thực tế**, việc result không được dùng đôi khi chính là bug benchmark; nhưng trong production logic, nếu kết quả **thật sự không cần**, JIT loại phép tính là một thành công tối ưu, không phải lỗi. Cần đo việc hệ thống **thực sự cần làm**.

## 14. JMH sample chính thức: JMHSample_08 và JMHSample_10

OpenJDK JMH có hai ví dụ tham khảo rất phù hợp:
- **`JMHSample_08_DeadCode`** minh họa một benchmark bỏ result bị tối ưu đi và cách trả về result/consume để hạn chế DCE.
- **`JMHSample_10_ConstantFold`** minh họa một phép tính có inputs là hằng bị fold và cách dùng **non-final instance fields trong JMH @State** để giảm bẫy.

Bài học từ cả hai: **chỉ giữ đầu ra chưa đủ nếu đầu vào vẫn là hằng; chỉ dùng input biến động cũng chưa đủ nếu output không được tiêu thụ**. Một benchmark có thể sai ở cả hai đầu.

Cần đọc mục tiêu của JMH API:
- `@Benchmark` đánh dấu method được harness đo.
- `@State(Scope.Thread)` tạo benchmark state theo thread.
- `@Param` cung cấp giá trị cấu hình của state.
- `Blackhole.consume` giúp result không bị optimizer bỏ đi một cách không mong muốn ở ranh giới benchmark.
- Return value của @Benchmark được JMH tiêu thụ thích hợp theo harness behavior.

Tài liệu nguồn:
- https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java
- https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_10_ConstantFold.java
- https://github.com/openjdk/jmh/blob/master/jmh-core/src/main/java/org/openjdk/jmh/infra/Blackhole.java

## 15. JMH vẫn có giới hạn: benchmark phải đại diện cho workload

Một microbenchmark chuẩn không đảm bảo kết quả dự đoán được latency trong ứng dụng web/server:
- JIT có thể inline code khác khi dùng từ application thật.
- Production có concurrency, lock contention, CPU throttling, GC, cache misses và I/O.
- Branch predictability và object lifetime khác benchmark input.
- Tối ưu có thể phụ thuộc call-site profile, type distribution, escape và steady-state warmup.
- Benchmark một method thuần số học không đại diện cho request xử lý JSON, network và database.
- Cách data được phân bố trong memory có thể chi phối cost hơn số phép nhân.

**Ưu tiên cả microbenchmark lẫn workload benchmark**:
1. Microbenchmark kiểm tra **một giả thuyết tối ưu** rõ ràng (ví dụ constant folding, branch elimination).
2. Macro/load test xác nhận **throughput, end-to-end p95/p99 và CPU/GC** của service thực.
3. Tất cả kết quả cần có **JDK exact build**, JVM flags, CPU quota/hardware, JMH version, forks, input distribution và error bars.

### Nhầm benchmark result với chi phí một câu lệnh

Nếu JMH báo dưới một nanosecond/op:
- Đó có thể là throughput amortization hoặc phép toán đã được fold.
- Có thể JMH tính “operation” khác với số instructions trực giác.
- CPU pipelining, vectorization và đo nhiều iterations cùng nhau có thể tạo ns/op nhỏ hơn độ trễ của một individual machine instruction.
- Đừng kết luận “CPU chạy 1 phép chia hết 0 ns”. Cần đọc mode, code shape, generated assembly/IR khi thực sự cần.

## 16. Các tối ưu hợp lệ khác gây nhầm lẫn khi đo

### Common subexpression elimination (CSE)

~~~java
int left = x * 31;
int right = x * 31;
return left + right;
~~~

Đây là snippet trong method, giả sử x là biến int. Compiler có thể chỉ tính `x*31` một lần và dùng lại kết quả. Nếu benchmark đặt hai phép nhân để đo “hai lần multiplication”, JIT có thể đo một phép nhân.

### Algebraic simplification

~~~java
int y = x + 0;
int z = y * 1;
return z;
~~~

Có thể đơn giản hóa thành return x. Không nói compiler phải giữ phép cộng/nhân vì source có hai operators.

### Branch folding

~~~java
if (false) {
    System.out.println("unreachable branch");
}
~~~

Đây là snippet. `javac` hoặc JIT có thể không cần thực thi nhánh không thể đạt. Tuy nhiên đừng đặt unreachable statements sau `return` nếu Java source sẽ không biên dịch theo reachability rules.

### Loop unrolling và vectorization

Một loop duyệt mảng nguyên số có thể được unroll hoặc vectorize bởi JIT nếu được hỗ trợ và các dependence/aliasing constraints cho phép. Khi đó việc “mỗi iteration có một phép toán scalar” không còn tương ứng một-một với machine instructions.

### Strength reduction

Thay một phép nhân/địa chỉ index bằng shift/lea hoặc arithmetic thích hợp là một optimization phụ thuộc target CPU. Không dùng số lượng operator trong source để suy runtime cycles.

### Escape analysis

Một object tạo trong loop nhưng không escape có thể được scalar-replace, làm allocation rate trong profiler thấp hơn số `new` expressions. Nếu mục tiêu là đo allocation/GC pressure, benchmark cần làm cho object **thực sự escape phù hợp production semantics**, không chỉ có chữ `new`.

## 17. Inlining và specialization theo profile có thể dẫn đến deoptimization

C2 có thể dùng observed type profiles để chuyên biệt hóa call path. Ví dụ một virtual call thường thấy cùng một receiver type có thể được inline với một guard. Khi một type khác xuất hiện, optimized assumption không còn hợp lệ.

Để vẫn đúng Java semantics, HotSpot có thể:
1. Guard runtime assumptions.
2. Thực thi optimized path khi assumption đúng.
3. Thực hiện deoptimization/uncommon trap hoặc đường fallback khi assumption không đúng.
4. Tiếp tục ở interpreter/compiled version phù hợp, có thể recompile sau đó.

**Điều này không làm folding/DCE sai.** Optimization chỉ được áp dụng trong miền điều kiện hợp lệ; guard/fallback bảo toàn behavior ngoài miền.

Dưới workload benchmark chỉ chứa một loại input/receiver, JIT có thể tạo specialized code mạnh. Trong production có nhiều loại và input distributions khác, branch prediction, guard miss và deoptimization overhead có thể thay đổi hiệu năng đáng kể. Vì vậy cần benchmark có **type diversity** và parameters đại diện.

## 18. Mối liên hệ với Java Memory Model và multithreading

JIT không được tự do xóa các operations có **happens-before/order semantics** theo Java Memory Model. Với `volatile`, synchronization, thread start/join hoặc atomic APIs, những ràng buộc quan sát được phải giữ.

Ví dụ:

~~~java
final class Shared {
    volatile boolean ready;
    int payload;

    void publish() {
        payload = 42;
        ready = true;
    }
}
~~~

Đây là class minh họa. Trong trường hợp thread khác đọc `ready` là true bằng volatile read phù hợp, Java Memory Model hỗ trợ visibility của những writes trước volatile write. JIT không thể xóa/reorder theo cách phá guarantee này.

**Nhưng** trong một single-threaded benchmark không có synchronization, compiler có thể optimize mạnh hơn. Đừng suy từ microbenchmark thiếu concurrency ra behavior của code chia sẻ giữa threads.

Một variable thông thường không phải volatile **không đồng nghĩa JVM được tự do trả kết quả sai trong mọi trường hợp**; sự khác biệt nằm trong ràng buộc Java Memory Model và các hành vi program được phép quan sát. Khi thiết kế benchmark concurrent, cần mô hình đúng data races, atomicity và synchronization thay vì tự suy luận JIT.

## 19. Dùng javap, PrintCompilation, JFR và assembly một cách thận trọng

### Bước 1: bytecode với javap

~~~bash
javac --release 17 FoldingAndDceDemo.java
javap -c -p FoldingAndDceDemo
~~~

Kiểm tra `compileTimeConstant` và `unusedArithmetic`. Nếu bytecode chứa arithmetic dư mà optimized run vẫn nhanh, điều đó **có thể** do JIT; cần thêm bằng chứng về generated code, không được kết luận chỉ từ timing.

### Bước 2: compilation events

~~~bash
java -XX:+PrintCompilation FoldingAndDceDemo
~~~

HotSpot có thể in compiler events, bao gồm library methods. **Không có log/compile ID/output thứ tự cố định** vì dependent JDK build, warmup, compiler queue và workload. Chương trình quá ngắn có thể không compile một số methods trước khi kết thúc.

### Bước 3: JFR

Có thể thu thông tin compiler/GC/CPU events với JFR theo JDK:

~~~bash
java -XX:StartFlightRecording=filename=jit-folding.jfr,dumponexit=true,settings=profile FoldingAndDceDemo
~~~

Recording có thể thiếu events nếu chương trình quá ngắn, và **JFR không trực tiếp chứng minh một expression đã được constant-fold**. JFR hữu ích để thấy JIT/CPU/GC context và tìm hotspots.

### Bước 4: generated machine code

Muốn chứng minh phép tính bị loại khỏi optimized code, cần dùng công cụ phù hợp để phân tích native instructions hoặc JIT compiler IR:
- HotSpot `-XX:+PrintAssembly` khi có disassembler/hsdis phù hợp, cùng các unlock flags tương ứng.
- JITWatch đọc compilation logs và hiển thị các quyết định compiler.
- Async profiler/perf disassembly với debug information nếu môi trường phù hợp.
- Ideal Graph Visualizer hoặc debug/JIT diagnostic facilities với môi trường hỗ trợ.

**Các công cụ này không phải lúc nào cũng cài sẵn hoặc hoạt động trên mọi JDK/platform**. Không viết lệnh `-XX:+PrintAssembly` như điều kiện bắt buộc để chạy code Java.

### LogCompilation để nghiên cứu deeper optimizer

~~~bash
java -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation -XX:LogFile=jit.xml FoldingAndDceDemo
~~~

Đây là HotSpot diagnostic option, XML format/version dependent. JITWatch có thể giúp xem inlining, compilation tiers, traps. Đừng nhầm compiler source IR với Java bytecode hoặc giả định source statement có một instruction tương ứng.

## 20. Vì sao timing bằng System.nanoTime() một lần dễ sai?

Một phép đo:

~~~java
long start = System.nanoTime();
// gọi work() nhiều lần
long elapsed = System.nanoTime() - start;
System.out.println(elapsed);
~~~

là **đoạn minh họa**, chưa đủ là benchmark đáng tin. Các nguồn sai lệch:

1. **Dead-code elimination:** nếu work result không dùng, work bị xóa.
2. **Constant folding:** nếu inputs known, kết quả có thể tính trước.
3. **JIT warmup/tiering:** vòng chạy đầu interpreter/C1, vòng sau C2, có OSR giữa chừng.
4. **Loop optimizations:** unrolling, LICM, vectorization, loop removal.
5. **Timer overhead/resolution:** phương thức đo có chi phí và noise.
6. **GC activity:** allocations hoặc logging gây pauses/churn.
7. **OS scheduling/CPU frequency:** thread bị preempt, thermal throttling.
8. **CPU caches/TLB:** dữ liệu nóng/lạnh khác nhau.
9. **Concurrent load:** nhiều tiến trình tranh CPU/memory bandwidth.
10. **Benchmark unit/normalization:** elapsed time của một loop không tự là latency của một operation trong ứng dụng.

Ngay cả khi lặp 100 lần và lấy median, nếu benchmark body bị fold/DCE thì bạn chỉ đo một workload đã bị tối ưu khác mục tiêu.

### Một checklist microbenchmark hợp lệ

- **Nêu giả thuyết:** đo phép nhân với runtime inputs hay đo input-hằng?
- **Đảm bảo input:** dùng state/datasets và tham số đủ đại diện.
- **Đảm bảo output được dùng:** return, Blackhole hoặc observable application semantics.
- **Không thêm artificial work áp đảo:** console I/O, random number generation, synchronization không có trong production.
- **Warmup/forks:** JMH giúp kiểm soát, đo nhiều lần.
- **JIT version/flags:** ghi JDK build, tiered mode và compiler version.
- **Compare like-for-like:** cùng dữ liệu, cùng correctness và tool settings.
- **Theo dõi CPU/GC:** không suy từ ns/op ra tổng hiệu năng service.
- **Kiểm chứng optimization:** khi nghi DCE/folding, xem compiler logs/assembly/IR phù hợp.
- **Báo variance:** không chỉ một con số đẹp nhất.

## 21. Bài tập tự kiểm tra

**Câu 1.** Với `static final int X = 6*7`, phần gấp hằng có thể xảy ra ở tầng nào?

**Đáp án:** `javac` có thể gấp ngay ở bytecode vì `X` là JLS constant variable; JIT còn có các dạng folding/propagation runtime khác.

**Câu 2.** `static final int Y = Integer.parseInt("42")` có phải compile-time constant variable?

**Đáp án:** **Không** theo JLS; initializer là method call, dù field được khai báo final.

**Câu 3.** Benchmark chỉ gọi `compute(10)` rồi bỏ result có hai nguy cơ nào?

**Đáp án:** **Constant folding** với input literal và **DCE** vì output không được dùng.

**Câu 4.** Return result trong JMH đã đủ ngăn gấp hằng chưa?

**Đáp án:** Chưa. JMH giữ output observable hơn, nhưng input literal vẫn có thể bị fold.

**Câu 5.** Vì sao không dùng `System.out.println` trong timed benchmark?

**Đáp án:** Console I/O có overhead lớn/biến động, đo nhầm in ra màn hình thay vì calculation.

**Câu 6.** Tại sao volatile sink có thể làm benchmark sai?

**Đáp án:** Nó thêm memory-ordering/visibility overhead và contention; là operation mới không nhất thiết có trong workload thật.

**Câu 7.** `int ignored = 100/x; return 7;` có luôn được phép loại phép chia không?

**Đáp án:** Không. Nếu x=0, phải ném `ArithmeticException`.

**Câu 8.** `return x*0+9;` có thể thành 9 khi x là int không?

**Đáp án:** Có nếu compiler chứng minh không có side effects phụ; integer multiplication by zero là 0 theo semantics.

**Câu 9.** `return x*0.0d;` có thể luôn thành +0.0d không?

**Đáp án:** Không. Infinity×0 là NaN và negative×positive zero có thể là -0.0.

**Câu 10.** Đọc `javap` thấy `imul` nghĩa CPU nhất thiết có lệnh nhân không?

**Đáp án:** Không. `javap` hiển thị bytecode; JIT có thể fold hoặc remove ở native level.

**Câu 11.** Một object được tạo bằng `new` không escape có nhất thiết tốn heap allocation trên optimized execution không?

**Đáp án:** Không. Escape analysis/scalar replacement có thể bỏ physical allocation khi giữ đúng semantics.

**Câu 12.** Vì sao @State non-final field tốt hơn một literal cố định trong constant-folding benchmark?

**Đáp án:** Đầu vào được lấy từ state thay vì hằng trực tiếp tại call site, giảm khả năng benchmark vô tình biến thành constant path; vẫn phải kiểm tra tối ưu/runtime.

**Câu 13.** Hoist một phép tính ra ngoài loop có làm sai Java không?

**Đáp án:** Nếu JIT chứng minh bảo toàn side effects, exception order và semantics, đó là tối ưu hợp lệ; nếu không chứng minh, không được hoist.

**Câu 14.** Một benchmark báo 0,2 ns/op có chắc CPU thực hiện operation trong 0,2 ns không?

**Đáp án:** Không. Cần xét loop amortization, compiler optimization, pipeline và measurement mode.

**Câu 15.** Nếu source có `new Object()` nhưng profiler không thấy allocation tương ứng, chắc profiler sai?

**Đáp án:** Không. JIT có thể loại allocation vật lý của object không escape/không quan sát được.

**Câu 16.** JMH benchmark có tốt sẽ dự đoán p99 REST API chính xác?

**Đáp án:** Không. Phải kiểm tra application workloads, I/O, concurrency, GC và tail latency.

## 22. Bản đồ ghi nhớ

~~~text
JAVA SOURCE
   |
   +--> JAVAC COMPILE-TIME
   |      constant expressions
   |      constant variables (final primitive/String)
   |      -> bytecode có thể chứa constant
   |
   v
HOTSPOT / INTERPRETER / JIT
   |
   +--> inlining + profile
   +--> constant propagation/folding
   +--> dead branch/code removal
   +--> loop invariant hoisting
   +--> scalar replacement / allocation elimination
   |
   v
MACHINE CODE (không 1-1 với source statements)
   |
   +--> phải bảo toàn Java semantics:
   |      exceptions, volatile/synchronization, I/O,
   |      floating-point, overflow, observable effects
   |
   v
BENCHMARK RISK
   |
   +-- input hằng?       -> code bị fold
   +-- output bỏ đi?      -> code bị DCE
   +-- input chưa đại diện? -> benchmark sai workload
   +-- volatile/print?    -> đo overhead mới
   |
   v
JMH @State + @Param + return/Blackhole
   |
   +-- warmup + forks + measurements
   +-- compiler logs / IR / assembly khi cần
   +-- verify correctness and production relevance
   |
   v
BENCHMARK ĐÁNG TIN HƠN
~~~

**Kết luận:** Folding và DCE là các tối ưu **đúng đắn và có lợi** của `javac`/HotSpot JIT, nhưng chúng có thể làm microbenchmark **không còn thực thi phần công việc người viết tưởng đang đo**. Hãy phân biệt constant expressions của JLS với runtime JIT optimization, không bỏ qua exceptions/side effects/IEEE 754, và kiểm soát **cả input lẫn output** trong benchmark bằng JMH. Mục tiêu của phép đo không phải giữ nguyên source instructions bằng mọi giá, mà là đại diện đúng **công việc thực tế của ứng dụng**.

## Tài liệu tham khảo

- [JLS Java SE 17 — §15.29 Constant Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29).
- [JLS Java SE 17 — §4.12.4 final Variables, Constant Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4).
- [JLS Java SE 17 — §13.1 binary compatibility và constant variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.1).
- [JLS Java SE 17 — §15.7 Evaluation Order](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.7).
- [JLS Java SE 17 — §17.4 Java Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4).
- [OpenJDK Java Microbenchmark Harness](https://openjdk.org/projects/code-tools/jmh/).
- [JMH — JMHSample_08_DeadCode](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java).
- [JMH — JMHSample_10_ConstantFold](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_10_ConstantFold.java).
- [JMH — Blackhole implementation/documentation](https://github.com/openjdk/jmh/blob/master/jmh-core/src/main/java/org/openjdk/jmh/infra/Blackhole.java).
- [Oracle JDK 17 — javap tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).
- [Oracle JDK 17 — Java launcher and JIT diagnostic options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html).
- [OpenJDK HotSpot C2 optimizer source](https://github.com/openjdk/jdk/tree/master/src/hotspot/share/opto).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-004">Tối ưu vòng lặp và loại kiểm tra dư thừa trong điều kiện phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Tối ưu vòng lặp và loại kiểm tra dư thừa trong điều kiện phù hợp

## 1. Vấn đề: Java phải an toàn, nhưng không nhất thiết kiểm tra lại cùng một điều ở mọi vòng lặp

Vòng lặp trên mảng là một trong những đoạn mã thường xuyên được thực thi trong Java. Ở mức ngữ nghĩa, mỗi lần truy cập `a[i]` phải tuân thủ quy tắc **không được đọc mảng null** và **index phải nằm trong `[0, a.length)`**. Nếu vi phạm, chương trình phải cho thấy ngoại lệ thích hợp (`NullPointerException` hoặc `ArrayIndexOutOfBoundsException`) theo hành vi Java. Nhưng nếu compiler có thể **chứng minh** một tập kiểm tra là dư thừa, nó có thể không cần thực hiện tất cả những lần kiểm tra đó trong mã máy tối ưu.

Ví dụ:

~~~java
static long sum(int[] a) {
    long result = 0;
    for (int i = 0; i < a.length; i++) {
        result += a[i];
    }
    return result;
}
~~~

Đây là **mảnh phương thức minh họa**, không có `main`. Về logic:

- `i` bắt đầu từ `0`.
- Điều kiện tiếp tục `i < a.length` cung cấp giới hạn trên của index.
- `i` tăng đúng một đơn vị.
- Độ dài của **một array object** không đổi trong suốt lifetime của array.
- Nếu `a` là `null`, việc lấy `a.length` vẫn phải thất bại đúng quy tắc Java.

Một HotSpot **C2** đủ điều kiện có thể tận dụng hình dạng này để giảm hoặc loại **array bounds checks** trong phần thân của một đường thực thi đã được chứng minh an toàn. Đây là ví dụ điển hình của **Range Check Elimination (RCE)** — loại kiểm tra biên mảng dựa trên phân tích phạm vi chỉ số.

**Điều phải nhớ:** tối ưu là quyền của JVM implementation, **không phải lời hứa ngôn ngữ Java**. Bytecode vẫn thường có `iaload`/`iastore`; JVM phải giữ **hành vi quan sát được** như giá trị, ngoại lệ, tác dụng phụ và thứ tự hợp lệ. `javap` cho biết bytecode, **không chứng minh** native code vẫn chứa mọi kiểm tra trước khi đọc mảng.

## 2. Những kiểm tra nào thường xuất hiện trong đường chạy nóng?

| Kiểm tra / điều kiện | Vì sao có? | Khi nào có thể được tối ưu? |
| --- | --- | --- |
| **Null check** với array/object | `a.length`, `a[i]`, method call trên null phải theo semantics NPE | Khi non-null đã được chứng minh, hoặc JVM dùng một implicit null check hợp lệ |
| **Array bounds check** | `i` phải thuộc `[0, a.length)` | Khi compiler chứng minh cả miền lặp hợp lệ hoặc tạo guard/slow path thích hợp |
| **Loop termination check** | Xác định còn iterations hay không | Có thể giảm tần suất qua unrolling hoặc xử lý counted-loop |
| **Loop-invariant branch** | Ví dụ `if (enabled)` không đổi giữa các iterations | Hoist hoặc **loop unswitching** nếu hữu ích và hợp lệ |
| **Repeated arithmetic** | Ví dụ tính một biểu thức chỉ phụ thuộc dữ liệu không đổi | Loop-invariant code motion, common subexpression elimination |
| **Type/subtype check** | Đa hình, array store check với reference arrays | Có thể được bỏ nếu type information/proofs đủ mạnh |
| **Synchronization và memory effects** | Java Memory Model và trạng thái shared giữa threads | Không được di chuyển trái semantics; `volatile`/locks là ràng buộc thực |

Không phải mọi kiểm tra đều là ứng viên tối ưu. Một thao tác `getInt` trên buffer, một `List.get(i)` với danh sách có thể thay đổi kích thước, một method call có side effect hoặc một chỉ số tính qua overflow có thể đòi phân tích khác hẳn array counted-loop cơ bản.

### “Loại kiểm tra” không có nghĩa tắt an toàn

Tối ưu đúng phải đảm bảo **mọi input hợp lệ và không hợp lệ** vẫn biểu hiện theo contract Java. Một đoạn code chạy nhanh với input hợp lệ không cho phép JVM đọc `a[-1]` hoặc `a[a.length]` tùy tiện. Nếu compiler chọn một **fast path** có ít checks, nó phải chứng minh đường đó an toàn hoặc bảo toàn **guard, uncommon trap, deoptimization và slow path** khi giả định không đúng.

Đó là lý do RCE không tương đương với tự viết native pointer arithmetic bỏ biên kiểm tra.

## 3. Counted loop: hình dạng dễ cho C2 phân tích

Một **counted loop** có induction variable, điểm bắt đầu, giới hạn, bước tăng/giảm và số iterations mà compiler có thể suy ra trong điều kiện thích hợp.

~~~java
for (int i = 0; i < n; i++) {
    use(i);
}
~~~

Mảnh này chỉ minh họa, `n` và `use` được xác định ở nơi khác. Trên lý thuyết dễ suy luận:

- `init = 0`
- `limit = n`
- `stride = +1`
- miền chỉ số là `0 .. n-1` khi `n > 0`.

Nhưng **không phải cứ thấy `for` là compiler có counted loop hoàn chỉnh**. Lời gọi `use`, `break`, `continue`, side effects, overflow, giới hạn biến động, nested loops, sự phức tạp của control-flow graph và lợi ích thực tế đều ảnh hưởng.

Đối chiếu ba hình dạng:

~~~java
// A: chỉ số bám theo array length; dễ chứng minh.
for (int i = 0; i < a.length; i++) {
    total += a[i];
}

// B: cận truyền từ bên ngoài; có thể cần guard.
for (int i = from; i < to; i++) {
    total += a[i];
}

// C: chỉ số phụ thuộc giá trị dữ liệu, khó suy ra phạm vi.
for (int i = 0; i < positions.length; i++) {
    total += a[positions[i]];
}
~~~

Các đoạn trên là minh họa trong method có `a`, `positions`, `total`, `from`, `to`. Với A, chứng minh `0 ≤ i < a.length` rõ ràng. Với B, nếu không bảo đảm `from ≥ 0` và `to ≤ a.length`, JVM phải giữ hành vi lỗi khi phạm vi thực sự vượt mảng. Với C, compiler không thể suy ra mọi phần tử `positions[i]` hợp lệ chỉ từ `i < positions.length`; kiểm tra `a[positions[i]]` thường vẫn quan trọng.

**Điều kiện then chốt:** miền giá trị của **biểu thức index thực sự** phải được chứng minh nằm trong miền hợp lệ của **mảng thực sự được truy cập**.

## 4. Range Check Elimination (RCE) diễn ra theo ý tưởng nào?

Một cách suy luận ở mức cao:

1. Nhận diện induction variable và miền lặp.
2. Thu thập các array accesses, ví dụ `a[i]`, `a[i+1]`, `b[2*i]`.
3. Suy ra **điều kiện** để các indices đều hợp lệ cho cả phần lặp có thể tối ưu.
4. Chứng minh các điều kiện từ loop bounds/array lengths hoặc đặt **guards** bảo vệ fast path.
5. Chuyển/loại các kiểm tra lặp lại ở đường đã được chứng minh an toàn.
6. Giữ slow path, pre/post loop, traps hoặc deoptimization nếu đường chung không đáp ứng chứng minh.

Sơ đồ khái niệm:

~~~text
Original loop
  mỗi iteration: test loop limit, access a[i] → runtime bounds semantics
                          |
                          v
C2 phân tích induction variable và array length
                          |
               miền chỉ số chắc chắn hợp lệ?
                   /                   \
                 Có                   Chưa chứng minh được
                 |                          |
       Đường tối ưu có thể            Giữ checks, tạo guards,
       bỏ check trong body            tách loop hoặc fallback
                          |
             Cả hai phải có cùng semantics Java
~~~

**RCE là khả năng của tối ưu C2, không phải tuyên bố JVM luôn xóa đúng N lệnh.** HotSpot có nhiều pass tương tác: loop optimization, loop predication, range-check analysis, IGVN, inlining, alias analysis, unrolling và backend instruction selection.

### 4.1. Với array length cố định, cái gì thật sự “bất biến”?

`a.length` không đổi cho một array object đã xác định. Nhưng **biến reference `a` có thể được gán lại trong mã**, hoặc một field chứa array có thể bị code khác thay đổi. Compiler không được tùy tiện hoist length check của **object A** rồi dùng nó như thể luôn là length của **object B**.

~~~java
for (int i = 0; i < holder.values.length; i++) {
    consume(holder.values[i]);
}
~~~

Ở đây `holder.values` là một field, `consume` là method minh họa. Nếu object field có thể đổi giữa các lần đọc, việc biến nó thành một local snapshot **có thể thay đổi semantics**, nhất là dưới concurrency hoặc side effects. C2 chỉ được loại các loads/checks mà nó có đủ chứng minh về memory effects và aliasing theo Java Memory Model.

Với API cho phép snapshot, lập trình viên có thể chủ động viết:

~~~java
int[] local = holder.values;
for (int i = 0; i < local.length; i++) {
    consume(local[i]);
}
~~~

Đây là một **thiết kế ngữ nghĩa khác**: cố ý chốt reference cho cả loop. Không nói hai đoạn luôn tương đương nếu `holder.values` có thể bị thay đổi.

### 4.2. Với cận ngoài, proof được đặt ở đâu?

Giả sử:

~~~java
static long sumRange(int[] a, int from, int to) {
    long sum = 0;
    for (int i = from; i < to; i++) {
        sum += a[i];
    }
    return sum;
}
~~~

Không thể giả định `from` và `to` luôn hợp lệ. Nếu `from = -2` hoặc `to > a.length`, ít nhất một iteration có thể truy cập index ngoài mảng và phải tuân thủ AIOOBE tương ứng. HotSpot có thể tìm đường xử lý phổ biến với điều kiện được bảo vệ, nhưng không đơn giản bỏ kiểm tra cho mọi input.

Vì vậy, nếu **API contract** xác định rõ phạm vi hợp lệ, hãy diễn đạt rõ và kiểm tra ở boundary. Tuy nhiên, xem mục 8: thêm explicit validation **có thể thay đổi ngoại lệ và thứ tự tác dụng phụ** so với phiên bản ban đầu.

## 5. Loop predication: chuyển điều kiện ra trước vòng lặp

**Loop predication** là một kỹ thuật HotSpot C2 có thể tạo **predicate/guard** ở đường đi vào loop, thay vì giữ cùng kiểu kiểm tra bất biến trong mọi iteration. Ý tưởng quan sát được từ tài liệu OpenJDK: với index affine kiểu `i * scale + offset`, có thể lập điều kiện về start, limit, stride và array length để lựa chọn đường tối ưu an toàn.

Một minh họa khái niệm:

~~~text
Input: a, from, to

Nếu chứng minh được miền index của body nằm trong [0, a.length):
    Fast loop: tiếp tục với ít range checks trong body
Nếu không:
    Fallback giữ semantics, có thể dùng trap/deoptimization hoặc slow loop
~~~

Đoạn pseudo-code chỉ giải thích ý tưởng; **không phải** mã Java có thể dùng để cài RCE bằng tay.

### Speculative guard và uncommon trap

JIT có thể chuyên biệt hóa native code cho profile đã quan sát: ví dụ các lần gọi nóng thường có `from=0` và `to=a.length`. Một predicate có thể bảo vệ giả định về miền index; nếu điều kiện không còn đúng, JVM cần giữ khả năng thực thi code tổng quát, có thể chuyển về interpreter hoặc compiler tier khác bằng **deoptimization**.

**Không nên nghĩ “deoptimization là bug”.** Đó là cơ chế để speculative optimization vẫn bảo toàn semantics Java khi profile thay đổi.

**Không phải mọi guard failure đều cần tạo Java exception ngay tại guard.** JVM có thể chuyển về đường thực thi tổng quát rồi thực hiện các thao tác theo thứ tự source để xác định có exception thật hay không.

### Tránh nhầm guard với manual validation

**Compiler guard** là bước tối ưu hóa nội bộ và bắt buộc giữ hành vi quan sát được của chương trình. **Manual validation** do người viết mã thêm có thể chủ động thay đổi contract API; đó là thay đổi ở tầng source.

Vì vậy câu “đưa mọi array bounds check ra đầu method” **không tự đúng** nếu trong source có I/O, side effects, `break` hoặc chỉ một phần loop có thể chạy.

## 6. Loop peeling, pre/main/post và ranh giới chỉ số

Một loop có thể có vùng đầu/cuối khó bảo đảm an toàn cho mọi iteration nhưng có vùng giữa chắc chắn hợp lệ. JVM có thể **peel/split** loops:

~~~text
Source:
 for i = begin ... end:
    use(a[i + 1])

Một tổ chức native khả dĩ:
 [pre-loop]      xử lý vài iteration đầu / checks cần thiết
 [main-loop]     miền chứng minh an toàn, checks được giảm
 [post-loop]     xử lý phần dư / tail với checks cần thiết
~~~

Đây là sơ đồ, **không cam kết** C2 sẽ luôn tạo đúng ba loops. Việc tách loop làm code lớn hơn và có overhead của branches/guards; với loops rất ngắn, tối ưu ấy có thể **không đáng**.

Ví dụ index `i + 1` đòi chứng minh `0 ≤ i + 1 < a.length`. Chỉ vì điều kiện `i < a.length` đúng **chưa đủ** chứng minh `i+1` hợp lệ ở iteration cuối. Người học cần nhìn **index expression**, không chỉ loop condition.

Trường hợp có hai arrays:

~~~java
for (int i = 0; i < a.length; i++) {
    sum += a[i] + b[i];
}
~~~

`a[i]` có bound theo `a.length`; còn `b[i]` chỉ hợp lệ mọi vòng nếu `b` đủ dài. Điều kiện an toàn thêm có thể là `b != null` và `b.length >= a.length`. JVM không được âm thầm bỏ check của `b[i]` khi `b` ngắn hơn.

## 7. Các biến đổi loop khác thường đi cùng RCE

### 7.1. Loop-invariant code motion — LICM

Một biểu thức chỉ phụ thuộc dữ liệu không đổi trong suốt loop, không có side effect hoặc ràng buộc thứ tự exception/memory cần giữ, có thể được dời ra ngoài loop.

~~~java
int scale = config.scale();
for (int i = 0; i < a.length; i++) {
    result += a[i] * scale;
}
~~~

Đoạn này đã chủ động gọi `config.scale()` **trước loop**. Nếu source ban đầu gọi `config.scale()` **bên trong loop**, tự dời call ra ngoài chỉ hợp lệ khi lời gọi không có side effects, không thay đổi giá trị, và việc thay đổi số lần/thời điểm gọi không đổi semantics. Với `volatile`, locks, I/O hoặc stateful method, không được tùy tiện hoist.

**LICM giảm work lặp lại nếu an toàn**, nhưng đôi khi làm tăng register pressure hoặc thực hiện một phép tính trước loop dù loop có zero iterations; compiler phải xét tính hợp lệ và lợi ích.

### 7.2. Loop unswitching

Nếu có nhánh **không thay đổi theo iteration**:

~~~java
for (int i = 0; i < a.length; i++) {
    if (enabled) {
        sum += a[i];
    }
}
~~~

C2 có thể tạo hai phiên bản loop:

~~~text
if (enabled):
    for (...) sum += a[i]
else:
    for (...) [body khác hoặc không có cập nhật sum]
~~~

Ý tưởng: chỉ kiểm tra `enabled` một lần thay vì ở mỗi iteration. Nhưng duplication tăng code size, có thể tác động instruction cache. **Không hứa C2 luôn unswitch**; JIT dùng cost model/compile budget. Nếu `enabled` là giá trị có thể thay đổi theo iteration (ví dụ `volatile` đọc lại), không được coi nó là invariant vô điều kiện.

### 7.3. Loop unrolling

Unrolling sao chép body nhiều lần:

~~~text
Ban đầu:
  for i: process(a[i])

Sau unroll 4:
  xử lý a[i], a[i+1], a[i+2], a[i+3]
  rồi i += 4
  phần còn dư dùng tail path
~~~

Lợi ích có thể gồm ít branch overhead và nhiều cơ hội instruction-level parallelism/RCE/vectorization. Nhưng body lớn hơn làm code cache/instruction cache chịu áp lực; loop quá ngắn hoặc body phức tạp có thể không lợi.

**Không được bỏ phần dư:** nếu array length = 9 và unroll factor = 4, chương trình vẫn phải xử lý đúng 9 elements (ví dụ 4 + 4 + 1), không đọc tới element thứ 12.

### 7.4. Auto-vectorization / SuperWord

Sau khi C2 thấy những operations trên các phần tử lân cận độc lập, có thể dùng SIMD instructions để xử lý nhiều dữ liệu cùng lúc; HotSpot có **SuperWord** vectorization. Không phải loop Java nào có array cũng vectorize.

Điều kiện/rào cản điển hình:

- Truy cập memory đều đặn/contiguous với dependencies đủ rõ.
- Không có aliasing gây sai thứ tự đọc/ghi.
- Phép toán có semantics cho phép vectorize (chú ý floating point, strict ordering, overflow và reductions).
- Hardware/CPU instruction set hỗ trợ và VM flags phù hợp.
- Không có branch/call/exception ordering khiến vectorized path sai.

**SIMD không đồng nghĩa Java Vector API**. Auto-vectorization là JIT optimization; Java Vector API ở Java 17 là incubator module và không phải phần phụ thuộc của các code mẫu trong bài.

### 7.5. Induction-variable simplification

C2 có thể canonicalize biểu thức index, loại các phép tính có thể suy ra từ induction variable và thay phép nhân lặp lại bằng increments nếu đúng semantics. Nhưng với `int` arithmetic, Java overflow **wrap quanh modulo 2^32**, nên không được thay đổi logic vòng lặp chỉ bằng trực giác số học vô hạn.

### 7.6. Common subexpression elimination

Nếu hai phép tính giống nhau dùng operands không thay đổi, không có memory aliasing hoặc side effects cần xét, compiler có thể dùng lại kết quả thay vì tính lại. Với array loads, phải xem **có store hoặc call nào có thể thay đổi data giữa hai lần đọc hay không**.

## 8. Cẩn thận: Java exceptions và side effects không được đổi chỗ tùy tiện

Xét một phương thức mô tả:

~~~java
static int useRange(int[] a, int from, int to) {
    int sum = 0;
    for (int i = from; i < to; i++) {
        System.out.println("VISITED=" + i);
        sum += a[i];
    }
    return sum;
}
~~~

Nếu `a.length=3`, `from=1`, `to=5`, source sẽ in các dòng `VISITED=1`, `VISITED=2` và `VISITED=3` trước khi truy cập `a[3]` ném `ArrayIndexOutOfBoundsException`. Nếu lập trình viên chuyển **toàn bộ validation** lên đầu method và ném ngay khi `to> a.length`, các `println` trước lỗi sẽ biến mất.

Đây là **thay đổi hành vi quan sát được**. C2 cũng không được tối ưu làm mất những side effects phải xảy ra trước exception theo semantics Java.

Một cách xây dựng API khác hoàn toàn hợp lệ:

~~~java
Objects.checkFromToIndex(from, to, a.length);
for (int i = from; i < to; i++) {
    process(a[i]);
}
~~~

Nhưng **đó là một API contract mới có kiểm tra upfront**, không tự tương đương với mọi code cũ, đặc biệt khi loop không có iterations hoặc khi có side effects/exception order. `Objects.checkFromToIndex` là API Java 9+, dùng được trên Java 17.

### Trường hợp loop zero iterations

Nếu:

~~~java
for (int i = 0; i < 0; i++) {
    sum += a[i];
}
~~~

Body không được chạy; nếu `a` là null, đoạn loop này **không nhất thiết NPE** vì không có lần dereference bên trong body. Nhưng nếu đổi thành `Objects.requireNonNull(a);` trước loop, nó sẽ ném NPE ngay.

Ngược lại, điều kiện `i < a.length` **có dereference `a` ngay khi kiểm tra condition**, dù body không chạy nếu length bằng 0. Từng biểu thức trong điều kiện mới quyết định semantics.

### Range check elimination không cho phép “bỏ qua ngoại lệ hiếm”

Nếu index out-of-range xuất hiện ở production, Java phải tiếp tục ném đúng lỗi khi code thực hiện invalid access. JIT có thể dùng guard và fallback thay vì giữ check trong hot body, nhưng **không được bỏ validation theo kiểu native unsafe**.

## 9. Mẫu Java 17 runnable: miền index rõ và đầu ra xác định

Tạo file **`LoopOptimizationDemo.java`**:

~~~java
import java.util.Objects;

public final class LoopOptimizationDemo {
    static long sumRange(int[] values, int from, int to) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromToIndex(from, to, values.length);
        long total = 0;
        for (int i = from; i < to; i++) {
            total += values[i];
        }
        return total;
    }

    static long sumIfEnabled(int[] values, boolean enabled) {
        long total = 0;
        for (int i = 0; i < values.length; i++) {
            if (enabled) {
                total += values[i];
            }
        }
        return total;
    }

    static long sumShifted(int[] values) {
        long total = 0;
        for (int i = 0; i + 1 < values.length; i++) {
            total += values[i + 1];
        }
        return total;
    }

    static int firstAtLeast(int[] values, int threshold) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= threshold) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] a = {3, 5, 7, 11, 13, 17};
        System.out.println("full=" + sumRange(a, 0, a.length));
        System.out.println("middle=" + sumRange(a, 1, 5));
        System.out.println("empty=" + sumRange(a, 4, 4));
        System.out.println("enabled=" + sumIfEnabled(a, true));
        System.out.println("disabled=" + sumIfEnabled(a, false));
        System.out.println("shifted=" + sumShifted(a));
        System.out.println("firstAtLeast12=" + firstAtLeast(a, 12));
        try {
            sumRange(a, 2, 7);
            System.out.println("unexpected");
        } catch (IndexOutOfBoundsException expected) {
            System.out.println("badRangeRejected=true");
        }
        try {
            sumRange(null, 0, 0);
            System.out.println("unexpected");
        } catch (NullPointerException expected) {
            System.out.println("nullRejected=true");
        }
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac --release 17 LoopOptimizationDemo.java
java LoopOptimizationDemo
~~~

**Stdout đã biên dịch/chạy kiểm chứng trên HotSpot/OpenJDK 24 với target Java 17:**

~~~text
full=56
middle=36
empty=0
enabled=56
disabled=0
shifted=53
firstAtLeast12=4
badRangeRejected=true
nullRejected=true
~~~

### Phân tích đúng phạm vi

- `sumRange`: `Objects.checkFromToIndex` kiểm tra **contract range `[from,to)`** trước khi loop. Trong phạm vi hợp lệ, counted-loop có giới hạn rõ, thuận lợi cho optimizer suy ra `from ≤ i < to ≤ values.length`.
- `sumIfEnabled`: `enabled` là parameter không bị gán lại trong method nên là một loop-invariant candidate. C2 **có thể** unswitch hoặc tối ưu đường false, nhưng kết quả `enabled=56`/`disabled=0` **không chứng minh native code đã unswitch**.
- `sumShifted`: điều kiện `i+1 < values.length` bảo vệ index thực sự `i+1`; tổng 53 là 56 trừ phần tử đầu tiên 3. Việc viết rõ boundary giúp code dễ review.
- `firstAtLeast`: có `return` giữa loop. Nếu threshold 12, phần tử đầu tiên đạt là 13 ở index 4. Việc có early exit có thể giảm cơ hội vectorize hoặc một số loop transformations; không khẳng định C2 sẽ từ chối mọi optimization.
- Invalid range bị từ chối trước loop bằng `Objects.checkFromToIndex`; method ném subtype `IndexOutOfBoundsException`, được catch ở main.
- `null` bị từ chối bởi `Objects.requireNonNull` kể cả khi `[from,to)` rỗng. Đó là lựa chọn rõ ràng của API này, không phải behavior mặc định của mọi loop Java.

**Chú ý:** chương trình chỉ gọi mỗi method vài lần, thường **chưa đủ** để C2 JIT compile chúng. Output này kiểm tra **semantics**, không phải đo RCE throughput hay bằng chứng có vector instructions.

## 10. Phương thức kiểm tra và tối ưu ở hai tầng khác nhau

### `javap` nhìn thấy bytecode, không nhìn thấy C2 native machine code

Sau khi biên dịch:

~~~bash
javap -classpath . -c -p LoopOptimizationDemo
~~~

Bạn sẽ thường thấy các instructions như `arraylength`, `iaload`, `iinc`, `if_icmpge`, `goto`, và các lời gọi `Objects.checkFromToIndex` trong bytecode tương ứng.

Ví dụ logic:

~~~text
arraylength   → lấy độ dài array
if_icmpge     → nhảy khi index vượt giới hạn loop
iaload        → đọc int element theo index
iinc          → tăng induction variable
goto          → backedge vòng lặp
~~~

`javap` không cho biết compiler C2 đã loại implicit bounds check trong **native code** hay chưa. Đây là lỗi chẩn đoán thường gặp: thấy `iaload` trong bytecode rồi kết luận “mỗi iteration vẫn phải chạy y nguyên array bounds check” là suy luận quá xa.

### Code JIT có thể thay đổi trong cùng JVM process

Ban đầu method interpreted, rồi C1, sau đó C2 hoặc lại deoptimize/recompile. Một benchmark bắt đầu đo ngay từ lần đầu có thể cộng **compilation/profiling** vào thời gian chạy và đưa ra kết luận khác steady state.

## 11. Java 17 runnable: tạo hot loop và xem JIT compilation log

Tạo file **`LoopWarmupProbe.java`**:

~~~java
public final class LoopWarmupProbe {
    private static volatile long published;

    static long sumArray(int[] values) {
        long sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] values = {3, 5, 7, 11, 13, 17};
        long checksum = 0;

        for (int k = 0; k < 100_000; k++) {
            checksum += sumArray(values);
        }

        published = checksum;
        System.out.println("rounds=100000");
        System.out.println("checksum=" + published);
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 LoopWarmupProbe.java
~~~

Chạy thông thường:

~~~bash
java LoopWarmupProbe
~~~

**Stdout cố định:**

~~~text
rounds=100000
checksum=5600000
~~~

Vì mỗi lượt cộng `3+5+7+11+13+17 = 56`, 100.000 lượt cho 5.600.000. Kết quả đã được thực sự chạy với JDK 24 target Java 17.

Bật HotSpot diagnostic printing:

~~~bash
java -XX:+PrintCompilation LoopWarmupProbe
~~~

**Một quan sát thực tế** khi chạy trên HotSpot 24 có các compilation lines liên quan:

~~~text
LoopWarmupProbe::sumArray ... level 3
LoopWarmupProbe::sumArray ... level 4
LoopWarmupProbe::main ... OSR/normal compilation
~~~

Ba dòng trên là **tóm tắt ý nghĩa của diagnostic output**, không phải sao chép nguyên văn format JVM. Compilation IDs, timings, flags và tier activity có thể khác ở Java 17, OS và các runs. Trong lần thử, JVM thực sự ghi nhận `sumArray` ở compilation level 3 rồi level 4; điều này chứng minh nó được **biên dịch**, **không tự chứng minh** cụ thể bao nhiêu range checks bị loại trong native code.

### `-Xint` để đối chiếu semantics

~~~bash
java -Xint LoopWarmupProbe
~~~

Interpreter-only vẫn in **cùng** `rounds=100000` và `checksum=5600000`. Đây là kiểm tra hành vi. **Không biến một lần chạy `-Xint` so với JIT thành kết luận speedup có giá trị production**: đo thời gian cần warm-up/forks, nhiều samples và tránh loại work.

### Không đồng nhất `PrintCompilation` với `PrintInlining`

Trên HotSpot, `-XX:+PrintCompilation` cho biết những compilation events, còn `-XX:+PrintInlining` có thể cần unlock diagnostic options:

~~~bash
java -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintInlining -XX:+PrintCompilation \
     LoopWarmupProbe
~~~

Đây là cú pháp Bash. Các logs có thể lớn và tạo overhead. `PrintInlining` cho biết quyết định **inlining** chứ **không phải** một báo cáo đầy đủ, chính thức rằng bounds-check elimination thành công.

## 12. Chứng minh native optimization khó hơn nhìn latency

Có những mức bằng chứng khác nhau:

| Mức | Bằng chứng | Có thể kết luận? |
| --- | --- | --- |
| 1 | Code vẫn trả đúng khi `java` hoặc `java -Xint` | **Semantics** đúng trên inputs đã thử |
| 2 | `javap -c -p` cho thấy `iaload`, loop branches | **Bytecode** có array operation; chưa biết optimized native code |
| 3 | `-XX:+PrintCompilation` có C2 level 4 | Hot method có native compilation; chưa biết RCE/LICM/unswitch riêng |
| 4 | JFR/method profiler cho thấy thời gian/counter thay đổi | Có khác biệt hiệu năng quan sát được; chưa biết biến đổi nào gây ra |
| 5 | Phân tích C2 Ideal graph/assembly (nếu công cụ và build hỗ trợ) | Có thể khảo sát guards/checks/vector ops trong **compiled version** cụ thể |
| 6 | Benchmark đúng phương pháp với nhiều forks/inputs và profiling | Có dữ liệu để đánh giá hiệu năng, nhưng không suy ra codegen y hệt trên mọi JVM |

**Assembly/Ideal graph không mặc định có sẵn.** Tùy HotSpot build, việc in disassembly có thể cần `hsdis` hoặc công cụ chuyên biệt. Các flags `TraceLoopOpts`/`TraceLoopPredicate` trong source HotSpot có thể là **develop-only** và không chạy trong product release builds; không dán tùy tiện vào production JVM rồi hứa nó luôn hỗ trợ.

### Phân tích C2 graph

OpenJDK có source liên quan `opto/loopnode.cpp`, `loopTransform.cpp`, `loopPredicate.cpp` và compiler flags. Tuy nhiên cấu trúc source thay đổi theo JDK branch. Đọc code giúp hiểu thuật toán, nhưng muốn chứng minh phiên compiled method cụ thể phải kiểm tra build/flags/profile tương ứng.

Dấu hiệu JIT loop optimization trong assembly/IR cần xem gồm:

- Có còn array bounds comparison cho **mỗi iteration** trong main loop không?
- Có **pre-loop/guard/predicate** thay cho checks per element không?
- Có tail loop sau unrolling/vectorization để xử lý remainder không?
- Null/bounds guard có tồn tại ở fast path entrance hay fallback không?
- Deoptimization entry/uncommon trap bảo vệ assumptions nào?
- Code compiled version đó có thực sự được dùng trong workload đo không?

**Không đủ** chỉ nhìn một loop máy không có nhánh so sánh mà bỏ qua prologue, versioning guard hoặc uncommon trap ở địa chỉ khác.

## 13. Rào cản thường gặp đối với RCE, LICM và vectorization

### 13.1. Biểu thức index không tuyến tính hoặc phụ thuộc dữ liệu

~~~java
for (int i = 0; i < indexes.length; i++) {
    total += values[indexes[i]];
}
~~~

`i` hợp lệ cho `indexes`, nhưng `indexes[i]` có thể là bất kỳ `int`, kể cả âm hoặc quá `values.length`. Một optimizer không được bỏ bounds check cho `values[indexes[i]]` trừ khi có proof/phạm vi thích hợp.

### 13.2. Thay đổi references hoặc aliasing

~~~java
for (int i = 0; i < holder.data.length; i++) {
    callback(holder);
    sum += holder.data[i];
}
~~~

`callback` có thể thay `holder.data`, cập nhật state hoặc throw. Không được coi `holder.data` luôn trỏ cùng mảng. Nếu muốn contract snapshot, thay đổi source một cách có ý thức và kiểm tra thread safety.

### 13.3. Volatile và synchronization

Đọc `volatile` hoặc vào/ra `synchronized` có memory ordering semantics; JIT không được bỏ các lần đọc/ghi cần thiết rồi nói “giá trị hầu như không đổi”. Với parameter `boolean enabled` không bị thay đổi, unswitch có thể phù hợp hơn so với `volatile boolean enabled` được đọc trong loop.

### 13.4. Side effects trước truy cập có thể ném exception

Nếu `log(i)` hoặc `counter++` cần xảy ra trước lần array access lỗi, không thể chuyển hết validation lên đầu để ném trước những side effects ấy mà vẫn gọi hai source versions tương đương.

### 13.5. Early exits và exception paths

`break`, `continue`, `return`, `throw` giữa loop có thể tạo control-flow phức tạp, hạn chế một số biến đổi. Không tuyên bố “có `break` thì JIT không tối ưu” — thực tế có thể tối ưu một phần.

### 13.6. Overflow trong induction variable

Với `int` cộng/trừ theo semantics Java, các giá trị có thể overflow. Ví dụ **không an toàn**:

~~~java
for (int i = start; i <= end; i++) {
    process(i);
}
~~~

Nếu `end == Integer.MAX_VALUE` và loop đạt `i == MAX_VALUE`, `i++` wrap về `Integer.MIN_VALUE` rồi điều kiện `i <= end` tiếp tục đúng; loop có thể không kết thúc. Đây không chỉ là chuyện hiệu năng mà là **logic bug**.

Với cấu trúc có khả năng chạm integer maximum, nên thiết kế cận và kiểu index phù hợp, tránh overflow hoặc dùng loop exit condition đã chứng minh an toàn. **Không sửa máy móc `<=` thành `<`** nếu điều đó làm bỏ phần tử cuối mà business cần.

### 13.7. Floating-point reductions

Tổng `double` qua các iterations có thể không associative theo IEEE 754; reorder hoặc vectorize reductions phải tuân theo các ràng buộc floating-point semantics của Java version/VM. Không mặc định C2 được tự do thay đổi thứ tự cộng như với số thực toán học lý tưởng.

### 13.8. Short loops và code size

Hoist/predicate/unroll có giá nhập môn, tăng code size. Một vòng chỉ 2–3 iterations có thể chạy nhanh hơn với checks đơn giản, không cần tạo nhiều versions. **Cost model** là lý do compiler đôi khi không tối ưu một loop có vẻ đủ điều kiện trên lý thuyết.

## 14. Thiết kế source hỗ trợ compiler: rõ contract, không “ép JIT”

### 14.1. Dùng cận phù hợp với dữ liệu thật

Thường nên ưu tiên:

~~~java
for (int i = 0; i < a.length; i++) {
    use(a[i]);
}
~~~

thay vì tự tính length từ một nguồn không liên quan. Điều này giúp người đọc thấy ngay miền truy cập an toàn, và cung cấp loop form dễ phân tích cho JIT.

### 14.2. Validation ở API boundary khi đó là contract mong muốn

Nếu method yêu cầu `[from,to)` hợp lệ **trước bất kỳ side effect nào**, hãy kiểm tra rõ:

~~~java
Objects.requireNonNull(a);
Objects.checkFromToIndex(from, to, a.length);
for (int i = from; i < to; i++) {
    sum += a[i];
}
~~~

`Objects.checkFromToIndex` (Java 9+) minh định API contract, giúp code an toàn và dễ review. Dù vậy, nó **không bảo đảm C2 đã loại mọi internal check**, và việc thêm validation vào một method cũ có side effects có thể đổi semantics như mục 8.

### 14.3. Dùng local reference khi contract muốn snapshot

~~~java
int[] snapshot = holder.data;
for (int i = 0; i < snapshot.length; i++) {
    process(snapshot[i]);
}
~~~

Nếu mục tiêu là xử lý **một phiên bản array reference cố định**, cách này thể hiện ý định. Nếu mục tiêu phải thấy những thay đổi field ngay trong loop, local snapshot **không tương đương**.

### 14.4. Không viết unrolling bằng tay trước khi benchmark

Thủ công viết 8 accesses mỗi vòng làm code phức tạp, dễ lỗi tail/overflow, có thể phá optimizer hoặc tốn instruction cache. Hãy để JIT có cơ hội tự làm qua canonical loop trước; chỉ manual optimize nếu có measurement/JMH + inspection chứng minh và tests bảo vệ.

### 14.5. Không giảm checks bằng `Unsafe` chỉ vì benchmarks

Chuyển Java array sang raw native pointer để bỏ bounds safety là một bước rất khác về safety/lifetime, có thể gây memory corruption. Các flags nội bộ `Unsafe`/module access không phải giải pháp chung cho một loop chậm. Tối ưu algorithm, data layout, allocation, concurrency và I/O trước khi phá contract memory safety.

## 15. Benchmark đúng cách: nóng, đo đủ lâu, tránh compiler ảo thuật

Một vòng `System.nanoTime()` bao quanh 5 iterations không đủ để kết luận `a[i]` đã được RCE. Những yếu tố gây nhiễu:

- JIT chưa đủ hot, hoặc đang đổi C1→C2/OSR.
- GC xảy ra trong khoảng đo.
- CPU turbo, thermal throttling và OS thread scheduling.
- Dữ liệu quá nhỏ, nằm toàn trong cache; memory bandwidth chưa được thử.
- JIT constant-fold hoặc dead-code eliminate một phép tính không dùng kết quả.
- Branch predictability, aliasing và vectorization phụ thuộc input shape.
- `nanoTime()` overhead chi phối work cực ngắn.
- Benchmark tool có **coordinated omission** nếu dùng cho request latency ở server.
- Ngoại lệ hiếm và slow paths không được test.

### JMH được ưu tiên cho microbenchmark

Java Microbenchmark Harness (JMH) từ dự án OpenJDK quản lý **forks, warm-up iterations, measurement iterations, state, parameterization, result consumption** và thống kê. Ví dụ mẫu cấu trúc JMH:

~~~java
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
public class SumBenchmark {
    @Param({"64", "4096"})
    public int size;

    int[] values;

    @Setup(Level.Trial)
    public void setUp() {
        values = new int[size];
        for (int i = 0; i < size; i++) values[i] = i;
    }

    @Benchmark
    public long sumRange() {
        return LoopOptimizationDemo.sumRange(values, 0, values.length);
    }
}
~~~

**Đây không phải chương trình biên dịch với `javac` trần**: cần JMH dependencies/plugin và benchmark class trong cùng package hoặc điều chỉnh visibility cho `LoopOptimizationDemo.sumRange`. `@Param` kiểm tra nhiều kích thước array thay vì chỉ một length có lợi cho compiler. JMH vẫn không thay thế end-to-end load testing với real request mix.

Không so loops chỉ từ “ns/op thấp nhất” nếu một version làm ít work hơn, bỏ exception behavior hoặc sử dụng dữ liệu có layout khác. Cần cùng correctness, cùng setup, cùng CPU/JDK flags và được warm theo điều kiện đại diện.

### Một phương pháp so A/B nghiêm túc

1. Giữ JDK build, `-Xms/-Xmx`, CPU quota và machine workload giống nhau.
2. Dùng representative lengths (0, 1, 2, 3, 64, 4096, hàng triệu phần tử).
3. Test invalid range, null, early return, dữ liệu âm/dương, overflow edge.
4. Warm-up đủ cho method/branches cần đo, kiểm chứng compilation mode bằng logs.
5. Dùng nhiều process forks độc lập, thay đổi thứ tự ứng viên khi cần.
6. Đo cả throughput/latency, errors và GC/allocation, không chỉ avg timing.
7. Nếu khẳng định **RCE thực sự**, phân tích optimized IR/native code trên build cụ thể.
8. Báo kết quả đúng giới hạn: “thử nghiệm cho thấy nhanh hơn” khác “mọi JVM đều bỏ checks”.

## 16. Những tối ưu này có còn đúng khi method bị deoptimize?

JIT code là một **implementation choice** của JVM. Nếu một guard assumption không còn đúng, HotSpot có thể **deoptimize** và tiếp tục tại bytecode-level state tương đương, sau đó interpreter hoặc compiler khác xử lý.

Một ví dụ giả định:

~~~text
Warm-up:
  99.99% calls có from=0, to=a.length, a non-null
              |
              v
C2 dùng profile/guards tạo đường tối ưu
              |
Production:
  thỉnh thoảng to>a.length
              |
              v
guard/trap/fallback phải bảo toàn ngoại lệ và side effects
              |
              v
có thể deoptimize hoặc dùng slower path
~~~

Không nói rằng `Objects.checkFromToIndex` sẽ luôn bị chuyển thành một trap, hoặc mọi invalid call chắc chắn deoptimize. Compiler có nhiều lựa chọn phụ thuộc code shape, profile và JDK build.

**Correctness được giữ ở mọi tier.** Java không cho phép chương trình cho kết quả sai chỉ vì code đã “nóng”. Đây là lý do một test chạy đúng ở JIT level 4 không phải chứng minh performance optimization cụ thể, nhưng là điều kiện cần để đánh giá đúng đắn.

## 17. Checklist khi thấy một vòng lặp Java bị chậm

- [ ] Xác định **đúng loop hot** bằng profiler, không đoán từ code review.
- [ ] Kiểm tra thuật toán O(N), data volume và memory locality trước khi đổ cho bounds checks.
- [ ] Xem source indexes: có `i`, `i+1`, `2*i`, `positions[i]` và nhiều arrays khác nhau không?
- [ ] Xem **bounds thực**: đã chứng minh trên index expression/mảng đang dùng chưa?
- [ ] Xem potential integer overflow/negative indices.
- [ ] Xem array reference có stable không, có call/side effect/volatile/aliasing trong body không?
- [ ] Xem early break/return hoặc error behavior có làm transformations bị ràng buộc không?
- [ ] Không tự move null/bounds checks ra trước side effects nếu thay đổi contract.
- [ ] Dùng `javap` hiểu bytecode; dùng compilation logs để biết JIT activity.
- [ ] Không coi `PrintCompilation` là báo cáo RCE hoặc vectorization.
- [ ] Khi cần xác nhận optimization, dùng IR/native assembly tools có build phù hợp.
- [ ] Benchmark bằng JMH, nhiều forks/iterations, representative inputs.
- [ ] Dùng `-Xint` để đối chiếu semantics khi có ích, không gọi đó là “mô phỏng cold JIT”.
- [ ] Đo cả hiệu năng và correctness của invalid/null/overflow edge cases.
- [ ] Nếu tự đổi cấu trúc loop, lưu tests so phiên bản trước/sau với nhiều random seeds.
- [ ] Giữ safe APIs trừ khi có lý do native memory thật sự và audit riêng.

## 18. Tự kiểm tra: dự đoán điều gì được tối ưu trong điều kiện phù hợp?

### Câu 1

Với `for (int i=0; i<a.length; i++) sum+=a[i]`, kiểm tra nào **có cơ hội** được giảm? **Array bounds checks** trên đường compiled đã được chứng minh an toàn, không phải nghĩa vụ bảo đảm runtime sẽ làm.

### Câu 2

`a[i+1]` với loop limit `i<a.length` có được phép bỏ check vô điều kiện không? **Không**, iteration cuối có thể truy cập quá giới hạn; cần điều kiện/một chiến lược xử lý đúng.

### Câu 3

Hai arrays `a`, `b` với loop limit `a.length` có được bỏ `b[i]` bounds check vì `a[i]` hợp lệ không? **Không**, `b` có thể ngắn hơn hoặc null; cần chứng minh riêng.

### Câu 4

`values[indexes[i]]` có thuộc counted loop dễ RCE không? **Không mặc định**, vì các giá trị trong `indexes` có thể ngoài phạm vi của `values`.

### Câu 5

Khi C2 chuyển điều kiện ra trước loop, nó có thể thay đổi ngoại lệ được ném hoặc bỏ side effects bắt buộc không? **Không**.

### Câu 6

`Objects.checkFromToIndex` có tồn tại trong Java 17 không? **Có**, từ Java 9.

### Câu 7

Nếu `checkFromToIndex(2,7,6)` ném exception trước loop, có phải JIT “đã phát hiện lỗi” không? **Không**, đó là validation do source API yêu cầu.

### Câu 8

Có `iaload` trong `javap` thì native code C2 chắc chạy một check cho mỗi `iaload` không? **Không**, bytecode khác native optimized code.

### Câu 9

`PrintCompilation` có level 4, có thể kết luận RCE đã xảy ra? **Không**, chỉ kết luận có compilation ở tier tương ứng.

### Câu 10

Loop unswitching áp dụng với `if (enabled)` khi `enabled` là parameter stable? **Có thể**, nếu compiler thấy đáng và hợp lệ.

### Câu 11

`volatile` field đọc trong body có thể bị coi không đổi suốt loop vô điều kiện không? **Không**, memory semantics có thể đòi những lần đọc.

### Câu 12

Unroll factor 4 trên array length 9 có thể bỏ phần tử cuối không? **Không**, phải có path xử lý phần dư hoặc tổ chức tương đương.

### Câu 13

RCE và auto-vectorization có phải một phép tối ưu duy nhất không? **Không**; RCE xử lý checks, vectorization kết hợp operations dùng SIMD trong điều kiện phù hợp.

### Câu 14

`-Xint` cho cùng output nhưng chậm hơn có chứng minh optimized native code đã vectorize không? **Không**.

### Câu 15

Vì sao `for (int i=start; i<=Integer.MAX_VALUE; i++)` nguy hiểm? **`int` overflow** ở lần increment, có thể khiến loop không kết thúc.

### Câu 16

Loop có `break` thì C2 chắc chắn không tối ưu gì? **Không**, có thể tối ưu một phần nhưng control flow phức tạp hơn.

### Câu 17

`holder.array` thay đổi giữa iterations, local snapshot có luôn giữ semantics cũ không? **Không**, chỉ làm vậy khi contract cho phép cố định reference.

### Câu 18

Benchmark một array dài 1 phần tử có thể kết luận tối ưu tốt cho array 1 triệu phần tử? **Không**, memory bandwidth/cache/branch và transformation profitability khác.

### Câu 19

Nếu guard giả định thất bại, compiler được phép làm gì? **Chọn fallback/deoptimization hợp lệ**, vẫn giữ Java semantics.

### Câu 20

Điều gì quan trọng nhất khi sửa code vì performance? **Đo đúng bottleneck và không thay đổi correctness**, kể cả exception ordering và side effects.

## 19. Nguồn tham khảo để đào sâu

- **Java Language Specification 17**, §15.10 Array Access Expressions (điều kiện truy cập/ngoại lệ): https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.4
- **JVMS 17**, bytecode `iaload`, `iastore`, array access instructions: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
- **Java SE 17 `Objects.checkFromToIndex`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Objects.html#checkFromToIndex(int,int,int)
- **Oracle JDK 17 `javap` disassembler**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html
- **Oracle JDK 17 `java` launcher** (`-Xint`, diagnostic compilation flags, JIT tooling): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **OpenJDK JDK 17u, HotSpot C2 compiler flags** (ví dụ LoopUnroll, UseLoopPredicate, LoopUnswitching, SuperWord): https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/opto/c2_globals.hpp
- **OpenJDK HotSpot loop optimizations** — tài liệu giải thích counted loops, loop predication và range-check elimination: https://wiki.openjdk.org/display/HotSpot/Loop+optimizations+in+Hotspot+Server+VM+Compiler+(C2)
- **Red Hat Developer, Range check elimination in loops in OpenJDK's HotSpot JVM** (2022): https://developers.redhat.com/articles/2022/03/16/range-check-elimination-loops-openjdks-hotspot-jvm
- **OpenJDK JMH** — phương pháp benchmark Java đáng tin hơn `nanoTime` vòng lặp thủ công: https://openjdk.org/projects/code-tools/jmh/

**Kết luận:** HotSpot C2 có thể cải thiện các vòng lặp bằng cách chứng minh **miền chỉ số**, loại những **array bounds/null checks dư thừa**, tạo **loop predicates**, tách **pre/main/post**, hoist những điều kiện bất biến, **unswitch**, **unroll** hoặc **vectorize** trong những điều kiện thích hợp. Nhưng **mọi bước đều bị ràng buộc bởi Java semantics**: null, giới hạn mảng, overflow, exceptions, side effects, aliasing, concurrency và điều kiện dừng phải còn đúng. Hãy viết source có contract rõ, chọn canonical loop dễ hiểu, đo bằng JMH/workload thật, và chỉ khẳng định một biến đổi JIT cụ thể sau khi xem **optimized IR/assembly** của đúng JVM build — không suy ra từ `javap`, một dòng `PrintCompilation` hoặc một lần benchmark chạy nhanh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-005">Phân tích đối tượng thoát (escape analysis) và thay thế bằng giá trị thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# Phân tích đối tượng thoát (escape analysis) và thay thế bằng giá trị thành phần trong HotSpot

## 1. Một object xuất hiện trong Java source có bắt buộc được cấp phát trên heap?

Không nhất thiết. Java cho phép viết `new Point(...)` để tạo một đối tượng về mặt **ngữ nghĩa**, nhưng chương trình chỉ được quan sát những hành vi mà ngôn ngữ cam kết: giá trị fields, reference identity khi có thể quan sát, kết quả gọi method, exceptions, đồng bộ, v.v. JVM được quyền tối ưu implementation nếu vẫn bảo toàn các hành vi đó.

Trong **HotSpot/OpenJDK Java 17**, trình biên dịch tối ưu **C2** có thể dùng **escape analysis (EA)** để xác định một object được tạo ra có thể được quan sát ở ngoài phạm vi đang xét hay không. Nếu một object không thoát và thỏa điều kiện triển khai, **scalar replacement of aggregates (SRA)** có thể biến các thao tác trên object thành các thao tác trên **các giá trị thành phần**, chẳng hạn `x` và `y`. Khi đó allocation thực và một số load/store/lock có thể bị loại khỏi mã máy.

Ba khái niệm phải tách bạch:

1. **Escape analysis** là bước **phân tích** dòng dữ liệu và các đường tham chiếu để đánh giá object có thể thoát phạm vi nào.
2. **Scalar replacement** là một phép **biến đổi code**: thay object aggregate bằng những trường/giá trị vô hướng có thể theo dõi độc lập; khi thành công có thể **loại bỏ allocation**.
3. **Lock elimination** là phép bỏ đồng bộ không cần thiết khi compiler chứng minh monitor không tạo ra tác dụng quan sát được giữa threads; nó có liên hệ với EA nhưng **không đồng nghĩa SRA**.

Một lưu ý cực quan trọng: **HotSpot C2 Java 17 không nói chung biến những Java objects không thoát thành heap objects được cấp phát trên Java stack**. Khi scalar replacement thành công, object có thể **không được materialize** như một allocation ở execution path nóng; các giá trị field có thể nằm trong CPU registers, stack slots của compiled frame hoặc được tối ưu tiếp. Đây **khác** việc cấp một object hoàn chỉnh với header trên stack.

### Mục tiêu học tập

Sau bài này, có thể giải thích:

- Object thoát khỏi method, thread hoặc phạm vi compiled graph như thế nào.
- Vì sao `return new Point(...)` và `return p.x + p.y` dẫn tới cơ hội tối ưu khác nhau.
- C2 quan sát allocation/reference graph và dùng **inlining, alias/points-to analysis** làm cơ sở.
- Scalar replacement có thể xóa `new`, field loads/stores và giảm GC pressure như thế nào.
- Những gì compiler cần giữ khi có exceptions, side effects, identity, locks và deoptimization.
- Vì sao **không phải cứ không thoát là chắc chắn hết allocation**.
- Cách kiểm chứng bằng JMH, JFR, allocation counters và đúng HotSpot flags của JDK 17.

Mọi ví dụ Java bên dưới chạy được trên **Java 17+** nếu ghi đầy đủ file; chỉ các đoạn nói rõ là *fragment/pseudocode* mới không độc lập. Những kết quả nào phụ thuộc compiler/GC được ghi rõ **không có output duy nhất**.

## 2. Java heap, stack và các đối tượng “ảo” của optimizer

### Java-level semantics không phải memory layout

Ở Java:

```java
Point point = new Point(3, 4);
int sum = point.x + point.y;
```

Đây là *fragment* minh họa. Về ngữ nghĩa, chương trình khởi tạo một `Point` và đọc hai fields. Nhưng compiler có thể chứng minh không một phần code khác cần nhận diện `point` như một object độc lập.

Sơ đồ trước tối ưu:

```text
Bytecode / mô hình object:
  new Point
  dup
  iconst_3
  iconst_4
  invokespecial Point.<init>
  astore point
  aload point
  getfield x
  aload point
  getfield y
  iadd

  -> sum
```

Sơ đồ sau tối ưu **minh họa**:

```text
  point.x -> scalar x = 3
  point.y -> scalar y = 4

  sum = x + y = 7

  Object header/allocation không cần tồn tại trên hot path
  nếu compiler đã chứng minh toàn bộ điều kiện an toàn.
```

Đây **không phải machine-code disassembly** của một lần chạy đã được xác minh. C2 có thể còn constant-fold `3 + 4` thành `7`, hoặc inlining/escape analysis đã không chạy khi program còn cold.

### Cần phân biệt 4 thứ

| Thành phần | Giải thích |
| --- | --- |
| **Java heap object** | Thực thể runtime được GC quản lý khi object thật sự được cấp phát |
| **Java reference** | Giá trị chương trình dùng để tham chiếu object; không có raw address API chuẩn |
| **Compiled frame/register values** | Nơi CPU/JIT có thể lưu giá trị `x`, `y` hoặc intermediate data |
| **Virtual/scalar-replaced object** | Biểu diễn **logic** trong optimizer, có thể không có allocation vật lý tương ứng trên hot path |

Không thể đo “Java object đã nằm ở stack địa chỉ nào” chỉ bằng `new` trong source. Lập trình viên không kiểm soát được layout ấy và không nên tự đặt yêu cầu “mọi short-lived object phải stack allocate” trên HotSpot Java 17.

## 3. “Escape” là thoát khỏi đâu?

Có ít nhất hai ranh giới quan trọng:

- **Method escape:** reference tới object được tạo ra có thể tồn tại hoặc bị sử dụng ngoài activation/analysis scope của method.
- **Thread escape:** object có thể được quan sát/truy cập bởi thread khác, thường qua field của object được chia sẻ, static reference hoặc một cơ chế xuất bản tương đương.

Ví dụ:

```java
final class Pair {
    int a, b;
    Pair(int a, int b) {
        this.a = a;
        this.b = b;
    }
}
```

Đây là class minh họa không có `main`. Các tình huống:

```java
static int localOnly(int x) {
    Pair p = new Pair(x, 2);
    return p.a + p.b;
}

static Pair returnsObject(int x) {
    return new Pair(x, 2);
}
```

Hai methods là *fragments*, có thể đặt trong một class bao ngoài.

- Ở `localOnly`, nếu mọi sử dụng đã biết, `p` có cơ hội rất tốt để được scalar-replace.
- Ở `returnsObject`, caller nhận một **reference** tới Pair. Ở biên method độc lập, Pair có vẻ escape. Tuy nhiên nếu caller **được inline vào cùng compiled graph** và chỉ đọc fields thì compiler vẫn có thể loại bỏ allocation xuyên qua method boundary.
- Nếu caller lưu reference tới static field hoặc return ra môi trường không được inline/analyzed thì object thường cần materialize.

**Escape có tính phạm vi phân tích.** Một object thoát một method nhỏ không nhất thiết thoát toàn **inlined compilation graph**; và object không qua thread khác vẫn có thể phải heap-allocate để giữ reference identity hoặc do compiler không đủ khả năng chứng minh điều kiện tối ưu.

## 4. Các trạng thái escape thường dùng trong tài liệu HotSpot

Tài liệu HotSpot Server Compiler mô tả các trạng thái phân tích quen thuộc:

| State | Cách hiểu thực hành | Ví dụ mô hình |
| --- | --- | --- |
| **NoEscape** | Compiler chứng minh candidate không thoát và có thể là scalar-replaceable khi đáp ứng các điều kiện liên quan | Tạo `Point`, chỉ đọc fields rồi return `int` |
| **ArgEscape** | Object đi qua argument/callee boundary nhưng không bị kết luận globally escape trong phân tích tương ứng | Truyền vào helper có hành vi được compiler xác định |
| **GlobalEscape** | Object có thể đi ra ngoài phạm vi kiểm soát/thread hoặc tồn tại ở nơi external code truy cập | Lưu vào static field, returned reference khỏi graph, publish sang shared object |

**Không được suy “NoEscape = chắc chắn heap allocation bị xóa”.** Đây là **kết quả phân tích** và cần thỏa thêm điều kiện của scalar replacement pass. Kích thước object, shape của graph, merges, arrays, calls và compiler heuristics có thể ngăn phép biến đổi.

Cũng không nên xem tên `GlobalEscape` như cách mô tả đầy đủ mọi code path ở source: C2 dùng các tập dữ liệu/flow-insensitive analysis và có thể đưa ra kết luận **bảo thủ** khi có một nhánh đủ điều kiện thoát.

## 5. Đường tham chiếu nào làm object thoát?

Những cách thường gặp:

### Trả reference

```java
static Pair makePair(int a, int b) {
    return new Pair(a, b);
}
```

Caller có thể giữ Pair. Nếu optimizer không thấy caller hoặc caller thực sự dùng reference lâu dài, allocation thường phải tồn tại. Nhưng nếu caller inline, kết quả chỉ tính `pair.a + pair.b` thì tối ưu vẫn có thể xảy ra.

### Gán vào static field

```java
static volatile Object published;

static void publish(int a) {
    published = new Pair(a, 10);
}
```

Đây là **fragments**. Value trong `published` được publish tới code khác. Nếu lời gán có thể quan sát, compiler không thể chỉ giữ `a`/`b` ở registers của method rồi làm như object reference đã thực sự tồn tại ở static field.

### Lưu vào object khác đã thoát

```java
static final class Holder {
    Object value;
}
static void attach(Holder external, int x) {
    external.value = new Pair(x, 1);
}
```

Nếu `external` có thể được code khác truy cập sau call, Pair cũng trở nên accessible qua holder. Kết luận escape tùy phân tích receiver/holder và compilation graph.

### Chuyển tới thread khác / queue / callback

```java
static void submit(java.util.concurrent.Executor executor, int id) {
    Pair p = new Pair(id, id + 1);
    executor.execute(() -> System.out.println(p.a + p.b));
}
```

Đây là fragment. Executor có thể xử lý lambda khi method `submit` đã kết thúc; lambda capture `p`, nên compiler phải giữ ngữ nghĩa reference cùng dữ liệu xuyên lifecycle đó. Tuy nhiên compiler có thể tối ưu các phần nội bộ của lambda/Pairs trong những điều kiện hẹp; không khẳng định luôn có chính xác hai allocations (Pair và lambda) cho mọi compiler.

### Gọi method chưa được compiler phân tích đủ

```java
static int passUnknown(Pair p, java.util.function.ToIntFunction<Pair> f) {
    return f.applyAsInt(p);
}
```

Nếu callsite tới implementation cụ thể chưa được inline hoặc có thể publish Pair, optimizer có thể phải bảo thủ. **Không phải bất kỳ argument truyền method đều tự động GlobalEscape**; static/interprocedural analysis hoặc inlining có thể chứng minh callee chỉ đọc field.

## 6. C2 dựa vào points-to / connection graph

Trong HotSpot C2, Escape Analysis dùng cấu trúc phân tích **connection graph (points-to/alias relationships)** trên compiler IR để theo dõi allocations và các tham chiếu có thể trỏ tới object nào.

Mô hình:

```text
new Pair(3, 4)
     |
     v
  P: object allocation node
    / \
   v   v
 field a field b
   |     |
   3     4

Local reference p ----------> P
Static/reference escape? ---> không thấy trong compiled graph

=> P có thể NoEscape / scalar-replaceable
```

Nếu reference `p` được gán vào static/returned field, graph chứa thêm edge tới escape boundary:

```text
Local p -> P -> static published
                      |
                      v
                reachable beyond scope

=> Compiler phải tính đến khả năng P escape
```

Đây là sơ đồ **khái niệm**, không phải định dạng nội bộ chính xác của `escape.cpp`. C2 phải xử lý aliasing, phi nodes, field loads/stores, nested objects, method inlining và exception paths. Đôi lúc một path có vẻ local ở source nhưng compiler không chứng minh được do merge nhiều references.

### Flow-insensitive có ý nghĩa gì?

Một giới hạn đã được OpenJDK phân tích: thuật toán EA truyền thống của C2 là **flow-insensitive** ở những khía cạnh quan trọng. Nếu cùng một allocation có **một nhánh escape**, compiler có thể conservatively coi nó escape ngay cả trên đường đi thường xuyên không thoát.

```java
static volatile Object shared;
static int conditionalEscape(int x, boolean publish) {
    Pair p = new Pair(x, 2);
    if (publish) {
        shared = p;
    }
    return p.a + p.b;
}
```

Đây là fragment. Về mặt logic, khi `publish=false`, object không cần được lưu lại ngoài method. Nhưng **không tự kết luận** HotSpot Java 17 sẽ phát sinh fast path không allocation cho riêng nhánh false. Partial escape analysis cần phép biến đổi sâu hơn, và implementation C2 có các hạn chế; các update JDK 17u khác nhau có thể cải thiện một số trường hợp cụ thể.

Không lấy lời nói “EA phân tích tất cả possible paths” làm bảo đảm **path-sensitive partial allocation** đã có cho mọi hình dạng code.

## 7. Scalar replacement of aggregates (SRA): thao tác từng field

Giả sử:

```java
static final class Point {
    int x;
    int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

static int compute(int input) {
    Point p = new Point(input, input + 1);
    p.x += 2;
    return p.x * p.y;
}
```

Đây là fragments, chưa có `main`. Các field operations có thể được biểu diễn:

```text
Original logical object:
  p = new Point(input, input+1)
  p.x = p.x + 2
  result = p.x * p.y

Scalar form:
  x = input
  y = input + 1
  x = x + 2
  result = x * y

Nếu không phải materialize p, loại bỏ allocation/header/field memory ops.
```

Chú ý compiler có thể đặt `x`, `y` trong registers hoặc stack slots tùy register allocator. Không có lời hứa rằng “mỗi field trở thành đúng một Java local variable trên stack”. Cũng không có lời hứa C2 sinh chính xác cùng số machine instructions như sơ đồ.

### Vì sao giảm allocation giúp GC?

Nếu mỗi request tạo hàng ngàn temporary wrappers/points nhỏ và optimizer loại bỏ nhiều allocations:

- Giảm bytes allocated trên Java heap.
- Giảm số object headers và các initialization/write operations liên quan.
- Giảm áp lực vào Eden/TLAB và có thể giảm young GC frequency.
- Giảm lượng data GC phải scan hoặc copy nếu object thật sự không tồn tại.
- Cho phép compiler tiếp tục constant propagation, dead-store elimination và strength reduction.

Nhưng **không phải mọi allocation giảm đều chuyển thành tốc độ tăng tương ứng**. HotSpot TLAB allocation vốn rất nhanh; bottleneck khác như I/O, CPU branch misprediction hoặc memory access có thể chiếm nhiều hơn.

## 8. Java 17 hoàn chỉnh: object tạm chỉ dùng để tính số

Tạo file **`ScalarPointDemo.java`**:

```java
public class ScalarPointDemo {
    static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    static int score(int input) {
        Point p = new Point(input, input + 1);
        return p.x * 2 + p.y;
    }

    public static void main(String[] args) {
        System.out.println("score3=" + score(3));
        System.out.println("score10=" + score(10));
    }
}
```

Biên dịch/chạy Java 17:

```bash
javac --release 17 ScalarPointDemo.java
java ScalarPointDemo
```

**Stdout chính xác:**

```text
score3=10
score10=31
```

Công thức `2×input + (input+1) = 3×input+1`. Object `Point` không thoát khỏi `score` theo Java source; về nguyên tắc phù hợp cho EA/SRA sau khi JIT compile/inline. **Hai dòng output không chứng minh `new Point` đã bị loại bỏ.** Có thể JVM chưa kịp C2 compile, hoặc compiler đã thực hiện các optimization khác.

Nếu muốn học riêng tác động của EA, cần đo trong workload lặp lại đủ dài và so flags/metrics trên cùng JVM; không đưa bất kỳ con số “object allocations = 0” nào như sự thật mặc định.

## 9. Quan hệ giữa inlining và escape analysis

Inlining thay method call bằng logic callee trong **compiled graph**. Điều này có thể giúp EA nhìn thấy một object chỉ được dùng để đọc fields, dù đã được truyền qua ranh giới method source.

Ví dụ:

```java
static int consume(Pair p) {
    return p.a + p.b;
}

static int calculation(int x) {
    Pair temporary = new Pair(x, x + 1);
    return consume(temporary);
}
```

Đây là fragments. Nếu `consume` được inline, optimizer có thể thấy:

```text
new Pair(x, x+1)
    |
    +--> load a
    +--> load b
    |
    v
return a+b

=> Pair allocation có thể scalar-replace nếu không escape.
```

Nếu `consume` quá lớn, có lời gọi unknown/native hoặc **không được inline**, compiler có thể khó chứng minh properties cần thiết. C2 có một số khả năng interprocedural EA hạn chế, nhưng **không** được giả định optimizer nhìn xuyên mọi method/library boundary.

### Không phải inlining lúc nào cũng tốt

C2 có **inline budgets**, giới hạn method size/call depth và phải cân bằng compiled code size, compile time, CPU instruction cache và runtime throughput. Ép inline quá nhiều có thể làm compiled methods phình to, làm EA graph phức tạp và giảm hiệu quả ở vùng code khác.

Vì thế câu “cứ tách method nhỏ thì escape analysis thất bại” cũng sai: inlining có thể nối chúng lại; ngược lại, một method rất lớn với phức tạp control flow không có nghĩa EA sẽ thành công.

## 10. Object không thoát vẫn có thể không được scalar-replace

Những trở ngại thường gặp (không phải điều kiện đủ để **chắc chắn** thất bại trong mọi release):

- **Control-flow merges:** cùng một biến reference sau `if` có thể trỏ đến hai allocations khác nhau; compiler cần xử lý phi nodes/aliasing.
- **Conditional escape:** object chỉ thoát một nhánh, nhưng flow-insensitive EA có thể đánh giá bảo thủ.
- **Non-inlined calls:** object truyền tới method chưa được phân tích đủ hoặc native call.
- **Loop-carried object state:** một object được cập nhật/truyền qua nhiều iterations với references/merges phức tạp.
- **Identity-sensitive behavior:** object thật sự cần reference identity, monitor, identity hash hoặc tương tác native/reflection.
- **Arrays:** variable index access, unknown sizes, aliasing hoặc array-copy semantics có thể làm scalarization khó hơn.
- **Large/nested object graphs:** chi phí biểu diễn từng field/element có thể vượt compiler limits.
- **Deoptimization state:** JIT phải bảo đảm có đủ thông tin để phục hồi object với fields/identity đúng khi cần.
- **Escape through exceptions:** một exception object hoặc cause/stack trace được return/throw có thể có lifetime và observation khác temporary math objects.
- **JVMTI/agent/debug instrumentation:** một số runtime observations/flags có thể thay đổi những optimization có thể áp dụng.

**NoEscape không phải code-generation guarantee.** Một object nhỏ không thoát vẫn có thể được heap-allocated nếu C2 chưa chạy, compiler budget không cho phép hoặc analysis bảo thủ.

### Control-flow merge minh họa

```java
static int mergeExample(boolean chooseNew, int x) {
    Pair p = new Pair(1, 2);
    if (chooseNew) {
        p = new Pair(x, x + 1);
    }
    return p.a;
}
```

Đây là fragment. Tại `return`, `p` có thể là object cũ hoặc mới. Một optimizer lý tưởng có thể biến code thành `chooseNew ? x : 1`; nhưng **không thể khẳng định C2 Java 17 luôn scalar-replace cả hai allocations**. Bản cập nhật JDK 17 khác nhau có thể có những khả năng xử lý merge khác nhau; đọc đúng JDK build và test thay vì thuộc lòng một danh sách “C2 không bao giờ”.

## 11. Lock elimination: bỏ đồng bộ vô ích khi object không chia sẻ

Java `synchronized` có thể áp dụng với một object không bao giờ được chia sẻ sang thread khác:

```java
static int localLock(int x) {
    Object privateLock = new Object();
    synchronized (privateLock) {
        return x + 1;
    }
}
```

Đây là method fragment. Nếu compiler chứng minh không thread khác có thể quan sát `privateLock` và mọi semantics khác được bảo toàn, HotSpot C2 có thể **loại bỏ thao tác locking/unlocking** không có tác dụng cạnh tranh thực.

**Điểm cần phân biệt:**

- EA phát hiện mức độ object có thể escape.
- Lock elimination loại bỏ một số monitor-enter/exit của compiled code khi hợp lệ.
- Scalar replacement có thể đồng thời loại bỏ allocation của lock object, nhưng **hai phép tối ưu không đồng nghĩa**.
- Không có bảo đảm Java compiler sẽ bỏ `synchronized` trong mọi trường hợp.
- Khi lock monitor là object có thể chia sẻ/cần phối hợp threads, compiler **không** được bỏ tác dụng đồng bộ yêu cầu bởi Java Memory Model.

### Vì sao synchronized vẫn phải đúng về exceptions?

Java `synchronized` cần bảo đảm monitor được giải phóng khi block kết thúc hoặc ném exception. Nếu JIT chứng minh lock hoàn toàn thread-local và tối ưu, nó vẫn phải giữ mọi **Java-visible result và exception behavior**, kể cả khi code đã deoptimized hoặc chuyển execution representation.

Đừng “tối ưu thủ công” bằng cách xóa synchronized trên shared data chỉ vì từng thấy JIT lock elimination trong một microbenchmark. JMM vẫn áp dụng đầy đủ.

## 12. Java 17 hoàn chỉnh: đồng bộ qua object riêng tư

Tạo file **`PrivateLockDemo.java`**:

```java
public class PrivateLockDemo {
    static int guardedSum(int a, int b) {
        Object lock = new Object();
        synchronized (lock) {
            return a + b;
        }
    }

    public static void main(String[] args) {
        System.out.println("sum=" + guardedSum(10, 32));
        System.out.println("same=" + (guardedSum(1, 2) == 3));
    }
}
```

```bash
javac --release 17 PrivateLockDemo.java
java PrivateLockDemo
```

**Stdout chính xác:**

```text
sum=42
same=true
```

Lệnh `synchronized` là Java code hợp lệ; việc compiler có loại bỏ monitor runtime hay không **không quan sát trực tiếp từ stdout**. Dùng debug/compiler diagnostics hoặc benchmarking có kiểm soát nếu mục tiêu là xác nhận lock elision, không dựa vào thời gian của một lần gọi.

## 13. Identity, hash code và hành vi quan sát object

Một temporary object chỉ dùng để đọc hai fields có vẻ có thể scalar-replace. Nhưng có những operations làm **identity** thành một phần observable semantics:

```java
static boolean sameReference(Object x, Object y) {
    return x == y;
}
```

Đây là fragment. Khi identity được dùng, HotSpot vẫn có thể tối ưu một số tình huống bằng phân tích aliasing mà **không bắt buộc materialize object**, nhưng phải bảo toàn:

- `a == b` trả đúng nếu cả hai references biểu diễn cùng logical object.
- Hai objects khác biệt về identity không được biến thành một object do scalar replacement.
- `System.identityHashCode(obj)` có semantics ổn định với **cùng object** (không phải raw memory address), và có thể tác động tới khả năng eliminate allocation.
- `synchronized(obj)`, `wait`, `notify`, JNI handles, identity-sensitive reflection/agent observations cần đúng contract.

**Tránh khẳng định quá mức:** “gọi `identityHashCode` luôn ngăn EA” chưa chắc đúng với mọi optimizer, và “object không thoát thì mọi identity operation đều miễn phí” cũng sai. Chỉ khi đã hiểu compiler codegen và deoptimization obligations mới có kết luận cho JDK/build cụ thể.

### Java object không trở thành giá trị (value type) chỉ vì SRA

Một Java class bình thường vẫn có **reference identity semantics**. Scalar replacement là lựa chọn internal codegen của JIT cho **một execution context**, không làm class thành primitive type, record value type không identity, hay biến đổi API contract.

## 14. Deoptimization: khi object ảo phải có hình dạng Java trở lại

HotSpot C2 thường tối ưu dựa vào dynamic profile và giả định có guards. Nếu một assumption không còn đúng, JVM có thể thực hiện **deoptimization** để khôi phục execution state tương thích với interpreter/compiled tier khác.

Giả sử trong optimized code:

```text
Java source:
  Point p = new Point(3, 4);
  int total = p.x + p.y;

Optimized state:
  virtual Point {x=3, y=4}
  total = 7
  không có object heap ở đường hot
```

Nếu deoptimization cần local `p` tiếp tục tồn tại với Java semantics, VM có thể phải **materialize/reallocate** object tương ứng dựa trên metadata ghi lại các fields và references.

```text
Nmethod optimized execution
  |
  | uncommon trap / invalidated assumption
  v
Deopt metadata:
  virtual object Point(x=3,y=4)
  |
  v
materialization / reconstruction khi cần
  |
  v
Interpreter-compatible frame, đúng Java behavior
```

**Hai nhầm lẫn cần tránh:**

1. Không phải mọi scalar-replaced object “nằm trên stack” từ đầu, rồi deopt chỉ sao chép nguyên object ấy ra heap. Có khi **không tồn tại object vật lý trước deopt**.
2. Không phải bất cứ deoptimization nào cũng chắc chắn tạo lại một object. Compiler/runtime chỉ làm những thao tác cần thiết để bảo toàn semantics của **trạng thái sống** phải phục hồi.

Deoptimization cost có thể bao gồm reconstruct states, materialize virtual objects và chuyển tier. Vì vậy một optimization tốt trên hot path vẫn có thể gây spike nếu speculative assumptions đổi thường xuyên.

## 15. Ví dụ Java 17: escape khi trả object, không escape khi chỉ trả scalar

Tạo file **`EscapePathDemo.java`**:

```java
public class EscapePathDemo {
    static final class Pair {
        final int left;
        final int right;

        Pair(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    static int computeScalar(int value) {
        Pair pair = new Pair(value, value + 2);
        return pair.left + pair.right;
    }

    static Pair createReference(int value) {
        return new Pair(value, value + 2);
    }

    public static void main(String[] args) {
        int a = computeScalar(5);
        Pair b = createReference(5);
        System.out.println("scalar=" + a);
        System.out.println("pair=" + (b.left + b.right));
        System.out.println("sameResult=" + (a == b.left + b.right));
    }
}
```

```bash
javac --release 17 EscapePathDemo.java
java EscapePathDemo
```

**Stdout chính xác:**

```text
scalar=12
pair=12
sameResult=true
```

Đây là cách minh họa **khác biệt API** giữa method trả `int` và method trả object. `createReference` trả object về caller nên về mặt method boundary có escape. Tuy nhiên, nếu JIT **inline cả createReference và các phép đọc fields trong main** thì nó vẫn có thể loại allocation trong compiled graph. Vì vậy chương trình **không chứng minh** chính xác method nào đã thật sự heap-allocate.

Không dùng `System.identityHashCode` để đọc địa chỉ object nhằm “chứng minh” object nằm trên stack hoặc heap.

## 16. Interprocedural EA và những giới hạn của call graph

C2 có thể phân tích một số callees để hiểu object arguments mà không nhất thiết phải inline toàn bộ, nhưng phạm vi vẫn có giới hạn về bytecode, call shape, virtual dispatch và compiler budget.

**Inlining đặc biệt quan trọng** vì nó mở rộng compiled graph, giúp optimizer thấy toàn bộ các uses của candidate object. Khi code có polymorphic dispatch, reflection, JNI hoặc native call, compiler khó chứng minh caller-owned temporary object không thoát.

Ví dụ:

```java
interface Consumer {
    int use(Pair pair);
}

static int callThroughInterface(Pair pair, Consumer consumer) {
    return consumer.use(pair);
}
```

Fragment này có khả năng receiver `consumer` thay đổi runtime type; C2 có thể devirtualize/inlining nhờ type profile/CHA trong một số cases nhưng không hứa luôn thành công.

Không viết rằng “object truyền vào phương thức khác chắc chắn thoát”, vì điều đó sai khi callee được inline hoặc compiler biết nó chỉ đọc fields. Cũng không viết “compiler luôn nhìn xuyên mọi call”, vì điều đó quá lạc quan.

## 17. Scalar replacement cho arrays có khác objects fields không?

Đối với một object có một số fields kích thước cố định, optimizer có thể dễ thay từng field bằng SSA values. Với arrays, compiler phải xử lý:

- **Array length:** có được biết hoặc giới hạn hợp lý?
- **Index:** là constant, small known range hay biến động khó chứng minh?
- **Aliasing:** nhiều references trỏ cùng array, array được truyền qua calls nào?
- **Bounds checks:** có thể loại mà vẫn giữ exception semantics?
- **Arraycopy/clone:** có semantics và lifetime phù hợp để scalarize không?
- **Object/reference elements:** liệu objects bên trong cũng escape hoặc cần giữ identity không?
- **Loop-carried state:** array bị cập nhật qua iterations có làm graph phức tạp không?

Một temporary `int[]` kích thước nhỏ với constant indexes có thể là candidate. Nhưng **không có bảo đảm HotSpot Java 17 tự xóa mọi `new int[3]`**; constraints/optimizations phụ thuộc build và compiled graph.

### Ví dụ chỉ minh họa cơ hội tối ưu

```java
static int fromSmallArray(int x) {
    int[] values = {x, x + 1, x + 2};
    return values[0] + values[2];
}
```

Đây là fragment. Nếu compiler có đủ thông tin, có thể đơn giản hóa thành `2*x + 2`. Không dùng code này để hứa `int[]` không tồn tại trong heap của mọi Java implementation.

## 18. C2 EA truyền thống có hạn chế về flow merges và vòng lặp

Theo báo cáo nghiên cứu OpenJDK về escape analysis:

- Một allocation chỉ escape ở **một nhánh** vẫn có thể bị xét bảo thủ.
- **Phi/merge** giữa hai references khác allocations có thể hạn chế scalar replacement.
- **Loop-carried allocations** và values đi qua iterations có thể khó optimize.
- **Non-inlined helpers**, bytecode size limits và call graphs phức tạp có thể ngăn candidate NoEscape.
- Những cải tiến như **allocation merge reductions** ở một số cập nhật JDK 17u có thể thay đổi kết quả cho pattern cụ thể; phải khóa **17.0.x vendor/build** để kết luận.

Điều này giải thích vì sao hai snippets nhìn “cùng không thoát” trên source lại có allocation rate rất khác trên JMH.

### Flow-insensitive không có nghĩa không phân tích control flow gì cả

Nó là cách mô tả **đặc tính thuật toán EA** và nơi compiler phải đưa ra kết luận bảo thủ, không có nghĩa C2 hoàn toàn không biết `if`, SSA, branches hoặc types. Compiler còn nhiều passes khác có thể tối ưu logic dù EA/SRA không xử lý hết pattern.

## 19. Flags liên quan trên HotSpot Java 17

Những flags hay dùng trong thí nghiệm về EA/SRA:

| Flag | Vai trò khái quát |
| --- | --- |
| `-XX:+DoEscapeAnalysis` | Cho phép EA; thường bật mặc định với C2 phù hợp |
| `-XX:-DoEscapeAnalysis` | Tắt EA để so sánh có kiểm soát |
| `-XX:+EliminateAllocations` | Cho phép allocation elimination/SRA khi điều kiện đạt |
| `-XX:-EliminateAllocations` | Tắt phần allocation elimination nhằm cô lập ảnh hưởng |
| `-XX:+EliminateLocks` | Cho phép một số lock elimination nếu hợp lệ |
| `-XX:-EliminateLocks` | Tắt lock elimination để đối chiếu |
| `-XX:+PrintCompilation` | Quan sát compilation events/tiers (không nói allocation đã bị xóa) |
| `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` | Quan sát inlining decisions trên build hỗ trợ |
| `-XX:-TieredCompilation` | Thay đổi chiến lược tiering; không phải công tắc đơn giản bật EA |

**Không giả định mọi HotSpot build chấp nhận đúng mọi flag ở mọi chế độ**. Kiểm tra flags ở runtime đang chạy:

```bash
java -XX:+PrintFlagsFinal -version
```

Lọc tên `DoEscapeAnalysis`, `EliminateAllocations` và `EliminateLocks` bằng công cụ phù hợp OS. Không hard-code một default qua mọi JDK distributions.

**Tránh hướng dẫn bật `-XX:+PrintEscapeAnalysis` hay `-XX:+PrintEliminateAllocations` như lệnh chắc chạy trên product HotSpot builds.** Những option này thường chỉ khả dụng trong **non-product/debug builds**; trên production VM có thể bị từ chối. Dùng JMH/allocation profiling/assembly diagnostics được hỗ trợ và ghi rõ build.

## 20. Vì sao không nên đo EA bằng một lần System.nanoTime?

Giả sử:

```java
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    new Point(i, i + 1);
}
System.out.println(System.nanoTime() - start);
```

Đây là fragment **không phải benchmark đáng tin**:

- **Dead-code elimination:** object không được dùng; compiler có thể loại toàn bộ logic rất dễ.
- **Constant folding**: input và tính toán có thể được tính trước.
- **Loop optimizations**: unrolling, elimination hoặc vectorization làm số operation khác source.
- **JIT warmup:** test ngắn đo interpreter/C1/C2 compilation lẫn nhau.
- **GC/OS scheduling** làm latency thay đổi theo hệ thống.
- **Object allocation** trên TLAB rất nhanh; chênh lệch một loop đơn có thể nhỏ và sai số lớn.
- **Disable EA flag** có thể ảnh hưởng nhiều optimization khác, không thể mặc định mọi delta CPU là “thời gian allocate Point” thuần.

Dùng **JMH** với forks/warmup/measurement/Blackhole hoặc return value có thể quan sát. Kết hợp **allocation rate per operation**, CPU time và latency, thay vì chỉ một con số nanoseconds.

## 21. Mô hình phép đo: so hai JVM configurations

Khi nghiên cứu EA, có thể thực hiện **hai lần chạy riêng biệt cùng workload**:

```bash
java -XX:+PrintCompilation ScalarPointDemo
java -XX:-DoEscapeAnalysis ScalarPointDemo
```

Các lệnh trên chạy ví dụ logic nhưng **không** cho benchmark được: chương trình kết thúc rất nhanh, chưa chắc có C2 compiled hot path. Dùng một JMH benchmark thích hợp và chạy nhiều forks để so:

- A: default EA on.
- B: `-XX:-DoEscapeAnalysis`.
- C nếu cần: `-XX:-EliminateAllocations` và giữ EA theo VM build.
- Các flags khác, heap, GC, threads, machine load tương đương.
- Có warmup đủ để cả hai chạy ở mức compilation so sánh được.

**Kết luận đúng:** nếu A có allocation bytes/op thấp hơn B trong benchmark đủ tin cậy thì EA/SRA có thể góp phần, nhưng cần xem effects khác, codegen và compiler diagnostics. Không coi mọi chênh lệch do duy nhất “Point trên stack”.

### Đơn vị bytes/op cần hiểu

- Allocation profiling của JFR có thể là sampled events, **không chắc** đếm mọi allocation chính xác từng byte.
- Per-thread allocated-bytes counters từ `com.sun.management.ThreadMXBean` (khi supported/enabled) có semantics theo thread, không giống process RSS/heap used.
- Heap used snapshots giảm do GC; **delta used heap không phải cumulative allocated bytes**.
- JMH `gc.alloc.rate.norm` có thể giúp định lượng bytes/op theo profiler phù hợp, nhưng vẫn là công cụ/implementation-specific measurement cần warmup và forks.
- Allocation “0 B/op” được quan sát **trong một benchmark cụ thể** không phải định luật rằng mọi call production đều không allocate.

## 22. Ví dụ đo allocated bytes bằng API HotSpot mở rộng (Java 17)

Trong JDK có module `jdk.management`, giao diện `com.sun.management.ThreadMXBean` cung cấp chức năng đo lượng heap memory đã allocated **theo thread** nếu VM hỗ trợ. Đo ví dụ này có **output động**, không in một giá trị bytes cố định.

Tạo file **`ThreadAllocationProbe.java`**:

```java
import java.lang.management.ManagementFactory;

public class ThreadAllocationProbe {
    static volatile int sink;

    static int calculation(int i) {
        int[] numbers = {i, i + 1, i + 2};
        return numbers[0] + numbers[2];
    }

    public static void main(String[] args) {
        java.lang.management.ThreadMXBean base =
                ManagementFactory.getThreadMXBean();

        if (!(base instanceof com.sun.management.ThreadMXBean bean)
                || !bean.isThreadAllocatedMemorySupported()) {
            System.out.println("thread-allocation=unsupported");
            return;
        }

        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }

        long tid = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(tid);

        int local = 0;
        for (int i = 0; i < 100_000; i++) {
            local += calculation(i);
        }
        sink = local;

        long after = bean.getThreadAllocatedBytes(tid);
        System.out.println("supported=true");
        System.out.println("deltaNonnegative=" + (after >= before));
        System.out.println("checksum=" + sink);
    }
}
```

```bash
javac --release 17 ThreadAllocationProbe.java
java ThreadAllocationProbe
```

Với môi trường `ThreadMXBean` hỗ trợ/enabled và trả counters hợp lệ, chương trình in:

```text
supported=true
deltaNonnegative=true
checksum=1410165408
```

**Kiểm tra phép tính:** một lượt `calculation(i)` trả `i + (i+2) = 2i+2`. Tổng số học toán học từ `i=0` tới `99.999` là `10.000.100.000`; Java `int` có overflow modulo 2³² nên kết quả `(10.000.100.000 mod 2³²) = 1.410.165.408`. Ở một VM không hỗ trợ, chỉ in:

```text
thread-allocation=unsupported
```

**Cảnh báo:** `getThreadAllocatedBytes` có thể trả số âm như `-1` trong những tình huống disabled/unavailable; production code cần xử lý tình huống đó. Demo đơn giản chủ yếu minh họa API, **không in allocation delta** để tránh gán một số phụ thuộc JIT/GC. JVM cũng có thể không cho thay đổi trạng thái đo ở một số môi trường; chương trình khi đó có thể cần quyền/cấu hình phù hợp.

Nếu mục tiêu là so hai JIT configurations, API nên được dùng trong benchmark đủ warmup, trừ overhead đo, đo lặp nhiều lần và xác nhận method thực sự được C2 compiled.

## 23. Kiểm tra số học trên int overflow khi scalar replacement

EA/SRA **không được phép đổi các quy tắc số học của Java**. Trong các ví dụ:

- `int` arithmetic wrap-around modulo 2³² khi overflow (trong biểu diễn signed two's-complement), nếu không dùng các phương pháp kiểm tra overflow như `Math.addExact`.
- Scalar replacement chỉ biến cách lưu trữ/trao đổi fields, không đổi kết quả do phép toán integer.
- Object field initialization/default values vẫn phải bảo toàn: nếu một field không được gán trong constructor, Java có default value 0/false/null theo kiểu.
- Side effects trong constructor (ghi static, I/O, throw exception) không thể tự bị xóa tùy ý chỉ vì object local không escape; compiler phải giữ observable behavior.

### Ví dụ constructor có side effect

```java
static int constructed;

static final class LoggedPoint {
    final int x;
    LoggedPoint(int x) {
        constructed++;
        this.x = x;
    }
}
```

Đây là fragment. Compiler có thể không cần vật lý cấp phát `LoggedPoint` nếu phù hợp, nhưng **không được bỏ tác dụng tăng `constructed`** nếu trạng thái ấy có thể được quan sát theo Java semantics. Không dùng “scalar replacement = bỏ mọi câu lệnh trong constructor” làm quy tắc.

## 24. Liên hệ với Garbage Collection và live set

EA/SRA không phải GC algorithm. GC thu hồi các **objects đã allocated và không còn reachable**, còn scalar replacement có thể làm cho một số logical objects **chưa từng cần tồn tại như heap allocation trên hot path**.

Ví dụ giả định (đây không phải benchmark):

```text
Trước:
  1.000.000 operations/s
  mỗi operation tạo một temporary 24-byte object trên heap
  => khoảng 24 MB/s payload/object layout tùy runtime

Sau EA/SRA thành công:
  nhiều temporary object allocations được loại
  => tốc độ heap allocation có thể giảm đáng kể
  => young GC activity có thể giảm nếu workload tương ứng
```

Đừng dùng 24 byte làm “kích thước chính xác” cho một class Java: header, compressed oops, field alignment và JVM flags quyết định real object size.

**Nếu object không escape nhưng scalar replacement thất bại**, nó vẫn có thể được heap-allocated bình thường rồi được GC thu hồi khi không còn reachable. Vì vậy mô hình Java “objects được GC quản lý trên heap” và tối ưu EA/SRA không mâu thuẫn ở cấp hành vi người dùng.

## 25. Scalar replacement có làm cache, listener, ThreadLocal bớt leak không?

Không theo nghĩa xóa các object đang bị giữ bởi **strong reference paths**. Nếu một object được lưu vào static cache, được callback giữ hoặc nằm trong long-lived `ThreadLocal`, nó có đường giữ sống mà VM cần tôn trọng. EA không được “scalarize rồi biến mất” một object mà ứng dụng vẫn có thể truy cập qua reference ấy.

Trong những tình huống đó cần sửa lifecycle:

- Cache có capacity/weight bounds và eviction/invalidation.
- Listener/observer unsubscribe khi owner đóng.
- ThreadLocal remove/restore trong `finally` sau request/task.
- Queues/backpressure giới hạn số object giữ đồng thời.

EA hữu ích cho **temporary allocations trong hot methods**, không thay thế garbage collector và không sửa **logical memory leak** ở application.

## 26. Tình huống escape qua record, StringBuilder và wrappers

### Record không tự đồng nghĩa scalarized

Java 17 hỗ trợ **records**:

```java
record PointRecord(int x, int y) {}

static int calculate(int n) {
    PointRecord p = new PointRecord(n, n + 1);
    return p.x() + p.y();
}
```

Đây là fragment nằm trong class phù hợp. Record là class có references/object identity theo Java 17, không phải primitive/value object. C2 có thể inline accessor và scalar-replace instance nếu đủ điều kiện, nhưng **record syntax không bảo đảm allocation elimination**.

### StringBuilder trong nối chuỗi

Một số chuỗi nối trong Java hiện đại có thể được triển khai bằng `invokedynamic`/StringConcatFactory và tối ưu theo chiến lược riêng. Đừng kết luận chỉ vì source có `new StringBuilder` hay phép `+` mà có một allocation cho mỗi thao tác, cũng đừng gán mọi tối ưu string concatenation cho EA/SRA. Dùng allocation profiler theo đúng JDK version.

### Boxing

`Integer.valueOf` có thể dùng cache ở một số giá trị, hoặc JIT unbox/optimize. Giảm boxing allocations có nhiều nguyên nhân: primitive specialization, cache, inlining, EA/SRA. Không khẳng định mọi `Integer` wrapper nhìn thấy trong source chắc bị scalar-replace.

## 27. Khi nào nên viết code “EA-friendly”?

Tập trung vào cách viết **rõ ngữ nghĩa**:

1. Tạo temporary immutable-ish objects khi giúp code dễ hiểu; đừng tự pooling mọi Point/DTO.
2. Giữ lifetimes và ownership rõ ràng; tránh publish reference khi không cần.
3. Nếu helper chỉ đọc fields, design API có thể inline được; nhưng đừng phân mảnh/ép inline code theo cảm tính.
4. Tránh giữ các temporary graphs vào static caches/listeners/ThreadLocal chỉ để “tiện debug”.
5. Dùng primitive locals khi một giá trị thật sự là scalar và code đơn giản hơn, thay vì ép object model bất lợi.
6. Đừng thay API đúng bằng code thủ công phức tạp chỉ vì chưa đo được EA.
7. Không tự bỏ synchronization khi object được chia sẻ.
8. Đo **allocation bytes/op**, GC pressure và end-to-end latency trước/sau bằng benchmark hợp lệ.
9. Khi C2 không optimize, xem control flow merges, method inlining, unknown calls, array indexes và identity-sensitive operations.
10. Đối chiếu đúng **JDK 17 build**: C2 EA có những cải tiến qua các cập nhật 17u.

Lợi ích quan trọng của EA/SRA là **JVM có thể tự tối ưu code object-oriented bình thường**. Code rõ ràng, không escape và được inline thích hợp đôi khi vừa maintainable vừa nhanh.

## 28. Thực hành: thiết kế một microbenchmark có thể kiểm chứng

Trên Java 17, bạn có thể tạo JMH benchmark (JMH phải được thêm dependency/setup riêng; không phải một class Java SE độc lập):

```java
// Fragment ý tưởng cho một benchmark JMH; cần dependencies,
// JMH annotations và build configuration đầy đủ.
@Benchmark
public int localPoint(BenchmarkState state) {
    Point p = new Point(state.value, state.value + 1);
    return p.x + p.y;
}
```

**Đây là fragment JMH**, không compile chỉ với `javac --release 17` nếu không cung cấp JMH và các declarations. Cách đo đúng:

1. Chạy warmup đủ để C1/C2 có cơ hội tối ưu hot path.
2. Dùng nhiều forks độc lập, nhiều iterations measurement.
3. Data biến đổi phù hợp, tránh benchmark bị constant-fold toàn bộ.
4. Kiểm tra profiler `gc` cho bytes/op và allocation rate normalized.
5. So với `-XX:-DoEscapeAnalysis` trên **một fork/experiment riêng**.
6. Đo p50/p99/time/op/throughput tùy mục tiêu.
7. Lặp trên cùng host, JDK vendor/build, heap/GC settings.
8. Nếu delta đáng kể, phân tích inlining/C2 compilation để hỗ trợ nguyên nhân.

Đừng đo chỉ `System.nanoTime()` trên một lần chạy. Trong ứng dụng thật, giảm allocation bytes/op có thể chưa giảm end-to-end request latency nếu bottleneck thuộc I/O hoặc locks.

## 29. Bảng chẩn đoán nhanh

| Hiện tượng | Giả thuyết hợp lý | Việc cần kiểm tra |
| --- | --- | --- |
| Temporary Point nhiều nhưng GC allocation ít | C2 có thể eliminate allocations | Warmup/JMH bytes/op, flags, inlining |
| `new` hiện trong bytecode `javap` | Bytecode giữ ngữ nghĩa allocation | Không suy machine code chắc có allocation |
| Scalar replacement không xảy ra | Object escapes hoặc compiler conservatively bails | Inlining, merges, identity, arrays, runtime build |
| Object trả từ method | Escape ở method boundary | Có caller inlining/scalarization xuyên graph hay không |
| Static field giữ Pair | Reference có thể dùng ngoài method | Đây là escape, GC vẫn phải bảo toàn object |
| Local `synchronized` không còn overhead thấy rõ | Lock elimination có thể xảy ra | JMH/compiler diagnostics và JMM semantics |
| Disable EA tăng bytes/op | EA/SRA có thể giúp workload | So cùng JDK, tiers, profiler và forks |
| JFR không thấy allocation | Có thể được optimize hoặc không được sample | Event/sampling semantics, alternative counters |
| Heap used không giảm ngay sau fix | Heap commit/GC timing khác allocation | Post-GC occupancy và GC/cgroup metrics |
| `PrintEscapeAnalysis` bị báo lỗi option | Product VM có thể không hỗ trợ debug-only flag | Dùng supported diagnostics; không ép flag |
| Method được C2 compiled nhưng vẫn allocate | NoEscape không đủ; transformation có giới hạn | JMH allocation, generated code/JIT logs |
| Workload polymorphism đổi sau warmup | Inlining/call graph và EA opportunities đổi | JFR/PrintInlining, compilation history |

## 30. Những nhầm lẫn thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Mọi object có `new` bắt buộc nằm trên heap vật lý” | Compiler có thể loại allocation nếu Java semantics giữ nguyên |
| “Escape analysis là GC” | EA là compiler analysis; GC thu hồi heap objects đã allocated |
| “Object không thoát luôn được stack allocate” | HotSpot C2 thường **scalar-replace**, không thực hiện stack allocation object như khẳng định ấy |
| “NoEscape chắc chắn zero allocation” | Còn SRA eligibility, inlining, graph/implementation limits |
| “Trả object từ helper luôn cấm EA” | Inlining caller/callee có thể làm allocation không escape compiled graph |
| “Object truyền argument là GlobalEscape” | Callee có thể không giữ nó; ArgEscape/inlining/interprocedural analysis |
| “Static publication không liên quan escape” | Nó đưa reference tới owner ngoài scope, rất quan trọng |
| “Scalar replacement biến class thành primitive” | Nó chỉ là optimizer transformation; class vẫn giữ Java identity semantics |
| “EA xóa toàn bộ constructor” | Side effects/exceptions/initialization semantics phải bảo toàn |
| “Scalar values phải nằm trên stack” | Có thể ở registers, compiled spills/stack slots hoặc tối ưu xa hơn |
| “Identity hash code là địa chỉ object” | Không; là identity-based hash, có thể trùng cho objects khác |
| “Lock elimination cho phép xóa mọi synchronized trong source” | Shared synchronization/JMM vẫn phải đúng |
| “Debug-only PrintEscapeAnalysis luôn chạy ở JDK production” | Nhiều product builds không có flag ấy |
| “JFR không có event nên chắc không allocate” | Sampling/event thresholds có thể che allocations |
| “C2 compile chắc loại hết temporary objects” | Tùy escape graph, size, merges, calls, compiler heuristics |
| “`-XX:-DoEscapeAnalysis` chỉ đổi một `new`” | Có thể tác động nhiều optimizations/CPU/GC, benchmark cần kiểm soát |
| “Tăng `-Xmx` kích hoạt SRA” | Heap sizing không đảm bảo JIT scalar replacement |
| “Giảm allocation luôn giảm latency tỉ lệ thuận” | Throughput/latency phụ thuộc toàn workload |
| “Heap dump thiếu object X nghĩa X chắc bị scalar-replace” | Object cũng có thể đã chết/GC reclaimed hoặc không nằm trong dump |
| “Java 17 record là value type không identity” | Java 17 records vẫn là reference classes có identity |

## 31. Câu hỏi tự kiểm tra

**Câu 1:** Trong `new Point(3,4)` rồi chỉ đọc `x+y`, điều gì khiến JIT có thể xóa allocation?

**Đáp:** EA xác định object không escape và SRA đủ điều kiện biến fields thành scalar values, trong khi Java-visible semantics không đổi.

**Câu 2:** NoEscape có bắt buộc đồng nghĩa zero heap allocations không?

**Không.** NoEscape là kết quả phân tích; SRA còn có constraints và compiler/JVM runtime có thể chưa optimize.

**Câu 3:** Một method trả `Pair` ra caller. Có thể vẫn scalar-replace không?

**Có thể**, nếu JIT inline method vào caller và toàn compiled graph không cần một reference object thực sự, nhưng không được bảo đảm.

**Câu 4:** Vì sao gọi helper chưa được inline có thể làm EA khó hơn?

Compiler có thể không thấy helper có publish object hay không, nên phải phân tích bảo thủ.

**Câu 5:** Scalar replacement có phải cấp phát Point object đầy đủ trên Java stack?

**Không.** Nó có thể xóa object allocation và thay fields bằng registers/scalars; đó là khác biệt quan trọng.

**Câu 6:** Một object được lưu trong static shared field thì có thể biến mất hoàn toàn bằng SRA không?

Nếu reference có thể quan sát sau đó, compiler phải giữ object semantics; không thể đơn giản xóa object và coi assignment chưa từng xảy ra.

**Câu 7:** SRA có thể loại bỏ đồng bộ nào?

Lock elimination có thể áp dụng với private/non-escaping monitor nếu đủ điều kiện; nhưng không được xóa synchronization giữa threads đang chia sẻ object.

**Câu 8:** Vì sao deoptimization có thể cần “materialize” object?

Interpreter-compatible state có thể phải chứa logical object với fields/identity đúng, trong khi optimized code trước đó chỉ giữ scalar values.

**Câu 9:** Một `new int[3]` chắc bị xóa không?

Không. Constant indexes/small arrays có thể giúp, nhưng array indexing, aliasing, compiler limits vẫn quan trọng.

**Câu 10:** Nếu JFR không ghi event cấp phát Point, có thể khẳng định SRA hoạt động?

**Không.** Event sampling/thresholds và GC timing có thể khiến ta không thấy; cần profile/counters phù hợp.

**Câu 11:** `javap -c` còn thấy `new`, vậy HotSpot có thể đã loại allocation chưa?

**Có thể.** `javap` đọc class-file bytecode, không phải compiled machine code của C2.

**Câu 12:** Lợi ích của scalar replacement với GC là gì?

Nếu heap allocation thật sự được loại, số object/bytes cần quản lý trên heap giảm, từ đó có thể giảm allocation/GC pressure.

## 32. Nguồn chuẩn và đọc sâu

- [Oracle Java SE 17 — Java HotSpot Virtual Machine Performance Enhancements, Escape Analysis](https://docs.oracle.com/en/java/javase/17/vm/java-hotspot-virtual-machine-performance-enhancements.html): escape states, scalar replacement, lock elimination và việc HotSpot không áp dụng stack allocation tổng quát.
- [OpenJDK — Escape Analysis and Scalar Replacement Status](https://cr.openjdk.org/~cslucas/escape-analysis/EscapeAnalysis.html): flow-insensitivity, control-flow merges, loop-carried cases, inlining/interprocedural limitations.
- [OpenJDK JDK 17 update branch — C2 Escape Analysis](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/opto/escape.cpp): implementation/connection graph; khi nghiên cứu cần chốt commit và build 17.0.x.
- [OpenJDK JDK 17 update branch — MethodData](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/oops/methodData.hpp): profiling structures phục vụ dynamic compilation decisions; thay đổi theo JDK build.
- [Microsoft Java Engineering — Improving OpenJDK Scalar Replacement](https://devblogs.microsoft.com/java/improving-openjdk-scalar-replacement-part-1-3/): inlining, C2 EA/SRA và những điểm cần cải thiện.
- [Java SE 17 — `java` command reference](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): tùy chọn VM, diagnostics, classpath, GC/heap settings.
- [Java SE 17 — `javap` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): phân biệt bytecode `new` và thực thi mã máy đã tối ưu.
- [Java SE 17 — `ThreadMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html) và [`com.sun.management.ThreadMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.management/com/sun/management/ThreadMXBean.html): allocation counters per thread trên implementation hỗ trợ.
- [Java SE 17 — JFR API](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html): sự kiện runtime/allocation diagnostics.
- [JVMS Java SE 17 — Run-Time Data Areas](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): heap allocation semantics ở cấp specification.
- [JMH — OpenJDK Java Microbenchmark Harness](https://github.com/openjdk/jmh): công cụ benchmark tránh nhiều lỗi warmup/DCE/loop optimization.

**Kết luận:** **Escape analysis** phân tích objects có thể thoát phạm vi compiled graph/thread hay không; **scalar replacement** có thể thay object tạm bằng các **giá trị fields** và từ đó bỏ allocation thực; **lock elimination** có thể bỏ những monitors vô ích khi chứng minh an toàn. Hiệu quả phụ thuộc **C2, inlining, aliasing, control flow, identity, exceptions, compiler budgets và runtime build**. Đừng diễn giải thành “mọi `new` thành stack object”; hãy đo bằng **benchmark có warmup, bytes/op, JIT diagnostics và cùng JDK 17 configuration**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-006">Phân biệt loại bỏ cấp phát với giả định đối tượng luôn được chuyển lên stack</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt loại bỏ cấp phát với giả định đối tượng luôn được chuyển lên stack

Khi đọc về **escape analysis (EA)** trong HotSpot JVM, ta thường gặp lời giải thích: “Nếu đối tượng không escape khỏi method, JVM sẽ chuyển nó từ heap lên stack”. Câu này **không mô tả chính xác tối ưu hóa của HotSpot C2 trong Java 17**. Escape analysis là quá trình **phân tích khả năng tham chiếu tới đối tượng thoát ra khỏi một phạm vi**. Khi chứng minh được đối tượng đủ điều kiện, C2 có thể áp dụng **scalar replacement**: thay các thao tác trên đối tượng bằng các giá trị thành phần, thậm chí **loại bỏ hoàn toàn thao tác cấp phát đối tượng**.

Nói cách khác:

- **Allocation elimination:** một object allocation mà source/bytecode biểu diễn có thể **không còn được thực hiện** trong machine code được tối ưu.
- **Scalar replacement:** các field của object có thể được xử lý dưới dạng scalars, compiler temporaries hoặc registers/stack slots theo nhu cầu của machine code. **Không bắt buộc tồn tại một object nguyên vẹn ở stack.**
- **Stack allocation:** một object có cấu trúc/identity được lưu trong stack frame thay vì Java heap. Đây là **một kỹ thuật khác**; tài liệu về escape analysis của HotSpot nêu rõ rằng C2 không đơn giản thực hiện stack allocation cho object chỉ vì không escape.
- **Thông thường**, nếu object thật sự vẫn phải được materialize/allocate, HotSpot vẫn quản lý nó trên **Java heap** theo đường cấp phát phù hợp, thường qua Eden/TLAB. Không có hợp đồng “mọi local object đều nằm trên stack”.

Bài thuộc mục **Tối ưu HotSpot**, sử dụng bối cảnh **HotSpot C2 trên Java 17**. Nội dung phân biệt khái niệm, điều kiện tối ưu, escape states, các giới hạn của compiler, deoptimization/materialization, tác động tới GC, cùng các ví dụ **Java 17 biên dịch/chạy được** và phương pháp đo mà không đưa ra kết luận từ phỏng đoán.

## 1. Bốn lớp cần phân biệt: source, bytecode, machine code và runtime allocation

```text
Java source:
    Pair p = new Pair(a, b);
    return p.left() + p.right();
                 │
                 ▼ javac
Class file bytecode:
    new Pair
    dup
    invokespecial Pair.<init>
    invokevirtual Pair.left ...
                 │
                 ▼ interpreter / C1 / C2
JIT optimized machine code (một khả năng):
    result = a + b
    [không cần new Pair, không cần object header]
                 │
                 ▼ runtime
    Không nhất thiết có heap object Pair tương ứng mỗi call.
    Không có nghĩa một Pair nguyên vẹn được đặt trên native stack.
```

**Source `new` không phải một cam kết tạo allocation có thể đo ở mỗi lần thực thi** sau khi JIT đã tối ưu. Nó biểu diễn semantics: tạo đối tượng mới với giá trị/identity phù hợp **nếu identity được quan sát**. Compiler có quyền làm một biến đổi nội bộ miễn Java behavior vẫn đúng.

**`javap -c` có `new` cũng không chứng minh runtime đã cấp phát object**: `javap` in bytecode, không in optimized machine code mà C2 sẽ sử dụng về sau.

### 1.1. Local variable là reference, không phải vị trí vật lý của object

```java
Object obj = new Object();
```

`obj` là biến local chứa **reference** về mặt Java semantics. Điều đó **không** chứng minh object đã được đặt trên JVM stack. Trong interpreter, local variables nằm trong **frame** của lời gọi method; reference có thể nằm trong local variable array. Trong compiled code, reference/giá trị có thể được giữ trong register, stack slot hoặc bị tối ưu đi.

**JVM stack frame** chứa execution state như locals, operand stack theo mô hình JVMS; **Java heap** là nơi quản lý phần lớn Java object allocations thực sự. Tránh dùng từ “biến local nằm trên stack” để suy ngược rằng “object mà biến trỏ tới cũng nằm trên stack”.

### 1.2. JVM stack và native stack không phải heap

Các thuật ngữ:

| Khái niệm | Chức năng | Điều không thể suy ra |
| --- | --- | --- |
| Local variable/reference | Giữ hoặc biểu diễn tham chiếu tới object | Object đích chắc chắn nằm cùng frame |
| Operand stack theo JVMS | Mô hình thao tác trung gian của bytecode | Mọi toán hạng đang nằm nguyên như vậy trong optimized machine code |
| Native thread stack | Bộ nhớ thực thi của native/JIT frames và registers spilled | HotSpot C2 sẽ chuyển mọi non-escaping object thành stack object |
| Java heap | Không gian quản lý Java object allocations | Mỗi từ khóa `new` luôn tạo một heap allocation sau JIT |
| TLAB | Vùng thread-local cấp phát nhanh từ heap | Một “thread stack” cho objects |
| Code Cache | Chứa compiled native machine code | Java heap chứa các object dữ liệu nghiệp vụ |

Từ **stack** bị dùng lẫn cho nhiều cơ chế, dẫn tới một hiểu nhầm cốt lõi: “reference/field scalar nằm trong stack slot” khác hoàn toàn “một object nguyên vẹn được stack-allocate”.

## 2. Escape analysis là điều kiện chứng minh, không phải chỉ nhìn biến local

HotSpot C2 phân tích **đường đi của reference trong phạm vi compiled graph** để quyết định object còn có thể được quan sát ở đâu. Một object tạo bên trong method vẫn có thể **escape** qua return value, static/shared field, JNI hoặc lời gọi mà compiler chưa chứng minh được; trái lại, `return new Pair(...)` vẫn có cơ hội được tối ưu nếu caller được inline và object không bị công bố ngoài graph.

Trong tài liệu HotSpot, các trạng thái thường được gọi là **NoEscape**, **ArgEscape**, **GlobalEscape**. Đây là phân loại **nội bộ của compiler**, không phải annotation hay thuộc tính trên Java object. `NoEscape` chỉ mở **khả năng** scalar replacement, không bảo đảm C2 loại được allocation; `ArgEscape` và `GlobalEscape` cũng cần xét cách call graph/alias/side effects thực sự được phân tích.

**Trọng tâm riêng của bài này:** một object *có thể không escape* vẫn không hề có quy tắc bắt buộc phải **được cấp phát trên native stack**. Để hiểu vì sao, phải xét cách C2 thay cả object bằng scalars và phân biệt ba kết quả: heap allocation, scalar replacement, stack object allocation.

## 3. Scalar replacement: bỏ biểu diễn object nguyên khối

Xét ví dụ trong một class có `Pair(int left, int right)` và hai field `int left, right`:

~~~java
static int sum(int a, int b) {
    Pair p = new Pair(a, b);
    return p.left + p.right;
}
~~~

Đây là **mảnh code**; cần định nghĩa `Pair` trước khi compile. Sau khi inline constructor và phân tích reference đủ mạnh, C2 **có thể** thay các thao tác trên object bằng `left=a`, `right=b` rồi chỉ còn `return a+b`. Khi đó **không cần heap allocation tương ứng mỗi lần gọi** trong optimized path, nhưng **cũng không có object `Pair` đầy đủ được chuyển lên stack**.

Giá trị scalars có thể ở register, spilled stack slot, hoặc constant-fold tiếp; vị trí vật lý không phải Java API contract. Phải phân biệt **escape analysis (điều kiện phân tích)**, **scalar replacement (thay object bằng các giá trị)**, **allocation elimination (không cấp phát object thực)** và **dead-code elimination (bỏ phần không ảnh hưởng hành vi)**. Một allocation không xuất hiện trong profile **không tự chứng minh** tối ưu nào gây ra việc đó.

Điều kiện và giới hạn thực tế của EA được phân tích chuyên sâu ở bài trước; phần tiếp theo của bài này tập trung trả lời vì sao **allocation biến mất ≠ stack allocation**.

## 4. Vì sao không được dạy “NoEscape object luôn lên stack”?

Nguồn nghiên cứu về **HotSpot Escape Analysis and Scalar Replacement Status** công khai chỉ ra: **HotSpot không thực hiện stack allocation** như một kết quả mặc định của escape analysis C2. Tài liệu Oracle về HotSpot performance cũng mô tả compiler **eliminates scalar-replaceable allocations** và **does not replace heap allocation with a stack allocation** cho non-global-escaping objects.

So sánh:

| Cơ chế | Object header đầy đủ? | Object còn có identity materialized? | Điều có thể diễn ra |
| --- | --- | --- | --- |
| Heap allocation thông thường | Có trong heap object | Có | `new` được thực thi theo đường VM cấp phát |
| Stack allocation đúng nghĩa | Có thể có representation object trong stack frame theo một VM khác | Có thể tồn tại trong frame | **Không phải lời mô tả mặc định về HotSpot C2/Java 17** |
| Scalar replacement / allocation elimination | **Không nhất thiết có object header** | Có thể chưa cần materialize | Các fields được thay thành scalars và allocation bị bỏ |
| TLAB fast allocation | **Có object thật trên heap** | Có | Cấp phát rẻ, nhưng không phải eliminated allocation |
| Escape nhưng field read optimized | Có thể vẫn có heap object | Có | Compiler vẫn có thể inline/copy-propagate một số fields dù object escape |

Giải thích chính xác hơn:

> “Nếu C2 chứng minh một allocation không escape và đủ điều kiện scalar replacement, nó **có thể loại allocation ấy**. Điều này không tương đương với việc **cấp phát cùng object trên thread stack**.”

### 4.1. Java specification có bắt buộc mọi object vật lý nằm heap không?

JVMS mô tả **heap là runtime data area dùng cấp phát cho class instances và arrays**, đồng thời cho phép implementation dùng các tối ưu không quan sát được miễn semantics được giữ. Developer không nên khẳng định “stack allocation mọi object” chỉ dựa trên quy tắc local variable. Với C2 hiện hành trong phạm vi bài, kỹ thuật đáng tập trung là **scalar replacement**, không phải stack object allocation.

Tranh luận “object có trên heap hay stack” cần xác định rõ **object runtime materialized có thực sự tồn tại không?** Nếu compiler xóa allocation và tính được kết quả bằng constants/scalars, câu hỏi “đối tượng nằm stack hay heap” có thể trở thành **câu hỏi sai tiền đề**.

## 5. TLAB: cấp phát trên heap có thể rất nhanh mà không cần stack

**TLAB (Thread-Local Allocation Buffer)** là cơ chế HotSpot tối ưu đường cấp phát **heap objects** bằng cách dành một vùng dung lượng thuộc Eden/young heap cho một thread thực hiện allocations nhanh. Khi đủ chỗ trong TLAB, một allocation thường gần với thao tác bump pointer, hạn chế lock chung.

```text
Java heap (ví dụ vùng Eden):
  ├─ TLAB của thread A ─► object1, object2, object3
  ├─ TLAB của thread B ─► object4, object5
  └─ vùng khác / allocation slow paths

Các TLAB ở trên vẫn nằm trong JAVA HEAP.
Không nằm trên native call stack của thread.
```

Do đó:

- “Object tạo quá nhanh, chắc nằm stack” là sai.
- Allocation còn diễn ra nhưng rẻ nhờ TLAB **khác** với allocation hoàn toàn bị loại.
- GC vẫn phải quản lý objects được tạo trong TLAB nếu chúng thật sự materialize trên heap.
- Khi JIT loại allocation, nó giảm work của cả allocator và GC theo cách khác TLAB optimization.

**Không phải mọi allocation đều đi qua TLAB**, ví dụ một số object lớn/slow paths hoặc compiler/collector behavior đặc biệt; điều này không làm TLAB trở thành một thế hệ GC riêng.

## 6. Khi nào đối tượng có thể escape?

### 6.1. Return reference khỏi method

```java
static Box makeBox(int value) {
    return new Box(value);
}
```

Ở ranh giới method source, return `Box` cho caller. Nếu caller có thể giữ, so identity hoặc truyền đối tượng đi xa, compiler thường cần materialized object. Tuy nhiên, **sau inlining**, JIT có thể nhìn thấy caller chỉ đọc field rồi bỏ reference; ở compiled region lớn hơn nó có thể vẫn loại allocation.

Không dùng “method trả object ⇒ tuyệt đối không thể scalarize” như một định luật xuyên toàn chương trình.

### 6.2. Lưu vào static field

```java
private static volatile Object shared;

static void publish(Box box) {
    shared = box;
}
```

Object được publish qua một field có thể truy cập từ code khác và thread khác. Khi assignment thực sự xảy ra và object có thể được quan sát, compiler phải bảo đảm Java memory model/object identity; không thể đơn giản xóa object mà làm mất observable reference.

`volatile` ở đây giúp mô hình công bố giữa threads rõ ràng hơn; nó **không** phải nguyên nhân duy nhất của escape. Một shared static field thông thường cũng có thể giữ object.

### 6.3. Đưa object vào container có vòng đời dài

```java
private static final java.util.List<Object> CACHE =
        new java.util.ArrayList<>();

static void remember(Object value) {
    CACHE.add(value);
}
```

Object lưu trong `CACHE` có strong path từ static collection. Nó có thể vượt qua lifetime của method tạo object và gây retained heap memory. Dù vậy JIT vẫn có thể loại các temporary objects khác trên đường chạy nếu chúng không escape.

### 6.4. Truyền vào method khác

```java
static int readValue(Box b) {
    return b.value;
}

static int caller(int n) {
    Box b = new Box(n);
    return readValue(b);
}
```

Truyền `b` làm argument **không tự chứng minh global escape**. Nếu C2 có thể inline `readValue`, thấy callee chỉ đọc field, và object graph đủ đơn giản, allocation có thể vẫn được loại.

Ngược lại, nếu callee là native method, reflective/dynamic call hoặc method quá lớn không được inline, compiler có thể không chứng minh được object không escape và phải materialize nó.

### 6.5. Ghi reference vào lambda/closure hoặc chuyển cho thread

Nếu một closure/lambda/task giữ reference và được lưu vào executor queue hoặc thread khác, đối tượng có thể sống lâu hơn method tạo nó. Tránh suy “lambda được khai báo trong method” nên toàn bộ captures nằm trên method stack.

Compiler có thể tối ưu những lambdas không escape trong tình huống đơn giản; nhưng các capture graphs cần phân tích chứ không thể kết luận theo hình thức source.

## 7. Không escape khỏi thread chưa đủ để loại allocation

Một object được giữ trong một `ThreadLocal`:

```java
static final ThreadLocal<Box> LOCAL = new ThreadLocal<>();

static void store(int v) {
    LOCAL.set(new Box(v));
}
```

**Thread confinement** có thể giúp lập luận về data races/locking, nhưng object vẫn có thể tồn tại sau khi `store()` kết thúc và được lấy lại từ `LOCAL.get()`. Nó **escape khỏi method** dù thông thường không được thread khác dùng.

Đây là một ví dụ điển hình: “không được thread khác chạm đến” **không** đồng nghĩa “nếu method kết thúc thì object không cần tồn tại”. Ngữ nghĩa của `ThreadLocal` đòi object identity/state tồn tại khi cần được truy xuất.

### 7.1. NoEscape, ArgEscape và GlobalEscape không phải public memory locations

Các escape classifications mô tả compiler biết gì về các references. Chúng **không** tự ánh xạ:

```text
NoEscape       ⇒ stack
ArgEscape      ⇒ survivor
GlobalEscape   ⇒ old
```

Cả ba mũi tên trên đều **sai**. Eden/Survivor/Old là **GC generations**, còn escape analysis là **compiler dataflow reasoning**. Một object GlobalEscape có thể cấp phát ở Eden rồi promoted; một NoEscape object có thể vẫn materialize trên heap nếu optimizer không thể hoặc không chọn scalar replacement.

## 8. Đánh đổi và giới hạn của scalar replacement

Một đối tượng “không escape theo trực giác” vẫn có thể không bị loại allocation vì:

- Compiler chưa compile method bằng C2, hoặc method chưa đủ “hot”.
- Đường gọi không được inline nên phạm vi phân tích bị giới hạn.
- Object graph phức tạp, aliasing nhiều hoặc control-flow merges khó phân tích.
- Arrays lớn, index access không biết tại compile time, hoặc số elements vượt giới hạn scalarization của JVM.
- Escape analysis/compiler budgets/timeouts/heuristics giới hạn work.
- Identity-sensitive operations và APIs yêu cầu materialized object.
- JVMTI/debugging/instrumentation, safepoints hoặc khả năng deopt cần giữ trạng thái phù hợp.
- Native/reflective calls có semantics khó chứng minh từ IR.
- Tối ưu bị tắt bằng flags hoặc JVM không dùng compiler hỗ trợ tương tự.

**Không phải mỗi mục trên là một blocker tuyệt đối trong mọi chương trình.** Một số field reads/aliases/calls vẫn được tối ưu nhờ inlining, constant folding và version-specific improvements.

### 8.1. Identity và `System.identityHashCode`

```java
Box a = new Box(7);
Box b = new Box(7);
boolean same = a == b;  // false đối với hai allocations quan sát độc lập
```

Nếu compiler tối ưu loại materialization, nó vẫn phải giữ `same == false` theo Java semantics. Việc không có heap allocation thực tế **không được làm đổi kết quả các phép so identity có thể quan sát**.

Gọi `System.identityHashCode(obj)`, dùng object monitor hoặc truyền object tới API có thể giữ reference có thể làm việc materialization cần thiết hoặc hạn chế scalar replacement. Nhưng không nói máy móc rằng “chỉ cần dùng `==` hoặc `synchronized` là mọi compiler bắt buộc tạo heap object” — một số comparisons/locks vẫn có thể được giải quyết hoặc loại bỏ khi chứng minh an toàn.

### 8.2. Arrays và scalar replacement

`new int[2]` trong một phép tính nhỏ có thể là ứng viên tối ưu khi kích thước/indices đủ rõ:

```java
static int sumTwo(int a, int b) {
    int[] values = {a, b};
    return values[0] + values[1];
}
```

Nhưng `new int[100_000]` với accesses động khác nhiều về khả năng scalarization và compiler costs. Tránh kết luận “mảng local luôn trên stack” hoặc “mảng không bao giờ scalarize”; kích thước, truy cập, aliasing và compiler version đều quan trọng.

### 8.3. Boxing, records và value-like temporary objects

`Integer` boxing, small immutable records và tiny holder objects là những ví dụ ứng viên tối ưu khi object identity không cần được quan sát. Nhưng:

- Autoboxing có thể dùng **cache** nên không nhất thiết tạo object mới ngay cả trước EA.
- Java 17 `record` vẫn là **reference class**, không phải primitive value type theo cách các dự án JVM giá trị tiên tiến từng đề xuất.
- `record Pair(int x, int y)` có thể là candidate cho scalar replacement, **không có hợp đồng record luôn unboxed**.
- Code size và polymorphism vẫn giới hạn khả năng JIT inline.

Không trộn scalar replacement với primitive specialties hay Project Valhalla proposals của các phiên bản/đề xuất khác.

## 9. Chương trình Java 17 đầy đủ: tạo object local và object escape

Lưu file **`EscapeAnalysisDemo.java`**:

```java
public class EscapeAnalysisDemo {
    record Pair(int left, int right) {}

    private static volatile Pair keepAlive;

    static long localSum(int n) {
        Pair pair = new Pair(n, n + 1);
        return (long) pair.left() + pair.right();
    }

    static Pair makePair(int n) {
        return new Pair(n, n + 1);
    }

    public static void main(String[] args) {
        long total = 0L;
        for (int i = 0; i < 100_000; i++) {
            total += localSum(i);
        }

        Pair retained = makePair(7);
        keepAlive = retained;

        Pair first = makePair(1);
        Pair second = makePair(1);

        System.out.println("sum=" + total);
        System.out.println("retainedValue=" +
                (retained.left() + retained.right()));
        System.out.println("distinctObjects=" + (first != second));
        System.out.println("sameRecordValue=" + first.equals(second));
    }
}
```

Biên dịch/chạy (JDK 17+):

```bash
javac --release 17 EscapeAnalysisDemo.java
java EscapeAnalysisDemo
```

**Stdout chính xác khi chương trình hoàn tất, không bật logging:**

```text
sum=10000000000
retainedValue=15
distinctObjects=true
sameRecordValue=true
```

**Giải thích:**

1. Mỗi iteration gọi `localSum(i)`, về semantics tạo `Pair(i, i+1)` rồi chỉ cộng hai field. Đây là ứng viên tốt cho **scalar replacement / allocation elimination** khi C2 tối ưu vùng code.
2. Tổng `2×i+1` với `i=0..99_999` là **100.000² = 10.000.000.000**, và được giữ bằng `long` để tránh overflow.
3. `makePair(7)` trả reference; `retained` được publish vào `static volatile keepAlive`, nên object có thể được code khác quan sát. Không thể tự suy lần gọi này được loại allocation.
4. `first` và `second` là hai allocations riêng. `first != second` là **true**, nhưng record `equals` so field contents nên `true`.
5. Java-level stdout giống nhau dù compiler dùng interpreter, C1 hay C2; **không có dòng nào cho biết allocation của `localSum` thật sự được eliminated trong lần chạy này**.

### 9.1. Kết quả khác nhau giữa source và runtime

`javap` vẫn có thể hiển thị:

```bash
javap -c -p EscapeAnalysisDemo
```

trong đó bytecode của `localSum` có `new` để tạo record object. Trong thời gian chạy, optimized code C2 **có thể** không thực hiện allocation ấy.

Vì thế **`javap -c` không chứng minh stack allocation, heap allocation hay scalar replacement runtime**. Nó chỉ là điểm đầu khi học bytecode và điều JVM có quyền tối ưu.

### 9.2. So sánh các chế độ thực thi mà không bịa stdout GC

```bash
# Bình thường: JVM tự profile và tier compile nếu phù hợp
java EscapeAnalysisDemo

# HotSpot interpreter-oriented, không dùng JIT compilation thông thường
java -Xint EscapeAnalysisDemo

# HotSpot JDK 17: tắt escape analysis
java -XX:-DoEscapeAnalysis EscapeAnalysisDemo
```

**Ba lệnh phải có cùng bốn dòng stdout Java-level** khi thực thi thành công. Nhưng runtime allocations, warm-up cost, GC activity và elapsed time có thể khác. `-Xint` có thể chậm hơn đáng kể; không dùng một lần chạy ngắn để tuyên bố hệ số tốc độ.

## 10. Chương trình thứ hai: escape qua field và qua return

Tạo file **`EscapePathsDemo.java`**:

```java
public class EscapePathsDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    private static volatile Box published;

    static int localOnly(int n) {
        Box b = new Box(n);
        b.value += 2;
        return b.value;
    }

    static Box returnsReference(int n) {
        return new Box(n);
    }

    static void publish(Box b) {
        published = b;
    }

    public static void main(String[] args) {
        System.out.println("local=" + localOnly(40));

        Box returned = returnsReference(5);
        System.out.println("returned=" + returned.value);

        Box first = new Box(9);
        publish(first);
        System.out.println("published=" + published.value);

        Box second = new Box(9);
        System.out.println("sameIdentity=" + (first == second));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 EscapePathsDemo.java
java EscapePathsDemo
```

**Stdout chính xác:**

```text
local=42
returned=5
published=9
sameIdentity=false
```

**Học được gì?**

- `localOnly` tạo `Box` chỉ dùng cho primitive result; có khả năng scalarize.
- `returnsReference` cho caller reference. Nếu JIT inline và thấy caller chỉ lấy field, **vẫn có trường hợp** compiler loại allocation ở vùng lớn hơn.
- `published` là một field chung, có thể được thread khác đọc; object đích cần tồn tại theo observable Java semantics.
- Hai lần `new Box(9)` tạo hai conceptual objects distinct. JIT không được biến `sameIdentity=false` thành true.
- Đây là **ví dụ phân tích đường escape**, không phải một tracer xác định physical allocation location.

### 10.1. Return object có phải lúc nào cũng GlobalEscape?

Tài liệu HotSpot mô tả return reference như ví dụ của **GlobalEscape** ở phạm vi analyzed method. Nhưng sau method inlining, compiler có thể biết caller không giữ reference ngoài phạm vi optimized region. Vì vậy cách diễn giải trong code review nên là:

> “Return reference là một **escape edge ở ranh giới method**, làm việc loại allocation phức tạp hơn; compiler có thể vẫn tối ưu nếu phân tích cả caller.”

Đừng đổi điều đó thành “return từ mọi method đều tạo permanent heap object” hoặc “nếu không có explicit return thì không thể escape qua field”.

## 11. Chương trình thứ ba: lock elimination không đồng nghĩa stack object

Một tác động khác của EA là **lock elimination**: nếu compiler có thể chứng minh lock trên một object không cần thiết để đồng bộ với thread khác và việc bỏ lock không làm sai observable semantics, nó có thể loại một số monitor enter/exit operations.

Tạo file **`LocalLockDemo.java`**:

```java
public class LocalLockDemo {
    static int calculate(int n) {
        Object guard = new Object();
        synchronized (guard) {
            return n + 1;
        }
    }

    public static void main(String[] args) {
        long total = 0L;
        for (int i = 0; i < 100_000; i++) {
            total += calculate(i);
        }
        System.out.println("total=" + total);
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 LocalLockDemo.java
java LocalLockDemo
```

**Stdout chính xác:**

```text
total=5000050000
```

`guard` được tạo để dùng bên trong `calculate`; vì code không công bố nó ra ngoài, compiler **có thể** chứng minh monitor operations không cần thiết và bỏ lock, thậm chí loại allocation. Nhưng:

- `synchronized` Java semantics không biến mất: nếu có exception/monitor-observable behavior, compiler phải giữ hành vi hợp lệ.
- Không khẳng định mọi local lock trong mọi JVM đều bị elide.
- Đây **không phải** bằng chứng `guard` đã được stack-allocate.
- Nếu thay `guard` bằng một shared lock field, việc bỏ mutual exclusion có thể không hợp lệ do thread khác cần đồng bộ.

**Lưu ý:** “lock elimination” khác với việc người viết code xóa từ khóa `synchronized`. Trong source, giữ synchronization cần thiết; JVM chỉ bỏ máy móc khi chứng minh hợp lệ.

## 12. Deoptimization: object bị scalarize có thể cần materialization

Một khó khăn lớn của scalar replacement là **JIT có thể cần deoptimize** và quay về interpreter khi một speculative assumption không còn đúng.

Giả sử:

```text
Java-level:
  Pair p = new Pair(3, 4);
  doSomething();
  use(p.left, p.right);

Optimized machine state:
  scalar_left = 3
  scalar_right = 4
  [không có physical Pair heap allocation]

Đột nhiên deoptimization ở một điểm cần resume interpreter:
  JVM dùng scope/debug info và virtual-object metadata
  để reconstruct Java-level frames, locals, operand stack
  và materialize Pair object nếu thực sự cần identity/reference.

Sau materialization nếu cần:
  có thể tồn tại heap object Pair để Java code tiếp tục đúng.
```

Điều đó **không** có nghĩa:

- Compiler phải giữ sẵn một “Pair object trên stack” trong suốt optimized execution.
- Deopt luôn tạo một object cho mọi scalarized allocation, bất kể Java state có cần quan sát nó hay không.
- Deopt chạy lại cả method từ đầu và lặp side effects.
- Vì thấy một allocation xuất hiện sau deopt nên mọi lời gọi trước đó đều đã cấp phát object.

Đúng hơn: **runtime có metadata đủ để khôi phục trạng thái Java tương đương**, bao gồm các virtual objects/frames khi cần. Deoptimization/materialization là một phần thiết yếu giúp **allocation elimination và speculative JIT optimization vẫn giữ correctness**.

### 12.1. Tại sao ảnh chụp stack không chỉ ra object ở stack?

Một optimized stack frame có thể chứa scalar fields, references, spills hoặc metadata phục vụ reconstruct. Việc công cụ nhìn thấy một vài int values trong native stack frame **không chứng minh** có một `Pair` nguyên vẹn đang cư trú ở đó.

Muốn phân tích thực sự phải xét compiler IR/machine code/VM metadata theo build phù hợp; Java stack trace chủ yếu mô tả call scopes, không phải physical object allocation.

### 12.2. Materialization có thể làm allocation metrics thay đổi

Trong phần lớn execution, allocation bị bỏ thì giảm pressure lên GC. Nhưng nếu chương trình thường xuyên deopt/reconstruct objects, một số allocations có thể xuất hiện tại những điểm khác so với trực giác từ source. Vì vậy không lấy một thời điểm GC allocation histogram và khẳng định object “luôn được đưa lên stack”.

**GC logs không nêu một trường “stack allocations eliminated by EA”**. Khi nhìn GC activity giảm, nguyên nhân có thể từ EA, dead-code elimination, allocation batching, TLAB sizing hoặc workload changes; cần thí nghiệm đối chứng.

## 13. Khi object không được loại allocation thì sao?

Giả sử ứng dụng tạo 100.000 `Box` rồi giữ tất cả trong một collection:

```java
java.util.List<Box> boxes = new java.util.ArrayList<>();

for (int i = 0; i < 100_000; i++) {
    boxes.add(new Box(i));
}
```

Đây là **snippet**, không phải file độc lập; sử dụng class `Box` từ ví dụ trước.

Nếu list còn reachable và code khác có thể truy cập các Boxes, runtime cần giữ object graph thực. Trên HotSpot với generational GC, các allocations vật lý thường nằm ở heap young/Eden theo đường phù hợp, có thể promoted nếu sống lâu.

Compiler vẫn có thể tối ưu constructor field stores, remove redundant bounds checks hoặc inline methods; **không loại allocation không có nghĩa không có bất kỳ tối ưu nào**.

### 13.1. Allocation trên heap không nhất thiết đắt

Với TLAB và GC generational, tạo các objects nhỏ chết trẻ thường có chi phí rất thấp trên HotSpot. Vì vậy, khuyến cáo “đừng bao giờ dùng `new` trong hot method” quá cực đoan.

Quan trọng là **allocation rate, survival/retention, cache locality và GC pause/cpu metrics**. Chuyển mọi object thành mutable reusable buffer có thể tạo aliasing bugs, dữ liệu cũ bị lộ và lock contention; đôi khi code immutable đơn giản, dễ tối ưu còn nhanh hơn.

### 13.2. Có thể tối ưu từ compiler mà không làm code khó đọc

Một holder object nhỏ, immutable dùng để biểu diễn cặp giá trị trong helper methods có thể làm code rõ ràng hơn so với truyền nhiều primitives. Nếu performance profiling không chỉ ra vấn đề, ưu tiên correctness/maintainability trước. JIT có thể scalarize một số temporary holders, nhưng đó là **cơ hội**, không là hợp đồng API.

## 14. Các cờ HotSpot Java 17 để thử nghiệm

### 14.1. Tắt escape analysis để so sánh

Oracle JDK 17 `java` manual ghi rõ:

```bash
-XX:+DoEscapeAnalysis       # Bật EA, mặc định trên HotSpot phù hợp
-XX:-DoEscapeAnalysis       # Tắt EA
```

Chạy:

```bash
java EscapeAnalysisDemo
java -XX:-DoEscapeAnalysis EscapeAnalysisDemo
```

Kết quả nghiệp vụ của hai lần chạy **giống nhau**. Nếu cấu hình khác/phiên chạy ngắn, có thể không thấy chênh lệch memory đáng kể; **không có bằng chứng từ stdout xác định EA đã loại bao nhiêu objects**.

### 14.2. `EliminateAllocations` trên HotSpot C2

Trong source của **OpenJDK JDK 17 C2** có VM option:

```bash
-XX:+EliminateAllocations
-XX:-EliminateAllocations
```

Option này điều khiển một nhánh loại allocations dựa trên EA của C2. Khi dùng HotSpot build hỗ trợ, thử:

```bash
java -XX:-EliminateAllocations EscapeAnalysisDemo
```

Nhưng **không suy việc tắt option bảo đảm mỗi source `new` sẽ tạo heap allocation**: còn các optimizations khác như dead-code elimination, constant folding, intrinsics, boxing caches và compiler policy. Cũng không coi option này là Java SE portable API.

**Chú ý:** Nếu JVM/vendor phản hồi `Unrecognized VM option`, dừng thử nghiệm và kiểm tra version/`PrintFlagsFinal`; không ép các option nội bộ của HotSpot lên JVM khác.

### 14.3. Tắt JIT để đối chiếu semantics

```bash
java -Xint EscapeAnalysisDemo
```

`-Xint` trên HotSpot chạy bytecode theo interpreter mode chủ yếu, vì thế các tối ưu C2 scalar replacement không có trong cùng code path; nhưng semantics/output Java vẫn đúng. Hiệu năng có thể giảm mạnh; không dùng nó để chạy benchmark throughput production.

`-XX:-TieredCompilation` **không đồng nghĩa tắt JIT**. Nó thay chính sách tiering, và một compiler khác vẫn có thể tối ưu.

### 14.4. Kiểm tra flags đang có thay vì nhớ tên từ tutorial cũ

```bash
java -XX:+PrintFlagsFinal -version
```

Có thể kết hợp filter theo shell/platform để xem `DoEscapeAnalysis`, `EliminateAllocations`, `EliminateLocks` và các flags liên quan.

Cẩn thận với cờ `PrintEscapeAnalysis` hoặc `PrintEliminateAllocations`: trên nhiều HotSpot **product/release builds**, đây là các options chỉ dành cho **non-product/debug VM**. Không đưa một lệnh `-XX:+PrintEscapeAnalysis` như thể bắt buộc chạy được trên mọi bản OpenJDK 17 production.

## 15. Đo allocation elimination đúng cách

Một phần mềm Java chạy nhanh hơn khi tắt/mở EA không tự chứng minh toàn bộ khác biệt đến từ một allocation site cụ thể. Có thể còn JIT warm-up, inlining, tiering, code cache, GC policy và CPU scheduling.

**Quy trình tối thiểu:**

1. **Cố định JDK vendor/version**: ghi chính xác `java -version`, architecture và server/client VM.
2. **Giữ workload đại diện**: inputs, object life spans và method polymorphism tương tự production.
3. **Warm-up đúng**: để JIT profile/compile trước khi lấy thông số steady state.
4. **So ba cấu hình**: baseline, `-XX:-DoEscapeAnalysis` và (nếu build hỗ trợ) `-XX:-EliminateAllocations`.
5. **So với `-Xint` về semantics**, nhưng không dùng interpreter performance để suy chi phí allocation C2.
6. **Dùng JFR/allocator counters/profiler** để so allocations, gc pauses, heap allocation rates và hotspots với settings giống nhau.
7. **Dùng nhiều forks/repetitions**, không tin một lần chạy 100 ms.
8. **Tránh benchmark chết**: không cho compiler xóa toàn bộ phép tính chỉ vì result không được dùng.
9. **Đo CPU và throughput cùng allocations**: giảm allocation không tự động cải thiện end-to-end p99 hay toàn bộ CPU.
10. **Không tuyên bố physical stack location** nếu chưa có bằng chứng cấp machine-code/runtime tương ứng.

### 15.1. Vì sao `new` trong vòng lặp không có nghĩa 1 triệu allocations?

```java
static long sum(int limit) {
    long result = 0;
    for (int i = 0; i < limit; i++) {
        Pair pair = new Pair(i, i + 1);
        result += (long) pair.left + pair.right;
    }
    return result;
}
```

Đây là **snippet**, cần định nghĩa class `Pair` có fields `left/right`. Nếu C2 inline/optimize, đôi khi object không còn materialized. Với interpreter hoặc nếu optimization không áp dụng, allocations vẫn có thể được thực hiện.

Đếm từ khóa `new` hoặc số vòng lặp chỉ đếm **source-level allocation expressions**, không đo actual heap allocations. Ngược lại, nếu JFR cho thấy allocation events ít hơn, cần hiểu JFR có thể dùng sampling/throttling tùy events/settings chứ không có nghĩa nó là exact per-object tracing.

### 15.2. JFR để đo theo vùng phương pháp

Khởi chạy:

```bash
java -XX:StartFlightRecording=filename=ea-test.jfr,settings=profile \
     EscapeAnalysisDemo
```

Một recording rất ngắn có thể có ít sự kiện hoặc không đủ warm-up. Java Flight Recorder có nhiều event types/thresholds/sampling settings. Trong JDK 17, theo dõi allocation/GC/compilation events với cấu hình thích hợp, sau đó xem trong JDK Mission Control hoặc tooling JFR.

JFR **không phải oracle xác nhận C2 đã stack-allocate một object**. Khi allocation rate giảm, điều có thể đúng là allocations đã bị eliminated; stack allocation vẫn phải có bằng chứng riêng từ implementation, không suy chỉ từ số alloc events.

### 15.3. GC logs có thể hỗ trợ, nhưng không kết luận được riêng EA

```bash
java -Xms256m -Xmx256m \
     -Xlog:gc*:file=gc-ea.log:uptime,level,tags \
     EscapeAnalysisDemo
```

Trong workload thực, có thể so allocation rate và GC frequency khi EA bật/tắt. Nhưng:

- GC logs thường chỉ cho **collectors events**, không phải danh sách mọi `new`.
- Một chương trình nhỏ có thể không trigger GC nào trong test run.
- Một ít GC events hơn không chứng minh chính xác allocation nào bị eliminated.
- Heap committed/RSS không nhất thiết giảm ngay dù số allocations giảm.
- Cần profiler/JIT compiler analysis khi muốn chỉ rõ một allocation site.

## 16. Microbenchmark: tránh “đo” chính compiler bỏ hết công việc

**Phản ví dụ:**

```java
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    new Pair(i, i + 1);
}
long elapsed = System.nanoTime() - start;
System.out.println(elapsed);
```

Đây là **snippet phản mẫu**, không phải một class độc lập. Nếu việc tạo `Pair` không có observable effects, JIT có thể xóa toàn bộ work; kết quả đo có thể phản ánh một vòng lặp rỗng hoặc ít work hơn dự kiến. Không thể kết luận “stack allocation cực nhanh” từ một benchmark như vậy.

**Phương pháp tốt hơn:**

- Sử dụng kết quả tính toán thật sự (hoặc JMH Blackhole phù hợp).
- Cố định inputs nhưng tránh compiler biết trước/tối ưu toàn bộ một cách không đại diện.
- Warm-up, nhiều iterations/forks, đo memory allocations bằng profiler được cấu hình đúng.
- Tách benchmark **candidate scalarization** khỏi benchmark **object graph escape**.
- Chạy JDK17 cùng flags, heap size và workload trong các lần đối chứng.
- Nếu muốn xem compiler IR, dùng tooling/diagnostic build phù hợp; không áp dụng “mắt thường thấy ít GC thì object nằm stack”.

### 16.1. JMH không thay profiler

**JMH (Java Microbenchmark Harness)** giúp giảm nhiều sai lầm benchmark về JIT compilation, warm-up và dead-code elimination, nhưng **không** tự chỉ ra nguyên nhân nội bộ của mọi performance delta. Khi đo allocation rate, có thể dùng JMH GC profiler/appropriate JVM diagnostic tooling và xem kết quả theo bytes/op; xác thực JVM/GC/JIT settings.

**Bytes/op thấp** không tự chứng minh “object nằm stack”. Nó có thể xuất phát từ scalar replacement, allocation elimination hoặc loại bỏ hoàn toàn work.

## 17. Các sai lầm thường gặp khi đọc bộ nhớ

| Khẳng định sai | Tại sao sai | Cách nói đúng hơn |
| --- | --- | --- |
| “Biến local ở stack → object cũng ở stack.” | Biến có thể là reference; object có lifetime khác. | JVM stack giữ frame/values; object materialized thường ở Java heap. |
| “Object không escape thì luôn stack allocate.” | HotSpot C2 dùng scalar replacement, không mặc định stack allocation. | Non-escaping object có thể **được loại allocation** nếu đủ điều kiện. |
| “Mỗi `new` gây một GC heap object.” | JIT có thể optimize allocation đi. | Phân biệt bytecode allocation expression với runtime allocation. |
| “Không thấy GC events nghĩa objects đã ở stack.” | Có thể do heap đủ chỗ, TLAB, ít allocations, GC chưa chạy. | Đo allocations/profiler và compiler behavior. |
| “TLAB là thread stack.” | TLAB là heap allocation buffer. | Vẫn là heap allocation nhưng đường nhanh. |
| “Scalar replacement chuyển field values sang heap.” | Scalar values có thể ở registers, stacks hoặc tối ưu bỏ. | Object không cần tồn tại nguyên khối nếu semantics cho phép. |
| “Đối tượng return từ helper không thể được loại.” | Caller may inline helper. | Escape phải xét compiled scope/graph. |
| “Nếu không có cross-thread sharing thì chắc NoEscape.” | Object có thể sống qua method qua ThreadLocal. | Thread confinement khác method escape. |
| “EA lúc nào cũng loại mọi lock của local object.” | Compiler cần chứng minh correctness. | Lock elimination có điều kiện, không tự do bỏ synchronization. |
| “Record luôn được unbox thành primitive.” | Record Java17 vẫn là reference class. | Nó có thể là candidate scalar replacement. |
| “Một heap dump không thấy `Pair` nghĩa nó nằm trên stack.” | Object có thể đã chết/được GC/eliminated/chưa materialized. | Phải phân tích runtime conditions và compiler. |
| “`javap` có `new` chứng minh JIT đã cấp phát.” | Bytecode khác optimized machine code. | `javap` cho source-level VM instructions. |
| “Tắt EA sẽ làm logic chương trình sai.” | Optimization không được đổi Java semantics. | Có thể đổi performance/allocations, không đổi kết quả hợp lệ. |
| “Đổi JVM flag một lần là đủ chứng minh.” | JIT/GC/timing biến thiên. | Thử nhiều forks và workload đại diện. |
| “Scalarized object không thể xuất hiện lúc deopt.” | HotSpot có metadata/materialization. | Runtime dựng lại Java state khi cần. |
| “C2 luôn biên dịch ngay method sau 10.000 calls.” | Tiering/hotness/heuristics phụ thuộc workload/build. | Xác minh compiler activity thực tế. |

## 18. Bài tập có đáp án

**Câu 1.** Trong `Point p = new Point(1,2)` ở một method local, có thể chắc chắn object nằm trên stack không?

**Đáp án:** **Không.** `p` là reference; runtime object có thể được cấp phát trên heap, hoặc C2 có thể loại allocation nhờ scalar replacement; đó không đồng nghĩa stack allocation.

**Câu 2.** Nếu C2 loại toàn bộ allocation, có còn một object header của `Point` trên stack không?

**Đáp án:** Không có lý do phải tồn tại một object header nguyên vẹn. Compiler có thể chỉ giữ các scalars/giá trị đã tính, thậm chí xóa luôn computations dư.

**Câu 3.** Object được tạo rồi trả reference cho caller. Có chắc chắn allocation không bao giờ bị eliminated không?

**Đáp án:** Không. Caller có thể được inline và reference không còn escape khỏi compiled region. Cần phân tích graph tối ưu thực tế.

**Câu 4.** Object được lưu vào `static volatile Object shared` và dùng từ thread khác. Nó có thể đơn giản bị biến thành hai int locals của caller không?

**Đáp án:** Không theo cách phá observable shared-reference semantics. Object có thể cần materialize và maintain identity/visibility.

**Câu 5.** Nếu benchmark không dùng kết quả của `new Box(...)`, một thời gian cực ngắn có chứng minh stack allocation hiệu quả không?

**Đáp án:** Không. Compiler có thể dead-code-eliminate toàn bộ object creation và các computations không quan sát được.

**Câu 6.** TLAB là stack hay heap?

**Đáp án:** TLAB là **một vùng heap**, cho thread cấp phát nhanh. Object trong TLAB vẫn là heap object.

**Câu 7.** `-XX:-DoEscapeAnalysis` có nghĩa JVM bỏ qua mọi khả năng tối ưu không?

**Đáp án:** Không. Nó tắt EA theo HotSpot option, nhưng constant folding, DCE, inlining, các tối ưu khác vẫn có thể diễn ra.

**Câu 8.** Có thể lấy số objects trong heap dump lúc cuối process để tính chính xác bao nhiêu `new` đã chạy không?

**Đáp án:** Không. Heap dump chỉ chứa objects còn tồn tại tại snapshot; objects đã GC và allocations eliminated không hiện diện.

**Câu 9.** Scalar replacement có buộc tất cả fields ở CPU registers không?

**Đáp án:** Không. Scalars có thể được register allocate, spill vào stack slot, constant-fold hoặc tối ưu bỏ.

**Câu 10.** Vì sao JVM có thể materialize object khi deoptimization xảy ra?

**Đáp án:** Vì Java execution state sau fallback có thể cần object identity/reference; JIT lưu metadata để tái tạo trạng thái đúng.

**Câu 11.** `new int[2]` và `new int[100_000]` có khả năng scalar replacement giống nhau không?

**Đáp án:** Không. Arrays nhỏ, kích cỡ/indices rõ có thể dễ optimize hơn; arrays lớn/dynamic indices khó hơn và có compiler limits.

**Câu 12.** Vì sao “object không escape thread” không đủ để nói object có thể bị xóa?

**Đáp án:** Nó vẫn có thể sống sau method thông qua `ThreadLocal` hoặc một cấu trúc riêng của thread. Method lifetime và thread visibility là hai tiêu chí khác.

**Câu 13.** `EscapeAnalysisDemo` in `sum` bằng bao nhiêu, tại sao?

**Đáp án:** **10.000.000.000**, vì tổng các số lẻ `1,3,...,199999` bằng `100000²`.

**Câu 14.** Kết quả `distinctObjects=true` có thay đổi khi JIT bật scalar replacement không?

**Đáp án:** Không. JVM phải giữ semantics hai allocations riêng khi identity được quan sát trong phép so.

**Câu 15.** Nếu `System.identityHashCode` có trong method, JIT luôn cấm mọi dạng allocation elimination ở cả method không?

**Đáp án:** Không nhất thiết cấm toàn method; nó có thể tạo ràng buộc tại object/site liên quan, còn những allocations khác vẫn tối ưu được. Cần quan sát compiler behavior thật.

**Câu 16.** Khi code dùng `synchronized` trên một object cục bộ không escape, lock elimination có an toàn không?

**Đáp án:** Có thể, nếu compiler chứng minh không phá semantics/observable synchronization; không được hiểu rằng mọi `synchronized` là không cần thiết.

**Câu 17.** Chạy `-Xint` và baseline cho stdout khác nhau ở ba demos thì nên làm gì?

**Đáp án:** Kiểm tra code/data race/undefined assumptions và môi trường, vì logic Java hợp lệ phải giữ kết quả Java-level; khác biệt về performance/GC events thì bình thường.

**Câu 18.** Nên viết gì trong tài liệu kỹ thuật thay cho “HotSpot đưa object local lên stack”?

**Đáp án:** “HotSpot C2 có thể **loại bỏ allocation** của một object khi escape analysis và scalar replacement chứng minh đủ điều kiện; nếu allocation vẫn cần thực hiện, object thường được cấp phát trên heap.”

## 19. Tóm tắt

```text
Source:
    new Pair(a, b)
          │
          ▼
  Escape analysis của JIT
    │
    ├─ Có escape hoặc không thể chứng minh NoEscape
    │       │
    │       ▼
    │    Materialized object có thể phải nằm Java heap
    │       ├─ TLAB/Eden fast allocation
    │       └─ đường allocation khác theo GC/VM
    │
    └─ NoEscape + scalar replaceable + compiler chọn tối ưu
            │
            ▼
      Scalar replacement
        field.left  → scalar a
        field.right → scalar b
            │
            ▼
      Allocation elimination
      (object có thể không tồn tại nguyên khối)
            │
            ▼
      Deoptimization khi cần:
      reconstruct state / materialize object phù hợp

KHÔNG CÓ HỆ QUẢ TẤT YẾU:
  "NoEscape ⇒ đặt nguyên Java object lên native stack".
```

**Chốt kiến thức:** **Escape analysis** cung cấp thông tin về các đường object reference có thể đi ra khỏi phạm vi. Trên **HotSpot C2/Java 17**, tối ưu chủ chốt là **scalar replacement kết hợp allocation elimination**, có thể làm object không cần được cấp phát dưới dạng một đơn vị nguyên vẹn. **Stack allocation** là một cơ chế khác và không phải kết luận mặc định của EA trong HotSpot. Nếu object thực sự phải tồn tại và được truy cập qua references có thể quan sát, VM dùng heap allocation theo policy; **TLAB vẫn thuộc heap**. Hãy đo bằng benchmark/profiler và so EA bật–tắt, không suy physical memory placement từ từ khóa `new`, phạm vi biến local hoặc sự im lặng của GC logs.

### Tài liệu tham khảo và nguồn đối chiếu

- [HotSpot Escape Analysis and Scalar Replacement Status](https://cr.openjdk.org/~cslucas/escape-analysis/EscapeAnalysis.html): mô tả EA, scalar replacement và ghi rõ HotSpot không thực hiện stack allocation theo nghĩa đang tranh luận.
- [Oracle Java HotSpot VM Performance Enhancements — Escape Analysis](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/performance-enhancements-7.html): các nhóm NoEscape/ArgEscape/GlobalEscape, scalar replacement, lock elimination và không thay heap allocation bằng stack allocation.
- [Oracle Java SE 17 — `java` launcher documentation](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `DoEscapeAnalysis`, `PrintFlagsFinal`, `-Xint`, `-Xlog` và JFR.
- [OpenJDK JDK 17 C2 VM options](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/opto/c2_globals.hpp): `EliminateAllocations`, `EliminateLocks`, `DoEscapeAnalysis` và debug-only printing flags.
- [OpenJDK JDK 17 `escape.cpp`](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/opto/escape.cpp): connection graph, NoEscape và scalar-replaceable allocation paths.
- [JVMS Java SE 17 §2.5 — Runtime Data Areas](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5): JVM stacks, frames và heap model.
- [Java SE 17 Javadoc — `java.lang.Record`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Record.html): records là reference classes trong Java 17.
- [Microsoft for Java Developers — Improving OpenJDK Scalar Replacement](https://devblogs.microsoft.com/java/improving-openjdk-scalar-replacement-part-2-3/): ví dụ những limitations và điều kiện phức tạp của scalar replacement.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-007">Loại bỏ khóa không cần thiết khi đối tượng không được chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# Loại bỏ khóa không cần thiết khi đối tượng không được chia sẻ

## 1. Bài toán: tại sao mã có `synchronized` đôi khi chạy mà không cần thực thi khóa?

Trong Java, từ khóa **`synchronized`** cung cấp cơ chế khóa monitor và các quan hệ **happens-before** theo Java Memory Model (JMM). Việc có keyword `synchronized` trong Java source/bytecode **không có nghĩa mọi lần thực thi trên HotSpot bắt buộc thực hiện đầy đủ thao tác monitor tại mức machine code**.

Khi **HotSpot C2 JIT compiler** chứng minh một monitor chỉ bảo vệ một đối tượng **không thể được chia sẻ theo cách dẫn tới cạnh tranh giữa các threads**, nó có thể **loại bỏ những thao tác khóa không cần thiết** (*lock elimination*, cũng gọi là *lock elision* trong bối cảnh này).

Ví dụ cơ bản:

```java
static int calculate(int x) {
    Object privateLock = new Object();
    synchronized (privateLock) {
        return x * 3 + 1;
    }
}
```

Ở mức source:

1. Mỗi lời gọi `calculate` tạo một monitor object mới.
2. `privateLock` không được trả ra ngoài, không ghi vào field/static field và không truyền cho thread khác.
3. Không có thread khác có reference hợp lệ để yêu cầu khóa **chính monitor object đó** trong thời gian đối tượng được dùng.
4. HotSpot có thể chứng minh điều kiện và bỏ thao tác **monitor enter/exit** trong optimized machine code.

Điều này **không** có nghĩa Java bỏ qua `synchronized` tùy tiện. Đây là một phép tối ưu **chỉ hợp lệ khi compiler có đủ bằng chứng**, và phải bảo toàn mọi hành vi quan sát được của chương trình.

### 1.1. Kết luận trước khi đi sâu

- **Đối tượng không thoát và không bị chia sẻ:** lock có thể là thừa; C2 có thể loại bỏ.
- **Đối tượng được chia sẻ giữa các threads:** synchronization có thể cần cho **mutual exclusion, atomicity và visibility**; không tự được bỏ.
- **Bytecode vẫn có monitor instructions:** JIT có thể loại chúng khỏi **machine code tối ưu**, không cần sửa tệp `.class`.
- **Không được viết logic phụ thuộc việc tối ưu có xảy ra:** HotSpot có thể không loại khóa khi không inline đủ, profile chưa nóng hoặc compiler không chứng minh được.
- **Không phải cùng một thứ với biased locking, lock coarsening hay scalar replacement**, dù các tối ưu này có liên quan.

Bài này tập trung HotSpot/JDK 17+ (nhất là C2) và phân biệt rõ **ngữ nghĩa Java bắt buộc** với **tối ưu implementation-specific**.

## 2. Monitor và `synchronized` hoạt động như thế nào về mặt ngữ nghĩa?

Java cung cấp hai cách phổ biến dùng intrinsic monitor:

```java
synchronized (someLock) {
    updateSharedState();
}
```

và:

```java
synchronized void update() {
    // khóa implicit monitor của this
}
```

Đây là **snippets minh họa**, chưa khai báo `someLock`, `updateSharedState` hoặc class đầy đủ.

### 2.1. Mutual exclusion

Mỗi Java object có một monitor liên quan về mặt ngữ nghĩa. Nếu hai threads cố **acquire cùng một monitor** theo cơ chế `synchronized`, JVM phải tuân theo mutual exclusion và monitor rules: tại một thời điểm một thread giữ quyền sở hữu monitor đó (với khả năng **reentrant** của cùng thread).

Điều này giúp bảo vệ các thao tác *read-modify-write* trên dữ liệu chia sẻ, chẳng hạn `count++` không phải phép tăng atomic khi thiếu đồng bộ phù hợp.

### 2.2. Visibility và happens-before

Việc **unlock monitor M** *happens-before* lần **lock M** tiếp theo theo các quy tắc của JMM. Nhờ đó các writes được publish đúng qua monitor có thể được thread acquire cùng monitor quan sát theo quan hệ happens-before.

Điều rất quan trọng: chỉ cần monitor dùng riêng cho một object mà **không thể có thread thứ hai tham gia giao tiếp qua monitor đó**, các hiệu ứng đồng bộ với thread khác có thể **không mang giá trị thực**. Compiler có thể loại bỏ thao tác monitor khi chứng minh các ràng buộc liên quan không cần thiết, **nhưng không được loại bỏ các synchronizations mà chương trình hợp lệ dùng để chia sẻ dữ liệu**.

### 2.3. Reentrancy

Một thread đang giữ monitor có thể acquire **cùng monitor** thêm lần nữa; JVM phải quản lý mức reentrant của synchronization đúng ngữ nghĩa.

Ví dụ:

```java
synchronized (lock) {
    synchronized (lock) {
        operation();
    }
}
```

Đây là **snippet minh họa**; `lock` và `operation` phải được định nghĩa. Nếu lock object thực sự được chia sẻ, các thao tác khóa có thể cần được giữ về mặt ngữ nghĩa; các tối ưu **nested lock elimination** là chủ đề riêng, không chứng minh monitor là thread-private.

### 2.4. Đồng bộ vẫn quan trọng ngay cả khi không thấy data race trong thử nghiệm

Một chương trình có thể “chạy đúng” 1.000 lần thiếu khóa nhưng vẫn có data race. HotSpot **không được** suy luận khóa thừa chỉ vì một đợt benchmark chưa có thread tranh chấp.

Sự khác biệt giữa **không có tranh chấp được quan sát** và **compiler có bằng chứng đối tượng không thể chia sẻ** là trọng tâm của lock elimination.

## 3. Phân biệt lock object và dữ liệu được bảo vệ

Trong code:

```java
static final Object LOCK = new Object();
static int counter;

static void increment() {
    synchronized (LOCK) {
        counter++;
    }
}
```

`LOCK` là **đối tượng monitor**, còn `counter` là **dữ liệu được bảo vệ**. Vì `LOCK` nằm trong static field, nhiều threads có thể truy cập cùng instance monitor.

Đừng suy luận “`LOCK` không có fields nên JIT chắc loại bỏ nó”. Không có fields không làm một monitor chia sẻ trở thành thừa. `synchronized` có tác dụng **theo identity của monitor**, không theo số fields trong object.

Ngược lại:

```java
static int computeLocally(int x) {
    Object lock = new Object();
    synchronized (lock) {
        return x + 7;
    }
}
```

monitor này được tạo trong method, không được public/publish, nên là ứng viên tốt cho loại bỏ monitor operations. Kết quả không liên quan thread khác vì họ không thể acquire chính object đó qua bất kỳ reference nào.

### 3.1. Một lỗi thiết kế: tạo khóa mới mỗi lần để bảo vệ static state

```java
static int unsafeCount;

static void unsafeIncrement() {
    Object lock = new Object();
    synchronized (lock) {
        unsafeCount++; // KHÔNG bảo vệ shared state giữa các threads
    }
}
```

Đây là **anti-pattern**, không phải ví dụ thread-safe. Mỗi lời gọi dùng **một object khác nhau**. Hai threads không đồng bộ trên **cùng monitor**, nên `unsafeCount++` vẫn có data race/lost updates.

HotSpot có thể loại khóa, nhưng **lỗi correctness đã tồn tại trong source**. Đừng giải quyết bằng cách “tắt escape analysis để giữ monitor”, vì monitor mới tạo mỗi lần vẫn không bảo vệ static data giữa threads.

Đúng hơn là dùng một **monitor chung** hoặc cơ chế atomic thích hợp:

```java
static final Object COUNT_LOCK = new Object();
static int count;

static void safeIncrement() {
    synchronized (COUNT_LOCK) {
        count++;
    }
}
```

Để bảo vệ cùng tài nguyên, các threads phải đồng bộ trên **cùng monitor object**.

## 4. Escape analysis đặt điều kiện để bỏ khóa

Để quyết định có thể bỏ monitor enter/exit, C2 phải chứng minh khóa **không cần thiết để bảo toàn đồng bộ hóa và observable behavior**. HotSpot escape analysis (EA) giúp nhận diện object có thể được chia sẻ hoặc thoát ra ngoài compiled scope. Trong thuật ngữ implementation, **NoEscape**, **ArgEscape** và **GlobalEscape** mô tả các mức độ thoát khác nhau; chúng **không** là bảo đảm public Java API rằng monitor chắc chắn được loại.

Một biến local vẫn có thể trỏ tới object đã được công bố ra static field; ngược lại, compiler có thể nhìn xuyên helper calls khi **inlining** mở rộng phạm vi phân tích. Vì vậy “local object” không mặc định có nghĩa “monitor không cần thiết”, và việc object **không escape khỏi thread** vẫn chưa đủ nếu compiler không chứng minh được một số điều kiện khác.

**Điểm riêng của bài lock elimination:** compiler có thể bỏ **các thao tác khóa không quan sát được** nhưng phải giữ quy tắc `synchronized`, exceptions, happens-before và identity khi thực sự có chia sẻ. Scalar replacement có thể hỗ trợ hoặc đi kèm, song việc một allocation bị loại **không đồng nhất** với một monitor bị loại; bài trước đã tập trung vào trạng thái escape và mô hình object/scalars.

## 5. Quá trình HotSpot loại bỏ khóa

Một sơ đồ hợp lý cho ví dụ private monitor:

```text
Java source
  Object lock = new Object();
  synchronized (lock) { result = x * 3 + 1; }
                 |
                 v
javac tạo bytecode chứa monitorenter/monitorexit
                 |
                 v
HotSpot interpreter/C1/C2 execution
                 |
            method trở nên hot
                 |
                 v
       C2 inlining / escape analysis
                 |
      compiler có chứng minh được
      object không thể global escape?
            /                \
          Có                 Không
          |                    |
          v                    v
   Có thể loại bỏ         Giữ đủ monitor
   monitor operations     semantics runtime
          |
          +-- object scalar replace được?
                /           \
              Có             Không
              |                |
              v                v
       Có thể bỏ object    Object allocation
       allocation           vẫn có thể tồn tại
```

Đây là mô hình **khái niệm**, không hứa mọi HotSpot release thực hiện các passes đúng theo thứ tự đơn giản trên. EA có thể thất bại do giới hạn phân tích, call không được inline, đường exception hoặc điều kiện khác.

### 5.1. Compiler loại bỏ cái gì?

Khi an toàn, compiler có thể loại chi phí:

- Monitor enter/exit ở optimized path.
- Các checks/bookkeeping liên quan monitor nếu chứng minh là thừa.
- Trong trường hợp object được scalar replace, cả allocation của monitor object.
- Những thao tác nội bộ liên quan object header/locking mà không còn cần.

Nhưng việc **sinh/loại machine instructions** phụ thuộc JIT build. Java source và bytecode vẫn có `synchronized`/`monitorenter`/`monitorexit`.

### 5.2. Vẫn phải giữ tính đúng của exceptions

Nếu code trong critical section ném exception, Java compiler sinh các đường exception để **nhả monitor** theo semantics. JIT có thể tối ưu những đường được chứng minh không cần monitor, nhưng exception propagation phải đúng.

Không được giả định xóa monitor đồng nghĩa bỏ qua exception handling hoặc các side effects của body.

### 5.3. Compile không phải chạy interpreter và C2 cùng một lúc

Phương thức có thể ban đầu interpreted, rồi C1, cuối cùng C2. **Không có guarantee JIT level 4** sau đúng 10.000 invocations. Vì vậy cùng một source code có thể thấy runtime chi phí lock khác trong warm-up và steady state.

## 6. `synchronized` ở bytecode vẫn hiện hữu

Xem đoạn method:

```java
static int localCriticalSection(int value) {
    Object privateLock = new Object();
    synchronized (privateLock) {
        return value * 3 + 1;
    }
}
```

Biên dịch với `javac`, dùng:

```bash
javap -c -p LockElisionDemo
```

Đoạn bytecode cho block synchronized có **`monitorenter`** và **`monitorexit`**, cùng exception-handling path để bảo đảm monitor exit khi có lỗi. Một mẫu logic:

```text
... new java/lang/Object
... dup
... invokespecial java/lang/Object.<init>
... astore ...
... monitorenter
... iload ...
... imul
... iadd
... monitorexit
... ireturn
... monitorexit   // nhánh xử lý exception
... athrow
```

**Đây là sơ đồ diễn giải, KHÔNG phải dump bytecode đầy đủ từ một phiên bản compiler cụ thể.** Những instruction offsets/local slots/exception table entries thay đổi khi code/`javac` thay đổi.

### 6.1. Tại sao `javap` không chứng minh lock vẫn hoạt động ở CPU?

`javap` chỉ đọc **class file bytecode**. C2 compiled machine code được tạo **khi JVM đang chạy**; tối ưu đó không sửa class file. Vì vậy thấy `monitorenter` trong `javap` **không bác bỏ lock elimination ở JIT**.

### 6.2. Tại sao cùng bytecode có thể chạy khác?

Interpreter cần thực thi monitor semantics theo bytecode. C2 JIT có thể xem toàn bộ control flow và escape state, rồi chọn đường machine code tương đương nhưng rẻ hơn.

Một chương trình chạy với `-Xint` hay JIT disabled có thể có chi phí locking khác; không có yêu cầu hai execution modes phải phát cùng số machine instructions, chỉ phải bảo đảm Java semantics.

## 7. Ví dụ Java 17+ chạy được: private monitor và shared monitor

Tạo tệp **`LockElisionDemo.java`**:

```java
public final class LockElisionDemo {
    private static final Object SHARED_LOCK = new Object();
    private static int sharedCount;
    private static volatile long checksumSink;

    static int localCriticalSection(int value) {
        Object privateLock = new Object();
        synchronized (privateLock) {
            return value * 3 + 1;
        }
    }

    static void incrementShared() {
        synchronized (SHARED_LOCK) {
            sharedCount++;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        long total = 0;

        for (int i = 0; i < 100_000; i++) {
            total += localCriticalSection(i);
        }

        checksumSink = total;
        System.out.println("local checksum=" + checksumSink);

        Thread first = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                incrementShared();
            }
        });

        Thread second = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                incrementShared();
            }
        });

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("shared count=" + sharedCount);
        System.out.println("expected shared=40000");
    }
}
```

### 7.1. Biên dịch và chạy

Yêu cầu JDK 17 trở lên:

```bash
javac --release 17 LockElisionDemo.java
java LockElisionDemo
```

**Stdout chính xác khi chạy bình thường:**

```text
local checksum=14999950000
shared count=40000
expected shared=40000
```

Tệp Java này được biên dịch bằng `javac --release 17` và thực thi thành công trên HotSpot với các cấu hình mặc định, `-Xint`, `-XX:-DoEscapeAnalysis` và `-XX:-EliminateLocks`. **Mọi cấu hình đều cho cùng ba dòng trên.**

### 7.2. Vì sao checksum bằng 14.999.950.000?

`localCriticalSection(i)` trả `3*i + 1` với `i` từ 0 tới 99.999. Tổng:

```text
sum(i=0..99999) (3*i + 1)
= 3 * (99999 * 100000 / 2) + 100000
= 3 * 4,999,950,000 + 100000
= 14,999,950,000
```

Đây là phép toán trong Java `long total` nên không bị overflow ở tổng này. Trong `localCriticalSection`, phép `value * 3 + 1` cũng chưa vượt giới hạn `int` với input của chương trình.

### 7.3. Vì sao sharedCount chính xác 40.000?

Hai threads cùng gọi `incrementShared()` 20.000 lần. Mỗi lần tăng đều nằm trong:

```java
synchronized (SHARED_LOCK) {
    sharedCount++;
}
```

Vì cùng một **static final monitor object** được dùng, increments trên `sharedCount` được bảo vệ bằng mutual exclusion. Các lời gọi `Thread.join()` tạo quan hệ happens-before cần thiết để `main` đọc kết quả sau khi hai threads kết thúc.

**Nếu xóa khóa chung**, `sharedCount++` có data race và chương trình có thể bị mất cập nhật; không còn cơ sở bảo đảm kết quả là 40.000.

### 7.4. Một ví dụ có thể loại khóa; một ví dụ phải giữ semantics

- **`localCriticalSection`:** lock mới tạo, không được publish. Đây là **ứng viên tốt** cho HotSpot lock elimination.
- **`incrementShared`:** lock là object chung qua static field. Các threads có thể tranh chấp; JIT **không được phép loại monitor semantics** làm phép tăng mất tính atomic.

Lưu ý: **Stdout giống nhau không chứng minh machine code của localCriticalSection đã bỏ monitor thật**. Muốn xác nhận optimizer đã loại instructions, cần công cụ phân tích compiled code hoặc benchmark có kiểm soát cộng với bằng chứng hỗ trợ.

## 8. Các cờ HotSpot để quan sát và tạo đối chứng

### 8.1. Mặc định

```bash
java LockElisionDemo
```

HotSpot thường bật escape analysis và lock elimination cho optimizing compiler khi feature/build hỗ trợ. Đừng dựa hoàn toàn vào giả định mặc định; hãy kiểm tra flags hiện tại.

### 8.2. Tắt escape analysis

```bash
java -XX:-DoEscapeAnalysis LockElisionDemo
```

Cờ HotSpot này tắt escape analysis, có thể ảnh hưởng **nhiều tối ưu cùng lúc**, gồm scalar replacement và lock elimination. Vì thay nhiều cơ chế, **không dùng chênh lệch thời gian để quy toàn bộ tác động chỉ cho khóa**.

### 8.3. Tắt lock elimination

```bash
java -XX:-EliminateLocks LockElisionDemo
```

Tùy build/version, đây là cách tạo đối chứng **gần trực tiếp hơn** với những tối ưu liên quan lock. Cờ `EliminateLocks` của HotSpot có phạm vi implementation-specific, liên quan lock optimizations (gồm lock coarsening theo các bản source/flag descriptions). Vì vậy **không khẳng định nó tắt duy nhất một transformation và giữ nguyên mọi thứ khác**.

### 8.4. Chạy interpreter-only

```bash
java -Xint LockElisionDemo
```

Chế độ này yêu cầu thực thi Java bytecode bằng interpreter, không JIT compile bytecode ứng dụng. Nó là **đối chứng**, nhưng loại bỏ **mọi lợi ích JIT** chứ không chỉ lock elimination. Nếu `-Xint` chậm hơn, không thể kết luận toàn bộ sự chênh lệch là “chi phí synchronized”.

### 8.5. Kiểm tra flags thật trên runtime

```bash
java -XX:+PrintFlagsFinal -version
```

Các mục quan tâm gồm `DoEscapeAnalysis`, `EliminateLocks`, `TieredCompilation`. Hãy so khớp output theo JDK/vendor/architecture cụ thể.

### 8.6. Tại sao không mặc định dùng `-XX:+PrintEliminateLocks`?

Một số tài liệu cũ khuyên bật `PrintEliminateLocks` hoặc `PrintEscapeAnalysis`. Trong nhiều **production HotSpot builds**, đây là **non-product/debug-only flags**, không hoạt động dù có `-XX:+UnlockDiagnosticVMOptions`.

**Không hứa JDK 17 bản phát hành thông thường sẽ in được danh sách “eliminated lock” bằng flag đó**. Nếu cần xác nhận optimizer, dùng bản VM/tool có khả năng phân tích machine code, thử nghiệm JMH có kiểm soát, hoặc debug/non-product build được hỗ trợ.

## 9. Dùng `javap` và `PrintCompilation` đúng mục đích

### 9.1. `javap` chứng minh compiler đã tạo monitor bytecode

```bash
javap -c -p LockElisionDemo
```

Trong thử nghiệm với mã trên, `javap` hiển thị **`monitorenter`** và **`monitorexit`** trong các methods tương ứng. Đây là bằng chứng `synchronized` tồn tại ở bytecode, **không phải đo trực tiếp optimized JIT machine code**.

### 9.2. `PrintCompilation` xác nhận JIT đã chạy

```bash
java -XX:+PrintCompilation LockElisionDemo
```

Tìm các tên như:

```text
LockElisionDemo::localCriticalSection
LockElisionDemo::incrementShared
```

Trong một lần chạy thử trên HotSpot, log chứa các dòng kiểu:

```text
... 3 ... LockElisionDemo::localCriticalSection (25 bytes)
... 4 ... LockElisionDemo::localCriticalSection (25 bytes)
... 3 ... LockElisionDemo::incrementShared (25 bytes)
... 4 ... LockElisionDemo::incrementShared (25 bytes)
```

**Đây là ví dụ về phần ý nghĩa của các dòng log được quan sát, không phải output đầy đủ hoặc cố định.** Các số compile IDs, timestamps và thứ tự thay đổi tùy JVM. Level 3 thường là C1 với profiling, level 4 thường là C2.

### 9.3. Level 4 có chứng minh lock bị loại không?

**Không.** Level 4 chỉ chứng minh method được C2 compile trong lần chạy tương ứng, không cho biết mỗi `monitorenter` đã bị loại. Muốn chứng minh lock elimination ở một call site, cần thêm **quan sát compiled IR/assembly hoặc thông tin compiler phù hợp**.

### 9.4. Bytecode monitor và nmethod có thể khác

```text
javap:
  monitorenter / monitorexit có mặt trong class file
           |
           v
C2 optimized code:
  có thể vẫn giữ monitor operations
  HOẶC loại chúng nếu chứng minh được an toàn
```

Kết luận tối ưu phải dựa vào **compiled execution path thực tế**, không suy luận từ một cờ đang bật hay từ sự tồn tại của `synchronized` trong source.

## 10. Scalar replacement và lock elimination khác nhau ra sao?

Hai tối ưu này thường được nhắc cùng nhau vì đều dựa trên escape analysis, nhưng **không đồng nhất**.

### 10.1. Lock elimination

Mục tiêu: bỏ chi phí monitor khi compiler chứng minh các synchronization operations không cần để giữ Java semantics.

Một object vẫn có thể cần materialize vì identity được quan sát theo cách không cho phép loại allocation, nhưng compiler có thể vẫn có cơ hội tối ưu monitor trong trường hợp đối tượng đủ thread-local.

### 10.2. Scalar replacement

Mục tiêu: không materialize một object đầy đủ trên Java heap khi có thể biểu diễn các fields bằng scalar values.

```java
static int computeFields(int x) {
    Pair pair = new Pair(x, x + 1);
    return pair.left + pair.right;
}
```

Đây là **mã minh họa**, `Pair` cần được định nghĩa. Nếu `Pair` không escape và mọi uses phù hợp, C2 có thể thay object bằng các scalar values và không cần một heap allocation vật lý trong optimized path.

### 10.3. Allocation elimination không phải “đối tượng sẽ nằm trên stack”

HotSpot C2 theo mô tả Oracle không thực hiện stack allocation thông thường cho non-globally escaping objects. Việc một optimized allocation biến mất vì scalar replacement khác với việc “chuyển object đầy đủ từ heap sang stack”.

### 10.4. Có thể lock bị loại nhưng allocation chưa bị loại?

Về **khái niệm**, có. Một số escape states có thể đủ để loại lock nhưng không đủ để scalar replace object. Tuy nhiên, **một ví dụ cụ thể có thực hiện điều đó không** còn tùy JDK/compiler graph.

Không nên đọc kết quả benchmark rồi khẳng định “lock elimination là nguyên nhân duy nhất” nếu có thể đồng thời xảy ra allocation elimination, inlining hoặc loop optimization.

## 11. Vì sao inlining rất quan trọng?

EA thường hiệu quả hơn khi compiler thấy **toàn bộ đường sử dụng object**. Nếu monitor object được truyền cho một method và compiler có thể inline method ấy, đường tham chiếu có thể trở nên rõ ràng.

Ví dụ:

```java
static int guardedCompute(Object lock, int x) {
    synchronized (lock) {
        return x * 7;
    }
}

static int caller(int x) {
    Object fresh = new Object();
    return guardedCompute(fresh, x);
}
```

Đây là **snippet**, cần đặt trong class. Ở method `guardedCompute` nếu xét độc lập, tham số `lock` có thể đến từ bất kỳ caller nào và có thể được chia sẻ. Ở call site `caller`, object `fresh` mới tạo, không publish, và compiler **có thể thấy/inline** đoạn code.

Vì vậy:

- Nếu inline đủ, compiler có thể chứng minh monitor thread-private và loại lock.
- Nếu không inline (method quá lớn, call site đa hình, profile hạn chế, compilation budget...), compiler có thể phải giữ monitor.
- Không có ngưỡng kích thước method bắt buộc cho mọi JDK mà “dưới ngưỡng thì chắc chắn elide lock”.

### 11.1. Method boundaries không đồng nghĩa boundaries của EA

Một số compiler optimizations hoạt động tốt khi được inlined xuyên methods. Nhưng JIT không luôn inline mọi helper, nên cấu trúc call graph thực sự quan trọng.

### 11.2. Polymorphic calls có thể cản tối ưu

Nếu `guardedCompute` ủy quyền cho một interface call tới implementation chưa thể dự đoán, compiler có thể không biết callee có publish monitor hay không. Không chứng minh được no-escape thì an toàn nhất là giữ monitor semantics.

### 11.3. Reflection và native calls

Nếu một object được truyền vào reflective/JNI/native code, việc chứng minh nó không escape có thể khó hơn. JIT không được tự giả định mã ngoài Java không publish object hoặc không lưu reference nếu không có cơ sở phù hợp.

**Không gọi internal/native code chỉ nhằm ép khóa bị loại trong benchmark; thao tác đó thường làm EA khó hơn.**

## 12. Tính đúng khi object thực sự được chia sẻ

### 12.1. Static/shared monitor

```java
private static final Object LOCK = new Object();
private static int balance;

static void changeBalance() {
    synchronized (LOCK) {
        balance++;
    }
}
```

Nếu nhiều threads cùng gọi `changeBalance`, shared monitor có vai trò bảo vệ `balance++`. **Lock elimination không được làm mất mutual exclusion** khi chương trình chia sẻ cùng lock.

### 12.2. Publish monitor qua field

```java
final class Service {
    final Object lock = new Object();
    int count;

    void increment() {
        synchronized (lock) {
            count++;
        }
    }
}
```

Khi một `Service` instance được chia sẻ giữa threads, trường `lock` của **cùng instance** cũng được dùng chung. Do đó monitor có vai trò thực.

Ngược lại, nếu toàn bộ instance Service chứng minh chỉ dùng ở một thread trong một optimized scope, compiler có thể có cơ hội tối ưu. **Không quyết định từ chữ `final`**: `final Object lock` chỉ khiến reference field không thể được gán lại sau initialization bình thường; nó không cam kết object không bị publish.

### 12.3. Java Memory Model vẫn phải được giữ

Nếu code dùng đúng cùng monitor để publish shared state, JIT phải bảo toàn các happens-before relationships. Nó không thể loại bỏ khóa dựa vào phỏng đoán “trên máy thử không có contention”.

### 12.4. Một object được chia sẻ không có nghĩa mọi khóa liên quan đều bắt buộc tồn tại vật lý

JIT vẫn có thể tối ưu một số nested/merged monitor operations hoặc vùng không ảnh hưởng observable synchronization theo luật. Nhưng **không được đánh đồng** tối ưu cục bộ với việc loại toàn bộ monitor semantics của shared synchronization.

## 13. Không nhầm ba tối ưu: elimination, coarsening, nesting

| Tối ưu | Tác động | Điều kiện trực giác | Ví dụ |
| --- | --- | --- | --- |
| **Lock elimination** | Bỏ monitor operations | Chứng minh monitor không cần, như thread-private object | `synchronized(new Object())` với object không escape |
| **Lock coarsening** | Gộp nhiều khóa nhỏ liên tiếp thành vùng khóa lớn hơn | Compiler chứng minh gộp không đổi semantics | Nhiều synchronized blocks liên tiếp trên cùng monitor |
| **Nested lock elimination** | Bỏ chi phí khóa lồng có tính dư thừa | Reentrant lock cùng monitor trong một scope phù hợp | `synchronized(lock)` bên trong `synchronized(lock)` |

### 13.1. Lock coarsening không phải loại khóa

```java
synchronized (lock) { a(); }
synchronized (lock) { b(); }
```

Đây là snippet minh họa. Một số compiler optimizations có thể cân nhắc gộp thành vùng:

```java
synchronized (lock) {
    a();
    b();
}
```

nhưng **không phải lúc nào việc gộp cũng hợp lệ**. Nó thay đổi khoảng thời gian giữ lock; nếu thread khác có thể can thiệp giữa hai synchronized blocks, semantic differences phải được xem xét. Đây chỉ là mô hình minh họa cơ chế, không phải hướng dẫn refactor trực tiếp mọi code.

### 13.2. Lock elimination không phải biased locking

**Biased locking** là cơ chế HotSpot lịch sử nhằm tối ưu một số tình huống khóa ít có contention bằng cách thiên về thread quen thuộc. **JEP 374** đã tắt biased locking mặc định từ JDK 15 và deprecated các flags liên quan.

Điều quan trọng:

- Lock elimination dựa vào **compiler chứng minh synchronized operations là thừa**.
- Biased locking là một cách tối ưu **thực hiện locking** trong HotSpot trước đây.
- Tắt biased locking **không đồng nghĩa** tắt C2 escape analysis/lock elimination.
- Không sử dụng mô tả “HotSpot hiện nay luôn dùng biased locking” như kiến thức bất biến.

## 14. Vì sao `synchronized(new Object())` dễ được tối ưu?

Hãy nhìn trực tiếp:

```java
static int local(int x) {
    synchronized (new Object()) {
        return x + 1;
    }
}
```

Nếu không có code nào nhận được reference tới object literal allocation, một thread khác không thể acquire **cùng monitor** để cùng tham gia synchronization. Compiler có thể chứng minh không có hữu ích observable synchronization thông qua monitor đó.

Nhưng có một số lưu ý:

1. Đây là **điển hình ứng viên**, không phải cam kết JIT loại khóa trong mọi JDK/compile mode.
2. Source vẫn tuân Java semantics và tạo ra bytecode liên quan monitor; interpreter vẫn xử lý.
3. Monitor object có identity và allocation semantics, nhưng optimized path có thể được scalar replace khi đủ điều kiện.
4. Nếu body có side effects, exceptions hoặc calls phức tạp, JIT vẫn phải bảo toàn chúng.
5. Nếu một reference tới lock được publish ra ngoài, lập luận này không còn hợp lệ.

**Tuyệt đối không dùng khóa mới mỗi lần để bảo vệ dữ liệu chia sẻ**. Dù compiler bỏ khóa hay không, nó không thể đồng bộ hai threads trên hai objects khác nhau.

## 15. `synchronized` method và lock elimination

Một instance method:

```java
final class Counter {
    private int value;

    synchronized void addOne() {
        value++;
    }

    int value() {
        return value;
    }
}
```

khóa monitor của **`this`** khi gọi `addOne`.

Nếu một `Counter` instance được tạo rồi sử dụng riêng trong một method, không escape và compiler thấy rõ đường sử dụng, lock trong `addOne` **có thể là ứng viên loại bỏ**, thường cần inlining hoặc phân tích tương đương.

Ví dụ minh họa:

```java
static int isolated() {
    Counter counter = new Counter();
    counter.addOne();
    counter.addOne();
    return counter.value();
}
```

Đây là snippet cần ghép với class `Counter` ở trên. `Counter` không được publish trong method này, nên optimized code có thể đồng thời áp dụng **inlining**, **scalar replacement** và **lock elimination**.

Nhưng nếu gọi `counter` từ một static registry, thread khác có thể quan sát và gọi `addOne`; **không được giả định khóa bị loại**.

### 15.1. `synchronized static` khóa gì?

`static synchronized` method khóa **Class object** của class chứa method, không khóa một `new Object()` riêng cho mỗi invocation. Class mirror đó có thể được các threads dùng chung; không suy rằng static method không nhận tham số object thì khóa thừa.

### 15.2. Tại sao `StringBuffer` là ví dụ lịch sử kinh điển?

`StringBuffer` có nhiều methods synchronized theo API; khi một `StringBuffer` được tạo và dùng hoàn toàn cục bộ, HotSpot có thể tận dụng EA/inlining để giảm chi phí monitor ở các methods synchronized.

Tuy nhiên:

- `StringBuffer` còn có allocation/backing array, buffer expansion và copy chi phí riêng.
- `StringBuilder` được thiết kế cho tác vụ không cần synchronization và thường là lựa chọn source-level hợp lý nếu không chia sẻ.
- Đổi `StringBuffer` sang `StringBuilder` phải cân nhắc **API contract và publication**, không chỉ benchmark một trường hợp.
- Không có guarantee mọi `StringBuffer.append` được loại lock trong mọi JVM.

## 16. Các yếu tố làm C2 không loại được khóa

Một trường hợp source “trông local” vẫn có thể không được tối ưu do:

1. **Object escape:** lưu vào static/shared field hoặc trả reference cho caller không phân tích được.
2. **Method không được inline:** compiler không thấy hết uses hoặc call graph quá lớn.
3. **Polymorphism:** receiver/callee thay đổi nhiều, profile không đủ chắc.
4. **JNI/native/reflective paths:** compiler thiếu thông tin để kết luận non-escape.
5. **Compiler EA constraints:** độ phức tạp control-flow, memory graphs hoặc limits/thời gian phân tích.
6. **Cold method:** chưa compile lên tier hỗ trợ optimization đó.
7. **JVM flags:** `-Xint`, `-XX:-DoEscapeAnalysis`, `-XX:-EliminateLocks`, hoặc các compile directives.
8. **JDK/compiler version:** implementation thay đổi, tối ưu có thể xuất hiện/mất đi.
9. **Observability requirements:** monitor/object identity được dùng theo cách không thể loại bỏ mà vẫn giữ semantics.
10. **Deoptimization/safepoint state:** VM cần khôi phục đúng state khi optimized assumptions không còn phù hợp.

**Không nên sửa code chỉ để “làm EA chắc chắn tối ưu” bằng các mẹo không portable**, nhất là với code có shared mutable state.

## 17. Quan sát performance một cách khoa học

### 17.1. Thí nghiệm đối chứng gợi ý

Dùng **cùng** `LockElisionDemo.class` và JDK build, chạy các modes:

```bash
java LockElisionDemo
java -XX:-EliminateLocks LockElisionDemo
java -XX:-DoEscapeAnalysis LockElisionDemo
java -Xint LockElisionDemo
```

Những lệnh trên phù hợp để **kiểm tra correctness**. Demo chỉ in checksum và count, **không in timing**; vì vậy không lấy output này để tuyên bố speedup.

### 17.2. Vì sao so mode mặc định với `-Xint` chưa đủ chứng minh lock elimination?

`-Xint` tắt **toàn bộ JIT bytecode compilation**. Sự khác biệt hiệu năng có thể đến từ:

- Method-call overhead.
- Inlining.
- Loop optimization.
- Bounds-check/branch optimizations.
- Allocation elimination.
- Lock elimination.
- Interpreter dispatch overhead.

Muốn cô lập tác động riêng của lock elimination, `-XX:-EliminateLocks` là đối chứng gần mục tiêu hơn **nếu build hỗ trợ** và bạn đã kiểm tra các optimization tương tác.

### 17.3. Dùng JMH cho microbenchmark

**JMH (Java Microbenchmark Harness)** của OpenJDK giúp benchmark có:

- Warm-up và measurement iterations.
- Forked JVM processes với flags tách biệt.
- Blackhole/result consumption để tránh dead-code elimination.
- Statistical reporting, score units và error estimation.
- Baseline kiểm soát mức compiler optimization.

Một JMH plan hợp lý:

```text
Fork A:
  default HotSpot options

Fork B:
  -XX:-EliminateLocks

Fork C:
  -XX:-DoEscapeAnalysis  (phụ trợ, không cô lập riêng lock)

Same:
  JDK build
  CPU quota
  benchmark code
  data distribution
  warm-up and measurement parameters
```

Nếu benchmark method trả result đơn giản, optimizer có thể bỏ rất nhiều computation; cần thiết kế sao cho **workload đo thật sự còn tồn tại**.

### 17.4. Tại sao `System.nanoTime` vòng lặp đơn giản dễ sai?

Một vòng lặp nhỏ có thể được:

- Fully inlined.
- Unrolled.
- Strength-reduced hoặc constant-folded.
- Escape-analyzed và scalar-replaced.
- Dead-code eliminated nếu result không dùng.

Điều đó chính là thứ bạn đang nghiên cứu. Nhưng nếu thí nghiệm không kiểm soát, kết quả **không cho biết khóa nào bị loại**.

### 17.5. Không coi “tối ưu vô hình” là guarantee

Nếu benchmark mặc định nhanh hơn khi tắt EliminateLocks, kết quả **phù hợp** giả thuyết lock elimination giúp giảm overhead, nhưng vẫn cần các điều kiện thống kê và compiled-code evidence để gán nguyên nhân chắc chắn.

## 18. Phân tích assembly và compiler diagnostics

Muốn quan sát một monitor site đã bị loại ở machine code, có thể sử dụng những công cụ chuyên sâu tùy JDK build:

- **`-XX:+PrintCompilation`:** biết compilation level/event, **không biết lock đã loại**.
- **`-XX:+PrintInlining`:** hiểu một số inlining decisions, **không trực tiếp chứng minh lock bị loại**.
- **`-XX:+PrintAssembly`** với disassembler `hsdis` phù hợp: nhìn compiled code, nhưng phải có kinh nghiệm đọc assembly/GC stubs.
- **JITWatch** hoặc công cụ phân tích JIT logs: kết hợp bytecode/assembly/compilation metadata.
- **Debug/non-product HotSpot builds:** có thể có flags để in compiler transformations không có trong production release.

### 18.1. Cẩn trọng với PrintAssembly

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly LockElisionDemo
```

Đây là **lệnh gợi ý thử trên build có disassembler hỗ trợ**; nếu thiếu `hsdis` hoặc output khác kiến trúc, không thể mong đợi một bản assembly đầy đủ dễ đọc.

### 18.2. Deoptimization làm việc kiểm chứng khó hơn

Một compiled method có thể được deoptimize hoặc recompilation khi assumptions thay đổi. Code được đo ở thời điểm A chưa chắc là cùng compiled version ở thời điểm B. Cần kiểm tra compilation timeline/profile và warm-up.

### 18.3. C2 level 4 vẫn có thể chứa monitor

Compiler C2 không bắt buộc loại khóa nếu EA không đủ bằng chứng. Do đó:

```text
C2 compilation happened
             ≠
Every synchronized monitor in that method was removed
```

### 18.4. Bằng chứng tin cậy

Một kết luận mạnh cần có:

1. Bytecode thể hiện source synchronized.
2. HotSpot compiled version/machine code được nhận diện.
3. Phân tích cho thấy monitor operations tương ứng không còn ở optimized path, hoặc compiler IR/debug evidence đáng tin.
4. Thử nghiệm đối chứng/timing không trái với suy luận.
5. Correctness được giữ khi chạy interpreter/JIT và multi-thread sharing case.

## 19. Lock elimination không giải quyết mọi contention

Một chương trình thực tế thường khóa **đối tượng chia sẻ** để bảo vệ state:

```java
final class QueueState {
    private final Object lock = new Object();
    private int pending;

    void addTask() {
        synchronized (lock) {
            pending++;
        }
    }
}
```

Nếu cùng `QueueState` được truy cập từ nhiều threads, lock này có tác dụng mutual exclusion. **Không có “phép màu EA” tự loại contention** mà vẫn giữ cùng semantics của `pending++`.

Muốn tối ưu đúng, cân nhắc:

- Giảm thời gian giữ shared lock.
- Đưa I/O/blocking work ra ngoài critical section nếu logic cho phép.
- Chia state theo thread/shard để giảm sharing.
- Dùng atomic operations cho counter thật sự độc lập.
- Dùng concurrent collections/pools thay cấu trúc tự viết khi phù hợp.
- Kiểm tra false sharing, thread scheduling và allocation rate bằng profiler.
- Cân nhắc `ReentrantLock`/`StampedLock` hoặc cấu trúc khác khi có nhu cầu rõ, **không mặc định luôn nhanh hơn intrinsic lock**.

### 19.1. `LongAdder` và counters chịu tải

Nếu nhiều threads tăng counter độc lập với một invariant lớn, `LongAdder` có thể phù hợp hơn một single shared lock trong workload có contention. Nhưng `LongAdder.sum()` không cung cấp một atomic snapshot đối với concurrent updates giống mọi thao tác synchronized tùy ý.

Không thay synchronized bằng `LongAdder` khi logic cần transaction nhiều fields hoặc invariant phải được duy trì trong cùng critical section.

### 19.2. Không bỏ khóa để sửa benchmark

Nếu đồng bộ là cần thiết, **không xóa `synchronized` chỉ để đạt tốc độ cao**. Một benchmark ít tranh chấp không chứng minh an toàn concurrency trong production.

## 20. Vấn đề identity, null và synchronized exceptions

### 20.1. `synchronized(null)`

Java sẽ ném `NullPointerException` khi expression monitor được đánh giá ra `null`:

```java
Object lock = null;
synchronized (lock) {
    System.out.println("never reached");
}
```

Đây là snippet minh họa. JIT **không được** loại bỏ các semantics của NPE một cách sai khi tối ưu; chỉ có thể biến đổi khi hành vi quan sát được không đổi.

### 20.2. Lock identity quan trọng

Hai object khác nhau có thể `equals` nhau nhưng **không phải cùng monitor**. Java `synchronized` khóa theo **reference identity của object**, không theo `equals()` hoặc `hashCode()`.

Đừng dùng object mới với cùng giá trị `equals` rồi kỳ vọng những threads dùng hai instances tự đồng bộ với nhau.

### 20.3. Identity hash không phải bằng chứng khóa không thể loại

Gọi `System.identityHashCode(lock)` có thể làm compiler phải materialize identity theo những điều kiện cụ thể. Nhưng **không có quy tắc portable “gọi identityHashCode chắc chắn cấm lock elimination”** hoặc “nó luôn pin object ở một địa chỉ”.

Việc identity được quan sát, allocation được scalar replace và khóa có thể bị loại là những quyết định compiler riêng, phụ thuộc behavior cần bảo toàn.

### 20.4. Wait/notify và monitor state

`Object.wait()`/`notify()` yêu cầu monitor ownership và có semantics riêng. Nếu code gọi chúng trên một monitor, compiler phải giữ behavior về ownership, `IllegalMonitorStateException` và các tác động concurrency cần thiết. **Không thể xóa những operations này bừa bãi** chỉ vì tên biến là local.

### 20.5. `volatile` không thay thế khóa trong mọi trường hợp

`volatile` hữu ích cho visibility/ordering theo JMM; nhưng `counter++` gồm read-modify-write và **không trở thành atomic** chỉ vì `counter` là volatile. Nếu cần atomic increment hãy dùng lock hay atomic primitives phù hợp.

## 21. Những hiểu lầm phổ biến

| Nhận định sai | Diễn giải đúng |
| --- | --- |
| “Cứ có `synchronized` là CPU luôn thực hiện full monitorenter/exit.” | C2 có thể loại monitor nếu chứng minh synchronization không cần. |
| “Biến khóa là local thì object chắc chắn không escape.” | Object có thể được return, publish vào static/shared field hoặc truyền qua call. |
| “Tối ưu lock elimination làm `synchronized` mất Java semantics.” | JIT bắt buộc bảo toàn semantics quan sát được. |
| “Thử không có contention nên compiler chắc bỏ khóa.” | Không tranh chấp được quan sát **không bằng** chứng minh thread-private. |
| “`synchronized(new Object())` là cách bảo vệ shared count.” | Mỗi lần gọi dùng monitor khác, **không đồng bộ threads với nhau**. |
| “Nhóm NoEscape là keyword/type của Java.” | Là phân loại EA của compiler, không phải cấu trúc ngôn ngữ. |
| “Non-escaping object chắc được cấp phát trên stack.” | C2 có scalar replacement; không hứa stack allocation cho object. |
| “Lock elimination và scalar replacement luôn xảy ra cùng nhau.” | Có thể có những điều kiện khác nhau và compiler decisions riêng. |
| “`-XX:-DoEscapeAnalysis` chỉ tắt lock elimination.” | Nó có thể ảnh hưởng allocation/scalar replacement và nhiều tối ưu EA. |
| “`-XX:-EliminateLocks` là lựa chọn production phải dùng.” | Chủ yếu là cờ thí nghiệm/đối chứng, có thể làm performance xấu đi. |
| “`-Xint` là đối chứng cô lập riêng lock overhead.” | Nó tắt toàn JIT bytecode compilation và ảnh hưởng nhiều tối ưu khác. |
| “`PrintCompilation` level 4 chứng minh monitor bị xóa.” | Chỉ cho thấy C2 compilation, không chứng minh specific transformation. |
| “`javap` có monitorenter nghĩa JIT không thể loại khóa.” | `javap` đọc bytecode trước JIT, không đọc optimized machine code. |
| “Lock coarsening cũng là xóa toàn bộ monitor.” | Coarsening gộp/đổi phạm vi khóa; khác elimination. |
| “Biased locking và C2 lock elimination là một.” | Một cái là historic runtime locking optimization; một cái là compiler optimization. |
| “Object `final lock` không thể bị chia sẻ.” | `final` giữ reference field không đổi, không ngăn publish owner object. |
| “Mọi method nóng chắc được EA tối ưu.” | Inlining, compiler limits, escape paths và build quyết định. |
| “Shared monitor có thể bỏ vì JIT thấy update luôn thành công.” | Nếu threads dùng chung monitor, mutual exclusion/JMM phải được giữ. |
| “`synchronized(null)` có thể không ném lỗi nếu JIT đủ tốt.” | NPE semantics vẫn bắt buộc được bảo toàn. |
| “`volatile int++` là atomic.” | Không; volatile không tự làm compound increment atomic. |
| “Một lần timing nhanh hơn chứng minh lock elision.” | Cần warm-up, lặp, profiler/assembly hoặc compiler evidence. |

## 22. Bài tập tự kiểm tra có đáp án

**Câu 1.** Lock elimination là gì?

**Đáp án:** Một tối ưu HotSpot JIT có thể bỏ monitor enter/exit ở optimized machine code khi compiler chứng minh khóa không cần để bảo toàn Java semantics, thường nhờ escape analysis.

**Câu 2.** Tại sao tạo `new Object()` trong synchronized block có thể là ứng viên loại khóa?

**Đáp án:** Nếu object không được publish/escape, thread khác không thể acquire cùng monitor để phối hợp, nên thao tác khóa có thể được chứng minh thừa.

**Câu 3.** Khóa một `new Object()` ở mỗi lời gọi có bảo vệ static counter giữa nhiều threads không?

**Đáp án:** Không. Mỗi lời gọi có monitor riêng, nên không có mutual exclusion giữa threads trên static counter.

**Câu 4.** EA có mấy nhóm escape states nổi tiếng trong tài liệu HotSpot?

**Đáp án:** NoEscape, ArgEscape và GlobalEscape.

**Câu 5.** `NoEscape` có nghĩa JVM cam kết stack-allocate object không?

**Đáp án:** Không. HotSpot có thể scalar-replace allocation; không được suy nó tạo object trên stack.

**Câu 6.** Một object được đặt vào static field còn nên coi chắc chắn non-escaping không?

**Đáp án:** Không. Static field là một đường publish ra ngoài local scope, có khả năng được nhiều threads truy cập.

**Câu 7.** `synchronized(lock)` trên một static shared lock có thể bỏ vô điều kiện không?

**Đáp án:** Không. Nó có thể cần bảo đảm atomicity/visibility giữa threads.

**Câu 8.** Vì sao `javap` vẫn thấy `monitorenter` sau khi C2 tối ưu?

**Đáp án:** `javap` đọc `.class` bytecode; C2 tối ưu machine code trong JVM runtime, không sửa class file.

**Câu 9.** `PrintCompilation` level 4 có chứng minh lock elimination xảy ra không?

**Đáp án:** Không. Nó chỉ cho thấy một C2 compilation event, không báo riêng monitor đã được loại.

**Câu 10.** Sự khác nhau giữa lock elimination và lock coarsening?

**Đáp án:** Elimination bỏ monitor operations chứng minh thừa; coarsening có thể gộp vùng synchronized theo điều kiện giữ semantics.

**Câu 11.** `-Xint` có là đối chứng chỉ riêng lock elimination không?

**Đáp án:** Không. Nó tắt JIT compilation cho bytecode nên còn loại bỏ nhiều tối ưu khác.

**Câu 12.** `-XX:-DoEscapeAnalysis` có thể ảnh hưởng gì ngoài khóa?

**Đáp án:** Scalar replacement, allocation elimination và các tối ưu EA khác.

**Câu 13.** JDK 17 còn bật biased locking mặc định không?

**Đáp án:** Không. JEP 374 đã tắt biased locking theo mặc định từ JDK 15; nó khác lock elimination.

**Câu 14.** Synchronized instance method khóa monitor nào?

**Đáp án:** Monitor của `this` instance nhận lời gọi.

**Câu 15.** Static synchronized method khóa monitor nào?

**Đáp án:** Monitor của `Class` object đại diện class chứa method.

**Câu 16.** Nếu `StringBuffer` chỉ dùng local thì mọi synchronized method chắc chắn bị JIT xóa khóa không?

**Đáp án:** Không. Nó là ứng viên tối ưu khi compiler có đủ bằng chứng; không có guarantee của Java/HotSpot cho mọi trường hợp.

**Câu 17.** Vì sao JMH phù hợp hơn đo một lần `System.nanoTime`?

**Đáp án:** JMH cung cấp forks, warm-up, measurement, blackhole và thống kê, giảm nhiều bẫy JIT benchmark.

**Câu 18.** Kết quả `local checksum` ở ví dụ là bao nhiêu?

**Đáp án:** `14999950000` với 100.000 giá trị `i = 0..99999`.

**Câu 19.** Hai threads cùng increment sharedCount 20.000 lần qua cùng monitor và join. Giá trị expected?

**Đáp án:** `40000` khi chạy hoàn tất bình thường.

**Câu 20.** Một monitor thật sự được dùng trong wait/notify có thể bị xóa tùy tiện không?

**Đáp án:** Không. Monitor ownership, wait/notify semantics và các exceptions phải được bảo toàn.

**Câu 21.** Nếu muốn chứng minh lock cụ thể đã bị loại ở machine code, cần quan sát gì?

**Đáp án:** Compiled code/IR hoặc compiler diagnostics đáng tin ở phiên bản HotSpot cụ thể, cộng thí nghiệm đối chứng; một log compilation đơn thuần không đủ.

## 23. Checklist ứng dụng và chẩn đoán thực tế

1. Xác nhận JVM là HotSpot và ghi **JDK build, CPU architecture, VM flags**.
2. Phân biệt **monitor object** với **dữ liệu được bảo vệ**.
3. Đảm bảo mọi threads muốn bảo vệ cùng invariant phải đồng bộ theo cùng cơ chế thích hợp.
4. Đừng xem tên biến local là bằng chứng đầy đủ của non-escape.
5. Tìm các escape paths: return, static/shared fields, callbacks, native calls.
6. Khi code hot, quan sát inlining/C1/C2/EA trong giới hạn công cụ.
7. Hiểu `javap` báo bytecode, không báo optimized assembly.
8. Dùng `-XX:-EliminateLocks` làm đối chứng có điều kiện, không coi là tuning mặc định.
9. Biết `-XX:-DoEscapeAnalysis` còn ảnh hưởng allocation optimizations.
10. Giữ code correctness ở `-Xint`, JIT và multi-threaded contexts.
11. Dùng JMH/benchmark nhiều forks và warm-up nếu đánh giá performance.
12. Không dựa vào `PrintEliminateLocks` trên bản JDK production nếu flag không được hỗ trợ.
13. Không nhầm lock coarsening/nested locks/biased locking với compiler lock elimination.
14. Khi shared state có contention thật, xử lý ở architecture/data sharing level thay vì trông cậy EA.
15. Đọc JMM/happens-before trước khi thay cơ chế đồng bộ.
16. Quan sát throughput, p95/p99 latency, CPU và GC cùng nhau khi tuning HotSpot.
17. Không thay đổi logic synchronization chỉ vì benchmark nhỏ dường như nhanh hơn.
18. Sau nâng JDK/đổi JVM vendor, **kiểm tra lại các giả thuyết performance** thay vì hardcode compiler behavior.

### Tóm tắt cuối bài

```text
Java source synchronized(lock)
                |
                v
javac giữ monitorenter / monitorexit bytecode
                |
                v
HotSpot JIT (khi code đủ hot)
                |
         Escape analysis
                |
     monitor không bị share?
          /           \
        Có            Không / không chứng minh được
        |                       |
        v                       v
  Có thể loại lock        Giữ monitor semantics
        |
        +--> Nếu scalar replace được:
             có thể loại cả object allocation

BẮT BUỘC trong cả hai trường hợp:
  - Mutual exclusion của shared synchronization vẫn đúng.
  - Happens-before / memory visibility vẫn đúng.
  - Exceptions, identity và observable Java semantics vẫn đúng.
```

**Thông điệp cốt lõi:** **HotSpot có thể loại bỏ khóa `synchronized` không cần thiết nhờ escape analysis khi đối tượng monitor không được chia sẻ theo cách gây tác dụng đồng bộ.** Đây là khả năng tối ưu của compiler, **không phải lời hứa rằng mọi khóa local đều được xóa**, càng không phải quyền xóa synchronization bảo vệ shared mutable state. Hãy thiết kế code đúng theo Java Memory Model trước; để HotSpot tự tối ưu khi nó có đủ bằng chứng.

### Tài liệu chính thức và mở rộng

- [Oracle JDK 21 — Java HotSpot VM Performance Enhancements, Escape Analysis](https://docs.oracle.com/en/java/javase/21/vm/java-hotspot-virtual-machine-performance-enhancements.html): NoEscape/ArgEscape/GlobalEscape, scalar replacement, elimination of non-global-escaping locks và ví dụ local StringBuffer.
- [Oracle JDK 17 — java Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): cờ `-XX:+DoEscapeAnalysis` và các JIT/diagnostic flags.
- [OpenJDK JDK 17u — C2 compiler flags](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/opto/c2_globals.hpp): `EliminateLocks`, `DoEscapeAnalysis`, `EliminateAllocations`, các flags debugging không có trong mọi product build.
- [OpenJDK — Escape Analysis and Scalar Replacement Status](https://cr.openjdk.org/~cslucas/escape-analysis/EscapeAnalysis.html): khả năng và giới hạn EA/C2, phân biệt stack allocation với scalar replacement.
- [JLS §17 — Threads and Locks (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): synchronized, memory model và happens-before.
- [JVMS §6.5 — monitorenter/monitorexit (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html): semantics của bytecode synchronization.
- [OpenJDK JEP 374 — Disable and Deprecate Biased Locking](https://openjdk.org/jeps/374): phân biệt biased locking lịch sử với C2 lock elimination.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): microbenchmark Java có warm-up, forks và output statistics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-008">Intrinsic và phụ thuộc vào kiến trúc CPU, JDK, cách triển khai</a>

<details>
<summary>Xem chi tiết</summary>

# Intrinsic và phụ thuộc vào kiến trúc CPU, JDK, cách triển khai

## 1. Intrinsic giải quyết vấn đề gì?

**Intrinsic** trong HotSpot là cơ chế JVM/JIT nhận ra một method hoặc thao tác thư viện Java đã biết và **có thể** thay đường biên dịch/thực thi thông thường bằng **compiler IR chuyên biệt, mã máy viết riêng hoặc runtime stub**. Tối ưu này tận dụng kiến thức mà compiler có về thư viện, CPU và runtime để làm công việc tương đương nhanh hơn.

Ví dụ, những operations có khả năng được HotSpot triển khai bằng intrinsics trên phần cứng phù hợp gồm:

- Đếm bit: `Integer.bitCount`, đếm leading/trailing zero bits.
- `Math.sqrt`, `Math.fma` và một số phép toán exact.
- `System.arraycopy`, so sánh arrays/strings theo đường chuyên biệt.
- Checksums `CRC32`, `CRC32C`; AES/SHA và các thuật toán xử lý crypto phù hợp.
- Một số operations nội bộ của BigInteger, Base64, runtime và atomic operations.

**Từ quan trọng nhất là “có thể”.** Có method ứng viên không có nghĩa mọi lần gọi sẽ được intrinsify. HotSpot phải xét phiên bản JDK, nền tảng CPU, cấu hình flags, compiler tier, method/call-site, JIT compilation state, input shape và các guards.

**Ba tầng khác nhau:**

1. **Java API contract:** kết quả, exception và các hành vi phải giữ.
2. **HotSpot implementation:** method nào có intrinsic ID, compiler/stub nào hỗ trợ, flags nào bật.
3. **CPU/hệ điều hành:** ISA extensions và khả năng VM dùng được những tính năng ấy.

~~~text
Java code:
  x = Integer.bitCount(mask);
              |
              v
      Library method call
              |
              v
  HotSpot nhận diện candidate?
       /               \
      No               Yes
      |                 |
      v                 v
 Normal path     Intrinsic path khả dụng?
                         /       \
                       Yes        No
                        |          |
                        v          v
                   Target IR/   Fallback
                   stub/code   library code
                        |          |
                        +-----+----+
                              |
                              v
                     Same Java result
~~~

**Intrinsics không phải thành phần bắt buộc của JVMS.** Bytecode Java portable theo JVM semantics; còn native compiled code mang tính phụ thuộc CPU/JVM.

## 2. Phân biệt intrinsic với method inlining

**Inlining** thường mở body của callee vào compiler IR của caller. **Intrinsification** thay/special-case operation bằng code hoặc IR do HotSpot biết riêng; nó có thể không giống body Java của callee.

~~~java
static int addOne(int x) {
    return x + 1;
}

static int compute(int n) {
    return addOne(n) * 2;
}
~~~

Hai methods trên là minh họa, không phải file Java chạy độc lập.

- Với **inlining**, optimizer có thể đưa body `addOne` vào `compute` và nhìn một biểu thức `(n + 1) * 2`.
- Với **intrinsic**, ví dụ `Integer.bitCount`, optimizer có thể dùng specialized bit-count node hoặc instruction sequence mà không cần triển khai từng bước giống Java library bytecode.

| Tiêu chí | Inlining | Intrinsic |
| --- | --- | --- |
| Nguyên lý | Mở callee IR ở call site | Thay/special-case bằng VM-known representation |
| Dựa vào method body? | Thường có | Không nhất thiết |
| Cần CPU instruction đặc biệt? | Không | Không bắt buộc; có thể dùng nhiều instructions/stub |
| Có thể đi kèm? | Có | Có |
| Bảo đảm dùng cho mọi call? | Không | Không |
| Cần đúng Java semantics? | Có | Có |

**Không thể suy** từ việc `javap -c` vẫn hiển thị `invokestatic` rằng JIT không dùng intrinsic: `javap` xem **class-file bytecode**, không phải optimized native code.

## 3. `@IntrinsicCandidate` là internal annotation, không phải lệnh ép JIT

OpenJDK có `jdk.internal.vm.annotation.IntrinsicCandidate`. Tài liệu trong source annotation cho biết:

- Một method/constructor được đánh dấu **có thể được HotSpot intrinsify**.
- Việc đánh dấu **không bảo đảm** intrinsic thực sự dùng trên mọi platform/cấu hình.
- Chỉ dành cho **JDK library maintainers**, không phải API ổn định cho application.
- Khi thư viện/HotSpot thay đổi, người bảo trì phải đảm bảo checks và semantics tương thích.
- Với `CheckIntrinsics`, HotSpot có thể kiểm tra sự nhất quán giữa các method được annotate và danh sách VM intrinsics khi loading class.

Không viết:

~~~java
// KHÔNG dùng trong application thông thường:
// @jdk.internal.vm.annotation.IntrinsicCandidate
// public static int fastWork(int x) { ... }
~~~

Annotation nội bộ **không tự tạo HotSpot intrinsic implementation** cho method tự viết. Thậm chí import package nội bộ `jdk.internal.*` vi phạm mục tiêu portable Java application và có thể bị module system hạn chế.

### Danh sách intrinsic ID ở đâu?

Trong HotSpot source, `src/hotspot/share/classfile/vmIntrinsics.hpp` mô tả các intrinsic IDs, method signatures và điều kiện liên quan. Nó phân biệt những intrinsic **được compiler thay bằng code/IR** và những **bytecode intrinsic** được compiler xử lý đặc biệt theo cách khác.

Danh mục này thay đổi qua JDK releases. Code ở nhánh OpenJDK `master` có thể khác JDK 17 hoặc JDK 25 trên máy; kiểm tra đúng **tag/branch tương ứng** khi cần nghiên cứu kỹ.

## 4. Giữ đúng ngữ nghĩa Java mới là điều bắt buộc

Mọi optimization phải giữ **kết quả và observable behavior được Java API bảo vệ**.

| Đối tượng | Nghĩa cần được giữ |
| --- | --- |
| `Integer.bitCount` | Đếm đúng số bit 1 theo biểu diễn nhị phân |
| `Math.fma` | **Fused** multiply-add rounding, không được thay tùy ý bằng hai phép được làm tròn riêng |
| `Math.addExact` | Ném `ArithmeticException` khi integer overflow |
| `System.arraycopy` | Bounds/null/type checks, overlap handling, partial copy khi type mismatch |
| Crypto digest | Algorithm/provider semantics và bytes output đúng |
| Atomic/volatile | Java Memory Model, atomicity và ordering phù hợp |
| Arrays/reference ops | Object identity, type safety, GC write barriers khi cần |

OpenJDK `IntrinsicCandidate` lưu ý đường intrinsic có thể không tự thực hiện các checks giống hệt Java method body **nếu checks đã nằm ở đường caller/guard tương đương**. Nhưng toàn bộ execution path phải đáp ứng contract; không thể “tối ưu” bằng cách cho phép truy cập ngoài bounds hay trả checksum sai.

**Compiler intrinsic không có nghĩa chỉ một CPU instruction.** Nó có thể là IR node mở rộng thành nhiều machine instructions, call tới stub native đặc biệt, hoặc nhánh code specialized với fallback.

## 5. Phụ thuộc CPU và ISA extensions

Một machine instruction chỉ tồn tại nếu CPU thực hỗ trợ feature tương ứng và VM được phép sử dụng nó. Ví dụ về các họ lệnh:

| Khả năng hardware | Ví dụ nhóm ISA | Java operation có thể được hỗ trợ |
| --- | --- | --- |
| Population count | x86 POPCNT, các phép đếm bit ARM | `Integer.bitCount` |
| Leading/trailing zero count | x86 LZCNT/TZCNT/BSR hoặc AArch64 CLZ và idioms khác | `numberOfLeadingZeros`, `numberOfTrailingZeros` |
| SIMD/vector | SSE/AVX trên x86, NEON/SVE trên ARM thích hợp | Arrays/strings/vectorized routines |
| AES acceleration | AES-NI trên một số x86, ARM crypto extensions | AES implementations |
| CRC | CRC instructions trên CPU hỗ trợ | CRC32C hoặc đường checksum tương ứng |
| SHA acceleration | SHA extensions của các CPU phù hợp | SHA provider code paths |
| Fused multiply-add | x86 FMA hoặc ARM64 FMA | `Math.fma` trên HotSpot phù hợp |

**Đặc biệt dễ nhầm:** CPU instruction x86 có tên `CRC32` thuộc nhóm CRC-32C (Castagnoli) phù hợp, **không có nghĩa mặc nhiên là IEEE CRC32 của `java.util.zip.CRC32`**. JVM phải sử dụng đúng thuật toán; hai cái có polynomial khác nhau.

Cùng một kiến trúc x86-64 không có nghĩa mọi chip có AVX-512, AES-NI, FMA hay POPCNT; extensions phụ thuộc CPU generation và feature bits. Trong virtual machines/hypervisor, một số features còn có thể bị mask.

### Fallback khi không có lệnh CPU

~~~text
Java API contract (không đổi)
              |
              v
HotSpot tìm hardware/VM-supported fast path
        /                \
       Có                Không
       |                   |
  specialized          ordinary optimized code /
  instruction/stub     library/software fallback
       |                   |
       +---------+---------+
                 |
                 v
          Observable result đúng
~~~

Thiếu acceleration **không có nghĩa Java API phải lỗi**. Java code gọi `Integer.bitCount` hoặc `CRC32.update` vẫn có implementation hợp lệ trên JVM/platform phù hợp, dù tốc độ có thể khác.

## 6. Intrinsic có thể thay đổi theo JDK và compiler

Cùng một Java binary, các môi trường có thể khác nhau ở nhiều chiều:

- **JDK release:** danh sách intrinsics, library methods, signatures và compiler improvements thay đổi.
- **JVM vendor:** HotSpot, OpenJ9 và những runtime khác có implementations/flags khác.
- **Compiler:** HotSpot C1, C2, JVMCI/Graal có khả năng intrinsify và policies khác nhau.
- **CPU/OS:** hardware features, platform backends, instruction selection và calling conventions khác.
- **JVM flags:** những nhóm UseXXXIntrinsics có thể bật/tắt tùy cấu hình.
- **Runtime state:** method hot chưa, JIT compile chưa, call-site shape và input type có phù hợp không.
- **Provider:** một `MessageDigest` hay `Cipher` có thể dùng provider khác với code path khác.

Một method **được JVM biết là intrinsic candidate** vẫn có thể được chạy bằng bytecode/library path nếu chưa compile hoặc đường intrinsic không khả dụng ở call site cụ thể.

**Sự khác biệt codegen không đồng nghĩa khác kết quả Java.** Trên HotSpot JDK 17 và JDK 25, native instruction sequence có thể khác, nhưng application vẫn phải quan sát semantics của Java API.

## 7. Ví dụ bit intrinsics: kết quả không đổi

Các biểu thức:

~~~java
int bits = Integer.bitCount(0xF0F0F0F0);
int leading = Integer.numberOfLeadingZeros(1);
int trailing = Long.numberOfTrailingZeros(1L << 40);
~~~

Là statements minh họa, không phải chương trình độc lập. Theo Java semantics:

- `0xF0F0F0F0` có 4 byte F0, mỗi byte có 4 bit 1, nên `bitCount` trả **16**.
- 1 trong 32 bit có **31 leading zeros**.
- `1L << 40` có bit đầu tiên ở vị trí 40 tính từ LSB, nên trailing zeros là **40**.

Trên CPU có POPCNT/LZCNT/TZCNT/CLZ phù hợp, JIT có thể sinh optimized instructions. Không suy ngược từ kết quả 16/31/40 ra CPU opcode đã thực thi: constant folding hoặc fallback cũng có thể cho cùng kết quả.

## 8. `Math.fma` và `Math.addExact` minh họa ràng buộc correctness

### Fused multiply-add

~~~java
double v = Math.fma(2.0, 3.0, 4.0); // 10.0
~~~

`Math.fma` có fused semantics: kết quả tương đương multiplication và addition với một rounding step theo API contract, không phải tùy ý thay bằng phép nhân rồi cộng với hai rounding steps. Với một số dữ liệu dấu phẩy động, `a * b + c` có thể cho kết quả khác `Math.fma(a,b,c)`.

Oracle JDK 25 `java` manual mô tả `-XX:+UseFMA` cho hardware-based FMA intrinsics trên những hệ thống có lệnh phù hợp (ví dụ các CPU Intel/ARM64 hỗ trợ). Trên CPU thiếu FMA extension, VM phải dùng implementation tương thích nếu API vẫn được hỗ trợ.

### Exact arithmetic

~~~java
Math.addExact(Integer.MAX_VALUE, 1);
~~~

Theo API, đoạn đó **ném `ArithmeticException`**. Oracle JDK 25 mô tả nhóm `-XX:+UseMathExactIntrinsics`. Intrinsic có thể tăng tốc phép toán kèm overflow check nhưng **không** được đổi thành `int` wraparound và âm thầm trả về `Integer.MIN_VALUE`.

## 9. `System.arraycopy`: không thể là memcpy mù quáng

`System.arraycopy` là ví dụ kinh điển về thao tác có thể được JVM tối ưu rất sâu nhưng phải giữ những điều kiện:

- Kiểm tra source/destination là arrays và không null.
- Kiểm tra vị trí, chiều dài, bounds và array component types.
- Nếu source/destination overlap, bảo toàn kết quả tương đương phép copy qua vùng tạm.
- Với reference arrays, kiểm tra assignability; nếu phần tử thứ k sai kiểu, phần prefix hợp lệ phía trước vẫn có thể được copy rồi `ArrayStoreException` được ném.
- Với arrays chứa references, cần các GC barriers/card marking phù hợp để object graph vẫn đúng.

Ví dụ:

~~~java
int[] a = {1, 2, 3, 4, 5};
System.arraycopy(a, 0, a, 1, 4);
// a = [1, 1, 2, 3, 4]
~~~

Nếu dùng routine copy forward thiếu xử lý overlap, kết quả có thể sai. Một arraycopy intrinsic phải chọn đúng direction/code path/guard, hoặc fallback.

**Không phải mọi `System.arraycopy` sẽ đi qua đúng cùng một stub.** Array type, length, alignment, overlap, CPU, GC và compiler decisions đều có thể ảnh hưởng.

## 10. CRC, SHA, AES và các flags HotSpot

Oracle JDK 25 `java` manual ghi các nhóm intrinsics:

| Nhóm thao tác | Flag có trong JDK 25 manual | Phụ thuộc |
| --- | --- | --- |
| CRC32 | `-XX:+UseCRC32Intrinsics` | CPU/backend support |
| CRC32C | `-XX:+UseCRC32CIntrinsics` | CPU/backend support |
| AES | `-XX:+UseAES`, `-XX:+UseAESIntrinsics` | Hardware AES extension |
| AES/CTR | `-XX:+UseAESCTRIntrinsics` | Algorithm/mode/hardware |
| GHASH | `-XX:+UseGHASHIntrinsics` | Crypto platform |
| SHA | `-XX:+UseSHA`, `-XX:+UseSHA256Intrinsics` và các variants | CPU/provider |
| Base64 | `-XX:+UseBASE64Intrinsics` | JVM/platform support |
| Adler32 | `-XX:+UseAdler32Intrinsics` | Build/platform |
| Math exact | `-XX:+UseMathExactIntrinsics` | Compiler path |
| BigInteger | `UseMultiplyToLenIntrinsic` và nhóm khác | Architecture/compiler |

**JDK 25 yêu cầu `-XX:+UnlockDiagnosticVMOptions` cho các flags kiểm soát intrinsics được nêu trong manual.** Ví dụ:

~~~bash
java -XX:+UnlockDiagnosticVMOptions -XX:-UseCRC32Intrinsics ChecksumIntrinsicDemo
~~~

Cờ chỉ có hiệu lực khi VM hỗ trợ; không tự tạo lệnh hardware nếu CPU thiếu. Dùng bản JDK khác nên kiểm tra docs/`java -version`.

### SHA provider không phải chi tiết phụ

`MessageDigest.getInstance("SHA-256")` lấy algorithm từ Java Security provider. Oracle JDK 25 có giới hạn về đường SHA hardware intrinsics trên provider `sun.security.provider.Sun` phù hợp. Vì vậy **không khẳng định mọi provider bên thứ ba đều được tăng tốc** bằng cùng HotSpot intrinsic.

**Intrinsic không làm thuật toán mật mã trở nên an toàn hơn về thiết kế.** Algorithm, mode, key, nonce, provider và side-channel considerations vẫn cần xử lý đúng.

## 11. Java 17+ runnable: bit, math, arraycopy, mismatch

Tạo file **`IntrinsicBasicsDemo.java`**:

~~~java
import java.util.Arrays;

public class IntrinsicBasicsDemo {
    public static void main(String[] args) {
        System.out.println("bitcount=" + Integer.bitCount(0xF0F0F0F0));
        System.out.println("leading=" + Integer.numberOfLeadingZeros(1));
        System.out.println("trailing=" + Long.numberOfTrailingZeros(1L << 40));

        System.out.println("sqrt=" + Math.sqrt(81.0));
        System.out.println("fma=" + Math.fma(2.0, 3.0, 4.0));

        int[] source = {10, 20, 30, 40};
        int[] dest = new int[3];
        System.arraycopy(source, 1, dest, 0, 3);
        System.out.println("copied=" + Arrays.toString(dest));

        byte[] left = {1, 2, 3};
        byte[] right = {1, 2, 4};
        System.out.println("mismatch=" + Arrays.mismatch(left, right));
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac -g IntrinsicBasicsDemo.java
java IntrinsicBasicsDemo
~~~

**Stdout chính xác khi chương trình hoàn tất bình thường:**

~~~text
bitcount=16
leading=31
trailing=40
sqrt=9.0
fma=10.0
copied=[20, 30, 40]
mismatch=2
~~~

Bài thực hành thể hiện **Java API semantics**, **không** chứng minh JVM sinh opcode POPCNT, FMA hay SIMD. JIT có thể constant-fold vì inputs cố định. Chạy `javap -c -p IntrinsicBasicsDemo` chỉ xem bytecode, không xem compiled native instructions.

## 12. Java 17+ runnable: CRC32, CRC32C và SHA-256

Tạo file **`ChecksumIntrinsicDemo.java`**:

~~~java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;

public class ChecksumIntrinsicDemo {
    public static void main(String[] args)
            throws NoSuchAlgorithmException {
        byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);

        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length);
        System.out.printf("crc32=%08x%n", crc32.getValue());

        CRC32C crc32c = new CRC32C();
        crc32c.update(data, 0, data.length);
        System.out.printf("crc32c=%08x%n", crc32c.getValue());

        byte[] sha = MessageDigest.getInstance("SHA-256")
                .digest("abc".getBytes(StandardCharsets.US_ASCII));
        System.out.println("sha256=" + HexFormat.of().formatHex(sha));
    }
}
~~~

Chạy:

~~~bash
javac -g ChecksumIntrinsicDemo.java
java ChecksumIntrinsicDemo
~~~

**Stdout chuẩn trên một JDK có SHA-256 provider khả dụng:**

~~~text
crc32=cbf43926
crc32c=e3069283
sha256=ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
~~~

Hai CRC có **polynomials khác nhau** nên kết quả chuẩn khác nhau; SHA-256 của chuỗi ASCII `abc` là test vector phổ biến. Nếu runtime cấu hình không có SHA-256 provider, method có thể ném `NoSuchAlgorithmException`, không phải intrinsic gây sai digest.

Thử với flags khi HotSpot hỗ trợ:

~~~bash
java -XX:+UnlockDiagnosticVMOptions -XX:-UseCRC32Intrinsics ChecksumIntrinsicDemo
java -XX:+UnlockDiagnosticVMOptions -XX:-UseCRC32CIntrinsics ChecksumIntrinsicDemo
~~~

**Kết quả API vẫn đúng** khi chương trình chạy thành công. Thay cờ chỉ thay những code paths được VM phép sử dụng, không đổi thuật toán hay test vectors.

## 13. Java 17+ runnable: exceptions vẫn được bảo toàn

Tạo file **`ArrayCopySemanticsDemo.java`**:

~~~java
import java.util.Arrays;

public class ArrayCopySemanticsDemo {
    public static void main(String[] args) {
        int[] overlap = {1, 2, 3, 4, 5};
        System.arraycopy(overlap, 0, overlap, 1, 4);
        System.out.println("overlap=" + Arrays.toString(overlap));

        Object[] mixed = {"ok", Integer.valueOf(17)};
        String[] target = new String[2];
        try {
            System.arraycopy(mixed, 0, target, 0, 2);
            System.out.println("unexpected-success");
        } catch (ArrayStoreException e) {
            System.out.println("caught=ArrayStoreException");
        }
        System.out.println("partial=" + Arrays.toString(target));

        try {
            Math.addExact(Integer.MAX_VALUE, 1);
            System.out.println("unexpected-success");
        } catch (ArithmeticException e) {
            System.out.println("caught=ArithmeticException");
        }
    }
}
~~~

~~~bash
javac -g ArrayCopySemanticsDemo.java
java ArrayCopySemanticsDemo
~~~

**Stdout chính xác:**

~~~text
overlap=[1, 1, 2, 3, 4]
caught=ArrayStoreException
partial=[ok, null]
caught=ArithmeticException
~~~

Trong arraycopy từ `Object[]` sang `String[]`, `"ok"` được copy trước, rồi `Integer` bị từ chối, nên target chứa `[ok, null]`. `addExact` phải ném exception khi tràn. **Dù HotSpot dùng intrinsics hay fallback, kết quả này vẫn phải hợp lệ**.

## 14. Flags bật khác chứng minh intrinsic thực sự chạy

Có bốn câu hỏi độc lập:

1. **JVM biết intrinsic ID không?** — source `vmIntrinsics.hpp` theo đúng JDK version.
2. **CPU/flags cho phép không?** — platform detection/VM initialization.
3. **Compiler/runtime có chọn intrinsic tại call site này không?** — profiling, tier, hotness, inputs, fallback.
4. **Native code thực tế là gì?** — compiler logs, disassembly hoặc profiling thích hợp.

Một flag `UseCRC32Intrinsics=true` chỉ cho biết **cấu hình cho phép**, chưa chứng minh method `CRC32.update` ở chỗ bạn nghiên cứu thực sự đi qua hardware instruction.

Cùng một method có thể:

- Chạy interpreted vì chưa đủ hot.
- Được C1 compile nhưng không dùng đường C2 tối ưu đặc biệt.
- Được C2 compile và intrinsify.
- Bị constant-fold vì input cố định.
- Gọi runtime stub chuyên biệt.
- Dùng fallback khi CPU thiếu instructions hoặc flags tắt.

**Không suy “intrinsics chỉ hoạt động ở C2” cho mọi VM operation.** HotSpot có compiler intrinsics lẫn runtime/library special-case paths, và mỗi tier/build có capabilities khác nhau.

## 15. Hướng dẫn quan sát HotSpot

Bắt đầu từ phiên bản/flags thật:

~~~bash
java -version
java -Xlog:help
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal -version
~~~

Có thể xem compilation/inlining:

~~~bash
java -XX:+PrintCompilation IntrinsicBasicsDemo
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining -XX:+PrintCompilation IntrinsicBasicsDemo
~~~

**Diagnostics có thể thêm dòng log**, nên stdout toàn process khi bật chúng không còn chỉ gồm các dòng output chương trình.

| Công cụ | Cho biết | Không chứng minh |
| --- | --- | --- |
| `java -version` | Vendor, JDK release, VM | Call site nào dùng intrinsic |
| `PrintFlagsFinal` | VM flag values | Compiled method đã dùng acceleration |
| `PrintCompilation` | Method compile events | CPU instruction cụ thể |
| `PrintInlining` | Inlining decisions | Mọi intrinsic path đã chạy |
| `LogCompilation` | Compiler log khi hỗ trợ | Thời gian chạy chuẩn khi logging bật |
| `PrintAssembly` + hsdis | Machine code theo công cụ hỗ trợ | Toàn bộ program/call sites nếu không capture đủ |
| JFR và profiler | Runtime behavior/alloc/CPU | Intrinsic codegen nếu không có instruction evidence |
| JMH | Microbenchmark hiệu năng | Bảo đảm intrinsic được sử dụng |

Oracle JDK 25 `java` manual mô tả `PrintAssembly` có thể cần external `hsdis` library. Không bắt buộc tải binary không rõ nguồn. Trên JVM khác HotSpot, những flags `-XX` có thể không tồn tại hoặc khác semantics.

### Fine-grained intrinsic controls

Trong OpenJDK HotSpot, `vmIntrinsics.hpp` có các control mechanisms như `ControlIntrinsic` / `DisableIntrinsic` bên cạnh nhóm `UseXXXIntrinsics`. Danh sách IDs và cú pháp phụ thuộc bản VM, nên **không đoán tên intrinsic ID từ Java method name** để thử trên production. Dùng flags nhóm chính thức đã tài liệu hóa sẽ dễ kiểm chứng hơn.

## 16. Đo hiệu năng intrinsic đúng cách

Một chương trình chỉ in `Integer.bitCount(0xFF)` **không phải benchmark POPCNT**, vì compiler có thể tính trước giá trị ngay lúc compile. Để nghiên cứu performance:

1. Sử dụng **JMH** với warmup/measurement iterations/forks phù hợp.
2. Tạo inputs thay đổi ở runtime, phản ánh kích thước và phân bố thực.
3. Dùng Blackhole hoặc consumption để tránh dead-code elimination.
4. So sánh **cùng JDK/CPU/workload** và chỉ thay một VM flag được hỗ trợ.
5. Tránh `println` hay verbose logs bên trong vùng đo.
6. Ghi rõ compiler tier, CPU ISA, provider và JVM flags.
7. Kiểm tra compiled machine code nếu mục tiêu là xác nhận opcode/ISA.
8. Báo kết quả biến thiên, throughput/latency, không chỉ một con số một lần chạy.

**Intrinsic không luôn nhanh hơn:** với input rất nhỏ, startup hoặc overhead guards/stubs chiếm ưu thế, đường code khác có thể tương đương hoặc tốt hơn. Thay đổi CPU/JDK/provider cùng lúc khiến so sánh không xác định được nguyên nhân.

### Production troubleshooting

Nếu throughput CRC/SHA khác nhau giữa nodes:

- Ghi `java -version` và flags `UseCRC32Intrinsics`/`UseSHA*Intrinsics`.
- Kiểm tra CPU feature masks, virtualization/hypervisor.
- Kiểm tra Java Security provider thực được chọn.
- So sánh compiled code/profile trên workload đã warmup.
- Xem CPU frequency, system load, GC và I/O để tránh gán mọi chênh lệch cho intrinsic.

Không chỉnh disable/enable intrinsic trên production trước khi kiểm chứng ở môi trường test.

## 17. Các ngộ nhận thường gặp

| Ngộ nhận | Điều đúng |
| --- | --- |
| “Intrinsic là bytecode mới.” | Thường là VM/compiler special case cho method/operation. |
| “`@IntrinsicCandidate` buộc HotSpot sử dụng.” | Chỉ đánh dấu candidate, không bảo đảm. |
| “Annotation trên method tự viết tạo intrinsic.” | Không; cần implementation trong VM. |
| “Một intrinsic luôn là một CPU instruction.” | Có thể là IR, nhiều instructions hoặc stub. |
| “Intrinsic = inlining.” | Không, có thể phối hợp nhưng cơ chế khác. |
| “Intrinsic lúc nào cũng nhanh hơn.” | Cần benchmark trên workload/CPU/JDK cụ thể. |
| “Tất cả CPU x86-64 có AES-NI/AVX-512.” | ISA extensions phụ thuộc hardware và feature masks. |
| “CPU instruction CRC32 là Java CRC32.” | x86 CRC32 tương ứng CRC32C polynomial trong ngữ cảnh thích hợp. |
| “JDK 17 và 25 buộc sinh cùng machine code.” | Compiler/library/backend thay đổi theo release. |
| “C1 và C2 hỗ trợ intrinsics giống hệt.” | Có thể khác. |
| “`javap -c` hiện calls chứng minh không intrinsic.” | `javap` chỉ xem bytecode. |
| “Flag UseXXX=true chứng minh opcode đã chạy.” | Chỉ cho biết điều kiện có thể cho phép. |
| “Tắt CRC32 intrinsic làm checksum khác.” | Java API contract không đổi. |
| “`Math.fma` luôn tương đương `a*b+c` về rounding.” | Fused semantics có thể khác. |
| “Arraycopy intrinsic được bỏ type checks.” | Exception/partial copy semantics phải được bảo toàn. |
| “CPU thiếu AES làm Java Cipher luôn lỗi.” | Provider có thể dùng software implementation đúng. |
| “SHA providers nào cũng dùng cùng HotSpot intrinsic.” | Provider/code path ảnh hưởng. |
| “Dùng JNI native method tự động thành intrinsic.” | JNI và compiler intrinsics khác nhau. |
| “JMH tự chứng minh CPU instruction.” | JMH đo performance; cần codegen evidence. |
| “Flags -XX thuộc Java standard.” | Vendor-specific, có thể khác theo build/version. |
| “Crypto nhanh bằng intrinsic nghĩa mode/key design an toàn.” | Security requirements độc lập với tốc độ. |

## 18. Tự kiểm tra có đáp án

**1.** Intrinsic là gì?  
**Đáp án:** HotSpot có thể special-case một method/operation qua compiler IR, machine code hoặc runtime stub để tối ưu.

**2.** `@IntrinsicCandidate` có bảo đảm được intrinsify?  
**Đáp án:** Không. Runtime CPU/JDK/compiler/flags quyết định khả dụng.

**3.** Intrinsic và inlining khác nhau ở đâu?  
**Đáp án:** Inlining mở body callee vào caller; intrinsic dùng VM-known specialized implementation.

**4.** Tại sao CPU bitcount khác nhau giữa x86 và ARM vẫn cho cùng Java result?  
**Đáp án:** Java API semantics cố định, machine instruction sequence do backend lựa chọn.

**5.** `Math.fma` khác `a*b+c` ở điểm nào?  
**Đáp án:** Fused rounding semantics có thể khác hai rounding steps.

**6.** CRC32 và CRC32C của `123456789` có giống nhau?  
**Đáp án:** Không: lần lượt `cbf43926` và `e3069283`.

**7.** Nếu arraycopy copy String rồi gặp Integer không phù hợp với String[] thì sao?  
**Đáp án:** Prefix hợp lệ có thể đã được copy; sau đó ném `ArrayStoreException`.

**8.** `UseCRC32Intrinsics=true` có chứng minh JIT dùng CPU CRC không?  
**Đáp án:** Không; cần xác nhận call site và compiled code.

**9.** JVM thiếu hardware AES extension có bắt buộc Java AES API không chạy?  
**Đáp án:** Không; implementation/provider có thể dùng software path phù hợp.

**10.** Tại sao số liệu intrinsic benchmark dễ sai khi input constants?  
**Đáp án:** JIT có thể constant-fold hoặc loại công việc, không đo thao tác thực.

**11.** Cùng CPU nhưng hai JDK releases có thể khác intrinsic availability không?  
**Đáp án:** Có, do VM source, policies, flags, library và compiler thay đổi.

**12.** Cách kiểm chứng CPU instructions thực tế?  
**Đáp án:** Xem compiled-code disassembly/profiling phù hợp trên đúng VM/JDK, không chỉ Java stdout hoặc `javap`.

## 19. Tài liệu tham khảo

- [OpenJDK — IntrinsicCandidate.java](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/jdk/internal/vm/annotation/IntrinsicCandidate.java): annotation nội bộ, điều kiện không bảo đảm intrinsic và cách giữ semantics.
- [OpenJDK HotSpot — vmIntrinsics.hpp](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/classfile/vmIntrinsics.hpp): intrinsic IDs, controls và feature dependence.
- [Oracle JDK 25 — java Command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): UseAES/CRC32/CRC32C/SHA/FMA/MathExact flags, UnlockDiagnosticVMOptions, PrintCompilation và PrintInlining.
- [OpenJDK HotSpot — globals.hpp](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/runtime/globals.hpp): implementation flags; nhánh master có thể khác bản JDK đang chạy.
- [Java SE 25 — System.arraycopy](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#arraycopy(java.lang.Object,int,java.lang.Object,int,int)): checks, overlap và partial-copy behavior.
- [Java SE 25 — Math.fma](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Math.html#fma(double,double,double)): fused multiply-add semantics.
- [Java SE 25 — Integer.bitCount](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Integer.html#bitCount(int)): bit count result contract.
- [Java SE 25 — CRC32](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/zip/CRC32.html) và [CRC32C](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/zip/CRC32C.html): hai checksum algorithms khác nhau.
- [Java SE 25 — Arrays.mismatch](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Arrays.html#mismatch(byte[],byte[])): index khác đầu tiên.
- [Java SE 25 — MessageDigest](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html): algorithm/provider behavior.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): đo hiệu năng JVM sau warmup có kiểm soát.

**Kết luận:** Intrinsic là cách HotSpot **có thể** thực thi method thông qua implementation đặc biệt, khai thác VM/compiler knowledge hoặc CPU ISA. Khả năng thực tế phụ thuộc **CPU, JDK release, VM implementation, provider, flags và JIT state**, nhưng **kết quả, exceptions và các ràng buộc Java API không được đổi**. Không nhầm intrinsic với inlining, JNI, vectorization hay một opcode duy nhất; không suy từ source code, bytecode hoặc một flag đã bật rằng CPU thực sự chạy intrinsic tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hot-spot-optimizations-009">Vì sao thay đổi mã nguồn nhỏ có thể làm kết quả hiệu năng thay đổi lớn</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao thay đổi mã nguồn nhỏ có thể làm kết quả hiệu năng thay đổi lớn?

Trong **HotSpot JVM**, một thay đổi chỉ vài ký tự trong Java source có thể tạo khác biệt hiệu năng lớn, nhỏ, hoặc thậm chí **đảo chiều** sau khi ứng dụng chạy đủ lâu. Nguyên nhân không đơn thuần là “dòng code ngắn hơn chạy nhanh hơn”: Java source được biên dịch thành **bytecode**, sau đó HotSpot có thể **thông dịch**, **thu thập profile**, **JIT compile** bằng C1/C2, **inline**, **devirtualize**, **loại bỏ allocation**, **tối ưu vòng lặp**, **vectorize**, hoặc **deoptimize** khi giả định về runtime không còn hợp lệ.

Một chỉnh sửa nhỏ đôi khi vô tình làm compiler vượt hoặc không vượt một **ngưỡng quyết định tối ưu**. Ví dụ một method lớn thêm một ít bytecode có thể không còn phù hợp với **inlining heuristic**, hoặc thay một field thành `volatile` có thể ngăn các phép đơn giản hóa từng có trước đó. Ngược lại, thay một cấu trúc dữ liệu có thể giảm rất nhiều cache misses hoặc allocations dù chỉ thay vài dòng.

Bài học giải thích **cơ chế nhân–quả**, **khi nào không được quy kết cho JIT**, và **cách thiết kế thí nghiệm đáng tin** trên Java 17+.

## 1. Hiệu năng không tỉ lệ tuyến tính với số dòng source

Sơ đồ pipeline điển hình:

```text
Java source
   │ javac
   ▼
JVM bytecode
   │ class loading / linking / execution
   ▼
Interpreter + profiling (level 0)
   │ method/loop "hot", compiler queues
   ▼
C1 (levels 1–3) ────► C2 (level 4)
   │                      │
   │       type/branch profiles + speculation
   └──────────────────────┘
               │
               ▼
      native machine code
      trong HotSpot code cache
               │
               ▼
  CPU pipeline, caches, memory hierarchy,
  allocation, GC, OS scheduler, contention
```

**Một diff nhỏ ở source** có thể tác động tại nhiều tầng:

- Thay đổi bytecode size/cấu trúc control flow.
- Thay độ nóng của method hoặc vòng lặp và thời điểm JIT compile.
- Thay các receiver types quan sát tại một call site.
- Thay cơ hội **method inlining**, đặc biệt ở những chuỗi lời gọi.
- Thay khả năng **constant folding**, **dead-code elimination** hay **escape analysis**.
- Thay phân bố nhánh, locality, branch prediction và cache behavior.
- Thay tốc độ cấp phát, thời điểm GC và safepoint.
- Thay lock contention hoặc ràng buộc memory ordering.
- Thay tính chất thí nghiệm: compiler đã tối ưu bỏ thứ được “đo”.

**Không có luật “thêm 1 dòng = chậm thêm X nanosecond”**. Phải xác định đường mã thực tế CPU chạy và toàn bộ điều kiện đo.

## 2. Hiểu đúng hai kiểu thay đổi

### 2.1. Thay đổi hành vi chương trình

Ví dụ thay thuật toán O(n²) bằng O(n log n), đổi danh sách liên kết thành mảng liên tiếp, thêm bộ nhớ đệm hoặc thay truy cập I/O. Những thay đổi đó có thể làm tốc độ khác lớn **mà không cần viện dẫn JIT**.

```java
// Đoạn minh họa trong một method, không phải chương trình độc lập.
for (int i = 0; i < values.length; i++) {
    result += values[i];
}
```

Nếu `values` là `int[]`, cách biểu diễn trực tiếp khác với `List<Integer>` có boxing/references và cách truy cập khác. Nhưng không được khẳng định mảng luôn nhanh hơn trong mọi tác vụ; API, access patterns, nullability và collections khác nhau có trade-off riêng.

### 2.2. Không đổi kết quả chức năng nhưng đổi đường tối ưu

Ví dụ thay một method nhỏ thành một chuỗi delegates, thêm một tầng interface/proxy, hoặc thêm một lệnh ghi `volatile`. Kết quả trả về có thể vẫn đúng như cũ, nhưng JIT và CPU nhìn thấy **khác hẳn**.

Chìa khóa là biết **đã thay cái gì**: semantic work, compiler opportunity hay môi trường đo? Nếu không phân loại, dễ quy mọi khác biệt cho “HotSpot khó đoán”.

## 3. Ngưỡng biên dịch phân tầng: thay đổi làm method nóng sớm hay muộn

HotSpot thường bắt đầu thực thi bytecode bằng **interpreter**, có thể biên dịch C1 rồi C2 khi profile và policy thích hợp:

| Level | Thành phần | Vai trò khái quát |
| --- | --- | --- |
| 0 | Interpreter | Thực thi ban đầu, thu thập counters/profile |
| 1 | C1 không full profiling | Compiled code nhẹ, không nhắm thu nhiều dữ liệu |
| 2 | C1 limited profiling | Theo dõi độ nóng với instrumentation hạn chế |
| 3 | C1 full profiling | Thu type/branch data phục vụ tối ưu tiếp |
| 4 | C2 | Optimized code, có thể dùng profile và assumptions |

**Không phải mọi method đều đi tuần tự qua 0 → 1 → 2 → 3 → 4.** Policy lựa chọn dựa trên invocation/backedge counters, thời điểm compiler threads xử lý queue, method size, code cache và những điều kiện khác.

### Một dòng thay đổi số lần gọi có thể đổi cả cấp execution

Giả sử method `process` trước đây được gọi 50 lần khi startup, sau khi refactor được gọi 50.000 lần. Cùng phần thân method có thể:

- Ban đầu chạy interpreter.
- Được C1 compile khi đủ nóng.
- Nhận type/branch profiles.
- Được C2 tối ưu sâu hơn sau warm-up.

Trong một benchmark quá ngắn, thay đổi như vậy khiến ta đang **so interpreter ở bản A với optimized code ở bản B**, không phải đang đo “tác động trực tiếp của một dòng refactor”.

### Vòng lặp dài và OSR

**On-Stack Replacement (OSR)** cho phép HotSpot chuyển một method **đang chạy trong loop dài** sang compiled code tại một bytecode index thích hợp. Vì vậy một thay đổi nhỏ về loop bounds có thể ảnh hưởng độ nóng qua backedge counters; kết quả chạy lần đầu và các lần sau khác nhau.

**Không suy** cứ vượt chính xác 10.000 iterations là OSR bắt buộc; compiler policy, flags và môi trường khác nhau.

## 4. Inlining cliff: thay vài bytecode nhưng mất cơ hội inline

**Inlining** thay lời gọi method bằng body của callee trong compiled caller (khi compiler thấy phù hợp). Với HotSpot, đó là quyết định theo **heuristics, profiling, bytecode size và budgets**, không phải quy tắc “mọi method dưới N dòng đều được inline”.

Ví dụ:

```java
static int addOne(int x) {
    return x + 1;
}

static int compute(int x) {
    return addOne(x) * 2;
}
```

Hai methods trên là **snippet đặt trong cùng class**. Nếu compiler inline `addOne`, nó có thể thấy toàn biểu thức `(x + 1) * 2` và tối ưu thêm. Nhưng không bảo đảm tất cả JDK builds/conditions phải inline.

### Một thay đổi nhỏ có thể vượt ngưỡng inlining

Giả sử bổ sung validation, logging, exception handling hoặc các nhánh vào callee:

```java
static int addOneWithCheck(int x) {
    if (x < 0) {
        throw new IllegalArgumentException("negative");
    }
    return x + 1;
}
```

Trên dữ liệu toàn số dương, kết quả kinh doanh vẫn là cộng 1. Nhưng body/call graph/cost thay đổi:

- Callee có nhiều bytecode hơn.
- Có nhánh mới và đường exception.
- Compiler phải bảo toàn exception semantics.
- Inlining decision có thể đổi.
- Khi không inline, caller có thể mất cơ hội constant propagation và các tối ưu xuyên method.

**Đây là khả năng, không phải một kết quả đo sẵn:** HotSpot có thể vẫn inline callee có kiểm tra, hoặc hoist/eliminate kiểm tra nếu đủ căn cứ. Không tuyên bố cứ thêm `if` sẽ làm code chậm.

### Inlining heuristic không phải ABI

Có những cờ HotSpot như `MaxInlineSize`, `FreqInlineSize`, `MaxTrivialSize` và các giới hạn ngân sách khác. Nhưng một threshold bytecode không độc lập quyết định tất cả: **hot methods** có thể có ngưỡng khác cold methods; call depth, recursive inlining, compiler budgets, type profile, code cache cũng tác động.

Khi sửa một method đúng gần ngưỡng, performance có thể có dạng **bước nhảy** chứ không tăng đều theo số bytecode. Vì thế gọi hiện tượng này là một **optimization cliff** hữu ích hơn diễn giải “dòng mới tốn 3 ns”.

## 5. Devirtualization cliff: receiver profile đổi khi refactor

Một interface call thường được biên dịch thành `invokeinterface` trong bytecode. Nhưng ở hot call site, HotSpot có thể dùng **class hierarchy analysis (CHA)** và **type profiling** để xác định đích, rồi direct call hoặc inline.

Ví dụ:

```java
interface Operation {
    int apply(int x);
}

final class AddOne implements Operation {
    public int apply(int x) {
        return x + 1;
    }
}

final class TimesTwo implements Operation {
    public int apply(int x) {
        return x * 2;
    }
}
```

Đây là những types minh họa, chưa có `main`. Một caller:

```java
static int route(Operation operation, int value) {
    return operation.apply(value);
}
```

**Case A:** trong warm-up, `route` gần như chỉ nhận `AddOne`. Type profile có thể **monomorphic**, thuận lợi cho guarded devirtualization và inlining.

**Case B:** chỉ thêm một đường gọi với `TimesTwo`, call site có thể trở thành **polymorphic**. Compiler có thể tạo guarded paths cho một vài targets, hoặc chọn generic dispatch, hoặc deopt/recompile nếu assumptions cũ không phù hợp.

**Không đồng nhất “có 2 implementations trong source” với “call site bimorphic”:** profile theo **runtime receiver types ở chính site**. Interface có 100 implementations nhưng site chỉ gặp một type vẫn có thể monomorphic.

### Giả định có thể bị mất hiệu lực

- New plugin class được load.
- Dynamic proxy hoặc subclass xuất hiện.
- Traffic chuyển sang một concrete type khác.
- Class hierarchy dependency không còn đúng.
- Code C2 từng inline target dựa trên speculative type data.

HotSpot cần **guard/fallback/deoptimization/compiled dependency invalidation** để bảo toàn Java semantics. Một site có target bất ngờ **không được** gọi nhầm implementation cũ. Nhưng không phải **mọi lần** gặp type mới đều buộc deopt: compiler có thể có fallback path phù hợp.

### Vì sao chênh lệch có thể lớn?

Nếu devirtualization giúp inline method nhỏ, còn inlining cho phép constant folding/escape analysis xuyên method, mất optimized target có thể làm mất **cả chuỗi tối ưu**. Không chỉ thêm chi phí một virtual dispatch instruction.

## 6. Escape analysis: thêm một reference escape làm biến mất scalar replacement

**Escape analysis (EA)** xem một object có thể thoát khỏi method/thread hay không. HotSpot C2 có thể dùng thông tin ấy để **scalar-replace** một object: giữ các giá trị field như scalars thay vì duy trì một heap allocation vật lý trong optimized path.

Ví dụ:

```java
static int localScore(int x) {
    Pair p = new Pair(x, x * 2);
    return p.left + p.right;
}
```

Đây là snippet; `Pair` phải được định nghĩa trong cùng class. Nếu `p` không escape và compiler hiểu toàn đường xử lý, việc tạo object có thể bị loại bỏ trong compiled code.

Chỉnh nhẹ:

```java
static volatile Object published;

static int publishedScore(int x) {
    Pair p = new Pair(x, x * 2);
    published = p;
    return p.left + p.right;
}
```

Đây là snippet minh họa trong một class. Ghi reference tới **static volatile field** làm object **được công bố ra ngoài scope cục bộ**, thường ngăn việc loại bỏ allocation bằng cùng chiến lược scalar replacement. Chênh lệch có thể liên quan hàng loạt:

- Heap allocations.
- TLAB refill và allocation fast path.
- Garbage generation/GC work.
- Write barriers và volatile memory-ordering costs.
- CPU cache traffic và memory bandwidth.

**Cẩn trọng:** không tuyên bố mọi `new Pair` tạo object vật lý trong mọi JVM/mọi path; cũng không nói local version chắc chắn được scalar-replace. Đây là **cơ hội tối ưu phụ thuộc C2/JDK/profile**.

### HotSpot EA không đồng nghĩa tự động cấp phát trên stack

Tài liệu HotSpot EA mô tả **scalar replacement**, không nên khẳng định HotSpot C2 JDK 17 mặc định thực hiện **general-purpose stack allocation** của mọi object non-escaping. “Allocation đã biến mất” có thể là vì các fields được thay thế bằng scalars/optimized operations, không phải vì object chỉ chuyển nơi cấp phát từ heap sang stack.

### Không chỉ `volatile` mới gây escape

Object có thể escape qua:

- Return object reference.
- Ghi vào field của một object sống lâu hoặc static field.
- Đưa vào collection được giữ từ ngoài.
- Truyền qua call mà JIT không thể chứng minh receiver/callee không giữ reference.
- Native/JNI interaction.
- Cấu trúc concurrent/shared state.

Tuy vậy, **return object** không tự có nghĩa allocation chắc chắn tồn tại sau inline của caller; compiler có thể phân tích xuyên method trong ngữ cảnh tối ưu. Cần xem escape **trong compiled scope**, không chỉ một câu lệnh Java.

## 7. Dead-code elimination: phép đo trông nhanh vì công việc đã bị xóa

Một microbenchmark ngây thơ:

```java
static int pure(int x) {
    return x * x + 1;
}

static void suspicious() {
    for (int i = 0; i < 1_000_000; i++) {
        pure(42);
    }
}
```

Đây là hai methods minh họa trong một class. Kết quả mỗi lần gọi `pure(42)` **không được sử dụng**, và `pure` không có side effects. JVM/JIT có thể loại bỏ phép tính cùng phần lớn/all vòng lặp khi quan sát rằng nó không ảnh hưởng hành vi chương trình.

Nếu một phiên bản benchmark **in kết quả** còn phiên bản khác **bỏ kết quả**, sự khác biệt tốc độ có thể khổng lồ vì **không còn đo cùng công việc**. Nhận định “phiên bản B chạy nhanh gấp 1.000 lần” lúc đó hoàn toàn không chứng minh thuật toán B tốt hơn.

### Khắc phục khi benchmark

- Kết quả phải được **consume** bằng cách mà harness làm phù hợp.
- Với **JMH**, có thể **return result** từ `@Benchmark` hoặc dùng `Blackhole.consume(result)`.
- Không vội dùng một `volatile` store **mỗi iteration** để “chống DCE” rồi quên rằng bạn vừa thêm chi phí synchronization/memory ordering vào phép đo.
- Dữ liệu đầu vào phải thay đổi theo trường hợp thực, không chỉ một literal compiler có thể constant-fold.
- Hãy đọc JMH samples chính thức về **Dead Code Elimination** và **Constant Folding**.

**Tránh hiểu quá mức:** chỉ vì bạn “dùng kết quả” trong Java source không đồng nghĩa benchmark đã đúng. JIT vẫn có thể xác định output là hằng số hoặc hoist công việc ra khỏi vùng đang đo. JMH giúp thiết kế harness, nhưng **không thay tư duy kiểm chứng**.

## 8. Constant folding và constant propagation: literal nhỏ cũng khác

Ví dụ:

```java
static int calculate(int x) {
    return (x * 31 + 7) & 1023;
}

static int constantInput() {
    return calculate(42);
}
```

Đây là snippet minh họa trong class. Nếu compiler inline `calculate`, input hằng `42` làm biểu thức có thể tính **ngay tại compile time**; chương trình chạy sau đó không còn thực hiện phép nhân/cộng ấy ở hot path.

Thay `42` bằng một runtime field:

```java
static volatile int changingInput = 42;

static int runtimeInput() {
    return calculate(changingInput);
}
```

Lệnh đọc `volatile` phải tuân thủ Java Memory Model, giá trị có thể thay đổi bởi thread khác. Compiler không thể tự biến mọi lần đọc thành một literal nếu như thế làm thay đổi semantics.

**Hai phiên bản không còn đo cùng điều kiện tính toán.** Nếu purpose là đo cost của `calculate` với inputs runtime, dùng non-constant `@State` trong JMH, tránh thiết kế loop để JIT suy đầu vào bất biến vô tình.

### `final` và compile-time constants

`static final int VALUE = 42;` là **constant variable** phù hợp với Java constant expressions và có thể được inlined tại bytecode level ở những call sites. Nhưng **không phải mọi `final` field là compile-time constant**: `final Object`, `final` field được khởi tạo runtime hoặc giá trị lấy từ I/O có ngữ nghĩa khác.

**Cẩn trọng khi benchmark:** “chỉ thêm `final`” đôi khi cho phép constant propagation, nhưng hiệu ứng phụ thuộc dữ liệu, cách khởi tạo, phạm vi compiled method và JIT.

## 9. Loop transformations: một nhánh hoặc alias thay đổi vectorization

Những biến đổi có thể áp dụng trên loops gồm:

- **Loop unrolling**: xử lý nhiều iterations mỗi lượt.
- **Loop invariant code motion**: đẩy biểu thức bất biến ra ngoài loop.
- **Range-check elimination**: bỏ bounds checks đã chứng minh dư thừa.
- **SuperWord/vectorization**: có thể dùng SIMD instructions khi mẫu truy cập/data dependencies phù hợp.
- **Strength reduction**: biến đổi phép toán tốn chi phí thành dạng phù hợp.
- **Auto-vectorization constraints**: aliasing, dependencies, exception semantics và CPU instruction set.

Ví dụ:

```java
static void addArrays(int[] a, int[] b, int[] out) {
    for (int i = 0; i < out.length; i++) {
        out[i] = a[i] + b[i];
    }
}
```

Đây là method có thể biên dịch khi đặt trong class, nhưng **nó chỉ đúng nếu a/b có độ dài tối thiểu bằng out**. VM phải giữ khả năng ném `ArrayIndexOutOfBoundsException` khi inputs ngắn. Không được bỏ checks nếu chưa chứng minh an toàn theo luật exception ordering.

Chỉnh loop:

```java
static void addArraysWithSideEffect(int[] a, int[] b, int[] out) {
    for (int i = 0; i < out.length; i++) {
        out[i] = a[i] + b[i];
        counter++;
    }
}
```

Đây là snippet; `counter` phải là field được khai báo. Với `counter` thông thường, JIT có thể gộp/di chuyển một số updates nếu không quan sát xen kẽ. Với `volatile counter`, mỗi iteration mang semantics khác, làm vectorization/hoisting phức tạp và tăng memory-ordering cost.

**Không khẳng định** vòng đầu luôn vectorized, vòng sau luôn scalar: cần kiểm tra CPU, compiler logs/assembly, array aliasing, hotness và JDK build. Chỉ sửa một dòng có thể làm **mất điều kiện áp dụng** một tối ưu, không bảo đảm kích hoạt/tắt một instruction duy nhất.

### Dependency giữa các vòng lặp

`out[i] = out[i - 1] + x` tạo loop-carried dependency (với i bắt đầu từ 1). Nó khác một loop độc lập mỗi element. Một refactor thay cách đọc/ghi có thể tạo data dependency khiến SIMD không còn phù hợp, dù số dòng rất ít.

## 10. Branch prediction và dữ liệu đầu vào

Một thay đổi nhỏ ở điều kiện `if` có thể:

- Làm hot path trở thành nhánh khó dự đoán.
- Thay số CPU branch mispredictions.
- Thay code layout sau JIT.
- Thay cơ hội hoist/inline/loop predication.
- Làm profile tại thời điểm warm-up không đại diện production.

Ví dụ minh họa:

```java
static int countPositive(int[] values) {
    int count = 0;
    for (int value : values) {
        if (value > 0) {
            count++;
        }
    }
    return count;
}
```

Nếu 99,9% values dương thì branch có phân bố khác với dữ liệu random 50/50. CPU và compiler có thể ứng xử khác, ngay cả khi source **không đổi**.

### “Chỉ đổi một phép so sánh” không tự chứng minh branch misprediction

Compiler có thể dùng branchless operations, vectorize hoặc hoán đổi branch order. Trong thực tế:

1. Đảm bảo hai phiên bản xử lý **cùng đầu vào và cùng semantic workload**.
2. Profiling bằng JFR/async-profiler/perf (nếu có quyền), so CPU hotspots/counters.
3. Kiểm tra compiled code nếu cần phân biệt branch/conditional move/vector instructions.
4. Đo sau warm-up đủ và trên nhiều runs.

**Không dùng một bảng “if/else luôn chậm hơn ternary”**; HotSpot quyết định codegen, CPU predictor quyết định chi phí theo mẫu data.

## 11. Cache locality và cấu trúc dữ liệu

Một thao tác đổi `int[]` thành `List<Integer>` nhìn có thể chỉ sửa một chữ ký, nhưng ảnh hưởng cả:

- Các element được lưu trực tiếp hay qua object references.
- Boxing/unboxing (có thể được tối ưu ở một số paths nhưng không nên mặc định).
- Memory footprint/header/padding của boxed values.
- Object graph và GC scanning.
- CPU cache misses, prefetching và pointer chasing.
- Allocation rate và retained heap.

Ví dụ conceptual:

```java
int[] numbers = new int[1000];
java.util.List<Integer> boxed = new java.util.ArrayList<>();

for (int i = 0; i < numbers.length; i++) {
    numbers[i] = i;
    boxed.add(i);
}
```

Đây là snippet trong method. `boxed` chứa references tới các `Integer` objects; có thể có boxing cache cho một số values, nhưng không được suy tất cả phần tử đều tạo object riêng hoặc `boxed` luôn chậm hơn trong mọi phép toán.

### Một sửa nhỏ thay allocation pattern

Trong một hot loop, thay `new StringBuilder()` mỗi iteration bằng một instance tái sử dụng có thể giảm allocations; nhưng nếu instance sống lâu và bị giữ qua thread/shared state, nó có thể tăng footprint, contention hoặc tạo bug. Cần đánh giá **ownership và thread safety** chứ không chỉ số lần `new`.

## 12. `volatile`, đồng bộ hóa và lock elision

Một field bình thường và một field `volatile` có semantics Java Memory Model khác nhau. Đổi khai báo:

```java
int counter;
```

thành:

```java
volatile int counter;
```

có thể ảnh hưởng memory ordering, CPU cache coherence và cơ hội compiler optimize reads/writes. Nhưng **không nên bỏ `volatile` chỉ vì benchmark chậm**: nếu nhiều threads cần visibility/happens-before, đó là yêu cầu correctness.

**Điểm quan trọng:** `volatile++` **không nguyên tử** khi nhiều threads cùng cập nhật; phép read-modify-write vẫn có thể mất update. Nếu cần atomic increment, dùng `AtomicInteger.incrementAndGet()` hoặc synchronization phù hợp.

### Lock elimination

Nếu compiler chứng minh object không escape và synchronization trên object không thể ảnh hưởng thread khác, HotSpot có thể thực hiện lock elision thích hợp. Nhưng một refactor đưa object vào static shared field/collection có thể mất proof, khiến synchronization thật sự tốn kém.

**Không định lượng trước** “synchronized thêm đúng X ns”; contention, biased/modern locking implementation, monitor inflation, safepoints và JVM release đều ảnh hưởng.

## 13. GC, allocation và safepoints khuếch đại khác biệt

Một allocation nhỏ tăng lên mỗi request đôi khi làm tốc độ thay đổi đáng kể vì:

- Tăng Eden/TLAB refill rate.
- Young GC diễn ra thường hơn.
- Promotion pressure làm old generation căng.
- More references trong object graph tăng tracing work.
- Safepoint pauses đụng vào request latency.
- CPU của GC threads cạnh tranh với application/JIT threads.

Ví dụ, nếu một refactor tạo 50.000 temporary objects mỗi request và tải là 100 requests/giây, số allocations tăng theo workload: **5 triệu objects/giây** chỉ từ nguồn đó, trước khi tính những allocations khác. Đây là phép nhân điều kiện, **không tự chứng minh GC pause tăng gấp bao nhiêu** vì object sizes/lifetimes/collector/JIT có thể khác.

### Đừng suy đoán do GC từ một lần đo

Một run “bản A 20 ms, bản B 50 ms” có thể chỉ khác GC timing. Cần nhiều iterations/forks và GC logs để phân biệt:

- Thời gian code thật.
- GC pauses phát sinh trong measurement.
- JIT compilation trong measurement.
- Allocation pressure/trạng thái heap.
- OS scheduling, CPU frequency và thermal throttling.

**Heap used giảm** cũng không tự nói end-to-end throughput tăng: collector khác nhau có thể giữ committed memory cho lần dùng sau, RSS không đồng nhất với live-set.

## 14. Code cache và compilation budget

HotSpot tạo machine code C1/C2 trong **code cache** (native executable memory), khác Java heap. Một thay đổi source nhỏ có thể:

- Làm JIT inline thêm hoặc bớt method.
- Tạo nmethod lớn/nhỏ khác.
- Thay số compiled variants và OSR entries.
- Tăng compilation CPU/time.
- Thay áp lực lên profiled/non-profiled code heaps.
- Khi cực đoan, góp phần làm code cache đầy và compilation bị hạn chế.

**C2 compile nhiều không phải luôn tốt hơn**: tăng code size có thể gây instruction-cache misses hoặc compilation overhead lớn hơn lợi ích runtime.

Nếu code cache đầy, JVM có thể in cảnh báo liên quan:

```text
CodeCache is full. Compiler has been disabled.
```

Đây là **mẫu có thể gặp**, không phải log bắt buộc. Muốn xác minh cần xem `Compiler.codecache`, flags và compilation logs chứ không dựa một symptom throughput thấp.

## 15. Ví dụ Java 17+ runnable: cùng kết quả logic, khác cơ hội tối ưu

Tạo file **`HotSpotSmallChangeDemo.java`**:

```java
public class HotSpotSmallChangeDemo {
    interface Operation {
        int apply(int x);
    }

    static final class AddOne implements Operation {
        @Override
        public int apply(int x) {
            return x + 1;
        }
    }

    static final class TimesTwo implements Operation {
        @Override
        public int apply(int x) {
            return x * 2;
        }
    }

    static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int score() {
            return x + y;
        }
    }

    // Giữ object reference ra ngoài phạm vi local.
    static volatile Point published;

    static int call(Operation operation, int value) {
        return operation.apply(value);
    }

    static long monomorphic(int count) {
        Operation add = new AddOne();
        long sum = 0;

        for (int i = 0; i < count; i++) {
            sum += call(add, i % 10);
        }
        return sum;
    }

    static long polymorphic(int count) {
        Operation add = new AddOne();
        Operation times = new TimesTwo();
        long sum = 0;

        for (int i = 0; i < count; i++) {
            Operation target = (i & 1) == 0 ? add : times;
            sum += call(target, i % 10);
        }
        return sum;
    }

    static long localPoints(int count) {
        long sum = 0;

        for (int i = 0; i < count; i++) {
            int value = i % 7;
            Point point = new Point(value, value * 2);
            sum += point.score();
        }
        return sum;
    }

    static long publishedPoints(int count) {
        long sum = 0;

        for (int i = 0; i < count; i++) {
            int value = i % 7;
            Point point = new Point(value, value * 2);
            published = point;
            sum += point.score();
        }
        return sum;
    }

    public static void main(String[] args) {
        long mono = monomorphic(100_000);
        long poly = polymorphic(100_000);
        long local = localPoints(70_000);
        long escaped = publishedPoints(70_000);

        System.out.println("monomorphic: " + mono);
        System.out.println("polymorphic: " + poly);
        System.out.println("local points: " + local);
        System.out.println("published points: " + escaped);
        System.out.println("equal point results: " + (local == escaped));
        System.out.println("last published x: " + published.x);
    }
}
```

### Biên dịch và chạy

```bash
javac -encoding UTF-8 HotSpotSmallChangeDemo.java
java HotSpotSmallChangeDemo
```

**Standard output xác định, không bật compiler/GC logging:**

```text
monomorphic: 550000
polymorphic: 750000
local points: 630000
published points: 630000
equal point results: true
last published x: 6
```

**Vì sao các số đúng:**

- `i % 10` lặp qua 0..9, tổng 45; `AddOne` cộng thêm 1 mỗi lần, tổng một chu kỳ 55. Có 10.000 chu kỳ: `55 × 10.000 = 550000`.
- Ở `polymorphic`, các chỉ số chẵn có inputs 0,2,4,6,8, `AddOne` tạo tổng 1+3+5+7+9 = **25** mỗi chu kỳ. Các chỉ số lẻ có inputs 1,3,5,7,9; `TimesTwo` tạo tổng 2+6+10+14+18 = **50**. Tổng mỗi chu kỳ 75, lặp 10.000 lần: **750000**.
- `Point(value, value * 2)` cho `score = 3 × value`. Với value 0..6, tổng một chu kỳ = `3 × 21 = 63`. Có 10.000 chu kỳ: **630000** ở cả local và published versions.
- Lượt cuối `i = 69999`, `i % 7 = 6`, nên `published.x` là **6**.

### Demo này chứng minh gì?

1. Runtime dispatch chọn đúng method khi call site nhận một hoặc hai implementation types.
2. Hai body khác nhau về object publication nhưng giữ **cùng kết quả số học** cho chuỗi inputs.
3. Ghi reference vào `volatile` field có semantics observable; đường local không tự động buộc object escape.
4. Có thể kiểm tra output đúng trên Java 17+ độc lập với JIT policy.

### Demo không chứng minh gì?

- **Không chứng minh** monomorphic path chắc chắn nhanh hơn polymorphic path: hai operations ở ví dụ còn khác phép toán, nên không thể dùng tổng thời gian để tách riêng dispatch cost.
- **Không chứng minh** localPoints luôn không allocate: còn phụ thuộc JIT, inlining/EA và runtime version.
- **Không chứng minh** publishedPoints nhất định gây N GC collections: timing và heap ergonomics không xác định.
- **Không chứng minh** chỉ tăng 1 dòng là tốc độ đổi X lần: muốn kết luận performance phải có benchmark tách workload và điều kiện đo.
- **Không chứng minh** JIT phải compile `call` ở level 4: code có thể còn interpreter/C1 hoặc bị inline vào caller.

**Đây là bài thử correctness và giải thích optimization opportunities, không phải microbenchmark chuẩn.**

## 16. Quan sát JIT behavior với cùng chương trình

Trên HotSpot JDK 17+:

```bash
java -XX:+PrintCompilation HotSpotSmallChangeDemo
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining HotSpotSmallChangeDemo
```

Bạn có thể thấy compiler activity cho cả Java standard library; logging có thể xen với output từ `System.out.println`. Tìm những method names như:

```text
HotSpotSmallChangeDemo::call
HotSpotSmallChangeDemo::localPoints
HotSpotSmallChangeDemo::publishedPoints
HotSpotSmallChangeDemo$Point::score
```

Đây là **các tên gợi ý để lọc logs**, không đảm bảo cả bốn được compile hoặc tên format y hệt ở mọi JDK/vendor.

### Những dấu hiệu cần phân biệt

| Dấu hiệu | Cách diễn giải đúng |
| --- | --- |
| Compile ID mới cho method | HotSpot ghi nhận một compilation event theo format JVM |
| Level 3/4 | Tiered compilation policy đưa method vào mức phù hợp |
| `inline (hot)` | Một compilation context đã chọn inline target |
| `virtual call` | Ở context đó có thể chưa inline/general dispatch vẫn cần |
| `made not entrant` | Một compiled version không còn nhận entries mới |
| Dấu `%` của compilation event | Thường báo OSR compilation |
| Không thấy method | Chưa chắc method không được tối ưu; có thể inline vào caller, chưa hot hoặc logs không hiện như mong đợi |

**Không kết luận DCE/EA/vectorization chỉ từ `PrintCompilation`:** công cụ này cho biết compilation events chứ không chứng minh machine instructions nào còn tồn tại.

### Chạy các JIT modes khác nhau

```bash
java -Xint HotSpotSmallChangeDemo
java -XX:TieredStopAtLevel=1 HotSpotSmallChangeDemo
java -XX:+PrintCompilation HotSpotSmallChangeDemo
java -XX:-TieredCompilation HotSpotSmallChangeDemo
```

Nếu binary hỗ trợ các HotSpot flags, **stdout tính toán vẫn phải giữ nguyên**. Nhưng execution mode, compile activity và timing có thể khác. **`-XX:-TieredCompilation` không đồng nghĩa `-Xint`**: nó thay policy chứ không bắt buộc tắt JIT hoàn toàn.

### Kiểm tra bytecode, không nhầm với machine code

```bash
javap -c -p HotSpotSmallChangeDemo
```

`javap` đọc bytecode trong class file: `invokeinterface`, `new`, `getfield`, `putstatic` ... vẫn có thể thấy dù compiled machine code đã inline hoặc scalar-replace. Không dùng sự tồn tại của `new` opcode như bằng chứng allocation vật lý chắc chắn xảy ra trong optimized execution.

## 17. Cách thiết kế benchmark thực sự bằng JMH

**Java Microbenchmark Harness (JMH)** là dự án của OpenJDK thiết kế để chạy và phân tích nano/micro/milli benchmarks, giúp kiểm soát warm-up, forks, iterations và những bẫy JIT phổ biến. Nhưng một benchmark dùng JMH **vẫn có thể sai** nếu so hai workloads khác nghĩa hoặc JIT optimize luôn công việc định đo.

### Thiết lập dự án

Cách khuyến nghị là dùng **Maven archetype chính thức của JMH**, cấu hình dependency và annotation processor đúng bản JMH bạn chọn. Xem README dự án để lấy **archetype version mới thực sự có ở môi trường build**, tránh tự đoán phiên bản:

- https://github.com/openjdk/jmh
- https://github.com/openjdk/jmh/tree/master/jmh-samples

Sau khi tạo một project JMH chuẩn (trong đó đã có `pom.xml` hợp lệ), thêm class sau vào source package phù hợp.

### Ví dụ JMH: tách các benchmark methods

Tạo file `DispatchBench.java` trong package `org.sample` của project JMH đã được cấu hình:

```java
package org.sample;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class DispatchBench {
    interface Op {
        int apply(int x);
    }

    static final class AddOne implements Op {
        public int apply(int x) {
            return x + 1;
        }
    }

    static final class TimesTwo implements Op {
        public int apply(int x) {
            return x * 2;
        }
    }

    private Op first;
    private Op second;
    private int counter;

    @Setup
    public void setup() {
        first = new AddOne();
        second = new TimesTwo();
        counter = 0;
    }

    private static int call(Op op, int value) {
        return op.apply(value);
    }

    @Benchmark
    public int oneReceiverType() {
        int x = counter++;
        return call(first, x & 31);
    }

    @Benchmark
    public int alternatingTypes() {
        int x = counter++;
        Op op = (x & 1) == 0 ? first : second;
        return call(op, x & 31);
    }
}
```

**Code trên yêu cầu thư viện JMH** và project có annotation processing, **không** được chạy bằng `javac DispatchBench.java` thuần nếu chưa có dependencies. Nó được thiết kế để biên dịch trong Maven JMH archetype project.

Build/run trong project JMH:

```bash
mvn -q clean package
java -jar target/benchmarks.jar DispatchBench
```

**Không có stdout nanoseconds cố định.** Results phụ thuộc CPU/JDK/GC/environment, harness forks và những optimizations ở mỗi benchmark. Chỉ nên dùng làm điểm bắt đầu để **quan sát behavior**, chưa đủ chứng minh riêng chi phí interface dispatch.

### Giới hạn quan trọng của ví dụ benchmark này

Hai phương thức khác nhau không thực hiện cùng một phép toán mỗi iteration: `alternatingTypes` có phép chọn receiver và đôi khi phép nhân thay vì cộng. Vì thế nếu kết quả timing khác, **chưa thể quy toàn bộ chênh lệch cho devirtualization**.

Để cô lập giả thuyết, cần thiết kế controls cùng operations, kiểm tra profiles/compiled code và chỉ thay một yếu tố; ví dụ hai implementations có **cùng math** nhưng khác concrete class, rồi benchmark target selection overhead độc lập. Thậm chí thế vẫn còn ảnh hưởng từ branch selection, class loading, JIT inlining context.

### Nguyên tắc JMH quan trọng

- **`@State`:** mang runtime inputs thay vì để mọi thứ thành compile-time constants.
- **`@Benchmark` trả về result:** harness consume để giảm rủi ro DCE.
- **`@Warmup`:** giúp compiler có thời gian profile/compile; số iterations phải phù hợp workload.
- **`@Fork`:** tách JVM processes, tránh nhiễm compiled state giữa cases.
- **`@Measurement`:** đo nhiều iterations sau warm-up, báo distribution/error.
- **`@Param`:** hữu ích để kiểm tra nhiều input distributions, nhưng không tự giải quyết mọi alias/profile issue.
- **`Blackhole`:** dùng khi cần consume nhiều outputs; không lạm dụng trong nội vòng nếu chính overhead của nó làm lệch kết quả.
- **Test setup:** tránh object creation/file I/O trong `@Benchmark` nếu mục tiêu chỉ là đo pure computation và việc setup có thể tách ra hợp lệ.

**Không dùng benchmark “càng nhanh càng tốt” nếu tối ưu loại bỏ công việc làm hai phiên bản không còn cùng semantics**. Luôn có correctness oracle độc lập.

## 18. Phương pháp A/B để tìm nguyên nhân thật sự

Khi thay một vài dòng mà thời gian đổi lớn, làm theo trình tự:

### Bước 1 — Chốt metric

Bạn đang đo **startup**, **time to first request**, **steady-state throughput**, **allocation rate**, **GC pause p99**, **request latency p99** hay **CPU cycles trên hot path**? Cùng code có thể tốt ở một metric và kém ở metric khác.

### Bước 2 — Giữ ổn định điều kiện

- Cùng **JDK vendor, build, JVM flags, garbage collector**.
- Cùng OS, CPU architecture, core availability, power plan/frequency, container CPU/memory limits.
- Cùng dữ liệu đầu vào và phân bố receiver types.
- Cùng heap, cache warm state và resource limits khi hợp lý.
- Cùng chuẩn chọn concurrency/thread count.
- Đo qua nhiều lần/forks, xem variability/error bars thay vì chỉ giá trị tốt nhất.

### Bước 3 — Kiểm tra đúng kết quả

Hai phiên bản phải cùng **ngữ nghĩa và side effects** trong yêu cầu nghiệp vụ. Kết quả số học bằng nhau chưa đủ nếu version mới loại bỏ logging, locking, I/O hoặc xử lý lỗi cần thiết.

### Bước 4 — Xem codegen/JIT evidence

Dùng compilation/inlining logs, JFR, perf profiler hoặc assembly (khi có) để kiểm tra:

- Method có tới cùng compilation level không?
- Inlining tree có khác?
- Có deopt/uncommon traps hoặc class hierarchy invalidations?
- Escape analysis/scalar replacement có tạo khác biệt không?
- Có số allocations, GC cycles, code cache growth khác?
- Có branch/cache misses khác khi profiler hỗ trợ?

### Bước 5 — Kiểm chứng giả thuyết bằng biến đổi nhỏ

Ví dụ nghi mất inlining, kiểm tra body bytecode `javap -c`, inlining logs, rồi tách một thay đổi nhỏ để cô lập. Nghi escape, dùng allocation profiler và cases local vs published. Nghi cache locality, đổi container/data layout với cùng workload.

**Tránh “shotgun tuning”** thay đồng thời 10 flags, rồi không biết điều gì làm khác.

### Bước 6 — Đánh giá dưới tải đại diện

Một microbenchmark thắng 25% chưa đủ để chứng minh service throughput sẽ tăng 25%. Khi phần lớn thời gian chờ I/O, database, lock contention hoặc GC, tối ưu một method có thể không ảnh hưởng mục tiêu đầu cuối.

## 19. Công cụ theo loại nguyên nhân

| Cần xác minh | Công cụ/phương pháp | Cẩn trọng |
| --- | --- | --- |
| Source đổi bytecode thế nào | `javap -c -p`, `javap -v` | Không phản ánh native code đã JIT |
| Method được compile ở level nào | `-XX:+PrintCompilation` | Logs biến động theo JVM/run |
| Caller/callee có inline không | `-XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining` | Inlining context và formatting phức tạp |
| Cần JIT detail | `-XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation` | Có thể tạo log lớn |
| Machine code thật | `PrintAssembly` cùng `hsdis` đúng binary, JIT analyzers | Không có hsdis không đồng nghĩa JIT không chạy |
| Allocation pressure | JFR allocation events, async-profiler, GC logs | Sampling/overhead và attribution cần xem |
| GC pause/throughput | `-Xlog:gc*`, JFR, production telemetry | Không nhầm GC duration với end-to-end latency |
| Code cache/queues | `jcmd PID Compiler.codecache`, `Compiler.queue` | PID thật, tool availability |
| CPU hot paths | JFR, async-profiler hoặc OS perf tools | Có thể cần quyền đặc biệt |
| Microbenchmark | JMH warm-up/forks/Blackhole | Harness tốt không chữa benchmark design sai |
| Retaining paths | Heap dump + dominator analysis | Live set khác allocation rate |
| Hardware memory/cache | Hardware PMU counters khi được phép | CPU/OS-specific và dễ nhiễu |

Một vài HotSpot JDK 17 diagnostics:

```bash
java -XX:+PrintCompilation HotSpotSmallChangeDemo
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining HotSpotSmallChangeDemo
java -Xlog:gc*:info HotSpotSmallChangeDemo
jcmd -l
jcmd PID Compiler.queue
jcmd PID Compiler.codecache
```

**Thay `PID` bằng process ID thực tế** của JVM còn chạy. Demo ngắn có thể thoát trước khi `jcmd` attach. Những commands/JVM flags có thể khác giữa vendors/builds, không được copy mặc định sang OpenJ9 hoặc Native Image.

### Diagnostic flags có thể thay cả measurement

Bật `PrintInlining`, `LogCompilation`, JFR heavy events hoặc profiler instrumentation có thể tạo overhead và thay timing. Dùng chúng để **giải thích** compiler decisions; không dùng thời gian từ một run có logging rất nặng để kết luận tốc độ production.

## 20. “Thay ít mà chênh lớn” cũng có thể là thống kê

Giả sử đo hai phiên bản:

```text
Run 1: A=18 ms, B=26 ms
Run 2: A=22 ms, B=17 ms
Run 3: A=20 ms, B=19 ms
```

Đây là **số giả định để luyện suy luận**, không phải một benchmark kết quả thật. Nếu chỉ lấy Run 1, người đọc sẽ thấy B chậm 44% và dễ đưa ra kết luận sai. Nhưng các runs sau đảo thứ tự, cho thấy môi trường hoặc trạng thái JVM đang nhiễu.

Khi phân tích phải báo:

- Số forks/iterations, warm-up và measurement duration.
- Center estimates (mean/median) kèm variability/error bars.
- Distribution/percentiles nếu đo latency.
- Input distributions và CPU/runtime configuration.
- Có outliers, GC events, compiler transitions hay OS interference.
- Tác động lên resource footprint và semantics.

**Không cherry-pick** run đẹp nhất, không dùng một median microbenchmark để cam kết p99 request latency sẽ tương ứng.

### Amdahl’s Law: tối ưu hot path chưa chắc đổi tổng throughput lớn

Nếu một method chỉ chiếm 1% tổng request time, làm method ấy nhanh gấp 10 lần chỉ có thể cải thiện nhỏ ở end-to-end time (bỏ qua tương tác hệ thống khác). Ngược lại, một thay đổi gây leak/GC pauses có thể làm p99 tăng vọt dù average CPU trên hot method chỉ nhích chút ít.

**Đo bottleneck thực tế** trước khi chỉnh source để JIT “vui hơn”.

## 21. Những hiểu nhầm thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Ít dòng code hơn chắc chắn nhanh hơn.” | Bytecode, JIT, allocations và CPU path mới quyết định |
| “Cùng thuật toán thì cùng performance.” | Data layout, profiling, cache và compiler khác |
| “`javap` thấy `new` nghĩa object chắc chắn được cấp phát vật lý.” | JIT có thể scalar-replace nếu đủ điều kiện |
| “Thêm một `if` luôn khiến code chậm.” | Branch có thể được fold, predict, hoist hoặc optimized |
| “`final` luôn giúp method nhanh.” | Có thể tạo opportunities, nhưng không bảo đảm thắng |
| “Interface calls luôn phải làm virtual dispatch.” | JIT có thể devirtualize/inline |
| “Một method thêm bytecode làm nó luôn mất inline.” | Thresholds là heuristic, cần logs và compile context |
| “C1/C2 biên dịch method ngay khi gọi đúng N lần.” | Tiered policy/queues/counters phức tạp hơn |
| “OSR chỉ xuất hiện sau khi method return.” | Có thể chuyển code trong loop đang chạy |
| “Profile monomorphic sẽ mãi mono.” | Traffic/class loading thay đổi, có guard/deopt |
| “Một deopt là JIT bug.” | Speculation có cơ chế rút lui đúng semantics |
| “`volatile` chỉ là gợi ý cho compiler.” | Là ràng buộc Java Memory Model |
| “`volatile++` đã atomic.” | Không, increment gồm read-modify-write |
| “Tắt inlining đồng nghĩa tắt JIT.” | Không, các cơ chế khác nhau |
| “`-XX:-TieredCompilation` = `-Xint`.” | Sai, một bên đổi policy, một bên chạy interpreter-only |
| “Output nanosecond trong benchmark phải cố định.” | Timing phụ thuộc nhiều yếu tố runtime/OS/CPU |
| “Dùng `System.nanoTime()` là tự động đo đúng.” | Không chữa warm-up, DCE và constant folding |
| “`Blackhole` bảo đảm benchmark không thể sai.” | Vẫn cần đúng workload/methodology |
| “GC pause là toàn bộ thời gian của service.” | Một nguồn trễ, phải đối chiếu traces |
| “Có logging JIT mà không thấy method thì chắc không có tối ưu.” | Nó có thể inline vào caller/chưa hot hoặc khác log format |
| “Microbenchmark nhanh gấp 2 → service cũng tăng 2x.” | Bottleneck end-to-end có thể ở nơi khác |
| “Đổi Java source là nguyên nhân duy nhất.” | CPU quota, heap, flags, thermal và dữ liệu có thể khác |

## 22. Bài tập kèm đáp án

**Bài 1.** Một method chỉ tăng vài bytecode nhưng hot caller bỗng chậm. Có thể có nguyên nhân JIT nào?  
**Đáp án:** Mất một inlining decision, làm mất các tối ưu xuyên method; xác minh bằng `PrintInlining` và benchmark đủ warm-up.

**Bài 2.** Tại sao loop triệu lần với kết quả không sử dụng có thể “nhanh phi lý”?  
**Đáp án:** Dead-code elimination/constant folding có thể xóa công việc. Dùng JMH result consumption/Blackhole và runtime inputs.

**Bài 3.** Đổi `calculate(42)` thành `calculate(dynamicValue)` làm thời gian tăng. Có chắc dynamic version kém?  
**Đáp án:** Không. Phiên bản literal có thể constant-fold, nên hai tests không đo cùng work.

**Bài 4.** Một interface có 20 concrete classes nhưng tại site chỉ nhận một type. Có phải site megamorphic?  
**Đáp án:** Không. Profile theo receiver types quan sát **tại call site**.

**Bài 5.** Một type mới xuất hiện sau warm-up, có thể tác động gì?  
**Đáp án:** Guard failure/fallback, deopt hoặc recompile; không được phá dynamic dispatch semantics.

**Bài 6.** C1 level 3 có phải luôn nhanh hơn C1 level 2?  
**Đáp án:** Không. Level 3 thu profile phong phú hơn và có instrumentation costs.

**Bài 7.** Một method được gọi một lần nhưng loop rất dài có thể được JIT không?  
**Đáp án:** Có thể qua OSR và backedge counters.

**Bài 8.** Tại sao ghi một object vào static volatile field có thể tăng allocation cost?  
**Đáp án:** Nó escape khỏi local scope, thường ngăn scalar replacement và thêm volatile semantics/write barrier.

**Bài 9.** Escape analysis có nghĩa mọi object non-escaping chuyển lên stack không?  
**Đáp án:** Không. HotSpot C2 có scalar replacement; không mô tả đó là general-purpose stack allocation.

**Bài 10.** Trong demo, `monomorphic(100000)` cho bao nhiêu?  
**Đáp án:** **550000**, mỗi chu kỳ 0..9 cộng 1 có tổng 55.

**Bài 11.** Trong demo, `polymorphic(100000)` cho bao nhiêu?  
**Đáp án:** **750000**, mỗi chu kỳ có tổng chẵn-plus-one 25 và lẻ-times-two 50.

**Bài 12.** Hai Point variants trong demo trả số nào?  
**Đáp án:** Cùng **630000**, vì tổng 0..6 nhân 3 là 63, lặp 10.000 chu kỳ.

**Bài 13.** Chỉ sửa `int` thành `volatile int` có phải chỉ thay một từ không ảnh hưởng correctness?  
**Đáp án:** Không. Nó làm thay đổi memory-visibility/order semantics; phải đánh giá concurrency correctness và performance.

**Bài 14.** Code truy cập `int[]` và `List<Integer>` có thể khác memory behavior ở đâu?  
**Đáp án:** Direct primitive storage so với boxing/references và backing arrays/objects, GC và cache locality.

**Bài 15.** JIT không inline một method có làm program sai logic?  
**Đáp án:** Không. Cách gọi khác, semantics bắt buộc không đổi.

**Bài 16.** Có thể khẳng định một loop với `if` luôn không vectorized?  
**Đáp án:** Không. Compiler/CPU/version/data dependencies quyết định.

**Bài 17.** GC log xuất hiện trong lần đo chậm có chứng minh code mới kém hơn?  
**Đáp án:** Chưa. Cần nhiều runs và kiểm tra allocation rate/live set/GC causality.

**Bài 18.** Cách dùng `-Xint` trong chẩn đoán là gì?  
**Đáp án:** So sánh với interpreter-only path về correctness/performance; không đại diện production JIT throughput.

**Bài 19.** Vì sao `javap -c` không đủ xác nhận C2 đã vectorize loop?  
**Đáp án:** Nó chỉ in JVM bytecode, không đọc machine code đã JIT.

**Bài 20.** Một microbenchmark thắng 20% có bảo đảm latency p99 API cũng tốt 20%?  
**Đáp án:** Không. End-to-end có I/O, queues, GC, locks và bottlenecks khác.

**Bài 21.** Sao phải fork JVM khi đo JMH?  
**Đáp án:** Giảm nhiễm state giữa benchmark runs, warm-up/compiler profiles/GC khác nhau; vẫn cần thiết kế inputs đúng.

**Bài 22.** Mục tiêu startup 200 ms, có nên warm-up 30 phút rồi chỉ đo steady-state?  
**Đáp án:** Không. Cần đo mục tiêu thật là startup/time-to-ready, song song với steady-state nếu cần.

**Bài 23.** `System.nanoTime()` trả kết quả rất khác sau khi bật `PrintInlining`; kết luận gì?  
**Đáp án:** Diagnostics có overhead và có thể thay scheduling. Không dùng logging-heavy run làm performance baseline.

**Bài 24.** Nên sửa interface thành concrete class trước khi đo để tránh virtual dispatch?  
**Đáp án:** Không mặc định; JIT có thể devirtualize và thiết kế OO có giá trị. Profile chứng minh bottleneck trước khi đổi API.

## 23. Checklist thực hành trước khi tin một “speedup” lớn

- [ ] Có cùng **kết quả chức năng và side effects** không?
- [ ] Đang đo **startup, warm-up, steady-state, throughput hay latency**?
- [ ] Đã giữ JVM vendor, version, collector, flags và CPU/memory constraints giống nhau?
- [ ] Có dùng inputs runtime đủ đa dạng, tránh constant folding ngoài ý định?
- [ ] Có dùng result consumption/Blackhole để tránh DCE?
- [ ] Có warm-up/forks/measurement iterations đủ và kết quả sai số?
- [ ] Có kiểm tra JIT compilation levels, `PrintInlining`, deopt khi cần?
- [ ] Có phân biệt interface-call cost với operation/branch cost?
- [ ] Có xem allocation profiler/GC logs nếu code tạo nhiều objects?
- [ ] Có phân biệt escape analysis opportunity với allocation vật lý đã được đo?
- [ ] Có cân nhắc vectorization/aliasing/CPU instruction availability?
- [ ] Có kiểm tra concurrency semantics khi thêm/bỏ `volatile` hoặc locks?
- [ ] Có ghi lại input distribution/receiver types của workload?
- [ ] Có chạy benchmark trên cùng máy/điều kiện power/CPU throttling?
- [ ] Có tách logging/profiling runs khỏi timing baselines?
- [ ] Có đo impact end-to-end sau microbenchmark?
- [ ] Có phân tích confidence/variance thay vì chỉ so hai con số?
- [ ] Có tài liệu hóa lý do thực nghiệm trước khi thay JIT flags production?

## 24. Tóm tắt

**Thay đổi ít ở Java source không có nghĩa thay đổi ít ở runtime.** HotSpot là compiler thích nghi với workload, nên một diff nhỏ có thể làm thay **thời điểm method được biên dịch, profile của call site, khả năng inline, escape, constant fold, loại dead code, vectorize, branch prediction, code cache và GC behavior**. Nhiều tối ưu tác động theo chuỗi, do đó tốc độ có thể thay đổi theo **bậc nhảy** thay vì tỉ lệ với độ dài diff.

Song song, benchmark có thể “nhanh lên” chỉ vì đã **không còn làm công việc cần đo**. Một kết quả tốc độ lớn chỉ có ý nghĩa khi đã kiểm chứng **cùng semantics, cùng workload, JIT phase tương đương và điều kiện hệ thống kiểm soát**.

### Tài liệu tham khảo

- **OpenJDK Java Microbenchmark Harness (JMH)** — cách xây dựng và chạy benchmark JVM: https://github.com/openjdk/jmh
- **OpenJDK JMH Sample 08 — Dead Code Elimination:** https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_08_DeadCode.java
- **OpenJDK JMH Sample 10 — Constant Folding:** https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_10_ConstantFold.java
- **Oracle JDK 17 — The java Command** (TieredCompilation, PrintCompilation, PrintInlining, Inline, Code Cache): https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **OpenJDK — HotSpot Escape Analysis and Scalar Replacement Status:** https://cr.openjdk.org/~cslucas/escape-analysis/EscapeAnalysis.html
- **OpenJDK — JEP 197 Segmented Code Cache:** https://openjdk.org/jeps/197
- **Oracle JDK 17 — HotSpot GC Tuning Guide:** https://docs.oracle.com/en/java/javase/17/gctuning/
- **OpenJDK JMH Blackhole source:** https://github.com/openjdk/jmh/blob/master/jmh-core/src/main/java/org/openjdk/jmh/infra/Blackhole.java

**Thông điệp cuối:** một regression hay speedup lớn sau refactor ngắn **là tín hiệu để điều tra cơ chế, không phải bằng chứng mã ngắn hơn hoặc một keyword cụ thể nhanh hơn**. Hãy dùng JMH và công cụ JIT/GC phù hợp để kiểm tra điều JVM thực sự đã làm, rồi đánh giá kết quả trên toàn ứng dụng.

</details>

- [Quay lại đầu trang](#back-to-top)
