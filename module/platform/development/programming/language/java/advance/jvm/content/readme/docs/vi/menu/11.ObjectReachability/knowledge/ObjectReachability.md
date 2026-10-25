<a id="back-to-top"></a>

# Khả năng truy cập

## Menu
- [GC xác định đối tượng còn truy cập được từ gốc như thế nào](#object-reachability-001)
- [Đồ thị tham chiếu, đối tượng không còn truy cập được và tham chiếu vòng](#object-reachability-002)
- [Vì sao gán null hoặc ra khỏi phạm vi biến không bảo đảm thu gom ngay](#object-reachability-003)
- [Tham chiếu mạnh, mềm, yếu và ảo trong java.lang.ref](#object-reachability-004)
- [ReferenceQueue và thời điểm thông báo đối tượng đã được xử lý tham chiếu](#object-reachability-005)
- [Rò rỉ bộ nhớ Java khi đối tượng vẫn được giữ bởi cache, listener hoặc ThreadLocal](#object-reachability-006)
- [GC thu hồi bộ nhớ khác với đóng file, socket và tài nguyên bên ngoài](#object-reachability-007)
- [Finalization, tình trạng deprecation và lựa chọn try-with-resources hoặc Cleaner](#object-reachability-008)
- [System.gc là yêu cầu gợi ý và những giới hạn khi dùng nó để kiểm chứng](#object-reachability-009)

## <a id="object-reachability-001">GC xác định đối tượng còn truy cập được từ gốc như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# GC xác định đối tượng còn truy cập được từ gốc như thế nào?

Trong Java, **garbage collector (GC)** không đơn giản xóa object sau khi một biến cục bộ kết thúc, cũng không chỉ đếm số reference trỏ vào object. Để xác định những object cần được giữ lại, các tracing collectors của JVM bắt đầu từ một tập **GC roots**, lần theo những đường tham chiếu có ý nghĩa giữ sống object và xây dựng tập đối tượng **reachable**. Những object không còn được giữ sống theo quy tắc reachability/reference processing có thể trở thành ứng viên thu hồi.

Đây là nền tảng của chủ đề **khả năng truy cập (reachability)**: phải phân biệt **reference tồn tại**, **reference còn sống về mặt chương trình**, **object reachable từ GC roots**, **object được xử lý theo weak/soft/phantom reference**, và **bộ nhớ thực sự đã được GC thu hồi**.

Điểm quan trọng ngay từ đầu:

- **Reachable** nghĩa là vẫn có đường giữ sống thích hợp từ một hoặc nhiều GC roots theo mô hình của collector.
- **Unreachable** không có nghĩa object được giải phóng ngay tại câu lệnh cuối cùng: GC chạy theo chính sách runtime, còn reference processing, finalization legacy và concurrency có những quy tắc bổ sung.
- **Chu trình reference không tự làm object sống mãi.** Hai object trỏ lẫn nhau nhưng không còn đường giữ sống từ root có thể được GC thu hồi.
- **Reference mạnh, mềm, yếu và phantom không mang cùng ý nghĩa giữ sống.** Nếu duyệt tất cả các cạnh mà không phân loại, bạn sẽ diễn giải sai các trường hợp dùng `WeakReference`, `SoftReference` hay `PhantomReference`.
- **GC roots thuộc trạng thái JVM**, không phải mọi biến từng xuất hiện trong source Java. JIT và liveness analysis có thể xác định reference nào thực sự còn cần để duy trì ngữ nghĩa.

Bài dùng Java 17+ cho ví dụ runnable và phân biệt **mô hình đồ thị để học** với **thuật toán/metadata cụ thể của HotSpot**. JVM Specification không bắt buộc mọi collector dùng một thuật toán marking hay một cấu trúc root table giống nhau.

## 1. Reachability là bài toán đồ thị

Hãy mô hình hóa các object trên heap thành **đỉnh** của một đồ thị. Một field kiểu reference hoặc một phần tử mảng chứa reference có thể tạo **cạnh** từ object đang chứa field/element tới object được trỏ tới.

```text
GC ROOTS
 ├── stack/local reference ──► A ─────► C ────► D
 └── static reference ───────► B ─────► C

                                 E ───► F
                                 ▲      │
                                 └──────┘
```

Trong đồ thị này, giả sử các mũi tên được vẽ là **strong reference edges**:

- **A, B** có đường bắt đầu từ root nên reachable.
- **C** reachable qua A hoặc B; **D** reachable qua C.
- **E và F** trỏ lẫn nhau nhưng không có đường từ root tới chúng. Chu trình E ↔ F không làm chúng reachable.
- Nếu sau đó mất cả hai cạnh A → C và B → C, C và D cũng không còn reachable từ các root **được vẽ**, trừ khi thực tế còn cạnh hoặc root khác chưa được thể hiện.

Đồ thị này là **ví dụ trừu tượng**, không phải ảnh chụp tất cả GC roots của một JVM. Một object vẫn có thể được giữ sống bởi references khác không nằm trong hình, như JNI global handle hoặc local reference còn được VM xem là live.

### Định nghĩa trực quan

Gọi **R** là tập GC roots, và **E** là tập các cạnh **strong references** giữa các object. Tập **strongly reachable** là các object có thể đi tới từ ít nhất một root bằng một chuỗi không hoặc nhiều cạnh có tính giữ sống mạnh.

Một cách mô tả thuật toán:

```text
worklist = các object tham chiếu trực tiếp bởi GC roots
marked   = tập rỗng

while worklist chưa rỗng:
    obj = lấy một object từ worklist
    if obj đã marked:
        tiếp tục
    thêm obj vào marked
    với mỗi strong reference từ obj:
        nếu referent khác null:
            đưa referent vào worklist

marked = tập strongly reachable trong mô hình snapshot này
```

Đây là **pseudocode**, không phải source Java độc lập hay mô tả một thuật toán duy nhất của mọi GC. Mục đích là **duyệt đồ thị có đánh dấu**. Nếu không có tập `marked`, một chu trình A → B → A có thể khiến phép duyệt lặp vô hạn.

### Vì sao không dùng reference counting thuần túy?

Một bộ đếm số reference đơn giản sẽ thấy E có một reference từ F và F có một reference từ E; cả hai có số đếm dương dù không còn root. **Tracing reachability** phân biệt được chu trình còn sống từ root và chu trình bị cô lập.

Không suy ra HotSpot chỉ đếm reference hay chỉ quét field lần lượt một lần: các collectors hiện đại có write barriers, concurrent phases, card tables/remembered sets và reference processing phục vụ tính đúng đắn lẫn hiệu năng.

## 2. GC roots là gì?

**GC root** là một điểm xuất phát mà collector có thể dùng để khám phá object còn cần giữ sống theo trạng thái thực thi JVM. Root thường không phải “object đầu tiên duy nhất”; hệ thống có thể có nhiều tập roots từ các vùng runtime khác nhau.

Những nhóm phổ biến trong HotSpot/JVM:

| Nguồn gốc root | Ví dụ | Lưu ý |
| --- | --- | --- |
| **Live references trong Java thread stacks/frames** | Tham chiếu tới object còn cần cho method đang thực thi | Không phải mọi local variable từng tồn tại đều tiếp tục là root |
| **Static fields / runtime class state còn sống** | Cache object trong static field của một class còn được giữ sống phù hợp | Class loader/class metadata reachability có ảnh hưởng; không phải mọi class từng load được giữ mãi |
| **JNI handles** | JNI global references, một số JNI local references | JNI **weak global** có semantics khác strong JNI references |
| **VM-internal handles/structures** | Object dùng bởi hệ thống VM, runtime services, references đang xử lý | Danh sách chính xác phụ thuộc collector/implementation |
| **Các reference trong đường thực thi native/transition** | Object đã được đưa vào cơ chế JNI/runtime handle | Collector cần giải mã những references theo luật VM |
| **Một số root đặc biệt do collector theo dõi** | Structures phục vụ class unloading, reference processing, code metadata | Cách tổ chức phụ thuộc phiên bản HotSpot |

**Không nên lấy danh sách trên làm hợp đồng bất biến giữa các collectors.** Có những nguồn được một collector coi là root trực tiếp, và nguồn khác được xử lý qua metadata/remembered sets hoặc một pha quét liên quan. Quan trọng là **mọi object phải sống theo ngữ nghĩa chương trình đều được bảo vệ khỏi thu hồi sai**.

### 2.1. Live references trong stack

Ví dụ:

```java
static void printValue() {
    Object value = new Object();
    System.out.println(value);
}
```

Đây là một **method minh họa**, cần đặt trong class để biên dịch. Trong lúc `value` còn cần cho `println`, VM phải bảo toàn khả năng dùng object đúng ngữ nghĩa. Nhưng **không thể khẳng định biến `value` là GC root mạnh suốt toàn bộ thời gian method còn nằm trên call stack**.

JIT có thể xác định **điểm sử dụng cuối** của reference và tối ưu những liveness intervals. Sau lần dùng cuối, một object có thể trở nên không còn strongly reachable sớm hơn thời điểm block/method kết thúc, nếu điều đó bảo toàn semantics.

Vì vậy câu “local variable còn trong scope Java thì object chắc chắn không bao giờ được GC” là một **kết luận quá mạnh**. Java-level scope và liveness mà VM cần duy trì không đồng nhất.

### 2.2. Static fields và class loaders

```java
class Registry {
    static Object shared = new Object();
}
```

Khi `Registry` và runtime state của nó được giữ sống, `Registry.shared` có thể góp một **đường giữ sống** tới object. Nhưng static state không là “bộ nhớ bất tử” trong mọi trường hợp: khi một **defining class loader** và các class do nó định nghĩa đủ điều kiện unloading, metadata/static references liên quan cũng có thể được giải phóng theo quy tắc VM.

**Lỗi memory leak phổ biến:** static collection của một class sống lâu giữ reference mạnh tới nhiều object không còn cần dùng:

```java
static final java.util.List<Object> CACHE = new java.util.ArrayList<>();
```

Đây chỉ là field minh họa trong một class. Nếu ứng dụng cứ thêm items vào `CACHE` mà không loại bỏ, các phần tử có thể tiếp tục reachable dù nghiệp vụ đã “quên” chúng. GC không thể tự biết object nào đã hết giá trị nghiệp vụ khi vẫn còn đường strong reference từ root.

### 2.3. JNI global và weak global references

Native code thông qua JNI có thể giữ reference tới Java objects bằng các JNI handles. **Global reference mạnh** có thể góp phần giữ sống object, trong khi **weak global reference** được xử lý theo quy tắc weak reachability thích hợp.

Điểm quan trọng: nếu heap analyzer không thể chỉ ra một static field hay local Java cụ thể nhưng object vẫn reachable, cần cân nhắc **JNI handles**, runtime services, class loaders, thread locals và các nguồn ngoài những field source thông thường.

## 3. Khi duyệt, GC theo cạnh nào?

Với một object reachable, collector có thể nhận diện references trong:

- **Instance reference fields**, ví dụ `Node.next`.
- **Reference array elements**, ví dụ các vị trí của `Object[]`.
- **Các object được nhắc tới gián tiếp thông qua metadata/handles** theo cơ chế VM.
- **Một số cấu trúc đặc biệt** như `java.lang.ref.Reference` phải xử lý theo luật khác với một field mạnh thông thường.

Không phải mọi byte trong một object đều là pointer:

```java
class Holder {
    int count;
    long timestamp;
    Object value;
}
```

Trong ví dụ này, `count` và `timestamp` là dữ liệu primitive, không phải heap pointers mà GC được phép tùy ý diễn giải. `value` là một reference field và có thể đóng vai trò cạnh.

HotSpot dựa vào **metadata về layout** để biết vị trí chứa object references trong fields, arrays và runtime frames. JIT-compiled code còn cung cấp **oop maps** hoặc thông tin tương đương tại các điểm GC cần biết vị trí live references trong registers/stack. Điều đó cho phép precise GC khác với lối coi mọi word có hình dạng địa chỉ là một root chắc chắn.

### 3.1. Không phải “mọi reference đều mạnh”

`WeakReference`, `SoftReference` và `PhantomReference` là những đối tượng Java có field nội bộ liên hệ tới referent. Tuy nhiên collector **không được coi cạnh tới referent từ những đối tượng này giống hệt một ordinary strong field**.

Ví dụ:

```java
java.lang.ref.WeakReference<Object> weak =
        new java.lang.ref.WeakReference<>(new Object());
```

Đây là đoạn trong method. Biến `weak` có thể còn strongly reachable, nhưng object **được `weak` tham chiếu tới** không vì thế tự có một strong path. Việc referent tồn tại tới lúc GC nào là chuyện khác, tùy còn đường strong/soft references ở nơi khác và timing GC.

Đừng lẫn **reference object** (`WeakReference` instance) với **referent** (object mà `get()` có thể trả về).

## 4. Marking: đánh dấu tập reachable

Một thuật toán tracing GC có thể chia công việc thành:

1. **Root discovery/scanning**: xác định các references xuất phát từ root set.
2. **Graph traversal**: lần theo những cạnh cần giữ sống, theo dõi objects đã thăm.
3. **Reference processing**: xử lý soft/weak/phantom references và các semantics đặc biệt.
4. **Reclamation**: cho phép tái sử dụng vùng nhớ của các objects không còn cần giữ sống, bằng sweep/copy/compaction hay cơ chế tương ứng.
5. **Cập nhật pointers/metadata khi di chuyển objects**: giữ các references còn sống trỏ đúng địa chỉ mới, nếu collector dùng moving/compacting collection.

Đây là **khung ý tưởng**, không phải thứ tự cứng của mọi JVM. Những pha có thể lồng nhau, chạy đồng thời hoặc khác nhau giữa Serial, Parallel, G1, ZGC, Shenandoah và các collectors khác.

### 4.1. Tri-color abstraction

Trong nhiều tài liệu, tracing được giải thích qua ba màu:

| Màu | Ý nghĩa trong mô hình |
| --- | --- |
| **White** | Chưa được phát hiện/đánh dấu |
| **Gray** | Đã được tìm thấy nhưng còn edges cần quét |
| **Black** | Đã quét xong các edges cần xem |

```text
Roots → GRAY
             │
             ▼
     lấy một gray object
             │
      khám phá children
             │
     white child → gray
             │
             ▼
   object vừa quét → black
```

Kết thúc tracing, những objects còn **white** trong tập đang được thu gom có thể trở thành ứng viên reclaim, **sau khi** thỏa các quy tắc reference processing, finalization legacy và collectors cụ thể.

**Tri-color là mô hình suy luận**, không bảo đảm mỗi collector có đúng ba bit màu vật lý được đặt trong mỗi object header. Có thể dùng mark bitmaps, remembered sets, queues và metadata phức tạp hơn.

### 4.2. DFS, BFS hay work stealing đều có thể đúng

Collector có thể dùng stack, queue, bitmap hoặc nhiều worklists song song để duyệt đồ thị. Thứ tự thăm A trước B hay B trước A không làm thay đổi tập reachable trong một **snapshot bất biến** nếu thuật toán hoàn chỉnh và xử lý chu trình đúng.

Với concurrent collectors, đồ thị **không bất biến** vì application threads (mutators) vẫn tạo/xóa refs. Khi đó cần cơ chế bổ sung để không bỏ sót các objects phải sống.

## 5. Stop-the-world và concurrent marking khác nhau như thế nào?

### Stop-the-world (STW)

Trong những pha STW, JVM tạm dừng application threads ở các điểm an toàn tương ứng để collector quan sát/truy cập trạng thái đủ nhất quán. Điều này làm dễ hình dung root scanning và một số thao tác di chuyển/cập nhật object graph.

Không phải mọi GC đều STW xuyên suốt toàn bộ chu kỳ. Nhiều collectors có một số pha STW ngắn và một số pha concurrent; độ dài pause phụ thuộc collector và workload.

### Concurrent marking

Trong concurrent marking, một hoặc nhiều GC threads quét đồ thị **trong khi mutator threads còn thay đổi references**. Điều đó tạo bài toán:

```text
Thời điểm 1:
Root → A → B

Đang mark thì application sửa:
Root → A
       A.child = null
Root → C (đối tượng mới được publish)
```

Nếu collector bỏ qua những thay đổi quan trọng, nó có thể bỏ sót object còn cần sống. Vì vậy các concurrent collectors sử dụng **barriers** và các luật về marking state để bảo đảm tính đúng đắn.

Hai ý tưởng tiêu biểu:

- **Snapshot-at-the-beginning (SATB):** theo dõi một cách thích hợp các references cũ khi application xóa/ghi đè cạnh, duy trì mô hình snapshot ban đầu theo invariants của collector.
- **Incremental-update:** ghi nhận các cạnh mới/changes cần xử lý để bảo toàn invariant khi object đã quét xong được cập nhật trỏ tới object chưa đánh dấu.

**Không nên nói tất cả JVM collectors dùng SATB hoặc tất cả đều dùng incremental-update cùng một cách.** Đây là hai họ kỹ thuật. Một collector có thể dùng nhiều barriers cho mục tiêu khác nhau (marking, card marking, relocation, load barrier...).

### 5.1. Write barrier là gì?

Một write barrier trong GC là đoạn cơ chế runtime được thực hiện quanh thao tác **ghi một reference** để cập nhật thông tin cho collector khi cần. Nó khác với Java memory-order barrier trong thảo luận JMM, dù tên tương tự.

Ví dụ source:

```java
a.next = b;
```

Compiler/JVM có thể thực hiện thao tác ghi reference cùng hoạt động barrier liên quan. **Barrier không phải một method Java source bắt buộc người lập trình phải tự gọi.** Nó có thể được JVM/JIT chèn vào machine code.

### 5.2. Load barriers và moving collectors

Một số collectors concurrent/moving dùng **load barriers** hoặc cơ chế đọc references đặc biệt để bảo đảm Java threads có reference hợp lệ trong khi objects di chuyển.

Đừng giả định bất kỳ reference nào cũng được cập nhật cùng cách: chiến lược VM có thể dùng forwarding pointers, colored pointers, relocation metadata hoặc phương án khác. Người viết Java chủ yếu cần tuân theo Java Memory Model và dùng các APIs chuẩn; không tự thao tác raw addresses.

## 6. Generational GC: quét gốc và nhớ cạnh từ vùng khác

Nhiều collectors chia heap thành vùng theo tuổi/lifetime (young/old generations hoặc regions). Một **young collection** không cần xem toàn bộ old generation như một root khổng lồ rồi quét mọi object sâu như full-heap traversal.

Vấn đề minh họa:

```text
Young region: Y
                 ▲
                 │
Old object O ────┘
```

Nếu object `O` ở vùng cũ giữ reference tới `Y` ở vùng trẻ, một collection chỉ tập trung vào young generation **không được bỏ qua cạnh O → Y** chỉ vì O nằm ngoài vùng đang thu gom.

**Remembered sets**, **card tables** và các cơ chế liên quan cho phép collector biết những vùng ngoài collection set có thể chứa references đi vào vùng cần thu gom. Chúng bổ sung cho root scanning.

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Collection set** | Những regions/generations đang được xử lý trong đợt thu gom phù hợp |
| **Remembered set** | Ghi nhớ references/locations từ ngoài vùng thu gom có thể trỏ vào vùng đó |
| **Card table** | Metadata thường dùng để theo dõi các vùng bộ nhớ đã bị ghi reference |
| **Post-write barrier** | Cơ chế đánh dấu/cập nhật metadata sau ghi phù hợp với collector |
| **Cross-generational reference** | Cạnh ví dụ old → young cần được tính khi thu gom young |

**Rút ra:** đồ thị reachable logic vẫn là nền tảng, nhưng collector tối ưu không nhất thiết quét **toàn bộ heap từ đầu** trong từng young GC. Nó sử dụng root set liên quan và metadata ghi nhớ các cạnh xuyên vùng.

## 7. Strong, soft, weak và phantom reachability

Java có API `java.lang.ref` để làm việc với những mức reference đặc biệt. Không thể thay chúng bằng một luật đơn giản “object nằm trên heap hoặc không”.

| Loại tham chiếu | Ý nghĩa khái quát | API ví dụ |
| --- | --- | --- |
| **Strong reference** | Đường tham chiếu thông thường có khả năng giữ object sống | `Object x = new Object()`, field, array element |
| **Soft reference** | Cho phép GC thu hồi referent theo chính sách, thường liên quan memory pressure | `SoftReference<T>` |
| **Weak reference** | Không ngăn việc collector thu hồi referent nếu chỉ còn weak reachability | `WeakReference<T>` |
| **Phantom reference** | Hỗ trợ theo dõi giai đoạn sau khi object không còn reachable ở mức mạnh hơn, thường đi với `ReferenceQueue` | `PhantomReference<T>` |
| **Finalizer legacy** | Cơ chế finalization lịch sử, không đáng tin cho quản lý tài nguyên | `finalize()` (deprecated từ Java 9; deprecated for removal từ JDK 18) |

### 7.1. Strong reference

```java
Object keepAlive = new Object();
```

Reference `keepAlive` có ý nghĩa giữ sống object **khi chính reference đó thực sự live và nằm trên đường được VM coi là cần bảo toàn**. Không thể suy ra một biến đã gán nhưng không còn sử dụng sẽ giữ object tới hết method.

Ordinary strong paths là tuyến nền tảng của thuật toán marking được trình bày ở mục 1.

### 7.2. SoftReference

```java
java.lang.ref.SoftReference<byte[]> soft =
        new java.lang.ref.SoftReference<>(new byte[1_024]);
```

Một `SoftReference` cho phép GC đưa ra quyết định thu hồi referent theo chính sách. Soft references từng hay được dùng để làm caches, nhưng **không nên coi soft cache là cơ chế giới hạn memory chính xác hay giải pháp thay thế cache eviction có kiểm soát**.

Theo API, soft references tới các objects softly reachable có các bảo đảm xử lý liên quan tới `OutOfMemoryError` trong phạm vi được mô tả, nhưng không có cam kết “GC sẽ xóa soft refs theo thứ tự LRU rõ ràng” hoặc “soft.get() chắc chắn khác null nếu heap còn X MB”. Các heuristics cụ thể phụ thuộc implementation.

### 7.3. WeakReference

```java
Object strong = new Object();
java.lang.ref.WeakReference<Object> weak =
        new java.lang.ref.WeakReference<>(strong);
```

Khi `strong` còn là một strong path live tới object, weak referent không bị clear chỉ vì tồn tại `WeakReference`. Nếu toàn bộ strong paths mất và object chỉ còn weakly reachable (không có soft path mạnh hơn còn giữ nó), một GC thích hợp có thể **clear weak reference** và đưa nó vào queue theo luật.

Ví dụ có `strong = null` **không tự động khiến `weak.get() == null` ngay dòng sau**. GC có thể chưa chạy; JIT liveness và paths khác cũng ảnh hưởng.

`WeakHashMap` sử dụng weak references cho **keys**, hữu ích trong những tình huống liên kết metadata với keys mà không muốn map giữ chúng sống bằng key reference của chính map. Nhưng nếu value giữ strong reference trở lại key, key vẫn có thể sống qua đường map → value → key; thiết kế cache cần xem đồ thị thực tế.

### 7.4. PhantomReference

```java
java.lang.ref.ReferenceQueue<Object> queue =
        new java.lang.ref.ReferenceQueue<>();
java.lang.ref.PhantomReference<Object> phantom =
        new java.lang.ref.PhantomReference<>(new Object(), queue);
```

**`phantom.get()` luôn trả `null`**, bất kể referent đã đến giai đoạn nào. API này dùng với queue để theo dõi các sự kiện GC-reference processing theo quy tắc, thường làm nền cho cơ chế cleanup không phụ thuộc finalizer.

Không dùng phantom reference để “cứu” object trở lại sử dụng như strong reference. Khi cần giải phóng file/socket/native resource đúng lúc, ưu tiên **`AutoCloseable` / try-with-resources**. Cleaner/phantom cleanup là cơ chế dự phòng có tính bất định về thời điểm, không bảo đảm sẽ chạy trước khi tiến trình thoát.

### 7.5. Thứ bậc reachability là khái niệm tinh tế

Trong mô hình Java references, có các trạng thái như **strongly reachable**, **softly reachable**, **weakly reachable**, **phantom reachable** và **unreachable**, phụ thuộc còn đường mạnh hơn hay không. Đường đi qua một `SoftReference` hoặc `WeakReference` không có cùng ngữ nghĩa giữ sống với ordinary field.

Đây không phải một bảng “GC chắc chắn reclaim trong X mili-giây” cho từng loại. Collector còn phải xử lý queues, reference clearing, cleanup, finalization legacy và các điều kiện runtime.

## 8. ReferenceQueue: quan sát thông báo, không ép GC theo lịch

Một `ReferenceQueue` nhận những `Reference` objects đã được xử lý/enqueue theo quy tắc API. Nó hữu ích để ứng dụng biết rằng referent đã đạt trạng thái liên quan, nhưng **không là lịch định thời GC**.

```java
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

class QueueExample {
    static void show() {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object value = new Object();
        WeakReference<Object> weak = new WeakReference<>(value, queue);

        System.out.println(weak.get() == value); // true tại thời điểm này
        value = null;

        // Không được khẳng định queue.poll() chắc chắn có kết quả ngay đây.
        System.out.println("GC timing is not deterministic");
    }
}
```

Đây là **class đầy đủ cú pháp** với static method `show()`, nhưng **không có main**. Hai dòng println bên trong minh họa cách dùng; để chạy độc lập cần gọi `QueueExample.show()` từ một main class.

**Hai điều khác nhau:** `weak.get()` có thể đã bị clear khi GC chạy; `queue.poll()` có thể chưa trả tham chiếu tương ứng vào đúng thời điểm bạn hỏi. Giữ `WeakReference` object mạnh khi muốn quan sát queue; bản thân reference object cũng có lifecycle riêng.

### Lưu ý về `System.gc()`

`System.gc()` về bản chất là **gợi ý/yêu cầu** đối với runtime, không phải hợp đồng cho phép test assert rằng ngay sau lời gọi, một object cụ thể đã được thu hồi. Có JVM flags/chế độ trong đó explicit GC bị bỏ qua hoặc xử lý khác. **Không dùng `Thread.sleep` + `System.gc` như một “đồng hồ chứng minh” reachability.**

## 9. Chu trình tham chiếu: điểm yếu của suy nghĩ theo reference count

Giả sử hai objects:

```java
class Node {
    Node next;
}
```

và trong method:

```java
Node a = new Node();
Node b = new Node();
a.next = b;
b.next = a;
```

Đây là code minh họa trong một method. Khi **không còn strong path từ GC roots** tới a hay b, vòng a → b → a không đủ giúp chúng sống. Một tracing collector có thể không đánh dấu chúng trong lần tracing phù hợp.

Nhưng **đừng vội kết luận GC đã thu hồi ngay** khi viết:

```java
a = null;
b = null;
```

Việc gán null không tự tạo ra một GC cycle và cũng không chứng minh không có reference khác (static cache, JNI, thread-local, field của một object sống, v.v.). Điều kiện đủ để một object **có thể là rác** phải xét **toàn bộ root graph thực tế**, không chỉ hai biến trong đoạn code.

### Cycle reachable và unreachable khác nhau

```text
Case 1: ROOT → A → B → A  (cả A và B reachable)
Case 2:         A → B → A  (không có đường root nào, có thể reclaim)
```

Trong case 1, cycle vẫn sống vì có **một lối vào từ root**. Trong case 2, mọi object của cycle có thể là ứng viên thu hồi sau khi xử lý các loại reference đặc biệt phù hợp.

## 10. Ví dụ Java 17+ chạy được: mô phỏng graph tracing có chu trình

Chương trình sau **không tự gọi GC**. Nó mô phỏng thuật toán duyệt từ một tập roots **do người học lựa chọn**, để có output **chính xác và có thể kiểm chứng**, không phụ thuộc thời điểm collector chạy.

Tạo file **`ReachabilityGraphDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class ReachabilityGraphDemo {
    static final class Node {
        final String name;
        Node first;
        Node second;

        Node(String name) {
            this.name = name;
        }
    }

    static Set<Node> trace(Node... roots) {
        Set<Node> marked =
                Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        Deque<Node> worklist = new ArrayDeque<>();

        for (Node root : roots) {
            if (root != null) {
                worklist.addLast(root);
            }
        }

        while (!worklist.isEmpty()) {
            Node current = worklist.removeFirst();

            if (!marked.add(current)) {
                continue;
            }

            if (current.first != null) {
                worklist.addLast(current.first);
            }
            if (current.second != null) {
                worklist.addLast(current.second);
            }
        }
        return marked;
    }

    static void printMarked(Set<Node> marked) {
        List<String> names = new ArrayList<>();
        for (Node node : marked) {
            names.add(node.name);
        }
        Collections.sort(names);
        System.out.println("reachable (" + marked.size() + "): " + names);
    }

    public static void main(String[] args) {
        Node rootA = new Node("rootA");
        Node rootB = new Node("rootB");
        Node branch = new Node("branch");
        Node leaf = new Node("leaf");
        Node orphanA = new Node("orphanA");
        Node orphanB = new Node("orphanB");

        rootA.first = branch;
        rootB.first = branch;
        branch.first = leaf;
        leaf.first = rootA; // Chu trình reachable từ rootA/rootB.

        orphanA.first = orphanB;
        orphanB.first = orphanA; // Chu trình tách khỏi roots đã chọn.

        System.out.println("total modeled nodes: 6");
        Set<Node> firstPass = trace(rootA, rootB);
        printMarked(firstPass);
        System.out.println("outside selected roots: " + (6 - firstPass.size()));

        rootA.first = null;
        rootB.first = null;

        Set<Node> secondPass = trace(rootA, rootB);
        printMarked(secondPass);
        System.out.println("outside selected roots: " + (6 - secondPass.size()));
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 ReachabilityGraphDemo.java
java ReachabilityGraphDemo
```

**Standard output chính xác:**

```text
total modeled nodes: 6
reachable (4): [branch, leaf, rootA, rootB]
outside selected roots: 2
reachable (2): [rootA, rootB]
outside selected roots: 4
```

### Giải thích từng dòng

1. Chương trình tạo **sáu `Node` objects** trong đồ thị mô hình.
2. `trace(rootA, rootB)` có hai roots và tìm được `rootA`, `rootB`, `branch`, `leaf`, tức bốn nodes.
3. Chu trình `orphanA ↔ orphanB` không có cạnh từ hai roots đã chọn, nên hai nodes đó không được `trace` đánh dấu.
4. Gỡ hai cạnh `rootA.first` và `rootB.first`; lần tracing thứ hai chỉ tìm được hai roots. `branch`/`leaf` đã mất lối vào, và chu trình orphan vẫn không có lối vào.
5. Dùng `IdentityHashMap` để tập `marked` so **object identity (`==`)**. Đánh dấu GC theo object identity; không dùng `equals()` do application override để tự gom các object khác nhau thành một.
6. `Collections.sort(names)` làm output theo tên ổn định dù traversal order hoặc identity hash khác nhau.

**Cực kỳ quan trọng:** output `outside selected roots` có nghĩa **không reachable trong đồ thị `Node.first/second` từ hai roots mô phỏng**. Nó **KHÔNG** chứng minh HotSpot đã thật sự thu hồi object nào! Trong `main`, các local variables `branch`, `leaf`, `orphanA`, `orphanB` và tập `firstPass` có thể vẫn chứa references mà JVM phải xét tùy liveness. Mô phỏng cũng bỏ qua references trong `String name`, collection metadata và các roots khác.

Đây là lý do viết mô hình cho học tập và đo GC thực tế là **hai nhiệm vụ khác nhau**. Mô hình minh họa thuật toán chính xác trong phạm vi đồ thị đã định nghĩa; không tự xưng là một heap inspector.

### 10.1. Độ phức tạp của tracing

Nếu mỗi object được đánh dấu đúng một lần và mỗi strong reference edge được xét hữu hạn lần, phép duyệt đồ thị có độ phức tạp **O(V + E)** với V = số object vertices và E = số reference edges trong phạm vi duyệt. Bộ nhớ phụ thường phụ thuộc tập marked và worklist.

Ở JVM thực tế, chi phí còn có:

- **Root scanning** và metadata tìm references.
- **Barriers/remembered sets** khi concurrent hoặc generational.
- **Reference processing**, moving/compaction và pointer updates.
- **Load balancing/work stealing** giữa GC threads.
- **Object layout**, region selection, card scanning và fragmentation.

Không dùng O(V+E) để suy ra tất cả GC pause đều có cùng thời gian tuyến tính trên toàn heap: các collector tối ưu theo tập object/region cần xử lý, thực hiện song song hoặc concurrent, và chỉ một số pha mới STW.

## 11. `Reference.reachabilityFence`: giữ sống object tới điểm cần thiết

JDK cung cấp API **`Reference.reachabilityFence(Object)`** từ Java 9 để đảm bảo một object còn **strongly reachable** cho tới một điểm thực thi cụ thể, khi điều đó quan trọng cho an toàn resource/native code.

Ví dụ khái niệm:

```java
import java.lang.ref.Reference;

final class NativeResource {
    final long handle;

    NativeResource(long handle) {
        this.handle = handle;
    }

    void perform() {
        long h = handle;
        NativeApi.useHandle(h);
        Reference.reachabilityFence(this);
    }
}
```

**Đây là đoạn minh họa, chưa chạy độc lập**: `NativeApi` chưa được định nghĩa, và `handle` chỉ là đại diện cho tài nguyên native. Ý nghĩa:

- Trong đường thực thi, sau khi lấy `handle`, native code có thể đang sử dụng tài nguyên gắn với object.
- Không đủ an toàn để giả định receiver `this` tự động live đến hết method chỉ vì method chưa return.
- `reachabilityFence(this)` giúp bảo đảm receiver sống tới sau lời gọi native trong ngữ cảnh được yêu cầu.
- API **không yêu cầu GC thu hồi object ngay sau fence**, và cũng không thay thế quản lý vòng đời tài nguyên tường minh.

Nếu resource cần đóng đúng lúc, dùng `AutoCloseable`/try-with-resources vẫn là cách thiết kế ưu tiên.

## 12. Finalization và object resurrection: ngoại lệ lịch sử cần biết

Các lớp Java cũ đôi khi override `finalize()`. Trong những mô hình finalization legacy, finalizer có thể làm object **reachable trở lại** (resurrection), ví dụ gán `this` vào một static field. Vì thế sơ đồ “không strongly reachable thì chắc chắn giải phóng ngay” không đủ mô tả mọi pha xử lý.

Tuy nhiên:

- **Finalization bị deprecated từ Java 9 và deprecated for removal từ JDK 18**; không khuyến khích cơ chế này trong Java hiện đại.
- Không có bảo đảm finalizer sẽ chạy đúng lúc hoặc chắc chắn trước khi process thoát.
- Finalizer có rủi ro security, reliability, deadlocks và memory retention.
- Không nên viết ứng dụng mới dựa trên resurrection hay `System.gc()` để ép finalize.

Thay thế hợp lý là **explicit close**, `try-with-resources`, và **Cleaner/phantom-based cleanup** chỉ như cơ chế dự phòng khi cần. Cleaner cũng có tính bất định về thời điểm, và cleaning action không nên giữ strong reference ngược lại tới referent mà nó cần theo dõi.

Khi đọc tài liệu reachability rất cũ, hãy nhận biết luật **finalization queue** có thể làm collector phải giữ objects thêm thời gian; các JVM/JDK mới tiếp tục phát triển để loại bỏ đường khởi tạo finalizer không an toàn.

## 13. GC eligibility khác thời điểm thu hồi và thời điểm tái sử dụng bộ nhớ

Ba trạng thái dễ lẫn:

| Câu hỏi | Ý nghĩa |
| --- | --- |
| Object còn strongly reachable? | Có đường reference mạnh từ roots tới object tại trạng thái liên quan? |
| Object eligible for collection? | Có còn cần bảo toàn theo các quy tắc reachability/reference processing khác không? |
| Bộ nhớ đã được reclaim/reused? | Collector đã thực hiện pha cho phép tái sử dụng không gian phù hợp chưa? |

Một object không còn reachable có thể:

- Nằm ở vùng heap chưa được collection cycle nào xử lý.
- Đang chờ một số reference processing/cleanup theo semantics.
- Có memory space được collector đánh dấu có thể dùng lại nhưng không lập tức trả về OS.
- Được thu hồi trong một collection, nhưng **heap committed size** hoặc **process RSS** không giảm tương ứng ngay.

Vì vậy khi đo memory:

- `-Xmx` là giới hạn Java heap, **không** là số byte heap đang dùng.
- `used heap` không đồng nghĩa retained size của một object graph.
- `committed` và `reserved` khác nhau; OS RSS lại là metric khác.
- Object đã chết không chứng minh heap sẽ lập tức thu nhỏ.
- Một memory leak Java thường là **unintended reachability**, không phải GC “quên chạy”.

## 14. Memory leaks do đối tượng còn reachable ngoài ý muốn

Một tracing GC **không thể biết logic nghiệp vụ** rằng object đã “không còn cần nữa”. Nếu vẫn còn đường strong reference từ root, nó vẫn được giữ sống.

### 14.1. Static collections

```java
import java.util.ArrayList;
import java.util.List;

class Cache {
    static final List<byte[]> ITEMS = new ArrayList<>();

    static void add() {
        ITEMS.add(new byte[1024]);
    }
}
```

Đây là class minh họa; khi class/static state còn sống, mỗi `byte[]` được thêm vào list sẽ có strong path qua `ITEMS` → list backing array → phần tử, cho tới khi ứng dụng loại bỏ reference hoặc class được unload phù hợp.

Dùng cache có **giới hạn số entry, TTL, eviction/size policy** và cách đóng tài nguyên liên quan khi thực tế cần cache.

### 14.2. ThreadLocal không được cleanup

Một `ThreadLocal` có thể làm dữ liệu sống lâu vì thread trong thread pool vẫn tồn tại. Dù key có weak-reference semantics ở một số cấu trúc HotSpot/JDK implementation, các **values** có thể còn được giữ qua thread-local map cho tới khi entries được dọn.

Quy tắc lập trình an toàn khi dùng thread pools:

```java
private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

static void process() {
    try {
        CONTEXT.set("request");
        // xử lý request ...
    } finally {
        CONTEXT.remove();
    }
}
```

Đây là **đoạn đặt trong một class**, không có main. Dòng `remove()` giúp tránh rò state qua thread tái sử dụng. Không nhầm `ThreadLocal` với “dữ liệu tự hủy khi method kết thúc”.

### 14.3. Listener, callbacks, executors và queues

- Event publisher có thể giữ listener instances bằng strong references.
- Scheduled tasks/futures có thể giữ closures/objects được capture.
- Blocking queue có thể giữ các jobs, payloads hoặc responses chưa được drain.
- Long-lived class loaders có thể giữ classes/metadata và chuỗi object graphs liên quan.
- JNI global references giữ object khỏi GC dù Java source không còn biến được nhìn thấy.

**Tối ưu vấn đề reachability là cắt/giới hạn đường giữ sống không cần thiết**, chứ không cố gọi `System.gc()` liên tục.

## 15. Dùng heap dump và GC-root paths để debug leak

Các heap analyzers (ví dụ Eclipse Memory Analyzer, VisualVM hoặc tooling thích hợp) có thể giúp:

1. Chụp heap dump tại thời điểm cần điều tra.
2. Xác định classes có retained heap lớn hoặc tăng bất thường.
3. Chọn một object/cụm object nghi ngờ.
4. Xem **Paths to GC Roots** hoặc retained/dominator tree.
5. Đánh giá cạnh nào là **strong references** và những loại reference nào cần loại khỏi đường giữ sống để thấy nguyên nhân.
6. Xác định field, array element, static cache, ThreadLocal, listener, JNI handle hoặc class loader gây giữ object.
7. Sửa vòng đời references và đo lại sau một chu kỳ tải có thể so sánh.

### Paths to GC Roots trả lời câu hỏi gì?

Giả sử công cụ tìm thấy:

```text
GC Root: live application thread
  → Thread.threadLocals
  → ThreadLocalMap.Entry.value
  → RequestContext
  → byte[] payload
```

Đây là **đường giữ sống minh họa**, không phải kết quả bắt buộc trên mọi JDK. Nếu request đã kết thúc nhưng `RequestContext` vẫn ở thread-local value của worker thread, thì GC **đúng khi giữ nó**, vì vẫn có đường strong reference từ root.

Một cách sửa có cơ sở là thiết kế lifecycle: `ThreadLocal.remove()` trong `finally`, giới hạn queues/cache, unregister listeners, release JNI handles, close resources. **Không tùy tiện xóa reference còn đang cần cho thread khác**, vì điều đó có thể tạo race hoặc làm ứng dụng sai ngữ nghĩa.

### Dominator/retained size không đồng nhất với kích thước shallow

Một object có thể nhỏ nhưng **retain** rất nhiều objects khi nó là đường root duy nhất giữ chúng sống. Ví dụ static `Map` object shallow size có thể nhỏ, nhưng keys/values/arrays giữ hàng trăm MB. Phân tích retained size hoặc dominator tree giúp tìm “ai đang giữ ai”, chứ không chỉ bảng classes có nhiều instances.

## 16. GC log và các thước đo không thể thay thế graph analysis

Để hiểu tần suất collection, pause time và memory pressure, có thể bật logging phù hợp cho **HotSpot JDK 17+**:

```bash
java -Xlog:gc*:info -version
```

Lệnh trên chạy JVM để in version và GC-related messages tùy thời điểm; nó **không** tự tạo workload đáng kể hay cho sẵn một heap dump. Với chương trình mô phỏng bên trên:

```bash
java -Xlog:gc*:info ReachabilityGraphDemo
```

Nếu output gồm GC logs, số dòng/timing phụ thuộc VM, heap size và tải. **Không có cam kết chắc chắn phải xảy ra GC cycle trong bài demo nhỏ**. Do đó chỉ hai nhóm `println` của chương trình (mục 10) có stdout dự kiến ổn định khi chạy không bật logging.

Một số chẩn đoán JDK phổ biến:

```bash
jcmd -l
jcmd PID GC.heap_info
jcmd PID GC.class_histogram
jcmd PID GC.heap_dump heap.hprof
```

Trong các lệnh trên, **`PID` là số tiến trình thật, phải thay bằng PID phù hợp**. Việc lấy class histogram/heap dump có thể tốn bộ nhớ, dung lượng đĩa hoặc gây pause lớn; chỉ thực hiện ở môi trường được phép. Các commands và tác dụng thực tế cần đối chiếu `jcmd PID help` của JVM đang chạy.

**GC logs cho biết khi nào collector làm việc và thống kê bộ nhớ; heap dump/root paths giúp chỉ ra tại sao object còn reachable.** Chúng giải quyết những câu hỏi khác nhau.

## 17. Những hiểu nhầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Biến gán `null` là object được free ngay.” | Chỉ mất một đường tham chiếu; còn các paths khác và GC chưa chắc đã chạy |
| “Hai objects trỏ lẫn nhau sẽ không bao giờ bị GC.” | Tracing GC có thể thu hồi chu trình **không reachable từ roots** |
| “GC chỉ đếm số reference tới object.” | Tracing collectors phân tích **reachability từ roots**, có xử lý special references |
| “Mọi local variable trong Java scope đều là GC root đến hết method.” | Liveness/JIT optimizations có thể rút ngắn thời gian VM cần giữ object sống |
| “Mọi loaded class có static fields đều tồn tại đến khi process tắt.” | Class unloading/class-loader lifecycle có thể làm runtime classes không còn sống |
| “`WeakReference` giữ object mạnh như field thông thường.” | Weak reference không ngăn clearing khi object chỉ còn weakly reachable |
| “`weak.get() == null` ngay sau khi `strong = null`.” | Không có bảo đảm timing GC; paths khác cũng có thể giữ referent |
| “`System.gc()` chắc chắn dọn mọi object đã chết ngay.” | Không có bảo đảm như vậy và runtime có thể bỏ qua explicit GC |
| “`ReferenceQueue.poll()` chắc chắn báo event ngay sau khi bỏ strong ref.” | Queue phụ thuộc reference processing/enqueue và timing runtime |
| “PhantomReference.get() trả object để phục hồi.” | Nó **luôn trả null** |
| “Concurrent GC chỉ quét graph một lần mà không cần theo dõi writes.” | Concurrent collectors cần barriers/invariants cho graph đang thay đổi |
| “Young GC bỏ qua mọi old-to-young refs.” | Remembered sets/card tables hỗ trợ xử lý cross-generation edges |
| “GC thu hồi object thì RSS phải giảm ngay.” | Heap committed/reserved và RSS khác, collector có thể giữ vùng trống để tái sử dụng |
| “Đối tượng reachable nhưng không còn giá trị nghiệp vụ là rác mà GC phải tự hiểu.” | Nếu còn strong path, GC phải giữ nó; đây là nguyên nhân Java memory leaks phổ biến |
| “Heap dump tự giải thích leak mà không cần xét roots.” | Cần xem retaining paths, dominators và object lifecycle |
| “Một class trống không thể làm object khác sống.” | Object/class/static references/runtime handles có thể tham gia root graph tùy lifecycle |

## 18. Bài tập và lời giải

### Bài 1 — Chu trình không có đường từ root

```text
ROOT → A       B ↔ C
```

**Hỏi:** Nếu không có edges nào khác, B và C còn strongly reachable từ root không?  
**Đáp án:** Không. Chúng tạo chu trình tự cô lập; tracing từ ROOT chỉ tìm thấy A.

### Bài 2 — Chu trình vẫn reachable

```text
ROOT → A → B → C → B
```

**Hỏi:** B/C có thể xem là rác chỉ vì chúng tạo vòng?  
**Đáp án:** Không. ROOT có strong path qua A tới B/C.

### Bài 3 — Một object, hai root paths

```text
ROOT1 → A → X
ROOT2 → B → X
```

**Hỏi:** Bỏ cạnh `A → X` đã đủ làm X unreachable?  
**Đáp án:** Chưa, vì còn `ROOT2 → B → X`.

### Bài 4 — Local variable và scope

**Hỏi:** Kết thúc một block source Java có đảm bảo GC lập tức reclaim object từng được biến local tham chiếu?  
**Đáp án:** Không. Cần xét all strong paths, liveness và lịch GC; ngược lại, có khi VM không cần giữ object đến tận cuối block nếu reference không còn được sử dụng.

### Bài 5 — `WeakReference`

**Hỏi:** `WeakReference<Object> w = new WeakReference<>(o);` nếu `o` còn một strong root path thì GC có được clear weak referent chỉ vì `w` là weak?  
**Đáp án:** Không. Object vẫn strongly reachable qua đường mạnh.

### Bài 6 — Một weak reference là đường duy nhất

**Hỏi:** Khi object chỉ còn weakly reachable và collector xử lý weak references, `w.get()` có thể thành gì?  
**Đáp án:** `null`, khi weak reference được cleared theo quy tắc. Thời điểm cụ thể không thể bảo đảm từ một phép gán null trong source.

### Bài 7 — Phantom get

**Hỏi:** `PhantomReference.get()` dùng để lấy lại object cho cleanup được không?  
**Đáp án:** Không; `get()` luôn là `null`. Cleanup nên dùng resource state khác mà không giữ referent mạnh trở lại.

### Bài 8 — Static cache leak

**Hỏi:** Vì sao `static List<Object>` có thể giữ một triệu objects sống dù ứng dụng không xử lý chúng nữa?  
**Đáp án:** Khi class/static cache còn sống, list giữ strong refs tới elements; collector không biết hết giá trị nghiệp vụ. Cần eviction/remove và lifecycle hợp lý.

### Bài 9 — ThreadLocal

**Hỏi:** Sau mỗi request, worker thread trong pool vẫn tồn tại. Vì sao request context có thể chưa được GC?  
**Đáp án:** ThreadLocal values có thể còn reachable qua runtime thread/local map. Dùng `remove()` trong `finally` khi hết scope nghiệp vụ.

### Bài 10 — Young GC

**Hỏi:** Một object old-generation tham chiếu object young-generation. Young GC có thể coi young object unreachable chỉ vì root scan không đi qua toàn bộ old heap?  
**Đáp án:** Không. Collector phải xử lý các cross-generation references thích hợp, ví dụ qua remembered sets/card metadata.

### Bài 11 — Đồng thời thay đổi đồ thị

**Hỏi:** Trong concurrent marking, mutator thay references giữa lúc collector quét. Tại sao cần barriers?  
**Đáp án:** Để duy trì marking invariants và tránh bỏ sót objects cần sống khi graph thay đổi; cách dùng SATB/incremental-update tùy collector.

### Bài 12 — Heap size

**Hỏi:** Sau GC, 50 MB objects không còn reachable đã được reclaim nhưng JVM process RSS không giảm. Có chắc GC lỗi?  
**Đáp án:** Không. JVM có thể giữ committed heap/native pages để tái sử dụng; RSS và live-set không đồng nhất.

### Bài 13 — Identity hay equality?

**Hỏi:** Hai `Node` khác instances nhưng `equals()` trả `true`, collector có được coi là cùng một object?  
**Đáp án:** Không. GC phải quản lý **object identity**; phép so bằng application-defined `equals()` không biến hai allocations thành một.

### Bài 14 — Một root path thay đổi

**Hỏi:** Một listener được remove khỏi event bus nhưng vẫn còn trong scheduled task queue. Có eligible ngay không?  
**Đáp án:** Chưa đủ bằng chứng. Task queue có thể giữ strong path; cần xem tất cả root paths.

### Bài 15 — Finalizer và reachability

**Hỏi:** Có nên cài `finalize()` để bảo đảm native handle được đóng ngay khi object unreachable?  
**Đáp án:** Không. Finalization legacy không bảo đảm timing/hoàn tất và có thể gây resurrection; ưu tiên `AutoCloseable`/try-with-resources.

### Bài 16 — `reachabilityFence`

**Hỏi:** `Reference.reachabilityFence(obj)` có bắt GC dọn `obj` ngay sau lệnh không?  
**Đáp án:** Không. Nó giúp duy trì reachability tới điểm cần thiết; thời điểm reclaim vẫn phụ thuộc GC/runtime.

## 19. Checklist giải thích “vì sao object chưa bị GC”

1. Object có đang nằm trong static cache, collection, map hoặc backing array không?
2. Có Java thread còn giữ reference live hoặc `ThreadLocal` value không?
3. Có scheduled task, listener, callback hoặc future đang giữ closure/object không?
4. Có JNI global handle hoặc VM-internal handle liên quan không?
5. Có weak/soft/phantom reference nào khiến việc diễn giải paths cần đặc biệt không?
6. Có object nào khác trong graph còn strong edge tới nó không?
7. Có class loader/class metadata lifecycle khiến static state còn sống không?
8. Object chỉ **eligible** hay collector đã chạy cycle xử lý vùng đó?
9. Heap dump/histogram đang cho **shallow** hay **retained** size?
10. Công cụ đã tìm Paths to GC Roots, lọc weak/soft/phantom refs thích hợp chưa?
11. Collector đang dùng generational/concurrent scheme nào và logs cho thấy những pha nào?
12. Mục tiêu là sửa **unintended strong reachability** hay chỉ giảm committed heap/RSS?

## 20. Kết luận và tài liệu đối chiếu

Để hiểu vì sao Java GC giữ một object, hãy bắt đầu từ câu hỏi **“có đường tham chiếu giữ sống từ GC roots tới object này không?”**. Tracing collector đánh dấu object theo đồ thị thay vì dựa trên số references hoặc tuổi của biến Java. Chu trình không có root path có thể được thu hồi. Ngược lại, static caches, thread locals, JNI handles và listeners có thể vô tình tạo root paths lâu dài, khiến object vẫn sống mặc dù ứng dụng đã “xử lý xong”.

Đối với concurrent và generational collectors, mô hình cơ bản vẫn là reachability nhưng việc quét được tối ưu bằng **marking invariants, barriers, cards và remembered sets**. Với `SoftReference`, `WeakReference`, `PhantomReference` và cleanup, **cạnh tham chiếu không có cùng ngữ nghĩa** như strong field. “Không còn reachable” cũng khác “bộ nhớ đã được thu hồi ngay” và càng khác “RSS phải giảm tức thì”.

**Tài liệu chuẩn:**

- Java SE 17 — **`java.lang.ref` package**, mô tả các mức reachability: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html
- Java SE 17 — **`Reference`** và `Reference.reachabilityFence`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html
- Java SE 17 — **`WeakReference`**, **`SoftReference`**, **`PhantomReference`**, **`ReferenceQueue`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html
- OpenJDK **JEP 421 — Deprecate Finalization for Removal**: https://openjdk.org/jeps/421
- Oracle JDK 17 — **Garbage Collection Tuning Guide**: https://docs.oracle.com/en/java/javase/17/gctuning/
- Oracle JDK 17 — **jcmd utility**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html

**Ghi nhớ:** GC không cần “xóa tất cả reference trong mọi object” trước khi thu hồi một chu trình. Nó cần xác định **tập object phải được giữ sống bắt đầu từ GC roots**, xử lý các loại reference đặc biệt và dùng thuật toán collector phù hợp để thu hồi vùng bộ nhớ có thể tái sử dụng. Khi debug heap, **Paths to GC Roots** là chiếc cầu nối giữa lý thuyết đồ thị và nguyên nhân object còn sống trong ứng dụng thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-002">Đồ thị tham chiếu, đối tượng không còn truy cập được và tham chiếu vòng</a>

<details>
<summary>Xem chi tiết</summary>

# Đồ thị tham chiếu, đối tượng không còn truy cập được và tham chiếu vòng

## 1. Mục tiêu

Java tự động quản lý bộ nhớ của phần lớn đối tượng trên heap bằng **garbage collection (GC)**. Tuy vậy, muốn biết một đối tượng có thể được giải phóng không, ta phải xét **đường đi từ những gốc còn sống (GC roots)** đến đối tượng ấy, thay vì chỉ đếm bao nhiêu đối tượng đang trỏ tới nó.

Ba nhận định quan trọng:

1. **Có đường tham chiếu mạnh từ GC roots tới object:** object được coi là còn **strongly reachable** và không thể bị thu hồi như rác trong khi đường giữ sống đó còn hiệu lực.
2. **Không còn đường tham chiếu mạnh từ roots:** object có thể chỉ còn được truy cập qua soft/weak/phantom references hoặc đã unreachable; phải xét các quy tắc reference processing tương ứng.
3. **Hai object tham chiếu vòng nhau không tự tạo memory leak:** một cycle không còn được giữ từ roots có thể được tracing collector thu hồi, dù các object trong cycle vẫn trỏ tới nhau.

Sau bài học, bạn sẽ biết:

- Biểu diễn Java heap thành **đồ thị có hướng**: nodes là objects/arrays, edges là references, roots là tập điểm bắt đầu.
- Duyệt graph theo strong edges để xác định reachable và unreachable groups.
- Giải thích chính xác vì sao vòng **A → B → C → A** có thể hoặc không thể được GC thu hồi.
- Phân biệt **gán null**, **hết scope**, **eligible for GC**, **reference cleared** và **memory reclaimed**.
- Hiểu ý nghĩa của **strong**, **soft**, **weak**, **phantom references**, **ReferenceQueue** và **Reference.reachabilityFence** trong Java 17.
- Nhận diện **logical memory leaks**: graph vẫn reachable nhưng đã hết vòng đời nghiệp vụ.
- Phân tích **WeakHashMap**, static caches, listeners, ThreadLocal, thread pools, JNI references và class loaders bằng đường đi tới roots.
- Dùng Java 17+ để mô phỏng graph, quan sát weak references một cách không phụ thuộc GC timing, và dùng công cụ xem path to GC root/retained heap khi xử lý sự cố.

**Phạm vi:** bài giải thích ngữ nghĩa reachability và mô hình tracing GC. Chi tiết roots cụ thể, safepoints, marking barriers, thời điểm reclaim và class unloading có thể khác giữa các JVM/collectors.

## 2. Đồ thị tham chiếu của đối tượng

Định nghĩa mô hình:

- **Đỉnh (vertex/node):** một Java object hoặc array instance.
- **Cạnh (directed edge):** một tham chiếu từ object A tới object B, ví dụ A.child = B hoặc A.elements[0] = B.
- **GC root:** một tham chiếu điểm xuất phát được collector coi là live trong trạng thái hiện tại của VM.
- **Path:** chuỗi cạnh có hướng nối một root tới một object.
- **Strongly reachable:** object có thể được truy cập từ nguồn còn sống mà không cần đi qua referent của Reference object (SoftReference/WeakReference/PhantomReference).
- **Unreachable:** không còn reachable qua các cấp độ quy định của java.lang.ref; đủ điều kiện để memory được reclaim khi JVM quyết định.

Sơ đồ giả định tất cả cạnh thường là strong:

~~~text
 GC ROOTS
   local session             static Registry.sessions
        |                             |
        v                             v
   Session A                      Cache C
        |                             |
        v                             v
   Account B                      Entry D
        |                             |
        v                             v
   Profile E                      Buffer F

 Nhóm không có đường từ roots:
     X ------------> Y
     ^               |
     |               |
     +---------------+
~~~

Có đường mạnh từ roots tới **A,B,C,D,E,F**, vì vậy nhóm này còn reachable. **X và Y** trỏ vòng nhau nhưng không có root path, nên chúng không được giữ sống chỉ nhờ tham chiếu qua lại.

**Cảnh báo:** Sơ đồ chỉ minh họa. Nếu ngoài hình còn một live local, static field, JNI global reference hoặc đường từ object khác trỏ tới X/Y, kết luận về nhóm ấy phải đổi.

### 2.1. Tham chiếu là cạnh, không phải object được copy

~~~java
// Minh họa bên trong một method:
Object a = new Object();
Object b = a;
~~~

Đây là **một object**, hai variables cùng tham chiếu tới nó. Gán **a = null** xóa một cạnh từ biến a, nhưng **b** vẫn có thể giữ object nếu b còn live. Không phải lệnh gán null “hủy object”.

Trong heap, mỗi field reference và phần tử object array có thể tạo thêm cạnh:

~~~java
// Minh họa:
Node parent = new Node();
Node child = new Node();
parent.next = child;
~~~

Nếu parent reachable, child cũng reachable qua **parent.next** cho tới khi cạnh thay đổi hoặc parent không còn được giữ.

### 2.2. Đường có hướng: A → B không đồng nghĩa B → A

Nếu A.next = B, thì reachability từ A đến B có thể tồn tại, nhưng từ B về A **chưa chắc** tồn tại. Đối với GC graph, hướng cạnh quan trọng.

**Một cạnh weak** có ngữ nghĩa khác strong edge: bản thân WeakReference wrapper có thể strongly reachable từ root, nhưng điều đó không đồng nghĩa referent được giữ mạnh. Đừng vẽ root→WeakReference→referent như hai strong arrows rồi kết luận referent không được GC.

## 3. GC roots có thể đến từ đâu?

Ở HotSpot và nhiều VM thực tế, tập roots bao gồm các nhóm tham chiếu đang live như:

| Nguồn có thể là root | Ví dụ |
| --- | --- |
| Execution state của thread đang sống | Live local reference, operand/register values cần cho computation |
| Static state từ runtime classes còn live | Static cache, singleton, registry |
| JNI/native global references | Native code giữ Java object qua JNI GlobalRef |
| VM/internal runtime structures | Tham chiếu VM cần duy trì trong loading, synchronization và runtime subsystems |
| Các runtime-managed handles | Những handles được collector cụ thể tính đến |

**Không nên học thuộc một danh sách cố định cho mọi JVM.** Root discovery khác nhau giữa implementations/collectors; một số công cụ phân tích heap biểu diễn thêm synthetic roots để dựng đồ thị.

### 3.1. Local variable sống đến khi nào?

Một tên biến nằm trong cặp dấu ngoặc nhọn của Java source **không đảm bảo** object được giữ tới dấu ngoặc đóng. JIT/compiler có thể chứng minh một reference không còn cần thiết trước khi hết lexical scope, theo những ngữ nghĩa được phép.

Trong trường hợp cần giữ object strongly reachable đến một mốc liên quan cleanup/reference processing, Java 17 cung cấp:

~~~java
Reference.reachabilityFence(resource);
~~~

Lời gọi này giữ **strong reachability ít nhất tới sau invocation**, không tự gọi GC và không pin object tại một memory address nhất định.

### 3.2. Static state cũng có vòng đời

~~~java
class Registry {
    static final java.util.List<Object> ITEMS =
            new java.util.ArrayList<>();
}
~~~

Nếu runtime class Registry và static state của nó còn live, **ITEMS** có thể giữ mạnh các objects trong list. Nhưng **static** không phải một hợp đồng “vĩnh viễn tồn tại dù class loader đã có thể unload”; trong các plugin/application servers, class loader lifetime có vai trò quan trọng.

## 4. Marking: tìm tập reachable bằng duyệt graph

Một phép duyệt graph mang tính khái niệm:

~~~text
function markReachable(rootReferences):
    worklist = new Queue()
    visited  = new IdentitySet()

    for each rootReference:
        if rootReference != null:
            worklist.add(rootReference.target)

    while worklist not empty:
        object = worklist.remove()
        if object in visited:
            continue

        visited.add(object)
        for each strong reference field/array element of object:
            if referent != null:
                worklist.add(referent)

    return visited
~~~

Những objects trong visited là strong-reachable **theo snapshot và root set của mô hình**. Phần graph không được đánh dấu sẽ không được giữ mạnh từ các roots ấy.

Sử dụng **IdentitySet**, không dùng business equals(), bởi hai objects có thể bằng nhau về nội dung nhưng khác object identities; GC quyết định trên object identities.

### 4.1. Cycle không làm duyệt graph lặp vô hạn

Với:

~~~text
A ---> B ---> C
^             |
+-------------+
~~~

Khi gặp lại A, thuật toán kiểm tra **visited** và bỏ qua. Cycle không ngăn traversal kết thúc. Một tracing GC có thể đánh dấu và thu hồi nhóm không reachable từ roots mà không cần “tháo từng cạnh bên trong cycle”.

Duyệt BFS/DFS một graph có N nodes và E edges thường có độ phức tạp **O(N+E)** trong mô hình, với cấu trúc visited phù hợp. Đây là bài tập thuật toán, không phải khẳng định mọi collector HotSpot triển khai đúng queue và visited objects bằng Java collections.

## 5. Tham chiếu vòng: có root và không có root

Trường hợp có root:

~~~text
ROOT --> A --> B --> C
         ^           |
         +-----------+
~~~

Từ ROOT tới A, rồi B rồi C, tất cả strongly reachable. Lúc này cycle tồn tại và được giữ bởi **ROOT → A**, không phải do cycle tự duy trì khả năng truy cập từ JVM.

Trường hợp không root:

~~~text
      A --> B --> C
      ^           |
      +-----------+
  Không còn đường strong từ roots vào nhóm.
~~~

Nếu không có cơ chế khác giữ chúng, nhóm A/B/C đủ điều kiện để thu hồi bằng GC dựa trên reachability.

### 5.1. Ví dụ vòng hai node

~~~java
// Mã minh họa, Node có field Node next:
Node a = new Node();
Node b = new Node();
a.next = b;
b.next = a;

a = null;
b = null;
~~~

Nếu không còn edges khác từ roots tới a/b, cycle không tự ngăn GC. Nhưng nếu trước đó chương trình đã ghi **CACHE.put("cycle", a)** thì CACHE có thể giữ a và cả b; chỉ gán các locals thành null không xóa cache entry.

### 5.2. Reference counting thuần túy khác tracing GC

Reference counting cơ bản có thể giữ lại cycle vì mỗi object vẫn có incoming reference từ object còn lại, khiến count không về 0. Tracing GC quyết định sống/chết bằng root paths, nên cycle không rooted có thể được thu hồi.

So sánh này chỉ đúng với **reference counting thuần túy không có cycle collection**. Một số runtime sử dụng ref counting nhưng bổ sung cơ chế phát hiện cycle; không nên tuyệt đối hóa thành “mọi ref-counting system đều leak cycles”.

### 5.3. Strongly connected components (SCC)

Một nhóm nodes **strongly connected** nếu mỗi node có đường có hướng tới mọi node khác trong nhóm. SCC là công cụ hữu ích để suy luận về cycles. Tuy nhiên GC không bắt buộc phân tích SCC rồi mới reclaim; chỉ cần quan hệ reachability đúng theo collector semantics.

## 6. Ví dụ Java 17+ số 1 — Tự duyệt một object graph

Tạo file **ReachabilityGraphDemo.java**:

~~~java
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ReachabilityGraphDemo {
    static final class Node {
        final String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    static Set<Node> markFrom(Node... roots) {
        Set<Node> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Node> queue = new ArrayDeque<>();

        for (Node root : roots) {
            if (root != null) {
                queue.addLast(root);
            }
        }

        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            if (!visited.add(node)) {
                continue;
            }
            if (node.next != null) {
                queue.addLast(node.next);
            }
        }

        return visited;
    }

    static String sortedNames(Iterable<Node> nodes) {
        TreeSet<String> names = new TreeSet<>();
        for (Node node : nodes) {
            names.add(node.name);
        }
        return names.toString();
    }

    public static void main(String[] args) {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");
        Node d = new Node("D");
        Node e = new Node("E");

        a.next = b;
        b.next = c;
        c.next = a;
        d.next = e;
        e.next = d;

        Set<Node> reachable = markFrom(a);
        List<Node> all = Arrays.asList(a, b, c, d, e);
        Set<Node> notReached =
                Collections.newSetFromMap(new IdentityHashMap<>());
        notReached.addAll(all);
        notReached.removeAll(reachable);

        System.out.println("reachable=" + sortedNames(reachable));
        System.out.println("notReached=" + sortedNames(notReached));
        System.out.println("cycleA=" + (c.next == a));
        System.out.println("cycleD=" + (e.next == d));
    }
}
~~~

Biên dịch/chạy bằng Java 17+:

~~~bash
javac --release 17 -encoding UTF-8 ReachabilityGraphDemo.java
java ReachabilityGraphDemo
~~~

**Stdout chính xác:**

~~~text
reachable=[A, B, C]
notReached=[D, E]
cycleA=true
cycleD=true
~~~

Giải thích:

1. Root của **phép mô phỏng** là a.
2. Thuật toán đi a→b→c→a và dừng vì a đã visited.
3. d/e tạo một cycle riêng không có đường từ root a nên không được đánh dấu.
4. **Cả hai nhóm có cycles**, nhưng chỉ nhóm a/b/c reachable từ root lựa chọn.

**Giới hạn cực kỳ quan trọng:** Chương trình đang giữ references tới d và e trong locals và collection **all** để tính toán. Do đó **notReached=[D,E] là kết quả của mô hình duyệt**, không phải chứng cứ JVM GC coi d/e là unreachable thật! Muốn kiểm tra GC thật cần xét root set thật và reference processing, không dùng một biến Java **notReached** do mình dựng làm nguồn sự thật.

### 6.1. Vì sao không dùng HashSet thông thường?

Nếu Node override equals() dựa vào name, hai Node objects khác identity nhưng cùng name có thể bị HashSet nhập thành một. **IdentityHashMap** đảm bảo so sánh objects theo reference identity, phù hợp graph simulation cho heap.

## 7. Phân biệt bốn giai đoạn thường bị trộn

| Tình huống | Điều có thể kết luận |
| --- | --- |
| **Một local bị gán null** | Đã xóa một reference edge cụ thể; chưa biết object có còn root path khác không |
| **Không còn strong path từ roots** | Object có thể chỉ softly/weakly/phantom reachable hoặc unreachable hoàn toàn |
| **Eligible for collection** | JVM có thể thu hồi khi GC chạy/phù hợp; **không có deadline** |
| **Memory reclaimed** | Collector đã thực sự xử lý và thu hồi; không thể chỉ suy từ gán null |

### 7.1. System.gc() không bảo đảm thời điểm thu gom

~~~java
Object value = new Object();
value = null;
System.gc();
~~~

Không có contract đảm bảo collector đã reclaim đúng object sau lời gọi. Trong một số cấu hình, explicit GC có thể bị vô hiệu hóa/điều chỉnh; collector có thể không xử lý object theo đúng lịch thời gian của test.

**Đừng assert** WeakReference.get()==null ngay sau System.gc() rồi coi test là chuẩn Java.

### 7.2. Heap used, committed và RSS khác nhau

Nếu collector thu hồi nhiều objects, heap **used** có thể giảm, nhưng JVM vẫn giữ memory **committed/reserved** để cấp phát sau. RSS của process còn liên quan native memory, code cache, stacks, metaspace và page management. Không suy “một graph không còn reachable” thành “RSS giảm đúng số byte” vào cùng thời điểm.

## 8. Strong, soft, weak và phantom reachability

Java SE 17 cung cấp package **java.lang.ref**. Các reference objects cho phép chương trình quan sát một phần vòng đời của referent mà không nhất thiết giữ nó bằng một strong edge.

| Loại | Có giữ object như một reference field mạnh thông thường? | Dùng khi nào? |
| --- | --- | --- |
| **Strong reference** | Có | Ownership/reference thường |
| **SoftReference** | Không theo mức strong; collector có thể clear theo memory demand | Một số memory-sensitive caches |
| **WeakReference** | Không; GC có thể clear khi referent chỉ weakly reachable | Weak-key mappings, canonical registry |
| **PhantomReference** | Không dùng để đọc referent; get() luôn null | Lifecycle notification/cleanup bookkeeping |
| **ReferenceQueue** | Không phải một kiểu reachability của referent | Nhận các Reference objects sau khi collector enqueue |

Các mức trong tài liệu java.lang.ref đi theo chuỗi **strong → soft → weak → phantom → unreachable**. Việc object có một weak reference trỏ đến **không** ngăn nó bị reclaim mãi.

### 8.1. WeakReference không giữ referent sống

~~~java
// Minh họa trong một method:
Object o = new Object();
WeakReference<Object> weak = new WeakReference<>(o);
~~~

Bản thân **weak** là một Java object. Nếu một root trỏ mạnh đến wrapper **weak**, wrapper còn sống; nhưng **referent o** có thể bị clear/reclaimed khi không còn strong/soft paths phù hợp.

### 8.2. SoftReference không có TTL hoặc “sống tới khi hết RAM” contract

Soft reference được clear theo chính sách VM và điều kiện memory. Không được dùng như một cơ chế cache có eviction timing/size chính xác. Nếu cached value biến mất, code phải tạo lại được một cách an toàn.

### 8.3. PhantomReference.get() luôn trả null

Phantom references thường phối hợp **ReferenceQueue** để nhận notification, không phải phương thức lấy object đã “chết”. Dùng **Cleaner** hoặc PhantomReference khi thiết kế cleanup cần cơ chế hậu kiểm, nhưng không dựa vào GC để đóng socket/file/DB connection đúng hạn.

### 8.4. Finalization là giải pháp lịch sử không nên phụ thuộc

Java object finalization có các vấn đề về reliability, performance và security; trên JDK 17 nó đã bị deprecate. Không dùng **finalize()** để xây dựng logic giao dịch hoặc quản lý tài nguyên bắt buộc. **try-with-resources** và explicit close() phù hợp hơn cho tài nguyên có lifecycle rõ.

## 9. Ví dụ Java 17+ số 2 — WeakReference với stdout xác định

Tạo **WeakReferenceDemo.java**:

~~~java
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class WeakReferenceDemo {
    public static void main(String[] args) {
        Object strong = new Object();
        WeakReference<Object> weak = new WeakReference<>(strong);

        System.out.println("whileStrong=" + (weak.get() != null));
        Reference.reachabilityFence(strong);

        strong = null;
        System.out.println("afterDrop=timing-not-guaranteed");
        System.out.println("wrapperAlive=" + (weak != null));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 WeakReferenceDemo.java
java WeakReferenceDemo
~~~

**Stdout:**

~~~text
whileStrong=true
afterDrop=timing-not-guaranteed
wrapperAlive=true
~~~

- Khi in **whileStrong**, **strong** được bảo đảm giữ reachable tới sau **Reference.reachabilityFence(strong)** nên referent chưa bị reclaim tại bước đọc.
- Sau **strong=null**, chương trình cố ý không in weak.get() vì kết quả sau GC là bất định.
- **wrapperAlive=true** chỉ nói Java reference wrapper chưa null; không có nghĩa referent còn strong reachable.

Để quan sát GC, có thể thêm:

~~~java
// Đoạn thêm vào main, kết quả KHÔNG cố định:
System.gc();
System.out.println("stillPresent=" + (weak.get() != null));
~~~

**stillPresent** có thể true hoặc false tùy GC, references khác và timing. Không dùng output đó làm bài toán có một stdout chuẩn.

## 10. ReferenceQueue: nhận thông báo nhưng không điều khiển GC

Ví dụ minh họa (cần các imports của java.lang.ref):

~~~java
ReferenceQueue<Object> queue = new ReferenceQueue<>();
Object referent = new Object();
WeakReference<Object> watch = new WeakReference<>(referent, queue);
referent = null;
Reference<?> notice = queue.poll();
~~~

Không thể khẳng định **notice != null** ngay khi gán referent=null. Queue có thể nhận reference sau khi collector xác định thay đổi reachability tương ứng.

**Cần giữ chính Reference wrapper reachable** khi muốn nhận notification: việc đăng ký nó vào queue không có nghĩa queue luôn giữ nó sống từ lúc đăng ký. Nếu wrapper bị mất, ứng dụng có thể không thấy thông báo mà mình muốn xử lý.

### 10.1. Tránh tự ngăn referent bị GC

Một lớp kế thừa WeakReference có thêm field **strongReferent** trỏ lại referent sẽ tạo đường strong vô tình, làm referent không còn weakly reachable như mong muốn. Với cleanup metadata, lưu các thông tin độc lập như identifier/native handle thay vì giữ mạnh chính referent.

### 10.2. Dùng try-with-resources cho tài nguyên bên ngoài

~~~java
// Minh họa; path là java.nio.file.Path đã khai báo:
try (java.io.InputStream in =
             java.nio.file.Files.newInputStream(path)) {
    // Xử lý dữ liệu.
}
~~~

Đây không phải chương trình độc lập; cần biến path và xử lý IOException. **close()** được gọi khi rời try-with-resources theo ngữ nghĩa Java, không chờ GC.

## 11. WeakHashMap: weak key vẫn có thể bị value giữ mạnh

WeakHashMap sử dụng weak references cho **keys**, còn **values** thường được map giữ bằng strong references. Đây là bẫy rất phổ biến:

~~~text
GC Root ---> WeakHashMap
                |
                +-- weak key ------> Key K
                |
                +-- strong value --> Value V
                                       |
                                       +-- strong field --> Key K
~~~

Nếu Value tham chiếu mạnh ngược tới Key, đường **root → map → value → key** vẫn tồn tại, dù entry key bên trong map là weak. Key vì vậy có thể không bao giờ đủ điều kiện xóa theo ý định ban đầu.

### 11.1. Ví dụ Java 17+ số 3 — Key được giữ bởi value

Tạo file **WeakMapPitfallDemo.java**:

~~~java
import java.util.WeakHashMap;

public class WeakMapPitfallDemo {
    static final class Key {
        final int id;

        Key(int id) {
            this.id = id;
        }
    }

    static final class Value {
        final Key backReference;

        Value(Key key) {
            this.backReference = key;
        }
    }

    static final WeakHashMap<Key, Value> CACHE =
            new WeakHashMap<>();

    public static void main(String[] args) {
        Key key = new Key(7);
        Value value = new Value(key);
        CACHE.put(key, value);

        key = null;
        value = null;

        System.out.println("entries=" + CACHE.size());
        System.out.println("valueBackReference=true");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 WeakMapPitfallDemo.java
java WeakMapPitfallDemo
~~~

**Stdout:**

~~~text
entries=1
valueBackReference=true
~~~

**Giải thích:** CACHE là static collection được main sử dụng; nó mạnh tới Value, còn Value.backReference mạnh tới Key. Cả graph reachable dù hai locals được gán null. Dòng thứ hai mô tả đường back-reference trong cấu trúc class, không phụ thuộc việc GC có chạy.

Nếu Value không trỏ ngược Key, một key không còn strong path có thể được reclaim, nhưng thời điểm entry biến mất/size() đổi không được bảo đảm ngay sau local=null.

### 11.2. Dùng weak references đúng nhu cầu

WeakHashMap thích hợp khi map không muốn là owner sống lâu của keys. Tuy nhiên:

- Có thể tồn tại root paths khác tới keys.
- Values có thể giữ keys hoặc giữ objects tham chiếu keys.
- Clear/enqueue/expunge không nhất thiết đồng bộ với dòng code vừa set null.
- Nếu cần bounded cache/eviction chính xác, weak key một mình chưa đủ.

## 12. Logical memory leak trong Java

**Memory leak về mặt logic** thường không phải “GC không biết thu hồi một object unreachable”. Nó thường là một **object graph vẫn reachable** nhưng **đã hết thời hạn sử dụng theo thiết kế ứng dụng**.

Ví dụ:

~~~text
Static Registry (rooted)
      |
      v
Map<UserId, Session>
      |
      +--> Session1 --> Cart --> ProductGraph
      +--> Session2 --> Cart --> ProductGraph
      +--> Session3 --> Cart --> ProductGraph
      +--> ...
~~~

Nếu ứng dụng liên tục thêm sessions mà không remove/expire, các graphs vẫn reachable. GC **đúng khi không thu hồi** chúng. Phải sửa lifecycle collection thay vì gọi System.gc() nhiều hơn.

### 12.1. Các root paths điển hình tạo leak

| Nguồn | Đường giữ sống | Cách sửa |
| --- | --- | --- |
| **Static cache/map** | Root → static → map → values → graph | Max size, TTL, eviction, remove |
| **Event bus/listener registry** | Root → publisher → listener → captured object | Unsubscribe tại lifecycle stop |
| **ThreadLocal** | Live thread → ThreadLocalMap → value → graph | remove() trong finally |
| **Thread pool/executor** | Thread/queue → task → captured closure | Complete/cancel task, shutdown pool |
| **Class loader** | Root → TCCL/registry → loader → classes/metadata | Clear references, stop worker threads |
| **HTTP sessions** | Session manager → session → large graph | Expire/invalidate, giới hạn dữ liệu |
| **Object pool** | Root → pool → idle objects | Bound pool, release entries |
| **JNI GlobalRef** | VM/native root → object graph | Delete global reference theo lifecycle |

**ThreadLocal trong thread pool** cần chú ý: request kết thúc không đồng nghĩa pooled thread kết thúc. Nếu value không được remove, nó có thể giữ request graph qua nhiều lượt xử lý.

### 12.2. Event listener gây leak không phải do cycle

~~~text
Long-lived Publisher --> Listener --> View
                              ^         |
                              |         |
                              +---------+
~~~

Cycle Listener↔View không tự làm leak. Nhưng Publisher được root giữ và tham chiếu Listener, nên toàn bộ nhóm vẫn reachable. Unsubscribe Listener có thể cắt đường root, nếu không còn đường giữ khác.

### 12.3. ClassLoader và redeploy

Một web container nạp plugin bằng Loader-v1, sau đó redeploy Loader-v2. Nếu pooled thread còn **contextClassLoader = Loader-v1**, loader v1 và metadata/classes/objects liên quan có thể còn reachable. Class unloading sẽ không giải phóng chúng như mong muốn cho đến khi lifecycle/reference paths phù hợp được giải quyết.

Đó là memory retention qua **GC root path**, không phải việc “GC không xử lý reference cycle”.

## 13. Shallow size, retained size và dominators

Các heap analyzers thường dùng:

- **Shallow size:** số byte của chính object, không cộng tất cả descendants.
- **Retained size:** kích thước graph chỉ còn reachable nhờ object/dominator được xét, theo mô hình công cụ.
- **Dominator:** node D thống trị X nếu mọi đường từ super-root tới X đều đi qua D.
- **Path to GC Root:** một hoặc nhiều đường chỉ ra lý do object vẫn reachable.
- **Incoming references:** những objects/roots đang trỏ đến object bị nghi ngờ.

Ví dụ:

~~~text
Root --> Cache --> Entry --> byte[10_000_000]
~~~

Cache có thể shallow size nhỏ nhưng retained size rất lớn nếu không có root path khác tới byte array. Ngược lại:

~~~text
Root1 --> CacheA --> SharedObject
Root2 --> CacheB --> SharedObject
~~~

Xóa CacheA chưa chắc khiến SharedObject eligible; Root2→CacheB vẫn giữ. Một **shortest path to GC Root** hữu ích nhưng không nhất thiết là đường duy nhất. Không suy leak root cause từ một path duy nhất khi object có nhiều owners hợp lệ.

### 13.1. Tại sao nên xem dominator tree?

Thay vì chỉ sắp xếp object classes theo số instance, dominator tree chỉ ra object/collection nào đang giữ độc quyền nhiều memory nhất. Ví dụ một ArrayList shallow nhỏ nhưng quản lý hàng trăm nghìn listeners với capture graphs lớn mới là điểm cần điều tra.

Đừng cộng hai retained sizes một cách tùy tiện vì graphs có thể trùng hoặc có quan hệ chi phối khác nhau.

## 14. GC timing và concurrent collectors

HotSpot có thể dùng các collectors khác nhau: Serial, Parallel, G1, ZGC và các biến thể theo JDK. Một số tiến hành marking concurrently, dùng barriers và nhiều chu kỳ GC.

Điều bất biến ở mức ngữ nghĩa:

- Java code không có deadline rằng object unreachable sẽ reclaimed trong X mili-giây.
- JVM phải duy trì memory safety và reference semantics khi objects/fields được cập nhật.
- Object graph thay đổi theo hoạt động của threads; sơ đồ trên giấy là snapshot lý tưởng.
- GC có thể di chuyển objects và cập nhật references; reference identity/equals semantics của Java vẫn được giữ.
- Một object không còn cần ở tầng business nhưng vẫn strongly reachable thì collector không được tự ý bỏ nó như garbage chỉ để giảm heap.

### 14.1. GC là cơ chế bộ nhớ, không phải transaction cleanup

Nếu bạn cần đóng file, socket, cursor hoặc DB transaction theo thời hạn, dùng explicit close/try-with-resources. GC/ReferenceQueue/Cleaner được dùng cho những trường hợp cleanup bổ trợ có chấp nhận tính bất định, không phải cơ chế commit/rollback.

## 15. Dùng công cụ để xem đường tới GC roots

### 15.1. Quan sát trước khi thu thập heap dump

Hỏi:

1. Có loại object nào tăng sau mỗi request/task dù lifecycle đã kết thúc?
2. Cache size/listener count/thread count có tăng mà không đạt trạng thái ổn định?
3. Object count và live heap qua các mốc đo có tăng liên tục hay chỉ tăng do warm-up?
4. Có plugin class loaders cũ còn tồn tại sau redeploy?
5. GC log cho thấy heap usage khác sau collection so với trước không?

Một memory snapshot duy nhất thường **không đủ** chứng minh leak. Cần trend theo workload và hiểu ownership.

### 15.2. jcmd và GC logs của HotSpot Java 17

Ví dụ (lệnh thay **<PID>** bằng process ID thật):

~~~bash
java -Xlog:gc*=info YourMainClass
jcmd
jcmd <PID> help
jcmd <PID> GC.heap_info
jcmd <PID> GC.class_histogram
~~~

Các commands và chi tiết output phụ thuộc JDK/JVM, quyền attach và cấu hình. **YourMainClass** là placeholder cho main class thực. Với hệ production, cân nhắc chi phí chẩn đoán và chính sách dữ liệu.

### 15.3. Heap dump

Trong môi trường được phép, HotSpot có thể hỗ trợ:

~~~bash
jcmd <PID> GC.heap_dump /path/to/heap.hprof
~~~

Heap dump có thể rất lớn và chứa dữ liệu cá nhân/bí mật của ứng dụng. Chụp dump có thể tác động latency hoặc gây GC theo options. Không thực hiện trên production mà chưa xem chi phí, quyền và hướng dẫn vận hành phù hợp.

Một số công cụ phân tích:

- **Eclipse Memory Analyzer (MAT):** dominator tree, retained heap, paths to GC roots.
- **VisualVM:** heap dump và heap sampling.
- **Java Flight Recorder (JFR):** allocation/runtime profiling tùy event configuration.
- **jcmd GC.class_histogram:** phân bố instances tại một thời điểm.

Các công cụ có thể biểu diễn soft/weak/phantom reference edges khác strong paths. Khi tìm leak, chọn bộ lọc đúng và không vô tình coi weak referent như đường mạnh giữ object.

## 16. Quy trình debug memory leak theo đồ thị

**Bước 1.** Reproduce với input/workload tương đương; ghi số sessions/tasks/objects được tạo và hoàn thành.

**Bước 2.** Thu metrics và snapshot tại các mốc có thể so sánh được (sau warm-up, sau cleanup), tránh so một peak với một idle snapshot.

**Bước 3.** Xác định classes/arrays tăng số lượng và nơi allocation.

**Bước 4.** Xem dominators có retained size cao; đừng chỉ nhìn shallow size.

**Bước 5.** Mở **path to GC root** cho một object hết vòng đời. Xác định static cache, live thread, ThreadLocal, listener, JNI hay class loader nào giữ.

**Bước 6.** Nếu thấy cycle, hỏi “root path vào cycle ở đâu?”. Cycle không phải nguyên nhân tự thân.

**Bước 7.** Sửa đúng **ownership/lifetime**: remove/evict/cancel/unsubscribe/close, cleanup trong finally hoặc scope phù hợp.

**Bước 8.** Đo lại dưới cùng workload. Xác minh object counts/retained graph trở về baseline hoặc ổn định.

**Bước 9.** Kiểm tra concurrent access và race conditions của caches/registries.

**Bước 10.** Thiết lập giới hạn và monitoring dài hạn thay vì phụ thuộc thao tác GC thủ công.

## 17. Các sai lầm phổ biến

1. **“Java thu gom rác khi reference count về 0.”** Không đúng như một quy tắc chung của tracing GC.
2. **“A ↔ B luôn memory leak.”** Sai nếu cycle không còn root path.
3. **“Xóa một local bằng null bảo đảm object được reclaim.”** Sai nếu còn references khác hoặc GC chưa chạy.
4. **“System.gc() chắc chắn dọn mọi object chết.”** Sai về timing/guarantee.
5. **“Strongly reachable chỉ cần object có một incoming edge bất kỳ.”** Sai: cần đường giữ từ roots.
6. **“WeakReference wrapper còn sống tức referent còn sống.”** Sai: wrapper và referent là hai objects.
7. **“WeakReference.get() lập tức null sau value=null.”** Sai: reference processing không đồng bộ với gán null.
8. **“SoftReference là cache TTL cố định.”** Sai: collector policy quyết định clearing.
9. **“PhantomReference.get() trả object khi sắp GC.”** Sai: get luôn null.
10. **“WeakHashMap value không thể giữ key.”** Sai: value→key có thể tạo strong path.
11. **“Có GC nên Java không leak.”** Sai: graph còn reachable nhưng không còn cần là leak logic.
12. **“Mọi local trong source scope luôn là root đến cuối block.”** Sai: JIT liveness có thể ngắn hơn.
13. **“Static field chắc chắn là root tồn tại tới hết process.”** Không tuyệt đối; class-loader lifecycle có ảnh hưởng.
14. **“Một heap dump có nhiều objects đã chứng minh leak.”** Sai: cần trend và lý do ownership.
15. **“Shallow size và retained size giống nhau.”** Sai: retained graph có thể rất lớn.
16. **“Dominator chỉ là object có nhiều incoming references nhất.”** Sai: mọi đường từ super-root tới node mới là tiêu chí.
17. **“Cycles xuất hiện trong heap dump nghĩa là GC không xử lý.”** Sai: snapshot có thể chứa objects còn reachable hoặc chưa reclaimed theo lịch.
18. **“Class loader mới tự giải phóng loader cũ.”** Sai nếu old loader còn reachable.
19. **“ReferenceQueue.poll() khác null ngay sau System.gc().”** Không được bảo đảm.
20. **“Cleanup native resource phải đợi finalizer/Cleaner.”** Sai: ưu tiên explicit lifecycle.

## 18. Bài tập tự kiểm tra có lời giải

**Câu 1.** ROOT→A→B→A. Nếu xóa ROOT→A và không còn root path khác, A/B có eligible for collection không?

**Đáp án:** Có theo mô hình reachability. Cycle A/B tự nó không tạo root path.

**Câu 2.** ROOT→A→B→C→A. Có bao nhiêu node strongly reachable từ ROOT?

**Đáp án:** A, B, C (ba nodes).

**Câu 3.** Object X được tham chiếu bởi hai locals p và q. p=null. X có trở thành unreachable ngay không?

**Đáp án:** Không nếu q vẫn live; còn phải xét static/other paths.

**Câu 4.** Graph A→B, B→C, C→B; D→E, E→D. Root model là A. Reachable?

**Đáp án:** A,B,C; D,E không có path từ root model, mặc dù cả hai nhóm có cycles.

**Câu 5.** Vì sao tracing GC thu hồi cycle unreachable mà basic reference counting không tự thu hồi?

**Đáp án:** Tracing dựa vào root reachability; basic ref count không về 0 do incoming edges nội bộ cycle.

**Câu 6.** Trong mã x=null; System.gc(); weak.get()==null có phải luôn true không?

**Đáp án:** Không. GC có thể không chạy/thực hiện kịp, referent có thể còn references khác.

**Câu 7.** Static WeakHashMap<Key, Value> có Value.backReference tới Key. Gán local key=null có chắc key collect được không?

**Đáp án:** Không. Static map giữ value mạnh, value giữ key mạnh.

**Câu 8.** Live pooled thread giữ ThreadLocal value là request data. Request xong rồi nhưng value chưa remove. Vì sao graph còn reachable?

**Đáp án:** Thread→ThreadLocalMap→value vẫn là strong path, có thể giữ cả graph.

**Câu 9.** Root1→A→X, Root2→B→X. Xóa root1→A có làm X unreachable không?

**Đáp án:** Không; root2 vẫn giữ X.

**Câu 10.** PhantomReference.get() trả referent lúc nào?

**Đáp án:** Không bao giờ trả referent; luôn null.

**Câu 11.** Một object shallow 32 bytes nhưng giữ độc quyền 100 MB arrays. Retained size có thể lớn không?

**Đáp án:** Có; retained size phản ánh graph bị giữ độc quyền qua object/dominator.

**Câu 12.** Heap histogram cho thấy 500 session objects sau load test. Có đủ kết luận leak?

**Đáp án:** Chưa; cần biết lifecycle, pending sessions, GC trạng thái, trend và paths to root.

**Câu 13.** Khi thấy cycle trong DOM tree và màn hình đã đóng, nên hỏi câu gì?

**Đáp án:** Có root path nào như listener/static cache/thread còn giữ tree hay không? Cycle tự thân không đủ kết luận leak.

**Câu 14.** Java GC tự giải phóng file descriptor đúng lúc rời method không?

**Đáp án:** Không được bảo đảm; dùng try-with-resources hoặc explicit close.

**Câu 15.** Tại sao mô phỏng graph bằng List all chứa toàn bộ nodes không chứng minh nodes “notReached” là GC-unreachable?

**Đáp án:** Chính List all và locals đang giữ strong references tới nodes; root set của thuật toán chỉ là đầu vào giả định, không phải roots thật của VM.

## 19. Checklist ghi nhớ

- Reachability là **đường đi từ GC roots**, không phải reference count.
- Duyệt graph theo **strong edges**, có **visited** để xử lý cycles.
- Một cycle không rooted **có thể** thu hồi; không có deadline bắt buộc.
- Gán null chỉ cắt **một cạnh**, không xóa object theo lệnh.
- **Eligible** khác **reclaimed**, và System.gc không quyết định được thời điểm.
- Weak/soft/phantom references có ngữ nghĩa khác strong; queue notification bất định.
- WeakHashMap vẫn có thể leak nếu value tham chiếu key.
- Java memory leaks thường là **graph còn reachable quá lâu** qua cache/listener/ThreadLocal/thread/class loader.
- Đọc heap dump bằng **paths to GC roots**, **dominator tree**, **retained sizes** và các mốc đo tương đương.
- Giữ tài nguyên external qua **explicit close**, không dựa vào GC.
- Khi mô phỏng, dùng **object identity** thay cho equals business.
- Xem tài liệu JLS/java.lang.ref để phân biệt ngữ nghĩa chuẩn và implementation collector.

### Tài liệu tham khảo chuẩn

- **Java SE 17 — java.lang.ref package (Reachability):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html
- **Java SE 17 — Reference và reachabilityFence:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html
- **Java SE 17 — WeakReference:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html
- **Java SE 17 — PhantomReference:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/PhantomReference.html
- **Java SE 17 — ReferenceQueue:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/ReferenceQueue.html
- **Java SE 17 — WeakHashMap:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html
- **JLS SE 17, §12.6 — Finalization và reachability:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.6
- **JVMS SE 17, §2.5.3 — Heap:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3
- **Oracle Java SE 17 Garbage Collection Tuning — Other Considerations:** https://docs.oracle.com/en/java/javase/17/gctuning/other-considerations.html

**Kết luận:** Một đối tượng trong Java không bị giữ sống chỉ vì còn được một đối tượng khác trong cùng cycle trỏ tới. **Đường tham chiếu mạnh từ GC roots** mới là yếu tố quyết định trong mô hình tracing. Một nhóm cycle không root có thể được thu gom, còn một object graph mà ứng dụng không cần nữa nhưng vẫn reachable qua cache/listener/thread có thể gây memory leak. Hãy tìm **GC root paths và ranh giới vòng đời**, không cố phá mọi cycle hoặc lặp System.gc().


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-003">Vì sao gán null hoặc ra khỏi phạm vi biến không bảo đảm thu gom ngay</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao gán null hoặc ra khỏi phạm vi biến không bảo đảm thu gom ngay?

## 1. Ý chính cần nắm

Trong Java, bạn có thể viết:

~~~java
Object object = new Object();
object = null;
~~~

Hoặc để một biến cục bộ ra khỏi block:

~~~java
{
    Object object = new Object();
}
// object không còn nằm trong phạm vi tên biến ở đây
~~~

Nhưng **không thao tác nào bảo đảm object vừa tạo sẽ được GC thu hồi ngay tại thời điểm đó**. Lý do gồm ba lớp khác nhau:

1. **Một biến không phải một object.** Gán `null` chỉ đổi giá trị của **một reference variable**; object có thể còn được tham chiếu từ nơi khác.
2. **Hết lexical scope không phải “sự kiện thu hồi”.** Java compiler/JIT và garbage collector phân tích **khả năng truy cập (reachability)**, không thực hiện lệnh `free` khi gặp dấu `}`.
3. **Ngay cả khi object không còn reachable, GC vẫn không bị buộc thu hồi tại một thời điểm cụ thể.** Bộ quản lý bộ nhớ tự động quyết định khi nào làm việc; trong nhiều trường hợp, chương trình có thể kết thúc trước khi object được thu gom.

Phân biệt cẩn thận:

~~~text
Gán null cho một biến / biến ra khỏi scope
                 |
                 v
Một đường tham chiếu có thể không còn sử dụng được
                 |
                 v
Có còn đường truy cập khác từ các live roots không?
        +-----------------------+
        |                       |
       CÓ                      KHÔNG
        |                       |
        v                       v
Object vẫn có thể        Object có thể trở thành
strongly reachable       không còn strongly reachable
        |                       |
        v                       v
Không đủ điều kiện        Có thể đủ điều kiện cho
thu gom theo cách         các bước xử lý reference/GC
thông thường                     |
                                 v
                         GC có thực sự chạy,
                         xử lý và thu hồi chưa?
                                 |
                        KHÔNG CÓ LỊCH CỐ ĐỊNH
~~~

Từ **“có thể đủ điều kiện” (eligible for reclamation)** tới **“đã được thu gom” (collected/reclaimed)** là một khoảng cách quan trọng. **Điều kiện được thu hồi không đồng nghĩa thao tác thu hồi đã diễn ra.**

## 2. Biến tham chiếu không phải bản thân object

Xét đoạn Java:

~~~java
final class Box {
    final int id;

    Box(int id) {
        this.id = id;
    }
}
~~~

Đây là class minh họa, chưa có `main`. Một object được tạo với:

~~~java
Box first = new Box(7);
Box second = first;
first = null;
~~~

Có thể vẽ:

~~~text
Trước khi first = null:

first  ----+
           |
           v
        Box(id=7)
           ^
           |
second ----+

Sau khi first = null:

first  ----> null

        Box(id=7)
           ^
           |
second ----+
~~~

**Object không bị xóa** chỉ vì `first` bị gán `null`. `second` vẫn giữ reference tới chính object đó, nên chương trình có thể đọc `second.id`.

Về mặt loại dữ liệu:

- `first` và `second` là các **reference variables** trong chương trình.
- `new Box(7)` tạo object theo ngữ nghĩa Java.
- Cả hai có thể mang **cùng reference value**, nên `first == second` đã từng là `true` trước khi `first` bị gán null.
- Gán `first = null` **không biến object thành null**, cũng không làm `second` tự đổi thành null.

**Lưu ý JIT:** nếu compiler thấy reference `second` không còn được sử dụng trong tương lai, liveness thực tế có thể sớm khác hình vẽ source. Nhưng khi chương trình còn thao tác quan sát được qua `second`, object phải tồn tại đúng theo ngữ nghĩa Java. Đừng dùng hình vẽ alias để dự đoán chính xác thời điểm GC sẽ chạy.

## 3. Ví dụ Java 17+: null một alias không xóa object

Tạo file **`AliasReachability.java`**:

~~~java
public class AliasReachability {
    static final class Box {
        final int id;

        Box(int id) {
            this.id = id;
        }
    }

    public static void main(String[] args) {
        Box first = new Box(7);
        Box second = first;

        System.out.println("same-before-null=" + (first == second));

        first = null;

        System.out.println("first-is-null=" + (first == null));
        System.out.println("still-readable=" + second.id);

        second = null;
        System.out.println("references-cleared");
        System.out.println("collection-is-not-guaranteed");
    }
}
~~~

Chạy bằng **JDK 17+**:

~~~bash
javac -g AliasReachability.java
java AliasReachability
~~~

**Stdout chính xác:**

~~~text
same-before-null=true
first-is-null=true
still-readable=7
references-cleared
collection-is-not-guaranteed
~~~

Điều mà chương trình **chứng minh**: sau khi `first` thành null, alias `second` vẫn truy cập `Box.id`. Hai dòng cuối là **thông điệp cố định của chương trình**, **không phải tín hiệu GC xác nhận đã hoặc chưa thu gom**.

Điều chương trình **không chứng minh**: ngay sau dòng `second = null`, garbage collector đã xử lý object. Không có phép đo GC trong ví dụ, và đặc tả không cho phép biến giả thiết “có lẽ không còn reference” thành kết luận “đã giải phóng heap”.

### Nếu một object có nhiều đường tham chiếu khác

Một reference khác có thể nằm trong:

- Trường của một object khác đang còn reachable.
- `static` field của runtime class còn được giữ theo các quy tắc reachability.
- Phần tử của array hoặc `List`/`Map`/cache.
- Biến cục bộ/cấu trúc runtime mà các thread đang thực thi còn sử dụng.
- Giá trị được closure/lambda hoặc callback giữ lại.
- Các JNI references/native-runtime handles hoặc hệ thống quản lý references khác.

Các mục trên là **những nguồn/thành phần đường truy cập thường gặp**, không phải danh sách GC roots cố định áp dụng y hệt mọi collector/JVM. Khi điều tra leak phải tìm **đường từ GC roots tới object**, thay vì chỉ thấy một biến cục bộ đã nhận `null`.

## 4. Reachability là thuộc tính của object graph, không phải số lần gọi null

Một mô hình học tập phổ biến là **object graph**:

- **Nodes**: runtime objects.
- **Edges**: references từ object tới object; cũng có references từ roots/runtime state.
- **Roots**: những điểm bắt đầu mà VM coi là sống khi xác định object nào phải giữ, tùy runtime và GC.
- **Reachable**: có đường truy cập phù hợp từ phần computation của những threads còn sống tới object.

~~~text
Sơ đồ mô hình root/heap:

GC root: active state của thread
   |
   v
CustomerService
   |
   +--> Repository ---------> Cache Map
   |                             |
   |                             +--> Customer #42
   |
   +--> Listener ---------> Customer #99

Local "temp" -------> Customer #42

temp = null:
  đường temp -> Customer #42 mất đi,
  nhưng Cache Map -> Customer #42 vẫn còn.
~~~

Object `Customer #42` **không được coi là unreachable chỉ vì reference `temp` không còn**, bởi vì cache map vẫn có thể giữ nó thông qua đường khác.

### Strong reachability và các mức khác

Tài liệu `java.lang.ref` của JDK định nghĩa những cấp độ reachability, từ mạnh đến yếu:

| Trạng thái khái niệm | Ý nghĩa |
| --- | --- |
| **Strongly reachable** | Có thể truy cập mà không phải đi qua referent của đối tượng `Reference` đặc biệt |
| **Softly reachable** | Không strongly reachable, nhưng có thể đi qua `SoftReference` |
| **Weakly reachable** | Không strongly/softly reachable, nhưng có thể đi qua `WeakReference` |
| **Phantom reachable** | Trường hợp đặc biệt dành cho `PhantomReference` và post-mortem cleanup theo API semantics |
| **Unreachable** | Không reachable qua các đường được hệ thống reference processing nhận diện; là đối tượng có thể được reclamation theo quy tắc liên quan |

**Đừng đồng nhất “không còn strong reference” với “memory đã được giải phóng”.** Khi dùng `SoftReference`/`WeakReference`/`PhantomReference`, garbage collector có thể thực hiện xử lý reference theo các quy tắc API; kết quả clearing/enqueueing và reclamation vẫn không có thời điểm do ứng dụng tùy ý đặt ra.

Một điểm tinh tế: **`PhantomReference.get()` luôn trả null**, dù referent chưa bị GC xử lý đến mức thông báo; nó không phải cách kiểm tra “object có còn sống không” bằng một phép `get()` như WeakReference.

## 5. Lexical scope: đóng dấu ngoặc không phải chạy garbage collector

Java có phạm vi tên biến theo cú pháp. Ví dụ:

~~~java
static void example() {
    {
        byte[] buffer = new byte[1024];
        System.out.println("bytes=" + buffer.length);
    }

    System.out.println("after-block");
}
~~~

Đây là **method minh họa phải nằm trong một class** để chạy. Sau block, tên `buffer` **không còn được source code truy cập hợp lệ**. Nhưng JVM không có opcode “closing brace; free the array now” tương ứng.

### Ba loại “lifetime” cần tách riêng

| Khái niệm | Bản chất | Có bảo đảm GC ngay không? |
| --- | --- | --- |
| **Lexical scope** | Phạm vi mà tên biến được dùng hợp lệ theo JLS | **Không** |
| **Runtime/JIT liveness** | VM/compiler đánh giá reference còn cần cho các phép tính tương lai hay không | **Không** |
| **Object reachability + GC decision** | Object còn đủ đường truy cập theo quy tắc hay không, rồi GC chọn lúc xử lý/reclaim | **Không** |

Lexical scope có thể kết thúc trước khi method return. Ngược lại, một biến có tên vẫn nằm trong scope nhưng **không còn được dùng tiếp**, nên compiler/JIT có thể coi giá trị reference là dead sớm hơn bạn tưởng.

### Method đã return thì object có chắc được GC?

Nếu object chỉ từng được tham chiếu bởi locals của frame đã kết thúc, các references ấy không còn là đường truy cập từ frame cũ. Nhưng vẫn có hai khả năng:

1. Object đã escape ra field/array/return value/callback/thread khác, nên còn reachable.
2. Object thực sự không còn reachable, nhưng **GC có thể chưa thực hiện reclamation**.

Vì vậy “method return rồi” **không đủ** để kết luận memory ngay lập tức được thu hồi. Thời điểm GC và quản lý heap thuộc runtime.

### `LocalVariableTable` không phải bản đồ cuối cùng của GC

Trong class file, debug `LocalVariableTable` có thể mô tả tên `buffer`, local slot, `Start` và `Length` tính theo bytecode indexes. Nó phục vụ debugger, **không phải lệnh bắt GC thu gom**.

- Compiler có thể tái sử dụng cùng một local slot cho nhiều biến không chồng phạm vi.
- JIT có thể giữ references trong registers, loại bỏ biến trung gian hoặc tối ưu code.
- GC sử dụng thông tin liveness/roots theo runtime state, không lấy dấu `}` làm tín hiệu “object chắc chắn đã chết và có thể thu ngay”.

Dùng `javap -c -l -p ClassName` để **quan sát bytecode và debug ranges**, nhưng **không suy trực tiếp từ output `-l` ra thời điểm thu gom thực tế**.

## 6. Liveness có thể kết thúc sớm hơn lexical scope

Ví dụ:

~~~java
static void process() {
    byte[] data = new byte[1024];
    int length = data.length;

    // Sau đây không sử dụng lại data.
    performOtherWork();

    System.out.println(length);
}
~~~

Đây là **minh họa**; `performOtherWork` cần được khai báo trong class chứa method. Bạn có thể nhìn source và nghĩ `data` còn sống tới hết method. Tuy nhiên nếu compiler/JIT chứng minh object không cần cho bất kỳ hành vi quan sát tiếp theo, reachability có thể chấm dứt sớm hơn “cuối method”. Đặc tả JLS `12.6.1` thừa nhận optimizing transformations có thể khiến tập reachable objects nhỏ hơn cách đọc ngây thơ từ source.

**Hướng ngược lại:** nếu `performOtherWork()` ghi `data` vào field của object khác hoặc tạo một asynchronous task giữ reference, object có thể **sống lâu hơn method**. Scope và reachability liên quan nhưng không đồng nhất.

### Escape analysis và scalar replacement

HotSpot JIT có thể nhận thấy một object không cần materialize thành một heap allocation riêng và tối ưu:

~~~java
static int sum() {
    Pair pair = new Pair(2, 3);
    return pair.left + pair.right;
}
~~~

`Pair` ở đây là **class minh họa** phải được định nghĩa trong chương trình. JIT có thể scalar-replace object thành các giá trị fields, **không cần tạo object heap vật lý** ở optimized code. Khi đó hỏi “object được GC lúc nào?” có thể không có ý nghĩa đối với một allocation đã bị tối ưu loại bỏ.

**Không khẳng định** mọi `new` đều bị loại bỏ hoặc HotSpot chuyển tất cả non-escaping objects sang stack. Đây là khả năng tối ưu, phụ thuộc code, profile, flags và compiler tier.

## 7. Eligible for GC khác GC đã chạy và khác memory đã được tái sử dụng

Ngay cả khi object không còn các đường truy cập khiến nó phải được giữ, các sự kiện sau là riêng biệt:

~~~text
T0: Ứng dụng không còn cần object
T1: Object thực tế không còn reachable theo trạng thái runtime
T2: GC thực hiện reachability/reference processing phù hợp
T3: GC reclaim/đánh dấu memory tái sử dụng, có thể compact/di chuyển
T4: Runtime cấp phát object khác và tái sử dụng vùng memory tương ứng

Không có điều kiện bắt buộc T1 == T2 == T3 == T4.
Không có deadline do source Java đặt ra từ câu lệnh = null.
~~~

Đây là sơ đồ logic. Trong garbage collectors khác nhau, các bước có thể diễn ra đồng thời/chồng lấp hoặc được tổ chức khác, không nhất thiết có đúng bốn pha vật lý tuần tự.

### Vì sao collector không thu ngay từng object?

GC là trade-off giữa throughput, latency, memory usage và overhead. Những thực tế thường gặp:

- Khi heap còn đủ dung lượng, thu gom ngay một object nhỏ có thể **không mang lại lợi ích đủ lớn**.
- GC thường thực hiện công việc theo vùng/chu kỳ, có thể concurrent hoặc stop-the-world tùy collector và giai đoạn.
- Cần xem xét những objects đang được threads sử dụng, reference graph và các yêu cầu memory barriers.
- Việc memory trở nên reusable **trong heap** không có nghĩa hệ điều hành lập tức thấy RSS/process memory giảm.
- GC có thể giữ heap committed/regions để cấp phát sau, thay vì trả ngay mọi trang memory cho OS.

Do đó “memory usage không giảm khi vừa `obj = null`” **không đủ** chứng minh Java memory leak. Cần quan sát nhiều chu kỳ GC, live-set và reference retention.

## 8. `System.gc()` không phải lời hứa thu hồi một object

Java có:

~~~java
System.gc();
Runtime.getRuntime().gc();
~~~

Đây là **các yêu cầu gợi ý VM nên dành nỗ lực để thu hồi objects không còn sử dụng**, không phải những thao tác với hậu điều kiện “mọi object unreachable lập tức biến mất”.

API `Runtime.gc()` của JDK 25 nói rõ VM **không bảo đảm** thu hồi số lượng object, dung lượng memory hoặc hoàn tất tại một thời điểm cụ thể, kể cả việc clearing/enqueueing reference objects. Ngoài ra, runtime có thể chịu ảnh hưởng của VM settings (chẳng hạn HotSpot `-XX:+DisableExplicitGC`).

**Không dùng** `System.gc()` trong vòng lặp để “bảo đảm giải phóng memory đúng từng bước” hoặc để sửa memory leak. Nó có thể làm xấu latency/throughput và không giải quyết việc các strong references vẫn giữ object reachable.

Đừng kiểm tra hành vi GC bằng:

~~~java
object = null;
System.gc();
// khẳng định: object đã bị thu gom
~~~

Kết luận cuối **không được bảo đảm**. Nếu object còn một reference khác thì vẫn không đủ điều kiện tương ứng; nếu đã unreachable thì GC vẫn không bị buộc thực hiện theo giả định thời gian của bạn.

## 9. Java 17+ runnable: ra khỏi scope không sinh “sự kiện GC”

Tạo file **`ScopeDemo.java`**:

~~~java
public class ScopeDemo {
    static final class ResourceHolder {
        final String name;

        ResourceHolder(String name) {
            this.name = name;
        }
    }

    static void work() {
        {
            ResourceHolder scoped = new ResourceHolder("temporary");
            System.out.println("inside=" + scoped.name);
        }
        System.out.println("scope-ended");
    }

    public static void main(String[] args) {
        work();
        System.out.println("method-returned");
        System.out.println("collection-timing-unspecified");
    }
}
~~~

Chạy:

~~~bash
javac -g ScopeDemo.java
java ScopeDemo
~~~

**Stdout chính xác:**

~~~text
inside=temporary
scope-ended
method-returned
collection-timing-unspecified
~~~

**Ý nghĩa:** tên `scoped` không thể được truy cập sau dấu `}` của block, `work()` rồi hoàn tất, và `main` tiếp tục. Dòng `collection-timing-unspecified` do programmer **in chủ động** để nhấn mạnh không có sự kiện GC được đo trong chương trình.

**Không suy từ output** rằng object đã được hoặc chưa được thu gom. Nếu JIT tối ưu đi, thậm chí một allocation vật lý riêng có thể không tồn tại. Mục tiêu duy nhất là phân biệt control flow, lexical scope và GC.

### Dùng `javap`

~~~bash
javap -c -l -p ScopeDemo
~~~

Bạn có thể xem:

- Bytecode tạo `ResourceHolder` và gọi constructor.
- `LineNumberTable` nếu compiler giữ thông tin line.
- `LocalVariableTable` cho `scoped` vì biên dịch bằng `-g`.

**Đừng biến `LocalVariableTable.Length` thành “số instruction rồi object chết”**. Debug scope không nhất thiết phản ánh JIT liveness hoặc thời điểm GC.

## 10. Ví dụ quan trọng: field/static/cache giữ object sau method return

Xét chương trình Java 17+ **`RetainedObjectDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class RetainedObjectDemo {
    private static final List<byte[]> CACHE = new ArrayList<>();

    static void populateCache() {
        byte[] data = new byte[1024];
        CACHE.add(data);
        data = null;
    }

    public static void main(String[] args) {
        populateCache();

        System.out.println("retained=" + CACHE.size());
        System.out.println("length=" + CACHE.get(0).length);

        CACHE.clear();
        System.out.println("after-clear=" + CACHE.size());
        System.out.println("collection-still-not-guaranteed");
    }
}
~~~

Biên dịch:

~~~bash
javac -g RetainedObjectDemo.java
java RetainedObjectDemo
~~~

**Stdout chính xác:**

~~~text
retained=1
length=1024
after-clear=0
collection-still-not-guaranteed
~~~

Giải thích chi tiết:

1. `populateCache` tạo một `byte[]` trên heap theo mô hình, rồi lưu reference vào `CACHE` (một `static` field trỏ tới `ArrayList`).
2. `data = null` chỉ bỏ reference local, **không xóa phần tử trong list**.
3. Khi method kết thúc, `CACHE` vẫn giữ reference tới array, vì vậy `CACHE.get(0).length` vẫn là 1024.
4. `CACHE.clear()` bỏ phần tử; theo API `ArrayList`, list không còn giữ phần tử ấy sau thao tác thành công. Nhưng object array có được GC ngay không vẫn **không có bảo đảm**.
5. `CACHE.size() == 0` chứng minh collection không giữ phần tử ở logic API, **không phải** xác nhận object array memory đã được reclaimed.

Nếu có một alias khác tới array, dù đã `CACHE.clear()`, array vẫn có thể reachable. Cần tìm **mọi relevant roots/path** khi debug giữ bộ nhớ.

### Các chỗ giữ references lâu hơn dự kiến

Trong ứng dụng lớn, những đường giữ object thường là:

- `static Map/List` hoặc cache không có eviction policy.
- Listener/callback được đăng ký mà không unregister.
- Thread pool task/queue giữ object, `CompletableFuture` callback hoặc lambda captures.
- Một `ThreadLocal` còn giữ reference trên platform thread sống lâu.
- Session map, HTTP request context hoặc registry tồn tại lâu.
- ClassLoader/plugin framework vẫn giữ object/Class/Method hoặc context loader cũ.
- JNI global references, native handles trong tích hợp native.

Một object **không phải leak chỉ vì còn trong heap**. Leak thực dụng xảy ra khi ứng dụng giữ objects **lâu hơn ý định**, khiến live-set không ngừng tăng hoặc tài nguyên không được giải phóng. Muốn kết luận cần đối chiếu logic ownership và đo memory qua thời gian.

## 11. WeakReference: quan sát mà không giữ strong reference

`java.lang.ref.WeakReference<T>` có thể tham chiếu một object mà **bản thân weak reference không giữ object strongly reachable**. Khi GC xác định referent chỉ còn weakly reachable theo các rules, nó có thể clear weak references và enqueue chúng nếu được đăng ký với queue.

**Quan trọng:** `weak.get() == null` không phải một timer; và `weak.get() != null` tại thời điểm kiểm tra không chứng minh GC không bao giờ xảy ra. Không nên viết unit test yêu cầu weak reference phải clear trong vài mili giây.

### Ví dụ Java 17+ với output hoàn toàn xác định

Tạo file **`WeakReferenceDemo.java`**:

~~~java
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class WeakReferenceDemo {
    static final class Node {
        final int id;

        Node(int id) {
            this.id = id;
        }
    }

    public static void main(String[] args) {
        Node keeper = new Node(42);
        WeakReference<Node> weak = new WeakReference<>(keeper);

        System.out.println("id=" + keeper.id);
        System.out.println("same-while-strong="
                + (weak.get() == keeper));

        // Bảo đảm keeper còn strongly reachable tới điểm này.
        Reference.reachabilityFence(keeper);

        keeper = null;
        System.gc();

        // Không in weak.get() sau GC vì kết quả không được bảo đảm.
        System.out.println("gc-requested");
        System.out.println("weak-clear-time-unspecified");
    }
}
~~~

Chạy:

~~~bash
javac -g WeakReferenceDemo.java
java WeakReferenceDemo
~~~

**Stdout chính xác:**

~~~text
id=42
same-while-strong=true
gc-requested
weak-clear-time-unspecified
~~~

Vì `Reference.reachabilityFence(keeper)` bảo đảm referent strongly reachable ít nhất đến điểm đó, kết quả so sánh `weak.get() == keeper` trước fence là `true` trong chương trình hợp lệ. Sau `keeper = null` và `System.gc()`, code **cố ý không in kết quả `weak.get()`**: kết quả có thể thay đổi do GC/JIT/runtime và không phải contract thời gian.

### Đừng giữ strong reference vô tình

Nếu bạn kiểm tra:

~~~java
Node keeper = new Node(42);
WeakReference<Node> weak = new WeakReference<>(keeper);
List<Node> list = new ArrayList<>();
list.add(keeper);
keeper = null;
~~~

Đây là **đoạn minh họa**, thiếu imports/khung class. `list` vẫn giữ **strong reference** tới Node (nếu list còn reachable); vì thế đừng chờ `WeakReference` tự clear chỉ vì `keeper` bị gán null.

### ReferenceQueue không biến GC thành callback đồng bộ

`ReferenceQueue<T>` cho phép nhận thông báo **sau khi GC xử lý references** theo điều kiện API. Đây không phải hệ thống callback “ngay lập tức khi biến ra khỏi scope”. `queue.poll()` có thể trả `null` nếu chưa có notification, dù ứng dụng đã đặt các locals thành null.

Với `PhantomReference`, `get()` luôn trả null; cần kết hợp queue/cleanup design đúng thay vì dùng nó để đếm objects sống bằng cách thử `get()`.

## 12. `Reference.reachabilityFence` dùng khi nào?

Từ Java 9, phương thức:

~~~java
java.lang.ref.Reference.reachabilityFence(object);
~~~

**Bảo đảm object được truyền vào còn strongly reachable ít nhất tới sau lời gọi fence** bất chấp tối ưu mà JVM có thể thực hiện. Đây là cơ chế rất hữu ích cho trường hợp **đối tượng Java giữ quyền sở hữu native resource** hoặc Cleaner/finalization-like processing mà thời điểm object bị coi là unreachable có hậu quả quan sát được.

### Tình huống lý thuyết với native resource

~~~java
final class NativeHolder implements AutoCloseable {
    private final long handle;

    NativeHolder(long handle) {
        this.handle = handle;
    }

    void use() {
        nativeUse(handle);
        java.lang.ref.Reference.reachabilityFence(this);
    }

    private static native void nativeUse(long handle);

    @Override
    public void close() {
        nativeClose(handle);
    }

    private static native void nativeClose(long handle);
}
~~~

**Đây là thiết kế minh họa, không phải chương trình chạy độc lập:** hai native methods cần implementation JNI/FFM thích hợp, logic idempotent close và kiểm soát concurrency thực tế. Ý của `reachabilityFence(this)`: đừng để compiler coi wrapper Java object không còn strongly reachable **trước khi** hoàn tất native operation mà wrapper sở hữu, khi cleanup phụ thuộc reachability.

**Giới hạn rõ ràng:**

- `reachabilityFence` **không gọi GC**, **không cấp phát memory**, **không ép giữ object sống vĩnh viễn**.
- Nó không tự đồng bộ các thao tác `close`/`use` trên nhiều threads; phải thiết kế protocol về resource ownership riêng.
- Không dùng fence cho mọi object để “chữa GC lỗi”: bình thường GC đã bảo đảm các use hợp lệ của Java code.

## 13. Tài nguyên ngoài heap cần đóng theo vòng đời, không chờ GC

Có những thứ quan trọng hơn Java heap memory: file descriptors, sockets, streams, database connections, locks hoặc native handles. Việc object wrapper trở nên unreachable **không đồng nghĩa** những resource ấy đã đóng đúng lúc.

Cách Java khuyến nghị là **`try-with-resources`** với `AutoCloseable`:

~~~java
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CloseDemo {
    public static void main(String[] args) throws IOException {
        byte[] bytes = {1, 2, 3};

        try (InputStream input = new ByteArrayInputStream(bytes)) {
            System.out.println("first=" + input.read());
        }

        System.out.println("resource-scope-ended");
    }
}
~~~

Biên dịch:

~~~bash
javac CloseDemo.java
java CloseDemo
~~~

**Stdout:**

~~~text
first=1
resource-scope-ended
~~~

Ở ví dụ này, `try-with-resources` bảo đảm gọi `close()` theo ngữ nghĩa Java khi rời block, kể cả trên đường exception phù hợp. `ByteArrayInputStream` không đại diện cho một file descriptor thật, nhưng minh họa control flow mà không cần file ngoài.

**Không suy** `close()` = GC object. `close()` là resource-lifecycle operation ở Java API; object wrapper vẫn có thể còn trên heap, trong khi resource đã được đóng. Nếu cần minh họa resource OS, dùng `Files.newBufferedReader`/`FileInputStream` trong bài thực hành có file cụ thể và kiểm soát exception.

### Finalization và Cleaner có thay được try-with-resources không?

- Java finalization bị **deprecated for removal**, và theo Java SE 25 implementation có thể disable finalization. Không viết logic cần `finalize()` chắc chắn được gọi để giải phóng tài nguyên đúng lúc.
- `Cleaner` có thể hỗ trợ cleanup fallback khi object trở nên phantom reachable theo semantics, nhưng **không có deadline** đủ thay thế thao tác `close()` tường minh cho file/socket/database resources quan trọng.
- **Ưu tiên sở hữu tài nguyên rõ ràng**, đóng trong `finally`/`try-with-resources` hoặc giao thức vòng đời cụ thể.

## 14. Làm sao phân biệt memory leak với GC chưa chạy?

Nếu bạn thấy heap tăng sau một request, đừng vội gán null hàng loạt. Hãy đánh giá:

### Trình tự chẩn đoán

1. **Kiểm tra live-set:** memory sau các GC chu kỳ phù hợp có tiếp tục tăng theo tải tương đương không?
2. **Xem GC logs/metrics:** collector có chạy chưa; heap used trước/sau GC, allocation rate và pause/throughput ra sao?
3. **Dùng heap dump khi có bằng chứng retention:** tìm object dominators và reference paths từ GC roots.
4. **Tìm owner dài hạn:** static caches, maps, event listeners, task queues, ThreadLocal, long-lived services, classloader references.
5. **Chứng minh path cụ thể giữ object:** nếu bỏ một local alias mà vẫn có static map, GC không thể thu object do map còn giữ.
6. **Sửa ownership/eviction:** remove entries, unregister listeners, close resources, giới hạn cache size, bỏ references không cần thiết.
7. **Kiểm chứng sau sửa** bằng workload tái hiện, heap profiling và GC metrics tương đương.

### Ví dụ phân biệt

| Quan sát | Điều có thể kết luận | Điều chưa được phép kết luận |
| --- | --- | --- |
| `obj = null` vừa chạy | Một variable được gán null | Object đã reclaimed |
| Method chứa `obj` vừa return | Frame cũ kết thúc về mặt semantics | Không còn reference ở field/callback/other thread |
| `WeakReference.get() != null` | Tại thời điểm đó referent còn được weak.get trả về | GC chưa hề từng chạy, object sẽ luôn còn |
| `WeakReference.get() == null` | Weak ref đã bị clear hoặc do code chủ động clear | Memory chắc chắn đã trả về OS |
| Heap used không giảm ngay | Chưa quan sát giảm theo metric đó | Memory leak chắc chắn tồn tại |
| Sau nhiều GC live-set vẫn tăng | Có dấu hiệu retention cần điều tra | Chính xác class nào leak nếu chưa xem graph |
| `CACHE.clear()` | Collection không giữ entries theo API | Mọi aliases khác đã mất hoặc GC vừa chạy |
| `System.gc()` đã return | VM đã xử lý yêu cầu best-effort theo API | Mọi unreachable object đã được thu gom |

**Một heap dump lớn không tự động là memory leak**: có thể do workload/caches hợp lệ. Điều quyết định là objects không cần nữa nhưng vẫn được giữ reachable qua owners không mong muốn.

### Quan sát HotSpot

Một số tùy chọn/công cụ:

~~~bash
java -version
java -Xlog:gc* -jar app.jar
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
~~~

Đây là **mẫu** cho ứng dụng và PID thực, **không chạy nguyên xi nếu không có `app.jar`**. `jcmd` là công cụ JDK để hỏi thông tin JVM được hỗ trợ và có quyền truy cập; các commands/outputs cụ thể có thể thay đổi theo phiên bản VM, `GC.class_histogram` có thể tốn chi phí. Dùng công cụ quan sát ở môi trường test/staging trước khi tác động lên production.

Với **JFR**, bạn có thể quan sát allocation rate, GC, object count/old object sample theo cấu hình recording. **Đừng nhầm allocation rate cao** với memory leak: object có thể được tạo nhanh nhưng cũng nhanh chóng trở thành unreachable; leak nằm ở live retention.

## 15. Thời điểm ra khỏi scope và tối ưu compiler/JIT

Ví dụ:

~~~java
static void mainWork() {
    Object a = new Object();
    consume(a);

    // Trong source, a vẫn có thể được nhắc đến theo scope.
    doSomethingElse();
}
~~~

Đây là **đoạn minh họa cần khai báo `consume`, `doSomethingElse` trong class**. Nếu `consume` không giữ alias và `doSomethingElse` không còn dùng `a`, optimizer có thể kết luận `a` không cần cho tiếp tục thực thi. Ngược lại `consume` có thể giữ reference bằng cách lưu vào field, array hoặc callback, khiến object sống lâu hơn method.

**Độ sâu đặc biệt:** JLS `12.6.1` nhắc rằng reachability gắn với **potential continuing computation**. Cách xác định giá trị có còn reachable không phức tạp hơn việc “biến còn nằm trong source scope”. GC không bắt buộc đợi đến hết dấu ngoặc để xem một object đã không còn cần nữa.

### Đừng tự ép `null` để “tối ưu GC” ở mọi nơi

Với một local ngắn hạn:

~~~java
void handle() {
    byte[] data = computeData();
    process(data);
    data = null;
    continueWork();
}
~~~

Đoạn này là minh họa, cần có các methods `computeData`/`process`/`continueWork`. Assignment `data = null` có thể vô ích vì JIT đã biết reference dead sau `process`. Trên một số code và tình huống memory pressure đặc biệt, bỏ giữ reference lâu là có ý nghĩa; nhưng **bỏ tham chiếu không đồng nghĩa bắt GC chạy**, và viết null cho mọi local làm code khó đọc hơn.

Một ví dụ hiệu quả hơn thường là **xóa reference khỏi container sống lâu** ở thời điểm owner không cần nữa, hoặc chia method có phạm vi dữ liệu lớn thành những bước rõ ràng. Lợi ích thực tế cần được đo, không đoán theo source.

## 16. Phân biệt mạnh/weak/soft/phantom trong thực hành

| Cơ chế | Mục tiêu | Có giữ object strongly reachable không? | Có bảo đảm thu ngay không? |
| --- | --- | --- | --- |
| Local/field/array **strong reference** | Truy cập object thông thường | Có thể, khi đường đó còn reachable và cần thiết | Không |
| **`SoftReference<T>`** | Bộ nhớ đệm nhạy với memory pressure | Không phải strong reference | Không |
| **`WeakReference<T>`** | Mapping/cache không giữ referent bằng strong ref | Không phải strong reference | Không |
| **`PhantomReference<T>`** | Theo dõi hậu kỳ và cleanup patterns qua queue | Không phải strong reference | Không |
| **`ReferenceQueue<T>`** | Nhận notification sau reference processing | Queue không tự đảm bảo giữ mọi referent | Không |
| **`Cleaner`** | Quản lý cleanup action khi referent đạt điều kiện liên quan | Không dùng thay deadline đóng resource | Không |
| **`Reference.reachabilityFence`** | Giữ đối số strongly reachable ít nhất tới fence | Có, tới fence theo semantics | Không yêu cầu thu gom |

**Lưu ý với `WeakHashMap`:** keys là weakly referenced, nhưng nếu một **value giữ strong reference ngược tới key**, entry có thể vẫn giữ key alive gián tiếp. Thực tế thiết kế weak caching cần xem toàn bộ object graph.

Không tạo vòng chờ bận (`while (weak.get() != null) { System.gc(); }`) để đòi GC theo deadline; cách này có thể không kết thúc đúng kỳ vọng, tiêu tốn CPU và sai với semantics.

## 17. Các hiểu nhầm phổ biến

| Ngộ nhận | Điều đúng |
| --- | --- |
| “`x = null` xóa object ngay.” | Chỉ đổi **một reference variable**; các aliases khác có thể còn. |
| “Đóng dấu `}` gọi GC.” | Scope là luật tên biến, không phải instruction thu gom. |
| “Method return thì mọi objects đã tạo trong method đều chết.” | Objects có thể escape qua return, field, static collection, callback, thread khác. |
| “Một object unreachable nghĩa đã được reclaimed.” | **Eligibility khác actual reclamation**. |
| “GC là một hành động xảy ra ngay khi reference count về 0.” | Java GC không được định nghĩa là reference counting và không có deadline như vậy. |
| “Cứ gán null là heap used giảm ngay.” | GC có thể chưa chạy; heap memory có thể còn committed dù đã reusable. |
| “`System.gc()` buộc JVM thu gom mọi object không còn dùng.” | API chỉ best-effort/hint, không có bảo đảm số lượng/thời gian. |
| “Tên biến còn trong scope thì object chắc chắn sống tới cuối block.” | Optimizer có thể rút ngắn liveness nếu không còn observable use. |
| “Ra khỏi scope thì object chắc chắn unreachable.” | References khác có thể tồn tại trong graph. |
| “LocalVariableTable length cho biết GC thu lúc nào.” | Debug table không phải GC liveness timeline. |
| “Một WeakReference tồn tại sẽ giữ object mãi.” | Weak reference không giữ referent strongly reachable. |
| “WeakReference.clear ở đúng thời điểm chạy `System.gc()`.” | Reference processing không có lịch do source đặt ra. |
| “`PhantomReference.get() == null` chứng minh object đã GC.” | `get()` của PhantomReference trả null theo API. |
| “`reachabilityFence` buộc GC chạy.” | Nó chỉ đảm bảo strong reachability đến điểm fence. |
| “Cleaner/finalize luôn chạy đúng hạn để đóng file.” | Không; finalization còn bị deprecated for removal, và cleanup GC không có deadline. |
| “Memory leak chỉ xảy ra khi developer dùng native allocation.” | Java object graph có thể giữ references quá lâu qua static caches/listeners/ThreadLocal. |
| “Cứ heap tăng một lần là leak.” | Cần quan sát live-set sau GC, allocation load, owners. |
| “Đóng `AutoCloseable` đồng nghĩa wrapper object được GC.” | `close` quản lý resource lifecycle; object có thể vẫn còn reachable. |

## 18. Tự kiểm tra

**1.** `Box a = new Box(7); Box b = a; a = null;` — object có biến mất không?  
**Đáp án:** Không. `b` vẫn giữ reference tới object; `b.id` có thể đọc được.

**2.** Tại sao dấu `}` kết thúc block không đảm bảo GC?  
**Đáp án:** Nó kết thúc **lexical scope của tên biến**, không tự xóa aliases khác, không bắt GC chạy ngay.

**3.** “Eligible for GC” có bằng “GC đã reclaim memory” không?  
**Đáp án:** Không. Đủ điều kiện có thể bị thu hồi và thực sự được thu hồi là hai trạng thái/thời điểm khác nhau.

**4.** `static List<byte[]> CACHE` giữ array; local `data=null` trong method tạo array có tác dụng bỏ reference trong CACHE không?  
**Đáp án:** Không. Cần loại entry trong cache nếu owner thực sự không cần.

**5.** `System.gc()` đảm bảo mọi WeakReference được clear ngay khi return?  
**Đáp án:** Không. API không hứa rõ số reference được xử lý hoặc deadline.

**6.** Tại sao dùng `WeakReference.get()` trong một unit test rồi chờ nó thành null theo millisecond không đáng tin?  
**Đáp án:** GC timing và reference processing không được API bảo đảm theo thời gian.

**7.** Có trường hợp GC được phép coi object không còn reachable trước khi biến source ra khỏi scope không?  
**Đáp án:** Có. Optimizing compiler/JIT có thể rút ngắn liveness khi không còn use quan sát được.

**8.** `Reference.reachabilityFence(x)` cho bảo đảm nào?  
**Đáp án:** Giữ `x` strongly reachable ít nhất đến sau lời gọi, bất chấp một số tối ưu; không ép thu gom.

**9.** Đóng socket/database connection theo cách đáng tin hơn chờ GC?  
**Đáp án:** `close()` hoặc `try-with-resources` theo đúng API và vòng đời ứng dụng.

**10.** Heap memory không giảm sau một lần `null` là chứng cứ leak chắc chắn?  
**Đáp án:** Không. Cần kiểm tra GC cycles, live set và graph retention.

**11.** Tại sao `weak.get() == null` chưa cho biết heap space đã trả lại cho OS?  
**Đáp án:** Reference clearing, object reclamation, heap reuse và trả memory về OS là những cơ chế không đồng thời được bảo đảm.

**12.** Trong class dùng JIT, `new Pair()` luôn tạo heap object thật không?  
**Đáp án:** Không. Escape analysis/scalar replacement có thể loại allocation nếu vẫn giữ semantics chương trình.

## 19. Nguồn chính thức

- [JLS Java SE 25 — §12.6 Finalization of Class Instances](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.6): nền tảng về object reachability/finalization; đặc biệt **`12.6.1`** và tác động của optimizing transformations.
- [JDK 25 — `java.lang.ref` package](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/package-summary.html): các loại strong/soft/weak/phantom reachability và ReferenceQueue.
- [JDK 25 — `java.lang.ref.Reference.reachabilityFence`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/Reference.html#reachabilityFence(java.lang.Object)): giữ strong reachability tới một điểm xác định.
- [JDK 25 — `java.lang.ref.WeakReference`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/WeakReference.html): semantics clearing của weak references.
- [JDK 25 — `java.lang.ref.PhantomReference`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/PhantomReference.html): phantom reachability và `get()`.
- [JDK 25 — `Runtime.gc()`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Runtime.html#gc()): best effort, không bảo đảm reclaim/count/timing.
- [JDK 25 — `System.gc()`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#gc()): shorthand đến Runtime.gc.
- [JVMS Java SE 25 — §2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.3): class instances/arrays trong heap, GC do VM quản lý.
- [JLS Java SE 25 — §14.4 Local Variable Declarations](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.4): biến cục bộ và phạm vi cú pháp.
- [JDK 25 — `AutoCloseable`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/AutoCloseable.html): đóng resources có quản lý vòng đời.
- [JDK 25 — `Cleaner`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/Cleaner.html): cleanup hỗ trợ khi reachability thay đổi; không có deadline bảo đảm.
- [JDK 25 — `jcmd`](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html): diagnostics liên quan heap/histogram và các lệnh VM được hỗ trợ.

**Kết luận:** **`= null` và hết scope chỉ tác động tới một cách giữ reference/địa chỉ tên biến; chúng không chứng minh toàn bộ đường truy cập tới object đã mất, và càng không buộc garbage collector thu hồi object ngay.** Hãy xác định object graph và ownership, phân biệt reachability với reclamation, sử dụng `AutoCloseable` để đóng tài nguyên có hạn định, và đánh giá GC bằng quan sát thực tế thay vì dựa vào dấu ngoặc hay phép gán null.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-004">Tham chiếu mạnh, mềm, yếu và ảo trong java.lang.ref</a>

<details>
<summary>Xem chi tiết</summary>

# Tham chiếu mạnh, mềm, yếu và ảo trong `java.lang.ref`

**Một đối tượng có còn được truy cập hay không** ảnh hưởng trực tiếp đến việc garbage collector (GC) có thể thu hồi nó. Nhưng “có một biến đang giữ reference” chưa đủ để kết luận object phải sống mãi: Java phân biệt **strong**, **soft**, **weak** và **phantom references**, với những quy tắc reachability khác nhau. Những cơ chế này quan trọng cho cache, bản đồ khóa yếu, canonicalization, quản lý metadata và dọn dẹp tài nguyên ngoài Java heap.

Bài thuộc mục **Khả năng truy cập** trong Java Advance – JVM. Mục tiêu là hiểu object graph dưới góc nhìn JVM, cách dùng `java.lang.ref` đúng hợp đồng Java 17, thời điểm `Reference.get()` trả `null`, vai trò `ReferenceQueue` và `Cleaner`, và những điều **không thể dự đoán chính xác** do GC là bất đồng bộ.

Đặc biệt phân biệt:

- **Strong reference** là reference thông thường mà code Java dùng; đối tượng còn strong-reachable **không bị thu hồi** chỉ vì GC chạy.
- **`SoftReference<T>`** cho phép đối tượng được giữ lại khi bộ nhớ phù hợp nhưng GC có thể xóa reference vì nhu cầu bộ nhớ; không phải hợp đồng cache có thời hạn giữ cố định.
- **`WeakReference<T>`** không giữ cho referent sống khi chỉ còn weak reachability; dùng cho metadata/canonical mappings và nền tảng của weak keys.
- **`PhantomReference<T>`** không cho lấy lại referent qua `get()`; phối hợp với `ReferenceQueue` để nhận tín hiệu phục vụ cleanup sau khi referent không còn được dùng.
- **`ReferenceQueue<T>`** chuyển thông tin về những **reference objects**, không trực tiếp “trả object đã chết”.
- **`Cleaner`** (Java 9+) xây trên phantom-reference processing để đăng ký hành động cleanup; **`AutoCloseable` và try-with-resources** vẫn nên là cách đóng tài nguyên đúng hạn.

## 1. Reachability: GC nhìn vào đồ thị tham chiếu, không nhìn tên biến

Để hiểu bốn mức reference, trước hết hình dung **GC roots**: những nguồn được JVM coi là điểm bắt đầu việc tìm object còn có thể được chương trình sử dụng, chẳng hạn Java thread-local state còn sống, static state phù hợp và các nguồn đặc biệt do VM/JNI quản lý.

```text
GC roots
   │
   ├── strong ──► User ──► strong ──► Address
   │
   └── strong ──► WeakReference ── weak ──► CachedObject
                                       │
                        không có strong path khác?
                                       │
                                GC có thể clear weak
```

Điều quan trọng là **loại cạnh trên đồ thị**. Một object `WeakReference` bản thân nó có thể đang được giữ bằng strong reference; nhưng liên kết từ `WeakReference` tới đối tượng đích (**referent**) lại có ngữ nghĩa **weak**. Giữ `WeakReference` trong một `static final` không tự biến referent thành strong-reachable.

Không phải tất cả các field trỏ tới object đều có loại `WeakReference`. Những biến/field/array elements chứa reference Java thông thường tạo strong edges, trừ khi API chứa chúng có đặc tả reachability đặc biệt.

### 1.1. “Strong reference” có phải một class không?

Không. Java không có `new StrongReference<>(obj)` trong `java.lang.ref`. “Strong reference” mô tả việc bạn **trực tiếp** có reference trong Java như:

```java
Object value = new Object();
Object alias = value;
```

Cả `value` và `alias` chứa reference thông thường tới cùng object. Nếu chúng còn thực sự được dùng, object không trở thành weakly reachable chỉ vì code cũng tạo một `WeakReference<>(value)`.

### 1.2. Không phải cứ ra khỏi dấu `}` là GC phải thu hồi

Một local variable hết lexical scope **không tạo mệnh lệnh GC**. JIT còn có thể xác định object không còn được dùng **sớm hơn** phần cuối scope, trong khi references ở field/static/stack khác có thể giữ object sống lâu hơn.

Không dựa vào phạm vi source đơn thuần để đo thời điểm enqueue hay kết thúc resource. Khi có việc quản lý tài nguyên với side effects quan sát được, `Reference.reachabilityFence` hoặc explicit close có thể quan trọng hơn tưởng tượng ban đầu.

## 2. Thứ tự strong → soft → weak → phantom → unreachable

Package specification Java 17 `java.lang.ref` mô tả các mức reachability theo thứ tự từ mạnh đến yếu:

| Mức | Điều kiện ý tưởng | Hệ quả chính |
| --- | --- | --- |
| **Strongly reachable** | Có đường đi từ thread/root mà **không traversing reference objects** theo định nghĩa reachability | GC không được thu hồi object chỉ vì đang chạy GC |
| **Softly reachable** | Không còn strong-reachable nhưng còn đường đi qua **soft reference** | GC có thể clear soft theo nhu cầu memory |
| **Weakly reachable** | Không còn strong/soft reachable nhưng còn đường qua **weak reference** | Khi GC xác định weak reachability, nó clear weak refs theo quy tắc |
| **Phantom reachable** | Không còn các mức trên, đã qua xử lý finalization theo định nghĩa và có phantom reference | Đủ điều kiện để phantom-reference processing và notification |
| **Unreachable** | Không thuộc các mức reachability trên | Có thể được GC thu hồi khi phù hợp |

Đây là **phân loại ở mức GC**, không phải các trạng thái do chương trình được quyền tự đặt bằng cách ghi enum vào object. Một referent chỉ còn weak-reachable có thể biến mất khỏi góc nhìn `WeakReference.get()` khi GC thực hiện reference processing.

### 2.1. Object chỉ có một “mức” tại một thời điểm theo con đường mạnh nhất

Giả sử một object có cả `SoftReference` và `WeakReference` trỏ tới nó:

```text
root ─► SoftReference ─soft─┐
                            ├──► Object
root ─► WeakReference ─weak─┘
```

Nếu không có strong path, nó đang **softly reachable**, không phải weakly reachable, vì soft mạnh hơn weak. Điều đó có thể trì hoãn clear weak ref so với trường hợp chỉ có weak. Khi các soft references bị clear và không còn strong path, object có thể trở thành weakly reachable trong đợt xử lý phù hợp.

Ngược lại, nếu có strong path khác, soft và weak refs trỏ tới cùng object thường vẫn có thể `get()` trả object (nếu chưa bị chương trình chủ động `clear()`). Một soft/weak reference **không ép GC xóa** khi target vẫn đang strongly reachable.

### 2.2. Không có đồng hồ đếm ngược cho weak/soft

GC timing phụ thuộc heap pressure, collector, safepoints, optimization, reference processing, CPU và việc object còn reachable từ đâu. **Không có hợp đồng “weak bị clear sau N giây”** hoặc “soft sống đến lúc heap dùng hết 80%”. Những giả định như vậy khiến unit tests không ổn định và cache behavior khó dự đoán.

## 3. Strong reference: con đường giữ object sống thông thường

Các field, local variables, array elements và method parameters bình thường có thể giữ strong references khi thực sự là các nguồn reachable. Ví dụ:

```java
import java.lang.ref.WeakReference;

public class StrongExample {
    public static void main(String[] args) {
        Object target = new Object();               // strong
        WeakReference<Object> weak = new WeakReference<>(target);

        System.out.println("alive=" + (weak.get() == target));
        java.lang.ref.Reference.reachabilityFence(target);
    }
}
```

Chạy:

```bash
javac --release 17 StrongExample.java
java StrongExample
```

Stdout:

```text
alive=true
```

`target` là strong reference và được sử dụng ở phép so sánh; `reachabilityFence(target)` giữ mạnh reachability đến điểm gọi fence. GC không được clear weak reference đến target chỉ vì có một weak wrapper khi target vẫn còn strong-reachable.

### 3.1. Một biến Java có thể được tối ưu ra khỏi liveness

Ví dụ native handle gắn với một Java wrapper:

```java
long handle = resource.getNativeHandle();
useNativeHandle(handle);
// Không còn đọc field nào của resource.
```

JIT có thể xem `resource` không còn được dùng sau khi lấy `handle`, mặc dù công việc native còn dùng tài nguyên gắn với resource. Nếu `Cleaner` thu hồi tài nguyên quá sớm, mã native có thể dùng handle đã đóng.

Trong một thiết kế thích hợp, đặt `Reference.reachabilityFence(resource)` **sau thao tác cuối cần tài nguyên còn sống**:

```java
long handle = resource.getNativeHandle();
try {
    useNativeHandle(handle);
} finally {
    java.lang.ref.Reference.reachabilityFence(resource);
}
```

Đây là **snippet ý tưởng**, cần tự định nghĩa `resource` và `useNativeHandle`. Fence không thay close/locking của tài nguyên; nó chỉ bảo đảm strong reachability ít nhất cho tới vị trí gọi. `reachabilityFence` không yêu cầu GC chạy.

## 4. `SoftReference<T>`: cache có thể bị GC giải phóng theo memory demand

`SoftReference` phù hợp với ý tưởng **“giữ một giá trị khi còn chỗ, nhưng cho GC bỏ cache khi cần memory”**. Constructor phổ biến:

```java
SoftReference<Image> ref = new SoftReference<>(image);
Image cached = ref.get();
```

Đoạn này là **minh họa** vì cần class `Image` và biến `image` đã định nghĩa.

API:

- `new SoftReference<>(value)` tạo soft reference **không đăng ký queue**.
- `new SoftReference<>(value, queue)` đăng ký với `ReferenceQueue`.
- `ref.get()` trả referent khi còn tồn tại, hoặc `null` nếu reference đã bị clear bởi GC/code.
- `ref.clear()` chủ động clear; không có nghĩa GC đã quyết định memory pressure.
- Theo Java 17 Javadoc, các soft references còn trỏ tới **softly-reachable objects** được đảm bảo bị clear trước khi VM ném `OutOfMemoryError` theo cơ chế được nêu. Điều đó **không** hứa mọi loại OOME đều phòng được bằng soft cache; native memory, quá nhiều threads hay heap live set strong có thể vẫn gây OOME.

### 4.1. Ví dụ cache phải luôn có đường tái tạo

```java
import java.lang.ref.SoftReference;
import java.util.Objects;
import java.util.function.Supplier;

public class SoftCacheDemo {
    private static SoftReference<String> cached = new SoftReference<>(null);

    static String getOrLoad(Supplier<String> loader) {
        String result = cached.get();
        if (result == null) {
            result = Objects.requireNonNull(loader.get());
            cached = new SoftReference<>(result);
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("first=" + getOrLoad(() -> "DATA"));
        cached.clear();    // Xóa thủ công để test nhánh cache miss.
        System.out.println("second=" + getOrLoad(() -> "DATA"));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 SoftCacheDemo.java
java SoftCacheDemo
```

Stdout xác định:

```text
first=DATA
second=DATA
```

Đây là demo **single-threaded** cho nhánh hit/miss; code sử dụng `clear()` chủ động để tạo miss có kiểm soát, **không giả vờ mô phỏng quyết định memory pressure của GC**. Trong hệ thống nhiều threads, code phải đồng bộ/cache atomic để tránh duplicate loads và data races.

### 4.2. Vì sao `SoftReference` không nên là chiến lược cache mặc định?

- Chính sách xóa do JVM/collector quyết định; cache có thể giữ dữ liệu quá lâu hoặc mất dữ liệu đồng loạt khi memory pressure.
- Không có giới hạn dung lượng, TTL, eviction policy, hay nguyên tắc chọn least-recently-used ở mức Java API.
- Reconstruct value có thể tốn CPU/I/O và gây cache stampede khi nhiều giá trị bị clear.
- Soft refs cũng là **objects** và có chi phí lookup/reference processing.
- Một soft cache giữ nhiều dữ liệu sẽ thay đổi retained heap và GC activity.

Với cache production cần quota/TTL/metrics/chủ động eviction, thường phù hợp hơn dùng cache có chính sách rõ (ví dụ bounded cache) thay vì giao toàn bộ quyết định cho soft reachability.

## 5. `WeakReference<T>`: không giữ referent sống

`WeakReference` thường dùng khi ứng dụng **muốn ghi nhớ một object chừng nào nơi khác vẫn dùng nó**; khi không còn strong/soft paths, GC có thể clear weak references theo quy tắc.

Ví dụ khái niệm:

```text
App ──strong──► WeakReference<W>
                     │
                   weak
                     │
                     ▼
                Widget@123

Nếu app không còn đường strong/soft tới Widget@123:
GC có thể phát hiện weak reachability và clear reference.
App vẫn giữ WeakReference object, nhưng get() về sau có thể là null.
```

### 5.1. Bài thử `WeakReference` không được dựa vào timing cố định

Một ví dụ được thấy nhiều:

```java
Object obj = new Object();
WeakReference<Object> weak = new WeakReference<>(obj);
obj = null;
System.gc();
System.out.println(weak.get());
```

Đây là **snippet KHÔNG có stdout cố định**. `System.gc()` là một lời yêu cầu, GC có thể không xảy ra ngay hoặc bị vô hiệu hóa; JIT liveness và references khác cũng ảnh hưởng. Kết quả có thể là `null` hoặc object theo thời điểm thực thi.

Đừng viết unit test kiểu `assertNull(weak.get())` sau một `System.gc()` duy nhất. Muốn kiểm tra logic khi referent biến mất, dùng `weak.clear()` để tạo tình huống deterministically hoặc tách phần queuing thành test phù hợp.

### 5.2. Atomic read pattern: luôn lưu kết quả `get()` vào strong local

```java
WeakReference<Resource> weak = obtainWeakRef();
Resource resource = weak.get();
if (resource != null) {
    resource.use();
}
```

Đây là **snippet minh họa**, `Resource`/`obtainWeakRef` cần được khai báo trong ứng dụng.

**Tại sao phải lưu local?** Nếu gọi `weak.get()` nhiều lần, lần đầu có thể trả object, lần sau có thể trả `null` khi referent chỉ còn weakly reachable giữa các lần. Lấy một lần vào local `resource` tạo strong reference trong thời gian nó cần được dùng theo semantics; có thể thêm `Reference.reachabilityFence(resource)` nếu công việc với tài nguyên nằm ngoài đường sử dụng Java mà optimizer khó quan sát.

Không viết:

```java
if (weak.get() != null) {
    weak.get().use();  // Rủi ro: get lần hai có thể là null.
}
```

`WeakReference` không cung cấp hợp đồng “giữa hai lệnh `get()` không bao giờ có GC”; dùng một strong local là cách đúng.

## 6. `WeakHashMap`: weak keys, nhưng values thường là strong

`java.util.WeakHashMap<K,V>` là ví dụ rất hữu ích của weak references:

- Map giữ các **keys bằng weak references**.
- Values được giữ bằng **strong references**.
- Key có thể bị GC loại bỏ nếu không còn ordinary strong/soft reachability từ các đường khác.
- Mapping có thể biến mất **dù code không gọi `remove`**, nên size/iteration có hành vi phụ thuộc GC.
- Map **không tự thread-safe**.
- `WeakHashMap` không phải `WeakReference` wrapping toàn bộ Map object; nó áp dụng weak semantics cho **keys**.

### 6.1. Ví dụ runnable có kết quả ổn định

Tạo file **`WeakMapDemo.java`**:

```java
import java.util.WeakHashMap;

public class WeakMapDemo {
    static class Key {
        final String code;
        Key(String code) { this.code = code; }
    }

    static class Value {
        final Key owner;  // Cố tình giữ strong reference ngược tới key.
        Value(Key owner) { this.owner = owner; }
    }

    public static void main(String[] args) {
        WeakHashMap<Key, Value> map = new WeakHashMap<>();
        Key key = new Key("A");
        map.put(key, new Value(key));

        key = null;  // Chỉ xóa strong local reference.
        System.out.println("size=" + map.size());
        map.clear();
        System.out.println("afterClear=" + map.size());
    }
}
```

Chạy:

```bash
javac --release 17 WeakMapDemo.java
java WeakMapDemo
```

Stdout xác định:

```text
size=1
afterClear=0
```

**Vì sao vẫn có 1 entry?** Map giữ `Value` mạnh; `Value.owner` lại giữ `Key` mạnh. Dù bản thân map dùng weak keys, graph có chuỗi:

```text
GC root → WeakHashMap → Value (strong)
                         │
                         └── owner (strong) → Key

WeakHashMap → weak key ──────────────► Key
```

Điều này ngăn key chỉ còn weakly reachable. Khi `map.clear()`, ứng dụng chủ động xóa mapping; output `0` không chứng minh GC đã xử lý key.

### 6.2. Trường hợp dễ tạo leak với weak-key map

Cache value thường có thể vô tình tham chiếu ngược về key, ví dụ value là `ListenerRegistration` giữ đối tượng listener cũng chính là key. Key vẫn reachable từ map value; mapping không tự biến mất.

Các giải pháp tùy thiết kế:

- Value chỉ lưu thông tin **không tham chiếu ngược tới key**.
- Dùng explicit `remove` khi vòng đời nghiệp vụ kết thúc.
- Nếu cần weak values thay vì weak keys, thiết kế riêng với `WeakReference<V>` hoặc cache library phù hợp.
- Nếu key là chuỗi literal/interned String, key có thể tồn tại lâu; weak key **không** có tác dụng như “TTL”.

## 7. `PhantomReference<T>`: nhận thông báo mà không lấy lại object

`PhantomReference` được thiết kế cho xử lý cuối vòng đời (post-mortem cleanup scheduling). Điểm khác rõ nhất so với soft/weak:

**`PhantomReference.get()` luôn trả `null`**, kể cả khi bạn vừa tạo reference trong lúc referent vẫn đang strongly reachable.

Phantom reference cần `ReferenceQueue` để có kênh nhận notification hữu ích:

```java
ReferenceQueue<Object> queue = new ReferenceQueue<>();
Object target = new Object();
PhantomReference<Object> phantom = new PhantomReference<>(target, queue);
```

Với Java 17:

- `phantom.get() == null` luôn đúng.
- `phantom.refersTo(target)` có thể kiểm tra phantom reference hiện đang trỏ tới object `target` (phương thức `Reference.refersTo` có từ Java 16).
- Khi collector xác định object phantom-reachable theo định nghĩa và reference chưa bị clear, JVM có thể clear và enqueue phantom reference theo quy tắc.
- `queue.poll()` nhận **PhantomReference object**, không cho lấy lại referent đã không còn accessible qua `get()`.
- Nếu constructor nhận `queue = null`, reference sẽ **không được enqueue**.

### 7.1. Tại sao không cho `get()` đọc referent?

Nếu cho Java code lấy lại referent qua phantom reference, chính việc lấy nó có thể làm object strong-reachable trở lại và gây phức tạp cho cleanup. Bằng việc luôn trả `null`, phantom reference chỉ dùng như **tín hiệu vòng đời** chứ không làm “cửa sau” để truy cập lại object.

Chính vì thế, không thiết kế phantom cleanup action bằng cách:

```java
Object obj = phantom.get();
obj.close();    // Sai: get() luôn null.
```

Cần giữ **thông tin cleanup riêng biệt** (ví dụ file descriptor, native handle, id tài nguyên) mà không tạo strong path từ registry/callback về object được theo dõi.

### 7.2. Không nhầm Java 8 phantom reference rules với Java 17

Tài liệu Java 17 mô tả việc GC **clear phantom references** khi object phantom-reachable rồi enqueue đồng thời hoặc sau đó. Một số tài liệu rất cũ (trước thay đổi của JDK 9) nói phantom references không được tự clear khi enqueue; **không áp dụng nguyên xi mô tả legacy đó cho Java 17**.

Ngoài ra, ngữ nghĩa liên quan finalization không phải bảo đảm rằng mọi chương trình thực sự chạy finalizer đúng thời điểm. Finalization đã bị phản đối mạnh do khó dự đoán, rủi ro và thiết kế kế thừa; nên dùng `Cleaner` hoặc explicit resource management thay vì dựa vào `finalize()`.

## 8. Chương trình Java 17 đầy đủ so sánh bốn kiểu reference

Tạo file **`ReferenceKindsDemo.java`**:

```java
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

public class ReferenceKindsDemo {
    public static void main(String[] args) {
        Object target = new Object();         // Strong reference.
        ReferenceQueue<Object> queue = new ReferenceQueue<>();

        SoftReference<Object> soft = new SoftReference<>(target);
        WeakReference<Object> weak = new WeakReference<>(target);
        PhantomReference<Object> phantom =
                new PhantomReference<>(target, queue);

        System.out.println("soft.same=" + (soft.get() == target));
        System.out.println("weak.same=" + (weak.get() == target));
        System.out.println("phantom.null=" + (phantom.get() == null));
        System.out.println("phantom.refersTo=" + phantom.refersTo(target));

        // Giữ target mạnh reachable trong toàn bộ phần kiểm tra trên.
        Reference.reachabilityFence(target);

        // Tạo kịch bản đã clear mà KHÔNG phụ thuộc thời gian GC.
        weak.clear();
        soft.clear();
        System.out.println("weak.cleared=" + (weak.get() == null));
        System.out.println("soft.cleared=" + (soft.get() == null));
        System.out.println("queue.empty=" + (queue.poll() == null));
    }
}
```

Biên dịch/chạy JDK 17:

```bash
javac --release 17 ReferenceKindsDemo.java
java ReferenceKindsDemo
```

**Stdout xác định:**

```text
soft.same=true
weak.same=true
phantom.null=true
phantom.refersTo=true
weak.cleared=true
soft.cleared=true
queue.empty=true
```

Giải thích:

1. `target` được giữ mạnh tới `reachabilityFence`, nên `soft.get()` và `weak.get()` cùng trỏ object ấy khi kiểm tra.
2. `phantom.get()` là `null` **theo API contract**, không phải do GC đã thu hồi object.
3. `phantom.refersTo(target)` là `true` trước khi bị clear, vì nó trỏ tới object `target`.
4. `weak.clear()` và `soft.clear()` là **chủ động xóa**, không biểu thị GC quyết định hai referents đã unreachable.
5. `Reference.clear()` **không tự enqueue reference**, nên queue phantom vẫn trống trong ví dụ; target chưa có lý do để phantom-enqueue trước điểm fence. Không dùng kết quả này để suy GC sẽ luôn chạy vào thời điểm khác.

### 8.1. Bài thử `System.gc()` chỉ để quan sát, không được dùng như test chắc chắn

Có thể thử tạo rất nhiều objects ngắn hạn, bỏ strong refs rồi gọi `System.gc()` để quan sát `WeakReference.get()` hoặc queue. Nhưng:

- `System.gc()` là request/hint; JVM có quyền khác nhau về xử lý.
- Compiler có thể làm đối tượng hết strong reachability sớm hơn trực giác.
- Không có SLA GC hoàn tất trong 10 ms, 1 s hay số vòng lặp cố định.
- Queue notification có thể đến muộn hơn việc clearing references.
- Vì vậy ví dụ học có stdout cố định ở trên **chủ động dùng `clear()` và `reachabilityFence`**, thay vì cam kết lịch trình GC.

## 9. `ReferenceQueue<T>`: nhận reference đã đăng ký, không lấy object chết

Reference queue hỗ trợ lập trình **phản ứng** với những thay đổi reachability.

Constructor phổ biến:

```java
ReferenceQueue<Resource> queue = new ReferenceQueue<>();
WeakReference<Resource> ref = new WeakReference<>(resource, queue);
```

Đoạn trên là minh họa cần `Resource` và `resource` phù hợp. Queue nhận **reference object** sau khi được JVM enqueue; không phải queue chứa một bản sao referent.

Các API quan trọng của Java 17:

| API | Ý nghĩa |
| --- | --- |
| `new ReferenceQueue<T>()` | Tạo queue |
| `queue.poll()` | Lấy ngay một enqueued reference hoặc `null` nếu queue trống |
| `queue.remove()` | Chờ tới khi có reference, có thể bị interrupt |
| `queue.remove(timeout)` | Chờ tối đa timeout milliseconds, trả reference hoặc `null` |
| `new WeakReference<>(obj, queue)` | Đăng ký weak reference với queue |
| `new SoftReference<>(obj, queue)` | Đăng ký soft reference với queue |
| `new PhantomReference<>(obj, queue)` | Đăng ký phantom reference với queue |
| `ref.clear()` | Xóa referent, **không** tự enqueue |
| `ref.enqueue()` | **Chủ động** clear và enqueue reference đã đăng ký, nếu phù hợp |
| `ref.refersTo(obj)` | Kiểm tra identity của referent, kể cả với phantom reference |

Không phụ thuộc `Reference.isEnqueued()`: Java 17 đã **deprecated** method này vì hành vi/test race; dùng kết quả `poll`/`remove` và cấu trúc quản lý riêng.

### 9.1. Demo queue không phụ thuộc collector

Tạo file **`ReferenceQueueDemo.java`**:

```java
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class ReferenceQueueDemo {
    public static void main(String[] args) {
        Object target = new Object();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        WeakReference<Object> ref = new WeakReference<>(target, queue);

        System.out.println("before=" + (queue.poll() == null));
        System.out.println("manualEnqueue=" + ref.enqueue());
        System.out.println("cleared=" + (ref.get() == null));
        System.out.println("sameRef=" + (queue.poll() == ref));
        System.out.println("emptyAfterPoll=" + (queue.poll() == null));
        java.lang.ref.Reference.reachabilityFence(target);
    }
}
```

Lệnh:

```bash
javac --release 17 ReferenceQueueDemo.java
java ReferenceQueueDemo
```

Stdout:

```text
before=true
manualEnqueue=true
cleared=true
sameRef=true
emptyAfterPoll=true
```

Điểm cần nhấn mạnh: `ref.enqueue()` là **thao tác thủ công** để kiểm thử cơ chế queue, không mô phỏng collector thực sự đã xác nhận target weakly reachable. Việc `ref.get()==null` ở đây là kết quả explicit enqueue/clearing, dù `target` còn strongly reachable.

### 9.2. Phải giữ bản thân reference object sống

Package specification nhấn mạnh: `ReferenceQueue` **không tự theo dõi và giữ mọi reference đã đăng ký**. Nếu `WeakReference`/`PhantomReference` object trở nên unreachable trước khi GC muốn enqueue, ứng dụng **không còn gì để quan sát**.

Vì vậy code làm tracking thường phải giữ các reference objects trong một collection mạnh cho đến khi xử lý xong:

```java
ReferenceQueue<Resource> queue = new ReferenceQueue<>();
Set<PhantomReference<Resource>> liveRefs = new HashSet<>();
PhantomReference<Resource> ref = new PhantomReference<>(resource, queue);
liveRefs.add(ref);
```

Đây là **snippet**, cần các imports, `Resource` và biến `resource`. Khi lấy reference từ queue, chương trình tìm thông tin cleanup tương ứng, xóa entry khỏi `liveRefs` và thực hiện công việc tài nguyên (không gọi `get()` của phantom để lấy resource).

Cần rất cẩn thận: **map từ phantom ref sang cleanup state không được vô tình giữ strong reference tới referent** — nếu có, đối tượng sẽ không đến trạng thái phantom-reachable để kích hoạt cleanup.

## 10. `Cleaner`: thay vì tự quản lý phantom queue thấp cấp

`java.lang.ref.Cleaner` (Java 9+) quản lý **phantom references + reference queue + cleaning actions** để giảm boilerplate. `Cleaner.register(object, action)` trả về `Cleanable` có method `clean()`.

Theo Javadoc:

- Cleaning action có thể chạy **sau khi referent trở thành phantom-reachable**, trừ khi đã chạy bằng explicit `clean()`.
- Một action được chạy **tối đa một lần** qua Cleanable.
- Action **không được strong-capture object được đăng ký**; nếu bắt giữ chính object, object đó không còn phantom-reachable và automatic cleanup không diễn ra.
- Cleaner có thread riêng để chạy cleanup theo cấu hình; thời điểm automatic cleanup không bảo đảm và hành vi khi `System.exit` là implementation-specific.
- **Gọi `clean()` chủ động trong `close()`** là cách hữu ích, nhưng vẫn phải thiết kế resource lifetime/ownership chính xác.

### 10.1. Ví dụ `Cleaner` và `AutoCloseable` chạy được, không phụ thuộc GC

Tạo file **`CleanerDemo.java`**:

```java
import java.lang.ref.Cleaner;

public class CleanerDemo {
    private static final Cleaner CLEANER = Cleaner.create();

    private static final class State implements Runnable {
        @Override
        public void run() {
            System.out.println("released");
        }
    }

    static final class Resource implements AutoCloseable {
        private final Cleaner.Cleanable cleanable =
                CLEANER.register(this, new State());

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("using");
        }
        System.out.println("closed");
    }
}
```

Chạy:

```bash
javac --release 17 CleanerDemo.java
java CleanerDemo
```

Stdout chính xác:

```text
using
released
closed
```

`Resource` được đóng **tường minh** bởi try-with-resources, nên `close()` gọi `cleanable.clean()` ở thời điểm rõ ràng và action in một lần. Không dùng thời điểm GC để quyết định stdout.

Chương trình chỉ **in một dòng giả lập cleanup**, không chứng minh việc giải phóng một socket/file descriptor thực. Trong production, `State` cần giữ resource state cần thiết (ví dụ native handle) và action an toàn, idempotent, không giữ strong reference tới `Resource`.

### 10.2. Sai lầm capture `this` trong cleaning action

**Sai**:

```java
cleanable = CLEANER.register(this, () -> closeNativeField());
```

Nếu lambda gọi instance method/field của object đang đăng ký, nó thường **capture `this`**. Cleaner giữ action, action giữ object, và object không thể phantom-reachable; automatic cleanup vì vậy không bao giờ chạy theo kỳ vọng.

**Đúng hơn**: tạo một `static` nested `State implements Runnable` chứa handle/metadata độc lập với owner; đăng ký `new State(handle)` và bảo đảm reference graph không dẫn ngược về owner.

### 10.3. Explicit resource management vẫn là chuẩn

`Cleaner` là cơ chế dự phòng hữu ích khi resource bị bỏ quên, **không thay thế**:

- `try-with-resources` cho stream, file, socket, DB connection và `AutoCloseable`.
- `finally` để giải phóng tài nguyên theo deterministic scope.
- Chính sách `close` idempotent/thread-safe và chuyển ownership được xác định rõ.
- Quản lý native handle sao cho không có use-after-close.

Chờ GC để đóng kết nối mạng có thể khiến file descriptors cạn trước khi collector quyết định xử lý. Không thiết kế business correctness phụ thuộc vào việc Cleaner phải chạy trước một deadline.

## 11. Sai lầm lớn: reference object còn sống không có nghĩa referent còn sống

Một trong những graph phổ biến:

```text
static Set<WeakReference<Client>> tracked
       │
       ├─ strong ──► WeakReference #1 ── weak ──► Client A
       ├─ strong ──► WeakReference #2 ── weak ──► Client B
       └─ strong ──► WeakReference #3 ── weak ──► Client C
```

Ứng dụng **giữ wrapper refs mạnh** để theo dõi queue nhưng không vì thế bắt GC phải giữ Client objects sống. Nếu không có strong paths khác tới `Client B`, weak reference có thể bị clear. Điều này chính là mục tiêu của weak tracking.

Tuy nhiên nếu value trong side map, listener list hoặc cleanup state lại trỏ strong về Client, tình huống đổi thành:

```text
static trackingData ──strong──► CleanupState
                                   │
                                   └─strong──► Client B
```

Lúc này Client B vẫn strongly reachable dù có weak/phantom refs. Muốn tìm memory leak, **đọc toàn bộ incoming reference chains tới object**, không chỉ xem người viết đã dùng `WeakReference` ở một field.

## 12. Phân biệt `Reference.get()`, `clear()`, `enqueue()` và `refersTo()`

| Method | Soft/Weak | Phantom | Điều cần nhớ |
| --- | --- | --- | --- |
| `get()` | Trả referent hoặc `null` sau clear | **Luôn `null`** | Không chứng minh object bị thu hồi hay GC đã chạy |
| `refersTo(obj)` | So identity của referent theo API | Cũng hỗ trợ, Java 16+ | Dùng được khi phantom.get() không cho lấy object |
| `clear()` | Chủ động clear referent | Chủ động clear referent | Không enqueue khi gọi `clear()` |
| `enqueue()` | Chủ động clear và enqueue nếu reference đã đăng ký/phù hợp | Tương tự theo API chung | Manual action, không chứng minh collector phát hiện trạng thái |
| `queue.poll()` | Lấy reference đã enqueue | Lấy reference đã enqueue | Không lấy lại referent |
| `queue.remove(timeout)` | Chờ notification có giới hạn | Chờ notification có giới hạn | Có thể timeout, không bảo đảm event xảy ra |

**Không tự viết vòng chờ vô hạn**:

```java
while (weak.get() != null) {
    System.gc();
}
```

Đoạn trên có thể không kết thúc, đốt CPU, gây GC pressure và vẫn không chứng minh gì về thời điểm thu hồi. Các kiểm thử GC nên tách khỏi unit tests đòi deterministic stdout và phải có timeouts/chấp nhận bất định rõ ràng.

## 13. Một đối tượng có thể “sống lại” sau finalization không?

Trong cơ chế finalization lịch sử, một finalizer có thể tạo strong reference mới tới object, gây **resurrection**. Điều này phức tạp hóa reachability và lifecycle; Java finalization bị deprecated từ Java 9 và deprecated for removal bởi JEP 421 trong JDK 18, vì nhiều lý do an toàn/hiệu năng.

Với Java 17:

- Đừng thiết kế code mới dựa trên `Object.finalize()` hay một thứ tự gọi finalizers tự giả định.
- Chấp nhận rằng GC/ref-processing semantics chịu ảnh hưởng các trạng thái vòng đời được JVMS/Javadoc mô tả.
- `PhantomReference.get()` luôn `null` nên không cung cấp cách resurrect referent.
- `Cleaner` và `AutoCloseable` đáng tin cậy hơn về thiết kế, trong đó **explicit close** mới cung cấp thời điểm rõ ràng cho tài nguyên quan trọng.
- Khi đọc tài liệu Java 8, thận trọng với mô tả `PhantomReference` có thể khác phiên bản Java 17 về chuyện clear/enqueue.

Nếu chương trình chứa legacy finalization, cần kiểm tra các đường strong-reference resurrection trước khi giải thích vì sao một object vẫn tồn tại hoặc vì sao queue notification chậm.

## 14. Chẩn đoán thực tế: memory leak hay GC hợp lệ?

### 14.1. Sự khác nhau giữa GC không chạy và object còn strong-reachable

Nếu `WeakReference.get()` vẫn trả object, có thể là:

- Object thực sự có **strong path** từ static cache, thread, event listener hoặc JNI global reference.
- Có **soft reference** khác đang giữ object ở mức softly reachable.
- GC chưa thực hiện xử lý weak refs trong điều kiện thích hợp.
- Một strong local đã được tạo bởi `weak.get()` đang giữ object trong lúc nó được dùng.
- Test tự giữ object bằng biến/lambda/log statement để kiểm tra identity và vô tình ngăn collection.

Đừng kết luận “WeakReference bị bug” chỉ vì sau một lần `System.gc()` vẫn thấy `get() != null`.

### 14.2. Khi `WeakHashMap` không giảm kích thước

Kiểm tra:

- **Values → keys** reference cycles xuất phát từ map root.
- Keys có nằm trong static collection khác không?
- Keys có là String literal/interned values hoặc các objects sống lâu khác không?
- Code có giữ iterator/list snapshot và references tạm không?
- Có thực sự cần weak keys, hay nghiệp vụ cần explicit removal/TTL?

Ngay cả khi Map entries biến mất, điều đó không chứng minh không còn leak ở object khác. Memory leak analysis phải xem **retained paths** trong heap dump.

### 14.3. Công cụ Java 17 thường dùng

```bash
# Xem phiên bản JVM:
java -version

# HotSpot heap class histogram tại thời điểm kiểm tra:
jcmd <pid> GC.class_histogram

# Khi được phép và môi trường phù hợp, tạo heap dump:
jcmd <pid> GC.heap_dump /path/to/heap.hprof

# Nhìn cờ VM thực tế đang dùng:
java -XX:+PrintFlagsFinal -version

# GC logging theo tag có sẵn trên bản HotSpot:
java -Xlog:gc*=info YourApp
```

Lưu ý:

- Heap dump/histogram có overhead lớn và có thể gây pause; chọn thời điểm và quyền truy cập phù hợp.
- Heap dump chứa object data **nhạy cảm**; không chia sẻ bừa.
- Không có một flag `-Xlog:weakreferencealways=true` chuẩn chung cho mọi JVM; xem các tags hỗ trợ của phiên bản VM thực tế nếu cần phân tích reference processing.
- `System.gc()` là request, không phải công cụ điều khiển lịch GC với deadline tuyệt đối.
- G1, ZGC, Shenandoah và các collectors/JDK releases có các chiến lược reference processing khác nhau; **public Javadoc semantics** vẫn là nguồn đáng dựa vào hơn một timing cụ thể của HotSpot.

## 15. Bảng chọn cơ chế theo bài toán

| Bài toán | Nên cân nhắc | Vì sao | Cần tránh |
| --- | --- | --- | --- |
| Dữ liệu nghiệp vụ đang dùng, bắt buộc còn tồn tại | **Strong reference** | Giữ object truy cập được khi cần | Đổi sang weak chỉ để “tiết kiệm RAM” |
| Cache tái tạo được, có thể mất khi thiếu memory | **SoftReference** hoặc cache có chính sách rõ | Soft cho GC cơ hội bỏ giá trị | Coi soft là TTL/eviction policy đáng tin |
| Map metadata đi theo vòng đời đối tượng key | **WeakHashMap** / weak keys | Không giữ keys chỉ vì metadata | Value giữ strong reference ngược tới key |
| Canonicalization không muốn giữ tất cả objects sống | **WeakReference** và bảng quản lý thích hợp | Canonical entries có thể bị bỏ | Trộn lẫn với `String.intern()` contract của JVM |
| Thông báo khi referent không còn accessible | **PhantomReference + ReferenceQueue** | Có tín hiệu cleanup mà không lấy lại object | `phantom.get()` hoặc action giữ strong referent |
| Cleanup tài nguyên native/file theo vòng đời | **AutoCloseable + try-with-resources**, có thể bổ sung Cleaner | Explicit close deterministic; Cleaner fallback | Đợi GC như chính sách đóng duy nhất |
| Thu hồi những native buffers do application quản lý | Explicit release, `Cleaner` khi phù hợp | Ownership rõ ràng | Trông GC tự `free` tùy tiện mọi native memory |
| Cache có TTL, LRU, metrics, max size | Bounded cache/explicit lifecycle | Có chính sách kiểm soát | Dùng soft refs như một cache framework đầy đủ |

Không có “loại reference tốt nhất”. Câu hỏi đầu tiên phải là **object cần sống vì lý do nghiệp vụ đến khi nào?** Sau đó mới chọn chính sách GC tương thích.

## 16. Các hiểu nhầm thường gặp

1. **“StrongReference là class trong `java.lang.ref`.”** Sai: strong reference là reference thông thường.
2. **“Có `WeakReference` trỏ tới object nghĩa là object sẽ bị GC ngay.”** Sai: strong/soft paths khác và GC timing quyết định.
3. **“Soft reference chỉ bị clear đúng lúc OOME.”** Sai: GC có thể xóa sớm tùy memory demand/policy.
4. **“Soft cache bảo đảm không bao giờ OOME.”** Sai: strong live data, native memory và nhiều nguyên nhân khác vẫn gây OOME.
5. **“WeakReference tự gọi `clear()` sau đúng N giây.”** Sai: không có thời gian cố định.
6. **“`System.gc()` chắc chắn clear hết weak refs trước khi trả về.”** Sai: không có timing guarantee như vậy.
7. **“`weak.get()` trả object thì cứ gọi lại bao nhiêu lần cũng an toàn.”** Sai: luôn giữ strong local trong lúc sử dụng.
8. **“`WeakHashMap` dùng weak cho cả keys và values.”** Sai: values thường là strong.
9. **“WeakHashMap luôn xóa key dù value trỏ ngược lại key.”** Sai: value có thể giữ strong path đến key.
10. **“`PhantomReference.get()` có lúc trả object trước GC.”** Sai: Java 17 luôn trả `null`.
11. **“Phantom reference giữ được resource để gọi `close()` khi GC tới.”** Sai: không lấy referent qua `get()`.
12. **“`ReferenceQueue` trả trực tiếp đối tượng đã thu hồi.”** Sai: queue chứa **reference objects**.
13. **“`clear()` và `enqueue()` là cùng một thao tác.”** Sai: clear riêng không enqueue.
14. **“Khi queue trống nghĩa là tất cả referents vẫn còn sống.”** Sai: có thể GC chưa enqueue, reference chưa đăng ký hoặc queue đã được đọc.
15. **“Không cần giữ reference wrapper vì queue tự giữ nó.”** Sai: phải đảm bảo reference object reachable.
16. **“Cleaner luôn chạy lúc JVM shutdown.”** Sai: không có guarantee.
17. **“Để lambda Cleaner capture `this` là tiện nhất.”** Sai: có thể giữ referent sống mãi.
18. **“Cleaner thay thế hoàn toàn `close()` và try-with-resources.”** Sai: cleanup do GC không có deadline.
19. **“Biến local hết scope nghĩa là resource luôn sống đến dấu ngoặc cuối.”** Sai: compiler/JIT liveness có thể kết thúc trước đó.
20. **“`reachabilityFence` là lệnh chạy GC.”** Sai: nó chỉ thiết lập strong-reachability đến điểm fence.
21. **“Dùng weak ref là cách sửa mọi memory leak.”** Sai: object graph có thể vẫn giữ strong paths khác.
22. **“JDK 8 và 17 có mọi chi tiết PhantomReference clearing giống nhau.”** Không đúng khi áp dụng các mô tả lịch sử; đọc docs đúng JDK.

## 17. Tự kiểm tra và đáp án

**Câu 1.** Nếu một object còn được tham chiếu qua một static field thông thường, một `WeakReference` trỏ tới nó có cho phép GC thu hồi object không?

**Đáp án:** Không chỉ vì weak wrapper. Static strong path còn sống khiến referent strongly reachable.

**Câu 2.** Có cả soft và weak refs cùng trỏ một object, nhưng không có strong path. Mức reachability là gì?

**Đáp án:** **Softly reachable**, vì soft mạnh hơn weak. GC có thể clear soft theo policy rồi xử lý trạng thái yếu hơn.

**Câu 3.** `SoftReference.get()` trả `null` nghĩa là OOME vừa xảy ra?

**Đáp án:** Không. Soft có thể bị clear trước OOME hoặc bị chương trình chủ động `clear()`.

**Câu 4.** `WeakReference.get()` có thể đang trả object, lần sau trả `null` không?

**Đáp án:** Có. Nếu không giữ strong reference tới referent giữa hai lần và GC xử lý, reference có thể bị clear.

**Câu 5.** Vì sao `WeakMapDemo` vẫn in `size=1` sau khi `key=null`?

**Đáp án:** Map giữ `Value` mạnh, `Value.owner` giữ key mạnh, nên weak key không phải đường sống duy nhất. `clear()` của map xóa entry chủ động.

**Câu 6.** `PhantomReference.get()` trả gì khi target còn mạnh reachable?

**Đáp án:** **`null`**, vì đó là contract cố định của `PhantomReference.get()` trong Java 17.

**Câu 7.** Tại sao phải giữ `PhantomReference` object trong một collection khi dùng `ReferenceQueue`?

**Đáp án:** Nếu wrapper reference object tự trở nên unreachable, chương trình có thể không nhận notification. Queue không tự quản lý strong registration cho mọi refs.

**Câu 8.** `Reference.clear()` có làm reference xuất hiện ngay trong queue không?

**Đáp án:** Không. `clear()` đơn lẻ không enqueue; `enqueue()` hoặc collector processing mới enqueue theo hợp đồng.

**Câu 9.** Trong `CleanerDemo`, vì sao stdout là deterministic mà không cần gọi `System.gc()`?

**Đáp án:** Vì try-with-resources gọi `Resource.close()`, và `close()` gọi `Cleanable.clean()` đồng bộ. Automatic GC cleanup chỉ là fallback.

**Câu 10.** Vì sao lambda cleanup `() -> closeNativeField()` có thể khiến Cleaner không bao giờ chạy tự động?

**Đáp án:** Nó thường capture `this` (owner), trong khi Cleaner giữ cleanup action mạnh, tạo strong path tới referent.

**Câu 11.** Khi native code dùng resource handle nhưng Java wrapper không còn được đọc, API nào có thể giúp duy trì strong reachability tới sau native call?

**Đáp án:** `Reference.reachabilityFence(wrapper)` đặt sau native operation, đúng nơi bảo đảm wrapper còn strongly reachable.

**Câu 12.** Dùng `WeakReference` có tự làm collection thread-safe không?

**Đáp án:** Không. Cấu trúc dữ liệu quản lý vẫn cần synchronization/concurrency policy riêng.

## 18. Tóm tắt bằng một sơ đồ

```text
Một object O trên heap
    ▲
    │
    ├── strong edge  → sống khi strong path còn
    │
    ├── soft edge    → GC có thể clear khi memory demand
    │
    ├── weak edge    → không giữ O sống sau khi hết strong/soft paths
    │
    └── phantom edge → chỉ thông báo lifecycle; get() luôn null

ReferenceQueue:
    Weak/Soft/Phantom ref ──(enqueue khi phù hợp)──► Queue
                                                        │
                                              poll()/remove()
                                                        │
                                              xử lý reference object
                                              KHÔNG lấy lại referent

Cleaner:
    Phantom/reference processing + cleanup action
      ├─ auto cleanup: không bảo đảm thời điểm
      └─ explicit clean(): có thể chạy đúng lúc close()

Reference.reachabilityFence:
    Giữ strong reachability đến vị trí gọi,
    không buộc GC hay thay thế đóng tài nguyên.
```

**Ghi nhớ:** Strong refs dành cho đối tượng còn đang dùng; Soft refs có thể làm cache phụ thuộc memory demand; Weak refs thích hợp cho metadata/canonical mappings không được phép giữ object sống; Phantom refs là tín hiệu sau khi object không còn truy cập thông thường, **không phải cửa sau để gọi method của object**. `ReferenceQueue` liên kết reference wrappers với một kênh thông báo, `Cleaner` cung cấp abstraction cleanup, còn `try-with-resources` mới là cách đáng tin cậy để đóng tài nguyên theo thời gian xác định. Luôn phân biệt **reachability của referent**, **lifetime của reference object**, **lifetime của tài nguyên ngoài heap** và **thời điểm GC thực thi**.

### Tài liệu tham khảo chuẩn

- [Java SE 17 — `java.lang.ref` package / Reachability](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html): các mức strong, soft, weak, phantom và notification.
- [Java SE 17 — `Reference` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html): `get`, `clear`, `enqueue`, `refersTo`, `reachabilityFence`.
- [Java SE 17 — `SoftReference` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/SoftReference.html): memory demand và OOME caveat.
- [Java SE 17 — `WeakReference` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): clear/enqueue semantics.
- [Java SE 17 — `PhantomReference` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/PhantomReference.html): `get() == null` và phantom reachability.
- [Java SE 17 — `ReferenceQueue` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/ReferenceQueue.html): polling và blocking notification.
- [Java SE 17 — `Cleaner` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html): phantom-backed cleanup và rủi ro action capture.
- [Java SE 17 — `WeakHashMap` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html): weak keys, strong values, cycles.
- [JLS Java SE 17 — Finalization of Class Instances (§12.6)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.6).
- [JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-005">ReferenceQueue và thời điểm thông báo đối tượng đã được xử lý tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# ReferenceQueue và thời điểm thông báo đối tượng đã được xử lý tham chiếu

## 1. ReferenceQueue dùng để quan sát điều gì?

`java.lang.ref.ReferenceQueue<T>` là hàng đợi nhận **đối tượng tham chiếu** (`Reference`), không phải nhận object gốc (*referent*) đã được theo dõi. Khi một `WeakReference`, `SoftReference` hoặc `PhantomReference` được đăng ký với queue và GC nhận biết thay đổi reachability thích hợp, reference có thể được **clear** rồi **enqueue**; code Java dùng `poll()` hoặc `remove()` để **dequeue** đối tượng `Reference` ấy.

Cần phân biệt các sự kiện theo trình tự suy luận:

1. **Registration:** ứng dụng tạo `new WeakReference<>(referent, queue)` hoặc một loại reference thích hợp khác. Registration không đồng nghĩa enqueue.
2. **Reachability thay đổi:** object không còn đường tham chiếu mạnh (*strong reachability*) từ GC roots; tuy nhiên có thể vẫn chịu ảnh hưởng của soft/weak/phantom references.
3. **GC phát hiện:** garbage collector kiểm tra khả năng truy cập vào một thời điểm tùy collector và workload. Gán biến bằng `null` không khiến GC buộc phải chạy ngay.
4. **Clearing:** reference bị xử lý và không còn trả referent qua `get()`, tùy loại reference. Việc clearing và việc enqueue không nhất thiết là cùng một thao tác hoặc cùng một thời điểm.
5. **Enqueue:** reference object được đưa vào queue; một reference chưa đăng ký queue thì không tự xuất hiện tại đây.
6. **Dequeue:** application lấy reference đã được enqueue và đọc metadata của **reference object**, rồi cập nhật cache hoặc cleanup resource tương ứng.

~~~text
Strong references biến mất
           |
           v
GC có thể phát hiện reachability thay đổi
           |
           v
Xử lý/clear các Reference đã đăng ký
           |
           v
Đưa Reference object vào ReferenceQueue
           |
           v
Application poll() / remove()
           |
           v
Dọn cache entry, xử lý metadata, cleanup nếu phù hợp
~~~

**Điều một event KHÔNG chứng minh:** không bảo đảm GC đã trả RAM về OS, không chứng minh byte memory đã được tái sử dụng, không phải thông báo có object gốc còn truy cập được, không bảo đảm native resource đã được đóng, và **không có deadline** từ thời điểm object mất strong references đến lúc ứng dụng nhận event.

Cần nhớ thêm: `Reference.enqueue()` có thể được **chính application gọi thủ công**. Nếu queue có một entry, không thể chỉ nhìn entry đó mà kết luận chắc chắn event đến từ GC.

## 2. Reachability: strong, soft, weak và phantom

Java định nghĩa các mức truy cập object liên quan đến GC, từ mạnh tới yếu:

| Mức / loại | Cách hiểu đúng | Có lấy referent qua `get()`? |
| --- | --- | --- |
| Strong reference | Đường tham chiếu mạnh thông thường từ thread, static field hoặc cấu trúc reachable | Truy cập object trực tiếp |
| `SoftReference<T>` | Không ngăn GC clear khi memory demand/chính sách yêu cầu, có thể phù hợp cho memory-sensitive caches | Có trước khi bị clear |
| `WeakReference<T>` | Không giữ object sống chỉ vì có weak reference; GC xử lý khi referent chỉ còn weakly reachable | Có trước khi bị clear |
| `PhantomReference<T>` | Mức rất yếu dùng để nhận notification/cleanup, không cho chương trình khôi phục referent | **Không**: `get()` luôn `null` |

**Strong references** là điều kiện quan trọng: nếu một static map, thread, cache value hoặc biến local vẫn giữ object qua đường mạnh, object chưa thể được coi là weakly reachable chỉ vì bạn vừa tạo một `WeakReference` tới nó.

**SoftReference** cho GC nhiều lựa chọn hơn: không hứa cứ hết strong references là bị clear tức thì. Đối với production cache cần bounded memory, nên đặt size/TTL/eviction thay vì dựa vào soft reference như cơ chế giới hạn duy nhất.

**WeakReference** thích hợp theo dõi weak keys, canonicalization hoặc các đối tượng mà hệ thống không muốn giữ sống. Nó vẫn cần GC chạy và phát hiện reachability. `weak.get() == null` có thể được quan sát trước khi reference object được queue trả về.

**PhantomReference** thường dùng phối hợp cleanup sau khi GC xác định referent đã đến trạng thái phù hợp. `phantom.get() == null` **ngay từ lúc tạo**, ngay cả nếu referent còn được strong-reference. Vì vậy không sử dụng `get()` để xác định thời điểm phantom notification.

Một loại **reference object** bản thân nó vẫn là Java object; nếu ứng dụng bỏ reference object trước khi GC xử lý thì thông báo có thể bị mất. Hệ quả này cực kỳ quan trọng cho thiết kế.

## 3. Registration là một chiều: queue không giữ danh sách đăng ký

Xem ví dụ:

~~~java
ReferenceQueue<Object> queue = new ReferenceQueue<>();
Object value = new Object();
WeakReference<Object> weak = new WeakReference<>(value, queue);
~~~

- `value` là object gốc (*referent*).
- `weak` là `WeakReference` object; nó nắm liên hệ **yếu** tới `value`.
- `queue` là nơi nhận **weak**, nếu GC hoặc application enqueue weak.
- `queue.poll()` trả một `Reference<? extends Object>` hoặc `null`; không trả `value`.

Đặc tả package `java.lang.ref` nói rõ mối quan hệ của một reference đã đăng ký với queue là **một chiều**: queue không được hiểu như một registry tự giữ mạnh tất cả reference đã đăng ký.

Lối viết sau **không đáng tin** nếu muốn nhận event:

~~~java
new WeakReference<>(value, queue); // không giữ WeakReference object
~~~

Trong code thực, hãy giữ **reference object** ở collection/registry cho tới khi event được xử lý hoặc registration bị hủy. Nhưng **không** giữ referent bằng một strong field khác, nếu mục tiêu là để object đó có thể được GC.

~~~text
Strong registry ──> TrackedWeakReference ──weak──> key object
       |
       └──> metadata (ID, cache key, native handle nếu phù hợp)

Registry được phép giữ reference object.
Registry KHÔNG được vô tình giữ key object qua đường mạnh
nếu muốn key trở nên weakly reachable.
~~~

**Sai lầm:** một subclass `WeakReference<T>` chứa `final T original` sẽ giữ referent sống; constructor dùng `super(original, queue)` không thể vô hiệu hóa strong field ấy.

## 4. API của ReferenceQueue và Reference cần phân biệt

| API | Ý nghĩa | Điều KHÔNG được suy ra |
| --- | --- | --- |
| `queue.poll()` | Thử lấy một reference sẵn có **không chờ** | `null` không chứng minh referent còn sống |
| `queue.remove()` | Chờ và lấy reference khi xuất hiện | Có thể chờ vô hạn nếu không có event |
| `queue.remove(timeoutMillis)` | Chờ tối đa theo timeout; có thể trả `null` | Không buộc GC phải enqueue trong timeout |
| `ref.get()` | Lấy referent nếu API cho phép và chưa bị clear | `null` không chứng minh reference đã enqueue |
| `ref.clear()` | Clear reference do Java code yêu cầu | **Không tự enqueue** |
| `ref.enqueue()` | Thử enqueue bằng Java code và trả boolean | Event có thể **không do GC** tạo |
| `ref.refersTo(object)` | Hỏi reference còn chỉ tới đúng object ấy hay không | Không phải công cụ ép GC |
| `ref.isEnqueued()` | Trạng thái queue ở một thời điểm | **Deprecated Java 16+**, có race, không dùng làm cleanup protocol |
| `Reference.reachabilityFence(x)` | Giữ `x` strongly reachable tới điểm gọi | Không dọn GC, không enqueue |

Với `remove(long timeout)`:

- `timeout > 0`: chờ có giới hạn (milliseconds), trả `null` nếu không nhận được reference trong thời gian chờ.
- `timeout == 0`: **chờ vô hạn**, không phải non-blocking.
- Muốn thử mà không chờ: **`poll()`**.
- Một thread bị interrupt khi đang chờ có thể nhận `InterruptedException`; consumer phải quản lý shutdown đúng cách.

### `clear` khác `enqueue`

~~~text
ref.clear()
   |
   +-- ref.get() có thể thành null
   +-- queue KHÔNG tự có entry chỉ vì clear()

ref.enqueue()
   |
   +-- application yêu cầu thêm reference vào queue
   +-- có thể xảy ra khi object gốc còn strong
   +-- trong Java 17 thao tác này cũng clear reference
~~~

GC có thể clear reference ở một thời điểm rồi enqueue ở thời điểm khác. Không mặc định rằng một `weak.get() == null` phải kèm `queue.poll() != null` **ngay tức khắc**.

## 5. Ví dụ Java 17 chạy được, stdout xác định: manual enqueue

Ví dụ đầu **cố ý không chờ GC** để cô lập ngữ nghĩa API. Tạo file **`ReferenceQueueBasicsDemo.java`**:

~~~java
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public final class ReferenceQueueBasicsDemo {
    public static void main(String[] args) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object referent = new Object();

        WeakReference<Object> weak = new WeakReference<>(referent, queue);
        PhantomReference<Object> phantom =
                new PhantomReference<>(referent, queue);

        System.out.println("weakAlive=" + (weak.get() != null));
        System.out.println("phantomGetNull=" + (phantom.get() == null));
        System.out.println("initialQueueEmpty=" + (queue.poll() == null));

        weak.clear();
        System.out.println("weakCleared=" + (weak.get() == null));
        System.out.println("clearDidNotEnqueue=" + (queue.poll() == null));

        boolean manualWeak = weak.enqueue();
        Reference<?> first = queue.poll();
        System.out.println("manualWeakEnqueued=" + manualWeak);
        System.out.println("firstIsWeak=" + (first == weak));

        boolean manualPhantom = phantom.enqueue();
        Reference<?> second = queue.poll();
        System.out.println("manualPhantomEnqueued=" + manualPhantom);
        System.out.println("secondIsPhantom=" + (second == phantom));

        Reference.reachabilityFence(referent);
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 ReferenceQueueBasicsDemo.java
java ReferenceQueueBasicsDemo
~~~

**Stdout chính xác đã kiểm tra bằng JDK 21 với `--release 17`:**

~~~text
weakAlive=true
phantomGetNull=true
initialQueueEmpty=true
weakCleared=true
clearDidNotEnqueue=true
manualWeakEnqueued=true
firstIsWeak=true
manualPhantomEnqueued=true
secondIsPhantom=true
~~~

### Giải thích

1. `weak.get() != null` vì `referent` vẫn được giữ mạnh, đồng thời `reachabilityFence(referent)` đảm bảo không thể xem object là hết dùng trước điểm cuối.
2. Phantom `get()` luôn null; không có bằng chứng object đã bị GC.
3. Chỉ đăng ký reference với queue không enqueue.
4. `weak.clear()` làm weak `get()` thành null nhưng `poll()` vẫn null.
5. `weak.enqueue()` trả true và `poll()` trả đúng `weak` object.
6. `phantom.enqueue()` trả true và `poll()` trả đúng `phantom` object.
7. Hai lần enqueue là hành động **thủ công**, trong khi referent **chưa chết** theo nghĩa GC. Đây là lý do event không được tự động gọi là `OBJECT_COLLECTED`.

Vì ví dụ không phụ thuộc collector, nên stdout **không cần** `System.gc()` và không có vòng đợi bất định.

## 6. Ví dụ quan sát GC: không được hứa stdout cố định

Bài này có code Java 17 hoàn chỉnh, nhưng **chủ ý không viết một kết quả duy nhất**, vì lịch chạy GC thay đổi theo JVM. Tạo **`WeakQueueObservationDemo.java`**:

~~~java
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public final class WeakQueueObservationDemo {
    private static WeakReference<byte[]> create(
            ReferenceQueue<byte[]> queue) {
        byte[] payload = new byte[1024 * 1024];
        return new WeakReference<>(payload, queue);
    }

    public static void main(String[] args)
            throws InterruptedException {
        ReferenceQueue<byte[]> queue = new ReferenceQueue<>();
        WeakReference<byte[]> ref = create(queue);

        System.gc(); // Yêu cầu, không phải cam kết GC
        Reference<? extends byte[]> event = queue.remove(1000);

        if (event == ref) {
            System.out.println("REFERENCE_ENQUEUED");
        } else {
            System.out.println("NO_EVENT_WITHIN_TIMEOUT");
        }

        System.out.println("cleared=" + (ref.get() == null));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 WeakQueueObservationDemo.java
java WeakQueueObservationDemo
~~~

Tùy GC, có ít nhất hai kết quả phân nhánh hợp lệ:

- **`REFERENCE_ENQUEUED`** nếu GC phát hiện và enqueue reference trong khoảng đợi.
- **`NO_EVENT_WITHIN_TIMEOUT`** nếu queue chưa có reference sau thời gian đã yêu cầu.

Dòng `cleared=` cũng là quan sát tại một thời điểm: có thể đã clear referent dù chưa enqueue; một số collector có thể chưa xử lý ref, cho nên không được khẳng định mọi lần đều `true`.

**`queue.remove(1000)` đặt thời gian chờ của chương trình, không đặt deadline GC.** `System.gc()` cũng không làm đổi điều này; JVM có thể bỏ qua yêu cầu explicit GC do flags hoặc chính sách.

### Tại sao tạo payload trong method riêng?

Sau khi method `create` trả, không còn một biến cục bộ trong `main` nắm trực tiếp `byte[]` payload. Caller chỉ giữ `WeakReference`, dễ quan sát việc GC xử lý. Nhưng chương trình vẫn không có quyền đòi collector chạy ngay, và những tối ưu reachability của runtime phải được hiểu theo semantics GC chứ không theo phạm vi chữ của biến Java.

## 7. Thời điểm chính xác: cleared, enqueued, dequeued và reclaimed

Một thứ tự **tư duy** hữu ích:

~~~text
t0: object và reference được tạo
t1: đường strong reachability cuối cùng có thể mất
t2: GC phát hiện trạng thái truy cập phù hợp
t3: GC clear reference
t4: GC enqueue reference (nếu đã đăng ký queue)
t5: application dequeue và xử lý

Không có SLA milliseconds bắt buộc t1 -> t2 -> t4 -> t5.
Một số sự kiện có thể cùng thời điểm hoặc không xảy ra
trong lifetime quan sát của tiến trình.
~~~

Đặc biệt:

- **`get() == null` không có nghĩa `isEnqueued() == true`**; chưa enqueue, đã dequeue hoặc clear thủ công đều có thể tạo khác biệt.
- **`poll() == null` không chứng minh object còn sống**; nó chỉ nói hiện không lấy được một entry trong queue.
- **`remove(timeout)` trả reference không khẳng định object đã bị deallocate ngay**; việc xử lý reference không là hóa đơn thu hồi byte RAM.
- **SoftReference** có thêm quyền quyết định của GC trước khi clear, theo memory demand.
- **PhantomReference** thông báo điều kiện phantom reachability phù hợp; `get()` luôn null và việc dọn native resource cần do code đúng quyền sở hữu thực hiện.
- **Manual `enqueue()`** phá giả định “event chỉ do GC”; cần tách event source nếu hệ thống cho phép enqueue thủ công.

Không xây nghiệp vụ cần SLA, giao dịch, locking hoặc resource closure bắt buộc đúng hạn dựa duy nhất vào một sự kiện GC/reference queue.

## 8. `poll()` theo đợt hay worker blocking?

### Poll opportunistic

Khi cache được truy cập, có thể dọn các stale references đang đợi:

~~~java
Reference<?> ref;
while ((ref = queue.poll()) != null) {
    // Dùng metadata của ref để xóa entry trong cache.
}
~~~

Đây là **mảnh code minh họa**, biến `queue` cần được định nghĩa theo dữ liệu cache thực. Cách này không tạo thread riêng; phù hợp một số caches với chi phí polling nhỏ. Nhưng nếu ứng dụng không bao giờ gọi cache nữa, việc cleanup dạng opportunistic có thể chưa diễn ra.

### Worker blocking với timeout

~~~java
while (!Thread.currentThread().isInterrupted()) {
    try {
        Reference<?> ref = queue.remove(500);
        if (ref != null) {
            // Xử lý reference đã dequeue; cleanup idempotent.
        }
    } catch (InterruptedException stop) {
        Thread.currentThread().interrupt();
        break;
    }
}
~~~

Đây cũng là **mảnh minh họa**, không phải chương trình độc lập. `remove(500)` giúp worker định kỳ kiểm tra trạng thái dừng. Với `remove()` không timeout, shutdown phải có cơ chế interrupt rõ.

Nguyên tắc:

- Consumer không nên busy-spin `while (queue.poll() == null) {}` liên tục, vì tốn CPU.
- Đừng tạo một worker non-daemon sống vĩnh viễn và ngăn JVM thoát.
- Khi processing exception xảy ra, không để một event làm chết toàn bộ worker; cần ghi log/giữ trạng thái theo chính sách.
- Nếu nhiều consumer cùng đọc queue, chỉ một consumer dequeue một reference, cần cấu trúc registry thread-safe.
- Sau dequeue, ứng dụng thường **xóa registration/reference object khỏi registry** để tránh leak metadata.

## 9. Dùng ID thay vì cố lấy referent đã bị clear

Khi `WeakReference` được GC clear, `weak.get()` thường trả null; bạn không thể trông chờ gọi `referent.close()` trên object đã không lấy được. Muốn biết cache entry nào cần dọn, lưu ID trên **reference object**.

Một phụ trợ minh họa:

~~~java
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

final class TrackedWeakRef<T> extends WeakReference<T> {
    final long id;

    TrackedWeakRef(T value, ReferenceQueue<? super T> queue, long id) {
        super(value, queue);
        this.id = id;
    }
}
~~~

Class này **không có main**; nó chỉ trình bày cách lưu ID độc lập với referent.

Một cache/registry có thể lưu `TrackedWeakRef` mạnh, lưu `id -> value/metadata` riêng, rồi khi dequeue nhận `TrackedWeakRef` đọc `id` để remove entry. Nếu value/metadata vô tình giữ lại key một cách mạnh, key có thể không bao giờ weakly reachable; điều này cần xét cụ thể.

**Chống ví dụ:**

~~~java
final class BadTrackedRef<T> extends WeakReference<T> {
    final T original; // mạnh, khiến referent vẫn reachable!

    BadTrackedRef(T value, ReferenceQueue<? super T> q) {
        super(value, q);
        original = value;
    }
}
~~~

Mặc dù extends `WeakReference`, field `original` làm hỏng mục đích theo dõi đối tượng theo reference yếu nếu instance BadTrackedRef còn được giữ.

Đối với `WeakHashMap`, hãy nhớ key là weak nhưng value có thể giữ lại key qua một đường mạnh khác. Không coi WeakHashMap như cam kết “mọi entry tự biến mất ngay khi bên ngoài ngừng dùng key”.

## 10. Vì sao PhantomReference thường dùng cho cleanup?

`PhantomReference<T>` được thiết kế để thông báo khi referent đã đủ điều kiện phantom reachability; ứng dụng không thể dùng `phantom.get()` lấy lại object gốc.

~~~text
Resource wrapper ───── owns ─────> Native handle H
       |
       +── PhantomReference<Resource>
                  |
                  +── Metadata độc lập: H, ID, cleanup state
                          |
                  GC enqueue ref khi phù hợp
                          |
                  Consumer đọc metadata
                          |
                  Giải phóng H nếu chưa được close
~~~

Muốn thiết kế kiểu này an toàn:

1. Metadata cleanup **không giữ strong reference tới Resource**; nếu giữ thì Resource không đạt điều kiện GC.
2. `H` phải còn hợp lệ tới khi consumer dọn nó.
3. Cleanup phải **idempotent**, kể cả nếu application đã explicit `close()`.
4. Native callbacks/async operations phải kết thúc trước khi free, nếu không use-after-free.
5. Reference object phải được giữ reachable cho tới khi event được nhận hoặc registration kết thúc.
6. Queue event không phải cam kết OS memory đã được hoàn trả trước khi cleanup action chạy.

Trong nhiều tình huống, dùng `java.lang.ref.Cleaner` dễ hơn tự quản PhantomReference+queue; nhưng **không có deadline cleanup tự động**.

## 11. Cleaner so với `close()` và `try-with-resources`

`Cleaner` (Java 9+) quản lý đăng ký và cleaning actions dựa trên một cơ chế reference thích hợp. Đây là lựa chọn hỗ trợ **fallback cleanup**, không phải thay thế cho **explicit resource ownership**.

Ví dụ Java 17 hoàn chỉnh **`CleanerOwnershipDemo.java`**:

~~~java
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CleanerOwnershipDemo {
    private static final Cleaner CLEANER = Cleaner.create();

    private static final class CleanupState implements Runnable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void run() {
            if (closed.compareAndSet(false, true)) {
                System.out.println("CLEANUP");
            }
        }
    }

    private static final class Resource implements AutoCloseable {
        private final Cleaner.Cleanable cleanable;

        Resource() {
            // Action không capture Resource/this.
            cleanable = CLEANER.register(this, new CleanupState());
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    public static void main(String[] args) {
        try (Resource r = new Resource()) {
            System.out.println("IN_USE");
        }
        System.out.println("DONE");
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 CleanerOwnershipDemo.java
java CleanerOwnershipDemo
~~~

**Stdout cố định nhờ `try-with-resources` gọi `close()` chủ động:**

~~~text
IN_USE
CLEANUP
DONE
~~~

Nếu bỏ `close`, bạn **không được hứa** `CLEANUP` sẽ xuất hiện trước khi process kết thúc; collector và cleaner có thể chưa chạy. CleanupState trong ví dụ là một `Runnable` riêng **không giữ referent**; tạo `CLEANER.register(this, () -> this.close())` có thể tự giữ `this` sống, làm logic cleaner mất ý nghĩa.

Đối với resource quan trọng như connection, socket, file, native buffer có owner rõ, hãy đóng ngay khi hoàn tất. Cleaner có thể làm fallback cho trường hợp programmer quên close, nhưng không tạo safety guarantee cho mọi lỗi double-free/concurrency.

## 12. `Reference.reachabilityFence` và cửa sổ native lifetime

JIT compiler có thể thấy một object không còn được dùng trong các expression Java về sau và cho phép object hết strongly reachable **sớm hơn cuối lexical scope**. Trong JNI/native-resource code, điều này có thể nguy hiểm khi native work còn đang dùng một handle do wrapper quản lý.

`Reference.reachabilityFence(obj)` (Java 9+) giúp đảm bảo `obj` còn strongly reachable tới chỗ gọi:

~~~java
static void useNative(NativeResource resource) {
    try {
        resource.invokeNative();
    } finally {
        java.lang.ref.Reference.reachabilityFence(resource);
    }
}
~~~

Đây **không phải chương trình độc lập** vì `NativeResource` là kiểu mô tả cần có ở application thực. Fence chỉ nói về **lifetime/reachability** của Java object đến mốc đã định. Nó:

- Không phải lệnh gọi GC.
- Không enqueue ReferenceQueue.
- Không cấp phát/free bộ nhớ mới.
- Không thay cho synchronized/atomic để bảo vệ dữ liệu native giữa thread.
- Không cho phép dùng một native pointer sau khi owner đã explicit close/free.

Khi đọc `WeakReference.get()`, hãy lưu **một lần** kết quả thành local mạnh trước khi dùng:

~~~java
Object snapshot = weak.get();
if (snapshot != null) {
    System.out.println(snapshot.toString());
}
~~~

Đây là **mảnh minh họa**, cần biến `weak` được định nghĩa trong scope. Không nên kiểm tra `if (weak.get() != null)` rồi lại gọi `weak.get().method()` lần hai mà tưởng hai kết quả sẽ luôn giống nhau: GC có thể clear reference giữa hai lần đọc.

## 13. Sai lầm thường gặp và hậu quả

1. **“Queue chứa object gốc đã chết.”** Không, chứa **Reference object**.
2. **“Tạo Reference có queue thì queue có ngay event.”** Sai, registration không enqueue.
3. **“`ref.clear()` tự enqueue.”** Sai.
4. **“`ref.enqueue()` chứng minh GC đã chạy.”** Sai: có thể là manual enqueue.
5. **“`weak.get() == null` nghĩa là GC đã enqueue.”** Sai, clearing và enqueue khác nhau.
6. **“`queue.poll() == null` nghĩa là referent còn sống.”** Sai, chỉ nghĩa hiện chưa có entry sẵn.
7. **“`PhantomReference.get() == null` nghĩa object đã GC.”** Sai: luôn trả null.
8. **“`queue.remove(0)` không chờ.”** Sai, đây là chờ vô hạn.
9. **“`System.gc()` buộc event trong 1 giây.”** Sai, không có deadline.
10. **“Queue event xác nhận native memory đã free.”** Sai, cleanup cần bước riêng.
11. **“ReferenceQueue giữ mọi registration sống.”** Sai, cần registry giữ reference objects.
12. **“Subclass WeakReference có field `original` là vô hại.”** Sai, field mạnh ngăn GC referent.
13. **“Cleaner có SLA giống `close()`.”** Sai, timing phụ thuộc GC.
14. **“`isEnqueued()` là cơ chế cleanup tốt.”** Sai, deprecated vì racy.
15. **“`WeakHashMap` value giữ key vẫn không sao.”** Sai, strong back-reference có thể giữ key.
16. **“Thread xử lý queue có thể bỏ qua interrupt.”** Sai, cần shutdown protocol.
17. **“Soft reference chắc bị clear ngay khi bỏ strong refs.”** Sai, GC có quyền giữ tùy memory demand.
18. **“GC notification đủ cho việc release lock/commit transaction.”** Sai, resource quan trọng cần explicit lifecycle.

## 14. Checklist triển khai ReferenceQueue an toàn

- Chọn đúng **loại Reference** (soft/weak/phantom) cho ý nghĩa ứng dụng.
- Tạo reference với **queue phù hợp**, không mặc định queue tự quản danh sách registration.
- Giữ **Reference object** mạnh trong registry; tuyệt đối tránh lưu referent ở cleanup metadata nếu muốn referent eligible.
- Với event, chỉ dùng ID/handle độc lập để xóa entries hoặc cleanup; không cố lấy referent đã cleared.
- Cài consumer bằng `poll()` theo hoạt động cache hoặc `remove(timeout)` trong worker có shutdown/interruption.
- Xử lý race giữa explicit `close()` và event GC, bảo đảm cleanup idempotent.
- Dọn registration khỏi registry khi event đã xử lý để không giữ metadata vô hạn.
- Không dùng `System.gc()` hay `isEnqueued()` làm đồng hồ hoặc giao thức correctness.
- Với native resources, yêu cầu explicit ownership, try-with-resources và kiểm tra use-after-free/double-free.
- Khi cần observability, đo GC logs, reference queue backlog, số registrations đang giữ và tần suất cleanup; không chỉ đếm số object đã mất strong refs.
- Test nhánh **manual enqueue** bằng stdout xác định; test nhánh **GC** phải chấp nhận timeout/non-determinism.
- Đối chiếu phiên bản JDK: các API trong ví dụ biên dịch với `--release 17`.

## 15. Câu hỏi tự kiểm tra

1. `ReferenceQueue.poll()` trả gì? **Reference object hoặc null**, không phải object gốc.
2. `ref.clear()` có làm `queue.poll()` khác null tức thì không? **Không**.
3. `Reference.enqueue()` có thể được Java code tự gọi không? **Có**.
4. `weak.get() == null` có chứng minh đã dequeue reference không? **Không**.
5. `remove(0)` và `poll()` khác gì? **remove(0) chờ vô hạn, poll không chờ**.
6. Vì sao phải giữ chính `WeakReference` trong một registry? **Để không mất reference notification/metadata khi reference object trở nên unreachable**.
7. Tại sao registry không được giữ `T original` của referent? **Vì tạo strong path ngăn weak reachability**.
8. `PhantomReference.get()` trả object khi chưa được GC không? **Không, luôn null**.
9. GC có cam kết enqueue trong 500 ms sau khi object unreachable không? **Không**.
10. Enqueue event có phải native `free` đã hoàn tất không? **Không**.
11. Vì sao cần idempotent cleanup? **Vì có thể vừa explicit close vừa notification cleanup**.
12. `Cleaner` có đảm bảo chạy khi JVM exit bình thường trong mọi hoàn cảnh không? **Không có deadline bảo đảm**.
13. `Reference.reachabilityFence(x)` giữ x tới thời điểm nào? **Tới mốc lời gọi fence**.
14. `WeakHashMap` có thể bị giữ entry do value trỏ ngược key không? **Có**.
15. SoftReference khác WeakReference ở đâu? **GC có thể giữ soft referent lâu hơn theo memory-demand policy**.
16. Nếu cần release socket trước khi HTTP request trả kết quả, dùng queue hay explicit close? **Explicit close/try-with-resources**.
17. Nếu GC clear reference rồi application poll ngay chưa có event, đó có trái API không? **Không**, enqueue có thể diễn ra sau.
18. Nếu class wrapper đã bị GC phát hiện, có thể lấy object ấy từ PhantomReference để gọi close không? **Không**.

## 16. Tài liệu Java SE 17 đáng đọc

- `java.lang.ref` — các cấp reachability, notification, queue registration: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html
- `ReferenceQueue` — `poll`, `remove`, `remove(long)`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/ReferenceQueue.html
- `Reference` — `get`, `clear`, `enqueue`, `refersTo`, `reachabilityFence`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html
- `WeakReference`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html
- `SoftReference`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/SoftReference.html
- `PhantomReference`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/PhantomReference.html
- `Cleaner`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html
- `WeakHashMap`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html

**Kết luận kỹ thuật:** ReferenceQueue là kênh **nhận những Reference objects đã enqueue**. Hãy tách rõ **mất strong reachability → GC phát hiện → clear reference → enqueue → application dequeue**, nhớ rằng các mốc **không có deadline**, và event có thể do **enqueue thủ công** chứ không phải GC. Để hệ thống đúng đắn, giữ reference object/metadata phù hợp nhưng không giữ mạnh referent, dùng queue cho **stale-entry cleanup và thông báo bổ trợ**; tài nguyên cần giải phóng đúng hạn phải có **explicit close và ownership rõ ràng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-006">Rò rỉ bộ nhớ Java khi đối tượng vẫn được giữ bởi cache, listener hoặc ThreadLocal</a>

<details>
<summary>Xem chi tiết</summary>

# Rò rỉ bộ nhớ Java khi đối tượng vẫn được giữ bởi cache, listener hoặc ThreadLocal

Java có Garbage Collector (GC), nhưng **có GC không đồng nghĩa ứng dụng không thể bị rò rỉ bộ nhớ**. GC thu hồi những object **không còn reachable theo các quy tắc reachability**; nó không thể tự kết luận rằng một object tuy còn đường tham chiếu mạnh nhưng đã **hết giá trị nghiệp vụ**. Nếu một cache không có giới hạn, một listener không được hủy đăng ký hoặc một `ThreadLocal` giữ dữ liệu qua hàng nghìn request, object vẫn có thể reachable và liên tục tiêu thụ heap.

Bài học đặt trong chủ đề **khả năng truy cập (reachability)** trên JVM, dùng **Java 17** cho code và API. Cần phân biệt rõ **memory leak logic** (object không còn cần nhưng vẫn bị giữ sống), **allocation nhiều nhưng đã thu hồi**, **cache cố ý giữ dữ liệu**, và **native memory leak**. Các ví dụ tập trung vào ba nguồn giữ tham chiếu phổ biến: **cache, listener, ThreadLocal**; sau đó trình bày cách tìm **path to GC roots**, đọc heap dump và thiết kế vòng đời giải phóng tài nguyên.

**Thông điệp chính:** khi điều tra rò rỉ bộ nhớ Java, đừng chỉ hỏi “object nào tăng?”; hãy hỏi thêm **“ai đang giữ reference tới object đó và tại sao đường giữ sống ấy chưa được cắt?”**

## 1. Khả năng truy cập là cơ chế quyết định điều gì có thể bị thu gom

Trong mô hình heap của JVM, objects và arrays có thể tham chiếu lẫn nhau. GC nhận diện objects cần giữ dựa vào một tập **GC roots** và những đường tham chiếu hợp lệ đi từ roots vào object graph.

```text
GC root: static field APP_CACHE
    |
    v
ConcurrentHashMap
    |
    v
Map entry -> CachedResult -> List -> byte[] rất lớn
                 ^
                 |
            nghiệp vụ đã hết cần, nhưng cache chưa xóa entry

GC vẫn nhìn thấy một đường tham chiếu mạnh từ root đến byte[].
Vì thế byte[] và các object trên đường giữ sống chưa thể được
thu hồi chỉ vì ứng dụng "không còn dùng" chúng nữa.
```

Một **strong reference** là tham chiếu thông thường, ví dụ field `Object value`, một phần tử trong `List` hay value trong `Map`. Các sources giữ sống thường gặp gồm:

- Static fields của các class còn sống, như `static final Map`, singleton, registries.
- Local variables/operand-stack references của **active thread frames**, trong khoảng mà VM thực sự còn xem chúng live.
- Instance fields và array elements được truy cập qua các object reachable khác.
- Những cấu trúc VM/JNI handles theo semantics tương ứng.
- Objects gắn với threads sống lâu, bao gồm các entries trong thread-local map.

**Object graph chứa chu trình không tự tạo memory leak.** Nếu `A.next = B` và `B.next = A` nhưng không có đường giữ sống từ GC roots tới A/B, tracing GC vẫn có thể thu gom cả chu trình. Java GC không phụ thuộc vào việc bạn chủ động phá mọi vòng reference như một hệ thống reference counting đơn giản.

### “Reachable” khác “còn có ích”

| Tình trạng | Reachability | Kết quả mong đợi |
| --- | --- | --- |
| Đối tượng đang phục vụ request | Reachable và còn cần | Giữ lại |
| Kết quả cache nằm trong chính sách size/TTL | Reachable và được phép giữ | Có thể giữ, đây không tự là leak |
| Listener của màn hình đã đóng nhưng vẫn được event bus giữ | Reachable, hết vòng đời sử dụng | **Logical memory leak** |
| Giá trị của request cũ trong thread pool worker `ThreadLocal` | Reachable qua thread đang sống, không còn cần | **Logical memory leak**, có thể thêm lỗi dữ liệu |
| Object không còn đường strong reachability | Có thể trở nên collectible theo quy tắc reference processing | GC có thể thu hồi khi thích hợp, **không có thời điểm bảo đảm** |

Không dùng việc `System.gc()` trả về để chứng minh một object đã được thu hồi; `System.gc()` chỉ là yêu cầu thực hiện GC và VM không cam kết thu hồi object nào ngay lập tức.

## 2. Bốn thuật ngữ quan trọng: allocation, retention, reachability, leak

1. **Allocation:** JVM tạo objects/arrays cho tính toán. Một ứng dụng xử lý nhiều request có thể allocate rất nhanh nhưng GC cũng có thể thu hồi nhanh.
2. **Retention:** các references giữ một tập objects sống qua thời gian. Một object nhỏ có thể giữ nguyên cả một cây dữ liệu lớn bên dưới.
3. **Reachability:** tồn tại hoặc không tồn tại đường đi từ GC roots qua các reference edges theo mô hình reachability.
4. **Memory leak logic:** giữ lại objects **không còn cần thiết** lâu hơn vòng đời thiết kế, thường khiến lượng memory cần giữ liên tục tăng hoặc không trở về mức kỳ vọng.

Không có một công thức “used heap lớn = leak”. Trong một JVM dùng GC, có thể quan sát heap usage dạng **răng cưa**: used tăng do allocations rồi giảm sau các lần collection. Nếu mức **post-GC live set** sau các workload tương đương cứ tăng và không ổn định, đó là dấu hiệu cần điều tra retention.

**Ngược lại, không có `OutOfMemoryError` cũng không chứng minh không có leak**: leak nhỏ có thể tích tụ nhiều ngày, hoặc vẫn nằm dưới heap limit tại thời điểm quan sát.

## 3. GC roots, paths và retained size

Xét sơ đồ:

```text
Root 1: Application.CACHE
   |
   +--> CacheEntry("account-1")
            |
            +--> Report
                  |
                  +--> List<Attachment>
                           |
                           +--> Attachment[1] --> byte[5 MiB]
                           +--> Attachment[2] --> byte[8 MiB]

Root 2: Worker Thread
   |
   +--> ThreadLocalMap
             |
             +--> RequestContext --> Report
```

Nếu cùng một `Report` reachable qua **hai đường**, xóa một cache entry **chưa đủ** thu hồi Report vì `ThreadLocalMap` vẫn giữ đường còn lại. Đây là lý do công cụ heap dump cho phép xem **Path to GC Roots**, **incoming references**, **dominator tree** và **retained size**.

Phân biệt kích thước:

- **Shallow size:** kích thước trực tiếp của object đó, không tính các objects nó tham chiếu.
- **Retained size:** tổng memory sẽ có khả năng được giải phóng về reachability nếu object này và các objects mà nó **giữ sống độc quyền** không còn reachable, theo mô hình dominator của heap snapshot.
- Một `HashMap` entry chỉ tốn ít byte shallow nhưng có thể giữ cả graph hàng chục MiB qua `value`.
- Nếu một object còn đường khác từ GC roots, nó **không nhất thiết** nằm trong retained set độc quyền của cache entry đang xét.

**Mối liên hệ cần nhớ:** trong việc tìm leak, object tăng nhanh quan trọng, nhưng **object/root nào giữ chúng lại** mới chỉ ra vị trí cần sửa. `byte[]` lớn không có nghĩa `byte[]` là thành phần lập trình lỗi.

### Cẩn trọng về thời điểm snapshot

Một heap dump là **ảnh chụp tại thời điểm cụ thể**, không tự chứng minh một object đã hết giá trị nghiệp vụ. Một cache đúng thiết kế cũng có thể có retained size lớn. Cần đặt dữ liệu vào bối cảnh lifecycle, lượng request, cache hit rate, cardinality và khoảng thời gian từ lúc object được tạo.

## 4. Nguồn leak thứ nhất: cache không có giới hạn hoặc không có cơ chế hết hạn

Một cache là cơ chế giữ objects **có chủ đích** để tái sử dụng. Nó trở thành leak khi chính sách giữ vượt khỏi nhu cầu ứng dụng, ví dụ:

- `static Map<String, Payload> CACHE` không có giới hạn kích thước.
- Mỗi request tạo key mới nên cache entry **không bao giờ được truy cập lại**, nhưng cũng không bao giờ bị xóa.
- Có TTL trên giấy nhưng không có hành động expire/cleanup thực tế, hoặc eviction chỉ chạy khi có thao tác mới.
- Cache key giữ toàn bộ request/session/context thay vì chỉ một định danh nhỏ, tạo đường giữ sống ngoài dự đoán.
- Cache value giữ object graph lớn: images, buffers, documents, response bodies, class loaders.
- Cache phân vùng theo tenant/user nhưng không gỡ entries khi tenant/user/session hết vòng đời.
- Một cache dùng cho “tối ưu” bị coi thành kho lưu trữ vĩnh viễn mà không có memory budget.

### Mẫu code không nên đưa vào production nếu cardinality tăng không giới hạn

```java
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class UnboundedCache {
    private static final Map<String, byte[]> CACHE =
            new ConcurrentHashMap<>();

    static void remember(String requestId, byte[] payload) {
        CACHE.put(requestId, payload);
    }
}
```

Đây là **class minh họa**, không có `main`. Nếu `requestId` luôn mới, map giữ mỗi `byte[]` vì static field `CACHE` là một đường root/owner sống dài. `ConcurrentHashMap` giải quyết **thread safety của map**, **không giải quyết giới hạn bộ nhớ**.

Một lần gọi `CACHE.clear()` có thể cắt đường giữ sống qua cache, nhưng không bảo đảm ngay sau lời gọi heap used hoặc RSS giảm tương ứng: vẫn có thể còn references khác và GC/heap commitment có cơ chế riêng.

### Cache đúng cần chính sách quản lý vòng đời

Có thể kết hợp:

- **Maximum entry count**: số entries không vượt quá một giới hạn.
- **Maximum weight**: budget theo ước lượng bytes/cost của value, phù hợp hơn khi kích thước entry chênh lệch lớn.
- **Time-based expiry**: expire after write/access nếu hợp lý với nghiệp vụ; không dùng TTL thay cho capacity bound một cách máy móc.
- **Eviction policy**: LRU/LFU/segmented, dựa vào hit/miss và workload.
- **Invalidation** khi thay đổi dữ liệu hoặc kết thúc session/tenant.
- **Explicit cleanup** cho value sở hữu resources như file handles/native buffers, vì xóa reference không phải là lời gọi `close()`.
- **Metrics**: cache entry count, estimated size/weight, eviction count, hit ratio, estimated retained bytes và phân vùng owner.

Một bounded cache có thể sử dụng memory đáng kể vì đó là **hành vi mong muốn**. Tính đúng là giữ trong budget và không tiếp tục tích tụ vô hạn.

## 5. Ví dụ Java 17 chạy được: bounded LRU cache bằng LinkedHashMap

Tạo file **`BoundedCacheDemo.java`**:

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class BoundedCacheDemo {

    static final class LruCache<K, V> extends LinkedHashMap<K, V> {
        private final int capacity;

        LruCache(int capacity) {
            super(16, 0.75f, true); // access-order
            if (capacity < 1) {
                throw new IllegalArgumentException("capacity");
            }
            this.capacity = capacity;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > capacity;
        }
    }

    public static void main(String[] args) {
        Map<Integer, String> cache = new LruCache<>(3);

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        cache.get(1);           // 1 là entry vừa được dùng gần nhất
        cache.put(4, "four");   // loại bỏ entry 2 (cũ nhất)

        System.out.println("size=" + cache.size());
        System.out.println("keys=" + cache.keySet());
        System.out.println("contains2=" + cache.containsKey(2));
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 BoundedCacheDemo.java
java BoundedCacheDemo
```

**Stdout chính xác:**

```text
size=3
keys=[3, 1, 4]
contains2=false
```

Vì `LinkedHashMap` được bật **access-order**, lời gọi `get(1)` chuyển key 1 thành mới dùng gần nhất. Thêm key 4 khiến map loại key 2; thứ tự còn lại là `3, 1, 4`.

**Giới hạn của ví dụ:** `LinkedHashMap` ở đây **không thread-safe**; chỉ dùng minh họa eviction trong một thread. Ứng dụng đồng thời nên chọn cache implementation/concurrency policy phù hợp (hoặc đồng bộ đầy đủ). `removeEldestEntry` dùng **số entries**, không theo số byte, và không tự có TTL/async cleanup hay eviction callbacks. Không suy rằng mỗi cache nên tự viết một `LinkedHashMap` trong production; có thể dùng thư viện cache chuyên dụng với policy/metrics rõ ràng.

### Tại sao LRU không phải lời giải duy nhất?

- Một entry `byte[200 MiB]` và một entry `byte[2 KiB]` cùng tính là một entry; giới hạn count có thể không đủ.
- Nếu key cardinality khổng lồ, eviction liên tục gây churn/GC pressure ngay cả khi không leak.
- Dữ liệu nghiệp vụ có deadline hết hạn, security/PII retention và invalidation riêng; LRU chỉ điều khiển **độ gần đây của truy cập**, không tự thỏa chính sách retention.
- Cache nằm trong singleton/static và tồn tại suốt process thường **không tự giải phóng** khi một màn hình/session đóng, trừ khi key và eviction/lifecycle được thiết kế tương ứng.

## 6. Weak/soft references có giúp cache tự giải phóng không?

`java.lang.ref.WeakReference` và `SoftReference` có semantics riêng, nhưng chúng **không** tự biến mọi cache thành bounded cache đúng.

- **WeakReference:** khi referent chỉ còn weakly reachable theo quy tắc reference processing, GC có thể clear weak reference; thời điểm GC không xác định. Dùng khi muốn không giữ đối tượng sống chỉ vì reference yếu.
- **SoftReference:** referent có thể được giữ lâu hơn trong một số điều kiện memory, nhưng chính sách clearing phụ thuộc implementation/pressure. Không xem SoftReference là SLA, TTL, capacity bound hoặc giải pháp tổng quát để tránh OOME.
- **PhantomReference:** chủ yếu hỗ trợ cơ chế theo dõi cleanup sau trạng thái reachability thích hợp; `get()` không cho object để dùng như cache value.

### Cạm bẫy của WeakHashMap

`WeakHashMap` giữ **weak keys**, nhưng **values thông thường vẫn là strong references** từ map. Nếu value giữ một reference mạnh quay trở lại chính key (trực tiếp hoặc gián tiếp), key có thể vẫn reachable qua `map → value → key`, vì thế entry **không tự biến mất như dự kiến**.

```text
WeakHashMap
   | strong
   v
 value
   | strong
   v
 key <--- weak edge --- map's weak-key entry

Đường map -> value -> key là strong, nên key vẫn reachable.
```

Đừng dựa vào một test kiểu `System.gc(); assert(map.isEmpty())`: GC không có thời điểm thực thi được bảo đảm, và WeakHashMap có thể expunge stale entries trong các thao tác nội bộ. Nếu mục đích là “cache chỉ dùng đến 64 MiB”, cách rõ ràng hơn là **maximum weight + eviction**.

## 7. Nguồn leak thứ hai: listener/observer không được hủy đăng ký

Trong mô hình Observer/EventBus, một publisher sống dài giữ danh sách callbacks/listeners. Nếu listener là instance method reference hoặc lambda **capture** object của view/controller/request, publisher có thể vô tình giữ toàn bộ object graph ấy.

```text
GC root
    |
    v
Application singleton EventBus
    |
    v
List<Listener>
    |
    v
listener (ví dụ: controller::onEvent)
    |
    v
Controller đã đóng
    |
    +--> ViewModel --> List lớn --> images/buffers
    +--> UserSession
    +--> các closures và objects khác
```

Người dùng đã đóng màn hình, nhưng EventBus chưa remove listener. **Controller vẫn reachable**, nên GC không thể biết controller không còn được sử dụng.

Cần nhớ:

- **Lambda capture là reference retention**: `event -> controller.handle(event)` giữ `controller` nếu lambda object còn sống. Method reference `controller::handle` cũng có thể giữ receiver.
- Nếu listener là một anonymous inner class, nó có thể giữ reference tới enclosing instance tùy trường hợp.
- Nếu event bus lưu listener trong static field hoặc application-scope singleton, owner thường sống lâu hơn UI component hoặc request.
- Có thể leak **không chỉ memory**: listener cũ vẫn nhận event, thực hiện side effects lặp lại, ghi dữ liệu vào view đã đóng.

### Nguyên tắc vòng đời

**Subscription phải đi cùng một phương thức unsubscribe/close rõ ràng.** Khi một component đăng ký listener, nó nên giữ token/subscription handle để hủy đăng ký ở `close()`, `dispose()`, `onStop` hoặc lifecycle hook tương ứng.

Không phải “xóa biến local listener” là đủ: khi publisher đang giữ strong reference, listener vẫn sống. Phải cắt **đường giữ sống từ publisher**.

## 8. Ví dụ Java 17: listener subscription có thể đóng

Tạo file **`ListenerLifecycleDemo.java`**:

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class ListenerLifecycleDemo {

    static final class EventBus {
        private final List<Consumer<String>> listeners =
                new CopyOnWriteArrayList<>();

        Subscription subscribe(Consumer<String> listener) {
            listeners.add(listener);
            return new Subscription(this, listener);
        }

        void publish(String event) {
            for (Consumer<String> listener : listeners) {
                listener.accept(event);
            }
        }

        int listenerCount() {
            return listeners.size();
        }

        private void unsubscribe(Consumer<String> listener) {
            listeners.remove(listener);
        }
    }

    static final class Subscription implements AutoCloseable {
        private final EventBus bus;
        private final Consumer<String> listener;
        private boolean closed;

        Subscription(EventBus bus, Consumer<String> listener) {
            this.bus = bus;
            this.listener = listener;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                bus.unsubscribe(listener);
            }
        }
    }

    static final class Screen {
        private final String name;

        Screen(String name) {
            this.name = name;
        }

        void onEvent(String event) {
            System.out.println(name + ":" + event);
        }
    }

    public static void main(String[] args) {
        EventBus bus = new EventBus();
        Screen screen = new Screen("screen-A");

        try (Subscription subscription =
                     bus.subscribe(screen::onEvent)) {
            bus.publish("open");
            System.out.println("during=" + bus.listenerCount());
        }

        bus.publish("after-close");
        System.out.println("after=" + bus.listenerCount());
    }
}
```

```bash
javac --release 17 ListenerLifecycleDemo.java
java ListenerLifecycleDemo
```

**Stdout chính xác:**

```text
screen-A:open
during=1
after=0
```

Sau `try-with-resources`, `Subscription.close()` xóa listener khỏi EventBus; `publish("after-close")` không còn gọi Screen. Đây là cách **cắt đường giữ sống từ publisher**.

**Phạm vi giới hạn của mẫu:** nó chỉ minh họa vòng đời cơ bản trong một thread. Trong ứng dụng concurrent thật, `Subscription.close()` cần được thiết kế thread-safe/idempotent, và việc remove callback không nhất thiết hủy một callback đang được thực thi trong snapshot của `CopyOnWriteArrayList`. Vì vậy lifecycle/race semantics phải được định nghĩa rõ. Một `Subscription` do chính component còn giữ cũng có thể chứa listener reference, nhưng khi component và subscription đều không còn reachable qua owner dài hạn, chúng có thể được GC xử lý.

### Một số tình huống listener tương tự

- Listeners của Swing/JavaFX, event emitters hoặc message buses sống dài hơn view.
- Application event handlers đăng ký mỗi lần start nhưng không deregister khi shutdown/reload.
- Reactive streams có `subscribe()` trả về subscription/disposable nhưng chủ sở hữu không hủy.
- Callbacks vào web socket/session mà connection manager giữ lại sau disconnect.
- Watchers/observers/callbacks mang theo closures capture cả context nặng.

Giải pháp không phải luôn dùng `WeakReference` cho listener. Weak listener có thể bị thu gom **quá sớm** nếu phía subscriber không có owner strong rõ ràng; cũng có thể khiến lifecycle không quan sát được. Ưu tiên **explicit subscribe/unsubscribe** khi hợp đồng cho phép.

## 9. Nguồn leak thứ ba: ThreadLocal trong thread pool

`ThreadLocal<T>` cung cấp giá trị **riêng theo từng thread**. Điểm dễ gây nhầm: object `ThreadLocal` không phải nơi chứa một bản value chung cho tất cả threads; mỗi `Thread` có cấu trúc thread-local entries tương ứng.

Trong thread pool, worker thread có thể sống **lâu hơn hàng nghìn request**. Nếu request đặt dữ liệu vào `ThreadLocal` rồi không `remove()`, worker tiếp tục giữ value sau khi request đã kết thúc.

```text
ExecutorService
    |
    v
Worker Thread (vẫn sống)
    |
    v
threadLocals / ThreadLocalMap
    |
    v
Entry.value (strong reference)
    |
    v
RequestContext
    |
    +--> User / token / session metadata
    +--> buffers / parsed request / classloader-related references
```

Hai hệ quả:

1. **Memory retention:** objects thuộc request cũ không thể thu gom nếu vẫn được ThreadLocal entry giữ mạnh.
2. **Context contamination:** task mới được chạy trên **cùng worker** có thể đọc thấy giá trị của request cũ, nếu code không khởi tạo hoặc xóa context đúng cách. Điều này còn nguy hiểm về an toàn dữ liệu giữa requests.

### Các điểm kỹ thuật cần nắm

- `ThreadLocal.set(value)` ghi value cho **thread hiện tại**.
- `ThreadLocal.get()` đọc value gắn với thread hiện tại, có thể trả `null` nếu chưa gán và không có `initialValue` khác.
- `ThreadLocal.remove()` xóa giá trị gắn với **thread hiện tại** cho key ấy.
- `ThreadLocalMap` trên HotSpot thường dùng **weak reference cho key `ThreadLocal`** và **strong reference cho value**. Key bị GC clear không đồng nghĩa value lập tức biến mất: stale entry có thể còn đó cho đến khi map cleanup thực hiện.
- Khi `ThreadLocal` được giữ trong `static final`, key vẫn có thể sống dài; nếu không `remove()`, value vẫn được worker giữ đến lúc bị thay/xóa hoặc worker kết thúc.
- `ThreadLocal` **không phải cơ chế truyền context an toàn giữa các threads**. Các asynchronous continuations có thể chạy trên thread khác hoặc thread cũ được tái sử dụng.

### Quy tắc thực tế

Luôn đặt `remove()` trong `finally` tại **ranh giới sở hữu vòng đời dữ liệu**, nhất là khi dữ liệu thuộc request/transaction/task:

```java
CONTEXT.set(context);
try {
    processRequest();
} finally {
    CONTEXT.remove();
}
```

Đây là **fragment minh họa**; `CONTEXT`, `context`, `processRequest()` thuộc code ứng dụng, không phải file Java độc lập. Nếu cần khôi phục một context đã tồn tại vì nested operation, phải thiết kế policy restore phù hợp thay vì luôn xóa mù quáng. Với nhiều values, xóa tất cả ThreadLocals thuộc phạm vi task và đóng resources trước khi trả worker về pool.

## 10. Ví dụ Java 17 xác định: cùng worker nhưng không rò context

Tạo file **`ThreadLocalCleanupDemo.java`**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadLocalCleanupDemo {
    private static final ThreadLocal<String> REQUEST_ID =
            new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = pool.submit(() -> {
                REQUEST_ID.set("request-A");
                try {
                    System.out.println("first=" + REQUEST_ID.get());
                } finally {
                    REQUEST_ID.remove();
                }
            });
            first.get();

            Future<?> second = pool.submit(() -> {
                System.out.println("second=" + REQUEST_ID.get());
            });
            second.get();
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac --release 17 ThreadLocalCleanupDemo.java
java ThreadLocalCleanupDemo
```

**Stdout chính xác trong đường chạy thành công:**

```text
first=request-A
second=null
```

`newSingleThreadExecutor()` đảm bảo các tasks đã nộp được xử lý tuần tự trên **một worker** (trong đường chạy thông thường, nếu worker không cần thay thế vì failure). `first.get()` hoàn thành trước `second`, và `remove()` xóa giá trị từ worker trong `finally`. Vì vậy request thứ hai không thấy `request-A`.

Nếu bỏ `REQUEST_ID.remove()`, cùng worker có thể in `second=request-A`, minh họa sự rò context. **Không dùng output `null` để chứng minh object đã được GC thu hồi**; demo chỉ chứng minh một reference path đã bị xóa ở ThreadLocal.

### Những điều `remove()` chưa giải quyết

- Một `RequestContext` còn được static cache hoặc listener giữ lại vẫn reachable.
- Một resource trong value như database connection cần được **đóng/return pool** riêng; chỉ `remove()` không tự đóng resource.
- `InheritableThreadLocal` có semantics truyền dữ liệu sang threads con tại thời điểm thích hợp; nó **không** phải cơ chế propagate request state thích hợp cho thread pool được tạo lâu trước request.
- Nếu dùng `CompletableFuture`, scheduler hoặc reactive programming, các callbacks có thể chạy trên những threads khác nhau; thiết kế context propagation tường minh thay vì giả định cùng một thread.

## 11. Vì sao key yếu của ThreadLocalMap vẫn có thể leak value?

Một hiểu lầm là “`ThreadLocalMap` dùng weak key nên giá trị sẽ tự thu hồi nếu key mất”. Thực tế, nếu key `ThreadLocal` không còn strong reference từ ứng dụng, entry có thể có dạng:

```text
Worker Thread (sống dài)
    |
    v
ThreadLocalMap
    |
    v
Entry
    key   = null  (weak key đã được clear)
    value = RequestContext (strong reference)
                |
                v
             graph lớn
```

HotSpot có cơ chế **expunge stale entries** khi truy cập/sửa map thích hợp, nhưng **không được mặc định việc clear weak key sẽ tức khắc xóa value**. Một worker idle lâu hoặc hoạt động theo pattern nhất định có thể giữ stale values lâu hơn mong đợi.

Chỉ tạo `new ThreadLocal<>()` trong mỗi request mà không giữ key và không gọi `remove()` còn làm tình huống khó chẩn đoán hơn. Vì thế:

- Với ThreadLocal thuộc task, giữ reference tới key trong scope đủ để gọi `remove()`.
- Dùng `try/finally` kể cả khi code ném exception.
- Tránh value chứa cả object graph lớn khi chỉ cần một ID nhỏ.
- Chọn ranh giới cleanup ở request/task filter/interceptor, không phân tán nhiều nơi.
- Kiểm tra thread pool/task wrapper của framework có tự dọn context hay chưa; đừng đoán.

**Ghi chú về Java 17:** ví dụ dùng platform threads và `ThreadLocal` truyền thống. Virtual threads đã có những thay đổi nền tảng ở các JDK sau; không lấy giả thiết áp dụng cho một phiên bản mới để giải thích nguyên văn runtime Java 17.

## 12. Static fields, singleton và class loader cũng có thể giữ sống dữ liệu

Một static field có thể là owner hợp lệ của cache/event bus. Trong JVM, việc giữ sống không đơn giản là “mọi static field của mọi class tồn tại vĩnh viễn”, vì lifecycle của class loader và khả năng unloading cũng quan trọng.

Ví dụ tình huống web application/plugin reload:

```text
GC root trong host (thread/pool/singleton)
    |
    v
Listener hoặc ThreadLocal value
    |
    v
Object từ plugin class
    |
    v
Plugin's defining ClassLoader
    |
    v
Class metadata và object graph liên quan
```

Nếu host vẫn giữ một object do plugin class loader định nghĩa, class loader ấy có thể **không đủ điều kiện được thu gom/unload**, dẫn tới tăng heap và class metadata/metaspace qua nhiều lần reload.

Điều này không có nghĩa “ClassLoader nào còn object thì chắc chắn class không bao giờ unload” ở mọi collector/runtime; nó chỉ mô tả **một đường retention điển hình** cần kiểm tra trong heap dump và class unloading metrics.

### Những nguồn giữ class loader ngoài dự đoán

- Worker thread giữ `ThreadLocal` value thuộc web app cũ.
- Thread Context ClassLoader (TCCL) vẫn trỏ đến loader plugin đã unload về nghiệp vụ.
- Static global registry hoặc listener bus của host giữ callbacks từ plugin.
- Scheduler/timer/task queue còn pending tasks capture class/plugin objects.
- JNI global references hoặc caches ngoài JVM giữ handles/lifecycle tương ứng.
- Logging/metrics adapters tạo registrations mà plugin không unregister khi shutdown.

Giải pháp phải bắt đầu từ **owner có vòng đời dài hơn**. Thường cần deregister/unsubscribe, cancel tasks, shutdown thread pools thuộc plugin, reset TCCL, remove ThreadLocals, đóng resources và tránh giữ Class/Method/MethodHandle/plugin instances quá lâu trong global caches.

## 13. Phân biệt memory leak với resource leak và cache hữu ích

Một object chưa được GC thu hồi không tự chứng minh chương trình sai. Ví dụ:

| Tình huống | Có thực sự là leak? | Bằng chứng cần thêm |
| --- | --- | --- |
| Cache chứa 10.000 entries nhưng giới hạn đúng 10.000 và hit rate cao | Chưa thể kết luận | Budget, hit rate, giá trị cache mang lại |
| Cache user/session tăng theo thời gian dù users đã logout | Rất đáng nghi | Key cardinality, eviction/TTL/invalidation |
| Listener list tăng theo số lần mở rồi đóng màn hình | Rất đáng nghi | Registration lifecycle và path tới publisher |
| Thread pool dùng 50 workers, mỗi worker giữ context cũ | Đáng nghi | ThreadLocalMap values, request boundary cleanup |
| Heap used tăng trước GC rồi giảm về baseline sau GC | Có thể bình thường | Post-GC live set và throughput/latency |
| Native direct buffers không được giải phóng đúng lifetime | Có thể là native resource retention | Native memory/JFR/NMT/OS metrics; heap dump có thể chỉ thấy wrapper |
| Objects ở cache đúng TTL nhưng heap committed không giảm | Không đủ kết luận leak | Heap used sau GC, GC uncommit policy, RSS |

**Rò rỉ file descriptor/socket không nhất thiết là rò rỉ heap.** Có khi một Java object giữ native resource và cần `close()` ngay cả khi GC về sau có thể thu hồi wrapper. Dùng `try-with-resources`/explicit lifecycle cho các resource như streams, database connections, response bodies và sockets; không trông chờ finalizer hoặc GC để hoàn tất nghiệp vụ giải phóng.

## 14. Chẩn đoán: dấu hiệu đúng là xu hướng retained heap sau GC

Khi ứng dụng chậm đi hoặc báo `OutOfMemoryError: Java heap space`, đừng lập tức quy nguyên nhân cho cache hay GC. Quy trình nên kết hợp:

1. **Xác định triệu chứng và scope:** heap OOME, `GC overhead limit exceeded`, metaspace OOME, direct/native memory pressure, hay container OOMKilled?
2. **Ghi version và collector:** `java -version`, flags `-Xms`, `-Xmx`, collector và workload khi lỗi xuất hiện.
3. **Theo dõi heap occupancy qua thời gian:** so các điểm tương đương về workload và các số đo sau GC khi có thể.
4. **Nhìn số lượng objects theo class** qua class histogram ở những thời điểm phù hợp.
5. **Lấy heap dump khi đủ quyền và tài nguyên** rồi xem retained size, dominator tree và GC roots.
6. **Truy ngược đến owner:** static cache, event bus, thread, ThreadLocalMap, scheduler hoặc class loader.
7. **Đối chiếu với lifecycle:** object có đáng lẽ biến mất sau logout, response completion, component close hay plugin unload?
8. **Sửa chỗ giữ reference**, thêm giới hạn/cleanup, viết regression test và theo dõi lại trên workload tương đương.

Một snapshot chỉ cho biết tại thời điểm đó objects nào được giữ. **Leak kết luận tốt hơn qua biến thiên và nghiệp vụ**, ví dụ cùng một thử nghiệm “mở/đóng 1.000 màn hình” lặp lại mà số listeners/retained controllers không trở về baseline.

### Heap used chưa chắc là live set

Ở giữa hai lần GC, heap có thể chứa các objects đã không còn reachable nhưng chưa reclaimed. Vì vậy:

- Heap used cao không tự chứng minh có strong references tới toàn bộ những bytes đã allocated.
- Post-GC occupancy có thể hữu ích, nhưng cách collector thu gom và timestamp metrics cần được hiểu đúng.
- Heap committed và process RSS có thể không giảm ngay dù leak đã được sửa, do JVM giữ memory để tái sử dụng hoặc OS/JVM không uncommit ngay.
- NMT/RSS/direct memory numbers thuộc phạm vi khác; không dùng `-Xmx` như giới hạn tổng memory của Java process.

## 15. Dùng jcmd, histogram và heap dump trên Java 17

Một số lệnh hữu ích trên JDK có công cụ `jcmd`:

```bash
jcmd -l
jcmd <PID> VM.flags
jcmd <PID> GC.heap_info
jcmd <PID> GC.class_histogram
jcmd <PID> GC.heap_dump /path/to/heap-issue.hprof
```

**Lưu ý:** `<PID>` và `/path/to/heap-issue.hprof` là **placeholder**, không phải một PID hoặc đường dẫn được xác minh tồn tại. Các lệnh histogram/heap dump có thể **tốn CPU, memory, disk, tạo pause hoặc ảnh hưởng production**; cần chốt quyền, dung lượng disk, bảo mật dữ liệu và thời điểm chạy.

- **`GC.heap_info`:** xem thông tin heap/GC tùy JVM.
- **`GC.class_histogram`:** thống kê số lượng và shallow bytes theo class tại thời điểm quan sát. Không cho biết ai giữ object hoặc retained size.
- **`GC.heap_dump`:** xuất snapshot heap để phân tích object graph. Heap dump có thể chứa secrets, tokens, PII và dữ liệu người dùng; áp dụng quyền đọc, lưu trữ, mã hóa và thời hạn xóa phù hợp.
- Các flags/biến thể hỗ trợ như `-all` có thể thay đổi việc có yêu cầu full GC trước dump hay không. **Đọc `jcmd <PID> help GC.heap_dump`** trên đúng JVM thay vì phỏng đoán hiệu ứng pause hoặc đối tượng được đưa vào dump.

Heap dump không phải “bộ giải đáp tự động”. Bạn cần truy **reference chains** để biết vì sao các objects còn sống.

### Dùng Eclipse MAT / heap analyzer

Một quy trình tương đối hiệu quả:

1. Mở `.hprof` trong công cụ phân tích như Eclipse Memory Analyzer (MAT).
2. Xem histogram, tìm classes bất thường: `byte[]`, `char[]`, DTOs, `ArrayList`, `HashMap$Node`, listener classes, request contexts.
3. Xem **Dominator Tree** để tìm object owners có **retained size lớn**.
4. Với một nhóm instances nghi ngờ, dùng **Path To GC Roots** và loại trừ những weak/soft/phantom edges nếu muốn tìm đường strong retention cụ thể.
5. Đọc **incoming/outgoing references** từ owner về các values; tìm field cụ thể như `CACHE`, `listeners`, `threadLocals`.
6. Đối chiếu lifecycle: owner nên còn sống hay phải được giải phóng? Nếu đáng lẽ đã đóng, kiểm tra `close/remove/invalidate` đã chạy trên tất cả đường lỗi chưa.
7. Lặp lại sau fix cùng kịch bản tải; số cache entries/listeners/context values có về mức ổn định không?

**Một đường tới GC root có nhiều nhánh.** Việc tìm được `static CACHE -> object` không chứng minh đó là **đường duy nhất**. Xóa cache rồi vẫn thấy object sống có thể do ThreadLocal, listener hoặc pending task còn giữ.

## 16. Đọc ThreadLocal leak trong heap dump

Một đường retention điển hình trong MAT có dạng ý niệm:

```text
Thread "http-worker-17"
  └── threadLocals
      └── ThreadLocalMap
          └── table[]
              └── Entry
                  ├── referent: ThreadLocal key hoặc null nếu cleared
                  └── value: RequestContext
                              └── byte[] / user/session graph
```

Tùy JDK/build/dump, tên field nội bộ có thể khác. Đây là sơ đồ minh họa chứ không phải hợp đồng API về cấu trúc `ThreadLocalMap`.

Điều tra các câu hỏi:

- Thread có phải worker lâu dài trong pool không? Pool sở hữu bởi ai?
- `ThreadLocal` key còn tồn tại hay đã được GC clear thành stale entry?
- Value chứa những reference gì, retained size bao nhiêu?
- Có dùng `remove()` trong `finally` cho mọi đường thoát, bao gồm exception/timeouts/cancellation không?
- Code có vô tình tạo `ThreadLocal` trong mỗi request thay vì tái dùng một key có lifecycle rõ ràng?
- Task có chạy xen kẽ giữa nhiều tenants/users, và leak value có thể gây lỗi bảo mật?

Nếu chỉ có một instance value lớn, đó có thể là request đang chạy hợp lệ. Nếu mỗi worker giữ contexts của requests đã hoàn thành lâu, hoặc nhiều stale entries tích tụ sau workload tương đương, nghi ngờ cao hơn.

## 17. Đọc cache/listener leak trong heap dump

### Cache

```text
Application.class
  └── static CACHE
      └── ConcurrentHashMap
          └── table[]
              └── Node
                  ├── key: request-382773
                  └── val: ResponseSnapshot
                              └── byte[] / nested DTOs
```

Dấu hiệu đáng nghi: key cardinality liên tục tăng, keys không được tái sử dụng, values không có TTL hoặc bộ nhớ vẫn giữ sau khi client/session kết thúc. Lưu ý class histogram của `byte[]` không nói keys thuộc map nào; cần đi từ **incoming references** về owner.

### Listener

```text
GlobalEventBus
  └── listeners
      └── ArrayList/CopyOnWriteArrayList
          └── Consumer/lambda/method reference
              └── ScreenController
                  └── viewModel / resource graph
```

Nếu Controller đã bị nghiệp vụ đóng nhưng vẫn có path từ singleton event bus, đó là dấu hiệu mạnh của leak. Fix là **remove listener đúng instance/reference**, không chỉ `screen = null` ở một biến local không liên quan.

### Một leak có thể có nhiều “bẫy chủ sở hữu”

Nếu Controller tự đăng ký listener, rồi listener capture Controller, thì việc Controller giữ `Subscription` là một chu trình. Chu trình ấy **tự nó không gây leak** nếu toàn bộ graph không có roots. Vấn đề là global bus tiếp tục giữ một đầu của chu trình; vì vậy cần **hủy đăng ký với bus**.

## 18. Tránh sửa leak bằng giải pháp che giấu triệu chứng

Những cách đối phó không giải quyết nguyên nhân:

- **Tăng `-Xmx`:** có thể trì hoãn OOME nhưng cache/listeners/ThreadLocals vẫn giữ objects theo quy luật cũ.
- **Gọi `System.gc()` liên tục:** không thể thu hồi objects còn strongly reachable; còn có thể giảm throughput và tăng pauses.
- **Chỉ xóa biến local:** nếu object còn được static cache hoặc listener list giữ, reference ở owner vẫn tồn tại.
- **Đổi mọi reference thành WeakReference:** có thể làm mất dữ liệu khi GC, không giải quyết memory budget hay logic cleanup.
- **Đóng worker thread pool vô tội vạ:** có thể cắt ThreadLocal values nhưng làm hỏng service, còn không sửa owner gốc của task lifecycle.
- **Chỉ sửa heap dump analyzer:** root cause nằm ở ownership/lifecycle code, không ở công cụ quan sát.
- **Tin vào finalizer:** finalization không có bảo đảm thời điểm và là cơ chế lỗi thời; dùng explicit `close()`, `Cleaner` khi thật phù hợp cho backup native cleanup, và `try-with-resources`.

**Fix tốt nhất** thường là một trong ba loại: đặt **budget/eviction cho cache**, **unsubscribe listener đúng lifecycle**, hoặc **remove/restore ThreadLocal đúng task boundary**. Có thể cần kết hợp nếu object đang bị giữ qua nhiều roots.

## 19. Checklist thiết kế để không tái xuất hiện leak

| Thành phần | Câu hỏi review | Biện pháp |
| --- | --- | --- |
| Cache | Số entries/estimated weight có giới hạn không? | Maximum size/weight, eviction, expiration, invalidation |
| Cache key | Key có giữ request/session/object graph không? | Key nhỏ, immutable, stable, có lifecycle |
| Cache value | Value có giữ cả graph lớn hay native resource không? | Giảm retained graph, close khi evict nếu cần |
| Listener | Ai giữ subscription và hủy vào lúc nào? | `AutoCloseable`/dispose/unsubscribe |
| Listener | Lambda/method reference capture object nào? | Tránh capture context quá rộng; cleanup đúng |
| ThreadLocal | Value thuộc thread hay thuộc request/task? | `try/finally remove()` tại task boundary |
| ThreadLocal | Thread pool có reuse workers giữa users? | Context isolation và cleanup |
| ThreadLocal | Có stale entry do key bị clear không? | Giữ key lifecycle rõ, gọi remove, kiểm tra heap dump |
| ClassLoader | Plugin có được shutdown đầy đủ khi unload không? | Cancel tasks, close registries, unregister, reset TCCL |
| Resources | Có file/socket/direct memory cần release không? | Explicit close và owner lifecycle |
| Diagnostics | Có baseline và metrics trước/sau workload? | Heap occupancy, counts, heap dumps, JFR khi phù hợp |
| Testing | Có lặp thao tác tạo/hủy rồi đo retention? | Lifecycle regression và boundedness tests |

Một policy tốt còn ghi **ai sở hữu dữ liệu** và **khi nào kết thúc thời gian giữ**: request, session, window, tenant, plugin hay cả application. Cùng một object có thể được cache hợp lệ trong vòng đời session nhưng thành leak nếu giữ sau logout.

## 20. Bài tự kiểm tra

**Câu 1:** Một `HashMap` chứa object A và B trỏ qua lại thành chu trình. Sau khi bỏ tất cả external roots tới map, A và B có bị “kẹt” vì cycle không?

**Không.** Tracing GC có thể thu gom cả graph không reachable từ GC roots. Chu trình không tự tạo leak.

**Câu 2:** `static final Map` có phải luôn gây leak?

**Không.** Static map có thể là cache/registry đúng thiết kế. Leak xuất hiện khi retention vượt vòng đời/budget thực tế; modifier `final` chỉ ngăn gán lại field reference, không ngăn contents của map thay đổi.

**Câu 3:** `ConcurrentHashMap` có tự eviction không?

**Không.** Nó cung cấp concurrent map semantics; phải có policy size/time/invalidation riêng.

**Câu 4:** Một view đã đóng nhưng `GlobalEventBus.listeners` còn chứa `view::onEvent`. Vì sao view chưa collectible?

Vì bus giữ method-reference callback, callback giữ receiver view; có **strong path từ root** tới view.

**Câu 5:** `ThreadLocalMap` có weak keys, vậy value chắc chắn biến mất ngay khi key bị clear?

**Không.** Value có thể là strong reference trong stale entry tới khi cleanup, nên cần `remove()` đúng vòng đời.

**Câu 6:** Một worker dùng `REQUEST_ID.set(...)` cho request A rồi xử lý request B. Nếu thiếu cleanup có thể xảy ra gì?

Memory retention qua context A, và nguy cơ B đọc **stale data** của A nếu code dùng `get()` khi không set lại.

**Câu 7:** `WeakHashMap` với value có strong reference quay lại key có chắc entry sẽ biến mất khi không còn external key variable không?

**Không.** Đường `map → value → key` có thể vẫn giữ key strongly reachable.

**Câu 8:** Heap used tăng ngay trước GC nhưng về gần baseline sau GC. Đây có chắc là leak không?

**Không.** Có thể là allocation churn bình thường. Cần quan sát retained live set và object lifecycle.

**Câu 9:** Heap dump histogram cho thấy rất nhiều `byte[]`. Có thể kết luận lỗi nằm ở nơi cấp phát byte arrays?

**Không.** Phải truy retained owner/path to GC roots. Một cache entry hoặc ThreadLocal value có thể đang giữ các arrays.

**Câu 10:** Xóa entry cache liệu có buộc RSS giảm ngay tương ứng với retained size?

**Không.** Có thể còn roots khác; GC chưa thu hồi ngay; JVM có thể giữ committed heap, còn OS RSS theo trang/bộ nhớ native.

## 21. Quy trình thực hành điều tra và xác nhận fix

Một kịch bản kiểm thử có thể lập lại:

1. **Đo baseline** sau warm-up hợp lý, lưu memory pools, số listeners, cache entry counts, thread count và heap usage.
2. **Tạo chuỗi thao tác** có lifecycle rõ: 1.000 request/100 lần mở-đóng view/10 lần plugin start-stop, với đầu vào tương đương.
3. **Chờ hệ thống quiescent** nếu hợp lý, đo lại counts và các metrics sau GC ở những thời điểm được instrument/ghi nhận, không ép một con số chính xác.
4. **Nếu object counts tăng liên tục**, thu heap dump và tìm top owners bằng dominator tree.
5. **Tìm path to GC roots**: static cache, event bus, threadLocalMap hay một owner khác.
6. **Xác định object hết vòng đời ở điểm nào**: response complete, screen close, tenant delete, task finally, plugin shutdown.
7. **Sửa code tại owner** bằng eviction/invalidate, unsubscribe/dispose hoặc remove ThreadLocal.
8. **Bổ sung test**: sau lifecycle close, listenerCount bằng 0; cache size không vượt max; task tiếp theo không thấy request context cũ.
9. **Kiểm tra lại dưới concurrent workload**: cleanup trong `finally` kể cả khi lỗi, cancellation hoặc timeout; tránh race giữa subscribe và unsubscribe.
10. **Theo dõi production**: số lượng listeners/cache entries, hit rate, ThreadLocal context hygiene, heap live set, OOME/GC pressure. Fix phải duy trì ổn định theo thời gian.

**Không cần đợi `OutOfMemoryError` mới kiểm tra:** leak mức thấp thường biểu hiện trước qua cache size tăng đều, listeners tích lũy và long-lived thread contexts.

## Tài liệu chuẩn để tra cứu

- [JVMS Java SE 17 §2.5.3 — Heap](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): objects, arrays và quản lý heap.
- [Java SE 17 `java.lang.ref` package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html): các cấp reachability và reference objects.
- [Java SE 17 `Reference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html), [`WeakReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html), [`SoftReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/SoftReference.html): reachability, clearing, `reachabilityFence` và vòng đời reference.
- [Java SE 17 `WeakHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html): weak keys, lưu ý về value trỏ ngược key.
- [Java SE 17 `ThreadLocal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): `get`, `set`, `remove` và per-thread values.
- [Java SE 17 `LinkedHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/LinkedHashMap.html): access order và `removeEldestEntry` cho bounded LRU demo.
- [Java SE 17 `AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): quản lý vòng đời unsubscribe/cleanup với try-with-resources.
- [Java SE 17 `ManagementFactory`, `MemoryMXBean`](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html): heap/non-heap usage và metrics.
- [Java SE 17 `jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): `GC.heap_info`, `GC.class_histogram`, `GC.heap_dump` và điều kiện chẩn đoán.
- [Java SE 17 `jstat`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): thống kê GC/memory utilization.
- [Eclipse Memory Analyzer (MAT)](https://eclipse.dev/mat/): heap dump, retained size, dominator tree, path to GC roots.
- [OpenJDK JEP 349 — JFR Event Streaming](https://openjdk.org/jeps/349): cơ sở quan sát runtime qua JFR, khi cần bổ sung evidence theo thời gian.

**Ghi nhớ:** GC thu hồi những objects **không còn reachable**, chứ không tự đoán dữ liệu nào đã hết giá trị nghiệp vụ. **Cache cần budget/eviction**, **listener cần unsubscribe đúng vòng đời**, **ThreadLocal cần `remove()` hoặc restore trong `finally`**. Khi thấy heap tăng, hãy truy **đường tham chiếu mạnh từ GC roots tới retained graph**, sửa **owner giữ sống**, rồi xác minh bằng một workload lặp lại và metrics trước–sau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-007">GC thu hồi bộ nhớ khác với đóng file, socket và tài nguyên bên ngoài</a>

<details>
<summary>Xem chi tiết</summary>

# GC thu hồi bộ nhớ khác với đóng file, socket và tài nguyên bên ngoài

## 1. Luận điểm chính: một object chết không đồng nghĩa tài nguyên bên ngoài đã được đóng

Trong Java, **garbage collector (GC)** quản lý khả năng tái sử dụng **bộ nhớ của những Java objects** mà chương trình không còn cần tới theo quy tắc reachability. Nhưng một Java object có thể chỉ là **lớp bao (wrapper)** của một tài nguyên không thuộc Java heap, chẳng hạn:

- File descriptor hoặc OS handle của một tệp.
- Socket và kết nối TCP.
- Database connection hoặc cursor/statement/result set.
- Direct/native buffer, native allocation hoặc mapping của file.
- Process, executor, thread pool và các đối tượng gắn với vòng đời tác vụ.
- Khóa, đăng ký listener, timer, resource của thư viện native.

**GC không phải API đóng tài nguyên theo lịch xác định.** Có thể một Java object đã hết lexical scope, bị bỏ reference hoặc trở thành eligible for collection, nhưng file vẫn đang mở, TCP connection vẫn tồn tại, native allocation vẫn chưa được thu hồi hoặc transaction chưa được rollback/commit.

Ví dụ:

```java
void readFile(Path file) throws IOException {
    InputStream stream = Files.newInputStream(file);
    int firstByte = stream.read();
    // Không gọi close() và không dùng try-with-resources.
}
```

Đây là **mã minh họa**, cần đặt trong class và khai báo các imports phù hợp. Biến local `stream` không còn dùng sau method, nhưng **Java không cam kết file descriptor sẽ được đóng ngay khi method return**. Một số implementation/library có cơ chế Cleaner hay giải phóng dự phòng; đó không phải lý do để dựa vào GC thay cho `close()`.

Cách nên viết:

```java
void readFile(Path file) throws IOException {
    try (InputStream stream = Files.newInputStream(file)) {
        int firstByte = stream.read();
        // Xử lý kết quả.
    }
}
```

`try-with-resources` thiết lập một đường **đóng có thứ tự, theo luồng điều khiển của Java**, dù block hoàn tất bình thường hay bằng exception.

**Phân biệt ba câu hỏi:**

1. **Object có còn strongly reachable không?** → liên quan tới GC và reachability.
2. **External resource có đang open/allocated không?** → phụ thuộc API/OS và state của resource.
3. **Ai chịu trách nhiệm gọi close/release/rollback/shutdown?** → là **ownership contract** của ứng dụng và thư viện.

Không thể trả lời câu 2 hoặc 3 chỉ từ câu 1.

## 2. Bản đồ bộ nhớ heap và tài nguyên bên ngoài

```text
               Java heap                     Hệ điều hành / native
        +---------------------+             +-------------------------+
        | FileInputStream     | --handle--> | File descriptor/handle  |
        | hoặc FileChannel    |             | file đang mở             |
        +---------------------+             +-------------------------+

        +---------------------+             +-------------------------+
        | Socket Java object  | --handle--> | Socket TCP kernel state  |
        +---------------------+             +-------------------------+

        +---------------------+             +-------------------------+
        | Connection wrapper  | --owns?---> | DB session / pool slot   |
        +---------------------+             +-------------------------+

        +---------------------+             +-------------------------+
        | Native buffer owner | --owns?---> | Native bytes / mapping   |
        +---------------------+             +-------------------------+
```

Sơ đồ thể hiện **mối liên hệ trách nhiệm**, không khẳng định mỗi tài nguyên luôn có một wrapper duy nhất hoặc một OS handle riêng: thư viện có thể chia sẻ descriptors, quản lý connection pool, tái sử dụng channels hay dùng nhiều native structures.

### 2.1. Java heap do GC quản lý

Theo **JVMS §2.5.3**, heap dùng để cấp phát class instances và arrays. VM dùng automatic storage management để reclaim heap storage của objects không còn cần. Garbage collector có thể chạy theo chính sách riêng; `System.gc()` không cho một deadline portable để mọi object không còn dùng được thu hồi.

Điều GC **không thể tự suy ra từ một kiểu object tùy ý** là “giải quyết transaction”, “flush dữ liệu nghiệp vụ”, “gửi protocol close frame”, “trả lại connection pool slot đúng deadline” hoặc “đóng file handle vào thời điểm yêu cầu”.

### 2.2. External resource có giới hạn riêng

Ví dụ hệ điều hành có thể giới hạn số file descriptors của một process. Một ứng dụng giữ hàng nghìn sockets chưa đóng có thể gặp lỗi kiểu **too many open files** hoặc resource exhaustion ngay cả khi Java heap còn khá nhiều.

Một connection pool có thể hết connections dù heap còn trống. Một native allocation có thể làm process RSS tăng ngoài phần bộ nhớ Java heap. **Tăng `-Xmx` không tự xử lý external resource leak** và đôi khi làm ngân sách native memory của container khan hiếm hơn.

### 2.3. Mỗi loại “release” có hiệu ứng khác nhau

| Hoạt động | Tác động trực tiếp | Không tự bảo đảm |
| --- | --- | --- |
| Bỏ reference local | Giảm một đường tham chiếu trong đồ thị Java | Object GC ngay; socket/file tự đóng |
| `System.gc()` | Gợi ý runtime thực hiện GC | Một resource bất kỳ sẽ được đóng trước deadline |
| `close()` | Thực hiện hợp đồng đóng của resource tương ứng | Java heap object được free tức thời |
| `shutdown()` của executor | Bắt đầu quy trình dừng theo API | Mọi task đã hoàn thành ngay |
| `commit()` / `rollback()` | Điều khiển transaction theo JDBC/database | Connection wrapper bị GC ngay |
| `Files.delete(...)` | Yêu cầu xóa directory entry/file theo OS | Mọi open handle liên quan tự đóng hoặc có cùng semantics trên mọi OS |

**Điều cần thiết kế là state/lifecycle**, không chỉ số byte sử dụng.

## 3. Scope, reachability và resource lifetime là ba trục độc lập

Xét:

```java
InputStream in = Files.newInputStream(path);
try {
    consume(in);
} finally {
    in.close();
}
```

Đây là **mã minh họa**, cần có `path`, `consume`, imports và xử lý exception theo ứng dụng. Trong đoạn này:

- Biến local `in` có **scope** riêng.
- Object stream có **reachability** riêng; còn có thể được giữ ở phương thức khác hoặc bởi field nếu code chia sẻ reference.
- File descriptor có **open/closed state** theo API/OS; lời gọi `close()` chủ động chuyển lifecycle qua bước đóng.

Bạn có thể chủ động **close resource khi Java wrapper object vẫn strongly reachable**, ví dụ `in.close(); System.out.println(in != null);`. Biến `in` vẫn có giá trị reference; điều này không có nghĩa file còn open.

Ngược lại, bạn có thể bỏ mọi local reference tới một wrapper nhưng chưa có bảo đảm external resource đã được đóng theo deadline.

### 3.1. Object còn sống nhưng tài nguyên đã đóng

```java
InputStream stream = Files.newInputStream(path);
stream.close();
boolean wrapperStillReferenced = stream != null;
```

`wrapperStillReferenced` bằng true khi đoạn code hoàn tất bình thường. Nhưng tài nguyên stream đã được yêu cầu đóng. **Object memory lifetime ≠ resource open state**.

Một số API cho phép gọi `close` lặp lại; một số API/implementation có hành vi riêng. Đừng đồng nhất “wrapper còn sống” với “resource có thể tiếp tục dùng hợp lệ”.

### 3.2. Object hết scope nhưng resource có thể vẫn open

```java
static void openAndForget(Path path) throws IOException {
    InputStream stream = Files.newInputStream(path);
    stream.read();
    // Không close.
}
```

Đây là **anti-pattern**. Sau return không nên suy đoán OS handle đã được giải phóng. Có thư viện cung cấp cleanup dự phòng thông qua reference processing, nhưng thời điểm GC/cleaner không xác định; không được thiết kế resource ownership dựa vào may rủi đó.

### 3.3. Reference còn sống nhưng resource không còn sử dụng được

Một socket/stream/DB connection đã đóng có thể vẫn là một Java object bình thường. Nhưng gọi read/write/query sau khi đóng có thể ném exception hoặc hành xử theo API tương ứng.

**Không dùng `object != null` làm phép kiểm tra duy nhất rằng resource vẫn open.** State và errors phải được lấy từ hợp đồng của resource.

## 4. Tại sao finalization không thể là chiến lược đóng file/socket đáng tin?

Finalization từng cho phép class ghi đè `finalize()` để thực thi code khi runtime nhận thấy điều kiện đặc biệt liên quan object reachability. Cơ chế này có nhiều vấn đề:

- Không có hạn định khi nào GC phát hiện object cần xử lý.
- Finalizer có thể chậm, không được chạy trước khi process kết thúc.
- Có rủi ro trì hoãn reclaim bộ nhớ, deadlock, vấn đề security/concurrency và object resurrection.
- Runtime mới có thể vô hiệu hóa hoặc loại bỏ tính năng.
- Finalizer không có cơ chế business transaction đáng tin cậy để đảm bảo dữ liệu đã flush/commit.

**JEP 421** deprecate finalization for removal từ JDK 18. Code mới không nên viết `finalize` để đóng file descriptor, network session hay database connection. Nên dùng `try-with-resources`/`AutoCloseable` hoặc explicit lifecycle.

### 4.1. Finalizer đã chạy không có nghĩa logic resource hoàn tất

Đóng file có thể thất bại bởi `IOException`; transaction có thể cần rollback/commit theo quy tắc; network close có thể phụ thuộc protocol và trạng thái peer. Những tình huống này cần có **luồng xử lý lỗi do caller kiểm soát**.

Nếu chỉ dựa vào finalization, caller không có một điểm rõ ràng để nhận và xử lý lỗi đóng trước khi trả kết quả nghiệp vụ.

## 5. `AutoCloseable` và `Closeable` — ngôn ngữ của quyền sở hữu

**`AutoCloseable`** cung cấp phương thức `close()`:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

`java.io.Closeable` mở rộng `AutoCloseable` với hợp đồng `close()` thích hợp cho I/O (thường khai báo `IOException`). Không phải mọi AutoCloseable đều là Closeable; không phải mọi `close` đều có cùng semantics idempotency hoặc loại exception.

### 5.1. `try-with-resources` thực hiện việc gì?

Với:

```java
try (Resource resource = open()) {
    use(resource);
}
```

Java compiler đảm bảo `close()` được gọi khi rời body nếu resource được tạo thành công, **kể cả khi body ném exception**. Khi có nhiều resources, chúng được đóng **theo thứ tự ngược lại thứ tự khởi tạo**.

Đoạn này chỉ là **mẫu cấu trúc**, `Resource`, `open` và `use` cần khai báo tương ứng. Bài thực hành độc lập ở phần tiếp theo dùng file thật để kiểm chứng.

### 5.2. Đừng hiểu `AutoCloseable` là Java destructor

- `close()` chỉ làm những gì API/implementation quy định.
- Object Java vẫn có thể tồn tại sau `close()`.
- `close()` có thể flush/rollback/trả tài nguyên pool/thả native handle theo từng API.
- Một `AutoCloseable` có thể không sở hữu native resource nào; nó vẫn có thể quản lý lifecycle logic.
- `try-with-resources` không ép GC chạy.

### 5.3. Quyền sở hữu phải được xác định

Khi method nhận một `InputStream` từ caller, cần phân biệt:

- **Callee sở hữu:** method có trách nhiệm đóng sau khi dùng.
- **Caller sở hữu:** method chỉ mượn stream và không đóng, trừ khi hợp đồng API nói khác.

Ví dụ:

```java
static byte[] readWithoutClosing(InputStream borrowed)
        throws IOException {
    return borrowed.readAllBytes();
}
```

Đây là một **method minh họa**. Caller có trách nhiệm đóng stream nếu nó sở hữu resource. Việc callee tự close có thể làm hỏng thao tác tiếp theo của caller. Cách đặt tên/documentation cần nói rõ ownership; không phải cứ thấy `InputStream` là mọi method đều nên gọi `close`.

## 6. Ví dụ Java 17+ đầy đủ: file thật, close đảo thứ tự, suppressed exception

Tạo tệp **`ResourceLifecycleDemo.java`**:

```java
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResourceLifecycleDemo {
    static final class Tracked implements AutoCloseable {
        private final String name;
        private boolean closed;

        Tracked(String name) {
            this.name = name;
        }

        void use() {
            System.out.println("using " + name);
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                System.out.println("closed " + name);
            }
        }
    }

    static final class FailingClose implements AutoCloseable {
        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("resource-lifecycle-", ".txt");

        try {
            try (BufferedWriter writer =
                         Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write("Hello JVM");
            }

            try (BufferedReader reader =
                         Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                System.out.println("file content: " + reader.readLine());
            }
        } finally {
            Files.deleteIfExists(file);
        }

        try (Tracked first = new Tracked("first");
             Tracked second = new Tracked("second")) {
            first.use();
            second.use();
        }

        try (FailingClose failing = new FailingClose()) {
            throw new IllegalStateException("body failed");
        } catch (IllegalStateException error) {
            System.out.println("primary: " + error.getMessage());
            System.out.println("suppressed count: "
                    + error.getSuppressed().length);
            System.out.println("suppressed type: "
                    + error.getSuppressed()[0]
                            .getClass().getSimpleName());
        }

        System.out.println("done");
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 ResourceLifecycleDemo.java
java ResourceLifecycleDemo
```

**Stdout chính xác trong lần chạy bình thường**, khi có quyền tạo/đọc/xóa file tạm trong thư mục tạm hệ thống:

```text
file content: Hello JVM
using first
using second
closed second
closed first
primary: body failed
suppressed count: 1
suppressed type: IOException
done
```

Ví dụ được kiểm tra bằng `javac --release 17` và chạy trên OpenJDK 21, stdout khớp. Chương trình sử dụng file tạm thực qua `Files.createTempFile`, không in đường dẫn ngẫu nhiên, để đầu ra ổn định giữa các lần chạy có môi trường I/O hợp lệ.

### 6.1. Phần file I/O giải quyết resource lifecycle

`Files.newBufferedWriter` mở một writer, ghi `Hello JVM` và try-with-resources đóng writer khi block kết thúc; việc đóng writer cũng đảm bảo thực hiện những hoạt động flush/close phù hợp hợp đồng API. Sau đó reader được mở riêng, đọc dòng và đóng.

`Files.deleteIfExists(file)` trong `finally` xóa file tạm dù phần thao tác I/O thành công hay ném exception (với các giới hạn lỗi của file system). Không có dòng nào phải gọi `System.gc()` để hoàn thành vòng đời file.

**Chú ý:** `deleteIfExists` không luôn thành công; ví dụ `IOException` có thể xảy ra do quyền truy cập/hệ thống file. Điều kiện “stdout chính xác” ở trên áp dụng **khi thao tác file tạm thành công bình thường**, không khẳng định mọi môi trường sandbox đều cho phép I/O.

### 6.2. `close()` diễn ra theo thứ tự ngược

Hai tài nguyên:

```java
try (Tracked first = new Tracked("first");
     Tracked second = new Tracked("second")) {
    first.use();
    second.use();
}
```

được tạo theo thứ tự `first` rồi `second`. Khi thoát block, compiler đóng `second` trước `first`:

```text
using first
using second
closed second
closed first
```

Thứ tự ngược đặc biệt có ích khi resource thứ hai phụ thuộc vào resource thứ nhất; ví dụ wrapper buffering trùm trên stream gốc. Tuy nhiên, nếu hai wrappers **cùng trỏ tới một underlying resource**, phải hiểu riêng hợp đồng ownership để tránh đóng lặp hoặc đóng quá sớm.

### 6.3. `close()` có thể ném exception khi body đã thất bại

Ở ví dụ:

```java
try (FailingClose failing = new FailingClose()) {
    throw new IllegalStateException("body failed");
}
```

Khi rời body, Java vẫn gọi `FailingClose.close()`. `close` ném `IOException("close failed")`, nhưng vì **body đã có primary exception** `IllegalStateException("body failed")`, exception từ close được đính vào mảng **suppressed exceptions** của primary exception.

Bởi vậy output cho thấy:

```text
primary: body failed
suppressed count: 1
suppressed type: IOException
```

**Không được đánh mất suppressed exception khi debug.** Nó có thể chứa thông tin rằng một file/socket/channel không đóng sạch dù lỗi chính xuất phát từ xử lý nội dung.

### 6.4. Khi cả body và `close` thành công

`close` vẫn được gọi, nhưng không có exception. Chương trình không cần và không nên dùng `System.gc()` chỉ để “kích hoạt” đóng file.

## 7. Ngoại lệ, thứ tự đóng và partial acquisition

`try-with-resources` giải quyết những góc khó của cleanup:

### 7.1. Resource thứ hai mở thất bại

```java
try (InputStream first = openFirst();
     InputStream second = openSecond()) {
    use(first, second);
}
```

Đây là code minh họa, chưa độc lập. Nếu `openFirst()` thành công nhưng `openSecond()` ném exception, Java sẽ **đóng `first` đã được khởi tạo thành công**. Đây là một ưu điểm so với code mở nhiều resources rồi chỉ gọi `close` ở cuối body.

### 7.2. Nhiều `close` cùng ném lỗi

Các resource được đóng theo thứ tự ngược; nếu body đã ném lỗi, các lỗi trong close được **suppressed** phù hợp. Nếu body hoàn tất bình thường nhưng một close ném lỗi, lỗi từ close có thể trở thành primary exception. Với nhiều close errors, những lỗi tiếp theo có thể được suppressed vào lỗi chính theo quy tắc Java.

Do đó, không thể suy luận mọi trường hợp `close` thất bại đều bị nuốt; cần kiểm tra exception chain và `getSuppressed()`.

### 7.3. Đừng thay `try-with-resources` bằng `finally` sai

Ví dụ dễ mất thông tin:

```java
Resource r = open();
try {
    doWork(r);
} finally {
    r.close(); // Nếu cũng ném, có nguy cơ che lấp lỗi gốc.
}
```

Đây là code minh họa chưa độc lập. Khi có thể, `try-with-resources` giúp Java giữ quan hệ primary/suppressed exception đúng hơn so với một khối finally tự viết chưa xét đầy đủ các tình huống lỗi.

### 7.4. Đừng bắt mọi lỗi rồi tiếp tục như thể đóng thành công

`IOException` từ `close` có thể báo flush không thành công, mất kết nối, hỏng đĩa hoặc một sự cố I/O khác. Cách xử lý đúng tùy nghiệp vụ: propagate, retry có giới hạn, đánh dấu transaction thất bại, báo lỗi hoặc log có ngữ cảnh.

Không làm:

```java
try {
    resource.close();
} catch (Exception ignored) {
    // Luôn coi thành công: anti-pattern.
}
```

Mã trên là **anti-pattern minh họa**, không phải quy tắc khuyến nghị.

## 8. File, stream và channel: đóng đúng đối tượng sở hữu

### 8.1. `InputStream`, `OutputStream`, `Reader`, `Writer`

Các stream/writer thường đại diện khả năng đọc/ghi. Việc đóng:

- Kết thúc sử dụng resource theo hợp đồng API.
- Có thể flush các buffers phù hợp trước khi release.
- Có thể chuyển OS handle sang trạng thái đã đóng.
- Có thể ném exception; phải xử lý.

Nếu dùng `BufferedInputStream` bọc một stream khác, cần xác định wrapper có sở hữu underlying stream không. Trong API thông thường, đóng wrapper cũng đóng underlying stream; nếu underlying stream còn được dùng ở nơi khác, việc đóng wrapper có thể làm hỏng caller.

### 8.2. `FileChannel` và `SeekableByteChannel`

`FileChannel` là một `Channel` và có `close()` theo API. Dùng try-with-resources:

```java
try (java.nio.channels.FileChannel channel =
         java.nio.channels.FileChannel.open(path,
                 java.nio.file.StandardOpenOption.READ)) {
    long size = channel.size();
    // Xử lý size.
}
```

Đây là **code minh họa**, `path` được khai báo trong context. Đóng channel là hoạt động lifecycle độc lập với GC heap object.

### 8.3. `MappedByteBuffer` và mapped memory

Memory-mapped files phức tạp hơn vì mapping và OS pages có vòng đời riêng. Dùng `FileChannel.map` có thể tạo một mapped buffer; **đóng FileChannel không đồng nghĩa mọi mapping được unmapped ngay** theo mọi tình huống.

Không hứa “try-with-resources cho channel sẽ lập tức unmap mọi buffer còn reachable”. Việc lifecycle của mapping còn phụ thuộc buffer, JVM/OS và các API hỗ trợ. Nếu cần deterministic unmapping, thiết kế qua API thích hợp của JDK mục tiêu thay vì dùng private `Unsafe` tricks không portable.

### 8.4. Flush không phải close, và close không phải fsync universal

- `flush()` thường yêu cầu đẩy dữ liệu đã buffer xuống tầng dưới, theo API.
- `close()` kết thúc sử dụng resource, đôi khi flush trước khi release.
- Bảo đảm dữ liệu đã thực sự **durable** trên thiết bị lưu trữ là vấn đề khác, có thể cần các API như `FileChannel.force` và cân nhắc hợp đồng OS/file system.

Đừng nhầm “file descriptor đóng xong” với “mọi byte đã được đảm bảo ghi vĩnh viễn bất chấp mất điện”.

## 9. Socket: lifetime của Java wrapper khác kết nối TCP

Một `java.net.Socket` thường đại diện một endpoint TCP. Khi socket đã kết nối, OS giữ trạng thái network, buffers và descriptors ngoài Java heap.

### 9.1. Đóng socket chủ động

Ví dụ Java source minh họa:

```java
import java.net.Socket;

try (Socket socket = new Socket("example.com", 443)) {
    socket.setSoTimeout(5000);
    // Giao tiếp theo protocol tương ứng.
}
```

**Không phải chương trình độc lập**: cần method/exception handling thích hợp, network access và một protocol hợp lệ. Đừng coi ví dụ là HTTP/TLS client hoàn chỉnh vì kết nối TCP tới port 443 không tự thực hiện TLS handshake hay HTTP request.

Khi try block kết thúc, `Socket.close()` được gọi theo hợp đồng. GC không phải tác nhân được dùng để đảm bảo socket đóng đúng thời điểm.

### 9.2. Đóng socket không phải xác nhận dữ liệu đã đến peer

TCP là một protocol có trạng thái. `close()` bắt đầu/hoàn tất những hoạt động đóng phía local theo API/OS, nhưng **không tự trở thành một ACK ở mức nghiệp vụ** rằng server đã xử lý transaction hoặc ghi dữ liệu vào database.

Muốn xác nhận xử lý thành công, ứng dụng cần protocol response, timeout và chiến lược retry/idempotency phù hợp.

### 9.3. `shutdownInput`, `shutdownOutput` khác `close`

`shutdownOutput()` và `shutdownInput()` có hợp đồng **half-close** của socket ở những trường hợp được hỗ trợ; không nên tùy tiện coi chúng tương đương `Socket.close()`.

Một ứng dụng streaming đôi khi cần báo đã gửi xong data bằng half-close nhưng vẫn đọc response. Nếu gọi close quá sớm, nó có thể phá hỏng protocol flow.

### 9.4. Socket timeout không thay resource cleanup

`setSoTimeout` điều chỉnh thời gian chờ đọc theo API, giúp tránh một số blocking read vô thời hạn. Nhưng timeout không mặc nhiên đóng socket, không tự dọn listeners hoặc đóng connection pool khi hết request. Cleanup và timeout là **hai lớp bảo vệ bổ sung**.

### 9.5. `ServerSocket` cũng cần close

`ServerSocket` có thể giữ port bind và OS descriptor. Hết scope của biến server socket không hứa port được release ngay. Dùng try-with-resources và explicit shutdown/cancellation cho accept loop khi phù hợp.

## 10. JDBC: `Connection.close()` có thể trả về pool, không phải đóng TCP thật

Trong ứng dụng production, kết nối database thường đi qua **connection pool**. Một lời gọi:

```java
try (java.sql.Connection connection =
         dataSource.getConnection()) {
    // Sử dụng connection.
}
```

là **đoạn mã minh họa**, `dataSource` phải được cấu hình. Với một connection wrapper từ pool, `close()` thường có nghĩa **trả logical connection về pool** sau khi xử lý trạng thái phù hợp, không nhất thiết hủy underlying physical socket.

Đây là ví dụ điển hình rằng:

> **Hợp đồng đóng một resource không được suy ra từ tên kiểu Java mà phải đọc tài liệu của provider và ownership model.**

### 10.1. Đóng cả `ResultSet`, `Statement` khi sở hữu

```java
try (Connection c = dataSource.getConnection();
     PreparedStatement p =
             c.prepareStatement("SELECT name FROM users WHERE id = ?")) {
    p.setLong(1, userId);
    try (ResultSet rs = p.executeQuery()) {
        if (rs.next()) {
            String name = rs.getString(1);
            // Xử lý name.
        }
    }
}
```

Đây là code minh họa, cần `dataSource`, `userId` và imports/driver phù hợp. Việc đóng theo scope giúp tránh giữ cursor, statement hoặc connection pool slot không cần thiết.

### 10.2. Transaction lifecycle khác GC

Một transaction cần logic rõ:

- `commit()` khi nghiệp vụ thành công và các điều kiện được thỏa.
- `rollback()` khi gặp lỗi hoặc validation thất bại.
- `close()`/trả connection theo hợp đồng provider.

**Không giả định `GC đã thu hồi Connection object` đồng nghĩa transaction đã commit.** Việc connection được close có thể làm transaction rollback theo behavior của driver/pool/DB, nhưng không thay quyết định giao dịch rõ ràng.

### 10.3. Connection pool exhaustion

Một endpoint quên gọi close có thể làm pool hết available connections. Lúc đó hệ thống có thể treo chờ hoặc timeout dù **Java heap còn rất nhiều memory**.

Cần theo dõi pool metrics: active, idle, pending acquisition, timeout, connection age và ứng xử release. GC logs không đủ để chứng minh pool lifecycle đúng.

## 11. Executor, thread và process: “đóng” không phải một thao tác chung

Không phải mọi tài nguyên đều có đúng method tên `close` hoặc cùng hợp đồng:

| Resource | API lifecycle thường dùng | Lưu ý |
| --- | --- | --- |
| `InputStream` / `Reader` | `close()` | Có thể ném `IOException` |
| `FileChannel` | `close()` | File/channel ownership |
| `Socket` / `ServerSocket` | `close()` | OS socket và protocol semantics |
| JDBC `Connection` | `close()` | Có thể trả connection vào pool |
| JDBC `Statement`, `ResultSet` | `close()` | Thường cần nested try-with-resources |
| `ExecutorService` | `shutdown()`, `shutdownNow()`, `awaitTermination()` | Dừng nhận task và đợi task kết thúc theo API |
| `Process` | `destroy()`, `destroyForcibly()`, `waitFor()` | Điều khiển tiến trình con, không phải GC |
| Application listener/registration | `removeListener`, `unregister` | Phải cắt nguồn callback khi hết lifecycle |
| `ThreadLocal` | `remove()` | Tránh giữ request state giữa các pool tasks |
| Lock/semaphore | `unlock()` / `release()` | Cân bằng acquire/release, không phải heap free |

**Lưu ý phiên bản:** JDK 17 `ExecutorService` chủ yếu dùng `shutdown`/`awaitTermination`; không áp dụng bừa những tiện ích hoặc cách dùng try-with-resources được thêm vào interface ở các JDK mới hơn cho code `--release 17`.

### 11.1. Executor shutdown đúng cách

```java
ExecutorService pool = Executors.newFixedThreadPool(2);
try {
    // Submit công việc và xử lý kết quả theo hợp đồng.
} finally {
    pool.shutdown();
    if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
        pool.shutdownNow();
    }
}
```

Đây là code minh họa, cần imports và xử lý `InterruptedException`. `shutdown()` là yêu cầu ngừng nhận công việc mới và cho các tasks đang có cơ hội hoàn tất; nó **không bảo đảm tức thì mọi thread đã chết**. `awaitTermination` giúp caller chờ có giới hạn. `shutdownNow` cố gắng ngắt theo cơ chế tương ứng, không cam kết kill mọi tác vụ bất kể chúng có phản hồi interruption không.

### 11.2. ThreadLocal retention

Một worker thread trong pool có thể sống qua hàng nghìn requests. Dù biến local của request đã hết scope, giá trị `ThreadLocal` có thể vẫn được giữ qua thread-local map.

```java
try {
    CONTEXT.set(requestContext);
    handleRequest();
} finally {
    CONTEXT.remove();
}
```

Code trên là **mẫu minh họa**, cần định nghĩa `CONTEXT`, `requestContext` và `handleRequest`. `remove()` là cleanup của **logical scope request**, không phải lệnh yêu cầu GC.

## 12. `Cleaner`: cleanup dự phòng khi object trở thành phantom reachable

Java 9+ có `java.lang.ref.Cleaner` để đăng ký một cleaning action tương ứng object. Cleaner dựa trên **`PhantomReference` và `ReferenceQueue`** để nhận biết thay đổi reachability.

Cần hiểu ba điểm:

1. Cleaning action được chạy khi GC/reference processing báo object trở thành **phantom reachable**, hoặc khi code **explicitly gọi `Cleanable.clean()`**.
2. **Không có deadline bảo đảm** khi action do GC kích hoạt; trước khi process kết thúc có thể nó chưa chạy.
3. **Action không được giữ strong reference tới object được đăng ký**, nếu không object có thể không trở thành phantom reachable và cleaner tự động sẽ không được kích hoạt.

Vì thế **Cleaner là safety net**, không thay `try-with-resources` hoặc explicit close với file/socket/DB cần release đúng thời điểm.

### 12.1. Ví dụ pattern an toàn

```java
import java.lang.ref.Cleaner;

final class NativeHandle implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    private static final class State implements Runnable {
        private boolean cleaned;

        @Override
        public synchronized void run() {
            if (cleaned) return;
            cleaned = true;
            // Release native handle do State sở hữu.
        }
    }

    private final State state = new State();
    private final Cleaner.Cleanable cleanable =
            CLEANER.register(this, state);

    @Override
    public void close() {
        cleanable.clean();
    }
}
```

Đây là **class minh họa**, chưa thật sự cấp phát native handle. `State` được khai báo `static` để không capture `NativeHandle.this`. Trong code thật, `State` phải giữ đủ dữ liệu để giải phóng tài nguyên, nhưng **không được vô tình giữ chính wrapper referent**.

**Không đăng ký `this::cleanup` hoặc lambda dùng fields của `this` làm cleaning action** nếu nó giữ strong reference tới referent, bởi điều này có thể cản trở GC-based cleanup.

## 13. Ví dụ Java 17+ đầy đủ: gọi Cleaner chủ động, không đợi GC

Tạo tệp **`CleanerSafetyDemo.java`**:

```java
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CleanerSafetyDemo implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    private static final class State implements Runnable {
        private final AtomicBoolean cleaned = new AtomicBoolean();

        @Override
        public void run() {
            if (cleaned.compareAndSet(false, true)) {
                System.out.println("cleaned once");
            }
        }
    }

    private final Cleaner.Cleanable cleanable =
            CLEANER.register(this, new State());

    void use() {
        System.out.println("using wrapper");
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    public static void main(String[] args) {
        try (CleanerSafetyDemo resource = new CleanerSafetyDemo()) {
            resource.use();
        }
        System.out.println("explicit close finished");
    }
}
```

Chạy:

```bash
javac --release 17 CleanerSafetyDemo.java
java CleanerSafetyDemo
```

**Stdout chính xác:**

```text
using wrapper
cleaned once
explicit close finished
```

Ví dụ đã được biên dịch với `--release 17` và chạy thành công trên OpenJDK 21.

Giải thích:

- `Cleaner.register(this, new State())` đăng ký wrapper là referent và một `State` **không tham chiếu tới wrapper**.
- `try-with-resources` chủ động gọi `close()`.
- `close()` gọi `cleanable.clean()`, nên action chạy theo đường **explicit** và in `cleaned once` trước khi block hoàn tất.
- Code **không gọi GC**, không đợi phantom reachability; vì vậy stdout không phụ thuộc lịch GC.
- `AtomicBoolean` làm hành vi action idempotent trong demo; theo hợp đồng Cleaner, một cleanable action được thực thi tối đa một lần theo cơ chế thích hợp.
- Demo **không quản lý OS handle thật**, chỉ biểu diễn mối quan hệ giữa close và Cleaner. Các tài nguyên thật cần logic release chính xác và xử lý lỗi riêng.

### 13.1. Tại sao Cleaner không nên được dùng cho transaction quan trọng?

Nếu chương trình bỏ quên `close()`, action phụ thuộc GC có thể chỉ chạy muộn hoặc không kịp trước khi process kết thúc. Transaction không thể dựa vào “đến khi object phantom reachable thì commit/rollback” để có consistency theo deadline.

## 14. `Reference.reachabilityFence` và native resource wrappers

Một trường hợp tinh vi xuất hiện khi Java wrapper sở hữu native handle, nhưng native operation chỉ nhận một giá trị số như địa chỉ/handle:

```java
static void perform(NativeWrapper owner) {
    long handle = owner.handle();
    nativeCall(handle);
    java.lang.ref.Reference.reachabilityFence(owner);
}
```

Đây là **mã minh họa**; `NativeWrapper` và `nativeCall` chưa được khai báo. Compiler/JIT có thể nhận thấy bản thân `owner` không còn được sử dụng qua Java reference trong native operation sau khi lấy `handle`, trong khi cleaning mechanism có thể release native handle nếu wrapper trở thành phantom reachable quá sớm.

`Reference.reachabilityFence(owner)` bảo đảm **object strongly reachable ít nhất tới sau invocation này**, giúp ngăn một số trường hợp cleanup sớm khi native operation vẫn đang sử dụng handle.

Nhưng nó:

- Không gọi `close()`.
- Không gọi `System.gc()`.
- Không pin object tại một địa chỉ heap bất biến.
- Không giải quyết vấn đề thread khác chủ động gọi `close()` đồng thời.
- Không thay một ownership protocol có synchronization phù hợp.

Đối với file stream Java thông thường, **try-with-resources** thường là abstraction đúng hơn; `reachabilityFence` đặc biệt phù hợp ở các tầng native-resource wrapper được thiết kế cẩn thận.

## 15. Native/off-heap buffers có vòng đời riêng

Một số API phân bổ bộ nhớ **ngoài Java heap**, chẳng hạn direct buffers hoặc native allocations. Số byte Java heap nhìn thấy trong profiler có thể nhỏ hơn tổng RSS của tiến trình.

### 15.1. `ByteBuffer.allocateDirect`

```java
java.nio.ByteBuffer direct =
        java.nio.ByteBuffer.allocateDirect(1024 * 1024);
```

Đây là code minh họa. Direct buffer giữ data vùng memory mà VM cố gắng hỗ trợ I/O/native access hiệu quả, không phải một mảng Java heap `byte[]` thông thường.

Khả năng reclaim native memory của direct buffer phụ thuộc VM và các cơ chế buffer/reference processing. **Không có `close()` chuẩn trên mọi `ByteBuffer`** để ứng dụng gọi như `InputStream`.

Với những resources đòi deterministic lifecycle, chọn API có hỗ trợ **explicit ownership/release** ở JDK mục tiêu, ví dụ các cấu trúc Foreign Function & Memory API nếu dùng JDK hỗ trợ ổn định. Không dùng private `Unsafe.invokeCleaner` như một lời hứa portable của Java SE.

### 15.2. `FileChannel.map` / mapped buffers

Mapped memory có thể tồn tại sau khi channel đóng. Mặc dù channel close giải phóng những resources theo API, memory mapping có semantics/lifetime riêng. Đừng coi GC của Java wrapper hay đóng channel là bằng chứng mapping đã được unmap tại đúng thời điểm.

### 15.3. RSS cao không đồng nghĩa heap leak

Khi container bị kill vì vượt memory limit, kiểm tra thêm:

- Heap used/committed và GC.
- Metaspace/class metadata.
- Native allocations, direct/mapped buffers.
- Thread stacks.
- Code cache, VM/GC native structures.
- File/socket handles và những hệ quả tài nguyên khác.

Đây là phần mở rộng để giải thích **ngoài-heap**, không có nghĩa mọi FD leak tự làm RSS tăng theo cùng tỉ lệ; FD count và heap/RSS là những chỉ số độc lập.

## 16. Idempotency, thread safety và đóng nhiều lần

Không được giả định tất cả `close()` của mọi `AutoCloseable` đều có cùng semantics. Một số resource được thiết kế **idempotent close**; một số có state hoặc exception behavior riêng.

Trong code ứng dụng tự viết, có thể ưu tiên `close` idempotent nếu điều đó phù hợp hợp đồng:

```java
final class Gate implements AutoCloseable {
    private boolean closed;

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        releaseOwnedResource();
    }
}
```

Đây là **mã minh họa**, `releaseOwnedResource` chưa được định nghĩa. Với nhiều threads, biến `closed` không có synchronization phù hợp **không đủ bảo đảm thread-safe close**. Cần lập kế hoạch state machine, atomic/synchronized operations, tránh use-after-close và double-free.

### 16.1. Close cùng lúc với read/write

Nếu một thread đang sử dụng socket/channel, thread khác gọi close, kết quả phụ thuộc API (có thể ném exception/hủy blocking I/O). Không coi GC hoặc `volatile boolean closed` đơn giản là giải pháp cho concurrency correctness.

Trong API low-level native, use-after-free còn có thể gây lỗi nghiêm trọng. Quy tắc ownership phải xác định ai đang mượn handle và khi nào có quyền release nó.

### 16.2. Nested resources và shared underlying handle

Nếu tạo hai wrappers cùng sở hữu một underlying stream, đóng một wrapper có thể làm wrapper khác không dùng tiếp được. Không mặc định “mỗi wrapper đóng một lần” là luôn đúng khi có **shared ownership**.

Khi thực sự muốn stream còn open cho caller, dùng API có hợp đồng mượn/lending rõ hoặc wrapper non-closing được thiết kế riêng, thay vì lặng lẽ bỏ close và hy vọng GC dọn.

## 17. Phân biệt resource leak và memory leak

| Hiện tượng | Dấu hiệu thường gặp | Điều cần xem |
| --- | --- | --- |
| **Java heap retention/leak** | Live heap hoặc retained object graph tăng | GC roots, static collections, cache, ThreadLocal |
| **File descriptor leak** | Số OS FDs tăng, không mở được file/socket mới | Paths mở chưa đóng, resource ownership |
| **Socket/connection leak** | TCP connections/pool active tăng, timeout khi lấy connection | `Socket.close()`, pool metrics, protocol/lifecycle |
| **Native memory leak** | RSS/native allocation tăng ngoài heap | Direct buffers, JNI/native allocations, NMT |
| **Thread leak** | Threads sống lâu không cần thiết, stack memory tăng | Executor shutdown, task cancellation |
| **Database cursor leak** | Cursor/statement resource exhaustion | `ResultSet`, `Statement`, driver/pool semantics |
| **Mapped memory retention** | Memory mappings/handles persist | Mapped buffers và API unmap/lifecycle |
| **Class loader retention** | Metaspace/class counts tăng sau redeploy | Loader references, reflection caches, listeners |

Một resource leak có thể dẫn đến memory pressure, nhưng **không bắt buộc luôn biểu hiện trước bằng `OutOfMemoryError: Java heap space`**. Một heap leak cũng không nhất thiết liên quan FD count.

**Đừng dùng “GC log trông bình thường” để bác bỏ resource leak.** GC logs quan sát memory-management behavior, không đếm mọi external resource đang giữ.

## 18. Chẩn đoán trên JVM và hệ điều hành

### 18.1. Đếm file descriptors

Trên Linux, có thể xem OS-level FD count với những công cụ như `lsof` hoặc `/proc/<pid>/fd` (phụ thuộc quyền truy cập/container):

```bash
lsof -p <pid>
ls /proc/<pid>/fd
```

`<pid>` là placeholder, thay bằng PID thật của Java process. Đây là lệnh quan sát phục vụ chẩn đoán; không phải hành vi phải chạy trong chương trình Java.

**Không phải mọi nền tảng đều có `/proc` hoặc `lsof`.** Windows có công cụ handles/process monitoring riêng; không chuyển lệnh Linux sang Windows rồi kỳ vọng chạy được.

### 18.2. Theo dõi pool và sockets

- Database connection pool: active/idle/pending/timeouts.
- HTTP client connection pool: leased/idle/available connections.
- Server sockets: accepted connections, thời điểm đóng, số concurrent connections.
- OS socket states: established, close-wait, time-wait… phải diễn giải theo TCP semantics và OS, không xem mọi `TIME_WAIT` là leak.

Ví dụ nhiều `CLOSE_WAIT` kéo dài có thể gợi ý phía local chưa đóng socket sau khi peer đóng, nhưng cần đối chiếu application behavior và hệ điều hành.

### 18.3. JVM tools

Trên HotSpot JDK có thể dùng:

```bash
jcmd -l
jcmd <pid> GC.class_histogram
jcmd <pid> Thread.print
```

Để xem native memory theo NMT nếu đã bật từ startup:

```bash
jcmd <pid> VM.native_memory summary
```

**NMT không phải bộ đếm FDs/sockets.** Nó theo dõi nhiều hạng mục native allocations của VM, không nhất thiết bao gồm toàn bộ thư viện native bên ngoài hoặc lifecycle của mọi connection.

`GC.class_histogram` cho thấy số instance Java theo type, nhưng không chứng minh bao nhiêu file handles chưa đóng. Một `FileInputStream` object có thể còn trong heap nhưng đã đóng; một provider có thể có wrappers pooling/handles riêng.

### 18.4. Resource telemetry quan trọng hơn một lần lấy heap dump

Nên theo dõi theo thời gian:

- FD/handle count.
- Socket count theo trạng thái.
- Pool active/idle/waiting.
- Open-file/channel counts nếu thư viện công bố metrics.
- Heap allocated/live/committed và GC.
- Native/RSS memory.
- Rate của successful/failed close.
- Latency và timeout của resource acquisition.

Dùng cùng workload để so sánh trước/sau bản sửa. Không kết luận “sửa leak” chỉ vì một snapshot có con số thấp.

### 18.5. Quan sát close có xảy ra trong mọi control path?

Một kỹ thuật kiểm thử là wrap resource bằng lớp có đếm:

```java
final class CloseCounter implements AutoCloseable {
    int closes;
    @Override
    public void close() { closes++; }
}
```

Đây là snippet minh họa. Có thể dùng nó trong unit test để xác minh cleanup dù body ném exception hoặc return sớm. Với concurrency, dùng thread-safe counters nếu nhiều threads thao tác cùng đối tượng.

## 19. Trường hợp dễ bỏ sót: phản hồi HTTP và stream trả về

Ví dụ một thư viện HTTP cho phép truy cập **response body dưới dạng stream**. Dù client object/response wrapper còn scope ngắn, body stream hoặc response object có thể phải đóng theo tài liệu để:

- Trả connection về pool.
- Tránh leak underlying socket.
- Cho phép tái sử dụng connection.
- Đảm bảo chunked/body data được xử lý theo chính sách hợp lệ.

Với Java 11+ `java.net.http.HttpClient`, các body handlers khác nhau có lifecycle khác nhau. Đặc biệt, nếu nhận body dưới dạng `InputStream`, caller cần quản lý đóng stream theo API; không suy luận chỉ từ `HttpClient` object có bị GC hay chưa.

Một ứng dụng có thể dùng hàng nghìn HTTP requests nhưng lại giữ response streams lâu hơn yêu cầu, làm cạn connection pool trước khi Java heap hết.

## 20. Thiết kế ownership theo tầng

Một hướng kiến trúc rõ ràng:

```text
Application startup
     |
     +--> tạo long-lived pools/clients
     |         |
     |         +--> shutdown một lần lúc application stop
     |
     +--> mỗi request / task
               |
               +--> borrow connection/stream
               |
               +--> try-with-resources
               |       |
               |       +--> xử lý
               |       +--> commit/rollback theo nghiệp vụ
               |       +--> close / trả pool slot
               |
               +--> trả response
```

Không bắt buộc tạo/đóng một client heavyweight cho mọi request. Một số clients/pools **được thiết kế để tái sử dụng** và phải được đóng lúc application shutdown, còn các sessions/connections/response streams có scope ngắn hơn.

### 20.1. Bảng câu hỏi nên ghi trong API contract

| Câu hỏi | Ví dụ câu trả lời cần có |
| --- | --- |
| Ai tạo resource? | Factory hoặc pool |
| Ai sở hữu? | Caller, callee, application singleton |
| Ai có quyền đóng? | Owner hoặc wrapper có trách nhiệm |
| Đóng bằng cách nào? | `close`, `shutdown`, `rollback`, `cancel` |
| Khi nào đóng? | Cuối request, cuối transaction, application shutdown |
| Nếu close lỗi thì sao? | Propagate/log/retry/mark failure phù hợp |
| Dùng đồng thời có an toàn không? | Theo tài liệu thread-safety |
| Sau close có được tái sử dụng? | Theo contract từng API |
| Có fallback Cleaner không? | Chỉ bảo vệ phụ, không phụ thuộc deadline |

### 20.2. Bọc tài nguyên trong abstraction có lifecycle

Nếu business code thao tác file/native handle phức tạp, hãy tạo class **implement `AutoCloseable`**, có state machine rõ và tài liệu ownership. Sau đó callers dùng try-with-resources để đóng tài nguyên **dự đoán được**, không phải một collection event.

## 21. Những hiểu lầm phổ biến

### Sai lầm 1: “Java có GC nên không cần `close()`”
GC dọn Java heap theo reachability, không bảo đảm đóng mọi external resource đúng thời điểm.

### Sai lầm 2: “Biến ra khỏi `{ }` là OS file descriptor giải phóng”
Lexical scope không là deadline của OS resource. Cleanup phải dựa theo lifecycle/ownership.

### Sai lầm 3: “Đặt stream = null thì file đóng”
Đặt null chỉ thay reference edge. Không buộc library gọi close hay OS release handle ngay.

### Sai lầm 4: “`System.gc()` là cách chuẩn để dọn sockets”
Không. GC scheduling không phải network resource release API.

### Sai lầm 5: “Một resource đã close thì Java object phải biến mất”
Java wrapper vẫn có thể reachable; close và reclaim là hai trạng thái khác nhau.

### Sai lầm 6: “Nếu heap có nhiều dung lượng thì không thể hết FD”
FD pool/OS handles có giới hạn độc lập.

### Sai lầm 7: “`close()` của pooled JDBC connection chắc chắn đóng physical TCP socket”
Không. Trong pool, close có thể là trả logical connection về pool.

### Sai lầm 8: “try-with-resources chỉ close lúc body chạy thành công”
Sai. Java gọi close cả khi body ném exception hoặc return sớm; cần đọc primary/suppressed exception rules.

### Sai lầm 9: “Close exception luôn thay thế exception gốc”
Không. try-with-resources có suppressed exceptions để giữ thông tin đóng thất bại khi body đã có primary exception.

### Sai lầm 10: “Mọi `AutoCloseable.close` đều idempotent và thread-safe”
Không. Phải đọc hợp đồng cụ thể; tự viết `close` thread-safe khi có multi-thread ownership.

### Sai lầm 11: “Đóng `FileChannel` luôn unmap mapped buffer ngay”
Không có bảo đảm portable như vậy.

### Sai lầm 12: “Socket.close xác nhận remote server đã commit”
Không. TCP close không thay application-level acknowledgement.

### Sai lầm 13: “Cleaner thay được transaction rollback/commit”
Không. Cleaner do GC kích hoạt không có deadline hoặc transaction semantics.

### Sai lầm 14: “Làm Cleaner action gọi `this.close()` là an toàn”
Có thể giữ strong reference tới referent, khiến action không bao giờ chạy theo đường GC. Nên tách State khỏi wrapper.

### Sai lầm 15: “Finalizer chắc chắn chạy trước shutdown”
Không. Finalization deprecated for removal và không cho progress guarantee.

### Sai lầm 16: “GC heap ổn định thì không có native leak”
Sai. Native allocations, FD counts và sockets độc lập.

### Sai lầm 17: “`ThreadLocal` tự clear khi HTTP request kết thúc”
Sai. Worker thread có thể sống qua nhiều requests; request-scope cleanup cần explicit remove.

### Sai lầm 18: “Close/cleanup lỗi có thể bỏ qua vì GC sẽ xử lý lại”
Không có guarantee GC sẽ sửa được tình trạng resource đang lỗi hay chạy đúng logic cleanup thêm một lần.

## 22. Bài tập tự kiểm tra và đáp án

**Câu 1.** Tại sao `new FileInputStream(path)` tạo một Java object không có nghĩa file sẽ đóng khi biến local hết scope?

**Đáp án:** Vì wrapper object và OS file descriptor có vòng đời khác nhau. GC không bảo đảm tự gọi close đúng thời điểm; dùng try-with-resources.

**Câu 2.** Khi `InputStream.close()` chạy thành công, Java wrapper có được GC ngay không?

**Đáp án:** Không bắt buộc. Wrapper có thể vẫn reachable; close quản lý tài nguyên chứ không free Java heap object.

**Câu 3.** Hai `AutoCloseable` được khai báo theo thứ tự `a` rồi `b` trong try-with-resources. Thứ tự close là gì?

**Đáp án:** `b.close()` trước `a.close()`.

**Câu 4.** Body ném `IllegalStateException`, close ném `IOException`. Với try-with-resources, đâu là primary exception?

**Đáp án:** Thông thường `IllegalStateException` từ body là primary; `IOException` từ close được lưu trong suppressed exceptions của primary theo quy tắc.

**Câu 5.** `Connection.close()` trong JDBC pool có nhất thiết đóng physical socket không?

**Đáp án:** Không. Provider có thể trả logical connection về pool; đọc tài liệu của pool/driver.

**Câu 6.** Heap còn 300 MiB free nhưng process mở file mới thất bại do FD limit. Tăng `-Xmx` là giải pháp gốc không?

**Đáp án:** Không. Cần tìm file/socket handles mở mà chưa close, giới hạn OS và lifecycle của chúng.

**Câu 7.** `System.gc()` có là cách chắc chắn release socket không?

**Đáp án:** Không. Dùng explicit `Socket.close()` hoặc try-with-resources.

**Câu 8.** Một Cleaner action capture chính wrapper đang đăng ký sẽ gây nguy cơ gì?

**Đáp án:** Strong reference từ action tới referent có thể khiến object không trở thành phantom reachable, ngăn automatic cleaning.

**Câu 9.** Tại sao khi dùng Cleaner vẫn nên gọi `Cleanable.clean()` từ `close()`?

**Đáp án:** Để cleanup theo lịch do caller kiểm soát; không phụ thuộc khi nào GC báo phantom reachability.

**Câu 10.** `Reference.reachabilityFence(owner)` có tự đóng file không?

**Đáp án:** Không. Nó đảm bảo owner strongly reachable qua điểm gọi, không thực hiện resource close.

**Câu 11.** `Socket.close()` có chứng minh server đã xử lý và lưu thành công transaction không?

**Đáp án:** Không. Cần application-level response/acknowledgement phù hợp.

**Câu 12.** Vì sao đóng `FileChannel` không đủ để khẳng định mapped buffer đã được unmap ngay?

**Đáp án:** Mapping có lifecycle/semantics riêng; channel close không áp đặt một guarantee unmap tức thì cho mọi JDK.

**Câu 13.** Một ThreadLocal value còn giữ object graph sau request; caller local đã ra scope. Object còn sống bằng cách nào?

**Đáp án:** Worker thread giữ thread-local map entry/value. Dùng `remove()` khi request kết thúc nếu state chỉ thuộc request.

**Câu 14.** `CleanerSafetyDemo` tự in `cleaned once` bằng cơ chế gì?

**Đáp án:** `try-with-resources` gọi `close()`, và close gọi `cleanable.clean()` explicit; không cần GC.

**Câu 15.** Một client HTTP dùng connection pool nên được tạo và đóng cho mỗi request không?

**Đáp án:** Không nhất thiết. Có thể reuse client/pool lâu dài; response body streams hoặc borrowed connections được đóng ở scope ngắn hơn theo API.

**Câu 16.** Chỉ xem `GC.class_histogram` có đủ kết luận file handles được đóng đúng không?

**Đáp án:** Không. Phải quan sát FD count, close behavior và resource ownership; histogram chỉ cho số Java objects theo type.

**Câu 17.** Tại sao nên xem suppressed exceptions?

**Đáp án:** Vì close của resource có thể thất bại khi body đã có lỗi chính; suppressed exceptions lưu nguyên nhân đóng thất bại có giá trị chẩn đoán.

**Câu 18.** `ByteBuffer.allocateDirect` có thể làm tăng native memory mà heap usage không tăng tương ứng không?

**Đáp án:** Có. Direct buffer liên quan native/off-heap memory; accounting và reclamation phụ thuộc VM.

## 23. Checklist áp dụng trong dự án

1. Với mỗi tài nguyên, ghi rõ **owner, borrow semantics, cách đóng và điểm đóng**.
2. Ưu tiên **try-with-resources** cho `AutoCloseable` với scope hữu hạn.
3. Đóng resource theo thứ tự phù hợp, tránh double-close shared handle.
4. Giữ suppressed exceptions khi body và close đều thất bại.
5. Trong JDBC, phân biệt transaction lifecycle, statement/result set và connection pool semantics.
6. Trong network code, phân biệt close local, half-close, timeout và acknowledgement nghiệp vụ.
7. Dùng explicit shutdown/cancellation cho executors, tasks, streams/listeners theo API.
8. Với `ThreadLocal` request data, dùng `finally` để `remove()` khi thích hợp.
9. Dùng Cleaner chỉ như **fallback**, bảo đảm action không capture referent.
10. Không dựa vào `finalize`, `System.gc()` hoặc `Thread.sleep` như cách đóng tài nguyên.
11. Với native wrappers, cân nhắc `reachabilityFence` và đồng bộ ownership khi có cạnh tranh giữa use/close.
12. Tách metrics heap/GC khỏi FD counts, socket states, pool metrics và native RSS.
13. Test đường success, exception, timeout, cancellation, partial acquisition và shutdown.
14. Đo resource usage qua nhiều requests/redeploy cycles thay vì một snapshot.
15. Nếu OS handle count tăng mãi, sửa **resource lifecycle** trước khi chỉ tăng giới hạn hệ điều hành.
16. Khi dùng mapped memory hoặc native FFM, đọc hợp đồng API theo JDK mục tiêu, tránh private implementation shortcuts.
17. Đảm bảo log lỗi đóng có ngữ cảnh nhưng không làm lộ secrets/PII.
18. Đừng tuyên bố **GC “không bao giờ” giúp release resource**: một số library có fallback cleanup; nhận định đúng là **GC không là cơ chế release deterministic theo deadline**.

### Tóm tắt cuối

```text
                Java object wrapper
                       |
        +--------------+---------------+
        |                              |
        v                              v
  Heap reachability            External resource state
        |                              |
        v                              v
  GC may reclaim               explicit close / release
  object memory                (owner-controlled)
        |                              |
        +--------------+---------------+
                       |
         KHÔNG đồng nghĩa cùng thời điểm

try-with-resources:
  cấp phát -> sử dụng -> close chắc chắn theo Java control flow
  (khi thực thi tới các điểm tương ứng; close vẫn có thể thất bại)

Cleaner:
  fallback cleanup theo phantom reachability;
  không có deadline, nên explicit clean/close là đường chính
```

**Thông điệp cần nhớ:** **GC quản lý bộ nhớ Java objects; ứng dụng phải quản lý tài nguyên bên ngoài theo hợp đồng sở hữu và đóng rõ ràng.** File descriptor, socket, connection, native allocation và worker threads không được phép phụ thuộc vào lúc một biến hết scope hay lúc collector tình cờ chạy.

### Tài liệu chuẩn

- [JVMS §2.5.3 — Heap (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): heap objects và automatic storage management.
- [JLS §14.20.3 — try-with-resources (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): quy tắc khởi tạo/đóng theo thứ tự ngược, exceptions và suppressed exceptions.
- [AutoCloseable API (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): hợp đồng close và khuyến nghị try-with-resources.
- [Closeable API (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/Closeable.html): close với I/O resources.
- [Cleaner API (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html): phantom reachability, explicit `clean` và ngăn action capture referent.
- [Reference.reachabilityFence API (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html#reachabilityFence(java.lang.Object)): giữ mạnh wrapper tới điểm dùng cuối.
- [Socket API (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/Socket.html): close, half-close và timeout.
- [Files.newBufferedWriter (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/Files.html#newBufferedWriter(java.nio.file.Path,java.nio.charset.Charset,java.nio.file.OpenOption...)): mở writer trên filesystem.
- [Connection API (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.sql/java/sql/Connection.html): transaction và close theo JDBC.
- [OpenJDK JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421): hạn chế và lộ trình loại bỏ finalization.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-008">Finalization, tình trạng deprecation và lựa chọn try-with-resources hoặc Cleaner</a>

<details>
<summary>Xem chi tiết</summary>

# Finalization, deprecation và lựa chọn `try-with-resources` hoặc `Cleaner` trong Java

**Finalization** là cơ chế lâu đời của Java: một object có thể định nghĩa phương thức `finalize()`, và JVM có thể gọi nó sau khi xác định object không còn được truy cập theo những điều kiện nhất định của GC. Nó từng được dùng để đóng file, socket, database connection hoặc giải phóng native handle khi lập trình viên quên cleanup.

Tuy nhiên, **finalization không cung cấp thời điểm thực thi đáng tin cậy, có nguy cơ làm sống lại object, tạo lỗi concurrency, deadlock, rò tài nguyên và ảnh hưởng hiệu năng**. Java hiện khuyến nghị **không viết finalizer mới**. Với tài nguyên phải giải phóng đúng hạn, dùng **`AutoCloseable` + `try-with-resources`**; khi thực sự cần một tuyến dọn dẹp dự phòng lúc object không còn reachable, dùng **`java.lang.ref.Cleaner`**, đồng thời vẫn ưu tiên `close()` rõ ràng.

Bài học thuộc phần **Khả năng truy cập** trong JVM, tập trung vào reachability, vòng đời object và lựa chọn API phù hợp. Ví dụ chính chạy với **Java 17+**, chỉ minh họa kết quả stdout khi kiểm soát được thời điểm, **không giả định JVM chắc chắn chạy GC/finalizer/Cleaner trước một deadline**.

## 1. Phân biệt GC, finalization và cleanup tài nguyên

Có ba công việc thường bị nói gộp:

1. **GC memory reclamation:** JVM nhận ra object trên Java heap không còn cần thiết theo quy tắc reachability và có thể thu hồi vùng nhớ heap của object.
2. **Finalization:** cơ chế legacy có thể gọi override `Object.finalize()` trên một object đủ điều kiện, trước khi vùng nhớ của object được thu hồi theo trình tự hỗ trợ finalization.
3. **Resource cleanup:** đóng socket, file descriptor, transaction, native pointer hoặc lock/connection. Nhiều tài nguyên này **không tự được giải phóng đúng hạn chỉ vì Java object wrapper trở nên unreachable**.

```text
Java object trên heap
       │
       ├─ vẫn strongly reachable
       │     └─ ứng dụng đang giữ và sử dụng object
       │
       └─ không còn strong reachability
             │
             ├─ GC có thể phân tích reachability và xử lý references
             ├─ legacy finalization có thể chạy (nếu object có finalizer,
             │   runtime vẫn bật, và các điều kiện áp dụng)
             ├─ Cleaner có thể nhận phantom reachability notification
             │   và chạy cleaning action được đăng ký
             └─ heap storage có thể được tái sử dụng khi GC cho phép

Không nhầm:
  "object có thể được collect"
       ≠ "finalize chắc chắn đã chạy"
       ≠ "native/socket resource đã đóng đúng thời hạn".
```

GC có trách nhiệm với **Java heap**, nhưng connection pool, file descriptor, native allocation và OS handle còn có chính sách sở hữu/đóng riêng. Thiết kế chỉ dựa vào finalization có thể khiến một ứng dụng cạn file descriptors dù heap vẫn còn nhiều dung lượng và collector chưa muốn chạy.

### 1.1. Tại sao mạnh-yếu-ảo quan trọng?

Package `java.lang.ref` mô tả **strong, soft, weak, phantom reachability**. Finalization lịch sử có mối liên hệ với các giai đoạn reachability và xử lý object; `Cleaner` dùng cơ chế **phantom references** cùng reference queue để nhận thông báo khi đối tượng đăng ký không còn được truy cập bình thường.

Điểm khác cốt lõi: **Cleaner không gọi method của referent bằng cách lấy lại object qua `PhantomReference.get()`**. Cleaning action phải cầm **state độc lập** cần thiết để cleanup, vì phantom `get()` luôn trả `null`.

## 2. Tình trạng deprecated qua các JDK releases

Cần phân biệt việc **một API đánh dấu deprecated**, **deprecated for removal**, **disabled by default** và **removed**. Đó là bốn tình trạng khác nhau.

| Phiên bản / mốc | Tình trạng đáng nhớ |
| --- | --- |
| **Java 9** | `Object.finalize()` được đánh dấu **`@Deprecated(since="9")`**; `Cleaner` được giới thiệu |
| **JDK 17** | `Object.finalize()` vẫn tồn tại nhưng **deprecated**; finalization là cơ chế legacy, không nên dùng cho code mới |
| **JDK 18 – JEP 421** | Finalization được **deprecated for removal**; nhiều API liên quan được đánh dấu tương ứng; có tùy chọn `--finalization=disabled` để kiểm thử sự phụ thuộc |
| **JDK 26 (tài liệu migration 2026)** | Oracle vẫn mô tả finalization là **deprecated for removal**; không được khẳng định nó đã bị loại bỏ hoàn toàn chỉ vì có JEP 421 |
| **Một release tương lai** | Dự kiến cơ chế sẽ bị vô hiệu hóa theo mặc định rồi được gỡ bỏ theo quá trình chuyển đổi của nền tảng; thời điểm cụ thể phải kiểm chứng theo release notes tương ứng |

**Kết luận quan trọng:** “`Object.finalize()` deprecated” không có nghĩa compiler Java 17 không nhận diện method nữa. Ngược lại, compiler chấp nhận mã tương thích nhưng **cảnh báo**, và API được đánh dấu không nên dùng. Mốc **deprecated for removal ở JDK 18** là tín hiệu tương thích mạnh hơn, không phải một lời bảo đảm rằng code sẽ hỏng đúng ở bản JDK kế tiếp.

### 2.1. Thử vô hiệu hóa finalization ở JDK 18+

JEP 421 đưa ra công tắc kiểm thử khi runtime hỗ trợ:

```bash
# JDK 18+ có hỗ trợ tùy chọn theo JEP 421:
java --finalization=disabled -cp out MyApplication
```

Nó giúp phát hiện code hoặc dependency đang **phụ thuộc ngầm vào finalization**. Không sử dụng lệnh trên làm một ví dụ bắt buộc chạy được bằng Java 17: **JDK 17 không có `--finalization=disabled` được giới thiệu ở JDK 18**.

Lưu ý:

- Không được hiểu disabled finalization là disabled GC.
- Việc tắt finalization không tự động gọi `close()` thay bạn.
- Nếu ứng dụng có tài nguyên chỉ được đóng bằng `finalize()`, việc vô hiệu hóa có thể làm lộ resource leak hoặc thay đổi hành vi; đó là vấn đề cần sửa trong code/lifecycle.
- Không nên dùng tùy chọn JVM để né việc di trú khỏi finalizers trong codebase.
- Tài liệu JVM mới có thể đổi cờ/trạng thái qua các phiên bản; khi triển khai luôn kiểm tra `java -version` và release notes đúng vendor.

### 2.2. Vì sao “bị gỡ” khác “bị deprecated”?

**Deprecated:** lời gọi API vẫn có thể tồn tại và hoạt động, nhưng có cảnh báo/tài liệu khuyến cáo tránh dùng.

**Deprecated for removal:** API/cơ chế có kế hoạch bị gỡ; mã phụ thuộc nên được di trú sớm và kiểm thử tương thích.

**Disabled:** runtime có thể giữ code/API tương thích nhưng **không thực hiện công việc finalization**; logic dựa vào side effect của `finalize()` sẽ thất bại.

**Removed:** API/cơ chế không còn được cung cấp trong phạm vi phát hành cụ thể; source, binary compatibility hoặc runtime behavior có thể phải thay đổi.

Đừng suy trạng thái chỉ từ tên `finalize` trong stack trace hoặc mã thư viện cũ: phải xác định release và class thực sự được nạp.

## 3. `Object.finalize()` từng hoạt động ra sao?

Java 17 `Object.finalize()` có chữ ký:

```java
@Deprecated(since = "9")
protected void finalize() throws Throwable
```

Một class legacy có thể override:

```java
// ĐOẠN MÃ LỊCH SỬ ĐỂ ĐỌC/DI TRÚ, KHÔNG KHUYẾN NGHỊ VIẾT MỚI:
class LegacyResource {
    @Override
    protected void finalize() throws Throwable {
        try {
            // cleanupLegacyHandle();  // Giả định code cũ có native handle.
        } finally {
            super.finalize();
        }
    }
}
```

Đây là **snippet minh họa**, không phải file có `main`; cố ý không thực hiện tác vụ cleanup. Việc gọi `super.finalize()` trong một override `finalize()` không được Java tự chèn như cách constructor chain hoạt động.

### 3.1. Không có bảo đảm về thời điểm

Ngay cả khi finalization được bật:

- JVM không hứa gọi `finalize()` ngay khi một local variable ra khỏi scope.
- Có thể trì hoãn rất lâu hoặc **không chạy trước khi process kết thúc**.
- Không có thứ tự gọi finalizers giữa các objects khác nhau.
- Không được xây business rule giả định finalizer thực hiện trong thread hiện tại.
- Một exception không được xử lý ném từ `finalize()` bị bỏ qua theo quy tắc `Object.finalize()`; nó không tự chuyển về lời gọi business code để catch.

**Đừng dùng `System.gc()` hoặc `System.runFinalization()` như “đồng hồ” buộc finalizer chạy.** Các lời gọi đó không cung cấp SLA cho cleanup. `System.runFinalization()` cũng nằm trong nhóm API liên quan cần tránh và đã được deprecated for removal trong JDK 18.

### 3.2. Finalizer có thể làm object sống lại (resurrection)

Một finalizer legacy có thể đặt `this` vào static field/list rồi khiến object trở lại strongly reachable:

```java
// CHỈ LÀ MÔ HÌNH LỖI LỊCH SỬ — KHÔNG CHẠY VỚI GC TIMING CỐ ĐỊNH:
static Object resurrected;

@Override
protected void finalize() throws Throwable {
    resurrected = this; // Làm đối tượng có strong path trở lại.
}
```

Ở đây object từng đủ điều kiện finalization nhưng sau đó **resurrect**. Một finalizer **không được tự động gọi lại nhiều lần** cho cùng object đã finalizable theo hợp đồng. Vì vậy việc “dọn dẹp object lần nữa khi nó chết lần hai” không thể xem là đáng tin.

Không nên thử resurrection trong code production: nó làm phức tạp lifecycle, khó dự đoán thời điểm release native resources và là lý do cơ chế finalization bị phản đối.

### 3.3. Finalizer thread, locks và concurrency

Finalizer không chạy trên Java caller thread theo một thời điểm do bạn chọn. Nếu một finalizer cố chờ lock/thread trong khi một thread khác đang giữ lock và đợi GC-related work hoặc resource, ứng dụng có thể gặp deadlock hoặc hang. Finalization cũng gây áp lực lên GC/reference processing và làm objects cần sống lâu hơn để đi qua chu trình finalization.

Một finalizer làm I/O blocking kéo dài có thể khiến việc xử lý các finalizers khác bị trễ. Đây là vấn đề của thiết kế: **lifecycle không nên phụ thuộc vào một thread dọn dẹp bất định** khi tài nguyên cần đóng ngay sau khi xong việc.

## 4. Vì sao finalization không phù hợp cho quản lý tài nguyên?

### 4.1. Java heap không phản ánh số file descriptors

Ví dụ chương trình mở hàng nghìn file streams rồi bỏ reference:

```java
// Ví dụ PHẢN MẪU, không nên dùng:
for (int i = 0; i < 100_000; i++) {
    java.io.FileInputStream stream =
            new java.io.FileInputStream("data.txt");
    stream.read();
    // Không close(), chỉ bỏ reference.
}
```

Đây là **snippet không độc lập**, giả sử file tồn tại. Có thể hết OS file descriptors **trước khi** GC quyết định thu hồi các wrappers; ngoài ra exception ở `read()` càng khiến code bỏ qua đường cleanup.

Giải pháp đúng là **close tài nguyên tại thời điểm biết đã dùng xong**, không đợi heap thiếu chỗ. Chính Javadoc Java 17 của `FileInputStream` khuyến cáo đóng trực tiếp hoặc dùng try-with-resources.

### 4.2. Native pointer và use-after-free

Một Java wrapper có thể giữ `long handle` trỏ tới native resource. Nếu finalizer cleanup chạy sau khi optimizer thấy wrapper không còn live, trong khi mã native vẫn xử lý handle, có thể xảy ra **use-after-free**.

`Reference.reachabilityFence(wrapper)` có thể giữ wrapper strongly reachable tới một điểm xác định trong Java code, nhưng đây là công cụ bổ sung cho lifetime correctness, **không thay thế việc quản lý ownership và close có kiểm soát**.

### 4.3. Security và khả năng object nửa khởi tạo bị lợi dụng

Một số class có constructor ném exception trước khi khởi tạo hoàn chỉnh vẫn có thể gặp rủi ro liên quan finalization trong thiết kế legacy. Những cơ chế có khả năng làm đối tượng sống lại hoặc thực thi code lúc trạng thái chưa hoàn tất có thể mở lỗ hổng và làm khó lập luận bảo mật. Đây là một lý do được nêu trong JEP 421 khi deprecate finalization.

**Không dùng finalizer để thực hiện authorization, flush bắt buộc, commit transaction hoặc lưu trạng thái quan trọng**: bạn không có bảo đảm code ấy sẽ chạy đúng thời điểm, đúng thứ tự hoặc chắc chắn hoàn thành.

## 5. Lựa chọn số một: `AutoCloseable` + `try-with-resources`

Một resource có thể được đóng tường minh qua `close()`. Interface `AutoCloseable` định nghĩa:

```java
public interface AutoCloseable {
    void close() throws Exception;
}
```

Nhiều Java APIs như `InputStream`, `OutputStream`, `Reader`, JDBC `Connection`, `Statement`, `ResultSet` và `java.nio` resources hỗ trợ cơ chế đóng theo scope (cần xem contract riêng từng API).

`try-with-resources` trong Java giúp compiler gọi `close()` khi control rời `try`, kể cả:

- Hoàn tất bình thường.
- `return` từ thân `try`.
- Exception trong thân `try`.
- Những luồng điều khiển hợp lệ khác rời statement theo ngữ nghĩa Java.

Đây là **cleanup deterministic theo luồng điều khiển**, không phụ thuộc GC nhận ra object unreachable.

### 5.1. Thứ tự đóng khi có nhiều tài nguyên

Resources được **khởi tạo từ trái qua phải** và **đóng theo thứ tự ngược lại** (LIFO-like).

Tạo file **`TryWithResourcesOrderDemo.java`**:

```java
public class TryWithResourcesOrderDemo {
    static final class Tracer implements AutoCloseable {
        private final String name;

        Tracer(String name) {
            this.name = name;
            System.out.println("open " + name);
        }

        @Override
        public void close() {
            System.out.println("close " + name);
        }
    }

    public static void main(String[] args) {
        try (Tracer first = new Tracer("A");
             Tracer second = new Tracer("B")) {
            System.out.println("body");
        }
        System.out.println("after");
    }
}
```

Biên dịch/chạy trên Java 17+:

```bash
javac --release 17 TryWithResourcesOrderDemo.java
java TryWithResourcesOrderDemo
```

**Stdout chính xác:**

```text
open A
open B
body
close B
close A
after
```

**Giải thích:** code tạo `A` rồi `B`, vào thân `try` và sau đó đóng `B` trước `A`. Đây là cơ chế `try-with-resources` theo ngôn ngữ Java, không phải JVM đợi GC trước khi gọi `close()`.

### 5.2. Nếu constructor resource thứ hai ném exception?

Nếu `new Tracer("B")` ném exception trong lúc resource acquisition, Java vẫn **đóng những resource trước đó đã khởi tạo thành công**, trong ví dụ là `A`. Như vậy try-with-resources bảo vệ cả trường hợp chưa vào phần body.

Đó là một lý do tạo resource ngay trong header `try` thay vì mở nhiều resources bằng `new` trước rồi dựa vào một `finally` viết tay.

### 5.3. Reusing effectively final resource (Java 9+)

Java 9 cho phép dùng biến tài nguyên `final` hoặc **effectively final** đã được khởi tạo trước:

```java
var stream = java.nio.file.Files.newInputStream(path);
try (stream) {
    // Đọc dữ liệu...
}
```

Đây là **snippet**, cần biến `path` kiểu `java.nio.file.Path` và xử lý `IOException`. Nó minh họa cú pháp `try (stream)` hỗ trợ Java 9+, trong khi các ví dụ độc lập của bài đều dùng Java 17.

**Lưu ý:** Resource phải thực hiện `AutoCloseable`, không thể chỉ có một method tên `close` không liên quan interface rồi mong compiler tự xử lý.

## 6. `try-with-resources` và suppressed exceptions

Có một tình huống quan trọng: **thân try ném exception, rồi `close()` cũng ném exception**. Java không đơn giản thay lỗi gốc bằng lỗi đóng (trong trường hợp thông thường):

- Exception từ phần thân try là **primary exception**.
- Exception từ `close()` được **suppressed** vào primary bằng cơ chế `Throwable.addSuppressed`.
- Caller catch primary và có thể xem các lỗi phụ qua `Throwable.getSuppressed()`.

### 6.1. Ví dụ Java 17 đầy đủ và stdout xác định

Tạo file **`SuppressedCloseDemo.java`**:

```java
public class SuppressedCloseDemo {
    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() throws Exception {
            throw new Exception("close failed");
        }
    }

    public static void main(String[] args) {
        try (FailingResource ignored = new FailingResource()) {
            throw new IllegalStateException("body failed");
        } catch (Exception ex) {
            System.out.println("primary=" + ex.getMessage());
            System.out.println("suppressedCount=" +
                    ex.getSuppressed().length);
            System.out.println("suppressedMessage=" +
                    ex.getSuppressed()[0].getMessage());
        }
    }
}
```

Chạy:

```bash
javac --release 17 SuppressedCloseDemo.java
java SuppressedCloseDemo
```

Stdout:

```text
primary=body failed
suppressedCount=1
suppressedMessage=close failed
```

`IllegalStateException` từ body vẫn là lỗi gốc. Exception từ `close` được giữ dưới dạng suppressed, không bị nuốt mất. Trong code production, đừng giả định `getSuppressed()[0]` luôn tồn tại; chương trình demo biết chắc resource được thiết kế để ném từ `close`.

### 6.2. `getCause()` không phải `getSuppressed()`

- **Cause chain:** thường chỉ nguyên nhân tạo ra một exception wrapper (ví dụ `new IOException("failed", cause)`).
- **Suppressed exceptions:** các lỗi phát sinh trong cleanup, được giữ thêm bên cạnh primary exception.

Khi chẩn đoán thất bại trong nhiều resources, đọc **exception chính**, **cause** và **suppressed exceptions**. Một log chỉ in `ex.getMessage()` có thể bỏ qua lỗi close quan trọng.

### 6.3. Trái ngược với `finally` viết tay

Một `finally` tự viết:

```java
try {
    throw new IllegalStateException("body failed");
} finally {
    throw new IllegalStateException("cleanup failed");
}
```

Đây là **snippet phản mẫu**; lỗi `cleanup failed` thay thế normal/abrupt completion trước đó và có thể che lấp lỗi `body failed` nếu không giữ lại thủ công. Try-with-resources được thiết kế để tránh mất primary exception trong tình huống đóng resource.

## 7. Lựa chọn số hai: `Cleaner` khi thật sự cần cleanup dự phòng

`java.lang.ref.Cleaner` xuất hiện từ Java 9, quản lý **phantom references và reference queues** để liên kết một object với **`Runnable` cleaning action**. Khi object đăng ký trở thành phantom-reachable theo quy tắc, Cleaner có thể thực hiện action trên thread phù hợp. API cũng cung cấp `Cleanable.clean()` để gọi action **một cách chủ động**.

Cleaner **tốt hơn finalization** về khả năng tách cleanup state khỏi referent và tránh trực tiếp gọi method của một object đang chết, nhưng **automatic Cleaner cleanup vẫn không có deadline đáng tin**.

### 7.1. API quan trọng

```java
Cleaner cleaner = Cleaner.create();
Cleaner.Cleanable cleanable = cleaner.register(owner, cleanupAction);
cleanable.clean();
```

- `owner` là object được theo dõi về phantom reachability.
- `cleanupAction` là `Runnable` thực hiện giải phóng state riêng.
- `cleanable.clean()` yêu cầu thực hiện cleanup **một lần nếu chưa thực hiện trước đó**.
- Action được Cleaner gọi **tối đa một lần** đối với registration tương ứng; nếu không explicit clean và object không trở thành phantom-reachable trong thời gian JVM chạy, action có thể **không chạy**.
- Khi automatic processing, action có thể chạy trên thread riêng do Cleaner quản lý và có thể đồng thời với code còn chạy; hành vi khi `System.exit` không có bảo đảm mọi action sẽ được chạy.
- Exception từ cleaning action được Cleaner bỏ qua theo API; logging/monitoring cần được thiết kế nếu cleanup có thể thất bại.

### 7.2. Sơ đồ không giữ owner sống sai cách

```text
ĐÚNG:
   Cleaner/registration ──strong──► State (handle/resource data)
            │
            └──phantom──► Owner
   State không có strong path trở lại Owner.

SAI:
   Cleaner/registration ──strong──► lambda cleanup
                                      │
                                      └──strong capture this──► Owner
   Owner vẫn strongly reachable → automatic Cleaner không thể kích hoạt.
```

**Điểm then chốt:** Không cho `cleanupAction` giữ strong reference tới `owner` — kể cả gián tiếp qua inner class, lambda `() -> this.close()`, hay field của `State` trỏ về owner. Nếu có, owner không thành phantom-reachable và cleanup sẽ không chạy tự động.

Dùng **`static` nested class chứa state cần đóng** là mô hình an toàn dễ hiểu.

## 8. Chương trình Cleaner + try-with-resources Java 17 hoàn chỉnh

Tạo file **`CleanerResourceDemo.java`**:

```java
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

public class CleanerResourceDemo {
    private static final Cleaner CLEANER = Cleaner.create();

    private static final class State implements Runnable {
        private final AtomicBoolean released = new AtomicBoolean();

        @Override
        public void run() {
            if (released.compareAndSet(false, true)) {
                System.out.println("release");
            }
        }
    }

    static final class Resource implements AutoCloseable {
        private final State state = new State();
        private final Cleaner.Cleanable cleanable =
                CLEANER.register(this, state);

        void use() {
            if (state.released.get()) {
                throw new IllegalStateException("closed");
            }
            System.out.println("using");
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            resource.use();
        }
        System.out.println("after");
    }
}
```

Lệnh Java 17:

```bash
javac --release 17 CleanerResourceDemo.java
java CleanerResourceDemo
```

**Stdout xác định:**

```text
using
release
after
```

Giải thích:

1. `CLEANER` sống đủ lâu nhờ static reference.
2. Mỗi `Resource` tạo một `State` **không giữ reference ngược tới Resource**.
3. Cleaner đăng ký theo dõi `Resource` cùng `state`.
4. `try-with-resources` đảm bảo `close()` chạy khi rời body.
5. `close()` gọi `cleanable.clean()`, action chạy tại thời điểm rõ ràng khi method này được gọi.
6. `AtomicBoolean` mô tả một guard cho tình huống cleanup lặp/đồng thời; action không in/release hai lần. Dù `clean()` đã là at-most-once theo Cleanable, guard vẫn giúp minh họa việc state dọn dẹp nên idempotent.
7. Không cần `System.gc()` hoặc finalizer để ra stdout mong đợi.

**Đây là demo quản lý một resource giả lập**. `State.run()` chỉ in thông báo, không thực sự giải phóng native memory hoặc file descriptor. Ứng dụng thật cần logic `free(handle)`/`close(fd)`/release connection phù hợp, xử lý errors, thread-safety và đảm bảo code không dùng resource sau close.

### 8.1. Vì sao không khai báo non-static inner `State`?

`class State implements Runnable` nằm **bên trong `Resource` nhưng không có `static`** có thể có implicit strong reference tới outer `Resource`. Cleaner giữ `State` → `State` giữ owner → owner không phantom-reachable.

Trong ví dụ, `State` là **static nested class** thuộc `CleanerResourceDemo`, nên không có implicit outer instance reference. Nó cũng không giữ explicit field nào có loại `Resource`.

### 8.2. Vì sao cleaner không thay thế `close()`?

Cleaner chỉ có thể chạy tự động khi owner đủ điều kiện xử lý phantom reachability và runtime xử lý notification. GC thường kích hoạt bởi **heap memory pressure**, không bởi việc hệ điều hành sắp hết file handles. Một resource có thể ngừng được ứng dụng sử dụng nhưng vẫn giữ file descriptor lâu nếu chờ GC.

Vì thế thiết kế đúng thường là:

```text
Application lifecycle:
  acquire resource
       │
       ▼
  use resource trong try-with-resources
       │
       ▼
  close() → Cleanable.clean() → release đúng lúc

Fallback:
  application quên close()
       │
       ▼
  owner có thể phantom-reachable về sau
       │
       ▼
  Cleaner có thể chạy action ở thời điểm không bảo đảm
```

Fallback có thể giúp hạn chế một số leak, nhưng **không được là đường duy nhất cho tài nguyên quan trọng**.

## 9. Finalization và Cleaner: đừng so sánh nhầm trách nhiệm

| Thuộc tính | Legacy `finalize()` | `Cleaner` | `AutoCloseable` / try-with-resources |
| --- | --- | --- | --- |
| API / tình trạng | Deprecated; finalization deprecated for removal từ JDK 18 | Java 9+, API đang được khuyến nghị cho cleanup dự phòng | Chuẩn cho explicit lifecycle |
| Trigger thông thường | GC/finalization processing sau reachability conditions | Phantom reachability & reference processing | Scope exit / explicit `close()` |
| Thời điểm xác định? | **Không** | **Không** cho automatic cleanup | **Có**, theo luồng điều khiển Java |
| Có thể đảm bảo chạy trước JVM exit? | **Không** | **Không** (automatic) | Có khi control thực sự đi qua `close`/`try`, không phải nếu process bị kill |
| Có method trên object đang finalizing? | `finalize()` được gọi trên chính object | Action tách khỏi owner; **không** giữ owner | `close()` gọi khi object còn được code quản lý |
| Object resurrection? | Có thể trong finalizer legacy | Không được truy cập lại referent qua phantom `get()` | Không có finalizer resurrection pattern |
| Exception semantics | Uncaught finalizer exception bị bỏ qua | Exceptions từ cleaning action bị bỏ qua theo API | `close()` có thể throw và caller xử lý; suppressed exceptions |
| Có thể gọi cleanup chủ động? | Không dùng `finalize()` như close API | `Cleanable.clean()` | `close()` hoặc try-with-resources |
| Khi nào phù hợp? | **Không viết mới; chỉ xử lý/migrate legacy** | Fallback cho native/off-heap state khi không luôn kiểm soát được caller | File/socket/JDBC/native handle có rõ ownership và scope |

**Thực hành:** Nếu thư viện đã hỗ trợ `Closeable`/`AutoCloseable`, hãy dùng nó. Đừng tự bọc thư viện trong finalizer. Cleaner chỉ cần thiết khi thiết kế **một wrapper có state ngoại heap đặc thù**, muốn một lớp bảo vệ bổ sung khi consumer quên close.

## 10. Rủi ro nâng cấp: thư viện legacy phụ thuộc finalization

Một số code cũ đã ghi cleanup trong `finalize()` mà không có public `close()`. Khi migrate:

1. **Tìm override `finalize()`** trong codebase và bytecode của dependency khi có thể, không chỉ tìm những chỗ gọi `System.gc()`.
2. Xác định loại resource được giữ: OS file descriptor, native memory, executor, socket, registry entries hoặc data structure Java thuần.
3. Làm rõ **ai sở hữu resource** và thời điểm kết thúc sử dụng.
4. Bổ sung **`AutoCloseable`/`close()`** và sửa callers để dùng try-with-resources hoặc `finally`.
5. Nếu không kiểm soát được mọi client hoặc có native resource cần fallback, thiết kế Cleaner với **independent state** không giữ owner.
6. Kiểm tra idempotency và concurrency giữa explicit close, automatic cleanup và code đang sử dụng tài nguyên.
7. Loại bỏ finalizer legacy sau khi đảm bảo các đường cleanup khác bao phủ nhu cầu.
8. Kiểm thử trên JDK có hỗ trợ `--finalization=disabled` (JDK 18+) để phát hiện giả định còn sót.
9. Quan sát file handle/native memory metrics và integration tests trong môi trường thực, không chỉ số lượng objects còn trên heap.

### 10.1. Migration pattern trước – sau

**Trước (chỉ minh họa pattern legacy):**

```java
class LegacyHandle {
    private long nativeHandle;

    @Override
    protected void finalize() throws Throwable {
        try {
            releaseNative(nativeHandle);
        } finally {
            super.finalize();
        }
    }

    private static native void releaseNative(long handle);
}
```

Đây không phải chương trình độc lập và cố ý minh họa thiết kế cần bỏ. Nó thiếu close API và dựa vào GC timing.

**Sau — khung thiết kế phù hợp:**

```java
final class HandleOwner implements AutoCloseable {
    private final Cleaner.Cleanable cleanable;

    HandleOwner(long handle) {
        // State là static nested class, giữ handle độc lập,
        // dùng guard atomic để release at most once.
        State state = new State(handle);
        this.cleanable = CLEANER.register(this, state);
    }

    @Override
    public void close() {
        cleanable.clean();
    }
}
```

Đây là **pseudocode minh họa**, không phải Java file standalone: cần định nghĩa `CLEANER`, `State`, quyền sở hữu `handle` và native release logic. Chương trình `CleanerResourceDemo.java` ở mục 8 là phiên bản đầy đủ biên dịch/chạy để quan sát pattern và output.

### 10.2. Ownership phải rõ để tránh double-free

Đối với native handle, các phương thức như `releaseNative(handle)` có thể không an toàn nếu chạy hai lần. Thiết kế cần đồng bộ và chuyển trạng thái **OPEN → CLOSED** đúng một lần, không cho mượn raw handle sau close, và bảo đảm fail-safe trên đường exception.

Chỉ thêm một `boolean closed` bình thường không đủ khi nhiều threads có thể chạy đồng thời. Dùng AtomicBoolean/CAS hoặc synchronization phù hợp với API contract; nếu native resource được dùng song song, guard cho cleanup một lần cũng **chưa đủ** để chống use-after-close — còn cần thiết kế ownership/lifetime hoặc lock cho concurrent operations.

## 11. Trường hợp khó: resource phải sống đến sau thao tác native

GC/JIT có thể xem một wrapper Java không còn được dùng **sớm hơn cuối lexical scope**, nếu thread chỉ còn dùng primitive `long handle` được lấy từ wrapper. Cleaner có thể chạy trước khi một native operation sử dụng handle kết thúc nếu không bảo vệ lifetime.

Java cung cấp `Reference.reachabilityFence(Object)`:

```java
// Đoạn minh họa, giả định owner và nativeUse đã được khai báo:
long handle = owner.getHandle();
try {
    nativeUse(handle);
} finally {
    java.lang.ref.Reference.reachabilityFence(owner);
}
```

Phương thức fence giữ `owner` strongly reachable ít nhất đến vị trí fence. Nó **không gọi GC, không đóng resource**, cũng không thay thế việc khóa/đảm bảo đồng bộ khi `close()` có thể chạy từ thread khác.

Đặt fence trong `finally` giúp bảo đảm nó được gọi cả khi native operation ném Java exception. Hãy áp dụng khi có lifecycle cần thiết; không thêm một cách máy móc ở mọi nơi rồi coi đó là giải pháp toàn diện cho resource safety.

## 12. Tại sao không tự gọi `finalize()` để làm cleanup?

Một method tên `finalize()` về mặt Java vẫn có thể được gọi trực tiếp trong những tình huống access phù hợp, nhưng làm vậy **không biến nó thành API đóng tài nguyên có contract chuẩn**:

- Nó có visibility thường là `protected`, không dành cho consumer bên ngoài.
- Nếu JVM còn bật finalization, đường tự gọi không có nghĩa cơ chế GC sẽ không bao giờ gọi finalizer về sau.
- Code override có thể phụ thuộc vào các trạng thái chưa được chuẩn bị cho nhiều lần gọi.
- Một method dọn dẹp nên có tên/API rõ như `close()`, được gọi theo ownership và có trạng thái idempotent phù hợp.

**Không dùng** `object.finalize()`, `System.runFinalization()` hay `System.gc()` để xử lý một resource mà chính bạn biết đã dùng xong.

## 13. Cách kiểm tra mã legacy và cảnh báo compiler

### 13.1. Java 17 cảnh báo deprecated

Ví dụ đọc một class cũ có override `finalize()` (không khuyến khích chạy):

```java
class DeprecatedFinalizerDemo {
    @Override
    protected void finalize() throws Throwable {
        System.out.println("legacy cleanup");
        super.finalize();
    }
}
```

Nếu lưu thành `DeprecatedFinalizerDemo.java`, có thể biên dịch để **quan sát cảnh báo**:

```bash
javac --release 17 -Xlint:deprecation DeprecatedFinalizerDemo.java
```

Chương trình trên không có `main` và **không được dùng để đo số lần/timing finalizer**. Output cảnh báo của `javac` thay đổi theo compiler/version/locale, nên không đưa một stderr “chuẩn tuyệt đối”.

Trên JDK 18+, `finalize()`/finalization được đánh dấu deprecated for removal theo JEP 421, và compiler có thể hiển thị cảnh báo cụ thể hơn. Đừng dựa vào text warning cố định để quyết định application correctness.

### 13.2. Tìm override/bytecode

Có thể rà soát code sở hữu theo công cụ repository:

```bash
# Tìm các method declarations/overrides trong source (chỉ định hướng):
grep -R "finalize(" src

# Phân tích method trong một class đã compile:
javap -p YourClass
javap -c -p YourClass
```

Text search chỉ là bước đầu: một thư viện transitive có thể chứa finalizer mà source không nằm trong repository. Khi dùng `javap`, tên `finalize` trong binary không tự chứng minh nó thực sự quản lý tài nguyên hay finalization luôn đang được bật.

Một số APIs quản lý resource của JDK đã di trú khỏi finalization hoặc dùng cleanup khác; đọc Javadoc của **API/JDK release cụ thể** trước khi kết luận mọi `InputStream` “vẫn có finalizer”.

### 13.3. Kiểm thử khi disabled finalization

Một test có giá trị là: trên JDK 18+ có hỗ trợ `--finalization=disabled`, chạy integration suite mô phỏng tạo/dùng/đóng tài nguyên và khẳng định **không có file descriptor/native memory tăng vô hạn**. Test phải tập trung explicit cleanup và quota, **không** cần chờ GC để thành công.

Nếu test chỉ kiểm tra object bị thu hồi khi gọi `System.gc()` rồi không xem tài nguyên thực, có thể bỏ qua leak ở file descriptors, sockets hoặc native allocations.

## 14. Những nhầm lẫn thường gặp

1. **“Finalization đã bị xóa khỏi Java 17.”** Sai. `Object.finalize()` vẫn có trong Java 17 nhưng deprecated.
2. **“JEP 421 ra đời ở Java 9.”** Sai. Java 9 đánh dấu `Object.finalize()` deprecated; **JEP 421 thuộc JDK 18** và deprecate finalization for removal.
3. **“Deprecated for removal nghĩa là JDK kế tiếp chắc chắn xóa ngay.”** Sai. Không có ngày xóa cố định trong trạng thái này.
4. **“Finalization và garbage collection là cùng thao tác.”** Sai. GC có thể thu hồi heap; finalizer là callback legacy có chính sách xử lý riêng.
5. **“Một object vừa mất strong reference thì `finalize()` sẽ chạy ngay.”** Sai. Finalizer timing không được đảm bảo.
6. **“`System.gc()` là lệnh chạy đồng bộ hết finalizers.”** Sai. Không có SLA như vậy.
7. **“Finalizer có thể an toàn đảm nhiệm commit transaction.”** Sai. Không thể dựa vào thời điểm và bảo đảm hoàn thành.
8. **“Exception từ finalizer sẽ xuất hiện trong `catch` của caller.”** Sai. Uncaught finalizer exceptions thường bị bỏ qua theo contract.
9. **“Finalizer không thể giữ object sống lại.”** Sai. Legacy finalization có khả năng resurrection.
10. **“Nếu không có heap leak thì không thể có resource leak.”** Sai. File descriptors/native handles còn ngoài heap.
11. **“Mọi tài nguyên nên gắn `Cleaner` thay vì có `close()`.”** Sai. Explicit close là đường chính.
12. **“Cleaner action có thể gọi `owner.close()` thoải mái.”** Sai. Capture owner có thể khiến automatic cleanup không thể kích hoạt.
13. **“Cleaner chạy đúng khi scope kết thúc.”** Sai. Automatic cleaning phụ thuộc phantom reachability/GC; chỉ explicit clean có timing xác định.
14. **“Cleaner chắc chắn chạy trước `System.exit()`.”** Sai. Javadoc không đảm bảo.
15. **“Cleaning action được thực hiện bao nhiêu lần cũng được.”** Không nên. `Cleanable` có semantics at-most-once cho registration; state cleanup nên idempotent để bảo vệ resource.
16. **“try-with-resources chỉ gọi close khi không có exception.”** Sai. Nó đóng cả exceptional paths theo quy tắc.
17. **“Tài nguyên khai báo đầu tiên sẽ được đóng đầu tiên.”** Sai. Resources đóng theo thứ tự đảo.
18. **“Một exception từ close luôn xóa exception gốc của try.”** Sai. Try-with-resources dùng suppressed exceptions cho tình huống phù hợp.
19. **“`AutoCloseable` và `Closeable` là hai interface hoàn toàn không liên quan.”** Sai. `java.io.Closeable` mở rộng `AutoCloseable`, tinh chỉnh contract.
20. **“`reachabilityFence` chạy GC và giải phóng tài nguyên.”** Sai. Nó giữ strong reachability đến vị trí call.
21. **“Cứ dùng AtomicBoolean là hết race giữa use và close.”** Sai. Guard một lần release không tự giải quyết concurrent use-after-close.
22. **“Có Cleaner thì ứng dụng đã được miễn trách nhiệm xử lý suppressed exceptions.”** Sai. Exceptions trên explicit close, Cleaner action và business code có contract khác nhau.
23. **“Đọc tên `finalize()` trong bytecode là bằng chứng luôn có finalizer thread chạy.”** Sai. Runtime có thể tắt finalization theo JDK/flags; code có thể không thực thi.
24. **“JDK 17 có thể chạy lệnh `--finalization=disabled` như JDK 18.”** Sai. Cờ được giới thiệu ở JDK 18.

## 15. Tự kiểm tra và đáp án

**Câu 1.** Vì sao không nên đóng file/socket trong `finalize()`?

**Đáp án:** JVM không đảm bảo thời điểm hay việc finalizer sẽ chạy trước process exit; OS handles có thể cạn dù Java heap vẫn ổn.

**Câu 2.** `Object.finalize()` deprecated ở mốc nào, và JEP 421 làm gì?

**Đáp án:** Method được đánh dấu deprecated từ Java 9; JEP 421 ở JDK 18 đánh dấu finalization **deprecated for removal** và thêm phương án kiểm thử tắt cơ chế.

**Câu 3.** Ba resources `A`, `B`, `C` được khởi tạo thành công trong cùng `try` header. Đóng theo thứ tự nào?

**Đáp án:** **C → B → A**.

**Câu 4.** Thân `try` ném lỗi `E1`, `close()` ném `E2`. Với try-with-resources, lỗi nào là primary?

**Đáp án:** `E1` thường là primary; `E2` được suppressed vào `E1`, truy cập qua `getSuppressed()`.

**Câu 5.** Tại sao Cleaner action dạng `() -> this.release()` là bẫy?

**Đáp án:** Lambda capture `this`, Cleaner giữ action và qua đó giữ owner strongly reachable; owner có thể không bao giờ đạt phantom reachability để automatic action chạy.

**Câu 6.** `CleanerResourceDemo` in `release` do GC chạy đúng giờ hay do explicit close?

**Đáp án:** Do `try-with-resources` gọi `close()` và `close()` gọi `Cleanable.clean()`, nên stdout không cần GC timing.

**Câu 7.** `Cleaner` có bảo đảm mọi action sẽ chạy khi `System.exit()` không?

**Đáp án:** Không. Java 17 Javadoc nói behavior khi exit là implementation-specific; automatic cleanup không có deadline.

**Câu 8.** Một thư viện legacy cần test bỏ finalizer nhưng ứng dụng dùng JDK 17. Có dùng `--finalization=disabled` trực tiếp không?

**Đáp án:** Không. Flag đó được giới thiệu từ JDK 18. Chạy test trên một JDK có hỗ trợ flag hoặc di trú resource lifecycle sang explicit close.

**Câu 9.** `Reference.reachabilityFence(wrapper)` giúp xử lý tình huống nào?

**Đáp án:** Bảo đảm Java wrapper còn strongly reachable tới vị trí fence, hữu ích khi native operation vẫn cần resource gắn với wrapper dù optimizer không còn thấy use Java-level khác.

**Câu 10.** Vì sao atomic guard trong State không đủ để làm native resource thread-safe?

**Đáp án:** Nó giúp ngăn release hai lần, nhưng nếu thread khác vẫn đang sử dụng handle trong khi close, vẫn có nguy cơ use-after-close; cần ownership/synchronization policy.

**Câu 11.** Điều gì xảy ra khi code viết `finalize()` ném exception chưa bắt?

**Đáp án:** Theo `Object.finalize()` API contract, exception không được xử lý sẽ bị bỏ qua và việc finalization của object đó kết thúc; không trả về caller business code.

**Câu 12.** Khi nào nên dùng Cleaner thay vì chỉ có try-with-resources?

**Đáp án:** Khi thiết kế một wrapper quản lý state ngoài heap/native cần **cleanup dự phòng** nếu người dùng API quên close, đồng thời vẫn có explicit `close()` làm cơ chế chính. Nếu resource đã có close/lifecycle tốt, Cleaner thường không cần tự thêm.

## 16. Tổng kết bằng quyết định kiến trúc

```text
Bạn đang quản lý tài nguyên gì?
       │
       ├─ Object Java thuần, không có resource phải đóng?
       │       └─ Giữ references đúng nhu cầu, để GC quản lý heap.
       │
       └─ File/socket/JDBC/native handle/OS resource cần đóng?
               │
               ▼
       Có thể xác định owner và thời điểm kết thúc dùng?
               │
           Có  ├──► Implement AutoCloseable
               │     + try-with-resources / explicit close
               │     + test exceptional paths / suppressed exceptions
               │
           Không/khó kiểm soát mọi client?
               └──► Vẫn cung cấp explicit close
                     + cân nhắc Cleaner fallback
                     + State độc lập không capture owner
                     + đồng bộ và idempotent release
                     + chấp nhận automatic cleanup không có deadline

Legacy finalize():
    CHỈ để nhận diện, đánh giá và migrate;
    không chọn làm giải pháp thiết kế mới.
```

**Chốt kiến thức:** Garbage collection thu hồi **Java heap memory**, nhưng không cam kết tài nguyên hệ điều hành được đóng kịp thời. `Object.finalize()` là cơ chế đã deprecated và finalization bị **deprecated for removal từ JDK 18**, vì hành vi bất định, resurrection, security/reliability risks và chi phí. Với Java hiện đại, **`try-with-resources`/`AutoCloseable` là lựa chọn chính** cho lifecycle rõ ràng; **`Cleaner` chỉ bổ sung cleanup dự phòng** qua phantom reachability, với action không giữ owner và không có bảo đảm thời điểm. Thiết kế đúng phải chú ý ownership, exception preservation, race giữa use/close và kiểm thử khi finalization bị tắt.

### Tài liệu tham khảo chính thức

- [Java SE 17 — `Object.finalize()` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#finalize()): deprecated, timing/ordering/exception limitations và đề xuất alternatives.
- [JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421): mốc JDK 18, lý do và `--finalization=disabled`.
- [Oracle JDK 26 Migration Guide — Finalization Deprecated for Removal](https://docs.oracle.com/en/java/javase/26/migrate/migrating-from-jdk-8-later-jdk-releases.html): trạng thái migration được tài liệu 2026 mô tả.
- [Java SE 17 — `Cleaner`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html): phantom reachability, no-reference-to-owner và at-most-once actions.
- [Java SE 17 — `AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html).
- [JLS Java SE 17 — `try-with-resources` (§14.20.3)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): close order và suppressed exceptions.
- [Java SE 17 — `Throwable.getSuppressed()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html#getSuppressed()).
- [Java SE 17 — `Reference.reachabilityFence`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html#reachabilityFence(java.lang.Object)).
- [Java SE 17 — `FileInputStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/FileInputStream.html): resource release guidance.
- [Oracle JDK 18 Release Notes — JEP 421](https://www.oracle.com/java/technologies/javase/18-relnote-issues.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="object-reachability-009">System.gc là yêu cầu gợi ý và những giới hạn khi dùng nó để kiểm chứng</a>

<details>
<summary>Xem chi tiết</summary>

# `System.gc()` là yêu cầu gợi ý và những giới hạn khi dùng nó để kiểm chứng

Trong Java, `System.gc()` **không phải lệnh đảm bảo thu hồi object ngay lập tức**. Đây là lời yêu cầu/gợi ý JVM dành công sức cho garbage collection. Việc JVM thực sự chạy collector lúc nào, chạy loại collection nào, có xử lý object cụ thể chưa, đã giải phóng bao nhiêu memory, và có trả pages về hệ điều hành không đều **không được bảo đảm bởi một lời gọi `System.gc()`**.

Bài học thuộc chủ đề **khả năng truy cập (reachability)**: muốn hiểu một object có thể bị GC hay không, trước hết phải xét đường tham chiếu từ **GC roots**. Việc gọi explicit GC request là một sự kiện khác hẳn với việc object mất strong references. Không được dùng `System.gc()` để thay thế phân tích root paths, xác minh tài nguyên đã `close()`, hay tạo assertion phụ thuộc GC timing.

## 1. Chính xác `System.gc()` có nghĩa gì?

```java
System.gc();
```

Đây là static method `void` của `java.lang.System`, không nhận đối số. Theo Java SE API, lời gọi **gợi ý JVM nỗ lực tái chế những object không còn được sử dụng để bộ nhớ chúng chiếm có thể sớm được tái dùng**. Cách JVM phản ứng là một quyết định của runtime.

Về bản chất API, nó tương đương yêu cầu:

```java
Runtime.getRuntime().gc();
```

Hai biểu thức không tạo ra một cơ chế “mạnh” và một cơ chế “yếu” riêng biệt. Chúng đều là **explicit GC request**.

| Phát biểu | Đúng/sai | Vì sao? |
| --- | --- | --- |
| `System.gc()` đề nghị VM xem xét thu gom rác | Đúng | Đây là ý định của API |
| Luôn có một Full GC đồng bộ | Sai | Collector/configuration có thể phản ứng khác hoặc bỏ qua |
| Sau lời gọi, mọi object eligible đều đã bị reclaim | Sai | Không có bảo đảm timing/phạm vi |
| Object còn strongly reachable có thể bị hủy vì request này | Sai | JVM phải giữ ngữ nghĩa chương trình |
| `WeakReference.get()` chắc chắn là `null` sau lời gọi | Sai | Còn strong paths, liveness và GC policy |
| JVM nhất định giải phóng RAM vật lý về OS | Sai | Heap committed và RSS có thể giữ nguyên |
| Lời gọi trả về boolean báo GC thành công | Sai | Return type `void` |
| Có thể dùng logs/MXBeans để quan sát VM đã phản ứng thế nào | Đúng | Nhưng kết quả chỉ chứng minh phạm vi điều đã quan sát |

**Quy tắc học tập:** `System.gc()` là *yêu cầu*, không phải *bằng chứng GC đã chạy*, càng không phải *bằng chứng object X đã chết*.

## 2. Reachability: một object là rác khi nào?

Các tracing garbage collectors xét **đồ thị object references** từ một tập **GC roots**. Những roots điển hình gồm references còn live trong execution frames, static/runtime class state phù hợp, JNI handles và các cấu trúc runtime có chức năng giữ sống object.

```text
  GC root 1 ──► A ──► C ──► D
  GC root 2 ──► B ──► C

  Không có root ──► E ◄─► F
```

Giả sử mọi mũi tên trên là **strong references**:

- A, B, C, D có thể lần tới từ root, vì thế thuộc tập strongly reachable.
- E và F trỏ lẫn nhau nhưng không có đường từ root đến chúng. Chu trình không tự giữ object sống: một tracing collector có thể reclaim chu trình không reachable.
- Nếu bỏ root 1 thì C/D vẫn reachable qua B. Mất **một** đường chưa đủ nếu còn đường khác.
- Một local variable được gán `null` không chứng minh không còn đường giữ sống qua field, static cache, ThreadLocal, listener hay JNI.

### Ba câu hỏi khác nhau

1. **Strongly reachable?** Có đường strong reference sống từ một root tới object trong ngữ cảnh đang xét?
2. **Eligible for reclamation?** Sau khi xét special-reference processing và các điều kiện VM, object có thể được thu hồi?
3. **Đã được thu hồi?** Collector đã thực hiện công việc reclaim/reuse tương ứng?

Object có thể eligible nhưng chưa được GC xử lý. Thậm chí khi đã reclaim, vùng heap vẫn có thể giữ **committed memory** để dùng cho allocation sau, nên process RSS không nhất thiết giảm.

### `null` không phải lệnh thu hồi

```java
Object a = new Object();
Object b = a;
a = null;
System.gc();
System.out.println(b != null); // true
```

Đây là đoạn đặt trong một method. Vì `b` còn được sử dụng và trỏ tới object, một collector đúng không thể làm object biến mất trước dòng `println`. Không có cách dùng `System.gc()` để “ép xóa” object còn cần cho ngữ nghĩa Java.

Ngay cả khi nguồn không còn `b`, JIT/VM có thể xác định **reference liveness** khác phạm vi lexical của biến; việc còn hoặc mất strong path phải suy từ toàn bộ runtime graph, không chỉ từ số dấu ngoặc của source.

## 3. JVM có thể không thực hiện explicit GC như bạn tưởng

Một số người gọi `System.gc()` rồi tìm một dòng “Full GC” trong logs. Nhưng phản ứng phụ thuộc:

- **JVM implementation** và release cụ thể: HotSpot, OpenJ9, những VM khác không phải luôn dùng cùng policy.
- **Garbage collector**: Serial, Parallel, G1, ZGC, Shenandoah hoặc collector được hỗ trợ khác.
- **Flags**: explicit GC bị vô hiệu hóa, được hướng tới concurrent cycle, hay xử lý theo mặc định.
- **Tải runtime**: allocation rate, heap occupancy, marking state và các pha concurrent đang diễn ra.
- **Quyết định hệ thống**: việc thu hồi những vùng bộ nhớ phù hợp, lịch của các GC threads và thời điểm kiểm tra metrics.

### Một số flags HotSpot đáng biết

| Cấu hình | Ý nghĩa |
| --- | --- |
| `-XX:+DisableExplicitGC` | Bỏ qua một số explicit GC requests như `System.gc()`; automatic GC vẫn hoạt động |
| `-XX:+ExplicitGCInvokesConcurrent` | Trên collector/configuration hỗ trợ, explicit request có thể dùng cơ chế concurrent |
| `-Xlog:gc*:info` | Ghi GC logs bằng unified logging trên HotSpot JDK 17+ |
| `-XX:+PrintFlagsFinal` | Quan sát flags và giá trị thực tế của JVM được chạy |

Không đưa các flags này vào production theo công thức duy nhất. Một JVM có thể không hỗ trợ flag HotSpot hoặc hỗ trợ với giới hạn khác. Luôn xác nhận `java --version` và chạy `java -XX:+PrintFlagsFinal -version` trước khi giải thích log hoặc sửa tuning.

### `DisableExplicitGC` không có nghĩa “tắt garbage collector”

Nếu HotSpot bỏ qua lời gọi `System.gc()`, nó **vẫn cần GC tự động** khi application tạo allocation pressure. Flag này không ngăn collector thu hồi object theo các chu kỳ thông thường. Nó chỉ ảnh hưởng đường explicit request phù hợp, không làm garbage collection vô hiệu hoàn toàn.

### “Full GC” không phải kết quả portable

Trên một số HotSpot collector/config, explicit GC có thể gây stop-the-world Full GC. Nhưng **không được đưa quy luật đó thành Java SE guarantee**. Một cấu hình khác có thể khởi động concurrent work hoặc không làm gì. Việc lựa chọn collector cần được đọc cùng log, không chỉ từ một câu gọi Java.

## 4. GC roots quyết định object còn sống, không phải `System.gc()`

Ví dụ:

```java
class Cache {
    static final java.util.List<Object> ITEMS =
            new java.util.ArrayList<>();

    static void add() {
        ITEMS.add(new byte[1024]);
    }
}
```

Đây là class minh họa có thể biên dịch. Nếu runtime class/static state còn live, mỗi phần tử trong `ITEMS` có strong path phù hợp:

```text
GC root / runtime class state
  → Cache.ITEMS
  → ArrayList.elementData
  → byte[] element
```

Gọi `System.gc()` nhiều lần cũng **không được** reclaim arrays khi đường giữ sống còn hợp lệ. Đây thường là lỗi quản lý lifecycle/cache, không phải “GC quá yếu”.

Các ví dụ khác về giữ sống ngoài ý muốn:

- Một `ThreadLocal` chưa được `remove()` trên worker thread pool tồn tại lâu.
- Listener được đăng ký mà không unregister.
- Callback/lambda capture object trong scheduled task.
- Queue giữ payloads quá lâu.
- JNI global reference chưa release.
- Class loader hoặc class metadata giữ cả đồ thị object/plugin.
- Static map không có eviction/TTL/size bounds.

Cách sửa là **cắt bớt strong references không còn cần thiết**, đặt giới hạn và quản lý lifecycle. `System.gc()` không tự biết object đã hết ý nghĩa nghiệp vụ.

## 5. Strong, soft, weak và phantom references khác nhau

Một phép thử dùng `WeakReference` có ý nghĩa khác với việc theo dõi ordinary strong references.

| Loại | Tác dụng về reachability | Hạn chế khi dùng để kiểm chứng |
| --- | --- | --- |
| Strong reference | Giữ object theo đường tham chiếu mạnh còn live | Source scope không đồng nhất hoàn toàn với VM liveness |
| `SoftReference<T>` | GC có thể clear referent theo chính sách, liên quan memory demand | Không cam kết clear sau một lần explicit GC |
| `WeakReference<T>` | Không giữ referent sống khi chỉ còn weakly reachable | `get()` có thể vẫn khác `null` sau `System.gc()` |
| `PhantomReference<T>` | Theo dõi giai đoạn thích hợp qua queue, thường dùng làm nền cho cleanup | `get()` **luôn trả `null`**, kể cả trước GC |
| `ReferenceQueue<T>` | Nhận các reference objects được enqueue theo luật API | Queue event không được bảo đảm xuất hiện ngay sau explicit request |

### Có những điểm rất dễ bị nhầm

- Một **`WeakReference` object** có thể vẫn strongly reachable, trong khi **referent** của nó không còn được giữ mạnh.
- `weak.get()==null` chứng minh weak reference **không còn trả referent tại thời điểm đó**. Nó **không chứng minh** chính lời gọi `System.gc()` đã làm việc này: application có thể gọi `weak.clear()`, hoặc GC khác đã thực hiện.
- `weak.get()!=null` không chứng minh object đang leak. Có thể GC chưa chạy, hoặc còn strong/soft references theo luật runtime.
- `PhantomReference.get()` luôn `null`; không thể dùng giá trị này làm bài test đã reclaim.
- Việc clearing và enqueuing có những chi tiết xử lý riêng. `queue.poll()==null` không chứng minh referent vẫn sống.

Nếu cần chắc chắn file/socket/native handle được đóng, dùng `AutoCloseable` và `try-with-resources`; không xây logic resource cleanup dựa vào GC timing.

## 6. Ví dụ Java 17+ chạy được: strong path và weak reference

Chương trình sau minh họa **hai kết quả chắc chắn khi còn strong path** và **hai quan sát không được bảo đảm khi bỏ strong path**. Không có assertion dựa vào GC scheduling.

Tạo file `ExplicitGcDemo.java`:

```java
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class ExplicitGcDemo {
    private static volatile Object held;

    public static void main(String[] args) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        Object payload = new byte[1024];
        held = payload;

        WeakReference<Object> weak = new WeakReference<>(payload, queue);

        System.out.println("start");
        System.out.println("strongly held: " + (weak.get() == payload));

        System.gc(); // Gợi ý, không phải lệnh buộc reclaim.

        System.out.println("after hint while held: "
                + (weak.get() == payload));

        // Giữ tham chiếu payload sống tới vị trí này.
        Reference.reachabilityFence(payload);

        held = null;
        payload = null;
        // Không còn strong path được lập trình cố ý trong source.

        System.gc();

        boolean cleared = weak.get() == null;
        boolean enqueued = queue.poll() != null;

        System.out.println("weak cleared: " + cleared);
        System.out.println("queue observed: " + enqueued);
        System.out.println("end");
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac -encoding UTF-8 ExplicitGcDemo.java
java ExplicitGcDemo
```

**Những dòng luôn có mặt theo thứ tự, với giá trị ổn định:**

```text
start
strongly held: true
after hint while held: true
```

Sau ba dòng trên, chương trình in tiếp:

```text
weak cleared: <true-or-false>
queue observed: <true-or-false>
end
```

`<true-or-false>` **là placeholder giải thích**: mỗi vị trí được chương trình in thành literal `true` hoặc `false`, phụ thuộc collector, timing và reference processing. Đây không phải bộ stdout cố định duy nhất.

**Giải thích:**

1. `payload` và static `held` cùng giữ object trong giai đoạn đầu, nên GC phải bảo toàn referent; `weak.get()==payload` đúng.
2. Lời gọi `System.gc()` đầu tiên không được phá hủy object đang còn strongly reachable.
3. `Reference.reachabilityFence(payload)` biểu thị ranh giới bảo toàn strong reachability đến điểm đó.
4. Sau khi xóa hai strong paths cố ý, object **có thể** trở thành weakly reachable; nhưng Java không cam kết GC đã clear weak referent trước dòng kiểm tra.
5. `queue.poll()` chỉ quan sát việc enqueue tới thời điểm hỏi, không đồng nghĩa “GC cycle đã hoàn tất”.
6. `weak.get()` có thể tạm tạo strong reference khi trả referent khác `null`; bản thân phép đo có thể ảnh hưởng liveness ở khoảng thời gian rất ngắn.

**Không sửa chương trình thành một vòng lặp vô hạn gọi `System.gc()` cho tới khi `weak.get()==null`.** Thí nghiệm đó có thể không kết thúc khi explicit GC bị bỏ qua, còn root paths hoặc chỉ vì VM chưa xử lý referent.

## 7. Chạy cùng ví dụ với GC logs và flags

Trên HotSpot JDK 17+:

```bash
java -Xlog:gc*:info ExplicitGcDemo
java -XX:+DisableExplicitGC -Xlog:gc*:info ExplicitGcDemo
```

Ở lần đầu, JVM có thể log một GC event có nguyên nhân liên quan `System.gc()`, tùy collector và cấu hình. Ở lần thứ hai, HotSpot được yêu cầu bỏ qua explicit GC requests phù hợp; tuy nhiên GC **tự động** do allocation pressure hoặc các lý do khác vẫn có thể xuất hiện.

Một dạng log minh họa **không cố định**:

```text
[info][gc] ... Pause Full (System.gc()) ...
```

Dấu `...` trong ví dụ không phải offset/timing cố định. Một số collectors dùng concurrent work hoặc format/cause khác. **Không viết unit test assert rằng stdout/log luôn phải chứa đúng dòng này.**

### Kết quả logs chứng minh điều gì?

- Có event được ghi rõ nguyên nhân explicit GC: JVM trên môi trường ấy đã ghi nhận/làm công việc liên quan request.
- Không có event: có thể request bị bỏ qua, VM xử lý khác hoặc event không được bật dưới tag/level đang quan sát.
- Heap “before/after” thay đổi: có công việc GC/memory accounting được đo, **không xác định chắc chắn object duy nhất nào đã reclaim**.
- Log có “Pause Full”: phản ánh loại event được JVM ghi ở lần chạy đó, không chứng minh mọi collector luôn Full GC khi gặp `System.gc()`.

**So sánh giữa hai runs không hoàn toàn giống hệt nhau**: JIT warm-up, thread scheduling, heap sizes và tiến trình allocation đều có thể khác. Để đánh giá performance, cần lặp lại nhiều lần với workload, flags và môi trường kiểm soát.

## 8. GC logs và Java Management APIs: quan sát có giới hạn

Java cung cấp `GarbageCollectorMXBean` để đọc tên collector và các thống kê như số lần/thời gian thu gom, khi implementation hỗ trợ.

Tạo file `GcCounterDemo.java`:

```java
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

public class GcCounterDemo {
    private static void show(String label) {
        List<GarbageCollectorMXBean> beans =
                ManagementFactory.getGarbageCollectorMXBeans();

        System.out.println(label);
        for (GarbageCollectorMXBean bean : beans) {
            System.out.println(bean.getName() + ": count="
                    + bean.getCollectionCount()
                    + ", time(ms)=" + bean.getCollectionTime());
        }
    }

    public static void main(String[] args) {
        show("before");
        System.gc();
        show("after");
        System.out.println("request completed");
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 GcCounterDemo.java
java GcCounterDemo
```

**Dạng output:**

```text
before
<các dòng collector và số liệu thực tế>
after
<các dòng collector và số liệu thực tế>
request completed
```

Hai dòng `<các dòng collector và số liệu thực tế>` là **mẫu giải thích**, không phải kết quả in nguyên văn; collector names và counts phụ thuộc JVM. Có bean trả `-1` nếu một thông số không được hỗ trợ.

**Không có quy tắc “`System.gc()` được gọi một lần thì tổng collection count tăng đúng một”.** Các JVM/collectors có định nghĩa collection event, pha concurrent và metrics khác nhau. VM cũng có thể thực hiện GC vì lý do không liên quan giữa hai lần đo.

Nếu mục tiêu là xem các event chi tiết hơn, dùng GC unified logging hoặc Java Flight Recorder (JFR) theo policy của môi trường. Nếu mục tiêu là tìm **đường giữ sống** của object, dùng heap dump/profiler thay vì chỉ GC counters.

## 9. Tại sao đo bộ nhớ trước/sau `System.gc()` dễ sai?

Mẫu phổ biến:

```java
Runtime r = Runtime.getRuntime();
long before = r.totalMemory() - r.freeMemory();
System.gc();
long after = r.totalMemory() - r.freeMemory();
System.out.println("delta=" + (before - after));
```

Đây là **đoạn trong method**, không phải chương trình độc lập. `delta` ở đây **không phải kích thước chính xác của object muốn kiểm tra**.

Những nguồn sai lệch:

- GC có thể chưa chạy, chạy từ trước hoặc chạy vì một nguyên nhân khác.
- Các Java/JVM threads khác có thể allocate thêm objects giữa hai snapshots.
- Compiler/JIT có thể tối ưu bỏ những allocations không quan sát được, qua escape analysis hoặc scalar replacement.
- VM có thể commit hoặc uncommit heap pages theo policy khác thời điểm object reclamation.
- `Runtime.totalMemory()` và `freeMemory()` đo theo mô hình heap của VM, **không đồng nghĩa retained size của một object graph**.
- Collector có thể thu hồi nhiều objects không liên quan, làm delta không đại diện riêng cho object X.

### Các thước đo khác nhau

| Chỉ số | Điều đo |
| --- | --- |
| Heap used | Bộ nhớ heap đang được dùng theo metric JVM |
| Heap committed | Phần heap JVM đã committed |
| Heap maximum / reserved | Giới hạn hoặc không gian ảo VM cho phép sử dụng theo cấu hình |
| Process RSS | Các trang RAM resident OS gán cho process, gồm nhiều vùng ngoài Java heap |
| Shallow object size | Bộ nhớ của chính object, không đi theo các references |
| Retained size | Bộ nhớ có thể trở nên reclaimable khi mất object dominator phù hợp |

Object đã bị GC reclaim không có nghĩa process phải trả các pages ngay về OS. JVM thường giữ vùng heap đã committed để cấp phát lại nhanh. Do đó **RSS không giảm** không đủ để nói “GC không có tác dụng”.

Muốn đo shallow size của object, cân nhắc Java Object Layout (JOL) hoặc `Instrumentation.getObjectSize` (có giới hạn implementation). Muốn đo retained size, dùng heap profiler/dominator tree.

## 10. Vì sao `ReferenceQueue` cũng không tạo lịch GC?

`ReferenceQueue` nhận các reference objects khi chúng được VM enqueue theo các luật reference processing. Nó hữu ích khi ứng dụng muốn quan sát và xử lý một sự kiện, nhưng không bảo đảm sự kiện đã xảy ra ngay sau `System.gc()`.

```java
ReferenceQueue<Object> queue = new ReferenceQueue<>();
Object obj = new Object();
WeakReference<Object> weak = new WeakReference<>(obj, queue);
obj = null;
System.gc();

// Không có bảo đảm queue.poll() đã khác null tại đây.
boolean received = queue.poll() != null;
```

Đây là **đoạn code minh họa đặt trong method**, cần import `java.lang.ref.*` nếu muốn biên dịch. Ngoài GC timing, chính phép phân tích liveness và những reference còn ở nơi khác cũng ảnh hưởng việc object eligible.

### Đừng dùng `PhantomReference.get()` làm cờ “đã GC”

```java
ReferenceQueue<Object> q = new ReferenceQueue<>();
Object object = new Object();
PhantomReference<Object> p = new PhantomReference<>(object, q);
System.out.println(p.get() == null); // true, ngay cả trước GC
```

Đoạn này yêu cầu import `java.lang.ref.*` và thuộc một method. `PhantomReference.get()` **luôn trả `null`** theo API, vì vậy không phản ánh object đã thu hồi hay chưa. Khi cần cleanup theo API, dùng `ReferenceQueue` / Cleaner thích hợp với những giới hạn về timing, không tự suy luận từ `get()`.

## 11. `Reference.reachabilityFence` giải quyết một bài toán khác

JIT có thể xác định object không còn cần dùng ở thời điểm sớm hơn cuối method hoặc cuối lexical scope. Khi object quản lý một native handle, có tình huống native operation vẫn cần tài nguyên gắn với object nhưng VM không còn thấy object phải sống nếu thiếu một lời bảo đảm thích hợp.

Java 9+ hỗ trợ:

```java
Reference.reachabilityFence(object);
```

Phương thức này cho phép giữ mạnh object **ít nhất tới điểm gọi fence**, khi cần bảo đảm reachability cho đoạn code phía trước. Nó **không** ép GC chạy, không biến `System.gc()` thành đồng bộ và không bảo đảm object sẽ chết ngay sau fence.

Ví dụ minh họa (chưa chạy độc lập vì cần khai báo `NativeApi`):

```java
final class NativeResource {
    final long handle;

    NativeResource(long handle) {
        this.handle = handle;
    }

    void use() {
        NativeApi.perform(handle);
        java.lang.ref.Reference.reachabilityFence(this);
    }
}
```

Khi tài nguyên phải đóng một cách có thứ tự, nên ưu tiên `AutoCloseable`, `close()` và `try-with-resources`. Fence phục vụ một ràng buộc reachability; **không thay thế resource lifecycle**.

## 12. `System.gc()` không đảm bảo chạy finalizers/Cleaners

**Finalization** là cơ chế Java lịch sử, được deprecated for removal vì không đáng tin và có vấn đề về an toàn/hiệu năng. `System.gc()` không bảo đảm finalizers chạy đúng lúc; `System.runFinalization()` cũng không phải “nút đóng hết file handles”.

- Finalizer có thể không chạy trước khi process thoát.
- Finalizer có thể giữ/sống lại object trong những trường hợp legacy.
- Cleaner dựa vào GC/reference processing nên thời điểm chạy cũng **không xác định**.
- Resource file/socket/database connection cần close theo lifecycle của chương trình, không chờ heap pressure.
- Gọi GC thường xuyên có thể tạo pause mà vẫn không đóng tài nguyên đang bị giữ bởi reference mạnh.

Ví dụ dùng API chuẩn để đóng tài nguyên:

```java
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;

class ReadFileExample {
    static String firstLine(Path path) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.readLine();
        }
    }
}
```

Đây là **class minh họa có method**, chưa có `main`. `try-with-resources` gọi `close()` khi ra khỏi block theo ngữ nghĩa Java, bất kể `System.gc()` có chạy hay không.

## 13. Testing: đừng viết assertion phụ thuộc GC timing

Assertion sau **không đáng tin**:

```java
WeakReference<Object> weak = new WeakReference<>(new Object());
System.gc();
assert weak.get() == null;
```

Đoạn này đặt trong một method có import phù hợp. Nó có thể thất bại ngay cả khi application và JVM hoàn toàn đúng. Lý do: explicit request có thể bị bỏ qua hoặc collector chưa clear reference ở thời điểm assert. Chạy với `-XX:+DisableExplicitGC` làm sự thiếu chắc chắn càng rõ.

### Kiểm chứng hành vi ứng dụng có thể xác định

Nếu mục tiêu là đảm bảo một resource được close, viết test dựa trên **hành vi tường minh**, ví dụ:

```java
final class Resource implements AutoCloseable {
    private boolean closed;

    @Override
    public void close() {
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }
}
```

Trong method test:

```java
Resource resource = new Resource();
resource.close();
if (!resource.isClosed()) {
    throw new AssertionError("resource was not closed");
}
```

Không cần `System.gc()`; test chỉ phụ thuộc contract của `Resource.close()`.

### Nếu bắt buộc kiểm tra yếu tố GC ở integration tests

1. Chạy test trong **JVM process riêng** để không ảnh hưởng suite khác.
2. Ghi rõ JVM version/vendor/collector/flags.
3. Giữ references/referents đúng vòng đời, tránh test fixture giữ mạnh object.
4. Dùng timeout có giới hạn và chấp nhận kết quả **inconclusive về timing** nếu không thấy GC.
5. Không tạo vòng lặp vô hạn gọi `System.gc()`.
6. Không dùng kết quả không xảy ra GC trong timeout để kết luận chương trình leak.
7. Kết hợp `ReferenceQueue` với GC logs khi cần, ghi ranh giới chứng minh của từng phép đo.

**Đặc biệt:** trong vòng lặp `weak.get()!=null`, mỗi lần gọi `get()` trả referent khác null sẽ tạo một temporary strong reference. Điều đó có thể làm liveness khó dự đoán hơn; tránh dùng polling theo cách làm thay đổi đối tượng đang quan sát.

## 14. Các công cụ có giá trị hơn khi debug “GC không dọn object”

Một số công cụ trên HotSpot JDK 17:

```bash
java --version
java -XX:+PrintFlagsFinal -version
jcmd -l
jcmd PID GC.heap_info
jcmd PID GC.class_histogram
```

`PID` là placeholder: thay bằng PID thật từ `jcmd -l`. `jcmd PID help` giúp xem JVM cụ thể hỗ trợ lệnh nào. Heap histogram/dump có thể tốn thời gian và gây pause, nhất là trên production; luôn cân nhắc quyền truy cập, lưu lượng và dữ liệu nhạy cảm.

### Chọn công cụ theo câu hỏi

| Câu hỏi | Công cụ hữu ích | Giới hạn |
| --- | --- | --- |
| VM có ghi nhận explicit GC request? | `-Xlog:gc*`, JFR GC events | Không chứng minh object riêng đã reclaimed |
| Tần suất/thời gian GC thế nào? | `GarbageCollectorMXBean`, logs, JFR | Không cho retaining path của object |
| Object nào giữ object X sống? | Heap dump, **Paths to GC Roots** | Snapshot, cần hiểu reference strength |
| Có một static cache giữ data không? | Heap analyzer + dominator tree | Thấy retaining paths, vẫn phải xác minh logic nghiệp vụ |
| Mức heap used/committed/RSS ra sao? | MXBeans, OS monitoring, NMT | Các chỉ số không được đồng nhất |
| Một object chiếm bao nhiêu byte shallow? | JOL, Instrumentation | Không cho GC timing |
| Tài nguyên đã đóng chưa? | Explicit close, API state, test doubles | Không nên dùng GC làm oracle |

Với **memory leak** do một root path ngoài ý muốn, “chạy GC mạnh hơn” không phải cách sửa. Tìm root path và cắt reference không cần giữ mới là hướng đúng.

## 15. `System.gc()` và hiệu năng: tại sao không gọi sau mỗi request?

Collector thường có policy về heap occupancy, allocation rate, generations/regions, time budgets và phases đang diễn ra. Application không đủ bối cảnh nội bộ để chọn một collection point tối ưu sau mỗi request.

Lặp explicit GC quá thường xuyên có thể:

- Tăng pauses và latency p95/p99 khi VM chọn một dạng collection tốn kém.
- Tiêu thụ CPU/memory bandwidth của GC threads.
- Làm nhiễu warm-up/JIT benchmark hoặc những tác vụ có độ trễ chặt.
- Không mang lại gì nếu VM bỏ qua requests hoặc live-set vẫn còn root paths mạnh.
- Khiến nhóm phát triển hiểu nhầm vấn đề leak/resource management đã được giải quyết.

**Không khẳng định một lời gọi luôn chậm** — collector có thể bỏ qua hoặc xử lý concurrent. Nhưng gọi một cách mù quáng trong production là lựa chọn cần số liệu thực nghiệm chứng minh.

Nếu app liên tục gần OutOfMemoryError, hãy xem allocation rate, retaining paths, cache/queue sizes, heap limits, native memory và công cụ profiling. Đổi thứ tự GC requests mà không sửa cấu trúc giữ sống thường không giải quyết vấn đề.

## 16. Những hiểu nhầm phổ biến

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`System.gc()` luôn thu gom ngay.” | Đây là request, collector tùy JVM/flags |
| “Mọi `System.gc()` đều là Full GC.” | Không phải cam kết Java SE |
| “Bỏ strong reference thì GC sẽ chạy ngay.” | Reachability và lịch collector là hai việc khác |
| “`System.gc()` xóa cả object vẫn có strong root path.” | Collector phải giữ object còn cần cho chương trình |
| “`WeakReference.get()==null` chứng minh explicit GC đã thành công.” | Có thể clear bằng cách khác; chỉ nói về weak reference tại thời điểm đọc |
| “`WeakReference.get()!=null` chứng minh object leak.” | Có thể GC chưa chạy hoặc còn đường sống hợp lệ |
| “`PhantomReference.get()==null` nghĩa object đã chết.” | `get()` luôn trả null |
| “`ReferenceQueue.poll()==null` chứng minh object đang sống.” | Enqueue có timing riêng |
| “Sau `System.gc()` RSS phải giảm.” | JVM có thể giữ committed pages |
| “Heap used delta chính là size object X.” | Delta gồm nhiều objects và tác động runtime khác |
| “`-XX:+DisableExplicitGC` ngắt GC tự động.” | Automatic GC vẫn hoạt động |
| “Finalizer chắc chắn chạy sau explicit GC.” | Không đảm bảo; finalization là legacy |
| “GC thay được `close()`.” | Resource cleanup cần lifecycle tường minh |
| “Mọi local trong scope là root tới hết method.” | JIT liveness có thể ngắn hơn lexical scope |
| “`System.gc()` làm test weak cache deterministic.” | GC timing là bất định |
| “Gọi explicit GC nhiều lần sửa memory leak.” | Strong root paths vẫn giữ object |

## 17. Bài tập kèm đáp án

**Bài 1.** Có `Object a = new Object(); Object b = a; a = null;` rồi `System.gc()`. Sau đó `System.out.println(b != null)` in gì?  
**Đáp án:** `true`. `b` còn tham chiếu object khi được dùng.

**Bài 2.** JVM chạy `-XX:+DisableExplicitGC`, GC logs không báo event ngay sau `System.gc()`. Có phải GC hỏng?  
**Đáp án:** Không. Explicit request có thể bị bỏ qua; VM vẫn tự GC khi cần.

**Bài 3.** `weak.get()` khác `null` sau `System.gc()`. Có phải object chắc chắn leak?  
**Đáp án:** Không. Có thể còn strong path hoặc chưa xử lý weak reference.

**Bài 4.** `weak.get()==null` có chứng minh RSS giảm không?  
**Đáp án:** Không. Weak reference clearing và memory accounting/OS pages là các sự kiện khác nhau.

**Bài 5.** `PhantomReference.get()` trả `null` ngay sau constructor. Có phải referent đã reclaimed?  
**Đáp án:** Không. API luôn trả null.

**Bài 6.** Gọi explicit GC sau mỗi request làm latency cao. Có nên tăng số lần gọi cho sạch hơn?  
**Đáp án:** Không; điều tra root cause, đo pause/tải và cân nhắc bỏ explicit requests.

**Bài 7.** Static `List<byte[]>` giữ một triệu phần tử. `System.gc()` có phải cách giảm retained heap?  
**Đáp án:** Không. Cần loại bỏ/giới hạn references khi không cần.

**Bài 8.** `GarbageCollectorMXBean` count tăng một lần. Có chắc GC reclaim object X?  
**Đáp án:** Không. Count phản ánh collection events theo collector, không báo một object identity cụ thể.

**Bài 9.** Sau GC, heap used giảm nhưng process RSS không giảm. Có bắt buộc xem là lỗi?  
**Đáp án:** Không. JVM có thể giữ committed heap để tái sử dụng.

**Bài 10.** Muốn test file resource được đóng, nên làm gì?  
**Đáp án:** Dùng `try-with-resources`/`close()` và assert hành vi tường minh; không chờ `System.gc()`.

**Bài 11.** Vì sao compiler liveness khiến test dùng `System.gc()` khó đoán?  
**Đáp án:** VM có thể xác định reference không cần giữ tới hết scope; còn temporary references hoặc tối ưu allocation làm kết quả khác kỳ vọng.

**Bài 12.** `Reference.reachabilityFence(x)` có ép GC ngay sau đó không?  
**Đáp án:** Không. Nó giữ object mạnh tới vị trí cần thiết, không điều khiển lịch GC.

**Bài 13.** Cần biết tại sao một object chưa chết dù gọi explicit GC nhiều lần, dùng công cụ nào?  
**Đáp án:** Heap dump/profiler để xem **Paths to GC Roots**, static refs, ThreadLocals, listeners, queues, JNI handles.

**Bài 14.** `Runtime.getRuntime().gc()` có stronger guarantee hơn `System.gc()`?  
**Đáp án:** Không. Cùng ý nghĩa explicit GC request, không bảo đảm thu hồi.

**Bài 15.** Gọi `System.gc()` xong return bình thường có nghĩa toàn bộ GC đã hoàn tất?  
**Đáp án:** Không có guarantee portable về kiểu GC và thời điểm hoàn tất toàn bộ công việc.

## 18. Checklist kiểm chứng an toàn

- [ ] Xác định rõ đang hỏi: **GC request có được xử lý**, hay **object còn reachable**, hay **resource đã close**?
- [ ] Ghi JVM vendor, version, garbage collector và flags của tiến trình.
- [ ] Không assert GC timing hay `WeakReference` clearing sau một lời gọi.
- [ ] Hiểu `WeakReference`, `PhantomReference` và `ReferenceQueue` có semantics khác nhau.
- [ ] Kiểm tra hidden strong paths qua static fields, ThreadLocal, listeners, JNI.
- [ ] Dùng GC logs/JFR/MXBeans nếu kiểm tra collector behavior.
- [ ] Dùng heap dump và Paths to GC Roots nếu kiểm tra retaining paths.
- [ ] Không đánh đồng heap used, committed, reserved, RSS và retained size.
- [ ] Có timeout nếu thí nghiệm reference enqueue; không đợi vô hạn.
- [ ] Không gọi explicit GC như phương thức quản lý resource.
- [ ] Dùng test deterministic cho logic ứng dụng và lifecycle.
- [ ] Tránh tuning production dựa trên một lần chạy, một collector hoặc một bản JDK.

## 19. Tổng kết và tài liệu chuẩn

**`System.gc()` chỉ là lời gợi ý đối với JVM**, không phải một primitive đồng bộ để đo object lifetime, ép chạy Full GC, khẳng định weak reference bị clear, hoặc buộc hệ điều hành nhận lại bộ nhớ.

- Muốn hiểu **tại sao object sống**, tìm **strong paths tới GC roots**.
- Muốn biết **collector phản ứng với explicit request thế nào**, dùng GC logs/JFR/MXBeans của JVM và flags đang chạy.
- Muốn kiểm tra **memory leak**, dùng root-path/retained-size analysis, không ép GC lặp lại.
- Muốn bảo đảm **resource cleanup**, dùng API đóng tường minh.
- Muốn quan sát weak/soft/phantom references, chấp nhận **timing không được bảo đảm** và semantics khác nhau của từng loại reference.

**Tài liệu đối chiếu:**

1. Java SE 17 — `System.gc()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#gc()
2. Java SE 17 — `Runtime.gc()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Runtime.html#gc()
3. Java SE 17 — `java.lang.ref` package, định nghĩa các mức reachability: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html
4. Java SE 17 — `Reference.reachabilityFence`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html
5. Oracle JDK 17 — Garbage Collection Tuning Guide: https://docs.oracle.com/en/java/javase/17/gctuning/
6. Oracle JDK 17 — `java` command/flags: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
7. OpenJDK JEP 421 — Deprecate Finalization for Removal: https://openjdk.org/jeps/421

**Ghi nhớ:** nếu một bài kiểm thử có tiền đề “gọi `System.gc()` rồi object X chắc chắn biến mất”, tiền đề ấy sai. Hãy biến bài kiểm thử thành **kiểm chứng điều Java thực sự bảo đảm**, và dùng explicit GC chỉ như một tín hiệu quan sát trong thí nghiệm JVM có điều kiện rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)
