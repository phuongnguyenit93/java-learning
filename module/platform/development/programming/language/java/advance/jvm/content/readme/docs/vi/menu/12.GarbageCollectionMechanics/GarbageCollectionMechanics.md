<a id="back-to-top"></a>

# Cơ chế thu gom rác

## Menu
- [Đánh dấu, quét, sao chép và nén: các cách xác định và thu hồi vùng nhớ](#garbage-collection-mechanics-001)
- [Phân mảnh bộ nhớ và đánh đổi giữa di chuyển đối tượng với chi phí cập nhật tham chiếu](#garbage-collection-mechanics-002)
- [Giả thuyết thế hệ: đối tượng sống ngắn, già hóa và chuyển thế hệ](#garbage-collection-mechanics-003)
- [Eden, survivor và old trong mô hình bộ thu gom theo thế hệ](#garbage-collection-mechanics-004)
- [Thu gom nối tiếp, song song và đồng thời với ứng dụng](#garbage-collection-mechanics-005)
- [Stop-the-world và safepoint; phân biệt thời gian GC với tổng thời gian dừng](#garbage-collection-mechanics-006)
- [Write barrier, card table và remembered set khi theo dõi tham chiếu](#garbage-collection-mechanics-007)
- [Tốc độ cấp phát, lượng dữ liệu còn sống và khoảng trống cần cho GC](#garbage-collection-mechanics-008)
- [Thông lượng, độ trễ, CPU và dung lượng bộ nhớ trong bài toán lựa chọn GC](#garbage-collection-mechanics-009)

## <a id="garbage-collection-mechanics-001">Đánh dấu, quét, sao chép và nén: các cách xác định và thu hồi vùng nhớ</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh dấu, quét, sao chép và nén: các cách xác định và thu hồi vùng nhớ trong JVM

Garbage Collector (GC) giúp Java thu hồi vùng nhớ của những object không còn cần được giữ sống theo **reachability**. Tuy nhiên, “GC chạy” không có nghĩa chỉ có một hành động xóa object: một collector có thể **đánh dấu (mark)** object còn sống, **quét (sweep)** các vùng không còn sử dụng, **sao chép (copy/evacuate)** object sống sang vùng khác, và/hoặc **nén (compact)** các object sống để giảm phân mảnh. Những thao tác có mục đích khác nhau và thường được kết hợp.

Bài giải thích chính xác các thuật toán cơ sở và tác động lên **liveness, allocation, fragmentation, reference updating, pause time, throughput**. Sau đó đối chiếu với cách HotSpot Java 17 sử dụng các cơ chế này ở **Serial, Parallel, G1, ZGC và Shenandoah**. Mục đích không phải suy ngược rằng “mỗi collector bằng đúng một thuật toán”, mà hiểu vì sao các collector lựa chọn phối hợp nhiều bước.

## 1. Hai câu hỏi khác nhau: còn sống hay cần chuyển chỗ?

GC phải giải quyết hai vấn đề liên quan nhưng không đồng nhất:

1. **Liveness/reachability:** object nào còn có thể được chương trình truy cập qua các reference hợp lệ và phải được bảo toàn?
2. **Reclamation/placement:** sau khi biết object nào cần giữ, làm sao tái sử dụng vùng nhớ bỏ trống và bố trí các object còn sống sao cho những lần cấp phát sau hiệu quả?

**Mark** chủ yếu giải câu hỏi thứ nhất. **Sweep**, **copy/evacuation** và **compaction** giải câu hỏi thứ hai theo những chiến lược khác nhau. Các thuật toán GC thực tế còn phải xử lý nhiều threads, references, class metadata, Java Memory Model, JNI, vùng nhớ lớn và chi phí runtime.

Giả sử một heap nhỏ đang giữ các object A, B, C, D:

```text
GC roots
   |
   v
   A ---> B ---> C         D (không còn đường từ root)
           ^
           |
           +---- A

A, B, C: reachable theo graph.
D: không reachable từ các roots được xét; có thể thu hồi.
```

Dù vòng tham chiếu xuất hiện trong graph, **tracing GC không coi chu trình là leak tự thân**. Nếu toàn bộ một nhóm object không còn đường đi từ GC roots, collector có thể reclaim nhóm đó. Ngược lại, cache chứa object không còn giá trị nghiệp vụ nhưng vẫn có strong reference từ static root thì GC không được tự coi nó là rác.

### Một số thuật ngữ nền tảng

| Thuật ngữ | Nghĩa trong bài |
| --- | --- |
| **GC root** | Điểm bắt đầu theo dõi reachability, chẳng hạn reference đang sống trong thread frame/VM structures hoặc static references phù hợp |
| **Live/reachable object** | Object cần được bảo toàn theo các quy tắc reachability tại GC phase được xét |
| **Garbage/dead object** | Object không còn reachable theo mô hình tracing và reference-processing tương ứng |
| **Object graph** | Các object là nodes, fields/array entries tham chiếu là edges |
| **Mark bit/mark bitmap** | Metadata dùng ghi nhận object đã được phát hiện còn sống |
| **Free list** | Danh sách/cấu trúc quản lý những khoảng trống có thể cấp phát |
| **Fragmentation** | Tổng dung lượng trống có thể đủ, nhưng bị chia thành các khoảng nhỏ gây khó cấp phát liên tục |
| **Evacuation/to-space** | Di chuyển/copy live objects khỏi một vùng đang collection tới vùng đích |
| **Forwarding information** | Ánh xạ vị trí object cũ sang vị trí mới để tránh copy lặp và giúp cập nhật references |
| **Write/load barrier** | Runtime/JIT instrumentation duy trì tính đúng của marking, remembered sets hoặc relocation |

Đừng đồng nhất **Java object identity** với **địa chỉ bộ nhớ**. Một object có thể đổi vị trí do GC nhưng `a == b` vẫn phải phản ánh cùng identity logic, và các fields phải giữ đúng ngữ nghĩa.

## 2. Reachability: bước quyết định object nào cần giữ

Một tracing collector bắt đầu ở **GC roots**, đi theo những reference edges thích hợp và phát hiện tập objects reachable. Những nguồn roots trong HotSpot thường gồm references còn sống ở **Java thread frames**, registers/JIT metadata, static fields, JNI handles và các cấu trúc runtime cần thiết.

Ví dụ:

```text
Root R1 --> A --> B --> C
            |
            +----> E

Root R2 --> B

D --> F     (không có đường từ roots vào D hoặc F)
```

Nếu chỉ xét **strong reachability** ở ví dụ đơn giản, tập được mark là **{A, B, C, E}**. Hai object D, F không có đường từ roots nên có thể reclaimed. B vẫn là **một object duy nhất**, dù có đường từ R1 và R2.

Trong GC thật còn có các **Reference** types như `WeakReference`, `SoftReference`, `PhantomReference` và quy tắc reference processing; vì vậy mô hình “đi theo mọi reference edge như nhau” chưa đủ cho toàn bộ JDK.

### Marking không nhất thiết đi qua mọi object theo một thứ tự

Các collector có thể thực hiện DFS, BFS, dùng marking stacks/queues, bitmaps, xử lý song song và tận dụng data structures của heap. **Thứ tự duyệt** không làm thay đổi tập reachable đúng, nhưng ảnh hưởng locality, work balancing, pause và concurrency overhead.

**Đã đánh dấu object** cũng không đồng nghĩa “đã thu hồi memory”: marking chỉ xác định một tập cần bảo toàn. Phải có bước reclaim/evacuate/compact hoặc cơ chế tương đương để dung lượng thật sự trở lại allocator.

## 3. Thuật toán đánh dấu (Mark): từ roots tới toàn bộ objects reachable

Mô hình đánh dấu đơn giản:

1. Chuẩn bị trạng thái mark cho các objects thuộc phạm vi GC.
2. Đưa GC roots vào hàng đợi/cấu trúc duyệt.
3. Lặp: lấy một reference, nếu object chưa được mark thì đánh dấu và duyệt các reference fields/array entries thích hợp.
4. Khi không còn node cần xử lý, tập objects đã mark là **live set** theo snapshot/model GC đang sử dụng.
5. Những object chưa mark có thể xử lý bằng sweep hoặc bị loại bỏ gián tiếp khi copying/compaction.

Pseudocode:

```text
mark(roots):
    worklist = roots
    while worklist is not empty:
        object = worklist.remove()
        if object == null or alreadyMarked(object):
            continue
        setMarked(object)
        for ref in strongReferencesFrom(object):
            worklist.add(ref)
```

Đây là **mã giả của tracing strong references**, không phải bộ thuật toán đầy đủ của JDK. GC thực còn có safepoints, object layouts, concurrent updates, reference processing, class unloading và loại roots khác.

### Ba màu: white, gray, black

Mô hình tri-color phổ biến trong giải thích marking:

- **White:** object chưa được phát hiện/đánh dấu.
- **Gray:** đã phát hiện nhưng chưa quét xong outgoing references.
- **Black:** đã quét xong outgoing references trong mô hình đang xét.

```text
Ban đầu:
  roots --> [A:GRAY] --> [B:WHITE] --> [C:WHITE]

Xử lý A:
  [A:BLACK] --> [B:GRAY] --> [C:WHITE]

Xử lý B:
  [A:BLACK] --> [B:BLACK] --> [C:GRAY]

Xử lý C:
  [A:BLACK] --> [B:BLACK] --> [C:BLACK]
```

**White không luôn nghĩa là garbage tại mọi thời điểm**: một object còn white trong lúc marking chưa hoàn tất có thể sẽ được phát hiện. Mô hình tri-color đặc biệt hữu ích để hiểu các **barriers**: khi Java threads sửa object graph đồng thời với marking, collector phải ngăn một live object bị bỏ lỡ.

## 4. Ví dụ Java 17: mô phỏng reachability bằng đồ thị

Tạo file độc lập **`TraceGraphDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class TraceGraphDemo {
    static Set<String> mark(Map<String, List<String>> edges,
                            List<String> roots) {
        Set<String> marked = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(roots);

        while (!pending.isEmpty()) {
            String node = pending.pop();
            if (marked.add(node)) {
                for (String next : edges.getOrDefault(node, List.of())) {
                    pending.push(next);
                }
            }
        }
        return marked;
    }

    public static void main(String[] args) {
        Map<String, List<String>> graph = new HashMap<>();
        graph.put("A", Arrays.asList("B", "E"));
        graph.put("B", List.of("C"));
        graph.put("C", List.of("A")); // tạo chu trình
        graph.put("D", List.of("F"));
        graph.put("E", List.of());
        graph.put("F", List.of());

        Set<String> live = mark(graph, List.of("A"));
        Set<String> all = new TreeSet<>(graph.keySet());
        Set<String> unreachable = new TreeSet<>(all);
        unreachable.removeAll(live);

        System.out.println("live=" + new TreeSet<>(live));
        System.out.println("unreachable=" + unreachable);
    }
}
```

Biên dịch/chạy bằng Java 17+:

```bash
javac --release 17 TraceGraphDemo.java
java TraceGraphDemo
```

**Stdout chính xác:**

```text
live=[A, B, C, E]
unreachable=[D, F]
```

- Marking từ root A đi qua B, C, E; chu trình A→B→C→A không gây lặp vô hạn vì `marked.add(node)` chỉ trả `true` lần đầu.
- D và F nằm trong graph nhưng không reachable từ root A được chọn.
- Đây là **bộ mô phỏng đồ thị**, **không thực hiện GC của HotSpot**. Các objects `HashMap`, `String`, `Set` do chương trình Java này tạo vẫn có reachability thực tế riêng; kết quả chỉ phản ánh quy tắc *mark từ tập roots do ví dụ định nghĩa*.
- Bài học: trước khi reclaim, phải xác định **đường reachable từ roots**, không đếm đơn thuần số object hoặc số reference cycles.

## 5. Mark–Sweep: đánh dấu rồi quét vùng không còn sử dụng

Thuật toán **mark-and-sweep** tách thành hai công đoạn chính:

1. **Mark:** xác định những objects reachable.
2. **Sweep:** duyệt vùng heap/phạm vi collection, nhận diện các allocations không được mark và đưa những khoảng nhớ đó vào free list/cấu trúc quản lý vùng trống; chuẩn bị trạng thái mark cho collection tiếp theo theo cách phù hợp.

Ví dụ một dãy allocations (mỗi ô là một block minh họa):

```text
Trước marking:
| A | B | C | D | E | F | G | H |

Sau marking:
| A*| B | C*| D | E*| F | G | H |
  * = reachable/live; B,D,F,G,H không reachable

Sau sweep:
| A | . | C | . | E | . | . | . |

Các free runs: [1 block], [1 block], [3 blocks].
A, C, E KHÔNG đổi vị trí trong mô hình non-moving mark-sweep.
```

### Ưu điểm

- Không bắt buộc copy các live objects; thuận lợi khi phần lớn heap đang **live**, nếu dùng một phương án non-moving.
- Có thể reclaim dead blocks và tái sử dụng mà không cần thêm một vùng to-space bằng kích thước tương đương toàn collection set.
- Khi object không bị dời, có thể tránh chi phí relocation/reference fix-up của giai đoạn ấy.

### Nhược điểm

- **External fragmentation:** những khoảng trống nhỏ rải rác khiến allocator không tìm được block đủ lớn dù **tổng** free bytes có thể nhiều.
- **Allocation complexity:** có thể cần free lists theo size, coalescing adjacent free blocks hoặc chiến lược allocator khác; không còn chỉ “bump pointer” trên một vùng liên tục.
- **Sweep cost:** cần duyệt allocations/metadata trong scope; nhiều dead blocks có thể làm công việc duyệt và quản lý free list đáng kể.
- **Có thể cần compaction sau đó:** ứng dụng có object lớn hoặc vùng nhớ cần liên tục có thể không sử dụng được dung lượng trống bị chia nhỏ.

Không suy “mark-sweep luôn có pause dài” hay “không thể concurrent”: có các thiết kế concurrent/parallel marking và sweeping. Thuật toán khái niệm **không quyết định duy nhất** cách triển khai lịch GC.

### Khi nào fragmentation thật sự quan trọng?

Giả sử heap mô phỏng có tổng cộng **4 đơn vị trống**, nhưng các free runs chỉ có độ dài 1, 1 và 2. Yêu cầu allocation một object kích thước **3 đơn vị liên tục** có thể **thất bại trong vùng ấy**, dù 4 đơn vị trống trên tổng. GC/allocator có thể tìm region khác, mở rộng heap, trigger collection/compaction hoặc báo lỗi nếu không có phương án.

Điều này khác **internal fragmentation**: cấp một block lớn hơn yêu cầu vì alignment/size classes, khiến phần memory nằm **bên trong block đã cấp** không được dùng hết.

## 6. Mark–Compact: đánh dấu, tính vị trí mới và gom objects sống

**Mark-compact** bắt đầu bằng việc xác định live objects, rồi sắp xếp/di chuyển chúng theo một bố cục đích để tạo những khoảng free lớn và tương đối liên tục.

Một kiểu **sliding compaction** đơn giản:

```text
Trước:
| A*| . | C*| . | E*| . | . | . |
   ^      ^      ^
   live objects có các vị trí cũ khác nhau

Sau compact:
| A | C | E | . | . | . | . | . |
            ^  vùng free liên tục ở cuối

Mapping minh họa:
  A: old slot 0 -> new slot 0
  C: old slot 2 -> new slot 1
  E: old slot 4 -> new slot 2
```

Đối với object graph thật, một collector compaction có thể cần:

1. Xác định live objects và kích thước từng object.
2. Tính **destination address** và lưu forwarding/offset metadata phù hợp.
3. **Cập nhật references** từ GC roots, object fields, array elements và VM structures để chúng trỏ tới đích mới.
4. Di chuyển dữ liệu object theo thứ tự an toàn, bảo toàn Java object identity, monitor state, hash/metadata và field values.
5. Công bố vùng trống mới cho allocator, đồng bộ hóa và kết thúc phase theo invariant collector.

**Mark-compact không nhất thiết có một lần sweep đầy đủ trước khi copy**. Đó là nhóm thuật toán: mark xong có thể xác định destinations rồi slide/copy và lập vùng free, không buộc chạy riêng một phase free-list sweeping giống mark-sweep.

### Ưu điểm và trade-offs

- **Giảm external fragmentation:** tạo free regions lớn, giúp allocation nhanh và đều hơn.
- **Locality:** live objects được gom có thể cải thiện locality/TLB/cache behavior trong những trường hợp thích hợp.
- **Có chi phí relocation:** di chuyển bytes live và cập nhật references/metadata; trong một số workloads, đắt hơn non-moving sweep.
- **Pause/concurrency:** một implementation STW compact toàn heap có thể có pause lớn; những thuật toán concurrent/incremental làm giảm pause bằng cách bổ sung barriers và phức tạp.
- **Độ phức tạp thực** phụ thuộc live bytes, số reference edges, layout, barriers, parallelism và phần heap được chọn.

Đừng cho rằng mọi collector có nhãn “compacting” thực hiện **cùng một sliding algorithm**, hoặc rằng compact chỉ dùng khi heap hoàn toàn đầy.

## 7. Copying/Evacuation: giữ live objects bằng cách chuyển sang vùng đích

Một **copying collector** chọn một vùng nguồn (from-space/collection set), tìm các object còn sống và **copy** chúng sang **to-space/destination**. Sau đó, vùng nguồn có thể được reclaimed tương đối nhanh nếu toàn bộ references hợp lệ đã được điều phối.

```text
From-space trước GC:
| A*| B | C*| D | E*| F | ... |
   |      |      |
   +------+------+
          |
          v
To-space sau copying:
| A | C | E | free ........ |

From-space có thể được tái sử dụng khi relocation hoàn tất an toàn.
```

Cơ chế rất quen thuộc là **forwarding**:

```text
Root R1 ----> A_old
Root R2 ----> A_old

Lần đầu gặp A_old:
  copy A_old -> A_new
  forwarding(A_old) = A_new

Lần thứ hai gặp A_old:
  thấy forwarding đã tồn tại, KHÔNG tạo thêm một Java object A mới
  cập nhật/chuyển hướng R2 tới A_new

Cuối phase:
Root R1 ----> A_new
Root R2 ----> A_new
```

Về mặt Java, hai roots vẫn trỏ tới **cùng object A**. Forwarding pointers hay forwarding tables là **implementation details**, không phải Java fields được phép đọc trong source.

### Vì sao copying thu hút nhiều collectors?

- **Allocation sau copying** thường có thể dùng bump pointer trong vùng đích có không gian liên tục, giảm overhead allocator.
- Khi tỷ lệ object chết cao, lượng bytes phải copy là **live bytes**, thường ít hơn total allocated bytes của vùng.
- To-space có thể cho bố cục ít phân mảnh sau mỗi evacuation.
- Hợp với mô hình **generational hypothesis**: phần lớn objects được tạo trong thời gian ngắn có vòng đời ngắn, vì vậy young generation có live set tương đối nhỏ trong nhiều workloads.

### Nhược điểm và giới hạn

- Cần **đủ destination space** để đưa các live objects tới nơi ở mới; đây là tài nguyên không thể bỏ qua.
- Khi phần lớn objects còn sống, **copy cost** và destination space có thể rất lớn.
- Cần scan/cập nhật references, quản lý forwarding, synchronization giữa GC workers và tương tác với runtime/JIT/JNI.
- “Copy mọi object” không áp dụng: thường chỉ objects trong selected regions/young generation được xử lý; objects ở nơi khác có thể không đổi.
- Khi thiếu to-space, collector phải có cơ chế fallback/failure handling. Không thể giả định mọi evacuation luôn thành công.

Một copying collector kinh điển có thể dùng **hai semispaces**, luân phiên vai trò from/to. **G1 không phải mô hình hai nửa heap cố định**; nó chọn nhiều regions và evacuate tới những regions phù hợp. Đừng đồng nhất mọi copying implementation với “chỉ sử dụng 50% heap”.

## 8. Bảng đối chiếu ba cách thu hồi chính

| Thuộc tính | Mark–Sweep | Mark–Compact | Copy/Evacuation |
| --- | --- | --- | --- |
| Phân biệt live/dead | Mark reachable objects | Mark reachable objects | Discover/trace reachable objects trong source set |
| Xử lý dead space | Add free blocks vào allocator/free lists | Gom live objects, tạo free span(s) | Reclaim vùng nguồn sau khi evacuate |
| Object sống có bắt buộc move? | **Không** trong mô hình non-moving | **Có thể** move | **Có thể** move khi thuộc vùng được evacuate |
| External fragmentation | Có thể còn đáng kể | Thường giảm mạnh | Ít hơn trong vùng đích mới gom |
| Chi phí theo live bytes | Không cần copy live bytes | Có relocation/compaction work | Có copy/evacuation work |
| Extra destination space | Thường không cần equal-size to-space | Không nhất thiết cần full second space | Phải có space để chứa objects được copy |
| Reference fix-up | Không cần vì riêng non-moving sweep | Cần nếu objects move | Cần hoặc dùng forwarding/barriers thích hợp |
| Allocation strategy điển hình | Free lists/size classes | Bump pointer trên vùng free liên tục | Bump pointer trong to-space |
| Workload hợp lý | Live set lớn, hạn chế copy, chấp nhận/kiểm soát fragmentation | Cần chống fragmentation, muốn contiguous allocation | Vùng có nhiều dead objects, như young regions |
| Pause time | Phụ thuộc thiết kế/parallelism | Phụ thuộc amount move và thiết kế | Phụ thuộc live bytes, roots, edges, space, barriers |

**Bảng mô tả nguyên lý**, không phải benchmark hay bảo đảm một collector cụ thể luôn nhanh/chậm hơn collector khác. Một thiết kế hiện đại có thể kết hợp **concurrent mark + region evacuation + occasional full compact**, tùy phase.

## 9. Ví dụ Java 17: mô phỏng sweep và compact trên các slots

Tạo **`SweepCompactToy.java`**:

```java
import java.util.Arrays;

public class SweepCompactToy {
    static String[] sweep(String[] slots, boolean[] live) {
        String[] copy = slots.clone();
        for (int i = 0; i < copy.length; i++) {
            if (!live[i]) {
                copy[i] = null;
            }
        }
        return copy;
    }

    static String[] compact(String[] swept) {
        String[] result = new String[swept.length];
        int destination = 0;
        for (String item : swept) {
            if (item != null) {
                result[destination++] = item;
            }
        }
        return result;
    }

    public static void main(String[] args) {
        String[] slots = {"A", "B", "C", "D", "E", "F"};
        boolean[] live = {true, false, true, false, true, false};

        String[] swept = sweep(slots, live);
        String[] compacted = compact(swept);

        System.out.println("before=" + Arrays.toString(slots));
        System.out.println("swept=" + Arrays.toString(swept));
        System.out.println("compacted=" + Arrays.toString(compacted));
        System.out.println("freeAfterSweep=" + 3);
        System.out.println("largestFreeRunAfterSweep=" + 1);
        System.out.println("largestFreeRunAfterCompact=" + 3);
    }
}
```

```bash
javac --release 17 SweepCompactToy.java
java SweepCompactToy
```

**Stdout:**

```text
before=[A, B, C, D, E, F]
swept=[A, null, C, null, E, null]
compacted=[A, C, E, null, null, null]
freeAfterSweep=3
largestFreeRunAfterSweep=1
largestFreeRunAfterCompact=3
```

**Đây là mô phỏng bố cục**, không phải GC runtime: `String[]` và các String literals thực tế của Java program vẫn được GC quản lý riêng. Các biến `slots`, `swept` và `compacted` là ba Java arrays đang cùng tồn tại tại thời điểm in; không có thao tác di chuyển object vật lý của HotSpot ở đây.

Mô hình cho thấy **tổng số free blocks sau sweep là 3** nhưng bị chia ba vị trí rời rạc; sau compact cùng tổng free bằng 3 nhưng liên tục. Nó cũng cố ý bỏ qua việc thật sự cập nhật object-reference addresses vì các strings A/C/E chỉ là **nhãn block**.

## 10. Update references: di chuyển object không được đổi Java identity

Một object có thể được refer tới từ nhiều chỗ:

- Local variables/operand stacks/JIT registers còn live trong thread frames.
- Static fields của classes có owner còn sống.
- Fields của objects khác trong heap.
- Reference-array elements, collections.
- JNI handles và VM runtime structures.

Khi copy/compact object, GC phải bảo đảm mọi đường tham chiếu **còn sử dụng được** đi tới logical object đúng. Với stop-the-world collector, collector có thể cập nhật relevant slots trước khi mutator tiếp tục. Với concurrent relocating collector, forwarding/barriers có thể cho phép **lazy remapping**, nên chưa chắc mọi slot chứa representation địa chỉ mới đồng thời.

```text
Trước:
  root1 --> A@old
  root2 --> A@old
  B.ref --> A@old

Sau relocation:
  root1 --> A@new
  root2 --> A@new
  B.ref --> A@new

A vẫn là A: giữ fields, identity, locking semantics.
```

**`System.identityHashCode(A)` không phải raw address.** Nó không được phép đổi chỉ vì GC chuyển chỗ một object vẫn là object ấy. Hai object khác nhau cũng có thể trùng identity hash code, nên identity hash không phải unique ID vĩnh viễn.

Các thuật ngữ như compressed oops, object header/mark word, forwarding bits, barriers, remembered sets là **internal HotSpot**. Mã Java portable không được dựa vào layout pointer bits.

## 11. Generational GC: tại sao young/old thường có chính sách khác nhau?

Giả thuyết thiết kế phổ biến: **đa số object chết tương đối sớm**, trong khi một số object có tuổi thọ dài. Một generational collector thường tổ chức object lifetimes vào các vùng trẻ và già để collection hiệu quả.

Ví dụ luồng khái niệm:

```text
Allocation --> Eden (young)
                  |
                  | Young collection
                  v
            Survivor regions / copies
                  |
                  | qua ngưỡng tuổi / điều kiện collector
                  v
              Old regions

Young collection có thể thu gom nhiều objects chết nhanh.
Old collection xử lý objects sống lâu theo chính sách khác.
```

Không có quy tắc “mọi Java object luôn qua đúng N young GCs rồi mới promotion”. Ngưỡng tuổi, survivor size, promotion và object có được phân bổ thẳng vào một vùng khác hay không phụ thuộc GC và flags.

### Tại sao young generation hay dùng copying?

Nếu 100 MiB được tạo ra nhưng chỉ 5 MiB còn sống sau một young collection, copying khoảng 5 MiB có thể rẻ hơn việc phải quản lý hàng chục nghìn free blocks nhỏ. Tuy nhiên đây là **ví dụ giả định**, không phải con số/live ratio của Java mặc định.

### Old generation có phải luôn dùng mark-sweep?

Không. Từng collector chọn thuật toán; Serial Old/Parallel Old thường thực hiện **mark-compact** trong những tình huống phù hợp; G1 có **region evacuation** và full compaction fallback; ZGC/Shenandoah có **concurrent relocation**. Thuật ngữ “young = copying, old = mark-sweep” là đơn giản hóa quá mức đối với HotSpot hiện đại.

## 12. G1 Java 17: kết hợp marking, evacuation và compaction

G1 (Garbage-First) trên HotSpot chia heap thành **regions**. Eden, survivor và old là các tập regions logic; không nhất thiết là những khối địa chỉ lớn liền nhau. G1 được thiết kế để cân bằng throughput và pause-time goals bằng cách chỉ thu gom các **collection sets (CSet)** phù hợp.

Các hoạt động chính:

1. **Young evacuation pauses:** chọn young regions trong CSet, tìm roots và references vào CSet, copy live objects ra các destination regions, reclaim selected source regions.
2. **Concurrent marking:** theo dõi live data của old regions, giúp ước lượng nơi có nhiều reclaimable garbage.
3. **Mixed collections:** ngoài young regions, chọn thêm những old regions có lợi để evacuate.
4. **Fallback Full GC:** trong những tình huống memory pressure/không thể tiến triển, collector có thể cần một full-heap compaction STW, có pause dài hơn.
5. **Humongous allocations/regions:** objects rất lớn có chính sách riêng, không giống các young objects nhỏ; fragmentation theo regions và trigger cycles cần được theo dõi.

### G1 “mark” và “evacuate” không nhất thiết cùng lúc

- **Concurrent marking** đánh giá liveness để chọn regions và chuẩn bị collection.
- **Evacuation** copy live objects trong **STW pauses** của G1 Java 17 cho các selected regions.
- **Remembered sets/card barriers** giúp biết references **từ ngoài collection set vào trong**, giảm nhu cầu quét toàn bộ heap ở mỗi young pause.
- G1 marking dùng nguyên lý **Snapshot-At-The-Beginning (SATB)** và write barriers để duy trì một liveness snapshot thích hợp trong khi mutators chạy.

Đừng nói “vì G1 có concurrent marking nên G1 luôn concurrent-copying mọi object”. Đây là **hai công việc khác nhau**. Những object ở ngoài CSet thường không move trong pause ấy.

### Nhìn ví dụ region bố cục

```text
G1 heap (regions, không phải semispaces 50%-50%):

R0: Eden         [A* B C* D]   <- chọn vào CSet
R1: Survivor     [E* F G]      <- chọn vào CSet
R2: Old          [H I* J K*]  <- tùy young/mixed CSet
R3: Old          [L* M]        <- nằm ngoài CSet
R4: Free         [            ]
R5: Free         [            ]

Evacuate A,C,E... sang destination regions;
reclaim R0,R1 khi an toàn;
R3 không tự copy chỉ vì một young collection.
```

Vị trí và ngưỡng thực do G1 quyết định. Ký hiệu `*` là object live **trong ví dụ**, không phải dữ liệu heap dump thật.

### Evacuation failure

Nếu thiếu available to-space, G1 có thể gặp **evacuation failure**; collector phải duy trì reference correctness dù một số live objects không được copy như dự định. Sau đó có thể phát sinh memory pressure và một Full GC nếu không đủ tiến triển. Vì thế **reserve/headroom** là một yếu tố của vận hành GC, không chỉ “heap used chưa đầy là đủ”.

Theo Oracle G1 tuning guidance, policy luôn là **mục tiêu tối ưu**, không có bảo đảm mọi pause luôn thấp hơn một số mili giây đã cấu hình.

## 13. Các collectors khác trong HotSpot Java 17

| Collector | Lựa chọn cơ chế ở mức khái quát | Trade-off cần hiểu |
| --- | --- | --- |
| **Serial GC** (`-XX:+UseSerialGC`) | GC work chủ yếu một collector thread; young copying và old collection/compaction theo implementation | Đơn giản, phù hợp một số heaps nhỏ/CPU hạn chế; pause STW |
| **Parallel GC** (`-XX:+UseParallelGC`) | Các GC threads làm việc song song; young copying và old **parallel compaction** theo cấu hình chuẩn | Tối ưu throughput, có thể chấp nhận STW pauses |
| **G1 GC** (`-XX:+UseG1GC`) | Region-based concurrent marking + STW evacuation/mixed collections + fallback compaction | Cân bằng throughput/pause goals và memory overhead |
| **ZGC** (`-XX:+UseZGC`) | Concurrent marking/relocation với load barriers và runtime metadata đặc thù | Ưu tiên latency thấp, có overhead/throughput trade-offs |
| **Shenandoah** (`-XX:+UseShenandoahGC` trên distributions phù hợp) | Concurrent marking/evacuation, forwarding/barriers theo mode | Giảm pause, trade-offs về CPU/memory/availability theo build |

Đây là **tổng quan giáo dục** của Java 17, không hứa mỗi distribution JDK 17 đều bật/bundled tất cả collectors. **Java SE specification không bắt buộc collector cụ thể.**

**Quan trọng về phiên bản:** ZGC trong JDK 17 khác các kiến trúc **Generational ZGC** của các JDK mới hơn. Khi đọc tài liệu tuning hoặc báo cáo số liệu, giữ đúng cặp **JDK version + collector**; không lấy hành vi của bản mới áp nguyên cho Java 17.

### Đừng xếp collector theo “tốc độ tuyệt đối”

- Throughput: phần thời gian application code làm việc thay vì GC.
- Latency: độ dài/độ phân bố pauses ảnh hưởng request tail latency.
- CPU overhead: marking/evacuation/barriers/JIT work có thể tốn CPU ngay cả khi pause ngắn.
- Memory overhead: remembered sets, bitmap, to-space/headroom và metadata.
- Predictability: workload allocation rate/live-set size quyết định nhiều.

Một collector throughput cao có thể có long pauses; một collector low-latency có thể tiêu tốn thêm CPU/memory. Phải đo theo workload thực thay vì chọn chỉ qua tên thuật toán.

## 14. Concurrent marking cần barriers để bảo vệ tính đúng

Nếu GC marking diễn ra **đồng thời** với application threads (mutators), object graph có thể thay đổi giữa lúc một object được quét và lúc collector quét reference kế tiếp.

Ví dụ tri-color:

```text
A:BLACK  (đã scan xong)
B:GRAY   (đang chờ scan)
C:WHITE  (chưa được phát hiện)

Nếu mutator đổi reference graph khiến C chỉ reachable
từ A sau khi A đã BLACK, collector có nguy cơ bỏ sót C,
nếu không có barrier/invariant phù hợp.
```

Các collectors thường dùng **write barriers** để ghi nhận/điều chỉnh thay đổi reference fields:

- **Snapshot-At-The-Beginning (SATB)**: mô hình bảo vệ snapshot của objects còn sống tại một mốc thích hợp; ví dụ cần ghi nhận **old reference value** khi một edge bị thay thế để tránh làm mất dấu object snapshot-live.
- **Incremental update**: một nhóm thiết kế khác tập trung vào những edges mới hoặc object colors để giữ invariant của marking.
- **Card marking/remembered sets**: ghi nhận những heap cards/regions có reference changes, hữu ích cho generational/region collectors khi tìm cross-region edges.

**Không phải mọi write barrier làm cùng một việc**, và không phải mọi GC đều dùng toàn bộ ba cơ chế trên. G1 Java 17 dùng barrier infrastructure cho SATB marking và tracking remembered sets với các vai trò khác nhau.

### Barriers của GC khác `volatile`/`synchronized`

- **GC barrier** là instrumentation của VM/JIT bảo vệ tracing/relocation invariant.
- **Java Memory Model barrier/synchronization** kiểm soát visibility và ordering giữa application threads.
- Hai hệ thống có thể cùng dùng các thao tác máy đặc thù, nhưng **mục đích/contract khác nhau**. Không bỏ `volatile` hoặc locks vì “GC cũng có barrier”.

### Concurrent relocation còn cần đúng target reference

Đối với ZGC/Shenandoah, khi object đang được di chuyển trong lúc mutator tiếp tục chạy, collector dùng **forwarding/remapping/load barriers** để bảo đảm việc truy cập tới đúng object. Do đó không thể dùng mô hình “STW sửa mọi references cùng một lần rồi mới resume” để giải thích toàn bộ concurrent relocation.

## 15. Stop-the-world, safepoints và GC phases

**Stop-the-world (STW)** nghĩa là các application threads tạm dừng ở những điểm/thời điểm VM yêu cầu để GC hoặc VM thực hiện công việc cần trạng thái heap/thread đủ nhất quán. GC không mặc định là một thread ngầm duy nhất chạy cạnh application.

Các dạng công việc trong một collection có thể gồm:

- Root enumeration/initial mark.
- Concurrent marking khi collector hỗ trợ.
- Reference processing.
- Evacuation/copying hoặc compaction.
- Root/reference fix-up, remapping.
- Remark/cleanup, class unloading và bookkeeping.

Một collector có thể vừa có **concurrent phases** vừa có **STW pauses**. Ví dụ G1 có concurrent marking và STW evacuation. ZGC thiết kế phần lớn công việc đắt tiền concurrent, nhưng không đồng nghĩa tuyệt đối không có pause nào.

**Safepoint pause không chỉ do GC.** JVM có thể cần safepoints/handshakes cho những hoạt động VM khác. Khi đọc log latency, xem loại VM operation, GC ID, elapsed time và ứng dụng đang chạy gì.

## 16. GC roots, reference types và quyết định giữ sống

Một tracing collector không thể coi **mọi edge** như strong reference thông thường. Java cung cấp reference objects với semantics riêng:

| Loại | Quy tắc liên quan | Tác dụng đến GC |
| --- | --- | --- |
| **Strong reference** | Reference thông thường từ fields/locals/arrays | Giữ referent reachable nếu đường từ roots hợp lệ |
| **SoftReference** | Referent có thể được GC clear theo memory sensitivity | Không phải TTL hay cache bound chính xác |
| **WeakReference** | Không giữ referent strong-live; có thể clear khi weakly reachable | Thích hợp cho một số lookup/canonicalization, không đảm bảo lúc clear |
| **PhantomReference** | Dùng cho cleanup lifecycle khi object đạt trạng thái tương ứng; `get()` luôn null | Không dùng đọc lại object như cache value |
| **JNI handles** | Có local/global/weak global semantics riêng | Native code phải theo contract để GC quản lý references |

### Clearing khác reclaiming và relocation

- **Mark:** xác định liveness theo reachability và những reference rules thích hợp.
- **Clear weak reference:** thay referent logic bằng null khi các điều kiện GC thỏa; không phải object bị copy sang nơi mới.
- **Reclaim dead object:** đưa memory của object không còn cần giữ về allocator.
- **Relocate live object:** đưa object còn sống tới location mới, giữ identity.

Đây là bốn hành động khác nhau. Thấy `weak.get() == null` không chứng minh “GC đã nén heap”; thấy output vẫn đúng cũng không chứng minh object thật đã được chuyển chỗ.

### Finalization và Cleaner

Không trông chờ finalizer để quản lý file/socket/database connections. Finalization có nhiều vấn đề và không có deadline rõ. Dùng `try-with-resources`, `AutoCloseable` hoặc lifecycle code tường minh; `Cleaner` là lựa chọn hạn chế cho fallback native cleanup khi thiết kế phù hợp. GC không phải service gọi `close()` vào đúng thời điểm nghiệp vụ yêu cầu.

## 17. Java 17: object identity được giữ bất chấp GC có di chuyển hay không

Tạo **`IdentityAcrossGc.java`**:

```java
import java.util.IdentityHashMap;

public class IdentityAcrossGc {
    static final class Node {
        final int id;
        Node child;

        Node(int id) {
            this.id = id;
        }
    }

    static volatile Object allocationSink;

    public static void main(String[] args) {
        Node parent = new Node(10);
        Node child = new Node(20);
        parent.child = child;

        Node alias = parent;
        Node[] roots = {parent};
        IdentityHashMap<Node, String> names = new IdentityHashMap<>();
        names.put(parent, "same-object");

        int hashBefore = System.identityHashCode(parent);

        for (int i = 0; i < 512; i++) {
            allocationSink = new byte[16 * 1024];
        }

        System.out.println("alias=" + (alias == parent));
        System.out.println("array=" + (roots[0] == parent));
        System.out.println("childId=" + parent.child.id);
        System.out.println("lookup=" + names.get(alias));
        System.out.println("hashStable="
                + (System.identityHashCode(parent) == hashBefore));
    }
}
```

```bash
javac --release 17 IdentityAcrossGc.java
java IdentityAcrossGc
```

**Stdout:**

```text
alias=true
array=true
childId=20
lookup=same-object
hashStable=true
```

Chương trình kiểm tra **Java reference semantics**, không phụ thuộc collector nào hoạt động. GC **có thể không chạy**, hoặc chạy nhưng object parent không nằm trong collection set; vòng tạo arrays **không chứng minh physical relocation**. `allocationSink` chỉ giữ một array được ghi gần nhất reachable thông qua static field volatile; compiler/GC vẫn có quyền tối ưu hợp lệ.

Nếu chạy với heap nhỏ và GC logging, có thể dễ quan sát một số collections, nhưng output cuối vẫn phải đúng miễn ứng dụng không gặp memory/resource errors. Không nên cố đọc address từ `System.identityHashCode`.

## 18. Tác động của live set, allocation rate và fragmentation

Một collector không chỉ chịu ảnh hưởng bởi **tổng heap size**. Đặc biệt quan trọng:

- **Live set size:** lượng bytes và objects cần bảo toàn sau marking. Copy/compact live set lớn có thể tốn nhiều công hơn.
- **Allocation rate:** số bytes được cấp theo thời gian. Allocation nhanh gây nhiều cycles/young pauses dù live set cuối không lớn.
- **Survival rate:** tỷ lệ objects còn sống khi collection xảy ra; quyết định copying cost.
- **Reference graph complexity:** số edges cần scan, pointer density và metadata.
- **Fragmentation:** có bao nhiêu free bytes **trong các spans đủ lớn**; nhiều free nhỏ có thể không giúp allocation một object lớn.
- **Headroom/to-space:** vùng đích có đủ chỗ cho surviving objects không?
- **Concurrency/CPU budget:** marking/relocation concurrent có tranh tài nguyên với application.
- **Collector tuning/ergonomics:** region sizes, generations, thresholds, pause goals, parallel threads.

### Ví dụ số giả định

Giả sử young regions có 300 MiB đã allocate và chỉ 20 MiB objects còn sống khi collection diễn ra. Copying 20 MiB có thể có lợi nếu overhead root scanning/barriers và destination space hợp lý.

Nếu một khác workload có 280 MiB objects còn sống trên 300 MiB, evacuation phải copy gần như toàn bộ live bytes; tương quan cost thay đổi. Không được biến ví dụ thành định luật “copy luôn nhanh hơn mark-sweep” hay “mark-compact luôn chậm nhất”.

### Fragmentation không đồng nghĩa OOME ngay lập tức

Allocator/collector có thể thử tìm nơi khác, reclaim, compact, expand committed heap hoặc fallback tùy implementation. Một `OutOfMemoryError` có thể do **thực sự thiếu free memory**, **không đủ contiguous regions/to-space**, **retained live set lớn**, metaspace/native memory hoặc giới hạn container. Phải đọc loại lỗi và log trước khi kết luận.

## 19. Xem GC logging trên HotSpot Java 17

Bật Unified Logging bằng `-Xlog`:

```bash
java -Xms128m -Xmx512m -XX:+UseG1GC -Xlog:gc IdentityAcrossGc
```

Với phân rã phases chi tiết hơn, trên build hỗ trợ tags/levels tương ứng:

```bash
java -Xms128m -Xmx512m -XX:+UseG1GC -Xlog:gc*,gc+phases=debug IdentityAcrossGc
```

**Không có stdout GC cố định**: số collections, thời điểm pause, phases và heap occupancy phụ thuộc workload, OS, collector, JIT và build. Chạy ví dụ với heap vừa đủ thậm chí **có thể không phát sinh GC event đáng kể**. Dùng log để tìm hành vi thực, không giả lập một trace rồi trình bày như đã đo.

Trong G1 GC logs, có thể thấy các thông tin về:

- Young/normal/mixed collections.
- Eden, survivor và old regions trước/sau.
- Evacuate Collection Set / merge heap roots, copy/scan phases, reference processing.
- Concurrent marking phases, Remark/Cleanup.
- Pause durations và memory occupancy.

Các tên logs thay đổi theo bản JDK. Lấy **ý nghĩa phases** và thực nghiệm đúng JVM đang dùng làm căn cứ, không viết parser quá cứng theo một chuỗi log duy nhất.

### Công cụ quan sát khác

```bash
jcmd <PID> VM.flags
jcmd <PID> GC.heap_info
jstat -gc <PID> 1000 5
```

`<PID>` là **placeholder process ID**, không phải một process đã xác minh. Các lệnh `jcmd`/`jstat` cần process Java đang chạy và đủ quyền; `IdentityAcrossGc` kết thúc rất nhanh nên thường không có PID để attach khi bạn thực thi lệnh về sau.

- `VM.flags` giúp biết cấu hình VM/GC.
- `GC.heap_info` báo trạng thái heap/collector theo implementation.
- `jstat -gc` cung cấp counters/occupancy/cycle stats theo collector/JDK.
- `jcmd` có thể có diagnostic commands khác; chạy `jcmd <PID> help` để biết những lệnh thực sự được hỗ trợ.

**Heap dump** có ích khi muốn biết vì sao object còn reachable, chứ không mô tả đầy đủ các bước copy/compact trong GC. **GC logs** cho biết collection hoạt động thế nào, chứ không chỉ ra độc nhất object nào là leak root.

## 20. Phân tích một tình huống chẩn đoán thực tế

Giả sử một service có:

- Heap configured maximum 2 GiB.
- GC log: young collections ngày càng dày, mixed cycles sau đó, thỉnh thoảng Full GC.
- Heap after-GC occupancy tăng từ 700 MiB lên 1,7 GiB dưới workload tương đương.
- Có thông báo evacuation failure/to-space pressure ở một số thời điểm.

Một quy trình hợp lý:

1. **Kiểm tra liveness/retention trước:** live set tăng do cache, listener, ThreadLocal, request queues hoặc data growth có chủ đích? Lấy histogram/heap dump khi an toàn.
2. **Đọc GC logs:** có phải allocation rate tăng, young survival rate tăng, promotion/old occupancy tăng không? Full GC xuất hiện vì vấn đề gì?
3. **Tìm fragmented/region pressure:** liệu collection sets còn đủ destination space để evacuate không? Có nhiều humongous allocations/regions không?
4. **Đánh giá policy GC:** collector/heap sizing/headroom phù hợp với pause goals và CPU/container budgets chưa?
5. **Sửa nguyên nhân ở application nếu live set không còn cần:** bounded cache, release listeners, remove ThreadLocal, shutdown resource owners.
6. **Kiểm tra lại cả throughput và latency:** sửa chỉ để hết `OutOfMemoryError` nhưng tăng pause lên vài giây có thể chưa đạt mục tiêu vận hành.

**Tăng `-Xmx` có thể trì hoãn tình huống thiếu chỗ**, nhưng nếu ứng dụng tiếp tục giữ vô hạn object, nó không sửa memory leak logic. Ngược lại, nếu live set hợp lệ và heap quá nhỏ, cần tính capacity/headroom thực sự và bộ nhớ ngoài heap trong container.

## 21. Mười lăm hiểu lầm cần tránh

| Hiểu lầm | Sự thật |
| --- | --- |
| “Mark tự giải phóng object” | Mark chỉ phân loại liveness; cần reclaim/evacuation/compaction |
| “Object không được mark tại mọi thời điểm là garbage” | Marking có các trạng thái trung gian; white có thể còn chưa scan |
| “Chu trình A↔B làm tracing GC không giải phóng được” | Chu trình không reachable từ roots vẫn có thể reclaim |
| “Mark-sweep luôn dồn các live objects sát nhau” | Non-moving sweep thường để lại holes và free lists |
| “Có tổng free lớn thì cấp được object lớn” | External fragmentation có thể không đủ contiguous space |
| “Copy phải sao chép toàn bộ heap” | Thường chỉ selected generation/regions, live objects |
| “Copying luôn phải giữ nửa heap trống” | Hai semispaces là một thiết kế; G1 dùng selected regions |
| “Compact là sweep nhưng xóa mạnh hơn” | Compact **di chuyển/gom live objects**, sweep chủ yếu reclaim dead blocks |
| “GC move object làm `==` hoặc identity hash thay đổi” | Java-visible object identity phải giữ nguyên |
| “Mọi GC Java 17 đều dùng cùng một thuật toán” | Serial, Parallel, G1, ZGC, Shenandoah phối hợp khác nhau |
| “G1 concurrent marking nghĩa G1 concurrent evacuation như ZGC” | G1 Java 17 evacuate CSet trong STW pauses chủ yếu |
| “GC write barrier bảo đảm Java application thread safety” | GC barrier khác `volatile`, locks và happens-before |
| “Một GC event chứng minh object X cụ thể đã move” | Không; collection scope/relocation cần bằng chứng sâu hơn |
| “Mark-compact luôn là Full GC” | Có compaction/evacuation theo regions hoặc phases tùy collector |
| “Heap used lớn là heap leak chắc chắn” | Cần post-GC live set, retention paths và workload evidence |

## 22. Checklist quyết định thuật toán và phân tích performance

Trước khi so một collector hay đọc một biểu đồ GC, tự hỏi:

1. Object graph gồm bao nhiêu **live bytes**? Có cache/registries cố giữ objects không?
2. Tỷ lệ objects chết ngay sau allocation là bao nhiêu?
3. Collection đang xử lý **toàn heap** hay **young/old selected regions**?
4. **Mark** diễn ra STW, parallel hay concurrent? Dùng barrier model gì?
5. Dead memory được reclaim bằng **sweep/free list**, **evacuation** hay **compact**?
6. Nếu relocate objects, collector tìm forwarding destination và **sửa/đọc references** bằng cách nào?
7. Có **to-space/headroom** đủ cho live objects không?
8. Fragmentation là **free blocks nhỏ**, **humongous regions**, alignment hay allocator metadata?
9. Phần lớn thời gian GC tiêu vào root scanning, reference processing, copy bytes hay compaction?
10. Mục tiêu cần tối ưu **throughput**, **p99 pause latency** hay footprint?
11. JVM/collector release có đúng Java 17 không? Tài liệu và flags có thuộc đúng bản đó không?
12. Kết luận được chứng minh bằng GC logs/JFR/benchmark trên workload thật hay chỉ bằng lý thuyết?

## 23. Câu hỏi tự kiểm tra

**Câu 1:** Một object A có đường đi từ GC root, object B không có. Marking dùng để xác định điều gì?

**Đáp:** A reachable theo roots được xét; B chưa thuộc tập live tương ứng và có thể được reclaim theo reference rules.

**Câu 2:** Sau mark-sweep, tổng trống 10 MiB nhưng không có free span nào lớn hơn 2 MiB. Request 4 MiB có chắc cấp phát thành công trong cùng vùng đó không?

**Không.** Nếu cần 4 MiB contiguous và không có vùng khác/compaction/expansion, external fragmentation gây khó cấp phát.

**Câu 3:** Điểm khác cốt lõi giữa mark-sweep và mark-compact?

Mark-sweep thường để live objects tại chỗ và đưa dead blocks vào allocator; mark-compact gom/move live objects để tạo free space liên tục.

**Câu 4:** Copying collector gặp hai roots cùng trỏ A. Có tạo hai object A mới không?

**Không được tạo hai Java object identity độc lập.** Forwarding/claim logic bảo đảm các reference cùng đến một logical destination.

**Câu 5:** Vì sao copying có thể hiệu quả cho young generation?

Vì trong nhiều workload nhiều objects chết nhanh; chỉ cần evacuate một live subset nhỏ, reclaimed source space nhanh và allocation đích có thể đơn giản.

**Câu 6:** G1 concurrent mark và G1 evacuation có phải cùng một phase?

**Không.** Concurrent marking chủ yếu đánh giá liveness; selected-region evacuation thường thực hiện trong STW collection pauses của G1 Java 17.

**Câu 7:** `IdentityAcrossGc` in toàn bộ `true` có chứng minh parent thật sự đã chuyển địa chỉ?

**Không.** Chương trình chỉ kiểm tra contract Java; GC có thể không chạy hoặc object không move.

**Câu 8:** GC write barrier có thay thế `synchronized` để ngăn data race không?

**Không.** GC barriers giữ collector invariants, Java concurrency cần JMM synchronization phù hợp.

**Câu 9:** ZGC và Shenandoah giúp concurrent relocation mà Java vẫn thấy đúng reference bằng cách nào?

Dùng kết hợp forwarding metadata và barriers/remapping theo thuật toán/phiên bản, thay vì yêu cầu mọi reference slot được eager fix-up trong một global STW pause.

**Câu 10:** Vì sao một Full GC có thể chậm dù phần lớn objects còn sống?

Vì tracing, cập nhật references và di chuyển/compact lượng live bytes lớn vẫn tốn công, nhất là khi thiếu memory headroom/parallelism.

## 24. Tài liệu tham khảo chuẩn

- [JVMS Java SE 17 — Run-Time Data Areas / Heap (`2.5.3`)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): heap và quản lý objects theo đặc tả.
- [Java SE 17 Garbage Collection Tuning Guide — Available Collectors](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html): Serial, Parallel, G1, ZGC và lựa chọn collector.
- [Java SE 17 Garbage Collection Tuning Guide — G1 Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): regions, marking, evacuation, collection sets, compaction fallback, fragmentation.
- [Oracle — Garbage-First Garbage Collector Tuning](https://www.oracle.com/java/technologies/g1gc.html): evacuation, remembered sets và parallel copying theo mô hình G1.
- [JEP 333 — ZGC: A Scalable Low-Latency Garbage Collector](https://openjdk.org/jeps/333): concurrent marking/relocation, colored pointers/load barriers.
- [JEP 189 — Shenandoah: A Low-Pause-Time Garbage Collector](https://openjdk.org/jeps/189): concurrent evacuation và low-pause design.
- [Java SE 17 `Reference` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html), [`WeakReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html), [`SoftReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/SoftReference.html), [`PhantomReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/PhantomReference.html): reference reachability và clearing.
- [Java SE 17 `System.identityHashCode` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#identityHashCode(java.lang.Object)): identity không đồng nhất địa chỉ object.
- [Java SE 17 `jcmd` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html), [`jstat` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): heap diagnostics và GC counters.
- [Java SE 17 `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xlog`, GC/heap flags.

**Ghi nhớ:** **Mark** tìm objects cần giữ; **sweep** đưa dead blocks về vùng trống; **copy** di chuyển live objects sang vùng đích; **compact** gom objects sống để giảm phân mảnh. Không thuật toán nào là toàn bộ GC hiện đại: HotSpot phối hợp marking, reference processing, evacuation, forwarding, barriers và region/heap management theo từng collector. Hãy dựa vào **reachability, live-set size, fragmentation, memory headroom và GC phases thực tế** để giải thích vì sao một lần GC thu hồi hiệu quả hoặc gặp khó khăn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-002">Phân mảnh bộ nhớ và đánh đổi giữa di chuyển đối tượng với chi phí cập nhật tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Phân mảnh bộ nhớ và đánh đổi giữa di chuyển đối tượng với chi phí cập nhật tham chiếu

## Mục tiêu bài học

Một Java heap có thể còn nhiều byte rảnh nhưng vẫn khó phục vụ một yêu cầu cấp phát mới nếu **các khoảng trống không phù hợp với kích thước hoặc chính sách của bộ cấp phát**. Đó là vấn đề **phân mảnh bộ nhớ** (*memory fragmentation*). Garbage collector (GC) có thể giảm phân mảnh bằng cách **di chuyển/thu gọn các đối tượng đang sống**, nhưng khi vị trí của một đối tượng thay đổi, runtime phải bảo đảm **mọi tham chiếu hợp lệ vẫn tìm đúng đối tượng ấy**. Việc sao chép dữ liệu, xác định vị trí mới, sửa hoặc điều hướng tham chiếu và đồng bộ với application threads tạo ra một đánh đổi thực sự về CPU, memory bandwidth, thời gian tạm dừng và mức headroom của heap.

Bài này đặt trọng tâm vào **cơ chế GC và HotSpot**, không đánh đồng Java reference với địa chỉ vật lý bất biến. Sau bài học, bạn có thể:

- Phân biệt **external fragmentation**, **internal fragmentation**, free-space topology, wasted capacity và tổng heap còn rảnh.
- Giải thích **mark–sweep**, **mark–compact**, **copying/evacuation** và cách mỗi chiến lược xử lý khoảng trống.
- Mô tả vì sao di chuyển một object buộc collector phải xử lý **root references, references trong objects và array elements**, đôi khi cả references bên ngoài vùng thu gom.
- Phân biệt **forwarding information**, relocation maps, barriers, remembered sets, card tables và GC-root metadata; không coi chúng là cùng một cơ chế.
- Hiểu tại sao **một object có rất nhiều references** có thể rẻ để copy payload nhưng đắt để duy trì reachability/references, trong khi **object rất lớn có ít references** có thể đắt để copy dù cập nhật tham chiếu ít.
- So sánh những hướng triển khai của **Serial/Parallel, G1, ZGC, Shenandoah** theo phiên bản và collector cụ thể.
- Hiểu trường hợp **G1 humongous objects**, vùng nhớ còn dư nhưng không sử dụng được, evacuation failure và Full GC.
- Dùng Java 17+ chạy mô phỏng compaction và ví dụ chứng minh aliasing/identity vẫn đúng, rồi đọc GC logs mà không dựa vào thời điểm GC không xác định.
- Đo đúng bằng **GC logs, JFR, jcmd, JDK Mission Control**, tránh xem heap usage cao là bằng chứng chắc chắn của fragmentation.

## 1. Từ đâu xuất hiện phân mảnh?

Giả sử một allocator có tám ô bằng nhau. Sau khi nhiều object được tạo rồi trở nên unreachable, sơ đồ giả lập có thể thành:

~~~text
Trước thu gom / sau khi xác định object đã chết:

index:  0   1   2   3   4   5   6   7
        +---+---+---+---+---+---+---+---+
state:  | A | . | B | . | C | . | D | . |
        +---+---+---+---+---+---+---+---+

A, B, C, D: object còn sống
.         : ô nhớ rảnh

Tổng trống: 4 ô
Khoảng trống liền nhau lớn nhất: 1 ô
Yêu cầu: cấp phát object cần 3 ô liền nhau
~~~

Đây là **mô hình khối nhớ giả lập**, không phải cách HotSpot bắt buộc phân bổ mọi object trong heap. Với allocator yêu cầu một vùng liền 3 ô, tổng 4 ô rảnh chưa đủ: **không có khoảng trống đủ dài**. Đó là ví dụ của **external fragmentation**.

Nếu di chuyển B, C, D sát A:

~~~text
Sau compaction:

index:  0   1   2   3   4   5   6   7
        +---+---+---+---+---+---+---+---+
state:  | A | B | C | D | . | . | . | . |
        +---+---+---+---+---+---+---+---+

Tổng trống: 4 ô (không đổi)
Khoảng trống liền nhau lớn nhất: 4 ô
Yêu cầu 3 ô bây giờ có thể đáp ứng
~~~

**Compaction không tự tạo thêm bytes rảnh từ hư không.** Nó **sắp xếp lại live objects** để gộp những khoảng trống thành các vùng thuận lợi cho allocation. Nếu các object được xác định chết từ trước và đã reclaim, tổng free bytes có thể giữ nguyên qua bước di chuyển; khác biệt nằm ở **hình dạng free space**.

### External fragmentation

Là tình trạng free space bị chia thành nhiều đoạn nhỏ hoặc thành các vùng không đáp ứng tiêu chí cấp phát. Trong một heap dùng **regions**, vấn đề còn liên quan đến khả năng tìm đủ contiguous regions, chính sách chọn region và số region bị giữ bởi live/pinned/humongous objects.

### Internal fragmentation

Là phần bytes bị tiêu thụ hoặc để trống **bên trong đơn vị allocation** dù không chứa dữ liệu hữu ích cho ứng dụng. Ví dụ:
- Padding do object alignment.
- Các slot bị dự trữ nhưng chưa dùng trong một block/array capacity.
- Phần dư của region cuối cùng được dành riêng cho một object lớn, không thể dùng cho object khác theo chính sách allocator đó.

**Không đồng nhất** mọi padding với external fragmentation. External là **khoảng trống giữa các allocations khó sử dụng**; internal là **phần dư trong allocation unit hoặc reserved region**.

### “Heap rảnh” có nhiều nghĩa

| Chỉ số | Nó nói gì | Điều chưa thể kết luận |
| --- | --- | --- |
| Committed heap − used heap | Khoảng chênh theo cách VM báo used/committed | Free slots có phù hợp một allocation lớn không? |
| Total free bytes trong một vùng | Tổng không gian chưa dùng | Có đủ contiguous space không? |
| Largest contiguous free block | Khối rảnh lớn nhất theo allocator | Có thể allocate sau GC/expansion hay không? |
| Number of free regions | Số regions hoàn toàn rảnh | Có đủ một dải regions liền cho humongous object? |
| Live-set size | Bao nhiêu memory phải giữ object sống | Bao nhiêu copy/update work cần làm? |
| Heap fragmentation metric của GC cụ thể | Mức rời rạc theo heuristic của collector | Có cùng nghĩa với metric collector khác không? |

Đừng dùng một số `Runtime.freeMemory()` để “chứng minh” heap fragmentation. API báo số liệu ở phạm vi runtime, không thể hiện chính xác free-block topology của mọi collector.

## 2. Tại sao mark–sweep có thể để lại các lỗ?

Cơ chế **mark–sweep** cổ điển:

1. Bắt đầu từ **GC roots** và đánh dấu các objects reachable theo graph.
2. Quét vùng bộ nhớ/metadata cần xử lý.
3. Reclaim các object không còn live.
4. Giữ các live objects tại vị trí cũ.

Khi nhiều object chết nằm xen giữa những object sống, mark–sweep có thể để lại **nhiều free holes**.

~~~text
Trước:  [A][X][B][Y][C][Z][D]   X/Y/Z unreachable
Sweep:  [A][ ][B][ ][C][ ][D]

Live objects vẫn ở vị trí cũ.
Free list hoặc allocator cần quản lý các holes.
~~~

**Ưu điểm có điều kiện:** không phải copy toàn bộ live-object bytes chỉ để compact. Những object không được di chuyển không cần sửa reference vì địa chỉ của chúng giữ nguyên (bỏ qua những xử lý GC khác).

**Đổi lại:** allocator có thể phải dùng free lists, phân loại blocks, coalescing và gặp external fragmentation. Các kỹ thuật chống phân mảnh như gộp adjacent free blocks giúp ích, nhưng **không thể gộp qua một live object nằm giữa** nếu không di chuyển nó.

Không phải GC không di chuyển nào cũng luôn gặp fragmentation nghiêm trọng: allocator có thể dùng size classes, segregated free lists, region management, allocation policies và GC heuristics phù hợp. Cũng không phải một GC có giai đoạn sweeping thì chắc chắn “không compact ở nơi nào”.

## 3. Mark–compact: gộp free space bằng cách di chuyển live objects

**Mark–compact** thường gồm:
1. Xác định objects live.
2. Tính hoặc xác định vị trí đích cho các live objects.
3. Di chuyển object contents/metadata khi cần.
4. Bảo đảm references đang trỏ tới objects đó được đổi/điều hướng để trỏ đúng instance.
5. Có một vùng free space lớn hơn hoặc các vùng rảnh phù hợp sau compact.

~~~text
Before:
  root1 ----> [A @slot0]  [hole]  [B @slot2]  [hole]
                              \____________/
                               phân mảnh

Destination planning:
  A: slot0 -> slot0
  B: slot2 -> slot1

After:
  root1 ----> [A @slot0][B @slot1][free][free]
                           ^
                           |
                   old refs to B được xử lý
~~~

**Lợi ích:**
- Có thể giảm external fragmentation, giúp phục vụ yêu cầu allocation lớn.
- Sau compact, một dạng bump-pointer allocation có thể hiệu quả.
- Object locality có thể thay đổi theo hướng tích cực nếu allocator bố trí tốt, nhưng không hứa tất cả objects liên quan nằm cạnh nhau.

**Chi phí:**
- Đọc/ghi bytes của live objects cần di chuyển, tiêu thụ CPU và memory bandwidth.
- Tìm/ghi vị trí mới hoặc quản lý forwarding metadata.
- Xử lý các references tới objects đã di chuyển.
- Phối hợp với application threads, có thể cần **stop-the-world (STW)** hoặc barriers/handshakes đối với collector concurrent.
- Có thể cần vùng đích hoặc metadata phụ; thuật toán in-place và copying có chi phí memory headroom khác nhau.

**Không có một “số lần cập nhật references = số objects di chuyển” bắt buộc.** Một object được nhiều root/fields/array slots tham chiếu có thể có hàng triệu references. Collector phải bảo đảm mọi reference hợp lệ được giải quyết đúng, nhưng cách làm có thể là cập nhật từng slot, remap khi đọc, remembered sets hoặc cơ chế khác tùy GC.

## 4. Copying collection và evacuation

Một collector có thể chọn **copying/evacuation**: chuyển live objects từ các vùng nguồn sang các vùng đích khác, rồi **reclaim toàn bộ vùng nguồn** khi điều kiện an toàn thỏa mãn.

~~~text
Source region (CSet):             Destination region:

[A live][B dead][C live]  ==>    [A'][C'][free ...]
           |                        ^
           |                        |
           +-- nếu references trỏ A/C
               phải được chuyển hướng tới A'/C'
~~~

Lợi thế của evacuation:
- Để lại vùng nguồn trống hoàn toàn, phù hợp **region reuse**.
- Tránh giữ nhiều holes trong chính vùng nguồn được evacuate thành công.
- Có thể thu gom từng tập regions, thay vì luôn compact toàn heap.

Chi phí:
- **Copy live bytes** của các regions được chọn.
- **Destination space/headroom** để chứa live objects khi chúng đang được di chuyển.
- **Reference fixing/barrier work**.
- Có thể có **evacuation failure** nếu không đủ vùng đích hoặc bị giới hạn pinning/collector-specific constraints.
- Nếu selection kém và region rất nhiều live bytes, chi phí copy lớn nhưng dung lượng reclaim nhỏ.

### Tại sao “garbage-first” chú ý tỷ lệ garbage?

Giả sử hai regions có cùng size:
- Region P: chỉ 10% live, 90% garbage.
- Region Q: 95% live, 5% garbage.

Nếu evacuate P, collector phải copy ít live bytes và reclaim nhiều memory. Nếu evacuate Q, phải copy rất nhiều bytes để lấy lại ít free space. Đây là một tiêu chí trực giác khiến region-based collectors ưu tiên vùng **nhiều garbage và ít chi phí kết nối**. Quyết định thực tế vẫn dựa nhiều heuristic khác như reference scanning, pause targets và remembered-set cost.

## 5. Di chuyển object không được làm thay đổi identity

Trong Java source, một biến reference **không phải địa chỉ heap bất biến do ứng dụng quản lý**. Ví dụ:

~~~java
Person p = new Person();
Person alias = p;
System.out.println(p == alias); // true
~~~

Đây là đoạn minh họa cần class `Person` phù hợp. Nếu GC di chuyển object mà p và alias đang tham chiếu, Java vẫn phải quan sát **`p == alias` là true** sau khi collection, miễn không có phép gán lại biến làm thay đổi references.

Collector có thể đổi nơi lưu object vật lý từ “old address” sang “new address”, nhưng **logical object identity phải được bảo toàn**:
- Các reference comparisons/`==` cho cùng object vẫn đúng.
- `Object.equals` nếu không override vẫn có semantics identity.
- `System.identityHashCode(object)` phải nhất quán trong lifetime của object theo contract, không đơn giản “đổi vì địa chỉ di chuyển”.
- Monitor/lock state và object metadata phải giữ đúng semantics; cách bảo toàn thông tin phụ thuộc HotSpot/collector.
- Field contents phải được giữ đúng theo Java Memory Model và các quy tắc GC.

**Không lấy `System.identityHashCode` làm địa chỉ RAM**, cũng không dùng `toString()` mặc định để đo vị trí object. Hash code không phải pointer; có thể trùng giữa objects khác nhau.

## 6. Những tham chiếu nào bị ảnh hưởng khi object di chuyển?

Một object đang được tham chiếu có thể có những đường đi đến từ:

- **Local variables/operand stack** trong các JVM frames của threads còn hoạt động.
- **Instance reference fields** của những objects khác.
- **Static reference fields** của class/interface runtime.
- **Reference-array elements**.
- **JNI/native handles** hoặc các roots/runtime references được VM quản lý.
- Các cấu trúc nội bộ GC/VM, code metadata và các loại references có contract riêng.

~~~text
Root: local r -----------+
                         |
Root: static cached -----+----> [Customer OLD]
                         |         |
Object A.ref ------------+         +--> [Address]
                         |
Array refs[19] ----------+

Di chuyển Customer OLD -> Customer NEW:

Root r ------------------+
                         |
static cached -----------+----> [Customer NEW]
                         |         |
Object A.ref ------------+         +--> [Address]
                         |
Array refs[19] ----------+
~~~

**Sơ đồ mô tả kết quả logic**, không buộc HotSpot cập nhật từng root/field ngay cùng một thời điểm. Một collector concurrent có thể tạm dùng forwarding metadata, load barriers, remapping và cơ chế đảm bảo mọi truy cập của mutator nhìn thấy object chính xác khi relocation đang diễn ra.

### Giá của reference update phụ thuộc graph

Nếu object X chỉ được tham chiếu bởi một reference trong root set, số logical references cần xử lý có thể nhỏ. Nếu X là “hot shared object” xuất hiện trong một triệu elements của arrays/collections, có thể có rất nhiều edges tới X. Collector vẫn phải bảo toàn aliasing sau relocation; có thể trả chi phí quét/cập nhật/remap tương ứng.

Do đó **số object di chuyển**, **bytes copy**, **số references/edges liên quan**, **cách references phân bố giữa regions** và **số roots** là những biến số khác nhau, ảnh hưởng khác nhau tới CPU và pause.

## 7. Forwarding: biết object đã chuyển từ đâu tới đâu

Để chuyển hướng references, một moving collector thường cần cách tìm **vị trí mới** từ **vị trí cũ**. Có nhiều chiến lược:
- Forwarding information được ghi hoặc mã hóa ở nơi thích hợp.
- Bảng ánh xạ/relocation metadata theo phạm vi collector.
- Tham chiếu có metadata hoặc dùng read/load barriers để chuyển đến đối tượng mới.
- Cập nhật reference slots vào lúc quét hoặc vào thời điểm mutator đọc reference.

Sơ đồ đơn giản:

~~~text
Old location -> New location

0x1000 -> 0x8000  (A moved)
0x2000 -> 0x8020  (B moved)
0x3000 -> 0x9000  (C moved)

reference slot holding old B
    |
    +--> forwarding/relocation info
                 |
                 v
         valid reference to new B
~~~

Đây là **địa chỉ giả lập**, không phải địa chỉ VM thật. Reference representation của HotSpot có thể dùng compressed oops hoặc metadata riêng; không dựa vào một pointer width duy nhất.

### Vì sao không thể chỉ copy object rồi bỏ qua references?

Nếu chỉ copy B sang một vị trí mới mà các references còn chỉ tới memory location cũ, những references đó có thể bị dangling hoặc chỉ vào storage đã được tái sử dụng. JVM cần một giao thức GC để mọi reference hợp lệ tiếp tục truy cập đúng B.

Điều đó **không đồng nghĩa API Java cho phép bạn thấy từng reference đang được sửa**. Moving GC là việc VM quản lý; source Java tiếp tục làm việc với object identity, không phải raw heap addresses.

### Forwarding chưa đủ cho concurrent collection

Khi application threads (mutators) vẫn chạy trong lúc collector di chuyển objects, có các race có thể xảy ra:
- Mutator đọc reference cũ ngay sau khi collector chuyển object.
- Mutator ghi field vào object đang hoặc đã di chuyển.
- Một root/reference được cập nhật đồng thời với GC.
- Reference nội bộ từ vùng không thu gom trỏ đến vùng đang evacuate.

Collector concurrent phải phối hợp bằng **barriers**, synchronization/handshakes, forwarding/remapping và ordering thích hợp. Chỉ tạo mapping old→new không đủ để chứng minh algorithm thread-safe.

## 8. Roots, oop maps, remembered sets và card tables khác nhau

Các thuật ngữ dễ bị nhập nhằng:

| Khái niệm | Mục đích chính | Không phải |
| --- | --- | --- |
| **GC roots** | Các điểm bắt đầu reachability như thread references, static roots, handles... | Danh sách mọi reference field trong heap. |
| **Oop maps/stack maps nội bộ** | Giúp VM nhận biết vị trí references trong compiled/interpreted frames ở những safepoints thích hợp | Java API công khai và cố định cho mọi collector. |
| **Forwarding information** | Xác định hoặc dẫn đến vị trí mới của object đã di chuyển | Tập hợp tất cả inbound reference edges. |
| **Card table** | Theo dõi các phần bộ nhớ mà writes có thể làm thay đổi quan hệ references | Danh sách chính xác từng reference riêng lẻ. |
| **Remembered set** | Tập vị trí/region có thể chứa references từ ngoài tới một vùng/các vùng cần thu gom | Công cụ tự sửa mọi references mà không cần scan. |
| **Read/load barrier** | Xử lý một số điều kiện GC khi mutator đọc reference, có thể liên quan relocation/remapping | Luôn có trên mọi collector, mọi JVM. |
| **Write barrier** | Hỗ trợ GC theo dõi updates khi mutator ghi refs, ví dụ marking hoặc region cross-refs | Không phải keyword `volatile` trong Java source. |

**Oop** là thuật ngữ HotSpot cho *ordinary object pointer* hoặc biểu diễn object reference. Oop maps phục vụ VM biết nơi nào cần xử lý như object refs khi quét stack, không đồng nghĩa source variable chắc chắn nằm ở một stack address cụ thể nếu JIT đã tối ưu.

### Remembered set giảm quét toàn heap như thế nào?

Giả sử G1 đang evacuate một **collection set (CSet)** gồm một số regions. References đến objects trong CSet có thể nằm ở:
- Roots/frames/static state.
- Chính các objects thuộc CSet.
- Các objects ở **regions ngoài CSet**.

Nếu muốn biết incoming references từ ngoài mà phải quét mọi object của toàn heap ở mỗi young GC, chi phí rất lớn. **Remembered sets/card marking** giúp giới hạn những vị trí cần xem, nhưng vẫn cần thực hiện work để định vị các slots/relationships thực sự.

G1 dùng **post-write barrier** để theo dõi cross-region changes và duy trì remembered-set-related metadata theo thuật toán collector. Đây là **GC bookkeeping**, không phải application synchronization. Chi phí của nó tồn tại cả khi không diễn ra GC pause.

Không nói “remembered set chứa mọi vị trí chính xác và luôn update ref tự động”: các entries có thể là **coarse-grained card locations** và yêu cầu scan để tìm references cụ thể.

## 9. Ví dụ Java 17+: mô phỏng compaction và cập nhật tham chiếu

Để có kết quả tái lập được mà **không phụ thuộc GC timing**, ta tạo mô hình nhỏ:
- Một array `slots` mô phỏng tám ô heap; -1 là slot trống.
- `edges[i]` mô phỏng một reference từ object tại ô i tới ô khác.
- `roots` mô phỏng những vị trí root đang trỏ tới objects.
- `forward[oldIndex] = newIndex` là relocation table.
- Sau compact, phải sửa cả root indices và edges để giữ đúng đồ thị.

**Đây chỉ là mô hình thuật toán bằng Java**, không phải cách JVM phơi bày/sửa raw memory addresses.

Tạo file **`CompactionSimulation.java`**:

~~~java
import java.util.Arrays;

public class CompactionSimulation {
    static int largestRun(int[] slots) {
        int best = 0;
        int current = 0;
        for (int slot : slots) {
            if (slot == -1) {
                current++;
                best = Math.max(best, current);
            } else {
                current = 0;
            }
        }
        return best;
    }

    public static void main(String[] args) {
        final int empty = -1;

        // Bốn live objects tại các index 0, 2, 4, 6.
        int[] slots = {10, empty, 20, empty,
                       30, empty, 40, empty};

        // Object tại 0 -> 4; object tại 2 -> 6.
        int[] edges = {4, empty, 6, empty,
                       empty, empty, empty, empty};

        // Hai root references, trỏ index 0 và index 4.
        int[] roots = {0, 4};

        System.out.println("before=" + Arrays.toString(slots));
        System.out.println("largestFreeBefore=" + largestRun(slots));

        int[] forward = new int[slots.length];
        Arrays.fill(forward, empty);

        int[] packed = new int[slots.length];
        int[] newEdges = new int[slots.length];
        Arrays.fill(packed, empty);
        Arrays.fill(newEdges, empty);

        int liveCount = 0;
        int movedCount = 0;

        // Bước 1: tính vị trí mới và chuyển dữ liệu.
        for (int oldIndex = 0; oldIndex < slots.length; oldIndex++) {
            if (slots[oldIndex] != empty) {
                forward[oldIndex] = liveCount;
                packed[liveCount] = slots[oldIndex];

                if (oldIndex != liveCount) {
                    movedCount++;
                }
                liveCount++;
            }
        }

        // Bước 2: sửa root references.
        for (int i = 0; i < roots.length; i++) {
            roots[i] = forward[roots[i]];
        }

        // Bước 3: sửa links bên trong object được chuyển.
        for (int oldIndex = 0; oldIndex < slots.length; oldIndex++) {
            if (slots[oldIndex] != empty) {
                int newIndex = forward[oldIndex];
                int oldTarget = edges[oldIndex];
                newEdges[newIndex] =
                        oldTarget == empty ? empty : forward[oldTarget];
            }
        }

        System.out.println("after=" + Arrays.toString(packed));
        System.out.println("roots=" + Arrays.toString(roots));
        System.out.println("links=" + Arrays.toString(
                Arrays.copyOf(newEdges, liveCount)));
        System.out.println("moved=" + movedCount);
        System.out.println("largestFreeAfter=" + largestRun(packed));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 CompactionSimulation.java
java CompactionSimulation
~~~

**Stdout chính xác:**

~~~text
before=[10, -1, 20, -1, 30, -1, 40, -1]
largestFreeBefore=1
after=[10, 20, 30, 40, -1, -1, -1, -1]
roots=[0, 2]
links=[2, 3, -1, -1]
moved=3
largestFreeAfter=4
~~~

**Giải thích từng phần:**

1. Ban đầu có 4 empty slots nhưng largest contiguous free run chỉ dài 1.
2. `forward` nhận mappings 0→0, 2→1, 4→2, 6→3. Có **3 objects đổi vị trí**.
3. `roots` từ [0,4] thành **[0,2]** để root thứ hai vẫn trỏ đến object ID 30, không bị nhầm với object khác.
4. Edge của object ID 10 trước đây trỏ old index 4, nay phải trỏ **new index 2**. Edge của ID 20 trỏ old index 6, nay thành **new index 3**.
5. `links=[2,3,-1,-1]` cho thấy **reference updates** độc lập với việc copy dữ liệu.
6. Sau compact vẫn có 4 free slots, nhưng contiguous run dài 4, giúp phục vụ allocation mới cần ba ô.

**Cố ý không mô phỏng** GC roots thực, object identity metadata, concurrency/barriers, JNI handles, remembered sets, dynamic object sizes hoặc GC heuristics. Chương trình chỉ minh họa **logic old→new mapping và cập nhật edges**.

### “Quên update reference” là lỗi gì trong mô hình?

Nếu ta chuyển ID 30 từ index 4 sang index 2 nhưng giữ root value 4, root sẽ nhìn vào **slot trống** sau compact. Nếu giữ edge từ ID 10 tới index 4, đồ thị object logic đã bị hỏng. GC thật không được để Java program quan sát tình trạng sai này sau một collection hoàn tất.

## 10. Ví dụ Java 17+: identity không đổi khi GC hoạt động

Ứng dụng Java **không cần và không được** tự sửa reference values khi HotSpot di chuyển object. Một ví dụ kiểm tra semantics xác định:

Tạo file **`IdentityInvariantDemo.java`**:

~~~java
public class IdentityInvariantDemo {
    static final class Node {
        final String label;
        Node next;

        Node(String label) {
            this.label = label;
        }
    }

    public static void main(String[] args) {
        Node first = new Node("first");
        Node second = new Node("second");

        first.next = second;
        Node alias = first;

        // Không gọi System.gc(): bài kiểm tra không dựa GC timing.
        System.out.println("sameRoot=" + (first == alias));
        System.out.println("sameTarget=" + (first.next == second));

        first = null;
        System.out.println("aliasLabel=" + alias.label);
        System.out.println("linkedLabel=" + alias.next.label);
        System.out.println("sameTargetAfter=" + (alias.next == second));
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 IdentityInvariantDemo.java
java IdentityInvariantDemo
~~~

**Stdout chính xác:**

~~~text
sameRoot=true
sameTarget=true
aliasLabel=first
linkedLabel=second
sameTargetAfter=true
~~~

Lưu ý:
- Dòng `first = null` chỉ thay đổi **một local reference**, không làm object first biến mất khi `alias` vẫn trỏ tới nó.
- Ngay cả khi một GC di chuyển objects ở thời điểm thích hợp, tính đúng đắn của references và comparisons phải được bảo toàn.
- Chạy chương trình này **không chứng minh đã có một GC hoặc một relocation xảy ra**. Nó chỉ kiểm tra semantics Java mà GC phải duy trì.

Không dùng `System.gc()` như bảo đảm sẽ có compact hay một object sẽ bị chuyển chỗ. Lời gọi này không hứa một GC cycle cụ thể, càng không hứa di chuyển một instance riêng.

## 11. Chi phí di chuyển: không chỉ số objects

Các chiều chi phí thường cần xét riêng:

| Đại lượng | Ảnh hưởng chủ yếu |
| --- | --- |
| **Bytes của live objects cần copy** | Memory bandwidth, CPU cycles, cache traffic |
| **Số object cần relocate** | Forwarding/relocation bookkeeping, header/metadata handling |
| **Số reference slots/root slots cần xử lý** | Scan, reference remapping, update work |
| **Số cross-region edges** | Remembered-set/card scanning hoặc overhead tương đương |
| **Tỷ lệ live trong vùng chọn** | Dung lượng reclaim trên mỗi byte copy |
| **GC-headroom/destination capacity** | Khả năng evacuation thành công và áp lực bộ nhớ |
| **Mutator write/read rate** | Barrier overhead và độ phức tạp khi GC chạy đồng thời |
| **Pinning/humongous regions** | Hạn chế khả năng di chuyển/reclaim một số vùng |
| **Pause-time/CPU budget** | Tập regions được chọn; mức concurrent work chấp nhận được |

### Hai tình huống đối lập

**Tình huống A: object lớn, ít inbound references.** Một `byte[]` nhiều MB có thể chỉ được một cache entry tham chiếu. Copy toàn bộ payload có chi phí memory bandwidth lớn; cập nhật một reference logic có thể tương đối rẻ.

**Tình huống B: object nhỏ, rất nhiều inbound references.** Một object vài chục bytes được hàng trăm nghìn array slots trỏ tới. Copy object payload rất rẻ, nhưng GC phải đảm bảo nhiều references vẫn đúng. Những thuật toán dùng remapping/barriers có thể trả chi phí theo cách khác so với cập nhật tất cả slots tại STW.

**Không tự khẳng định O(R) exact cho mọi GC:** một thuật toán trực tiếp quét và sửa tất cả inbound slots có work tỷ lệ với số slot xét; collector khác có thể lazy-fix và phân bổ chi phí qua mutator reads. Complexity và constant factors phụ thuộc implementation.

### Chi phí copy và locality

Di chuyển objects có thể cải thiện locality trong một số trường hợp nhưng cũng có thể:
- Làm tăng cache traffic và áp lực lên memory bandwidth.
- Gây dirtying nhiều memory pages.
- Gây page faults/memory commit nếu cần destination pages.
- Làm cache lines của dữ liệu cũ mất giá trị.
- Đòi hỏi synchronization/safepoints để các threads không thấy object sai trạng thái.

Không mặc định “compact luôn tăng tốc truy cập”: việc nhóm objects mới tốt đến đâu còn tùy collector, đường truy cập và workload.

## 12. G1: chọn regions để evacuation và compact từng phần

**G1 (Garbage-First)** là collector dạng **region-based, generational** trong HotSpot. Heap được chia thành các **regions**; eden/survivor/old là những tập regions theo vai trò, không bắt buộc mỗi generation là một dải địa chỉ vật lý liên tục.

Trong các young/mixed collections, G1 chọn **Collection Set (CSet)** gồm những regions mục tiêu rồi:
1. Xác định objects live phù hợp.
2. Copy/evacuate live objects từ CSet sang các destination regions thích hợp.
3. Xử lý references từ roots và ngoài CSet, sử dụng remembered-set/card tracking và mechanisms liên quan.
4. Sau evacuation thành công, **reclaim các source regions** để tái dùng.

Việc di chuyển live objects giúp compact vùng thu gom theo từng đợt. G1 cố cân bằng **dung lượng reclaim** và **pause-time target**, nhưng target chỉ là mục tiêu mềm, **không phải deadline cứng**.

### Một region nhiều live objects không phải ứng viên lý tưởng

Evacuate region 95% live đòi copy gần như cả region mà chỉ thu hồi ít garbage. Do đó collector dùng live fraction và connectivity/work prediction để đánh giá collection efficiency. Nếu heap luôn có rất nhiều long-lived objects, copy chúng trong mỗi lần collection cũng tốn kém.

### Remembered set là phần đánh đổi

G1 tránh quét toàn bộ heap khi collect một số regions, nhưng phải trả chi phí **duy trì và quét remembered sets**. Write barriers và card refinement có thể tăng CPU overhead ở application/GC threads. Một workload có nhiều cross-region references hoặc mutation dày đặc có thể làm tăng loại chi phí này.

### G1 evacuation failure

Khi destination space không đủ cho các objects cần chuyển hoặc có ràng buộc collector-specific, evacuation có thể thất bại. G1 có những đường xử lý failure và có thể dẫn đến **Full GC** với compaction đắt tiền nếu áp lực heap nghiêm trọng.

Đừng diễn giải mọi log có chữ “evacuation” là lỗi: **evacuation là hoạt động bình thường của G1**, còn `Evacuation Failure` mới là tín hiệu đáng điều tra.

## 13. Humongous objects: trường hợp phân mảnh đặc biệt của G1

Theo tài liệu G1, một object cỡ **bằng hoặc lớn hơn một nửa region** được xem là **humongous**. Nó thường được cấp vào một chuỗi **contiguous humongous regions** ở old generation, không di chuyển theo những evacuation pauses thông thường.

Ví dụ **minh họa** với region size 2 MiB:
- Threshold humongous khoảng 1 MiB (kích thước object thực tế đã gồm header/layout theo VM).
- Một object cỡ 1,1 MiB có thể chiếm trọn một region 2 MiB, làm phần dư gần 0,9 MiB khó dùng cho allocation thông thường trong region đó.
- Một object cỡ 2,1 MiB có thể cần hai contiguous regions, tổng 4 MiB; phần dư region cuối không được dùng như free region độc lập.
- Nhiều large arrays sống/chết xen kẽ có thể làm việc tìm contiguous humongous regions khó hơn dù heap còn dung lượng rảnh theo một số metric.

**Các con số chỉ là tính nhẩm minh họa**: kích thước object thực tế có header/alignment, vùng heap và policy có thể khác. Không thử biến ví dụ thành một công thức OOME cố định.

G1 thường chỉ reclaim humongous objects khi kết luận chúng đã chết theo cơ chế phù hợp; một số arrays có thể được **eager reclaim** theo heuristic. Humongous objects thường **không được copy trong evacuation thông thường**; di chuyển chúng là đường hiếm/last resort trong một số collection modes/versions.

**Trade-off:** không copy các arrays khổng lồ giúp giảm chi phí sao chép, nhưng có thể tăng **wasted region tail** và hạn chế compaction, dẫn đến áp lực free contiguous regions.

### Không nhầm với “Java heap còn 2 GiB trống, nên object 1 GiB chắc chắn cấp phát được”

Thành công còn phụ thuộc:
- `-Xmx`, committed/free heap thực tế.
- Số contiguous regions khả dụng, region size.
- Collector policy cho large allocations.
- Live-set occupancy, GC timing và native/OS constraints thích hợp.
- Memory headroom cho cả application allocations và GC work.

Một collector khác có thể xử lý very large objects khác G1; đừng áp quy tắc humongous của G1 cho mọi GC.

## 14. So sánh các hướng GC trong HotSpot

| Collector / thuật toán | Chiến lược liên quan di chuyển | Điểm mạnh | Chi phí/rủi ro |
| --- | --- | --- | --- |
| **Mark–sweep cổ điển** | Reclaim dead objects, có thể giữ live objects nguyên chỗ | Ít live-data copy | External fragmentation, free-list management |
| **Mark–compact** | Dồn live objects, sửa references | Gộp free space, giúp future allocations | Pause/copy/update work và metadata |
| **Copying / semi-space** | Copy live objects sang destination | Nguồn sau copy trở thành vùng trống lớn | Cần destination memory, copy live bytes |
| **Serial GC** | Young collection copying; old collection thường compact theo collector/version | Đơn giản, phù hợp workload nhỏ một số trường hợp | STW pauses, hạn chế parallelism |
| **Parallel GC** | Parallel young copying và old compaction ở những giai đoạn thích hợp | Ưu tiên throughput trên nhiều cores | Có STW và copy/update work |
| **G1** | Region evacuation/partial compaction, remembered sets | Balance throughput và pause-time, incremental regions | RSet/barrier overhead, humongous fragmentation, evacuation failure |
| **ZGC** | Concurrent marking/relocation với metadata/barriers theo phiên bản | Low pause targets trên heaps lớn | Concurrent CPU, barriers, allocation headroom, throughput trade-off |
| **Shenandoah** | Concurrent evacuation/compaction với barriers tùy phiên bản | Giảm lượng GC work trong STW | Concurrent CPU/bandwidth, barrier overhead, availability tùy OpenJDK build |

**Chú ý phiên bản:** không có một triển khai cố định trên mọi Java 17/21/26. Với ZGC, chi tiết chế độ generational và cách barriers/metadata thay đổi qua các bản JDK; với Shenandoah, tính khả dụng/flags thay đổi theo JDK distribution và release. Tránh nói “mọi collector concurrent đều không pause” hoặc “G1 không bao giờ di chuyển humongous object”.

### Stop-the-world và concurrent relocation

**STW moving collector** có thể dừng mutator threads khi cập nhật references để tránh races, đổi lại pause có thể tăng cùng live/edge workload. **Concurrent moving collector** cho phép nhiều công việc di chuyển diễn ra đồng thời với mutators, nhưng cần trả chi phí barriers/coordination và headroom, đồng thời vẫn có thể có những pauses ngắn.

Điều này không có nghĩa concurrent luôn nhanh hơn:
- **Throughput-oriented** workload có thể hưởng lợi từ những pause dài hơn nhưng tổng overhead thấp.
- **Latency-sensitive** service có thể ưu tiên concurrent collector dù cần CPU/memory hơn.
- Khi hệ thống đã gần 100% CPU hoặc heap quá chật, concurrent GC khó theo kịp tốc độ allocation.
- Tối ưu phải đo trên workload và SLA thật.

## 15. Pinning và những vùng không thể di chuyển ngay

Một số điều kiện runtime/native interoperability có thể khiến collector **không thể tự do di chuyển** một object hoặc vùng tại mọi thời điểm. Ví dụ các tình huống liên quan **JNI critical access** và pinning có thể tạo ràng buộc đến một array/object đang được native code truy cập.

Hậu quả tùy collector:
- Giữ object tại chỗ trong một collection phase.
- Bỏ qua hoặc trì hoãn evacuation đối với vùng liên quan.
- Phải xử lý references/memory theo con đường chậm khác.
- Khiến một số vùng ít khả năng trở thành free region ngay.

**Không khẳng định mọi JNI reference đều pin object**: JNI handles thường được VM quản lý, còn pinning cụ thể tùy API, collector và phiên bản. Chẳng hạn `GetPrimitiveArrayCritical` có contract đặc biệt; cách HotSpot thực hiện cần xét phiên bản/GC.

Không dùng địa chỉ của Java object như một pointer ổn định cho native code mà không theo đúng JNI/Panama/Memory API contract. Java references không phải một ABI thô cho phép giữ physical heap address qua GC tùy ý.

## 16. Phân mảnh, OOME và sự khác biệt với memory leak

Phân mảnh và leak có thể có triệu chứng gần nhau nhưng nguyên nhân khác:

| Hiện tượng | Khả năng cần kiểm tra |
| --- | --- |
| Live heap rất cao, nhiều objects vẫn reachable | Retention/leak hoặc dữ liệu hợp lệ lớn |
| Heap có free bytes nhưng một allocation lớn vẫn khó | Fragmentation, contiguous-region requirement, humongous allocation policy |
| GC liên tục và Full GC thường xuyên | Live-set cao, allocation rate cao, insufficient headroom, evacuation failure |
| Memory OS/RSS không giảm sau GC | Heap vẫn committed, native memory, collector uncommit policy; không đồng nghĩa leak |
| Pauses tăng khi live ratio cao | Copy/scan/reference update work hoặc collector heuristics khác |
| GC logs có humongous regions nhiều | Large-object pressure, wasted tail, allocation/fragmentation risk |
| Một object lớn giữ lại nhiều graph | Retained heap, không nhất thiết là external fragmentation |

**Memory leak (về mặt Java)** thường là tình huống references ngoài ý muốn giữ objects vẫn reachable. **Fragmentation** là vấn đề tổ chức free space hoặc internal waste, kể cả khi một số objects đã thực sự chết/reclaim.

Tăng `-Xmx` có thể giúp có thêm headroom, nhưng không nên xem đó là cách duy nhất hoặc luôn đúng. Nếu dữ liệu còn live quá lớn so với heap limit, compaction không giải quyết được vấn đề; muốn giảm live-set phải đổi ownership/lifetime/data representation hoặc heap budget.

## 17. Đo đúng bằng GC logs, JFR và công cụ HotSpot

Hãy bắt đầu bằng **kết quả đo chứ không bằng giả định phân mảnh**. Trên HotSpot Java 17+, có thể bật Unified GC Logging:

~~~bash
java -Xms256m -Xmx512m -Xlog:gc*:file=gc.log:time,uptime,level,tags CompactionSimulation
~~~

Lệnh minh họa cho ví dụ Java ở mục 9 sau khi đã biên dịch, chạy bằng classpath mặc định trong thư mục hiện hành. File `gc.log` và số liệu cụ thể phụ thuộc JVM/OS/heap/tải, **không có một output GC log cố định**. Một bài mô phỏng chỉ cấp phát ít memory có thể **không tạo GC event nào**, hoàn toàn bình thường.

### Những dấu hiệu trong G1 GC logs

| Nội dung log | Cách diễn giải thận trọng |
| --- | --- |
| `Pause Young (Normal) (G1 Evacuation Pause)` | Young evacuation hoạt động bình thường. |
| `Pause Young (Mixed)` | G1 có thể collect thêm old regions đã chọn. |
| `Evacuation Failure` | Collector gặp ràng buộc khi evacuate, cần xem headroom/pinning/collector-specific diagnostics. |
| `Pause Full (G1 Compaction Pause)` | Full heap compaction STW, thường cần điều tra nếu ảnh hưởng SLA. |
| Humongous region counters | Mức large-object pressure và occupancy theo log, không phải bằng chứng duy nhất của fragmentation. |
| Region/heap usage trước-sau GC | Gợi ý live-set và reclaim efficiency, không hiển thị toàn bộ object graph. |

**Tên tags và message** có thể thay đổi theo JDK/GC flags; không viết parser production dựa trên một đoạn log không được đặc tả ổn định, trừ khi kiểm soát build và có test.

### jcmd / JFR / JMC

Những lệnh ví dụ trên HotSpot hỗ trợ, thay `PID` bằng process ID thật:

~~~bash
jcmd PID GC.heap_info
jcmd PID GC.class_histogram
jcmd PID JFR.start name=GCStudy settings=profile duration=60s filename=gc-study.jfr
jcmd PID JFR.check
~~~

- `GC.heap_info`: thông tin heap, không đảm bảo cung cấp một “fragmentation percentage” chung.
- `GC.class_histogram`: đếm classes/instances/bytes theo một thời điểm, không tự xác định free-block topology.
- **Java Flight Recorder (JFR)**: events về GC pauses, allocations và các hoạt động VM khi được bật theo cấu hình.
- **JDK Mission Control**: phân tích recordings phù hợp để tìm GC/heap/CPU hotspots.
- **Heap dump + dominator tree**: giúp tìm objects còn reachable và retainers, đặc biệt khi nghi leak hơn là fragmentation.

**Cảnh báo:** histogram, heap dump hoặc cấu hình recording có thể ảnh hưởng tải; heap dump chứa dữ liệu nhạy cảm của ứng dụng. Chỉ thu thập khi được phép và bảo vệ files.

### Ví dụ về trình tự phân tích sự cố

1. Xác định collector thật (G1/ZGC/Parallel/...) và JDK build.
2. Ghi `-Xms`, `-Xmx`, heap usage, allocation rate, live set, GC pause distribution.
3. Với G1, quan sát humongous regions, collection set, evacuation failure, Full GC và young/mixed cycles.
4. Nếu live set gần `-Xmx`, ưu tiên kiểm tra **retention** và heap headroom trước khi gọi là fragmentation.
5. Nếu phân mảnh/humongous nghiêm trọng, kiểm tra **kích thước large arrays**, ring buffers/caches và allocation size distribution.
6. Khi thử tùy chọn tuning, lưu baseline và so **throughput, p95/p99 latency, CPU GC overhead, RSS và OOME risk**.
7. Không thay nhiều JVM flags đồng thời nếu muốn biết thay đổi nào hiệu quả.

## 18. Những thước đo trade-off cần so sánh

Một collector hoặc cấu hình không chỉ có “GC pause time”. Cần nhìn đa mục tiêu:

| Mục tiêu | Điều có thể cải thiện | Đổi lại có thể phát sinh |
| --- | --- | --- |
| Ít phân mảnh | Copy/compact thường xuyên hơn | CPU/bandwidth, reference update work |
| Pauses ngắn | Concurrent relocation, chọn regions nhỏ | Barrier overhead, headroom, tổng CPU cao |
| Throughput cao | Ít barriers hơn, batch STW công việc | Pause tail lớn hơn |
| Heap nhỏ | Hạn chế headroom, giảm committed memory | Evacuation failures, allocation stalls, GC frequency |
| Cấp phát nhanh | TLAB/bump pointer, vùng rảnh lớn | Reserve/copy/GC overhead về sau |
| Giảm large object pressure | Reuse/batching/chunking phù hợp | Code complexity, dữ liệu có thể phải copy theo kiểu khác |
| Ít retention | Hạn chế root references/long-lived caches | Cache miss/application compute tăng |
| Tăng locality | Compact hoặc lưu dữ liệu liên tiếp | Copy cost và đổi representation |

**Không đánh đồng throughput với latency.** Một service có thể đạt throughput cao nhưng có những STW pauses dài gây p99 latency tệ. Một collector ít pause có thể tiêu tốn nhiều CPU và headroom hơn, làm throughput peak thấp hơn. Quyết định phải căn theo yêu cầu vận hành.

## 19. Ngộ nhận thường gặp

| Ngộ nhận | Giải thích đúng |
| --- | --- |
| “Tổng free bytes đủ thì allocation lớn chắc chắn thành công.” | Có thể cần một dải free space hoặc contiguous regions đủ lớn. |
| “GC thu gom objects chết sẽ tự động loại hết phân mảnh.” | Sweep reclaim khác compaction; collector policy có thể để lại holes/waste. |
| “Compaction làm tăng tổng bytes rảnh.” | Chủ yếu thay **hình dạng free space**; reclaim garbage là công việc liên quan nhưng khác. |
| “Di chuyển object chỉ cần copy bytes.” | References/roots/barriers/metadata phải giữ semantic integrity. |
| “Một object được move thì chỉ có một reference cần update.” | Có thể có nhiều aliases và inbound edges từ nhiều roots/fields/arrays. |
| “Mọi reference cập nhật bằng quét toàn heap STW.” | Tùy GC; có remembered sets, forwarding, barriers, concurrent remapping. |
| “G1 chỉ là mark–sweep không di chuyển.” | G1 dùng evacuation/copying live objects ở selected regions. |
| “G1 luôn di chuyển humongous objects như young objects.” | Humongous dùng contiguous regions và thường không được evacuate thông thường. |
| “G1 Evacuation Pause tức là GC lỗi.” | Evacuation là hoạt động bình thường; *Evacuation Failure* mới là tình huống cần xem. |
| “Moving GC làm thay đổi `System.identityHashCode`.” | Identity hash semantics phải được bảo toàn; hash không phải address. |
| “Java reference là địa chỉ heap người dùng giữ cố định.” | VM quản lý reference representation và object relocation. |
| “Remembered set là danh sách chính xác mọi reference slot.” | Có thể dùng coarse-grained card info cần scan thêm. |
| “Concurrent GC không cần stop-the-world bao giờ.” | Vẫn có safepoints/pauses/handshakes tùy collector, chỉ giảm công việc STW. |
| “GC càng compact mạnh thì throughput càng cao.” | Compact có copy/CPU/bandwidth/barrier costs; cần đo. |
| “Object nhỏ luôn rẻ vì chỉ cần copy vài bytes.” | Nhiều inbound refs/metadata và lượng object lớn vẫn tốn. |
| “Object lớn ít refs thì GC không tốn gì.” | Copy bytes lớn có thể rất đắt; G1 humongous còn có region waste. |
| “Heap usage cao là phân mảnh.” | Có thể là live-set hoặc retained objects; cần kiểm tra metrics. |
| “Đặt field null sẽ compact heap ngay.” | Chỉ cắt reference edge; GC scheduling/compaction không được hứa. |
| “Chạy `System.gc()` cho kết quả compaction cố định.” | Không có bảo đảm di chuyển object hoặc thời gian collection. |

## 20. Bài tập tư duy có lời giải

**Câu 1.** Heap có 200 MiB free rải đều thành 200 khoảng 1 MiB. Một allocator cần 8 MiB contiguous. Có đủ memory không?

**Đáp án:** **Không nhất thiết**. Tổng free lớn hơn yêu cầu nhưng không có đoạn 8 MiB. Điều này là ví dụ external fragmentation trong mô hình allocator cần contiguous block.

**Câu 2.** Compaction biến 200 MiB free phân tán thành một khoảng 200 MiB. Nó đã “thu hồi thêm 200 MiB” chưa?

**Đáp án:** Không. Nó **gộp free space**, không tự tăng free-byte count.

**Câu 3.** Khi GC chuyển B từ vị trí cũ sang mới, variable `x` và field `a.b` đều trỏ B. Chỉ sửa x đã đủ chưa?

**Đáp án:** Không, collector còn phải bảo đảm `a.b` tiếp tục trỏ đúng B, theo cách update/remap của collector.

**Câu 4.** Một object 8 KiB với một inbound reference và một object 32 byte với một triệu inbound references. Object nào “đắt để move” hơn?

**Đáp án:** Không thể kết luận chỉ từ size hoặc số references. Object 8 KiB đắt hơn về copy bytes, object 32 byte có thể đắt về reference processing; phụ thuộc GC và thuật toán.

**Câu 5.** Mark–sweep có thể để lại holes vì sao?

**Đáp án:** Nó có thể reclaim garbage nhưng để live objects tại chỗ, khiến free blocks xen kẽ object sống.

**Câu 6.** Evacuation giúp gộp free space bằng cách nào?

**Đáp án:** Copy live objects khỏi các source regions được chọn và reclaim toàn bộ source regions khi thành công.

**Câu 7.** G1 có phải STW toàn bộ concurrent marking không?

**Đáp án:** Không. G1 có những phases đồng thời với mutator, nhưng evacuation pauses là STW và Full GC có thể STW dài.

**Câu 8.** Remembered set của G1 phục vụ điều gì?

**Đáp án:** Giúp giới hạn việc tìm refs từ bên ngoài collection set vào các regions đang thu gom, thay vì quét mọi object của toàn heap tại mỗi pause.

**Câu 9.** Vì sao humongous object có thể làm tăng wasted capacity ở G1?

**Đáp án:** Nó chiếm một hoặc nhiều contiguous regions; phần dư của region cuối có thể không dùng được cho allocations khác đến khi object được reclaim.

**Câu 10.** Một chương trình in true từ `a == b` trước và sau GC có chứng minh GC không move object không?

**Đáp án:** Không. Identity được VM bảo toàn kể cả khi object đã move.

**Câu 11.** Collector concurrent giảm pause có phải miễn phí CPU?

**Đáp án:** Không. Barrier/coordination và công việc concurrent có thể tiêu tốn CPU, memory bandwidth và headroom.

**Câu 12.** Heap có live-set 95% limit nhưng ít holes. Vì sao vẫn có thể Full GC/OOME?

**Đáp án:** Không đủ reclaimable space hoặc destination/headroom cho allocation/evacuation, dù external fragmentation không phải nguyên nhân chính.

**Câu 13.** Khi muốn xác nhận một memory leak, class histogram đã đủ chưa?

**Đáp án:** Chưa. Histogram cho số lượng và bytes theo class, còn dominator tree/GC-root paths giúp xác định ai giữ references.

**Câu 14.** Có nên tự lấy địa chỉ object bằng kỹ thuật không chuẩn rồi giữ qua một moving GC?

**Đáp án:** Không. Java reference không phải physical pointer ổn định; cần dùng API/JNI/native interfaces đúng contract.

**Câu 15.** Với một region 2 MiB, object lớn hơn 1 MiB có thể rơi vào loại gì trong G1?

**Đáp án:** **Humongous**, vì kích thước đạt ít nhất nửa region; xử lý special allocation/evacuation phải xét G1 version và policy.

## 21. Bản đồ ghi nhớ

~~~text
               HEAP CÓ FREE SPACE
                         |
                  Free space rời rạc?
                   /             \
                 Có              Không
                 |                 |
        EXTERNAL FRAGMENTATION   Vẫn có thể thiếu
                 |               live/headroom
                 v
         CHỌN THUẬT TOÁN GC
            /            \
       NON-MOVING        MOVING
       mark/sweep      compact / copy
            |               |
       ít copy live      gộp free blocks /
       có thể để holes   reclaim source regions
                            |
                 +----------+-----------+
                 |          |           |
              Copy bytes  Forwarding  Update/remap refs
                 |          |           |
                 +----------+-----------+
                            |
               CPU / bandwidth / pause / headroom
                            |
        G1: regions + RSet + evacuation + humongous
        ZGC / Shenandoah: concurrent relocation + barriers
                            |
                  MEASURE, DO NOT GUESS
                            |
            GC logs + JFR + jcmd + heap dump
                            |
          throughput / p99 latency / GC CPU / RSS
~~~

**Điểm cốt lõi:** phân mảnh là vấn đề **tổ chức free space**, không nhất thiết là thiếu tổng bytes. Di chuyển objects có thể compact heap hoặc giải phóng nguyên regions, nhưng phải duy trì các references hợp lệ — qua update trực tiếp, forwarding, barriers hoặc cơ chế tương đương. Đánh đổi giữa **ít fragmentation** và **chi phí copy/reference handling, pauses, concurrent CPU, memory headroom** phụ thuộc loại GC và workload, nên luôn kiểm chứng trên HotSpot thật.

## Tài liệu chính thức

- [JVMS Java SE 21 — §2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5.3).
- [Oracle Java 21 — Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-g1-garbage-collector1.html).
- [Oracle Java 21 — G1 Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-garbage-collector-tuning.html).
- [Oracle Java 21 — Available Collectors](https://docs.oracle.com/en/java/javase/21/gctuning/available-collectors.html).
- [Oracle Java 21 — Z Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/z-garbage-collector2.html).
- [Oracle Java 21 — The java Command, Unified GC Logging](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- [Oracle Java 21 — jcmd](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html).
- [OpenJDK JEP 333 — ZGC](https://openjdk.org/jeps/333).
- [OpenJDK JEP 189 — Shenandoah GC](https://openjdk.org/jeps/189).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-003">Giả thuyết thế hệ: đối tượng sống ngắn, già hóa và chuyển thế hệ</a>

<details>
<summary>Xem chi tiết</summary>

# Giả thuyết thế hệ: đối tượng sống ngắn, già hóa và chuyển thế hệ

## 1. Ý tưởng cốt lõi: “đa số object chết trẻ”

**Giả thuyết thế hệ** (*generational hypothesis*) là một quan sát thực nghiệm trên nhiều loại chương trình: **đa số objects được tạo có thời gian sống ngắn**, trong khi một phần nhỏ được giữ rất lâu. Chúng ta tạo iterator, temporary strings, request objects, buffer hay kết quả tính trung gian rất thường xuyên; nhiều object không còn reachable sau một thao tác ngắn. Các objects như application configuration, shared caches hợp lệ, sessions hoặc indexes có thể sống rất lâu.

**Generational garbage collection** dùng quan sát này để phân vùng quản lý heap theo tuổi thọ tương đối:

1. **Young generation** dành cho những objects mới hoặc chưa sống lâu qua nhiều lần young GC.
2. **Survivor** giữ các objects vẫn còn reachable sau một lần young GC để tránh đưa tất cả lên Old quá sớm.
3. **Old generation** quản lý phần dữ liệu đã sống đủ lâu hoặc được chuyển tới bởi chính sách collector.
4. **Promotion/tenuring** là quá trình đưa object còn sống từ Young sang Old.

Mục tiêu là thu gom thường xuyên ở nơi có nhiều rác và lượng dữ liệu còn sống tương đối nhỏ, thay vì bắt buộc quét mọi object của toàn heap cho mỗi temporary allocation.

**Điều phải nói rõ:** đây là **chiến lược của các garbage collectors**, không phải yêu cầu cứng của Java language hay JVMS rằng mọi JVM đều có Eden, Survivor và Old. **JVMS `2.5.3`** mô tả heap cho class instances/arrays và quản lý bộ nhớ tự động, còn tổ chức generations tùy VM và collector.

### Một giả thuyết chứ không phải lời hứa cho mọi ứng dụng

| Workload / object | Lifetime thường gặp | Ý nghĩa đối với generational GC |
| --- | --- | --- |
| Temporary string/iterator | Rất ngắn | Có thể chết trước young collection đầu tiên |
| DTO phục vụ request | Ngắn | Thường có nhiều cơ hội reclaim ở Young |
| Buffer của tác vụ bất đồng bộ | Trung bình | Có thể sống qua vài young collections |
| Một phần session/cache entries | Trung bình hoặc dài | Có thể già hóa rồi promotion |
| Configuration hoặc shared registry | Dài | Có thể góp vào old live-set |
| Cache không giới hạn giữ data lỗi thời | Quá dài so với nhu cầu thực tế | Có thể gây memory retention/old pressure |

Đây là các ví dụ về xu hướng, **không phải quy luật của class names**. Một `String` cũng có thể sống suốt vòng đời chương trình, còn một object `Session` có thể chết ngay.

## 2. Minh họa phân bố tuổi thọ

~~~text
Số objects có tuổi thọ tương ứng
  ^
  | #################
  | #################
  | ####################
  | ########################           ##
  | ################################ #######
  +---------------------------------------------------->
       rất ngắn              trung bình          rất dài
                         lifetime
~~~

Đây là **minh họa định tính**, không phải biểu đồ thống kê của ứng dụng cụ thể. Nguồn Oracle về *weak generational hypothesis* giải thích phần lớn objects thường chỉ sống trong giai đoạn rất ngắn, dù có workload có phân bố khác đáng kể.

**“Chết” về GC nghĩa là trở nên không còn reachable theo semantics phù hợp**, không phải đơn giản là source Java vừa chạy `obj = null` hoặc đi qua dấu `}`. Nếu object còn trong field, collection, task queue hoặc cache đang reachable, GC vẫn phải giữ nó. Ngược lại, ngay khi object unreachable, GC không nhất thiết chạy lập tức.

## 3. Heap thế hệ: Eden, Survivor và Old

Trong mô hình generational copying collector cổ điển:

~~~text
                    JAVA HEAP
+-------------------------------------------+--------------------+
| YOUNG GENERATION                          | OLD GENERATION     |
|                                           |                    |
| +---------------+ +----------+ +---------+| +----------------+ |
| | Eden          | | Survivor | |Survivor | | | Object đã sống| |
| | Object mới    | | From     | |To       | | | lâu/promoted   | |
| +---------------+ +----------+ +---------+| +----------------+ |
+-------------------------------------------+--------------------+

new -> Eden
Young GC: dead -> reclaim; live -> Survivor hoặc Old
Nhiều collection sống sót -> đủ điều kiện promotion -> Old
~~~

Trong các collector truyền thống dùng hai survivor spaces, chúng có thể đổi vai trò **From/To** sau GC. Nhưng **không phải mọi object phải ghé qua cả hai Survivor spaces** và không phải mọi collector có đúng hai khối Survivor liên tục.

### G1 trong HotSpot JDK 25

G1 là trường hợp cần phân biệt với sơ đồ cũ. G1 chia heap thành những **regions**, gán vai trò như Eden, Survivor, Old hoặc Humongous; các regions cùng generation **không bắt buộc liền kề** trong memory.

~~~text
Heap region sequence minh họa (không phải địa chỉ bắt buộc):

[Old][Free][Eden][Survivor][Eden][Old][Humongous][Survivor]
           \        |        /
                  YOUNG
~~~

Theo **Oracle JDK 25 G1 guide**, young objects còn sống có thể được copy từ Eden/Survivor sang **survivor hoặc old regions tùy tuổi**; những old objects thuộc collection set khi bị evacuate được chuyển theo quy tắc old regions. G1 dùng young-only phases và mixed collections, không đồng nhất young GC với full collection.

**Không đồng nhất “Old” với “Metaspace”:** Old generation trong bài này là **vùng Java object heap** của collector. Metaspace là chi tiết HotSpot về class metadata; chúng không phải một vùng.

## 4. Vì sao thu gom Young thường hiệu quả?

Giả sử có **100.000 objects** vừa được cấp phát và đến thời điểm young GC:

- 96.000 objects không còn reachable;
- 4.000 objects vẫn cần giữ.

Nếu collector chỉ phải copy/evacuate dữ liệu live phù hợp, phần rất lớn memory của nhóm object trẻ có thể được reclaim trong một đợt mà không bắt buộc quét/reclaim toàn bộ Old.

~~~text
100.000 objects mới
       |
       v
   Young GC
    /      \
dead       live
96.000     4.000
  |          |
reclaim   copy/evacuate -> Survivor hoặc Old
~~~

**Những số liệu này hoàn toàn giả định** để minh họa lợi ích của việc nhiều objects chết trẻ. Chi phí GC thực tế còn có roots, reference graphs, remembered sets, synchronization, stop-the-world/concurrent tasks và policies riêng.

Nếu workload có **survival rate cao**, young GC có thể phải copy nhiều live bytes, khiến giả thuyết thế hệ đem lại lợi ích thấp hơn. Vì vậy tuning phải bắt đầu bằng **allocation rate và survival rate đo được**, không phải dựa vào một tỉ lệ đẹp trên slide.

## 5. “Tuổi” của object trong GC là gì?

**Tuổi (age) của object** trong generational GC thường là thông tin về **số lần object đã sống sót qua những young collections phù hợp**. Nó **không phải** số giây/phút kể từ lúc `new`, không phải số vòng lặp hoặc số lần method được gọi.

Một diễn tiến minh họa:

~~~text
Lúc tạo X trong Eden:          age ~ 0
Young collection #1: X live -> Survivor, age tăng
Young collection #2: X live -> Survivor/Old, theo policy
Young collection #3: X live -> có thể promotion nếu đạt threshold
Old generation:             X vẫn có thể chết và được GC thu hồi sau
~~~

**Không được suy** “sau đúng 3 young collections mọi object đều chuyển Old”: ví dụ trên cố tình không gán threshold cố định. Số lần tăng age, thời điểm promotion và cách biểu diễn tuổi phụ thuộc collector.

### Phân biệt tuổi tính theo GC và thời gian đồng hồ

~~~text
Object A: sống 30 phút nhưng không có young GC trong thời gian đó.
Object B: sống 2 giây nhưng workload tạo nhiều temporary objects,
          làm xảy ra nhiều young collections và B sống qua tất cả.

B có thể đã tích lũy age nhiều hơn A theo chính sách GC.
~~~

Đây là tình huống khái niệm; thời gian A/B không dự đoán được object location cụ thể.

| Khẳng định | Đúng không? |
| --- | --- |
| “Object sống 10 phút chắc chắn ở Old” | **Không** |
| “Object mới vài mili giây không thể được promotion” | **Không**, có trường hợp special policy/pressure |
| “Một object được promotion sẽ không còn bị GC” | **Sai**, Old vẫn được thu |
| “Tuổi tăng khi thread gọi `Thread.sleep`” | **Không trực tiếp** |
| “Object trẻ nhưng còn reachable sẽ bị reclaim như rác” | **Sai**, collector phải bảo vệ live objects |

## 6. Promotion / tenuring hoạt động như thế nào?

**Promotion** là chuyển một **live object** từ young generation sang old generation. Tùy collector, việc chuyển có thể được thực hiện qua object evacuation/copy, cập nhật references/barriers hoặc cơ chế tương đương.

Có ba nhóm nguyên nhân quan trọng:

1. **Age threshold:** object đã sống qua đủ những collections theo policy.
2. **Survivor capacity pressure:** survivor destination không đủ chỗ giữ tất cả live young objects; collector có thể promotion sớm.
3. **Collector-specific cases:** large/humongous allocations, GC ergonomics và các đường xử lý riêng có thể không theo hành trình Eden → Survivor → Old cổ điển.

### Ví dụ ngưỡng tuổi bằng 4 (chỉ để học)

| Sự kiện | Tuổi minh họa | Vị trí có thể có |
| --- | ---: | --- |
| Vừa tạo | 0 | Eden |
| Sống qua young GC 1 | 1 | Survivor |
| Sống qua young GC 2 | 2 | Survivor |
| Sống qua young GC 3 | 3 | Survivor |
| Sống qua young GC 4, thỏa threshold 4 | 4 | Old |

**Ngưỡng 4 là giả định cho ví dụ**, không phải giá trị mặc định của G1. Trong runtime thật, survivor pressure hoặc những quyết định policy khác có thể chuyển object sớm hơn.

### MaxTenuringThreshold có phải “đúng N lần GC”?

**Không.** Với các HotSpot collectors sử dụng age-based tenuring, cờ `-XX:MaxTenuringThreshold` là một **giới hạn trên cho ngưỡng** phù hợp, không buộc mọi object phải chờ đúng bằng giá trị ấy. Collector có thể chọn một **desired tenuring threshold** thấp hơn theo survivor occupancy và age distribution.

Ngoài ra, những collectors khác nhau có cơ chế tenuring và flags khác nhau. Một giá trị ở `-XX:+PrintFlagsFinal` không phải lời hứa rằng mọi object đều sống qua chừng ấy GC mới promotion.

## 7. Survivor space/region, dynamic threshold và promotion sớm

Giả sử tại young GC, tổng live young data cần giữ là 120 MiB nhưng phần survivor destination dự kiến chỉ đủ 32 MiB.

~~~text
Young live data:     120 MiB
Survivor capacity:    32 MiB
--------------------------------
Phần không phù hợp Survivor có thể cần vào Old hoặc
đi đường collector-specific khác.
~~~

Đây là số giả định. **Survivor không đủ không đồng nghĩa JVM lập tức OOME**: collector có thể promotion hoặc xử lý allocation qua đường khác nếu tổng heap/Old còn khả năng đáp ứng.

**Premature promotion** là chuyển nhiều objects sang Old trước mức age mong muốn, ví dụ do survival rate cao hoặc survivor không đủ. Tác động có thể gồm:

- Old occupancy tăng;
- tăng chi phí marking/mixed/old collection;
- những object tuổi trung bình chết trong Old trước khi chu kỳ Old reclaim phù hợp;
- tăng memory pressure và khó đáp ứng mục tiêu pause time.

**Không tự động kết luận đây là lỗi của collector.** Đôi khi promotion sớm là quyết định cần thiết để bảo đảm allocation progress, và điều chỉnh một flag không thay đổi profile sống của object thực tế.

## 8. Cross-generation references: Old → Young

Nếu collector chỉ tập trung young objects, nó vẫn phải nhận ra **Old object đang giữ Young object**:

~~~text
OLD OBJECT                              YOUNG OBJECT
+----------------+                     +-----------------+
| SessionManager | ------------------> | NewSession      |
| currentSession |                     | still live      |
+----------------+                     +-----------------+
~~~

Young object **không được phép bị reclaim** vì một old object đang reachable trỏ tới nó. Để tránh phải duyệt toàn bộ old heap mỗi lần young GC, collector có các cơ chế như:

- **Write barriers**: JVM/JIT ghi nhận thông tin khi chương trình sửa reference fields.
- **Card table / remembered sets**: metadata giúp tìm những references trỏ tới young/collection set từ ngoài collection set.
- **Root scanning** và graph traversal theo thuật toán collector.

Trong **G1**, remembered sets dùng để ghi nhận vị trí ở ngoài collection set chứa references vào collection set, giúp duy trì chính xác live reachability khi evacuate Young.

**Không nhầm** write barrier với Java `synchronized` hoặc `volatile`. Write barrier là cơ chế implementation của collector; `synchronized`/`volatile` có vai trò thuộc Java Memory Model và điều khiển đồng bộ, dù VM có thể phối hợp nhiều cơ chế dưới machine code.

## 9. Young-only, mixed, old và full GC khác nhau

| Kiểu công việc | Ý nghĩa |
| --- | --- |
| **Young-only collection** | Thu gom young regions theo collection set thích hợp, copy giữ live objects |
| **Concurrent marking** | Xác định old live data/candidates, có các pha chạy đồng thời với ứng dụng tùy G1 |
| **G1 mixed collection** | Bao gồm Young và một số Old regions được chọn dựa trên hiệu quả reclaim |
| **Full GC** | Xử lý toàn heap theo đường collector tương ứng; thường có pause-cost lớn |
| **Generational ZGC/Shenandoah cycles** | Có young/old handling nhưng thuật toán/concurrency/logging khác G1 |

**Young GC không có nghĩa “xóa sạch mọi rác của JVM”**, và **promotion không có nghĩa một Full GC đã diễn ra**. Khi một object được promotion, nó vẫn đang live; về sau nếu trở thành unreachable, Old reclamation phù hợp mới có thể thu hồi.

Theo Oracle G1 tuning guide, G1 có young-only phase, concurrent mark và các mixed collections nhằm lấy lại Old regions hiệu quả. Dòng log “`Pause Young`” vì vậy không được đọc thành “`Full GC`”.

## 10. Ví dụ Java 17+: temporary objects và nhóm được giữ lại

Tạo file **`GenerationalWorkload.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class GenerationalWorkload {
    private static final List<byte[]> RETAINED = new ArrayList<>();
    private static volatile Object lastPublished;

    public static void main(String[] args) {
        // Có một nhóm mảng được giữ reachable bởi static list.
        for (int i = 0; i < 16; i++) {
            byte[] array = new byte[4096];
            array[0] = 1;
            RETAINED.add(array);
        }

        int created = 0;

        // Nhiều mảng tạm, chỉ reference cuối gần nhất được giữ bởi
        // volatile field; các mảng trước đó có thể trở thành unreachable.
        for (int batch = 0; batch < 24; batch++) {
            for (int j = 0; j < 8192; j++) {
                byte[] temporary = new byte[1024];
                temporary[0] = (byte) (j & 127);
                lastPublished = temporary;
                created++;
            }
        }

        lastPublished = null;
        System.out.println("temporary-created=" + created);
        System.out.println("retained-count=" + RETAINED.size());
        System.out.println("retained-length=" + RETAINED.get(0).length);

        RETAINED.clear();
        System.out.println("after-clear=" + RETAINED.size());
        System.out.println("gc-timing-not-guaranteed");
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac -g GenerationalWorkload.java
java GenerationalWorkload
~~~

**Stdout chính xác trong điều kiện chạy bình thường:**

~~~text
temporary-created=196608
retained-count=16
retained-length=4096
after-clear=0
gc-timing-not-guaranteed
~~~

Vì:

- `24 × 8192 = 196608`;
- Có 16 retained arrays dài 4096;
- `RETAINED.clear()` đặt kích thước list về 0 theo API;
- Không có phép đo GC trong những dòng output trên.

### Ví dụ chứng minh điều gì và không chứng minh điều gì?

**Chứng minh:** chương trình tạo rất nhiều nhu cầu cấp phát tạm thời, đồng thời chủ động giữ một nhóm objects reachable trong list. Sau khi bỏ references từ list, GC **vẫn không có deadline** phải thu hồi objects.

**Không chứng minh:** object cụ thể nào đã được promotion ở lần GC bao nhiêu, current tenuring threshold bằng mấy, số object nằm ở Survivor/Old, hoặc HotSpot có chạy một mã lệnh cấp phát nhất định. Những thông tin đó là collector/runtime-specific.

`lastPublished` là volatile field để tạo đường escape quan sát được cho temporary arrays, hạn chế tối ưu xóa sạch allocations; nhưng đừng suy có tương ứng 1:1 tuyệt đối giữa mỗi biểu thức `new` trong source và một allocation event profiler nhìn thấy, vì JIT/collector có thể tối ưu.

## 11. Quan sát GC trên HotSpot: phải tách program stdout và diagnostics

Thử trên HotSpot (ví dụ JDK 17/21/25 phù hợp):

~~~bash
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc=info GenerationalWorkload
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc+heap=info GenerationalWorkload
~~~

Program stdout vẫn có **năm dòng xác định** nêu trên. Nhưng **GC logs không có output cố định**: số GC, timestamps, số regions, bytes trước/sau pause phụ thuộc JDK/heap/JIT/CPU/collector/workload.

Với workload trên, tổng bytes temporary allocations theo logic vượt giới hạn heap 64 MiB trong một lần chạy, nhưng nhiều objects có thể trở nên unreachable và được reclaim; **`-Xmx64m` không phải giới hạn tổng lượng bytes có thể allocate cộng dồn**. Nếu máy/VM không phù hợp, điều chỉnh heap lên mức an toàn hoặc bỏ flags; không cố ép gây OOME trên môi trường dùng chung.

### Đọc G1 region logs

Ví dụ một đoạn **minh họa**, không phải output được bảo đảm:

~~~text
[gc,heap] GC(12) Eden regions: 20->0(15)
[gc,heap] GC(12) Survivor regions: 2->4(5)
[gc,heap] GC(12) Old regions: 10->11
[gc]      GC(12) Pause Young (Normal) (G1 Evacuation Pause) ...
~~~

- `Eden regions`: region counts trước/sau collection và mục tiêu phân vùng tiếp theo theo format log.
- `Survivor regions`: lượng regions được gán survivor role thay đổi khi live objects được evacuated.
- `Old regions`: thông tin region count, **không phải số lượng individual objects được promotion**.
- `Pause Young`: nhãn G1 young collection, **không phải Full GC**.

G1 region sizes và vùng Young không nhất thiết nằm liên tiếp trong address space.

## 12. Quan sát tuổi và ngưỡng tenuring

HotSpot unified logging cho phép các tag GC liên quan age trên phiên bản/collector hỗ trợ:

~~~bash
java -Xlog:help
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc+age=debug GenerationalWorkload
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc+age=trace GenerationalWorkload
~~~

Có thể thấy:

- **Desired survivor size**: kích thước survivor mà policy nhắm tới.
- **New threshold / desired tenuring threshold**: ngưỡng tuổi hiện hành, có thể thấp hơn maximum.
- **Age distribution**: lượng bytes sống thuộc các nhóm age.
- **Total**: số bytes cộng dồn đến một nhóm age trong mẫu log.

Một output **giả định hoàn toàn để dạy cách đọc**:

~~~text
[gc,age] Desired survivor size 2097152 bytes, new threshold 5 (max threshold 15)
[gc,age] - age   1: 800000 bytes, 800000 total
[gc,age] - age   2: 420000 bytes, 1220000 total
[gc,age] - age   3: 160000 bytes, 1380000 total
~~~

Điều quan trọng:

- `threshold 5` **không** đồng nhất với `max 15`.
- `age 1: 800000 bytes` **không** có nghĩa “800000 objects”.
- Age table đo đặc tính live survivor data theo collector, không chỉ ra source variable nào đã già.
- Nếu một workload ngắn/collector khác không phát sinh các dòng này, kiểm tra `-Xlog:help` và version thay vì bịa logs.

Có thể xem flags:

~~~bash
java -XX:+UseG1GC -XX:+PrintFlagsFinal -version
~~~

Tìm `MaxTenuringThreshold`, `TargetSurvivorRatio` và các parameters liên quan trong output phù hợp. Không tự thay đổi production flags chỉ vì một giá trị trông “nhỏ”: adaptive policies có thể đã lựa chọn thích hợp với workload.

## 13. Vì sao Old → Young references làm GC phức tạp hơn?

Tưởng tượng young GC không xử lý Old → Young references:

~~~text
Old Object (reachable) --> Young Object (không có local var nào khác)
~~~

Một thuật toán chỉ thấy young local roots mà quên cross-generation edge có thể kết luận sai Young object là garbage. Vì vậy generational collector phải duy trì metadata/algorithms phát hiện những edges phù hợp.

**G1 remembered sets** ghi nhận những tham chiếu **từ ngoài collection set trỏ vào collection set**, giúp xử lý tập vùng nhỏ hơn toàn heap. Update references có thể dùng write barriers, dirty cards và concurrent refinement tùy collector.

Đó là phần **giá phải trả** để thu gom Young hiệu quả. Không được trình bày rằng young GC là “không cần quan sát Old dưới bất kỳ hình thức nào”.

## 14. Large/humongous objects và những ngoại lệ

Không phải mọi object trải qua đúng hành trình:

~~~text
Eden -> Survivor S0 -> Survivor S1 -> Old
~~~

G1 có **humongous objects** chiếm một số lượng regions theo các quy tắc riêng. Một số large objects/arrays có thể được phân bổ và xử lý trực tiếp trong vùng đặc biệt thay vì bắt đầu bằng Eden rồi increment age thông thường.

Các trường hợp promotion sớm cũng có thể đến từ Survivor capacity; và một optimized method có `new` trên bytecode nhưng JIT escape analysis/scalar replacement loại heap allocation vật lý. Vì vậy **đừng đếm Java `new` statements rồi suy chính xác số object ở Eden hoặc Old**.

## 15. Generational collectors khác nhau không dùng cùng policy

| Collector | Đặc trưng đáng nhớ |
| --- | --- |
| **Serial GC** | Mô hình generational Eden/Survivor/Old tương đối trực quan |
| **Parallel GC** | Generational collector tập trung throughput; có ergonomics/adaptive sizing |
| **G1 GC** | Dùng heap regions, young-only và mixed collection sets, remembered sets |
| **Generational ZGC** | Low-latency concurrent generational collector, policy/barrier/age khác G1 |
| **Generational Shenandoah** | Chế độ generational trên phiên bản/cấu hình phù hợp, nhiều pha concurrent |
| **Non-generational collectors/modes** | Không nhất thiết dùng Young/Old hoặc tenuring giống ví dụ |
| **Epsilon GC** | No-op collector, không reclaim objects; dành cho thí nghiệm chuyên biệt |

Theo các release JDK hiện đại, **ZGC đã có generational mode** và trên JDK 25 dùng generational mode; Generational Shenandoah có trong các bản JDK phù hợp. Tuy nhiên không đồng nghĩa các flags `MaxTenuringThreshold` hoặc logging `gc+age` có ý nghĩa giống nhau trên tất cả những collectors này.

**Không suy từ tài liệu Java 8 rằng G1 của Java 25 phải có hai Survivor spaces vật lý liền kề**. Hãy chọn đúng phiên bản Oracle/OpenJDK GC guide cho môi trường đang chạy.

## 16. Tuning phải dựa trên phân bố lifetime thật

Khi gặp nhiều young GCs hoặc old occupancy tăng nhanh, kiểm tra:

1. **Allocation rate** (bytes/s), **young GC frequency**, pause time, application throughput và heap size.
2. **Survival rate** qua young collections và **promotion rate**.
3. **Old live-set** sau các old/mixed collections; có đang tăng theo tải tương đương không?
4. **Survivor occupancy** và **desired tenuring threshold** qua age logs.
5. **Object graph và owners**: cache, long-lived sessions, queues, thread-locals, listeners, unbounded maps.
6. **Humongous allocation**/collector-specific constraints.
7. **Phiên bản JDK/collector/flags** và khác biệt khi thử cấu hình.

Một bảng trade-offs:

| Thay đổi | Lợi ích có thể | Rủi ro |
| --- | --- | --- |
| Young capacity lớn hơn | Có thể giảm số young GCs | Có thể tăng live bytes cần xử lý; Old capacity giảm nếu heap fixed |
| Survivor capacity lớn hơn | Giảm promotion sớm trong một số trường hợp | Lấy bớt young capacity dành cho Eden |
| Tenuring threshold khác | Ảnh hưởng thời gian young objects chuyển Old | Có thể tăng copy cost hoặc old pressure |
| Heap lớn hơn | Thêm allocation headroom | Không sửa retention/leak tự động |
| Giảm object allocations không cần thiết | Giảm GC pressure | Có thể hy sinh code clarity nếu tối ưu thiếu benchmark |
| Giải phóng cache entries đúng lifecycle | Giảm live-set/old pressure | Cần bảo đảm correctness và cache policy |

**Không có “ngưỡng tenuring chuẩn cho mọi ứng dụng”.** Garbage collectors hiện đại có nhiều ergonomic decisions tự động; thay flags chỉ nên thực hiện sau khi đo workload tái tạo tương đương.

## 17. Vì sao promotion rate cao chưa chắc là memory leak?

Nếu ứng dụng có nhiều objects **medium-lived hợp lệ**, chúng có thể sống qua vài young GCs rồi lên Old trước khi trở thành unreachable. Ngoài ra, Survivor capacity pressure có thể promote sớm. Vì vậy promotion rate cao **không tự chứng minh leak**.

Memory leak về mặt ứng dụng thường là **retention không mong muốn**:

~~~text
Static Cache -> Map -> Session -> Large Object Graph
                     ^
              request đã kết thúc,
              nhưng cache vẫn giữ reference
~~~

Trong tình huống đó, GC **không thể thu hồi object còn reachable** chỉ vì nó ở Young hay Old. Cách sửa là làm rõ ownership/eviction, xóa entries không cần, unregister listeners hoặc đóng resources đúng lifecycle. Không có `MaxTenuringThreshold` nào giúp GC xóa an toàn một object vẫn còn được chương trình sử dụng.

Khi đánh giá leak, xem **live-set sau nhiều GC cycles** và các đường từ GC roots tới retained objects, không chỉ nhìn tổng allocated bytes. Một chương trình tạo nhiều temporary objects có thể tạo tổng allocation cực lớn nhưng live-set vẫn nhỏ.

## 18. Những ngộ nhận cần sửa

| Hiểu nhầm | Điều đúng |
| --- | --- |
| “Mọi Java object chết trẻ.” | Là xu hướng thực nghiệm trong nhiều workloads, không phải bảo đảm |
| “JVMS bắt buộc có Eden/Survivor/Old.” | Đây là thiết kế GC implementation |
| “Age là số giây kể từ `new`.” | Thường dựa trên survival qua collections |
| “Mỗi young GC mọi live object tăng tuổi đúng một theo mọi collector.” | Phụ thuộc loại collection/policy |
| “`MaxTenuringThreshold=15` buộc đủ 15 GCs.” | Có dynamic threshold, survivor pressure và ngoại lệ |
| “Survivor đầy lập tức gây OOME.” | Có thể promotion hoặc đường xử lý khác |
| “Promotion là thu gom object.” | Promotion **giữ** live object và chuyển generation |
| “Object được promotion sẽ không bao giờ bị GC.” | Old objects vẫn có thể reclaimed về sau |
| “Young GC đồng nghĩa Full GC.” | Young-only không xử lý toàn bộ heap |
| “Old region tăng 1 nghĩa một object được promote.” | Region count không phải object count |
| “G1 có hai Survivor spaces contiguous cứng.” | G1 dùng survivor regions phân bố trong heap |
| “Young GC hoàn toàn không cần xét Old references.” | Cross-generation references phải được bảo vệ |
| “GC tạo ra thread-safety cho các references.” | Không; JMM và đồng bộ là chủ đề khác |
| “`System.gc()` sẽ tăng tuổi/promotion object theo ý muốn.” | Không có guarantee về GC/promotion timing |
| “Mọi collector có cùng age log format/threshold.” | Collector-specific implementation |
| “Promotion rate cao luôn là leak.” | Có thể lifetime profile hợp lệ hoặc premature promotion |
| “Cứ `new` là một vật thể heap thật.” | JIT có thể loại allocation |
| “Chỉnh GC flags đủ sửa static cache leak.” | Không, phải sửa object retention |
| “Generational hypothesis bảo đảm young GC luôn nhanh.” | Phụ thuộc survival rate và heap dynamics |

## 19. Câu hỏi ôn tập có đáp án

**1.** Giả thuyết thế hệ mô tả điều gì?  
**Đáp án:** Trong nhiều workloads, phần lớn objects không sống lâu, tạo cơ sở để thu gom Young thường xuyên.

**2.** Object ở Survivor có nghĩa nó đã tồn tại bao nhiêu giây?  
**Đáp án:** Không thể suy chỉ từ vị trí; age thường theo những lần young collection phù hợp.

**3.** Promotion xảy ra khi nào?  
**Đáp án:** Khi live object thỏa age threshold hoặc các điều kiện survivor capacity/collector policy khác.

**4.** Object ở Old là bất tử?  
**Đáp án:** Không. Nếu không còn reachable, nó có thể reclaimed trong old/mixed/full hoặc collector-specific cycles.

**5.** Vì sao Old → Young reference cần được ghi nhận?  
**Đáp án:** Nếu bỏ sót, young GC có thể thu nhầm Young object vẫn được Old object giữ sống.

**6.** `MaxTenuringThreshold=15` có nghĩa mọi object phải qua đúng 15 collections?  
**Đáp án:** Không; đó là giới hạn chính sách khi collector áp dụng, với desired threshold và các nhánh promotion khác.

**7.** G1 có một Eden vật lý liền kề mọi Survivor regions?  
**Đáp án:** Không. G1 phân vùng heap thành regions có vai trò khác nhau và không bắt buộc liền kề.

**8.** `Old regions: 10->11` cho biết có đúng một object được promotion?  
**Đáp án:** Không. Một region chứa nhiều objects.

**9.** `System.gc()` có bảo đảm tạo young GC và làm một object tăng age?  
**Đáp án:** Không.

**10.** Nếu static cache giữ object quá lâu, sửa gì trước?  
**Đáp án:** Xác định ownership và eviction/release, thay vì chỉ chỉnh tenuring threshold.

**11.** Young allocation rate lớn nhưng Old live-set nhỏ có thể là trường hợp bình thường?  
**Đáp án:** Có, nhiều temporary objects nhanh chết tạo tổng allocated bytes lớn nhưng ít retained objects.

**12.** Tại sao không so G1 age logs trực tiếp với Generational ZGC?  
**Đáp án:** Collector implementations và log semantics khác nhau.

## 20. Nguồn tham khảo đáng tin cậy

- [JVMS Java SE 25 — `2.5.3` Heap](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.3): heap và storage management, không quy định một chiến lược generations cố định.
- [Oracle Java 8 — Generations / Weak Generational Hypothesis](https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/generations.html): nguồn kinh điển “most objects die young”; **mô tả layout/collector mang tính lịch sử**.
- [Oracle JDK 25 — G1 Garbage Collector](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-first-g1-garbage-collector1.html): Eden/Survivor/Old regions, age-based promotion, remembered sets, young/mixed phases.
- [Oracle JDK 25 — Garbage Collector Implementation](https://docs.oracle.com/en/java/javase/25/gctuning/garbage-collector-implementation.html): giải thích cách đọc G1 region logs.
- [Oracle JDK 25 — Factors Affecting GC Performance](https://docs.oracle.com/en/java/javase/25/gctuning/factors-affecting-garbage-collection-performance.html): survivor sizing, thresholds và tuning trade-offs.
- [Oracle JDK 25 — java Command / Unified Logging](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): `-Xlog:gc+age=debug/trace` và các flags chẩn đoán.
- [OpenJDK JEP 439 — Generational ZGC](https://openjdk.org/jeps/439): ý nghĩa phân thế hệ trong low-latency ZGC.
- [OpenJDK JEP 474 — ZGC: Generational Mode by Default](https://openjdk.org/jeps/474): chế độ generational ZGC ở releases liên quan.
- [OpenJDK JEP 490 — Remove Non-Generational ZGC](https://openjdk.org/jeps/490): lộ trình loại non-generational mode.
- [OpenJDK JEP 521 — Generational Shenandoah](https://openjdk.org/jeps/521): generational Shenandoah và giới hạn phiên bản.
- [OpenJDK HotSpot — gc_globals.hpp](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/gc/shared/gc_globals.hpp): các cờ age/tenuring của HotSpot; mã master có thể khác phiên bản chạy thực.

**Ghi nhớ:** đối tượng mới thường được quản lý ở **Young**; đa số chết trước khi cần giữ lâu; những object còn reachable có thể ở **Survivor**, tăng **GC age**, rồi **promotion** sang **Old** khi policy yêu cầu. **Tuổi không phải thời gian đồng hồ, ngưỡng có thể động, G1 dùng regions chứ không bắt buộc các spaces liền nhau, Old vẫn được GC, và mọi kết luận về performance/retention phải dựa trên dữ liệu workload thật.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-004">Eden, survivor và old trong mô hình bộ thu gom theo thế hệ</a>

<details>
<summary>Xem chi tiết</summary>

# Eden, Survivor và Old trong mô hình bộ thu gom rác theo thế hệ của JVM

Trong Java, phần lớn đối tượng được tạo ra, sử dụng trong thời gian ngắn rồi không còn được tham chiếu. Nhưng cũng có những đối tượng sống hàng phút hoặc hàng giờ: cache, connection pool, dữ liệu phiên, các cấu trúc nghiệp vụ lớn. **Generational garbage collection** tận dụng sự khác biệt về vòng đời này để thu gom hiệu quả: ưu tiên quét/thu hồi những đối tượng mới tạo thường chết sớm, và quản lý riêng những đối tượng đã sống qua nhiều lần thu gom.

Ở cách giải thích kinh điển, **young generation** gồm **Eden** và các **Survivor spaces**; **old generation** chứa những đối tượng đã tồn tại lâu hơn hoặc được cấp phát vào old theo chính sách collector. Từ đó có ba câu hỏi trọng tâm:

1. Đối tượng mới thường được tạo ở đâu và điều gì xảy ra khi Eden không còn đủ chỗ?
2. Khi còn sống sau Young GC, đối tượng chuyển tới Survivor hay Old? **Age**, **tenuring** và **promotion** khác gì nhau?
3. Vì sao **Eden/Survivor/Old trong HotSpot G1 Java 17 là vai trò của các regions**, không đơn giản là ba dải bộ nhớ cố định như nhiều sơ đồ cũ?

Bài học tập trung vào mô hình thế hệ và ứng dụng của nó trong **HotSpot G1 trên Java 17**, đồng thời chỉ rõ phần nào thuộc khái niệm chung, phần nào là implementation detail. Có sơ đồ, timeline, Java 17 chạy được, hướng dẫn GC logging, các sai lầm thường gặp và bài tập.

## 1. Tại sao cần thu gom theo thế hệ?

Một quan sát thực nghiệm thường gọi là **weak generational hypothesis**: nhiều object được tạo chỉ tồn tại rất ngắn, và nhiều object đã tồn tại lâu có xu hướng tiếp tục tồn tại. Đây là **xu hướng workload**, không phải quy luật “mọi object trẻ đều chết” hay “mọi object già đều bất tử”.

Ví dụ trong một web service:

| Object | Vòng đời thường gặp | Hệ quả cho GC |
| --- | --- | --- |
| `StringBuilder` tạm trong xử lý request | Một vài method calls | Có thể chết nhanh, đáng thu gom ở young |
| DTO giải mã JSON chỉ dùng trong một request | Từ vài ms đến vài giây | Có thể được thu gom sau khi response hoàn tất |
| Bản ghi cache có TTL | Nhiều requests | Có thể sống qua các lần Young GC |
| Map chứa trạng thái ứng dụng lâu dài | Phút/giờ hoặc suốt runtime | Thường vẫn reachable trong old |
| Object dùng trong bootstrap rồi bỏ | Dài/ngắn tùy ứng dụng | Không thể suy tuổi chỉ từ nơi source khởi tạo |

Thay vì mỗi lần bộ thu gom đều xử lý **toàn bộ heap** bất kể tỷ lệ sống, generational collector có thể xử lý **young** thường xuyên. Nó tìm những object trẻ còn reachable, chuyển/copy các object sống sang khu vực thích hợp và tái sử dụng phần lớn dung lượng Eden chứa object đã chết.

Đây là mô hình tối ưu, **không thay thế reachability analysis**. Một object “già” nhưng không còn reference vẫn là garbage, và một object mới nhưng được giữ bởi static field vẫn là live.

### 1.1. Object chết vì mất reachability, không phải vì “đến tuổi”

Một object chỉ được xem là có thể thu hồi khi không còn reachable từ những GC roots/đường tham chiếu mà collector phải xét. GC không xóa object chỉ vì nó đã “ở Eden đủ ba giây”.

- **Age** thường được dùng để quyết định **nơi giữ object còn sống** sau một lần young evacuation.
- **Reachability** dùng để quyết định **object còn sống hay là garbage**.
- **Wall-clock age** (số giây kể từ `new`) không phải trường `age` mà generational GC dựa vào khi tenuring.

Hai object tạo cùng thời điểm có thể được đối xử khác nhau nếu một object được giữ trong list lâu dài, còn object kia không còn reference.

## 2. Ba vai trò: Eden, Survivor, Old

| Vùng | Vai trò | Điều xảy ra thường thấy |
| --- | --- | --- |
| **Eden** | Nơi các object nhỏ mới tạo được cấp phát | Young GC thu gom Eden; object chết được bỏ, object sống được evacuate |
| **Survivor** | Giữ những object còn sống sau young collection nhưng chưa phải old | Object có thể tiếp tục sống qua nhiều young collections và tăng tuổi |
| **Old** | Giữ đối tượng đã được promoted, đối tượng được cấp phát đặc biệt vào old và một số object sống lâu | Old được đánh dấu/thu hồi qua những chu kỳ collection phù hợp; không phải vùng “GC không bao giờ đụng” |

Mô hình dễ nhớ:

```text
                     YOUNG GENERATION
            ┌──────────────────────────────┐
new objects │          EDEN                │
    ───────►│  allocation, many die young  │
            └────────────┬─────────────────┘
                         │ Young GC / evacuation
                  ┌──────┴──────────┐
             unreachable        reachable
                  │                  │
                  ▼                  ▼
              discard         SURVIVOR  (age tăng)
                                     │
                              còn trẻ / đủ chỗ
                                     │ tiếp tục Young GC
                                     ▼
                                  SURVIVOR
                                     │
                     đủ tuổi hoặc cần promote theo policy
                                     ▼
                            OLD GENERATION
                          (vẫn chịu GC khi cần)
```

Đây là **sơ đồ hành vi khái niệm**, không phải một bảo đảm đối tượng luôn tuần tự đi đúng `Eden → Survivor 1 → Survivor 2 → Old`. Có trường hợp đối tượng được promoted sớm hoặc vào old theo đường cấp phát đặc biệt.

### 2.1. Có phải mọi object Java đều cấp phát vào Eden?

**Không.** “Thông thường object được tạo trong Eden” là quy tắc mô hình, không phải hợp đồng áp dụng cho mọi collector và mọi allocation. Với HotSpot G1, đối tượng **humongous** được cấp phát trực tiếp vào các **old/humongous regions**. Ngoài ra, JIT có thể **scalar-replace** một số allocations nếu chứng minh object không escape; khi ấy source có `new` nhưng không nhất thiết có một heap object độc lập cần GC.

Không suy nơi cấp phát từ việc thấy từ khóa `new` trong Java source. Cần kiểm tra GC implementation, kích thước object, escape analysis và hoạt động runtime.

## 3. Eden: vùng tạo object và áp lực cấp phát

Nhiều allocations của Java threads trong HotSpot được tối ưu bằng **TLAB — Thread-Local Allocation Buffer**. Mỗi thread được cấp một buffer cấp phát nhỏ trong không gian young/eden phù hợp. Khi còn đủ chỗ, cấp phát object thường chỉ cần tăng một con trỏ, tránh phải khóa toàn bộ heap cho từng allocation.

```text
Heap young / Eden capacity
    ├─ TLAB của thread A: allocations nhanh của A
    ├─ TLAB của thread B: allocations nhanh của B
    └─ không gian bổ sung / slow-path allocations

Khi TLAB hết:
    thread thử refill/new TLAB hoặc allocation slow path;
    nếu không đủ vùng young thích hợp, JVM có thể cần GC.
```

**TLAB không phải một thế hệ GC riêng**, mà là cơ chế **tối ưu allocation**. Một object cấp phát vào TLAB không có “GC riêng cho thread A”. Các đối tượng vẫn thuộc các vùng của heap và được quản lý theo collector.

### 3.1. Eden đầy không đồng nghĩa toàn bộ heap hết bộ nhớ

Khi Eden/young allocation space bị tiêu thụ, collector có thể làm một **Young GC** để thu hồi rác trẻ và tạo thêm khả năng cấp phát. Nếu vẫn còn nhiều vùng old trống hoặc heap còn dư, việc Young GC xảy ra là bình thường, **không phải tự nó là memory leak hay OutOfMemoryError**.

Nếu application tạo nhiều object ngắn hạn, số lần Young GC có thể tăng. Điều cần đo là allocation rate, live set, pause times, throughput, old occupancy và SLA, không chỉ đếm số GC.

### 3.2. Có phải Eden cứ đạt đúng 100% là GC lập tức xảy ra?

Không nên suy một ngưỡng cố định. Young generation size có thể được collector điều chỉnh, TLAB refill và các allocation paths có chi phí/điều kiện khác nhau. G1 còn dùng **adaptive sizing** với mục tiêu pause time và capacity. Công thức “Eden 100% → một loại sự kiện duy nhất” chỉ hữu ích ở sơ đồ nhập môn, không phải một state machine tuyệt đối cho mọi tình huống HotSpot.

## 4. Survivor spaces: lưu object sống qua Young GC

Một Young GC cần phân biệt:

1. Object trong Eden/Survivor không còn reachable: thu hồi cùng source regions.
2. Object vẫn reachable nhưng chưa cần promoted: **copy/evacuate** sang Survivor regions mới.
3. Object vẫn reachable và đủ tuổi hoặc không phù hợp giữ trong survivor set: **promote** sang old regions theo policy.

Trong collector copying truyền thống, sách thường vẽ **Survivor S0 và S1** hay **From/To**: một vùng đóng vai trò nguồn, vùng kia nhận những object được copy; sau collection, vai trò hoán đổi.

```text
MÔ HÌNH HAI SURVIVOR SPACES (minh họa kinh điển)
Trước Young GC:
     Eden:     [A live][B dead][C live]
     From S0:  [D live][E dead]
     To S1:    [trống]

Young GC:
     A, C, D → To S1 nếu còn đủ điều kiện
     B, E    → garbage; không được copy
     Một số object sống đủ già → Old

Sau Young GC:
     From S0/Eden cũ được tái sử dụng
     S1 đóng vai trò survivor nguồn cho chu kỳ sau
```

**Cẩn thận với G1:** mô hình `S0/S1` giúp hiểu copying, nhưng G1 dùng **một tập survivor heap regions thay đổi theo từng collection**, không bắt buộc là đúng hai “khối không gian Survivor cố định” nằm kề nhau. Trên G1, tập young regions gồm Eden regions và Survivor regions; collector evacuate object sống vào những **new survivor regions** hoặc **old regions**.

### 4.1. Survivor chứa object sống, không chứa “object sắp chết”

Một object có thể ở Survivor vì nó **vẫn live** sau một đợt young collection. Không phải JVM biết trước object đó còn sống thêm bao lâu. Nếu không còn reachable trước collection kế tiếp, object sẽ không được copy nữa và dung lượng vùng chứa nó được thu hồi.

### 4.2. Survivor có cần hai vùng bằng nhau?

Không có quy tắc bắt buộc xuyên mọi collector. Ở sơ đồ copying đời cũ, From/To survivor spaces có thể được cấu hình theo tỷ lệ nhất định; với G1, số **regions** được dùng cho Eden và Survivor được điều chỉnh theo collector heuristics. Tuning flags như `SurvivorRatio`, `NewRatio` trong các tài liệu collectors khác **không nên áp dụng máy móc vào G1** như thể nó duy trì hai fixed semispaces giống nhau.

## 5. Age, tenuring threshold và promotion vào Old

Một object vừa tạo không có “đồng hồ Java” đo bao nhiêu giây để được chuyển vào Old. Generational collector thường theo dõi **tuổi sống sót qua young evacuations**.

Timeline lý tưởng:

```text
time t0: tạo Object X → Eden
young GC #1: X live → Survivor, tuổi tăng
young GC #2: X live → Survivor mới, tuổi tăng
young GC #3: X live → có thể promote vào Old
... nếu X trở nên unreachable, Old GC sẽ thu hồi về sau ...
```

Dòng “#3” ở sơ đồ là **giả định để học**, không phải giá trị threshold cố định của Java 17. G1 quyết định threshold và điểm đích tùy adaptive policy, survivor capacity, tuổi và các ràng buộc GC.

### 5.1. Tenuring threshold là gì?

**Tenuring** mô tả việc một object sống qua đủ young collections/đạt điều kiện để không ở survivor nữa. **Tenuring threshold** là ngưỡng tuổi mà collector dùng để quyết định promotion trong điều kiện phù hợp.

- **Maximum tenuring threshold** là mức giới hạn mà một số HotSpot collectors cho phép cấu hình qua `-XX:MaxTenuringThreshold`.
- **Desired/effective tenuring threshold** có thể **thay đổi theo workload** vì collector muốn khống chế lượng live bytes cần chứa ở Survivor.
- Nếu survivor capacity không đủ, object có thể được **promoted sớm hơn** ngưỡng tưởng tượng.
- Những “age” trong diagnostic logs là **GC age**, không phải số giây tuổi đời object hoặc một Java field công khai.

**Không có Java API chuẩn `getObjectGeneration(object)`** để hỏi chính xác object hiện ở Eden, Survivor hay Old.

### 5.2. Promotion không biến object thành GC root

Khi object chuyển từ Survivor sang Old:

- Identity theo Java (`==`, `equals`, `System.identityHashCode` semantics) phải tiếp tục đúng.
- Program references được cập nhật khi collector di chuyển/copy objects; code Java không được tự sửa pointers thủ công.
- Object vẫn có thể trở nên unreachable và được collector thu hồi về sau.
- Promotion là thay đổi **vùng quản lý heap**, không phải lời cam kết ứng dụng sẽ giữ object đó mãi.

### 5.3. Survivor overflow và premature promotion

Một ứng dụng có tỷ lệ đối tượng trẻ sống sót rất cao có thể tạo áp lực lên Survivor. Khi capacity survivor không đủ so với live set, collector có thể promote objects sớm.

Hệ quả: **old occupancy tăng nhanh** dù object mới tạo chưa sống lâu theo thời gian thực. Đây không nhất thiết là memory leak: có thể do allocation bursts, object retention tạm thời, survivor sizing hoặc cache giữ references lâu hơn dự kiến.

Chẩn đoán cần đặt các chỉ số song song: bytes promoted, survivor usage, old occupancy qua nhiều chu kỳ và object lifetime/retained references.

## 6. Old generation: lưu objects sống lâu nhưng vẫn thu gom

Old là nơi thường chứa các objects đã được promoted hoặc được cấp phát trực tiếp theo policy. **Old không đồng nghĩa “Permanent Generation” và cũng không phải Metaspace**.

| Khái niệm | Chứa gì? | GC có liên quan không? |
| --- | --- | --- |
| **Old generation** | Java heap objects trong những vùng thuộc old | **Có**; collector thực hiện marking/reclamation/evacuation tương ứng |
| **Metaspace** (HotSpot) | Class metadata trong native memory | Cơ chế quản lý/unloading riêng, không phải heap old |
| **Java static field** | Field thuộc class; giá trị reference có thể trỏ tới heap object | Object đích nằm trong heap theo chính sách collector; **không tự nằm ở old chỉ vì field static** |
| **GC roots** | Các nguồn root reachability | Không phải mọi GC root đều là heap object; roots giúp xác định objects live |

Ví dụ một `static List<byte[]> CACHE` có thể giữ các arrays sống qua nhiều young collections và cuối cùng chúng được promoted. Khi `CACHE.clear()` và không còn refs khác, chúng có thể trở thành garbage **dù đang ở old**.

### 6.1. Thu gom Old có nhất thiết là Full GC?

**Không.** Trong G1, có:

- Young-only evacuations chỉ tập trung young regions.
- **Mixed collections** xử lý young cùng **một tập old regions được chọn** sau khi có thông tin concurrent marking.
- **Full GC** là cơ chế tốn kém hơn, thường liên quan các tình huống không thể đáp ứng phân bổ/evacuation bằng quy trình thông thường; không phải mọi lần reclaim old đều là Full GC.

Không dùng “Major GC = Full GC = Mixed GC” như ba tên đồng nghĩa. Những thuật ngữ **minor/major** trong sách cũ không ánh xạ một-một vào toàn bộ pha GC của G1.

## 7. HotSpot G1: Eden/Survivor/Old là vai trò của heap REGIONS

**G1 (Garbage-First)** chia Java heap thành nhiều **regions** thường cùng kích cỡ trong một cấu hình. Các regions có thể được gán vai trò Eden, Survivor, Old, Humongous hoặc Free. Những regions thuộc cùng vai trò **không cần nằm liên tục cạnh nhau** như một dải young cố định.

```text
G1 heap gồm nhiều regions (sơ đồ minh họa, KHÔNG theo địa chỉ thật):
┌──────┬──────┬──────┬──────┬──────┬──────┬──────┐
│Eden  │Old   │Free  │Eden  │Surv. │Old   │Eden  │
├──────┼──────┼──────┼──────┼──────┼──────┼──────┤
│Humong│Humong│Old   │Free  │Surv. │Eden  │Free  │
├──────┼──────┼──────┼──────┼──────┼──────┼──────┤
│Old   │Eden  │Free  │Old   │Eden  │Free  │Old   │
└──────┴──────┴──────┴──────┴──────┴──────┴──────┘

Young = tập Eden regions + Survivor regions.
Old   = tập Old regions (có cả cách quản lý humongous riêng).
Mỗi collection có thể thay đổi số regions và vai trò của chúng.
```

### 7.1. Young GC trong G1 thực sự làm gì?

Một **G1 Young GC** điển hình (stop-the-world evacuation pause):

1. JVM xác định tập regions cần thu gom (**collection set**, thường viết tắt `CSet`): các Eden regions và Survivor regions trước đó.
2. Tìm live objects trong tập đó, bao gồm việc xét refs từ roots **và references đi vào từ vùng ngoài CSet** theo remembered sets.
3. Evacuate/copy những object trẻ còn sống sang **new Survivor regions** hoặc **Old regions** dựa trên tuổi/collector policy.
4. Cập nhật các references thích hợp đến object mới được chuyển.
5. Toàn bộ source regions không còn chứa live objects cần giữ có thể được đưa về **Free** và cấp phát lại.
6. Collector điều chỉnh young size, survivor destinations và các chỉ tiêu pause cho những lần kế tiếp.

Với mô hình region-based này, việc thu gom young không cần “quét từng byte của toàn bộ Old” ở mỗi pause để tìm các tham chiếu cũ tới trẻ; G1 dùng remembered sets và write barriers để quản lý thông tin cross-region references. Điều đó là một phần quan trọng để generational collection an toàn.

### 7.2. Một object từ Old trỏ tới Young thì sao?

Ví dụ:

```java
static final java.util.List<Object> ROOTS =
        new java.util.ArrayList<>();

static void remember(Object obj) {
    ROOTS.add(obj);
}
```

Nếu `ROOTS` đã sống trong old và code ghi reference tới một object mới ở Eden, object trẻ **vẫn reachable** qua đường từ Old → Young. Young GC **phải tính tới reference này**, nếu không sẽ thu hồi sai object còn đang được ứng dụng dùng.

Các generational collectors áp dụng **write barriers**, card tables hoặc remembered-set mechanisms theo collector để nhận biết các references cần xét. **Barriers không phải `synchronized` hoặc CPU memory barrier trong Java source do developer phải gọi thủ công**; đây là công việc JVM/GC runtime phối hợp với compiled/interpreted code.

### 7.3. Young size trong G1 có cố định không?

G1 điều chỉnh số regions trong young generation theo pause target, live-data feedback và heap pressure. Điều chỉnh Eden/Survivor capacity giúp cân đối hai mục tiêu:

- Young generation quá nhỏ → young collections thường xuyên, throughput có thể giảm.
- Young generation quá lớn → một đợt evacuation có thể copy nhiều live bytes hơn, tăng pause duration và tốn temporary destination space.

Không có một tỷ lệ “Eden luôn 80% và Survivor mỗi bên 10%” bắt buộc trong G1 Java 17. Các tỷ lệ 8:1:1 phổ biến trong giáo trình chỉ là **ví dụ minh họa cho một số thiết lập/collectors cũ**, không phải hợp đồng của JVM.

## 8. G1 Mixed GC, concurrent marking và thu hồi old regions

Old objects vẫn cần được xem xét khi không còn reachable. G1 phối hợp nhiều pha:

```text
Young-only phase:
  Young GC → Young GC → ... 
              │
              ▼
Concurrent marking cycle (có các pause như initial mark/remark/cleanup)
              │
              ▼
Thu được thông tin live bytes / ứng viên old regions
              │
              ▼
Space-reclamation phase:
  Mixed GC #1: Young + một số Old regions
  Mixed GC #2: Young + một số Old regions
  ...
              │
              ▼
Quay về Young-only phase khi đã xử lý đủ candidate regions
```

**Concurrent marking không đồng nghĩa “old objects được copy ngay khi marking thread quét qua”**. Marking thu thập thông tin liveness cho việc chọn regions, còn evacuation/reclamation diễn ra ở những pha phù hợp. “Concurrent” cũng không có nghĩa toàn bộ G1 không có stop-the-world pauses: G1 vẫn có STW young/mixed evacuations và những pause đánh dấu cụ thể.

### 8.1. Vì sao Mixed GC gồm cả Young và Old?

G1 chọn thêm **một số Old regions đáng thu hồi** vào CSet của một collection đang xử lý Young. Nhờ vậy nó có thể thu hồi old incrementally thay vì buộc mỗi lần phải compact toàn bộ heap trong một Full GC.

Không phải mỗi mixed collection phải thu hồi toàn bộ Old. Chọn ít hay nhiều candidate regions còn liên quan pause target, expected reclaim và policies.

### 8.2. Full GC xuất hiện khi nào?

Trong HotSpot G1, **Full GC** thường là đường tốn kém hơn khi các cơ chế young/mixed/concurrent thông thường không còn tạo đủ tiến triển để đáp ứng allocation. Nó có thể gây pause lớn; cần xem **GC cause/logs** để phân biệt allocation failure, evacuation failure hoặc các điều kiện liên quan.

Một dòng “Old occupancy tăng” **không chứng minh Full GC chắc chắn xảy ra ngay**. Old occupancy là tín hiệu, không phải một câu lệnh GC bắt buộc.

## 9. Humongous objects: ngoại lệ quan trọng của con đường Eden → Survivor → Old

Với G1 Java 17, một object được coi là **humongous** khi kích thước của nó **từ một nửa region trở lên** theo định nghĩa tài liệu G1 (dựa trên region size). Những object này có đường allocation riêng, trực tiếp sử dụng các **humongous regions thuộc old generation**.

```text
Object bình thường, kích thước nhỏ:
    Eden → Survivor → Old (nếu sống/đủ điều kiện)

Object humongous, ví dụ big byte[]:
    cấp phát trực tiếp trong contiguous Humongous regions
    thuộc Old generation
    không bắt buộc đi qua Eden và Survivor
```

Hệ quả:

- Một object ở old **không nhất thiết đã sống qua nhiều Young GC**. Nó có thể được cấp phát trực tiếp theo kích cỡ.
- Humongous objects thường dùng nhiều regions liên tục, và dung lượng dư ở region cuối có thể không được dùng cho object khác cho tới khi reclaim.
- G1 có các đường thu hồi humongous objects tùy loại/điều kiện, bao gồm các cơ chế reclaim opportunistic và các pha marking/cleanup; không nên khẳng định “chỉ Full GC mới thu hồi humongous”.
- GC activity/old occupancy có thể tăng bất ngờ vì **mảng lớn** dù cache data không tồn tại lâu.

Ví dụ `new byte[10_000_000]` **có thể** là humongous tùy region size, heap/VM settings. Đừng khẳng định một kích cỡ cố định như “mọi mảng 1 MB đều là humongous”: ngưỡng là tương đối với **G1HeapRegionSize**. Object headers/alignment cũng ảnh hưởng kích thước tổng của object.

### 9.1. Humongous không đồng nghĩa Large Object Heap của .NET

Các nền tảng khác có cách quản lý large objects khác nhau. Trong Java G1, khái niệm humongous phụ thuộc region size và G1 collector policy. Không đem một ngưỡng của CLR hoặc thế hệ VM khác áp dụng trực tiếp vào HotSpot.

## 10. Từ mô hình tới số liệu: allocation rate, survival rate và promotion rate

Khi chẩn đoán generational GC, nên tách ba câu hỏi:

1. **Allocation rate:** ứng dụng tạo bao nhiêu bytes mới mỗi giây?
2. **Survival rate:** bao nhiêu bytes ở Young còn live sau collection?
3. **Promotion rate:** bao nhiêu bytes được chuyển sang Old qua các collections?

| Hiện tượng | Dấu hiệu hay gặp | Điều cần điều tra |
| --- | --- | --- |
| High allocation, low survival | Young collections thường xuyên; Eden giảm mạnh sau GC | Tạo nhiều đối tượng tạm, boxing, serialization, buffer copy |
| High survival | Survivor/Old destinations nhận nhiều live bytes | Live object graph, caches, queues, transaction lifetime |
| Premature promotion | Old usage tăng dù objects chưa tồn tại lâu trong workload | Survivor pressure, tenuring threshold, burst retention |
| Old không giảm qua mixed cycles | Long-lived live set hoặc GC chưa reclaim đủ | Retained references, heap size, marking, candidate reclaim |
| Humongous pressure | Old/humongous region usage tăng | Large arrays/strings, region size, data shape |
| Full GC hoặc evacuation failure | Pause lớn/không đủ evacuation destinations | Heap headroom, fragmentation, reserve, live set, workload |

**Đừng tối ưu chỉ bằng một con số “GC count”.** Một ứng dụng có nhiều Young GCs ngắn có thể đạt p99 latency tốt hơn ứng dụng có ít GCs nhưng pause dài. Cần đọc logs và kết quả latency/throughput của workload thật.

### 10.1. Allocation ≠ retained heap

Nếu code tạo 1 triệu objects nhỏ rồi mất hết references, tổng allocations có thể lớn trong khi heap live set sau GC rất nhỏ. Ngược lại, ít allocations nhưng một map toàn cục giữ vài GB dữ liệu có thể làm old occupancy cao.

Trình quản lý memory kiểu GC theo thế hệ dựa mạnh vào chênh lệch giữa **allocated bytes** và **live bytes**: phần không còn live trong Eden có thể tái sử dụng bằng cách giữ lại chỉ những object còn sống.

## 11. Chương trình Java 17: tạo allocation churn và giữ một tập object nhỏ

Tạo file **`GenerationalGcDemo.java`**:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenerationalGcDemo {
    private static volatile List<byte[]> keepAlive;

    public static void main(String[] args) {
        Set<String> poolNames = new HashSet<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            poolNames.add(pool.getName());
        }

        System.out.println("g1EdenPool=" + poolNames.contains("G1 Eden Space"));
        System.out.println("g1SurvivorPool=" +
                poolNames.contains("G1 Survivor Space"));
        System.out.println("g1OldPool=" + poolNames.contains("G1 Old Gen"));

        List<byte[]> retained = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 100_000; i++) {
            byte[] block = new byte[2048];
            block[0] = (byte) (i & 127);
            checksum += block[0] & 0xff;
            if (i % 5000 == 0) {
                retained.add(block);
            }
        }

        keepAlive = retained;
        System.out.println("retained=" + retained.size());
        System.out.println("checksumPositive=" + (checksum > 0));
        System.out.println("done");
    }
}
```

Biên dịch và chạy với **HotSpot Java 17 G1**:

```bash
javac --release 17 GenerationalGcDemo.java
java -XX:+UseG1GC -Xms64m -Xmx64m GenerationalGcDemo
```

**Stdout của mã ứng dụng trong môi trường HotSpot JDK 17 G1:**

```text
g1EdenPool=true
g1SurvivorPool=true
g1OldPool=true
retained=20
checksumPositive=true
done
```

### 11.1. Mã này chứng minh điều gì?

- Với **HotSpot JDK 17 G1**, `MemoryPoolMXBean` thường công bố pool names `G1 Eden Space`, `G1 Survivor Space`, `G1 Old Gen`. Đây là tên **MXBean thuộc implementation**, không phải API requirement cho mọi JVM, collector hay phiên bản.
- Vòng lặp tạo 100.000 mảng `byte[2048]` ở Java source. Collector/JIT quyết định allocation path thực tế; không dùng vòng lặp để suy mỗi `new` chắc chắn tương ứng một vật thể cuối cùng trong heap.
- `retained` giữ **20 mảng** do điều kiện `i % 5000 == 0`; các mảng còn lại không được lưu ở collection đó. Compiler/JIT và các temporary references có thể ảnh hưởng liveness, nhưng có xu hướng tạo lượng lớn dữ liệu tạm.
- `checksumPositive=true` là phép kiểm tra kết quả độc lập với lịch GC, không đòi một số lần collection cụ thể.
- **Chương trình không in “Eden→Survivor→Old” cho từng object**; Java public API không cung cấp mapping generation per object.

### 11.2. Tại sao output không được ghi số lần Young GC?

GC có thể chạy nhiều/lần khác nhau tùy JDK build, heap sizing, CPU, JIT, TLAB và hệ điều hành. Nếu chúng ta in `MemoryPoolMXBean.getUsage()` hay `GarbageCollectorMXBean.getCollectionCount()`, số liệu không có stdout cố định. Bài này intentionally chỉ đưa những dòng có thể suy từ code và tên memory pools trong môi trường HotSpot G1 được chỉ định.

Ở collector khác, ba dòng `g1...Pool` có thể là `false`. Điều này **không** phủ định mô hình generational GC; chỉ cho thấy pool management interface khác nhau.

## 12. Quan sát Eden/Survivor/Old bằng GC logs trên HotSpot JDK 17

Dùng **Unified Logging** của HotSpot:

```bash
# Logging nhiều thông tin; output sẽ gồm GC logs và stdout ứng dụng.
java -XX:+UseG1GC -Xms64m -Xmx64m \
  -Xlog:gc*,gc+age=trace,gc+heap=debug \
  GenerationalGcDemo
```

Lệnh viết theo cú pháp shell POSIX; trên Windows PowerShell có thể viết **một dòng**, bỏ dấu `\` cuối dòng:

```text
java -XX:+UseG1GC -Xms64m -Xmx64m -Xlog:gc*,gc+age=trace,gc+heap=debug GenerationalGcDemo
```

**Không có một stdout/stderr GC log cố định**. Các tag/tên trường và số liệu phụ thuộc JDK build, GC activity, heap size, lịch thực thi và log level. Có thể xuất hiện những thông tin kiểu:

```text
[info ][gc,heap] ... Eden regions: N->0(...)
[info ][gc,heap] ... Survivor regions: A->B(...)
[info ][gc,heap] ... Old regions: X->Y
[trace][gc,age ] ... age 1: ... bytes
```

Đây là **mẫu cách đọc log, không phải output thực tế của một lần chạy cụ thể**. Giá trị N, A, B, X, Y đều là ký hiệu minh họa. Đọc log theo **gc id** và thời điểm trước/sau, không ghép thông tin từ những GC events khác nhau thành một lần tưởng tượng.

### 12.1. Diễn giải một event Young GC

Nếu log ghi một event có ý nghĩa:

```text
Eden regions:       20->0
Survivor regions:    2->3
Old regions:        10->12
```

Đây là **ví dụ số liệu giả lập để luyện đọc**, không phải bảo đảm output của G1:

- Eden source regions đã được thu gom, thường về số lượng 0 trong old eden-set sau evacuation.
- Survivor destination-set dùng 3 regions ở cuối event; không thể suy “3 regions = 3 objects”.
- Old tăng từ 10 lên 12 regions có thể liên quan promotion/allocations và chính sách region, **không tự chứng minh memory leak**.
- Phải kết hợp event type, region size, live bytes/heap occupancy, thời điểm và allocation rate mới hiểu tác động tổng thể.

### 12.2. `gc+age=trace` nói gì?

Age table có thể thể hiện số bytes objects theo **tuổi GC** và thông tin desired survivor size/new threshold trong những bản HotSpot phù hợp. Ví dụ ý tưởng:

```text
age 1: ... bytes
age 2: ... bytes
new threshold ... 
```

**Age 2 không nghĩa object có tuổi đời hai giây.** Nó phản ánh việc object sống qua những lần young processing phù hợp theo collector. Log age table còn không phải thống kê tuổi cho **mọi old object** trong toàn heap. Tùy flags/version, một số dòng không xuất hiện nếu không có event hoặc level phù hợp.

### 12.3. Ghi GC log ra file thay vì màn hình

```bash
java -XX:+UseG1GC -Xms64m -Xmx64m \
  -Xlog:gc*,gc+age=trace,gc+heap=debug:file=gc.log:time,uptime,level,tags \
  GenerationalGcDemo
```

Đây là syntax HotSpot Unified Logging trên Java 17: selector, output file và decorators. File `gc.log` được ghi ở thư mục làm việc; quản lý retention/log rotation của môi trường production cần cấu hình riêng. Đừng để `trace` logs dung lượng lớn chạy không giới hạn ở server bận.

### 12.4. So với tool `jstat`

HotSpot có `jstat -gc <pid> 1000` để quan sát thống kê GC/pool capacity/usage của **process đang chạy**. Nhưng demo ở mục 11 có thể hoàn tất nhanh nên không kịp lấy mẫu. Nếu muốn giám sát thật, dùng process/server sống đủ lâu; không kéo dài demo tùy tiện bằng sleep rồi tự cho rằng GC chắc chắn xảy ra.

Một số cột `jstat` được đặt tên theo generations cũ; cách diễn giải phải gắn với collector và version. Trong G1 nên ưu tiên GC logs và memory pool metrics đúng collector.

## 13. Đọc đúng “Minor”, “Major”, “Young”, “Mixed”, “Full”

Các tài liệu Java GC thường dùng từ:

| Thuật ngữ | Cách hiểu đúng trong bài |
| --- | --- |
| **Minor GC** | Thu gom young trong cách gọi phổ biến/truyền thống; không phải mọi collector dùng đúng tên event này |
| **Young GC** | Trong G1, young-only evacuation của Eden/Survivor regions |
| **Mixed GC** | Trong G1, collection có Young và một số Old regions phù hợp |
| **Major GC** | Thuật ngữ không thống nhất giữa collectors/tài liệu; cần xác định GC log event thực |
| **Full GC** | Một kiểu collection xử lý heap rộng hơn, thường có mức pause và cách thực hiện khác young/mixed; không đồng nhất với mixed |
| **Concurrent mark** | Pha tìm liveness, có phần chạy đồng thời; không có nghĩa thu hồi/copy mọi old region ngay |

Không rút gọn G1 thành “Minor GC chỉ trẻ, Major GC quét già mỗi khi Old đầy” như thể có đúng hai nút bấm. G1 thường xen kẽ Young GC, concurrent marking, mixed reclamation, và chỉ dùng Full GC trong một số điều kiện.

## 14. Tuning: ưu tiên hiểu collector trước khi chỉnh thế hệ

G1 được thiết kế để tự điều chỉnh nhiều thông số, đặc biệt là young generation size, nhằm đáp ứng **pause time goal** và cân bằng throughput.

Những knobs phổ biến trong HotSpot Java 17:

- `-XX:+UseG1GC`: chọn G1 khi muốn điều kiện thử nghiệm rõ ràng.
- `-Xms` / `-Xmx`: initial/maximum Java heap; ảnh hưởng headroom và tần suất GC.
- `-XX:MaxGCPauseMillis=<ms>`: **mục tiêu pause**, không phải hard deadline.
- `-XX:MaxTenuringThreshold=<n>`: giới hạn tuổi dùng trong chính sách tenuring khi phù hợp; không buộc mọi object “ở Survivor n lần rồi vào Old”.
- `-XX:G1HeapRegionSize=<bytes>`: thay region size, ảnh hưởng số regions và ngưỡng humongous; không chỉnh bừa để chữa mọi GC problem.

**Đừng copy blind** một đoạn tuning cũ cho Serial/Parallel GC rồi áp vào G1. Chẳng hạn hình ảnh `Eden:S0:S1 = 8:1:1` chỉ là biểu diễn của một số collectors/thiết lập và có thể không phản ánh young region sizing adaptive của G1.

### 14.1. Cách thử nghiệm khi old tăng nhanh

1. Chụp cấu hình thực tế: JDK vendor/version, `java -version`, collector, heap size, CPU và GC logs.
2. Đo allocation throughput và tỷ lệ sống trẻ qua age/survivor information.
3. Kiểm tra promotion pressure và thời gian giữ references trong cache, queue, thread-local hoặc listeners.
4. Xem old occupancy **sau nhiều collections**, không đọc một snapshot đơn lẻ.
5. Nếu có humongous pressure, kiểm tra kích thước arrays và region sizes trước khi chỉnh `G1HeapRegionSize`.
6. Đối chiếu pause time (p95/p99), application throughput và CPU sử dụng.
7. Chỉ thay **một** nhóm tham số mỗi thí nghiệm rồi chạy lại cùng workload; ghi rõ kết quả.
8. Ưu tiên loại bỏ retention không cần thiết và allocation bùng nổ trong code trước khi ép các ngưỡng tenuring.

### 14.2. Khi nào old generation tăng là bình thường?

Sau startup hoặc warmup, những structures được khởi tạo một lần rồi giữ lâu (caches, class data structures, connection pools, compiled-code-related references) có thể làm old live set tăng và **ổn định**. Điều quan trọng là old retained memory có tăng không giới hạn theo traffic hay có đạt plateau.

Một đường old usage hình răng cưa với nhiều mixed GC không tự là leak. Cần phân biệt **lượng memory được committed/occupied** với **lượng live bytes không thể giải phóng**.

## 15. Không thể suy object age từ source code

Hai snippets:

```java
static byte[] permanentlyHeld = new byte[1024];

static void temporary() {
    byte[] tmp = new byte[1024];
    System.out.println(tmp.length);
}
```

Đều có `new byte[1024]`, nhưng:

- `permanentlyHeld` có static strong reference, có thể sống qua nhiều Young GCs và được promoted.
- `tmp` không còn needed sau method, có thể chết sớm hoặc JIT có thể tối ưu tùy phép sử dụng.
- Không có bảo đảm `permanentlyHeld` bắt đầu ở Eden trên mọi VM/GC/optimization.
- Nếu object static bị thay/xóa và không còn references khác, nó vẫn có thể trở thành garbage dù đã vào Old.

Điểm cốt lõi là **liveness và allocation path** của runtime chứ không phải tên biến.

### 15.1. Object được promoted rồi có thể quay lại Eden không?

Trong mô hình generational phổ biến, object đã ở Old **không được “trẻ hóa” trở lại Eden** chỉ vì ứng dụng cập nhật field hoặc tạo reference mới tới nó. Hãy coi promotion là một bước chuyển vùng quản lý đối tượng, không phải đếm ngược tuổi khi hết thời gian.

Tuy vậy, chi tiết di chuyển objects có thể khác giữa collectors. Không biến câu này thành một quy tắc ABI chung về địa chỉ object trên Java heap.

## 16. Các lỗi hiểu sai thường gặp

1. **“Young generation luôn chiếm đúng 1/3 heap.”** Sai; sizing tùy collector/config và G1 thường adaptive.
2. **“Eden, S0, S1 và Old luôn là bốn dải địa chỉ liên tục cố định.”** Sai với G1 region model.
3. **“Eden đầy thì chắc chắn OutOfMemoryError.”** Sai; Young GC có thể giải phóng space để cấp phát tiếp.
4. **“Mỗi object phải đi qua Survivor đúng hai lần trước khi Old.”** Sai; age threshold và survivor capacity thay đổi.
5. **“Age 3 nghĩa object đã sống ba giây.”** Sai; age liên quan số lần sống qua young processing.
6. **“Có strong reference thì object phải ở Old ngay.”** Sai; reachability và generation location khác nhau.
7. **“Object ở Old không bao giờ được GC.”** Sai; G1 mixed/other collection xử lý old.
8. **“Mixed GC chính là Full GC.”** Sai; mixed là evacuation young + một tập old regions.
9. **“Concurrent marking nghĩa toàn bộ GC không có pause.”** Sai; G1 vẫn có pauses STW.
10. **“Mọi object đều cấp phát vào Eden.”** Sai; G1 humongous objects vào old regions, JIT có thể loại bỏ allocation.
11. **“Một object ở Old chắc chắn đã rất già.”** Sai; có đường trực tiếp vào old.
12. **“Old tăng sau một GC = memory leak.”** Sai; có thể là promotion bình thường.
13. **“Số lần GC càng ít càng tốt.”** Không nhất thiết; phải cân bằng pause, throughput, live set và SLA.
14. **“Minor, Major và Full GC là thuật ngữ chuẩn giống nhau trên mọi VM.”** Sai; xem log và collector thực.
15. **“Tăng MaxTenuringThreshold luôn tốt hơn.”** Sai; giữ objects quá lâu ở survivor cũng tốn copy và pause.
16. **“Đặt survivor quá bé không có tác dụng phụ.”** Sai; có thể dẫn đến premature promotion và old pressure.
17. **“Object ở Survivor chắc chắn sắp chết.”** Sai; nó đang live tại thời điểm collection.
18. **“TLAB là một thế hệ mới giữa Eden và Survivor.”** Sai; TLAB là allocation optimization.
19. **“GC logs nói Eden regions=30 nghĩa 30 object.”** Sai; một region chứa nhiều objects.
20. **“Survivor region count tăng luôn chứng minh leak.”** Sai; có thể do current live set và adaptive sizing.
21. **“Static field luôn đặt object trong Old.”** Sai; reference origin không quy định chỗ cấp phát object.
22. **“Humongous object phải qua Eden rồi mới thăng cấp.”** Sai với G1.
23. **“G1HeapRegionSize không ảnh hưởng large object behavior.”** Sai; nó ảnh hưởng humongous threshold.
24. **“Bật `-Xlog:gc*=debug` sẽ tạo stdout giống hệt mọi máy.”** Sai; logs phụ thuộc JDK và workload.

## 17. Tự kiểm tra có đáp án

**Câu 1.** Tại sao GC theo thế hệ thường xử lý Eden thường xuyên hơn Old?

**Đáp án:** Nhiều objects mới tạo chết sớm, nên thu gom young có thể lấy lại nhiều dung lượng với chi phí quét/copy live objects thấp hơn so với xử lý mọi old data ở mỗi event.

**Câu 2.** Một object ở Eden còn reachable qua static list khi Young GC xảy ra. Điều gì có thể xảy ra?

**Đáp án:** Nó được giữ lại bằng evacuation/copy, thường sang Survivor hoặc Old theo age/policy. Không bị xóa chỉ vì nằm trong Eden.

**Câu 3.** Age của object từ 1 lên 2 nghĩa là gì?

**Đáp án:** Trong mô hình young copying, nó đã sống qua thêm một lần xử lý/evacuation young phù hợp; không phải tự cộng một giây tuổi đời.

**Câu 4.** Survivor không đủ chỗ cho object còn live thì sao?

**Đáp án:** Collector có thể promote sớm vào Old hoặc áp dụng cơ chế khác theo policy, nếu có đủ vùng đích phù hợp.

**Câu 5.** G1 có đúng hai Survivor memory blocks cố định không?

**Đáp án:** Không. G1 quản lý survivor **regions** thuộc young set, có thể thay đổi số lượng/vị trí theo cycles. S0/S1 là sơ đồ kinh điển giúp hiểu việc copying.

**Câu 6.** Vì sao G1 cần remembered sets/write barriers?

**Đáp án:** Young GC phải thấy các references từ những regions **ngoài CSet** vào những objects trong CSet để không thu hồi nhầm objects vẫn reachable, ví dụ Old → Young.

**Câu 7.** Mixed GC khác Full GC thế nào?

**Đáp án:** G1 mixed thu gom young và **một số old regions** dựa trên selection, trong khi Full GC là đường thu gom rộng/tốn kém hơn và không đồng nghĩa với mixed.

**Câu 8.** Vì sao một mảng `byte[]` lớn có thể vào Old ngay?

**Đáp án:** Nếu được G1 coi là **humongous** theo kích thước tương đối với region (từ nửa region trở lên), nó dùng humongous regions thuộc old generation.

**Câu 9.** `-XX:MaxGCPauseMillis=100` có bảo đảm mọi GC dưới 100 ms không?

**Đáp án:** Không. Đây là mục tiêu để G1 heuristic điều chỉnh collection set/young size, không phải SLA cứng.

**Câu 10.** Vì sao `GenerationalGcDemo` chỉ in 20 retained arrays dù loop chạy 100.000 lần?

**Đáp án:** Điều kiện `i % 5000 == 0` đúng cho `i = 0, 5000, ..., 95000`, tức 20 lần. Các arrays còn lại không được thêm vào `retained`.

**Câu 11.** Sau khi loại hết references tới cache objects trong Old, chúng có bị thu hồi ngay không?

**Đáp án:** Không có bảo đảm ngay lập tức; chúng có thể được reclaim trong những collections phù hợp về sau. Old không bất tử, nhưng GC scheduling không deterministic.

**Câu 12.** Có thể khẳng định một class field khai báo `static final` khiến object của nó được cấp phát Old từ đầu không?

**Đáp án:** Không. Static strong reachability làm object có khả năng sống lâu, không tự quyết định generation/location của allocation.

## 18. Tổng kết

```text
Most new heap objects
       │
       ▼
     EDEN
       │
       ▼ Young GC
 ┌─────┴─────────────┐
 │ unreachable       │ still reachable
 ▼                   ▼
reclaim          SURVIVOR
                    │
                    ├── live qua young evacuations
                    │      → age tăng, có thể copy tiếp
                    │
                    ├── đủ điều kiện promote
                    │      → OLD
                    │
                    └── unreachable lần sau
                           → reclaim
                            
OLD:
  - không đồng nghĩa immortal
  - G1 concurrent marking + mixed reclamation
  - special path cho humongous allocations
  - Full GC chỉ khi phù hợp các tình huống runtime

G1:
  Eden/Survivor/Old = vai trò của REGIONS;
  không phải ba dải địa chỉ bất biến.
```

**Ghi nhớ:** Eden là nơi thường đón allocations mới; Survivor giữ live young objects qua những lần young collections; Old giữ long-lived/promoted objects và một số object được cấp phát đặc biệt. **Promotion do GC age, survivor capacity và collector heuristics chi phối**, không theo số giây kể từ `new`. Với **G1 Java 17**, ba vai trò này được xây trên **regions** thay đổi linh hoạt; G1 có young-only collections, concurrent marking, mixed collections và những cơ chế thu hồi đặc biệt cho humongous/old. Để tối ưu đúng, hãy nhìn **allocation rate, survival/promotion rates, retained heap, pauses và GC logs**, thay vì cố đoán tuổi đối tượng từ source code.

### Tài liệu tham khảo chuẩn

- [Oracle Java SE 17 — Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): young/old regions, copying, humongous và GC cycles.
- [Oracle Java SE 17 — Garbage-First Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector-tuning.html): adaptive sizing và tuning recommendations.
- [Oracle Java SE 17 — HotSpot GC Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/index.html): thế hệ GC và collector-specific behavior.
- [Oracle — Garbage First GC Tuning](https://www.oracle.com/java/technologies/g1gc.html): phân biệt young/mixed/concurrent marking và tenuring.
- [Oracle JDK 17 `java` launcher manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `UseG1GC`, `Xms`/`Xmx`, `Xlog` và HotSpot flags.
- [Oracle JDK 17 `jstat` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): thống kê GC/memory pools.
- [Java SE 17 — `MemoryPoolMXBean` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryPoolMXBean.html): Java management API cho memory pools.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-005">Thu gom nối tiếp, song song và đồng thời với ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Thu gom nối tiếp, song song và đồng thời với ứng dụng

## 1. Trọng tâm: “serial”, “parallel” và “concurrent” không cùng một trục

Khi nghiên cứu **garbage collection (GC)** trong JVM, ba thuật ngữ thường xuất hiện là:

- **Serial (thu gom nối tiếp):** một luồng GC thực hiện công việc thu gom chính của pha đang xét.
- **Parallel (thu gom song song):** nhiều luồng GC cùng thực hiện công việc trong một pha để tăng tốc xử lý.
- **Concurrent with the application (thu gom đồng thời với ứng dụng):** một hoặc nhiều luồng GC thực hiện **một phần công việc trong lúc các luồng ứng dụng vẫn chạy**.

Hai từ **parallel** và **concurrent** rất dễ bị dịch/hiểu lẫn. Trong ngữ cảnh GC, câu hỏi quyết định là:

1. **Có bao nhiêu GC workers cùng thực hiện một pha?** → trục **serial ↔ parallel**.
2. **Trong lúc pha GC chạy, các application threads (mutators) có bị dừng hay vẫn tiếp tục hoạt động?** → trục **stop-the-world (STW) ↔ concurrent**.

Một collector hoàn toàn có thể **parallel trong những STW pauses** và **concurrent ở những pha khác**. Ví dụ HotSpot **G1** sử dụng các GC workers **song song** khi evacuate regions trong STW pause, nhưng còn có **concurrent marking** diễn ra đồng thời với mutators.

### 1.1. Mô hình hai trục

```text
                        ỨNG DỤNG
                       bị dừng?              còn chạy?
                  +--------------------+--------------------+
1 GC worker       | Serial STW         | Serial concurrent  |
                  | VD: Serial GC      | (pha GC một worker |
                  |                    | chạy với mutator)  |
                  +--------------------+--------------------+
N GC workers      | Parallel STW       | Parallel concurrent|
                  | VD: Parallel GC,   | VD: concurrent     |
                  | G1 evacuation      | marking nhiều GC   |
                  |                    | workers, tùy VM    |
                  +--------------------+--------------------+
```

Các ô trên là **khái niệm về pha thực thi**, không phải danh sách cứng “mỗi collector chỉ nằm trong một ô”. Một collector có thể đi qua **nhiều ô trong một chu kỳ GC**.

**Điểm nhớ quan trọng nhất:** *Parallel GC không nhất thiết là concurrent với ứng dụng*. Trong HotSpot, collector tên **Parallel GC** chủ yếu dùng **nhiều GC threads cho các pha STW**, nghĩa là ứng dụng vẫn có thể bị dừng trong lúc các GC workers chạy song song.

## 2. Vì sao ứng dụng đang chạy làm GC phức tạp?

Ứng dụng Java tạo object, cập nhật reference fields, thay đổi dữ liệu trong collections, và có thể giữ các object còn sống theo những đường tham chiếu mới. Những thread thực thi code ứng dụng thường được gọi là **mutator threads**, vì chúng **làm thay đổi đồ thị object references** trong heap.

GC muốn xác định object nào còn reachable và thu hồi vùng bộ nhớ không còn cần thiết.

```text
GC quan sát đồ thị:                  Mutator có thể thay đổi:
Root -> A -> B                      Root -> A -> B
         \                               \
          C                               D (mới)
```

Nếu GC đánh dấu những object reachable trong lúc mutators vẫn thay đổi các cạnh tham chiếu, collector cần **giao thức nhất quán/đồng bộ thích hợp** để không nhầm một object còn cần thành rác.

Hai cách tổ chức lớn:

- **Dừng mutators trong một pha:** đồ thị ứng dụng không tiếp tục bị những threads ấy cập nhật theo cách thường thấy trong pha này; GC có thể thực hiện nhiều thao tác với một trạng thái ổn định hơn.
- **Cho mutators tiếp tục chạy:** collector phải theo dõi/sửa lại những thay đổi đáng kể, sử dụng **write/read/load barriers**, snapshots, remembered sets, forwarding/colored-pointer metadata hoặc cơ chế khác tùy collector.

**Concurrent collection không có nghĩa không còn phối hợp.** Nó làm tăng yêu cầu về correctness, đồng bộ, CPU time và các barriers. Nó cũng có thể vẫn có các **STW pauses ngắn** để xử lý những điểm nhạy cảm của thuật toán.

## 3. Stop-the-world (STW) là gì?

**STW pause** là khoảng thời gian JVM **tạm dừng các luồng ứng dụng thuộc phạm vi dừng đó** để runtime thực hiện một số công việc phối hợp, như những pha của garbage collection. Các GC worker threads có thể vẫn chạy trong pause.

Sơ đồ trục thời gian:

```text
Thời gian ---------------------------------------------->

Application:  RUNNING █████ | STOPPED .... | █████ RUNNING
GC worker 1:          idle | work work ... | idle
GC worker 2:          idle | work work ... | idle
                          ^ STW pause ^
```

Trong STW, **GC có thể là parallel** nếu có nhiều workers, nhưng **application không đồng thời chạy cùng các GC workers cho pha STW ấy**.

### 3.1. Pause time khác tổng thời gian GC

- **Pause time:** thời gian chương trình bị ngừng tiến triển vì một pause GC cụ thể, theo cách JVM ghi nhận.
- **GC work time:** tổng thời gian GC workers làm việc; nếu nhiều workers làm song song, **tổng CPU time** không bằng đúng wall-clock pause time.
- **Concurrent phase time:** thời gian trôi qua khi GC thực hiện công việc cùng lúc với ứng dụng.
- **Application latency:** thời gian request/task hoàn tất từ góc nhìn người dùng; có thể chịu ảnh hưởng GC pause **lẫn CPU contention** trong concurrent phases.
- **Throughput:** tỷ lệ công việc hữu ích mà hệ thống hoàn thành trong một khoảng thời gian.

**Không dùng “một GC cycle dài 500 ms” làm bằng chứng request bị pause 500 ms** nếu phần lớn cycle chạy concurrently. Ngược lại, không chỉ nhìn “STW pause 2 ms” rồi kết luận GC không ảnh hưởng latency: concurrent worker competition, allocation stalls và barriers cũng có thể tăng thời gian xử lý.

### 3.2. STW không đồng nghĩa toàn bộ các nguyên nhân dừng ứng dụng

Application latency còn bị ảnh hưởng bởi OS scheduling, I/O, locks, JIT compilation, safepoint operations, CPU throttling và memory pressure. GC logs là **một phần** để điều tra latency, không là bằng chứng duy nhất.

### 3.3. Safepoints và handshakes

HotSpot dùng **safepoint/handshake machinery** cho những hoạt động cần phối hợp thread states ở mức VM. GC pauses có thể cần đưa mutators vào trạng thái an toàn để quan sát/update references.

Không nên giảng rằng mỗi STW luôn đơn giản là “gửi signal đóng băng mọi OS threads cùng một instruction”: cách triển khai có thể phối hợp tại safepoints, sử dụng thread-local handshakes hoặc các cơ chế khác.

## 4. Thu gom nối tiếp (serial): ít GC workers, ít overhead phối hợp

**Serial collector** trong HotSpot dùng **một luồng** để thực hiện công việc GC theo thiết kế. Trong Java 17, bật collector này bằng:

```bash
java -XX:+UseSerialGC -jar application.jar
```

`application.jar` là **placeholder cho JAR có Main-Class hợp lệ**. Đây là minh họa cấu hình; bài thực hành riêng ở phần sau có tệp Java chạy được.

### 4.1. Hoạt động theo mô hình khái quát

```text
Application   ██████████ |       STOP       | ███████████
GC Worker 1   ---------- | MARK/COPY/SWEEP   | -----------
GC Worker 2   (không có worker thứ hai cho công việc thu gom chính)
```

Tùy thế hệ/pha, Serial collector có thể sử dụng kỹ thuật **copying**, **mark-sweep-compact** và các bước riêng. Điểm bản chất của từ “serial” là **độ song song của GC work**, không phải “JVM có duy nhất một application thread”.

### 4.2. Ưu điểm

- Ít overhead phối hợp giữa GC workers.
- Phù hợp với heap nhỏ, số CPU hạn chế hoặc ứng dụng có workload đơn giản.
- Dễ hình dung quan hệ “mutators dừng → một worker thu gom → mutators tiếp tục”.
- Trong một số workload nhỏ, chi phí tổ chức nhiều worker có thể lớn hơn lợi ích của parallelism.

### 4.3. Hạn chế

- Trên heap/live set lớn, một GC worker có thể không tận dụng nhiều CPU cores.
- STW pause có thể dài nếu lượng công việc thu gom lớn.
- Nếu SLA có yêu cầu latency chặt, Serial có thể không phù hợp.

**Không kết luận Serial luôn chậm nhất cho mọi workload.** Với lượng dữ liệu nhỏ, CPU quota thấp, hoặc overhead parallel setup đáng kể, một worker có thể đủ và đôi khi hiệu quả hơn.

## 5. Thu gom song song (parallel): chia việc cho nhiều GC workers

**Parallel collector**, còn được gọi là **throughput collector**, sử dụng nhiều GC workers để xử lý những pha thu gom của nó. Trong HotSpot JDK 17:

```bash
java -XX:+UseParallelGC -jar application.jar
```

Với các tác vụ phù hợp, tổng công việc GC được chia cho nhiều worker cùng tiến hành.

```text
Application   ██████████ |         STOP          | ███████████
GC worker 1   ---------- | MARK/COPY PARTITION 1 | -----------
GC worker 2   ---------- | MARK/COPY PARTITION 2 | -----------
GC worker 3   ---------- | MARK/COPY PARTITION 3 | -----------
GC worker 4   ---------- | MARK/COPY PARTITION 4 | -----------
```

**Cực kỳ quan trọng:** mặc dù **GC workers chạy parallel**, ứng dụng vẫn đang trong **STW pause**. Tên “Parallel GC” không có nghĩa GC song song với ứng dụng.

### 5.1. Tại sao parallel có thể rút ngắn pause?

Nếu có lượng công việc GC là `W` và có `P` workers, trực giác lý tưởng có thể là “mỗi worker xử lý khoảng `W/P`”. Tuy nhiên, thời gian thực tế có overhead:

- Phân chia công việc.
- Đồng bộ, barriers giữa GC workers.
- Work stealing hoặc mất cân bằng tải.
- CPU scheduling và quota trong container.
- Tranh chấp memory bandwidth, cache và NUMA effects.
- Những pha không thể song song hóa hoàn toàn.

Vì vậy **không có bảo đảm tăng gấp bốn workers thì pause giảm bốn lần**.

### 5.2. Throughput vs pause time

Parallel GC thường là lựa chọn được cân nhắc khi hệ thống cần **throughput cao** và có thể chấp nhận STW pauses dài hơn những ứng dụng low-latency.

Ví dụ một batch job xử lý dữ liệu lớn có thể ưu tiên tổng số records/second hơn p99 request latency, nên throughput GC đáng thử. Nhưng nếu đó là interactive service có SLA p99 rất thấp, nên đo thêm các collector có concurrent phases.

### 5.3. Khi parallel workers quá nhiều

Nếu JVM chạy trong container chỉ được cấp ít CPU quota, việc tạo nhiều GC workers có thể tạo overhead mà không có lượng CPU thực để dùng. Ergonomics của HotSpot thường tính toán workers từ CPU khả dụng, nhưng cần kiểm tra giá trị thực tế, đặc biệt khi thay đổi container/cgroup configuration.

Không giả định host có 64 cores là JVM container sẽ được dùng cả 64 cores.

## 6. Thu gom đồng thời (concurrent): mutator vẫn hoạt động trong một phần chu kỳ

Một **concurrent phase** của collector chạy khi **application threads vẫn có thể tiếp tục làm việc**. Nghĩa là GC và application chia sẻ CPU/memory bandwidth, phối hợp để truy vết/reference update theo mô hình của collector.

```text
Thời gian ---------------------------------------------------->

Application   ███████████ | pause | █████████████ | pause | ███
GC worker(s)  ----------- | init  | CONCURRENT   | final | ---
                                     ^
                          Mutators cũng đang RUNNING
```

Đây là mô hình **một chu kỳ có thể kết hợp pauses ngắn và concurrent work**, không phải timeline bắt buộc của mọi collector.

### 6.1. Concurrent không đồng nghĩa parallel worker count > 1

Một GC worker có thể làm việc **đồng thời với một application thread** nếu hai bên được hệ điều hành luân phiên hay chạy trên cores khác nhau. Ngược lại, nhiều GC workers có thể chạy song song nhau trong STW mà **không concurrent với mutators**.

Để trả lời câu hỏi “GC này là parallel hay concurrent?”, hãy hỏi **đang nói tới collector hay pha nào**, và xác định ai bị dừng trong khoảng thời gian đó.

### 6.2. Lợi ích

- Chuyển phần công việc GC ra khỏi những khoảng dừng ứng dụng.
- Có tiềm năng giảm **thời gian STW** và cải thiện tail latency.
- Có thể giảm sự phụ thuộc của độ dài pause vào kích thước heap/live set đối với những collector nhất định.
- Phù hợp với các ứng dụng yêu cầu latency thấp.

### 6.3. Chi phí

- GC và application cạnh tranh CPU/memory bandwidth.
- Write/load barriers và GC bookkeeping có overhead.
- Cần heap headroom để mutators tiếp tục allocate trong lúc GC cycle chưa xong.
- Nếu tốc độ cấp phát vượt tốc độ collector thu hồi, có thể xảy ra **allocation stall**, fallback hoặc OOME tùy collector và tình huống.
- Implementation phức tạp hơn, cần cơ chế duy trì tính đúng khi object graph thay đổi.

**Không có collector nào tạo “GC miễn phí” hoặc bảo đảm zero-pause tuyệt đối chỉ nhờ concurrent.**

## 7. Các cơ chế đồng bộ để concurrent collector vẫn đúng

Một bài GC cần trả lời: nếu mutator liên tục cập nhật reference trong lúc GC mark, làm sao không thu hồi nhầm object?

### 7.1. Marking, write barriers và snapshots

Giả sử GC đã quét object `A` rồi mutator đổi `A.child` từ `B` sang `C`. Một thuật toán concurrent mark cần xử lý thay đổi này theo cách bảo toàn các invariants của nó.

Các họ kỹ thuật gồm:

- **Snapshot-at-the-beginning (SATB):** ghi nhận một góc nhìn snapshot logic tại thời điểm bắt đầu marking, với các cơ chế barrier để theo dõi những reference có thể bị loại khỏi đồ thị.
- **Incremental-update marking:** theo dõi/thích ứng những cạnh mới làm thay đổi đồ thị đang mark.
- **Load/read barriers:** kiểm tra/điều chỉnh references khi ứng dụng đọc, quan trọng trong một số collector có concurrent relocation.
- **Remembered sets/card tables:** theo dõi vùng heap/nhóm generations liên quan, tránh phải quét toàn bộ heap cho mỗi GC nhỏ.

**Không có một barrier protocol duy nhất áp dụng cho mọi collector**. Ví dụ G1 trong các cấu hình được mô tả bởi tài liệu Oracle sử dụng **SATB concurrent marking** và cơ chế remembered sets; ZGC/Shenandoah dùng các kỹ thuật riêng cho marking/relocation.

### 7.2. Tại sao remark vẫn cần dừng?

Sau một concurrent marking phase, collector có thể cần chốt những dữ liệu được tích lũy và trạng thái liveness nhất quán. Với G1, **Remark** là một **STW pause**, xử lý phần còn lại của concurrent marking theo thuật toán.

Do đó:

```text
G1 concurrent mark ≠ “G1 không còn STW”
```

Một collector được mô tả là **mostly concurrent** vẫn có thể cần các stop-the-world phases khác.

### 7.3. Mutation rate ảnh hưởng concurrent GC

Nếu ứng dụng có rate cập nhật references và allocation rất cao, lượng GC bookkeeping và marking cần thực hiện có thể tăng. Một collector có concurrent marking không thể bỏ qua mọi object mutations.

Khi benchmark, ngoài heap size, cần đo **allocation rate, live-set size, mutation rate và CPU capacity**. Hai ứng dụng dùng cùng `-Xmx` có thể có GC behavior rất khác nhau.

## 8. Phân loại theo pha thay vì gắn một nhãn cho toàn collector

| Collector/pha trong HotSpot | GC workers có thể parallel? | Mutators còn chạy trong pha ấy? | Ý nghĩa |
| --- | --- | --- | --- |
| **Serial GC — collection pause** | Không, một GC worker chính | **Không — STW** | Đơn giản, ít coordination |
| **Parallel GC — collection pause** | **Có, nhiều workers** | **Không — STW** | Ưu tiên throughput |
| **G1 — young/mixed evacuation** | **Có** | **Không — STW** | Evacuate/copy live objects theo regions |
| **G1 — concurrent marking** | **Có thể nhiều workers** | **Có** | Truy vết live data trong lúc ứng dụng chạy |
| **G1 — remark** | Có thể parallel GC work | **Không — STW** | Hoàn tất phần marking cần synchronization |
| **ZGC — phần lớn expensive GC work** | Có nhiều workers tùy pha | **Có** | Hướng latency thấp, vẫn có pauses ngắn |
| **Shenandoah — nhiều mark/evacuation phases** | Có thể parallel | **Có** | Concurrent mark và relocation, tùy build/mode |
| **Full GC fallback, nếu xảy ra** | Tùy collector/phiên bản | Thường có **STW** đáng kể | Không nên nhầm với cycle thường gặp |

Bảng mang tính mô tả các **phases điển hình**, không phải hợp đồng mọi bản HotSpot đều dùng đúng số threads/phases. Flag, collector version và architecture có thể làm thay đổi chi tiết.

## 9. Serial GC: cơ chế và lựa chọn sử dụng

Serial GC là ví dụ rõ của **thu gom nối tiếp và STW**. Những điểm cần nhớ:

1. **Single GC worker** làm công việc thu gom.
2. **Young collection** trong mô hình generational thường chuyển các object còn sống và thu hồi vùng young.
3. **Old collection** có thể thực hiện marking, sweep/compact tùy hoạt động cụ thể của Serial collector.
4. Công việc thu gom chính tạo ra pause khi mutators bị dừng.
5. Không có overhead phải phối hợp nhiều GC workers cho mỗi collection.

### 9.1. Chọn Serial khi nào?

- Heap nhỏ hoặc object graph nhỏ.
- Môi trường có CPU cực hạn chế.
- Tool/CLI hoặc batch workload ít concurrency.
- Thời gian pause đo được đáp ứng yêu cầu.
- Muốn đo baseline đơn giản trước khi thử collector phức tạp hơn.

### 9.2. Khi nào nên cân nhắc thay?

Nếu request p99 bị ảnh hưởng bởi STW pauses dài hoặc ứng dụng có nhiều cores và GC chiếm phần đáng kể wall time, thử Parallel/G1/ZGC theo mục tiêu đo lường.

Đừng đổi collector chỉ vì “Serial nghe có vẻ lỗi thời”: một cấu hình nhỏ vẫn có thể dùng nó rất hợp lý.

## 10. Parallel GC: thông lượng và các điều chỉnh

Parallel collector dùng nhiều workers cho GC, thường tập trung **throughput**. Nó vẫn có những pause mà ứng dụng ngừng.

### 10.1. Điều chỉnh workers với dữ liệu thật

Cờ HotSpot hay gặp:

```text
-XX:ParallelGCThreads=<n>
```

Đây là **placeholder**; khi cấu hình thực tế phải thay `<n>` bằng số nguyên hợp lệ, xem tương tác với collector và runtime. Flag này liên quan số GC workers cho những công việc parallel trong các collectors có hỗ trợ.

Không phải “đặt workers = toàn bộ CPU cores” luôn tối ưu. Cần tính toán CPU quota, application threads, pause target và overhead.

### 10.2. Cấu hình throughput-oriented

Parallel GC có thể được điều chỉnh với các mục tiêu throughput/pause và adaptive sizing của collector trong HotSpot, nhưng các cờ có ý nghĩa/tác động cụ thể theo phiên bản.

```text
-XX:+UseParallelGC
-XX:ParallelGCThreads=4
```

Là ví dụ tùy chọn, không phải mặc định khuyến nghị cho mọi server. Tốt hơn là bắt đầu từ ergonomic defaults và đo GC logs, p95/p99, throughput, CPU và pause distribution.

### 10.3. Một vấn đề hay hiểu sai: nhiều CPU làm STW biến mất?

Không. Parallelization có thể rút ngắn **thời gian wall-clock** của pha GC được song song hóa, nhưng vẫn là một **STW pause**. Nếu live set lớn, heap pressure nặng hoặc nhiều data phải di chuyển, pause vẫn có thể đáng kể.

## 11. G1: collector vừa parallel, vừa mostly concurrent, vừa STW

**G1 (Garbage-First)** là collector mặc định trong nhiều cấu hình HotSpot Java 17 phổ biến, nhưng default lựa chọn thực tế còn phụ thuộc VM ergonomics và môi trường. Cờ bật tường minh:

```bash
java -XX:+UseG1GC -jar application.jar
```

G1 chia heap thành **regions**, quản lý các phần logical young/old và chọn những vùng đáng thu hồi. Nó dùng concurrent marking để xác định liveness/ứng viên cũ và dùng những STW evacuations để thu hồi tập regions.

### 11.1. Những pha tiêu biểu

```text
G1 cycle — sơ đồ khái quát

[STW Young / Concurrent Start]
            |
            v
   Concurrent Root Region Scan
            |
            v
     Concurrent Marking
            |
            +-- Có thể xen các STW Young collections
            |
            v
       [STW Remark]
            |
            v
   [STW + Concurrent Cleanup]
            |
            v
  Một số [STW Mixed collections]
            |
            v
    Quay lại young-only phase
```

Các tên/sắp xếp cụ thể phản ánh mô tả G1 của JDK 17 trong tình huống hoạt động điển hình; không phải một trace buộc xảy ra mọi lần GC.

### 11.2. G1 evacuation vẫn là STW

Trong young/mixed GC:

- JVM chọn tập regions.
- Nhiều GC workers có thể **copy/evacuate** live objects sang regions khác.
- Object references được cập nhật theo thuật toán.
- **Mutators bị dừng trong pause evacuation**.

Vì G1 có **concurrent marking** nên gọi “G1 là collector không STW” là sai.

### 11.3. G1 pause goal không phải deadline chắc chắn

```text
-XX:MaxGCPauseMillis=200
```

Ví dụ này thể hiện **pause-time goal** cho collector có hỗ trợ, **không phải bảo đảm mọi pause ≤200 ms**. G1 điều chỉnh công việc dựa trên các dự báo, và có thể bỏ lỡ mục tiêu khi áp lực bộ nhớ, live set, CPU hoặc workload bất lợi.

Không dùng pause goal làm thay thế đo tail latency.

### 11.4. Full GC fallback

Trong một số tình huống allocation/evacuation/marking không theo kịp và heap quá chật, G1 có thể phải thực hiện một **Full GC fallback** tốn kém, thường gây STW pause dài.

Không suy luận “chọn G1 thì sẽ không bao giờ Full GC”. Cần có headroom và điều tra nguyên nhân áp lực.

## 12. ZGC và Shenandoah: đưa thêm công việc ra concurrent phases

### 12.1. ZGC

**Z Garbage Collector (ZGC)** tập trung vào **low latency**, thực hiện phần lớn công việc đắt đỏ **đồng thời với mutators**; vẫn có các phối hợp/pauses ngắn tùy version và phase.

Bật trên một bản HotSpot hỗ trợ bằng:

```bash
java -XX:+UseZGC -jar application.jar
```

ZGC thiết kế để giới hạn pauses mà không cần để toàn bộ marking/relocation của live set trong một STW window. Nó có thể sử dụng specialized pointer/barrier mechanisms và cần CPU/headroom cho concurrent work.

Nhưng:

- Không hứa **0 ms pause**.
- Không hứa p99 của mọi endpoint sẽ tốt hơn trong mọi workload.
- Có thể chịu throughput overhead và allocation stalls khi không đủ CPU/headroom.
- Flag và mode generational/non-generational **thay đổi theo JDK**; không áp dụng mọi tuning recipe cho Java 17, 21, 25 và những bản mới.

### 12.2. Shenandoah

**Shenandoah** là collector hướng **low-pause**, thực hiện nhiều hoạt động (gồm marking và, trong thiết kế tương ứng, evacuation/compaction) concurrently với application.

Một số OpenJDK/HotSpot builds hỗ trợ:

```bash
java -XX:+UseShenandoahGC -jar application.jar
```

**Không phải mọi vendor/build/platform đều cung cấp Shenandoah**. Nếu tùy chọn không được VM nhận, đó là vấn đề hỗ trợ tính năng của runtime; dùng `java -version` và tài liệu vendor trước khi chọn.

### 12.3. Tại sao concurrent relocation khó?

Trong marking, collector xác định objects còn sống. Trong **relocation**, collector có thể chuyển live objects sang vị trí mới. Nếu mutators vẫn đang chạy, reference loads/stores cần thấy đối tượng đúng trong quá trình chuyển; VM phải phối hợp pointer forwarding, barriers hoặc cơ chế khác.

**Concurrent compaction không phải đơn giản gọi `System.arraycopy` trong background rồi hy vọng mọi Java references tự đúng**. Đây là một bài toán phối hợp tinh vi giữa mutator, GC và runtime representation.

### 12.4. “Pause time độc lập hoàn toàn heap size” cần hiểu có điều kiện

Các collector low-pause thường thiết kế để tránh pause tăng tỷ lệ trực tiếp với lượng live data lớn, nhưng các yếu tố như OS scheduling, root processing, CPU starvation, safepoint latency và fallback vẫn ảnh hưởng pause. Đừng coi một biểu thức marketing “sub-millisecond” là SLA tuyệt đối của mọi JDK/build/workload.

## 13. “Concurrent” là giao thoa thời gian, không bảo đảm đồng thời trên các core riêng

Trong một môi trường container có **một CPU quota**, các GC workers và application threads có thể xen kẽ trên cùng CPU. Họ vẫn được coi là **concurrent** nếu collector phase hoạt động trong giai đoạn mutators được phép chạy, dù không thực sự có hai instruction thực thi **đúng cùng một nanosecond** trên hai CPU cores.

Phân biệt:

- **Concurrency:** các tác vụ có khoảng sống/thực thi chồng nhau về mặt logic.
- **Parallelism:** nhiều tác vụ thực sự tiến hành cùng lúc trên các xử lý song song, trong điều kiện thích hợp.
- **STW:** mutators không tiến hành bình thường trong khoảng pause, dù GC workers có thể dùng nhiều CPU.

Lúc đọc tài liệu GC, **parallel** thường là số **GC worker threads** tham gia một phase, không chỉ là định nghĩa tổng quát của concurrency trong lập trình đa luồng.

## 14. Chi phí của concurrent GC: CPU, barriers, allocation headroom

Một concurrent collector có thể giảm pause bằng cách trả “giá” ở nơi khác.

### 14.1. CPU contention

Nếu GC workers hoạt động concurrent trong 300 ms, ứng dụng vẫn chạy nhưng có thể thiếu CPU capacity. Một request không bị STW pause vẫn có thể tăng latency do scheduler contention.

### 14.2. Allocation headroom

Mutators tiếp tục cấp phát memory trong lúc collector chưa hoàn tất việc reclaim. Nếu heap quá đầy, collector có thể không có chỗ để giữ đối tượng tạm thời hoặc copy/relocate. Khi đó xuất hiện những **failure/stall paths** tùy thuật toán.

### 14.3. Barriers và metadata

Write/load barriers làm tăng một số instructions/memory operations của mutators, tùy collector. Remembered sets, mark bitmaps và forwarding data cũng cần memory.

### 14.4. Throughput trade-off

Một benchmark có thể cho:

- Parallel GC: throughput cao hơn nhưng p99 pause dài hơn.
- G1: throughput thấp hơn một chút, p99 pause dễ kiểm soát hơn.
- ZGC/Shenandoah: pause thấp hơn nhưng có thể sử dụng thêm CPU/heap cho concurrent work.

**Đây là tình huống minh họa, không phải bảng ranking cố định.** Kết quả thực tế thay đổi theo workload, hardware, JDK/GC version và cách đo.

## 15. Một ví dụ Java 17+ chạy được trên nhiều collector

Chương trình dưới đây tạo một **allocation workload có kiểm soát**. Nó giữ lại một số arrays trong list, còn các arrays khác không được giữ lâu dài. Vì GC schedule là không xác định, chương trình **không in số GC events hay pause timings**; những số liệu ấy được xem trong GC logs.

Tạo tệp **`GCWorkloadDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class GCWorkloadDemo {
    public static void main(String[] args) {
        List<byte[]> retained = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 512; i++) {
            byte[] block = new byte[512 * 1024];
            block[0] = (byte) i;

            checksum += block[0] & 0xFF;
            if (i % 32 == 0) {
                retained.add(block);
            }
        }

        long sum = 0;
        for (byte[] block : retained) {
            sum += block[0] & 0xFF;
        }

        System.out.println("checksum=" + checksum);
        System.out.println("retained arrays=" + retained.size());
        System.out.println("retained first bytes sum=" + sum);
    }
}
```

### 15.1. Biên dịch

```bash
javac --release 17 GCWorkloadDemo.java
```

### 15.2. Chạy với các collectors

Trên một OpenJDK/HotSpot build có hỗ trợ các collector tương ứng:

```bash
java -Xms64m -Xmx64m -XX:+UseSerialGC GCWorkloadDemo
java -Xms64m -Xmx64m -XX:+UseParallelGC GCWorkloadDemo
java -Xms64m -Xmx64m -XX:+UseG1GC GCWorkloadDemo
java -Xms64m -Xmx64m -XX:+UseZGC GCWorkloadDemo
```

**Stdout của ứng dụng khi các lần chạy hoàn tất bình thường:**

```text
checksum=65280
retained arrays=16
retained first bytes sum=1792
```

Mã được biên dịch bằng `javac --release 17` trên OpenJDK 21 và chạy thử với **Serial, Parallel, G1 và ZGC ở 64 MiB heap**; cả bốn đều in cùng kết quả như trên.

Đừng xem `-Xmx64m` là cấu hình khuyến nghị cho production: đây là heap nhỏ tạo áp lực GC để dễ thực hành. Trong môi trường thiếu native memory, GC mode chưa được build, có cờ khác tương tác hoặc CPU quota quá thấp, một lệnh có thể thất bại hay cho GC timeline khác.

### 15.3. Giải thích phép tính

- Có 512 lần cấp phát `byte[512*1024]`.
- `block[0]` là byte của `i`; khi đọc lại dưới dạng unsigned với `& 0xFF`, các giá trị `0..255` lặp hai lần.
- Tổng là `2 × (0+1+...+255) = 65280`.
- Mỗi 32 vòng giữ một array: `i = 0,32,...,480`, tổng **16 arrays**.
- Các byte đầu của 16 arrays giữ lại là `0,32,...,224` lặp hai lần, tổng **1792**.

Ứng dụng không phụ thuộc collector để tính đúng. Khác biệt thuộc **GC throughput, pause/concurrent timings, CPU usage, memory footprint**.

### 15.4. Tại sao không viết test “GC phải xảy ra đúng 12 lần”?

Collector chọn thời điểm và lượng work theo heuristics, allocation patterns, JDK/VM options, heap pressure và JIT. Không có guarantee một chương trình cấp phát 512 arrays thì Java phải chạy đúng N GCs.

Để đo, dùng logging/profiling và so sánh phân phối thay vì hardcode số events.

## 16. Bật GC logging để quan sát STW và concurrent phases

HotSpot JDK 17 có **Unified Logging**. Ví dụ:

```bash
java -Xms64m -Xmx64m -XX:+UseSerialGC -Xlog:gc*=info GCWorkloadDemo
java -Xms64m -Xmx64m -XX:+UseParallelGC -Xlog:gc*=info GCWorkloadDemo
java -Xms64m -Xmx64m -XX:+UseG1GC -Xlog:gc*=info GCWorkloadDemo
java -Xms64m -Xmx64m -XX:+UseZGC -Xlog:gc*=info GCWorkloadDemo
```

**Output GC logs KHÔNG cố định** và sẽ xuất hiện ngoài ba dòng stdout của ứng dụng: timestamp, GC ID, event count, heap occupancy, workers và timings thay đổi theo runtime và môi trường. Không được lấy một log sample rồi khẳng định mọi máy phải cho kết quả y hệt.

### 16.1. Đọc các chữ quan trọng trong logs

Trên những bản HotSpot phù hợp:

- `Pause Young`: một khoảng STW pause liên quan young collection.
- `Pause Full`: một khoảng dừng thu gom toàn heap hoặc fallback tương ứng.
- `Concurrent Mark` hoặc `Concurrent ...`: pha GC được tổ chức làm việc concurrent với mutators.
- `Using ...`: các dòng startup có thể nêu collector được chọn.
- `workers` hoặc các tag phase/worker: giúp quan sát mức parallelism và thời gian worker.

**Đừng hiểu mọi dòng chứa `GC(n)` là một lần STW.** Một GC cycle có thể có nhiều phase logs; các concurrent phases có thể mang cùng GC ID hoặc những cách tổ chức log khác nhau.

### 16.2. So sánh Serial và Parallel

Trong nhiều workload:

- Serial có GC work dựa trên một worker, STW times.
- Parallel có nhiều GC workers trong pause, STW times vẫn tồn tại.
- Các dòng log khác nhau vì collector-specific format và thuật toán.

Không kết luận “Parallel nhanh hơn Serial” nếu chỉ nhìn **một pause**: cần so sánh tổng throughput, pause distribution và CPU cost.

### 16.3. So sánh G1 và ZGC

- G1 có cả STW young/mixed evacuations và concurrent marking.
- ZGC chuyển nhiều công việc nặng sang các pha concurrent; vẫn có một số pauses/coordination.
- Mức phù hợp tùy latency budget, CPU và heap headroom.

### 16.4. Kiểm tra logging tags

```bash
java -Xlog:help
```

Các tag và levels có thể khác khi đổi JDK, không nên copy một tổ hợp `-Xlog` đặc thù từ blog cũ rồi dùng cho mọi HotSpot versions.

## 17. Các tham số nên đo khi quyết định collector

| Chỉ số | Ý nghĩa | Cách sử dụng |
| --- | --- | --- |
| **STW pause p50/p95/p99/max** | Distribution các khoảng dừng | Xem latency tail, outliers và SLA |
| **Request p95/p99** | Độ trễ người dùng thực | Kết hợp GC logs và tracing |
| **Throughput** | Requests/records/tasks mỗi giây | So sánh lợi ích ứng dụng cuối cùng |
| **CPU utilization** | Mutators + GC workers | Nhận biết GC contention |
| **Allocation rate** | Bytes tạo mới mỗi giây | Phân tích áp lực trên young/old heap |
| **Live set** | Dung lượng objects còn reachable | Quyết định lượng mark/copy/compact |
| **Heap occupancy / headroom** | Không gian còn cho allocation/relocation | Tránh stalls, fallback, OOME |
| **GC CPU time vs wall time** | Chi phí tính toán và thời gian trôi | Đừng cộng nhầm nhiều workers như một pause |
| **Concurrent cycle duration** | Thời gian GC còn chạy cùng mutators | Phát hiện cycle không theo kịp |
| **Full GC / allocation stalls** | Tình huống bất thường, có thể gây latency | Phân tích memory pressure và tuning |

### 17.1. Phân biệt latency và throughput

- Ứng dụng xử lý batch có thể tối ưu records/s, chấp nhận STW pauses.
- API cho người dùng có thể ưu tiên p99 end-to-end latency.
- Một hệ thống mixed workload phải cân bằng cả hai, tùy SLO.

Cùng một collector không luôn tối ưu cho mọi mục tiêu.

### 17.2. Kết quả benchmark cần lặp và có baseline

Một quy trình tốt:

1. Giữ **cùng JDK build**, cùng application artifact và workload.
2. Cố định heap options phù hợp, CPU quota và container limits.
3. Warm-up trước khi đo để giảm nhiễu JIT/class loading.
4. Lặp nhiều runs, đo throughput và pause/latency distribution.
5. Ghi số classes, live set, allocation rate, CPU, memory.
6. So sánh theo mục tiêu nghiệp vụ rồi mới chọn collector.

Nếu một run duy nhất cho Parallel GC p99 tốt hơn G1, đó là quan sát chứ không phải quy luật vĩnh viễn.

## 18. Các cờ liên quan và lưu ý không “tối ưu mù”

### 18.1. Chọn collector

```text
-XX:+UseSerialGC
-XX:+UseParallelGC
-XX:+UseG1GC
-XX:+UseZGC
-XX:+UseShenandoahGC  (khi bản HotSpot hỗ trợ)
```

Mỗi tiến trình JVM thường chọn **một collector phù hợp**; không gộp nhiều cờ lựa chọn collector khác nhau tùy tiện trong một lệnh. Tính khả dụng và defaults phụ thuộc VM/JDK build.

### 18.2. Mục tiêu G1 và workers

```text
-XX:MaxGCPauseMillis=200
-XX:ParallelGCThreads=4
-XX:ConcGCThreads=2
```

Các values trên **chỉ minh họa các loại cờ**, không là cấu hình production mặc định. `ParallelGCThreads` kiểm soát parallel work trong những collector/phases hỗ trợ; `ConcGCThreads` là tham số liên quan concurrent worker count theo collector.

Nếu đặt concurrent workers quá ít, collector có thể không theo kịp allocation/mutation rate. Nếu quá nhiều, ứng dụng có thể thiếu CPU.

### 18.3. Tránh dựa vào tuning recipe cũ

Các collectors, cờ và algorithms đã thay đổi qua JDK releases. Ví dụ **CMS** (Concurrent Mark Sweep) là collector lịch sử, bị loại khỏi HotSpot từ **JDK 14** (JEP 363), vì vậy không dùng `-XX:+UseConcMarkSweepGC` như một tùy chọn hiện hành của JDK 17.

Với ZGC, các phiên bản mới có hỗ trợ **generational** thay đổi lớn trong thiết kế và tuning. Không lấy một tập cờ từ JDK 17 rồi áp cho JDK 25/27 mà không xem migration notes.

### 18.4. “GC pause goal 50ms” không là real-time guarantee

Một số cờ giúp collector nhắm đến pause target bằng heuristics, nhưng không cam kết deadline tuyệt đối cho mọi tình huống. Bảo đảm thời gian thực (*hard real-time*) cần mô hình hệ thống và runtime chuyên dụng khác, không chỉ đổi cờ GC.

## 19. Một chu kỳ G1 có thể vừa parallel vừa concurrent: diễn giải đầy đủ

Hãy giả sử (để dễ hình dung) ứng dụng có 4 application threads và G1 được phân 4 parallel workers trong STW pauses, 2 workers trong concurrent phases. Đây là **con số minh họa**, không phải giá trị mặc định được bảo đảm.

```text
                t0              t1              t2             t3
                |               |               |              |
Mutators A-D    RUNNING  ...  [PAUSE]   RUNNING  ...          [PAUSE]
                                  |                              |
GC 4 workers                      | parallel evacuation          | parallel work
                                  |
GC 2 workers                                     concurrent mark
                                                 +------+--------+
                                                        |
                                               mutators RUNNING
```

Diễn giải:

1. Trong **STW evacuation**: 4 GC workers có thể chạy đồng thời nhau, còn 4 mutator threads bị dừng.
2. Trong **concurrent mark**: 2 GC workers làm việc trong giai đoạn 4 mutators được phép chạy.
3. Cuối marking có thể có **STW remark/cleanup**.
4. Không được tính tổng thời gian concurrent phase thành pause.
5. Application CPU có thể bị chia sẻ trong concurrent mark.

Chính vì thế một câu mô tả như **“G1 là parallel, mostly concurrent, evacuating và stop-the-world”** không tự mâu thuẫn; mỗi thuộc tính mô tả một khía cạnh/pha của collector.

## 20. Phân tích tình huống thực tế

### Tình huống A — CLI tool nhỏ, chạy vài giây

Một CLI tool có heap nhỏ, lượng objects không lớn và CPU quota thấp có thể thử **Serial GC**. Overhead tạo nhiều GC workers đôi khi không đem lại lợi ích. Nhưng cần xác minh runtime và độ trễ tổng, không dựa vào tên collector.

### Tình huống B — Batch processing cần throughput

Một batch worker chạy trên máy đa core, cho phép pauses khá dài nhưng muốn tổng thời gian xử lý ngắn có thể thử **Parallel GC**. Vẫn đo thời gian tạm dừng và memory, bởi pauses quá dài có thể ảnh hưởng job coordination.

### Tình huống C — HTTP API có p99 target

Một HTTP service cần giảm tail latency có thể bắt đầu với **G1** (thường là default trên cấu hình Java 17 server-class) và quan sát STW young/mixed pauses. Nếu p99 vẫn không đáp ứng, thử **ZGC** trên runtime hỗ trợ, đồng thời đo CPU, request latency và allocation stalls.

### Tình huống D — Ứng dụng tạo object cực nhanh

Một system có allocation rate cao, live set lớn và heap ít headroom có thể làm **concurrent collector không theo kịp**. Không đơn giản giải quyết bằng việc “chọn concurrent GC”; cần giảm allocation, sửa retention, tăng heap/headroom hợp lý hoặc điều chỉnh workload.

### Tình huống E — Container 2 vCPU bị CPU throttling

Một JVM chạy concurrent GC trong container có thể bị CPU contention/throttling, làm cả GC cycle và request time kéo dài. GC pauses đo được có thể nhỏ nhưng p99 request vẫn tăng. Kiểm tra CPU quota và throttling metrics thay vì nhìn mỗi GC logs.

## 21. Những hiểu lầm phổ biến

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Parallel GC chạy cùng ứng dụng.” | Parallel chủ yếu nói GC workers; Parallel collector có STW phases trong đó mutators dừng. |
| “Serial GC chỉ chạy khi ứng dụng có một thread.” | Một GC worker không giới hạn số application threads. |
| “Concurrent GC không bao giờ STW.” | Nhiều concurrent collectors vẫn có pauses/coordination ngắn. |
| “G1 chỉ concurrent, không parallel.” | G1 dùng cả parallel STW evacuation lẫn concurrent marking. |
| “Một GC cycle dài 500 ms nghĩa request pause 500 ms.” | Concurrent cycle elapsed time khác STW pause time. |
| “4 GC workers chắc chắn nhanh gấp 4.” | Đồng bộ/overhead/CPU và phần work không parallel giới hạn tốc độ. |
| “STW pause 2ms nghĩa GC không tốn CPU.” | Concurrent phases, barriers và runtime bookkeeping có thể tốn CPU. |
| “Concurrent GC không cần heap headroom.” | Mutators vẫn allocate trong lúc GC chưa reclaim xong. |
| “`MaxGCPauseMillis` là deadline tuyệt đối.” | Là mục tiêu heuristic, không phải hard SLA. |
| “Bật ZGC làm throughput luôn tốt hơn.” | Low-latency có trade-offs CPU/throughput và phụ thuộc workload. |
| “`System.gc()` bảo đảm một GC cycle bắt đầu/kết thúc theo lịch chính xác.” | Đây không phải công cụ test số GC events portable. |
| “Heap lớn thì không cần đo GC.” | Live set, allocation/mutation rates và latency vẫn quan trọng. |
| “CMS vẫn là lựa chọn bình thường trên JDK17.” | CMS đã bị loại khỏi HotSpot từ JDK14. |
| “Shenandoah luôn có trên mọi JDK.” | Tùy vendor/build/platform. |
| “G1 default trên tất cả mọi JVM.” | Default phụ thuộc implementation/ergonomics, dù G1 phổ biến trên HotSpot server configurations. |
| “Mọi GC log line biểu thị một pause.” | Có cả concurrent phase logs, startup info và nhiều events trong một cycle. |
| “CPU host có 64 cores nghĩa container dùng đủ 64.” | CPU quota/cgroup có thể giới hạn. |
| “Có một collector tốt nhất cho mọi hệ thống.” | Phải chọn theo SLO, workload, heap, CPU, version và đo lường. |

## 22. Bài tập tự kiểm tra kèm đáp án

**Câu 1.** Parallel GC có cần dừng application threads trong những collection pauses không?

**Đáp án:** Có. Nhiều GC workers làm việc **song song với nhau** trong **STW**, không có nghĩa họ chạy đồng thời với mutators.

**Câu 2.** Serial GC có cấm chương trình Java tạo nhiều threads không?

**Đáp án:** Không. “Serial” nói về GC work, không phải số application threads.

**Câu 3.** Một concurrent GC phase có đúng một GC worker và mutators vẫn chạy. Có phải concurrent không?

**Đáp án:** Có. Concurrent với ứng dụng là một trục khác với số GC workers parallel.

**Câu 4.** G1 có thể có concurrent mark và STW evacuation cùng trong một GC lifecycle không?

**Đáp án:** Có. G1 kết hợp nhiều pha; không mâu thuẫn giữa “mostly concurrent” và “stop-the-world phases”.

**Câu 5.** Tại sao concurrent collector cần write/load barriers?

**Đáp án:** Mutators còn thay đổi hoặc truy cập đồ thị references trong khi GC làm việc; barriers giúp bảo toàn invariants của thuật toán marking/relocation tương ứng.

**Câu 6.** Một concurrent marking phase dài 300 ms có nghĩa ứng dụng bị pause 300 ms không?

**Đáp án:** Không. Đây có thể là thời gian GC làm việc trong lúc mutators tiếp tục; pauses nếu có cần xem những pha/logs riêng.

**Câu 7.** Nếu ZGC có pause nhỏ nhưng CPU usage tăng, ứng dụng có chắc nhanh hơn không?

**Đáp án:** Không. CPU contention/barrier overhead có thể ảnh hưởng throughput và latency; phải benchmark end-to-end.

**Câu 8.** Vì sao Parallel GC có thể giúp batch job?

**Đáp án:** Nhiều GC workers có thể rút ngắn wall time của các pha collection trên hệ đa core, cải thiện throughput khi pauses dài vẫn chấp nhận được.

**Câu 9.** Có thể xem `-XX:MaxGCPauseMillis=50` là SLA p99 dưới 50ms không?

**Đáp án:** Không. Đó là GC pause goal, không bảo đảm mọi pause hay request response time dưới 50ms.

**Câu 10.** Concurrent collector còn làm việc khi mutators tạo thêm nhiều objects. Rủi ro gì?

**Đáp án:** Heap headroom có thể cạn trước khi GC reclaim đủ, gây allocation stalls/fallback/OOME tùy collector.

**Câu 11.** ZGC và Shenandoah có ở mọi Java 17 distribution không?

**Đáp án:** Không thể giả định. Tính năng và độ hỗ trợ phụ thuộc VM vendor/build/platform.

**Câu 12.** G1 young collection thường STW hay concurrent với ứng dụng?

**Đáp án:** Việc evacuation của selected young regions diễn ra trong **STW pause**; các GC workers có thể hoạt động parallel.

**Câu 13.** Vì sao không đo tốc độ GC bằng một dòng tổng elapsed time rồi so sánh?

**Đáp án:** Một collector có thể có concurrent work không dừng mutators; cần tách pause times, CPU work, throughput và effect đến application.

**Câu 14.** Thấy gc worker count 8 trong log, có chứng minh 8 CPU core thật chạy cùng lúc không?

**Đáp án:** Không. Số worker threads không đảm bảo parallel hardware execution, đặc biệt với CPU quota/throttling.

**Câu 15.** Đổi từ Serial sang Parallel luôn giảm p99 request latency?

**Đáp án:** Không. Có thể giảm một số STW duration nhưng còn overhead và workload differences; cần đo.

**Câu 16.** Vì sao `GCWorkloadDemo` có cùng stdout khi chạy các collector khác nhau?

**Đáp án:** GC chọn cách thu hồi bộ nhớ nhưng phải giữ Java program semantics. Checksum và các phần tử trong list được giữ có kết quả xác định.

**Câu 17.** Khi nào cân nhắc ZGC thay G1?

**Đáp án:** Khi latency SLO khó đạt dù đã tối ưu workload/heap và G1, trong khi runtime hỗ trợ ZGC và CPU/heap headroom cho concurrent GC. Phải xác minh bằng benchmark.

**Câu 18.** Nếu G1 đang concurrent mark nhưng có xen STW young collection, gọi G1 là concurrent có sai không?

**Đáp án:** Không. G1 là mostly concurrent ở một số công việc, nhưng vẫn có pauses. Cần mô tả ở cấp pha.

## 23. Checklist lựa chọn và vận hành

1. Xác định **SLO latency**, **throughput** và ngân sách CPU/memory.
2. Ghi nhận JVM vendor, version, architecture, cgroup quota và heap limits.
3. Bắt đầu với collector default nếu chưa có yêu cầu đặc biệt, xem log xác nhận collector thực tế.
4. Đo allocation rate, live set, promotion và heap headroom.
5. Quan sát **STW pause distribution**, không chỉ tổng GC duration.
6. Quan sát **CPU contention trong concurrent phases**.
7. Xem Full GC fallback, allocation stalls và OOME nếu có.
8. Với G1, hiểu concurrent marking khác STW evacuation.
9. Với Parallel, nhớ GC workers parallel không có nghĩa mutators còn chạy trong STW.
10. Với low-latency collectors, không giả định zero pause hoặc free CPU cost.
11. So sánh workloads sau warm-up, lặp runs, cùng resource limits.
12. Nếu container bị throttling, xem quota và available processor count.
13. Xem các cờ hiện tại bằng `java -XX:+PrintFlagsFinal -version`, đừng đoán defaults.
14. Kiểm tra hỗ trợ collector trước khi đặt cờ, nhất là Shenandoah và flags qua JDK versions.
15. Không cố kích hoạt GC trong unit test rồi đo số events cố định.
16. Sửa **unbounded allocation hoặc object retention** trước khi coi GC flag là thuốc chữa mọi vấn đề.
17. Với hệ thống production, kết hợp GC logs, JFR, application tracing và OS metrics.
18. Đảm bảo mọi tối ưu collector không đổi semantics và correctness của ứng dụng.

### Tổng kết cuối

```text
                   Có bao nhiêu GC workers?
                     1              N
                     |              |
               Serial GC work    Parallel GC work
                     \              /
                      \            /
               Mutators có còn chạy?
                 /             \
               Không            Có
                |                |
                v                v
           STW phase         Concurrent phase
                |                |
          Application pause    GC & application
                               cùng tiến triển
                                (chia sẻ CPU)

Một collector hiện đại có thể dùng N GC workers trong STW
VÀ nhiều GC workers chạy concurrent ở một pha khác.
```

**Kết luận:** **Serial/parallel** mô tả **mức độ song song của công việc GC**, còn **STW/concurrent** mô tả **GC làm việc trong khi ứng dụng bị dừng hay còn chạy**. Đừng gộp hai trục thành một.

### Tài liệu tham khảo chính thức

- [Oracle JDK 17 — Available Collectors](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html): Serial, Parallel, G1, ZGC và các tiêu chí lựa chọn.
- [Oracle JDK 17 — G1 Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): GC workers, STW evacuation, concurrent marking, pause goals.
- [Oracle — G1 Garbage Collector Phases](https://www.oracle.com/java/technologies/g1gc.html): SATB, remark, young/mixed evacuation, phase-specific concurrency.
- [OpenJDK JEP 333 — ZGC: A Scalable Low-Latency Garbage Collector](https://openjdk.org/jeps/333): concurrent GC và low-latency objectives.
- [OpenJDK JEP 189 — Shenandoah: A Low-Pause-Time Garbage Collector](https://openjdk.org/jeps/189): concurrent compaction/reclamation.
- [OpenJDK JEP 363 — Remove the Concurrent Mark Sweep (CMS) Garbage Collector](https://openjdk.org/jeps/363): CMS lịch sử không còn trong JDK 14+ HotSpot.
- [Oracle JDK 17 — java command options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): lựa chọn collector và Unified Logging `-Xlog`.
- [JVMS `2.5.3` — Heap (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): automatic storage management không bắt buộc một GC implementation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-006">Stop-the-world và safepoint; phân biệt thời gian GC với tổng thời gian dừng</a>

<details>
<summary>Xem chi tiết</summary>

# Stop-the-world và safepoint; phân biệt thời gian GC với tổng thời gian dừng

## 1. Tổng quan: GC, STW và safepoint không đồng nghĩa

Trong một JVM, **garbage collection (GC)** quản lý khả năng truy cập và thu hồi tài nguyên heap, **stop-the-world (STW)** mô tả khoảng thời gian các luồng Java liên quan phải ngừng thực thi mã ứng dụng thông thường để VM làm một operation, còn **safepoint** là cơ chế phối hợp đưa các luồng tới trạng thái an toàn cho những operation đó.

Phân biệt ba nhận xét:

- **Có GC nhưng không phải mọi thời gian GC đều STW.** Collector concurrent có thể mark hoặc thực hiện các phase khác khi application threads vẫn chạy.
- **Có safepoint nhưng không hề chạy GC.** VM có thể cần phối hợp global safepoint cho những tác vụ nội bộ khác, tùy JDK và implementation.
- **Có request latency tăng nhưng không phải do GC hay safepoint.** Lock, socket, CPU starvation, scheduling, I/O hoặc cgroup throttling có thể làm ứng dụng chậm.

Mục tiêu bài học là **phân biệt phạm vi những đồng hồ đo**: một dòng GC `Pause ... 0.5ms`, một dòng safepoint `Total: ... ns`, một biểu đồ GC CPU time và một request latency 120 ms **không tự động đo cùng một khoảng**.

> **Tư duy đúng:** cần hỏi mỗi metric ghi nhận **work của GC**, **wall-clock GC pause**, **thời gian tiến đến safepoint**, **tổng khoảng VM phối hợp dừng**, hay **thời gian end-to-end của người dùng**.

### Hai tình huống ngược nhau

**Trường hợp 1:** GC thực hiện công việc STW trong 2 ms, nhưng phải đợi Java threads đạt safe states tới 30 ms. JVM stop interval liên quan safepoint có thể dài hơn đáng kể 2 ms. Tối ưu thuật toán copy object có thể không giải quyết nút thắt **time to safepoint**.

**Trường hợp 2:** một concurrent GC cycle mất 500 ms wall-clock nhưng đa phần thời gian Java threads vẫn thực thi, chỉ có vài pause ngắn. Gọi cả 500 ms là “application stopped” sẽ sai.

## 2. Safepoint vận hành như thế nào?

JVM cần biết khi nào một thread đang ở trạng thái mà một số runtime operations có thể kiểm tra hoặc thay đổi object references, stacks, metadata và cấu trúc nội bộ một cách nhất quán. Trong HotSpot, compiled/interpreted code và thread transitions hỗ trợ các **safepoint polls** hoặc safe states thích hợp. Vị trí và chiến lược poll do JVM/JIT quyết định, không phải do source Java đặt thủ công ở từng dòng.

Một global safepoint có thể hình dung:

~~~text
              JVM yêu cầu global safepoint
                          |
           +--------------+---------------+
           |              |               |
 Thread A đang Java  Thread B đang Java  Thread C blocked/native
           |              |               |
     poll / safe     thêm vài lệnh        VM đánh giá
       state          rồi safe          safe state
           |              |               |
           +--------------+---------------+
                          |
                 Các điều kiện đạt
                          |
                 VM operation chạy
                          |
                  Java tiếp tục
~~~

Các luồng không nhất thiết cùng dừng trong một microsecond. Thread A có thể ngừng sớm rồi chờ thread B. Điều này là một phần lý do **reaching safepoint** có thể ảnh hưởng trải nghiệm của ứng dụng dù work ở safepoint tương đối ngắn.

**Phân biệt thêm:**

- Global safepoint là cơ chế phối hợp toàn cục đối với những Java threads cần thiết.
- Một số operation có thể dùng **thread-local handshake**, nhắm tới một hoặc nhiều threads thay vì tạo global pause.
- `Thread.sleep`, `Thread.yield`, `Thread.State.WAITING` không phải API cho Java developer tự buộc VM safepoint.
- Native OS threads không nhất thiết bị ngừng tuyệt đối chỉ vì các Java mutators đang STW. Native transitions và một số JNI critical regions có quy tắc riêng.

**Stop-the-world không có nghĩa toàn bộ máy tính, kernel, native code, GC worker và mọi tiến trình khác bị đóng băng.** Nó là ràng buộc đối với hoạt động Java application theo giao thức runtime.

## 3. Phân rã một lần safepoint thành những khoảng đo

Trong một **timeline khái niệm**, ta có bốn mốc:

~~~text
        t_request              t_safe           t_work_end       t_resume
             |                    |                  |               |
             v                    v                  v               v
 Java đang chạy |--- phối hợp ----|--- VM work -------|-- hoàn tất ---| Java chạy
                 reaching/TTSP       at safepoint       leaving
             |<---------------- total safepoint --------------------->|
~~~

Ở các log HotSpot hiện đại, có thể gặp những trường:

- **Reaching safepoint:** khoảng phối hợp từ lúc bắt đầu yêu cầu đến khi các threads cần thiết ở safe states. Thường liên hệ với **time to safepoint (TTSP)**.
- **At safepoint:** elapsed interval khi VM ở điều kiện safepoint thực hiện operation và các phần overhead liên quan.
- **Leaving safepoint:** phần hoàn tất/cho phép Java thực thi tiếp.
- **Total:** phạm vi total safepoint theo timer của JVM và bản log đó.

Các mốc là mô hình để giải thích. **Không lấy nhãn của một bản HotSpot làm chuẩn JVM Specification cho mọi collector/JDK.**

### 3.1. Reaching safepoint không phải GC heap work

TTSP cao có thể do:

- Thread phản hồi safepoint poll chậm do compiled code hoặc native transition.
- Một luồng chưa được scheduler cấp CPU đủ kịp để đến safe state.
- CPU saturation, container CPU throttling hoặc concurrency quá cao.
- JNI critical region và các điều kiện đặc biệt ở runtime.
- Chi phí synchronization/coordinating giữa các threads.

Không kết luận “GC mark chậm” khi log cho biết hầu hết thời gian nằm ở **reaching**: GC work ở safepoint thậm chí còn chưa bắt đầu.

### 3.2. At safepoint không nhất thiết bằng GC elapsed

At-safepoint interval có thể gồm GC work thực sự cùng overhead VM operation, safepoint bookkeeping, preparation hoặc completion tương ứng. Một dòng GC summary có ranh giới timer riêng và có thể nhỏ hơn số **At safepoint** hoặc **Total**.

Nếu safepoint reason không liên quan GC, có thể có **At safepoint** đáng kể mà **không có GC pause work**.

### 3.3. Leaving safepoint chưa phải toàn bộ thời gian request phục hồi

VM hoàn tất handoff cho application threads không đảm bảo mọi thread được OS lên lịch thực thi ngay lập tức. CPU contention, ready queue và business code còn lại có thể kéo request ra xa mốc JVM báo `Total`.

Do đó **total safepoint wall time** khác **độ trễ tăng thêm do user/client quan sát**.

## 4. Mô hình toán học: chỉ cộng những khoảng cùng phạm vi

Nếu mọi mốc trên một monotonic clock:

~~~text
TTSP              = t_safe - t_request
GC_work_elapsed   = t_work_end - t_safe   (mô hình GC duy nhất đơn giản)
At_safepoint      = t_resume - t_safe
Total_safepoint   = t_resume - t_request
Request_latency   = t_response - t_request_start
~~~

Trong mô hình này, At-safepoint có thể lớn hơn `GC_work_elapsed` vì overhead sau khi GC algorithm hoàn tất. Request latency có thể gồm processing, queueing, I/O và những pauses khác.

**Không thể suy rộng:** một concurrent GC cycle có nhiều pause hoặc work song song không thể quy về đúng một `t_safe` và một `t_work_end`. Không được cộng các durations của nhiều GC worker threads thành elapsed duration khi chúng chạy đồng thời; đó có thể là **CPU time**.

### Ví dụ con số giả định

| Đại lượng | Giá trị |
| --- | ---: |
| VM yêu cầu safepoint | 4.0 ms từ lúc request bắt đầu |
| Thread cuối tới trạng thái an toàn | 6.5 ms |
| GC work kết thúc | 13.0 ms |
| JVM cho phép Java tiếp tục | 14.0 ms |
| Request trả response | 23.5 ms |

Kết quả tính ra TTSP = **2.5 ms**, GC work = **6.5 ms**, At-safepoint = **7.5 ms**, Total-safepoint = **10.0 ms**, Request latency = **23.5 ms**. Đây không phải các giá trị “không khớp”; chúng là những khoảng đo khác nhau.

## 5. Chương trình Java 17 kiểm tra phép tính một cách xác định

Tạo file **`PauseBudgetDemo.java`**:

~~~java
import java.util.Locale;

public final class PauseBudgetDemo {
    static String ms(long micros) {
        return String.format(Locale.ROOT, "%.1f ms", micros / 1000.0);
    }

    public static void main(String[] args) {
        // Các mốc giả lập đều có đơn vị microseconds.
        long requestStart = 0;
        long safepointRequested = 4_000;
        long allThreadsSafe = 6_500;
        long gcWorkFinished = 13_000;
        long threadsResumed = 14_000;
        long requestFinished = 23_500;

        System.out.println("sync=" +
                ms(allThreadsSafe - safepointRequested));
        System.out.println("gcWork=" +
                ms(gcWorkFinished - allThreadsSafe));
        System.out.println("atSafepoint=" +
                ms(threadsResumed - allThreadsSafe));
        System.out.println("totalSafepoint=" +
                ms(threadsResumed - safepointRequested));
        System.out.println("requestLatency=" +
                ms(requestFinished - requestStart));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 PauseBudgetDemo.java
java PauseBudgetDemo
~~~

**Stdout đã biên dịch/chạy đối chiếu thực tế (JDK 24, target Java 17):**

~~~text
sync=2.5 ms
gcWork=6.5 ms
atSafepoint=7.5 ms
totalSafepoint=10.0 ms
requestLatency=23.5 ms
~~~

Điểm học tập:

1. `sync` là độ trễ phối hợp safepoint, **không tự là thời gian GC algorithm**.
2. `gcWork` đo một vùng work giả định duy nhất; không phải CPU time tổng của nhiều GC workers.
3. `atSafepoint` = 7.5 ms, lớn hơn GC work 1.0 ms do overhead giả định.
4. `totalSafepoint` = 10 ms cộng giai đoạn phối hợp với giai đoạn VM ở safepoint.
5. Request latency = 23.5 ms vì ngoài khoảng safepoint còn business processing/wait.

**Đây là chương trình giải tích các timestamps giả lập, không phải Java API đọc thời gian safepoint thực tế.** Để quan sát JVM thật, phải dùng logs/JFR ở các mục sau.

## 6. Stop-the-world trong các collector phổ biến

### Serial GC và Parallel GC

- Serial collector thực hiện những collection pauses cần dừng Java mutators; nó dùng ít GC worker hơn các collector song song.
- Parallel GC có thể sử dụng nhiều GC workers để thu gom **trong một STW pause**. Vì hoạt động song song, **tổng CPU time** của GC workers có thể **lớn hơn** wall-clock elapsed pause.
- Khi GC log ghi `User`, `Sys`, `Real` (tùy collector/level), `Real` phản ánh elapsed wall-clock theo phạm vi event, không giống phép cộng CPU time của nhiều cores.
- Một GC pause ngắn nhưng xảy ra thường xuyên vẫn có thể ảnh hưởng throughput; một pause hiếm và dài có thể ảnh hưởng tail latency.

### G1 GC

G1 dùng các evacuation pauses, đồng thời có nhiều bước concurrent như concurrent marking. Trên HotSpot/JDK 17, tùy workload bạn có thể thấy young/mixed pauses, remark/cleanup phases hoặc failure-related full collections.

**Không gọi toàn bộ thời gian concurrent marking là “application stopped”.** Java application có thể chạy trong phần lớn concurrent cycle. Đồng thời, concurrent GC vẫn có thể cạnh tranh CPU/memory bandwidth làm throughput/latency thay đổi mà không phải STW.

`-XX:MaxGCPauseMillis` là một **mục tiêu tuning tương ứng**, không phải hard deadline cho mọi GC event, safepoint hoặc request p99.

### ZGC và Shenandoah

Các concurrent low-latency collectors tìm cách dời nhiều GC work ra khỏi global STW và giảm những pauses cần thiết, nhưng không nên tuyên bố chúng **không bao giờ STW** hoặc không có operation safepoint khác.

Mức hỗ trợ, collector phases và flags khác nhau giữa JDK builds/versions; ví dụ một số distribution Java 17 không đóng gói Shenandoah. Hãy kiểm tra `java -version`, các flags và collector đang dùng trước khi so sánh.

### Concurrent GC có thể làm chậm dù pause log nhỏ

Nếu concurrent threads của collector dùng nhiều cores và memory bandwidth, business threads có thể chạy chậm hơn ngay cả khi stop interval ngắn. Khi p99 cao nhưng safepoint total thấp, điều tra **CPU contention, allocator pressure, work queues và system throttling** thay vì kết luận GC chắc chắn không liên quan.

## 7. Bài thực hành Java 17 tạo GC log và safepoint log thực tế

Tạo file **`GcTraceLab.java`**:

~~~java
import java.lang.ref.Reference;

public final class GcTraceLab {
    public static void main(String[] args) {
        byte[][] recent = new byte[4][];
        long allocated = 0;

        for (int i = 0; i < 240; i++) {
            recent[i % recent.length] = new byte[256 * 1024];
            allocated += recent[i % recent.length].length;

            if (i % 60 == 0) {
                System.out.println("phase=" + (i / 60));
            }
        }

        System.out.println("allocatedMiB=" +
                (allocated / (1024 * 1024)));

        Reference.reachabilityFence(recent);
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 GcTraceLab.java
~~~

Với **HotSpot** và Bash/Linux/macOS, chạy bằng heap 32 MiB, Serial GC, ghi log vào file:

~~~bash
java -Xms32m -Xmx32m -XX:+UseSerialGC \
  '-Xlog:gc=info,safepoint=info:file=gc-safepoint.log:uptime,level,tags' \
  GcTraceLab
~~~

Trong PowerShell trên Windows, dùng một dòng:

~~~powershell
java -Xms32m -Xmx32m -XX:+UseSerialGC '-Xlog:gc=info,safepoint=info:file=gc-safepoint.log:uptime,level,tags' GcTraceLab
~~~

**Stdout của chương trình trong lần chạy bình thường:**

~~~text
phase=0
phase=1
phase=2
phase=3
allocatedMiB=60
~~~

Chương trình đã được **biên dịch bằng `javac --release 17` và chạy trên JDK 24** với các flags này, stdout đúng 5 dòng. Tổng phân bổ lũy kế = 240 × 256 KiB = 60 MiB; **không có nghĩa giữ đồng thời 60 MiB**, vì mảng `recent` có 4 slots liên tục thay bằng mảng mới.

**Các con số VM log không cố định:** số lần GC, loại pause, heap occupancy và durations tùy JDK, máy, collector và lần chạy. Việc ép Serial GC và heap nhỏ làm log dễ quan sát, không biến thời gian GC thành một kết quả xác định theo Java Specification.

### Một cặp GC và safepoint log quan sát thực tế

Trong một lần chạy JDK 24 đã nêu, file log có cặp:

~~~text
[0.032s][info][gc] GC(0) Pause Young (Allocation Failure) 9M->2M(30M) 0.496ms
[0.032s][info][safepoint] Safepoint "SerialCollectForAllocation", Time since last: 16604300 ns, Reaching safepoint: 7500 ns, At safepoint: 573500 ns, Leaving safepoint: 1400 ns, Total: 582400 ns
~~~

Phân tích:

- `GC(0)` cùng cause `Allocation Failure` gắn với một young collection cần STW.
- GC summary ghi **0.496 ms** theo phạm vi timer của GC summary.
- Safepoint line ghi `Reaching = 7500 ns` (**0.0075 ms**), `At = 573500 ns` (**0.5735 ms**), `Leaving = 1400 ns` (**0.0014 ms**), Total = **582400 ns = 0.5824 ms**.
- Trong event này, total safepoint **không bằng** GC summary duration; các timers bao phủ các phần khác nhau, không chứng minh metric bị hỏng.
- Đây là **một mẫu dữ liệu của HotSpot 24**, không phải format hoặc kết quả cố định của tất cả JDK 17, G1, ZGC hay một process production khác.

Cách đọc đúng: quan sát các **phase intervals liên hệ trong cùng timeline**; không lấy GC id bất kỳ ghép với safepoint ở thời điểm khác.

## 8. Công cụ `-Xlog`: GC và safepoint là hai nguồn đo

Unified Logging của HotSpot Java 17+:

~~~bash
java -Xlog:gc=info,safepoint=info Main
~~~

`Main` là **tên main class minh họa**, cần thay bằng class thực. Dùng `java -Xlog:help` để xem các tags/levels mà bản JVM cụ thể hỗ trợ.

| Cấu hình | Bạn quan sát được gì? |
| --- | --- |
| `-Xlog:gc=info` | GC cause/type, event summaries và một số elapsed pauses |
| `-Xlog:safepoint=info` | Safepoint operation name, reaching/at/leaving/total khi có trong format phiên bản đó |
| `-Xlog:gc*=debug` | Nhiều thông tin GC phases và subtags hơn, log có thể rất lớn |
| `-Xlog:safepoint=debug` | Chi tiết safepoint coordination ở mức cao hơn nếu JVM hỗ trợ |
| `-Xlog:gc+cpu=info` | Một số số liệu GC user/sys/real CPU nếu tag/event hỗ trợ |
| `-Xlog:help` | Xác minh tags, decorators và cú pháp trên runtime thật |

Một dòng GC summary có thể có format:

~~~text
[0.123s][info][gc] GC(7) Pause Young (...) 64M->19M(128M) 1.234ms
~~~

Dòng trên là **minh họa**, không phải stdout cố định. Đừng coi `1.234ms` là toàn bộ thời gian mọi request bị dừng, hay giả định GC events gồm chỉ một pause.

### Các bẫy khi đối chiếu

1. Đơn vị khác nhau: GC pause có thể in **ms**, safepoint fields có thể in **ns**; **1 ms = 1,000,000 ns**.
2. Concurrent phases chạy chồng lắp với Java work; không cộng wall time của chúng vào STW total.
3. Worker phase durations có thể chạy song song; **tổng work time** không bằng elapsed wall time.
4. GC event và safepoint event có identifiers khác nhau; ghép theo **operation cause, timestamp và timeline**, không ghép bừa theo thứ tự dòng nếu nhiều activities.
5. GC logging chi tiết cũng tạo overhead; benchmark cần cố định logging configuration giữa các lần.
6. `-Xlog` và GC tuning flags là HotSpot-specific tools; JVM khác có logging interface khác.
7. Dòng `GC Pause` không bao trọn công việc VM ở safepoint, và safepoint có thể không liên quan GC.

## 9. JFR, JMC và phân tích theo cùng trục thời gian

**Java Flight Recorder (JFR)** cung cấp events có timestamps và durations, hữu ích hơn việc chỉ nhìn một con số GC. Tên events hoặc threshold tùy cấu hình JDK, nhưng những nhóm thường quan tâm gồm:

- `jdk.GarbageCollection` và `jdk.GCPhasePause` cho GC events/phases.
- Safepoint-related events như `jdk.SafepointBegin`/`jdk.SafepointEnd` khi khả dụng và được bật.
- Một số VM operation, class loading, compilation, allocation và thread scheduling/park/monitor events.
- CPU, I/O và thread stacks giúp xác định các nguyên nhân ngoài GC.

**Không mặc định mọi event được bật bởi recording settings mặc định.** Phải kiểm tra JFR event settings của JDK đang chạy và biết duration field đo gì.

### Thu thập JFR

Khi khởi động application:

~~~bash
java '-XX:StartFlightRecording=filename=latency.jfr,duration=60s,settings=profile' -jar app.jar
~~~

`app.jar` chỉ là artifact minh họa, phải thay bằng file thực.

Khi đã có JVM PID thực, dùng `jcmd`:

~~~text
jcmd PID JFR.start name=Latency settings=profile
jcmd PID JFR.dump name=Latency filename=latency.jfr
jcmd PID JFR.stop name=Latency
~~~

Thay `PID` bằng số process ID; các lệnh cần quyền OS/JVM phù hợp. Có thể xem recording bằng JDK Mission Control (JMC) hoặc `jfr` CLI:

~~~bash
jfr summary latency.jfr
jfr print --events jdk.GarbageCollection,jdk.GCPhasePause latency.jfr
~~~

JFR data có thể chứa method names, stack traces và thông tin nội bộ ứng dụng; hãy giữ file ở nơi được kiểm soát quyền truy cập.

### Vì sao cần app tracing đi cùng JFR?

Giả sử request mất 200 ms, trong khoảng đó có safepoint 4 ms nhưng 160 ms đang chờ database. Chỉ thấy GC 4 ms trùng thời điểm **không đủ** để quy toàn bộ request latency cho GC. Khi có request spans và JFR, bạn mới xác định critical path nào bị chặn.

## 10. Phân biệt GC CPU time, elapsed time và tổng application pause

**GC CPU time**: thời gian CPU mà GC threads tiêu thụ (tùy cách thống kê), có thể tổng hợp từ nhiều cores/threads. Khi bốn GC workers cùng chạy 5 ms CPU, tổng GC user CPU có thể gần **20 ms** trong khi elapsed pause xấp xỉ **5 ms** cộng overhead.

**GC wall elapsed time**: độ dài theo đồng hồ thực của một GC event, pause hoặc concurrent cycle, phụ thuộc loại event. Elapsed của concurrent cycle **không bằng** STW.

**Safepoint total elapsed**: từ mốc JVM yêu cầu tới completion/release theo timer của log, có thể bao gồm reaching và overhead, và có thể là **một VM operation không phải GC**.

**Application stopped time**: thời gian Java mutator progress bị dừng/giới hạn bởi những safepoints và hoạt động VM, được công cụ ghi theo phạm vi riêng.

**Request end-to-end latency**: gồm application compute, locks, queuing, network, downstream services, scheduling, concurrent CPU contention và những STW intervals nằm trên đường xử lý.

Những metric này không được dùng thay thế lẫn nhau.

| Câu hỏi | Số đo cần xem |
| --- | --- |
| GC work pause một event bao lâu? | GC pause summary/phase event |
| Đợi tất cả threads tới safe states bao lâu? | `Reaching safepoint`, TTSP |
| JVM ở global safepoint bao lâu? | `At safepoint` và `Total` |
| GC workers tốn bao nhiêu CPU? | GC user/sys CPU times |
| Người dùng cảm thấy chậm bao lâu? | Request latency tracing/p99 |
| Concurrent GC ảnh hưởng CPU/throughput? | CPU utilization, allocation rate, concurrent phases |
| Vì sao request pause trùng mốc GC? | Correlated GC + safepoint + JFR + request spans |

### P99 không thể suy ra bằng tổng GC time chia tổng uptime

Ví dụ tổng 300 ms STW trong một phút không nói chính xác p99 latency tăng mấy ms. Một pause 100 ms trúng request critical path có thể tác động tail latency rất khác 100 pauses x 1 ms phân bố rải rác. Cần histogram/distribution và khả năng overlap với requests.

## 11. Những nguyên nhân safepoint không phải GC

Không phải global safepoint nào cũng để thu gom rác. Trong HotSpot, tùy JVM version và cơ chế:

- Một số operations liên quan deoptimization hoặc management of compiled code.
- Một số class redefinition/instrumentation/tooling actions.
- Một số VM runtime maintenance tasks.
- Một số diagnostics cần nhất quán trên nhiều Java stacks/threads.

Nhiều operations hiện đại có thể dùng **thread-local handshake**, vì vậy không khẳng định rằng **mọi thread dump, mọi stack inspection hoặc mọi deoptimization** đều global STW. Hãy đọc **safepoint operation name** và JFR events của bản JVM thật.

Nếu safepoint 20 ms không thuộc GC, nó vẫn có thể ảnh hưởng throughput/latency, nhưng không được ghi nhận là “GC algorithm mất 20 ms”.

## 12. Chẩn đoán TTSP dài, GC dài và request latency dài

Một checklist phân nhánh ngắn:

**Trường hợp A — `Reaching safepoint` cao, GC summary thấp.** Điều tra phản hồi safepoint polls, native/JNI transitions, scheduling và CPU quota. Đừng vội tăng số GC threads.

**Trường hợp B — `At safepoint` cao, reaching thấp.** Xác định operation name. Nếu là GC, xem collector phases/root scanning, heap pressure, reference processing và evacuation; nếu là VM op khác, chuyển hướng tới operation tương ứng.

**Trường hợp C — GC CPU cao hơn GC wall elapsed.** Có thể do GC workers chạy parallel hoặc concurrent; không tính CPU user như elapsed pause.

**Trường hợp D — GC pause thấp, p99 vẫn cao.** Kiểm tra CPU contention do concurrent GC, locks, I/O, TCP/DB latency, OS scheduling, queue depth và safepoints khác.

**Trường hợp E — tổng safepoint thấp nhưng service throughput giảm.** Concurrent GC work cạnh tranh CPU/memory bandwidth; allocator churn và object allocation rate có thể gây pressure. Cần xem GC frequency và CPU utilization.

**Trường hợp F — một request rất chậm trùng nhiều safepoints.** Đối chiếu từng interval và request critical path, tránh cộng các khoảng trùng nhau hoặc cộng cả concurrent cycle.

### Một event mô phỏng TTSP gây chậm

~~~text
Reaching safepoint  = 41 ms
At safepoint        =  2 ms
Leaving safepoint   =  1 ms
Total               = 44 ms

GC work summary có thể chỉ ~1.6 ms.
~~~

Đây là **tình huống số giả định**. Dù GC algorithm chỉ hơn 1 ms, tổng khoảng VM safepoint có thể dài vì phối hợp threads. Không kết luận JVM đã “pause 44ms chỉ để quét object”.

### Một event mô phỏng non-GC safepoint

~~~text
Operation = VM maintenance (không phải GC)
Reaching  = 1 ms
At        = 30 ms
Leaving   = 1 ms
Total     = 32 ms
~~~

Đây cũng là **mô phỏng**, không phải tên event/format exact JDK. Dòng 32 ms có thể ảnh hưởng application latency nhưng không nên cộng vào **GC pause work**.

## 13. Những nhận định sai thường gặp

1. **“GC time = mọi thời gian Java application dừng.”** Sai; phải đọc phạm vi timer.
2. **“Safepoint chính là GC.”** Sai, VM có những safepoints vì operations khác.
3. **“GC concurrent toàn bộ thời gian là STW.”** Sai.
4. **“Stop-the-world làm mọi native OS thread dừng.”** Sai.
5. **“Tất cả Java threads dừng đúng cùng một nanosecond.”** Sai; có giai đoạn reaching/synchronization.
6. **“GC Pause 1 ms thì request p99 bị tăng tối đa 1 ms.”** Sai.
7. **“At safepoint không có overhead.”** Sai.
8. **“TTSP dài chứng minh GC mark chậm.”** Sai, GC work có thể chưa bắt đầu.
9. **“GC CPU time giống wall-clock pause.”** Sai với parallel/concurrent work.
10. **“Concurrent GC không ảnh hưởng latency.”** Sai, có cạnh tranh CPU/memory bandwidth.
11. **“ZGC không cần safepoint nào trong bất kỳ trường hợp nào.”** Không có bảo đảm như vậy.
12. **“MaxGCPauseMillis là giới hạn cứng của request latency.”** Sai.
13. **“Cộng tất cả GC worker thread durations là STW elapsed.”** Sai.
14. **“Một GC summary đủ giải thích HTTP 500 ms.”** Sai.
15. **“Có một safepoint thì mọi requests chậm đúng toàn bộ thời lượng đó.”** Sai, còn critical path.
16. **“Có GC concurrent 500 ms thì ứng dụng dừng 500 ms.”** Sai.
17. **“GC log và safepoint log phải có duration giống hệt.”** Sai.
18. **“Safepoint poll là câu lệnh Java có thể gọi thủ công.”** Sai.
19. **“Dòng Total 582400 ns bằng 582.4 ms.”** Sai: bằng 0.5824 ms.
20. **“Nếu GC không xuất hiện trong log, chắc không có nguyên nhân JVM nào khác.”** Sai.

## 14. Tự kiểm tra và đáp án

1. GC work 7 ms, reaching 3 ms, overhead còn lại 2 ms. Trong mô hình không trùng, total safepoint? **12 ms**.
2. G1 concurrent marking 400 ms, hai pauses 5 và 3 ms. Application có dừng 400 ms không? **Không**, concurrent phase không đồng nghĩa STW.
3. Bốn GC workers chạy đồng thời mỗi worker 5 ms CPU. Total CPU có thể ~20 ms, wall elapsed ~5 ms? **Có**, cộng overhead.
4. VM maintenance safepoint 12 ms có ghi là GC pause 12 ms không? **Không**.
5. Safepoint reaching 20 ms và GC work 1 ms có chứng minh GC algorithm tốn 21 ms không? **Không**.
6. Request 150 ms chồng lắp safepoint 2 ms có thể quy toàn bộ 150 ms cho GC không? **Không**.
7. Lệnh HotSpot cơ bản xem GC/safepoint? **`-Xlog:gc=info,safepoint=info`**.
8. 582400 nanoseconds bằng bao nhiêu milliseconds? **0.5824 ms**.
9. Mọi thread inspection hiện đại đều global safepoint? **Không**, có targeted handshakes.
10. Collector concurrent có thể gây CPU contention dù pause nhỏ? **Có**.
11. `MaxGCPauseMillis` là hard bound p99 latency? **Không**.
12. `System.gc()` luôn buộc STW GC ngay lập tức? **Không**; phụ thuộc VM/collector/flags.
13. Vì sao GC CPU user có thể cao hơn GC elapsed? **Song song nhiều GC workers**.
14. Nếu total safepoint cao hơn GC pause, cần xem gì? **Reaching/At/Leaving, operation name, timer boundary**.
15. Nếu safepoint operation không phải GC, có bỏ qua khi điều tra latency? **Không**, nhưng phân loại đúng là non-GC.

## 15. Nguồn tham khảo Java 17 và HotSpot

- Oracle JDK 17 `java` tool: Unified JVM Logging, `-Xlog`, tags và decorators: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- Java SE 17 Garbage Collection Tuning Guide (Serial, Parallel, G1, low-latency collector): https://docs.oracle.com/en/java/javase/17/gctuning/
- Oracle JDK 17 Diagnostic Tools (JFR/JMC, GC pause và troubleshooting): https://docs.oracle.com/en/java/javase/17/troubleshoot/diagnostic-tools.html
- JDK 17 `jcmd`, JFR.start/JFR.dump/JFR.stop: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- JDK 17 `jfr` command: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jfr.html
- JDK 17 Java Flight Recorder module documentation: https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html
- JVM Specification 17, Runtime Data Areas: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5

**Kết luận:** **GC** là quá trình thu gom bộ nhớ; **STW** là khoảng application Java bị dừng/phối hợp; **safepoint** là cơ chế VM đưa các Java threads vào trạng thái an toàn khi cần. Số **GC pause** không tự bằng **total safepoint**, **GC CPU** không bằng **elapsed wall time**, và **request latency** còn chứa những nguyên nhân khác. Cần đọc đồng thời **GC logs**, **safepoint logs**, **JFR events** và **application tracing** theo cùng timeline, event, cause và đơn vị trước khi chẩn đoán hoặc tối ưu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-007">Write barrier, card table và remembered set khi theo dõi tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Write barrier, card table và remembered set khi theo dõi tham chiếu

## 1. Vấn đề cốt lõi

Một Java heap chứa hàng triệu đối tượng và các tham chiếu giữa chúng. Khi ứng dụng đang chạy, những phép gán như **holder.child = newChild** liên tục thay đổi đồ thị đối tượng. Nếu bộ gom rác (GC) chỉ thu gom một phần heap hoặc đánh dấu đồng thời với các thread ứng dụng, nó cần biết **các cạnh tham chiếu nào có thể đã thay đổi** mà không buộc phải quét toàn bộ heap sau mỗi phép gán.

Ba khái niệm giải quyết phần lớn câu chuyện trong các generational/region-based tracing collectors:

- **Write barrier:** phần logic nội bộ GC được thực hiện quanh thao tác ghi tham chiếu (trước hoặc sau ghi), giúp duy trì thông tin về biến đổi đồ thị heap.
- **Card table:** cấu trúc metadata chia các địa chỉ heap thành những **card** nhỏ; khi ghi vào một vị trí có tham chiếu đáng quan tâm, collector có thể đánh dấu card tương ứng là **dirty** để quét lại.
- **Remembered set (RSet):** cấu trúc tóm tắt những **vị trí nguồn ở ngoài vùng đang thu gom** có thể tham chiếu **vào vùng đó**; giúp thu gom vùng riêng mà không scan toàn bộ heap còn lại.

Với HotSpot **G1 Garbage Collector**, còn có hai nhiệm vụ barrier nên phân biệt rõ:

1. **Post-write barrier** phục vụ **tracking các tham chiếu chéo region**, đóng góp vào card/remembered-set maintenance.
2. **Pre-write barrier** phục vụ **Snapshot-At-The-Beginning (SATB)** trong concurrent marking, chủ yếu xử lý **tham chiếu cũ sắp bị ghi đè**.

Hai barrier này cùng liên quan write operations nhưng **không phải một thuật toán, không ghi cùng dữ liệu và không tồn tại chỉ nhằm tracking old-to-young**.

Sau bài, bạn có thể đọc một đồ thị references, dự đoán card nào phải được xét, giải thích ý nghĩa một inbound RSet, phân biệt SATB pre-/G1 post-barrier, hiểu chi phí đồng bộ hóa với mutator, dùng GC logs đúng cách và tránh kết luận sai về tính reachability từ một dirty card.

Bài sử dụng **HotSpot G1 trên JDK 17** làm ví dụ cụ thể và phân biệt rõ **nguyên lý tracing GC** với **chi tiết collector implementation**. JVMS **không bắt buộc** một JVM sử dụng card table, RSet hay chính các barriers của G1.

## 2. Nền tảng: GC roots, object graph và thu gom từng phần

Trong mô hình object graph:

- Một **object/array** là một đỉnh.
- Một **reference field/array element** tạo một cạnh có hướng.
- **GC roots** là các nguồn tham chiếu đang live.
- Một object còn **strongly reachable** nếu có đường tham chiếu mạnh phù hợp từ roots đến nó.

Ví dụ:

~~~text
Root --> Old object A --> Young object Y
             |
             +----------> Old object B

Root --> Young object Z
~~~

Nếu thu gom toàn heap, collector có thể bắt đầu tại tất cả roots rồi duyệt các cạnh theo thiết kế. Nhưng **minor/young collection** chỉ muốn xử lý young objects. Vấn đề: **Y có thể sống vì old object A đang trỏ tới nó**. Quét mỗi young region và thread roots mà bỏ qua old-to-young edges sẽ có nguy cơ xử lý Y như rác khi thực ra nó còn reachable.

### 2.1. Tại sao không quét toàn bộ old generation mỗi lần?

Giả sử old heap có 12 GiB nhưng young generation chỉ có vài trăm MiB. Quét lại tất cả old objects ở mọi young GC có thể rất đắt, đi ngược mục tiêu thu gom nhanh những objects ngắn hạn.

GC cần một **nguồn thông tin tóm tắt** cho biết: “Những vùng/vị trí ở ngoài collection set nào có thể chứa references đi vào collection set?”

Đây là lý do xuất hiện **remembered sets và card marking**. Chúng có thể cho phép một collector xử lý một phần heap trong khi vẫn xem xét **các cạnh từ bên ngoài phần được thu gom**.

### 2.2. G1 không chỉ có hai khoảng young/old liên tục

G1 phân heap thành **regions** (các đoạn địa chỉ heap có kích thước đều trong một cấu hình cụ thể). Tại mỗi thời điểm, một region có thể giữ Eden, Survivor, Old hoặc dữ liệu đặc biệt như humongous objects theo cơ chế collector.

~~~text
G1 heap (sơ đồ logic):
 +--------+--------+--------+--------+--------+--------+
 | Eden A | Old B  | Old C  | Surv D | Eden E | Old F  |
 +--------+--------+--------+--------+--------+--------+
           |         |                  ^
           +----------------------------+
                     |
           Có thể có cross-region refs
~~~

G1 có thể thu gom **collection set (CSet)** gồm những regions được chọn. Trong một young GC, phần CSet thường chứa young regions; một mixed collection còn có thể gồm một số old regions. Vì vậy **tracking cross-region references** là mục tiêu rộng hơn việc chỉ tracking old-to-young.

## 3. Write barrier là gì?

**Write barrier** trong GC là đoạn công việc mà runtime/compiler thêm vào đường thực hiện **một số thao tác ghi tham chiếu**. Nó có thể kiểm tra old/new reference, địa chỉ ô nhớ được ghi, trạng thái marking, region membership và cập nhật metadata/queues.

Biểu thức Java:

~~~java
owner.child = newChild;
~~~

về logic:

~~~text
Thông tin trước ghi: oldValue = owner.child
                  |
       [Có thể có pre-write barrier]
                  |
       STORE owner.child = newChild
                  |
       [Có thể có post-write barrier]
                  |
             Tiếp tục chạy
~~~

Đây là mô hình hành vi, **không** phải cam kết mọi object field store luôn sinh hai hàm gọi Java độc lập. Barriers thường được JIT/interpreter/VM intrinsics tạo ở mức thấp; compiler có thể tối ưu khi biết store không cần một số bookkeeping.

### 3.1. Pre-write vs post-write

| Đặc điểm | Pre-write barrier | Post-write barrier |
| --- | --- | --- |
| Mốc khái niệm | Trước khi overwrite một reference slot | Sau khi store reference mới |
| Dữ liệu quan tâm | Thường là **old referent** bị thay thế | Thường là **slot/source location** vừa đổi và/hoặc new referent |
| Ví dụ trong G1 | Ghi nhận old value cho **SATB concurrent marking** | Card marking/dirty-card queue phục vụ cross-region RSet |
| Tại sao cần? | Tránh làm mất cạnh thuộc snapshot đang được đánh dấu | Đừng bỏ sót nguồn reference mới đi vào collection region |
| Có cần mọi lúc? | Phụ thuộc marking phase và kiểm tra khác | Phụ thuộc kind of store, source/target regions và runtime conditions |

**Không có định nghĩa toàn cầu** rằng pre-write luôn dùng cho SATB hay post-write luôn dùng cho remembered set trong mọi collector. Đây là sự phân vai **điển hình ở G1 HotSpot JDK 17**. Các collectors khác dùng barriers khác, thậm chí đọc tham chiếu cũng có **load/read barriers**.

### 3.2. Write barrier khác memory barrier của Java Memory Model

GC write barrier **không đồng nghĩa** CPU memory fence, volatile semantics hoặc monitor synchronization.

- **Write barrier GC:** giữ đúng thông tin phục vụ collector.
- **Memory barrier/fence/JMM ordering:** phục vụ visibility/ordering/happens-before giữa threads.
- **Card table:** metadata GC không phải một cách publish đối tượng đến threads khác.

Một **owner.child = x** bình thường có thể làm thay đổi card metadata nhưng **không vì thế** tạo ra happens-before giữa các threads. Nếu cần thread safety, vẫn sử dụng volatile, locks, atomics hoặc synchronization phù hợp.

## 4. Card table: bản đồ đánh dấu các khối nhỏ của heap

Một **card** là một phần nhỏ của vùng địa chỉ heap được chia đều theo cơ chế card-table của collector. Với G1 HotSpot, kích thước card điển hình được tài liệu mô tả là **512 byte**, nhưng nên xem đây là **chi tiết phiên bản/implementation**, không phải hằng số của Java language.

Ví dụ giả định:

~~~text
Heap base = 0
Card size = 512 bytes

  Card 0: [    0,   511]
  Card 1: [  512,  1023]
  Card 2: [ 1024,  1535]
  Card 3: [ 1536,  2047]
  ...
~~~

Một mô hình ánh xạ đơn giản:

~~~text
cardIndex = floor((slotAddress - heapBase) / cardSize)
~~~

Nếu một reference field nằm tại địa chỉ offset **760**, thì:

~~~text
floor(760 / 512) = 1
~~~

Collector quan tâm đến **card 1** để có thể quét lại các reference slots trong khoảng địa chỉ liên quan.

### 4.1. Card table lưu cái gì?

Một mô hình học tập đơn giản:

~~~text
Heap:
  card 0  card 1  card 2  card 3
    |       |       |       |
    v       v       v       v
   [0..] [.. ]  [.. ]  [.. ]

Card-table metadata:
   CLEAN  DIRTY   CLEAN  CLEAN
~~~

Trong implementations thật, một card có thể có nhiều trạng thái/bits hoặc cơ chế buffers khác nhau; không nhất thiết chỉ là một Java boolean.

**Dirty** chỉ là “nên xem xét lại vùng này”; nó **không có nghĩa**:

- Có chính xác một reference đã thay đổi.
- Target object còn sống hoặc vừa được tạo.
- Card chắc chắn chứa old-to-young reference ở thời điểm GC.
- Card nằm trong collection set.
- Mọi bytes trong card đều là Java references.
- Một dirty event tương ứng một bản ghi RSet mới.

Một card chứa nhiều objects/reference slots; ghi cùng card hàng trăm lần vẫn có thể được coalesced thành ít bookkeeping entries.

### 4.2. Vì sao granularity là card thay vì từng reference slot?

Nếu mỗi byte/slot store phải cập nhật một index chính xác tới từng object reference, metadata và synchronization có thể rất đắt. Đánh dấu **vùng nhỏ** là sự đánh đổi:

- **Ưu:** cập nhật tại mutator rẻ, metadata nhỏ, batch được nhiều writes.
- **Nhược:** collector có thể phải scan một card dù chỉ một reference field thay đổi, tức có **false positives/coarse precision**.

Đây là một ví dụ tối ưu **write-time overhead vs scan-time overhead**, không phải “mất thông tin dẫn tới thu hồi nhầm”. Collector phải giữ đủ độ đúng để không bỏ sót live references.

## 5. Remembered set: những nguồn nào tham chiếu vào một vùng?

**Remembered set (RSet)** là metadata giúp collector tìm references từ **ngoài vùng cần thu gom** vào bên trong. Ở mô hình G1 JDK 17, cách diễn giải thuận tiện là **inbound RSet** cho target region: một tập các source locations/cards có thể chứa pointers vào target.

Ví dụ:

~~~text
             outside collection set
  Old Region R1                  Old Region R2
  +-------------------+          +-------------------+
  | Object A          |          | Object B          |
  | field x ----------+--------+ | field y ----------+-------+
  +-------------------+        | +-------------------+       |
                               |                             |
                               v                             v
                        Young Region R3 (CSet)
                        +----------------------+
                        | Object P   Object Q  |
                        +----------------------+

Inbound RSet for R3 (minh họa):
    possible source cards in R1, R2
~~~

Khi thu gom R3, collector không cần scan nguyên R1 và R2; nó có thể xem những source cards được chỉ định và kiểm tra references thật.

### 5.1. RSet không phải danh sách tất cả objects đang sống

RSet thường chứa **các vị trí ứng viên** — có thể biểu diễn bằng cards, ranges hoặc các cấu trúc nén theo implementation. Khi quét:

- Một candidate card có thể chứa pointer vào R3.
- Một candidate card có thể đã không còn pointer liên quan vì reference slot bị sửa/null.
- Một card có thể chứa nhiều pointers thuộc nhiều objects.
- Một pointer thật vẫn phải được kiểm tra xem target thuộc collection set và phải được xử lý ra sao.

Do đó, RSet được dùng như **nguồn để tìm incoming edges**, không phải một “đồ thị complete object identity chính xác từng byte” cũng không phải reachability proof cho mọi object.

### 5.2. RSet là inbound, không phải outbound

Với cạnh **A trong R1 → B trong R3**:

- Đây là **outbound edge** khi đứng ở R1.
- Nhưng là **inbound edge** khi đứng ở R3.
- RSet của **target R3** ghi nhận **nơi nguồn từ R1 có thể tham chiếu vào R3**.

Nếu nói “RSet của R1 ghi các references R1 trỏ đi nơi khác”, đó không phải cách diễn giải chuẩn của **per-target inbound RSet** đang bàn. Một collector khác có thể có data structures khác, nên luôn nêu rõ hướng RSet.

### 5.3. Old-to-young là trường hợp phổ biến, không phải duy nhất

Với generational GC:

~~~text
Old O ----> Young Y
~~~

Đây là đường rất quan trọng trong young GC. Nhưng G1 còn tracking các cạnh **old region A → old region B** khi cần thu gom B trong mixed collection, và có những cross-region edges giữa các young regions.

Không nên rút gọn G1 remembered set thành “một list tất cả old objects trỏ young objects”. Nó là cấu trúc theo **regions/locations** với chính sách duy trì phụ thuộc collector.

## 6. Pipeline G1: write → dirty card → refinement → RSet

Ví dụ giả định một reference field trong source region R1 được cập nhật để trỏ vào target region R3:

~~~text
(1) Mutator thread:
    A.child = P       A thuộc R1, P thuộc R3
          |
          v
(2) G1 post-write barrier (điều kiện phù hợp)
    xác định reference/source slot đáng quan tâm
    đánh dấu source card là dirty
    có thể đưa card vào dirty-card queue
          |
          v
(3) Concurrent refinement / GC pause processing
    lấy dirty cards đã được ghi nhận
    scan references thích hợp trong card
    xác định actual cross-region targets
          |
          v
(4) Update remembered set metadata
    Inbound RSet của R3 ghi nhận source location/card
          |
          v
(5) Khi thu gom collection set gồm R3:
    scan incoming candidates từ RSet
    kiểm tra references thật và xử lý moved objects
~~~

Thành phần thực tế có thêm chính sách lược bớt, buffering, card states, collection-set handling, queue thresholds, forwarding pointers, remembered-set coarsening và các pha khác. Đây là **pipeline để hiểu trách nhiệm**, không phải đồ thị flow bắt buộc nguyên dạng ở mọi G1 build.

### 6.1. Vì sao không cập nhật RSet ngay trong mỗi assignment?

Lý do chính là giảm overhead của mutator:

- Có thể nhiều field writes cùng card.
- Một card có thể đổi target nhiều lần trước khi collector scan.
- Mutator có thể chỉ cần ghi metadata/queue; thread khác refinement theo batch.
- Cập nhật concurrent RSet trực tiếp từng reference có thể cần synchronization, allocation hoặc random memory accesses tốn kém.

G1 sử dụng **dirty card buffers/queues** và **concurrent refinement** để chuyển bớt công việc ra khỏi đường nóng của ứng dụng. Nhưng nếu backlog lớn, GC pause có thể phải xử lý các cập nhật RSet còn pending trước khi scan candidates.

### 6.2. Điều gì nếu write thay reference rồi lại xóa trước refinement?

~~~text
t0: A.child = null
t1: A.child = P (P in R3)     -> card dirty
t2: A.child = null            -> card lại có thể được ghi nhận
t3: refinement scan current slot
    A.child == null
~~~

Nếu chỉ xét slot này và không còn pointer khác vào R3 trong card, collector có thể loại/không bổ sung candidate tương ứng theo chính sách implementation. **Dirty card không chứng minh giá trị reference vừa được ghi vẫn còn hiện hữu**.

Đó cũng là lý do RSet processing thường phải đối chiếu **current references** khi phù hợp, không chỉ tin một sự kiện assignment lịch sử.

## 7. G1 pre-write barrier: SATB và old reference

G1 còn làm concurrent marking để xác định live objects trong old-generation areas phục vụ lựa chọn regions cho mixed collections. Đánh dấu diễn ra trong khi mutator có thể thay đổi đồ thị tham chiếu.

**SATB — Snapshot-At-The-Beginning** dùng khái niệm “giữ các đối tượng có trong snapshot ban đầu” theo thuật toán. Một quan hệ đang tồn tại vào thời điểm bắt đầu mark có thể bị mutator xóa trước khi marker thread duyệt cạnh đó.

Ví dụ:

~~~text
Khi bắt đầu marking (snapshot logic):
  Root ---> A ---> X

Mutator sắp ghi:
  A.child = Y

Trước STORE:
  oldValue = X
  G1 pre-barrier có thể enqueue X (nếu SATB marking active)

Sau STORE:
  A.child = Y
~~~

Nếu X chỉ còn được dẫn đến qua cạnh cũ **A.child**, việc xóa cạnh giữa lúc mark có thể làm marker không thấy X qua đường thông thường. Enqueue old reference trong **SATB buffer** giúp duy trì bất biến snapshot của concurrent mark, tránh bỏ sót một object cần được xem là live theo thuật toán.

Điểm cần nhớ:

- **Pre-barrier quan tâm old value** trước khi bị ghi đè, **không phải chỉ new value**.
- Thường chỉ active khi SATB concurrent marking đang cần; có fast-path guards.
- Một số writes có thể được compiler/VM tối ưu và không sinh thực hiện pre-barrier riêng nếu chứng minh an toàn.
- Objects được giữ live do snapshot dù sau đó không còn đường hiện thời có thể tạo **floating garbage**, được thu hồi ở các chu kỳ sau.

### 7.1. SATB pre-barrier có dùng card table để ghi old value không?

Không nên đồng nhất. SATB pre-barrier G1 thường đưa **old referent** vào một **SATB marking queue/buffer** khi điều kiện phù hợp. Trong khi đó, post-write barrier phục vụ **card/RSet tracking**. Cả hai có thể liên quan đến cùng một assignment nhưng **dữ liệu và invariant khác nhau**.

### 7.2. Nguy cơ nếu không xử lý cạnh bị xóa trong concurrent mark

Sơ đồ ba màu:

- **White:** chưa mark/khám phá.
- **Grey:** đã phát hiện nhưng chưa quét hết edges.
- **Black:** đã xử lý trong mô hình marking.

Nếu mutator xóa một đường dẫn tới object trắng thuộc snapshot trước khi marker phát hiện, có thể cần SATB bookkeeping để tránh sai lệch. Đây chỉ là trực giác; thuật toán thực có xử lý allocations sau snapshot, barriers, termination checks và GC-specific invariants.

Không suy rằng “X đã chuyển thành unreachable nên được thu hồi ngay trong cùng mark cycle” — SATB có thể cố ý giữ X qua chu kỳ hiện tại để bảo toàn safety.

## 8. G1 post-write barrier: card marking cho cross-region pointers

Ngược lại, post-write barrier tập trung vào **reference mới được lưu** và **địa chỉ source slot** vừa thay đổi. Khi đó collector cần biết có thể xuất hiện một edge từ region này tới region khác.

Sơ đồ:

~~~text
Trước:
    A thuộc R1
    A.child = null

Ghi:
    A.child = P;  // P thuộc R3

Sau:
    R1 --(reference slot của A)--> R3
                   |
                   v
         Dirty source card (ứng viên)
                   |
                   v
         Refine cập nhật inbound RSet R3
~~~

Với G1, post-barrier có thể dùng những điều kiện lọc như null references, nguồn/đích cùng region, card state, vùng thuộc young generation và những optimization khác. **Không phải mọi lần gán một Java reference đều dirty card**.

### 8.1. Pseudo-code dạy học của hai barriers

~~~text
function storeReference(slot, newReferent):
    oldReferent = slot.currentReferent

    // Pre-write: khi SATB marking active và old value liên quan
    if satbMarkingActive and oldReferent != null:
        enqueueSATBCandidate(oldReferent)

    // Java-level store
    slot.currentReferent = newReferent

    // Post-write: khi chính sách cross-region tracking cần
    if newReferent != null and crossesRelevantRegionBoundary(slot, newReferent):
        dirtyCard(slot.address)
        enqueueCardForRefinementIfNeeded()
~~~

**Đây là pseudo-code về trách nhiệm**, không phải HotSpot C++ chính xác. Các đường ghi null, copy arrays, initialization barriers, concurrent races, card-queue coalescing, oop compression, GC barriers và compiler optimizations khiến implementation thực phức tạp hơn.

Quan trọng hơn, **đánh dấu card và bổ sung inbound RSet không nhất thiết cùng xảy ra ngay trong mutator write**.

## 9. G1 collection set và inbound scanning

G1 chọn một tập regions để thu gom, gọi là **collection set/CSet**. Với references từ ngoài CSet trỏ vào objects trong CSet, collector cần tìm và cập nhật chúng nếu live objects bị di chuyển.

~~~text
Outside CSet:                         Inside CSet:
  R1 [A.child -----------]-----> R3 [P]
  R2 [B.entry ----------]-----> R3 [Q]

R3 inbound RSet (candidates):
  Card chứa A.child trong R1
  Card chứa B.entry trong R2
~~~

Trong thời gian GC pause, collector:

1. Lấy các roots và incoming candidates vào CSet.
2. Duyệt/kiểm tra actual references thích hợp.
3. Di chuyển live objects nếu collector evacuation cho phép.
4. Cập nhật các tham chiếu trỏ tới vị trí cũ theo cơ chế GC.
5. Xử lý metadata/remembered-set trạng thái phù hợp cho chu kỳ tiếp theo.

**RSet không thay thế toàn bộ GC roots.** Các roots như thread stacks, static/JNI handles và GC-specific sources vẫn cần được xử lý. RSet bổ sung đường **heap-to-heap references từ ngoài CSet**.

### 9.1. Không được chỉ quét target region

Nếu R3 chỉ chứa Y còn A ở old region R1 trỏ tới Y, muốn giữ Y sống qua evacuation phải biết đường từ A. Việc “Y đang nằm trong young” không cho biết Y live; RSet cung cấp cách tìm các external incoming edges để GC xử lý đúng.

### 9.2. Một RSet candidate có thể stale hoặc coarse

Giả sử RSet của R3 nói card K ở R1 “có thể chứa pointer vào R3”. Sau nhiều updates, nó có thể không còn pointer ấy. Scan sẽ kiểm tra và bỏ qua không cần thiết. False positives làm tăng scan cost nhưng **không gây nhầm đối tượng chết thành object không an toàn để thu hồi** trong mô hình chính xác của collector.

Đối lập, **false negative nguy hiểm**: một pointer thật từ ngoài CSet vào CSet bị bỏ sót có thể làm collector mất một đường giữ sống hoặc không cập nhật reference sau relocation. Barrier/queue synchronization được thiết kế để tránh điều đó.

## 10. Ví dụ Java 17+: mô phỏng card table và inbound RSet

Đây là **một chương trình Java chạy được nhưng chỉ mô phỏng**, không can thiệp G1 thật. Nó cố ý đánh dấu card bất cứ khi nào reference slot được ghi, rồi quét những slots hiện có để cập nhật candidate inbound cards. Nhờ vậy có thể thấy dirty cards không phải bản ghi chính xác của giá trị cuối cùng.

Tạo file **CardRememberedSetModel.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CardRememberedSetModel {
    private static final int CARD_BYTES = 512;

    static final class RefSlot {
        final int heapOffset;
        final int sourceRegion;
        int targetRegion; // -1 đại diện null

        RefSlot(int heapOffset, int sourceRegion) {
            this.heapOffset = heapOffset;
            this.sourceRegion = sourceRegion;
            this.targetRegion = -1;
        }
    }

    private static final List<RefSlot> SLOTS = new ArrayList<>();
    private static final Set<Integer> DIRTY = new TreeSet<>();
    private static final Map<Integer, Set<Integer>> INBOUND =
            new TreeMap<>();

    static void write(RefSlot slot, int newTargetRegion) {
        slot.targetRegion = newTargetRegion;
        DIRTY.add(slot.heapOffset / CARD_BYTES);
    }

    static void refine() {
        for (int card : DIRTY) {
            // Bỏ entries cũ của card trong model.
            for (Set<Integer> sourceCards : INBOUND.values()) {
                sourceCards.remove(card);
            }

            // Scan các current reference slots thuộc card.
            for (RefSlot slot : SLOTS) {
                int slotCard = slot.heapOffset / CARD_BYTES;
                if (slotCard == card
                        && slot.targetRegion >= 0
                        && slot.sourceRegion != slot.targetRegion) {
                    INBOUND.computeIfAbsent(
                            slot.targetRegion, key -> new TreeSet<>()
                    ).add(card);
                }
            }
        }
        DIRTY.clear();
    }

    static Set<Integer> inbound(int region) {
        return INBOUND.getOrDefault(region, Set.of());
    }

    public static void main(String[] args) {
        RefSlot first = new RefSlot(600, 0);
        RefSlot second = new RefSlot(620, 0);
        SLOTS.add(first);
        SLOTS.add(second);

        write(first, 2);
        write(second, 2);
        System.out.println("dirty=" + DIRTY);

        refine();
        System.out.println("inbound2=" + inbound(2));

        write(first, -1);
        refine();
        System.out.println("afterOneNull=" + inbound(2));

        write(second, -1);
        refine();
        System.out.println("afterBothNull=" + inbound(2));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 CardRememberedSetModel.java
java CardRememberedSetModel
~~~

**Stdout cố định:**

~~~text
dirty=[1]
inbound2=[1]
afterOneNull=[1]
afterBothNull=[]
~~~

**Lý do:**

1. Heap offsets 600 và 620 cùng nằm trong **card index = 1** vì 512 ≤ offset ≤ 1023.
2. Hai writes vào target region 2 khiến model đặt duy nhất **dirty card 1**.
3. Refine scan thấy hai refs từ source region 0 sang target region 2; inbound RSet target 2 có candidate **card 1**.
4. Xóa first reference nhưng second còn, nên inbound card 1 vẫn tồn tại.
5. Xóa second rồi refine khiến current card không còn refs sang region 2, nên tập inbound thành [].

**Khác biệt với G1 thực:**

- Model lưu **source/target region IDs** do Java code chỉ định, không đo region membership thực.
- Model dirty mọi write và sửa RSet ngay khi refine; G1 có guards/queues/coarsening, race protocols và handling khác.
- Model xóa stale candidates bằng cách scan card đầy đủ; G1 không có nghĩa giữ chính cấu trúc TreeMap hoặc xóa mọi entries theo một thuật toán Java như thế.
- Model không có GC roots, object liveness, evacuation, SATB, concurrent refinement hay actual heap addresses.

Vì vậy chương trình giải thích **logic metadata**, chứ không phải API điều khiển G1.

## 11. Ví dụ Java 17+: mô phỏng SATB pre-barrier

Tạo file **SATBPreWriteModel.java**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class SATBPreWriteModel {
    static final class RefSlot {
        String value;

        RefSlot(String value) {
            this.value = value;
        }
    }

    static final List<String> SATB_QUEUE = new ArrayList<>();
    static boolean marking = true;

    static void overwrite(RefSlot slot, String newValue) {
        String oldValue = slot.value;

        // Pre-write logging: old reference, trước store.
        if (marking && oldValue != null) {
            SATB_QUEUE.add(oldValue);
        }

        slot.value = newValue;
    }

    public static void main(String[] args) {
        RefSlot ref = new RefSlot("A");
        overwrite(ref, "B");
        overwrite(ref, null);

        System.out.println("oldCandidates=" + SATB_QUEUE);
        System.out.println("current=" + ref.value);

        marking = false;
        overwrite(ref, "C");
        System.out.println("afterMarking=" + SATB_QUEUE);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 SATBPreWriteModel.java
java SATBPreWriteModel
~~~

**Stdout chính xác:**

~~~text
oldCandidates=[A, B]
current=null
afterMarking=[A, B]
~~~

Diễn giải:

- Ghi A→B khi marking đang active có thể ghi nhận **A**, là tham chiếu cũ.
- Ghi B→null có thể ghi nhận **B**, không phải null.
- Khi marking=false, mô hình không queue old value.
- **A, B, C ở đây chỉ là String labels**, không phải heap addresses; queue của model dùng ArrayList nên hoàn toàn không phải bộ buffer của HotSpot.

**Quan trọng:** Trong G1 thật, SATB queue references phải tuân theo các điều kiện về marking và filtering; việc có một old candidate không chứng minh nó còn reachable ở graph hiện tại. Demo không tạo GC cycle hay chứng minh bất kỳ String nào bị thu gom.

## 12. Ví dụ Java 17+: hành vi Java không thay đổi bởi barriers

Write barriers được sinh bởi VM nhưng không tạo ra từ khóa Java mới. Chương trình dưới đây thể hiện thao tác ref fields và array slots mà G1 có thể quan sát nội bộ.

Tạo file **ReferenceMutationDemo.java**:

~~~java
public class ReferenceMutationDemo {
    static final class Box {
        Box next;
        final int id;

        Box(int id) {
            this.id = id;
        }
    }

    public static void main(String[] args) {
        Box root = new Box(1);
        Box first = new Box(2);
        Box second = new Box(3);
        Box[] slots = new Box[2];

        root.next = first;
        slots[0] = root;
        slots[1] = second;

        System.out.println("first=" + root.next.id);
        root.next = second;
        System.out.println("replaced=" + root.next.id);

        slots[1] = null;
        System.out.println("slotCleared=" + (slots[1] == null));
        System.out.println("sameObject=" + (root.next == second));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 ReferenceMutationDemo.java
java -XX:+UseG1GC ReferenceMutationDemo
~~~

**Stdout:**

~~~text
first=2
replaced=3
slotCleared=true
sameObject=true
~~~

Với chương trình này, **không thể khẳng định** từ source rằng root thuộc old region còn first/second thuộc young region tại một thời điểm cố định: lifetime/generations phụ thuộc allocations, JVM/GC và triggers. Đây chỉ là các **Java reference writes** có thể đi qua barrier infrastructure phù hợp.

Đổi collector có thể đổi thuật toán/barrier strategy nhưng vẫn phải bảo toàn **Java-level output** và memory safety. Đừng dùng stdout như bằng chứng card nào đã dirty.

## 13. Card marking không đồng nghĩa reference tới đối tượng mới

Trong trường hợp:

~~~java
holder.child = other;
holder.child = null;
holder.child = previous;
~~~

Collector có thể ghi nhận card source nhiều lần, nhưng trạng thái cuối chỉ còn reference tới previous. Tương tự, ghi **same value** vào một slot có thể được compiler/VM xử lý khác nhau tùy đường thực thi.

Các kết luận không hợp lệ:

- “Ba assignments = ba RSet entries.”
- “Card dirty = old-to-young hiện còn.”
- “Card clean = suốt lịch sử chưa có assignment.”
- “RSet có một candidate = object đích còn sống.”
- “Cùng một card có hai refs vào hai regions thì chỉ một target RSet được cập nhật.”

Cần phân biệt **event log/dirty hint** với **snapshot của reference slots tại lúc scan**.

### 13.1. False positives và false negatives

Ví dụ card dirty do một tham chiếu vào R3 rồi được overwrite thành null. RSet có thể vẫn có candidate trong một giai đoạn; đó là **false positive candidate** và gây scan overhead, nhưng GC sẽ kiểm tra slot thật.

Nếu có tham chiếu thật từ ngoài CSet trỏ vào một object trong CSet mà collector không ghi nhận qua bất kỳ đường root/remembered metadata phù hợp nào, đó có thể là **false negative nguy hiểm**. Barrier correctness là điều kiện an toàn, quan trọng hơn độ chính xác tuyệt đối từng candidate.

## 14. Concurrent refinement, queues và GC pause

G1 có thể thực hiện một phần việc cập nhật RSet **đồng thời với mutator**. Khi card marking tạo backlog:

~~~text
Mutator threads
  | dirty cards / buffers
  v
Pending work queues
  |
  +-- Concurrent refinement workers
  |       |
  |       +--> Update RSet metadata
  |
  +-- Pending work at collection pause
          |
          +--> Process/merge/update needed metadata
          +--> Scan incoming candidates for CSet
~~~

Các phiên bản HotSpot có thể thay đổi tên phase, queues và processing strategy. Với JDK 17, **Update RS / Scan RS** là các khái niệm thường gặp khi giải thích chi phí pause: cập nhật outstanding remembered-set work và scan candidates.

### 14.1. Khi nào overhead tăng?

Một số yếu tố làm tăng write-barrier/card/RSet cost:

- Mutation rate cao với rất nhiều reference field/array element writes.
- Object graph có nhiều **cross-region edges**.
- Một số regions có incoming candidates dày đặc.
- Card dirtied thường xuyên rồi được refined nhiều lần.
- Backlog dirty cards chưa được xử lý kịp trước GC pause.
- Tỷ lệ scan card cao do **coarse-grained metadata** hoặc stale candidates.
- Allocation/promotion pattern khiến old-to-young references phát sinh nhiều.
- Memory pressure, region sizing và live set thay đổi CSet composition.

Không đoán bằng con số field assignments trong source: JIT, escape analysis, array-copy barriers, null stores, VM safepoints và region placements ảnh hưởng số barrier actions thực.

### 14.2. RSet coarsening

Để tránh sử dụng metadata quá lớn, một remembered-set implementation có thể chuyển từ biểu diễn rất chính xác (ví dụ các card indices) sang biểu diễn **thô hơn** cho một source area/region. Điều này tiết kiệm metadata nhưng khiến collector scan rộng hơn khi cần.

Đó là một trade-off:

~~~text
Precise remembered set:
  nhiều entries / độ chọn lọc cao / metadata lớn

Coarser remembered set:
  ít metadata hơn / có thể scan nhiều card hoặc vùng hơn
~~~

Không phải mọi GC có cùng ngưỡng hoặc cấu trúc hash tables/bitmaps; các flags có thể thay đổi qua JDK. Trên Java 17, nên đo số liệu trước khi chọn tuning thủ công.

## 15. Tránh đồng nhất G1 với mọi collector khác

| Collector/cơ chế | Cách hiểu thích hợp |
| --- | --- |
| **Serial/Parallel generational GC** | Có những cơ chế remembered/card-style tracking cho generational boundary, nhưng thuật toán cụ thể khác G1 |
| **G1** | Region-based, remembered-set candidates, post-write card marking, concurrent SATB pre-barrier khi marking |
| **ZGC Java 17** | Low-latency collector có load barriers/colored pointers và cơ chế concurrency khác; không sao chép nguyên G1 RSet model |
| **Shenandoah** | Low-pause/concurrent collector với barrier set và forwarding/load-reference xử lý khác; tùy distribution/build |
| **Không có GC hoặc collector đặc biệt** | Không nên giả định tồn tại card table giống G1 |

**GC barrier là implementation detail**. Java source vẫn có các types **Object**, **String**, reference fields và array components; không có một kiểu **CardTable** bắt buộc trong Java language spec.

### 15.1. Barrier hiệu lực ở đâu?

Barriers có thể được triển khai bởi interpreter, C1/C2 JIT, stubs/runtime, array-copy paths hoặc những đường ghi đặc biệt. Việc một assignment Java được tối ưu/loại bỏ hoàn toàn nếu object không thoát và không có side effects cũng có thể khiến không có tương ứng một heap write cần card marking.

Không kết luận “mỗi dòng Java gán tham chiếu → luôn 2 barriers”.

## 16. Những quan hệ thường gây nhầm trong sơ đồ

### 16.1. Source vs target region

Cho:

~~~text
A thuộc Region 1
B thuộc Region 4
A.child = B
~~~

- **Source card** nằm tại slot **A.child**, tức trong Region 1.
- **Target object** B nằm trong Region 4.
- **Inbound RSet của Region 4** có thể ghi nhận source card của Region 1.
- Card table thường được lập chỉ mục theo **source slot address** (vị trí field được ghi), không theo target address đơn thuần.

### 16.2. Old-to-young vs young-to-old

~~~text
Old A ---> Young B
Young C ---> Old D
~~~

Trong một young collection:

- **Old→Young** là cạnh từ ngoài vùng đang thu gom vào young; cần root/remembered processing phù hợp.
- **Young→Old** không tự là một incoming root từ old vào young. Khi scan young object C sống, nó có reference D thuộc old; đó là cạnh hướng ngược.
- Với G1 theo regions, cần xét region membership/CSet chứ không chỉ nhãn young/old.

### 16.3. Một card có hai target regions

~~~text
Source card K:
  field x ---> Region 4
  field y ---> Region 7
~~~

Refinement có thể tìm references liên quan đến cả Region 4 và Region 7; inbound metadata của hai targets đều có thể ghi nhận card K hoặc biểu diễn tương ứng. **Card dirty event không chứa bắt buộc một target region duy nhất**.

### 16.4. Source object được GC di chuyển

Khi evacuation di chuyển object, source/target addresses thay đổi. GC phải cập nhật pointers và metadata liên quan, không thể xem “card index của object” như identifier vĩnh viễn gắn với Java object identity.

## 17. Quan sát G1 trên HotSpot Java 17

### 17.1. Xác nhận collector

Có thể khởi chạy một chương trình với G1:

~~~bash
java -XX:+UseG1GC ReferenceMutationDemo
~~~

Nếu lệnh biên dịch và classpath hợp lệ, ví dụ này in stdout như mục 12. Việc bật G1 không tự làm chương trình Java in dirty-card events.

Kiểm tra flags JVM thực:

~~~bash
java -XX:+PrintFlagsFinal -version
~~~

Tìm **UseG1GC**, **G1HeapRegionSize**, **MaxGCPauseTimeMillis** và các flags phù hợp khác. Mỗi JDK/VM có ergonomics và cách hiển thị khác nhau; không suy final collector chỉ từ một tutorial.

### 17.2. Dùng Unified JVM Logging

Trong HotSpot Java 17:

~~~bash
java -XX:+UseG1GC -Xlog:gc*=info ReferenceMutationDemo
~~~

Một chương trình chạy quá nhanh hoặc cấp phát rất ít có thể **không thực hiện young/mixed GC nào** trong một lần chạy; vì vậy không có một số lượng event cố định để dự đoán.

Để xem available tags:

~~~bash
java -Xlog:help
~~~

Một cấu hình thử nghiệm giàu thông tin hơn trong môi trường chấp nhận nhiều log:

~~~bash
java -XX:+UseG1GC -Xlog:gc*,gc+remset=debug ReferenceMutationDemo
~~~

**Không bảo đảm** mọi output hay tag availability giống nhau trên mọi JVM/JDK build. Xem **java -Xlog:help**, rồi điều chỉnh log level/tag. Không chạy trace log khối lượng lớn trên production trước khi đánh giá overhead/dung lượng.

### 17.3. Điều gì có thể xem từ log?

Tùy bản JDK và logging configuration, bạn có thể thấy:

- Loại pause (young evacuation, concurrent start, mixed...).
- Pause duration, collection set composition/regions.
- Thời gian làm việc cho root scanning, remembered-set processing/merging và object copying.
- Số scanned cards/remembered-set statistics tại những log levels thích hợp.
- Concurrent refinement/marking events và dữ liệu GC pauses.

**GC log là aggregate diagnostics**, không phải bản in chính xác mọi Java field assignment. Không thể suy từ một dòng “Scan RS” rằng ứng dụng đã sửa field nào tại Java source line nào.

### 17.4. Kết hợp profiling và heap graph

Nếu pause cost liên quan remembered sets tăng, hãy kiểm tra:

1. Allocation rate và promotion rate.
2. Cấu trúc objects có rất nhiều cross-region pointers.
3. Mutable collections/arrays chứa nhiều references.
4. Cache retaining old objects rồi liên tục thêm young references.
5. Card refinement backlog và pause breakdown.
6. Heap region size thực được ergonomics chọn.
7. GC algorithm và CPU resources được phân cho concurrent workers.

Nếu vấn đề là memory leak, dùng **heap dump/path-to-GC-root** để xác định graph còn reachable. Write barrier/RSet logs **không thay thế** reachability analysis.

## 18. Cách phân tích một bài tập “A trỏ B” từng bước

Giả sử:

~~~text
Region O1: old object A
Region O2: old object C
Region Y3: young object B
Region Y4: young object D

A.ref = B
C.ref = D
B.ref = C
~~~

Các bước:

1. Xác định **source slot** của từng cạnh: A.ref ở O1; C.ref ở O2; B.ref ở Y3.
2. Xác định **target region**: B ở Y3, D ở Y4, C ở O2.
3. Đánh dấu **cross-region**: cả ba cạnh đều crossing vì source/target regions khác nhau.
4. Với G1 post-write barrier, xét guards/card marking phù hợp khi từng store xảy ra; không khẳng định cùng số queue entries với số edges.
5. Khi refinement, **inbound RSet Y3** có thể ghi nhận source card O1; **inbound RSet Y4** có thể ghi nhận source card O2; **inbound RSet O2** có thể ghi nhận source card Y3 khi chính sách/phase duy trì metadata đó.
6. Khi young CSet gồm Y3/Y4, collector quan tâm đặc biệt tới incoming edges từ **ngoài CSet** (O1 và O2). Cạnh Y3→O2 không phải incoming from outside vào young CSet.
7. Nếu đồng thời SATB marking đang active và một slot bị overwrite, **pre-barrier** có thể enqueue referent cũ, độc lập ý nghĩa RSet.
8. Nếu A.ref sau đó trở thành null, card/RSet candidate có thể stale một thời gian; GC phải kiểm tra actual reference.

**Quan trọng:** Một số phần metadata cho những targets có thể được duy trì chọn lọc. Không xem sơ đồ ở bước 5 là cam kết “G1 luôn có một stored RSet entry cho mọi cross-region edge tại mọi thời điểm”.

## 19. Write barrier và các tính chất của chương trình Java

### 19.1. Không làm thay đổi Java reference identity

~~~java
Object a = new Object();
Object b = a;
System.out.println(a == b); // true
~~~

Dù G1 thực hiện barrier operations khi có reference writes phù hợp, Java-level **==**, **equals()**, **getClass()** và memory-safety guarantees giữ nguyên. Runtime không được để bookkeeping của collector tạo ra object identity khác.

### 19.2. Không thay thế thread synchronization

Nếu hai threads cập nhật một ordinary reference field không volatile/không synchronized, vẫn có thể có data race theo Java Memory Model. GC barriers không biến field ấy thành atomic/volatile publication contract. Thiết kế thread-safe cần dùng Java concurrency mechanisms riêng.

### 19.3. Không phải đối tượng Java để ứng dụng chỉnh sửa

Card table/RSet không phải Collection hay Map công khai cho ứng dụng. Bạn không nên viết code tự sửa metadata GC bằng JNI/Unsafe. Ví dụ Java trong bài chỉ **mô phỏng**, tuyệt đối không thao tác internals của live collector.

## 20. Tối ưu hiệu năng: khi nào nên chú ý?

**Không bắt đầu bằng sửa cờ RSet/card** khi chưa có đo đạc. Nếu GC logs cho thấy remembered-set work/scan là một thành phần đáng kể của pause, hãy kiểm tra nguyên nhân ở **application object graph** và heap ergonomics.

Một số hướng:

- Giảm churn của references trong những long-lived arrays/containers nếu không cần thiết.
- Hạn chế cấu trúc graph với quá nhiều cross-region pointers khi nó không mang giá trị nghiệp vụ.
- Xem cache/queue ownership; cải thiện locality bằng thiết kế data structures phù hợp.
- Đặt heap/region/GC goals dựa trên throughput/latency thực, không chỉ một log line.
- Đánh giá CPU cycles spent in mutator barriers so với GC refinement/scanning.
- Không ép “ít references” bằng cách nhét dữ liệu vào off-heap hoặc primitive buffers nếu gây complexity/rủi ro lớn hơn; cần benchmark toàn hệ.

### 20.1. Trade-offs từ region size

Regions lớn có thể giảm số lượng cross-region boundaries trong một số workload, từ đó giảm metadata/refinement work. Nhưng regions lớn cũng có thể làm thay đổi lượng live data cần evacuate, humongous object handling và pause behavior. **G1HeapRegionSize** là tuning flag dành cho trường hợp có evidence rõ, không phải “càng lớn càng tốt”.

### 20.2. Thay đổi collector

Nếu mục tiêu là latency rất thấp, có thể đánh giá collector khác phù hợp JDK/workload. Tuy nhiên mỗi collector có barrier strategy, CPU/memory overhead, requirement và maturity khác nhau; không chuyển collector dựa trên nhận định rằng “write barrier luôn đắt nên ZGC không dùng barrier”.

## 21. Những hiểu lầm cần tránh

1. **“Write barrier là Java volatile/memory fence.”** Không: GC barrier và JMM synchronization phục vụ vấn đề khác.
2. **“Mọi reference assignment luôn thực hiện đúng hai barrier functions.”** Không: có phase guards/optimizations.
3. **“Pre-write G1 ghi new reference.”** Sai trong vai trò SATB: quan tâm **old value** sắp bị overwrite.
4. **“Post-write G1 ghi old reference để SATB.”** Sai: nhiệm vụ chính ở đây là cross-region/card/RSet tracking.
5. **“Card dirty = có một young object sống.”** Sai: dirty chỉ là candidate; card có thể stale.
6. **“Card table lưu danh sách đầy đủ Java objects.”** Sai: nó mô tả vùng/slot candidates.
7. **“RSet chứa mọi outgoing edges của target region.”** Sai với inbound RSet; nguồn nằm ngoài region.
8. **“RSet là danh sách chính xác tất cả live objects trong region.”** Sai.
9. **“Một assignment tương ứng đúng một RSet entry.”** Sai: batching, card granularity, coalescing.
10. **“G1 chỉ tracking old-to-young.”** Sai: region-based cross-region edges phục vụ young/mixed CSet.
11. **“Old→Young có thể bỏ qua vì object ở old đã được quét trong quá khứ.”** Sai: đồ thị có thể đổi sau đó.
12. **“Quét remembered set có nghĩa quét toàn old heap.”** Không: mục tiêu là quét vùng candidates liên quan.
13. **“Concurrent refinement luôn hoàn tất ngay khi Java store kết thúc.”** Sai: có pending queues.
14. **“Card/RSet false positives đồng nghĩa memory safety sai.”** Sai: có thể chỉ tăng scan work.
15. **“SATB giữ graph đúng y như hiện tại từng nanosecond.”** Không: đó là logical snapshot approximation theo marking invariant.
16. **“Nếu old edge bị xóa thì object chắc chắn thu gom ngay trong marking đó.”** Không: SATB có thể tạo floating garbage.
17. **“GC logs cho biết Java source statement nào làm card dirty.”** Không phải một tính năng mặc định như vậy.
18. **“Dirty card là một GC root độc lập kiểu static field.”** Không: metadata hỗ trợ root/incoming scanning.
19. **“Để sửa memory leak, tắt write barriers.”** Không: barriers là phần bảo đảm an toàn collector; sửa ownership/root paths.
20. **“Giảm card size luôn nhanh hơn.”** Không: metadata, write, refinement và scan có trade-offs.
21. **“RSet precision luôn tuyệt đối.”** Không: có stale/coarse candidates, quan trọng là không bỏ sót references cần thiết.
22. **“ZGC/Shenandoah phải dùng y nguyên G1 cards.”** Không: implementation barriers khác nhau.
23. **“Cứ java -XX:+UseG1GC là sẽ có GC log mọi lần chạy.”** Không nếu không bật logging phù hợp hoặc không có collection.
24. **“G1 pre-barrier hoạt động chỉ ở old-to-young writes.”** Không: mục đích SATB là snapshot preservation khi concurrent marking.
25. **“Collector có quyền làm Java reference semantics thay đổi để tracking nhanh hơn.”** Không: phải bảo toàn ngữ nghĩa Java.

## 22. Bài tập có đáp án

**Bài 1.** Old object O tham chiếu Young object Y. Young GC chỉ scan thread roots và young regions, không scan old-to-young candidates. Có nguy cơ gì?

**Đáp án:** Có thể bỏ sót một đường giữ Y sống qua O. Generational collector cần xem xét old-to-young references qua remembered/card/barrier hoặc cơ chế tương đương.

**Bài 2.** Giả sử card size 512 byte, source slot address offset 1300 tính từ heap base 0. Card index?

**Đáp án:** floor(1300/512)=**2**; card 2 có miền byte [1024,1535].

**Bài 3.** Một card có hai fields trỏ lần lượt Region 5 và Region 8. Cả hai cần được xét khi refine không?

**Đáp án:** Có thể. Một dirty card không giới hạn một target; inbound metadata cho cả regions có thể cần cập nhật theo chính sách collector.

**Bài 4.** Source object A trong Region 1 trỏ target B trong Region 7. Inbound RSet của region nào ghi nhận source card?

**Đáp án:** **Region 7** theo mô hình per-target inbound RSet.

**Bài 5.** Gán A.ref từ B sang null khi G1 SATB concurrent marking active. Pre-barrier quan tâm cái gì?

**Đáp án:** Old referent **B**, có thể được enqueue trước khi overwrite để bảo toàn SATB snapshot invariant.

**Bài 6.** Gán A.ref sang C ở region khác khi không có concurrent marking. G1 pre SATB có bắt buộc enqueue giá trị cũ không?

**Đáp án:** Không như một rule mọi lúc; pre-barrier có kiểm tra marking-active, nhưng post-barrier có thể vẫn tracking cross-region reference phù hợp.

**Bài 7.** Ghi một field 100 lần nhưng chỉ trên một source card. Có chắc 100 RSet entries không?

**Đáp án:** Không. Card-level metadata, duplicate filtering và batch refinement có thể gộp work.

**Bài 8.** Một dirty card sau refine không còn reference sang target region. Dirty event trước đó có nghĩa memory leak không?

**Đáp án:** Không. Nó có thể là stale/false-positive candidate; GC phải kiểm tra actual slots phù hợp.

**Bài 9.** Vì sao G1 có thể xử lý old→old region references?

**Đáp án:** Vì mixed collection có thể chọn một số old regions vào CSet; incoming edges từ old regions khác vẫn cần được xem xét.

**Bài 10.** Có thể suy từ việc RSet chứa một card rằng object đích chắc chắn live?

**Đáp án:** Không. RSet là metadata các source locations tiềm năng; live/dead phụ thuộc roots/tracing và các điều kiện GC.

**Bài 11.** Card marking thuộc compiler/runtime hay được lập trình viên viết thủ công trong Java?

**Đáp án:** Runtime/JIT/interpreter/VM barrier infrastructure; Java source viết phép gán bình thường.

**Bài 12.** GC barrier có thay thế volatile publication giữa threads không?

**Đáp án:** Không. GC bookkeeping độc lập với happens-before/thread synchronization.

**Bài 13.** Trong SATB, vì sao marker có thể cần old reference khi mutator overwrite field?

**Đáp án:** Cạnh thuộc snapshot logic có thể bị xóa trước khi marker scan; old reference giúp tránh bỏ sót các objects cần được xem là live theo snapshot.

**Bài 14.** Một card 512 byte có 32 reference slots, nhưng chỉ một slot trỏ vào CSet. Tại sao collector vẫn scan thêm slots?

**Đáp án:** Card granularity ưu tiên metadata/write efficiency; scan một card có thể bao gồm những slots không liên quan.

**Bài 15.** G1 có bắt buộc dùng cùng queue layout/tracing code như một tutorial JDK 8 ở JDK 17/26 không?

**Đáp án:** Không. Cơ chế nội bộ và flags thay đổi; giữ mô hình invariants và đọc docs/source đúng version.

**Bài 16.** Khi GC pause bị chi phối bởi remembered-set scan, nên làm gì trước?

**Đáp án:** Đo lại bằng G1 GC logs, xem cross-region connectivity/dirty-card rate/region sizing/live set và workload. Tránh chỉnh flags tùy ý.

**Bài 17.** Tại sao model CardRememberedSetModel in inbound2=[1] dù hai writes cùng card?

**Đáp án:** RSet của model lưu **source card candidates**, không đếm số references; hai fields thuộc card 1.

**Bài 18.** Model đã null first nhưng vẫn in afterOneNull=[1] vì sao?

**Đáp án:** Field second trong cùng card vẫn trỏ target region 2; khi refine scan card, candidate vẫn phù hợp.

**Bài 19.** Một source card ở outside CSet trỏ vào two targets trong CSet. Dùng một inbound RSet chung liệu bắt buộc mất thông tin không?

**Đáp án:** Không; metadata có thể map candidate cards vào targets hoặc hợp thành tập cần scan. GC cần đảm bảo mọi incoming reference cần thiết vẫn được xem.

**Bài 20.** Có thể giảm GC work bằng cách bỏ SATB pre-barrier khi concurrent marking không?

**Đáp án:** Không nếu không có một thuật toán/giải pháp đúng thay thế. Tắt cơ chế cần thiết có thể phá marking invariant; người viết application không quản lý barrier này.

## 23. Checklist khi đọc code hoặc phân tích sự cố

- Bắt đầu bằng **loại GC** và JDK build cụ thể.
- Vẽ **cạnh từ source slot → target object**; ghi rõ region membership.
- Với young/mixed CSet, xác định **incoming edges từ ngoài CSet**.
- Phân biệt **card table** (dirty candidate marker) và **remembered set** (incoming sources có thể cần scan).
- Nhớ **source card index** dựa trên nơi field được ghi, không phải chỉ target address.
- G1 **post-barrier** để phục vụ tracking source cards/cross-region updates.
- G1 **pre-barrier** để phục vụ SATB concurrent marking với old referent.
- Queue/refinement có thể batch/delay; không giả định RSet đã cập nhật tức thì sau Java assignment.
- Dùng **false positive vs false negative** để đánh giá tính đúng.
- RSet có thể coarse/stale nhưng phải đủ để tránh mất incoming edges.
- Phân biệt **liveness tracing** với **card bookkeeping** và **object relocation**.
- Khi GC log xấu, đo remembered-set work/scan, object graph topology và mutation rate.
- Không coi memory barrier của JMM là GC write barrier.
- Không nghĩ static cache leak có thể sửa bằng điều chỉnh RSet; phải sửa root/ownership path.
- Khi đổi collector, kiểm tra cơ chế barriers mới; không áp dụng máy móc tài liệu G1 cho ZGC.
- Với Java 17, dùng chính tài liệu Oracle 17/HotSpot logs để tránh dựa vào internal details ở bản khác.

### Tài liệu tham khảo chuẩn và chuyên sâu

- **Oracle Java SE 17 — Garbage-First (G1) Garbage Collector:** https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html
- **Oracle Java SE 17 — G1 Tuning:** https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector-tuning.html
- **Oracle Java SE 17 — Unified JVM Logging trong java command:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **OpenJDK JDK 17 HotSpot — G1 barrier implementation/source tree:** https://github.com/openjdk/jdk17u/tree/master/src/hotspot/share/gc/g1
- **OpenJDK JDK 17 HotSpot — Card table infrastructure:** https://github.com/openjdk/jdk17u/tree/master/src/hotspot/share/gc/shared
- **JVMS Java SE 17 — Run-Time Data Areas/Heap:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3
- **Java SE 17 — java.lang.ref reachability semantics:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html

**Ghi nhớ cốt lõi:** **Write barriers** giữ thông tin về biến đổi tham chiếu để một collector không bỏ sót các cạnh quan trọng. **Card table** đánh dấu các source areas cần kiểm tra; **remembered sets** mô tả những incoming candidates từ ngoài regions cần thu gom để giảm quét toàn heap. Trong HotSpot G1, **post-write** phục vụ cross-region card/RSet tracking, còn **pre-write** bảo toàn SATB snapshot khi đánh dấu đồng thời. Dirty card và RSet entries là **metadata ứng viên**, không tự chứng minh liveness, và các chi tiết storage/queue/barrier optimization phụ thuộc collector/version.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-008">Tốc độ cấp phát, lượng dữ liệu còn sống và khoảng trống cần cho GC</a>

<details>
<summary>Xem chi tiết</summary>

# Tốc độ cấp phát, lượng dữ liệu còn sống và khoảng trống cần cho GC

Một ứng dụng Java có thể tạo **rất nhiều objects mỗi giây** nhưng vẫn chạy ổn định nếu chúng chết nhanh và GC thu hồi được; ngược lại, ứng dụng cấp phát khá ít vẫn có thể gặp `OutOfMemoryError` nếu phần lớn objects được **giữ sống lâu dài**. Một hiện tượng thứ ba là Java heap còn có vẻ “dư” ở mức tổng số byte, nhưng collector **không còn đủ khoảng trống phù hợp để di chuyển objects đang sống** trong một collection.

Ba biến số này cần được đọc riêng:

1. **Allocation rate** – tốc độ cấp phát: mỗi giây application tạo ra bao nhiêu bytes/objects trên heap.
2. **Live set / retained live data** – lượng dữ liệu còn sống: có bao nhiêu bytes thực sự phải bảo toàn theo reachability tại mốc collection hoặc sau GC.
3. **Free-space headroom / evacuation space** – dung lượng trống hữu dụng: collector còn bao nhiêu không gian để tiếp tục cấp phát, chứa survivors/promoted objects, hoàn thành concurrent cycles hoặc xử lý biến động tải.

Bài đặt trong bối cảnh **Java 17, HotSpot và G1** để giải thích các hệ quả trong thực hành. Những công thức đơn giản được ghi rõ là **mô hình ước lượng**, không phải đẳng thức JVMS cho mọi garbage collector. Các thuật ngữ cụ thể như *to-space exhausted*, *mixed collection*, *reserve percent* và *adaptive IHOP* thuộc chiến lược G1 của HotSpot, không phải quy tắc bắt buộc cho mọi JVM.

## 1. Cùng là heap dùng nhiều nhưng có thể do ba nguyên nhân khác nhau

Giả sử hai ứng dụng đều có `-Xmx1g` và báo heap used quanh 700 MiB tại một thời điểm:

| Đặc điểm | Ứng dụng A | Ứng dụng B |
| --- | --- | --- |
| Allocation rate | 500 MiB/giây | 20 MiB/giây |
| Objects sống qua GC | Ít; phần lớn là dữ liệu request tạm | Nhiều; cache và session giữ lâu |
| Sau GC | Có thể giảm nhanh về 200 MiB | Có thể vẫn ở 670 MiB |
| GC behavior | Young collections thường xuyên | Ít collection hơn nhưng khả năng reclaim thấp |
| Hướng điều tra | Allocation hotspots, object lifetime, throughput | Retention, cache size, GC roots, live-set trend |

Các giá trị trong bảng chỉ là **tình huống giả định**, không phải benchmark. Bên ngoài hai trường hợp còn có thể xuất hiện **headroom pressure**: ví dụ GC đang cần copy 180 MiB surviving objects sang vùng đích nhưng không tìm được đủ destination regions phù hợp, dù dashboard hiển thị một số byte “free” theo cách tổng hợp.

**Điểm then chốt:** không chẩn đoán “GC chậm vì heap used cao” khi chưa biết **cấp phát nhanh đến mức nào**, **đối tượng nào còn sống** và **GC còn khoảng trống hiệu dụng nào để làm việc**.

## 2. Định nghĩa chính xác các chỉ số thường thấy

| Chỉ số | Đơn vị thường dùng | Ý nghĩa |
| --- | --- | --- |
| Allocation rate | MiB/s, GiB/s, objects/s | Lượng dữ liệu mới được cấp phát trong một khoảng thời gian |
| Young allocation rate | MiB/s | Phần allocations do collector tính cho vùng trẻ; phạm vi có thể khác tổng heap allocations |
| Live set | MiB/GiB | Objects cần giữ sống tại pha GC/snapshot xác định |
| Post-GC occupancy | MiB/GiB | Heap used sau một collection; không mặc định bằng live set toàn heap |
| Survival rate | % | Tỷ lệ live bytes trong tập objects/regions được thu gom |
| Promotion rate | MiB/s hoặc MiB/GC | Lượng object/bytes được chuyển sang old region/generation |
| Heap used | Bytes | Metric JVM về dung lượng heap hiện đang ghi nhận sử dụng |
| Heap committed | Bytes | Heap đã cam kết khả năng sử dụng |
| Heap max | Bytes | Giới hạn có thể dùng; thường liên quan `-Xmx` trên HotSpot |
| Free within committed | Bytes | `committed - used` trong một snapshot cùng phạm vi |
| Uncommitted capacity | Bytes | Phần khác biệt giữa max và committed, khi max hữu hạn; không chắc mở rộng được ngay |
| G1 to-space/headroom | Regions/bytes | Destination capacity thực tế phục vụ evacuation/promotion theo policy G1 |
| GC pause duration | ms | Khoảng dừng ứng dụng trong những phase STW cụ thể |
| GC overhead | % CPU hoặc % wall time, tùy nguồn | Phần tài nguyên/thời gian dùng cho GC; phải ghi rõ định nghĩa |

Hai số đo **used** và **committed** của `MemoryUsage` có quan hệ `used <= committed`; nếu `max` hữu hạn thì `committed <= max`. Nhưng không được lấy `max - used` rồi mặc định toàn bộ phần đó là **G1 to-space sẵn dùng ngay**, vì heap regions, evacuation reserves, generation occupancy, humongous objects, fragmentation và chính sách commit tham gia.

### Phân biệt “đang dùng” với “thực sự còn sống”

Giữa hai lần GC, heap có thể chứa objects **đã không còn reachable** nhưng GC chưa kịp reclaim. Chúng góp vào used cho đến khi thích hợp được thu hồi. Ngược lại, một young GC có thể chỉ collection một phần heap; số **post-young-GC heap used** vẫn chứa old objects chưa được full-heap tracing lại ở chu kỳ ấy. Không gọi tùy tiện mọi post-GC heap occupancy là “live bytes toàn JVM chính xác đến từng byte”.

## 3. Allocation rate: chương trình tạo ra bao nhiêu rác mỗi giây?

**Tốc độ cấp phát** phản ánh dữ liệu mới được application tạo trên heap, thường được biểu diễn:

```text
Allocation rate ≈ bytes allocated trong khoảng Δt / Δt

Đơn vị ví dụ:
  600 MiB allocated trong 3 giây -> 200 MiB/giây
```

Tốc độ này chịu tác động từ nhiều nguồn:

- Tạo DTOs, request objects, temporary strings, arrays, JSON trees.
- Boxing, intermediate collections, lambdas/closures có capture, stream pipeline materialization.
- Dữ liệu nén/giải nén, serialization và copies vào buffers.
- Batch transformations, sorting, grouping và object graphs trung gian.
- Logging và formatting có eager construction, kể cả khi output không thật sự cần.
- Workload thực tế: QPS × bytes allocated/request, với nhiều loại request có kích thước khác nhau.

**Allocation cao không tự là memory leak.** Nếu các object chết nhanh, generational GC có thể thu hồi rất hiệu quả. Tuy vậy, quá nhiều allocations vẫn có chi phí: áp lực Eden, GC frequency, write barriers, TLAB refills, copy survivors và CPU dành cho runtime.

### Ví dụ số: allocation rate theo request throughput

Giả sử (chỉ để ước lượng):

- Tải: **2.000 requests/giây**.
- Mỗi request cấp phát trung bình **120 KiB** objects/arrays trên heap.
- Phần lớn sống không quá một request.

Ước lượng:

```text
2.000 requests/s × 120 KiB/request
= 240.000 KiB/s
≈ 234,375 MiB/s
```

Con số này chưa tính background jobs, GC/internal allocations và những biến động theo request payload; cũng chưa nói **bao nhiêu trong 234 MiB/s sẽ sống qua GC**. Phải đo survival/retention tiếp.

Một tuyến xử lý cùng QPS nhưng allocation giảm một nửa có thể giảm young GC pressure, **nhưng không chắc giảm p99 latency** nếu bottleneck chính nằm ở database/I/O hoặc live set vẫn quá lớn. Luôn đo end-to-end.

## 4. Thread Local Allocation Buffer (TLAB) và allocation fast path

HotSpot có thể cho mỗi thread một vùng **Thread Local Allocation Buffer (TLAB)** để nhiều allocations nhỏ diễn ra nhanh bằng cách tăng con trỏ trong buffer. Khi buffer hết chỗ, runtime refill hoặc dùng allocation path khác.

Ý nghĩa:

- Allocation thường **rẻ hơn** thao tác xin memory từ OS cho từng object.
- Allocation bytes vẫn phải được GC quản lý, kể cả khi cấp rất nhanh.
- Thống kê allocation thông qua TLAB/ngoài TLAB có các góc nhìn hạch toán khác nhau; dùng cùng nguồn metric nếu so rate.
- Một phần object có thể bị JIT **scalar replacement/escape analysis**, nên số allocations vật lý quan sát được không nhất thiết bằng số `new` trong source.
- TLAB thuộc nội bộ HotSpot; đừng coi nó là Java API bắt buộc mọi JVM có cùng memory layout.

### Vì sao giảm số `new` chưa chắc là tối ưu đúng?

Một object nhỏ, ngắn hạn có thể được cấp phát rất nhanh, rồi bị GC thu hồi rẻ. Thay nó bằng **object pool thủ công** có thể làm object sống lâu hơn, tăng synchronization contention, giữ graph lớn và **tăng live set**. Ưu tiên **đo allocation hotspots và GC pressure** trước khi tái sử dụng object bằng pooling.

## 5. Live set: cái GC không thể đơn giản bỏ qua

**Live set** là phần object graph được coi cần giữ sống ở một thời điểm/snapshot theo quy tắc collector. Nó có thể bao gồm:

- Objects đang được active requests sử dụng.
- Cache entries, session data, singleton registries.
- Queued tasks, pending futures, retries, event listeners.
- Long-lived maps, object graphs của database/application state.
- ThreadLocal values đang được worker threads giữ.
- Các object còn reachable qua nhiều owners/GC roots.

Live data quan trọng vì collector phải:

1. Xác định liveness qua tracing/marking và metadata.
2. Bảo toàn object fields, references và identity.
3. Khi dùng evacuation/compaction, **copy hoặc di chuyển một phần live bytes** tới vùng đích.
4. Tiếp tục giữ những objects thực sự thuộc tập sống, dù ứng dụng có muốn giữ chúng quá lâu hay không.

Không thể “tune GC” để thu hồi một object vẫn còn **strongly reachable** mà Java code có thể sử dụng hợp lệ. Nếu live set tăng do listener chưa unsubscribe hoặc cache không có bound, vấn đề nằm ở **ownership/lifecycle** chứ không chỉ GC flags.

### Live set lớn tác động tới pause/CPU như thế nào?

- **Marking/scanning:** nhiều live objects và reference edges có thể làm công việc tracing nhiều hơn.
- **Copy/evacuation:** nhiều live bytes trong collection set thì phải copy nhiều, tốn bandwidth và destination regions.
- **Old occupancy:** object sống lâu chuyển hoặc nằm ở old regions, làm old regions khó thu hồi.
- **Headroom:** live data chiếm phần dung lượng đáng ra có thể dùng làm vùng đích hoặc khoảng đệm cho allocation trong lúc concurrent marking chạy.
- **Fragmentation/humongous objects:** live data layout có thể khiến collector không có vùng liên tục hoặc đủ regions cho yêu cầu cấp phát cụ thể.

**Không có công thức “GC pause = live bytes / bandwidth” dùng chung**: ngoài copy bytes, còn root scanning, reference processing, synchronization giữa GC workers, card/remembered sets, OS/JIT và CPU competition.

## 6. Tỷ lệ sống (survival rate) và lượng copy

Trong một GC xử lý một collection set, định nghĩa mô hình:

```text
CSet occupancy trước collection  = live bytes + dead bytes
survival rate (mô hình)          = live bytes / occupancy

Giả sử:
  occupancy = 400 MiB
  live      = 40 MiB
  dead      = 360 MiB

survival rate = 40 / 400 = 10%
```

Nếu collector evacuation phải copy gần 40 MiB (bỏ qua chi tiết tối ưu/objects không move), việc reclaim 360 MiB có thể khá có lợi.

Một collection set khác với occupancy 400 MiB nhưng live 360 MiB:

```text
survival rate = 360 / 400 = 90%
dead = 40 MiB
```

Lần này GC phải bảo toàn/có thể copy rất nhiều bytes để thu hồi ít hơn. **Cùng 400 MiB occupancy nhưng GC efficiency khác xa**.

### Tại sao không lấy `heap used trước - heap used sau` làm live set chính xác?

- Collection có thể chỉ xử lý young regions chứ không toàn heap.
- Mutators có thể chạy giữa các mốc đo, đặc biệt trong concurrent GC.
- Promotion, surviving objects, metadata và compaction có thể thay đổi distribution.
- GC log có thể báo region occupancy/evacuation theo định nghĩa collector riêng.
- Live data ở old regions không nhất thiết được rà soát đầy đủ ở mỗi young GC.

Chỉ dùng chênh lệch trước–sau làm **ước lượng reclaim dưới điều kiện ghi rõ**, không gọi nó là tập object thực tế duy nhất của toàn heap.

## 7. Promotion rate: vì sao objects sống lâu tạo áp lực old generation?

Trong generational collection, objects sống qua một hoặc nhiều young GCs có thể được **promote** từ young/survivor sang old, tùy collector, age threshold, survivor capacity và các điều kiện policy.

Mô hình:

```text
Allocation -> Eden
                |
           Young collection
          /              \
     chết/reclaim       sống/copy
                          |
                    survivor / age
                          |
                    promote to old
                          |
                  old occupancy tăng
```

**Promotion rate** đo lượng dữ liệu chuyển sang old trong một khoảng thời gian hoặc mỗi collection. Nó khác allocation rate:

- 500 MiB/s allocation có thể chỉ promote vài MiB/s nếu phần lớn objects chết nhanh.
- 50 MiB/s allocation có thể promote đáng kể nếu dữ liệu giữ xuyên nhiều requests.
- Một spike promotion ngắn chưa chắc leak; có thể là batch/cached warm-up.
- Promotion tăng liên tục cùng old occupancy sau collection tăng có thể báo cần điều tra retained graph hoặc collector capacity.

### Survivor capacity và premature promotion

Nếu survivor space/regions không đủ, một số objects có thể được đưa lên old sớm hơn tuổi thọ thiết kế lý tưởng của collector. Điều này làm **old occupancy tăng nhanh**, dù nhiều object sớm chết ở old. Khi đó GC phải tổ chức thêm marking/mixed collections để thu hồi old garbage.

Không có quy tắc “mọi object phải sống đúng N young GCs mới promotion”: age threshold/regions đều có tính động và thay đổi theo flags/workload.

## 8. Free space, committed và max là ba lớp giới hạn khác nhau

Một số liệu Java heap có thể có:

```text
Heap max       = 2048 MiB
Heap committed = 1536 MiB
Heap used      = 1200 MiB

Trong cùng snapshot:
  committed - used = 336 MiB
  max - committed  = 512 MiB
  max - used       = 848 MiB
```

Nhưng **848 MiB không tự bằng “space available for G1 evacuation”**.

- `committed - used` là phần heap chưa used trong mức committed; nó có thể phân bố ở regions/segments với chức năng khác nhau.
- `max - committed` là khả năng mở rộng **theo metric**, không bảo đảm luôn commit thêm ngay vì OS/container physical/native budget hoặc GC decisions.
- `max - used` là mức chênh lệch tổng hợp, không mô tả fragmentation, pinned/humongous regions hay reservation policy.
- G1 chọn **destination regions/to-space** dựa trên cấu trúc regions và policy. Cần đủ free regions để copy survivors/promoted objects thuộc CSet.
- Collector còn phải duy trì **headroom cho allocations xảy ra trong khi concurrent marking hoặc background reclamation chưa xong**.

### Vì sao heap có “free” vẫn không cấp phát được object?

Một collector có thể không tìm được **liên tục hoặc đúng loại vùng** đủ chứa một object; hoặc phải giữ những regions làm GC reserve; hoặc đang chịu **to-space exhaustion** khi copy những live objects. Tương tự, JVM có thể gặp allocation failure và thử GC/heap expansion trước khi báo OOME.

**Đừng đồng nhất** “heap used thấp hơn max” với “GC chắc chắn thành công trong một pause tiếp theo”.

## 9. Headroom: khoảng đệm mà GC cần để tiếp tục tiến triển

**Headroom** là dung lượng khả dụng hoặc được dự phòng giúp collector xử lý tình huống **trong lúc garbage vẫn chưa được reclaim**. Với relocating collector, nó có hai vai trò lớn:

1. **Mutator allocation headroom:** application vẫn tạo objects mới giữa các collections/trong concurrent phases.
2. **Evacuation/relocation headroom:** cần destination memory cho những live objects được copy/promote, trước khi source regions có thể được reclaim an toàn.

Mô hình ngân sách cực giản:

```text
Giả sử (minh họa):
  H = tổng heap capacity phù hợp
  L = occupied live data sau collection
  G = lượng allocation mới cần chịu trong một khoảng chờ GC
  E = yêu cầu destination/to-space + dự phòng GC
  M = safety margin

Muốn tránh pressure trong khoảng ấy:
  H phải đủ cho L + G + E + M
```

**Không dùng như điều kiện cần-đủ JVMS.** Các khoản có thể chồng lấn theo pha GC, một phần source heap được tái sử dụng, collectors có policy khác nhau, và `L`/`G` không nhất thiết đo cùng mốc. Công thức hữu ích để **nhớ các loại nhu cầu** nhưng không phải bộ tính heap size tự động.

### Minh họa to-space

```text
Trước evacuation:
  Region R0 (CSet) [live A B C + dead objects]
  Region R1 (CSet) [live D E + dead objects]
  Region R2 (free)
  Region R3 (free)
  Region R4 (old, ngoài CSet)

GC copy A,B,C,D,E sang R2,R3 (khi đủ chỗ).
Sau đó R0,R1 trở thành free và có thể tái sử dụng.

Nếu R2,R3 không đủ destination cho live data,
việc reclaim R0,R1 có thể không diễn ra trọn kế hoạch.
```

Trong trường hợp nguồn và đích chồng chéo bất hợp lý hoặc không còn free regions phù hợp, collector phải có kế hoạch fallback chứ không được đè object đang sống.

### G1 và `-XX:G1ReservePercent`

Trên HotSpot G1, `-XX:G1ReservePercent` là một tỷ lệ heap được **dự phòng như một false ceiling** để giảm rủi ro promotion/evacuation failure. Ví dụ thiết lập tùy mục đích:

```bash
java -Xms512m -Xmx2g -XX:+UseG1GC -XX:G1ReservePercent=15 -jar app.jar
```

`app.jar` là tên minh họa; lệnh chỉ hợp lệ nếu bạn có ứng dụng JAR tương ứng. **Không hiểu 15% là một vùng RAM vật lý cố định khóa riêng và không bao giờ được dùng.** Đây là **GC policy reserve** trong phần heap mà HotSpot quản lý; tác dụng, default và quan hệ tới adaptive IHOP/young sizing phụ thuộc JDK build.

**Không chỉnh reserve đơn độc theo một quy tắc máy móc.** Tăng reserve có thể giảm khả năng eviction/to-space exhaustion trong một workload, nhưng nó giảm effective heap capacity dành cho application và có thể đòi điều chỉnh total heap budget. Cần xem GC logs và benchmark.

## 10. G1 Java 17: tốc độ cấp phát quyết định nhịp young GC

G1 chia heap thành regions, dùng các young/old/survivor regions theo policy. Một chuỗi hoạt động có thể là:

1. Application allocation tạo objects, phần lớn vào Eden/young regions; có những trường hợp khác như humongous allocations.
2. Khi young collection được kích hoạt, G1 chọn collection set, root scan/remembered-set processing và **evacuate live objects** sang survivor/old destinations trong STW pause.
3. Source regions được reclaim; application tiếp tục allocation.
4. Old occupancy/projections tăng khiến G1 khởi động **concurrent marking** để chuẩn bị reclaim old regions.
5. Sau marking, **mixed collections** có thể thu hồi selected old regions cùng young collections.

Với allocation rate cao, Eden/young capacity có thể được dùng hết nhanh hơn → young GC có thể xảy ra thường xuyên hơn. Đây là xu hướng chứ **không phải công thức 1:1**: G1 thích nghi young-generation sizing, pause goals, GC causes và space availability.

### Mối quan hệ giữa Eden size, allocation rate và chu kỳ GC

Ước lượng rất thô trong một đoạn workload ổn định:

```text
Thời gian lấp đầy một budget Eden tương đương
≈ budget có thể cấp phát trong young / allocation rate

Ví dụ:
  budget hữu dụng ≈ 240 MiB
  allocation rate ≈ 120 MiB/s
  thời gian ≈ 2 s
```

**Đây không phải dự báo thời gian Young GC chính xác.** Có TLABs, humongous allocations, survivor occupancy, young sizing, pretenuring/other allocation paths và GC triggers khác. Nếu log thực tế cho thấy 0,5 s giữa young GCs, đừng “sửa số liệu” để khớp công thức; hãy điều tra nguyên nhân thật.

## 11. G1 concurrent marking: cần hoàn thành trước khi old đầy

Một điều G1 cần tránh: old regions tiếp tục tăng vì promotion/allocation, nhưng **concurrent marking chưa hoàn thành đủ sớm** để mixed collections thu hồi vùng old không còn live.

Khi quá trình chuẩn bị old-space reclamation chậm hơn nhịp tăng occupancy:

- Những young collections vẫn có thể xảy ra trong lúc concurrent marking đang chạy.
- Old occupancy có thể tiếp tục tăng qua promotion/humongous allocations.
- Khoảng trống cho evacuation/to-space và allocation mới thu hẹp.
- Nếu marking/reclamation không bắt kịp, có thể phát sinh **evacuation failure/to-space exhausted**, Full GC và trong tình huống nặng là OOME.

G1 sử dụng cơ chế **Initiating Heap Occupancy Percent (IHOP)** và có thể tự thích nghi ngưỡng bắt đầu marking dựa vào dự báo. Điều quan trọng là **mark sớm đủ để GC hoàn thành và bắt đầu reclaim old regions trước khi hết headroom**, chứ không phải cứ giảm một flag tới mức rất thấp là tốt.

### Liveness trong G1 Concurrent Marking không luôn là liveness “ngay lúc này”

G1 có sử dụng **Snapshot-At-The-Beginning (SATB)** cho concurrent marking. Object còn live ở đầu snapshot có thể được coi live trong marking cycle dù về sau đã không còn strongly reachable. Vì vậy:

- Phép đếm liveness trong một cycle có thể mang tính **conservative**.
- Một số objects chết sau snapshot có thể chỉ reclaim hiệu quả ở cycle về sau.
- Heap occupancy không giảm ngay khi code bỏ reference.
- “Object vừa bị dereference” không đồng nghĩa “G1 sẽ lập tức trả toàn bộ memory cho allocator”.

Đây là một lý do phải so **trends qua nhiều cycles**, không chỉ một con số sau đúng một marking phase.

## 12. Evacuation failure, to-space exhaustion và Full GC

Theo tài liệu G1 Java 17, khi collection giữ sống nhiều bytes đến mức **không tìm đủ destination space để copy**, G1 có thể gặp evacuation failure. Collector duy trì tính đúng bằng cách xử lý objects đã move và những objects chưa move theo cơ chế fallback; GC phase có thể tốn thêm thời gian.

Nếu application vẫn không có đủ headroom để tiếp tục cho đến khi reclaim old space thành công, G1 có thể phải dùng **Full GC, in-place compaction**, thường đắt hơn những young/mixed collections thông thường.

Các dấu hiệu trong GC logs tùy JDK build:

- `to-space exhausted` hoặc dấu hiệu evacuation failure.
- `Pause Full (Allocation Failure)` / Full GC variants.
- Old regions tăng nhanh, free regions giảm, young/mixed collections dày.
- Concurrent marking kéo dài so với tốc độ old occupancy tăng.
- Humongous regions nhiều, fragmentation/region pressure.

**Không suy rằng cứ thấy Full GC là Java heap leak.** Full GC có thể do retained live set lớn, allocation spikes, thiếu to-space, concurrent marking không kịp hoặc humongous allocations; cũng có thể là `System.gc()`/diagnostic action theo cấu hình. Cần xem **GC cause và timeline**.

### Humongous objects tạo áp lực khác bình thường

Trong G1, objects có kích thước lớn theo ngưỡng region size được xử lý đặc biệt dưới dạng **humongous objects/regions**. Chúng có thể chiếm nhiều regions liên tiếp và gây khó khăn cho region utilization, không nhất thiết theo đường young object thông thường.

- Allocation một array lớn có thể tiêu tốn một số nguyên regions, còn phần cuối chưa dùng vẫn không trở thành vùng tự do chung ngay.
- Nhiều humongous objects có thể làm old occupancy cao và tạo fragmentation theo regions.
- G1 có thể cần khởi động marking hoặc có các cơ chế reclaim đặc biệt tùy version.
- Điều chỉnh region size để thay ngưỡng humongous phải dựa trên **histogram kích thước objects và GC log thực tế**, không phải giải pháp mặc định.

## 13. Một bài toán số: allocation, live bytes và destination pressure

Giả sử (tất cả số đều minh họa):

- Một vùng collection chứa **300 MiB allocated**.
- Trong đó **30 MiB live** và **270 MiB dead**.
- Trước evacuation có **80 MiB destination capacity** theo giả định.

Ở đây GC có thể cần copy khoảng 30 MiB và vùng đích 80 MiB **có vẻ đủ** trong mô hình đơn giản. Sau evacuation, nó có thể reclaim 300 MiB source regions và còn lại khoảng 50 MiB destination capacity giả định, bỏ qua overhead.

Nếu tỷ lệ sống tăng:

- Collection set vẫn 300 MiB.
- Live data thành **250 MiB**.
- Destination capacity vẫn 80 MiB.

Một evacuating collector **không thể copy 250 MiB sống vào chỉ 80 MiB destination theo mô hình ấy**. Nó phải thay đổi CSet/policy hoặc fallback; với G1, một số dạng evacuation failure/to-space exhaustion có thể xảy ra trong tình huống headroom quá thấp.

**Quan trọng:** ví dụ này không phải công thức exact về G1 to-space vì collector có thể tái dùng regions, cập nhật schedule, mixed CSet và các vùng dự phòng khác. Nó minh họa **vì sao live bytes và destination capacity phải được xét cùng nhau**.

### Tập dữ liệu giống nhau, nhu cầu copy khác nhau

```text
Case A:
  CSet occupied  = 300 MiB
  live           = 30 MiB
  dead/reclaim   = 270 MiB
  survival       = 10%

Case B:
  CSet occupied  = 300 MiB
  live           = 250 MiB
  dead/reclaim   = 50 MiB
  survival       ≈ 83,33%
```

Allocation rate của application thậm chí có thể bằng nhau ở hai cases; khác biệt sống sót mới tạo độ khó GC khác hẳn.

## 14. Chương trình Java 17: tính các con số mô hình một cách xác định

Tạo file **`GcBudgetMath.java`**:

```java
public class GcBudgetMath {
    private static final long MIB = 1024L * 1024L;

    static long allocatedPerSecond(long requestsPerSecond,
                                   long bytesPerRequest) {
        return requestsPerSecond * bytesPerRequest;
    }

    static long deadBytes(long occupied, long live) {
        if (live < 0 || occupied < live) {
            throw new IllegalArgumentException("invalid liveness");
        }
        return occupied - live;
    }

    public static void main(String[] args) {
        long rateBytes = allocatedPerSecond(1024L, 128L * 1024L);
        long occupied = 300L * MIB;
        long live = 30L * MIB;
        long destination = 80L * MIB;

        System.out.println("rateMiBps=" + rateBytes / MIB);
        System.out.println("occupiedMiB=" + occupied / MIB);
        System.out.println("liveMiB=" + live / MIB);
        System.out.println("deadMiB=" + deadBytes(occupied, live) / MIB);
        System.out.println("survivalPercent=" + live * 100 / occupied);
        System.out.println("destinationEnough=" + (destination >= live));
    }
}
```

```bash
javac --release 17 GcBudgetMath.java
java GcBudgetMath
```

**Stdout chính xác:**

```text
rateMiBps=128
occupiedMiB=300
liveMiB=30
deadMiB=270
survivalPercent=10
destinationEnough=true
```

Giải thích:

- `1024 requests/s × 128 KiB = 128 MiB/s`.
- Phần live 30 MiB trong 300 MiB là 10%, phần dead là 270 MiB.
- `destinationEnough=true` **chỉ là so sánh số trong mô hình**, không phải kết quả kiểm tra G1 thực tế. Runtime còn có region granularity, collector reserve, object alignment, old occupancy, humongous allocations và nhiều trạng thái khác.
- Biểu thức `live * 100 / occupied` là **số nguyên** và sẽ làm tròn xuống nếu tỷ lệ không nguyên. Không dùng nó thay measurement precision cho GC metrics thực.

## 15. Chương trình Java 17: allocation churn không đồng nghĩa retention leak

Tạo file **`AllocationChurnDemo.java`**:

```java
public class AllocationChurnDemo {
    static volatile Object sink;

    public static void main(String[] args) {
        final int count = 4000;
        final int bytesPerAllocation = 32 * 1024;

        for (int i = 0; i < count; i++) {
            byte[] block = new byte[bytesPerAllocation];
            block[0] = (byte) i;
            sink = block;
        }

        long totalBytes = (long) count * bytesPerAllocation;
        System.out.println("iterations=" + count);
        System.out.println("nominalAllocatedMiB="
                + totalBytes / (1024 * 1024));
        System.out.println("lastBlockLength="
                + ((byte[]) sink).length);
    }
}
```

```bash
javac --release 17 AllocationChurnDemo.java
java AllocationChurnDemo
```

**Stdout của đường chạy bình thường, khi cấp phát thành công:**

```text
iterations=4000
nominalAllocatedMiB=125
lastBlockLength=32768
```

Trong vòng lặp, `sink` chỉ giữ **block mới nhất**; các blocks cũ không còn reachable qua `sink` (trừ những đường giữ sống đặc biệt khác). Vì vậy mã tạo **125 MiB dung lượng array danh nghĩa cộng dồn** nhưng không giữ lại 125 MiB arrays trong một collection thông thường.

**Không coi 125 MiB là kết quả đo allocation profiler chính xác**: object headers, alignment và các optimizations/runtime costs có thể khác, và ứng dụng còn cấp phát nhiều objects khác. `sink` volatile khiến việc publish reference diễn ra thật theo ngữ nghĩa, nhưng **không cam kết có đúng N lần GC** hoặc một cách tối ưu allocation cố định.

### Chạy thử với heap nhỏ và GC logs

Trên HotSpot Java 17:

```bash
java -Xms32m -Xmx64m -XX:+UseG1GC -Xlog:gc AllocationChurnDemo
```

Số collections, GC IDs, regions và pauses trong log **không cố định**; không đưa ra một stdout chung cho các dòng GC. Ba dòng do `System.out.println` trong code vẫn như trên nếu chương trình chạy xong không có OOME/resource failure.

## 16. Chương trình Java 17: giữ sống nhiều dữ liệu có chủ đích

Tạo file **`RetainedDataDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class RetainedDataDemo {
    static final List<byte[]> KEEP = new ArrayList<>();

    public static void main(String[] args) {
        for (int i = 0; i < 8; i++) {
            byte[] block = new byte[1024 * 1024];
            block[0] = 1;
            KEEP.add(block);
        }

        System.out.println("retainedEntries=" + KEEP.size());
        System.out.println("retainedNominalMiB="
                + KEEP.size() * 1024 * 1024 / (1024 * 1024));

        KEEP.clear();
        System.out.println("afterClear=" + KEEP.size());
    }
}
```

```bash
javac --release 17 RetainedDataDemo.java
java RetainedDataDemo
```

**Stdout chính xác:**

```text
retainedEntries=8
retainedNominalMiB=8
afterClear=0
```

Trong trường hợp này, static `KEEP` giữ **8 entries** tới khi `clear()` xóa các references. Đây là mô hình retention đơn giản; một real cache có thể giữ hàng triệu entries với graphs lớn hơn nhiều.

**Không suy từ `afterClear=0` rằng GC đã chạy hay RSS phải giảm ngay.** `clear()` chỉ cắt những đường reference từ list; vẫn có thể có references khác và GC có thời điểm làm việc riêng.

### Giữ sống cao không luôn là leak

Nếu 8 MiB này là model data mà ứng dụng cần dùng lại trong toàn bộ vòng đời, giữ chúng là đúng. Vấn đề xảy ra khi retention vượt **business lifecycle hoặc memory budget**, ví dụ cache không có limit, listener không được unregister, `ThreadLocal` giữ request cũ. Cách phân biệt là **ownership + lifecycle + xu hướng post-GC occupancy**, không phải object vẫn còn sau một lần collection.

## 17. Đo heap thật bằng `MemoryMXBean`

Java Management API cung cấp các snapshots của heap:

```java
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

public class HeapSnapshotDemo {
    private static final long MIB = 1024L * 1024L;

    public static void main(String[] args) {
        MemoryUsage heap =
                ManagementFactory.getMemoryMXBean()
                        .getHeapMemoryUsage();

        System.out.println("usedMiB=" + heap.getUsed() / MIB);
        System.out.println("committedMiB=" + heap.getCommitted() / MIB);
        System.out.println("maxMiB=" +
                (heap.getMax() < 0
                        ? "undefined"
                        : Long.toString(heap.getMax() / MIB)));
    }
}
```

```bash
javac --release 17 HeapSnapshotDemo.java
java -Xms64m -Xmx256m HeapSnapshotDemo
```

**Output có ba dòng nhưng giá trị phụ thuộc JVM/OS/timing**; không có “stdout chính xác chung” về heap used. `MemoryMXBean` cũng **không trả allocation rate** trực tiếp; nó là snapshot occupancy/commit/max, không thay thế profiler hoặc cumulative allocation counters.

Để ước lượng allocation rate bằng cách lấy delta của những **allocation counters có định nghĩa phù hợp**, phải đảm bảo metric thật sự **đếm bytes đã allocated**, không chỉ là used heap snapshot vốn có thể giảm do GC. **Không tính `(used2-used1)/seconds` thành allocation rate**: GC và object reclamation có thể làm chênh lệch âm dù application vẫn allocate nhanh.

## 18. Đo allocation rate, live set và GC pressure bằng công cụ nào?

| Câu hỏi | Quan sát phù hợp | Không nên dùng một cách máy móc |
| --- | --- | --- |
| Allocation nhiều đến từ method nào? | JFR allocation events, allocation profiling (async profiler/YourKit/JMC...) | Chỉ nhìn `-Xmx` |
| Allocation rate theo thời gian? | JFR/GC logs/counters có semantics allocation rõ ràng | `Δ(heap used)/Δt` khi có GC |
| Young collections diễn ra dày không? | `-Xlog:gc`, `jstat -gc` với đúng collector | Lấy tần suất cố định từ mã source |
| Survivors/promotions tăng không? | GC logs về Eden/Survivor/Old; collector-specific counters | Gọi mọi survivor bytes là old allocation |
| Post-GC live set có tăng đều? | Occupancy sau các collections đủ đại diện; heap dump/histogram | Một số used heap bất kỳ trước GC |
| Vì sao objects vẫn sống? | Heap dump, dominator tree, paths to GC roots | Đổ lỗi cho `byte[]` chỉ vì có shallow bytes lớn |
| G1 còn to-space/headroom không? | GC logs về free regions, evacuation failures, heap/region state | `-Xmx - heapUsed` coi là exact to-space |
| Full GC/OOME do gì? | GC cause, full GC timeline, object retention, memory limit | Mặc định “GC quá yếu” |
| Container có đủ tổng memory? | Cgroup memory metrics, RSS/PSS, heap + native budget | `-Xmx` đại diện toàn process |

**JFR (Java Flight Recorder)** hữu ích khi cần liên hệ allocation hotspots với CPU/time và GC events. Recording có chi phí runtime, sampling granularity và event thresholds; một allocation profiling view không nhất thiết bắt mọi allocation vật lý với độ chính xác byte tuyệt đối.

### Quy trình đơn giản khi đo một service Java 17

Đầu tiên, ghi lại:

- `java -version`, JVM vendor/build và GC đang dùng.
- `-Xms`, `-Xmx`, G1 flags liên quan và container memory limit.
- Tải thực: requests/s, concurrency, bytes/request, batch sizes.
- GC logging và metric timestamps theo cùng PID.
- Heap occupancy sau collection, young/old regions, promotion, pauses và percent time in GC.

Đo ít nhất ba giai đoạn: **warm-up**, **steady state** và **peak/burst**. Không lấy một lần khởi động vừa JIT/initialize cache xong làm đại diện cho steady-state.

## 19. Unified GC Logging Java 17: cách đọc nhịp GC

Trên HotSpot:

```bash
java -Xms256m -Xmx1g -XX:+UseG1GC -Xlog:gc* -jar app.jar
```

Muốn tập trung GC phases/heap với mức chi tiết tùy build:

```bash
java -Xms256m -Xmx1g -XX:+UseG1GC \
  -Xlog:gc*,gc+heap=info,gc+phases=debug \
  -jar app.jar
```

Đây là **lệnh mẫu cho shell hỗ trợ backslash line continuation**; trên Windows PowerShell 5.1 phải viết thành một dòng hoặc dùng cú pháp tiếp dòng của PowerShell. `app.jar` là tên minh họa của một ứng dụng thật, không phải file được tạo sẵn trong bài.

Trong G1 logs, tìm:

- **Young GC cadence:** khoảng thời gian giữa các collections và bytes/regions Eden đã được dùng.
- **Eden/Survivor/Old regions:** vùng nào tăng/giảm sau pauses.
- **Evacuation time:** phases scan/merge heap roots, object copy, reference processing (tên thay theo JDK).
- **Concurrent Start/Remark/Cleanup:** marking có bắt đầu và hoàn thành đủ sớm không.
- **Mixed GC:** old region reclamation có diễn ra và thu hồi đáng kể không.
- **Humongous regions:** vùng bị object rất lớn giữ, có xu hướng tăng hay không.
- **Evacuation Failure / to-space exhausted:** có phải collector thiếu destination memory?
- **Full GC:** lý do/cause và pause duration, sau đó heap reclaim được bao nhiêu.

**Đừng coi một dấu hiệu đơn lẻ là kết luận root cause.** Ví dụ young GC dày có thể do allocation rate cao, young sizing nhỏ, heap pressure hoặc collector policy; cần xem thêm allocation histogram/throughput/live set.

### Dùng `jcmd` và `jstat` đúng bối cảnh

```bash
jcmd <PID> VM.flags
jcmd <PID> GC.heap_info
jstat -gc <PID> 1000 5
```

`<PID>` là placeholder cho process Java đang chạy đủ lâu. Đơn vị/counters của `jstat` có thể khác collector, và một số fields không áp dụng. Đừng giả định các số `S0U`/`S1U` hoặc `OU` ở các collector hiện đại luôn tương ứng đúng một contiguous memory block theo mô hình Serial GC cổ điển.

## 20. Cách ước lượng memory budget để có headroom

Đặt mục tiêu rõ:

- Peak concurrent requests/batches làm live working set tăng bao nhiêu?
- Allocation rate trong steady state và burst cao nhất là bao nhiêu?
- Young survival rate thay đổi ra sao giữa các loại request?
- Old live occupancy sau marking/mixed cycles là bao nhiêu?
- Tỷ lệ heap cần duy trì làm GC reserve/evacuation space phù hợp?
- Container còn headroom cho **native memory, metaspace, code cache, thread stacks, direct buffers, OS/cgroup accounting** hay không?

Mô hình ngân sách vận hành (không phải phép cộng chính xác của mọi metrics):

```text
Cần có không gian cho:
  - ổn định: live objects còn cần cho ứng dụng
  - biến động: allocation trong lúc collector đang làm việc
  - di chuyển: survivors/promotion/to-space
  - bất định: spikes, humongous objects, fragmentation, reserve
  - ngoài heap: native memory + container headroom
```

Khi đặt `-Xmx` rất sát container limit, dù heap chưa chạm max, process vẫn có thể bị OOMKilled bởi **native/process total memory**. Tăng heap để chữa to-space pressure có thể đòi tăng container budget, giảm native usage hoặc chỉnh đồng thời các memory consumers khác.

### Vì sao tăng heap không luôn làm pause tốt hơn?

Một heap lớn hơn có thể giảm GC frequency vì nhiều allocation headroom, nhưng có thể làm lượng memory cần scan/mark/reclaim của một số phases lớn hơn, tăng native/physical footprint và khiến garbage tồn tại lâu hơn giữa cycles. Kết quả phụ thuộc GC và workload; phải đo **throughput, p95/p99 pauses, footprint** trước–sau, không chỉ nhìn “GC ít đi”.

## 21. Tối ưu allocation rate: các chiến lược ít rủi ro

Trước tiên tìm hot paths có **allocation nhiều mà không cần thiết**:

1. Giảm tạo object trung gian trong serialization/parsing khi đã có API stream/buffer phù hợp.
2. Tránh copy `String`/arrays/collections nhiều lần nếu có thể dùng view hoặc immutable reference an toàn.
3. Trong vòng lặp hot path, tránh boxing/unboxing ngoài ý muốn và các temporary collections có thể thay bằng primitive data structures khi được chứng minh đáng giá.
4. Nếu logging disabled, tránh eager formatting/concatenation gây allocations không cần thiết; dùng parameterized logging phù hợp.
5. Giới hạn batch sizes, ưu tiên streaming/backpressure thay vì đọc toàn bộ dữ liệu vào RAM.
6. Nếu tái sử dụng buffers, định nghĩa rõ ownership/size limit và tránh giữ buffer quá lớn cho workloads nhỏ.
7. Tối ưu theo **allocation bytes/op + CPU + latency + retained heap**, không chỉ giảm số lệnh `new` trong source.
8. Cẩn trọng object pooling: giữ object lâu làm tăng live set, fragment memory hoặc thêm contention.

**Nguyên tắc:** giảm allocation **vô ích**, không nhất thiết giảm mọi allocation ngắn hạn. Young GC được thiết kế để xử lý nhiều object chết nhanh.

## 22. Tối ưu live set và tránh retention leak

Một live set lớn có thể hoàn toàn hợp lệ (ví dụ dữ liệu model đang phục vụ users), nhưng nếu tăng theo thời gian không giới hạn cần kiểm tra:

- Cache không có maximum size/weight/TTL hoặc invalidation.
- Listener/observer đã đăng ký nhưng không unsubscribe.
- `ThreadLocal` giữ request context trong worker thread pool vì thiếu `remove()` trong `finally`.
- Pending tasks/futures/queues/backpressure không có giới hạn.
- Static singleton giữ toàn bộ session, transaction hoặc plugin data.
- References từ long-lived class loader/TCCL/JNI handles giữ class metadata/object graphs.
- Heap dump chỉ ra một object root hoặc path giữ sống có retained size bất thường.

Không ép `System.gc()` để xóa một object còn strongly reachable. Sửa tại **owner của reference path** bằng bounded caching, unsubscribe, cancel/release resources hoặc hoàn tất lifecycle.

### Ví dụ phân biệt allocation spike và retained growth

- **Spike 1:** Service tạo tạm 500 MiB buffer data khi xử lý một batch rồi bỏ hết. Heap used có thể tăng nhanh, GC sau đó reclaim, live set ổn định.
- **Spike 2:** Service thêm mỗi batch một entry 20 MiB vào static cache không eviction. Sau 20 batches, cache đã giữ thêm 400 MiB live data. GC không reclaim entry đó, dù không còn request chạy.
- **Spike 3:** Service giữ đúng 400 MiB state cần thiết suốt phiên làm việc. Đây không phải leak nếu có budget/lifecycle được chấp nhận, nhưng vẫn cần headroom cho GC.

Cả ba có thể trông “heap increased 400–500 MiB” trên dashboard tại một thời điểm, nhưng fix hoàn toàn khác nhau.

## 23. GC frequency, pause time và overhead: đọc cùng nhau

Tăng allocation rate thường làm young collections xuất hiện thường xuyên hơn **nếu capacity và policy tương đương**, nhưng pause duration có thể không tăng tương ứng khi survival rate thấp. Ngược lại, live data lớn trong CSet có thể làm một pause đắt dù young collections không quá dày.

Ví dụ hai profile giả định:

| Metric | Profile X | Profile Y |
| --- | ---: | ---: |
| Allocation rate | 600 MiB/s | 100 MiB/s |
| Young survival ratio | 3% | 60% |
| Young GC frequency | Cao | Có thể thấp hơn |
| Bytes phải copy mỗi collection | Thấp so với occupancy | Cao so với occupancy |
| Risk | CPU/throughput do GC cadence | Pause/to-space pressure và old occupancy |

Kết luận nào về p99 latency cũng cần xem **actual pauses**, thread scheduling và workload. Một collector có thể phải chịu overhead concurrent CPU ngay cả khi số STW pauses nhỏ.

### Little’s Law là gợi ý, không phải phép đo exact live set

Có thể dùng trực giác **bytes in-flight ≈ input rate × average time kept**, tương tự Little’s Law, nếu hệ thống ổn định và units/ownership được định nghĩa:

```text
100 MiB/s object payload
× 2 s average retention
≈ 200 MiB dữ liệu đang in-flight (mô hình)
```

Phép tính này **không phải live heap đo bằng GC**, vì kích thước object graph, duplication, caching, peak concurrency và JVM layout làm con số khác. Nó hữu ích để suy ra nếu request latency tăng gấp đôi mà throughput giữ nguyên, **số dữ liệu đang đồng thời sống** có thể tăng.

## 24. Tình huống chẩn đoán: allocation cao, live set thấp

**Dấu hiệu:** nhiều young GCs nhưng old generation ổn định; post-young-GC occupancy giảm mạnh; Full GC hiếm; CPU GC đáng kể.

**Giả thuyết:** ứng dụng tạo nhiều short-lived intermediates; GC reclaim tốt nhưng phải chạy quá thường xuyên.

**Hướng làm:**

1. Dùng JFR/allocation profiler xác định methods phân bổ nhiều bytes/op.
2. Đối chiếu GC rate với QPS và request types; nhận diện request hay payload gây burst.
3. Tối ưu allocation hot path (giảm temporary objects/copies) hoặc cân nhắc young sizing/heap capacity sau khi có benchmark.
4. Đo lại CPU/throughput, p99 và live set để chắc rằng tối ưu không giữ data lâu hơn.
5. Không tăng heap vô hạn chỉ nhằm tránh một vài young GCs; kiểm tra footprint và latency budget.

## 25. Tình huống chẩn đoán: allocation vừa phải, live set tăng

**Dấu hiệu:** old occupancy tăng qua các cycles, post-GC heap used không về baseline, GC reclaim ratio giảm, cuối cùng heap gần max.

**Giả thuyết:** cache/listener/ThreadLocal/queue giữ những object graphs không còn cần, hoặc legitimate working set đang tăng theo dữ liệu người dùng.

**Hướng làm:**

1. Chụp heap dump/histogram tại mốc phù hợp, đọc retained size và paths to GC roots.
2. So với business lifecycle: object có lẽ phải hết sau request/logout/component close.
3. Nếu leak, cắt reference path từ owner có vòng đời dài hơn.
4. Nếu live data hợp lệ, điều chỉnh retention policy, sharding/batching hoặc capacity budget phù hợp.
5. Tái kiểm tra post-GC occupancy trên workload tương đương sau fix.

**Không kết luận một static map lớn tự động là leak.** Nếu nó là cache có giới hạn và hit rate tốt, lượng memory giữ có thể là purposeful working set.

## 26. Tình huống chẩn đoán: thiếu to-space dù heap chưa báo 100%

**Dấu hiệu:** G1 log có `to-space exhausted`/evacuation failure, old regions cao, mixed collections không kịp, Full GC hoặc pause spikes.

**Giả thuyết:** nhiều survivors cần copy, free regions hiệu dụng không đủ hoặc old marking/reclamation đến quá muộn. Cũng có thể có humongous region pressure.

**Hướng làm:**

1. Đọc **GC cause**, old/young/humongous regions, evacuation/marking timeline.
2. Xác định **live bytes và promotion rate** có tăng không; kiểm tra retained objects bằng heap dump nếu nghi leak.
3. Kiểm tra allocation bursts/humongous allocations và concurrency của workload.
4. Dùng tài liệu đúng JDK/GC để xem `G1ReservePercent`, adaptive IHOP, heap sizing và concurrent marking capacity.
5. Cân nhắc tăng heap/headroom **cùng container limit phù hợp** sau khi xác minh ứng dụng thực sự cần live set ấy.
6. Benchmark lại p99 pauses, throughput, memory footprint; tránh đổi nhiều flags cùng lúc không thể quy nguyên nhân.

Một Full GC có thể giải quyết nhất thời fragmentation và phục hồi headroom, nhưng không sửa được một retained graph tăng vô hạn.

## 27. Những nhầm lẫn cần loại bỏ

| Hiểu sai | Cách hiểu chính xác |
| --- | --- |
| “Allocation rate cao là memory leak” | Allocation có thể là short-lived churn; leak là retention không còn cần |
| “GC càng thường xuyên thì live set càng lớn” | GC frequency còn phụ thuộc rate, young sizing và policy |
| “Heap used là toàn bộ live bytes” | Used còn chứa garbage chưa reclaim; partial collections không đo toàn heap |
| “Sau một young GC, old occupancy là live set exact” | Old regions có objects chưa trace đầy đủ ở mốc đó |
| “Tổng free heap bằng toàn bộ G1 to-space” | G1 dùng regions, reserve, policy và destination constraints |
| “`-Xmx - used` là headroom có thể dùng ngay” | Chưa xét committed, OS limits, fragmentation và GC reserves |
| “Copying collector không cần extra space” | Evacuation cần destination cho live objects |
| “Survival rate cao luôn tốt vì objects còn sống” | Nó làm copy/retention cost cao và reclaim ratio thấp |
| “Promotion luôn là lỗi” | Một phần dữ liệu hợp lệ sống lâu và cần old storage |
| “Tăng `-Xmx` luôn giảm pause” | Có thể giảm frequency nhưng tăng footprint hoặc work per cycle |
| “Tăng reserve luôn tăng throughput” | Reserve giảm usable application capacity và cần benchmark |
| “`System.gc()` giải phóng object còn được cache giữ” | GC không bỏ qua strong reachability |
| “Allocation rate = thay đổi heap used theo giây” | Heap used có thể giảm do GC, không đếm cumulative allocated bytes |
| “Có Full GC chắc chắn leak” | Cần phân tích GC cause, live data, to-space và OS limits |
| “G1 concurrent marking nghĩa object copy hoàn toàn concurrent” | G1 Java 17 evacuates CSet chủ yếu trong STW pauses |
| “Humongous objects giống objects nhỏ trong Eden” | G1 có chính sách special region allocation/reclaim |
| “Đo một snapshot đủ để biết xu hướng” | Cần timeline qua workload/GC cycles tương đương |
| “Collector nhanh nhất là collector có ít GC events nhất” | Cần cả throughput, tail latency, CPU và footprint |

## 28. Checklist thực hành khi chọn cách xử lý GC pressure

Trước khi tune flags, hãy trả lời được:

1. **Allocation rate hiện tại** là bao nhiêu MiB/s và phân bổ theo endpoint/job nào?
2. Workload **steady-state hay burst**? QPS/concurrency và payload sizes có biến động không?
3. **Survival rate/bytes copied** của các collection sets có cao bất thường không?
4. **Promotion rate** và old occupancy đang tăng, ổn định hay giảm?
5. **Post-GC live data** có ổn định sau các chu kỳ tương đương không?
6. Có **heap retention graph** không còn hợp lệ (cache/listener/ThreadLocal/queues)?
7. G1 có **free regions/to-space** đủ cho survivors/promotions không?
8. Concurrent marking bắt đầu **đủ sớm** để mixed collections reclaim old space?
9. Có nhiều **humongous objects**, fragmentation hay to-space exhaustion không?
10. Hiện tượng xảy ra ở JVM heap hay ở **native/container memory** ngoài `-Xmx`?
11. GC time chiếm bao nhiêu % CPU/wall time; **p95/p99 pause** có vi phạm SLO?
12. Có thể giảm **allocation không cần thiết**, giảm retention, giới hạn concurrency trước khi tăng heap?
13. Các flags có được hỗ trợ bởi **JDK 17 build** đang chạy không?
14. Thay đổi đã được **benchmark từng biến** và có plan rollback khi p99/footprint xấu hơn chưa?

## 29. Câu hỏi tự kiểm tra

**Câu 1:** Service A tạo 800 MiB/s nhưng post-GC live set giữ ổn định 200 MiB. Có thể kết luận bị leak?

**Không.** Đây có thể là allocation churn rất cao; cần kiểm tra GC frequency/CPU và allocation hotspots, không khẳng định retained graph tăng.

**Câu 2:** Service B tạo 20 MiB/s nhưng sau mỗi chu kỳ old occupancy lại tăng. Hướng điều tra?

**Retention và promotion**, có thể cache/ThreadLocal/listener hoặc working set hợp lệ tăng; dùng heap dump và owner/lifecycle analysis.

**Câu 3:** 400 MiB CSet có 40 MiB live và 360 MiB dead. Survival rate theo mô hình?

**10%**. Khoảng 40 MiB live phải được bảo toàn; 360 MiB là reclaimable trong mô hình.

**Câu 4:** Heap max 2 GiB, used 1,6 GiB. Có chắc 0,4 GiB là to-space rảnh của G1?

**Không.** Đây chỉ là tổng chênh lệch; phải xem committed, regions, reserve và các constraints khác.

**Câu 5:** Vì sao G1 có thể Full GC sau to-space exhausted?

Collector thiếu destination hoặc không reclaim old space kịp; fallback full-heap compaction có thể được dùng để tạo tiến triển.

**Câu 6:** `G1ReservePercent` có phải reservation RAM vật lý tách riêng ngoài `-Xmx`?

**Không.** Nó là policy reserve/false ceiling liên quan dung lượng heap nhằm giảm nguy cơ to-space/promotion failure.

**Câu 7:** Vì sao concurrent marking có thể chưa reclaim một object vừa trở nên unreachable?

G1 SATB có thể vẫn coi object snapshot-live trong marking cycle đang diễn ra; reclamation có thể xảy ra ở cycle tiếp theo.

**Câu 8:** Hai ứng dụng có allocation rate như nhau nhưng survival 5% và 90%. Evacuation cost có thể khác không?

**Có.** Tập live cần bảo toàn/copy khác đáng kể, ảnh hưởng GC CPU, pause và destination space.

**Câu 9:** Dùng `(heapUsed2 - heapUsed1)/Δt` có đo chính xác allocation rate không?

**Không.** GC có thể reclaim giữa hai lần đọc, nên chênh lệch là occupancy change chứ không phải cumulative allocation.

**Câu 10:** Một heap dump thấy nhiều `byte[]` lớn. Đã chứng minh nơi allocation chúng là lỗi chưa?

**Chưa.** Cần truy path to GC roots/retained owner và xem lifecycle; nơi tạo bytes có thể hợp lý nhưng nơi giữ quá lâu mới là bug.

## 30. Tài liệu tham chiếu

- [Oracle Java SE 17 — Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): collection phases, SATB marking, mixed collections, regions, evacuation failure và chính sách thu hồi.
- [Oracle Java SE 17 — Garbage-First Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector-tuning.html): allocation pressure, Full GC, to-space exhaustion, reserve/IHOP và cách chẩn đoán.
- [Oracle Java SE 17 — Introduction to Garbage Collection Tuning](https://docs.oracle.com/en/java/javase/17/gctuning/introduction-garbage-collection-tuning.html): throughput, pause goals, allocation/reclamation và các cơ chế GC.
- [Oracle Java SE 17 — Available Collectors](https://docs.oracle.com/en/java/javase/17/gctuning/available-collectors.html): Serial, Parallel, G1, ZGC và lựa chọn collector.
- [Java SE 17 — `MemoryUsage` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryUsage.html): used, committed, max và init.
- [Java SE 17 — `MemoryMXBean` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/MemoryMXBean.html): heap/non-heap usage.
- [Java SE 17 — `ThreadMXBean` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.management/java/lang/management/ThreadMXBean.html): thread diagnostics.
- [Java SE 17 — `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): `-Xms`/`-Xmx`, `-Xlog`, `G1ReservePercent` và các VM flags.
- [Java SE 17 — `jcmd` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html) và [`jstat` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jstat.html): heap/GC runtime diagnostics.
- [Java SE 17 — Flight Recorder API](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/module-summary.html): nền tảng thu thập sự kiện runtime và hỗ trợ quan sát allocations/GC.

**Ghi nhớ:** **Allocation rate** giải thích tốc độ heap được tiêu thụ; **live set/survival** giải thích GC phải giữ hoặc copy bao nhiêu dữ liệu; **headroom/to-space** giải thích collector có đủ chỗ để tiếp tục trong khi chưa reclaim được source space hay không. Chỉ khi đo và liên hệ cả ba với **GC phases, old occupancy, promotion, humongous regions, pause/throughput và giới hạn process/container** mới có thể phân biệt allocation churn bình thường, memory retention leak và evacuation pressure.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="garbage-collection-mechanics-009">Thông lượng, độ trễ, CPU và dung lượng bộ nhớ trong bài toán lựa chọn GC</a>

<details>
<summary>Xem chi tiết</summary>

# Lựa chọn Garbage Collector: thông lượng, độ trễ, CPU và dung lượng bộ nhớ

## Mục tiêu bài học

Không có một garbage collector (GC) luôn tốt nhất cho mọi ứng dụng Java. Một batch job có thể cần **xử lý được nhiều công việc nhất mỗi phút**, một API giao dịch có thể phải giữ **p99 response time** thấp, còn một container dịch vụ nhỏ có thể bị giới hạn **CPU quota và RAM** nghiêm ngặt. Những yêu cầu đó có thể xung đột: GC chạy đồng thời có thể giảm thời gian dừng ứng dụng nhưng sử dụng thêm CPU và memory bandwidth; một heap rộng có thể giảm tần suất GC nhưng tăng memory footprint; một collector ưu tiên thông lượng có thể chấp nhận những lần dừng dài hơn.

Sau bài học, bạn có thể:

- Định nghĩa và đo đúng **throughput**, **latency (p50/p95/p99)**, **GC pause**, **GC CPU cost**, **allocation rate**, **live set**, **heap footprint** và **process RSS**.
- Phân biệt **tỷ lệ thời gian Stop-The-World (STW)** với thông lượng nghiệp vụ; biết vì sao concurrent GC không hiện đầy đủ trong tỷ lệ STW.
- Hiểu tác động của heap headroom, live-object ratio, object lifetime, large allocations và áp lực bộ nhớ.
- So sánh **Serial, Parallel, G1, ZGC và Shenandoah** trong HotSpot với ràng buộc phiên bản/JDK distribution.
- Xây dựng một **quy trình lựa chọn dựa trên SLA/SLO**, không lựa collector chỉ từ khẩu hiệu “GC nhanh nhất”.
- Lấy GC logs, JFR, metrics và jcmd đúng cách; thiết kế thử nghiệm A/B có warmup, tải thực và cùng điều kiện CPU/RAM.
- Diễn giải ví dụ Java 17+ có kết quả xác định mà không bịa số liệu GC phụ thuộc môi trường.
- Nhận ra những lỗi phổ biến khi tuning và các chiến lược giảm allocation/retention trước khi đổi collector.

## 1. Bốn trục đánh đổi

Một collector ảnh hưởng ít nhất bốn trục chính:

| Trục | Câu hỏi của người vận hành | Ví dụ chỉ số |
| --- | --- | --- |
| **Thông lượng (throughput)** | Trong một đơn vị thời gian, ứng dụng hoàn thành bao nhiêu công việc hữu ích? | requests/s, jobs/min, records/s, bytes/s |
| **Độ trễ (latency)** | Một yêu cầu mất bao lâu, đặc biệt ở phần đuôi phân phối? | p50, p95, p99, p99.9, max response time; GC pause distribution |
| **CPU** | Collector lấy bao nhiêu năng lực xử lý từ ứng dụng và hệ thống? | process CPU time, GC threads CPU, CPU quota throttling, cycles/op |
| **Bộ nhớ (memory footprint)** | Cần bao nhiêu heap và bộ nhớ của cả tiến trình để an toàn? | -Xmx, live heap, committed heap, RSS, native memory, headroom |

Chúng liên quan nhưng **không đồng nhất**. Ví dụ, một hệ thống có p99 GC pause rất thấp vẫn có thể có p99 request latency cao do database, queueing, locks, CPU contention hoặc network. Ngược lại, throughput tốt không có nghĩa một giao dịch riêng không từng bị dừng bởi GC.

### Ví dụ ba workload khác nhau

**Batch ETL:** xử lý 200 triệu records trong thời gian ngắn nhất; có thể chấp nhận pause hàng trăm mili-giây đến giây nếu công việc không phục vụ tương tác tức thì. Có thể ưu tiên throughput.

**REST API tương tác:** p99 từ ingress tới response cần dưới một ngưỡng nhất định, ví dụ 150 ms. GC pause một lần 250 ms có thể trực tiếp gây trượt SLO, nhưng **không chỉ GC** tạo tail latency. Cần quan sát full latency path.

**Nhiều JVM nhỏ trên cùng host/container:** mỗi process chỉ có 1 vCPU và hạn mức RAM chặt. Việc dành quá nhiều CPU cho concurrent GC hoặc quá nhiều headroom cho mỗi JVM có thể làm giảm mật độ triển khai, dù pause nhỏ.

**Kết luận đầu tiên:** hãy viết rõ **SLO hữu ích cho người dùng/kinh doanh**, sau đó mới chọn GC và heap size.

## 2. Định nghĩa throughput: phải nói đang đo thứ gì

Trong tài liệu GC, throughput đôi khi được mô tả gần đúng là **tỷ lệ thời gian chương trình làm việc thay vì STW collection**. Nhưng trong vận hành thực tế, cách đo quan trọng hơn là **tốc độ hoàn thành công việc hữu ích** dưới tải và SLO.

Hai phép đo có liên quan nhưng khác nhau:

~~~text
Application throughput:
  jobs_completed / wall_clock_seconds
  hoặc requests_completed / elapsed_seconds

STW pause fraction (chỉ để tham khảo):
  sum(STW_pause_durations_within_window) / window_duration

Mutator available time fraction (xấp xỉ):
  1 - STW_pause_fraction
~~~

Ví dụ trong một khoảng 10 giây có tổng STW pauses 0,3 giây:

~~~text
Window                  = 10.0 s
Total STW pauses        =  0.3 s
STW pause fraction      =  3%
Approx mutator time     = 97%
~~~

**Không được tuyên bố “throughput ứng dụng = 97%”** chỉ từ những con số trên. Khi GC chạy concurrent:
- Collector vẫn sử dụng CPU cores và memory bandwidth trong lúc mutators không bị dừng.
- Read/write barriers có thể làm đường đi của ứng dụng tốn thêm instructions.
- CPU contention có thể làm chậm request completion mà không xuất hiện như một STW pause.
- JIT, application locking, I/O và tail effects có thể thay đổi hiệu năng tổng.

Để đánh giá business throughput, hãy đo **completed work/s** và **saturation point**, đồng thời ghi **error rate**. Một chương trình trả lỗi nhanh có thể có requests/s lớn nhưng không hoàn thành công việc hữu ích đúng nghĩa.

### Đặt tải không đổi khi so sánh

So hai collector ở 500 requests/s sẽ không có ý nghĩa nếu một collector thực tế nhận 1000 requests/s trong thử nghiệm kia. Hãy phân biệt:
- **Offered load**: tốc độ client cố gắng gửi requests.
- **Completed throughput**: tốc độ server hoàn tất requests thành công.
- **Backlog/queue depth**: số yêu cầu chờ.
- **Latency distribution**: thời gian chờ + xử lý thực tế.
- **Error/timeout rate**: số requests không thành công.

Một collector tăng completed throughput nhưng tạo p99 vượt ngưỡng vẫn có thể **không đạt SLO**. Chọn theo mục tiêu chứ không xếp hạng collector bằng một số duy nhất.

## 3. Độ trễ: phân biệt GC pause với thời gian phản hồi

**GC pause** là khoảng thời gian collector dừng các application threads do sự kiện GC cụ thể (tùy cách ghi log và phase). **Response time** của một HTTP request là thời gian từ lúc request được nhận/đo ở một điểm đầu đến khi trả response ở điểm cuối được xác định.

Chúng không bằng nhau.

~~~text
Request timeline (minh họa):

Ingress --> queue wait --> application CPU --> GC STW --> DB wait
        --> serialization --> network send --> response

Response latency = tất cả các đoạn trong định nghĩa đo
GC pause latency = chỉ khoảng dừng do GC đã đo riêng
~~~

Một request có thể trượt p99 vì bị queueing dưới CPU throttling dù GC pause nhỏ. Ngược lại, một GC pause 100 ms có thể khiến nhiều requests cùng bị trễ và làm **tail latency** nặng hơn thời gian pause riêng lẻ do hàng đợi tích tụ.

### Percentile p50/p95/p99 là gì?

Nếu đo đủ mẫu cùng định nghĩa:
- **p50**: trung vị gần đúng; nửa quan sát không lớn hơn ngưỡng này theo quy tắc percentile được chọn.
- **p95**: khoảng 95% observations không vượt ngưỡng.
- **p99**: khoảng 99% observations không vượt ngưỡng.
- **p99.9**: quan tâm những lần hiếm hơn, cần số mẫu lớn và phương pháp thống kê phù hợp.

**Các công cụ percentile có thể dùng thuật toán/interpolation khác nhau** nên kết quả số cụ thể đôi lúc khác. Khi so, hãy dùng cùng phương pháp, cùng cửa sổ thời gian và tập requests. Đừng lấy **p99 GC pause** làm **p99 request latency**.

### Ví dụ một sự cố thật về mặt logic

Một collector giữ 99% GC pauses dưới 8 ms nhưng vẫn có một lần stop-the-world 400 ms trong một giờ. Nếu hệ thống có SLA p99.9 dưới 100 ms hoặc hàng đợi rất nhạy, đuôi còn lại có thể đủ gây lỗi. Ngược lại, một collector có vài pauses 60 ms có thể không làm request p99 trượt nếu ứng dụng còn dư latency budget và throughput ổn định.

**Điều kiện cần** là đưa GC metrics vào cùng timeline với service latency, CPU saturation, queueing, allocation pressure và errors.

## 4. CPU: tổng chi phí, CPU đồng thời và quota

GC có hai loại work chính (không phải mọi collector chia chính xác như nhau):

1. **STW work**: dừng mutators để một phần GC xử lý roots, references, copy/sweep/compact...
2. **Concurrent/background work**: marking, remapping, refinement, relocation hoặc housekeeping khi application threads vẫn chạy.

Hai collector có cùng tổng STW pause fraction có thể tiêu thụ lượng CPU rất khác nhau.

Ví dụ giả định:
- Collector A STW 4% thời gian, gần như không làm concurrent work.
- Collector B STW 0,3% nhưng dùng thêm một lượng đáng kể CPU cores để mark/relocate đồng thời.

Không thể kết luận B có throughput cao hơn chỉ vì STW thấp. Nếu máy có nhiều idle cores, phần CPU đồng thời có thể chấp nhận được. Nếu container chỉ được **1 vCPU**, GC threads có thể cạnh tranh mạnh với application threads, làm tăng tail latency.

### Phân biệt CPU utilization

Một process có thể dùng **200% CPU** trên hệ thống đo kiểu “100% = một core” khi chạy trên hai cores. Vì vậy cần ghi:
- Host có bao nhiêu logical cores?
- Container CPU quota/cpuset bao nhiêu?
- JVM đã nhận diện bao nhiêu active processors?
- CPU usage là **process**, **container** hay **host**?
- Có CPU throttling không?
- Collector sử dụng threads riêng bao nhiêu?

Các công cụ OS/container (ví dụ cgroup CPU throttling metrics) cần được phối hợp với Java GC data. Không suy luận “GC ít pause thì ít tốn CPU” hay “CPU host còn rảnh tức JVM không bị quota”.

### Memory bandwidth cũng là tài nguyên

GC copying/evacuation, root scanning và write barriers có thể tăng:
- cache misses,
- memory bandwidth,
- work trên reference graph,
- dirty pages,
- CPU contention.

Ngay khi CPU usage không đạt 100%, hệ thống vẫn có thể bị memory-bandwidth pressure hoặc memory stalls. Lựa chọn GC phải xét cả **CPU/memory architecture** và cách dữ liệu của ứng dụng phân bổ.

## 5. Bộ nhớ: heap size không phải toàn bộ process footprint

Một JVM process cần nhiều hơn Java heap:

~~~text
Process memory / RSS (minh họa)
  |
  +-- Java heap (objects/arrays, GC regions)
  +-- Metaspace / class metadata
  +-- Code cache / JIT-compiled code
  +-- Thread stacks / native thread-related memory
  +-- Direct ByteBuffers / native allocations
  +-- GC native metadata, card tables/remembered sets
  +-- Native libraries, runtime structures, memory mapping ...
~~~

Vì vậy **`-Xmx=2g` không có nghĩa RSS process sẽ luôn dưới 2 GiB**. Và committed heap lớn không phải đồng nghĩa live objects đều đang dùng từng byte. Một số cấu hình collector giữ nhiều committed memory để tái sử dụng, một số có cơ chế uncommit khác nhau.

| Khái niệm | Nghĩa | Cảnh báo |
| --- | --- | --- |
| **-Xms** | Initial heap size theo JVM flags | Không phải toàn bộ RAM đã dùng của process. |
| **-Xmx** | Max Java heap | Không giới hạn mọi native memory của process. |
| **Used heap** | Dung lượng heap JVM coi đang được sử dụng | Không luôn là live bytes sau full liveness analysis. |
| **Committed heap** | Bộ nhớ JVM đã commit cho heap | Có thể lớn hơn used. |
| **Live set** | Objects còn sống/reachable cần giữ | Thay đổi theo thời điểm và collector/cách đo. |
| **Allocation rate** | Bytes được cấp phát theo thời gian | Có thể rất lớn dù live set nhỏ. |
| **Heap headroom** | Chênh giữa heap budget và phần bộ nhớ cần thiết cho live data, allocations, collector work | Là khái niệm vận hành, không chỉ một phép trừ cố định. |
| **RSS / working set** | Memory process/container thực sự chiếm theo OS | Có memory ngoài heap và các sắc thái page reclaim. |

### Headroom cho concurrent collectors

Một GC concurrent phải thu hồi bộ nhớ trong khi mutators **tiếp tục allocate**. Nếu tốc độ phát sinh objects mới vượt khả năng collector hoàn thành cycle hoặc destination capacity không đủ, hệ thống có thể bị **allocation stalls**, forced collection hoặc out-of-memory theo collector cụ thể.

Một bộ nhớ quá chật:
- làm GC chạy thường xuyên;
- giảm vùng dự trữ cho các objects được di chuyển;
- tăng rủi ro phải chuyển sang đường xử lý đắt hơn;
- tăng độ khó giữ latency ổn định.

Ngược lại, tăng `-Xmx`:
- thường giảm áp lực GC đối với một số workload;
- nhưng tăng ngân sách memory, có thể tăng footprint và làm giảm density triển khai;
- có thể tăng thời gian xử lý tập live lớn hoặc thay đổi behavior GC;
- không chữa retention leak nếu object graph tăng vô hạn.

**Đừng khuyên “cứ tăng heap thật lớn để giảm GC”** nếu chưa kiểm tra RAM quota, native memory, live-set và SLA.

## 6. Tỷ lệ sống/chết quan trọng hơn chỉ số `new`

GC phải xử lý **đối tượng sống** theo thuật toán phù hợp. Allocation lớn nhưng objects chết nhanh có thể được collector xử lý tương đối hiệu quả, nhất là trong young-generation workloads. Tuy nhiên allocation quá nhanh vẫn dùng CPU/bandwidth và tạo pressure.

Hai tình huống:

**Workload A:** tạo 2 GB temporary byte arrays trong mỗi khoảng thời gian, nhưng chỉ giữ 200 MB live. Young collection có thể reclaim phần lớn dung lượng, nhưng allocation rate cao gây work.

**Workload B:** allocation rate thấp, nhưng có 10 GB objects sống lâu trong cache; mỗi major cycle phải xử lý lượng live data lớn, có thể làm heap/headroom rất căng.

Bởi vậy các metric cần cùng xuất hiện:
- bytes allocated/op và bytes/s,
- live heap sau các collection phù hợp,
- object ages/lifetime histogram khi có dữ liệu,
- humongous/large-object allocation rate,
- GC frequency, pause distribution và CPU,
- total process memory và CPU quota.

**Một “memory leak” trong Java** thường là sự giữ references ngoài ý muốn. Đổi từ G1 sang ZGC không tự làm những objects còn reachable biến mất. Khi vấn đề là leak, sửa cache/ownership/lifetime thường quan trọng hơn tuning collector.

## 7. Tổng quan Serial, Parallel, G1, ZGC, Shenandoah

Các nhận định dưới đây mô tả **hướng thiết kế chung**, không phải bảng xếp hạng tuyệt đối. Các thuật toán và flags thay đổi theo JDK release/HotSpot vendor.

| Collector | Hướng ưu tiên thường gặp | Điểm mạnh | Đánh đổi cần đo |
| --- | --- | --- | --- |
| **Serial GC** | Đơn giản, heap/workload nhỏ hoặc CPU rất hạn chế | Ít GC worker overhead, setup đơn giản | STW và GC làm việc bằng ít threads, không hợp pauses lớn trên heap lớn |
| **Parallel GC** | Ưu tiên **throughput** với parallel collection | Tận dụng nhiều CPU cores cho GC, good throughput trong nhiều batch workloads | STW pauses có thể dài, không phù hợp tail latency chặt |
| **G1 GC** | Cân bằng throughput và pause-time goal | Region-based, incremental evacuation, concurrent marking, pause target heuristic | Concurrent work, remembered-set/card overhead, evacuation/headroom và humongous object considerations |
| **ZGC** | **Low-pause/low-latency** theo hướng concurrent GC | Phần nặng của marking/relocation được làm concurrent; giảm STW contribution | CPU/bandwidth, barrier overhead, đủ heap headroom để không stall |
| **Shenandoah** | Low-pause với concurrent evacuation/compaction | Nhiều công việc GC thực hiện đồng thời với mutators | Tăng concurrent/barrier work và memory headroom; availability/mode tùy bản JDK |

### Serial GC

`-XX:+UseSerialGC` là một lựa chọn hợp lý để **thử nghiệm** cho chương trình nhỏ, heap nhỏ hoặc khi chỉ có một CPU và pauses không quan trọng. Nó không phải “GC cho Java cũ” theo nghĩa không thể dùng trên JDK mới. Nhưng khi heap lớn và độ trễ nhạy, làm collection trong STW có thể gây pause dài.

### Parallel GC

`-XX:+UseParallelGC` là collector hướng throughput. Nhiều GC worker threads giúp hoàn tất công việc GC trong những pauses; điều đó có thể làm throughput tốt khi workload có nhiều cores và chấp nhận pauses. **Parallelism của GC không biến toàn bộ collection thành concurrent**; application vẫn có thể bị dừng.

### G1

`-XX:+UseG1GC` là lựa chọn mặc định trên **nhiều cấu hình HotSpot hiện đại**. G1 thiết kế để đạt pause target với xác suất cao trong khi giữ throughput tốt; pause target là **soft goal**, không phải SLA cứng. G1 thu gom theo regions; young/mixed collections có evacuation, marking một phần đồng thời, và overhead remembered sets/barriers.

### ZGC

`-XX:+UseZGC` chọn ZGC nếu bản JDK/VM hỗ trợ. ZGC được thiết kế để làm những công việc nặng đồng thời nhằm giảm thời gian dừng ứng dụng, thích hợp với latency-sensitive workloads. Nhưng low GC pause **không miễn phí**: concurrent work và heap headroom có thể giảm throughput hoặc tăng RSS theo workload.

### Shenandoah

`-XX:+UseShenandoahGC` chọn Shenandoah **trên các build có hỗ trợ**. Đây là collector low-pause bằng concurrent work, nhưng availability/flags/modes phụ thuộc bản OpenJDK vendor và release. Không kết luận “Oracle JDK, OpenJDK, tất cả distributions cùng có Shenandoah và một bộ flags giống nhau”.

## 8. Cảnh báo khác biệt giữa các bản Java: đặc biệt ZGC

Khi đưa lệnh vào README hoặc playbook, cần ghi **bản Java mục tiêu**. Ví dụ:

~~~text
JDK 17:
  -XX:+UseZGC                 (ZGC non-generational trên JDK 17)

JDK 21:
  -XX:+UseZGC -XX:+ZGenerational
    (chọn Generational ZGC trên JDK 21)

JDK 23:
  -XX:+UseZGC                 (generational mode mặc định theo JEP 474)

JDK 24 trở lên với HotSpot tương ứng:
  -XX:+UseZGC                 (non-generational ZGC đã bị loại bỏ)
~~~

Đây là **sơ đồ tùy chọn theo phiên bản**, không phải một script chạy nguyên khối. JDK 24 loại bỏ non-generational ZGC và option `ZGenerational`, nên **không bê nguyên** lệnh JDK 21 có `-XX:+ZGenerational` sang JDK 24+. Shenandoah cũng có những bước phát triển generational và flags tùy phiên bản.

Một thực hành tốt:

~~~bash
java -version
java -XX:+PrintFlagsFinal -version
~~~

Lệnh thứ hai có thể in rất nhiều flags. Đừng đoán collector từ năm phát hành Java; ghi **JDK build** và **selected GC thực tế**. Có thể dùng `-Xlog:gc*=info` hoặc `jcmd PID VM.flags` để hỗ trợ điều tra.

**Không nâng cấp Java rồi tái dùng một bộ GC tuning flags cũ mà không kiểm tra.** Defaults và algorithm có thể thay đổi đáng kể giữa Java 8, 17, 21, 24 và các release sau.

## 9. Đặt vấn đề dưới dạng ngân sách SLO

Thay vì hỏi “GC nào nhanh nhất?”, hãy tạo một ngân sách:

| Tiêu chí | Ví dụ yêu cầu (giả định) |
| --- | --- |
| Sustained throughput | Ít nhất 2000 successful requests/s |
| End-to-end p99 latency | Không quá 120 ms tại 2000 requests/s |
| Error/timeout rate | Dưới 0,1% |
| CPU budget | Không vượt 4 vCPU quota, tránh CPU throttling kéo dài |
| Container RAM limit | 4 GiB |
| Headroom | Không xảy ra allocation stalls/Full GC/OOME trong test kéo dài |
| GC pauses | GC-related pauses không được thường xuyên chiếm phần lớn latency budget |
| Scale-out density | Số instances cần để đáp ứng tải trong ngân sách hạ tầng |

**Đây là ví dụ học thuật, không phải chỉ tiêu áp dụng mọi ứng dụng.** Tất cả số liệu phải phù hợp dịch vụ, container và SLO thực tế. Một quyết định GC thành công phải **đồng thời** thỏa throughput, latency, stability và memory/CPU cost; nếu bất cứ mục quan trọng nào thất bại thì không nên chọn chỉ vì collector có pause median thấp.

### Latency budget

Giả sử p99 SLO = 120 ms, trong đó không-GC logic đã thường cần 100 ms ở tải mục tiêu. GC cùng CPU contention chỉ có khoảng không hẹp trước khi tổng vượt 120 ms. Nhưng không thể lấy đơn giản “p99 app = p99 business + p99 GC” trong mọi tình huống do **các phân phối có tương quan**, queueing và sampling windows. Sử dụng request tracing, event correlation và load testing để đo distribution cuối cùng.

## 10. Ma trận lựa chọn theo tình huống

| Tình huống | Khởi điểm thử nghiệm | Điểm cần kiểm tra |
| --- | --- | --- |
| CLI tool nhỏ, chạy rất ngắn, heap nhỏ | Mặc định, thử Serial nếu phù hợp | Startup, overall runtime, GC worker overhead |
| Batch throughput-first, pauses vài giây có thể chấp nhận | Parallel và mặc định/G1 làm baseline | Jobs/s, total CPU time, elapsed time, GC pause impact |
| Web API với latency SLO vừa phải | G1 mặc định làm baseline | p99, pause distribution, remembered-set work, CPU/RSS |
| API giao dịch p99 rất chặt | So G1 được tuning với ZGC hoặc Shenandoah nếu build có hỗ trợ | Tail latency **end-to-end**, CPU/headroom, allocation stalls |
| Container RAM thấp, rất nhiều JVM instances | Baseline default/Serial/G1 tùy heap và CPU | RSS, native memory, memory.max, Full GC, OOME |
| Heap nhiều GB, nhiều long-lived objects | G1/ZGC/Shenandoah theo SLA | Live-set, concurrent work, headroom, marking speed |
| Allocation rate cao, temporary objects | G1/Parallel/ZGC theo SLO; tối ưu churn trước | Bytes/op, throughput, GC CPU, stalls |
| Large arrays/humongous objects | Collector baseline và phân tích allocation distribution | G1 humongous regions, fragmentation, headroom |
| Low-latency nhưng CPU quota chặt | Không mặc định chọn concurrent GC | CPU throttling, concurrent CPU, backlog, service p99 |

**Gợi ý mặc định:** bắt đầu với lựa chọn collector mặc định của chính JDK production và heap budget hợp lý; chỉ đổi GC nếu dữ liệu cho thấy không đạt SLO hoặc cost. Oracle GC tuning guide cũng định hướng bắt đầu từ cấu hình mặc định trừ khi yêu cầu đặc thù.

## 11. Kích thước heap: rất nhỏ và rất lớn đều có mặt trái

### Khi `-Xmx` quá nhỏ

- Young/old collections xảy ra thường xuyên.
- Ít evacuation/headroom cho collector cần di chuyển objects.
- Allocation stalls hoặc Full GC có thể tăng.
- Với GC concurrent, không đủ khoảng trống để hoàn thành cycle trước khi mutator dùng hết memory.
- Có thể OOME dù ứng dụng không có memory leak, chỉ do heap budget không đáp ứng peak live set.

### Khi `-Xmx` quá lớn

- Reserved/committed footprint có thể cao; mật độ triển khai giảm.
- Tập live data vẫn có thể lớn và GC cần làm nhiều work.
- Ứng dụng có thể ẩn memory retention bug lâu hơn trước khi biểu hiện.
- Có thể đẩy tổng process memory vượt container RAM do phần **ngoài heap**.
- Với một số collector/configuration, thay đổi heap làm thay đổi generation sizing/GC cycle.

Không được kết luận “heap càng to thì GC pauses càng dài” hay “heap càng to thì latency luôn tốt”: quan hệ tùy collector và workload. Hãy đo **khoảng giá trị heap** ở tải thật và tìm điểm đáp ứng cả headroom lẫn memory cost.

### `-Xms` và `-Xmx` bằng nhau?

Đặt initial/max heap bằng nhau có thể giảm resizing activity và làm behavior ổn định hơn trong một số workload, nhưng làm committed/reserved memory cao ngay từ đầu theo VM behavior và không phải luôn tốt cho memory-constrained fleet. Một số collector có cơ chế uncommit khác nhau. Đừng biến `-Xms=-Xmx` thành quy tắc cho mọi ứng dụng.

## 12. Các flags thường dùng và giới hạn của chúng

| Cờ | Vai trò | Sai lầm phổ biến |
| --- | --- | --- |
| `-Xms256m` | Khởi tạo Java heap khoảng 256 MiB | Coi là toàn bộ process memory. |
| `-Xmx2g` | Giới hạn max Java heap | Coi là giới hạn RSS/container. |
| `-XX:+UseParallelGC` | Chọn Parallel GC | Hứa p99 nhỏ hơn G1 trong mọi workload. |
| `-XX:+UseG1GC` | Chọn G1 | Coi pause target là cam kết SLA. |
| `-XX:+UseZGC` | Chọn ZGC nếu hỗ trợ | Coi có zero overhead hoặc mọi JDK có same mode. |
| `-XX:MaxGCPauseMillis=200` | Pause-time target cho collector hỗ trợ, đặc biệt G1 | Đọc là “không pause nào vượt 200 ms”. |
| `-XX:MaxRAMPercentage=...` | Dùng tỷ lệ RAM khả dụng mà JVM nhận diện để suy heap limit | Quên native memory và cgroup headroom. |
| `-XX:ActiveProcessorCount=...` | Ghi đè số processor JVM sử dụng cho một số heuristics | Dùng để che CPU quota thay vì tìm saturation. |
| `-Xlog:gc*:file=gc.log:time,uptime,level,tags` | Bật GC unified logging theo mức và đích file | Không giới hạn rotation/dung lượng log khi chạy lâu. |

Đây là danh sách **định hướng**, không phải yêu cầu phải bật tất cả cùng lúc. Flags availability/semantic có thể thay đổi theo release. Ví dụ `MaxGCPauseMillis` là **mục tiêu điều chỉnh heuristic**, không phải timer cưỡng chế GC return.

### Tránh ép young-generation sizing tùy tiện với G1

G1 sử dụng young generation size như một trong những công cụ để cân bằng pause target và throughput. Ép `-Xmn` hay các flags thế hệ cũ có thể hạn chế khả năng tự thích nghi của G1. Tốt hơn:
1. Đặt heap bounds phù hợp.
2. Đo logs và latency trước.
3. Nếu cần, điều chỉnh **một** goal như `MaxGCPauseMillis`.
4. Kiểm tra trade-off throughput/CPU/GC frequency sau mỗi thay đổi.

**Không tối ưu một pause riêng biệt bằng cách bỏ qua hiệu quả của toàn hệ thống**.

## 13. Ví dụ Java 17+: tính throughput và percentiles từ dữ liệu cố định

Một ví dụ nhỏ, **chỉ dạy cách tính metric**, không tự sinh số liệu GC của máy:

Tạo tệp **`GcMetricMathDemo.java`**:

~~~java
import java.util.Arrays;
import java.util.Locale;

public class GcMetricMathDemo {
    static long nearestRank(long[] sorted, double fraction) {
        int rank = (int) Math.ceil(fraction * sorted.length);
        return sorted[Math.max(0, rank - 1)];
    }

    public static void main(String[] args) {
        long[] latenciesMs = {5, 7, 8, 9, 10, 11, 12, 13, 30, 90};
        Arrays.sort(latenciesMs);

        long completed = 9_500L;
        double windowSeconds = 10.0;
        double stwSeconds = 0.30;

        System.out.printf(Locale.ROOT, "throughput=%.1f req/s%n",
                completed / windowSeconds);
        System.out.printf(Locale.ROOT, "stwFraction=%.1f%%%n",
                100.0 * stwSeconds / windowSeconds);
        System.out.println("p50=" +
                nearestRank(latenciesMs, 0.50) + "ms");
        System.out.println("p90=" +
                nearestRank(latenciesMs, 0.90) + "ms");
        System.out.println("p99=" +
                nearestRank(latenciesMs, 0.99) + "ms");
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac --release 17 GcMetricMathDemo.java
java GcMetricMathDemo
~~~

**Stdout chính xác:**

~~~text
throughput=950.0 req/s
stwFraction=3.0%
p50=10ms
p90=30ms
p99=90ms
~~~

Giải thích:
- 9.500 công việc hoàn thành trong 10 giây → **950 req/s**.
- 0,3 giây STW trên 10 giây → **3%** theo mô hình đơn giản.
- Với **nearest-rank percentile**, p50 là phần tử thứ 5, p90 là thứ 9 và p99 là thứ 10 của 10 mẫu.
- **10 mẫu không đủ** để đại diện p99 thực tế của một hệ thống sản xuất; ví dụ chỉ nhằm làm rõ công thức. Phải có cửa sổ thời gian, số lượng mẫu và histogram phù hợp.
- Đừng lấy 3% STW để suy business throughput tăng 3% nếu loại bỏ GC, vì concurrent CPU, queueing, cache effects và các bottlenecks khác vẫn tồn tại.

## 14. Ví dụ Java 17+: workload tạo object để thử GC

Một chương trình có thể tạo nhiều arrays và giữ lại một phần nhỏ để thử các collectors **trên cùng dữ liệu logic**. Tuy nhiên GC scheduling, số pauses và timings phụ thuộc máy/JDK/heap, nên stdout chỉ cam kết những thông tin nghiệp vụ đã được viết xác định.

Tạo tệp **`GcAllocationDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class GcAllocationDemo {
    private static volatile byte[] latest;

    public static void main(String[] args) {
        List<byte[]> survivors = new ArrayList<>();
        long checksum = 0;

        for (int i = 0; i < 100_000; i++) {
            byte[] data = new byte[1_024];
            data[0] = (byte) i;
            checksum += Byte.toUnsignedInt(data[0]);

            if ((i % 128) == 0) {
                survivors.add(data);
            } else {
                latest = data;
            }
        }

        System.out.println("survivors=" + survivors.size());
        System.out.println("checksum=" + checksum);
    }
}
~~~

Chạy trên **JDK 17+**:

~~~bash
javac --release 17 GcAllocationDemo.java
java -Xms32m -Xmx128m -XX:+UseG1GC GcAllocationDemo
~~~

**Stdout nghiệp vụ chính xác** (không tính GC logs nếu được bật):

~~~text
survivors=782
checksum=12742320
~~~

Tại sao chương trình có ý nghĩa:
- Mỗi lượt tạo một byte array; tổng logical allocation payload cỡ 100.000 × 1.024 bytes, nhưng **allocation bytes thực tế** còn có header, alignment và JIT effects.
- Mỗi 128 lượt giữ một array trong list, tức có 782 survivors được tham chiếu từ collection ở cuối vòng lặp.
- Field `latest` được khai báo `volatile` và giữ reference gần nhất của các vòng không được retain; nó không bảo đảm mỗi GC sẽ xảy ra đúng thời điểm hoặc mỗi array buộc phải được allocation vật lý trong mọi JIT mode.
- Checksum chỉ phụ thuộc dãy byte 0..255 lặp lại, không phụ thuộc GC collector.
- **Không so sánh hiệu năng bằng stdout ở đây**; muốn đo hiệu năng phải thêm phép thử có setup, warmup, số lần lặp, tính nhiều runs và tools phù hợp.

### Chạy cùng chương trình với các collectors

Ví dụ trên JDK 17+ có G1 và Parallel:

~~~bash
java -Xms32m -Xmx128m -XX:+UseParallelGC GcAllocationDemo
java -Xms32m -Xmx128m -XX:+UseG1GC GcAllocationDemo
~~~

Trên JDK 21 có thể so Generational ZGC:

~~~bash
java -Xms32m -Xmx128m -XX:+UseZGC -XX:+ZGenerational GcAllocationDemo
~~~

**Cảnh báo:** heap 128 MiB ở ví dụ chỉ là cấu hình thử cho workload nhỏ, **không phải khuyến nghị đủ headroom cho ZGC trong mọi tình huống**. Nếu một JDK/VM không hỗ trợ option đó hoặc thất bại với cấu hình hạn chế, hãy kiểm tra flags/version và tăng ngân sách theo điều kiện thử nghiệm; không suy rằng bản thân thuật toán luôn lỗi.

Với JDK 24+ (khi ZGC generational là chế độ duy nhất), chỉ dùng `-XX:+UseZGC` thay vì giữ cờ `-XX:+ZGenerational` đã loại bỏ.

### Có thể buộc GC đúng số lần không?

**Không.** `System.gc()` không phải cơ chế bảo đảm một lần STW, một lần compact hay một loại GC event cố định. Đừng viết ví dụ giáo trình hứa một số pause, GC-count hoặc milliseconds giống nhau trên mọi máy.

## 15. Bật GC logs và đọc đúng

Đối với workload đã biên dịch, ví dụ:

~~~bash
java -Xms32m -Xmx128m -XX:+UseG1GC -Xlog:gc*:file=gc.log:time,uptime,level,tags GcAllocationDemo
~~~

GC log file `gc.log` có thể chứa các dòng về initialization, pause, region usage và heap transitions tùy bản HotSpot/flags. **Không có stdout GC chính xác bất biến** vì scheduling và allocation behavior phụ thuộc runtime. Cũng có thể chương trình quá ngắn hoặc heap đủ rộng khiến event ít hơn kỳ vọng.

Nên ghi:
- Tên collector thực sự được chọn.
- JVM build và tất cả JVM args.
- Timestamps, GC IDs, phases và durations.
- Heap occupancy before/after GC, frequency, pause maxima/percentiles.
- Concurrent GC cycle work và dấu hiệu allocation stall/failure.
- G1: young/mixed/full events, remembered-set/ref-processing phases nếu cần.
- ZGC/Shenandoah: phase/concurrent work/stalls theo tags/format của bản JDK.

**Một dòng `Pause Young` trong G1 là hoạt động bình thường**, không mặc định là sự cố. **Full GC liên tục**, allocation stalls hoặc các lần pause kéo dài bất thường trong SLO mới là dấu hiệu đáng điều tra.

### Rotation logs khi chạy lâu

Ví dụ với HotSpot unified logging (điều chỉnh theo môi trường):

~~~bash
java -Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=20M -Xmx512m -XX:+UseG1GC GcAllocationDemo
~~~

Kiểm tra cú pháp các decorators/rotation options theo bản JDK. Log GC có thể lớn, cần định nghĩa retention và quyền truy cập; không ghi vô hạn lên ổ đĩa production.

## 16. JFR, jcmd và các số liệu cần thu

Để thấy bức tranh rộng hơn GC pauses, dùng **JFR (Java Flight Recorder)** và các diagnostics của HotSpot. Ví dụ lệnh tương tác với một **Java process đang chạy**, thay `PID` bằng PID thật trước khi thực thi:

~~~bash
jcmd PID VM.flags
jcmd PID GC.heap_info
jcmd PID JFR.start name=GCStudy settings=profile duration=60s filename=gc-study.jfr
jcmd PID JFR.check
~~~

Đây là cú pháp minh họa với tên process PID cần thay, **không** phải command literal PID. Tên diagnostics và cách cung cấp arguments có thể khác nhau giữa JDK builds.

Một cách khác là khởi động recorder ngay từ đầu (JDK hỗ trợ):

~~~bash
java -XX:StartFlightRecording=filename=gc-study.jfr,duration=60s,settings=profile -Xmx512m GcAllocationDemo
~~~

Những lệnh trên có thể ghi file JFR lớn và chứa dữ liệu ứng dụng; bảo vệ quyền truy cập giống các diagnostic artifacts khác. Recording duration 60s có thể dài hơn thời gian ứng dụng minh họa chạy, nên **không hứa recording đầy đủ 60s** nếu process kết thúc sớm.

Khi nghi **object retention/leak** thay vì pause problem, có thể xem histogram:

~~~bash
jcmd PID GC.class_histogram
~~~

Hoặc heap dump khi đã đánh giá ảnh hưởng đến process:

~~~bash
jcmd PID GC.heap_dump filename=gc-study.hprof
~~~

**Cảnh báo:** class histogram và heap dump có thể gây high impact/pause, và heap dump có thể chứa secrets/PII. Không thu/sao chép trên production nếu chưa có quyền và kế hoạch xử lý dữ liệu.

### Metric nào trả lời câu hỏi gì?

| Công cụ/nguồn | Trả lời tốt | Không đủ để kết luận |
| --- | --- | --- |
| GC unified logs | Pause events, heap transitions, GC phase metrics | Request p99, CPU throttling và toàn bộ native memory |
| JFR | GC, allocation samples, threads, CPU/runtime events theo cấu hình | Không phải mọi event đều được bật/sampled 100% |
| jcmd GC.heap_info | Heap trạng thái hiện tại | Retained owner graph, p99 |
| Class histogram | Count/bytes theo runtime class | Ai giữ object còn reachable |
| Heap dump + dominator | Retainers/paths to GC roots | Latency/time spent trong requests |
| Service metrics/tracing | Successful RPS, p99, errors, queue depth | GC algorithm root cause nếu chỉ đo service |
| Container/OS metrics | CPU quota, throttling, RSS/OOM killer | Phân biệt heap/native/GC phase nếu đứng riêng |
| Load-testing tool | SLO tại offered load cố định | Hiệu năng production nếu traffic model không thực |

**Cần tương quan cùng timeline**: một GC pause xảy ra lúc 10:00:03 có gây request spikes không, hay spikes tới từ DB/network/CPU throttling ở cùng cửa sổ?

## 17. Thực nghiệm lựa chọn GC có kiểm soát

Một quy trình A/B đáng tin:

**Bước 1 — Xác định ràng buộc:** workload, API SLA, p99/p99.9, desired throughput, error budget, CPU quota, RAM/RSS budget, peak traffic và thời gian tải kéo dài.

**Bước 2 — Chạy baseline:** dùng collector mặc định hoặc collector production, ghi version JDK, heap flags, CPU limits, traffic distribution, GC logging/JFR. Đo sau warmup.

**Bước 3 — Chỉ thay một yếu tố chính:** thử Parallel, G1, ZGC hoặc Shenandoah (nếu có hỗ trợ), giữ nguyên ứng dụng, tập dữ liệu, offered load và CPU/memory budget.

**Bước 4 — Test nhiều mức tải:** thường tải thấp, tải mục tiêu, peak và burst. Chạy đủ lâu để thấy aging/old-gen cycles, cache growth và các concurrent GC cycles.

**Bước 5 — Thu cả phân phối:** RPS, latency p50/p95/p99/p99.9, error rate, GC pause histogram, GC CPU, total process CPU, throttling, RSS, heap after GC, allocation stalls.

**Bước 6 — Lặp runs:** warmup/JIT, CPU frequency, cache/cgroup sharing và GC scheduling có thể gây nhiễu. Một lần chạy đẹp không đủ kết luận.

**Bước 7 — Chọn theo constraint:** loại mọi cấu hình vi phạm SLO/budget; trong số còn lại, so tổng chi phí và độ ổn định.

**Bước 8 — Canary production:** kiểm tra với traffic thật, cảnh báo CPU/GC/OOM, rollback plan và bảo đảm diagnostic logs không vượt disk budget.

Một **Pareto frontier** hữu ích hơn “GC tốt nhất”: một cấu hình có throughput cao hơn nhưng p99 tệ hơn có thể không *dominate* cấu hình khác; tùy SLA, mỗi cấu hình phù hợp mục tiêu khác nhau.

### Tránh phép so sánh thiên lệch

- Không để Parallel chạy với `-Xmx=8g` còn ZGC bị giới hạn `-Xmx=512m` rồi kết luận collector nào tốt hơn.
- Không lấy G1 sau warmup so với ZGC ngay lần chạy đầu.
- Không chỉ so trên máy local nhiều cores khi production container chỉ có 1 CPU quota.
- Không chỉ xem average latency nếu business SLO là p99.
- Không chỉ xem GC pause time mà quên throughput và total CPU.
- Không chỉ xem heap usage khi container OOM bởi direct buffers, stacks hoặc metaspace.
- Không dùng synthetic allocation benchmark như bằng chứng đủ cho data access/database workload.

## 18. Phân tích kết quả bằng một ma trận giả định

Bảng dưới đây là **số giả định để học cách ra quyết định**, không phải benchmark thật hay số liệu của bất kỳ JVM nào:

| Chỉ số tại offered load cố định | Parallel | G1 | ZGC |
| --- | ---: | ---: | ---: |
| Successful throughput | 2.300 req/s | 2.180 req/s | 2.050 req/s |
| Application p99 latency | 240 ms | 115 ms | 78 ms |
| Total process CPU | 2,8 cores | 3,1 cores | 3,7 cores |
| RSS | 2,1 GiB | 2,5 GiB | 3,0 GiB |
| Full GC / stalls | Không quan sát trong window | Không quan sát | Không quan sát |

Tùy ràng buộc:
- **Batch job không cần low latency:** Parallel có thể tốt nếu jobs/s là mục tiêu chính.
- **API p99 phải dưới 120 ms, CPU ≤3,5 cores, RSS ≤2,8 GiB:** G1 là **ứng viên đáp ứng ví dụ**; Parallel vượt p99 và ZGC vượt CPU/RSS.
- **API p99 dưới 90 ms, CPU ≤4 cores, RSS ≤3,5 GiB:** ZGC có thể là ứng viên trong bảng giả định, nhưng cần thêm test ổn định và peak.
- **Nếu tất cả đều vượt budget ở peak:** không nên ép chọn một GC; cần giảm allocation/retention, scale out, đổi heap/quota hoặc xem lại SLO/cấu trúc.

Đây là bài tập **đọc ma trận đa mục tiêu**. Không lấy số minh họa làm khuyến nghị mua CPU/RAM hay dự đoán hiệu năng của G1/ZGC thực tế.

## 19. Các nguyên nhân thường quan trọng hơn đổi GC

### Tạo object quá nhiều

Autoboxing, intermediate collections, serializers, regex/JSON objects, string concatenation patterns và temporary buffers có thể tăng allocation rate. Dùng JFR allocation profiling, benchmarks hợp lệ và tối ưu ở điểm nóng để giảm công việc của mọi collectors.

### Retention/caches quá lớn

Cache không eviction, long-lived collections, listener registrations, thread locals và large payload references có thể giữ live heap gần `-Xmx`. Collector nào cũng phải giữ objects còn reachable. Dùng heap dump + dominator tree để tìm owners.

### CPU throttling trong container

Nếu cgroup thường xuyên throttle CPU, collector concurrent có thể không hoàn thành marking đủ nhanh hoặc làm tăng queueing trong request processing. Phải điều chỉnh quota, concurrency, GC selection và ứng dụng; không giải quyết chỉ bằng `MaxGCPauseMillis`.

### Too many threads / native memory

Nhiều thread stacks, direct buffers và metaspace có thể đẩy RSS vượt memory quota dù Java heap còn rảnh. Đo native memory/OS metrics, cân nhắc thread management và container limits; đừng chỉ tăng heap.

### Large/huge allocations

G1 có thể xử lý **humongous objects** khác object nhỏ, gây áp lực contiguous regions và headroom. Giảm temporary large buffers hoặc tổ chức dữ liệu khác có thể hiệu quả hơn đổi collector. Nhưng đừng tự cắt arrays thành chunks nếu điều đó gây nhiều copies và phức tạp ứng dụng mà chưa đo.

## 20. Lỗi thường gặp khi lựa chọn GC

| Nhận định | Vì sao không chính xác |
| --- | --- |
| “ZGC có low pause nên luôn nhanh nhất.” | CPU concurrent, memory headroom và barrier overhead có thể giảm throughput. |
| “Parallel ưu tiên throughput nên mọi batch job đều phải dùng.” | Depends on heap/live set/cores; mặc định hoặc G1 cũng có thể tốt. |
| “G1 MaxGCPauseMillis=50 nghĩa là mọi pause ≤50 ms.” | Đó là soft target, không phải deadline cứng. |
| “GC p99 pause=10 ms nên API p99 ≤10 ms.” | Request latency còn application work, I/O, queueing và concurrent CPU. |
| “STW fraction 1% nghĩa ứng dụng throughput đạt 99%.” | Chỉ là available-time approximation, không tính concurrent GC/cost khác. |
| “Heap -Xmx=2g thì container 2g đủ.” | RSS bao gồm metaspace, code cache, stacks, direct buffers, GC native overhead. |
| “RSS cao chứng minh leak Java heap.” | Có native/committed memory hoặc caching pages, cần phân tách. |
| “Ít GC event nghĩa collector tốt.” | Event hiếm nhưng rất dài vẫn có thể gây p99 spikes. |
| “GC chạy concurrent là không dừng ứng dụng bao giờ.” | Vẫn có short pauses/safepoints/handshakes theo collector. |
| “CPU host còn 50% rảnh nghĩa container không bị throttle.” | Container quota có thể giới hạn độc lập với host idle cores. |
| “Giảm -Xmx luôn tiết kiệm RAM không ảnh hưởng latency.” | Có thể tăng GC frequency và allocation stalls/OOME. |
| “Tăng -Xmx luôn giảm latency.” | Có trade-offs về footprint, live set, collector scheduling. |
| “Một benchmark 30 giây đủ cho mọi long-lived workload.” | Chưa thấy old-gen cycles, aging, leak, peak behavior. |
| “Dùng cùng flags ZGC cho JDK 17, 21, 24, 26.” | Generational mode/options thay đổi qua releases. |
| “Shenandoah có trên mọi HotSpot distribution.” | Availability phụ thuộc build/vendor/version. |
| “G1 là default nên luôn tối ưu nhất.” | Default cân bằng, không phải tối ưu cho mọi workload cụ thể. |
| “Một lần System.gc() đo GC rất khách quan.” | Không hứa loại/time GC, còn ảnh hưởng warmed state và collector policies. |
| “GC logs tự cho biết throughput business.” | Cần service counters/completed work và latency metrics riêng. |
| “Tối ưu GC bằng một flag cùng lúc với đổi code/heap.” | Không biết thay đổi nào tác động; so sánh không kiểm soát. |
| “Pause trung bình giảm đủ để đạt p99.” | Tail latency phải đo distribution; mean có thể che outliers. |

## 21. Câu hỏi tự kiểm tra

**1.** Nếu tổng STW time là 0,5 giây trong 10 giây, có thể tuyên bố throughput ứng dụng 95% không?

**Đáp án:** Không. Có thể nói STW pause fraction khoảng 5%; throughput ứng dụng cần đo successful work/s, còn concurrent GC cũng dùng CPU.

**2.** p99 GC pause và p99 HTTP response latency có phải cùng chỉ số không?

**Đáp án:** Không. Chúng đo hai population khác nhau; cần correlation và request SLO.

**3.** Vì sao ZGC có thể dùng nhiều CPU hơn nhưng có pauses thấp hơn?

**Đáp án:** Nhiều GC work được làm concurrent, cùng barrier/coordination overhead và memory bandwidth; giảm work trong STW có thể đổi lấy work lúc mutators đang chạy.

**4.** Khi một container chỉ có 1 vCPU, có nên mặc định chọn GC nhiều concurrent threads không?

**Đáp án:** Không. Cần đo CPU competition/throttling và application p99 trước; có thể throughput hoặc latency bị ảnh hưởng.

**5.** Có thể đặt `-Xmx=4g` trong container RAM limit 4 GiB mà bảo đảm không bị OOMKilled không?

**Đáp án:** Không. Ngoài Java heap còn native memory, metaspace, code cache, thread stacks và runtime overhead.

**6.** Vì sao G1 pause-time target không được coi là SLA?

**Đáp án:** `MaxGCPauseMillis` là heuristic goal, không phải giới hạn bắt buộc cho mọi event.

**7.** Chọn Parallel hay ZGC cho một batch không có latency SLO?

**Đáp án:** Đo throughput/CPU/RSS trên workload thật; Parallel là một ứng viên throughput-first nhưng không bảo đảm thắng.

**8.** Tăng heap có thể gây tác động trái chiều nào?

**Đáp án:** Có thể giảm collection frequency nhưng tăng footprint/density cost, thay đổi collector behavior và che retention.

**9.** Live-set lớn nhưng allocation rate thấp có thể khiến GC đắt không?

**Đáp án:** Có. Collector vẫn cần scan/copy/mark nhiều live objects theo thuật toán, cần headroom.

**10.** Nên lấy metrics gì khi nghi allocation stalls?

**Đáp án:** GC logs với stall/failure markers, heap before/after, allocation rate, concurrent GC timing, CPU quota và free headroom.

**11.** Khi nào dùng heap dump thay vì chỉ GC log?

**Đáp án:** Khi cần tìm object graph retention, dominator/GC-root paths hoặc nghi leak. Heap dump có chi phí và chứa dữ liệu nhạy cảm.

**12.** Mục tiêu “request p99 <100 ms” có thể chứng minh chỉ bằng p99 GC pause <10 ms không?

**Đáp án:** Không. Phải đo end-to-end p99 tại tải mục tiêu và đối chiếu bottlenecks khác.

**13.** JDK 21 bật generational ZGC bằng flag nào?

**Đáp án:** `-XX:+UseZGC -XX:+ZGenerational` trên JDK 21 hỗ trợ. Từ JDK 24, không dùng `ZGenerational` vì non-generational mode đã loại bỏ.

**14.** Vì sao cùng một collector trên JDK 17 và JDK 25 vẫn phải benchmark lại?

**Đáp án:** GC algorithms, heuristics, flags, JIT/compiler, VM defaults và container awareness có thể thay đổi.

**15.** Tập thử nghiệm có throughput cao nhưng errors 5% có tốt không?

**Đáp án:** Không nếu SLO error budget thấp hơn. Completed successful work và correctness mới có ý nghĩa.

## 22. Bản đồ ghi nhớ

~~~text
                 APPLICATION GOALS / SLO
                           |
         +-----------------+------------------+
         |                 |                  |
      THROUGHPUT         LATENCY          RESOURCE BUDGET
      requests/s          p99/p99.9         CPU + RAM/RSS
         |                 |                  |
         +-----------------+------------------+
                           |
                     BASELINE DEFAULT GC
                           |
                   MEASURE WORKLOAD
             GC logs + JFR + service metrics
                  + container/OS metrics
                           |
               +-----------+-----------+
               |                       |
         Throughput first          Low latency first
               |                       |
         Parallel candidate        G1 / ZGC / Shenandoah
               |                       |
               +-----------+-----------+
                           |
                    HEADROOM / LIVE SET
                    ALLOCATION / GC CPU
                    ERRORS / STALLS / RSS
                           |
                   RUN CONTROLLED A/B
                           |
                   DOES IT MEET ALL SLOs?
                     /              \
                    NO              YES
                    |                |
             TUNE / FIX /         CANARY + MONITOR
             TRY ANOTHER
                           |
                  OPTIMIZE THE SYSTEM,
                 NOT JUST ONE GC NUMBER
~~~

**Điểm cốt lõi:** chọn GC là **bài toán nhiều mục tiêu**, không phải tối thiểu hóa một pause đơn lẻ. Throughput, tail latency, CPU quota, RSS, live set và heap headroom tác động lẫn nhau. **Parallel** thường là ứng viên throughput-first, **G1** hướng tới cân bằng, **ZGC/Shenandoah** là ứng viên low-pause khi có hỗ trợ, nhưng kết quả phải được xác nhận bằng workload và SLO thực tế. Đo đạc, giới hạn ngân sách và so sánh có kiểm soát đáng tin hơn bất kỳ lời hứa “collector nhanh nhất” nào.

## Tài liệu tham khảo chính thức

- [Oracle Java SE 21 — Available Collectors, hướng dẫn chọn GC](https://docs.oracle.com/en/java/javase/21/gctuning/available-collectors.html).
- [Oracle Java SE 21 — G1 Garbage Collector Tuning](https://docs.oracle.com/en/java/javase/21/gctuning/garbage-first-garbage-collector-tuning.html).
- [Oracle Java SE 21 — Z Garbage Collector](https://docs.oracle.com/en/java/javase/21/gctuning/z-garbage-collector2.html).
- [Oracle Java SE 21 — HotSpot GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/).
- [Oracle Java SE 21 — The java Command, GC logging and JVM flags](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html).
- [Oracle Java SE 21 — jcmd Command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html).
- [OpenJDK JEP 439 — Generational ZGC](https://openjdk.org/jeps/439).
- [OpenJDK JEP 474 — Generational ZGC by Default](https://openjdk.org/jeps/474).
- [OpenJDK JEP 490 — Remove Non-Generational ZGC](https://openjdk.org/jeps/490).
- [OpenJDK JEP 189 — Shenandoah GC](https://openjdk.org/jeps/189).
- [Java Flight Recorder — Java SE 21 documentation](https://docs.oracle.com/en/java/javase/21/jfapi/).


</details>

- [Quay lại đầu trang](#back-to-top)
